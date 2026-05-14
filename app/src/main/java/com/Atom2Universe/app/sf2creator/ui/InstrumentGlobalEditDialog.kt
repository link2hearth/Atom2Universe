package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.ui.components.ExpandableSectionView

/**
 * Dialog for editing SF2 instrument global zone parameters.
 * These parameters apply to ALL samples in the instrument and are written
 * to the global zone when exporting to SF2.
 *
 * All values are stored in native SF2 units (timecents for times, centibels for levels, etc.)
 * which allows for lossless round-trip import/export.
 */
class InstrumentGlobalEditDialog(
    context: Context,
    private val instrument: Sf2InstrumentEntity,
    private val onSave: (Sf2InstrumentEntity) -> Unit
) : Dialog(context) {

    // UI elements
    private lateinit var dialogTitle: TextView
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    // Sections
    private lateinit var sectionVolEnv: ExpandableSectionView
    private lateinit var sectionModEnv: ExpandableSectionView
    private lateinit var sectionVibLfo: ExpandableSectionView
    private lateinit var sectionModLfo: ExpandableSectionView
    private lateinit var sectionTuning: ExpandableSectionView
    private lateinit var sectionFilter: ExpandableSectionView
    private lateinit var sectionEffects: ExpandableSectionView

    // Parameter rows - stored for easy access
    private val paramRows = mutableMapOf<String, ParamRow>()

    // All editable values (initialized from instrument) - stored in SF2 native units (timecents)
    // Volume Envelope
    private var globalVolEnvDelay: Int = instrument.globalVolEnvDelay
    private var globalVolEnvAttack: Int = instrument.globalVolEnvAttack
    private var globalVolEnvHold: Int = instrument.globalVolEnvHold
    private var globalVolEnvDecay: Int = instrument.globalVolEnvDecay
    private var globalVolEnvSustain: Int = instrument.globalVolEnvSustain
    private var globalVolEnvRelease: Int = instrument.globalVolEnvRelease

    // Modulation Envelope
    private var globalModEnvDelay: Int = instrument.globalModEnvDelay
    private var globalModEnvAttack: Int = instrument.globalModEnvAttack
    private var globalModEnvHold: Int = instrument.globalModEnvHold
    private var globalModEnvDecay: Int = instrument.globalModEnvDecay
    private var globalModEnvSustain: Int = instrument.globalModEnvSustain
    private var globalModEnvRelease: Int = instrument.globalModEnvRelease
    private var globalModEnvToPitch: Int = instrument.globalModEnvToPitch
    private var globalModEnvToFilterFc: Int = instrument.globalModEnvToFilterFc

    // Vibrato LFO
    private var globalVibLfoDelay: Int = instrument.globalVibLfoDelay
    private var globalVibLfoFreq: Int = instrument.globalVibLfoFreq
    private var globalVibLfoToPitch: Int = instrument.globalVibLfoToPitch

    // Modulation LFO
    private var globalModLfoDelay: Int = instrument.globalModLfoDelay
    private var globalModLfoFreq: Int = instrument.globalModLfoFreq
    private var globalModLfoToPitch: Int = instrument.globalModLfoToPitch
    private var globalModLfoToFilterFc: Int = instrument.globalModLfoToFilterFc
    private var globalModLfoToVolume: Int = instrument.globalModLfoToVolume

    // Tuning
    private var globalCoarseTune: Int = instrument.globalCoarseTune
    private var globalFineTune: Int = instrument.globalFineTune
    private var globalAttenuation: Int = instrument.globalAttenuation

    // Filter
    private var globalFilterFc: Int = instrument.globalFilterFc
    private var globalFilterQ: Int = instrument.globalFilterQ

    // Effects
    private var globalChorusSend: Int = instrument.globalChorusSend
    private var globalReverbSend: Int = instrument.globalReverbSend
    private var globalPan: Int = instrument.globalPan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_instrument_global_edit)

        // Make dialog wider and limit height
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.85).toInt()
        )

        findViews()
        setupSections()
        setupButtons()
        loadPresetValues()
    }

    private fun findViews() {
        dialogTitle = findViewById(R.id.dialog_title)
        cancelButton = findViewById(R.id.cancel_button)
        saveButton = findViewById(R.id.save_button)

        sectionVolEnv = findViewById(R.id.section_global_vol_env)
        sectionModEnv = findViewById(R.id.section_global_mod_env)
        sectionVibLfo = findViewById(R.id.section_global_vib_lfo)
        sectionModLfo = findViewById(R.id.section_global_mod_lfo)
        sectionTuning = findViewById(R.id.section_global_tuning)
        sectionFilter = findViewById(R.id.section_global_filter)
        sectionEffects = findViewById(R.id.section_global_effects)

        // Set title with instrument name
        dialogTitle.text = context.getString(R.string.sf2_global_params_title_format, instrument.name)
    }

    private fun setupSections() {
        // Volume Envelope
        setupVolEnvSection()
        sectionVolEnv.setOnResetClickListener { resetVolEnvSection() }

        // Modulation Envelope
        setupModEnvSection()
        sectionModEnv.setOnResetClickListener { resetModEnvSection() }

        // Vibrato LFO
        setupVibLfoSection()
        sectionVibLfo.setOnResetClickListener { resetVibLfoSection() }

        // Modulation LFO
        setupModLfoSection()
        sectionModLfo.setOnResetClickListener { resetModLfoSection() }

        // Tuning
        setupTuningSection()
        sectionTuning.setOnResetClickListener { resetTuningSection() }

        // Filter
        setupFilterSection()
        sectionFilter.setOnResetClickListener { resetFilterSection() }

        // Effects
        setupEffectsSection()
        sectionEffects.setOnResetClickListener { resetEffectsSection() }

        // Masquer les sections qui n'ont aucun paramètre visible
        updateSectionVisibility()
    }

    private fun updateSectionVisibility() {
        // Masquer une section si tous ses paramètres sont masqués
        sectionVolEnv.visibility = if (hasVisibleParams(listOf(
            "global_vol_env_delay", "global_vol_env_attack", "global_vol_env_hold",
            "global_vol_env_decay", "global_vol_env_sustain", "global_vol_env_release"
        ))) View.VISIBLE else View.GONE

        sectionModEnv.visibility = if (hasVisibleParams(listOf(
            "global_mod_env_delay", "global_mod_env_attack", "global_mod_env_hold",
            "global_mod_env_decay", "global_mod_env_sustain", "global_mod_env_release",
            "global_mod_env_to_pitch", "global_mod_env_to_filter"
        ))) View.VISIBLE else View.GONE

        sectionVibLfo.visibility = if (hasVisibleParams(listOf(
            "global_vib_lfo_delay", "global_vib_lfo_freq", "global_vib_lfo_to_pitch"
        ))) View.VISIBLE else View.GONE

        sectionModLfo.visibility = if (hasVisibleParams(listOf(
            "global_mod_lfo_delay", "global_mod_lfo_freq", "global_mod_lfo_to_pitch",
            "global_mod_lfo_to_filter", "global_mod_lfo_to_vol"
        ))) View.VISIBLE else View.GONE

        // Tuning est toujours visible car l'atténuation est toujours affichée
        sectionTuning.visibility = View.VISIBLE

        sectionFilter.visibility = if (hasVisibleParams(listOf(
            "global_filter_cutoff", "global_filter_resonance"
        ))) View.VISIBLE else View.GONE

        sectionEffects.visibility = if (hasVisibleParams(listOf(
            "global_chorus", "global_reverb", "global_pan"
        ))) View.VISIBLE else View.GONE
    }

    private fun hasVisibleParams(keys: List<String>): Boolean {
        return keys.any { key ->
            paramRows[key]?.view?.visibility == View.VISIBLE
        }
    }

    // ==================== VOLUME ENVELOPE ====================

    private fun setupVolEnvSection() {
        // All envelope times use timecents: -12000 to +8000
        // Seekbar 0-200 maps to -12000 to +8000 (x100)
        setupParamRow("global_vol_env_delay", R.id.global_vol_env_delay_row, context.getString(R.string.sf2_delay_label), 200, globalVolEnvDelay != 0) { progress ->
            globalVolEnvDelay = timecentsSeekbarToValue(progress)
            formatTimecents(globalVolEnvDelay)
        }
        setupParamRow("global_vol_env_attack", R.id.global_vol_env_attack_row, context.getString(R.string.sf2_attack_label), 200, globalVolEnvAttack != 0) { progress ->
            globalVolEnvAttack = timecentsSeekbarToValue(progress)
            formatTimecents(globalVolEnvAttack)
        }
        setupParamRow("global_vol_env_hold", R.id.global_vol_env_hold_row, context.getString(R.string.sf2_hold_label), 200, globalVolEnvHold != 0) { progress ->
            globalVolEnvHold = timecentsSeekbarToValue(progress)
            formatTimecents(globalVolEnvHold)
        }
        setupParamRow("global_vol_env_decay", R.id.global_vol_env_decay_row, context.getString(R.string.sf2_decay_label), 200, globalVolEnvDecay != 0) { progress ->
            globalVolEnvDecay = timecentsSeekbarToValue(progress)
            formatTimecents(globalVolEnvDecay)
        }
        // Sustain in centibels (0 = full, 1440 = silence), seekbar 0-144 (x10)
        setupParamRow("global_vol_env_sustain", R.id.global_vol_env_sustain_row, context.getString(R.string.sf2_sustain_label), 144, globalVolEnvSustain != 0) { progress ->
            globalVolEnvSustain = progress * 10
            formatSustainCb(globalVolEnvSustain)
        }
        setupParamRow("global_vol_env_release", R.id.global_vol_env_release_row, context.getString(R.string.sf2_release_label), 200, globalVolEnvRelease != 0) { progress ->
            globalVolEnvRelease = timecentsSeekbarToValue(progress)
            formatTimecents(globalVolEnvRelease)
        }
    }

    private fun resetVolEnvSection() {
        globalVolEnvDelay = 0
        globalVolEnvAttack = 0
        globalVolEnvHold = 0
        globalVolEnvDecay = 0
        globalVolEnvSustain = 0
        globalVolEnvRelease = 0

        paramRows["global_vol_env_delay"]?.setProgress(timecentsValueToSeekbar(globalVolEnvDelay))
        paramRows["global_vol_env_attack"]?.setProgress(timecentsValueToSeekbar(globalVolEnvAttack))
        paramRows["global_vol_env_hold"]?.setProgress(timecentsValueToSeekbar(globalVolEnvHold))
        paramRows["global_vol_env_decay"]?.setProgress(timecentsValueToSeekbar(globalVolEnvDecay))
        paramRows["global_vol_env_sustain"]?.setProgress(globalVolEnvSustain / 10)
        paramRows["global_vol_env_release"]?.setProgress(timecentsValueToSeekbar(globalVolEnvRelease))

        showResetToast()
    }

    // ==================== MODULATION ENVELOPE ====================

    private fun setupModEnvSection() {
        setupParamRow("global_mod_env_delay", R.id.global_mod_env_delay_row, context.getString(R.string.sf2_delay_label), 200, globalModEnvDelay != 0) { progress ->
            globalModEnvDelay = timecentsSeekbarToValue(progress)
            formatTimecents(globalModEnvDelay)
        }
        setupParamRow("global_mod_env_attack", R.id.global_mod_env_attack_row, context.getString(R.string.sf2_attack_label), 200, globalModEnvAttack != 0) { progress ->
            globalModEnvAttack = timecentsSeekbarToValue(progress)
            formatTimecents(globalModEnvAttack)
        }
        setupParamRow("global_mod_env_hold", R.id.global_mod_env_hold_row, context.getString(R.string.sf2_hold_label), 200, globalModEnvHold != 0) { progress ->
            globalModEnvHold = timecentsSeekbarToValue(progress)
            formatTimecents(globalModEnvHold)
        }
        setupParamRow("global_mod_env_decay", R.id.global_mod_env_decay_row, context.getString(R.string.sf2_decay_label), 200, globalModEnvDecay != 0) { progress ->
            globalModEnvDecay = timecentsSeekbarToValue(progress)
            formatTimecents(globalModEnvDecay)
        }
        setupParamRow("global_mod_env_sustain", R.id.global_mod_env_sustain_row, context.getString(R.string.sf2_sustain_label), 144, globalModEnvSustain != 0) { progress ->
            globalModEnvSustain = progress * 10
            formatSustainCb(globalModEnvSustain)
        }
        setupParamRow("global_mod_env_release", R.id.global_mod_env_release_row, context.getString(R.string.sf2_release_label), 200, globalModEnvRelease != 0) { progress ->
            globalModEnvRelease = timecentsSeekbarToValue(progress)
            formatTimecents(globalModEnvRelease)
        }
        // Range: -12000 to +12000 cents, seekbar 0-240 (x100)
        setupParamRow("global_mod_env_to_pitch", R.id.global_mod_env_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240, globalModEnvToPitch != 0) { progress ->
            globalModEnvToPitch = (progress - 120) * 100
            formatCents(globalModEnvToPitch)
        }
        setupParamRow("global_mod_env_to_filter", R.id.global_mod_env_to_filter_row, context.getString(R.string.sf2_to_filter_label), 240, globalModEnvToFilterFc != 0) { progress ->
            globalModEnvToFilterFc = (progress - 120) * 100
            formatCents(globalModEnvToFilterFc)
        }
    }

    private fun resetModEnvSection() {
        globalModEnvDelay = 0
        globalModEnvAttack = 0
        globalModEnvHold = 0
        globalModEnvDecay = 0
        globalModEnvSustain = 0
        globalModEnvRelease = 0
        globalModEnvToPitch = 0
        globalModEnvToFilterFc = 0

        paramRows["global_mod_env_delay"]?.setProgress(timecentsValueToSeekbar(globalModEnvDelay))
        paramRows["global_mod_env_attack"]?.setProgress(timecentsValueToSeekbar(globalModEnvAttack))
        paramRows["global_mod_env_hold"]?.setProgress(timecentsValueToSeekbar(globalModEnvHold))
        paramRows["global_mod_env_decay"]?.setProgress(timecentsValueToSeekbar(globalModEnvDecay))
        paramRows["global_mod_env_sustain"]?.setProgress(globalModEnvSustain / 10)
        paramRows["global_mod_env_release"]?.setProgress(timecentsValueToSeekbar(globalModEnvRelease))
        paramRows["global_mod_env_to_pitch"]?.setProgress(globalModEnvToPitch / 100 + 120)
        paramRows["global_mod_env_to_filter"]?.setProgress(globalModEnvToFilterFc / 100 + 120)

        showResetToast()
    }

    // ==================== VIBRATO LFO ====================

    private fun setupVibLfoSection() {
        setupParamRow("global_vib_lfo_delay", R.id.global_vib_lfo_delay_row, context.getString(R.string.sf2_delay_label), 200, globalVibLfoDelay != 0) { progress ->
            globalVibLfoDelay = timecentsSeekbarToValue(progress)
            formatTimecents(globalVibLfoDelay)
        }
        // Freq: -16000 to 4500 cents, map to 0-100 (approx 0.001 Hz to 100 Hz)
        setupParamRow("global_vib_lfo_freq", R.id.global_vib_lfo_freq_row, context.getString(R.string.sf2_lfo_freq_label), 100, globalVibLfoFreq != 0) { progress ->
            globalVibLfoFreq = lfoSeekbarToCents(progress)
            formatLfoFreq(globalVibLfoFreq)
        }
        setupParamRow("global_vib_lfo_to_pitch", R.id.global_vib_lfo_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240, globalVibLfoToPitch != 0) { progress ->
            globalVibLfoToPitch = (progress - 120) * 100
            formatCents(globalVibLfoToPitch)
        }
    }

    private fun resetVibLfoSection() {
        globalVibLfoDelay = 0
        globalVibLfoFreq = 0
        globalVibLfoToPitch = 0

        paramRows["global_vib_lfo_delay"]?.setProgress(timecentsValueToSeekbar(globalVibLfoDelay))
        paramRows["global_vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(globalVibLfoFreq))
        paramRows["global_vib_lfo_to_pitch"]?.setProgress(globalVibLfoToPitch / 100 + 120)

        showResetToast()
    }

    // ==================== MODULATION LFO ====================

    private fun setupModLfoSection() {
        setupParamRow("global_mod_lfo_delay", R.id.global_mod_lfo_delay_row, context.getString(R.string.sf2_delay_label), 200, globalModLfoDelay != 0) { progress ->
            globalModLfoDelay = timecentsSeekbarToValue(progress)
            formatTimecents(globalModLfoDelay)
        }
        setupParamRow("global_mod_lfo_freq", R.id.global_mod_lfo_freq_row, context.getString(R.string.sf2_lfo_freq_label), 100, globalModLfoFreq != 0) { progress ->
            globalModLfoFreq = lfoSeekbarToCents(progress)
            formatLfoFreq(globalModLfoFreq)
        }
        setupParamRow("global_mod_lfo_to_pitch", R.id.global_mod_lfo_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240, globalModLfoToPitch != 0) { progress ->
            globalModLfoToPitch = (progress - 120) * 100
            formatCents(globalModLfoToPitch)
        }
        setupParamRow("global_mod_lfo_to_filter", R.id.global_mod_lfo_to_filter_row, context.getString(R.string.sf2_to_filter_label), 240, globalModLfoToFilterFc != 0) { progress ->
            globalModLfoToFilterFc = (progress - 120) * 100
            formatCents(globalModLfoToFilterFc)
        }
        // Volume: -960 to +960 cB, seekbar 0-192 (x10)
        setupParamRow("global_mod_lfo_to_vol", R.id.global_mod_lfo_to_vol_row, context.getString(R.string.sf2_to_vol_label), 192, globalModLfoToVolume != 0) { progress ->
            globalModLfoToVolume = (progress - 96) * 10
            formatDb(globalModLfoToVolume / 10f)
        }
    }

    private fun resetModLfoSection() {
        globalModLfoDelay = 0
        globalModLfoFreq = 0
        globalModLfoToPitch = 0
        globalModLfoToFilterFc = 0
        globalModLfoToVolume = 0

        paramRows["global_mod_lfo_delay"]?.setProgress(timecentsValueToSeekbar(globalModLfoDelay))
        paramRows["global_mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(globalModLfoFreq))
        paramRows["global_mod_lfo_to_pitch"]?.setProgress(globalModLfoToPitch / 100 + 120)
        paramRows["global_mod_lfo_to_filter"]?.setProgress(globalModLfoToFilterFc / 100 + 120)
        paramRows["global_mod_lfo_to_vol"]?.setProgress(globalModLfoToVolume / 10 + 96)

        showResetToast()
    }

    // ==================== TUNING ====================

    private fun setupTuningSection() {
        // Coarse: -120 to +120 semitones
        setupParamRow("global_coarse_tune", R.id.global_coarse_tune_row, context.getString(R.string.sf2_coarse_tune_label), 240, globalCoarseTune != 0) { progress ->
            globalCoarseTune = progress - 120
            formatSemitones(globalCoarseTune)
        }
        // Fine: -99 to +99 cents
        setupParamRow("global_fine_tune", R.id.global_fine_tune_row, context.getString(R.string.sf2_fine_tune_label), 198, globalFineTune != 0) { progress ->
            globalFineTune = progress - 99
            formatCents(globalFineTune)
        }
        // Gain/Attenuation: 0-480 cB (0 = full volume) - TOUJOURS AFFICHÉ
        setupParamRow("global_gain", R.id.global_gain_row, context.getString(R.string.sf2_gain_label), 480, true) { progress ->
            globalAttenuation = 480 - progress
            formatDb(-globalAttenuation / 10f)
        }
    }

    private fun resetTuningSection() {
        globalCoarseTune = 0
        globalFineTune = 0
        globalAttenuation = 0

        paramRows["global_coarse_tune"]?.setProgress(globalCoarseTune + 120)
        paramRows["global_fine_tune"]?.setProgress(globalFineTune + 99)
        paramRows["global_gain"]?.setProgress(480 - globalAttenuation)

        showResetToast()
    }

    // ==================== FILTER ====================

    private fun setupFilterSection() {
        // Filter cutoff: stored as additive cents (-12000 to +12000)
        // Seekbar 0-240 maps to -12000 to +12000 (x100)
        setupParamRow("global_filter_cutoff", R.id.global_filter_cutoff_row, context.getString(R.string.sf2_filter_cutoff_label), 240, globalFilterFc != 0) { progress ->
            globalFilterFc = (progress - 120) * 100
            formatFilterCents(globalFilterFc)
        }
        setupParamRow("global_filter_resonance", R.id.global_filter_resonance_row, context.getString(R.string.sf2_filter_resonance_label), 400, globalFilterQ != 0) { progress ->
            globalFilterQ = progress
            formatDb(progress / 10f)
        }
    }

    private fun resetFilterSection() {
        globalFilterFc = 0
        globalFilterQ = 0

        paramRows["global_filter_cutoff"]?.setProgress(globalFilterFc / 100 + 120)
        paramRows["global_filter_resonance"]?.setProgress(globalFilterQ)

        showResetToast()
    }

    // ==================== EFFECTS ====================

    private fun setupEffectsSection() {
        setupParamRow("global_chorus", R.id.global_chorus_row, context.getString(R.string.sf2_chorus_send_label), 1000, globalChorusSend != 0) { progress ->
            globalChorusSend = progress
            formatEffectPercent(progress)
        }
        setupParamRow("global_reverb", R.id.global_reverb_row, context.getString(R.string.sf2_reverb_send_label), 1000, globalReverbSend != 0) { progress ->
            globalReverbSend = progress
            formatEffectPercent(progress)
        }
        // Pan: -500 to +500
        setupParamRow("global_pan", R.id.global_pan_row, context.getString(R.string.sf2_pan_label), 1000, globalPan != 0) { progress ->
            globalPan = progress - 500
            formatPan(globalPan)
        }
    }

    private fun resetEffectsSection() {
        globalChorusSend = 0
        globalReverbSend = 0
        globalPan = 0

        paramRows["global_chorus"]?.setProgress(globalChorusSend)
        paramRows["global_reverb"]?.setProgress(globalReverbSend)
        paramRows["global_pan"]?.setProgress(globalPan + 500)

        showResetToast()
    }

    // ==================== SETUP HELPERS ====================

    private fun setupParamRow(
        key: String,
        viewId: Int,
        label: String,
        max: Int,
        shouldShow: Boolean = true,
        onProgress: (Int) -> String
    ) {
        val view = findViewById<View>(viewId) ?: return
        val labelText = view.findViewById<TextView>(R.id.param_label)
        val seekbar = view.findViewById<SeekBar>(R.id.param_seekbar)
        val valueText = view.findViewById<TextView>(R.id.param_value)

        labelText.text = label
        seekbar.max = max

        // Masquer la ligne si le paramètre n'est pas modifié
        view.visibility = if (shouldShow) View.VISIBLE else View.GONE

        val row = ParamRow(view, seekbar, valueText, onProgress)
        paramRows[key] = row

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        cancelButton.setOnClickListener { dismiss() }
        saveButton.setOnClickListener { saveAndDismiss() }
    }

    private fun loadPresetValues() {
        // Volume Envelope
        paramRows["global_vol_env_delay"]?.setProgress(timecentsValueToSeekbar(globalVolEnvDelay))
        paramRows["global_vol_env_attack"]?.setProgress(timecentsValueToSeekbar(globalVolEnvAttack))
        paramRows["global_vol_env_hold"]?.setProgress(timecentsValueToSeekbar(globalVolEnvHold))
        paramRows["global_vol_env_decay"]?.setProgress(timecentsValueToSeekbar(globalVolEnvDecay))
        paramRows["global_vol_env_sustain"]?.setProgress(globalVolEnvSustain / 10)
        paramRows["global_vol_env_release"]?.setProgress(timecentsValueToSeekbar(globalVolEnvRelease))

        // Modulation Envelope
        paramRows["global_mod_env_delay"]?.setProgress(timecentsValueToSeekbar(globalModEnvDelay))
        paramRows["global_mod_env_attack"]?.setProgress(timecentsValueToSeekbar(globalModEnvAttack))
        paramRows["global_mod_env_hold"]?.setProgress(timecentsValueToSeekbar(globalModEnvHold))
        paramRows["global_mod_env_decay"]?.setProgress(timecentsValueToSeekbar(globalModEnvDecay))
        paramRows["global_mod_env_sustain"]?.setProgress(globalModEnvSustain / 10)
        paramRows["global_mod_env_release"]?.setProgress(timecentsValueToSeekbar(globalModEnvRelease))
        paramRows["global_mod_env_to_pitch"]?.setProgress(globalModEnvToPitch / 100 + 120)
        paramRows["global_mod_env_to_filter"]?.setProgress(globalModEnvToFilterFc / 100 + 120)

        // Vibrato LFO
        paramRows["global_vib_lfo_delay"]?.setProgress(timecentsValueToSeekbar(globalVibLfoDelay))
        paramRows["global_vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(globalVibLfoFreq))
        paramRows["global_vib_lfo_to_pitch"]?.setProgress(globalVibLfoToPitch / 100 + 120)

        // Modulation LFO
        paramRows["global_mod_lfo_delay"]?.setProgress(timecentsValueToSeekbar(globalModLfoDelay))
        paramRows["global_mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(globalModLfoFreq))
        paramRows["global_mod_lfo_to_pitch"]?.setProgress(globalModLfoToPitch / 100 + 120)
        paramRows["global_mod_lfo_to_filter"]?.setProgress(globalModLfoToFilterFc / 100 + 120)
        paramRows["global_mod_lfo_to_vol"]?.setProgress(globalModLfoToVolume / 10 + 96)

        // Tuning
        paramRows["global_coarse_tune"]?.setProgress(globalCoarseTune + 120)
        paramRows["global_fine_tune"]?.setProgress(globalFineTune + 99)
        paramRows["global_gain"]?.setProgress(480 - globalAttenuation)

        // Filter
        paramRows["global_filter_cutoff"]?.setProgress(globalFilterFc / 100 + 120)
        paramRows["global_filter_resonance"]?.setProgress(globalFilterQ)

        // Effects
        paramRows["global_chorus"]?.setProgress(globalChorusSend)
        paramRows["global_reverb"]?.setProgress(globalReverbSend)
        paramRows["global_pan"]?.setProgress(globalPan + 500)
    }

    private fun showResetToast() {
        Toast.makeText(context, R.string.sf2_section_reset_toast, Toast.LENGTH_SHORT).show()
    }

    private fun saveAndDismiss() {
        val updatedInstrument = instrument.copy(
            // Volume Envelope
            globalVolEnvDelay = globalVolEnvDelay,
            globalVolEnvAttack = globalVolEnvAttack,
            globalVolEnvHold = globalVolEnvHold,
            globalVolEnvDecay = globalVolEnvDecay,
            globalVolEnvSustain = globalVolEnvSustain,
            globalVolEnvRelease = globalVolEnvRelease,
            // Modulation Envelope
            globalModEnvDelay = globalModEnvDelay,
            globalModEnvAttack = globalModEnvAttack,
            globalModEnvHold = globalModEnvHold,
            globalModEnvDecay = globalModEnvDecay,
            globalModEnvSustain = globalModEnvSustain,
            globalModEnvRelease = globalModEnvRelease,
            globalModEnvToPitch = globalModEnvToPitch,
            globalModEnvToFilterFc = globalModEnvToFilterFc,
            // Vibrato LFO
            globalVibLfoDelay = globalVibLfoDelay,
            globalVibLfoFreq = globalVibLfoFreq,
            globalVibLfoToPitch = globalVibLfoToPitch,
            // Modulation LFO
            globalModLfoDelay = globalModLfoDelay,
            globalModLfoFreq = globalModLfoFreq,
            globalModLfoToPitch = globalModLfoToPitch,
            globalModLfoToFilterFc = globalModLfoToFilterFc,
            globalModLfoToVolume = globalModLfoToVolume,
            // Tuning
            globalCoarseTune = globalCoarseTune,
            globalFineTune = globalFineTune,
            globalAttenuation = globalAttenuation,
            // Filter
            globalFilterFc = globalFilterFc,
            globalFilterQ = globalFilterQ,
            // Effects
            globalChorusSend = globalChorusSend,
            globalReverbSend = globalReverbSend,
            globalPan = globalPan
        )

        onSave(updatedInstrument)
        dismiss()
    }

    // ==================== FORMATTING ====================

    private fun formatTimecents(tc: Int): String {
        // Convert timecents to milliseconds for display
        if (tc <= -12000) return context.getString(R.string.sf2_ms_value, 0)
        if (tc >= 8000) return context.getString(R.string.sf2_ms_value, 30000)
        val seconds = Math.pow(2.0, tc / 1200.0)
        val ms = (seconds * 1000).toInt().coerceIn(0, 30000)
        return context.getString(R.string.sf2_ms_value, ms)
    }

    private fun formatSustainCb(cb: Int): String {
        // Convert centibels to percentage (0 cB = 100%, 1000 cB = 0%)
        val percent = (100 - cb / 10).coerceIn(0, 100)
        return context.getString(R.string.sf2_percent_value, percent)
    }

    private fun formatCents(cents: Int): String = context.getString(R.string.sf2_cents_value, cents)
    private fun formatSemitones(semitones: Int): String = context.getString(R.string.sf2_semitones_value, semitones)
    private fun formatDb(db: Float): String = context.getString(R.string.sf2_gain_value, db)
    private fun formatEffectPercent(value: Int): String = context.getString(R.string.sf2_effects_send_value, value / 10f)

    private fun formatFilterCents(cents: Int): String {
        // Display as additive cents (e.g., "+1200 cents" or "-600 cents")
        return if (cents >= 0) {
            context.getString(R.string.sf2_cents_value, cents)
        } else {
            context.getString(R.string.sf2_cents_value, cents)
        }
    }

    private fun formatLfoFreq(cents: Int): String {
        val hz = 8.176 * Math.pow(2.0, cents / 1200.0)
        return context.getString(R.string.sf2_hz_value, hz.toFloat())
    }

    private fun formatPan(pan: Int): String {
        return when {
            pan < 0 -> context.getString(R.string.sf2_pan_value_left, -pan / 5)
            pan > 0 -> context.getString(R.string.sf2_pan_value_right, pan / 5)
            else -> context.getString(R.string.sf2_pan_value_center)
        }
    }

    // ==================== CONVERSIONS ====================

    /**
     * Convert seekbar position (0-200) to timecents (-12000 to +8000).
     * Position 120 = 0 timecents (neutral), values below are negative, above are positive.
     */
    private fun timecentsSeekbarToValue(progress: Int): Int {
        return (progress - 120) * 100
    }

    /**
     * Convert timecents value to seekbar position.
     */
    private fun timecentsValueToSeekbar(tc: Int): Int {
        return (tc / 100 + 120).coerceIn(0, 200)
    }

    private fun lfoSeekbarToCents(progress: Int): Int {
        // Map 0-100 to approximately -16000 to +4500 cents
        return ((progress - 50) * 200).coerceIn(-16000, 4500)
    }

    private fun lfoCentsToSeekbar(cents: Int): Int {
        return (cents / 200 + 50).coerceIn(0, 100)
    }

    // ==================== HELPER CLASS ====================

    private class ParamRow(
        val view: View,
        private val seekbar: SeekBar,
        private val valueText: TextView,
        private val formatter: (Int) -> String
    ) {
        fun setProgress(progress: Int) {
            seekbar.progress = progress
            valueText.text = formatter(progress)
        }

        fun setVisible(visible: Boolean) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
