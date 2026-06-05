package com.Atom2Universe.app.games.starswar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

class StarsWarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        const val VW = 480f
        const val VH = 720f
        const val PLAYER_FIRE_RATE_BASE = 0.5f
        const val PLAYER_BULLET_SPEED = 380f
        const val PLAYER_MAX_HP_BASE = 3
        const val PLAYER_DAMAGE_COOLDOWN = 0.8f
        const val PLAYER_AREA_TOP = VH * 0.45f
        const val PLAYER_SIZE = 38f
        const val PLAYER_HITBOX_R = PLAYER_SIZE * 0.38f
        const val PBULLET_W = 8f
        const val PBULLET_H = 22f
        const val EBULLET_W = 6f
        const val EBULLET_H = 16f
        const val EBULLET_SPEED = 220f
        const val ENEMY_SIZE_DEFAULT = 46f
        const val BOSS_SIZE = 76f
        const val BOSS_HITBOX_R = BOSS_SIZE * 0.40f
        const val BOSS_MAX_Y = VH * 0.42f
        const val STAR_COUNT = 90
        const val WAVE_CLEAR_DELAY = 0.4f
        const val WAVE_ANNOUNCE_DURATION = 1.8f
        const val PATH_LINE    = 0
        const val PATH_SIN     = 1
        const val PATH_ENTRY_L = 2   // Entrée côté gauche → arc vers formation haut
        const val PATH_ENTRY_R = 3   // Entrée côté droit
        const val PATH_LOOP_L  = 4   // Grand arc sweeping depuis la gauche
        const val PATH_LOOP_R  = 5   // Grand arc sweeping depuis la droite
        const val ENTRY_DUR_SWEEP = 2.0f   // secondes pour un arc d'entrée court
        const val ENTRY_DUR_LOOP  = 3.2f   // secondes pour un grand arc
        const val CONVOY_SPAWN_INT = 0.22f // délai entre deux membres d'un convoi
        const val ROCKET_SPEED = 210f
        const val ROCKET_R = 7f
        // Upgrade IDs
        const val UPG_RAPID_FIRE  = 0
        const val UPG_MULTI_SHOT  = 1
        const val UPG_POWER_SHOT  = 2
        const val UPG_SHIELD      = 3
        const val UPG_HEAL        = 4
        const val UPG_MAX_HP_RARE = 5   // super rare, boss only
        // 6 = obsolete
        const val UPG_PIERCE      = 7
        const val UPG_ROCKET      = 8
        const val UPG_ROCKET_RATE = 9
        const val UPG_ROCKET_DMG  = 10
        const val UPG_MAGNET      = 11
        const val UPG_MAGNET_DUR  = 12
        const val UPG_DRONE       = 13
        const val UPG_VAMPIRE     = 14
        const val UPG_NOVA        = 15
        const val MAGNET_RADIUS   = 130f
        const val MAGNET_COOLDOWN_MAX = 25f
        const val NOVA_RADIUS     = 200f
        const val DRONE_ORBIT_R   = 52f
        const val DRONE_ORBIT_SPEED = 1.8f
        const val DRONE_FIRE_RATE  = 1.8f
        const val DRONE_FIRE_RATE_ALERT = 0.35f
        const val DRONE_ALERT_RANGE = 150f
        const val DRONE_BULLET_SPEED = 310f
        const val METEOR_PHASE_DURATION = 30f
    }

    // ── Enums ─────────────────────────────────────────────────────────────────
    private enum class Phase { READY, RUNNING, WAVE_CLEAR, UPGRADE, METEOR, PAUSED, GAME_OVER }

    // ── Data classes ──────────────────────────────────────────────────────────
    private data class EnemyDef(
        val hp: Int, val speed: Float, val score: Int,
        val canShoot: Boolean = false, val fireRate: Float = 2.5f,
        val path: Int = PATH_LINE
    )

    private data class Enemy(
        var x: Float, var y: Float,
        val typeIdx: Int,         // 0-7 normal, 8-9 boss
        var hp: Int, val maxHp: Int,
        val speed: Float,
        val canShoot: Boolean, val fireRate: Float, var fireCooldown: Float,
        val path: Int,
        val baseX: Float,
        val sinAmp: Float, val sinFreq: Float,
        val isBoss: Boolean = false,
        var elapsed: Float = 0f,
        // Champs pour les chemins en formation (PATH_ENTRY_*/PATH_LOOP_*)
        val entryX0: Float = 0f,
        val entryY0: Float = 0f,
        val formX: Float = 0f,
        val formY: Float = 0f,
        val entryDur: Float = 0f
    )

    private data class Bullet(
        var x: Float, var y: Float,
        var pierceLeft: Int = 0,
        var dx: Float = 0f,
        var dy: Float = 1f,
        val isMissile: Boolean = false,
        val dmg: Int = -1,       // -1 = utilise bulletDmg
        val isDrone: Boolean = false
    )
    private data class Rocket(var x: Float, var y: Float)
    private data class Drone(var angle: Float, var fireCooldown: Float)
    private data class Meteor(var x: Float, var y: Float, val radius: Float, val speed: Float, val dx: Float = 0f)
    private data class PendingSpawn(
        val typeIdx: Int, val path: Int,
        val formX: Float, val formY: Float,
        val entryX: Float, val entryY: Float
    )

    private data class Star(val x: Float, var y: Float, val speed: Float, val radius: Float, val alpha: Float)

    private data class ScorePopup(var x: Float, var y: Float, var life: Float, val text: String, val isGold: Boolean = false)

    private data class UpgradeData(
        val id: Int, val name: String, val desc: String,
        val hexColor: String, val maxStack: Int
    )

    // ── Enemy definitions (7 types matching JS) ───────────────────────────────
    // Order matches SPRITE_DEFINITIONS: drone, fast, gunner, tank, kamikaze, sniper, carrier
    private val enemyDefs = arrayOf(
        EnemyDef(hp = 1, speed = 75f,  score = 50,  path = PATH_LINE),                                   // 0 drone
        EnemyDef(hp = 1, speed = 155f, score = 70,  path = PATH_SIN),                                    // 1 fast
        EnemyDef(hp = 2, speed = 65f,  score = 100, canShoot = true, fireRate = 2.0f, path = PATH_LINE), // 2 gunner
        EnemyDef(hp = 4, speed = 45f,  score = 160, canShoot = true, fireRate = 3.5f, path = PATH_LINE), // 3 tank
        EnemyDef(hp = 1, speed = 140f, score = 80,  path = PATH_SIN),                                    // 4 kamikaze
        EnemyDef(hp = 2, speed = 60f,  score = 110, canShoot = true, fireRate = 1.5f, path = PATH_LINE), // 5 sniper
        EnemyDef(hp = 2, speed = 55f,  score = 90,  canShoot = true, fireRate = 2.5f, path = PATH_SIN)   // 6 carrier
    )
    // Per-type display sizes (pixels) — matches JS: drone/fast/etc=40px, tank=48px
    private val enemySizes = floatArrayOf(46f, 46f, 46f, 54f, 44f, 46f, 50f)

    // ── Upgrade pool ──────────────────────────────────────────────────────────
    private val upgradePool by lazy { listOf(
        UpgradeData(UPG_RAPID_FIRE,  s(R.string.sw_upg_rapid_fire_name),   s(R.string.sw_upg_rapid_fire_desc),   "#FF8C00", 99),
        UpgradeData(UPG_MULTI_SHOT,  s(R.string.sw_upg_multi_shot_name),   s(R.string.sw_upg_multi_shot_desc),   "#4488FF",  3),
        UpgradeData(UPG_POWER_SHOT,  s(R.string.sw_upg_power_shot_name),   s(R.string.sw_upg_power_shot_desc),   "#FF3344", 99),
        UpgradeData(UPG_SHIELD,      s(R.string.sw_upg_shield_name),       s(R.string.sw_upg_shield_desc),       "#00CED1",  3),
        UpgradeData(UPG_HEAL,        s(R.string.sw_upg_heal_name),         s(R.string.sw_upg_heal_desc),         "#44DD55", 99),
        UpgradeData(UPG_MAX_HP_RARE, s(R.string.sw_upg_max_hp_name),       s(R.string.sw_upg_max_hp_desc),       "#228B22",  3),
        UpgradeData(UPG_PIERCE,      s(R.string.sw_upg_pierce_name),       s(R.string.sw_upg_pierce_desc),       "#CC44FF",  3),
        UpgradeData(UPG_ROCKET,      s(R.string.sw_upg_rocket_name),       s(R.string.sw_upg_rocket_desc),       "#FF6600",  1),
        UpgradeData(UPG_ROCKET_RATE, s(R.string.sw_upg_rocket_rate_name),  s(R.string.sw_upg_rocket_rate_desc),  "#FFAA00", 99),
        UpgradeData(UPG_ROCKET_DMG,  s(R.string.sw_upg_rocket_dmg_name),   s(R.string.sw_upg_rocket_dmg_desc),   "#FF4400", 99),
        UpgradeData(UPG_MAGNET,      s(R.string.sw_upg_magnet_name),       s(R.string.sw_upg_magnet_desc),       "#8844FF",  1),
        UpgradeData(UPG_MAGNET_DUR,  s(R.string.sw_upg_magnet_dur_name),   s(R.string.sw_upg_magnet_dur_desc),   "#AA66FF",  4),
        UpgradeData(UPG_DRONE,       s(R.string.sw_upg_drone_name),        s(R.string.sw_upg_drone_desc),        "#44FFAA",  3),
        UpgradeData(UPG_VAMPIRE,     s(R.string.sw_upg_vampire_name),      s(R.string.sw_upg_vampire_desc),      "#FF4488",  1),
        UpgradeData(UPG_NOVA,        s(R.string.sw_upg_nova_name),         s(R.string.sw_upg_nova_desc),         "#FFEE44",  1)
    ) }

    // ── Phase & threading ─────────────────────────────────────────────────────
    @Volatile private var phase = Phase.READY
    @Volatile private var pendingReset = false
    private var gameThread: Thread? = null
    @Volatile private var running = false

    // ── Player state ──────────────────────────────────────────────────────────
    private var playerX = VW / 2f
    private var playerY = VH * 0.8f
    private var playerHp = PLAYER_MAX_HP_BASE
    private var playerMaxHp = PLAYER_MAX_HP_BASE
    private var playerFireTimer = 0f
    private var playerDamageTimer = 0f
    private var playerVisible = true
    private var playerBlinkTimer = 0f

    // Player upgrades
    private var fireRateMult = 1f
    private var bulletCount = 1
    private var bulletDmg = 1
    private var shieldCharges = 0
    private var pierceCount = 0
    private var hasRocket = false
    private var rocketFireRate = 10f
    private var rocketFireTimer = 0f
    private val upgradeStacks = IntArray(16)  // 0-15

    // Magnet state
    private var magnetActive = false
    private var magnetTimer = 0f
    private var magnetCooldown = 0f
    private fun magnetDuration() = 5f + upgradeStacks[UPG_MAGNET_DUR] * 1.25f

    // Nova state
    private var novaAvailable = false
    private var novaFlashTimer = 0f
    private var lastSecondFingerTapMs = 0L

    // Perfect cycle & meteor phase
    private var perfectCycle = true
    private var meteorPhaseTimer = 0f
    private var meteorSpawnTimer = 0f
    private var meteorSessionCount = 0  // nb de phases météore déclenchées dans cette partie
    private var pendingUpgradePicks = 1

    // Active upgrades list for HUD display
    private val activeUpgrades = mutableListOf<String>()

    // ── Wave state ────────────────────────────────────────────────────────────
    private var waveNumber = 0
    private var waveEnemyCount = 0
    private var waveEnemiesSpawned = 0
    private var waveEnemiesKilled = 0
    private var waveClearTimer = 0f
    private var waveAnnounceTimer = 0f
    private var spawnTimer = 0f
    private var spawnInterval = 1.0f
    private var bossSpawned = false

    // ── Upgrade state ─────────────────────────────────────────────────────────
    private var upgradeChoices = listOf<UpgradeData>()

    // ── Entities ──────────────────────────────────────────────────────────────
    private val enemies = mutableListOf<Enemy>()
    private val playerBullets = mutableListOf<Bullet>()
    private val enemyBullets = mutableListOf<Bullet>()
    private val rockets = mutableListOf<Rocket>()
    private val drones = mutableListOf<Drone>()
    private val meteors = mutableListOf<Meteor>()
    private val spawnQueue = ArrayDeque<PendingSpawn>()
    private var convoyTimer = 0f
    private var enemyShotCounter = 0
    private val stars = mutableListOf<Star>()
    private val scorePopups = mutableListOf<ScorePopup>()

    // ── Score / records ───────────────────────────────────────────────────────
    private var score = 0
    private var bestScore = 0
    private var bestWave = 0
    private var elapsed = 0f
    private var newBestScore = false
    private var newBestWave = false

    // ── Bitmaps ───────────────────────────────────────────────────────────────
    private var playerBmp: Bitmap? = null
    private val enemyBmps = arrayOfNulls<Bitmap>(7)   // 7 types matching JS
    private val bossBmps  = arrayOfNulls<Bitmap>(2)   // 2 boss sprites from top half

    // ── Sprite sets ───────────────────────────────────────────────────────────
    // [shipsPath] for original sheet, [shipsPath, boss1Path, boss2Path] for families
    private val spriteSets = arrayOf(
        arrayOf("Stars war/StarsWar.png"),
        arrayOf("Stars war/ships/neonships.png",    "Stars war/Boss/neonboss1.png",    "Stars war/Boss/neonboss2.png"),
        arrayOf("Stars war/ships/Chtuluships.png",  "Stars war/Boss/Chtuluboss1.png",  "Stars war/Boss/chtuluboss2.png"),
        arrayOf("Stars war/ships/peintureships.png","Stars war/Boss/peintureboss1.png","Stars war/Boss/peintureboss2.png"),
        arrayOf("Stars war/ships/kawaiships.png",   "Stars war/Boss/kawaiboss1.png",   "Stars war/Boss/kawaiboss2.png")
    )
    private var currentSetIdx = -1
    @Volatile private var pendingFrame: SpriteFrame? = null
    private var pendingSetIdx = -1
    private var preloadThread: Thread? = null

    private class SpriteFrame(
        val player: Bitmap,
        val enemies: Array<Bitmap?>,
        val bosses: Array<Bitmap?>
    )

    // ── Callbacks audio (branchés depuis StarsWarActivity) ───────────────────
    var onPlayerShot:     (() -> Unit)? = null
    var onEnemyDestroyed: (() -> Unit)? = null
    var onBossDestroyed:  (() -> Unit)? = null
    var onPlayerHitCb:    (() -> Unit)? = null
    var onGameOverCb:     (() -> Unit)? = null
    var onNewWaveCb:      ((Int) -> Unit)? = null
    var onMeteorPhaseCb:  (() -> Unit)? = null

    // ── Touch ─────────────────────────────────────────────────────────────────
    private var dragPointerId = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ── Scale (letterbox) ─────────────────────────────────────────────────────
    private var scaleX = 1f
    private var scaleY = 1f
    private var offX = 0f
    private var offY = 0f

    // ── Paints ───────────────────────────────────────────────────────────────
    private val spritePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val pbPaint        = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80E8FF") }
    private val ebPaint        = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4055") }
    private val starPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hpBarPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayBgPaint = Paint().apply { color = Color.parseColor("#DD040810") }
    private val cardBgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#BB0a1228") }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.parseColor("#446688")
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 24f
        setShadowLayer(4f, 2f, 2f, Color.parseColor("#AA000000"))
    }
    private val hudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AABBCC"); textSize = 17f
        setShadowLayer(2f, 1f, 1f, Color.parseColor("#88000000"))
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 40f
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#BB0055FF"))
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBDDFF"); textSize = 22f; textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#99000000"))
    }
    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 21f
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#88000000"))
    }
    private val cardDescPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AABBDD"); textSize = 16f
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFCC"); textSize = 16f; textAlign = Paint.Align.CENTER
    }
    private val waveAnnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 48f
        textAlign = Paint.Align.CENTER
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#CC0088FF"))
    }
    private val upgHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88AABB"); textSize = 13f
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun s(id: Int) = context.getString(id)
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    // ── Prefs ─────────────────────────────────────────────────────────────────
    private val prefs by lazy { context.getSharedPreferences("stars_war_save", Context.MODE_PRIVATE) }

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        holder.addCallback(this)
        isFocusable = true
        loadSpriteSet(0)
        bestScore = prefs.getInt("best_score", 0)
        bestWave  = prefs.getInt("best_wave", 0)
        initStars()
    }

    private fun initStars() {
        repeat(STAR_COUNT) {
            stars += Star(
                x      = Random.nextFloat() * VW,
                y      = Random.nextFloat() * VH,
                speed  = Random.nextFloat() * 70f + 30f,
                radius = Random.nextFloat() * 1.8f + 0.4f,
                alpha  = Random.nextFloat() * 0.7f + 0.3f
            )
        }
    }

    // ── Asset loading ─────────────────────────────────────────────────────────
    // Chargement synchrone (initial ou fallback)
    private fun loadSpriteSet(idx: Int) {
        val set = spriteSets[idx]
        val frame = if (set.size == 1) loadOriginalFrame() else loadFamilyFrame(set[0], set[1], set[2])
        frame?.let { applySpriteFrame(it, idx) }
    }

    private fun applySpriteFrame(frame: SpriteFrame, idx: Int) {
        playerBmp?.recycle(); playerBmp = frame.player
        for (i in 0..6) { enemyBmps[i]?.recycle(); enemyBmps[i] = frame.enemies[i] }
        for (i in 0..1) { bossBmps[i]?.recycle(); bossBmps[i] = frame.bosses[i] }
        currentSetIdx = idx
    }

    // Spritesheet original 1024×1024 — retourne un SpriteFrame (thread-safe, pas d'accès aux fields)
    private fun loadOriginalFrame(): SpriteFrame? {
        var result: SpriteFrame? = null
        loadBitmap("Stars war/StarsWar.png") { sheet ->
            val enemyCoords = arrayOf(
                intArrayOf(0, 512), intArrayOf(512, 768), intArrayOf(0, 768),
                intArrayOf(256, 512), intArrayOf(768, 512), intArrayOf(256, 768), intArrayOf(512, 512)
            )
            val enemies = arrayOfNulls<Bitmap>(7)
            for ((i, coord) in enemyCoords.withIndex()) {
                val src = cropArgb(sheet, coord[0], coord[1], 256, 256)
                enemies[i] = scaledArgb(src, enemySizes[i].toInt(), enemySizes[i].toInt()).also { src.recycle() }
            }
            val pSrc = cropArgb(sheet, 768, 768, 256, 256)
            val player = rotated180(scaledArgb(pSrc, PLAYER_SIZE.toInt(), PLAYER_SIZE.toInt()).also { pSrc.recycle() })
            val bosses = arrayOfNulls<Bitmap>(2)
            for (i in 0..1) {
                val bSrc = cropArgb(sheet, if (i == 0) 512 else 0, 0, 512, 512)
                bosses[i] = scaledArgb(bSrc, BOSS_SIZE.toInt(), BOSS_SIZE.toInt()).also { bSrc.recycle() }
            }
            sheet.recycle()
            result = SpriteFrame(player, enemies, bosses)
        }
        return result
    }

    // Familles custom : grille 2×4, ships face le HAUT → rotation 180° ennemis, joueur sans rotation
    private fun loadFamilyFrame(shipsPath: String, boss1Path: String, boss2Path: String): SpriteFrame? {
        val enemies = arrayOfNulls<Bitmap>(7)
        var player: Bitmap? = null
        loadBitmap(shipsPath) { raw ->
            val sheet = removeWhite(raw)   // raw recycled
            val cW = sheet.width / 4; val cH = sheet.height / 2
            for (t in 0..6) {
                val src = cropArgb(sheet, (t % 4) * cW, (t / 4) * cH, cW, cH)
                enemies[t] = rotated180(scaledArgb(src, enemySizes[t].toInt(), enemySizes[t].toInt()).also { src.recycle() })
            }
            val pSrc = cropArgb(sheet, 3 * cW, cH, cW, cH)
            player = scaledArgb(pSrc, PLAYER_SIZE.toInt(), PLAYER_SIZE.toInt()).also { pSrc.recycle() }
            sheet.recycle()
        }
        val bosses = arrayOfNulls<Bitmap>(2)
        loadBitmap(boss1Path) { raw ->
            bosses[0] = scaledArgb(removeWhite(raw), BOSS_SIZE.toInt(), BOSS_SIZE.toInt())
        }
        loadBitmap(boss2Path) { raw ->
            bosses[1] = scaledArgb(removeWhite(raw), BOSS_SIZE.toInt(), BOSS_SIZE.toInt())
        }
        return player?.let { SpriteFrame(it, enemies, bosses) }
    }

    // Remplace les pixels blancs/quasi-blancs par du transparent (color-key)
    private fun removeWhite(src: Bitmap, threshold: Int = 238): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            if ((c shr 16 and 0xFF) >= threshold &&
                (c shr 8  and 0xFF) >= threshold &&
                (c        and 0xFF) >= threshold) pixels[i] = 0
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        return dst
    }

    // Extrait une région en ARGB_8888 propre via Canvas (préserve la transparence)
    private fun cropArgb(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(dst).drawBitmap(src, Rect(x, y, x + w, y + h), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    // Scale en ARGB_8888 via Canvas — préserve la transparence même sur bitmap hardware
    private fun scaledArgb(src: Bitmap, w: Int, h: Int): Bitmap {
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(dst).drawBitmap(soft, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        if (soft !== src) soft.recycle()
        return dst
    }

    // Rotation 180° via Canvas — consomme src
    private fun rotated180(src: Bitmap): Bitmap {
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply { postRotate(180f, src.width / 2f, src.height / 2f) }
        Canvas(dst).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return dst
    }

    private inline fun loadBitmap(path: String, block: (Bitmap) -> Unit) {
        try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts)?.let(block) }
        } catch (_: Exception) {}
    }

    // ── Surface callbacks ─────────────────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        if (gameThread?.isAlive != true) {
            gameThread = Thread(this, "StarsWarThread").apply { start() }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val ar = width.toFloat() / height
        val gameAr = VW / VH
        if (ar > gameAr) {
            scaleY = height / VH; scaleX = scaleY
            offX = (width - VW * scaleX) / 2f; offY = 0f
        } else {
            scaleX = width / VW; scaleY = scaleX
            offX = 0f; offY = (height - VH * scaleY) / 2f
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        joinThread()
    }

    private fun joinThread() {
        try { gameThread?.join(500) } catch (_: InterruptedException) {}
        gameThread = null
    }

    // ── Public controls ───────────────────────────────────────────────────────
    fun pause() {
        if (phase == Phase.RUNNING) phase = Phase.PAUSED
        running = false
        joinThread()
    }

    fun resume() {
        running = true
        if (gameThread?.isAlive != true) {
            gameThread = Thread(this, "StarsWarThread").apply { start() }
        }
    }

    fun togglePause() {
        phase = when (phase) {
            Phase.RUNNING -> Phase.PAUSED
            Phase.PAUSED  -> Phase.RUNNING
            else          -> phase
        }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    override fun run() {
        var lastNano = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNano = now

            when (phase) {
                Phase.RUNNING    -> update(dt)
                Phase.WAVE_CLEAR -> updateWaveClear(dt)
                Phase.METEOR     -> updateMeteorPhase(dt)
                else             -> {}
            }

            val c = holder.lockCanvas() ?: continue
            try { drawFrame(c) } finally { holder.unlockCanvasAndPost(c) }

            val remainMs = 16L - (System.nanoTime() - now) / 1_000_000L
            if (remainMs > 0) Thread.sleep(remainMs)
        }
    }

    // ── Wave lifecycle ────────────────────────────────────────────────────────
    private fun startWave(n: Int) {
        // Début d'un nouveau groupe → appliquer le set préchargé (ou charger en sync si pas prêt)
        if ((n - 1) % 5 == 0) {
            perfectCycle = true
            preloadThread?.join(); preloadThread = null
            val frame = pendingFrame; pendingFrame = null
            if (frame != null) {
                applySpriteFrame(frame, pendingSetIdx)
            } else {
                // Fallback synchrone (1ère vague ou preload raté)
                val next = (spriteSets.indices - currentSetIdx).random()
                loadSpriteSet(next)
            }
        }
        onNewWaveCb?.invoke(n)
        waveNumber = n
        val isBossWave = n % 5 == 0
        waveEnemyCount = if (isBossWave) 6 + n / 5 * 2 else (6 + n * 2).coerceAtMost(24)
        waveEnemiesSpawned = 0
        waveEnemiesKilled  = 0
        bossSpawned = false
        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        rockets.clear()
        spawnQueue.clear()
        convoyTimer = 0f
        spawnInterval = (0.95f - n * 0.03f).coerceAtLeast(0.32f)
        spawnTimer = 0.5f
        waveAnnounceTimer = WAVE_ANNOUNCE_DURATION
        if (upgradeStacks[UPG_NOVA] > 0) novaAvailable = true
        phase = Phase.RUNNING
    }

    private fun updateWaveClear(dt: Float) {
        for (s in stars) { s.y += s.speed * dt; if (s.y > VH) s.y = -s.radius * 2f }
        waveClearTimer -= dt
        if (waveClearTimer <= 0f) {
            showNextUpgradeOrWave()
        }
    }

    private fun showNextUpgradeOrWave() {
        val choices = generateUpgradeChoices()
        if (choices.isEmpty() || pendingUpgradePicks <= 0) {
            pendingUpgradePicks = 0
            startWave(waveNumber + 1)
        } else {
            upgradeChoices = choices
            phase = Phase.UPGRADE
        }
    }

    private fun generateUpgradeChoices(): List<UpgradeData> {
        val bossJustDone = waveNumber % 5 == 0
        val available = upgradePool.filter { upg ->
            val s = upgradeStacks[upg.id]
            when (upg.id) {
                UPG_HEAL        -> playerHp < playerMaxHp && s < upg.maxStack
                UPG_SHIELD      -> shieldCharges < 3
                UPG_MULTI_SHOT  -> bossJustDone && s < upg.maxStack
                UPG_MAX_HP_RARE -> bossJustDone && s < upg.maxStack && Random.nextFloat() < 0.30f
                UPG_ROCKET      -> s < upg.maxStack
                UPG_ROCKET_RATE -> hasRocket && s < upg.maxStack
                UPG_ROCKET_DMG  -> hasRocket && s < upg.maxStack
                UPG_MAGNET      -> bossJustDone && s < upg.maxStack
                UPG_MAGNET_DUR  -> upgradeStacks[UPG_MAGNET] > 0 && s < upg.maxStack
                UPG_DRONE       -> s < upg.maxStack
                UPG_VAMPIRE     -> bossJustDone && waveNumber >= 15 && s < upg.maxStack
                UPG_NOVA        -> bossJustDone && upgradeStacks[UPG_MAGNET] > 0 && s < upg.maxStack
                else            -> s < upg.maxStack
            }
        }.toMutableList()
        available.shuffle()
        return available.take(3)
    }

    private fun applyUpgrade(upg: UpgradeData) {
        upgradeStacks[upg.id]++
        when (upg.id) {
            UPG_RAPID_FIRE  -> fireRateMult *= 0.95f
            UPG_MULTI_SHOT  -> bulletCount = (bulletCount + 1).coerceAtMost(5)
            UPG_POWER_SHOT  -> bulletDmg++
            UPG_SHIELD      -> shieldCharges = (shieldCharges + 1).coerceAtMost(3)
            UPG_HEAL        -> playerHp = (playerHp + 1).coerceAtMost(playerMaxHp)
            UPG_MAX_HP_RARE -> { playerMaxHp++; playerHp = playerMaxHp }
            UPG_PIERCE      -> pierceCount++
            UPG_ROCKET      -> { hasRocket = true; rocketFireTimer = rocketFireRate }
            UPG_ROCKET_RATE -> rocketFireRate *= 0.95f
            UPG_ROCKET_DMG  -> { /* stacks tracked via upgradeStacks */ }
            UPG_MAGNET      -> { magnetCooldown = 0f }
            UPG_MAGNET_DUR  -> { /* stacks tracked via upgradeStacks */ }
            UPG_DRONE       -> {
                val startAngle = if (drones.isEmpty()) 0f else drones.last().angle + 2.09f
                drones += Drone(startAngle, DRONE_FIRE_RATE)
            }
            UPG_VAMPIRE     -> { /* stacks tracked via upgradeStacks */ }
            UPG_NOVA        -> { novaAvailable = true }
        }
        rebuildUpgradeHudList()
        pendingUpgradePicks--
        showNextUpgradeOrWave()
    }

    private fun rebuildUpgradeHudList() {
        activeUpgrades.clear()
        if (fireRateMult < 0.99f) activeUpgrades += "RF×${upgradeStacks[UPG_RAPID_FIRE]}"
        if (bulletCount > 1)      activeUpgrades += s(R.string.sw_hud_multishot, bulletCount)
        if (bulletDmg > 1)        activeUpgrades += "Dmg+${bulletDmg - 1}"
        if (pierceCount > 0)      activeUpgrades += s(R.string.sw_hud_pierce, pierceCount)
        if (hasRocket)            activeUpgrades += if (upgradeStacks[UPG_ROCKET_RATE] > 0)
            s(R.string.sw_hud_rocket, upgradeStacks[UPG_ROCKET_RATE]) else s(R.string.sw_hud_rocket_base)
        if (upgradeStacks[UPG_ROCKET_DMG] > 0) activeUpgrades += "RktDmg+${upgradeStacks[UPG_ROCKET_DMG]}"
        if (drones.isNotEmpty())              activeUpgrades += "×${drones.size}Drone"
        if (upgradeStacks[UPG_VAMPIRE] > 0)  activeUpgrades += s(R.string.sw_hud_leech)
    }

    // ── Update ────────────────────────────────────────────────────────────────
    private fun update(dt: Float) {
        if (pendingReset) { pendingReset = false; resetGame() }
        if (waveNumber == 0) { startWave(1); return }
        elapsed += dt

        // Wave announce timer
        if (waveAnnounceTimer > 0f) waveAnnounceTimer -= dt

        // Stars scroll
        for (s in stars) { s.y += s.speed * dt; if (s.y > VH) s.y = -s.radius * 2f }

        // Player fire
        playerFireTimer -= dt
        if (playerFireTimer <= 0f) {
            fireBullets()
            playerFireTimer = PLAYER_FIRE_RATE_BASE * fireRateMult
        }

        // Rocket fire
        if (hasRocket) {
            rocketFireTimer -= dt
            if (rocketFireTimer <= 0f) {
                rockets += Rocket(playerX, playerY - PLAYER_SIZE / 2f)
                rocketFireTimer = rocketFireRate
            }
        }

        // Player blink (invincibility)
        if (playerDamageTimer > 0f) {
            playerDamageTimer -= dt
            playerBlinkTimer += dt
            if (playerBlinkTimer >= 0.1f) { playerBlinkTimer = 0f; playerVisible = !playerVisible }
            if (playerDamageTimer <= 0f)  { playerDamageTimer = 0f; playerVisible = true }
        }

        // Player bullets (et balles de drone)
        val pbIter = playerBullets.iterator()
        while (pbIter.hasNext()) {
            val b = pbIter.next()
            if (b.isDrone) {
                b.x += b.dx * DRONE_BULLET_SPEED * dt
                b.y += b.dy * DRONE_BULLET_SPEED * dt
                if (b.y < -PBULLET_H || b.y > VH + PBULLET_H || b.x < -20f || b.x > VW + 20f) pbIter.remove()
            } else {
                b.y -= PLAYER_BULLET_SPEED * dt
                if (b.y < -PBULLET_H) pbIter.remove()
            }
        }

        // Enemy bullets
        val ebIter = enemyBullets.iterator()
        while (ebIter.hasNext()) {
            val b = ebIter.next()
            b.x += b.dx * EBULLET_SPEED * dt
            b.y += b.dy * EBULLET_SPEED * dt
            if (circleOverlap(b.x, b.y, EBULLET_W / 2f, playerX, playerY, PLAYER_HITBOX_R)) {
                ebIter.remove(); hitPlayer(); continue
            }
            if (b.y > VH + 20f || b.y < -20f || b.x < -20f || b.x > VW + 20f) ebIter.remove()
        }

        // Champ magnétique : repousse les balles ennemies proches
        if (magnetActive) {
            magnetTimer -= dt
            if (magnetTimer <= 0f) {
                magnetActive = false
                magnetCooldown = MAGNET_COOLDOWN_MAX
            } else {
                for (b in enemyBullets) {
                    val ddx = b.x - playerX; val ddy = b.y - playerY
                    val dist = sqrt(ddx * ddx + ddy * ddy)
                    if (dist < MAGNET_RADIUS && dist > 1f) {
                        val strength = (1f - dist / MAGNET_RADIUS)
                        // Déplacement direct (effet immédiat)
                        val push = strength * 320f * dt
                        b.x += ddx / dist * push
                        b.y += ddy / dist * push
                        // Déviation de trajectoire
                        val deflect = strength * 2.0f * dt
                        b.dx += ddx / dist * deflect
                        b.dy += ddy / dist * deflect
                        val len = sqrt(b.dx * b.dx + b.dy * b.dy).coerceAtLeast(0.01f)
                        b.dx /= len; b.dy /= len
                    }
                }
            }
        } else if (magnetCooldown > 0f) {
            magnetCooldown = (magnetCooldown - dt).coerceAtLeast(0f)
        }

        // Flash nova
        if (novaFlashTimer > 0f) novaFlashTimer = (novaFlashTimer - dt).coerceAtLeast(0f)

        // Drones : orbite + tir
        val droneDmg = (bulletDmg / 2).coerceAtLeast(1)
        for (drone in drones) {
            drone.angle += DRONE_ORBIT_SPEED * dt
            val droneX = playerX + cos(drone.angle) * DRONE_ORBIT_R
            val droneY = playerY + sin(drone.angle) * DRONE_ORBIT_R
            drone.fireCooldown -= dt
            if (drone.fireCooldown <= 0f && enemies.isNotEmpty()) {
                val nearEnemy = enemies.filter { e ->
                    val ex = e.x - playerX; val ey = e.y - playerY
                    ex * ex + ey * ey < DRONE_ALERT_RANGE * DRONE_ALERT_RANGE
                }.minByOrNull { e ->
                    val ex = e.x - playerX; val ey = e.y - playerY; ex * ex + ey * ey
                }
                val isAlert = nearEnemy != null
                val target = nearEnemy ?: enemies[Random.nextInt(enemies.size)]
                val tdx = target.x - droneX; val tdy = target.y - droneY
                val dist = sqrt(tdx * tdx + tdy * tdy).coerceAtLeast(0.01f)
                val spread = if (isAlert) 0f else (Random.nextFloat() - 0.5f) * 0.55f
                val bdx = tdx / dist + spread; val bdy = tdy / dist + spread
                val bLen = sqrt(bdx * bdx + bdy * bdy).coerceAtLeast(0.01f)
                playerBullets += Bullet(droneX, droneY, dx = bdx / bLen, dy = bdy / bLen,
                    dmg = droneDmg, isDrone = true)
                drone.fireCooldown = if (isAlert) DRONE_FIRE_RATE_ALERT else DRONE_FIRE_RATE
            }
        }

        // Drain de la queue de convoi (indépendant du timer de spawn normal)
        if (spawnQueue.isNotEmpty()) {
            convoyTimer -= dt
            if (convoyTimer <= 0f) {
                val ps = spawnQueue.removeFirst()
                spawnQueuedEnemy(ps)
                waveEnemiesSpawned++
                convoyTimer = CONVOY_SPAWN_INT
            }
        }

        // Spawns réguliers (pausés pendant qu'un convoi se déploie)
        if (waveEnemiesSpawned < waveEnemyCount && spawnQueue.isEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnNextEnemy()
                spawnTimer = spawnInterval
            }
        }

        // Move enemies + combat
        val dead = mutableListOf<Enemy>()
        for (enemy in enemies) {
            enemy.elapsed += dt
            when (enemy.path) {
                PATH_LINE -> {
                    enemy.y += enemy.speed * dt
                }
                PATH_SIN -> {
                    enemy.y += enemy.speed * dt
                    if (enemy.isBoss) enemy.y = enemy.y.coerceAtMost(BOSS_MAX_Y)
                    enemy.x = enemy.baseX + sin(enemy.elapsed * enemy.sinFreq * 2f * PI.toFloat()) * enemy.sinAmp
                }
                else -> {
                    // Chemins en formation : Bézier cubique en entrée, puis oscillation légère
                    val t = (enemy.elapsed / enemy.entryDur).coerceIn(0f, 1f)
                    if (t < 1f) {
                        val sx = enemy.entryX0; val sy = enemy.entryY0
                        val fx = enemy.formX;   val fy = enemy.formY
                        val cp1x = when (enemy.path) { PATH_ENTRY_L -> VW*0.15f; PATH_ENTRY_R -> VW*0.85f; PATH_LOOP_L -> -VW*0.25f; else -> VW*1.25f }
                        val cp1y = when (enemy.path) { PATH_LOOP_L, PATH_LOOP_R -> VH*0.85f; else -> VH*0.82f }
                        val cp2x = when (enemy.path) { PATH_ENTRY_L, PATH_ENTRY_R -> fx; PATH_LOOP_L -> VW*1.1f; else -> -VW*0.1f }
                        val cp2y = when (enemy.path) { PATH_ENTRY_L, PATH_ENTRY_R -> fy + 280f; else -> VH*0.38f }
                        enemy.x = cubicBezier(t, sx, cp1x, cp2x, fx)
                        enemy.y = cubicBezier(t, sy, cp1y, cp2y, fy)
                    } else {
                        // En formation : légère oscillation horizontale
                        enemy.x = enemy.formX + sin(enemy.elapsed * 0.8f + enemy.formX * 0.005f) * 20f
                        enemy.y = enemy.formY
                    }
                }
            }

            // Enemy fire
            if (enemy.canShoot) {
                enemy.fireCooldown -= dt
                if (enemy.fireCooldown <= 0f) {
                    val eHalf = (if (enemy.isBoss) BOSS_SIZE else enemySizes.getOrElse(enemy.typeIdx) { ENEMY_SIZE_DEFAULT }) / 2f
                    val bx = enemy.x; val by = enemy.y + eHalf
                    enemyShotCounter++
                    val fireMissile = waveNumber > 5 && enemyShotCounter % missileInterval() == 0
                    val fireFast    = !fireMissile && waveNumber >= 4 && enemyShotCounter % fastShotInterval() == 0
                    if (fireMissile) {
                        val ddx = playerX - bx; val ddy = playerY - by
                        val dist = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(1f)
                        enemyBullets += Bullet(bx, by, dx = ddx / dist, dy = ddy / dist, isMissile = true)
                    } else if (fireFast) {
                        enemyBullets += Bullet(bx, by, dy = 1.5f)
                    } else {
                        enemyBullets += Bullet(bx, by)
                    }
                    if (enemy.isBoss) {
                        fireBossPattern(bx, by)
                    }
                    enemy.fireCooldown = enemy.fireRate
                }
            }

            // Player-enemy body collision
            val eSize = if (enemy.isBoss) BOSS_SIZE else enemySizes.getOrElse(enemy.typeIdx) { ENEMY_SIZE_DEFAULT }
            val hitR = eSize * (if (enemy.isBoss) 0.40f else 0.42f)
            if (circleOverlap(enemy.x, enemy.y, hitR, playerX, playerY, PLAYER_HITBOX_R)) hitPlayer()

            // Bullet-enemy collision (handle pierce)
            val toRemovePb = mutableListOf<Bullet>()
            for (pb in playerBullets) {
                if (circleOverlap(pb.x, pb.y, PBULLET_W / 2f, enemy.x, enemy.y, hitR)) {
                    enemy.hp -= if (pb.dmg >= 0) pb.dmg else bulletDmg
                    if (pb.pierceLeft > 0) pb.pierceLeft-- else toRemovePb += pb
                }
            }
            playerBullets.removeAll(toRemovePb)

            if (enemy.hp <= 0) {
                score += enemyDefs.getOrNull(enemy.typeIdx)?.score
                    ?: if (enemy.isBoss) 500 + waveNumber * 50 else 80
                scorePopups += ScorePopup(
                    enemy.x, enemy.y, 0.9f,
                    "+${enemyDefs.getOrNull(enemy.typeIdx)?.score ?: (500 + waveNumber * 50)}",
                    enemy.isBoss
                )
                waveEnemiesKilled++
                dead += enemy
                if (enemy.isBoss) onBossDestroyed?.invoke() else onEnemyDestroyed?.invoke()
                if (upgradeStacks[UPG_VAMPIRE] > 0 && Random.nextFloat() < 0.05f)
                    playerHp = (playerHp + 1).coerceAtMost(playerMaxHp)
            }
        }
        // Rockets : mouvement + collision
        if (rockets.isNotEmpty()) {
            val deadRockets = mutableListOf<Rocket>()
            for (rocket in rockets) {
                val target = enemies.minByOrNull { e ->
                    val dx = e.x - rocket.x; val dy = e.y - rocket.y; dx * dx + dy * dy
                }
                if (target != null) {
                    val dx = target.x - rocket.x; val dy = target.y - rocket.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                    rocket.x += dx / dist * ROCKET_SPEED * dt
                    rocket.y += dy / dist * ROCKET_SPEED * dt
                } else {
                    rocket.y -= ROCKET_SPEED * dt
                }
                if (rocket.y < -60f || rocket.x < -60f || rocket.x > VW + 60f) {
                    deadRockets += rocket; continue
                }
                for (enemy in enemies) {
                    if (enemy in dead) continue
                    val eSize = if (enemy.isBoss) BOSS_SIZE else enemySizes.getOrElse(enemy.typeIdx) { ENEMY_SIZE_DEFAULT }
                    val hitR = eSize * (if (enemy.isBoss) 0.40f else 0.42f)
                    if (circleOverlap(rocket.x, rocket.y, ROCKET_R, enemy.x, enemy.y, hitR)) {
                        enemy.hp -= bulletDmg * (2 + upgradeStacks[UPG_ROCKET_DMG])
                        if (enemy.hp <= 0) {
                            val pts = enemyDefs.getOrNull(enemy.typeIdx)?.score ?: if (enemy.isBoss) 500 + waveNumber * 50 else 80
                            score += pts
                            scorePopups += ScorePopup(enemy.x, enemy.y, 0.9f, "+$pts", enemy.isBoss)
                            waveEnemiesKilled++; dead += enemy
                            if (upgradeStacks[UPG_VAMPIRE] > 0 && Random.nextFloat() < 0.05f)
                                playerHp = (playerHp + 1).coerceAtMost(playerMaxHp)
                        }
                        deadRockets += rocket; break
                    }
                }
            }
            rockets.removeAll(deadRockets)
        }

        enemies.removeAll(dead)
        // LINE/SIN : reviennent en haut si sortent par le bas — formation : restent dans leur slot
        for (e in enemies) {
            if (e.path >= PATH_ENTRY_L) continue
            val sz = if (e.isBoss) BOSS_SIZE else enemySizes.getOrElse(e.typeIdx) { ENEMY_SIZE_DEFAULT }
            if (e.y > VH + sz) e.y = -sz
        }

        // Score popups
        scorePopups.removeAll { p -> p.y -= 55f * dt; p.life -= dt; p.life <= 0f }

        // Wave complete?
        if (waveEnemiesSpawned >= waveEnemyCount && enemies.isEmpty() && enemyBullets.isEmpty()) {
            if (waveNumber % 5 == 0 && preloadThread == null) {
                pendingSetIdx = (spriteSets.indices - currentSetIdx).random()
                val idx = pendingSetIdx; val set = spriteSets[idx]
                preloadThread = Thread {
                    pendingFrame = if (set.size == 1) loadOriginalFrame() else loadFamilyFrame(set[0], set[1], set[2])
                }.also { it.start() }
            }
            if (waveNumber % 5 == 0 && perfectCycle) {
                startMeteorPhase()
            } else {
                pendingUpgradePicks = 1
                waveClearTimer = WAVE_CLEAR_DELAY
                phase = Phase.WAVE_CLEAR
            }
        }
    }

    private fun fireBullets() {
        val spacing = 18f
        for (i in 0 until bulletCount) {
            val offset = (i - (bulletCount - 1) / 2f) * spacing
            playerBullets += Bullet(playerX + offset, playerY - PLAYER_SIZE / 2f, pierceCount)
        }
        onPlayerShot?.invoke()
    }

    private fun startMeteorPhase() {
        onMeteorPhaseCb?.invoke()
        meteorSessionCount++
        meteors.clear()
        playerBullets.clear()
        enemyBullets.clear()
        rockets.clear()
        meteorPhaseTimer = METEOR_PHASE_DURATION
        meteorSpawnTimer = 1.0f
        pendingUpgradePicks = 2
        phase = Phase.METEOR
    }

    private fun updateMeteorPhase(dt: Float) {
        for (st in stars) { st.y += st.speed * dt; if (st.y > VH) st.y = -st.radius * 2f }

        meteorPhaseTimer -= dt
        if (meteorPhaseTimer <= 0f) {
            meteors.clear()
            playerBullets.clear()
            showNextUpgradeOrWave()
            return
        }

        // Tir joueur désactivé pendant la phase météore

        // Spawn météores : de plus en plus dense et vite, amplifié par le nb de sessions
        meteorSpawnTimer -= dt
        val elapsed = METEOR_PHASE_DURATION - meteorPhaseTimer
        val sessionBonus = (meteorSessionCount - 1).coerceAtLeast(0)
        val spawnStart = (1.4f - sessionBonus * 0.18f).coerceAtLeast(0.55f)
        val spawnMin   = (0.28f - sessionBonus * 0.04f).coerceAtLeast(0.10f)
        val spawnRate  = (spawnStart - elapsed * 0.07f).coerceAtLeast(spawnMin)
        if (meteorSpawnTimer <= 0f) {
            spawnMeteor()
            // À partir de la 3e session, parfois double-spawn
            if (meteorSessionCount >= 3 && Random.nextFloat() < 0.35f + sessionBonus * 0.08f) spawnMeteor()
            meteorSpawnTimer = spawnRate
        }

        // Mouvement balles joueur (passent à travers les météores)
        val pbIter = playerBullets.iterator()
        while (pbIter.hasNext()) {
            val b = pbIter.next()
            b.y -= PLAYER_BULLET_SPEED * dt
            if (b.y < -PBULLET_H) pbIter.remove()
        }

        // Mouvement météores + collision joueur (pas de dégâts, perte du double reward)
        val dead = mutableListOf<Meteor>()
        for (m in meteors) {
            m.x += m.dx * m.speed * dt
            m.y += m.speed * dt
            if (m.y > VH + m.radius * 2) { dead += m; continue }
            if (playerDamageTimer <= 0f && circleOverlap(m.x, m.y, m.radius * 0.75f, playerX, playerY, PLAYER_HITBOX_R)) {
                pendingUpgradePicks = 1
                playerDamageTimer = PLAYER_DAMAGE_COOLDOWN
                playerBlinkTimer = 0f
            }
        }
        meteors.removeAll(dead)

        // Invincibilité joueur
        if (playerDamageTimer > 0f) {
            playerDamageTimer -= dt
            playerBlinkTimer += dt
            if (playerBlinkTimer >= 0.1f) { playerBlinkTimer = 0f; playerVisible = !playerVisible }
            if (playerDamageTimer <= 0f) { playerDamageTimer = 0f; playerVisible = true }
        }
    }

    private fun spawnMeteor() {
        val radius = Random.nextFloat() * 22f + 10f
        val x = Random.nextFloat() * (VW - radius * 2f) + radius
        val elapsed = METEOR_PHASE_DURATION - meteorPhaseTimer
        val sessionBonus = (meteorSessionCount - 1).coerceAtLeast(0)
        val baseSpeed = Random.nextFloat() * 130f + 70f
        val speedCap = (380f + sessionBonus * 50f).coerceAtMost(600f)
        val speed = (baseSpeed + elapsed * 8f + sessionBonus * 45f).coerceAtMost(speedCap)
        val dx = (Random.nextFloat() - 0.5f) * 0.7f
        meteors += Meteor(x, -radius * 2f, radius, speed, dx)
    }

    private fun launchNova() {
        novaAvailable = false
        novaFlashTimer = 0.45f
        val novaR2 = NOVA_RADIUS * NOVA_RADIUS
        enemyBullets.removeAll { b ->
            val dx = b.x - playerX; val dy = b.y - playerY
            dx * dx + dy * dy <= novaR2
        }
    }

    private fun enemyHpBonus(): Int = when {
        waveNumber <= 5  -> (waveNumber - 1) / 2
        waveNumber <= 15 -> 2 + (waveNumber - 5)
        else             -> 12 + (waveNumber - 15) * 2
    }

    private fun fireBossPattern(bx: Float, by: Float) {
        val patternCount = when {
            waveNumber >= 20 -> 8
            waveNumber >= 15 -> 6
            waveNumber >= 10 -> 4
            else             -> 2
        }
        val spreadAngle = when {
            waveNumber >= 20 -> PI.toFloat() * 0.9f
            waveNumber >= 15 -> PI.toFloat() * 0.65f
            waveNumber >= 10 -> PI.toFloat() * 0.5f
            else             -> PI.toFloat() * 0.3f
        }
        val towardPlayer = waveNumber >= 10 && Random.nextFloat() < 0.45f
        val aimAngle = if (towardPlayer) {
            atan2(playerY - by, playerX - bx)
        } else {
            PI.toFloat() / 2f
        }
        val baseAngle = aimAngle + (Random.nextFloat() - 0.5f) * 0.3f
        for (i in 0 until patternCount) {
            val angle = if (patternCount == 1) baseAngle
            else baseAngle - spreadAngle / 2f + i.toFloat() / (patternCount - 1) * spreadAngle
            enemyBullets += Bullet(bx, by, dx = cos(angle), dy = sin(angle))
        }
    }

    private fun missileInterval() = when {
        waveNumber <= 10 -> 10
        waveNumber <= 15 -> 7
        waveNumber <= 20 -> 5
        else             -> 3
    }

    private fun fastShotInterval() = when {
        waveNumber < 4   -> Int.MAX_VALUE
        waveNumber <= 10 -> 15
        waveNumber <= 20 -> 10
        waveNumber <= 50 -> 8
        else             -> 6
    }

    private fun cubicBezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u*u*u*p0 + 3f*u*u*t*p1 + 3f*u*t*t*p2 + t*t*t*p3
    }

    private fun convoyChance() = when {
        waveNumber >= 8 -> 0.55f
        waveNumber >= 5 -> 0.40f
        waveNumber >= 3 -> 0.25f
        else            -> 0.15f
    }

    private fun getAvailableTypes(): IntArray = when {
        waveNumber <= 1 -> intArrayOf(0, 1)
        waveNumber <= 3 -> intArrayOf(0, 1, 4)
        waveNumber <= 5 -> intArrayOf(0, 1, 2, 3, 4)
        waveNumber <= 8 -> intArrayOf(0, 1, 2, 3, 4, 5)
        else            -> intArrayOf(0, 1, 2, 3, 4, 5, 6)
    }

    private fun queueConvoy() {
        val available = getAvailableTypes()
        val typeIdx = available[Random.nextInt(available.size)]
        val fromLeft = Random.nextBoolean()
        val useLoop = waveNumber >= 4 && Random.nextFloat() < 0.45f
        val pathType = when {
            useLoop && fromLeft  -> PATH_LOOP_L
            useLoop && !fromLeft -> PATH_LOOP_R
            fromLeft             -> PATH_ENTRY_L
            else                 -> PATH_ENTRY_R
        }
        val count = Random.nextInt(3, 6)
        val formYBase = VH * (0.09f + Random.nextFloat() * 0.08f)
        val slots = FloatArray(count) { i ->
            VW * (0.12f + i.toFloat() / (count - 1).coerceAtLeast(1) * 0.76f)
        }
        val ex = if (pathType == PATH_ENTRY_L || pathType == PATH_LOOP_L) -ENEMY_SIZE_DEFAULT else VW + ENEMY_SIZE_DEFAULT
        val ey = VH * 0.45f
        for (slot in slots) {
            spawnQueue += PendingSpawn(typeIdx, pathType, slot, formYBase + Random.nextFloat() * 12f, ex, ey)
        }
        convoyTimer = 0f
    }

    private fun spawnQueuedEnemy(ps: PendingSpawn) {
        val def = enemyDefs[ps.typeIdx]
        val hp = def.hp + enemyHpBonus()
        val dur = if (ps.path >= PATH_LOOP_L) ENTRY_DUR_LOOP else ENTRY_DUR_SWEEP
        enemies += Enemy(
            x = ps.entryX, y = ps.entryY,
            typeIdx = ps.typeIdx, hp = hp, maxHp = hp,
            speed = 0f,
            canShoot = def.canShoot, fireRate = def.fireRate,
            fireCooldown = def.fireRate * (0.3f + Random.nextFloat() * 0.7f),
            path = ps.path, baseX = ps.formX,
            sinAmp = 0f, sinFreq = 0f,
            entryX0 = ps.entryX, entryY0 = ps.entryY,
            formX = ps.formX, formY = ps.formY,
            entryDur = dur
        )
    }

    private fun spawnNextEnemy() {
        val isBossWave = waveNumber % 5 == 0
        if (isBossWave && !bossSpawned && waveEnemiesSpawned == 0) {
            spawnBoss()
            waveEnemiesSpawned++
        } else if (waveNumber >= 2 && Random.nextFloat() < convoyChance()) {
            queueConvoy()
            // waveEnemiesSpawned est incrémenté un-par-un dans le drain du convoi
        } else {
            spawnNormalEnemy()
            waveEnemiesSpawned++
        }
    }

    private fun spawnNormalEnemy() {
        val lane = Random.nextInt(5)
        val x = VW / 6f * (lane + 1)
        val available = getAvailableTypes()
        val typeIdx = available[Random.nextInt(available.size)]
        val def = enemyDefs[typeIdx]
        val speedBoost = 1f + waveNumber * 0.04f
        val hp = def.hp + enemyHpBonus()
        enemies += Enemy(
            x = x, y = -ENEMY_SIZE_DEFAULT,
            typeIdx = typeIdx, hp = hp, maxHp = hp,
            speed = def.speed * speedBoost,
            canShoot = def.canShoot, fireRate = def.fireRate,
            fireCooldown = if (def.canShoot) Random.nextFloat() * def.fireRate else 0f,
            path = def.path, baseX = x,
            sinAmp = Random.nextFloat() * 50f + 35f,
            sinFreq = Random.nextFloat() * 0.9f + 0.7f
        )
    }

    private fun spawnBoss() {
        bossSpawned = true
        val bossTypeIdx = if ((waveNumber / 5) % 2 == 1) 8 else 9
        val hp = waveNumber * waveNumber + waveNumber * 2 + 5
        enemies += Enemy(
            x = VW / 2f, y = -BOSS_SIZE,
            typeIdx = bossTypeIdx, hp = hp, maxHp = hp,
            speed = 38f,
            canShoot = true, fireRate = (1.0f - waveNumber * 0.025f).coerceAtLeast(0.4f),
            fireCooldown = (4.5f - waveNumber * 0.12f).coerceAtLeast(1.5f),
            path = PATH_SIN, baseX = VW / 2f,
            sinAmp = 90f, sinFreq = 0.45f,
            isBoss = true
        )
    }

    private fun hitPlayer() {
        if (playerDamageTimer > 0f) return
        if (shieldCharges > 0) {
            shieldCharges--
            playerDamageTimer = PLAYER_DAMAGE_COOLDOWN * 0.5f
            playerBlinkTimer = 0f
            return
        }
        perfectCycle = false
        playerHp--
        onPlayerHitCb?.invoke()
        if (playerHp <= 0) { triggerGameOver(); return }
        playerDamageTimer = PLAYER_DAMAGE_COOLDOWN
        playerBlinkTimer = 0f
    }

    private fun triggerGameOver() {
        onGameOverCb?.invoke()
        phase = Phase.GAME_OVER
        val editor = prefs.edit()
        if (score > bestScore) { bestScore = score; newBestScore = true; editor.putInt("best_score", bestScore) }
        if (waveNumber > bestWave) { bestWave = waveNumber; newBestWave = true; editor.putInt("best_wave", bestWave) }
        editor.apply()
    }

    private fun circleOverlap(x1: Float, y1: Float, r1: Float, x2: Float, y2: Float, r2: Float): Boolean {
        val dx = x1 - x2; val dy = y1 - y2; val d = r1 + r2; return dx * dx + dy * dy < d * d
    }

    private fun resetGame() {
        playerX = VW / 2f; playerY = VH * 0.8f
        playerHp = PLAYER_MAX_HP_BASE; playerMaxHp = PLAYER_MAX_HP_BASE
        playerFireTimer = 0f; playerDamageTimer = 0f; playerVisible = true; playerBlinkTimer = 0f
        fireRateMult = 1f; bulletCount = 1; bulletDmg = 1
        shieldCharges = 0; pierceCount = 0
        hasRocket = false; rocketFireRate = 10f; rocketFireTimer = 0f
        magnetActive = false; magnetTimer = 0f; magnetCooldown = 0f
        novaAvailable = false; novaFlashTimer = 0f; lastSecondFingerTapMs = 0L
        perfectCycle = true; meteorPhaseTimer = 0f; meteorSpawnTimer = 0f; meteorSessionCount = 0; pendingUpgradePicks = 1
        drones.clear(); meteors.clear()
        upgradeStacks.fill(0); activeUpgrades.clear()
        enemies.clear(); playerBullets.clear(); enemyBullets.clear(); rockets.clear(); scorePopups.clear()
        spawnQueue.clear(); convoyTimer = 0f; enemyShotCounter = 0
        score = 0; elapsed = 0f; newBestScore = false; newBestWave = false
        waveNumber = 0; upgradeChoices = emptyList()
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (phase) {
                    Phase.READY -> {
                        pendingReset = true; phase = Phase.RUNNING
                        // Will call startWave(1) after reset — handled via waveNumber==0 check in update
                        dragPointerId = event.getPointerId(0)
                        lastTouchX = toVx(event.x); lastTouchY = toVy(event.y)
                    }
                    Phase.GAME_OVER -> phase = Phase.READY
                    Phase.PAUSED    -> phase = Phase.RUNNING
                    Phase.UPGRADE   -> handleUpgradeTap(toVx(event.x), toVy(event.y))
                    Phase.RUNNING, Phase.METEOR -> {
                        dragPointerId = event.getPointerId(0)
                        lastTouchX = toVx(event.x); lastTouchY = toVy(event.y)
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (phase == Phase.RUNNING || phase == Phase.METEOR) {
                    val idx = event.actionIndex
                    if (dragPointerId == -1) {
                        dragPointerId = event.getPointerId(idx)
                        lastTouchX = toVx(event.getX(idx)); lastTouchY = toVy(event.getY(idx))
                    } else {
                        // 2e doigt : double-tap < 500ms → nova, sinon → mag-field
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastSecondFingerTapMs) < 500L
                        lastSecondFingerTapMs = now
                        if (isDoubleTap && upgradeStacks[UPG_NOVA] > 0 && novaAvailable) {
                            launchNova()
                        } else if (!isDoubleTap && upgradeStacks[UPG_MAGNET] > 0 && !magnetActive && magnetCooldown <= 0f) {
                            magnetActive = true
                            magnetTimer = magnetDuration()
                            magnetCooldown = MAGNET_COOLDOWN_MAX
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if ((phase == Phase.RUNNING || phase == Phase.METEOR) && dragPointerId != -1) {
                    val idx = event.findPointerIndex(dragPointerId)
                    if (idx != -1) {
                        val vx = toVx(event.getX(idx)); val vy = toVy(event.getY(idx))
                        val mx = vx - lastTouchX
                        val my = vy - lastTouchY
                        playerX = (playerX + mx).coerceIn(PLAYER_SIZE / 2f, VW - PLAYER_SIZE / 2f)
                        playerY = (playerY + my).coerceIn(PLAYER_AREA_TOP, VH - PLAYER_SIZE / 2f)
                        lastTouchX = vx; lastTouchY = vy
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragPointerId = -1
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == dragPointerId) dragPointerId = -1
            }
        }
        return true
    }

    private fun handleUpgradeTap(vx: Float, vy: Float) {
        if (upgradeChoices.isEmpty()) return
        val cardW = VW * 0.86f
        val cardH = 88f
        val cardX = (VW - cardW) / 2f
        val cardYs = floatArrayOf(VH * 0.30f, VH * 0.48f, VH * 0.66f)
        upgradeChoices.forEachIndexed { i, upg ->
            val cy = cardYs.getOrElse(i) { return }
            if (vx >= cardX && vx <= cardX + cardW && vy >= cy && vy <= cy + cardH) {
                applyUpgrade(upg)
            }
        }
    }

    private fun toVx(sx: Float) = (sx - offX) / scaleX
    private fun toVy(sy: Float) = (sy - offY) / scaleY

    // ── Render ────────────────────────────────────────────────────────────────
    private fun drawFrame(canvas: Canvas) {
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scaleX, scaleY)

        drawBackground(canvas)
        if (phase == Phase.METEOR) {
            drawMeteors(canvas)
            drawPlayerBullets(canvas)
            drawDrones(canvas)
            drawPlayer(canvas)
            drawMeteorHud(canvas)
        } else {
            drawPlayerBullets(canvas)
            drawEnemyBullets(canvas)
            drawRockets(canvas)
            drawDrones(canvas)
            drawEnemies(canvas)
            drawPlayer(canvas)
            drawScorePopups(canvas)
            drawHud(canvas)
            if (waveAnnounceTimer > 0f && (phase == Phase.RUNNING || phase == Phase.WAVE_CLEAR)) {
                drawWaveAnnounce(canvas)
            }
            when (phase) {
                Phase.READY     -> drawOverlay(canvas, s(R.string.sw_title), s(R.string.sw_start_hint))
                Phase.PAUSED    -> drawOverlay(canvas, s(R.string.sw_paused), s(R.string.sw_tap_resume))
                Phase.GAME_OVER -> drawGameOver(canvas)
                Phase.UPGRADE   -> drawUpgradeScreen(canvas)
                Phase.WAVE_CLEAR -> {
                    val alpha = ((waveClearTimer / WAVE_CLEAR_DELAY) * 200).toInt().coerceIn(0, 200)
                    val p = Paint(bodyPaint).apply { this.alpha = alpha; textSize = 30f; color = Color.parseColor("#88FFBB") }
                    canvas.drawText(s(R.string.sw_wave_cleared, waveNumber), VW / 2f, VH * 0.5f, p)
                }
                else -> {}
            }
        }

        canvas.restore()
    }

    private fun drawMeteors(canvas: Canvas) {
        val list = meteors.toList()
        val outerP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC554433") }
        val innerP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF332211") }
        val craterP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#88221100") }
        for (m in list) {
            canvas.drawCircle(m.x, m.y, m.radius, outerP)
            canvas.drawCircle(m.x, m.y, m.radius * 0.65f, innerP)
            canvas.drawCircle(m.x - m.radius * 0.25f, m.y - m.radius * 0.2f, m.radius * 0.22f, craterP)
        }
    }

    private fun drawMeteorHud(canvas: Canvas) {
        // Titre
        val titleP = Paint(waveAnnPaint).apply { textSize = 34f; alpha = 220 }
        canvas.drawText(s(R.string.sw_meteor_title), VW / 2f, 44f, titleP)
        // Barre de temps restant
        val ratio = (meteorPhaseTimer / METEOR_PHASE_DURATION).coerceIn(0f, 1f)
        val barW = VW * 0.7f; val barH = 6f
        val barX = (VW - barW) / 2f; val barY = 58f
        hpBarPaint.color = Color.parseColor("#33FFFFFF")
        canvas.drawRect(barX, barY, barX + barW, barY + barH, hpBarPaint)
        hpBarPaint.color = Color.parseColor("#FF88FFCC")
        canvas.drawRect(barX, barY, barX + barW * ratio, barY + barH, hpBarPaint)
        // Indicateur +2 upgrades
        val bonusP = Paint(hudSmall).apply { textAlign = Paint.Align.CENTER; color = Color.parseColor("#FFEE44"); alpha = 200 }
        canvas.drawText(s(R.string.sw_meteor_bonus), VW / 2f, VH - 16f, bonusP)
        // HP joueur
        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.color = Color.parseColor("#FF6688")
        canvas.drawText("♥".repeat(playerHp.coerceAtLeast(0)), VW - 12f, 32f, hudPaint)
        hudPaint.color = Color.WHITE
    }

    private fun drawDrones(canvas: Canvas) {
        if (drones.isEmpty()) return
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4444FFAA") }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC44FFAA") }
        for (drone in drones) {
            val dx = playerX + cos(drone.angle) * DRONE_ORBIT_R
            val dy = playerY + sin(drone.angle) * DRONE_ORBIT_R
            canvas.drawCircle(dx, dy, 9f, glow)
            canvas.drawCircle(dx, dy, 4.5f, core)
        }
    }

    private fun drawRockets(canvas: Canvas) {
        val list = rockets.toList()
        if (list.isEmpty()) return
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66FF6600") }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF8800") }
        for (r in list) {
            canvas.drawCircle(r.x, r.y, ROCKET_R * 2.2f, glow)
            canvas.drawCircle(r.x, r.y, ROCKET_R, core)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#06080f"))
        for (s in stars) {
            starPaint.color = Color.argb((s.alpha * 255f).toInt(), 255, 255, 255)
            canvas.drawCircle(s.x, s.y, s.radius, starPaint)
        }
    }

    private fun drawPlayerBullets(canvas: Canvas) {
        val bulletList = playerBullets.toList()
        val droneBulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#BB44FFAA") }
        for (b in bulletList) {
            if (b.isDrone) {
                canvas.drawCircle(b.x, b.y, 4f, droneBulletPaint)
            } else {
                pbPaint.alpha = 60
                canvas.drawRoundRect(b.x - PBULLET_W, b.y - PBULLET_H, b.x + PBULLET_W, b.y + PBULLET_H,
                    PBULLET_W, PBULLET_W, pbPaint)
                pbPaint.alpha = 255
                canvas.drawRoundRect(b.x - PBULLET_W / 2f, b.y - PBULLET_H / 2f,
                    b.x + PBULLET_W / 2f, b.y + PBULLET_H / 2f,
                    PBULLET_W / 2f, PBULLET_W / 2f, pbPaint)
            }
        }
    }

    private fun drawEnemyBullets(canvas: Canvas) {
        val bulletList = enemyBullets.toList()
        for (b in bulletList) {
            canvas.drawRoundRect(b.x - EBULLET_W / 2f, b.y - EBULLET_H / 2f,
                b.x + EBULLET_W / 2f, b.y + EBULLET_H / 2f,
                EBULLET_W / 2f, EBULLET_W / 2f, ebPaint)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        val enemyList = enemies.toList()
        for (enemy in enemyList) {
            val size = if (enemy.isBoss) BOSS_SIZE else enemySizes.getOrElse(enemy.typeIdx) { ENEMY_SIZE_DEFAULT }
            val half = size / 2f
            val bmp = if (enemy.isBoss) bossBmps[(enemy.typeIdx - 8).coerceIn(0, 1)] else enemyBmps.getOrNull(enemy.typeIdx)
            if (bmp == null) {
                hpBarPaint.color = if (enemy.isBoss) Color.parseColor("#FF22AA") else Color.RED
                canvas.drawCircle(enemy.x, enemy.y, half, hpBarPaint)
            } else {
                canvas.drawBitmap(bmp, null,
                    RectF(enemy.x - half, enemy.y - half, enemy.x + half, enemy.y + half),
                    spritePaint)
            }
            if (enemy.isBoss) {
                val barW = size * 0.9f; val barH = 7f
                val barX = enemy.x - barW / 2f; val barY = enemy.y + half + 3f
                hpBarPaint.color = Color.parseColor("#33222222")
                canvas.drawRect(barX, barY, barX + barW, barY + barH, hpBarPaint)
                val ratio = enemy.hp.toFloat() / enemy.maxHp.toFloat()
                hpBarPaint.color = if (enemy.isBoss) Color.parseColor("#FF4488") else Color.parseColor("#44FF66")
                canvas.drawRect(barX, barY, barX + barW * ratio, barY + barH, hpBarPaint)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        if (!playerVisible) return
        // Flash nova
        if (novaFlashTimer > 0f) {
            val ratio = novaFlashTimer / 0.45f
            hpBarPaint.color = Color.argb((ratio * 140).toInt(), 255, 238, 80)
            canvas.drawCircle(playerX, playerY, NOVA_RADIUS * (1f - ratio * 0.25f), hpBarPaint)
        }
        // Aura magnétique
        if (magnetActive) {
            val ratio = (magnetTimer / magnetDuration()).coerceIn(0f, 1f)
            val outerAlpha = (ratio * 55 + 25).toInt()
            hpBarPaint.color = Color.argb(outerAlpha, 180, 100, 255)
            canvas.drawCircle(playerX, playerY, MAGNET_RADIUS, hpBarPaint)
            hpBarPaint.color = Color.argb((outerAlpha * 0.6f).toInt(), 140, 70, 255)
            canvas.drawCircle(playerX, playerY, MAGNET_RADIUS * 0.55f, hpBarPaint)
        }
        // Shield glow
        if (shieldCharges > 0) {
            hpBarPaint.color = Color.parseColor("#5500CCFF")
            canvas.drawCircle(playerX, playerY, PLAYER_SIZE * 0.7f, hpBarPaint)
        }
        val bmp = playerBmp
        val half = PLAYER_SIZE / 2f
        if (bmp == null) {
            hpBarPaint.color = Color.parseColor("#00CCFF"); canvas.drawCircle(playerX, playerY, half, hpBarPaint)
        } else {
            canvas.drawBitmap(bmp, null,
                RectF(playerX - half, playerY - half, playerX + half, playerY + half), spritePaint)
        }
    }

    private fun drawScorePopups(canvas: Canvas) {
        val list = scorePopups.toList()
        for (p in list) {
            val a = (p.life / 0.9f * 255f).toInt().coerceIn(0, 255)
            popupPaint.alpha = a
            if (p.isGold) popupPaint.color = Color.parseColor("#FFD700") else popupPaint.color = Color.parseColor("#AAFFCC")
            canvas.drawText(p.text, p.x, p.y, popupPaint)
        }
        popupPaint.alpha = 255
    }

    private fun drawHud(canvas: Canvas) {
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("$score", 12f, 32f, hudPaint)

        hudPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(s(R.string.sw_hud_wave, waveNumber), VW / 2f, 32f, hudPaint)

        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.color = Color.parseColor("#FF6688")
        canvas.drawText("♥".repeat(playerHp.coerceAtLeast(0)), VW - 12f, 32f, hudPaint)
        hudPaint.color = Color.WHITE

        if (shieldCharges > 0) {
            hudSmall.textAlign = Paint.Align.RIGHT
            hudSmall.color = Color.parseColor("#00CED1")
            canvas.drawText("◆".repeat(shieldCharges), VW - 12f, 52f, hudSmall)
        }

        if (activeUpgrades.isNotEmpty()) {
            upgHudPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(activeUpgrades.joinToString("  "), 12f, VH - 10f, upgHudPaint)
        }

        // Indicateur du champ magnétique et nova
        if (upgradeStacks[UPG_MAGNET] > 0 || upgradeStacks[UPG_NOVA] > 0) {
            val parts = mutableListOf<Pair<String, Int>>()
            if (upgradeStacks[UPG_MAGNET] > 0) {
                val magText = when {
                    magnetActive        -> "MAG ${ceil(magnetTimer).toInt()}s"
                    magnetCooldown > 0f -> "MAG ${ceil(magnetCooldown).toInt()}s"
                    else                -> "MAG ●"
                }
                val magColor = when {
                    magnetActive        -> Color.parseColor("#CC88FF")
                    magnetCooldown > 0f -> Color.parseColor("#664488")
                    else                -> Color.parseColor("#AA66FF")
                }
                parts += Pair(magText, magColor)
            }
            if (upgradeStacks[UPG_NOVA] > 0) {
                val novaText = if (novaAvailable) "NOVA ●" else "NOVA ✗"
                val novaColor = if (novaAvailable) Color.parseColor("#FFEE44") else Color.parseColor("#665500")
                parts += Pair(novaText, novaColor)
            }
            var rx = VW - 12f
            upgHudPaint.textAlign = Paint.Align.RIGHT
            for ((text, color) in parts.reversed()) {
                upgHudPaint.color = color
                canvas.drawText(text, rx, VH - 10f, upgHudPaint)
                rx -= upgHudPaint.measureText(text) + 14f
            }
            upgHudPaint.color = Color.parseColor("#88AABB")
        }
    }

    private fun drawWaveAnnounce(canvas: Canvas) {
        val ratio = waveAnnounceTimer / WAVE_ANNOUNCE_DURATION
        val alpha = when {
            ratio > 0.7f -> 255
            else         -> ((ratio / 0.7f) * 255).toInt()
        }
        waveAnnPaint.alpha = alpha
        val isBoss = waveNumber % 5 == 0
        val text = if (isBoss) s(R.string.sw_boss_wave_announce) else s(R.string.sw_wave_announce, waveNumber)
        canvas.drawText(text, VW / 2f, VH * 0.42f, waveAnnPaint)
    }

    private fun drawUpgradeScreen(canvas: Canvas) {
        canvas.drawRect(0f, 0f, VW, VH, overlayBgPaint)
        titlePaint.textSize = 28f
        canvas.drawText(s(R.string.sw_choose_upgrade), VW / 2f, VH * 0.20f, titlePaint)
        titlePaint.textSize = 40f

        val cardW = VW * 0.86f
        val cardH = 88f
        val cardX = (VW - cardW) / 2f
        val cardYs = floatArrayOf(VH * 0.30f, VH * 0.48f, VH * 0.66f)
        val cornerR = 12f

        upgradeChoices.forEachIndexed { i, upg ->
            val cy = cardYs.getOrElse(i) { return@forEachIndexed }
            // Card background
            val cardRect = RectF(cardX, cy, cardX + cardW, cy + cardH)
            canvas.drawRoundRect(cardRect, cornerR, cornerR, cardBgPaint)
            canvas.drawRoundRect(cardRect, cornerR, cornerR, cardBorderPaint)
            // Color accent bar on left
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(upg.hexColor); alpha = 200
            }
            val accentRect = RectF(cardX, cy, cardX + 8f, cy + cardH)
            canvas.drawRoundRect(accentRect, cornerR, cornerR, accentPaint)
            canvas.drawRect(accentRect.right - cornerR, cy, accentRect.right, cy + cardH, accentPaint)
            // Text
            val tx = cardX + 22f
            canvas.drawText(upg.name, tx, cy + 34f, cardTitlePaint)
            canvas.drawText(upg.desc, tx, cy + 58f, cardDescPaint)
            // Stack indicator
            val stacks = upgradeStacks[upg.id]
            if (stacks > 0 && upg.id != UPG_SHIELD) {
                val stkPaint = Paint(cardDescPaint).apply { color = Color.parseColor(upg.hexColor); textAlign = Paint.Align.RIGHT }
                canvas.drawText("[$stacks/${upg.maxStack}]", cardX + cardW - 12f, cy + 34f, stkPaint)
            }
        }

        val hintP = Paint(hudSmall).apply { textAlign = Paint.Align.CENTER; alpha = 160 }
        canvas.drawText(s(R.string.sw_best_hint, bestScore, bestWave), VW / 2f, VH * 0.88f, hintP)
    }

    private fun drawOverlay(canvas: Canvas, title: String, msg: String) {
        canvas.drawRect(0f, 0f, VW, VH, overlayBgPaint)
        canvas.drawText(title, VW / 2f, VH * 0.38f, titlePaint)
        var y = VH * 0.52f
        for (line in msg.split("\n")) { canvas.drawText(line, VW / 2f, y, bodyPaint); y += 36f }
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, VW, VH, overlayBgPaint)
        canvas.drawText(s(R.string.sw_game_over), VW / 2f, VH * 0.32f, titlePaint)
        val lines = mutableListOf<String>()
        lines += s(R.string.sw_score, score)
        lines += s(R.string.sw_reached_wave, waveNumber)
        if (newBestScore) lines += s(R.string.sw_new_best_score)
        if (newBestWave)  lines += s(R.string.sw_new_best_wave)
        lines += ""
        lines += s(R.string.sw_best_summary, bestScore, bestWave)
        lines += ""
        lines += s(R.string.sw_tap_play_again)
        var y = VH * 0.47f
        for (line in lines) { canvas.drawText(line, VW / 2f, y, bodyPaint); y += 32f }
    }
}
