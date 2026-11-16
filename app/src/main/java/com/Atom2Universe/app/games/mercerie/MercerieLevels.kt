package com.Atom2Universe.app.games.mercerie

/**
 * Ce qu'il faut réussir. Un niveau sans rien de tout cela se joue « au score » : il faut atteindre
 * la première étoile avant le dernier coup.
 */
class Goal(
    /** Pièces à récolter, par couleur. */
    val collect: IntArray = IntArray(MercerieBoard.COLORS),
    /** Découdre tous les écussons. */
    val patches: Boolean = false,
    /** Dés à coudre à faire descendre. */
    val thimbles: Int = 0
) {
    val onlyScore: Boolean get() = !patches && thimbles == 0 && collect.all { it == 0 }
}

class MercerieLevel(
    val number: Int,
    val moves: Int,
    /** Nombre de couleurs en jeu (4 à 6). */
    val colors: Int,
    /** Une ligne de texte par rangée ; voir [MercerieLevels] pour les signes. */
    val map: List<String>,
    val goal: Goal,
    /** Scores des trois étoiles. */
    val stars: IntArray,
    /** Dés à coudre présents en même temps sur le plateau. */
    val thimblesAtOnce: Int = 1
) {
    val cols = map[0].length
    val rows = map.size
    fun with(moves: Int = this.moves, stars: IntArray = this.stars) =
        MercerieLevel(number, moves, colors, map, goal, stars, thimblesAtOnce)
}

/**
 * Les niveaux, dans l'ordre.
 *
 * Signes d'une carte : `.` case ; espace = pas de case ; `p` `P` écusson simple ou double ;
 * `k` `K` `M` nœud de laine à 1, 2 ou 3 couches ; `q` `Q` nœud posé sur un écusson ;
 * `x` pièce épinglée ; `y` pièce épinglée sur un écusson.
 *
 * Les coups et les étoiles de [TUNING] sortent du banc `MercerieBalanceTest` : on y recolle sa
 * sortie après avoir touché à une carte.
 */
object MercerieLevels {
    const val RED = 0
    const val BLUE = 1
    const val YELLOW = 2
    const val GREEN = 3
    const val PURPLE = 4
    const val ORANGE = 5

    private fun collect(vararg pairs: Pair<Int, Int>) = Goal(collect = IntArray(MercerieBoard.COLORS).also { a -> pairs.forEach { a[it.first] = it.second } })
    private fun rect(cols: Int, rows: Int) = List(rows) { ".".repeat(cols) }

    private class Raw(val colors: Int, val goal: Goal, val map: List<String>, val atOnce: Int = 1)

    private val RAW = listOf(
        // 1 à 3 : aligner, récolter
        Raw(4, Goal(), rect(7, 7)),
        Raw(5, collect(RED to 20, BLUE to 20), rect(7, 7)),
        Raw(5, Goal(), rect(8, 8)),
        // 4 : premiers écussons
        Raw(4, Goal(patches = true), listOf(
            "........",
            "........",
            "..pppp..",
            "..pppp..",
            "..pppp..",
            "..pppp..",
            "........",
            "........")),
        Raw(5, collect(YELLOW to 25, GREEN to 25), listOf(
            " ...... ",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            " ...... ")),
        Raw(5, Goal(patches = true), listOf(
            "........",
            "........",
            "........",
            "........",
            "........",
            "pppppppp",
            "pppppppp",
            "pppppppp")),
        // 7 : premiers nœuds
        Raw(5, Goal(patches = true), listOf(
            "........",
            "........",
            "..pppp..",
            "..pqqp..",
            "..pqqp..",
            "..pppp..",
            "........",
            "........")),
        // 8 : premiers dés à coudre
        Raw(5, Goal(thimbles = 2), rect(7, 8)),
        // 9 : premières épingles
        Raw(5, Goal(patches = true), listOf(
            "........",
            ".y....y.",
            "..pppp..",
            "..pyyp..",
            "..pyyp..",
            "..pppp..",
            ".y....y.",
            "........")),
        Raw(5, Goal(), listOf(
            "   ...   ",
            "  .....  ",
            " ....... ",
            ".........",
            ".........",
            ".........",
            " ....... ",
            "  .....  ",
            "   ...   ")),
        // 11
        Raw(5, Goal(patches = true), listOf(
            "........",
            "........",
            ".pppppp.",
            ".pPPPPp.",
            ".pPPPPp.",
            ".pPPPPp.",
            ".pppppp.",
            "........",
            "........")),
        Raw(6, Goal(), rect(8, 8)),
        Raw(5, collect(RED to 35, PURPLE to 35), listOf(
            "........",
            "........",
            "........",
            ".kkkkkk.",
            "........",
            "........",
            "........",
            "........")),
        Raw(5, Goal(thimbles = 3), listOf(
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            ".k....k.",
            "........",
            "........"), atOnce = 2),
        Raw(5, Goal(patches = true), listOf(
            "pp.....pp",
            "pp.....pp",
            ".........",
            "...   ...",
            "...   ...",
            "...   ...",
            ".........",
            "pp.....pp",
            "pp.....pp")),
        // 16
        Raw(6, collect(RED to 20, YELLOW to 20, ORANGE to 20), rect(8, 8)),
        Raw(5, Goal(patches = true), List(7) { "ppppppp" }),
        Raw(5, Goal(patches = true), listOf(
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "qpqpqpqp",
            "pqpqpqpq",
            "qpqpqpqp")),
        Raw(5, Goal(thimbles = 3), rect(7, 9), atOnce = 2),
        Raw(5, Goal(patches = true), listOf(
            "  .....  ",
            "  .....  ",
            "..ppppp..",
            "..pyyyp..",
            "..pyPyp..",
            "..pyyyp..",
            "..ppppp..",
            "  .....  ",
            "  .....  ")),
        // 21
        Raw(6, Goal(), listOf(
            "MM....MM",
            "MM....MM",
            "........",
            "........",
            "........",
            "........",
            "MM....MM",
            "MM....MM")),
        Raw(5, Goal(patches = true), listOf(
            "........",
            ".PPPPPP.",
            ".PPPPPP.",
            ".PPPPPP.",
            ".PPPPPP.",
            ".PPPPPP.",
            ".PPPPPP.",
            "........")),
        Raw(5, collect(BLUE to 30, GREEN to 30), List(8) { ".... ...." }),
        Raw(5, Goal(thimbles = 3), listOf(
            "........",
            "........",
            "..x..x..",
            "........",
            "........",
            ".x....x.",
            "........",
            "...xx...",
            "........"), atOnce = 2),
        Raw(5, Goal(patches = true), listOf(
            ".........",
            ".........",
            ".........",
            ".........",
            "kk.kkk.kk",
            "PPPPPPPPP",
            "PPPPPPPPP",
            ".........",
            ".........")),
        // 26
        Raw(5, Goal(patches = true), listOf(
            "........",
            ".p.p.p.p",
            "y.y.y.y.",
            ".p.p.p.p",
            "p.p.p.p.",
            ".y.y.y.y",
            "p.p.p.p.",
            "........")),
        Raw(5, collect(RED to 45), listOf(
            " ..   .. ",
            ".... ....",
            ".........",
            ".........",
            ".........",
            " ....... ",
            "  .....  ",
            "   ...   ",
            "    .    ")),
        Raw(6, Goal(patches = true, thimbles = 2), listOf(
            "........",
            "........",
            "..k..k..",
            "........",
            "pppppppp",
            "pqppppqp",
            "pppppppp",
            "........",
            "........")),
        Raw(6, Goal(patches = true), listOf(
            ".........",
            ".PP...PP.",
            ".PQ...QP.",
            "....p....",
            "...pPp...",
            "....p....",
            ".PQ...QP.",
            ".PP...PP.",
            ".........")),
        Raw(6, Goal(collect = IntArray(MercerieBoard.COLORS).also { it[YELLOW] = 30 }, patches = true), listOf(
            "  .....  ",
            " .ppppp. ",
            ".pp.x.pp.",
            ".p.yPy.p.",
            ".pxPMPxp.",
            ".p.yPy.p.",
            ".pp.x.pp.",
            " .ppppp. ",
            "  .....  "))
    )

    /** Par niveau : coups, puis scores des trois étoiles. */
    private val TUNING = arrayOf(
        intArrayOf(14, 5000, 9000, 14000),
        intArrayOf(18, 1500, 3400, 5400),
        intArrayOf(18, 3500, 6000, 9000),
        intArrayOf(12, 3000, 9000, 15000),
        intArrayOf(20, 2500, 5000, 8000),
        intArrayOf(28, 5000, 9000, 14000),
        intArrayOf(16, 3000, 6500, 10000),
        intArrayOf(34, 4000, 7500, 11500),
        intArrayOf(32, 5000, 10000, 15000),
        intArrayOf(22, 5000, 8500, 13000),
        intArrayOf(24, 5000, 12000, 19000),
        intArrayOf(22, 3000, 5000, 7500),
        intArrayOf(28, 4000, 7500, 11500),
        intArrayOf(38, 5000, 10000, 16000),
        intArrayOf(40, 3500, 6500, 10000),
        intArrayOf(32, 3000, 5000, 8000),
        intArrayOf(34, 5000, 9500, 15000),
        intArrayOf(34, 6000, 13000, 20000),
        intArrayOf(32, 5000, 9500, 15000),
        intArrayOf(24, 4000, 8000, 13000),
        intArrayOf(24, 2500, 4000, 6000),
        intArrayOf(34, 8000, 15000, 24000),
        intArrayOf(34, 3000, 6000, 9000),
        intArrayOf(36, 5000, 10000, 15500),
        intArrayOf(34, 6000, 14000, 22000),
        intArrayOf(36, 6000, 11000, 17000),
        intArrayOf(36, 4000, 7500, 11500),
        intArrayOf(44, 6000, 12000, 19000),
        intArrayOf(42, 6000, 11000, 17000),
        intArrayOf(42, 5000, 9500, 15000)
    )

    val all: List<MercerieLevel> = RAW.mapIndexed { n, raw ->
        val t = TUNING[n]
        MercerieLevel(n + 1, t[0], raw.colors, raw.map, raw.goal, intArrayOf(t[1], t[2], t[3]), raw.atOnce)
    }

    /** Nombre de niveaux faits main ; au-delà, [MercerieGenerator] fabrique les étages. */
    val HANDMADE: Int get() = all.size
    fun get(number: Int): MercerieLevel = all[(number - 1).coerceIn(0, all.size - 1)]
}
