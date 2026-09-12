package com.Atom2Universe.app.games.caves.world

/**
 * Cartes Assaut fabriquées par le code, livrées sans fichier ni passage par l'éditeur.
 */
internal object BuiltinMaps {

    const val ARENA_ID = "arena"

    const val ARENA_SIZE = 100
    const val ARENA_WALL_HEIGHT = 5

    /**
     * Arène d'essai : un sol d'herbe de 100 × 100, entouré d'un mur de pierre de 5 blocs de haut.
     * Le camp A apparaît dans un coin, le camp B dans le coin opposé.
     */
    fun arena(): A2Map {
        val last = ARENA_SIZE - 1
        return A2Map.capture(
            "arena", ARENA_SIZE, ARENA_WALL_HEIGHT + 1, ARENA_SIZE,
            blockAt = { x, y, z ->
                when {
                    y == 0 -> GRASS
                    x == 0 || x == last || z == 0 || z == last -> STONE
                    y == 1 && x == 2 && z == 2 -> SPAWN_MARKER_A
                    y == 1 && x == last - 2 && z == last - 2 -> SPAWN_MARKER_B
                    else -> AIR
                }
            },
            metaAt = { _, _, _ -> 0 },
        )
    }
}
