package com.Atom2Universe.app.music.sync.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks local play count increments since the last cloud sync.
 *
 * When a track is played:
 * 1. The play count is incremented in play_counts table (as usual)
 * 2. A delta of +1 is recorded here for cloud sync
 *
 * During cloud sync:
 * 1. All deltas are uploaded to Google Drive
 * 2. Deltas from other devices are downloaded and merged
 * 3. Local deltas are cleared after successful sync
 *
 * The delta approach allows:
 * - Offline devices to catch up without losing plays
 * - Multiple devices listening to same track to sum their plays
 * - Never decrementing (MAX rule still applies during merge)
 */
@Entity(
    tableName = "sync_play_count_deltas",
    indices = [Index(value = ["metadataKey"], unique = true)]
)
data class SyncPlayCountDelta(
    @PrimaryKey
    val metadataKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val delta: Long,
    val updatedAt: Long
)
