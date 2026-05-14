package com.Atom2Universe.app.sf2creator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.audio.PitchDetector
import com.Atom2Universe.app.sf2creator.audio.SamplePlayer
import com.Atom2Universe.app.sf2creator.audio.SampleRecorder
import com.Atom2Universe.app.sf2creator.data.PitchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Fragment for recording audio samples for SF2 creation.
 *
 * Uses a 3-phase recording approach with TWO buttons:
 * 1. IDLE: User sees "Get Ready" button enabled, "Record" button disabled/grayed
 * 2. PREPARING: User clicked "Get Ready", mic is active, "Record" button now enabled
 * 3. RECORDING: User clicked "Record", timer starts, waiting for "Stop"
 * 4. COMPLETED: User clicked "Stop" (or max duration reached), sample extracted
 *
 * This approach eliminates microphone startup transients by having the mic
 * already running when the user starts the actual recording.
 */
class RecordSampleFragment : Fragment() {

    private lateinit var recorder: SampleRecorder
    private lateinit var pitchDetector: PitchDetector
    private lateinit var samplePlayer: SamplePlayer

    // UI elements
    private lateinit var getReadyButton: ImageButton
    private lateinit var getReadyLabel: TextView
    private lateinit var recordButton: ImageButton
    private lateinit var recordLabel: TextView
    private lateinit var playButton: ImageButton
    private lateinit var amplitudeBar: ProgressBar
    private lateinit var durationText: TextView
    private lateinit var statusText: TextView
    private lateinit var pitchText: TextView
    private lateinit var continueButton: Button
    private lateinit var retryButton: Button
    private lateinit var myProjectsButton: Button
    private lateinit var settingsContainer: LinearLayout
    private lateinit var countdownSlider: Slider
    private lateinit var countdownValueText: TextView
    private lateinit var maxDurationSlider: Slider
    private lateinit var maxDurationValueText: TextView

    // State
    private var recordedFile: File? = null
    private var recordedSamples: ShortArray? = null
    private var detectedPitch: PitchResult = PitchResult.UNKNOWN
    private var countdownDelaySeconds: Int = 3
    private var maxDurationSeconds: Int = 5
    private var countdownJob: Job? = null

    // Listeners
    var onSampleRecorded: ((File, ShortArray, PitchResult) -> Unit)? = null
    var onProjectsRequested: (() -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startPreparing()
        } else {
            statusText.text = getString(R.string.sf2_permission_denied)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_record_sample, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize audio components
        recorder = SampleRecorder(requireContext())
        pitchDetector = PitchDetector()
        samplePlayer = SamplePlayer()

        // Find views
        getReadyButton = view.findViewById(R.id.get_ready_button)
        getReadyLabel = view.findViewById(R.id.get_ready_label)
        recordButton = view.findViewById(R.id.record_button)
        recordLabel = view.findViewById(R.id.record_label)
        playButton = view.findViewById(R.id.play_button)
        amplitudeBar = view.findViewById(R.id.amplitude_bar)
        durationText = view.findViewById(R.id.duration_text)
        statusText = view.findViewById(R.id.status_text)
        pitchText = view.findViewById(R.id.pitch_text)
        continueButton = view.findViewById(R.id.continue_button)
        retryButton = view.findViewById(R.id.retry_button)
        myProjectsButton = view.findViewById(R.id.my_projects_button)
        settingsContainer = view.findViewById(R.id.settings_container)
        countdownSlider = view.findViewById(R.id.countdown_slider)
        countdownValueText = view.findViewById(R.id.countdown_value)
        maxDurationSlider = view.findViewById(R.id.max_duration_slider)
        maxDurationValueText = view.findViewById(R.id.max_duration_value)

        setupSliders()
        setupListeners()
        observeRecordingState()
        updateUI(State.IDLE)
    }

    private fun setupSliders() {
        // Countdown slider (0-10 seconds)
        countdownSlider.addOnChangeListener { _, value, _ ->
            countdownDelaySeconds = value.toInt()
            countdownValueText.text = if (countdownDelaySeconds == 0) {
                getString(R.string.sf2_countdown_none)
            } else {
                getString(R.string.sf2_seconds_format, countdownDelaySeconds)
            }
            updateStatusForCountdownMode()
        }

        // Max duration slider (1-10 seconds)
        maxDurationSlider.addOnChangeListener { _, value, _ ->
            maxDurationSeconds = value.toInt()
            maxDurationValueText.text = getString(R.string.sf2_seconds_format, maxDurationSeconds)
            // Update the recorder's max duration
            recorder.maxDurationMs = maxDurationSeconds * 1000L
        }

        // Initialize values
        countdownValueText.text = getString(R.string.sf2_seconds_format, countdownDelaySeconds)
        maxDurationValueText.text = getString(R.string.sf2_seconds_format, maxDurationSeconds)
        recorder.maxDurationMs = maxDurationSeconds * 1000L
    }

    private fun updateStatusForCountdownMode() {
        if (recorder.recordingState.value is SampleRecorder.RecordingState.Idle) {
            statusText.text = if (countdownDelaySeconds == 0) {
                getString(R.string.sf2_tap_get_ready)
            } else {
                getString(R.string.sf2_tap_to_start)
            }
        }
    }

    private fun setupListeners() {
        // Get Ready button - starts the mic (and countdown if configured)
        getReadyButton.setOnClickListener {
            when (recorder.recordingState.value) {
                is SampleRecorder.RecordingState.Idle -> {
                    checkPermissionAndPrepare()
                }
                is SampleRecorder.RecordingState.Preparing -> {
                    // During countdown, cancel and reset
                    if (countdownJob?.isActive == true) {
                        cancelCountdown()
                        recorder.cancel()
                        recorder.reset()
                        updateUI(State.IDLE)
                    }
                }
                else -> {
                    // In other states, this button should be hidden or disabled
                }
            }
        }

        // Record button - starts/stops the actual recording
        recordButton.setOnClickListener {
            when (recorder.recordingState.value) {
                is SampleRecorder.RecordingState.Preparing -> {
                    // Start recording (mark start position in buffer)
                    recorder.startRecording()
                }
                is SampleRecorder.RecordingState.Recording -> {
                    // Stop recording (extract sample)
                    recorder.stopRecording()
                }
                else -> {
                    // Button should be disabled in other states
                }
            }
        }

        playButton.setOnClickListener {
            recordedSamples?.let { samples ->
                samplePlayer.loadSample(samples)
                samplePlayer.setRootNote(detectedPitch.midiNote)
                samplePlayer.play()
            }
        }

        continueButton.setOnClickListener {
            recordedFile?.let { file ->
                recordedSamples?.let { samples ->
                    onSampleRecorded?.invoke(file, samples, detectedPitch)
                }
            }
        }

        retryButton.setOnClickListener {
            reset()
        }

        myProjectsButton.setOnClickListener {
            onProjectsRequested?.invoke()
        }
    }

    private fun observeRecordingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            recorder.recordingState.collectLatest { state ->
                when (state) {
                    is SampleRecorder.RecordingState.Idle -> {
                        updateUI(State.IDLE)
                    }
                    is SampleRecorder.RecordingState.Preparing -> {
                        // In countdown mode, don't override with PREPARING state
                        // (the countdown coroutine handles the UI)
                        if (countdownJob?.isActive != true) {
                            updateUI(State.PREPARING)
                        }
                    }
                    is SampleRecorder.RecordingState.Recording -> {
                        updateUI(State.RECORDING)
                    }
                    is SampleRecorder.RecordingState.MaxDurationReached -> {
                        statusText.text = getString(R.string.sf2_max_duration_reached)
                    }
                    is SampleRecorder.RecordingState.Completed -> {
                        recordedFile = state.file
                        recordedSamples = state.samples
                        detectPitch(state.samples)
                        updateUI(State.COMPLETED)
                        if (state.noiseReductionApplied) {
                            val noiseDb = "%.0f".format(state.noiseLevelDb)
                            statusText.text = "${statusText.text}\n" +
                                getString(R.string.sf2_noise_reduced, noiseDb)
                        }
                    }
                    is SampleRecorder.RecordingState.Error -> {
                        statusText.text = state.message
                        updateUI(State.ERROR)
                    }
                }
            }
        }

        // Observe amplitude for visualization (during both preparing and recording)
        viewLifecycleOwner.lifecycleScope.launch {
            recorder.amplitude.collectLatest { amp ->
                amplitudeBar.progress = (amp * 100).toInt()
            }
        }

        // Observe recording duration - ONLY update during actual recording
        viewLifecycleOwner.lifecycleScope.launch {
            recorder.recordingDuration.collectLatest { durationMs ->
                // Only show recording duration when actually recording
                if (recorder.recordingState.value is SampleRecorder.RecordingState.Recording) {
                    val seconds = durationMs / 1000f
                    durationText.text = String.format(
                        Locale.getDefault(),
                        getString(R.string.sf2_recording_duration_format),
                        seconds,
                        maxDurationSeconds
                    )
                }
            }
        }
    }

    private fun checkPermissionAndPrepare() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startPreparing()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                statusText.text = getString(R.string.sf2_permission_rationale)
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startPreparing() {
        recorder.startPreparing()

        // If countdown is configured, start it automatically
        if (countdownDelaySeconds > 0) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            updateUI(State.COUNTDOWN)

            // Count down from configured seconds
            for (remaining in countdownDelaySeconds downTo 1) {
                statusText.text = getString(R.string.sf2_countdown_waiting, remaining)
                durationText.text = "$remaining"
                delay(1000)
            }

            // Show GO! briefly then start recording
            statusText.text = getString(R.string.sf2_countdown_go)
            durationText.text = getString(R.string.sf2_countdown_go)
            delay(300)

            // Start actual recording (mark position in buffer)
            recorder.startRecording()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun detectPitch(samples: ShortArray) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Run pitch detection on background thread
            val result = withContext(Dispatchers.Default) {
                pitchDetector.detectPitch(samples, SampleRecorder.SAMPLE_RATE)
            }

            detectedPitch = result

            if (detectedPitch.confidence > 0.3f) {
                pitchText.text = getString(
                    R.string.sf2_detected_pitch,
                    detectedPitch.noteName,
                    detectedPitch.frequency
                )
            } else {
                pitchText.text = getString(R.string.sf2_pitch_uncertain)
                // Default to C4 if uncertain
                detectedPitch = PitchResult.fromFrequency(261.63f, 0.5f)
            }
        }
    }

    private fun reset() {
        cancelCountdown()
        recorder.reset()
        recordedFile = null
        recordedSamples = null
        detectedPitch = PitchResult.UNKNOWN
        samplePlayer.stop()
        updateUI(State.IDLE)
    }

    private fun updateUI(state: State) {
        when (state) {
            State.IDLE -> {
                // Get Ready enabled, Record disabled/grayed
                getReadyButton.isEnabled = true
                getReadyButton.alpha = 1f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_primary))

                // In countdown mode, hide Record button; in manual mode, show but disabled
                if (countdownDelaySeconds == 0) {
                    recordButton.visibility = View.VISIBLE
                    recordButton.isEnabled = false
                    recordButton.alpha = 0.4f
                    recordLabel.visibility = View.VISIBLE
                    recordLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))
                    recordLabel.text = getString(R.string.sf2_record)
                } else {
                    recordButton.visibility = View.GONE
                    recordLabel.visibility = View.GONE
                }

                settingsContainer.visibility = View.VISIBLE
                playButton.visibility = View.GONE
                continueButton.visibility = View.GONE
                retryButton.visibility = View.GONE
                pitchText.visibility = View.GONE
                statusText.text = if (countdownDelaySeconds == 0) {
                    getString(R.string.sf2_tap_get_ready)
                } else {
                    getString(R.string.sf2_tap_to_start)
                }
                durationText.text = String.format(
                    Locale.getDefault(),
                    getString(R.string.sf2_recording_duration_format),
                    0f,
                    maxDurationSeconds
                )
                amplitudeBar.progress = 0
            }
            State.PREPARING -> {
                // Manual mode: Get Ready now shows as "active/listening", Record now enabled
                getReadyButton.isEnabled = false
                getReadyButton.alpha = 0.6f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))

                recordButton.visibility = View.VISIBLE
                recordButton.isEnabled = true
                recordButton.alpha = 1f
                recordLabel.visibility = View.VISIBLE
                recordLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_primary))
                recordLabel.text = getString(R.string.sf2_record)

                settingsContainer.visibility = View.GONE
                playButton.visibility = View.GONE
                continueButton.visibility = View.GONE
                retryButton.visibility = View.GONE
                pitchText.visibility = View.GONE
                statusText.text = getString(R.string.sf2_mic_ready)
                // Keep duration at 0 during preparing - timer only starts on Record
                durationText.text = String.format(
                    Locale.getDefault(),
                    getString(R.string.sf2_recording_duration_format),
                    0f,
                    maxDurationSeconds
                )
            }
            State.COUNTDOWN -> {
                // Countdown mode: Get Ready enabled to allow cancel, Record hidden
                getReadyButton.isEnabled = true
                getReadyButton.alpha = 1f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_primary))

                recordButton.visibility = View.GONE
                recordLabel.visibility = View.GONE

                settingsContainer.visibility = View.GONE
                playButton.visibility = View.GONE
                continueButton.visibility = View.GONE
                retryButton.visibility = View.GONE
                pitchText.visibility = View.GONE
                // Status and duration are updated by the countdown coroutine
            }
            State.RECORDING -> {
                // Get Ready disabled, Record shows "Stop"
                getReadyButton.isEnabled = false
                getReadyButton.alpha = 0.4f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))

                recordButton.visibility = View.VISIBLE
                recordButton.isEnabled = true
                recordButton.alpha = 1f
                recordLabel.visibility = View.VISIBLE
                recordLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_primary))
                recordLabel.text = getString(R.string.sf2_stop)

                settingsContainer.visibility = View.GONE
                playButton.visibility = View.GONE
                continueButton.visibility = View.GONE
                retryButton.visibility = View.GONE
                pitchText.visibility = View.GONE
                statusText.text = getString(R.string.sf2_recording)
            }
            State.COMPLETED -> {
                // Both main buttons disabled, show action buttons
                getReadyButton.isEnabled = false
                getReadyButton.alpha = 0.4f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))

                recordButton.visibility = View.VISIBLE
                recordButton.isEnabled = false
                recordButton.alpha = 0.4f
                recordLabel.visibility = View.VISIBLE
                recordLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))
                recordLabel.text = getString(R.string.sf2_record)

                settingsContainer.visibility = View.GONE
                playButton.visibility = View.VISIBLE
                continueButton.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
                pitchText.visibility = View.VISIBLE
                statusText.text = getString(R.string.sf2_recording_complete)
            }
            State.ERROR -> {
                // Reset to idle-like state but show retry
                getReadyButton.isEnabled = true
                getReadyButton.alpha = 1f
                getReadyLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_primary))

                recordButton.visibility = View.VISIBLE
                recordButton.isEnabled = false
                recordButton.alpha = 0.4f
                recordLabel.visibility = View.VISIBLE
                recordLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.startup_text_secondary))
                recordLabel.text = getString(R.string.sf2_record)

                settingsContainer.visibility = View.VISIBLE
                playButton.visibility = View.GONE
                continueButton.visibility = View.GONE
                retryButton.visibility = View.VISIBLE
                pitchText.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelCountdown()
        recorder.cancel()
        samplePlayer.release()
    }

    private enum class State {
        IDLE, PREPARING, COUNTDOWN, RECORDING, COMPLETED, ERROR
    }

    companion object {
        fun newInstance(): RecordSampleFragment {
            return RecordSampleFragment()
        }
    }
}
