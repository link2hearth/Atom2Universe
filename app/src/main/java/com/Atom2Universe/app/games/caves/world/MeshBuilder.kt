package com.Atom2Universe.app.games.caves.world

internal object MeshBuilder {

    // Vertices en coordonnées locales au chunk (0..CHUNK_SIZE).
    // La translation vers l'espace caméra-relatif est appliquée via u_chunk_offset dans le vertex shader.
    //
    // Couches de texture (GL_TEXTURE_2D_ARRAY) :
    //  Solides :
    //   0:stone  1:granite  2:quartz  3:coal  4:gold  5:crystal  6:dirt  7:gravel
    //   8:iron  9:silver  10:ruby  11:lava  12:furnace  13:emerald  14:copper
    //   15:dirt_grass(côtés GRASS)  16:grass_top  17:trunk_side  18:trunk_top  19:leaves
    //   20:sand  21:stone_grass  22:redsand
    //   23:ice  24:snow(dessus)  25:dirt_snow(côtés SNOW)  26:brick_red
    //  Décors cross-sprite :
    //   27:rock  28:rock_moss  29:mushroom_red  30:mushroom_brown  31:mushroom_tan
    //  Transitions biome pour faces STONE latérales (rotCW requis) :
    //   32:stone_sand_side  33:stone_snow_side

    fun build(chunk: Chunk, world: World): FloatArray {
        val buf = GrowableFloatArray()

        for (lz in 0 until CHUNK_SIZE)
        for (ly in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            val block = chunk.blockAt(lx, ly, lz)
            if (block == AIR) continue
            val x = lx.toFloat(); val y = ly.toFloat(); val z = lz.toFloat()

            if (isDecoration(block)) {
                addCrossSprite(buf, x, y, z, block)
                continue
            }

            val above = world.neighborBlock(chunk, lx, ly + 1, lz)
            if (isVisible(above))                                        addFace(buf, x, y, z, 0, block, above)
            if (isVisible(world.neighborBlock(chunk, lx, ly - 1, lz)))  addFace(buf, x, y, z, 1, block, AIR)
            if (isVisible(world.neighborBlock(chunk, lx + 1, ly, lz)))  addFace(buf, x, y, z, 2, block, above)
            if (isVisible(world.neighborBlock(chunk, lx - 1, ly, lz)))  addFace(buf, x, y, z, 3, block, above)
            if (isVisible(world.neighborBlock(chunk, lx, ly, lz + 1)))  addFace(buf, x, y, z, 4, block, above)
            if (isVisible(world.neighborBlock(chunk, lx, ly, lz - 1)))  addFace(buf, x, y, z, 5, block, above)
        }
        return buf.toFloatArray()
    }

    // Un voisin est "visible" (laisse passer la lumière) si c'est de l'air ou un décor.
    private fun isVisible(block: Byte) = block == AIR || isDecoration(block)

    private fun addFace(buf: GrowableFloatArray, x: Float, y: Float, z: Float, face: Int, block: Byte, above: Byte) {
        val isSide = face > 1
        val layer = when (block) {
            GRASS     -> when (face) { 0 -> 16; 1 -> 6; else -> 15 }
            STONE     -> when {
                face == 0                                         -> 16  // dessus : grass_top
                isSide && (above == SAND || above == REDSAND)    -> 32  // stone_sand_side
                isSide && (above == SNOW || above == ICE)        -> 33  // stone_snow_side
                isSide && above == AIR                           -> 21  // stone_grass
                else                                              -> 0
            }
            WOOD      -> if (face <= 1) 18 else 17
            LEAVES    -> 19
            SAND      -> 20
            REDSAND   -> 22
            ICE       -> 23
            SNOW      -> when (face) { 0 -> 24; 1 -> 6; else -> 25 }
            BRICK_RED -> 26
            else      -> block.toInt() - 1
        }
        // Toutes les faces latérales appliquent rotCW pour que U=horizontal et V=vertical.
        // Sans ça, le mapping v0→v1 suit l'axe Y (vertical) et toutes les textures apparaissent
        // tournées 90°. Les textures de transition (dirt_grass, stone_grass, etc.) bénéficient
        // automatiquement du même traitement.
        val rotCW = isSide
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

    // Sprite en croix : deux quads perpendiculaires, UV avec v=1 en bas → image droit sur l'écran.
    // Android bitmap row-0=haut → GL UV(v=0)=bas : on inverse v pour afficher l'image à l'endroit.
    private fun addCrossSprite(buf: GrowableFloatArray, x: Float, y: Float, z: Float, block: Byte) {
        val layer = when (block) {
            ROCK           -> 27
            ROCK_MOSS      -> 28
            MUSHROOM_RED   -> 29
            MUSHROOM_BROWN -> 30
            MUSHROOM_TAN   -> 31
            else -> return
        }
        val (margin, h) = when (block) {
            ROCK, ROCK_MOSS -> Pair(0.20f, 0.45f)   // petits cailloux
            else             -> Pair(0.10f, 0.90f)   // champignons hauts
        }
        val packed = layer.toFloat()  // faceDir=0 → lumière max (top face)

        // Quad A : aligné Z (traverse en X)
        buf.add6(x+margin,   y,   z+0.5f, 0f, 1f, packed)
        buf.add6(x+1f-margin,y,   z+0.5f, 1f, 1f, packed)
        buf.add6(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed)
        buf.add6(x+margin,   y,   z+0.5f, 0f, 1f, packed)
        buf.add6(x+1f-margin,y+h, z+0.5f, 1f, 0f, packed)
        buf.add6(x+margin,   y+h, z+0.5f, 0f, 0f, packed)

        // Quad B : aligné X (traverse en Z)
        buf.add6(x+0.5f, y,   z+margin,    0f, 1f, packed)
        buf.add6(x+0.5f, y,   z+1f-margin, 1f, 1f, packed)
        buf.add6(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed)
        buf.add6(x+0.5f, y,   z+margin,    0f, 1f, packed)
        buf.add6(x+0.5f, y+h, z+1f-margin, 1f, 0f, packed)
        buf.add6(x+0.5f, y+h, z+margin,    0f, 0f, packed)
    }

    // UV normaux (0,0)→(1,0)→(1,1)→(0,1) ou pivotés 90° CW (0,1)→(0,0)→(1,0)→(1,1)
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
