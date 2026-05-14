package com.Atom2Universe.app.sf2creator.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.SpectrumView
import com.Atom2Universe.app.sf2creator.audio.AudioProcessor
import com.Atom2Universe.app.sf2creator.audio.DeClicker
import com.Atom2Universe.app.sf2creator.audio.EnvelopeGenerator
import com.Atom2Universe.app.sf2creator.audio.Normalizer
import com.Atom2Universe.app.sf2creator.audio.SamplePlayer
import com.Atom2Universe.app.sf2creator.audio.SimpleSamplePlayer
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for editing and processing recorded samples.
 *
 * Features:
 * - Waveform visualization with trim and loop markers
 * - Edit mode toggle (Trim / Loop)
 * - Trim controls: adjust sample start/end, apply trim
 * - Loop point adjustment
 * - Processing options
 * - ADSR preset selection
 * - Preview playback
 */
class SampleEditorFragment : Fragment() {

    private lateinit var waveformView: SampleWaveformView
    private lateinit var spectrumView: SpectrumView
    private lateinit var samplePlayer: SamplePlayer
    private lateinit var simplePlayer: SimpleSamplePlayer  // Clean player for sustain/loop tests
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var normalizer: Normalizer
    private lateinit var deClicker: DeClicker
    private lateinit var envelopeGenerator: EnvelopeGenerator

    // UI elements
    private lateinit var infoText: TextView
    private lateinit var trimInfoText: TextView
    private lateinit var loopInfoText: TextView
    private lateinit var loopHintText: TextView
    private lateinit var hintText: TextView
    private lateinit var playButton: View
    private lateinit var continueButton: Button
    private lateinit var editModeToggle: MaterialButtonToggleGroup
    private lateinit var trimModeButton: MaterialButton
    private lateinit var loopModeButton: MaterialButton
    private lateinit var trimControls: LinearLayout
    private lateinit var loopControls: LinearLayout
    private lateinit var previewTrimButton: Button
    private lateinit var applyTrimButton: Button
    private lateinit var autoLoopButton: Button
    private lateinit var sustainTestButton: Button
    private lateinit var noLoopButton: Button

    // Sustain test state
    private var isSustainPlaying = false

    private lateinit var processButton: Button
    private lateinit var adsrRadioGroup: RadioGroup

    // Sample data
    private var rawSamples: ShortArray? = null
    private var processedSamples: ShortArray? = null
    private var processingResult: AudioProcessor.ProcessedResult? = null
    private var rootNote: Int = 60

    // Processing options
    private var selectedAdsrPreset: EnvelopeGenerator.ADSRSettings = EnvelopeGenerator.Presets.NONE

    // Pending setup state
    private var pendingSetup = false

    // Restored state from previous edit session
    private var pendingLoopStart: Int? = null
    private var pendingLoopEnd: Int? = null
    private var pendingHasLoop: Boolean? = null
    private var pendingAdsrPreset: EnvelopeGenerator.ADSRSettings? = null

    // Listener for when editing is complete
    var onEditCompleteListener: ((samples: ShortArray, loopStart: Int, loopEnd: Int, hasLoop: Boolean, adsrPreset: EnvelopeGenerator.ADSRSettings) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sample_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize processors
        samplePlayer = SamplePlayer()
        simplePlayer = SimpleSamplePlayer()  // Clean player for loop/sustain tests
        audioProcessor = AudioProcessor()
        normalizer = Normalizer()
        deClicker = DeClicker()
        envelopeGenerator = EnvelopeGenerator(44100)

        // Find views
        waveformView = view.findViewById(R.id.waveformView)
        spectrumView = view.findViewById(R.id.spectrumView)
        spectrumView.setInteractive(false)  // Spectrum is read-only visual reference
        infoText = view.findViewById(R.id.infoText)
        trimInfoText = view.findViewById(R.id.trimInfoText)
        loopInfoText = view.findViewById(R.id.loopInfoText)
        loopHintText = view.findViewById(R.id.loopHintText)
        hintText = view.findViewById(R.id.hint_text)
        playButton = view.findViewById(R.id.playButton)
        continueButton = view.findViewById(R.id.continueButton)
        editModeToggle = view.findViewById(R.id.editModeToggle)
        trimModeButton = view.findViewById(R.id.trimModeButton)
        loopModeButton = view.findViewById(R.id.loopModeButton)
        trimControls = view.findViewById(R.id.trim_controls)
        loopControls = view.findViewById(R.id.loop_controls)
        previewTrimButton = view.findViewById(R.id.previewTrimButton)
        applyTrimButton = view.findViewById(R.id.applyTrimButton)
        autoLoopButton = view.findViewById(R.id.autoLoopButton)
        sustainTestButton = view.findViewById(R.id.sustainTestButton)
        noLoopButton = view.findViewById(R.id.noLoopButton)
        processButton = view.findViewById(R.id.processButton)
        adsrRadioGroup = view.findViewById(R.id.adsrRadioGroup)

        setupListeners()
        setupAdsrPresets()
        setupEditModeToggle()

        // Start in trim mode
        editModeToggle.check(R.id.trimModeButton)

        // Apply pending setup if data was set before view created
        applyPendingSetup()
    }

    private fun applyPendingSetup() {
        if (!pendingSetup) return
        pendingSetup = false

        rawSamples?.let { samples ->
            viewLifecycleOwner.lifecycleScope.launch {
                processAndDisplay(samples)
            }
        }
    }

    private fun setupEditModeToggle() {
        editModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.trimModeButton -> setEditMode(SampleWaveformView.EditMode.TRIM)
                    R.id.loopModeButton -> setEditMode(SampleWaveformView.EditMode.LOOP)
                }
            }
        }
    }

    private fun setEditMode(mode: SampleWaveformView.EditMode) {
        waveformView.setEditMode(mode)

        when (mode) {
            SampleWaveformView.EditMode.TRIM -> {
                trimControls.visibility = View.VISIBLE
                loopControls.visibility = View.GONE
                trimInfoText.visibility = View.VISIBLE
                loopInfoText.visibility = View.GONE
                loopHintText.visibility = View.GONE
                hintText.text = getString(R.string.sf2_trim_markers_hint)
                updateTrimInfo()
            }
            SampleWaveformView.EditMode.LOOP -> {
                trimControls.visibility = View.GONE
                loopControls.visibility = View.VISIBLE
                trimInfoText.visibility = View.GONE
                loopInfoText.visibility = View.VISIBLE
                hintText.text = getString(R.string.sf2_drag_markers_hint)

                // Just update the display with current loop state
                // DON'T auto-enable loop - respect the original hasLoop state
                val currentLoopStart = waveformView.getLoopStartSample()
                val currentLoopEnd = waveformView.getLoopEndSample()
                updateLoopInfo(currentLoopStart, currentLoopEnd)
            }
        }
    }

    private fun setupListeners() {
        playButton.setOnClickListener {
            playPreview()
        }

        continueButton.setOnClickListener {
            onContinue()
        }

        applyTrimButton.setOnClickListener {
            applyTrim()
        }

        previewTrimButton.setOnClickListener {
            previewTrim()
        }

        autoLoopButton.setOnClickListener {
            addLoop()
        }

        // Sustain test: hold to play continuously with loop
        sustainTestButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startSustainTest()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopSustainTest()
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopSustainTest()
                    true
                }
                else -> false
            }
        }

        noLoopButton.setOnClickListener {
            removeLoop()
        }

        processButton.setOnClickListener {
            applyProcessing()
        }

        waveformView.onLoopChangedListener = { loopStart, loopEnd ->
            updateLoopInfo(loopStart, loopEnd)
        }

        waveformView.onTrimChangedListener = { _, _ ->
            updateTrimInfo()
        }
    }

    private fun onContinue() {
        // Get the trimmed samples if any trimming was applied
        val samples = waveformView.getTrimmedSamples() ?: processedSamples ?: return

        // Get loop points relative to the trimmed samples
        val trimStart = waveformView.getTrimStartSample()
        val loopStart = (waveformView.getLoopStartSample() - trimStart).coerceAtLeast(0)
        val loopEnd = (waveformView.getLoopEndSample() - trimStart).coerceAtMost(samples.size)
        val hasLoop = loopEnd > loopStart && (loopEnd - loopStart) > 100

        onEditCompleteListener?.invoke(samples, loopStart, loopEnd, hasLoop, selectedAdsrPreset)
    }

    private fun setupAdsrPresets() {
        // Add radio buttons for each ADSR preset
        val presets = listOf(
            "None" to EnvelopeGenerator.Presets.NONE,
            "Piano" to EnvelopeGenerator.Presets.PIANO,
            "Organ" to EnvelopeGenerator.Presets.ORGAN,
            "Pad" to EnvelopeGenerator.Presets.PAD,
            "Pluck" to EnvelopeGenerator.Presets.PLUCK,
            "Strings" to EnvelopeGenerator.Presets.STRINGS
        )

        // Determine which preset should be selected (restored or default)
        val presetToSelect = pendingAdsrPreset ?: selectedAdsrPreset

        for ((name, preset) in presets) {
            val radioButton = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = name
                tag = preset
                setTextColor(resources.getColor(R.color.startup_text_primary, null))
                setPadding(16, 8, 24, 8)
                // Check if this preset matches the one to restore
                isChecked = (preset == presetToSelect)
            }
            adsrRadioGroup.addView(radioButton)
        }

        // If a preset was restored, update selectedAdsrPreset
        pendingAdsrPreset?.let { selectedAdsrPreset = it }

        attachAdsrListener()
    }

    /**
     * Attach the ADSR RadioGroup listener.
     */
    private fun attachAdsrListener() {
        adsrRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            val radioButton = group.findViewById<RadioButton>(checkedId)
            selectedAdsrPreset = radioButton?.tag as? EnvelopeGenerator.ADSRSettings
                ?: EnvelopeGenerator.Presets.NONE

            // Auto-disable loop for presets that don't need it (None, Piano, Pluck, Pad, etc.)
            if (!selectedAdsrPreset.requiresLoop) {
                removeLoop()
            }
        }
    }

    /**
     * Set the sample data to edit.
     * Can be called before onViewCreated - data will be applied once the view is ready.
     */
    fun setSampleData(samples: ShortArray, rootNote: Int) {
        this.rawSamples = samples
        this.rootNote = rootNote
        this.pendingSetup = true

        // Only process if view is already created
        if (view != null && ::waveformView.isInitialized) {
            applyPendingSetup()
        }
    }

    /**
     * Restore previous edit state (loop points and ADSR preset).
     * Call this after setSampleData to restore state from a previous session.
     */
    fun restoreEditState(loopStart: Int, loopEnd: Int, hasLoop: Boolean, adsrPreset: EnvelopeGenerator.ADSRSettings?) {
        // Update the selected preset
        adsrPreset?.let {
            selectedAdsrPreset = it
            // If RadioGroup already exists, update its selection
            if (::adsrRadioGroup.isInitialized) {
                updateRadioGroupSelection(it)
            }
        }

        // If samples are already loaded, apply loop state directly
        if (processedSamples != null && ::waveformView.isInitialized) {
            if (hasLoop) {
                waveformView.setLoopPoints(loopStart, loopEnd, true)
                updateLoopInfo(loopStart, loopEnd)
            } else {
                waveformView.setLoopPoints(0, 0, false)
                updateLoopInfo(0, 0)
            }
        } else {
            // Store for later use in processAndDisplay
            pendingLoopStart = loopStart
            pendingLoopEnd = loopEnd
            pendingHasLoop = hasLoop
            pendingAdsrPreset = adsrPreset
        }
    }

    /**
     * Update the RadioGroup selection to match the given preset.
     * Note: Does NOT call removeLoop() - that's handled by processAndDisplay via pending values.
     */
    private fun updateRadioGroupSelection(preset: EnvelopeGenerator.ADSRSettings) {
        // Temporarily remove listener to avoid triggering it during programmatic change
        adsrRadioGroup.setOnCheckedChangeListener(null)

        for (i in 0 until adsrRadioGroup.childCount) {
            val radioButton = adsrRadioGroup.getChildAt(i) as? RadioButton
            val tagPreset = radioButton?.tag as? EnvelopeGenerator.ADSRSettings
            // Compare by values (data class equality)
            if (tagPreset == preset) {
                radioButton.isChecked = true
                break
            }
        }

        // Re-attach listener
        attachAdsrListener()
    }

    private suspend fun processAndDisplay(samples: ShortArray) {
        // Apply basic processing to avoid saturation and clicks
        // This makes the preview sound cleaner without requiring manual "Process" click
        val cleanedSamples = withContext(Dispatchers.Default) {
            var processed = samples.copyOf()

            // 1. Remove DC offset (prevents clicks at start/end)
            processed = deClicker.removeDCOffset(processed)

            // 2. Apply soft clipping to prevent harsh digital distortion
            processed = deClicker.applySoftClipping(processed, threshold = 0.85f)

            // 3. Normalize to -3dB to leave headroom and prevent saturation
            processed = normalizer.normalize(processed, Normalizer.Mode.PEAK, targetDb = -3f)

            processed
        }

        processedSamples = cleanedSamples

        // Update waveform with cleaned samples
        waveformView.setSamples(cleanedSamples)
        waveformView.setTrimPoints(0, cleanedSamples.size)

        // Restore loop points if we have pending state, otherwise no loop
        val restoreLoop = pendingHasLoop == true && pendingLoopStart != null && pendingLoopEnd != null
        if (restoreLoop) {
            val loopStart = pendingLoopStart!!.coerceIn(0, cleanedSamples.size - 1)
            val loopEnd = pendingLoopEnd!!.coerceIn(0, cleanedSamples.size - 1)
            waveformView.setLoopPoints(loopStart, loopEnd, true)
        } else {
            waveformView.setLoopPoints(0, 0, false)  // No loop by default
        }

        // Update spectrum view
        updateSpectrum(cleanedSamples)

        // Show basic info about the cleaned sample
        val durationMs = cleanedSamples.size * 1000 / 44100
        val peakDb = normalizer.measurePeakDb(cleanedSamples)
        val rmsDb = normalizer.measureRMSDb(cleanedSamples)

        infoText.text = buildString {
            appendLine(getString(R.string.sf2_duration_value_ms, durationMs))
            appendLine(getString(R.string.sf2_peak_value_db, peakDb))
            appendLine(getString(R.string.sf2_rms_value_db, rmsDb))
        }

        updateTrimInfo()
        if (restoreLoop) {
            updateLoopInfo(pendingLoopStart!!, pendingLoopEnd!!)
        } else {
            updateLoopInfo(0, 0)
        }

        // Clear pending state
        pendingLoopStart = null
        pendingLoopEnd = null
        pendingHasLoop = null
        pendingAdsrPreset = null
    }

    private fun updateSpectrum(samples: ShortArray) {
        // Convert ShortArray to FloatArray (normalized -1.0 to 1.0)
        val floatSamples = FloatArray(samples.size) { i ->
            samples[i].toFloat() / Short.MAX_VALUE
        }
        spectrumView.generateFromSamples(floatSamples, 44100)
    }

    private fun updateInfo(result: AudioProcessor.ProcessedResult) {
        val durationMs = result.processedLengthMs
        val peakDb = result.peakDb
        val rmsDb = result.rmsDb

        infoText.text = buildString {
            appendLine(getString(R.string.sf2_duration_value_ms, durationMs))
            appendLine(getString(R.string.sf2_peak_value_db, peakDb))
            appendLine(getString(R.string.sf2_rms_value_db, rmsDb))
        }
    }

    private fun updateTrimInfo() {
        val durationMs = waveformView.getTrimmedDurationMs()
        trimInfoText.text = getString(R.string.sf2_duration_value_ms, durationMs)
    }

    private fun updateLoopInfo(loopStart: Int, loopEnd: Int) {
        if (processedSamples == null) return
        val sampleRate = 44100

        val loopLength = loopEnd - loopStart
        val loopMs = loopLength * 1000 / sampleRate

        val hasLoop = loopEnd > loopStart && loopLength > 100

        loopInfoText.text = if (hasLoop) {
            getString(R.string.sf2_loop_range_value, loopStart, loopEnd, loopMs)
        } else {
            getString(R.string.sf2_no_loop)
        }

        // Show/hide loop hint and sustain button
        loopHintText.visibility = if (hasLoop && waveformView.getEditMode() == SampleWaveformView.EditMode.LOOP) View.VISIBLE else View.GONE
        sustainTestButton.visibility = if (hasLoop) View.VISIBLE else View.GONE
    }

    /**
     * Preview the trimmed selection without applying it.
     * Plays only the portion between the trim markers.
     * Uses SimpleSamplePlayer for clean playback.
     */
    private fun previewTrim() {
        val trimmedSamples = waveformView.getTrimmedSamples()
        if (trimmedSamples == null || trimmedSamples.isEmpty()) {
            Toast.makeText(requireContext(), R.string.sf2_invalid_trim_selection, Toast.LENGTH_SHORT).show()
            return
        }

        // Play directly with SimpleSamplePlayer (no need to restore anything)
        simplePlayer.play(trimmedSamples)

        val trimDurationMs = trimmedSamples.size * 1000 / 44100
        Toast.makeText(
            requireContext(),
            getString(R.string.sf2_preview_duration_ms, trimDurationMs),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun applyTrim() {
        val trimmedSamples = waveformView.getTrimmedSamples()
        if (trimmedSamples == null || trimmedSamples.isEmpty()) {
            Toast.makeText(requireContext(), R.string.sf2_invalid_trim_selection, Toast.LENGTH_SHORT).show()
            return
        }

        val trimDurationMs = trimmedSamples.size * 1000 / 44100

        // Replace the current samples with trimmed version
        processedSamples = trimmedSamples
        waveformView.setSamples(trimmedSamples)
        waveformView.setTrimPoints(0, trimmedSamples.size)

        // Reset loop points to full range
        waveformView.setLoopPoints(0, trimmedSamples.size, false)

        // Update spectrum
        updateSpectrum(trimmedSamples)

        // Update info
        updateTrimInfo()
        updateLoopInfo(0, 0)

        Toast.makeText(
            requireContext(),
            getString(R.string.sf2_trim_applied, trimDurationMs),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun playPreview() {
        val samples = processedSamples
        if (samples == null || samples.isEmpty()) {
            Toast.makeText(requireContext(), R.string.sf2_no_sample_loaded, Toast.LENGTH_SHORT).show()
            return
        }

        // Apply ADSR envelope to samples
        val presetName = getPresetName(selectedAdsrPreset)
        android.util.Log.d("SampleEditor", "playPreview: $presetName - A=${selectedAdsrPreset.attackMs}ms D=${selectedAdsrPreset.decayMs}ms S=${selectedAdsrPreset.sustainLevel} R=${selectedAdsrPreset.releaseMs}ms")

        // Apply envelope using EnvelopeGenerator
        val withEnvelope = envelopeGenerator.applyEnvelope(samples, selectedAdsrPreset)

        // Play using SimpleSamplePlayer for clean, click-free playback
        simplePlayer.play(withEnvelope)

        Toast.makeText(
            requireContext(),
            getString(R.string.sf2_playing_with_envelope, presetName),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getPresetName(preset: EnvelopeGenerator.ADSRSettings): String {
        return when {
            preset.attackMs == 0f && preset.decayMs == 0f &&
            preset.sustainLevel == 1.0f && preset.releaseMs == 0f -> "None"
            preset.attackMs == EnvelopeGenerator.Presets.PIANO.attackMs &&
            preset.decayMs == EnvelopeGenerator.Presets.PIANO.decayMs -> "Piano"
            preset.sustainLevel == EnvelopeGenerator.Presets.ORGAN.sustainLevel &&
            preset.attackMs == EnvelopeGenerator.Presets.ORGAN.attackMs -> "Organ"
            preset.attackMs == EnvelopeGenerator.Presets.PAD.attackMs -> "Pad"
            preset.decayMs == EnvelopeGenerator.Presets.PLUCK.decayMs &&
            preset.sustainLevel == EnvelopeGenerator.Presets.PLUCK.sustainLevel -> "Pluck"
            preset.attackMs == EnvelopeGenerator.Presets.STRINGS.attackMs -> "Strings"
            else -> "Custom"
        }
    }

    /**
     * Add loop markers at the beginning and end of the sample.
     * User can then adjust the markers manually.
     */
    private fun addLoop() {
        val samples = processedSamples ?: return
        val sampleCount = samples.size

        if (sampleCount > 200) {
            waveformView.setLoopPoints(0, sampleCount - 1, true)
            updateLoopInfo(0, sampleCount - 1)
        }
    }

    private fun removeLoop() {
        waveformView.setLoopPoints(0, 0, false)
        loopInfoText.text = getString(R.string.sf2_no_loop)
        loopHintText.visibility = View.GONE
        sustainTestButton.visibility = View.GONE
    }

    /**
     * Start sustained playback - plays attack then loops continuously.
     * Called when the sustain test button is pressed.
     */
    private fun startSustainTest() {
        if (isSustainPlaying) return

        val samples = processedSamples ?: return
        val loopStart = waveformView.getLoopStartSample()
        val loopEnd = waveformView.getLoopEndSample()

        if (loopEnd <= loopStart || (loopEnd - loopStart) < 100) {
            Toast.makeText(requireContext(), getString(R.string.sf2_no_loop), Toast.LENGTH_SHORT).show()
            return
        }

        isSustainPlaying = true
        sustainTestButton.text = "..."

        // Calculate loop count for ~10 seconds of playback
        val loopDurationMs = (loopEnd - loopStart) * 1000 / 44100
        val loopCount = maxOf(1, (10000 / loopDurationMs))

        // Use SimpleSamplePlayer's built-in looped sample preparation
        val loopedSample = simplePlayer.prepareLoopedSample(
            samples = samples,
            loopStart = loopStart,
            loopEnd = loopEnd,
            loopCount = loopCount
        )

        simplePlayer.play(loopedSample)
    }

    /**
     * Stop sustained playback.
     * Called when the sustain test button is released.
     */
    private fun stopSustainTest() {
        if (!isSustainPlaying) return

        isSustainPlaying = false
        sustainTestButton.text = getString(R.string.sf2_sustain_test)
        simplePlayer.stop()
    }

    private fun applyProcessing() {
        val samples = rawSamples ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val options = AudioProcessor.ProcessingOptions(
                    removeDCOffset = true,
                    deClick = true,
                    applyNoiseGate = true,
                    autoDetectNoiseGate = true,
                    trimSilence = true,
                    normalize = true,
                    fadeInMs = 5,
                    fadeOutMs = 15,
                    detectLoop = true
                )
                audioProcessor.process(samples, options)
            }

            processingResult = result
            processedSamples = result.samples

            // Update display
            waveformView.setSamples(result.samples)
            waveformView.setTrimPoints(0, result.samples.size)
            waveformView.setLoopPoints(result.loopStart, result.loopEnd, result.hasLoop)
            updateSpectrum(result.samples)

            updateInfo(result)
            updateTrimInfo()
            updateLoopInfo(result.loopStart, result.loopEnd)

            Toast.makeText(requireContext(), R.string.sf2_processing_applied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Get the edited sample data.
     */
    @Suppress("unused")
    fun getEditedData(): EditedSampleData? {
        val samples = waveformView.getTrimmedSamples() ?: processedSamples ?: return null

        val trimStart = waveformView.getTrimStartSample()
        val loopStart = (waveformView.getLoopStartSample() - trimStart).coerceAtLeast(0)
        val loopEnd = (waveformView.getLoopEndSample() - trimStart).coerceAtMost(samples.size)

        return EditedSampleData(
            samples = samples,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = loopEnd > loopStart && (loopEnd - loopStart) > 100,
            adsrPreset = selectedAdsrPreset
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        samplePlayer.release()
        simplePlayer.release()
    }

    data class EditedSampleData(
        val samples: ShortArray,
        val loopStart: Int,
        val loopEnd: Int,
        val hasLoop: Boolean,
        val adsrPreset: EnvelopeGenerator.ADSRSettings
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EditedSampleData
            return samples.contentEquals(other.samples) &&
                    loopStart == other.loopStart &&
                    loopEnd == other.loopEnd
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + loopStart
            result = 31 * result + loopEnd
            return result
        }
    }
}
