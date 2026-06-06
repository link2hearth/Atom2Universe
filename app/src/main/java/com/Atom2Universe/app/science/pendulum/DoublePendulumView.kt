package com.Atom2Universe.app.science.pendulum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
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

    // ── Toggles traînées ──────────────────────────────────────────────────
    var showTrailPivot1 = false  // articulation haute (= point fixe, pas très intéressant)
    var showTrailPivot2 = true   // articulation intermédiaire
    var showTrailTip    = true   // extrémité

    // ── État interne ───────────────────────────────────────────────────────
    private val pendulums = mutableListOf<PendulumState>()
    private var originX = 0f
    private var originY = 0f
    private var scale = 0f       // px par unité de longueur

    private val rodPaint = Paint().apply {
        strokeWidth = 3f
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
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pivotPaint = Paint().apply {
        color = Color.parseColor("#888899")
        isAntiAlias = true
        style = Paint.Style.FILL
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
        for (p in pendulums) repeat(steps) { p.integrate(subDt, gravity, armLength, mass) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#0D0D1A"))

        // Point d'ancrage fixe
        canvas.drawCircle(originX, originY, 6f, pivotPaint)

        for (p in pendulums) {
            val (x1, y1) = worldToScreen(p.x1, p.y1)
            val (x2, y2) = worldToScreen(p.x2, p.y2)

            // Traînée pivot 2
            if (showTrailPivot2 && p.trailPivot2.size > 1) drawTrail(canvas, p.trailPivot2, p.color, 0.5f)
            // Traînée extrémité
            if (showTrailTip && p.trailTip.size > 1) drawTrail(canvas, p.trailTip, p.color, 1f)

            // Bras
            rodPaint.color = applyAlpha(p.color, 0.55f)
            canvas.drawLine(originX, originY, x1, y1, rodPaint)
            canvas.drawLine(x1, y1, x2, y2, rodPaint)

            // Bobs
            bobPaint.color = applyAlpha(p.color, 0.85f)
            canvas.drawCircle(x1, y1, 8f, bobPaint)
            canvas.drawCircle(x2, y2, 10f, bobPaint)
        }
    }

    private fun drawTrail(canvas: Canvas, trail: ArrayDeque<Pair<Float, Float>>, color: Int, opacityMax: Float) {
        if (trail.size < 2) return
        val path = Path()
        val list = trail.toList()
        path.moveTo(list[0].first, list[0].second)
        for (i in 1 until list.size) path.lineTo(list[i].first, list[i].second)

        trailPaint.color = applyAlpha(color, opacityMax)
        canvas.drawPath(path, trailPaint)
    }

    private fun worldToScreen(wx: Double, wy: Double): Pair<Float, Float> =
        Pair(originX + (wx * scale).toFloat(), originY + (wy * scale).toFloat())

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            originX = event.x
            originY = event.y
            for (p in pendulums) { p.trailPivot2.clear(); p.trailTip.clear() }
        }
        return true
    }

    // ── État d'un seul pendule ─────────────────────────────────────────────
    inner class PendulumState(
        var theta1: Double,
        var theta2: Double,
        val color: Int,
        var omega1: Double = 0.0,
        var omega2: Double = 0.0
    ) {
        val trailPivot2 = ArrayDeque<Pair<Float, Float>>()
        val trailTip    = ArrayDeque<Pair<Float, Float>>()

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

        private fun equations(t1: Double, t2: Double, o1: Double, o2: Double, g: Double, l: Double, m: Double): Pair<Double, Double> {
            val dt = t1 - t2
            val denom1 = (2 * m) * l * (2.0 - cos(2 * dt))
            val denom2 = l * (2.0 - cos(2 * dt))

            val alpha1 = (-g * (2 * m) * sin(t1)
                    - m * g * sin(t1 - 2 * t2)
                    - 2 * sin(dt) * m * (o2 * o2 * l + o1 * o1 * l * cos(dt))
                    ) / (denom1 * l)

            val alpha2 = (2 * sin(dt) * (
                    o1 * o1 * l * (2 * m)
                            + g * (2 * m) * cos(t1)
                            + o2 * o2 * l * m * cos(dt)
                    )) / (denom2 * (2 * m) * l)

            return Pair(alpha1, alpha2)
        }

        private fun recordTrails() {
            val (sx1, sy1) = worldToScreen(x1, y1)
            val (sx2, sy2) = worldToScreen(x2, y2)

            trailPivot2.addLast(Pair(sx1, sy1))
            trailTip.addLast(Pair(sx2, sy2))

            while (trailPivot2.size > trailLength) trailPivot2.removeFirst()
            while (trailTip.size > trailLength) trailTip.removeFirst()
        }
    }
}
