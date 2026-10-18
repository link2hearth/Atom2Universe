package com.Atom2Universe.app.readingprogress.data

import android.content.Context
import com.Atom2Universe.app.music.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Accès à la progression de lecture locale (livres/BD), utilisée pour la reprise
 * de lecture et synchronisée entre appareils via ReadingProgressSyncManager.
 */
class ReadingProgressRepository(context: Context) {

    private val dao = ReadingProgressDatabase.getInstance(context).readingProgressDao()

    /**
     * Met à jour la progression locale d'un titre et déclenche une sync différée
     * (comme les autres données locales : favoris, playlists…).
     */
    suspend fun updateProgress(mediaType: String, title: String, progressPercent: Float) {
        withContext(Dispatchers.IO) {
            dao.upsert(
                ReadingProgressEntity(
                    bookKey = readingProgressKey(mediaType, title),
                    mediaType = mediaType,
                    title = title,
                    progressPercent = progressPercent.coerceIn(0f, 1f),
                    lastReadTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = null
                )
            )
        }
        CloudSyncManager.triggerDebouncedSync()
    }

    suspend fun getProgress(mediaType: String, title: String): ReadingProgressEntity? = withContext(Dispatchers.IO) {
        dao.getByKey(readingProgressKey(mediaType, title))
    }
}
