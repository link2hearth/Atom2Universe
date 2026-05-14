package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.lyrics.data.LyricsEntity
import com.Atom2Universe.app.music.sync.model.LyricsSyncFile
import com.Atom2Universe.app.music.sync.model.SyncLyricsEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Merges lyrics from cloud with local data.
 *
 * Algorithm (Last-Write-Wins):
 * 1. Compare local and cloud lyrics by metadata key
 * 2. For conflicts, use the entry with the most recent timestamp
 * 3. Support soft-delete for proper cross-device sync
 * 4. Update local cache and queue file writes
 */
object LyricsMerger {

    private const val TAG = "LyricsMerger"
    private const val DELETED_LYRICS_FILENAME = "deleted_lyrics.json"

    // Cache des paroles supprimées (en attente de sync)
    private val deletedLyricsCache = mutableMapOf<String, DeletedLyricsEntry>()
    private var isDeletedCacheLoaded = false

    data class DeletedLyricsEntry(
        val key: String,
        val artist: String,
        val title: String,
        val album: String,
        val lyrics: String,
        val source: String,
        val isSynced: Boolean,
        val modifiedAt: Long,
        val deletedAt: Long
    )

    /**
     * Merges cloud lyrics with local data.
     * Supports soft-delete: if cloud entry is deleted and newer, removes local entry.
     */
    suspend fun merge(context: Context, cloudLyrics: LyricsSyncFile) {
        if (cloudLyrics.lyrics.isEmpty()) {
            Log.d(TAG, "No cloud lyrics to merge")
            return
        }

        loadDeletedCache(context)

        val db = MusicDatabase.getInstance(context)
        val lyricsDao = db.lyricsDao()

        var addedCount = 0
        var updatedCount = 0
        var removedCount = 0

        for (cloudEntry in cloudLyrics.lyrics) {
            val localEntry = lyricsDao.getByKey(cloudEntry.key)
            val localDeletedEntry = deletedLyricsCache[cloudEntry.key]

            // Determine local timestamp (either from active entry or deleted entry)
            val localTimestamp = when {
                localEntry != null -> localEntry.lastModified
                localDeletedEntry != null -> localDeletedEntry.deletedAt
                else -> 0L
            }

            val cloudTimestamp = cloudEntry.getLastModifiedTimestamp()

            if (cloudTimestamp > localTimestamp) {
                // Cloud is newer
                if (cloudEntry.isActive()) {
                    // Cloud has active lyrics - add or update locally
                    if (localEntry == null) {
                        lyricsDao.insert(
                            LyricsEntity(
                                metadataKey = cloudEntry.key,
                                trackId = 0,
                                lyrics = cloudEntry.lyrics,
                                source = "cloud_sync",
                                language = null,
                                isSynced = cloudEntry.isSynced,
                                fetchedAt = cloudEntry.modifiedAt,
                                lastModified = cloudEntry.modifiedAt,
                                isSyncedToFile = false
                            )
                        )
                        // Remove from deleted cache if was there
                        deletedLyricsCache.remove(cloudEntry.key)
                        addedCount++
                    } else {
                        lyricsDao.insert(
                            localEntry.copy(
                                lyrics = cloudEntry.lyrics,
                                source = "cloud_sync",
                                isSynced = cloudEntry.isSynced,
                                lastModified = cloudEntry.modifiedAt,
                                isSyncedToFile = false
                            )
                        )
                        updatedCount++
                    }
                } else {
                    // Cloud has deleted lyrics - remove locally
                    if (localEntry != null) {
                        lyricsDao.delete(cloudEntry.key)
                        removedCount++
                    }
                    // Remove from deleted cache too (cloud deletion takes precedence)
                    deletedLyricsCache.remove(cloudEntry.key)
                }
            }
        }

        saveDeletedCache(context)
        Log.d(TAG, "Merge complete: added $addedCount, updated $updatedCount, removed $removedCount")
    }

    /**
     * Gets all local lyrics in sync format (including deleted entries for soft-delete sync).
     */
    suspend fun getLocalLyrics(context: Context): List<SyncLyricsEntry> {
        return try {
            loadDeletedCache(context)

            val result = mutableListOf<SyncLyricsEntry>()

            // Add active lyrics (exclude "no lyrics found" markers — purement internes à l'appareil)
            val db = MusicDatabase.getInstance(context)
            val lyricsDao = db.lyricsDao()
            val allLyrics = lyricsDao.getAll().filter { !it.noLyricsFound && it.lyrics.isNotEmpty() }

            for (entity in allLyrics) {
                // La clé est au format "title-artist-album". On la découpe avec limit=3
                // pour préserver les tirets éventuels dans les métadonnées.
                val parts = entity.metadataKey.split("-", limit = 3)
                val title = parts.getOrNull(0) ?: ""
                val artist = parts.getOrNull(1) ?: ""
                val album = parts.getOrNull(2) ?: ""

                result.add(SyncLyricsEntry(
                    key = entity.metadataKey,
                    artist = artist,
                    title = title,
                    album = album,
                    lyrics = entity.lyrics,
                    source = entity.source,
                    isSynced = entity.isSynced,
                    modifiedAt = entity.lastModified,
                    deletedAt = null
                ))
            }

            // Add deleted lyrics (soft-delete) for sync
            for ((_, deletedEntry) in deletedLyricsCache) {
                result.add(SyncLyricsEntry(
                    key = deletedEntry.key,
                    artist = deletedEntry.artist,
                    title = deletedEntry.title,
                    album = deletedEntry.album,
                    lyrics = deletedEntry.lyrics,
                    source = deletedEntry.source,
                    isSynced = deletedEntry.isSynced,
                    modifiedAt = deletedEntry.modifiedAt,
                    deletedAt = deletedEntry.deletedAt
                ))
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local lyrics", e)
            emptyList()
        }
    }

    /**
     * Tracks a lyrics deletion for cloud sync.
     * Call this when lyrics are deleted locally.
     */
    suspend fun trackLyricsDeletion(context: Context, entity: LyricsEntity) {
        loadDeletedCache(context)

        val parts = entity.metadataKey.split("-", limit = 3)
        val title = parts.getOrNull(0) ?: ""
        val artist = parts.getOrNull(1) ?: ""
        val album = parts.getOrNull(2) ?: ""

        deletedLyricsCache[entity.metadataKey] = DeletedLyricsEntry(
            key = entity.metadataKey,
            artist = artist,
            title = title,
            album = album,
            lyrics = entity.lyrics,
            source = entity.source,
            isSynced = entity.isSynced,
            modifiedAt = entity.lastModified,
            deletedAt = System.currentTimeMillis()
        )

        saveDeletedCache(context)
        Log.d(TAG, "Tracked lyrics deletion: ${entity.metadataKey}")
    }

    /**
     * Clears the deleted lyrics cache after successful sync upload.
     */
    suspend fun clearDeletedCache(context: Context) {
        deletedLyricsCache.clear()
        saveDeletedCache(context)
        Log.d(TAG, "Cleared deleted lyrics cache after sync")
    }

    // ==================== Deleted Cache Persistence ====================

    private fun getDeletedCacheFile(context: Context): File {
        return File(context.filesDir, DELETED_LYRICS_FILENAME)
    }

    private fun loadDeletedCache(context: Context) {
        if (isDeletedCacheLoaded) return

        try {
            val file = getDeletedCacheFile(context)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val array = json.optJSONArray("deletedLyrics") ?: JSONArray()

                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    val key = entry.optString("key", "")
                    if (key.isNotEmpty()) {
                        deletedLyricsCache[key] = DeletedLyricsEntry(
                            key = key,
                            artist = entry.optString("artist", ""),
                            title = entry.optString("title", ""),
                            album = entry.optString("album", ""),
                            lyrics = entry.optString("lyrics", ""),
                            source = entry.optString("source", ""),
                            isSynced = entry.optBoolean("isSynced", false),
                            modifiedAt = entry.optLong("modifiedAt", 0),
                            deletedAt = entry.optLong("deletedAt", 0)
                        )
                    }
                }
                Log.d(TAG, "Loaded ${deletedLyricsCache.size} deleted lyrics entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading deleted lyrics cache", e)
        }

        isDeletedCacheLoaded = true
    }

    private fun saveDeletedCache(context: Context) {
        try {
            val json = JSONObject().apply {
                put("deletedLyrics", JSONArray().apply {
                    deletedLyricsCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("key", entry.key)
                            put("artist", entry.artist)
                            put("title", entry.title)
                            put("album", entry.album)
                            put("lyrics", entry.lyrics)
                            put("source", entry.source)
                            put("isSynced", entry.isSynced)
                            put("modifiedAt", entry.modifiedAt)
                            put("deletedAt", entry.deletedAt)
                        })
                    }
                })
            }

            val file = getDeletedCacheFile(context)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving deleted lyrics cache", e)
        }
    }
}
