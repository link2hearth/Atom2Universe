package com.Atom2Universe.app.stats.sync

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.Atom2Universe.app.music.sync.DriveFileInfo
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.stats.data.DailySummaryBuilder
import com.Atom2Universe.app.stats.data.StatsDatabase
import com.Atom2Universe.app.stats.data.UsageSessionDao
import com.Atom2Universe.app.stats.data.UsageSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gestionnaire de synchronisation des statistiques avec Google Drive.
 * Synchronise les sessions d'utilisation entre tous les appareils.
 *
 * Chaque appareil publie ses seules sessions dans son propre fichier ([filenameFor]), que lui seul
 * écrit. Personne ne réécrit donc les sessions d'un autre : une lecture ratée ou un fichier illisible
 * ne peut rien effacer, l'import attend simplement la synchro suivante.
 */
object StatsSyncManager {

    private const val TAG = "StatsSyncManager"

    /** Début du nom des fichiers par appareil. Public : l'écran Cloud les range dans « Statistiques » par lui. */
    const val FILE_PREFIX = "usage_sessions_"

    private const val PREFS = "stats_sync"
    private const val KEY_SENT_COUNT = "sent_count"
    private const val KEY_SENT_LATEST = "sent_latest"

    /** Devant le nom d'un fichier d'un autre appareil : la date Drive à laquelle il a été fusionné ici. */
    private const val KEY_MERGED_PREFIX = "merged:"

    private lateinit var appContext: Context
    private var isInitialized = false

    /** Même étiquette que les mesures de CloudSyncManager : un seul filtre logcat pour tout. */
    private const val TIMING_TAG = "SyncTiming"

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            Log.i(TIMING_TAG, "$label : ${SystemClock.elapsedRealtime() - start} ms")
        }
    }

    fun filenameFor(deviceId: String) = "$FILE_PREFIX$deviceId.json"

    /**
     * Initialise le gestionnaire de sync.
     */
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        Log.d(TAG, "StatsSyncManager initialized")
    }

    /**
     * Synchronise les statistiques avec Google Drive.
     * Importe les sessions des autres appareils dont le fichier a changé, puis publie les nôtres
     * s'il y en a de nouvelles.
     */
    suspend fun syncStats(): SyncResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext SyncResult(false, "Not initialized")
        }

        try {
            // Vérifier que l'utilisateur est connecté
            val googleSignInManager = GoogleSignInManager(appContext)
            if (!googleSignInManager.isSignedIn()) {
                return@withContext SyncResult(false, "Not signed in to Google")
            }

            val account = googleSignInManager.getSignedInAccount()
            if (account == null) {
                return@withContext SyncResult(false, "No Google account")
            }

            // Récupérer le device ID depuis la base de données Music
            val musicDb = MusicDatabase.getInstance(appContext)
            val syncMetadataDao = musicDb.syncMetadataDao()
            val deviceId = syncMetadataDao.getDeviceId()
            if (deviceId == null) {
                return@withContext SyncResult(false, "No device ID")
            }

            val driveClient = GoogleDriveAppDataClient(appContext, account)
            val statsDb = StatsDatabase.getInstance(appContext)

            // 1. Les fichiers des appareils. Sans réponse de Drive, on ne sait même pas si le nôtre
            //    est là-haut : rien n'est importé ni écrit.
            val files = timed("stats : liste des fichiers") {
                driveClient.listFileDetailsChecked("name contains '$FILE_PREFIX'")
            }?.filter { it.name.startsWith(FILE_PREFIX) }
                ?: return@withContext SyncResult(false, "Drive did not answer")

            // 2. Les sessions des autres appareils
            val importedCount = timed("stats : import des autres appareils") {
                importForeignFiles(driveClient, files, deviceId, statsDb)
            }

            // 3. Les nôtres
            val uploaded = timed("stats : upload") {
                publishOwnFile(driveClient, files, deviceId, statsDb.usageSessionDao())
            }
            if (!uploaded) {
                return@withContext SyncResult(false, "Failed to upload to Drive", importedCount)
            }

            Log.d(TAG, "Stats sync complete: imported $importedCount remote sessions")

            SyncResult(true, "Synced successfully", importedCount)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing stats", e)
            SyncResult(false, "Error: ${e.message}")
        }
    }

    /**
     * Importe les fichiers des autres appareils, sauf ceux dont la date Drive n'a pas bougé depuis
     * leur dernière fusion : les relire n'ajouterait rien. Un fichier illisible est sauté et sera
     * relu la prochaine fois. Seuls les jours qui reçoivent des sessions voient leur résumé recalculé.
     *
     * @return le nombre de sessions réellement ajoutées ici
     */
    private suspend fun importForeignFiles(
        client: GoogleDriveAppDataClient,
        files: List<DriveFileInfo>,
        deviceId: String,
        statsDb: StatsDatabase
    ): Int {
        val prefs = prefs()
        val ownFile = filenameFor(deviceId)
        val dao = statsDb.usageSessionDao()
        val zone = ZoneId.systemDefault()
        val touchedDays = HashSet<LocalDate>()
        var importedCount = 0

        for (file in files) {
            if (file.name == ownFile) continue
            val key = KEY_MERGED_PREFIX + file.name
            if (file.modifiedTime > 0L && prefs.getLong(key, -1L) == file.modifiedTime) continue

            val content = client.readJsonFileById(file.id, file.name) ?: continue
            val sessions = try {
                UsageSessionsSyncFile.fromJson(content).sessions
            } catch (e: Exception) {
                Log.e(TAG, "Unreadable ${file.name}, skipped", e)
                continue
            }

            val entities = sessions.map { it.toImportedEntity() }
            val ids = dao.insertSessionsIgnoreConflict(entities)
            entities.forEachIndexed { i, entity ->
                if (ids.getOrNull(i) != -1L) {
                    importedCount++
                    touchedDays += Instant.ofEpochMilli(entity.startTimestamp).atZone(zone).toLocalDate()
                }
            }
            prefs.edit { putLong(key, file.modifiedTime) }
        }

        // Un fichier disparu du cloud n'a plus de date à retenir.
        val listed = files.mapTo(HashSet()) { KEY_MERGED_PREFIX + it.name }
        val stale = prefs.all.keys.filter { it.startsWith(KEY_MERGED_PREFIX) && it !in listed }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }

        if (touchedDays.isNotEmpty()) {
            val builder = DailySummaryBuilder(dao, statsDb.dailySummaryDao())
            timed("stats : résumés de ${touchedDays.size} jour(s)") {
                for (day in touchedDays) {
                    builder.updateSummaryForDay(day.atStartOfDay(zone).toInstant().toEpochMilli())
                }
            }
        }
        return importedCount
    }

    /**
     * Publie nos sessions locales (sourceDeviceId IS NULL : les importées ne repartent jamais sous notre
     * nom), seulement s'il y en a de nouvelles depuis le dernier envoi, ou si notre fichier a disparu
     * du cloud (effacé depuis l'écran Cloud).
     *
     * @return false si l'envoi a échoué
     */
    private suspend fun publishOwnFile(
        client: GoogleDriveAppDataClient,
        files: List<DriveFileInfo>,
        deviceId: String,
        dao: UsageSessionDao
    ): Boolean {
        val filename = filenameFor(deviceId)
        val onCloud = files.any { it.name == filename }
        val summary = dao.getLocalSessionsSummary()
        val prefs = prefs()
        if (!onCloud && summary.sessionCount == 0) return true
        if (onCloud &&
            prefs.getInt(KEY_SENT_COUNT, -1) == summary.sessionCount &&
            prefs.getLong(KEY_SENT_LATEST, -1L) == summary.latestEnd
        ) {
            Log.d(TAG, "No new local session since last upload (${summary.sessionCount}), skipping")
            return true
        }

        val localSessions = dao.getLocalSessionsSince(0)
        val syncFile = UsageSessionsSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            sessions = localSessions.map { it.toSync(deviceId) }
        )
        Log.i(TIMING_TAG, "stats : ${localSessions.size} sessions locales envoyées")
        if (!client.writeJsonFile(filename, syncFile.toJson())) return false

        prefs.edit {
            putInt(KEY_SENT_COUNT, localSessions.size)
            putLong(KEY_SENT_LATEST, localSessions.maxOfOrNull { it.endTimestamp } ?: 0L)
        }
        return true
    }

    private fun UsageSessionEntity.toSync(deviceId: String) = SyncUsageSession(
        // Stable d'un envoi à l'autre : l'identifiant local suffit, l'appareil est dans le nom du fichier.
        id = "${deviceId}_$id",
        deviceId = deviceId,
        moduleType = moduleType,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationMs = durationMs,
        trackTitle = trackTitle,
        trackArtist = trackArtist,
        trackAlbum = trackAlbum,
        trackAlbumArtist = trackAlbumArtist,
        midiFileName = midiFileName,
        practiceScore = practiceScore,
        radioStationName = radioStationName,
        readingTitle = readingTitle
    )

    private fun SyncUsageSession.toImportedEntity() = UsageSessionEntity(
        id = 0,  // Auto-generate
        moduleType = moduleType,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationMs = durationMs,
        trackTitle = trackTitle,
        trackArtist = trackArtist,
        trackAlbum = trackAlbum,
        trackAlbumArtist = trackAlbumArtist,
        midiFileName = midiFileName,
        practiceScore = practiceScore,
        radioStationName = radioStationName,
        readingTitle = readingTitle,
        sourceDeviceId = deviceId  // Marquer l'appareil d'origine pour ne pas réexporter sous le mauvais deviceId
    )

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Résultat d'une synchronisation.
     */
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val importedCount: Int = 0
    )
}
