package com.Atom2Universe.app.games.caves.world

/** Tour fermée : cinq plateaux de bureaux, douze escaliers continus et éclairage mural. */
internal object OfficeTowerMap {
    const val ID = "office_tower"
    const val DUSK_MS = 1_240_000L
    const val WIDTH = 140
    const val DEPTH = 120
    const val HEIGHT = 42
    const val FLOORS = 5
    const val ROOF_Y = 32
    const val PLAYABLE_LEVELS = FLOORS + 1
    private const val STAIR: Short = 2406

    fun create(): A2Map = Builder().build()

    private class Builder {
        private val blocks = ShortArray(WIDTH * DEPTH * HEIGHT)
        private val meta = ByteArray(blocks.size)

        private fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
                        block: Short, orientation: Byte = 0) {
            require(x0 in 0..x1 && x1 < WIDTH && y0 in 0..y1 && y1 < HEIGHT &&
                z0 in 0..z1 && z1 < DEPTH)
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) {
                val i = x + WIDTH * (z + DEPTH * y)
                blocks[i] = block
                meta[i] = orientation
            }
        }

        private fun torch(x: Int, y: Int, z: Int, orientation: Byte) =
            box(x, y, z, x, y, z, TORCH, orientation)

        fun build(): A2Map {
            box(0, 0, 0, WIDTH - 1, 2, DEPTH - 1, BRICK_GREY)
            // Façades closes et fenêtres vitrées : pas de sortie vers le vide.
            box(0, 3, 0, 0, 32, 119, BRICK_GREY)
            box(139, 3, 0, 139, 32, 119, BRICK_GREY)
            box(1, 3, 0, 138, 32, 0, BRICK_GREY)
            box(1, 3, 119, 138, 32, 119, BRICK_GREY)
            for (level in 0 until FLOORS) floor(level)
            roof()

            // Réserver toute la cage, paliers et accès compris, après le mobilier et les murs.
            // Volées suspendues : un remplissage plein sous la volée supérieure bloquerait
            // la tête du joueur en haut de la volée inférieure.
            for (x in listOf(8, 56, 78, 124)) for (z in listOf(8, 47, 100)) {
                box(x - 1, 3, z - 1, x + 4, ROOF_Y + 3, z + 9, AIR)
                for (level in 0 until PLAYABLE_LEVELS) {
                    val base = 2 + level * 6
                    box(x - 1, base, z - 1, x + 4, base, z + 9, BRICK_GREY)
                    if (level > 0) box(x, base, z + 1, x + 3, base, z + 6, AIR)
                }
                for (floor in 0 until FLOORS) {
                    val base = 2 + floor * 6
                    for (step in 0..5) {
                        box(x, base + 1 + step, z + 1 + step, x + 3, base + 1 + step, z + 1 + step, STAIR)
                    }
                }
                // Rambardes latérales des trémies, les deux paliers restent ouverts.
                box(x - 1, ROOF_Y + 1, z + 1, x - 1, ROOF_Y + 1, z + 6, GLASS)
                box(x + 4, ROOF_Y + 1, z + 1, x + 4, ROOF_Y + 1, z + 6, GLASS)
            }
            // Départ dans le hall, derrière le comptoir ; adversaires dans l'aile opposée.
            box(69, 3, 9, 69, 3, 9, SPAWN_MARKER_A)
            box(118, 27, 112, 118, 27, 112, SPAWN_MARKER_B)
            return A2Map.capture(ID, WIDTH, HEIGHT, DEPTH,
                blockAt = { x, y, z -> blocks[x + WIDTH * (z + DEPTH * y)] },
                metaAt = { x, y, z -> meta[x + WIDTH * (z + DEPTH * y)] })
        }

        private fun roof() {
            // Terrasse à ciel ouvert. Aucun équipement près du parapet ne sert de marchepied.
            box(0, ROOF_Y, 0, 139, ROOF_Y, 119, BRICK_GREY)
            box(0, ROOF_Y + 1, 0, 0, ROOF_Y + 2, 119, BRICK_OBSIDIAN)
            box(139, ROOF_Y + 1, 0, 139, ROOF_Y + 2, 119, BRICK_OBSIDIAN)
            box(1, ROOF_Y + 1, 0, 138, ROOF_Y + 2, 0, BRICK_OBSIDIAN)
            box(1, ROOF_Y + 1, 119, 138, ROOF_Y + 2, 119, BRICK_OBSIDIAN)
            for (x in listOf(30, 104)) for (z in listOf(28, 84)) {
                // Antennes sur socle bas, avec deux traverses et une balise lumineuse.
                box(x - 1, 33, z - 1, x + 1, 33, z + 1, BRICK_GREY)
                box(x, 34, z, x, 39, z, BRICK_OBSIDIAN)
                box(x - 2, 37, z, x + 2, 37, z, BRICK_GREY)
                box(x, 38, z - 1, x, 38, z + 1, BRICK_GREY)
                torch(x, 40, z, 0)
            }
            // Deux petits groupes de ventilation, le reste de la terrasse demeure dégagé.
            for (x in listOf(40, 92)) {
                box(x, 33, 58, x + 5, 34, 61, BRICK_GREY)
                box(x + 1, 35, 59, x + 4, 35, 60, BRICK_OBSIDIAN)
            }
        }

        private fun floor(level: Int) {
            val base = 2 + level * 6
            val feet = base + 1
            val accent = listOf(PLANK_BLUE, PLANK_RED, PLANK_JUNGLE, PLANK_PURPLE, PLANK_DARK)[level]
            box(1, base, 1, 138, base, 118, PLANK)
            box(0, base + 6, 0, 139, base + 6, 119, BRICK_GREY)
            // Couloirs en croix, dix blocs libres ; portes larges de quatre blocs.
            for (x in listOf(64, 75)) {
                box(x, feet, 1, x, base + 5, 118, BRICK_SANDY)
                box(x, feet, 55, x, base + 5, 64, AIR)
                for (z in listOf(18, 42, 74, 98)) {
                    box(x, feet, z, x, feet + 2, z + 3, AIR)
                    torch(if (x == 64) x + 1 else x - 1, feet + 3, z + 5, if (x == 64) 1 else 2)
                }
                box(x, base + 4, 1, x, base + 4, 53, accent)
                box(x, base + 4, 66, x, base + 4, 118, accent)
            }
            for (z in listOf(54, 65)) {
                for (range in listOf(1..63, 76..138)) {
                    box(range.first, feet, z, range.last, base + 5, z, BRICK_SANDY)
                    for (x in listOf(range.first + 17, range.first + 43)) {
                        box(x, feet, z, x + 3, feet + 2, z, AIR)
                        torch(x + 5, feet + 3, if (z == 54) z + 1 else z - 1, if (z == 54) 3 else 4)
                    }
                }
            }
            // Huit grandes salles par étage, traversantes vers les escaliers et le hall.
            for (range in listOf(1..63, 76..138)) for (z in listOf(30, 89)) {
                box(range.first, feet, z, range.last, base + 5, z, BRICK_SANDY)
                for (x in listOf(range.first + 4, range.first + 30, range.last - 7))
                    box(x, feet, z, x + 3, feet + 2, z, AIR)
                for (x in range.first + 12..range.last - 10 step 12) {
                    torch(x, feet + 3, z - 1, 4)
                    torch(x, feet + 3, z + 1, 3)
                }
            }
            // Baies panoramiques, piliers conservés entre les vitrages.
            for (x in 5..130 step 12) for (z in listOf(0, 119))
                box(x, feet + 1, z, x + 6, feet + 3, z, GLASS)
            for (z in 5..108 step 12) for (x in listOf(0, 139))
                box(x, feet + 1, z, x, feet + 3, z + 6, GLASS)
            for (x in 6..132 step 12) {
                torch(x, feet + 3, 1, 3)
                torch(x, feet + 3, 118, 4)
            }
            for (z in 6..112 step 12) {
                torch(1, feet + 3, z, 1)
                torch(138, feet + 3, z, 2)
            }
            // Bureaux bas, écrans et cloisons : couvertures avec passages entre les îlots.
            for (x in listOf(22, 42, 84, 104)) for (z in listOf(12, 39, 73, 102)) {
                box(x, feet, z, x + 7, feet, z + 2, PLANK_DARK)
                box(x + 2, feet + 1, z + 1, x + 3, feet + 1, z + 1, BRICK_OBSIDIAN)
                box(x, feet, z + 6, x + 7, feet + 1, z + 6, accent)
                box(x + 10, feet, z, x + 11, feet + 2, z + 2, BRICK_GREY)
                torch(x + 10, feet + 3, z + 1, 0)
            }
            // Comptoirs décalés : brisent les longues lignes de tir sans fermer le couloir.
            for (z in listOf(15, 46, 78, 108))
                box(if (z % 2 == 0) 70 else 65, feet, z,
                    if (z % 2 == 0) 74 else 69, feet + 1, z + 1, accent)
        }
    }
}
