package com.Atom2Universe.app.games.circles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Un atome vu de dessus : chaque anneau est une couche d'électrons (un tube lumineux de six
 * arcs colorés) autour d'un noyau qui palpite. Quand tout est aligné, l'atome entre en résonance.
 */
class CirclesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var game: CirclesGame? = null

    /** Called after each rotation animation ends. */
    var onRotationEnd: ((solved: Boolean) -> Unit)? = null

    private val segPath = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = SEPARATOR_COLOR
    }
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ── Géométrie et dégradés (recalculés seulement si la taille ou le nombre d'anneaux change) ──
    private var layoutW = 0
    private var layoutH = 0
    private var layoutRings = 0
    private var boardRadius = 0f
    private var nucleusRadius = 0f
    private var ringOuter = FloatArray(0)
    private var ringInner = FloatArray(0)
    private var ringShaders = arrayOfNulls<Shader>(0)
    private var haloShader: Shader? = null
    private var nucleusShader: Shader? = null

    // ── Animation de rotation ─────────────────────────────────────────────────────
    private var animActive = false
    private val animFrom = HashMap<Int, Float>()
    private val animTo = HashMap<Int, Float>()
    private val animCurrent = HashMap<Int, Float>()
    private var animStartMs = 0L
    private var animSolved = false
    private var animPrimary = -1
    private var animLinked = -1

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

    // ── Effets : ondes du noyau, résonance de victoire, étincelles ────────────────
    private val rippleStart = LongArray(RIPPLES)
    private val rippleAmp = FloatArray(RIPPLES)
    private var celebrateStart = 0L
    private var lastDrawMs = 0L
    private val sparkX = FloatArray(SPARKS)
    private val sparkY = FloatArray(SPARKS)
    private val sparkVx = FloatArray(SPARKS)
    private val sparkVy = FloatArray(SPARKS)
    private val sparkLife = FloatArray(SPARKS)
    private val sparkColor = IntArray(SPARKS)

    // ── Touch state ───────────────────────────────────────────────────────────────
    private var touchRingIndex = -1
    private var touchStartAngle = 0f
    private var touchMoved = false
    private var touchLinked = -1

    private val density get() = resources.displayMetrics.density

    companion object {
        private const val ANIM_MS = 300L
        private const val RIPPLES = 4
        private const val RIPPLE_MS = 1000f
        private const val CELEBRATE_MS = 1800f
        private const val SPARKS = 44
        private const val NUCLEUS_GLOW = 3.4f
        private const val SPARK_MS = 1500f
        private const val IDLE_FRAME_MS = 40L
        private const val PULSE_PERIOD_MS = 2600L
        private const val PRIMARY_COLOR = 0xFFFFFFFF.toInt()
        private const val LINKED_COLOR = 0xFFFFB02E.toInt()
        private const val SEPARATOR_COLOR = 0xE006081A.toInt()
        private const val TWO_PI = (PI * 2).toFloat()
        private val TOUCH_THRESHOLD =
            (Math.PI * 2 / CirclesGame.SEGMENTS * 0.45).toFloat().coerceAtLeast(0.2f)
    }

    // ── Public API ────────────────────────────────────────────────────────────────

    /** Triggers a programmatic rotation (e.g. hint). Game state must already be updated. */
    fun playAnimation(affected: IntArray, direction: Int, solved: Boolean) {
        startAnimation(affected, direction, solved)
    }

    /** Nouvelle partie ou recommencement : on repart d'un atome calme. */
    fun refresh() {
        clearEffects()
        invalidate()
    }

    /** Résonance de victoire : le noyau flambe, trois ondes partent, des étincelles jaillissent. */
    fun celebrate() {
        val now = SystemClock.uptimeMillis()
        celebrateStart = now
        addRipple(1f, 0L)
        addRipple(0.8f, 200L)
        addRipple(0.6f, 400L)
        val rng = Random.Default
        for (i in 0 until SPARKS) {
            val angle = rng.nextFloat() * TWO_PI
            val speed = 0.35f + rng.nextFloat() * 0.75f
            sparkX[i] = 0f
            sparkY[i] = 0f
            sparkVx[i] = cos(angle) * speed
            sparkVy[i] = sin(angle) * speed
            sparkLife[i] = 0.6f + rng.nextFloat() * 0.4f
            sparkColor[i] = CirclesGame.COLORS[rng.nextInt(CirclesGame.COLORS.size)]
        }
        invalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        ensureLayout(g)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastDrawMs == 0L) 16L else (now - lastDrawMs).coerceIn(1L, 64L)
        lastDrawMs = now

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        fxPaint.shader = haloShader
        canvas.drawCircle(0f, 0f, boardRadius * 1.18f, fxPaint)
        fxPaint.shader = null
        drawRings(canvas, g)
        val busy = drawEffects(canvas, now, dt)
        canvas.restore()

        // Le noyau palpite en permanence, mais au ralenti tant que rien ne bouge.
        if (busy || animActive) postInvalidateOnAnimation() else postInvalidateDelayed(IDLE_FRAME_MS)
    }

    private fun ensureLayout(g: CirclesGame) {
        if (width == layoutW && height == layoutH && g.ringCount == layoutRings) return
        layoutW = width
        layoutH = height
        layoutRings = g.ringCount
        boardRadius = min(width, height) / 2f * 0.92f

        val n = g.ringCount
        val thickness = boardRadius / (n + 0.25f)
        val gap = min(thickness * 0.18f, 6f * density)
        ringOuter = FloatArray(n)
        ringInner = FloatArray(n)
        var outer = boardRadius
        for (i in 0 until n) {
            val inner = (outer - (thickness - gap)).coerceAtLeast(0f)
            ringOuter[i] = outer
            ringInner[i] = inner
            outer = (inner - gap).coerceAtLeast(0f)
        }

        // Tube lumineux : bords sombres, arête claire au centre.
        ringShaders = Array(n) { i ->
            val o = ringOuter[i].coerceAtLeast(1f)
            val inner = ringInner[i]
            RadialGradient(
                0f, 0f, o,
                intArrayOf(0x66000000, 0x38FFFFFF, 0x59000000),
                floatArrayOf(inner / o, (inner + o) / 2f / o, 1f),
                Shader.TileMode.CLAMP
            )
        }
        haloShader = RadialGradient(
            0f, 0f, boardRadius * 1.18f,
            intArrayOf(0x667C3AED, 0x267C3AED, 0x007C3AED),
            floatArrayOf(0.6f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
        nucleusRadius = max(ringInner[n - 1], 3f * density)
        nucleusShader = RadialGradient(
            0f, 0f, nucleusRadius * NUCLEUS_GLOW,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF1B8.toInt(), 0x66FFB347, 0x00FFB347),
            floatArrayOf(0f, 1f / NUCLEUS_GLOW * 0.9f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawRings(canvas: Canvas, g: CirclesGame) {
        val segDeg = 360f / CirclesGame.SEGMENTS
        val primary = if (animActive) animPrimary else touchRingIndex
        val linked = if (animActive) animLinked else touchLinked

        for (i in 0 until g.ringCount) {
            val outer = ringOuter[i]
            val inner = ringInner[i]
            val displayRot = if (animActive && animCurrent.containsKey(i)) {
                normalizeDisplay(animCurrent[i]!!)
            } else {
                g.rotations[i].toFloat()
            }
            val rotDeg = displayRot * segDeg

            // Chaque arc de couleur
            for (s in 0 until CirclesGame.SEGMENTS) {
                paint.color = g.rings[i][s]
                paint.style = Paint.Style.FILL
                drawDonutSlice(canvas, outer, inner, rotDeg + s * segDeg, segDeg)
            }

            // Relief du tube
            shadePaint.shader = ringShaders[i]
            shadePaint.strokeWidth = outer - inner
            canvas.drawCircle(0f, 0f, (outer + inner) / 2f, shadePaint)
            shadePaint.shader = null

            // Coutures entre les arcs
            sepPaint.strokeWidth = max(1.5f * density, (outer - inner) * 0.04f)
            for (s in 0 until CirclesGame.SEGMENTS) {
                canvas.save()
                canvas.rotate(rotDeg + s * segDeg)
                canvas.drawLine(inner, 0f, outer, 0f, sepPaint)
                canvas.restore()
            }

            // Les anneaux hors sélection s'effacent pour laisser ressortir la paire couplée
            if (primary >= 0 && i != primary && i != linked) {
                shadePaint.color = 0xA6000000.toInt()
                canvas.drawCircle(0f, 0f, (outer + inner) / 2f, shadePaint)
            }
        }

        if (primary >= 0) {
            if (linked >= 0) {
                drawBridge(canvas, ringOuter[primary], ringInner[primary], ringOuter[linked], ringInner[linked])
                drawHighlight(canvas, ringOuter[linked], ringInner[linked], LINKED_COLOR)
            }
            drawHighlight(canvas, ringOuter[primary], ringInner[primary], PRIMARY_COLOR)
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

    /** Voile coloré sur toute l'épaisseur de l'anneau et liseré net sur ses deux bords. */
    private fun drawHighlight(canvas: Canvas, outer: Float, inner: Float, color: Int) {
        val edge = 3f * density
        val rgb = color and 0x00FFFFFF
        focusPaint.color = rgb or 0x40000000
        focusPaint.strokeWidth = (outer - inner).coerceAtLeast(1f)
        canvas.drawCircle(0f, 0f, (outer + inner) / 2f, focusPaint)
        focusPaint.color = color
        focusPaint.strokeWidth = edge
        canvas.drawCircle(0f, 0f, outer - edge / 2f, focusPaint)
        canvas.drawCircle(0f, 0f, inner + edge / 2f, focusPaint)
    }

    /** Petit pont ambre entre l'anneau touché et son couplé, en haut de l'atome. */
    private fun drawBridge(canvas: Canvas, outerA: Float, innerA: Float, outerB: Float, innerB: Float) {
        val a = -(outerA + innerA) / 2f
        val b = -(outerB + innerB) / 2f
        focusPaint.color = LINKED_COLOR
        focusPaint.strokeWidth = 3f * density
        canvas.drawLine(0f, a, 0f, b, focusPaint)
        focusPaint.style = Paint.Style.FILL
        focusPaint.color = PRIMARY_COLOR
        canvas.drawCircle(0f, a, 5f * density, focusPaint)
        focusPaint.color = LINKED_COLOR
        canvas.drawCircle(0f, b, 5f * density, focusPaint)
        focusPaint.style = Paint.Style.STROKE
    }

    /** Noyau, ondes et étincelles. Renvoie true tant qu'un effet est en cours. */
    private fun drawEffects(canvas: Canvas, now: Long, dt: Long): Boolean {
        var busy = false

        var flare = 0f
        if (celebrateStart != 0L) {
            val t = (now - celebrateStart) / CELEBRATE_MS
            if (t < 1f) { flare = 1f - t; busy = true } else celebrateStart = 0L
        }
        val phase = (now % PULSE_PERIOD_MS).toFloat() / PULSE_PERIOD_MS * TWO_PI
        val beat = sin(phase)
        val scale = 1f + 0.07f * beat + flare * 0.9f
        canvas.save()
        canvas.scale(scale, scale)
        fxPaint.shader = nucleusShader
        fxPaint.alpha = if (flare > 0f) 255 else (215 + 40 * beat).toInt()
        canvas.drawCircle(0f, 0f, nucleusRadius * NUCLEUS_GLOW, fxPaint)
        fxPaint.shader = null
        canvas.restore()

        fxPaint.style = Paint.Style.STROKE
        for (k in 0 until RIPPLES) {
            val start = rippleStart[k]
            if (start == 0L) continue
            val t = (now - start) / RIPPLE_MS
            if (t >= 1f) { rippleStart[k] = 0L; continue }
            busy = true
            if (t < 0f) continue
            val eased = 1f - (1f - t) * (1f - t)
            fxPaint.color = 0xFFD6CCFF.toInt()
            fxPaint.alpha = (rippleAmp[k] * (1f - t) * 200).toInt()
            fxPaint.strokeWidth = (1.5f + 4f * (1f - t)) * density
            canvas.drawCircle(0f, 0f, boardRadius * (0.1f + 0.95f * eased), fxPaint)
        }
        fxPaint.style = Paint.Style.FILL

        for (i in 0 until SPARKS) {
            val life = sparkLife[i]
            if (life <= 0f) continue
            busy = true
            val step = dt / 1000f * boardRadius
            sparkX[i] += sparkVx[i] * step
            sparkY[i] += sparkVy[i] * step
            val drag = 0.985f.pow(dt / 16f)
            sparkVx[i] *= drag
            sparkVy[i] *= drag
            sparkLife[i] = life - dt / SPARK_MS
            fxPaint.color = sparkColor[i]
            fxPaint.alpha = (life.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawCircle(sparkX[i], sparkY[i], (1.5f + 2.5f * life) * density, fxPaint)
        }
        return busy
    }

    private fun addRipple(amp: Float, delayMs: Long) {
        val now = SystemClock.uptimeMillis()
        var slot = 0
        for (k in 0 until RIPPLES) {
            if (rippleStart[k] == 0L) { slot = k; break }
            if (rippleStart[k] < rippleStart[slot]) slot = k
        }
        rippleStart[slot] = now + delayMs
        rippleAmp[slot] = amp
    }

    private fun clearEffects() {
        rippleStart.fill(0L)
        sparkLife.fill(0f)
        celebrateStart = 0L
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
                touchLinked = linkedOf(ri, g)
                invalidate()
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
        touchLinked = -1
        invalidate()
    }

    private fun linkedOf(ringIndex: Int, g: CirclesGame): Int {
        val linked = g.rotationLinks.getOrElse(ringIndex) { -1 }
        return if (linked >= 0 && linked != ringIndex) linked else -1
    }

    private fun findRingAt(dist: Float, g: CirclesGame): Int {
        ensureLayout(g)
        for (i in 0 until g.ringCount) {
            if (dist <= ringOuter[i] && dist >= ringInner[i]) return i
        }
        return -1
    }

    // ── Animation ─────────────────────────────────────────────────────────────────

    private fun startAnimation(affected: IntArray, direction: Int, solved: Boolean) {
        removeCallbacks(animTicker)
        animFrom.clear(); animTo.clear(); animCurrent.clear()
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
        }
        animPrimary = affected.getOrElse(0) { -1 }
        animLinked = affected.getOrElse(1) { -1 }
        if (!solved) addRipple(0.3f, 0L)
        postOnAnimation(animTicker)
        invalidate()
    }

    private fun finishAnimation() {
        val solved = animSolved
        animActive = false
        animFrom.clear(); animTo.clear(); animCurrent.clear()
        animPrimary = -1
        animLinked = -1
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
