package com.Atom2Universe.app.games.caves.node

/** Authored overlapping silhouettes; periodic brush writes at native atlas resolution.
 * No radial domes or image sampling. This proposal is not assigned to a block yet.
 */
internal object MossCushionTexture {
    const val SIZE = 96
    private val colors = intArrayOf(0x3F552F, 0x4E6737, 0x607B40, 0x718A49, 0x829951, 0x94A85E, 0xA8B975)
    private val shapes = arrayOf(
        arrayOf(
            "......##....##....", "...######..###....", "...############...",
            ".###############..", "..###############.", "################..",
            ".#################", "#################.", "..###############.",
            ".################.", "...############...", "..##############..",
            "....###########...", "....###.######....", ".....##..###......"
        ),
        arrayOf(
            "...###....##.....", "..#####..####....", ".#############...",
            "..##############.", "################.", ".################",
            "################.", "..###############", ".###############.",
            "...############..", "..############...", "....###########..",
            "...####..####....", "....##....##....."
        ),
        arrayOf(
            ".......###.......", "...##.#####......", "..###########....",
            "...############..", ".###############.", "################.",
            "..###############", ".################", "################.",
            "..##############.", "...############..", "..######.#####...",
            "....###...####...", ".....##....##...."
        )
    )
    // x, y, width, height: unequal tufts overlap and straddle all four tile edges.
    private val tufts = arrayOf(
        intArrayOf(-8,-7,24,20), intArrayOf(21,-9,25,24), intArrayOf(54,-5,20,18),
        intArrayOf(80,-4,23,22), intArrayOf(7,12,26,22), intArrayOf(37,9,16,15),
        intArrayOf(56,18,26,20), intArrayOf(85,24,20,19), intArrayOf(-5,36,23,19),
        intArrayOf(24,31,24,25), intArrayOf(45,36,17,17), intArrayOf(67,42,27,23),
        intArrayOf(7,54,23,21), intArrayOf(33,61,27,22), intArrayOf(57,64,17,17),
        intArrayOf(79,68,26,24), intArrayOf(-3,80,23,22), intArrayOf(20,84,21,19),
        intArrayOf(49,82,24,23)
    )
    private val tile by lazy { draw() }
    fun pixels(): IntArray = tile.copyOf()
    fun pixel(worldX: Int, worldY: Int): Int =
        tile[Math.floorMod(worldY, SIZE) * SIZE + Math.floorMod(worldX, SIZE)]

    private fun hash(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 xor (y * 668265263) xor (salt * 1274126177)
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7fffffff
    }

    private fun draw(): IntArray {
        val pixels = IntArray(SIZE * SIZE) { i ->
            0xFF000000.toInt() or colors[1 + hash(i % SIZE / 3, i / SIZE / 3, 7) % 2]
        }
        fun put(x: Int, y: Int, shade: Int) {
            pixels[Math.floorMod(y, SIZE) * SIZE + Math.floorMod(x, SIZE)] =
                0xFF000000.toInt() or colors[shade.coerceIn(0, colors.lastIndex)]
        }
        fun stamp(ox: Int, oy: Int, width: Int, height: Int, seed: Int, base: Int) {
            val shape = shapes[seed % shapes.size]
            fun inside(x: Int, y: Int): Boolean {
                if (x !in 0 until width || y !in 0 until height) return false
                val row = shape[y * shape.size / height]
                val sx = x * row.length / width
                return row[if (seed % 2 == 0) sx else row.lastIndex - sx] == '#'
            }
            // Broken lower shadows, never a ring surrounding the tuft.
            for (y in 0 until height) for (x in 0 until width)
                if (inside(x,y) && !inside(x,y+2) && hash(x/2,y,seed)%4 != 0)
                    put(ox+x+1,oy+y+2,1)
            for (y in 0 until height) for (x in 0 until width) {
                if (!inside(x,y)) continue
                val patch = hash(x/3,y/3,seed+59)%11
                var shade = base
                if (y > height*2/3) shade--
                if (patch < 3) shade--
                if (patch > 7) shade++
                put(ox+x,oy+y,shade)
            }
            // Angular, scattered leaf-tip marks rather than a lit dome at the centre.
            for (mark in 0 until width/3) {
                val x=1+hash(mark,3,seed)%(width-3)
                val y=1+hash(mark,13,seed)%(height*2/3)
                val bright=(base+1+if(mark%3==0) 1 else 0).coerceAtMost(6)
                for(dy in 0..2) for(dx in 0..3) {
                    if ((dx==3 && dy!=1) || (dx==0 && dy==2)) continue
                    if (inside(x+dx,y+dy)) put(ox+x+dx,oy+y+dy,bright)
                }
            }
        }
        // Darker undergrowth fills gaps, avoiding a continuous mortar grid.
        for(i in 0 until 35) {
            val x=hash(i,17,73)%SIZE; val y=hash(i,29,131)%SIZE
            stamp(x,y,7+i%6,7+i%5,i+29,2+i%2)
        }
        for((i,t) in tufts.withIndex()) stamp(t[0],t[1],t[2],t[3],i+101,3+i%2)
        return pixels
    }
}
