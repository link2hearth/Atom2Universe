package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.AlbumFavoritesManager
import com.Atom2Universe.app.music.sync.model.AlbumFavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.SyncAlbumFavoriteEntry

/**
 * Merges album favorites from cloud with local data.
 *
 * Algorithm (Last-Write-Wins):
 * 1. Compare local and cloud album favorites by key (artistName|albumName)
 * 2. For conflicts, use the entry with the most recent timestamp
 * 3. Apply adds/removes based on timestamps
 *
 * An album favorite entry has:
 * - addedAt: When the album was added to favorites
 * - removedAt: When the album was removed (null if still active)
 *
 * An album favorite is active if: removedAt == null OR addedAt > removedAt
 */
object AlbumFavoritesMerger {

    private const val TAG = "AlbumFavoritesMerger"

    /**
     * Merges cloud album favorites with local data.
     */
    suspend fun merge(@Suppress("unused") context: Context, cloudFavorites: AlbumFavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) {
            Log.d(TAG, "No cloud album favorites to merge")
            return
        }

        val localFavorites = getLocalFavorites(context).associateBy { it.key }.toMutableMap()
        var addedCount = 0
        var removedCount = 0

        for (cloudEntry in cloudFavorites.favorites) {
            val localEntry = localFavorites[cloudEntry.key]

            if (localEntry == null) {
                // New entry from cloud
                if (cloudEntry.isActive()) {
                    // Add to local favorites
                    AlbumFavoritesManager.addFavoriteFromSync(
                        artistName = cloudEntry.artistName,
                        albumName = cloudEntry.albumName,
                        addedTimestamp = cloudEntry.addedAt
                    )
                    addedCount++
                }
            } else {
                // Conflict resolution: last-write-wins
                val cloudTimestamp = cloudEntry.getLastModifiedTimestamp()
                val localTimestamp = localEntry.getLastModifiedTimestamp()

                if (cloudTimestamp > localTimestamp) {
                    // Cloud is newer
                    if (cloudEntry.isActive() && !localEntry.isActive()) {
                        // Re-add removed favorite
                        AlbumFavoritesManager.addFavoriteFromSync(
                            artistName = cloudEntry.artistName,
                            albumName = cloudEntry.albumName,
                            addedTimestamp = cloudEntry.addedAt
                        )
                        addedCount++
                    } else if (!cloudEntry.isActive() && localEntry.isActive()) {
                        // Remove active favorite
                        AlbumFavoritesManager.removeFavoriteFromSync(
                            artistName = cloudEntry.artistName,
                            albumName = cloudEntry.albumName,
                            removedTimestamp = cloudEntry.removedAt ?: System.currentTimeMillis()
                        )
                        removedCount++
                    }
                }
            }
        }

        Log.d(TAG, "Merge complete: added $addedCount, removed $removedCount")
    }

    /**
     * Gets all local album favorites in sync format.
     */
    fun getLocalFavorites(@Suppress("unused") context: Context): List<SyncAlbumFavoriteEntry> {
        return try {
            AlbumFavoritesManager.getAllFavoritesForSync()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local album favorites", e)
            emptyList()
        }
    }
}
