package com.Atom2Universe.app.sf2creator.ui

import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.util.Log
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.sf2.Sf2Engine
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import com.Atom2Universe.app.sf2creator.data.Sf2Clipboard
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.util.WavUtils
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment showing details of a single SF2 project.
 * Displays samples and allows adding more or exporting.
 * Includes a keyboard preview to test the project before export.
 */
class ProjectDetailFragment : Fragment() {

    private lateinit var projectNameText: TextView
    private lateinit var samplesCountText: TextView
    private lateinit var programText: TextView
    private lateinit var changeProgramButton: Button
    private lateinit var addSampleButton: Button
    private lateinit var exportProjectButton: Button
    private lateinit var samplesRecycler: RecyclerView
    private lateinit var samplesEmptyState: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressText: TextView

    // Programs section (MIDI Program 0-127)
    private lateinit var programsLabel: TextView
    private lateinit var programsScroll: HorizontalScrollView
    private lateinit var programsContainer: LinearLayout
    private lateinit var addProgramButton: ImageButton

    // Instruments section (presets in code, instruments in SF2 terms)
    private lateinit var presetsLabel: TextView
    private lateinit var presetsScroll: HorizontalScrollView
    private lateinit var presetsContainer: LinearLayout
    private lateinit var addPresetButton: ImageButton

    // Keyboard preview
    private lateinit var keyboardPreview: KeyboardPreviewView
    private lateinit var keyboardSection: LinearLayout
    private lateinit var keyboardLabel: TextView
    private lateinit var keyboardLoading: ProgressBar
    private lateinit var keyboardEmptyHint: TextView
    private var octaveDownButton: ImageButton? = null
    private var octaveUpButton: ImageButton? = null

    // Engine selector
    private var engineNameText: TextView? = null
    private var switchEngineButton: Button? = null

    private lateinit var repository: Sf2ProjectRepository
    private lateinit var adapter: ProjectSampleAdapter

    private var projectId: Long = -1
    private var selectedProgramId: Long = -1
    private var selectedPresetId: Long = -1
    private var currentPrograms: List<Sf2ProgramEntity> = emptyList()
    private var currentPresets: List<Sf2PresetEntity> = emptyList()
    private var programInstrumentCounts: Map<Long, Int> = emptyMap()
    private var presetSampleCounts: Map<Long, Int> = emptyMap()

    // Engines for keyboard preview
    private var sf2Engine: Sf2Engine? = null
    private var fluidSynthEngine: FluidSynthEngine? = null
    private var tempSf2File: File? = null
    private var engineReady: Boolean = false
    private var useFluidSynth: Boolean = false
    private var previewGenerationJob: Job? = null
    private var currentSamplesList: List<Sf2SampleEntity> = emptyList()

    // Debounce and synchronization for preview generation
    private var lastPreviewRequestTime: Long = 0
    private val previewDebounceMs = 300L
    private val engineLock = Any()

    // Jobs for flow collection (to cancel when needed)
    private var presetsCollectionJob: Job? = null
    private var samplesCollectionJob: Job? = null

    // Callbacks
    var onAddSampleRequested: (() -> Unit)? = null
    var onExportComplete: ((File) -> Unit)? = null
    var onSampleEditRequested: ((Sf2SampleEntity) -> Unit)? = null

    // SAF launcher for export - lets user choose where to save
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { exportToUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_project_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = Sf2ProjectRepository(requireContext())

        findViews(view)
        setupAdapter()
        setupKeyboard()
        setupButtons()

        // Load project if ID was set
        if (projectId > 0) {
            loadProject()
        }
    }

    private fun findViews(view: View) {
        projectNameText = view.findViewById(R.id.project_name_text)
        samplesCountText = view.findViewById(R.id.samples_count_text)
        programText = view.findViewById(R.id.program_text)
        changeProgramButton = view.findViewById(R.id.change_program_button)
        addSampleButton = view.findViewById(R.id.add_sample_button)
        exportProjectButton = view.findViewById(R.id.export_project_button)
        samplesRecycler = view.findViewById(R.id.samples_recycler)
        samplesEmptyState = view.findViewById(R.id.samples_empty_state)
        progressOverlay = view.findViewById(R.id.progress_overlay)
        progressText = view.findViewById(R.id.progress_text)

        // Programs section
        programsLabel = view.findViewById(R.id.programs_label)
        programsScroll = view.findViewById(R.id.programs_scroll)
        programsContainer = view.findViewById(R.id.programs_container)
        addProgramButton = view.findViewById(R.id.add_program_button)

        // Instruments section (presets in code)
        presetsLabel = view.findViewById(R.id.presets_label)
        presetsScroll = view.findViewById(R.id.presets_scroll)
        presetsContainer = view.findViewById(R.id.presets_container)
        addPresetButton = view.findViewById(R.id.add_preset_button)

        // Keyboard preview
        keyboardPreview = view.findViewById(R.id.keyboard_preview)
        keyboardSection = view.findViewById(R.id.keyboard_section)
        keyboardLabel = view.findViewById(R.id.keyboard_label)
        keyboardLoading = view.findViewById(R.id.keyboard_loading)
        keyboardEmptyHint = view.findViewById(R.id.keyboard_empty_hint)
        octaveDownButton = view.findViewById(R.id.octave_down_button)
        octaveUpButton = view.findViewById(R.id.octave_up_button)

        // Engine selector
        engineNameText = view.findViewById(R.id.engine_name_text)
        switchEngineButton = view.findViewById(R.id.switch_engine_button)
    }

    private fun setupAdapter() {
        adapter = ProjectSampleAdapter(
            onDeleteClick = { sample ->
                showDeleteSampleConfirmation(sample)
            },
            onItemClick = { sample ->
                // Select sample and update keyboard to show its key range
                selectSample(sample)
            },
            onLongClick = { sample ->
                // Long press shows context menu with edit option
                showSampleOptionsDialog(sample)
            }
        )
        samplesRecycler.adapter = adapter
    }

    /**
     * Select a sample and update the keyboard preview to show its key range.
     */
    private fun selectSample(sample: Sf2SampleEntity) {
        adapter.setSelectedSample(sample.id)
        updateKeyboardForSample(sample)
    }

    /**
     * Update the keyboard to display the selected sample's root note and key range.
     */
    private fun updateKeyboardForSample(sample: Sf2SampleEntity) {
        // Set the root note (keyboard centers around it)
        keyboardPreview.setRootNote(sample.rootNote)

        // Set the key range if it differs from just the root note
        if (sample.keyRangeStart != sample.keyRangeEnd ||
            sample.keyRangeStart != sample.rootNote) {
            keyboardPreview.setKeyRange(sample.keyRangeStart, sample.keyRangeEnd)
        } else {
            keyboardPreview.clearKeyRange()
        }

        updateOctaveButtonStates()
    }

    private fun showSampleOptionsDialog(sample: Sf2SampleEntity) {
        val optionsList = mutableListOf(
            getString(R.string.sf2_copy_sample),
            getString(R.string.sf2_edit_waveform),
            getString(R.string.sf2_edit_parameters)
        )

        // Add paste option if clipboard has samples
        if (Sf2Clipboard.hasSamples()) {
            optionsList.add(getString(R.string.sf2_paste_here))
        }

        optionsList.add(getString(R.string.sf2_delete_sample))

        AlertDialog.Builder(requireContext())
            .setTitle(sample.name)
            .setItems(optionsList.toTypedArray()) { _, which ->
                when (optionsList[which]) {
                    getString(R.string.sf2_copy_sample) -> copySample(sample)
                    getString(R.string.sf2_edit_waveform) -> onSampleEditRequested?.invoke(sample)
                    getString(R.string.sf2_edit_parameters) -> showSampleParametersDialog(sample)
                    getString(R.string.sf2_paste_here) -> pasteToPreset(selectedPresetId)
                    getString(R.string.sf2_delete_sample) -> showDeleteSampleConfirmation(sample)
                }
            }
            .show()
    }

    /**
     * Show advanced SF2 parameters dialog for editing sample synthesis parameters.
     */
    private fun showSampleParametersDialog(sample: Sf2SampleEntity) {
        SampleEditAdvancedDialog(
            context = requireContext(),
            sample = sample,
            onSave = { updatedSample ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateSample(updatedSample)
                    Toast.makeText(requireContext(), R.string.sf2_sample_updated, Toast.LENGTH_SHORT).show()
                    // Regenerate preview to reflect changes
                    generateProjectPreviewSf2()
                }
            }
        ).show()
    }

    private fun copySample(sample: Sf2SampleEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Sf2Clipboard.copySample(requireContext(), sample)
            Toast.makeText(requireContext(), R.string.sf2_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupKeyboard() {
        // Connect keyboard callbacks to active engine with thread-safe access
        keyboardPreview.onNoteOn = { note, velocity ->
            synchronized(engineLock) {
                Log.d(TAG, "Keyboard NoteOn: note=$note, velocity=$velocity, engineReady=$engineReady, useFluidSynth=$useFluidSynth")
                if (engineReady) {
                    if (useFluidSynth) {
                        fluidSynthEngine?.sendNoteOn(0, note, velocity)
                    } else {
                        sf2Engine?.sendNoteOn(0, note, velocity)
                    }
                } else {
                    Log.w(TAG, "Engine not ready, ignoring note on")
                }
            }
        }
        keyboardPreview.onNoteOff = { note ->
            synchronized(engineLock) {
                Log.d(TAG, "Keyboard NoteOff: note=$note, engineReady=$engineReady")
                if (engineReady) {
                    if (useFluidSynth) {
                        fluidSynthEngine?.sendNoteOff(0, note)
                    } else {
                        sf2Engine?.sendNoteOff(0, note)
                    }
                }
            }
        }

        // Octave navigation buttons
        octaveDownButton?.setOnClickListener {
            keyboardPreview.shiftOctaveDown()
            updateOctaveButtonStates()
        }

        octaveUpButton?.setOnClickListener {
            keyboardPreview.shiftOctaveUp()
            updateOctaveButtonStates()
        }

        // Callback when octave changes
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
        if (currentSamplesList.isNotEmpty()) {
            generateProjectPreviewSf2()
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

    private fun setupButtons() {
        addSampleButton.setOnClickListener {
            onAddSampleRequested?.invoke()
        }

        exportProjectButton.setOnClickListener {
            exportProject()
        }

        changeProgramButton.setOnClickListener {
            showProgramSelectionDialog()
        }

        addProgramButton.setOnClickListener {
            showNewProgramDialog()
        }

        addPresetButton.setOnClickListener {
            showNewPresetDialog()
        }
    }

    private fun showProgramSelectionDialog() {
        val preset = currentPresets.find { it.id == selectedPresetId } ?: return
        showProgramSelectionDialogForPreset(preset)
    }

    @Suppress("unused")
    private fun updatePresetProgram(newProgram: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updatePresetProgramNumber(selectedPresetId, newProgram)
            updateProgramDisplay(newProgram)
            // Regenerate preview if needed
            if (currentSamplesList.isNotEmpty()) {
                generateProjectPreviewSf2()
            }
        }
    }

    private fun updateProgramDisplay(programNumber: Int) {
        val instruments = resources.getStringArray(R.array.gm_instruments)
        val instrumentName = if (programNumber in instruments.indices) {
            instruments[programNumber]
        } else {
            "Unknown"
        }
        programText.text = getString(R.string.sf2_program_current, programNumber, instrumentName)
    }

    @Suppress("unused")
    fun setProjectId(id: Long) {
        projectId = id
        if (view != null) {
            loadProject()
        }
    }

    private fun loadProject() {
        viewLifecycleOwner.lifecycleScope.launch {
            val project = repository.getProjectById(projectId)
            if (project == null) {
                Toast.makeText(requireContext(), R.string.sf2_project_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }

            projectNameText.text = project.name

            // Observe programs first
            observePrograms()
        }
    }

    private fun observePrograms() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getProgramsForProjectFlow(projectId).collect { programs ->
                currentPrograms = programs

                // Get instrument counts for each program
                programInstrumentCounts = programs.associate { program ->
                    program.id to repository.getPresetZonesForProgram(program.id).size
                }

                // If no program selected or selected program was deleted, select best program
                if (selectedProgramId <= 0 || programs.none { it.id == selectedProgramId }) {
                    if (programs.isNotEmpty()) {
                        // Prefer a program that has instruments, otherwise select first
                        val programWithInstruments = programs.find { program ->
                            (programInstrumentCounts[program.id] ?: 0) > 0
                        }
                        selectedProgramId = programWithInstruments?.id ?: programs.first().id
                    }
                }

                // Update UI
                updateProgramsChips()

                // Load instruments for selected program
                if (selectedProgramId > 0) {
                    observePresets()
                }
            }
        }
    }

    private fun observePresets() {
        // Cancel previous collection job to avoid multiple collectors
        presetsCollectionJob?.cancel()

        presetsCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            // Get instruments for selected program
            if (selectedProgramId > 0) {
                repository.getPresetZonesForProgramFlow(selectedProgramId).collect { presets ->
                    currentPresets = presets

                    // Get sample counts for each preset zone (via instrument)
                    presetSampleCounts = presets.associate { preset ->
                        preset.id to repository.getSamplesForInstrument(preset.instrumentId).size
                    }

                    // If no preset selected or selected preset was deleted, select first
                    if (selectedPresetId <= 0 || presets.none { it.id == selectedPresetId }) {
                        if (presets.isNotEmpty()) {
                            selectedPresetId = presets.first().id
                        } else {
                            selectedPresetId = -1
                        }
                    }

                    // Update UI
                    updatePresetsChips()
                    updateSelectedPresetInfo()

                    // Load samples for selected preset
                    if (selectedPresetId > 0) {
                        loadSamples()
                    } else {
                        // No presets - show empty state
                        currentSamplesList = emptyList()
                        adapter.submitList(emptyList())
                        updateSamplesCount(0)
                        updateEmptyState(true)
                        updateKeyboardState(emptyList())
                        releaseEngine()
                    }
                }
            } else {
                // Fallback: show all presets if no program selected
                repository.getPresetsForProjectFlow(projectId).collect { presets ->
                    currentPresets = presets
                    presetSampleCounts = repository.getPresetZoneSampleCounts(projectId)

                    if (selectedPresetId <= 0 || presets.none { it.id == selectedPresetId }) {
                        if (presets.isNotEmpty()) {
                            selectedPresetId = presets.first().id
                        } else {
                            selectedPresetId = -1
                        }
                    }

                    updatePresetsChips()
                    updateSelectedPresetInfo()

                    if (selectedPresetId > 0) {
                        loadSamples()
                    } else {
                        currentSamplesList = emptyList()
                        adapter.submitList(emptyList())
                        updateSamplesCount(0)
                        updateEmptyState(true)
                        updateKeyboardState(emptyList())
                        releaseEngine()
                    }
                }
            }
        }
    }

    private fun updateProgramsChips() {
        // Remove all chips except the add button
        val childCount = programsContainer.childCount
        for (i in childCount - 2 downTo 0) {
            programsContainer.removeViewAt(i)
        }

        // Add chip for each program
        val inflater = LayoutInflater.from(requireContext())
        for ((index, program) in currentPrograms.withIndex()) {
            val chipView = inflater.inflate(R.layout.item_program_chip, programsContainer, false)
            val card = chipView.findViewById<MaterialCardView>(R.id.program_chip_card)
            val numberBadge = chipView.findViewById<TextView>(R.id.program_number_badge)
            val nameText = chipView.findViewById<TextView>(R.id.program_name)
            val countText = chipView.findViewById<TextView>(R.id.program_instruments_count)

            // Show program number in badge
            numberBadge.text = program.programNumber.toString()

            nameText.text = program.name
            val instrumentCount = programInstrumentCounts[program.id] ?: 0
            countText.text = getString(R.string.sf2_program_instruments, instrumentCount)

            // Highlight selected program - uses theme accent color
            val isSelected = program.id == selectedProgramId
            if (isSelected) {
                val accentColor = getThemeColor(R.attr.a2uMidiAccent)
                card.setCardBackgroundColor(accentColor)
                nameText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                countText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                numberBadge.setBackgroundResource(R.drawable.bg_rounded_white)
                numberBadge.setTextColor(accentColor)
            }

            card.setOnClickListener {
                selectProgram(program.id)
            }

            card.setOnLongClickListener {
                showProgramOptionsDialog(program)
                true
            }

            // Insert before the add button
            programsContainer.addView(chipView, index)
        }
    }

    private fun updatePresetsChips() {
        // Remove all chips except the add button
        val childCount = presetsContainer.childCount
        for (i in childCount - 2 downTo 0) {
            presetsContainer.removeViewAt(i)
        }

        // Add chip for each preset (instrument)
        val inflater = LayoutInflater.from(requireContext())
        for ((index, preset) in currentPresets.withIndex()) {
            val chipView = inflater.inflate(R.layout.item_preset_chip, presetsContainer, false)
            val card = chipView.findViewById<MaterialCardView>(R.id.preset_chip_card)
            val programBadge = chipView.findViewById<TextView>(R.id.preset_program_badge)
            val nameText = chipView.findViewById<TextView>(R.id.preset_name)
            val countText = chipView.findViewById<TextView>(R.id.preset_samples_count)

            // Hide program badge for instruments (it's shown in parent program)
            programBadge.visibility = View.GONE

            nameText.text = preset.name
            val sampleCount = presetSampleCounts[preset.id] ?: 0
            countText.text = getString(R.string.sf2_chip_samples, sampleCount)

            // Highlight selected preset - uses theme accent color
            val isSelected = preset.id == selectedPresetId
            if (isSelected) {
                card.setCardBackgroundColor(getThemeColor(R.attr.a2uMidiAccent))
                nameText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                countText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }

            card.setOnClickListener {
                selectPreset(preset.id)
            }

            card.setOnLongClickListener {
                showPresetOptionsDialog(preset)
                true
            }

            // Insert before the add button
            presetsContainer.addView(chipView, index)
        }
    }

    private fun selectProgram(programId: Long) {
        if (selectedProgramId != programId) {
            selectedProgramId = programId
            selectedPresetId = -1 // Reset preset selection
            updateProgramsChips()
            observePresets() // Load instruments for this program
        }
    }

    private fun selectPreset(presetId: Long) {
        if (selectedPresetId != presetId) {
            selectedPresetId = presetId
            updatePresetsChips()
            updateSelectedPresetInfo()
            loadSamples()
        }
    }

    private fun updateSelectedPresetInfo() {
        val preset = currentPresets.find { it.id == selectedPresetId }
        if (preset != null) {
            updateProgramDisplay(preset.programNumber)
        }
    }

    private fun showProgramOptionsDialog(program: Sf2ProgramEntity) {
        val instrumentCount = programInstrumentCounts[program.id] ?: 0
        val optionsList = mutableListOf(
            getString(R.string.sf2_edit_program),
            getString(R.string.sf2_edit_program_global_params),
            getString(R.string.sf2_copy_program)
        )

        // Add paste option if clipboard has a program
        if (Sf2Clipboard.hasProgram()) {
            optionsList.add(getString(R.string.sf2_paste_program))
        }

        optionsList.add(getString(R.string.sf2_delete_program))

        // Show program info in title
        val title = "${program.name}\nProgram ${program.programNumber} · Bank ${program.bankNumber} · $instrumentCount instruments"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(optionsList.toTypedArray()) { _, which ->
                when (optionsList[which]) {
                    getString(R.string.sf2_edit_program) -> showEditProgramDialog(program)
                    getString(R.string.sf2_edit_program_global_params) -> showEditProgramGlobalParamsDialog(program)
                    getString(R.string.sf2_copy_program) -> copyProgram(program)
                    getString(R.string.sf2_paste_program) -> pasteProgram()
                    getString(R.string.sf2_delete_program) -> confirmDeleteProgram(program)
                }
            }
            .show()
    }

    private fun pasteProgram() {
        viewLifecycleOwner.lifecycleScope.launch {
            val newProgramId = Sf2Clipboard.pasteProgramToProject(requireContext(), projectId)
            if (newProgramId != null) {
                Toast.makeText(requireContext(), R.string.sf2_pasted_program, Toast.LENGTH_SHORT).show()
                // Select the new program
                selectedProgramId = newProgramId
            } else {
                Toast.makeText(requireContext(), R.string.sf2_paste_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditProgramDialog(program: Sf2ProgramEntity) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_new_program, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.program_name_input)
        val programSpinner = dialogView.findViewById<Spinner>(R.id.program_number_spinner)
        val bankSpinner = dialogView.findViewById<Spinner>(R.id.bank_spinner)

        // Set current values
        nameInput.setText(program.name)

        // Setup program spinner (0-127)
        val instruments = resources.getStringArray(R.array.gm_instruments)
        programSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            instruments
        )
        programSpinner.setSelection(program.programNumber.coerceIn(0, 127))

        // Setup bank spinner (0-127)
        val banks = (0..127).map { "Bank $it" }
        bankSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            banks
        )
        bankSpinner.setSelection(program.bankNumber.coerceIn(0, 127))

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_edit_program)
            .setView(dialogView)
            .setPositiveButton(R.string.sf2_save) { dialog, _ ->
                val newName = nameInput.text?.toString()?.trim() ?: program.name
                val newProgram = programSpinner.selectedItemPosition
                val newBank = bankSpinner.selectedItemPosition

                if (newName.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.updateProgram(
                            program.copy(
                                name = newName,
                                programNumber = newProgram,
                                bankNumber = newBank
                            )
                        )
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.midi_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditProgramGlobalParamsDialog(program: Sf2ProgramEntity) {
        ProgramGlobalEditDialog(requireContext(), program) { updatedProgram ->
            viewLifecycleOwner.lifecycleScope.launch {
                repository.updateProgram(updatedProgram)
                Toast.makeText(requireContext(), R.string.sf2_global_params_saved, Toast.LENGTH_SHORT).show()
                loadProject()
            }
        }.show()
    }

    private fun copyProgram(program: Sf2ProgramEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Sf2Clipboard.copyProgram(requireContext(), program)
            Toast.makeText(requireContext(), R.string.sf2_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteProgram(program: Sf2ProgramEntity) {
        if (currentPrograms.size <= 1) {
            Toast.makeText(requireContext(), R.string.sf2_cannot_delete_last_program, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_delete_program)
            .setMessage(getString(R.string.sf2_delete_program_confirm, program.name))
            .setPositiveButton(R.string.midi_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = repository.deleteProgram(program.id)
                    if (deleted) {
                        Toast.makeText(requireContext(), R.string.sf2_program_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun showNewProgramDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Check if there's an available program number first
            val nextProgram = repository.getNextAvailableProgramNumber(projectId)
            if (nextProgram == null) {
                // All program numbers (0-127) are already used
                android.widget.Toast.makeText(
                    requireContext(),
                    "All program numbers (0-127) are already in use. Cannot create more programs.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_new_program, null)

            val nameInput = dialogView.findViewById<TextInputEditText>(R.id.program_name_input)
            val programSpinner = dialogView.findViewById<Spinner>(R.id.program_number_spinner)
            val bankSpinner = dialogView.findViewById<Spinner>(R.id.bank_spinner)

            // Setup program spinner (0-127)
            val instruments = resources.getStringArray(R.array.gm_instruments)
            programSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                instruments
            )

            // Setup bank spinner (0-127)
            val banks = (0..127).map { "Bank $it" }
            bankSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                banks
            )

            // Pre-select next available program
            programSpinner.setSelection(nextProgram)

            // Default name
            nameInput.setText(getString(R.string.sf2_program_name_default, currentPrograms.size + 1))

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sf2_new_program)
                .setView(dialogView)
                .setPositiveButton(R.string.sf2_confirm) { _, _ ->
                    val name = nameInput.text?.toString()?.trim()
                        ?: getString(R.string.sf2_program_name_default, currentPrograms.size + 1)
                    val programNumber = programSpinner.selectedItemPosition
                    val bankNumber = bankSpinner.selectedItemPosition

                    if (name.isNotEmpty()) {
                        createNewProgram(name, programNumber, bankNumber)
                    }
                }
                .setNegativeButton(R.string.midi_cancel, null)
                .show()
        }
    }

    private fun createNewProgram(name: String, programNumber: Int, bankNumber: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newProgramId = repository.createProgram(projectId, name, programNumber, bankNumber)
            // Select the new program
            selectedProgramId = newProgramId
            selectedPresetId = -1
        }
    }

    private fun showPresetOptionsDialog(preset: Sf2PresetEntity) {
        val sampleCount = presetSampleCounts[preset.id] ?: 0
        val optionsList = mutableListOf(
            getString(R.string.sf2_edit_instrument),
            getString(R.string.sf2_edit_global_params),
            getString(R.string.sf2_program_change),
            getString(R.string.sf2_copy_preset)
        )

        // Add paste option if clipboard has content
        if (Sf2Clipboard.hasContent()) {
            optionsList.add(getString(R.string.sf2_paste_here))
        }

        optionsList.add(getString(R.string.sf2_delete_preset))

        // Show instrument info in title
        val title = "${preset.name}\nProgram ${preset.programNumber} · $sampleCount samples"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(optionsList.toTypedArray()) { _, which ->
                when (optionsList[which]) {
                    getString(R.string.sf2_edit_instrument) -> showEditInstrumentDialog(preset)
                    getString(R.string.sf2_edit_global_params) -> showEditGlobalParamsDialog(preset)
                    getString(R.string.sf2_program_change) -> showProgramSelectionDialogForPreset(preset)
                    getString(R.string.sf2_copy_preset) -> copyPreset(preset)
                    getString(R.string.sf2_paste_here) -> pasteToPreset(preset.id)
                    getString(R.string.sf2_delete_preset) -> confirmDeletePreset(preset)
                }
            }
            .show()
    }

    /**
     * Show dialog to edit an instrument (preset) - rename and change program number.
     */
    private fun showEditInstrumentDialog(preset: Sf2PresetEntity) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_instrument, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.instrument_name_input)
        val programSpinner = dialogView.findViewById<Spinner>(R.id.program_spinner)
        val bankSpinner = dialogView.findViewById<Spinner>(R.id.bank_spinner)
        val sampleCountText = dialogView.findViewById<TextView>(R.id.sample_count_text)

        // Set current values
        nameInput.setText(preset.name)

        // Setup program spinner (0-127)
        val instruments = resources.getStringArray(R.array.gm_instruments)
        programSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            instruments
        )
        programSpinner.setSelection(preset.programNumber.coerceIn(0, 127))

        // Setup bank spinner (0-127)
        val banks = (0..127).map { "Bank $it" }
        bankSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            banks
        )
        bankSpinner.setSelection(preset.bankNumber.coerceIn(0, 127))

        // Show sample count
        val sampleCount = presetSampleCounts[preset.id] ?: 0
        sampleCountText.text = getString(R.string.sf2_instrument_samples, sampleCount)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_edit_instrument)
            .setView(dialogView)
            .setPositiveButton(R.string.sf2_save) { dialog, _ ->
                val newName = nameInput.text?.toString()?.trim() ?: preset.name
                val newProgram = programSpinner.selectedItemPosition
                val newBank = bankSpinner.selectedItemPosition

                if (newName.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.updatePreset(
                            preset.copy(
                                name = newName,
                                programNumber = newProgram,
                                bankNumber = newBank
                            )
                        )
                        loadProject() // Refresh
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.midi_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditGlobalParamsDialog(preset: Sf2PresetEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get the instrument associated with this preset zone
            val instrument = repository.getInstrumentById(preset.instrumentId)
            if (instrument == null) {
                Toast.makeText(requireContext(), "Instrument not found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            InstrumentGlobalEditDialog(requireContext(), instrument) { updatedInstrument ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateInstrument(updatedInstrument)
                    Toast.makeText(requireContext(), R.string.sf2_global_params_saved, Toast.LENGTH_SHORT).show()
                    loadProject()
                }
            }.show()
        }
    }

    private fun copyPreset(preset: Sf2PresetEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val samples = repository.getSamplesForInstrument(preset.instrumentId)
            Sf2Clipboard.copyPreset(requireContext(), preset, samples)
            Toast.makeText(requireContext(), R.string.sf2_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteToPreset(presetId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get the preset to find its instrument
            val preset = repository.getPresetById(presetId) ?: return@launch
            val pastedCount = Sf2Clipboard.pasteToInstrument(requireContext(), preset.instrumentId)
            if (pastedCount > 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sf2_pasted, pastedCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showProgramSelectionDialogForPreset(preset: Sf2PresetEntity) {
        val instruments = resources.getStringArray(R.array.gm_instruments)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_program_label)
            .setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, instruments)
            ) { _, which ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updatePresetProgramNumber(preset.id, which)
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun confirmDeletePreset(preset: Sf2PresetEntity) {
        if (currentPresets.size <= 1) {
            Toast.makeText(requireContext(), R.string.sf2_cannot_delete_last_preset, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_delete_preset)
            .setMessage(getString(R.string.sf2_delete_preset_confirm, preset.name))
            .setPositiveButton(R.string.midi_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = repository.deletePresetZone(preset.id)
                    if (deleted) {
                        Toast.makeText(requireContext(), R.string.sf2_preset_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun showNewPresetDialog() {
        if (selectedProgramId <= 0) {
            Toast.makeText(requireContext(), R.string.sf2_no_programs, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_new_preset, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.preset_name_input)
        val programSpinner = dialogView.findViewById<Spinner>(R.id.program_spinner)
        val programLabel = dialogView.findViewById<TextView>(R.id.program_label)

        // Hide program spinner (instrument inherits from parent program)
        programSpinner.visibility = View.GONE
        programLabel.visibility = View.GONE

        // Default name
        nameInput.setText(getString(R.string.sf2_instrument_name_default, currentPresets.size + 1))

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_add_instrument)
            .setView(dialogView)
            .setPositiveButton(R.string.sf2_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                    ?: getString(R.string.sf2_instrument_name_default, currentPresets.size + 1)

                if (name.isNotEmpty()) {
                    createNewPreset(name)
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun createNewPreset(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get the selected program to inherit its program/bank numbers
            val program = repository.getProgramById(selectedProgramId)
            if (program != null) {
                // First create an instrument for this preset zone
                val instrumentId = repository.createInstrument(
                    projectId = projectId,
                    name = name
                )
                // Then create the preset zone referencing the instrument
                val newPresetId = repository.createPresetZoneForProgram(
                    projectId = projectId,
                    programId = selectedProgramId,
                    instrumentId = instrumentId,
                    name = name,
                    programNumber = program.programNumber,
                    bankNumber = program.bankNumber
                )
                // Select the new preset
                selectedPresetId = newPresetId
            }
        }
    }

    private fun loadSamples() {
        if (selectedPresetId <= 0) return

        // Cancel previous samples collection to avoid multiple collectors
        samplesCollectionJob?.cancel()

        samplesCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            // Get the instrument ID from the selected preset zone
            val presetZone = currentPresets.find { it.id == selectedPresetId }
            if (presetZone == null) return@launch

            repository.getSamplesForInstrumentFlow(presetZone.instrumentId).collect { samples ->
                currentSamplesList = samples
                adapter.submitList(samples)
                updateSamplesCount(samples.size)
                updateEmptyState(samples.isEmpty())
                updateExportButtonState()
                updateKeyboardState(samples)

                // Regenerate preview SF2 when samples change
                if (samples.isNotEmpty()) {
                    generateProjectPreviewSf2()
                } else {
                    releaseEngine()
                }
            }
        }
    }

    private fun updateExportButtonState() {
        // Enable export if any preset has samples
        val totalSamples = presetSampleCounts.values.sum()
        val hasSamples = totalSamples > 0
        exportProjectButton.isEnabled = hasSamples
        exportProjectButton.alpha = if (hasSamples) 1f else 0.5f
    }

    private fun updateSamplesCount(count: Int) {
        samplesCountText.text = getString(R.string.sf2_samples_count, count)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        samplesEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        samplesRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateKeyboardState(samples: List<Sf2SampleEntity>) {
        val hasSamples = samples.isNotEmpty()

        // Show/hide keyboard elements based on sample availability
        keyboardPreview.alpha = if (hasSamples) 1f else 0.3f
        keyboardPreview.isEnabled = hasSamples
        keyboardEmptyHint.visibility = if (hasSamples) View.GONE else View.VISIBLE

        if (hasSamples) {
            // Check if current selection is still valid
            val currentSelectedId = adapter.getSelectedSampleId()
            val selectedSample = samples.find { it.id == currentSelectedId }

            if (selectedSample != null) {
                // Keep current selection and update keyboard
                updateKeyboardForSample(selectedSample)
            } else {
                // Select first sample by root note order
                val firstSample = samples.minByOrNull { it.rootNote }
                firstSample?.let {
                    selectSample(it)
                }
            }
        } else {
            // No samples - clear selection and keyboard
            adapter.clearSelection()
            keyboardPreview.clearKeyRange()
        }
    }

    /**
     * Generate a temporary SF2 file from selected preset samples and initialize Sf2Engine.
     * Includes debouncing to prevent crashes from rapid changes.
     */
    private fun generateProjectPreviewSf2() {
        // Debounce rapid calls
        val now = System.currentTimeMillis()
        if (now - lastPreviewRequestTime < previewDebounceMs) {
            // Schedule a delayed generation instead
            previewGenerationJob?.cancel()
            previewGenerationJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(previewDebounceMs)
                if (isAdded && view != null) {
                    doGeneratePreviewSf2()
                }
            }
            return
        }
        lastPreviewRequestTime = now

        previewGenerationJob?.cancel()
        previewGenerationJob = viewLifecycleOwner.lifecycleScope.launch {
            doGeneratePreviewSf2()
        }
    }

    /**
     * Actually perform the SF2 generation and engine initialization.
     */
    private suspend fun doGeneratePreviewSf2() {
        if (!isAdded || view == null) return

        keyboardLoading.visibility = View.VISIBLE
        engineReady = false

        // Release previous engines safely
        releaseEngineSafe()

        // Clear SF2 file cache to force re-parsing (same path reused)
        Sf2FileCache.clear()

        val sf2File = withContext(Dispatchers.IO) {
            generatePreviewSf2File()
        }

        // Check if fragment is still valid after IO operation
        if (!isAdded || view == null) {
            sf2File?.delete()
            return
        }

        if (sf2File != null) {
            Log.d(TAG, "Generated preview SF2: ${sf2File.absolutePath}, size: ${sf2File.length()} bytes")
            tempSf2File = sf2File

            try {
                if (useFluidSynth && FluidSynthEngine.isSupported()) {
                    // Use FluidSynth engine
                    Log.d(TAG, "Initializing FluidSynth engine...")
                    val engine = FluidSynthEngine(requireContext())
                    val initialized = engine.initialize(sf2File.absolutePath)

                    Log.d(TAG, "FluidSynth initialized: $initialized")
                    if (initialized && isAdded) {
                        engine.startAudioRenderer()
                        synchronized(engineLock) {
                            fluidSynthEngine = engine
                            engineReady = true
                        }
                        Log.d(TAG, "FluidSynth engine ready")
                    } else {
                        Log.e(TAG, "FluidSynth initialization failed")
                        engine.release()
                    }
                } else {
                    // Use SF2 Engine (default)
                    Log.d(TAG, "Initializing SF2 Engine...")
                    val engine = Sf2Engine(requireContext())
                    val initialized = engine.initialize(sf2File.absolutePath)

                    Log.d(TAG, "SF2 Engine initialized: $initialized")
                    if (initialized && isAdded) {
                        engine.startAudioRenderer()
                        synchronized(engineLock) {
                            sf2Engine = engine
                            engineReady = true
                        }
                        Log.d(TAG, "SF2 Engine ready")
                    } else {
                        Log.e(TAG, "SF2 Engine initialization failed")
                        engine.release()
                    }

                    // If FluidSynth was requested but not available, update display
                    if (useFluidSynth) {
                        useFluidSynth = false
                        withContext(Dispatchers.Main) {
                            updateEngineDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle initialization failure gracefully
                Log.e(TAG, "Failed to initialize engine", e)
            }
        } else {
            Log.e(TAG, "Failed to generate preview SF2 file")
        }

        if (isAdded && view != null) {
            keyboardLoading.visibility = View.GONE
        }
    }

    /**
     * Generate a temporary SF2 file from the currently selected preset's samples.
     * This runs on IO dispatcher.
     */
    private suspend fun generatePreviewSf2File(): File? {
        val project = repository.getProjectById(projectId) ?: run {
            Log.e(TAG, "generatePreviewSf2File: project not found")
            return null
        }

        // Get samples ONLY from the selected preset zone's instrument, not all project samples
        val presetId = selectedPresetId
        if (presetId <= 0) {
            Log.e(TAG, "generatePreviewSf2File: no preset selected")
            return null
        }

        val preset = repository.getPresetById(presetId)
        if (preset == null) {
            Log.e(TAG, "generatePreviewSf2File: preset $presetId not found")
            return null
        }

        val samples = repository.getSamplesForInstrument(preset.instrumentId)
        if (samples.isEmpty()) {
            Log.e(TAG, "generatePreviewSf2File: no samples for instrument ${preset.instrumentId}")
            return null
        }

        Log.d(TAG, "generatePreviewSf2File: generating SF2 with ${samples.size} samples")

        // Get preset info for naming
        val presetName = preset?.name ?: project.name

        val sf2Writer = Sf2Writer()

        // Convert entities to InMemorySample objects
        val inMemorySamples = samples.mapNotNull { sampleEntity ->
            // Load audio data: from WAV file if extracted, or from SF2 source if patch-based
            val audioData: ShortArray? = if (sampleEntity.audioFilePath.isNotEmpty()) {
                // Extracted sample - load from WAV file
                val audioFile = File(sampleEntity.audioFilePath)
                if (!audioFile.exists()) {
                    Log.e(TAG, "generatePreviewSf2File: audio file not found: ${sampleEntity.audioFilePath}")
                    return@mapNotNull null
                }
                loadWavFile(audioFile) ?: run {
                    Log.e(TAG, "generatePreviewSf2File: failed to load audio file: ${sampleEntity.audioFilePath}")
                    return@mapNotNull null
                }
            } else if (sampleEntity.sourceFilePath != null && sampleEntity.sourceSmplOffset > 0 && sampleEntity.sourceSampleSize > 0) {
                // Patch-based sample - load from SF2 source file
                val sourceFile = File(sampleEntity.sourceFilePath!!)
                if (!sourceFile.exists()) {
                    Log.e(TAG, "generatePreviewSf2File: SF2 source file not found: ${sampleEntity.sourceFilePath}")
                    return@mapNotNull null
                }
                loadAudioFromSf2Source(sourceFile, sampleEntity.sourceSmplOffset, sampleEntity.sourceSampleSize) ?: run {
                    Log.e(TAG, "generatePreviewSf2File: failed to load audio from SF2 source for sample ${sampleEntity.name}")
                    return@mapNotNull null
                }
            } else {
                Log.e(TAG, "generatePreviewSf2File: sample ${sampleEntity.name} has no audio source (no WAV and no SF2 source)")
                return@mapNotNull null
            }

            if (audioData == null) {
                Log.e(TAG, "generatePreviewSf2File: failed to load audio data for sample ${sampleEntity.name}")
                return@mapNotNull null
            }

            // Both Sf2SampleEntity and InMemorySample use SF2 native units - direct copy
            Sf2Writer.InMemorySample(
                name = sampleEntity.name,
                samples = audioData,
                sampleRate = sampleEntity.sampleRate,
                rootNote = sampleEntity.rootNote,
                keyRangeStart = sampleEntity.keyRangeStart,
                keyRangeEnd = sampleEntity.keyRangeEnd,
                loopStart = sampleEntity.loopStart,
                loopEnd = sampleEntity.loopEnd,
                hasLoop = sampleEntity.hasLoop,
                attenuation = sampleEntity.attenuation,
                fineTuneCents = sampleEntity.fineTuneCents,
                // Volume Envelope - SF2 native units (timecents, centibels)
                volEnvDelay = sampleEntity.volEnvDelay,
                volEnvAttack = sampleEntity.volEnvAttack,
                volEnvHold = sampleEntity.volEnvHold,
                volEnvDecay = sampleEntity.volEnvDecay,
                volEnvSustain = sampleEntity.volEnvSustain,
                volEnvRelease = sampleEntity.volEnvRelease,
                // Filter - SF2 native units (absolute cents)
                filterFc = sampleEntity.filterFc,
                filterQ = sampleEntity.filterQ,
                chorusSend = sampleEntity.chorusSend,
                reverbSend = sampleEntity.reverbSend,
                pan = sampleEntity.pan,
                // Advanced SF2 parameters for accurate preview
                velRangeStart = sampleEntity.velRangeStart,
                velRangeEnd = sampleEntity.velRangeEnd,
                coarseTune = sampleEntity.coarseTune,
                scaleTuning = sampleEntity.scaleTuning,
                // Modulation Envelope - SF2 native units
                modEnvDelay = sampleEntity.modEnvDelay,
                modEnvAttack = sampleEntity.modEnvAttack,
                modEnvHold = sampleEntity.modEnvHold,
                modEnvDecay = sampleEntity.modEnvDecay,
                modEnvSustain = sampleEntity.modEnvSustain,
                modEnvRelease = sampleEntity.modEnvRelease,
                modEnvToPitch = sampleEntity.modEnvToPitch,
                modEnvToFilterFc = sampleEntity.modEnvToFilterFc,
                // LFOs - SF2 native units
                vibLfoDelay = sampleEntity.vibLfoDelay,
                vibLfoFreq = sampleEntity.vibLfoFreq,
                vibLfoToPitch = sampleEntity.vibLfoToPitch,
                modLfoDelay = sampleEntity.modLfoDelay,
                modLfoFreq = sampleEntity.modLfoFreq,
                modLfoToPitch = sampleEntity.modLfoToPitch,
                modLfoToFilterFc = sampleEntity.modLfoToFilterFc,
                modLfoToVolume = sampleEntity.modLfoToVolume,
                exclusiveClass = sampleEntity.exclusiveClass,
                keyToVolEnvHold = sampleEntity.keyToVolEnvHold,
                keyToVolEnvDecay = sampleEntity.keyToVolEnvDecay,
                keyToModEnvHold = sampleEntity.keyToModEnvHold,
                keyToModEnvDecay = sampleEntity.keyToModEnvDecay,
                fixedKey = sampleEntity.fixedKey,
                fixedVelocity = sampleEntity.fixedVelocity,
                pitchCorrection = sampleEntity.pitchCorrection
            )
        }

        if (inMemorySamples.isEmpty()) {
            Log.e(TAG, "generatePreviewSf2File: no valid samples after loading audio files")
            return null
        }

        Log.d(TAG, "generatePreviewSf2File: loaded ${inMemorySamples.size} in-memory samples")

        // Use a unique filename per preset to avoid conflicts
        val context = try { requireContext() } catch (_: Exception) {
            Log.e(TAG, "generatePreviewSf2File: context not available")
            return null
        }
        val tempDir = context.cacheDir
        val tempFile = File(tempDir, "preset_preview_${projectId}_${presetId}.sf2")

        Log.d(TAG, "generatePreviewSf2File: writing to ${tempFile.absolutePath}")

        val success = sf2Writer.writeSf2FromMemorySamples(
            outputFile = tempFile,
            instrumentName = presetName.take(19),
            samples = inMemorySamples,
            presetNumber = 0,
            bankNumber = 0
        )

        Log.d(TAG, "generatePreviewSf2File: write success=$success")

        if (success) {
            val isValid = sf2Writer.validateSf2(tempFile)
            Log.d(TAG, "generatePreviewSf2File: validation=$isValid, file size=${tempFile.length()}")
            return if (isValid) tempFile else null
        }

        return null
    }

    /**
     * Load samples from a WAV file.
     * Delegates to WavUtils.
     */
    private fun loadWavFile(file: File): ShortArray? = WavUtils.loadWavFile(file)

    /**
     * Load audio samples directly from SF2 source file at a specific offset.
     * Used for patch-based samples that haven't been extracted to WAV files.
     */
    private fun loadAudioFromSf2Source(sourceFile: File, offset: Long, size: Long): ShortArray? {
        if (!sourceFile.exists() || size <= 0) return null

        return try {
            java.io.RandomAccessFile(sourceFile, "r").use { raf ->
                raf.seek(offset)
                val numSamples = (size / 2).toInt()
                val samples = ShortArray(numSamples)
                val buffer = ByteArray(size.toInt())
                val bytesRead = raf.read(buffer)

                if (bytesRead < size) return null

                // Convert bytes to shorts (little-endian, SF2 format)
                for (i in 0 until numSamples) {
                    val lo = buffer[i * 2].toInt() and 0xFF
                    val hi = buffer[i * 2 + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort()
                }
                samples
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio from SF2 source", e)
            null
        }
    }

    /**
     * Release the engines synchronously (for use in onDestroyView).
     */
    private fun releaseEngine() {
        synchronized(engineLock) {
            engineReady = false
            sf2Engine?.let { engine ->
                try {
                    engine.stopAudioRenderer()
                    engine.release()
                } catch (e: Exception) {
                    Log.e("ProjectDetailFragment", "Error releasing SF2 engine", e)
                }
            }
            sf2Engine = null

            fluidSynthEngine?.let { engine ->
                try {
                    engine.stopAudioRenderer()
                    engine.release()
                } catch (e: Exception) {
                    Log.e("ProjectDetailFragment", "Error releasing FluidSynth engine", e)
                }
            }
            fluidSynthEngine = null
        }
    }

    /**
     * Release the engines safely on IO dispatcher (for use during regeneration).
     */
    private suspend fun releaseEngineSafe() = withContext(Dispatchers.IO) {
        synchronized(engineLock) {
            engineReady = false
            sf2Engine?.let { engine ->
                try {
                    engine.stopAudioRenderer()
                    engine.release()
                } catch (e: Exception) {
                    Log.e("ProjectDetailFragment", "Error releasing SF2 engine", e)
                }
            }
            sf2Engine = null

            fluidSynthEngine?.let { engine ->
                try {
                    engine.stopAudioRenderer()
                    engine.release()
                } catch (e: Exception) {
                    Log.e("ProjectDetailFragment", "Error releasing FluidSynth engine", e)
                }
            }
            fluidSynthEngine = null
        }
    }

    private fun showDeleteSampleConfirmation(sample: Sf2SampleEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_delete_sample)
            .setMessage(getString(R.string.sf2_delete_sample_confirm, sample.name))
            .setPositiveButton(R.string.midi_delete) { _, _ ->
                deleteSample(sample)
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun deleteSample(sample: Sf2SampleEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteSample(sample.id)
        }
    }

    /**
     * Launch the system file picker to let user choose where to save the SF2 file.
     */
    private fun exportProject() {
        viewLifecycleOwner.lifecycleScope.launch {
            val project = repository.getProjectById(projectId)
            if (project == null) {
                Toast.makeText(requireContext(), R.string.sf2_project_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Sanitize filename and launch SAF picker
            val sanitizedName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            exportLauncher.launch("$sanitizedName.sf2")
        }
    }

    /**
     * Write the SF2 file to the user-selected URI.
     */
    private fun exportToUri(uri: Uri) {
        showProgress(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Create a temporary file first
                    val tempFile = File(requireContext().cacheDir, "temp_project_export.sf2")

                    val exportSuccess = repository.exportProjectToSf2(projectId, tempFile)

                    if (exportSuccess && Sf2Writer().validateSf2(tempFile)) {
                        // Copy temp file to user-selected URI
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

            showProgress(false)

            if (success) {
                Toast.makeText(requireContext(), R.string.sf2_save_as_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.sf2_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Show progress overlay with a custom message.
     * Called by the activity during SF2 import parsing.
     */
    fun showProgress(message: String) {
        progressText.text = message
        progressOverlay.visibility = View.VISIBLE
    }

    /**
     * Hide the progress overlay.
     */
    fun hideProgress() {
        progressOverlay.visibility = View.GONE
    }

    /**
     * Force regeneration of the preview SF2.
     * Call this after editing a sample.
     */
    @Suppress("unused")
    fun refreshPreview() {
        if (currentSamplesList.isNotEmpty()) {
            generateProjectPreviewSf2()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presetsCollectionJob?.cancel()
        samplesCollectionJob?.cancel()
        previewGenerationJob?.cancel()
        releaseEngine()
        tempSf2File?.delete()
        tempSf2File = null
    }

    /**
     * Get the currently selected preset ID.
     * Used by the activity when adding samples.
     */
    @Suppress("unused")
    fun getSelectedPresetId(): Long = selectedPresetId

    /**
     * Get color from theme attribute.
     * Uses the dynamic theme system instead of hardcoded colors.
     */
    private fun getThemeColor(@AttrRes attrResId: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attrResId, typedValue, true)
        return typedValue.data
    }

    companion object {
        private const val TAG = "ProjectDetailFragment"

        fun newInstance(projectId: Long): ProjectDetailFragment {
            return ProjectDetailFragment().apply {
                this.projectId = projectId
            }
        }
    }
}
