package com.Atom2Universe.app.games.caves.world

internal object MeshBuilder {

    // packed = faceDir * 32 + texLayer  (faceDir 0-5, texLayer 0-19)
    // texLayer = blockType - 1 pour les blocs simples ; GRASS/WOOD ont des layers différents par face.
    // Décodé dans le fragment shader pour appliquer lumière de face + sample texture array.
    //   0:stone 1:greystone 2:greysand(quartz) 3:coal 4:gold 5:crystal 6:dirt 7:gravel
    //   8:iron 9:silver 10:ruby 11:lava 12:oven 13:emerald 14:copper
    //   15:dirt_grass(côtés GRASS) 16:grass_top(dessus GRASS)
    //   17:trunk_side(côtés WOOD) 18:trunk_top(haut/bas WOOD) 19:leaves
    //   20:sand 21:stone_grass(côtés STONE exposé à l'air, adjacent à un sol herbeux) 22:redsand

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
            val x = ox + lx; val y = oy + ly; val z = oz + lz
            val above = world.neighborBlock(chunk, lx, ly + 1, lz)

            if (above == AIR)                                        addFace(buf, x, y, z, 0, block, AIR)
            if (world.neighborBlock(chunk, lx, ly - 1, lz) == AIR)  addFace(buf, x, y, z, 1, block, AIR)
            if (world.neighborBlock(chunk, lx + 1, ly, lz) == AIR)  addFace(buf, x, y, z, 2, block, above)
            if (world.neighborBlock(chunk, lx - 1, ly, lz) == AIR)  addFace(buf, x, y, z, 3, block, above)
            if (world.neighborBlock(chunk, lx, ly, lz + 1) == AIR)  addFace(buf, x, y, z, 4, block, above)
            if (world.neighborBlock(chunk, lx, ly, lz - 1) == AIR)  addFace(buf, x, y, z, 5, block, above)
        }
        return buf.toFloatArray()
    }

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, block: Byte, above: Byte) {
        val isSide = face > 1
        val layer = when (block) {
            GRASS   -> when (face) { 0 -> 16; 1 -> 6; else -> 15 }  // dessus=grass_top, dessous=dirt, côtés=dirt_grass
            STONE   -> when {
                face == 0             -> 16  // dessus exposé à l'air : grass_top
                isSide && above == AIR -> 21 // côtés exposés à l'air : stone_grass
                else                  -> 0   // enterré : stone
            }
            WOOD    -> if (face <= 1) 18 else 17
            LEAVES  -> 19
            SAND    -> 20
            REDSAND -> 22
            else    -> block.toInt() - 1
        }
        // Les sprites grass-side (dirt_grass=15, stone_grass=21) sont pivotés 90° CW
        val rotCW = isSide && (layer == 15 || layer == 21)
        val packed = face * 32f + layer.toFloat()
        when (face) {
            0 -> buf.quad(x,y+1,z,  x+1,y+1,z,  x+1,y+1,z+1, x,y+1,z+1, packed, false)
            1 -> buf.quad(x,y,z+1,  x+1,y,z+1,  x+1,y,z,     x,y,z,     packed, false)
            2 -> buf.quad(x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z,   x+1,y,z,   packed, rotCW)
            3 -> buf.quad(x,y,z,    x,y+1,z,     x,y+1,z+1,   x,y,z+1,   packed, rotCW)
            4 -> buf.quad(x,y,z+1,  x,y+1,z+1,   x+1,y+1,z+1, x+1,y,z+1, packed, rotCW)
            5 -> buf.quad(x+1,y,z,  x+1,y+1,z,   x,y+1,z,     x,y,z,     packed, rotCW)
        }
    }

    // UV normaux (0,0)→(1,0)→(1,1)→(0,1) ou pivotés 90° CW (0,1)→(0,0)→(1,0)→(1,1)
    private fun GrowableFloatArray.quad(
        x0:Float,y0:Float,z0:Float,
        x1:Float,y1:Float,z1:Float,
        x2:Float,y2:Float,z2:Float,
        x3:Float,y3:Float,z3:Float,
        packed:Float,
        rotCW:Boolean
    ) {
        if (rotCW) {
            add6(x0,y0,z0, 0f,1f, packed)
            add6(x1,y1,z1, 0f,0f, packed)
            add6(x2,y2,z2, 1f,0f, packed)
            add6(x0,y0,z0, 0f,1f, packed)
            add6(x2,y2,z2, 1f,0f, packed)
            add6(x3,y3,z3, 1f,1f, packed)
        } else {
            add6(x0,y0,z0, 0f,0f, packed)
            add6(x1,y1,z1, 1f,0f, packed)
            add6(x2,y2,z2, 1f,1f, packed)
            add6(x0,y0,z0, 0f,0f, packed)
            add6(x2,y2,z2, 1f,1f, packed)
            add6(x3,y3,z3, 0f,1f, packed)
        }
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
