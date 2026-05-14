package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.sf2.Sf2Engine
import com.Atom2Universe.app.sf2creator.data.PitchResult
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dialog for selecting or confirming the pitch/root note of a recorded sample.
 * Displays the auto-detected pitch and allows manual adjustment.
 */
class PitchSelectionDialog(
    context: Context,
    private val detectedPitch: PitchResult,
    private val samples: ShortArray,
    private val onPitchSelected: (Int) -> Unit
) : Dialog(context) {

    private lateinit var detectedPitchText: TextView
    private lateinit var octaveSpinner: Spinner
    private lateinit var noteSpinner: Spinner
    private lateinit var frequencyText: TextView
    private lateinit var keyboardPreview: KeyboardPreviewView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private var loadingIndicator: ProgressBar? = null
    private var octaveDownButton: ImageButton? = null
    private var octaveUpButton: ImageButton? = null

    private var selectedNote = detectedPitch.midiNote
    private val scope = CoroutineScope(Dispatchers.Main)
    private val sf2Writer = Sf2Writer()
    private var sf2Engine: Sf2Engine? = null
    private var tempSf2File: File? = null
    private var engineReady: Boolean = false
    private var sf2Job: Job? = null

    companion object {
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val OCTAVES = (-1..9).map { it.toString() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_pitch_selection)

        // Make dialog wider
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViews()
        setupSpinners()
        setupKeyboard()
        setupButtons()
        generatePreviewSf2()
        updateDisplay()
    }

    private fun findViews() {
        detectedPitchText = findViewById(R.id.detected_pitch_text)
        octaveSpinner = findViewById(R.id.octave_spinner)
        noteSpinner = findViewById(R.id.note_spinner)
        frequencyText = findViewById(R.id.frequency_text)
        keyboardPreview = findViewById(R.id.keyboard_preview)
        confirmButton = findViewById(R.id.confirm_button)
        cancelButton = findViewById(R.id.cancel_button)
        loadingIndicator = findViewById(R.id.loading_indicator)
        octaveDownButton = findViewById(R.id.octave_down_button)
        octaveUpButton = findViewById(R.id.octave_up_button)
    }

    /**
     * Generate a temporary SF2 file from the current sample and initialize Sf2Engine.
     */
    private fun generatePreviewSf2() {
        sf2Job?.cancel()
        loadingIndicator?.visibility = View.VISIBLE
        engineReady = false

        sf2Job = scope.launch {
            // Release previous engine
            sf2Engine?.stopAudioRenderer()
            sf2Engine?.release()
            sf2Engine = null

            val sf2File = withContext(Dispatchers.IO) {
                val tempDir = context.cacheDir
                val tempFile = File(tempDir, "pitch_preview.sf2")

                val inMemorySample = Sf2Writer.InMemorySample(
                    name = "Preview",
                    samples = samples,
                    sampleRate = 44100,
                    rootNote = selectedNote,
                    keyRangeStart = 0,
                    keyRangeEnd = 127
                )

                val success = sf2Writer.writeSf2FromMemory(tempFile, "Preview", inMemorySample)
                if (success) tempFile else null
            }

            if (sf2File != null) {
                tempSf2File = sf2File

                val engine = Sf2Engine(context)
                val initialized = engine.initialize(sf2File.absolutePath)

                if (initialized) {
                    engine.startAudioRenderer()
                    sf2Engine = engine
                    engineReady = true
                }
            }

            loadingIndicator?.visibility = View.GONE
        }
    }

    private fun setupSpinners() {
        // Note spinner with custom layouts for dark theme
        val noteAdapter = ArrayAdapter(context, R.layout.item_spinner_selected, NOTE_NAMES)
        noteAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        noteSpinner.adapter = noteAdapter

        // Octave spinner with custom layouts for dark theme
        val octaveAdapter = ArrayAdapter(context, R.layout.item_spinner_selected, OCTAVES)
        octaveAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        octaveSpinner.adapter = octaveAdapter

        // Set initial values from detected pitch
        val noteIndex = selectedNote % 12
        val octave = (selectedNote / 12) - 1

        noteSpinner.setSelection(noteIndex)
        octaveSpinner.setSelection(octave + 1) // +1 because OCTAVES starts at -1

        // Listen for changes
        noteSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSelectedNote()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        octaveSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSelectedNote()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun setupKeyboard() {
        keyboardPreview.setRootNote(selectedNote)

        // Connect keyboard callbacks to Sf2Engine
        keyboardPreview.onNoteOn = { note, velocity ->
            if (engineReady) {
                sf2Engine?.sendNoteOn(0, note, velocity)
            }
        }
        keyboardPreview.onNoteOff = { note ->
            if (engineReady) {
                sf2Engine?.sendNoteOff(0, note)
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

        updateOctaveButtonStates()
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
        confirmButton.setOnClickListener {
            onPitchSelected(selectedNote)
            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun updateSelectedNote() {
        val noteIndex = noteSpinner.selectedItemPosition
        val octave = octaveSpinner.selectedItemPosition - 1 // -1 because OCTAVES starts at -1

        val newNote = ((octave + 1) * 12 + noteIndex).coerceIn(0, 127)

        // Only update if note actually changed
        if (newNote != selectedNote) {
            selectedNote = newNote
            keyboardPreview.setRootNote(selectedNote)
            generatePreviewSf2()
        }
        updateDisplay()
    }

    private fun updateDisplay() {
        // Show detected pitch info
        if (detectedPitch.confidence > 0.3f) {
            detectedPitchText.text = context.getString(
                R.string.sf2_auto_detected,
                detectedPitch.noteName,
                detectedPitch.frequency
            )
        } else {
            detectedPitchText.text = context.getString(R.string.sf2_pitch_not_detected)
        }

        // Show current selection frequency
        val freq = SampleData.midiNoteToFrequency(selectedNote)
        frequencyText.text = context.getString(
            R.string.sf2_selected_frequency,
            SampleData.midiNoteToName(selectedNote),
            freq
        )
    }

    override fun dismiss() {
        sf2Job?.cancel()
        sf2Engine?.stopAudioRenderer()
        sf2Engine?.release()
        sf2Engine = null
        engineReady = false
        tempSf2File?.delete()
        tempSf2File = null
        super.dismiss()
    }
}
