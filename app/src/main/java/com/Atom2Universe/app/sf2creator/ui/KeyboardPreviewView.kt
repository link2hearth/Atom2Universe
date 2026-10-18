package com.Atom2Universe.app.sf2creator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData

/**
 * A visual piano keyboard for previewing samples at different pitches.
 * Displays 2 octaves centered around the root note.
 */
class KeyboardPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val NUM_OCTAVES = 2
        private const val KEYS_PER_OCTAVE = 12
        private const val WHITE_KEYS_PER_OCTAVE = 7
        private const val NUM_WHITE_KEYS = NUM_OCTAVES * WHITE_KEYS_PER_OCTAVE
    }

    // Paint objects
    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = getThemeAccentColor()
        style = Paint.Style.FILL
    }

    private val rootKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FFC107".toColorInt() // Amber accent
        style = Paint.Style.FILL
    }

    // Key range highlight (pale version of root color)
    private val rangeKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#80FFC107".toColorInt() // Semi-transparent amber
        style = Paint.Style.FILL
    }

    // For black keys in range - slightly darker
    private val rangeBlackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#60CC9900".toColorInt() // Darker amber overlay
        style = Paint.Style.FILL
    }

    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val blackLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    // Keyboard geometry
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f

    // State
    private var startNote = 48 // C3 default
    private var rootNote = 60 // C4 default
    private var pressedNote = -1

    // Key range for SF2 export
    private var keyRangeStart = -1 // -1 means not set (use root note only)
    private var keyRangeEnd = -1

    // Note callbacks for external playback
    var onNoteOn: ((note: Int, velocity: Int) -> Unit)? = null
    var onNoteOff: ((note: Int) -> Unit)? = null

    // Key rects for hit testing
    private val whiteKeyRects = mutableListOf<Pair<RectF, Int>>() // rect to MIDI note
    private val blackKeyRects = mutableListOf<Pair<RectF, Int>>()

    // Listeners
    var onNotePressed: ((Int) -> Unit)? = null
    var onOctaveChanged: ((startNote: Int, endNote: Int) -> Unit)? = null

    /**
     * Set the root note (the original pitch of the sample).
     * The keyboard will center around this note.
     */
    fun setRootNote(note: Int) {
        rootNote = note.coerceIn(0, 127)
        // Center the keyboard around the root note
        // Start from an octave below the root (but not below 0)
        startNote = ((rootNote / 12) - 1).coerceAtLeast(0) * 12
        // Recalculate key rectangles with new MIDI note mappings
        calculateKeyDimensions()
        invalidate()
    }

    /**
     * Get the currently set root note.
     */
    @Suppress("unused")
    fun getRootNote(): Int = rootNote

    /**
     * Set the key range for the sample.
     * Keys within this range will be highlighted with a pale color.
     * Pass -1 for both to clear the range (only root note will be shown).
     */
    fun setKeyRange(start: Int, end: Int) {
        keyRangeStart = start.coerceIn(-1, 127)
        keyRangeEnd = end.coerceIn(-1, 127)
        invalidate()
    }

    /**
     * Clear the key range highlight.
     */
    fun clearKeyRange() {
        keyRangeStart = -1
        keyRangeEnd = -1
        invalidate()
    }

    /**
     * Check if a note is within the key range.
     */
    private fun isInKeyRange(note: Int): Boolean {
        if (keyRangeStart < 0 || keyRangeEnd < 0) return false
        return note in keyRangeStart..keyRangeEnd
    }

    /**
     * Get the current visible note range.
     */
    @Suppress("unused")
    fun getVisibleNoteRange(): Pair<Int, Int> {
        val endNote = (startNote + NUM_OCTAVES * 12 - 1).coerceAtMost(127)
        return Pair(startNote, endNote)
    }

    /**
     * Shift the keyboard view up by one octave.
     * @return true if the shift was possible, false if already at max
     */
    fun shiftOctaveUp(): Boolean {
        val newStart = startNote + 12
        if (newStart + NUM_OCTAVES * 12 > 128) return false

        startNote = newStart
        calculateKeyDimensions()
        invalidate()

        val endNote = (startNote + NUM_OCTAVES * 12 - 1).coerceAtMost(127)
        onOctaveChanged?.invoke(startNote, endNote)
        return true
    }

    /**
     * Shift the keyboard view down by one octave.
     * @return true if the shift was possible, false if already at min
     */
    fun shiftOctaveDown(): Boolean {
        val newStart = startNote - 12
        if (newStart < 0) return false

        startNote = newStart
        calculateKeyDimensions()
        invalidate()

        val endNote = (startNote + NUM_OCTAVES * 12 - 1).coerceAtMost(127)
        onOctaveChanged?.invoke(startNote, endNote)
        return true
    }

    /**
     * Check if can shift up.
     */
    fun canShiftUp(): Boolean = startNote + 12 + NUM_OCTAVES * 12 <= 128

    /**
     * Check if can shift down.
     */
    fun canShiftDown(): Boolean = startNote >= 12

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyDimensions()
    }

    private fun calculateKeyDimensions() {
        whiteKeyWidth = width.toFloat() / NUM_WHITE_KEYS
        blackKeyWidth = whiteKeyWidth * 0.6f
        blackKeyHeight = height * 0.6f

        // Build key rectangles
        whiteKeyRects.clear()
        blackKeyRects.clear()

        var whiteKeyIndex = 0
        for (octave in 0 until NUM_OCTAVES) {
            for (noteInOctave in 0 until KEYS_PER_OCTAVE) {
                val midiNote = startNote + octave * 12 + noteInOctave
                if (midiNote > 127) break

                if (isBlackKey(noteInOctave)) {
                    // Black key - position relative to previous white key
                    val x = whiteKeyIndex * whiteKeyWidth - blackKeyWidth / 2
                    val rect = RectF(x, 0f, x + blackKeyWidth, blackKeyHeight)
                    blackKeyRects.add(Pair(rect, midiNote))
                } else {
                    // White key
                    val x = whiteKeyIndex * whiteKeyWidth
                    val rect = RectF(x, 0f, x + whiteKeyWidth, height.toFloat())
                    whiteKeyRects.add(Pair(rect, midiNote))
                    whiteKeyIndex++
                }
            }
        }
    }

    private fun isBlackKey(noteInOctave: Int): Boolean {
        return noteInOctave in listOf(1, 3, 6, 8, 10) // C#, D#, F#, G#, A#
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw white keys first
        for ((rect, note) in whiteKeyRects) {
            val paint = when {
                note == pressedNote -> pressedKeyPaint
                note == rootNote -> rootKeyPaint
                isInKeyRange(note) -> rangeKeyPaint
                else -> whiteKeyPaint
            }
            canvas.drawRect(rect, paint)
            canvas.drawRect(rect, keyBorderPaint)

            // Draw note label for C notes
            if (note % 12 == 0) {
                val octave = (note / 12) - 1
                val label = "C$octave"
                canvas.drawText(label, rect.centerX(), rect.bottom - 10f, labelPaint)
            }
        }

        // Draw black keys on top
        for ((rect, note) in blackKeyRects) {
            // For black keys in range, draw black first then overlay
            if (isInKeyRange(note) && note != rootNote && note != pressedNote) {
                canvas.drawRoundRect(rect, 4f, 4f, blackKeyPaint)
                canvas.drawRoundRect(rect, 4f, 4f, rangeBlackKeyPaint)
            } else {
                val paint = when {
                    note == pressedNote -> pressedKeyPaint
                    note == rootNote -> rootKeyPaint
                    else -> blackKeyPaint
                }
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }

            // Draw label if this is the root note
            if (note == rootNote) {
                val noteName = SampleData.midiNoteToName(note)
                canvas.drawText(noteName, rect.centerX(), rect.bottom - 10f, blackLabelPaint)
            }
        }

        // Draw root note indicator if it's on a white key
        if (rootNote in whiteKeyRects.map { it.second }) {
            val (rect, _) = whiteKeyRects.find { it.second == rootNote } ?: return
            val noteName = SampleData.midiNoteToName(rootNote)
            labelPaint.color = Color.WHITE
            canvas.drawText(noteName, rect.centerX(), rect.bottom - 30f, labelPaint)
            labelPaint.color = Color.DKGRAY
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val note = getNoteAtPosition(event.x, event.y)
                if (note != pressedNote && note >= 0) {
                    // NoteOff on the previous note
                    if (pressedNote >= 0) {
                        onNoteOff?.invoke(pressedNote)
                    }
                    pressedNote = note
                    // NoteOn on the new note
                    onNoteOn?.invoke(note, 100)
                    onNotePressed?.invoke(note)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressedNote >= 0) {
                    onNoteOff?.invoke(pressedNote)
                }
                pressedNote = -1
                invalidate()
            }
        }
        return true
    }

    private fun getNoteAtPosition(x: Float, y: Float): Int {
        // Check black keys first (they're on top)
        for ((rect, note) in blackKeyRects) {
            if (rect.contains(x, y)) {
                return note
            }
        }

        // Check white keys
        for ((rect, note) in whiteKeyRects) {
            if (rect.contains(x, y)) {
                return note
            }
        }

        return -1
    }

    /**
     * Update the accent color to match the app theme.
     */
    @Suppress("unused")
    fun setAccentColor(color: Int) {
        pressedKeyPaint.color = color
        invalidate()
    }

    /**
     * Set the color for the root note highlight.
     */
    @Suppress("unused")
    fun setRootKeyColor(color: Int) {
        rootKeyPaint.color = color
        invalidate()
    }

    /**
     * Get the theme accent color.
     */
    private fun getThemeAccentColor(): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)) {
            typedValue.data
        } else {
            "#4CAF50".toColorInt() // Fallback
        }
    }
}
