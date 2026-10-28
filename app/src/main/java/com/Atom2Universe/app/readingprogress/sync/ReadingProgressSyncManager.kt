package com.Atom2Universe.app.readingprogress.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient.ReadResult
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.readingprogress.data.ReadingProgressDatabase
import com.Atom2Universe.app.readingprogress.data.ReadingProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synchronise la progression de lecture (livres/BD) avec Google Drive : un fichier JSON
 * partagé dans l'AppData Drive, merge par clé avec la règle "dernier écrivain gagne"
 * (comparaison de lastReadTimestamp).
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
            val db = ReadingProgressDatabase.getInstance(appContext)
            val dao = db.readingProgressDao()

            // 1. Télécharger la progression existante depuis le Drive. Une panne ou un fichier
            //    illisible n'est pas un cloud vide : on n'écrit rien, ni ici ni là-haut.
            val remoteItems = when (val read = driveClient.readJsonFileChecked(SYNC_FILE)) {
                ReadResult.Failed -> return@withContext SyncResult(false, "Drive did not answer")
                ReadResult.NotFound -> emptyList()
                is ReadResult.Found -> try {
                    ReadingProgressSyncFile.fromJson(read.content).items
                } catch (e: Exception) {
                    Log.e(TAG, "Unreadable $SYNC_FILE, nothing written", e)
                    return@withContext SyncResult(false, "Unreadable cloud file")
                }
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

            // 4. Uploader la progression mergée, seulement si elle dit autre chose que le cloud
            if (merged.toSet() != remoteItems.toSet()) {
                val syncFile = ReadingProgressSyncFile(
                    version = 1,
                    lastModified = System.currentTimeMillis(),
                    items = merged
                )
                val uploaded = driveClient.writeJsonFile(SYNC_FILE, syncFile.toJson())
                if (!uploaded) {
                    return@withContext SyncResult(false, "Failed to upload to Drive")
                }
            }

            // 5. Appliquer en local les titres où le distant a gagné. Relu dans la transaction : une
            //    page tournée pendant l'envoi est plus récente, elle reste.
            val localSet = localItems.toSet()
            val remoteWins = merged.filter { it !in localSet }
            if (remoteWins.isNotEmpty()) {
                db.withTransaction {
                    for (item in remoteWins) {
                        val current = dao.getByKey(item.bookKey)
                        if (current != null && current.lastReadTimestamp >= item.lastReadTimestamp) continue
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
                }
            }

            SyncResult(true, "Synced successfully", merged.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
