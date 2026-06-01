package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MobRegistry
import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class SpawnManager(
    private val world: World,
    private val worldSeed: Long,
    private val enemies: ArrayList<Enemy>,
    private val wardStoneZones: List<Pair<Double, Double>>,
    seed: Long
) {
    var worldSpawnX: Double = 0.0
    var worldSpawnZ: Double = 0.0
    var eventBus: EventBus? = null

    private val rng          = Random(seed xor -0x4E94FF4A4E94FF4BL)
    private var nextId       = 0
    private var bossEnemyId  = -1
    private var bossRewardGiven = false
    private var spawnCooldown = SPAWN_INTERVAL

    // ── Tick principal ────────────────────────────────────────────────────────

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        val nearbyCount = enemies.count { e ->
            val dx = e.x - px; val dz = e.z - pz
            dx * dx + dz * dz <= (SPAWN_MAX_CHUNKS * CHUNK_SIZE).toDouble().let { it * it }
        }
        if (!isInSafeZone(px, pz) && nearbyCount < MAX_ENEMIES_NEARBY) {
            spawnCooldown -= dt
            if (spawnCooldown <= 0f) {
                spawnCooldown = SPAWN_INTERVAL + rng.nextFloat() * SPAWN_JITTER
                tryAmbientSpawn(px, py, pz)
            }
        }
    }

    // Appelé par EnemyManager quand un ennemi est retiré (mort).
    fun onEnemyDied(e: Enemy) {
        val wasBoss = e.id == bossEnemyId && !bossRewardGiven
        if (wasBoss) { bossRewardGiven = true; bossEnemyId = -1 }
        eventBus?.publish(GameEvent.MobDied(e.id, e.x, e.y, e.z, e.level, wasBoss, e.def.id))
    }

    // ── Spawn ambiant ─────────────────────────────────────────────────────────

    private fun tryAmbientSpawn(px: Double, py: Double, pz: Double) {
        val spawnBoss = bossEnemyId == -1 && rng.nextFloat() < BOSS_CHANCE
        val minDist = SPAWN_MIN_CHUNKS * CHUNK_SIZE.toDouble()
        val maxDist = SPAWN_MAX_CHUNKS * CHUNK_SIZE.toDouble()

        repeat(20) {
            val angle = rng.nextDouble() * 2 * PI
            val dist  = minDist + rng.nextDouble() * (maxDist - minDist)
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (!canSpawnAt(sx, sz)) return@repeat

            val sy = findSpawnGround(sx, sz, py) ?: return@repeat

            val biome = biomeAt(sx, sy, sz)
            val zone  = computeLevel(sx, sz).coerceAtLeast(1)
            val eligible = MobRegistry.allEligibleFor(biome, zone)
                .ifEmpty { MobRegistry.all().toList() }
                .ifEmpty { return }

            val def = eligible[rng.nextInt(eligible.size)]
            val e = Enemy(nextId++, def, sx, sy, sz)
            e.spriteSheet = def.spriteSheet
            e.level       = zone
            e.isBoss       = spawnBoss
            e.hp           = e.maxHp
            e.state        = EnemyState.CHASE

            if (spawnBoss) {
                bossEnemyId = e.id; bossRewardGiven = false
                eventBus?.publish(GameEvent.BossSpawned(e.id))
            }
            enemies.add(e)
            return
        }
    }

    // ── Helpers terrain ───────────────────────────────────────────────────────

    private fun findSpawnGround(sx: Double, sz: Double, nearY: Double): Double? {
        val bx = Math.floor(sx).toInt()
        val bz = Math.floor(sz).toInt()
        val startY = (nearY + 8.0).toInt()
        for (by in startY downTo startY - 40) {
            val b = world.blockAt(bx, by, bz)
            if (b == AIR || isWater(b) || isDecoration(b)) continue
            val a1 = world.blockAt(bx, by + 1, bz)
            val a2 = world.blockAt(bx, by + 2, bz)
            if (a1 == AIR && a2 == AIR) return (by + 1).toDouble()
        }
        return null
    }

    private fun isInSafeZone(px: Double, pz: Double): Boolean {
        if (distFromSpawnChunks(px, pz) < SAFE_ZONE_CHUNKS) return true
        return wardStoneZones.any { (wx, wz) ->
            val dX = (px - wx) / CHUNK_SIZE; val dZ = (pz - wz) / CHUNK_SIZE
            sqrt(dX * dX + dZ * dZ) < WARD_SAFE_RADIUS
        }
    }

    private fun canSpawnAt(sx: Double, sz: Double): Boolean {
        if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return false
        return wardStoneZones.none { (wx, wz) ->
            val dX = (sx - wx) / CHUNK_SIZE; val dZ = (sz - wz) / CHUNK_SIZE
            sqrt(dX * dX + dZ * dZ) < WARD_SAFE_RADIUS
        }
    }

    private fun distFromSpawnChunks(x: Double, z: Double): Double {
        val dX = (x - worldSpawnX) / CHUNK_SIZE
        val dZ = (z - worldSpawnZ) / CHUNK_SIZE
        return sqrt(dX * dX + dZ * dZ)
    }

    fun computeLevel(blockX: Double, blockZ: Double): Int =
        (distFromSpawnChunks(blockX, blockZ) / 10.0).toInt() + 1

    // Identifiant de biome sous forme de chaîne pour MobRegistry.allEligibleFor().
    // En surface → SurfaceBiome.name.lowercase(), en cave → Biome.name.lowercase().
    private fun biomeAt(wx: Double, sy: Double, wz: Double): String {
        val chunkY = Math.floorDiv(sy.toInt(), CHUNK_SIZE)
        return if (chunkY >= 0) {
            BiomeMap.surfaceBiomeAt(wx, wz, worldSeed).name.lowercase()
        } else {
            val chunkX = Math.floorDiv(wx.toInt(), CHUNK_SIZE)
            val chunkZ = Math.floorDiv(wz.toInt(), CHUNK_SIZE)
            BiomeMap.biomeAt(chunkX, chunkY, chunkZ, worldSeed).name.lowercase()
        }
    }

    companion object {
        const val SAFE_ZONE_CHUNKS  = 5.0
        const val WARD_SAFE_RADIUS  = 5.0
        const val SPAWN_INTERVAL    = 5f
        const val SPAWN_JITTER      = 3f
        const val MAX_ENEMIES_NEARBY = 10
        const val SPAWN_MIN_CHUNKS  = 2
        const val SPAWN_MAX_CHUNKS  = 4
        const val BOSS_CHANCE       = 0.01f
    }
}
