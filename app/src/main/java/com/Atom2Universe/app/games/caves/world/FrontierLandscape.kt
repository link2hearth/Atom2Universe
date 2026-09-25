package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import com.Atom2Universe.app.games.caves.node.FarmSoil
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/** Version 5 landmarks: each blueprint is independent of chunk generation order. */
internal class FrontierLandscape(private val seed: Long, private val terrain: NaturalTerrain) {
    private data class Voxel(val x: Int,val y: Int,val z: Int,val id: Short)
    private data class Site(val x: Int,val z: Int,val y: Int,val size: Int,
                            val chunks: Map<Triple<Int,Int,Int>, List<Voxel>>)
    private val sites=ConcurrentHashMap<Long,Site>()
    private fun site(cx: Int,cz: Int): Site = sites.getOrPut(columnCacheKey(cx,cz)) {
        if(sites.size>48) sites.clear()
        val rng=Random(seed xor cx.toLong()*341873128712L xor cz.toLong()*132897987541L xor 0x6F3A17L)
        val x=cx*192+36+rng.nextInt(80); val z=cz*192+36+rng.nextInt(80)
        val y=terrain.height(x.toDouble(),z.toDouble()).toInt()
        val empty=Site(x,z,y,0,emptyMap())
        if(rng.nextFloat()>.72f) return@getOrPut empty
        val kind=rng.nextInt(6); val size=if(kind==0) 43 else 19
        // Full footprint survey rejects shores, cliffs and cave mouths, not just its corners.
        for(dx in -2..size+2 step 3) for(dz in -2..size+2 step 3) {
            val wx=x+dx; val wz=z+dz
            val h=terrain.height(wx.toDouble(),wz.toDouble()).toInt()
            if(abs(h-y)>3 || h<=terrain.waterLevelAt(wx.toDouble(),wz.toDouble()) || terrain.caveAt(wx,h,wz)) return@getOrPut empty
        }
        val biome=terrain.biomeIdAt(x.toDouble(),z.toDouble())
        val arid=biome in setOf("desert","savanna","volcanic")
        val cold=terrain.temperature(x.toDouble(),z.toDouble())<.25
        val wood=if(cold) WOOD_SAPIN else if(arid) WOOD_DARK else WOOD
        val plank=if(cold) PLANK_SAPIN else if(arid) PLANK_DARK else PLANK
        val stone=if(arid) SANDSTONE else if(cold) STONE else COBBLESTONE
        val data=linkedMapOf<Triple<Int,Int,Int>,Short>()
        fun put(dx: Int,dy: Int,dz: Int,id: Short) { data[Triple(dx,dy,dz)]=id }
        fun plot(ox: Int,oz: Int,w: Int,d: Int,floor: Short) {
            for(dx in ox until ox+w) for(dz in oz until oz+d) {
                val h=terrain.height((x+dx).toDouble(),(z+dz).toDouble()).toInt()-y
                for(dy in minOf(h,-1)..0) put(dx,dy,dz,if(dy==0) floor else stone)
                for(dy in 1..12) put(dx,dy,dz,AIR)
            }
        }
        fun hut(ox: Int,oz: Int) {
            plot(ox-1,oz-1,11,11,stone)
            for(dx in 0..8) for(dz in 0..8) {
                put(ox+dx,0,oz+dz,plank)
                if(dx==0 || dx==8 || dz==0 || dz==8) for(dy in 1..4) {
                    val doorway=dz==0 && dx in 3..4 && dy<=2
                    val window=dy in 2..3 && ((dx in 3..5 && dz==8) || (dz in 3..5 && dx in listOf(0,8)))
                    put(ox+dx,dy,oz+dz,when { doorway->AIR;window->GLASS;dx in listOf(0,8) && dz in listOf(0,8)->wood;else->plank })
                }
            }
            for(dx in -1..9) for(dz in -1..9) put(ox+dx,5+minOf(dx+1,9-dx)/2,oz+dz,wood)
            put(ox+2,1,oz+6,F.CACHE); put(ox+6,1,oz+6,F.COOKER)
            put(ox+1,1,oz+1,TORCH)
            // The terrain may be three blocks lower than the floor: provide a walkable entrance.
            for(step in 0..4) for(dx in 3..4) {
                val px=ox+dx; val pz=oz-2-step
                val ground=terrain.height((x+px).toDouble(),(z+pz).toDouble()).toInt()-y
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
                for(ox in listOf(1,30)) for(oz in listOf(1,30)) hut(ox,oz)
                for(i in 0..40) for(w in 18..22) {
                    plot(i,w,1,1,GRAVEL); plot(w,i,1,1,GRAVEL)
                }
                garden(5,16); garden(27,16)
                for(px in listOf(3,4,34,35)) for(pz in 10..29) plot(px,pz,1,1,GRAVEL)
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
            else -> { // Prospector workshop with an unpowered mill to reclaim.
                hut(1,1); plot(11,1,7,16,GRAVEL)
                put(13,1,3,F.MILL); put(14,1,3,F.SHAFT); put(15,1,3,F.WATERWHEEL)
                for(dz in 9..14) for(dx in 11..16) put(dx,1,dz,if(rng.nextInt(5)==0) COPPER else ROCK)
                put(12,1,6,F.CACHE)
            }
        }
        val voxels=data.map { (p,id)->Voxel(x+p.first,y+p.second,z+p.third,id) }
        Site(x,z,y,size,voxels.groupBy { Triple(Math.floorDiv(it.x,16),Math.floorDiv(it.y,16),Math.floorDiv(it.z,16)) })
    }
    fun homes(x: Double,z: Double): List<FrontierLife.Place> = buildList {
        val cx=kotlin.math.floor(x/192).toInt();val cz=kotlin.math.floor(z/192).toInt()
        for(dx in -1..1) for(dz in -1..1) {
            val s=site(cx+dx,cz+dz)
            if(s.size==43) add(FrontierLife.Place(s.x,s.y,s.z))
        }
    }
    fun reserves(x: Int,z: Int,margin: Int = 0): Boolean {
        for(cx in Math.floorDiv(x-margin,192)..Math.floorDiv(x+margin,192))
            for(cz in Math.floorDiv(z-margin,192)..Math.floorDiv(z+margin,192)) {
                val s=site(cx,cz)
                if(s.size>0 && x in s.x-margin-2..s.x+s.size+margin+2 && z in s.z-margin-2..s.z+s.size+margin+2) return true
            }
        return false
    }
    fun decorate(c: Chunk) {
        val site=site(Math.floorDiv(c.worldX,192),Math.floorDiv(c.worldZ,192))
        for(v in site.chunks[Triple(c.cx,c.cy,c.cz)].orEmpty()) {
            c.setBlock(v.x-c.worldX,v.y-c.worldY,v.z-c.worldZ,v.id)
        }
    }
}
