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

    fun build(chunk: Chunk, world: World): FloatArray {
        val buf = solidScratch.get()!!.also { it.clear() }
        val cache = World.ChunkLookupCache()

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
            if (shouldRenderFace(block, above))                                        addFace(buf, x, y, z, 0, block, above, meta, knotFace, skyOf(chunk, world, lx, ly + 1, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly - 1, lz, cache)))  addFace(buf, x, y, z, 1, block, AIR,  meta, knotFace, skyOf(chunk, world, lx, ly - 1, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx + 1, ly, lz, cache)))  addFace(buf, x, y, z, 2, block, above, meta, knotFace, skyOf(chunk, world, lx + 1, ly, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx - 1, ly, lz, cache)))  addFace(buf, x, y, z, 3, block, above, meta, knotFace, skyOf(chunk, world, lx - 1, ly, lz, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz + 1, cache)))  addFace(buf, x, y, z, 4, block, above, meta, knotFace, skyOf(chunk, world, lx, ly, lz + 1, cache))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz - 1, cache)))  addFace(buf, x, y, z, 5, block, above, meta, knotFace, skyOf(chunk, world, lx, ly, lz - 1, cache))
        }
        if (buf.size == 0) return emptyVertices
        // Read the reusable builder directly; avoid a second full-sized intermediate array.
        val raw = buf.data
        val colored = FloatArray(buf.size / 7 * 11)
        val blend = ClimateBlend(chunk.worldX, chunk.worldZ, BlockRegistry.vividStyle, world::vegetationClimateAt)
        for (vertex in 0 until buf.size / 7) {
            val src = vertex * 7; val dst = vertex * 11
            raw.copyInto(colored, dst, src, src + 7)
            val mask = BlockRegistry.climateMask(raw[src + 5].toInt() % 4096)
            if (mask != 0 && chunk.worldY >= 0) {
                blend.writeDelta(raw[src], raw[src + 2], colored, dst + 7)
                colored[dst + 10] = mask.toFloat()
            }
        }
        return colored
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

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, block: Short, above: Short, meta: Byte = 0, knotFace: Int = -1, sky: Float = 1f) {
        val baseLayer = BlockRegistry.getLayerForFace(block, face, above, meta)
        val layer = if (face == knotFace) BlockRegistry.knotLayer(baseLayer)
            else baseLayer
        val rotCW  = face > 1
        val packed = (if (BlockRegistry.lightEmission(block) > 0) 7 else face) * 4096f + layer.toFloat()
        when (face) {
            0 -> buf.quad(x,y+1,z,  x+1,y+1,z,  x+1,y+1,z+1, x,y+1,z+1, packed, false, sky)
            1 -> buf.quad(x,y,z+1,  x+1,y,z+1,  x+1,y,z,     x,y,z,     packed, false, sky)
            2 -> buf.quad(x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z,   x+1,y,z,   packed, rotCW, sky)
            3 -> buf.quad(x,y,z,    x,y+1,z,     x,y+1,z+1,   x,y,z+1,   packed, rotCW, sky)
            4 -> buf.quad(x,y,z+1,  x,y+1,z+1,   x+1,y+1,z+1, x+1,y,z+1, packed, rotCW, sky)
            5 -> buf.quad(x+1,y,z,  x+1,y+1,z,   x,y+1,z,     x,y,z,     packed, rotCW, sky)
        }
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
            val below = world.neighborBlock(chunk, lx, ly - 1, lz, cache)
            val east  = world.neighborBlock(chunk, lx + 1, ly, lz, cache)
            val west  = world.neighborBlock(chunk, lx - 1, ly, lz, cache)
            val south = world.neighborBlock(chunk, lx, ly, lz + 1, cache)
            val north = world.neighborBlock(chunk, lx, ly, lz - 1, cache)

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
