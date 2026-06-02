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
                addCrossSprite(buf, x, y, z, block)
                continue
            }

            val meta  = chunk.metaAt(lx, ly, lz)
            val above = world.neighborBlock(chunk, lx, ly + 1, lz)
            if (shouldRenderFace(block, above))                                        addFace(buf, x, y, z, 0, block, above, meta)
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly - 1, lz)))  addFace(buf, x, y, z, 1, block, AIR,  meta)
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx + 1, ly, lz)))  addFace(buf, x, y, z, 2, block, above, meta)
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx - 1, ly, lz)))  addFace(buf, x, y, z, 3, block, above, meta)
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz + 1)))  addFace(buf, x, y, z, 4, block, above, meta)
            if (shouldRenderFace(block, world.neighborBlock(chunk, lx, ly, lz - 1)))  addFace(buf, x, y, z, 5, block, above, meta)
        }
        return buf.toFloatArray()
    }

    private fun isVisible(block: Short) =
        block == AIR || isDecoration(block) || isTransparent(block) || isWater(block)

    private fun shouldRenderFace(block: Short, neighbor: Short): Boolean {
        if (!isVisible(neighbor)) return false
        if (isTransparent(block) && block == neighbor) return false
        return true
    }

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, block: Short, above: Short, meta: Byte = 0) {
        val layer  = BlockRegistry.getLayerForFace(block, face, above, meta)
        val rotCW  = face > 1
        val packed = face * 4096f + layer.toFloat()
        when (face) {
            0 -> buf.quad(x,y+1,z,  x+1,y+1,z,  x+1,y+1,z+1, x,y+1,z+1, packed, false)
            1 -> buf.quad(x,y,z+1,  x+1,y,z+1,  x+1,y,z,     x,y,z,     packed, false)
            2 -> buf.quad(x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z,   x+1,y,z,   packed, rotCW)
            3 -> buf.quad(x,y,z,    x,y+1,z,     x,y+1,z+1,   x,y,z+1,   packed, rotCW)
            4 -> buf.quad(x,y,z+1,  x,y+1,z+1,   x+1,y+1,z+1, x+1,y,z+1, packed, rotCW)
            5 -> buf.quad(x+1,y,z,  x+1,y+1,z,   x,y+1,z,     x,y,z,     packed, rotCW)
        }
    }

    private fun addCrossSprite(buf: GrowableFloatArray, x: Float, y: Float, z: Float, block: Short) {
        val layer  = BlockRegistry.getLayerForDecoration(block)
        val margin = BlockRegistry.getSpriteMargin(block)
        val h      = BlockRegistry.getSpriteHeight(block)
        val packed = layer.toFloat()

        buf.add6(x+margin,   y,   z+0.5f, 0f, 1f, packed)
        buf.add6(x+1f-margin,y,   z+0.5f, 1f, 1f, packed)
        buf.add6(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed)
        buf.add6(x+margin,   y,   z+0.5f, 0f, 1f, packed)
        buf.add6(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed)
        buf.add6(x+margin,   y+h, z+0.5f, 0f, 0f, packed)

        buf.add6(x+0.5f, y,   z+margin,    0f, 1f, packed)
        buf.add6(x+0.5f, y,   z+1f-margin, 1f, 1f, packed)
        buf.add6(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed)
        buf.add6(x+0.5f, y,   z+margin,    0f, 1f, packed)
        buf.add6(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed)
        buf.add6(x+0.5f, y+h, z+margin,    0f, 0f, packed)
    }

    private fun GrowableFloatArray.quad(
        x0:Float,y0:Float,z0:Float,
        x1:Float,y1:Float,z1:Float,
        x2:Float,y2:Float,z2:Float,
        x3:Float,y3:Float,z3:Float,
        packed:Float, rotCW:Boolean
    ) {
        if (rotCW) {
            add6(x0,y0,z0, 0f,1f, packed); add6(x1,y1,z1, 0f,0f, packed); add6(x2,y2,z2, 1f,0f, packed)
            add6(x0,y0,z0, 0f,1f, packed); add6(x2,y2,z2, 1f,0f, packed); add6(x3,y3,z3, 1f,1f, packed)
        } else {
            add6(x0,y0,z0, 0f,0f, packed); add6(x1,y1,z1, 1f,0f, packed); add6(x2,y2,z2, 1f,1f, packed)
            add6(x0,y0,z0, 0f,0f, packed); add6(x2,y2,z2, 1f,1f, packed); add6(x3,y3,z3, 0f,1f, packed)
        }
    }

    fun buildWater(chunk: Chunk, world: World): FloatArray {
        val buf = GrowableFloatArray()
        for (lz in 0 until CHUNK_SIZE)
        for (ly in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            if (!isWater(chunk.blockAt(lx, ly, lz))) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()
            val packed = 0f

            if (world.neighborBlock(chunk, lx, ly + 1, lz) == AIR)
                buf.quad(x,y+1f,z, x+1f,y+1f,z, x+1f,y+1f,z+1f, x,y+1f,z+1f, packed, false)
            if (world.neighborBlock(chunk, lx, ly - 1, lz) == AIR)
                buf.quad(x,y,z+1f, x+1f,y,z+1f, x+1f,y,z, x,y,z, packed, false)
            if (world.neighborBlock(chunk, lx + 1, ly, lz) == AIR)
                buf.quad(x+1f,y,z+1f, x+1f,y+1f,z+1f, x+1f,y+1f,z, x+1f,y,z, packed, true)
            if (world.neighborBlock(chunk, lx - 1, ly, lz) == AIR)
                buf.quad(x,y,z, x,y+1f,z, x,y+1f,z+1f, x,y,z+1f, packed, true)
            if (world.neighborBlock(chunk, lx, ly, lz + 1) == AIR)
                buf.quad(x,y,z+1f, x,y+1f,z+1f, x+1f,y+1f,z+1f, x+1f,y,z+1f, packed, true)
            if (world.neighborBlock(chunk, lx, ly, lz - 1) == AIR)
                buf.quad(x+1f,y,z, x+1f,y+1f,z, x,y+1f,z, x,y,z, packed, true)
        }
        return buf.toFloatArray()
    }

    private fun GrowableFloatArray.add6(x:Float,y:Float,z:Float, u:Float,v:Float,p:Float) {
        add(x); add(y); add(z); add(u); add(v); add(p)
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
