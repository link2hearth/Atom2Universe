package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.ArtistCustomizationManager
import com.Atom2Universe.app.music.sync.model.ArtistFavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.SyncArtistFavoriteEntry

/**
 * Merges artist favorites from cloud with local data.
 *
 * Algorithm (Last-Write-Wins):
 * 1. Compare local and cloud artist favorites by key (artistName lowercased)
 * 2. For conflicts, use the entry with the most recent timestamp
 * 3. Apply adds/removes based on timestamps
 *
 * An artist favorite entry has:
 * - addedAt: When the artist was added to favorites
 * - removedAt: When the artist was removed (null if still active)
 *
 * An artist favorite is active if: removedAt == null OR addedAt > removedAt
 *
 * Note: This handles BOTH regular artists AND album artists since they share
 * the same ArtistCustomizationManager.
 */
object ArtistFavoritesMerger {

    private const val TAG = "ArtistFavoritesMerger"

    /**
     * Merges cloud artist favorites with local data.
     */
    suspend fun merge(@Suppress("unused") context: Context, cloudFavorites: ArtistFavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) {
            Log.d(TAG, "No cloud artist favorites to merge")
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
                    ArtistCustomizationManager.addFavoriteFromSync(
                        artistName = cloudEntry.artistName,
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
                        ArtistCustomizationManager.addFavoriteFromSync(
                            artistName = cloudEntry.artistName,
                            addedTimestamp = cloudEntry.addedAt
                        )
                        addedCount++
                    } else if (!cloudEntry.isActive() && localEntry.isActive()) {
                        // Remove active favorite
                        ArtistCustomizationManager.removeFavoriteFromSync(
                            artistName = cloudEntry.artistName,
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
     * Gets all local artist favorites in sync format.
     */
    fun getLocalFavorites(@Suppress("unused") context: Context): List<SyncArtistFavoriteEntry> {
        return try {
            ArtistCustomizationManager.getAllFavoritesForSync()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local artist favorites", e)
            emptyList()
        }
    }
}
