package com.Atom2Universe.app.sf2creator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.audio.AudioFeedback as Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioThemedActivity
import com.Atom2Universe.app.sf2creator.data.PitchResult
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult
import com.Atom2Universe.app.sf2creator.ui.ExportFragment
import com.Atom2Universe.app.sf2creator.ui.PitchSelectionDialog
import com.Atom2Universe.app.sf2creator.ui.ProjectDetailFragment
import com.Atom2Universe.app.sf2creator.ui.RecordSampleFragment
import com.Atom2Universe.app.sf2creator.ui.SampleEditorFragment
import com.Atom2Universe.app.sf2creator.ui.Sf2ImportFragment
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.util.enableImmersiveMode
import java.io.File

/**
 * A project of the SF2 creator, opened from the gallery ([Sf2LibraryActivity]).
 *
 * Shows the project ([ProjectDetailFragment]), or first the presets to import when it
 * opens an SF2 as a new project. From the project:
 * - "Record": record (RecordSampleFragment), edit (SampleEditorFragment), adjust
 *   (ExportFragment), then the sample goes into the instrument selected in the project;
 * - editing a sample opens the waveform editor then its parameters.
 */
class Sf2CreatorActivity : AudioThemedActivity() {

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
    private var returnToProjectAfterRecord: Boolean = false
    /** Preset zone selected in the project when recording started: the sample goes into its instrument. */
    private var recordTargetPresetId: Long = -1
    /** The project was created to import an SF2: cancelling the import removes it. */
    private var importingNewProject: Boolean = false

    // Sample editing state (when editing existing sample in project)
    private var editingSampleEntity: Sf2SampleEntity? = null
    private var editingSampleAudio: ShortArray? = null

    // Fragments
    private var recordFragment: RecordSampleFragment? = null
    private var editorFragment: SampleEditorFragment? = null
    private var exportFragment: ExportFragment? = null
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
            val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1)
            val importUri = intent.data
            when {
                projectId > 0 -> showProjectDetail(projectId)
                importUri != null -> handleSf2ImportAsNewProject(importUri)
                else -> {
                    startActivity(Sf2LibraryActivity.intent(this))
                    finish()
                    return
                }
            }
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
                    Step.RECORD -> backToProject()
                    Step.PROJECT_DETAIL -> finish()
                    Step.EDIT_PROJECT_SAMPLE -> {
                        // Cancel editing and return to project
                        editingSampleEntity = null
                        editingSampleAudio = null
                        backToProject()
                    }
                    Step.SF2_IMPORT -> sf2ImportFragment?.onCancel?.invoke() ?: backToProject()
                }
            }
        })
    }

    private fun findViews() {
        titleText = findViewById(R.id.title_text)
        stepIndicator = findViewById(R.id.step_indicator)
        backButton = findViewById(R.id.back_button)
    }

    /** Back to the open project, or to the gallery. */
    private fun backToProject() {
        returnToProjectAfterRecord = false
        currentProjectId?.let { showProjectDetail(it) } ?: finish()
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
            fragment.getSampleParams()?.let { addRecordedSampleToProject(it) }
        }
        exportFragment = fragment

        replaceFragment(fragment)
        updateUI()
    }

    /** Adds the recorded sample to the instrument selected in the open project. */
    private fun addRecordedSampleToProject(params: ExportFragment.SampleParams) {
        val projectId = currentProjectId ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val repository = Sf2ProjectRepository(this@Sf2CreatorActivity)
            val instrumentId = repository.getPresetById(recordTargetPresetId)?.instrumentId
                ?: repository.getOrCreateDefaultPreset(projectId).instrumentId
            repository.addSampleToInstrument(
                instrumentId = instrumentId,
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
                volEnvAttack = Sf2UnitConverter.msToTimecents(params.attackMs),
                volEnvDecay = Sf2UnitConverter.msToTimecents(params.decayMs),
                volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(params.sustainPercent),
                volEnvRelease = Sf2UnitConverter.msToTimecents(params.releaseMs),
                filterFc = Sf2UnitConverter.hzToFilterCents(params.filterCutoffHz),
                filterQ = params.filterResonanceCb,
                chorusSend = params.chorusSend,
                reverbSend = params.reverbSend,
                pan = params.pan
            )
            Toast.makeText(this@Sf2CreatorActivity, R.string.sf2_sample_added, Toast.LENGTH_SHORT).show()
            savedExportParams = null
            backToProject()
        }
    }

    private fun showProjectDetail(projectId: Long) {
        currentStep = Step.PROJECT_DETAIL
        currentProjectId = projectId

        val fragment = ProjectDetailFragment.newInstance(projectId)

        fragment.onAddSampleRequested = {
            // Record a sample for the selected instrument, then come back to the project
            recordTargetPresetId = fragment.getSelectedPresetId()
            returnToProjectAfterRecord = true
            recordedFile = null
            recordedSamples = null
            savedExportParams = null
            showRecordStep()
        }

        fragment.onImportSf2Requested = {
            sf2ImportLauncher.launch(arrayOf("*/*"))
        }

        fragment.onExportComplete = { exportedFile ->
            Toast.makeText(
                this,
                getString(R.string.sf2_saved_to, exportedFile.absolutePath),
                Toast.LENGTH_LONG
            ).show()
        }

        fragment.onSampleEditRequested = { sample ->
            // Load audio data (WAV, or the imported SF2) and open full editor
            CoroutineScope(Dispatchers.Main).launch {
                val audioData = Sf2ProjectRepository(this@Sf2CreatorActivity).loadSampleAudio(sample)
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
        currentStep = Step.SF2_IMPORT
        updateUI()
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
                finish()
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
                finish()
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
                finish()
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
            importingNewProject = true

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
            importingNewProject = false
            // Return to project detail and refresh
            showProjectDetail(projectId)
        }

        fragment.onCancel = {
            // Clean up temp file
            tempFile.delete()
            sf2ImportFragment = null
            if (importingNewProject) {
                // Nothing was imported: the project made for it goes away
                CoroutineScope(Dispatchers.Main).launch {
                    Sf2ProjectRepository(this@Sf2CreatorActivity).deleteProject(projectId)
                    finish()
                }
            } else {
                showProjectDetail(projectId)
            }
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
            val repository = Sf2ProjectRepository(this@Sf2CreatorActivity)
            // An imported sample has no WAV until its audio is changed
            val savedSample = repository.saveEditedSampleAudio(originalSample, editedAudio)

            if (savedSample != null) {
                // Convert UI units (ms, %, Hz) to SF2 native units (timecents, centibels, cents)
                val updatedSample = savedSample.copy(
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

                repository.updateSample(updatedSample)
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_sample_updated,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@Sf2CreatorActivity,
                    R.string.sf2_sample_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Clear editing state and return to project
            editingSampleEntity = null
            editingSampleAudio = null
            currentProjectId?.let { showProjectDetail(it) }
        }
    }

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

    private enum class Step {
        RECORD,
        EDIT,
        EXPORT,
        PROJECT_DETAIL,
        EDIT_PROJECT_SAMPLE,
        SF2_IMPORT
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"

        /** Opens a project of the gallery. */
        fun openIntent(context: Context, projectId: Long): Intent =
            Intent(context, Sf2CreatorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)

        /** Imports an SF2 file as a new project. */
        fun importIntent(context: Context, uri: Uri): Intent =
            Intent(context, Sf2CreatorActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
