package com.Atom2Universe.app.games.roguelike

import com.Atom2Universe.app.R
import kotlin.random.Random

/** Format and geography are rolled without reading the dungeon depth. */
internal enum class DungeonFormat(val label:Int,val minSize:Int,val maxSize:Int,val minPacks:Int,val maxPacks:Int) {
    MICRO(R.string.roguelike_format_micro,17,23,3,4),
    EXPEDITION(R.string.roguelike_format_expedition,25,39,5,8),
    REGION(R.string.roguelike_format_region,45,69,10,18),
    VAST(R.string.roguelike_format_vast,71,99,18,30);
}
internal data class DungeonSpec(val format:DungeonFormat,val w:Int,val h:Int,val packs:Int,val regions:List<DungeonTheme>)
internal object DungeonFormats {
    fun roll(rng:Random,format:DungeonFormat= DungeonFormat.entries.random(rng)):DungeonSpec {
        fun dimension()=format.minSize+2*rng.nextInt((format.maxSize-format.minSize)/2+1)
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
        return DungeonSpec(format,dimension(),dimension(),rng.nextInt(format.minPacks,format.maxPacks+1),regions)
    }
}
