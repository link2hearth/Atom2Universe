package com.Atom2Universe.app.games.cosmorun

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rendu en fausse 3D du runner : la piste est un trapèze qui converge vers un
 * point de fuite, les entités grossissent en s'approchant du joueur.
 * La logique du jeu vit dans [CosmoRunGame] ; ici on ne fait que dessiner
 * et traduire les gestes (swipe gauche/droite/haut, tap = saut).
 */
class CosmoRunView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    companion object {
        private const val FRAME_TIME_NS = 1_000_000_000L / 60
        private const val Z_HALF = 9f          // contrôle la courbure de la perspective
        private const val HORIZON_RATIO = 0.30f
        private const val PLAYER_Y_RATIO = 0.82f
        private const val LANE_SPACING_RATIO = 0.30f // écart entre voies en bas d'écran
        private const val MIN_DEPTH_SCALE = 0.06f
        private const val JUMP_HEIGHT_RATIO = 0.16f
        private const val SWIPE_THRESHOLD_DP = 42f
    }

    val game = CosmoRunGame()
    var onGameOver: (() -> Unit)? = null

    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastNs = 0L
    private var gameOverNotified = false

    private var vw = 1f; private var vh = 1f
    private var horizonY = 0f; private var playerBaseY = 0f
    private var laneSpacing = 0f
    private val swipeThresholdPx = SWIPE_THRESHOLD_DP * context.resources.displayMetrics.density

    // ── Sprites ────────────────────────────────────────────────────────────────
    private var astronautBmp: Bitmap? = null
    private val atomBmps = arrayOfNulls<Bitmap>(CosmoRunGame.ATOM_VARIANTS)

    // ── Paints ─────────────────────────────────────────────────────────────────
    private val skyPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val entityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 40f; typeface = android.graphics.Typeface.MONOSPACE
    }
    private val stars = Array(60) {
        Triple(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat() * 1.3f)
    }

    // ── Input ──────────────────────────────────────────────────────────────────
    private var downX = 0f; private var downY = 0f
    private var gestureDone = false

    init {
        holder.addCallback(this)
        isFocusable = true
        loadAssets()
    }

    private fun loadAssets() {
        astronautBmp = loadBitmap("Assets/Image/Astronaute.png")
        for (i in atomBmps.indices) atomBmps[i] = loadBitmap("Assets/Image/Atom$i.png")
    }

    private fun loadBitmap(path: String): Bitmap? =
        try { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }
        catch (_: Exception) { null }

    fun startGame() {
        gameOverNotified = false
        game.start()
    }

    // ── Surface / thread ───────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) { resume() }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
        vw = w.toFloat().coerceAtLeast(1f)
        vh = ht.toFloat().coerceAtLeast(1f)
        horizonY = vh * HORIZON_RATIO
        playerBaseY = vh * PLAYER_Y_RATIO
        laneSpacing = vw * LANE_SPACING_RATIO
        hudPaint.textSize = (vh * 0.030f).coerceIn(28f, 52f)
        linePaint.strokeWidth = (vh * 0.003f).coerceAtLeast(2f)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { pause() }

    fun resume() {
        if (running) return
        running = true; lastNs = System.nanoTime()
        thread = Thread(this).apply { name = "CosmoRun"; start() }
    }

    fun pause() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 1f / 30f)
            lastNs = now

            val wasRunning = game.isRunning
            game.update(dt)
            if (wasRunning && game.isGameOver && !gameOverNotified) {
                gameOverNotified = true
                onGameOver?.invoke()
            }

            val canvas = try { holder.lockCanvas() } catch (_: Exception) { null }
            if (canvas != null) try { renderFrame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }

            val sleep = FRAME_TIME_NS - (System.nanoTime() - now)
            if (sleep > 1_000_000L) Thread.sleep(sleep / 1_000_000L)
        }
    }

    // ── Touch : swipe gauche/droite = changer de voie, haut ou tap = sauter ────
    //
    // Un seul geste par toucher : une fois le swipe reconnu, il faut relever
    // le doigt de l'écran pour que le geste suivant soit pris en compte.

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; gestureDone = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureDone) detectSwipe(ev.x - downX, ev.y - downY, allowTap = false)
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureDone) detectSwipe(ev.x - downX, ev.y - downY, allowTap = true)
            }
        }
        return true
    }

    private fun detectSwipe(dx: Float, dy: Float, allowTap: Boolean) {
        when {
            abs(dx) >= swipeThresholdPx && abs(dx) > abs(dy) -> {
                if (dx > 0) game.moveRight() else game.moveLeft()
                gestureDone = true
            }
            dy <= -swipeThresholdPx -> { game.jump(); gestureDone = true }
            allowTap && abs(dx) < swipeThresholdPx && abs(dy) < swipeThresholdPx -> {
                game.jump(); gestureDone = true
            }
        }
    }

    // ── Projection ─────────────────────────────────────────────────────────────
    //
    // f(z) = z / (z + Z_HALF) donne 0 au plan du joueur et tend vers 1 au loin ;
    // on normalise pour que SPAWN_Z tombe pile sur l'horizon.

    private val fMax = CosmoRunGame.SPAWN_Z / (CosmoRunGame.SPAWN_Z + Z_HALF)

    private fun depthNorm(z: Float): Float {
        val zc = z.coerceAtLeast(0f)
        return (zc / (zc + Z_HALF)) / fMax
    }

    private fun screenY(z: Float) = playerBaseY + (horizonY - playerBaseY) * depthNorm(z)

    private fun depthScale(z: Float) =
        (1f - depthNorm(z) * (1f - MIN_DEPTH_SCALE)).coerceAtLeast(MIN_DEPTH_SCALE)

    private fun laneX(laneF: Float, z: Float) =
        vw / 2f + (laneF - 1f) * laneSpacing * depthScale(z)

    // ── Rendu ──────────────────────────────────────────────────────────────────

    private fun renderFrame(canvas: Canvas) {
        drawSky(canvas)
        drawTrack(canvas)
        drawEntities(canvas)
        drawPlayer(canvas)
        drawHud(canvas)
    }

    private fun drawSky(canvas: Canvas) {
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, vh,
            intArrayOf(Color.rgb(10, 6, 26), Color.rgb(24, 10, 46), Color.rgb(6, 4, 14)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, vw, vh, skyPaint)
        // Étoiles fixes (au-dessus de l'horizon surtout)
        stars.forEach { (rx, ry, r) ->
            val y = ry * vh
            val alpha = if (y < horizonY) 200 else 70
            starPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(rx * vw, y, r, starPaint)
        }
    }

    private fun drawTrack(canvas: Canvas) {
        val halfBottom = laneSpacing * 1.5f
        val halfTop = halfBottom * MIN_DEPTH_SCALE
        val bottomY = vh
        // Le trapèze descend jusqu'en bas de l'écran (la piste passe "sous" le joueur)
        val path = Path().apply {
            moveTo(vw / 2f - halfBottom * 1.25f, bottomY)
            lineTo(vw / 2f - halfTop, horizonY)
            lineTo(vw / 2f + halfTop, horizonY)
            lineTo(vw / 2f + halfBottom * 1.25f, bottomY)
            close()
        }
        trackPaint.shader = LinearGradient(
            0f, horizonY, 0f, bottomY,
            intArrayOf(Color.rgb(30, 20, 60), Color.rgb(16, 12, 34)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, trackPaint)

        // Bords lumineux
        linePaint.color = Color.argb(150, 140, 90, 255)
        canvas.drawLine(vw / 2f - halfBottom * 1.25f, bottomY, vw / 2f - halfTop, horizonY, linePaint)
        canvas.drawLine(vw / 2f + halfBottom * 1.25f, bottomY, vw / 2f + halfTop, horizonY, linePaint)

        // Séparateurs de voies : traits pointillés qui défilent avec la distance
        linePaint.color = Color.argb(70, 160, 140, 255)
        val dashSpacing = 7f // un trait tous les 7 m
        val offset = game.distance % dashSpacing
        for (sep in intArrayOf(-1, 1)) {
            var z = dashSpacing - offset
            while (z < CosmoRunGame.SPAWN_Z) {
                val x0 = vw / 2f + sep * laneSpacing * 0.5f * depthScale(z)
                val x1 = vw / 2f + sep * laneSpacing * 0.5f * depthScale(z + 2.2f)
                canvas.drawLine(x0, screenY(z), x1, screenY(z + 2.2f), linePaint)
                z += dashSpacing
            }
        }
    }

    private fun drawEntities(canvas: Canvas) {
        // Du plus loin au plus proche pour un recouvrement correct
        val sorted = game.entities.sortedByDescending { it.z }
        for (e in sorted) {
            if (e.z < -1f || e.z > CosmoRunGame.SPAWN_Z) continue
            when (e.type) {
                CosmoRunGame.EntityType.ASTEROID -> drawAsteroid(canvas, e)
                CosmoRunGame.EntityType.BARRIER -> drawBarrier(canvas, e)
                CosmoRunGame.EntityType.ATOM -> drawAtom(canvas, e)
            }
        }
    }

    private fun drawAsteroid(canvas: Canvas, e: CosmoRunGame.Entity) {
        val s = depthScale(e.z)
        val x = laneX(e.lane.toFloat(), e.z)
        val y = screenY(e.z)
        val r = laneSpacing * 0.42f * s
        if (r < 1f) return
        // Rocher : dégradé radial gris + quelques cratères déterministes
        entityPaint.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.35f, r * 1.6f,
            intArrayOf(Color.rgb(150, 140, 135), Color.rgb(70, 62, 60)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y - r * 0.35f, r, entityPaint)
        entityPaint.shader = null
        entityPaint.color = Color.argb(110, 40, 34, 32)
        val craterSeed = e.variant
        canvas.drawCircle(x + r * 0.35f * (craterSeed - 1), y - r * (0.15f + 0.25f * craterSeed % 2), r * 0.20f, entityPaint)
        canvas.drawCircle(x - r * 0.30f, y - r * 0.55f, r * 0.13f, entityPaint)
    }

    private fun drawBarrier(canvas: Canvas, e: CosmoRunGame.Entity) {
        val s = depthScale(e.z)
        val y = screenY(e.z)
        val half = laneSpacing * 1.5f * s
        val h = (vh * 0.035f * s).coerceAtLeast(2f)
        // Barre d'énergie : halo puis cœur
        entityPaint.shader = null
        entityPaint.color = Color.argb(70, 255, 60, 120)
        canvas.drawRoundRect(RectF(vw / 2f - half, y - h * 1.8f, vw / 2f + half, y + h * 0.6f), h, h, entityPaint)
        entityPaint.color = Color.argb(230, 255, 90, 150)
        canvas.drawRoundRect(RectF(vw / 2f - half, y - h * 1.3f, vw / 2f + half, y), h * 0.6f, h * 0.6f, entityPaint)
    }

    private fun drawAtom(canvas: Canvas, e: CosmoRunGame.Entity) {
        val s = depthScale(e.z)
        val x = laneX(e.lane.toFloat(), e.z)
        // Petit flottement vertical pour donner vie
        val bob = sin((game.distance + e.z) * 0.8f) * vh * 0.006f
        val y = screenY(e.z) - laneSpacing * 0.28f * s + bob
        val size = laneSpacing * 0.5f * s
        if (size < 2f) return
        val bmp = atomBmps[e.variant % atomBmps.size]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(x - size / 2, y - size / 2, x + size / 2, y + size / 2), bmpPaint)
        } else {
            entityPaint.shader = null
            entityPaint.color = Color.rgb(90, 220, 255)
            canvas.drawCircle(x, y, size * 0.4f, entityPaint)
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val x = laneX(game.laneF, 0f)
        val jumpT = if (game.isJumping) game.jumpProgress else -1f
        val jumpLift = if (jumpT >= 0f) sin(jumpT * PI.toFloat()) * vh * JUMP_HEIGHT_RATIO else 0f
        val size = laneSpacing * 0.78f
        val y = playerBaseY - jumpLift

        // Ombre au sol (rétrécit en l'air)
        val shrink = 1f - (jumpLift / (vh * JUMP_HEIGHT_RATIO)) * 0.45f
        canvas.drawOval(
            RectF(x - size * 0.32f * shrink, playerBaseY + size * 0.38f,
                  x + size * 0.32f * shrink, playerBaseY + size * 0.50f),
            shadowPaint
        )

        val bmp = astronautBmp
        if (bmp != null) {
            val ar = bmp.width.toFloat() / bmp.height
            val h = size; val w = h * ar
            canvas.drawBitmap(bmp, null, RectF(x - w / 2, y - h * 0.55f, x + w / 2, y + h * 0.45f), bmpPaint)
        } else {
            entityPaint.shader = null
            entityPaint.color = Color.WHITE
            canvas.drawCircle(x, y, size * 0.3f, entityPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        if (!game.isRunning && !game.isGameOver) return
        val pad = vw * 0.04f
        hudPaint.textAlign = Paint.Align.LEFT
        hudPaint.color = Color.WHITE
        canvas.drawText("${game.score}", pad, horizonY * 0.35f, hudPaint)
        hudPaint.color = Color.argb(170, 150, 220, 255)
        canvas.drawText("⚛ ${game.atomsCollected}", pad, horizonY * 0.35f + hudPaint.textSize * 1.25f, hudPaint)
        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawText("${game.distance.toInt()} m", vw - pad, horizonY * 0.35f, hudPaint)
    }
}
