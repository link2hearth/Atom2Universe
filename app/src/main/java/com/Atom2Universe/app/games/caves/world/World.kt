package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

class World(private val seed: Long = 42L, private val storage: CaveWorldChunkStorage? = null,
            /** Blocs préparés à l'avance ; null = génération procédurale (voir [WorldSource]). */
            private val source: WorldSource? = null) {
    val terrainVersion: Int get() = 8
    internal fun frontierHomes(x: Double,z: Double) = if (source == null) settlements.homes(x,z) else emptyList()
    internal fun generatedWindmill(p: FrontierWorkshops.Pos) = source == null && settlements.windmill(p)
    private val settlements by lazy { RegionalSettlements(seed, natural) }
    private val undergroundSites by lazy { UndergroundSites(seed, natural) }
    private val natural by lazy { NaturalTerrain(seed) }
    val surfaceChunkMax get() = NaturalTerrain.SURFACE_MAX_CY
    private val landscape by lazy { CozyLandscape(seed, ::nearSurfaceCave, natural, settlements::reserves) }

    internal fun naturalSurfaceBiomeAt(x: Double, y: Double, z: Double): String? =
        if (source == null && y >= natural.height(x, z) - 12) natural.biomeIdAt(x, z) else null

    /** Ecology reads loaded metadata only; the render thread never generates a site. */
    internal fun undergroundSiteAt(x: Int, y: Int, z: Int): UndergroundSites.Site? {
        if (source != null) return null
        val c = getChunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16)) ?: return null
        return if(c.generated) c.undergroundSite?.takeIf { it.contains(x,y,z) } else null
    }
    internal fun nearbyUndergroundSites(x: Double, y: Double, z: Double): List<UndergroundSites.Site> {
        if(source != null) return emptyList()
        return chunks.values.asSequence().filter { it.generated }.mapNotNull { it.undergroundSite }
            .distinctBy { it.id }.filter {
                abs(it.entrance.y-y)<20 && (it.bossPoint.x-x).pow(2)+(it.bossPoint.z-z).pow(2)<96.0*96.0
            }.toList()
    }
    internal fun undergroundCacheAt(p: FrontierWorkshops.Pos): UndergroundSites.Site? =
        getChunk(Math.floorDiv(p.x,16),Math.floorDiv(p.y,16),Math.floorDiv(p.z,16))
            ?.takeIf { it.generated }?.undergroundSite?.takeIf { UndergroundSites.Point(p.x,p.y,p.z) in it.cachePositions }

    /** Visual climate only: does not alter generation, block IDs, or saved chunks. */
    internal fun vegetationClimateAt(wx: Int, wz: Int): Int {
        source?.vegetationClimateAt(wx, wz)?.let { return it }
        val biomes = BiomeRegistry.surfaceBiomes
        if (biomes.isEmpty()) return 0
        val temperature = natural.temperature(wx.toDouble(), wz.toDouble()) - max(0.0, natural.height(wx.toDouble(), wz.toDouble()) - 200) / 1600
        val humidity = natural.humidity(wx.toDouble(), wz.toDouble())
        return when {
            temperature < .30 -> 4
            humidity > .78 && temperature < .72 -> 3
            humidity < .36 -> 1
            temperature > .70 && humidity > .62 -> 2
            else -> 0
        }
    }
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    val rebuildQueue = ConcurrentLinkedQueue<Long>()
    val waterRebuildQueue = ConcurrentLinkedQueue<Long>()
    // File de propagation de la skylight (drainée sur le thread GL, séparée du meshing).
    val lightQueue = ConcurrentLinkedQueue<Long>()
    private val lightQueued = ConcurrentHashMap.newKeySet<Long>()
    var renderRadiusXZ = 8
        private set
    fun setSimulationDistance(chunks: Int) {
        renderRadiusXZ = chunks.coerceIn(6, 32)
    }
    val renderRadiusYSurface = 5  // plage Y en surface (cylindre) — identique à avant
    val renderRadiusCave   = 7   // rayon vertical maximal en souterrain

    private val SEA_LEVEL = NaturalTerrain.SEA_LEVEL

    fun chunkKey(cx: Int, cy: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or
        ((cy.toLong() and 0xFFFFF) shl 20) or
        ((cz.toLong() and 0xFFFFF) shl 40)

    fun getChunk(cx: Int, cy: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cy, cz)]
    fun getChunkByKey(key: Long): Chunk? = chunks[key]

    /**
     * Cache local de résolution de chunk, à créer par le buildeur de mesh et jeter ensuite.
     * Jamais partagé entre appels ni entre threads : évite de reboxer la clé Long et de
     * rehasher `chunks` quand le même voisin (au bord d'un chunk) est revisité des dizaines
     * de fois pendant un seul maillage. Mesuré au profiler : ~7 % du CPU pendant un chargement
     * de chunks intensif venait de ces lookups boîtés.
     */
    class ChunkLookupCache {
        @PublishedApi internal val keys = LongArray(6) { Long.MIN_VALUE }   // chunkKey() ne produit jamais MIN_VALUE (60 bits, positif)
        @PublishedApi internal val vals = arrayOfNulls<Chunk?>(6)
        @PublishedApi internal var next = 0
        inline fun getOrPut(key: Long, compute: () -> Chunk?): Chunk? {
            for (i in 0 until 6) if (keys[i] == key) return vals[i]
            val v = compute()
            keys[next] = key; vals[next] = v; next = (next + 1) % 6
            return v
        }
    }
    fun allChunks(): Collection<Chunk> = chunks.values

    fun hasPendingLight(): Boolean = lightQueue.isNotEmpty()

    /** Chunks demandés dont la génération n'est pas finie. */
    inline fun forEachInFlight(action: (Long) -> Unit) { for (key in inFlightKeys) action(key) }
    @PublishedApi internal val inFlightKeys: Set<Long> get() = inFlight

    fun enqueueLight(key: Long) {
        chunks[key]?.ecologyLightReady=false
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
        // Monde fini (carte Assaut) : on charge toute la carte, sans distance de vue.
        source?.chunkBounds()?.let { bounds ->
            return streamWholeWorld(bounds, pcx, pcy, pcz, viewDirX, viewDirZ, maxNewChunks, onNeedGenerate)
        }

        // File pleine et joueur immobile : rien à lancer, rien à décharger. Le balayage du cylindre
        // (des milliers de cases à la distance 16) ne changerait rien ; on redemandera plus tard.
        if (maxNewChunks <= 0 && chunkKey(pcx, pcy, pcz) == lastUnloadCenter &&
            lastUnloadRadius == renderRadiusXZ) return true

        // Seuls les [maxNewChunks] meilleurs candidats servent : on les garde au fil du parcours
        // (insertion bornée, sans objet ni tri). Trier les milliers de chunks manquants à chaque
        // passage — douze fois par seconde, sur le fil de rendu — coûtait 80 % de ce fil à la
        // distance 16 (mesuré), dont la moitié à emballer des entiers pour le comparateur.
        val keep = maxNewChunks.coerceIn(0, bestKeys.size)
        var kept = 0
        var missing = 0
        fun offer(dx: Int, dy: Int, dz: Int, key: Long, tier: Int = 0) {
            missing++
            if (keep == 0) return
            val priority = tier * 1_000_000 + streamPriority(dx, dy, dz, viewDirX, viewDirZ)
            if (kept == keep && priority >= bestPriority[kept - 1]) return
            var i = if (kept < keep) kept++ else kept - 1
            while (i > 0 && bestPriority[i - 1] > priority) {
                bestPriority[i] = bestPriority[i - 1]; bestKeys[i] = bestKeys[i - 1]; i--
            }
            bestPriority[i] = priority; bestKeys[i] = key
        }
        val isSurface = pcy * CHUNK_SIZE >= surfaceHeight(pcx * 16.0 + 8, pcz * 16.0 + 8) - 32

        if (isSurface) {
            // Cylindre : disque XZ + plage Y fixe — simple et stable
            // Cylindre : disque XZ + plage Y autour du joueur, prolongée vers le haut jusqu'au sommet
            // de chaque colonne. Sans ça, une montagne plus haute que la plage n'était jamais
            // chargée en détail et restait en LOD (de grands pans lisses au-dessus du terrain).
            val rxz = renderRadiusXZ; val ry = renderRadiusYSurface
            val rxz2 = rxz * rxz
            for (dz in -rxz..rxz)
                for (dx in -rxz..rxz) {
                if (dx * dx + dz * dz > rxz2) continue
                val cx = pcx + dx; val cz = pcz + dz
                val top = columnReach(cx, cz, pcy, ry)
                val surface = columnSurfaceSpan(cx, cz)
                for (dy in -ry..top) {
                    val cy = pcy + dy
                    val key = chunkKey(cx, cy, cz)
                    if (!chunks.containsKey(key) && !inFlight.contains(key)) {
                        // Collisions proches d'abord, puis relief/arbres visibles, puis les autres
                        // étages. Le sous-sol lointain ne retient plus la surface derrière le LOD.
                        val tier = when {
                            maxOf(abs(dx), abs(dy), abs(dz)) <= 2 -> 0
                            cy in surface -> 1
                            else -> 2
                        }
                        offer(dx, dy, dz, key, tier)
                    }
                }
            }
        } else {
            // Horizontal distance is configurable; keep the vertical band bounded.
            val r = renderRadiusXZ; val ry = minOf(r, renderRadiusCave)
            val r2 = r * r; val ry2 = ry * ry
            for (dz in -r..r)
                for (dy in -ry..ry)
                    for (dx in -r..r) {
                if ((dx * dx + dz * dz) * ry2 + dy * dy * r2 > r2 * ry2) continue
                val cx = pcx + dx; val cy = pcy + dy; val cz = pcz + dz
                val key = chunkKey(cx, cy, cz)
                if (!chunks.containsKey(key) && !inFlight.contains(key)) offer(dx, dy, dz, key)
            }
        }

        // Les plus proches d'abord, avec un petit bonus dans la direction du regard.
        var scheduled = 0
        for (i in 0 until kept) {
            val key = bestKeys[i]
            if (!inFlight.add(key)) continue
            val chunk = Chunk(keyToCx(key), keyToCy(key), keyToCz(key))
            chunks[key] = chunk
            onNeedGenerate(chunk)
            scheduled++
        }

        // Décharger ne sert qu'après un déplacement : sans changement de chunk, rien ne sort du
        // cylindre, et ce balayage de tous les chunks chargés se refaisait douze fois par seconde.
        val center = chunkKey(pcx, pcy, pcz)
        if (center == lastUnloadCenter && lastUnloadRadius == renderRadiusXZ) return missing > scheduled
        lastUnloadCenter = center
        lastUnloadRadius = renderRadiusXZ

        // Déchargement : même forme que le chargement (disque en surface, ellipsoïde en souterrain),
        // un chunk de marge pour qu'un pas en arrière ne recharge pas le bord.
        // La boîte carrée d'avant (±2 chunks, ±2 étages) gardait jusqu'à trois fois le cylindre
        // chargé : en explorant, tout ce qu'on traversait restait maillé et dessiné, et la
        // mémoire graphique montait sans fin (5 500 maillages et 580 Mo mesurés sur tablette).
        if (isSurface) {
            val rxz = renderRadiusXZ + 1; val ry = renderRadiusYSurface + 1
            val rxz2 = rxz * rxz
            chunks.entries.filter { (_, c) ->
                val dx = c.cx - pcx; val dz = c.cz - pcz; val dy = c.cy - pcy
                !inFlight.contains(chunkKey(c.cx, c.cy, c.cz)) &&
                    (dx * dx + dz * dz > rxz2 || dy < -ry || dy > maxOf(ry, columnReach(c.cx, c.cz, pcy, ry - 1) + 1))
            }.forEach { (key, _) -> chunks.remove(key); inFlight.remove(key) }
        } else {
            val unloadR2 = (renderRadiusXZ + 2).let { it * it }
            val unloadY2 = (minOf(renderRadiusXZ, renderRadiusCave) + 2).let { it * it }
            chunks.entries.filter { (_, c) ->
                val dx = c.cx - pcx; val dy = c.cy - pcy; val dz = c.cz - pcz
                !inFlight.contains(chunkKey(c.cx, c.cy, c.cz)) &&
                    (dx * dx + dz * dz) * unloadY2 + dy * dy * unloadR2 > unloadR2 * unloadY2
            }.forEach { (key, _) -> chunks.remove(key); inFlight.remove(key) }
        }
        return missing > scheduled
    }

    /**
     * Hauteur (en chunks au-dessus du joueur) jusqu'où charger la colonne : au moins [ry], et
     * jusqu'au chunk de son sommet s'il est plus haut (plus un chunk pour les arbres), borné à
     * [COLUMN_EXTRA_CY] de plus pour qu'un pic extrême ne charge pas tout le ciel.
     */
    private fun columnReach(cx: Int, cz: Int, pcy: Int, ry: Int): Int {
        if (source != null) return ry
        val top = columnSurfaceSpan(cx, cz).last - TREE_CHUNKS_ABOVE_SURFACE
        return (top + 1 - pcy).coerceIn(ry, ry + COLUMN_EXTRA_CY)
    }

    /** Relief, eau et arbres : plage estimée avec les mêmes 9 échantillons que le plafond. */
    private fun columnSurfaceSpan(cx: Int, cz: Int): IntRange {
        val key = (cx.toLong() and 0xFFFFF) or ((cz.toLong() and 0xFFFFF) shl 20)
        columnTops[key]?.let { return it }
        var top = Double.NEGATIVE_INFINITY
        var bottom = Double.POSITIVE_INFINITY
        for (z in 0..2) for (x in 0..2) {
            val h = surfaceHeight(cx * 16.0 + x * 7.5, cz * 16.0 + z * 7.5)
            top = maxOf(top, h)
            bottom = minOf(bottom, h)
        }
        if (columnTops.size > 20000) columnTops.clear()
        // Une marge sous le sol couvre les flancs et les variations entre échantillons.
        return (Math.floorDiv(bottom.toInt(), CHUNK_SIZE) - 1..
            Math.floorDiv(top.toInt(), CHUNK_SIZE) + TREE_CHUNKS_ABOVE_SURFACE)
            .also { columnTops[key] = it }
    }
    private val TREE_CHUNKS_ABOVE_SURFACE = (TreeShape.HEIGHT + CHUNK_SIZE - 1) / CHUNK_SIZE
    private val columnTops = HashMap<Long, IntRange>()
    // 8 chunks = 128 blocs de plus au-dessus de la plage normale, au plus.
    private val COLUMN_EXTRA_CY = 8

    // Meilleurs candidats du passage en cours (fil de rendu uniquement) et dernier centre déchargé.
    private val bestKeys = LongArray(16)
    private val bestPriority = IntArray(16)
    private var lastUnloadCenter = Long.MIN_VALUE
    private var lastUnloadRadius = -1

    /**
     * Charge tous les chunks de [bounds], les plus proches du joueur d'abord, et n'en décharge
     * jamais aucun : une carte est petite (une arène de 100 × 100 = 49 chunks), elle tient en entier.
     */
    private fun streamWholeWorld(
        bounds: ChunkBounds, pcx: Int, pcy: Int, pcz: Int,
        viewDirX: Float, viewDirZ: Float, maxNewChunks: Int,
        onNeedGenerate: (Chunk) -> Unit
    ): Boolean {
        val candidates = mutableListOf<ChunkCandidate>()
        for (cz in bounds.minCz..bounds.maxCz)
            for (cy in bounds.minCy..bounds.maxCy)
                for (cx in bounds.minCx..bounds.maxCx) {
                    val key = chunkKey(cx, cy, cz)
                    if (!chunks.containsKey(key) && !inFlight.contains(key))
                        candidates.add(ChunkCandidate(cx, cy, cz, key,
                            streamPriority(cx - pcx, cy - pcy, cz - pcz, viewDirX, viewDirZ)))
                }
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
        return candidates.size > scheduled
    }

    private data class ChunkCandidate(val cx: Int, val cy: Int, val cz: Int, val key: Long, val priority: Int)

    private fun streamPriority(dx: Int, dy: Int, dz: Int, viewDirX: Float, viewDirZ: Float): Int {
        val dist = dx * dx + dz * dz + dy * dy
        val horizontal = sqrt((dx * dx + dz * dz).toFloat()).coerceAtLeast(1f)
        val facing = ((dx * viewDirX + dz * viewDirZ) / horizontal).coerceIn(-1f, 1f)
        // Distance always wins. Looking ahead only breaks ties on the same distance shell.
        return dist * 32 + ((1f - facing) * 8f).toInt()
    }

    fun abandonChunk(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        if(chunks.remove(key,chunk)) inFlight.remove(key)
    }

    fun markGenerated(chunk: Chunk) {
        val key = chunkKey(chunk.cx, chunk.cy, chunk.cz)
        // A synchronous travel preload may already have replaced this background job.
        if(chunks[key] !== chunk) return
        inFlight.remove(key)
        chunk.generated = true
        chunk.meshDirty = true
        rebuildQueue.add(key)
        enqueueLight(key)
        // Les fluides ne traversent jamais un chunk non généré. Quand ce chunk devient
        // disponible, on réveille uniquement les écoulements qui attendaient précisément
        // cette frontière, sans scanner tout son volume (important pour les océans).
        resumeDeferredWaterActivations(key)
        wakeExposedWater(chunk)
        val neighbors = arrayOf(
            intArrayOf(chunk.cx-1,chunk.cy,chunk.cz), intArrayOf(chunk.cx+1,chunk.cy,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy-1,chunk.cz), intArrayOf(chunk.cx,chunk.cy+1,chunk.cz),
            intArrayOf(chunk.cx,chunk.cy,chunk.cz-1), intArrayOf(chunk.cx,chunk.cy,chunk.cz+1)
        )
        // Les voisins ne sont pas re-maillés ici mais quand la lumière de ce chunk se sera
        // stabilisée (CaveRenderer.processLightBatch) : un seul passage pour la forme et la lumière.
        for ((nx, ny, nz) in neighbors) {
            val nb = getChunk(nx, ny, nz) ?: continue
            if (nb.generated) enqueueLight(nx, ny, nz)
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
        enqueueLight(key)
        resumeDeferredWaterActivations(key)
        wakeExposedWater(chunk)
        return chunk
    }

    fun findSpawnPoint(): FloatArray {
        if (source == null) {
            // Search dry, walkable terrain before loading chunks, including ocean seeds.
            for (radius in 0..128) for (side in 0 until if (radius == 0) 1 else radius * 8) {
                val span = max(1, radius * 2)
                val edge = side / span; val step = side % span - radius
                val wx = 8 + 16 * when (edge) { 0 -> step; 1 -> radius; 2 -> -step; else -> -radius }
                val wz = 8 + 16 * when (edge) { 0 -> -radius; 1 -> step; 2 -> radius; else -> -step }
                val h = natural.height(wx.toDouble(), wz.toDouble()).toInt()
                if (h < SEA_LEVEL + 3 || nearSurfaceCave(wx, wz)) continue
                if (listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2).any { (dx, dz) ->
                        abs(natural.height((wx + dx).toDouble(), (wz + dz).toDouble()) - h) > 2.5 }) continue
                val cx = Math.floorDiv(wx, 16); val cz = Math.floorDiv(wz, 16)
                for (cy in Math.floorDiv(h, 16)..Math.floorDiv(h + 3, 16)) pregenerateChunk(cx, cy, cz)
                if (blockAt(wx, h + 1, wz) != AIR || blockAt(wx, h + 2, wz) != AIR) continue
                return floatArrayOf(wx + .5f, h + 1 + 1.62f, wz + .5f)
            }
        }
        val approxCy = (surfaceHeight(8.0, 8.0) / CHUNK_SIZE).toInt()

        // Prégenère les chunks dans le rayon de recherche
        for (dcy in 0 downTo -2) for (dcz in -1..1) for (dcx in -1..1)
            pregenerateChunk(dcx, (approxCy + dcy).coerceIn(0, surfaceChunkMax), dcz)

        // Cherche un sol solide (non eau) dans un carré 32×32 centré sur (8,8)
        for (dcy in 0 downTo -2) {
            val cyCand = approxCy + dcy
            if (cyCand !in 0..surfaceChunkMax) continue
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
        if (isWood(old) || isLeaf(old)) {
            for (dz in -6..6) for (dy in -6..6) for (dx in -6..6) {
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) <= 6 &&
                    isLeaf(blockAt(wx + dx, wy + dy, wz + dz)))
                    leafChecks.add(Triple(wx + dx, wy + dy, wz + dz))
            }
        }
        chunk.setBlock(lx, ly, lz, type)
        chunk.version++; chunk.meshDirty = true
        val key = chunkKey(cx, cy, cz)
        rebuildQueue.add(key)
        if (skyPassable(old) != skyPassable(type) || emission(old)!=emission(type)) enqueueLightAndNeighbors(cx, cy, cz)
        storage?.recordChange(cx, cy, cz,
            lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
    }

    /** One procedural generator; authored Assault maps still supply their own blocks. */
    fun generate(chunk: Chunk) {
        val src = source
        if (src != null) src.fill(chunk) else {
            natural.generate(chunk, landscape)
            undergroundSites.decorate(chunk)
            settlements.decorate(chunk)
        }
        storage?.applyDiff(chunk)
        storage?.applyMetaDiff(chunk)
    }

    private fun nearSurfaceCave(x: Int, z: Int): Boolean =
        natural.caveAt(x, natural.height(x.toDouble(), z.toDouble()).toInt(), z)

    internal fun surfaceHeight(wx: Double, wz: Double): Double =
        source?.skyTopY(floor(wx).toInt(), floor(wz).toInt())?.minus(1)?.toDouble() ?: natural.height(wx, wz)

    /** Analytical surface only: no chunk allocation, population, lighting or simulation. */
    internal fun distantSurface(wx: Int, wz: Int): Pair<Int, Short> {
        val x = wx.toDouble(); val z = wz.toDouble()
        val height = surfaceHeight(x, z).toInt()
        val water = natural.waterLevelAt(x, z)
        if (height < water) return water to if (natural.temperature(x, z) < .18) ICE else WATER
        val id = natural.biomeIdAt(x, z)
        val biome = BiomeRegistry.surfaceBiomes.first { it.id == id }
        return height to natural.topBlock(biome, x, z, height, water)
    }

    // ── Modification de blocs ─────────────────────────────────────────────────

    private fun emission(block: Short) = when(block) { TORCH->15;LAVA->12;else->com.Atom2Universe.app.games.caves.node.BlockRegistry.lightEmission(block) }
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

    private val leafChecks = java.util.LinkedHashSet<Triple<Int, Int, Int>>()
    private var leafScanChunks = emptyList<Long>()
    private var leafScanChunk = 0
    private var leafScanIndex = 0

    /** Bounded background sweep also resumes decay after saving/reloading or chunk streaming. */
    internal fun tickLeaves(onSupported: (Int, Int, Int) -> Unit,
                           onDecay: (Int, Int, Int, Short, Byte) -> Unit) {
        if (leafScanChunk >= leafScanChunks.size) {
            leafScanChunks = chunks.keys.toList()
            leafScanChunk = 0
            leafScanIndex = 0
        }
        var scans = 0
        while (leafScanChunk < leafScanChunks.size && scans++ < 512) {
            val chunk = chunks[leafScanChunks[leafScanChunk]]
            if (chunk == null || !chunk.generated) { leafScanChunk++; leafScanIndex = 0; continue }
            val i = leafScanIndex++
            val x = i % 16; val y = i / 16 % 16; val z = i / 256
            if (isLeaf(chunk.blockAt(x, y, z))) leafChecks.add(Triple(chunk.worldX + x, chunk.worldY + y, chunk.worldZ + z))
            if (leafScanIndex == 4096) { leafScanIndex = 0; leafScanChunk++ }
            if (leafChecks.size >= 32) break
        }
        repeat(8) {
            val p = leafChecks.firstOrNull() ?: return
            leafChecks.remove(p)
            val (x, y, z) = p
            val id = blockAt(x, y, z)
            val metadata = metaAt(x,y,z)
            if (!isLeaf(id) || TreeSpecies.persistent(metadata)) return@repeat
            val supported = LeafSupport.state(x, y, z) { a, b, c ->
                getChunk(Math.floorDiv(a, 16), Math.floorDiv(b, 16), Math.floorDiv(c, 16))
                    ?.takeIf { it.generated }?.blockAt(Math.floorMod(a, 16), Math.floorMod(b, 16), Math.floorMod(c, 16))
            }
            if (supported == true) onSupported(x,y,z)
            if (supported == false) {
                onDecay(x,y,z,id,metadata)
                setBlock(x, y, z, AIR)
            }
        }
    }

    private val grassRandom = java.util.Random()

    /**
     * Propagation de l'herbe, façon Minecraft : à chaque appel, quelques cases tirées au hasard
     * dans chaque chunk chargé. Une terre éclairée (lumière ≥ 9), sans bloc plein ni eau au-dessus,
     * devient herbe si une herbe se trouve à côté (±1 en X/Z, d'un bloc plus bas à trois plus haut).
     */
    fun tickGrass(samplesPerChunk: Int = 6) {
        for (chunk in chunks.values) {
            if (!chunk.generated) continue
            repeat(samplesPerChunk) {
                val i = grassRandom.nextInt(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE)
                val lx = i % CHUNK_SIZE; val ly = i / CHUNK_SIZE % CHUNK_SIZE; val lz = i / (CHUNK_SIZE * CHUNK_SIZE)
                if (chunk.blockAt(lx, ly, lz) != DIRT) return@repeat
                val above = neighborBlockOr(chunk, lx, ly + 1, lz, STONE)
                if (!skyPassable(above) || isWater(above)) return@repeat
                val light = max(skyLightAt(chunk, lx, ly + 1, lz), passableBlockLightAt(chunk, lx, ly + 1, lz))
                if (light < 9) return@repeat
                val wx = chunk.worldX + lx; val wy = chunk.worldY + ly; val wz = chunk.worldZ + lz
                if (hasGrassNear(wx, wy, wz)) setBlock(wx, wy, wz, GRASS)
            }
        }
    }

    private fun hasGrassNear(wx: Int, wy: Int, wz: Int): Boolean {
        for (dy in -1..3) for (dz in -1..1) for (dx in -1..1)
            if ((dx != 0 || dy != 0 || dz != 0) && blockAt(wx + dx, wy + dy, wz + dz) == GRASS) return true
        return false
    }

    internal var onBlockReplaced: (Int, Int, Int) -> Unit = { _,_,_ -> }

    fun setBlock(wx: Int, wy: Int, wz: Int, type: Short) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        val old = chunk.blockAt(lx, ly, lz)
        if (old == type) return
        if (isWood(old) || isLeaf(old)) {
            for (dz in -6..6) for (dy in -6..6) for (dx in -6..6) {
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) <= 6 &&
                    isLeaf(blockAt(wx + dx, wy + dy, wz + dz)))
                    leafChecks.add(Triple(wx + dx, wy + dy, wz + dz))
            }
        }
        onBlockReplaced(wx,wy,wz)
        chunk.setBlock(lx, ly, lz, type)
        // Leaf species/persistence must not leak into the replacement (or inherit an orientation).
        if (isLeaf(old) || isLeaf(type)) setMeta(wx,wy,wz,0)
        if (old == WATER_FLOW) clearWaterFlowLevel(wx, wy, wz)
        if (type == WATER_FLOW) setWaterFlowLevel(wx, wy, wz, 1)
        chunk.version++
        val key = chunkKey(cx, cy, cz)
        chunk.meshDirty = true
        rebuildQueue.add(key)
        if (skyPassable(old) != skyPassable(type) || emission(old)!=emission(type)) enqueueLightAndNeighbors(cx, cy, cz)
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
        markWaterMeshDirty(wx, wy, wz)
        waterRouteMasks.clear()
        activateWaterNeighborhood(wx, wy, wz, urgent = true)
        // Un obstacle ou un trou peut modifier un chemin à plusieurs cases de distance.
        // Inclure les sorties voisines pour retirer les branches devenues non prioritaires.
        val routingRadius = WaterFlowRouting.SEARCH_DISTANCE + 1
        for (dy in 0..1) for (dz in -routingRadius..routingRadius)
            for (dx in -routingRadius..routingRadius) {
                if (kotlin.math.abs(dx) + kotlin.math.abs(dz) <= routingRadius &&
                    isWater(blockAt(wx + dx, wy + dy, wz + dz)))
                    queueWaterUpdate(wx + dx, wy + dy, wz + dz, urgent = true)
            }
    }

    fun setMeta(wx: Int, wy: Int, wz: Int, value: Byte) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = getChunk(cx, cy, cz)?.takeIf { it.generated } ?: return
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        if (chunk.metaAt(lx, ly, lz) == value) return
        chunk.setMeta(lx, ly, lz, value)
        storage?.recordMetaChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, value)
        // Orientation changes also change the shape of adjacent stair corners.
        chunk.version++
        chunk.meshDirty = true
        rebuildQueue.add(chunkKey(cx, cy, cz))
        for ((nx, ny, nz) in arrayOf(
            intArrayOf(cx - 1, cy, cz), intArrayOf(cx + 1, cy, cz),
            intArrayOf(cx, cy - 1, cz), intArrayOf(cx, cy + 1, cz),
            intArrayOf(cx, cy, cz - 1), intArrayOf(cx, cy, cz + 1)
        )) {
            val neighbor = getChunk(nx, ny, nz)?.takeIf { it.generated } ?: continue
            neighbor.meshDirty = true
            rebuildQueue.add(chunkKey(nx, ny, nz))
        }
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
    private val waterUpdates = WaterUpdateQueue()
    // Generator threads enqueue background work; only descendants of a local edit inherit urgency.
    private val urgentWaterWave = ThreadLocal.withInitial { false }
    private val deferredWaterActivations = ConcurrentHashMap<Long, ConcurrentHashMap<Long, IntArray>>()
    private val horizontalWaterDirs = arrayOf(
        intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
        intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
    )
    private val waterNeighborDirs = arrayOf(
        intArrayOf(0, -1, 0), intArrayOf(0, 1, 0),
        *horizontalWaterDirs
    )
    private val MAX_WATER_FLOW_LEVEL = 8
    // Réutilisé pendant un seul tick ; AIR et WATER_FLOW ont la même traversabilité.
    private val waterRouteMasks = HashMap<Long, Int>()

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
                neighbor.waterVersion++
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
        // Les hauteurs des coins lisent aussi les voisins diagonaux, y compris sous
        // une colonne pleine située à la frontière verticale du chunk.
        val dx = if (lx == 0) -1 else if (lx == CHUNK_SIZE - 1) 1 else 0
        val dz = if (lz == 0) -1 else if (lz == CHUNK_SIZE - 1) 1 else 0
        if (dx != 0 && dz != 0) markNeighbor(cx + dx, cy, cz + dz)
        if (ly == 0) {
            if (dx != 0) markNeighbor(cx + dx, cy - 1, cz)
            if (dz != 0) markNeighbor(cx, cy - 1, cz + dz)
            if (dx != 0 && dz != 0) markNeighbor(cx + dx, cy - 1, cz + dz)
        }
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
        if (old == WATER || type == WATER) waterRouteMasks.clear()
        if (old == WATER_FLOW) clearWaterFlowLevel(wx, wy, wz)
        chunk.setBlock(lx, ly, lz, type)
        markWaterMeshDirty(wx, wy, wz)
        storage?.recordChange(cx, cy, cz, lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE, type)
        return true
    }

    private fun queueWaterUpdate(wx: Int, wy: Int, wz: Int, urgent: Boolean = urgentWaterWave.get() == true) {
        if (!isGeneratedBlock(wx, wy, wz)) return
        val block = blockAt(wx, wy, wz)
        if (!isWater(block) && !canWaterDisplace(block)) return
        waterUpdates.offer(waterKey(wx, wy, wz), wx, wy, wz, urgent)
    }

    private fun activateWaterNeighborhood(wx: Int, wy: Int, wz: Int, urgent: Boolean = urgentWaterWave.get() == true) {
        queueWaterUpdate(wx, wy, wz, urgent)
        for (d in waterNeighborDirs) queueWaterUpdate(wx + d[0], wy + d[1], wz + d[2], urgent)
    }

    fun onWaterSourcePlaced(wx: Int, wy: Int, wz: Int) {
        clearWaterFlowLevel(wx, wy, wz)
        activateWaterNeighborhood(wx, wy, wz, urgent = true)
    }

    fun onWaterSourceRemoved(wx: Int, wy: Int, wz: Int) {
        activateWaterNeighborhood(wx, wy, wz, urgent = true)
    }

    private fun deferWaterActivation(sourceX: Int, sourceY: Int, sourceZ: Int, targetX: Int, targetY: Int, targetZ: Int) {
        val targetKey = chunkKey(
            Math.floorDiv(targetX, CHUNK_SIZE),
            Math.floorDiv(targetY, CHUNK_SIZE),
            Math.floorDiv(targetZ, CHUNK_SIZE)
        )
        deferredWaterActivations.getOrPut(targetKey) { ConcurrentHashMap() }
            .putIfAbsent(waterKey(sourceX, sourceY, sourceZ), intArrayOf(sourceX, sourceY, sourceZ))
        // Le générateur peut avoir terminé entre le test du voisin et l'inscription.
        // Dans ce cas sa notification est déjà passée : réveiller aussi directement
        // la cellule pour ne pas dépendre d'un prochain rechargement du chunk.
        if (isGeneratedBlock(targetX, targetY, targetZ)) {
            activateWaterNeighborhood(sourceX, sourceY, sourceZ)
            resumeDeferredWaterActivations(targetKey)
        }
    }

    private fun resumeDeferredWaterActivations(chunkKey: Long) {
        val waiting = deferredWaterActivations.remove(chunkKey) ?: return
        for (source in waiting.values) {
            activateWaterNeighborhood(source[0], source[1], source[2])
        }
    }

    /**
     * Le générateur pose l'eau immobile : un bord de lac qui touche un trou ou une grotte resterait
     * suspendu dans le vide jusqu'à ce que le joueur modifie un bloc à côté. Dès qu'un chunk est
     * prêt, on réveille donc chaque source posée contre de l'air (en dessous ou sur le côté).
     *
     * Deux sens à couvrir, car les chunks arrivent dans n'importe quel ordre :
     *  - l'eau de CE chunk contre l'air d'un voisin déjà prêt (ou de ce chunk) ;
     *  - l'eau des voisins déjà prêts contre l'air de CE chunk, qui vient d'apparaître.
     * Un voisin pas encore généré sera traité à son tour, quand il arrivera.
     * Les flux sauvegardés reprennent aussi leur simulation, même entourés d'eau.
     * Les sources d'un océan fermé restent endormies.
     */
    private fun wakeExposedWater(chunk: Chunk) {
        // Les cartes dessinées à la main (Assaut…) gardent leur eau telle qu'elle a été posée.
        if (source != null) return
        val s = CHUNK_SIZE
        fun openAt(wx: Int, wy: Int, wz: Int): Boolean {
            val n = getChunk(Math.floorDiv(wx, s), Math.floorDiv(wy, s), Math.floorDiv(wz, s))
            if (n == null || !n.generated) return false
            val block = n.blockAt(Math.floorMod(wx, s), Math.floorMod(wy, s), Math.floorMod(wz, s))
            return block == WATER_FLOW || canWaterDisplace(block)
        }
        fun exposed(wx: Int, wy: Int, wz: Int) = openAt(wx, wy - 1, wz) ||
            horizontalWaterDirs.any { openAt(wx + it[0], wy, wz + it[2]) }
        val bx = chunk.worldX; val by = chunk.worldY; val bz = chunk.worldZ
        for (ly in 0 until s) for (lz in 0 until s) for (lx in 0 until s) {
            val block = chunk.blocks[lx + ly * s + lz * s * s]
            if (block == WATER_FLOW || block == WATER && exposed(bx + lx, by + ly, bz + lz))
                queueWaterUpdate(bx + lx, by + ly, bz + lz)
        }
        // Sources des voisins posées juste contre une face de ce chunk (dessus : eau qui tombe).
        fun wakeNeighbour(wx: Int, wy: Int, wz: Int) {
            val n = getChunk(Math.floorDiv(wx, s), Math.floorDiv(wy, s), Math.floorDiv(wz, s)) ?: return
            if (!n.generated) return
            val block = n.blockAt(Math.floorMod(wx, s), Math.floorMod(wy, s), Math.floorMod(wz, s))
            if (block == WATER_FLOW || block == WATER && exposed(wx, wy, wz)) queueWaterUpdate(wx, wy, wz)
        }
        for (a in 0 until s) for (b in 0 until s) {
            wakeNeighbour(bx - 1, by + a, bz + b); wakeNeighbour(bx + s, by + a, bz + b)
            wakeNeighbour(bx + a, by + b, bz - 1); wakeNeighbour(bx + a, by + b, bz + s)
            wakeNeighbour(bx + a, by + s, bz + b)
            wakeNeighbour(bx + a, by - 1, bz + b)
        }
    }

    /** L'herbe et les petits végétaux remplaçables ne constituent pas une digue. */
    private fun canWaterDisplace(block: Short): Boolean = block == AIR || isDecoration(block) &&
        com.Atom2Universe.app.games.caves.node.BlockRegistry.get(block)?.let {
            it.decoration && it.replaceable && !it.water && !it.waterlogged
        } == true

    private fun setFlowWater(wx: Int, wy: Int, wz: Int, level: Int): Boolean {
        if (!isGeneratedBlock(wx, wy, wz)) return false
        val current = blockAt(wx, wy, wz)
        if (current != WATER_FLOW && !canWaterDisplace(current)) return false
        val normalized = level.coerceIn(0, MAX_WATER_FLOW_LEVEL)
        val oldLevel = if (current == WATER_FLOW) cachedWaterFlowLevel(wx, wy, wz) else -1
        if (current == WATER_FLOW && oldLevel == normalized) return false
        if (current == AIR && !setWaterBlock(wx, wy, wz, WATER_FLOW)) return false
        // Les plantes ont un maillage solide : il faut aussi le reconstruire pour
        // retirer leur sprite, contrairement à la simple transition air/eau.
        if (current != AIR && current != WATER_FLOW) setBlock(wx, wy, wz, WATER_FLOW)
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

    private fun canRefillWaterSurface(wx: Int, wy: Int, wz: Int): Boolean {
        if (sourceNeighborCount(wx, wy, wz) < 2) return false
        if (!isGeneratedBlock(wx, wy - 1, wz)) {
            deferWaterActivation(wx, wy, wz, wx, wy - 1, wz)
            return false
        }
        // Deux vraies sources peuvent remettre la surface à niveau, sans créer une source autonome.
        val below = blockAt(wx, wy - 1, wz)
        return below != WATER_FLOW && !canWaterDisplace(below)
    }

    private fun waterDirections(wx: Int, wy: Int, wz: Int, level: Int): Int {
        val key = waterKey(wx, wy, wz)
        waterRouteMasks[key]?.let { if (it ushr 4 == level) return it and 15 }
        val cache = ChunkLookupCache()
        val mask = WaterFlowRouting.directions(wx, wy, wz, MAX_WATER_FLOW_LEVEL - level) { x, y, z ->
            if (isGeneratedBlock(x, y, z)) {
                val block = blockAt(x, y, z, cache)
                if (canWaterDisplace(block)) AIR else block
            }
            else {
                deferWaterActivation(wx, wy, wz, x, y, z)
                null
            }
        }
        waterRouteMasks[key] = (level shl 4) or mask
        return mask
    }

    private fun incomingWaterLevel(wx: Int, wy: Int, wz: Int): Int? {
        var best = Int.MAX_VALUE
        val above = blockAt(wx, wy + 1, wz)
        // La chute renouvelle la portée horizontale, sans créer une source permanente.
        // Quand l'alimentation supérieure disparaît, ce flux peut donc se vider.
        if (isWater(above)) return 0
        for ((direction, d) in horizontalWaterDirs.withIndex()) {
            val nx = wx + d[0]; val nz = wz + d[2]
            val neighbor = blockAt(nx, wy, nz)
            if (!isWater(neighbor)) continue
            val level = waterFlowLevelKnown(neighbor, nx, wy, nz)
            if (level >= MAX_WATER_FLOW_LEVEL || level + 1 >= best) continue
            // Même règle en réception qu'en émission, sinon les cases latérales
            // réveillées aspireraient l'eau malgré le chemin sélectionné.
            val towardCell = 1 shl (direction xor 1)
            if (waterDirections(nx, wy, nz, level) and towardCell != 0)
                best = min(best, level + 1)
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
            if (blockAt(nx, wy, nz) == WATER_FLOW && !isGeneratedBlock(nx, wy - 1, nz)) {
                deferWaterActivation(wx, wy, wz, nx, wy - 1, nz)
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
        val below = blockAt(wx, belowY, wz)
        if (canWaterDisplace(below)) {
            setFlowWater(wx, belowY, wz, 0)
            return
        }
        if (below == WATER_FLOW) return
        if (level >= MAX_WATER_FLOW_LEVEL) return
        val nextLevel = level + 1
        val directions = waterDirections(wx, wy, wz, level)
        for ((direction, d) in horizontalWaterDirs.withIndex()) {
            if (directions and (1 shl direction) == 0) continue
            val nx = wx + d[0]; val nz = wz + d[2]
            if (!isGeneratedBlock(nx, wy, nz)) {
                deferWaterActivation(wx, wy, wz, nx, wy, nz)
                continue
            }
            if (canWaterDisplace(blockAt(nx, wy, nz))) setFlowWater(nx, wy, nz, nextLevel)
        }
    }

    private fun updateWaterAt(wx: Int, wy: Int, wz: Int) {
        if (!isGeneratedBlock(wx, wy, wz)) return
        when (val current = blockAt(wx, wy, wz)) {
            WATER -> spreadWaterFrom(wx, wy, wz, 0)
            else -> {
                if (current != WATER_FLOW && !canWaterDisplace(current)) return
                // Flow remains flow even where two sources meet. Otherwise closing a sluice leaves
                // newly manufactured permanent sources behind, feeding the supposedly cut-off stream.
                val incoming = if (canRefillWaterSurface(wx, wy, wz)) 0 else incomingWaterLevel(wx, wy, wz)
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
        waterRouteMasks.clear()
        val budget = maxOps.coerceAtLeast(0)
        if (budget == 0) return
        val localCount = minOf(budget - budget / 4, waterUpdates.size(true))
        val terrainCount = minOf(budget - localCount, waterUpdates.size(false))
        fun wave(urgent: Boolean, count: Int, nanos: Long) {
            val deadline = System.nanoTime() + nanos
            urgentWaterWave.set(urgent)
            try {
                repeat(count) {
                    if (it > 0 && System.nanoTime() >= deadline) return
                    val item = waterUpdates.poll(urgent) ?: return
                    updateWaterAt(item[0], item[1], item[2])
                }
            } finally {
                urgentWaterWave.set(false)
            }
        }
        // Both queues get a bounded turn; loading coastlines cannot block a player's sluice.
        wave(true, localCount, if (terrainCount == 0) 2_000_000L else 1_500_000L)
        wave(false, terrainCount, if (localCount == 0) 2_000_000L else 500_000L)
    }

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

    fun blockAt(wx: Int, wy: Int, wz: Int, cache: ChunkLookupCache? = null): Short {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = (if (cache != null) cache.getOrPut(chunkKey(cx, cy, cz)) { getChunk(cx, cy, cz) } else getChunk(cx, cy, cz))
            ?: return AIR
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

    /**
     * Comme [neighborBlock], mais une case d'un chunk absent ou pas encore généré vaut [unknown]
     * au lieu d'air : au bord de la zone chargée, « je ne sais pas » n'est pas « il n'y a rien ».
     */
    fun neighborBlockOr(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, unknown: Short,
                        cache: ChunkLookupCache? = null): Short {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = (if (cache != null) cache.getOrPut(chunkKey(ncx, ncy, ncz)) { getChunk(ncx, ncy, ncz) }
            else getChunk(ncx, ncy, ncz)) ?: return unknown
        if (!neighbor.generated) return unknown
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }

    fun neighborBlock(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, cache: ChunkLookupCache? = null): Short {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.blockAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val nKey = chunkKey(ncx, ncy, ncz)
        val neighbor = (if (cache != null) cache.getOrPut(nKey) { getChunk(ncx, ncy, ncz) } else getChunk(ncx, ncy, ncz))
            ?: return AIR
        if (!neighbor.generated) return AIR
        return neighbor.blockAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
    }

    /**
     * Lumière des torches (0..15, quartet haut de `chunk.light`) d'une case, même hors du chunk
     * de base, et ce qu'elle contient : un sommet ne moyenne que les cases où la lumière passe.
     * Renvoie -1 pour une case pleine ou d'un chunk pas encore généré.
     */
    fun passableBlockLightAt(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, cache: ChunkLookupCache? = null): Int {
        val both = passableLightAt(baseChunk, lx, ly, lz, cache)
        return if (both < 0) -1 else (both ushr 4) and 15
    }

    /** Même chose pour la lumière du ciel (quartet bas de `chunk.light`). */
    fun passableSkyLightAt(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, cache: ChunkLookupCache? = null): Int {
        val both = passableLightAt(baseChunk, lx, ly, lz, cache)
        return if (both < 0) -1 else both and 15
    }

    // Octet de lumière complet (ciel + torches) d'une case où la lumière passe, sinon -1.
    private fun passableLightAt(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, cache: ChunkLookupCache?): Int {
        val chunk: Chunk; val x: Int; val y: Int; val z: Int
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE) {
            chunk = baseChunk; x = lx; y = ly; z = lz
        } else {
            val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
            val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
            val neighbor = (if (cache != null) cache.getOrPut(chunkKey(ncx, ncy, ncz)) { getChunk(ncx, ncy, ncz) }
                else getChunk(ncx, ncy, ncz)) ?: return -1
            if (!neighbor.generated) return -1
            chunk = neighbor; x = wx - ncx * CHUNK_SIZE; y = wy - ncy * CHUNK_SIZE; z = wz - ncz * CHUNK_SIZE
        }
        if (!LightEngine.passable(chunk.blockAt(x, y, z))) return -1
        return chunk.light[x + y * CHUNK_SIZE + z * CHUNK_SIZE * CHUNK_SIZE].toInt() and 0xFF
    }

    // ── Lumière du ciel ───────────────────────────────────────────────────────

    // Cache de la hauteur de surface analytique par colonne (déterministe pour une seed donnée,
    // insensible aux éditions du joueur). Sert d'oracle « ciel ouvert » quand le chunk au-dessus
    // n'est pas encore chargé. Cache borné par worker, sans boxing ni croissance à l'exploration.
    private val surfaceTopCache = ThreadLocal.withInitial { SurfaceColumnCache() }

    fun surfaceTopY(wx: Int, wz: Int): Int {
        source?.let { return it.skyTopY(wx, wz) }
        val key = columnCacheKey(wx, wz)
        val cache = surfaceTopCache.get()!!
        val cached = cache.get(key)
        if (cached != Int.MIN_VALUE) return cached
        val v = max(natural.waterLevelAt(wx.toDouble(), wz.toDouble()), natural.height(wx.toDouble(), wz.toDouble()).toInt()) + 1
        cache.put(key, v)
        return v
    }

    /**
     * Niveau de lumière du ciel (0..15) au voxel (lx,ly,lz) relatif à [baseChunk], cross-chunk.
     * Si le chunk visé n'est pas chargé : repli analytique — 15 si le voxel est au-dessus de la
     * surface (ciel ouvert), 0 sinon. Garde les bords des chunks de surface éclairés avant que le
     * voisin se charge ; la cascade de LightEngine corrige une fois le voisin disponible.
     */
    fun skyLightAt(baseChunk: Chunk, lx: Int, ly: Int, lz: Int, cache: ChunkLookupCache? = null): Int {
        if (lx in 0 until CHUNK_SIZE && ly in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE)
            return baseChunk.skyAt(lx, ly, lz)
        val wx = baseChunk.worldX + lx; val wy = baseChunk.worldY + ly; val wz = baseChunk.worldZ + lz
        val ncx = Math.floorDiv(wx, CHUNK_SIZE); val ncy = Math.floorDiv(wy, CHUNK_SIZE); val ncz = Math.floorDiv(wz, CHUNK_SIZE)
        val neighbor = if (cache != null) cache.getOrPut(chunkKey(ncx, ncy, ncz)) { getChunk(ncx, ncy, ncz) } else getChunk(ncx, ncy, ncz)
        if (neighbor != null && neighbor.generated)
            return neighbor.skyAt(wx - ncx * CHUNK_SIZE, wy - ncy * CHUNK_SIZE, wz - ncz * CHUNK_SIZE)
        // Natural terrain's analytic sky starts above max(sea level, ground).
        // Missing deep neighbours therefore need no height lookup at all.
        if (source == null && wy <= SEA_LEVEL) return 0
        return if (wy >= surfaceTopY(wx, wz)) 15 else 0
    }
}
