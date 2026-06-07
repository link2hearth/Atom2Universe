package com.Atom2Universe.app.games.motocross

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port fidèle du mini-jeu Motocross JavaScript (assets/scripts/arcade/motocross.js).
 *
 * Le monde est exprimé dans les mêmes unités que le JS (BIKE_SCALE 0.6, roue ≈ 10.8 u).
 * La piste est générée par une bibliothèque de blocs lissés par splines Catmull-Rom,
 * exactement comme le JS, ce qui garantit un sol continu sans discontinuités.
 *
 * Rendu : la caméra applique un facteur [density] pour reproduire le rapport pixel CSS
 * du JS (sur une toile web, 1 unité monde ≈ 1 px CSS). Sans ce facteur, la moto
 * apparaîtrait minuscule sur l'écran haute densité d'un téléphone.
 */
class MotocrossView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // ── Terrain ────────────────────────────────────────────────────────────────

    private val points = ArrayList<FloatArray>()       // [x, y] en coords monde
    private val segments = ArrayList<Segment>()
    private val checkpoints = ArrayList<FloatArray>()   // [x, y]

    private var prevBlock: Block = START_BLOCK
    private var genY = 0f
    private var trackLength = 0f
    private val history = ArrayList<String>()
    private var nextCheckpointX = 10f
    private var highestY = 0f
    private var fallThreshold = 600f

    private var currentCheckpoint = 0
    private var respawnX = 0f
    private var respawnY = 0f
    private var respawnAngle = 0f

    // ── État du vélo ─────────────────────────────────────────────────────────────

    private var posX = 0f; private var posY = 0f
    private var velX = 0f; private var velY = 0f
    private var angle = 0f; private var angVel = 0f
    private var runStartX = 0f

    private val back = Wheel()
    private val front = Wheel()
    private val cBack = Contact()
    private val cFront = Contact()

    // Boost (maintien de l'accélérateur)
    private var boostHold = 0f
    private var boostFactor = 1f

    // ── Entrées ─────────────────────────────────────────────────────────────────

    @Volatile private var accel = false
    @Volatile private var brake = false

    // ── Caméra ──────────────────────────────────────────────────────────────────

    private var camX = 0f; private var camY = 0f
    private var camZoom = 1f
    private var vw = 1f; private var vh = 1f
    private var density = 1f

    // ── État du jeu ──────────────────────────────────────────────────────────────

    var gameOver = false; private set
    private var pendingRespawn = false
    private var maxDistM = 0f
    private var bestDistM = 0f
    private var accumulator = 0f

    @Volatile private var running = false
    @Volatile private var resetRequested = false
    private var thread: Thread? = null
    private var lastNs = 0L

    var onStats: ((distM: Float, speedKmh: Float) -> Unit)? = null

    // ── Assets ────────────────────────────────────────────────────────────────────

    @Volatile private var bikeBmp: Bitmap? = null
    @Volatile private var bgBmp: Bitmap? = null
    // Tuile de fond pré-redimensionnée (évite le rééchantillonnage par frame)
    private var bgTile: Bitmap? = null
    private var bgTileSrc: Bitmap? = null
    private var bgTileH = 0f

    // ── Paints / shaders cachés ────────────────────────────────────────────────────

    private val overlayPaint = Paint()
    private var overlayShader: LinearGradient? = null
    private var skyShader: LinearGradient? = null
    private val groundFill  = Paint().apply { color = Color.argb(242, 16, 22, 38) }
    private val trackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(96, 165, 250) }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shapePaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // Buffers réutilisés pour éviter les allocations dans la boucle physique
    private val sampleBuf = FloatArray(6)
    private val sampleBufB = FloatArray(6)
    private val sampleBufC = FloatArray(6)

    init {
        holder.addCallback(this)
        isFocusable = true
        density = resources.displayMetrics.density.coerceAtLeast(1f)
        loadAssets()
    }

    private fun loadAssets() {
        Thread {
            try {
                context.assets.open("Assets/sprites/Moto.png").use { bikeBmp = BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {}
            val bg = if (Random.nextBoolean()) "city_background_night.png" else "city_background_sunset.png"
            try {
                context.assets.open("Assets/sprites/$bg").use { bgBmp = BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {}
        }.start()
    }

    // ── Cycle SurfaceView ──────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {}

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
        vw = w.toFloat().coerceAtLeast(1f)
        vh = ht.toFloat().coerceAtLeast(1f)
        txtPaint.textSize = vw * 0.05f
        overlayShader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.argb(89, 8, 12, 24), Color.argb(217, 5, 8, 18)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        skyShader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.rgb(11, 18, 36), Color.rgb(5, 8, 17)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        bgTile = null   // forcer la régénération de la tuile à la nouvelle taille
        resetRequested = true
        if (!running) resume()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { pause() }

    fun resume() {
        if (running) return
        running = true; accumulator = 0f; lastNs = System.nanoTime()
        thread = Thread(this).apply { name = "Motocross"; start() }
    }

    fun pause() {
        running = false; thread?.join(500); thread = null
    }

    /** Demande une réinitialisation ; effectuée sur le thread physique (cf. [doReset]). */
    fun resetGame() { resetRequested = true }

    private fun doReset() {
        buildTrack()
        currentCheckpoint = 0
        computeRespawnData(0)
        applyRespawn(false)
        maxDistM = 0f; gameOver = false; pendingRespawn = false; accumulator = 0f
        boostHold = 0f; boostFactor = 1f
        runStartX = respawnX
        loadBest()
        lastNs = System.nanoTime()
    }

    // ── Génération de piste (port du JS) ──────────────────────────────────────────

    private fun buildTrack() {
        camX = 0f; camY = 0f   // évite que prune() rogne la nouvelle piste avec un ancien camX
        points.clear(); segments.clear(); checkpoints.clear(); history.clear()
        prevBlock = START_BLOCK; genY = 0f; trackLength = 0f
        history.add(START_BLOCK.id)
        val end = appendBlockPoints(START_BLOCK, 0f, 0f, false)
        if (end != null) { trackLength = end[0]; genY = end[1] }
        nextCheckpointX = (if (points.isNotEmpty()) points[0][0] else 0f) + 10f
        rebuildSegments()
        recomputeHighest()
        ensureCheckpoints()
        extendTrack(INITIAL_BLOCK_COUNT)
    }

    private fun appendBlockPoints(block: Block, offsetX: Float, offsetY: Float, skipFirst: Boolean): FloatArray? {
        var last: FloatArray? = null
        for (i in block.geo.indices) {
            if (skipFirst && i == 0) continue
            val p = block.geo[i]
            val wp = floatArrayOf(offsetX + p[0], offsetY + p[1])
            points.add(wp); last = wp
        }
        return last
    }

    private fun extendTrack(count: Int) {
        var added = 0
        while (added < count) {
            var next = pickNextBlock(prevBlock, genY)
            if (next == null) next = pickLandingBlock(prevBlock, genY) ?: START_BLOCK
            val last = appendBlockPoints(next, trackLength, genY, true) ?: break
            genY = last[1]; trackLength = last[0]
            history.add(next.id); if (history.size > 5) history.removeAt(0)
            prevBlock = next
            added++
        }
        prune()
        rebuildSegments()
        recomputeHighest()
        ensureCheckpoints()
    }

    private fun ensureTrackAhead() {
        if (trackLength - posX < TRACK_AHEAD_BUFFER) extendTrack(EXTEND_BLOCK_COUNT)
    }

    private fun pickNextBlock(prev: Block, currentY: Float): Block? {
        val tolerances = floatArrayOf(SLOPE_STEP, SLOPE_STEP * 1.5f, SLOPE_STEP * 2.5f, Float.POSITIVE_INFINITY)
        var pool: List<Block> = emptyList()
        for (tol in tolerances) {
            pool = TRACK_BLOCKS.filter { b ->
                blockWithinElevation(b, currentY) &&
                    (tol.isInfinite() || abs(prev.slopeOut - b.slopeIn) <= tol)
            }
            if (pool.isNotEmpty()) break
        }
        if (pool.isEmpty()) return null
        val preferred = pool.filter { !history.contains(it.id) }
        val candidates = if (preferred.isNotEmpty()) preferred else pool
        var tuned = candidates
        if (currentY > ELEVATION_LIMIT * 0.5f) {
            val descending = candidates.filter { it.y1 <= 0f }
            if (descending.isNotEmpty()) tuned = descending
        } else if (currentY < -ELEVATION_LIMIT * 0.5f) {
            val ascending = candidates.filter { it.y1 >= 0f }
            if (ascending.isNotEmpty()) tuned = ascending
        }
        return tuned[Random.nextInt(tuned.size)]
    }

    private fun pickLandingBlock(prev: Block, currentY: Float): Block? {
        val tolerances = floatArrayOf(SLOPE_STEP, SLOPE_STEP * 2f, Float.POSITIVE_INFINITY)
        var pool: List<Block> = emptyList()
        for (tol in tolerances) {
            pool = LANDING_BLOCKS.filter { b ->
                blockWithinElevation(b, currentY) &&
                    (tol.isInfinite() || abs(prev.slopeOut - b.slopeIn) <= tol)
            }
            if (pool.isNotEmpty()) break
        }
        if (pool.isEmpty()) return null
        return pool[Random.nextInt(pool.size)]
    }

    private fun blockWithinElevation(b: Block, baseY: Float): Boolean {
        val mn = baseY + b.minY
        val mx = baseY + b.maxY
        return mn >= -ELEVATION_LIMIT && mx <= ELEVATION_LIMIT
    }

    private fun rebuildSegments() {
        segments.clear()
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]
            val len = hypot(dx, dy)
            if (len <= 0.0001f) continue
            val tx = dx / len; val ty = dy / len
            segments.add(Segment(a[0], a[1], b[0], b[1], tx, ty, ty, -tx, min(a[0], b[0]), max(a[0], b[0])))
        }
    }

    private fun recomputeHighest() {
        var h = Float.NEGATIVE_INFINITY
        for (p in points) if (p[1] > h) h = p[1]
        highestY = if (h.isFinite()) h else 0f
        fallThreshold = highestY + FALL_EXTRA_MARGIN
    }

    private fun ensureCheckpoints() {
        while (nextCheckpointX <= trackLength) {
            sample(nextCheckpointX, sampleBufC)
            checkpoints.add(floatArrayOf(sampleBufC[0], sampleBufC[1]))
            nextCheckpointX += CHECKPOINT_INTERVAL
        }
    }

    private fun prune() {
        if (camX == 0f) return
        val cut = camX - PRUNE_BEHIND
        while (points.size > 60 && points[1][0] < cut) points.removeAt(0)
    }

    // Échantillonne la piste à l'abscisse [x] → out = [px, py, nx, ny, tx, ty]
    private fun sample(x: Float, out: FloatArray) {
        if (segments.isEmpty()) {
            out[0] = x; out[1] = 0f; out[2] = 0f; out[3] = -1f; out[4] = 1f; out[5] = 0f; return
        }
        val cx = x.coerceIn(segments.first().minX, segments.last().maxX)
        var lo = 0; var hi = segments.size - 1; var idx = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].minX <= cx) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        val s = segments[idx.coerceIn(0, segments.size - 1)]
        val dx = s.p1x - s.p0x
        var t = 0f
        if (abs(dx) > 1e-6f) t = (cx - s.p0x) / dx
        t = t.coerceIn(0f, 1f)
        val py = s.p0y + (s.p1y - s.p0y) * t
        out[0] = cx; out[1] = py; out[2] = s.nx; out[3] = s.ny; out[4] = s.tx; out[5] = s.ty
    }

    // ── Respawn / checkpoints ──────────────────────────────────────────────────────

    private fun computeRespawnData(index: Int) {
        if (checkpoints.isEmpty()) { respawnX = 200f; respawnY = -WHEEL_RADIUS; respawnAngle = 0f; return }
        val idx = index.coerceIn(0, checkpoints.size - 1)
        val cp = checkpoints[idx]
        sample(cp[0], sampleBuf)
        sample(cp[0] + WHEEL_BASE, sampleBufB)
        val bX = sampleBuf[0]; val bY = sampleBuf[1] - WHEEL_RADIUS
        val fX = sampleBufB[0]; val fY = sampleBufB[1] - WHEEL_RADIUS
        val avgX = (bX + fX) / 2f; val avgY = (bY + fY) / 2f
        val ang = atan2(fY - bY, fX - bX)
        respawnX = avgX + sin(ang) * WHEEL_VERTICAL_OFFSET
        respawnY = avgY - cos(ang) * WHEEL_VERTICAL_OFFSET
        respawnAngle = ang
    }

    private fun applyRespawn(boost: Boolean) {
        posX = respawnX; posY = respawnY; angle = respawnAngle
        velX = if (boost) 110f else 0f; velY = 0f; angVel = 0f
        updateWheelData()
        camX = posX; camY = posY - CAM_OFFSET_Y; camZoom = 1f
        pendingRespawn = false
    }

    private fun updateCheckpointsProgress() {
        if (checkpoints.size < 2) return
        val nextIndex = min(checkpoints.size - 1, currentCheckpoint + 1)
        if (posX >= checkpoints[nextIndex][0] - 10f) {
            currentCheckpoint = nextIndex
            computeRespawnData(currentCheckpoint)
        }
    }

    // ── Boucle principale ──────────────────────────────────────────────────────────

    override fun run() {
        while (running) {
            if (resetRequested) { resetRequested = false; doReset() }

            val now = System.nanoTime()
            var delta = (now - lastNs) / 1e9f
            lastNs = now
            if (!delta.isFinite() || delta <= 0f) delta = PHYSICS_STEP
            delta = delta.coerceAtMost(MAX_FRAME_STEP)

            if (segments.isEmpty()) {
                val canvas0 = try { holder.lockCanvas() } catch (e: Exception) { null }
                if (canvas0 != null) try { drawBackground(canvas0) } finally { holder.unlockCanvasAndPost(canvas0) }
                Thread.sleep(8); continue
            }

            if (pendingRespawn && !gameOver) applyRespawn(true) else pendingRespawn = false

            if (!gameOver) {
                accumulator += delta
                val maxAcc = PHYSICS_STEP * 5f
                if (accumulator > maxAcc) accumulator = maxAcc
                while (accumulator >= PHYSICS_STEP) {
                    stepPhysics(PHYSICS_STEP)
                    accumulator -= PHYSICS_STEP
                }
                updateCamera(delta)
                val spd = hypot(velX, velY) * UNIT_TO_METERS * 3.6f
                onStats?.invoke(maxDistM, spd)
            }

            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (canvas != null) try { renderFrame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }

            val sleep = FRAME_NS - (System.nanoTime() - now)
            if (sleep > 1_000_000L) Thread.sleep(sleep / 1_000_000L)
        }
    }

    // ── Touch ────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (gameOver) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) resetGame()
            return true
        }
        val mid = vw / 2f
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                var l = false; var r = false
                for (i in 0 until ev.pointerCount) { if (ev.getX(i) < mid) l = true else r = true }
                brake = l; accel = r
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { brake = false; accel = false }
            MotionEvent.ACTION_POINTER_UP -> {
                val up = ev.actionIndex; var l = false; var r = false
                for (i in 0 until ev.pointerCount) {
                    if (i == up) continue
                    if (ev.getX(i) < mid) l = true else r = true
                }
                brake = l; accel = r
            }
        }
        return true
    }

    // ── Physique (port exact du JS stepPhysics) ───────────────────────────────────

    private fun updateWheelData() {
        val ca = cos(angle); val sa = sin(angle)
        // arrière : localX = -WHEEL_BASE/2 ; avant : +WHEEL_BASE/2 ; localY = WHEEL_VERTICAL_OFFSET
        var lx = -WHEEL_BASE / 2f; val ly = WHEEL_VERTICAL_OFFSET
        back.posX = posX + lx * ca - ly * sa
        back.posY = posY + lx * sa + ly * ca
        back.rx = back.posX - posX; back.ry = back.posY - posY
        lx = WHEEL_BASE / 2f
        front.posX = posX + lx * ca - ly * sa
        front.posY = posY + lx * sa + ly * ca
        front.rx = front.posX - posX; front.ry = front.posY - posY
    }

    private fun updateBoost(dt: Float, accelerateHeld: Boolean) {
        if (accelerateHeld) {
            boostHold += dt
            val maxHold = BOOST_BASE_DELAY + BOOST_STEP_DURATION * BOOST_MAX_STEPS
            if (boostHold > maxHold) boostHold = maxHold
            val extra = boostHold - BOOST_BASE_DELAY
            if (extra >= BOOST_STEP_DURATION) {
                val steps = min((extra / BOOST_STEP_DURATION).toInt(), BOOST_MAX_STEPS)
                boostFactor = (1f + BOOST_STEP_RATE).pow(steps.toFloat()).coerceIn(1f, BOOST_MAX_MULTIPLIER)
            } else boostFactor = 1f
        } else { boostHold = 0f; boostFactor = 1f }
    }

    /** Remplit [out] et renvoie onGround. */
    private fun computeWheelContact(wh: Wheel, driveInput: Float, out: Contact) {
        sample(wh.posX, sampleBuf)
        val px = sampleBuf[0]; val py = sampleBuf[1]
        val nx = sampleBuf[2]; val ny = sampleBuf[3]
        val tx = sampleBuf[4]; val ty = sampleBuf[5]
        val slopeAngle = atan2(ty, tx)
        val toSx = wh.posX - px; val toSy = wh.posY - py
        val distance = toSx * nx + toSy * ny
        val penetration = WHEEL_RADIUS - distance
        val onGround = penetration > 0f
        out.onGround = onGround
        out.clearance = distance - WHEEL_RADIUS
        out.nx = nx; out.ny = ny; out.tx = tx; out.ty = ty
        out.steepGripFactor = 0f

        var driveForce = when {
            driveInput > 0f -> driveInput * ENGINE_FORCE * boostFactor
            driveInput < 0f -> driveInput * BRAKE_FORCE
            else -> 0f
        }

        if (!onGround) {
            out.normalForce = 0f; out.tangentForce = driveForce; out.corrX = 0f; out.corrY = 0f
            return
        }

        if (driveInput < 0f) driveForce *= GROUND_BRAKE_MULTIPLIER
        if (driveInput > 0f) {
            val slopeFactor = (1f + max(0f, abs(ty) - 0.2f) * 2.4f).coerceIn(1f, 3.5f)
            driveForce *= slopeFactor
        }

        val pvx = velX - angVel * wh.ry
        val pvy = velY + angVel * wh.rx
        val normalVel = pvx * nx + pvy * ny
        var normalForce = penetration * SPRING_STIFFNESS - normalVel * SPRING_DAMPING
        if (normalForce < 0f) normalForce = 0f

        var frictionScale = FRICTION_COEFFICIENT
        var steepGripFactor = 0f
        if (ty > 0f && normalForce > 0f) {
            val thr = GRIP_THRESHOLD_RAD
            val maxA = max(thr + 0.0001f, GRIP_MAX_RAD)
            if (slopeAngle > thr) {
                val norm = ((slopeAngle - thr) / (maxA - thr)).coerceIn(0f, 1f)
                steepGripFactor = norm
                normalForce += GRIP_NORMAL_BOOST * norm
                normalForce += GRIP_DOWNFORCE * norm
                frictionScale *= 1f + GRIP_FRICTION_MULT * norm
            }
        }
        out.steepGripFactor = steepGripFactor

        val tangentVel = pvx * tx + pvy * ty
        val desired = -tangentVel * FRICTION_DAMPING + driveForce
        val maxFric = normalForce * frictionScale
        val tangentForce = desired.coerceIn(-maxFric, maxFric)

        var correctionScale = penetration * NORMAL_CORRECTION_FACTOR
        if (steepGripFactor > 0f) correctionScale += GRIP_CORRECTION_BOOST * steepGripFactor

        out.normalForce = normalForce
        out.tangentForce = tangentForce
        out.corrX = nx * correctionScale
        out.corrY = ny * correctionScale
    }

    private fun stepPhysics(dt: Float) {
        val accelerateHeld = accel
        val brakeHeld = brake
        updateBoost(dt, accelerateHeld)

        updateWheelData()
        val controlDelta = (if (accelerateHeld) 1f else 0f) - (if (brakeHeld) 1f else 0f)

        if (accelerateHeld && brakeHeld) {
            velX = 0f; velY = 0f; angVel = 0f
            updateWheelData()
            return
        }

        // Sondes (drive 0) pour déterminer le contact
        computeWheelContact(back, 0f, cBack)
        computeWheelContact(front, 0f, cFront)
        val backOnGround = cBack.onGround
        val frontOnGround = cFront.onGround
        val airborne = !backOnGround && !frontOnGround
        val backClearance = cBack.clearance
        val frontClearance = cFront.clearance
        val wheelsCloseToGround = backClearance < GROUND_PROXIMITY_THRESHOLD && frontClearance < GROUND_PROXIMITY_THRESHOLD
        val minClearance = min(backClearance, frontClearance)
        val nearByWheels = minClearance < UPSIDE_DOWN_WHEEL_CLEARANCE

        // Dégagement au centre du châssis
        sample(posX, sampleBufC)
        val toCx = posX - sampleBufC[0]; val toCy = posY - sampleBufC[1]
        val centerClearance = max(0f, toCx * sampleBufC[2] + toCy * sampleBufC[3])
        val nearByCenter = centerClearance < UPSIDE_DOWN_CENTER_CLEARANCE
        val nearGroundWhileUpsideDown = nearByWheels || nearByCenter

        val allowRotationControl = !wheelsCloseToGround
        val driveControl = if (airborne) 0f else controlDelta
        val tiltControl = if (allowRotationControl) controlDelta else 0f

        // Contacts définitifs (avec force motrice)
        if (!airborne) {
            computeWheelContact(back, driveControl, cBack)
            computeWheelContact(front, driveControl * 0.85f, cFront)
        }

        var totalForceX = 0f
        var totalForceY = MASS * GRAVITY
        val tiltMultiplier = if (airborne) 0f else 0.35f
        var totalTorque = tiltControl * TILT_TORQUE * tiltMultiplier
        var corrX = 0f; var corrY = 0f; var corrCount = 0
        var slopeAngularDamping = 0f
        var frontSteepGrip = 0f

        for (k in 0..1) {
            val wh = if (k == 0) back else front
            val c = if (k == 0) cBack else cFront
            totalForceX += c.normalForce * c.nx + c.tangentForce * c.tx
            totalForceY += c.normalForce * c.ny + c.tangentForce * c.ty
            val torqueNormal = wh.rx * c.ny - wh.ry * c.nx
            val torqueTangent = wh.rx * c.ty - wh.ry * c.tx
            totalTorque += torqueNormal * c.normalForce + torqueTangent * c.tangentForce
            if (c.onGround) { corrX += c.corrX; corrY += c.corrY; corrCount++ }
            if (c.steepGripFactor > 0f) {
                slopeAngularDamping = max(slopeAngularDamping, GRIP_ANG_DAMP_MULT * c.steepGripFactor)
                if (k == 1) frontSteepGrip = max(frontSteepGrip, c.steepGripFactor)
            }
        }

        if (frontSteepGrip > 0f && totalTorque > 0f) totalTorque -= GRIP_TORQUE_ASSIST * frontSteepGrip

        // En montée, le poids se déplace vers l'avant pour accrocher la pente
        if (!airborne) {
            val avgTy = (cBack.ty + cFront.ty) * 0.5f
            val climbFactor = (-avgTy).coerceAtLeast(0f)  // positif uniquement en montée
            if (climbFactor > 0.04f) {
                val velFwd = (velX * cBack.tx + velY * cBack.ty).coerceAtLeast(0f)
                val speedFactor = (velFwd / 100f).coerceIn(0f, 1f)
                val accelFactor = if (accelerateHeld) 1f else 0.45f
                totalTorque -= CLIMB_WEIGHT_TRANSFER * climbFactor * (0.55f + speedFactor * 0.45f) * accelFactor
            }
        }

        if (airborne) {
            val rotationInput = if (allowRotationControl) controlDelta.coerceIn(-1f, 1f) else 0f
            val targetAngular = rotationInput * AIR_ROTATION_MAX_SPEED
            val maxDelta = AIR_ROTATION_ACCEL * dt
            angVel += (targetAngular - angVel).coerceIn(-maxDelta, maxDelta)
        }

        velX += (totalForceX / MASS) * dt
        velY += (totalForceY / MASS) * dt
        angVel += (totalTorque / BIKE_INERTIA) * dt

        velX *= 1f - LINEAR_DAMPING * dt
        velY *= 1f - LINEAR_DAMPING * dt
        val angDampFactor = ((ANGULAR_DAMPING + slopeAngularDamping) * dt).coerceIn(0f, 0.95f)
        angVel *= 1f - angDampFactor

        if (airborne) angVel = angVel.coerceIn(-AIR_ROTATION_MAX_SPEED, AIR_ROTATION_MAX_SPEED)

        posX += velX * dt; posY += velY * dt; angle += angVel * dt

        if (corrCount > 0) { posX += corrX / corrCount; posY += corrY / corrCount }

        updateWheelData()

        val progress = posX - runStartX
        if (progress.isFinite()) {
            val meters = progress * UNIT_TO_METERS
            if (meters > maxDistM) {
                maxDistM = meters
                if (maxDistM > bestDistM) { bestDistM = maxDistM; saveBest() }
            }
        }

        updateCheckpointsProgress()
        ensureTrackAhead()

        if (posY - WHEEL_RADIUS > fallThreshold) pendingRespawn = true

        if (!gameOver) {
            val normalizedAngle = atan2(sin(angle), cos(angle))
            val isUpsideDown = abs(normalizedAngle) > (PI_F * 0.75f)
            val falling = velY > 0f
            if (isUpsideDown && (!airborne || (falling && nearGroundWhileUpsideDown))) gameOver = true
        }
    }

    // ── Caméra ────────────────────────────────────────────────────────────────────

    private fun updateCamera(dt: Float) {
        val lookAhead = (velX * CAM_LOOK_AHEAD).coerceIn(-160f, 240f)
        val targetX = posX + lookAhead
        val targetY = posY - CAM_OFFSET_Y
        val smooth = 1f - exp(-CAM_SMOOTH * dt)
        camX += (targetX - camX) * smooth
        camY += (targetY - camY) * smooth
        val zoomTarget = computeCameraZoom(targetY)
        val zoomSmooth = 1f - exp(-CAM_ZOOM_SMOOTH * dt)
        camZoom += (zoomTarget - camZoom) * zoomSmooth
    }

    private fun computeCameraZoom(targetY: Float): Float {
        val height = vh / density   // hauteur logique (≈ px CSS du JS)
        if (height <= 0f) return camZoom
        val minX = posX - CAM_WINDOW_BEHIND
        val maxX = posX + CAM_WINDOW_AHEAD
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var found = false
        for (s in segments) {
            if (s.maxX < minX) continue
            if (s.minX > maxX && found) break
            minY = min(minY, min(s.p0y, s.p1y)); maxY = max(maxY, max(s.p0y, s.p1y)); found = true
        }
        if (!found) return camZoom
        val zoomMin = if (height <= CAM_SMALL_SCREEN_H) min(CAM_ZOOM_MIN_SMALL, CAM_ZOOM_MIN) else CAM_ZOOM_MIN
        var allowed = CAM_ZOOM_MAX
        val topAvail = height * CAM_VERTICAL_ANCHOR
        val bottomAvail = height * (1f - CAM_VERTICAL_ANCHOR)
        if (topAvail > 0f) {
            val topDelta = targetY - (minY - CAM_TOP_MARGIN)
            if (topDelta > 0f) allowed = min(allowed, topAvail / topDelta)
        }
        if (bottomAvail > 0f) {
            val bottomDelta = (maxY + CAM_BOTTOM_MARGIN) - targetY
            if (bottomDelta > 0f) allowed = min(allowed, bottomAvail / bottomDelta)
        }
        if (!allowed.isFinite() || allowed <= 0f) allowed = camZoom
        return allowed.coerceIn(zoomMin, CAM_ZOOM_MAX)
    }

    // ── Rendu ────────────────────────────────────────────────────────────────────

    private fun renderFrame(canvas: Canvas) {
        drawBackground(canvas)

        val scale = density * camZoom
        canvas.save()
        canvas.translate(vw * 0.5f, vh * CAM_VERTICAL_ANCHOR)
        canvas.scale(scale, scale)
        canvas.translate(-camX, -camY)
        drawTrack(canvas, scale)
        drawBike(canvas)
        canvas.restore()

        if (maxDistM < 8f) drawHints(canvas)
        if (gameOver) drawGameOver(canvas)
    }

    /** Prépare une tuile de fond mise à l'échelle une seule fois (blit 1:1 ensuite). */
    private fun ensureBgTile() {
        val src = bgBmp ?: return
        // Hauteur = plein écran (couverture garantie), largeur plafonnée pour borner la mémoire.
        val targetH = vh
        if (bgTile != null && bgTileSrc === src && bgTileH == targetH) return
        bgTileSrc = src; bgTileH = targetH
        if (src.width <= 0 || src.height <= 0) { bgTile = null; return }
        val h = vh.toInt().coerceAtLeast(1)
        val w = (src.width * (vh / src.height)).coerceIn(1f, 4096f).toInt()
        bgTile = try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (e: Exception) { null }
    }

    private fun drawBackground(canvas: Canvas) {
        ensureBgTile()
        val tile = bgTile
        if (tile != null) {
            val scaledW = tile.width.toFloat()
            val scaledH = tile.height.toFloat()
            val raw = (camX * BACKGROUND_SCROLL_RATIO * density) % scaledW
            val offset = (raw + scaledW) % scaledW
            val drawY = (vh - scaledH) / 2f
            var x = -offset
            while (x < vw) { canvas.drawBitmap(tile, x, drawY, null); x += scaledW }
        } else {
            overlayPaint.shader = skyShader
            canvas.drawRect(0f, 0f, vw, vh, overlayPaint)
            overlayPaint.shader = null
        }
        // Voile d'assombrissement (shader caché, comme le JS)
        overlayPaint.shader = overlayShader
        canvas.drawRect(0f, 0f, vw, vh, overlayPaint)
        overlayPaint.shader = null
    }

    private fun drawTrack(canvas: Canvas, scale: Float) {
        if (points.size < 2) return
        val halfW = (vw * 0.5f) / scale
        val left = camX - halfW - 80f
        val right = camX + halfW + 80f
        val bottom = camY + (vh * (1f - CAM_VERTICAL_ANCHOR)) / scale + 200f

        val path = Path()
        val line = Path()
        var started = false
        var firstX = 0f; var lastX = 0f
        for (p in points) {
            if (p[0] < left) continue
            if (!started) {
                path.moveTo(p[0], bottom)
                path.lineTo(p[0], p[1])
                line.moveTo(p[0], p[1])
                firstX = p[0]; started = true
            } else {
                path.lineTo(p[0], p[1])
                line.lineTo(p[0], p[1])
            }
            lastX = p[0]
            if (p[0] > right) break
        }
        if (!started) return
        path.lineTo(lastX, bottom)
        path.lineTo(firstX, bottom)
        path.close()

        canvas.drawPath(path, groundFill)

        // Épaisseur en unités monde : après mise à l'échelle (density*zoom) ≈ 4 px CSS
        trackStroke.strokeWidth = 4f / camZoom
        canvas.drawPath(line, trackStroke)
    }

    private fun drawBike(canvas: Canvas) {
        val image = bikeBmp
        if (image != null && image.width > 0 && image.height > 0) {
            val w = image.width.toFloat(); val h = image.height.toFloat()
            val bpx = BIKE_ANCHOR_BACK_X * w; val bpy = BIKE_ANCHOR_Y * h
            val fpx = BIKE_ANCHOR_FRONT_X * w; val fpy = BIKE_ANCHOR_Y * h
            val pixelDist = hypot(fpx - bpx, fpy - bpy)
            var adx = front.posX - back.posX; var ady = front.posY - back.posY
            var adist = hypot(adx, ady)
            if (adist <= 0.001f) { adist = WHEEL_BASE; adx = cos(angle) * adist; ady = sin(angle) * adist }
            if (pixelDist > 0f) {
                val axx = adx / adist; val axy = ady / adist
                val ayx = -axy; val ayy = axx
                val sc = adist / pixelDist
                val m = Matrix()
                m.setValues(floatArrayOf(
                    axx * sc, ayx * sc, back.posX,
                    axy * sc, ayy * sc, back.posY,
                    0f, 0f, 1f))
                canvas.save()
                canvas.concat(m)
                canvas.drawBitmap(image, -bpx, -bpy, spritePaint)
                canvas.restore()
                return
            }
        }
        drawBikeFallback(canvas)
    }

    private fun drawBikeFallback(canvas: Canvas) {
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = Color.rgb(15, 23, 42)
        canvas.drawCircle(back.posX, back.posY, WHEEL_RADIUS, shapePaint)
        canvas.drawCircle(front.posX, front.posY, WHEEL_RADIUS, shapePaint)
        canvas.save()
        canvas.translate(posX, posY)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        shapePaint.color = Color.rgb(56, 189, 248)
        canvas.drawRect(-CHASSIS_WIDTH / 2f, -CHASSIS_HEIGHT / 2f, CHASSIS_WIDTH / 2f, CHASSIS_HEIGHT / 2f, shapePaint)
        shapePaint.color = Color.rgb(186, 230, 253)
        canvas.drawRect(-CHASSIS_WIDTH / 4f, -CHASSIS_HEIGHT * 0.75f, CHASSIS_WIDTH / 4f, -CHASSIS_HEIGHT * 0.25f, shapePaint)
        canvas.restore()
    }

    private fun drawHints(canvas: Canvas) {
        txtPaint.typeface = Typeface.DEFAULT
        txtPaint.color = Color.argb(155, 255, 255, 255)
        txtPaint.textSize = vw * 0.038f
        canvas.drawText("◀ Freiner / Pencher", vw * 0.25f, vh * 0.9f, txtPaint)
        canvas.drawText("Accélérer / Pencher ▶", vw * 0.75f, vh * 0.9f, txtPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        overlayPaint.color = Color.argb(168, 0, 0, 0)
        canvas.drawRect(0f, 0f, vw, vh, overlayPaint)
        txtPaint.typeface = Typeface.DEFAULT_BOLD
        txtPaint.color = Color.rgb(255, 82, 44); txtPaint.textSize = vw * 0.09f
        canvas.drawText("GAME OVER", vw / 2f, vh * 0.38f, txtPaint)
        txtPaint.typeface = Typeface.DEFAULT; txtPaint.color = Color.WHITE; txtPaint.textSize = vw * 0.048f
        canvas.drawText("Distance : ${maxDistM.toInt()} m", vw / 2f, vh * 0.52f, txtPaint)
        canvas.drawText("Meilleur : ${bestDistM.toInt()} m", vw / 2f, vh * 0.62f, txtPaint)
        txtPaint.color = Color.rgb(88, 208, 255); txtPaint.textSize = vw * 0.042f
        canvas.drawText("Appuyez pour rejouer", vw / 2f, vh * 0.74f, txtPaint)
    }

    // ── Persistance ──────────────────────────────────────────────────────────────

    private fun loadBest() {
        bestDistM = context.getSharedPreferences("motocross_save", Context.MODE_PRIVATE).getFloat("best", 0f)
    }

    private fun saveBest() {
        context.getSharedPreferences("motocross_save", Context.MODE_PRIVATE).edit().putFloat("best", bestDistM).apply()
    }

    // ── Types internes ─────────────────────────────────────────────────────────────

    private class Wheel { var posX = 0f; var posY = 0f; var rx = 0f; var ry = 0f }
    private class Contact {
        var normalForce = 0f; var tangentForce = 0f
        var corrX = 0f; var corrY = 0f
        var nx = 0f; var ny = 0f; var tx = 0f; var ty = 0f
        var onGround = false; var clearance = 0f; var steepGripFactor = 0f
    }

    private companion object {
        const val PI_F = Math.PI.toFloat()

        // Physique (unités monde, identiques au JS)
        const val MASS = 60f
        const val GRAVITY = 1200f
        const val BIKE_SCALE = 0.6f
        const val CHASSIS_WIDTH = 108f * BIKE_SCALE
        const val CHASSIS_HEIGHT = 32f * BIKE_SCALE
        const val WHEEL_BASE = 92f * BIKE_SCALE
        const val WHEEL_RADIUS = 18f * BIKE_SCALE
        const val WHEEL_VERTICAL_OFFSET = 20f * BIKE_SCALE
        val BIKE_INERTIA = (MASS * (CHASSIS_WIDTH * CHASSIS_WIDTH + CHASSIS_HEIGHT * CHASSIS_HEIGHT)) / 12f
        const val ENGINE_FORCE = 24800f
        const val BRAKE_FORCE = ENGINE_FORCE * 0.9f
        const val GROUND_BRAKE_MULTIPLIER = 1.35f
        const val TILT_TORQUE = 31200f
        const val SPRING_STIFFNESS = 4200f
        const val SPRING_DAMPING = 180f
        const val FRICTION_DAMPING = 36f
        const val FRICTION_COEFFICIENT = 1.25f
        const val LINEAR_DAMPING = 0.05f
        const val ANGULAR_DAMPING = 0.12f
        const val NORMAL_CORRECTION_FACTOR = 0.7f
        val AIR_ROTATION_MAX_SPEED = PI_F * 3f
        val AIR_ROTATION_ACCEL = AIR_ROTATION_MAX_SPEED * 6f
        const val FALL_EXTRA_MARGIN = 480f
        const val GROUND_PROXIMITY_THRESHOLD = 10f
        const val UPSIDE_DOWN_WHEEL_CLEARANCE = 64f
        const val UPSIDE_DOWN_CENTER_CLEARANCE = CHASSIS_HEIGHT * 3f

        const val PHYSICS_STEP = 1f / 120f
        const val MAX_FRAME_STEP = 1f / 30f
        const val FRAME_NS = 1_000_000_000L / 60

        const val UNIT_TO_METERS = 0.1f / 3f

        // Boost
        const val BOOST_BASE_DELAY = 1f
        const val BOOST_STEP_DURATION = 0.1f
        const val BOOST_STEP_RATE = 0.1f
        const val BOOST_MAX_MULTIPLIER = 3f
        val BOOST_MAX_STEPS = kotlin.math.ceil(ln(BOOST_MAX_MULTIPLIER.toDouble()) / ln(1.0 + BOOST_STEP_RATE)).toInt()

        // steepSlopeGrip (defaults de motocross.json)
        val GRIP_THRESHOLD_RAD = Math.toRadians(40.0).toFloat()
        val GRIP_MAX_RAD = Math.toRadians(80.0).toFloat()
        const val GRIP_NORMAL_BOOST = 8800f
        const val GRIP_CORRECTION_BOOST = 18f
        const val GRIP_TORQUE_ASSIST = 42000f
        const val CLIMB_WEIGHT_TRANSFER = 68000f
        const val GRIP_ANG_DAMP_MULT = 1.6f
        const val GRIP_FRICTION_MULT = 1.8f
        const val GRIP_DOWNFORCE = 2200f

        // Génération de piste
        const val SLOPE_STEP = 0.12f
        const val ELEVATION_LIMIT = 520f
        const val INITIAL_BLOCK_COUNT = 14
        const val EXTEND_BLOCK_COUNT = 6
        const val TRACK_AHEAD_BUFFER = 3600f
        const val CHECKPOINT_INTERVAL = 960f
        const val PRUNE_BEHIND = 3000f

        // Caméra (motocross.json)
        const val CAM_LOOK_AHEAD = 0.24f
        const val CAM_OFFSET_Y = 180f
        const val CAM_SMOOTH = 6.5f
        const val CAM_ZOOM_SMOOTH = 5.2f
        const val CAM_VERTICAL_ANCHOR = 0.55f
        const val CAM_ZOOM_MIN = 0.48f
        const val CAM_ZOOM_MAX = 1f
        const val CAM_WINDOW_BEHIND = 420f
        const val CAM_WINDOW_AHEAD = 960f
        const val CAM_TOP_MARGIN = 140f
        const val CAM_BOTTOM_MARGIN = 220f
        const val CAM_SMALL_SCREEN_H = 720f
        const val CAM_ZOOM_MIN_SMALL = 0.4f

        // Fond
        const val BACKGROUND_SCROLL_RATIO = 0.22f
        const val BG_PORTRAIT_SCALE = 2f

        // Sprite moto (anchors JS)
        const val BIKE_ANCHOR_BACK_X = 0.18f
        const val BIKE_ANCHOR_FRONT_X = 0.82f
        const val BIKE_ANCHOR_Y = 0.82f
    }
}
