package com.Atom2Universe.app.games.caves.world

/**
 * La Tour du crépuscule : un niveau à parcourir, du sas d'entrée jusqu'à l'héliport du toit.
 *
 * Chaque étage n'a qu'**un seul escalier qui monte**, et il est toujours à l'opposé de celui par
 * lequel on arrive : on ne peut pas grimper la tour d'une traite, il faut traverser chaque étage.
 * Autour du chemin, des pièces sans issue (on y entre, on doit en ressortir) et, par endroits, une
 * boucle qui permet de prendre des défenseurs à revers. Les cages d'escalier qui montent sont en
 * briques bleues sur tous les étages : c'est le repère visuel du chemin.
 *
 * Le parcours, étage par étage (x vers l'est, z vers le sud) :
 * - **Rez-de-chaussée** : sas au sud-ouest → grand hall (l'escalier d'honneur est effondré) → poste
 *   de sécurité ou restaurant d'entreprise → quai de livraison → escalier au nord-est.
 * - **1er, l'open-space** : arrivée au nord-est → plateau de bureaux, salle de réunion traversante
 *   → archives au sud-ouest.
 * - **2e, les serveurs** : arrivée au sud-ouest → couloir technique → salle des baies, un
 *   labyrinthe d'allées dont la moitié sont des impasses → couloir est → escalier au sud-est.
 * - **3e, la direction** : arrivée au sud-est → couloir sud barricadé (meurtrières) → bureau
 *   traversant, couloir nord coupé par une porte coupe-feu, salle du conseil → escalier au nord-ouest.
 *   Un second bureau traversant permet de prendre la barricade à revers.
 * - **4e, le chantier** : arrivée au nord-ouest → plateau en travaux, murs inachevés, nid de tireurs
 *   sur un échafaudage → escalier au sud-est.
 * - **Toit** : sortie au sud-est → salle des machines (on peut monter dessus) → héliport au nord-ouest.
 */
internal object OfficeTowerMap {
    const val ID = "office_tower"
    const val DUSK_MS = 1_240_000L
    const val WIDTH = 96
    const val DEPTH = 80
    const val HEIGHT = 42
    const val FLOORS = 5
    const val ROOF_Y = 32
    const val PLAYABLE_LEVELS = FLOORS + 1
    private const val STAIR: Short = 2406

    /** Hauteur des pieds sur le plancher du niveau [level] (0 = rez-de-chaussée, 5 = toit). */
    fun feet(level: Int) = 3 + level * 6

    /** Poste d'une escouade : son chef démarre en (x, y, z) — y = hauteur des pieds —, les autres autour. */
    data class Post(val x: Int, val y: Int, val z: Int, val size: Int)

    /**
     * La garnison, posée à la main le long du parcours : les points de passage obligés (haut des
     * escaliers, barricade, héliport), quelques pièces sans issue, et rien dans le sas de départ.
     */
    val POSTS: List<Post> = listOf(
        // Rez-de-chaussée
        Post(23, feet(0), 45, 3),   // derrière l'accueil du hall
        Post(30, feet(0), 7, 2),    // PC sécurité (impasse)
        Post(72, feet(0), 18, 3),   // quai, entre les caisses
        Post(66, feet(0), 62, 2),   // restaurant d'entreprise
        Post(86, feet(0), 24, 2),   // pied de l'escalier de service
        // 1er : open-space
        Post(74, feet(1), 34, 3),   // plateau est
        Post(46, feet(1), 8, 2),    // bureau de direction (impasse vitrée)
        Post(48, feet(1), 37, 2),   // salle de réunion
        Post(11, feet(1), 73, 2),   // archives, au pied de l'escalier
        // 2e : serveurs
        Post(47, feet(2), 23, 3),   // croisement central des baies
        Post(66, feet(2), 3, 2),    // allée nord
        Post(90, feet(2), 15, 2),   // salle de supervision (impasse)
        Post(82, feet(2), 58, 2),   // couloir est, avant l'escalier
        // 3e : direction
        Post(37, feet(3), 46, 3),   // derrière la barricade
        Post(45, feet(3), 17, 2),   // salle du conseil
        Post(80, feet(3), 10, 2),   // bureau du PDG (impasse)
        Post(18, feet(3), 14, 2),   // palier de l'escalier nord-ouest
        // 4e : chantier
        Post(33, feet(4) + 3, 60, 2), // nid de tireurs sur l'échafaudage
        Post(20, feet(4), 56, 3),   // plateau ouest
        Post(78, feet(4), 40, 2),   // plateau est
        Post(73, feet(4), 72, 2),   // avant la sortie sur le toit
        // Toit
        Post(49, feet(5) + 5, 38, 2), // sur la salle des machines
        Post(66, feet(5), 44, 2),   // entre les groupes de ventilation
        Post(18, feet(5) + 1, 18, 4), // l'héliport, dernier carré
    )

    fun create(): A2Map = Builder().build()

    private class Builder {
        private val blocks = ShortArray(WIDTH * DEPTH * HEIGHT)
        private val meta = ByteArray(blocks.size)

        private fun inside(x: Int, y: Int, z: Int) =
            x in 0 until WIDTH && y in 0 until HEIGHT && z in 0 until DEPTH

        private fun set(x: Int, y: Int, z: Int, block: Short, orientation: Int = 0) {
            require(inside(x, y, z)) { "Hors de la tour : $x, $y, $z" }
            val i = x + WIDTH * (z + DEPTH * y)
            blocks[i] = block
            meta[i] = orientation.toByte()
        }

        private fun get(x: Int, y: Int, z: Int): Short =
            if (inside(x, y, z)) blocks[x + WIDTH * (z + DEPTH * y)] else AIR

        /** Pavé plein ; les coins peuvent être donnés dans n'importe quel ordre. */
        private fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
                        block: Short, orientation: Int = 0) {
            for (y in minOf(y0, y1)..maxOf(y0, y1))
                for (z in minOf(z0, z1)..maxOf(z0, z1))
                    for (x in minOf(x0, x1)..maxOf(x0, x1)) set(x, y, z, block, orientation)
        }

        // ── Vocabulaire d'un étage : tout se mesure depuis le plancher du niveau ──

        /** Cloison pleine, du plancher au plafond. */
        private fun wall(level: Int, x0: Int, z0: Int, x1: Int, z1: Int, block: Short = BRICK_SANDY) =
            box(x0, feet(level), z0, x1, feet(level) + 4, z1, block)

        /** Porte de trois blocs de haut percée dans une cloison. */
        private fun door(level: Int, x0: Int, z0: Int, x1: Int, z1: Int) =
            box(x0, feet(level), z0, x1, feet(level) + 2, z1, AIR)

        /** Ouverture sur toute la hauteur. */
        private fun gap(level: Int, x0: Int, z0: Int, x1: Int, z1: Int) =
            box(x0, feet(level), z0, x1, feet(level) + 4, z1, AIR)

        /** Meuble, muret ou pile de matériaux posés au sol, de [height] blocs. */
        private fun low(level: Int, x0: Int, z0: Int, x1: Int, z1: Int, block: Short, height: Int = 1) =
            box(x0, feet(level), z0, x1, feet(level) + height - 1, z1, block)

        /** Vitrage à hauteur d'yeux dans une cloison déjà posée. */
        private fun glass(level: Int, x0: Int, z0: Int, x1: Int, z1: Int) =
            box(x0, feet(level) + 1, z0, x1, feet(level) + 3, z1, GLASS)

        /** Pilier carré de deux blocs, du sol au plafond. */
        private fun pillar(level: Int, x: Int, z: Int) = box(x, feet(level), z, x + 1, feet(level) + 4, z + 1, BRICK_GREY)

        /** Jardinière : un bac et son feuillage, deux blocs de haut, couverture complète. */
        private fun planter(level: Int, x: Int, z: Int) {
            low(level, x, z, x + 1, z + 1, PLANK_DARK)
            box(x, feet(level) + 1, z, x + 1, feet(level) + 1, z + 1, LEAVES)
        }

        /**
         * Éclaire une pièce : une applique tous les [spacing] blocs le long de ses murs, à hauteur
         * de plafond, là où un mur plein est là pour la porter. Une pièce peu éclairée se lit
         * comme un endroit où l'on n'est pas censé aller.
         */
        private fun lights(level: Int, x0: Int, z0: Int, x1: Int, z1: Int, spacing: Int = 8) {
            val y = feet(level) + 3
            fun mount(x: Int, z: Int, wx: Int, wz: Int, orientation: Int) {
                if (get(x, y, z) == AIR && get(wx, y, wz) != AIR && get(wx, y, wz) != GLASS)
                    set(x, y, z, TORCH, orientation)
            }
            for (x in x0 + spacing / 2..x1 step spacing) {
                mount(x, z0, x, z0 - 1, 3)
                mount(x, z1, x, z1 + 1, 4)
            }
            for (z in z0 + spacing / 2..z1 step spacing) {
                mount(x0, z, x0 - 1, z, 1)
                mount(x1, z, x1 + 1, z, 2)
            }
        }

        /**
         * Volée droite de six marches, du niveau [level] au suivant, dans sa propre cage.
         *
         * (ox, oz) est le coin de la case d'approche, [dir] le sens de la montée (0 +Z, 1 +X,
         * 2 −Z, 3 −X, comme l'orientation des marches). La volée fait quatre de large. En bas, la
         * cage s'ouvre du côté de l'approche ; en haut, elle entoure la trémie et débouche une case
         * après la dernière marche. Le dessous des marches est plein : aucune autre volée n'est
         * empilée au-dessus, donc rien ne vient cogner la tête.
         */
        private fun flight(level: Int, ox: Int, oz: Int, dir: Int, cage: Short = BRICK_COBALT) {
            val b = 2 + level * 6
            fun at(u: Int, v: Int, y: Int, block: Short, orientation: Int = 0) {
                val x = when (dir) { 1 -> ox + u; 3 -> ox - u; else -> ox + v }
                val z = when (dir) { 0 -> oz + u; 2 -> oz - u; else -> oz + v }
                // La façade sert de mur de cage quand la volée la longe : on n'y touche pas.
                if (x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1) return
                set(x, y, z, block, orientation)
            }
            // Vider la cage des deux étages (meubles, appliques, cloisons de passage).
            for (u in 0..7) for (v in 0..3) for (y in b + 1..b + 5) at(u, v, y, AIR)
            for (u in 0..6) for (v in 0..3) for (y in b + 7..b + 11) at(u, v, y, AIR)
            // Trémie dans le plancher du dessus.
            for (u in 1..5) for (v in 0..3) at(u, v, b + 6, AIR)
            for (s in 0..5) for (v in 0..3) {
                for (y in b + 1 until b + 1 + s) at(1 + s, v, y, BRICK_GREY)
                at(1 + s, v, b + 1 + s, STAIR, dir)
            }
            for (y in b + 1..b + 5) {
                for (u in 0..7) { at(u, -1, y, cage); at(u, 4, y, cage) }
                for (v in -1..4) at(7, v, y, cage)
            }
            for (y in b + 7..b + 11) {
                for (u in 0..6) { at(u, -1, y, cage); at(u, 4, y, cage) }
                for (v in -1..4) at(0, v, y, cage)
            }
            // Une applique en bas, une en haut, contre le mur de gauche.
            val side = if (dir == 0 || dir == 2) 1 else 3
            at(0, 0, b + 4, TORCH, side)
            at(6, 0, b + 10, TORCH, side)
        }

        fun build(): A2Map {
            box(0, 0, 0, WIDTH - 1, 2, DEPTH - 1, BRICK_GREY)
            val floors = listOf(QUARTZ, PLANK, STONE, PLANK_DARK, GREYSTONE)
            for (level in 0 until FLOORS) {
                val base = 2 + level * 6
                box(1, base, 1, WIDTH - 2, base, DEPTH - 2, floors[level])
                box(0, base + 6, 0, WIDTH - 1, base + 6, DEPTH - 1, BRICK_GREY)
            }
            facade()
            groundFloor()
            openSpace()
            serverFloor()
            executiveFloor()
            buildingSite()
            roof()

            // Les escaliers en dernier : ils creusent leurs trémies et posent leurs cages
            // par-dessus les cloisons des étages.
            flight(0, 91, 16, 2)   // quai → open-space (nord-est)
            flight(1, 1, 72, 2)    // archives → serveurs (sud-ouest)
            flight(2, 91, 76, 2)   // couloir est → direction (sud-est)
            flight(3, 12, 1, 3)    // palier nord-ouest → chantier
            flight(4, 80, 75, 1)   // chantier → toit (sud-est)
            roofHouse()

            // Départ dans le sas, face au hall ; balise adverse sur l'héliport.
            set(12, feet(0), 75, SPAWN_MARKER_A)
            set(18, feet(5) + 1, 22, SPAWN_MARKER_B)
            return A2Map.capture(ID, WIDTH, HEIGHT, DEPTH,
                blockAt = { x, y, z -> blocks[x + WIDTH * (z + DEPTH * y)] },
                metaAt = { x, y, z -> meta[x + WIDTH * (z + DEPTH * y)] })
        }

        /** Façades closes et bandeaux vitrés à chaque étage : la ville au crépuscule, sans sortie. */
        private fun facade() {
            box(0, 3, 0, 0, ROOF_Y, DEPTH - 1, BRICK_GREY)
            box(WIDTH - 1, 3, 0, WIDTH - 1, ROOF_Y, DEPTH - 1, BRICK_GREY)
            box(1, 3, 0, WIDTH - 2, ROOF_Y, 0, BRICK_GREY)
            box(1, 3, DEPTH - 1, WIDTH - 2, ROOF_Y, DEPTH - 1, BRICK_GREY)
            for (level in 0 until FLOORS) {
                val y = feet(level)
                for (x in 4 until WIDTH - 8 step 9) {
                    box(x, y + 1, 0, x + 4, y + 3, 0, GLASS)
                    box(x, y + 1, DEPTH - 1, x + 4, y + 3, DEPTH - 1, GLASS)
                }
                for (z in 4 until DEPTH - 8 step 9) {
                    box(0, y + 1, z, 0, y + 3, z + 4, GLASS)
                    box(WIDTH - 1, y + 1, z, WIDTH - 1, y + 3, z + 4, GLASS)
                }
            }
        }

        // ── Rez-de-chaussée ─────────────────────────────────────────────────────

        private fun groundFloor() {
            val l = 0
            // Grandes cloisons : hall au sud-ouest, locaux au nord, quai au nord-est, restaurant au sud-est.
            wall(l, 1, 69, 46, 69)
            wall(l, 23, 70, 23, 78)
            wall(l, 1, 29, 61, 29)
            wall(l, 21, 1, 21, 28)
            wall(l, 61, 1, 61, 28)
            wall(l, 47, 30, 47, 78)
            wall(l, 47, 42, 94, 42)

            // Sas d'entrée : portes vitrées sur la rue, portiques de contrôle, grande baie vers le hall.
            box(4, feet(l), DEPTH - 1, 19, feet(l) + 3, DEPTH - 1, GLASS)
            gap(l, 9, 69, 14, 69)
            low(l, 3, 72, 20, 72, BRICK_OBSIDIAN)
            for (x in listOf(6, 11, 16)) door(l, x, 72, x + 1, 72)
            lights(l, 1, 70, 22, 78)

            // Sanitaires : impasse derrière le sas.
            door(l, 36, 69, 38, 69)
            low(l, 25, 71, 34, 71, QUARTZ)
            for (x in listOf(28, 32, 36, 40, 44)) low(l, x, 75, x, 78, BRICK_SANDY, 3)
            lights(l, 24, 70, 46, 78, 12)

            // Grand hall : piliers, accueil, jardinières, ascenseurs en panne.
            for (x in listOf(9, 22, 35)) for (z in listOf(37, 60)) pillar(l, x, z)
            low(l, 18, 49, 28, 49, PLANK_DARK)
            low(l, 18, 50, 18, 52, PLANK_DARK)
            low(l, 28, 50, 28, 52, PLANK_DARK)
            box(22, feet(l) + 1, 49, 24, feet(l) + 1, 49, BRICK_OBSIDIAN)
            for ((x, z) in listOf(14 to 64, 30 to 64, 41 to 44, 4 to 32, 42 to 32)) planter(l, x, z)
            for (z in listOf(40, 46, 52)) box(1, feet(l), z, 1, feet(l) + 2, z + 2, BRICK_OBSIDIAN)
            // L'escalier d'honneur s'est effondré : quatre marches, puis les gravats jusqu'au plafond.
            for (s in 0..3) for (x in 12..15) {
                for (y in feet(l) until feet(l) + s) set(x, y, 43 - s, BRICK_GREY)
                set(x, feet(l) + s, 43 - s, STAIR, 2)
            }
            low(l, 11, 40, 11, 44, BRICK_GREY)
            low(l, 16, 40, 16, 44, BRICK_GREY)
            box(11, feet(l), 33, 16, feet(l) + 4, 39, COBBLESTONE)
            box(11, feet(l), 36, 16, feet(l) + 1, 40, GRAVEL)
            box(12, feet(l) + 3, 41, 13, feet(l) + 3, 41, COBBLESTONE)
            lights(l, 1, 30, 46, 68, 9)

            // Local technique : impasse sombre, deux appliques seulement.
            door(l, 8, 29, 10, 29)
            for ((x, z) in listOf(4 to 4, 12 to 4, 4 to 14, 12 to 14))
                low(l, x, z, x + 3, z + 3, BRICK_OBSIDIAN, 2)
            low(l, 3, 22, 18, 22, BRICK_GREY)
            lights(l, 1, 1, 20, 28, 16)

            // Sécurité : portiques au sud, PC vitré au nord-ouest, couloir vers le quai au nord-est.
            door(l, 34, 29, 37, 29)
            wall(l, 22, 14, 48, 14)
            glass(l, 24, 14, 38, 14)
            wall(l, 41, 1, 41, 13)
            door(l, 41, 9, 41, 11)
            glass(l, 41, 2, 41, 6)
            low(l, 24, 21, 46, 21, BRICK_OBSIDIAN)
            for (x in listOf(28, 34, 40)) door(l, x, 21, x + 1, 21)
            low(l, 25, 4, 36, 5, PLANK_DARK)
            box(26, feet(l) + 1, 4, 35, feet(l) + 1, 4, BRICK_OBSIDIAN)
            low(l, 52, 16, 58, 17, PLANK_DARK)
            door(l, 61, 4, 61, 7)
            lights(l, 22, 15, 60, 28)
            lights(l, 22, 1, 40, 13)
            lights(l, 42, 1, 60, 13)

            // Quai de livraison : caisses et palettes, rideaux métalliques sur la façade est.
            for ((x, z) in listOf(70 to 16, 82 to 30)) pillar(l, x, z)
            for (c in listOf(
                intArrayOf(66, 6, 2, 2, 2), intArrayOf(70, 10, 3, 2, 1), intArrayOf(75, 4, 2, 3, 2),
                intArrayOf(65, 21, 2, 2, 1), intArrayOf(73, 24, 4, 2, 2), intArrayOf(80, 12, 2, 2, 2),
                intArrayOf(86, 34, 3, 3, 2), intArrayOf(55, 33, 2, 2, 2), intArrayOf(51, 38, 3, 1, 1),
                intArrayOf(67, 34, 2, 2, 1), intArrayOf(76, 36, 2, 2, 2), intArrayOf(84, 22, 2, 2, 1),
            )) low(l, c[0], c[1], c[0] + c[2] - 1, c[1] + c[3] - 1, if (c[4] == 2) WOOD else PLANK, c[4])
            for (z in listOf(20, 30)) box(WIDTH - 2, feet(l), z, WIDTH - 2, feet(l) + 3, z + 5, BRICK_OBSIDIAN)
            lights(l, 62, 1, 94, 41)
            lights(l, 48, 30, 60, 41)

            // Restaurant d'entreprise : tables, comptoir, cuisine en impasse.
            door(l, 64, 42, 67, 42)
            door(l, 47, 52, 47, 55)
            wall(l, 79, 61, 79, 78)
            wall(l, 79, 61, 94, 61)
            door(l, 85, 61, 87, 61)
            for (x in 52..72 step 7) for (z in listOf(46, 54, 66, 73))
                low(l, x, z, x + 2, z + 1, PLANK_RED)
            low(l, 60, 59, 76, 59, QUARTZ)
            low(l, 81, 64, 81, 76, QUARTZ)
            low(l, 84, 70, 92, 71, QUARTZ)
            low(l, 92, 63, 93, 66, FURNACE)
            lights(l, 48, 43, 78, 78)
            lights(l, 79, 43, 94, 60)
            lights(l, 80, 62, 94, 78, 12)
        }

        // ── 1er : l'open-space ──────────────────────────────────────────────────

        private fun island(level: Int, x: Int, z: Int, accent: Short) {
            low(level, x, z, x + 7, z + 1, PLANK_DARK)
            low(level, x, z + 2, x + 7, z + 2, accent, 2)
            low(level, x, z + 3, x + 7, z + 4, PLANK_DARK)
            for (sx in listOf(x + 1, x + 5)) {
                set(sx, feet(level) + 1, z + 1, BRICK_OBSIDIAN)
                set(sx, feet(level) + 1, z + 3, BRICK_OBSIDIAN)
            }
        }

        private fun openSpace() {
            val l = 1
            val accent = PLANK_BLUE
            // Bande nord : kitchenette, bureau de direction vitré, reprographie (arrivée).
            wall(l, 1, 18, 94, 18)
            wall(l, 30, 1, 30, 17)
            wall(l, 61, 1, 61, 17)
            door(l, 66, 18, 69, 18)
            door(l, 45, 18, 47, 18)
            door(l, 12, 18, 14, 18)
            glass(l, 33, 18, 43, 18)
            glass(l, 49, 18, 58, 18)
            low(l, 3, 2, 26, 3, QUARTZ)
            low(l, 10, 8, 14, 10, PLANK_RED)
            low(l, 38, 5, 52, 7, PLANK_DARK)
            box(44, feet(l) + 1, 5, 46, feet(l) + 1, 5, BRICK_OBSIDIAN)
            for ((x, z) in listOf(64 to 3, 70 to 3, 76 to 3)) low(l, x, z, x + 3, z + 1, BRICK_GREY, 2)
            low(l, 66, 10, 72, 11, PLANK_DARK)
            lights(l, 1, 1, 29, 17, 12)
            lights(l, 31, 1, 60, 17, 12)
            lights(l, 62, 1, 89, 17)

            // Archives au sud-ouest : la seule porte est tout au sud, contre la façade.
            wall(l, 1, 55, 21, 55)
            wall(l, 21, 55, 21, 78)
            door(l, 21, 74, 21, 76)
            for (x in listOf(9, 13, 17)) low(l, x, 57, x + 1, 68, BRICK_GREY, 3)
            lights(l, 6, 56, 20, 78, 10)

            // Salle de réunion traversante, au centre du plateau.
            wall(l, 37, 33, 59, 33)
            wall(l, 37, 51, 59, 51)
            wall(l, 37, 33, 37, 51)
            wall(l, 59, 33, 59, 51)
            glass(l, 39, 33, 57, 33)
            glass(l, 39, 51, 57, 51)
            door(l, 37, 40, 37, 42)
            door(l, 59, 44, 59, 46)
            low(l, 42, 40, 54, 43, PLANK_DARK)
            lights(l, 38, 34, 58, 50, 10)

            // Cloison vitrée d'un bout à l'autre du plateau : on voit les archives, on ne passe
            // qu'au bout est. Toute la moitié nord-ouest devient un détour sans issue.
            wall(l, 22, 54, 94, 54)
            glass(l, 23, 54, 86, 54)
            gap(l, 88, 54, 91, 54)

            // Îlots de bureaux, armoires hautes : on voit loin par endroits, jamais en ligne droite.
            for ((x, z) in listOf(
                4 to 24, 22 to 24, 4 to 36, 22 to 36, 4 to 46, 22 to 46,
                64 to 24, 80 to 24, 64 to 36, 80 to 36, 64 to 48, 80 to 48,
                26 to 60, 44 to 58, 62 to 62, 80 to 62, 26 to 71, 44 to 70, 62 to 72, 80 to 72,
            )) island(l, x, z, accent)
            for ((x, z) in listOf(33 to 56, 57 to 55, 76 to 44, 14 to 30)) low(l, x, z, x + 1, z, BRICK_GREY, 3)
            for ((x, z) in listOf(34 to 22, 60 to 28, 92 to 57, 40 to 76)) planter(l, x, z)
            lights(l, 1, 19, 94, 78, 10)
        }

        // ── 2e : la salle des serveurs ──────────────────────────────────────────

        private fun serverFloor() {
            val l = 2
            val y = feet(l)
            // Arrivée au sud-ouest, couloir technique vers le nord, local onduleurs en impasse.
            wall(l, 1, 56, 15, 56)
            wall(l, 15, 56, 15, 78)
            door(l, 4, 56, 7, 56)
            wall(l, 15, 1, 15, 55)
            door(l, 15, 44, 15, 46)
            wall(l, 1, 15, 14, 15)
            door(l, 6, 15, 8, 15)
            for (z in listOf(3, 8)) low(l, 2, z, 11, z + 2, BRICK_OBSIDIAN, 2)
            for (z in listOf(22, 34)) low(l, 1, z, 3, z + 3, BRICK_GREY, 2)
            low(l, 10, 28, 14, 28, BRICK_GREY, 2)
            lights(l, 1, 57, 14, 78)
            lights(l, 1, 16, 14, 55)
            lights(l, 1, 1, 14, 14, 12)

            // Salle des baies : entrée au sud-ouest, sortie au nord-est.
            wall(l, 16, 49, 79, 49)
            wall(l, 79, 1, 79, 49)
            door(l, 79, 2, 79, 4)
            door(l, 30, 49, 32, 49)
            // Moitié ouest fermée au nord, moitié est fermée au sud par les climatiseurs :
            // la moitié des allées sont des impasses, la traversée passe par les coupures.
            box(16, y, 1, 45, y + 4, 5, BRICK_GREY)
            box(49, y, 43, 78, y + 3, 48, BRICK_GREY)
            for (x in 51..77 step 4) box(x, y + 1, 43, x + 1, y + 2, 43, BRICK_OBSIDIAN)
            for ((k, x) in (19..74 step 5).withIndex()) {
                box(x, y, 6, x + 1, y + 3, 42, BRICK_OBSIDIAN)
                for (z in 8..40 step 4) set(x + (k and 1), y + 1, z, BRICK_COBALT)
                box(x, y, 22, x + 1, y + 3, 24, AIR)
                if (k % 2 == 1) box(x, y, 12, x + 1, y + 3, 13, AIR)
                if (k % 2 == 0 && k > 0) box(x, y, 33, x + 1, y + 3, 34, AIR)
            }
            for (x in 21..76 step 10) for (z in listOf(10, 30)) set(x, y + 3, z, TORCH, 1)
            lights(l, 16, 43, 48, 48, 10)
            lights(l, 46, 1, 78, 5, 10)

            // Local climatisation : grande impasse au sud.
            for (x in 20..70 step 10) {
                box(x, y, 54, x + 4, y + 3, 58, BRICK_GREY)
                box(x, y, 66, x + 4, y + 2, 70, BRICK_GREY)
            }
            box(16, y + 3, 62, 78, y + 3, 62, BRICK_OBSIDIAN)
            lights(l, 16, 50, 78, 78, 16)

            // Côté est : couloir de service, supervision et salle des batteries en impasse.
            wall(l, 79, 50, 79, 78)
            wall(l, 85, 1, 85, 61)
            wall(l, 86, 31, 94, 31)
            wall(l, 86, 61, 94, 61)
            door(l, 85, 14, 85, 16)
            door(l, 85, 44, 85, 46)
            low(l, 88, 6, 93, 7, PLANK_DARK)
            for (x in 88..92 step 2) set(x, y + 1, 6, BRICK_OBSIDIAN)
            low(l, 88, 20, 92, 21, PLANK_DARK)
            for (z in 34..56 step 4) low(l, 88, z, 93, z + 1, BRICK_OBSIDIAN, 2)
            low(l, 80, 30, 81, 32, BRICK_GREY, 2)
            low(l, 83, 48, 84, 49, WOOD, 2)
            lights(l, 80, 1, 84, 78)
            lights(l, 86, 1, 94, 30, 12)
            lights(l, 86, 32, 94, 60, 14)
            lights(l, 86, 62, 94, 78)
        }

        // ── 3e : la direction ───────────────────────────────────────────────────

        private fun executiveFloor() {
            val l = 3
            val y = feet(l)
            wall(l, 1, 23, 94, 23)
            wall(l, 1, 29, 94, 29)
            wall(l, 1, 43, 94, 43)
            wall(l, 1, 49, 69, 49)
            wall(l, 70, 49, 94, 49)
            gap(l, 76, 49, 81, 49)
            wall(l, 69, 50, 69, 78)
            wall(l, 29, 1, 29, 22)
            wall(l, 61, 1, 61, 22)
            // La porte coupe-feu est fermée : le couloir nord ne se traverse pas d'un bout à l'autre.
            wall(l, 44, 24, 44, 28, BRICK_RED)

            // Bureaux nord, vitrés sur le couloir sud ; deux sont traversants.
            for (x in listOf(12, 24, 36, 48, 60, 72, 84)) wall(l, x, 30, x, 42)
            val north = listOf(1 to 11, 13 to 23, 25 to 35, 37 to 47, 49 to 59, 61 to 71, 73 to 83, 85 to 94)
            for ((a, b) in north) {
                val mid = (a + b) / 2
                glass(l, a + 1, 43, b - 1, 43)
                door(l, mid - 1, 43, mid + 1, 43)
                low(l, mid - 2, 34, mid + 2, 35, PLANK_DARK)
                set(mid, y + 1, 34, BRICK_OBSIDIAN)
                lights(l, a, 30, b, 42, 10)
            }
            door(l, 29, 29, 31, 29)
            door(l, 53, 29, 55, 29)

            // Bureaux sud : des impasses, vitrées elles aussi.
            for (x in listOf(12, 24, 36, 48, 60)) wall(l, x, 50, x, 78)
            val south = listOf(1 to 11, 13 to 23, 25 to 35, 37 to 47, 49 to 59, 61 to 68)
            for ((a, b) in south) {
                val mid = (a + b) / 2
                glass(l, a + 1, 49, b - 1, 49)
                door(l, mid - 1, 49, mid + 1, 49)
                low(l, mid - 2, 62, mid + 2, 63, PLANK_DARK)
                low(l, a + 1, 76, b - 1, 77, BRICK_GREY, 2)
                lights(l, a, 50, b, 78, 10)
            }

            // Barricade dans le couloir sud : caisses jusqu'au plafond, deux meurtrières à hauteur d'yeux.
            box(40, y, 44, 41, y, 48, COBBLESTONE)
            box(40, y + 1, 44, 41, y + 4, 48, WOOD)
            for (z in listOf(45, 47)) box(40, y + 1, z, 41, y + 1, z, AIR)
            low(l, 33, 44, 34, 45, WOOD, 2)
            low(l, 30, 47, 30, 48, COBBLESTONE)
            lights(l, 1, 44, 94, 48, 9)
            lights(l, 1, 24, 94, 28, 9)

            // Salle du conseil : la grande table, deux portes sur le couloir nord.
            door(l, 34, 23, 36, 23)
            door(l, 50, 23, 52, 23)
            low(l, 36, 8, 54, 12, PLANK_DARK)
            for (x in 37..53 step 4) {
                set(x, y, 7, PLANK_RED)
                set(x, y, 13, PLANK_RED)
            }
            for ((x, z) in listOf(31 to 2, 58 to 2, 31 to 19, 58 to 19)) planter(l, x, z)
            lights(l, 30, 1, 60, 22)

            // Bureau du PDG : impasse au nord-est, baies sur la ville.
            door(l, 76, 23, 78, 23)
            low(l, 74, 6, 84, 8, PLANK_DARK)
            box(78, y + 1, 7, 80, y + 1, 7, BRICK_OBSIDIAN)
            low(l, 66, 14, 70, 18, PLANK_PURPLE)
            for ((x, z) in listOf(63 to 2, 91 to 2, 91 to 19)) planter(l, x, z)
            lights(l, 62, 1, 94, 22, 10)

            // Palier nord-ouest, où monte l'escalier du chantier.
            door(l, 16, 23, 18, 23)
            low(l, 20, 10, 26, 11, PLANK_PURPLE)
            for ((x, z) in listOf(2 to 18, 25 to 2)) planter(l, x, z)
            lights(l, 1, 1, 28, 22)

            // Hall de la direction, au sud-est : l'arrivée.
            low(l, 73, 56, 78, 57, PLANK_PURPLE)
            low(l, 73, 64, 74, 70, PLANK_PURPLE)
            low(l, 80, 54, 86, 55, PLANK_DARK)
            for ((x, z) in listOf(71 to 51, 87 to 51, 71 to 76)) planter(l, x, z)
            lights(l, 70, 50, 94, 78)
        }

        // ── 4e : l'étage en travaux ─────────────────────────────────────────────

        private fun buildingSite() {
            val l = 4
            val y = feet(l)
            // Bureaux déjà finis au nord : trois impasses.
            wall(l, 13, 22, 94, 22)
            wall(l, 41, 1, 41, 21)
            wall(l, 69, 1, 69, 21)
            wall(l, 13, 6, 13, 21)
            for (x in listOf(25, 54, 80)) door(l, x, 22, x + 2, 22)
            low(l, 18, 8, 34, 9, PLANK_DARK)
            low(l, 46, 4, 50, 18, WOOD, 2)
            low(l, 56, 6, 64, 7, PLANK_DARK)
            low(l, 74, 4, 90, 5, PLANK_DARK)
            low(l, 84, 12, 88, 16, COTTON_WHITE, 2)
            lights(l, 14, 1, 40, 21, 12)
            lights(l, 42, 1, 68, 21, 12)
            lights(l, 70, 1, 94, 21, 12)

            // Murs porteurs inachevés : trois blocs de haut, on ne voit pas par-dessus.
            box(1, y, 45, 61, y + 2, 45, BRICK_RED)
            box(8, y, 45, 11, y + 2, 45, AIR)
            box(40, y, 45, 43, y + 2, 45, AIR)
            box(62, y, 23, 62, y + 2, 78, BRICK_RED)
            box(62, y, 30, 62, y + 2, 33, AIR)
            box(62, y, 52, 62, y + 2, 55, AIR)
            for (x in listOf(20, 30, 50)) set(x, y + 3, 45, BRICK_RED)
            box(20, y, 30, 20, y + 2, 38, BRICK_RED)
            box(76, y, 64, 94, y + 2, 64, BRICK_RED)
            box(84, y, 64, 87, y + 2, 64, AIR)
            // Cage grillagée au nord-est : stock de matériel, une seule entrée.
            box(82, y, 26, 94, y + 1, 26, PLANK_YELLOW)
            box(82, y, 26, 82, y + 1, 40, PLANK_YELLOW)
            box(82, y, 40, 94, y + 1, 40, PLANK_YELLOW)
            box(82, y, 32, 82, y + 1, 34, AIR)

            // Échafaudage : plateforme à trois blocs, garde-corps, trois marches côté sud.
            for ((x, z) in listOf(30 to 56, 37 to 56, 30 to 63, 37 to 63)) box(x, y, z, x, y + 1, z, PLANK_DARK)
            box(30, y + 2, 56, 37, y + 2, 63, PLANK)
            box(30, y + 3, 56, 37, y + 3, 56, PLANK_YELLOW)
            box(30, y + 3, 56, 30, y + 3, 63, PLANK_YELLOW)
            box(37, y + 3, 56, 37, y + 3, 63, PLANK_YELLOW)
            box(30, y + 3, 63, 32, y + 3, 63, PLANK_YELLOW)
            box(35, y + 3, 63, 37, y + 3, 63, PLANK_YELLOW)
            for (s in 0..2) for (x in 33..34) {
                for (fy in y until y + s) set(x, fy, 66 - s, PLANK_DARK)
                set(x, y + s, 66 - s, STAIR, 2)
            }

            // Matériaux entreposés : palettes de briques, piles de planches, sacs, bétonnière, conteneur.
            for (c in listOf(
                intArrayOf(4, 28, 3, 2, 1), intArrayOf(10, 38, 2, 2, 2), intArrayOf(26, 26, 4, 1, 2),
                intArrayOf(30, 36, 2, 3, 1), intArrayOf(46, 30, 3, 2, 2), intArrayOf(54, 38, 2, 2, 1),
                intArrayOf(4, 52, 2, 3, 2), intArrayOf(14, 62, 3, 2, 1), intArrayOf(8, 72, 2, 2, 2),
                intArrayOf(22, 68, 3, 1, 1), intArrayOf(46, 52, 2, 2, 2), intArrayOf(50, 70, 4, 2, 1),
                intArrayOf(58, 62, 2, 2, 2), intArrayOf(70, 28, 2, 3, 2), intArrayOf(74, 46, 3, 2, 1),
                intArrayOf(68, 56, 2, 2, 2), intArrayOf(88, 48, 2, 2, 1), intArrayOf(72, 74, 2, 2, 1),
            )) {
                val block = when (c[4]) { 1 -> BRICK_RED; else -> if (c[0] % 3 == 0) PLANK else SANDSTONE }
                low(l, c[0], c[1], c[0] + c[2] - 1, c[1] + c[3] - 1, block, c[4])
            }
            low(l, 40, 64, 41, 65, BRICK_GREY, 2)
            set(40, y + 2, 64, COBBLESTONE)
            box(64, y, 68, 69, y + 2, 71, WOOD_BLUE)
            lights(l, 1, 23, 94, 78, 10)
        }

        // ── Toit ────────────────────────────────────────────────────────────────

        private fun roof() {
            val y = ROOF_Y + 1
            box(0, ROOF_Y, 0, WIDTH - 1, ROOF_Y, DEPTH - 1, BRICK_GREY)
            box(0, y, 0, 0, y + 1, DEPTH - 1, BRICK_OBSIDIAN)
            box(WIDTH - 1, y, 0, WIDTH - 1, y + 1, DEPTH - 1, BRICK_OBSIDIAN)
            box(1, y, 0, WIDTH - 2, y + 1, 0, BRICK_OBSIDIAN)
            box(1, y, DEPTH - 1, WIDTH - 2, y + 1, DEPTH - 1, BRICK_OBSIDIAN)

            // Salle des machines d'ascenseur : un bloc plein, un escalier extérieur, un garde-corps en haut.
            box(40, y, 30, 58, y + 4, 46, BRICK_GREY)
            box(40, y + 5, 30, 58, y + 5, 30, BRICK_OBSIDIAN)
            box(40, y + 5, 30, 40, y + 5, 46, BRICK_OBSIDIAN)
            box(58, y + 5, 30, 58, y + 5, 46, BRICK_OBSIDIAN)
            box(40, y + 5, 46, 52, y + 5, 46, BRICK_OBSIDIAN)
            for (s in 0..4) for (z in 47..48) {
                val x = 57 - s
                for (fy in y until y + s) set(x, fy, z, BRICK_GREY)
                set(x, y + s, z, STAIR, 3)
            }
            set(47, y + 5, 38, TORCH, 0)

            // L'héliport : une dalle surélevée d'un bloc, son « H », et l'hélicoptère posé dessus.
            box(6, y, 6, 30, y, 30, BRICK_GREY)
            box(12, y, 12, 13, y, 24, COTTON_WHITE)
            box(23, y, 12, 24, y, 24, COTTON_WHITE)
            box(14, y, 17, 22, y, 18, COTTON_WHITE)
            for (x in listOf(6, 30)) for (z in listOf(6, 30)) set(x, y + 1, z, TORCH, 0)
            box(8, y + 1, 26, 15, y + 2, 28, BRICK_OBSIDIAN)
            box(16, y + 1, 27, 20, y + 1, 27, BRICK_OBSIDIAN)
            box(20, y + 2, 26, 20, y + 3, 28, BRICK_OBSIDIAN)
            box(11, y + 3, 27, 12, y + 3, 27, BRICK_OBSIDIAN)
            box(6, y + 4, 27, 17, y + 4, 27, PLANK_DARK)

            // Ventilation, château d'eau, antennes : de la couverture, jamais une ligne droite vers l'héliport.
            for (c in listOf(
                intArrayOf(64, 58, 4, 3, 2), intArrayOf(72, 38, 3, 4, 2), intArrayOf(28, 48, 4, 3, 1),
                intArrayOf(18, 62, 3, 3, 2), intArrayOf(64, 14, 4, 3, 2), intArrayOf(84, 22, 3, 3, 1),
                intArrayOf(76, 60, 3, 2, 1), intArrayOf(34, 36, 2, 3, 2), intArrayOf(62, 44, 2, 2, 1),
                intArrayOf(86, 44, 3, 3, 2), intArrayOf(36, 66, 4, 2, 1), intArrayOf(48, 14, 3, 3, 2),
            )) box(c[0], y, c[1], c[0] + c[2] - 1, y + c[4] - 1, c[1] + c[3] - 1, BRICK_GREY)
            // Champ de panneaux solaires au sud-ouest : des rangées basses, à couvert accroupi.
            for (z in 46..70 step 6) {
                box(6, y, z, 16, y, z + 1, PLANK_BLUE)
                box(20, y, z, 30, y, z + 1, PLANK_BLUE)
            }
            box(66, y, 64, 70, y + 1, 67, BRICK_GREY)
            box(78, y, 50, 81, y + 1, 52, BRICK_GREY)
            box(72, y, 6, 77, y + 3, 11, WOOD_DARK)
            box(73, y + 4, 7, 76, y + 4, 10, WOOD_DARK)
            for ((x, z) in listOf(88 to 8, 8 to 70)) {
                box(x - 1, y, z - 1, x + 1, y, z + 1, BRICK_GREY)
                box(x, y + 1, z, x, y + 6, z, BRICK_OBSIDIAN)
                box(x - 2, y + 4, z, x + 2, y + 4, z, BRICK_GREY)
                set(x, y + 7, z, TORCH, 0)
            }
        }

        /** L'édicule de l'escalier du toit : un toit sur la cage, la sortie reste ouverte à l'est. */
        private fun roofHouse() {
            box(80, ROOF_Y + 6, 73, 86, ROOF_Y + 6, 78, BRICK_GREY)
        }
    }
}
