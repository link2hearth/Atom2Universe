package com.Atom2Universe.app.games.roguelike

import com.Atom2Universe.app.R

/** Shared exploration and combat regions; all art is generated in code. */
enum class DungeonTheme(val label: Int, val ground: Int, val stone: Int,
    val outdoor: Boolean, vararg val backdrops: DungeonBackdrop) {
    CEMETERY(R.string.roguelike_region_cemetery, 0xFF344340.toInt(), 0xFF77817E.toInt(), true,
        DungeonBackdrop.CEMETERY_DAY, DungeonBackdrop.CEMETERY_NIGHT, DungeonBackdrop.CRYPT),
    DUNGEON(R.string.roguelike_region_dungeon, 0xFF34343E.toInt(), 0xFF676878.toInt(), false, DungeonBackdrop.DUNGEON),
    FOREST(R.string.roguelike_region_forest, 0xFF385344.toInt(), 0xFF427354.toInt(), true,
        DungeonBackdrop.MEADOW_DAY, DungeonBackdrop.MEADOW_NIGHT, DungeonBackdrop.JUNGLE_DAY, DungeonBackdrop.JUNGLE_NIGHT),
    FIELDS(R.string.roguelike_region_fields, 0xFF736748.toInt(), 0xFFB39A56.toInt(), true,
        DungeonBackdrop.WHEAT_DAY, DungeonBackdrop.WHEAT_NIGHT),
    BATTLEFIELD(R.string.roguelike_region_battlefield, 0xFF544B43.toInt(), 0xFF82796B.toInt(), true,
        DungeonBackdrop.BATTLEFIELD_DAY, DungeonBackdrop.BATTLEFIELD_NIGHT),
    MINE(R.string.roguelike_region_mine, 0xFF403B38.toInt(), 0xFF796A5B.toInt(), false, DungeonBackdrop.MINE),
    LIBRARY(R.string.roguelike_region_library, 0xFF554039.toInt(), 0xFF96704F.toInt(), false, DungeonBackdrop.LIBRARY),
    MONASTERY(R.string.roguelike_region_monastery, 0xFF63665D.toInt(), 0xFF999B89.toInt(), false,
        DungeonBackdrop.MONASTERY_DAY, DungeonBackdrop.MONASTERY_NIGHT),
    PIRATE(R.string.roguelike_region_pirate, 0xFF624A38.toInt(), 0xFF99764B.toInt(), false,
        DungeonBackdrop.PIRATE_DECK_DAY, DungeonBackdrop.PIRATE_DECK_NIGHT, DungeonBackdrop.CAPTAIN_CABIN),
    INN(R.string.roguelike_region_inn, 0xFF634E3C.toInt(), 0xFF9A754C.toInt(), false, DungeonBackdrop.INN),
    SPACESHIP(R.string.roguelike_region_spaceship, 0xFF303F53.toInt(), 0xFF647D93.toInt(), false, DungeonBackdrop.SPACESHIP);

    fun backdrop(seed: Int) = backdrops[Math.floorMod(seed, backdrops.size)]
    companion object {
        fun forFloor(floor: Int) = entries[(floor.coerceAtLeast(1) - 1) % entries.size]
    }
}
