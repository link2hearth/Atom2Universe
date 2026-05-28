package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

class World(private val seed: Long = 42L) {
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
        for (dz in -renderRadius..renderRadius)
        for (dy in -renderRadius..renderRadius)
        for (dx in -renderRadius..renderRadius) {
            val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
            val key = chunkKey(cx, cy, cz)
            if (!chunks.containsKey(key) && inFlight.add(key)) {
                val chunk = Chunk(cx, cy, cz)
                chunks[key] = chunk
                onNeedGenerate(chunk)
            }
        }
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
        // Scan de haut en bas sur TOUTES les colonnes : premier sol trouvé (air sur solide).
        // La sphère peut traverser toute la hauteur du chunk sur la colonne centrale,
        // mais ses bords ont des sols valides sur les colonnes périphériques.
        for (ly in CHUNK_SIZE - 1 downTo 1)
        for (lz in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            if (chunk.blockAt(lx, ly, lz) == AIR && chunk.blockAt(lx, ly - 1, lz) != AIR)
                return floatArrayOf(lx + 0.5f, ly + 1.62f, lz + 0.5f)
        }
        // Fallback : premier air (le joueur tombe jusqu'au prochain sol)
        for (ly in CHUNK_SIZE - 1 downTo 0)
        for (lz in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            if (chunk.blockAt(lx, ly, lz) == AIR)
                return floatArrayOf(lx + 0.5f, ly + 1.62f, lz + 0.5f)
        }
        return floatArrayOf(8.5f, 9.62f, 8.5f)
    }

    // ── Génération bruit (conservé) ───────────────────────────────────────────
    /*
    fun generate(chunk: Chunk) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE)
        for (ly in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            val x = (wx + lx).toDouble()
            val y = (wy + ly).toDouble()
            val z = (wz + lz).toDouble()
            val cave = SimplexNoise.noise(x * 0.04, y * 0.04, z * 0.04) +
                       0.5 * SimplexNoise.noise(x * 0.09, y * 0.09, z * 0.09) +
                       0.25 * SimplexNoise.noise(x * 0.18, y * 0.18, z * 0.18)
            if (cave < 0.15) { chunk.setBlock(lx, ly, lz, AIR); continue }
            val typeN = SimplexNoise.noise(x * 0.015 + seed, y * 0.015, z * 0.015)
            val depth = -chunk.cy
            val block: Byte = when {
                depth > 4 && typeN > 0.55 -> GOLD
                typeN > 0.30              -> COAL
                typeN > -0.10             -> GRANITE
                typeN > -0.50             -> QUARTZ
                depth > 2 && typeN < -0.7 -> CRYSTAL
                else                      -> STONE
            }
            chunk.setBlock(lx, ly, lz, block)
        }
    }
    */

    // ── Génération worms + grandes salles ────────────────────────────────────

    fun generate(chunk: Chunk) {
        chunk.blocks.fill(STONE)

        // Grandes salles en premier (rayon 1 suffit, sphères max ~18 blocs)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveRoomFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz)

        // Worms (rayon 1, connectent les salles entre elles)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveWormsFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz)

        applyOres(chunk)
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
        if (!forced && rng.nextFloat() > 0.01f) return

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

    private fun carveWormsFrom(target: Chunk, cx: Int, cy: Int, cz: Int) {
        val rng = chunkRng(cx, cy, cz)

        // Densité réduite : 50 % de chance de ne rien creuser depuis ce chunk source
        val wormCount = when {
            rng.nextFloat() < 0.50f -> 0
            rng.nextFloat() < 0.85f -> 1
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
            val radius = if (rng.nextFloat() > 0.20f)
                1.5f + rng.nextFloat() * 1.0f
            else
                2.5f + rng.nextFloat() * 1.5f

            val length = 100 + rng.nextInt(150)   // 100-250 steps

            // Bornes étendues du chunk cible : évite d'appeler carveInChunk inutilement
            val bMinX = target.worldX - radius; val bMaxX = target.worldX + CHUNK_SIZE + radius
            val bMinY = target.worldY - radius; val bMaxY = target.worldY + CHUNK_SIZE + radius
            val bMinZ = target.worldZ - radius; val bMaxZ = target.worldZ + CHUNK_SIZE + radius

            repeat(length) {
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

    private fun applyOres(chunk: Chunk) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        // Distance au spawn en chunks — 3D, toutes directions équivalentes
        val dist = sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()

        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            if (chunk.blockAt(lx, ly, lz) == AIR) continue
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()

            val nRock  = SimplexNoise.noise(x * 0.025 + seed,       y * 0.025, z * 0.025)
            val nPatch = SimplexNoise.noise(x * 0.07  + seed * 1.5, y * 0.07,  z * 0.07)
            val nVein  = SimplexNoise.noise(x * 0.05  + seed * 0.7, y * 0.05,  z * 0.05)
            val nVein2 = SimplexNoise.noise(x * 0.09  - seed * 0.4, y * 0.09,  z * 0.09)

            // Petites poches de lave — rares, réparties dans tout le monde
            if (nVein > 0.76 && nVein2 > 0.70) { chunk.setBlock(lx, ly, lz, LAVA); continue }

            // Roche de base : noise pur, aucune notion de profondeur ou direction
            val base: Byte = when {
                nPatch > 0.58  -> GRAVEL
                nPatch > 0.22  -> DIRT
                nRock  > 0.38  -> GRANITE
                nRock  > -0.05 -> STONE
                nRock  > -0.42 -> QUARTZ
                else           -> STONE
            }

            // Minerais : progression radiale depuis le spawn
            val ore: Byte? = when {
                nVein < 0.44 -> null
                dist < 50f   -> when {
                    nVein > 0.58 -> COPPER
                    nVein > 0.50 -> COAL
                    else         -> null
                }
                dist < 100f  -> when {
                    nVein > 0.62 -> IRON
                    nVein > 0.52 -> COAL
                    nVein > 0.46 -> COPPER
                    else         -> null
                }
                dist < 200f  -> when {
                    nVein > 0.65 -> SILVER
                    nVein > 0.54 -> IRON
                    nVein > 0.47 -> COAL
                    else         -> null
                }
                dist < 300f  -> when {
                    nVein > 0.68 -> GOLD
                    nVein > 0.57 -> SILVER
                    nVein > 0.48 -> IRON
                    else         -> null
                }
                dist < 450f  -> when {
                    nVein > 0.70 -> RUBY
                    nVein > 0.60 -> GOLD
                    nVein > 0.50 -> SILVER
                    else         -> null
                }
                dist < 650f  -> when {
                    nVein > 0.72 -> EMERALD
                    nVein > 0.63 -> RUBY
                    nVein > 0.52 -> GOLD
                    else         -> null
                }
                else         -> when {
                    nVein > 0.76 -> CRYSTAL
                    nVein > 0.66 -> EMERALD
                    nVein > 0.55 -> RUBY
                    else         -> null
                }
            }

            chunk.setBlock(lx, ly, lz, ore ?: base)
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
        chunk.setBlock(wx - cx * CHUNK_SIZE, wy - cy * CHUNK_SIZE, wz - cz * CHUNK_SIZE, type)
        chunk.version++           // invalide tout build en cours pour ce chunk
        val key = chunkKey(cx, cy, cz)
        chunk.meshDirty = true
        rebuildQueue.add(key)
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

    fun neighborBlock(baseChunk: Chunk, lx: Int, ly: Int, lz: Int): Byte {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx
        val wy = baseChunk.worldY + ly
        val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE)
        val ncy = Math.floorDiv(wy, CHUNK_SIZE)
        val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = getChunk(ncx, ncy, ncz) ?: return STONE
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }
}
