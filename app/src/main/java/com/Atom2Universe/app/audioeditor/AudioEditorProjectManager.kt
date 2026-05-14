package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistence of the audio editor project state.
 * Saves/loads tracks, history, and manages history size limits.
 */
class AudioEditorProjectManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioEditorProject"
        private const val PROJECT_FILE = "audio_editor_project.json"
        private const val PROJECT_DIR = "audio_editor"
        private const val HISTORY_DIR = "history"

        // Default max history size: 100 MB
        const val DEFAULT_MAX_HISTORY_SIZE_BYTES = 100L * 1024 * 1024

        // Bug 2.23: Max JSON file size to prevent OOM (10 MB should be more than enough)
        private const val MAX_JSON_FILE_SIZE_BYTES = 10L * 1024 * 1024

        // Bug 2.26: Max iterations for history cleanup loop to prevent infinite loops
        private const val MAX_CLEANUP_ITERATIONS = 1000
    }

    private val projectDir: File
        get() = File(context.filesDir, PROJECT_DIR).also { it.mkdirs() }

    private val historyDir: File
        get() = File(projectDir, HISTORY_DIR).also { it.mkdirs() }

    private val projectFile: File
        get() = File(projectDir, PROJECT_FILE)

    var maxHistorySizeBytes: Long = DEFAULT_MAX_HISTORY_SIZE_BYTES

    /**
     * Save the current project state to disk.
     */
    fun saveProject(
        tracks: List<AudioTrack>,
        activeTrackIndex: Int,
        clipboardFilePath: String?
    ): Boolean {
        return try {
            val json = JSONObject().apply {
                put("version", 1)
                put("activeTrackIndex", activeTrackIndex)
                put("clipboardFilePath", clipboardFilePath ?: "")
                put("savedAt", System.currentTimeMillis())

                val tracksArray = JSONArray()
                tracks.forEach { track ->
                    val trackJson = JSONObject().apply {
                        put("id", track.id)
                        put("name", track.name)
                        put("filePath", track.filePath)
                        put("durationMs", track.durationMs)

                        // Save undo stack
                        val undoArray = JSONArray()
                        track.undoStack.forEach { undoArray.put(it) }
                        put("undoStack", undoArray)

                        // Save redo stack
                        val redoArray = JSONArray()
                        track.redoStack.forEach { redoArray.put(it) }
                        put("redoStack", redoArray)
                    }
                    tracksArray.put(trackJson)
                }
                put("tracks", tracksArray)
            }

            projectFile.writeText(json.toString(2))
            Log.d(TAG, "Project saved: ${tracks.size} tracks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project", e)
            false
        }
    }

    /**
     * Check if a saved project exists.
     */
    fun hasSavedProject(): Boolean {
        return projectFile.exists() && projectFile.length() > 0
    }

    /**
     * Load the project state from disk.
     * Returns null if no project exists or loading fails.
     */
    fun loadProject(): ProjectState? {
        if (!projectFile.exists()) {
            Log.d(TAG, "No project file found")
            return null
        }

        // Bug 2.23: Check file size before reading to prevent OOM
        val fileSize = projectFile.length()
        if (fileSize > MAX_JSON_FILE_SIZE_BYTES) {
            Log.e(TAG, "Project file too large ($fileSize bytes), max is $MAX_JSON_FILE_SIZE_BYTES")
            return null
        }

        return try {
            val json = JSONObject(projectFile.readText())
            json.optInt("version", 1)

            val activeTrackIndex = json.optInt("activeTrackIndex", -1)
            val clipboardFilePath = json.optString("clipboardFilePath", "").takeIf { it.isNotEmpty() }

            val tracksArray = json.getJSONArray("tracks")
            val tracks = mutableListOf<AudioTrack>()

            for (i in 0 until tracksArray.length()) {
                val trackJson = tracksArray.getJSONObject(i)
                val filePath = trackJson.getString("filePath")

                // Skip if file doesn't exist
                if (!File(filePath).exists()) {
                    Log.w(TAG, "Track file not found, skipping: $filePath")
                    continue
                }

                // Load undo stack
                val undoStack = mutableListOf<String>()
                val undoArray = trackJson.optJSONArray("undoStack")
                if (undoArray != null) {
                    for (j in 0 until undoArray.length()) {
                        val path = undoArray.getString(j)
                        if (File(path).exists()) {
                            undoStack.add(path)
                        }
                    }
                }

                // Load redo stack
                val redoStack = mutableListOf<String>()
                val redoArray = trackJson.optJSONArray("redoStack")
                if (redoArray != null) {
                    for (j in 0 until redoArray.length()) {
                        val path = redoArray.getString(j)
                        if (File(path).exists()) {
                            redoStack.add(path)
                        }
                    }
                }

                val track = AudioTrack(
                    id = trackJson.getString("id"),
                    name = trackJson.getString("name"),
                    filePath = filePath,
                    uri = Uri.fromFile(File(filePath)),
                    durationMs = trackJson.optLong("durationMs", 0),
                    undoStack = undoStack,
                    redoStack = redoStack
                )
                tracks.add(track)
            }

            if (tracks.isEmpty()) {
                Log.d(TAG, "No valid tracks found in project")
                return null
            }

            // Adjust active index if needed
            val validActiveIndex = if (activeTrackIndex >= 0 && activeTrackIndex < tracks.size) {
                activeTrackIndex
            } else {
                0
            }

            Log.d(TAG, "Project loaded: ${tracks.size} tracks")
            ProjectState(tracks, validActiveIndex, clipboardFilePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load project", e)
            null
        }
    }

    /**
     * Clear the saved project.
     */
    fun clearProject() {
        try {
            projectFile.delete()
            Log.d(TAG, "Project cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear project", e)
        }
    }

    /**
     * Generate a unique file path for history files.
     * Validates trackId to prevent path traversal attacks.
     */
    fun generateHistoryFilePath(trackId: String, type: String): String {
        // Sanitize trackId: only allow alphanumeric, dashes, and underscores
        val sanitizedTrackId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // Additional safety check: ensure the sanitized ID is not empty
        val safeTrackId = if (sanitizedTrackId.isBlank()) "unknown_${System.currentTimeMillis()}" else sanitizedTrackId

        val trackHistoryDir = File(historyDir, safeTrackId).also { it.mkdirs() }

        // Bug 2.24: Verify the resulting path is actually inside historyDir (defense in depth)
        // Use toRealPath-like approach by resolving canonical paths and handling symlinks
        val resultPath = File(trackHistoryDir, "${type}_${System.currentTimeMillis()}.wav")
        try {
            val historyCanonical = historyDir.canonicalFile
            val resultCanonical = resultPath.canonicalFile

            // Check that result is within history directory
            if (!resultCanonical.path.startsWith(historyCanonical.path + File.separator) &&
                resultCanonical.path != historyCanonical.path) {
                Log.e(TAG, "Path traversal attempt detected for trackId: $trackId")
                // Fall back to a safe path
                return File(historyDir, "safe_${System.currentTimeMillis()}.wav").absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving path for trackId: $trackId", e)
            return File(historyDir, "safe_${System.currentTimeMillis()}.wav").absolutePath
        }

        return resultPath.absolutePath
    }

    /**
     * Calculate total history size across all tracks.
     * Bug 2.25: Added size limit to prevent excessive computation
     */
    fun calculateTotalHistorySize(tracks: List<AudioTrack>): Long {
        var totalSize = 0L
        val maxSize = maxHistorySizeBytes * 2 // Cap at 2x max to prevent overflow issues

        for (track in tracks) {
            totalSize += track.getHistorySize()
            // Early exit if we're already over the limit
            if (totalSize > maxSize) {
                return totalSize
            }
        }
        return totalSize
    }

    /**
     * Enforce the history size limit by removing oldest entries.
     * Returns the number of entries removed.
     * Bug 2.26: Added iteration counter to prevent infinite loops
     */
    fun enforceHistorySizeLimit(tracks: List<AudioTrack>): Int {
        var totalSize = calculateTotalHistorySize(tracks)
        var removedCount = 0
        var iterations = 0

        while (totalSize > maxHistorySizeBytes && iterations < MAX_CLEANUP_ITERATIONS) {
            iterations++

            // Find the oldest history entry across all tracks
            var oldestTrack: AudioTrack? = null
            var oldestFile: String? = null
            var oldestTime = Long.MAX_VALUE

            for (track in tracks) {
                // Check undo stack (oldest first)
                if (track.undoStack.isNotEmpty()) {
                    val path = track.undoStack.first()
                    val file = File(path)
                    if (file.exists() && file.lastModified() < oldestTime) {
                        oldestTime = file.lastModified()
                        oldestFile = path
                        oldestTrack = track
                    }
                }
                // Check redo stack (oldest first)
                if (track.redoStack.isNotEmpty()) {
                    val path = track.redoStack.first()
                    val file = File(path)
                    if (file.exists() && file.lastModified() < oldestTime) {
                        oldestTime = file.lastModified()
                        oldestFile = path
                        oldestTrack = track
                    }
                }
            }

            if (oldestTrack == null || oldestFile == null) break

            // Remove the oldest entry
            val file = File(oldestFile)
            val fileSize = file.length()

            val removed = if (oldestTrack.undoStack.remove(oldestFile)) {
                file.delete()
                totalSize -= fileSize
                removedCount++
                Log.d(TAG, "Removed old undo entry: $oldestFile")
                true
            } else if (oldestTrack.redoStack.remove(oldestFile)) {
                file.delete()
                totalSize -= fileSize
                removedCount++
                Log.d(TAG, "Removed old redo entry: $oldestFile")
                true
            } else {
                false
            }

            // Bug 2.26: Safety check - if we couldn't remove anything, break to prevent infinite loop
            if (!removed) {
                Log.w(TAG, "Failed to remove oldest entry, breaking cleanup loop")
                break
            }
        }

        if (iterations >= MAX_CLEANUP_ITERATIONS) {
            Log.w(TAG, "History cleanup reached max iterations ($MAX_CLEANUP_ITERATIONS), stopping")
        }

        if (removedCount > 0) {
            Log.d(TAG, "Enforced history limit: removed $removedCount entries, new size: ${totalSize / 1024 / 1024} MB")
        }

        return removedCount
    }

    /**
     * Clean up orphaned history files that are not referenced by any track.
     */
    fun cleanupOrphanedFiles(tracks: List<AudioTrack>) {
        val referencedFiles = mutableSetOf<String>()

        // Collect all referenced files
        tracks.forEach { track ->
            referencedFiles.add(track.filePath)
            referencedFiles.addAll(track.undoStack)
            referencedFiles.addAll(track.redoStack)
        }

        // Bug 2.38: Delete orphaned files in history directory with explicit null checks
        val historyContents = historyDir.listFiles()
        if (historyContents != null) {
            for (trackDir in historyContents) {
                if (trackDir.isDirectory) {
                    val trackFiles = trackDir.listFiles()
                    if (trackFiles != null) {
                        for (file in trackFiles) {
                            if (file.absolutePath !in referencedFiles) {
                                val deleted = file.delete()
                                if (deleted) {
                                    Log.d(TAG, "Deleted orphaned file: ${file.name}")
                                } else {
                                    Log.w(TAG, "Failed to delete orphaned file: ${file.name}")
                                }
                            }
                        }
                    }
                    // Remove empty directories - check listFiles() result
                    val remainingFiles = trackDir.listFiles()
                    if (remainingFiles != null && remainingFiles.isEmpty()) {
                        trackDir.delete()
                    }
                }
            }
        } else {
            Log.d(TAG, "History directory is empty or inaccessible")
        }
    }

    /**
     * Get human-readable history size.
     */
    fun getHistorySizeFormatted(tracks: List<AudioTrack>): String {
        val bytes = calculateTotalHistorySize(tracks)
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / 1024 / 1024} MB"
        }
    }

    /**
     * Project state data class.
     */
    data class ProjectState(
        val tracks: List<AudioTrack>,
        val activeTrackIndex: Int,
        val clipboardFilePath: String?
    )
}
