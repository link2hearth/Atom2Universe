package com.Atom2Universe.app.games.circles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class CirclesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var game: CirclesGame? = null

    /** Called after each rotation animation ends. */
    var onRotationEnd: ((solved: Boolean) -> Unit)? = null

    /** Rings to highlight for hint display (set from outside). */
    var hintHighlight: Set<Int>? = null
        set(value) { field = value; invalidate() }

    private val segPath = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Animation state ───────────────────────────────────────────────────────────
    private var animActive = false
    private val animFrom = HashMap<Int, Float>()
    private val animTo = HashMap<Int, Float>()
    private val animCurrent = HashMap<Int, Float>()
    private val animHighlight = HashSet<Int>()
    private var animStartMs = 0L
    private var animSolved = false

    private val animTicker = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - animStartMs
            val progress = (elapsed.toFloat() / ANIM_MS).coerceIn(0f, 1f)
            val eased = easeInOutCubic(progress)
            for ((idx, from) in animFrom) {
                animCurrent[idx] = from + shortestDelta(from, animTo[idx] ?: from) * eased
            }
            invalidate()
            if (progress < 1f) postOnAnimation(this) else finishAnimation()
        }
    }

    // ── Touch state ───────────────────────────────────────────────────────────────
    private var touchRingIndex = -1
    private var touchStartAngle = 0f
    private var touchMoved = false
    private val pointerHighlight = HashSet<Int>()

    private val density get() = resources.displayMetrics.density

    companion object {
        private const val ANIM_MS = 300L
        private val TOUCH_THRESHOLD =
            (Math.PI * 2 / CirclesGame.SEGMENTS * 0.45).toFloat().coerceAtLeast(0.2f)
    }

    // ── Public API ────────────────────────────────────────────────────────────────

    /** Triggers a programmatic rotation (e.g. hint). Game state must already be updated. */
    fun playAnimation(affected: IntArray, direction: Int, solved: Boolean) {
        startAnimation(affected, direction, solved)
    }

    fun refresh() = invalidate()

    // ── Drawing ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.92f
        canvas.save()
        canvas.translate(cx, cy)
        drawRings(canvas, g, radius)
        canvas.restore()
    }

    private fun drawRings(canvas: Canvas, g: CirclesGame, radius: Float) {
        val metrics = ringMetrics(radius, g.ringCount)
        val segDeg = 360f / CirclesGame.SEGMENTS

        for (i in 0 until g.ringCount) {
            val (outer, inner) = metrics[i]
            val displayRot = if (animActive && animCurrent.containsKey(i)) {
                normalizeDisplay(animCurrent[i]!!)
            } else {
                g.rotations[i].toFloat()
            }
            val rotDeg = displayRot * segDeg

            // Draw each color segment
            for (s in 0 until CirclesGame.SEGMENTS) {
                paint.color = g.rings[i][s]
                paint.style = Paint.Style.FILL
                drawDonutSlice(canvas, outer, inner, rotDeg + s * segDeg, segDeg)
            }

            // Highlight ring if touched, animated, or hinted
            val highlighted = (animActive && animHighlight.contains(i))
                || hintHighlight?.contains(i) == true
                || pointerHighlight.contains(i)
            if (highlighted) drawHighlight(canvas, outer, inner)
        }
    }

    private fun drawDonutSlice(canvas: Canvas, outer: Float, inner: Float, startDeg: Float, sweepDeg: Float) {
        segPath.reset()
        outerRect.set(-outer, -outer, outer, outer)
        innerRect.set(-inner, -inner, inner, inner)
        segPath.arcTo(outerRect, startDeg, sweepDeg, true)
        segPath.arcTo(innerRect, startDeg + sweepDeg, -sweepDeg, false)
        segPath.close()
        canvas.drawPath(segPath, paint)
    }

    private fun drawHighlight(canvas: Canvas, outer: Float, inner: Float) {
        val mid = (outer + inner) / 2f
        val thickness = (outer - inner).coerceAtLeast(1f)
        paint.style = Paint.Style.STROKE
        paint.color = 0xD75ED5FF.toInt()
        paint.strokeWidth = (thickness * 0.12f).coerceAtLeast(2f)
        outerRect.set(-mid, -mid, mid, mid)
        canvas.drawOval(outerRect, paint)
        paint.style = Paint.Style.FILL
    }

    private fun ringMetrics(radius: Float, ringCount: Int): Array<Pair<Float, Float>> {
        val thickness = radius / (ringCount + 0.25f)
        val gap = min(thickness * 0.18f, 6f * density)
        val result = Array(ringCount) { Pair(0f, 0f) }
        var outer = radius
        for (i in 0 until ringCount) {
            val inner = (outer - (thickness - gap)).coerceAtLeast(0f)
            result[i] = Pair(outer, inner)
            outer = (inner - gap).coerceAtLeast(0f)
        }
        return result
    }

    // ── Touch ─────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return true
        if (animActive) return true

        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        val angle = atan2(dy, dx)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val ri = findRingAt(dist, g)
                if (ri < 0) return true
                touchRingIndex = ri
                touchStartAngle = angle
                touchMoved = false
                updatePointerHighlight(ri, g)
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchRingIndex < 0) return true
                val delta = normAngle(angle - touchStartAngle)
                if (abs(delta) >= TOUCH_THRESHOLD) {
                    touchMoved = true
                    val dir = if (delta > 0) 1 else -1
                    doRotate(touchRingIndex, dir, g)
                    touchStartAngle = angle
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> clearTouch()
        }
        return true
    }

    private fun doRotate(ringIndex: Int, direction: Int, g: CirclesGame) {
        val affected = g.rotateRing(ringIndex, direction)
        if (affected.isEmpty()) return
        startAnimation(affected, direction, g.solved)
    }

    private fun clearTouch() {
        touchRingIndex = -1
        touchMoved = false
        pointerHighlight.clear()
        invalidate()
    }

    private fun updatePointerHighlight(ringIndex: Int, g: CirclesGame) {
        pointerHighlight.clear()
        pointerHighlight.add(ringIndex)
        val linked = g.rotationLinks.getOrElse(ringIndex) { -1 }
        if (linked >= 0 && linked != ringIndex) pointerHighlight.add(linked)
        invalidate()
    }

    private fun findRingAt(dist: Float, g: CirclesGame): Int {
        val radius = min(width, height) / 2f * 0.92f
        val metrics = ringMetrics(radius, g.ringCount)
        for (i in 0 until g.ringCount) {
            val (outer, inner) = metrics[i]
            if (dist <= outer && dist >= inner) return i
        }
        return -1
    }

    // ── Animation ─────────────────────────────────────────────────────────────────

    private fun startAnimation(affected: IntArray, direction: Int, solved: Boolean) {
        removeCallbacks(animTicker)
        animFrom.clear(); animTo.clear(); animCurrent.clear(); animHighlight.clear()
        animActive = true
        animSolved = solved
        animStartMs = System.currentTimeMillis()
        val dir = if (direction > 0) 1f else -1f
        val g = game ?: return
        for (idx in affected) {
            val end = g.rotations[idx].toFloat()
            val start = normalizeDisplay(end - dir)
            animFrom[idx] = start
            animTo[idx] = end
            animCurrent[idx] = start
            animHighlight.add(idx)
        }
        postOnAnimation(animTicker)
        invalidate()
    }

    private fun finishAnimation() {
        val solved = animSolved
        animActive = false
        animFrom.clear(); animTo.clear(); animCurrent.clear(); animHighlight.clear()
        invalidate()
        onRotationEnd?.invoke(solved)
    }

    // ── Math helpers ──────────────────────────────────────────────────────────────

    private fun normalizeDisplay(v: Float): Float {
        val s = CirclesGame.SEGMENTS.toFloat()
        return ((v % s) + s) % s
    }

    private fun shortestDelta(start: Float, end: Float): Float {
        val s = CirclesGame.SEGMENTS.toFloat()
        var d = end - start
        if (d > s / 2f) d -= s else if (d < -s / 2f) d += s
        return d
    }

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4 * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

    private fun normAngle(delta: Float): Float {
        var a = delta
        val pi = Math.PI.toFloat()
        while (a <= -pi) a += 2 * pi
        while (a > pi) a -= 2 * pi
        return a
    }
}
