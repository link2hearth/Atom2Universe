package com.Atom2Universe.app.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SokobanBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: SokobanGame? = null
    var onChanged: (() -> Unit)? = null
    var onSolved: (() -> Unit)? = null

    private var solved = false
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    private val accent: Int = run {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(
                com.Atom2Universe.app.R.attr.a2uMidiAccent, tv, true)) tv.data
        else 0xFF38BDF8.toInt()
    }

    private val paintWall = fill(0xFF334155.toInt())
    private val paintFloor = fill(0xFF1E293B.toInt())
    private val paintFloorBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF273449.toInt()
        strokeWidth = 2f
    }
    private val paintGoal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF22C55E.toInt()
        strokeWidth = 4f
    }
    private val paintBox = fill(0xFFB45309.toInt())
    private val paintBoxTop = fill(0xFFD97706.toInt())
    private val paintBoxDone = fill(0xFF16A34A.toInt())
    private val paintBoxDoneTop = fill(0xFF22C55E.toInt())
    private val paintPlayer = fill(accent)
    private val paintPlayerCore = fill(0xFF0F172A.toInt())

    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = c
    }

    // Swipe
    private var downX = 0f
    private var downY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }

    private fun recalcLayout() {
        val p = game?.puzzle ?: return
        val cellW = width.toFloat() / p.width
        val cellH = height.toFloat() / p.height
        cellSize = minOf(cellW, cellH)
        offsetX = (width - cellSize * p.width) / 2f
        offsetY = (height - cellSize * p.height) / 2f
    }

    fun loadGame(g: SokobanGame) {
        game = g
        solved = false
        recalcLayout()
        invalidate()
    }

    /** Joue un déplacement et déclenche les rappels. Utilisé par le swipe et le D-pad. */
    fun move(dir: SokobanDir) {
        if (solved) return
        val g = game ?: return
        if (!g.move(dir)) return
        invalidate()
        onChanged?.invoke()
        if (g.isSolved()) {
            solved = true
            onSolved?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val p = g.puzzle ?: return
        val r = cellSize * 0.12f
        val inset = cellSize * 0.04f

        for (y in 0 until p.height) {
            for (x in 0 until p.width) {
                val idx = y * p.width + x
                val cx = offsetX + x * cellSize
                val cy = offsetY + y * cellSize
                val l = cx + inset; val t = cy + inset
                val rr = cx + cellSize - inset; val bb = cy + cellSize - inset

                if (p.isWall(idx)) {
                    canvas.drawRoundRect(l, t, rr, bb, r, r, paintWall)
                    continue
                }
                canvas.drawRoundRect(l, t, rr, bb, r, r, paintFloor)
                canvas.drawRoundRect(l, t, rr, bb, r, r, paintFloorBorder)

                if (idx in p.goals) {
                    val g0 = cellSize * 0.30f
                    canvas.drawRoundRect(cx + g0, cy + g0, cx + cellSize - g0, cy + cellSize - g0,
                        r * 0.5f, r * 0.5f, paintGoal)
                }
            }
        }

        // Caisses
        for (box in g.boxes) {
            val x = box % p.width; val y = box / p.width
            val cx = offsetX + x * cellSize
            val cy = offsetY + y * cellSize
            val onGoal = box in p.goals
            val m = cellSize * 0.14f
            canvas.drawRoundRect(cx + m, cy + m, cx + cellSize - m, cy + cellSize - m, r, r,
                if (onGoal) paintBoxDone else paintBox)
            val m2 = cellSize * 0.26f
            canvas.drawRoundRect(cx + m2, cy + m2, cx + cellSize - m2, cy + cellSize - m2, r * 0.6f, r * 0.6f,
                if (onGoal) paintBoxDoneTop else paintBoxTop)
        }

        // Joueur
        val px = g.player % p.width; val py = g.player / p.width
        val pcx = offsetX + px * cellSize + cellSize / 2f
        val pcy = offsetY + py * cellSize + cellSize / 2f
        canvas.drawCircle(pcx, pcy, cellSize * 0.34f, paintPlayer)
        canvas.drawCircle(pcx, pcy, cellSize * 0.14f, paintPlayerCore)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = cellSize * 0.4f
                if (abs(dx) < threshold && abs(dy) < threshold) return true
                val dir = if (abs(dx) > abs(dy)) {
                    if (dx > 0) SokobanDir.RIGHT else SokobanDir.LEFT
                } else {
                    if (dy > 0) SokobanDir.DOWN else SokobanDir.UP
                }
                move(dir)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
