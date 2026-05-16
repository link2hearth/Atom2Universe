package com.Atom2Universe.app.pixelart

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import androidx.core.content.edit

/**
 * Action types for history tracking
 */
enum class ActionType {
    DRAW,           // Pencil, marker, brush strokes
    ERASE,          // Eraser strokes
    FILL,           // Flood fill
    LINE,           // Line shape
    RECTANGLE,      // Rectangle shape
    CIRCLE,         // Circle shape
    CLEAR,          // Clear canvas
    FLIP_H,         // Flip horizontal
    FLIP_V,         // Flip vertical
    ROTATE_CW,      // Rotate clockwise
    ROTATE_CCW,     // Rotate counter-clockwise
    PASTE,          // Paste clipboard
    CUT,            // Cut selection
    MOVE,           // Move layer/selection
    RESIZE,         // Resize canvas
    IMPORT          // Import image
}

/**
 * Represents a single action in history (lightweight for large canvases)
 */
data class HistoryAction(
    val id: Long,
    val frameId: Int,
    val actionType: ActionType,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistoryAction) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Delta-based history entry for large canvases
 * Only stores changed pixels instead of full canvas
 */
data class DeltaHistoryEntry(
    val changedPixels: Map<Int, Int>, // index -> previous color
    val actionType: ActionType
)

/**
 * History stack for a single frame
 * Supports both full snapshot and delta-based history
 */
data class FrameHistory(
    val frameId: Int,
    val undoStack: MutableList<IntArray> = mutableListOf(),
    val redoStack: MutableList<IntArray> = mutableListOf(),
    // Delta-based stacks for large canvases
    val deltaUndoStack: MutableList<DeltaHistoryEntry> = mutableListOf(),
    val deltaRedoStack: MutableList<DeltaHistoryEntry> = mutableListOf()
) {
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        deltaUndoStack.clear()
        deltaRedoStack.clear()
    }

    fun clone(): FrameHistory {
        return FrameHistory(
            frameId,
            undoStack.map { it.clone() }.toMutableList(),
            redoStack.map { it.clone() }.toMutableList(),
            deltaUndoStack.toMutableList(),
            deltaRedoStack.toMutableList()
        )
    }
}

/**
 * Manages undo/redo history for the pixel art editor.
 * - Maintains separate history stacks per frame
 * - Tracks global action log across all frames
 * - Persists history to SharedPreferences
 */
class PixelArtHistoryManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val saveLock = Any()

    // Per-frame history stacks
    private val frameHistories = mutableMapOf<Int, FrameHistory>()

    // Global action log (tracks all actions across frames for potential cross-frame undo)
    // DISABLED for large canvases to save memory
    private val globalActionLog = mutableListOf<HistoryAction>()
    private var nextActionId = 1L

    // Configuration
    var maxHistoryPerFrame = 50
    var maxGlobalActions = 200

    // Large canvas optimizations
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var useDeltaHistory = false
    private var enableGlobalLog = true

    // Thresholds for optimization
    companion object {
        private const val PREF_NAME = "atom2univers_pixelart_history"
        private const val KEY_PREFIX = "history_"

        // Canvas size thresholds
        private const val LARGE_CANVAS_THRESHOLD = 512 * 512     // 262K pixels
        private const val HUGE_CANVAS_THRESHOLD = 1024 * 1024    // 1M pixels
        private const val MEGA_CANVAS_THRESHOLD = 2048 * 2048    // 4M pixels

        // Memory threshold (don't save history if less than 50MB free)
        private const val MIN_FREE_MEMORY_MB = 50

        // Maximum total memory for history (100 MB)
        // This ensures fair distribution regardless of canvas size:
        // - Pixel art 64x64: ~2000 entries possible
        // - HD 2048x2048: ~6 entries possible
        // - 4K 4096x4096: ~1-2 entries possible
        private const val MAX_HISTORY_MEMORY_BYTES = 100L * 1024 * 1024
    }

    // Track total memory used by history
    private var totalHistoryMemoryBytes = 0L

    // Listener for history changes
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // Current project ID (for persistent storage)
    private var currentProjectId: String = "default"

    /**
     * Set canvas size and adapt history settings accordingly
     */
    fun setCanvasSize(width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        val pixelCount = width * height

        when {
            pixelCount >= MEGA_CANVAS_THRESHOLD -> {
                // Very large canvas (4K+): minimal history, delta-based
                maxHistoryPerFrame = 5
                useDeltaHistory = true
                enableGlobalLog = false
            }
            pixelCount >= HUGE_CANVAS_THRESHOLD -> {
                // Large canvas (1-4 megapixels): reduced history, delta-based
                maxHistoryPerFrame = 10
                useDeltaHistory = true
                enableGlobalLog = false
            }
            pixelCount >= LARGE_CANVAS_THRESHOLD -> {
                // Medium-large canvas: reduced history, full snapshots
                maxHistoryPerFrame = 20
                useDeltaHistory = false
                enableGlobalLog = false
            }
            else -> {
                // Small canvas: full features
                maxHistoryPerFrame = 50
                useDeltaHistory = false
                enableGlobalLog = true
            }
        }

        // Clear global log if disabled
        if (!enableGlobalLog) {
            globalActionLog.clear()
        }

        // Trim existing histories if needed
        for ((_, history) in frameHistories) {
            while (history.undoStack.size > maxHistoryPerFrame) {
                history.undoStack.removeAt(0)
            }
            while (history.deltaUndoStack.size > maxHistoryPerFrame) {
                history.deltaUndoStack.removeAt(0)
            }
        }
    }

    /**
     * Check if there's enough memory to save history
     */
    private fun hasEnoughMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - freeMemory
        val availableMemory = maxMemory - usedMemory
        return availableMemory > MIN_FREE_MEMORY_MB * 1024 * 1024
    }

    /**
     * Calculate memory size of an IntArray (4 bytes per int)
     */
    private fun calculateMemorySize(data: IntArray): Long {
        return data.size.toLong() * 4
    }

    /**
     * Calculate memory size of a delta entry
     */
    private fun calculateDeltaMemorySize(delta: DeltaHistoryEntry): Long {
        // Each entry in the map: Int key (4 bytes) + Int value (4 bytes) + overhead (~16 bytes)
        return delta.changedPixels.size.toLong() * 24
    }

    /**
     * Trim history to stay within memory limit
     * Removes oldest entries first (FIFO)
     */
    private fun trimHistoryToMemoryLimit() {
        while (totalHistoryMemoryBytes > MAX_HISTORY_MEMORY_BYTES) {
            var removed = false

            // Find and remove the oldest entry across all frames
            for ((_, history) in frameHistories) {
                // Try removing from full snapshot undo stack first (largest entries)
                if (history.undoStack.isNotEmpty()) {
                    val oldest = history.undoStack.removeAt(0)
                    totalHistoryMemoryBytes -= calculateMemorySize(oldest)
                    removed = true
                    break
                }
                // Then try delta undo stack
                if (history.deltaUndoStack.isNotEmpty()) {
                    val oldest = history.deltaUndoStack.removeAt(0)
                    totalHistoryMemoryBytes -= calculateDeltaMemorySize(oldest)
                    removed = true
                    break
                }
            }

            // Safety: if nothing was removed, reset counter to avoid infinite loop
            if (!removed) {
                totalHistoryMemoryBytes = 0
                break
            }
        }

        // Ensure memory counter doesn't go negative
        if (totalHistoryMemoryBytes < 0) {
            totalHistoryMemoryBytes = 0
        }
    }

    /**
     * Recalculate total memory used by all history entries
     * Call this after loading or when memory tracking might be out of sync
     */
    private fun recalculateTotalMemory() {
        totalHistoryMemoryBytes = 0
        for ((_, history) in frameHistories) {
            for (data in history.undoStack) {
                totalHistoryMemoryBytes += calculateMemorySize(data)
            }
            for (data in history.redoStack) {
                totalHistoryMemoryBytes += calculateMemorySize(data)
            }
            for (delta in history.deltaUndoStack) {
                totalHistoryMemoryBytes += calculateDeltaMemorySize(delta)
            }
            for (delta in history.deltaRedoStack) {
                totalHistoryMemoryBytes += calculateDeltaMemorySize(delta)
            }
        }
    }

    /**
     * Set the current project ID for persistence
     */
    fun setProjectId(projectId: String) {
        if (currentProjectId != projectId) {
            // Save current history before switching
            saveToStorage()
            currentProjectId = projectId
            // Load history for new project
            loadFromStorage()
        }
    }

    /**
     * Get or create history for a frame
     */
    private fun getFrameHistory(frameId: Int): FrameHistory {
        return frameHistories.getOrPut(frameId) { FrameHistory(frameId) }
    }

    /**
     * Save current pixel state to history before making changes
     */
    fun saveToHistory(frameId: Int, pixelData: IntArray, actionType: ActionType = ActionType.DRAW) {
        // Skip if not enough memory
        if (!hasEnoughMemory()) {
            return
        }

        val history = getFrameHistory(frameId)

        // Clear redo stacks and subtract their memory (new action invalidates redo)
        for (data in history.redoStack) {
            totalHistoryMemoryBytes -= calculateMemorySize(data)
        }
        history.redoStack.clear()
        for (delta in history.deltaRedoStack) {
            totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)
        }
        history.deltaRedoStack.clear()

        // Clone and add to undo stack
        val clonedData = pixelData.clone()
        val entrySize = calculateMemorySize(clonedData)
        history.undoStack.add(clonedData)
        totalHistoryMemoryBytes += entrySize

        // Trim by count limit
        while (history.undoStack.size > maxHistoryPerFrame) {
            val removed = history.undoStack.removeAt(0)
            totalHistoryMemoryBytes -= calculateMemorySize(removed)
        }

        // Trim by memory limit (applies to ALL history across all frames)
        trimHistoryToMemoryLimit()

        // Add to global action log only if enabled (disabled for large canvases)
        if (enableGlobalLog) {
            val action = HistoryAction(
                id = nextActionId++,
                frameId = frameId,
                actionType = actionType,
                timestamp = System.currentTimeMillis()
            )
            globalActionLog.add(action)

            // Trim global log if exceeds max
            while (globalActionLog.size > maxGlobalActions) {
                globalActionLog.removeAt(0)
            }
        }

        notifyHistoryChanged(frameId)
    }

    /**
     * Save delta-based history (only changed pixels)
     * More memory efficient for large canvases with small changes
     */
    fun saveDeltaToHistory(frameId: Int, changedPixels: Map<Int, Int>, actionType: ActionType = ActionType.DRAW) {
        if (changedPixels.isEmpty()) return
        if (!hasEnoughMemory()) return

        val history = getFrameHistory(frameId)

        // Clear redo stacks and subtract their memory
        for (delta in history.deltaRedoStack) {
            totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)
        }
        history.deltaRedoStack.clear()

        // Add delta entry
        val deltaEntry = DeltaHistoryEntry(changedPixels, actionType)
        val entrySize = calculateDeltaMemorySize(deltaEntry)
        history.deltaUndoStack.add(deltaEntry)
        totalHistoryMemoryBytes += entrySize

        // Trim by count limit
        while (history.deltaUndoStack.size > maxHistoryPerFrame) {
            val removed = history.deltaUndoStack.removeAt(0)
            totalHistoryMemoryBytes -= calculateDeltaMemorySize(removed)
        }

        // Trim by memory limit
        trimHistoryToMemoryLimit()

        notifyHistoryChanged(frameId)
    }

    /**
     * Undo the last action on a frame
     * Supports both full snapshot and delta-based history
     * @return The pixel data to restore, or null if nothing to undo
     */
    fun undo(frameId: Int, currentPixelData: IntArray): IntArray? {
        val history = getFrameHistory(frameId)

        // Try delta undo first (more recent entries are delta-based for large canvases)
        if (history.deltaUndoStack.isNotEmpty()) {
            val delta = history.deltaUndoStack.removeAt(history.deltaUndoStack.lastIndex)
            totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)

            // Save current state of changed pixels to redo stack
            val redoDelta = mutableMapOf<Int, Int>()
            for ((index, _) in delta.changedPixels) {
                if (index >= 0 && index < currentPixelData.size) {
                    redoDelta[index] = currentPixelData[index]
                }
            }
            val redoEntry = DeltaHistoryEntry(redoDelta, delta.actionType)
            history.deltaRedoStack.add(redoEntry)
            totalHistoryMemoryBytes += calculateDeltaMemorySize(redoEntry)

            // Apply delta (restore original colors)
            val result = currentPixelData.clone()
            for ((index, originalColor) in delta.changedPixels) {
                if (index >= 0 && index < result.size) {
                    result[index] = originalColor
                }
            }

            notifyHistoryChanged(frameId)
            return result
        }

        // Fall back to full snapshot undo
        if (history.undoStack.isEmpty()) {
            return null
        }

        // Save current state to redo stack
        val clonedCurrent = currentPixelData.clone()
        history.redoStack.add(clonedCurrent)
        totalHistoryMemoryBytes += calculateMemorySize(clonedCurrent)

        // Get previous state from undo stack
        val previousState = history.undoStack.removeAt(history.undoStack.lastIndex)
        totalHistoryMemoryBytes -= calculateMemorySize(previousState)

        notifyHistoryChanged(frameId)
        return previousState
    }

    /**
     * Redo the last undone action on a frame
     * Supports both full snapshot and delta-based history
     * @return The pixel data to restore, or null if nothing to redo
     */
    fun redo(frameId: Int, currentPixelData: IntArray): IntArray? {
        val history = getFrameHistory(frameId)

        // Try delta redo first
        if (history.deltaRedoStack.isNotEmpty()) {
            val delta = history.deltaRedoStack.removeAt(history.deltaRedoStack.lastIndex)
            totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)

            // Save current state of changed pixels to undo stack
            val undoDelta = mutableMapOf<Int, Int>()
            for ((index, _) in delta.changedPixels) {
                if (index >= 0 && index < currentPixelData.size) {
                    undoDelta[index] = currentPixelData[index]
                }
            }
            val undoEntry = DeltaHistoryEntry(undoDelta, delta.actionType)
            history.deltaUndoStack.add(undoEntry)
            totalHistoryMemoryBytes += calculateDeltaMemorySize(undoEntry)

            // Apply delta (restore redo colors)
            val result = currentPixelData.clone()
            for ((index, color) in delta.changedPixels) {
                if (index >= 0 && index < result.size) {
                    result[index] = color
                }
            }

            notifyHistoryChanged(frameId)
            return result
        }

        // Fall back to full snapshot redo
        if (history.redoStack.isEmpty()) {
            return null
        }

        // Save current state to undo stack
        val clonedCurrent = currentPixelData.clone()
        history.undoStack.add(clonedCurrent)
        totalHistoryMemoryBytes += calculateMemorySize(clonedCurrent)

        // Get next state from redo stack
        val nextState = history.redoStack.removeAt(history.redoStack.lastIndex)
        totalHistoryMemoryBytes -= calculateMemorySize(nextState)

        notifyHistoryChanged(frameId)
        return nextState
    }

    /**
     * Check if undo is available for a frame
     */
    fun canUndo(frameId: Int): Boolean {
        val history = getFrameHistory(frameId)
        return history.undoStack.isNotEmpty() || history.deltaUndoStack.isNotEmpty()
    }

    /**
     * Check if redo is available for a frame
     */
    fun canRedo(frameId: Int): Boolean {
        val history = getFrameHistory(frameId)
        return history.redoStack.isNotEmpty() || history.deltaRedoStack.isNotEmpty()
    }

    /**
     * Get undo count for a frame
     */
    fun getUndoCount(frameId: Int): Int {
        val history = getFrameHistory(frameId)
        return history.undoStack.size + history.deltaUndoStack.size
    }

    /**
     * Get redo count for a frame
     */
    fun getRedoCount(frameId: Int): Int {
        val history = getFrameHistory(frameId)
        return history.redoStack.size + history.deltaRedoStack.size
    }

    /**
     * Clear history for a specific frame
     */
    fun clearFrameHistory(frameId: Int) {
        val history = frameHistories[frameId]
        if (history != null) {
            // Subtract memory used by this frame's history
            for (data in history.undoStack) {
                totalHistoryMemoryBytes -= calculateMemorySize(data)
            }
            for (data in history.redoStack) {
                totalHistoryMemoryBytes -= calculateMemorySize(data)
            }
            for (delta in history.deltaUndoStack) {
                totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)
            }
            for (delta in history.deltaRedoStack) {
                totalHistoryMemoryBytes -= calculateDeltaMemorySize(delta)
            }
            history.clear()
        }
        if (totalHistoryMemoryBytes < 0) totalHistoryMemoryBytes = 0
        notifyHistoryChanged(frameId)
    }

    /**
     * Clear all history
     */
    fun clearAllHistory() {
        frameHistories.clear()
        globalActionLog.clear()
        nextActionId = 1L
        totalHistoryMemoryBytes = 0
    }

    /**
     * Called when a frame is deleted - removes its history
     */
    fun onFrameDeleted(frameId: Int) {
        // Clear and remove history for this frame (updates memory counter)
        clearFrameHistory(frameId)
        frameHistories.remove(frameId)
        // Remove actions for this frame from global log
        globalActionLog.removeAll { it.frameId == frameId }
    }

    /**
     * Called when frames are reordered - updates frame IDs in history
     * (Not strictly necessary if we use frame IDs not indices)
     */
    fun onFramesReordered(oldToNewMapping: Map<Int, Int>) {
        // Remap frame histories
        val newHistories = mutableMapOf<Int, FrameHistory>()
        for ((oldId, history) in frameHistories) {
            val newId = oldToNewMapping[oldId] ?: oldId
            newHistories[newId] = FrameHistory(newId, history.undoStack, history.redoStack)
        }
        frameHistories.clear()
        frameHistories.putAll(newHistories)
    }

    /**
     * Get global action log (for debugging or advanced features)
     */
    fun getGlobalActionLog(): List<HistoryAction> = globalActionLog.toList()

    /**
     * Get last N actions across all frames
     */
    fun getRecentActions(count: Int): List<HistoryAction> {
        return globalActionLog.takeLast(count)
    }

    private fun notifyHistoryChanged(frameId: Int) {
        onHistoryChanged?.invoke(canUndo(frameId), canRedo(frameId))
    }

    // ========== PERSISTENCE ==========

    /**
     * Save history to persistent storage
     */
    fun saveToStorage() {
        synchronized(saveLock) {
            try {
                val payload = JSONObject()

                // Save frame histories
                val historiesJson = JSONObject()
                for ((frameId, history) in frameHistories) {
                    val frameJson = JSONObject()

                    // Encode undo stack (only save last 10 to reduce storage)
                    val undoArray = JSONArray()
                    val undoToSave = history.undoStack.takeLast(10)
                    for (data in undoToSave) {
                        undoArray.put(encodePixelData(data))
                    }
                    frameJson.put("undo", undoArray)

                    // Encode redo stack (only save last 10)
                    val redoArray = JSONArray()
                    val redoToSave = history.redoStack.takeLast(10)
                    for (data in redoToSave) {
                        redoArray.put(encodePixelData(data))
                    }
                    frameJson.put("redo", redoArray)

                    historiesJson.put(frameId.toString(), frameJson)
                }
                payload.put("frameHistories", historiesJson)

                // Save metadata
                payload.put("nextActionId", nextActionId)
                payload.put("maxHistoryPerFrame", maxHistoryPerFrame)
                payload.put("timestamp", System.currentTimeMillis())

                // Store under project-specific key
                val key = "$KEY_PREFIX$currentProjectId"
                preferences.edit { putString(key, payload.toString()) }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Load history from persistent storage
     */
    fun loadFromStorage() {
        synchronized(saveLock) {
            try {
                val key = "$KEY_PREFIX$currentProjectId"
                val raw = preferences.getString(key, null) ?: return

                val payload = JSONObject(raw)

                // Load metadata
                nextActionId = payload.optLong("nextActionId", 1L)

                // Load frame histories
                frameHistories.clear()
                val historiesJson = payload.optJSONObject("frameHistories") ?: return

                val keys = historiesJson.keys()
                while (keys.hasNext()) {
                    val frameIdStr = keys.next()
                    val frameId = frameIdStr.toIntOrNull() ?: continue
                    val frameJson = historiesJson.getJSONObject(frameIdStr)

                    val history = FrameHistory(frameId)

                    // Decode undo stack
                    val undoArray = frameJson.optJSONArray("undo")
                    if (undoArray != null) {
                        for (i in 0 until undoArray.length()) {
                            val decoded = decodePixelData(undoArray.getString(i))
                            if (decoded != null) {
                                history.undoStack.add(decoded)
                            }
                        }
                    }

                    // Decode redo stack
                    val redoArray = frameJson.optJSONArray("redo")
                    if (redoArray != null) {
                        for (i in 0 until redoArray.length()) {
                            val decoded = decodePixelData(redoArray.getString(i))
                            if (decoded != null) {
                                history.redoStack.add(decoded)
                            }
                        }
                    }

                    frameHistories[frameId] = history
                }

                // Recalculate total memory after loading
                recalculateTotalMemory()

                // Trim if loaded history exceeds memory limit
                trimHistoryToMemoryLimit()

            } catch (e: Exception) {
                e.printStackTrace()
                // On error, start fresh
                frameHistories.clear()
                totalHistoryMemoryBytes = 0
            }
        }
    }

    /**
     * Clear persisted history for current project
     */
    fun clearStorage() {
        synchronized(saveLock) {
            val key = "$KEY_PREFIX$currentProjectId"
            preferences.edit { remove(key) }
        }
    }

    /**
     * Clear all persisted history for all projects
     */
    fun clearAllStorage() {
        synchronized(saveLock) {
            val allKeys = preferences.all.keys.filter { it.startsWith(KEY_PREFIX) }
            val editor = preferences.edit()
            for (key in allKeys) {
                editor.remove(key)
            }
            editor.apply()
        }
    }

    // ========== ENCODING HELPERS ==========

    private fun encodePixelData(data: IntArray): String {
        val buffer = ByteBuffer.allocate(data.size * 4)
        for (pixel in data) {
            buffer.putInt(pixel)
        }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decodePixelData(encoded: String): IntArray? {
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes)
            val data = IntArray(bytes.size / 4)
            for (i in data.indices) {
                data[i] = buffer.getInt()
            }
            data
        } catch (e: Exception) {
            null
        }
    }
}
