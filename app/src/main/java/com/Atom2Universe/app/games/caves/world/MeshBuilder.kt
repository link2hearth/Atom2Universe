package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.MeadowTextures
import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants

internal object MeshBuilder {
    private val solidScratch = ThreadLocal.withInitial { GrowableFloatArray() }
    private val waterScratch = ThreadLocal.withInitial { GrowableFloatArray() }
    private val emptyVertices = FloatArray(0)
    private val faceTriangles = intArrayOf(0, 1, 2, 0, 2, 3)
    private val faceOffsets = arrayOf(intArrayOf(0,1,0), intArrayOf(0,-1,0),
        intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,0,1), intArrayOf(0,0,-1))

    /** Maillage solide d'un chunk, tassé pour la carte graphique (voir [PackedMesh]). */
    fun build(chunk: Chunk, world: World, mergeFaces: Boolean = true): PackedMesh {
        val buf = solidScratch.get()!!.also { it.clear() }
        val cache = World.ChunkLookupCache()
        val blend = ClimateBlend(chunk.worldX, chunk.worldZ, BlockRegistry.vividStyle, world::vegetationClimateAt)
        val greedy = greedyScratch.get()!!.also { it.reset() }

        for (lz in 0 until CHUNK_SIZE)
            for (ly in 0 until CHUNK_SIZE)
                for (lx in 0 until CHUNK_SIZE) {
            val block = chunk.blockAt(lx, ly, lz)
            if (block == AIR || isWater(block)) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()

            if (block == TORCH) {
                addTorch(buf, x, y, z, chunk.metaAt(lx, ly, lz), chunk.skyAt(lx, ly, lz) / 15f)
                continue
            }
            if (isDecoration(block)) {
                val below = world.neighborBlock(chunk, lx, ly - 1, lz, cache)
                val offset = if (below == com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND) -1f/16f else 0f
                addCrossSprite(buf, x, y + offset, z, block, chunk.skyAt(lx, ly, lz) / 15f)
                continue
            }

            val meta  = chunk.metaAt(lx, ly, lz)
            val definition = if (BlockRegistry.isPartial(block)) BlockRegistry.get(block) else null
            if (definition != null && (definition.stairs || definition.slab || definition.blockHeight < 1f)) {
                val sky = skyOf(chunk, world, lx, ly, lz, cache)
                val mask = StairConnections.maskAt(chunk.worldX + lx, chunk.worldY + ly, chunk.worldZ + lz,
                    { bx, by, bz -> world.blockAt(bx, by, bz, cache) }, world::metaAt)
                for (face in PartialBlockModel.faces(meta, definition.slab, definition.blockHeight, mask)) {
                    // Low soil keeps its recessed top, but buried bottoms and shared sides
                    // need no vertices. Do not apply full-face occlusion to stairs or slabs.
                    if (definition.blockHeight < 1f && face.direction != 0) {
                        val offset = faceOffsets[face.direction]
                        val neighbor = world.neighborBlock(chunk, lx+offset[0], ly+offset[1], lz+offset[2], cache)
                        val other = BlockRegistry.get(neighbor)
                        if (neighbor != AIR && other != null && !other.decoration && !other.transparent &&
                            !other.water && !other.stairs && !other.slab &&
                            other.blockHeight >= (if (face.direction == 1) 1f else definition.blockHeight)) continue
                    }
                    val packed = face.direction * 4096f + BlockRegistry.getLayerForFace(block, face.direction, AIR)
                    for (i in faceTriangles) {
                        val v = face.vertices[i]
                        val u = when (face.direction) { 2, 3 -> v[2]; else -> v[0] }
                        val vv = if (face.direction < 2) v[2] else 1f - v[1]
                        buf.add7(x+v[0], y+v[1], z+v[2], u, vv, packed, sky)
                    }
                }
                continue
            }
            val knotFace = if (BlockRegistry.isWood(block))
                MeadowTextures.rareKnotFace(chunk.worldX + lx, chunk.worldY + ly, chunk.worldZ + lz)
                else -1
            val above = world.neighborBlock(chunk, lx, ly + 1, lz, cache)
            // Faces pleines : mises de côté pour être fusionnées (voir [mergeCubeFaces]).
            if (shouldRenderFace(block, above))
                collectCube(greedy, chunk, world, cache, blend, 0, lx, ly, lz, block, above, meta, knotFace,
                    skyOf(chunk, world, lx, ly + 1, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly - 1, lz, cache)))
                collectCube(greedy, chunk, world, cache, blend, 1, lx, ly, lz, block, AIR, meta, knotFace,
                    skyOf(chunk, world, lx, ly - 1, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx + 1, ly, lz, cache)))
                collectCube(greedy, chunk, world, cache, blend, 2, lx, ly, lz, block, above, meta, knotFace,
                    skyOf(chunk, world, lx + 1, ly, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx - 1, ly, lz, cache)))
                collectCube(greedy, chunk, world, cache, blend, 3, lx, ly, lz, block, above, meta, knotFace,
                    skyOf(chunk, world, lx - 1, ly, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz + 1, cache)))
                collectCube(greedy, chunk, world, cache, blend, 4, lx, ly, lz, block, above, meta, knotFace,
                    skyOf(chunk, world, lx, ly, lz + 1, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz - 1, cache)))
                collectCube(greedy, chunk, world, cache, blend, 5, lx, ly, lz, block, above, meta, knotFace,
                    skyOf(chunk, world, lx, ly, lz - 1, cache))
        }
        mergeCubeFaces(greedy, mergeFaces)
        val merged = greedy.out
        if (buf.size == 0 && merged.size == 0) return PackedMesh.EMPTY
        // Read the reusable builder directly; avoid a second full-sized intermediate array.
        val raw = buf.data
        val colored = FloatArray(buf.size / 7 * 12 + merged.size)
        merged.data.copyInto(colored, buf.size / 7 * 12, 0, merged.size)
        for (vertex in 0 until buf.size / 7) {
            val src = vertex * 7; val dst = vertex * 12
            raw.copyInto(colored, dst, src, src + 7)
            colored[dst + 11] = vertexBlockLight(chunk, world, cache, raw[src], raw[src + 1], raw[src + 2],
                (raw[src + 5] / 4096f).toInt())
            val mask = BlockRegistry.climateMask(raw[src + 5].toInt() % 4096)
            if (mask != 0 && chunk.worldY >= 0) {
                blend.writeDelta(raw[src], raw[src + 2], colored, dst + 7)
                colored[dst + 10] = mask.toFloat()
            }
        }
        return PackedMesh.pack(colored, 12)
    }

    /**
     * Étape B de la distance de vue : les faces pleines de même aspect (texture, ciel, torches,
     * teinte de climat) sont réunies en rectangles, tranche par tranche, comme un carrelage posé
     * en grandes dalles. Une plaine de 16 × 16 blocs d'herbe devient une face au lieu de 256.
     * La texture se répète d'elle-même (GL_REPEAT) : ses coordonnées vont de 0 à la taille du
     * rectangle. Une face dont les quatre coins diffèrent (dégradé de lumière ou de teinte)
     * reste seule, avec ses valeurs par sommet : l'éclairage ne change pas d'un pixel.
     */
    private class GreedyScratch {
        val packed = IntArray(6 * 4096)
        val sky = FloatArray(6 * 4096)
        val light = FloatArray(6 * 4096)
        val tint = FloatArray(6 * 4096 * 4)
        val out = GrowableFloatArray(8192)
        val cornerLight = FloatArray(4)
        val cornerTint = FloatArray(16)
        val corner = FloatArray(20)   // x, y, z, u, v des quatre coins
        fun reset() { packed.fill(-1); out.clear() }
    }
    private val greedyScratch = ThreadLocal.withInitial { GreedyScratch() }

    // Coins d'une face pleine d'un bloc, dans l'ordre où [emitFace] les écrit.
    private val cubeCorners = arrayOf(
        intArrayOf(0,1,0, 1,1,0, 1,1,1, 0,1,1), intArrayOf(0,0,1, 1,0,1, 1,0,0, 0,0,0),
        intArrayOf(1,0,1, 1,1,1, 1,1,0, 1,0,0), intArrayOf(0,0,0, 0,1,0, 0,1,1, 0,0,1),
        intArrayOf(0,0,1, 0,1,1, 1,1,1, 1,0,1), intArrayOf(1,0,0, 1,1,0, 0,1,0, 0,0,0))

    private fun collectCube(g: GreedyScratch, chunk: Chunk, world: World, cache: World.ChunkLookupCache,
                            blend: ClimateBlend, face: Int, lx: Int, ly: Int, lz: Int, block: Short,
                            above: Short, meta: Byte, knotFace: Int, sky: Float) {
        val baseLayer = BlockRegistry.getLayerForFace(block, face, above, meta)
        val layer = if (face == knotFace) BlockRegistry.knotLayer(baseLayer) else baseLayer
        val marker = if (BlockRegistry.lightEmission(block) > 0) 7 else face
        val packed = marker * 4096 + layer
        val corners = cubeCorners[face]
        val light = g.cornerLight; val tint = g.cornerTint
        val mask = if (chunk.worldY >= 0) BlockRegistry.climateMask(layer) else 0
        var uniform = true
        for (c in 0 until 4) {
            val x = (lx + corners[c * 3]).toFloat(); val y = (ly + corners[c * 3 + 1]).toFloat()
            val z = (lz + corners[c * 3 + 2]).toFloat()
            light[c] = vertexBlockLight(chunk, world, cache, x, y, z, marker)
            if (mask != 0) { blend.writeDelta(x, z, tint, c * 4); tint[c * 4 + 3] = mask.toFloat() }
            else { tint[c * 4] = 0f; tint[c * 4 + 1] = 0f; tint[c * 4 + 2] = 0f; tint[c * 4 + 3] = 0f }
            if (c > 0 && (light[c] != light[0] || tint[c * 4] != tint[0] ||
                    tint[c * 4 + 1] != tint[1] || tint[c * 4 + 2] != tint[2])) uniform = false
        }
        if (!uniform) {
            emitFace(g, face, lx.toFloat(), ly.toFloat(), lz.toFloat(), lx + 1f, ly + 1f, lz + 1f,
                packed.toFloat(), sky)
            return
        }
        val cell = face * 4096 + lx + ly * 16 + lz * 256
        g.packed[cell] = packed; g.sky[cell] = sky; g.light[cell] = light[0]
        tint.copyInto(g.tint, cell * 4, 0, 4)
    }

    /** Case (tranche, a, b) d'une direction de face → indice de cellule. */
    private fun cellOf(face: Int, slice: Int, a: Int, b: Int): Int = face * 4096 + when (face) {
        0, 1 -> a + slice * 16 + b * 256        // tranche = y, a = x, b = z
        2, 3 -> slice + b * 16 + a * 256        // tranche = x, a = z, b = y
        else -> a + b * 16 + slice * 256        // tranche = z, a = x, b = y
    }

    private fun sameCell(g: GreedyScratch, i: Int, j: Int): Boolean =
        g.packed[j] == g.packed[i] && g.sky[j] == g.sky[i] && g.light[j] == g.light[i] &&
            g.tint[j * 4] == g.tint[i * 4] && g.tint[j * 4 + 1] == g.tint[i * 4 + 1] &&
            g.tint[j * 4 + 2] == g.tint[i * 4 + 2] && g.tint[j * 4 + 3] == g.tint[i * 4 + 3]

    private fun mergeCubeFaces(g: GreedyScratch, merge: Boolean) {
        for (face in 0 until 6) for (slice in 0 until 16) for (b in 0 until 16) {
            var a = 0
            while (a < 16) {
                val start = cellOf(face, slice, a, b)
                if (g.packed[start] < 0) { a++; continue }
                var w = 1
                while (merge && a + w < 16 && sameCell(g, start, cellOf(face, slice, a + w, b))) w++
                var h = 1
                grow@ while (merge && b + h < 16) {
                    for (k in 0 until w) if (!sameCell(g, start, cellOf(face, slice, a + k, b + h))) break@grow
                    h++
                }
                g.cornerLight.fill(g.light[start])
                for (c in 0 until 4) g.tint.copyInto(g.cornerTint, c * 4, start * 4, start * 4 + 4)
                val packed = g.packed[start].toFloat(); val sky = g.sky[start]
                for (hb in 0 until h) for (k in 0 until w) g.packed[cellOf(face, slice, a + k, b + hb)] = -1
                val s = slice.toFloat(); val a0 = a.toFloat(); val a1 = (a + w).toFloat()
                val b0 = b.toFloat(); val b1 = (b + h).toFloat()
                when (face) {
                    0, 1 -> emitFace(g, face, a0, s, b0, a1, s + 1f, b1, packed, sky)
                    2, 3 -> emitFace(g, face, s, b0, a0, s + 1f, b1, a1, packed, sky)
                    else -> emitFace(g, face, a0, b0, s, a1, b1, s + 1f, packed, sky)
                }
                a += w
            }
        }
    }

    /**
     * Une face pleine couvrant la boîte [x0,x1]×[y0,y1]×[z0,z1], au format large (12 flottants),
     * avec la lumière et la teinte de `g.cornerLight` / `g.cornerTint` : la texture garde le sens
     * qu'elle a sur un bloc seul, répétée sur toute la taille du rectangle.
     */
    private fun emitFace(g: GreedyScratch, face: Int, x0: Float, y0: Float, z0: Float,
                         x1: Float, y1: Float, z1: Float, packed: Float, sky: Float) {
        val wx = x1 - x0; val hy = y1 - y0; val wz = z1 - z0
        val c = g.corner
        fun corner(i: Int, x: Float, y: Float, z: Float, u: Float, v: Float) {
            c[i * 5] = x; c[i * 5 + 1] = y; c[i * 5 + 2] = z; c[i * 5 + 3] = u; c[i * 5 + 4] = v
        }
        when (face) {
            0 -> { corner(0, x0, y1, z0, 0f, 0f); corner(1, x1, y1, z0, wx, 0f)
                   corner(2, x1, y1, z1, wx, wz); corner(3, x0, y1, z1, 0f, wz) }
            1 -> { corner(0, x0, y0, z1, 0f, 0f); corner(1, x1, y0, z1, wx, 0f)
                   corner(2, x1, y0, z0, wx, wz); corner(3, x0, y0, z0, 0f, wz) }
            2 -> { corner(0, x1, y0, z1, 0f, hy); corner(1, x1, y1, z1, 0f, 0f)
                   corner(2, x1, y1, z0, wz, 0f); corner(3, x1, y0, z0, wz, hy) }
            3 -> { corner(0, x0, y0, z0, 0f, hy); corner(1, x0, y1, z0, 0f, 0f)
                   corner(2, x0, y1, z1, wz, 0f); corner(3, x0, y0, z1, wz, hy) }
            4 -> { corner(0, x0, y0, z1, 0f, hy); corner(1, x0, y1, z1, 0f, 0f)
                   corner(2, x1, y1, z1, wx, 0f); corner(3, x1, y0, z1, wx, hy) }
            else -> { corner(0, x1, y0, z0, 0f, hy); corner(1, x1, y1, z0, 0f, 0f)
                      corner(2, x0, y1, z0, wx, 0f); corner(3, x0, y0, z0, wx, hy) }
        }
        val out = g.out; val light = g.cornerLight; val tint = g.cornerTint
        for (i in faceTriangles) {
            out.add(c[i * 5]); out.add(c[i * 5 + 1]); out.add(c[i * 5 + 2])
            out.add(c[i * 5 + 3]); out.add(c[i * 5 + 4]); out.add(packed); out.add(sky)
            out.add(tint[i * 4]); out.add(tint[i * 4 + 1]); out.add(tint[i * 4 + 2]); out.add(tint[i * 4 + 3])
            out.add(light[i])
        }
    }

    /**
     * Lumière des torches d'un sommet (0..1) : moyenne des quatre cases d'air qui le touchent du
     * côté éclairé de la face. C'est la moyenne qui lisse d'un sommet à l'autre — une seule case
     * par face donnerait des carreaux ; une case pleine n'entre pas dans le compte, sinon chaque
     * coin de mur noircirait. La face émissive (flamme, lampe) reste à pleine lumière.
     */
    private fun vertexBlockLight(chunk: Chunk, world: World, cache: World.ChunkLookupCache,
                                 x: Float, y: Float, z: Float, face: Int): Float {
        if (face !in 0..5) return 1f
        val n = faceOffsets[face]
        val sx = x + n[0] * .5f; val sy = y + n[1] * .5f; val sz = z + n[2] * .5f
        // Les deux axes du plan de la face, décalés d'un demi-bloc de part et d'autre du sommet.
        val ax = if (n[0] != 0) 0f else .5f
        val ay = if (n[1] != 0) 0f else .5f
        val az = if (n[2] != 0) 0f else .5f
        var sum = 0; var count = 0
        for (i in 0 until 4) {
            // Deux des trois décalages varient ; le troisième (le long de la normale) vaut 0.
            val first = if (i and 1 == 0) -1f else 1f
            val second = if (i and 2 == 0) -1f else 1f
            val ox: Float; val oy: Float; val oz: Float
            when {
                n[0] != 0 -> { ox = 0f; oy = ay * first; oz = az * second }
                n[1] != 0 -> { ox = ax * first; oy = 0f; oz = az * second }
                else -> { ox = ax * first; oy = ay * second; oz = 0f }
            }
            val level = world.passableBlockLightAt(chunk, kotlin.math.floor(sx + ox).toInt(),
                kotlin.math.floor(sy + oy).toInt(), kotlin.math.floor(sz + oz).toInt(), cache)
            if (level < 0) continue
            sum += level; count++
        }
        return if (count == 0) 0f else sum / (count * 15f)
    }

    // Lumière du ciel (0..1) du voxel d'air adjacent à une face.
    private fun skyOf(chunk: Chunk, world: World, lx: Int, ly: Int, lz: Int,
                      cache: World.ChunkLookupCache? = null): Float {
        val block = world.neighborBlock(chunk, lx, ly, lz, cache)
        var light = world.skyLightAt(chunk, lx, ly, lz, cache)
        if (!BlockRegistry.isPartial(block)) return light / 15f
        val def = BlockRegistry.get(block) ?: return light / 15f
        val wx = chunk.worldX + lx; val wy = chunk.worldY + ly; val wz = chunk.worldZ + lz
        val cells = PartialBlockModel.boxes(world.metaAt(wx, wy, wz), def.slab, def.blockHeight,
            StairConnections.maskAt(wx, wy, wz, { x, y, z -> world.blockAt(x, y, z, cache) }, world::metaAt))
        // The voxel itself is opaque to skylight, but its empty part sees adjacent air.
        // Sample only open boundaries: a slab's solid half must not light a ceiling through its roof.
        for ((face, offset) in faceOffsets.withIndex()) {
            if (!PartialBlockModel.hasOpenBoundary(cells, face)) continue
            light = maxOf(light, world.skyLightAt(chunk, lx + offset[0], ly + offset[1], lz + offset[2], cache))
        }
        return light / 15f
    }
    private fun isVisible(block: Short) =
        block == AIR || isDecoration(block) || isTransparent(block) || isWater(block) || BlockRegistry.isPartial(block)

    private fun shouldRenderFace(block: Short, neighbor: Short): Boolean {
        if (!isVisible(neighbor)) return false
        if (isTransparent(block) && block == neighbor) return false
        return true
    }

    private fun addTorch(buf: GrowableFloatArray, x: Float, y: Float, z: Float, meta: Byte, sky: Float) {
        for (part in TorchModel.parts) {
            val vertices = TorchModel.vertices(meta, part)
            for ((faceIndex, face) in TorchModel.faces.withIndex()) {
                // Face marker 6 is emissive, independent of daylight and face shading.
                val packed = (if (part.material >= 2) 6 else faceIndex) * 4096f +
                    BlockRegistry.torchLayers[part.material]
                val a = vertices[face[0]]; val b = vertices[face[1]]
                val c = vertices[face[2]]; val d = vertices[face[3]]
                buf.quad(x+a.x,y+a.y,z+a.z, x+b.x,y+b.y,z+b.z,
                    x+c.x,y+c.y,z+c.z, x+d.x,y+d.y,z+d.z, packed, faceIndex > 1, sky)
            }
        }
    }

    private fun addCrossSprite(buf: GrowableFloatArray, x: Float, y: Float, z: Float, block: Short, sky: Float = 1f) {
        val layer = BlockRegistry.getLayerForDecoration(block)
        val margin = BlockRegistry.getSpriteMargin(block)
        val h      = BlockRegistry.getSpriteHeight(block)
        val packed = (if (BlockRegistry.lightEmission(block) > 0) 7 * 4096f else 0f) + layer.toFloat()

        buf.add7(x+margin,   y,   z+0.5f, 0f, 1f, packed, sky)
        buf.add7(x+1f-margin,y,   z+0.5f, 1f, 1f, packed, sky)
        buf.add7(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed, sky)
        buf.add7(x+margin,   y,   z+0.5f, 0f, 1f, packed, sky)
        buf.add7(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed, sky)
        buf.add7(x+margin,   y+h, z+0.5f, 0f, 0f, packed, sky)

        buf.add7(x+0.5f, y,   z+margin,    0f, 1f, packed, sky)
        buf.add7(x+0.5f, y,   z+1f-margin, 1f, 1f, packed, sky)
        buf.add7(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed, sky)
        buf.add7(x+0.5f, y,   z+margin,    0f, 1f, packed, sky)
        buf.add7(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed, sky)
        buf.add7(x+0.5f, y+h, z+margin,    0f, 0f, packed, sky)
        FarmShowcasePlants.sample(block)?.let { (crop, stage) ->
            if (FarmShowcasePlants.hasLeafTiers(crop) && stage > 0) {
                val growth = FarmShowcasePlants.stemGrowth(stage)
                val foliage = BlockRegistry.get(block)!!.layerSide.toFloat()
                val crown = h * FarmShowcasePlants.crownHeight(crop) * growth
                for (tier in 0..1) {
                    val halfWidth = (if (tier == 0) .85f else .7f) * growth
                    val cy = y + crown * (if (tier == 0) .65f else 1f)
                    val tilt = .12f * growth * (if (tier == 0) 1f else -1f)
                    // Shallow tilted leaf planes above the fruit-bearing branches, not copies
                    // of the entire plant. The upper tier is turned for a less rigid silhouette.
                    val angle = if (tier == 0) 0f else (Math.PI / 6).toFloat()
                    val cos = kotlin.math.cos(angle); val sin = kotlin.math.sin(angle)
                    fun point(dx: Float, dz: Float) = floatArrayOf(
                        x + .5f + dx * cos - dz * sin,
                        cy + if (dz < 0f) -tilt else tilt,
                        z + .5f + dx * sin + dz * cos)
                    val a = point(-halfWidth, -halfWidth); val b = point(halfWidth, -halfWidth)
                    val c = point(halfWidth, halfWidth); val d = point(-halfWidth, halfWidth)
                    buf.quad(a[0],a[1],a[2], b[0],b[1],b[2], c[0],c[1],c[2], d[0],d[1],d[2],
                        foliage, false, sky)
                }
            }
        }
    }

    private fun GrowableFloatArray.quad(
        x0:Float,y0:Float,z0:Float,
        x1:Float,y1:Float,z1:Float,
        x2:Float,y2:Float,z2:Float,
        x3:Float,y3:Float,z3:Float,
        packed:Float, rotCW:Boolean, sky:Float = 1f
    ) {
        if (rotCW) {
            add7(x0,y0,z0, 0f,1f, packed, sky); add7(x1,y1,z1, 0f,0f, packed, sky); add7(x2,y2,z2, 1f,0f, packed, sky)
            add7(x0,y0,z0, 0f,1f, packed, sky); add7(x2,y2,z2, 1f,0f, packed, sky); add7(x3,y3,z3, 1f,1f, packed, sky)
        } else {
            add7(x0,y0,z0, 0f,0f, packed, sky); add7(x1,y1,z1, 1f,0f, packed, sky); add7(x2,y2,z2, 1f,1f, packed, sky)
            add7(x0,y0,z0, 0f,0f, packed, sky); add7(x2,y2,z2, 1f,1f, packed, sky); add7(x3,y3,z3, 0f,1f, packed, sky)
        }
    }

    private fun waterFaceVisible(block: Short): Boolean = !isWater(block) &&
        (block == AIR || isDecoration(block) || isTransparent(block) || BlockRegistry.isPartial(block))

    fun buildWater(chunk: Chunk, world: World): FloatArray {
        val buf = waterScratch.get()!!.also { it.clear() }
        val cache = World.ChunkLookupCache()
        for (lz in 0 until CHUNK_SIZE)
            for (ly in 0 until CHUNK_SIZE)
                for (lx in 0 until CHUNK_SIZE) {
            if (!isWater(chunk.blockAt(lx, ly, lz))) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()
            val wx = chunk.worldX + lx; val wy = chunk.worldY + ly; val wz = chunk.worldZ + lz
            val packed = 0f

            val above = world.neighborBlock(chunk, lx, ly + 1, lz, cache)
            // Côtés et dessous : un voisin pas encore chargé compte comme de l'eau. Sinon chaque bord
            // de la zone chargée dresse un grand rideau d'eau vertical en pleine mer, qui avance avec
            // le joueur. Le chargement du voisin reconstruit ce maillage, et la vraie rive apparaît.
            val below = world.neighborBlockOr(chunk, lx, ly - 1, lz, WATER, cache)
            val east  = world.neighborBlockOr(chunk, lx + 1, ly, lz, WATER, cache)
            val west  = world.neighborBlockOr(chunk, lx - 1, ly, lz, WATER, cache)
            val south = world.neighborBlockOr(chunk, lx, ly, lz + 1, WATER, cache)
            val north = world.neighborBlockOr(chunk, lx, ly, lz - 1, WATER, cache)

            // Les coins partagent leurs hauteurs avec les cases voisines : le flux crée ainsi
            // une pente continue au lieu de cubes d'eau empilés.
            val needsSurface = waterFaceVisible(above) || waterFaceVisible(east) || waterFaceVisible(west) || waterFaceVisible(south) || waterFaceVisible(north)
            val hNW: Float
            val hNE: Float
            val hSE: Float
            val hSW: Float
            if (needsSurface) {
                val hC  = waterHeight(world, wx,     wy, wz, cache)
                val hW  = waterHeight(world, wx - 1, wy, wz, cache)
                val hE  = waterHeight(world, wx + 1, wy, wz, cache)
                val hN  = waterHeight(world, wx, wy, wz - 1, cache)
                val hS  = waterHeight(world, wx, wy, wz + 1, cache)
                val hNW0 = waterHeight(world, wx - 1, wy, wz - 1, cache)
                val hNE0 = waterHeight(world, wx + 1, wy, wz - 1, cache)
                val hSE0 = waterHeight(world, wx + 1, wy, wz + 1, cache)
                val hSW0 = waterHeight(world, wx - 1, wy, wz + 1, cache)
                hNW = averageWaterHeights(hC, hW, hN, hNW0)
                hNE = averageWaterHeights(hC, hE, hN, hNE0)
                hSE = averageWaterHeights(hC, hE, hS, hSE0)
                hSW = averageWaterHeights(hC, hW, hS, hSW0)
            } else {
                hNW = 1f; hNE = 1f; hSE = 1f; hSW = 1f
            }

            if (waterFaceVisible(above))
                buf.quad(x,y+hNW,z, x+1f,y+hNE,z, x+1f,y+hSE,z+1f, x,y+hSW,z+1f, packed, false, skyOf(chunk, world, lx, ly + 1, lz, cache))
            if (waterFaceVisible(below))
                buf.quad(x,y,z+1f, x+1f,y,z+1f, x+1f,y,z, x,y,z, packed, false, skyOf(chunk, world, lx, ly - 1, lz, cache))
            if (waterFaceVisible(east))
                buf.quad(x+1f,y,z+1f, x+1f,y+hSE,z+1f, x+1f,y+hNE,z, x+1f,y,z, packed, true, skyOf(chunk, world, lx + 1, ly, lz, cache))
            if (waterFaceVisible(west))
                buf.quad(x,y,z, x,y+hNW,z, x,y+hSW,z+1f, x,y,z+1f, packed, true, skyOf(chunk, world, lx - 1, ly, lz, cache))
            if (waterFaceVisible(south))
                buf.quad(x,y,z+1f, x,y+hSW,z+1f, x+1f,y+hSE,z+1f, x+1f,y,z+1f, packed, true, skyOf(chunk, world, lx, ly, lz + 1, cache))
            if (waterFaceVisible(north))
                buf.quad(x+1f,y,z, x+1f,y+hNE,z, x,y+hNW,z, x,y,z, packed, true, skyOf(chunk, world, lx, ly, lz - 1, cache))
        }
        return buf.toFloatArray()
    }

    private fun waterHeight(world: World, wx: Int, wy: Int, wz: Int, cache: World.ChunkLookupCache? = null): Float {
        val block = world.blockAt(wx, wy, wz, cache)
        if (!isWater(block)) return 0f
        // Seules les cellules couvertes d'eau sont pleines, pour raccorder les chutes.
        // Une surface libre reste légèrement sous le bord du bloc, même à niveau 0.
        if (isWater(world.blockAt(wx, wy + 1, wz, cache))) return 1f
        val level = world.waterFlowLevelKnown(block, wx, wy, wz)
        return (8f / 9f) * (9 - level.coerceIn(0, 8)) / 9f
    }

    private fun averageWaterHeights(a: Float, b: Float, c: Float, d: Float): Float {
        // Une colonne pleine doit rejoindre exactement l'étage supérieur.
        if (a >= 1f || b >= 1f || c >= 1f || d >= 1f) return 1f
        var total = 0f
        var count = 0
        if (a > 0f) { total += a; count++ }
        if (b > 0f) { total += b; count++ }
        if (c > 0f) { total += c; count++ }
        if (d > 0f) { total += d; count++ }
        return if (count == 0) 0f else total / count
    }

    /** Même interpolation triangulaire que le mesh, sans créer de géométrie. */
    fun isPointInWater(world: World, x: Double, y: Double, z: Double): Boolean {
        val wx = kotlin.math.floor(x).toInt()
        val wy = kotlin.math.floor(y).toInt()
        val wz = kotlin.math.floor(z).toInt()
        if (!isWater(world.blockAt(wx, wy, wz))) return false
        if (isWater(world.blockAt(wx, wy + 1, wz))) return true
        val cache = World.ChunkLookupCache()
        val c = waterHeight(world, wx, wy, wz, cache)
        val w = waterHeight(world, wx - 1, wy, wz, cache)
        val e = waterHeight(world, wx + 1, wy, wz, cache)
        val n = waterHeight(world, wx, wy, wz - 1, cache)
        val s = waterHeight(world, wx, wy, wz + 1, cache)
        val nw = averageWaterHeights(c, w, n, waterHeight(world, wx - 1, wy, wz - 1, cache))
        val ne = averageWaterHeights(c, e, n, waterHeight(world, wx + 1, wy, wz - 1, cache))
        val se = averageWaterHeights(c, e, s, waterHeight(world, wx + 1, wy, wz + 1, cache))
        val sw = averageWaterHeights(c, w, s, waterHeight(world, wx - 1, wy, wz + 1, cache))
        val fx = x - wx; val fz = z - wz
        val height = if (fx >= fz) nw + (ne - nw) * fx + (se - ne) * fz
                     else nw + (se - sw) * fx + (sw - nw) * fz
        return y - wy < height
    }

    private fun GrowableFloatArray.add7(x:Float,y:Float,z:Float, u:Float,v:Float,p:Float, sky:Float) {
        add(x); add(y); add(z); add(u); add(v); add(p); add(sky)
    }

    private class GrowableFloatArray(capacity: Int = 16384) {
        var data = FloatArray(capacity)
            private set
        var size = 0
            private set
        fun clear() {
            size = 0
            // Do not pin an exceptional, multi-megabyte chunk on every coroutine worker.
            if (data.size > 131072) data = FloatArray(16384)
        }
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toFloatArray(): FloatArray = if (size == 0) emptyVertices else data.copyOf(size)
    }
}
