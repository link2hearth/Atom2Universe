package com.Atom2Universe.app.games.caves.world

/** Quartier industriel à trois axes, construit sans dépendance au registre graphique. */
internal object BuiltinMaps {
    const val ARENA_ID = "arena"
    const val ARENA_SIZE = 140
    const val ARENA_DEPTH = 120
    const val ARENA_WALL_HEIGHT = 15

    fun arena(): A2Map = District().build()

    private class District {
        private val height = 16
        // Identifiants stables des blocs existants ; aucune dépendance au chargement des textures.
        private val stair: Short = 2406
        private val slab: Short = 2506
        private val blocks = ShortArray(ARENA_SIZE * ARENA_DEPTH * height)
        private val metadata = ByteArray(blocks.size)

        private fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, material: Short, meta: Byte = 0) {
            require(x0 >= -20 && x1 < 120 && y0 >= 0 && y1 < height && z0 >= -10 && z1 < 110)
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) {
                val index = (x + 20) + ARENA_SIZE * ((z + 10) + ARENA_DEPTH * y)
                blocks[index] = material
                metadata[index] = meta
            }
        }

        // Toutes les positions tactiques sont réfléchies est/ouest, matériaux distincts par camp.
        private fun pair(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
                         material: Short, east: Short = material, meta: Byte = 0) {
            box(x0, y0, z0, x1, y1, z1, material, meta)
            val mirrored = when {
                material == stair && meta.toInt() and 1 != 0 -> (meta.toInt() xor 2).toByte()
                material == TORCH && meta.toInt() in 1..2 -> (3 - meta.toInt()).toByte()
                else -> meta
            }
            box(99 - x1, y0, z0, 99 - x0, y1, z1, east, mirrored)
        }

        private fun crate(x: Int, y: Int, z: Int, tall: Boolean = false) {
            val top = y + if (tall) 2 else 1
            pair(x, y, z, x + 2, top, z + 2, PLANK)
            // Cadres sombres : silhouettes de caisses lisibles sans nouveau type de collision.
            for (dx in listOf(0, 2)) for (dz in listOf(0, 2))
                pair(x + dx, y, z + dz, x + dx, top, z + dz, PLANK_DARK)
            pair(x, top, z + 1, x + 2, top, z + 1, PLANK_DARK)
        }

        /** RDC de cinq blocs libres, étage de quatre ; portes de trois blocs et deux escaliers. */
        private fun building(z: Int, warehouse: Boolean) {
            val end = z + 21
            val facade = if (warehouse) BRICK_RED else SANDSTONE
            val other = if (warehouse) BRICK_GREY else BRICK_SANDY
            pair(22, 4, z, 39, 13, end, facade, other)
            pair(23, 4, z + 1, 38, 13, end - 1, AIR)
            pair(22, 9, z, 39, 9, end, PLANK_DARK)
            pair(22, 14, z, 39, 14, end, BRICK_GREY)
            // Bandeaux et soubassement.
            pair(22, 3, z, 39, 3, end, BRICK_GREY)
            for (wall in listOf(22, 39)) {
                pair(wall, 4, z + 9, wall, 6, z + 11, AIR)
                for (wz in listOf(z + 4, z + 15)) {
                    pair(wall, 6, wz, wall, 7, wz + 2, AIR)
                    pair(wall, 11, wz, wall, 12, wz + 2, AIR)
                }
            }
            for (wall in listOf(z, end)) {
                pair(29, 4, wall, 32, 6, wall, AIR)
                pair(29, 11, wall, 32, 12, wall, AIR)
            }
            // Petits bureaux côté arrière, grande salle traversante côté rue.
            pair(28, 4, z + 8, 28, 8, end - 1, facade, other)
            pair(28, 4, z + 10, 28, 6, z + 12, AIR)
            pair(23, 4, z + 15, 27, 8, z + 15, facade, other)
            pair(24, 4, z + 15, 26, 6, z + 15, AIR)
            pair(31, 10, z + 13, 38, 13, z + 13, facade, other)
            pair(33, 10, z + 13, 35, 12, z + 13, AIR)
            // Escalier intérieur de trois blocs de large ; trémie entièrement ouverte.
            pair(24, 9, z + 1, 26, 9, z + 7, AIR)
            for (step in 0..5)
                pair(24, 4, z + 2 + step, 26, 4 + step, z + 2 + step, BRICK_GREY)
            for (step in 0..5)
                pair(24, 4 + step, z + 2 + step, 26, 4 + step, z + 2 + step, stair)
            // Accès extérieur indépendant à une terrasse et à la porte de l'étage.
            for (step in 0..5)
                pair(34, 4, end + 7 - step, 36, 4 + step, end + 7 - step, BRICK_GREY)
            for (step in 0..5)
                pair(34, 4 + step, end + 7 - step, 36, 4 + step, end + 7 - step, stair, meta = 2)
            pair(33, 9, end + 1, 37, 9, end + 1, BRICK_GREY)
            pair(34, 10, end, 36, 12, end, AIR)
            crate(34, 4, z + 3)
            crate(24, 4, z + 18)
            crate(35, 10, z + 17)
            // Longues embrasures sur la façade rue, RDC et étage.
            // Banquette de 1/2 bloc : le canon accroupi dépasse le soubassement.
            for (floor in listOf(4, 10)) {
                pair(39, floor + 1, z + 14, 39, floor + 2, z + 18, AIR)
                pair(39, floor + 2, z + 14, 39, floor + 2, z + 18, slab)
                pair(38, floor, z + 14, 38, floor, z + 18, slab)
            }
            // Auvent mince au-dessus des deux portes de rue.
            pair(40, 7, z + 8, 40, 7, z + 12, slab, meta = 4)
            // Torches murales : salles, bureaux et cage d'escalier, hors des fenêtres.
            pair(23, 8, z + 3, 23, 8, z + 3, TORCH, meta = 1)
            pair(23, 6, z + 13, 23, 6, z + 13, TORCH, meta = 1)
            pair(23, 6, z + 19, 23, 6, z + 19, TORCH, meta = 1)
            pair(38, 7, z + 7, 38, 7, z + 7, TORCH, meta = 2)
            pair(38, 7, z + 12, 38, 7, z + 12, TORCH, meta = 2)
            pair(23, 12, z + 9, 23, 12, z + 9, TORCH, meta = 1)
            pair(38, 12, z + 7, 38, 12, z + 7, TORCH, meta = 2)
            pair(33, 12, end - 1, 33, 12, end - 1, TORCH, meta = 4)
            // Comptoir bas : couverture partielle, circulation préservée.
            pair(30, 4, z + 16, 32, 4, z + 18, PLANK_DARK)
        }

        /** Clockwise perimeter; side stairs own the four corner cells. */
        private fun pitStairRing(left: Int, right: Int, north: Int, south: Int, y: Int) {
            box(left + 1, y, north, right - 1, y, north, stair, 2)
            box(left + 1, y, south, right - 1, y, south, stair, 0)
            box(left, y, north, left, y, south, stair, 3)
            box(right, y, north, right, y, south, stair, 1)
        }
        fun build(): A2Map {
            box(-20, 0, -10, 119, 3, 109, STONE)
            box(-19, 3, -9, 118, 3, 108, BRICK_GREY)
            // Enceinte haute, éloignée des étages pour empêcher de sortir par un saut.
            box(-20, 4, -10, -20, ARENA_WALL_HEIGHT, 109, STONE)
            box(119, 4, -10, 119, ARENA_WALL_HEIGHT, 109, STONE)
            box(-19, 4, -10, 118, ARENA_WALL_HEIGHT, -10, STONE)
            box(-19, 4, 109, 118, ARENA_WALL_HEIGHT, 109, STONE)
            // Voie technique en contrebas : deux marches continues, pas de cul-de-sac.
            box(43, 2, 9, 56, 3, 90, AIR)
            box(42, 3, 9, 57, 3, 90, AIR)
            box(43, 1, 9, 56, 1, 90, SANDSTONE)
            for (z in listOf(9, 90)) box(43, 2, z, 56, 2, z, BRICK_GREY)
            // Pont transversal : passage inférieur de trois blocs libres.
            box(41, 5, 47, 58, 5, 52, BRICK_GREY)
            pair(39, 4, 47, 40, 4, 52, BRICK_GREY)
            pair(41, 4, 47, 42, 4, 52, BRICK_GREY)
            box(44, 6, 47, 48, 6, 47, BRICK_GREY)
            box(51, 6, 52, 55, 6, 52, BRICK_GREY)
            // Piliers hors de l'axe du passage souterrain.
            pair(44, 2, 47, 45, 4, 48, BRICK_GREY)
            pair(44, 2, 51, 45, 4, 52, BRICK_GREY)
            building(17, warehouse = true)
            building(61, warehouse = false)
            // Écrans des cours de départ : sorties nord/sud, aucun tir direct au spawn.
            pair(17, 4, 40, 18, 9, 59, BRICK_GREY)
            pair(5, 3, 42, 12, 3, 57, PLANK_BLUE, PLANK_RED)
            pair(17, 8, 44, 17, 8, 55, PLANK_BLUE, PLANK_RED)
            // Couvertures des trois axes, alternance hauteur de poitrine / couverture complète.
            for (z in listOf(10, 47, 88)) {
                crate(25, 4, z, tall = true)
                crate(35, 4, z + 3)
            }
            for (z in listOf(23, 70)) {
                crate(8, 4, z)
                pair(12, 4, z + 5, 14, 5, z + 9, BRICK_GREY)
            }
            // Chicanes dans la voie basse : restent six blocs libres pour contourner.
            box(44, 2, 29, 48, 4, 31, BRICK_RED)
            box(51, 2, 68, 55, 4, 70, BRICK_RED)
            pair(31, 4, 54, 34, 5, 56, BRICK_GREY)
            // Annexes traversantes des cours de déploiement.
            for (z in listOf(12, 70)) {
                pair(-13, 4, z, 1, 9, z + 17, BRICK_SANDY)
                pair(-12, 4, z + 1, 0, 8, z + 16, AIR)
                pair(-8, 4, z, -5, 6, z, AIR)
                pair(-8, 4, z + 17, -5, 6, z + 17, AIR)
                pair(1, 4, z + 7, 1, 6, z + 10, AIR)
                crate(-10, 4, z + 9)
                pair(-12, 6, z + 5, -12, 6, z + 5, TORCH, meta = 1)
                pair(0, 6, z + 13, 0, 6, z + 13, TORCH, meta = 2)
            }
            for (z in listOf(-4, 101)) {
                pair(12, 4, z, 22, 6, z + 2, BRICK_GREY)
                crate(34, 4, z, tall = true)
            }
            // Mur devant le spawn : jonction aux annexes nord (z=29) et sud (z=70).
            // Les portes et les accès de contournement restent ouverts.
            pair(-5, 4, 30, -3, 6, 69, BRICK_GREY)
            pair(-15, 3, 44, -8, 3, 54, PLANK_BLUE, PLANK_RED)
            // Vrais escaliers pour les accès au pont et à la voie basse.
            pair(39, 4, 47, 39, 4, 52, stair, meta = 1)
            pair(41, 5, 47, 41, 5, 52, stair, meta = 1)
            // Two concentric rings, with identical extents for excavation and steps.
            // The north/south runs meet the side runs at each corner; their perpendicular
            // orientation makes StairConnections resolve an inner corner automatically.
            pitStairRing(42, 57, 8, 91, 3)
            pitStairRing(43, 56, 9, 90, 2)
            // Keep the bridge abutments solid, instead of leaving an air gap below them.
            pair(42, 3, 47, 42, 3, 52, BRICK_GREY)
            // Postes de tir bas à contourner : fente de six blocs, jambages pleins.
            for (z in listOf(28, 75)) {
                pair(12, 4, z, 14, 6, z + 4, AIR)
                pair(13, 4, z, 13, 4, z + 7, BRICK_GREY)
                pair(13, 6, z, 13, 6, z + 7, slab)
                for (end in listOf(z, z + 7))
                    pair(13, 5, end, 13, 6, end, BRICK_GREY)
                pair(12, 4, z + 1, 12, 4, z + 6, slab)
                pair(14, 4, z + 1, 14, 4, z + 6, slab)
            }
            // Fentes dans les chicanes du contournement nord/sud.
            for (z in listOf(-4, 101)) {
                pair(15, 5, z, 19, 6, z + 2, AIR)
                pair(15, 6, z, 19, 6, z + 2, slab)
                pair(15, 4, z - 1, 19, 4, z - 1, slab)
                pair(15, 4, z + 3, 19, 4, z + 3, slab)
            }
            // Balises consommées par capture : elles deviennent de l'air.
            pair(-11, 4, 48, -11, 4, 48, SPAWN_MARKER_A, SPAWN_MARKER_B)
            return A2Map.capture(ARENA_ID, ARENA_SIZE, height, ARENA_DEPTH,
                blockAt = { x, y, z -> blocks[x + ARENA_SIZE * (z + ARENA_DEPTH * y)] },
                metaAt = { x, y, z -> metadata[x + ARENA_SIZE * (z + ARENA_DEPTH * y)] })
        }
    }
}
