package com.Atom2Universe.app.games.roguelike

internal data class MapWaterway(val bridge: Boolean, val horizontalFlow: Boolean, val connections: Int, val variant: Int)

/** Short streams are committed only if all surviving floor cells remain reachable. */
internal object DungeonWaterways {
    fun place(level: DungeonLevel): Map<Pos, MapWaterway> {
        val protected = level.passages.keys.flatMap { p -> (-1..1).flatMap { dy -> (-1..1).map { dx -> Pos(p.x+dx,p.y+dy) } } }.toMutableSet()
        for (m in level.mausoleums) for (y in m.y-1..m.y+2) for (x in m.x-1..m.x+2) protected += Pos(x,y)
        val candidates = (2 until level.h-2).flatMap { y -> (2 until level.w-2).map { x -> Pos(x,y) } }
            .sortedBy { (it.x * 73856093 xor it.y * 19349663 xor level.floor * 83492791) and Int.MAX_VALUE }
        for (length in listOf(7,5,3)) for (center in candidates) for (horizontal in listOf(false,true)) {
            val dx=if(horizontal)1 else 0; val dy=1-dx
            val cells=(-length/2..length/2).map { Pos(center.x+it*dx,center.y+it*dy) }
            val theme=level.themeAt(center.x,center.y)
            if (!theme.outdoor || theme == DungeonTheme.PORT || cells.any { p -> p.x !in 2 until level.w-2 || p.y !in 2 until level.h-2 ||
                level.themeAt(p.x,p.y)!=theme || p==level.start || p in protected || level.tiles[p.y][p.x]==TileType.STAIRS_DOWN }) continue
            // Keep both bridge approaches and their existing decorations clear.
            if (listOf(-1,1).any { s -> !level.walkable(center.x+s*dy,center.y+s*dx) }) continue
            if (level.tiles[center.y][center.x]!=TileType.FLOOR) continue
            // A stream needs open banks, not a channel carved through a solid forest wall.
            if (cells.count { level.walkable(it.x,it.y) } < length-1) continue
            val old=cells.map { level.tiles[it.y][it.x] }
            for (p in cells) level.tiles[p.y][p.x]=if(p==center) TileType.FLOOR else TileType.WALL
            val distance=DungeonGenerator.distances(level.tiles,level.start)
            if ((0 until level.h).any { y -> (0 until level.w).any { x -> level.walkable(x,y) && distance[y][x]<0 } }) {
                cells.forEachIndexed { i,p -> level.tiles[p.y][p.x]=old[i] }
                continue
            }
            val directions=listOf(Pos(0,-1),Pos(1,0),Pos(0,1),Pos(-1,0))
            return cells.associateWith { p ->
                var bits=0
                directions.forEachIndexed { i,d -> if(Pos(p.x+d.x,p.y+d.y) in cells) bits=bits or (1 shl i) }
                MapWaterway(p==center,horizontal,bits,Math.floorMod(p.x*7+p.y*11,4))
            }
        }
        return emptyMap()
    }
}
