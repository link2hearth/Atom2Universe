package com.Atom2Universe.app.games.mercerie

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Les étages sans fin : passé les niveaux faits main, chaque étage est fabriqué à partir de son
 * numéro, donc identique d'un appareil à l'autre.
 *
 * La variété vient de trois tirages : la forme du plateau ([SHAPES]), l'objectif (les cinq sortes
 * reviennent toutes dans chaque groupe de cinq étages) et les obstacles, posés en motifs
 * symétriques. La difficulté, elle, ne monte pas : [tune] fait jouer le robot pour fixer les coups
 * et les étoiles, et un étage qu'il ne sait pas gagner est refait en plus léger ([build], `variant`).
 */
object MercerieGenerator {
    /** À augmenter quand la fabrication change : les réglages gardés en mémoire sont alors refaits. */
    const val VERSION = 1

    private val SHAPES: List<List<String>> = listOf(
        List(8) { "........" },
        List(9) { "........." },
        List(9) { "......." },
        List(9) { "........" },
        listOf(" ...... ", "........", "........", "........", "........", "........", "........", " ...... "),
        listOf("  .....  ", " ....... ", ".........", ".........", ".........", ".........", ".........", " ....... ", "  .....  "),
        listOf("   ...   ", "  .....  ", " ....... ", ".........", ".........", ".........", " ....... ", "  .....  ", "   ...   "),
        listOf("  .....  ", "  .....  ", ".........", ".........", ".........", ".........", ".........", "  .....  ", "  .....  "),
        listOf(".........", ".........", ".........", "...   ...", "...   ...", "...   ...", ".........", ".........", "........."),
        List(8) { ".... ...." },
        listOf(" ..   .. ", ".... ....", ".........", ".........", ".........", " ....... ", "  .....  ", "   ...   ", "    .    "),
        listOf(".........", ".........", " ....... ", "  .....  ", "  .....  ", "  .....  ", " ....... ", ".........", "........."),
        listOf(".... ....", ".... ....", ".... ....", ".... ....", ".........", ".........", ".........", "........."),
        listOf(". . . . .", ".........", ".........", ".........", ".........", ".........", ".........", "........."),
        listOf(" ...... ", "........", "........", "...  ...", "...  ...", "........", "........", " ...... ")
    )

    private enum class Type { SCORE, COLLECT, PATCHES, THIMBLES, MIXED }

    private fun typeOf(number: Int): Type {
        val k = (number - MercerieLevels.HANDMADE - 1).coerceAtLeast(0)
        return Type.entries.shuffled(Random(k / 5 * 7919L + 13))[k % 5]
    }

    /**
     * Les formes passent toutes avant de revenir, dans un ordre retiré à chaque tour. Un tour
     * commence par une forme de la première moitié de [SHAPES] et finit par une de la seconde :
     * deux étages de suite n'ont donc jamais la même forme, même d'un tour à l'autre.
     */
    private fun shapeOf(number: Int): Int {
        val k = (number - MercerieLevels.HANDMADE - 1).coerceAtLeast(0)
        val size = SHAPES.size
        val order = (0 until size).shuffled(Random(k / size * 104729L + 7)).toMutableList()
        val head = order.first { it < size / 2 }
        order.remove(head)
        order.add(0, head)
        val tail = order.last { it >= size / 2 }
        order.remove(tail)
        order.add(tail)
        return order[k % size]
    }

    /** Un motif symétrique gauche-droite : dit pour chaque case si elle en fait partie. */
    private fun motif(kind: Int, rows: Int, cols: Int, rnd: Random): (Int, Int) -> Boolean {
        val mr = (rows - 1) / 2f
        val mc = (cols - 1) / 2f
        val a = rnd.nextInt(2)
        val b = rnd.nextInt(3)
        val f: (Int, Int) -> Boolean = when (kind) {
            // Un pavé au centre.
            0 -> { r, c -> abs(r - mr) <= 1.5f + a && abs(c - mc) <= 1.5f + b }
            // Les rangées du bas.
            1 -> { r, _ -> r >= rows - 2 - a }
            // Un damier dans la moitié basse.
            2 -> { r, c -> r >= rows / 2 - a && (r + abs(c - mc).toInt()) % 2 == 0 }
            // Un cadre, à une ou deux cases du bord.
            3 -> { r, c -> min(min(r, rows - 1 - r), min(c, cols - 1 - c)) == 1 + a }
            // Les quatre coins.
            4 -> { r, c -> min(r, rows - 1 - r) <= 1 + a && min(c, cols - 1 - c) <= 1 + a }
            // Une colonne sur deux.
            5 -> { r, c -> r >= rows / 3 && abs(c - mc).toInt() % 2 == a }
            // Une croix en diagonale.
            6 -> { r, c -> abs(abs(r - mr) - abs(c - mc)) < .6f }
            // Une bande au milieu.
            else -> { r, _ -> abs(r - mr) <= .5f + a }
        }
        return f
    }

    /** Fabrique l'étage [number]. Chaque [variant] est plus léger que le précédent ; le troisième se joue au score. */
    fun build(number: Int, variant: Int = 0): MercerieLevel {
        val rnd = Random(number * 1_000_003L + variant * 97L)
        val shape = SHAPES[shapeOf(number)]
        val rows = shape.size
        val cols = shape[0].length
        val grid = Array(rows) { r -> CharArray(cols) { c -> shape[r][c] } }
        val type = if (variant >= 3) Type.SCORE else typeOf(number)
        val colors = if (variant == 0 && rnd.nextInt(4) == 0) 6 else 5
        val ease = 1f / (1 + variant)

        fun cellsOf(limit: Int, least: Int, keep: (Int, Int) -> Boolean): List<Pair<Int, Int>> {
            // Essaie des motifs jusqu'à en trouver un de la bonne taille.
            repeat(8) {
                val m = motif(rnd.nextInt(8), rows, cols, rnd)
                val found = ArrayList<Pair<Int, Int>>()
                for (r in 0 until rows) for (c in 0 until cols) if (grid[r][c] != ' ' && m(r, c) && keep(r, c)) found += r to c
                if (found.size in least..limit) return found
            }
            return emptyList()
        }

        fun knots(limit: Int, fromRow: Int) {
            val max = (limit * ease).toInt()
            if (max < 2) return
            val strong = rnd.nextInt(3) == 0
            for ((r, c) in cellsOf(max, 2) { r, _ -> r >= fromRow }) grid[r][c] = when (grid[r][c]) {
                'p' -> 'q'
                'P' -> 'Q'
                '.' -> if (strong) 'K' else 'k'
                else -> grid[r][c]
            }
        }

        fun pins(limit: Int, fromRow: Int) {
            val max = (limit * ease).toInt()
            if (max < 2) return
            for ((r, c) in cellsOf(max, 2) { r, _ -> r >= fromRow }) grid[r][c] = when (grid[r][c]) {
                'p' -> 'y'
                '.' -> 'x'
                else -> grid[r][c]
            }
        }

        fun patches() {
            val area = shape.sumOf { row -> row.count { it == '.' } }
            for ((r, c) in cellsOf((area * .55f * ease).toInt().coerceAtLeast(10), 8) { _, _ -> true }) grid[r][c] = 'p'
            if (grid.all { row -> row.none { it == 'p' } }) {
                // Aucun motif à la bonne taille : les deux rangées du bas, toujours jouables.
                for (r in rows - 2 until rows) for (c in 0 until cols) if (grid[r][c] == '.') grid[r][c] = 'p'
            }
            if (variant == 0 && rnd.nextInt(5) < 2) {
                val m = motif(rnd.nextInt(8), rows, cols, rnd)
                for (r in 0 until rows) for (c in 0 until cols) if (grid[r][c] == 'p' && m(r, c)) grid[r][c] = 'P'
            }
        }

        val collect = IntArray(MercerieBoard.COLORS)
        var wantPatches = false
        var thimbles = 0
        var atOnce = 1
        when (type) {
            Type.SCORE -> { knots(10, 0); if (rnd.nextInt(10) < 3) pins(6, 1) }
            Type.COLLECT -> {
                val picked = (0 until colors).shuffled(rnd).take(1 + rnd.nextInt(3))
                for (c in picked) collect[c] = 18 + rnd.nextInt(5) * 4
                if (rnd.nextInt(10) < 6) knots(10, 0)
                if (rnd.nextInt(10) < 3) pins(6, 1)
            }
            Type.PATCHES -> {
                wantPatches = true
                patches()
                if (rnd.nextInt(2) == 0) knots(8, 0)
                if (rnd.nextInt(10) < 4) pins(6, 1)
            }
            Type.THIMBLES -> {
                // Les dés à coudre demandent beaucoup de coups : un de moins à chaque variante.
                thimbles = when (variant) { 0 -> 2 + rnd.nextInt(2); 1 -> 2; else -> 1 }
                atOnce = if (thimbles >= 3) 2 else 1
                if (rnd.nextInt(10) < 4) knots(6, 2)
                if (rnd.nextInt(10) < 3) pins(4, 1)
            }
            Type.MIXED -> {
                wantPatches = true
                patches()
                if (rnd.nextBoolean()) collect[rnd.nextInt(colors)] = 20 + rnd.nextInt(3) * 4 else thimbles = 2
                if (rnd.nextInt(10) < 3) knots(6, 2)
            }
        }
        // L'entrée des colonnes reste libre sur au moins la moitié du plateau : les pièces doivent pouvoir arriver.
        var blocked = 0
        var entries = 0
        for (c in 0 until cols) {
            val top = (0 until rows).firstOrNull { grid[it][c] != ' ' } ?: continue
            entries++
            if (grid[top][c] in "kKMqQxy") blocked++
        }
        if (blocked * 2 > entries) for (c in 0 until cols) {
            val top = (0 until rows).firstOrNull { grid[it][c] != ' ' } ?: continue
            grid[top][c] = when (grid[top][c]) { 'q', 'y' -> 'p'; 'Q' -> 'P'; 'k', 'K', 'M', 'x' -> '.'; else -> grid[top][c] }
        }
        return MercerieLevel(
            number, 30, colors, grid.map { String(it) },
            Goal(collect, wantPatches, thimbles), intArrayOf(1000, 2000, 3000), atOnce
        )
    }

    private fun round(v: Float) = (v / 100f).toInt() * 100

    /**
     * Fixe les coups et les étoiles de [draft] en faisant jouer le robot huit fois avec une large
     * réserve : on garde ce qu'il lui a fallu à sa septième meilleure partie, plus une marge d'un
     * dixième, pour que le joueur ne finisse pas à un coup près. Rend null si l'étage est trop long
     * (plus de 38 coups pour cinq parties sur huit) : il faut alors un [build] plus léger.
     */
    fun tune(draft: MercerieLevel): MercerieLevel? {
        val games = 8
        val n = draft.number
        val moves: Int
        if (draft.goal.onlyScore) {
            moves = 16 + Random(n * 31L).nextInt(5) * 2
        } else {
            val wide = draft.with(moves = 60, stars = intArrayOf(0, 1, 2))
            val used = (0 until games).map { g ->
                MercerieRobot.play(wide, n * 1000L + g).let { if (it.won) it.used else 99 }
            }.sorted()
            if (used[4] > 38) return null
            moves = ((minOf(used[6], 42) * 1.1f).toInt() + 2).coerceAtLeast(12)
        }
        val real = draft.with(moves = moves, stars = intArrayOf(0, 1, 2))
        val scores = (0 until games).map { g -> MercerieRobot.play(real, n * 5000L + g).score }.sorted()
        val median = scores[games / 2].toFloat()
        val s1 = round(median * (if (draft.goal.onlyScore) .3f else .2f)).coerceAtLeast(500)
        val s2 = maxOf(round(median * .5f), s1 + 500)
        val s3 = maxOf(round(median * .8f), s2 + 500)
        return draft.with(moves = moves, stars = intArrayOf(s1, s2, s3))
    }

    /** L'étage [number] prêt à jouer, et la variante retenue. Lent (le robot joue) : hors du fil d'affichage. */
    fun make(number: Int): Pair<Int, MercerieLevel> {
        for (variant in 0..3) tune(build(number, variant))?.let { return variant to it }
        return 3 to build(number, 3).with(moves = 20, stars = intArrayOf(3000, 6000, 9000))
    }
}
