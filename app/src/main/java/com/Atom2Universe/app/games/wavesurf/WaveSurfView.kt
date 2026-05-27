package com.Atom2Universe.app.games.wavesurf

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

class WaveSurfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    enum class ColorTheme(val shortLabel: String) {
        VIVID("Vivid"), PASTEL("Pastel"), WEATHERED("Old"), GRAYSCALE("Gray")
    }

    companion object {
        private const val PIXELS_PER_METER = 60f
        private const val BASE_GRAVITY = 900f
        private const val DIVE_MULTIPLIER = 3.2f
        private const val BASE_PUSH_ACCEL = 42f
        private const val START_GROUND_SPEED = 90f
        private const val INITIAL_LAUNCH_SPEED = 360f
        private const val GROUND_DRAG = 0.9978f
        private const val HOLDING_DRAG = 0.9991f
        private const val AIR_DRAG = 0.999f
        private const val DIVE_PULL_STRENGTH = 0.85f
        private const val JUMP_BASE = 180f
        private const val JUMP_SPEED_RATIO = 0.4f
        private const val MAX_JUMP_IMPULSE = 380f
        private const val AUTO_JUMP_SPEED_THRESHOLD = 120f
        private const val MIN_LANDING_SPEED = 22f
        private const val UPHILL_DECEL_FACTOR_MIN = 0.35f
        private const val UPHILL_DECEL_SPEED = 420f
        private const val UPHILL_DRAG_BONUS = 0.9995f
        private const val UPHILL_PRESS_GRACE_DURATION = 2f
        private const val AIR_PRESS_FORWARD_IMPULSE = 120f
        private const val AIR_PRESS_DOWN_IMPULSE = 260f
        private const val GROUND_PRESS_FORWARD_IMPULSE = 140f
        private const val DOWNHILL_IMPULSE_MULTIPLIER = 1.35f
        private const val START_SCREEN_X_RATIO = 0.2f
        private const val CAMERA_LERP = 0.12f
        private const val CAMERA_VERTICAL_LERP = 0.10f
        // Ratio de l'écran depuis le haut où le joueur est ancré (0.62 = bas du 2/3 supérieur)
        private const val CAMERA_TARGET_Y_RATIO = 0.62f
        private const val TRAIL_MAX_POINTS = 24
        private const val TRAIL_MAX_AGE_MS = 320L
        private const val TRAIL_SAMPLE_INTERVAL_MS = 30L
        private const val FRAME_TIME_NS = 1_000_000_000L / 60
    }

    // ── Terrain ───────────────────────────────────────────────────────────────

    private data class TP(val x: Float, val y: Float)

    private enum class SegType { NORMAL, DOUBLE_HILL, LONG_VALLEY, PLATEAU, ROLLERS, BIG_RAMP }

    private inner class Terrain {
        val pts = mutableListOf<TP>()
        var minY = 120f; var maxY = 480f
        var baseLevel = 320f; var defBase = 320f
        val span get() = maxY - minY
        var cx = 0f; var cy = baseLevel
        var minAmp = 48f; var maxAmp = 120f
        var curAmp = 72f; var phase = 0f; var phaseSpd = 0.015f

        fun configure(minY: Float, maxY: Float, base: Float) {
            this.minY = minY; this.maxY = maxY
            baseLevel = base.coerceIn(minY + 20f, maxY - 20f)
            defBase = baseLevel
            minAmp = (span * 0.12f).coerceAtLeast(24f)
            maxAmp = (span * 0.26f).coerceAtLeast(minAmp + 12f)
            cy = baseLevel
        }

        fun reset(startX: Float, endX: Float) {
            pts.clear(); cx = startX
            val margin = (maxAmp * 0.6f).coerceAtLeast(36f)
            baseLevel = (defBase + rnd(-span * 0.08f, span * 0.08f))
                .coerceIn(minY + margin, maxY - margin)
            curAmp = rnd(minAmp, maxAmp)
            val wl = rnd(640f, 1240f)
            phaseSpd = (2f * PI.toFloat() / wl.coerceAtLeast(60f))
            phase = rnd(0f, 2f * PI.toFloat())
            cy = (baseLevel + sin(phase) * curAmp).coerceIn(minY, maxY)
            pts.add(TP(cx, cy)); ensure(endX)
        }

        fun ensure(maxX: Float) {
            if (pts.isEmpty()) pts.add(TP(cx, cy))
            while (pts.last().x < maxX) append()
        }

        fun prune(minX: Float) {
            while (pts.size > 4 && pts[1].x < minX) pts.removeAt(0)
        }

        fun height(x: Float): Float {
            if (pts.isEmpty()) return baseLevel
            if (x <= pts.first().x) return pts.first().y
            if (x >= pts.last().x) return pts.last().y
            for (i in 1 until pts.size) {
                if (x <= pts[i].x) {
                    val p = pts[i - 1]; val q = pts[i]
                    val t = ((x - p.x) / (q.x - p.x).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return lerp(p.y, q.y, t)
                }
            }
            return baseLevel
        }

        fun slope(x: Float): Float {
            if (pts.size < 2) return 0f
            if (x <= pts.first().x) return atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x)
            for (i in 1 until pts.size) {
                if (x <= pts[i].x) return atan2(pts[i].y - pts[i - 1].y, pts[i].x - pts[i - 1].x)
            }
            val n = pts.size
            return atan2(pts[n - 1].y - pts[n - 2].y, pts[n - 1].x - pts[n - 2].x)
        }

        // ── Segment types ────────────────────────────────────────────────────

        private fun pickType(): SegType {
            val r = Random.nextFloat()
            return when {
                r < 0.33f -> SegType.NORMAL
                r < 0.53f -> SegType.DOUBLE_HILL
                r < 0.68f -> SegType.LONG_VALLEY
                r < 0.78f -> SegType.PLATEAU
                r < 0.93f -> SegType.ROLLERS
                else      -> SegType.BIG_RAMP
            }
        }

        private fun append() {
            when (pickType()) {
                SegType.NORMAL      -> normalHill()
                SegType.DOUBLE_HILL -> doubleHill()
                SegType.LONG_VALLEY -> longValley()
                SegType.PLATEAU     -> plateau()
                SegType.ROLLERS     -> rollers()
                SegType.BIG_RAMP    -> bigRamp()
            }
        }

        // Bosse sinusoïdale standard
        private fun normalHill() {
            val tb = newBase()
            val amp = rnd(minAmp, maxAmp)
            val len = rnd(640f, 1240f)
            subSeg(targetBase = tb, amplitude = amp, length = len, wavelength = len)
        }

        // Deux bosses avec mini-creux entre elles
        private fun doubleHill() {
            val margin = (maxAmp * 0.6f).coerceAtLeast(36f)
            val peakBase = rnd(minY + margin, minY + margin + span * 0.25f)
                .coerceIn(minY + margin, maxY - margin)
            val valleyBase = (peakBase + rnd(span * 0.08f, span * 0.18f))
                .coerceIn(minY + margin, maxY - margin)
            val amp = rnd(minAmp * 0.45f, maxAmp * 0.65f)
            val halfLen = rnd(380f, 680f)
            // Première bosse
            subSeg(peakBase, amp, halfLen, halfLen)
            // Mini-creux rapide
            subSeg(valleyBase, amp * 0.35f, halfLen * 0.35f, halfLen * 0.35f)
            // Deuxième bosse légèrement décalée
            val peak2 = (peakBase + rnd(-span * 0.06f, span * 0.06f)).coerceIn(minY + margin, maxY - margin)
            subSeg(peak2, amp * rnd(0.8f, 1.1f), halfLen * rnd(0.9f, 1.2f), halfLen)
        }

        // Creux long et presque plat
        private fun longValley() {
            val margin = (maxAmp * 0.6f).coerceAtLeast(36f)
            val valBase = rnd(maxY - margin - span * 0.18f, maxY - margin)
                .coerceIn(minY + margin, maxY - margin)
            val amp = rnd(6f, 22f)  // quasi-plat = amplitude minuscule
            val len = rnd(1200f, 2600f)
            subSeg(targetBase = valBase, amplitude = amp, length = len, wavelength = len * 0.6f)
        }

        // Plateau relativement plat à hauteur variable
        private fun plateau() {
            val plateauBase = rnd(defBase - span * 0.18f, defBase + span * 0.18f)
                .coerceIn(minY + (maxAmp * 0.6f).coerceAtLeast(36f), maxY - (maxAmp * 0.6f).coerceAtLeast(36f))
            val amp = rnd(4f, 14f)
            val len = rnd(500f, 1100f)
            subSeg(plateauBase, amp, len, len * 0.8f)
        }

        // Petites bosses serrées (washboard)
        private fun rollers() {
            val base = newBase()
            val amp = rnd(minAmp * 0.28f, minAmp * 0.65f)
            val totalLen = rnd(640f, 1240f)
            val wl = rnd(180f, 300f) // courte longueur d'onde = beaucoup d'oscillations
            subSeg(base, amp, totalLen, wl)
        }

        // Montée raide suivie d'un long creux
        private fun bigRamp() {
            val margin = (maxAmp * 0.6f).coerceAtLeast(36f)
            val topBase = rnd(minY + margin, defBase - span * 0.05f)
                .coerceIn(minY + margin, maxY - margin)
            val bottomBase = rnd(defBase + span * 0.05f, maxY - margin)
                .coerceIn(minY + margin, maxY - margin)
            // Montée abrupte
            subSeg(topBase, minAmp * 0.25f, rnd(320f, 580f), 300f)
            // Long creux plat
            subSeg(bottomBase, rnd(8f, 20f), rnd(900f, 1800f), 800f)
        }

        private fun subSeg(targetBase: Float, amplitude: Float, length: Float, wavelength: Float) {
            val steps = (length / 36f).toInt().coerceAtLeast(8)
            val stepLen = length / steps
            val sb = baseLevel; val sa = curAmp; val sp = phaseSpd
            val ep = (2f * PI.toFloat() / wavelength.coerceAtLeast(60f))
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val e = ease(t)
                phase += lerp(sp, ep, e) * stepLen
                val y = (lerp(sb, targetBase, e) + sin(phase) * lerp(sa, amplitude, e)).coerceIn(minY, maxY)
                cx += stepLen; cy = y; pts.add(TP(cx, y))
            }
            baseLevel = targetBase; curAmp = amplitude
            phaseSpd = (2f * PI.toFloat() / wavelength.coerceAtLeast(60f))
            phase = ((phase % (2f * PI.toFloat())) + 2f * PI.toFloat()) % (2f * PI.toFloat())
        }

        private fun newBase(): Float {
            val margin = (maxAmp * 0.6f).coerceAtLeast(30f)
            return (baseLevel + rnd(-span * 0.04f, span * 0.04f)).coerceIn(minY + margin, maxY - margin)
        }
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private data class TrailPt(val x: Float, val y: Float, val t: Long)

    private var px = 0f; private var py = 0f
    private var pvx = 0f; private var pvy = 0f
    private var pspd = START_GROUND_SPEED; private var onGround = true
    private val trail = mutableListOf<TrailPt>()
    private var lastTrail = Long.MIN_VALUE / 2
    private var dist = 0f; private var elapsedMs = 0L
    private var sessionBestSpeed = 0f
    private var sessionBestAlt = 0f

    // ── Input ─────────────────────────────────────────────────────────────────

    @Volatile private var pressing = false
    private var pressDur = 0f; private var pendRel = false

    // ── Camera ────────────────────────────────────────────────────────────────

    private var camX = 0f; private var camY = 0f; private var camS = 1f

    // ── Dimensions ────────────────────────────────────────────────────────────

    private var vw = 1f; private var vh = 1f

    // ── Objects ───────────────────────────────────────────────────────────────

    private val terrain = Terrain()
    var currentTheme: ColorTheme = ColorTheme.VIVID

    private val skyPaint = Paint()
    private val terrFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Array(45) { Triple(Random.nextFloat(), Random.nextFloat() * 0.55f, 0.7f + Random.nextFloat() * 1.1f) }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastNs = 0L

    var onStats: ((distM: Float, speedKmh: Float, altitudeM: Float) -> Unit)? = null

    init { holder.addCallback(this); isFocusable = true }

    // ── Surface lifecycle ─────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {}

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
        vw = w.toFloat().coerceAtLeast(1f)
        vh = ht.toFloat().coerceAtLeast(1f)
        terrStroke.strokeWidth = (vh * 0.004f).coerceAtLeast(1.5f)
        terrain.configure(vh * 0.72f, vh * 0.96f, vh * 0.87f)
        resetGame()
        if (!running) resume()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { pause() }

    fun resume() {
        if (running) return
        running = true; lastNs = System.nanoTime()
        thread = Thread(this).apply { name = "WaveSurf"; start() }
    }

    fun pause() {
        running = false; thread?.join(500); thread = null
    }

    fun cycleTheme(): ColorTheme {
        currentTheme = when (currentTheme) {
            ColorTheme.VIVID     -> ColorTheme.PASTEL
            ColorTheme.PASTEL    -> ColorTheme.WEATHERED
            ColorTheme.WEATHERED -> ColorTheme.GRAYSCALE
            ColorTheme.GRAYSCALE -> ColorTheme.VIVID
        }
        return currentTheme
    }

    // ── Game loop ─────────────────────────────────────────────────────────────

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val delta = ((now - lastNs) / 1e9f).coerceIn(0f, 1f / 30f)
            lastNs = now
            update(delta)
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (canvas != null) try { renderFrame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            val sleep = FRAME_TIME_NS - (System.nanoTime() - now)
            if (sleep > 1_000_000L) Thread.sleep(sleep / 1_000_000L)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> setPress(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (ev.pointerCount <= 1) setPress(false)
            MotionEvent.ACTION_POINTER_UP -> if (ev.pointerCount <= 1) setPress(false)
        }
        return true
    }

    private fun setPress(p: Boolean) {
        if (p == pressing) return
        pressing = p
        if (p) { pressDur = 0f; pendRel = false; applyImpulse() }
        else   { pressDur = 0f; pendRel = onGround }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun resetGame() {
        camS = 1f; camY = 0f
        val ww = vw / camS
        terrain.reset(-ww, -ww + ww * 3f)
        px = -ww + ww * START_SCREEN_X_RATIO
        py = terrain.height(px) - 12f
        pspd = START_GROUND_SPEED; pvx = 0f; pvy = 0f; onGround = true
        camX = px - ww * START_SCREEN_X_RATIO
        dist = 0f; pressing = false; pressDur = 0f; pendRel = false
        trail.clear(); lastTrail = Long.MIN_VALUE / 2; elapsedMs = 0L
        val a = Math.toRadians(20.0).toFloat()
        onGround = false; pspd = INITIAL_LAUNCH_SPEED
        pvx = cos(a) * INITIAL_LAUNCH_SPEED; pvy = -sin(a) * INITIAL_LAUNCH_SPEED
    }

    // ── Physics ───────────────────────────────────────────────────────────────

    private fun update(dt: Float) {
        elapsedMs += (dt * 1000f).toLong()
        if (pressing) pressDur += dt else pressDur = 0f
        val ww = vw / camS
        terrain.ensure(camX + ww * 2.6f); terrain.prune(camX - ww * 1.2f)

        if (onGround) {
            val sl = terrain.slope(px)
            val tx = cos(sl); val ty = sin(sl)
            val grace = pressing && sl > 0 && pressDur < UPHILL_PRESS_GRACE_DURATION
            val dive = pressing && !grace
            val g = BASE_GRAVITY * if (dive) DIVE_MULTIPLIER else 1f
            var slopeA = -g * sin(sl)
            if (sl > 0 && pspd > 0) slopeA *= lerp(1f, UPHILL_DECEL_FACTOR_MIN, (pspd / UPHILL_DECEL_SPEED).coerceIn(0f, 1f))
            pspd += (slopeA + if (pressing) BASE_PUSH_ACCEL else BASE_PUSH_ACCEL * 0.12f) * dt
            var drag = if (dive) HOLDING_DRAG else GROUND_DRAG
            if (sl > 0 && pspd > 0) drag = drag.coerceAtLeast(lerp(drag, UPHILL_DRAG_BONUS, (pspd / UPHILL_DECEL_SPEED).coerceIn(0f, 1f)))
            pspd *= drag.dpow(dt * 60f); if (pspd < 0.001f) pspd = 0f
            pvx = pspd * tx; pvy = pspd * ty
            px += pvx * dt; py = terrain.height(px)
            val crest = sl < Math.toRadians(-6.0).toFloat()
            if ((crest && pspd >= AUTO_JUMP_SPEED_THRESHOLD) || (!pressing && pendRel && crest && pspd > 0f)) {
                val imp = (JUMP_BASE + pspd * JUMP_SPEED_RATIO).coerceIn(JUMP_BASE, MAX_JUMP_IMPULSE)
                onGround = false; pvy -= imp; py -= 1.5f
                pspd = hypot(pvx, pvy); pendRel = false
            }
        } else {
            val g = BASE_GRAVITY * if (pressing) DIVE_MULTIPLIER else 1f
            pvy += g * dt
            val drag = AIR_DRAG.dpow(dt * 60f); pvx *= drag; pvy *= drag
            if (pressing) {
                val sl = terrain.slope(px)
                val dp = (-sin(sl)).coerceAtLeast(0f)
                if (dp > 0f) { val da = g * dp * dt * DIVE_PULL_STRENGTH; pvx += cos(sl) * da; pvy += sin(sl) * da }
            }
            px += pvx * dt; py += pvy * dt
            val gy = terrain.height(px)
            if (py >= gy) {
                py = gy
                val sl = terrain.slope(px); val tx = cos(sl); val ty = sin(sl)
                val proj = pvx * tx + pvy * ty
                pspd = if ((proj * 0.97f) < MIN_LANDING_SPEED) 0f else proj * 0.97f
                pvx = pspd * tx; pvy = pspd * ty; onGround = true; pendRel = false
            }
        }
        if (!onGround) { pendRel = false; pspd = hypot(pvx, pvy) }
        dist = dist.coerceAtLeast(px)
        updateCamera(dt); captureTrail()
        val speedKmh = (pspd / PIXELS_PER_METER) * 3.6f
        val groundY = terrain.height(px)
        val altitudeM = ((groundY - py) / PIXELS_PER_METER).coerceAtLeast(0f)
        if (speedKmh > sessionBestSpeed || altitudeM > sessionBestAlt) {
            if (speedKmh > sessionBestSpeed) sessionBestSpeed = speedKmh
            if (altitudeM > sessionBestAlt) sessionBestAlt = altitudeM
            saveRecords()
        }
        onStats?.invoke(dist / PIXELS_PER_METER, speedKmh, altitudeM)
    }

    private fun saveRecords() {
        val prefs = context.getSharedPreferences("wave_surf_save", Context.MODE_PRIVATE)
        val prevSpeed = prefs.getInt("best_speed", 0)
        val prevAlt   = prefs.getInt("best_altitude", 0)
        val newSpeed  = sessionBestSpeed.toInt()
        val newAlt    = sessionBestAlt.toInt()
        if (newSpeed > prevSpeed || newAlt > prevAlt) {
            prefs.edit()
                .putInt("best_speed",    maxOf(newSpeed, prevSpeed))
                .putInt("best_altitude", maxOf(newAlt, prevAlt))
                .apply()
        }
    }

    private fun applyImpulse() {
        if (onGround) {
            val sl = terrain.slope(px)
            var imp = GROUND_PRESS_FORWARD_IMPULSE
            if (sl < 0) imp *= 1f + (DOWNHILL_IMPULSE_MULTIPLIER - 1f) * (-sin(sl)).coerceIn(0.2f, 1f)
            pvx += cos(sl) * imp; pvy += sin(sl) * imp; pspd = hypot(pvx, pvy)
        } else {
            pvx += AIR_PRESS_FORWARD_IMPULSE; pvy += AIR_PRESS_DOWN_IMPULSE; pspd = hypot(pvx, pvy)
        }
    }

    private fun updateCamera(dt: Float) {
        val ww = vw / camS
        camX += ((px - ww * START_SCREEN_X_RATIO) - camX) * CAMERA_LERP.coerceIn(0.08f, 0.25f)
        // Ancre le joueur à CAMERA_TARGET_Y_RATIO depuis le haut.
        // Quand le joueur saute haut, py diminue → desired devient négatif → clampé à 0
        // → la caméra reste en haut du monde et le terrain descend vers le bas de l'écran.
        val desired = (py - vh * CAMERA_TARGET_Y_RATIO).coerceAtLeast(0f)
        camY += (desired - camY) * CAMERA_VERTICAL_LERP.coerceIn(0.06f, 0.22f)
        camY = camY.coerceAtLeast(0f)
    }

    private fun captureTrail() {
        if (elapsedMs - lastTrail >= TRAIL_SAMPLE_INTERVAL_MS) {
            trail.add(TrailPt(px, py, elapsedMs)); lastTrail = elapsedMs
        }
        while (trail.size > TRAIL_MAX_POINTS) trail.removeAt(0)
        while (trail.isNotEmpty() && elapsedMs - trail.first().t > TRAIL_MAX_AGE_MS) trail.removeAt(0)
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderFrame(canvas: Canvas) {
        drawSky(canvas); drawTerrain(canvas); drawBall(canvas)
    }

    private fun drawSky(canvas: Canvas) {
        skyPaint.shader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.rgb(5, 8, 16), Color.rgb(4, 6, 13), Color.rgb(1, 1, 3)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, vw, vh, skyPaint)
        stars.forEach { (rx, ry, r) ->
            starPaint.color = Color.argb((90 + (rx * 130).toInt()).coerceIn(60, 210), 255, 255, 255)
            canvas.drawCircle(rx * vw, ry * vh, r, starPaint)
        }
    }

    private fun drawTerrain(canvas: Canvas) {
        val pts = terrain.pts; if (pts.isEmpty()) return
        val s = camS; val ww = vw / s
        val x0 = camX - ww * 0.25f; val x1 = camX + ww * 1.1f
        val path = Path()
        path.moveTo((x0 - camX) * s, vh)
        var started = false
        for (pt in pts) {
            if (pt.x < x0) continue
            if (pt.x > x1) { path.lineTo((x1 - camX) * s, vh); break }
            path.lineTo((pt.x - camX) * s, (pt.y - camY) * s)
            started = true
        }
        if (!started) path.lineTo((x1 - camX) * s, vh)
        path.lineTo((x1 - camX) * s, vh); path.close()

        // Gradient horizontal basé sur le thème (8 stops répartis sur la largeur visible)
        val stops = 8
        val gColors = IntArray(stops)
        val gPos = FloatArray(stops) { i -> i.toFloat() / (stops - 1) }
        for (i in 0 until stops) {
            gColors[i] = getTerrainColor(camX + ww * gPos[i])
        }
        terrFill.shader = LinearGradient(0f, 0f, vw, 0f, gColors, gPos, Shader.TileMode.CLAMP)
        canvas.drawPath(path, terrFill)

        // Assombrissement vertical pour donner de la profondeur
        terrDark.shader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(155, 0, 0, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, terrDark)

        terrStroke.color = Color.argb(70, 255, 255, 255)
        canvas.drawPath(path, terrStroke)
    }

    private fun getTerrainColor(worldX: Float): Int {
        return when (currentTheme) {
            ColorTheme.VIVID -> {
                val hue = ((worldX / 28000f) * 360f + 360f * 100f) % 360f
                Color.HSVToColor(floatArrayOf(hue, 0.82f, 0.74f))
            }
            ColorTheme.PASTEL -> {
                val hue = ((worldX / 28000f) * 360f + 360f * 100f) % 360f
                Color.HSVToColor(floatArrayOf(hue, 0.28f, 0.97f))
            }
            ColorTheme.WEATHERED -> {
                val hue = ((worldX / 40000f) * 360f + 15f + 360f * 100f) % 360f
                Color.HSVToColor(floatArrayOf(hue, 0.40f, 0.50f))
            }
            ColorTheme.GRAYSCALE -> {
                val wave = (sin(worldX / 7000f) * 0.55f + sin(worldX / 2800f + 1.4f) * 0.45f + 1f) / 2f
                val v = (0.17f + 0.62f * wave).coerceIn(0.12f, 0.88f)
                val g = (v * 255).toInt()
                Color.rgb(g, g, g)
            }
        }
    }

    private fun drawBall(canvas: Canvas) {
        val s = camS
        val r = (vh * 0.04f).coerceIn(12f, 28f)
        val sr = (r * s).coerceAtLeast(6f)
        val bx = (px - camX) * s; val by = (py - camY) * s
        val cy2 = by - sr * 0.4f
        val now = elapsedMs

        // Couleur basée sur la vitesse : bleu (210°) → rouge (0°) à 200 km/h
        val speedKmh = (pspd / PIXELS_PER_METER) * 3.6f
        val t = (speedKmh / 200f).coerceIn(0f, 1f)
        val hue = 210f * (1f - t)
        val sat = 0.68f + 0.28f * t
        val coreColor  = Color.HSVToColor(floatArrayOf(hue, sat, 1.0f))
        val glowColor  = Color.HSVToColor(floatArrayOf(hue, sat * 0.50f, 1.0f))
        val trailColor = Color.HSVToColor(floatArrayOf(hue, sat * 0.72f, 0.94f))
        val trailR = Color.red(trailColor); val trailG = Color.green(trailColor); val trailB = Color.blue(trailColor)
        val glowR  = Color.red(glowColor);  val glowG  = Color.green(glowColor);  val glowB  = Color.blue(glowColor)
        val highlight = Color.HSVToColor(floatArrayOf(hue, sat * 0.12f, 1.0f))

        // Trail
        trail.forEach { pt ->
            val age = now - pt.t; if (age < 0 || age > TRAIL_MAX_AGE_MS) return@forEach
            val life = (1f - age.toFloat() / TRAIL_MAX_AGE_MS).coerceIn(0f, 1f)
            val alpha = ((0.08f + life * 0.30f) * 255).toInt().coerceIn(0, 255)
            trailPaint.color = Color.argb(alpha, trailR, trailG, trailB)
            val sx = (pt.x - camX) * s; val sy = (pt.y - camY) * s
            canvas.drawCircle(sx, sy, sr * (0.4f + life * 0.55f), trailPaint)
        }
        // Halo
        ballPaint.shader = RadialGradient(bx, cy2, sr * 1.5f,
            intArrayOf(Color.argb(175, glowR, glowG, glowB), Color.argb(0, glowR, glowG, glowB)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, cy2, sr * 1.5f, ballPaint)
        // Cœur
        ballPaint.shader = RadialGradient(bx - sr * 0.28f, cy2 - sr * 0.28f, sr,
            intArrayOf(highlight, coreColor),
            floatArrayOf(0.08f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, cy2, sr, ballPaint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun ease(t: Float) = (1f - cos(PI.toFloat() * t.coerceIn(0f, 1f))) / 2f
    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun Float.dpow(e: Float) = this.toDouble().pow(e.toDouble()).toFloat()
}
