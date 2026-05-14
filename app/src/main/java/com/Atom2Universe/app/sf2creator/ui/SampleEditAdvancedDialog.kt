package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.ui.components.ExpandableSectionView
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter

/**
 * Advanced dialog for editing all SF2 sample parameters.
 * Features collapsible sections for organized parameter editing.
 * Compatible with small screens through scroll and compact layout.
 */
class SampleEditAdvancedDialog(
    context: Context,
    private val sample: Sf2SampleEntity,
    private val onSave: (Sf2SampleEntity) -> Unit
) : Dialog(context) {

    // UI elements
    private lateinit var sampleNameInput: EditText
    private lateinit var keyRangeText: TextView
    private lateinit var editKeyRangeButton: Button
    private lateinit var velRangeText: TextView
    private lateinit var editVelRangeButton: Button
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
    private lateinit var sectionAdvanced: ExpandableSectionView

    // Parameter rows - stored for easy access
    private val paramRows = mutableMapOf<String, ParamRow>()

    // All editable values (initialized from sample)
    private var name: String = sample.name
    private var keyRangeStart: Int = sample.keyRangeStart
    private var keyRangeEnd: Int = sample.keyRangeEnd
    private var velRangeStart: Int = sample.velRangeStart
    private var velRangeEnd: Int = sample.velRangeEnd

    // Volume Envelope - UI values in ms/% (converted from SF2 native timecents/centibels)
    private var volEnvDelayMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvDelay)
    private var attackMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvAttack)
    private var volEnvHoldMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvHold)
    private var decayMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvDecay)
    private var sustainPercent: Int = Sf2UnitConverter.centibelsToSustainPercent(sample.volEnvSustain)
    private var releaseMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvRelease)

    // Modulation Envelope - UI values in ms/% (converted from SF2 native)
    private var modEnvDelayMs: Int = Sf2UnitConverter.timecentsToMs(sample.modEnvDelay)
    private var modEnvAttackMs: Int = Sf2UnitConverter.timecentsToMs(sample.modEnvAttack)
    private var modEnvHoldMs: Int = Sf2UnitConverter.timecentsToMs(sample.modEnvHold)
    private var modEnvDecayMs: Int = Sf2UnitConverter.timecentsToMs(sample.modEnvDecay)
    private var modEnvSustainPercent: Int = Sf2UnitConverter.centibelsToSustainPercent(sample.modEnvSustain)
    private var modEnvReleaseMs: Int = Sf2UnitConverter.timecentsToMs(sample.modEnvRelease)
    private var modEnvToPitch: Int = sample.modEnvToPitch
    private var modEnvToFilterFc: Int = sample.modEnvToFilterFc

    // Vibrato LFO - UI values (delay in ms, freq already in cents)
    private var vibLfoDelayMs: Int = Sf2UnitConverter.timecentsToMs(sample.vibLfoDelay)
    private var vibLfoFreqCents: Int = sample.vibLfoFreq
    private var vibLfoToPitch: Int = sample.vibLfoToPitch

    // Modulation LFO - UI values
    private var modLfoDelayMs: Int = Sf2UnitConverter.timecentsToMs(sample.modLfoDelay)
    private var modLfoFreqCents: Int = sample.modLfoFreq
    private var modLfoToPitch: Int = sample.modLfoToPitch
    private var modLfoToFilterFc: Int = sample.modLfoToFilterFc
    private var modLfoToVolume: Int = sample.modLfoToVolume

    // Tuning - these are already in the right units
    private var coarseTune: Int = sample.coarseTune
    private var fineTuneCents: Int = sample.fineTuneCents
    private var scaleTuning: Int = sample.scaleTuning
    private var attenuation: Int = sample.attenuation

    // Filter - UI values (Hz converted from SF2 cents)
    private var filterCutoffHz: Float = Sf2UnitConverter.filterCentsToHz(sample.filterFc)
    private var filterResonanceCb: Int = sample.filterQ

    // Effects
    private var chorusSend: Int = sample.chorusSend
    private var reverbSend: Int = sample.reverbSend
    private var pan: Int = sample.pan
    private var exclusiveClass: Int = sample.exclusiveClass

    // Key-to-envelope scaling
    private var keyToVolEnvHold: Int = sample.keyToVolEnvHold
    private var keyToVolEnvDecay: Int = sample.keyToVolEnvDecay
    private var keyToModEnvHold: Int = sample.keyToModEnvHold
    private var keyToModEnvDecay: Int = sample.keyToModEnvDecay

    // Fixed key/velocity
    private var fixedKey: Int = sample.fixedKey
    private var fixedVelocity: Int = sample.fixedVelocity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_sample_edit_advanced)

        // Make dialog wider and limit height
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.85).toInt()
        )

        findViews()
        setupSections()
        setupButtons()
        loadSampleValues()
    }

    private fun findViews() {
        sampleNameInput = findViewById(R.id.sample_name_input)
        keyRangeText = findViewById(R.id.key_range_text)
        editKeyRangeButton = findViewById(R.id.edit_key_range_button)
        velRangeText = findViewById(R.id.vel_range_text)
        editVelRangeButton = findViewById(R.id.edit_vel_range_button)
        cancelButton = findViewById(R.id.cancel_button)
        saveButton = findViewById(R.id.save_button)

        sectionVolEnv = findViewById(R.id.section_vol_env)
        sectionModEnv = findViewById(R.id.section_mod_env)
        sectionVibLfo = findViewById(R.id.section_vib_lfo)
        sectionModLfo = findViewById(R.id.section_mod_lfo)
        sectionTuning = findViewById(R.id.section_tuning)
        sectionFilter = findViewById(R.id.section_filter)
        sectionEffects = findViewById(R.id.section_effects)
        sectionAdvanced = findViewById(R.id.section_advanced)
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

        // Advanced
        setupAdvancedSection()
        sectionAdvanced.setOnResetClickListener { resetAdvancedSection() }
    }

    // ==================== VOLUME ENVELOPE ====================

    private fun setupVolEnvSection() {
        setupParamRow("vol_env_delay", R.id.vol_env_delay_row, context.getString(R.string.sf2_delay_label), 5000) { progress ->
            volEnvDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("vol_env_attack", R.id.vol_env_attack_row, context.getString(R.string.sf2_attack_label), 5000) { progress ->
            attackMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("vol_env_hold", R.id.vol_env_hold_row, context.getString(R.string.sf2_hold_label), 5000) { progress ->
            volEnvHoldMs = progress
            formatMs(progress)
        }
        setupParamRow("vol_env_decay", R.id.vol_env_decay_row, context.getString(R.string.sf2_decay_label), 5000) { progress ->
            decayMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("vol_env_sustain", R.id.vol_env_sustain_row, context.getString(R.string.sf2_sustain_label), 100) { progress ->
            sustainPercent = progress
            formatPercent(progress)
        }
        setupParamRow("vol_env_release", R.id.vol_env_release_row, context.getString(R.string.sf2_release_label), 5000) { progress ->
            releaseMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        // Key-to-envelope scaling: -1200 to +1200 timecents per key, seekbar 0-240 (x10)
        setupParamRow("key_to_vol_env_hold", R.id.key_to_vol_env_hold_row, context.getString(R.string.sf2_key_to_hold_label), 240) { progress ->
            keyToVolEnvHold = (progress - 120) * 10
            formatTimecentsPerKey(keyToVolEnvHold)
        }
        setupParamRow("key_to_vol_env_decay", R.id.key_to_vol_env_decay_row, context.getString(R.string.sf2_key_to_decay_label), 240) { progress ->
            keyToVolEnvDecay = (progress - 120) * 10
            formatTimecentsPerKey(keyToVolEnvDecay)
        }
    }

    private fun resetVolEnvSection() {
        // Reset to original sample values (convert from SF2 native units to UI units)
        volEnvDelayMs = Sf2UnitConverter.timecentsToMs(sample.volEnvDelay)
        attackMs = Sf2UnitConverter.timecentsToMs(sample.volEnvAttack)
        volEnvHoldMs = Sf2UnitConverter.timecentsToMs(sample.volEnvHold)
        decayMs = Sf2UnitConverter.timecentsToMs(sample.volEnvDecay)
        sustainPercent = Sf2UnitConverter.centibelsToSustainPercent(sample.volEnvSustain)
        releaseMs = Sf2UnitConverter.timecentsToMs(sample.volEnvRelease)
        keyToVolEnvHold = sample.keyToVolEnvHold
        keyToVolEnvDecay = sample.keyToVolEnvDecay

        paramRows["vol_env_delay"]?.setProgress(volEnvDelayMs)
        paramRows["vol_env_attack"]?.setProgress(attackMs)
        paramRows["vol_env_hold"]?.setProgress(volEnvHoldMs)
        paramRows["vol_env_decay"]?.setProgress(decayMs)
        paramRows["vol_env_sustain"]?.setProgress(sustainPercent)
        paramRows["vol_env_release"]?.setProgress(releaseMs)
        paramRows["key_to_vol_env_hold"]?.setProgress(keyToVolEnvHold / 10 + 120)
        paramRows["key_to_vol_env_decay"]?.setProgress(keyToVolEnvDecay / 10 + 120)

        showResetToast()
    }

    // ==================== MODULATION ENVELOPE ====================

    private fun setupModEnvSection() {
        setupParamRow("mod_env_delay", R.id.mod_env_delay_row, context.getString(R.string.sf2_delay_label), 5000) { progress ->
            modEnvDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_env_attack", R.id.mod_env_attack_row, context.getString(R.string.sf2_attack_label), 5000) { progress ->
            modEnvAttackMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("mod_env_hold", R.id.mod_env_hold_row, context.getString(R.string.sf2_hold_label), 5000) { progress ->
            modEnvHoldMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_env_decay", R.id.mod_env_decay_row, context.getString(R.string.sf2_decay_label), 5000) { progress ->
            modEnvDecayMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        setupParamRow("mod_env_sustain", R.id.mod_env_sustain_row, context.getString(R.string.sf2_sustain_label), 100) { progress ->
            modEnvSustainPercent = progress
            formatPercent(progress)
        }
        setupParamRow("mod_env_release", R.id.mod_env_release_row, context.getString(R.string.sf2_release_label), 5000) { progress ->
            modEnvReleaseMs = progress.coerceAtLeast(1)
            formatMs(progress)
        }
        // Range: -12000 to +12000 cents, seekbar 0-240 (x100)
        setupParamRow("mod_env_to_pitch", R.id.mod_env_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240) { progress ->
            modEnvToPitch = (progress - 120) * 100
            formatCents(modEnvToPitch)
        }
        setupParamRow("mod_env_to_filter", R.id.mod_env_to_filter_row, context.getString(R.string.sf2_to_filter_label), 240) { progress ->
            modEnvToFilterFc = (progress - 120) * 100
            formatCents(modEnvToFilterFc)
        }
        // Key-to-envelope scaling: -1200 to +1200 timecents per key, seekbar 0-240 (x10)
        setupParamRow("key_to_mod_env_hold", R.id.key_to_mod_env_hold_row, context.getString(R.string.sf2_key_to_hold_label), 240) { progress ->
            keyToModEnvHold = (progress - 120) * 10
            formatTimecentsPerKey(keyToModEnvHold)
        }
        setupParamRow("key_to_mod_env_decay", R.id.key_to_mod_env_decay_row, context.getString(R.string.sf2_key_to_decay_label), 240) { progress ->
            keyToModEnvDecay = (progress - 120) * 10
            formatTimecentsPerKey(keyToModEnvDecay)
        }
    }

    private fun resetModEnvSection() {
        // Reset to original sample values (convert from SF2 native units to UI units)
        modEnvDelayMs = Sf2UnitConverter.timecentsToMs(sample.modEnvDelay)
        modEnvAttackMs = Sf2UnitConverter.timecentsToMs(sample.modEnvAttack)
        modEnvHoldMs = Sf2UnitConverter.timecentsToMs(sample.modEnvHold)
        modEnvDecayMs = Sf2UnitConverter.timecentsToMs(sample.modEnvDecay)
        modEnvSustainPercent = Sf2UnitConverter.centibelsToSustainPercent(sample.modEnvSustain)
        modEnvReleaseMs = Sf2UnitConverter.timecentsToMs(sample.modEnvRelease)
        modEnvToPitch = sample.modEnvToPitch
        modEnvToFilterFc = sample.modEnvToFilterFc
        keyToModEnvHold = sample.keyToModEnvHold
        keyToModEnvDecay = sample.keyToModEnvDecay

        paramRows["mod_env_delay"]?.setProgress(modEnvDelayMs)
        paramRows["mod_env_attack"]?.setProgress(modEnvAttackMs)
        paramRows["mod_env_hold"]?.setProgress(modEnvHoldMs)
        paramRows["mod_env_decay"]?.setProgress(modEnvDecayMs)
        paramRows["mod_env_sustain"]?.setProgress(modEnvSustainPercent)
        paramRows["mod_env_release"]?.setProgress(modEnvReleaseMs)
        paramRows["mod_env_to_pitch"]?.setProgress(modEnvToPitch / 100 + 120)
        paramRows["mod_env_to_filter"]?.setProgress(modEnvToFilterFc / 100 + 120)
        paramRows["key_to_mod_env_hold"]?.setProgress(keyToModEnvHold / 10 + 120)
        paramRows["key_to_mod_env_decay"]?.setProgress(keyToModEnvDecay / 10 + 120)

        showResetToast()
    }

    // ==================== VIBRATO LFO ====================

    private fun setupVibLfoSection() {
        setupParamRow("vib_lfo_delay", R.id.vib_lfo_delay_row, context.getString(R.string.sf2_delay_label), 5000) { progress ->
            vibLfoDelayMs = progress
            formatMs(progress)
        }
        // Freq: -16000 to 4500 cents, map to 0-100 (approx 0.001 Hz to 100 Hz)
        setupParamRow("vib_lfo_freq", R.id.vib_lfo_freq_row, context.getString(R.string.sf2_lfo_freq_label), 100) { progress ->
            vibLfoFreqCents = lfoSeekbarToCents(progress)
            formatLfoFreq(vibLfoFreqCents)
        }
        setupParamRow("vib_lfo_to_pitch", R.id.vib_lfo_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240) { progress ->
            vibLfoToPitch = (progress - 120) * 100
            formatCents(vibLfoToPitch)
        }
    }

    private fun resetVibLfoSection() {
        // Reset to original sample values (convert from SF2 native units to UI units)
        vibLfoDelayMs = Sf2UnitConverter.timecentsToMs(sample.vibLfoDelay)
        vibLfoFreqCents = sample.vibLfoFreq
        vibLfoToPitch = sample.vibLfoToPitch

        paramRows["vib_lfo_delay"]?.setProgress(vibLfoDelayMs)
        paramRows["vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(vibLfoFreqCents))
        paramRows["vib_lfo_to_pitch"]?.setProgress(vibLfoToPitch / 100 + 120)

        showResetToast()
    }

    // ==================== MODULATION LFO ====================

    private fun setupModLfoSection() {
        setupParamRow("mod_lfo_delay", R.id.mod_lfo_delay_row, context.getString(R.string.sf2_delay_label), 5000) { progress ->
            modLfoDelayMs = progress
            formatMs(progress)
        }
        setupParamRow("mod_lfo_freq", R.id.mod_lfo_freq_row, context.getString(R.string.sf2_lfo_freq_label), 100) { progress ->
            modLfoFreqCents = lfoSeekbarToCents(progress)
            formatLfoFreq(modLfoFreqCents)
        }
        setupParamRow("mod_lfo_to_pitch", R.id.mod_lfo_to_pitch_row, context.getString(R.string.sf2_to_pitch_label), 240) { progress ->
            modLfoToPitch = (progress - 120) * 100
            formatCents(modLfoToPitch)
        }
        setupParamRow("mod_lfo_to_filter", R.id.mod_lfo_to_filter_row, context.getString(R.string.sf2_to_filter_label), 240) { progress ->
            modLfoToFilterFc = (progress - 120) * 100
            formatCents(modLfoToFilterFc)
        }
        // Volume: -960 to +960 cB, seekbar 0-192 (x10)
        setupParamRow("mod_lfo_to_vol", R.id.mod_lfo_to_vol_row, context.getString(R.string.sf2_to_vol_label), 192) { progress ->
            modLfoToVolume = (progress - 96) * 10
            formatDb(modLfoToVolume / 10f)
        }
    }

    private fun resetModLfoSection() {
        // Reset to original sample values (convert from SF2 native units to UI units)
        modLfoDelayMs = Sf2UnitConverter.timecentsToMs(sample.modLfoDelay)
        modLfoFreqCents = sample.modLfoFreq
        modLfoToPitch = sample.modLfoToPitch
        modLfoToFilterFc = sample.modLfoToFilterFc
        modLfoToVolume = sample.modLfoToVolume

        paramRows["mod_lfo_delay"]?.setProgress(modLfoDelayMs)
        paramRows["mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(modLfoFreqCents))
        paramRows["mod_lfo_to_pitch"]?.setProgress(modLfoToPitch / 100 + 120)
        paramRows["mod_lfo_to_filter"]?.setProgress(modLfoToFilterFc / 100 + 120)
        paramRows["mod_lfo_to_vol"]?.setProgress(modLfoToVolume / 10 + 96)

        showResetToast()
    }

    // ==================== TUNING ====================

    private fun setupTuningSection() {
        // Coarse: -120 to +120 semitones
        setupParamRow("coarse_tune", R.id.coarse_tune_row, context.getString(R.string.sf2_coarse_tune_label), 240) { progress ->
            coarseTune = progress - 120
            formatSemitones(coarseTune)
        }
        // Fine: -99 to +99 cents
        setupParamRow("fine_tune", R.id.fine_tune_row, context.getString(R.string.sf2_fine_tune_label), 198) { progress ->
            fineTuneCents = progress - 99
            formatCents(fineTuneCents)
        }
        // Scale: 0-200 (representing 0-200%, normal is 100)
        setupParamRow("scale_tune", R.id.scale_tune_row, context.getString(R.string.sf2_scale_tune_label), 200) { progress ->
            scaleTuning = progress
            formatScalePercent(progress)
        }
        // Gain/Attenuation: 0-480 cB (0 = full volume)
        setupParamRow("gain", R.id.gain_row, context.getString(R.string.sf2_gain_label), 480) { progress ->
            attenuation = 480 - progress
            formatDb(-attenuation / 10f)
        }
    }

    private fun resetTuningSection() {
        // Reset to original sample values, not SF2 defaults
        coarseTune = sample.coarseTune
        fineTuneCents = sample.fineTuneCents
        scaleTuning = sample.scaleTuning
        attenuation = sample.attenuation

        paramRows["coarse_tune"]?.setProgress(coarseTune + 120)
        paramRows["fine_tune"]?.setProgress(fineTuneCents + 99)
        paramRows["scale_tune"]?.setProgress(scaleTuning)
        paramRows["gain"]?.setProgress(480 - attenuation)

        showResetToast()
    }

    // ==================== FILTER ====================

    private fun setupFilterSection() {
        setupParamRow("filter_cutoff", R.id.filter_cutoff_row, context.getString(R.string.sf2_filter_cutoff_label), 1000) { progress ->
            filterCutoffHz = seekbarToCutoffHz(progress)
            formatCutoffHz(filterCutoffHz)
        }
        setupParamRow("filter_resonance", R.id.filter_resonance_row, context.getString(R.string.sf2_filter_resonance_label), 400) { progress ->
            filterResonanceCb = progress
            formatDb(progress / 10f)
        }
    }

    private fun resetFilterSection() {
        // Reset to original sample values (convert from SF2 native units to UI units)
        filterCutoffHz = Sf2UnitConverter.filterCentsToHz(sample.filterFc)
        filterResonanceCb = sample.filterQ

        paramRows["filter_cutoff"]?.setProgress(cutoffHzToSeekbar(filterCutoffHz))
        paramRows["filter_resonance"]?.setProgress(filterResonanceCb)

        showResetToast()
    }

    // ==================== EFFECTS ====================

    private fun setupEffectsSection() {
        setupParamRow("chorus", R.id.chorus_row, context.getString(R.string.sf2_chorus_send_label), 1000) { progress ->
            chorusSend = progress
            formatEffectPercent(progress)
        }
        setupParamRow("reverb", R.id.reverb_row, context.getString(R.string.sf2_reverb_send_label), 1000) { progress ->
            reverbSend = progress
            formatEffectPercent(progress)
        }
        // Pan: -500 to +500
        setupParamRow("pan", R.id.pan_row, context.getString(R.string.sf2_pan_label), 1000) { progress ->
            pan = progress - 500
            formatPan(pan)
        }
        // Exclusive class: 0-127
        setupParamRow("exclusive_class", R.id.exclusive_class_row, context.getString(R.string.sf2_exclusive_class_label), 127) { progress ->
            exclusiveClass = progress
            formatExclusiveClass(progress)
        }
    }

    private fun resetEffectsSection() {
        // Reset to original sample values, not SF2 defaults
        chorusSend = sample.chorusSend
        reverbSend = sample.reverbSend
        pan = sample.pan
        exclusiveClass = sample.exclusiveClass

        paramRows["chorus"]?.setProgress(chorusSend)
        paramRows["reverb"]?.setProgress(reverbSend)
        paramRows["pan"]?.setProgress(pan + 500)
        paramRows["exclusive_class"]?.setProgress(exclusiveClass)

        showResetToast()
    }

    // ==================== ADVANCED ====================

    private fun setupAdvancedSection() {
        // Fixed Key: -1 (off) to 127, seekbar 0-128 (0 = off)
        setupParamRow("fixed_key", R.id.fixed_key_row, context.getString(R.string.sf2_fixed_key_label), 128) { progress ->
            fixedKey = if (progress == 0) -1 else progress - 1
            formatFixedValue(fixedKey, true)
        }
        // Fixed Velocity: -1 (off) to 127, seekbar 0-128 (0 = off)
        setupParamRow("fixed_velocity", R.id.fixed_velocity_row, context.getString(R.string.sf2_fixed_velocity_label), 128) { progress ->
            fixedVelocity = if (progress == 0) -1 else progress - 1
            formatFixedValue(fixedVelocity, false)
        }
    }

    private fun resetAdvancedSection() {
        // Reset to original sample values, not SF2 defaults
        fixedKey = sample.fixedKey
        fixedVelocity = sample.fixedVelocity

        paramRows["fixed_key"]?.setProgress(if (fixedKey < 0) 0 else fixedKey + 1)
        paramRows["fixed_velocity"]?.setProgress(if (fixedVelocity < 0) 0 else fixedVelocity + 1)

        showResetToast()
    }

    // ==================== SETUP HELPERS ====================

    private fun setupParamRow(
        key: String,
        viewId: Int,
        label: String,
        max: Int,
        onProgress: (Int) -> String
    ) {
        val view = findViewById<View>(viewId) ?: return
        val labelText = view.findViewById<TextView>(R.id.param_label)
        val seekbar = view.findViewById<SeekBar>(R.id.param_seekbar)
        val valueText = view.findViewById<TextView>(R.id.param_value)

        labelText.text = label
        seekbar.max = max

        val row = ParamRow(seekbar, valueText, onProgress)
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
        editKeyRangeButton.setOnClickListener { showKeyRangeDialog() }
        editVelRangeButton.setOnClickListener { showVelRangeDialog() }
        cancelButton.setOnClickListener { dismiss() }
        saveButton.setOnClickListener { saveAndDismiss() }
    }

    private fun showKeyRangeDialog() {
        KeyRangeDialog(
            context = context,
            rootNote = sample.rootNote,
            currentStart = keyRangeStart,
            currentEnd = keyRangeEnd,
            onRangeSelected = { start, end ->
                keyRangeStart = start
                keyRangeEnd = end
                updateKeyRangeDisplay()
            }
        ).show()
    }

    private fun showVelRangeDialog() {
        VelocityRangeDialog(
            context = context,
            currentStart = velRangeStart,
            currentEnd = velRangeEnd,
            onRangeSelected = { start, end ->
                velRangeStart = start
                velRangeEnd = end
                updateVelRangeDisplay()
            }
        ).show()
    }

    private fun loadSampleValues() {
        sampleNameInput.setText(sample.name)
        updateKeyRangeDisplay()
        updateVelRangeDisplay()

        // Volume Envelope
        paramRows["vol_env_delay"]?.setProgress(volEnvDelayMs)
        paramRows["vol_env_attack"]?.setProgress(attackMs)
        paramRows["vol_env_hold"]?.setProgress(volEnvHoldMs)
        paramRows["vol_env_decay"]?.setProgress(decayMs)
        paramRows["vol_env_sustain"]?.setProgress(sustainPercent)
        paramRows["vol_env_release"]?.setProgress(releaseMs)
        paramRows["key_to_vol_env_hold"]?.setProgress(keyToVolEnvHold / 10 + 120)
        paramRows["key_to_vol_env_decay"]?.setProgress(keyToVolEnvDecay / 10 + 120)

        // Modulation Envelope
        paramRows["mod_env_delay"]?.setProgress(modEnvDelayMs)
        paramRows["mod_env_attack"]?.setProgress(modEnvAttackMs)
        paramRows["mod_env_hold"]?.setProgress(modEnvHoldMs)
        paramRows["mod_env_decay"]?.setProgress(modEnvDecayMs)
        paramRows["mod_env_sustain"]?.setProgress(modEnvSustainPercent)
        paramRows["mod_env_release"]?.setProgress(modEnvReleaseMs)
        paramRows["mod_env_to_pitch"]?.setProgress(modEnvToPitch / 100 + 120)
        paramRows["mod_env_to_filter"]?.setProgress(modEnvToFilterFc / 100 + 120)
        paramRows["key_to_mod_env_hold"]?.setProgress(keyToModEnvHold / 10 + 120)
        paramRows["key_to_mod_env_decay"]?.setProgress(keyToModEnvDecay / 10 + 120)

        // Vibrato LFO
        paramRows["vib_lfo_delay"]?.setProgress(vibLfoDelayMs)
        paramRows["vib_lfo_freq"]?.setProgress(lfoCentsToSeekbar(vibLfoFreqCents))
        paramRows["vib_lfo_to_pitch"]?.setProgress(vibLfoToPitch / 100 + 120)

        // Modulation LFO
        paramRows["mod_lfo_delay"]?.setProgress(modLfoDelayMs)
        paramRows["mod_lfo_freq"]?.setProgress(lfoCentsToSeekbar(modLfoFreqCents))
        paramRows["mod_lfo_to_pitch"]?.setProgress(modLfoToPitch / 100 + 120)
        paramRows["mod_lfo_to_filter"]?.setProgress(modLfoToFilterFc / 100 + 120)
        paramRows["mod_lfo_to_vol"]?.setProgress(modLfoToVolume / 10 + 96)

        // Tuning
        paramRows["coarse_tune"]?.setProgress(coarseTune + 120)
        paramRows["fine_tune"]?.setProgress(fineTuneCents + 99)
        paramRows["scale_tune"]?.setProgress(scaleTuning)
        paramRows["gain"]?.setProgress(480 - attenuation)

        // Filter
        paramRows["filter_cutoff"]?.setProgress(cutoffHzToSeekbar(filterCutoffHz))
        paramRows["filter_resonance"]?.setProgress(filterResonanceCb)

        // Effects
        paramRows["chorus"]?.setProgress(chorusSend)
        paramRows["reverb"]?.setProgress(reverbSend)
        paramRows["pan"]?.setProgress(pan + 500)
        paramRows["exclusive_class"]?.setProgress(exclusiveClass)

        // Advanced
        paramRows["fixed_key"]?.setProgress(if (fixedKey < 0) 0 else fixedKey + 1)
        paramRows["fixed_velocity"]?.setProgress(if (fixedVelocity < 0) 0 else fixedVelocity + 1)
    }

    private fun updateKeyRangeDisplay() {
        val startName = SampleData.midiNoteToName(keyRangeStart)
        val endName = SampleData.midiNoteToName(keyRangeEnd)
        keyRangeText.text = if (keyRangeStart == keyRangeEnd) {
            startName
        } else {
            context.getString(R.string.sf2_key_range_label, startName, endName)
        }
    }

    private fun updateVelRangeDisplay() {
        velRangeText.text = context.getString(R.string.sf2_vel_range_label, velRangeStart, velRangeEnd)
    }

    private fun showResetToast() {
        Toast.makeText(context, R.string.sf2_section_reset_toast, Toast.LENGTH_SHORT).show()
    }

    private fun saveAndDismiss() {
        name = sampleNameInput.text.toString().trim().ifEmpty { sample.name }

        // Convert UI values (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
        val updatedSample = sample.copy(
            name = name,
            keyRangeStart = keyRangeStart,
            keyRangeEnd = keyRangeEnd,
            velRangeStart = velRangeStart,
            velRangeEnd = velRangeEnd,
            // Volume Envelope - convert ms to timecents, % to centibels
            volEnvDelay = Sf2UnitConverter.msToTimecents(volEnvDelayMs),
            volEnvAttack = Sf2UnitConverter.msToTimecents(attackMs),
            volEnvHold = Sf2UnitConverter.msToTimecents(volEnvHoldMs),
            volEnvDecay = Sf2UnitConverter.msToTimecents(decayMs),
            volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(sustainPercent),
            volEnvRelease = Sf2UnitConverter.msToTimecents(releaseMs),
            // Modulation Envelope - convert ms to timecents, % to centibels
            modEnvDelay = Sf2UnitConverter.msToTimecents(modEnvDelayMs),
            modEnvAttack = Sf2UnitConverter.msToTimecents(modEnvAttackMs),
            modEnvHold = Sf2UnitConverter.msToTimecents(modEnvHoldMs),
            modEnvDecay = Sf2UnitConverter.msToTimecents(modEnvDecayMs),
            modEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(modEnvSustainPercent),
            modEnvRelease = Sf2UnitConverter.msToTimecents(modEnvReleaseMs),
            modEnvToPitch = modEnvToPitch,
            modEnvToFilterFc = modEnvToFilterFc,
            // Vibrato LFO - convert ms to timecents
            vibLfoDelay = Sf2UnitConverter.msToTimecents(vibLfoDelayMs),
            vibLfoFreq = vibLfoFreqCents,
            vibLfoToPitch = vibLfoToPitch,
            // Modulation LFO - convert ms to timecents
            modLfoDelay = Sf2UnitConverter.msToTimecents(modLfoDelayMs),
            modLfoFreq = modLfoFreqCents,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilterFc = modLfoToFilterFc,
            modLfoToVolume = modLfoToVolume,
            // Tuning - already in correct units
            coarseTune = coarseTune,
            fineTuneCents = fineTuneCents,
            scaleTuning = scaleTuning,
            attenuation = attenuation,
            // Filter - convert Hz to cents
            filterFc = Sf2UnitConverter.hzToFilterCents(filterCutoffHz),
            filterQ = filterResonanceCb,
            // Effects
            chorusSend = chorusSend,
            reverbSend = reverbSend,
            pan = pan,
            exclusiveClass = exclusiveClass,
            // Key-to-envelope scaling
            keyToVolEnvHold = keyToVolEnvHold,
            keyToVolEnvDecay = keyToVolEnvDecay,
            keyToModEnvHold = keyToModEnvHold,
            keyToModEnvDecay = keyToModEnvDecay,
            // Fixed key/velocity
            fixedKey = fixedKey,
            fixedVelocity = fixedVelocity
        )

        onSave(updatedSample)
        dismiss()
    }

    // ==================== FORMATTING ====================

    private fun formatMs(ms: Int): String = context.getString(R.string.sf2_ms_value, ms)
    private fun formatPercent(percent: Int): String = context.getString(R.string.sf2_percent_value, percent)
    private fun formatCents(cents: Int): String = context.getString(R.string.sf2_cents_value, cents)
    private fun formatSemitones(semitones: Int): String = context.getString(R.string.sf2_semitones_value, semitones)
    private fun formatDb(db: Float): String = context.getString(R.string.sf2_gain_value, db)
    private fun formatScalePercent(percent: Int): String = context.getString(R.string.sf2_scale_value, percent)
    private fun formatEffectPercent(value: Int): String = context.getString(R.string.sf2_effects_send_value, value / 10f)

    private fun formatCutoffHz(hz: Float): String {
        return if (hz >= 1000f) {
            context.getString(R.string.sf2_filter_cutoff_value_khz, hz / 1000f)
        } else {
            context.getString(R.string.sf2_filter_cutoff_value_hz, hz.toInt())
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

    private fun formatExclusiveClass(classId: Int): String {
        return if (classId == 0) {
            context.getString(R.string.sf2_class_none)
        } else {
            context.getString(R.string.sf2_class_value, classId)
        }
    }

    private fun formatTimecentsPerKey(tc: Int): String {
        // Format timecents per key as a simple value
        return if (tc == 0) "0" else "%+d".format(tc)
    }

    private fun formatFixedValue(value: Int, isKey: Boolean): String {
        return if (value < 0) {
            context.getString(R.string.sf2_fixed_off)
        } else if (isKey) {
            SampleData.midiNoteToName(value)
        } else {
            value.toString()
        }
    }

    // ==================== CONVERSIONS ====================

    private fun seekbarToCutoffHz(progress: Int): Float {
        return (200.0 * Math.pow(100.0, progress / 1000.0)).toFloat()
    }

    private fun cutoffHzToSeekbar(hz: Float): Int {
        return (1000.0 * Math.log10(hz.toDouble() / 200.0) / Math.log10(100.0)).toInt().coerceIn(0, 1000)
    }

    private fun lfoSeekbarToCents(progress: Int): Int {
        // Map 0-100 to approximately -16000 to +4500 cents
        // Using a simplified linear mapping for usability
        return ((progress - 50) * 200).coerceIn(-16000, 4500)
    }

    private fun lfoCentsToSeekbar(cents: Int): Int {
        return (cents / 200 + 50).coerceIn(0, 100)
    }

    // ==================== HELPER CLASS ====================

    private class ParamRow(
        private val seekbar: SeekBar,
        private val valueText: TextView,
        private val formatter: (Int) -> String
    ) {
        fun setProgress(progress: Int) {
            seekbar.progress = progress
            valueText.text = formatter(progress)
        }
    }
}
