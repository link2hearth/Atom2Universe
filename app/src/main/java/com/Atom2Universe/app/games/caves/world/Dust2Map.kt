package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems

/**
 * Just You, inspirée de Dust 2 : B à l'ouest, A à l'est, départ au sud.
 * Les volumes sont originaux, construits avec les matériaux déjà disponibles dans Cave World.
 * Trois voies, des tunnels à deux niveaux et un passage CT sous la plateforme A.
 * Les coordonnées sont locales ; les hauteurs désignent les pieds, pas les yeux.
 */
internal object Dust2Map {
    const val ID = "dust2"
    const val WIDTH = 128
    const val DEPTH = 144
    const val HEIGHT = 24
    const val GROUND = 5
    const val SPAWN_HEIGHT = 11
    const val TOP_MID_HEIGHT = 9
    const val CATWALK_HEIGHT = 8
    const val SITE_A_HEIGHT = 9
    val PLAYER_SPAWN = MapPoint(62, SPAWN_HEIGHT, 129)

    /** Réservoirs de garnison au sol : une escouade sur chaque site et une au milieu CT. */
    data class Sector(val x0: Int, val z0: Int, val x1: Int, val z1: Int, val feet: Int) {
        fun contains(x: Int, y: Int, z: Int) = x in x0..x1 && z in z0..z1 && y == feet
    }
    val DEFENSE_SECTORS = listOf(
        Sector(10, 17, 36, 40, GROUND),
        Sector(89, 16, 111, 28, SITE_A_HEIGHT),
        Sector(43, 29, 69, 41, GROUND),
    )

    fun create(): A2Map = Builder().build()

    private class Builder {
        private val blocks = ShortArray(WIDTH * DEPTH * HEIGHT)
        private val metadata = ByteArray(blocks.size)
        private val decor = ArrayList<CaveDecor>()
        // Escaliers en briques claires, orientés comme PartialBlockModel (+Z, +X, -Z, -X).
        private val stair: Short = 2406

        private fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
                        material: Short, direction: Int = 0) {
            require(x0 in 0..x1 && x1 < WIDTH && y0 in 0..y1 && y1 < HEIGHT &&
                z0 in 0..z1 && z1 < DEPTH)
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) {
                val index = x + WIDTH * (z + DEPTH * y)
                blocks[index] = material
                metadata[index] = direction.toByte()
            }
        }

        /** Creuse une rue ou une salle, avec un sol porteur même sur les parties surélevées. */
        private fun passage(x0: Int, z0: Int, x1: Int, z1: Int, feet: Int = GROUND,
                            ceiling: Int = HEIGHT, paving: Short = SANDSTONE) {
            box(x0, feet, z0, x1, ceiling - 1, z1, AIR)
            box(x0, 0, z0, x1, feet - 1, z1, STONE)
            box(x0, feet - 1, z0, x1, feet - 1, z1, paving)
        }

        private fun crate(x: Int, y: Int, z: Int, width: Int = 3, height: Int = 3, depth: Int = 3) {
            box(x, y, z, x + width - 1, y + height - 1, z + depth - 1, PLANK)
            for (dx in listOf(0, width - 1)) for (dz in listOf(0, depth - 1))
                box(x + dx, y, z + dz, x + dx, y + height - 1, z + dz, PLANK_DARK)
            box(x, y + height - 1, z, x + width - 1, y + height - 1, z, PLANK_DARK)
            box(x, y + height - 1, z + depth - 1,
                x + width - 1, y + height - 1, z + depth - 1, PLANK_DARK)
        }

        /** Porte ouverte avec linteau en gradins ; le centre reste large de cinq blocs. */
        private fun gateZ(x: Int, z: Int, feet: Int, width: Int) {
            box(x, feet, z, x + width - 1, feet + 7, z + 1, BRICK_SANDY)
            box(x + 2, feet, z, x + width - 3, feet + 4, z + 1, AIR)
            box(x + 3, feet + 5, z, x + width - 4, feet + 5, z + 1, AIR)
            // Battants ouverts, ferrures sombres, décalage créant les angles de couverture.
            box(x + 2, feet, z - 2, x + 2, feet + 3, z + 1, PLANK_DARK)
            box(x + width - 3, feet, z, x + width - 3, feet + 3, z + 3, PLANK_DARK)
            for (y in listOf(feet + 1, feet + 3)) {
                box(x + 2, y, z - 2, x + 2, y, z + 1, BRICK_GREY)
                box(x + width - 3, y, z, x + width - 3, y, z + 3, BRICK_GREY)
            }
        }

        fun build(): A2Map {
            // Îlots pleins : aucune pièce aveugle où une IA pourrait être enfermée.
            box(0, 0, 0, WIDTH - 1, 3, DEPTH - 1, STONE)
            box(0, 4, 0, WIDTH - 1, 15, DEPTH - 1, SANDSTONE)
            for (z in 0 until DEPTH step 12) for (x in 0 until WIDTH step 12) {
                val top = 16 + (x / 12 + z / 12 * 3) % 4
                val right = minOf(x + 11, WIDTH - 1)
                val bottom = minOf(z + 11, DEPTH - 1)
                val facade = if ((x / 12 + z / 12) % 3 == 0) BRICK_SANDY else SANDSTONE
                box(x, 5, z, right, top, bottom, facade)
                box(x, top, z, right, top, bottom, BRICK_SANDY)
            }
            southernApproaches()
            tunnelsAndB()
            middleAndShort()
            longAndA()
            workshopConnector()
            architecture()
            coverAndLandmarks()
            streetLife()
            lighting()
            return A2Map(ID, WIDTH, HEIGHT, DEPTH, blocks, metadata,
                listOf(PLAYER_SPAWN, MapPoint(58, SPAWN_HEIGHT, 130), MapPoint(66, SPAWN_HEIGHT, 130)),
                listOf(MapPoint(23, GROUND, 36), MapPoint(105, SITE_A_HEIGHT, 23),
                    MapPoint(65, GROUND, 34)), decor)
        }

        private fun southernApproaches() {
            passage(43, 118, 83, 135, feet = SPAWN_HEIGHT) // Belvédère de départ.
            passage(14, 112, 50, 124)
            // Six blocs de descente vers la cour des tunnels, avec des paliers de deux blocs.
            passage(45, 112, 50, 124, feet = SPAWN_HEIGHT)
            for (step in 0..5) {
                val x = 33 + step * 2
                val feet = GROUND + 1 + step
                passage(x, 112, x + 1, 124, feet = feet)
                box(x, feet - 1, 112, x, feet - 1, 124, stair, 1)
            }
            passage(10, 86, 30, 115) // Extérieur des tunnels.
            // Le haut du milieu est une terrasse, deux blocs sous le départ.
            for (z in 100..122) {
                val feet = when {
                    z >= 116 -> SPAWN_HEIGHT
                    z >= 113 -> TOP_MID_HEIGHT + 1
                    else -> TOP_MID_HEIGHT
                }
                passage(49, z, 76, z, feet = feet)
                if (z == 113 || z == 116) box(49, feet - 1, z, 76, feet - 1, z, stair, 0)
            }
            // La troisième sortie descend jusqu'aux portes longues, restées au niveau bas.
            for (z in 106..119) {
                val feet = GROUND + (z - 106) / 2
                passage(76, z, 101, z, feet = feet)
                if (z >= 108 && z % 2 == 0) box(76, feet - 1, z, 101, feet - 1, z, stair, 0)
            }
            passage(88, 89, 104, 107, ceiling = 13) // Sas des portes longues.
            passage(104, 89, 120, 99)
            // Écran face au départ : on choisit un côté avant d'exposer le milieu.
            box(54, SPAWN_HEIGHT, 117, 68, SPAWN_HEIGHT + 6, 119, BRICK_SANDY)
        }

        private fun tunnelsAndB() {
            passage(17, 65, 31, 88, feet = 7, ceiling = 13, paving = BRICK_SANDY)
            passage(19, 42, 25, 68, feet = 7, ceiling = 13, paving = BRICK_SANDY)
            passage(31, 72, 49, 80, ceiling = 11, paving = BRICK_SANDY)
            // Entrée sud et escalier vers le tunnel inférieur (deux blocs de dénivelé).
            for (step in 0..1) {
                passage(17, 90 - step, 30, 90 - step, feet = 6 + step)
                box(17, 5 + step, 90 - step, 30, 5 + step, 90 - step, stair, 2)
                passage(31 + step, 72, 31 + step, 80, feet = 7 - step, ceiling = 11)
                box(31 + step, 6 - step, 72, 31 + step, 6 - step, 80, stair, 3)
            }
            passage(8, 15, 39, 43) // Cour B.
            for (step in 0..1) {
                passage(19, 44 + step, 25, 44 + step, feet = 6 + step, ceiling = 13)
                box(19, 5 + step, 44 + step, 25, 5 + step, 44 + step, stair, 0)
            }
            passage(9, 16, 16, 26, feet = 6, paving = BRICK_SANDY) // Fond de B.
            box(9, 5, 27, 16, 5, 27, stair, 2)
            // Porte B vers CT et fenêtre voisine, franchissable depuis les deux côtés.
            passage(39, 34, 48, 40)
            passage(43, 24, 48, 34)
            passage(39, 24, 44, 28, feet = 6, ceiling = 11)
            box(38, 5, 24, 38, 5, 28, stair, 1)
            box(44, 5, 24, 44, 5, 28, stair, 3)
            box(40, 5, 34, 41, 12, 40, BRICK_SANDY)
            box(40, 5, 35, 41, 9, 39, AIR)
            box(38, 5, 35, 40, 8, 35, PLANK_DARK)
            box(41, 5, 39, 43, 8, 39, PLANK_DARK)
            // Piliers du tunnel supérieur, hors du passage vers B et du couloir inférieur.
            box(18, 7, 69, 19, 12, 70, BRICK_SANDY)
            box(28, 7, 83, 29, 12, 84, BRICK_SANDY)
        }

        private fun middleAndShort() {
            // Longue pente centrale : 9 en haut, 5 aux portes, sans marche de plus d'un bloc.
            for (z in 43..109) {
                val feet = GROUND + ((z - 70).coerceAtLeast(0) / 8).coerceAtMost(4)
                passage(49, z, 62, z, feet = feet)
                if (z in listOf(78, 86, 94, 102))
                    box(49, feet - 1, z, 62, feet - 1, z, stair, 0)
            }
            passage(43, 29, 70, 43)
            passage(63, 74, 71, 104, feet = CATWALK_HEIGHT, paving = BRICK_SANDY)
            passage(64, 67, 79, 75, feet = CATWALK_HEIGHT, paving = BRICK_SANDY)
            passage(72, 43, 79, 73, feet = CATWALK_HEIGHT, paving = BRICK_SANDY)
            box(63, TOP_MID_HEIGHT - 1, 105, 71, TOP_MID_HEIGHT - 1, 105, stair, 0)
            // Muret sur le bord du milieu ; la corniche conserve huit blocs utiles.
            box(63, CATWALK_HEIGHT, 76, 63, CATWALK_HEIGHT, 100, BRICK_SANDY)
            // Une ouverture au-dessus de la caisse conserve le raccourci depuis le milieu.
            box(63, CATWALK_HEIGHT, 81, 63, CATWALK_HEIGHT, 83, AIR)
            // Court A domine maintenant le bas du milieu de trois blocs.
            passage(72, 46, 79, 51, feet = SITE_A_HEIGHT, paving = BRICK_SANDY)
            box(72, SITE_A_HEIGHT - 1, 51, 79, SITE_A_HEIGHT - 1, 51, stair, 2)
            passage(72, 38, 94, 45, feet = SITE_A_HEIGHT, paving = BRICK_SANDY)
            gateZ(49, 46, GROUND, 14)
        }

        private fun longAndA() {
            passage(108, 34, 120, 98)
            passage(113, 96, 123, 107, feet = 3, paving = BRICK_SANDY) // Fosse longue.
            // Toute la largeur de la fosse est desservie par les marches côté nord.
            passage(113, 94, 120, 94, feet = 5)
            passage(113, 95, 120, 95, feet = 4)
            box(113, 4, 94, 120, 4, 94, stair, 2)
            box(113, 3, 95, 120, 3, 95, stair, 2)
            passage(89, 14, 112, 41, feet = SITE_A_HEIGHT, paving = BRICK_SANDY)
            passage(108, 14, 120, 35, feet = SITE_A_HEIGHT, paving = BRICK_SANDY)
            // Rampe longue vers A, quatre marches largement espacées.
            for (step in 0..3) {
                val z = 43 - step * 2
                passage(113, z - 1, 120, z, feet = 6 + step, paving = BRICK_SANDY)
                box(113, 5 + step, z, 120, 5 + step, z, stair, 2)
            }
            // Mur séparant la rampe du couloir CT, sans fermer leurs débouchés.
            box(108, 5, 36, 112, 8, 43, BRICK_SANDY)
            // CT traverse sous A : trois blocs libres sous le tablier à y=8.
            passage(70, 30, 98, 36, ceiling = 8, paving = BRICK_GREY)
            // À l'ouest du tablier, la cour CT reste à ciel ouvert.
            box(70, 8, 30, 88, HEIGHT - 1, 36, AIR)
            for (step in 0..3) {
                val x = 99 + step * 2
                passage(x, 30, x + 1, 36, feet = 6 + step, paving = BRICK_SANDY)
                box(x, 5 + step, 30, x, 5 + step, 36, stair, 1)
            }
            passage(107, 30, 112, 36, feet = SITE_A_HEIGHT, paving = BRICK_SANDY)
            gateZ(88, 102, GROUND, 17)
            // Sortie latérale du sas : pas de ligne droite à travers les deux portes.
            box(88, 5, 89, 103, 12, 90, BRICK_SANDY)
            gateZ(104, 91, GROUND, 17)
        }

        /** Liaison de trois blocs de large : milieu → corniche → alcôve de la longue. */
        private fun workshopConnector() {
            // Monte depuis le milieu bas (5) sur la corniche (8), sans saut obligatoire.
            for (step in 0..2) {
                val x = 63 + step
                val feet = GROUND + 1 + step
                passage(x, 69, x, 71, feet = feet, ceiling = 12, paving = BRICK_SANDY)
                box(x, feet - 1, 69, x, feet - 1, 71, stair, 1)
            }
            // Le palier traverse l'îlot à hauteur de la corniche. Quatre blocs de hauteur libre.
            passage(80, 69, 92, 71, feet = CATWALK_HEIGHT, ceiling = 12, paving = BRICK_SANDY)
            // Descente côté atelier : marches orientées vers le haut (-X), paliers de deux blocs.
            for (step in 0..2) {
                val x = 92 + step * 2
                val feet = CATWALK_HEIGHT - step
                box(x, feet - 1, 69, x, feet - 1, 71, stair, 3)
                passage(x + 1, 69, x + 2, 71, feet = feet - 1,
                    ceiling = feet + 3, paving = BRICK_SANDY)
            }
            passage(99, 69, 102, 71, ceiling = 9, paving = BRICK_GREY)
            // L'alcôve (103..107) est ouverte et meublée par streetLife().
        }

        private fun architecture() {
            // Fenêtres bleues en retrait visuel dans des façades pleines, jamais des trous de collision.
            for (z in listOf(49, 61, 73, 85)) {
                box(121, 9, z, 121, 11, z + 2, PLANK_BLUE)
                box(121, 12, z - 1, 121, 12, z + 3, BRICK_SANDY)
            }
            for (x in listOf(12, 22, 32)) {
                box(x, 10, 14, x + 2, 12, 14, PLANK_DARK)
                box(x - 1, 13, 14, x + 3, 13, 14, BRICK_SANDY)
            }
            for (x in listOf(47, 59, 73)) {
                box(x, SPAWN_HEIGHT + 4, 136, x + 2, SPAWN_HEIGHT + 6, 136, PLANK_BLUE)
                box(x - 1, SPAWN_HEIGHT + 3, 136, x + 3, SPAWN_HEIGHT + 3, 136, BRICK_SANDY)
            }
            // Poutres hautes des tunnels ; l'éclairage est posé après tous les percements.
            for (z in listOf(55, 67, 79, 87)) {
                box(17, 12, z, 31, 12, z, PLANK_DARK)
            }
            // Deux petits volumes de toiture donnent une silhouette de village désertique.
            box(82, 17, 18, 86, 20, 22, BRICK_SANDY)
            box(83, 21, 19, 85, 21, 21, SANDSTONE)
            box(4, 16, 47, 9, 20, 52, BRICK_SANDY)
        }

        /** Coordonnées du bloc porteur ; la normale place et oriente la torche sur sa face. */
        private fun wallTorch(wallX: Int, y: Int, wallZ: Int, facing: Byte) {
            val (nx, nz) = TorchModel.normal(facing)
            require(nx != 0 || nz != 0)
            val support = blocks[wallX + WIDTH * (wallZ + DEPTH * y)]
            check(support in listOf(STONE, SANDSTONE, BRICK_SANDY, BRICK_GREY, PLANK_DARK)) {
                "Torch support missing at ($wallX, $y, $wallZ): block=$support"
            }
            val x = wallX + nx
            val z = wallZ + nz
            check(blocks[x + WIDTH * (z + DEPTH * y)] == AIR) {
                "Torch position occupied at ($x, $y, $z)"
            }
            item(x, y, z, TORCH, facing.toInt())
        }

        private fun lighting() {
            // Le tunnel vers B est plus étroit que la salle : ses parois sont à x=18 et x=26.
            wallTorch(18, 10, 55, 1)
            // La cour extérieure commence à z=86 : le mur ouest s'arrête donc à z=85.
            for (z in listOf(67, 79, 85)) wallTorch(16, 10, z, 1)
            wallTorch(37, 9, 71, 3)
            wallTorch(47, 9, 71, 3)
            wallTorch(91, 7, 29, 3)
            // Le nouveau passage est éclairé à son palier haut et au pied de l'escalier.
            wallTorch(84, 10, 68, 3)
            wallTorch(90, 10, 68, 3)
            wallTorch(98, 7, 72, 4)
        }

        private fun coverAndLandmarks() {
            crate(12, 6, 18, height = 3) // Fond B.
            crate(28, 5, 19, width = 5, height = 3)
            crate(29, 5, 30, height = 2)
            crate(10, 5, 37, width = 4, height = 2)
            crate(91, 9, 17, width = 3, height = 3)
            crate(103, 9, 25, width = 4, height = 2)
            crate(91, 9, 35, height = 2)
            crate(59, 6, 81, width = 4, height = 1) // Milieu à 6, caisse à 7, corniche à 8.
            crate(44, 5, 31, height = 2)
            crate(20, 7, 78, height = 2)
            crate(16, 5, 104, height = 2)
            crate(78, SPAWN_HEIGHT, 124, height = 2)
            // Conteneur bleu à la sortie des portes longues.
            box(105, 5, 96, 108, 7, 101, PLANK_BLUE)
            box(105, 8, 96, 108, 8, 101, BRICK_GREY)
            // Le modèle partagé apporte roues, vitres et carrosserie, avec ses collisions.
            prop("outdoor.family_car", 118.7f, 5f, 57f, .30f)
            siteMark(95, 8, 19, true)
            siteMark(20, 4, 23, false)
        }

        /** Pose le bas réel du modèle sur le sol (leurs origines ne sont pas toutes à zéro). */
        private fun prop(id: String, x: Float, feet: Float, z: Float,
                         scale: Float = .12f, turn: Int = 0): CaveDecor {
            val model = CaveDecorModels.get(id)
            return CaveDecor(id, x, feet - model.bounds.bottom * scale, z, scale, turn)
                .also { decor += it }
        }

        private fun item(x: Int, feet: Int, z: Int, id: Short, facing: Int = 0) =
            box(x, feet, z, x, feet, z, id, facing)

        /** Un petit stock de vrais tonneaux et caisses ; empreinte de trois blocs sur deux. */
        private fun supplies(x: Int, feet: Int, z: Int, dark: Boolean = false) {
            val barrel = if (dark) UndergroundSites.BARREL_FIR else UndergroundSites.BARREL_OAK
            val crate = if (dark) UndergroundSites.CRATE_FIR else UndergroundSites.CRATE_OAK
            item(x, feet, z, barrel)
            item(x + 1, feet, z, barrel)
            item(x, feet, z + 1, barrel)
            item(x + 2, feet, z, crate)
            item(x + 2, feet + 1, z, crate)
        }

        /** Petites scènes en bord de voie, en dehors des escaliers et des seuils des portes. */
        private fun streetLife() {
            // Place de départ : terrasse, comptoir sous auvent et marchandises au mur.
            val terrace = SPAWN_HEIGHT.toFloat()
            prop("outdoor.bench", 49f, terrace, 134f, .17f, 2)
            prop("outdoor.planter", 45.5f, terrace, 133.5f, .14f)
            prop("living.dining_table", 75f, terrace, 132f, .13f)
            prop("living.dining_chair", 75f, terrace, 130.3f, .12f)
            prop("living.dining_chair", 75f, terrace, 133.7f, .12f, 2)
            // Le plateau du modèle de table est à 11,1 unités au-dessus de son origine.
            prop("kitchen.fruit_bowl", 75f, terrace + 11.1f * .13f, 132f, .09f)
            box(78, SPAWN_HEIGHT, 134, 81, SPAWN_HEIGHT, 134, PLANK_DARK)
            item(79, SPAWN_HEIGHT + 1, 134, UndergroundSites.CRATE_OAK)
            item(81, SPAWN_HEIGHT + 1, 134, UndergroundSites.BARREL_OAK)
            box(72, SPAWN_HEIGHT + 4, 130, 82, SPAWN_HEIGHT + 4, 135, 2506)
            box(72, SPAWN_HEIGHT, 130, 72, SPAWN_HEIGHT + 3, 130, WOOD)
            box(82, SPAWN_HEIGHT, 130, 82, SPAWN_HEIGHT + 3, 130, WOOD)
            for (x in 72..82 step 2) item(x, SPAWN_HEIGHT + 4, 130, PLANK_BLUE)
            supplies(45, SPAWN_HEIGHT, 120)
            item(49, SPAWN_HEIGHT, 120, FrontierItems.CHEST, 1)

            // Approche et intérieur des tunnels : réserves contre les parois latérales.
            supplies(11, 5, 110, dark = true)
            item(25, 5, 113, FrontierItems.CHEST)
            prop("garage.crate", 12.5f, 5f, 96f, .16f)
            supplies(27, 7, 67)
            item(18, 7, 85, FrontierItems.CHEST, 3)
            item(18, 7, 86, UndergroundSites.BARREL_FIR)
            item(37, 5, 79, UndergroundSites.CRATE_FIR)
            item(38, 5, 79, FrontierItems.CHEST)

            // Cour B : stocks de bois et coffres orientés vers la cour.
            supplies(34, 5, 17)
            item(34, 5, 20, FrontierItems.CHEST, 1)
            item(10, 6, 23, FrontierItems.CHEST, 3)
            item(10, 6, 25, UndergroundSites.BARREL_FIR)
            supplies(33, 5, 41, dark = true)
            prop("garage.storage_rack", 10f, 5f, 31f, .13f, 1)
            prop("garage.crate", 14f, 5f, 32f, .14f, 1)

            // Côté défense et corniche : ponctuation légère, sans encombrer les marches.
            supplies(67, 5, 40, dark = true)
            item(67, 5, 30, FrontierItems.CHEST, 1)
            prop("outdoor.bench", 62f, 5f, 30.2f, .15f)
            item(70, CATWALK_HEIGHT, 91, UndergroundSites.BARREL_OAK)
            item(78, CATWALK_HEIGHT, 56, UndergroundSites.CRATE_OAK)
            prop("outdoor.planter", 78f, CATWALK_HEIGHT.toFloat(), 63f, .10f)

            // A : réserves en périphérie ; marquage et accès CT restent dégagés.
            supplies(108, 9, 15)
            item(112, 9, 16, FrontierItems.CHEST, 1)
            prop("outdoor.planter", 116.5f, 9f, 16f, .13f)
            prop("outdoor.bench", 118.7f, 9f, 23f, .15f, 3)
            item(90, 9, 28, UndergroundSites.BARREL_FIR)
            item(91, 9, 28, FrontierItems.CHEST)

            // Atelier ouvert sur longue A, dans une niche creusée à côté de la voie.
            passage(103, 64, 107, 74, ceiling = 10, paving = BRICK_GREY)
            val bench = prop("garage.workbench", 104.5f, 5f, 67f, .13f, 1)
            prop("garage.toolbox", 104.5f, bench.y + 10f * bench.scale, 67f, .08f, 1)
            prop("garage.tool_chest", 105f, 5f, 73.5f, .14f, 1)
            prop("garage.tires", 106.5f, 5f, 65f, .14f)
            box(108, 9, 64, 109, 9, 74, 2506)
            for (z in listOf(64, 74)) box(108, 5, z, 108, 8, z, WOOD_DARK)
            prop("garage.tires", 119.3f, 5f, 62.5f, .14f)
            prop("garage.cone", 117f, 5f, 61f, .13f)
            supplies(118, 5, 78, dark = true)
            item(119, 3, 105, UndergroundSites.BARREL_OAK)
            item(121, 3, 105, FrontierItems.CHEST)

            // Abords du sas long : un banc et des marchandises dans la cour extérieure.
            // Les objets sont sur le palier inférieur, jamais à cheval sur deux marches.
            prop("outdoor.bench", 96f, 5f, 106.8f, .16f, 2)
            prop("outdoor.planter", 100f, 5f, 106.8f, .12f)
            supplies(89, 5, 106)
            item(93, 5, 106, FrontierItems.CHEST, 1)
        }

        /** Pictogrammes tactiques A/B en mosaïque, affleurants : aucune collision ajoutée. */
        private fun siteMark(x: Int, y: Int, z: Int, a: Boolean) {
            val glyph = if (a) intArrayOf(14, 17, 31, 17, 17) else intArrayOf(30, 17, 30, 17, 30)
            box(x - 2, y, z - 2, x + 6, y, z + 6, BRICK_TERRACOTTA)
            box(x - 1, y, z - 1, x + 5, y, z + 5, SANDSTONE)
            for (row in glyph.indices) for (col in 0..4)
                if (glyph[row] and (1 shl (4 - col)) != 0)
                    box(x + col, y, z + row, x + col, y, z + row, BRICK_TERRACOTTA)
        }
    }
}
