package com.Atom2Universe.app.audioeditor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.AudioHubActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main Activity for the Audio Editor module.
 * Provides audio playback, waveform visualization, recording, and basic editing capabilities.
 */
class AudioEditorActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
    }

    private lateinit var viewModel: AudioEditorViewModel

    // Views - Tracks
    // Bug 2.29: trackViews access synchronized via trackViewsLock
    private lateinit var tracksContainer: android.widget.LinearLayout
    private lateinit var tracksScrollView: android.widget.ScrollView
    private lateinit var tvEmptyState: TextView
    private var activeWaveformView: WaveformView? = null
    private var activeSpectrumView: SpectrumView? = null
    private val trackViewsLock = Any()
    private val trackViews = mutableMapOf<Int, View>()

    // Views - Playback
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnOpen: MaterialButton
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnTrim: ImageButton
    private lateinit var tvFileName: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View
    private lateinit var btnBack: ImageButton

    // Views - Edit toolbar
    private lateinit var btnCopy: ImageButton
    private lateinit var btnCut: ImageButton
    private lateinit var btnPaste: ImageButton
    private lateinit var btnDelete: ImageButton

    // Views - Undo/Redo
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton

    // Views - Recording
    private lateinit var recordingOverlay: View
    private lateinit var recordingIndicator: View
    private lateinit var tvRecordingDuration: TextView
    private lateinit var recordingWaveformView: RecordingWaveformView
    private lateinit var btnCancelRecording: MaterialButton
    private lateinit var btnStopRecording: ImageButton

    // Blink animation for recording indicator
    private val blinkAnimation = AlphaAnimation(1f, 0f).apply {
        duration = 500
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
    }

    // Bug 2.40: File picker with error handling
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val displayName = getFileName(uri)
                    viewModel.loadAudioFile(uri, displayName)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEditorActivity", "Error handling file picker result", e)
            Toast.makeText(this, getString(R.string.audio_editor_error_generic, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    // Export file picker
    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingExportFormat?.let { format ->
                    performExport(uri, format)
                }
            }
        }
    }

    private var pendingExportFormat: AudioEditorViewModel.ExportFormat? = null
    private var pendingBitrate = 320
    private var pendingMetadata: AudioMetadata? = null
    private lateinit var btnToggleVisualMode: ImageButton
    private val waveformExtractor by lazy { WaveformExtractor(applicationContext) }
    private val fftProcessor = FFTProcessor()
    private var currentVisualMode = VisualMode.WAVEFORM

    // Track spectrogram loading jobs per track to allow cancellation
    private val spectrogramLoadingJobs = mutableMapOf<Int, Job>()

    // Bug 2.41: Throttle recording waveform updates (every 50ms)
    private var lastWaveformUpdateTime = 0L
    private val waveformUpdateThrottleMs = 50L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_audio_editor)

        viewModel = ViewModelProvider(this)[AudioEditorViewModel::class.java]

        initViews()
        setupListeners()
        observeViewModel()

        // Check if there's a saved project and show choice dialog
        checkForSavedProject()
    }

    private fun checkForSavedProject() {
        if (viewModel.hasSavedProject()) {
            // Show dialog to choose between continue or new project
            AlertDialog.Builder(this)
                .setTitle(R.string.audio_editor_title)
                .setMessage(R.string.audio_editor_project_found_message)
                .setPositiveButton(R.string.common_continue) { _, _ ->
                    viewModel.loadSavedProject()
                }
                .setNegativeButton(R.string.audio_editor_new_project) { _, _ ->
                    viewModel.startNewProject()
                }
                .setCancelable(false)
                .show()
        } else {
            // No saved project, start fresh
            viewModel.startNewProject()
        }
    }

    private fun initViews() {
        // Tracks views
        tracksContainer = findViewById(R.id.tracksContainer)
        tracksScrollView = findViewById(R.id.tracksScrollView)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        // Playback views
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnOpen = findViewById(R.id.btnOpen)
        btnRecord = findViewById(R.id.btnRecord)
        btnExport = findViewById(R.id.btnExport)
        btnTrim = findViewById(R.id.btnTrim)
        tvFileName = findViewById(R.id.tvFileName)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvDuration = findViewById(R.id.tvDuration)
        seekBar = findViewById(R.id.seekBar)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        btnBack = findViewById(R.id.btnBack)
        // Recording views
        recordingOverlay = findViewById(R.id.recordingOverlay)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        tvRecordingDuration = findViewById(R.id.tvRecordingDuration)
        recordingWaveformView = findViewById(R.id.recordingWaveformView)
        btnCancelRecording = findViewById(R.id.btnCancelRecording)
        btnStopRecording = findViewById(R.id.btnStopRecording)

        // Edit toolbar
        btnCopy = findViewById(R.id.btnCopy)
        btnCut = findViewById(R.id.btnCut)
        btnPaste = findViewById(R.id.btnPaste)
        btnDelete = findViewById(R.id.btnDelete)

        // Undo/Redo
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        // Visual mode toggle
        btnToggleVisualMode = findViewById(R.id.btnToggleVisualMode)

        // Initial state
        updatePlaybackButtons(AudioEditorViewModel.PlaybackState.STOPPED)
        btnExport.isEnabled = false
        btnTrim.isEnabled = false
        updateEditButtons(hasSelection = false, hasClipboard = false)
        updateToggleButtonIcon()
    }

    private fun setupListeners() {
        // Navigation
        btnBack.setOnClickListener { navigateBackToHub() }

        // File operations
        btnOpen.setOnClickListener { openFilePicker() }
        btnExport.setOnClickListener { showExportDialog() }

        // Playback controls
        btnPlay.setOnClickListener { viewModel.play() }
        btnPause.setOnClickListener { viewModel.pause() }
        btnStop.setOnClickListener { viewModel.stopPlayback() }
        btnTrim.setOnClickListener { trimSelection() }

        // Edit toolbar
        btnCopy.setOnClickListener { copySelection() }
        btnCut.setOnClickListener { cutSelection() }
        btnPaste.setOnClickListener { pasteAtPlayhead() }
        btnDelete.setOnClickListener { deleteSelection() }

        // Undo/Redo
        btnUndo.setOnClickListener { performUndo() }
        btnRedo.setOnClickListener { performRedo() }

        // Visual mode toggle
        btnToggleVisualMode.setOnClickListener {
            currentVisualMode = when (currentVisualMode) {
                VisualMode.WAVEFORM -> VisualMode.SPECTROGRAM
                VisualMode.SPECTROGRAM -> VisualMode.WAVEFORM
            }
            updateToggleButtonIcon()
        }

        // Recording
        btnRecord.setOnClickListener { startRecordingWithPermission() }
        btnStopRecording.setOnClickListener { stopRecording() }
        btnCancelRecording.setOnClickListener { cancelRecording() }

        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val progressFloat = progress / 1000f
                    viewModel.seekTo(progressFloat)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Waveform listeners are now set up in refreshTrackViews()
    }

    private fun observeViewModel() {
        // Tracks list
        viewModel.tracks.observe(this) { tracks ->
            refreshTrackViews(tracks)
        }

        // Active track index
        viewModel.activeTrackIndex.observe(this) { activeIndex ->
            updateActiveTrackHighlight(activeIndex)
        }

        // Waveform data
        viewModel.waveformData.observe(this) { data ->
            data?.let {
                activeWaveformView?.setWaveformData(it)
                btnExport.isEnabled = true
            }
        }

        // Playback state
        viewModel.playbackState.observe(this) { state ->
            updatePlaybackButtons(state)
        }

        viewModel.playbackProgress.observe(this) { progress ->
            seekBar.progress = (progress * 1000).toInt()
            activeWaveformView?.setPlaybackProgress(progress)
            activeSpectrumView?.setPlaybackProgress(progress)
        }

        viewModel.currentPosition.observe(this) { position ->
            tvCurrentTime.text = formatTime(position)
        }

        viewModel.duration.observe(this) { duration ->
            tvDuration.text = formatTime(duration)
        }

        // Loading state
        viewModel.isLoading.observe(this) { isLoading ->
            loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Errors
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        // File name
        viewModel.fileName.observe(this) { name ->
            tvFileName.text = name
        }

        // Operation progress
        viewModel.operationProgress.observe(this) { progress ->
            if (progress >= 0) {
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
            } else {
                progressBar.visibility = View.GONE
            }
        }

        // Recording state - show/hide overlay based on recording state
        viewModel.isRecording.observe(this) { isRecording ->
            if (isRecording) {
                // Make sure overlay is visible when recording
                if (recordingOverlay.visibility != View.VISIBLE) {
                    showRecordingOverlay()
                }
            } else {
                hideRecordingOverlay()
            }
        }

        // Bug 2.41: Throttle recording waveform updates to prevent UI overload
        viewModel.recordingAmplitude.observe(this) { amplitude ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastWaveformUpdateTime >= waveformUpdateThrottleMs) {
                recordingWaveformView.setCurrentAmplitude(amplitude)
                recordingWaveformView.addAmplitude(amplitude)
                lastWaveformUpdateTime = currentTime
            }
        }

        viewModel.recordingDurationMs.observe(this) { durationMs ->
            tvRecordingDuration.text = formatTime(durationMs)
        }

        // Clipboard state
        viewModel.hasClipboard.observe(this) { hasClipboard ->
            val hasSelection = getActiveSelection() != null
            updateEditButtons(hasSelection, hasClipboard)
        }

        // Undo/Redo state
        viewModel.canUndo.observe(this) { canUndo ->
            updateUndoRedoButtons(canUndo, viewModel.canRedo.value ?: false)
        }

        viewModel.canRedo.observe(this) { canRedo ->
            updateUndoRedoButtons(viewModel.canUndo.value ?: false, canRedo)
        }
    }

    // ==================== Recording ====================

    private fun startRecordingWithPermission() {
        if (checkRecordPermission()) {
            // Show overlay immediately for better UX
            showRecordingOverlay()
            viewModel.startRecording()
        } else {
            requestRecordPermission()
        }
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.common_permission_required)
                .setMessage(R.string.audio_editor_mic_permission_message)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        PERMISSION_REQUEST_RECORD_AUDIO
                    )
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Show overlay and start recording
                showRecordingOverlay()
                viewModel.startRecording()
            } else {
                Toast.makeText(this, R.string.audio_editor_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        viewModel.stopRecording()
    }

    private fun cancelRecording() {
        viewModel.cancelRecording()
    }

    private fun showRecordingOverlay() {
        recordingOverlay.visibility = View.VISIBLE
        recordingWaveformView.clear()
        recordingIndicator.startAnimation(blinkAnimation)
    }

    private fun hideRecordingOverlay() {
        recordingOverlay.visibility = View.GONE
        recordingIndicator.clearAnimation()
    }

    // ==================== Playback ====================

    private fun updatePlaybackButtons(state: AudioEditorViewModel.PlaybackState) {
        when (state) {
            AudioEditorViewModel.PlaybackState.PLAYING -> {
                btnPlay.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
            }
            AudioEditorViewModel.PlaybackState.PAUSED,
            AudioEditorViewModel.PlaybackState.STOPPED -> {
                btnPlay.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
            }
        }
    }

    // ==================== File Operations ====================

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        openFileLauncher.launch(intent)
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_audio, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Format spinner
        val spinnerFormat = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerFormat)
        val formats = AudioEditorViewModel.ExportFormat.entries
        val formatAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            formats.map { it.displayName }
        )
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFormat.adapter = formatAdapter

        // Bitrate spinner
        val bitrateContainer = dialogView.findViewById<View>(R.id.bitrateContainer)
        val spinnerBitrate = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerBitrate)
        val bitrates = listOf("128 kbps", "192 kbps", "256 kbps", "320 kbps")
        val bitrateValues = listOf(128, 192, 256, 320)
        val bitrateAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            bitrates
        )
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBitrate.adapter = bitrateAdapter
        spinnerBitrate.setSelection(3) // Default to 320 kbps

        // Show/hide bitrate based on format
        spinnerFormat.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val format = formats[position]
                bitrateContainer.visibility = if (format == AudioEditorViewModel.ExportFormat.WAV ||
                    format == AudioEditorViewModel.ExportFormat.FLAC) View.GONE else View.VISIBLE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Metadata fields
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etTitle)
        val etArtist = dialogView.findViewById<android.widget.EditText>(R.id.etArtist)
        val etAlbum = dialogView.findViewById<android.widget.EditText>(R.id.etAlbum)
        val etYear = dialogView.findViewById<android.widget.EditText>(R.id.etYear)

        // Pre-fill title with file name
        viewModel.fileName.value?.substringBeforeLast(".")?.let {
            etTitle.setText(it)
        }

        // Buttons
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnExport).setOnClickListener {
            val selectedFormat = formats[spinnerFormat.selectedItemPosition]
            pendingExportFormat = selectedFormat
            pendingBitrate = bitrateValues[spinnerBitrate.selectedItemPosition]

            // Collect metadata
            pendingMetadata = AudioMetadata(
                title = etTitle.text.toString().takeIf { it.isNotBlank() },
                artist = etArtist.text.toString().takeIf { it.isNotBlank() },
                album = etAlbum.text.toString().takeIf { it.isNotBlank() },
                year = etYear.text.toString().takeIf { it.isNotBlank() }
            )

            dialog.dismiss()
            launchExportFilePicker(selectedFormat)
        }

        dialog.show()
    }

    private fun launchExportFilePicker(format: AudioEditorViewModel.ExportFormat) {
        pendingExportFormat = format
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when (format) {
                AudioEditorViewModel.ExportFormat.MP3 -> "audio/mpeg"
                AudioEditorViewModel.ExportFormat.WAV -> "audio/wav"
                AudioEditorViewModel.ExportFormat.FLAC -> "audio/flac"
                AudioEditorViewModel.ExportFormat.OGG -> "audio/ogg"
                AudioEditorViewModel.ExportFormat.AAC -> "audio/mp4"
            }
            val baseName = viewModel.fileName.value?.substringBeforeLast(".") ?: "export"
            putExtra(Intent.EXTRA_TITLE, "$baseName.${format.extension}")
        }
        exportFileLauncher.launch(intent)
    }

    private fun performExport(uri: Uri, format: AudioEditorViewModel.ExportFormat) {
        val cacheDir = cacheDir
        val tempOutput = java.io.File(cacheDir, "export_temp.${format.extension}")

        viewModel.exportAudio(tempOutput.absolutePath, format, pendingBitrate, pendingMetadata) { success, error ->
            // Bug 2.43: Ensure temp file is always cleaned up in finally block
            try {
                if (success) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            tempOutput.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        Toast.makeText(this, R.string.audio_editor_export_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.audio_editor_save_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.audio_editor_export_failed, error), Toast.LENGTH_LONG).show()
                }
            } finally {
                // Always clean up temp file
                if (tempOutput.exists()) {
                    val deleted = tempOutput.delete()
                    if (!deleted) {
                        Log.w("AudioEditorActivity", "Failed to delete temp export file: ${tempOutput.absolutePath}")
                    }
                }
            }
        }
    }

    // ==================== Editing ====================

    private fun trimSelection() {
        val selection = getActiveSelection() ?: return
        val duration = viewModel.duration.value ?: return

        val startMs = (selection.first * duration).toLong()
        val endMs = (selection.second * duration).toLong()

        AlertDialog.Builder(this)
            .setTitle(R.string.audio_editor_trim_title)
            .setMessage(getString(R.string.audio_editor_trim_message, formatTime(startMs), formatTime(endMs)))
            .setPositiveButton(R.string.audio_editor_trim_action) { _, _ ->
                viewModel.trimAudio(startMs, endMs) { success, error ->
                    if (success) {
                        clearActiveSelection()
                        Toast.makeText(this, R.string.audio_editor_trim_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.audio_editor_trim_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun copySelection() {
        val selection = getActiveSelection() ?: return
        val duration = viewModel.duration.value ?: return

        val startMs = (selection.first * duration).toLong()
        val endMs = (selection.second * duration).toLong()

        viewModel.copySelection(startMs, endMs) { success, error ->
            if (success) {
                Toast.makeText(this, R.string.audio_editor_copy_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.audio_editor_copy_failed, error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cutSelection() {
        val selection = getActiveSelection() ?: return
        val duration = viewModel.duration.value ?: return

        val startMs = (selection.first * duration).toLong()
        val endMs = (selection.second * duration).toLong()

        AlertDialog.Builder(this)
            .setTitle(R.string.audio_editor_cut_title)
            .setMessage(getString(R.string.audio_editor_cut_message, formatTime(startMs), formatTime(endMs)))
            .setPositiveButton(R.string.audio_editor_cut_action) { _, _ ->
                viewModel.cutSelection(startMs, endMs) { success, error ->
                    if (success) {
                        clearActiveSelection()
                        Toast.makeText(this, R.string.audio_editor_cut_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.audio_editor_cut_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun pasteAtPlayhead() {
        val duration = viewModel.duration.value ?: return
        val progress = viewModel.playbackProgress.value ?: 0f
        val positionMs = (progress * duration).toLong()

        AlertDialog.Builder(this)
            .setTitle(R.string.audio_editor_paste_title)
            .setMessage(getString(R.string.audio_editor_paste_message, formatTime(positionMs)))
            .setPositiveButton(R.string.audio_editor_paste_action) { _, _ ->
                viewModel.pasteAtPosition(positionMs) { success, error ->
                    if (success) {
                        Toast.makeText(this, R.string.audio_editor_paste_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.audio_editor_paste_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun deleteSelection() {
        val selection = getActiveSelection() ?: return
        val duration = viewModel.duration.value ?: return

        val startMs = (selection.first * duration).toLong()
        val endMs = (selection.second * duration).toLong()

        AlertDialog.Builder(this)
            .setTitle(R.string.audio_editor_delete_title)
            .setMessage(getString(R.string.audio_editor_delete_message, formatTime(startMs), formatTime(endMs)))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                viewModel.deleteSelection(startMs, endMs) { success, error ->
                    if (success) {
                        clearActiveSelection()
                        Toast.makeText(this, R.string.audio_editor_delete_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.audio_editor_delete_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun performUndo() {
        viewModel.undo { success, error ->
            if (success) {
                clearActiveSelection()
                Toast.makeText(this, R.string.audio_editor_undo_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.audio_editor_undo_failed, error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRedo() {
        viewModel.redo { success, error ->
            if (success) {
                clearActiveSelection()
                Toast.makeText(this, R.string.audio_editor_redo_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.audio_editor_redo_failed, error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        btnUndo.isEnabled = canUndo
        btnUndo.alpha = if (canUndo) 1f else 0.4f

        btnRedo.isEnabled = canRedo
        btnRedo.alpha = if (canRedo) 1f else 0.4f
    }

    /**
     * Navigue vers le Hub si l'activité est la racine de la tâche (lancée depuis widget/raccourci),
     * sinon termine simplement l'activité pour revenir à l'écran précédent.
     */
    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    private fun updateEditButtons(hasSelection: Boolean, hasClipboard: Boolean) {
        btnCopy.isEnabled = hasSelection
        btnCopy.alpha = if (hasSelection) 1f else 0.4f

        btnCut.isEnabled = hasSelection
        btnCut.alpha = if (hasSelection) 1f else 0.4f

        btnDelete.isEnabled = hasSelection
        btnDelete.alpha = if (hasSelection) 1f else 0.4f

        btnPaste.isEnabled = hasClipboard
        btnPaste.alpha = if (hasClipboard) 1f else 0.4f

        btnTrim.isEnabled = hasSelection
        btnTrim.alpha = if (hasSelection) 1f else 0.4f
    }

    // ==================== Selection Helpers ====================

    private fun getActiveSelection(): Pair<Float, Float>? {
        return if (currentVisualMode == VisualMode.SPECTROGRAM) {
            activeSpectrumView?.getSelection()
        } else {
            activeWaveformView?.getSelection()
        }
    }

    private fun clearActiveSelection() {
        if (currentVisualMode == VisualMode.SPECTROGRAM) {
            activeSpectrumView?.clearSelection()
        } else {
            activeWaveformView?.clearSelection()
        }
    }

    // ==================== Utilities ====================

    private fun getFileName(uri: Uri): String {
        var name = "Audio"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // ==================== Multi-Track Management ====================

    // For drag & drop - Bug 2.30: Use AtomicInteger to prevent race conditions
    private var draggedView: View? = null
    private val draggedIndexAtomic = AtomicInteger(-1)

    private fun refreshTrackViews(tracks: List<AudioTrack>) {
        // Bug 2.29: Synchronized access to trackViews
        synchronized(trackViewsLock) {
            // Clear existing views
            tracksContainer.removeAllViews()
            trackViews.clear()

            // Show/hide empty state
            if (tracks.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                tracksScrollView.visibility = View.GONE
                activeWaveformView = null
                btnExport.isEnabled = false
                return
            }

            tvEmptyState.visibility = View.GONE
            tracksScrollView.visibility = View.VISIBLE

            // Create view for each track
            tracks.forEachIndexed { index, track ->
                val trackView = layoutInflater.inflate(R.layout.item_audio_track, tracksContainer, false)

                // Track number
                val tvTrackNumber = trackView.findViewById<TextView>(R.id.tvTrackNumber)
                tvTrackNumber.text = getString(R.string.audio_editor_track_number, index + 1)

                // Track name
                val tvTrackName = trackView.findViewById<TextView>(R.id.tvTrackName)
                tvTrackName.text = track.name

                // Track duration
                val tvTrackDuration = trackView.findViewById<TextView>(R.id.tvTrackDuration)
                tvTrackDuration.text = formatTime(track.durationMs)

                // Waveform
                val waveformView = trackView.findViewById<WaveformView>(R.id.trackWaveformView)
                track.waveformData?.let {
                    waveformView.setWaveformData(it)
                }

                // Spectrogram
                val spectrumView = trackView.findViewById<SpectrumView>(R.id.trackSpectrumView)

                // Set up waveform and spectrum listeners
                setupWaveformListeners(waveformView)
                setupSpectrumListeners(spectrumView)

                // Click on track container to make active
                val trackContainer = trackView.findViewById<View>(R.id.trackContainer)
                trackContainer.setOnClickListener {
                    viewModel.setActiveTrack(index)
                }

                // Remove button
                val btnRemove = trackView.findViewById<ImageButton>(R.id.btnRemoveTrack)
                btnRemove.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.audio_editor_delete_track_title)
                        .setMessage(getString(R.string.audio_editor_delete_track_message, track.name))
                        .setPositiveButton(R.string.common_delete) { _, _ ->
                            viewModel.removeTrack(index)
                        }
                        .setNegativeButton(R.string.common_cancel, null)
                        .show()
                }

                // Drag handle for reordering
                val dragHandle = trackView.findViewById<View>(R.id.dragHandle)
                setupDragHandle(dragHandle, trackView, index)

                tracksContainer.addView(trackView)
                trackViews[index] = trackView
            }
        }

        // Update highlight for active track
        val activeIndex = viewModel.activeTrackIndex.value ?: -1
        updateActiveTrackHighlight(activeIndex)
        updateToggleButtonIcon()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandle(dragHandle: View, trackView: View, index: Int) {
        dragHandle.setOnLongClickListener {
            draggedView = trackView
            // Bug 2.30: Use AtomicInteger for thread-safe access
            draggedIndexAtomic.set(index)
            trackView.alpha = 0.7f
            true
        }

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (draggedView != null) {
                        // Find target position based on Y coordinate
                        val y = event.rawY
                        var targetIndex = -1
                        // Bug 2.30: Read atomic value once for consistency
                        val currentDraggedIndex = draggedIndexAtomic.get()

                        for (i in 0 until tracksContainer.childCount) {
                            val child = tracksContainer.getChildAt(i)
                            val location = IntArray(2)
                            child.getLocationOnScreen(location)
                            val childCenterY = location[1] + child.height / 2

                            if (y < childCenterY) {
                                targetIndex = i
                                break
                            }
                        }

                        if (targetIndex == -1) {
                            targetIndex = tracksContainer.childCount - 1
                        }

                        // Visual feedback - highlight drop position
                        for (i in 0 until tracksContainer.childCount) {
                            val child = tracksContainer.getChildAt(i)
                            if (child != draggedView) {
                                child.alpha = if (i == targetIndex && targetIndex != currentDraggedIndex) 0.5f else 1f
                            }
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (draggedView != null) {
                        // Find final target position
                        val y = event.rawY
                        var targetIndex = tracksContainer.childCount - 1
                        // Bug 2.30: Read atomic value once for consistency
                        val currentDraggedIndex = draggedIndexAtomic.get()

                        for (i in 0 until tracksContainer.childCount) {
                            val child = tracksContainer.getChildAt(i)
                            val location = IntArray(2)
                            child.getLocationOnScreen(location)
                            val childCenterY = location[1] + child.height / 2

                            if (y < childCenterY) {
                                targetIndex = i
                                break
                            }
                        }

                        // Move track if position changed
                        if (targetIndex != currentDraggedIndex && currentDraggedIndex >= 0) {
                            viewModel.moveTrack(currentDraggedIndex, targetIndex)
                        }

                        // Reset visual state
                        for (i in 0 until tracksContainer.childCount) {
                            tracksContainer.getChildAt(i).alpha = 1f
                        }

                        draggedView = null
                        // Bug 2.30: Reset atomic value
                        draggedIndexAtomic.set(-1)
                    }
                }
            }
            false
        }
    }

    private fun updateActiveTrackHighlight(activeIndex: Int) {
        // Bug 2.29: Synchronized access to trackViews
        synchronized(trackViewsLock) {
            // Update visual highlight for all tracks
            trackViews.forEach { (index, view) ->
                val trackContainer = view.findViewById<View>(R.id.trackContainer)
                trackContainer.setBackgroundResource(
                    if (index == activeIndex) R.drawable.track_background_active
                    else R.drawable.track_background_inactive
                )
            }

            // Update active view references
            if (activeIndex >= 0 && trackViews.containsKey(activeIndex)) {
                val activeView = trackViews[activeIndex]
                activeWaveformView = activeView?.findViewById(R.id.trackWaveformView)
                activeSpectrumView = activeView?.findViewById(R.id.trackSpectrumView)

                // Update waveform data from active track
                viewModel.getActiveTrack()?.let { track ->
                    track.waveformData?.let { data ->
                        activeWaveformView?.setWaveformData(data)
                    }
                    if (currentVisualMode == VisualMode.SPECTROGRAM) {
                        activeSpectrumView?.let { view -> loadSpectrogram(track, view) }
                    }
                    // Update file name display
                    tvFileName.text = track.name
                }
            } else {
                activeWaveformView = null
                activeSpectrumView = null
            }
        }

        // Update edit buttons based on new active view
        val hasSelection = getActiveSelection() != null
        val hasClipboard = viewModel.hasClipboard.value ?: false
        updateEditButtons(hasSelection, hasClipboard)
    }

    private fun setupWaveformListeners(waveformView: WaveformView) {
        waveformView.onSeekListener = { progress ->
            viewModel.seekTo(progress)
        }

        waveformView.onSelectionChangedListener = { start, end ->
            // -1f indicates no selection
            val hasSelection = start >= 0f && end >= 0f && start != end
            val hasClipboard = viewModel.hasClipboard.value ?: false
            updateEditButtons(hasSelection, hasClipboard)
        }
    }

    private fun setupSpectrumListeners(spectrumView: SpectrumView) {
        spectrumView.onSeekListener = { progress ->
            viewModel.seekTo(progress)
        }

        spectrumView.onSelectionChangedListener = { start, end ->
            val hasSelection = start >= 0f && end >= 0f && start != end
            val hasClipboard = viewModel.hasClipboard.value ?: false
            updateEditButtons(hasSelection, hasClipboard)
        }
    }

    private fun updateToggleButtonIcon() {
        val showSpectrogram = currentVisualMode == VisualMode.SPECTROGRAM

        // Update button icon: show the opposite mode icon (what you'll switch TO)
        btnToggleVisualMode.setImageResource(
            if (showSpectrogram) R.drawable.ic_waveform else R.drawable.ic_spectrum
        )

        val tracks = viewModel.tracks.value ?: emptyList()
        // Bug 2.29 & 2.42: Synchronized access to trackViews with safe copy to prevent ConcurrentModification
        val trackViewsCopy: List<Pair<Int, View>>
        synchronized(trackViewsLock) {
            trackViewsCopy = trackViews.entries.map { it.key to it.value }
        }
        trackViewsCopy.forEach { (index, trackView) ->
            val waveformView = trackView.findViewById<WaveformView>(R.id.trackWaveformView)
            val spectrumView = trackView.findViewById<SpectrumView>(R.id.trackSpectrumView)

            // Synchronize selection between views before toggling visibility
            if (showSpectrogram) {
                // Switching TO spectrogram: copy waveform selection to spectrum
                val sel = waveformView.getSelection()
                if (sel != null) {
                    spectrumView.setSelection(sel.first, sel.second)
                } else {
                    spectrumView.clearSelection()
                }
            } else {
                // Switching TO waveform: copy spectrum selection to waveform
                val sel = spectrumView.getSelection()
                if (sel != null) {
                    waveformView.setSelection(sel.first, sel.second)
                } else {
                    waveformView.clearSelection()
                }
            }

            waveformView.visibility = if (showSpectrogram) View.GONE else View.VISIBLE
            spectrumView.visibility = if (showSpectrogram) View.VISIBLE else View.GONE

            if (showSpectrogram && index < tracks.size) {
                loadSpectrogram(tracks[index], spectrumView)
            }
        }
    }

    private fun loadSpectrogram(track: AudioTrack, spectrumView: SpectrumView) {
        val cachedData = track.cachedSpectrogramData
        val cachedSampleRate = track.cachedSpectrogramSampleRate
        val cachedFftSize = track.cachedSpectrogramFftSize
        if (cachedData != null && cachedSampleRate != null && cachedFftSize != null) {
            spectrumView.setSpectrogramData(cachedData, cachedSampleRate, cachedFftSize)
            return
        }

        // Get track index to track the job
        val trackIndex = viewModel.tracks.value?.indexOf(track) ?: -1

        // Cancel any existing job for this track
        if (trackIndex >= 0) {
            spectrogramLoadingJobs[trackIndex]?.cancel()
        }

        spectrumView.clear()
        val job = lifecycleScope.launch {
            try {
                val rawAudio = withContext(Dispatchers.IO) {
                    waveformExtractor.extractRawSamples(track.uri)
                }
                val fftSize = FFTProcessor.FFT_SIZE_1024
                val spectrogram = withContext(Dispatchers.Default) {
                    fftProcessor.computeSpectrogram(rawAudio.samples, fftSize)
                }
                track.cachedSpectrogramData = spectrogram
                track.cachedSpectrogramSampleRate = rawAudio.sampleRate
                track.cachedSpectrogramFftSize = fftSize
                spectrumView.setSpectrogramData(spectrogram, rawAudio.sampleRate, fftSize)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Job was cancelled, do nothing
            } catch (e: Exception) {
                spectrumView.clear()
                Toast.makeText(this@AudioEditorActivity, e.message ?: "Spectrogram error", Toast.LENGTH_SHORT).show()
            } finally {
                // Remove job from tracking map when done
                if (trackIndex >= 0) {
                    spectrogramLoadingJobs.remove(trackIndex)
                }
            }
        }

        // Store the job for potential cancellation
        if (trackIndex >= 0) {
            spectrogramLoadingJobs[trackIndex] = job
        }
    }

    private enum class VisualMode {
        WAVEFORM,
        SPECTROGRAM
    }
}
