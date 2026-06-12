package com.Atom2Universe.app.science.boids

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.Atom2Universe.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simulation de boids (Craig Reynolds, 1986).
 *
 * Chaque agent suit trois règles locales — séparation, alignement, cohésion —
 * dont émergent nuées d'étourneaux, bancs de poissons et essaims d'insectes.
 * La View ajoute des obstacles, des prédateurs, une interaction tactile
 * (attirer/repousser) et un mode « vision » pédagogique qui matérialise le
 * rayon de perception d'un boid, ses voisins et les trois forces qui le guident.
 *
 * La simulation tourne sur le thread UI via [postOnAnimation] : avec au plus
 * [MAX_BOIDS] agents le pas O(n²) reste sous la milliseconde.
 */
class BoidsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val TOUCH_ATTRACT = 0
        const val TOUCH_REPEL = 1
        const val TOUCH_OBSTACLE = 2

        const val COLOR_DIRECTION = 0
        const val COLOR_SPEED = 1
        const val COLOR_AURORA = 2
        const val COLOR_FIREFLIES = 3

        const val EDGE_WALLS = 0
        const val EDGE_WRAP = 1

        const val MIN_BOIDS = 20
        const val MAX_BOIDS = 400
        const val MAX_SPECIES = 3

        private const val MAX_OBSTACLES = 12
        private const val MAX_PREDATORS = 3
        private const val MAX_VISION_NEIGHBORS = 48

        /** Vitesse de croisière en dp/s pour un facteur de vitesse de 1. */
        private const val BASE_SPEED_DP = 100f
    }

    private val density = resources.displayMetrics.density

    // ── Paramètres pilotés par l'Activity ────────────────────────────────────
    var separationWeight = 0.9f
    var alignmentWeight = 1.5f
    var cohesionWeight = 0.9f
    var perceptionDp = 90f
    var speedFactor = 1.6f
    var colorMode = COLOR_DIRECTION
    var touchMode = TOUCH_ATTRACT
    var visionEnabled = false
    var edgeMode = EDGE_WALLS

    var speciesCount = 1
        set(value) { field = value.coerceIn(1, MAX_SPECIES) }

    /** Notifié à chaque ajout/suppression d'obstacle avec le nombre courant. */
    var onObstaclesChanged: ((Int) -> Unit)? = null

    var trailsEnabled = false
        set(value) {
            field = value
            if (!value) trailBitmap?.eraseColor(Color.TRANSPARENT)
        }

    // ── État du troupeau ─────────────────────────────────────────────────────
    private var count = 280
    private val x = FloatArray(MAX_BOIDS)
    private val y = FloatArray(MAX_BOIDS)
    private val vx = FloatArray(MAX_BOIDS)
    private val vy = FloatArray(MAX_BOIDS)
    private val phase = FloatArray(MAX_BOIDS) { Random.nextFloat() * 6.2832f }

    private var predatorCount = 0
    private val px = FloatArray(MAX_PREDATORS)
    private val py = FloatArray(MAX_PREDATORS)
    private val pvx = FloatArray(MAX_PREDATORS)
    private val pvy = FloatArray(MAX_PREDATORS)

    /** Triplets (x, y, rayon). */
    private val obstacles = FloatArray(MAX_OBSTACLES * 3)
    private var obstacleCount = 0

    private var touchActive = false
    private var touchX = 0f
    private var touchY = 0f

    private var time = 0f
    private var lastFrameNs = 0L
    private var running = false
    private var stepped = false

    val isRunning: Boolean get() = running

    // ── Données du mode vision (boid 0) ──────────────────────────────────────
    private val visionNeighbors = FloatArray(MAX_VISION_NEIGHBORS * 2)
    private var visionNeighborCount = 0
    private var visionSepX = 0f; private var visionSepY = 0f
    private var visionAliX = 0f; private var visionAliY = 0f
    private var visionCohX = 0f; private var visionCohY = 0f

    // ── Rendu ────────────────────────────────────────────────────────────────
    private val boidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        color = 0xFFCCCCDD.toInt()
    }
    private val bgPaint = Paint()
    private var trailBitmap: Bitmap? = null
    private var trailCanvas: Canvas? = null
    private val trailFadePaint = Paint().apply {
        color = Color.argb(22, 0, 0, 0)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val dashEffect = DashPathEffect(floatArrayOf(4f * density, 5f * density), 0f)

    private val boidPath = Path().apply {
        val s = 6.5f * density
        moveTo(s, 0f)
        lineTo(-s * 0.75f, s * 0.55f)
        lineTo(-s * 0.4f, 0f)
        lineTo(-s * 0.75f, -s * 0.55f)
        close()
    }

    private val sepColor = 0xFFFF5577.toInt()
    private val aliColor = 0xFF55E07A.toInt()
    private val cohColor = 0xFF55AAFF.toInt()

    private val directionLut = IntArray(72) {
        Color.HSVToColor(floatArrayOf(it * 5f, 0.72f, 1f))
    }

    private val speciesColors = intArrayOf(
        0xFF4FC3F7.toInt(), 0xFFFFB74D.toInt(), 0xFFBA68C8.toInt()
    )

    private val sepLabel = context.getString(R.string.boids_separation)
    private val aliLabel = context.getString(R.string.boids_alignment)
    private val cohLabel = context.getString(R.string.boids_cohesion)

    // ── Boucle d'animation ───────────────────────────────────────────────────
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
            lastFrameNs = now
            step(dt)
            stepped = true
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNs = System.nanoTime()
        postOnAnimation(frameRunnable)
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
    }

    // ── Commandes ────────────────────────────────────────────────────────────
    fun setBoidCount(n: Int) {
        count = n.coerceIn(MIN_BOIDS, MAX_BOIDS)
        if (!running) invalidate()
    }

    fun setPredatorCount(n: Int) {
        predatorCount = n.coerceIn(0, MAX_PREDATORS)
        if (!running) invalidate()
    }

    fun scatter() {
        if (width == 0) return
        initAgents()
        trailBitmap?.eraseColor(Color.TRANSPARENT)
        if (!running) invalidate()
    }

    fun clearObstacles() {
        obstacleCount = 0
        onObstaclesChanged?.invoke(0)
        if (!running) invalidate()
    }

    // ── Initialisation ───────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        initAgents()
        trailBitmap = createBitmap(w, h)
        trailCanvas = Canvas(trailBitmap!!)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFF0A0E1C.toInt(), 0xFF101626.toInt(), Shader.TileMode.CLAMP
        )
    }

    private fun initAgents() {
        val w = width.toFloat()
        val h = height.toFloat()
        val speed = BASE_SPEED_DP * density * speedFactor * 0.7f
        for (i in 0 until MAX_BOIDS) {
            x[i] = Random.nextFloat() * w
            y[i] = Random.nextFloat() * h
            val a = Random.nextFloat() * 6.2832f
            vx[i] = cos(a) * speed
            vy[i] = sin(a) * speed
        }
        for (i in 0 until MAX_PREDATORS) {
            px[i] = Random.nextFloat() * w
            py[i] = Random.nextFloat() * h
            val a = Random.nextFloat() * 6.2832f
            pvx[i] = cos(a) * speed
            pvy[i] = sin(a) * speed
        }
    }

    // ── Pilotage (steering de Reynolds) ──────────────────────────────────────
    private var steerX = 0f
    private var steerY = 0f

    /** Force = direction désirée normalisée × vitesse max − vitesse actuelle, bornée. */
    private fun steer(dirX: Float, dirY: Float, vxi: Float, vyi: Float, maxSpeed: Float, maxForce: Float) {
        val len = sqrt(dirX * dirX + dirY * dirY)
        if (len < 1e-5f) { steerX = 0f; steerY = 0f; return }
        var fx = dirX / len * maxSpeed - vxi
        var fy = dirY / len * maxSpeed - vyi
        val fl = sqrt(fx * fx + fy * fy)
        if (fl > maxForce) {
            fx = fx / fl * maxForce
            fy = fy / fl * maxForce
        }
        steerX = fx
        steerY = fy
    }

    // ── Un pas de simulation ─────────────────────────────────────────────────
    private fun step(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        time += dt

        val per = perceptionDp * density
        val per2 = per * per
        val sepR2 = per2 * 0.2f
        val sepOtherR2 = per2 * 0.55f
        val maxSpeed = BASE_SPEED_DP * density * speedFactor
        val minSpeed = maxSpeed * 0.45f
        val maxForce = maxSpeed * 3.5f
        val fleeR2 = per * 1.9f * (per * 1.9f)
        val obstacleMargin = 56f * density
        val repelRadius = 220f * density
        val wallMargin = 90f * density
        val sc = speciesCount
        val walls = edgeMode == EDGE_WALLS
        // « Espace vital » : rayon de contact incompressible, indépendant des curseurs
        val coreR = 14f * density
        val coreR2 = coreR * coreR

        val captureVision = visionEnabled
        if (captureVision) visionNeighborCount = 0

        for (i in 0 until count) {
            val xi = x[i]
            val yi = y[i]
            val vxi = vx[i]
            val vyi = vy[i]
            val si = i % sc
            var sepX = 0f; var sepY = 0f
            var aliX = 0f; var aliY = 0f
            var cohX = 0f; var cohY = 0f
            var coreX = 0f; var coreY = 0f
            var n = 0
            var nSep = 0
            var nCore = 0

            for (j in 0 until count) {
                if (j == i) continue
                val dx = x[j] - xi
                val dy = y[j] - yi
                val d2 = dx * dx + dy * dy
                if (d2 >= per2 || d2 < 1e-6f) continue
                if (d2 < coreR2) {
                    val d = sqrt(d2).coerceAtLeast(0.3f * density)
                    val push = 1f - d / coreR
                    coreX -= dx / d * push
                    coreY -= dy / d * push
                    nCore++
                }
                if (sc == 1 || j % sc == si) {
                    n++
                    aliX += vx[j]; aliY += vy[j]
                    cohX += x[j]; cohY += y[j]
                    if (d2 < sepR2) {
                        val inv = 1f / d2
                        sepX -= dx * inv
                        sepY -= dy * inv
                        nSep++
                    }
                } else if (d2 < sepOtherR2) {
                    // Les autres espèces repoussent plus fort et de plus loin
                    val inv = 2f / d2
                    sepX -= dx * inv
                    sepY -= dy * inv
                    nSep++
                }
                if (captureVision && i == 0 && visionNeighborCount < MAX_VISION_NEIGHBORS) {
                    visionNeighbors[visionNeighborCount * 2] = x[j]
                    visionNeighbors[visionNeighborCount * 2 + 1] = y[j]
                    visionNeighborCount++
                }
            }

            var fx = 0f
            var fy = 0f
            var ruleSepX = 0f; var ruleSepY = 0f
            var ruleAliX = 0f; var ruleAliY = 0f
            var ruleCohX = 0f; var ruleCohY = 0f

            if (nSep > 0) {
                // La séparation peut dominer les autres règles quand ça se densifie
                steer(sepX, sepY, vxi, vyi, maxSpeed, maxForce * 2f)
                ruleSepX = steerX * separationWeight
                ruleSepY = steerY * separationWeight
                fx += ruleSepX; fy += ruleSepY
            }
            if (nCore > 0) {
                // Répulsion de contact non normalisée : croît avec le chevauchement
                var cfx = coreX * maxForce * 2.5f
                var cfy = coreY * maxForce * 2.5f
                val cl = sqrt(cfx * cfx + cfy * cfy)
                val cap = maxForce * 3f
                if (cl > cap) {
                    cfx = cfx / cl * cap
                    cfy = cfy / cl * cap
                }
                fx += cfx; fy += cfy
            }
            if (n > 0) {
                steer(aliX, aliY, vxi, vyi, maxSpeed, maxForce)
                ruleAliX = steerX * alignmentWeight
                ruleAliY = steerY * alignmentWeight
                fx += ruleAliX; fy += ruleAliY

                steer(cohX / n - xi, cohY / n - yi, vxi, vyi, maxSpeed, maxForce)
                ruleCohX = steerX * cohesionWeight
                ruleCohY = steerY * cohesionWeight
                fx += ruleCohX; fy += ruleCohY
            }

            if (captureVision && i == 0) {
                visionSepX = ruleSepX; visionSepY = ruleSepY
                visionAliX = ruleAliX; visionAliY = ruleAliY
                visionCohX = ruleCohX; visionCohY = ruleCohY
            }

            // Fuite des prédateurs
            for (p in 0 until predatorCount) {
                val dx = xi - px[p]
                val dy = yi - py[p]
                if (dx * dx + dy * dy < fleeR2) {
                    steer(dx, dy, vxi, vyi, maxSpeed, maxForce)
                    fx += steerX * 2.6f
                    fy += steerY * 2.6f
                }
            }

            // Évitement des obstacles
            for (k in 0 until obstacleCount) {
                val dx = xi - obstacles[k * 3]
                val dy = yi - obstacles[k * 3 + 1]
                val influence = obstacles[k * 3 + 2] + obstacleMargin
                val d2 = dx * dx + dy * dy
                if (d2 < influence * influence && d2 > 1f) {
                    val d = sqrt(d2)
                    val push = (1f - d / influence) * maxForce * 2.2f
                    fx += dx / d * push
                    fy += dy / d * push
                }
            }

            // Interaction tactile
            if (touchActive) {
                when (touchMode) {
                    TOUCH_ATTRACT -> {
                        steer(touchX - xi, touchY - yi, vxi, vyi, maxSpeed, maxForce)
                        fx += steerX * 1.2f
                        fy += steerY * 1.2f
                    }
                    TOUCH_REPEL -> {
                        val dx = xi - touchX
                        val dy = yi - touchY
                        if (dx * dx + dy * dy < repelRadius * repelRadius) {
                            steer(dx, dy, vxi, vyi, maxSpeed, maxForce)
                            fx += steerX * 2.4f
                            fy += steerY * 2.4f
                        }
                    }
                }
            }

            // Évitement des bords (mode Murs) : force progressive à l'approche
            if (walls) {
                if (xi < wallMargin) fx += (1f - xi / wallMargin) * maxForce * 1.4f
                else if (xi > w - wallMargin) fx -= (1f - (w - xi) / wallMargin) * maxForce * 1.4f
                if (yi < wallMargin) fy += (1f - yi / wallMargin) * maxForce * 1.4f
                else if (yi > h - wallMargin) fy -= (1f - (h - yi) / wallMargin) * maxForce * 1.4f
            }

            // Intégration + bornes de vitesse + bords (torique ou murs)
            var nvx = vxi + fx * dt
            var nvy = vyi + fy * dt
            val sp = sqrt(nvx * nvx + nvy * nvy)
            if (sp > maxSpeed) {
                nvx = nvx / sp * maxSpeed
                nvy = nvy / sp * maxSpeed
            } else if (sp < minSpeed && sp > 1e-4f) {
                nvx = nvx / sp * minSpeed
                nvy = nvy / sp * minSpeed
            }
            var nx = xi + nvx * dt
            var ny = yi + nvy * dt
            if (walls) {
                if (nx < 1f) { nx = 1f; if (nvx < 0f) nvx = -nvx * 0.5f }
                else if (nx > w - 1f) { nx = w - 1f; if (nvx > 0f) nvx = -nvx * 0.5f }
                if (ny < 1f) { ny = 1f; if (nvy < 0f) nvy = -nvy * 0.5f }
                else if (ny > h - 1f) { ny = h - 1f; if (nvy > 0f) nvy = -nvy * 0.5f }
            } else {
                if (nx < 0f) nx += w else if (nx >= w) nx -= w
                if (ny < 0f) ny += h else if (ny >= h) ny -= h
            }
            vx[i] = nvx
            vy[i] = nvy
            x[i] = nx
            y[i] = ny
        }

        stepPredators(dt, w, h, per, maxSpeed, maxForce)
    }

    private fun stepPredators(dt: Float, w: Float, h: Float, per: Float, maxSpeed: Float, maxForce: Float) {
        val chaseR2 = per * 4f * (per * 4f)
        val predSpeed = maxSpeed * 0.85f
        for (p in 0 until predatorCount) {
            var bestD2 = chaseR2
            var bestI = -1
            for (i in 0 until count) {
                val dx = x[i] - px[p]
                val dy = y[i] - py[p]
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) { bestD2 = d2; bestI = i }
            }
            var fx = 0f
            var fy = 0f
            if (bestI >= 0) {
                steer(x[bestI] - px[p], y[bestI] - py[p], pvx[p], pvy[p], predSpeed, maxForce)
                fx = steerX
                fy = steerY
            } else {
                // Errance : la direction dévie aléatoirement
                val a = (Random.nextFloat() - 0.5f) * 3.5f * dt
                val c = cos(a); val s = sin(a)
                val nvx = pvx[p] * c - pvy[p] * s
                pvy[p] = pvx[p] * s + pvy[p] * c
                pvx[p] = nvx
            }
            var nvx = pvx[p] + fx * dt
            var nvy = pvy[p] + fy * dt
            val sp = sqrt(nvx * nvx + nvy * nvy)
            if (sp > predSpeed) {
                nvx = nvx / sp * predSpeed
                nvy = nvy / sp * predSpeed
            } else if (sp < predSpeed * 0.4f && sp > 1e-4f) {
                nvx = nvx / sp * predSpeed * 0.4f
                nvy = nvy / sp * predSpeed * 0.4f
            }
            var nx = px[p] + nvx * dt
            var ny = py[p] + nvy * dt
            if (edgeMode == EDGE_WALLS) {
                if (nx < 1f) { nx = 1f; if (nvx < 0f) nvx = -nvx }
                else if (nx > w - 1f) { nx = w - 1f; if (nvx > 0f) nvx = -nvx }
                if (ny < 1f) { ny = 1f; if (nvy < 0f) nvy = -nvy }
                else if (ny > h - 1f) { ny = h - 1f; if (nvy > 0f) nvy = -nvy }
            } else {
                if (nx < 0f) nx += w else if (nx >= w) nx -= w
                if (ny < 0f) ny += h else if (ny >= h) ny -= h
            }
            pvx[p] = nvx
            pvy[p] = nvy
            px[p] = nx
            py[p] = ny
        }
    }

    // ── Rendu ────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (trailsEnabled) {
            val tc = trailCanvas
            val tb = trailBitmap
            if (tc != null && tb != null) {
                if (stepped) {
                    tc.drawPaint(trailFadePaint)
                    stampTrails(tc)
                }
                canvas.drawBitmap(tb, 0f, 0f, null)
            }
        }
        stepped = false

        drawObstacles(canvas)
        drawBoids(canvas)
        drawPredators(canvas)
        if (visionEnabled && count > 0) drawVisionOverlay(canvas)
    }

    private fun stampTrails(tc: Canvas) {
        val r = 1.6f * density
        boidPaint.style = Paint.Style.FILL
        for (i in 0 until count) {
            boidPaint.color = colorFor(i)
            boidPaint.alpha = 150
            tc.drawCircle(x[i], y[i], r, boidPaint)
        }
    }

    private fun drawBoids(canvas: Canvas) {
        boidPaint.style = Paint.Style.FILL
        if (colorMode == COLOR_FIREFLIES) {
            val core = 2.4f * density
            val halo = 6.5f * density
            for (i in 0 until count) {
                val flicker = 0.6f + 0.4f * sin(time * 3f + phase[i])
                boidPaint.color = colorFor(i)
                boidPaint.alpha = (40 * flicker).toInt()
                canvas.drawCircle(x[i], y[i], halo, boidPaint)
                boidPaint.alpha = (90 + 165 * flicker).toInt().coerceAtMost(255)
                canvas.drawCircle(x[i], y[i], core, boidPaint)
            }
            return
        }
        for (i in 0 until count) {
            boidPaint.color = colorFor(i)
            val angle = atan2(vy[i], vx[i]) * 57.2958f
            canvas.withSave {
                translate(x[i], y[i])
                rotate(angle)
                drawPath(boidPath, boidPaint)
            }
        }
    }

    private fun colorFor(i: Int): Int = if (speciesCount > 1) {
        speciesColors[i % speciesCount]
    } else when (colorMode) {
        COLOR_SPEED -> {
            val maxSpeed = BASE_SPEED_DP * density * speedFactor
            val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
            lerpColor(0xFF2E4FC8.toInt(), 0xFFEAF6FF.toInt(), (sp / maxSpeed).coerceIn(0f, 1f))
        }
        COLOR_AURORA -> {
            val t = (y[i] / height.coerceAtLeast(1)).coerceIn(0f, 1f)
            lerpColor(0xFF3DDC97.toInt(), 0xFF8A6CF0.toInt(), t)
        }
        COLOR_FIREFLIES -> 0xFFFFD54F.toInt()
        else -> {
            var idx = ((atan2(vy[i], vx[i]) / 6.2832f + 0.5f) * 72f).toInt()
            if (idx < 0) idx = 0 else if (idx > 71) idx = 71
            directionLut[idx]
        }
    }

    private fun drawPredators(canvas: Canvas) {
        boidPaint.style = Paint.Style.FILL
        for (p in 0 until predatorCount) {
            boidPaint.color = 0xFFFF5252.toInt()
            boidPaint.alpha = 36
            canvas.drawCircle(px[p], py[p], 14f * density, boidPaint)
            boidPaint.alpha = 255
            val angle = atan2(pvy[p], pvx[p]) * 57.2958f
            canvas.withSave {
                translate(px[p], py[p])
                rotate(angle)
                scale(1.9f, 1.9f)
                drawPath(boidPath, boidPaint)
            }
        }
    }

    private fun drawObstacles(canvas: Canvas) {
        boidPaint.style = Paint.Style.FILL
        boidPaint.color = 0xFF1C2438.toInt()
        strokePaint.color = 0xFF44537A.toInt()
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.pathEffect = null
        for (k in 0 until obstacleCount) {
            val ox = obstacles[k * 3]
            val oy = obstacles[k * 3 + 1]
            val radius = obstacles[k * 3 + 2]
            canvas.drawCircle(ox, oy, radius, boidPaint)
            canvas.drawCircle(ox, oy, radius, strokePaint)
        }
    }

    // ── Superposition pédagogique : ce que « voit » le boid 0 ────────────────
    private fun drawVisionOverlay(canvas: Canvas) {
        val fx = x[0]
        val fy = y[0]
        val per = perceptionDp * density
        val maxForce = BASE_SPEED_DP * density * speedFactor * 3.5f

        // Rayon de perception + rayon de séparation
        strokePaint.color = Color.argb(70, 255, 255, 255)
        strokePaint.strokeWidth = 1f * density
        strokePaint.pathEffect = null
        canvas.drawCircle(fx, fy, per, strokePaint)
        strokePaint.pathEffect = dashEffect
        strokePaint.color = Color.argb(45, 255, 120, 150)
        canvas.drawCircle(fx, fy, per * sqrt(0.2f), strokePaint)
        strokePaint.pathEffect = null

        // Liens vers les voisins perçus
        strokePaint.color = Color.argb(50, 255, 255, 255)
        for (k in 0 until visionNeighborCount) {
            canvas.drawLine(fx, fy, visionNeighbors[k * 2], visionNeighbors[k * 2 + 1], strokePaint)
        }

        // Boid focalisé
        boidPaint.style = Paint.Style.FILL
        boidPaint.color = Color.WHITE
        val angle = atan2(vy[0], vx[0]) * 57.2958f
        canvas.withSave {
            translate(fx, fy)
            rotate(angle)
            scale(1.5f, 1.5f)
            drawPath(boidPath, boidPaint)
        }

        // Les trois forces, mises à l'échelle pour rester lisibles
        val arrowScale = 80f * density / maxForce
        drawForceArrow(canvas, fx, fy, visionSepX * arrowScale, visionSepY * arrowScale, sepColor)
        drawForceArrow(canvas, fx, fy, visionAliX * arrowScale, visionAliY * arrowScale, aliColor)
        drawForceArrow(canvas, fx, fy, visionCohX * arrowScale, visionCohY * arrowScale, cohColor)

        drawVisionLegend(canvas)
    }

    private fun drawForceArrow(canvas: Canvas, ox: Float, oy: Float, dx: Float, dy: Float, color: Int) {
        val len = sqrt(dx * dx + dy * dy)
        if (len < 3f * density) return
        val tx = ox + dx
        val ty = oy + dy
        strokePaint.color = color
        strokePaint.strokeWidth = 2f * density
        canvas.drawLine(ox, oy, tx, ty, strokePaint)
        val a = atan2(dy, dx)
        val hs = 5f * density
        canvas.drawLine(tx, ty, tx - hs * cos(a - 0.5f), ty - hs * sin(a - 0.5f), strokePaint)
        canvas.drawLine(tx, ty, tx - hs * cos(a + 0.5f), ty - hs * sin(a + 0.5f), strokePaint)
    }

    private fun drawVisionLegend(canvas: Canvas) {
        val pad = 12f * density
        val lineH = 16f * density
        val swatch = 14f * density
        val labels = arrayOf(sepLabel, aliLabel, cohLabel)
        val colors = intArrayOf(sepColor, aliColor, cohColor)
        strokePaint.strokeWidth = 3f * density
        for (r in 0..2) {
            val cy = pad + r * lineH + lineH / 2f
            strokePaint.color = colors[r]
            canvas.drawLine(pad, cy, pad + swatch, cy, strokePaint)
            canvas.drawText(labels[r], pad + swatch + 6f * density, cy + 4f * density, textPaint)
        }
    }

    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val r = (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t).toInt()
        val g = (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t).toInt()
        val b = (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t).toInt()
        return Color.rgb(r, g, b)
    }

    // ── Tactile ──────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (touchMode == TOUCH_OBSTACLE) {
                    if (obstacleCount < MAX_OBSTACLES) {
                        obstacles[obstacleCount * 3] = event.x
                        obstacles[obstacleCount * 3 + 1] = event.y
                        obstacles[obstacleCount * 3 + 2] = 30f * density
                        obstacleCount++
                        onObstaclesChanged?.invoke(obstacleCount)
                    }
                } else {
                    touchActive = true
                    touchX = event.x
                    touchY = event.y
                }
                if (!running) invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TOUCH_OBSTACLE) {
                    if (obstacleCount > 0) {
                        // Le glisser repositionne l'obstacle qui vient d'être posé
                        obstacles[(obstacleCount - 1) * 3] = event.x
                        obstacles[(obstacleCount - 1) * 3 + 1] = event.y
                    }
                } else {
                    touchX = event.x
                    touchY = event.y
                }
                if (!running) invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchActive = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
