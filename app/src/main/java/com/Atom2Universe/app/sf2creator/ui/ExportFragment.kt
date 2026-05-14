package com.Atom2Universe.app.sf2creator.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.sf2.Sf2Engine
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.ui.components.ExpandableSectionView
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for naming and exporting the SF2 file.
 * Allows testing the sample on a preview keyboard before export.
 * Now includes ALL SF2 parameters with expandable sections.
 */
class ExportFragment : Fragment() {

    // ==================== UI Elements ====================
    private lateinit var instrumentNameInput: EditText
    private lateinit var programLabel: TextView
    private lateinit var programSpinner: Spinner
    private lateinit var keyboardPreview: KeyboardPreviewView
    private lateinit var rootNoteText: TextView
    private lateinit var changeNoteButton: Button
    private lateinit var exportButton: Button
    private lateinit var shareButton: Button
    private lateinit var openMidiButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var keyboardLoadingIndicator: ProgressBar? = null
    private var octaveDownButton: ImageButton? = null
    private var octaveUpButton: ImageButton? = null
    private var addToProjectButton: Button? = null

    // Sections
    private lateinit var sectionTuning: ExpandableSectionView
    private lateinit var sectionVolEnv: ExpandableSectionView
    private lateinit var sectionModEnv: ExpandableSectionView
    private lateinit var sectionVibLfo: ExpandableSectionView
    private lateinit var sectionModLfo: ExpandableSectionView
    private lateinit var sectionFilter: ExpandableSectionView
    private lateinit var sectionEffects: ExpandableSectionView

    // Key/Velocity range UI
    private var keyRangeRow: View? = null
    private var keyRangeText: TextView? = null
    private var setKeyRangeButton: Button? = null
    private var velRangeRow: View? = null
    private var velRangeText: TextView? = null
    private var setVelRangeButton: Button? = null

    // Slider controls
    private lateinit var sliderControls: View
    private lateinit var selectedSliderLabel: TextView
    private lateinit var ctrlMinusLarge: Button
    private lateinit var ctrlMinusSmall: Button
    private lateinit var ctrlReset: Button
    private lateinit var ctrlPlusSmall: Button
    private lateinit var ctrlPlusLarge: Button

    // Parameter rows map
    private val paramRows = mutableMapOf<String, ParamRow>()
    private var selectedParam: String? = null

    // Engine selector UI
    private var engineNameText: TextView? = null
    private var switchEngineButton: Button? = null

    // ==================== State ====================
    private val sf2Writer = Sf2Writer()
    private var sampleFile: File? = null
    private var sampleAudio: ShortArray? = null
    private var rootNote: Int = 60
    private var exportedFile: File? = null
    private var pendingSetup = false

    private var sf2Engine: Sf2Engine? = null
    private var fluidSynthEngine: FluidSynthEngine? = null
    private var tempSf2File: File? = null
    private var engineReady: Boolean = false
    private var useFluidSynth: Boolean = false

    // Loop points
    private var loopStart: Int = 0
    private var loopEnd: Int = 0
    private var hasLoop: Boolean = false

    // Key/Velocity range
    private var keyRangeStart: Int = 0
    private var keyRangeEnd: Int = 127
    private var velRangeStart: Int = 0
    private var velRangeEnd: Int = 127
    private var keyRangeMode: Boolean = false

    // MIDI Program
    private var programNumber: Int = 0

    // ==================== All SF2 Parameters ====================
    // Tuning
    private var attenuation: Int = Sf2Constants.DEFAULT_ATTENUATION_CB
    private var coarseTune: Int = Sf2Constants.DEFAULT_COARSE_TUNE
    private var fineTuneCents: Int = Sf2Constants.DEFAULT_FINE_TUNE_CENTS
    private var scaleTuning: Int = Sf2Constants.DEFAULT_SCALE_TUNING

    // Volume Envelope
    private var volEnvDelayMs: Int = Sf2Constants.DEFAULT_VOL_ENV_DELAY_MS
    private var attackMs: Int = Sf2Constants.DEFAULT_ATTACK_MS
    private var volEnvHoldMs: Int = Sf2Constants.DEFAULT_VOL_ENV_HOLD_MS
    private var decayMs: Int = Sf2Constants.DEFAULT_DECAY_MS
    private var sustainPercent: Int = Sf2Constants.DEFAULT_SUSTAIN_PERCENT
    private var releaseMs: Int = Sf2Constants.DEFAULT_RELEASE_MS

    // Modulation Envelope
    private var modEnvDelayMs: Int = Sf2Constants.DEFAULT_MOD_ENV_DELAY_MS
    private var modEnvAttackMs: Int = Sf2Constants.DEFAULT_MOD_ENV_ATTACK_MS
    private var modEnvHoldMs: Int = Sf2Constants.DEFAULT_MOD_ENV_HOLD_MS
    private var modEnvDecayMs: Int = Sf2Constants.DEFAULT_MOD_ENV_DECAY_MS
    private var modEnvSustainPercent: Int = Sf2Constants.DEFAULT_MOD_ENV_SUSTAIN_PERCENT
    private var modEnvReleaseMs: Int = Sf2Constants.DEFAULT_MOD_ENV_RELEASE_MS
    private var modEnvToPitch: Int = Sf2Constants.DEFAULT_MOD_ENV_TO_PITCH
    private var modEnvToFilterFc: Int = Sf2Constants.DEFAULT_MOD_ENV_TO_FILTER_FC

    // Vibrato LFO
    private var vibLfoDelayMs: Int = Sf2Constants.DEFAULT_LFO_DELAY_MS
    private var vibLfoFreqCents: Int = Sf2Constants.DEFAULT_LFO_FREQ_CENTS
    private var vibLfoToPitch: Int = Sf2Constants.DEFAULT_LFO_TO_PITCH

    // Modulation LFO
    private var modLfoDelayMs: Int = Sf2Constants.DEFAULT_LFO_DELAY_MS
    private var modLfoFreqCents: Int = Sf2Constants.DEFAULT_LFO_FREQ_CENTS
    private var modLfoToPitch: Int = Sf2Constants.DEFAULT_LFO_TO_PITCH
    private var modLfoToFilterFc: Int = Sf2Constants.DEFAULT_LFO_TO_FILTER_FC
    private var modLfoToVolume: Int = Sf2Constants.DEFAULT_LFO_TO_VOLUME

    // Filter
    private var filterCutoffHz: Float = Sf2Constants.DEFAULT_FILTER_CUTOFF_HZ
    private var filterResonanceCb: Int = Sf2Constants.DEFAULT_FILTER_RESONANCE_CB

    // Effects
    private var chorusSend: Int = Sf2Constants.DEFAULT_CHORUS_SEND
    private var reverbSend: Int = Sf2Constants.DEFAULT_REVERB_SEND
    private var pan: Int = Sf2Constants.DEFAULT_PAN
    private var exclusiveClass: Int = Sf2Constants.EXCLUSIVE_CLASS_NONE

    // Edit mode
    private var editMode: Boolean = false
    private var pendingEditModeParams: EditModeParams? = null

    // Listeners
    var onExportComplete: ((File) -> Unit)? = null
    var onChangeNote: (() -> Unit)? = null
    var onAddToProject: (() -> Unit)? = null
    var onSaveExistingSample: (() -> Unit)? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { exportToUri(it) }
    }

    // ==================== Data Classes ====================
    data class EditModeParams(
        val sampleName: String,
        val keyStart: Int,
        val keyEnd: Int,
        val velStart: Int = 0,
        val velEnd: Int = 127,
        val attenuation: Int,
        val coarseTune: Int = 0,
        val fineTune: Int,
        val scaleTuning: Int = 100,
        val volEnvDelay: Int = 0,
        val attack: Int,
        val volEnvHold: Int = 0,
        val decay: Int,
        val sustain: Int,
        val release: Int,
        val modEnvDelay: Int = 0,
        val modEnvAttack: Int = 1,
        val modEnvHold: Int = 0,
        val modEnvDecay: Int = 1,
        val modEnvSustain: Int = 100,
        val modEnvRelease: Int = 1,
        val modEnvToPitch: Int = 0,
        val modEnvToFilter: Int = 0,
        val vibLfoDelay: Int = 0,
        val vibLfoFreq: Int = 0,
        val vibLfoToPitch: Int = 0,
        val modLfoDelay: Int = 0,
        val modLfoFreq: Int = 0,
        val modLfoToPitch: Int = 0,
        val modLfoToFilter: Int = 0,
        val modLfoToVol: Int = 0,
        val cutoffHz: Float,
        val resonanceCb: Int,
        val chorus: Int,
        val reverb: Int,
        val panValue: Int,
        val exclusiveClass: Int = 0
    )

    @Suppress("ArrayInDataClass")
    data class SampleParams(
        val name: String,
        val samples: ShortArray,
        val sampleRate: Int,
        val rootNote: Int,
        val keyRangeStart: Int,
        val keyRangeEnd: Int,
        val velRangeStart: Int = 0,
        val velRangeEnd: Int = 127,
        val loopStart: Int,
        val loopEnd: Int,
        val hasLoop: Boolean,
        val attenuation: Int,
        val coarseTune: Int = 0,
        val fineTuneCents: Int,
        val scaleTuning: Int = 100,
        val volEnvDelayMs: Int = 0,
        val attackMs: Int,
        val volEnvHoldMs: Int = 0,
        val decayMs: Int,
        val sustainPercent: Int,
        val releaseMs: Int,
        val modEnvDelayMs: Int = 0,
        val modEnvAttackMs: Int = 1,
        val modEnvHoldMs: Int = 0,
        val modEnvDecayMs: Int = 1,
        val modEnvSustainPercent: Int = 100,
        val modEnvReleaseMs: Int = 1,
        val modEnvToPitch: Int = 0,
        val modEnvToFilterFc: Int = 0,
        val vibLfoDelayMs: Int = 0,
        val vibLfoFreqCents: Int = 0,
        val vibLfoToPitch: Int = 0,
        val modLfoDelayMs: Int = 0,
        val modLfoFreqCents: Int = 0,
        val modLfoToPitch: Int = 0,
        val modLfoToFilterFc: Int = 0,
        val modLfoToVolume: Int = 0,
        val filterCutoffHz: Float,
        val filterResonanceCb: Int,
        val chorusSend: Int,
        val reverbSend: Int,
        val pan: Int,
        val exclusiveClass: Int = 0
    )

    private data class ParamRow(
        val seekbar: SeekBar,
        val valueText: TextView,
        val formatter: (Int) -> String,
        val defaultValue: Int,
        val smallIncrement: Int,
        val largeIncrement: Int
    ) {
        fun setProgress(progress: Int) {
            seekbar.progress = progress
            valueText.text = formatter(progress)
        }
    }

    // ==================== Lifecycle ====================
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_export_sf2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupProgramSpinner()
        setupKeyboard()
        setupSections()
        setupSliderControls()
        setupButtons()

        applyPendingSetup()
        updateUI()
    }

    private fun findViews(view: View) {
        instrumentNameInput = view.findViewById(R.id.instrument_name_input)
        programLabel = view.findViewById(R.id.program_label)
        programSpinner = view.findViewById(R.id.program_spinner)
        keyboardPreview = view.findViewById(R.id.keyboard_preview)
        rootNoteText = view.findViewById(R.id.root_note_text)
        changeNoteButton = view.findViewById(R.id.change_note_button)
        exportButton = view.findViewById(R.id.export_button)
        shareButton = view.findViewById(R.id.share_button)
        openMidiButton = view.findViewById(R.id.open_midi_button)
        progressBar = view.findViewById(R.id.progress_bar)
        statusText = view.findViewById(R.id.status_text)
        keyboardLoadingIndicator = view.findViewById(R.id.keyboard_loading_indicator)
        octaveDownButton = view.findViewById(R.id.octave_down_button)
        octaveUpButton = view.findViewById(R.id.octave_up_button)
        addToProjectButton = view.findViewById(R.id.add_to_project_button)
        engineNameText = view.findViewById(R.id.engine_name_text)
        switchEngineButton = view.findViewById(R.id.switch_engine_button)

        // Sections
        sectionTuning = view.findViewById(R.id.section_tuning)
        sectionVolEnv = view.findViewById(R.id.section_vol_env)
        sectionModEnv = view.findViewById(R.id.section_mod_env)
        sectionVibLfo = view.findViewById(R.id.section_vib_lfo)
        sectionModLfo = view.findViewById(R.id.section_mod_lfo)
        sectionFilter = view.findViewById(R.id.section_filter)
        sectionEffects = view.findViewById(R.id.section_effects)

        // Key/Vel range
        keyRangeRow = view.findViewById(R.id.key_range_row)
        keyRangeText = view.findViewById(R.id.key_range_text)
        setKeyRangeButton = view.findViewById(R.id.set_key_range_button)
        velRangeRow = view.findViewById(R.id.vel_range_row)
        velRangeText = view.findViewById(R.id.vel_range_text)
        setVelRangeButton = view.findViewById(R.id.set_vel_range_button)

        // Slider controls
        sliderControls = view.findViewById(R.id.slider_controls)
        selectedSliderLabel = view.findViewById(R.id.selected_slider_label)
        ctrlMinusLarge = view.findViewById(R.id.ctrl_minus_large)
        ctrlMinusSmall = view.findViewById(R.id.ctrl_minus_small)
        ctrlReset = view.findViewById(R.id.ctrl_reset)
        ctrlPlusSmall = view.findViewById(R.id.ctrl_plus_small)
        ctrlPlusLarge = view.findViewById(R.id.ctrl_plus_large)
    }

    // ==================== Sections Setup ====================
    private fun setupSections() {
        setupTuningSection()
        sectionTuning.setOnResetClickListener { resetTuningSection() }

        setupVolEnvSection()
        sectionVolEnv.setOnResetClickListener { resetVolEnvSection() }

        setupModEnvSection()
        sectionModEnv.setOnResetClickListener { resetModEnvSection() }

        setupVibLfoSection()
        sectionVibLfo.setOnResetClickListener { resetVibLfoSection() }

        setupModLfoSection()
        sectionModLfo.setOnResetClickListener { resetModLfoSection() }

        setupFilterSection()
        sectionFilter.setOnResetClickListener { resetFilterSection() }

        setupEffectsSection()
        sectionEffects.setOnResetClickListener { resetEffectsSection() }
    }

    // ==================== TUNING ====================
    private fun setupTuningSection() {
        setupParamRow("gain", R.id.gain_row, getString(R.string.sf2_gain_label), 0, 480, 480, 10, 50) { progress ->
            attenuation = 480 - progress
            formatDb(-attenuation / 10f)
        }
        setupParamRow("coarse_tune", R.id.coarse_tune_row, getString(R.string.sf2_coarse_tune_label), 0, 240, 120, 1, 12) { progress ->
            coarseTune = progress - 120
            formatSemitones(coarseTune)
        }
        setupParamRow("fine_tune", R.id.fine_tune_row, getString(R.string.sf2_fine_tune_label), 0, 198, 99, 5, 25) { progress ->
            fineTuneCents = progress - 99
            formatCents(fineTuneCents)
        }
        setupParamRow("scale_tune", R.id.scale_tune_row, getString(R.string.sf2_scale_tune_label), 0, 200, 100, 5, 25) { progress ->
            scaleTuning = progress
            formatScalePercent(progress)
        }
    }

    private fun resetTuningSection() {
        attenuation = Sf2Constants.DEFAULT_ATTENUATION_CB
        coarseTune = Sf2Constants.DEFAULT_COARSE_TUNE
        fineTuneCents = Sf2Constants.DEFAULT_FINE_TUNE_CENTS
        scaleTuning = Sf2Constants.DEFAULT_SCALE_TUNING

        paramRows["gain"]?.setProgress(480 - attenuation)
        paramRows["coarse_tune"]?.setProgress(coarseTune + 120)
        paramRows["fine_tune"]?.setProgress(fineTuneCents + 99)
        paramRows["scale_tune"]?.setProgress(scaleTuning)

        showResetToast()
        generatePreviewSf2()
    }

    // ==================== VOLUME ENVELOPE ====================
    private fun setupVolEnvSection() {
        setupParamRow("vol_env_delay", R.id.vol_env_delay_row, getString(R.string.sf2_delay_label), 0, 5000, 0, 10, 100) { progress ->
            volEnvDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("vol_env_attack", R.id.vol_env_attack_row, getString(R.string.sf2_attack_label), 0, 5000, Sf2Constants.DEFAULT_ATTACK_MS, 10, 100) { progress ->
            attackMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("vol_env_hold", R.id.vol_env_hold_row, getString(R.string.sf2_hold_label), 0, 5000, 0, 10, 100) { progress ->
            volEnvHoldMs = progress
            formatMs(progress)
        }
        setupParamRow("vol_env_decay", R.id.vol_env_decay_row, getString(R.string.sf2_decay_label), 0, 5000, Sf2Constants.DEFAULT_DECAY_MS, 10, 100) { progress ->
            decayMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("vol_env_sustain", R.id.vol_env_sustain_row, getString(R.string.sf2_sustain_label), 0, 100, 100, 5, 25) { progress ->
            sustainPercent = progress
            formatPercent(progress)
        }
        setupParamRow("vol_env_release", R.id.vol_env_release_row, getString(R.string.sf2_release_label), 0, 5000, Sf2Constants.DEFAULT_RELEASE_MS, 50, 250) { progress ->
            releaseMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
    }

    private fun resetVolEnvSection() {
        volEnvDelayMs = Sf2Constants.DEFAULT_VOL_ENV_DELAY_MS
        attackMs = Sf2Constants.DEFAULT_ATTACK_MS
        volEnvHoldMs = Sf2Constants.DEFAULT_VOL_ENV_HOLD_MS
        decayMs = Sf2Constants.DEFAULT_DECAY_MS
        sustainPercent = Sf2Constants.DEFAULT_SUSTAIN_PERCENT
        releaseMs = Sf2Constants.DEFAULT_RELEASE_MS

        paramRows["vol_env_delay"]?.setProgress(volEnvDelayMs)
        paramRows["vol_env_attack"]?.setProgress(attackMs)
        paramRows["vol_env_hold"]?.setProgress(volEnvHoldMs)
        paramRows["vol_env_decay"]?.setProgress(decayMs)
        paramRows["vol_env_sustain"]?.setProgress(sustainPercent)
        paramRows["vol_env_release"]?.setProgress(releaseMs)

        showResetToast()
        generatePreviewSf2()
    }

    // ==================== MODULATION ENVELOPE ====================
    private fun setupModEnvSection() {
        setupParamRow("mod_env_delay", R.id.mod_env_delay_row, getString(R.string.sf2_delay_label), 0, 5000, 0, 10, 100) { progress ->
            modEnvDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_env_attack", R.id.mod_env_attack_row, getString(R.string.sf2_attack_label), 0, 5000, 1, 10, 100) { progress ->
            modEnvAttackMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("mod_env_hold", R.id.mod_env_hold_row, getString(R.string.sf2_hold_label), 0, 5000, 0, 10, 100) { progress ->
            modEnvHoldMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_env_decay", R.id.mod_env_decay_row, getString(R.string.sf2_decay_label), 0, 5000, 1, 10, 100) { progress ->
            modEnvDecayMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("mod_env_sustain", R.id.mod_env_sustain_row, getString(R.string.sf2_sustain_label), 0, 100, 100, 5, 25) { progress ->
            modEnvSustainPercent = progress
            formatPercent(progress)
        }
        setupParamRow("mod_env_release", R.id.mod_env_release_row, getString(R.string.sf2_release_label), 0, 5000, 1, 50, 250) { progress ->
            modEnvReleaseMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("mod_env_to_pitch", R.id.mod_env_to_pitch_row, getString(R.string.sf2_to_pitch_label), 0, 240, 120, 10, 50) { progress ->
            modEnvToPitch = (progress - 120) * 100
            formatCents(modEnvToPitch)
        }
        setupParamRow("mod_env_to_filter", R.id.mod_env_to_filter_row, getString(R.string.sf2_to_filter_label), 0, 240, 120, 10, 50) { progress ->
            modEnvToFilterFc = (progress - 120) * 100
            formatCents(modEnvToFilterFc)
        }
    }

    private fun resetModEnvSection() {
        modEnvDelayMs = Sf2Constants.DEFAULT_MOD_ENV_DELAY_MS
        modEnvAttackMs = Sf2Constants.DEFAULT_MOD_ENV_ATTACK_MS
        modEnvHoldMs = Sf2Constants.DEFAULT_MOD_ENV_HOLD_MS
        modEnvDecayMs = Sf2Constants.DEFAULT_MOD_ENV_DECAY_MS
        modEnvSustainPercent = Sf2Constants.DEFAULT_MOD_ENV_SUSTAIN_PERCENT
        modEnvReleaseMs = Sf2Constants.DEFAULT_MOD_ENV_RELEASE_MS
        modEnvToPitch = Sf2Constants.DEFAULT_MOD_ENV_TO_PITCH
        modEnvToFilterFc = Sf2Constants.DEFAULT_MOD_ENV_TO_FILTER_FC

        paramRows["mod_env_delay"]?.setProgress(modEnvDelayMs)
        paramRows["mod_env_attack"]?.setProgress(modEnvAttackMs)
        paramRows["mod_env_hold"]?.setProgress(modEnvHoldMs)
        paramRows["mod_env_decay"]?.setProgress(modEnvDecayMs)
        paramRows["mod_env_sustain"]?.setProgress(modEnvSustainPercent)
        paramRows["mod_env_release"]?.setProgress(modEnvReleaseMs)
        paramRows["mod_env_to_pitch"]?.setProgress(modEnvToPitch / 100 + 120)
        paramRows["mod_env_to_filter"]?.setProgress(modEnvToFilterFc / 100 + 120)

        showResetToast()
    }

    // ==================== VIBRATO LFO ====================
    private fun setupVibLfoSection() {
        setupParamRow("vib_lfo_delay", R.id.vib_lfo_delay_row, getString(R.string.sf2_delay_label), 0, 5000, 0, 50, 250) { progress ->
            vibLfoDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("vib_lfo_freq", R.id.vib_lfo_freq_row, getString(R.string.sf2_lfo_freq_label), 0, 100, 50, 5, 20) { progress ->
            vibLfoFreqCents = lfoSeekbarToCents(progress)
            formatLfoFreq(vibLfoFreqCents)
        }
        setupParamRow("vib_lfo_to_pitch", R.id.vib_lfo_to_pitch_row, getString(R.string.sf2_to_pitch_label), 0, 240, 120, 10, 50) { progress ->
            vibLfoToPitch = (progress - 120) * 100
            formatCents(vibLfoToPitch)
        }
    }

    private fun resetVibLfoSection() {
        vibLfoDelayMs = Sf2Constants.DEFAULT_LFO_DELAY_MS
        vibLfoFreqCents = Sf2Constants.DEFAULT_LFO_FREQ_CENTS
        vibLfoToPitch = Sf2Constants.DEFAULT_LFO_TO_PITCH

        paramRows["vib_lfo_delay"]?.setProgress(vibLfoDelayMs)
        paramRows["vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(vibLfoFreqCents))
        paramRows["vib_lfo_to_pitch"]?.setProgress(vibLfoToPitch / 100 + 120)

        showResetToast()
    }

    // ==================== MODULATION LFO ====================
    private fun setupModLfoSection() {
        setupParamRow("mod_lfo_delay", R.id.mod_lfo_delay_row, getString(R.string.sf2_delay_label), 0, 5000, 0, 50, 250) { progress ->
            modLfoDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_lfo_freq", R.id.mod_lfo_freq_row, getString(R.string.sf2_lfo_freq_label), 0, 100, 50, 5, 20) { progress ->
            modLfoFreqCents = lfoSeekbarToCents(progress)
            formatLfoFreq(modLfoFreqCents)
        }
        setupParamRow("mod_lfo_to_pitch", R.id.mod_lfo_to_pitch_row, getString(R.string.sf2_to_pitch_label), 0, 240, 120, 10, 50) { progress ->
            modLfoToPitch = (progress - 120) * 100
            formatCents(modLfoToPitch)
        }
        setupParamRow("mod_lfo_to_filter", R.id.mod_lfo_to_filter_row, getString(R.string.sf2_to_filter_label), 0, 240, 120, 10, 50) { progress ->
            modLfoToFilterFc = (progress - 120) * 100
            formatCents(modLfoToFilterFc)
        }
        setupParamRow("mod_lfo_to_vol", R.id.mod_lfo_to_vol_row, getString(R.string.sf2_to_vol_label), 0, 192, 96, 10, 25) { progress ->
            modLfoToVolume = (progress - 96) * 10
            formatDb(modLfoToVolume / 10f)
        }
    }

    private fun resetModLfoSection() {
        modLfoDelayMs = Sf2Constants.DEFAULT_LFO_DELAY_MS
        modLfoFreqCents = Sf2Constants.DEFAULT_LFO_FREQ_CENTS
        modLfoToPitch = Sf2Constants.DEFAULT_LFO_TO_PITCH
        modLfoToFilterFc = Sf2Constants.DEFAULT_LFO_TO_FILTER_FC
        modLfoToVolume = Sf2Constants.DEFAULT_LFO_TO_VOLUME

        paramRows["mod_lfo_delay"]?.setProgress(modLfoDelayMs)
        paramRows["mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(modLfoFreqCents))
        paramRows["mod_lfo_to_pitch"]?.setProgress(modLfoToPitch / 100 + 120)
        paramRows["mod_lfo_to_filter"]?.setProgress(modLfoToFilterFc / 100 + 120)
        paramRows["mod_lfo_to_vol"]?.setProgress(modLfoToVolume / 10 + 96)

        showResetToast()
    }

    // ==================== FILTER ====================
    private fun setupFilterSection() {
        setupParamRow("filter_cutoff", R.id.filter_cutoff_row, getString(R.string.sf2_filter_cutoff_label), 0, 1000, 1000, 50, 200) { progress ->
            filterCutoffHz = seekbarToCutoffHz(progress)
            formatCutoffHz(filterCutoffHz)
        }
        setupParamRow("filter_resonance", R.id.filter_resonance_row, getString(R.string.sf2_filter_resonance_label), 0, 400, 0, 10, 50) { progress ->
            filterResonanceCb = progress
            formatDb(progress / 10f)
        }
    }

    private fun resetFilterSection() {
        filterCutoffHz = Sf2Constants.DEFAULT_FILTER_CUTOFF_HZ
        filterResonanceCb = Sf2Constants.DEFAULT_FILTER_RESONANCE_CB

        paramRows["filter_cutoff"]?.setProgress(cutoffHzToSeekbar(filterCutoffHz))
        paramRows["filter_resonance"]?.setProgress(filterResonanceCb)

        showResetToast()
        generatePreviewSf2()
    }

    // ==================== EFFECTS ====================
    private fun setupEffectsSection() {
        setupParamRow("chorus", R.id.chorus_row, getString(R.string.sf2_chorus_send_label), 0, 1000, 0, 100, 250) { progress ->
            chorusSend = progress
            formatEffectPercent(progress)
        }
        setupParamRow("reverb", R.id.reverb_row, getString(R.string.sf2_reverb_send_label), 0, 1000, 0, 100, 250) { progress ->
            reverbSend = progress
            formatEffectPercent(progress)
        }
        setupParamRow("pan", R.id.pan_row, getString(R.string.sf2_pan_label), 0, 1000, 500, 50, 250) { progress ->
            pan = progress - 500
            formatPan(pan)
        }
        setupParamRow("exclusive_class", R.id.exclusive_class_row, getString(R.string.sf2_exclusive_class_label), 0, 127, 0, 1, 10) { progress ->
            exclusiveClass = progress
            formatExclusiveClass(progress)
        }
    }

    private fun resetEffectsSection() {
        chorusSend = Sf2Constants.DEFAULT_CHORUS_SEND
        reverbSend = Sf2Constants.DEFAULT_REVERB_SEND
        pan = Sf2Constants.DEFAULT_PAN
        exclusiveClass = Sf2Constants.EXCLUSIVE_CLASS_NONE

        paramRows["chorus"]?.setProgress(chorusSend)
        paramRows["reverb"]?.setProgress(reverbSend)
        paramRows["pan"]?.setProgress(pan + 500)
        paramRows["exclusive_class"]?.setProgress(exclusiveClass)

        showResetToast()
        generatePreviewSf2()
    }

    // ==================== Parameter Row Setup ====================
    private fun setupParamRow(
        key: String,
        viewId: Int,
        label: String,
        min: Int,
        max: Int,
        defaultValue: Int,
        smallIncrement: Int,
        largeIncrement: Int,
        onProgress: (Int) -> String
    ) {
        val rowView = view?.findViewById<View>(viewId) ?: return
        val labelText = rowView.findViewById<TextView>(R.id.param_label)
        val seekbar = rowView.findViewById<SeekBar>(R.id.param_seekbar)
        val valueText = rowView.findViewById<TextView>(R.id.param_value)

        labelText.text = label
        seekbar.min = min
        seekbar.max = max
        seekbar.progress = defaultValue

        val row = ParamRow(seekbar, valueText, onProgress, defaultValue, smallIncrement, largeIncrement)
        paramRows[key] = row

        // Initialize display
        valueText.text = onProgress(defaultValue)

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = onProgress(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                selectParam(key)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                generatePreviewSf2()
            }
        })

        // Click on row to select
        rowView.setOnClickListener { selectParam(key) }
    }

    // ==================== Slider Controls ====================
    private fun setupSliderControls() {
        ctrlMinusLarge.setOnClickListener { adjustSelectedParam(-1, large = true) }
        ctrlMinusSmall.setOnClickListener { adjustSelectedParam(-1, large = false) }
        ctrlReset.setOnClickListener { resetSelectedParam() }
        ctrlPlusSmall.setOnClickListener { adjustSelectedParam(1, large = false) }
        ctrlPlusLarge.setOnClickListener { adjustSelectedParam(1, large = true) }
    }

    private fun selectParam(key: String) {
        if (selectedParam == key) return

        // Clear previous selection highlight
        selectedParam?.let { prev ->
            paramRows[prev]?.seekbar?.parent?.let { parent ->
                (parent as? View)?.background = null
            }
        }

        selectedParam = key

        paramRows[key]?.let { row ->
            selectedSliderLabel.text = getString(R.string.sf2_selected_slider, row.seekbar.parent?.let {
                (it as? View)?.findViewById<TextView>(R.id.param_label)?.text
            } ?: key)

            // Highlight selected row with theme accent color
            val sliderRow = row.seekbar.parent as? View
            sliderRow?.setBackgroundResource(R.drawable.bg_slider_selected)
            sliderRow?.backgroundTintList = ColorStateList.valueOf(getThemeAccentColor())

            // Move slider controls to appear right after the selected slider row
            val sliderParent = sliderRow?.parent as? ViewGroup
            if (sliderParent != null) {
                // Remove from current parent
                (sliderControls.parent as? ViewGroup)?.removeView(sliderControls)
                (selectedSliderLabel.parent as? ViewGroup)?.removeView(selectedSliderLabel)

                // Find the index of the slider row in its parent
                val sliderIndex = sliderParent.indexOfChild(sliderRow)

                // Insert controls right after the slider row
                sliderParent.addView(sliderControls, sliderIndex + 1)
                sliderParent.addView(selectedSliderLabel, sliderIndex + 2)
            }

            sliderControls.visibility = View.VISIBLE
            selectedSliderLabel.visibility = View.VISIBLE
        }
    }

    @Suppress("unused")
    private fun deselectParam() {
        selectedParam?.let { key ->
            paramRows[key]?.seekbar?.parent?.let { parent ->
                (parent as? View)?.background = null
            }
        }
        selectedParam = null
        sliderControls.visibility = View.GONE
        selectedSliderLabel.visibility = View.GONE
    }

    private fun adjustSelectedParam(direction: Int, large: Boolean) {
        val key = selectedParam ?: return
        val row = paramRows[key] ?: return
        val increment = if (large) row.largeIncrement else row.smallIncrement
        val newProgress = (row.seekbar.progress + direction * increment).coerceIn(0, row.seekbar.max)
        row.seekbar.progress = newProgress
        row.valueText.text = row.formatter(newProgress)
        generatePreviewSf2()
    }

    private fun resetSelectedParam() {
        val key = selectedParam ?: return
        val row = paramRows[key] ?: return
        row.setProgress(row.defaultValue)
        generatePreviewSf2()
    }

    // ==================== Formatting ====================
    private fun formatMs(ms: Int): String = getString(R.string.sf2_ms_value, ms)
    private fun formatPercent(percent: Int): String = getString(R.string.sf2_percent_value, percent)
    private fun formatCents(cents: Int): String = getString(R.string.sf2_cents_value, cents)
    private fun formatSemitones(semitones: Int): String = getString(R.string.sf2_semitones_value, semitones)
    private fun formatDb(db: Float): String = getString(R.string.sf2_gain_value, db)
    private fun formatScalePercent(percent: Int): String = getString(R.string.sf2_scale_value, percent)
    private fun formatEffectPercent(value: Int): String = getString(R.string.sf2_effects_send_value, value / 10f)

    private fun formatCutoffHz(hz: Float): String {
        return if (hz >= 1000f) {
            getString(R.string.sf2_filter_cutoff_value_khz, hz / 1000f)
        } else {
            getString(R.string.sf2_filter_cutoff_value_hz, hz.toInt())
        }
    }

    private fun formatLfoFreq(cents: Int): String {
        val hz = 8.176 * Math.pow(2.0, cents / 1200.0)
        return getString(R.string.sf2_hz_value, hz.toFloat())
    }

    private fun formatPan(pan: Int): String {
        return when {
            pan < 0 -> getString(R.string.sf2_pan_value_left, -pan / 5)
            pan > 0 -> getString(R.string.sf2_pan_value_right, pan / 5)
            else -> getString(R.string.sf2_pan_value_center)
        }
    }

    private fun formatExclusiveClass(classId: Int): String {
        return if (classId == 0) {
            getString(R.string.sf2_class_none)
        } else {
            getString(R.string.sf2_class_value, classId)
        }
    }

    // ==================== Conversions ====================
    private fun seekbarToCutoffHz(progress: Int): Float {
        return (200.0 * Math.pow(100.0, progress / 1000.0)).toFloat()
    }

    private fun cutoffHzToSeekbar(hz: Float): Int {
        return (1000.0 * Math.log10(hz.toDouble() / 200.0) / Math.log10(100.0)).toInt().coerceIn(0, 1000)
    }

    private fun lfoSeekbarToCents(progress: Int): Int {
        return ((progress - 50) * 200).coerceIn(-16000, 4500)
    }

    private fun lfoCentsToSeekbar(cents: Int): Int {
        return (cents / 200 + 50).coerceIn(0, 100)
    }

    private fun showResetToast() {
        Toast.makeText(requireContext(), R.string.sf2_section_reset_toast, Toast.LENGTH_SHORT).show()
    }

    // ==================== Keyboard & Preview ====================
    private fun setupProgramSpinner() {
        val instruments = resources.getStringArray(R.array.gm_instruments)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, instruments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        programSpinner.adapter = adapter

        programSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                programNumber = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                programNumber = 0
            }
        }
    }

    private fun setupKeyboard() {
        keyboardPreview.setRootNote(rootNote)

        keyboardPreview.onNoteOn = { note, velocity ->
            if (engineReady) {
                if (useFluidSynth) {
                    fluidSynthEngine?.sendNoteOn(0, note, velocity)
                } else {
                    sf2Engine?.sendNoteOn(0, note, velocity)
                }
            }
        }
        keyboardPreview.onNoteOff = { note ->
            if (engineReady) {
                if (useFluidSynth) {
                    fluidSynthEngine?.sendNoteOff(0, note)
                } else {
                    sf2Engine?.sendNoteOff(0, note)
                }
            }
        }

        octaveDownButton?.setOnClickListener {
            keyboardPreview.shiftOctaveDown()
            updateOctaveButtonStates()
        }

        octaveUpButton?.setOnClickListener {
            keyboardPreview.shiftOctaveUp()
            updateOctaveButtonStates()
        }

        keyboardPreview.onOctaveChanged = { _, _ ->
            updateOctaveButtonStates()
        }

        // Engine selector
        updateEngineDisplay()
        switchEngineButton?.setOnClickListener {
            switchEngine()
        }

        updateOctaveButtonStates()
    }

    private fun updateEngineDisplay() {
        val engineName = if (useFluidSynth) {
            getString(R.string.sf2_engine_fluidsynth)
        } else {
            getString(R.string.sf2_engine_sf2)
        }
        engineNameText?.text = getString(R.string.sf2_preview_engine, engineName)
    }

    private fun switchEngine() {
        // Check if FluidSynth is available before switching to it
        if (!useFluidSynth && !FluidSynthEngine.isSupported()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.sf2_engine_not_available, getString(R.string.sf2_engine_fluidsynth)),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        useFluidSynth = !useFluidSynth
        updateEngineDisplay()

        // Regenerate preview with the new engine
        if (sampleAudio != null) {
            generatePreviewSf2()
        }
    }

    private fun updateOctaveButtonStates() {
        octaveDownButton?.apply {
            isEnabled = keyboardPreview.canShiftDown()
            alpha = if (isEnabled) 1f else 0.3f
        }
        octaveUpButton?.apply {
            isEnabled = keyboardPreview.canShiftUp()
            alpha = if (isEnabled) 1f else 0.3f
        }
    }

    private fun generatePreviewSf2() {
        val samples = sampleAudio ?: return

        keyboardLoadingIndicator?.visibility = View.VISIBLE
        engineReady = false

        viewLifecycleOwner.lifecycleScope.launch {
            // Release previous engines
            sf2Engine?.stopAudioRenderer()
            sf2Engine?.release()
            sf2Engine = null

            fluidSynthEngine?.release()
            fluidSynthEngine = null

            Sf2FileCache.clear()

            val sf2File = withContext(Dispatchers.IO) {
                val tempDir = requireContext().cacheDir
                val tempFile = File(tempDir, "preview_keyboard.sf2")

                // Convert UI values (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
                val inMemorySample = Sf2Writer.InMemorySample(
                    name = "Preview",
                    samples = samples,
                    sampleRate = 44100,
                    rootNote = rootNote,
                    keyRangeStart = 0,
                    keyRangeEnd = 127,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    hasLoop = hasLoop,
                    attenuation = attenuation,
                    fineTuneCents = fineTuneCents,
                    // Volume Envelope - convert ms to timecents, % to centibels
                    volEnvAttack = Sf2UnitConverter.msToTimecents(attackMs),
                    volEnvDecay = Sf2UnitConverter.msToTimecents(decayMs),
                    volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(sustainPercent),
                    volEnvRelease = Sf2UnitConverter.msToTimecents(releaseMs),
                    // Filter - convert Hz to cents
                    filterFc = Sf2UnitConverter.hzToFilterCents(filterCutoffHz),
                    filterQ = filterResonanceCb,
                    chorusSend = chorusSend,
                    reverbSend = reverbSend,
                    pan = pan
                )

                val success = sf2Writer.writeSf2FromMemory(tempFile, "Preview", inMemorySample)
                if (success) tempFile else null
            }

            if (sf2File != null) {
                tempSf2File = sf2File

                if (useFluidSynth && FluidSynthEngine.isSupported()) {
                    // Use FluidSynth engine
                    val engine = FluidSynthEngine(requireContext())
                    val initialized = engine.initialize(sf2File.absolutePath)

                    if (initialized) {
                        engine.startAudioRenderer()
                        fluidSynthEngine = engine
                        engineReady = true
                    }
                } else {
                    // Use SF2 Engine (default)
                    val engine = Sf2Engine(requireContext())
                    val initialized = engine.initialize(sf2File.absolutePath)

                    if (initialized) {
                        engine.startAudioRenderer()
                        sf2Engine = engine
                        engineReady = true
                    }

                    // If FluidSynth was requested but not available, update display
                    if (useFluidSynth) {
                        useFluidSynth = false
                        updateEngineDisplay()
                    }
                }
            }

            keyboardLoadingIndicator?.visibility = View.GONE
        }
    }

    // ==================== Buttons ====================
    private fun setupButtons() {
        changeNoteButton.setOnClickListener { onChangeNote?.invoke() }
        exportButton.setOnClickListener { exportSf2() }
        shareButton.setOnClickListener { shareSf2() }
        openMidiButton.setOnClickListener { openInMidiPlayer() }

        addToProjectButton?.setOnClickListener {
            if (editMode) {
                onSaveExistingSample?.invoke()
            } else {
                onAddToProject?.invoke()
            }
        }

        setKeyRangeButton?.setOnClickListener { showKeyRangeDialog() }
        setVelRangeButton?.setOnClickListener { showVelRangeDialog() }
    }

    private fun showKeyRangeDialog() {
        KeyRangeDialog(
            context = requireContext(),
            rootNote = rootNote,
            currentStart = keyRangeStart,
            currentEnd = keyRangeEnd,
            onRangeSelected = { start, end ->
                keyRangeStart = start
                keyRangeEnd = end
                keyRangeMode = true
                updateKeyRangeDisplay()
            }
        ).show()
    }

    private fun showVelRangeDialog() {
        VelocityRangeDialog(
            context = requireContext(),
            currentStart = velRangeStart,
            currentEnd = velRangeEnd,
            onRangeSelected = { start, end ->
                velRangeStart = start
                velRangeEnd = end
                updateVelRangeDisplay()
            }
        ).show()
    }

    private fun updateKeyRangeDisplay() {
        val startName = SampleData.midiNoteToName(keyRangeStart)
        val endName = SampleData.midiNoteToName(keyRangeEnd)
        keyRangeText?.text = getString(R.string.sf2_key_range_label, startName, endName)

        // Update keyboard visualization
        if (keyRangeMode && ::keyboardPreview.isInitialized) {
            keyboardPreview.setKeyRange(keyRangeStart, keyRangeEnd)
        }
    }

    private fun updateVelRangeDisplay() {
        velRangeText?.text = getString(R.string.sf2_vel_range_label, velRangeStart, velRangeEnd)
    }

    // ==================== Public API ====================
    fun setSampleData(file: File?, samples: ShortArray, note: Int) {
        sampleFile = file
        sampleAudio = samples
        rootNote = note
        pendingSetup = true

        if (view != null) {
            applyPendingSetup()
        }
    }

    fun setLoopPoints(start: Int, end: Int, loop: Boolean) {
        loopStart = start
        loopEnd = end
        hasLoop = loop
    }

    fun updateRootNote(note: Int) {
        rootNote = note
        if (view != null) {
            keyboardPreview.setRootNote(rootNote)
            generatePreviewSf2()
            updateUI()
        }
    }

    fun enableEditMode(
        sampleName: String,
        keyStart: Int,
        keyEnd: Int,
        velStart: Int = 0,
        velEnd: Int = 127,
        attenuation: Int,
        coarseTune: Int = 0,
        fineTune: Int,
        scaleTuning: Int = 100,
        volEnvDelay: Int = 0,
        attack: Int,
        volEnvHold: Int = 0,
        decay: Int,
        sustain: Int,
        release: Int,
        modEnvDelay: Int = 0,
        modEnvAttack: Int = 1,
        modEnvHold: Int = 0,
        modEnvDecay: Int = 1,
        modEnvSustain: Int = 100,
        modEnvRelease: Int = 1,
        modEnvToPitch: Int = 0,
        modEnvToFilter: Int = 0,
        vibLfoDelay: Int = 0,
        vibLfoFreq: Int = 0,
        vibLfoToPitch: Int = 0,
        modLfoDelay: Int = 0,
        modLfoFreq: Int = 0,
        modLfoToPitch: Int = 0,
        modLfoToFilter: Int = 0,
        modLfoToVol: Int = 0,
        cutoffHz: Float,
        resonanceCb: Int,
        chorus: Int,
        reverb: Int,
        panValue: Int,
        exclusiveClass: Int = 0
    ) {
        editMode = true
        pendingEditModeParams = EditModeParams(
            sampleName = sampleName,
            keyStart = keyStart,
            keyEnd = keyEnd,
            velStart = velStart,
            velEnd = velEnd,
            attenuation = attenuation,
            coarseTune = coarseTune,
            fineTune = fineTune,
            scaleTuning = scaleTuning,
            volEnvDelay = volEnvDelay,
            attack = attack,
            volEnvHold = volEnvHold,
            decay = decay,
            sustain = sustain,
            release = release,
            modEnvDelay = modEnvDelay,
            modEnvAttack = modEnvAttack,
            modEnvHold = modEnvHold,
            modEnvDecay = modEnvDecay,
            modEnvSustain = modEnvSustain,
            modEnvRelease = modEnvRelease,
            modEnvToPitch = modEnvToPitch,
            modEnvToFilter = modEnvToFilter,
            vibLfoDelay = vibLfoDelay,
            vibLfoFreq = vibLfoFreq,
            vibLfoToPitch = vibLfoToPitch,
            modLfoDelay = modLfoDelay,
            modLfoFreq = modLfoFreq,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilter = modLfoToFilter,
            modLfoToVol = modLfoToVol,
            cutoffHz = cutoffHz,
            resonanceCb = resonanceCb,
            chorus = chorus,
            reverb = reverb,
            panValue = panValue,
            exclusiveClass = exclusiveClass
        )

        if (view != null && ::exportButton.isInitialized) {
            applyEditModeParams()
        }
    }

    fun restoreAllParams(params: SampleParams) {
        loopStart = params.loopStart
        loopEnd = params.loopEnd
        hasLoop = params.hasLoop

        keyRangeStart = params.keyRangeStart
        keyRangeEnd = params.keyRangeEnd
        velRangeStart = params.velRangeStart
        velRangeEnd = params.velRangeEnd
        keyRangeMode = (params.keyRangeStart != params.rootNote || params.keyRangeEnd != params.rootNote)

        attenuation = params.attenuation
        coarseTune = params.coarseTune
        fineTuneCents = params.fineTuneCents
        scaleTuning = params.scaleTuning

        volEnvDelayMs = params.volEnvDelayMs
        attackMs = params.attackMs
        volEnvHoldMs = params.volEnvHoldMs
        decayMs = params.decayMs
        sustainPercent = params.sustainPercent
        releaseMs = params.releaseMs

        modEnvDelayMs = params.modEnvDelayMs
        modEnvAttackMs = params.modEnvAttackMs
        modEnvHoldMs = params.modEnvHoldMs
        modEnvDecayMs = params.modEnvDecayMs
        modEnvSustainPercent = params.modEnvSustainPercent
        modEnvReleaseMs = params.modEnvReleaseMs
        modEnvToPitch = params.modEnvToPitch
        modEnvToFilterFc = params.modEnvToFilterFc

        vibLfoDelayMs = params.vibLfoDelayMs
        vibLfoFreqCents = params.vibLfoFreqCents
        vibLfoToPitch = params.vibLfoToPitch

        modLfoDelayMs = params.modLfoDelayMs
        modLfoFreqCents = params.modLfoFreqCents
        modLfoToPitch = params.modLfoToPitch
        modLfoToFilterFc = params.modLfoToFilterFc
        modLfoToVolume = params.modLfoToVolume

        filterCutoffHz = params.filterCutoffHz
        filterResonanceCb = params.filterResonanceCb

        chorusSend = params.chorusSend
        reverbSend = params.reverbSend
        pan = params.pan
        exclusiveClass = params.exclusiveClass

        if (view != null) {
            updateAllSlidersFromValues()
        }
    }

    fun getSampleParams(): SampleParams? {
        val samples = sampleAudio ?: return null
        val name = instrumentNameInput.text.toString().trim().ifEmpty { "Sample" }

        return SampleParams(
            name = name,
            samples = samples,
            sampleRate = 44100,
            rootNote = rootNote,
            keyRangeStart = if (keyRangeMode) keyRangeStart else rootNote,
            keyRangeEnd = if (keyRangeMode) keyRangeEnd else rootNote,
            velRangeStart = velRangeStart,
            velRangeEnd = velRangeEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = hasLoop,
            attenuation = attenuation,
            coarseTune = coarseTune,
            fineTuneCents = fineTuneCents,
            scaleTuning = scaleTuning,
            volEnvDelayMs = volEnvDelayMs,
            attackMs = attackMs,
            volEnvHoldMs = volEnvHoldMs,
            decayMs = decayMs,
            sustainPercent = sustainPercent,
            releaseMs = releaseMs,
            modEnvDelayMs = modEnvDelayMs,
            modEnvAttackMs = modEnvAttackMs,
            modEnvHoldMs = modEnvHoldMs,
            modEnvDecayMs = modEnvDecayMs,
            modEnvSustainPercent = modEnvSustainPercent,
            modEnvReleaseMs = modEnvReleaseMs,
            modEnvToPitch = modEnvToPitch,
            modEnvToFilterFc = modEnvToFilterFc,
            vibLfoDelayMs = vibLfoDelayMs,
            vibLfoFreqCents = vibLfoFreqCents,
            vibLfoToPitch = vibLfoToPitch,
            modLfoDelayMs = modLfoDelayMs,
            modLfoFreqCents = modLfoFreqCents,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilterFc = modLfoToFilterFc,
            modLfoToVolume = modLfoToVolume,
            filterCutoffHz = filterCutoffHz,
            filterResonanceCb = filterResonanceCb,
            chorusSend = chorusSend,
            reverbSend = reverbSend,
            pan = pan,
            exclusiveClass = exclusiveClass
        )
    }

    // ==================== Private Helpers ====================
    private fun applyPendingSetup() {
        if (!pendingSetup) return
        pendingSetup = false

        sampleAudio?.let {
            keyboardPreview.setRootNote(rootNote)
            generatePreviewSf2()
        }

        updateAllSlidersFromValues()
        updateUI()

        if (pendingEditModeParams != null) {
            applyEditModeParams()
        }
    }

    private fun applyEditModeParams() {
        val params = pendingEditModeParams ?: return

        exportButton.visibility = View.GONE
        programLabel.visibility = View.GONE
        programSpinner.visibility = View.GONE

        addToProjectButton?.text = getString(R.string.sf2_save)
        addToProjectButton?.visibility = View.VISIBLE

        keyRangeRow?.visibility = View.VISIBLE
        velRangeRow?.visibility = View.VISIBLE

        instrumentNameInput.setText(params.sampleName)
        keyRangeStart = params.keyStart
        keyRangeEnd = params.keyEnd
        velRangeStart = params.velStart
        velRangeEnd = params.velEnd
        keyRangeMode = true
        updateKeyRangeDisplay()
        updateVelRangeDisplay()

        attenuation = params.attenuation
        coarseTune = params.coarseTune
        fineTuneCents = params.fineTune
        scaleTuning = params.scaleTuning

        volEnvDelayMs = params.volEnvDelay
        attackMs = params.attack
        volEnvHoldMs = params.volEnvHold
        decayMs = params.decay
        sustainPercent = params.sustain
        releaseMs = params.release

        modEnvDelayMs = params.modEnvDelay
        modEnvAttackMs = params.modEnvAttack
        modEnvHoldMs = params.modEnvHold
        modEnvDecayMs = params.modEnvDecay
        modEnvSustainPercent = params.modEnvSustain
        modEnvReleaseMs = params.modEnvRelease
        modEnvToPitch = params.modEnvToPitch
        modEnvToFilterFc = params.modEnvToFilter

        vibLfoDelayMs = params.vibLfoDelay
        vibLfoFreqCents = params.vibLfoFreq
        vibLfoToPitch = params.vibLfoToPitch

        modLfoDelayMs = params.modLfoDelay
        modLfoFreqCents = params.modLfoFreq
        modLfoToPitch = params.modLfoToPitch
        modLfoToFilterFc = params.modLfoToFilter
        modLfoToVolume = params.modLfoToVol

        filterCutoffHz = params.cutoffHz
        filterResonanceCb = params.resonanceCb

        chorusSend = params.chorus
        reverbSend = params.reverb
        pan = params.panValue
        exclusiveClass = params.exclusiveClass

        updateAllSlidersFromValues()
        generatePreviewSf2()

        pendingEditModeParams = null
    }

    private fun updateAllSlidersFromValues() {
        paramRows["gain"]?.setProgress(480 - attenuation)
        paramRows["coarse_tune"]?.setProgress(coarseTune + 120)
        paramRows["fine_tune"]?.setProgress(fineTuneCents + 99)
        paramRows["scale_tune"]?.setProgress(scaleTuning)

        paramRows["vol_env_delay"]?.setProgress(volEnvDelayMs)
        paramRows["vol_env_attack"]?.setProgress(attackMs)
        paramRows["vol_env_hold"]?.setProgress(volEnvHoldMs)
        paramRows["vol_env_decay"]?.setProgress(decayMs)
        paramRows["vol_env_sustain"]?.setProgress(sustainPercent)
        paramRows["vol_env_release"]?.setProgress(releaseMs)

        paramRows["mod_env_delay"]?.setProgress(modEnvDelayMs)
        paramRows["mod_env_attack"]?.setProgress(modEnvAttackMs)
        paramRows["mod_env_hold"]?.setProgress(modEnvHoldMs)
        paramRows["mod_env_decay"]?.setProgress(modEnvDecayMs)
        paramRows["mod_env_sustain"]?.setProgress(modEnvSustainPercent)
        paramRows["mod_env_release"]?.setProgress(modEnvReleaseMs)
        paramRows["mod_env_to_pitch"]?.setProgress(modEnvToPitch / 100 + 120)
        paramRows["mod_env_to_filter"]?.setProgress(modEnvToFilterFc / 100 + 120)

        paramRows["vib_lfo_delay"]?.setProgress(vibLfoDelayMs)
        paramRows["vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(vibLfoFreqCents))
        paramRows["vib_lfo_to_pitch"]?.setProgress(vibLfoToPitch / 100 + 120)

        paramRows["mod_lfo_delay"]?.setProgress(modLfoDelayMs)
        paramRows["mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(modLfoFreqCents))
        paramRows["mod_lfo_to_pitch"]?.setProgress(modLfoToPitch / 100 + 120)
        paramRows["mod_lfo_to_filter"]?.setProgress(modLfoToFilterFc / 100 + 120)
        paramRows["mod_lfo_to_vol"]?.setProgress(modLfoToVolume / 10 + 96)

        paramRows["filter_cutoff"]?.setProgress(cutoffHzToSeekbar(filterCutoffHz))
        paramRows["filter_resonance"]?.setProgress(filterResonanceCb)

        paramRows["chorus"]?.setProgress(chorusSend)
        paramRows["reverb"]?.setProgress(reverbSend)
        paramRows["pan"]?.setProgress(pan + 500)
        paramRows["exclusive_class"]?.setProgress(exclusiveClass)

        if (keyRangeMode) {
            updateKeyRangeDisplay()
            updateVelRangeDisplay()
        }
    }

    private fun updateUI() {
        if (!::rootNoteText.isInitialized) return

        rootNoteText.text = getString(
            R.string.sf2_root_note_label,
            SampleData.midiNoteToName(rootNote),
            SampleData.midiNoteToFrequency(rootNote)
        )

        val hasExported = exportedFile != null
        shareButton.visibility = if (hasExported) View.VISIBLE else View.GONE
        openMidiButton.visibility = if (hasExported) View.VISIBLE else View.GONE
    }

    // ==================== Export ====================
    private fun exportSf2() {
        val name = instrumentNameInput.text.toString().trim()
        if (name.isEmpty()) {
            instrumentNameInput.error = getString(R.string.sf2_name_required)
            return
        }

        if (sampleAudio == null) {
            Toast.makeText(requireContext(), R.string.sf2_sample_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        exportLauncher.launch("$sanitizedName.sf2")
    }

    private fun exportToUri(uri: Uri) {
        val name = instrumentNameInput.text.toString().trim()
        val samples = sampleAudio

        if (name.isEmpty() || samples == null) return

        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false
        statusText.text = getString(R.string.sf2_exporting)

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempFile = File(requireContext().cacheDir, "temp_export.sf2")

                    // Convert UI values (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
                    val inMemorySample = Sf2Writer.InMemorySample(
                        name = name,
                        samples = samples,
                        sampleRate = 44100,
                        rootNote = rootNote,
                        keyRangeStart = 0,
                        keyRangeEnd = 127,
                        loopStart = if (hasLoop && loopEnd > loopStart) loopStart else 0,
                        loopEnd = if (hasLoop && loopEnd > loopStart) loopEnd else 0,
                        hasLoop = hasLoop && loopEnd > loopStart,
                        attenuation = attenuation,
                        fineTuneCents = fineTuneCents,
                        // Volume Envelope - convert ms to timecents, % to centibels
                        volEnvAttack = Sf2UnitConverter.msToTimecents(attackMs),
                        volEnvDecay = Sf2UnitConverter.msToTimecents(decayMs),
                        volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(sustainPercent),
                        volEnvRelease = Sf2UnitConverter.msToTimecents(releaseMs),
                        // Filter - convert Hz to cents
                        filterFc = Sf2UnitConverter.hzToFilterCents(filterCutoffHz),
                        filterQ = filterResonanceCb,
                        chorusSend = chorusSend,
                        reverbSend = reverbSend,
                        pan = pan
                    )

                    val writeSuccess = sf2Writer.writeSf2FromMemory(tempFile, name, inMemorySample, programNumber)

                    if (writeSuccess && sf2Writer.validateSf2(tempFile)) {
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        tempFile.delete()
                        true
                    } else {
                        tempFile.delete()
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving SF2 file", e)
                    false
                }
            }

            progressBar.visibility = View.GONE
            exportButton.isEnabled = true

            if (success) {
                statusText.text = getString(R.string.sf2_save_as_success)
                Toast.makeText(requireContext(), R.string.sf2_save_as_success, Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = getString(R.string.sf2_export_failed)
                Toast.makeText(requireContext(), R.string.sf2_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareSf2() {
        val file = exportedFile ?: return

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.sf2_share_title)))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing SF2 file", e)
            Toast.makeText(requireContext(), R.string.sf2_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInMidiPlayer() {
        try {
            val intent = Intent(requireContext(), com.Atom2Universe.app.midi.ui.MidiPlayerActivity::class.java)
            startActivity(intent)

            Toast.makeText(
                requireContext(),
                getString(R.string.sf2_open_midi_hint),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening MIDI player", e)
            Toast.makeText(requireContext(), R.string.sf2_open_midi_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sf2Engine?.stopAudioRenderer()
        sf2Engine?.release()
        sf2Engine = null
        fluidSynthEngine?.stopAudioRenderer()
        fluidSynthEngine?.release()
        fluidSynthEngine = null
        engineReady = false
        tempSf2File?.delete()
        tempSf2File = null
    }

    /**
     * Get the theme accent color.
     */
    private fun getThemeAccentColor(): Int {
        val typedValue = TypedValue()
        return if (requireContext().theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)) {
            typedValue.data
        } else {
            0xFF4CAF50.toInt() // Fallback
        }
    }

    companion object {
        private const val TAG = "ExportFragment"

        fun newInstance(): ExportFragment {
            return ExportFragment()
        }
    }
}
