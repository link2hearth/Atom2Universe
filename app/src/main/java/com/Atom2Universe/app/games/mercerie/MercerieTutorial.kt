package com.Atom2Universe.app.games.mercerie

import com.Atom2Universe.app.R

/**
 * Un coup du tutoriel : échanger ([r1], [c1]) et ([r2], [c2]), annoncé par [text]. Le reste dit ce
 * que le coup doit montrer, et le garde-fou `MercerieTutorialTest` le vérifie : le bonus qui naît
 * ([makes]), la combinaison nommée ([combo]), les effets qui partent ([shows]).
 */
class TutorMove(
    val r1: Int, val c1: Int, val r2: Int, val c2: Int, val text: Int,
    val makes: Kind? = null, val combo: Combo? = null, val shows: List<FxType> = emptyList()
)

/** Un bonus posé d'avance sur le tableau, à la couleur de sa case. */
class TutorBonus(val r: Int, val c: Int, val kind: Kind)

/**
 * Un tableau du tutoriel : chaque pièce est posée à la main ([grid] : un chiffre par case pour la
 * couleur, `*` un arc-en-ciel, `k` un nœud de laine), et le joueur ne peut jouer que les coups
 * prévus ([moves]), l'un après l'autre. [normalOnly] : le papillon, absent du mode facile.
 *
 * Après la démonstration, le plateau devient libre et le joueur refait la même chose seul :
 * fabriquer le bonus [practice] (la fin de la démonstration en laisse toujours l'occasion, grâce
 * à la graine [seed] des pièces qui tombent), ou échanger les bonus [encore], qui apparaissent.
 */
class Lesson(
    val title: Int,
    val grid: List<String>,
    val moves: List<TutorMove>,
    val bonuses: List<TutorBonus> = emptyList(),
    val normalOnly: Boolean = false,
    /** Le bonus à refaire seul (ciseaux : LINE_H, dans un sens ou dans l'autre). */
    val practice: Kind? = null,
    /** Les deux bonus qui apparaissent côte à côte après la démonstration, à échanger seul. */
    val encore: List<TutorBonus> = emptyList(),
    /** La graine des pièces qui tombent. */
    val seed: Long = 9000L
) {
    /** Il y a quelque chose à refaire seul après la démonstration. */
    val hasPractice: Boolean get() = practice != null || encore.isNotEmpty()

    /** Le plateau prêt à jouer. Toujours le même : les pièces qui tombent sont tirées de [seed]. */
    fun board(easy: Boolean): MercerieBoard {
        val map = grid.map { row -> row.map { if (it == 'k') 'k' else '.' }.joinToString("") }
        // Assez de coups pour jouer librement après la démonstration sans jamais finir la partie.
        val level = MercerieLevel(0, 999, 5, map, Goal(), intArrayOf(1_000_000, 2_000_000, 3_000_000), easy = easy)
        val b = MercerieBoard(level, seed)
        for (r in grid.indices) for (c in grid[r].indices) {
            val ch = grid[r][c]
            val p = b.piece[b.idx(r, c)] ?: continue
            p.pinned = false
            p.timer = 0
            if (ch == '*') { p.color = -1; p.kind = Kind.RAINBOW } else { p.color = ch - '0'; p.kind = Kind.NORMAL }
        }
        for (s in bonuses) b.piece[b.idx(s.r, s.c)]!!.kind = s.kind
        return b
    }

    /** Les deux cases du coup [k], sur un plateau de la taille de [grid]. */
    fun cells(k: Int): IntArray {
        val m = moves[k]
        val cols = grid[0].length
        return intArrayOf(m.r1 * cols + m.c1, m.r2 * cols + m.c2)
    }

    /** Pose les bonus [encore] sur [b], au repos ; rend leurs cases. Un bonus garde la couleur de sa pièce. */
    fun placeEncore(b: MercerieBoard): List<Int> = encore.mapNotNull { e ->
        val i = b.idx(e.r, e.c)
        b.piece[i]?.let { p ->
            p.kind = e.kind
            p.pinned = false
            p.timer = 0
            if (e.kind == Kind.RAINBOW) p.color = -1 else if (p.color < 0) p.color = 0
            i
        }
    }

    /** Le coup qui refait l'exercice sur [b], s'il y en a un : celui que montre le conseil. */
    fun practiceMove(b: MercerieBoard): IntArray? {
        if (encore.isNotEmpty()) {
            val c = encore.map { b.idx(it.r, it.c) }
            val there = encore.indices.all { k -> b.piece[c[k]]?.kind?.let(::plain) == plain(encore[k].kind) }
            return if (there && b.canSwap(c[0], c[1])) c.toIntArray() else null
        }
        val kind = practice ?: return null
        return b.allMoves().firstOrNull { b.bonusMadeBy(it[0], it[1]) == kind }
    }

    /** Vrai si échanger [a] et [z] refait l'exercice, où que ce soit sur le plateau. Avant que le coup ne se joue. */
    fun practiced(b: MercerieBoard, a: Int, z: Int): Boolean {
        if (encore.isNotEmpty()) {
            val got = listOfNotNull(b.piece[a]?.kind, b.piece[z]?.kind).map(::plain).sorted()
            return got == encore.map { plain(it.kind) }.sorted()
        }
        val kind = practice ?: return false
        return b.bonusMadeBy(a, z) == kind
    }

    /** Des ciseaux sont des ciseaux, qu'ils coupent la ligne ou la colonne. */
    private fun plain(k: Kind) = if (k == Kind.LINE_V) Kind.LINE_H else k
}

/**
 * Les tableaux du tutoriel, dans l'ordre : chaque bonus se fabrique puis s'utilise, puis les
 * combinaisons de deux bonus, puis une réaction en chaîne.
 *
 * Le fond de chaque tableau suit deux rangées qui alternent (`1234123`, `3412341`) : jamais trois
 * pareilles en ligne ni en colonne, ni de carré. Les cœurs (`0`) y sont posés pour le coup prévu ;
 * les pièces au-dessus retombent là où le coup suivant les attend.
 */
object MercerieLessons {
    private const val P = "1234123"
    private const val Q = "3412341"

    private fun base() = listOf(P, Q, P, Q, P, Q, P)

    /** Le fond, avec des cases changées : (rangée, colonne) → signe. */
    private fun grid(vararg cells: Pair<Pair<Int, Int>, Char>): List<String> {
        val g = base().map { StringBuilder(it) }
        for ((at, ch) in cells) g[at.first][at.second] = ch
        return g.map { it.toString() }
    }

    private infix fun Int.x(c: Int) = this to c

    /** Les deux bonus qui réapparaissent après une démonstration de combinaison, à échanger seul. */
    private fun encore(a: Kind, b: Kind) = listOf(TutorBonus(2, 2, a), TutorBonus(2, 3, b))

    val ALL: List<Lesson> = listOf(
        // Les ciseaux : 4 cœurs alignés, puis les ciseaux alignés coupent leur colonne.
        Lesson(
            R.string.mercerie_tuto_line,
            grid((4 x 3) to '0', (4 x 4) to '0', (5 x 1) to '0', (5 x 2) to '0', (5 x 4) to '0', (6 x 5) to '0'),
            listOf(
                TutorMove(4, 3, 5, 3, R.string.mercerie_tuto_line_make, makes = Kind.LINE_V),
                TutorMove(5, 5, 6, 5, R.string.mercerie_tuto_line_use, shows = listOf(FxType.LINE_V))
            ),
            practice = Kind.LINE_H, seed = SEED_LINE
        ),
        // Le pompon : un L de cœurs, puis le pompon aligné éclate.
        Lesson(
            R.string.mercerie_tuto_bomb,
            grid((1 x 3) to '0', (3 x 3) to '0', (4 x 3) to '0', (4 x 4) to '0', (5 x 1) to '0', (5 x 2) to '0', (6 x 3) to '0'),
            listOf(
                TutorMove(5, 3, 6, 3, R.string.mercerie_tuto_bomb_make, makes = Kind.BOMB),
                TutorMove(4, 3, 4, 4, R.string.mercerie_tuto_bomb_use, shows = listOf(FxType.BLAST))
            ),
            practice = Kind.BOMB, seed = SEED_BOMB
        ),
        // L'arc-en-ciel : 5 cœurs alignés, puis échangé avec une pelote.
        Lesson(
            R.string.mercerie_tuto_rainbow,
            grid((4 x 3) to '0', (5 x 1) to '0', (5 x 2) to '0', (5 x 4) to '0', (5 x 5) to '0'),
            listOf(
                TutorMove(4, 3, 5, 3, R.string.mercerie_tuto_rainbow_make, makes = Kind.RAINBOW),
                TutorMove(5, 3, 6, 3, R.string.mercerie_tuto_rainbow_use, combo = Combo.RAINBOW_COLOR, shows = listOf(FxType.ZAP))
            ),
            practice = Kind.RAINBOW, seed = SEED_RAINBOW
        ),
        // Le papillon : un carré de cœurs, puis le papillon aligné s'envole sur le nœud.
        Lesson(
            R.string.mercerie_tuto_butterfly,
            grid((1 x 5) to 'k', (4 x 2) to '0', (4 x 3) to '0', (5 x 2) to '0', (5 x 5) to '0', (6 x 3) to '0', (6 x 4) to '0'),
            listOf(
                TutorMove(5, 3, 6, 3, R.string.mercerie_tuto_butterfly_make, makes = Kind.BUTTERFLY),
                TutorMove(5, 4, 6, 4, R.string.mercerie_tuto_butterfly_use, shows = listOf(FxType.FLY, FxType.KNOT))
            ),
            normalOnly = true, practice = Kind.BUTTERFLY, seed = SEED_BUTTERFLY
        ),
        // Les combinaisons : deux bonus côte à côte, à échanger ; puis deux autres apparaissent.
        Lesson(
            R.string.mercerie_tuto_cross, base(),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_cross_use, combo = Combo.CROSS)),
            listOf(TutorBonus(5, 3, Kind.LINE_V), TutorBonus(5, 4, Kind.LINE_H)),
            encore = encore(Kind.LINE_H, Kind.LINE_V), seed = 9124L
        ),
        Lesson(
            R.string.mercerie_tuto_line_bomb, base(),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_line_bomb_use, combo = Combo.LINE_BOMB)),
            listOf(TutorBonus(5, 3, Kind.LINE_H), TutorBonus(5, 4, Kind.BOMB)),
            encore = encore(Kind.BOMB, Kind.LINE_V), seed = 9155L
        ),
        Lesson(
            R.string.mercerie_tuto_big_bomb, base(),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_big_bomb_use, combo = Combo.BIG_BOMB)),
            listOf(TutorBonus(5, 3, Kind.BOMB), TutorBonus(5, 4, Kind.BOMB)),
            encore = encore(Kind.BOMB, Kind.BOMB), seed = 9186L
        ),
        Lesson(
            R.string.mercerie_tuto_rainbow_line, grid((5 x 3) to '*'),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_rainbow_line_use, combo = Combo.RAINBOW_LINE)),
            listOf(TutorBonus(5, 4, Kind.LINE_V)),
            encore = encore(Kind.LINE_H, Kind.RAINBOW), seed = 9217L
        ),
        Lesson(
            R.string.mercerie_tuto_rainbow_bomb, grid((5 x 3) to '*'),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_rainbow_bomb_use, combo = Combo.RAINBOW_BOMB)),
            listOf(TutorBonus(5, 4, Kind.BOMB)),
            encore = encore(Kind.RAINBOW, Kind.BOMB), seed = 9248L
        ),
        Lesson(
            R.string.mercerie_tuto_butterfly_line, grid((1 x 1) to 'k'),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_butterfly_line_use, combo = Combo.BUTTERFLY_LINE, shows = listOf(FxType.FLY))),
            listOf(TutorBonus(5, 3, Kind.BUTTERFLY), TutorBonus(5, 4, Kind.LINE_H)),
            normalOnly = true, encore = encore(Kind.LINE_V, Kind.BUTTERFLY), seed = 9279L
        ),
        Lesson(
            R.string.mercerie_tuto_double_rainbow, grid((5 x 3) to '*', (5 x 4) to '*'),
            listOf(TutorMove(5, 3, 5, 4, R.string.mercerie_tuto_double_rainbow_use, combo = Combo.DOUBLE_RAINBOW)),
            encore = encore(Kind.RAINBOW, Kind.RAINBOW), seed = 9310L
        ),
        // En chaîne : les ciseaux coupent la rangée, touchent le pompon, qui touche d'autres
        // ciseaux, qui coupent la colonne jusqu'à l'arc-en-ciel. Ensuite, jeu libre.
        Lesson(
            R.string.mercerie_tuto_chain,
            grid((1 x 6) to '*', (5 x 1) to '0', (5 x 2) to '0', (6 x 3) to '0'),
            listOf(TutorMove(5, 3, 6, 3, R.string.mercerie_tuto_chain_use, shows = listOf(FxType.LINE_H, FxType.BLAST, FxType.LINE_V, FxType.ZAP))),
            listOf(TutorBonus(5, 1, Kind.LINE_H), TutorBonus(5, 5, Kind.BOMB), TutorBonus(4, 6, Kind.LINE_V)),
            seed = 9341L
        )
    )

    /** Les tableaux à montrer : sans le papillon en mode facile. */
    fun forMode(easy: Boolean): List<Lesson> = ALL.filter { !easy || !it.normalOnly }

    /**
     * Graines des tableaux où l'on fabrique un bonus : choisies (garde-fou `MercerieTutorialTest`)
     * pour que la fin de la démonstration laisse, dans les deux modes, un coup qui refait le même
     * bonus, sur un plateau où aucun autre bonus ne traîne.
     */
    const val SEED_LINE = 9003L
    const val SEED_BOMB = 9031L
    const val SEED_RAINBOW = 9933L
    const val SEED_BUTTERFLY = 9000L
}

