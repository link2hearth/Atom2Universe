package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

internal object MeshBuilder {

    fun build(chunk: Chunk, world: World): FloatArray {
        val buf = GrowableFloatArray()

        for (lz in 0 until CHUNK_SIZE)
            for (ly in 0 until CHUNK_SIZE)
                for (lx in 0 until CHUNK_SIZE) {
            val block = chunk.blockAt(lx, ly, lz)
            if (block == AIR || isWater(block)) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()

            if (isDecoration(block)) {
                addCrossSprite(buf, x, y, z, block, chunk.skyAt(lx, ly, lz) / 15f)
                continue
            }

            val meta  = chunk.metaAt(lx, ly, lz)
            val above = world.neighborBlock(chunk, lx, ly + 1, lz)
            if (shouldRenderFace(block, above))                                        addFace(buf, x, y, z, 0, block, above, meta, skyOf(chunk, world, lx, ly + 1, lz))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly - 1, lz)))  addFace(buf, x, y, z, 1, block, AIR,  meta, skyOf(chunk, world, lx, ly - 1, lz))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx + 1, ly, lz)))  addFace(buf, x, y, z, 2, block, above, meta, skyOf(chunk, world, lx + 1, ly, lz))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx - 1, ly, lz)))  addFace(buf, x, y, z, 3, block, above, meta, skyOf(chunk, world, lx - 1, ly, lz))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz + 1)))  addFace(buf, x, y, z, 4, block, above, meta, skyOf(chunk, world, lx, ly, lz + 1))
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz - 1)))  addFace(buf, x, y, z, 5, block, above, meta, skyOf(chunk, world, lx, ly, lz - 1))
        }
        return buf.toFloatArray()
    }

    // Lumière du ciel (0..1) du voxel d'air adjacent à une face.
    private fun skyOf(chunk: Chunk, world: World, lx: Int, ly: Int, lz: Int): Float =
        world.skyLightAt(chunk, lx, ly, lz) / 15f

    private fun isVisible(block: Short) =
        block == AIR || isDecoration(block) || isTransparent(block) || isWater(block)

    private fun shouldRenderFace(block: Short, neighbor: Short): Boolean {
        if (!isVisible(neighbor)) return false
        if (isTransparent(block) && block == neighbor) return false
        return true
    }

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, block: Short, above: Short, meta: Byte = 0, sky: Float = 1f) {
        val layer  = BlockRegistry.getLayerForFace(block, face, above, meta)
        val rotCW  = face > 1
        val packed = face * 4096f + layer.toFloat()
        when (face) {
            0 -> buf.quad(x,y+1,z,  x+1,y+1,z,  x+1,y+1,z+1, x,y+1,z+1, packed, false, sky)
            1 -> buf.quad(x,y,z+1,  x+1,y,z+1,  x+1,y,z,     x,y,z,     packed, false, sky)
            2 -> buf.quad(x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z,   x+1,y,z,   packed, rotCW, sky)
            3 -> buf.quad(x,y,z,    x,y+1,z,     x,y+1,z+1,   x,y,z+1,   packed, rotCW, sky)
            4 -> buf.quad(x,y,z+1,  x,y+1,z+1,   x+1,y+1,z+1, x+1,y,z+1, packed, rotCW, sky)
            5 -> buf.quad(x+1,y,z,  x+1,y+1,z,   x,y+1,z,     x,y,z,     packed, rotCW, sky)
        }
    }

    private fun addCrossSprite(buf: GrowableFloatArray, x: Float, y: Float, z: Float, block: Short, sky: Float = 1f) {
        val layer  = BlockRegistry.getLayerForDecoration(block)
        val margin = BlockRegistry.getSpriteMargin(block)
        val h      = BlockRegistry.getSpriteHeight(block)
        val packed = layer.toFloat()

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

    fun buildWater(chunk: Chunk, world: World): FloatArray {
        val buf = GrowableFloatArray()
        for (lz in 0 until CHUNK_SIZE)
            for (ly in 0 until CHUNK_SIZE)
                for (lx in 0 until CHUNK_SIZE) {
            if (!isWater(chunk.blockAt(lx, ly, lz))) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()
            val wx = chunk.worldX + lx; val wy = chunk.worldY + ly; val wz = chunk.worldZ + lz
            val packed = 0f

            val above = world.neighborBlock(chunk, lx, ly + 1, lz)
            val below = world.neighborBlock(chunk, lx, ly - 1, lz)
            val east  = world.neighborBlock(chunk, lx + 1, ly, lz)
            val west  = world.neighborBlock(chunk, lx - 1, ly, lz)
            val south = world.neighborBlock(chunk, lx, ly, lz + 1)
            val north = world.neighborBlock(chunk, lx, ly, lz - 1)

            // Les coins partagent leurs hauteurs avec les cases voisines : le flux crée ainsi
            // une pente continue au lieu de cubes d'eau empilés.
            val needsSurface = above == AIR || east == AIR || west == AIR || south == AIR || north == AIR
            val hNW: Float
            val hNE: Float
            val hSE: Float
            val hSW: Float
            if (needsSurface) {
                val hC  = waterHeight(world, wx,     wy, wz)
                val hW  = waterHeight(world, wx - 1, wy, wz)
                val hE  = waterHeight(world, wx + 1, wy, wz)
                val hN  = waterHeight(world, wx, wy, wz - 1)
                val hS  = waterHeight(world, wx, wy, wz + 1)
                val hNW0 = waterHeight(world, wx - 1, wy, wz - 1)
                val hNE0 = waterHeight(world, wx + 1, wy, wz - 1)
                val hSE0 = waterHeight(world, wx + 1, wy, wz + 1)
                val hSW0 = waterHeight(world, wx - 1, wy, wz + 1)
                hNW = averageWaterHeights(hC, hW, hN, hNW0)
                hNE = averageWaterHeights(hC, hE, hN, hNE0)
                hSE = averageWaterHeights(hC, hE, hS, hSE0)
                hSW = averageWaterHeights(hC, hW, hS, hSW0)
            } else {
                hNW = 1f; hNE = 1f; hSE = 1f; hSW = 1f
            }

            if (above == AIR)
                buf.quad(x,y+hNW,z, x+1f,y+hNE,z, x+1f,y+hSE,z+1f, x,y+hSW,z+1f, packed, false, skyOf(chunk, world, lx, ly + 1, lz))
            if (below == AIR)
                buf.quad(x,y,z+1f, x+1f,y,z+1f, x+1f,y,z, x,y,z, packed, false, skyOf(chunk, world, lx, ly - 1, lz))
            if (east == AIR)
                buf.quad(x+1f,y,z+1f, x+1f,y+hSE,z+1f, x+1f,y+hNE,z, x+1f,y,z, packed, true, skyOf(chunk, world, lx + 1, ly, lz))
            if (west == AIR)
                buf.quad(x,y,z, x,y+hNW,z, x,y+hSW,z+1f, x,y,z+1f, packed, true, skyOf(chunk, world, lx - 1, ly, lz))
            if (south == AIR)
                buf.quad(x,y,z+1f, x,y+hSW,z+1f, x+1f,y+hSE,z+1f, x+1f,y,z+1f, packed, true, skyOf(chunk, world, lx, ly, lz + 1))
            if (north == AIR)
                buf.quad(x+1f,y,z, x+1f,y+hNE,z, x,y+hNW,z, x,y,z, packed, true, skyOf(chunk, world, lx, ly, lz - 1))
        }
        return buf.toFloatArray()
    }

    private fun waterHeight(world: World, wx: Int, wy: Int, wz: Int): Float {
        val block = world.blockAt(wx, wy, wz)
        if (!isWater(block)) return 0f
        // Une colonne avec de l'eau au-dessus est pleine. Les chutes (niveau 0) le sont aussi.
        if (isWater(world.blockAt(wx, wy + 1, wz))) return 1f
        val level = world.waterFlowLevelKnown(block, wx, wy, wz)
        return if (level == 0) 1f else (9 - level).coerceAtLeast(1) / 9f
    }

    private fun averageWaterHeights(a: Float, b: Float, c: Float, d: Float): Float {
        var total = 0f
        var count = 0
        if (a > 0f) { total += a; count++ }
        if (b > 0f) { total += b; count++ }
        if (c > 0f) { total += c; count++ }
        if (d > 0f) { total += d; count++ }
        return if (count == 0) 0f else total / count
    }

    private fun GrowableFloatArray.add7(x:Float,y:Float,z:Float, u:Float,v:Float,p:Float, sky:Float) {
        add(x); add(y); add(z); add(u); add(v); add(p); add(sky)
    }

    private class GrowableFloatArray(capacity: Int = 16384) {
        private var data = FloatArray(capacity)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toFloatArray(): FloatArray = data.copyOf(size)
    }
}
