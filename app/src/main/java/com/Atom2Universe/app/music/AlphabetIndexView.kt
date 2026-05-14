package com.Atom2Universe.app.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

/**
 * Vue personnalisée affichant une barre alphabétique verticale (0-9, A-Z)
 * pour la navigation rapide dans les listes triées alphabétiquement.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Lettres de l'index: # pour chiffres/symboles, puis A-Z
    private val letters = listOf("#") + ('A'..'Z').map { it.toString() }

    private var listener: OnLetterSelectedListener? = null
    private var selectedIndex = -1
    private var isTracking = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var letterHeight = 0f

    init {
        // Couleurs par défaut
        textPaint.color = ContextCompat.getColor(context, R.color.music_text_secondary)
        selectedPaint.color = ContextCompat.getColor(context, R.color.music_accent)
        backgroundPaint.color = ContextCompat.getColor(context, R.color.music_surface)
        backgroundPaint.alpha = 200
    }

    interface OnLetterSelectedListener {
        /**
         * Appelé quand l'utilisateur sélectionne une lettre.
         * @param letter La lettre sélectionnée ("#" pour chiffres/symboles)
         */
        fun onLetterSelected(letter: String)

        /**
         * Appelé quand l'utilisateur commence à toucher la barre.
         */
        fun onStartTouch() {}

        /**
         * Appelé quand l'utilisateur relâche la barre.
         */
        fun onStopTouch() {}
    }

    fun setOnLetterSelectedListener(listener: OnLetterSelectedListener) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Largeur fixe de 24dp
        val desiredWidth = (24 * resources.displayMetrics.density).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculer la hauteur de chaque lettre
        letterHeight = h.toFloat() / letters.size
        // Ajuster la taille du texte pour qu'il tienne bien
        textPaint.textSize = (letterHeight * 0.7f).coerceIn(8f * resources.displayMetrics.density, 14f * resources.displayMetrics.density)
        selectedPaint.textSize = textPaint.textSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fond semi-transparent si on est en train de toucher
        if (isTracking) {
            canvas.drawRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                width / 2f, width / 2f,
                backgroundPaint
            )
        }

        val centerX = width / 2f

        letters.forEachIndexed { index, letter ->
            val y = letterHeight * index + letterHeight / 2f + textPaint.textSize / 3f
            val paint = if (index == selectedIndex && isTracking) selectedPaint else textPaint
            canvas.drawText(letter, centerX, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTracking = true
                listener?.onStartTouch()
                updateSelectedLetter(event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelectedLetter(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                selectedIndex = -1
                listener?.onStopTouch()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelectedLetter(y: Float) {
        val index = (y / letterHeight).toInt().coerceIn(0, letters.size - 1)
        if (index != selectedIndex) {
            selectedIndex = index
            listener?.onLetterSelected(letters[index])
            invalidate()
        }
    }

    /**
     * Définit les lettres disponibles (celles qui ont des éléments correspondants).
     * Les lettres non disponibles seront grisées.
     */
    fun setAvailableLetters(availableLetters: Set<String>) {
        // Pour l'instant on affiche toutes les lettres
        // On pourrait griser celles non disponibles dans une future version
        invalidate()
    }
}
