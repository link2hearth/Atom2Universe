package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import com.Atom2Universe.app.games.caves.node.FarmSoil
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import kotlin.random.Random

/** Shared blueprints for the Assault showcase and small procedural landmarks. */
internal object FrontierLandscape {
    class Cell(val id: Short, val meta: Byte = 0)
    const val KINDS=6
    fun size(kind: Int)=if(kind==0) 43 else 19
    /** One landmark relative to its floor (y = 0); [heightAt] is the terrain height relative to that floor.
     * Huts add an entrance stair up to five blocks in front of them (negative z). */
    fun blueprint(kind: Int,arid: Boolean,cold: Boolean,rng: Random,heightAt: (Int,Int)->Int): Map<Triple<Int,Int,Int>,Cell> {
        val wood=if(cold) WOOD_SAPIN else if(arid) WOOD_DARK else WOOD
        val plank=if(cold) PLANK_SAPIN else if(arid) PLANK_DARK else PLANK
        val stone=if(arid) SANDSTONE else if(cold) STONE else COBBLESTONE
        val data=linkedMapOf<Triple<Int,Int,Int>,Cell>()
        fun put(dx: Int,dy: Int,dz: Int,id: Short,meta: Byte=0) { data[Triple(dx,dy,dz)]=Cell(id,meta) }
        fun plot(ox: Int,oz: Int,w: Int,d: Int,floor: Short) {
            for(dx in ox until ox+w) for(dz in oz until oz+d) {
                val h=heightAt(dx,dz)
                for(dy in minOf(h,-1)..0) put(dx,dy,dz,if(dy==0) floor else stone)
                for(dy in 1..12) put(dx,dy,dz,AIR)
            }
        }
        val houses=FrontierHouses.style(arid,cold)
        fun hut(ox: Int,oz: Int,model: Int) {
            val m=FrontierHouses.models[model]
            plot(ox-1,oz-1,m.width+2,m.depth+2,stone)
            FrontierHouses.build(model,houses) { dx,dy,dz,id -> put(ox+dx,dy,oz+dz,id) }
            put(ox+1,1,oz+m.depth-2,F.CACHE); put(ox+m.width-2,1,oz+m.depth-2,F.COOKER)
            put(ox+1,1,oz+m.doorZ+1,TORCH)
            // The terrain may be three blocks lower than the floor: provide a walkable entrance.
            for(step in 0..4) for(dx in m.doorX..m.doorX+1) {
                val px=ox+dx; val pz=oz-2-step
                val ground=heightAt(px,pz)
                val level=maxOf(ground.coerceAtMost(0),-step)
                for(dy in minOf(ground,level)..level) put(px,dy,pz,stone)
                for(dy in level+1..level+3) put(px,dy,pz,AIR)
            }
        }
        fun garden(ox: Int,oz: Int) {
            plot(ox,oz,9,9,DIRT)
            for(dx in 1..7) for(dz in 1..7) {
                if(dx==4) put(ox+dx,0,oz+dz,WATER)
                else {
                    put(ox+dx,0,oz+dz,FarmSoil.FARMLAND)
                    val crop=if(arid) listOf(1,9,14)[dz%3] else if(cold) listOf(4,5,15)[dz%3] else listOf(0,2,3,6,13)[dz%5]
                    put(ox+dx,1,oz+dz,FarmShowcasePlants.id(crop,4))
                }
            }
        }
        when(kind) {
            0 -> { // Four households around a planted square and communal store.
                // Four different houses; each fits 12 x 9 so the lanes and gardens stay clear.
                val models=(0 until FrontierHouses.models.size).shuffled(rng)
                for((i,plot) in listOf(1 to 1,30 to 1,1 to 30,30 to 30).withIndex()) hut(plot.first,plot.second,models[i])
                for(i in 0..40) for(w in 18..22) {
                    plot(i,w,1,1,GRAVEL); plot(w,i,1,1,GRAVEL)
                }
                garden(5,16); garden(27,16)
                for(px in listOf(3,4,34,35)) for(pz in 11..27) plot(px,pz,1,1,GRAVEL)
                plot(18,18,5,5,stone)
                for(dx in 19..21) for(dz in 19..21) put(dx,0,dz,WATER)
                for(dx in listOf(18,22)) for(dz in listOf(18,22)) { put(dx,1,dz,stone); put(dx,2,dz,TORCH) }
                put(16,1,18,F.CACHE); put(16,1,22,F.COMPOSTER)
            }
            1 -> { // Survey camp, two open tents and supplies.
                plot(0,0,18,18,GRAVEL_DIRT)
                for(ox in listOf(1,11)) for(dz in 0..6) for(dx in 0..5)
                    put(ox+dx,1+minOf(dx,5-dx),2+dz,plank)
                for(dx in 7..9) for(dz in 10..12) put(dx,1,dz,stone)
                put(8,2,11,TORCH); put(3,1,11,F.CACHE); put(13,1,11,8000)
                put(12,1,14,wood); put(13,1,14,wood); put(14,1,14,wood)
            }
            2 -> { // Roofless archive ruins, overgrown and worth searching.
                plot(1,1,16,16,MOSSY_COBBLESTONE)
                for(dx in 1..16) for(dz in 1..16) if(dx in listOf(1,16) || dz in listOf(1,16))
                    for(dy in 1..rng.nextInt(1,5)) if(!(dz==1 && dx in 7..9)) put(dx,dy,dz,if(rng.nextBoolean()) stone else MOSSY_COBBLESTONE)
                for(dx in listOf(4,13)) for(dz in listOf(4,13)) for(dy in 1..6) put(dx,dy,dz,stone)
                put(12,1,12,F.CACHE); put(5,1,6,MOSS); put(6,1,6,MOSS)
            }
            3 -> { // Celestial resonance garden: a Cave World landmark.
                plot(0,0,19,19,stone)
                for(dx in 0..18) for(dz in 0..18) {
                    val r=(dx-9)*(dx-9)+(dz-9)*(dz-9)
                    if(r in 50..75) put(dx,1,dz,QUARTZ)
                }
                for((dx,dz) in listOf(3 to 9,15 to 9,9 to 3,9 to 15)) {
                    for(dy in 1..4) put(dx,dy,dz,stone)
                    put(dx,5,dz,CRYSTAL)
                }
                put(9,1,9,QUARTZ); put(9,2,9,CRYSTAL); put(11,1,11,F.CACHE)
            }
            4 -> { // Kitchen garden and small open shelter.
                garden(1,1); garden(10,1)
                plot(1,11,17,7,plank)
                for(dx in listOf(1,17)) for(dz in listOf(11,17)) for(dy in 1..4) put(dx,dy,dz,wood)
                for(dx in 0..18) for(dz in 10..18) put(dx,5,dz,plank)
                put(3,1,15,F.COMPOSTER); put(8,1,15,F.CACHE); put(13,1,15,F.COOKER)
            }
            else -> { // Prospector workshop with a small windmill (axis codes as for logs: 0 = Y, 1 = X, 2 = Z).
                hut(1,1,if(rng.nextBoolean()) 0 else 4); plot(11,1,7,16,GRAVEL)
                // Timber tower; the head on its front, a shaft and a gearbox send the rotation down to the mill.
                for(dx in listOf(13,15)) for(dz in listOf(4,6)) for(dy in 1..6) put(dx,dy,dz,wood)
                for(dx in 13..15) for(dz in 3..6) put(dx,7,dz,plank)
                put(14,6,3,F.WINDMILL,2); put(14,6,4,F.SHAFT,2); put(14,6,5,F.GEARBOX,1)
                for(dy in 2..5) put(14,dy,5,F.SHAFT,0)
                put(14,1,5,F.MILL)
                // Sails left as blocks, a pinwheel in front of the head: using the head sets them turning.
                fun sail(a: Int,b: Int) = put(14+a,6+b,2,F.SAIL)
                sail(0,0)
                for(i in 1..3) { sail(i,0); sail(0,i); sail(-i,0); sail(0,-i) }
                for(i in 2..3) { sail(i,1); sail(-1,i); sail(-i,-1); sail(1,-i) }
                for(dz in 9..14) for(dx in 11..16) put(dx,1,dz,if(rng.nextInt(5)==0) COPPER else ROCK)
                put(12,1,6,F.CACHE)
            }
        }
        return data
    }
}
