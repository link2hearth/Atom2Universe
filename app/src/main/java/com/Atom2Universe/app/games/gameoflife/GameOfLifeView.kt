package com.Atom2Universe.app.games.gameoflife

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class GameOfLifeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val BASE_CELL_SIZE = 22f
        private const val MIN_CELL_SIZE = 4f
        private const val MAX_CELL_SIZE = 64f
        private const val GRID_FADE_START = 10f  // en-dessous : pas de grillage
        private const val GRID_FADE_END   = 22f  // au-dessus : grillage plein

        // Encodage compact (x,y) → Long pour les coordonnées infinies
        private fun encode(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
        private fun decodeX(v: Long): Int = (v ushr 32).toInt()
        private fun decodeY(v: Long): Int = v.toInt()
    }

    // Grille infinie — seules les cellules vivantes sont stockées
    private val cells = HashSet<Long>(1024)
    private val nextCells = HashSet<Long>(1024)

    // Viewport en coordonnées monde (originX = cellule monde à x=0 écran)
    private var cellSize = BASE_CELL_SIZE
    private var originX = 0f   // cellule monde correspondant au bord gauche du canvas
    private var originY = 0f

    // Dessin d'un seul doigt
    private var isDrawing = false
    private var hadMultiTouch = false
    private var drawValue = true
    private var lastTouchKey = Long.MIN_VALUE
    private val strokeChanges = mutableListOf<Pair<Long, Boolean>>() // (key, wasAlive)

    // Pan à 2 doigts
    private var lastPanX = 0f
    private var lastPanY = 0f

    var onCellCountChanged: ((alive: Int) -> Unit)? = null

    private val cellPaint = Paint().apply {
        color = Color.parseColor("#7B8CDE")
        isAntiAlias = false
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0D0D1A")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            // coordonnée monde du point focal avant zoom
            val worldX = originX + focusX / cellSize
            val worldY = originY + focusY / cellSize
            cellSize = (cellSize * detector.scaleFactor).coerceIn(MIN_CELL_SIZE, MAX_CELL_SIZE)
            // réancrer le point focal
            originX = worldX - focusX / cellSize
            originY = worldY - focusY / cellSize
            invalidate()
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Centre la vue sur (0,0) la première fois
        if (originX == 0f && originY == 0f && w > 0 && h > 0) {
            originX = -(w / cellSize) / 2f
            originY = -(h / cellSize) / 2f
        }
    }

    fun randomize(density: Float = 0.3f) {
        cells.clear()
        val halfW = ((width / cellSize) / 2f).toInt() + 10
        val halfH = ((height / cellSize) / 2f).toInt() + 10
        for (r in -halfH..halfH) {
            for (c in -halfW..halfW) {
                if (Math.random() < density) cells.add(encode(c, r))
            }
        }
        notifyCount()
        invalidate()
    }

    fun clear() {
        cells.clear()
        notifyCount()
        invalidate()
    }

    fun step() {
        // Compter les voisins de tous les candidats (cellules vivantes + leurs voisins)
        val neighborCounts = HashMap<Long, Int>(cells.size * 9)
        for (key in cells) {
            val x = decodeX(key)
            val y = decodeY(key)
            for (dx in -1..1) for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nk = encode(x + dx, y + dy)
                neighborCounts[nk] = (neighborCounts[nk] ?: 0) + 1
            }
        }
        nextCells.clear()
        for ((key, count) in neighborCounts) {
            val alive = cells.contains(key)
            if ((alive && count in 2..3) || (!alive && count == 3)) {
                nextCells.add(key)
            }
        }
        cells.clear()
        cells.addAll(nextCells)
        nextCells.clear()
        notifyCount()
        invalidate()
    }

    fun countAlive(): Int = cells.size

    private fun notifyCount() {
        post { onCellCountChanged?.invoke(cells.size) }
    }

    fun placePattern(pattern: Array<IntArray>) {
        val offsetC = -(pattern.maxOfOrNull { it.size } ?: 0) / 2
        val offsetR = -pattern.size / 2
        for ((r, row) in pattern.withIndex()) {
            for ((c, cell) in row.withIndex()) {
                if (cell != 0) cells.add(encode(c + offsetC, r + offsetR))
            }
        }
        notifyCount()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Bornes des cellules visibles (en coordonnées monde)
        val startC = floor(originX).toInt() - 1
        val endC   = ceil(originX + w / cellSize).toInt() + 1
        val startR = floor(originY).toInt() - 1
        val endR   = ceil(originY + h / cellSize).toInt() + 1

        // Grillage avec opacité progressive selon le niveau de zoom
        if (cellSize >= GRID_FADE_START) {
            val t = ((cellSize - GRID_FADE_START) / max(1f, GRID_FADE_END - GRID_FADE_START)).coerceIn(0f, 1f)
            val lineAlpha = (60 + t * 90).toInt()  // 60..150 sur 255
            linePaint.color = Color.argb(lineAlpha, 160, 190, 255)
            linePaint.strokeWidth = if (cellSize >= GRID_FADE_END) 1f else 0.8f

            for (c in startC..endC) {
                val px = (c - originX) * cellSize
                canvas.drawLine(px, 0f, px, h, linePaint)
            }
            for (r in startR..endR) {
                val py = (r - originY) * cellSize
                canvas.drawLine(0f, py, w, py, linePaint)
            }
        }

        // Cellules vivantes
        val gap = if (cellSize > 6f) min(1.5f, cellSize * 0.06f) else 0f
        for (key in cells) {
            val cx = decodeX(key)
            val cy = decodeY(key)
            if (cx < startC || cx > endC || cy < startR || cy > endR) continue
            val px = (cx - originX) * cellSize + gap
            val py = (cy - originY) * cellSize + gap
            canvas.drawRect(px, py, px + cellSize - gap * 2, py + cellSize - gap * 2, cellPaint)
        }
    }

    private fun cellAt(screenX: Float, screenY: Float): Long {
        val cx = floor(originX + screenX / cellSize).toInt()
        val cy = floor(originY + screenY / cellSize).toInt()
        return encode(cx, cy)
    }

    private fun applyDraw(key: Long) {
        if (key == lastTouchKey) return
        lastTouchKey = key
        val wasAlive = cells.contains(key)
        strokeChanges.add(key to wasAlive)
        if (drawValue) cells.add(key) else cells.remove(key)
        notifyCount()
        invalidate()
    }

    private fun revertStroke() {
        for ((key, wasAlive) in strokeChanges) {
            if (wasAlive) cells.add(key) else cells.remove(key)
        }
        strokeChanges.clear()
        notifyCount()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hadMultiTouch = false
                strokeChanges.clear()
                isDrawing = true
                val key = cellAt(event.x, event.y)
                drawValue = !cells.contains(key)
                applyDraw(key)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isDrawing) revertStroke()
                isDrawing = false
                hadMultiTouch = true
                lastTouchKey = Long.MIN_VALUE
                if (event.pointerCount >= 2) {
                    lastPanX = (event.getX(0) + event.getX(1)) / 2f
                    lastPanY = (event.getY(0) + event.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    originX -= (midX - lastPanX) / cellSize
                    originY -= (midY - lastPanY) / cellSize
                    invalidate()
                    lastPanX = midX
                    lastPanY = midY
                } else if (isDrawing && !hadMultiTouch) {
                    applyDraw(cellAt(event.x, event.y))
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val idx = if (event.actionIndex == 0) 1 else 0
                    lastPanX = event.getX(idx)
                    lastPanY = event.getY(idx)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                hadMultiTouch = false
                strokeChanges.clear()
                lastTouchKey = Long.MIN_VALUE
            }
        }
        return true
    }
}
