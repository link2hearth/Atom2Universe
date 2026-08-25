package com.Atom2Universe.app.games.nuclea

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NucleaView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    val game = NucleaGame(ctx)

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L

    private var _dp = 1f
    private var _sp = 1f

    // Couleurs pré-parsées — Color.parseColor() interdit dans la boucle 60fps
    private val C_BG        = Color.parseColor("#07070F")
    private val C_NEB1      = Color.argb(26, 110, 70, 200)
    private val C_NEB2      = Color.argb(20, 40, 120, 200)
    private val C_NEB3      = Color.argb(16, 200, 80, 140)
    private val C_GRAY      = Color.parseColor("#AAAAB4")
    private val C_CARD_BG   = Color.parseColor("#151527")
    private val C_BTN_BG    = Color.parseColor("#232338")
    private val C_BTN_BORDER= Color.parseColor("#4A4A66")
    private val C_GAMEOVER  = Color.parseColor("#FF5E5E")
    private val C_GOLD      = Color.parseColor("#FFE066")
    private val C_DUST      = Color.parseColor("#CBB8FF")
    private val C_HP_BG     = Color.parseColor("#1A0000")
    private val C_HP_MOTE   = Color.parseColor("#8BE87C")
    private val C_FLUX      = Color.argb(38, 160, 220, 255)
    private val C_FLUX_CORE = Color.argb(70, 210, 240, 255)
    private val C_FLUX_MAG  = Color.argb(44, 80, 190, 255)
    private val C_FLUX_GAM  = Color.argb(50, 255, 235, 150)
    private val C_BH_RING1  = Color.parseColor("#FF8A3C")
    private val C_BH_RING2  = Color.parseColor("#9A5CFF")
    private val C_GREEN     = Color.parseColor("#7EF9C8")
    private val C_STAR_DIM  = Color.parseColor("#3A3A52")

    // Corps des atomes : version assombrie de la couleur de tier
    private val tierBody = IntArray(NucleaGame.TIER_COUNT) { darken(NucleaGame.TIER_COLORS[it], 0.55f) }
    private fun darken(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt()
    )

    // ─── Sticks (écrits par le thread touch, lus par le thread de jeu) ───────

    @Volatile private var jmx = 0f
    @Volatile private var jmy = 0f
    @Volatile private var jax = 0f
    @Volatile private var jay = 0f
    private var moveId = -1
    private var aimId = -1
    private var moveCx = 0f; private var moveCy = 0f; private var moveKx = 0f; private var moveKy = 0f
    private var aimCx = 0f; private var aimCy = 0f; private var aimKx = 0f; private var aimKy = 0f
    private val JOY_OUTER_R get() = min(width, height) * 0.13f
    private val JOY_INNER_R get() = JOY_OUTER_R * 0.42f
    private val JOY_MAX get() = JOY_OUTER_R * 0.75f

    // Actions demandées depuis le thread UI, exécutées dans le thread de jeu
    @Volatile private var pendingStart = false
    @Volatile private var pendingPhase: NucleaPhase? = null
    @Volatile private var pendingBuyNode = -1

    // Sticks d'une manette physique (type Xbox) — prioritaires quand le tactile est relâché
    @Volatile private var padMx = 0f
    @Volatile private var padMy = 0f
    @Volatile private var padAx = 0f
    @Volatile private var padAy = 0f

    // ─── Strings mises en cache ──────────────────────────────────────────────

    private val sTitle        by lazy { ctx.getString(R.string.nuclea_title) }
    private val sTagline      by lazy { ctx.getString(R.string.nuclea_tagline) }
    private val sTapStart     by lazy { ctx.getString(R.string.nuclea_tap_to_start) }
    private val sConstBtn     by lazy { ctx.getString(R.string.nuclea_constellations) }
    private val sBack         by lazy { ctx.getString(R.string.nuclea_back) }
    private val sPaused       by lazy { ctx.getString(R.string.nuclea_paused) }
    private val sResume       by lazy { ctx.getString(R.string.nuclea_resume) }
    private val sQuit         by lazy { ctx.getString(R.string.nuclea_quit) }
    private val sMenu         by lazy { ctx.getString(R.string.nuclea_menu) }
    private val sGameOver     by lazy { ctx.getString(R.string.nuclea_game_over) }
    private val sBlackHole    by lazy { ctx.getString(R.string.nuclea_blackhole) }
    private val sBlackHoleHint by lazy { ctx.getString(R.string.nuclea_blackhole_hint) }

    // ─── Paints ──────────────────────────────────────────────────────────────

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val pTextL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.LEFT }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val fluxPath = Path()
    private val starPath = Path()

    // Rects de boutons
    private val btnRect1 = RectF()
    private val btnRect2 = RectF()
    private val constBtnRect = RectF()
    private val backBtnRect = RectF()
    private val nodeRects = Array(NucleaGame.META_NODES.size) { RectF() }

    // Étoiles de fond (positions fixes + scintillement)
    private val bgStars by lazy {
        val rng = Random(7)
        Array(70) { Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 2f * PI.toFloat()) }
    }
    // Dispersion angulaire fixe des photons du cône (pas d'allocation en boucle de rendu)
    private val fluxJitter = FloatArray(12) { Random(it).nextFloat() - 0.5f }
    private var animT = 0f

    init { holder.addCallback(this) }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {
        _dp = resources.displayMetrics.density
        _sp = resources.displayMetrics.density * resources.configuration.fontScale
        start()
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        game.onScreenSize(w.toFloat(), h2.toFloat())
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stop() }

    fun pause() { running = false; try { thread?.join(300) } catch (_: Exception) {} }
    fun resume() { if (!running) start() }

    private fun start() {
        val old = thread
        running = true
        lastNanos = System.nanoTime()
        thread = Thread(this, "NucleaGame").also { it.start() }
        old?.interrupt()
    }

    private fun stop() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
    }

    // ─── Boucle de jeu ────────────────────────────────────────────────────────

    override fun run() {
        val me = Thread.currentThread()
        while (running && me === thread) {
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = now
            animT += dt

            if (pendingStart) { pendingStart = false; game.startGame() }
            pendingPhase?.let { ph -> pendingPhase = null; game.phase = ph }
            val node = pendingBuyNode
            if (node >= 0) {
                pendingBuyNode = -1
                NucleaGame.META_NODES.getOrNull(node)?.let { game.tryBuyNode(it) }
            }

            // Tactile prioritaire quand un doigt tient le stick, sinon la manette
            val mx = if (moveId >= 0) jmx else padMx
            val my = if (moveId >= 0) jmy else padMy
            val ax = if (aimId >= 0) jax else padAx
            val ay = if (aimId >= 0) jay else padAy
            game.update(dt, mx, my, ax, ay)

            if (game.phase != NucleaPhase.PLAYING) {
                jmx = 0f; jmy = 0f; jax = 0f; jay = 0f
                moveId = -1; aimId = -1
            }

            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try { render(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
        }
    }

    fun requestMenu() { pendingPhase = NucleaPhase.MENU }
    fun requestPause() { if (game.phase == NucleaPhase.PLAYING) pendingPhase = NucleaPhase.PAUSED }

    // ─── Manette ──────────────────────────────────────────────────────────────

    /** Valeurs des deux sticks (déjà filtrées par la zone morte côté activity). */
    fun onPadSticks(mx: Float, my: Float, ax: Float, ay: Float) {
        padMx = mx; padMy = my; padAx = ax; padAy = ay
    }

    /** Bouton A ou START : valider / pause / reprendre selon la phase. */
    fun onPadConfirm(isStart: Boolean) {
        when (game.phase) {
            NucleaPhase.MENU -> pendingStart = true
            NucleaPhase.PLAYING -> if (isStart) pendingPhase = NucleaPhase.PAUSED
            NucleaPhase.PAUSED -> pendingPhase = NucleaPhase.PLAYING
            NucleaPhase.GAME_OVER -> pendingPhase = NucleaPhase.MENU
            else -> Unit
        }
    }

    /** Bouton Y : ouvrir les constellations depuis le menu. */
    fun onPadY() {
        if (game.phase == NucleaPhase.MENU) pendingPhase = NucleaPhase.CONSTELLATION
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (game.phase) {
            NucleaPhase.MENU -> handleMenuTouch(ev)
            NucleaPhase.CONSTELLATION -> handleConstellationTouch(ev)
            NucleaPhase.PLAYING -> handlePlayTouch(ev)
            NucleaPhase.PAUSED -> handlePausedTouch(ev)
            NucleaPhase.GAME_OVER -> handleGameOverTouch(ev)
        }
        return true
    }

    private fun handleMenuTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        if (constBtnRect.contains(ev.x, ev.y)) pendingPhase = NucleaPhase.CONSTELLATION
        else pendingStart = true
    }

    private fun handleConstellationTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        if (backBtnRect.contains(ev.x, ev.y)) { pendingPhase = NucleaPhase.MENU; return }
        nodeRects.forEachIndexed { i, r ->
            if (!r.isEmpty && r.contains(ev.x, ev.y)) pendingBuyNode = i
        }
    }

    private fun handlePlayTouch(ev: MotionEvent) {
        val pi = ev.actionIndex
        val pid = ev.getPointerId(pi)
        val px = ev.getX(pi); val py = ev.getY(pi)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (px < width / 2f && moveId < 0) {
                    moveId = pid
                    moveCx = px; moveCy = py; moveKx = px; moveKy = py
                    jmx = 0f; jmy = 0f
                } else if (px >= width / 2f && aimId < 0) {
                    aimId = pid
                    aimCx = px; aimCy = py; aimKx = px; aimKy = py
                    jax = 0f; jay = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    if (id == moveId) {
                        val dx = ev.getX(i) - moveCx; val dy = ev.getY(i) - moveCy
                        val dist = sqrt(dx * dx + dy * dy)
                        val clamped = dist.coerceAtMost(JOY_MAX)
                        val nx = if (dist > 0) dx / dist else 0f
                        val ny = if (dist > 0) dy / dist else 0f
                        moveKx = moveCx + nx * clamped; moveKy = moveCy + ny * clamped
                        jmx = nx * (clamped / JOY_MAX); jmy = ny * (clamped / JOY_MAX)
                    }
                    if (id == aimId) {
                        val dx = ev.getX(i) - aimCx; val dy = ev.getY(i) - aimCy
                        val dist = sqrt(dx * dx + dy * dy)
                        val clamped = dist.coerceAtMost(JOY_MAX)
                        val nx = if (dist > 0) dx / dist else 0f
                        val ny = if (dist > 0) dy / dist else 0f
                        aimKx = aimCx + nx * clamped; aimKy = aimCy + ny * clamped
                        jax = nx * (clamped / JOY_MAX); jay = ny * (clamped / JOY_MAX)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pid == moveId) { moveId = -1; jmx = 0f; jmy = 0f }
                if (pid == aimId) { aimId = -1; jax = 0f; jay = 0f }
            }
        }
    }

    private fun handlePausedTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        if (btnRect1.contains(ev.x, ev.y)) pendingPhase = NucleaPhase.PLAYING
        if (btnRect2.contains(ev.x, ev.y)) pendingPhase = NucleaPhase.MENU
    }

    private fun handleGameOverTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        if (btnRect1.contains(ev.x, ev.y)) pendingPhase = NucleaPhase.MENU
    }

    // ─── Rendu ────────────────────────────────────────────────────────────────

    private fun render(canvas: Canvas) {
        canvas.drawColor(C_BG)
        drawBackground(canvas)

        when (game.phase) {
            NucleaPhase.MENU -> drawMenu(canvas)
            NucleaPhase.CONSTELLATION -> drawConstellations(canvas)
            NucleaPhase.PLAYING, NucleaPhase.PAUSED -> {
                canvas.save()
                if (game.shakeTimer > 0f) {
                    val a = game.shakeTimer * 14f
                    canvas.translate((Random.nextFloat() - 0.5f) * a, (Random.nextFloat() - 0.5f) * a)
                }
                drawWorld(canvas)
                canvas.restore()
                drawHUD(canvas)
                drawJoysticks(canvas)
                drawBanners(canvas)
                if (game.superFlash > 0f) {
                    pFill.color = Color.argb((game.superFlash / 0.9f * 200).toInt().coerceIn(0, 200), 255, 245, 220)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
                }
                if (game.phase == NucleaPhase.PAUSED) drawPaused(canvas)
            }
            NucleaPhase.GAME_OVER -> {
                drawWorld(canvas)
                drawGameOver(canvas)
            }
        }
    }

    // ─── Fond nébuleuse ───────────────────────────────────────────────────────

    private fun drawBackground(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // Nappes de nébuleuse
        pFill.color = C_NEB1
        canvas.drawCircle(w * 0.25f, h * 0.28f, w * 0.45f, pFill)
        pFill.color = C_NEB2
        canvas.drawCircle(w * 0.8f, h * 0.6f, w * 0.5f, pFill)
        pFill.color = C_NEB3
        canvas.drawCircle(w * 0.4f, h * 0.85f, w * 0.4f, pFill)
        // Étoiles scintillantes
        for ((fx, fy, ph) in bgStars) {
            val tw = (sin(animT * 1.4f + ph) * 0.5f + 0.5f)
            pFill.color = Color.argb((60 + tw * 120).toInt(), 255, 255, 255)
            canvas.drawCircle(fx * w, fy * h, 1f + tw, pFill)
        }
    }

    // ─── Monde de jeu ─────────────────────────────────────────────────────────

    private fun drawWorld(canvas: Canvas) {
        drawBlackHole(canvas)
        drawFlux(canvas)
        drawShocks(canvas)
        drawMarks(canvas)
        drawMotes(canvas)
        drawPowerUps(canvas)
        drawAtoms(canvas)
        drawBooms(canvas)
        drawParticles(canvas)
        drawPlayer(canvas)
        drawFloats(canvas)
    }

    private fun drawBlackHole(canvas: Canvas) {
        val bh = game.blackHole ?: return
        val hz = bh.horizon()
        // Halo d'accrétion
        pGlow.color = Color.argb(70, 255, 138, 60)
        canvas.drawCircle(bh.x, bh.y, hz * 1.7f, pGlow)
        // Disque noir
        pFill.color = Color.BLACK
        canvas.drawCircle(bh.x, bh.y, hz, pFill)
        // Anneaux d'accrétion en rotation
        pStroke.strokeWidth = dp(3f)
        for (k in 0 until 3) {
            val ang = bh.spin * (1f + k * 0.35f) + k * 2.1f
            val r = hz * (1.15f + k * 0.22f)
            pStroke.color = if (k % 2 == 0) C_BH_RING1 else C_BH_RING2
            pStroke.alpha = 150 - k * 35
            val rect = RectF(bh.x - r, bh.y - r * 0.6f, bh.x + r, bh.y + r * 0.6f)
            canvas.save()
            canvas.rotate(ang * 57.3f, bh.x, bh.y)
            canvas.drawArc(rect, 0f, 230f, false, pStroke)
            canvas.restore()
        }
        pStroke.alpha = 255
        // Avertissement d'éruption : anneau rouge clignotant sur la zone de danger
        if (bh.pulseWarn > 0f) {
            val dangerR = game.pulseRadius(bh)
            val flash = (sin(animT * 24f) * 0.5f + 0.5f)
            pDash.color = Color.argb((90 + flash * 150).toInt(), 255, 110, 60)
            pDash.strokeWidth = dp(2.5f)
            canvas.drawCircle(bh.x, bh.y, dangerR, pDash)
        }
        // Jauge de masse autour du trou noir
        val frac = bh.massFed.toFloat() / bh.massNeeded
        val gr = hz + dp(12f)
        pStroke.color = Color.argb(70, 255, 255, 255); pStroke.strokeWidth = dp(4f)
        canvas.drawCircle(bh.x, bh.y, gr, pStroke)
        pStroke.color = C_DUST
        canvas.drawArc(RectF(bh.x - gr, bh.y - gr, bh.x + gr, bh.y + gr), -90f, 360f * frac, false, pStroke)
        // Masse restante
        pText.color = C_DUST; pText.textSize = sp(12f)
        canvas.drawText("${bh.massFed}/${bh.massNeeded}", bh.x, bh.y + gr + sp(14f), pText)
    }

    private fun drawFlux(canvas: Canvas) {
        if (!game.fluxActive || game.phase == NucleaPhase.GAME_OVER) return
        val range = game.fluxRange()
        val half = game.fluxHalfAngle()
        val aimAng = kotlin.math.atan2(game.aimY, game.aimX)
        val magnetic = game.powerActive(PowerUpType.MAGNETIC)
        val gamma = game.powerActive(PowerUpType.GAMMA)
        val color = when { gamma -> C_FLUX_GAM; magnetic -> C_FLUX_MAG; else -> C_FLUX }

        drawCone(canvas, aimAng, half, range, color)
        if (game.powerActive(PowerUpType.BINARY))
            drawCone(canvas, aimAng + PI.toFloat(), half, range, color)

        // Photons qui filent le long du cône (vers l'intérieur en mode magnétique)
        pFill.color = C_FLUX_CORE
        for (k in 0 until 12) {
            val t = ((animT * 1.6f + k * 0.083f) % 1f)
            val ang = aimAng + fluxJitter[k] * 2f * half * 0.8f
            val dist = if (magnetic) range * (1f - t) else t * range
            canvas.drawCircle(game.px + cos(ang) * dist, game.py + sin(ang) * dist,
                dp(1.6f) * (1f - t * 0.5f), pFill)
        }
    }

    private fun drawCone(canvas: Canvas, ang: Float, half: Float, range: Float, color: Int) {
        fluxPath.reset()
        fluxPath.moveTo(game.px, game.py)
        val steps = 8
        for (i in 0..steps) {
            val a = ang - half + (2f * half) * i / steps
            fluxPath.lineTo(game.px + cos(a) * range, game.py + sin(a) * range)
        }
        fluxPath.close()
        pFill.color = color
        canvas.drawPath(fluxPath, pFill)
    }

    private fun drawShocks(canvas: Canvas) {
        for (s in game.shocks) {
            val alpha = (s.life * 130).toInt().coerceIn(0, 130)
            pStroke.color = Color.argb(alpha, 255, 255, 255)
            pStroke.strokeWidth = dp(3f) * s.life + 1f
            canvas.drawCircle(s.x, s.y, s.r, pStroke)
        }
    }

    private fun drawMarks(canvas: Canvas) {
        for (m in game.marks) {
            val pulse = (sin(animT * 10f) * 0.5f + 0.5f)
            val col = if (m.anti) NucleaGame.ANTI_COLOR else NucleaGame.TIER_COLORS[m.tier]
            pDash.color = Color.argb((90 + pulse * 120).toInt(), Color.red(col), Color.green(col), Color.blue(col))
            pDash.strokeWidth = dp(2f)
            canvas.drawCircle(m.x, m.y, dp(14f) + m.timer * dp(10f), pDash)
        }
    }

    private fun drawMotes(canvas: Canvas) {
        for (m in game.motes) {
            val blink = m.life > 3f || (m.life * 6f).toInt() % 2 == 0
            if (!blink) continue
            val r = dp(2.6f) + min(m.value, 8) * dp(0.35f)
            pGlow.color = Color.argb(70, 203, 184, 255)
            canvas.drawCircle(m.x, m.y, r * 2f, pGlow)
            pFill.color = C_DUST
            canvas.drawCircle(m.x, m.y, r, pFill)
        }
        // Particules de soin : vertes, avec une petite croix
        for (m in game.hpMotes) {
            val blink = m.life > 3f || (m.life * 6f).toInt() % 2 == 0
            if (!blink) continue
            val r = dp(3f) + min(m.value, 6) * dp(0.35f)
            pGlow.color = Color.argb(80, 139, 232, 124)
            canvas.drawCircle(m.x, m.y, r * 2f, pGlow)
            pFill.color = C_HP_MOTE
            canvas.drawCircle(m.x, m.y, r, pFill)
            pStroke.color = Color.WHITE; pStroke.strokeWidth = dp(1.2f)
            canvas.drawLine(m.x - r * 0.5f, m.y, m.x + r * 0.5f, m.y, pStroke)
            canvas.drawLine(m.x, m.y - r * 0.5f, m.x, m.y + r * 0.5f, pStroke)
        }
    }

    private fun drawPowerUps(canvas: Canvas) {
        for (p in game.powerups) {
            val bob = sin(p.phase) * dp(3f)
            val blink = p.life > 3f || (p.life * 6f).toInt() % 2 == 0
            if (!blink) continue
            val r = dp(13f)
            pGlow.color = Color.argb(90, 126, 249, 200)
            canvas.drawCircle(p.x, p.y + bob, r * 1.8f, pGlow)
            pFill.color = C_CARD_BG
            canvas.drawCircle(p.x, p.y + bob, r, pFill)
            pStroke.color = C_GREEN; pStroke.strokeWidth = dp(2f)
            canvas.drawCircle(p.x, p.y + bob, r, pStroke)
            pText.color = C_GREEN; pText.textSize = sp(13f)
            val label = when (p.type) {
                PowerUpType.MAGNETIC -> "M"
                PowerUpType.TIME -> "T"
                PowerUpType.GAMMA -> "γ"
                PowerUpType.SHIELD -> "◆"
                PowerUpType.BINARY -> "B"
            }
            canvas.drawText(label, p.x, p.y + bob + sp(4.5f), pText)
        }
    }

    private fun drawAtoms(canvas: Canvas) {
        for (a in game.atoms) {
            // Clignotement pendant la décroissance
            if (a.decayTimer > 0f && (a.decayTimer * 10f).toInt() % 2 == 0) continue

            val col = if (a.anti) NucleaGame.ANTI_COLOR else NucleaGame.TIER_COLORS[a.tier]
            var haloAlpha = 55
            var drawR = a.radius

            // Fer : pulsation croissante avant la supernova
            if (a.superTimer > 0f) {
                val t = 1f - a.superTimer / 1.5f
                val pulse = sin(animT * 30f) * 0.5f + 0.5f
                haloAlpha = (55 + t * 160 + pulse * 40).toInt().coerceAtMost(255)
                drawR = a.radius * (1f + t * 0.25f)
            }
            if (a.hitFlash > 0f) haloAlpha = 130

            // Halo
            pFill.color = Color.argb(haloAlpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawCircle(a.x, a.y, drawR * 1.45f, pFill)
            // Corps
            pFill.color = if (a.anti) Color.parseColor("#241236") else tierBody[a.tier]
            canvas.drawCircle(a.x, a.y, drawR, pFill)
            // Anneau
            pStroke.color = col; pStroke.strokeWidth = dp(2f)
            canvas.drawCircle(a.x, a.y, drawR, pStroke)
            // Symbole
            pText.color = col
            pText.textSize = drawR * 0.85f
            val sym = NucleaGame.SYMBOLS[a.tier]
            canvas.drawText(sym, a.x, a.y + drawR * 0.3f, pText)
            // Antimatière : barre au-dessus du symbole (H̄)
            if (a.anti) {
                pStroke.strokeWidth = dp(1.5f)
                val hw = drawR * 0.32f
                canvas.drawLine(a.x - hw, a.y - drawR * 0.42f, a.x + hw, a.y - drawR * 0.42f, pStroke)
            }
        }
    }

    private fun drawBooms(canvas: Canvas) {
        for (b in game.booms) {
            val ratio = 1f - b.life / b.maxLife
            val r = b.maxR * ratio
            val alpha = (b.life / b.maxLife * 150).toInt()
            pFill.color = Color.argb(alpha, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            canvas.drawCircle(b.x, b.y, r, pFill)
            pFill.color = Color.argb((alpha * 0.6f).toInt(), 255, 255, 255)
            canvas.drawCircle(b.x, b.y, r * 0.45f, pFill)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in game.particles) {
            val alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            pFill.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(p.x, p.y, p.r, pFill)
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        if (game.phase == NucleaPhase.GAME_OVER) return
        val flashing = game.iframe > 0f && (game.iframe * 10).toInt() % 2 == 0
        if (flashing) return
        val r = dp(13f)
        // Cœur de proto-étoile : glow chaud
        pGlow.color = Color.argb(120, 255, 220, 140)
        canvas.drawCircle(game.px, game.py, r * 1.9f, pGlow)
        pFill.color = Color.parseColor("#FFE9B8")
        canvas.drawCircle(game.px, game.py, r, pFill)
        pFill.color = Color.parseColor("#FFF7E6")
        canvas.drawCircle(game.px, game.py, r * 0.55f, pFill)
        // Boucliers de quarks : anneaux bleus
        if (game.shieldCharges > 0) {
            pStroke.color = Color.parseColor("#44CCFF"); pStroke.strokeWidth = dp(2f)
            for (k in 0 until game.shieldCharges) {
                canvas.drawCircle(game.px, game.py, r + dp(5f) + k * dp(4f), pStroke)
            }
        }
    }

    private fun drawFloats(canvas: Canvas) {
        for (f in game.floats) {
            val alpha = ((f.life / 1.2f) * 255).toInt().coerceIn(0, 255)
            pText.color = (f.color and 0x00FFFFFF) or (alpha shl 24)
            pText.textSize = sp(15f)
            canvas.drawText(f.text, f.x, f.y, pText)
        }
    }

    // ─── HUD ──────────────────────────────────────────────────────────────────

    private fun drawHUD(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Barre de PV à gauche
        val barW = dp(11f); val barMargin = dp(4f)
        val hpTop = dp(6f)
        val barH = h - hpTop - barMargin
        pFill.color = C_HP_BG
        canvas.drawRect(barMargin, hpTop, barMargin + barW, hpTop + barH, pFill)
        val hpRatio = (game.hp / game.maxHp).coerceIn(0f, 1f)
        pFill.color = Color.rgb((255 * (1f - hpRatio * 0.6f)).toInt(), (220 * hpRatio).toInt(), 60)
        canvas.drawRect(barMargin, hpTop + barH * (1f - hpRatio), barMargin + barW, hpTop + barH, pFill)

        // Vague + poussière en haut à droite
        pTextL.textAlign = Paint.Align.RIGHT
        pTextL.color = Color.WHITE; pTextL.textSize = sp(14f)
        canvas.drawText(ctx().getString(R.string.nuclea_wave_label, game.wave), w - dp(10f), hpTop + sp(16f), pTextL)
        pTextL.color = C_DUST
        canvas.drawText("✦ ${game.runDust}", w - dp(10f), hpTop + sp(34f), pTextL)
        pTextL.textAlign = Paint.Align.LEFT

        // Combo au centre-haut
        if (game.comboCount >= 2 && game.comboTimer > 0f) {
            val alpha = ((game.comboTimer / 2.5f) * 255).toInt().coerceIn(0, 255)
            pText.color = (C_GOLD and 0x00FFFFFF) or (alpha shl 24)
            pText.textSize = sp(24f)
            canvas.drawText("×${game.comboCount}", w / 2f, hpTop + sp(30f), pText)
        }

        // Power-ups actifs : pastilles avec temps restant
        var px = w - dp(20f)
        val py = hpTop + sp(56f)
        for (t in PowerUpType.entries) {
            val left = game.powerTimers[t.ordinal]
            if (left <= 0f) continue
            pFill.color = C_CARD_BG
            canvas.drawCircle(px, py, dp(11f), pFill)
            pStroke.color = C_GREEN; pStroke.strokeWidth = dp(2f)
            val maxDur = if (t == PowerUpType.BINARY) 12f else 8f
            canvas.drawArc(RectF(px - dp(11f), py - dp(11f), px + dp(11f), py + dp(11f)),
                -90f, 360f * (left / maxDur).coerceIn(0f, 1f), false, pStroke)
            pText.color = C_GREEN; pText.textSize = sp(11f)
            val label = when (t) {
                PowerUpType.MAGNETIC -> "M"; PowerUpType.TIME -> "T"; PowerUpType.GAMMA -> "γ"
                PowerUpType.SHIELD -> "◆"; PowerUpType.BINARY -> "B"
            }
            canvas.drawText(label, px, py + sp(4f), pText)
            px -= dp(28f)
        }
    }

    private fun drawJoysticks(canvas: Canvas) {
        if (game.phase != NucleaPhase.PLAYING) return
        if (moveId >= 0) drawStick(canvas, moveCx, moveCy, moveKx, moveKy, Color.WHITE)
        if (aimId >= 0) drawStick(canvas, aimCx, aimCy, aimKx, aimKy, Color.parseColor("#A0DCFF"))
    }

    private fun drawStick(canvas: Canvas, cx: Float, cy: Float, kx: Float, ky: Float, tint: Int) {
        pFill.color = Color.argb(40, Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawCircle(cx, cy, JOY_OUTER_R, pFill)
        pStroke.color = Color.argb(90, Color.red(tint), Color.green(tint), Color.blue(tint))
        pStroke.strokeWidth = 2f
        canvas.drawCircle(cx, cy, JOY_OUTER_R, pStroke)
        pFill.color = Color.argb(140, Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawCircle(kx, ky, JOY_INNER_R, pFill)
    }

    private fun drawBanners(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (game.waveBanner > 0f && game.bossBanner <= 0f) {
            val alpha = ((game.waveBanner / 2f) * 230).toInt().coerceIn(0, 230)
            pText.color = Color.argb(alpha, 255, 255, 255)
            pText.textSize = sp(30f)
            canvas.drawText(ctx().getString(R.string.nuclea_wave_banner, game.wave), w / 2f, h * 0.3f, pText)
        }
        if (game.bossBanner > 0f) {
            val alpha = ((game.bossBanner / 3f) * 255).toInt().coerceIn(0, 255)
            pText.color = Color.argb(alpha, 255, 100, 60)
            pText.textSize = sp(30f)
            canvas.drawText(sBlackHole, w / 2f, h * 0.28f, pText)
            pText.color = Color.argb(alpha, 220, 220, 230)
            pText.textSize = sp(13f)
            canvas.drawText(sBlackHoleHint, w / 2f, h * 0.28f + sp(22f), pText)
        }
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────

    private fun drawMenu(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f

        pGlow.color = Color.argb(90, 154, 92, 255)
        canvas.drawCircle(cx, h * 0.2f, dp(50f), pGlow)
        pText.color = Color.WHITE; pText.textSize = sp(40f)
        canvas.drawText(sTitle, cx, h * 0.22f, pText)
        pText.color = C_GRAY; pText.textSize = sp(13f)
        canvas.drawText(sTagline, cx, h * 0.22f + sp(24f), pText)

        // Collection d'éléments synthétisés
        val er = dp(15f)
        val gap = dp(8f)
        val total = NucleaGame.TIER_COUNT * (er * 2f) + (NucleaGame.TIER_COUNT - 1) * gap
        var ex = cx - total / 2f + er
        val ey = h * 0.40f
        for (t in 0 until NucleaGame.TIER_COUNT) {
            val found = game.meta.foundMask and (1 shl t) != 0
            if (found) {
                pFill.color = tierBody[t]
                canvas.drawCircle(ex, ey, er, pFill)
                pStroke.color = NucleaGame.TIER_COLORS[t]; pStroke.strokeWidth = dp(2f)
                canvas.drawCircle(ex, ey, er, pStroke)
                pText.color = NucleaGame.TIER_COLORS[t]; pText.textSize = sp(11f)
                canvas.drawText(NucleaGame.SYMBOLS[t], ex, ey + sp(4f), pText)
            } else {
                pStroke.color = C_STAR_DIM; pStroke.strokeWidth = dp(2f)
                canvas.drawCircle(ex, ey, er, pStroke)
                pText.color = C_STAR_DIM; pText.textSize = sp(11f)
                canvas.drawText("?", ex, ey + sp(4f), pText)
            }
            ex += er * 2f + gap
        }

        // Record + solde
        pText.color = C_GRAY; pText.textSize = sp(14f)
        if (game.meta.bestWave > 0)
            canvas.drawText(ctx().getString(R.string.nuclea_best_wave, game.meta.bestWave), cx, h * 0.49f, pText)
        pText.color = C_DUST
        canvas.drawText("✦ ${game.meta.dust}", cx, h * 0.49f + sp(20f), pText)

        // Tap pour démarrer (pulsation)
        val pulse = (sin(animT * 3f) * 0.5f + 0.5f)
        pText.color = Color.argb((150 + pulse * 105).toInt(), 255, 255, 255)
        pText.textSize = sp(17f)
        canvas.drawText(sTapStart, cx, h * 0.63f, pText)

        // Bouton constellations
        val bw = dp(210f); val bh = dp(46f)
        constBtnRect.set(cx - bw / 2f, h * 0.72f, cx + bw / 2f, h * 0.72f + bh)
        pFill.color = C_BTN_BG
        canvas.drawRoundRect(constBtnRect, dp(10f), dp(10f), pFill)
        pStroke.color = C_DUST; pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(constBtnRect, dp(10f), dp(10f), pStroke)
        pText.color = C_DUST; pText.textSize = sp(15f)
        canvas.drawText("✧ $sConstBtn", constBtnRect.centerX(), constBtnRect.centerY() + sp(5f), pText)
    }

    // ─── Constellations ───────────────────────────────────────────────────────

    private fun drawConstellations(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Bouton retour + solde
        backBtnRect.set(dp(8f), dp(8f), dp(88f), dp(46f))
        pFill.color = C_BTN_BG
        canvas.drawRoundRect(backBtnRect, dp(10f), dp(10f), pFill)
        pText.color = Color.WHITE; pText.textSize = sp(13f)
        canvas.drawText("← $sBack", backBtnRect.centerX(), backBtnRect.centerY() + sp(4.5f), pText)

        pTextL.textAlign = Paint.Align.RIGHT
        pTextL.color = C_DUST; pTextL.textSize = sp(16f)
        canvas.drawText("✦ ${game.meta.dust}", w - dp(12f), dp(34f), pTextL)
        pTextL.textAlign = Paint.Align.LEFT

        pText.color = Color.WHITE; pText.textSize = sp(19f)
        canvas.drawText(sConstBtn, w / 2f + dp(20f), dp(34f), pText)

        // Zone de ciel : nœuds reliés par constellation
        val skyTop = dp(64f)
        val skyBottom = h - dp(96f)
        fun nodeX(n: MetaNode) = n.nx * w
        fun nodeY(n: MetaNode) = skyTop + n.ny * (skyBottom - skyTop)

        // Lignes entre étoiles d'une même constellation
        pStroke.strokeWidth = dp(1.5f)
        for (c in 0 until 3) {
            val nodes = NucleaGame.META_NODES.filter { it.constellation == c }
            val complete = game.constellationComplete(c)
            pStroke.color = if (complete) Color.argb(180, 255, 224, 102) else Color.argb(80, 203, 184, 255)
            for (i in 0 until nodes.size - 1) {
                canvas.drawLine(nodeX(nodes[i]), nodeY(nodes[i]), nodeX(nodes[i + 1]), nodeY(nodes[i + 1]), pStroke)
            }
        }

        // Étoiles-nœuds
        NucleaGame.META_NODES.forEachIndexed { i, n ->
            val x = nodeX(n); val y = nodeY(n)
            val lvl = game.meta.lvl(n.key)
            val maxed = lvl >= n.maxLvl
            val cost = game.nodeCost(n)
            val affordable = !maxed && game.meta.dust >= cost

            nodeRects[i].set(x - dp(30f), y - dp(30f), x + dp(30f), y + dp(38f))

            val starR = dp(11f)
            if (maxed) {
                pGlow.color = Color.argb(120, 255, 224, 102)
                canvas.drawCircle(x, y, starR * 2f, pGlow)
            } else if (affordable) {
                pGlow.color = Color.argb(70, 203, 184, 255)
                canvas.drawCircle(x, y, starR * 1.7f, pGlow)
            }
            setStar(x, y, starR, starR * 0.45f)
            pFill.color = when {
                maxed -> C_GOLD
                lvl > 0 -> C_DUST
                affordable -> Color.parseColor("#7A6AA8")
                else -> C_STAR_DIM
            }
            canvas.drawPath(starPath, pFill)

            // Label + niveau + coût
            pText.textSize = sp(10.5f)
            pText.color = if (maxed || lvl > 0 || affordable) Color.WHITE else C_GRAY
            canvas.drawText(ctx().getString(n.labelRes), x, y + dp(22f), pText)
            pText.textSize = sp(10f)
            if (maxed) {
                pText.color = C_GOLD
                canvas.drawText("MAX", x, y + dp(34f), pText)
            } else {
                pText.color = if (affordable) C_GOLD else C_GRAY
                canvas.drawText("$lvl/${n.maxLvl}  ✦$cost", x, y + dp(34f), pText)
            }
        }

        // Bandeau bas : bonus de constellation complète
        var by = h - dp(78f)
        pTextL.textSize = sp(11.5f)
        for (c in 0 until 3) {
            val complete = game.constellationComplete(c)
            pTextL.color = if (complete) C_GOLD else C_GRAY
            val check = if (complete) "★ " else "☆ "
            canvas.drawText(
                check + ctx().getString(NucleaGame.CONSTELLATION_NAMES[c]) + " — " +
                        ctx().getString(NucleaGame.CONSTELLATION_BONUS[c]),
                dp(12f), by, pTextL
            )
            by += sp(18f)
        }
    }

    private fun setStar(cx: Float, cy: Float, outerR: Float, innerR: Float) {
        starPath.reset()
        val points = 4
        val total = points * 2
        for (i in 0 until total) {
            val a = PI.toFloat() / points * i - PI.toFloat() / 2f
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    // ─── Pause / Game over ────────────────────────────────────────────────────

    private fun drawPaused(canvas: Canvas) {
        pFill.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f; val cy = height / 2f
        pText.color = Color.WHITE; pText.textSize = sp(28f)
        canvas.drawText(sPaused, cx, cy - dp(60f), pText)
        drawTwoButtons(canvas, cx, cy, sResume, sQuit)
    }

    private fun drawGameOver(canvas: Canvas) {
        pFill.color = Color.argb(210, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f; val cy = height / 2f

        pText.color = C_GAMEOVER; pText.textSize = sp(32f)
        canvas.drawText(sGameOver, cx, cy - dp(100f), pText)

        pText.color = Color.WHITE; pText.textSize = sp(15f)
        canvas.drawText(ctx().getString(R.string.nuclea_wave_reached, game.wave), cx, cy - dp(58f), pText)
        pText.color = C_DUST
        canvas.drawText(ctx().getString(R.string.nuclea_stardust_earned, game.runDust), cx, cy - dp(34f), pText)

        val reward = NeutrinoRewards.nuclea(game.wavesCleared)
        if (reward > 0) {
            pText.color = C_GREEN
            canvas.drawText(ctx().getString(R.string.nuclea_neutrinos_earned, reward), cx, cy - dp(10f), pText)
        }

        if (game.meta.bestWave > 0) {
            pText.color = C_GRAY; pText.textSize = sp(12f)
            canvas.drawText(ctx().getString(R.string.nuclea_best_wave, game.meta.bestWave), cx, cy + dp(14f), pText)
        }
        drawOneButton(canvas, cx, cy + dp(30f), sMenu)
    }

    // ─── Boutons ──────────────────────────────────────────────────────────────

    private fun drawOneButton(canvas: Canvas, cx: Float, cy: Float, label: String) {
        val bw = dp(160f); val bh = dp(44f); val by = cy + dp(20f)
        btnRect1.set(cx - bw / 2f, by, cx + bw / 2f, by + bh)
        btnRect2.setEmpty()
        pFill.color = C_BTN_BG
        canvas.drawRoundRect(btnRect1, dp(10f), dp(10f), pFill)
        pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(btnRect1, dp(10f), dp(10f), pStroke)
        pText.color = Color.WHITE; pText.textSize = sp(15f)
        canvas.drawText(label, btnRect1.centerX(), btnRect1.centerY() + sp(5f), pText)
    }

    private fun drawTwoButtons(canvas: Canvas, cx: Float, cy: Float, label1: String, label2: String) {
        val bw = dp(140f); val bh = dp(44f); val gap = dp(20f)
        val by = cy + dp(20f)
        btnRect1.set(cx - bw - gap / 2f, by, cx - gap / 2f, by + bh)
        btnRect2.set(cx + gap / 2f, by, cx + bw + gap / 2f, by + bh)
        for ((rect, label) in listOf(btnRect1 to label1, btnRect2 to label2)) {
            pFill.color = C_BTN_BG
            canvas.drawRoundRect(rect, dp(10f), dp(10f), pFill)
            pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = 1.5f
            canvas.drawRoundRect(rect, dp(10f), dp(10f), pStroke)
            pText.color = Color.WHITE; pText.textSize = sp(15f)
            canvas.drawText(label, rect.centerX(), rect.centerY() + sp(5f), pText)
        }
    }

    // ─── Util ─────────────────────────────────────────────────────────────────

    private fun ctx(): Context = context
    private fun dp(v: Float) = v * _dp
    private fun sp(v: Float) = v * _sp
}
