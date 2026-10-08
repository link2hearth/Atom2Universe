package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R

/**
 * Mini-clavier de référence montrant toutes les octaves MIDI (0-9)
 * avec la zone active surlignée.
 *
 * Affiche les noms des Do (C0, C1, etc.) pour les musiciens.
 * Permet de cliquer pour déplacer la zone active.
 */
class OctaveRangeIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Piano standard: A0 (21) à C8 (108), mais MIDI va de 0 à 127
        // On affiche les octaves 0 à 8 (C0 à B8)
        private const val TOTAL_OCTAVES = 9  // C0 à C8
        private const val NOTES_PER_OCTAVE = 12
        private const val WHITE_KEYS_PER_OCTAVE = 7
    }

    // Plage active (en numéros d'octave, 0-8)
    private var activeOctaveMin = 3  // C3
    private var activeOctaveMax = 5  // C5 (donc 3 octaves: C3, C4, C5)

    // Nombre d'octaves actives
    private var activeOctaveCount: Int
        get() = activeOctaveMax - activeOctaveMin + 1
        set(value) {
            val count = value.coerceIn(1, TOTAL_OCTAVES)
            // Centrer autour de C4 (octave 4)
            val center = (activeOctaveMin + activeOctaveMax) / 2
            val halfCount = count / 2
            activeOctaveMin = (center - halfCount).coerceIn(0, TOTAL_OCTAVES - count)
            activeOctaveMax = activeOctaveMin + count - 1
            invalidate()
            onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
        }

    // Paints
    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }

    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#303030")
        style = Paint.Style.FILL
    }

    private val activeOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.midi_accent)
        alpha = 80
        style = Paint.Style.FILL
    }

    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.midi_accent)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.midi_accent)
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // Dimensions calculées
    private var octaveWidth = 0f
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var keyboardTop = 0f
    private var keyboardHeight = 0f

    // Callback
    var onRangeChanged: ((noteMin: Int, noteMax: Int) -> Unit)? = null

    init {
        // Ajuster la taille du texte selon la densité
        val density = context.resources.displayMetrics.density
        textPaint.textSize = 10f * density
        activeTextPaint.textSize = 10f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (48 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val padding = 4f * resources.displayMetrics.density
        octaveWidth = (w - 2 * padding) / TOTAL_OCTAVES
        whiteKeyWidth = octaveWidth / WHITE_KEYS_PER_OCTAVE
        blackKeyWidth = whiteKeyWidth * 0.6f

        keyboardTop = h * 0.35f  // Espace pour les labels en haut
        keyboardHeight = h - keyboardTop - padding
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 4f * resources.displayMetrics.density

        // Dessiner chaque octave
        for (octave in 0 until TOTAL_OCTAVES) {
            val octaveX = padding + octave * octaveWidth

            // Dessiner les touches blanches
            for (whiteKey in 0 until WHITE_KEYS_PER_OCTAVE) {
                val keyX = octaveX + whiteKey * whiteKeyWidth
                val rect = RectF(keyX, keyboardTop, keyX + whiteKeyWidth - 1, keyboardTop + keyboardHeight)
                canvas.drawRect(rect, whiteKeyPaint)
                canvas.drawRect(rect, keyBorderPaint)
            }

            // Dessiner les touches noires
            val blackKeyPositions = listOf(0, 1, 3, 4, 5)  // C#, D#, F#, G#, A#
            for (pos in blackKeyPositions) {
                val keyX = octaveX + (pos + 1) * whiteKeyWidth - blackKeyWidth / 2
                val rect = RectF(keyX, keyboardTop, keyX + blackKeyWidth, keyboardTop + keyboardHeight * 0.6f)
                canvas.drawRect(rect, blackKeyPaint)
            }

            // Dessiner le label du Do
            val isActive = octave in activeOctaveMin..activeOctaveMax
            val labelPaint = if (isActive) activeTextPaint else textPaint
            val label = "C$octave"
            canvas.drawText(label, octaveX + whiteKeyWidth / 2, keyboardTop - 6f * resources.displayMetrics.density, labelPaint)
        }

        // Dessiner la zone active (overlay)
        val activeStartX = padding + activeOctaveMin * octaveWidth
        val activeEndX = padding + (activeOctaveMax + 1) * octaveWidth
        val activeRect = RectF(activeStartX, keyboardTop, activeEndX, keyboardTop + keyboardHeight)
        canvas.drawRect(activeRect, activeOverlayPaint)
        canvas.drawRect(activeRect, activeBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val padding = 4f * resources.displayMetrics.density
            val touchX = event.x - padding
            val clickedOctave = (touchX / octaveWidth).toInt().coerceIn(0, TOTAL_OCTAVES - 1)

            // Centrer la zone active sur l'octave cliquée
            val halfRange = activeOctaveCount / 2
            val newMin = (clickedOctave - halfRange).coerceIn(0, TOTAL_OCTAVES - activeOctaveCount)
            val newMax = newMin + activeOctaveCount - 1

            if (newMin != activeOctaveMin || newMax != activeOctaveMax) {
                activeOctaveMin = newMin
                activeOctaveMax = newMax
                invalidate()
                onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Définit la plage d'octaves active
     */
    fun setActiveRange(octaveMin: Int, octaveMax: Int) {
        activeOctaveMin = octaveMin.coerceIn(0, TOTAL_OCTAVES - 1)
        activeOctaveMax = octaveMax.coerceIn(activeOctaveMin, TOTAL_OCTAVES - 1)
        invalidate()
    }

    /**
     * Définit le nombre d'octaves à afficher
     */
    fun setOctaveCount(count: Int) {
        activeOctaveCount = count
    }

    /**
     * Récupère le nombre d'octaves actives
     */
    fun getOctaveCount(): Int = activeOctaveCount

    /**
     * Déplace la zone active vers les graves
     */
    fun shiftDown(): Boolean {
        if (activeOctaveMin > 0) {
            activeOctaveMin--
            activeOctaveMax--
            invalidate()
            onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
            return true
        }
        return false
    }

    /**
     * Déplace la zone active vers les aigus
     */
    fun shiftUp(): Boolean {
        if (activeOctaveMax < TOTAL_OCTAVES - 1) {
            activeOctaveMin++
            activeOctaveMax++
            invalidate()
            onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
            return true
        }
        return false
    }

    /**
     * Augmente le nombre d'octaves
     */
    fun increaseOctaveCount(): Boolean {
        if (activeOctaveCount < TOTAL_OCTAVES) {
            // Étendre vers le bas si possible, sinon vers le haut
            if (activeOctaveMin > 0) {
                activeOctaveMin--
            } else if (activeOctaveMax < TOTAL_OCTAVES - 1) {
                activeOctaveMax++
            } else {
                return false
            }
            invalidate()
            onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
            return true
        }
        return false
    }

    /**
     * Diminue le nombre d'octaves
     */
    fun decreaseOctaveCount(): Boolean {
        if (activeOctaveCount > 1) {
            // Réduire depuis le bas
            activeOctaveMin++
            invalidate()
            onRangeChanged?.invoke(activeOctaveMin * 12, (activeOctaveMax + 1) * 12 - 1)
            return true
        }
        return false
    }

    /**
     * Récupère la note MIDI minimale de la plage active
     */
    fun getNoteMin(): Int = activeOctaveMin * 12

    /**
     * Récupère la note MIDI maximale de la plage active
     */
    fun getNoteMax(): Int = (activeOctaveMax + 1) * 12 - 1
}
