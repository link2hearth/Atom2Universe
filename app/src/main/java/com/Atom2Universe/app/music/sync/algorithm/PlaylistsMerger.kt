package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicPlaylistManager
import com.Atom2Universe.app.music.sync.model.PlaylistsSyncFile
import com.Atom2Universe.app.music.sync.model.SyncPlaylistEntry

/**
 * Merges playlists from cloud with local data.
 *
 * Algorithm (Last-Write-Wins):
 * 1. Compare local and cloud playlists by ID
 * 2. For conflicts, use the entry with the most recent timestamp
 * 3. Apply creates/updates/deletes based on timestamps
 *
 * A playlist entry has:
 * - createdAt: When the playlist was created
 * - modifiedAt: When the playlist was last modified (name change, tracks change)
 * - deletedAt: When the playlist was deleted (null if still active)
 *
 * A playlist is active if: deletedAt == null OR modifiedAt > deletedAt
 */
object PlaylistsMerger {

    private const val TAG = "PlaylistsMerger"

    /**
     * Merges cloud playlists with local data.
     */
    suspend fun merge(context: Context, cloudPlaylists: PlaylistsSyncFile) {
        if (cloudPlaylists.playlists.isEmpty()) {
            Log.d(TAG, "No cloud playlists to merge")
            return
        }

        val localPlaylists = getLocalPlaylists(context).associateBy { it.id }.toMutableMap()
        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0

        for (cloudEntry in cloudPlaylists.playlists) {
            val localEntry = localPlaylists[cloudEntry.id]

            if (localEntry == null) {
                // New playlist from cloud
                if (cloudEntry.isActive()) {
                    // Create locally
                    MusicPlaylistManager.createPlaylistFromSync(
                        id = cloudEntry.id,
                        name = cloudEntry.name,
                        tracks = cloudEntry.tracks,
                        createdAt = cloudEntry.createdAt,
                        modifiedAt = cloudEntry.modifiedAt
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
                        // Re-create deleted playlist
                        MusicPlaylistManager.createPlaylistFromSync(
                            id = cloudEntry.id,
                            name = cloudEntry.name,
                            tracks = cloudEntry.tracks,
                            createdAt = cloudEntry.createdAt,
                            modifiedAt = cloudEntry.modifiedAt
                        )
                        addedCount++
                    } else if (!cloudEntry.isActive() && localEntry.isActive()) {
                        // Delete active playlist
                        MusicPlaylistManager.deletePlaylistFromSync(
                            id = cloudEntry.id,
                            deletedTimestamp = cloudEntry.deletedAt ?: System.currentTimeMillis()
                        )
                        deletedCount++
                    } else if (cloudEntry.isActive() && localEntry.isActive()) {
                        // Update existing playlist (name or tracks changed)
                        MusicPlaylistManager.updatePlaylistFromSync(
                            id = cloudEntry.id,
                            name = cloudEntry.name,
                            tracks = cloudEntry.tracks,
                            modifiedAt = cloudEntry.modifiedAt
                        )
                        updatedCount++
                    }
                }
            }
        }

        Log.d(TAG, "Merge complete: added $addedCount, updated $updatedCount, deleted $deletedCount")
    }

    /**
     * Gets all local playlists in sync format.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getLocalPlaylists(context: Context): List<SyncPlaylistEntry> {
        return try {
            MusicPlaylistManager.getAllPlaylistsForSync()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local playlists", e)
            emptyList()
        }
    }
}
