package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

class World(private val seed: Long = 42L, private val storage: CaveWorldChunkStorage? = null) {
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    val rebuildQueue = ConcurrentLinkedQueue<Long>()
    val renderRadiusXZ = 12
    val renderRadiusY  = 5   // couvre cy 0..10 pour un joueur à cy=5 (toute la bande de surface)

    private val SURFACE_CY_MAX = 9
    private val ISLAND_CY_MIN  = 625
    private val SURFACE_BASE_Y = 80.0

    fun chunkKey(cx: Int, cy: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or
        ((cy.toLong() and 0xFFFFF) shl 20) or
        ((cz.toLong() and 0xFFFFF) shl 40)

    fun getChunk(cx: Int, cy: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cy, cz)]
    fun getChunkByKey(key: Long): Chunk? = chunks[key]
    fun allChunks(): Collection<Chunk> = chunks.values

    fun keyToCx(key: Long): Int { val v = (key and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCy(key: Long): Int { val v = ((key shr 20) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCz(key: Long): Int { val v = ((key shr 40) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }

    fun updateAroundPlayer(pcx: Int, pcy: Int, pcz: Int, onNeedGenerate: (Chunk) -> Unit) {
        val toGenerate = mutableListOf<Chunk>()
        for (dz in -renderRadiusXZ..renderRadiusXZ)
        for (dy in -renderRadiusY..renderRadiusY)
        for (dx in -renderRadiusXZ..renderRadiusXZ) {
            val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
            // Ciel vide entre surface et îles flottantes : aucun contenu, inutile de générer.
            if (cy > SURFACE_CY_MAX && cy < ISLAND_CY_MIN) continue
            val key = chunkKey(cx, cy, cz)
            if (!chunks.containsKey(key) && inFlight.add(key)) {
                val chunk = Chunk(cx, cy, cz)
                chunks[key] = chunk
                toGenerate.add(chunk)
            }
        }
        toGenerate.sortBy { c ->
            val dx = c.cx - pcx; val dz = c.cz - pcz; val dy = c.cy - pcy
            // Priorité horizontale : les chunks au même niveau Y chargent en premier
            dx * dx + dz * dz + dy * dy * 8
        }
        toGenerate.forEach(onNeedGenerate)

        chunks.entries.filter { (_, c) ->
            // Ne jamais décharger un chunk en cours de génération (inFlight = génération en cours)
            !inFlight.contains(chunkKey(c.cx, c.cy, c.cz)) && (
                abs(c.cx - pcx) > renderRadiusXZ + 2 ||
                abs(c.cy - pcy) > renderRadiusY + 2 ||
                abs(c.cz - pcz) > renderRadiusXZ + 2)
        }.forEach { (key, _) -> chunks.remove(key); inFlight.remove(key) }
    }

    fun markGenerated(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        inFlight.remove(key)
        chunk.generated = true
        chunk.meshDirty = true
        rebuildQueue.add(key)
        val neighbors = arrayOf(
            intArrayOf(chunk.cx-1,chunk.cy,chunk.cz), intArrayOf(chunk.cx+1,chunk.cy,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy-1,chunk.cz), intArrayOf(chunk.cx,chunk.cy+1,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy,chunk.cz-1), intArrayOf(chunk.cx,chunk.cy,chunk.cz+1)
        )
        for ((nx, ny, nz) in neighbors) {
            val nb = getChunk(nx, ny, nz) ?: continue
            if (nb.generated) { nb.meshDirty = true; rebuildQueue.add(chunkKey(nx, ny, nz)) }
        }
    }

    fun pregenerateChunk(cx: Int, cy: Int, cz: Int): Chunk {
        val key = chunkKey(cx, cy, cz)
        chunks[key]?.takeIf { it.generated }?.let { return it }
        val chunk = Chunk(cx, cy, cz)
        generate(chunk)
        chunks[key] = chunk
        inFlight.remove(key)
        chunk.generated = true
        chunk.meshDirty = true
        rebuildQueue.add(key)
        return chunk
    }

    fun findSpawnPoint(): FloatArray {
        val approxCy = (surfaceHeight(8.0, 8.0) / CHUNK_SIZE).toInt()
        for (dcy in 0 downTo -2) {
            val cyCand = approxCy + dcy
            if (cyCand !in 0..SURFACE_CY_MAX) continue
            val chunk = pregenerateChunk(0, cyCand, 0)
            for (ly in CHUNK_SIZE - 1 downTo 1)
            for (lz in 0 until CHUNK_SIZE)
            for (lx in 0 until CHUNK_SIZE) {
                val below = chunk.blockAt(lx, ly - 1, lz)
                if (chunk.blockAt(lx, ly, lz) == AIR && below != AIR && !isDecoration(below))
                    return floatArrayOf(
                        chunk.worldX + lx + 0.5f,
                        chunk.worldY + ly + 1.62f,
                        chunk.worldZ + lz + 0.5f
                    )
            }
        }
        return floatArrayOf(8.5f, surfaceHeight(8.0, 8.0).toFloat() + 1.62f, 8.5f)
    }

    // ── Pipeline de génération ────────────────────────────────────────────────

    fun generate(chunk: Chunk) {
        when {
            chunk.cy in 0..SURFACE_CY_MAX -> generateSurface(chunk)
            chunk.cy < 0                  -> generateCave(chunk)
            chunk.cy >= ISLAND_CY_MIN     -> generateIsland(chunk)
            // sky void : chunk reste AIR (ByteArray initialisé à 0)
        }
        storage?.applyDiff(chunk)
    }

    private fun generateCave(chunk: Chunk) {
        val biome = BiomeMap.biomeAt(chunk.cx, chunk.cy, chunk.cz, seed)
        applyRockLayers(chunk, biome)
        carveVoidHalls(chunk)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveRoomFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz, biome)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveWormsFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz, biome)
        applyPockets(chunk, biome)
        applyOreVeins(chunk, biome)
        applyFloorSurface(chunk, biome)
        plantTrees(chunk, biome)
        applyDecorations(chunk, biome)
    }

    private fun generateSurface(chunk: Chunk) {
        val wy = chunk.worldY

        // Biome calculé une seule fois au centre du chunk (économie ×256 appels noise)
        val sb     = BiomeMap.surfaceBiomeAt(
            (chunk.worldX + CHUNK_SIZE / 2).toDouble(),
            (chunk.worldZ + CHUNK_SIZE / 2).toDouble(), seed)
        val dirtB  = surfaceDirtBlock(sb)
        val stoneB = surfaceStoneBlock(sb)

        // Précalcul du heightmap et des blocs de surface (256 colonnes, 1 passe)
        val heights   = IntArray(CHUNK_SIZE * CHUNK_SIZE)
        val topBlocks = ByteArray(CHUNK_SIZE * CHUNK_SIZE)
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val i = lz * CHUNK_SIZE + lx
            val wx = (chunk.worldX + lx).toDouble()
            val wz = (chunk.worldZ + lz).toDouble()
            heights[i]   = surfaceHeight(wx, wz).toInt()
            topBlocks[i] = surfaceTopBlock(sb, wx, wz)
        }

        // Remplissage des blocs
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val i = lz * CHUNK_SIZE + lx; val h = heights[i]; val top = topBlocks[i]
            for (ly in 0 until CHUNK_SIZE) {
                val worldY = wy + ly
                chunk.setBlock(lx, ly, lz, when {
                    worldY > h      -> AIR
                    worldY == h     -> top
                    worldY >= h - 3 -> dirtB
                    else            -> stoneB
                })
            }
        }

        // Entrée de grotte : un seul worm du chunk directement en-dessous (cy=0 uniquement)
        if (chunk.cy == 0) {
            val caveB = BiomeMap.biomeAt(chunk.cx, -1, chunk.cz, seed)
            carveWormsFrom(chunk, chunk.cx, -1, chunk.cz, caveB)
        }

        applyOreVeins(chunk, Biome.DEFAULT)
        plantSurfaceTrees(chunk, sb)
        applySurfaceDecorations(chunk, sb)
    }

    private fun generateIsland(chunk: Chunk) {
        val superSize = 30
        val scx = Math.floorDiv(chunk.cx, superSize)
        val scz = Math.floorDiv(chunk.cz, superSize)
        for (dscz in -1..1) for (dscx in -1..1) {
            val sx = scx + dscx; val sz = scz + dscz
            val rng = superCellRng(sx, sz + 77777)
            if (rng.nextFloat() > 0.03f) continue
            val cx = (sx * superSize + 3 + rng.nextInt(superSize - 6)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cz = (sz * superSize + 3 + rng.nextInt(superSize - 6)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cy = (ISLAND_CY_MIN + rng.nextInt(1249 - ISLAND_CY_MIN)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val hw    = 30f + rng.nextFloat() * 80f
            val hh    =  8f + rng.nextFloat() * 16f
            val hd    = 30f + rng.nextFloat() * 80f
            val round =  8f + rng.nextFloat() * 14f
            if (chunk.worldX + CHUNK_SIZE < cx - hw - round || chunk.worldX > cx + hw + round) continue
            if (chunk.worldY + CHUNK_SIZE < cy - hh - round || chunk.worldY > cy + hh + round) continue
            if (chunk.worldZ + CHUNK_SIZE < cz - hd - round || chunk.worldZ > cz + hd + round) continue
            val ox = chunk.worldX; val oy = chunk.worldY; val oz = chunk.worldZ
            val round2 = round * round
            for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
                val dx = max(0f, abs(ox + lx - cx) - hw)
                val dy = max(0f, abs(oy + ly - cy) - hh)
                val dz = max(0f, abs(oz + lz - cz) - hd)
                if (dx * dx + dy * dy + dz * dz <= round2) chunk.setBlock(lx, ly, lz, STONE)
            }
        }
        // Surface pass : herbe sur le dessus, terre en dessous
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                if (chunk.blockAt(lx, ly, lz) != STONE) continue
                chunk.setBlock(lx, ly, lz, GRASS)
                if (ly - 1 >= 0 && chunk.blockAt(lx, ly - 1, lz) == STONE) chunk.setBlock(lx, ly - 1, lz, DIRT)
                if (ly - 2 >= 0 && chunk.blockAt(lx, ly - 2, lz) == STONE) chunk.setBlock(lx, ly - 2, lz, DIRT)
                break
            }
        }
        val islandSb = BiomeMap.surfaceBiomeAt(
            (chunk.worldX + CHUNK_SIZE / 2).toDouble(),
            (chunk.worldZ + CHUNK_SIZE / 2).toDouble(), seed)
        plantSurfaceTrees(chunk, islandSb)
    }

    // ── Roche de base ─────────────────────────────────────────────────────────

    private fun applyRockLayers(chunk: Chunk, biome: Biome) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            val nZone = SimplexNoise.noise(x * 0.012, y * 0.012, z * 0.012)
            val block: Byte = when (biome) {
                Biome.DEFAULT        -> if (nZone > 0.20) GRANITE else STONE
                Biome.LIMESTONE      -> if (nZone > 0.40) QUARTZ  else STONE
                Biome.GRANITE_ROUGE  -> if (nZone > 0.30) BRICK_RED else GRANITE
                Biome.MUSHROOM_CAVE  -> STONE
                Biome.ICE_CAVE       -> if (nZone > -0.08) ICE else STONE
            }
            chunk.setBlock(lx, ly, lz, block)
        }
    }

    // ── Grandes salles ────────────────────────────────────────────────────────

    private fun carveRoomFrom(target: Chunk, cx: Int, cy: Int, cz: Int, biome: Biome) {
        val rng = chunkRng(cx, cy, cz)
        val forced = (cx == 0 && cy == 0 && cz == 0)
        val roomChance = when (biome) {
            Biome.LIMESTONE      -> 0.018f   // cavernes plus ouvertes
            Biome.GRANITE_ROUGE  -> 0.002f   // roche dense, peu de salles
            Biome.ICE_CAVE       -> 0.004f   // assez parsemé
            else                 -> 0.006f
        }
        if (!forced && rng.nextFloat() > roomChance) return

        val rx = (cx * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val ry = (cy * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val rz = (cz * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val radius = if (rng.nextFloat() > 0.20f)
            8f + rng.nextFloat() * 4f
        else
            14f + rng.nextFloat() * 4f
        val r2 = radius * radius
        val ox = target.worldX.toFloat(); val oy = target.worldY.toFloat(); val oz = target.worldZ.toFloat()
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val dx = ox + lx - rx; val dy = oy + ly - ry; val dz = oz + lz - rz
            if (dx*dx + dy*dy + dz*dz <= r2) target.setBlock(lx, ly, lz, AIR)
        }
    }

    // ── Worms ─────────────────────────────────────────────────────────────────

    private fun carveWormsFrom(target: Chunk, cx: Int, cy: Int, cz: Int, biome: Biome) {
        val rng = chunkRng(cx, cy, cz)
        val zeroChance = when (biome) {
            Biome.LIMESTONE     -> 0.30f  // plus de tunnels
            Biome.GRANITE_ROUGE -> 0.65f  // moins de tunnels
            else                -> 0.45f
        }
        val wormCount = when {
            rng.nextFloat() < zeroChance -> 0
            rng.nextFloat() < 0.80f      -> 1
            else                          -> 2
        }
        if (wormCount == 0) return

        repeat(wormCount) {
            var wx = (cx * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var wy = (cy * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var wz = (cz * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var yaw   = rng.nextFloat() * 2f * PI.toFloat()
            var pitch = (rng.nextFloat() - 0.5f) * (PI / 3).toFloat()

            val baseRadius = if (rng.nextFloat() > 0.20f)
                1.5f + rng.nextFloat() * 1.0f
            else
                2.5f + rng.nextFloat() * 1.5f
            val length = 100 + rng.nextInt(150)

            val bMinX = target.worldX - baseRadius - 1f; val bMaxX = target.worldX + CHUNK_SIZE + baseRadius + 1f
            val bMinY = target.worldY - baseRadius - 1f; val bMaxY = target.worldY + CHUNK_SIZE + baseRadius + 1f
            val bMinZ = target.worldZ - baseRadius - 1f; val bMaxZ = target.worldZ + CHUNK_SIZE + baseRadius + 1f

            repeat(length) { step ->
                val osc = sin(step.toFloat() * 0.18f) * baseRadius * 0.25f
                val radius = (baseRadius + osc).coerceIn(1.0f, 5.0f)
                if (wx in bMinX..bMaxX && wy in bMinY..bMaxY && wz in bMinZ..bMaxZ)
                    carveInChunk(target, wx, wy, wz, radius)
                wx += cos(pitch) * sin(yaw)
                wy += sin(pitch)
                wz += cos(pitch) * cos(yaw)
                yaw   += (rng.nextFloat() - 0.5f) * 0.45f
                pitch += (rng.nextFloat() - 0.5f) * 0.20f
                pitch  = pitch.coerceIn((-PI / 2.5).toFloat(), (PI / 2.5).toFloat())
            }
        }
    }

    private fun carveInChunk(chunk: Chunk, cx: Float, cy: Float, cz: Float, radius: Float) {
        val r = ceil(radius).toInt()
        val ox = chunk.worldX; val oy = chunk.worldY; val oz = chunk.worldZ
        val r2 = radius * radius
        for (dz in -r..r) for (dy in -r..r) for (dx in -r..r) {
            if (dx*dx + dy*dy + dz*dz > r2) continue
            val lx = (cx + dx).toInt() - ox
            val ly = (cy + dy).toInt() - oy
            val lz = (cz + dz).toInt() - oz
            if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
                chunk.setBlock(lx, ly, lz, AIR)
        }
    }

    // ── Poches de matériaux ───────────────────────────────────────────────────

    private fun applyPockets(chunk: Chunk, biome: Biome) {
        if (biome == Biome.ICE_CAVE) return  // glace et pierre : pas de poches de terre/sable
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue
            if (neighborBlock(chunk, lx, ly + 1, lz) == AIR) continue
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            val nPocket = SimplexNoise.noise(x * 0.065 + seed * 0.003, y * 0.065, z * 0.065)
            if (nPocket < 0.55) continue
            val nType = SimplexNoise.noise(x * 0.022 + seed * 0.005, y * 0.022, z * 0.022)
            val dist = chunkDist(chunk)
            val pocket: Byte = when (biome) {
                Biome.MUSHROOM_CAVE -> if (nType > 0.0) DIRT else GRAVEL
                Biome.GRANITE_ROUGE -> GRAVEL
                else -> when {
                    nType > 0.25  -> DIRT
                    nType > -0.20 -> GRAVEL
                    else          -> sandForDist(dist, (wx + lx).toDouble(), (wz + lz).toDouble())
                }
            }
            chunk.setBlock(lx, ly, lz, pocket)
        }
    }

    // ── Minerais ─────────────────────────────────────────────────────────────

    private fun applyOreVeins(chunk: Chunk, biome: Biome) {
        val rng = chunkRng(chunk.cx * 11 + 7, chunk.cy * 13 + 3, chunk.cz * 17 + 5)

        if (biome != Biome.DEFAULT) {
            when (biome) {
                Biome.LIMESTONE -> {
                    repeat(2 + rng.nextInt(3)) { placeOreBlob(chunk, rng, COAL,    3, 6) }
                    repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, CRYSTAL, 2, 5) }
                    if (rng.nextFloat() < 0.35f) placeOreBlob(chunk, rng, QUARTZ,  3, 6)
                }
                Biome.GRANITE_ROUGE -> {
                    repeat(2 + rng.nextInt(3)) { placeOreBlob(chunk, rng, GOLD,    4, 7) }
                    repeat(2 + rng.nextInt(2)) { placeOreBlob(chunk, rng, RUBY,    3, 6) }
                    if (rng.nextFloat() < 0.40f) placeOreBlob(chunk, rng, EMERALD, 2, 4)
                }
                Biome.MUSHROOM_CAVE -> {
                    repeat(2 + rng.nextInt(3)) { placeOreBlob(chunk, rng, EMERALD, 3, 6) }
                    repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, IRON,    3, 5) }
                    if (rng.nextFloat() < 0.30f) placeOreBlob(chunk, rng, COAL,    3, 5)
                }
                Biome.ICE_CAVE -> {
                    repeat(2 + rng.nextInt(2)) { placeOreBlob(chunk, rng, SILVER,  3, 6) }
                    repeat(2 + rng.nextInt(2)) { placeOreBlob(chunk, rng, CRYSTAL, 3, 5) }
                    if (rng.nextFloat() < 0.20f) placeOreBlob(chunk, rng, GOLD,    2, 4)
                }
                else -> {}
            }
            return
        }

        // DEFAULT : progression basée sur la distance au spawn
        val dist = chunkDist(chunk)
        when {
            dist < 50f  -> {
                repeat(2 + rng.nextInt(2)) { placeOreBlob(chunk, rng, COAL,   4, 7) }
                if (rng.nextFloat() < 0.5f) placeOreBlob(chunk, rng, COPPER,  3, 5)
            }
            dist < 100f -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, COAL,   4, 6) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, IRON,   4, 6) }
                if (rng.nextFloat() < 0.4f) placeOreBlob(chunk, rng, COPPER,  3, 5)
            }
            dist < 200f -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, IRON,   4, 6) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, SILVER, 3, 5) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, COAL,   3, 5)
            }
            dist < 300f -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, SILVER, 3, 5) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, GOLD,   3, 5) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, IRON,   3, 5)
            }
            dist < 450f -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, GOLD,   3, 5) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, RUBY,   2, 4) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, SILVER, 3, 4)
            }
            dist < 650f -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, RUBY,    2, 4) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, EMERALD, 2, 4) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, GOLD,   2, 4)
            }
            else -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, EMERALD, 2, 4) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, CRYSTAL, 2, 3) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, RUBY,   2, 3)
            }
        }
    }

    private fun placeOreBlob(chunk: Chunk, rng: Random, ore: Byte, minBlocks: Int, maxBlocks: Int) {
        var x = rng.nextInt(CHUNK_SIZE)
        var y = rng.nextInt(CHUNK_SIZE)
        var z = rng.nextInt(CHUNK_SIZE)
        val count = minBlocks + rng.nextInt(maxBlocks - minBlocks + 1)
        repeat(count) {
            val b = chunk.blockAt(x, y, z)
            if (b == STONE || b == GRANITE || b == QUARTZ || b == BRICK_RED || b == ICE)
                chunk.setBlock(x, y, z, ore)
            x = (x + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
            y = (y + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
            z = (z + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
        }
    }

    // ── Sol des espaces ouverts ───────────────────────────────────────────────

    private fun applyFloorSurface(chunk: Chunk, biome: Biome) {
        val dist = chunkDist(chunk)
        // Le biome DEFAULT n'applique ses sols herbus que près du spawn
        if (biome == Biome.DEFAULT && dist > 50f) return

        val wx = chunk.worldX.toDouble(); val wz = chunk.worldZ.toDouble()

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue
            if (neighborBlock(chunk, lx, ly + 1, lz) != AIR) continue
            if (neighborBlock(chunk, lx, ly + 2, lz) != AIR) continue

            val x = wx + lx; val z = wz + lz
            val terrain: Byte = when (biome) {
                Biome.DEFAULT -> {
                    val nType = SimplexNoise.noise(x * 0.09, 0.0, z * 0.09)
                    when {
                        nType > 0.20  -> GRASS
                        nType > -0.10 -> DIRT
                        nType > -0.40 -> GRAVEL
                        else          -> sandForDist(dist, x, z)
                    }
                }
                Biome.LIMESTONE -> {
                    val nType = SimplexNoise.noise(x * 0.12 + seed * 0.001, 0.0, z * 0.12)
                    if (nType > 0.10) GRAVEL else STONE
                }
                Biome.GRANITE_ROUGE -> REDSAND
                Biome.MUSHROOM_CAVE -> {
                    val nType = SimplexNoise.noise(x * 0.10 + seed * 0.002, 0.0, z * 0.10)
                    if (nType > 0.0) DIRT else GRAVEL
                }
                Biome.ICE_CAVE -> SNOW
            }
            chunk.setBlock(lx, ly, lz, terrain)
        }
    }

    // ── Arbres ────────────────────────────────────────────────────────────────

    private fun plantTrees(chunk: Chunk, biome: Biome) {
        if (biome != Biome.DEFAULT) return
        val rng = chunkRng(chunk.cx * 7 + 3, chunk.cy, chunk.cz * 13 + 5)
        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            if (rng.nextFloat() > 0.04f) continue
            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            }
            if (grassY < 0) continue
            val trunkH = 3 + rng.nextInt(3)
            if (grassY + trunkH + 3 >= CHUNK_SIZE) continue
            var blocked = false
            for (dy in 1..trunkH + 2) {
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            }
            if (blocked) continue
            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD)
            val topY = grassY + trunkH
            for (dy in -1..2) for (dz in -2..2) for (dx in -2..2) {
                val ly = topY + dy; val lxl = lx + dx; val lzl = lz + dz
                if (lxl !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE) continue
                if (dx*dx + dy*dy + dz*dz > 5) continue
                if (chunk.blockAt(lxl, ly, lzl) == AIR) chunk.setBlock(lxl, ly, lzl, LEAVES)
            }
        }
    }

    // ── Décorations (cross-sprites) ───────────────────────────────────────────

    private fun applyDecorations(chunk: Chunk, biome: Biome) {
        if (biome != Biome.LIMESTONE && biome != Biome.MUSHROOM_CAVE) return
        val rng = chunkRng(chunk.cx * 19 + 11, chunk.cy * 7 + 3, chunk.cz * 23 + 17)
        val density = if (biome == Biome.LIMESTONE) 0.12f else 0.16f

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            if (rng.nextFloat() > density) continue
            // Cherche le premier sol : bloc solide avec au moins 1 bloc d'air au-dessus
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b)) continue
                if (ly + 1 >= CHUNK_SIZE) break
                if (chunk.blockAt(lx, ly + 1, lz) != AIR) break
                val decor: Byte = when (biome) {
                    Biome.LIMESTONE -> if (rng.nextFloat() > 0.45f) ROCK else ROCK_MOSS
                    Biome.MUSHROOM_CAVE -> when {
                        rng.nextFloat() < 0.40f -> MUSHROOM_RED
                        rng.nextFloat() < 0.55f -> MUSHROOM_BROWN
                        else                     -> MUSHROOM_TAN
                    }
                    else -> break
                }
                chunk.setBlock(lx, ly + 1, lz, decor)
                break
            }
        }
    }

    // ── Surface : heightmap et blocs ─────────────────────────────────────────

    internal fun surfaceHeight(wx: Double, wz: Double): Double {
        val s = (seed and 0xFFFFF).toDouble() * 0.0001
        // Bruit 2D : ~35% plus rapide que 3D pour le même résultat (y constant)
        val h0 = SimplexNoise.noise(wx * 0.0015 + s,         wz * 0.0015)        * 32.0
        val h1 = SimplexNoise.noise(wx * 0.005  + s + 100.0, wz * 0.005)         * 14.0
        val h2 = SimplexNoise.noise(wx * 0.015  + s + 200.0, wz * 0.015)         *  6.0
        val h3 = SimplexNoise.noise(wx * 0.050  + s + 300.0, wz * 0.050)         *  2.5
        return SURFACE_BASE_Y + h0 + h1 + h2 + h3
    }

    private fun surfaceTopBlock(sb: SurfaceBiome, wx: Double, wz: Double): Byte = when (sb) {
        SurfaceBiome.DESERT -> if (SimplexNoise.noise(wx * 0.04 + 400.0, wz * 0.04) > 0.2) REDSAND else SAND
        SurfaceBiome.ROCKY  -> if (SimplexNoise.noise(wx * 0.07 + 500.0, wz * 0.07) > 0.0) GRANITE else STONE
        SurfaceBiome.PLAINS -> if (SimplexNoise.noise(wx * 0.035 + 600.0, wz * 0.035) > 0.50) SAND else GRASS
        SurfaceBiome.FOREST -> GRASS
    }

    private fun surfaceDirtBlock(sb: SurfaceBiome): Byte = when (sb) {
        SurfaceBiome.DESERT -> SAND
        SurfaceBiome.ROCKY  -> STONE
        else                -> DIRT
    }

    private fun surfaceStoneBlock(sb: SurfaceBiome): Byte = when (sb) {
        SurfaceBiome.ROCKY -> GRANITE
        else               -> STONE
    }

    // ── Surface : arbres groupés en forêts ────────────────────────────────────

    private fun plantSurfaceTrees(chunk: Chunk, sb: SurfaceBiome) {
        if (sb == SurfaceBiome.DESERT || sb == SurfaceBiome.ROCKY) return

        val rng = chunkRng(chunk.cx * 7 + 3, chunk.cy + 100, chunk.cz * 13 + 5)
        val clusterN = SimplexNoise.noise(
            (chunk.worldX + 8).toDouble() * 0.007 + 900.0,
            (chunk.worldZ + 8).toDouble() * 0.007)
        val density = when (sb) {
            SurfaceBiome.FOREST -> (0.28f + clusterN.toFloat() * 0.22f).coerceIn(0.02f, 0.55f)
            else                -> (0.04f + clusterN.toFloat() * 0.04f).coerceIn(0.00f, 0.12f)
        }

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            if (rng.nextFloat() > density) continue
            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue
            val trunkH = 3 + rng.nextInt(3)
            if (grassY + trunkH + 3 >= CHUNK_SIZE) continue
            var blocked = false
            for (dy in 1..(trunkH + 2))
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue
            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD)
            val topY = grassY + trunkH
            for (dy in -1..2) for (dz in -2..2) for (dx in -2..2) {
                val lyl = topY + dy; val lxl = lx + dx; val lzl = lz + dz
                if (lxl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE) continue
                if (dx*dx + dy*dy + dz*dz > 5) continue
                if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES)
            }
        }
    }

    // ── Surface : décorations ─────────────────────────────────────────────────

    private fun applySurfaceDecorations(chunk: Chunk, sb: SurfaceBiome) {
        val density = when (sb) {
            SurfaceBiome.DESERT -> 0.07f
            SurfaceBiome.ROCKY  -> 0.05f
            else                -> 0.01f
        }
        val rng = chunkRng(chunk.cx * 19 + 11, chunk.cy * 7 + 3, chunk.cz * 23 + 17)
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            if (rng.nextFloat() > density) continue
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b)) continue
                if (ly + 1 >= CHUNK_SIZE || chunk.blockAt(lx, ly + 1, lz) != AIR) break
                val decor: Byte = when (sb) {
                    SurfaceBiome.ROCKY  -> ROCK
                    SurfaceBiome.FOREST -> ROCK_MOSS
                    else                -> if (rng.nextFloat() > 0.5f) ROCK else ROCK_MOSS
                }
                chunk.setBlock(lx, ly + 1, lz, decor)
                break
            }
        }
    }

    // ── Grands espaces vides rectangulaires ──────────────────────────────────

    private fun carveVoidHalls(target: Chunk) {
        val superSize = 25
        val scx = Math.floorDiv(target.cx, superSize)
        val scz = Math.floorDiv(target.cz, superSize)

        for (dscz in -1..1) for (dscx in -1..1) {
            val sx = scx + dscx; val sz = scz + dscz
            val rng = superCellRng(sx, sz)
            if (rng.nextFloat() > 0.02f) continue

            val cx = ((sx * superSize + 3 + rng.nextInt(superSize - 6)).toFloat()) * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cy = ((rng.nextInt(9) - 4).toFloat()) * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cz = ((sz * superSize + 3 + rng.nextInt(superSize - 6)).toFloat()) * CHUNK_SIZE + CHUNK_SIZE * 0.5f

            val hw    = 90f + rng.nextFloat() * 150f   // 90–240 blocs demi-largeur X  (≈ 11–30 chunks total)
            val hh    = 10f + rng.nextFloat() * 14f    // 10–24 blocs demi-hauteur     (très plat)
            val hd    = 90f + rng.nextFloat() * 150f   // 90–240 blocs demi-profondeur Z
            val round =  8f + rng.nextFloat() * 10f    // 8–18 blocs d'arrondi aux bords/coins

            if (target.worldX + CHUNK_SIZE < cx - hw - round || target.worldX > cx + hw + round) continue
            if (target.worldY + CHUNK_SIZE < cy - hh - round || target.worldY > cy + hh + round) continue
            if (target.worldZ + CHUNK_SIZE < cz - hd - round || target.worldZ > cz + hd + round) continue

            val ox = target.worldX; val oy = target.worldY; val oz = target.worldZ
            val round2 = round * round
            for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
                val dx = max(0f, abs(ox + lx - cx) - hw)
                val dy = max(0f, abs(oy + ly - cy) - hh)
                val dz = max(0f, abs(oz + lz - cz) - hd)
                if (dx * dx + dy * dy + dz * dz <= round2) target.setBlock(lx, ly, lz, AIR)
            }
        }
    }

    private fun superCellRng(sx: Int, sz: Int): Random {
        val s = seed xor (sx.toLong() * 374761393L xor sz.toLong() * 1234567891L)
        return Random(s * 6364136223846793005L + 1442695040888963407L)
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private fun chunkDist(chunk: Chunk) =
        sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()

    private fun sandForDist(dist: Float, x: Double, z: Double): Byte = when {
        dist < 100f -> SAND
        dist > 200f -> REDSAND
        else -> if (SimplexNoise.noise(x * 0.05, 0.0, z * 0.05) > 0.0) SAND else REDSAND
    }

    private fun chunkRng(cx: Int, cy: Int, cz: Int): Random {
        val s = seed xor (chunkKey(cx, cy, cz) * 6364136223846793005L + 1442695040888963407L)
        return Random(s)
    }

    // ── Modification de blocs ─────────────────────────────────────────────────

    fun setBlock(wx: Int, wy: Int, wz: Int, type: Byte) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        chunk.setBlock(lx, ly, lz, type)
        chunk.version++
        val key = chunkKey(cx, cy, cz)
        chunk.meshDirty = true
        rebuildQueue.add(key)
        storage?.recordChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
        for ((nx, ny, nz) in arrayOf(
            intArrayOf(cx-1,cy,cz), intArrayOf(cx+1,cy,cz),
            intArrayOf(cx,cy-1,cz), intArrayOf(cx,cy+1,cz),
            intArrayOf(cx,cy,cz-1), intArrayOf(cx,cy,cz+1)
        )) {
            val n = getChunk(nx, ny, nz) ?: continue
            if (n.generated) { n.meshDirty = true; rebuildQueue.add(chunkKey(nx, ny, nz)) }
        }
    }

    // ── Voisinage pour le mesh ────────────────────────────────────────────────

    fun neighborBlock(baseChunk: Chunk, lx: Int, ly: Int, lz: Int): Byte {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = getChunk(ncx, ncy, ncz) ?: return STONE
        if (!neighbor.generated) return STONE
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }
}
