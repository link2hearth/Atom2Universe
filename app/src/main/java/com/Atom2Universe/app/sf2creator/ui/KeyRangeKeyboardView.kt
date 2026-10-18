package com.Atom2Universe.app.sf2creator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R

/**
 * A mini keyboard view that visualizes a key range.
 * Shows 3 octaves and highlights the selected range and root note.
 */
class KeyRangeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val NUM_OCTAVES = 3
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

    // Overlay for white keys in range (light tint)
    private val rangeWhiteOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#60FFC107".toColorInt() // Semi-transparent amber
        style = Paint.Style.FILL
    }

    // Overlay for black keys in range (visible tint)
    private val rangeBlackOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#80FFA000".toColorInt() // Semi-transparent orange
        style = Paint.Style.FILL
    }

    private val rootKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FFC107".toColorInt() // Amber accent
        style = Paint.Style.FILL
    }

    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // Overlay for keys outside range (dim effect)
    private val outOfRangeOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#60000000".toColorInt() // Lighter semi-transparent black
        style = Paint.Style.FILL
    }

    // Keyboard geometry
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f

    // State
    private var startNote = 36 // C2 default
    private var rootNote = 60 // C4 default
    private var rangeStart = 60
    private var rangeEnd = 60

    // Key rects for rendering
    private val whiteKeyRects = mutableListOf<Pair<RectF, Int>>() // rect to MIDI note
    private val blackKeyRects = mutableListOf<Pair<RectF, Int>>()

    init {
        // Get accent color from theme for range overlay
        val typedArray = context.obtainStyledAttributes(intArrayOf(R.attr.a2uMidiAccent))
        val accentColor = typedArray.getColor(0, "#FFC107".toColorInt())
        typedArray.recycle()

        // Apply theme color to overlays with transparency
        rangeWhiteOverlay.color = Color.argb(96, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        rangeBlackOverlay.color = Color.argb(128, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
    }

    /**
     * Set the root note and key range to display.
     */
    fun setRange(root: Int, start: Int, end: Int) {
        rootNote = root.coerceIn(0, 127)
        rangeStart = start.coerceIn(0, 127)
        rangeEnd = end.coerceIn(0, 127)

        // Center the keyboard around the root note
        startNote = ((rootNote / 12) - 1).coerceIn(0, 10 - NUM_OCTAVES) * 12

        calculateKeyDimensions()
        invalidate()
    }

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

    private fun isInRange(note: Int): Boolean {
        return note in rangeStart..rangeEnd
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw white keys first
        for ((rect, note) in whiteKeyRects) {
            // Always draw white base
            canvas.drawRect(rect, whiteKeyPaint)

            // Apply overlay based on state
            when {
                note == rootNote -> canvas.drawRect(rect, rootKeyPaint)
                isInRange(note) -> canvas.drawRect(rect, rangeWhiteOverlay)
                else -> canvas.drawRect(rect, outOfRangeOverlay)
            }

            canvas.drawRect(rect, keyBorderPaint)
        }

        // Draw black keys on top
        for ((rect, note) in blackKeyRects) {
            // Always draw black base
            canvas.drawRoundRect(rect, 2f, 2f, blackKeyPaint)

            // Apply overlay based on state
            when {
                note == rootNote -> canvas.drawRoundRect(rect, 2f, 2f, rootKeyPaint)
                isInRange(note) -> canvas.drawRoundRect(rect, 2f, 2f, rangeBlackOverlay)
                else -> canvas.drawRoundRect(rect, 2f, 2f, outOfRangeOverlay)
            }
        }
    }
}
