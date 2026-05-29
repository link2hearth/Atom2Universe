package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class EnemyManager(private val world: World, seed: Long = 0L) {

    val enemies = ArrayList<Enemy>(32)

    var playerHp = 20
    val playerMaxHp = 20
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)? = null

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
                // Spawner les ennemis de la vague toutes les SPAWN_INTERVAL secondes
                if (waveSpawned < waveSize) {
                    spawnTimer -= dt
                    if (spawnTimer <= 0f) {
                        spawnTimer = SPAWN_INTERVAL
                        trySpawnWaveEnemy(px, py, pz)
                    }
                }
                // La durée de la vague est écoulée → préparer le boss
                if (phaseTimer <= 0f) {
                    phase = WavePhase.BOSS_WAIT
                    phaseTimer = BOSS_INTRO_DELAY
                }
            }
            WavePhase.BOSS_WAIT -> {
                if (phaseTimer <= 0f) trySpawnBoss(px, py, pz)
            }
            WavePhase.BOSS -> {
                // Boss timeout : trop long, on passe à la suite
                if (phaseTimer <= 0f) {
                    enemies.removeIf { it.id == bossEnemyId }
                    bossEnemyId = -1
                    phase = WavePhase.COOLDOWN
                    phaseTimer = COOLDOWN_DURATION
                }
            }
            WavePhase.COOLDOWN -> {
                if (phaseTimer <= 0f) {
                    advanceWave()
                }
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

    private fun trySpawnWaveEnemy(px: Double, py: Double, pz: Double) {
        repeat(20) attempt@{
            val angle = rng.nextFloat() * 2 * PI.toFloat()
            val dist = 12.0 + rng.nextFloat() * 24.0   // 12–36 blocs du joueur
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            // Zone safe : pas de spawn dans les 5 chunks du spawn du monde
            if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return@attempt
            var sy = py
            for (dy in -10..10) {
                val ty = py + dy
                val bBelow = blockAt(floor(sx).toInt(), floor(ty - 1).toInt(), floor(sz).toInt())
                val bAt    = blockAt(floor(sx).toInt(), floor(ty).toInt(),     floor(sz).toInt())
                val bAbove = blockAt(floor(sx).toInt(), floor(ty + 1).toInt(), floor(sz).toInt())
                if (bBelow != AIR && !isDecoration(bBelow) && bAt == AIR && bAbove == AIR) {
                    sy = ty; break
                }
            }
            val e = Enemy(nextId++, waveType, sx, sy, sz)
            e.spriteFamily = waveFamily
            e.familyRow = waveFamilyRow
            e.level = computeLevel(sx, sz)
            e.hp = e.maxHp
            if (!collides(sx, sy, sz, e)) {
                enemies.add(e)
                waveSpawned++
                return
            }
        }
    }

    private fun trySpawnBoss(px: Double, py: Double, pz: Double) {
        repeat(30) attempt@{
            val angle = rng.nextFloat() * 2 * PI.toFloat()
            val dist = 14.0 + rng.nextFloat() * 20.0   // 14–34 blocs du joueur
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return@attempt
            var sy = py
            for (dy in -10..10) {
                val ty = py + dy
                val bBelow = blockAt(floor(sx).toInt(), floor(ty - 1).toInt(), floor(sz).toInt())
                val bAt    = blockAt(floor(sx).toInt(), floor(ty).toInt(),     floor(sz).toInt())
                val bAbove = blockAt(floor(sx).toInt(), floor(ty + 1).toInt(), floor(sz).toInt())
                if (bBelow != AIR && !isDecoration(bBelow) && bAt == AIR && bAbove == AIR) {
                    sy = ty; break
                }
            }
            val e = Enemy(nextId++, EnemyType.ZOMBIE, sx, sy, sz)
            e.spriteFamily = waveFamily
            e.familyRow = waveFamilyRow
            e.isBoss = true
            e.level = computeLevel(sx, sz).coerceAtLeast(1)
            e.hp = e.maxHp
            if (!collides(sx, sy, sz, e)) {
                bossEnemyId = e.id
                phase = WavePhase.BOSS
                phaseTimer = BOSS_MAX_DURATION
                enemies.add(e)
                return
            }
        }
        // Boss introuvable → passer directement au cooldown
        phase = WavePhase.COOLDOWN
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
                    e.yaw = atan2(-e.wanderDirX, -e.wanderDirZ) * (180f / PI.toFloat())
            }
            EnemyState.CHASE -> if (dist > 0.1) {
                val nx = dx / dist; val nz = dz / dist
                move(e, nx * spd, nz * spd)
                e.yaw = atan2(-nx.toFloat(), -nz.toFloat()) * (180f / PI.toFloat())
            }
            EnemyState.ATTACK -> {
                if (dist > 0.1)
                    e.yaw = atan2(-(px - e.x).toFloat(), -(pz - e.z).toFloat()) * (180f / PI.toFloat())
                e.attackCooldown -= dt
                if (e.attackCooldown <= 0f && playerInvTimer <= 0f) {
                    e.attackCooldown = ATTACK_CD
                    playerHp = (playerHp - e.scaledDamage).coerceAtLeast(0)
                    playerInvTimer = 0.5f
                    playerHpCallback?.invoke(playerHp, playerMaxHp)
                }
            }
        }

        if (e.type.flies) {
            val targetVY = ((py - 0.5 - e.y) * 3.0).coerceIn(-4.0, 4.0)
            e.velY += (targetVY - e.velY) * (dt * 5.0)
            val ny = e.y + e.velY * dt
            if (!collides(e.x, ny, e.z, e)) e.y = ny
        } else {
            e.velY = (e.velY - 22.0 * dt).coerceAtLeast(-30.0)
            val ny = e.y + e.velY * dt
            if (!collides(e.x, ny, e.z, e)) {
                e.y = ny
                e.onGround = false
            } else {
                if (e.velY < 0) {
                    e.onGround = true
                    if (!collides(e.x, e.y + 1.05, e.z, e)) e.y += 0.15
                }
                e.velY = 0.0
            }
        }
    }

    private fun move(e: Enemy, dx: Double, dz: Double) {
        if (!collides(e.x + dx, e.y, e.z, e)) e.x += dx
        if (!collides(e.x, e.y, e.z + dz, e)) e.z += dz
    }

    private fun collides(ex: Double, ey: Double, ez: Double, e: Enemy): Boolean {
        val r = e.type.radius.toDouble()
        val h = e.type.eyeHeight.toDouble()
        val x0 = floor(ex - r).toInt();  val x1 = floor(ex + r - 0.01).toInt()
        val y0 = floor(ey - h - 0.1).toInt(); val y1 = floor(ey).toInt()
        val z0 = floor(ez - r).toInt();  val z1 = floor(ez + r - 0.01).toInt()
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = blockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b)) return true
        }
        return false
    }

    private fun blockAt(bx: Int, by: Int, bz: Int): Byte {
        val cx = Math.floorDiv(bx, CHUNK_SIZE)
        val cy = Math.floorDiv(by, CHUNK_SIZE)
        val cz = Math.floorDiv(bz, CHUNK_SIZE)
        val ch = world.getChunk(cx, cy, cz) ?: return AIR
        if (!ch.generated) return AIR
        return ch.blockAt(bx - cx * CHUNK_SIZE, by - cy * CHUNK_SIZE, bz - cz * CHUNK_SIZE)
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
        const val POOL_SIZE = 32
        const val SPAWN_INTERVAL = 10f        // 1 ennemi toutes les 10s
        const val SPAWN_FIRST_DELAY = 3f
        const val BOSS_INTRO_DELAY = 3f       // pause avant le boss
        const val BOSS_MAX_DURATION = 300f    // 5 min max pour tuer le boss
        const val COOLDOWN_DURATION = 300f    // 5 min entre les vagues
        const val ATTACK_CD = 1.5f
        const val WAVE_SIZE_MIN = 10
        const val WAVE_SIZE_RANGE = 11        // taille : 10..20
        const val SAFE_ZONE_CHUNKS = 5.0      // 5 chunks = 80 blocs de safe zone

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
