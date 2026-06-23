package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

class World(private val seed: Long = 42L, private val storage: CaveWorldChunkStorage? = null) {
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    val rebuildQueue = ConcurrentLinkedQueue<Long>()
    val waterRebuildQueue = ConcurrentLinkedQueue<Long>()
    // File de propagation de la skylight (drainée sur le thread GL, séparée du meshing).
    val lightQueue = ConcurrentLinkedQueue<Long>()
    private val lightQueued = ConcurrentHashMap.newKeySet<Long>()
    val renderRadiusXZ     = 12  // rayon XZ commun aux deux modes
    val renderRadiusYSurface = 5  // plage Y en surface (cylindre) — identique à avant
    val renderRadiusCave   = 7   // rayon de la sphère souterrain

    // SURFACE_CY_MAX (bande de surface) est défini dans Chunk.kt, partagé avec LodBuilder et
    // CaveRenderer. Le coût de streaming reste borné par renderRadiusYSurface : seuls les chunks
    // dans ±renderRadiusYSurface autour du joueur sont chargés, quelle que soit l'altitude.
    private val ISLAND_CY_MIN  = 625
    private val SURFACE_BASE_Y = 80.0
    private val SEA_LEVEL      = 74
    // Pas d'échantillonnage du biome (le champ est très basse fréquence) : 1 argmax par cellule
    // BIOME_STEP×BIOME_STEP au lieu de par bloc. La hauteur reste calculée par bloc.
    private val BIOME_STEP     = 2

    // ── Couches souterraines ──────────────────────────────────────────────────
    // Structure verticale (cy négatif) : cave → surface souterraine → cave → ...
    // CAVE_PERIOD_CY  : épaisseur totale d'un cycle (cave + surface souterraine)
    // CAVE_DEPTH_CY   : portion cave du cycle (chunks de roche + worms)
    // US_HEIGHT_CY    : portion surface souterraine (terrain + ~130 blocs d'air)
    private val CAVE_PERIOD_CY  = 70
    private val CAVE_DEPTH_CY   = 60
    private val US_HEIGHT_CY    = CAVE_PERIOD_CY - CAVE_DEPTH_CY   // = 10 chunks = 160 blocs

    private fun isUndergroundSurface(cy: Int): Boolean {
        val depth = -cy - 1
        return (depth % CAVE_PERIOD_CY) >= CAVE_DEPTH_CY
    }

    // worldY du bas (Y le plus négatif) de la bande de surface souterraine contenant cy.
    private fun undergroundBandBaseWorldY(cy: Int): Int {
        val depth   = -cy - 1
        val cycle   = depth / CAVE_PERIOD_CY
        return -(cycle * CAVE_PERIOD_CY + CAVE_DEPTH_CY + US_HEIGHT_CY) * CHUNK_SIZE
    }

    fun chunkKey(cx: Int, cy: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or
        ((cy.toLong() and 0xFFFFF) shl 20) or
        ((cz.toLong() and 0xFFFFF) shl 40)

    fun getChunk(cx: Int, cy: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cy, cz)]
    fun getChunkByKey(key: Long): Chunk? = chunks[key]
    fun allChunks(): Collection<Chunk> = chunks.values

    fun hasPendingLight(): Boolean = lightQueue.isNotEmpty()

    fun enqueueLight(key: Long) {
        if (lightQueued.add(key)) lightQueue.add(key)
    }

    fun enqueueLight(cx: Int, cy: Int, cz: Int) = enqueueLight(chunkKey(cx, cy, cz))

    fun pollLightKey(): Long? {
        val key = lightQueue.poll() ?: return null
        lightQueued.remove(key)
        return key
    }

    fun keyToCx(key: Long): Int { val v = (key and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCy(key: Long): Int { val v = ((key shr 20) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    fun keyToCz(key: Long): Int { val v = ((key shr 40) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }

    fun updateAroundPlayer(
        pcx: Int,
        pcy: Int,
        pcz: Int,
        viewDirX: Float = 0f,
        viewDirZ: Float = 0f,
        maxNewChunks: Int = Int.MAX_VALUE,
        onNeedGenerate: (Chunk) -> Unit
    ): Boolean {
        val candidates = mutableListOf<ChunkCandidate>()
        val isSurface = pcy >= 0

        if (isSurface) {
            // Cylindre : disque XZ + plage Y fixe — simple et stable
            val rxz = renderRadiusXZ; val ry = renderRadiusYSurface
            val rxz2 = rxz * rxz
            for (dz in -rxz..rxz)
                for (dy in -ry..ry)
                    for (dx in -rxz..rxz) {
                if (dx * dx + dz * dz > rxz2) continue
                val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
                if (cy > SURFACE_CY_MAX && cy < ISLAND_CY_MIN) continue
                val key = chunkKey(cx, cy, cz)
                if (!chunks.containsKey(key) && !inFlight.contains(key))
                    candidates.add(ChunkCandidate(cx, cy, cz, key, streamPriority(dx, dy, dz, viewDirX, viewDirZ)))
            }
        } else {
            // Sphère : rayon uniforme en souterrain
            val r = renderRadiusCave; val r2 = r * r
            for (dz in -r..r)
                for (dy in -r..r)
                    for (dx in -r..r) {
                if (dx * dx + dy * dy + dz * dz > r2) continue
                val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
                if (cy > SURFACE_CY_MAX && cy < ISLAND_CY_MIN) continue
                val key = chunkKey(cx, cy, cz)
                if (!chunks.containsKey(key) && !inFlight.contains(key))
                    candidates.add(ChunkCandidate(cx, cy, cz, key, streamPriority(dx, dy, dz, viewDirX, viewDirZ)))
            }
        }

        // Stream nearest chunks first, with a small view-direction bonus.
        candidates.sortBy { it.priority }
        var scheduled = 0
        for (c in candidates) {
            if (scheduled >= maxNewChunks) break
            if (!inFlight.add(c.key)) continue
            val chunk = Chunk(c.cx, c.cy, c.cz)
            chunks[c.key] = chunk
            onNeedGenerate(chunk)
            scheduled++
        }

        // Déchargement : bounding-box cylindre en surface, sphère en souterrain
        if (isSurface) {
            val rxz = renderRadiusXZ + 2; val ry = renderRadiusYSurface + 2
            chunks.entries.filter { (_, c) ->
                !inFlight.contains(chunkKey(c.cx, c.cy, c.cz)) && (
                    abs(c.cx - pcx) > rxz || abs(c.cy - pcy) > ry || abs(c.cz - pcz) > rxz)
            }.forEach { (key, _) -> chunks.remove(key); inFlight.remove(key) }
        } else {
            val unloadR2 = (renderRadiusCave + 2).let { it * it }
            chunks.entries.filter { (_, c) ->
                val dx = c.cx - pcx; val dy = c.cy - pcy; val dz = c.cz - pcz
                !inFlight.contains(chunkKey(c.cx, c.cy, c.cz)) && dx * dx + dy * dy + dz * dz > unloadR2
            }.forEach { (key, _) -> chunks.remove(key); inFlight.remove(key) }
        }
        return candidates.size > scheduled
    }

    private data class ChunkCandidate(val cx: Int, val cy: Int, val cz: Int, val key: Long, val priority: Int)

    private fun streamPriority(dx: Int, dy: Int, dz: Int, viewDirX: Float, viewDirZ: Float): Int {
        val dist = dx * dx + dz * dz + dy * dy * 8
        val dot = dx * viewDirX + dz * viewDirZ
        return dist - (dot * renderRadiusXZ * 2).toInt()
    }

    fun abandonChunk(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        chunks.remove(key)
        inFlight.remove(key)
    }

    fun markGenerated(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        inFlight.remove(key)
        chunk.generated = true
        chunk.meshDirty = true
        enqueueLight(key)
        // Les fluides ne traversent jamais un chunk non généré. Quand ce chunk devient
        // disponible, on réveille uniquement les écoulements qui attendaient précisément
        // cette frontière, sans scanner tout son volume (important pour les océans).
        resumeDeferredWaterActivations(key)
        val neighbors = arrayOf(
            intArrayOf(chunk.cx-1,chunk.cy,chunk.cz), intArrayOf(chunk.cx+1,chunk.cy,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy-1,chunk.cz), intArrayOf(chunk.cx,chunk.cy+1,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy,chunk.cz-1), intArrayOf(chunk.cx,chunk.cy,chunk.cz+1)
        )
        for ((nx, ny, nz) in neighbors) {
            val nb = getChunk(nx, ny, nz) ?: continue
            if (nb.generated) {
                nb.meshDirty = true
                enqueueLight(nx, ny, nz)
            }
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
        enqueueLight(key)
        resumeDeferredWaterActivations(key)
        return chunk
    }

    fun findSpawnPoint(): FloatArray {
        val approxCy = (surfaceHeight(8.0, 8.0) / CHUNK_SIZE).toInt()

        // Prégenère les chunks dans le rayon de recherche
        for (dcy in 0 downTo -2) for (dcz in -1..1) for (dcx in -1..1)
            pregenerateChunk(dcx, (approxCy + dcy).coerceIn(0, SURFACE_CY_MAX), dcz)

        // Cherche un sol solide (non eau) dans un carré 32×32 centré sur (8,8)
        for (dcy in 0 downTo -2) {
            val cyCand = approxCy + dcy
            if (cyCand !in 0..SURFACE_CY_MAX) continue
            for (dz in -16..16) for (dx in -16..16) {
                val wx = 8 + dx; val wz = 8 + dz
                val chx = Math.floorDiv(wx, CHUNK_SIZE)
                val chz = Math.floorDiv(wz, CHUNK_SIZE)
                val chunk = getChunk(chx, cyCand, chz) ?: continue
                val lx = wx - chx * CHUNK_SIZE
                val lz = wz - chz * CHUNK_SIZE
                for (ly in CHUNK_SIZE - 1 downTo 1) {
                    val worldY = chunk.worldY + ly
                    if (worldY < SEA_LEVEL) continue   // jamais spawner sous la surface de l'eau
                    val below = chunk.blockAt(lx, ly - 1, lz)
                    if (chunk.blockAt(lx, ly, lz) == AIR
                        && below != AIR && !isDecoration(below) && !isWater(below))
                        return floatArrayOf(wx + 0.5f, worldY + 1.62f, wz + 0.5f)
                }
            }
        }

        // Aucun sol solide trouvé → île artificielle
        return buildSpawnIsland()
    }

    /** Construit une petite île rocailleuse au niveau de la mer et retourne la position de spawn. */
    private fun buildSpawnIsland(): FloatArray {
        val ox = 8; val oz = 8
        val topY = SEA_LEVEL  // surface de l'île au niveau de la mer

        // Prégenère les chunks couverts par l'île
        val botCy = Math.floorDiv(topY - 4, CHUNK_SIZE)
        val topCy = Math.floorDiv(topY + 2, CHUNK_SIZE)
        for (cy in botCy..topCy) for (dcz in -1..1) for (dcx in -1..1)
            pregenerateChunk(dcx, cy, dcz)

        // Île 5×5 : 2 couches de pierre, 1 de terre, 1 d'herbe
        for (dz in -2..2) for (dx in -2..2) {
            val wx = ox + dx; val wz = oz + dz
            setBlockRaw(wx, topY - 3, wz, STONE)
            setBlockRaw(wx, topY - 2, wz, STONE)
            setBlockRaw(wx, topY - 1, wz, DIRT)
            setBlockRaw(wx, topY,     wz, GRASS)
            // Efface l'eau au-dessus de l'île (WATER_FLOW peut occuper ces cases)
            for (dy in 1..4) {
                if (isWater(rawBlockAt(wx, topY + dy, wz))) setBlockRaw(wx, topY + dy, wz, AIR)
            }
        }

        // Quelques cailloux éparpillés sur l'île
        // rock_moss au centre, rocks sur les côtés
        setBlockRaw(ox,     topY + 1, oz,     ROCK_MOSS)
        setBlockRaw(ox - 1, topY + 1, oz + 1, ROCK)
        setBlockRaw(ox + 2, topY + 1, oz - 1, ROCK)
        setBlockRaw(ox - 2, topY + 1, oz - 2, ROCK_MOSS)
        setBlockRaw(ox + 1, topY + 1, oz + 2, ROCK)

        return floatArrayOf(ox + 0.5f, topY + 1 + 1.62f, oz + 0.5f)
    }

    // Lit un bloc en coordonnées monde sans générer de chunk
    private fun rawBlockAt(wx: Int, wy: Int, wz: Int): Short {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz) ?: return AIR
        return chunk.blockAt(wx - cx * CHUNK_SIZE, wy - cy * CHUNK_SIZE, wz - cz * CHUNK_SIZE)
    }

    // Place un bloc directement (bypass "generated" check, pour construction au spawn)
    private fun setBlockRaw(wx: Int, wy: Int, wz: Int, type: Short) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz) ?: return
        val lx = wx - cx * CHUNK_SIZE
        val ly = wy - cy * CHUNK_SIZE
        val lz = wz - cz * CHUNK_SIZE
        val old = chunk.blockAt(lx, ly, lz)
        if (old == type) return
        chunk.setBlock(lx, ly, lz, type)
        chunk.version++; chunk.meshDirty = true
        val key = chunkKey(cx, cy, cz)
        rebuildQueue.add(key)
        if (skyPassable(old) != skyPassable(type)) enqueueLightAndNeighbors(cx, cy, cz)
        storage?.recordChange(cx, cy, cz,
            lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
    }

    private fun setBlockRawIfAir(wx: Int, wy: Int, wz: Int, type: Short) {
        if (rawBlockAt(wx, wy, wz) == AIR) setBlockRaw(wx, wy, wz, type)
    }

    // ── Puits / failles vers les caves ───────────────────────────────────────

    // Retourne (lx, lz, rayon) du puits pour la colonne (cx,cz), ou null.
    // Déterministe : le même résultat pour tous les chunks de la colonne.
    private fun cavePitAt(cx: Int, cz: Int): Triple<Int, Int, Float>? {
        val rng = chunkRng(cx * 1031 + 17, 9999, cz * 1009 + 31)
        if (rng.nextFloat() > 0.14f) return null       // ~14% des colonnes ont un puits
        val lx = 2 + rng.nextInt(12)
        val lz = 2 + rng.nextInt(12)
        val radius = 2.5f + rng.nextFloat() * 2.5f     // ouverture 2.5 – 5 blocs
        return Triple(lx, lz, radius)
    }

    // Creuse le puits à travers tout le chunk en entonnoir (large en haut, étroit en bas).
    private fun carveSurfacePit(chunk: Chunk, lx: Int, lz: Int, topRadius: Float) {
        val wx = (chunk.worldX + lx).toDouble()
        val wz = (chunk.worldZ + lz).toDouble()
        val surfH = surfaceHeight(wx, wz).toInt()
        for (ly in 0 until CHUNK_SIZE) {
            val worldY = chunk.worldY + ly
            if (worldY > surfH + 1) continue            // ne dépasse pas la surface
            val depthBelow = (surfH - worldY + 1).toFloat()
            // Entonnoir : large en haut, se resserre jusqu'à 1.5 en profondeur
            val radius = (topRadius - depthBelow * 0.055f).coerceAtLeast(1.5f)
            carveInChunk(chunk, lx.toFloat() + 0.5f, ly.toFloat() + 0.5f, lz.toFloat() + 0.5f, radius)
        }
    }

    // ── Pipeline de génération ────────────────────────────────────────────────

    fun generate(chunk: Chunk) {
        when {
            chunk.cy in 0..SURFACE_CY_MAX                           -> generateSurface(chunk)
            chunk.cy < 0 && isUndergroundSurface(chunk.cy)         -> generateUndergroundSurface(chunk)
            chunk.cy < 0                                            -> generateCave(chunk)
            chunk.cy >= ISLAND_CY_MIN                               -> generateIsland(chunk)
            // sky void : chunk reste AIR (ByteArray initialisé à 0)
        }
        storage?.applyDiff(chunk)
        storage?.applyMetaDiff(chunk)
    }

    private val defaultCaveBiome: CaveBiomeDef by lazy {
        BiomeRegistry.caveBiomes.firstOrNull { it.id == "default" }
            ?: BiomeRegistry.caveBiomes.firstOrNull()
            ?: error("Aucun biome de cave chargé (caves/biomes/cave manquant ?)")
    }

    private fun generateCave(chunk: Chunk) {
        val bb = BiomeMap.biomeBlendAt(chunk.cx, chunk.cy, chunk.cz, seed)
        val biome = bb.primary
        applyRockLayers(chunk, bb)
        carveGigaCaves(chunk)
        for (dcz in -1..1) for (dcy in -1..1) for (dcx in -1..1)
            carveWormsFrom(chunk, chunk.cx + dcx, chunk.cy + dcy, chunk.cz + dcz)
        // Worms de surface qui descendent jusqu'ici
        for (dcz in -1..1) for (dcx in -1..1)
            carveVerticalWormsFrom(chunk, chunk.cx + dcx, chunk.cz + dcz)
        applyPockets(chunk, biome)
        applyOreVeins(chunk, biome)
        applyFloorSurface(chunk, bb)
        plantTrees(chunk, biome)
        applyDecorations(chunk, bb)
    }

    private fun generateSurface(chunk: Chunk) {
        val wy = chunk.worldY
        val biomes = BiomeRegistry.surfaceBiomes

        // Biome échantillonné AU BLOC : les frontières entre biomes suivent le champ de bruit
        // (courbes organiques) au lieu de s'aligner sur la grille 16×16 des chunks.
        val colBiome  = IntArray(CHUNK_SIZE * CHUNK_SIZE)
        val heights   = IntArray(CHUNK_SIZE * CHUNK_SIZE)
        val topBlocks = ShortArray(CHUNK_SIZE * CHUNK_SIZE)
        val capDepth  = IntArray(CHUNK_SIZE * CHUNK_SIZE)   // épaisseur du bloc de surface (neige…)
        // Biome échantillonné sur une grille BIOME_STEP×BIOME_STEP (1 argmax pour le groupe),
        // la hauteur reste calculée par bloc (octaves par bloc → relief lisse). surfaceHeight()
        // applique le même snap pour rester cohérent (structures, spawn, puits).
        val weights   = DoubleArray(biomes.size)
        for (cz0 in 0 until CHUNK_SIZE step BIOME_STEP) for (cx0 in 0 until CHUNK_SIZE step BIOME_STEP) {
            val dom = BiomeMap.biomeWeights(
                (chunk.worldX + cx0).toDouble(), (chunk.worldZ + cz0).toDouble(), seed, weights)
            val sb = biomes[dom]
            for (dz in 0 until BIOME_STEP) for (dx in 0 until BIOME_STEP) {
                val lx = cx0 + dx; val lz = cz0 + dz
                val i = lz * CHUNK_SIZE + lx
                colBiome[i] = dom
                val wx = (chunk.worldX + lx).toDouble()
                val wz = (chunk.worldZ + lz).toDouble()
                val h = blendedSurfaceHeight(wx, wz, weights).toInt()
                heights[i] = h
                val alt = if (h < SEA_LEVEL) null else altitudeBlock(sb, wx, wz, h)
                topBlocks[i] = when {
                    h < SEA_LEVEL -> SAND
                    alt != null   -> alt.block
                    else          -> surfaceNoiseBlock(sb, wx, wz)
                }
                capDepth[i] = alt?.thickness ?: 1
            }
        }

        // Remplissage des blocs : couche de surface (cap) / terre (topsoil) / pierre, par colonne
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val i = lz * CHUNK_SIZE + lx; val h = heights[i]; val top = topBlocks[i]
            val sb = biomes[colBiome[i]]
            val dirtB = sb.dirtBlock; val stoneB = sb.stoneBlock
            val cap = capDepth[i]; val soil = sb.topsoilDepth
            for (ly in 0 until CHUNK_SIZE) {
                val worldY = wy + ly
                chunk.setBlock(lx, ly, lz, when {
                    worldY > h                  -> AIR
                    worldY > h - cap            -> top
                    worldY > h - cap - soil     -> dirtB
                    else                        -> stoneB
                })
            }
        }

        // Puits / failles vers les caves (traverse tous les chunks de la colonne)
        val pit = cavePitAt(chunk.cx, chunk.cz)
        if (pit != null) carveSurfacePit(chunk, pit.first, pit.second, pit.third)

        // Remplissage eau sous le niveau de la mer
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val i = lz * CHUNK_SIZE + lx; val h = heights[i]
            if (h >= SEA_LEVEL) continue
            for (ly in 0 until CHUNK_SIZE) {
                val worldY = wy + ly
                if (worldY > h && worldY <= SEA_LEVEL) {
                    chunk.setBlock(lx, ly, lz, WATER)
                }
            }
        }

        // Entrées de grotte : worms depuis cy=-1 et cy=0 pour multiplier les connexions
        if (chunk.cy == 0) {
            carveWormsFrom(chunk, chunk.cx, -1, chunk.cz)
            carveWormsFrom(chunk, chunk.cx,  0, chunk.cz)
        }

        // Worms de surface → cave (descendent sous la surface depuis ce chunk)
        for (dcz in -1..1) for (dcx in -1..1)
            carveVerticalWormsFrom(chunk, chunk.cx + dcx, chunk.cz + dcz)

        applyOreVeins(chunk, defaultCaveBiome)
        plantSurfaceTrees(chunk, colBiome)
        plantSurfaceBushes(chunk, colBiome)
        applySurfaceDecorations(chunk, colBiome)
        applySurfaceVegetation(chunk, colBiome)
        placeStructuresInChunk(chunk)
    }

    // ── Surface souterraine ───────────────────────────────────────────────────
    // Même pipeline que generateSurface mais avec les biomes souterrains.
    // La bande fait US_HEIGHT_CY chunks (160 blocs). Le terrain occupe les ~2 chunks du bas
    // (hauteurs relative 2..50 blocs depuis le fond) → ~110 blocs d'air + plafond de roche
    // formé naturellement par la couche de cave au-dessus.

    private fun blendedUndergroundHeight(wx: Double, wz: Double, w: DoubleArray, bandBaseWorldY: Int): Double {
        val biomes = BiomeRegistry.undergroundBiomes
        var base = 0.0; var amp = 0.0; var rough = 0.0
        for (i in biomes.indices) {
            val wi = w[i]; if (wi == 0.0) continue
            base  += wi * biomes[i].heightBase
            amp   += wi * biomes[i].heightAmplitude
            rough += wi * biomes[i].heightRoughness
        }
        val s = (seed and 0xFFFFF).toDouble() * 0.0001 + 77777.0
        val nLow  = SimplexNoise.noise(wx * 0.0009 + s,         wz * 0.0009)
        val nMid  = SimplexNoise.noise(wx * 0.0035 + s + 120.0, wz * 0.0035)
        val nHigh = SimplexNoise.noise(wx * 0.0200 + s + 240.0, wz * 0.0200)
        val softAmp = amp * 0.42
        val softRough = rough * 0.28
        val maxRelH = (US_HEIGHT_CY * CHUNK_SIZE - 110).toDouble()
        val relH = (8.0 + base + nLow * softAmp + nMid * softAmp * 0.22 + nHigh * softRough).coerceIn(2.0, maxRelH)
        return bandBaseWorldY + relH
    }

    private fun generateUndergroundSurface(chunk: Chunk) {
        val bandBaseWorldY = undergroundBandBaseWorldY(chunk.cy)
        val maxTerrainWorldY = bandBaseWorldY + (US_HEIGHT_CY * CHUNK_SIZE - 110)
        // Chunks entièrement au-dessus du terrain max restent AIR (défaut de Chunk)
        if (chunk.worldY > maxTerrainWorldY) return

        val wy     = chunk.worldY
        val uBiomes = BiomeRegistry.undergroundBiomes
        val colBiome  = IntArray(CHUNK_SIZE * CHUNK_SIZE)
        val heights   = IntArray(CHUNK_SIZE * CHUNK_SIZE)
        val topBlocks = ShortArray(CHUNK_SIZE * CHUNK_SIZE)
        val weights   = DoubleArray(uBiomes.size)

        for (cz0 in 0 until CHUNK_SIZE step BIOME_STEP) for (cx0 in 0 until CHUNK_SIZE step BIOME_STEP) {
            val dom = BiomeMap.undergroundBiomeWeights(
                (chunk.worldX + cx0).toDouble(), (chunk.worldZ + cz0).toDouble(), seed, weights)
            val sb = uBiomes[dom]
            for (dz in 0 until BIOME_STEP) for (dx in 0 until BIOME_STEP) {
                val lx = cx0 + dx; val lz = cz0 + dz
                val i  = lz * CHUNK_SIZE + lx
                colBiome[i]  = dom
                val wx = (chunk.worldX + lx).toDouble()
                val wz = (chunk.worldZ + lz).toDouble()
                heights[i]   = blendedUndergroundHeight(wx, wz, weights, bandBaseWorldY).toInt()
                topBlocks[i] = surfaceNoiseBlock(sb, wx, wz)
            }
        }

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val i = lz * CHUNK_SIZE + lx; val h = heights[i]; val top = topBlocks[i]
            val sb = uBiomes[colBiome[i]]
            val soil = sb.topsoilDepth
            for (ly in 0 until CHUNK_SIZE) {
                val worldY = wy + ly
                chunk.setBlock(lx, ly, lz, when {
                    worldY > h          -> AIR
                    worldY == h         -> top
                    worldY >= h - soil  -> sb.dirtBlock
                    else                -> sb.stoneBlock
                })
            }
        }

        applyOreVeins(chunk, defaultCaveBiome)
        plantSurfaceTrees(chunk, colBiome, uBiomes)
        plantSurfaceBushes(chunk, colBiome, uBiomes)
        applySurfaceDecorations(chunk, colBiome, uBiomes)
        applySurfaceVegetation(chunk, colBiome, uBiomes)
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
        val islandIdx = BiomeMap.surfaceBiomeIndexAt(
            (chunk.worldX + CHUNK_SIZE / 2).toDouble(),
            (chunk.worldZ + CHUNK_SIZE / 2).toDouble(), seed)
        plantSurfaceTrees(chunk, IntArray(CHUNK_SIZE * CHUNK_SIZE) { islandIdx })
    }

    // ── Roche de base ─────────────────────────────────────────────────────────

    private fun applyRockLayers(chunk: Chunk, bb: BiomeBlend) {
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        val blendOffset = bb.blend * 0.09f
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            val nZone = SimplexNoise.noise(x * 0.012, y * 0.012, z * 0.012).toFloat()
            val blockPrimary: Short = if (nZone > bb.primary.secondaryNoiseThreshold) bb.primary.secondaryBlock else bb.primary.baseBlock
            val blockSecondary: Short = if (nZone + blendOffset > bb.secondary.secondaryNoiseThreshold) bb.secondary.secondaryBlock else bb.secondary.baseBlock
            val block = if (bb.blend > 0.01f && nZone < bb.blend - 0.5f) blockSecondary else blockPrimary
            chunk.setBlock(lx, ly, lz, block)
        }
    }

    // ── Giga-grottes ─────────────────────────────────────────────────────────
    // Grandes cavernes ellipsoïdales avec sol habité (biome souterrain) et stalactites.
    // Supercell 3D : 20×30×20 chunks. Chaque supercell génère au plus une giga-grotte.

    private fun carveGigaCaves(target: Chunk) {
        val gigaBiomes = BiomeRegistry.gigaCaveBiomes
        if (gigaBiomes.isEmpty()) return

        val superXZ = 20; val superY = 30
        val scx = Math.floorDiv(target.cx, superXZ)
        val scy = Math.floorDiv(target.cy, superY)
        val scz = Math.floorDiv(target.cz, superXZ)

        for (dscy in -1..1) for (dscz in -1..1) for (dscx in -1..1) {
            val sx = scx + dscx; val sy = scy + dscy; val sz = scz + dscz
            if (sy >= 0) continue

            val rng   = gigaCaveRng(sx, sy, sz)
            val biome = BiomeMap.gigaCaveBiomeAt(sx.toDouble(), sz.toDouble(), seed) ?: continue
            if (rng.nextFloat() > biome.chance) continue

            val cx = (sx * superXZ + 2 + rng.nextInt(superXZ - 4)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cy = (sy * superY  + 3 + rng.nextInt(superY  - 6)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f
            val cz = (sz * superXZ + 2 + rng.nextInt(superXZ - 4)).toFloat() * CHUNK_SIZE + CHUNK_SIZE * 0.5f

            if (isUndergroundSurface(Math.floorDiv(cy.toInt(), CHUNK_SIZE))) continue

            val hw = biome.widthMin  + rng.nextFloat() * (biome.widthMax  - biome.widthMin)
            val hh = biome.heightMin + rng.nextFloat() * (biome.heightMax - biome.heightMin)
            val hd = biome.depthMin  + rng.nextFloat() * (biome.depthMax  - biome.depthMin)

            val margin = 14f
            if (target.worldX + CHUNK_SIZE < cx - hw - margin || target.worldX > cx + hw + margin) continue
            if (target.worldY + CHUNK_SIZE < cy - hh - margin || target.worldY > cy + hh + margin) continue
            if (target.worldZ + CHUNK_SIZE < cz - hd - margin || target.worldZ > cz + hd + margin) continue

            val ox = target.worldX.toFloat()
            val oy = target.worldY.toFloat()
            val oz = target.worldZ.toFloat()
            val ns = (seed and 0xFFFFF).toDouble() * 0.0001 + sx * 0.07 + sz * 0.13

            val uBiomes   = BiomeRegistry.undergroundBiomes
            val floorIdx  = uBiomes.indexOfFirst { it.id == biome.floorBiome }
            val floorDef  = if (floorIdx >= 0) uBiomes[floorIdx] else null

            // 1. Creuse l'ellipsoïde avec bords organiques bruités
            for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
                val wx = ox + lx; val wy = oy + ly; val wz = oz + lz
                val dx = (wx - cx) / hw; val dy = (wy - cy) / hh; val dz = (wz - cz) / hd
                val dist2 = dx * dx + dy * dy + dz * dz
                if (dist2 > 1.35f) continue
                val nDisp = SimplexNoise.noise(wx.toDouble() * 0.025 + ns, wy.toDouble() * 0.03, wz.toDouble() * 0.025).toFloat()
                val limit = 1.0f + nDisp * 0.20f
                if (dist2 > limit * limit) continue
                target.setBlock(lx, ly, lz, AIR)
            }

            // 2. Sol : terrain surface-like au fond de l'ellipsoïde
            for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
                val wx = ox + lx; val wz = oz + lz
                val dx = (wx - cx) / hw; val dz = (wz - cz) / hd
                val xzD2 = dx * dx + dz * dz
                if (xzD2 >= 0.90f) continue

                val ellipseBot = cy - hh * sqrt(max(0f, 1f - xzD2))
                val noiseH = SimplexNoise.noise(wx.toDouble() * 0.05 + ns + 333.0, wz.toDouble() * 0.05).toFloat()
                val floorOffset = ((noiseH * 0.5f + 0.5f) * 0.55f + 0.10f).coerceIn(0.05f, 0.70f)
                val terrainWorldY = (ellipseBot + biome.floorHeightVariance * floorOffset).toInt()

                for (ly in CHUNK_SIZE - 1 downTo 0) {
                    val worldY = oy.toInt() + ly
                    if (worldY > terrainWorldY) continue
                    val block: Short = when {
                        worldY == terrainWorldY -> if (floorDef != null) surfaceNoiseBlock(floorDef, wx.toDouble(), wz.toDouble()) else GRASS
                        floorDef != null && worldY >= terrainWorldY - floorDef.topsoilDepth -> floorDef.dirtBlock
                        floorDef != null -> floorDef.stoneBlock
                        else -> STONE
                    }
                    target.setBlock(lx, ly, lz, block)
                }
            }

            // 3. Stalactites depuis le plafond
            if (biome.stalactiteDensity > 0f) {
                for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
                    val wx = ox + lx; val wz = oz + lz
                    val dx = (wx - cx) / hw; val dz = (wz - cz) / hd
                    if (dx * dx + dz * dz >= 0.82f) continue
                    val nStal = SimplexNoise.noise(wx.toDouble() * 0.14 + ns + 888.0, wz.toDouble() * 0.14).toFloat()
                    if (nStal < 1f - biome.stalactiteDensity * 2.5f) continue
                    val stalLen = (1 + ((nStal + 1f) * 4f).toInt()).coerceIn(1, 10)
                    for (ly in CHUNK_SIZE - 2 downTo 1) {
                        if (target.blockAt(lx, ly, lz) != AIR) continue
                        if (target.blockAt(lx, ly + 1, lz) == AIR) continue
                        repeat(stalLen) { dy ->
                            val sly = ly - dy
                            if (sly >= 0 && target.blockAt(lx, sly, lz) == AIR)
                                target.setBlock(lx, sly, lz, biome.stalactiteBlock)
                        }
                        break
                    }
                }
            }

            // 4. Végétation et arbres sur le sol
            if (floorIdx >= 0) {
                val colBiome = IntArray(CHUNK_SIZE * CHUNK_SIZE) { floorIdx }
                plantSurfaceTrees(target, colBiome, uBiomes)
                plantSurfaceBushes(target, colBiome, uBiomes)
                applySurfaceDecorations(target, colBiome, uBiomes)
                applySurfaceVegetation(target, colBiome, uBiomes)
            }
        }
    }

    private fun gigaCaveRng(sx: Int, sy: Int, sz: Int): Random {
        val s = seed xor (sx.toLong() * 2654435761L xor sy.toLong() * 2246822519L xor sz.toLong() * 3266489917L)
        return Random(s * 6364136223846793005L + 1442695040888963407L)
    }

    // ── Worms de cave (horizontaux) ───────────────────────────────────────────

    // Le biome est calculé depuis le chunk SEED (cx,cy,cz) et non depuis le chunk cible,
    // pour garantir que tous les chunks qui échantillonnent ce seed voient le même worm.
    private fun carveWormsFrom(target: Chunk, cx: Int, cy: Int, cz: Int) {
        val biome = BiomeMap.biomeAt(cx, cy, cz, seed)
        val rng = chunkRng(cx, cy, cz)
        // Un seul worm par chunk source, sauf 15 % de chance d'en avoir 2
        if (rng.nextFloat() < biome.wormZeroChance) return
        val wormCount = if (rng.nextFloat() < 0.15f) 2 else 1

        val bMargin = 11f
        val bMinX = target.worldX - bMargin; val bMaxX = target.worldX + CHUNK_SIZE + bMargin
        val bMinY = target.worldY - bMargin; val bMaxY = target.worldY + CHUNK_SIZE + bMargin
        val bMinZ = target.worldZ - bMargin; val bMaxZ = target.worldZ + CHUNK_SIZE + bMargin

        repeat(wormCount) {
            var curX = (cx * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var curY = (cy * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var curZ = (cz * CHUNK_SIZE + rng.nextInt(CHUNK_SIZE)).toFloat()
            var curYaw   = rng.nextFloat() * 2f * PI.toFloat()
            var curPitch = (rng.nextFloat() - 0.5f) * (PI / 3).toFloat()

            val baseRadius = if (rng.nextFloat() > 0.20f)
                2.5f + rng.nextFloat() * 1.5f   // 2.5–4.0 blocs (80 % des tunnels)
            else
                4.0f + rng.nextFloat() * 1.5f   // 4.0–5.5 blocs (20 % — couloirs larges)
            val mainLength = 100 + rng.nextInt(80)
            // Chaînage : jusqu'à 3 segments, chacun plus court, pour des réseaux denses
            val chainCount = when {
                rng.nextFloat() < 0.25f -> 0
                rng.nextFloat() < 0.50f -> 2
                else                    -> 1
            }

            repeat(1 + chainCount) { seg ->
                val segLen = if (seg == 0) mainLength else 70 + rng.nextInt(80)
                repeat(segLen) { step ->
                    val osc    = sin(step.toFloat() * 0.18f) * baseRadius * 0.20f
                    val radius = (baseRadius + osc).coerceIn(2.0f, 6.0f)
                    if (curX in bMinX..bMaxX && curY in bMinY..bMaxY && curZ in bMinZ..bMaxZ)
                        carveInChunk(target, curX, curY, curZ, radius)
                    curX += cos(curPitch) * sin(curYaw)
                    curY += sin(curPitch)
                    curZ += cos(curPitch) * cos(curYaw)
                    curYaw   += (rng.nextFloat() - 0.5f) * 0.45f
                    curPitch += (rng.nextFloat() - 0.5f) * 0.20f
                    curPitch  = curPitch.coerceIn((-PI / 2.5).toFloat(), (PI / 2.5).toFloat())
                }
            }
        }
    }

    // ── Worms de surface → cave (tire-bouchon, percent la surface puis descend ~100 blocs) ─

    private fun carveVerticalWormsFrom(target: Chunk, cx: Int, cz: Int) {
        val rng = chunkRng(cx * 7919 + 13, 88888, cz * 6271 + 7)
        if (rng.nextFloat() > 0.12f) return   // ~12 % des colonnes ont un worm de surface

        val startWx = (cx * CHUNK_SIZE + 4 + rng.nextInt(8)).toFloat()
        val startWz = (cz * CHUNK_SIZE + 4 + rng.nextInt(8)).toFloat()
        val surfH   = surfaceHeight(startWx.toDouble(), startWz.toDouble()).toFloat()
        // Évite les entrées sous-marines : eau de génération remplirait le trou
        if (surfH < SEA_LEVEL + 3) return

        // Départ : 10–18 blocs sous la surface ; le worm monte pour percer, puis descend
        val startDepth = 10f + rng.nextFloat() * 8f
        var wx = startWx; var wy = surfH - startDepth; var wz = startWz
        var yaw = rng.nextFloat() * 2f * PI.toFloat()
        // Pitch initial vers le haut → va percer la surface dans les 15–30 premiers pas
        var pitch = (PI / 5.0 + rng.nextDouble() * PI / 8.0).toFloat()

        val baseRadius = 2.0f + rng.nextFloat() * 1.5f
        val length     = 150 + rng.nextInt(80)            // 150–230 pas ≈ 100+ blocs de descente
        val uphaseLen  = 15 + rng.nextInt(15)             // durée de la phase montante

        val bMinX = target.worldX - baseRadius - 1f; val bMaxX = target.worldX + CHUNK_SIZE + baseRadius + 1f
        val bMinY = target.worldY - baseRadius - 1f; val bMaxY = target.worldY + CHUNK_SIZE + baseRadius + 1f
        val bMinZ = target.worldZ - baseRadius - 1f; val bMaxZ = target.worldZ + CHUNK_SIZE + baseRadius + 1f

        repeat(length) { step ->
            val osc    = sin(step.toFloat() * 0.20f) * baseRadius * 0.30f
            val radius = (baseRadius + osc).coerceIn(1.5f, 5.0f)
            if (wx in bMinX..bMaxX && wy in bMinY..bMaxY && wz in bMinZ..bMaxZ)
                carveInChunk(target, wx, wy, wz, radius)
            wx += cos(pitch) * sin(yaw)
            wy += sin(pitch)
            wz += cos(pitch) * cos(yaw)
            // Grand yaw → effet tire-bouchon prononcé
            yaw += (rng.nextFloat() - 0.5f) * 1.00f
            // Phase montante → descente progressive via interpolation vers pitch cible
            val targetPitch = if (step < uphaseLen) (PI / 5).toFloat() else (-PI / 2.5).toFloat()
            pitch += (targetPitch - pitch) * 0.06f + (rng.nextFloat() - 0.5f) * 0.20f
            pitch  = pitch.coerceIn((-PI * 0.75).toFloat(), (PI / 2.5).toFloat())
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

    private fun applyPockets(chunk: Chunk, biome: CaveBiomeDef) {
        if (!biome.pocketsEnabled) return
        val wx = chunk.worldX; val wy = chunk.worldY; val wz = chunk.worldZ
        for (lz in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue
            if (neighborBlock(chunk, lx, ly + 1, lz) == AIR) continue
            val x = (wx + lx).toDouble(); val y = (wy + ly).toDouble(); val z = (wz + lz).toDouble()
            val nPocket = SimplexNoise.noise(x * 0.065 + seed * 0.003, y * 0.065, z * 0.065)
            if (nPocket < 0.55) continue
            val nType = SimplexNoise.noise(x * 0.022 + seed * 0.005, y * 0.022, z * 0.022).toFloat()
            val pocketBlocks = biome.pocketBlocks
            if (pocketBlocks.isEmpty()) continue
            val pocket = pocketBlocks.firstOrNull { nType >= it.noiseMin }?.block
                ?: pocketBlocks.last().block
            chunk.setBlock(lx, ly, lz, pocket)
        }
    }

    // ── Minerais ─────────────────────────────────────────────────────────────

    private fun applyOreVeins(chunk: Chunk, biome: CaveBiomeDef) {
        val rng = chunkRng(chunk.cx * 11 + 7, chunk.cy * 13 + 3, chunk.cz * 17 + 5)
        val dist = chunkDist(chunk)

        val oreList: List<OreEntry> = when {
            biome.oreByDistance != null -> {
                biome.oreByDistance.firstOrNull { dist < it.distMax }?.ores
                    ?: biome.oreByDistance.last().ores
            }
            else -> biome.ores
        }

        for (entry in oreList) {
            if (entry.chance < 1.0f && rng.nextFloat() >= entry.chance) continue
            val repeats = entry.repeatMin + if (entry.repeatMax > entry.repeatMin) rng.nextInt(entry.repeatMax - entry.repeatMin + 1) else 0
            repeat(repeats) { placeOreBlob(chunk, rng, entry.block, entry.minCount, entry.maxCount) }
        }
    }

    private fun placeOreBlob(chunk: Chunk, rng: Random, ore: Short, minBlocks: Int, maxBlocks: Int) {
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

    private fun applyFloorSurface(chunk: Chunk, bb: BiomeBlend) {
        val biome = bb.primary
        val dist = chunkDist(chunk)
        if (dist > biome.floorSkipBeyondDist) return
        val floorBlocks = biome.floorBlocks
        if (floorBlocks.isEmpty()) return

        val wx = chunk.worldX.toDouble(); val wz = chunk.worldZ.toDouble()
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) for (ly in 0 until CHUNK_SIZE) {
            val b = chunk.blockAt(lx, ly, lz)
            if (b == AIR || b == LAVA) continue
            if (neighborBlock(chunk, lx, ly + 1, lz) != AIR) continue
            if (neighborBlock(chunk, lx, ly + 2, lz) != AIR) continue
            val x = wx + lx; val z = wz + lz
            val useSecondary = bb.blend > 0.05f &&
                SimplexNoise.noise(x * 0.07 + 555.0, 0.0, z * 0.07) < bb.blend.toDouble() - 0.5
            val activeFloor = if (useSecondary && bb.secondary.floorBlocks.isNotEmpty()) bb.secondary.floorBlocks else floorBlocks
            val nType = SimplexNoise.noise(x * 0.09 + seed * 0.002, 0.0, z * 0.09).toFloat()
            val terrain = activeFloor.firstOrNull { nType >= it.noiseMin }?.block ?: activeFloor.last().block
            chunk.setBlock(lx, ly, lz, terrain)
        }
    }

    // ── Arbres ────────────────────────────────────────────────────────────────

    private fun plantTrees(chunk: Chunk, biome: CaveBiomeDef) {
        if (!biome.treesEnabled) return
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

    private fun applyDecorations(chunk: Chunk, bb: BiomeBlend) {
        val biome = bb.primary
        if (biome.decorationBlocks.isEmpty()) return
        val rng = chunkRng(chunk.cx * 19 + 11, chunk.cy * 7 + 3, chunk.cz * 23 + 17)
        val density = biome.decorationDensity * (1f - bb.blend * 0.75f)
        if (density <= 0f) return
        val blocks = biome.decorationBlocks

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            if (rng.nextFloat() > density) continue
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b)) continue
                if (ly + 1 >= CHUNK_SIZE) break
                if (chunk.blockAt(lx, ly + 1, lz) != AIR) break
                val decor = blocks[rng.nextInt(blocks.size)]
                chunk.setBlock(lx, ly + 1, lz, decor)
                break
            }
        }
    }

    // ── Surface : heightmap et blocs ─────────────────────────────────────────

    internal fun surfaceHeight(wx: Double, wz: Double): Double {
        val w = DoubleArray(BiomeRegistry.surfaceBiomes.size)
        // Snap aligné sur la grille BIOME_STEP de generateSurface → même biome, donc même hauteur.
        val bx = floor(wx / BIOME_STEP) * BIOME_STEP
        val bz = floor(wz / BIOME_STEP) * BIOME_STEP
        BiomeMap.biomeWeights(bx, bz, seed, w)
        return blendedSurfaceHeight(wx, wz, w)
    }

    /**
     * Hauteur du sol en (wx,wz), pilotée par les paramètres de relief des biomes.
     * [w] = poids normalisés des biomes (cf. [BiomeMap.biomeWeights]). On mélange `heightBase`,
     * `heightAmplitude` et `heightRoughness` selon ces poids, puis on applique des octaves de
     * bruit communes (donc continues d'un biome à l'autre) modulées par l'amplitude mélangée :
     * pas de falaise aux frontières, mais un biome montagneux culmine très haut, un océan plonge.
     */
    private fun blendedSurfaceHeight(wx: Double, wz: Double, w: DoubleArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes): Double {
        var base = 0.0; var amp = 0.0; var rough = 0.0
        for (i in biomes.indices) {
            val wi = w[i]; if (wi == 0.0) continue
            base  += wi * biomes[i].heightBase
            amp   += wi * biomes[i].heightAmplitude
            rough += wi * biomes[i].heightRoughness
        }

        val s = (seed and 0xFFFFF).toDouble() * 0.0001
        val nLow  = SimplexNoise.noise(wx * 0.0013 + s,         wz * 0.0013)         // masse continentale
        val nMid  = SimplexNoise.noise(wx * 0.0060 + s + 120.0, wz * 0.0060)         // collines
        val nHigh = SimplexNoise.noise(wx * 0.0450 + s + 240.0, wz * 0.0450)         // détail fin
        val ridge = 1.0 - abs(SimplexNoise.noise(wx * 0.0040 + s + 500.0, wz * 0.0040)) // crêtes 0..1
        val ruggedness = smoothstep(50.0, 180.0, amp)
        val softAmp = amp * (0.28 + ruggedness * 0.52)
        val softRough = rough * (0.22 + ruggedness * 0.35)
        val peakGate = smoothstep(0.78, 0.97, ridge)

        val maxY = (SURFACE_CY_MAX + 1) * CHUNK_SIZE - 2.0
        return (SURFACE_BASE_Y + base
                + nLow  * softAmp
                + nMid  * softAmp * 0.22
                + peakGate * peakGate * amp * ruggedness * 0.38
                + nHigh * softRough)
            .coerceIn(6.0, maxY)
    }

    private fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** Entrée d'altitude correspondant à la surface en (wx,wz,h), ou null. Ligne légèrement bruitée. */
    private fun altitudeBlock(sb: SurfaceBiomeDef, wx: Double, wz: Double, h: Int): HeightBlockEntry? {
        if (sb.heightBlocks.isEmpty()) return null
        val hj = h + SimplexNoise.noise(wx * 0.05 + 800.0, wz * 0.05) * 3.0
        for (hb in sb.heightBlocks) if (hj >= hb.minHeight) return hb
        return null
    }

    private fun surfaceNoiseBlock(sb: SurfaceBiomeDef, wx: Double, wz: Double): Short {
        val n = SimplexNoise.noise(wx * sb.surfaceVarietyScale + sb.surfaceVarietyOffset, wz * sb.surfaceVarietyScale).toFloat()
        return sb.surfaceBlocks.firstOrNull { n >= it.noiseMin }?.block ?: sb.surfaceBlocks.last().block
    }

    // ── Surface : arbres groupés en forêts (biome résolu par colonne) ─────────

    private fun plantSurfaceTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        plantOakTrees(chunk, colBiome, biomes)
        plantBirchTrees(chunk, colBiome, biomes)
        plantDarkwoodTrees(chunk, colBiome, biomes)
        plantSapinTrees(chunk, colBiome, biomes)
        plantJungleTrees(chunk, colBiome, biomes)
        plantRedwoodTrees(chunk, colBiome, biomes)
        plantColoredTrees(chunk, colBiome, biomes)
    }

    private fun plantOakTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 7 + 3, chunk.cy + 100, chunk.cz * 13 + 5)

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "oak") continue
            if (sb.treeDensityBase <= 0f && sb.treeDensityNoiseScale <= 0f) continue
            val clusterN = SimplexNoise.noise(
                (chunk.worldX + lx).toDouble() * 0.007 + 900.0,
                (chunk.worldZ + lz).toDouble() * 0.007)
            val density = (sb.treeDensityBase + clusterN.toFloat() * sb.treeDensityNoiseScale).coerceIn(0.00f, 0.55f)
            if (rng.nextFloat() > density) continue
            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue
            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            val r = sb.canopyRadius
            if (grassY + trunkH + r + 1 >= CHUNK_SIZE) continue
            var blocked = false
            for (dy in 1..(trunkH + 2))
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue
            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD)
            val topY = grassY + trunkH
            val rr = r * r + 1
            for (dy in -1..r) for (dz in -r..r) for (dx in -r..r) {
                val lyl = topY + dy; val lxl = lx + dx; val lzl = lz + dz
                if (lxl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE) continue
                if (dx*dx + dy*dy + dz*dz > rr) continue
                if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES)
            }
        }
    }

    // ── Surface : décorations ─────────────────────────────────────────────────

    private fun applySurfaceDecorations(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 19 + 11, chunk.cy * 7 + 3, chunk.cz * 23 + 17)
        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.decorationBlocks.isEmpty()) continue
            if (rng.nextFloat() > sb.decorationDensity) continue
            val decor = sb.decorationBlocks[rng.nextInt(sb.decorationBlocks.size)]
            val wl = isWaterlogged(decor)
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                // Ne jamais poser sur de l'air, une autre décoration, du bois ou des feuilles
                if (b == AIR || isDecoration(b) || isLeaf(b) || isWood(b)) continue
                // Ne jamais poser une décoration non-waterlogged sur ou sous l'eau
                if (isWater(b) && !wl) break
                val above = if (ly + 1 < CHUNK_SIZE) chunk.blockAt(lx, ly + 1, lz) else AIR
                // Sol normal : la case au-dessus doit être vide
                // Sol waterlogged : la case au-dessus peut être eau ou vide
                if (above != AIR && !(wl && isWater(above))) break
                if (ly + 1 >= CHUNK_SIZE) break
                chunk.setBlock(lx, ly + 1, lz, decor)
                break
            }
        }
    }

    // ── Arbustes (feuilles transparentes) ────────────────────────────────────

    // ── Bouleaux ──────────────────────────────────────────────────────────────

    private fun plantBirchTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 23 + 11, chunk.cy + 400, chunk.cz * 43 + 7)

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "birch") continue
            if (sb.treeDensityBase <= 0f) continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            // Tronc paramétré par le biome, taillé à la hauteur disponible dans le chunk
            val maxTrunk = CHUNK_SIZE - grassY - 5   // 4 pour les feuilles du haut + 1 de marge
            val trunkH = (sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1)))
                .coerceAtMost(maxTrunk)
            if (trunkH < 6) continue

            // Vérifier espace libre pour le tronc
            var blocked = false
            for (dy in 1..trunkH + 1)
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue

            // Tronc
            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD_WHITE)

            val topY = grassY + trunkH

            // Gros patch de feuilles au sommet (sphère, rayon piloté par le biome)
            val topR = sb.canopyRadius + rng.nextInt(2)
            for (dy in -topR..(topR + 1)) for (dz in -topR..topR) for (dx in -topR..topR) {
                if (dx * dx + (dy - 1) * (dy - 1) + dz * dz > topR * topR) continue
                val ly = topY + dy; val lx2 = lx + dx; val lz2 = lz + dz
                if (lx2 !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lz2 !in 0 until CHUNK_SIZE) continue
                if (chunk.blockAt(lx2, ly, lz2) == AIR) chunk.setBlock(lx2, ly, lz2, LEAVES_FALL)
            }

            // Petits patches irréguliers au 1/3 et 2/3 du tronc
            for (patchFrac in listOf(trunkH / 3, 2 * trunkH / 3)) {
                val pY = grassY + patchFrac
                if (pY !in 0 until CHUNK_SIZE) continue
                for (dz in -2..2) for (dx in -2..2) {
                    if (abs(dx) + abs(dz) > 2 || (dx == 0 && dz == 0)) continue
                    if (rng.nextFloat() > 0.55f) continue
                    val lx2 = lx + dx; val lz2 = lz + dz
                    if (lx2 in 0 until CHUNK_SIZE && lz2 in 0 until CHUNK_SIZE &&
                        chunk.blockAt(lx2, pY, lz2) == AIR)
                        chunk.setBlock(lx2, pY, lz2, LEAVES_FALL)
                }
            }
        }
    }

    // ── Sapin : 1×1, haut (7-12), canopée conique, variante légèrement plus sombre que darkwood ──

    private fun plantSapinTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 31 + 17, chunk.cy + 900, chunk.cz * 41 + 19)

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "sapin") continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            if (grassY + trunkH + 4 >= CHUNK_SIZE) continue

            var blocked = false
            for (dy in 1..trunkH + 2)
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue

            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD_SAPIN)

            val topY = grassY + trunkH
            // Canopée conique : étages du bas vers le haut, rayon décroissant
            val layers = 6
            for (layer in 0 until layers) {
                val dy = layer - (layers - 1)
                val r = (layers - layer).coerceAtLeast(1)
                for (dz in -r..r) for (dx in -r..r) {
                    if (dx * dx + dz * dz > r * r) continue
                    val lxl = lx + dx; val lzl = lz + dz; val lyl = topY + dy
                    if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                    if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES_SAPIN)
                }
            }
            // Pointe
            if (topY + 1 < CHUNK_SIZE && chunk.blockAt(lx, topY + 1, lz) == AIR)
                chunk.setBlock(lx, topY + 1, lz, LEAVES_SAPIN)
        }
    }

    // ── Dark Wood : 2×2, court (6-8), canopée large et plate ─────────────────

    private fun plantDarkwoodTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 11 + 7, chunk.cy + 500, chunk.cz * 17 + 3)

        for (lz in 2 until CHUNK_SIZE - 3) for (lx in 2 until CHUNK_SIZE - 3) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "darkwood") continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            if (grassY + trunkH + 5 >= CHUNK_SIZE) continue

            // Vérifier espace libre pour les 4 colonnes du tronc
            var blocked = false
            outer@ for (dz in 0..1) for (dx in 0..1)
                for (dy in 1..trunkH + 1)
                    if (chunk.blockAt(lx + dx, grassY + dy, lz + dz) != AIR) { blocked = true; break@outer }
            if (blocked) continue

            // Tronc 2×2
            for (dy in 1..trunkH) for (dz in 0..1) for (dx in 0..1)
                chunk.setBlock(lx + dx, grassY + dy, lz + dz, WOOD_DARK)

            val topY = grassY + trunkH

            // Canopée : grande dalle plate (style dark oak Minecraft)
            // Couche top+1 : grand carré 7×7 avec coins arrondis
            // Couche top   : 5×5
            // Couche top-1 : 3×3 autour du tronc
            val cx = lx + 0 ; val cz = lz + 0  // coin bas-gauche du tronc

            for ((dy, radius) in listOf(Pair(2, 3), Pair(1, 2), Pair(0, 1))) {
                val r = radius
                for (dz in -r..r + 1) for (dx in -r..r + 1) {
                    // Couper les coins extrêmes pour un look plus naturel
                    if (abs(dx - 0) == r + 1 && abs(dz - 0) == r + 1) continue
                    if (abs(dx - 1) == r + 1 && abs(dz - 1) == r + 1) continue
                    val lxl = cx + dx; val lzl = cz + dz; val lyl = topY + dy
                    if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                    if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES_DARK)
                }
            }
            // Quelques feuilles qui pendent sous la canopée (dy=-1)
            for (dz in -1..2) for (dx in -1..2) {
                if (rng.nextFloat() > 0.45f) continue
                val lxl = cx + dx; val lzl = cz + dz; val lyl = topY - 1
                if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES_DARK)
            }
        }
    }

    // ── Jungle : 2×2 haut (12-20) avec branches + variante basse 1×1 (4-7) ──

    private fun plantJungleTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 13 + 5, chunk.cy + 600, chunk.cz * 19 + 11)

        for (lz in 2 until CHUNK_SIZE - 3) for (lx in 2 until CHUNK_SIZE - 3) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "jungle" && sb.treeType != "jungle_small") continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            val small = sb.treeType == "jungle_small"
            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            val canopyR = if (small) 2 else sb.canopyRadius
            if (grassY + trunkH + canopyR + 2 >= CHUNK_SIZE) continue

            if (small) {
                // 1×1, vérif simple
                var blocked = false
                for (dy in 1..trunkH + 2)
                    if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
                if (blocked) continue
                for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD_JUNGLE)
            } else {
                // 2×2, vérif sur les 4 colonnes
                var blocked = false
                outer@ for (dz in 0..1) for (dx in 0..1)
                    for (dy in 1..trunkH + 2)
                        if (chunk.blockAt(lx + dx, grassY + dy, lz + dz) != AIR) { blocked = true; break@outer }
                if (blocked) continue
                for (dy in 1..trunkH) for (dz in 0..1) for (dx in 0..1)
                    chunk.setBlock(lx + dx, grassY + dy, lz + dz, WOOD_JUNGLE)
            }

            val topY = grassY + trunkH
            val rr = canopyR * canopyR + 1

            // Canopée sphérique au sommet
            for (dy in -canopyR..canopyR) for (dz in -canopyR..canopyR) for (dx in -canopyR..canopyR) {
                if (dx * dx + dy * dy + dz * dz > rr) continue
                val ox = if (small) lx else lx + 1
                val oz = if (small) lz else lz + 1
                val lxl = ox + dx; val lzl = oz + dz; val lyl = topY + dy
                if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES_JUNGLE)
            }

            // Branches intermédiaires (seulement sur les grands arbres)
            if (!small && trunkH >= 10) {
                for (branch in 0..1) {
                    val branchY = grassY + (trunkH * (if (branch == 0) 2 else 3)) / 4
                    if (branchY !in 0 until CHUNK_SIZE) continue
                    val bDx = (rng.nextInt(5) - 2)
                    val bDz = (rng.nextInt(5) - 2)
                    for (dz in -2..2) for (dx in -2..2) {
                        if (dx * dx + dz * dz > 5) continue
                        if (rng.nextFloat() > 0.7f) continue
                        val lxl = lx + 1 + bDx + dx; val lzl = lz + 1 + bDz + dz
                        if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE) continue
                        if (chunk.blockAt(lxl, branchY, lzl) == AIR) chunk.setBlock(lxl, branchY, lzl, LEAVES_JUNGLE)
                    }
                }
            }
        }
    }

    // ── Redwood : 1×1, haut (8-12), canopée conique ───────────────────────────

    private fun plantRedwoodTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 17 + 9, chunk.cy + 700, chunk.cz * 23 + 13)

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            if (sb.treeType != "redwood") continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            if (grassY + trunkH + 4 >= CHUNK_SIZE) continue

            var blocked = false
            for (dy in 1..trunkH + 2)
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue

            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, WOOD_RED)

            // Canopée conique : rayon max en bas, réduit vers le sommet
            val topY = grassY + trunkH
            val layers = 5
            for (layer in 0 until layers) {
                val dy = layer - (layers - 1)   // de -(layers-1) à 0
                val r = (layers - 1 - layer) + 1  // rayon décroît vers le haut
                for (dz in -r..r) for (dx in -r..r) {
                    if (dx * dx + dz * dz > r * r + r / 2) continue
                    val lxl = lx + dx; val lzl = lz + dz; val lyl = topY + dy
                    if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                    if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, LEAVES_RED)
                }
            }
            // Pointe au sommet
            if (topY + 1 < CHUNK_SIZE && chunk.blockAt(lx, topY + 1, lz) == AIR)
                chunk.setBlock(lx, topY + 1, lz, LEAVES_RED)
        }
    }

    // ── Arbres colorés : 1×1, 5-8 blocs, petite sphère ──────────────────────

    private fun plantColoredTrees(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 29 + 13, chunk.cy + 800, chunk.cz * 37 + 7)

        val treeTypeToBlocks = mapOf(
            "blue"   to Pair(WOOD_BLUE,   LEAVES_BLUE),
            "purple" to Pair(WOOD_PURPLE, LEAVES_PURPLE),
            "yellow" to Pair(WOOD_YELLOW, LEAVES_YELLOW),
            "pink"   to Pair(WOOD_PINK,   LEAVES_PINK),
        )

        for (lz in 2 until CHUNK_SIZE - 2) for (lx in 2 until CHUNK_SIZE - 2) {
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]
            val (woodBlock, leavesBlock) = treeTypeToBlocks[sb.treeType] ?: continue
            if (rng.nextFloat() > sb.treeDensityBase) continue

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0)
                if (chunk.blockAt(lx, ly, lz) == GRASS) { grassY = ly; break }
            if (grassY < 0) continue

            val trunkH = sb.treeMinHeight + rng.nextInt((sb.treeMaxHeight - sb.treeMinHeight + 1).coerceAtLeast(1))
            if (grassY + trunkH + 3 >= CHUNK_SIZE) continue

            var blocked = false
            for (dy in 1..trunkH + 2)
                if (chunk.blockAt(lx, grassY + dy, lz) != AIR) { blocked = true; break }
            if (blocked) continue

            for (dy in 1..trunkH) chunk.setBlock(lx, grassY + dy, lz, woodBlock)

            val topY = grassY + trunkH
            val r = sb.canopyRadius
            val rr = r * r + 1
            for (dy in -1..r) for (dz in -r..r) for (dx in -r..r) {
                if (dx * dx + (dy - r / 2) * (dy - r / 2) + dz * dz > rr) continue
                val lxl = lx + dx; val lzl = lz + dz; val lyl = topY + dy
                if (lxl !in 0 until CHUNK_SIZE || lzl !in 0 until CHUNK_SIZE || lyl !in 0 until CHUNK_SIZE) continue
                if (chunk.blockAt(lxl, lyl, lzl) == AIR) chunk.setBlock(lxl, lyl, lzl, leavesBlock)
            }
        }
    }

    private fun plantSurfaceBushes(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng = chunkRng(chunk.cx * 41 + 17, chunk.cy + 300, chunk.cz * 53 + 29)
        val surfaceSets = arrayOfNulls<Set<Short>>(biomes.size)

        for (lz in 1 until CHUNK_SIZE - 1) for (lx in 1 until CHUNK_SIZE - 1) {
            val bi = colBiome[lz * CHUNK_SIZE + lx]
            val sb = biomes[bi]
            val bushBlock = sb.bushBlock ?: continue
            if (sb.bushDensity <= 0f) continue
            if (rng.nextFloat() > sb.bushDensity) continue
            val surfaceBlockSet = surfaceSets[bi]
                ?: sb.surfaceBlocks.map { it.block }.toSet().also { surfaceSets[bi] = it }

            var surfaceY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b in surfaceBlockSet) {
                    if (ly + 3 < CHUNK_SIZE && chunk.blockAt(lx, ly + 1, lz) == AIR) surfaceY = ly
                    break
                }
            }
            if (surfaceY < 0) continue

            val fullBush = rng.nextFloat() > 0.40f
            if (fullBush) {
                for ((dx, dz) in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val bx = lx + dx; val bz = lz + dz
                    if (bx in 0 until CHUNK_SIZE && bz in 0 until CHUNK_SIZE &&
                        chunk.blockAt(bx, surfaceY + 1, bz) == AIR)
                        chunk.setBlock(bx, surfaceY + 1, bz, bushBlock)
                }
                if (chunk.blockAt(lx, surfaceY + 2, lz) == AIR)
                    chunk.setBlock(lx, surfaceY + 2, lz, bushBlock)
            } else {
                chunk.setBlock(lx, surfaceY + 1, lz, bushBlock)
            }
        }
    }

    // ── Végétation de surface (herbes folles + blé sauvage) ──────────────────

    private fun applySurfaceVegetation(chunk: Chunk, colBiome: IntArray, biomes: List<SurfaceBiomeDef> = BiomeRegistry.surfaceBiomes) {
        val rng  = chunkRng(chunk.cx * 31 + 7, chunk.cy + 200, chunk.cz * 37 + 13)
        val wx0  = chunk.worldX.toDouble()
        val wz0  = chunk.worldZ.toDouble()

        for (lz in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            if (lz + 1 >= CHUNK_SIZE) continue
            val sb = biomes[colBiome[lz * CHUNK_SIZE + lx]]

            var grassY = -1
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b)) continue
                if (b == GRASS && ly + 1 < CHUNK_SIZE && chunk.blockAt(lx, ly + 1, lz) == AIR)
                    grassY = ly
                break
            }
            if (grassY < 0) continue

            val wx = wx0 + lx; val wz = wz0 + lz

            // ── Végétation ─────────────────────────────────────────────────
            if (sb.vegetationBlocks.isNotEmpty() && sb.vegetationDensity > 0f) {
                val clusterN = SimplexNoise.noise(wx * 0.09 + 300.0, wz * 0.09)
                if (clusterN > 0.10 && rng.nextFloat() < sb.vegetationDensity) {
                    val totalWeight = sb.vegetationBlocks.sumOf { it.weight }
                    val roll = rng.nextInt(totalWeight)
                    var acc = 0; var decor = sb.vegetationBlocks[0].block
                    for (ve in sb.vegetationBlocks) { acc += ve.weight; if (roll < acc) { decor = ve.block; break } }
                    chunk.setBlock(lx, grassY + 1, lz, decor)
                    continue
                }
            }

            // ── Blé sauvage ─────────────────────────────────────────────────
            if (!sb.wheatEnabled) continue
            val wheatN = SimplexNoise.noise(wx * 0.045 + 500.0, wz * 0.045)
            if (wheatN < 0.40 || rng.nextFloat() > 0.55f) continue
            val maturity = SimplexNoise.noise(wx * 0.018 + 700.0, wz * 0.018)
            val wheat: Short = when {
                maturity >  0.30 -> WHEAT4
                maturity >  0.00 -> WHEAT3
                maturity > -0.30 -> WHEAT2
                else             -> WHEAT1
            }
            chunk.setBlock(lx, grassY + 1, lz, wheat)
        }
    }

    // ── Structures (maisons, ruines…) ────────────────────────────────────────

    // Espacement : cellule de 10 chunks (~160 blocs), 50 % de chance par cellule.
    private val STRUCT_SUPER = 10

    private fun placeStructuresInChunk(chunk: Chunk) {
        val scx = Math.floorDiv(chunk.cx, STRUCT_SUPER)
        val scz = Math.floorDiv(chunk.cz, STRUCT_SUPER)
        for (dscz in -1..1) for (dscx in -1..1) {
            val sx = scx + dscx; val sz = scz + dscz
            val rng = structRng(sx, sz)
            if (rng.nextFloat() > 0.50f) continue

            // Position d'origine de la structure (coin bas-gauche, en coordonnées chunk)
            val originCx = sx * STRUCT_SUPER + 1 + rng.nextInt(STRUCT_SUPER - 2)
            val originCz = sz * STRUCT_SUPER + 1 + rng.nextInt(STRUCT_SUPER - 2)

            // Biome à l'emplacement de la structure (pas celui du chunk courant)
            val originWx = originCx * CHUNK_SIZE
            val originWz = originCz * CHUNK_SIZE
            val originSb = BiomeMap.surfaceBiomeAt(
                (originWx + 7).toDouble(), (originWz + 7).toDouble(), seed)
            // 30 % de chance d'utiliser une structure joueur si disponible, sinon une du biome
            val userList = StructureRegistry.userStructures
            val structDef = if (userList.isNotEmpty() && rng.nextFloat() < 0.30f)
                userList[rng.nextInt(userList.size)]
            else pickBiomeStructure(originSb, rng) ?: continue

            // Y = surface au centre de la structure
            val originWy = surfaceHeight(
                (originWx + structDef.sizeX / 2).toDouble(),
                (originWz + structDef.sizeZ / 2).toDouble()
            ).toInt() + 1

            // Test d'intersection avec ce chunk (AABB)
            if (chunk.worldX + CHUNK_SIZE <= originWx) continue
            if (chunk.worldX >= originWx + structDef.sizeX) continue
            if (chunk.worldZ + CHUNK_SIZE <= originWz) continue
            if (chunk.worldZ >= originWz + structDef.sizeZ) continue
            if (chunk.worldY + CHUNK_SIZE <= originWy) continue
            if (chunk.worldY >= originWy + structDef.sizeY) continue

            // Appliquer les blocs qui tombent dans ce chunk
            for (blk in structDef.blocks) {
                val wx = originWx + blk[0]; val wy = originWy + blk[1]; val wz = originWz + blk[2]
                val lx = wx - chunk.worldX; val ly = wy - chunk.worldY; val lz = wz - chunk.worldZ
                if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
                    chunk.setBlock(lx, ly, lz, blk[3].toShort())
            }
        }
    }

    /** Choisit une structure du biome par tirage pondéré, ou null si le biome n'en a pas / nom inconnu. */
    private fun pickBiomeStructure(sb: SurfaceBiomeDef, rng: Random): StructureDef? {
        val list = sb.structures
        if (list.isEmpty()) return null
        val total = list.sumOf { it.weight }
        if (total <= 0) return null
        var roll = rng.nextInt(total)
        for (e in list) { roll -= e.weight; if (roll < 0) return StructureRegistry.byName(e.name) }
        return StructureRegistry.byName(list.last().name)
    }

    private fun structRng(sx: Int, sz: Int): Random {
        val s = seed xor (sx.toLong() * 987654321L xor sz.toLong() * 123456789L)
        return Random(s * 6364136223846793005L + 1442695040888963407L)
    }

    private fun superCellRng(sx: Int, sz: Int): Random {
        val s = seed xor (sx.toLong() * 374761393L xor sz.toLong() * 1234567891L)
        return Random(s * 6364136223846793005L + 1442695040888963407L)
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private fun chunkDist(chunk: Chunk) =
        sqrt((chunk.cx.toLong() * chunk.cx + chunk.cy.toLong() * chunk.cy + chunk.cz.toLong() * chunk.cz).toDouble()).toFloat()

    private fun chunkRng(cx: Int, cy: Int, cz: Int): Random {
        val s = seed xor (chunkKey(cx, cy, cz) * 6364136223846793005L + 1442695040888963407L)
        return Random(s)
    }

    // ── Modification de blocs ─────────────────────────────────────────────────

    private fun skyPassable(block: Short): Boolean =
        block == AIR || isTransparent(block) || isDecoration(block) || isWater(block) || isLeaf(block)

    private fun enqueueLightAndNeighbors(cx: Int, cy: Int, cz: Int) {
        enqueueLight(cx, cy, cz)
        enqueueLight(cx - 1, cy, cz)
        enqueueLight(cx + 1, cy, cz)
        enqueueLight(cx, cy - 1, cz)
        enqueueLight(cx, cy + 1, cz)
        enqueueLight(cx, cy, cz - 1)
        enqueueLight(cx, cy, cz + 1)
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, type: Short) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        val old = chunk.blockAt(lx, ly, lz)
        if (old == type) return
        chunk.setBlock(lx, ly, lz, type)
        if (old == WATER_FLOW) clearWaterFlowLevel(wx, wy, wz)
        if (type == WATER_FLOW) setWaterFlowLevel(wx, wy, wz, 1)
        chunk.version++
        val key = chunkKey(cx, cy, cz)
        chunk.meshDirty = true
        rebuildQueue.add(key)
        if (skyPassable(old) != skyPassable(type)) enqueueLightAndNeighbors(cx, cy, cz)
        storage?.recordChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
        for ((nx, ny, nz) in arrayOf(
            intArrayOf(cx-1,cy,cz), intArrayOf(cx+1,cy,cz),
            intArrayOf(cx,cy-1,cz), intArrayOf(cx,cy+1,cz),
            intArrayOf(cx,cy,cz-1), intArrayOf(cx,cy,cz+1)
        )) {
            val n = getChunk(nx, ny, nz) ?: continue
            if (n.generated) { n.meshDirty = true; rebuildQueue.add(chunkKey(nx, ny, nz)) }
        }
        // La physique de l'eau est entièrement événementielle : un bloc de terrain
        // ajouté/enlevé ne réveille que son voisinage immédiat.
        activateWaterNeighborhood(wx, wy, wz)
    }

    fun setMeta(wx: Int, wy: Int, wz: Int, value: Byte) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        chunk.setMeta(lx, ly, lz, value)
        storage?.recordMetaChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, value)
    }

    fun metaAt(wx: Int, wy: Int, wz: Int): Byte {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz) ?: return 0
        if (!chunk.generated) return 0
        return chunk.metaAt(wx - cx * CHUNK_SIZE, wy - cy * CHUNK_SIZE, wz - cz * CHUNK_SIZE)
    }

    // ── Simulation eau ────────────────────────────────────────────────────────

    // Les océans sont déjà des sources stables. On ne les parcourt donc jamais : une cellule
    // d'eau n'est calculée qu'après une modification de bloc dans son voisinage immédiat.
    private val waterFlowLevels = ConcurrentHashMap<Long, Byte>()
    private val waterSpreadQueue = ConcurrentLinkedQueue<IntArray>() // [wx, wy, wz]
    private val waterQueued = ConcurrentHashMap.newKeySet<Long>()
    private val deferredWaterActivations = ConcurrentHashMap<Long, ConcurrentLinkedQueue<IntArray>>()
    private val horizontalWaterDirs = arrayOf(
        intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
        intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
    )
    private val waterNeighborDirs = arrayOf(
        intArrayOf(0, -1, 0), intArrayOf(0, 1, 0),
        *horizontalWaterDirs
    )
    private val MAX_WATER_FLOW_LEVEL = 8

    /** Niveau de flux : 0 pour une source ou une chute, 1..8 pour une eau qui s'étale. */
    fun waterFlowLevel(wx: Int, wy: Int, wz: Int): Int =
        waterFlowLevelKnown(blockAt(wx, wy, wz), wx, wy, wz)

    /** Variante pour le maillage, qui connaît déjà le type du bloc. */
    fun waterFlowLevelKnown(blockType: Short, wx: Int, wy: Int, wz: Int): Int = when (blockType) {
        WATER -> 0
        WATER_FLOW -> cachedWaterFlowLevel(wx, wy, wz)
        else -> 0
    }

    private fun waterKey(wx: Int, wy: Int, wz: Int): Long =
        (wx.toLong() and 0x3FFFFF) or
        ((wy.toLong() and 0x3FFFFF) shl 22) or
        ((wz.toLong() and 0x3FFFFF) shl 44)

    private fun isGeneratedBlock(wx: Int, wy: Int, wz: Int): Boolean {
        val chunk = getChunk(
            Math.floorDiv(wx, CHUNK_SIZE),
            Math.floorDiv(wy, CHUNK_SIZE),
            Math.floorDiv(wz, CHUNK_SIZE)
        )
        return chunk?.generated == true
    }

    private fun cachedWaterFlowLevel(wx: Int, wy: Int, wz: Int): Int {
        val key = waterKey(wx, wy, wz)
        return waterFlowLevels[key]?.toInt()?.and(0xFF)
            ?: (metaAt(wx, wy, wz).toInt() and 0xFF)
    }

    // Le niveau est aussi sauvegardé dans meta : les cascades gardent leur forme après un
    // rechargement du monde, sans conserver un état de simulation global pour chaque océan.
    private fun setWaterFlowLevel(wx: Int, wy: Int, wz: Int, level: Int) {
        val clamped = level.coerceIn(0, MAX_WATER_FLOW_LEVEL)
        val key = waterKey(wx, wy, wz)
        waterFlowLevels[key] = clamped.toByte()
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        if ((chunk.metaAt(lx, ly, lz).toInt() and 0xFF) == clamped) return
        chunk.setMeta(lx, ly, lz, clamped.toByte())
        storage?.recordMetaChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, clamped.toByte())
    }

    private fun clearWaterFlowLevel(wx: Int, wy: Int, wz: Int) {
        waterFlowLevels.remove(waterKey(wx, wy, wz))
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        if (chunk.metaAt(lx, ly, lz) == 0.toByte()) return
        chunk.setMeta(lx, ly, lz, 0)
        storage?.recordMetaChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, 0)
    }

    private fun markWaterMeshDirty(wx: Int, wy: Int, wz: Int) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        chunk.waterVersion++
        chunk.waterMeshDirty = true
        waterRebuildQueue.add(chunkKey(cx, cy, cz))
        fun markNeighbor(ncx: Int, ncy: Int, ncz: Int) {
            val neighbor = getChunk(ncx, ncy, ncz) ?: return
            if (neighbor.generated) {
                neighbor.waterMeshDirty = true
                waterRebuildQueue.add(chunkKey(ncx, ncy, ncz))
            }
        }
        if (lx == 0) markNeighbor(cx - 1, cy, cz)
        if (lx == CHUNK_SIZE - 1) markNeighbor(cx + 1, cy, cz)
        if (ly == 0) markNeighbor(cx, cy - 1, cz)
        if (ly == CHUNK_SIZE - 1) markNeighbor(cx, cy + 1, cz)
        if (lz == 0) markNeighbor(cx, cy, cz - 1)
        if (lz == CHUNK_SIZE - 1) markNeighbor(cx, cy, cz + 1)
    }

    /** Modifie seulement le mesh eau : les faces solides restent valides face à eau ou air. */
    private fun setWaterBlock(wx: Int, wy: Int, wz: Int, type: Short): Boolean {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return false
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        val old = chunk.blockAt(lx, ly, lz)
        if (old == type) return false
        if (old == WATER_FLOW) clearWaterFlowLevel(wx, wy, wz)
        chunk.setBlock(lx, ly, lz, type)
        markWaterMeshDirty(wx, wy, wz)
        storage?.recordChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
        return true
    }

    private fun queueWaterUpdate(wx: Int, wy: Int, wz: Int) {
        if (!isGeneratedBlock(wx, wy, wz)) return
        val key = waterKey(wx, wy, wz)
        if (waterQueued.add(key)) waterSpreadQueue.add(intArrayOf(wx, wy, wz))
    }

    private fun activateWaterNeighborhood(wx: Int, wy: Int, wz: Int) {
        queueWaterUpdate(wx, wy, wz)
        for (d in waterNeighborDirs) queueWaterUpdate(wx + d[0], wy + d[1], wz + d[2])
    }

    fun onWaterSourcePlaced(wx: Int, wy: Int, wz: Int) {
        clearWaterFlowLevel(wx, wy, wz)
        activateWaterNeighborhood(wx, wy, wz)
    }

    fun onWaterSourceRemoved(wx: Int, wy: Int, wz: Int) {
        activateWaterNeighborhood(wx, wy, wz)
    }

    private fun deferWaterActivation(sourceX: Int, sourceY: Int, sourceZ: Int, targetX: Int, targetY: Int, targetZ: Int) {
        val targetKey = chunkKey(
            Math.floorDiv(targetX, CHUNK_SIZE),
            Math.floorDiv(targetY, CHUNK_SIZE),
            Math.floorDiv(targetZ, CHUNK_SIZE)
        )
        deferredWaterActivations.getOrPut(targetKey) { ConcurrentLinkedQueue() }
            .add(intArrayOf(sourceX, sourceY, sourceZ))
    }

    private fun resumeDeferredWaterActivations(chunkKey: Long) {
        val waiting = deferredWaterActivations.remove(chunkKey) ?: return
        while (true) {
            val source = waiting.poll() ?: break
            activateWaterNeighborhood(source[0], source[1], source[2])
        }
    }

    private fun setFlowWater(wx: Int, wy: Int, wz: Int, level: Int): Boolean {
        if (!isGeneratedBlock(wx, wy, wz)) return false
        val current = blockAt(wx, wy, wz)
        if (current != AIR && current != WATER_FLOW) return false
        val normalized = level.coerceIn(0, MAX_WATER_FLOW_LEVEL)
        val oldLevel = if (current == WATER_FLOW) cachedWaterFlowLevel(wx, wy, wz) else -1
        if (current == WATER_FLOW && oldLevel == normalized) return false
        if (current == AIR && !setWaterBlock(wx, wy, wz, WATER_FLOW)) return false
        setWaterFlowLevel(wx, wy, wz, normalized)
        if (current == WATER_FLOW) markWaterMeshDirty(wx, wy, wz)
        activateWaterNeighborhood(wx, wy, wz)
        return true
    }

    private fun sourceNeighborCount(wx: Int, wy: Int, wz: Int): Int {
        var count = 0
        for (d in horizontalWaterDirs) {
            if (blockAt(wx + d[0], wy, wz + d[2]) == WATER) count++
        }
        return count
    }

    private fun canBecomeWaterSource(wx: Int, wy: Int, wz: Int): Boolean {
        if (sourceNeighborCount(wx, wy, wz) < 2) return false
        if (!isGeneratedBlock(wx, wy - 1, wz)) {
            deferWaterActivation(wx, wy, wz, wx, wy - 1, wz)
            return false
        }
        // Une source se forme sur un support, mais pas au milieu d'une chute d'eau.
        val below = blockAt(wx, wy - 1, wz)
        return below != AIR && below != WATER_FLOW
    }

    private fun incomingWaterLevel(wx: Int, wy: Int, wz: Int): Int? {
        var best = Int.MAX_VALUE
        val above = blockAt(wx, wy + 1, wz)
        if (isWater(above)) best = min(best, waterFlowLevelKnown(above, wx, wy + 1, wz))
        for (d in horizontalWaterDirs) {
            val nx = wx + d[0]; val nz = wz + d[2]
            when (blockAt(nx, wy, nz)) {
                WATER -> best = min(best, 1)
                WATER_FLOW -> {
                    val next = cachedWaterFlowLevel(nx, wy, nz) + 1
                    if (next <= MAX_WATER_FLOW_LEVEL) best = min(best, next)
                }
            }
        }
        return best.takeIf { it != Int.MAX_VALUE }
    }

    private fun hasUnloadedWaterInput(wx: Int, wy: Int, wz: Int): Boolean {
        var hasMissingInput = false
        if (!isGeneratedBlock(wx, wy + 1, wz)) {
            deferWaterActivation(wx, wy, wz, wx, wy + 1, wz)
            hasMissingInput = true
        }
        for (d in horizontalWaterDirs) {
            val nx = wx + d[0]; val nz = wz + d[2]
            if (!isGeneratedBlock(nx, wy, nz)) {
                deferWaterActivation(wx, wy, wz, nx, wy, nz)
                hasMissingInput = true
            }
        }
        return hasMissingInput
    }

    private fun spreadWaterFrom(wx: Int, wy: Int, wz: Int, level: Int) {
        val belowY = wy - 1
        if (!isGeneratedBlock(wx, belowY, wz)) {
            deferWaterActivation(wx, wy, wz, wx, belowY, wz)
            return
        }
        if (blockAt(wx, belowY, wz) == AIR) {
            setFlowWater(wx, belowY, wz, level)
            return
        }
        if (level >= MAX_WATER_FLOW_LEVEL) return
        val nextLevel = level + 1
        for (d in horizontalWaterDirs) {
            val nx = wx + d[0]; val nz = wz + d[2]
            if (!isGeneratedBlock(nx, wy, nz)) {
                deferWaterActivation(wx, wy, wz, nx, wy, nz)
                continue
            }
            if (blockAt(nx, wy, nz) == AIR) setFlowWater(nx, wy, nz, nextLevel)
        }
    }

    private fun updateWaterAt(wx: Int, wy: Int, wz: Int) {
        if (!isGeneratedBlock(wx, wy, wz)) return
        when (val current = blockAt(wx, wy, wz)) {
            WATER -> spreadWaterFrom(wx, wy, wz, 0)
            AIR, WATER_FLOW -> {
                if (canBecomeWaterSource(wx, wy, wz)) {
                    if (current != WATER) setWaterBlock(wx, wy, wz, WATER)
                    activateWaterNeighborhood(wx, wy, wz)
                    spreadWaterFrom(wx, wy, wz, 0)
                    return
                }
                val incoming = incomingWaterLevel(wx, wy, wz)
                if (incoming == null) {
                    // À la frontière d'un chunk non chargé, garder le flux en attente : il
                    // sera recalculé lorsque la source potentielle redevient disponible.
                    if (current == WATER_FLOW && hasUnloadedWaterInput(wx, wy, wz)) return
                    if (current == WATER_FLOW && setWaterBlock(wx, wy, wz, AIR))
                        activateWaterNeighborhood(wx, wy, wz)
                    return
                }
                if (setFlowWater(wx, wy, wz, incoming)) {
                    // La prochaine vague traitera les voisins réveillés par setFlowWater.
                    return
                }
                spreadWaterFrom(wx, wy, wz, incoming)
            }
        }
    }

    /**
     * Traite seulement les cellules réveillées par une action locale. La file est dédupliquée,
     * bornée par tick et se vide naturellement dès que le liquide est stable.
     */
    fun tickWater(maxOps: Int = 128) {
        repeat(maxOps) {
            val item = waterSpreadQueue.poll() ?: return
            waterQueued.remove(waterKey(item[0], item[1], item[2]))
            updateWaterAt(item[0], item[1], item[2])
        }
    }

    /** Compatibilité avec l'ancienne boucle renderer : la vidange est désormais réactive. */
    fun tickDrain() = Unit

    // ── Simulation gravité (sable, gravier…) ─────────────────────────────────

    val gravityQueue         = ConcurrentLinkedQueue<IntArray>()  // entrée : positions à vérifier
    val pendingFallingBlocks = ConcurrentLinkedQueue<IntArray>()  // sortie : [wx, wy, wz, type] → animation CaveRenderer
    private val fallingDest  = ConcurrentHashMap.newKeySet<Long>() // destinations réservées (évite les collisions)

    /** Vérifie si (wx,wy,wz) est un bloc soumis à la gravité et l'enfile si oui. */
    fun enqueueIfFalling(wx: Int, wy: Int, wz: Int) {
        if (isFalling(blockAt(wx, wy, wz))) gravityQueue.add(intArrayOf(wx, wy, wz))
    }

    /**
     * Lance les animations de chute (retire le bloc du monde, réserve la destination).
     * Le renderer anime visuellement et appelle [onFallingBlockLanded] à l'atterrissage.
     */
    fun tickFalling(maxOps: Int = 64) {
        repeat(maxOps) {
            val item = gravityQueue.poll() ?: return
            val wx = item[0]; val wy = item[1]; val wz = item[2]
            val block = blockAt(wx, wy, wz)
            if (!isFalling(block)) return@repeat
            val destKey = waterKey(wx, wy - 1, wz)
            if (blockAt(wx, wy - 1, wz) != AIR || fallingDest.contains(destKey)) return@repeat
            setBlock(wx, wy, wz, AIR)        // retire du monde immédiatement
            fallingDest.add(destKey)          // réserve la case d'atterrissage
            pendingFallingBlocks.add(intArrayOf(wx, wy, wz, block.toInt() and 0xFF))
            enqueueIfFalling(wx, wy + 1, wz) // le bloc au-dessus suit
        }
    }

    /** Appelé par le renderer quand l'animation d'un bloc se termine. */
    fun onFallingBlockLanded(wx: Int, wy: Int, wz: Int, type: Short) {
        fallingDest.remove(waterKey(wx, wy, wz))
        setBlock(wx, wy, wz, type)
    }

    // ── Requêtes de bloc / sol pour les entités ───────────────────────────────

    fun blockAt(wx: Int, wy: Int, wz: Int): Short {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz) ?: return AIR
        if (!chunk.generated) return AIR
        return chunk.blockAt(wx - cx * CHUNK_SIZE, wy - cy * CHUNK_SIZE, wz - cz * CHUNK_SIZE)
    }

    // Retourne la coordonnée Y du dessus du premier bloc solide sous (wx, wz)
    // en partant de floor(fromY) vers le bas. Null si rien dans la plage.
    fun groundBelow(wx: Double, wz: Double, fromY: Double, maxSearch: Int = 16): Double? {
        val bx = Math.floor(wx).toInt()
        val bz = Math.floor(wz).toInt()
        val startY = Math.floor(fromY).toInt()
        for (by in startY downTo startY - maxSearch) {
            if (blockAt(bx, by, bz) != AIR) return (by + 1).toDouble()
        }
        return null
    }

    // ── Voisinage pour le mesh ────────────────────────────────────────────────

    fun neighborBlock(baseChunk: Chunk, lx: Int, ly: Int, lz: Int): Short {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = getChunk(ncx, ncy, ncz) ?: return AIR
        if (!neighbor.generated) return AIR
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }

    // ── Lumière du ciel ───────────────────────────────────────────────────────

    // Cache de la hauteur de surface analytique par colonne (déterministe pour une seed donnée,
    // insensible aux éditions du joueur). Sert d'oracle « ciel ouvert » quand le chunk au-dessus
    // n'est pas encore chargé. Entrée minuscule (Long→Int) ; croît lentement avec l'exploration.
    private val surfaceTopCache = ConcurrentHashMap<Long, Int>()

    fun surfaceTopY(wx: Int, wz: Int): Int {
        val key = (wx.toLong() and 0xFFFFFFFFL) or ((wz.toLong() and 0xFFFFFFFFL) shl 32)
        surfaceTopCache[key]?.let { return it }
        val v = surfaceHeight(wx + 0.5, wz + 0.5).toInt()
        surfaceTopCache[key] = v
        return v
    }

    /**
     * Niveau de lumière du ciel (0..15) au voxel (lx,ly,lz) relatif à [baseChunk], cross-chunk.
     * Si le chunk visé n'est pas chargé : repli analytique — 15 si le voxel est au-dessus de la
     * surface (ciel ouvert), 0 sinon. Garde les bords des chunks de surface éclairés avant que le
     * voisin se charge ; la cascade de LightEngine corrige une fois le voisin disponible.
     */
    fun skyLightAt(baseChunk: Chunk, lx: Int, ly: Int, lz: Int): Int {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.skyAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = getChunk(ncx, ncy, ncz)
        if (neighbor != null && neighbor.generated)
            return neighbor.skyAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
        return if (wy >= surfaceTopY(wx, wz)) 15 else 0
    }
}
