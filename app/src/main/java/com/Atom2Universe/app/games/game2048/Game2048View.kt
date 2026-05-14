package com.Atom2Universe.app.games.game2048

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Vue Canvas du plateau 2048.
 * Dessine les cellules vides et les tuiles, détecte les swipes.
 */
class Game2048View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface SwipeListener {
        fun onSwipe(direction: Game2048Logic.Direction)
    }

    var swipeListener: SwipeListener? = null
    var game: Game2048Logic? = null
        set(value) {
            field = value
            invalidate()
        }

    // Couleurs des tuiles (valeur → couleur de fond)
    private val tileColors = mapOf(
        0 to Color.parseColor("#CDC1B4"),
        2 to Color.parseColor("#EEE4DA"),
        4 to Color.parseColor("#EDE0C8"),
        8 to Color.parseColor("#F2B179"),
        16 to Color.parseColor("#F59563"),
        32 to Color.parseColor("#F67C5F"),
        64 to Color.parseColor("#F65E3B"),
        128 to Color.parseColor("#EDCF72"),
        256 to Color.parseColor("#EDCC61"),
        512 to Color.parseColor("#EDC850"),
        1024 to Color.parseColor("#EDC53F"),
        2048 to Color.parseColor("#EDC22E")
    )

    // Couleurs du texte selon la valeur
    private val darkTextValues = setOf(2, 4)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val GRID_BG_COLOR = Color.parseColor("#BBADA0")
    private val CELL_COLOR = Color.parseColor("#CDC1B4")
    private val DARK_TEXT = Color.parseColor("#776E65")
    private val LIGHT_TEXT = Color.parseColor("#F9F6F2")

    private var cellSize = 0f
    private var gap = 0f
    private var cornerRadius = 0f

    // Détection de swipe
    // Seuils en dp pour être cohérents quelle que soit la densité d'écran
    private val density = context.resources.displayMetrics.density
    private val SWIPE_THRESHOLD = 20f * density        // 20dp — gestes courts acceptés
    private val SWIPE_VELOCITY_THRESHOLD = 80f * density  // 80dp/s — vitesse minimale légère

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val absDx = kotlin.math.abs(dx)
            val absDy = kotlin.math.abs(dy)

            // Rejeter si le déplacement est trop petit dans les deux axes
            if (absDx < SWIPE_THRESHOLD && absDy < SWIPE_THRESHOLD) return false
            // Rejeter si trop lent dans les deux axes
            if (kotlin.math.abs(velocityX) < SWIPE_VELOCITY_THRESHOLD &&
                kotlin.math.abs(velocityY) < SWIPE_VELOCITY_THRESHOLD) return false

            // Direction : l'axe dominant gagne (tolérance 45°)
            val direction = if (absDx >= absDy) {
                if (dx > 0) Game2048Logic.Direction.RIGHT else Game2048Logic.Direction.LEFT
            } else {
                if (dy > 0) Game2048Logic.Direction.DOWN else Game2048Logic.Direction.UP
            }
            swipeListener?.onSwipe(direction)
            return true
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Le plateau est toujours carré, limité par la plus petite dimension
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = min(w, h)
        setMeasuredDimension(side, side)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        recomputeMetrics()
    }

    private fun recomputeMetrics() {
        val g = game ?: return
        val side = min(width, height).toFloat()
        val n = g.size
        gap = side * 0.025f
        cellSize = (side - gap * (n + 1)) / n
        cornerRadius = cellSize * 0.1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return

        if (cellSize <= 0f) recomputeMetrics()
        if (cellSize <= 0f) return

        val n = g.size
        val side = min(width, height).toFloat()

        // Fond du plateau
        bgPaint.color = GRID_BG_COLOR
        canvas.drawRoundRect(0f, 0f, side, side, cornerRadius * 1.5f, cornerRadius * 1.5f, bgPaint)

        // Cellules vides
        cellPaint.color = CELL_COLOR
        for (row in 0 until n) {
            for (col in 0 until n) {
                val rect = cellRect(row, col)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
            }
        }

        // Tuiles
        for (row in 0 until n) {
            for (col in 0 until n) {
                val value = g.board[row * n + col]
                if (value == 0) continue

                val rect = cellRect(row, col)
                tilePaint.color = tileColors[value] ?: Color.parseColor("#3C3A32")
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tilePaint)

                val textColor = if (value in darkTextValues) DARK_TEXT else LIGHT_TEXT
                textPaint.color = textColor
                textPaint.textSize = computeTextSize(value, cellSize)

                val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(value.toString(), rect.centerX(), textY, textPaint)
            }
        }
    }

    private fun cellRect(row: Int, col: Int): RectF {
        val left = gap + col * (cellSize + gap)
        val top = gap + row * (cellSize + gap)
        return RectF(left, top, left + cellSize, top + cellSize)
    }

    private fun computeTextSize(value: Int, cellSizePx: Float): Float {
        return when {
            value < 100 -> cellSizePx * 0.45f
            value < 1000 -> cellSizePx * 0.36f
            value < 10000 -> cellSizePx * 0.28f
            else -> cellSizePx * 0.22f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun refresh() {
        recomputeMetrics()
        invalidate()
    }
}
