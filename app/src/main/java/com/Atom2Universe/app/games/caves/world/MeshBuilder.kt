package com.Atom2Universe.app.games.caves.world

internal object MeshBuilder {

    // packed = faceDir * 16 + texLayer  (faceDir 0-5, texLayer 0-12)
    // texLayer = blockType - 1  (STONE=1→0, GRANITE=2→1, … FURNACE=13→12)
    // Décodé dans le fragment shader pour appliquer lumière de face + sample texture array.

    fun build(chunk: Chunk, world: World): FloatArray {
        val buf = GrowableFloatArray()
        val ox = chunk.worldX.toFloat()
        val oy = chunk.worldY.toFloat()
        val oz = chunk.worldZ.toFloat()

        for (lz in 0 until CHUNK_SIZE)
        for (ly in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            val block = chunk.blockAt(lx, ly, lz)
            if (block == AIR) continue
            val layer = block.toInt() - 1
            val x = ox + lx; val y = oy + ly; val z = oz + lz

            if (world.neighborBlock(chunk, lx, ly + 1, lz) == AIR) addFace(buf, x, y, z, 0, layer)
            if (world.neighborBlock(chunk, lx, ly - 1, lz) == AIR) addFace(buf, x, y, z, 1, layer)
            if (world.neighborBlock(chunk, lx + 1, ly, lz) == AIR) addFace(buf, x, y, z, 2, layer)
            if (world.neighborBlock(chunk, lx - 1, ly, lz) == AIR) addFace(buf, x, y, z, 3, layer)
            if (world.neighborBlock(chunk, lx, ly, lz + 1) == AIR) addFace(buf, x, y, z, 4, layer)
            if (world.neighborBlock(chunk, lx, ly, lz - 1) == AIR) addFace(buf, x, y, z, 5, layer)
        }
        return buf.toFloatArray()
    }

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, layer: Int) {
        val packed = face * 16f + layer.toFloat()
        when (face) {
            0 -> buf.quad(x,y+1,z,  x+1,y+1,z,  x+1,y+1,z+1, x,y+1,z+1, packed)
            1 -> buf.quad(x,y,z+1,  x+1,y,z+1,  x+1,y,z,     x,y,z,     packed)
            2 -> buf.quad(x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z,   x+1,y,z,   packed)
            3 -> buf.quad(x,y,z,    x,y+1,z,     x,y+1,z+1,   x,y,z+1,   packed)
            4 -> buf.quad(x,y,z+1,  x,y+1,z+1,   x+1,y+1,z+1, x+1,y,z+1, packed)
            5 -> buf.quad(x+1,y,z,  x+1,y+1,z,   x,y+1,z,     x,y,z,     packed)
        }
    }

    // UV corners : (0,0) (1,0) (1,1) (0,1) — triangle 0-1-2 + 0-2-3
    private fun GrowableFloatArray.quad(
        x0:Float,y0:Float,z0:Float,
        x1:Float,y1:Float,z1:Float,
        x2:Float,y2:Float,z2:Float,
        x3:Float,y3:Float,z3:Float,
        packed:Float
    ) {
        add6(x0,y0,z0, 0f,0f, packed)
        add6(x1,y1,z1, 1f,0f, packed)
        add6(x2,y2,z2, 1f,1f, packed)
        add6(x0,y0,z0, 0f,0f, packed)
        add6(x2,y2,z2, 1f,1f, packed)
        add6(x3,y3,z3, 0f,1f, packed)
    }

    private fun GrowableFloatArray.add6(x:Float,y:Float,z:Float, u:Float,v:Float,p:Float) {
        add(x); add(y); add(z); add(u); add(v); add(p)
    }

    private class GrowableFloatArray(capacity: Int = 32768) {
        private var data = FloatArray(capacity)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toFloatArray(): FloatArray = data.copyOf(size)
    }
}
