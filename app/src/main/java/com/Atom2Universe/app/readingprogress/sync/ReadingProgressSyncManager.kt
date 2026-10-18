package com.Atom2Universe.app.readingprogress.sync

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.readingprogress.data.ReadingProgressDatabase
import com.Atom2Universe.app.readingprogress.data.ReadingProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synchronise la progression de lecture (livres/BD) avec Google Drive, sur le même modèle
 * que StatsSyncManager : un fichier JSON dans l'AppData Drive, merge par clé avec la
 * règle "dernier écrivain gagne" (comparaison de lastReadTimestamp).
 */
object ReadingProgressSyncManager {

    private const val TAG = "ReadingProgressSync"
    private const val SYNC_FILE = "reading_progress.json"

    private lateinit var appContext: Context
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
    }

    suspend fun syncProgress(): SyncResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext SyncResult(false, "Not initialized")
        }

        try {
            val googleSignInManager = GoogleSignInManager(appContext)
            if (!googleSignInManager.isSignedIn()) {
                return@withContext SyncResult(false, "Not signed in to Google")
            }
            val account = googleSignInManager.getSignedInAccount()
                ?: return@withContext SyncResult(false, "No Google account")

            val deviceId = MusicDatabase.getInstance(appContext).syncMetadataDao().getDeviceId()
                ?: return@withContext SyncResult(false, "No device ID")

            val driveClient = GoogleDriveAppDataClient(appContext, account)
            val dao = ReadingProgressDatabase.getInstance(appContext).readingProgressDao()

            // 1. Télécharger la progression existante depuis le Drive
            val existingJson = driveClient.readJsonFile(SYNC_FILE)
            val remoteItems = if (existingJson != null) {
                try {
                    ReadingProgressSyncFile.fromJson(existingJson).items
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing existing progress", e)
                    emptyList()
                }
            } else {
                emptyList()
            }

            // 2. Récupérer la progression locale (état courant, une ligne par titre)
            val localEntries = dao.getAll()
            val localItems = localEntries.map { entry ->
                SyncReadingProgress(
                    bookKey = entry.bookKey,
                    deviceId = entry.sourceDeviceId ?: deviceId,
                    mediaType = entry.mediaType,
                    title = entry.title,
                    progressPercent = entry.progressPercent,
                    lastReadTimestamp = entry.lastReadTimestamp
                )
            }

            // 3. Merger par bookKey : le dernier écrivain (lastReadTimestamp le plus récent) gagne
            val merged = (remoteItems + localItems)
                .groupBy { it.bookKey }
                .mapValues { (_, items) -> items.maxBy { it.lastReadTimestamp } }
                .values
                .toList()

            // 4. Uploader la progression mergée
            val syncFile = ReadingProgressSyncFile(
                version = 1,
                lastModified = System.currentTimeMillis(),
                items = merged
            )
            val uploaded = driveClient.writeJsonFile(SYNC_FILE, syncFile.toJson())
            if (!uploaded) {
                return@withContext SyncResult(false, "Failed to upload to Drive")
            }

            // 5. Appliquer le résultat mergé en local (met à jour les titres où le distant a gagné)
            merged.forEach { item ->
                dao.upsert(
                    ReadingProgressEntity(
                        bookKey = item.bookKey,
                        mediaType = item.mediaType,
                        title = item.title,
                        progressPercent = item.progressPercent,
                        lastReadTimestamp = item.lastReadTimestamp,
                        sourceDeviceId = if (item.deviceId == deviceId) null else item.deviceId
                    )
                )
            }

            SyncResult(true, "Synced successfully", merged.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing reading progress", e)
            SyncResult(false, "Error: ${e.message}")
        }
    }

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val itemCount: Int = 0
    )
}
