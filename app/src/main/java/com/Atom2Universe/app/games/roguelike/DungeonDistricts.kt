package com.Atom2Universe.app.games.roguelike

import java.util.PriorityQueue
import kotlin.math.sin
import kotlin.random.Random

/** Connected maze skeleton with irregular, contiguous geographic districts grown from seeds. */
internal object DungeonDistricts {
    data class Plan(val layout:DungeonLayout,val themes:Array<Array<DungeonTheme>>,val districts:Array<IntArray>,val spec:DungeonSpec)
    private val dirs=listOf(Pos(0,-1),Pos(1,0),Pos(0,1),Pos(-1,0))
    private data class Frontier(val pos:Pos,val district:Int,val cost:Int)
    fun generate(spec:DungeonSpec,rng:Random):Plan {
        val w=spec.w;val h=spec.h
        val layout=DungeonGenerator.generate(w,h,rng)
        val tiles=layout.tiles
        tiles[layout.stairs.y][layout.stairs.x]=TileType.FLOOR
        val vertical=rng.nextBoolean();val phase=rng.nextDouble()*6.28
        val seeds=spec.regions.indices.map { i ->
            val along=(i+.5)/spec.regions.size
            val across=.5+sin(phase+i*1.9)*.24
            Pos(((if(vertical)across else along)*(w-4)+2).toInt(),((if(vertical)along else across)*(h-4)+2).toInt())
        }
        val districts=Array(h){IntArray(w){-1}}
        val costs=Array(h){IntArray(w){Int.MAX_VALUE}}
        val queue=PriorityQueue<Frontier>(compareBy<Frontier>{it.cost}.thenBy{it.district}.thenBy{it.pos.y}.thenBy{it.pos.x})
        val salt=rng.nextInt()
        seeds.forEachIndexed { i,p -> costs[p.y][p.x]=0;queue.add(Frontier(p,i,0)) }
        while(queue.isNotEmpty()) {
            val cell=queue.remove();val p=cell.pos
            if(cell.cost!=costs[p.y][p.x] || districts[p.y][p.x]>=0)continue
            districts[p.y][p.x]=cell.district
            for(d in dirs) {
                val q=Pos(p.x+d.x,p.y+d.y)
                if(q.x !in 0 until w || q.y !in 0 until h || districts[q.y][q.x]>=0)continue
                // Low-frequency terrain noise gives meandering borders rather than straight halves.
                val noise=(q.x/3*73856093 xor q.y/3*19349663 xor salt) and Int.MAX_VALUE
                val cost=cell.cost+8+noise%13
                if(cost<costs[q.y][q.x]) {costs[q.y][q.x]=cost;queue.add(Frontier(q,cell.district,cost))}
            }
        }
        // A room has one identity even when a geographic frontier crosses its footprint.
        for(room in layout.rooms) {
            val id=districts[room.center().y][room.center().x]
            for(y in room.y until room.y+room.h)for(x in room.x until room.x+room.w)districts[y][x]=id
        }
        val themes=Array(h){y->Array(w){x->spec.regions[districts[y][x]]}}
        if(spec.regions==listOf(DungeonTheme.PIRATE)) {
            val cabin=layout.rooms.maxBy { it.x*3+it.w*it.h }
            for(y in cabin.y-1..cabin.y+cabin.h)for(x in cabin.x-1..cabin.x+cabin.w)
                if(x in 1 until w-1 && y in 1 until h-1 && (cabin.contains(Pos(x,y)) || tiles[y][x]==TileType.WALL)) {
                    themes[y][x]=DungeonTheme.PIRATE_CABIN;districts[y][x]=1
                }
        }
        val widen=mutableListOf<Pos>()
        for(y in 1 until h-1)for(x in 1 until w-1) {
            if(themes[y][x].outdoor && tiles[y][x]==TileType.WALL &&
                dirs.any { tiles[y+it.y][x+it.x]!=TileType.WALL } && rng.nextFloat()<.58f)widen+=Pos(x,y)
        }
        widen.forEach { tiles[it.y][it.x]=TileType.FLOOR }
        val start=layout.rooms.filter { districts[it.center().y][it.center().x]==0 }
            .minByOrNull { it.center().chebyshev(seeds.first()) }?.center() ?: layout.start
        val distance=DungeonGenerator.distances(tiles,start)
        val last=if(spec.regions==listOf(DungeonTheme.PIRATE))1 else spec.regions.lastIndex
        val roomCells=layout.rooms.flatMap { room -> (room.y until room.y+room.h).flatMap { y -> (room.x until room.x+room.w).map { x -> Pos(x,y) } } }
        val stairs=roomCells.filter { it!=start && districts[it.y][it.x]==last }
            .maxByOrNull { distance[it.y][it.x] } ?: roomCells.filter { it!=start }.maxBy { distance[it.y][it.x] }
        tiles[stairs.y][stairs.x]=TileType.STAIRS_DOWN
        val ends=mutableListOf<Pos>()
        for(y in 1 until h-1)for(x in 1 until w-1)
            if(tiles[y][x]==TileType.FLOOR && dirs.count { tiles[y+it.y][x+it.x]!=TileType.WALL }==1)ends+=Pos(x,y)
        return Plan(DungeonLayout(tiles,layout.rooms,start,stairs,ends),themes,districts,spec)
    }
}
