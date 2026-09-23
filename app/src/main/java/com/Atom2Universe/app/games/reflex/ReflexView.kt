package com.Atom2Universe.app.games.reflex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Affichage du Collisionneur : fond, particules, effets, bandeau et
 * écrans (menu, pause, fin de partie), tout dessiné sur la même toile.
 *
 * Un seul fil touche la partie : le fil de jeu. Le fil de l'interface ne fait que
 * **noter** les appuis ([onTouchEvent]) ; le fil de jeu les rejoue à l'image
 * suivante, avec l'heure réelle du doigt pour que le jugement reste juste.
 */
class ReflexView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable, ReflexListener {

    /** Ce que l'activité apprend d'une partie finie (sur le fil de jeu). */
    var onGameFinished: ((score: Long, bestCombo: Int) -> Unit)? = null

    /** Record connu, fourni par l'activité ; sert à annoncer un nouveau record. */
    @Volatile var bestScore = 0L

    var sound: ReflexSoundEngine? = null

    val game = ReflexGame().also { it.listener = this }

    // ── Fil de jeu ───────────────────────────────────────────────────────────
    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    /** Voir NucleaView.lockFrame : un refus du canevas matériel vaut pour toujours. */
    private var hardwareCanvas = true

    // Appuis notés par le fil de l'interface : x, y, heure de l'appui.
    private val tapLock = Any()
    private val tapX = FloatArray(16)
    private val tapY = FloatArray(16)
    private val tapTime = LongArray(16)
    private var tapCount = 0

    // ── Mesures ──────────────────────────────────────────────────────────────
    private val dp = resources.displayMetrics.density
    private val sp = dp * resources.configuration.fontScale
    private var w = 1f
    private var h = 1f
    private val hudHeight = 76f * dp
    private val heatBarY get() = h - 22f * dp

    // ── Textes (lus une fois, jamais en dur) ─────────────────────────────────
    private val numberFormat = NumberFormat.getIntegerInstance()
    private val txtTitle = ctx.getString(R.string.reflex_title).uppercase()
    private val txtPlay = ctx.getString(R.string.reflex_start)
    private val txtReplay = ctx.getString(R.string.reflex_replay)
    private val txtMenu = ctx.getString(R.string.reflex_menu)
    private val txtResume = ctx.getString(R.string.reflex_resume)
    private val txtPaused = ctx.getString(R.string.reflex_paused)
    private val txtGameOver = ctx.getString(R.string.reflex_game_over)
    private val txtNewRecord = ctx.getString(R.string.reflex_new_record)
    private val txtOverheat = ctx.getString(R.string.reflex_overheat)
    private val txtPerfect = ctx.getString(R.string.reflex_grade_perfect)
    private val txtGood = ctx.getString(R.string.reflex_grade_good)
    private val txtEarly = ctx.getString(R.string.reflex_grade_early)
    private val txtMiss = ctx.getString(R.string.reflex_grade_miss)
    private val txtLifeUp = ctx.getString(R.string.reflex_life_up)
    private val kindNames = ParticleKind.entries.map {
        ctx.getString(
            when (it) {
                ParticleKind.PROTON -> R.string.reflex_kind_proton
                ParticleKind.ANTI -> R.string.reflex_kind_anti
                ParticleKind.ELECTRON -> R.string.reflex_kind_electron
                ParticleKind.NUCLEUS -> R.string.reflex_kind_nucleus
                ParticleKind.CHAIN -> R.string.reflex_kind_chain
            }
        )
    }
    private val chainDigits = (1..ReflexGame.CHAIN_LENGTH).map { numberFormat.format(it) }
    private fun fmt(n: Long): String = numberFormat.format(n)
    private var bestCache = -1L
    private var bestStr = ""
    private fun bestLine(): String {
        if (bestScore != bestCache) { bestCache = bestScore; bestStr = context.getString(R.string.reflex_best_format, fmt(bestScore)) }
        return bestStr
    }
    private fun comboLine(c: Int) = context.getString(R.string.reflex_combo_format, c)
    private fun multLine(m: Int) = context.getString(R.string.reflex_mult_format, m)
    private fun pointsLine(p: Int) = context.getString(R.string.reflex_points_format, p)

    // ── Pinceaux (aucune allocation dans la boucle) ──────────────────────────
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()
    private val crackLine = FloatArray(16)

    private val sprites = arrayOfNulls<Bitmap>(ParticleKind.entries.size)
    /** Rayon du cœur dans les images cuites ; l'éclat s'étend jusqu'à [glow] fois ce rayon. */
    private val spriteR = 56f * dp

    // ── Effets ───────────────────────────────────────────────────────────────
    private val maxSparks = 420
    private val sx = FloatArray(maxSparks); private val sy = FloatArray(maxSparks)
    private val svx = FloatArray(maxSparks); private val svy = FloatArray(maxSparks)
    private val sLife = FloatArray(maxSparks); private val sMax = FloatArray(maxSparks)
    private val sColor = IntArray(maxSparks)
    private var sparkCursor = 0

    private val maxWaves = 24
    private val wx = FloatArray(maxWaves); private val wy = FloatArray(maxWaves)
    private val wr0 = FloatArray(maxWaves); private val wr1 = FloatArray(maxWaves)
    private val wt = FloatArray(maxWaves); private val wDur = FloatArray(maxWaves)
    private val wColor = IntArray(maxWaves)
    private var waveCursor = 0

    private val maxTexts = 20
    private val tStr = arrayOfNulls<String>(maxTexts)
    private val tx = FloatArray(maxTexts); private val ty = FloatArray(maxTexts)
    private val tt = FloatArray(maxTexts); private val tDur = FloatArray(maxTexts)
    private val tColor = IntArray(maxTexts); private val tSize = FloatArray(maxTexts)
    private var textCursor = 0

    private var shake = 0f
    private var redFlash = 0f
    private var whiteFlash = 0f
    private var comboPop = 0f
    private var lifePop = 0f
    private var shownScore = 0f
    private var animT = 0f
    private var collider = 0f
    private var unlockKind: ParticleKind? = null
    private var unlockT = 0f
    /** Petite attente avant que l'écran de fin accepte un appui (pas de relance par erreur). */
    private var overlayGuard = 0f
    private var lastWasRecord = false
    private var lastNeutrinos = 0
    private var endComboStr = ""
    private var endPerfectStr = ""
    private var endNeutrinoStr = ""
    private var brokenChain = -1
    private var brokenChainT = 0f
    private val shakeRng = Random(11)

    // Étoiles : position (0..1), phase de scintillement, vitesse de chute.
    private val starX = FloatArray(80) { Random(it).nextFloat() }
    private val starY = FloatArray(80) { Random(it + 500).nextFloat() }
    private val starPhase = FloatArray(80) { Random(it + 900).nextFloat() * 6.28f }
    private val starSpeed = FloatArray(80) { 0.004f + Random(it + 1300).nextFloat() * 0.012f }

    // ── Boutons ──────────────────────────────────────────────────────────────
    private val btnMain = RectF()
    private val btnSecond = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cycle de vie
    // ═══════════════════════════════════════════════════════════════════════

    override fun surfaceCreated(h: SurfaceHolder) { if (!running) start() }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // La toile appartient au fil de jeu : on l'arrête le temps de tout remesurer.
        stop()
        w = width.toFloat()
        h = height.toFloat()
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(0xFF080818.toInt(), 0xFF0E0A26.toInt(), 0xFF170A22.toInt()),
            null, Shader.TileMode.CLAMP)
        game.setField(16f * dp, hudHeight, w - 16f * dp, heatBarY - 18f * dp, dp)
        if (sprites[0] == null) bakeSprites()
        start()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stop() }

    /** Appelé par l'activité en arrière-plan : on arrête le fil puis on met en pause. */
    fun pause() {
        stop()
        if (game.phase == ReflexPhase.PLAYING) game.phase = ReflexPhase.PAUSED
    }

    fun resume() { if (!running && holder.surface?.isValid == true) start() }

    /** Retour système : l'activité demande, le fil de jeu décide. */
    @Volatile private var pendingBack = false
    fun requestBack(): Boolean {
        if (game.phase == ReflexPhase.MENU) return false
        pendingBack = true
        return true
    }

    private fun start() {
        if (running && thread?.isAlive == true) return
        running = true
        lastNanos = System.nanoTime()
        thread = Thread(this, "ReflexGame").also { it.start() }
    }

    private fun stop() {
        running = false
        val t = thread
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(500) } catch (_: InterruptedException) {}
        }
        thread = null
    }

    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try { return holder.lockHardwareCanvas() } catch (_: Throwable) { hardwareCanvas = false }
        }
        return holder.lockCanvas()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Boucle
    // ═══════════════════════════════════════════════════════════════════════

    override fun run() {
        val me = Thread.currentThread()
        while (running && me === thread) {
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = now

            if (pendingBack) { pendingBack = false; handleBack() }
            consumeTaps()
            game.update(dt)
            updateEffects(dt)

            val canvas = lockFrame()
            if (canvas != null) {
                try { render(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            }

            // 60 images par seconde suffisent : le jugement se fait sur l'heure du doigt.
            val elapsed = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
        }
    }

    private fun handleBack() {
        when (game.phase) {
            ReflexPhase.PLAYING -> game.phase = ReflexPhase.PAUSED
            ReflexPhase.PAUSED, ReflexPhase.GAME_OVER -> goToMenu()
            ReflexPhase.MENU -> Unit
        }
    }

    private fun goToMenu() {
        game.particles.clear()
        game.phase = ReflexPhase.MENU
        unlockKind = null
    }

    private fun startGame() {
        sound?.onButton()
        game.start()
        shownScore = 0f
        unlockKind = null
        brokenChain = -1
        clearEffects()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Appuis
    // ═══════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val i = ev.actionIndex
            synchronized(tapLock) {
                if (tapCount < tapX.size) {
                    tapX[tapCount] = ev.getX(i)
                    tapY[tapCount] = ev.getY(i)
                    tapTime[tapCount] = ev.eventTime
                    tapCount++
                }
            }
        }
        return true
    }

    private val pendX = FloatArray(16)
    private val pendY = FloatArray(16)
    private val pendT = LongArray(16)

    private fun consumeTaps() {
        val n: Int
        synchronized(tapLock) {
            n = tapCount
            System.arraycopy(tapX, 0, pendX, 0, n)
            System.arraycopy(tapY, 0, pendY, 0, n)
            System.arraycopy(tapTime, 0, pendT, 0, n)
            tapCount = 0
        }
        if (n == 0) return
        val uptime = SystemClock.uptimeMillis()
        val phaseBefore = game.phase
        for (i in 0 until n) {
            val x = pendX[i]
            val y = pendY[i]
            when (game.phase) {
                ReflexPhase.PLAYING -> game.tap(x, y, (uptime - pendT[i]) / 1000f)
                ReflexPhase.MENU -> if (btnMain.contains(x, y)) startGame()
                ReflexPhase.PAUSED -> when {
                    btnMain.contains(x, y) -> { sound?.onButton(); game.phase = ReflexPhase.PLAYING }
                    btnSecond.contains(x, y) -> { sound?.onButton(); goToMenu() }
                }
                ReflexPhase.GAME_OVER -> if (overlayGuard <= 0f) when {
                    btnMain.contains(x, y) -> startGame()
                    btnSecond.contains(x, y) -> { sound?.onButton(); goToMenu() }
                }
            }
            // Un appui qui change d'écran ne se prolonge pas sur le suivant.
            if (game.phase != phaseBefore) break
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Événements de la partie → effets et sons
    // ═══════════════════════════════════════════════════════════════════════

    override fun onHit(p: Particle, grade: HitGrade, points: Int) {
        val c = p.kind.color
        when (grade) {
            HitGrade.PERFECT -> {
                burst(p.x, p.y, c, 26, 420f)
                burst(p.x, p.y, Color.WHITE, 8, 260f)
                wave(p.x, p.y, p.radius, p.radius * 2.8f, 0.35f, c)
                wave(p.x, p.y, p.radius * 0.6f, p.radius * 1.8f, 0.22f, Color.WHITE)
                floatText(txtPerfect, p.x, p.y - p.radius * 0.6f, 0xFFFFF59D.toInt(), 20f)
                shake = maxOf(shake, 2.5f * dp)
            }
            HitGrade.GOOD -> {
                burst(p.x, p.y, c, 16, 320f)
                wave(p.x, p.y, p.radius, p.radius * 2.2f, 0.3f, c)
                floatText(txtGood, p.x, p.y - p.radius * 0.6f, 0xFFB3E5FC.toInt(), 17f)
            }
            HitGrade.EARLY -> {
                burst(p.x, p.y, c, 8, 200f)
                floatText(txtEarly, p.x, p.y - p.radius * 0.6f, 0xFFB0BEC5.toInt(), 15f)
            }
        }
        floatText(pointsLine(points), p.x, p.y + p.radius * 0.5f, c, 13f)
        if (grade != HitGrade.EARLY) comboPop = 1f
        sound?.onHit(grade, game.combo)
    }

    override fun onCrack(p: Particle) {
        burst(p.x, p.y, p.kind.color, 10, 240f)
        wave(p.x, p.y, p.radius, p.radius * 1.6f, 0.2f, p.kind.color)
        shake = maxOf(shake, 1.5f * dp)
        sound?.onCrack()
    }

    override fun onMiss(p: Particle) {
        burst(p.x, p.y, 0xFF78909C.toInt(), 10, 120f)
        floatText(txtMiss, p.x, p.y, 0xFFFF8A80.toInt(), 16f)
        if (p.kind == ParticleKind.CHAIN) { brokenChain = p.chainId; brokenChainT = 0.4f }
        loseLifeFx()
        sound?.onMiss()
    }

    override fun onAntiTouched(p: Particle) {
        burst(p.x, p.y, p.kind.color, 40, 520f)
        burst(p.x, p.y, 0xFF1A0008.toInt(), 14, 300f)
        wave(p.x, p.y, p.radius, p.radius * 4f, 0.45f, p.kind.color)
        loseLifeFx()
        shake = 14f * dp
        redFlash = 1f
        sound?.onAntiTouched()
    }

    override fun onAntiFaded(p: Particle) {
        wave(p.x, p.y, p.radius * 0.9f, p.radius * 0.2f, 0.25f, p.kind.color)
    }

    override fun onEmptyTap(x: Float, y: Float) {
        wave(x, y, 4f * dp, 18f * dp, 0.2f, 0x80FFFFFF.toInt())
        sound?.onEmptyTap()
    }

    override fun onWrongOrder(p: Particle) {
        brokenChain = p.chainId
        brokenChainT = 0.35f
        shake = maxOf(shake, 4f * dp)
        sound?.onWrongOrder()
    }

    override fun onComboBroken(combo: Int) {
        if (combo >= 10) shake = maxOf(shake, 5f * dp)
    }

    override fun onLifeRegained() {
        lifePop = 1f
        floatText(txtLifeUp, w - 60f * dp, hudHeight * 0.75f, 0xFF80DEEA.toInt(), 16f)
        sound?.onLifeRegained()
    }

    override fun onOverheatStart() {
        whiteFlash = 0.6f
        shake = maxOf(shake, 8f * dp)
        wave(w / 2, h / 2, 20f * dp, maxOf(w, h), 0.6f, 0xFFFF9800.toInt())
        sound?.onOverheatStart()
    }

    override fun onUnlock(kind: ParticleKind) {
        unlockKind = kind
        unlockT = 0f
        sound?.onUnlock()
    }

    override fun onGameOver() {
        for (p in game.particles) burst(p.x, p.y, p.kind.color, 12, 300f)
        lastWasRecord = game.score > bestScore && game.score > 0
        lastNeutrinos = NeutrinoRewards.reflex(game.score)
        if (lastWasRecord) bestScore = game.score
        endComboStr = context.getString(R.string.reflex_stat_combo, game.bestCombo)
        endPerfectStr = context.getString(R.string.reflex_stat_perfects, game.perfects)
        endNeutrinoStr = context.getString(R.string.reflex_stat_neutrinos, lastNeutrinos)
        overlayGuard = 0.8f
        redFlash = 0.8f
        sound?.onGameOver()
        onGameFinished?.invoke(game.score, game.bestCombo)
    }

    private fun loseLifeFx() {
        shake = maxOf(shake, 9f * dp)
        redFlash = maxOf(redFlash, 0.55f)
        lifePop = -1f
        post { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Effets
    // ═══════════════════════════════════════════════════════════════════════

    private fun burst(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        repeat(count) {
            val i = sparkCursor
            sparkCursor = (sparkCursor + 1) % maxSparks
            val a = shakeRng.nextFloat() * 2f * PI.toFloat()
            val v = speed * dp * (0.35f + shakeRng.nextFloat() * 0.65f)
            sx[i] = x; sy[i] = y
            svx[i] = cos(a) * v; svy[i] = sin(a) * v
            sMax[i] = 0.35f + shakeRng.nextFloat() * 0.35f
            sLife[i] = sMax[i]
            sColor[i] = color
        }
    }

    private fun wave(x: Float, y: Float, r0: Float, r1: Float, dur: Float, color: Int) {
        val i = waveCursor
        waveCursor = (waveCursor + 1) % maxWaves
        wx[i] = x; wy[i] = y; wr0[i] = r0; wr1[i] = r1
        wt[i] = 0f; wDur[i] = dur; wColor[i] = color
    }

    private fun floatText(s: String, x: Float, y: Float, color: Int, sizeSp: Float) {
        val i = textCursor
        textCursor = (textCursor + 1) % maxTexts
        tStr[i] = s; tx[i] = x; ty[i] = y
        tt[i] = 0f; tDur[i] = 0.75f; tColor[i] = color; tSize[i] = sizeSp * sp
    }

    private fun clearEffects() {
        sLife.fill(0f)
        for (i in 0 until maxWaves) wDur[i] = 0f
        for (i in 0 until maxTexts) tStr[i] = null
        shake = 0f; redFlash = 0f; whiteFlash = 0f
    }

    private fun updateEffects(dt: Float) {
        animT += dt
        val drag = exp(-3.2f * dt)
        for (i in 0 until maxSparks) {
            if (sLife[i] <= 0f) continue
            sLife[i] -= dt
            svx[i] *= drag; svy[i] *= drag
            sx[i] += svx[i] * dt; sy[i] += svy[i] * dt
        }
        for (i in 0 until maxWaves) if (wDur[i] > 0f) {
            wt[i] += dt
            if (wt[i] >= wDur[i]) wDur[i] = 0f
        }
        for (i in 0 until maxTexts) if (tStr[i] != null) {
            tt[i] += dt
            if (tt[i] >= tDur[i]) tStr[i] = null
        }
        shake *= exp(-9f * dt)
        redFlash = (redFlash - dt * 2.2f).coerceAtLeast(0f)
        whiteFlash = (whiteFlash - dt * 2.5f).coerceAtLeast(0f)
        comboPop = (comboPop - dt * 5f).coerceAtLeast(0f)
        lifePop = if (lifePop > 0f) (lifePop - dt * 3f).coerceAtLeast(0f)
                  else (lifePop + dt * 3f).coerceAtMost(0f)
        brokenChainT = (brokenChainT - dt).coerceAtLeast(0f)
        overlayGuard = (overlayGuard - dt).coerceAtLeast(0f)
        if (unlockKind != null) {
            unlockT += dt
            if (unlockT > 2.4f) unlockKind = null
        }
        // Le score affiché rattrape le vrai à vitesse constante en secondes.
        shownScore += (game.score - shownScore) * (1f - exp(-12f * dt))
        if (abs(game.score - shownScore) < 1f) shownScore = game.score.toFloat()
        // Le collisionneur tourne plus vite avec la difficulté, et s'emballe en surchauffe.
        val spin = 0.25f + game.difficulty() * 0.9f + (if (game.isOverheating) 2.5f else 0f)
        collider += spin * dt
        for (i in starY.indices) {
            val speed = starSpeed[i] * (if (game.isOverheating) 9f else 1f)
            starY[i] += speed * dt * 4f
            if (starY[i] > 1f) { starY[i] -= 1f; starX[i] = shakeRng.nextFloat() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Images cuites des particules
    // ═══════════════════════════════════════════════════════════════════════

    private val glow = 1.9f

    private companion object {
        /** Zigzag de la fêlure, en fractions du rayon (x, y alternés). */
        val CRACK_POINTS = floatArrayOf(-0.75f, -0.35f, -0.25f, 0.05f, 0.1f, -0.2f, 0.45f, 0.25f, 0.8f, 0.1f)
    }

    private fun bakeSprites() {
        for (kind in ParticleKind.entries) sprites[kind.ordinal] = bake(kind)
    }

    private fun bake(kind: ParticleKind): Bitmap {
        val r = spriteR
        val size = (r * glow * 2f).toInt() + 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val color = kind.color
        fun a(col: Int, alpha: Int) = (col and 0x00FFFFFF) or (alpha shl 24)
        fun shade(col: Int, f: Float) = Color.rgb(
            (Color.red(col) * f).toInt().coerceIn(0, 255),
            (Color.green(col) * f).toInt().coerceIn(0, 255),
            (Color.blue(col) * f).toInt().coerceIn(0, 255))
        fun tint(col: Int, f: Float) = Color.rgb(
            (Color.red(col) + (255 - Color.red(col)) * f).toInt(),
            (Color.green(col) + (255 - Color.green(col)) * f).toInt(),
            (Color.blue(col) + (255 - Color.blue(col)) * f).toInt())

        // Halo
        p.shader = RadialGradient(cx, cx, r * glow,
            intArrayOf(a(color, 0xA0), a(color, 0x40), a(color, 0)),
            floatArrayOf(0.35f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, r * glow, p)

        // Piquants de l'antimatière : elle se reconnaît à sa silhouette, pas seulement à sa couleur.
        if (kind == ParticleKind.ANTI) {
            p.shader = null
            p.color = color
            val path = Path()
            val spikes = 10
            for (i in 0 until spikes) {
                val ang = i * 2f * PI.toFloat() / spikes
                val half = PI.toFloat() / spikes * 0.45f
                path.moveTo(cx + cos(ang - half) * r * 0.92f, cx + sin(ang - half) * r * 0.92f)
                path.lineTo(cx + cos(ang) * r * 1.38f, cx + sin(ang) * r * 1.38f)
                path.lineTo(cx + cos(ang + half) * r * 0.92f, cx + sin(ang + half) * r * 0.92f)
                path.close()
            }
            c.drawPath(path, p)
        }

        // Cœur
        val coreColors = if (kind == ParticleKind.ANTI)
            intArrayOf(0xFFFF6A95.toInt(), 0xFF7A0026.toInt(), 0xFF22000A.toInt())
        else intArrayOf(tint(color, 0.55f), color, shade(color, 0.45f))
        p.shader = RadialGradient(cx - r * 0.3f, cx - r * 0.3f, r * 1.35f,
            coreColors, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, r, p)
        p.shader = null

        // Liseré
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        p.color = if (kind == ParticleKind.ANTI) color else 0x90FFFFFF.toInt()
        c.drawCircle(cx, cx, r * 0.96f, p)
        p.style = Paint.Style.FILL

        when (kind) {
            ParticleKind.PROTON, ParticleKind.ELECTRON -> {
                // Proton « + », électron « − » : la charge, comme sur un schéma d'atome.
                p.color = 0xC0FFFFFF.toInt()
                val t = r * 0.11f
                val l = r * 0.36f
                c.drawRect(cx - l, cx - t, cx + l, cx + t, p)
                if (kind == ParticleKind.PROTON) c.drawRect(cx - t, cx - l, cx + t, cx + l, p)
            }
            ParticleKind.ANTI -> {
                // Barre de l'interdit.
                p.color = 0xE0FFFFFF.toInt()
                c.save()
                c.rotate(-45f, cx, cx)
                c.drawRect(cx - r * 0.55f, cx - r * 0.1f, cx + r * 0.55f, cx + r * 0.1f, p)
                c.restore()
            }
            ParticleKind.NUCLEUS -> {
                p.color = 0xFFFF7043.toInt()
                c.drawCircle(cx - r * 0.28f, cx + r * 0.12f, r * 0.34f, p)
                p.color = 0xFFFFF3C4.toInt()
                c.drawCircle(cx + r * 0.26f, cx - r * 0.14f, r * 0.34f, p)
            }
            ParticleKind.CHAIN -> Unit
        }

        // Reflet
        p.color = 0x60FFFFFF
        c.drawCircle(cx - r * 0.38f, cx - r * 0.38f, r * 0.24f, p)
        return bmp
    }

    private fun drawSprite(canvas: Canvas, kind: ParticleKind, x: Float, y: Float, r: Float, alpha: Int) {
        val bmp = sprites[kind.ordinal] ?: return
        val half = r * glow
        rect.set(x - half, y - half, x + half, y + half)
        spritePaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, rect, spritePaint)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rendu
    // ═══════════════════════════════════════════════════════════════════════

    private fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.save()
        if (shake > 0.3f) {
            canvas.translate((shakeRng.nextFloat() - 0.5f) * 2f * shake,
                (shakeRng.nextFloat() - 0.5f) * 2f * shake)
        }
        drawBackground(canvas)
        when (game.phase) {
            ReflexPhase.MENU -> drawMenuDecor(canvas)
            else -> drawParticles(canvas)
        }
        drawEffects(canvas)
        canvas.restore()

        if (game.phase != ReflexPhase.MENU) drawHud(canvas)
        drawUnlockBanner(canvas)

        when (game.phase) {
            ReflexPhase.MENU -> drawMenu(canvas)
            ReflexPhase.PAUSED -> drawPause(canvas)
            ReflexPhase.GAME_OVER -> drawGameOver(canvas)
            ReflexPhase.PLAYING -> Unit
        }

        if (redFlash > 0f) {
            fill.color = Color.argb((redFlash * 110).toInt(), 255, 30, 60)
            canvas.drawRect(0f, 0f, w, h, fill)
        }
        if (whiteFlash > 0f) {
            fill.color = Color.argb((whiteFlash * 150).toInt(), 255, 236, 200)
            canvas.drawRect(0f, 0f, w, h, fill)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val hot = game.isOverheating
        // Étoiles, étirées en traits quand ça surchauffe.
        for (i in starX.indices) {
            val x = starX[i] * w
            val y = starY[i] * h
            val tw = 0.45f + 0.55f * (0.5f + 0.5f * sin(animT * 2.2f + starPhase[i]))
            if (hot) {
                stroke.strokeWidth = 1.4f * dp
                stroke.color = Color.argb((tw * 170).toInt(), 255, 200, 140)
                canvas.drawLine(x, y, x, y - 26f * dp, stroke)
            } else {
                fill.color = Color.argb((tw * 170).toInt(), 200, 210, 255)
                canvas.drawCircle(x, y, (0.7f + tw) * dp, fill)
            }
        }

        // Anneaux du collisionneur au centre du terrain.
        val cx = w / 2f
        val cy = (hudHeight + heatBarY) / 2f
        val base = min(w, heatBarY - hudHeight) * 0.5f
        val ringColor = if (hot) 0xFFFF9800.toInt() else 0xFF5C6BC0.toInt()
        stroke.strokeWidth = 1f * dp
        for (k in 1..3) {
            stroke.color = (ringColor and 0x00FFFFFF) or (0x22 shl 24)
            canvas.drawCircle(cx, cy, base * (0.3f + 0.25f * k), stroke)
        }
        stroke.strokeWidth = 2.5f * dp
        stroke.strokeCap = Paint.Cap.ROUND
        for (k in 1..3) {
            val rr = base * (0.3f + 0.25f * k)
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            val dir = if (k % 2 == 0) -1f else 1f
            val start = (collider * 57.3f * dir * (1f + k * 0.3f)) % 360f
            stroke.color = (ringColor and 0x00FFFFFF) or (0x55 shl 24)
            canvas.drawArc(rect, start, 38f, false, stroke)
            canvas.drawArc(rect, start + 180f, 22f, false, stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT

        if (hot) {
            val pulse = 0.5f + 0.5f * sin(animT * 9f)
            fill.color = Color.argb((22 + pulse * 22).toInt(), 255, 120, 20)
            canvas.drawRect(0f, 0f, w, h, fill)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val now = game.time
        val list = game.particles

        // Liens des chaînes, d'abord, pour passer sous les particules.
        for (i in list.indices) {
            val a = list[i]
            if (!a.alive || a.kind != ParticleKind.CHAIN) continue
            for (j in list.indices) {
                val b = list[j]
                if (!b.alive || b.chainId != a.chainId || b.chainIndex != a.chainIndex + 1) continue
                val broken = brokenChainT > 0f && brokenChain == a.chainId
                stroke.strokeWidth = 3f * dp
                stroke.color = if (broken) 0xC0FF5252.toInt() else 0x70B388FF
                canvas.drawLine(a.x, a.y, b.x, b.y, stroke)
            }
        }

        for (p in list) {
            if (!p.alive) continue
            val age = now - p.ringStart
            val pop = (age / 0.12f).coerceIn(0f, 1f)
            val grow = 0.6f + 0.4f * (1f - (1f - pop) * (1f - pop))
            var r = p.radius * grow
            val late = now > p.hitAt

            if (p.kind == ParticleKind.ANTI) {
                val fadeOut = if (late) (1f - (now - p.hitAt) / 0.35f).coerceIn(0f, 1f) else 1f
                val alpha = (pop * fadeOut * 255).toInt()
                // L'antimatière tourne lentement sur elle-même et bat comme un cœur.
                r *= 1f + 0.05f * sin(animT * 10f)
                canvas.save()
                canvas.rotate(animT * 50f, p.x, p.y)
                drawSprite(canvas, p.kind, p.x, p.y, r, alpha)
                canvas.restore()
                continue
            }

            // Anneau d'approche : il se resserre jusqu'au cœur au « bon moment ».
            val prog = p.ringProgress(now)
            val ringR = p.radius * (1f + 2.1f * (1f - prog))
            val inPerfect = abs(now - p.hitAt) <= ReflexGame.PERFECT_WINDOW
            stroke.strokeWidth = (if (inPerfect) 4.5f else 3f) * dp
            stroke.color = when {
                inPerfect -> Color.WHITE
                late -> 0xFFFF5252.toInt()
                else -> (p.kind.color and 0x00FFFFFF) or ((90 + prog * 165).toInt() shl 24)
            }
            canvas.drawCircle(p.x, p.y, ringR, stroke)

            if (p.kind == ParticleKind.ELECTRON) {
                // Petite orbite qui tourne autour de l'électron.
                canvas.save()
                canvas.rotate(animT * 160f, p.x, p.y)
                stroke.strokeWidth = 1.6f * dp
                stroke.color = 0x907CFF6B.toInt()
                rect.set(p.x - r * 1.45f, p.y - r * 0.55f, p.x + r * 1.45f, p.y + r * 0.55f)
                canvas.drawOval(rect, stroke)
                canvas.restore()
            }

            val lateFade = if (late) (1f - (now - p.hitAt) / ReflexGame.GOOD_WINDOW * 0.5f) else 1f
            drawSprite(canvas, p.kind, p.x, p.y, r, (pop * lateFade * 255).toInt())

            when (p.kind) {
                ParticleKind.CHAIN -> {
                    text.textSize = r * 0.95f
                    text.color = Color.WHITE
                    canvas.drawText(chainDigits[p.chainIndex - 1], p.x, p.y + r * 0.33f, text)
                }
                ParticleKind.NUCLEUS -> if (p.hitsLeft == 1) drawCrack(canvas, p.x, p.y, r)
                else -> Unit
            }
        }
    }

    /** Fêlure en zigzag sur un noyau déjà frappé une fois. */
    private fun drawCrack(canvas: Canvas, x: Float, y: Float, r: Float) {
        val pts = CRACK_POINTS
        var k = 0
        for (i in 0 until 4) {
            crackLine[k++] = x + pts[i * 2] * r; crackLine[k++] = y + pts[i * 2 + 1] * r
            crackLine[k++] = x + pts[i * 2 + 2] * r; crackLine[k++] = y + pts[i * 2 + 3] * r
        }
        stroke.strokeWidth = 3f * dp
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = 0xFF3E2723.toInt()
        canvas.drawLines(crackLine, 0, 16, stroke)
        stroke.strokeWidth = 1.2f * dp
        stroke.color = Color.WHITE
        canvas.drawLines(crackLine, 0, 16, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun drawEffects(canvas: Canvas) {
        for (i in 0 until maxWaves) {
            if (wDur[i] <= 0f) continue
            val t = wt[i] / wDur[i]
            val ease = 1f - (1f - t) * (1f - t)
            stroke.strokeWidth = (5f * (1f - t) + 1f) * dp
            stroke.color = (wColor[i] and 0x00FFFFFF) or (((1f - t) * Color.alpha(wColor[i])).toInt() shl 24)
            canvas.drawCircle(wx[i], wy[i], wr0[i] + (wr1[i] - wr0[i]) * ease, stroke)
        }
        for (i in 0 until maxSparks) {
            if (sLife[i] <= 0f) continue
            val f = sLife[i] / sMax[i]
            fill.color = (sColor[i] and 0x00FFFFFF) or ((f * 255).toInt() shl 24)
            canvas.drawCircle(sx[i], sy[i], (0.8f + 2.6f * f) * dp, fill)
        }
        for (i in 0 until maxTexts) {
            val s = tStr[i] ?: continue
            val t = tt[i] / tDur[i]
            val scale = if (t < 0.15f) 0.6f + t / 0.15f * 0.5f else 1.1f - (t - 0.15f) * 0.15f
            text.textSize = tSize[i] * scale
            text.color = (tColor[i] and 0x00FFFFFF) or (((1f - t * t) * 255).toInt() shl 24)
            canvas.drawText(s, tx[i], ty[i] - t * 36f * dp, text)
        }
    }

    // ── Bandeau ──────────────────────────────────────────────────────────────

    private var scoreCache = -1L
    private var scoreStr = ""
    private var comboCache = -1
    private var comboStr = ""
    private var multCache = -1
    private var multStr = ""

    private fun drawHud(canvas: Canvas) {
        // Score, qui défile jusqu'à sa vraie valeur.
        val s = shownScore.toLong()
        if (s != scoreCache) { scoreCache = s; scoreStr = fmt(s) }
        text.color = Color.WHITE
        text.textSize = 30f * sp
        text.setShadowLayer(8f * dp, 0f, 0f, 0x8029E0FF.toInt())
        canvas.drawText(scoreStr, w / 2f, 38f * dp, text)
        text.clearShadowLayer()

        // Combo et multiplicateur sous le score.
        val combo = game.combo
        if (combo >= 2) {
            if (combo != comboCache) { comboCache = combo; comboStr = comboLine(combo) }
            text.textSize = 15f * sp * (1f + comboPop * 0.35f)
            text.color = when {
                game.isOverheating -> 0xFFFFB74D.toInt()
                combo >= 30 -> 0xFFFFD54F.toInt()
                combo >= 10 -> 0xFF80DEEA.toInt()
                else -> 0xFFB0BEC5.toInt()
            }
            canvas.drawText(comboStr, w / 2f, 62f * dp, text)
        }
        val mult = game.multiplier
        if (mult > 1) {
            if (mult != multCache) { multCache = mult; multStr = multLine(mult) }
            text.textSize = 18f * sp
            text.color = if (game.isOverheating) 0xFFFF9800.toInt() else 0xFFFFD54F.toInt()
            canvas.drawText(multStr, w / 2f + 96f * dp, 36f * dp, text)
        }

        // Vies : trois petits atomes en haut à droite.
        val lifeR = 8f * dp
        for (i in 0 until ReflexGame.MAX_LIVES) {
            val x = w - 22f * dp - i * 28f * dp
            val y = 30f * dp
            val has = i < game.lives
            val popScale = if (i == game.lives - 1 && lifePop > 0f) 1f + lifePop * 0.6f
                           else if (i == game.lives && lifePop < 0f) 1f - lifePop * 0.8f else 1f
            val rr = lifeR * popScale
            if (has) {
                drawSprite(canvas, ParticleKind.PROTON, x, y, rr, 255)
            } else {
                stroke.strokeWidth = 1.5f * dp
                stroke.color = 0x60FFFFFF
                canvas.drawCircle(x, y, rr, stroke)
            }
            stroke.strokeWidth = 1.2f * dp
            stroke.color = if (has) 0xB0FFFFFF.toInt() else 0x30FFFFFF
            canvas.save()
            canvas.rotate(-30f, x, y)
            rect.set(x - rr * 1.7f, y - rr * 0.6f, x + rr * 1.7f, y + rr * 0.6f)
            canvas.drawOval(rect, stroke)
            canvas.restore()
        }

        // Jauge de surchauffe.
        val left = 24f * dp
        val right = w - 24f * dp
        val y = heatBarY
        val barH = 8f * dp
        rect.set(left, y - barH / 2, right, y + barH / 2)
        fill.shader = null
        fill.color = 0x30FFFFFF
        canvas.drawRoundRect(rect, barH, barH, fill)
        val heat = game.heat
        if (heat > 0f) {
            rect.set(left, y - barH / 2, left + (right - left) * heat, y + barH / 2)
            fill.color = if (game.isOverheating) {
                val pulse = 0.5f + 0.5f * sin(animT * 12f)
                Color.rgb(255, (130 + pulse * 80).toInt(), 20)
            } else {
                // Du cyan vers l'orange à mesure que la jauge monte.
                Color.rgb((41 + 214 * heat).toInt(), (224 - 90 * heat).toInt(), (255 - 235 * heat).toInt())
            }
            canvas.drawRoundRect(rect, barH, barH, fill)
        }
        if (game.isOverheating) {
            text.textSize = 13f * sp
            text.color = 0xFFFFB74D.toInt()
            canvas.drawText(txtOverheat, w / 2f, y - 10f * dp, text)
        }
    }

    private fun drawUnlockBanner(canvas: Canvas) {
        val kind = unlockKind ?: return
        if (game.phase != ReflexPhase.PLAYING) return
        val t = unlockT
        val alpha = when {
            t < 0.25f -> t / 0.25f
            t > 1.9f -> ((2.4f - t) / 0.5f).coerceIn(0f, 1f)
            else -> 1f
        }
        val y = hudHeight + 40f * dp
        val slide = (1f - (t / 0.25f).coerceAtMost(1f)) * 30f * dp
        drawSprite(canvas, kind, w / 2f - 70f * dp + slide, y, 16f * dp, (alpha * 255).toInt())
        text.textAlign = Paint.Align.LEFT
        text.textSize = 20f * sp
        text.color = (kind.color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
        canvas.drawText(kindNames[kind.ordinal], w / 2f - 44f * dp + slide, y + 7f * sp, text)
        text.textAlign = Paint.Align.CENTER
    }

    // ── Écrans ───────────────────────────────────────────────────────────────

    /** Quelques particules qui flottent derrière le menu, pour donner le ton. */
    private fun drawMenuDecor(canvas: Canvas) {
        val kinds = ParticleKind.entries
        for (i in 0 until 7) {
            val kind = kinds[i % kinds.size]
            val x = w * (0.12f + 0.76f * ((i * 0.37f + 0.1f) % 1f)) + sin(animT * 0.6f + i) * 14f * dp
            val y = h * (0.12f + 0.8f * ((i * 0.61f + 0.05f) % 1f)) + cos(animT * 0.5f + i * 2f) * 18f * dp
            drawSprite(canvas, kind, x, y, (14f + (i % 3) * 5f) * dp, 70)
        }
    }

    private fun button(canvas: Canvas, r: RectF, label: String, color: Int, primary: Boolean) {
        val pulse = if (primary) 0.5f + 0.5f * sin(animT * 3f) else 0f
        val rad = r.height() / 2f
        fill.color = (color and 0x00FFFFFF) or ((if (primary) 0x40 + (pulse * 40).toInt() else 0x22) shl 24)
        canvas.drawRoundRect(r, rad, rad, fill)
        stroke.strokeWidth = 2f * dp
        stroke.color = color
        canvas.drawRoundRect(r, rad, rad, stroke)
        text.textSize = 18f * sp
        text.color = Color.WHITE
        canvas.drawText(label, r.centerX(), r.centerY() + 6.5f * sp, text)
    }

    private fun layoutButtons(y: Float, two: Boolean) {
        val bw = min(w * 0.62f, 260f * dp)
        val bh = 56f * dp
        btnMain.set(w / 2 - bw / 2, y, w / 2 + bw / 2, y + bh)
        if (two) btnSecond.set(w / 2 - bw / 2, y + bh + 14f * dp, w / 2 + bw / 2, y + 2 * bh + 14f * dp)
        else btnSecond.setEmpty()
    }

    private fun drawMenu(canvas: Canvas) {
        val titleY = h * 0.24f
        // Le titre rétrécit s'il ne tient pas dans la largeur (« COLLISIONNEUR » est long).
        text.textSize = 46f * sp
        val titleW = text.measureText(txtTitle)
        if (titleW > w * 0.9f) text.textSize *= w * 0.9f / titleW
        text.color = Color.WHITE
        text.setShadowLayer(18f * dp, 0f, 0f, 0xFF29E0FF.toInt())
        canvas.drawText(txtTitle, w / 2f, titleY, text)
        text.clearShadowLayer()

        if (bestScore > 0) {
            text.textSize = 17f * sp
            text.color = 0xFFFFD54F.toInt()
            canvas.drawText(bestLine(), w / 2f, titleY + 40f * dp, text)
        }

        // Les cinq particules, avec leur nom : on les reconnaîtra en jeu.
        val kinds = ParticleKind.entries
        val slot = min(w / kinds.size, 96f * dp)
        val startX = w / 2f - slot * (kinds.size - 1) / 2f
        val y = h * 0.47f
        for ((i, kind) in kinds.withIndex()) {
            val x = startX + slot * i
            val bob = sin(animT * 2f + i) * 3f * dp
            drawSprite(canvas, kind, x, y + bob, 20f * dp, 255)
            if (kind == ParticleKind.CHAIN) {
                text.textSize = 19f * sp
                text.color = Color.WHITE
                canvas.drawText(chainDigits[0], x, y + bob + 7f * sp, text)
            }
            text.textSize = 12f * sp
            text.color = (kind.color and 0x00FFFFFF) or (0xE0 shl 24)
            canvas.drawText(kindNames[i], x, y + 50f * dp, text)
        }

        layoutButtons(h * 0.66f, two = false)
        button(canvas, btnMain, txtPlay, 0xFF29E0FF.toInt(), primary = true)
    }

    private fun dim(canvas: Canvas) {
        fill.color = 0xC0050510.toInt()
        canvas.drawRect(0f, 0f, w, h, fill)
    }

    private fun drawPause(canvas: Canvas) {
        dim(canvas)
        text.textSize = 36f * sp
        text.color = Color.WHITE
        canvas.drawText(txtPaused, w / 2f, h * 0.36f, text)
        layoutButtons(h * 0.48f, two = true)
        button(canvas, btnMain, txtResume, 0xFF29E0FF.toInt(), primary = true)
        button(canvas, btnSecond, txtMenu, 0xFF9FA8DA.toInt(), primary = false)
    }

    private fun drawGameOver(canvas: Canvas) {
        dim(canvas)
        val top = h * 0.2f
        text.textSize = 28f * sp
        if (lastWasRecord) {
            text.color = 0xFFFFD54F.toInt()
            text.setShadowLayer(14f * dp, 0f, 0f, 0xFFFF9800.toInt())
            canvas.drawText(txtNewRecord, w / 2f, top, text)
            text.clearShadowLayer()
        } else {
            text.color = 0xFFB0BEC5.toInt()
            canvas.drawText(txtGameOver, w / 2f, top, text)
        }

        text.textSize = 54f * sp
        text.color = Color.WHITE
        canvas.drawText(fmt(game.score), w / 2f, top + 70f * dp, text)

        val lineY = top + 118f * dp
        text.textSize = 16f * sp
        text.color = 0xFF80DEEA.toInt()
        canvas.drawText(endComboStr, w / 2f, lineY, text)
        text.color = 0xFFFFF59D.toInt()
        canvas.drawText(endPerfectStr, w / 2f, lineY + 26f * dp, text)
        if (lastNeutrinos > 0) {
            text.color = 0xFFB388FF.toInt()
            canvas.drawText(endNeutrinoStr, w / 2f, lineY + 52f * dp, text)
        }

        layoutButtons(h * 0.6f, two = true)
        val ready = overlayGuard <= 0f
        button(canvas, btnMain, txtReplay, if (ready) 0xFF29E0FF.toInt() else 0x6029E0FF, primary = ready)
        button(canvas, btnSecond, txtMenu, 0xFF9FA8DA.toInt(), primary = false)
    }
}
