package com.Atom2Universe.app.games.roguelike

/** Place solid 2x2 monuments before spawning loot or enemies. Reject any cut-off path. */
internal object CemeteryMonuments {
    const val SIZE = 2
    fun place(level: DungeonLevel): List<Pos> {
        val candidates = mutableListOf<Pos>()
        for (y in 2 until level.h - SIZE - 1) for (x in 2 until level.w - SIZE - 1) {
            if ((y until y + SIZE).any { yy -> (x until x + SIZE).any { xx -> level.themeAt(xx, yy) != DungeonTheme.CEMETERY } }) continue
            if (level.start.x in x - 1..x + SIZE && level.start.y in y - 1..y + SIZE) continue
            if ((y until y + SIZE).any { yy -> (x until x + SIZE).any { xx -> level.tiles[yy][xx] == TileType.STAIRS_DOWN } }) continue
            // A visible facade needs a walkable approach on its south side.
            if ((x until x + SIZE).count { level.walkable(it, y + SIZE) } < SIZE) continue
            candidates += Pos(x, y)
        }
        val selected = mutableListOf<Pos>()
        val ordered = candidates.sortedByDescending { p ->
            (p.y until p.y + SIZE).sumOf { y -> (p.x until p.x + SIZE).count { x -> !level.walkable(x, y) } }
        }
        for (p in ordered) {
            if (selected.any { kotlin.math.abs(it.x - p.x) < SIZE + 3 && kotlin.math.abs(it.y - p.y) < SIZE + 3 }) continue
            val old = Array(SIZE) { y -> Array(SIZE) { x -> level.tiles[p.y + y][p.x + x] } }
            for (y in 0 until SIZE) for (x in 0 until SIZE) level.tiles[p.y + y][p.x + x] = TileType.WALL
            val distance = DungeonGenerator.distances(level.tiles, level.start)
            val connected = (0 until level.h).all { y -> (0 until level.w).all { x ->
                !level.walkable(x, y) || distance[y][x] >= 0
            } }
            if (connected) {
                selected += p
                if (selected.size >= 2) break
            } else for (y in 0 until SIZE) for (x in 0 until SIZE) level.tiles[p.y + y][p.x + x] = old[y][x]
        }
        return selected
    }
}
