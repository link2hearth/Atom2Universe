package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter

/**
 * Dialog for editing an existing sample in a project.
 * Allows modifying: name, key range, ADSR, filters, effects, pan.
 */
class SampleEditDialog(
    context: Context,
    private val sample: Sf2SampleEntity,
    private val onSave: (Sf2SampleEntity) -> Unit
) : Dialog(context) {

    // UI elements
    private lateinit var sampleNameInput: EditText
    private lateinit var keyRangeText: TextView
    private lateinit var editKeyRangeButton: Button
    private lateinit var gainSeekbar: SeekBar
    private lateinit var gainValueText: TextView
    private lateinit var attackSeekbar: SeekBar
    private lateinit var attackValueText: TextView
    private lateinit var decaySeekbar: SeekBar
    private lateinit var decayValueText: TextView
    private lateinit var sustainSeekbar: SeekBar
    private lateinit var sustainValueText: TextView
    private lateinit var releaseSeekbar: SeekBar
    private lateinit var releaseValueText: TextView
    private lateinit var fineTuneSeekbar: SeekBar
    private lateinit var fineTuneValueText: TextView
    private lateinit var filterCutoffSeekbar: SeekBar
    private lateinit var filterCutoffValueText: TextView
    private lateinit var filterResonanceSeekbar: SeekBar
    private lateinit var filterResonanceValueText: TextView
    private lateinit var chorusSeekbar: SeekBar
    private lateinit var chorusValueText: TextView
    private lateinit var reverbSeekbar: SeekBar
    private lateinit var reverbValueText: TextView
    private lateinit var panSeekbar: SeekBar
    private lateinit var panValueText: TextView
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    // Editable values (initialized from sample - converted from SF2 native to UI units)
    private var name: String = sample.name
    private var keyRangeStart: Int = sample.keyRangeStart
    private var keyRangeEnd: Int = sample.keyRangeEnd
    private var attenuation: Int = sample.attenuation
    // Volume Envelope - convert timecents to ms, centibels to %
    private var attackMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvAttack)
    private var decayMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvDecay)
    private var sustainPercent: Int = Sf2UnitConverter.centibelsToSustainPercent(sample.volEnvSustain)
    private var releaseMs: Int = Sf2UnitConverter.timecentsToMs(sample.volEnvRelease)
    private var fineTuneCents: Int = sample.fineTuneCents
    // Filter - convert cents to Hz
    private var filterCutoffHz: Float = Sf2UnitConverter.filterCentsToHz(sample.filterFc)
    private var filterResonanceCb: Int = sample.filterQ
    private var chorusSend: Int = sample.chorusSend
    private var reverbSend: Int = sample.reverbSend
    private var pan: Int = sample.pan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_sample_edit)

        // Make dialog wider
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViews()
        setupSliders()
        setupButtons()
        loadSampleValues()
    }

    private fun findViews() {
        sampleNameInput = findViewById(R.id.sample_name_input)
        keyRangeText = findViewById(R.id.key_range_text)
        editKeyRangeButton = findViewById(R.id.edit_key_range_button)
        gainSeekbar = findViewById(R.id.gain_seekbar)
        gainValueText = findViewById(R.id.gain_value_text)
        attackSeekbar = findViewById(R.id.attack_seekbar)
        attackValueText = findViewById(R.id.attack_value_text)
        decaySeekbar = findViewById(R.id.decay_seekbar)
        decayValueText = findViewById(R.id.decay_value_text)
        sustainSeekbar = findViewById(R.id.sustain_seekbar)
        sustainValueText = findViewById(R.id.sustain_value_text)
        releaseSeekbar = findViewById(R.id.release_seekbar)
        releaseValueText = findViewById(R.id.release_value_text)
        fineTuneSeekbar = findViewById(R.id.fine_tune_seekbar)
        fineTuneValueText = findViewById(R.id.fine_tune_value_text)
        filterCutoffSeekbar = findViewById(R.id.filter_cutoff_seekbar)
        filterCutoffValueText = findViewById(R.id.filter_cutoff_value_text)
        filterResonanceSeekbar = findViewById(R.id.filter_resonance_seekbar)
        filterResonanceValueText = findViewById(R.id.filter_resonance_value_text)
        chorusSeekbar = findViewById(R.id.chorus_seekbar)
        chorusValueText = findViewById(R.id.chorus_value_text)
        reverbSeekbar = findViewById(R.id.reverb_seekbar)
        reverbValueText = findViewById(R.id.reverb_value_text)
        panSeekbar = findViewById(R.id.pan_seekbar)
        panValueText = findViewById(R.id.pan_value_text)
        cancelButton = findViewById(R.id.cancel_button)
        saveButton = findViewById(R.id.save_button)
    }

    private fun setupSliders() {
        // Gain slider
        gainSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            attenuation = 480 - progress
            updateGainDisplay()
        })

        // Attack
        attackSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            attackMs = progress.coerceAtLeast(1)
            updateAttackDisplay()
        })

        // Decay
        decaySeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            decayMs = progress.coerceAtLeast(1)
            updateDecayDisplay()
        })

        // Sustain
        sustainSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            sustainPercent = progress
            updateSustainDisplay()
        })

        // Release
        releaseSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            releaseMs = progress.coerceAtLeast(1)
            updateReleaseDisplay()
        })

        // Fine tune
        fineTuneSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            fineTuneCents = progress - 99
            updateFineTuneDisplay()
        })

        // Filter cutoff
        filterCutoffSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            filterCutoffHz = seekbarToCutoffHz(progress)
            updateFilterCutoffDisplay()
        })

        // Filter resonance
        filterResonanceSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            filterResonanceCb = progress
            updateFilterResonanceDisplay()
        })

        // Chorus
        chorusSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            chorusSend = progress
            updateChorusDisplay()
        })

        // Reverb
        reverbSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            reverbSend = progress
            updateReverbDisplay()
        })

        // Pan
        panSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            pan = progress - 500
            updatePanDisplay()
        })
    }

    private fun simpleSeekBarListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun setupButtons() {
        editKeyRangeButton.setOnClickListener {
            showKeyRangeDialog()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        saveButton.setOnClickListener {
            saveAndDismiss()
        }
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

    private fun loadSampleValues() {
        // Name
        sampleNameInput.setText(sample.name)

        // Key range
        updateKeyRangeDisplay()

        // Gain (attenuation: 0 = full, 480 = -48dB)
        gainSeekbar.progress = 480 - attenuation
        updateGainDisplay()

        // ADSR
        attackSeekbar.progress = attackMs
        decaySeekbar.progress = decayMs
        sustainSeekbar.progress = sustainPercent
        releaseSeekbar.progress = releaseMs
        updateAttackDisplay()
        updateDecayDisplay()
        updateSustainDisplay()
        updateReleaseDisplay()

        // Fine tune
        fineTuneSeekbar.progress = fineTuneCents + 99
        updateFineTuneDisplay()

        // Filter
        filterCutoffSeekbar.progress = cutoffHzToSeekbar(filterCutoffHz)
        filterResonanceSeekbar.progress = filterResonanceCb
        updateFilterCutoffDisplay()
        updateFilterResonanceDisplay()

        // Effects
        chorusSeekbar.progress = chorusSend
        reverbSeekbar.progress = reverbSend
        updateChorusDisplay()
        updateReverbDisplay()

        // Pan
        panSeekbar.progress = pan + 500
        updatePanDisplay()
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

    private fun updateGainDisplay() {
        val db = -attenuation / 10f
        gainValueText.text = context.getString(R.string.sf2_gain_value, db)
    }

    private fun updateAttackDisplay() {
        attackValueText.text = context.getString(R.string.sf2_ms_value, attackMs)
    }

    private fun updateDecayDisplay() {
        decayValueText.text = context.getString(R.string.sf2_ms_value, decayMs)
    }

    private fun updateSustainDisplay() {
        sustainValueText.text = context.getString(R.string.sf2_percent_value, sustainPercent)
    }

    private fun updateReleaseDisplay() {
        releaseValueText.text = context.getString(R.string.sf2_ms_value, releaseMs)
    }

    private fun updateFineTuneDisplay() {
        fineTuneValueText.text = context.getString(R.string.sf2_cents_value, fineTuneCents)
    }

    private fun updateFilterCutoffDisplay() {
        if (filterCutoffHz >= 1000f) {
            filterCutoffValueText.text = context.getString(R.string.sf2_filter_cutoff_value_khz, filterCutoffHz / 1000f)
        } else {
            filterCutoffValueText.text = context.getString(R.string.sf2_filter_cutoff_value_hz, filterCutoffHz.toInt())
        }
    }

    private fun updateFilterResonanceDisplay() {
        val resonanceDb = filterResonanceCb / 10f
        filterResonanceValueText.text = context.getString(R.string.sf2_filter_resonance_value, resonanceDb)
    }

    private fun updateChorusDisplay() {
        val chorusPercent = chorusSend / 10f
        chorusValueText.text = context.getString(R.string.sf2_effects_send_value, chorusPercent)
    }

    private fun updateReverbDisplay() {
        val reverbPercent = reverbSend / 10f
        reverbValueText.text = context.getString(R.string.sf2_effects_send_value, reverbPercent)
    }

    private fun updatePanDisplay() {
        panValueText.text = when {
            pan < 0 -> context.getString(R.string.sf2_pan_value_left, -pan / 5)
            pan > 0 -> context.getString(R.string.sf2_pan_value_right, pan / 5)
            else -> context.getString(R.string.sf2_pan_value_center)
        }
    }

    private fun seekbarToCutoffHz(progress: Int): Float {
        // Logarithmic scale: 200 Hz at 0, 20000 Hz at 1000
        return (200.0 * Math.pow(100.0, progress / 1000.0)).toFloat()
    }

    private fun cutoffHzToSeekbar(hz: Float): Int {
        // Reverse of seekbarToCutoffHz
        // progress = 1000 * log100(hz / 200)
        return (1000.0 * Math.log10(hz.toDouble() / 200.0) / Math.log10(100.0)).toInt().coerceIn(0, 1000)
    }

    private fun saveAndDismiss() {
        name = sampleNameInput.text.toString().trim().ifEmpty { sample.name }

        // Convert UI values (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
        val updatedSample = sample.copy(
            name = name,
            keyRangeStart = keyRangeStart,
            keyRangeEnd = keyRangeEnd,
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

        onSave(updatedSample)
        dismiss()
    }
}
