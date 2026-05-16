package com.Atom2Universe.app.games.flappycat

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random
import androidx.core.content.edit

class FlappyCatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // ── Virtual canvas ──────────────────────────────────────────────────────
    private val VW = 420f
    private val VH = 560f

    // ── Sprite / layout constants ───────────────────────────────────────────
    private val CAT_W = 51f
    private val CAT_H = 34f
    private val GROUND_H = 60f          // zone sol en bas
    private val CEIL_H = 40f            // zone plafond en haut (game over identique au sol)
    private val BIRD_SRC = 128
    private val BIRD_SIZE = 72f

    // Bird animation: 2 sets × 2 frames dans une sheet 256×256 (grille 2×2 de 128×128)
    // set 0 = colonne gauche (sx=0), set 1 = colonne droite (sx=128); frameIdx = ligne

    // ── Physics ─────────────────────────────────────────────────────────────
    private val GRAVITY = 2400f
    private val JUMP_IMPULSE = 760f
    private val MAX_FALL = 1800f
    private val BG_SCROLL = 110f

    // ── Limites de jeu ───────────────────────────────────────────────────────
    private val ceilY  get() = CEIL_H                  // y du bord bas du plafond
    private val floorY get() = VH - GROUND_H           // y du bord haut du sol

    // ── État ─────────────────────────────────────────────────────────────────
    private enum class Phase { READY, RUNNING, PAUSED, GAME_OVER }
    private var phase = Phase.READY

    private var catX = VW * 0.25f
    private var catY = VH * 0.5f
    private var catVY = 0f
    private var catFrame = 0
    private var catFrameTimer = 0f
    private var catTilt = 0f

    private data class Bird(
        var x: Float, var y: Float, val vx: Float,
        val animSet: Int,
        var frameIdx: Int = 0,
        var animTimer: Float = 0f,
        val wobblePhase: Float = 0f,
        var passed: Boolean = false
    )

    private val birds = mutableListOf<Bird>()

    private var score = 0
    private var bestScore = 0
    private var newBest = false
    private var elapsed = 0f
    private var bgOffset = 0f
    private var bgWidth = VW
    private var nextBird = 1.5f

    // ── Bitmaps ──────────────────────────────────────────────────────────────
    private val catFrames = arrayOfNulls<Bitmap>(2)
    private var birdBitmap: Bitmap? = null
    private var bgBitmap: Bitmap? = null

    // ── Scaling (letterbox) ───────────────────────────────────────────────────
    private var scaleX = 1f
    private var scaleY = 1f
    private var offX = 0f
    private var offY = 0f

    // ── Thread ───────────────────────────────────────────────────────────────
    private var gameThread: Thread? = null
    @Volatile private var running = false

    // ── Paints ───────────────────────────────────────────────────────────────
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val overlayBg = Paint().apply { color = Color.parseColor("#BB0a0e1a") }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.parseColor("#99000000"))
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#99000000"))
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 2f, 2f, Color.parseColor("#AA000000"))
    }
    private val borderPaint = Paint().apply { color = Color.parseColor("#1a1c28") }
    private val borderLinePaint = Paint().apply {
        color = Color.parseColor("#2a2d3e")
        strokeWidth = 2f
    }

    // ── Prefs ────────────────────────────────────────────────────────────────
    private val prefs by lazy { context.getSharedPreferences("flappy_cat_save", Context.MODE_PRIVATE) }

    // ── Init ─────────────────────────────────────────────────────────────────
    init {
        holder.addCallback(this)
        isFocusable = true
        loadAssets()
        loadBestScore()
    }

    private fun loadAssets() {
        loadBitmap("Assets/sprites/Chat.png") { sheet ->
            val fw = sheet.width / 2
            catFrames[0] = Bitmap.createBitmap(sheet, 0, 0, fw, sheet.height)
            catFrames[1] = Bitmap.createBitmap(sheet, fw, 0, fw, sheet.height)
            sheet.recycle()
        }
        loadBitmap("Assets/sprites/Bird.png") { birdBitmap = it }
        loadBitmap("Assets/sprites/FondChat.png") { bmp ->
            bgBitmap = bmp
            bgWidth = (VH / bmp.height.toFloat()) * bmp.width.toFloat()
        }
    }

    private inline fun loadBitmap(path: String, block: (Bitmap) -> Unit) {
        try { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.let(block) } }
        catch (_: Exception) {}
    }

    private fun loadBestScore()  { bestScore = prefs.getInt("best_score", 0) }
    private fun saveBestScore()  { prefs.edit { putInt("best_score", bestScore) } }

    // ── Surface callbacks ─────────────────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        gameThread = Thread(this, "FlappyCatThread").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        recomputeScale(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        joinThread()
    }

    private fun recomputeScale(w: Int, h: Int) {
        val screenAR = w.toFloat() / h
        val gameAR   = VW / VH
        if (screenAR > gameAR) {
            scaleY = h / VH; scaleX = scaleY
            offX = (w - VW * scaleX) / 2f; offY = 0f
        } else {
            scaleX = w / VW; scaleY = scaleX
            offX = 0f; offY = (h - VH * scaleY) / 2f
        }
    }

    private fun joinThread() {
        try { gameThread?.join(500) } catch (_: InterruptedException) {}
        gameThread = null
    }

    // ── Contrôles publics ─────────────────────────────────────────────────────
    fun togglePause() {
        phase = when (phase) {
            Phase.RUNNING -> Phase.PAUSED
            Phase.PAUSED  -> Phase.RUNNING
            else          -> phase
        }
    }

    fun pause() {
        if (phase == Phase.RUNNING) phase = Phase.PAUSED
        running = false
        joinThread()
    }

    fun resume() {
        running = true
        if (gameThread?.isAlive != true) {
            gameThread = Thread(this, "FlappyCatThread").apply { start() }
        }
    }

    // ── Boucle principale ─────────────────────────────────────────────────────
    override fun run() {
        var lastNano = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNano = now

            if (phase == Phase.RUNNING) update(dt)

            val c = holder.lockCanvas() ?: continue
            try { drawFrame(c) } finally { holder.unlockCanvasAndPost(c) }

            val remainMs = 16L - (System.nanoTime() - now) / 1_000_000L
            if (remainMs > 0) Thread.sleep(remainMs)
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    private fun update(dt: Float) {
        // Physique du chat
        catVY = (catVY + GRAVITY * dt).coerceAtMost(MAX_FALL)
        catY += catVY * dt

        // Inclinaison fluide
        val targetTilt = (catVY * 0.032f).coerceIn(-28f, 70f)
        catTilt += (targetTilt - catTilt) * (dt * 8f)

        // Animation
        catFrameTimer += dt
        if (catFrameTimer > 0.18f) { catFrame = catFrame xor 1; catFrameTimer = 0f }

        // Collision sol (game over)
        if (catY >= floorY - CAT_H / 2f) {
            catY = floorY - CAT_H / 2f
            triggerGameOver(); return
        }
        // Collision plafond (game over — symétrique au sol)
        if (catY <= ceilY + CAT_H / 2f) {
            catY = ceilY + CAT_H / 2f
            triggerGameOver(); return
        }

        // Spawn oiseaux
        nextBird -= dt
        if (nextBird <= 0f) spawnBird()

        // Déplacement et scoring oiseaux
        birds.removeAll { bird ->
            bird.x += bird.vx * dt
            val wobble = sin(bird.wobblePhase + elapsed * 2f).toFloat() * 26f
            bird.y = (bird.y + wobble * dt).coerceIn(ceilY + BIRD_SIZE / 2f, floorY - BIRD_SIZE / 2f)
            bird.animTimer += dt
            if (bird.animTimer >= 0.5f) { bird.frameIdx = bird.frameIdx xor 1; bird.animTimer = 0f }
            if (!bird.passed && bird.x + BIRD_SIZE / 2f < catX) { bird.passed = true; score++ }
            bird.x + BIRD_SIZE < -80f
        }

        bgOffset = (bgOffset + BG_SCROLL * dt) % bgWidth
        elapsed += dt

        checkCollisions()
    }

    private fun spawnBird() {
        // Toute la hauteur jouable (entre plafond et sol), sans restriction de zone
        val margin = BIRD_SIZE / 2f + 6f
        val minY = ceilY + margin
        val maxY = floorY - margin
        val y = (Random.nextFloat() * (maxY - minY) + minY).coerceIn(minY, maxY)
        val speed = Random.nextFloat() * 80f + 150f
        birds += Bird(
            x = VW + BIRD_SIZE,
            y = y,
            vx = -speed,
            animSet = Random.nextInt(2),
            wobblePhase = Random.nextFloat() * (2f * PI.toFloat())
        )
        nextBird = nextBirdInterval()
    }

    // t=0 : 1.5–2.5 s  |  t=60 s : 0.6–1.1 s  |  t=120 s+ : 0.4–0.7 s
    private fun nextBirdInterval(): Float {
        val factor = (1.0f - elapsed / 60f).coerceAtLeast(0.15f)
        val minI = 0.3f + factor * 1.2f
        val maxI = 0.6f + factor * 1.9f
        return minI + Random.nextFloat() * (maxI - minI)
    }

    // ── Collisions ────────────────────────────────────────────────────────────
    private fun checkCollisions() {
        val cx = catX - CAT_W * 0.35f
        val cy = catY - CAT_H * 0.35f
        val cw = CAT_W * 0.7f
        val ch = CAT_H * 0.7f
        val bHit = BIRD_SIZE * 0.7f
        for (bird in birds) {
            if (rectOverlap(cx, cy, cw, ch, bird.x - bHit / 2f, bird.y - bHit / 2f, bHit, bHit)) {
                triggerGameOver(); return
            }
        }
    }

    private fun rectOverlap(ax: Float, ay: Float, aw: Float, ah: Float,
                             bx: Float, by: Float, bw: Float, bh: Float) =
        ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by

    private fun triggerGameOver() {
        if (score > bestScore) { bestScore = score; newBest = true; saveBestScore() }
        phase = Phase.GAME_OVER
    }

    private fun resetGame() {
        catX = VW * 0.25f; catY = VH * 0.5f
        catVY = 0f; catFrame = 0; catFrameTimer = 0f; catTilt = 0f
        birds.clear()
        score = 0; elapsed = 0f; bgOffset = 0f; newBest = false
        nextBird = 1.5f
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        when (phase) {
            Phase.READY     -> { resetGame(); phase = Phase.RUNNING; catVY = -JUMP_IMPULSE }
            Phase.RUNNING   -> catVY = -JUMP_IMPULSE
            Phase.PAUSED    -> phase = Phase.RUNNING
            Phase.GAME_OVER -> { resetGame(); phase = Phase.READY }
        }
        return true
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────
    private fun drawFrame(canvas: Canvas) {
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scaleX, scaleY)

        drawBackground(canvas)
        drawBirds(canvas)
        drawCeiling(canvas)
        drawGround(canvas)
        drawCat(canvas)
        drawHud(canvas)

        when (phase) {
            Phase.READY     -> drawOverlay(canvas,
                context.getString(R.string.flappy_cat_title),
                context.getString(R.string.flappy_cat_tap_to_start), false)
            Phase.GAME_OVER -> drawOverlay(canvas,
                context.getString(R.string.flappy_cat_game_over),
                buildGameOverMsg(), newBest)
            Phase.PAUSED    -> drawOverlay(canvas,
                context.getString(R.string.flappy_cat_paused),
                context.getString(R.string.flappy_cat_tap_to_resume), false)
            else -> {}
        }
        canvas.restore()
    }

    private fun buildGameOverMsg(): String {
        val retry = context.getString(R.string.flappy_cat_tap_to_retry)
        return if (newBest) "★ ${context.getString(R.string.flappy_cat_new_best)} ★\n$retry"
        else "${context.getString(R.string.flappy_cat_score_label)}: $score\n$retry"
    }

    private fun drawBackground(canvas: Canvas) {
        val bg = bgBitmap
        if (bg == null) { canvas.drawColor(Color.parseColor("#0b0f1a")); return }
        val src = Rect(0, 0, bg.width, bg.height)
        var x = -(bgOffset % bgWidth)
        while (x < VW + bgWidth) {
            canvas.drawBitmap(bg, src, RectF(x, 0f, x + bgWidth, VH), spritePaint)
            x += bgWidth
        }
    }

    private fun drawGround(canvas: Canvas) {
        canvas.drawRect(0f, floorY, VW, VH, borderPaint)
        canvas.drawLine(0f, floorY, VW, floorY, borderLinePaint)
    }

    private fun drawCeiling(canvas: Canvas) {
        canvas.drawRect(0f, 0f, VW, ceilY, borderPaint)
        canvas.drawLine(0f, ceilY, VW, ceilY, borderLinePaint)
    }

    private fun drawBirds(canvas: Canvas) {
        val bmp = birdBitmap
        for (bird in birds) {
            if (bmp == null) {
                spritePaint.color = Color.parseColor("#ff6b6b")
                canvas.drawCircle(bird.x, bird.y, BIRD_SIZE * 0.35f, spritePaint)
                spritePaint.color = Color.WHITE
            } else {
                val sx = bird.animSet * BIRD_SRC
                val sy = bird.frameIdx * BIRD_SRC
                val src = Rect(sx, sy, sx + BIRD_SRC, sy + BIRD_SRC)
                val half = BIRD_SIZE * 0.5f
                canvas.drawBitmap(bmp, src,
                    RectF(bird.x - half, bird.y - half, bird.x + half, bird.y + half), spritePaint)
            }
        }
    }

    private fun drawCat(canvas: Canvas) {
        val frame = catFrames[catFrame] ?: catFrames[0]
        canvas.save()
        canvas.rotate(catTilt, catX, catY)
        if (frame == null) {
            spritePaint.color = Color.parseColor("#ffd166")
            canvas.drawRect(catX - CAT_W / 2f, catY - CAT_H / 2f,
                            catX + CAT_W / 2f, catY + CAT_H / 2f, spritePaint)
            spritePaint.color = Color.WHITE
        } else {
            canvas.drawBitmap(frame, null,
                RectF(catX - CAT_W / 2f, catY - CAT_H / 2f,
                      catX + CAT_W / 2f, catY + CAT_H / 2f), spritePaint)
        }
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas) {
        scorePaint.textSize = 22f
        canvas.drawText("$score", 16f, ceilY + 30f, scorePaint)
        scorePaint.textSize = 13f
        canvas.drawText("${context.getString(R.string.flappy_cat_best_label)}: $bestScore",
            16f, ceilY + 46f, scorePaint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, message: String, highlight: Boolean) {
        canvas.drawRect(0f, 0f, VW, VH, overlayBg)
        titlePaint.textSize = 38f
        titlePaint.color = if (highlight) Color.parseColor("#FFD700") else Color.WHITE
        canvas.drawText(title, VW / 2f, VH * 0.40f, titlePaint)
        titlePaint.color = Color.WHITE
        bodyPaint.textSize = 17f
        var y = VH * 0.51f
        for (line in message.split("\n")) { canvas.drawText(line, VW / 2f, y, bodyPaint); y += 26f }
    }
}
