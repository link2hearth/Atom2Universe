package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

class World(private val seed: Long = 42L, private val storage: CaveWorldChunkStorage? = null) {
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    // File des chunks dont le mesh doit être reconstruit (thread-safe)
    val rebuildQueue = ConcurrentLinkedQueue<Long>()

    val renderRadius = 4

    fun chunkKey(cx: Int, cy: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or
        ((cy.toLong() and 0xFFFFF) shl 20) or
        ((cz.toLong() and 0xFFFFF) shl 40)

    fun getChunk(cx: Int, cy: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cy, cz)]
    fun getChunkByKey(key: Long): Chunk? = chunks[key]

    fun allChunks(): Collection<Chunk> = chunks.values

    // Décode les coordonnées de chunk depuis la clé Long
    fun keyToCx(key: Long): Int { val v = (key and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCy(key: Long): Int { val v = ((key shr 20) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCz(key: Long): Int { val v = ((key shr 40) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }

    fun updateAroundPlayer(pcx: Int, pcy: Int, pcz: Int, onNeedGenerate: (Chunk) -> Unit) {
        // Collecte puis trie par distance au joueur — les chunks proches génèrent en premier
        val toGenerate = mutableListOf<Chunk>()
        for (dz in -renderRadius..renderRadius)
        for (dy in -renderRadius..renderRadius)
        for (dx in -renderRadius..renderRadius) {
            val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
            val key = chunkKey(cx, cy, cz)
            if (!chunks.containsKey(key) && inFlight.add(key)) {
                val chunk = Chunk(cx, cy, cz)
                chunks[key] = chunk
                toGenerate.add(chunk)
            }
        }
        toGenerate.sortBy { c ->
            val dx = c.cx - pcx; val dy = c.cy - pcy; val dz = c.cz - pcz
            dx * dx + dy * dy + dz * dz
        }
        toGenerate.forEach(onNeedGenerate)

        val toRemove = chunks.entries.filter { (_, chunk) ->
            abs(chunk.cx - pcx) > renderRadius + 1 ||
            abs(chunk.cy - pcy) > renderRadius + 1 ||
            abs(chunk.cz - pcz) > renderRadius + 1
        }
        toRemove.forEach { (key, _) ->
            chunks.remove(key)
            inFlight.remove(key)
        }
    }

    fun markGenerated(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        inFlight.remove(key)
        chunk.generated = true
        chunk.meshDirty = true
        rebuildQueue.add(key)

        // Seam fix : les 6 voisins face-à-face doivent recalculer leurs faces frontières
        val neighbors = arrayOf(
            intArrayOf(chunk.cx - 1, chunk.cy, chunk.cz),
            intArrayOf(chunk.cx + 1, chunk.cy, chunk.cz),
            intArrayOf(chunk.cx, chunk.cy - 1, chunk.cz),
            intArrayOf(chunk.cx, chunk.cy + 1, chunk.cz),
            intArrayOf(chunk.cx, chunk.cy, chunk.cz - 1),
            intArrayOf(chunk.cx, chunk.cy, chunk.cz + 1),
        )
        for ((nx, ny, nz) in neighbors) {
            val neighbor = getChunk(nx, ny, nz) ?: continue
            if (neighbor.generated) {
                neighbor.meshDirty = true
                rebuildQueue.add(chunkKey(nx, ny, nz))
            }
        }
    }

    // Génère le chunk (cx,cy,cz) et l'insère directement dans la world map.
    // Utilisé pour le spawn : le chunk existe déjà quand le joueur tombe dessus.
    fun pregenerateChunk(cx: Int, cy: Int, cz: Int): Chunk {
        val key = chunkKey(cx, cy, cz)
        chunks[key]?.takeIf { it.generated }?.let { return it }  // déjà là
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
        val chunk = pregenerateChunk(0, 0, 0)
        // Cherche un espace avec sol solide + 2 blocs d'air (pieds à ly, tête à ly+1).
        // neighborBlock gère les bords de chunk (les voisins sont déjà pré-générés).
        for (ly in CHUNK_SIZE - 1 downTo 1)
        for (lz in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            if (neighborBlock(chunk, lx, ly,     lz) == AIR &&
                neighborBlock(chunk, lx, ly + 1, lz) == AIR &&
                neighborBlock(chunk, lx, ly - 1, lz) != AIR)
                return floatArrayOf(lx + 0.5f, ly + 1.62f, lz + 0.5f)
        }
        // Fallback : premier espace de 2 blocs d'air consécutifs
        for (ly in CHUNK_SIZE - 1 downTo 1)
        for (lz in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            if (chunk.blockAt(lx, ly, lz) == AIR && chunk.blockAt(lx, ly + 1, lz) == AIR)
                return floatArrayOf(lx + 0.5f, ly + 1.62f, lz + 0.5f)
        }
        return floatArrayOf(8.5f, 9.62f, 8.5f)
    }

    // ── Pipeline de génération ────────────────────────────────────────────────

    fun generate(chunk: Chunk) {
        // 1. Roche de base avec failles géologiques
        applyRockLayers(chunk)

        // 2. Creusage : grandes salles puis worms (voisinage rayon 1)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveRoomFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveWormsFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz)

        // 3. Poches de matériaux (dirt/gravel/sand dans la masse rocheuse)
        applyPockets(chunk)

        // 4. Veines de minerais (progression radiale)
        applyOreVeins(chunk)

        // 5. Sol des espaces ouverts (herbe/dirt/gravel/sable) — ≤ 50 chunks du spawn
        applyFloorSurface(chunk)

        // 6. Arbres sur les blocs GRASS
        plantTrees(chunk)

        // Applique les modifications du joueur par-dessus la génération procédurale
        storage?.applyDiff(chunk)
    }

    // ── Roche de base ────────────────────────────────────────────────────────

    // Grandes zones homogènes : STONE dominant (~70 %), GRANITE en îlots (~30 %).
    // Le QUARTZ n'apparaît que via applyPockets / applyOreVeins, jamais ici.
    private fun applyRockLayers(chunk: Chunk) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            // Pas d'offset seed ici : les zones géologiques sont universelles (même pattern pour toute seed)
            val nZone = SimplexNoise.noise(x * 0.012, y * 0.012, z * 0.012)
            chunk.setBlock(lx, ly, lz, if (nZone > 0.20) GRANITE else STONE)
        }
    }

    // ── Grandes salles ────────────────────────────────────────────────────────

    // 1 % de chance qu'un chunk soit centre d'une salle.
    // Le chunk (0,0,0) est toujours une salle (spawn garanti).
    // Sphère centrée au milieu du chunk :
    //   80 % petite (r 8-12) → ≤ 4 blocs dans les voisins
    //   20 % grande (r 14-18) → ≤ 10 blocs dans les voisins, rare
    private fun carveRoomFrom(target: Chunk, cx: Int, cy: Int, cz: Int) {
        val rng = chunkRng(cx, cy, cz)
        val forced = (cx == 0 && cy == 0 && cz == 0)
        if (!forced && rng.nextFloat() > 0.006f) return

        val rx = (cx * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val ry = (cy * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val rz = (cz * CHUNK_SIZE + CHUNK_SIZE / 2).toFloat()
        val radius = if (rng.nextFloat() > 0.20f)
            8f + rng.nextFloat() * 4f    // 8-12 blocs (commun)
        else
            14f + rng.nextFloat() * 4f   // 14-18 blocs (rare)

        val r2 = radius * radius
        val ox = target.worldX.toFloat()
        val oy = target.worldY.toFloat()
        val oz = target.worldZ.toFloat()
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val dx = ox + lx - rx; val dy = oy + ly - ry; val dz = oz + lz - rz
            if (dx * dx + dy * dy + dz * dz <= r2)
                target.setBlock(lx, ly, lz, AIR)
        }
    }

    // ── Worms (grottes serpents) ──────────────────────────────────────────────

    private fun carveWormsFrom(target: Chunk, cx: Int, cy: Int, cz: Int) {
        val rng = chunkRng(cx, cy, cz)

        // 45 % de chance de ne rien creuser depuis ce chunk source
        val wormCount = when {
            rng.nextFloat() < 0.45f -> 0
            rng.nextFloat() < 0.80f -> 1
            else                    -> 2
        }
        if (wormCount == 0) return

        repeat(wormCount) {
            var wx = (cx * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var wy = (cy * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var wz = (cz * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var yaw   = rng.nextFloat() * 2f * PI.toFloat()
            var pitch = (rng.nextFloat() - 0.5f) * (PI / 3).toFloat()

            // 80 % fin (r 1.5-2.5 → tunnel 3-5 blocs de large)
            // 20 % large (r 2.5-4.0 → tunnel 5-8 blocs de large)
            val baseRadius = if (rng.nextFloat() > 0.20f)
                1.5f + rng.nextFloat() * 1.0f
            else
                2.5f + rng.nextFloat() * 1.5f

            val length = 100 + rng.nextInt(150)   // 100-250 steps

            val bMinX = target.worldX - baseRadius - 1f; val bMaxX = target.worldX + CHUNK_SIZE + baseRadius + 1f
            val bMinY = target.worldY - baseRadius - 1f; val bMaxY = target.worldY + CHUNK_SIZE + baseRadius + 1f
            val bMinZ = target.worldZ - baseRadius - 1f; val bMaxZ = target.worldZ + CHUNK_SIZE + baseRadius + 1f

            repeat(length) { step ->
                // Oscillation du rayon : rétrécissements et élargissements naturels
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
        val r  = ceil(radius).toInt()
        val ox = chunk.worldX; val oy = chunk.worldY; val oz = chunk.worldZ
        val r2 = radius * radius
        for (dz in -r..r) for (dy in -r..r) for (dx in -r..r) {
            if (dx * dx + dy * dy + dz * dz > r2) continue
            val lx = (cx + dx).toInt() - ox
            val ly = (cy + dy).toInt() - oy
            val lz = (cz + dz).toInt() - oz
            if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
                chunk.setBlock(lx, ly, lz, AIR)
        }
    }

    // ── Poches de matériaux ───────────────────────────────────────────────────

    // Bulles de DIRT / GRAVEL / SAND strictement à l'intérieur de la roche.
    // On skip tout bloc qui a de l'AIR au-dessus : les parois/sols exposés
    // sont gérés par applyFloorSurface et ne doivent pas être polluées par les poches.
    private fun applyPockets(chunk: Chunk) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue
            // Seulement dans la masse rocheuse, jamais sur une surface exposée
            if (neighborBlock(chunk, lx, ly + 1, lz) == AIR) continue
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            val nPocket = SimplexNoise.noise(x * 0.065 + seed * 0.003, y * 0.065, z * 0.065)
            if (nPocket < 0.55) continue
            val nType = SimplexNoise.noise(x * 0.022 + seed * 0.005, y * 0.022, z * 0.022)
            val dist = sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()
            val pocket: Byte = when {
                nType > 0.25  -> DIRT
                nType > -0.20 -> GRAVEL
                else          -> sandForDist(dist, (wx + lx).toDouble(), (wz + lz).toDouble())
            }
            chunk.setBlock(lx, ly, lz, pocket)
        }
    }

    // ── Minerais : budget par chunk ───────────────────────────────────────────

    // Chaque chunk tire un nombre déterministe de blobs d'ore selon sa distance au spawn.
    // Un blob = random walk de N blocs qui ne pose de l'ore que sur de la roche solide.
    // Les chunks très creux placeront naturellement moins de blocs (le walk tombe dans du vide).
    private fun applyOreVeins(chunk: Chunk) {
        val dist = sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()
        val rng = chunkRng(chunk.cx * 11 + 7, chunk.cy * 13 + 3, chunk.cz * 17 + 5)

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
            else        -> {
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, EMERALD, 2, 4) }
                repeat(1 + rng.nextInt(2)) { placeOreBlob(chunk, rng, CRYSTAL, 2, 3) }
                if (rng.nextFloat() < 0.3f) placeOreBlob(chunk, rng, RUBY,   2, 3)
            }
        }
    }

    // Random walk dans la roche : ne pose de l'ore que sur STONE / GRANITE / QUARTZ.
    private fun placeOreBlob(chunk: Chunk, rng: Random, ore: Byte, minBlocks: Int, maxBlocks: Int) {
        var x = rng.nextInt(CHUNK_SIZE)
        var y = rng.nextInt(CHUNK_SIZE)
        var z = rng.nextInt(CHUNK_SIZE)
        val count = minBlocks + rng.nextInt(maxBlocks - minBlocks + 1)
        repeat(count) {
            val b = chunk.blockAt(x, y, z)
            if (b == STONE || b == GRANITE || b == QUARTZ) chunk.setBlock(x, y, z, ore)
            x = (x + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
            y = (y + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
            z = (z + rng.nextInt(3) - 1).coerceIn(0, CHUNK_SIZE - 1)
        }
    }

    // ── Sol des espaces ouverts ───────────────────────────────────────────────

    // Pose GRASS / DIRT / GRAVEL / SAND sur tout bloc solide qui a ≥ 2 blocs d'AIR
    // au-dessus (espace assez ouvert pour qu'on marche dessus).
    // neighborBlock gère les bords de chunks (retourne STONE pour chunk inconnu → pas de faux sol).
    // Actif seulement dans les 50 chunks autour du spawn.
    private fun applyFloorSurface(chunk: Chunk) {
        val dist = sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()
        if (dist > 50f) return

        val wx = chunk.worldX.toDouble()
        val wz = chunk.worldZ.toDouble()

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue                               // seulement les blocs solides
            if (neighborBlock(chunk, lx, ly + 1, lz) != AIR) continue        // doit avoir de l'air juste au-dessus
            if (neighborBlock(chunk, lx, ly + 2, lz) != AIR) continue        // et encore au-dessus (≥ 2 blocs dégagés)

            val x = wx + lx; val z = wz + lz
            val nType = SimplexNoise.noise(x * 0.09, 0.0, z * 0.09)
            val terrain: Byte = when {
                nType > 0.20  -> GRASS
                nType > -0.10 -> DIRT
                nType > -0.40 -> GRAVEL
                else          -> sandForDist(dist, x, z)
            }
            chunk.setBlock(lx, ly, lz, terrain)
        }
    }

    private fun plantTrees(chunk: Chunk) {
        val rng = chunkRng(chunk.cx * 7 + 3, chunk.cy, chunk.cz * 13 + 5)
        for (lz in 2 until CHUNK_SIZE - 2) {
            for (lx in 2 until CHUNK_SIZE - 2) {
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
                    if (dx * dx + dy * dy + dz * dz > 5) continue
                    if (chunk.blockAt(lxl, ly, lzl) == AIR) chunk.setBlock(lxl, ly, lzl, LEAVES)
                }
            }
        }
    }

    // RNG déterministe par chunk, indépendant pour chaque (cx, cy, cz)
    private fun chunkRng(cx: Int, cy: Int, cz: Int): Random {
        val s = seed xor (chunkKey(cx, cy, cz) * 6364136223846793005L + 1442695040888963407L)
        return Random(s)
    }

    // ── Modification de blocs (minage) ───────────────────────────────────────

    fun setBlock(wx: Int, wy: Int, wz: Int, type: Byte) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        chunk.setBlock(lx, ly, lz, type)
        chunk.version++           // invalide tout build en cours pour ce chunk
        val key = chunkKey(cx, cy, cz)
        chunk.meshDirty = true
        rebuildQueue.add(key)
        storage?.recordChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
        // Face neighbors
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

    // SAND < 100 chunks, mix 100-200, REDSAND > 200
    private fun sandForDist(dist: Float, x: Double, z: Double): Byte = when {
        dist < 100f -> SAND
        dist > 200f -> REDSAND
        else -> if (SimplexNoise.noise(x * 0.05, 0.0, z * 0.05) > 0.0) SAND else REDSAND
    }

    fun neighborBlock(baseChunk: Chunk, lx: Int, ly: Int, lz: Int): Byte {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx
        val wy = baseChunk.worldY + ly
        val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE)
        val ncy = Math.floorDiv(wy, CHUNK_SIZE)
        val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        // Un chunk non-généré a blocks=ByteArray=0=AIR — traiter comme STONE pour éviter
        // que applyFloorSurface place du GRASS sur les bords de chunk (voisin créé mais vide).
        val neighbor = getChunk(ncx, ncy, ncz) ?: return STONE
        if (!neighbor.generated) return STONE
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }
}
