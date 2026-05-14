package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.service.MidiAudioMixer
import com.Atom2Universe.app.midi.sf2.MidiEqualizerEngine
import com.Atom2Universe.app.midi.sf2.Sf2Voice
import com.Atom2Universe.app.midi.sf2.VelocityCurve
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

/**
 * Dialog pour configurer les paramètres audio du lecteur MIDI
 *
 * - Preset de normalisation (Off, Light, Medium, Strong, Aggressive)
 * - Master gain (slider)
 * - Preset de reverb
 * - SF2 engine settings (velocity curve) - shown only when SF2 is active
 * - EQ 10 bandes avec presets
 */
class AudioSettingsDialog : DialogFragment() {

    private lateinit var settingsRepository: SettingsRepository

    // Views
    private lateinit var chipGroupPreset: ChipGroup
    private lateinit var seekBarGain: SeekBar
    private lateinit var textGainValue: TextView
    private lateinit var chipGroupReverb: ChipGroup

    // SF2 Settings Views
    private lateinit var sectionSf2Settings: LinearLayout
    private lateinit var chipGroupVelocityCurve: ChipGroup

    // EQ Views
    private lateinit var switchEqEnabled: SwitchCompat
    private lateinit var chipGroupEqPreset: ChipGroup
    private lateinit var eqBandsContainer: LinearLayout
    private val eqSeekBars = arrayOfNulls<SeekBar>(MidiEqualizerEngine.BAND_COUNT)
    private val eqLabels   = arrayOfNulls<TextView>(MidiEqualizerEngine.BAND_COUNT)

    // Callbacks
    var onReverbChanged: ((Int) -> Unit)? = null
    var onEqEnabled: ((Boolean) -> Unit)? = null
    var onEqBandChanged: ((Int, Int) -> Unit)? = null  // band, millibels

    // Flag to show SF2 settings section
    var showSf2Settings: Boolean = false

    companion object {
        const val TAG = "AudioSettingsDialog"

        // Presets EQ : valeurs en millibels pour les 10 bandes (32..16000 Hz)
        private val EQ_PRESETS = linkedMapOf(
            "Flat"       to intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            "Rock"       to intArrayOf(400, 300, 0, -200, -100, 0, 200, 300, 400, 400),
            "Jazz"       to intArrayOf(200, 100, 0, -200, -100, 0, 100, 200, 300, 300),
            "Piano"      to intArrayOf(-200, -100, 0, 100, 200, 300, 300, 200, 100, 0),
            "Bass Boost" to intArrayOf(600, 500, 300, 100, 0, 0, 0, 0, 0, 0)
        )

        // Range des seekbars : 0-240 → -1200..+1200 mB, centre = 120
        private const val SEEKBAR_CENTER = 120
        private const val SEEKBAR_MAX    = 240

        fun newInstance(showSf2Settings: Boolean = false) = AudioSettingsDialog().apply {
            this.showSf2Settings = showSf2Settings
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        settingsRepository = SettingsRepository(requireContext())

        val view = layoutInflater.inflate(R.layout.dialog_audio_settings, null)
        setupViews(view)
        loadCurrentSettings()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.midi_audio_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                saveSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun setupViews(view: View) {
        chipGroupPreset = view.findViewById(R.id.chip_group_preset)
        seekBarGain = view.findViewById(R.id.seekbar_master_gain)
        textGainValue = view.findViewById(R.id.text_gain_value)
        chipGroupReverb = view.findViewById(R.id.chip_group_reverb)

        // SF2 Settings
        sectionSf2Settings = view.findViewById(R.id.section_sf2_settings)
        chipGroupVelocityCurve = view.findViewById(R.id.chip_group_velocity_curve)

        // EQ
        switchEqEnabled   = view.findViewById(R.id.switch_eq_enabled)
        chipGroupEqPreset = view.findViewById(R.id.chip_group_eq_preset)
        eqBandsContainer  = view.findViewById(R.id.eq_bands_container)

        // Show/hide SF2 section based on flag
        sectionSf2Settings.visibility = if (showSf2Settings) View.VISIBLE else View.GONE

        // Ajouter les chips pour les presets de normalisation
        MidiAudioMixer.NormalizationPreset.values().forEachIndexed { index, preset ->
            val chip = Chip(requireContext()).apply {
                text = preset.label
                isCheckable = true
                tag = index
            }
            chipGroupPreset.addView(chip)
        }

        // Ajouter les chips pour les presets de reverb
        val reverbPresets = listOf(
            -1 to getString(R.string.midi_reverb_off),
            0 to getString(R.string.midi_reverb_large_hall),
            1 to getString(R.string.midi_reverb_hall),
            2 to getString(R.string.midi_reverb_chamber),
            3 to getString(R.string.midi_reverb_room)
        )

        reverbPresets.forEach { (value, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                tag = value
            }
            chipGroupReverb.addView(chip)
        }

        // Listener pour le slider de gain
        seekBarGain.max = 100
        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gainPercent = progress
                textGainValue.text = "$gainPercent%"

                // Appliquer immédiatement pour preview
                if (fromUser) {
                    val gain = progress / 100f
                    MidiAudioMixer.setMasterGain(gain)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Listener pour les presets de normalisation (appliqué immédiatement)
        chipGroupPreset.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val presetIndex = chip?.tag as? Int ?: 2
                val preset = MidiAudioMixer.NormalizationPreset.values()[presetIndex]
                MidiAudioMixer.setPreset(preset)

                // Mettre à jour le slider de gain pour refléter le preset
                seekBarGain.progress = (preset.masterGain * 100).toInt()
            }
        }

        // Listener pour les presets de reverb
        chipGroupReverb.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val reverbValue = chip?.tag as? Int ?: 1
                onReverbChanged?.invoke(reverbValue)
            }
        }

        // Setup SF2 velocity curve chips
        if (showSf2Settings) {
            setupVelocityCurveChips()
        }

        // Setup EQ
        setupEqSection()
    }

    private fun setupVelocityCurveChips() {
        val velocityCurves = listOf(
            Triple(0, getString(R.string.midi_sf2_velocity_linear), getString(R.string.midi_sf2_velocity_linear_desc)),
            Triple(1, getString(R.string.midi_sf2_velocity_concave), getString(R.string.midi_sf2_velocity_concave_desc)),
            Triple(2, getString(R.string.midi_sf2_velocity_soft), getString(R.string.midi_sf2_velocity_soft_desc)),
            Triple(3, getString(R.string.midi_sf2_velocity_hard), getString(R.string.midi_sf2_velocity_hard_desc))
        )

        velocityCurves.forEach { (index, label, _) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                tag = index
            }
            chipGroupVelocityCurve.addView(chip)
        }

        // Listener pour appliquer immédiatement
        chipGroupVelocityCurve.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val curveIndex = chip?.tag as? Int ?: 1
                val curve = when (curveIndex) {
                    0 -> VelocityCurve.LINEAR
                    1 -> VelocityCurve.CONCAVE
                    2 -> VelocityCurve.SOFT
                    3 -> VelocityCurve.HARD
                    else -> VelocityCurve.CONCAVE
                }
                Sf2Voice.velocityCurve = curve
            }
        }
    }

    private fun setupEqSection() {
        // Switch enable/disable
        switchEqEnabled.setOnCheckedChangeListener { _, checked ->
            eqBandsContainer.visibility  = if (checked) View.VISIBLE else View.GONE
            chipGroupEqPreset.visibility = if (checked) View.VISIBLE else View.GONE
            onEqEnabled?.invoke(checked)
        }

        // Chips presets
        val presetLabels = listOf(
            getString(R.string.midi_eq_preset_flat),
            getString(R.string.midi_eq_preset_rock),
            getString(R.string.midi_eq_preset_jazz),
            getString(R.string.midi_eq_preset_piano),
            getString(R.string.midi_eq_preset_bass)
        )
        val presetKeys = EQ_PRESETS.keys.toList()

        presetLabels.forEachIndexed { idx, label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                tag = presetKeys[idx]
            }
            chipGroupEqPreset.addView(chip)
        }

        chipGroupEqPreset.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val key = chip?.tag as? String ?: return@setOnCheckedStateChangeListener
                applyEqPreset(key)
            }
        }

        // Construire les 10 rangées de SeekBar
        buildEqBandRows()
    }

    private fun buildEqBandRows() {
        val freqLabels = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        for (band in 0 until MidiEqualizerEngine.BAND_COUNT) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 6.dp }
            }

            // Label fréquence (fixe, 40dp)
            val tvFreq = TextView(requireContext()).apply {
                text = freqLabels[band]
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(40.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            // SeekBar
            val seekBar = SeekBar(requireContext()).apply {
                max = SEEKBAR_MAX
                progress = SEEKBAR_CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Label valeur dB (variable, 48dp)
            val tvDb = TextView(requireContext()).apply {
                text = "0 dB"
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(48.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            eqSeekBars[band] = seekBar
            eqLabels[band]   = tvDb

            val bandIndex = band
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val millibels = (progress - SEEKBAR_CENTER) * 10
                    val db = millibels / 100f
                    tvDb.text = when {
                        db == 0f  -> "0 dB"
                        db > 0f   -> "+${String.format("%.1f", db)}"
                        else      -> String.format("%.1f", db)
                    }
                    if (fromUser) {
                        onEqBandChanged?.invoke(bandIndex, millibels)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            row.addView(tvFreq)
            row.addView(seekBar)
            row.addView(tvDb)
            eqBandsContainer.addView(row)
        }
    }

    private fun applyEqPreset(key: String) {
        val values = EQ_PRESETS[key] ?: return
        for (band in 0 until MidiEqualizerEngine.BAND_COUNT) {
            val mb = values[band]
            val progress = mb / 10 + SEEKBAR_CENTER
            eqSeekBars[band]?.progress = progress.coerceIn(0, SEEKBAR_MAX)
            onEqBandChanged?.invoke(band, mb)
        }
    }

    private fun setBandUi(band: Int, millibels: Int) {
        val progress = millibels / 10 + SEEKBAR_CENTER
        eqSeekBars[band]?.progress = progress.coerceIn(0, SEEKBAR_MAX)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            // Charger le preset actuel
            val presetIndex = settingsRepository.getMixerPreset()
            val presetCount = MidiAudioMixer.NormalizationPreset.entries.size
            val safePresetIndex = presetIndex.coerceIn(0, presetCount - 1)
            val presetChip = chipGroupPreset.getChildAt(safePresetIndex) as? Chip
            presetChip?.isChecked = true

            // Charger le gain
            val gain = settingsRepository.getMasterGain()
            seekBarGain.progress = (gain * 100).toInt()
            textGainValue.text = "${(gain * 100).toInt()}%"

            // Charger le reverb
            val reverbPreset = settingsRepository.getReverbPreset()
            for (i in 0 until chipGroupReverb.childCount) {
                val chip = chipGroupReverb.getChildAt(i) as? Chip
                if (chip?.tag == reverbPreset) {
                    chip.isChecked = true
                    break
                }
            }

            // Appliquer les settings au mixer
            val preset = MidiAudioMixer.NormalizationPreset.entries[safePresetIndex]
            MidiAudioMixer.setPreset(preset)
            MidiAudioMixer.setMasterGain(gain)

            // Charger les settings SF2 si la section est visible
            if (showSf2Settings) {
                val velocityCurveIndex = settingsRepository.getSf2VelocityCurve()
                for (i in 0 until chipGroupVelocityCurve.childCount) {
                    val chip = chipGroupVelocityCurve.getChildAt(i) as? Chip
                    if (chip?.tag == velocityCurveIndex) {
                        chip.isChecked = true
                        break
                    }
                }

                // Appliquer la courbe au moteur SF2
                val curve = when (velocityCurveIndex) {
                    0 -> VelocityCurve.LINEAR
                    1 -> VelocityCurve.CONCAVE
                    2 -> VelocityCurve.SOFT
                    3 -> VelocityCurve.HARD
                    else -> VelocityCurve.CONCAVE
                }
                Sf2Voice.velocityCurve = curve
            }

            // Charger les settings EQ
            val eqEnabled = settingsRepository.getMidiEqEnabled()
            switchEqEnabled.isChecked = eqEnabled
            eqBandsContainer.visibility  = if (eqEnabled) View.VISIBLE else View.GONE
            chipGroupEqPreset.visibility = if (eqEnabled) View.VISIBLE else View.GONE

            for (band in 0 until MidiEqualizerEngine.BAND_COUNT) {
                val mb = settingsRepository.getMidiEqBandLevel(band)
                setBandUi(band, mb)
            }
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            // Sauvegarder le preset
            val checkedPresetId = chipGroupPreset.checkedChipId
            if (checkedPresetId != View.NO_ID) {
                val chip = chipGroupPreset.findViewById<Chip>(checkedPresetId)
                val presetIndex = chip?.tag as? Int ?: 2
                settingsRepository.saveMixerPreset(presetIndex)
            }

            // Sauvegarder le gain
            val gain = seekBarGain.progress / 100f
            settingsRepository.saveMasterGain(gain)

            // Sauvegarder le reverb
            val checkedReverbId = chipGroupReverb.checkedChipId
            if (checkedReverbId != View.NO_ID) {
                val chip = chipGroupReverb.findViewById<Chip>(checkedReverbId)
                val reverbValue = chip?.tag as? Int ?: 1
                settingsRepository.saveReverbPreset(reverbValue)
            }

            // Sauvegarder les settings SF2 si la section est visible
            if (showSf2Settings) {
                val checkedVelocityId = chipGroupVelocityCurve.checkedChipId
                if (checkedVelocityId != View.NO_ID) {
                    val chip = chipGroupVelocityCurve.findViewById<Chip>(checkedVelocityId)
                    val curveIndex = chip?.tag as? Int ?: 1
                    settingsRepository.saveSf2VelocityCurve(curveIndex)
                }
            }

            // Sauvegarder l'EQ
            settingsRepository.setMidiEqEnabled(switchEqEnabled.isChecked)
            for (band in 0 until MidiEqualizerEngine.BAND_COUNT) {
                val progress = eqSeekBars[band]?.progress ?: SEEKBAR_CENTER
                val millibels = (progress - SEEKBAR_CENTER) * 10
                settingsRepository.setMidiEqBandLevel(band, millibels)
            }
        }
    }
}
