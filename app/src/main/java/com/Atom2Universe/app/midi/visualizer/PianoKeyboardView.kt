package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.midi.practice.ColorSettingsManager
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Clavier de piano animé pour UN canal MIDI.
 *
 * La vue ne reçoit aucun événement : à chaque image elle lit [MidiLiveState] pour son canal.
 * Elle est donc toujours juste, quel que soit le moment où elle est créée, recyclée ou
 * réaffichée, et n'a besoin ni d'être « enregistrée » quelque part ni de bouton de rafraîchissement.
 *
 * Les touches relâchées s'éteignent en fondu au lieu de disparaître d'un coup.
 */
class PianoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), MidiFrameClock.Tickable {

    private companion object {
        const val DEFAULT_HEIGHT_DP = 64f
        const val BLACK_KEY_WIDTH_RATIO = 0.62f
        const val BLACK_KEY_HEIGHT_RATIO = 0.62f
        const val KEY_GAP_DP = 1f
        const val KEY_RADIUS_DP = 3f
        const val RELEASE_FADE_MS = 160f

        val BLACK_PITCH_CLASSES = booleanArrayOf(
            false, true, false, true, false, false, true, false, true, false, true, false
        )

        val WHITE_KEY = Color.rgb(0xEE, 0xEF, 0xF4)
        val WHITE_KEY_SHADE = Color.rgb(0xC9, 0xCC, 0xD6)
        val BLACK_KEY = Color.rgb(0x16, 0x18, 0x1F)
        val LABEL = Color.rgb(0x8A, 0x8F, 0x9E)
    }

    // Canal affiché et plage de notes
    private var channel = 0
    private var noteRangeMin = 48
    private var noteRangeMax = 84
    private var fallbackProgram = 0

    // Programme réellement utilisé pour la couleur (celui du morceau si connu, sinon l'analyse)
    private var colorProgram = -1
    private var baseColor = Color.CYAN

    // Intensité d'allumage par note (0 = éteinte, 1 = vélocité max) ; décroît au relâchement
    private val level = FloatArray(MidiLiveState.NOTES)

    private var seenNotesVersion = -1
    private var lastFrameNanos = 0L
    private var fading = false
    private var needsSync = true

    // Géométrie précalculée
    private var whiteNotes = IntArray(0)
    private var blackNotes = IntArray(0)
    private var blackLeft = FloatArray(0)
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f

    private val density = resources.displayMetrics.density
    private val gap = KEY_GAP_DP * density
    private val radius = KEY_RADIUS_DP * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    /**
     * Associe le clavier à un canal. Peut être rappelée à chaque rebind sans effet de bord.
     */
    fun bindChannel(channel: Int, minNote: Int, maxNote: Int, initialProgram: Int) {
        val newMin = minNote.coerceIn(0, 120)
        val newMax = maxNote.coerceIn(newMin + 12, 127)
        val geometryChanged = newMin != noteRangeMin || newMax != noteRangeMax

        if (channel != this.channel || geometryChanged || initialProgram != fallbackProgram) {
            java.util.Arrays.fill(level, 0f)
            needsSync = true
        }
        this.channel = channel
        noteRangeMin = newMin
        noteRangeMax = newMax
        fallbackProgram = initialProgram
        colorProgram = -1  // recalculé à la prochaine image

        if (geometryChanged || whiteNotes.isEmpty()) computeGeometry()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        needsSync = true
        lastFrameNanos = 0L
        MidiFrameClock.register(this)
    }

    override fun onDetachedFromWindow() {
        MidiFrameClock.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (!isShown) {
            // Caché : on ne calcule rien, mais on se resynchronise dès qu'on redevient visible
            needsSync = true
            lastFrameNanos = 0L
            return
        }

        val dtMs = if (lastFrameNanos == 0L) 16f
        else min((frameTimeNanos - lastFrameNanos) / 1_000_000f, 50f)
        lastFrameNanos = frameTimeNanos

        var changed = false

        val program = MidiLiveState.program(channel).takeIf { it >= 0 } ?: fallbackProgram
        if (program != colorProgram) {
            colorProgram = program
            baseColor = ColorSettingsManager.getNoteColor(channel, program)
            changed = true
        }

        val version = MidiLiveState.notesVersion()
        if (version != seenNotesVersion || fading || needsSync) {
            seenNotesVersion = version
            needsSync = false
            var stillFading = false
            val fadeStep = dtMs / RELEASE_FADE_MS
            for (note in noteRangeMin until noteRangeMax) {
                val velocity = MidiLiveState.velocity(channel, note)
                val current = level[note]
                if (velocity > 0) {
                    val target = 0.55f + 0.45f * (velocity / 127f)
                    if (current != target) {
                        level[note] = target
                        changed = true
                    }
                } else if (current > 0f) {
                    level[note] = (current - fadeStep).coerceAtLeast(0f)
                    changed = true
                    if (level[note] > 0f) stillFading = true
                }
            }
            fading = stillFading
        }

        if (changed) invalidate()
    }

    private fun isBlack(note: Int) = BLACK_PITCH_CLASSES[note % 12]

    private fun computeGeometry() {
        var whiteCount = 0
        for (n in noteRangeMin until noteRangeMax) if (!isBlack(n)) whiteCount++
        whiteNotes = IntArray(whiteCount)

        val blacks = ArrayList<Int>()
        var w = 0
        for (n in noteRangeMin until noteRangeMax) {
            if (isBlack(n)) blacks.add(n) else whiteNotes[w++] = n
        }
        blackNotes = blacks.toIntArray()
        blackLeft = FloatArray(blackNotes.size)
        layoutKeys()
    }

    private fun layoutKeys() {
        if (whiteNotes.isEmpty() || width == 0 || height == 0) return

        whiteKeyWidth = width.toFloat() / whiteNotes.size
        blackKeyWidth = whiteKeyWidth * BLACK_KEY_WIDTH_RATIO
        blackKeyHeight = height * BLACK_KEY_HEIGHT_RATIO
        labelPaint.textSize = (whiteKeyWidth * 0.42f).coerceIn(8f * density, 11f * density)

        // Une touche noire est centrée sur la frontière entre la blanche d'avant et celle d'après
        for (i in blackNotes.indices) {
            val whiteIndex = whiteNotes.indexOfLast { it < blackNotes[i] }
            blackLeft[i] = (whiteIndex + 1) * whiteKeyWidth - blackKeyWidth / 2f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (whiteNotes.isEmpty()) computeGeometry() else layoutKeys()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (DEFAULT_HEIGHT_DP * density).roundToInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> desired.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        if (whiteNotes.isEmpty()) return
        val h = height.toFloat()

        // Touches blanches. Le haut est dessiné hors de la vue pour n'arrondir que le bas.
        for (i in whiteNotes.indices) {
            val note = whiteNotes[i]
            val left = i * whiteKeyWidth + gap / 2f
            rect.set(left, -radius, left + whiteKeyWidth - gap, h)

            val lit = level[note]
            fillPaint.color = if (lit > 0f) {
                ColorUtils.blendARGB(WHITE_KEY, baseColor, lit)
            } else {
                WHITE_KEY
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            // Léger ombrage en bas de la touche, pour le relief
            fillPaint.color = ColorUtils.setAlphaComponent(WHITE_KEY_SHADE, if (lit > 0f) 60 else 150)
            canvas.drawRect(rect.left + radius, h - 2f * density, rect.right - radius, h - radius, fillPaint)

            if (note % 12 == 0) {
                val label = "C${note / 12 - 1}"
                labelPaint.color = if (lit > 0.5f) BLACK_KEY else LABEL
                canvas.drawText(label, rect.centerX(), h - 5f * density, labelPaint)
            }
        }

        // Touches noires par-dessus
        for (i in blackNotes.indices) {
            val note = blackNotes[i]
            rect.set(blackLeft[i], -radius, blackLeft[i] + blackKeyWidth, blackKeyHeight)

            val lit = level[note]
            fillPaint.color = if (lit > 0f) {
                ColorUtils.blendARGB(BLACK_KEY, ColorUtils.blendARGB(baseColor, Color.BLACK, 0.2f), lit)
            } else {
                BLACK_KEY
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }
}
