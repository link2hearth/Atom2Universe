package com.Atom2Universe.app.games.particules

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

class ParticulesView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // ── Meta snapshot ────────────────────────────────────────────
    data class MetaSnapshot(
        val highScore: Long = 0L,
        val highestLevel: Int = 0,
        val runsPlayed: Int = 0,
        val totalGold: Long = 0L,
        val totalXp: Long = 0L,
        val bestCombo: Int = 0,
        val shopExtraLives: Int = 0,
        val shopSlowBall: Int = 0,
        val shopWidePaddle: Int = 0,
        val shopStartShield: Int = 0,
        val shopGoldMagnet: Int = 0,
        val shopMultiStart: Int = 0,
        val shopStackTimers: Int = 0
    )
    var onMetaUpdate: ((MetaSnapshot) -> Unit)? = null
    private var meta = MetaSnapshot()
    fun setMeta(m: MetaSnapshot) {
        meta = m
        // Si la surface est prête et qu'aucune partie n'a encore commencé,
        // appliquer les bonus shop aux variables de jeu (sinon ils seraient ignorés
        // pour la première partie de chaque session).
        if (state == State.READY && W > 0f) {
            maxLives = BASE_MAX_LIVES + m.shopExtraLives
            lives    = maxLives
            shields  = if (m.shopStartShield > 0) 1 else 0
            ballBaseSpd = (W + H) / 2f * 0.000456f * (1f - m.shopSlowBall * 0.08f)
            ballSpeed   = ballBaseSpeed()
            syncPaddleWidth()
        }
    }

    // ── Constants ────────────────────────────────────────────────
    private val TARGET_FPS       = 60L
    private val FRAME_MS         = 1000L / TARGET_FPS
    private val MAX_DELTA_MS     = 32L
    private val BASE_MAX_LIVES   = 3
    private val MAX_SPARKS       = 280
    private val LASER_INTERVAL   = 420L
    companion object { private const val TRAIL_MAX = 9 }
    private val COMBO_GRACE_BASE = 2800L
    private val PANIC_THRESHOLD_MS = 60_000L

    // ── State machine ────────────────────────────────────────────
    enum class State { READY, PLAYING, PAUSED, LIFE_LOST, LEVEL_CLEAR, GAME_OVER, SHOP }
    @Volatile private var state = State.READY
    var music: ParticulesMusic? = null

    // ── Layout ───────────────────────────────────────────────────
    private var W = 1f; private var H = 1f
    private var brickAreaTop = 0f; private var brickAreaBottom = 0f
    private var brickW = 0f;    private var brickH = 0f
    private val GAP = 3f

    // ── Game metrics ─────────────────────────────────────────────
    private var score      = 0L
    private var level      = 1
    private var lives      = BASE_MAX_LIVES
    private var maxLives   = BASE_MAX_LIVES
    private var gold       = 0L
    private var ballBaseSpd = 0f
    private var ballSpeed  = 0f
    private var spdMult    = 1f
    private var combo      = 0
    private var comboTimer = 0L
    private var bestCombo  = 0
    private var comboGrace = COMBO_GRACE_BASE
    private var scoreMult  = 1f

    // ── Rogue-like ───────────────────────────────────────────────
    private val ownedRelics = mutableSetOf<RelicId>()
    private var relicChoices: List<Relic> = emptyList()
    private val choiceRects = arrayOf(RectF(), RectF(), RectF())
    private val skipRelicRect = RectF()
    private var secondChanceUsed = false
    private var levelStartPending = false

    // ── Shop ─────────────────────────────────────────────────────
    private data class ShopItem(
        val key: String, val name: String, val desc: String,
        val price: Long, val maxLevel: Int, val icon: String
    )
    private val shopItems = listOf(
        ShopItem("extraLives",  "Vie +1",           "+1 vie max au départ",     500L,  3, "♥"),
        ShopItem("slowBall",    "Balle lente",      "-8% vitesse de balle",     400L,  3, "◎"),
        ShopItem("widePaddle",  "Raquette large",   "+12% largeur de raquette", 300L,  3, "▬"),
        ShopItem("startShield", "Bouclier initial", "1 bouclier au départ",     350L,  1, "◈"),
        ShopItem("goldMagnet",  "Aimant doré",      "×1.5 pièces par brique",   600L,  1, "◆"),
        ShopItem("multiStart",  "Multi-départ",     "2 balles au départ",       800L,  1, "⊕"),
        ShopItem("stackTimers", "Chrono+",          "Bonus cumulés : +temps",   700L,  1, "⧗")
    )
    private val shopRects = Array(7) { RectF() }
    private val shopBackRect = RectF()

    private fun shopLevel(key: String): Int = when (key) {
        "extraLives"  -> meta.shopExtraLives
        "slowBall"    -> meta.shopSlowBall
        "widePaddle"  -> meta.shopWidePaddle
        "startShield" -> meta.shopStartShield
        "goldMagnet"  -> meta.shopGoldMagnet
        "multiStart"  -> meta.shopMultiStart
        "stackTimers" -> meta.shopStackTimers
        else -> 0
    }

    private fun buyShopItem(item: ShopItem) {
        val cur = shopLevel(item.key)
        if (cur >= item.maxLevel) return
        val price = item.price * (cur + 1)
        if (gold < price) return
        gold -= price
        meta = meta.copy(
            shopExtraLives  = if (item.key == "extraLives")  meta.shopExtraLives + 1  else meta.shopExtraLives,
            shopSlowBall    = if (item.key == "slowBall")    meta.shopSlowBall + 1    else meta.shopSlowBall,
            shopWidePaddle  = if (item.key == "widePaddle")  meta.shopWidePaddle + 1  else meta.shopWidePaddle,
            shopStartShield = if (item.key == "startShield") meta.shopStartShield + 1 else meta.shopStartShield,
            shopGoldMagnet  = if (item.key == "goldMagnet")  meta.shopGoldMagnet + 1  else meta.shopGoldMagnet,
            shopMultiStart  = if (item.key == "multiStart")  meta.shopMultiStart + 1  else meta.shopMultiStart,
            shopStackTimers = if (item.key == "stackTimers") meta.shopStackTimers + 1 else meta.shopStackTimers
        )
        onMetaUpdate?.invoke(meta)
    }

    // ── Panic mode (finger damage) ───────────────────────────────
    private var lastDestroyedMs = 0L
    private var panicLatched = false
    private var fingerBreakReadyAtMs = 0L
    private val panicThreshold get() = if (RelicId.PANIC_TRAINER in ownedRelics) 30_000L else PANIC_THRESHOLD_MS
    private fun panicActive(): Boolean {
        if (state != State.PLAYING) return false
        if (!panicLatched && System.currentTimeMillis() - lastDestroyedMs > panicThreshold) {
            panicLatched = true
        }
        return panicLatched
    }
    private var fingerX = -1f; private var fingerY = -1f
    private var launchOnUp = false

    // ── Power-up timers (ms) ─────────────────────────────────────
    private var timerExtend  = 0L
    private var timerLaser   = 0L
    private var timerSpeed   = 0L
    private var timerSlow    = 0L
    private var timerFloor   = 0L
    private var timerPierce  = 0L
    private var timerFire    = 0L
    private var timerMagnet  = 0L
    private var shields      = 0
    private var laserCooldown = 0L

    // ── Effects ──────────────────────────────────────────────────
    private var shakeAmount = 0f
    private val shakeDecay  = 0.012f
    private var flashWhite  = 0f
    private var nebulaPulse = 0f

    // ── Entities ─────────────────────────────────────────────────
    enum class BType { SIMPLE, RESISTANT, BONUS, EXPLOSIVE, INDESTRUCTIBLE, REGEN, ICE }
    enum class PType { EXTEND, MULTIBALL, LASER, SPEED, FLOOR, PIERCE, FIRE, MAGNET, SHIELD, SLOW }
    enum class Shape { RECT, PILL, DIAMOND, HEX }
    enum class LayoutKind { GRID, STAGGER, ARC, CLUSTER, SPIRAL, BOSS }

    private class Ball(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var stuck: Boolean = false, var stickOffset: Float = 0f
    ) {
        val trailX = FloatArray(TRAIL_MAX)
        val trailY = FloatArray(TRAIL_MAX)
        var trailLen = 0
        var trailHead = -1
    }
    enum class MovePattern { NONE, HORIZONTAL, VERTICAL, ORBIT }
    private class Brick(
        val rect: RectF, var hits: Int, val maxHits: Int, val type: BType, val baseColor: Int,
        val shape: Shape = Shape.RECT,
        var regenTimer: Long = 0L, var flash: Float = 0f, var shake: Float = 0f,
        var movePattern: MovePattern = MovePattern.NONE,
        var originX: Float = 0f, var originY: Float = 0f,
        var moveSpeed: Float = 0f, var movePhase: Float = 0f,
        var moveRange: Float = 0f
    )
    private data class PowerUp(var x: Float, var y: Float, var vy: Float, val type: PType, var wob: Float = 0f)
    private data class LaserBeam(var x: Float, var y: Float)
    private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float = 1f)
    private data class Shockwave(var x: Float, var y: Float, var r: Float, val maxR: Float, var life: Float, val color: Int)
    private data class FloatText(var x: Float, var y: Float, var life: Float, val text: String, val color: Int, val size: Float)
    private data class Star(var x: Float, var y: Float, val depth: Float, val size: Float, val hue: Int)
    private data class PaddleGhost(var x: Float, val y: Float, var life: Float, val w: Float)

    private val balls       = mutableListOf<Ball>()
    private val bricks      = mutableListOf<Brick>()
    private val powerUps    = mutableListOf<PowerUp>()
    private val lasers      = mutableListOf<LaserBeam>()
    private val sparks      = mutableListOf<Spark>()
    private val shockwaves  = mutableListOf<Shockwave>()
    private val floatTexts  = mutableListOf<FloatText>()
    private val stars       = mutableListOf<Star>()
    private val paddleGhosts = mutableListOf<PaddleGhost>()

    // ── Paddle ───────────────────────────────────────────────────
    private val paddle       = RectF()
    private var paddleBaseW  = 0f
    private var paddleH      = 0f
    private var paddleY      = 0f
    private var paddleTarget = 0f
    private var paddleLastX  = 0f
    private var paddleVelX   = 0f

    private var ballR = 0f

    // ── Paints (reused, configured inline) ───────────────────────
    private val pNebula  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pOverlay = Paint().apply { color = 0xCC040810.toInt() }
    private val pBall    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val pPaddle  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBrick   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBrickGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCrack   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val pSpark   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLaser   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF4D9A.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pShock   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val pFloor   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStar    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pHud     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val pTitle   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val pSub     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAABBCC.toInt(); textAlign = Paint.Align.CENTER
    }
    private val pPuLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pCombo   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val pCard    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pPath    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brickPath = Path()
    private val tmpBallRect = RectF()
    private val tmpBrickRect = RectF()

    // ── Colors ───────────────────────────────────────────────────
    private val simpleColors = intArrayOf(
        0xFFFF4455.toInt(), 0xFF44CC55.toInt(), 0xFF4488FF.toInt(),
        0xFFFF8844.toInt(), 0xFF88FF44.toInt(), 0xFF44AAFF.toInt()
    )
    private val colorResistant      = 0xFF6B4EFF.toInt()
    private val colorBonus          = 0xFFFFD700.toInt()
    private val colorExplosive      = 0xFFFF5522.toInt()
    private val colorIndestructible = 0xFF555566.toInt()
    private val colorRegen          = 0xFF22DDAA.toInt()
    private val colorIce            = 0xFF88E5FF.toInt()

    private val puColors = mapOf(
        PType.EXTEND    to 0xFF66F4FF.toInt(),
        PType.MULTIBALL to 0xFFFFE066.toInt(),
        PType.LASER     to 0xFFFF4D9A.toInt(),
        PType.SPEED     to 0xFF9D7BFF.toInt(),
        PType.FLOOR     to 0xFF6EF7A6.toInt(),
        PType.PIERCE    to 0xFFFFFFFF.toInt(),
        PType.FIRE      to 0xFFFF6A2A.toInt(),
        PType.MAGNET    to 0xFFE255FF.toInt(),
        PType.SHIELD    to 0xFF4AC5FF.toInt(),
        PType.SLOW      to 0xFF5EE6B4.toInt()
    )
    private val puLabels = mapOf(
        PType.EXTEND to "L", PType.MULTIBALL to "M", PType.LASER to "T",
        PType.SPEED to "S", PType.FLOOR to "F",
        PType.PIERCE to "P", PType.FIRE to "B", PType.MAGNET to "A",
        PType.SHIELD to "D", PType.SLOW to "R"
    )

    // ── Thread ───────────────────────────────────────────────────
    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        setup(width.toFloat(), height.toFloat())
        startThread()
    }
    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, hi: Int) {
        setup(w.toFloat(), hi.toFloat())
    }
    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

    private fun setup(w: Float, h: Float) {
        W = w; H = h
        brickAreaTop = h * 0.10f
        brickAreaBottom = if (h > w) h * 0.58f else h * 0.54f

        paddleH     = maxOf(h * 0.022f, 12f)
        paddleBaseW = w * 0.18f
        paddleY     = h * 0.87f
        ballR       = maxOf(w * 0.014f, 7f)
        ballBaseSpd = (w + h) / 2f * 0.000456f
        if (RelicId.PRESSURE in ownedRelics) ballBaseSpd *= 0.95f
        ballSpeed   = ballBaseSpd

        pHud.textSize     = maxOf(h * 0.030f, 22f)
        pTitle.textSize   = maxOf(h * 0.042f, 28f)
        pSub.textSize     = maxOf(h * 0.028f, 20f)
        pPuLabel.textSize = maxOf(h * 0.022f, 16f)
        pCombo.textSize   = maxOf(h * 0.050f, 28f)

        pNebula.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF1E0F42.toInt(), 0xFF0A0820.toInt(), 0xFF1B0A45.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        refreshPaddlePaint()
        paddle.set(w / 2 - paddleBaseW / 2, paddleY, w / 2 + paddleBaseW / 2, paddleY + paddleH)
        paddleTarget = paddle.centerX()
        paddleLastX  = paddle.centerX()

        if (stars.isEmpty()) seedStars()
        if (bricks.isEmpty()) generateLevel()
        if (balls.isEmpty()) resetBallOnPaddle()
    }

    private fun seedStars() {
        stars.clear()
        val palette = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFAABBFF.toInt(), 0xFFFFCCAA.toInt(),
            0xFFCCFFEE.toInt(), 0xFFFFEEBB.toInt()
        )
        repeat(140) {
            stars.add(Star(
                Random.nextFloat() * W,
                Random.nextFloat() * H,
                Random.nextFloat() * 0.85f + 0.15f,
                Random.nextFloat() * 2.4f + 0.6f,
                palette[Random.nextInt(palette.size)]
            ))
        }
    }

    private fun refreshPaddlePaint() {
        val baseTop = 0xFF7AF8FF.toInt()
        val baseBot = 0xFF1E8FFF.toInt()
        val top = when {
            timerFire > 0   -> 0xFFFFD27A.toInt()
            timerMagnet > 0 -> 0xFFFFAAFF.toInt()
            else -> baseTop
        }
        val bot = when {
            timerFire > 0   -> 0xFFFF5522.toInt()
            timerMagnet > 0 -> 0xFFBB4AFF.toInt()
            else -> baseBot
        }
        pPaddle.shader = LinearGradient(
            0f, paddleY, 0f, paddleY + paddleH,
            intArrayOf(top, bot),
            null, Shader.TileMode.CLAMP
        )
        pFloor.color = 0xFF1EC37A.toInt()
        pFloor.alpha = 140
    }

    // ── Level generation (varied layouts/shapes) ─────────────────
    private var bossHpBar = 0f
    private var bossHpBarMax = 0f

    private fun themeForLevel(lv: Int): Pair<LayoutKind, Shape> {
        if (lv % 5 == 0) return LayoutKind.BOSS to Shape.DIAMOND
        return when (lv % 7) {
            0 -> LayoutKind.STAGGER to Shape.RECT
            1 -> LayoutKind.GRID    to Shape.PILL
            2 -> LayoutKind.STAGGER to Shape.HEX
            3 -> LayoutKind.ARC     to Shape.DIAMOND
            4 -> LayoutKind.GRID    to Shape.RECT
            5 -> LayoutKind.SPIRAL  to Shape.HEX
            else -> LayoutKind.STAGGER to Shape.PILL
        }
    }

    private fun generateLevel() {
        bricks.clear(); powerUps.clear(); lasers.clear(); sparks.clear()
        shockwaves.clear(); floatTexts.clear(); paddleGhosts.clear()

        val (layout, shape) = themeForLevel(level)
        val resChance = minOf(0.10f + (level - 1) * 0.018f, 0.38f)
        val bonChance = minOf(0.08f + (level - 1) * 0.006f, 0.18f)
        val expChance = if (level >= 3) minOf(0.04f + (level - 3) * 0.012f, 0.18f) else 0f
        val indChance = if (level >= 5) minOf(0.03f + (level - 5) * 0.008f, 0.14f) else 0f
        val regChance = if (level >= 4) minOf(0.04f + (level - 4) * 0.008f, 0.12f) else 0f
        val iceChance = if (level >= 4) minOf(0.04f + (level - 4) * 0.006f, 0.10f) else 0f

        when (layout) {
            LayoutKind.GRID     -> buildGrid(shape, false, resChance, bonChance, expChance, indChance, regChance, iceChance)
            LayoutKind.STAGGER  -> buildGrid(shape, true,  resChance, bonChance, expChance, indChance, regChance, iceChance)
            LayoutKind.ARC      -> buildArc(shape, resChance, bonChance, expChance, regChance, iceChance)
            LayoutKind.CLUSTER  -> buildCluster(shape, resChance, bonChance, expChance, indChance, regChance, iceChance)
            LayoutKind.SPIRAL   -> buildSpiral(shape, resChance, bonChance, expChance, regChance, iceChance)
            LayoutKind.BOSS     -> buildBoss(shape)
        }

        // Évite les briques hors écran (layouts en arc/spirale/mandala) qui rendaient le niveau infaisable
        bricks.removeAll { b ->
            b.rect.left < 2f || b.rect.right > W - 2f ||
            b.rect.top < brickAreaTop - 0.5f || b.rect.bottom > brickAreaBottom + 0.5f
        }

        if (bricks.count { it.type != BType.INDESTRUCTIBLE } < 5) {
            val cols = if (H > W) 9 else 12
            brickW = (W - 20f - GAP * (cols - 1)) / cols
            brickH = maxOf(H * 0.035f, 18f)
            for (col in 0 until minOf(cols, 8)) {
                val x = 10f + col * (brickW + GAP)
                bricks.add(Brick(RectF(x, brickAreaTop, x + brickW, brickAreaTop + brickH),
                    1, 1, BType.SIMPLE, simpleColors[col % simpleColors.size], shape))
            }
        }

        applyLevelStartRelics()
        levelStartPending = true
        lastDestroyedMs = System.currentTimeMillis()
    }

    private fun makeBrick(
        x: Float, y: Float, w: Float, h: Float, shape: Shape, ci: Int,
        resCh: Float, bonCh: Float, expCh: Float, indCh: Float, regCh: Float, iceCh: Float
    ): Brick {
        val type = pickType(expCh, indCh, regCh, iceCh, resCh, bonCh)
        val maxH = when (type) {
            BType.RESISTANT -> when {
                level < 3  -> 2
                level < 6  -> 2 + Random.nextInt(0, 2)
                level < 10 -> 3 + Random.nextInt(0, 2)
                else       -> 3 + Random.nextInt(0, 3)
            }
            BType.REGEN -> 2
            else -> 1
        }
        val baseColor = when (type) {
            BType.SIMPLE         -> simpleColors[ci % simpleColors.size]
            BType.RESISTANT      -> colorResistant
            BType.BONUS          -> colorBonus
            BType.EXPLOSIVE      -> colorExplosive
            BType.INDESTRUCTIBLE -> colorIndestructible
            BType.REGEN          -> colorRegen
            BType.ICE            -> colorIce
        }
        return Brick(RectF(x, y, x + w, y + h), maxH, maxH, type, baseColor, shape)
    }

    private fun buildGrid(
        shape: Shape, stagger: Boolean,
        resCh: Float, bonCh: Float, expCh: Float, indCh: Float, regCh: Float, iceCh: Float
    ) {
        val portrait = H > W
        val cols = if (portrait) 9 else 12
        val rows = if (portrait) 8 else 6
        val padX = if (portrait) 10f else W * 0.06f
        val areaW = W - padX * 2
        brickW = (areaW - GAP * (cols - 1)) / cols
        brickH = ((brickAreaBottom - brickAreaTop) - GAP * (rows - 1)) / rows
        val fill = minOf(0.55f + (level - 1) * 0.02f, 0.88f)
        var ci = 0
        for (row in 0 until rows) {
            val offset = if (stagger && row % 2 == 1) brickW * 0.5f else 0f
            for (col in 0 until cols) {
                if (Random.nextFloat() > fill) continue
                val x = padX + offset + col * (brickW + GAP)
                val y = brickAreaTop + row * (brickH + GAP)
                if (x + brickW > W - 6f) continue
                bricks.add(makeBrick(x, y, brickW, brickH, shape, ci++, resCh, bonCh, expCh, indCh, regCh, iceCh))
            }
        }
    }

    private fun buildArc(
        shape: Shape,
        resCh: Float, bonCh: Float, expCh: Float, regCh: Float, iceCh: Float
    ) {
        val centerX = W / 2f
        val centerY = brickAreaTop - H * 0.05f
        brickW = W * 0.075f
        brickH = H * 0.035f
        val rings = if (H > W) 5 else 4
        val baseR = H * 0.15f
        val step = H * 0.06f
        var ci = 0
        for (r in 0 until rings) {
            val radius = baseR + r * step
            val circumference = PI.toFloat() * radius
            val slots = (circumference / (brickW + 6f)).toInt().coerceAtLeast(5)
            val fill = minOf(0.65f + r * 0.05f, 0.9f)
            for (i in 0 until slots) {
                if (Random.nextFloat() > fill) continue
                val t = i.toFloat() / slots
                val angle = -PI.toFloat() + t * PI.toFloat()
                val cx = centerX + cos(angle) * radius
                val cy = centerY + sin(angle).absoluteValue * radius * 0.65f + brickAreaTop * 0.2f
                if (cy + brickH > brickAreaBottom) continue
                val x = cx - brickW / 2f
                val y = cy - brickH / 2f
                bricks.add(makeBrick(x, y, brickW, brickH, shape, ci++, resCh, bonCh, expCh, 0f, regCh, iceCh))
            }
        }
    }

    private fun buildCluster(
        shape: Shape,
        resCh: Float, bonCh: Float, expCh: Float, indCh: Float, regCh: Float, iceCh: Float
    ) {
        // Boss: dense core + scattered satellites + indestructible frame
        val cx = W / 2f; val cy = (brickAreaTop + brickAreaBottom) / 2f
        brickW = W * 0.08f
        brickH = H * 0.034f
        var ci = 0
        val coreCols = 5; val coreRows = 4
        val cw = brickW + GAP
        val ch = brickH + GAP
        for (r in 0 until coreRows) {
            for (c in 0 until coreCols) {
                val x = cx - coreCols * cw / 2f + c * cw
                val y = cy - coreRows * ch / 2f + r * ch
                val isEdge = r == 0 || r == coreRows - 1 || c == 0 || c == coreCols - 1
                val type = if (isEdge && Random.nextFloat() < 0.35f) BType.INDESTRUCTIBLE
                           else if (Random.nextFloat() < 0.55f) BType.RESISTANT
                           else BType.BONUS
                val maxH = if (type == BType.RESISTANT) 3 + Random.nextInt(0, 3) else 1
                val baseColor = when (type) {
                    BType.RESISTANT -> colorResistant
                    BType.INDESTRUCTIBLE -> colorIndestructible
                    BType.BONUS -> colorBonus
                    else -> simpleColors[ci % simpleColors.size]
                }
                bricks.add(Brick(RectF(x, y, x + brickW, y + brickH), maxH, maxH, type, baseColor, shape))
                ci++
            }
        }
        // Satellite arcs
        for (a in 0 until 12) {
            if (Random.nextFloat() < 0.3f) continue
            val ang = a / 12f * 2 * PI.toFloat()
            val rad = H * 0.18f
            val x = cx + cos(ang) * rad - brickW / 2f
            val y = cy + sin(ang) * rad * 0.7f - brickH / 2f
            if (y < brickAreaTop || y + brickH > brickAreaBottom) continue
            bricks.add(makeBrick(x, y, brickW, brickH, shape, ci++, resCh, bonCh, expCh, indCh, regCh, iceCh))
        }
    }

    private fun buildSpiral(
        shape: Shape,
        resCh: Float, bonCh: Float, expCh: Float, regCh: Float, iceCh: Float
    ) {
        val cx = W / 2f; val cy = (brickAreaTop + brickAreaBottom) / 2f
        brickW = W * 0.075f
        brickH = H * 0.033f
        var ci = 0
        val turns = 2.4f
        val steps = 48
        for (i in 0 until steps) {
            val t = i / steps.toFloat()
            val angle = t * turns * 2 * PI.toFloat()
            val radius = (H * 0.06f) + t * H * 0.22f
            val x = cx + cos(angle) * radius - brickW / 2f
            val y = cy + sin(angle) * radius * 0.7f - brickH / 2f
            if (y < brickAreaTop || y + brickH > brickAreaBottom) continue
            if (x < 6f || x + brickW > W - 6f) continue
            bricks.add(makeBrick(x, y, brickW, brickH, shape, ci++, resCh, bonCh, expCh, 0f, regCh, iceCh))
        }
    }

    private fun buildBoss(shape: Shape) {
        val bossLevel = level / 5
        val cx = W / 2f
        val cy = (brickAreaTop + brickAreaBottom) * 0.42f

        // Boss central : grosse brique mouvante avec beaucoup de PV
        val bossW = W * 0.28f
        val bossH = H * 0.055f
        val bossHp = 6 + bossLevel * 4
        val bossBrick = Brick(
            RectF(cx - bossW / 2, cy - bossH / 2, cx + bossW / 2, cy + bossH / 2),
            bossHp, bossHp, BType.RESISTANT, 0xFFDD2266.toInt(), shape,
            movePattern = MovePattern.HORIZONTAL,
            originX = cx, originY = cy,
            moveSpeed = 0.0008f + bossLevel * 0.0002f,
            moveRange = W * 0.28f
        )
        bricks.add(bossBrick)
        bossHpBar = bossHp.toFloat()
        bossHpBarMax = bossHp.toFloat()

        // Escortes : briques qui orbitent autour du boss
        val escorts = 4 + minOf(bossLevel, 4)
        val orbitR = H * 0.12f
        brickW = W * 0.065f
        brickH = H * 0.03f
        for (i in 0 until escorts) {
            val angle = i.toFloat() / escorts * 2f * PI.toFloat()
            val ex = cx + cos(angle) * orbitR
            val ey = cy + sin(angle) * orbitR * 0.6f
            val eHp = 2 + bossLevel
            val b = Brick(
                RectF(ex - brickW / 2, ey - brickH / 2, ex + brickW / 2, ey + brickH / 2),
                eHp, eHp, BType.RESISTANT, 0xFFCC4488.toInt(), shape,
                movePattern = MovePattern.ORBIT,
                originX = cx, originY = cy,
                moveSpeed = 0.0006f + bossLevel * 0.00015f,
                movePhase = angle,
                moveRange = orbitR
            )
            bricks.add(b)
        }

        // Sentinelles : briques qui montent/descendent sur les côtés
        val sentinels = 2 + minOf(bossLevel, 3)
        for (i in 0 until sentinels) {
            val side = if (i % 2 == 0) W * 0.15f else W * 0.85f
            val sy = brickAreaTop + (brickAreaBottom - brickAreaTop) * (0.2f + 0.6f * i.toFloat() / sentinels)
            val sHp = 2 + bossLevel / 2
            val b = Brick(
                RectF(side - brickW / 2, sy - brickH / 2, side + brickW / 2, sy + brickH / 2),
                sHp, sHp, BType.BONUS, 0xFFFFAA44.toInt(), shape,
                movePattern = MovePattern.VERTICAL,
                originX = side, originY = sy,
                moveSpeed = 0.0005f + bossLevel * 0.0001f,
                moveRange = H * 0.06f
            )
            bricks.add(b)
        }
    }

    private fun pickType(
        expCh: Float, indCh: Float, regCh: Float, iceCh: Float, resCh: Float, bonCh: Float
    ): BType {
        val r = Random.nextFloat()
        var acc = 0f
        acc += bonCh; if (r < acc) return BType.BONUS
        acc += expCh; if (r < acc) return BType.EXPLOSIVE
        acc += indCh; if (r < acc) return BType.INDESTRUCTIBLE
        acc += regCh; if (r < acc) return BType.REGEN
        acc += iceCh; if (r < acc) return BType.ICE
        acc += resCh; if (r < acc) return BType.RESISTANT
        return BType.SIMPLE
    }

    private fun applyLevelStartRelics() {
        if (RelicId.SHIELD_START in ownedRelics) shields = minOf(shields + 1, 3)
        if (RelicId.MAGNET_START in ownedRelics) timerMagnet = maxOf(timerMagnet, 4_000L)
        if (RelicId.PIERCE_START in ownedRelics) timerPierce = maxOf(timerPierce, 3_000L)
        if (RelicId.GUARDIAN_LASER in ownedRelics) { timerLaser = maxOf(timerLaser, 4_000L); laserCooldown = 0L }
        if (RelicId.FIRE_HEART in ownedRelics) timerFire = maxOf(timerFire, 3_000L)
    }

    // ── Ball helpers ─────────────────────────────────────────────
    private fun resetBallOnPaddle() {
        balls.clear()
        balls.add(Ball(paddle.centerX(), paddle.top - ballR * 1.5f, 0f, 0f, stuck = true, stickOffset = 0f))
    }

    private fun launchBall() {
        val stuckBalls = balls.filter { it.stuck }
        if (stuckBalls.isNotEmpty()) {
            for (b in stuckBalls) {
                b.stuck = false
                val deg = Random.nextDouble(-96.0, -84.0)
                val rad = Math.toRadians(deg).toFloat()
                val spd = ballSpeed * spdMult
                b.vx = cos(rad) * spd; b.vy = sin(rad) * spd
            }
            return
        }
        val b = balls.firstOrNull() ?: return
        if (b.vx == 0f && b.vy == 0f) {
            val deg = Random.nextDouble(-96.0, -84.0)
            val rad = Math.toRadians(deg).toFloat()
            val spd = ballSpeed * spdMult
            b.vx = cos(rad) * spd
            b.vy = sin(rad) * spd
        }
        if (levelStartPending) {
            levelStartPending = false
            if (RelicId.MULTI_START in ownedRelics || meta.shopMultiStart > 0) {
                val src = balls.first()
                val spd = hypot(src.vx, src.vy)
                val a = atan2(src.vy, src.vx)
                balls.add(Ball(src.x, src.y, cos(a + 0.25f) * spd, sin(a + 0.25f) * spd))
            }
        }
    }

    // ── Thread ───────────────────────────────────────────────────
    private fun startThread() {
        running = true
        thread = Thread({
            var last = System.currentTimeMillis()
            while (running) {
                val now   = System.currentTimeMillis()
                val delta = minOf(now - last, MAX_DELTA_MS)
                last = now
                if (state == State.PLAYING) update(delta)
                else updateAmbient(delta)
                val canvas = holder.lockCanvas() ?: continue
                try { drawFrame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
                val sleep = FRAME_MS - (System.currentTimeMillis() - now)
                if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
            }
        }, "ParticulesThread").also { it.isDaemon = true }
        thread!!.start()
    }

    private fun stopThread() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun updateAmbient(dt: Long) {
        val f = dt.toFloat()
        for (s in stars) {
            s.y += s.depth * 0.015f * f
            if (s.y > H) { s.y = -2f; s.x = Random.nextFloat() * W }
        }
        nebulaPulse += f / 2600f
        if (shakeAmount > 0f) shakeAmount = (shakeAmount - shakeDecay * f).coerceAtLeast(0f)
    }

    private fun update(dt: Long) {
        val f = dt.toFloat()

        for (s in stars) {
            s.y += s.depth * 0.03f * f
            if (s.y > H) { s.y = -2f; s.x = Random.nextFloat() * W }
        }
        nebulaPulse += f / 2400f

        val paddleSpeedBase = 0.040f
        val paddleMoveMul = (if (RelicId.PADDLE_GRAVITY in ownedRelics) 1.6f else 1f) *
                            (if (RelicId.PADDLE_SPRINT in ownedRelics) 1.5f else 1f)
        val diff = paddleTarget - paddle.centerX()
        val maxStep = W * paddleSpeedBase * paddleMoveMul * f / 16f
        val step = diff.coerceIn(-maxStep, maxStep)
        applyPaddleMove(paddle.centerX() + step)
        paddleVelX = (paddle.centerX() - paddleLastX) / f.coerceAtLeast(1f)
        paddleLastX = paddle.centerX()

        if (abs(paddleVelX) > W * 0.0012f && paddleGhosts.size < 6) {
            paddleGhosts.add(PaddleGhost(paddle.centerX(), paddle.top, 1f, paddle.width()))
        }

        timerExtend   = (timerExtend  - dt).coerceAtLeast(0L)
        timerLaser    = (timerLaser   - dt).coerceAtLeast(0L)
        val wasSpeed  = timerSpeed > 0L
        timerSpeed    = (timerSpeed   - dt).coerceAtLeast(0L)
        val wasSlow   = timerSlow > 0L
        timerSlow     = (timerSlow    - dt).coerceAtLeast(0L)
        timerFloor    = (timerFloor   - dt).coerceAtLeast(0L)
        timerPierce   = (timerPierce  - dt).coerceAtLeast(0L)
        timerFire     = (timerFire    - dt).coerceAtLeast(0L)
        timerMagnet   = (timerMagnet  - dt).coerceAtLeast(0L)

        if (timerExtend == 0L) syncPaddleWidth()
        if ((wasSpeed && timerSpeed == 0L) || (wasSlow && timerSlow == 0L)) restoreSpeed()

        if (comboTimer > 0L) {
            comboTimer = (comboTimer - dt).coerceAtLeast(0L)
            if (comboTimer == 0L) combo = 0
        }

        if (shakeAmount > 0f) shakeAmount = (shakeAmount - shakeDecay * f).coerceAtLeast(0f)
        if (flashWhite > 0f)  flashWhite  = (flashWhite - 0.006f * f).coerceAtLeast(0f)

        if (timerLaser > 0) {
            laserCooldown -= dt
            if (laserCooldown <= 0) { laserCooldown = LASER_INTERVAL; fireLasers() }
        }

        for (b in bricks) {
            if (b.flash > 0f) b.flash = (b.flash - f / 250f).coerceAtLeast(0f)
            if (b.shake > 0f) b.shake = (b.shake - f / 200f).coerceAtLeast(0f)
            if (b.type == BType.REGEN && b.hits in 1 until b.maxHits) {
                b.regenTimer += dt
                if (b.regenTimer > 4500L) { b.hits = b.maxHits; b.regenTimer = 0L; b.flash = 1f }
            }
            if (b.movePattern != MovePattern.NONE) {
                b.movePhase += b.moveSpeed * f
                val w = b.rect.width(); val h = b.rect.height()
                when (b.movePattern) {
                    MovePattern.HORIZONTAL -> {
                        val nx = b.originX + sin(b.movePhase) * b.moveRange
                        b.rect.set(nx - w / 2, b.rect.top, nx + w / 2, b.rect.bottom)
                    }
                    MovePattern.VERTICAL -> {
                        val ny = b.originY + sin(b.movePhase) * b.moveRange
                        b.rect.set(b.rect.left, ny - h / 2, b.rect.right, ny + h / 2)
                    }
                    MovePattern.ORBIT -> {
                        val nx = b.originX + cos(b.movePhase) * b.moveRange
                        val ny = b.originY + sin(b.movePhase) * b.moveRange * 0.6f
                        b.rect.set(nx - w / 2, ny - h / 2, nx + w / 2, ny + h / 2)
                    }
                    else -> {}
                }
            }
        }
        if (level % 5 == 0) {
            bossHpBar = bricks.filter { it.type != BType.INDESTRUCTIBLE && it.hits > 0 }
                .sumOf { it.hits }.toFloat()
        }

        // Balls (iterator for alloc-free removal)
        val bIter = balls.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            if (b.stuck) {
                b.x = paddle.centerX() + b.stickOffset
                b.y = paddle.top - ballR * 1.2f
                pushTrail(b); continue
            }
            if (b.vx == 0f && b.vy == 0f) {
                b.x = paddle.centerX(); b.y = paddle.top - ballR * 1.5f
                pushTrail(b); continue
            }
            moveBall(b, f)
            pushTrail(b)
            if (b.y > H + ballR * 3) bIter.remove()
        }

        if (balls.isEmpty()) {
            when {
                timerFloor > 0 -> {
                    timerFloor = 0
                    val rad = Math.toRadians(Random.nextDouble(-96.0, -84.0)).toFloat()
                    val spd = ballSpeed * spdMult
                    balls.add(Ball(paddle.centerX(), paddle.top - ballR * 1.5f, cos(rad) * spd, sin(rad) * spd))
                }
                shields > 0 -> {
                    shields--
                    addFloatText(paddle.centerX(), paddle.top - 30f, "BOUCLIER !", 0xFF4AC5FF.toInt(), 1.2f)
                    val rad = Math.toRadians(Random.nextDouble(-96.0, -84.0)).toFloat()
                    val spd = ballSpeed * spdMult
                    balls.add(Ball(paddle.centerX(), paddle.top - ballR * 1.5f, cos(rad) * spd, sin(rad) * spd))
                    shakeAmount = maxOf(shakeAmount, W * 0.004f)
                }
                else -> onLifeLost()
            }
        }

        // Lasers
        val lSpd = W * 0.0024f
        val lIter = lasers.iterator()
        while (lIter.hasNext()) {
            val l = lIter.next()
            l.y -= lSpd * f
            if (l.y < -20f) { lIter.remove(); continue }
            val lr = RectF(l.x - 2f, l.y - 12f, l.x + 2f, l.y)
            val hit = bricks.firstOrNull { it.hits > 0 && it.type != BType.INDESTRUCTIBLE && RectF.intersects(lr, it.rect) }
            if (hit != null) { damageBrick(hit); lIter.remove() }
        }

        // Power-ups falling
        val pIter = powerUps.iterator()
        while (pIter.hasNext()) {
            val pu = pIter.next()
            pu.y += pu.vy * f
            pu.wob += f / 180f
            if (pu.y > H + 60f) { pIter.remove(); continue }
            val pr = RectF(pu.x - 22f, pu.y - 22f, pu.x + 22f, pu.y + 22f)
            if (RectF.intersects(pr, paddle)) { collectPowerUp(pu.type); pIter.remove() }
        }

        // Sparks
        val sIter = sparks.iterator()
        while (sIter.hasNext()) {
            val s = sIter.next()
            s.x += s.vx * f; s.y += s.vy * f
            s.vy += 0.00085f * f
            s.life -= f / 700f
            if (s.life <= 0f) sIter.remove()
        }

        // Shockwaves
        val wIter = shockwaves.iterator()
        while (wIter.hasNext()) {
            val sw = wIter.next()
            sw.r += (sw.maxR - sw.r) * 0.12f * f / 16f
            sw.life -= f / 500f
            if (sw.life <= 0f) wIter.remove()
        }

        // Floating texts
        val fIter = floatTexts.iterator()
        while (fIter.hasNext()) {
            val ft = fIter.next()
            ft.y -= 0.04f * f
            ft.life -= f / 900f
            if (ft.life <= 0f) fIter.remove()
        }

        // Paddle ghosts
        val gIter = paddleGhosts.iterator()
        while (gIter.hasNext()) {
            val g = gIter.next()
            g.life -= f / 180f
            if (g.life <= 0f) gIter.remove()
        }

        if (bricks.none { it.hits > 0 && it.type != BType.INDESTRUCTIBLE }) onLevelClear()
    }

    private fun pushTrail(b: Ball) {
        b.trailHead = (b.trailHead + 1) % TRAIL_MAX
        b.trailX[b.trailHead] = b.x
        b.trailY[b.trailHead] = b.y
        if (b.trailLen < TRAIL_MAX) b.trailLen++
    }

    private fun restoreSpeed() {
        val target = when {
            timerSpeed > 0L -> 1.35f
            timerSlow > 0L  -> 0.65f
            else -> 1f
        }
        if (spdMult == target) return
        val ratio = target / spdMult
        spdMult = target
        for (b in balls) {
            if (b.stuck || (b.vx == 0f && b.vy == 0f)) continue
            b.vx *= ratio; b.vy *= ratio
        }
    }

    private fun moveBall(b: Ball, dt: Float) {
        val prevY = b.y
        val prevX = b.x
        b.x += b.vx * dt; b.y += b.vy * dt

        if (b.x - ballR < 0f)  { b.x = ballR;     b.vx =  abs(b.vx) }
        if (b.x + ballR > W)   { b.x = W - ballR; b.vx = -abs(b.vx) }
        if (b.y - ballR < 0f)  { b.y = ballR;     b.vy =  abs(b.vy) }

        if (timerFloor > 0L && b.vy > 0f) {
            val floorH = maxOf(H * 0.06f, 10f)
            val floorTop = H - floorH
            if (b.y + ballR >= floorTop) {
                b.y = floorTop - ballR
                b.vy = -abs(b.vy)
            }
        }

        // Détection de collision raquette avec test "swept" pour éviter le tunneling à haute vitesse
        var paddleHit = false
        if (b.vy > 0f) {
            tmpBallRect.set(b.x - ballR, b.y - ballR, b.x + ballR, b.y + ballR)
            if (RectF.intersects(tmpBallRect, paddle)) {
                paddleHit = true
            } else {
                val prevBottom = prevY + ballR
                val newBottom = b.y + ballR
                if (prevBottom <= paddle.top && newBottom >= paddle.top) {
                    val dy = b.y - prevY
                    val t = if (dy != 0f) ((paddle.top - prevBottom) / dy).coerceIn(0f, 1f) else 0f
                    val xCross = prevX + (b.x - prevX) * t
                    if (xCross + ballR >= paddle.left && xCross - ballR <= paddle.right) {
                        b.x = xCross
                        paddleHit = true
                    }
                }
            }
        }
        if (paddleHit) {
            b.y = paddle.top - ballR
            val pos = ((b.x - paddle.left) / paddle.width()).coerceIn(0f, 1f)
            val angle = Math.toRadians((-155.0 + pos * 130.0)).toFloat()
            val spd = hypot(b.vx, b.vy).coerceAtLeast(ballSpeed * 0.8f)
            val spinMul = if (RelicId.PADDLE_GRAVITY in ownedRelics) 0.24f else 0.12f
            b.vx = cos(angle) * spd + paddleVelX * spinMul
            b.vy = sin(angle) * spd
            val s2 = hypot(b.vx, b.vy)
            if (s2 > 0f) { b.vx = b.vx / s2 * spd; b.vy = b.vy / s2 * spd }
            if (combo > bestCombo) bestCombo = combo
            combo = 0; comboTimer = 0L

            if (timerMagnet > 0L) {
                b.stuck = true
                b.stickOffset = b.x - paddle.centerX()
                b.vx = 0f; b.vy = 0f
            }
        }

        tmpBallRect.set(b.x - ballR, b.y - ballR, b.x + ballR, b.y + ballR)
        val hit = bricks.firstOrNull { it.hits > 0 && RectF.intersects(tmpBallRect, it.rect) } ?: return
        val pierceActive = timerPierce > 0L && hit.type != BType.INDESTRUCTIBLE

        if (!pierceActive) {
            val ol  = tmpBallRect.right  - hit.rect.left
            val or_ = hit.rect.right - tmpBallRect.left
            val ot  = tmpBallRect.bottom - hit.rect.top
            val ob  = hit.rect.bottom - tmpBallRect.top
            val min = minOf(ol, or_, ot, ob)
            when (min) {
                ol  -> { b.x = hit.rect.left  - ballR; b.vx = -abs(b.vx) }
                or_ -> { b.x = hit.rect.right + ballR; b.vx =  abs(b.vx) }
                ot  -> { b.y = hit.rect.top   - ballR; b.vy = -abs(b.vy) }
                else -> { b.y = hit.rect.bottom + ballR; b.vy = abs(b.vy) }
            }
        }

        when (hit.type) {
            BType.INDESTRUCTIBLE -> {
                hit.shake = 1f
                spawnSparks(hit, count = 4)
            }
            BType.ICE -> {
                damageBrick(hit)
                val s = hypot(b.vx, b.vy)
                if (s > ballSpeed * 0.5f) { b.vx *= 0.75f; b.vy *= 0.75f }
            }
            else -> {
                damageBrick(hit)
                if (RelicId.HEAVY_BALL in ownedRelics && hit.hits > 0 && Random.nextFloat() < 0.25f) {
                    damageBrick(hit)
                }
            }
        }

        if (timerFire > 0L && hit.type != BType.INDESTRUCTIBLE) {
            val cx = hit.rect.centerX(); val cy = hit.rect.centerY()
            val rad = maxOf(brickW, brickH) * 1.6f
            for (n in bricks.toList()) {
                if (n === hit || n.hits <= 0 || n.type == BType.INDESTRUCTIBLE) continue
                val d = hypot(n.rect.centerX() - cx, n.rect.centerY() - cy)
                if (d < rad) { n.flash = 1f; damageBrick(n) }
            }
            shockwaves.add(Shockwave(cx, cy, brickW, rad, 1f, 0xFFFF6A2A.toInt()))
        }
    }

    private fun damageBrick(brick: Brick) {
        if (brick.type == BType.INDESTRUCTIBLE) return
        brick.hits--
        brick.flash = 1f
        brick.shake = 1f
        brick.regenTimer = 0L
        spawnSparks(brick)
        if (brick.hits <= 0) {
            music?.onBrickDestroyed(brick.type, level)
            bricks.remove(brick)
            combo++
            comboTimer = comboGrace
            panicLatched = false
            lastDestroyedMs = System.currentTimeMillis()
            val mult = 1 + combo / 4
            val base = when (brick.type) {
                BType.SIMPLE        -> 120L
                BType.RESISTANT     -> (160L + brick.maxHits * 40L)
                BType.BONUS         -> 160L
                BType.EXPLOSIVE     -> 180L
                BType.REGEN         -> 260L
                BType.ICE           -> 200L
                BType.INDESTRUCTIBLE -> 0L
            }
            val gained = (base * level * mult * scoreMult).toLong()
            score += gained

            var goldGain = 1L
            if (RelicId.COLLECTOR in ownedRelics) goldGain += 1
            if (meta.shopGoldMagnet > 0) goldGain = (goldGain * 1.5f).toLong().coerceAtLeast(goldGain + 1)
            if (RelicId.COMBO_GREED in ownedRelics && combo >= 5) goldGain *= 2
            if (RelicId.BOSS_SLAYER in ownedRelics && level % 5 == 0) goldGain *= 2
            gold += goldGain

            if (combo == 5 || combo == 10 || combo == 15) {
                spawnComboBurst(brick.rect.centerX(), brick.rect.centerY())
                music?.onCombo(combo)
            }
            if (combo >= 4) addFloatText(brick.rect.centerX(), brick.rect.centerY(),
                "x${mult} combo ${combo}", brick.baseColor, 1f)

            when (brick.type) {
                BType.BONUS -> spawnPowerUp(brick.rect.centerX(), brick.rect.centerY())
                BType.SIMPLE -> if (RelicId.LUCKY in ownedRelics && Random.nextFloat() < 0.10f) {
                    spawnPowerUp(brick.rect.centerX(), brick.rect.centerY())
                }
                BType.EXPLOSIVE -> explode(brick)
                else -> {}
            }
        } else {
            music?.onBrickHit()
        }
    }

    private fun explode(brick: Brick) {
        val cx = brick.rect.centerX(); val cy = brick.rect.centerY()
        val mul = if (RelicId.EXPLOSIVE_FUSE in ownedRelics) 1.5f else 1f
        val rad = maxOf(brickW, brickH) * 2.1f * mul
        shockwaves.add(Shockwave(cx, cy, brickW * 0.5f, rad, 1f, 0xFFFF5522.toInt()))
        shakeAmount = maxOf(shakeAmount, W * 0.006f)
        repeat(22) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = Random.nextFloat() * 0.55f + 0.15f
            sparks.add(Spark(cx, cy, cos(a) * sp, sin(a) * sp,
                Random.nextFloat() * 0.6f + 0.6f, 0xFFFF9944.toInt(), 1.4f))
        }
        for (n in bricks.toList()) {
            if (n === brick || n.hits <= 0 || n.type == BType.INDESTRUCTIBLE) continue
            val d = hypot(n.rect.centerX() - cx, n.rect.centerY() - cy)
            if (d < rad) {
                n.flash = 1f
                damageBrick(n)
            }
        }
    }

    private fun spawnComboBurst(x: Float, y: Float) {
        val colors = intArrayOf(0xFFFFE066.toInt(), 0xFFFFAA44.toInt(), 0xFFFF66AA.toInt())
        repeat(18) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = Random.nextFloat() * 0.45f + 0.2f
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp,
                Random.nextFloat() * 0.7f + 0.7f, colors[Random.nextInt(colors.size)], 1.2f))
        }
        shockwaves.add(Shockwave(x, y, 10f, W * 0.15f, 1f, 0xFFFFE066.toInt()))
    }

    private fun spawnSparks(brick: Brick, count: Int = -1) {
        val cx = brick.rect.centerX(); val cy = brick.rect.centerY()
        val n = if (count > 0) count else Random.nextInt(5, 10)
        if (sparks.size + n > MAX_SPARKS) repeat(n) { if (sparks.isNotEmpty()) sparks.removeAt(0) }
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val spd = Random.nextFloat() * 0.28f + 0.08f
            sparks.add(Spark(cx, cy, cos(a) * spd, sin(a) * spd,
                Random.nextFloat() * 0.5f + 0.5f, brick.baseColor))
        }
    }

    private fun spawnPowerUp(x: Float, y: Float) {
        val type = PType.values()[Random.nextInt(PType.values().size)]
        powerUps.add(PowerUp(x, y, W * 0.00038f, type))
    }

    private fun collectPowerUp(type: PType) {
        addFloatText(paddle.centerX(), paddle.top - 20f, labelFor(type), puColors[type] ?: 0xFFFFFFFF.toInt(), 1f)
        music?.onPowerUp(type)
        flashWhite = 0.25f
        val stack = meta.shopStackTimers > 0
        when (type) {
            PType.EXTEND -> {
                timerExtend = if (stack) timerExtend + 12_000L else 12_000L
                syncPaddleWidth()
            }
            PType.MULTIBALL -> {
                val extra = mutableListOf<Ball>()
                for (b in balls) {
                    if (b.stuck || (b.vx == 0f && b.vy == 0f)) continue
                    val spd = hypot(b.vx, b.vy)
                    val a = atan2(b.vy, b.vx)
                    extra.add(Ball(b.x, b.y, cos(a + 0.4f) * spd, sin(a + 0.4f) * spd))
                    extra.add(Ball(b.x, b.y, cos(a - 0.4f) * spd, sin(a - 0.4f) * spd))
                }
                balls.addAll(extra)
            }
            PType.LASER   -> { timerLaser = if (stack) timerLaser + 9_000L else 9_000L; laserCooldown = 0L }
            PType.SPEED   -> { timerSlow = 0L; timerSpeed = if (stack) timerSpeed + 8_000L else 8_000L; restoreSpeed() }
            PType.SLOW    -> { timerSpeed = 0L; timerSlow = if (stack) timerSlow + 7_000L else 7_000L; restoreSpeed() }
            PType.FLOOR   -> { timerFloor = if (stack) timerFloor + 10_000L else 10_000L }
            PType.PIERCE  -> { timerPierce = if (stack) timerPierce + 9_000L else 9_000L }
            PType.FIRE    -> { timerFire = if (stack) timerFire + 9_000L else 9_000L }
            PType.MAGNET  -> { timerMagnet = if (stack) timerMagnet + 11_000L else 11_000L }
            PType.SHIELD  -> { shields = minOf(shields + 1, 3) }
        }
    }

    private fun labelFor(type: PType) = when (type) {
        PType.EXTEND -> "PADDLE+"; PType.MULTIBALL -> "MULTI"
        PType.LASER -> "LASER";    PType.SPEED -> "RAPIDE"
        PType.SLOW -> "LENT";      PType.FLOOR -> "SOL"
        PType.PIERCE -> "PERFORE"; PType.FIRE -> "FEU"
        PType.MAGNET -> "AIMANT";  PType.SHIELD -> "BOUCLIER"
    }

    private fun addFloatText(x: Float, y: Float, text: String, color: Int, size: Float) {
        floatTexts.add(FloatText(x, y, 1f, text, color, size))
        if (floatTexts.size > 18) floatTexts.removeAt(0)
    }

    private fun fireLasers() {
        val cx = paddle.centerX()
        lasers.add(LaserBeam(cx - paddle.width() * 0.35f, paddle.top))
        lasers.add(LaserBeam(cx + paddle.width() * 0.35f, paddle.top))
    }

    private fun effectiveBaseWidth(): Float {
        val rm = if (RelicId.PADDLE_ZEPHYR in ownedRelics) 1.15f else 1f
        val sm = 1f + meta.shopWidePaddle * 0.12f
        return paddleBaseW * rm * sm
    }

    private fun syncPaddleWidth() {
        val w = effectiveBaseWidth() * (if (timerExtend > 0) 1.6f else 1f)
        val cx = paddle.centerX()
        paddle.set(cx - w / 2, paddleY, cx + w / 2, paddleY + paddleH)
    }

    private fun applyPaddleMove(cx: Float) {
        val w = paddle.width()
        val ncx = cx.coerceIn(w / 2f, W - w / 2f)
        paddle.set(ncx - w / 2, paddleY, ncx + w / 2, paddleY + paddleH)
    }

    // ── State transitions ─────────────────────────────────────────
    fun startGame() {
        score = 0L; level = 1; gold = 0L
        ownedRelics.clear()
        secondChanceUsed = false
        scoreMult = 1f
        comboGrace = COMBO_GRACE_BASE
        maxLives = BASE_MAX_LIVES + meta.shopExtraLives
        lives = maxLives
        spdMult = 1f
        combo = 0; comboTimer = 0L; bestCombo = 0
        timerExtend = 0L; timerLaser = 0L; timerSpeed = 0L; timerSlow = 0L
        timerFloor = 0L; timerPierce = 0L; timerFire = 0L; timerMagnet = 0L
        shields = if (meta.shopStartShield > 0) 1 else 0
        shakeAmount = 0f; flashWhite = 0f
        ballBaseSpd = (W + H) / 2f * 0.000456f * (1f - meta.shopSlowBall * 0.08f)
        ballSpeed = ballBaseSpeed()
        lastDestroyedMs = System.currentTimeMillis()
        panicLatched = false; fingerBreakReadyAtMs = 0L
        generateLevel(); resetBallOnPaddle(); syncPaddleWidth()
        state = State.READY
        music?.onLevelChanged(1)
    }

    private fun ballBaseSpeed() = ballBaseSpd * (1.15f).pow(minOf(level - 1, 9).toFloat())

    private fun onLevelClear() {
        music?.onLevelClear()
        val clearedLevel = level
        if (level > meta.highestLevel) meta = meta.copy(highestLevel = level)
        panicLatched = false; fingerBreakReadyAtMs = 0L
        level++
        music?.onLevelChanged(level)
        ballSpeed = ballBaseSpeed()
        spdMult = 1f
        timerExtend = 0L; timerLaser = 0L; timerSpeed = 0L; timerSlow = 0L
        timerFloor = 0L; timerPierce = 0L; timerFire = 0L; timerMagnet = 0L
        syncPaddleWidth()

        if (clearedLevel % 5 == 0) {
            state = State.SHOP
            return
        }

        relicChoices = RelicCatalog.roll(
            ownedRelics, 3,
            rareBoost = ((level - 1) % 5 == 0)
        )
        if (relicChoices.isEmpty()) {
            generateLevel(); resetBallOnPaddle(); syncPaddleWidth()
            state = State.READY
            return
        }
        state = State.LEVEL_CLEAR
    }

    private fun onLifeLost() {
        lives--
        combo = 0; comboTimer = 0L
        timerExtend = 0L; timerLaser = 0L; timerSpeed = 0L; timerSlow = 0L
        timerFloor = 0L; timerPierce = 0L; timerFire = 0L; timerMagnet = 0L
        syncPaddleWidth(); powerUps.clear(); lasers.clear()
        shakeAmount = maxOf(shakeAmount, W * 0.008f)
        if (lives <= 0) {
            if (RelicId.SECOND_CHANCE in ownedRelics && !secondChanceUsed) {
                secondChanceUsed = true
                lives = 1
                addFloatText(W / 2, H / 2, "SECONDE CHANCE !", 0xFFFFE066.toInt(), 1.5f)
                music?.onLifeLost()
                state = State.LIFE_LOST
                resetBallOnPaddle()
                return
            }
            music?.onGameOver()
            state = State.GAME_OVER
            commitMeta()
        } else {
            music?.onLifeLost()
            state = State.LIFE_LOST
            resetBallOnPaddle()
        }
    }

    private fun commitMeta() {
        val newHigh = maxOf(meta.highScore, score)
        val newBestCombo = maxOf(meta.bestCombo, bestCombo)
        val xpGain = score / 10L
        meta = meta.copy(
            highScore = newHigh,
            runsPlayed = meta.runsPlayed + 1,
            totalGold = meta.totalGold + gold,
            totalXp = meta.totalXp + xpGain,
            bestCombo = newBestCombo
        )
        gold = 0L
        onMetaUpdate?.invoke(meta)
    }

    fun pause()    { if (state == State.PLAYING) state = State.PAUSED }
    fun resume()   { if (state == State.PAUSED)  state = State.PLAYING }
    fun isPaused() = state == State.PAUSED
    fun isPlaying() = state == State.PLAYING

    private fun pickRelic(relic: Relic) {
        ownedRelics.add(relic.id)
        applyRelicInstant(relic.id)
        addFloatText(W / 2, H * 0.4f, relic.name.uppercase(), 0xFFFFE066.toInt(), 1.4f)
        generateLevel()
        resetBallOnPaddle()
        syncPaddleWidth()
        relicChoices = emptyList()
        state = State.READY
    }

    private fun applyRelicInstant(id: RelicId) {
        when (id) {
            RelicId.STONE_HEART  -> { maxLives += 1; lives = minOf(lives + 1, maxLives) }
            RelicId.PRESSURE     -> { ballBaseSpd *= 0.95f; ballSpeed = ballBaseSpeed() }
            RelicId.RESONANCE    -> { comboGrace = (COMBO_GRACE_BASE * 1.5f).toLong() }
            RelicId.PADDLE_ZEPHYR-> syncPaddleWidth()
            else -> {}
        }
    }

    // ── Touch ─────────────────────────────────────────────────────
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        fingerX = ev.x; fingerY = ev.y
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (state == State.LEVEL_CLEAR && relicChoices.isNotEmpty()) {
                    if (skipRelicRect.contains(ev.x, ev.y)) {
                        generateLevel(); resetBallOnPaddle(); syncPaddleWidth()
                        relicChoices = emptyList(); state = State.READY
                        return true
                    }
                    for (i in relicChoices.indices) {
                        if (choiceRects[i].contains(ev.x, ev.y)) {
                            pickRelic(relicChoices[i])
                            return true
                        }
                    }
                    return true
                }
                paddleTarget = ev.x
                when (state) {
                    State.READY, State.LIFE_LOST -> launchOnUp = true
                    State.PAUSED     -> state = State.PLAYING
                    State.LEVEL_CLEAR -> {}
                    State.GAME_OVER  -> { startGame(); launchOnUp = false }
                    State.SHOP -> {
                        if (shopBackRect.contains(ev.x, ev.y)) {
                            generateLevel(); resetBallOnPaddle(); syncPaddleWidth()
                            state = State.READY; return true
                        }
                        for (i in shopItems.indices) {
                            if (shopRects[i].contains(ev.x, ev.y)) {
                                buyShopItem(shopItems[i]); return true
                            }
                        }
                        return true
                    }
                    State.PLAYING    -> {
                        if (balls.any { it.stuck }) { launchBall(); return true }
                        // Panic mode: finger damage (1x toutes les 5s)
                        if (panicActive() && System.currentTimeMillis() >= fingerBreakReadyAtMs) {
                            val touch = 18f // tolérance de toucher en px
                            val hit = bricks.firstOrNull { b ->
                                b.hits > 0 && b.type != BType.INDESTRUCTIBLE &&
                                ev.x >= b.rect.left - touch && ev.x <= b.rect.right + touch &&
                                ev.y >= b.rect.top  - touch && ev.y <= b.rect.bottom + touch
                            }
                            if (hit != null) {
                                damageBrick(hit)
                                fingerBreakReadyAtMs = System.currentTimeMillis() + 5_000L
                                shockwaves.add(Shockwave(ev.x, ev.y, 8f, W * 0.08f, 1f, 0xFFFFE066.toInt()))
                                repeat(8) {
                                    val a = Random.nextFloat() * 2f * PI.toFloat()
                                    val sp = Random.nextFloat() * 0.3f + 0.1f
                                    sparks.add(Spark(ev.x, ev.y, cos(a) * sp, sin(a) * sp,
                                        0.8f, 0xFFFFE066.toInt(), 1.3f))
                                }
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> if (state != State.LEVEL_CLEAR) paddleTarget = ev.x
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                fingerX = -1f; fingerY = -1f
                if (launchOnUp && (state == State.READY || state == State.LIFE_LOST)) {
                    state = State.PLAYING
                    launchBall()
                }
                launchOnUp = false
            }
            MotionEvent.ACTION_CANCEL -> {
                fingerX = -1f; fingerY = -1f
                launchOnUp = false
            }
        }
        return true
    }

    // ── Draw ──────────────────────────────────────────────────────
    private fun drawFrame(canvas: Canvas) {
        canvas.drawRect(0f, 0f, W, H, pNebula)
        drawNebulaPulse(canvas)
        drawStars(canvas)

        val saved = canvas.save()
        if (shakeAmount > 0f && state == State.PLAYING) {
            val dx = (Random.nextFloat() - 0.5f) * shakeAmount
            val dy = (Random.nextFloat() - 0.5f) * shakeAmount
            canvas.translate(dx, dy)
        }

        drawFloor(canvas)
        drawBricks(canvas)
        drawShockwaves(canvas)
        drawSparks(canvas)
        drawPowerUps(canvas)
        drawLasers(canvas)
        drawPaddleGhosts(canvas)
        drawPaddle(canvas)
        drawBalls(canvas)
        drawFloatTexts(canvas)

        canvas.restoreToCount(saved)

        drawHud(canvas)
        drawTimerBadges(canvas)
        drawComboBanner(canvas)
        drawPanicIndicator(canvas)
        drawFlash(canvas)
        drawOverlay(canvas)
    }

    private fun drawNebulaPulse(canvas: Canvas) {
        // Two soft glowing blobs that drift gently
        val t = nebulaPulse
        val x1 = W * (0.3f + 0.12f * sin(t.toDouble()).toFloat())
        val y1 = H * (0.25f + 0.08f * cos(t.toDouble() * 0.7).toFloat())
        val x2 = W * (0.75f - 0.1f * cos(t.toDouble() * 0.5).toFloat())
        val y2 = H * (0.7f + 0.1f * sin(t.toDouble() * 0.8).toFloat())
        val r1 = W * 0.35f; val r2 = W * 0.3f
        pPath.shader = RadialGradient(x1, y1, r1,
            0x33AA4FFF.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawCircle(x1, y1, r1, pPath)
        pPath.shader = RadialGradient(x2, y2, r2,
            0x33FF5FAA.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawCircle(x2, y2, r2, pPath)
        pPath.shader = null
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            pStar.color = s.hue
            pStar.alpha = (s.depth * 220).toInt().coerceIn(90, 240)
            canvas.drawCircle(s.x, s.y, s.size * s.depth, pStar)
            if (s.depth > 0.7f) {
                pStar.alpha = 60
                canvas.drawCircle(s.x, s.y, s.size * s.depth * 2f, pStar)
            }
        }
    }

    private fun drawHud(canvas: Canvas) {
        pHud.textAlign = Paint.Align.LEFT; pHud.color = 0xFFFFFFFF.toInt()
        canvas.drawText("Niv.$level", 18f, pHud.textSize + 6f, pHud)
        pHud.textAlign = Paint.Align.CENTER
        canvas.drawText(fmtScore(score), W / 2, pHud.textSize + 6f, pHud)
        pHud.textSize *= 0.7f
        pHud.color = 0xFFFFD55A.toInt()
        canvas.drawText("◆ $gold", W / 2, pHud.textSize * 2f + 12f, pHud)
        pHud.textSize /= 0.7f
        pHud.color = 0xFFFFFFFF.toInt()

        val r = pHud.textSize * 0.33f
        for (i in 0 until maxLives) {
            val cx = W - 18f - i * (r * 2.6f) - r
            val cy = pHud.textSize / 2 + 6f
            if (i < lives) {
                pBall.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(cx, cy, r, pBall)
            } else {
                pCrack.color = 0x44FFFFFF
                canvas.drawCircle(cx, cy, r, pCrack)
            }
        }
        if (shields > 0) {
            for (i in 0 until shields) {
                val cx = W - 18f - (maxLives + i) * (r * 2.6f) - r
                val cy = pHud.textSize / 2 + 6f
                pBall.color = 0xFF4AC5FF.toInt()
                canvas.drawCircle(cx, cy, r * 1.1f, pBall)
                pBall.color = 0xFF05071A.toInt()
                canvas.drawCircle(cx, cy, r * 0.55f, pBall)
            }
        }
        if (ownedRelics.isNotEmpty()) {
            pPuLabel.textSize = pHud.textSize * 0.48f
            pPuLabel.textAlign = Paint.Align.LEFT
            pPuLabel.color = 0xFFCCAAFF.toInt()
            canvas.drawText("Reliques : ${ownedRelics.size}", 18f, pHud.textSize * 1.85f, pPuLabel)
        }
        if (level % 5 == 0 && bossHpBarMax > 0f && state == State.PLAYING) {
            val barW = W * 0.6f; val barH = H * 0.012f
            val barX = (W - barW) / 2f; val barY = brickAreaBottom + 8f
            val ratio = (bossHpBar / bossHpBarMax).coerceIn(0f, 1f)
            pBrick.color = 0xFF1A1A2A.toInt()
            canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 4f, 4f, pBrick)
            val hpColor = when {
                ratio > 0.5f -> 0xFFDD2266.toInt()
                ratio > 0.25f -> 0xFFFF8844.toInt()
                else -> 0xFFFF4444.toInt()
            }
            pBrick.color = hpColor
            canvas.drawRoundRect(barX, barY, barX + barW * ratio, barY + barH, 4f, 4f, pBrick)
            pPuLabel.textSize = barH * 1.3f
            pPuLabel.textAlign = Paint.Align.CENTER
            pPuLabel.color = 0xFFFFFFFF.toInt()
            canvas.drawText("BOSS", W / 2, barY - 3f, pPuLabel)
        }
    }

    private fun drawFloor(canvas: Canvas) {
        if (timerFloor <= 0L) return
        val alpha = (timerFloor.toFloat() / 10_000f * 170).toInt().coerceIn(50, 170)
        pFloor.alpha = alpha
        val h = maxOf(H * 0.06f, 10f)
        canvas.drawRect(0f, H - h, W, H, pFloor)
        pFloor.alpha = (alpha * 0.6f).toInt()
        canvas.drawRect(0f, H - h, W, H - h + 3f, pFloor)
    }

    private fun drawBrickShape(canvas: Canvas, b: Brick, rect: RectF) {
        when (b.shape) {
            Shape.RECT -> canvas.drawRoundRect(rect, 5f, 5f, pBrick)
            Shape.PILL -> canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pBrick)
            Shape.DIAMOND -> {
                val cx = rect.centerX(); val cy = rect.centerY()
                rect.width() / 2f; rect.height() / 2f
                brickPath.rewind()
                brickPath.moveTo(cx, rect.top)
                brickPath.lineTo(rect.right, cy)
                brickPath.lineTo(cx, rect.bottom)
                brickPath.lineTo(rect.left, cy)
                brickPath.close()
                canvas.drawPath(brickPath, pBrick)
            }
            Shape.HEX -> {
                rect.centerX(); val cy = rect.centerY()
                rect.width() / 2f; rect.height() / 2f
                val inset = rect.width() * 0.22f
                brickPath.rewind()
                brickPath.moveTo(rect.left + inset, rect.top)
                brickPath.lineTo(rect.right - inset, rect.top)
                brickPath.lineTo(rect.right, cy)
                brickPath.lineTo(rect.right - inset, rect.bottom)
                brickPath.lineTo(rect.left + inset, rect.bottom)
                brickPath.lineTo(rect.left, cy)
                brickPath.close()
                canvas.drawPath(brickPath, pBrick)
            }
        }
    }

    private fun drawBrickBorder(canvas: Canvas, b: Brick, rect: RectF) {
        when (b.shape) {
            Shape.RECT -> canvas.drawRoundRect(rect, 5f, 5f, pCrack)
            Shape.PILL -> canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pCrack)
            else -> canvas.drawPath(brickPath, pCrack)
        }
    }

    private fun drawBricks(canvas: Canvas) {
        for (b in bricks) {
            if (b.hits <= 0) continue
            val t = 1f - b.hits.toFloat() / b.maxHits
            val shakeDx = if (b.shake > 0f) (Random.nextFloat() - 0.5f) * b.shake * 4f else 0f
            val base = blendColor(b.baseColor, 0xFF2A2A3A.toInt(), t * 0.55f)
            val flashed = blendColor(base, 0xFFFFFFFF.toInt(), b.flash * 0.6f)
            pBrick.color = flashed

            tmpBrickRect.set(b.rect.left + shakeDx, b.rect.top, b.rect.right + shakeDx, b.rect.bottom)
            val rect = tmpBrickRect

            val glowColor = when (b.type) {
                BType.EXPLOSIVE -> 0x55FF5522.toInt()
                BType.BONUS     -> 0x55FFD700.toInt()
                BType.REGEN     -> 0x4422DDAA.toInt()
                BType.ICE       -> 0x4488E5FF.toInt()
                BType.INDESTRUCTIBLE -> 0x33000000.toInt()
                else -> 0
            }
            if (glowColor != 0) {
                pBrickGlow.color = glowColor
                when (b.shape) {
                    Shape.RECT, Shape.PILL -> canvas.drawRoundRect(
                        rect.left - 3f, rect.top - 3f, rect.right + 3f, rect.bottom + 3f,
                        if (b.shape == Shape.PILL) rect.height() / 2f else 7f,
                        if (b.shape == Shape.PILL) rect.height() / 2f else 7f,
                        pBrickGlow
                    )
                    else -> canvas.drawRoundRect(
                        rect.left - 3f, rect.top - 3f, rect.right + 3f, rect.bottom + 3f, 7f, 7f, pBrickGlow
                    )
                }
            }

            drawBrickShape(canvas, b, rect)

            when (b.type) {
                BType.EXPLOSIVE -> {
                    pCrack.color = 0xCCFFFFFF.toInt(); pCrack.strokeWidth = 2f
                    val cx = rect.centerX(); val cy = rect.centerY()
                    val r1 = minOf(rect.width(), rect.height()) * 0.18f
                    canvas.drawCircle(cx, cy, r1, pCrack)
                    pCrack.strokeWidth = 1f
                    canvas.drawLine(cx - r1 * 1.8f, cy, cx + r1 * 1.8f, cy, pCrack)
                    canvas.drawLine(cx, cy - r1 * 1.8f, cx, cy + r1 * 1.8f, pCrack)
                }
                BType.INDESTRUCTIBLE -> {
                    pCrack.color = 0xFF9999AA.toInt(); pCrack.strokeWidth = 1.2f
                    val step = rect.width() / 4f
                    for (i in 1..3) canvas.drawLine(
                        rect.left + step * i, rect.top + 3f,
                        rect.left + step * i, rect.bottom - 3f, pCrack
                    )
                }
                BType.BONUS -> {
                    pCrack.color = 0xFFFFF6A0.toInt(); pCrack.strokeWidth = 2f
                    val cx = rect.centerX(); val cy = rect.centerY()
                    val rr = minOf(rect.width(), rect.height()) * 0.22f
                    canvas.drawCircle(cx, cy, rr, pCrack)
                }
                BType.REGEN -> {
                    pCrack.color = 0xFFAAFFEE.toInt(); pCrack.strokeWidth = 1.8f
                    val cx = rect.centerX(); val cy = rect.centerY()
                    canvas.drawArc(cx - 8f, cy - 8f, cx + 8f, cy + 8f, 40f, 280f, false, pCrack)
                }
                BType.ICE -> {
                    pCrack.color = 0xCCFFFFFF.toInt(); pCrack.strokeWidth = 1.2f
                    val cx = rect.centerX(); val cy = rect.centerY()
                    val r1 = minOf(rect.width(), rect.height()) * 0.28f
                    for (i in 0..2) {
                        val a = i * PI.toFloat() / 3f
                        canvas.drawLine(cx - cos(a) * r1, cy - sin(a) * r1,
                            cx + cos(a) * r1, cy + sin(a) * r1, pCrack)
                    }
                }
                BType.RESISTANT -> {
                    val damageCount = b.maxHits - b.hits
                    if (damageCount > 0) {
                        pCrack.color = 0x99000000.toInt()
                        pCrack.strokeWidth = 1.6f
                        val cx = rect.centerX(); val cy = rect.centerY()
                        val wHalf = rect.width() * 0.30f
                        val hHalf = rect.height() * 0.30f
                        if (damageCount >= 1) canvas.drawLine(cx - wHalf, cy - hHalf, cx + wHalf, cy + hHalf, pCrack)
                        if (damageCount >= 2) canvas.drawLine(cx - wHalf, cy + hHalf, cx + wHalf, cy - hHalf, pCrack)
                        if (damageCount >= 3) canvas.drawLine(cx - wHalf * 1.1f, cy, cx + wHalf * 1.1f, cy, pCrack)
                        if (damageCount >= 4) canvas.drawLine(cx, cy - hHalf * 1.1f, cx, cy + hHalf * 1.1f, pCrack)
                        if (damageCount >= 5) canvas.drawLine(cx - wHalf * 0.6f, cy - hHalf, cx + wHalf * 0.2f, cy + hHalf * 0.4f, pCrack)
                    }
                }
                else -> {}
            }

            pCrack.color = blendColor(0xAAFFFFFF.toInt(), 0x22FFFFFF.toInt(), t)
            pCrack.strokeWidth = 1f
            drawBrickBorder(canvas, b, rect)
        }
    }

    private fun drawShockwaves(canvas: Canvas) {
        for (sw in shockwaves) {
            pShock.color = sw.color
            pShock.alpha = (sw.life * 180).toInt().coerceIn(0, 220)
            pShock.strokeWidth = 2f + sw.life * 4f
            canvas.drawCircle(sw.x, sw.y, sw.r, pShock)
        }
    }

    private fun drawSparks(canvas: Canvas) {
        for (s in sparks) {
            pSpark.color = s.color
            pSpark.alpha = (s.life * 230).toInt().coerceIn(0, 255)
            val r = (s.life * 4.5f * s.size + 1f).coerceAtLeast(0.5f)
            canvas.drawCircle(s.x, s.y, r, pSpark)
        }
    }

    private fun drawPowerUps(canvas: Canvas) {
        for (pu in powerUps) {
            val color = puColors[pu.type] ?: 0xFFFFFFFF.toInt()
            val wob = sin(pu.wob) * 2f
            pBrickGlow.color = (color and 0x00FFFFFF) or 0x55000000.toInt()
            canvas.drawRoundRect(pu.x - 26f + wob, pu.y - 26f, pu.x + 26f + wob, pu.y + 26f, 9f, 9f, pBrickGlow)
            pBrick.color = color
            canvas.drawRoundRect(pu.x - 22f + wob, pu.y - 22f, pu.x + 22f + wob, pu.y + 22f, 7f, 7f, pBrick)
            pPuLabel.color = darken(color, 0.55f)
            pPuLabel.isFakeBoldText = true
            pPuLabel.textAlign = Paint.Align.CENTER
            canvas.drawText(puLabels[pu.type] ?: "P", pu.x + wob, pu.y + pPuLabel.textSize * 0.38f, pPuLabel)
        }
    }

    private fun drawLasers(canvas: Canvas) {
        for (l in lasers) {
            pLaser.alpha = 220
            canvas.drawLine(l.x, l.y, l.x, l.y - 28f, pLaser)
            pLaser.alpha = 80
            canvas.drawLine(l.x, l.y - 28f, l.x, l.y - 48f, pLaser)
        }
        pLaser.alpha = 255
    }

    private fun drawPaddleGhosts(canvas: Canvas) {
        for (g in paddleGhosts) {
            pBall.color = 0xFF7AF8FF.toInt()
            pBall.alpha = (g.life * 90).toInt().coerceIn(0, 120)
            val rect = RectF(g.x - g.w / 2, g.y, g.x + g.w / 2, g.y + paddleH)
            canvas.drawRoundRect(rect, paddleH / 2, paddleH / 2, pBall)
        }
        pBall.alpha = 255
    }

    private fun drawPaddle(canvas: Canvas) {
        refreshPaddlePaint()
        canvas.drawRoundRect(paddle, paddleH / 2, paddleH / 2, pPaddle)
        pBall.color = 0x66FFFFFF
        canvas.drawRoundRect(
            RectF(paddle.left + 4f, paddle.top + 1f, paddle.right - 4f, paddle.top + paddleH * 0.45f),
            paddleH / 3, paddleH / 3, pBall)
        if (timerMagnet > 0L) {
            pShock.color = 0xFFE255FF.toInt()
            pShock.alpha = ((sin(System.currentTimeMillis() / 160.0).toFloat() + 1f) * 60f).toInt().coerceIn(40, 160)
            pShock.strokeWidth = 2f
            canvas.drawRoundRect(
                paddle.left - 4f, paddle.top - 4f, paddle.right + 4f, paddle.bottom + 4f,
                paddleH, paddleH, pShock
            )
        }
    }

    private fun drawBalls(canvas: Canvas) {
        val trailTint = when {
            timerFire > 0L   -> 0xFFFF7733.toInt()
            timerPierce > 0L -> 0xFFFFFFFF.toInt()
            else -> 0xFFAADDFF.toInt()
        }
        val invTrail = 1f / TRAIL_MAX
        for (b in balls) {
            pBall.color = trailTint
            for (i in 0 until b.trailLen) {
                val idx = ((b.trailHead - i) + TRAIL_MAX) % TRAIL_MAX
                val tf = 1f - i * invTrail
                pBall.alpha = (tf * 140).toInt().coerceIn(0, 255)
                canvas.drawCircle(b.trailX[idx], b.trailY[idx], ballR * (0.3f + tf * 0.7f), pBall)
            }
            pBall.color = when {
                timerFire > 0L   -> 0x44FF8844.toInt()
                timerPierce > 0L -> 0x44FFFFFF.toInt()
                else -> 0x33AADDFF
            }
            pBall.alpha = 100
            canvas.drawCircle(b.x, b.y, ballR * 2.1f, pBall)
            pBall.color = when {
                timerFire > 0L   -> 0xFFFFCC88.toInt()
                timerPierce > 0L -> 0xFFFFFFFF.toInt()
                else -> 0xFFE8F4FF.toInt()
            }
            pBall.alpha = 255
            canvas.drawCircle(b.x, b.y, ballR, pBall)
            pBall.color = 0xCCFFFFFF.toInt()
            canvas.drawCircle(b.x - ballR * 0.3f, b.y - ballR * 0.3f, ballR * 0.35f, pBall)
        }
    }

    private fun drawFloatTexts(canvas: Canvas) {
        for (ft in floatTexts) {
            pCombo.textSize = pHud.textSize * 0.9f * ft.size
            pCombo.color = ft.color
            pCombo.alpha = (ft.life * 240).toInt().coerceIn(0, 255)
            canvas.drawText(ft.text, ft.x, ft.y, pCombo)
        }
        pCombo.alpha = 255
    }

    private fun drawTimerBadges(canvas: Canvas) {
        var x = 14f; val y = H * 0.93f; val bh = H * 0.034f; val bw = bh * 2.6f
        pPuLabel.textSize = bh * 0.65f; pPuLabel.textAlign = Paint.Align.LEFT
        fun badge(timer: Long, color: Int, label: String, textDark: Boolean) {
            if (timer <= 0L) return
            pBrick.color = color
            canvas.drawRoundRect(x, y, x + bw, y + bh, 4f, 4f, pBrick)
            pPuLabel.color = if (textDark) darken(color, 0.6f) else 0xFFFFFFFF.toInt()
            canvas.drawText("$label ${timer / 1000}s", x + 5f, y + bh * 0.76f, pPuLabel)
            x += bw + 6f
        }
        badge(timerExtend, puColors[PType.EXTEND]!!, "L", true)
        badge(timerLaser,  puColors[PType.LASER]!!,  "T", false)
        badge(timerSpeed,  puColors[PType.SPEED]!!,  "S", false)
        badge(timerSlow,   puColors[PType.SLOW]!!,   "R", true)
        badge(timerFloor,  puColors[PType.FLOOR]!!,  "F", true)
        badge(timerPierce, puColors[PType.PIERCE]!!, "P", true)
        badge(timerFire,   puColors[PType.FIRE]!!,   "B", false)
        badge(timerMagnet, puColors[PType.MAGNET]!!, "A", false)
    }

    private fun drawComboBanner(canvas: Canvas) {
        if (combo < 3 || comboTimer <= 0L) return
        val alpha = (comboTimer.toFloat() / comboGrace).coerceIn(0f, 1f)
        pCombo.color = 0xFFFFE066.toInt()
        pCombo.alpha = (alpha * 230).toInt().coerceIn(0, 255)
        pCombo.textSize = pHud.textSize * 1.4f
        canvas.drawText("COMBO x${combo}", W / 2, pHud.textSize * 2.4f, pCombo)
        pCombo.alpha = 255
    }

    private fun drawPanicIndicator(canvas: Canvas) {
        if (!panicActive()) return
        // Pulsing banner + finger aura
        val pulse = ((sin(System.currentTimeMillis() / 220.0).toFloat() + 1f) * 0.5f)
        pCombo.color = 0xFFFFE066.toInt()
        pCombo.alpha = (180 + pulse * 60).toInt().coerceIn(0, 255)
        pCombo.textSize = pHud.textSize * 1.1f
        val remain = ((fingerBreakReadyAtMs - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f)
        val msg = if (remain > 0f) "CONTACT ! Recharge… %.1fs".format(remain)
                  else "CONTACT ! Touche une brique (1 / 5s)"
        canvas.drawText(msg, W / 2, H * 0.18f, pCombo)
        pCombo.alpha = 255

        if (fingerX > 0f && fingerY > 0f) {
            pShock.color = 0xFFFFE066.toInt()
            pShock.alpha = (80 + pulse * 120).toInt().coerceIn(0, 255)
            pShock.strokeWidth = 2f
            canvas.drawCircle(fingerX, fingerY, 28f + pulse * 10f, pShock)
            canvas.drawCircle(fingerX, fingerY, 40f + pulse * 14f, pShock)
        }
    }

    private fun drawFlash(canvas: Canvas) {
        if (flashWhite <= 0f) return
        val p = Paint().apply { color = 0xFFFFFFFF.toInt(); alpha = (flashWhite * 80).toInt().coerceIn(0, 120) }
        canvas.drawRect(0f, 0f, W, H, p)
    }

    private fun rarityColor(r: Rarity) = when (r) {
        Rarity.COMMON -> 0xFF8899AA.toInt()
        Rarity.RARE   -> 0xFF5CA8FF.toInt()
        Rarity.EPIC   -> 0xFFFF8FEF.toInt()
    }

    private fun drawRelicChoice(canvas: Canvas) {
        val choices = relicChoices
        if (choices.isEmpty()) return
        val n = choices.size
        val margin = W * 0.05f
        val gap = W * 0.025f
        val cardW = (W - margin * 2 - gap * (n - 1)) / n
        val cardH = H * 0.38f
        val top = H * 0.44f

        pTitle.textSize = maxOf(H * 0.04f, 26f)
        pTitle.color = 0xFFFFE066.toInt()
        canvas.drawText("Choisis une relique", W / 2, top - 40f, pTitle)
        pSub.textSize = maxOf(H * 0.023f, 16f)
        pSub.color = 0xFFAABBCC.toInt()
        canvas.drawText("Niveau ${level - 1} terminé • Pièces : $gold", W / 2, top - 10f, pSub)

        for (i in 0 until n) {
            val r = choices[i]
            val x0 = margin + i * (cardW + gap)
            choiceRects[i].set(x0, top, x0 + cardW, top + cardH)

            pCard.color = 0xFF1A1F3A.toInt()
            canvas.drawRoundRect(choiceRects[i], 14f, 14f, pCard)
            pCardStroke.color = rarityColor(r.rarity)
            canvas.drawRoundRect(choiceRects[i], 14f, 14f, pCardStroke)

            pCard.color = rarityColor(r.rarity)
            canvas.drawRect(choiceRects[i].left + 10f, choiceRects[i].top + 4f,
                choiceRects[i].right - 10f, choiceRects[i].top + 10f, pCard)

            pTitle.textSize = maxOf(cardH * 0.09f, 18f)
            pTitle.color = 0xFFFFFFFF.toInt()
            canvas.drawText(r.name, choiceRects[i].centerX(), choiceRects[i].top + cardH * 0.24f, pTitle)

            pSub.textSize = maxOf(cardH * 0.06f, 14f)
            pSub.color = rarityColor(r.rarity)
            canvas.drawText(
                when (r.rarity) { Rarity.COMMON -> "commune"; Rarity.RARE -> "rare"; Rarity.EPIC -> "épique" },
                choiceRects[i].centerX(), choiceRects[i].top + cardH * 0.36f, pSub
            )

            pSub.textSize = maxOf(cardH * 0.065f, 14f)
            pSub.color = 0xFFCCDDEE.toInt()
            val lines = wrapText(r.desc, pSub, cardW - 20f)
            var yy = choiceRects[i].top + cardH * 0.55f
            for (l in lines.take(4)) {
                canvas.drawText(l, choiceRects[i].centerX(), yy, pSub)
                yy += pSub.textSize * 1.2f
            }
        }

        val btnW = W * 0.32f; val btnH = H * 0.044f
        val btnY = top + cardH + H * 0.025f
        skipRelicRect.set(W / 2 - btnW / 2, btnY, W / 2 + btnW / 2, btnY + btnH)
        pCard.color = 0xFF1A1020.toInt()
        canvas.drawRoundRect(skipRelicRect, 8f, 8f, pCard)
        pCardStroke.color = 0xFF776688.toInt()
        canvas.drawRoundRect(skipRelicRect, 8f, 8f, pCardStroke)
        pSub.textSize = maxOf(btnH * 0.55f, 15f)
        pSub.color = 0xFF998BAA.toInt()
        canvas.drawText("✕ Passer", W / 2, btnY + btnH * 0.72f, pSub)
    }

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
        val words = text.split(' ')
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            val test = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(test) <= maxW) cur = StringBuilder(test)
            else {
                if (cur.isNotEmpty()) out.add(cur.toString())
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun drawShop(canvas: Canvas) {
        canvas.drawRect(0f, 0f, W, H, pNebula)
        canvas.drawRect(0f, 0f, W, H, pOverlay)

        pTitle.textSize = maxOf(H * 0.04f, 26f)
        pTitle.color = 0xFFDD2266.toInt()
        canvas.drawText("⚔ Boss vaincu !", W / 2, H * 0.055f, pTitle)

        pTitle.color = 0xFFFFD55A.toInt()
        pTitle.textSize = maxOf(H * 0.035f, 22f)
        canvas.drawText("◆ Boutique", W / 2, H * 0.095f, pTitle)

        pSub.textSize = maxOf(H * 0.025f, 17f)
        pSub.color = 0xFFFFFFFF.toInt()
        canvas.drawText("Pièces disponibles : $gold", W / 2, H * 0.13f, pSub)

        val cardW = W * 0.42f
        val cardH = H * 0.11f
        val gap = H * 0.012f
        val startY = H * 0.15f
        val cols = 2
        val xLeft = W / 2 - cardW - gap / 2
        val xRight = W / 2 + gap / 2

        for (i in shopItems.indices) {
            val item = shopItems[i]
            val col = i % cols
            val row = i / cols
            val x = if (col == 0) xLeft else xRight
            val y = startY + row * (cardH + gap)
            shopRects[i].set(x, y, x + cardW, y + cardH)

            val cur = shopLevel(item.key)
            val maxed = cur >= item.maxLevel
            val price = item.price * (cur + 1)
            val canBuy = !maxed && gold >= price

            pCard.color = if (canBuy) 0xFF1A1F3A.toInt() else 0xFF111122.toInt()
            canvas.drawRoundRect(shopRects[i], 10f, 10f, pCard)
            pCardStroke.color = when {
                maxed   -> 0xFF44DD66.toInt()
                canBuy  -> 0xFFFFD55A.toInt()
                else    -> 0xFF555566.toInt()
            }
            canvas.drawRoundRect(shopRects[i], 10f, 10f, pCardStroke)

            pTitle.textSize = maxOf(cardH * 0.18f, 15f)
            pTitle.color = 0xFFFFFFFF.toInt()
            canvas.drawText("${item.icon} ${item.name}", shopRects[i].centerX(), y + cardH * 0.28f, pTitle)

            pSub.textSize = maxOf(cardH * 0.14f, 12f)
            pSub.color = 0xFFAABBCC.toInt()
            canvas.drawText(item.desc, shopRects[i].centerX(), y + cardH * 0.50f, pSub)

            if (maxed) {
                pSub.color = 0xFF44DD66.toInt()
                canvas.drawText("MAX ($cur/${item.maxLevel})", shopRects[i].centerX(), y + cardH * 0.75f, pSub)
            } else {
                val lvlTxt = if (item.maxLevel > 1) "Niv.$cur/${item.maxLevel} • " else ""
                pSub.color = if (canBuy) 0xFFFFD55A.toInt() else 0xFFFF6666.toInt()
                canvas.drawText("${lvlTxt}◆ $price", shopRects[i].centerX(), y + cardH * 0.75f, pSub)
            }
        }

        val btnW = W * 0.35f; val btnH = H * 0.045f
        val btnY = startY + ((shopItems.size + 1) / 2) * (cardH + gap) + gap
        shopBackRect.set(W / 2 - btnW / 2, btnY, W / 2 + btnW / 2, btnY + btnH)
        pCard.color = 0xFF2A1F4A.toInt()
        canvas.drawRoundRect(shopBackRect, 8f, 8f, pCard)
        pCardStroke.color = 0xFFAABBCC.toInt()
        canvas.drawRoundRect(shopBackRect, 8f, 8f, pCardStroke)
        pSub.textSize = maxOf(btnH * 0.55f, 16f)
        pSub.color = 0xFFFFFFFF.toInt()
        canvas.drawText("▶ Continuer", W / 2, btnY + btnH * 0.70f, pSub)
    }

    private fun drawOverlay(canvas: Canvas) {
        if (state == State.PLAYING) return
        if (state == State.SHOP) { drawShop(canvas); return }
        canvas.drawRect(0f, 0f, W, H, pOverlay)
        val cy = H / 2f
        when (state) {
            State.READY -> {
                pTitle.color = 0xFFFFFFFF.toInt()
                pTitle.textSize = maxOf(H * 0.042f, 28f)
                if (level == 1) {
                    canvas.drawText("Particules", W / 2, cy - pTitle.textSize, pTitle)
                    pSub.color = 0xFFAABBCC.toInt()
                    canvas.drawText("Détruisez, enchaînez, survivez", W / 2, cy + 10f, pSub)
                    canvas.drawText("Glissez pour viser • Relâchez pour lancer", W / 2, cy + pSub.textSize + 16f, pSub)
                    if (meta.highScore > 0) {
                        pSub.color = 0xFFFFD55A.toInt()
                        canvas.drawText("Record : ${fmtScore(meta.highScore)}  •  Niv.max ${meta.highestLevel}",
                            W / 2, cy + pSub.textSize * 2 + 30f, pSub)
                    }
                } else if (level % 5 == 0) {
                    pTitle.color = 0xFFDD2266.toInt()
                    canvas.drawText("⚔ BOSS ⚔", W / 2, cy - pTitle.textSize, pTitle)
                    pTitle.color = 0xFFFFFFFF.toInt()
                    canvas.drawText("Niveau $level", W / 2, cy + 10f, pTitle)
                    pSub.color = 0xFFAABBCC.toInt()
                    canvas.drawText("Glissez pour viser • Relâchez pour lancer", W / 2, cy + pTitle.textSize + 20f, pSub)
                } else {
                    canvas.drawText("Niveau $level", W / 2, cy - 10f, pTitle)
                    pSub.color = 0xFFAABBCC.toInt()
                    canvas.drawText("Glissez pour viser • Relâchez pour lancer", W / 2, cy + pTitle.textSize + 8f, pSub)
                }
            }
            State.PAUSED -> {
                pTitle.textSize = maxOf(H * 0.042f, 28f)
                canvas.drawText("En pause", W / 2, cy - 10f, pTitle)
                pSub.color = 0xFFAABBCC.toInt()
                canvas.drawText("Touchez pour reprendre", W / 2, cy + pTitle.textSize + 8f, pSub)
            }
            State.LIFE_LOST -> {
                pTitle.textSize = maxOf(H * 0.042f, 28f)
                pTitle.color = 0xFFFF6677.toInt()
                canvas.drawText("Particule perdue !", W / 2, cy - 10f, pTitle)
                pSub.color = 0xFFAABBCC.toInt()
                canvas.drawText("$lives vie(s) restante(s)", W / 2, cy + pTitle.textSize + 8f, pSub)
                canvas.drawText("Glissez pour viser • Relâchez pour lancer", W / 2, cy + pTitle.textSize + pSub.textSize + 18f, pSub)
            }
            State.LEVEL_CLEAR -> drawRelicChoice(canvas)
            State.GAME_OVER -> {
                pTitle.textSize = maxOf(H * 0.042f, 28f)
                pTitle.color = 0xFFFFFFFF.toInt()
                canvas.drawText("Partie terminée", W / 2, cy - pTitle.textSize * 1.5f, pTitle)
                pSub.color = 0xFFEEEEEE.toInt()
                canvas.drawText("Score final : ${fmtScore(score)}", W / 2, cy - pTitle.textSize * 0.3f, pSub)
                pSub.color = 0xFFAABBCC.toInt()
                canvas.drawText("Niv.${ level - 1} atteint", W / 2, cy + pSub.textSize * 0.7f, pSub)
                pSub.color = 0xFFFFD55A.toInt()
                canvas.drawText("+$gold pièces • Total : ${meta.totalGold}", W / 2, cy + pSub.textSize * 2f, pSub)
                if (bestCombo >= 3) {
                    pSub.color = 0xFFFFE066.toInt()
                    canvas.drawText("Meilleur combo : x$bestCombo", W / 2, cy + pSub.textSize * 3.2f, pSub)
                }
                if (meta.highScore > 0) {
                    pSub.color = 0xFFAABBCC.toInt()
                    canvas.drawText("Record : ${fmtScore(meta.highScore)}",
                        W / 2, cy + pSub.textSize * 4.4f, pSub)
                }
                pSub.color = 0xFFAABBCC.toInt()
                canvas.drawText("Touchez pour rejouer", W / 2, cy + pSub.textSize * 6f, pSub)
            }
            State.PLAYING, State.SHOP -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun blendColor(a: Int, b: Int, t: Float): Int {
        val t1 = t.coerceIn(0f, 1f); val t0 = 1f - t1
        return Color.rgb(
            (Color.red(a)   * t0 + Color.red(b)   * t1).toInt(),
            (Color.green(a) * t0 + Color.green(b) * t1).toInt(),
            (Color.blue(a)  * t0 + Color.blue(b)  * t1).toInt()
        )
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color)   * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color)  * factor).toInt()
    )

    private fun fmtScore(s: Long) =
        s.toString().reversed().chunked(3).joinToString("\u202F").reversed()
}
