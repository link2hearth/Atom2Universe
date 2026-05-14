package com.Atom2Universe.app.games.pipetap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class PipeTapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnTileRotatedListener {
        fun onTileRotated(solved: Boolean)
    }

    var game: PipeTapGame? = null
    var listener: OnTileRotatedListener? = null

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
    }
    private val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
    }
    private val paintDisconnected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#374151")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val paintConnected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06B6D4")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val paintSourceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintSourceRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val cellRect = RectF()
    private var reachable: Set<Int> = emptySet()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        val size = g.difficulty.gridSize
        if (size == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 8f
        val tileSize = minOf((w - padding * 2) / size, (h - padding * 2) / size)
        val startX = (w - tileSize * size) / 2f
        val startY = (h - tileSize * size) / 2f

        canvas.drawRect(0f, 0f, w, h, paintBg)

        val pipeSW = tileSize * 0.28f
        paintDisconnected.strokeWidth = pipeSW
        paintConnected.strokeWidth    = pipeSW

        for (y in 0 until size) {
            for (x in 0 until size) {
                val tx = startX + x * tileSize
                val ty = startY + y * tileSize
                val cx = tx + tileSize / 2f
                val cy = ty + tileSize / 2f

                cellRect.set(tx + 1f, ty + 1f, tx + tileSize - 1f, ty + tileSize - 1f)
                canvas.drawRoundRect(cellRect, 4f, 4f, paintCell)

                val mask = g.grid[y][x]
                if (mask == 0) continue

                val id = y * size + x
                val isConnected = id in reachable
                val isSource = x == g.source.first && y == g.source.second
                val pipePaint = if (isConnected) paintConnected else paintDisconnected
                val halfPipe = pipeSW / 2f

                if (mask and PipeTapGame.NORTH != 0) canvas.drawLine(cx, cy - halfPipe, cx, ty, pipePaint)
                if (mask and PipeTapGame.SOUTH != 0) canvas.drawLine(cx, cy + halfPipe, cx, ty + tileSize, pipePaint)
                if (mask and PipeTapGame.EAST  != 0) canvas.drawLine(cx + halfPipe, cy, tx + tileSize, cy, pipePaint)
                if (mask and PipeTapGame.WEST  != 0) canvas.drawLine(cx - halfPipe, cy, tx, cy, pipePaint)

                val dotR = pipeSW * 0.7f
                if (isSource) {
                    paintSourceFill.color = if (isConnected) Color.parseColor("#F59E0B") else Color.parseColor("#92400E")
                    canvas.drawCircle(cx, cy, dotR, paintSourceFill)
                    paintSourceRing.color = paintSourceFill.color
                    paintSourceRing.strokeWidth = tileSize * 0.06f
                    canvas.drawCircle(cx, cy, dotR + tileSize * 0.09f, paintSourceRing)
                } else {
                    val saved = pipePaint.style
                    pipePaint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, dotR, pipePaint)
                    pipePaint.style = saved
                }
            }
        }

        // Grid lines
        for (i in 0..size) {
            val l = startX + i * tileSize
            val t = startY + i * tileSize
            canvas.drawLine(l, startY, l, startY + tileSize * size, paintGrid)
            canvas.drawLine(startX, t, startX + tileSize * size, t, paintGrid)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val g = game ?: return true
        if (g.solved) return true
        val size = g.difficulty.gridSize

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 8f
        val tileSize = minOf((w - padding * 2) / size, (h - padding * 2) / size)
        val startX = (w - tileSize * size) / 2f
        val startY = (h - tileSize * size) / 2f

        val tx = ((event.x - startX) / tileSize).toInt()
        val ty = ((event.y - startY) / tileSize).toInt()
        if (tx < 0 || ty < 0 || tx >= size || ty >= size) return true
        if (g.grid[ty][tx] == 0) return true

        g.rotateTileAt(tx, ty)
        reachable = g.computeReachable()
        invalidate()
        listener?.onTileRotated(g.solved)
        return true
    }

    fun refresh() {
        reachable = game?.computeReachable() ?: emptySet()
        invalidate()
    }
}
