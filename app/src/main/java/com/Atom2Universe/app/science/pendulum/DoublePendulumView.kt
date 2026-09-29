package com.Atom2Universe.app.science.pendulum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class DoublePendulumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Paramètres physiques ───────────────────────────────────────────────
    var gravity = 9.81
    var armLength = 1.0          // longueur normalisée de chaque bras (0.5–2.0)
    var mass = 1.0               // masse des deux bobs
    var simSpeed = 1.0           // multiplicateur de vitesse
    var trailLength = 300        // nombre de points conservés par traînée
    var pendulumCount = 8        // nombre de pendules actifs
    var damping = 0.0            // friction par step (0 = sans friction, 1 = arrêt immédiat)
    var isLocked = false         // cadenas : ancre fixe, grab des bobs activé

    private var grabbedIndex = -1
    private var grabbingTip = false
    private val grabRadiusPx get() = 48f * resources.displayMetrics.density

    // ── Toggles traînées ──────────────────────────────────────────────────
    var showTrailPivot1 = false  // articulation haute (= point fixe, pas très intéressant)
    var showTrailPivot2 = true   // articulation intermédiaire
    var showTrailTip    = true   // extrémité

    // ── État interne ───────────────────────────────────────────────────────
    private val pendulums = mutableListOf<PendulumState>()
    private var originX = 0f
    private var originY = 0f
    private var scale = 0f       // px par unité de longueur

    // Tailles en dp : en pixels bruts, bobs et tiges devenaient minuscules sur les écrans denses.
    private val density = resources.displayMetrics.density
    private val bobRadius1 = 8f * density
    private val bobRadius2 = 10f * density
    private val pivotRadius = 6f * density
    private val trailPath = Path()

    private val rodPaint = Paint().apply {
        strokeWidth = 3f * density
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val bobPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pivotPaint = Paint().apply {
        color = Color.parseColor("#888899")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val grabHighlightPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val PALETTE = intArrayOf(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#FFD93D"),
        Color.parseColor("#6BCB77"),
        Color.parseColor("#4D96FF"),
        Color.parseColor("#FF9F1C"),
        Color.parseColor("#C77DFF"),
        Color.parseColor("#00F5D4"),
        Color.parseColor("#FF4D6D"),
        Color.parseColor("#90E0EF"),
        Color.parseColor("#F4A261")
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        originX = w / 2f
        originY = h * 0.28f
        scale = h * 0.22f
        if (pendulums.isEmpty()) reset(pendulumCount)
    }

    fun reset(count: Int = pendulumCount) {
        pendulumCount = count
        pendulums.clear()
        val baseAngle = Math.PI * 0.75
        val spread = if (count > 1) 1e-4 else 0.0
        for (i in 0 until count) {
            val offset = (i - count / 2.0) * spread
            pendulums.add(PendulumState(
                theta1 = baseAngle + offset,
                theta2 = baseAngle + offset,
                color = PALETTE[i % PALETTE.size]
            ))
        }
        invalidate()
    }

    fun step(dtSeconds: Double) {
        val dt = dtSeconds * simSpeed
        val steps = if (dt > 0.008) (dt / 0.005).toInt().coerceAtLeast(1) else 1
        val subDt = dt / steps
        for ((i, p) in pendulums.withIndex()) {
            if (i == grabbedIndex) continue  // physique suspendue pendant le grab
            repeat(steps) { p.integrate(subDt, gravity, armLength, mass) }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BACKGROUND)

        // Point d'ancrage fixe
        canvas.drawCircle(originX, originY, pivotRadius, pivotPaint)

        for ((i, p) in pendulums.withIndex()) {
            val x1 = screenX(p.x1); val y1 = screenY(p.y1)
            val x2 = screenX(p.x2); val y2 = screenY(p.y2)

            // Traînée pivot 2
            if (showTrailPivot2) drawTrail(canvas, p.trailPivot2, p.color, 0.5f)
            // Traînée extrémité
            if (showTrailTip) drawTrail(canvas, p.trailTip, p.color, 1f)

            // Bras
            rodPaint.color = applyAlpha(p.color, 0.55f)
            canvas.drawLine(originX, originY, x1, y1, rodPaint)
            canvas.drawLine(x1, y1, x2, y2, rodPaint)

            // Bobs
            bobPaint.color = applyAlpha(p.color, 0.85f)
            canvas.drawCircle(x1, y1, bobRadius1, bobPaint)
            canvas.drawCircle(x2, y2, bobRadius2, bobPaint)

            // Highlight du bob grabé
            if (i == grabbedIndex) {
                if (grabbingTip) canvas.drawCircle(x2, y2, bobRadius2 + 4f * density, grabHighlightPaint)
                else canvas.drawCircle(x1, y1, bobRadius1 + 4f * density, grabHighlightPaint)
            }
        }
    }

    private fun drawTrail(canvas: Canvas, trail: Trail, color: Int, opacityMax: Float) {
        // Seuls les trailLength derniers points : le curseur peut avoir réduit la longueur.
        val count = minOf(trail.size, trailLength)
        if (count < 2) return
        val first = trail.size - count
        trailPath.rewind()
        trailPath.moveTo(trail.x(first), trail.y(first))
        for (i in first + 1 until trail.size) trailPath.lineTo(trail.x(i), trail.y(i))

        trailPaint.color = applyAlpha(color, opacityMax)
        canvas.drawPath(trailPath, trailPaint)
    }

    private fun screenX(wx: Double): Float = originX + (wx * scale).toFloat()
    private fun screenY(wy: Double): Float = originY + (wy * scale).toFloat()

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isLocked) {
                    tryGrab(event.x, event.y)
                } else {
                    originX = event.x
                    originY = event.y
                    for (p in pendulums) { p.trailPivot2.clear(); p.trailTip.clear() }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLocked && grabbedIndex >= 0) {
                    applyGrab(event.x, event.y)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grabbedIndex >= 0) {
                    // Vitesse nulle à la release : repart de la position posée
                    pendulums[grabbedIndex].omega1 = 0.0
                    pendulums[grabbedIndex].omega2 = 0.0
                    grabbedIndex = -1
                }
            }
        }
        return true
    }

    private fun tryGrab(sx: Float, sy: Float) {
        var bestDist = grabRadiusPx
        grabbedIndex = -1

        for ((i, p) in pendulums.withIndex()) {
            val dTip = hypot(sx - screenX(p.x2), sy - screenY(p.y2))
            if (dTip < bestDist) { bestDist = dTip; grabbedIndex = i; grabbingTip = true }

            val dPivot = hypot(sx - screenX(p.x1), sy - screenY(p.y1))
            if (dPivot < bestDist) { bestDist = dPivot; grabbedIndex = i; grabbingTip = false }
        }

        if (grabbedIndex >= 0) {
            pendulums[grabbedIndex].trailPivot2.clear()
            pendulums[grabbedIndex].trailTip.clear()
        }
    }

    private fun applyGrab(sx: Float, sy: Float) {
        val p = pendulums[grabbedIndex]
        val wx = ((sx - originX) / scale).toDouble()
        val wy = ((sy - originY) / scale).toDouble()
        if (grabbingTip) {
            p.theta2 = atan2(wx - p.x1, wy - p.y1)
            p.omega2 = 0.0
        } else {
            p.theta1 = atan2(wx, wy)
            p.omega1 = 0.0
        }
    }

    // ── État d'un seul pendule ─────────────────────────────────────────────
    inner class PendulumState(
        var theta1: Double,
        var theta2: Double,
        val color: Int,
        var omega1: Double = 0.0,
        var omega2: Double = 0.0
    ) {
        internal val trailPivot2 = Trail()
        internal val trailTip    = Trail()

        val x1 get() = armLength * sin(theta1)
        val y1 get() = armLength * cos(theta1)
        val x2 get() = x1 + armLength * sin(theta2)
        val y2 get() = y1 + armLength * cos(theta2)

        fun integrate(dt: Double, g: Double, l: Double, m: Double) {
            // Équations de Lagrange du pendule double (masses égales, bras égaux)
            val (a1, a2) = equations(theta1, theta2, omega1, omega2, g, l, m)
            // RK4
            val k1o1 = a1; val k1o2 = a2
            val k1t1 = omega1; val k1t2 = omega2

            val (a1b, a2b) = equations(
                theta1 + k1t1 * dt / 2, theta2 + k1t2 * dt / 2,
                omega1 + k1o1 * dt / 2, omega2 + k1o2 * dt / 2, g, l, m
            )
            val k2o1 = a1b; val k2o2 = a2b
            val k2t1 = omega1 + k1o1 * dt / 2; val k2t2 = omega2 + k1o2 * dt / 2

            val (a1c, a2c) = equations(
                theta1 + k2t1 * dt / 2, theta2 + k2t2 * dt / 2,
                omega1 + k2o1 * dt / 2, omega2 + k2o2 * dt / 2, g, l, m
            )
            val k3o1 = a1c; val k3o2 = a2c
            val k3t1 = omega1 + k2o1 * dt / 2; val k3t2 = omega2 + k2o2 * dt / 2

            val (a1d, a2d) = equations(
                theta1 + k3t1 * dt, theta2 + k3t2 * dt,
                omega1 + k3o1 * dt, omega2 + k3o2 * dt, g, l, m
            )
            val k4o1 = a1d; val k4o2 = a2d
            val k4t1 = omega1 + k3o1 * dt; val k4t2 = omega2 + k3o2 * dt

            omega1 += dt / 6.0 * (k1o1 + 2 * k2o1 + 2 * k3o1 + k4o1)
            omega2 += dt / 6.0 * (k1o2 + 2 * k2o2 + 2 * k3o2 + k4o2)
            theta1 += dt / 6.0 * (k1t1 + 2 * k2t1 + 2 * k3t1 + k4t1)
            theta2 += dt / 6.0 * (k1t2 + 2 * k2t2 + 2 * k3t2 + k4t2)

            if (damping > 0.0) {
                val retain = 1.0 - damping * dt * 60.0
                omega1 *= retain.coerceAtLeast(0.0)
                omega2 *= retain.coerceAtLeast(0.0)
            }

            recordTrails()
        }

        /**
         * Équations du pendule double pour m1 = m2 = m et L1 = L2 = l :
         *   θ1'' = [−3mg·sinθ1 − mg·sin(θ1−2θ2) − 2m·sinΔ·(ω2²l + ω1²l·cosΔ)] / [l·m(3 − cos2Δ)]
         *   θ2'' = [2·sinΔ·(2mω1²l + 2mg·cosθ1 + mω2²l·cosΔ)] / [l·m(3 − cos2Δ)]
         * La version précédente divisait par 2m·l²(2 − cos2Δ) : l'énergie dérivait de plus de
         * 100 % en 100 s (contre ~10⁻⁵ ici) et la longueur des bras jouait au carré.
         */
        private fun equations(t1: Double, t2: Double, o1: Double, o2: Double, g: Double, l: Double, m: Double): Pair<Double, Double> {
            val delta = t1 - t2
            val denom = l * m * (3.0 - cos(2 * delta))

            val alpha1 = (-3 * m * g * sin(t1)
                    - m * g * sin(t1 - 2 * t2)
                    - 2 * sin(delta) * m * (o2 * o2 * l + o1 * o1 * l * cos(delta))
                    ) / denom

            val alpha2 = (2 * sin(delta) * (
                    o1 * o1 * l * (2 * m)
                            + g * (2 * m) * cos(t1)
                            + o2 * o2 * l * m * cos(delta)
                    )) / denom

            return Pair(alpha1, alpha2)
        }

        private fun recordTrails() {
            trailPivot2.add(screenX(x1), screenY(y1))
            trailTip.add(screenX(x2), screenY(y2))
        }
    }
}

private const val BACKGROUND = 0xFF0D0D1A.toInt()

/**
 * Traînée en tampon circulaire de floats : l'ancienne ArrayDeque<Pair<Float, Float>> allouait
 * une paire et deux Float à chaque sous-pas, et le dessin recopiait toute la liste à chaque image.
 */
internal class Trail(private val capacity: Int = MAX_POINTS) {
    companion object {
        /** Longueur maximale proposée par le curseur de traînée. */
        const val MAX_POINTS = 1000
    }

    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private var start = 0
    var size = 0
        private set

    fun add(x: Float, y: Float) {
        val end = (start + size) % capacity
        xs[end] = x
        ys[end] = y
        if (size < capacity) size++ else start = (start + 1) % capacity
    }

    fun clear() { start = 0; size = 0 }

    /** i-ème point, du plus ancien (0) au plus récent (size − 1). */
    fun x(i: Int): Float = xs[(start + i) % capacity]
    fun y(i: Int): Float = ys[(start + i) % capacity]
}
