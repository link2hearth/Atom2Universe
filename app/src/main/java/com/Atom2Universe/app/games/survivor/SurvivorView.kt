package com.Atom2Universe.app.games.survivor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import kotlin.math.*

class SurvivorView @JvmOverloads constructor(
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

    val game = SurvivorGame(ctx)

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L

    // Densité mise en cache — évite resources.displayMetrics à chaque dp()/sp()
    private var _dp = 1f
    private var _sp = 1f

    // Couleurs pré-parsées — Color.parseColor() interdit dans la boucle 60fps
    private val C_BG          = Color.parseColor("#0D0D0F")
    private val C_XP_BG       = Color.parseColor("#220044")
    private val C_XP_FILL     = Color.parseColor("#B1DF8C")
    private val C_HP_BG       = Color.parseColor("#1A0000")
    private val C_SHIELD_BG   = Color.parseColor("#001A2A")
    private val C_SHIELD_FILL = Color.parseColor("#44CCFF")
    private val C_AURA_S      = Color.argb(60, 74, 240, 255)
    private val C_AURA_F      = Color.argb(18, 74, 240, 255)
    private val C_PROJ_CRIT   = Color.parseColor("#FFFF44")
    private val C_BNC_NORM    = Color.parseColor("#FF8844")
    private val C_BNC_CRIT    = Color.parseColor("#FFDD00")
    private val C_BOMB_LIT    = Color.parseColor("#FF4400")
    private val C_BOMB_NORM   = Color.parseColor("#FF8800")
    private val C_CHAIN_CORE  = Color.parseColor("#00EEFF")
    private val C_CHAIN_GLOW  = Color.parseColor("#0066AA")
    private val C_ORB_CORE    = Color.parseColor("#AADDFF")
    private val C_ORB_GLOW    = Color.parseColor("#224466")
    private val C_POISON      = Color.parseColor("#44FF88")
    private val C_BURN        = Color.parseColor("#FF6600")
    private val C_GRAY        = Color.parseColor("#A5C2BD")
    private val C_CARD_BG     = Color.parseColor("#18383F")
    private val C_BTN_BG      = Color.parseColor("#23535A")
    private val C_BTN_BORDER  = Color.parseColor("#70B4A5")
    private val C_GAMEOVER    = Color.parseColor("#FF4444")

    // Joystick state (written from touch thread, read from game thread)
    @Volatile private var jx = 0f
    @Volatile private var jy = 0f
    private var joystickActive = false
    private var joystickTouchId = -1
    private var joyCenterX = 0f
    private var joyCenterY = 0f
    private var joyKnobX = 0f
    private var joyKnobY = 0f
    private val JOY_OUTER_R get() = width * 0.13f
    private val JOY_INNER_R get() = JOY_OUTER_R * 0.42f
    private val JOY_MAX     get() = JOY_OUTER_R * 0.75f

    // Level-up card selection
    @Volatile private var pendingCardIndex = -1

    /**
     * Ce que le fil d'affichage peut demander. Tout est appliqué dans la boucle de jeu :
     * charger ou sauvegarder pendant qu'un `update` tourne casserait les listes.
     */
    private enum class SAction { HOME, NEW_GAME, LOAD_RUN, PAUSE, RESUME }

    // Actions demandées depuis le thread UI, exécutées dans le thread de jeu
    @Volatile private var pendingAction: SAction? = null
    @Volatile private var pendingStartWeapon: WeaponType? = null

    /** En-tête de la partie sauvegardée, relu à chaque retour au menu. */
    @Volatile private var savedRun: SurvivorSave.RunSummary? = null
    private var prevPhase = GamePhase.MENU

    // Strings cached
    private val sLevelUp   by lazy { ctx.getString(R.string.survivor_level_up) }
    private val sChoose    by lazy { ctx.getString(R.string.survivor_choose_upgrade) }
    private val sGameOver  by lazy { ctx.getString(R.string.survivor_game_over) }
    private val sMenu      by lazy { ctx.getString(R.string.survivor_menu) }
    private val sResume    by lazy { ctx.getString(R.string.survivor_resume) }
    private val sPaused    by lazy { ctx.getString(R.string.survivor_paused) }
    private val sWave      by lazy { ctx.getString(R.string.survivor_wave) }
    private val sKills     by lazy { ctx.getString(R.string.survivor_kills) }
    private val sBest      by lazy { ctx.getString(R.string.survivor_best) }
    private val sBoss      by lazy { ctx.getString(R.string.survivor_boss) }
    private val sTitle        by lazy { ctx.getString(R.string.survivor_title) }
    private val sSelectWeapon by lazy { ctx.getString(R.string.survivor_select_weapon) }
    private val sRevived      by lazy { ctx.getString(R.string.survivor_revived) }
    private val sNewGame      by lazy { ctx.getString(R.string.survivor_new_game) }
    private val sSavedRun     by lazy { ctx.getString(R.string.survivor_saved_run) }
    private val sRetry        by lazy { ctx.getString(R.string.survivor_retry) }
    private val sTime         by lazy { ctx.getString(R.string.survivor_time) }
    private val sLevel        by lazy { ctx.getString(R.string.survivor_level) }

    // Paints
    private val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke= Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val pTextL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.LEFT }
    private val ellipsisPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL) }

    // Chemin réutilisé pour les formes géométriques ennemies
    private val art = SurvivorArt()

    // Card rects computed each draw pass
    private val cardRects = Array(3) { RectF() }

    // Weapon selection cards
    private val weaponCardRects = Array(7) { RectF() }
    private data class WpnCard(val type: WeaponType, val labelRes: Int, val descRes: Int, val shortLabel: String, val color: Int)
    private val wpnCards by lazy { listOf(
        WpnCard(WeaponType.PROJECTILE,     R.string.survivor_weapon_projectile,       R.string.survivor_weapon_desc_projectile,      "PROJ", Color.parseColor("#88AAFF")),
        WpnCard(WeaponType.BOUNCING,       R.string.survivor_weapon_bouncing,          R.string.survivor_weapon_desc_bouncing,        "BNC",  Color.parseColor("#FF9944")),
        WpnCard(WeaponType.LASER,          R.string.survivor_weapon_laser,             R.string.survivor_weapon_desc_laser,           "LZR",  Color.parseColor("#FF3366")),
        WpnCard(WeaponType.CHAIN_LIGHTNING,R.string.survivor_weapon_chain_lightning,   R.string.survivor_weapon_desc_chain_lightning, "⚡",   Color.parseColor("#00BBFF")),
        WpnCard(WeaponType.AURA,           R.string.survivor_weapon_aura,              R.string.survivor_weapon_desc_aura,            "AUR",  Color.parseColor("#4AF0FF")),
        WpnCard(WeaponType.BOMB,           R.string.survivor_weapon_bomb,              R.string.survivor_weapon_desc_bomb,            "BMB",  Color.parseColor("#FF8800")),
        WpnCard(WeaponType.ORBITAL,        R.string.survivor_weapon_orbital,           R.string.survivor_weapon_desc_orbital,         "ORB",  Color.parseColor("#AADDFF")),
    ) }

    // Boutons empilés des menus (accueil, pause, mort) : au plus trois à la fois
    private val menuBtns = Array(3) { RectF() }
    private var menuBtnCount = 0

    init { holder.addCallback(this) }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {
        _dp = resources.displayMetrics.density
        _sp = resources.displayMetrics.density * resources.configuration.fontScale
        start()
    }
    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        game.screenW = w.toFloat()
        game.screenH = h2.toFloat()
    }
    override fun surfaceDestroyed(h: SurfaceHolder) { stop() }

    fun pause()  { running = false; try { thread?.join(300) } catch (_: Exception) {} }
    fun resume() { if (!running) start() }

    private fun start() {
        val old = thread
        running = true
        lastNanos = System.nanoTime()
        thread = Thread(this, "SurvivorGame").also { it.start() }
        old?.interrupt()
    }

    private fun stop() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
    }

    // ─── Game loop ────────────────────────────────────────────────────────────

    override fun run() {
        val me = Thread.currentThread()
        while (running && me === thread) {
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = now

            // Traiter les actions demandées depuis le thread UI
            pendingAction?.let { a -> pendingAction = null; applyAction(a) }
            pendingStartWeapon?.let { w ->
                pendingStartWeapon = null
                // Une nouvelle partie remplace l'ancienne : plus rien à reprendre.
                SurvivorSave.clearRun(context); savedRun = null
                game.startGame(w)
            }
            val card = pendingCardIndex
            if (card >= 0) {
                pendingCardIndex = -1
                game.pendingUpgrades?.getOrNull(card)?.let { game.applyUpgrade(it) }
            }

            game.update(dt, jx, jy)

            // La mort clôt la partie : le record est posé, la sauvegarde n'a plus d'objet.
            if (game.phase == GamePhase.GAME_OVER && prevPhase != GamePhase.GAME_OVER) {
                SurvivorSave.saveBest(context, game)
                SurvivorSave.clearRun(context)
                savedRun = null
            }
            prevPhase = game.phase

            // Si le jeu n'est plus en cours de jeu, le joystick doit être relâché
            if (game.phase != GamePhase.PLAYING) {
                jx = 0f; jy = 0f
                joystickActive = false; joystickTouchId = -1
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

    // ─── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (game.phase) {
            GamePhase.MENU          -> handleMenuTouch(ev)
            GamePhase.WEAPON_SELECT -> handleWeaponSelectTouch(ev)
            GamePhase.PLAYING       -> handlePlayTouch(ev)
            GamePhase.LEVEL_UP -> handleLevelUpTouch(ev)
            GamePhase.PAUSED   -> handlePausedTouch(ev)
            GamePhase.GAME_OVER-> handleGameOverTouch(ev)
        }
        return true
    }

    private fun handleMenuTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        when (tappedButton(ev.x, ev.y)) {
            0 -> pendingAction = if (savedRun != null) SAction.LOAD_RUN else SAction.NEW_GAME
            1 -> pendingAction = SAction.NEW_GAME
        }
    }

    /** Index du bouton empilé touché, ou -1. */
    private fun tappedButton(x: Float, y: Float): Int {
        for (i in 0 until menuBtnCount) if (menuBtns[i].contains(x, y)) return i
        return -1
    }

    private fun handleWeaponSelectTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        val x = ev.x; val y = ev.y
        wpnCards.forEachIndexed { i, card ->
            if (!weaponCardRects[i].isEmpty && weaponCardRects[i].contains(x, y))
                pendingStartWeapon = card.type
        }
    }

    private fun handlePlayTouch(ev: MotionEvent) {
        val pi = ev.actionIndex
        val pid = ev.getPointerId(pi)
        val px = ev.getX(pi); val py = ev.getY(pi)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (!joystickActive) {
                    joystickActive = true; joystickTouchId = pid
                    joyCenterX = px; joyCenterY = py
                    joyKnobX = px; joyKnobY = py
                    jx = 0f; jy = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    if (ev.getPointerId(i) == joystickTouchId) {
                        val dx = ev.getX(i) - joyCenterX
                        val dy = ev.getY(i) - joyCenterY
                        val dist = sqrt(dx * dx + dy * dy)
                        val clamped = dist.coerceAtMost(JOY_MAX)
                        val nx = if (dist > 0) dx / dist else 0f
                        val ny = if (dist > 0) dy / dist else 0f
                        joyKnobX = joyCenterX + nx * clamped
                        joyKnobY = joyCenterY + ny * clamped
                        jx = nx * (clamped / JOY_MAX)
                        jy = ny * (clamped / JOY_MAX)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pid == joystickTouchId) { joystickActive = false; joystickTouchId = -1; jx = 0f; jy = 0f }
            }
        }
    }

    private fun handleLevelUpTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        val x = ev.x; val y = ev.y
        cardRects.forEachIndexed { i, r -> if (r.contains(x, y)) { pendingCardIndex = i } }
    }

    private fun handlePausedTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        when (tappedButton(ev.x, ev.y)) {
            0 -> pendingAction = SAction.RESUME
            1 -> pendingAction = SAction.HOME
        }
    }

    private fun handleGameOverTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        when (tappedButton(ev.x, ev.y)) {
            0 -> pendingAction = SAction.NEW_GAME
            1 -> pendingAction = SAction.HOME
        }
    }

    /** Exécuté dans le fil de jeu, boucle à l'arrêt entre deux images. */
    private fun applyAction(a: SAction) {
        when (a) {
            // Quitter une partie en cours ne l'efface pas : elle attend au menu.
            SAction.HOME -> {
                SurvivorSave.saveRun(context, game)
                savedRun = SurvivorSave.peekRun(context)
                game.phase = GamePhase.MENU
            }
            SAction.NEW_GAME -> game.phase = GamePhase.WEAPON_SELECT
            SAction.LOAD_RUN -> {
                SurvivorSave.loadRun(context, game)
                savedRun = SurvivorSave.peekRun(context)
            }
            SAction.PAUSE    -> if (game.phase == GamePhase.PLAYING) game.phase = GamePhase.PAUSED
            SAction.RESUME   -> if (game.phase == GamePhase.PAUSED) game.resumePlaying()
        }
    }

    /** Ouvre le jeu sur son menu d'accueil. Appelé avant le démarrage de la boucle. */
    fun showHome() {
        SurvivorSave.loadBest(context, game)
        savedRun = SurvivorSave.peekRun(context)
        game.phase = GamePhase.MENU
        prevPhase = GamePhase.MENU
    }

    /**
     * Écrit records et partie en cours. À n'appeler qu'après [pause],
     * la boucle arrêtée : la sérialisation parcourt les listes du jeu.
     */
    fun persist() {
        SurvivorSave.saveBest(context, game)
        SurvivorSave.saveRun(context, game)
        // Revenir dans l'application ne doit pas relancer le combat sans prévenir.
        if (game.phase == GamePhase.PLAYING) game.phase = GamePhase.PAUSED
    }

    /** Retour système. Retourne faux quand il ne reste plus qu'à fermer l'activité. */
    fun onBackPressed(): Boolean {
        pendingAction = when (game.phase) {
            GamePhase.MENU -> return false
            GamePhase.PLAYING -> SAction.PAUSE
            else -> SAction.HOME
        }
        return true
    }

    fun requestPause() { pendingAction = SAction.PAUSE }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    private fun render(canvas: Canvas) {
        canvas.drawColor(C_BG)
        drawStars(canvas)

        val cx = width / 2f; val cy = height / 2f
        val camX = game.player.x; val camY = game.player.y

        fun wx(wx: Float) = cx + (wx - camX)
        fun wy(wy: Float) = cy + (wy - camY)

        when (game.phase) {
            GamePhase.MENU          -> drawHome(canvas)
            GamePhase.WEAPON_SELECT -> drawWeaponSelect(canvas)
            GamePhase.PLAYING, GamePhase.LEVEL_UP, GamePhase.PAUSED -> {
                drawAura(canvas, wx(game.player.x), wy(game.player.y))
                drawOrbital(canvas, ::wx, ::wy)
                drawResidues(canvas, ::wx, ::wy)
                drawExplosions(canvas, ::wx, ::wy)
                drawBombs(canvas, ::wx, ::wy)
                drawProjectiles(canvas, ::wx, ::wy)
                drawBouncingProjs(canvas, ::wx, ::wy)
                drawLasers(canvas, ::wx, ::wy)
                drawChainLightnings(canvas, ::wx, ::wy)
                drawEnemies(canvas, ::wx, ::wy)
                drawParticles(canvas, ::wx, ::wy)
                drawEnemyBullets(canvas, ::wx, ::wy)
                drawPlayer(canvas, wx(game.player.x), wy(game.player.y))
                drawDmgNums(canvas, ::wx, ::wy)
                drawHUD(canvas)
                drawJoystick(canvas)
                if (game.bossWarning > 0f) drawBossWarning(canvas)
                if (game.reviveFlashTimer > 0f) drawReviveFlash(canvas)
                if (game.phase == GamePhase.LEVEL_UP) drawLevelUp(canvas)
                if (game.phase == GamePhase.PAUSED)   drawPaused(canvas)
            }
            GamePhase.GAME_OVER -> {
                drawEnemies(canvas, ::wx, ::wy)
                drawHUD(canvas)
                drawGameOver(canvas)
            }
        }
    }

    private fun drawStars(canvas: Canvas) {
        art.terrain(canvas, game.player.x, game.player.y, game.survivalTime)
    }

    private fun drawPlayer(canvas: Canvas, sx: Float, sy: Float) {
        val p = game.player
        if (p.iframeCd > 0f && (p.iframeCd * 10).toInt() % 2 == 0) return
        art.player(canvas, sx, sy, SurvivorGame.PLAYER_R.toFloat(), game.survivalTime,
            abs(jx) + abs(jy) > 0.05f)
    }

    private fun drawEnemies(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (e in game.enemies) {
            val sx = wx(e.x); val sy = wy(e.y)
            if (sx < -e.radius * 2 || sx > width + e.radius * 2 ||
                sy < -e.radius * 2 || sy > height + e.radius * 2) continue
            art.enemy(canvas, e, sx, sy, game.survivalTime, game.player.x, game.player.y)
            if (e.poisonTimer > 0f) {
                pStroke.color = C_POISON; pStroke.strokeWidth = 2f
                canvas.drawCircle(sx, sy, e.radius + 3f, pStroke)
            }
            if (e.burnTimer > 0f) {
                pStroke.color = C_BURN; pStroke.strokeWidth = 2f
                canvas.drawCircle(sx, sy, e.radius + 7f, pStroke)
            }
        }
    }
    // ─── Aura ─────────────────────────────────────────────────────────────────

    private fun drawAura(canvas: Canvas, sx: Float, sy: Float) {
        if (!game.player.weapons.contains(WeaponType.AURA)) return
        val r = game.auraRadius()
        pStroke.color = C_AURA_S; pStroke.strokeWidth = 3f
        canvas.drawCircle(sx, sy, r, pStroke)
        pFill.color = C_AURA_F
        canvas.drawCircle(sx, sy, r, pFill)
    }

    // ─── Projectiles ──────────────────────────────────────────────────────────

    private fun drawProjectiles(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (p in game.projectiles) {
            pFill.color = if (p.isCrit) C_PROJ_CRIT else Color.WHITE
            canvas.drawCircle(wx(p.x), wy(p.y), p.radius, pFill)
        }
    }

    private fun drawLasers(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (l in game.lasers) {
            val alpha = ((l.lifetime / SurvivorGame.LASER_DUR) * 255).toInt().coerceIn(0, 255)
            pStroke.color = Color.argb(alpha, 255, 50, 100)
            pStroke.strokeWidth = l.width
            canvas.drawLine(wx(l.x1), wy(l.y1), wx(l.x2), wy(l.y2), pStroke)
            // Core line
            pStroke.color = Color.argb(alpha, 255, 200, 220)
            pStroke.strokeWidth = l.width * 0.3f
            canvas.drawLine(wx(l.x1), wy(l.y1), wx(l.x2), wy(l.y2), pStroke)
        }
    }

    private fun drawBouncingProjs(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (b in game.bouncingProjs) {
            pFill.color = if (b.isCrit) C_BNC_CRIT else C_BNC_NORM
            canvas.drawCircle(wx(b.x), wy(b.y), b.radius, pFill)
        }
    }

    private fun drawBombs(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (b in game.bombs) {
            pFill.color = if (b.lifetime / SurvivorGame.BOMB_LIFE < 0.3f) C_BOMB_LIT else C_BOMB_NORM
            canvas.drawCircle(wx(b.x), wy(b.y), b.radius, pFill)
        }
    }

    private fun drawResidues(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (res in game.residues) {
            val pulse = 0.75f + 0.25f * sin((res.duration * 4f).toDouble()).toFloat()
            val alpha = ((res.duration / SurvivorGame.RESIDUE_BASE_DURATION).coerceIn(0f, 1f) * 120 * pulse).toInt()
            pFill.color = Color.argb(alpha, 180, 80, 0)
            canvas.drawCircle(wx(res.x), wy(res.y), res.radius, pFill)
            pFill.color = Color.argb((alpha * 0.5f).toInt(), 255, 140, 0)
            canvas.drawCircle(wx(res.x), wy(res.y), res.radius * 0.6f, pFill)
        }
    }

    private fun drawExplosions(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (ex in game.explosions) {
            val ratio = 1f - ex.life / ex.maxLife
            val r = ex.maxRadius * ratio
            val alpha = (ex.life / ex.maxLife * 180).toInt()
            pFill.color = Color.argb(alpha, 255, 140, 0)
            canvas.drawCircle(wx(ex.x), wy(ex.y), r, pFill)
            pFill.color = Color.argb((alpha * 0.5f).toInt(), 255, 220, 100)
            canvas.drawCircle(wx(ex.x), wy(ex.y), r * 0.5f, pFill)
        }
    }

    // ─── Chain lightning ──────────────────────────────────────────────────────

    private fun drawChainLightnings(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (cl in game.chainLightnings) {
            val alpha = ((cl.lifetime / 0.18f) * 255).toInt().coerceIn(0, 255)
            // Glow
            pStroke.color = Color.argb((alpha * 0.4f).toInt(), 0, 180, 255)
            pStroke.strokeWidth = 10f
            canvas.drawLine(wx(cl.x1), wy(cl.y1), wx(cl.x2), wy(cl.y2), pStroke)
            // Core
            pStroke.color = if (cl.isCrit) Color.argb(alpha, 255, 255, 100) else Color.argb(alpha, 0, 238, 255)
            pStroke.strokeWidth = 2.5f
            canvas.drawLine(wx(cl.x1), wy(cl.y1), wx(cl.x2), wy(cl.y2), pStroke)
        }
    }

    // ─── Orbital ──────────────────────────────────────────────────────────────

    private fun drawOrbital(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        if (!game.player.weapons.contains(WeaponType.ORBITAL)) return
        for (i in 0 until game.orbitalCount) {
            val ox = game.orbXY[i * 2]; val oy = game.orbXY[i * 2 + 1]
            val sx = wx(ox); val sy = wy(oy)
            pGlow.color = Color.argb(60, 170, 221, 255)
            canvas.drawCircle(sx, sy, SurvivorGame.ORB_R * 2.2f, pGlow)
            pFill.color = C_ORB_CORE
            canvas.drawCircle(sx, sy, SurvivorGame.ORB_R, pFill)
            pStroke.color = Color.WHITE; pStroke.strokeWidth = 1.5f; pStroke.alpha = 160
            canvas.drawCircle(sx, sy, SurvivorGame.ORB_R, pStroke)
            pStroke.alpha = 255
        }
    }

    // ─── Enemy bullets ────────────────────────────────────────────────────────

    private fun drawEnemyBullets(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        if (game.enemyBullets.isEmpty()) return
        val pulse = (sin(game.survivalTime * 9f) * 0.5f + 0.5f).toFloat()
        val alpha = (180 + (pulse * 75f).toInt()).coerceIn(0, 255)
        val rv    = (180 + (pulse * 75f).toInt()).coerceIn(0, 255)
        for (b in game.enemyBullets) {
            val sx = wx(b.x); val sy = wy(b.y)
            pGlow.color = Color.argb((alpha * 0.35f).toInt(), 255, 30, 30)
            canvas.drawCircle(sx, sy, b.radius * 2.2f, pGlow)
            pFill.color = Color.argb(alpha, rv, 30, 30)
            canvas.drawCircle(sx, sy, b.radius, pFill)
        }
    }

    // ─── Particles ────────────────────────────────────────────────────────────

    private fun drawParticles(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (p in game.particles) {
            val alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            pFill.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(wx(p.x), wy(p.y), p.r, pFill)
        }
    }

    // ─── Damage numbers ───────────────────────────────────────────────────────

    private fun drawDmgNums(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        for (n in game.dmgNums) {
            val alpha = ((n.life / 0.8f) * 255).toInt().coerceIn(0, 255)
            pText.textSize = if (n.isCrit) sp(18f) else sp(14f)
            pText.color = if (n.isCrit) Color.YELLOW else Color.WHITE
            pText.alpha = alpha
            canvas.drawText(n.text, wx(n.x), wy(n.y), pText)
        }
        pText.alpha = 255
    }

    // ─── HUD ──────────────────────────────────────────────────────────────────

    private val xpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var xpGradientWidth = -1
    private val hudText = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawHUD(canvas: Canvas) {
        val p = game.player
        val edge = dp(4f)
        val barWidth = dp(10f)
        val xpLeft = dp(4f)
        val xpRight = width - dp(4f)
        val xpTop = dp(3f)
        val xpBottom = dp(8f)
        if (xpGradientWidth != width) {
            xpGradientWidth = width
            // Dégradé ancré sur toute la piste : de nouvelles teintes apparaissent avec l'XP.
            xpPaint.shader = LinearGradient(xpLeft, 0f, xpRight, 0f,
                intArrayOf(0xFF579A92.toInt(), 0xFF85B995.toInt(), 0xFFC1C38C.toInt(), 0xFFC79894.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        pFill.color = Color.argb(145, 12, 30, 30)
        canvas.drawRoundRect(xpLeft, xpTop, xpRight, xpBottom, dp(2f), dp(2f), pFill)
        if (p.xpRatio() > 0f) {
            canvas.save()
            canvas.clipRect(xpLeft, xpTop, xpLeft + (xpRight - xpLeft) * p.xpRatio(), xpBottom)
            canvas.drawRoundRect(xpLeft, xpTop, xpRight, xpBottom, dp(2f), dp(2f), xpPaint)
            canvas.restore()
        }
        hudText.color = Color.rgb(195, 211, 199)
        hudText.textSize = sp(11f); hudText.textAlign = Paint.Align.CENTER
        canvas.drawText(context.getString(R.string.survivor_hud_level, p.level), width / 2f, dp(25f), hudText)

        val barTop = dp(16f)
        val barBottom = height - dp(8f)
        val barHeight = (barBottom - barTop).coerceAtLeast(0f)
        pFill.color = C_HP_BG
        canvas.drawRoundRect(edge, barTop, edge + barWidth, barBottom, dp(5f), dp(5f), pFill)
        pFill.color = if (p.hpRatio() < 0.3f) C_GAMEOVER else Color.rgb(119, 222, 172)
        canvas.drawRoundRect(edge, barBottom - barHeight * p.hpRatio(), edge + barWidth, barBottom, dp(5f), dp(5f), pFill)
        hudText.textSize = sp(10f); hudText.textAlign = Paint.Align.LEFT
        hudText.color = Color.rgb(154, 220, 180)
        canvas.drawText(context.getString(R.string.survivor_hud_health, p.hp.toInt(), p.maxHp.toInt()),
            edge + barWidth + dp(6f), height - dp(12f), hudText)
        if (p.maxShield > 0f) {
            val shieldLeft = width - edge - barWidth
            pFill.color = C_SHIELD_BG
            canvas.drawRoundRect(shieldLeft, barTop, width - edge, barBottom, dp(5f), dp(5f), pFill)
            pFill.color = C_SHIELD_FILL
            canvas.drawRoundRect(shieldLeft, barBottom - barHeight * p.shieldRatio(), width - edge, barBottom, dp(5f), dp(5f), pFill)
            hudText.textAlign = Paint.Align.RIGHT; hudText.color = Color.rgb(146, 206, 231)
            canvas.drawText(context.getString(R.string.survivor_hud_shield, p.shield.toInt(), p.maxShield.toInt()),
                shieldLeft - dp(6f), height - dp(12f), hudText)
        }
        if (p.revivesLeft > 0) {
            hudText.textAlign = Paint.Align.LEFT; hudText.color = Color.WHITE; hudText.textSize = sp(12f)
            canvas.drawText("❤".repeat(p.revivesLeft), edge + barWidth + dp(6f), height - dp(30f), hudText)
        }
    }    // ─── Joystick ─────────────────────────────────────────────────────────────

    private fun drawJoystick(canvas: Canvas) {
        if (!joystickActive) return
        pFill.color = Color.argb(100, 18, 53, 59)
        canvas.drawCircle(joyCenterX, joyCenterY, JOY_OUTER_R, pFill)
        pStroke.color = Color.argb(160, 125, 220, 194); pStroke.strokeWidth = 2f
        canvas.drawCircle(joyCenterX, joyCenterY, JOY_OUTER_R, pStroke)
        pFill.color = Color.argb(180, 149, 224, 204)
        canvas.drawCircle(joyKnobX, joyKnobY, JOY_INNER_R, pFill)
    }

    // ─── Revive flash ─────────────────────────────────────────────────────────

    private fun drawReviveFlash(canvas: Canvas) {
        val t = game.reviveFlashTimer
        val alpha = ((t / 2.5f) * 180).toInt().coerceIn(0, 180)
        pFill.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val textAlpha = ((t / 2.5f) * 255).toInt().coerceIn(0, 255)
        pText.color = Color.argb(textAlpha, 255, 230, 80)
        pText.textSize = sp(42f)
        pText.textAlign = Paint.Align.CENTER
        canvas.drawText(sRevived, width / 2f, height / 2f, pText)
    }

    // ─── Boss warning ─────────────────────────────────────────────────────────

    private fun drawBossWarning(canvas: Canvas) {
        val alpha = ((game.bossWarning / 3f) * 200).toInt().coerceIn(0, 200)
        pText.color = Color.argb(alpha, 255, 60, 60)
        pText.textSize = sp(32f)
        canvas.drawText(sBoss, width / 2f, height / 2f, pText)
        pText.color = Color.WHITE
    }

    // ─── Weapon select ────────────────────────────────────────────────────────

    private fun drawWeaponSelect(canvas: Canvas) {
        val cx = width / 2f
        pFill.color = Color.argb(205, 9, 25, 32)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        pText.textAlign = Paint.Align.CENTER
        pText.color = C_XP_FILL; pText.textSize = sp(28f)
        canvas.drawText(sTitle, cx, dp(52f), pText)

        pText.textSize = sp(11f); pText.color = C_GRAY
        canvas.drawText(context.getString(R.string.survivor_sanctuary), cx, dp(73f), pText)
        val nextY = dp(98f)
        pText.textSize = sp(14f); pText.color = C_GRAY
        canvas.drawText(sSelectWeapon, cx, nextY, pText)

        val cardW = width * 0.84f
        val gap   = dp(8f)
        val startY = nextY + dp(18f)
        val cardH = min(dp(68f), (height - startY - dp(14f) - gap * 6f) / 7f).coerceAtLeast(dp(30f))

        wpnCards.forEachIndexed { i, card ->
            val top   = startY + i * (cardH + gap)
            val left  = cx - cardW / 2f
            val right = cx + cardW / 2f
            weaponCardRects[i].set(left, top, right, top + cardH)

            pFill.color = C_CARD_BG
            canvas.drawRoundRect(weaponCardRects[i], dp(12f), dp(12f), pFill)
            pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = dp(0.6f)
            canvas.drawRoundRect(weaponCardRects[i], dp(12f), dp(12f), pStroke)

            pFill.color = card.color
            canvas.drawRoundRect(left, top, left + dp(6f), top + cardH, dp(3f), dp(3f), pFill)

            val iconCx = left + dp(46f)
            val iconCy = top + cardH / 2f
            pFill.color = Color.argb(180, Color.red(card.color), Color.green(card.color), Color.blue(card.color))
            canvas.drawCircle(iconCx, iconCy, min(dp(19f), cardH * 0.35f), pFill)
            pText.color = Color.WHITE; pText.textSize = sp(10f)
            canvas.drawText(card.shortLabel, iconCx, iconCy + sp(4f), pText)

            pTextL.color = Color.WHITE; pTextL.textSize = min(sp(15f), cardH * 0.25f)
            ellipsisPaint.set(pTextL)
            val label = android.text.TextUtils.ellipsize(context.getString(card.labelRes),
                ellipsisPaint, cardW - dp(87f), android.text.TextUtils.TruncateAt.END)
            canvas.drawText(label.toString(), left + dp(75f), top + cardH * 0.42f, pTextL)
            pTextL.color = C_GRAY; pTextL.textSize = min(sp(12f), cardH * 0.21f)
            ellipsisPaint.set(pTextL)
            val description = android.text.TextUtils.ellipsize(context.getString(card.descRes),
                ellipsisPaint, cardW - dp(87f), android.text.TextUtils.TruncateAt.END)
            canvas.drawText(description.toString(), left + dp(75f), top + cardH * 0.73f, pTextL)
        }
        pText.color = Color.WHITE
    }

    // ─── Accueil ──────────────────────────────────────────────────────────────

    private fun drawHome(canvas: Canvas) {
        val cx = width / 2f
        pFill.color = Color.argb(210, 9, 25, 32)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)

        // Un bloc titre : la silhouette du survivant, le nom, la devise.
        val headY = height * 0.16f
        art.player(canvas, cx, headY, dp(30f), 0f, false)
        pText.textAlign = Paint.Align.CENTER
        pText.color = C_XP_FILL; pText.textSize = sp(34f)
        canvas.drawText(sTitle, cx, headY + dp(66f), pText)
        pText.textSize = sp(11f); pText.color = C_GRAY
        canvas.drawText(context.getString(R.string.survivor_sanctuary), cx, headY + dp(86f), pText)

        var y = headY + dp(118f)
        if (game.bestTime > 0f) {
            pText.textSize = sp(13f); pText.color = C_GRAY
            canvas.drawText("$sBest ${mmss(game.bestTime)}   ${killsText(game.bestKills)}", cx, y, pText)
            y += dp(26f)
        }

        // La partie sauvegardée se montre avant de se proposer : on voit ce qu'on reprend.
        val run = savedRun
        val labels = ArrayList<String>(2)
        if (run != null) {
            y = drawRunCard(canvas, cx, y, run) + dp(20f)
            labels.add(sResume)
        } else {
            y += dp(10f)
        }
        labels.add(sNewGame)
        // Les boutons gardent la même hauteur qu'il y ait une partie à reprendre ou non :
        // sinon l'unique bouton flotterait au milieu du vide.
        drawButtonStack(canvas, cx, max(y, height * 0.60f), labels)
        pText.color = Color.WHITE
    }

    private val runCardRect = RectF()

    /** Carte d'accroche de la partie sauvegardée ; retourne son bord bas. */
    private fun drawRunCard(canvas: Canvas, cx: Float, top: Float, run: SurvivorSave.RunSummary): Float {
        val cardW = min(dp(300f), width * 0.84f)
        val cardH = dp(88f)
        runCardRect.set(cx - cardW / 2f, top, cx + cardW / 2f, top + cardH)
        pFill.color = C_CARD_BG
        canvas.drawRoundRect(runCardRect, dp(14f), dp(14f), pFill)
        pStroke.color = C_BTN_BORDER; pStroke.strokeWidth = dp(0.8f)
        canvas.drawRoundRect(runCardRect, dp(14f), dp(14f), pStroke)
        pFill.color = C_XP_FILL
        canvas.drawRoundRect(runCardRect.left, top, runCardRect.left + dp(6f), top + cardH, dp(3f), dp(3f), pFill)

        val textLeft = runCardRect.left + dp(20f)
        pTextL.color = C_GRAY; pTextL.textSize = sp(10f)
        canvas.drawText(sSavedRun, textLeft, top + dp(22f), pTextL)
        pTextL.color = Color.WHITE; pTextL.textSize = sp(26f)
        canvas.drawText(mmss(run.survivalTime), textLeft, top + dp(52f), pTextL)
        pTextL.color = C_GRAY; pTextL.textSize = sp(11f)
        canvas.drawText("${levelText(run.level)}   $sWave ${run.wave}   ${killsText(run.kills)}",
            textLeft, top + dp(72f), pTextL)

        // Les armes portées, en pastilles, à droite de la carte.
        var chipCx = runCardRect.right - dp(26f)
        for (w in run.weapons.reversed()) {
            val card = wpnCards.firstOrNull { it.type == w } ?: continue
            pFill.color = Color.argb(180, Color.red(card.color), Color.green(card.color), Color.blue(card.color))
            canvas.drawCircle(chipCx, top + cardH / 2f, dp(17f), pFill)
            pText.color = Color.WHITE; pText.textSize = sp(9f)
            canvas.drawText(card.shortLabel, chipCx, top + cardH / 2f + sp(3f), pText)
            chipCx -= dp(40f)
        }
        return top + cardH
    }

    // ─── Level-up ─────────────────────────────────────────────────────────────

    private fun drawLevelUp(canvas: Canvas) {
        val choices = game.pendingUpgrades ?: return
        // Dim
        pFill.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)

        val cx = width / 2f
        pText.color = Color.WHITE; pText.textSize = sp(28f)
        canvas.drawText(sLevelUp, cx, dp(70f), pText)
        pText.textSize = sp(14f); pText.color = C_GRAY
        canvas.drawText(sChoose, cx, dp(100f), pText)

        val cardW = (width * 0.82f)
        val cardH = dp(100f)
        val startY = dp(120f)
        val gap = dp(12f)

        choices.forEachIndexed { i, opt ->
            val top = startY + i * (cardH + gap)
            val left = cx - cardW / 2f
            val right = cx + cardW / 2f
            cardRects[i].set(left, top, right, top + cardH)

            // Card background
            pFill.color = C_CARD_BG
            canvas.drawRoundRect(cardRects[i], dp(12f), dp(12f), pFill)

            // Colored left accent bar
            pFill.color = opt.cardColor
            canvas.drawRoundRect(left, top, left + dp(6f), top + cardH, dp(3f), dp(3f), pFill)

            // Icon circle
            val iconCx = left + dp(50f)
            val iconCy = top + cardH / 2f
            pFill.color = Color.argb(180, Color.red(opt.cardColor), Color.green(opt.cardColor), Color.blue(opt.cardColor))
            canvas.drawCircle(iconCx, iconCy, dp(22f), pFill)
            pText.color = Color.WHITE; pText.textSize = sp(11f)
            canvas.drawText(opt.shortLabel, iconCx, iconCy + sp(4f), pText)

            // Label
            pTextL.color = Color.WHITE; pTextL.textSize = sp(16f)
            canvas.drawText(context.getString(opt.labelRes), left + dp(82f), top + cardH * 0.42f, pTextL)

            // Current level indicator
            val lvl = game.player.upg(opt.id)
            if (lvl > 0) {
                pTextL.color = opt.cardColor; pTextL.textSize = sp(11f)
                canvas.drawText(levelText(lvl), right - dp(40f), top + cardH * 0.35f, pTextL)
            }

            // Description
            pTextL.color = C_GRAY; pTextL.textSize = sp(12f)
            canvas.drawText(context.getString(opt.descRes), left + dp(82f), top + cardH * 0.72f, pTextL)
        }

        // Fill unused card slots if fewer than 3 choices
        for (i in choices.size until 3) {
            cardRects[i].setEmpty()
        }
    }

    // ─── Pause ────────────────────────────────────────────────────────────────

    private fun drawPaused(canvas: Canvas) {
        pFill.color = Color.argb(190, 6, 18, 24)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f
        val top = height * 0.22f
        pText.textAlign = Paint.Align.CENTER
        pText.color = Color.WHITE; pText.textSize = sp(30f)
        canvas.drawText(sPaused, cx, top, pText)

        val p = game.player
        val y = drawStatGrid(canvas, cx, top + dp(28f), listOf(
            sTime  to mmss(game.survivalTime),
            sLevel to p.level.toString(),
            sWave  to game.wave.toString(),
            sKills to p.kills.toString()
        ))
        // Le retour au menu garde la partie : ce n'est pas un abandon.
        drawButtonStack(canvas, cx, y + dp(30f), listOf(sResume, sMenu))
        pText.color = Color.WHITE
    }

    // ─── Fin de partie ────────────────────────────────────────────────────────

    private fun drawGameOver(canvas: Canvas) {
        pFill.color = Color.argb(205, 6, 12, 16)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f
        val top = height * 0.22f
        pText.textAlign = Paint.Align.CENTER
        pText.color = C_GAMEOVER; pText.textSize = sp(34f)
        canvas.drawText(sGameOver, cx, top, pText)

        val p = game.player
        var y = drawStatGrid(canvas, cx, top + dp(28f), listOf(
            sTime  to mmss(game.survivalTime),
            sLevel to p.level.toString(),
            sWave  to game.wave.toString(),
            sKills to p.kills.toString()
        ))
        if (game.bestTime > 0f) {
            y += dp(26f)
            // Un record battu se lit dans la couleur, sans mot de plus.
            pText.color = if (game.survivalTime >= game.bestTime) C_XP_FILL else C_GRAY
            pText.textSize = sp(12f)
            canvas.drawText("$sBest ${mmss(game.bestTime)}   ${killsText(game.bestKills)}", cx, y, pText)
        }
        drawButtonStack(canvas, cx, y + dp(28f), listOf(sRetry, sMenu))
        pText.color = Color.WHITE
    }

    // ─── Blocs de menu ────────────────────────────────────────────────────────

    /** Une ligne de chiffres : la valeur au-dessus, son intitulé en dessous. */
    private fun drawStatGrid(canvas: Canvas, cx: Float, top: Float, stats: List<Pair<String, String>>): Float {
        if (stats.isEmpty()) return top
        val gridW = min(dp(300f), width * 0.86f)
        val colW = gridW / stats.size
        val left = cx - gridW / 2f
        pText.textAlign = Paint.Align.CENTER
        stats.forEachIndexed { i, (label, value) ->
            val colCx = left + colW * (i + 0.5f)
            pText.color = Color.WHITE; pText.textSize = sp(19f)
            canvas.drawText(value, colCx, top + dp(24f), pText)
            pText.color = C_GRAY; pText.textSize = sp(10f)
            canvas.drawText(label, colCx, top + dp(40f), pText)
        }
        return top + dp(40f)
    }

    /**
     * Empile des boutons pleine largeur sous [topY]. Le premier est le bouton principal :
     * seul lui est rempli, les autres restent en contour. Retourne le bas de la pile.
     */
    private fun drawButtonStack(canvas: Canvas, cx: Float, topY: Float, labels: List<String>): Float {
        val bw = min(dp(260f), width * 0.74f)
        val bh = dp(50f)
        val gap = dp(12f)
        menuBtnCount = labels.size.coerceAtMost(menuBtns.size)
        for (r in menuBtns) r.setEmpty()
        var y = topY
        for (i in 0 until menuBtnCount) {
            val r = menuBtns[i]
            r.set(cx - bw / 2f, y, cx + bw / 2f, y + bh)
            val primary = i == 0
            pFill.color = if (primary) C_BTN_BG else Color.argb(80, 18, 45, 51)
            canvas.drawRoundRect(r, dp(12f), dp(12f), pFill)
            pStroke.color = if (primary) C_BTN_BORDER else Color.argb(110, 112, 180, 165)
            pStroke.strokeWidth = dp(if (primary) 1.4f else 1f)
            canvas.drawRoundRect(r, dp(12f), dp(12f), pStroke)
            pText.textAlign = Paint.Align.CENTER
            pText.color = if (primary) Color.WHITE else C_GRAY
            pText.textSize = sp(if (primary) 16f else 14f)
            canvas.drawText(labels[i], r.centerX(), r.centerY() + sp(5.5f), pText)
            y += bh + gap
        }
        pText.color = Color.WHITE
        return y - gap
    }

    // ─── Util ─────────────────────────────────────────────────────────────────

    /** mm:ss — le format vit dans les ressources, pas ici. */
    private fun mmss(seconds: Float): String {
        val s = seconds.toInt()
        return context.getString(R.string.survivor_time_mmss, s / 60, s % 60)
    }

    private fun killsText(n: Int) = context.getString(R.string.survivor_kills_count, n)

    private fun levelText(n: Int) = context.getString(R.string.survivor_level_short, n)

    private fun dp(v: Float) = v * _dp
    private fun sp(v: Float) = v * _sp
}
