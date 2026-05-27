package com.Atom2Universe.app.music.equalizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.MusicPlaybackHolder
import com.Atom2Universe.app.music.equalizer.MusicEqualizerManager
import com.Atom2Universe.app.music.equalizer.data.EqAlbumOverride
import com.Atom2Universe.app.music.equalizer.data.EqArtistOverride
import com.Atom2Universe.app.music.equalizer.data.EqPreset
import com.Atom2Universe.app.music.equalizer.data.OverrideSource
import com.Atom2Universe.app.music.equalizer.data.ResolvedPreset
import com.Atom2Universe.app.music.model.MusicTrack
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet fragment for the equalizer UI.
 * Displays 10-band EQ, bass boost, virtualizer, and preset management.
 */
class EqualizerFragment : BottomSheetDialogFragment(), MusicEqualizerManager.EqualizerListener {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var spinnerPreset: Spinner
    private lateinit var btnSavePreset: ImageButton
    private lateinit var btnDeletePreset: ImageButton
    private lateinit var chipGroupApplyTo: ChipGroup
    private lateinit var chipGlobal: Chip
    private lateinit var chipTrack: Chip
    private lateinit var chipAlbum: Chip
    private lateinit var chipArtist: Chip
    private lateinit var txtOverrideInfo: TextView
    private lateinit var layoutEqBands: LinearLayout
    private lateinit var layoutEffectBands: LinearLayout
    private lateinit var viewEffectSeparator: View
    private lateinit var chipGroupQuickPresets: ChipGroup

    private val bandViews = mutableListOf<BandViewHolder>()
    private var bassBoostView: EffectViewHolder? = null
    private var virtualizerView: EffectViewHolder? = null
    private var presets = listOf<EqPreset>()
    private var presetAdapter: ArrayAdapter<String>? = null
    private var currentTrack: MusicTrack? = null
    private var isUpdatingUI = false
    private var isInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure MusicEqualizerManager is initialized (in case no music has been played yet)
        MusicEqualizerManager.initialize(requireContext())

        // Initialize views
        switchEnabled = view.findViewById(R.id.switchEqEnabled)
        spinnerPreset = view.findViewById(R.id.spinnerPreset)
        btnSavePreset = view.findViewById(R.id.btnSavePreset)
        btnDeletePreset = view.findViewById(R.id.btnDeletePreset)
        chipGroupApplyTo = view.findViewById(R.id.chipGroupApplyTo)
        chipGlobal = view.findViewById(R.id.chipGlobal)
        chipTrack = view.findViewById(R.id.chipTrack)
        chipAlbum = view.findViewById(R.id.chipAlbum)
        chipArtist = view.findViewById(R.id.chipArtist)
        txtOverrideInfo = view.findViewById(R.id.txtOverrideInfo)
        layoutEqBands = view.findViewById(R.id.layoutEqBands)
        layoutEffectBands = view.findViewById(R.id.layoutEffectBands)
        viewEffectSeparator = view.findViewById(R.id.viewEffectSeparator)
        chipGroupQuickPresets = view.findViewById(R.id.chipGroupQuickPresets)

        // Get current track
        currentTrack = MusicPlaybackHolder.getCurrentTrack()

        // Disable BottomSheet drag to allow vertical SeekBars to work properly
        setupBottomSheetBehavior()

        // Setup UI
        setupEqBands()
        setupEffectBands()
        setupPresetSpinner()
        setupListeners()
        setupQuickPresets()

        // Initial state
        switchEnabled.isChecked = MusicEqualizerManager.isEnabled.value

        // Select default chip based on context:
        // If a track is playing, default to "Track" so changes are per-track
        // Otherwise, default to "Global"
        selectDefaultApplyToChip()

        // Load presets and initialize UI
        loadPresetsAndInitialize()

        // Register listener
        MusicEqualizerManager.addListener(this)
    }

    private fun setupBottomSheetBehavior() {
        // Disable drag-to-dismiss to prevent conflicts with vertical SeekBars
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            bottomSheetDialog.behavior.isDraggable = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        MusicEqualizerManager.removeListener(this)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // Notify parent that the fragment is closing (to update equalizer button color)
        if (isAdded) {
            parentFragmentManager.setFragmentResult(TAG, Bundle())
        }
    }

    private fun setupEqBands() {
        val frequencies = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
        val inflater = LayoutInflater.from(requireContext())

        frequencies.forEachIndexed { index, freq ->
            val bandView = inflater.inflate(R.layout.view_eq_band, layoutEqBands, false)

            val txtFrequency = bandView.findViewById<TextView>(R.id.txtFrequency)
            val txtDbValue = bandView.findViewById<TextView>(R.id.txtDbValue)
            val seekBar = bandView.findViewById<SeekBar>(R.id.seekBarBand)

            txtFrequency.text = freq
            txtDbValue.text = "0"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isUpdatingUI) {
                        // Convert progress (0-2400) to millibels (-1200 to +1200)
                        val level = progress - 1200
                        // Apply immediately for audio feedback (without saving)
                        MusicEqualizerManager.setBandLevelWithoutSave(index, level)
                        txtDbValue.text = formatDb(level)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // Save when user finishes adjusting, respecting chip selection
                    if (!isUpdatingUI) {
                        saveCurrentSettingsToSelectedTarget()
                    }
                }
            })

            bandViews.add(BandViewHolder(txtDbValue, seekBar))
            // Insert before the separator (which is the first child initially)
            val separatorIndex = layoutEqBands.indexOfChild(viewEffectSeparator)
            layoutEqBands.addView(bandView, separatorIndex)
        }
    }

    private fun setupEffectBands() {
        val inflater = LayoutInflater.from(requireContext())

        // Bass Boost band (always show)
        val bassView = inflater.inflate(R.layout.view_eq_effect_band, layoutEffectBands, false)
        val txtBassValue = bassView.findViewById<TextView>(R.id.txtEffectValue)
        val txtBassName = bassView.findViewById<TextView>(R.id.txtEffectName)
        val seekBarBass = bassView.findViewById<SeekBar>(R.id.seekBarEffect)

        txtBassName.text = getString(R.string.eq_bass_short)
        txtBassValue.text = "0%"

        seekBarBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUI) {
                    MusicEqualizerManager.setBassBoostWithoutSave(progress)
                    txtBassValue.text = "${progress / 10}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!isUpdatingUI) {
                    saveCurrentSettingsToSelectedTarget()
                }
            }
        })

        bassBoostView = EffectViewHolder(txtBassValue, seekBarBass)
        layoutEffectBands.addView(bassView)

        // Virtualizer band (always show)
        val virtualizerViewLayout = inflater.inflate(R.layout.view_eq_effect_band, layoutEffectBands, false)
        val txtVirtValue = virtualizerViewLayout.findViewById<TextView>(R.id.txtEffectValue)
        val txtVirtName = virtualizerViewLayout.findViewById<TextView>(R.id.txtEffectName)
        val seekBarVirt = virtualizerViewLayout.findViewById<SeekBar>(R.id.seekBarEffect)

        txtVirtName.text = getString(R.string.eq_virtual_short)
        txtVirtValue.text = "0%"

        seekBarVirt.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUI) {
                    MusicEqualizerManager.setVirtualizerWithoutSave(progress)
                    txtVirtValue.text = "${progress / 10}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!isUpdatingUI) {
                    saveCurrentSettingsToSelectedTarget()
                }
            }
        })

        virtualizerView = EffectViewHolder(txtVirtValue, seekBarVirt)
        layoutEffectBands.addView(virtualizerViewLayout)
    }

    private fun setupPresetSpinner() {
        presetAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerPreset.adapter = presetAdapter

        spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingUI && isInitialized && position in presets.indices) {
                    val preset = presets[position]
                    applySelectedPreset(preset)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        // Enable switch
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUI) {
                MusicEqualizerManager.setEnabled(isChecked)
            }
        }

        // When user changes the "Apply to" chip, automatically apply the current preset
        // to the new target. This is important because if the spinner already shows "Rock"
        // and the user switches from Track to Album, clicking "Rock" again won't trigger
        // the spinner's onItemSelected (since the selection didn't change).
        chipGroupApplyTo.setOnCheckedStateChangeListener { group, checkedIds ->
            if (!isUpdatingUI && isInitialized && checkedIds.isNotEmpty()) {
                android.util.Log.d(TAG, "ChipGroup selection CHANGED: checkedIds=$checkedIds, applying current preset to new target")

                // Get the currently selected preset and apply it to the new target
                val selectedPosition = spinnerPreset.selectedItemPosition
                if (selectedPosition in presets.indices) {
                    val currentPreset = presets[selectedPosition]
                    applySelectedPreset(currentPreset)
                }
            }
        }

        // Long press on a chip clears that override level
        // and falls back to the next level in the cascade (Track → Album → Artist → Global)
        chipTrack.setOnLongClickListener {
            if (isInitialized && !isUpdatingUI) {
                clearOverrideAndFallback(OverrideSource.TRACK)
                true
            } else false
        }
        chipAlbum.setOnLongClickListener {
            if (isInitialized && !isUpdatingUI) {
                clearOverrideAndFallback(OverrideSource.ALBUM)
                true
            } else false
        }
        chipArtist.setOnLongClickListener {
            if (isInitialized && !isUpdatingUI) {
                clearOverrideAndFallback(OverrideSource.ARTIST)
                true
            } else false
        }
        // Note: chipGlobal doesn't need this - there's nothing to clear at global level

        // Save preset button
        btnSavePreset.setOnClickListener {
            showSavePresetDialog()
        }

        // Delete preset button
        btnDeletePreset.setOnClickListener {
            val position = spinnerPreset.selectedItemPosition
            if (position in presets.indices) {
                val preset = presets[position]
                if (preset.isSystemPreset) {
                    Toast.makeText(context, R.string.eq_cannot_delete_system, Toast.LENGTH_SHORT).show()
                } else {
                    showDeletePresetDialog(preset)
                }
            }
        }
    }

    private fun setupQuickPresets() {
        val quickPresetNames = listOf("Flat", "Rock", "Pop", "Jazz", "Bass Boost", "Vocal")

        quickPresetNames.forEach { name ->
            val chip = Chip(requireContext()).apply {
                text = name
                isCheckable = true
            }
            chipGroupQuickPresets.addView(chip)
        }

        // Use ChipGroup's selection listener instead of individual click listeners
        // This works better with singleSelection mode
        chipGroupQuickPresets.setOnCheckedStateChangeListener { group, checkedIds ->
            android.util.Log.d(TAG, "chipGroupQuickPresets: checkedIds=$checkedIds, isUpdatingUI=$isUpdatingUI, isInitialized=$isInitialized, presetsSize=${presets.size}")
            if (!isUpdatingUI && isInitialized && checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val presetName = selectedChip?.text?.toString() ?: return@setOnCheckedStateChangeListener
                android.util.Log.d(TAG, "Quick preset chip selected: '$presetName'")

                viewLifecycleOwner.lifecycleScope.launch {
                    val preset = presets.find { it.name == presetName }
                    android.util.Log.d(TAG, "Quick preset found: ${preset?.name ?: "NULL"}, id=${preset?.id ?: -1}")
                    if (preset != null) {
                        applySelectedPreset(preset)
                    } else {
                        android.util.Log.e(TAG, "Preset '$presetName' NOT FOUND in presets list!")
                    }
                }
            }
        }
    }

    /**
     * Selects the "Apply to" chip based on the current preset source.
     * This shows the user which level is currently active (Track/Album/Artist/Global).
     */
    private fun selectDefaultApplyToChip() {
        val currentResolved = MusicEqualizerManager.currentPreset.value
        val source = currentResolved?.source ?: OverrideSource.GLOBAL
        updateChipSelection(source)
        android.util.Log.d(TAG, "selectDefaultApplyToChip: source=$source")
    }

    private fun loadPresetsAndInitialize() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Load all presets
            val loadedPresets = MusicEqualizerManager.getAllPresets()
            presets = loadedPresets
            android.util.Log.d(TAG, "loadPresetsAndInitialize: Loaded ${loadedPresets.size} presets")
            loadedPresets.forEach { preset ->
                android.util.Log.d(TAG, "  - Preset: id=${preset.id}, name='${preset.name}', isSystem=${preset.isSystemPreset}")
            }

            // Get current settings
            val globalPresetId = MusicEqualizerManager.getGlobalPresetId()
            val currentResolved = MusicEqualizerManager.currentPreset.value
            android.util.Log.d(TAG, "loadPresetsAndInitialize: globalPresetId=$globalPresetId, currentResolved=${currentResolved?.preset?.name}")

            // Get actual current band levels (may differ from preset if user adjusted)
            val currentBandLevels = MusicEqualizerManager.getAllBandLevels()
            val currentBass = MusicEqualizerManager.getBassBoost()
            val currentVirtualizer = MusicEqualizerManager.getVirtualizer()

            withContext(Dispatchers.Main) {
                isUpdatingUI = true

                // Update spinner
                presetAdapter?.clear()
                presetAdapter?.addAll(loadedPresets.map { it.name })
                presetAdapter?.notifyDataSetChanged()

                // Select current preset in spinner
                val presetToSelect = currentResolved?.preset ?: loadedPresets.find { it.id == globalPresetId }
                presetToSelect?.let { preset ->
                    val index = loadedPresets.indexOfFirst { it.id == preset.id }
                    if (index >= 0) {
                        spinnerPreset.setSelection(index)
                    }
                }

                // Update bands UI with actual current values (not preset values)
                updateBandsFromCurrentValues(currentBandLevels, currentBass, currentVirtualizer)

                // Update override info
                currentResolved?.let { updateOverrideInfo(it) }

                // Update quick presets selection
                updateQuickPresetsSelection()

                isUpdatingUI = false
                isInitialized = true
            }
        }
    }

    private fun updateBandsFromCurrentValues(bandLevels: List<Int>, bassBoost: Int, virtualizer: Int) {
        bandViews.forEachIndexed { index, holder ->
            val level = bandLevels.getOrElse(index) { 0 }
            holder.seekBar.progress = level + 1200 // Convert to 0-2400
            holder.txtDbValue.text = formatDb(level)
        }

        // Update bass boost
        bassBoostView?.let { holder ->
            holder.seekBar.progress = bassBoost
            holder.txtValue.text = "${bassBoost / 10}%"
        }

        // Update virtualizer
        virtualizerView?.let { holder ->
            holder.seekBar.progress = virtualizer
            holder.txtValue.text = "${virtualizer / 10}%"
        }
    }

    private fun applySelectedPreset(preset: EqPreset) {
        viewLifecycleOwner.lifecycleScope.launch {
            val track = currentTrack
            val checkedChipId = chipGroupApplyTo.checkedChipId

            // Debug: log individual chip states
            android.util.Log.d(TAG, "applySelectedPreset: chipStates - Global=${chipGlobal.isChecked}, Track=${chipTrack.isChecked}, Album=${chipAlbum.isChecked}, Artist=${chipArtist.isChecked}")
            android.util.Log.d(TAG, "applySelectedPreset: preset='${preset.name}' (id=${preset.id}), track=${track?.id} '${track?.title}', checkedChipId=$checkedChipId")
            android.util.Log.d(TAG, "applySelectedPreset: R.id.chipGlobal=${R.id.chipGlobal}, R.id.chipTrack=${R.id.chipTrack}, R.id.chipAlbum=${R.id.chipAlbum}, R.id.chipArtist=${R.id.chipArtist}")

            // Determine what to save to based on chip selection
            when (checkedChipId) {
                R.id.chipGlobal -> {
                    android.util.Log.d(TAG, "applySelectedPreset: Setting as GLOBAL preset")
                    MusicEqualizerManager.setGlobalPreset(preset.id)
                }
                R.id.chipTrack -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "applySelectedPreset: Setting TRACK override for trackId=${track.id}")
                        MusicEqualizerManager.setTrackOverride(track.id, preset.id)
                    } else {
                        android.util.Log.w(TAG, "applySelectedPreset: chipTrack selected but no track - saving as global")
                        MusicEqualizerManager.setGlobalPreset(preset.id)
                    }
                }
                R.id.chipAlbum -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "applySelectedPreset: Setting ALBUM override for album='${track.album}'")
                        MusicEqualizerManager.setAlbumOverride(track.album, track.albumArtist, preset.id)
                    } else {
                        MusicEqualizerManager.setGlobalPreset(preset.id)
                    }
                }
                R.id.chipArtist -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "applySelectedPreset: Setting ARTIST override for artist='${track.artist}'")
                        MusicEqualizerManager.setArtistOverride(track.artist, track.albumArtist, preset.id)
                    } else {
                        MusicEqualizerManager.setGlobalPreset(preset.id)
                    }
                }
                else -> {
                    android.util.Log.d(TAG, "applySelectedPreset: No chip selected, defaulting to global")
                    MusicEqualizerManager.setGlobalPreset(preset.id)
                }
            }

            // Apply directly for immediate feedback
            android.util.Log.d(TAG, "applySelectedPreset: Calling applyPresetDirectly")
            MusicEqualizerManager.applyPresetDirectly(preset)

            // Update UI - bands, spinner selection, and quick presets
            withContext(Dispatchers.Main) {
                isUpdatingUI = true
                updateBandsFromPreset(preset)
                selectPresetInSpinner(preset.id)
                updateQuickPresetsSelection()
                isUpdatingUI = false
            }
        }
    }

    /**
     * Saves the current slider settings to the selected target (Track/Album/Artist/Global).
     * Creates or updates a "Custom" preset with current values, then saves it as an override.
     */
    private fun saveCurrentSettingsToSelectedTarget() {
        val track = currentTrack
        val checkedChipId = chipGroupApplyTo.checkedChipId

        android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: checkedChipId=$checkedChipId, track=${track?.id}")

        viewLifecycleOwner.lifecycleScope.launch {
            // Get current slider values
            val bandLevels = bandViews.map { it.seekBar.progress - 1200 }
            val bassBoost = bassBoostView?.seekBar?.progress ?: 0
            val virtualizer = virtualizerView?.seekBar?.progress ?: 0

            // Each target (track/album/artist) gets its own isolated custom preset slot
            // to avoid overwriting another target's adjustments when sharing a single "Custom" preset.
            val targetPresetName = when (checkedChipId) {
                R.id.chipTrack -> if (track != null) "~custom_track_${track.id}" else MusicEqualizerManager.CUSTOM_PRESET_NAME
                R.id.chipAlbum -> if (track != null) "~custom_album_${EqAlbumOverride.createKey(track.album, track.albumArtist)}" else MusicEqualizerManager.CUSTOM_PRESET_NAME
                R.id.chipArtist -> if (track != null) "~custom_artist_${EqArtistOverride.createKey(track.artist, track.albumArtist)}" else MusicEqualizerManager.CUSTOM_PRESET_NAME
                else -> MusicEqualizerManager.CUSTOM_PRESET_NAME
            }

            val customPresetId = withContext(Dispatchers.IO) {
                MusicEqualizerManager.createOrUpdateCustomPreset(bandLevels, bassBoost, virtualizer, targetPresetName)
            }

            android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: preset='$targetPresetName' id=$customPresetId")

            // Save the preset to the appropriate target
            when (checkedChipId) {
                R.id.chipGlobal -> {
                    android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: Saving as GLOBAL")
                    MusicEqualizerManager.setGlobalPreset(customPresetId)
                }
                R.id.chipTrack -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: Saving as TRACK override for trackId=${track.id}")
                        MusicEqualizerManager.setTrackOverride(track.id, customPresetId)
                    } else {
                        android.util.Log.w(TAG, "saveCurrentSettingsToSelectedTarget: No track, saving as global")
                        MusicEqualizerManager.setGlobalPreset(customPresetId)
                    }
                }
                R.id.chipAlbum -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: Saving as ALBUM override for album='${track.album}'")
                        MusicEqualizerManager.setAlbumOverride(track.album, track.albumArtist, customPresetId)
                    } else {
                        MusicEqualizerManager.setGlobalPreset(customPresetId)
                    }
                }
                R.id.chipArtist -> {
                    if (track != null) {
                        android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: Saving as ARTIST override for artist='${track.artist}'")
                        MusicEqualizerManager.setArtistOverride(track.artist, track.albumArtist, customPresetId)
                    } else {
                        MusicEqualizerManager.setGlobalPreset(customPresetId)
                    }
                }
                else -> {
                    android.util.Log.d(TAG, "saveCurrentSettingsToSelectedTarget: No chip selected, defaulting to global")
                    MusicEqualizerManager.setGlobalPreset(customPresetId)
                }
            }
        }
    }

    private fun updateApplyToSelection() {
        // This is called when user clicks on a chip
        // We don't need to change the preset here, just note the selection
        // The next preset selection will use this "apply to" setting
    }

    private fun selectPresetInSpinner(presetId: Long) {
        val index = presets.indexOfFirst { it.id == presetId }
        if (index >= 0 && spinnerPreset.selectedItemPosition != index) {
            spinnerPreset.setSelection(index)
        }
    }

    private fun updateBandsFromPreset(preset: EqPreset) {
        val levels = preset.getBandLevels()
        bandViews.forEachIndexed { index, holder ->
            val level = levels.getOrElse(index) { 0 }
            holder.seekBar.progress = level + 1200 // Convert to 0-2400
            holder.txtDbValue.text = formatDb(level)
        }

        // Update bass boost
        bassBoostView?.let { holder ->
            holder.seekBar.progress = preset.bassBoostStrength
            holder.txtValue.text = "${preset.bassBoostStrength / 10}%"
        }

        // Update virtualizer
        virtualizerView?.let { holder ->
            holder.seekBar.progress = preset.virtualizerStrength
            holder.txtValue.text = "${preset.virtualizerStrength / 10}%"
        }
    }

    private fun updateUIFromPreset(resolved: ResolvedPreset) {
        isUpdatingUI = true

        val preset = resolved.preset

        // Update bands
        updateBandsFromPreset(preset)

        // Update spinner selection
        selectPresetInSpinner(preset.id)

        // Update override info
        updateOverrideInfo(resolved)

        // Update chip selection to match the current source
        updateChipSelection(resolved.source)

        // Update quick presets
        updateQuickPresetsSelection()

        isUpdatingUI = false
    }

    /**
     * Updates the chip selection to match the given source.
     * Called when the preset changes (e.g., after clearing an override).
     */
    private fun updateChipSelection(source: OverrideSource) {
        when (source) {
            OverrideSource.TRACK -> chipTrack.isChecked = true
            OverrideSource.ALBUM -> chipAlbum.isChecked = true
            OverrideSource.ARTIST -> chipArtist.isChecked = true
            OverrideSource.GLOBAL -> chipGlobal.isChecked = true
        }
    }

    private fun updateOverrideInfo(resolved: ResolvedPreset) {
        val sourceText = when (resolved.source) {
            OverrideSource.TRACK -> getString(R.string.eq_override_track, resolved.preset.name)
            OverrideSource.ALBUM -> getString(R.string.eq_override_album, resolved.preset.name)
            OverrideSource.ARTIST -> getString(R.string.eq_override_artist, resolved.preset.name)
            OverrideSource.GLOBAL -> getString(R.string.eq_override_global, resolved.preset.name)
        }

        txtOverrideInfo.text = sourceText
        txtOverrideInfo.visibility = if (resolved.source != OverrideSource.GLOBAL) View.VISIBLE else View.GONE

        // NOTE: We no longer change chip selection here.
        // The chip selection represents what the user WANTS to apply to (Track/Album/Artist/Global),
        // not what the current preset source IS.
        // selectDefaultApplyToChip() sets the default based on context (Track if playing, Global if not),
        // and the user can change it by clicking on chips.
    }

    private fun updateQuickPresetsSelection() {
        val currentPresetName = MusicEqualizerManager.currentPreset.value?.preset?.name
            ?: presets.getOrNull(spinnerPreset.selectedItemPosition)?.name

        for (i in 0 until chipGroupQuickPresets.childCount) {
            val chip = chipGroupQuickPresets.getChildAt(i) as? Chip
            chip?.isChecked = chip?.text == currentPresetName
        }
    }

    /**
     * Clears the override at the specified level and falls back to the next level.
     * Cascade order: Track → Album → Artist → Global
     *
     * For example, if the track has a "Rock" override and the album has a "Jazz" override:
     * - Clearing Track will fall back to Album's "Jazz" preset
     * - Clearing Album will fall back to Artist or Global
     */
    private fun clearOverrideAndFallback(level: OverrideSource) {
        val track = currentTrack ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            android.util.Log.d(TAG, "clearOverrideAndFallback: Clearing $level override for track ${track.id}")

            // Clear the override at this level
            when (level) {
                OverrideSource.TRACK -> {
                    MusicEqualizerManager.clearTrackOverride(track.id)
                    Toast.makeText(context, R.string.eq_override_cleared_track, Toast.LENGTH_SHORT).show()
                }
                OverrideSource.ALBUM -> {
                    MusicEqualizerManager.clearAlbumOverride(track.album, track.albumArtist)
                    Toast.makeText(context, R.string.eq_override_cleared_album, Toast.LENGTH_SHORT).show()
                }
                OverrideSource.ARTIST -> {
                    MusicEqualizerManager.clearArtistOverride(track.artist, track.albumArtist)
                    Toast.makeText(context, R.string.eq_override_cleared_artist, Toast.LENGTH_SHORT).show()
                }
                OverrideSource.GLOBAL -> {
                    // Nothing to clear at global level
                    return@launch
                }
            }

            // The clear functions in MusicEqualizerManager already call onTrackChanged
            // which will re-resolve and apply the fallback preset.
            // We just need to update our UI when the preset changes (via the listener callback)
        }
    }

    private fun showSavePresetDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_save_preset, null)
        dialogView.findViewById<TextInputLayout>(R.id.tilPresetName)
        val editPresetName = dialogView.findViewById<TextInputEditText>(R.id.editPresetName)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editPresetName.text?.toString()?.trim()
                if (name.isNullOrEmpty()) {
                    Toast.makeText(context, R.string.eq_preset_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    saveCurrentAsPreset(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCurrentAsPreset(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bandLevels = bandViews.map { it.seekBar.progress - 1200 }
            val bassBoost = bassBoostView?.seekBar?.progress ?: 0
            val virtualizer = virtualizerView?.seekBar?.progress ?: 0

            val presetId = MusicEqualizerManager.createPreset(name, bandLevels, bassBoost, virtualizer)

            withContext(Dispatchers.Main) {
                if (presetId > 0) {
                    Toast.makeText(context, R.string.eq_preset_saved, Toast.LENGTH_SHORT).show()
                    // Reload presets
                    loadPresetsAndInitialize()
                }
            }
        }
    }

    private fun showDeletePresetDialog(preset: EqPreset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.eq_delete_preset)
            .setMessage(getString(R.string.eq_delete_confirm, preset.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                deletePreset(preset)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deletePreset(preset: EqPreset) {
        viewLifecycleOwner.lifecycleScope.launch {
            MusicEqualizerManager.deletePreset(preset.id)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.eq_preset_deleted, Toast.LENGTH_SHORT).show()
                // Reload presets
                loadPresetsAndInitialize()
            }
        }
    }

    private fun formatDb(millibels: Int): String {
        val db = millibels / 100f
        return when {
            db > 0 -> "+${db.toInt()}"
            else -> "${db.toInt()}"
        }
    }

    // EqualizerListener callbacks
    override fun onPresetChanged(resolved: ResolvedPreset) {
        if (isInitialized && !isUpdatingUI) {
            updateUIFromPreset(resolved)
        }
    }

    override fun onEnabledChanged(enabled: Boolean) {
        if (!isUpdatingUI && switchEnabled.isChecked != enabled) {
            isUpdatingUI = true
            switchEnabled.isChecked = enabled
            isUpdatingUI = false
        }
    }

    private data class BandViewHolder(
        val txtDbValue: TextView,
        val seekBar: SeekBar
    )

    private data class EffectViewHolder(
        val txtValue: TextView,
        val seekBar: SeekBar
    )

    companion object {
        const val TAG = "EqualizerFragment"

        fun newInstance(): EqualizerFragment {
            return EqualizerFragment()
        }
    }
}
