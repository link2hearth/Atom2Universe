package com.Atom2Universe.app.games.caves.world

/**
 * Définitions de structures voxel prêtes à placer dans le monde.
 * [dx, dy, dz, blockId] — coordonnées relatives à l'origine (coin bas-gauche, Y=sol).
 * Les blocs AIR ne sont pas encodés (implicites).
 */
data class StructureDef(
    val name: String,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val blocks: Array<IntArray>  // chaque IntArray = [dx, dy, dz, blockId]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StructureDef) return false
        return name == other.name
    }
    override fun hashCode(): Int = name.hashCode()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun b(id: Byte) = id.toInt()

/** Génère une rangée pleine sur l'axe X de x0 à x1 (inclus) pour une position (y, z) fixe. */
private fun rowX(y: Int, z: Int, x0: Int, x1: Int, id: Byte): List<IntArray> =
    (x0..x1).map { intArrayOf(it, y, z, b(id)) }

/** Génère une rangée pleine sur l'axe Z de z0 à z1 (inclus) pour une position (x, y) fixe. */
private fun rowZ(x: Int, y: Int, z0: Int, z1: Int, id: Byte): List<IntArray> =
    (z0..z1).map { intArrayOf(x, y, it, b(id)) }

/** Dalle pleine (XZ) à hauteur y. */
private fun slab(y: Int, x0: Int, x1: Int, z0: Int, z1: Int, id: Byte): List<IntArray> =
    (x0..x1).flatMap { x -> (z0..z1).map { z -> intArrayOf(x, y, z, b(id)) } }

// ─────────────────────────────────────────────────────────────────────────────
//  HOUSE_WOOD — Maison rustique en bois  (12 × 10 × 10)
//
//  Plan XZ (vue du dessus) :
//    X  →  0 1 2 3 4 5 6 7 8 9 10 11
//    Z=0   [façade avant, porte en x=5-6]
//    Z=9   [mur arrière]
//
//  Élévations Y :
//    y=0  Fondations STONE
//    y=1–4  Murs PLANK, fenêtres à y=2-3 sur façades latérales et arrière
//    y=5–9  Toit en pente WOOD (logs), avec LEAVES débordants
// ─────────────────────────────────────────────────────────────────────────────

private val HOUSE_WOOD_BLOCKS: Array<IntArray> by lazy {
    val list = mutableListOf<IntArray>()

    // ── y=0 : Fondations en STONE (dalle complète 12×10) ─────────────────────
    list += slab(0, 0, 11, 0, 9, STONE)

    // ── y=1–4 : Murs en PLANK ───────────────────────────────────────────────
    //   Mur avant  (z=0)  — porte à x=5,6  (trou y=1..2)
    //   Mur arrière (z=9)
    //   Mur gauche  (x=0)
    //   Mur droit   (x=11)
    for (y in 1..4) {
        // Mur avant z=0 : x=0..11, sauf porte x=5-6 aux niveaux y=1,2
        for (x in 0..11) {
            val isDoorCol = x == 5 || x == 6
            val isDoorRow = y == 1 || y == 2
            if (!(isDoorCol && isDoorRow)) {
                list += intArrayOf(x, y, 0, b(PLANK))
            }
        }
        // Mur arrière z=9
        list += rowX(y, 9, 0, 11, PLANK)
        // Mur gauche x=0 (z=1..8, coins déjà couverts)
        list += rowZ(0, y, 1, 8, PLANK)
        // Mur droit x=11 (z=1..8)
        list += rowZ(11, y, 1, 8, PLANK)
    }

    // Fenêtres (trous 2×2) dans les murs latéraux à y=2-3
    //   Mur gauche (x=0) : z=2..3 et z=6..7 → on retire ces blocs
    //   Mur droit  (x=11): z=2..3 et z=6..7
    // On remplace par AIR en ne les ajoutant pas — il faut retirer de la liste
    // Astuce : reconstruire les murs latéraux sans les fenêtres
    list.removeAll { it[0] == 0 && (it[1] == 2 || it[1] == 3) && (it[2] == 2 || it[2] == 3 || it[2] == 6 || it[2] == 7) }
    list.removeAll { it[0] == 11 && (it[1] == 2 || it[1] == 3) && (it[2] == 2 || it[2] == 3 || it[2] == 6 || it[2] == 7) }
    // Fenêtre mur arrière (z=9) centrée : x=2..3 et x=8..9
    list.removeAll { it[2] == 9 && (it[1] == 2 || it[1] == 3) && (it[0] == 2 || it[0] == 3 || it[0] == 8 || it[0] == 9) }

    // ── y=1–4 : Sol intérieur en PLANK (x=1..10, z=1..8) ────────────────────
    list += slab(0, 1, 10, 1, 8, PLANK)   // sol intérieur par-dessus les fondations

    // ── Intérieur : FURNACE au fond (contre mur arrière, centré) ─────────────
    // y=1, z=8 (devant mur arrière z=9), centré x=5
    list += intArrayOf(5, 1, 8, b(FURNACE))

    // ── Intérieur : TORCH sur les murs intérieurs ────────────────────────────
    // TORCH est un cross-sprite → posé sur le sol/sur une surface horizontale
    // On les place au pied des murs intérieurs (y=1, décollés d'1 bloc du mur)
    list += intArrayOf(1, 1, 1, b(TORCH))   // coin avant-gauche intérieur
    list += intArrayOf(10, 1, 1, b(TORCH))  // coin avant-droit intérieur
    list += intArrayOf(1, 1, 7, b(TORCH))   // coin arrière-gauche intérieur
    list += intArrayOf(10, 1, 7, b(TORCH))  // coin arrière-droit intérieur

    // ── y=5–9 : Toit en pente WOOD (faîtage à x=5-6, descend vers x=0 et x=11) ──
    //  Le toit est orienté sur l'axe X (pignon sur murs avant/arrière).
    //  Couche de bord (y=5) : x=0..11, z=0..9 (toute la largeur)
    //  Chaque niveau y monte d'un bloc côté X : à y=5+k, les X valides sont k..(11-k)
    //  k=0 → x=0..11, k=1 → x=1..10, ..., k=5 → x=5..6 (faîtage)

    for (k in 0..5) {
        val roofY = 5 + k
        val xMin = k
        val xMax = 11 - k
        for (x in xMin..xMax) {
            // Seulement le bord externe (le dessous est creux)
            val isEdge = (x == xMin || x == xMax)
            // On ne place que la pente extérieure (une couche d'épaisseur 1 sur chaque versant)
            // + la colonne centrale (faîtage)
            if (isEdge || k == 5) {
                for (z in 0..9) {
                    list += intArrayOf(x, roofY, z, b(WOOD))
                }
            }
        }
    }

    // Pignons (triangles sur murs avant z=0 et arrière z=9, y=5..9 au-dessus des murs)
    for (k in 1..5) {
        val roofY = 5 + k
        val xMin = k
        val xMax = 11 - k
        for (x in xMin..xMax) {
            list += intArrayOf(x, roofY, 0, b(WOOD))   // pignon avant
            list += intArrayOf(x, roofY, 9, b(WOOD))   // pignon arrière
        }
    }

    // ── LEAVES débordants autour du toit (y=5, bords extérieurs) ────────────
    // On ajoute des feuilles qui dépassent d'1 bloc sur les côtés X et Z
    for (z in -1..10) {
        list += intArrayOf(-1, 5, z, b(LEAVES))
        list += intArrayOf(12, 5, z, b(LEAVES))
    }
    for (x in 0..11) {
        list += intArrayOf(x, 5, -1, b(LEAVES))
        list += intArrayOf(x, 5, 10, b(LEAVES))
    }
    // Quelques touffes supplémentaires en y=6 sur les côtés
    for (z in 1..8 step 2) {
        list += intArrayOf(-1, 6, z, b(LEAVES))
        list += intArrayOf(12, 6, z, b(LEAVES))
    }

    list.toTypedArray()
}

// ─────────────────────────────────────────────────────────────────────────────
//  RUINS_STONE — Ruines en pierre avec végétation  (14 × 7 × 14)
//
//  Plan XZ : murs sur le périmètre, effondrements aléatoires déterministes,
//  intérieur avec sol STONE + patches GRAVEL + champignons.
//
//  Murs d'épaisseur 1, hauteur variable par segment :
//    Mur avant (z=0)  : z=0, x=0..13, hauteur 3-5 avec brèches
//    Mur arrière (z=13): z=13, x=0..13, hauteur 2-4 avec brèches
//    Mur gauche (x=0) : x=0, z=0..13, hauteur 3-5 avec brèches
//    Mur droit  (x=13): x=13, z=0..13, hauteur 3-5 avec brèches
//    Piliers intérieurs effondrés
// ─────────────────────────────────────────────────────────────────────────────

private val RUINS_STONE_BLOCKS: Array<IntArray> by lazy {
    val list = mutableListOf<IntArray>()

    // ── y=0 : Sol en STONE (dalle complète 14×14) ────────────────────────────
    list += slab(0, 0, 13, 0, 13, STONE)

    // ── Sol intérieur : patches de GRAVEL (y=0 par-dessus) ───────────────────
    // Pattern déterministe : quelques zones irrégulières
    val gravelPatches = listOf(
        2 to 2, 3 to 2, 2 to 3,
        5 to 6, 6 to 6, 5 to 7, 6 to 7,
        9 to 3, 10 to 3,
        8 to 9, 9 to 9, 8 to 10,
        3 to 10, 4 to 11
    )
    for ((x, z) in gravelPatches) {
        list += intArrayOf(x, 0, z, b(GRAVEL))
    }

    // ── Murs effondrés : hauteurs variables par colonne ───────────────────────
    // Tableau de hauteurs pour chaque colonne x (mur avant/arrière) et z (mur latéral)
    // Hauteur 0 = effondrement total sur cette colonne (brèche)

    // Mur avant z=0 : hauteurs par x (0..13)
    val frontHeight = intArrayOf(4, 5, 5, 3, 2, 0, 0, 2, 4, 5, 4, 5, 3, 4)
    for (x in 0..13) {
        val h = frontHeight[x]
        for (y in 1..h) {
            val block = if (y >= h - 1 && (x + y) % 3 == 0) GRANITE else STONE
            list += intArrayOf(x, y, 0, b(block))
        }
        // Patches ROCK_MOSS en sommet de mur
        if (h >= 2) list += intArrayOf(x, h, 0, b(ROCK_MOSS))
    }

    // Mur arrière z=13 : hauteurs par x
    val backHeight = intArrayOf(3, 2, 4, 5, 5, 4, 0, 0, 3, 4, 5, 3, 2, 3)
    for (x in 0..13) {
        val h = backHeight[x]
        for (y in 1..h) {
            val block = if ((x + y) % 4 == 1) GRANITE else STONE
            list += intArrayOf(x, y, 13, b(block))
        }
        if (h >= 2) list += intArrayOf(x, h, 13, b(ROCK_MOSS))
    }

    // Mur gauche x=0 : hauteurs par z (0..13, coins déjà couverts)
    val leftHeight = intArrayOf(4, 4, 3, 5, 5, 2, 0, 4, 5, 3, 2, 4, 5, 3)
    for (z in 1..12) {
        val h = leftHeight[z]
        for (y in 1..h) {
            val block = if ((z + y) % 3 == 2) GRANITE else STONE
            list += intArrayOf(0, y, z, b(block))
        }
        if (h >= 2) list += intArrayOf(0, h, z, b(ROCK_MOSS))
    }

    // Mur droit x=13 : hauteurs par z
    val rightHeight = intArrayOf(3, 5, 4, 2, 4, 5, 4, 0, 0, 5, 3, 4, 5, 3)
    for (z in 1..12) {
        val h = rightHeight[z]
        for (y in 1..h) {
            val block = if ((z + y) % 5 == 0) GRANITE else STONE
            list += intArrayOf(13, y, z, b(block))
        }
        if (h >= 2) list += intArrayOf(13, h, z, b(ROCK_MOSS))
    }

    // ── Piliers intérieurs effondrés ─────────────────────────────────────────
    // 4 piliers à l'intérieur, partiellement debout
    val pillars = listOf(
        Triple(3, 3, 4),   // (x, z, hauteur)
        Triple(10, 3, 3),
        Triple(3, 10, 5),
        Triple(10, 10, 2)
    )
    for ((px, pz, ph) in pillars) {
        for (y in 1..ph) {
            list += intArrayOf(px, y, pz, b(STONE))
        }
        list += intArrayOf(px, ph, pz, b(ROCK_MOSS))
    }

    // ── Décombres : blocs isolés sur le sol intérieur ────────────────────────
    val rubble = listOf(
        intArrayOf(4, 1, 4, b(STONE)),
        intArrayOf(5, 1, 3, b(GRANITE)),
        intArrayOf(7, 1, 5, b(STONE)),
        intArrayOf(9, 1, 7, b(GRANITE)),
        intArrayOf(6, 1, 10, b(STONE)),
        intArrayOf(11, 1, 4, b(STONE)),
        intArrayOf(2, 1, 8, b(GRANITE))
    )
    list += rubble

    // ── Champignons à l'intérieur (cross-sprites sur le sol) ─────────────────
    list += intArrayOf(4,  1, 6,  b(MUSHROOM_RED))
    list += intArrayOf(7,  1, 3,  b(MUSHROOM_BROWN))
    list += intArrayOf(9,  1, 10, b(MUSHROOM_RED))
    list += intArrayOf(6,  1, 8,  b(MUSHROOM_BROWN))
    list += intArrayOf(11, 1, 9,  b(MUSHROOM_TAN))
    list += intArrayOf(2,  1, 11, b(MUSHROOM_TAN))
    list += intArrayOf(8,  1, 5,  b(MUSHROOM_RED))

    // ── ROCK_MOSS au sol dans les coins ──────────────────────────────────────
    list += intArrayOf(1,  1, 1,  b(ROCK_MOSS))
    list += intArrayOf(12, 1, 1,  b(ROCK_MOSS))
    list += intArrayOf(1,  1, 12, b(ROCK_MOSS))
    list += intArrayOf(12, 1, 12, b(ROCK_MOSS))
    list += intArrayOf(1,  1, 6,  b(ROCK))
    list += intArrayOf(12, 1, 7,  b(ROCK))

    list.toTypedArray()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Catalogue global
// ─────────────────────────────────────────────────────────────────────────────

object StructureData {

    /**
     * Maison rustique en bois (12 × 10 × 10 blocs).
     * Fondations STONE, murs PLANK avec fenêtres et porte, toit en pente WOOD,
     * FURNACE intérieur, TORCHes aux coins, LEAVES débordants.
     */
    val HOUSE_WOOD: StructureDef = StructureDef(
        name  = "house_wood",
        sizeX = 14,  // +1 de chaque côté pour les LEAVES
        sizeY = 10,
        sizeZ = 12,
        blocks = HOUSE_WOOD_BLOCKS
    )

    /**
     * Ruines en pierre (14 × 7 × 14 blocs).
     * Murs STONE/GRANITE effondrés, sol avec GRAVEL, piliers brisés,
     * ROCK_MOSS en sommet de murs, champignons et décombres à l'intérieur.
     */
    val RUINS_STONE: StructureDef = StructureDef(
        name  = "ruins_stone",
        sizeX = 14,
        sizeY = 7,
        sizeZ = 14,
        blocks = RUINS_STONE_BLOCKS
    )

    val all: List<StructureDef> = listOf(HOUSE_WOOD, RUINS_STONE)
}
