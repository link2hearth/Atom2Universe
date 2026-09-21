package com.Atom2Universe.app.games.roguelike

/** Decorative open thresholds and their solid side barriers, stored separately from collision tiles. */
internal enum class PassageKind { PATH, DOOR, GATE, PORTCULLIS, FENCE }
internal data class MapPassage(val kind: PassageKind, val horizontal: Boolean, val connections: Int = 0)

internal object DungeonPassages {
    private val directions = listOf(Pos(0,-1), Pos(1,0), Pos(0,1), Pos(-1,0))

    /** Run after monuments and before actors/items are placed. Closing shoulders is accepted only
     * when every remaining walkable cell stays connected to the start. Thresholds remain open.
     */
    fun place(level: DungeonLevel): Map<Pos, MapPassage> {
        val result = linkedMapOf<Pos, MapPassage>()
        val outsideEntrances = mutableListOf<Pair<Pos, Pos>>()
        for (y in 1 until level.h-1) for (x in 1 until level.w-1) {
            val p=Pos(x,y)
            if (level.tiles[y][x] != TileType.FLOOR || !level.themeAt(x,y).outdoor) continue
            for (d in directions) {
                val q=Pos(x+d.x,y+d.y)
                if (level.walkable(q.x,q.y) && !level.themeAt(q.x,q.y).outdoor) {
                    outsideEntrances += p to d
                    break
                }
            }
        }
        var tightened=0
        for ((p,d) in outsideEntrances) {
            if (result.keys.any { it.chebyshev(p)<3 } || !level.walkable(p.x,p.y) || p==level.start) continue
            val ahead=Pos(p.x+d.x,p.y+d.y);val behind=Pos(p.x-d.x,p.y-d.y)
            if (!level.walkable(ahead.x,ahead.y) || !level.walkable(behind.x,behind.y)) continue
            val sides=listOf(Pos(p.x+d.y,p.y+d.x), Pos(p.x-d.y,p.y-d.x))
            val candidates=sides.filter { level.walkable(it.x,it.y) }
            if (candidates.isNotEmpty()) {
                if (tightened>=4 || candidates.any { it==level.start || level.tiles[it.y][it.x]!=TileType.FLOOR ||
                    !level.themeAt(it.x,it.y).outdoor || level.mausoleums.any { m -> it.x in m.x until m.x+2 && it.y in m.y until m.y+2 } }) continue
                candidates.forEach { level.tiles[it.y][it.x]=TileType.WALL }
                val distance=DungeonGenerator.distances(level.tiles,level.start)
                val connected=(0 until level.h).all { y -> (0 until level.w).all { x -> !level.walkable(x,y) || distance[y][x]>=0 } }
                if (!connected) {
                    candidates.forEach { level.tiles[it.y][it.x]=TileType.FLOOR }
                    continue
                }
                tightened++
            }
            val horizontal=d.x!=0
            result[p]=MapPassage(if(level.themeAt(p.x,p.y)==DungeonTheme.CEMETERY) PassageKind.GATE else PassageKind.DOOR,horizontal)
            // Closed shoulders read as a continuous fence, not random unrelated scenery.
            for (s in sides) if (!level.mausoleums.any { m -> s.x in m.x until m.x+2 && s.y in m.y until m.y+2 })
                result[s]=MapPassage(PassageKind.FENCE,horizontal)
            // Short approach shares the actual route; it never paints over a blocker or stairs.
            for (i in 1..3) {
                val t=Pos(p.x-d.x*i,p.y-d.y*i)
                if (!level.inBounds(t.x,t.y) || level.tiles[t.y][t.x]!=TileType.FLOOR || !level.themeAt(t.x,t.y).outdoor) break
                if (t !in result) result[t]=MapPassage(PassageKind.PATH,horizontal)
            }
        }
        // Existing one-cell room entrances receive an open door or a raised portcullis.
        for (y in 1 until level.h-1) for (x in 1 until level.w-1) {
            val p=Pos(x,y)
            if (p in result || level.tiles[y][x]!=TileType.FLOOR || level.themeAt(x,y).outdoor) continue
            val horizontal=level.walkable(x-1,y)&&level.walkable(x+1,y)&&!level.walkable(x,y-1)&&!level.walkable(x,y+1)
            val vertical=level.walkable(x,y-1)&&level.walkable(x,y+1)&&!level.walkable(x-1,y)&&!level.walkable(x+1,y)
            if (!horizontal && !vertical) continue
            val dx=if(horizontal)1 else 0;val dy=if(horizontal)0 else 1
            val a=level.themeAt(x-dx,y-dy);val b=level.themeAt(x+dx,y+dy)
            if (a==b || a.outdoor || b.outdoor || a==DungeonTheme.SPACESHIP || b==DungeonTheme.SPACESHIP) continue
            if (result.any { (pos, passage) -> passage.kind!=PassageKind.PATH && pos.chebyshev(p)<2 }) continue
            result[p]=MapPassage(if(a==DungeonTheme.MONASTERY || b==DungeonTheme.MONASTERY) PassageKind.PORTCULLIS else PassageKind.DOOR,horizontal)
        }
        // Paths join each other and the threshold in all four directions.
        for ((p,passage) in result.toMap()) if (passage.kind==PassageKind.PATH) {
            var bits=0
            directions.forEachIndexed { i,d ->
                val q=Pos(p.x+d.x,p.y+d.y)
                if (result[q]?.kind in listOf(PassageKind.PATH,PassageKind.DOOR,PassageKind.GATE,PassageKind.PORTCULLIS)) bits=bits or (1 shl i)
            }
            result[p]=passage.copy(connections=bits)
        }
        return result
    }
}
