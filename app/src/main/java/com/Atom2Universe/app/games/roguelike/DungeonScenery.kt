package com.Atom2Universe.app.games.roguelike

internal enum class SceneryKind(val span:Int=1, val solid:Boolean=true) {
    BONFIRE(solid=false), WELL, STALL, CART, CAMPFIRE, RUIN(2), CHAPEL(2), MINECART,
    RAIL(solid=false), MARSH(solid=false), CLIFF,
    CARGO(2), REACTOR(2), BOOKCASES(2), ORE_VEIN(2), OAK(2), HAYSTACK(2),
    BANQUET_TABLE(2), MEMORIAL(2)
}
internal data class MapScenery(val kind:SceneryKind,val column:Int=0,val row:Int=0,val variant:Int=0,val connections:Int=0) {
    val solid get() = kind.solid && !(kind in listOf(SceneryKind.RUIN, SceneryKind.CHAPEL) && column==1 && row==1)
}

/** Decorations are part of collision, never painted over an existing route without validation. */
internal object DungeonScenery {
    private val directions=listOf(Pos(0,-1),Pos(1,0),Pos(0,1),Pos(-1,0))
    fun place(level:DungeonLevel):Map<Pos,MapScenery> {
        val result=linkedMapOf<Pos,MapScenery>()
        val protected=mutableSetOf<Pos>()
        fun protect(p:Pos) { for(y in p.y-1..p.y+1)for(x in p.x-1..p.x+1)protected+=Pos(x,y) }
        protect(level.start)
        for(y in 0 until level.h)for(x in 0 until level.w)if(level.tiles[y][x]==TileType.STAIRS_DOWN)protect(Pos(x,y))
        level.passages.keys.forEach(::protect);level.waterways.keys.forEach(::protect)
        for(m in level.mausoleums)for(y in m.y..m.y+1)for(x in m.x..m.x+1)protect(Pos(x,y))
        val ordered=(2 until level.h-2).flatMap { y -> (2 until level.w-2).map { x -> Pos(x,y) } }
            .sortedBy { (it.x*73856093 xor it.y*19349663 xor level.floor*1640531527) and Int.MAX_VALUE }
        fun connected():Boolean {
            val distance=DungeonGenerator.distances(level.tiles,level.start)
            return (0 until level.h).all { y -> (0 until level.w).all { x -> !level.walkable(x,y)||distance[y][x]>=0 } }
        }
        val anchors=mutableListOf<Pos>()
        for(kind in listOf(SceneryKind.CARGO, SceneryKind.REACTOR, SceneryKind.BOOKCASES,
            SceneryKind.ORE_VEIN, SceneryKind.OAK, SceneryKind.HAYSTACK,
            SceneryKind.BANQUET_TABLE, SceneryKind.MEMORIAL,
            SceneryKind.CHAPEL,SceneryKind.RUIN,SceneryKind.WELL,SceneryKind.STALL,SceneryKind.CART,SceneryKind.CAMPFIRE)) {
            // Trade stalls belong to settled field edges, chapels to the cemetery.
            val candidates=ordered.filter { p ->
                when(kind) {
                    SceneryKind.CARGO -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.PIRATE, DungeonTheme.PORT)
                    SceneryKind.REACTOR -> level.themeAt(p.x,p.y)==DungeonTheme.SPACESHIP
                    SceneryKind.BOOKCASES -> level.themeAt(p.x,p.y)==DungeonTheme.LIBRARY
                    SceneryKind.ORE_VEIN -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.MINE, DungeonTheme.MINE_DEPOT)
                    SceneryKind.OAK -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.FOREST, DungeonTheme.CAMP, DungeonTheme.VILLAGE)
                    SceneryKind.HAYSTACK -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.FIELDS, DungeonTheme.VILLAGE, DungeonTheme.CAMP)
                    SceneryKind.BANQUET_TABLE -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.INN, DungeonTheme.PIRATE_CABIN)
                    SceneryKind.MEMORIAL -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.MONASTERY, DungeonTheme.DUNGEON, DungeonTheme.CRYPT, DungeonTheme.BATTLEFIELD)
                    SceneryKind.CHAPEL -> level.themeAt(p.x,p.y)==DungeonTheme.CEMETERY
                    SceneryKind.STALL -> level.themeAt(p.x,p.y) in listOf(DungeonTheme.VILLAGE,DungeonTheme.FIELDS,DungeonTheme.PORT) ||
                        (level.themeAt(p.x,p.y).outdoor && (-3..3).any { dx -> (-3..3).any { dy -> level.themeAt(p.x+dx,p.y+dy)==DungeonTheme.INN } })
                    else -> level.themeAt(p.x,p.y).outdoor
                }
            }
            for(p in candidates) {
                val span=kind.span
                if(p.x+span>=level.w-1 || p.y+span>=level.h-1 || anchors.any { it.chebyshev(p)<4 })continue
                val cells=(0 until span).flatMap { y -> (0 until span).map { x -> Pos(p.x+x,p.y+y) } }
                if(cells.any { it in protected || it in result || level.themeAt(it.x,it.y)!=level.themeAt(p.x,p.y) })continue
                // Keep the front accessible, and the 2x2 entrance walkable.
                if((0 until span).any { !level.walkable(p.x+it,p.y+span) })continue
                if(kind in listOf(SceneryKind.RUIN, SceneryKind.CHAPEL) && !level.walkable(p.x+1,p.y+1))continue
                val old=cells.map { level.tiles[it.y][it.x] }
                cells.forEach { q -> if(MapScenery(kind,q.x-p.x,q.y-p.y).solid)level.tiles[q.y][q.x]=TileType.WALL }
                if(!connected()) {cells.forEachIndexed { i,q -> level.tiles[q.y][q.x]=old[i] };continue}
                for(q in cells)result[q]=MapScenery(kind,q.x-p.x,q.y-p.y,(p.x+p.y)%3)
                cells.forEach(::protect)
                anchors+=p
                break
            }
        }
        // Isolated cliff faces reuse existing obstacles; shallow pools remain walkable.
        var cliffs=0;var marsh=0
        for(p in ordered) {
            if(p in protected || p in result || !level.themeAt(p.x,p.y).outdoor)continue
            if(!level.walkable(p.x,p.y) && level.walkable(p.x,p.y+1) && cliffs<3) {
                result[p]=MapScenery(SceneryKind.CLIFF,variant=(p.x+p.y)%3);cliffs++
            } else if(level.tiles[p.y][p.x]==TileType.FLOOR && marsh<3 &&
                level.waterways.keys.any { it.chebyshev(p) in 2..3 }) {
                result[p]=MapScenery(SceneryKind.MARSH,variant=(p.x+p.y)%3);marsh++
            }
        }
        // A connected rail route follows actual mine floors, including turns.
        val mine=ordered.filter { level.themeAt(it.x,it.y) in listOf(DungeonTheme.MINE,DungeonTheme.MINE_DEPOT) && level.tiles[it.y][it.x]==TileType.FLOOR && it !in protected }
        var track=emptyList<Pos>()
        for(start in mine.take(32)) {
            val parent=linkedMapOf<Pos,Pos?>();parent[start]=null
            val queue=ArrayDeque<Pos>();queue.add(start)
            var end=start
            while(queue.isNotEmpty()) {
                val p=queue.removeFirst();end=p
                for(d in directions) {
                    val q=Pos(p.x+d.x,p.y+d.y)
                    if(q !in parent && q in mine) {parent[q]=p;queue.add(q)}
                }
            }
            val path=mutableListOf<Pos>();var p:Pos?=end
            while(p!=null) {path+=p;p=parent[p]}
            if(path.size>track.size)track=path.take(14)
        }
        if(track.size>=3) {
            for(p in track) {
                var bits=0
                directions.forEachIndexed { i,d -> if(Pos(p.x+d.x,p.y+d.y) in track)bits=bits or (1 shl i) }
                result[p]=MapScenery(SceneryKind.RAIL,connections=bits)
            }
            // A parked wagon uses a siding beside the route; never closes the rail itself.
            for(p in track) {
                val siding=directions.map { Pos(p.x+it.x,p.y+it.y) }.firstOrNull { q ->
                    q.x in 1 until level.w-1 && q.y in 1 until level.h-1 && q !in protected && q !in result && level.themeAt(q.x,q.y) in listOf(DungeonTheme.MINE,DungeonTheme.MINE_DEPOT)
                } ?: continue
                val old=level.tiles[siding.y][siding.x];level.tiles[siding.y][siding.x]=TileType.WALL
                if(!connected()) {level.tiles[siding.y][siding.x]=old;continue}
                val direction=directions.indexOf(Pos(siding.x-p.x,siding.y-p.y))
                result[p]=result.getValue(p).copy(connections=result.getValue(p).connections or (1 shl direction))
                result[siding]=MapScenery(SceneryKind.MINECART,connections=1 shl ((direction+2)%4));break
            }
        }
        return result
    }
}
