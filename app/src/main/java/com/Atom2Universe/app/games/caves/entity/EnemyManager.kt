package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class EnemyManager(private val world: World, seed: Long = 0L) {

    val enemies = ArrayList<Enemy>(32)

    var playerHp = 20
    var playerMaxHp = 20
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)? = null

    var playerShieldMax     = 0
    var playerShieldCurrent = 0
    var shieldRechargeTimer = 0f
    var shieldCallback: ((current: Int, max: Int) -> Unit)? = null

    var worldSpawnX: Double = 0.0
    var worldSpawnZ: Double = 0.0

    private var nextId = 0
    private val rng = Random(seed xor 0x5A5A5A5A5A5A5A5AL)

    val familyPool: List<String> = ALL_FAMILIES.shuffled(Random(seed)).take(POOL_SIZE)

    // ── État de vague ─────────────────────────────────────────────────────────

    private enum class WavePhase { WAVE, BOSS_WAIT, BOSS, COOLDOWN }
    private var phase = WavePhase.WAVE
    private var phaseTimer: Float

    private var waveIndex = 0
    private var waveFamily = familyPool[0]
    private var waveFamilyRow = 0
    private var waveType = EnemyType.ZOMBIE
    private var waveSize = WAVE_SIZE_MIN + Random(seed + 1L).nextInt(WAVE_SIZE_RANGE)
    private var waveSpawned = 0
    private var bossEnemyId = -1

    private var spawnTimer = SPAWN_FIRST_DELAY
    private var playerInvTimer = 0f

    init {
        phaseTimer = waveSize * SPAWN_INTERVAL
    }

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        playerInvTimer = (playerInvTimer - dt).coerceAtLeast(0f)

        // Recharge bouclier après délai
        if (playerShieldMax > 0 && playerShieldCurrent < playerShieldMax) {
            shieldRechargeTimer -= dt
            if (shieldRechargeTimer <= 0f) {
                playerShieldCurrent = playerShieldMax
                shieldCallback?.invoke(playerShieldCurrent, playerShieldMax)
            }
        }

        for (e in enemies) if (e.hp > 0) e.animTime += dt

        enemies.removeAll { e ->
            if (e.hp <= 0) {
                if (e.id == bossEnemyId) {
                    bossEnemyId = -1
                    if (phase == WavePhase.BOSS) {
                        phase = WavePhase.COOLDOWN
                        phaseTimer = COOLDOWN_DURATION
                    }
                }
                true
            } else false
        }

        phaseTimer -= dt

        when (phase) {
            WavePhase.WAVE -> {
                if (waveSpawned < waveSize) {
                    spawnTimer -= dt
                    if (spawnTimer <= 0f) {
                        spawnTimer = SPAWN_INTERVAL
                        trySpawnWaveEnemy(px, py, pz)
                    }
                }
                if (phaseTimer <= 0f) {
                    phase = WavePhase.BOSS_WAIT
                    phaseTimer = BOSS_INTRO_DELAY
                }
            }
            WavePhase.BOSS_WAIT -> {
                if (phaseTimer <= 0f) trySpawnBoss(px, py, pz)
            }
            WavePhase.BOSS -> {
                if (phaseTimer <= 0f) {
                    enemies.removeIf { it.id == bossEnemyId }
                    bossEnemyId = -1
                    phase = WavePhase.COOLDOWN
                    phaseTimer = COOLDOWN_DURATION
                }
            }
            WavePhase.COOLDOWN -> {
                if (phaseTimer <= 0f) advanceWave()
            }
        }

        for (e in enemies) updateEnemy(e, dt, px, py, pz)
    }

    private fun advanceWave() {
        enemies.clear()
        bossEnemyId = -1
        waveIndex++
        val idx = waveIndex % POOL_SIZE
        waveFamily = familyPool[idx]
        waveFamilyRow = idx
        waveType = EnemyType.values()[waveIndex % EnemyType.values().size]
        waveSize = WAVE_SIZE_MIN + rng.nextInt(WAVE_SIZE_RANGE)
        waveSpawned = 0
        spawnTimer = SPAWN_FIRST_DELAY
        phase = WavePhase.WAVE
        phaseTimer = waveSize * SPAWN_INTERVAL
    }

    // Spawn libre : pas de contrainte de terrain, pas en-dessous du joueur
    private fun trySpawnWaveEnemy(px: Double, py: Double, pz: Double) {
        repeat(20) {
            val angle = rng.nextFloat() * 2 * PI.toFloat()
            val dist  = 12.0 + rng.nextFloat() * 4.0
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return@repeat
            val sy = py + rng.nextFloat() * 10f - 2f
            val e = Enemy(nextId++, waveType, sx, sy, sz)
            e.spriteFamily = waveFamily
            e.familyRow    = waveFamilyRow
            e.level        = computeLevel(sx, sz)
            e.hp           = e.maxHp
            enemies.add(e)
            waveSpawned++
            return
        }
    }

    private fun trySpawnBoss(px: Double, py: Double, pz: Double) {
        repeat(30) {
            val angle = rng.nextFloat() * 2 * PI.toFloat()
            val dist  = 12.0 + rng.nextFloat() * 4.0
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return@repeat
            val sy = py + rng.nextFloat() * 10f - 2f
            val e = Enemy(nextId++, EnemyType.ZOMBIE, sx, sy, sz)
            e.spriteFamily = waveFamily
            e.familyRow    = waveFamilyRow
            e.isBoss       = true
            e.level        = computeLevel(sx, sz).coerceAtLeast(1)
            e.hp           = e.maxHp
            bossEnemyId = e.id
            phase       = WavePhase.BOSS
            phaseTimer  = BOSS_MAX_DURATION
            enemies.add(e)
            return
        }
        phase      = WavePhase.COOLDOWN
        phaseTimer = COOLDOWN_DURATION
    }

    private fun distFromSpawnChunks(x: Double, z: Double): Double {
        val dX = (x - worldSpawnX) / CHUNK_SIZE
        val dZ = (z - worldSpawnZ) / CHUNK_SIZE
        return sqrt(dX * dX + dZ * dZ)
    }

    private fun updateEnemy(e: Enemy, dt: Float, px: Double, py: Double, pz: Double) {
        if (e.hitFlash > 0f) e.hitFlash -= dt

        val dx = px - e.x
        val dz = pz - e.z
        val dist = sqrt(dx * dx + dz * dz)

        e.state = when (e.state) {
            EnemyState.WANDER -> if (dist < e.type.detectRange) EnemyState.CHASE else EnemyState.WANDER
            EnemyState.CHASE  -> when {
                dist < e.type.attackRange       -> EnemyState.ATTACK
                dist > e.type.detectRange * 1.5 -> EnemyState.WANDER
                else                             -> EnemyState.CHASE
            }
            EnemyState.ATTACK -> if (dist > e.type.attackRange * 1.5) EnemyState.CHASE else EnemyState.ATTACK
        }

        val spd = e.scaledSpeed.toDouble() * dt
        when (e.state) {
            EnemyState.WANDER -> {
                e.wanderTimer -= dt
                if (e.wanderTimer <= 0f) {
                    val a = rng.nextFloat() * 2 * PI.toFloat()
                    e.wanderDirX = sin(a); e.wanderDirZ = cos(a)
                    e.wanderTimer = rng.nextFloat() * 3f + 2f
                }
                move(e, e.wanderDirX * spd * 0.4, e.wanderDirZ * spd * 0.4)
                if (e.wanderDirX != 0f || e.wanderDirZ != 0f)
                    e.yaw = atan2(e.wanderDirX, e.wanderDirZ) * (180f / PI.toFloat())
            }
            EnemyState.CHASE -> if (dist > 0.1) {
                val nx = dx / dist; val nz = dz / dist
                move(e, nx * spd, nz * spd)
                e.yaw = atan2(nx.toFloat(), nz.toFloat()) * (180f / PI.toFloat())
            }
            EnemyState.ATTACK -> {
                if (dist > 0.1)
                    e.yaw = atan2((px - e.x).toFloat(), (pz - e.z).toFloat()) * (180f / PI.toFloat())
                e.attackCooldown -= dt
                if (e.attackCooldown <= 0f && playerInvTimer <= 0f) {
                    e.attackCooldown = ATTACK_CD
                    val dmg      = e.scaledDamage
                    val shAbsorb = minOf(playerShieldCurrent, dmg)
                    playerShieldCurrent -= shAbsorb
                    val hpDmg = dmg - shAbsorb
                    if (hpDmg > 0) playerHp = (playerHp - hpDmg).coerceAtLeast(0)
                    playerInvTimer = 0.5f
                    if (shAbsorb > 0 || dmg > 0) {
                        shieldRechargeTimer = SHIELD_RECHARGE_DELAY
                        shieldCallback?.invoke(playerShieldCurrent, playerShieldMax)
                    }
                    playerHpCallback?.invoke(playerHp, playerMaxHp)
                }
            }
        }

        // Tous les ennemis flottent vers la hauteur du joueur (pas de gravité, traverse les murs)
        val targetVY = ((py - 0.9 - e.y) * 4.0).coerceIn(-8.0, 8.0)
        e.velY += (targetVY - e.velY) * (dt * 4.0)
        e.y += e.velY * dt
    }

    // Déplacement sans collision bloc — les mobs traversent les murs
    private fun move(e: Enemy, dx: Double, dz: Double) {
        e.x += dx
        e.z += dz
    }

    fun hitByLaser(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        maxDist: Float
    ): Enemy? {
        var closest: Enemy? = null
        var closestT = maxDist.toDouble()
        for (e in enemies) {
            if (e.hp <= 0) continue
            val r = e.type.radius.toDouble()
            val t = rayAABB(
                ox, oy, oz, ddx, ddy, ddz,
                e.x - r, e.y - e.type.eyeHeight, e.z - r,
                e.x + r, e.y + 0.1, e.z + r
            ) ?: continue
            if (t < closestT) { closestT = t; closest = e }
        }
        return closest
    }

    fun damageEnemy(e: Enemy, dmg: Int) {
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        e.hitFlash = 0.18f
        if (e.state == EnemyState.WANDER) e.state = EnemyState.CHASE
    }

    fun healPlayer(amount: Int) {
        playerHp = (playerHp + amount).coerceAtMost(playerMaxHp)
        playerHpCallback?.invoke(playerHp, playerMaxHp)
    }

    fun computeLevel(blockX: Double, blockZ: Double): Int {
        val distChunks = distFromSpawnChunks(blockX, blockZ)
        return (distChunks / 10.0).toInt() + 1
    }

    private fun rayAABB(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double
    ): Double? {
        var tMin = 0.0; var tMax = Double.MAX_VALUE
        fun axis(o: Double, d: Float, lo: Double, hi: Double): Boolean {
            if (d == 0f) return o in lo..hi
            val t1 = (lo - o) / d; val t2 = (hi - o) / d
            tMin = maxOf(tMin, minOf(t1, t2))
            tMax = minOf(tMax, maxOf(t1, t2))
            return tMax >= tMin
        }
        if (!axis(ox, ddx, minX, maxX)) return null
        if (!axis(oy, ddy, minY, maxY)) return null
        if (!axis(oz, ddz, minZ, maxZ)) return null
        return if (tMax < 0.0) null else tMin
    }

    companion object {
        const val POOL_SIZE            = 32
        const val SPAWN_INTERVAL       = 10f
        const val SPAWN_FIRST_DELAY    = 3f
        const val BOSS_INTRO_DELAY     = 3f
        const val BOSS_MAX_DURATION    = 300f
        const val COOLDOWN_DURATION    = 300f
        const val ATTACK_CD            = 1.5f
        const val WAVE_SIZE_MIN        = 10
        const val WAVE_SIZE_RANGE      = 11
        const val SAFE_ZONE_CHUNKS     = 5.0
        const val SHIELD_RECHARGE_DELAY = 10f

        val ALL_FAMILIES = listOf(
            "amg1", "amg2", "amg3", "amg4",
            "avt1", "avt2", "avt3", "avt4",
            "bmg1", "bmg2", "bmg3", "bmg4",
            "chr1", "dvl1",
            "ftr1", "ftr2", "ftr3", "ftr4",
            "gsd1", "isd1", "jli1", "kin1",
            "knt1", "knt2", "knt3", "knt4",
            "man1", "man2", "man3", "man4",
            "mnt1", "mnt2", "mnt3", "mnt4",
            "mnv1", "mnv2", "mnv3", "mnv4",
            "mst1", "mst2", "mst3", "mst4",
            "nja1", "nja2", "nja3", "nja4",
            "npc1", "npc2", "npc3", "npc4", "npc5", "npc6", "npc7", "npc8", "npc9",
            "pdn1", "pdn2", "pdn3", "pdn4",
            "scr1", "scr2", "scr3", "scr4",
            "skl1",
            "smr1", "smr2", "smr3", "smr4",
            "spd1", "syb1",
            "thf1", "thf2", "thf3", "thf4",
            "trk1",
            "wmg1", "wmg2", "wmg3", "wmg4",
            "wmn1", "wmn2", "wmn3",
            "wnv1", "wnv2", "wnv3", "wnv4",
            "ybo1", "ygr1", "zph1"
        )
    }
}
