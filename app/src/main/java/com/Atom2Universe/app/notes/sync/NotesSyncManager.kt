package com.Atom2Universe.app.notes.sync

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.InvalidationTracker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient.ReadResult
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.notes.data.NotesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Les notes sur Drive : un seul fichier, fusionné note par note (voir [NotesMerge]).
 *
 * Trois chemins y mènent :
 * - le bouton « Sync Google » de la bibliothèque ([sync]), qui marche dès qu'on est connecté ;
 * - la sync automatique ([syncIfEnabled]) : à l'ouverture des notes, deux minutes après la dernière
 *   modification, et dans la sync générale ([CloudSyncManager.syncNow]). Celle-là suit l'interrupteur
 *   de synchronisation de l'écran Cloud ;
 * - rien d'autre : jamais depuis l'éditeur, jamais en plein milieu d'une frappe.
 */
object NotesSyncManager {

    private const val TAG = "NotesSyncManager"

    /** Nom sur Drive. Public : l'écran Cloud le range dans « Notes » par ce nom. */
    const val SYNC_FILE = "notes_sync.json"

    private const val PREFS = "notes_sync"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_AUTO = "last_auto"
    private const val WORK_DEBOUNCED = "notes_sync_debounced"

    /** Après une modification, on attend ce délai (renouvelé à chaque modification) avant d'envoyer. */
    private const val DEBOUNCE_MINUTES = 2L
    private const val DEBOUNCE_MS = DEBOUNCE_MINUTES * 60_000L

    /** Des modifications sans fin ne repoussent pas l'envoi au-delà de ça après la première (sous les 10 min d'un travail). */
    private const val MAX_WAIT_MS = 7 * 60_000L

    /** La présence d'un compte n'est revérifiée qu'au plus une fois par minute, pas à chaque écriture. */
    private const val ACCOUNT_CHECK_MS = 60_000L

    /** À l'ouverture des notes, pas de nouvelle sync si la dernière date de moins que ça. */
    private const val OPEN_THROTTLE_MS = 2 * 60_000L

    private val lock = Mutex()

    @Volatile
    private var watching = false

    enum class Outcome { SYNCED, NOT_SIGNED_IN, FAILED, TOO_NEW }

    /** Le résultat d'une sync, avec de quoi dire à l'écran s'il y a du nouveau ici. */
    class Report(val outcome: Outcome, val changedLocal: Boolean = false)

    suspend fun sync(context: Context): Report = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val signIn = GoogleSignInManager(app)
            if (!signIn.isSignedIn()) return@withLock Report(Outcome.NOT_SIGNED_IN)
            val account = signIn.getSignedInAccount() ?: return@withLock Report(Outcome.NOT_SIGNED_IN)
            val client = GoogleDriveAppDataClient(app, account)
            val engine = NotesSyncEngine(
                store = NotesSyncStore(NotesDatabase.getInstance(app)),
                cloud = DriveNotesCloud(client),
                stateStore = PrefsStateStore(app),
                deviceName = SharedGameStats.deviceName(app),
                conflictSuffix = app.getString(R.string.notes_conflict_suffix),
            )
            try {
                when (val r = engine.sync()) {
                    is NotesSyncEngine.Result.Done -> Report(Outcome.SYNCED, r.changedLocal)
                    NotesSyncEngine.Result.Failed -> Report(Outcome.FAILED)
                    NotesSyncEngine.Result.TooNew -> Report(Outcome.TOO_NEW)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Notes sync failed", e)
                Report(Outcome.FAILED)
            }
        }
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /** Lance [sync] dans le scope de l'application et rend le résultat sur le fil principal. */
    fun launchSync(context: Context, onDone: (Report) -> Unit) {
        val app = context.applicationContext
        appScope.launch {
            val report = sync(app)
            withContext(Dispatchers.Main) { onDone(report) }
        }
    }

    /**
     * La sync automatique : seulement si l'interrupteur de l'écran Cloud est allumé.
     * @param minIntervalMs ne rien faire si une sync automatique date de moins que ça
     */
    suspend fun syncIfEnabled(context: Context, minIntervalMs: Long = 0L): Report? {
        val app = context.applicationContext
        CloudSyncManager.init(app)
        if (!CloudSyncManager.isSyncEnabled()) return null
        val prefs = prefs(app)
        if (minIntervalMs > 0 && System.currentTimeMillis() - prefs.getLong(KEY_LAST_AUTO, 0L) < minIntervalMs) return null
        val report = sync(app)
        if (report.outcome == Outcome.SYNCED) prefs.edit { putLong(KEY_LAST_AUTO, System.currentTimeMillis()) }
        return report
    }

    /** À l'ouverture de l'écran des notes. */
    suspend fun syncOnOpen(context: Context): Report? = syncIfEnabled(context, OPEN_THROTTLE_MS)

    /**
     * Surveille la base : toute écriture dans les notes (la leur, les groupes, les tags) programme une sync
     * [DEBOUNCE_MINUTES] minutes après la dernière écriture. Un seul observateur, posé une fois.
     * Les écritures de la synchro elle-même ne comptent pas : sinon chaque synchro en programmait une autre.
     */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        NotesDatabase.getInstance(app).invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("notes", "note_groups", "tags", "tag_categories", "note_tags") {
                override fun onInvalidated(tables: Set<String>) {
                    if (!NotesSyncStore.isOwnWrite()) scheduleSoon(app)
                }
            },
        )
    }

    /** La dernière et la première écriture pas encore envoyées ([System.currentTimeMillis]). */
    @Volatile
    private var lastChangeAt = 0L
    @Volatile
    private var firstChangeAt = 0L
    /** Un travail est déjà programmé et n'a pas encore commencé sa sync : une écriture de plus n'a qu'à avancer [lastChangeAt]. */
    @Volatile
    private var pending = false

    @Volatile
    private var hasAccount = false
    @Volatile
    private var signedInCheckedAt = 0L

    private fun signedIn(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - signedInCheckedAt > ACCOUNT_CHECK_MS) {
            hasAccount = GoogleSignInManager(context).isSignedIn()
            signedInCheckedAt = now
        }
        return hasAccount
    }

    /**
     * Un vrai anti-rebond, sans jamais annuler un travail : chaque écriture note sa date, et un seul travail est
     * programmé à la fois. Le travail relit ces dates en démarrant ([waitForQuiet]). Une écriture pendant
     * une sync en cours ajoute un travail à la suite (APPEND_OR_REPLACE), au lieu d'interrompre celle-ci.
     */
    private fun scheduleSoon(context: Context) {
        val now = System.currentTimeMillis()
        lastChangeAt = now
        if (pending) return
        // Pas de compte, pas de travail à programmer ; l'interrupteur, lui, est relu par le travail.
        if (!signedIn(context)) return
        pending = true
        firstChangeAt = now
        val request = OneTimeWorkRequestBuilder<NotesSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_DEBOUNCED, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Au démarrage du travail : attend que les écritures se calment ([DEBOUNCE_MS] après la dernière), sans
     * dépasser [MAX_WAIT_MS] après la première. Ensuite, toute nouvelle écriture programmera un nouveau travail.
     */
    internal suspend fun waitForQuiet() {
        try {
            while (true) {
                val wait = minOf(lastChangeAt + DEBOUNCE_MS, firstChangeAt + MAX_WAIT_MS) - System.currentTimeMillis()
                if (wait <= 0) break
                delay(wait)
            }
        } finally {
            // Avant la photo de la sync : une écriture qui arrive après sera dans un prochain travail.
            pending = false
        }
    }

    // ─── Branchements ─────────────────────────────────────────────────────────

    private class DriveNotesCloud(private val client: GoogleDriveAppDataClient) : NotesCloud {
        override suspend fun read(): NotesCloudRead = when (val r = client.readJsonFileChecked(SYNC_FILE)) {
            is ReadResult.Found -> NotesCloudRead.Found(r.content)
            ReadResult.NotFound -> NotesCloudRead.Missing
            ReadResult.Failed -> NotesCloudRead.Failed
        }

        override suspend fun write(text: String): Boolean = client.writeJsonFile(SYNC_FILE, text)
    }

    private class PrefsStateStore(private val context: Context) : NotesStateStore {
        override fun load(): NotesSyncState = NotesSyncState.fromJson(prefs(context).getString(KEY_STATE, null))
        override fun save(state: NotesSyncState) = prefs(context).edit { putString(KEY_STATE, state.toJson()) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Le travail programmé après une modification : une sync automatique des notes, si elle est permise. */
class NotesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NotesSyncManager.waitForQuiet()
        val report = NotesSyncManager.syncIfEnabled(applicationContext)
        return if (report?.outcome == NotesSyncManager.Outcome.FAILED && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
