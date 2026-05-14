package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData

/**
 * Dialog for selecting a key range for a sample.
 * The root note is fixed (displayed for reference), and the user can
 * select the start and end notes of the range.
 *
 * Validation: keyRangeStart <= rootNote <= keyRangeEnd
 */
class KeyRangeDialog(
    context: Context,
    private val rootNote: Int,
    currentStart: Int = rootNote,
    currentEnd: Int = rootNote,
    private val onRangeSelected: (start: Int, end: Int) -> Unit
) : Dialog(context) {

    private val initialStart = currentStart
    private val initialEnd = currentEnd

    private lateinit var rootNoteText: TextView
    private lateinit var rangeKeyboard: KeyRangeKeyboardView
    private lateinit var rangePreviewText: TextView
    private lateinit var startNoteSpinner: Spinner
    private lateinit var startOctaveSpinner: Spinner
    private lateinit var startNoteDisplay: TextView
    private lateinit var endNoteSpinner: Spinner
    private lateinit var endOctaveSpinner: Spinner
    private lateinit var endNoteDisplay: TextView
    private lateinit var errorText: TextView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button

    private var selectedStart: Int = initialStart
    private var selectedEnd: Int = initialEnd

    companion object {
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val OCTAVES = (-1..9).map { it.toString() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_key_range)

        // Make dialog wider
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViews()
        setupSpinners()
        setupButtons()
        updateDisplay()
    }

    private fun findViews() {
        rootNoteText = findViewById(R.id.root_note_text)
        rangeKeyboard = findViewById(R.id.range_keyboard)
        rangePreviewText = findViewById(R.id.range_preview_text)
        startNoteSpinner = findViewById(R.id.start_note_spinner)
        startOctaveSpinner = findViewById(R.id.start_octave_spinner)
        startNoteDisplay = findViewById(R.id.start_note_display)
        endNoteSpinner = findViewById(R.id.end_note_spinner)
        endOctaveSpinner = findViewById(R.id.end_octave_spinner)
        endNoteDisplay = findViewById(R.id.end_note_display)
        errorText = findViewById(R.id.error_text)
        confirmButton = findViewById(R.id.confirm_button)
        cancelButton = findViewById(R.id.cancel_button)

        // Set root note text
        val rootNoteName = SampleData.midiNoteToName(rootNote)
        rootNoteText.text = context.getString(R.string.sf2_root_note_fixed, rootNoteName)
    }

    private fun setupSpinners() {
        // Note adapters with custom layouts for dark theme
        val noteAdapter = ArrayAdapter(context, R.layout.item_spinner_selected, NOTE_NAMES)
        noteAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)

        // Octave adapter
        val octaveAdapter = ArrayAdapter(context, R.layout.item_spinner_selected, OCTAVES)
        octaveAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)

        // Start note spinners
        startNoteSpinner.adapter = noteAdapter
        startOctaveSpinner.adapter = ArrayAdapter(context, R.layout.item_spinner_selected, OCTAVES).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        // End note spinners
        endNoteSpinner.adapter = ArrayAdapter(context, R.layout.item_spinner_selected, NOTE_NAMES).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        endOctaveSpinner.adapter = ArrayAdapter(context, R.layout.item_spinner_selected, OCTAVES).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        // Set initial values
        setSpinnerToNote(startNoteSpinner, startOctaveSpinner, selectedStart)
        setSpinnerToNote(endNoteSpinner, endOctaveSpinner, selectedEnd)

        // Listeners for start note
        val startListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStart = getSpinnerNote(startNoteSpinner, startOctaveSpinner)
                updateDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        startNoteSpinner.onItemSelectedListener = startListener
        startOctaveSpinner.onItemSelectedListener = startListener

        // Listeners for end note
        val endListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedEnd = getSpinnerNote(endNoteSpinner, endOctaveSpinner)
                updateDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        endNoteSpinner.onItemSelectedListener = endListener
        endOctaveSpinner.onItemSelectedListener = endListener
    }

    private fun setSpinnerToNote(noteSpinner: Spinner, octaveSpinner: Spinner, midiNote: Int) {
        val noteIndex = midiNote % 12
        val octave = (midiNote / 12) - 1

        noteSpinner.setSelection(noteIndex)
        octaveSpinner.setSelection(octave + 1) // +1 because OCTAVES starts at -1
    }

    private fun getSpinnerNote(noteSpinner: Spinner, octaveSpinner: Spinner): Int {
        val noteIndex = noteSpinner.selectedItemPosition
        val octave = octaveSpinner.selectedItemPosition - 1 // -1 because OCTAVES starts at -1

        return ((octave + 1) * 12 + noteIndex).coerceIn(0, 127)
    }

    private fun setupButtons() {
        confirmButton.setOnClickListener {
            if (validateRange()) {
                onRangeSelected(selectedStart, selectedEnd)
                dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun validateRange(): Boolean {
        // Rule: keyRangeStart <= rootNote <= keyRangeEnd
        if (selectedStart > rootNote) {
            errorText.text = context.getString(
                R.string.sf2_range_error_start_after_root,
                SampleData.midiNoteToName(rootNote)
            )
            errorText.visibility = View.VISIBLE
            return false
        }

        if (selectedEnd < rootNote) {
            errorText.text = context.getString(
                R.string.sf2_range_error_end_before_root,
                SampleData.midiNoteToName(rootNote)
            )
            errorText.visibility = View.VISIBLE
            return false
        }

        errorText.visibility = View.GONE
        return true
    }

    private fun updateDisplay() {
        // Update note displays
        startNoteDisplay.text = SampleData.midiNoteToName(selectedStart)
        endNoteDisplay.text = SampleData.midiNoteToName(selectedEnd)

        // Update keyboard visualization
        rangeKeyboard.setRange(rootNote, selectedStart, selectedEnd)

        // Update range preview text
        val startName = SampleData.midiNoteToName(selectedStart)
        val endName = SampleData.midiNoteToName(selectedEnd)
        val noteCount = selectedEnd - selectedStart + 1
        rangePreviewText.text = context.getString(R.string.sf2_range_preview, startName, endName, noteCount)

        // Validate and update button state
        val isValid = selectedStart <= rootNote && selectedEnd >= rootNote && selectedStart <= selectedEnd
        confirmButton.isEnabled = isValid
        confirmButton.alpha = if (isValid) 1f else 0.5f

        // Show/hide error
        if (!isValid) {
            when {
                selectedStart > rootNote -> {
                    errorText.text = context.getString(
                        R.string.sf2_range_error_start_after_root,
                        SampleData.midiNoteToName(rootNote)
                    )
                }
                selectedEnd < rootNote -> {
                    errorText.text = context.getString(
                        R.string.sf2_range_error_end_before_root,
                        SampleData.midiNoteToName(rootNote)
                    )
                }
            }
            errorText.visibility = View.VISIBLE
        } else {
            errorText.visibility = View.GONE
        }
    }
}
