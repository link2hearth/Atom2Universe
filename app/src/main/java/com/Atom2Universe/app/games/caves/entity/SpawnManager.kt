package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MobDef
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
    var worldSpawnY: Double = 0.0
    var worldSpawnZ: Double = 0.0
    var eventBus: EventBus? = null

    private val rng          = Random(seed xor -0x4E94FF4A4E94FF4BL)
    private var nextId       = 0
    private var bossEnemyId  = -1
    private var bossRewardGiven = false
    private var spawnCooldown = SPAWN_INTERVAL

    private data class SpawnBlock(
        val x: Double,
        val y: Double,
        val z: Double,
        val biome: String,
        val zone: Int
    )

    // Pool shufflée une fois à l'init selon la seed du monde.
    // Indexée par zone (modulo taille) → même zone = même mob pour ce monde.
    private val shuffledPool: List<String> by lazy {
        MobRegistry.all().map { it.id }.shuffled(Random(worldSeed))
    }

    private fun mobForZone(zone: Int, biome: String): MobDef {
        // D'abord chercher un override JSON pour ce biome/zone précis
        val overrides = MobRegistry.allEligibleFor(biome, zone)
        if (overrides.isNotEmpty()) {
            val idx = ((worldSeed xor zone.toLong()) and 0x7FFFFFFFFFFFFFFF) % overrides.size
            return overrides[idx.toInt()]
        }
        // Sinon : pool shufflée → index = zone % taille, au pif total selon la seed
        val pool = shuffledPool
        if (pool.isEmpty()) return MobRegistry.all().first()
        val id = pool[zone % pool.size]
        return MobRegistry.get(id)
    }

    private fun rollMobForSpawn(zone: Int, biome: String): MobDef {
        val eligible = MobRegistry.allEligibleFor(biome, zone)
        if (eligible.isNotEmpty()) return eligible[rng.nextInt(eligible.size)]
        return mobForZone(zone, biome)
    }

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
                tryAmbientSpawn(px, py, pz, MAX_ENEMIES_NEARBY - nearbyCount)
            }
        }
    }

    // Appelé par EnemyManager quand un ennemi est retiré (mort).
    fun onEnemyDied(e: Enemy) {
        val wasBoss = e.id == bossEnemyId && !bossRewardGiven
        if (wasBoss) { bossRewardGiven = true; bossEnemyId = -1 }
        eventBus?.publish(GameEvent.MobDied(e.id, e.x, e.y, e.z, e.level, wasBoss, e.def.id, e.maxHp))
    }

    // ── Spawn ambiant ─────────────────────────────────────────────────────────

    private fun tryAmbientSpawn(px: Double, py: Double, pz: Double, spawnSlots: Int) {
        if (spawnSlots <= 0) return

        val availableBlocks = collectAvailableSpawnBlocks(px, py, pz)
        if (availableBlocks.isEmpty()) return

        val origin = availableBlocks[rng.nextInt(availableBlocks.size)]
        val def = rollMobForSpawn(origin.zone, origin.biome)
        val spawnBoss = bossEnemyId == -1 && def.bossEligible && rng.nextFloat() < BOSS_CHANCE
        val packSize = if (spawnBoss) 1 else rollPackSize().coerceAtMost(spawnSlots)

        val selected = dispersePack(origin, availableBlocks, packSize)
        for ((index, block) in selected.withIndex()) {
            val e = Enemy(nextId++, def, block.x, block.y, block.z)
            e.level       = block.zone
            e.isBoss      = spawnBoss && index == 0
            e.hp          = e.maxHp
            e.state       = EnemyState.CHASE

            if (e.isBoss) {
                bossEnemyId = e.id; bossRewardGiven = false
                eventBus?.publish(GameEvent.BossSpawned(e.id))
            }
            enemies.add(e)
        }
    }

    private fun rollPackSize(): Int = when (rng.nextInt(100)) {
        in 0 until 45  -> 1
        in 45 until 75 -> 2
        in 75 until 93 -> 3
        else           -> 4
    }

    private fun collectAvailableSpawnBlocks(px: Double, py: Double, pz: Double): List<SpawnBlock> {
        val result = ArrayList<SpawnBlock>(MAX_SPAWN_CANDIDATES)
        val minDist = SPAWN_MIN_CHUNKS * CHUNK_SIZE.toDouble()
        val maxDist = SPAWN_MAX_CHUNKS * CHUNK_SIZE.toDouble()
        repeat(SPAWN_BLOCK_CHECKS) {
            if (result.size >= MAX_SPAWN_CANDIDATES) return@repeat

            val angle = rng.nextDouble() * 2 * PI
            val dist  = minDist + rng.nextDouble() * (maxDist - minDist)
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (!canSpawnAt(sx, sz)) return@repeat
            if (isNearExistingEnemy(sx, sz)) return@repeat

            val sy = findSpawnGround(sx, sz, py) ?: return@repeat
            if (!hasSpawnHeadroom(sx, sy, sz)) return@repeat

            val biome = biomeAt(sx, sy, sz)
            val zone  = computeLevel(sx, sy, sz).coerceAtLeast(1)
            result.add(SpawnBlock(sx, sy, sz, biome, zone))
        }
        return result
    }

    private fun dispersePack(origin: SpawnBlock, blocks: List<SpawnBlock>, count: Int): List<SpawnBlock> {
        if (count <= 1) return listOf(origin)
        val selected = ArrayList<SpawnBlock>(count)
        selected.add(origin)

        val shuffled = blocks.shuffled(rng)
        for (block in shuffled) {
            if (selected.size >= count) break
            if (selected.any { squaredDistanceXZ(it, block) < PACK_MIN_SPACING * PACK_MIN_SPACING }) continue
            if (squaredDistanceXZ(origin, block) > PACK_DISPERSE_RADIUS * PACK_DISPERSE_RADIUS) continue
            selected.add(block)
        }

        for (block in shuffled) {
            if (selected.size >= count) break
            if (block !in selected) selected.add(block)
        }
        return selected
    }

    // ── Helpers terrain ───────────────────────────────────────────────────────

    private fun findSpawnGround(sx: Double, sz: Double, nearY: Double): Double? {
        val bx = Math.floor(sx).toInt()
        val bz = Math.floor(sz).toInt()
        val startY = (nearY + 20.0).toInt()
        for (by in startY downTo startY - 120) {
            val b = world.blockAt(bx, by, bz)
            if (b == AIR || isWater(b) || isDecoration(b)) continue
            val a1 = world.blockAt(bx, by + 1, bz)
            val a2 = world.blockAt(bx, by + 2, bz)
            if (a1 == AIR && a2 == AIR) return (by + 1).toDouble()
        }
        return null
    }

    private fun hasSpawnHeadroom(sx: Double, sy: Double, sz: Double): Boolean {
        val bx = Math.floor(sx).toInt()
        val by = Math.floor(sy).toInt()
        val bz = Math.floor(sz).toInt()
        return isFreeForSpawn(bx, by, bz) && isFreeForSpawn(bx, by + 1, bz)
    }

    private fun isFreeForSpawn(bx: Int, by: Int, bz: Int): Boolean {
        val b = world.blockAt(bx, by, bz)
        return b == AIR || isWater(b) || isDecoration(b)
    }

    private fun isNearExistingEnemy(sx: Double, sz: Double): Boolean =
        enemies.any { e ->
            val dx = e.x - sx
            val dz = e.z - sz
            dx * dx + dz * dz < MOB_MIN_SPACING * MOB_MIN_SPACING
        }

    private fun squaredDistanceXZ(a: SpawnBlock, b: SpawnBlock): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
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

    fun computeLevel(blockX: Double, blockY: Double, blockZ: Double): Int {
        val dX = (blockX - worldSpawnX) / CHUNK_SIZE
        val dY = (blockY - worldSpawnY) / CHUNK_SIZE
        val dZ = (blockZ - worldSpawnZ) / CHUNK_SIZE
        return (sqrt(dX * dX + dY * dY + dZ * dZ) / 10.0).toInt() + 1
    }

    // Identifiant de biome sous forme de chaîne pour MobRegistry.allEligibleFor().
    // En surface → SurfaceBiome.name.lowercase(), en cave → Biome.name.lowercase().
    private fun biomeAt(wx: Double, sy: Double, wz: Double): String {
        val chunkY = Math.floorDiv(sy.toInt(), CHUNK_SIZE)
        return if (chunkY >= 0) {
            BiomeMap.surfaceBiomeAt(wx, wz, worldSeed).id
        } else {
            val chunkX = Math.floorDiv(wx.toInt(), CHUNK_SIZE)
            val chunkZ = Math.floorDiv(wz.toInt(), CHUNK_SIZE)
            BiomeMap.biomeAt(chunkX, chunkY, chunkZ, worldSeed).id
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
        const val SPAWN_BLOCK_CHECKS = 96
        const val MAX_SPAWN_CANDIDATES = 32
        const val MOB_MIN_SPACING = 3.0
        const val PACK_MIN_SPACING = 2.0
        const val PACK_DISPERSE_RADIUS = 18.0
    }
}
