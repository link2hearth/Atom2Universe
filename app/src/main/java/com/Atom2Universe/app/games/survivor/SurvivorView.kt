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
    private val C_XP_FILL     = Color.parseColor("#AA44FF")
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
    private val C_GRAY        = Color.parseColor("#AAAAAA")
    private val C_CARD_BG     = Color.parseColor("#1A1A2E")
    private val C_BTN_BG      = Color.parseColor("#2A2A3A")
    private val C_BTN_BORDER  = Color.parseColor("#555566")
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

    // Actions demandées depuis le thread UI, exécutées dans le thread de jeu
    @Volatile private var pendingWeaponSelect = false
    @Volatile private var pendingStartWeapon: WeaponType? = null
    @Volatile private var pendingPhase: GamePhase? = null

    // Strings cached
    private val sTapStart  by lazy { ctx.getString(R.string.survivor_tap_to_start) }
    private val sLevelUp   by lazy { ctx.getString(R.string.survivor_level_up) }
    private val sChoose    by lazy { ctx.getString(R.string.survivor_choose_upgrade) }
    private val sGameOver  by lazy { ctx.getString(R.string.survivor_game_over) }
    private val sRestart   by lazy { ctx.getString(R.string.survivor_restart) }
    private val sQuit      by lazy { ctx.getString(R.string.survivor_quit) }
    private val sResume    by lazy { ctx.getString(R.string.survivor_resume) }
    private val sPaused    by lazy { ctx.getString(R.string.survivor_paused) }
    private val sWave      by lazy { ctx.getString(R.string.survivor_wave) }
    private val sKills     by lazy { ctx.getString(R.string.survivor_kills) }
    private val sBest      by lazy { ctx.getString(R.string.survivor_best) }
    private val sBoss      by lazy { ctx.getString(R.string.survivor_boss) }
    private val sTitle        by lazy { ctx.getString(R.string.survivor_title) }
    private val sSelectWeapon by lazy { ctx.getString(R.string.survivor_select_weapon) }

    // Paints
    private val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke= Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val pTextL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.LEFT }
    private val pGlow  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL) }

    // Chemin réutilisé pour les formes géométriques ennemies
    private val shapePath = Path()

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

    // Pause / game-over button rects
    private val btnRect1 = RectF()
    private val btnRect2 = RectF()

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
            if (pendingWeaponSelect) { pendingWeaponSelect = false; game.phase = GamePhase.WEAPON_SELECT }
            pendingStartWeapon?.let { w -> pendingStartWeapon = null; game.startGame(w) }
            pendingPhase?.let { ph -> pendingPhase = null; game.phase = ph }
            val card = pendingCardIndex
            if (card >= 0) {
                pendingCardIndex = -1
                game.pendingUpgrades?.getOrNull(card)?.let { game.applyUpgrade(it) }
            }

            game.update(dt, jx, jy)

            // Si le jeu n'est plus en cours de jeu, le joystick doit être relâché
            if (game.phase != GamePhase.PLAYING) {
                jx = 0f; jy = 0f
                joystickActive = false; joystickTouchId = -1
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
        if (ev.action == MotionEvent.ACTION_UP) pendingWeaponSelect = true
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
        val x = ev.x; val y = ev.y
        if (btnRect1.contains(x, y)) pendingPhase = GamePhase.PLAYING
        if (btnRect2.contains(x, y)) pendingWeaponSelect = true
    }

    private fun handleGameOverTouch(ev: MotionEvent) {
        if (ev.action != MotionEvent.ACTION_UP) return
        val x = ev.x; val y = ev.y
        if (btnRect1.contains(x, y)) pendingWeaponSelect = true
        if (btnRect2.contains(x, y)) pendingWeaponSelect = true
    }

    fun requestMenu()  { pendingWeaponSelect = true }
    fun requestPause() { if (game.phase == GamePhase.PLAYING) pendingPhase = GamePhase.PAUSED }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    private fun render(canvas: Canvas) {
        canvas.drawColor(C_BG)
        drawStars(canvas)

        val cx = width / 2f; val cy = height / 2f
        val camX = game.player.x; val camY = game.player.y

        fun wx(wx: Float) = cx + (wx - camX)
        fun wy(wy: Float) = cy + (wy - camY)

        when (game.phase) {
            GamePhase.MENU          -> drawMenu(canvas)
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

    // ─── Background stars ─────────────────────────────────────────────────────

    private val starPositions by lazy {
        val rng = kotlin.random.Random(42)
        Array(80) { Pair(rng.nextFloat() * 4000f - 2000f, rng.nextFloat() * 4000f - 2000f) }
    }

    private fun drawStars(canvas: Canvas) {
        val camX = game.player.x; val camY = game.player.y
        val cx = width / 2f; val cy = height / 2f
        pFill.color = Color.WHITE
        for ((sx, sy) in starPositions) {
            val screenX = cx + ((sx - camX * 0.3f) % width + width) % width - width / 2f
            val screenY = cy + ((sy - camY * 0.3f) % height + height) % height - height / 2f
            canvas.drawCircle(screenX, screenY, 1f, pFill)
        }
    }

    // ─── Player ───────────────────────────────────────────────────────────────

    private fun drawPlayer(canvas: Canvas, sx: Float, sy: Float) {
        val p = game.player
        val r = SurvivorGame.PLAYER_R.toFloat()
        val flashing = p.iframeCd > 0f && ((p.iframeCd * 10).toInt() % 2 == 0)
        if (flashing) return

        // Glow
        pGlow.color = game.playerColor(p.hpRatio()) and 0x66FFFFFF.toInt()
        canvas.drawCircle(sx, sy, r * 1.6f, pGlow)

        // Body
        pFill.color = game.playerColor(p.hpRatio())
        canvas.drawCircle(sx, sy, r, pFill)

        // Outline
        pStroke.color = Color.WHITE; pStroke.strokeWidth = 2f; pStroke.alpha = 180
        canvas.drawCircle(sx, sy, r, pStroke)
        pStroke.alpha = 255
    }

    // ─── Enemies ──────────────────────────────────────────────────────────────

    private fun drawEnemies(canvas: Canvas, wx: (Float) -> Float, wy: (Float) -> Float) {
        val pulse     = (sin(game.survivalTime * 5.5f)  * 0.5f + 0.5f).toFloat()
        val pulseFast = (sin(game.survivalTime * 10.0f) * 0.5f + 0.5f).toFloat()
        for (e in game.enemies) {
            val sx = wx(e.x); val sy = wy(e.y)
            if (sx < -e.radius * 2 || sx > width + e.radius * 2 || sy < -e.radius * 2 || sy > height + e.radius * 2) continue

            val col = when (e.type) {
                EnemyType.SHOOTER -> {
                    val bv = (180 + (pulse * 75f).toInt()).coerceIn(0, 255)
                    val rv = (80  + (pulse * 60f).toInt()).coerceIn(0, 255)
                    Color.rgb(rv, 15, bv)
                }
                EnemyType.FAST -> {
                    // rouge vif / pastel → bordeaux selon HP, pulse rapide
                    val hp = e.hpRatio
                    val r = (100 + hp * 155f + pulseFast * hp * 55f).toInt().coerceIn(0, 255)
                    val g = (pulseFast * hp * 125f).toInt().coerceIn(0, 255)
                    val b = (hp * 18f  + pulseFast * hp * 70f).toInt().coerceIn(0, 255)
                    Color.rgb(r, g, b)
                }
                else -> game.enemyColor(e.hpRatio)
            }

            // Rayon visuel pulsant pour les FAST (sans toucher au rayon de collision)
            val drawR = if (e.type == EnemyType.FAST) e.radius * (0.87f + pulseFast * 0.26f) else e.radius

            // Halo
            pGlow.color = col and 0x44FFFFFF.toInt()
            canvas.drawCircle(sx, sy, drawR * 1.4f, pGlow)

            // Corps — forme selon le type
            pFill.color = col
            val isCircle = e.type == EnemyType.ZOMBIE
            val angleToPlayer = if (e.type == EnemyType.FAST)
                atan2(game.player.y - e.y, game.player.x - e.x) else 0f
            if (!isCircle) buildEnemyPath(e.type, sx, sy, drawR, angleToPlayer)
            if (isCircle) canvas.drawCircle(sx, sy, drawR, pFill)
            else canvas.drawPath(shapePath, pFill)

            // Contour
            pStroke.color = Color.argb(100, 255, 255, 255); pStroke.strokeWidth = 1.5f
            if (isCircle) canvas.drawCircle(sx, sy, drawR, pStroke)
            else canvas.drawPath(shapePath, pStroke)
            pStroke.alpha = 255

            // Indicateurs DoT
            if (e.poisonTimer > 0f) {
                pStroke.color = Color.argb(180, 68, 255, 136); pStroke.strokeWidth = 2f
                canvas.drawCircle(sx, sy, e.radius + 3f, pStroke)
            }
            if (e.burnTimer > 0f) {
                pStroke.color = Color.argb(180, 255, 102, 0); pStroke.strokeWidth = 2f
                canvas.drawCircle(sx, sy, e.radius + 3f + (if (e.poisonTimer > 0f) 4f else 0f), pStroke)
            }

            // Anneau boss
            if (e.type == EnemyType.MINI_BOSS) {
                pStroke.color = Color.WHITE; pStroke.strokeWidth = 2.5f
                canvas.drawCircle(sx, sy, e.radius + 4f, pStroke)
            }
        }
    }

    private fun buildEnemyPath(type: EnemyType, cx: Float, cy: Float, r: Float, angleToPlayer: Float = 0f) {
        when (type) {
            EnemyType.FAST     -> setPolygonPath(cx, cy, r, 3, angleToPlayer)   // pointe vers le joueur
            EnemyType.ERRATIC  -> setPolygonPath(cx, cy, r, 4, PI.toFloat() / 4f)    // losange
            EnemyType.ORBITER  -> setPolygonPath(cx, cy, r, 6, 0f)                    // hexagone
            EnemyType.SHOOTER  -> setPolygonPath(cx, cy, r, 4, PI.toFloat() / 4f)    // losange (distinct par couleur)
            EnemyType.MINI_BOSS-> setStarPath(cx, cy, r, r * 0.45f, 5)               // étoile 5 branches
            else -> {}
        }
    }

    private fun setPolygonPath(cx: Float, cy: Float, r: Float, sides: Int, rotOffset: Float) {
        shapePath.reset()
        val step = 2f * PI.toFloat() / sides
        for (i in 0 until sides) {
            val a = step * i + rotOffset
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            if (i == 0) shapePath.moveTo(x, y) else shapePath.lineTo(x, y)
        }
        shapePath.close()
    }

    private fun setStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float, points: Int) {
        shapePath.reset()
        val total = points * 2
        for (i in 0 until total) {
            val a = PI.toFloat() / points * i - PI.toFloat() / 2f
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            if (i == 0) shapePath.moveTo(x, y) else shapePath.lineTo(x, y)
        }
        shapePath.close()
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
        for ((ox, oy) in game.orbitalPositions) {
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
            pText.alpha = alpha
            pText.textSize = if (n.isCrit) sp(18f) else sp(14f)
            pText.color = if (n.isCrit) Color.YELLOW else Color.WHITE
            pText.alpha = alpha
            canvas.drawText(n.text, wx(n.x), wy(n.y), pText)
        }
        pText.alpha = 255
    }

    // ─── HUD ──────────────────────────────────────────────────────────────────

    private fun drawHUD(canvas: Canvas) {
        val p = game.player
        val barW = dp(12f)
        val barMargin = dp(4f)
        val xpH = dp(8f)
        val topPad = dp(2f)

        // XP bar — top
        pFill.color = C_XP_BG
        canvas.drawRect(0f, topPad, width.toFloat(), topPad + xpH, pFill)
        pFill.color = C_XP_FILL
        canvas.drawRect(0f, topPad, width * p.xpRatio(), topPad + xpH, pFill)

        // HP bar — left
        val hpTop = topPad + xpH + barMargin
        val barH = height - hpTop - barMargin
        pFill.color = C_HP_BG
        canvas.drawRect(barMargin, hpTop, barMargin + barW, hpTop + barH, pFill)
        val hpFill = barH * p.hpRatio()
        pFill.color = game.playerColor(p.hpRatio())
        canvas.drawRect(barMargin, hpTop + barH - hpFill, barMargin + barW, hpTop + barH, pFill)

        // Shield bar — right
        if (p.maxShield > 0f) {
            val rx = width - barMargin - barW
            pFill.color = C_SHIELD_BG
            canvas.drawRect(rx, hpTop, rx + barW, hpTop + barH, pFill)
            val shFill = barH * p.shieldRatio()
            pFill.color = C_SHIELD_FILL
            canvas.drawRect(rx, hpTop + barH - shFill, rx + barW, hpTop + barH, pFill)
        }

    }

    // ─── Joystick ─────────────────────────────────────────────────────────────

    private fun drawJoystick(canvas: Canvas) {
        if (!joystickActive) return
        pFill.color = Color.argb(50, 255, 255, 255)
        canvas.drawCircle(joyCenterX, joyCenterY, JOY_OUTER_R, pFill)
        pStroke.color = Color.argb(100, 255, 255, 255); pStroke.strokeWidth = 2f
        canvas.drawCircle(joyCenterX, joyCenterY, JOY_OUTER_R, pStroke)
        pFill.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(joyKnobX, joyKnobY, JOY_INNER_R, pFill)
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
        pText.color = Color.WHITE; pText.textSize = sp(34f)
        canvas.drawText(sTitle, cx, dp(52f), pText)

        var nextY = dp(78f)
        if (game.bestTime > 0f) {
            val sec = game.bestTime.toInt()
            pText.textSize = sp(12f); pText.color = C_GRAY
            canvas.drawText("$sBest: %d:%02d  ${game.bestKills}☠".format(sec / 60, sec % 60), cx, nextY, pText)
            nextY += dp(16f)
        }
        pText.textSize = sp(14f); pText.color = C_GRAY
        canvas.drawText(sSelectWeapon, cx, nextY, pText)

        val cardW = width * 0.84f
        val cardH = dp(60f)
        val gap   = dp(9f)
        val startY = nextY + dp(14f)

        wpnCards.forEachIndexed { i, card ->
            val top   = startY + i * (cardH + gap)
            val left  = cx - cardW / 2f
            val right = cx + cardW / 2f
            weaponCardRects[i].set(left, top, right, top + cardH)

            pFill.color = C_CARD_BG
            canvas.drawRoundRect(weaponCardRects[i], dp(12f), dp(12f), pFill)

            pFill.color = card.color
            canvas.drawRoundRect(left, top, left + dp(6f), top + cardH, dp(3f), dp(3f), pFill)

            val iconCx = left + dp(46f)
            val iconCy = top + cardH / 2f
            pFill.color = Color.argb(180, Color.red(card.color), Color.green(card.color), Color.blue(card.color))
            canvas.drawCircle(iconCx, iconCy, dp(19f), pFill)
            pText.color = Color.WHITE; pText.textSize = sp(10f)
            canvas.drawText(card.shortLabel, iconCx, iconCy + sp(4f), pText)

            pTextL.color = Color.WHITE; pTextL.textSize = sp(15f)
            canvas.drawText(context.getString(card.labelRes), left + dp(75f), top + cardH * 0.42f, pTextL)
            pTextL.color = C_GRAY; pTextL.textSize = sp(12f)
            canvas.drawText(context.getString(card.descRes), left + dp(75f), top + cardH * 0.73f, pTextL)
        }
        pText.color = Color.WHITE
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────

    private fun drawMenu(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        pText.color = Color.WHITE; pText.textSize = sp(36f)
        canvas.drawText(sTitle, cx, cy - dp(60f), pText)
        pText.textSize = sp(16f); pText.color = C_GRAY
        canvas.drawText(sTapStart, cx, cy, pText)
        if (game.bestTime > 0f) {
            val sec = game.bestTime.toInt()
            pText.textSize = sp(13f)
            canvas.drawText("$sBest: %d:%02d  ${game.bestKills}☠".format(sec / 60, sec % 60), cx, cy + dp(30f), pText)
        }
        pText.color = Color.WHITE
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
        pText.textSize = sp(14f); pText.color = Color.parseColor("#AAAAAA")
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
                canvas.drawText("Lv.$lvl", right - dp(40f), top + cardH * 0.35f, pTextL)
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

    // ─── Paused ───────────────────────────────────────────────────────────────

    private fun drawPaused(canvas: Canvas) {
        pFill.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f; val cy = height / 2f
        pText.color = Color.WHITE; pText.textSize = sp(30f)
        canvas.drawText(sPaused, cx, cy - dp(60f), pText)
        drawTwoButtons(canvas, cx, cy, sResume, sQuit)
    }

    // ─── Game over ────────────────────────────────────────────────────────────

    private fun drawGameOver(canvas: Canvas) {
        pFill.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
        val cx = width / 2f; val cy = height / 2f
        pText.color = C_GAMEOVER; pText.textSize = sp(34f)
        canvas.drawText(sGameOver, cx, cy - dp(80f), pText)
        pText.color = Color.WHITE; pText.textSize = sp(14f)
        val p = game.player
        val sec = game.survivalTime.toInt()
        canvas.drawText("%d:%02d  —  Lv.%d  —  %d kills".format(sec / 60, sec % 60, p.level, p.kills), cx, cy - dp(40f), pText)
        if (game.bestTime > 0f) {
            val bs = game.bestTime.toInt()
            pText.color = C_GRAY; pText.textSize = sp(12f)
            canvas.drawText("$sBest %d:%02d  ${game.bestKills}☠".format(bs / 60, bs % 60), cx, cy - dp(16f), pText)
        }
        pText.color = Color.WHITE
        drawTwoButtons(canvas, cx, cy + dp(10f), sRestart, sQuit)
    }

    // ─── Button helper ────────────────────────────────────────────────────────

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

    private fun dp(v: Float) = v * _dp
    private fun sp(v: Float) = v * _sp
}
