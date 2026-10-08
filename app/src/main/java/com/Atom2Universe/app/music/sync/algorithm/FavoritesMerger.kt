package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicFavoritesManager
import com.Atom2Universe.app.music.sync.model.FavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.SyncFavoriteEntry

/**
 * Merges favorites from cloud with local data.
 *
 * Algorithm (Last-Write-Wins):
 * 1. Compare local and cloud favorites by metadata key
 * 2. For conflicts, use the entry with the most recent timestamp
 * 3. Apply adds/removes based on timestamps
 *
 * A favorite entry has:
 * - addedAt: When the favorite was added
 * - removedAt: When the favorite was removed (null if still active)
 *
 * A favorite is active if: removedAt == null OR addedAt > removedAt
 */
object FavoritesMerger {

    private const val TAG = "FavoritesMerger"

    /**
     * Merges cloud favorites with local data.
     */
    suspend fun merge(context: Context, cloudFavorites: FavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) {
            Log.d(TAG, "No cloud favorites to merge")
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
                    MusicFavoritesManager.addFavoriteByMetadata(
                        context = context,
                        artist = cloudEntry.artist,
                        title = cloudEntry.title,
                        album = cloudEntry.album,
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
                        MusicFavoritesManager.addFavoriteByMetadata(
                            context = context,
                            artist = cloudEntry.artist,
                            title = cloudEntry.title,
                            album = cloudEntry.album,
                            addedTimestamp = cloudEntry.addedAt
                        )
                        addedCount++
                    } else if (!cloudEntry.isActive() && localEntry.isActive()) {
                        // Remove active favorite
                        MusicFavoritesManager.removeFavoriteByMetadata(
                            context = context,
                            artist = cloudEntry.artist,
                            title = cloudEntry.title,
                            album = cloudEntry.album,
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
     * Gets all local favorites in sync format.
     */
    fun getLocalFavorites(context: Context): List<SyncFavoriteEntry> {
        return try {
            MusicFavoritesManager.getAllFavoritesForSync(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local favorites", e)
            emptyList()
        }
    }
}
