package com.Atom2Universe.app.audioeditor

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the Audio Editor.
 * Manages audio playback, waveform extraction, and FFmpeg operations.
 */
class AudioEditorViewModel(application: Application) : AndroidViewModel(application) {

    // Keep the Application reference (not a Context field) to avoid context leak lint warnings.
    private val app = getApplication<Application>()
    private val waveformExtractor = WaveformExtractor(app)
    val audioRecorder = AudioRecorder(app)

    // Project manager for persistence
    private val projectManager = AudioEditorProjectManager(app)
    private val saveProjectMutex = Mutex()

    /** Cache directory for temporary audio operations (cut, paste, delete, etc.) */
    private val tempCacheDir: File
        get() = File(app.cacheDir, "audio_editor").also { it.mkdirs() }

    // Multi-track management
    private val trackList = mutableListOf<AudioTrack>()
    private val _tracks = MutableLiveData<List<AudioTrack>>(emptyList())
    val tracks: LiveData<List<AudioTrack>> = _tracks

    private val _activeTrackIndex = MutableLiveData<Int>(-1)
    val activeTrackIndex: LiveData<Int> = _activeTrackIndex

    // Audio clipboard - protected by clipboardLock for thread-safe access (Bug 2.21)
    private val clipboardLock = Any()
    private var clipboardFilePath: String? = null
    private val _hasClipboard = MutableLiveData<Boolean>(false)
    val hasClipboard: LiveData<Boolean> = _hasClipboard

    // Undo/Redo state (per-track, managed via AudioTrack)
    private val _canUndo = MutableLiveData<Boolean>(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData<Boolean>(false)
    val canRedo: LiveData<Boolean> = _canRedo

    // Project loaded flag
    private var projectLoaded = false

    // MediaPlayer for playback
    private var mediaPlayer: MediaPlayer? = null
    private val mediaPlayerLock = Any()
    private val handler = Handler(Looper.getMainLooper())

    // Recording state exposed as LiveData
    private val _isRecording = MutableLiveData<Boolean>(false)
    val isRecording: LiveData<Boolean> = _isRecording

    private val _recordingAmplitude = MutableLiveData<Float>(0f)
    val recordingAmplitude: LiveData<Float> = _recordingAmplitude

    private val _recordingDurationMs = MutableLiveData<Long>(0L)
    val recordingDurationMs: LiveData<Long> = _recordingDurationMs

    // LiveData
    private val _waveformData = MutableLiveData<WaveformData?>()
    val waveformData: LiveData<WaveformData?> = _waveformData

    private val _playbackState = MutableLiveData<PlaybackState>(PlaybackState.STOPPED)
    val playbackState: LiveData<PlaybackState> = _playbackState

    private val _playbackProgress = MutableLiveData<Float>(0f)
    val playbackProgress: LiveData<Float> = _playbackProgress

    private val _currentPosition = MutableLiveData<Long>(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = _duration

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _operationProgress = MutableLiveData<Int>(-1) // -1 = not running
    val operationProgress: LiveData<Int> = _operationProgress

    // Track active FFmpeg sessions for cancellation
    private val activeFFmpegSessions = mutableListOf<FFmpegSession>()

    private val _fileName = MutableLiveData<String>("")
    val fileName: LiveData<String> = _fileName

    enum class PlaybackState {
        PLAYING, PAUSED, STOPPED
    }

    /**
     * Execute FFmpeg command asynchronously with cancellation support.
     * Returns the session for tracking. Callback is invoked on main thread.
     */
    private fun executeFFmpegAsync(
        command: String,
        onComplete: (success: Boolean, error: String?) -> Unit
    ): FFmpegSession {
        val session = FFmpegKit.executeAsync(command) { completedSession ->
            handler.post {
                synchronized(activeFFmpegSessions) {
                    activeFFmpegSessions.remove(completedSession)
                }
                if (ReturnCode.isSuccess(completedSession.returnCode)) {
                    onComplete(true, null)
                } else if (ReturnCode.isCancel(completedSession.returnCode)) {
                    onComplete(false, "Operation cancelled")
                } else {
                    onComplete(false, completedSession.failStackTrace ?: "FFmpeg operation failed")
                }
            }
        }
        synchronized(activeFFmpegSessions) {
            activeFFmpegSessions.add(session)
        }
        return session
    }

    /**
     * Cancel all active FFmpeg operations.
     */
    fun cancelAllFFmpegOperations() {
        synchronized(activeFFmpegSessions) {
            activeFFmpegSessions.forEach { session ->
                FFmpegKit.cancel(session.sessionId)
            }
            activeFFmpegSessions.clear()
        }
        _operationProgress.value = -1
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            synchronized(mediaPlayerLock) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val position = player.currentPosition.toLong()
                        val total = player.duration.toLong()
                        _currentPosition.value = position
                        _playbackProgress.value = if (total > 0) position.toFloat() / total else 0f
                        handler.postDelayed(this, 50)
                    }
                }
            }
        }
    }

    // ==================== Project Persistence ====================

    /**
     * Check if there's a saved project.
     */
    fun hasSavedProject(): Boolean {
        return projectManager.hasSavedProject()
    }

    /**
     * Start a new empty project, clearing any saved project.
     */
    fun startNewProject() {
        if (projectLoaded) return
        projectLoaded = true

        viewModelScope.launch {
            _isLoading.value = true
            projectManager.clearProject()
            trackList.clear()
            _tracks.value = emptyList()
            _activeTrackIndex.value = -1
            clipboardFilePath = null
            _hasClipboard.value = false
            _isLoading.value = false
        }
    }

    /**
     * Load saved project on startup.
     * Call this from Activity's onCreate after observing LiveData.
     */
    fun loadSavedProject() {
        if (projectLoaded) return
        projectLoaded = true

        viewModelScope.launch {
            _isLoading.value = true
            val savedProject = projectManager.loadProject()
            if (savedProject != null && savedProject.tracks.isNotEmpty()) {
                // Restore tracks
                trackList.clear()
                savedProject.tracks.forEach { track ->
                    // Reload waveform data
                    try {
                        val waveformData = waveformExtractor.extract(track.uri)
                        trackList.add(track.copy(waveformData = waveformData))
                    } catch (_: Exception) {
                        // Use track without waveform if extraction fails
                        trackList.add(track)
                    }
                }

                _tracks.value = trackList.toList()

                // Restore clipboard (Bug 2.21: synchronized access)
                savedProject.clipboardFilePath?.let { path ->
                    if (File(path).exists()) {
                        synchronized(clipboardLock) {
                            clipboardFilePath = path
                        }
                        _hasClipboard.value = true
                    }
                }

                // Set active track
                if (savedProject.activeTrackIndex >= 0 && savedProject.activeTrackIndex < trackList.size) {
                    setActiveTrack(savedProject.activeTrackIndex)
                } else if (trackList.isNotEmpty()) {
                    setActiveTrack(0)
                }

                // Clean up orphaned files
                projectManager.cleanupOrphanedFiles(trackList)
            }
            _isLoading.value = false
        }
    }

    /**
     * Save project state (called automatically after changes).
     * Uses a mutex to prevent concurrent save operations.
     */
    private fun saveProject() {
        viewModelScope.launch(Dispatchers.IO) {
            saveProjectMutex.withLock {
                projectManager.saveProject(trackList, _activeTrackIndex.value ?: -1, clipboardFilePath)
            }
        }
    }

    /**
     * Update undo/redo button state based on active track.
     */
    private fun updateUndoRedoState() {
        val track = getActiveTrack()
        _canUndo.value = track?.canUndo() ?: false
        _canRedo.value = track?.canRedo() ?: false
    }

    /**
     * Clear all project data and start fresh.
     */
    @Suppress("unused")
    fun clearProject() {
        stopPlayback()
        trackList.forEach { it.clearAllHistory() }
        trackList.clear()
        _tracks.value = emptyList()
        _activeTrackIndex.value = -1
        _waveformData.value = null
        _duration.value = 0L
        _fileName.value = ""
        // Bug 2.21: Synchronized clipboard cleanup
        synchronized(clipboardLock) {
            clipboardFilePath?.let { File(it).delete() }
            clipboardFilePath = null
        }
        _hasClipboard.value = false
        mediaPlayer?.release()
        mediaPlayer = null
        projectManager.clearProject()
        updateUndoRedoState()
    }

    /**
     * Get current history size formatted.
     */
    @Suppress("unused")
    fun getHistorySizeFormatted(): String {
        return projectManager.getHistorySizeFormatted(trackList)
    }

    // ==================== Multi-Track Management ====================

    /**
     * Get the currently active track, or null if none.
     */
    fun getActiveTrack(): AudioTrack? {
        val index = _activeTrackIndex.value ?: -1
        return if (index >= 0 && index < trackList.size) trackList[index] else null
    }

    /**
     * Get the file path of the active track.
     */
    private fun getActiveFilePath(): String? = getActiveTrack()?.filePath

    /**
     * Get the URI of the active track.
     */
    @Suppress("unused")
    private fun getActiveUri(): Uri? = getActiveTrack()?.uri

    /**
     * Set the active track by index.
     */
    fun setActiveTrack(index: Int) {
        if (index >= 0 && index < trackList.size) {
            // Stop current playback
            stopPlayback()

            _activeTrackIndex.value = index
            val track = trackList[index]

            // Update UI state for the new active track
            _waveformData.value = track.waveformData
            _duration.value = track.durationMs
            _fileName.value = track.name

            // Prepare MediaPlayer for the new track
            try {
                prepareMediaPlayer(track.uri)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load track: ${e.message}"
            }

            // Update undo/redo state for the new active track (don't clear history!)
            updateUndoRedoState()

            // Save project state
            saveProject()
        }
    }

    /**
     * Remove a track by index.
     */
    fun removeTrack(index: Int) {
        // Bug 2.22: Verify index is valid before proceeding
        if (index < 0 || index >= trackList.size) return

        val track = trackList[index]

        // Clean up the track's history files
        track.clearAllHistory()

        // Clean up the track's audio file
        File(track.filePath).delete()

        trackList.removeAt(index)
        _tracks.value = trackList.toList()

        // Adjust active track index
        val currentActive = _activeTrackIndex.value ?: -1
        when {
            trackList.isEmpty() -> {
                _activeTrackIndex.value = -1
                _waveformData.value = null
                _duration.value = 0L
                _fileName.value = ""
                mediaPlayer?.release()
                mediaPlayer = null
                updateUndoRedoState()
            }
            index == currentActive -> {
                // Active track was removed, switch to first track
                setActiveTrack(0)
            }
            index < currentActive -> {
                // Removed track was before active, adjust index
                _activeTrackIndex.value = currentActive - 1
                updateUndoRedoState()
            }
        }

        // Save project state
        saveProject()
    }

    /**
     * Move a track from one position to another.
     */
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= trackList.size) return
        if (toIndex < 0 || toIndex >= trackList.size) return
        if (fromIndex == toIndex) return

        val track = trackList.removeAt(fromIndex)
        trackList.add(toIndex, track)
        _tracks.value = trackList.toList()

        // Save project state
        saveProject()

        // Adjust active track index if needed
        val currentActive = _activeTrackIndex.value ?: -1
        val newActiveIndex = when {
            currentActive == fromIndex -> toIndex
            fromIndex < currentActive && toIndex >= currentActive -> currentActive - 1
            fromIndex > currentActive && toIndex <= currentActive -> currentActive + 1
            else -> currentActive
        }

        if (newActiveIndex != currentActive) {
            _activeTrackIndex.value = newActiveIndex
        }
    }

    /**
     * Update a track in the list (after edit operations).
     */
    private fun updateActiveTrack(newFilePath: String, newUri: Uri) {
        val index = _activeTrackIndex.value ?: return
        // Bug 2.22: Verify index is still valid before async update
        if (index < 0 || index >= trackList.size) return

        viewModelScope.launch {
            try {
                // Bug 2.22: Re-validate index after suspension point
                val currentIndex = _activeTrackIndex.value ?: return@launch
                if (currentIndex < 0 || currentIndex >= trackList.size) return@launch
                if (currentIndex != index) return@launch // Index changed, abort

                val oldTrack = trackList[currentIndex]
                val newWaveformData = waveformExtractor.extract(newUri)

                val updatedTrack = oldTrack.copy(
                    filePath = newFilePath,
                    uri = newUri,
                    waveformData = newWaveformData,
                    durationMs = newWaveformData.durationMs
                )

                // Bug 2.22: Final index validation before mutation
                if (currentIndex < trackList.size) {
                    trackList[currentIndex] = updatedTrack
                    _tracks.value = trackList.toList()

                    // Update UI
                    _waveformData.value = newWaveformData
                    _duration.value = newWaveformData.durationMs

                    // Prepare new MediaPlayer
                    prepareMediaPlayer(newUri)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update track: ${e.message}"
            }
        }
    }

    /**
     * Add a new track from URI.
     */
    fun addTrack(uri: Uri, displayName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Stop current playback
                stopPlayback()

                val trackName = displayName ?: "Track ${trackList.size + 1}"

                // Copy to persistent storage for FFmpeg operations
                val filePath = copyToPersistentStorage(uri)
                val fileUri = Uri.fromFile(File(filePath))

                // Extract waveform
                val waveformData = waveformExtractor.extract(fileUri)

                // Create new track (starts with empty history)
                val newTrack = AudioTrack(
                    name = trackName,
                    filePath = filePath,
                    uri = fileUri,
                    waveformData = waveformData,
                    durationMs = waveformData.durationMs
                )

                // Add to list
                trackList.add(newTrack)
                _tracks.value = trackList.toList()

                // Set as active track
                val newIndex = trackList.size - 1
                _activeTrackIndex.value = newIndex

                // Update UI state
                _waveformData.value = waveformData
                _duration.value = waveformData.durationMs
                _fileName.value = trackName

                // Prepare MediaPlayer
                prepareMediaPlayer(fileUri)

                // Update undo/redo state (new track has no history)
                updateUndoRedoState()

                // Save project state
                saveProject()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to add track: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Legacy method for compatibility - now calls addTrack.
     */
    fun loadAudioFile(uri: Uri, displayName: String? = null) {
        addTrack(uri, displayName)
    }

    /**
     * Copy audio file to persistent storage (filesDir) for project persistence.
     */
    private suspend fun copyToPersistentStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        val audioDir = File(app.filesDir, "audio_editor/tracks")
        audioDir.mkdirs()

        val fileName = "track_${System.currentTimeMillis()}.wav"
        val destFile = File(audioDir, fileName)
        app.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    }

    private fun prepareMediaPlayer(uri: Uri) {
        synchronized(mediaPlayerLock) {
            // Release old player first
            mediaPlayer?.release()
            mediaPlayer = null

            // Create new player with proper error handling to prevent leaks
            var newPlayer: MediaPlayer? = null
            try {
                newPlayer = MediaPlayer().apply {
                    setDataSource(app, uri)
                    prepare()
                    setOnCompletionListener {
                        _playbackState.value = PlaybackState.STOPPED
                        _playbackProgress.value = 0f
                        _currentPosition.value = 0L
                    }
                }
                mediaPlayer = newPlayer
            } catch (e: Exception) {
                // Release the player if prepare() or any step fails
                newPlayer?.release()
                mediaPlayer = null
                throw e
            }
        }
    }

    // Playback controls
    fun play() {
        synchronized(mediaPlayerLock) {
            mediaPlayer?.let { player ->
                player.start()
                _playbackState.value = PlaybackState.PLAYING
                handler.post(progressRunnable)
            }
        }
    }

    fun pause() {
        synchronized(mediaPlayerLock) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _playbackState.value = PlaybackState.PAUSED
                    handler.removeCallbacks(progressRunnable)
                }
            }
        }
    }

    fun stopPlayback() {
        synchronized(mediaPlayerLock) {
            mediaPlayer?.let { player ->
                try {
                    player.stop()
                    player.prepare()
                    player.seekTo(0)
                } catch (e: Exception) {
                    // Bug fix: Handle prepare() failure gracefully
                    // Player may be in an invalid state, log but don't crash
                    android.util.Log.w("AudioEditorViewModel", "Error resetting player in stopPlayback", e)
                }
                _playbackState.value = PlaybackState.STOPPED
                _playbackProgress.value = 0f
                _currentPosition.value = 0L
                handler.removeCallbacks(progressRunnable)
            }
        }
    }

    fun seekTo(progress: Float) {
        synchronized(mediaPlayerLock) {
            mediaPlayer?.let { player ->
                val duration = player.duration
                // Guard against division by zero or invalid duration
                if (duration <= 0) return@synchronized

                val position = (progress * duration).toInt().coerceIn(0, duration)
                player.seekTo(position)
                _currentPosition.value = position.toLong()
                _playbackProgress.value = progress.coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Export audio to different format using FFmpeg.
     */
    fun exportAudio(
        outputPath: String,
        format: ExportFormat,
        bitrate: Int = 320,
        metadata: AudioMetadata? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        val command = buildExportCommand(inputPath, outputPath, format, bitrate, metadata)
        if (command == null) {
            onComplete(false, "Invalid file path")
            return
        }

        _operationProgress.value = 0
        executeFFmpegAsync(command) { success, error ->
            _operationProgress.value = -1
            onComplete(success, error)
        }
    }

    private fun buildExportCommand(
        inputPath: String,
        outputPath: String,
        format: ExportFormat,
        bitrate: Int,
        metadata: AudioMetadata? = null
    ): String? {
        // Validate paths to prevent command injection
        val safeInputPath = AudioMetadata.sanitizeFilePath(inputPath) ?: return null
        val safeOutputPath = AudioMetadata.sanitizeFilePath(outputPath) ?: return null

        val metadataArgs = metadata?.toFFmpegArgs() ?: ""

        return when (format) {
            ExportFormat.MP3 -> "-i \"$safeInputPath\"$metadataArgs -b:a ${bitrate}k -y \"$safeOutputPath\""
            ExportFormat.WAV -> "-i \"$safeInputPath\"$metadataArgs -ar 44100 -sample_fmt s16 -y \"$safeOutputPath\""
            ExportFormat.FLAC -> "-i \"$safeInputPath\"$metadataArgs -compression_level 8 -y \"$safeOutputPath\""
            ExportFormat.OGG -> "-i \"$safeInputPath\"$metadataArgs -c:a libvorbis -b:a ${bitrate}k -y \"$safeOutputPath\""
            ExportFormat.AAC -> "-i \"$safeInputPath\"$metadataArgs -c:a aac -b:a ${bitrate}k -y \"$safeOutputPath\""
        }
    }

    /**
     * Escape a file path for use in FFmpeg concat list files.
     * FFmpeg concat format uses single quotes, so we need to escape them properly.
     */
    private fun escapePathForConcatList(path: String): String {
        // In FFmpeg concat list, single quotes need to be escaped
        return path.replace("'", "'\\''")
    }

    /**
     * Generate a safe output path in the persistent storage directory.
     */
    private fun generateTrimmedOutputPath(): String {
        val audioDir = File(app.filesDir, "audio_editor/tracks")
        audioDir.mkdirs()
        return File(audioDir, "audio_${System.currentTimeMillis()}_trimmed.wav").absolutePath
    }

    // ==================== Undo/Redo System (Per-Track) ====================

    /**
     * Save current state to the active track's undo stack before destructive operation.
     */
    private suspend fun saveStateForUndo(): Boolean = withContext(Dispatchers.IO) {
        val track = getActiveTrack() ?: return@withContext false
        val currentPath = track.filePath

        val currentFile = File(currentPath)
        if (!currentFile.exists()) return@withContext false

        // Create a backup copy in the track's history directory
        val backupPath = projectManager.generateHistoryFilePath(track.id, "undo")
        try {
            currentFile.copyTo(File(backupPath), overwrite = true)

            // Add to track's undo stack
            track.undoStack.add(backupPath)

            // Clear redo stack when new action is performed
            track.clearRedoHistory()

            // Enforce size limit across all tracks
            projectManager.enforceHistorySizeLimit(trackList)

            withContext(Dispatchers.Main) {
                updateUndoRedoState()
                // Save project with updated history
                saveProject()
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Undo the last destructive operation on the active track.
     */
    fun undo(onComplete: (Boolean, String?) -> Unit) {
        val track = getActiveTrack()
        if (track == null || !track.canUndo()) {
            onComplete(false, "Nothing to undo")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            withContext(Dispatchers.IO) {
                try {
                    val currentPath = track.filePath
                    val currentFile = File(currentPath)

                    // Save current state to redo stack
                    if (currentFile.exists()) {
                        val redoPath = projectManager.generateHistoryFilePath(track.id, "redo")
                        currentFile.copyTo(File(redoPath), overwrite = true)
                        track.redoStack.add(redoPath)
                    }

                    // Restore from undo stack
                    val restorePath = track.undoStack.removeAt(track.undoStack.lastIndex)
                    val restoreFile = File(restorePath)

                    if (restoreFile.exists()) {
                        withContext(Dispatchers.Main) {
                            updateUndoRedoState()
                            _operationProgress.value = -1

                            updateActiveTrack(restorePath, Uri.fromFile(restoreFile))
                            saveProject()
                            onComplete(true, null)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Undo file not found")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _operationProgress.value = -1
                        onComplete(false, "Undo failed: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Redo the last undone operation on the active track.
     */
    fun redo(onComplete: (Boolean, String?) -> Unit) {
        val track = getActiveTrack()
        if (track == null || !track.canRedo()) {
            onComplete(false, "Nothing to redo")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            withContext(Dispatchers.IO) {
                try {
                    val currentPath = track.filePath
                    val currentFile = File(currentPath)

                    // Save current state to undo stack
                    if (currentFile.exists()) {
                        val undoPath = projectManager.generateHistoryFilePath(track.id, "undo")
                        currentFile.copyTo(File(undoPath), overwrite = true)
                        track.undoStack.add(undoPath)
                    }

                    // Restore from redo stack
                    val restorePath = track.redoStack.removeAt(track.redoStack.lastIndex)
                    val restoreFile = File(restorePath)

                    if (restoreFile.exists()) {
                        withContext(Dispatchers.Main) {
                            updateUndoRedoState()
                            _operationProgress.value = -1

                            updateActiveTrack(restorePath, Uri.fromFile(restoreFile))
                            saveProject()
                            onComplete(true, null)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Redo file not found")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _operationProgress.value = -1
                        onComplete(false, "Redo failed: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Clear undo/redo history for all tracks.
     */
    @Suppress("unused")
    fun clearHistory() {
        trackList.forEach { it.clearAllHistory() }
        updateUndoRedoState()
        saveProject()
    }

    // ==================== Audio Operations ====================

    /**
     * Cut a portion of the audio (keep only the selected region).
     */
    fun trimAudio(startMs: Long, endMs: Long, onComplete: (Boolean, String?) -> Unit) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            // Save state for undo before destructive operation
            saveStateForUndo()

            val outputPath = generateTrimmedOutputPath()
            val startSec = startMs / 1000.0
            val endSec = endMs / 1000.0

            val command = "-i \"$inputPath\" -ss $startSec -to $endSec -ar 44100 -sample_fmt s16 -y \"$outputPath\""

            executeFFmpegAsync(command) { success, error ->
                _operationProgress.value = -1
                if (success) {
                    // Update the active track with new file
                    updateActiveTrack(outputPath, Uri.fromFile(File(outputPath)))
                    onComplete(true, null)
                } else {
                    onComplete(false, error ?: "Trim failed")
                }
            }
        }
    }

    enum class ExportFormat(val extension: String, val displayName: String) {
        MP3("mp3", "MP3"),
        WAV("wav", "WAV"),
        FLAC("flac", "FLAC"),
        OGG("ogg", "OGG Vorbis"),
        AAC("m4a", "AAC")
    }

    // ==================== Clipboard Operations ====================

    /**
     * Copy selection to clipboard (extract audio segment to temp file).
     */
    fun copySelection(startMs: Long, endMs: Long, onComplete: (Boolean, String?) -> Unit) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        _operationProgress.value = 0

        // Bug 2.21: Clean up previous clipboard with synchronization
        synchronized(clipboardLock) {
            clipboardFilePath?.let { File(it).delete() }
        }

        val clipboardPath = File(app.cacheDir, "audio_clipboard_${System.currentTimeMillis()}.wav").absolutePath
        val startSec = startMs / 1000.0
        val endSec = endMs / 1000.0

        val command = "-i \"$inputPath\" -ss $startSec -to $endSec -ar 44100 -sample_fmt s16 -y \"$clipboardPath\""

        executeFFmpegAsync(command) { success, error ->
            _operationProgress.value = -1
            if (success) {
                // Bug 2.21: Synchronized clipboard write
                synchronized(clipboardLock) {
                    clipboardFilePath = clipboardPath
                }
                _hasClipboard.value = true
                onComplete(true, null)
            } else {
                onComplete(false, error ?: "Copy failed")
            }
        }
    }

    /**
     * Cut selection (copy to clipboard then remove from audio).
     */
    fun cutSelection(startMs: Long, endMs: Long, onComplete: (Boolean, String?) -> Unit) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            // Save state for undo before destructive operation
            saveStateForUndo()

            // Bug 2.21: First, copy to clipboard with synchronization
            synchronized(clipboardLock) {
                clipboardFilePath?.let { File(it).delete() }
            }
            val clipboardPath = File(app.cacheDir, "audio_clipboard_${System.currentTimeMillis()}.wav").absolutePath
            val startSec = startMs / 1000.0
            val endSec = endMs / 1000.0

            val copyCommand = "-i \"$inputPath\" -ss $startSec -to $endSec -ar 44100 -sample_fmt s16 -y \"$clipboardPath\""

            withContext(Dispatchers.IO) {
                val copySession = FFmpegKit.execute(copyCommand)

                if (!ReturnCode.isSuccess(copySession.returnCode)) {
                    withContext(Dispatchers.Main) {
                        _operationProgress.value = -1
                        onComplete(false, "Failed to copy to clipboard")
                    }
                    return@withContext
                }

                // Bug 2.21: Synchronized clipboard write
                synchronized(clipboardLock) {
                    clipboardFilePath = clipboardPath
                }
                _hasClipboard.postValue(true)

                // Now remove the selection from original
                // We need to keep [0, startMs] and [endMs, end] and concatenate
                val totalDuration = _duration.value ?: 0L
                val timestamp = System.currentTimeMillis()
                val part1Path = File(tempCacheDir, "cut_part1_$timestamp.wav").absolutePath
                val part2Path = File(tempCacheDir, "cut_part2_$timestamp.wav").absolutePath
                val outputPath = File(tempCacheDir, "audio_${timestamp}_cut.wav").absolutePath

                // Extract part before selection
                val hasPartBefore = startMs > 0 // Only if selection doesn't start at beginning
                if (hasPartBefore) {
                    val part1Command = "-i \"$inputPath\" -ss 0 -to $startSec -ar 44100 -sample_fmt s16 -y \"$part1Path\""
                    val part1Session = FFmpegKit.execute(part1Command)
                    if (!ReturnCode.isSuccess(part1Session.returnCode)) {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part before selection")
                        }
                        return@withContext
                    }
                }

                // Extract part after selection
                val hasPartAfter = endMs < totalDuration // Only if selection doesn't end at end
                if (hasPartAfter) {
                    val part2Command = "-i \"$inputPath\" -ss $endSec -ar 44100 -sample_fmt s16 -y \"$part2Path\""
                    val part2Session = FFmpegKit.execute(part2Command)
                    if (!ReturnCode.isSuccess(part2Session.returnCode)) {
                        File(part1Path).delete()
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part after selection")
                        }
                        return@withContext
                    }
                }

                // Concatenate parts
                val concatCommand = when {
                    hasPartBefore && hasPartAfter -> {
                        // Create concat file
                        val concatListFile = File(app.cacheDir, "concat_list.txt")
                        concatListFile.writeText("file '${escapePathForConcatList(part1Path)}'\nfile '${escapePathForConcatList(part2Path)}'")
                        "-f concat -safe 0 -i \"${concatListFile.absolutePath}\" -ar 44100 -sample_fmt s16 -y \"$outputPath\""
                    }
                    hasPartBefore -> {
                        // Only keep part before
                        "-i \"$part1Path\" -c copy -y \"$outputPath\""
                    }
                    hasPartAfter -> {
                        // Only keep part after
                        "-i \"$part2Path\" -c copy -y \"$outputPath\""
                    }
                    else -> {
                        // Nothing left
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Cannot cut entire audio")
                        }
                        return@withContext
                    }
                }

                val concatSession = FFmpegKit.execute(concatCommand)

                // Clean up temp files
                File(part1Path).delete()
                File(part2Path).delete()
                File(app.cacheDir, "concat_list.txt").delete()

                withContext(Dispatchers.Main) {
                    _operationProgress.value = -1

                    if (ReturnCode.isSuccess(concatSession.returnCode)) {
                        // Update the active track with new file
                        updateActiveTrack(outputPath, Uri.fromFile(File(outputPath)))
                        onComplete(true, null)
                    } else {
                        onComplete(false, concatSession.failStackTrace ?: "Cut failed")
                    }
                }
            }
        }
    }

    /**
     * Delete selection (remove without copying to clipboard).
     */
    fun deleteSelection(startMs: Long, endMs: Long, onComplete: (Boolean, String?) -> Unit) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            // Save state for undo before destructive operation
            saveStateForUndo()

            withContext(Dispatchers.IO) {
                val startSec = startMs / 1000.0
                val endSec = endMs / 1000.0
                val totalDuration = _duration.value ?: 0L
                val timestamp = System.currentTimeMillis()

                val part1Path = File(tempCacheDir, "del_part1_$timestamp.wav").absolutePath
                val part2Path = File(tempCacheDir, "del_part2_$timestamp.wav").absolutePath
                val outputPath = File(tempCacheDir, "audio_${timestamp}_deleted.wav").absolutePath

                // Extract part before selection
                val hasPartBefore = startMs > 0
                if (hasPartBefore) {
                    val part1Command = "-i \"$inputPath\" -ss 0 -to $startSec -ar 44100 -sample_fmt s16 -y \"$part1Path\""
                    val part1Session = FFmpegKit.execute(part1Command)
                    if (!ReturnCode.isSuccess(part1Session.returnCode)) {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part before selection")
                        }
                        return@withContext
                    }
                }

                // Extract part after selection
                val hasPartAfter = endMs < totalDuration
                if (hasPartAfter) {
                    val part2Command = "-i \"$inputPath\" -ss $endSec -ar 44100 -sample_fmt s16 -y \"$part2Path\""
                    val part2Session = FFmpegKit.execute(part2Command)
                    if (!ReturnCode.isSuccess(part2Session.returnCode)) {
                        File(part1Path).delete()
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part after selection")
                        }
                        return@withContext
                    }
                }

                // Concatenate remaining parts
                val concatCommand = when {
                    hasPartBefore && hasPartAfter -> {
                        val concatListFile = File(tempCacheDir, "concat_list.txt")
                        concatListFile.writeText("file '${escapePathForConcatList(part1Path)}'\nfile '${escapePathForConcatList(part2Path)}'")
                        "-f concat -safe 0 -i \"${concatListFile.absolutePath}\" -ar 44100 -sample_fmt s16 -y \"$outputPath\""
                    }
                    hasPartBefore -> {
                        "-i \"$part1Path\" -c copy -y \"$outputPath\""
                    }
                    hasPartAfter -> {
                        "-i \"$part2Path\" -c copy -y \"$outputPath\""
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Cannot delete entire audio")
                        }
                        return@withContext
                    }
                }

                val concatSession = FFmpegKit.execute(concatCommand)

                // Clean up temp files
                File(part1Path).delete()
                File(part2Path).delete()
                File(tempCacheDir, "concat_list.txt").delete()

                withContext(Dispatchers.Main) {
                    _operationProgress.value = -1

                    if (ReturnCode.isSuccess(concatSession.returnCode)) {
                        updateActiveTrack(outputPath, Uri.fromFile(File(outputPath)))
                        onComplete(true, null)
                    } else {
                        onComplete(false, concatSession.failStackTrace ?: "Delete failed")
                    }
                }
            }
        }
    }

    /**
     * Paste clipboard content at specified position.
     */
    fun pasteAtPosition(positionMs: Long, onComplete: (Boolean, String?) -> Unit) {
        val inputPath = getActiveFilePath() ?: run {
            onComplete(false, "No audio file loaded")
            return
        }

        // Bug 2.21: Synchronized clipboard read
        val clipPath: String
        synchronized(clipboardLock) {
            clipPath = clipboardFilePath ?: run {
                onComplete(false, "Clipboard is empty")
                return
            }
        }

        if (!File(clipPath).exists()) {
            _hasClipboard.value = false
            onComplete(false, "Clipboard content not found")
            return
        }

        viewModelScope.launch {
            _operationProgress.value = 0

            // Save state for undo before destructive operation
            saveStateForUndo()

            withContext(Dispatchers.IO) {
                val positionSec = positionMs / 1000.0
                val totalDuration = _duration.value ?: 0L
                val timestamp = System.currentTimeMillis()

                val part1Path = File(tempCacheDir, "paste_part1_$timestamp.wav").absolutePath
                val part2Path = File(tempCacheDir, "paste_part2_$timestamp.wav").absolutePath
                val outputPath = File(tempCacheDir, "audio_${timestamp}_pasted.wav").absolutePath

                // Extract part before paste position
                val hasPartBefore = positionMs > 0
                if (hasPartBefore) {
                    val part1Command = "-i \"$inputPath\" -ss 0 -to $positionSec -ar 44100 -sample_fmt s16 -y \"$part1Path\""
                    val part1Session = FFmpegKit.execute(part1Command)
                    if (!ReturnCode.isSuccess(part1Session.returnCode)) {
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part before position")
                        }
                        return@withContext
                    }
                }

                // Extract part after paste position
                val hasPartAfter = positionMs < totalDuration
                if (hasPartAfter) {
                    val part2Command = "-i \"$inputPath\" -ss $positionSec -ar 44100 -sample_fmt s16 -y \"$part2Path\""
                    val part2Session = FFmpegKit.execute(part2Command)
                    if (!ReturnCode.isSuccess(part2Session.returnCode)) {
                        File(part1Path).delete()
                        withContext(Dispatchers.Main) {
                            _operationProgress.value = -1
                            onComplete(false, "Failed to extract part after position")
                        }
                        return@withContext
                    }
                }

                // Create concat list (part1 + clipboard + part2)
                val concatListFile = File(app.cacheDir, "concat_list.txt")
                val concatContent = StringBuilder()
                if (hasPartBefore) concatContent.append("file '${escapePathForConcatList(part1Path)}'\n")
                concatContent.append("file '${escapePathForConcatList(clipPath)}'\n")
                if (hasPartAfter) concatContent.append("file '${escapePathForConcatList(part2Path)}'\n")
                concatListFile.writeText(concatContent.toString())

                val concatCommand = "-f concat -safe 0 -i \"${concatListFile.absolutePath}\" -ar 44100 -sample_fmt s16 -y \"$outputPath\""
                val concatSession = FFmpegKit.execute(concatCommand)

                // Clean up temp files
                File(part1Path).delete()
                File(part2Path).delete()
                concatListFile.delete()

                withContext(Dispatchers.Main) {
                    _operationProgress.value = -1

                    if (ReturnCode.isSuccess(concatSession.returnCode)) {
                        // Update the active track with new file
                        updateActiveTrack(outputPath, Uri.fromFile(File(outputPath)))
                        onComplete(true, null)
                    } else {
                        onComplete(false, concatSession.failStackTrace ?: "Paste failed")
                    }
                }
            }
        }
    }

    /**
     * Clear the clipboard.
     */
    @Suppress("unused")
    fun clearClipboard() {
        // Bug 2.21: Synchronized clipboard clear
        synchronized(clipboardLock) {
            clipboardFilePath?.let { File(it).delete() }
            clipboardFilePath = null
        }
        _hasClipboard.value = false
    }

    // ==================== Recording Methods ====================

    private var recordingCollectorJob: kotlinx.coroutines.Job? = null
    private var currentRecordingSessionId: Long = 0
    private var processedRecordingSessionId: Long = 0

    /**
     * Start recording from the microphone.
     */
    fun startRecording() {
        // Cancel any previous collector
        recordingCollectorJob?.cancel()

        // Generate new session ID to ignore any old Completed states
        currentRecordingSessionId = System.currentTimeMillis()

        _isRecording.value = true

        // Reset the recorder state first
        audioRecorder.reset()

        // Start collectors for recording state
        val sessionId = currentRecordingSessionId
        recordingCollectorJob = viewModelScope.launch {
            // Collect amplitude updates
            launch {
                audioRecorder.amplitude.collect { amplitude ->
                    _recordingAmplitude.value = amplitude
                }
            }

            // Collect duration updates
            launch {
                audioRecorder.recordingDuration.collect { duration ->
                    _recordingDurationMs.value = duration
                }
            }

            // Collect state changes - use drop(1) to skip initial state
            launch {
                audioRecorder.recordingState
                    .collect { state ->
                        // Ignore if this is from a different session
                        if (sessionId != currentRecordingSessionId) return@collect

                        when (state) {
                            is AudioRecorder.RecordingState.Completed -> {
                                // Prevent duplicate processing for same session
                                if (processedRecordingSessionId == sessionId) return@collect
                                processedRecordingSessionId = sessionId

                                _isRecording.value = false
                                // Cancel collector to prevent re-processing
                                recordingCollectorJob?.cancel()
                                recordingCollectorJob = null

                                // Load the recorded file on main thread
                                handler.post {
                                    val trackNumber = trackList.size + 1
                                    val recordingName = app.getString(
                                        com.Atom2Universe.app.R.string.audio_editor_recording_name,
                                        trackNumber
                                    )
                                    loadAudioFile(Uri.fromFile(state.file), recordingName)
                                }
                            }
                            is AudioRecorder.RecordingState.Error -> {
                                _isRecording.value = false
                                _errorMessage.value = state.message
                            }
                            is AudioRecorder.RecordingState.Idle -> {
                                // Ignore idle state - we already set _isRecording = true
                            }
                            is AudioRecorder.RecordingState.Recording -> {
                                // Recording started successfully
                            }
                        }
                    }
            }
        }

        // Start recording (this is now synchronous, starts thread internally)
        audioRecorder.startRecording()
    }

    /**
     * Stop the current recording.
     */
    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    /**
     * Cancel the current recording without saving.
     */
    fun cancelRecording() {
        recordingCollectorJob?.cancel()
        recordingCollectorJob = null
        audioRecorder.cancelRecording()
        _isRecording.value = false
        _recordingAmplitude.value = 0f
        _recordingDurationMs.value = 0L
    }

    /**
     * Check if we have permission to record audio.
     */
    @Suppress("unused")
    fun hasRecordPermission(): Boolean = audioRecorder.hasRecordPermission()

    override fun onCleared() {
        super.onCleared()
        // Bug 2.20: Remove all pending handler callbacks to prevent leaks
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null

        // Cancel recording collector job to prevent leaks
        recordingCollectorJob?.cancel()
        recordingCollectorJob = null

        // Cancel all active FFmpeg operations
        cancelAllFFmpegOperations()

        // Stop any ongoing recording
        if (_isRecording.value == true) {
            audioRecorder.cancelRecording()
        }

        // Save project state before ViewModel is cleared
        // Note: viewModelScope is cancelled, so we use runBlocking with Dispatchers.IO
        // to move the I/O off the main thread while still completing before onCleared returns
        try {
            // Bug 2.21: Synchronized clipboard read for saving
            val clipPath = synchronized(clipboardLock) { clipboardFilePath }
            runBlocking(Dispatchers.IO) {
                projectManager.saveProject(trackList, _activeTrackIndex.value ?: -1, clipPath)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioEditorViewModel", "Failed to save project in onCleared", e)
        }

        // Clean up old temporary recordings (but keep project data)
        audioRecorder.cleanupOldRecordings()
    }
}
