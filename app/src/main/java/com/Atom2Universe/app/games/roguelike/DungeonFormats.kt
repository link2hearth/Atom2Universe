package com.Atom2Universe.app.games.roguelike

import com.Atom2Universe.app.R
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

internal enum class DungeonFormat(val label:Int,val minSize:Int,val maxSize:Int,val minPacks:Int,val maxPacks:Int) {
    MICRO(R.string.roguelike_format_micro,17,23,3,4),
    EXPEDITION(R.string.roguelike_format_expedition,25,39,5,8),
    REGION(R.string.roguelike_format_region,45,69,10,18),
    VAST(R.string.roguelike_format_vast,71,99,18,30);
}
internal data class DungeonSpec(val format:DungeonFormat,val w:Int,val h:Int,val packs:Int,val regions:List<DungeonTheme>)
internal object DungeonFormats {
    fun roll(rng:Random,format:DungeonFormat= DungeonFormat.entries.random(rng)):DungeonSpec {
        return build(format,rng,packBonus=0)
    }

    fun rollForFloor(floor:Int,rng:Random):DungeonSpec {
        val depth=floor.coerceAtLeast(1)
        val maxSize=maxSizeForFloor(depth)
        fun dimension()=17+2*rng.nextInt((maxSize-17)/2+1)
        val w=dimension()
        val h=dimension()
        val format=formatForSize(maxOf(w,h))
        return build(format,rng,w,h,packsFor(depth,w,h,rng))
    }

    private fun build(format:DungeonFormat,rng:Random,packBonus:Int):DungeonSpec {
        fun dimension()=format.minSize+2*rng.nextInt((format.maxSize-format.minSize)/2+1)
        return build(format,rng,dimension(),dimension(),rng.nextInt(format.minPacks,format.maxPacks+1)+packBonus)
    }

    private fun build(format:DungeonFormat,rng:Random,w:Int,h:Int,packs:Int):DungeonSpec {
        val regions=when(format) {
            DungeonFormat.MICRO -> listOf(listOf(DungeonTheme.SPACESHIP),listOf(DungeonTheme.PIRATE),listOf(DungeonTheme.CRYPT),listOf(DungeonTheme.LIBRARY),listOf(DungeonTheme.INN)).random(rng)
            DungeonFormat.EXPEDITION -> listOf(listOf(DungeonTheme.MINE,DungeonTheme.MINE_DEPOT),listOf(DungeonTheme.MONASTERY,DungeonTheme.DUNGEON),listOf(DungeonTheme.DUNGEON,DungeonTheme.CRYPT)).random(rng)
            else -> {
                val story=listOf(
                    listOf(DungeonTheme.VILLAGE,DungeonTheme.FIELDS,DungeonTheme.FOREST,DungeonTheme.MONASTERY),
                    listOf(DungeonTheme.CEMETERY,DungeonTheme.FOREST,DungeonTheme.CRYPT,DungeonTheme.DUNGEON),
                    listOf(DungeonTheme.BATTLEFIELD,DungeonTheme.CAMP,DungeonTheme.MONASTERY,DungeonTheme.LIBRARY),
                    listOf(DungeonTheme.MINE,DungeonTheme.MINE_DEPOT,DungeonTheme.LIBRARY,DungeonTheme.DUNGEON),
                    listOf(DungeonTheme.PORT,DungeonTheme.PIRATE,DungeonTheme.PIRATE_CABIN,DungeonTheme.INN)
                ).random(rng)
                story.take(if(format==DungeonFormat.VAST)4 else rng.nextInt(2,4))
            }
        }
        return DungeonSpec(format,w,h,packs,regions)
    }

    private fun maxSizeForFloor(depth:Int):Int {
        val t=(depth.coerceAtMost(500).toDouble()/500.0).pow(1.25)
        val raw=23+(DungeonFormat.VAST.maxSize-23)*t
        return raw.roundToInt().let { if(it%2==0)it+1 else it }.coerceIn(23,DungeonFormat.VAST.maxSize)
    }

    private fun formatForSize(size:Int)=when {
        size<=23 -> DungeonFormat.MICRO
        size<=39 -> DungeonFormat.EXPEDITION
        size<=69 -> DungeonFormat.REGION
        else -> DungeonFormat.VAST
    }

    private fun packsFor(depth:Int,w:Int,h:Int,rng:Random):Int {
        val area=w*h
        val density=(area/220.0).roundToInt()+(kotlin.math.sqrt(area.toDouble())/7.0).roundToInt()
        val depthPressure=(depth/40).coerceAtMost(8)
        val variance=(maxOf(w,h)-17)/16
        val earlyCap=when {
            depth<=25 -> 12
            depth<=50 -> 16
            depth<=100 -> 24
            else -> 64
        }
        return (density+depthPressure+variance+rng.nextInt(-1,2)).coerceIn(3,earlyCap)
    }
}
