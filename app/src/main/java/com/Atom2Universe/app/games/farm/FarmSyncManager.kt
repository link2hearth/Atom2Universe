package com.Atom2Universe.app.games.farm

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient.ReadResult
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The farm on Drive: downloaded when the game opens, published once it closes.
 *
 * Nothing pushes. `appDataFolder` is a drawer, not a server: it never wakes another device and
 * never announces a change. A device learns that another one played only by going to look, and
 * the moment to look is the opening of the game - right when the answer matters.
 *
 * Publishing, on the other hand, does not have to happen on the spot. Closing the game hands the
 * job to WorkManager ([FarmUploadWorker]), which waits for a network, survives the app being
 * killed, and tries again after a failure. The farm is safe on disk the whole time; only the cloud
 * lags behind.
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

    /** One pending upload at most: it reads the disk when it runs, so it always sends the latest farm. */
    private const val UPLOAD_WORK = "farm_upload"

    /**
     * Rounds of sending in one job. A second round only happens when the farm was saved again
     * while the first was on its way; the cap is there so a job can never keep the lock forever.
     */
    private const val MAX_ROUNDS = 3

    /** Opening, choosing and publishing must never overlap - two farms would race for one file. */
    private val lock = Mutex()

    sealed class OpenResult {
        /** Nothing to do: signed out, offline, or this device is already up to date. */
        data object Idle : OpenResult()

        /** The cloud farm is now on disk. The screen has to be rebuilt on top of it. */
        data object Applied : OpenResult()

        /** Both sides moved on. Only the player can say which farm is the real one. */
        data class Conflict(val local: FarmSyncFile, val remote: FarmSyncFile) : OpenResult()
    }

    /** What one upload attempt came to. Only [FAILED] is worth trying again. */
    enum class UploadOutcome {
        /** The cloud already has this farm, or there is no farm here, or nobody is signed in. */
        NOTHING_NEW,
        SENT,
        /** The cloud moved on while we played. Settled by the player at the next opening. */
        DEFERRED,
        /** Drive did not answer, or refused the write. */
        FAILED
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
                val remote = when (val read = client.readJsonFileChecked(SYNC_FILE)) {
                    // No answer is not an empty cloud. Play on the farm we have; nothing is decided.
                    ReadResult.Failed -> return@withLock OpenResult.Idle
                    ReadResult.NotFound -> null
                    is ReadResult.Found -> FarmSyncFile.fromJson(read.content)
                }
                if (remote == null) {
                    // Nothing usable up there: never published, or wiped from the cloud screen,
                    // whose warning promises that this device republishes what it holds. Ours is
                    // then the only copy, so it goes up - through the job, which retries if needed.
                    scheduleUpload(app)
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

    /** Called as the farm leaves the screen. The work itself happens in [FarmUploadWorker]. */
    fun onFarmClosed(context: Context) = scheduleUpload(context.applicationContext)

    /**
     * Queues the upload for whenever a network is there.
     *
     * KEEP, not REPLACE. A job already waiting reads the disk when it finally runs, so it will send
     * this session too - replacing it would gain nothing. And replacing a job that is running
     * cancels it, possibly between the moment Drive took the file and the moment we wrote down
     * that it did.
     */
    private fun scheduleUpload(context: Context) {
        val request = OneTimeWorkRequestBuilder<FarmUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UPLOAD_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * The job's entry point.
     *
     * It goes round again after a successful send: a farm saved while the first round was on its
     * way would otherwise wait for the next closing, because a job that is running does not take
     * a second one queued behind it.
     */
    suspend fun uploadPending(context: Context): UploadOutcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val client = driveClient(app) ?: return@withLock UploadOutcome.NOTHING_NEW
            var outcome = upload(app, client)
            var rounds = 1
            while (outcome == UploadOutcome.SENT && rounds < MAX_ROUNDS) {
                val again = upload(app, client)
                if (again == UploadOutcome.NOTHING_NEW) break
                outcome = again
                rounds++
            }
            outcome
        }
    }

    /** Publishes the local farm, unless there is nothing new or someone got there first. */
    private suspend fun upload(context: Context, client: GoogleDriveAppDataClient): UploadOutcome {
        val local = localState(context) ?: return UploadOutcome.NOTHING_NEW
        val prefs = prefs(context)
        val fingerprint = local.fingerprint()
        if (fingerprint == prefs.getString(KEY_SENT_HASH, null)) return UploadOutcome.NOTHING_NEW

        val base = prefs.getLong(KEY_BASE_SEQ, 0L)
        when (val read = client.readJsonFileChecked(SYNC_FILE)) {
            // We cannot know what is up there, so we write nothing. The job tries again later.
            ReadResult.Failed -> return UploadOutcome.FAILED
            // An empty cloud, or one wiped from the cloud screen: ours is the only copy.
            ReadResult.NotFound -> Unit
            is ReadResult.Found -> {
                val remote = FarmSyncFile.fromJson(read.content)
                if (remote != null && remote.seq != base) {
                    // Someone published while we were playing. Ours is no longer a continuation of
                    // the cloud farm, and writing over it would erase a whole session. We stay
                    // quiet: the local farm remains unsent, and the next opening turns the mess
                    // into a question the player can settle.
                    Log.d(TAG, "Cloud moved to ${remote.seq} while we played on $base - upload deferred")
                    return UploadOutcome.DEFERRED
                }
            }
        }
        val next = FarmSyncFile(
            seq        = nextSeq(base),
            format     = FarmSyncFile.formatOf(local),
            savedAt    = System.currentTimeMillis(),
            deviceName = SharedGameStats.deviceName(context),
            state      = local
        )
        // Sending and writing down that we sent are one act. If WorkManager stops the job in
        // between - the network drops, the constraint fails - Drive would hold our farm under a
        // number this device never recorded, and the next opening would ask the player to choose
        // between their farm and itself.
        return withContext(NonCancellable) {
            if (!client.writeJsonFile(SYNC_FILE, next.toJson())) return@withContext UploadOutcome.FAILED
            prefs.edit {
                putLong(KEY_BASE_SEQ, next.seq)
                putString(KEY_SENT_HASH, fingerprint)
            }
            Log.d(TAG, "Farm published as version ${next.seq} (${local.length / 1024} kB)")
            UploadOutcome.SENT
        }
    }

    /**
     * A number no device has used before.
     *
     * Versions are only ever compared for equality, never ordered, so what matters is that one
     * never comes back. A plain +1 would restart at 1 after the cloud is wiped, and could land on a
     * number another device still holds as its base - that device would then take a new farm for
     * the one it already has. A clock value cannot collide that way; the max keeps it moving
     * forward on a device whose clock was set back.
     */
    private fun nextSeq(base: Long): Long = maxOf(base + 1, System.currentTimeMillis())

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
     * Enough to tell two farms apart without keeping a second copy of a hundred-kilobyte save in
     * the preferences. Length and hash together: a hash alone collides once in four billion, and a
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
