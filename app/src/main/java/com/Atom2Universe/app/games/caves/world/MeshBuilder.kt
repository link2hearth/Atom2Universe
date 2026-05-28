package com.Atom2Universe.app.games.caves.world

internal object MeshBuilder {

    private val COLORS = mapOf(
        STONE   to floatArrayOf(0.42f, 0.42f, 0.46f),
        GRANITE to floatArrayOf(0.52f, 0.36f, 0.33f),
        QUARTZ  to floatArrayOf(0.82f, 0.80f, 0.78f),
        COAL    to floatArrayOf(0.16f, 0.16f, 0.18f),
        GOLD    to floatArrayOf(0.70f, 0.58f, 0.14f),
        CRYSTAL to floatArrayOf(0.30f, 0.65f, 0.85f),
    )

    // Ambient occlusion multiplier per face direction: +Y, -Y, +X, -X, +Z, -Z
    private val FACE_LIGHT = floatArrayOf(1.00f, 0.45f, 0.72f, 0.72f, 0.62f, 0.62f)

    // 6 faces × (4 verts × 3 pos + 2 tri indices), stored as tri verts for simplicity.
    // Each face: 2 triangles = 6 vertices. Vertex = x,y,z,r,g,b → 6 floats.
    private const val FLOATS_PER_VERTEX = 6
    private const val VERTS_PER_FACE = 6

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
            val c = COLORS[block] ?: COLORS[STONE]!!
            val x = ox + lx; val y = oy + ly; val z = oz + lz

            // +Y
            if (world.neighborBlock(chunk, lx, ly + 1, lz) == AIR)
                addFace(buf, x, y, z, 0, c[0], c[1], c[2])
            // -Y
            if (world.neighborBlock(chunk, lx, ly - 1, lz) == AIR)
                addFace(buf, x, y, z, 1, c[0], c[1], c[2])
            // +X
            if (world.neighborBlock(chunk, lx + 1, ly, lz) == AIR)
                addFace(buf, x, y, z, 2, c[0], c[1], c[2])
            // -X
            if (world.neighborBlock(chunk, lx - 1, ly, lz) == AIR)
                addFace(buf, x, y, z, 3, c[0], c[1], c[2])
            // +Z
            if (world.neighborBlock(chunk, lx, ly, lz + 1) == AIR)
                addFace(buf, x, y, z, 4, c[0], c[1], c[2])
            // -Z
            if (world.neighborBlock(chunk, lx, ly, lz - 1) == AIR)
                addFace(buf, x, y, z, 5, c[0], c[1], c[2])
        }
        return buf.toFloatArray()
    }

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float,
                        face: Int, r: Float, g: Float, b: Float) {
        val l = FACE_LIGHT[face]
        val lr = r * l; val lg = g * l; val lb = b * l
        // Quad corners for each face, wound counter-clockwise (front-face = outward)
        when (face) {
            0 -> { // +Y top
                buf.quad(x,y+1,z, x+1,y+1,z, x+1,y+1,z+1, x,y+1,z+1, lr,lg,lb)
            }
            1 -> { // -Y bottom
                buf.quad(x,y,z+1, x+1,y,z+1, x+1,y,z, x,y,z, lr,lg,lb)
            }
            2 -> { // +X
                buf.quad(x+1,y,z+1, x+1,y+1,z+1, x+1,y+1,z, x+1,y,z, lr,lg,lb)
            }
            3 -> { // -X
                buf.quad(x,y,z, x,y+1,z, x,y+1,z+1, x,y,z+1, lr,lg,lb)
            }
            4 -> { // +Z
                buf.quad(x,y,z+1, x,y+1,z+1, x+1,y+1,z+1, x+1,y,z+1, lr,lg,lb)
            }
            5 -> { // -Z
                buf.quad(x+1,y,z, x+1,y+1,z, x,y+1,z, x,y,z, lr,lg,lb)
            }
        }
    }

    // Emits 2 triangles (6 vertices) for a quad defined by 4 corners (CCW order).
    private fun GrowableFloatArray.quad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float
    ) {
        // Triangle 1: 0,1,2
        add6(x0,y0,z0,r,g,b); add6(x1,y1,z1,r,g,b); add6(x2,y2,z2,r,g,b)
        // Triangle 2: 0,2,3
        add6(x0,y0,z0,r,g,b); add6(x2,y2,z2,r,g,b); add6(x3,y3,z3,r,g,b)
    }

    private fun GrowableFloatArray.add6(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
        add(x); add(y); add(z); add(r); add(g); add(b)
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
