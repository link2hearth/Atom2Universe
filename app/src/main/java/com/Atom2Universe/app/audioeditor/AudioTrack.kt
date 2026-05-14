package com.Atom2Universe.app.audioeditor

import android.net.Uri
import android.util.Log
import java.util.UUID

/**
 * Represents a single audio track in the multi-track editor.
 * Each track has its own undo/redo history.
 */
data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val uri: Uri,
    var waveformData: WaveformData? = null,
    val durationMs: Long = 0,
    // Per-track undo/redo history (file paths)
    val undoStack: MutableList<String> = mutableListOf(),
    val redoStack: MutableList<String> = mutableListOf(),
    // Cached spectrum data (frequency bands per time slice)
    var cachedFrequencyBands: FloatArray? = null,
    var cachedFrequencyColors: IntArray? = null,
    var cachedSpectrogramData: Array<FloatArray>? = null,
    var cachedSpectrogramSampleRate: Int? = null,
    var cachedSpectrogramFftSize: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioTrack
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    /**
     * Create a copy with updated waveform data.
     */
    @Suppress("unused")
    fun withWaveformData(data: WaveformData): AudioTrack {
        return copy(waveformData = data, durationMs = data.durationMs)
    }

    /**
     * Check if this track has undo history.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Check if this track has redo history.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Clear redo history (called when a new edit is made).
     * Bug 2.39: Log when redo history is cleared for debugging
     */
    fun clearRedoHistory() {
        if (redoStack.isNotEmpty()) {
            Log.d("AudioTrack", "Clearing redo history for track $id: ${redoStack.size} entries")
        }
        redoStack.forEach { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        Log.w("AudioTrack", "Failed to delete redo file: $path")
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioTrack", "Error deleting redo file: $path", e)
            }
        }
        redoStack.clear()
    }

    /**
     * Clear all history for this track.
     */
    fun clearAllHistory() {
        undoStack.forEach { path ->
            try { java.io.File(path).delete() } catch (_: Exception) {}
        }
        redoStack.forEach { path ->
            try { java.io.File(path).delete() } catch (_: Exception) {}
        }
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Get total size of history files in bytes.
     */
    fun getHistorySize(): Long {
        var size = 0L
        undoStack.forEach { path ->
            try { size += java.io.File(path).length() } catch (_: Exception) {}
        }
        redoStack.forEach { path ->
            try { size += java.io.File(path).length() } catch (_: Exception) {}
        }
        return size
    }

    /**
     * Check if spectrum data is cached.
     */
    @Suppress("unused")
    fun hasSpectrumCache(): Boolean = cachedFrequencyBands != null

    /**
     * Clear cached spectrum data.
     */
    @Suppress("unused")
    fun clearSpectrumCache() {
        cachedFrequencyBands = null
        cachedFrequencyColors = null
        cachedSpectrogramData = null
        cachedSpectrogramSampleRate = null
        cachedSpectrogramFftSize = null
    }
}
