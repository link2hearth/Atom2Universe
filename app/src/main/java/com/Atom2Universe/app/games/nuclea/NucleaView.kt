package com.Atom2Universe.app.games.nuclea

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NucleaView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    /** Faux dès qu'une surface a refusé le canevas matériel : voir [lockFrame]. */
    private var hardwareCanvas = true

    /**
     * Attrape l'image à venir — **sur le processeur graphique**.
     *
     * C'était [SurfaceHolder.lockCanvas], donc un canevas logiciel : le processeur
     * calculait et écrivait lui-même les quatre millions et demi de pixels de l'écran,
     * à chaque image. Mesuré à la tablette sur le jeu Particules, qui souffrait du même
     * mal, cela coûtait un cœur entier et un ampère pendant que la puce graphique
     * restait à deux pour cent ; le basculement a ramené le jeu à trente pour cent d'un
     * cœur et la puce de 53 à 36 degrés. Remplir des surfaces est précisément ce que la
     * carte graphique fait pour rien.
     *
     * [SurfaceHolder.lockHardwareCanvas] ne demande qu'une chose : **tout redessiner à
     * chaque image**, puisque le contenu de l'image précédente n'est pas conservé — ce
     * que cette vue fait déjà, son rendu commençant par repeindre l'écran entier.
     *
     * Le repli logiciel n'est pas de la prudence de principe : une surface peut refuser
     * le canevas matériel, et le jeu doit alors continuer comme avant plutôt que de
     * s'arrêter. Un refus vaut pour toujours, on ne le redemande pas soixante fois par
     * seconde ; une toile nulle, en revanche, veut seulement dire que la surface n'est
     * pas prête, et c'est l'appelant qui patiente.
     */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

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
    private val C_AURA      = Color.parseColor("#7EF9C8")
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
    @Volatile private var pendingRespec = false
    @Volatile private var pendingResume = false
    /** Appuis de martèlement accumulés par le thread UI, consommés par le thread de jeu. */
    private val mashCount = java.util.concurrent.atomic.AtomicInteger(0)

    // Sticks d'une manette physique (type Xbox) — prioritaires quand le tactile est relâché
    @Volatile private var padMx = 0f
    @Volatile private var padMy = 0f
    @Volatile private var padAx = 0f
    @Volatile private var padAy = 0f

    // ─── Strings mises en cache ──────────────────────────────────────────────

    private val sTitle        by lazy { ctx.getString(R.string.nuclea_title) }
    private val sTagline      by lazy { ctx.getString(R.string.nuclea_tagline) }
    private val sConstBtn     by lazy { ctx.getString(R.string.nuclea_constellations) }
    private val sBack         by lazy { ctx.getString(R.string.nuclea_back) }
    private val sRespec       by lazy { ctx.getString(R.string.nuclea_respec) }
    private val sRespecOk     by lazy { ctx.getString(R.string.nuclea_respec_confirm) }
    private val sDetails      by lazy { ctx.getString(R.string.nuclea_details) }
    private val sPaused       by lazy { ctx.getString(R.string.nuclea_paused) }
    private val sResume       by lazy { ctx.getString(R.string.nuclea_resume) }
    private val sNewGame      by lazy { ctx.getString(R.string.nuclea_new_game) }
    private val sQuit         by lazy { ctx.getString(R.string.nuclea_quit) }
    private val sMenu         by lazy { ctx.getString(R.string.nuclea_menu) }
    private val sGameOver     by lazy { ctx.getString(R.string.nuclea_game_over) }
    private val sBlackHole    by lazy { ctx.getString(R.string.nuclea_blackhole) }
    private val sBlackHoleHint by lazy { ctx.getString(R.string.nuclea_blackhole_hint) }
    private val sCaptured     by lazy { ctx.getString(R.string.nuclea_captured) }
    private val sCaptureHint  by lazy { ctx.getString(R.string.nuclea_capture_hint) }

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
    // Flux de photons : remplissage et bords peints avec un dégradé radial centré
    // sur le joueur, pour que le cône se dissolve dans le vide au lieu de se couper net.
    private val pFluxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pFluxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pFluxWave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private var fluxShRange = -1f
    private var fluxShColor = 0
    // Aura du pulsar : bulle dégradée + anneaux d'onde
    private val pAuraFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pAuraRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private var auraShRadius = -1f
    private val fluxPath = Path()
    private val wavePath = Path()
    private val starPath = Path()
    private val jetPath = Path()

    // Rects de boutons
    private val btnRect1 = RectF()
    private val btnRect2 = RectF()
    private val btnRect3 = RectF()
    private val resumeBtnRect = RectF()
    private val newBtnRect = RectF()
    private val constBtnRect = RectF()
    private val respecBtnRect = RectF()
    private val infoBtnRect = RectF()
    /** Panneau « détails » : liste chiffrée des améliorations, à la place du ciel. */
    private var showInfo = false
    /** Le bouton de remise à zéro demande confirmation : deuxième appui avant cette date. */
    private var respecConfirmUntil = -1f
    private val backBtnRect = RectF()
    private val nodeRects = Array(NucleaGame.META_NODES.size) { RectF() }
    // Positions écran des étoiles de la constellation en cours de dessin (pas d'allocation)
    private val starBuf = FloatArray(NucleaGame.SHAPES.maxOf { it.mags.size } * 2)

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
            if (pendingResume) { pendingResume = false; if (!game.resumeRun()) game.startGame() }
            pendingPhase?.let { ph -> pendingPhase = null; game.phase = ph }
            val node = pendingBuyNode
            if (node >= 0) {
                pendingBuyNode = -1
                NucleaGame.META_NODES.getOrNull(node)?.let { game.tryBuyNode(it) }
            }
            if (pendingRespec) { pendingRespec = false; game.respec() }

            // Martèlement : on rejoue tous les appuis reçus depuis la frame précédente
            val mash = mashCount.getAndSet(0)
            repeat(mash) { game.mashEscape() }

            // Tactile prioritaire quand un doigt tient le stick, sinon la manette
            val mx = if (moveId >= 0) jmx else padMx
            val my = if (moveId >= 0) jmy else padMy
            val ax = if (aimId >= 0) jax else padAx
            val ay = if (aimId >= 0) jay else padAy
            game.update(dt, mx, my, ax, ay)

            if (game.phase != NucleaPhase.PLAYING) {
                jmx = 0f; jmy = 0f; jax = 0f; jay = 0f
                moveId = -1; aimId = -1
                mashCount.set(0)
            }

            val canvas = lockFrame()
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

    /** Bouton A ou START : valider / pause / reprendre / se débattre selon la phase. */
    fun onPadConfirm(isStart: Boolean) {
        when (game.phase) {
            NucleaPhase.MENU -> if (game.hasResumableRun()) pendingResume = true else pendingStart = true
            // Happé par le trou noir : A devient le bouton de martèlement
            NucleaPhase.PLAYING -> if (isStart) pendingPhase = NucleaPhase.PAUSED
                                   else if (game.captured) mashCount.incrementAndGet()
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
        when {
            constBtnRect.contains(ev.x, ev.y) -> pendingPhase = NucleaPhase.CONSTELLATION
            !resumeBtnRect.isEmpty && resumeBtnRect.contains(ev.x, ev.y) -> pendingResume = true
            newBtnRect.contains(ev.x, ev.y) -> pendingStart = true
        }
    }

    private fun handleConstellationTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        if (backBtnRect.contains(ev.x, ev.y)) { pendingPhase = NucleaPhase.MENU; return }
        if (infoBtnRect.contains(ev.x, ev.y)) { showInfo = !showInfo; respecConfirmUntil = -1f; return }
        if (showInfo) { showInfo = false; return }
        if (!respecBtnRect.isEmpty && respecBtnRect.contains(ev.x, ev.y)) {
            // Premier appui : on demande confirmation. Deuxième appui dans les 3 s : on applique.
            if (animT < respecConfirmUntil) { pendingRespec = true; respecConfirmUntil = -1f }
            else respecConfirmUntil = animT + 3f
            return
        }
        respecConfirmUntil = -1f
        nodeRects.forEachIndexed { i, r ->
            if (!r.isEmpty && r.contains(ev.x, ev.y)) pendingBuyNode = i
        }
    }

    private fun handlePlayTouch(ev: MotionEvent) {
        val pi = ev.actionIndex
        val pid = ev.getPointerId(pi)
        val px = ev.getX(pi); val py = ev.getY(pi)

        // Capturé : les sticks sont coupés, chaque poser de doigt — n'importe où —
        // compte comme un appui du martèlement
        if (game.captured) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
                ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) mashCount.incrementAndGet()
            moveId = -1; aimId = -1
            return
        }

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
                        val fx = ev.getX(i); val fy = ev.getY(i)
                        var dx = fx - moveCx; var dy = fy - moveCy
                        var dist = sqrt(dx * dx + dy * dy)
                        // Stick flottant : passé le rayon max, la base suit le pouce au
                        // lieu de rester plantée là où le doigt s'est posé.
                        if (dist > JOY_MAX) {
                            val over = dist - JOY_MAX
                            moveCx += dx / dist * over; moveCy += dy / dist * over
                            dx = fx - moveCx; dy = fy - moveCy
                            dist = JOY_MAX
                        }
                        val nx = if (dist > 0f) dx / dist else 0f
                        val ny = if (dist > 0f) dy / dist else 0f
                        moveKx = fx; moveKy = fy
                        jmx = nx * (dist / JOY_MAX); jmy = ny * (dist / JOY_MAX)
                    }
                    if (id == aimId) {
                        val fx = ev.getX(i); val fy = ev.getY(i)
                        var dx = fx - aimCx; var dy = fy - aimCy
                        var dist = sqrt(dx * dx + dy * dy)
                        if (dist > JOY_MAX) {
                            val over = dist - JOY_MAX
                            aimCx += dx / dist * over; aimCy += dy / dist * over
                            dx = fx - aimCx; dy = fy - aimCy
                            dist = JOY_MAX
                        }
                        val nx = if (dist > 0f) dx / dist else 0f
                        val ny = if (dist > 0f) dy / dist else 0f
                        aimKx = fx; aimKy = fy
                        jax = nx * (dist / JOY_MAX); jay = ny * (dist / JOY_MAX)
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
        if (btnRect2.contains(ev.x, ev.y)) pendingStart = true
        // « Quitter » retourne au menu sans abandonner la partie : elle reste reprenable
        if (btnRect3.contains(ev.x, ev.y)) pendingPhase = NucleaPhase.MENU
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
                drawCapture(canvas)
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
        drawAura(canvas)
        drawShocks(canvas)
        drawMarks(canvas)
        drawMotes(canvas)
        drawPowerUps(canvas)
        drawAtoms(canvas)
        drawNeutrons(canvas)
        drawRays(canvas)
        drawBooms(canvas)
        drawParticles(canvas)
        drawPlayer(canvas)
        drawFloats(canvas)
    }

    private fun drawBlackHole(canvas: Canvas) {
        val bh = game.blackHole ?: return
        // Apparition : le trou noir grandit depuis un point pendant sa phase inoffensive
        val appear = (1f - bh.grace / BlackHole.SPAWN_GRACE).coerceIn(0f, 1f)
        val hz = bh.horizon() * (0.25f + 0.75f * appear)
        // Halo d'accrétion — éteint et vacillant tant qu'il est sonné par une évasion,
        // pour qu'on voie d'un coup d'œil que le puits de gravité est coupé
        val stunned = bh.stun > 0f
        val haloA = if (stunned) (18 + (sin(animT * 18f) * 0.5f + 0.5f) * 22).toInt() else 70
        pGlow.color = Color.argb(haloA, 255, 138, 60)
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
        // Anneau qui se resserre pendant l'apparition : indique où il se forme
        if (bh.grace > 0f) {
            pDash.color = Color.argb(210, 255, 138, 60)
            pDash.strokeWidth = dp(2.5f)
            canvas.drawCircle(bh.x, bh.y, bh.horizon() * (1f + 5f * (1f - appear)), pDash)
        }
        // Éruption : jet polaire. Pendant l'avertissement on montre les deux faisceaux
        // en pointillés — les zones perpendiculaires restent sûres, à l'inverse de
        // l'ancienne onde circulaire qui n'offrait aucune sortie.
        if (bh.pulseWarn > 0f || bh.jetFlash > 0f) {
            val reach = max(width, height).toFloat()
            val half = game.jetHalfWidth()
            val ux = cos(bh.jetAngle); val uy = sin(bh.jetAngle)
            // Vecteur perpendiculaire : donne l'épaisseur du faisceau
            val nxp = -uy * half; val nyp = ux * half

            if (bh.jetFlash > 0f) {
                // Le jet est parti : faisceau plein qui s'estompe
                val a = (bh.jetFlash / 0.35f * 210).toInt().coerceIn(0, 210)
                pFill.color = Color.argb(a, 255, 176, 103)
                jetPath.rewind()
                jetPath.moveTo(bh.x - ux * reach + nxp, bh.y - uy * reach + nyp)
                jetPath.lineTo(bh.x + ux * reach + nxp, bh.y + uy * reach + nyp)
                jetPath.lineTo(bh.x + ux * reach - nxp, bh.y + uy * reach - nyp)
                jetPath.lineTo(bh.x - ux * reach - nxp, bh.y - uy * reach - nyp)
                jetPath.close()
                canvas.drawPath(jetPath, pFill)
                pFill.color = Color.argb((a * 0.8f).toInt(), 255, 255, 240)
                pStroke.color = Color.argb(a, 255, 255, 240); pStroke.strokeWidth = dp(3f)
                canvas.drawLine(bh.x - ux * reach, bh.y - uy * reach,
                    bh.x + ux * reach, bh.y + uy * reach, pStroke)
            } else {
                // Avertissement : contours clignotants, on a 1,1 s pour dégager
                val flash = (sin(animT * 24f) * 0.5f + 0.5f)
                val a = (60 + flash * 90).toInt()
                pFill.color = Color.argb((a * 0.35f).toInt(), 255, 138, 60)
                jetPath.rewind()
                jetPath.moveTo(bh.x - ux * reach + nxp, bh.y - uy * reach + nyp)
                jetPath.lineTo(bh.x + ux * reach + nxp, bh.y + uy * reach + nyp)
                jetPath.lineTo(bh.x + ux * reach - nxp, bh.y + uy * reach - nyp)
                jetPath.lineTo(bh.x - ux * reach - nxp, bh.y - uy * reach - nyp)
                jetPath.close()
                canvas.drawPath(jetPath, pFill)
                pDash.color = Color.argb((90 + flash * 150).toInt(), 255, 110, 60)
                pDash.strokeWidth = dp(2.5f)
                for (s in intArrayOf(1, -1)) {
                    canvas.drawLine(bh.x - ux * reach + nxp * s, bh.y - uy * reach + nyp * s,
                        bh.x + ux * reach + nxp * s, bh.y + uy * reach + nyp * s, pDash)
                }
            }
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

        ensureFluxShaders(range, color)
        drawCone(canvas, aimAng, half, range, magnetic)
    }

    /** Onde de répulsion du pulsar : bulle qui pulse tout autour du joueur. */
    private fun drawAura(canvas: Canvas) {
        if (game.phase == NucleaPhase.GAME_OVER) return
        val left = game.powerTimers[PowerUpType.PULSAR.ordinal]
        if (left <= 0f) return
        val r = game.auraRadius()
        if (r != auraShRadius) {
            auraShRadius = r
            pAuraFill.shader = RadialGradient(
                0f, 0f, r,
                intArrayOf(withA(C_AURA, 0f), withA(C_AURA, 16f), withA(C_AURA, 46f), withA(C_AURA, 10f)),
                floatArrayOf(0f, 0.5f, 0.88f, 1f), Shader.TileMode.CLAMP
            )
        }
        // Clignote sur la fin pour annoncer l'expiration
        val expiring = if (left < 2f) (0.55f + 0.45f * sin(animT * 16f)) else 1f

        canvas.save()
        canvas.translate(game.px, game.py)
        pAuraFill.alpha = (255 * expiring).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, pAuraFill)

        // Deux ondes qui s'écartent du joueur jusqu'au bord de la bulle
        for (k in 0 until 2) {
            val t = (animT * 0.9f + k * 0.5f) % 1f
            val fade = sin(t * PI.toFloat()) * expiring
            pAuraRing.color = withA(C_AURA, 170f * fade)
            pAuraRing.strokeWidth = dp(2.2f) * (0.6f + fade * 0.6f)
            ripplePath(r * (0.15f + 0.85f * t), 0.045f, animT * 2.4f + k)
            canvas.drawPath(wavePath, pAuraRing)
        }

        // Liseré extérieur : la limite d'action, légèrement ondulante
        pAuraRing.color = withA(C_AURA, 130f * expiring)
        pAuraRing.strokeWidth = dp(1.6f)
        ripplePath(r, 0.03f, -animT * 1.8f)
        canvas.drawPath(wavePath, pAuraRing)
        canvas.restore()
    }

    /** Cercle légèrement ondulé de rayon [r], écrit dans [wavePath] (aucune allocation). */
    private fun ripplePath(r: Float, amp: Float, phase: Float) {
        wavePath.reset()
        val n = 30
        for (i in 0..n) {
            val a = i / n.toFloat() * 2f * PI.toFloat()
            val rr = r * (1f + amp * sin(a * 5f + phase))
            val x = cos(a) * rr; val y = sin(a) * rr
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        wavePath.close()
    }

    /**
     * Dégradés du flux : recréés seulement quand la portée ou la couleur change
     * (jamais dans la boucle de rendu). Ils sont centrés sur (0,0) : le canvas est
     * translaté sur le joueur avant le dessin, donc le dégradé suit le joueur tout seul.
     */
    private fun ensureFluxShaders(range: Float, color: Int) {
        if (range == fluxShRange && color == fluxShColor) return
        fluxShRange = range
        fluxShColor = color
        val a = Color.alpha(color)
        pFluxFill.shader = RadialGradient(
            0f, 0f, range,
            intArrayOf(withA(color, a * 2.6f), withA(color, a * 1.6f), withA(color, a * 0.7f), withA(color, 0f)),
            floatArrayOf(0f, 0.32f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        val edge = RadialGradient(
            0f, 0f, range,
            intArrayOf(withA(color, a * 5f), withA(color, a * 3f), withA(color, 0f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        pFluxEdge.shader = edge
        pFluxWave.shader = edge
    }

    private fun withA(c: Int, alpha: Float) =
        (c and 0x00FFFFFF) or (alpha.toInt().coerceIn(0, 255) shl 24)

    /**
     * Demi-ouverture du cône à la distance relative [t] (0 = joueur, 1 = portée max).
     * Le facteur (0.62 + 0.38·t) donne une silhouette évasée façon pavillon plutôt
     * qu'un triangle droit, et le sinus fait onduler le bord dans le temps.
     */
    private fun coneEdge(t: Float, half: Float, phase: Float) =
        half * (0.62f + 0.38f * t) * (1f + 0.10f * sin(t * 9f - animT * 5.5f + phase))

    private fun drawCone(canvas: Canvas, ang: Float, half: Float, range: Float, magnetic: Boolean) {
        canvas.save()
        canvas.translate(game.px, game.py)
        canvas.rotate(ang * 57.29578f)

        // ── Silhouette : bord bas ondulant → front d'onde arrondi → bord haut ──
        val eNeg = coneEdge(1f, half, 0f)
        val ePos = coneEdge(1f, half, 2.2f)
        fluxPath.reset()
        fluxPath.moveTo(0f, 0f)
        val steps = 14
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val e = -coneEdge(t, half, 0f)
            fluxPath.lineTo(cos(e) * range * t, sin(e) * range * t)
        }
        val arcSteps = 18
        for (i in 0..arcSteps) {
            val f = i / arcSteps.toFloat()
            val e = -eNeg + (eNeg + ePos) * f
            val r = range * (1f + 0.035f * sin(f * 11f - animT * 6f))
            fluxPath.lineTo(cos(e) * r, sin(e) * r)
        }
        for (i in steps downTo 1) {
            val t = i / steps.toFloat()
            val e = coneEdge(t, half, 2.2f)
            fluxPath.lineTo(cos(e) * range * t, sin(e) * range * t)
        }
        fluxPath.close()
        canvas.drawPath(fluxPath, pFluxFill)
        pFluxEdge.strokeWidth = dp(1.5f)
        canvas.drawPath(fluxPath, pFluxEdge)

        // ── Fronts d'onde qui remontent le cône (vers le joueur si magnétique) ──
        for (k in 0 until 3) {
            var t = (animT * 0.7f + k / 3f) % 1f
            if (magnetic) t = 1f - t
            val fade = sin(t * PI.toFloat())
            val e = coneEdge(t, half, k * 1.7f)
            wavePath.reset()
            val n = 14
            for (i in 0..n) {
                val f = i / n.toFloat()
                val aa = -e + 2f * e * f
                val rr = range * t * (1f + 0.05f * sin(f * 7f + animT * 4f + k))
                val x = cos(aa) * rr; val y = sin(aa) * rr
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            pFluxWave.strokeWidth = dp(2.4f) * (0.5f + fade * 0.8f)
            pFluxWave.alpha = (215 * fade).toInt().coerceIn(0, 255)
            canvas.drawPath(wavePath, pFluxWave)
        }
        pFluxWave.alpha = 255

        // ── Photons qui filent le long du flux ──
        pFill.color = C_FLUX_CORE
        for (k in 0 until 12) {
            val t = (animT * 1.6f + k * 0.083f) % 1f
            val e = coneEdge(t, half, k * 0.9f)
            val aa = fluxJitter[k] * 1.7f * e
            val dist = (if (magnetic) 1f - t else t) * range
            canvas.drawCircle(cos(aa) * dist, sin(aa) * dist, dp(1.6f) * (1.2f - t * 0.6f), pFill)
        }

        // ── Cœur lumineux à la base du flux ──
        pGlow.color = withA(fluxShColor, 150f)
        canvas.drawCircle(dp(4f), 0f, dp(7f), pGlow)

        canvas.restore()
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
            val col = if (m.anti) NucleaGame.antiColor(m.antiKind) else NucleaGame.TIER_COLORS[m.tier]
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
            canvas.drawText(powerLabel(p.type), p.x, p.y + bob + sp(4.5f), pText)
        }
    }

    private fun drawAtoms(canvas: Canvas) {
        for (a in game.atoms) {
            // Clignotement pendant la décroissance
            if (a.decayTimer > 0f && (a.decayTimer * 10f).toInt() % 2 == 0) continue

            val col = if (a.anti) NucleaGame.antiColor(a.antiKind) else NucleaGame.TIER_COLORS[a.tier]
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
            // Symbole. Les antiparticules portent le leur (e⁺ positron, p⁻ antiproton) :
            // c'est ce qui dit au joueur quel bonus il récupérera en les percutant.
            pText.color = col
            if (a.anti) {
                pText.textSize = drawR * 0.72f
                canvas.drawText(NucleaGame.antiSymbol(a.antiKind), a.x, a.y + drawR * 0.28f, pText)
                // Anneau intérieur en pointillés : signature visuelle de l'antimatière
                pDash.color = Color.argb(190, Color.red(col), Color.green(col), Color.blue(col))
                pDash.strokeWidth = dp(1.4f)
                canvas.drawCircle(a.x, a.y, drawR * 0.72f, pDash)
            } else {
                pText.textSize = drawR * 0.85f
                canvas.drawText(NucleaGame.SYMBOLS[a.tier], a.x, a.y + drawR * 0.3f, pText)
            }
        }
    }

    /**
     * Neutron : bille grise sans anneau coloré (il n'a pas de charge), avec un
     * halo qui palpite pour se distinguer d'un atome au premier coup d'œil.
     * Il clignote quand il ne lui reste que 3 s avant de se désintégrer.
     */
    private fun drawNeutrons(canvas: Canvas) {
        val r = dp(7f)
        for (n in game.neutrons) {
            // Clignote sur les 2 dernières secondes : il est sur le point de se désintégrer
            if (n.life < 2f && (n.life * 6f).toInt() % 2 == 0) continue
            val pulse = sin(animT * 7f + n.x * 0.05f) * 0.5f + 0.5f
            val col = NucleaGame.NEUTRON_COLOR
            pGlow.color = Color.argb((50 + pulse * 50).toInt(),
                Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawCircle(n.x, n.y, r * 2.2f, pGlow)
            pFill.color = if (n.hitFlash > 0f) Color.WHITE else Color.parseColor("#5A5F6E")
            canvas.drawCircle(n.x, n.y, r, pFill)
            pStroke.color = col; pStroke.strokeWidth = dp(1.6f)
            canvas.drawCircle(n.x, n.y, r, pStroke)
            // Le « n » du neutron
            pText.color = col; pText.textSize = r * 1.25f
            canvas.drawText("n", n.x, n.y + r * 0.42f, pText)
        }
    }

    /**
     * Rayon cosmique : d'abord une ligne pointillée qui annonce la trajectoire
     * (0,8 s pour dégager), puis le trait lumineux de la particule elle-même,
     * dessiné comme une comète — tête brillante et sillage qui s'éteint.
     */
    private fun drawRays(canvas: Canvas) {
        val reach = max(width, height).toFloat() * 2f
        for (r in game.rays) {
            if (r.warn > 0f) {
                // Télégraphe : il se resserre et s'intensifie à l'approche du tir
                val t = 1f - r.warn / CosmicRay.WARN_TIME
                val flash = (sin(animT * 26f) * 0.5f + 0.5f)
                val a = (40 + t * 120 + flash * 60).toInt().coerceIn(0, 255)
                pDash.color = Color.argb(a, Color.red(NucleaGame.RAY_COLOR),
                    Color.green(NucleaGame.RAY_COLOR), Color.blue(NucleaGame.RAY_COLOR))
                pDash.strokeWidth = dp(1.5f) + dp(2f) * t
                // La ligne visée passe par le point de mire, dans les deux sens
                val cx = r.x0 + r.dirX * r.span * 0.5f
                val cy = r.y0 + r.dirY * r.span * 0.5f
                canvas.drawLine(cx - r.dirX * reach, cy - r.dirY * reach,
                    cx + r.dirX * reach, cy + r.dirY * reach, pDash)
                continue
            }
            val tailLen = dp(90f)
            val tx = r.x - r.dirX * tailLen
            val ty = r.y - r.dirY * tailLen
            pStroke.color = Color.argb(90, Color.red(NucleaGame.RAY_COLOR),
                Color.green(NucleaGame.RAY_COLOR), Color.blue(NucleaGame.RAY_COLOR))
            pStroke.strokeWidth = dp(6f)
            canvas.drawLine(tx, ty, r.x, r.y, pStroke)
            pStroke.color = Color.WHITE; pStroke.strokeWidth = dp(2.5f)
            canvas.drawLine(tx, ty, r.x, r.y, pStroke)
            pGlow.color = Color.argb(160, Color.red(NucleaGame.RAY_COLOR),
                Color.green(NucleaGame.RAY_COLOR), Color.blue(NucleaGame.RAY_COLOR))
            canvas.drawCircle(r.x, r.y, dp(8f), pGlow)
            pFill.color = Color.WHITE
            canvas.drawCircle(r.x, r.y, dp(3.5f), pFill)
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
            // Les boosts d'antimatière gardent leur couleur : on relie la pastille
            // à l'antiparticule qu'on vient de percuter
            val tint = when (t) {
                PowerUpType.SWIFT -> NucleaGame.ANTI_COLOR
                PowerUpType.THRUST -> NucleaGame.ANTI_THRUST_COLOR
                else -> C_GREEN
            }
            pFill.color = C_CARD_BG
            canvas.drawCircle(px, py, dp(11f), pFill)
            pStroke.color = tint; pStroke.strokeWidth = dp(2f)
            val maxDur = NucleaGame.powerMaxDuration(t)
            canvas.drawArc(RectF(px - dp(11f), py - dp(11f), px + dp(11f), py + dp(11f)),
                -90f, 360f * (left / maxDur).coerceIn(0f, 1f), false, pStroke)
            pText.color = tint; pText.textSize = sp(11f)
            canvas.drawText(powerLabel(t), px, py + sp(4f), pText)
            px -= dp(28f)
        }
    }

    /** Pictogramme d'un bonus — mêmes symboles sur la pastille du HUD et au sol. */
    private fun powerLabel(t: PowerUpType): String = when (t) {
        PowerUpType.MAGNETIC -> "M"
        PowerUpType.TIME -> "T"
        PowerUpType.GAMMA -> "γ"
        PowerUpType.SHIELD -> "◆"
        PowerUpType.PULSAR -> "P"
        PowerUpType.SWIFT -> NucleaGame.antiSymbol(NucleaGame.ANTI_SWIFT)
        PowerUpType.THRUST -> NucleaGame.antiSymbol(NucleaGame.ANTI_THRUST)
    }

    private fun drawJoysticks(canvas: Canvas) {
        if (game.phase != NucleaPhase.PLAYING) return
        if (game.captured) return   // pendant la capture on ne pilote plus, on martèle
        if (moveId >= 0) drawStick(canvas, moveCx, moveCy, moveKx, moveKy, Color.WHITE)
        if (aimId >= 0) drawStick(canvas, aimCx, aimCy, aimKx, aimKy, Color.parseColor("#A0DCFF"))
    }

    /**
     * Écran de capture : le joueur est happé dans le trou noir. Deux jauges se
     * font face — la COHÉSION qui se vide toute seule, et l'ÉVASION qu'on remplit
     * en martelant. Tant que l'évasion gagne la course, on ressort vivant.
     */
    private fun drawCapture(canvas: Canvas) {
        if (!game.captured) return
        val w = width.toFloat(); val h = height.toFloat()

        // Assombrissement : tout le reste de l'arène passe au second plan
        pFill.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, pFill)

        // Spirales de marée qui convergent sur le joueur
        val kick = (game.captureKick / 0.12f).coerceIn(0f, 1f)
        pStroke.strokeWidth = dp(1.5f)
        for (k in 0 until 5) {
            val base = animT * 3f + k * 1.256f
            val rr = dp(70f) + dp(40f) * ((animT * 1.6f + k * 0.2f) % 1f)
            pStroke.color = Color.argb((110 - k * 14).coerceAtLeast(20), 255, 138, 60)
            canvas.drawArc(
                RectF(game.px - rr, game.py - rr, game.px + rr, game.py + rr),
                base * 57.3f % 360f, 110f, false, pStroke
            )
        }
        // Halo qui pulse à chaque appui : le retour visuel du martèlement
        if (kick > 0f) {
            pGlow.color = Color.argb((kick * 170).toInt(), 255, 233, 184)
            canvas.drawCircle(game.px, game.py, dp(26f) + dp(24f) * kick, pGlow)
        }

        // Titre + consigne
        pText.color = Color.parseColor("#FF8A3C"); pText.textSize = sp(30f)
        canvas.drawText(sCaptured, w / 2f, h * 0.2f, pText)
        val blink = (sin(animT * 8f) * 0.5f + 0.5f)
        pText.color = Color.argb((160 + blink * 95).toInt(), 255, 255, 255); pText.textSize = sp(15f)
        canvas.drawText(sCaptureHint, w / 2f, h * 0.2f + sp(26f), pText)

        // Jauge de COHÉSION : le compte à rebours avant l'écrasement
        val barW = w * 0.62f
        val barH = dp(14f)
        val bx = (w - barW) / 2f
        var by = h * 0.74f
        val coh = (game.captureCohesion / NucleaGame.CAPTURE_TIME).coerceIn(0f, 1f)
        pFill.color = Color.argb(180, 30, 10, 10)
        canvas.drawRect(bx, by, bx + barW, by + barH, pFill)
        pFill.color = Color.rgb(255, (60 + 150 * coh).toInt(), 60)
        canvas.drawRect(bx, by, bx + barW * coh, by + barH, pFill)
        pStroke.color = Color.argb(150, 255, 160, 120); pStroke.strokeWidth = dp(1.5f)
        canvas.drawRect(bx, by, bx + barW, by + barH, pStroke)

        // Jauge d'ÉVASION : elle monte à chaque appui
        by += barH + dp(10f)
        val esc = (game.capturePresses.toFloat() / game.captureNeeded).coerceIn(0f, 1f)
        pFill.color = Color.argb(180, 10, 25, 30)
        canvas.drawRect(bx, by, bx + barW, by + barH, pFill)
        pFill.color = C_GREEN
        canvas.drawRect(bx, by, bx + barW * esc, by + barH, pFill)
        pStroke.color = Color.argb(150, 126, 249, 200)
        canvas.drawRect(bx, by, bx + barW, by + barH, pStroke)
        pText.color = C_GREEN; pText.textSize = sp(13f)
        canvas.drawText("${game.capturePresses} / ${game.captureNeeded}",
            w / 2f, by + barH + sp(16f), pText)

        // Repère de martèlement : un point avec des ondes qui s'échappent, lisible
        // aussi bien pour un doigt (tapez n'importe où) que pour le bouton A d'une
        // manette. Il grossit à chaque appui, pour que le martèlement ait du punch.
        val cy = h * 0.55f
        val cr = dp(46f) + dp(10f) * kick + dp(4f) * blink
        pFill.color = Color.argb(70, 255, 233, 184)
        canvas.drawCircle(w / 2f, cy, cr, pFill)
        pStroke.color = Color.argb(230, 255, 233, 184); pStroke.strokeWidth = dp(3f)
        canvas.drawCircle(w / 2f, cy, cr, pStroke)
        pFill.color = Color.WHITE
        canvas.drawCircle(w / 2f, cy, dp(11f), pFill)
        // Ondes concentriques : le geste « tapoter ici »
        pStroke.strokeWidth = dp(2f)
        for (k in 0 until 2) {
            val t = ((animT * 1.8f + k * 0.5f) % 1f)
            pStroke.color = Color.argb(((1f - t) * 200).toInt(), 255, 233, 184)
            canvas.drawCircle(w / 2f, cy, dp(14f) + (cr - dp(14f)) * t, pStroke)
        }
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

        // Boutons : empilés en portrait, alignés sur une rangée en paysage (écran bas)
        val resumable = game.hasResumableRun()
        val row = w > h
        val bh = dp(46f)
        val bw = if (row) dp(150f) else dp(210f)
        val bgap = dp(12f)

        if (row) {
            val count = if (resumable) 3 else 2
            val totalW = count * bw + (count - 1) * bgap
            var bx = cx - totalW / 2f
            val by = h * 0.70f
            if (resumable) {
                resumeBtnRect.set(bx, by, bx + bw, by + bh); bx += bw + bgap
            } else resumeBtnRect.setEmpty()
            newBtnRect.set(bx, by, bx + bw, by + bh); bx += bw + bgap
            constBtnRect.set(bx, by, bx + bw, by + bh)
        } else {
            var by = h * (if (resumable) 0.58f else 0.63f)
            if (resumable) {
                resumeBtnRect.set(cx - bw / 2f, by, cx + bw / 2f, by + bh)
                by += bh + dp(30f)
            } else resumeBtnRect.setEmpty()
            newBtnRect.set(cx - bw / 2f, by, cx + bw / 2f, by + bh)
            by += bh + dp(14f)
            constBtnRect.set(cx - bw / 2f, by, cx + bw / 2f, by + bh)
        }

        if (resumable) {
            // Bouton principal : liseré vert qui respire pour attirer l'œil
            val pulse = (sin(animT * 3f) * 0.5f + 0.5f)
            pFill.color = C_BTN_BG
            canvas.drawRoundRect(resumeBtnRect, dp(10f), dp(10f), pFill)
            pStroke.color = Color.argb((160 + pulse * 95).toInt(), 126, 249, 200)
            pStroke.strokeWidth = dp(2f)
            canvas.drawRoundRect(resumeBtnRect, dp(10f), dp(10f), pStroke)
            pText.color = C_GREEN; pText.textSize = if (row) sp(13.5f) else sp(16f)
            canvas.drawText("▶ $sResume", resumeBtnRect.centerX(), resumeBtnRect.centerY() + sp(5f), pText)
            // Rappel de la vague où on s'est arrêté
            pText.color = C_GRAY; pText.textSize = sp(11.5f)
            canvas.drawText(
                ctx().getString(R.string.nuclea_wave_label, game.resumableWave()),
                resumeBtnRect.centerX(), resumeBtnRect.bottom + sp(15f), pText
            )
        }

        pFill.color = C_BTN_BG
        canvas.drawRoundRect(newBtnRect, dp(10f), dp(10f), pFill)
        pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(newBtnRect, dp(10f), dp(10f), pStroke)
        pText.color = Color.WHITE; pText.textSize = if (row) sp(13.5f) else sp(15f)
        canvas.drawText(sNewGame, newBtnRect.centerX(), newBtnRect.centerY() + sp(5f), pText)

        pFill.color = C_BTN_BG
        canvas.drawRoundRect(constBtnRect, dp(10f), dp(10f), pFill)
        pStroke.color = C_DUST; pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(constBtnRect, dp(10f), dp(10f), pStroke)
        pText.color = C_DUST; pText.textSize = if (row) sp(13.5f) else sp(15f)
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

        // Bouton « tout réinitialiser » : rembourse la poussière investie
        val refund = game.respecRefund()
        val confirming = animT < respecConfirmUntil
        val label = if (confirming) sRespecOk else "↺ $sRespec"
        val amount = if (refund > 0L && !confirming) "  +✦$refund" else ""
        pText.textSize = sp(12.5f)
        val bw = pText.measureText(label + amount) + dp(24f)
        respecBtnRect.set(w - dp(12f) - bw, dp(54f), w - dp(12f), dp(96f))
        if (refund > 0L) {
            pFill.color = if (confirming) Color.parseColor("#4A2A2A") else C_BTN_BG
            canvas.drawRoundRect(respecBtnRect, dp(10f), dp(10f), pFill)
            pStroke.color = if (confirming) C_GAMEOVER else C_BTN_BORDER
            pStroke.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(respecBtnRect, dp(10f), dp(10f), pStroke)
            val ty = respecBtnRect.centerY() + sp(4.5f)
            if (amount.isEmpty()) {
                pText.color = if (confirming) C_GAMEOVER else Color.WHITE
                canvas.drawText(label, respecBtnRect.centerX(), ty, pText)
            } else {
                // Libellé blanc + montant remboursé en doré, collés au centre du bouton
                val lw = pText.measureText(label)
                val aw = pText.measureText(amount)
                val x0 = respecBtnRect.centerX() - (lw + aw) / 2f
                pText.textAlign = Paint.Align.LEFT
                pText.color = Color.WHITE
                canvas.drawText(label, x0, ty, pText)
                pText.color = C_GOLD
                canvas.drawText(amount, x0 + lw, ty, pText)
                pText.textAlign = Paint.Align.CENTER
            }
        } else {
            respecBtnRect.setEmpty()
        }

        // Bouton « i » : bascule entre le ciel et la liste détaillée
        val infoR = dp(21f)
        val infoCx = (if (respecBtnRect.isEmpty) w - dp(12f) else respecBtnRect.left - dp(10f)) - infoR
        val infoCy = dp(54f) + infoR
        infoBtnRect.set(infoCx - infoR, infoCy - infoR, infoCx + infoR, infoCy + infoR)
        pFill.color = if (showInfo) C_DUST else C_BTN_BG
        canvas.drawCircle(infoCx, infoCy, infoR, pFill)
        pStroke.color = if (showInfo) C_DUST else C_BTN_BORDER
        pStroke.strokeWidth = dp(1.5f)
        canvas.drawCircle(infoCx, infoCy, infoR, pStroke)
        pText.color = if (showInfo) C_CARD_BG else Color.WHITE
        pText.textSize = sp(16f)
        canvas.drawText("i", infoCx, infoCy + sp(5.5f), pText)

        // Zone de ciel : chaque constellation est tracée à sa vraie forme
        val skyTop = dp(104f)
        val skyBottom = h - dp(96f)
        val skyH = skyBottom - skyTop
        val edge = dp(42f)   // marge pour que les libellés ne débordent pas de l'écran

        if (showInfo) {
            drawInfoPanel(canvas, w, skyTop, skyBottom)
            drawConstellationBanner(canvas, h)
            return
        }

        for (c in 0 until 3) {
            val sh = NucleaGame.SHAPES[c]
            val complete = game.constellationComplete(c)

            // Mise à l'échelle uniforme dans le cadre : la forme n'est jamais déformée
            val panel = if (w > h) sh.panelLand else sh.panel
            val pl = (panel[0] * w).coerceAtLeast(edge)
            val pt = skyTop + panel[1] * skyH
            val pr = (panel[2] * w).coerceAtMost(w - edge)
            val pb = skyTop + panel[3] * skyH
            val shW = sh.maxX - sh.minX
            val shH = sh.maxY - sh.minY
            val sc = min((pr - pl) / shW, (pb - pt) / shH)
            val ox = pl + ((pr - pl) - shW * sc) / 2f - sh.minX * sc
            val oy = pt + ((pb - pt) - shH * sc) / 2f - sh.minY * sc
            val count = sh.mags.size
            for (i in 0 until count) {
                starBuf[i * 2] = sh.stars[i * 2] * sc + ox
                starBuf[i * 2 + 1] = sh.stars[i * 2 + 1] * sc + oy
            }

            // Astérisme : les segments réels du dessin céleste
            pStroke.strokeWidth = dp(1.5f)
            pStroke.color = if (complete) Color.argb(180, 255, 224, 102) else Color.argb(70, 203, 184, 255)
            var k = 0
            while (k < sh.lines.size) {
                val a = sh.lines[k]; val b = sh.lines[k + 1]
                canvas.drawLine(starBuf[a * 2], starBuf[a * 2 + 1], starBuf[b * 2], starBuf[b * 2 + 1], pStroke)
                k += 2
            }

            // Étoiles décoratives : celles qui ne portent pas d'amélioration
            for (i in 0 until count) {
                if (sh.nodeStars.contains(i)) continue
                val mag = sh.mags[i]
                val r = dp(1.4f) + dp(2.4f) * mag
                val x = starBuf[i * 2]; val y = starBuf[i * 2 + 1]
                pGlow.color = Color.argb((35 + 65 * mag).toInt(), 203, 184, 255)
                canvas.drawCircle(x, y, r * 2.4f, pGlow)
                pFill.color = Color.argb((140 + 115 * mag).toInt(), 235, 232, 255)
                canvas.drawCircle(x, y, r, pFill)
            }

            // Nom de la constellation, sous son tracé
            pText.textSize = sp(12f)
            pText.color = if (complete) C_GOLD else C_GRAY
            canvas.drawText(
                ctx().getString(NucleaGame.CONSTELLATION_NAMES[c]),
                ox + (sh.minX + shW / 2f) * sc,
                min(oy + sh.maxY * sc + dp(54f), skyBottom - dp(2f)), pText
            )

            // Étoiles-nœuds : les 3 améliorations, posées sur de vraies étoiles
            var slot = 0
            NucleaGame.META_NODES.forEachIndexed { gi, n ->
                if (n.constellation != c) return@forEachIndexed
                val star = sh.nodeStars[slot]; slot++
                val x = starBuf[star * 2]; val y = starBuf[star * 2 + 1]
                val lvl = game.meta.lvl(n.key)
                val maxed = lvl >= n.maxLvl
                val cost = game.nodeCost(n)
                val affordable = !maxed && game.meta.dust >= cost

                nodeRects[gi].set(x - dp(30f), y - dp(30f), x + dp(30f), y + dp(38f))

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
        }

        drawConstellationBanner(canvas, h)
    }

    /**
     * Liste détaillée des améliorations : niveau, effet actuel et effet au niveau suivant.
     * En paysage les trois constellations passent en colonnes, sinon elles s'empilent.
     */
    private fun drawInfoPanel(canvas: Canvas, w: Float, skyTop: Float, skyBottom: Float) {
        val rowH = dp(24f)
        val headH = dp(30f)
        val blockH = headH + 3 * rowH
        // Empilé si le ciel est assez haut, sinon en trois colonnes (paysage, écran partagé)
        val cols = if (skyBottom - skyTop >= 3 * blockH + dp(46f)) 1 else 3
        val panelH = (if (cols == 3) blockH else 3 * blockH) + dp(46f)
        val panelW = w - dp(24f)
        val top = skyTop + ((skyBottom - skyTop) - panelH).coerceAtLeast(0f) / 2f
        val panel = RectF(dp(12f), top, dp(12f) + panelW, top + panelH)

        pFill.color = C_CARD_BG
        canvas.drawRoundRect(panel, dp(14f), dp(14f), pFill)
        pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(panel, dp(14f), dp(14f), pStroke)

        pText.color = C_DUST; pText.textSize = sp(14f)
        canvas.drawText(sDetails, panel.centerX(), panel.top + dp(24f), pText)

        val colW = (panelW - dp(20f)) / cols
        for (c in 0 until 3) {
            val colX = panel.left + dp(10f) + (if (cols == 3) c * colW else 0f)
            var y = panel.top + dp(38f) + (if (cols == 3) 0f else c * blockH)
            val complete = game.constellationComplete(c)

            // Titre de la constellation
            pTextL.textSize = sp(12.5f)
            pTextL.color = if (complete) C_GOLD else C_GRAY
            canvas.drawText(
                (if (complete) "★ " else "☆ ") + ctx().getString(NucleaGame.CONSTELLATION_NAMES[c]),
                colX, y + sp(12f), pTextL
            )
            y += headH

            for (n in NucleaGame.META_NODES) {
                if (n.constellation != c) continue
                val lvl = game.meta.lvl(n.key)
                val maxed = lvl >= n.maxLvl
                val baseline = y + sp(11f)

                // Nom de l'amélioration + niveau
                pTextL.textSize = sp(11.5f)
                pTextL.color = if (lvl > 0) Color.WHITE else C_GRAY
                canvas.drawText(ctx().getString(n.labelRes), colX + dp(4f), baseline, pTextL)
                pTextL.color = C_GRAY
                canvas.drawText("$lvl/${n.maxLvl}", colX + colW * 0.42f, baseline, pTextL)

                // Effet actuel → effet au niveau suivant (ou coût du prochain niveau)
                val cur = game.nodeEffect(n.key, lvl)
                val next = if (maxed) "" else "  →  " + game.nodeEffect(n.key, lvl + 1)
                val right = colX + colW - dp(12f)
                pTextL.textAlign = Paint.Align.RIGHT
                if (maxed) {
                    pTextL.color = C_GOLD
                    canvas.drawText(cur, right, baseline, pTextL)
                } else {
                    pTextL.color = C_GOLD
                    canvas.drawText(next, right, baseline, pTextL)
                    pTextL.color = Color.WHITE
                    canvas.drawText(cur, right - pTextL.measureText(next), baseline, pTextL)
                }
                pTextL.textAlign = Paint.Align.LEFT
                y += rowH
            }
        }

        // Rappel : un appui n'importe où referme le panneau
        pText.color = C_GRAY; pText.textSize = sp(10.5f)
        canvas.drawText("✕", panel.right - dp(16f), panel.top + dp(24f), pText)
    }

    /** Bandeau du bas : rappel des bonus de constellation complète. */
    private fun drawConstellationBanner(canvas: Canvas, h: Float) {
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
        // Reprendre en large, puis Nouvelle partie / Quitter côte à côte
        val bw = dp(220f); val bh = dp(44f)
        btnRect1.set(cx - bw / 2f, cy + dp(20f), cx + bw / 2f, cy + dp(20f) + bh)
        drawButton(canvas, btnRect1, sResume, C_GREEN)
        val sw = dp(106f); val gap = dp(8f)
        val y2 = btnRect1.bottom + dp(12f)
        btnRect2.set(cx - sw - gap / 2f, y2, cx - gap / 2f, y2 + bh)
        btnRect3.set(cx + gap / 2f, y2, cx + sw + gap / 2f, y2 + bh)
        drawButton(canvas, btnRect2, sNewGame, Color.WHITE)
        drawButton(canvas, btnRect3, sQuit, Color.WHITE)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, tint: Int) {
        pFill.color = C_BTN_BG
        canvas.drawRoundRect(rect, dp(10f), dp(10f), pFill)
        pStroke.color = if (tint == Color.WHITE) C_BTN_BORDER else tint
        pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(rect, dp(10f), dp(10f), pStroke)
        pText.color = tint
        pText.textSize = if (rect.width() < dp(130f)) sp(12.5f) else sp(15f)
        canvas.drawText(label, rect.centerX(), rect.centerY() + sp(5f), pText)
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

    // ─── Util ─────────────────────────────────────────────────────────────────

    private fun ctx(): Context = context
    private fun dp(v: Float) = v * _dp
    private fun sp(v: Float) = v * _sp
}
