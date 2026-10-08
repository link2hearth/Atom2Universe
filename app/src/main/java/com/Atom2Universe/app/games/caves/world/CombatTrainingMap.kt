package com.Atom2Universe.app.games.caves.world

/** Real underground blueprints, all on the first equipment layer, in connected test bays. */
internal object CombatTrainingMap {
    const val FLOOR = 16
    const val WIDTH = 192
    const val DEPTH = 256
    const val HEIGHT = 32
    data class Bay(val kind: UndergroundSites.Kind, val x: Int, val z: Int, val plan: UndergroundSites.Plan) {
        val entrance get() = MapPoint(x, FLOOR + 1, z - 6)
    }
    val bays by lazy {
        UndergroundSites.Kind.entries.mapIndexed { i, kind ->
            Bay(kind, 32 + i % 3 * 64, 12 + i / 3 * 64,
                UndergroundSites.blueprint(kind, 7219L + i, stage = 0))
        }
    }

    fun create(): A2Map {
        val blocks = ShortArray(WIDTH * HEIGHT * DEPTH) { STONE }
        val meta = ByteArray(blocks.size)
        fun put(x: Int, y: Int, z: Int, id: Short, orientation: Byte = 0) {
            require(x in 0 until WIDTH && y in 0 until HEIGHT && z in 0 until DEPTH)
            val i = x + WIDTH * (z + DEPTH * y)
            blocks[i] = id; meta[i] = orientation
        }
        // Galleries pass in front of the portals; a side passage links all four rows.
        for (row in 0..3) for (x in 3..166) for (z in 4 + row * 64..8 + row * 64)
            for (y in FLOOR + 1..FLOOR + 4) put(x, y, z, AIR)
        for (x in 3..7) for (z in 4..200) for (y in FLOOR + 1..FLOOR + 4) put(x,y,z,AIR)
        for (bay in bays) {
            for ((p, cell) in bay.plan.blocks) put(bay.x+p.x, FLOOR+p.y, bay.z+p.z, cell.id, cell.meta)
            // Light the training route without moving the authored furnishings or spawn pads.
            for (z in listOf(-6, 0, 10, 20, 30, 39)) {
                val x = bay.x + if (z == 39) 5 else 1
                val wz = bay.z + z
                val i = x + WIDTH * (wz + DEPTH * (FLOOR + 1))
                if (blocks[i] == AIR && blocks[i-WIDTH*DEPTH] != AIR) put(x,FLOOR+1,wz,TORCH)
            }
        }
        for (row in 0..3) for (x in 12..164 step 12) put(x,FLOOR+1,4+row*64,TORCH)
        for (z in 12..196 step 12) put(3,FLOOR+1,z,TORCH)
        return A2Map("combat_training",WIDTH,HEIGHT,DEPTH,blocks,meta,bays.map { it.entrance },emptyList())
    }
}
