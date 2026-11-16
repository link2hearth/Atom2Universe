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
    val thimbles: Int = 0,
    /** Chasser tous les moutons de poussière. */
    val dust: Boolean = false
) {
    val onlyScore: Boolean get() = !patches && thimbles == 0 && !dust && collect.all { it == 0 }

    /**
     * Le moins de coups qu'on puisse laisser pour récolter : un coup pour deux pièces. Un alignement
     * simple en donne trois ; il reste ainsi de la marge sans compter sur les bonus ni les cascades.
     */
    val minMoves: Int get() = (collect.sum() + 1) / 2
}

/** Un téléporteur : les pièces entrent par le dessous de la case ([r1], [c1]) et ressortent par le dessus de ([r2], [c2]). */
class Portal(val r1: Int, val c1: Int, val r2: Int, val c2: Int)

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
    val thimblesAtOnce: Int = 1,
    val portals: List<Portal> = emptyList(),
    /** Minuteurs : coups laissés à chacun pour être aligné (0 : pas de minuteur). */
    val timer: Int = 0,
    /** Minuteurs en tout sur la partie, ceux de la carte (`t`) compris. */
    val timers: Int = 0,
    val timersAtOnce: Int = 1,
    /**
     * Le défi : les coups qu'il a fallu au robot (sa partie médiane). Atteindre l'objectif en autant
     * de coups ou moins rapporte de quoi avoir les trois étoiles. 0 : pas de défi (niveau au score).
     */
    val par: Int = 0
) {
    val cols = map[0].length
    val rows = map.size
    fun with(moves: Int = this.moves, stars: IntArray = this.stars, number: Int = this.number, par: Int = this.par) =
        MercerieLevel(number, moves, colors, map, goal, stars, thimblesAtOnce, portals, timer, timers, timersAtOnce, par)
}

/**
 * Les niveaux, dans l'ordre.
 *
 * Signes d'une carte : `.` case ; espace = pas de case ; `p` `P` écusson simple ou double ;
 * `k` `K` `M` nœud de laine à 1, 2 ou 3 couches ; `q` `Q` nœud posé sur un écusson ;
 * `x` pièce épinglée ; `y` pièce épinglée sur un écusson ; `d` mouton de poussière ; `e` mouton
 * posé sur un écusson ; `t` pièce à minuteur (le niveau donne son compte de départ).
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

    private class Raw(
        val colors: Int, val goal: Goal, val map: List<String>, val atOnce: Int = 1,
        val portals: List<Portal> = emptyList(), val timer: Int = 0, val timers: Int = 0, val timersAtOnce: Int = 1
    )

    /** Les trente niveaux d'origine, à leur place. */
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

    /**
     * Les dix niveaux de la mise à jour (papillons, moutons, minuteurs, téléporteurs). Ils ne
     * s'intercalent pas dans les anciens : ils se posent à la suite de là où en est le joueur
     * ([MercerieProgress.extraAt]), au plus tôt après le trentième.
     */
    private val EXTRA = listOf(
        // Premiers moutons de poussière.
        Raw(5, Goal(dust = true), listOf(
            "........",
            "........",
            "........",
            "........",
            "...dd...",
            "...dd...",
            "...dd...",
            "........")),
        // Premiers téléporteurs : trois étages en zigzag.
        Raw(5, collect(GREEN to 25, PURPLE to 25), MercerieShapes.ZIGZAG.map, portals = MercerieShapes.ZIGZAG.portals),
        // Premiers minuteurs, sur un bouton à quatre trous.
        Raw(5, collect(RED to 30), listOf(
            "  .....  ",
            " ...t... ",
            ".........",
            "... . ...",
            ".........",
            "... . ...",
            ".........",
            " ....... ",
            "  .....  "), timer = 12, timers = 3),
        // Une fleur : le mouton du cœur gagne les écussons des pétales.
        Raw(5, Goal(patches = true), listOf(
            "   ...   ",
            "  .....  ",
            ".. ppp ..",
            "..ppppp..",
            "..ppdpp..",
            "..ppppp..",
            ".. ppp ..",
            "  .....  ",
            "   ...   ")),
        // Deux tours : les dés à coudre passent de l'une à l'autre.
        Raw(5, Goal(thimbles = 2), MercerieShapes.TOWERS.map, portals = MercerieShapes.TOWERS.portals),
        // Un champignon, deux minuteurs à la fois.
        Raw(5, collect(BLUE to 30, YELLOW to 30), MercerieShapes.MUSHROOM.map, timer = 9, timers = 4, timersAtOnce = 2),
        // Un papillon plein de moutons.
        Raw(5, Goal(dust = true), listOf(
            "...   ...",
            ".d.. ..d.",
            ".........",
            " ....... ",
            " ....... ",
            ".........",
            ".d.. ..d.",
            "...   ...")),
        // Une étoile d'écussons sous la menace des minuteurs.
        Raw(5, Goal(patches = true), listOf(
            "    .    ",
            "   ...   ",
            "  .ppp.  ",
            "..ppppp..",
            " .ppppp. ",
            "  .ppp.  ",
            " ... ... ",
            "...   ...",
            "..     .."), timer = 10, timers = 3),
        // Le miroir : en passant, les pièces changent de côté.
        Raw(5, Goal(patches = true), listOf(
            "........",
            "........",
            ".k....k.",
            "........",
            "        ",
            "........",
            "........",
            "pppppppp",
            "pPPPPPPp"), portals = MercerieShapes.MIRROR.portals),
        // La maison : des moutons au grenier et des minuteurs.
        Raw(5, collect(PURPLE to 30), listOf(
            "    .    ",
            "   ...   ",
            "  .d.d.  ",
            " ....... ",
            ".........",
            ".. ... ..",
            ".. ... ..",
            "...   ...",
            "...   ..."), timer = 10, timers = 3)
    )

    /**
     * Par niveau : coups, scores des trois étoiles, puis le défi (coups médians du robot, 0 pour un
     * niveau au score). Les trente anciens gardent leurs coups et leurs étoiles d'origine, qu'on ne
     * touche pas ; les dix de la mise à jour viennent du banc, à la mesure d'un humain.
     */
    private val TUNING = arrayOf(
        intArrayOf(14, 5000, 9000, 14000, 0),
        intArrayOf(18, 1500, 3400, 5400, 12),
        intArrayOf(18, 3500, 6000, 9000, 0),
        intArrayOf(12, 3000, 9000, 15000, 4),
        intArrayOf(20, 2500, 5000, 8000, 11),
        intArrayOf(28, 5000, 9000, 14000, 10),
        intArrayOf(16, 3000, 6500, 10000, 5),
        intArrayOf(34, 4000, 7500, 11500, 12),
        intArrayOf(32, 5000, 10000, 15000, 14),
        intArrayOf(22, 5000, 8500, 13000, 0),
        intArrayOf(24, 5000, 12000, 19000, 9),
        intArrayOf(22, 3000, 5000, 7500, 0),
        intArrayOf(28, 4000, 7500, 11500, 16),
        intArrayOf(38, 5000, 10000, 16000, 14),
        intArrayOf(40, 3500, 6500, 10000, 14),
        intArrayOf(32, 3000, 5000, 8000, 16),
        intArrayOf(34, 5000, 9500, 15000, 18),
        intArrayOf(34, 6000, 13000, 20000, 13),
        intArrayOf(32, 5000, 9500, 15000, 14),
        intArrayOf(24, 4000, 8000, 13000, 11),
        intArrayOf(24, 2500, 4000, 6000, 0),
        intArrayOf(34, 8000, 15000, 24000, 16),
        intArrayOf(34, 3000, 6000, 9000, 18),
        intArrayOf(36, 5000, 10000, 15500, 13),
        intArrayOf(34, 6000, 14000, 22000, 13),
        intArrayOf(36, 6000, 11000, 17000, 16),
        intArrayOf(36, 4000, 7500, 11500, 22),
        intArrayOf(44, 6000, 12000, 19000, 28),
        intArrayOf(42, 6000, 11000, 17000, 26),
        intArrayOf(42, 5000, 9500, 15000, 22),
        intArrayOf(12, 1000, 3500, 4800, 3),
        intArrayOf(22, 2600, 9000, 10400, 13),
        intArrayOf(23, 2200, 8100, 9200, 14),
        intArrayOf(13, 2200, 6700, 9300, 7),
        intArrayOf(44, 3400, 10400, 12500, 28),
        intArrayOf(28, 3800, 10600, 13000, 17),
        intArrayOf(12, 900, 2800, 4300, 6),
        intArrayOf(14, 1900, 5800, 6900, 8),
        intArrayOf(23, 3400, 11700, 14400, 14),
        intArrayOf(35, 2300, 7700, 9600, 22)
    )

    private fun level(number: Int, raw: Raw, t: IntArray, moves: Int = t[0]) =
        MercerieLevel(number, moves, raw.colors, raw.map, raw.goal, intArrayOf(t[1], t[2], t[3]), raw.atOnce,
            raw.portals, raw.timer, raw.timers, raw.timersAtOnce, if (raw.goal.onlyScore) 0 else t[4])

    val all: List<MercerieLevel> = RAW.mapIndexed { n, raw -> level(n + 1, raw, TUNING[n]) }

    /** Nombre de niveaux d'origine ; au-delà viennent les niveaux de la mise à jour, puis les étages de [MercerieGenerator]. */
    val HANDMADE: Int get() = all.size
    val EXTRA_COUNT: Int get() = EXTRA.size
    fun get(number: Int): MercerieLevel = all[(number - 1).coerceIn(0, all.size - 1)]

    /** Le [k]-ième niveau de la mise à jour, posé au numéro [number]. */
    fun extra(k: Int, number: Int): MercerieLevel =
        TUNING[RAW.size + k].let { t -> level(number, EXTRA[k], t, maxOf(t[0], EXTRA[k].goal.minMoves)) }

    /** Tous les niveaux faits main, rangés comme pour un nouveau joueur : pour les garde-fous et le banc. */
    val handmade: List<MercerieLevel> by lazy { all + EXTRA.indices.map { extra(it, HANDMADE + 1 + it) } }

    /** Ce que contient le numéro [number] quand les niveaux de la mise à jour commencent à [extraAt]. */
    sealed class Slot {
        class Base(val number: Int) : Slot()
        class Extra(val k: Int) : Slot()
        /** Un étage sans fin : [floor] est son numéro de fabrication, sans les niveaux de la mise à jour. */
        class Floor(val floor: Int) : Slot()
    }

    fun slot(number: Int, extraAt: Int): Slot = when {
        number in extraAt until extraAt + EXTRA_COUNT -> Slot.Extra(number - extraAt)
        number <= HANDMADE -> Slot.Base(number)
        number >= extraAt + EXTRA_COUNT -> Slot.Floor(number - EXTRA_COUNT)
        else -> Slot.Floor(number)
    }
}

/** Une forme de plateau : sa carte (`.` case, espace trou) et ses téléporteurs. */
class Shape(val map: List<String>, val portals: List<Portal> = emptyList())

/**
 * Les silhouettes des plateaux : objets de mercerie, animaux, fleurs, et trois plateaux à
 * téléporteurs où les pièces changent d'étage ou de côté en tombant.
 */
object MercerieShapes {
    val HEART = Shape(listOf(
        " ..   .. ",
        ".... ....",
        ".........",
        ".........",
        ".........",
        " ....... ",
        "  .....  ",
        "   ...   "))
    val CAT = Shape(listOf(
        ".       .",
        "..     ..",
        ".........",
        ".........",
        ".. ... ..",
        ".........",
        ".........",
        " ....... ",
        "  .....  "))
    val FLOWER = Shape(listOf(
        "   ...   ",
        "  .....  ",
        ".. ... ..",
        ".........",
        ".........",
        ".........",
        ".. ... ..",
        "  .....  ",
        "   ...   "))
    val BUTTERFLY = Shape(listOf(
        "...   ...",
        ".... ....",
        ".........",
        " ....... ",
        " ....... ",
        ".........",
        ".... ....",
        "...   ..."))
    val MUSHROOM = Shape(listOf(
        "  .....  ",
        " ....... ",
        ".........",
        ".........",
        ".........",
        "   ...   ",
        "   ...   ",
        "   ...   ",
        "  .....  "))
    val SPOOL = Shape(listOf(
        ".........",
        ".........",
        "  .....  ",
        "   ...   ",
        "   ...   ",
        "   ...   ",
        "  .....  ",
        ".........",
        "........."))
    val STAR = Shape(listOf(
        "    .    ",
        "   ...   ",
        "  .....  ",
        ".........",
        " ....... ",
        "  .....  ",
        " ... ... ",
        "...   ...",
        "..     .."))
    val HOUSE = Shape(listOf(
        "    .    ",
        "   ...   ",
        "  .....  ",
        " ....... ",
        ".........",
        ".. ... ..",
        ".. ... ..",
        "...   ...",
        "...   ..."))
    val BUTTON = Shape(listOf(
        "  .....  ",
        " ....... ",
        ".........",
        "... . ...",
        ".........",
        "... . ...",
        ".........",
        " ....... ",
        "  .....  "))
    val FISH = Shape(listOf(
        "   ....  ",
        ". ...... ",
        ".........",
        ".........",
        ".........",
        ". ...... ",
        "   ....  "))
    val CROWN = Shape(listOf(
        ".   .   .",
        ".. ... ..",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "........."))
    val HOURGLASS = Shape(listOf(
        ".........",
        " ....... ",
        "  .....  ",
        "   ...   ",
        "   ...   ",
        "  .....  ",
        " ....... ",
        "........."))
    val CLOUD = Shape(listOf(
        "  ..  .. ",
        " ....... ",
        ".........",
        ".........",
        ".........",
        " ....... "))

    /** Trois étages en zigzag : les pièces passent à droite, puis reviennent à gauche. */
    val ZIGZAG = Shape(listOf(
        "......   ",
        "......   ",
        "......   ",
        "   ......",
        "   ......",
        "   ......",
        "......   ",
        "......   ",
        "......   "),
        listOf(Portal(2, 0, 3, 6), Portal(2, 1, 3, 7), Portal(2, 2, 3, 8), Portal(5, 6, 6, 0), Portal(5, 7, 6, 1), Portal(5, 8, 6, 2)))

    /** Deux tours : ce qui tombe au pied de la tour de gauche ressort en haut de celle de droite. */
    val TOWERS = Shape(List(8) { "...   ..." }, listOf(Portal(7, 0, 0, 6), Portal(7, 1, 0, 7), Portal(7, 2, 0, 8)))

    /** Le miroir : en passant d'une moitié à l'autre, chaque colonne change de côté. */
    val MIRROR = Shape(List(9) { if (it == 4) "        " else "........" }, List(8) { Portal(3, it, 5, 7 - it) })

    /** Les silhouettes nouvelles, pour les étages sans fin. */
    val ALL: List<Shape> = listOf(HEART, CAT, FLOWER, BUTTERFLY, MUSHROOM, SPOOL, STAR, HOUSE, BUTTON, FISH, CROWN, HOURGLASS, CLOUD, ZIGZAG, TOWERS, MIRROR)
}
