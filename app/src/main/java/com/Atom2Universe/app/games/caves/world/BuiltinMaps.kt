package com.Atom2Universe.app.games.caves.world

/**
 * Cartes Assaut fabriquées par le code, livrées sans fichier ni passage par l'éditeur.
 */
internal object BuiltinMaps {

    const val ARENA_ID = "arena"

    const val ARENA_SIZE = 100
    const val ARENA_WALL_HEIGHT = 5

    /** Un pavé d'obstacle au sol : de (x0, z0) à (x1, z1) inclus, [height] blocs de haut. */
    private class Box(val x0: Int, val z0: Int, val x1: Int, val z1: Int, val height: Int, val block: Short)

    // Obstacles d'un quart de l'arène (coin x < 50, z < 50), recopiés en miroir dans les quatre
    // quarts : aucun camp n'est avantagé. Murets de 2 blocs = couverture debout, de 1 bloc = à moitié.
    private val quarterObstacles = listOf(
        Box(20, 15, 29, 15, 2, STONE),     // muret long
        Box(40, 22, 40, 30, 2, STONE),     // muret en travers
        Box(25, 42, 32, 42, 1, STONE),     // muret bas
        Box(12, 30, 13, 31, 2, PLANK),     // caisse
        Box(35, 35, 36, 36, 3, PLANK),     // pile de caisses
        Box(47, 47, 49, 49, 3, STONE),     // quart du pilier central
    )

    /**
     * Arène d'essai : un sol d'herbe de 100 × 100 entouré d'un mur de pierre de 5 blocs, avec des
     * murets et des caisses disposés symétriquement. Le camp A apparaît dans un coin, le camp B dans
     * le coin opposé.
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
                    else -> obstacleAt(minOf(x, last - x), y, minOf(z, last - z))
                }
            },
            metaAt = { _, _, _ -> 0 },
        )
    }

    /** Bloc d'obstacle en (x, y, z) ramené dans le premier quart, ou de l'air. */
    private fun obstacleAt(x: Int, y: Int, z: Int): Short {
        for (b in quarterObstacles) {
            if (x in b.x0..b.x1 && z in b.z0..b.z1 && y <= b.height) return b.block
        }
        return AIR
    }
}
