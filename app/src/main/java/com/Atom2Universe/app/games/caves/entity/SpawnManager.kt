package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.node.MobRegistry
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.render.MobModels
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
    var exploration = false
    var lightAt: ((Int, Int, Int) -> Int)? = null
    /** Palier du site, puis position du gardien : ceux qui étaient là débloquent la pioche. */
    var siteBossDefeated: ((stage: Int, x: Double, y: Double, z: Double) -> Unit)? = null
    var siteEntered: ((UndergroundSites.Site) -> Unit)? = null
    private val defeatedSiteBosses = mutableSetOf<String>()
    private val visitedSites = mutableSetOf<String>()
    @Synchronized fun restoreDefeatedSiteBosses(ids: Set<String>) {
        defeatedSiteBosses.clear()
        defeatedSiteBosses.addAll(ids)
    }
    @Synchronized fun defeatedSiteBossesSnapshot(): Set<String> = defeatedSiteBosses.toSet()

    private val rng          = Random(seed xor -0x4E94FF4A4E94FF4BL)
    private var nextId       = 0
    private var bossEnemyId  = -1
    private var bossRewardGiven = false
    private var spawnCooldown = SPAWN_INTERVAL
    private var siteCheckCooldown = 0f
    fun resetAfterTravel() { bossEnemyId=-1;bossRewardGiven=false;spawnCooldown=SPAWN_INTERVAL;siteCheckCooldown=0f }

    private data class SpawnBlock(
        val x: Double,
        val y: Double,
        val z: Double,
        val biome: String,
        val zone: Int,
        val site: UndergroundSites.Site? = null
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
        val eligible = MobRegistry.allEligibleFor(biome, zone).filter { def ->
            !exploration || when(def.id) {
                "mummy" -> biome in setOf("desert", "sandstone", "savanna")
                "imp", "golem" -> biome in setOf("volcanic", "lava", "basalt") || zone >= 4
                "wraith" -> biome.startsWith("magic") || zone >= 3
                "ogre", "troll" -> biome in setOf("dark_forest", "jungle") || zone >= 3
                "dwarf" -> zone >= 2
                else -> true
            }
        }
        if (eligible.isEmpty()) return mobForZone(zone, biome)
        // Tirage pondéré : petits mobs (poids élevé) fréquents, gros (poids faible) rares.
        val totalWeight = eligible.sumOf { it.spawnWeight.toDouble() }
        if (totalWeight <= 0.0) return eligible[rng.nextInt(eligible.size)]
        var r = rng.nextDouble() * totalWeight
        for (def in eligible) {
            r -= def.spawnWeight
            if (r <= 0.0) return def
        }
        return eligible.last()
    }

    // ── Tick principal ────────────────────────────────────────────────────────

    var enabled = true

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        if (!enabled) return
        if (bossEnemyId >= 0 && enemies.none { it.id == bossEnemyId }) {
            bossEnemyId = -1; bossRewardGiven = false
        }
        if (exploration) {
            siteCheckCooldown -= dt
            if (siteCheckCooldown <= 0f) {
                siteCheckCooldown = 1f
                val site = world.undergroundSiteAt(floor(px).toInt(), floor(py).toInt(), floor(pz).toInt())
                if (site != null) {
                    if (visitedSites.add(site.id)) siteEntered?.invoke(site)
                    if (enemies.count { it.hp > 0 } < MAX_ENEMIES_NEARBY && !isInSafeZone(px,pz))
                        trySiteBoss(site, px, py, pz)
                }
            }
        }
        val nearbyCount = enemies.count { e ->
            val dx = e.x - px; val dz = e.z - pz
            e.hp > 0 && (exploration || dx * dx + dz * dz <= (SPAWN_MAX_CHUNKS * CHUNK_SIZE).toDouble().let { it * it })
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
        val siteVictory = e.isSiteBoss && synchronized(this) { defeatedSiteBosses.add(e.undergroundSiteId!!) }
        val wasBoss = siteVictory || e.id == bossEnemyId && !bossRewardGiven
        if (e.id == bossEnemyId && wasBoss) { bossRewardGiven = true; bossEnemyId = -1 }
        eventBus?.publish(GameEvent.MobDied(e.id, e.x, e.y, e.z, e.level, wasBoss, e.def.id, e.maxHp))
        // Loot and XP subscribers run synchronously before requesting the same world checkpoint.
        if (siteVictory) siteBossDefeated?.invoke(UndergroundSites.stageOfId(e.undergroundSiteId!!), e.x, e.y, e.z)
    }

    // ── Spawn ambiant ─────────────────────────────────────────────────────────

    private fun tryAmbientSpawn(px: Double, py: Double, pz: Double, spawnSlots: Int) {
        if (spawnSlots <= 0) return

        val availableBlocks = collectAvailableSpawnBlocks(px, py, pz)
        if (availableBlocks.isEmpty()) return

        // Loaded ruins receive candidates directly, so a short view radius does not leave them empty.
        val siteBlocks = if (exploration) availableBlocks.filter { it.site != null } else emptyList()
        val pool = if (siteBlocks.isNotEmpty() && rng.nextInt(4) != 0) siteBlocks else availableBlocks
        val origin = pool[rng.nextInt(pool.size)]
        val site = origin.site
        val def = if (site != null) MobRegistry.get(site.kind.mob) else rollMobForSpawn(origin.zone, origin.biome)
        // Exploration bosses belong to persistent encounters; random bosses remain in legacy modes.
        val spawnBoss = !exploration && bossEnemyId == -1 && def.bossEligible && rng.nextFloat() < BOSS_CHANCE
        val siteSlots = if (site == null) spawnSlots else site.kind.population - sitePopulation(site)
        val slots = min(spawnSlots, siteSlots)
        if (slots <= 0) return
        val packSize = if (spawnBoss) 1 else rollPackSize().coerceAtMost(slots)

        val selected = dispersePack(origin, availableBlocks.filter { it.site?.id == site?.id }, packSize)
        for ((index, block) in selected.withIndex()) {
            if (exploration && !hasBodyRoom(def, block.x, block.y, block.z, false)) continue
            if (exploration && isNearExistingEnemy(block.x, block.y, block.z)) continue
            val e = Enemy(nextId++, def, block.x, block.y, block.z)
            e.exploration=exploration
            e.level       = block.zone
            e.undergroundSiteId = site?.id
            e.isBoss      = spawnBoss && index == 0
            e.hp          = e.maxHp
            e.state       = if (exploration) EnemyState.WANDER else EnemyState.CHASE

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
        if (exploration) for (site in world.nearbyUndergroundSites(px,py,pz).shuffled(rng)) {
            if (sitePopulation(site) >= site.kind.population) continue
            for (point in site.spawnPoints.shuffled(rng)) {
                if (result.size >= MAX_SPAWN_CANDIDATES / 2) break
                val sx = point.x + .5; val sy = point.y.toDouble(); val sz = point.z + .5
                val distance = (sx-px).pow(2) + (sy-py).pow(2) + (sz-pz).pow(2)
                if (distance < 16.0*16.0 || distance > 64.0*64.0 || !canSpawnAt(sx,sz) ||
                    isNearExistingEnemy(sx,sy,sz) || guardianSpaceReserved(site,sx,sy,sz)) continue
                if ((lightAt?.invoke(point.x,point.y,point.z) ?: 15) > MAX_SPAWN_LIGHT ||
                    !hasBodyRoom(MobRegistry.get(site.kind.mob),sx,sy,sz,false)) continue
                result.add(SpawnBlock(sx,sy,sz,"",site.level,site))
            }
        }
        val minDist = SPAWN_MIN_CHUNKS * CHUNK_SIZE.toDouble()
        val maxDist = SPAWN_MAX_CHUNKS * CHUNK_SIZE.toDouble()
        repeat(SPAWN_BLOCK_CHECKS) {
            if (result.size >= MAX_SPAWN_CANDIDATES) return@repeat

            val angle = rng.nextDouble() * 2 * PI
            val dist  = minDist + rng.nextDouble() * (maxDist - minDist)
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (!canSpawnAt(sx, sz)) return@repeat
            if (!exploration && isNearExistingEnemy(sx, sz)) return@repeat

            val sy = findSpawnGround(sx, sz, py) ?: return@repeat
            if (!hasSpawnHeadroom(sx, sy, sz)) return@repeat
            if (exploration && (lightAt?.invoke(floor(sx).toInt(), floor(sy).toInt(), floor(sz).toInt()) ?: 15) > MAX_SPAWN_LIGHT) return@repeat
            if (exploration && isNearExistingEnemy(sx,sy,sz)) return@repeat

            val biome = biomeAt(sx, sy, sz)
            val site = if (exploration) world.undergroundSiteAt(floor(sx).toInt(),floor(sy).toInt(),floor(sz).toInt()) else null
            if (site != null && (sitePopulation(site) >= site.kind.population || guardianSpaceReserved(site,sx,sy,sz))) return@repeat
            val zone  = site?.level ?: computeLevel(sx, sy, sz).coerceAtLeast(1)
            result.add(SpawnBlock(sx, sy, sz, biome, zone, site))
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

    private fun sitePopulation(site: UndergroundSites.Site): Int = enemies.count { e ->
        e.hp > 0 && !e.isBoss && (e.undergroundSiteId == site.id ||
            site.contains(floor(e.x).toInt(),floor(e.y).toInt(),floor(e.z).toInt()))
    }

    private fun guardianSpaceReserved(site: UndergroundSites.Site, x: Double, y: Double, z: Double): Boolean {
        if (synchronized(this) { site.id in defeatedSiteBosses }) return false
        val point = site.bossPoint
        return (x-point.x-.5).pow(2) + (y-point.y).pow(2) + (z-point.z-.5).pow(2) < 25.0
    }

    private fun trySiteBoss(site: UndergroundSites.Site, px: Double, py: Double, pz: Double) {
        if (synchronized(this) { site.id in defeatedSiteBosses } ||
            enemies.any { it.hp > 0 && it.isBoss && it.undergroundSiteId == site.id }) return
        val point = site.bossPoint
        val sx = point.x + .5; val sy = point.y.toDouble(); val sz = point.z + .5
        val distance = (sx-px).pow(2) + (sy-py).pow(2) + (sz-pz).pow(2)
        if (distance < 8.0*8.0 || distance > 28.0*28.0 || !canSpawnAt(sx,sz)) return
        val def = MobRegistry.get(site.kind.mob)
        if (!hasBodyRoom(def,sx,sy,sz,true)) return
        val radius = def.radius * Enemy.BOSS_SPRITE_SCALE
        if (enemies.any { it.hp > 0 && abs(it.y-sy) < 6 &&
                (it.x-sx).pow(2) + (it.z-sz).pow(2) < (radius+it.collisionRadius+1).pow(2) }) return
        val enemy = Enemy(nextId++,def,sx,sy,sz).apply {
            exploration = true; undergroundSiteId = site.id; level = site.level
            isBoss = true; hp = maxHp; state = EnemyState.WANDER
        }
        enemies.add(enemy)
        eventBus?.publish(GameEvent.BossSpawned(enemy.id))
    }

    /** Check the full rendered body against loaded, dry cells and its supporting floor. */
    private fun hasBodyRoom(def: MobDef, x: Double, y: Double, z: Double, boss: Boolean): Boolean {
        val scale = if (boss) Enemy.BOSS_SPRITE_SCALE else 1f
        val radius = def.radius.toDouble() * scale
        val height = MobModels.bodyHeightWorld(def.model,def.spriteScale*scale).toDouble().coerceAtLeast(.5)
        for (bx in floor(x-radius+.01).toInt()..floor(x+radius-.01).toInt())
            for (bz in floor(z-radius+.01).toInt()..floor(z+radius-.01).toInt()) {
                val floorY = floor(y-.01).toInt()
                if (!loaded(bx,floorY,bz)) return false
                val ground = world.blockAt(bx,floorY,bz)
                if (ground == AIR || isWater(ground) || ground == LAVA || isDecoration(ground) || BlockRegistry.isPartial(ground)) return false
                for (by in floor(y+.01).toInt()..floor(y+height-.01).toInt()) {
                    if (!loaded(bx,by,bz)) return false
                    val block = world.blockAt(bx,by,bz)
                    if (block != AIR && !isDecoration(block)) return false
                }
            }
        return true
    }

    private fun loaded(x: Int, y: Int, z: Int) =
        world.getChunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16))?.generated == true

    private fun isNearExistingEnemy(x: Double, y: Double, z: Double): Boolean = enemies.any { e ->
        e.hp > 0 && (e.x-x).pow(2) + (e.y-y).pow(2) + (e.z-z).pow(2) < MOB_MIN_SPACING*MOB_MIN_SPACING
    }

    private fun findSpawnGround(sx: Double, sz: Double, nearY: Double): Double? {
        val bx = Math.floor(sx).toInt()
        val bz = Math.floor(sz).toInt()
        val startY = (nearY + 20.0).toInt()
        for (by in startY downTo startY - 120) {
            if (world.getChunk(Math.floorDiv(bx,16),Math.floorDiv(by+2,16),Math.floorDiv(bz,16))?.generated != true ||
                world.getChunk(Math.floorDiv(bx,16),Math.floorDiv(by,16),Math.floorDiv(bz,16))?.generated != true) continue
            val b = world.blockAt(bx, by, bz)
            if (b == AIR || isWater(b) || isDecoration(b) || isLeaf(b) ||
                com.Atom2Universe.app.games.caves.node.BlockRegistry.isPartial(b)) continue
            val a1 = world.blockAt(bx, by + 1, bz)
            val a2 = world.blockAt(bx, by + 2, bz)
            // Les plantes traversables ne doivent pas stériliser les sols des biomes fongiques.
            // La lumière reste vérifiée séparément, notamment dans une case contenant une torche.
            if ((a1 == AIR || exploration && isDecoration(a1)) &&
                (a2 == AIR || exploration && isDecoration(a2))) return (by + 1).toDouble()
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
        if (distFromSpawnChunks(px, pz) < if(exploration) 1.0 else SAFE_ZONE_CHUNKS) return true
        return wardStoneZones.any { (wx, wz) ->
            val dX = (px - wx) / CHUNK_SIZE; val dZ = (pz - wz) / CHUNK_SIZE
            sqrt(dX * dX + dZ * dZ) < WARD_SAFE_RADIUS
        }
    }

    private fun canSpawnAt(sx: Double, sz: Double): Boolean {
        if (distFromSpawnChunks(sx, sz) < if(exploration) 1.0 else SAFE_ZONE_CHUNKS) return false
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
        if(exploration) {
            return com.Atom2Universe.app.games.caves.world.MineralProgression.stage(blockY)+1
        }
        val dX = (blockX - worldSpawnX) / CHUNK_SIZE
        val dY = (blockY - worldSpawnY) / CHUNK_SIZE
        val dZ = (blockZ - worldSpawnZ) / CHUNK_SIZE
        return (sqrt(dX * dX + dY * dY + dZ * dZ) / 10.0).toInt() + 1
    }

    // Identifiant de biome sous forme de chaîne pour MobRegistry.allEligibleFor().
    // En surface → SurfaceBiome.name.lowercase(), en cave → Biome.name.lowercase().
    private fun biomeAt(wx: Double, sy: Double, wz: Double): String {
        world.naturalSurfaceBiomeAt(wx, sy, wz)?.let { return com.Atom2Universe.app.games.caves.world.RegionalBiomes.parent(it) }
        // Cavern ecology follows the cave biome noise, even inside a mountain above Y=0.
        return BiomeMap.biomeAt(floor(wx / CHUNK_SIZE).toInt(), floor(sy / CHUNK_SIZE).toInt(),
            floor(wz / CHUNK_SIZE).toInt(), worldSeed).id
    }

    companion object {
        // Pénombre naturelle (champignons <= 5) : spawn possible. Torche 15 : protection
        // jusqu'à 9 pas de propagation dans l'air, les murs arrêtant la lumière.
        const val MAX_SPAWN_LIGHT = 5
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
