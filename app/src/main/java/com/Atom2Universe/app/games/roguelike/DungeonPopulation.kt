package com.Atom2Universe.app.games.roguelike

import kotlin.random.Random
import kotlin.math.sqrt

/** Distances use exactly the eight-direction movement and corner rules used by the hero. */
internal object DungeonPaths {
    fun distances(level:DungeonLevel,from:Pos,limit:Int=Int.MAX_VALUE)=distances(level,listOf(from),limit)
    fun distances(level:DungeonLevel,sources:List<Pos>,limit:Int=Int.MAX_VALUE):Array<IntArray> {
        val result=Array(level.h){IntArray(level.w){-1}}
        val queue=IntArray(level.w*level.h);var head=0;var tail=0
        for(p in sources)if(level.walkable(p.x,p.y)&&result[p.y][p.x]<0){result[p.y][p.x]=0;queue[tail++]=p.y*level.w+p.x}
        while(head<tail) {
            val index=queue[head++];val p=Pos(index%level.w,index/level.w);val distance=result[p.y][p.x]
            if(distance>=limit)continue
            for(dy in -1..1)for(dx in -1..1) {
                if(dx==0&&dy==0 || !level.canStep(p,dx,dy))continue
                val x=p.x+dx;val y=p.y+dy
                if(result[y][x]>=0)continue
                result[y][x]=distance+1;queue[tail++]=y*level.w+x
            }
        }
        return result
    }
}
internal enum class MapSiteKind { CAMP, EXIT, HOTSPOT, QUIET, TRANSITION }
internal data class MapSite(val pos:Pos,val kind:MapSiteKind)
internal enum class PackRole { GUARD, CLUSTER, PATROL, LOCAL }
internal data class PackSpawn(val pos:Pos,val role:PackRole,val patrol:List<Pos> = emptyList())
internal data class PopulationPlan(val spawns:List<PackSpawn>,val sites:List<MapSite>,val quietCells:Set<Pos>,val quotas:Map<Int,Int>)

internal object DungeonPopulation {
    fun plan(level:DungeonLevel,rooms:List<Room>,target:Int,rng:Random):PopulationPlan {
        val camp=DungeonPaths.distances(level,level.start)
        val stairs=(0 until level.h).firstNotNullOf { y -> (0 until level.w).firstOrNull { x -> level.tiles[y][x]==TileType.STAIRS_DOWN }?.let { Pos(it,y) } }
        val exit=DungeonPaths.distances(level,stairs)
        val all=(1 until level.h-1).flatMap { y -> (1 until level.w-1).map { x -> Pos(x,y) } }.filter { level.tiles[it.y][it.x]==TileType.FLOOR }
        val safeDistance=if(level.format==DungeonFormat.MICRO)8 else 12
        val candidates=all.filter { camp[it.y][it.x]>=safeDistance && exit[it.y][it.x]>=3 }.shuffled(rng)
        val sites=mutableListOf(MapSite(level.start,MapSiteKind.CAMP),MapSite(stairs,MapSiteKind.EXIT))
        val decorations=level.scenery.filterValues { it.solid }.keys.toList()+level.mausoleums
        val approach=decorations.flatMap { p -> listOf(Pos(p.x,p.y+1),Pos(p.x+1,p.y),Pos(p.x-1,p.y),Pos(p.x,p.y-1)) }.filter { level.walkable(it.x,it.y) }
        val poiDistance=DungeonPaths.distances(level,approach)
        val centers=(rooms.map { it.center() }+approach).distinct().filter { it in candidates }
        val hotspotCount=if(target>=10)2 else 1
        val hotspotFields=mutableListOf<Array<IntArray>>()
        for(p in centers.sortedBy { if(level.themeAt(it.x,it.y) in listOf(DungeonTheme.CEMETERY,DungeonTheme.VILLAGE,DungeonTheme.CAMP,DungeonTheme.MINE_DEPOT))0 else 1 }) {
            if(hotspotFields.any { it[p.y][p.x] in 0..12 })continue
            sites+=MapSite(p,MapSiteKind.HOTSPOT);hotspotFields+=DungeonPaths.distances(level,p)
            if(hotspotFields.size==hotspotCount)break
        }
        if(hotspotFields.isEmpty() && candidates.isNotEmpty()) {
            val p=candidates.first();sites+=MapSite(p,MapSiteKind.HOTSPOT);hotspotFields+=DungeonPaths.distances(level,p)
        }
        val quiet=mutableSetOf<Pos>()
        val quietCenters=mutableListOf<Pos>()
        val quietCount=when(level.format){DungeonFormat.MICRO->1;DungeonFormat.EXPEDITION->2;DungeonFormat.REGION->3;DungeonFormat.VAST->4}
        for(p in (rooms.map { it.center() }.shuffled(rng)+candidates)) {
            if(p !in candidates || exit[p.y][p.x]<8 || hotspotFields.any { it[p.y][p.x] in 0..8 } || p in quiet)continue
            val field=DungeonPaths.distances(level,p,if(level.format==DungeonFormat.MICRO)2 else 3)
            for(q in all)if(field[q.y][q.x]>=0)quiet+=q
            quietCenters+=p;sites+=MapSite(p,MapSiteKind.QUIET)
            if(quietCenters.size==quietCount)break
        }
        val eligible=candidates.filter { it !in quiet }
        val groups=eligible.groupBy { level.districts[it.y][it.x] }
        val quotas=groups.keys.associateWith { 1 }.toMutableMap()
        val weights=groups.mapValues { (_,cells) -> sqrt(cells.size.toDouble()) * when(level.themeAt(cells.first().x,cells.first().y)) {DungeonTheme.FOREST->.7;DungeonTheme.CEMETERY,DungeonTheme.VILLAGE,DungeonTheme.MINE_DEPOT->1.2;else->1.0} }
        repeat((target-quotas.size).coerceAtLeast(0)) {
            val id=groups.keys.maxByOrNull { weights.getValue(it)/(quotas.getValue(it)+1) } ?: return@repeat
            quotas[id]=quotas.getValue(id)+1
        }
        val spawns=mutableListOf<PackSpawn>()
        val nearest=Array(level.h){IntArray(level.w){Int.MAX_VALUE}}
        fun add(p:Pos,role:PackRole) {
            val distance=DungeonPaths.distances(level,p,12)
            for(y in 0 until level.h)for(x in 0 until level.w)if(distance[y][x]>=0)nearest[y][x]=minOf(nearest[y][x],distance[y][x])
            val route=if(role==PackRole.PATROL) all.filter { q -> distance[q.y][q.x] in 3..7 &&
                level.districts[q.y][q.x]!=level.districts[p.y][p.x] && q !in quiet && camp[q.y][q.x]>=safeDistance && exit[q.y][q.x]>6 }
                .minByOrNull { distance[it.y][it.x] }?.let { listOf(p,it) } ?: emptyList() else emptyList()
            spawns+=PackSpawn(p,if(role==PackRole.PATROL && route.isEmpty())PackRole.LOCAL else role,route)
            if(route.isNotEmpty())sites+=MapSite(p,MapSiteKind.TRANSITION)
        }
        // One guard faces the approach; the eight cells around the stairs stay free.
        eligible.filter { exit[it.y][it.x] in 3..6 }.minByOrNull { exit[it.y][it.x] }?.let { add(it,PackRole.GUARD) }
        // Populate the combat sites first: a marker alone must not become an empty hotspot.
        for(field in hotspotFields) repeat(2) {
            val p=eligible.filter { q ->
                val id=level.districts[q.y][q.x]
                exit[q.y][q.x]>6 && field[q.y][q.x] in 0..7 && nearest[q.y][q.x]>=3 &&
                    spawns.count { level.districts[it.pos.y][it.pos.x]==id }<(quotas[id] ?: 0)
            }.minByOrNull { field[it.y][it.x] }
            if(p!=null && spawns.size<target)add(p,PackRole.CLUSTER)
        }
        val border=eligible.filter { p -> listOf(Pos(0,-1),Pos(1,0),Pos(0,1),Pos(-1,0)).any { d ->
            level.walkable(p.x+d.x,p.y+d.y) && level.districts[p.y+d.y][p.x+d.x]!=level.districts[p.y][p.x] } }
        val borderDistance=DungeonPaths.distances(level,border)
        for((id,quota) in quotas) {
            val pool=groups.getValue(id).filter { exit[it.y][it.x]>6 }
            val already=spawns.count { level.districts[it.pos.y][it.pos.x]==id }
            repeat((quota-already).coerceAtLeast(0)) { index ->
                val role=when { index==0 && border.isNotEmpty()->PackRole.PATROL;index%2==1->PackRole.CLUSTER;else->PackRole.LOCAL }
                fun score(p:Pos):Int=when(role) {
                    PackRole.PATROL -> if(borderDistance[p.y][p.x]<0)10000 else borderDistance[p.y][p.x]
                    PackRole.CLUSTER -> hotspotFields.minOfOrNull { it[p.y][p.x].takeIf { d -> d>=0 } ?: 10000 } ?: 10000
                    else -> if(poiDistance[p.y][p.x]<0)10 else poiDistance[p.y][p.x]
                }
                val ranked=pool.sortedBy(::score)
                val spacing=if(role==PackRole.CLUSTER)3 else if(level.themeAt(pool.firstOrNull()?.x ?: level.start.x,pool.firstOrNull()?.y ?: level.start.y)==DungeonTheme.FOREST)8 else 6
                val p=ranked.firstOrNull { nearest[it.y][it.x]>=spacing } ?: ranked.firstOrNull { nearest[it.y][it.x]>=3 }
                if(p!=null)add(p,role)
            }
        }
        // Tiny districts may lack enough sites; redistribute unused capacity, never the safe zones.
        for(p in eligible) {
            if(spawns.size>=target)break
            if(exit[p.y][p.x]>6 && nearest[p.y][p.x]>=3)add(p,PackRole.LOCAL)
        }
        return PopulationPlan(spawns,sites,quiet,spawns.groupingBy { level.districts[it.pos.y][it.pos.x] }.eachCount())
    }
}
