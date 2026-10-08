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
 *
 * En mode quantique, détecte aussi le glissement doigt d'une case vers une case adjacente
 * pour déclencher onCellMerge (fusion ciblée coûtant un joker).
 */
class Game2048View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface SwipeListener {
        fun onSwipe(direction: Game2048Logic.Direction)
        fun onCellMerge(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {}
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

    private val darkTextValues = setOf(2, 4)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val density = context.resources.displayMetrics.density
    private val dragHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f * density
    }
    private val dragTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7C3AED")
        strokeWidth = 4f * density
    }

    private val GRID_BG_COLOR = Color.parseColor("#BBADA0")
    private val CELL_COLOR = Color.parseColor("#CDC1B4")
    private val DARK_TEXT = Color.parseColor("#776E65")
    private val LIGHT_TEXT = Color.parseColor("#F9F6F2")

    private var cellSize = 0f
    private var gap = 0f
    private var cornerRadius = 0f

    // État du drag quantique
    private var dragSourceRow = -1
    private var dragSourceCol = -1
    private var dragHoverRow = -1
    private var dragHoverCol = -1
    private var touchDownX = 0f
    private var touchDownY = 0f

    // Seuils de swipe en dp
    private val SWIPE_THRESHOLD = 20f * density
    private val SWIPE_VELOCITY_THRESHOLD = 80f * density
    // Distance max pour qu'un mouvement soit considéré comme un drag ciblé (pas un swipe)
    private val DRAG_MAX_DIST = 2.2f  // en unités cellSize (calculé après onSizeChanged)

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

            if (absDx < SWIPE_THRESHOLD && absDy < SWIPE_THRESHOLD) return false
            if (kotlin.math.abs(velocityX) < SWIPE_VELOCITY_THRESHOLD &&
                kotlin.math.abs(velocityY) < SWIPE_VELOCITY_THRESHOLD) return false

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

        bgPaint.color = GRID_BG_COLOR
        canvas.drawRoundRect(0f, 0f, side, side, cornerRadius * 1.5f, cornerRadius * 1.5f, bgPaint)

        cellPaint.color = CELL_COLOR
        for (row in 0 until n) {
            for (col in 0 until n) {
                val rect = cellRect(row, col)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
            }
        }

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

        // Surbrillance de la case source du drag quantique
        if (dragSourceRow >= 0 && dragSourceCol >= 0) {
            canvas.drawRoundRect(
                cellRect(dragSourceRow, dragSourceCol),
                cornerRadius, cornerRadius, dragHighlightPaint
            )
        }

        // Surbrillance de la case destination survolée (si adjacente)
        if (dragHoverRow >= 0 && dragHoverCol >= 0 &&
            (dragHoverRow != dragSourceRow || dragHoverCol != dragSourceCol)) {
            val dr = kotlin.math.abs(dragHoverRow - dragSourceRow)
            val dc = kotlin.math.abs(dragHoverCol - dragSourceCol)
            if (dr + dc == 1) {
                canvas.drawRoundRect(
                    cellRect(dragHoverRow, dragHoverCol),
                    cornerRadius, cornerRadius, dragTargetPaint
                )
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

    private fun hitTestRow(y: Float): Int {
        val g = game ?: return -1
        for (row in 0 until g.size) {
            val top = gap + row * (cellSize + gap)
            if (y >= top && y <= top + cellSize) return row
        }
        return -1
    }

    private fun hitTestCol(x: Float): Int {
        val g = game ?: return -1
        for (col in 0 until g.size) {
            val left = gap + col * (cellSize + gap)
            if (x >= left && x <= left + cellSize) return col
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game
        val jokerReady = g?.quantumMode == true && g.nextMoveIsJoker

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touchDownX = event.x
                touchDownY = event.y

                if (jokerReady && g != null) {
                    val r = hitTestRow(event.y)
                    val c = hitTestCol(event.x)
                    if (r >= 0 && c >= 0 && g.board[r * g.size + c] != 0) {
                        dragSourceRow = r
                        dragSourceCol = c
                        dragHoverRow = r
                        dragHoverCol = c
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragSourceRow >= 0 && cellSize > 0f) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    // Si le doigt s'éloigne trop, c'est un swipe → abandonner le drag ciblé
                    if (kotlin.math.abs(dx) > cellSize * DRAG_MAX_DIST ||
                        kotlin.math.abs(dy) > cellSize * DRAG_MAX_DIST) {
                        dragSourceRow = -1
                        dragSourceCol = -1
                        dragHoverRow = -1
                        dragHoverCol = -1
                        invalidate()
                    } else {
                        // Mettre à jour la case survolée pour le retour visuel
                        val hr = hitTestRow(event.y)
                        val hc = hitTestCol(event.x)
                        if (hr != dragHoverRow || hc != dragHoverCol) {
                            dragHoverRow = hr
                            dragHoverCol = hc
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val srcRow = dragSourceRow
                val srcCol = dragSourceCol
                dragSourceRow = -1
                dragSourceCol = -1
                dragHoverRow = -1
                dragHoverCol = -1
                invalidate()

                if (srcRow >= 0 && srcCol >= 0) {
                    val endRow = hitTestRow(event.y)
                    val endCol = hitTestCol(event.x)
                    if (endRow >= 0 && endCol >= 0) {
                        val dr = kotlin.math.abs(endRow - srcRow)
                        val dc = kotlin.math.abs(endCol - srcCol)
                        val totalDist = kotlin.math.sqrt(
                            ((event.x - touchDownX) * (event.x - touchDownX) +
                             (event.y - touchDownY) * (event.y - touchDownY)).toDouble()
                        ).toFloat()
                        if (dr + dc == 1 && cellSize > 0f && totalDist < cellSize * DRAG_MAX_DIST) {
                            swipeListener?.onCellMerge(srcRow, srcCol, endRow, endCol)
                            return true  // Consommé — pas de swipe
                        }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                dragSourceRow = -1
                dragSourceCol = -1
                dragHoverRow = -1
                dragHoverCol = -1
                invalidate()
            }
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    fun refresh() {
        recomputeMetrics()
        invalidate()
    }
}
