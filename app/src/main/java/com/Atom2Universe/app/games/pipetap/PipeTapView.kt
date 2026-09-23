package com.Atom2Universe.app.games.pipetap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/** A miniature hydraulic console. Simulation stays in PipeTapGame; flow is purely visual. */
class PipeTapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    interface OnTileRotatedListener { fun onTileRotated(solved: Boolean) }

    var game: PipeTapGame? = null
    var listener: OnTileRotatedListener? = null
    var animationsActive = true
        set(value) { field = value; updateAnimation() }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val board = RectF()
    private val density = resources.displayMetrics.density
    private val directions = intArrayOf(1, 2, 4, 8)
    private val dx = intArrayOf(0, 1, 0, -1)
    private val dy = intArrayOf(-1, 0, 1, 0)
    private var tile = 0f
    private var reached = BooleanArray(0)
    private var arrivals = LongArray(0)
    private var parents = IntArray(0)
    private var turns = LongArray(0)
    private var turnOffsets = FloatArray(0)
    private var pressedTile = -1
    private var frameTime = 0L
    private var fillDuration = 180f

    fun flowSettlingDelay(): Long {
        if (!ValueAnimator.areAnimatorsEnabled()) return 0L
        val lastArrival = arrivals.indices.filter { reached[it] }.maxOfOrNull { arrivals[it] }
            ?: return 0L
        return (lastArrival + fillDuration.toLong() - SystemClock.uptimeMillis()).coerceAtLeast(0L)
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }
    private val navy = Color.rgb(9, 23, 30)
    private val edge = Color.rgb(47, 73, 82)
    private val metal = Color.rgb(85, 115, 121)
    private val brass = Color.rgb(201, 156, 89)
    private val water = Color.rgb(55, 218, 203)

    init { isClickable = true }

    private fun updateAnimation() {
        val run = animationsActive && isAttachedToWindow && isShown &&
            windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && game != null
        if (run && !animator.isStarted) animator.start()
        else if (!run) animator.cancel()
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateAnimation()
    }

    private fun geometry() {
        val n = game?.grid?.size ?: 0
        if (n == 0) { tile = 0f; return }
        val margin = 16f * density
        tile = ((minOf(width, height) - margin * 2) / n).coerceAtLeast(0f)
        val side = tile * n
        board.set((width - side) / 2, (height - side) / 2,
            (width + side) / 2, (height + side) / 2)
    }

    private fun fill(color: Int, alpha: Int = 255) {
        ink.style = Paint.Style.FILL
        ink.color = color
        ink.alpha = alpha
    }
    private fun stroke(color: Int, width: Float, alpha: Int = 255) {
        fill(color, alpha)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = width
        ink.strokeCap = Paint.Cap.ROUND
    }
    private fun round(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, ink)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(navy)
        val g = game ?: return
        geometry()
        if (tile <= 0f || reached.size != g.grid.size * g.grid.size) return
        frameTime = SystemClock.uptimeMillis()
        val animated = animator.isRunning
        val phase = if (animated) (frameTime % 2400L) / 2400f else 0f
        val n = g.grid.size
        // Recessed frame, corner fasteners and individual enamel panels.
        fill(Color.rgb(4, 13, 19))
        round(canvas, board.left - 9 * density, board.top - 9 * density,
            board.right + 9 * density, board.bottom + 9 * density, 12 * density)
        stroke(edge, density)
        round(canvas, board.left - 9 * density, board.top - 9 * density,
            board.right + 9 * density, board.bottom + 9 * density, 12 * density)
        for (corner in 0..3) {
            val x = if (corner % 2 == 0) board.left - 4 * density else board.right + 4 * density
            val y = if (corner < 2) board.top - 4 * density else board.bottom + 4 * density
            fill(brass); canvas.drawCircle(x, y, 1.6f * density, ink)
        }
        for (y in 0 until n) for (x in 0 until n) {
            val id = y * n + x
            val cx = board.left + (x + .5f) * tile
            val cy = board.top + (y + .5f) * tile
            val half = tile / 2
            val amount = if (!reached[id]) 0f else if (!animated) 1f
                else ((frameTime - arrivals[id]) / fillDuration).coerceIn(0f, 1f)
            fill(if ((x + y) % 2 == 0) Color.rgb(20, 40, 49) else Color.rgb(23, 44, 53))
            round(canvas, cx - half + 1, cy - half + 1, cx + half - 1, cy + half - 1, tile * .09f)
            stroke(edge, density * .6f)
            round(canvas, cx - half + 2, cy - half + 2, cx + half - 2, cy + half - 2, tile * .08f)
            if (g.grid[y][x] == 0) continue
            canvas.save()
            // Clip before rotating so the pipe and water stay inside their tile.
            canvas.clipRect(
                board.left + x * tile, board.top + y * tile,
                board.left + (x + 1) * tile, board.top + (y + 1) * tile
            )
            canvas.rotate(if (animated) rotationOffset(id, frameTime) else 0f, cx, cy)
            val mask = g.grid[y][x]
            // Concentric strokes give the pipes a solid wall and a recessed glass channel.
            for (layer in 0..3) {
                val color = when (layer) { 0 -> Color.rgb(3, 14, 20); 1 -> metal; 2 -> edge; else -> navy }
                val width = tile * when (layer) { 0 -> .36f; 1 -> .30f; 2 -> .245f; else -> .18f }
                stroke(color, width)
                // Flat mouths meet at the boundary; round caps would overlap the
                // next pipe and close its channel with a U-shaped plug.
                ink.strokeCap = Paint.Cap.BUTT
                for (d in 0..3) if (mask and directions[d] != 0)
                    canvas.drawLine(cx, cy, cx + dx[d] * half, cy + dy[d] * half, ink)
                // Round only the centre, keeping elbows and terminal hubs smooth.
                fill(color)
                canvas.drawCircle(cx, cy, width / 2f, ink)
            }
            // Entry fills toward the hub, then branches fill outward.
            if (amount > 0f) {
                for (d in 0..3) if (mask and directions[d] != 0) {
                    val incoming = parents[id] == d
                    val from = if (incoming) 1f else 0f
                    val to = if (incoming) 1f - (amount * 2).coerceAtMost(1f)
                        else if (parents[id] < 0) amount else (amount * 2 - 1).coerceAtLeast(0f)
                    if (from != to) {
                        stroke(water, tile * .145f)
                        canvas.drawLine(cx + dx[d] * half * from, cy + dy[d] * half * from,
                            cx + dx[d] * half * to, cy + dy[d] * half * to, ink)
                        stroke(Color.rgb(158, 255, 237), tile * .035f, 170)
                        canvas.drawLine(cx + dx[d] * half * from, cy + dy[d] * half * from,
                            cx + dx[d] * half * to, cy + dy[d] * half * to, ink)
                    }
                    if (amount == 1f && animated) {
                        val p = (phase * 3 + (id % 7) / 7f) % 1f
                        val travel = if (incoming) 1f - p else p
                        fill(Color.rgb(222, 255, 246), 210)
                        canvas.drawCircle(cx + dx[d] * half * travel, cy + dy[d] * half * travel,
                            tile * .025f, ink)
                    }
                }
            }
            // Brass compression collars keep each rotatable segment easy to read.
            for (d in 0..3) if (mask and directions[d] != 0) {
                val px = cx + dx[d] * tile * .40f
                val py = cy + dy[d] * tile * .40f
                stroke(brass, tile * .045f)
                canvas.drawLine(px - dy[d] * tile * .145f, py + dx[d] * tile * .145f,
                    px + dy[d] * tile * .145f, py - dx[d] * tile * .145f, ink)
            }
            val source = x == g.source.first && y == g.source.second
            if (source) {
                fill(brass); canvas.drawCircle(cx, cy, tile * .235f, ink)
                fill(navy); canvas.drawCircle(cx, cy, tile * .185f, ink)
                stroke(brass, tile * .035f)
                rect.set(cx - tile * .13f, cy - tile * .13f, cx + tile * .13f, cy + tile * .13f)
                canvas.drawArc(rect, -220f, 260f, false, ink)
                stroke(water, tile * .035f)
                val angle = (-.7f + if (animated) sin(phase * Math.PI * 2).toFloat() * .12f else 0f)
                canvas.drawLine(cx, cy, cx + kotlin.math.cos(angle) * tile * .11f,
                    cy + sin(angle) * tile * .11f, ink)
                fill(brass); canvas.drawCircle(cx, cy, tile * .035f, ink)
            } else if (Integer.bitCount(mask) != 2) {
                fill(edge); canvas.drawCircle(cx, cy, tile * .13f, ink)
                fill(if (amount > .5f) water else metal)
                canvas.drawCircle(cx, cy, tile * .075f, ink)
            }
            canvas.restore()
        }
    }

    private fun rotationOffset(id: Int, now: Long): Float {
        val t = ((now - turns[id]) / 190f).coerceIn(0f, 1f)
        return turnOffsets[id] * (1f - t) * (1f - t) * (1f - t)
    }

    private fun tileAt(x: Float, y: Float): Int {
        geometry()
        if (tile <= 0f || !board.contains(x, y)) return -1
        val n = game?.grid?.size ?: return -1
        return ((y - board.top) / tile).toInt() * n + ((x - board.left) / tile).toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedTile = tileAt(event.x, event.y); return pressedTile >= 0 }
            MotionEvent.ACTION_MOVE -> if (tileAt(event.x, event.y) != pressedTile) pressedTile = -1
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> pressedTile = -1
            MotionEvent.ACTION_UP -> {
                val id = tileAt(event.x, event.y)
                val valid = id >= 0 && id == pressedTile
                pressedTile = -1
                val g = game ?: return true
                if (!valid || g.solved || id >= turns.size) return true
                val n = g.grid.size
                if (g.grid[id / n][id % n] == 0) return true
                performClick()
                val now = SystemClock.uptimeMillis()
                turnOffsets[id] = rotationOffset(id, now) - 90f
                turns[id] = now
                g.rotateTileAt(id % n, id / n)
                updateFlow(false)
                invalidate()
                listener?.onTileRotated(g.solved)
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    /** BFS schedules each newly wet tile after its upstream neighbour, even on rapid taps. */
    private fun updateFlow(reset: Boolean) {
        val g = game ?: return
        val n = g.grid.size
        if (n == 0) return
        val count = n * n
        // Keep the full wave under about four seconds, including on the largest grid.
        fillDuration = (2400f / count).coerceIn(60f, 180f)
        val previous = if (!reset && reached.size == count) reached else BooleanArray(count)
        if (reset || arrivals.size != count) {
            arrivals = LongArray(count)
            turns = LongArray(count)
            turnOffsets = FloatArray(count)
        }
        reached = BooleanArray(count)
        parents = IntArray(count) { -1 }
        val source = g.source.second * n + g.source.first
        if (source !in 0 until count) return
        val now = SystemClock.uptimeMillis()
        val queue = ArrayDeque<Int>()
        queue.addLast(source)
        reached[source] = true
        if (!previous[source]) arrivals[source] = now
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val x = id % n
            val y = id / n
            for (d in 0..3) {
                val nx = x + dx[d]; val ny = y + dy[d]
                if (nx !in 0 until n || ny !in 0 until n) continue
                val next = ny * n + nx
                val opposite = (d + 2) % 4
                if (reached[next] || g.grid[y][x] and directions[d] == 0 ||
                    g.grid[ny][nx] and directions[opposite] == 0) continue
                reached[next] = true
                parents[next] = opposite
                if (!previous[next]) arrivals[next] = maxOf(now, arrivals[id] + fillDuration.toLong())
                queue.addLast(next)
            }
        }
    }

    fun refresh() { updateFlow(true); updateAnimation() }
}
