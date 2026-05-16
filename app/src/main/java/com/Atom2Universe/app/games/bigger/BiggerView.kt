package com.Atom2Universe.app.games.bigger

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class BiggerView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    val game = BiggerGame()

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var gameInitialized = false

    @Volatile private var indicatorX = 0f
    @Volatile private var indicatorVisible = false

    private val dp = resources.displayMetrics.density

    // Couleurs par tier (1 → 1024)
    private val TIER_FILL = intArrayOf(
        Color.parseColor("#DFF5E0"), // 1   vert pâle
        Color.parseColor("#66BB6A"), // 2   vert
        Color.parseColor("#FFEE58"), // 4   jaune
        Color.parseColor("#FFA726"), // 8   orange
        Color.parseColor("#FF7043"), // 16  rouge-orangé
        Color.parseColor("#EF5350"), // 32  rouge
        Color.parseColor("#EC407A"), // 64  rose
        Color.parseColor("#AB47BC"), // 128 violet
        Color.parseColor("#5C6BC0"), // 256 indigo
        Color.parseColor("#29B6F6"), // 512 bleu clair
        Color.parseColor("#FFD700"), // 1024 or
    )
    private val TIER_TEXT = intArrayOf(
        Color.parseColor("#1B5E20"),
        Color.parseColor("#1B5E20"),
        Color.parseColor("#4E342E"),
        Color.parseColor("#BF360C"),
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
        Color.parseColor("#0D47A1"),
        Color.parseColor("#4E342E"),
    )

    // Paints — alloués une seule fois
    private val pBall = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
        color = Color.argb(80, 255, 255, 255)
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f
        color = Color.argb(130, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
    }
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val pDangerFill = Paint().apply { color = Color.argb(25, 255, 50, 50) }
    private val pDangerLine = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(120, 255, 60, 60)
    }
    private val pHudBg = Paint().apply { color = Color.argb(210, 12, 12, 22) }
    private val pHudLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 10f * dp; color = Color.parseColor("#777777")
    }
    private val pHudVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pHudStats = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT; textSize = 9f * dp; color = Color.parseColor("#555566")
    }
    private val pOverBg = Paint().apply { color = Color.argb(210, 8, 8, 18) }
    private val pOverTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 26f * dp
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pOverMsg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 13f * dp; color = Color.parseColor("#AAAAAA")
    }
    private val pOverStats = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * dp; color = Color.parseColor("#666677")
    }
    private val pBtn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252535") }
    private val pBtnBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#4455AA")
    }
    private val pBtnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 15f * dp; color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private val HUD_H = 80f * dp
    private val btnRect = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true; lastNanos = System.nanoTime()
        thread = Thread(this, "BiggerPhysics").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        val cz = w / 10f
        synchronized(game) {
            game.resize(cz, w.toFloat(), h - HUD_H)
            if (!gameInitialized) { game.reset(); gameInitialized = true }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false; thread?.join(2000); thread = null
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = now
            synchronized(game) { game.step(dt) }
            val canvas = holder.lockCanvas() ?: continue
            try { synchronized(game) { drawFrame(canvas) } }
            finally { holder.unlockCanvasAndPost(canvas) }
            Thread.sleep(5)
        }
    }

    fun pause()  { running = false; thread?.join(1500); thread = null }
    fun resume() {
        if (!running) {
            running = true; lastNanos = System.nanoTime()
            thread = Thread(this, "BiggerPhysics").also { it.start() }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ratio = (event.x / width).coerceIn(0f, 1f)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (game.isGameOver) return true
                indicatorX = event.x; indicatorVisible = true
            }
            MotionEvent.ACTION_UP -> {
                if (game.isGameOver) {
                    if (btnRect.contains(event.x, event.y)) synchronized(game) { game.reset() }
                } else {
                    synchronized(game) { game.drop(ratio) }
                }
                indicatorVisible = false
            }
            MotionEvent.ACTION_CANCEL -> indicatorVisible = false
        }
        return true
    }

    // ─── Rendu ────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#0C0C14"))

        val bw = game.boardWidth
        val bh = game.boardHeight

        // Zone de danger (haut)
        canvas.drawRect(0f, 0f, bw, 56f * dp, pDangerFill)
        canvas.drawLine(0f, 0f, bw, 0f, pDangerLine)

        // Indicateur de drop
        if (indicatorVisible && !game.isGameOver) {
            val cv = game.current
            val r = if (VALUE_ORDER.contains(cv)) game.radius(cv) else 0f
            val cx = indicatorX.coerceIn(r, bw - r)
            canvas.drawLine(cx, 0f, cx, bh, pLine)
            if (r > 0f) {
                val tier = getTier(cv)
                val col = TIER_FILL.getOrElse(tier) { Color.WHITE }
                pGhost.color = Color.argb(100, Color.red(col), Color.green(col), Color.blue(col))
                canvas.drawCircle(cx, r + 8f, r, pGhost)
            }
        }

        // Billes
        for (b in game.balls) {
            val tier = getTier(b.value)
            pBall.color = TIER_FILL.getOrElse(tier) { Color.WHITE }
            canvas.drawCircle(b.x, b.y, b.radius, pBall)
            canvas.drawCircle(b.x, b.y, b.radius, pRing)
            val ts = (b.radius * 0.58f).coerceIn(9f, 72f)
            pText.textSize = ts
            pText.color = TIER_TEXT.getOrElse(tier) { Color.WHITE }
            canvas.drawText(b.value.toString(), b.x, b.y + ts * 0.37f, pText)
        }

        drawHud(canvas, bw, bh)
        if (game.isGameOver) drawOverlay(canvas)
    }

    private fun drawHud(canvas: Canvas, bw: Float, bh: Float) {
        val hudTop = bh
        canvas.drawRect(0f, hudTop, bw, hudTop + HUD_H, pHudBg)

        val midY  = hudTop + HUD_H * 0.5f
        val labelY = hudTop + 14f * dp
        val valY   = hudTop + HUD_H * 0.78f

        // Bille courante
        val cv = game.current
        canvas.drawText("Bille", bw * 0.13f, labelY, pHudLabel)
        if (VALUE_ORDER.contains(cv)) {
            val tier = getTier(cv)
            pHudVal.textSize = 22f * dp
            pHudVal.color = TIER_FILL.getOrElse(tier) { Color.WHITE }
            canvas.drawText(cv.toString(), bw * 0.13f, valY, pHudVal)
        }

        // Séparateur vertical
        val sepX = bw * 0.27f
        canvas.drawLine(sepX, hudTop + 10f * dp, sepX, hudTop + HUD_H - 10f * dp, pDangerLine)

        // File d'attente
        val qCount = game.queue.size.coerceAtMost(QUEUE_LENGTH)
        val slotW = (bw * 0.58f) / qCount.coerceAtLeast(1)
        for (i in 0 until qCount) {
            val qv = game.queue.getOrElse(i) { 0 }
            val qx = bw * 0.29f + slotW * (i + 0.5f)
            canvas.drawText(if (i == 0) "Suivant" else "•••".take(i), qx, labelY, pHudLabel)
            if (VALUE_ORDER.contains(qv)) {
                val tier = getTier(qv)
                val ts2 = (22f - i * 3.5f).coerceAtLeast(11f) * dp
                pHudVal.textSize = ts2
                pHudVal.color = TIER_FILL.getOrElse(tier) { Color.WHITE }
                canvas.drawText(qv.toString(), qx, valY, pHudVal)
            }
        }

        // Stats (droite)
        canvas.drawText("T:${game.stats.turns}  F:${game.stats.merges}", bw - 8f * dp, midY + 5f * dp, pHudStats)
        if (game.stats.largest > 0) {
            canvas.drawText("Max:${game.stats.largest}", bw - 8f * dp, midY - 8f * dp, pHudStats)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, pOverBg)
        val cx = w / 2f; val cy = h * 0.38f

        val victory = game.result == GameResult.VICTORY
        pOverTitle.color = if (victory) Color.parseColor("#FFD700") else Color.parseColor("#FF5555")
        canvas.drawText(if (victory) "Objectif atteint !" else "Bac saturé", cx, cy, pOverTitle)

        canvas.drawText(
            if (victory) "La bille 1024 forgée en ${game.stats.turns} lancers !"
            else "Le bac est plein. Réessayez !",
            cx, cy + 32f * dp, pOverMsg
        )
        canvas.drawText(
            "Lancers : ${game.stats.turns}   Fusions : ${game.stats.merges}   Max : ${game.stats.largest}",
            cx, cy + 54f * dp, pOverStats
        )

        // Bouton Rejouer
        val bw = 160f * dp; val bh2 = 44f * dp
        btnRect.set(cx - bw / 2f, cy + 80f * dp, cx + bw / 2f, cy + 80f * dp + bh2)
        canvas.drawRoundRect(btnRect, 10f * dp, 10f * dp, pBtn)
        canvas.drawRoundRect(btnRect, 10f * dp, 10f * dp, pBtnBorder)
        canvas.drawText("Rejouer", cx, btnRect.top + bh2 * 0.65f, pBtnText)
    }
}
