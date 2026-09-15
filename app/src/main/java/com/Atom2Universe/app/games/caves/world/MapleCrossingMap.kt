package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.StreetMaterials as Street
import kotlin.math.floor
import kotlin.random.Random

/** Built from the room and circulation plan in CAVE_WORLD_MAPLE_CROSSING.md. */
internal object MapleCrossingMap {
    const val ID = "maple_crossing"
    const val SIZE = 176
    const val PLOT_STEP = 62
    const val PLOT_COUNT = 3
    const val HEIGHT = 32
    const val FLOOR = 4
    const val FEET = 5
    fun create(): A2Map = Builder().build()

    private data class ClearWay(val x0: Float, val z0: Float, val x1: Float, val z1: Float)
    private class Builder {
        val blocks = ShortArray(SIZE*SIZE*HEIGHT)
        val meta = ByteArray(blocks.size)
        val decor = ArrayList<CaveDecor>()
        val passages = ArrayList<ClearWay>()
        val checkpoints = ArrayList<MapPoint>()
        fun index(x: Int,y: Int,z: Int) = x+SIZE*(z+SIZE*y)
        fun block(x: Int,y: Int,z: Int): Short =
            if(x in 0 until SIZE && y in 0 until HEIGHT && z in 0 until SIZE) blocks[index(x,y,z)] else AIR
        fun put(x: Int,y: Int,z: Int,id: Short) {
            require(x in 0 until SIZE && y in 0 until HEIGHT && z in 0 until SIZE)
            blocks[index(x,y,z)]=id
        }
        fun fill(x0: Int,y0: Int,z0: Int,x1: Int,y1: Int,z1: Int,id: Short) {
            require(x0<=x1 && y0<=y1 && z0<=z1)
            for(y in y0..y1) for(z in z0..z1) for(x in x0..x1) put(x,y,z,id)
        }

        fun build(): A2Map {
            fill(0,0,0,SIZE-1,2,SIZE-1,DIRT)
            fill(0,3,0,SIZE-1,3,SIZE-1,DIRT)
            fill(0,FLOOR,0,SIZE-1,FLOOR,SIZE-1,GRASS)
            street()
            val styles = shortArrayOf(PLANK_BLUE, WOOD_PLANK_WHITE, PLANK_PINK, PLANK)
            for(row in 0 until PLOT_COUNT) for(col in 0 until PLOT_COUNT) {
                val variant=(row*PLOT_COUNT+col)%4
                House(4+col*PLOT_STEP,4+row*PLOT_STEP,if(row<2) 0 else 2,variant,styles[variant]).build()
            }
            // Continuous rear-garden routes connect the nine walkable plots.
            fill(0,4,0,1,8,SIZE-1,BRICK_GREY); fill(SIZE-2,4,0,SIZE-1,8,SIZE-1,BRICK_GREY)
            fill(2,4,0,SIZE-3,8,1,BRICK_GREY); fill(2,4,SIZE-2,SIZE-3,8,SIZE-1,BRICK_GREY)
            // Staggered street parking breaks long sight lines but leaves both crossing axes open.
            decor += CaveDecor("outdoor.family_car",78f,4f,58f,.22f,1)
            decor += CaveDecor("outdoor.family_car",33f,4f,53f,.22f,3)
            decor += CaveDecor("outdoor.family_car",140f,4f,120f,.22f,1)
            decor += CaveDecor("outdoor.family_car",95f,4f,115f,.22f,3)
            for(x in intArrayOf(49,62,111,124)) for(z in intArrayOf(49,62,111,124)) {
                fill(x,5,z,x,9,z,BRICK_OBSIDIAN)
                put(x,10,z,Street.LIGHT)
            }
            val spawn=MapPoint(21,5,12)
            validate(spawn)
            return A2Map(ID,SIZE,HEIGHT,SIZE,blocks,meta,listOf(spawn),
                listOf(MapPoint(145,5,136),MapPoint(145,5,12),MapPoint(21,5,136)),decor)
        }

        private fun street() {
            val roads = intArrayOf(51, 113)
            fun road(t: Int) = roads.any { t in it..it+9 }
            // Paint sidewalks first, then cut both road axes through them.
            for(r in roads) for(b in listOf(r-4..r-1,r+10..r+13)) {
                fill(b.first,4,0,b.last,4,SIZE-1,Street.SIDEWALK)
                fill(0,4,b.first,SIZE-1,4,b.last,Street.SIDEWALK)
            }
            for(r in roads) {
                fill(r,4,0,r+9,4,SIZE-1,AIR)
                fill(0,4,r,SIZE-1,4,r+9,AIR)
                fill(r,3,0,r+9,3,SIZE-1,Street.ASPHALT)
                fill(0,3,r,SIZE-1,3,r+9,Street.ASPHALT)
            }
            for(r in roads) for(t in 2 until SIZE-2) {
                if(roads.none { t in it-5..it+14 }) {
                    put(r+4,3,t,Street.LANE_NS); put(t,3,r+4,Street.LANE_EW)
                }
                if(!road(t)) {
                    put(r-1,4,t,Street.CURB); put(r+10,4,t,Street.CURB)
                    put(t,4,r-1,Street.CURB); put(t,4,r+10,Street.CURB)
                }
            }
            for(rx in roads) for(rz in roads) {
                for(t in 0..9) for(offset in intArrayOf(-4,-3,12,13)) {
                    put(rx+t,3,rz+offset,Street.CROSS_NS)
                    put(rx+offset,3,rz+t,Street.CROSS_EW)
                }
                for(t in 5..9) {
                    put(rx+t,3,rz-6,Street.STOP_NS)
                    put(rx-6,3,rz+9-t,Street.STOP_EW)
                }
                for(t in 0..3) {
                    put(rx+t,3,rz+15,Street.STOP_NS)
                    put(rx+15,3,rz+9-t,Street.STOP_EW)
                }
                put(rx+3,3,rz+3,Street.MANHOLE)
                put(rx+7,3,rz+7,Street.MANHOLE)
            }
            for(r in roads) for(t in 20 until SIZE-4 step 23) if(!road(t)) {
                put(r,3,t,Street.DRAIN); put(r+9,3,t,Street.DRAIN)
                put(t,3,r,Street.DRAIN); put(t,3,r+9,Street.DRAIN)
            }
        }
        private inner class House(val ox: Int,val oz: Int,val turn: Int,val variant: Int,val siding: Short) {
            // Voxel centres transform around the 42 x 42 parcel centre.
            fun wx(x: Int) = ox+if(turn==0) x else 41-x
            fun wz(z: Int) = oz+if(turn==0) z else 41-z
            fun box(x0: Int,y0: Int,z0: Int,x1: Int,y1: Int,z1: Int,id: Short) {
                fill(minOf(wx(x0),wx(x1)),y0,minOf(wz(z0),wz(z1)),
                    maxOf(wx(x0),wx(x1)),y1,maxOf(wz(z0),wz(z1)),id)
            }
            fun clearWay(x0: Float,z0: Float,x1: Float,z1: Float) {
                fun x(v: Float)=ox+if(turn==0) v else 42-v
                fun z(v: Float)=oz+if(turn==0) v else 42-v
                passages += ClearWay(minOf(x(x0),x(x1)),minOf(z(z0),z(z1)),maxOf(x(x0),x(x1)),maxOf(z(z0),z(z1)))
            }
            fun checkpoint(x: Int,z: Int) { checkpoints += MapPoint(wx(x),FEET,wz(z)) }
            fun prop(id: String,x: Float,z: Float,scale: Float=.11f,facing: Int=0,bottom: Float=FEET.toFloat()): CaveDecor {
                val model=CaveDecorModels.get(id)
                val p=CaveDecor(id,ox+if(turn==0) x else 42-x,bottom-model.bounds.bottom*scale,
                    oz+if(turn==0) z else 42-z,scale,(facing+turn)%4)
                decor += p
                return p
            }
            fun on(support: CaveDecor,id: String,side: Float=0f,forward: Float=0f,scale: Float=.065f) {
                val placement=support.placement()
                // The pegboard/dishes are higher than the usable worktop: use the actual surface.
                val surface=when(support.modelId) {
                    "garage.workbench" -> 10f
                    "living.dining_table" -> 11.1f
                    else -> placement.model.bounds.top
                }
                decor += CaveDecor(id,support.x+placement.rotatedX(side,forward),
                    support.y+surface*support.scale-CaveDecorModels.get(id).bounds.bottom*scale,
                    support.z+placement.rotatedZ(side,forward),scale,support.quarterTurns)
            }

            fun build() {
                shell()
                interiors()
                yard()
                // Exact floor regions that must stay open for circulation and combat.
                clearWay(16f,10f,19f,38f)
                for(z in intArrayOf(15,27)) clearWay(13f,z.toFloat(),17f,z+3f)
                for(z in intArrayOf(13,20,27)) clearWay(18f,z.toFloat(),22f,z+3f)
                clearWay(27f,28f,33f,31f)
                clearWay(33f,31f,38f,42f)
                // Check the salon's open floor, not the voxel touched by the armchair's armrest.
                for(p in listOf(17 to 12,17 to 32,12 to 16,10 to 30,22 to 14,22 to 21,22 to 28,32 to 29,35 to 35))
                    checkpoint(p.first,p.second)
            }

            private fun shell() {
                box(4,4,10,29,4,33,PLANK)
                box(4,5,10,4,8,33,siding); box(29,5,10,29,8,33,siding)
                box(5,5,10,28,8,10,siding); box(5,5,33,28,8,33,siding)
                box(15,5,11,15,8,32,WOOD_PLANK_WHITE); box(19,5,11,19,8,32,WOOD_PLANK_WHITE)
                box(5,5,21,14,8,21,WOOD_PLANK_WHITE)
                box(20,5,18,28,8,18,WOOD_PLANK_WHITE); box(20,5,24,28,8,24,WOOD_PLANK_WHITE)
                box(20,4,11,28,4,17,Street.TILE); box(20,4,25,28,4,32,Street.TILE)
                for(z in intArrayOf(15,27)) box(15,5,z,15,7,z+2,AIR)
                for(z in intArrayOf(13,20,27)) box(19,5,z,19,7,z+2,AIR)
                for(z in intArrayOf(10,33)) box(16,5,z,18,7,z,AIR)
                // Glazed openings with sills, opaque piers and a lintel above.
                for(x in intArrayOf(7,23)) for(z in intArrayOf(10,33)) {
                    box(x,6,z,x+3,7,z,GLASS)
                    box(x,5,z,x+3,5,z,WOOD_PLANK_WHITE)
                }
                for(z in intArrayOf(13,26)) { box(4,6,z,4,7,z+3,GLASS); box(29,6,z,29,7,z+2,GLASS) }
                // Garage, lower roof and an open internal link through the side of the house.
                box(31,4,19,39,4,33,Street.CONCRETE)
                box(31,5,19,31,7,33,siding); box(39,5,19,39,7,33,siding)
                box(32,5,19,38,7,19,siding); box(32,5,33,38,7,33,WOOD_PLANK_WHITE)
                box(33,5,33,37,7,33,AIR)
                box(29,4,28,31,4,30,Street.CONCRETE)
                box(29,5,28,31,7,30,AIR)
                box(30,5,27,30,7,27,siding); box(30,5,31,30,7,31,siding)
                box(30,8,27,31,8,31,Street.SHINGLES)
                box(31,8,18,40,8,34,Street.SHINGLES)
                box(4,9,10,29,9,33,WOOD_PLANK_WHITE)
                // Hollow stepped gable above a sealed ceiling, with generous white eaves.
                for(x in 3..30) {
                    val rise=minOf(x-3,30-x)/2
                    if(rise>0) { box(x,10,10,x,9+rise,10,siding); box(x,10,33,x,9+rise,33,siding) }
                    box(x,10+rise,9,x,10+rise,34,Street.SHINGLES)
                }
                box(6,10,13,7,16,14,BRICK_RED)
                box(12,4,34,22,4,36,Street.CONCRETE)
                for(x in intArrayOf(12,22)) box(x,5,36,x,7,36,WOOD_PLANK_WHITE)
                box(11,8,34,23,8,37,Street.SHINGLES)
                for(p in listOf(9 to 16,9 to 27,24 to 14,24 to 21,24 to 29,17 to 24)) box(p.first,9,p.second,p.first,9,p.second,Street.LIGHT)
                box(35,8,25,35,8,25,Street.LIGHT)
                box(17,8,35,17,8,35,Street.LIGHT)
            }

            private fun interiors() {
                prop("bedroom.double_bed",9f,14.6f,.12f)
                val bedside=prop("bedroom.nightstand",7.4f,13.3f,.12f)
                on(bedside,"bedroom.table_lamp",scale=.1f)
                on(prop("bedroom.nightstand",10.6f,13.3f,.12f),"living.books",scale=.055f)
                prop("bedroom.wardrobe",6f,19f,.12f,1)
                prop("office.dresser",11.5f,19.6f,.11f,2)
                prop("living.sofa",8f,25f,.14f)
                prop("living.tv_cabinet",8f,31.5f,.12f,2)
                on(prop("living.coffee_table",8f,28f,.1f),"living.books",side=-.2f,scale=.05f)
                prop("living.armchair",11.5f,28f,.14f,3)
                prop("living.floor_lamp",6f,25f,.12f)
                prop("living.plant",12f,31f,.12f)
                prop("kitchen.fridge",21f,25.7f)
                prop("kitchen.oven",22.4f,25.7f)
                prop("kitchen.sink",23.8f,25.7f)
                val counter=prop("kitchen.counter",25.4f,25.7f)
                on(counter,"kitchen.toaster",side=-.3f,scale=.07f)
                on(counter,"kitchen.kettle",side=.38f,scale=.065f)
                on(prop("living.dining_table",24f,30f,.1f),"kitchen.fruit_bowl",scale=.055f)
                prop("living.dining_chair",24f,28.6f,.105f)
                prop("living.dining_chair",24f,31.4f,.105f,2)
                prop(if(variant%2==0) "bathroom.shower" else "bathroom.bathtub",21.4f,12.25f,.11f,
                    facing=if(variant%2==0) 0 else 1)
                prop("bathroom.vanity",24.3f,11.9f)
                prop("bathroom.mirror",24.3f,11.25f,.1f,bottom=6.4f)
                prop("bathroom.toilet",27f,12f,.1f)
                prop("bathroom.towel_rack",27f,16f,.1f)
                when(variant) {
                    0 -> {
                        val desk=prop("cave.desk",25f,20f,.1f)
                        on(desk,"office.computer",side=-.3f)
                        on(desk,"office.pc_tower",side=.7f)
                        prop("living.dining_chair",25f,21.6f,.105f,2)
                    }
                    1 -> {
                        prop("bedroom.double_bed",24.2f,21.5f,.09f)
                        on(prop("office.dresser",26.6f,20f,.11f),"kawaii.robot",scale=.085f)
                        prop("kawaii.train",26f,22.5f,.12f)
                        prop("bedroom.nightstand",23.5f,19.9f,.12f)
                    }
                    2 -> {
                        prop("kawaii.arcade_cabinet",25f,20.1f,.28f)
                        val table=prop("cave.desk",27f,22f,.1f,3)
                        on(table,"kawaii.handheld_console",scale=.08f)
                        on(table,"kawaii.gamepad",side=.5f,scale=.07f)
                    }
                    else -> {
                        prop("office.exercise_bike",25f,21f,.14f)
                        prop("garage.tool_chest",27.5f,20f,.11f)
                        prop("living.plant",27.5f,22.5f,.1f)
                    }
                }
                prop("outdoor.family_car",35f,28f,.22f)
                on(prop("garage.workbench",35f,20.6f,.11f),"garage.toolbox",side=-.5f,forward=.1f,scale=.075f)
                prop("garage.tool_chest",37.8f,20.6f,.1f)
                prop("garage.tires",37.8f,24f,.1f)
                prop("bathroom.washer",32.7f,21f,.11f)
                prop("bathroom.laundry_basket",32.7f,23f,.1f)
            }

            private fun yard() {
                box(16,4,0,18,4,9,Street.CONCRETE)
                box(16,4,37,18,4,46,Street.CONCRETE)
                box(33,4,34,37,4,46,Street.CONCRETE)
                prop("outdoor.planter",14f,35f,.13f)
                prop("outdoor.mailbox",25f,40f,.11f)
                prop("outdoor.bench",8f,6f,.13f,2)
                prop("outdoor.hedge",8f,40f,.13f)
                // Native trees, never overwrite architecture or props with generated foliage.
                for((i,p) in listOf(36 to 3,40 to 40).withIndex()) {
                    GrandTrees.generate(if(i==0) "giant_pine" else "broad_oak",Random(9021+variant*7+i)) { x,y,z,id,_ ->
                        val gx=wx(p.first+x); val gz=wz(p.second+z); val gy=FLOOR+y
                        if(gx in 2 until SIZE-2 && gz in 2 until SIZE-2 && gy in 5 until HEIGHT && block(gx,gy,gz)==AIR) put(gx,gy,gz,id)
                    }
                }
                // Low side fence with a rear passage: the back garden remains a flank route.
                box(1,5,12,1,5,33,WOOD_PLANK_WHITE)
                for(z in 12..33 step 4) box(1,5,z,1,6,z,WOOD_PLANK_WHITE)
            }
        }

        private fun validate(spawn: MapPoint) {
            require(decor.size<=A2Map.MAX_DECOR) { "Maple Crossing: too many props" }
            for(obj in decor) for(b in obj.placement().solids) {
                require(b.left>=0 && b.right<SIZE && b.back>=0 && b.front<SIZE && b.bottom>=0 && b.top<HEIGHT)
                for(x in floor(b.left+.015f).toInt()..floor(b.right-.015f).toInt())
                    for(y in floor(b.bottom+.015f).toInt()..floor(b.top-.015f).toInt())
                        for(z in floor(b.back+.015f).toInt()..floor(b.front-.015f).toInt())
                            require(block(x,y,z)==AIR) { "Maple Crossing: ${obj.modelId} intersects block at $x,$y,$z" }
                if(b.bottom<FEET+2.8f && b.top>FEET+.05f) for(p in passages)
                    require(b.right<=p.x0 || b.left>=p.x1 || b.front<=p.z0 || b.back>=p.z1) {
                        "Maple Crossing: ${obj.modelId} obstructs a reserved passage"
                    }
            }
            require(block(spawn.x,spawn.y-1,spawn.z)!=AIR && block(spawn.x,spawn.y,spawn.z)==AIR)
            for(p in checkpoints) require(block(p.x,p.y,p.z)==AIR && block(p.x,p.y+1,p.z)==AIR) {
                "Maple Crossing: blocked room access $p"
            }
            // Ground-only flood fill: every room/garage must connect without climbing furniture.
            val scene=CaveDecorScene(decor)
            val levels=IntArray(SIZE*SIZE) { i -> if(block(i%SIZE,FLOOR,i/SIZE)==AIR) FLOOR else FEET }
            val free=BooleanArray(levels.size) { i ->
                val x=i%SIZE; val z=i/SIZE; val y=levels[i]
                block(x,y-1,z)!=AIR && block(x,y,z)==AIR && block(x,y+1,z)==AIR &&
                    !scene.occupied(x,y,z) && !scene.occupied(x,y+1,z)
            }
            val visited=BooleanArray(levels.size); val queue=IntArray(levels.size)
            val start=spawn.x+spawn.z*SIZE
            require(free[start]) { "Maple Crossing: spawn obstructed" }
            var read=0; var write=1; queue[0]=start; visited[start]=true
            val dx=intArrayOf(1,-1,0,0); val dz=intArrayOf(0,0,1,-1)
            while(read<write) {
                val n=queue[read++]; val x=n%SIZE; val z=n/SIZE
                for(d in 0..3) {
                    val nx=x+dx[d]; val nz=z+dz[d]
                    if(nx !in 0 until SIZE || nz !in 0 until SIZE) continue
                    val next=nx+nz*SIZE
                    if(!visited[next] && free[next] && kotlin.math.abs(levels[next]-levels[n])<=1) {
                        visited[next]=true; queue[write++]=next
                    }
                }
            }
            for(p in checkpoints) require(visited[p.x+p.z*SIZE]) { "Maple Crossing: unreachable room $p" }
        }
    }
}
