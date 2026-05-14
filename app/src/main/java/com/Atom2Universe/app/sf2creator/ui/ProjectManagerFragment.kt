package com.Atom2Universe.app.sf2creator.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for managing SF2 projects.
 * Shows list of existing projects and allows creating new ones.
 *
 * Each sample added to a project represents ONE note (keyRange = rootNote).
 * If the user tries to add a sample with a note that already exists,
 * we show a conflict dialog.
 */
class ProjectManagerFragment : Fragment() {

    private lateinit var projectsRecycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var newProjectButton: Button
    private lateinit var importSf2Button: Button

    private lateinit var repository: Sf2ProjectRepository
    private lateinit var adapter: ProjectAdapter

    // Callbacks
    var onProjectSelected: ((Long) -> Unit)? = null
    var onNewProjectCreated: ((Long) -> Unit)? = null
    var onRedefineNoteRequested: (() -> Unit)? = null // Called when user wants to change the note
    var onImportSf2Requested: ((Uri) -> Unit)? = null // Called when user wants to import an SF2 file as a new project

    // SF2 import launcher
    private val sf2ImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportSf2Requested?.invoke(it) }
    }

    // If set, adds the sample to the selected project
    var pendingSampleToAdd: PendingSample? = null

    data class PendingSample(
        val name: String,
        val samples: ShortArray,
        val sampleRate: Int,
        val rootNote: Int,
        val keyRangeStart: Int,
        val keyRangeEnd: Int,
        val loopStart: Int,
        val loopEnd: Int,
        val hasLoop: Boolean,
        val attenuation: Int,
        val fineTuneCents: Int,
        val attackMs: Int,
        val decayMs: Int,
        val sustainPercent: Int,
        val releaseMs: Int,
        val filterCutoffHz: Float,
        val filterResonanceCb: Int,
        val chorusSend: Int,
        val reverbSend: Int,
        val pan: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_project_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = Sf2ProjectRepository(requireContext())

        findViews(view)
        setupAdapter()
        setupButtons()
        observeProjects()
    }

    private fun findViews(view: View) {
        projectsRecycler = view.findViewById(R.id.projects_recycler)
        emptyState = view.findViewById(R.id.empty_state)
        newProjectButton = view.findViewById(R.id.new_project_button)
        importSf2Button = view.findViewById(R.id.import_sf2_button)
    }

    private fun setupAdapter() {
        adapter = ProjectAdapter(
            onProjectClick = { project ->
                handleProjectSelected(project)
            },
            onDeleteClick = { project ->
                showDeleteConfirmation(project)
            }
        )
        projectsRecycler.adapter = adapter
    }

    private fun setupButtons() {
        newProjectButton.setOnClickListener {
            showCreateProjectDialog()
        }

        importSf2Button.setOnClickListener {
            // Launch file picker for SF2 files
            sf2ImportLauncher.launch(arrayOf("*/*"))
        }
    }

    private fun observeProjects() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getAllProjectsFlow().collectLatest { projects ->
                // Get sample counts for each project
                val projectsWithCounts = projects.map { project ->
                    val count = repository.getSampleCountForProject(project.id)
                    ProjectWithCount(project, count)
                }

                adapter.submitList(projectsWithCounts)
                updateEmptyState(projectsWithCounts.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        projectsRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showCreateProjectDialog() {
        val context = requireContext()
        val editText = EditText(context).apply {
            hint = getString(R.string.sf2_project_name_hint)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.sf2_new_project)
            .setView(editText)
            .setPositiveButton(R.string.midi_create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createProject(name)
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun createProject(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val projectId = repository.createProject(name)

            // If we have a pending sample, add it to the new project
            // New project = no conflict possible
            val pending = pendingSampleToAdd
            if (pending != null) {
                addSampleToProject(projectId, pending)
                pendingSampleToAdd = null
            }

            onNewProjectCreated?.invoke(projectId)
        }
    }

    private fun handleProjectSelected(project: Sf2ProjectEntity) {
        val pending = pendingSampleToAdd
        if (pending != null) {
            // Check if the key range overlaps with existing samples in this project
            viewLifecycleOwner.lifecycleScope.launch {
                val overlappingSamples = repository.findOverlappingSamplesInProject(
                    project.id,
                    pending.keyRangeStart,
                    pending.keyRangeEnd
                )

                if (overlappingSamples.isNotEmpty()) {
                    // Range conflict! Show dialog
                    showRangeConflictDialog(project, pending, overlappingSamples)
                } else {
                    // No conflict, add the sample
                    addSampleToProject(project.id, pending)
                    pendingSampleToAdd = null
                    onProjectSelected?.invoke(project.id)
                }
            }
        } else {
            // Just navigate to project detail
            onProjectSelected?.invoke(project.id)
        }
    }

    /**
     * Show a dialog when the user tries to add a sample with a key range
     * that overlaps with existing samples in the project.
     */
    private fun showRangeConflictDialog(
        project: Sf2ProjectEntity,
        pending: PendingSample,
        overlapping: List<com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity>
    ) {
        val startName = SampleData.midiNoteToName(pending.keyRangeStart)
        val endName = SampleData.midiNoteToName(pending.keyRangeEnd)
        val rangeText = if (pending.keyRangeStart == pending.keyRangeEnd) {
            startName
        } else {
            "$startName – $endName"
        }

        val conflictingNames = overlapping.joinToString(", ") { it.name }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_note_conflict_title)
            .setMessage(getString(R.string.sf2_range_conflict) + "\n\n" +
                    "Range: $rangeText\n" +
                    "Conflicts with: $conflictingNames")
            .setPositiveButton(R.string.sf2_choose_other_project) { _, _ ->
                // User wants to choose another project - do nothing, stay on this screen
            }
            .setNegativeButton(R.string.sf2_redefine_note) { _, _ ->
                // User wants to go back and change the note/range
                pendingSampleToAdd = null
                onRedefineNoteRequested?.invoke()
            }
            .setNeutralButton(R.string.midi_cancel, null)
            .show()
    }

    private suspend fun addSampleToProject(projectId: Long, pending: PendingSample) {
        val preset = repository.getOrCreateDefaultPreset(projectId)

        // Use the key range from SampleParams (can be single note or full range)
        // Convert UI units (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
        // Samples are now added to the instrument, not the preset zone
        repository.addSampleToInstrument(
            instrumentId = preset.instrumentId,
            name = pending.name,
            samples = pending.samples,
            sampleRate = pending.sampleRate,
            rootNote = pending.rootNote,
            keyRangeStart = pending.keyRangeStart,
            keyRangeEnd = pending.keyRangeEnd,
            loopStart = pending.loopStart,
            loopEnd = pending.loopEnd,
            hasLoop = pending.hasLoop,
            attenuation = pending.attenuation,
            fineTuneCents = pending.fineTuneCents,
            // Volume Envelope - convert ms to timecents, % to centibels
            volEnvAttack = Sf2UnitConverter.msToTimecents(pending.attackMs),
            volEnvDecay = Sf2UnitConverter.msToTimecents(pending.decayMs),
            volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(pending.sustainPercent),
            volEnvRelease = Sf2UnitConverter.msToTimecents(pending.releaseMs),
            // Filter - convert Hz to cents
            filterFc = Sf2UnitConverter.hzToFilterCents(pending.filterCutoffHz),
            filterQ = pending.filterResonanceCb,
            chorusSend = pending.chorusSend,
            reverbSend = pending.reverbSend,
            pan = pending.pan
        )
    }

    private fun showDeleteConfirmation(project: Sf2ProjectEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sf2_delete_project)
            .setMessage(getString(R.string.sf2_delete_project_confirm, project.name))
            .setPositiveButton(R.string.midi_delete) { _, _ ->
                deleteProject(project)
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun deleteProject(project: Sf2ProjectEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteProject(project.id)
        }
    }

    companion object {
        fun newInstance(): ProjectManagerFragment {
            return ProjectManagerFragment()
        }
    }
}
