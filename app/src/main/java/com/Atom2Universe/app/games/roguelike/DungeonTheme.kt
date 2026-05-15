package com.Atom2Universe.app.games.roguelike

private const val BASE = "Assets/sprites/Dungeon"

data class DungeonTheme(
    val floorDir: String,
    val wallDir: String
) {
    companion object {
        val ALL = listOf(
            DungeonTheme("$BASE/floor/brick/mossy",           "$BASE/wall/brick/mossy"),
            DungeonTheme("$BASE/floor/cinder",                "$BASE/wall/cinder"),
            DungeonTheme("$BASE/floor/sandstone",             "$BASE/wall/sandstone/smooth"),
            DungeonTheme("$BASE/floor/labyrinth/undamaged",   "$BASE/wall/labyrinth/style1"),
            DungeonTheme("$BASE/floor/flesh",                 "$BASE/wall/flesh"),
            DungeonTheme("$BASE/floor/metal",                 "$BASE/wall/metal/style1"),
            DungeonTheme("$BASE/floor/cobblestone",           "$BASE/wall/brick/style 1"),
        )
    }
}
