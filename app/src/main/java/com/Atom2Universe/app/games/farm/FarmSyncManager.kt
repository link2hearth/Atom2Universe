package com.Atom2Universe.app.games.farm

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The farm on Drive: downloaded when the game opens, published when it closes.
 *
 * Nothing pushes. `appDataFolder` is a drawer, not a server: it never wakes another device and
 * never announces a change. A device learns that another one played only by going to look, and
 * the moment to look is the opening of the game - right when the answer matters.
 *
 * That is also why the farm gets its own file rather than a slot inside `games_state.json`: the
 * clicker changes every second and is synced on demand, the farm changes in bursts and is synced
 * twice per session. Sharing one file would have dragged one rhythm onto the other.
 *
 * The whole safety of the thing rests on two values kept side by side:
 * - [KEY_BASE_SEQ], the cloud version this device is built on. While the file up there still
 *   carries that number, nobody else has played, and publishing over it continues the story.
 * - [KEY_SENT_HASH], the fingerprint of the farm as it was last exchanged. When the farm on disk
 *   no longer matches it, this device has played since.
 *
 * One of the two moved: the answer is obvious, apply or publish. Both moved: two real sessions
 * exist and no rule can merge them - a farm is not a high score. The player chooses.
 */
object FarmSyncManager {

    private const val TAG = "FarmSyncManager"

    /** Name on Drive. Public: the cloud screen files it under "game saves" by this name. */
    const val SYNC_FILE = "farm_state.json"

    private const val PREFS = "farm_sync"
    private const val KEY_BASE_SEQ = "base_seq"
    private const val KEY_SENT_HASH = "sent_hash"

    /** The upload outlives the activity that asked for it, so it cannot hang on its scope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Opening and closing must never overlap - two farms would race for the same file. */
    private val lock = Mutex()

    sealed class OpenResult {
        /** Nothing to do: signed out, offline, or this device is already up to date. */
        data object Idle : OpenResult()

        /** The cloud farm is now on disk. The screen has to be rebuilt on top of it. */
        data object Applied : OpenResult()

        /** Both sides moved on. Only the player can say which farm is the real one. */
        data class Conflict(val local: FarmSyncFile, val remote: FarmSyncFile) : OpenResult()
    }

    // ─── Ouverture du jeu ─────────────────────────────────────────────────────

    /**
     * Asks Drive whether another device has played since the last exchange.
     *
     * @param openedWith the save as it stood when the screen opened, read synchronously by the
     * activity. The download takes a moment and the player can plant something meanwhile; a farm
     * that changed under us during that moment counts as played on, not as untouched.
     */
    suspend fun onFarmOpened(context: Context, openedWith: String?): OpenResult =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val app = context.applicationContext
                val client = driveClient(app) ?: return@withLock OpenResult.Idle
                val remote = client.readJsonFile(SYNC_FILE)?.let { FarmSyncFile.fromJson(it) }
                if (remote == null) {
                    // Drive answers null both for "no such file" and for a request that failed.
                    // Publishing over a farm we merely failed to read would erase it, so we only
                    // seed the cloud when this device has never exchanged anything at all.
                    if (prefs(app).getLong(KEY_BASE_SEQ, 0L) == 0L) upload(app, client)
                    return@withLock OpenResult.Idle
                }
                if (remote.format > FarmState.MAX_SAVE_VERSION) {
                    // A farm written by a newer build. Applying it would make the parser refuse the
                    // save and start a fresh farm - the one loss this whole file exists to prevent.
                    // We leave the cloud alone until this build catches up.
                    Log.w(TAG, "Cloud farm in format ${remote.format}, this build reads up to ${FarmState.MAX_SAVE_VERSION}")
                    return@withLock OpenResult.Idle
                }
                val prefs = prefs(app)
                // The cloud is where we left it. Anything new here goes up at closing time.
                if (remote.seq == prefs.getLong(KEY_BASE_SEQ, 0L)) return@withLock OpenResult.Idle

                val local = localState(app)
                val played = local != null &&
                    (local.fingerprint() != prefs.getString(KEY_SENT_HASH, null) || local != openedWith)
                if (!played) {
                    apply(app, remote)
                    return@withLock OpenResult.Applied
                }
                OpenResult.Conflict(local = describeLocal(app, local!!), remote = remote)
            }
        }

    // ─── Le choix, quand il y a conflit ───────────────────────────────────────

    /** Keep the cloud farm: it lands on disk and becomes the farm this device plays. */
    suspend fun keepRemote(context: Context, remote: FarmSyncFile) = withContext(Dispatchers.IO) {
        lock.withLock { apply(context.applicationContext, remote) }
    }

    /**
     * Keep the farm of this device.
     *
     * We take the cloud version as our base without touching the fingerprint: the local farm stays
     * marked as played on, so closing the game publishes it over the other one. Nothing is uploaded
     * here - the player is still playing, and the session is not over.
     */
    fun keepLocal(context: Context, remote: FarmSyncFile) {
        prefs(context.applicationContext).edit { putLong(KEY_BASE_SEQ, remote.seq) }
    }

    // ─── Fermeture du jeu ─────────────────────────────────────────────────────

    /** Called as the farm leaves the screen. Fire and forget: it must survive the activity. */
    fun onFarmClosed(context: Context) {
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val client = driveClient(app) ?: return@withLock
                upload(app, client)
            }
        }
    }

    /** Publishes the local farm, unless there is nothing new or someone got there first. */
    private suspend fun upload(context: Context, client: GoogleDriveAppDataClient) {
        val local = localState(context) ?: return
        val prefs = prefs(context)
        val fingerprint = local.fingerprint()
        if (fingerprint == prefs.getString(KEY_SENT_HASH, null)) return

        val base = prefs.getLong(KEY_BASE_SEQ, 0L)
        val remote = client.readJsonFile(SYNC_FILE)?.let { FarmSyncFile.fromJson(it) }
        // Two reasons not to write, and the same answer to both: say nothing. The local farm stays
        // unsent, so the next opening turns the situation into a question the player can settle.
        //  - the cloud moved on while we played: ours is no longer a continuation of it, and
        //    overwriting would erase a whole session;
        //  - we have a version behind us but cannot read the file: null means "not found" and
        //    "request failed" alike, and only one of those makes overwriting safe.
        if (remote == null && base != 0L) {
            Log.d(TAG, "Cloud farm unreadable while we held version $base - upload deferred")
            return
        }
        if (remote != null && remote.seq != base) {
            Log.d(TAG, "Cloud moved to ${remote.seq} while we played on $base - upload deferred")
            return
        }
        val next = FarmSyncFile(
            seq        = base + 1,
            format     = FarmSyncFile.formatOf(local),
            savedAt    = System.currentTimeMillis(),
            deviceName = SharedGameStats.deviceName(context),
            state      = local
        )
        if (client.writeJsonFile(SYNC_FILE, next.toJson())) {
            prefs.edit {
                putLong(KEY_BASE_SEQ, next.seq)
                putString(KEY_SENT_HASH, fingerprint)
            }
            Log.d(TAG, "Farm published as version ${next.seq} (${local.length / 1024} kB)")
        }
    }

    // ─── Outils ───────────────────────────────────────────────────────────────

    private fun apply(context: Context, remote: FarmSyncFile) {
        context.getSharedPreferences(FarmState.PREFS, Context.MODE_PRIVATE)
            .edit { putString(FarmState.KEY_STATE, remote.state) }
        prefs(context).edit {
            putLong(KEY_BASE_SEQ, remote.seq)
            putString(KEY_SENT_HASH, remote.state.fingerprint())
        }
        Log.d(TAG, "Cloud farm version ${remote.seq} applied")
    }

    /** The local farm dressed as a sync file, so the choice screen describes both the same way. */
    private fun describeLocal(context: Context, state: String) = FarmSyncFile(
        seq        = prefs(context).getLong(KEY_BASE_SEQ, 0L),
        format     = FarmSyncFile.formatOf(state),
        // No date: this device never recorded when it last saved, and inventing one would put a
        // made-up fact beside a real one on the very screen where the player compares the two.
        savedAt    = 0L,
        deviceName = SharedGameStats.deviceName(context),
        state      = state
    )

    /** The save on this device, or null when the farm has never been played here. */
    private fun localState(context: Context): String? =
        context.getSharedPreferences(FarmState.PREFS, Context.MODE_PRIVATE)
            .getString(FarmState.KEY_STATE, null)

    /**
     * Enough to tell two farms apart without keeping a second copy of a sixty-kilobyte save in the
     * preferences. Length and hash together: a hash alone collides once in four billion, and a
     * collision here would silently skip one upload.
     */
    private fun String.fingerprint(): String = "$length:${hashCode()}"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun driveClient(context: Context): GoogleDriveAppDataClient? {
        val signIn = GoogleSignInManager(context)
        if (!signIn.isSignedIn()) return null
        val account = signIn.getSignedInAccount() ?: return null
        return GoogleDriveAppDataClient(context, account)
    }
}
