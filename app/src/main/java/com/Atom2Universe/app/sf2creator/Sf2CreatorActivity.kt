package com.Atom2Universe.app.sf2creator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.sf2creator.data.PitchResult
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult
import com.Atom2Universe.app.sf2creator.ui.ExportFragment
import com.Atom2Universe.app.sf2creator.ui.PitchSelectionDialog
import com.Atom2Universe.app.sf2creator.ui.ProjectDetailFragment
import com.Atom2Universe.app.sf2creator.ui.ProjectManagerFragment
import com.Atom2Universe.app.sf2creator.ui.RecordSampleFragment
import com.Atom2Universe.app.sf2creator.ui.SampleEditorFragment
import com.Atom2Universe.app.sf2creator.ui.Sf2ImportFragment
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import com.Atom2Universe.app.sf2creator.util.WavUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.util.enableImmersiveMode
import java.io.File

/**
 * Main activity for the SF2 Creator module.
 * Guides the user through recording a sample, detecting pitch, and exporting an SF2 file.
 *
 * Flow:
 * 1. Record sample (RecordSampleFragment) - pitch auto-detected
 * 2. Edit sample (SampleEditorFragment) - trim, loop, process
 * 3. Name and export (ExportFragment) OR add to project
 *
 * Project flow:
 * - From Export: "Add to Project" → ProjectManager → select/create project → ProjectDetail
 * - From ProjectDetail: "Add sample" → back to Record flow
 */
class Sf2CreatorActivity : ThemedActivity() {

    private lateinit var titleText: TextView
    private lateinit var stepIndicator: TextView
    private lateinit var backButton: ImageButton

    // Current state
    private var currentStep = Step.RECORD
    private var recordedFile: File? = null
    private var recordedSamples: ShortArray? = null
    private var selectedPitch: Int = 60 // Default C4

    // All export parameters (saved when navigating between pages)
    private var savedExportParams: ExportFragment.SampleParams? = null

    // Project state
    private var currentProjectId: Long? = null
    private var pendingSampleParams: ExportFragment.SampleParams? = null
    private var returnToProjectAfterRecord: Boolean = false

    // Sample editing state (when editing existing sample in project)
    private var editingSampleEntity: Sf2SampleEntity? = null
    private var editingSampleAudio: ShortArray? = null

    // Fragments
    private var recordFragment: RecordSampleFragment? = null
    private var editorFragment: SampleEditorFragment? = null
    private var exportFragment: ExportFragment? = null
    private var projectManagerFragment: ProjectManagerFragment? = null
    private var projectDetailFragment: ProjectDetailFragment? = null
    private var sf2ImportFragment: Sf2ImportFragment? = null

    // SF2 import launcher
    private val sf2ImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleSf2Import(it) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_sf2_creator)

        findViews()
        setupBackButton()
        setupBackPressedCallback()

        if (savedInstanceState == null) {
            showRecordStep()
        }

        updateUI()
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentStep) {
                    Step.EXPORT -> {
                        // Save ALL export parameters before going back
                        exportFragment?.getSampleParams()?.let { params ->
                            savedExportParams = params
                        }
                        showEditStep()
                    }
                    Step.EDIT -> showRecordStep()
                    Step.RECORD -> {
                        if (returnToProjectAfterRecord && currentProjectId != null) {
                            // Return to project detail instead of exiting
                            returnToProjectAfterRecord = false
                            showProjectDetail(currentProjectId!!)
                        } else {
                            navigateBackToHub()
                        }
                    }
                    Step.PROJECT_MANAGER -> {
                        // If we came from Export with a pending sample, go back to export
                        if (pendingSampleParams != null) {
                            pendingSampleParams = null
                            showExportStep()
                        } else {
                            showRecordStep()
                        }
                    }
                    Step.PROJECT_DETAIL -> {
                        currentProjectId = null
                        showProjectManager()
                    }
                    Step.EDIT_PROJECT_SAMPLE -> {
                        // Cancel editing and return to project
                        editingSampleEntity = null
                        editingSampleAudio = null
                        currentProjectId?.let { showProjectDetail(it) }
                            ?: showProjectManager()
                    }
                    Step.SF2_IMPORT -> {
                        // Cancel import and return to project detail
                        sf2ImportFragment = null
                        currentProjectId?.let { showProjectDetail(it) }
                            ?: showProjectManager()
                    }
                }
            }
        })
    }

    private fun findViews() {
        titleText = findViewById(R.id.title_text)
        stepIndicator = findViewById(R.id.step_indicator)
        backButton = findViewById(R.id.back_button)
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showRecordStep() {
        currentStep = Step.RECORD

        val fragment = RecordSampleFragment.newInstance()
        fragment.onSampleRecorded = { file, samples, pitch ->
            recordedFile = file
            recordedSamples = samples
            selectedPitch = pitch.midiNote
            showEditStep()
        }
        fragment.onProjectsRequested = {
            // Navigate to project manager
            showProjectManager()
        }
        recordFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    private fun showEditStep() {
        currentStep = Step.EDIT

        val samples = recordedSamples
        if (samples == null) {
            showRecordStep()
            return
        }

        val fragment = SampleEditorFragment()
        fragment.onEditCompleteListener = { editedSamples, loopStart, loopEnd, hasLoop, adsrPreset ->
            // Store edited samples
            recordedSamples = editedSamples

            // Update savedExportParams with new loop/ADSR values from editor
            // Keep other params if they exist, otherwise they'll be defaults
            savedExportParams = savedExportParams?.copy(
                samples = editedSamples,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                attackMs = adsrPreset.attackMs.toInt(),
                decayMs = adsrPreset.decayMs.toInt(),
                sustainPercent = (adsrPreset.sustainLevel * 100).toInt(),
                releaseMs = adsrPreset.releaseMs.toInt()
            ) ?: ExportFragment.SampleParams(
                name = "Sample",
                samples = editedSamples,
                sampleRate = 44100,
                rootNote = selectedPitch,
                keyRangeStart = selectedPitch,
                keyRangeEnd = selectedPitch,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                attenuation = 0,
                fineTuneCents = 0,
                attackMs = adsrPreset.attackMs.toInt(),
                decayMs = adsrPreset.decayMs.toInt(),
                sustainPercent = (adsrPreset.sustainLevel * 100).toInt(),
                releaseMs = adsrPreset.releaseMs.toInt(),
                filterCutoffHz = 20000f,
                filterResonanceCb = 0,
                chorusSend = 0,
                reverbSend = 0,
                pan = 0
            )

            showExportStep()
        }
        editorFragment = fragment

        replaceFragment(fragment)
        updateUI()

        // Restore previous edit state BEFORE setting sample data
        // This ensures pending values are set before processAndDisplay runs
        savedExportParams?.let { params ->
            // Convert ADSR values back to preset format
            val adsrPreset = com.Atom2Universe.app.sf2creator.audio.EnvelopeGenerator.ADSRSettings(
                attackMs = params.attackMs.toFloat(),
                decayMs = params.decayMs.toFloat(),
                sustainLevel = params.sustainPercent / 100f,
                releaseMs = params.releaseMs.toFloat(),
                requiresLoop = params.hasLoop
            )
            fragment.restoreEditState(
                loopStart = params.loopStart,
                loopEnd = params.loopEnd,
                hasLoop = params.hasLoop,
                adsrPreset = adsrPreset
            )
        }

        // Set sample data after fragment is attached (this triggers processAndDisplay)
        fragment.setSampleData(samples, selectedPitch)
    }

    private fun showExportStep() {
        currentStep = Step.EXPORT

        val file = recordedFile
        val samples = recordedSamples

        if (file == null || samples == null) {
            showRecordStep()
            return
        }

        val fragment = ExportFragment.newInstance()
        fragment.setSampleData(file, samples, selectedPitch)

        // Restore ALL saved parameters if available (coming back from Edit page)
        savedExportParams?.let { params ->
            fragment.restoreAllParams(params)
        }
        fragment.onExportComplete = { _ ->
            // Export complete - could show success message or return to hub
        }
        fragment.onChangeNote = {
            // Allow changing the note
            samples.let { s ->
                PitchSelectionDialog(
                    context = this,
                    detectedPitch = PitchResult.fromFrequency(
                        com.Atom2Universe.app.sf2creator.data.SampleData.midiNoteToFrequency(selectedPitch),
                        1.0f
                    ),
                    samples = s
                ) { selectedNote ->
                    selectedPitch = selectedNote
                    exportFragment?.updateRootNote(selectedNote)
                }.show()
            }
        }
        fragment.onAddToProject = {
            // Get current sample parameters and navigate to project manager
            val params = fragment.getSampleParams()
            if (params != null) {
                pendingSampleParams = params
                showProjectManager()
            }
        }
        exportFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    private fun showProjectManager() {
        currentStep = Step.PROJECT_MANAGER

        val fragment = ProjectManagerFragment.newInstance()

        // If we have a pending sample, pass it to the fragment
        // Now supports keyRangeStart/End from SampleParams
        pendingSampleParams?.let { params ->
            fragment.pendingSampleToAdd = ProjectManagerFragment.PendingSample(
                name = params.name,
                samples = params.samples,
                sampleRate = params.sampleRate,
                rootNote = params.rootNote,
                keyRangeStart = params.keyRangeStart,
                keyRangeEnd = params.keyRangeEnd,
                loopStart = params.loopStart,
                loopEnd = params.loopEnd,
                hasLoop = params.hasLoop,
                attenuation = params.attenuation,
                fineTuneCents = params.fineTuneCents,
                attackMs = params.attackMs,
                decayMs = params.decayMs,
                sustainPercent = params.sustainPercent,
                releaseMs = params.releaseMs,
                filterCutoffHz = params.filterCutoffHz,
                filterResonanceCb = params.filterResonanceCb,
                chorusSend = params.chorusSend,
                reverbSend = params.reverbSend,
                pan = params.pan
            )
        }

        fragment.onProjectSelected = { projectId ->
            if (pendingSampleParams != null) {
                // Sample was added to the project
                Toast.makeText(this, R.string.sf2_sample_added, Toast.LENGTH_SHORT).show()
                pendingSampleParams = null
            }
            currentProjectId = projectId
            showProjectDetail(projectId)
        }

        fragment.onNewProjectCreated = { projectId ->
            if (pendingSampleParams != null) {
                // Sample was added to the new project
                Toast.makeText(this, R.string.sf2_sample_added, Toast.LENGTH_SHORT).show()
                pendingSampleParams = null
            }
            currentProjectId = projectId
            showProjectDetail(projectId)
        }

        fragment.onRedefineNoteRequested = {
            // User wants to go back to Export to change the note
            pendingSampleParams = null
            showExportStep()
        }

        fragment.onImportSf2Requested = { uri ->
            // Import SF2 as a new project (1 SF2 = 1 Project architecture)
            handleSf2ImportAsNewProject(uri)
        }

        projectManagerFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    private fun showProjectDetail(projectId: Long) {
        currentStep = Step.PROJECT_DETAIL
        currentProjectId = projectId

        val fragment = ProjectDetailFragment.newInstance(projectId)

        fragment.onAddSampleRequested = {
            // Start the record flow, but mark that we should return to project
            returnToProjectAfterRecord = true
            showRecordStep()
        }

        fragment.onExportComplete = { exportedFile ->
            Toast.makeText(
                this,
                getString(R.string.sf2_saved_to, exportedFile.absolutePath),
                Toast.LENGTH_LONG
            ).show()
        }

        fragment.onSampleEditRequested = { sample ->
            // Load audio data and open full editor
            CoroutineScope(Dispatchers.Main).launch {
                val audioData = withContext(Dispatchers.IO) {
                    loadWavFile(File(sample.audioFilePath))
                }
                if (audioData != null) {
                    editingSampleEntity = sample
                    editingSampleAudio = audioData
                    showEditProjectSample(sample, audioData)
                } else {
                    Toast.makeText(
                        this@Sf2CreatorActivity,
                        R.string.sf2_sample_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        projectDetailFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    /**
     * Handle SF2 file import from content URI.
     * Copies the file to a temporary location for RandomAccessFile access,
     * parses it, and shows the import fragment.
     */
    private fun handleSf2Import(uri: Uri) {
        val projectId = currentProjectId ?: return

        CoroutineScope(Dispatchers.Main).launch {
            // Show progress
            projectDetailFragment?.showProgress(getString(R.string.sf2_parsing_file))

            val importer = Sf2Importer(this@Sf2CreatorActivity)

            // Copy file to temp location for RandomAccessFile access
            // This is necessary because content URIs don't support RandomAccessFile
            val tempFile = withContext(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: return@withContext null

                    // Create cache directory for imports
                    val cacheDir = File(cacheDir, "sf2_import")
                    cacheDir.mkdirs()

                    // Get original filename if possible
                    val fileName = getFileNameFromUri(uri) ?: "import_temp.sf2"
                    val tempFile = File(cacheDir, fileName)

                    // Copy file (streaming for large files)
                    tempFile.outputStream().buffered().use { output ->
                        inputStream.buffered().use { input ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }

                    tempFile
                } catch (e: Exception) {
                    android.util.Log.e("Sf2CreatorActivity", "Failed to copy SF2 file", e)
                    null
                }
            }

            if (tempFile == null) {
                projectDetailFragment?.hideProgress()
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Parse the SF2 file (lazy loading - only parses structure, not audio data)
            val parseResult = withContext(Dispatchers.IO) {
                importer.parseFile(tempFile)
            }

            projectDetailFragment?.hideProgress()

            if (parseResult == null) {
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
                tempFile.delete()
                return@launch
            }

            // Show import fragment
            showSf2Import(parseResult, tempFile, projectId)
        }
    }

    /**
     * Handle SF2 file import as a new project (1 SF2 = 1 Project architecture).
     * This is the "Polyphone-like" approach where each SF2 becomes its own project.
     */
    private fun handleSf2ImportAsNewProject(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            // Show progress (using a toast since we don't have a progress overlay here)
            Toast.makeText(
                this@Sf2CreatorActivity,
                R.string.sf2_parsing_file,
                Toast.LENGTH_SHORT
            ).show()

            val importer = Sf2Importer(this@Sf2CreatorActivity)

            // Copy file to temp location for RandomAccessFile access
            val tempFile = withContext(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: return@withContext null

                    // Create cache directory for imports
                    val importCacheDir = File(cacheDir, "sf2_import")
                    importCacheDir.mkdirs()

                    // Get original filename if possible
                    val fileName = getFileNameFromUri(uri) ?: "import_temp.sf2"
                    val file = File(importCacheDir, fileName)

                    // Copy file (streaming for large files)
                    file.outputStream().buffered().use { output ->
                        inputStream.buffered().use { input ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }

                    file
                } catch (e: Exception) {
                    android.util.Log.e("Sf2CreatorActivity", "Failed to copy SF2 file", e)
                    null
                }
            }

            if (tempFile == null) {
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Check file size - limit to 512MB to avoid OutOfMemoryError
            // The sampleIndexMapping JSON stored in SQLite can cause OOM for very large SF2 files
            val maxSizeBytes = 512L * 1024 * 1024 // 512 MB
            if (tempFile.length() > maxSizeBytes) {
                val sizeMB = tempFile.length() / (1024 * 1024)
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    getString(R.string.sf2_file_too_large, sizeMB, maxSizeBytes / (1024 * 1024)),
                    Toast.LENGTH_LONG
                ).show()
                tempFile.delete()
                return@launch
            }

            // Parse the SF2 file
            val parseResult = withContext(Dispatchers.IO) {
                importer.parseFile(tempFile)
            }

            if (parseResult == null) {
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
                tempFile.delete()
                return@launch
            }

            // Create an empty project with the SF2 file name (without extension)
            // Using createEmptyProject to avoid default entities that would prevent
            // exact match detection for hybrid/direct copy export
            val projectName = tempFile.nameWithoutExtension
            val projectId = withContext(Dispatchers.IO) {
                Sf2ProjectRepository(this@Sf2CreatorActivity).createEmptyProject(projectName)
            }

            currentProjectId = projectId

            // Show the import fragment to select which presets to import
            showSf2Import(parseResult, tempFile, projectId)
        }
    }

    /**
     * Get filename from content URI.
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Show the SF2 import fragment for selecting presets to import.
     */
    private fun showSf2Import(parseResult: Sf2ParseResult, tempFile: File, projectId: Long) {
        currentStep = Step.SF2_IMPORT

        val fragment = Sf2ImportFragment.newInstance(projectId)
        fragment.setParseResult(parseResult, projectId)

        fragment.onImportComplete = { _ ->
            // Clean up temp file
            tempFile.delete()
            // Return to project detail and refresh
            showProjectDetail(projectId)
        }

        fragment.onCancel = {
            // Clean up temp file
            tempFile.delete()
            // Return to project detail
            showProjectDetail(projectId)
        }

        sf2ImportFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    /**
     * Show the sample editor for editing an existing sample in a project.
     */
    private fun showEditProjectSample(sample: Sf2SampleEntity, audioData: ShortArray) {
        currentStep = Step.EDIT_PROJECT_SAMPLE

        val fragment = SampleEditorFragment()
        fragment.onEditCompleteListener = { editedSamples, loopStart, loopEnd, hasLoop, adsrPreset ->
            // Audio editing complete - now show export fragment in edit mode
            editingSampleAudio = editedSamples

            // Update the sample entity with new loop points and ADSR
            // Convert UI units (ms, %) to SF2 native units (timecents, centibels)
            val updatedSample = sample.copy(
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                volEnvAttack = Sf2UnitConverter.msToTimecents(adsrPreset.attackMs.toInt()),
                volEnvDecay = Sf2UnitConverter.msToTimecents(adsrPreset.decayMs.toInt()),
                volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels((adsrPreset.sustainLevel * 100).toInt()),
                volEnvRelease = Sf2UnitConverter.msToTimecents(adsrPreset.releaseMs.toInt())
            )
            editingSampleEntity = updatedSample

            // Show export fragment in edit mode (with keyboard preview)
            showExportForSampleEdit(updatedSample, editedSamples)
        }
        editorFragment = fragment

        replaceFragment(fragment)
        updateUI()

        // Set sample data after fragment is attached
        fragment.setSampleData(audioData, sample.rootNote)

        // Restore loop points and ADSR preset from sample
        // Convert SF2 native units (timecents, centibels) to UI units (ms, %)
        val adsrPreset = com.Atom2Universe.app.sf2creator.audio.EnvelopeGenerator.ADSRSettings(
            attackMs = Sf2UnitConverter.timecentsToMs(sample.volEnvAttack).toFloat(),
            decayMs = Sf2UnitConverter.timecentsToMs(sample.volEnvDecay).toFloat(),
            sustainLevel = Sf2UnitConverter.centibelsToSustainPercent(sample.volEnvSustain) / 100f,
            releaseMs = Sf2UnitConverter.timecentsToMs(sample.volEnvRelease).toFloat(),
            requiresLoop = sample.hasLoop
        )
        fragment.restoreEditState(sample.loopStart, sample.loopEnd, sample.hasLoop, adsrPreset)
    }

    /**
     * Show the export fragment in edit mode for editing sample parameters.
     * This provides the keyboard preview for testing.
     */
    private fun showExportForSampleEdit(sample: Sf2SampleEntity, editedAudio: ShortArray) {
        currentStep = Step.EDIT_PROJECT_SAMPLE

        val fragment = ExportFragment.newInstance()

        // Set sample data (audio + root note)
        fragment.setSampleData(null, editedAudio, sample.rootNote)
        fragment.setLoopPoints(sample.loopStart, sample.loopEnd, sample.hasLoop)

        fragment.onChangeNote = {
            // Allow changing the note
            PitchSelectionDialog(
                context = this,
                detectedPitch = PitchResult.fromFrequency(
                    com.Atom2Universe.app.sf2creator.data.SampleData.midiNoteToFrequency(sample.rootNote),
                    1.0f
                ),
                samples = editedAudio
            ) { selectedNote ->
                // Update root note (but key range stays the same unless user changes it)
                editingSampleEntity = editingSampleEntity?.copy(rootNote = selectedNote)
                exportFragment?.updateRootNote(selectedNote)
            }.show()
        }

        fragment.onSaveExistingSample = {
            // Get updated parameters and save
            val params = fragment.getSampleParams()
            if (params != null) {
                saveSampleWithParams(sample, editedAudio, params)
            }
        }

        exportFragment = fragment

        // Enable edit mode (will be applied when view is ready)
        // Convert SF2 native units (timecents, centibels) to UI units (ms, %)
        fragment.enableEditMode(
            sampleName = sample.name,
            keyStart = sample.keyRangeStart,
            keyEnd = sample.keyRangeEnd,
            velStart = sample.velRangeStart,
            velEnd = sample.velRangeEnd,
            attenuation = sample.attenuation,
            coarseTune = sample.coarseTune,
            fineTune = sample.fineTuneCents,
            scaleTuning = sample.scaleTuning,
            // Volume Envelope - convert timecents to ms, centibels to %
            volEnvDelay = Sf2UnitConverter.timecentsToMs(sample.volEnvDelay),
            attack = Sf2UnitConverter.timecentsToMs(sample.volEnvAttack),
            volEnvHold = Sf2UnitConverter.timecentsToMs(sample.volEnvHold),
            decay = Sf2UnitConverter.timecentsToMs(sample.volEnvDecay),
            sustain = Sf2UnitConverter.centibelsToSustainPercent(sample.volEnvSustain),
            release = Sf2UnitConverter.timecentsToMs(sample.volEnvRelease),
            // Modulation Envelope - convert timecents to ms, centibels to %
            modEnvDelay = Sf2UnitConverter.timecentsToMs(sample.modEnvDelay),
            modEnvAttack = Sf2UnitConverter.timecentsToMs(sample.modEnvAttack),
            modEnvHold = Sf2UnitConverter.timecentsToMs(sample.modEnvHold),
            modEnvDecay = Sf2UnitConverter.timecentsToMs(sample.modEnvDecay),
            modEnvSustain = Sf2UnitConverter.centibelsToSustainPercent(sample.modEnvSustain),
            modEnvRelease = Sf2UnitConverter.timecentsToMs(sample.modEnvRelease),
            modEnvToPitch = sample.modEnvToPitch,
            modEnvToFilter = sample.modEnvToFilterFc,
            // LFOs - convert timecents to ms
            vibLfoDelay = Sf2UnitConverter.timecentsToMs(sample.vibLfoDelay),
            vibLfoFreq = sample.vibLfoFreq,
            vibLfoToPitch = sample.vibLfoToPitch,
            modLfoDelay = Sf2UnitConverter.timecentsToMs(sample.modLfoDelay),
            modLfoFreq = sample.modLfoFreq,
            modLfoToPitch = sample.modLfoToPitch,
            modLfoToFilter = sample.modLfoToFilterFc,
            modLfoToVol = sample.modLfoToVolume,
            // Filter - convert cents to Hz
            cutoffHz = Sf2UnitConverter.filterCentsToHz(sample.filterFc),
            resonanceCb = sample.filterQ,
            chorus = sample.chorusSend,
            reverb = sample.reverbSend,
            panValue = sample.pan,
            exclusiveClass = sample.exclusiveClass
        )

        replaceFragment(fragment)

        // Update title
        titleText.text = getString(R.string.sf2_edit_sample)
        stepIndicator.visibility = View.GONE
    }

    /**
     * Save the edited sample with new parameters.
     */
    private fun saveSampleWithParams(
        originalSample: Sf2SampleEntity,
        editedAudio: ShortArray,
        params: ExportFragment.SampleParams
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                saveWavFile(File(originalSample.audioFilePath), editedAudio)
            }

            if (success) {
                // Convert UI units (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
                val updatedSample = originalSample.copy(
                    name = params.name,
                    rootNote = params.rootNote,
                    keyRangeStart = params.keyRangeStart,
                    keyRangeEnd = params.keyRangeEnd,
                    velRangeStart = params.velRangeStart,
                    velRangeEnd = params.velRangeEnd,
                    loopStart = params.loopStart,
                    loopEnd = params.loopEnd,
                    hasLoop = params.hasLoop,
                    attenuation = params.attenuation,
                    coarseTune = params.coarseTune,
                    fineTuneCents = params.fineTuneCents,
                    scaleTuning = params.scaleTuning,
                    // Volume Envelope - convert ms to timecents, % to centibels
                    volEnvDelay = Sf2UnitConverter.msToTimecents(params.volEnvDelayMs),
                    volEnvAttack = Sf2UnitConverter.msToTimecents(params.attackMs),
                    volEnvHold = Sf2UnitConverter.msToTimecents(params.volEnvHoldMs),
                    volEnvDecay = Sf2UnitConverter.msToTimecents(params.decayMs),
                    volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(params.sustainPercent),
                    volEnvRelease = Sf2UnitConverter.msToTimecents(params.releaseMs),
                    // Modulation Envelope - convert ms to timecents, % to centibels
                    modEnvDelay = Sf2UnitConverter.msToTimecents(params.modEnvDelayMs),
                    modEnvAttack = Sf2UnitConverter.msToTimecents(params.modEnvAttackMs),
                    modEnvHold = Sf2UnitConverter.msToTimecents(params.modEnvHoldMs),
                    modEnvDecay = Sf2UnitConverter.msToTimecents(params.modEnvDecayMs),
                    modEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(params.modEnvSustainPercent),
                    modEnvRelease = Sf2UnitConverter.msToTimecents(params.modEnvReleaseMs),
                    modEnvToPitch = params.modEnvToPitch,
                    modEnvToFilterFc = params.modEnvToFilterFc,
                    // LFOs - convert ms to timecents
                    vibLfoDelay = Sf2UnitConverter.msToTimecents(params.vibLfoDelayMs),
                    vibLfoFreq = params.vibLfoFreqCents,
                    vibLfoToPitch = params.vibLfoToPitch,
                    modLfoDelay = Sf2UnitConverter.msToTimecents(params.modLfoDelayMs),
                    modLfoFreq = params.modLfoFreqCents,
                    modLfoToPitch = params.modLfoToPitch,
                    modLfoToFilterFc = params.modLfoToFilterFc,
                    modLfoToVolume = params.modLfoToVolume,
                    // Filter - convert Hz to cents
                    filterFc = Sf2UnitConverter.hzToFilterCents(params.filterCutoffHz),
                    filterQ = params.filterResonanceCb,
                    chorusSend = params.chorusSend,
                    reverbSend = params.reverbSend,
                    pan = params.pan,
                    exclusiveClass = params.exclusiveClass
                )

                val repository = Sf2ProjectRepository(this@Sf2CreatorActivity)
                repository.updateSample(updatedSample)
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_sample_updated,
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Clear editing state and return to project
            editingSampleEntity = null
            editingSampleAudio = null
            currentProjectId?.let { showProjectDetail(it) }
        }
    }

    /**
     * Load samples from a WAV file.
     * Delegates to WavUtils.
     */
    private fun loadWavFile(file: File): ShortArray? = WavUtils.loadWavFile(file)

    /**
     * Save samples to a WAV file.
     * Delegates to WavUtils.
     */
    private fun saveWavFile(file: File, samples: ShortArray): Boolean = WavUtils.writeWavFile(file, samples)

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateUI() {
        when (currentStep) {
            Step.RECORD -> {
                titleText.text = getString(R.string.sf2_step_record_title)
                stepIndicator.text = getString(R.string.sf2_step_1_of_3)
                stepIndicator.visibility = View.VISIBLE
            }
            Step.EDIT -> {
                titleText.text = getString(R.string.sf2_step_edit_title)
                stepIndicator.text = getString(R.string.sf2_step_2_of_3)
                stepIndicator.visibility = View.VISIBLE
            }
            Step.EXPORT -> {
                titleText.text = getString(R.string.sf2_step_export_title)
                stepIndicator.text = getString(R.string.sf2_step_3_of_3)
                stepIndicator.visibility = View.VISIBLE
            }
            Step.PROJECT_MANAGER -> {
                titleText.text = getString(R.string.sf2_project_manager)
                stepIndicator.visibility = View.GONE
            }
            Step.PROJECT_DETAIL -> {
                titleText.text = getString(R.string.sf2_project_detail_title)
                stepIndicator.visibility = View.GONE
            }
            Step.EDIT_PROJECT_SAMPLE -> {
                titleText.text = getString(R.string.sf2_edit_sample)
                stepIndicator.visibility = View.GONE
            }
            Step.SF2_IMPORT -> {
                titleText.text = getString(R.string.sf2_import_title)
                stepIndicator.visibility = View.GONE
            }
        }
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    private enum class Step {
        RECORD,
        EDIT,
        EXPORT,
        PROJECT_MANAGER,
        PROJECT_DETAIL,
        EDIT_PROJECT_SAMPLE,
        SF2_IMPORT
    }
}
