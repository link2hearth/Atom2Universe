package com.Atom2Universe.app.games.survivor

import android.content.Context
import android.graphics.Color
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class GamePhase { MENU, WEAPON_SELECT, PLAYING, LEVEL_UP, PAUSED, GAME_OVER }

enum class WeaponType(val unlockLevel: Int) {
    PROJECTILE(1), LASER(3), ORBITAL(4), AURA(6), CHAIN_LIGHTNING(8), BOUNCING(10), BOMB(15)
}

enum class EnemyType { ZOMBIE, FAST, MINI_BOSS, ERRATIC, ORBITER, SHOOTER }

// ─── Game objects ─────────────────────────────────────────────────────────────

class SPlayer {
    var x = 0f; var y = 0f
    var hp = 100f; var maxHp = 100f
    var shield = 0f; var maxShield = 0f
    var xp = 0f; var xpToNext = 50f; var level = 1
    var speed = 180f
    var iframeCd = 0f
    var shieldRegenDelay = 0f
    var revivesLeft = 0
    var kills = 0
    var lifeStealCd = 0f
    val upgrades = mutableMapOf<String, Int>()
    val weapons = mutableListOf(WeaponType.PROJECTILE)
    val weaponCds = mutableMapOf<WeaponType, Float>()

    fun upg(id: String) = upgrades.getOrDefault(id, 0)
    fun hpRatio() = (hp / maxHp).coerceIn(0f, 1f)
    fun shieldRatio() = if (maxShield > 0f) (shield / maxShield).coerceIn(0f, 1f) else 0f
    fun xpRatio() = (xp / xpToNext).coerceIn(0f, 1f)
}

class SEnemy(
    var x: Float, var y: Float,
    var hp: Float, val maxHp: Float,
    val baseSpeed: Float, val damage: Float,
    val xpDrop: Float, val radius: Float,
    val type: EnemyType,
    var slowFactor: Float = 1f,
    var slowCd: Float = 0f,
    var orbitAngle: Float = 0f,
    var orbitRadius: Float = 0f,
    var erraticOffset: Float = 0f,
    var erraticCd: Float = 0f,
    var shootCd: Float = 2f,
    // DoT state
    var burnDmg: Float = 0f,
    var burnTimer: Float = 0f,
    var poisonDmg: Float = 0f,
    var poisonTimer: Float = 0f,
    var poisonTickCd: Float = 0f,
    // Formation
    var formation: SFormation? = null
) {
    val hpRatio get() = (hp / maxHp).coerceIn(0f, 1f)
}

class SEnemyBullet(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val damage: Float = 8f,
    val radius: Float = 5f,
    var lifetime: Float = 4f
)

class SProjectile(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val damage: Float, val radius: Float,
    var lifetime: Float, var penetration: Int,
    val isCrit: Boolean,
    val isFork: Boolean = false,
    val isOrbital: Boolean = false
)

class SLaser(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    var lifetime: Float,
    val damage: Float, val width: Float,
    val isCrit: Boolean
)

class SChainLightning(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    var lifetime: Float = 0.18f,
    val isCrit: Boolean
)

class SBouncingProj(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val damage: Float, val radius: Float,
    var bouncesLeft: Int, val isCrit: Boolean,
    val hitSet: MutableSet<SEnemy> = mutableSetOf()
)

class SBomb(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val damage: Float, val radius: Float,
    val explosionRadius: Float,
    var lifetime: Float, val isCrit: Boolean
)

data class FormationSlot(val enemy: SEnemy, val offX: Float, val offY: Float)

class SFormation(
    var worldX: Float,   // position absolue dans le monde
    var worldY: Float,
    var vx: Float,
    var vy: Float,
    val slots: MutableList<FormationSlot> = mutableListOf(),
    var entering: Boolean = true  // vrai tant que la formation n'a pas encore atteint l'aire de rebond
)

class SExplosion(
    val x: Float, val y: Float,
    val maxRadius: Float,
    var life: Float, val maxLife: Float
)

class SResidue(
    val x: Float, val y: Float,
    val radius: Float,
    val tickDamage: Float,
    var duration: Float,
    var tickTimer: Float = 0f
)

class SParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Int, var r: Float
)

class SDmgNum(
    var x: Float, var y: Float,
    var vy: Float = -60f,
    val text: String,
    var life: Float = 0.8f,
    val isCrit: Boolean
)

data class UpgradeOption(
    val id: String,
    val weaponType: WeaponType? = null,
    val labelRes: Int,
    val descRes: Int,
    val shortLabel: String,
    val maxLevel: Int,
    val cardColor: Int
)

// ─── Game ─────────────────────────────────────────────────────────────────────

class SurvivorGame(private val ctx: Context) {

    var phase = GamePhase.WEAPON_SELECT
    val player = SPlayer()
    val enemies = mutableListOf<SEnemy>()
    val enemyBullets = mutableListOf<SEnemyBullet>()
    val projectiles = mutableListOf<SProjectile>()
    val lasers = mutableListOf<SLaser>()
    val chainLightnings = mutableListOf<SChainLightning>()
    val bouncingProjs = mutableListOf<SBouncingProj>()
    val bombs = mutableListOf<SBomb>()
    val explosions = mutableListOf<SExplosion>()
    val residues = mutableListOf<SResidue>()
    val particles = mutableListOf<SParticle>()
    val dmgNums = mutableListOf<SDmgNum>()
    // Positions des orbitales (x0,y0, x1,y1, ...) — FloatArray évite 20 Pair/frame à 60fps
    val orbXY = FloatArray(40)
    var orbitalCount = 0
    val formations = mutableListOf<SFormation>()

    var screenW = 1080f
    var screenH = 1920f

    var wave = 1
    var survivalTime = 0f
    var spawnCd = 0f
    var waveCd = WAVE_DUR
    var bossCd = BOSS_INTERVAL
    var auraCd = 0f
    var bossWarning = 0f

    var bestTime = 0f
    var bestKills = 0
    var pendingUpgrades: List<UpgradeOption>? = null
    var formationCd = 55f
    var pendingLevelUps = 0
    var resumeRampTimer = 0f
    var reviveFlashTimer = 0f

    // Suivi DPS joueur (fenêtre glissante de 5s)
    var playerDps = 0f
    private var dpsAccum = 0f
    private var dpsWindowCd = 5f

    private val rng = Random.Default
    private val orbitalFireCds    = FloatArray(20)
    private val orbitalContactCds = FloatArray(20)

    companion object {
        const val WAVE_DUR = 30f
        const val BOSS_INTERVAL = 90f
        const val SPAWN_DIST = 700f
        const val IFRAME = 0.5f
        const val PLAYER_R = 16f

        const val PROJ_DMG = 5f;    const val PROJ_RATE = 2f
        const val PROJ_SPEED = 300f; const val PROJ_R = 4f; const val PROJ_LIFE = 2f

        const val LASER_DMG = 5f;   const val LASER_RATE = 1f
        const val LASER_RANGE = 250f; const val LASER_W = 6f; const val LASER_DUR = 0.4f

        const val AURA_DMG = 3f;    const val AURA_R = 120f
        const val AURA_TICK = 2.5f; const val AURA_SLOW = 0.7f

        const val BOUNCE_DMG = 5f;   const val BOUNCE_RATE = 0.8f
        const val BOUNCE_SPEED = 220f; const val BOUNCE_MAX = 1; const val BOUNCE_R = 7f

        const val BOMB_DMG = 20f;   const val BOMB_RATE = 0.5f
        const val BOMB_SPEED = 120f; const val BOMB_EXPL_R = 70f
        const val BOMB_R = 8f;      const val BOMB_LIFE = 3f
        const val RESIDUE_BASE_DURATION = 2f
        const val RESIDUE_TICK_INTERVAL = 0.5f
        const val RESIDUE_TICK_FRACTION = 0.2f

        const val CHAIN_DMG = 5f;   const val CHAIN_RATE = 1.2f
        const val CHAIN_RANGE = 300f

        const val ORB_RADIUS = 60f;    const val ORB_SPEED = 2.5f
        const val ORB_PROJ_DMG = 2.5f; const val ORB_R = 10f
        const val ORB_FIRE_RATE = 1.5f; const val ORB_CONTACT_CD = 0.3f
        const val RESUME_RAMP_DURATION = 0.5f
        const val ORB_RADIUS_OUTER = 110f

        val COL_BOSS_DEATH    = Color.parseColor("#FF2200")
        val COL_FAST_DEATH    = Color.parseColor("#FF4400")
        val COL_ZOMBIE_DEATH  = Color.parseColor("#FF6600")
        val COL_BOMB_BURST    = Color.parseColor("#FF8800")
        val COL_ERRATIC_DEATH = Color.parseColor("#AA44FF")
        val COL_ORBITER_DEATH = Color.parseColor("#44FFDD")
        val COL_SHOOTER_DEATH = Color.parseColor("#FF1111")
        val COL_CHAIN         = Color.parseColor("#00CCFF")
        val COL_POISON        = Color.parseColor("#44FF88")
    }

    private val hsvBuf = floatArrayOf(0f, 1f, 1f)

    private val _killEnemyBullets = HashSet<SEnemyBullet>(16)
    private var chainReactionInProgress = false
    private val _killEnemies  = HashSet<SEnemy>(32)
    private val _killExplosion = HashSet<SEnemy>(16)
    private val _killProj     = HashSet<SProjectile>(32)
    private val _killBouncing = HashSet<SBouncingProj>(8)
    private val _killBombs    = HashSet<SBomb>(8)
    private val _killExpl     = HashSet<SExplosion>(8)
    private val _killResidue  = HashSet<SResidue>(8)
    private var separationFrame = 0
    private val enemySnapshot  = ArrayList<SEnemy>(128)
    private var lifeStealFactor = 0f

    private val catalog: List<UpgradeOption> by lazy {
        listOf(
            UpgradeOption("speed",                null,                    R.string.survivor_upg_speed,                    R.string.survivor_upg_speed_desc,                    "SPD", 10,         Color.parseColor("#4488FF")),
            UpgradeOption("maxHp",                null,                    R.string.survivor_upg_max_hp,                   R.string.survivor_upg_max_hp_desc,                   "HP+", Int.MAX_VALUE, Color.parseColor("#44FF88")),
            UpgradeOption("lifeSteal",            null,                    R.string.survivor_upg_life_steal,               R.string.survivor_upg_life_steal_desc,               "VMP", 10,         Color.parseColor("#FF4488")),
            UpgradeOption("regen",                null,                    R.string.survivor_upg_regen,                    R.string.survivor_upg_regen_desc,                    "REG", 15,         Color.parseColor("#44FFAA")),
            UpgradeOption("armor",                null,                    R.string.survivor_upg_armor,                    R.string.survivor_upg_armor_desc,                    "ARM", 10,         Color.parseColor("#AAAAFF")),
            UpgradeOption("crit",                 null,                    R.string.survivor_upg_crit,                     R.string.survivor_upg_crit_desc,                     "CRT", 10,         Color.parseColor("#FFFF44")),
            UpgradeOption("crit_mult",            null,                    R.string.survivor_upg_crit_mult,                R.string.survivor_upg_crit_mult_desc,                "C×+", Int.MAX_VALUE, Color.parseColor("#FFEE44")),
            UpgradeOption("revive",               null,                    R.string.survivor_upg_revive,                   R.string.survivor_upg_revive_desc,                   "REV", 3,          Color.parseColor("#FFFFFF")),
            UpgradeOption("xpBonus",              null,                    R.string.survivor_upg_xp_bonus,                 R.string.survivor_upg_xp_bonus_desc,                 "XP+", 20,         Color.parseColor("#AA44FF")),
            UpgradeOption("shield",               null,                    R.string.survivor_upg_shield,                   R.string.survivor_upg_shield_desc,                   "SHD", 10,         Color.parseColor("#44CCFF")),
            UpgradeOption("thorns",               null,                    R.string.survivor_upg_thorns,                   R.string.survivor_upg_thorns_desc,                   "THN", 10,         Color.parseColor("#88FF44")),
            UpgradeOption("explosion_on_kill",    null,                    R.string.survivor_upg_explosion_on_kill,        R.string.survivor_upg_explosion_on_kill_desc,        "EXP", 8,          Color.parseColor("#FF8800")),
            UpgradeOption("unlock_laser",         null,                    R.string.survivor_upg_unlock_laser,             R.string.survivor_upg_unlock_laser_desc,             "LZR", 1,          Color.parseColor("#FF3366")),
            UpgradeOption("unlock_orbital",       null,                    R.string.survivor_upg_unlock_orbital,           R.string.survivor_upg_unlock_orbital_desc,           "ORB", 1,          Color.parseColor("#AADDFF")),
            UpgradeOption("unlock_aura",          null,                    R.string.survivor_upg_unlock_aura,              R.string.survivor_upg_unlock_aura_desc,              "AUR", 1,          Color.parseColor("#4AF0FF")),
            UpgradeOption("unlock_chain_lightning",null,                   R.string.survivor_upg_unlock_chain_lightning,   R.string.survivor_upg_unlock_chain_lightning_desc,   "⚡LT", 1,          Color.parseColor("#00BBFF")),
            UpgradeOption("unlock_bouncing",      null,                    R.string.survivor_upg_unlock_bouncing,          R.string.survivor_upg_unlock_bouncing_desc,          "BNC", 1,          Color.parseColor("#FF8844")),
            UpgradeOption("unlock_bomb",          null,                    R.string.survivor_upg_unlock_bomb,              R.string.survivor_upg_unlock_bomb_desc,              "BMB", 1,          Color.parseColor("#FF8800")),
            UpgradeOption("proj_count",           WeaponType.PROJECTILE,   R.string.survivor_upg_proj_count,              R.string.survivor_upg_proj_count_desc,               "+1P", 10,         Color.parseColor("#88AAFF")),
            UpgradeOption("proj_dmg",             WeaponType.PROJECTILE,   R.string.survivor_upg_proj_dmg,                R.string.survivor_upg_proj_dmg_desc,                 "DMG", 20,         Color.parseColor("#88AAFF")),
            UpgradeOption("proj_rate",            WeaponType.PROJECTILE,   R.string.survivor_upg_proj_rate,               R.string.survivor_upg_proj_rate_desc,                "CDN", 15,         Color.parseColor("#88AAFF")),
            UpgradeOption("proj_speed",           WeaponType.PROJECTILE,   R.string.survivor_upg_proj_speed,              R.string.survivor_upg_proj_speed_desc,               "VIT", 10,         Color.parseColor("#88AAFF")),
            UpgradeOption("proj_pen",             WeaponType.PROJECTILE,   R.string.survivor_upg_proj_pen,                R.string.survivor_upg_proj_pen_desc,                 "PEN", 5,          Color.parseColor("#88AAFF")),
            UpgradeOption("proj_range",           WeaponType.PROJECTILE,   R.string.survivor_upg_proj_range,              R.string.survivor_upg_proj_range_desc,               "PRT", 8,          Color.parseColor("#88AAFF")),
            UpgradeOption("proj_fork",            WeaponType.PROJECTILE,   R.string.survivor_upg_proj_fork,               R.string.survivor_upg_proj_fork_desc,                "FRK", 5,          Color.parseColor("#88AAFF")),
            UpgradeOption("poison",               WeaponType.PROJECTILE,   R.string.survivor_upg_poison,                  R.string.survivor_upg_poison_desc,                   "PSN", 10,         Color.parseColor("#44FF88")),
            UpgradeOption("laser_range",          WeaponType.LASER,        R.string.survivor_upg_laser_range,             R.string.survivor_upg_laser_range_desc,              "PRT", 15,         Color.parseColor("#FF6688")),
            UpgradeOption("laser_dmg",            WeaponType.LASER,        R.string.survivor_upg_laser_dmg,               R.string.survivor_upg_laser_dmg_desc,                "DMG", 20,         Color.parseColor("#FF6688")),
            UpgradeOption("laser_burn",           WeaponType.LASER,        R.string.survivor_upg_laser_burn,              R.string.survivor_upg_laser_burn_desc,               "BRN", 10,         Color.parseColor("#FF6688")),
            UpgradeOption("laser_rate",           WeaponType.LASER,        R.string.survivor_upg_laser_rate,              R.string.survivor_upg_laser_rate_desc,               "CDN", 10,         Color.parseColor("#FF6688")),
            UpgradeOption("laser_multi",          WeaponType.LASER,        R.string.survivor_upg_laser_multi,             R.string.survivor_upg_laser_multi_desc,              "MLT", 3,          Color.parseColor("#FF6688")),
            UpgradeOption("aura_radius",          WeaponType.AURA,         R.string.survivor_upg_aura_radius,             R.string.survivor_upg_aura_radius_desc,              "RAY", 20,         Color.parseColor("#4AF0FF")),
            UpgradeOption("aura_dmg",             WeaponType.AURA,         R.string.survivor_upg_aura_dmg,                R.string.survivor_upg_aura_dmg_desc,                 "DMG", 20,         Color.parseColor("#4AF0FF")),
            UpgradeOption("aura_slow",            WeaponType.AURA,         R.string.survivor_upg_aura_slow,               R.string.survivor_upg_aura_slow_desc,                "SLO", 10,         Color.parseColor("#4AF0FF")),
            UpgradeOption("aura_tick",            WeaponType.AURA,         R.string.survivor_upg_aura_tick,               R.string.survivor_upg_aura_tick_desc,                "FRQ", 12,         Color.parseColor("#4AF0FF")),
            UpgradeOption("chain_dmg",            WeaponType.CHAIN_LIGHTNING, R.string.survivor_upg_chain_dmg,            R.string.survivor_upg_chain_dmg_desc,                "DMG", 20,         Color.parseColor("#00BBFF")),
            UpgradeOption("chain_bounces",        WeaponType.CHAIN_LIGHTNING, R.string.survivor_upg_chain_bounces,        R.string.survivor_upg_chain_bounces_desc,            "+CH", 8,          Color.parseColor("#00BBFF")),
            UpgradeOption("chain_rate",           WeaponType.CHAIN_LIGHTNING, R.string.survivor_upg_chain_rate,           R.string.survivor_upg_chain_rate_desc,               "CDN", 10,         Color.parseColor("#00BBFF")),
            UpgradeOption("chain_range",          WeaponType.CHAIN_LIGHTNING, R.string.survivor_upg_chain_range,          R.string.survivor_upg_chain_range_desc,              "PRT", 10,         Color.parseColor("#00BBFF")),
            UpgradeOption("chain_split",          WeaponType.CHAIN_LIGHTNING, R.string.survivor_upg_chain_split,          R.string.survivor_upg_chain_split_desc,              "SPL", 5,          Color.parseColor("#00EEFF")),
            UpgradeOption("orbital_count",        WeaponType.ORBITAL,      R.string.survivor_upg_orbital_count,           R.string.survivor_upg_orbital_count_desc,            "+OR", 18,         Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_dmg",          WeaponType.ORBITAL,      R.string.survivor_upg_orbital_dmg,             R.string.survivor_upg_orbital_dmg_desc,              "DMG", 20,         Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_speed",        WeaponType.ORBITAL,      R.string.survivor_upg_orbital_speed,           R.string.survivor_upg_orbital_speed_desc,            "VIT", 10,         Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_rate",         WeaponType.ORBITAL,      R.string.survivor_upg_orbital_rate,            R.string.survivor_upg_orbital_rate_desc,             "FRQ", 12,         Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_multi",        WeaponType.ORBITAL,      R.string.survivor_upg_orbital_multi,           R.string.survivor_upg_orbital_multi_desc,            "+1P", 5,          Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_frag",         WeaponType.ORBITAL,      R.string.survivor_upg_orbital_frag,            R.string.survivor_upg_orbital_frag_desc,             "FRK", 3,          Color.parseColor("#AADDFF")),
            UpgradeOption("orbital_poison",       WeaponType.ORBITAL,      R.string.survivor_upg_orbital_poison,          R.string.survivor_upg_orbital_poison_desc,           "PSN", 10,         Color.parseColor("#44FF88")),
            UpgradeOption("orbital_burn",         WeaponType.ORBITAL,      R.string.survivor_upg_orbital_burn,            R.string.survivor_upg_orbital_burn_desc,             "BRN", 10,         Color.parseColor("#FF6600")),
            UpgradeOption("bounce_count",         WeaponType.BOUNCING,     R.string.survivor_upg_bounce_count,            R.string.survivor_upg_bounce_count_desc,             "REB", 15,         Color.parseColor("#FF9944")),
            UpgradeOption("bounce_dmg",           WeaponType.BOUNCING,     R.string.survivor_upg_bounce_dmg,              R.string.survivor_upg_bounce_dmg_desc,               "DMG", 20,         Color.parseColor("#FF9944")),
            UpgradeOption("bounce_speed",         WeaponType.BOUNCING,     R.string.survivor_upg_bounce_speed,            R.string.survivor_upg_bounce_speed_desc,             "VIT", 10,         Color.parseColor("#FF9944")),
            UpgradeOption("bounce_rate",          WeaponType.BOUNCING,     R.string.survivor_upg_bounce_rate,             R.string.survivor_upg_bounce_rate_desc,              "CDN", 12,         Color.parseColor("#FF9944")),
            UpgradeOption("bomb_radius",          WeaponType.BOMB,         R.string.survivor_upg_bomb_radius,             R.string.survivor_upg_bomb_radius_desc,              "RAY", 15,         Color.parseColor("#FFAA00")),
            UpgradeOption("bomb_dmg",             WeaponType.BOMB,         R.string.survivor_upg_bomb_dmg,                R.string.survivor_upg_bomb_dmg_desc,                 "DMG", 20,         Color.parseColor("#FFAA00")),
            UpgradeOption("bomb_rate",            WeaponType.BOMB,         R.string.survivor_upg_bomb_rate,               R.string.survivor_upg_bomb_rate_desc,               "CDN", 10,         Color.parseColor("#FFAA00")),
            UpgradeOption("bomb_multi",           WeaponType.BOMB,         R.string.survivor_upg_bomb_multi,              R.string.survivor_upg_bomb_multi_desc,               "+1B", 4,          Color.parseColor("#FFAA00")),
            UpgradeOption("bomb_residue",         WeaponType.BOMB,         R.string.survivor_upg_bomb_residue,            R.string.survivor_upg_bomb_residue_desc,             "RSD", 1,          Color.parseColor("#CC6600")),
            UpgradeOption("bomb_residue_duration",WeaponType.BOMB,         R.string.survivor_upg_bomb_residue_duration,   R.string.survivor_upg_bomb_residue_duration_desc,    "DUR", 10,         Color.parseColor("#CC6600")),
        )
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun startGame(startWeapon: WeaponType = WeaponType.PROJECTILE) {
        phase = GamePhase.PLAYING
        with(player) {
            x = 0f; y = 0f
            hp = 100f; maxHp = 100f
            shield = 0f; maxShield = 0f
            xp = 0f; xpToNext = 50f; level = 1
            speed = 180f; iframeCd = 0f; shieldRegenDelay = 0f; lifeStealCd = 0f
            revivesLeft = 0; kills = 0
            upgrades.clear()
            weapons.clear(); weapons.add(startWeapon)
            weaponCds.clear()
        }
        lifeStealFactor = 0f
        enemies.clear(); enemyBullets.clear(); projectiles.clear(); lasers.clear()
        chainLightnings.clear(); bouncingProjs.clear(); bombs.clear(); explosions.clear(); residues.clear()
        particles.clear(); dmgNums.clear(); orbitalCount = 0
        orbitalFireCds.fill(0f); orbitalContactCds.fill(0f)
        formations.clear()
        formationCd = 55f
        playerDps = 0f; dpsAccum = 0f; dpsWindowCd = 5f
        wave = 1; survivalTime = 0f; spawnCd = 0f
        waveCd = WAVE_DUR; bossCd = BOSS_INTERVAL; auraCd = 0f; bossWarning = 0f
        pendingUpgrades = null; pendingLevelUps = 0; resumeRampTimer = 0f; reviveFlashTimer = 0f
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    fun update(dt: Float, jx: Float, jy: Float) {
        if (phase != GamePhase.PLAYING) return
        // Ralenti progressif après sélection d'upgrade (0 → 100% sur RESUME_RAMP_DURATION)
        val eff = if (resumeRampTimer > 0f) {
            val scale = 1f - (resumeRampTimer / RESUME_RAMP_DURATION)
            resumeRampTimer = (resumeRampTimer - dt).coerceAtLeast(0f)
            dt * scale
        } else dt

        survivalTime += eff
        if (bossWarning > 0f) bossWarning -= eff
        if (reviveFlashTimer > 0f) reviveFlashTimer -= eff

        updateDpsWindow(eff)
        movePlayer(eff, jx, jy)
        enemySnapshot.clear(); enemySnapshot.addAll(enemies)
        regenShield(eff)
        regenHp(eff)
        updateEnemies(eff)
        separateEnemies()
        updateFormations(eff)
        updateDoTs(eff)
        spawnEnemies(eff)
        updateWave(eff)
        fireWeapons(eff)
        fireAura(eff)
        updateOrbital(eff)
        updateProjectiles(eff)
        updateLasers(eff)
        updateChainLightnings(eff)
        updateBouncingProjs(eff)
        updateBombs(eff)
        updateExplosions(eff)
        updateResidues(eff)
        updateEnemyBullets(eff)
        updateParticles(eff)
        updateDmgNums(eff)
        checkPlayerCollisions()
    }

    // ─── Player ───────────────────────────────────────────────────────────────

    private fun movePlayer(dt: Float, jx: Float, jy: Float) {
        if (player.iframeCd > 0f) player.iframeCd -= dt
        if (player.lifeStealCd > 0f) player.lifeStealCd -= dt
        player.x += jx * player.speed * dt
        player.y += jy * player.speed * dt
    }

    private fun regenShield(dt: Float) {
        if (player.shieldRegenDelay > 0f) { player.shieldRegenDelay -= dt; return }
        if (player.shield < player.maxShield)
            player.shield = (player.shield + 5f * dt).coerceAtMost(player.maxShield)
    }

    private fun regenHp(dt: Float) {
        val rate = player.upg("regen").toFloat()
        if (rate > 0f && player.hp < player.maxHp)
            player.hp = (player.hp + rate * dt).coerceAtMost(player.maxHp)
    }

    // ─── Enemies ──────────────────────────────────────────────────────────────

    private fun updateEnemies(dt: Float) {
        val px = player.x; val py = player.y
        for (e in enemies) {
            if (e.formation != null) continue  // position gérée par la formation
            if (e.slowCd > 0f) { e.slowCd -= dt; if (e.slowCd <= 0f) e.slowFactor = 1f }
            when (e.type) {
                EnemyType.ZOMBIE, EnemyType.FAST, EnemyType.MINI_BOSS -> moveToward(e, px, py, dt)
                EnemyType.ERRATIC -> moveErratic(e, px, py, dt)
                EnemyType.ORBITER -> moveOrbiter(e, px, py, dt)
                EnemyType.SHOOTER -> moveShooter(e, px, py, dt)
            }
        }
    }

    // ─── Formations DVD ───────────────────────────────────────────────────────

    private fun updateFormations(dt: Float) {
        if (wave >= 3) {
            formationCd -= dt
            if (formationCd <= 0f) {
                formationCd = 65f + rng.nextFloat() * 40f
                val blockFormation = survivalTime < 900f && formations.isNotEmpty()
                if (!blockFormation) spawnFormation()
            }
        }
        val halfW = screenW / 2f
        val halfH = screenH / 2f
        val margin = 280f
        val iter = formations.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            if (f.slots.isEmpty()) { iter.remove(); continue }
            f.worldX += f.vx * dt
            f.worldY += f.vy * dt
            // Position relative à l'écran (pour le calcul des rebonds et la phase d'entrée)
            val scrX = f.worldX - player.x
            val scrY = f.worldY - player.y
            // Phase d'entrée : on attend que la formation soit dans l'aire de rebond avant d'activer les collisions de bord
            if (f.entering) {
                if (abs(scrX) <= halfW - margin && abs(scrY) <= halfH - margin) f.entering = false
            } else {
                // rebond DVD sur les bords de l'écran — recale en monde pour éviter les rebonds multiples
                if (scrX < -(halfW - margin)) { f.worldX = player.x - (halfW - margin); f.vx =  abs(f.vx) }
                if (scrX >  (halfW - margin)) { f.worldX = player.x + (halfW - margin); f.vx = -abs(f.vx) }
                if (scrY < -(halfH - margin)) { f.worldY = player.y - (halfH - margin); f.vy =  abs(f.vy) }
                if (scrY >  (halfH - margin)) { f.worldY = player.y + (halfH - margin); f.vy = -abs(f.vy) }
            }
            for (slot in f.slots) {
                slot.enemy.x = f.worldX + slot.offX
                slot.enemy.y = f.worldY + slot.offY
                if (slot.enemy.type == EnemyType.SHOOTER) {
                    val dx = player.x - slot.enemy.x; val dy = player.y - slot.enemy.y
                    val d = sqrt(dx * dx + dy * dy)
                    slot.enemy.shootCd -= dt
                    if (slot.enemy.shootCd <= 0f && d in 1f..520f) {
                        slot.enemy.shootCd = 1.8f + rng.nextFloat() * 0.8f
                        enemyBullets.add(SEnemyBullet(slot.enemy.x, slot.enemy.y, dx / d * 210f, dy / d * 210f))
                    }
                }
            }
        }
    }

    private fun spawnFormation() {
        val hpScale  = 1f + wave * 0.10f
        val total    = (20 + wave * 2).coerceIn(20, 50)
        val shooters = (2 + wave / 4).coerceIn(2, 6)
        val troops   = total - shooters

        // Position de départ hors-écran sur un bord aléatoire
        val halfW = screenW / 2f; val halfH = screenH / 2f
        val (startSX, startSY) = when (rng.nextInt(4)) {
            0 -> -(halfW + 220f) to (rng.nextFloat() * 2f - 1f) * halfH * 0.6f
            1 ->  (halfW + 220f) to (rng.nextFloat() * 2f - 1f) * halfH * 0.6f
            2 -> (rng.nextFloat() * 2f - 1f) * halfW * 0.6f to -(halfH + 220f)
            else -> (rng.nextFloat() * 2f - 1f) * halfW * 0.6f to  (halfH + 220f)
        }
        // Vélocité vers un point aléatoire à l'intérieur de l'écran (effet DVD dès l'entrée)
        val margin = 280f
        val targetSX = (rng.nextFloat() * 2f - 1f) * (halfW - margin) * 0.8f
        val targetSY = (rng.nextFloat() * 2f - 1f) * (halfH - margin) * 0.8f
        val angle = atan2(targetSY - startSY, targetSX - startSX)
        val speed = 105f + rng.nextFloat() * 55f
        val worldCx = player.x + startSX
        val worldCy = player.y + startSY
        val f = SFormation(worldCx, worldCy, cos(angle) * speed, sin(angle) * speed)

        // Tireurs au centre en cercle serré
        repeat(shooters) { i ->
            val a = i * 2f * PI.toFloat() / shooters
            val offX = cos(a) * 45f; val offY = sin(a) * 45f
            val e = SEnemy(worldCx + offX, worldCy + offY, 5f * hpScale, 5f * hpScale, 0f, 5f, 10f, 20f, EnemyType.SHOOTER)
            e.shootCd = 1f + rng.nextFloat() * 2f; e.formation = f
            f.slots.add(FormationSlot(e, offX, offY)); enemies.add(e)
        }

        // Troupes dans des anneaux concentriques
        var remaining = troops
        for (ringRadius in floatArrayOf(95f, 160f, 230f)) {
            if (remaining <= 0) break
            val capacity = (2f * PI.toFloat() * ringRadius / 33f).toInt().coerceAtMost(remaining)
            repeat(capacity) { i ->
                val a = i * 2f * PI.toFloat() / capacity + rng.nextFloat() * 0.08f
                val offX = cos(a) * ringRadius; val offY = sin(a) * ringRadius
                val isFast = rng.nextFloat() < 0.25f
                val e = if (isFast)
                    SEnemy(worldCx + offX, worldCy + offY, 4f * hpScale, 4f * hpScale, 0f, 5f, 4f, 11f, EnemyType.FAST)
                else
                    SEnemy(worldCx + offX, worldCy + offY, 8f * hpScale, 8f * hpScale, 0f, 10f, 6f, 14f, EnemyType.ZOMBIE)
                e.formation = f
                f.slots.add(FormationSlot(e, offX, offY)); enemies.add(e)
            }
            remaining -= capacity
        }
        formations.add(f)
    }

    private fun separateEnemies() {
        if (enemies.size > 100) return
        if (++separationFrame % 3 != 0) return
        val list = enemies
        val n = list.size
        for (i in 0 until n) {
            val a = list[i]
            if (a.formation != null) continue
            for (j in i + 1 until n) {
                val b = list[j]
                if (b.formation != null) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val minDist = a.radius + b.radius + 1f
                val distSq = dx * dx + dy * dy
                if (distSq < minDist * minDist && distSq > 0.0001f) {
                    val dist = sqrt(distSq)
                    val push = (minDist - dist) * 0.5f / dist
                    val px = dx * push; val py = dy * push
                    a.x -= px; a.y -= py
                    b.x += px; b.y += py
                }
            }
        }
    }

    private fun updateDoTs(dt: Float) {
        val toKill = _killEnemies.also { it.clear() }
        for (e in enemySnapshot) {
            if (e.hp <= 0f || e in toKill) continue
            if (e.burnTimer > 0f) {
                e.burnTimer -= dt
                if (hitEnemy(e, e.burnDmg * dt, false)) { toKill.add(e); continue }
            }
            if (e.poisonTimer > 0f) {
                e.poisonTimer -= dt
                e.poisonTickCd -= dt
                if (e.poisonTickCd <= 0f) {
                    e.poisonTickCd = 0.5f
                    if (hitEnemy(e, e.poisonDmg, false)) { toKill.add(e); continue }
                }
            } else {
                e.poisonDmg = 0f
            }
        }
        enemies.removeAll(toKill)
    }

    private fun moveToward(e: SEnemy, px: Float, py: Float, dt: Float) {
        val dx = px - e.x; val dy = py - e.y
        val d = sqrt(dx * dx + dy * dy)
        if (d > 0f) { val spd = e.baseSpeed * e.slowFactor; e.x += dx / d * spd * dt; e.y += dy / d * spd * dt }
    }

    private fun moveErratic(e: SEnemy, px: Float, py: Float, dt: Float) {
        e.erraticCd -= dt
        if (e.erraticCd <= 0f) {
            e.erraticOffset = (rng.nextFloat() - 0.5f) * PI.toFloat() * 1.4f
            e.erraticCd = 0.15f + rng.nextFloat() * 0.35f
        }
        val dx = px - e.x; val dy = py - e.y
        if (dx * dx + dy * dy > 0f) {
            val angle = atan2(dy, dx) + e.erraticOffset
            val spd = e.baseSpeed * e.slowFactor
            e.x += cos(angle) * spd * dt; e.y += sin(angle) * spd * dt
        }
    }

    private fun moveOrbiter(e: SEnemy, px: Float, py: Float, dt: Float) {
        e.orbitAngle += 1.8f * dt
        e.orbitRadius = (e.orbitRadius - 28f * dt).coerceAtLeast(0f)
        if (e.orbitRadius <= 0f) { moveToward(e, px, py, dt); return }
        val targetX = px + cos(e.orbitAngle) * e.orbitRadius
        val targetY = py + sin(e.orbitAngle) * e.orbitRadius
        val dx = targetX - e.x; val dy = targetY - e.y
        val d = sqrt(dx * dx + dy * dy)
        if (d > 0f) { val spd = e.baseSpeed * e.slowFactor; e.x += dx / d * spd * dt; e.y += dy / d * spd * dt }
    }

    private fun moveShooter(e: SEnemy, px: Float, py: Float, dt: Float) {
        val dx = px - e.x; val dy = py - e.y
        val d = sqrt(dx * dx + dy * dy)
        val preferred = 320f
        val spd = e.baseSpeed * e.slowFactor
        if (d > 0f) {
            when {
                d > preferred + 60f -> { e.x += dx / d * spd * dt; e.y += dy / d * spd * dt }
                d < preferred - 60f -> { e.x -= dx / d * spd * dt; e.y -= dy / d * spd * dt }
                else -> { e.x += (-dy / d) * spd * 0.5f * dt; e.y += (dx / d) * spd * 0.5f * dt }
            }
        }
        e.shootCd -= dt
        if (e.shootCd <= 0f && d in 1f..520f) {
            e.shootCd = 1.8f + rng.nextFloat() * 0.8f
            val bspd = 210f
            enemyBullets.add(SEnemyBullet(e.x, e.y, dx / d * bspd, dy / d * bspd))
        }
    }

    private fun updateEnemyBullets(dt: Float) {
        val dead = _killEnemyBullets.also { it.clear() }
        for (b in enemyBullets) {
            b.x += b.vx * dt; b.y += b.vy * dt; b.lifetime -= dt
            if (b.lifetime <= 0f) dead.add(b)
        }
        enemyBullets.removeAll(dead)
    }

    private fun spawnEnemies(dt: Float) {
        spawnCd -= dt
        if (spawnCd > 0f) return

        // Pression de spawn : peu d'ennemis ou DPS joueur élevé → spawn plus vite et en plus grand groupe
        val nonFormationEnemies = enemies.count { it.formation == null }
        val minTarget = (6 + wave * 2).coerceAtMost(28)
        val countPressure = if (nonFormationEnemies < minTarget)
            (minTarget.toFloat() / (nonFormationEnemies + 1f)).coerceAtMost(4f) else 1f
        val targetDps = 8f + wave * 5f
        val dpsPressure = if (playerDps > targetDps)
            (playerDps / targetDps).coerceAtMost(4f) else 1f
        val pressure = maxOf(countPressure, dpsPressure)

        val interval = (0.5f - wave * 0.02f).coerceAtLeast(0.1f) / pressure
        spawnCd = interval + rng.nextFloat() * 0.05f

        val type = pickEnemyType()
        doSpawn(type)

        val allowGroup = type != EnemyType.SHOOTER && type != EnemyType.ORBITER && type != EnemyType.MINI_BOSS
        val groupChance = (0.12f + (pressure - 1f) * 0.10f).coerceAtMost(0.50f)
        if (allowGroup && wave >= 2 && rng.nextFloat() < groupChance) {
            val n = (2 + rng.nextInt(wave.coerceAtMost(5))).coerceAtMost(8)
            repeat(n) { doSpawn(type) }
        }
    }

    private fun updateDpsWindow(dt: Float) {
        dpsWindowCd -= dt
        if (dpsWindowCd <= 0f) {
            playerDps = dpsAccum / 5f
            dpsAccum = 0f
            dpsWindowCd = 5f
        }
    }

    private fun pickEnemyType(): EnemyType {
        val r = rng.nextFloat()
        return when {
            wave >= 5 && r < 0.08f -> EnemyType.SHOOTER
            wave >= 4 && r < 0.18f -> EnemyType.ORBITER
            wave >= 3 && r < 0.30f -> EnemyType.ERRATIC
            wave >= 2 && r < (0.35f + wave * 0.02f).coerceAtMost(0.55f) -> EnemyType.FAST
            else -> EnemyType.ZOMBIE
        }
    }

    private fun spawnEdgePos(): Pair<Float, Float> {
        val margin = 80f
        val halfW = screenW / 2f + margin
        val halfH = screenH / 2f + margin
        return when (rng.nextInt(4)) {
            0 -> player.x + (rng.nextFloat() * 2f - 1f) * halfW to player.y - halfH
            1 -> player.x + (rng.nextFloat() * 2f - 1f) * halfW to player.y + halfH
            2 -> player.x - halfW to player.y + (rng.nextFloat() * 2f - 1f) * halfH
            else -> player.x + halfW to player.y + (rng.nextFloat() * 2f - 1f) * halfH
        }
    }

    private fun doSpawn(type: EnemyType) {
        val (ex, ey) = spawnEdgePos()
        val hpScale = 1f + wave * 0.10f
        val e = when (type) {
            EnemyType.ZOMBIE   -> SEnemy(ex, ey, 8f   * hpScale, 8f   * hpScale, 80f,  10f, 5f,   14f, type)
            EnemyType.FAST     -> SEnemy(ex, ey, 4f   * hpScale, 4f   * hpScale, 140f, 5f,  3f,   11f, type)
            EnemyType.MINI_BOSS-> SEnemy(ex, ey, 188f * hpScale, 188f * hpScale, 60f,  25f, 500f, 32f, type)
            EnemyType.ERRATIC  -> SEnemy(ex, ey, 6f   * hpScale, 6f   * hpScale, 105f, 8f,  4f,   13f, type)
            EnemyType.ORBITER  -> SEnemy(ex, ey, 16f  * hpScale, 16f  * hpScale, 95f,  15f, 10f,  18f, type)
            EnemyType.SHOOTER  -> SEnemy(ex, ey, 5f   * hpScale, 5f   * hpScale, 45f,  5f,  8f,   20f, type)
        }
        when (type) {
            EnemyType.ORBITER -> {
                val dx = ex - player.x; val dy = ey - player.y
                val spawnDist = sqrt(dx * dx + dy * dy)
                e.orbitAngle = atan2(dy, dx) + PI.toFloat()
                e.orbitRadius = spawnDist * 0.85f
            }
            EnemyType.ERRATIC -> e.erraticCd = rng.nextFloat() * 0.3f
            EnemyType.SHOOTER -> e.shootCd = 1f + rng.nextFloat() * 1.5f
            else -> {}
        }
        enemies.add(e)
    }

    private fun updateWave(dt: Float) {
        waveCd -= dt
        if (waveCd <= 0f) { wave++; waveCd = WAVE_DUR }
        bossCd -= dt
        if (bossCd <= 0f) {
            bossCd = BOSS_INTERVAL
            val bossAlive = survivalTime < 900f && enemies.any { it.type == EnemyType.MINI_BOSS }
            if (!bossAlive) { bossWarning = 3f; doSpawn(EnemyType.MINI_BOSS) }
        }
    }

    // ─── Weapons ──────────────────────────────────────────────────────────────

    private fun fireWeapons(dt: Float) {
        for (type in player.weapons) {
            if (type == WeaponType.AURA || type == WeaponType.ORBITAL) continue
            val cd = (player.weaponCds[type] ?: 0f) - dt
            player.weaponCds[type] = cd
            if (cd <= 0f && fireWeapon(type))
                player.weaponCds[type] = getInterval(type)
        }
    }

    private fun getInterval(t: WeaponType) = when (t) {
        WeaponType.PROJECTILE     -> 1f / (PROJ_RATE    * (1f + player.upg("proj_rate")   * 0.10f))
        WeaponType.LASER          -> 1f / (LASER_RATE   * (1f + player.upg("laser_rate")  * 0.15f))
        WeaponType.CHAIN_LIGHTNING-> 1f / (CHAIN_RATE   * (1f + player.upg("chain_rate")  * 0.10f))
        WeaponType.BOUNCING       -> 1f / (BOUNCE_RATE  * (1f + player.upg("bounce_rate") * 0.10f))
        WeaponType.BOMB           -> 1f / (BOMB_RATE    * (1f + player.upg("bomb_rate")   * 0.10f))
        else -> 1f
    }

    private fun fireWeapon(type: WeaponType): Boolean {
        if (enemies.isEmpty()) return false
        return when (type) {
            WeaponType.PROJECTILE      -> fireProjectile()
            WeaponType.LASER           -> fireLaser()
            WeaponType.CHAIN_LIGHTNING -> fireChainLightning()
            WeaponType.BOUNCING        -> fireBouncing()
            WeaponType.BOMB            -> fireBomb()
            else -> false
        }
    }

    private fun fireProjectile(): Boolean {
        val t = nearestEnemy() ?: return false
        val dx = t.x - player.x; val dy = t.y - player.y
        val d = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: return false
        val dmg = PROJ_DMG * (1f + player.upg("proj_dmg") * 0.30f)
        val spd = PROJ_SPEED * (1f + player.upg("proj_speed") * 0.15f)
        val r   = PROJ_R
        val lt  = PROJ_LIFE * (1f + player.upg("proj_range") * 0.20f)
        val pen = player.upg("proj_pen")
        val cnt = 1 + player.upg("proj_count")
        val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
        val nx = dx / d; val ny = dy / d
        // Décalage latéral — même direction pour tous, positions de départ côte à côte
        val perpX = -ny; val perpY = nx
        val spacing = 14f
        for (i in 0 until cnt) {
            val offset = if (cnt > 1) (i - (cnt - 1) / 2f) * spacing else 0f
            projectiles.add(SProjectile(player.x + perpX * offset, player.y + perpY * offset, nx * spd, ny * spd, fd, r, lt, pen, isCrit))
        }
        return true
    }

    private fun fireLaser(): Boolean {
        val range = LASER_RANGE * (1f + player.upg("laser_range") * 0.20f)
        val rangeSq = range * range
        val multi = 1 + player.upg("laser_multi")
        val distSq = { e: SEnemy -> (e.x - player.x).let { it * it } + (e.y - player.y).let { it * it } }
        val primary = if (multi == 1) enemies.minByOrNull(distSq) ?: return false
                      else enemies.sortedBy(distSq).firstOrNull() ?: return false
        val dx = primary.x - player.x; val dy = primary.y - player.y
        if (dx * dx + dy * dy > rangeSq) return false
        val dmg   = LASER_DMG * (1f + player.upg("laser_dmg") * 0.30f)
        val width = LASER_W
        val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
        val targets = if (multi == 1) listOf(primary) else enemies.sortedBy(distSq).take(multi)
        val toKill = _killEnemies.also { it.clear() }
        val burnLevel = player.upg("laser_burn")
        val burnDmg = if (burnLevel > 0) LASER_DMG * 0.5f * (1f + burnLevel * 0.30f) else 0f
        for (tgt in targets) {
            val tdx = tgt.x - player.x; val tdy = tgt.y - player.y
            val dist = sqrt(tdx * tdx + tdy * tdy)
            if (dist > range) continue
            val endX = player.x + tdx / dist * range
            val endY = player.y + tdy / dist * range
            val beamWidth = if (tgt == primary) width else width * 0.6f
            lasers.add(SLaser(player.x, player.y, endX, endY, LASER_DUR, fd, beamWidth, isCrit))
            for (e in enemySnapshot) {
                if (e.hp <= 0f || e in toKill) continue
                if (distToSeg(e.x, e.y, player.x, player.y, endX, endY) < e.radius + beamWidth / 2f) {
                    if (burnLevel > 0) { e.burnDmg = burnDmg; e.burnTimer = 2f }
                    if (hitEnemy(e, fd, isCrit)) toKill.add(e)
                }
            }
        }
        enemies.removeAll(toKill)
        return true
    }

    private fun fireChainLightning(): Boolean {
        if (enemies.isEmpty()) return false
        val dmg = CHAIN_DMG * (1f + player.upg("chain_dmg") * 0.30f)
        val chains = 2 + player.upg("chain_bounces")
        val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
        val toKill = _killEnemies.also { it.clear() }
        val hit = mutableSetOf<SEnemy>()
        val range = CHAIN_RANGE + player.upg("chain_range") * 50f
        val rangeSq = range * range
        val splitChance = player.upg("chain_split") * 0.10f

        // File de segments : Triple(fromX, fromY, sautsRestants)
        val pending = ArrayDeque<Triple<Float, Float, Int>>()
        pending.add(Triple(player.x, player.y, chains))

        while (pending.isNotEmpty()) {
            val (cx, cy, hops) = pending.removeFirst()
            val next = nearestInRange(cx, cy, rangeSq, hit, toKill) ?: continue
            hit.add(next)
            chainLightnings.add(SChainLightning(cx, cy, next.x, next.y, 0.18f, isCrit))
            if (hitEnemy(next, fd, isCrit)) toKill.add(next)
            if (hops > 0) {
                pending.add(Triple(next.x, next.y, hops - 1))
                if (splitChance > 0f && rng.nextFloat() < splitChance)
                    pending.add(Triple(next.x, next.y, hops - 1))
            }
        }
        enemies.removeAll(toKill)
        return true
    }

    private fun fireBouncing(): Boolean {
        val t = nearestEnemy() ?: return false
        val dx = t.x - player.x; val dy = t.y - player.y
        val d = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: return false
        val dmg  = BOUNCE_DMG  * (1f + player.upg("bounce_dmg") * 0.30f)
        val spd  = BOUNCE_SPEED* (1f + player.upg("bounce_speed") * 0.15f)
        val bnc  = BOUNCE_MAX  + player.upg("bounce_count")
        val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
        bouncingProjs.add(SBouncingProj(player.x, player.y, dx / d * spd, dy / d * spd, fd, BOUNCE_R, bnc, isCrit))
        return true
    }

    private fun fireBomb(): Boolean {
        val t = nearestEnemy() ?: return false
        val dx = t.x - player.x; val dy = t.y - player.y
        val d = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: return false
        val dmg  = BOMB_DMG   * (1f + player.upg("bomb_dmg") * 0.30f)
        val expl = BOMB_EXPL_R* (1f + player.upg("bomb_radius") * 0.10f)
        val cnt  = 1 + player.upg("bomb_multi")
        val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
        val nx = dx / d; val ny = dy / d
        repeat(cnt) {
            val sp = if (cnt > 1) (rng.nextFloat() - 0.5f) * 0.25f else 0f
            val c = cos(sp); val s = sin(sp)
            bombs.add(SBomb(player.x, player.y, (nx * c - ny * s) * BOMB_SPEED, (nx * s + ny * c) * BOMB_SPEED, fd, BOMB_R, expl, BOMB_LIFE, isCrit))
        }
        return true
    }

    // ─── Aura ─────────────────────────────────────────────────────────────────

    private fun fireAura(dt: Float) {
        if (!player.weapons.contains(WeaponType.AURA)) return
        val radius   = AURA_R    * (1f + player.upg("aura_radius") * 0.15f)
        val dmg      = AURA_DMG  * (1f + player.upg("aura_dmg") * 0.30f)
        val tickRate = AURA_TICK * (1f + player.upg("aura_tick") * 0.15f)
        val slowF    = (AURA_SLOW - player.upg("aura_slow") * 0.05f).coerceAtLeast(0.3f)
        auraCd -= dt
        if (auraCd > 0f) return
        auraCd = 1f / tickRate
        val toKill = _killEnemies.also { it.clear() }
        for (e in enemySnapshot) {
            if (e.hp <= 0f) continue
            val dx = e.x - player.x; val dy = e.y - player.y
            if (dx * dx + dy * dy <= (radius + e.radius) * (radius + e.radius)) {
                e.slowFactor = slowF; e.slowCd = 1f
                if (hitEnemy(e, dmg, false)) toKill.add(e)
            }
        }
        enemies.removeAll(toKill)
    }

    // ─── Orbital ──────────────────────────────────────────────────────────────

    private fun updateOrbital(dt: Float) {
        if (!player.weapons.contains(WeaponType.ORBITAL)) return
        orbitalCount = 0
        val count    = 2 + player.upg("orbital_count")
        val speed    = ORB_SPEED + player.upg("orbital_speed") * 0.3f
        val dmg      = ORB_PROJ_DMG * (1f + player.upg("orbital_dmg") * 0.30f)
        val fireRate = ORB_FIRE_RATE * (1f + player.upg("orbital_rate") * 0.15f)
        val multi    = 1 + player.upg("orbital_multi")
        val toKill   = _killEnemies.also { it.clear() }

        // Anneau intérieur : 8 orbes max à ORB_RADIUS
        // Anneau extérieur : 12 orbes max à ORB_RADIUS_OUTER (indices 8–19)
        val innerCount = count.coerceAtMost(8)
        val outerCount = (count - 8).coerceAtLeast(0)

        for (ring in 0..1) {
            val ringCount = if (ring == 0) innerCount else outerCount
            if (ringCount == 0) continue
            val orbRadius = if (ring == 0) ORB_RADIUS else ORB_RADIUS_OUTER
            val ringSpeed = if (ring == 0) speed else speed * 0.75f
            val cdOffset  = ring * 8

            for (j in 0 until ringCount) {
                val i = cdOffset + j
                val angle = survivalTime * ringSpeed + j * (2f * PI.toFloat() / ringCount)
                val ox = player.x + cos(angle) * orbRadius
                val oy = player.y + sin(angle) * orbRadius
                val oi = orbitalCount * 2; orbXY[oi] = ox; orbXY[oi + 1] = oy; orbitalCount++

                // Dégâts au contact
                orbitalContactCds[i] = (orbitalContactCds[i] - dt).coerceAtLeast(-1f)
                if (orbitalContactCds[i] <= 0f) {
                    for (e in enemySnapshot) {
                        if (e.hp <= 0f || e in toKill) continue
                        val cdx = e.x - ox; val cdy = e.y - oy
                        if (cdx * cdx + cdy * cdy < (e.radius + ORB_R) * (e.radius + ORB_R)) {
                            if (hitEnemy(e, dmg, false)) toKill.add(e)
                            orbitalContactCds[i] = ORB_CONTACT_CD
                            break
                        }
                    }
                }

                // Tir de projectile
                orbitalFireCds[i] = (orbitalFireCds[i] - dt).coerceAtLeast(-1f)
                if (orbitalFireCds[i] > 0f) continue
                val target = nearestEnemy() ?: continue
                orbitalFireCds[i] = 1f / fireRate
                val dx = target.x - ox; val dy = target.y - oy
                val d = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: continue
                val nx = dx / d; val ny = dy / d
                val isCrit = isCrit(); val fd = if (isCrit) dmg * critMult() else dmg
                val perpX = -ny; val perpY = nx
                val spacing = 12f
                for (k in 0 until multi) {
                    val offset = if (multi > 1) (k - (multi - 1) / 2f) * spacing else 0f
                    projectiles.add(SProjectile(ox + perpX * offset, oy + perpY * offset, nx * PROJ_SPEED, ny * PROJ_SPEED, fd, PROJ_R, PROJ_LIFE, 0, isCrit, false, true))
                }
            }
        }
        enemies.removeAll(toKill)
    }

    // ─── Projectile updates ───────────────────────────────────────────────────

    private fun updateProjectiles(dt: Float) {
        val dead   = _killProj.also { it.clear() }
        val toKill = _killEnemies.also { it.clear() }
        val poisonLevel = player.upg("poison")
        val poisonDmgBase = if (poisonLevel > 0) PROJ_DMG * 0.4f * (1f + poisonLevel * 0.30f) else 0f
        // Itération indexée jusqu'à la taille initiale : les forks ajoutés pendant la boucle sont ignorés (traités à la prochaine frame)
        val initialProjCount = projectiles.size
        for (pi in 0 until initialProjCount) {
            val p = projectiles[pi]
            p.x += p.vx * dt; p.y += p.vy * dt; p.lifetime -= dt
            if (p.lifetime <= 0f) { dead.add(p); continue }
            for (e in enemySnapshot) {
                if (e.hp <= 0f) continue
                if (e in toKill) continue
                val dx = e.x - p.x; val dy = e.y - p.y
                if (dx * dx + dy * dy < (e.radius + p.radius) * (e.radius + p.radius)) {
                    if (!p.isOrbital && poisonLevel > 0) {
                        e.poisonDmg = (e.poisonDmg + poisonDmgBase).coerceAtMost(poisonDmgBase * 3f)
                        e.poisonTimer = 3f
                        if (e.poisonTickCd <= 0f) e.poisonTickCd = 0.5f
                    }
                    if (p.isOrbital) {
                        val op = player.upg("orbital_poison")
                        if (op > 0) {
                            val pd = ORB_PROJ_DMG * 0.4f * (1f + op * 0.30f)
                            e.poisonDmg = (e.poisonDmg + pd).coerceAtMost(pd * 3f)
                            e.poisonTimer = 3f; if (e.poisonTickCd <= 0f) e.poisonTickCd = 0.5f
                        }
                        val ob = player.upg("orbital_burn")
                        if (ob > 0) { e.burnDmg = ORB_PROJ_DMG * 0.5f * (1f + ob * 0.30f); e.burnTimer = 2f }
                    }
                    if (hitEnemy(e, p.damage, p.isCrit)) toKill.add(e)
                    p.penetration--
                    if (p.penetration < 0) {
                        if (p.isOrbital) { if (player.upg("orbital_frag") > 0) spawnForkProjectiles(p) }
                        else if (!p.isFork && player.upg("proj_fork") > 0) spawnForkProjectiles(p)
                        dead.add(p); break
                    }
                }
            }
        }
        enemies.removeAll(toKill)
        projectiles.removeAll(dead)
    }

    private fun spawnForkProjectiles(p: SProjectile) {
        val spd = sqrt(p.vx * p.vx + p.vy * p.vy).takeIf { it > 0f } ?: return
        val nx = p.vx / spd; val ny = p.vy / spd
        val forkDmg = p.damage * 0.5f; val forkLt = 0.7f
        val c = cos(0.44f); val s = sin(0.44f)
        projectiles.add(SProjectile(p.x, p.y, (nx*c - ny*s)*spd,  (nx*s + ny*c)*spd,  forkDmg, PROJ_R*0.7f, forkLt, 0, p.isCrit, true))
        projectiles.add(SProjectile(p.x, p.y, (nx*c + ny*s)*spd, (-nx*s + ny*c)*spd, forkDmg, PROJ_R*0.7f, forkLt, 0, p.isCrit, true))
    }

    private fun updateLasers(dt: Float) {
        val iter = lasers.iterator()
        while (iter.hasNext()) { val l = iter.next(); l.lifetime -= dt; if (l.lifetime <= 0f) iter.remove() }
    }

    private fun updateChainLightnings(dt: Float) {
        val iter = chainLightnings.iterator()
        while (iter.hasNext()) { val c = iter.next(); c.lifetime -= dt; if (c.lifetime <= 0f) iter.remove() }
    }

    private fun updateBouncingProjs(dt: Float) {
        val dead   = _killBouncing.also { it.clear() }
        val toKill = _killEnemies.also { it.clear() }
        for (b in bouncingProjs) {
            b.x += b.vx * dt; b.y += b.vy * dt
            val dx = b.x - player.x; val dy = b.y - player.y
            if (dx * dx + dy * dy > 4f * SPAWN_DIST * SPAWN_DIST) { dead.add(b); continue }
            for (e in enemySnapshot) {
                if (e.hp <= 0f || e in b.hitSet || e in toKill) continue
                val ex = e.x - b.x; val ey = e.y - b.y
                if (ex * ex + ey * ey < (e.radius + b.radius) * (e.radius + b.radius)) {
                    b.hitSet.add(e)
                    if (hitEnemy(e, b.damage, b.isCrit)) toKill.add(e)
                    if (b.bouncesLeft > 0) {
                        b.bouncesLeft--
                        var bestNext: SEnemy? = null; var bestDist = Float.MAX_VALUE
                        for (ne in enemies) {
                            if (ne in b.hitSet || ne in toKill) continue
                            val dSq = (ne.x - b.x).let { it * it } + (ne.y - b.y).let { it * it }
                            if (dSq < bestDist) { bestNext = ne; bestDist = dSq }
                        }
                        if (bestNext != null) {
                            val nd = sqrt(bestDist)
                            val spd = sqrt(b.vx * b.vx + b.vy * b.vy)
                            b.vx = (bestNext.x - b.x) / nd * spd; b.vy = (bestNext.y - b.y) / nd * spd
                        } else dead.add(b)
                    } else dead.add(b)
                    break
                }
            }
        }
        enemies.removeAll(toKill)
        bouncingProjs.removeAll(dead)
    }

    private fun updateBombs(dt: Float) {
        val dead = _killBombs.also { it.clear() }
        for (b in bombs) {
            b.x += b.vx * dt; b.y += b.vy * dt; b.lifetime -= dt
            var explode = b.lifetime <= 0f
            if (!explode) {
                for (e in enemySnapshot) {
                    if (e.hp <= 0f) continue
                    val dx = e.x - b.x; val dy = e.y - b.y
                    if (dx * dx + dy * dy < (e.radius + b.radius) * (e.radius + b.radius)) { explode = true; break }
                }
            }
            if (explode) {
                applyExplosionDamage(b.x, b.y, b.explosionRadius, b.damage)
                explosions.add(SExplosion(b.x, b.y, b.explosionRadius, 0.5f, 0.5f))
                if (player.upg("bomb_residue") > 0) {
                    val dur = RESIDUE_BASE_DURATION + player.upg("bomb_residue_duration") * 0.5f
                    residues.add(SResidue(b.x, b.y, b.explosionRadius, b.damage * RESIDUE_TICK_FRACTION, dur))
                }
                spawnBurstParticles(b.x, b.y, COL_BOMB_BURST, 15)
                dead.add(b)
            }
        }
        bombs.removeAll(dead)
    }

    private fun applyExplosionDamage(x: Float, y: Float, radius: Float, damage: Float) {
        val toKill = _killExplosion.also { it.clear() }
        for (e in enemySnapshot) {
            if (e.hp <= 0f || e in toKill) continue
            val dx = e.x - x; val dy = e.y - y
            if (dx * dx + dy * dy < (radius + e.radius) * (radius + e.radius)) {
                if (hitEnemy(e, damage, false)) toKill.add(e)
            }
        }
        enemies.removeAll(toKill)
    }

    private fun updateExplosions(dt: Float) {
        val dead = _killExpl.also { it.clear() }
        for (ex in explosions) {
            ex.life -= dt
            if (ex.life <= 0f) dead.add(ex)
        }
        explosions.removeAll(dead)
    }

    private fun updateResidues(dt: Float) {
        val dead = _killResidue.also { it.clear() }
        for (res in residues) {
            res.duration -= dt
            if (res.duration <= 0f) { dead.add(res); continue }
            res.tickTimer -= dt
            if (res.tickTimer <= 0f) {
                res.tickTimer += RESIDUE_TICK_INTERVAL
                val toKill = _killEnemies.also { it.clear() }
                for (e in enemySnapshot) {
                    if (e.hp <= 0f || e in toKill) continue
                    val dx = e.x - res.x; val dy = e.y - res.y
                    if (dx * dx + dy * dy < (res.radius + e.radius) * (res.radius + e.radius)) {
                        if (hitEnemy(e, res.tickDamage, false)) toKill.add(e)
                    }
                }
                enemies.removeAll(toKill)
            }
        }
        residues.removeAll(dead)
    }

    // ─── Particles ────────────────────────────────────────────────────────────

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.92f; p.vy *= 0.92f; p.r *= 0.97f; p.life -= dt
            if (p.life <= 0f || p.r < 0.5f) iter.remove()
        }
    }

    private fun updateDmgNums(dt: Float) {
        val iter = dmgNums.iterator()
        while (iter.hasNext()) {
            val n = iter.next()
            n.y += n.vy * dt; n.life -= dt
            if (n.life <= 0f) iter.remove()
        }
    }

    // ─── Player collisions ────────────────────────────────────────────────────

    private fun checkPlayerCollisions() {
        if (player.iframeCd > 0f) return
        for (e in enemies) {
            val dx = e.x - player.x; val dy = e.y - player.y
            if (dx * dx + dy * dy < (PLAYER_R + e.radius) * (PLAYER_R + e.radius)) { damagePlayer(e.damage); return }
        }
        val bIter = enemyBullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            val dx = b.x - player.x; val dy = b.y - player.y
            if (dx * dx + dy * dy < (PLAYER_R + b.radius) * (PLAYER_R + b.radius)) {
                damagePlayer(b.damage); bIter.remove(); return
            }
        }
    }

    private fun damagePlayer(raw: Float) {
        val reduction = (player.upg("armor") * 0.05f).coerceAtMost(0.75f)
        val dmg = raw * (1f - reduction)
        player.shieldRegenDelay = 3f
        if (player.shield > 0f) {
            val absorbed = dmg.coerceAtMost(player.shield)
            player.shield -= absorbed
            val overflow = dmg - absorbed
            if (overflow > 0f) player.hp -= overflow
            val thornsLevel = player.upg("thorns")
            if (thornsLevel > 0 && absorbed > 0f) {
                val thornsDmg = absorbed * thornsLevel * 0.15f
                val toKill = _killEnemies.also { it.clear() }
                for (e in enemySnapshot) {
                    if (e.hp <= 0f) continue
                    val dx = e.x - player.x; val dy = e.y - player.y
                    if (dx * dx + dy * dy < 22500f) {
                        if (hitEnemy(e, thornsDmg, false)) toKill.add(e)
                    }
                }
                enemies.removeAll(toKill)
            }
        } else {
            player.hp -= dmg
        }
        player.iframeCd = IFRAME
        if (player.hp <= 0f) {
            if (player.revivesLeft > 0) { player.revivesLeft--; player.hp = player.maxHp * 0.3f; player.iframeCd = 2f; reviveFlashTimer = 2.5f }
            else { if (survivalTime > bestTime) bestTime = survivalTime; if (player.kills > bestKills) bestKills = player.kills; phase = GamePhase.GAME_OVER }
        }
    }

    // ─── Enemy hit ────────────────────────────────────────────────────────────

    private fun hitEnemy(e: SEnemy, dmg: Float, isCrit: Boolean): Boolean {
        e.hp -= dmg
        dpsAccum += dmg
        if (dmg >= 0.5f && dmgNums.size < 60)
            dmgNums.add(SDmgNum(e.x + rng.nextFloat() * 20f - 10f, e.y - e.radius, -60f, dmg.toInt().toString(), 0.8f, isCrit))
        val ls = lifeStealFactor
        if (ls > 0f && player.lifeStealCd <= 0f) {
            val heal = (dmg * ls).coerceAtMost(player.maxHp * 0.05f)
            if (heal >= 0.5f && player.hp < player.maxHp) {
                player.hp = (player.hp + heal).coerceAtMost(player.maxHp)
                player.lifeStealCd = 0.2f
            }
        }
        if (e.hp <= 0f) { onEnemyKill(e); return true }
        return false
    }

    private fun onEnemyKill(e: SEnemy) {
        e.formation?.slots?.removeAll { it.enemy === e }
        player.kills++
        val xp = e.xpDrop * (1f + player.upg("xpBonus") * 0.10f)
        addXp(xp)
        spawnDeathParticles(e)
        val explChance = player.upg("explosion_on_kill") * 0.12f
        if (!chainReactionInProgress && explChance > 0f && rng.nextFloat() < explChance) {
            val explDmg = e.maxHp * 0.4f
            chainReactionInProgress = true
            applyExplosionDamage(e.x, e.y, 90f, explDmg)
            chainReactionInProgress = false
            explosions.add(SExplosion(e.x, e.y, 90f, 0.4f, 0.4f))
            spawnBurstParticles(e.x, e.y, COL_BOMB_BURST, 8)
        }
    }

    // ─── XP / level-up ───────────────────────────────────────────────────────

    private fun addXp(amount: Float) {
        player.xp += amount
        while (player.xp >= player.xpToNext) {
            player.xp -= player.xpToNext
            player.level++
            player.xpToNext *= 1.15f
            pendingLevelUps++
        }
        if (phase == GamePhase.PLAYING && pendingLevelUps > 0) {
            pendingLevelUps--
            pendingUpgrades = buildChoices()
            phase = GamePhase.LEVEL_UP
        }
    }

    private fun buildChoices(): List<UpgradeOption> {
        val avail = catalog.filter { opt ->
            if (player.upg(opt.id) >= opt.maxLevel) return@filter false
            if (opt.weaponType != null && opt.weaponType !in player.weapons) return@filter false
            if (opt.id == "crit_mult" && player.upg("crit") == 0) return@filter false
            if (opt.id.startsWith("unlock_")) {
                val wt = unlockWeaponType(opt.id) ?: return@filter false
                if (wt in player.weapons) return@filter false
                if (player.level < wt.unlockLevel) return@filter false
                if (player.weapons.size >= 2) return@filter false
            }
            true
        }.toMutableList()
        avail.shuffle()
        return avail.take(3)
    }

    fun applyUpgrade(opt: UpgradeOption) {
        player.upgrades[opt.id] = player.upg(opt.id) + 1
        when (opt.id) {
            "maxHp"                -> { player.maxHp += 20f; player.hp = player.maxHp }
            "shield"               -> { player.maxShield += 25f; player.shield = player.maxShield }
            "revive"               -> player.revivesLeft++
            "lifeSteal"            -> lifeStealFactor = player.upg("lifeSteal") * 0.10f
            "speed"                -> player.speed = 180f + player.upg("speed") * 25f
            "unlock_laser"         -> player.weapons.add(WeaponType.LASER)
            "unlock_aura"          -> player.weapons.add(WeaponType.AURA)
            "unlock_bouncing"      -> player.weapons.add(WeaponType.BOUNCING)
            "unlock_bomb"          -> player.weapons.add(WeaponType.BOMB)
            "unlock_chain_lightning"-> player.weapons.add(WeaponType.CHAIN_LIGHTNING)
            "unlock_orbital"       -> player.weapons.add(WeaponType.ORBITAL)
        }
        pendingUpgrades = null
        if (pendingLevelUps > 0) {
            pendingLevelUps--
            pendingUpgrades = buildChoices()
            phase = GamePhase.LEVEL_UP
        } else {
            resumeRampTimer = RESUME_RAMP_DURATION
            phase = GamePhase.PLAYING
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    fun nearestEnemy() = enemies.minByOrNull { e -> (e.x - player.x).let { it * it } + (e.y - player.y).let { it * it } }

    private fun nearestInRange(cx: Float, cy: Float, rangeSq: Float, exclude1: Set<SEnemy>, exclude2: Set<SEnemy>): SEnemy? {
        var best: SEnemy? = null
        var bestDist = rangeSq
        for (e in enemies) {
            if (e in exclude1 || e in exclude2) continue
            val dx = e.x - cx; val dy = e.y - cy
            val dSq = dx * dx + dy * dy
            if (dSq < bestDist) { best = e; bestDist = dSq }
        }
        return best
    }

    fun auraRadius() = AURA_R * (1f + player.upg("aura_radius") * 0.15f)

    private fun isCrit() = rng.nextFloat() < player.upg("crit") * 0.05f

    private fun critMult() = 1.5f + player.upg("crit_mult") * 0.5f

    private fun distToSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax; val aby = by - ay
        val t = ((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby + 1e-6f)
        val cx = ax + t.coerceIn(0f, 1f) * abx; val cy = ay + t.coerceIn(0f, 1f) * aby
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun unlockWeaponType(id: String) = when (id) {
        "unlock_laser"          -> WeaponType.LASER
        "unlock_aura"           -> WeaponType.AURA
        "unlock_bouncing"       -> WeaponType.BOUNCING
        "unlock_bomb"           -> WeaponType.BOMB
        "unlock_chain_lightning"-> WeaponType.CHAIN_LIGHTNING
        "unlock_orbital"        -> WeaponType.ORBITAL
        else -> null
    }

    private fun spawnDeathParticles(e: SEnemy) {
        val (n, col) = when (e.type) {
            EnemyType.MINI_BOSS -> 10 to COL_BOSS_DEATH
            EnemyType.FAST      -> 3  to COL_FAST_DEATH
            EnemyType.ZOMBIE    -> 5  to COL_ZOMBIE_DEATH
            EnemyType.ERRATIC   -> 4  to COL_ERRATIC_DEATH
            EnemyType.ORBITER   -> 6  to COL_ORBITER_DEATH
            EnemyType.SHOOTER   -> 3  to COL_SHOOTER_DEATH
        }
        spawnBurstParticles(e.x, e.y, col, n, e.radius * 0.5f)
    }

    private fun spawnBurstParticles(x: Float, y: Float, color: Int, n: Int, baseR: Float = 5f) {
        val count = minOf(n, 150 - particles.size)
        if (count <= 0) return
        repeat(count) {
            val angle = rng.nextFloat() * 2f * PI.toFloat()
            val spd = 80f + rng.nextFloat() * 180f
            val life = 0.35f + rng.nextFloat() * 0.55f
            particles.add(SParticle(x, y, cos(angle) * spd, sin(angle) * spd, life, life, color, baseR + rng.nextFloat() * 4f))
        }
    }

    fun enemyColor(hpRatio: Float): Int {
        hsvBuf[0] = hpRatio * 60f; hsvBuf[1] = 1f; hsvBuf[2] = 1f
        return Color.HSVToColor(hsvBuf)
    }

    fun playerColor(hpRatio: Float): Int {
        hsvBuf[0] = hpRatio * 120f; hsvBuf[1] = 0.9f; hsvBuf[2] = 0.85f
        return Color.HSVToColor(hsvBuf)
    }
}
