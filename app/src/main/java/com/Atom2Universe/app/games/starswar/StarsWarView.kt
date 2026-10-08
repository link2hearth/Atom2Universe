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
        var hitFlash: Float = 0f,
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
    private data class Rocket(var x: Float, var y: Float, var angle: Float = 0f)
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
        val maxStack: Int
    )

    // ── Enemy definitions (7 silhouettes permanentes) ───────────────────────────────
    // Ordre partagé avec SpaceFightArt : éclaireur, rapide, artilleur, blindé, percuteur, prisme, porteur
    private val enemyDefs = arrayOf(
        EnemyDef(hp = 1, speed = 75f,  score = 50,  path = PATH_LINE),                                   // 0 drone
        EnemyDef(hp = 1, speed = 155f, score = 70,  path = PATH_SIN),                                    // 1 fast
        EnemyDef(hp = 2, speed = 65f,  score = 100, canShoot = true, fireRate = 2.0f, path = PATH_LINE), // 2 gunner
        EnemyDef(hp = 4, speed = 45f,  score = 160, canShoot = true, fireRate = 3.5f, path = PATH_LINE), // 3 tank
        EnemyDef(hp = 1, speed = 140f, score = 80,  path = PATH_SIN),                                    // 4 kamikaze
        EnemyDef(hp = 2, speed = 60f,  score = 110, canShoot = true, fireRate = 1.5f, path = PATH_LINE), // 5 sniper
        EnemyDef(hp = 2, speed = 55f,  score = 90,  canShoot = true, fireRate = 2.5f, path = PATH_SIN)   // 6 carrier
    )
    // Dimensions logiques conservées pour le dessin et les collisions
    private val enemySizes = floatArrayOf(46f, 46f, 46f, 54f, 44f, 46f, 50f)

    // ── Upgrade pool ──────────────────────────────────────────────────────────
    private val upgradePool by lazy { listOf(
        UpgradeData(UPG_RAPID_FIRE,  s(R.string.sw_upg_rapid_fire_name),   s(R.string.sw_upg_rapid_fire_desc), 99),
        UpgradeData(UPG_MULTI_SHOT,  s(R.string.sw_upg_multi_shot_name),   s(R.string.sw_upg_multi_shot_desc),  3),
        UpgradeData(UPG_POWER_SHOT,  s(R.string.sw_upg_power_shot_name),   s(R.string.sw_upg_power_shot_desc), 99),
        UpgradeData(UPG_SHIELD,      s(R.string.sw_upg_shield_name),       s(R.string.sw_upg_shield_desc),  3),
        UpgradeData(UPG_HEAL,        s(R.string.sw_upg_heal_name),         s(R.string.sw_upg_heal_desc), 99),
        UpgradeData(UPG_MAX_HP_RARE, s(R.string.sw_upg_max_hp_name),       s(R.string.sw_upg_max_hp_desc),  3),
        UpgradeData(UPG_PIERCE,      s(R.string.sw_upg_pierce_name),       s(R.string.sw_upg_pierce_desc),  3),
        UpgradeData(UPG_ROCKET,      s(R.string.sw_upg_rocket_name),       s(R.string.sw_upg_rocket_desc),  1),
        UpgradeData(UPG_ROCKET_RATE, s(R.string.sw_upg_rocket_rate_name),  s(R.string.sw_upg_rocket_rate_desc), 99),
        UpgradeData(UPG_ROCKET_DMG,  s(R.string.sw_upg_rocket_dmg_name),   s(R.string.sw_upg_rocket_dmg_desc), 99),
        UpgradeData(UPG_MAGNET,      s(R.string.sw_upg_magnet_name),       s(R.string.sw_upg_magnet_desc),  1),
        UpgradeData(UPG_MAGNET_DUR,  s(R.string.sw_upg_magnet_dur_name),   s(R.string.sw_upg_magnet_dur_desc),  4),
        UpgradeData(UPG_DRONE,       s(R.string.sw_upg_drone_name),        s(R.string.sw_upg_drone_desc),  3),
        UpgradeData(UPG_VAMPIRE,     s(R.string.sw_upg_vampire_name),      s(R.string.sw_upg_vampire_desc),  1),
        UpgradeData(UPG_NOVA,        s(R.string.sw_upg_nova_name),         s(R.string.sw_upg_nova_desc),  1)
    ) }

    // ── Phase & threading ─────────────────────────────────────────────────────
    @Volatile private var phase = Phase.READY
    @Volatile private var pendingReset = false

    /**
     * Les gestes qui **changent l'état du jeu**, notés par l'interface et joués par le
     * fil de jeu.
     *
     * `pendingReset` suivait déjà ce principe ; deux chemins l'oubliaient. `launchNova`
     * vide la liste des tirs ennemis et `handleUpgradeTap` applique une amélioration,
     * tous deux appelés depuis `onTouchEvent`, donc sur le fil de l'interface, pendant
     * que le fil de jeu parcourt ces mêmes listes. Le même défaut a fait tomber
     * FlappyCat une fois la cadence rendue au jeu.
     *
     * Le second doigt ne transmet que son **intention** — simple ou double appui — et
     * non sa conséquence : c'est au fil de jeu de décider si la nova est disponible ou
     * si le champ magnétique est encore en attente, parce que lui seul lit cet état
     * sans risquer de le lire à moitié écrit.
     */
    @Volatile private var pendingUpgradeTap = false
    @Volatile private var pendingUpgradeX = 0f
    @Volatile private var pendingUpgradeY = 0f
    @Volatile private var pendingSecondFinger = 0
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
    private var lastSecondFingerTapMs = 0L

    // Perfect cycle & meteor phase
    private var perfectCycle = true
    private var meteorPhaseTimer = 0f
    private var meteorSpawnTimer = 0f
    private var meteorSessionCount = 0  // nb de phases météore déclenchées dans cette partie
    private var pendingUpgradePicks = 1

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

    private val art = SpaceFightArt()
    private var playerBank = 0f
    private var previousPlayerX = VW / 2f
    private var phaseBeforePause = Phase.RUNNING
    @Volatile private var pendingPauseToggle = false
    @Volatile private var pendingPower = 0

    // Seuls des états modifiés sont envoyés à l'interface Android, sur son fil.
    var onControlsChanged: ((Int, Int, Boolean, Boolean) -> Unit)? = null
    private var previousControls = Int.MIN_VALUE

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

    private val uiPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardBounds = Array(3) { i -> RectF(28f, 216f + i * 126f, 452f, 324f + i * 126f) }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun s(id: Int) = context.getString(id)
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    // ── Prefs ─────────────────────────────────────────────────────────────────
    private val prefs by lazy { context.getSharedPreferences("stars_war_save", Context.MODE_PRIVATE) }

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        holder.addCallback(this)
        isFocusable = true
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
    val isAudioPaused: Boolean get() = phase == Phase.PAUSED

    fun pause() {
        if (phase == Phase.RUNNING || phase == Phase.METEOR) {
            phaseBeforePause = phase
            phase = Phase.PAUSED
        }
        running = false
        joinThread()
    }

    fun resume() {
        running = true
        if (gameThread?.isAlive != true) {
            gameThread = Thread(this, "StarsWarThread").apply { start() }
        }
    }

    fun togglePause() { pendingPauseToggle = true }
    fun requestMagnet() { pendingPower = 1 }
    fun requestNova() { pendingPower = 2 }

    private fun applyPauseToggle() {
        if (phase == Phase.PAUSED) phase = phaseBeforePause
        else if (phase == Phase.RUNNING || phase == Phase.METEOR) {
            phaseBeforePause = phase
            phase = Phase.PAUSED
        }
    }

    private fun usePower(nova: Boolean) {
        if (phase != Phase.RUNNING) return
        if (nova) {
            if (upgradeStacks[UPG_NOVA] > 0 && novaAvailable) launchNova()
        } else if (upgradeStacks[UPG_MAGNET] > 0 && !magnetActive && magnetCooldown <= 0f) {
            magnetActive = true
            magnetTimer = magnetDuration()
            magnetCooldown = MAGNET_COOLDOWN_MAX
        }
    }

    private fun publishControls() {
        // -2 : non acquis ; -1 : prêt ; > 0 : recharge ; < -2 : champ actif.
        val mag = when {
            upgradeStacks[UPG_MAGNET] == 0 -> -2
            magnetActive -> -3 - ceil(magnetTimer).toInt()
            magnetCooldown > 0f -> ceil(magnetCooldown).toInt()
            else -> -1
        }
        val nova = if (upgradeStacks[UPG_NOVA] == 0) -2 else if (novaAvailable) -1 else 0
        val active = phase == Phase.RUNNING
        val paused = phase == Phase.PAUSED
        val signature = (mag + 20) * 100 + (nova + 2) * 10 + (if (active) 1 else 0) + (if (paused) 2 else 0)
        if (signature != previousControls) {
            previousControls = signature
            onControlsChanged?.invoke(mag, nova, active, paused)
        }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    override fun run() {
        var lastNano = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNano = now

            // Les gestes notés par l'interface se jouent ici, et **hors** du `when`
            // ci-dessous : le choix d'amélioration se fait en phase UPGRADE, où aucun
            // `update` ne tourne. Voir [pendingUpgradeTap].
            if (pendingUpgradeTap) {
                pendingUpgradeTap = false
                if (phase == Phase.UPGRADE) handleUpgradeTap(pendingUpgradeX, pendingUpgradeY)
            }
            if (pendingPauseToggle) { pendingPauseToggle = false; applyPauseToggle() }
            if (pendingPower != 0) {
                val action = pendingPower; pendingPower = 0
                usePower(action == 2)
            }
            if (pendingSecondFinger != 0) {
                val action = pendingSecondFinger; pendingSecondFinger = 0
                usePower(action == 2)
            }
            if (phase != Phase.PAUSED) {
                art.update(dt)
                val targetBank = ((playerX - previousPlayerX) * 2.5f).coerceIn(-14f, 14f)
                playerBank += (targetBank - playerBank) * (dt * 9f).coerceAtMost(1f)
                previousPlayerX = playerX
                if (phase == Phase.RUNNING || phase == Phase.METEOR) art.trail(dt, playerX, playerY)
            }

            when (phase) {
                Phase.RUNNING    -> update(dt)
                Phase.WAVE_CLEAR -> updateWaveClear(dt)
                Phase.METEOR     -> updateMeteorPhase(dt)
                else             -> {}
            }

            // Une toile nulle veut dire que la surface n'est pas prête. On attend quand
            // même : le `continue` d'avant sautait le sommeil, et la boucle occupait un
            // cœur à tourner à vide le temps d'un changement d'écran.
            publishControls()
            val c = lockFrame()
            if (c != null) {
                try { drawFrame(c) } finally { holder.unlockCanvasAndPost(c) }
            }

            val remainMs = 16L - (System.nanoTime() - now) / 1_000_000L
            if (remainMs > 0) Thread.sleep(remainMs)
        }
    }

    // ── Wave lifecycle ────────────────────────────────────────────────────────
    private fun startWave(n: Int) {
        // Chaque secteur change d'ambiance, les personnages gardent leur identité.
        if ((n - 1) % 5 == 0) perfectCycle = true
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
        if (upg.id == UPG_HEAL || upg.id == UPG_MAX_HP_RARE) art.repair(playerX, playerY)
        pendingUpgradePicks--
        showNextUpgradeOrWave()
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
            enemy.hitFlash = (enemy.hitFlash - dt).coerceAtLeast(0f)
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
                    enemy.hitFlash = .16f
                    art.hit(pb.x, pb.y)
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
                    s(R.string.sw_gain, enemyDefs.getOrNull(enemy.typeIdx)?.score ?: (500 + waveNumber * 50)),
                    enemy.isBoss
                )
                waveEnemiesKilled++
                dead += enemy
                art.burst(enemy.x, enemy.y, if (enemy.isBoss) SpaceFightArt.GOLD else SpaceFightArt.LILAC, enemy.isBoss)
                if (enemy.isBoss) onBossDestroyed?.invoke() else onEnemyDestroyed?.invoke()
                recoverEnergy()
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
                    rocket.angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
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
                        enemy.hitFlash = .16f
                        art.burst(rocket.x, rocket.y, SpaceFightArt.GOLD)
                        enemy.hp -= bulletDmg * (2 + upgradeStacks[UPG_ROCKET_DMG])
                        if (enemy.hp <= 0) {
                            val pts = enemyDefs.getOrNull(enemy.typeIdx)?.score ?: if (enemy.isBoss) 500 + waveNumber * 50 else 80
                            score += pts
                            scorePopups += ScorePopup(enemy.x, enemy.y, 0.9f, s(R.string.sw_gain, pts), enemy.isBoss)
                            waveEnemiesKilled++; dead += enemy
                            art.burst(enemy.x, enemy.y, if (enemy.isBoss) SpaceFightArt.GOLD else SpaceFightArt.LILAC, enemy.isBoss)
                            if (enemy.isBoss) onBossDestroyed?.invoke() else onEnemyDestroyed?.invoke()
                            recoverEnergy()
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

        // Un impact fatal prime sur la transition de vague.
        if (phase != Phase.RUNNING) return
        // Wave complete?
        if (waveEnemiesSpawned >= waveEnemyCount && enemies.isEmpty() && enemyBullets.isEmpty()) {
            if (waveNumber % 5 == 0 && perfectCycle) {
                startMeteorPhase()
            } else {
                pendingUpgradePicks = 1
                waveClearTimer = WAVE_CLEAR_DELAY
                phase = Phase.WAVE_CLEAR
            }
        }
    }

    private fun recoverEnergy() {
        if (upgradeStacks[UPG_VAMPIRE] > 0 && Random.nextFloat() < .05f && playerHp < playerMaxHp) {
            playerHp++
            art.repair(playerX, playerY)
        }
    }

    private fun fireBullets() {
        val spacing = 18f
        for (i in 0 until bulletCount) {
            val offset = (i - (bulletCount - 1) / 2f) * spacing
            playerBullets += Bullet(playerX + offset, playerY - PLAYER_SIZE / 2f, pierceCount)
        }
        art.fire()
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
                art.playerHit(playerX, playerY, true)
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
        art.nova(playerX, playerY)
        val novaR2 = NOVA_RADIUS * NOVA_RADIUS
        enemyBullets.removeAll { b ->
            val dx = b.x - playerX; val dy = b.y - playerY
            val erased = dx * dx + dy * dy <= novaR2
            if (erased) art.hit(b.x, b.y)
            erased
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
        if (playerDamageTimer > 0f || phase != Phase.RUNNING) return
        art.playerHit(playerX, playerY, shieldCharges > 0)
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
        novaAvailable = false; lastSecondFingerTapMs = 0L
        perfectCycle = true; meteorPhaseTimer = 0f; meteorSpawnTimer = 0f; meteorSessionCount = 0; pendingUpgradePicks = 1
        drones.clear(); meteors.clear()
        upgradeStacks.fill(0)
        enemies.clear(); playerBullets.clear(); enemyBullets.clear(); rockets.clear(); scorePopups.clear()
        spawnQueue.clear(); convoyTimer = 0f; enemyShotCounter = 0
        score = 0; elapsed = 0f; newBestScore = false; newBestWave = false
        waveNumber = 0; upgradeChoices = emptyList()
        art.clear(); playerBank = 0f; previousPlayerX = playerX
        pendingPower = 0; pendingSecondFinger = 0
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (phase) {
                    Phase.READY, Phase.GAME_OVER -> {
                        pendingReset = true; phase = Phase.RUNNING
                        // Will call startWave(1) after reset — handled via waveNumber==0 check in update
                        dragPointerId = event.getPointerId(0)
                        lastTouchX = toVx(event.x); lastTouchY = toVy(event.y)
                    }
                    Phase.PAUSED    -> pendingPauseToggle = true
                    Phase.UPGRADE   -> {
                        // On note le point touché, le fil de jeu appliquera le choix.
                        pendingUpgradeX = toVx(event.x); pendingUpgradeY = toVy(event.y)
                        pendingUpgradeTap = true
                    }
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
                        // 2e doigt : double-tap < 500ms → nova, sinon → mag-field.
                        // On ne transmet que l'intention ; l'éligibilité et l'effet sont
                        // décidés par le fil de jeu, seul à lire cet état sans risque.
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastSecondFingerTapMs) < 500L
                        lastSecondFingerTapMs = now
                        pendingSecondFinger = if (isDoubleTap) 2 else 1
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
        upgradeChoices.forEachIndexed { i, upg ->
            if (cardBounds[i].contains(vx, vy)) {
                applyUpgrade(upg)
                return
            }
        }
    }

    private fun toVx(sx: Float) = (sx - offX) / scaleX
    private fun toVy(sy: Float) = (sy - offY) / scaleY

    // ── Rendu Space Fight : coordonnées communes au dessin et aux zones tactiles ──
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 18f,
                     color: Int = SpaceFightArt.IVORY, align: Paint.Align = Paint.Align.LEFT,
                     maxWidth: Float = VW - 48f) {
        uiPaint.shader = null
        uiPaint.color = color
        uiPaint.alpha = Color.alpha(color)
        uiPaint.textAlign = align
        uiPaint.textSize = size
        val measured = uiPaint.measureText(value)
        if (measured > maxWidth) uiPaint.textSize = (size * maxWidth / measured).coerceAtLeast(11f)
        // Les noms traduits très longs sont coupés proprement, les descriptions sont
        // réparties sur plusieurs lignes par wrappedText.
        val fitted = if (uiPaint.measureText(value) <= maxWidth) value else
            android.text.TextUtils.ellipsize(value, uiPaint, maxWidth, android.text.TextUtils.TruncateAt.END)
        c.drawText(fitted.toString(), x, y, uiPaint)
    }

    private fun wrappedText(c: Canvas, value: String, x: Float, y: Float, width: Float,
                            size: Float = 16f, color: Int = SpaceFightArt.MUTED,
                            centered: Boolean = false, maxLines: Int = 4) {
        var baseline = y
        var lines = 0
        for (paragraph in value.split('\n')) {
            if (paragraph.isEmpty()) { baseline += size * .6f; continue }
            var rest = paragraph
            while (rest.isNotEmpty() && lines < maxLines) {
                uiPaint.textSize = size
                var count = uiPaint.breakText(rest, true, width, null).coerceAtLeast(1)
                if (count < rest.length) {
                    val space = rest.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space
                }
                val line = if (lines == maxLines - 1) rest else rest.substring(0, count)
                text(c, line, x, baseline, size, color,
                    if (centered) Paint.Align.CENTER else Paint.Align.LEFT, width)
                rest = rest.substring(count).trimStart()
                baseline += size * 1.4f
                lines++
            }
        }
    }

    private fun sectorName(): String = s(when (((waveNumber.coerceAtLeast(1) - 1) / 5) % 3) {
        0 -> R.string.sw_sector_helium
        1 -> R.string.sw_sector_ion
        else -> R.string.sw_sector_ice
    })

    private fun upgradeColor(id: Int): Int = when (id) {
        UPG_SHIELD, UPG_HEAL, UPG_MAX_HP_RARE, UPG_DRONE -> SpaceFightArt.MINT
        UPG_PIERCE, UPG_MAGNET, UPG_MAGNET_DUR, UPG_VAMPIRE -> SpaceFightArt.LILAC
        UPG_POWER_SHOT, UPG_ROCKET_DMG -> SpaceFightArt.CORAL
        else -> SpaceFightArt.GOLD
    }

    private fun drawFrame(c: Canvas) {
        c.drawColor(SpaceFightArt.INK)
        c.save()
        c.translate(offX, offY)
        c.scale(scaleX, scaleY)
        c.clipRect(0f, 0f, VW, VH)
        art.background(c, ((waveNumber.coerceAtLeast(1) - 1) / 5) % 3, phase == Phase.METEOR)
        for (star in stars) {
            val twinkle = .75f + sin(art.time * 1.2f + star.x) * .25f
            starPaint.color = SpaceFightArt.alpha(SpaceFightArt.MUTED, (star.alpha * twinkle * 155f).toInt())
            val y = (star.y + art.time * star.speed * .08f) % VH
            if (star.radius > 1.9f) art.sparkle(c, star.x, y, star.radius * 1.3f, starPaint.color)
            else c.drawCircle(star.x, y, star.radius * .65f, starPaint)
        }

        when (phase) {
            Phase.READY -> drawWelcome(c)
            Phase.UPGRADE -> drawUpgradeScreen(c)
            Phase.GAME_OVER -> drawGameOver(c)
            else -> {
                drawWorld(c)
                if (phase == Phase.METEOR || phase == Phase.PAUSED && phaseBeforePause == Phase.METEOR) drawMeteorHud(c)
                else drawHud(c)
                if (phase == Phase.PAUSED) drawPause(c)
                else if (phase == Phase.WAVE_CLEAR) {
                    text(c, s(R.string.sw_wave_cleared, waveNumber), VW / 2f, 335f, 25f, SpaceFightArt.MINT, Paint.Align.CENTER)
                } else if (waveAnnounceTimer > 0f && phase == Phase.RUNNING) drawWaveAnnounce(c)
            }
        }
        c.restore()
    }

    private fun drawWorld(c: Canvas) {
        for (m in meteors) art.meteor(c, m.x, m.y, m.radius, m.dx)
        if (magnetActive) art.magnetic(c, playerX, playerY, MAGNET_RADIUS, magnetTimer)
        for (b in playerBullets) art.photon(c, b.x, b.y, if (b.isDrone) b.dx else 0f,
            if (b.isDrone) b.dy else -1f, bulletDmg, b.pierceLeft > 0, b.isDrone)
        for (b in enemyBullets) art.hostileBullet(c, b.x, b.y, b.dx, b.dy, b.isMissile)
        for (r in rockets) art.rocket(c, r.x, r.y, r.angle)
        for (enemy in enemies) {
            val size = if (enemy.isBoss) BOSS_SIZE else enemySizes[enemy.typeIdx]
            val charge = if (enemy.canShoot) (1f - enemy.fireCooldown / .4f).coerceIn(0f, 1f) else 0f
            if (enemy.isBoss) {
                art.boss(c, enemy.typeIdx, enemy.x, enemy.y, size, charge, enemy.hitFlash)
                val left = (enemy.x - 48f).coerceIn(8f, VW - 104f)
                val top = enemy.y + size * .61f
                art.panel(c, left, top, left + 96f, top + 4f, SpaceFightArt.EDGE, 2f, Color.TRANSPARENT)
                val ratio = (enemy.hp.toFloat() / enemy.maxHp).coerceIn(0f, 1f)
                art.panel(c, left, top, left + 96f * ratio, top + 4f, SpaceFightArt.LILAC, 2f, Color.TRANSPARENT)
            } else art.enemy(c, enemy.typeIdx, enemy.x, enemy.y, size, enemy.elapsed, charge, enemy.hitFlash)
        }
        for (drone in drones) {
            val dx = playerX + cos(drone.angle) * DRONE_ORBIT_R
            val dy = playerY + sin(drone.angle) * DRONE_ORBIT_R
            art.satellite(c, dx, dy, 20f, drone.fireCooldown < .3f)
        }
        art.shield(c, playerX, playerY, shieldCharges)
        // Le scintillement laisse toujours la coque perceptible, sans flash plein écran.
        if (playerVisible) art.player(c, playerX, playerY, PLAYER_SIZE, playerBank, true)
        else art.ellipse(c, playerX, playerY, PLAYER_HITBOX_R, PLAYER_HITBOX_R, SpaceFightArt.alpha(SpaceFightArt.MINT, 60))
        art.repairHalo(c, playerX, playerY)
        art.effects(c)
        for (popup in scorePopups) text(c, popup.text, popup.x, popup.y, if (popup.isGold) 20f else 15f,
            SpaceFightArt.alpha(if (popup.isGold) SpaceFightArt.GOLD else SpaceFightArt.MINT,
                (popup.life / .9f * 230).toInt()), Paint.Align.CENTER)
    }

    private fun drawHull(c: Canvas) {
        text(c, s(R.string.sw_hull_label), VW - 18f, 18f, 11f, SpaceFightArt.MUTED, Paint.Align.RIGHT, 88f)
        for (i in 0 until playerMaxHp) {
            val left = VW - 28f - (playerMaxHp - 1 - i) * 13f
            art.panel(c, left, 25f, left + 9f, 38f,
                if (i < playerHp) SpaceFightArt.MINT else SpaceFightArt.EDGE, 3f, Color.TRANSPARENT)
        }
        repeat(shieldCharges) { art.ellipse(c, VW - 24f - it * 13f, 46f, 2.2f, 2.2f, SpaceFightArt.LILAC) }
    }

    private fun drawHud(c: Canvas) {
        art.panel(c, 0f, 0f, VW, 53f, SpaceFightArt.alpha(SpaceFightArt.INK, 215), 0f, Color.TRANSPARENT)
        text(c, s(R.string.sw_score_label), 18f, 18f, 11f, SpaceFightArt.MUTED, maxWidth = 112f)
        text(c, s(R.string.sw_number, score), 18f, 40f, 22f, maxWidth = 112f)
        text(c, sectorName(), VW / 2f, 18f, 11f, SpaceFightArt.MUTED, Paint.Align.CENTER, 205f)
        text(c, s(R.string.sw_hud_wave, waveNumber), VW / 2f, 40f, 18f, SpaceFightArt.IVORY, Paint.Align.CENTER, 180f)
        drawHull(c)
        var slot = 0
        for (upg in upgradePool) {
            val count = if (upg.id == UPG_SHIELD) shieldCharges else upgradeStacks[upg.id]
            if (count <= 0) continue
            val x = 22f + slot * 30f
            art.upgrade(c, upg.id, x, VH - 22f, 18f)
            text(c, s(R.string.sw_number, count), x, VH - 5f, 10f, SpaceFightArt.MUTED, Paint.Align.CENTER, 28f)
            slot++
        }
    }

    private fun drawMeteorHud(c: Canvas) {
        art.panel(c, 0f, 0f, VW, 71f, SpaceFightArt.alpha(SpaceFightArt.INK, 220), 0f, Color.TRANSPARENT)
        text(c, s(R.string.sw_meteor_title), 18f, 27f, 20f, SpaceFightArt.IVORY, maxWidth = 328f)
        text(c, s(R.string.sw_seconds, ceil(meteorPhaseTimer).toInt()), VW - 18f, 27f, 18f, SpaceFightArt.MINT, Paint.Align.RIGHT, 86f)
        val ratio = (meteorPhaseTimer / METEOR_PHASE_DURATION).coerceIn(0f, 1f)
        art.panel(c, 18f, 42f, VW - 18f, 47f, SpaceFightArt.EDGE, 2f, Color.TRANSPARENT)
        art.panel(c, 18f, 42f, 18f + (VW - 36f) * ratio, 47f, SpaceFightArt.MINT, 2f, Color.TRANSPARENT)
        text(c, s(R.string.sw_meteor_hint), VW / 2f, 65f, 12f, SpaceFightArt.MUTED, Paint.Align.CENTER)
        text(c, s(if (pendingUpgradePicks == 2) R.string.sw_meteor_bonus else R.string.sw_meteor_single),
            VW / 2f, VH - 18f, 17f, SpaceFightArt.GOLD, Paint.Align.CENTER)
    }

    private fun drawWaveAnnounce(c: Canvas) {
        val a = (waveAnnounceTimer / .5f * 230f).toInt().coerceIn(0, 230)
        val isBoss = waveNumber % 5 == 0
        val label = if (isBoss) s(if ((waveNumber / 5) % 2 == 1) R.string.sw_boss_rings else R.string.sw_boss_sun)
            else s(R.string.sw_wave_announce, waveNumber)
        art.panel(c, 64f, 254f, VW - 64f, 329f, SpaceFightArt.alpha(SpaceFightArt.PANEL, a), 20f, Color.TRANSPARENT)
        text(c, if (isBoss) s(R.string.sw_boss_wave_announce) else sectorName(), VW / 2f, 279f, 12f,
            SpaceFightArt.alpha(SpaceFightArt.MUTED, a), Paint.Align.CENTER, 330f)
        text(c, label, VW / 2f, 311f, 25f, SpaceFightArt.alpha(if (isBoss) SpaceFightArt.LILAC else SpaceFightArt.MINT, a), Paint.Align.CENTER, 320f)
    }

    private fun drawWelcome(c: Canvas) {
        text(c, s(R.string.sw_brand_name), VW / 2f, 94f, 45f, SpaceFightArt.IVORY, Paint.Align.CENTER)
        text(c, s(R.string.sw_subtitle), VW / 2f, 127f, 15f, SpaceFightArt.MINT, Paint.Align.CENTER)
        c.save(); c.rotate(-17f, 240f, 261f)
        art.arc(c, 240f, 261f, 105f, 50f, 0f, 360f, SpaceFightArt.alpha(SpaceFightArt.LILAC, 100))
        c.restore()
        val hover = sin(art.time * 1.5f) * 5f
        art.player(c, 240f, 253f + hover, 118f, sin(art.time * .7f) * 3f)
        art.satellite(c, 240f + cos(art.time * .6f) * 110f, 267f + sin(art.time * .6f) * 46f, 29f)
        text(c, s(R.string.sw_probe_label), VW / 2f, 355f, 14f, SpaceFightArt.MUTED, Paint.Align.CENTER)
        wrappedText(c, s(R.string.sw_start_hint), VW / 2f, 411f, 372f, 19f, centered = true)
        art.panel(c, 77f, 515f, 403f, 574f, SpaceFightArt.MINT, 20f, Color.TRANSPARENT)
        text(c, s(R.string.sw_launch), VW / 2f, 552f, 23f, SpaceFightArt.INK, Paint.Align.CENTER, 306f)
        wrappedText(c, s(R.string.sw_best_hint, bestScore, bestWave), VW / 2f, 617f, 395f, 15f, centered = true)
        text(c, s(R.string.sw_brand_line), VW / 2f, 691f, 11f, SpaceFightArt.MUTED, Paint.Align.CENTER)
    }

    private fun drawUpgradeScreen(c: Canvas) {
        text(c, s(R.string.sw_workshop), VW / 2f, 51f, 30f, SpaceFightArt.IVORY, Paint.Align.CENTER)
        art.player(c, 240f, 114f + sin(art.time * 2f) * 3f, 70f)
        text(c, s(R.string.sw_choose_upgrade), VW / 2f, 182f, 22f, SpaceFightArt.IVORY, Paint.Align.CENTER)
        text(c, s(R.string.sw_picks_remaining, pendingUpgradePicks), VW / 2f, 203f, 13f, SpaceFightArt.MUTED, Paint.Align.CENTER)
        upgradeChoices.forEachIndexed { i, upg ->
            val bounds = cardBounds[i]
            val accent = upgradeColor(upg.id)
            art.panel(c, bounds.left, bounds.top, bounds.right, bounds.bottom, SpaceFightArt.PANEL, 20f, SpaceFightArt.alpha(accent, 130))
            art.ellipse(c, bounds.left + 42f, bounds.centerY(), 29f, 29f, SpaceFightArt.alpha(accent, 15))
            art.upgrade(c, upg.id, bounds.left + 42f, bounds.centerY(), 45f)
            val tx = bounds.left + 82f
            text(c, s(R.string.sw_module_index, i + 1), tx, bounds.top + 20f, 11f, accent, maxWidth = 220f)
            text(c, upg.name, tx, bounds.top + 47f, 21f, maxWidth = 312f)
            wrappedText(c, upg.desc, tx, bounds.top + 70f, 312f, 15f, maxLines = 2)
            val stacks = if (upg.id == UPG_SHIELD) shieldCharges else upgradeStacks[upg.id]
            if (stacks > 0) text(c, s(R.string.sw_fraction, stacks, upg.maxStack), bounds.right - 16f,
                bounds.top + 20f, 12f, accent, Paint.Align.RIGHT, 90f)
        }
        wrappedText(c, s(R.string.sw_best_hint, bestScore, bestWave), VW / 2f, 631f, 390f, 14f, centered = true)
    }

    private fun drawPause(c: Canvas) {
        art.panel(c, 24f, 75f, VW - 24f, 654f, SpaceFightArt.PANEL, 25f)
        text(c, s(R.string.sw_paused), VW / 2f, 126f, 31f, SpaceFightArt.IVORY, Paint.Align.CENTER)
        art.player(c, VW / 2f, 190f, 68f)
        text(c, s(R.string.sw_modules_title), VW / 2f, 260f, 16f, SpaceFightArt.MINT, Paint.Align.CENTER)
        var index = 0
        for (upg in upgradePool) {
            val count = if (upg.id == UPG_SHIELD) shieldCharges else upgradeStacks[upg.id]
            if (count <= 0) continue
            val x = 47f + index % 2 * 202f
            val y = 293f + index / 2 * 29f
            art.upgrade(c, upg.id, x + 8f, y - 4f, 19f)
            text(c, s(R.string.sw_module_owned, upg.name, count), x + 27f, y, 13f, maxWidth = 165f)
            index++
        }
        if (index == 0) text(c, s(R.string.sw_modules_empty), VW / 2f, 314f, 16f, SpaceFightArt.MUTED, Paint.Align.CENTER, 360f)
        wrappedText(c, s(R.string.sw_power_gestures), VW / 2f, 548f, 370f, 14f, centered = true, maxLines = 3)
        art.panel(c, 78f, 593f, 402f, 637f, SpaceFightArt.MINT, 15f, Color.TRANSPARENT)
        text(c, s(R.string.sw_tap_resume), VW / 2f, 622f, 18f, SpaceFightArt.INK, Paint.Align.CENTER, 300f)
    }

    private fun drawGameOver(c: Canvas) {
        text(c, s(R.string.sw_game_over), VW / 2f, 99f, 34f, SpaceFightArt.IVORY, Paint.Align.CENTER)
        art.player(c, VW / 2f, 188f + sin(art.time) * 3f, 89f, -7f)
        art.panel(c, 58f, 271f, 422f, 470f, SpaceFightArt.PANEL, 24f)
        text(c, s(R.string.sw_score_label), VW / 2f, 307f, 13f, SpaceFightArt.MUTED, Paint.Align.CENTER)
        text(c, s(R.string.sw_number, score), VW / 2f, 359f, 43f, SpaceFightArt.MINT, Paint.Align.CENTER, 320f)
        text(c, s(R.string.sw_reached_wave, waveNumber), VW / 2f, 399f, 20f, SpaceFightArt.IVORY, Paint.Align.CENTER, 320f)
        if (newBestScore || newBestWave) text(c, s(if (newBestScore) R.string.sw_new_best_score else R.string.sw_new_best_wave),
            VW / 2f, 441f, 17f, SpaceFightArt.GOLD, Paint.Align.CENTER, 320f)
        wrappedText(c, s(R.string.sw_best_summary, bestScore, bestWave), VW / 2f, 505f, 382f, 15f, centered = true)
        art.panel(c, 77f, 577f, 403f, 635f, SpaceFightArt.MINT, 20f, Color.TRANSPARENT)
        text(c, s(R.string.sw_tap_play_again), VW / 2f, 613f, 20f, SpaceFightArt.INK, Paint.Align.CENTER, 304f)
    }
}
