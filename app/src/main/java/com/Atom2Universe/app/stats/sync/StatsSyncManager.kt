package com.Atom2Universe.app.stats.sync

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.stats.data.DailySummaryBuilder
import com.Atom2Universe.app.stats.data.StatsDatabase
import com.Atom2Universe.app.stats.data.UsageSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Gestionnaire de synchronisation des statistiques avec Google Drive.
 * Synchronise les sessions d'utilisation entre tous les appareils.
 */
object StatsSyncManager {

    private const val TAG = "StatsSyncManager"
    private const val STATS_SYNC_FILE = "usage_sessions.json"

    private lateinit var appContext: Context
    private var isInitialized = false

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
     * Upload les sessions locales et download les sessions des autres appareils.
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

            // Créer le client Google Drive
            val driveClient = GoogleDriveAppDataClient(appContext, account)

            // 1. Télécharger les sessions existantes depuis le Drive
            Log.d(TAG, "Downloading stats from Drive...")
            val existingJson = driveClient.readJsonFile(STATS_SYNC_FILE)
            val existingSessions = if (existingJson != null) {
                try {
                    UsageSessionsSyncFile.fromJson(existingJson).sessions
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing existing stats", e)
                    emptyList()
                }
            } else {
                emptyList()
            }

            Log.d(TAG, "Found ${existingSessions.size} existing sessions on Drive")

            // 2. Récupérer uniquement les sessions créées localement (exclure les sessions importées d'autres appareils)
            val statsDb = StatsDatabase.getInstance(appContext)
            val usageSessionDao = statsDb.usageSessionDao()

            val localSessions = getLocalSessionsSince(usageSessionDao, 0)  // 0 = depuis le début, sessions locales uniquement

            Log.d(TAG, "Found ${localSessions.size} local sessions")

            // 3. Convertir les sessions locales en format sync avec UUID
            val localSyncSessions = localSessions.map { session ->
                SyncUsageSession(
                    id = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    moduleType = session.moduleType,
                    startTimestamp = session.startTimestamp,
                    endTimestamp = session.endTimestamp,
                    durationMs = session.durationMs,
                    trackTitle = session.trackTitle,
                    trackArtist = session.trackArtist,
                    trackAlbum = session.trackAlbum,
                    trackAlbumArtist = session.trackAlbumArtist,
                    midiFileName = session.midiFileName,
                    practiceScore = session.practiceScore,
                    radioStationName = session.radioStationName
                )
            }

            // 4. Merger les sessions (éviter les doublons basés sur timestamp + device)
            val mergedSessions = mergeSessions(existingSessions, localSyncSessions, deviceId)

            Log.d(TAG, "Merged to ${mergedSessions.size} total sessions")

            // 5. Uploader le fichier mergé vers Drive
            val syncFile = UsageSessionsSyncFile(
                version = 1,
                lastModified = System.currentTimeMillis(),
                sessions = mergedSessions
            )

            val uploaded = driveClient.writeJsonFile(STATS_SYNC_FILE, syncFile.toJson())

            if (!uploaded) {
                return@withContext SyncResult(false, "Failed to upload to Drive")
            }

            // 6. Importer les nouvelles sessions des autres appareils dans la base locale
            val importedCount = importRemoteSessions(mergedSessions, deviceId)

            // 7. Recalculer les résumés journaliers si des sessions ont été importées
            if (importedCount > 0) {
                Log.d(TAG, "Rebuilding daily summaries after importing $importedCount sessions...")
                val dailySummaryDao = statsDb.dailySummaryDao()
                val builder = DailySummaryBuilder(usageSessionDao, dailySummaryDao)
                builder.backfillAllSummaries()
            }

            Log.d(TAG, "Stats sync complete: imported $importedCount remote sessions")

            SyncResult(true, "Synced successfully", importedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing stats", e)
            SyncResult(false, "Error: ${e.message}")
        }
    }

    /**
     * Récupère uniquement les sessions créées localement (sourceDeviceId IS NULL).
     * Exclut les sessions importées d'autres appareils pour éviter de les réexporter sous le mauvais deviceId.
     */
    private suspend fun getLocalSessionsSince(
        dao: com.Atom2Universe.app.stats.data.UsageSessionDao,
        sinceTimestamp: Long
    ): List<UsageSessionEntity> {
        return dao.getLocalSessionsSince(sinceTimestamp)
    }

    /**
     * Merge les sessions locales et distantes, élimine les doublons.
     * Deux sessions sont considérées comme doublons si elles ont:
     * - Le même deviceId
     * - Des timestamps très proches (< 1 seconde d'écart)
     */
    private fun mergeSessions(
        remote: List<SyncUsageSession>,
        local: List<SyncUsageSession>,
        currentDeviceId: String
    ): List<SyncUsageSession> {
        val allSessions = (remote + local).toMutableList()

        // Filtrer les doublons du device courant
        val seenKeys = mutableSetOf<String>()
        val uniqueSessions = allSessions.filter { session ->
            val key = if (session.deviceId == currentDeviceId) {
                // Pour le device courant, utiliser un key basé sur timestamp
                "${session.deviceId}_${session.startTimestamp / 1000}"
            } else {
                // Pour les autres devices, utiliser l'ID unique
                session.id
            }

            if (seenKeys.contains(key)) {
                false
            } else {
                seenKeys.add(key)
                true
            }
        }

        // Trier par timestamp décroissant (garder tout l'historique)
        return uniqueSessions.sortedByDescending { it.endTimestamp }
    }

    /**
     * Importe les sessions des autres appareils dans la base locale.
     * Ne réimporte pas les sessions du device courant.
     */
    private suspend fun importRemoteSessions(
        allSessions: List<SyncUsageSession>,
        currentDeviceId: String
    ): Int {
        val statsDb = StatsDatabase.getInstance(appContext)
        val usageSessionDao = statsDb.usageSessionDao()

        // Filtrer uniquement les sessions des autres appareils
        val remoteSessions = allSessions.filter { it.deviceId != currentDeviceId }

        var importedCount = 0

        remoteSessions.forEach { syncSession ->
            val entity = UsageSessionEntity(
                id = 0,  // Auto-generate
                moduleType = syncSession.moduleType,
                startTimestamp = syncSession.startTimestamp,
                endTimestamp = syncSession.endTimestamp,
                durationMs = syncSession.durationMs,
                trackTitle = syncSession.trackTitle,
                trackArtist = syncSession.trackArtist,
                trackAlbum = syncSession.trackAlbum,
                trackAlbumArtist = syncSession.trackAlbumArtist,
                midiFileName = syncSession.midiFileName,
                practiceScore = syncSession.practiceScore,
                radioStationName = syncSession.radioStationName,
                sourceDeviceId = syncSession.deviceId  // Marquer l'appareil d'origine pour ne pas réexporter sous le mauvais deviceId
            )

            // insertSessionIgnoreConflict retourne -1 si la session existe déjà (contrainte unique moduleType+startTimestamp+endTimestamp)
            val inserted = usageSessionDao.insertSessionIgnoreConflict(entity)
            if (inserted != -1L) {
                importedCount++
            }
        }

        return importedCount
    }

    /**
     * Résultat d'une synchronisation.
     */
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val importedCount: Int = 0
    )
}
