package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicPlayCountManager
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PlayCountEntry
import com.Atom2Universe.app.music.sync.model.PlayCountDeltaFile

/**
 * Merges play count deltas from cloud with local data.
 *
 * Algorithm:
 * 1. Sum all deltas from all devices for each track
 * 2. Calculate: earnedPlayCount (local plays only) + cloud deltas (other devices)
 * 3. Apply MAX rule to ensure counts never decrease
 *
 * IMPORTANT: Uses earnedPlayCount (not playCount) as the base to avoid double-counting
 * when a delta file is re-downloaded after being updated the same day.
 *
 * Example:
 * - Local earnedPlayCount: 5 plays (on this device)
 * - Cloud delta from device A: +3
 * - Cloud delta from device B: +2
 * - Result: MAX(currentPlayCount, 5 + 3 + 2) = MAX(currentPlayCount, 10)
 */
object PlayCountMerger {

    private const val TAG = "PlayCountMerger"

    /**
     * Aggregated delta info including track metadata.
     */
    private data class AggregatedDelta(
        val key: String,
        val artist: String,
        val title: String,
        val album: String,
        var delta: Long
    )

    /**
     * Merges cloud deltas with local play counts.
     */
    suspend fun merge(context: Context, cloudDeltas: List<PlayCountDeltaFile>) {
        if (cloudDeltas.isEmpty()) return

        // Sum deltas per track across all devices, keeping track metadata
        val aggregatedDeltas = mutableMapOf<String, AggregatedDelta>()
        for (deltaFile in cloudDeltas) {
            for (delta in deltaFile.deltas) {
                val existing = aggregatedDeltas[delta.key]
                if (existing != null) {
                    existing.delta += delta.delta
                } else {
                    aggregatedDeltas[delta.key] = AggregatedDelta(
                        key = delta.key,
                        artist = delta.artist,
                        title = delta.title,
                        album = delta.album,
                        delta = delta.delta
                    )
                }
            }
        }

        // Apply to local play counts using MAX rule
        val db = MusicDatabase.getInstance(context)
        val playCountDao = db.playCountDao()

        var updatedCount = 0
        var createdCount = 0
        val now = System.currentTimeMillis()

        for ((key, aggDelta) in aggregatedDeltas) {
            val localEntry = playCountDao.getByKey(key)
            if (localEntry != null) {
                // Use earnedPlayCount (local plays only) + cloud deltas to avoid double-counting
                // when the same delta file is re-downloaded after being updated the same day.
                // The MAX rule in updatePlayCountMax ensures counts never decrease.
                val newCount = localEntry.earnedPlayCount + aggDelta.delta
                playCountDao.updatePlayCountMax(key, newCount)
                updatedCount++
            } else {
                // Track exists in cloud but not locally - CREATE new entry
                val newEntry = PlayCountEntry(
                    metadataKey = key,
                    title = aggDelta.title,
                    artist = aggDelta.artist,
                    album = aggDelta.album,
                    playCount = aggDelta.delta,
                    lastPlayed = 0,
                    createdAt = now,
                    updatedAt = now
                )
                playCountDao.insert(newEntry)
                createdCount++
            }
        }

        Log.d(TAG, "Merged play counts: updated $updatedCount, created $createdCount")

        // Update in-memory cache
        try {
            MusicPlayCountManager.refreshCache(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh cache", e)
        }
    }
}
