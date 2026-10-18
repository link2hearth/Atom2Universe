package com.Atom2Universe.app.games.sudoku

/**
 * Classe une grille selon les techniques qu'un joueur doit employer pour la finir, à la manière
 * de « Solo » de Simon Tatham : le nombre d'indices ne dit presque rien de la difficulté, ce qui
 * compte c'est le raisonnement qu'il faut pour avancer.
 *
 * - niveau 1 : singletons nus et cachés (une case n'a plus qu'un chiffre possible, ou un chiffre
 *   n'a plus qu'une case possible dans une ligne, une colonne ou un bloc) ;
 * - niveau 2 : candidats verrouillés (pointage, réclamation), paires nues et cachées ;
 * - niveau 3 : triplets nus et cachés, X-wing, swordfish, XY-wing.
 *
 * Toutes ces techniques sont sûres : une grille que ce correcteur termine n'a qu'une solution.
 */
object SudokuGrader {

    /** Les 27 unités (9 lignes, 9 colonnes, 9 blocs), chacune en indices de cases 0..80. */
    private val units: Array<IntArray> = Array(27) { u ->
        IntArray(9) { i ->
            when {
                u < 9 -> u * 9 + i
                u < 18 -> i * 9 + (u - 9)
                else -> {
                    val b = u - 18
                    (b / 3 * 3 + i / 3) * 9 + b % 3 * 3 + i % 3
                }
            }
        }
    }

    /** Les 20 voisines de chaque case. */
    private val peers: Array<IntArray> = Array(81) { cell ->
        val r = cell / 9
        val c = cell % 9
        val set = LinkedHashSet<Int>()
        for (i in 0 until 9) {
            set.add(r * 9 + i)
            set.add(i * 9 + c)
            set.add((r / 3 * 3 + i / 3) * 9 + c / 3 * 3 + i % 3)
        }
        set.remove(cell)
        set.toIntArray()
    }

    /** Les couples (bloc, ligne ou colonne) qui se croisent sur trois cases, et ces trois cases. */
    private val crossings: List<Triple<Int, Int, IntArray>> = buildList {
        for (a in units.indices) for (b in units.indices) {
            if (a == b) continue
            val inter = units[a].filter { it in units[b] }
            if (inter.size == 3) add(Triple(a, b, inter.toIntArray()))
        }
    }

    private const val ALL = 0x3FE

    /**
     * Le niveau nécessaire (1 à [maxLevel]) pour finir [puzzle] (81 cases, 0 = vide), ou -1 si
     * les techniques jusqu'à [maxLevel] ne suffisent pas.
     */
    fun grade(puzzle: IntArray, maxLevel: Int = 3): Int {
        val values = puzzle.copyOf()
        val cand = IntArray(81)
        for (cell in 0 until 81) {
            if (values[cell] != 0) continue
            var used = 0
            for (p in peers[cell]) if (values[p] != 0) used = used or (1 shl values[p])
            cand[cell] = ALL and used.inv()
            if (cand[cell] == 0) return -1
        }
        var level = 1
        while (true) {
            if (values.none { it == 0 }) return level
            if (singles(values, cand)) {
                if (cand.indices.any { values[it] == 0 && cand[it] == 0 }) return -1
                continue
            }
            if (maxLevel >= 2 && (lockedCandidates(values, cand) || subsets(values, cand, 2))) {
                level = maxOf(level, 2)
                continue
            }
            if (maxLevel >= 3 && (subsets(values, cand, 3) || fish(values, cand, 2) ||
                    fish(values, cand, 3) || xyWing(values, cand))) {
                level = 3
                continue
            }
            return -1
        }
    }

    private fun place(values: IntArray, cand: IntArray, cell: Int, digit: Int) {
        values[cell] = digit
        cand[cell] = 0
        val bit = 1 shl digit
        for (p in peers[cell]) cand[p] = cand[p] and bit.inv()
    }

    private fun singles(values: IntArray, cand: IntArray): Boolean {
        var progress = false
        for (cell in 0 until 81) {
            if (values[cell] == 0 && Integer.bitCount(cand[cell]) == 1) {
                place(values, cand, cell, Integer.numberOfTrailingZeros(cand[cell]))
                progress = true
            }
        }
        for (unit in units) for (d in 1..9) {
            val bit = 1 shl d
            var where = -1
            var count = 0
            for (cell in unit) {
                if (values[cell] == d) { count = -100; break }
                if (values[cell] == 0 && cand[cell] and bit != 0) { where = cell; count++ }
            }
            if (count == 1) {
                place(values, cand, where, d)
                progress = true
            }
        }
        return progress
    }

    /** Pointage (bloc → ligne/colonne) et réclamation (ligne/colonne → bloc). */
    private fun lockedCandidates(values: IntArray, cand: IntArray): Boolean {
        var progress = false
        for ((a, b, inter) in crossings) {
            for (d in 1..9) {
                val bit = 1 shl d
                val inA = units[a].filter { values[it] == 0 && cand[it] and bit != 0 }
                if (inA.isEmpty() || !inA.all { it in inter }) continue
                for (cell in units[b]) {
                    if (cell !in inter && values[cell] == 0 && cand[cell] and bit != 0) {
                        cand[cell] = cand[cell] and bit.inv()
                        progress = true
                    }
                }
            }
        }
        return progress
    }

    /** Sous-ensembles nus et cachés de taille [k] dans chaque unité. */
    private fun subsets(values: IntArray, cand: IntArray, k: Int): Boolean {
        var progress = false
        for (unit in units) {
            val empty = unit.filter { values[it] == 0 }
            if (empty.size <= k) continue
            // Nus : k cases dont l'union des candidats compte k chiffres.
            combinations(empty.size, k) { pick ->
                var union = 0
                for (i in pick) union = union or cand[empty[i]]
                if (Integer.bitCount(union) == k) {
                    val chosen = pick.map { empty[it] }
                    for (cell in empty) if (cell !in chosen && cand[cell] and union != 0) {
                        cand[cell] = cand[cell] and union.inv()
                        progress = true
                    }
                }
            }
            // Cachés : k chiffres qui ne tiennent que dans les mêmes k cases.
            val digits = (1..9).filter { d -> empty.any { cand[it] and (1 shl d) != 0 } }
            if (digits.size <= k) continue
            combinations(digits.size, k) { pick ->
                var mask = 0
                for (i in pick) mask = mask or (1 shl digits[i])
                val cells = empty.filter { cand[it] and mask != 0 }
                if (cells.size == k) {
                    for (cell in cells) if (cand[cell] and mask.inv() and ALL != 0) {
                        cand[cell] = cand[cell] and mask
                        progress = true
                    }
                }
            }
        }
        return progress
    }

    /** X-wing (k = 2) et swordfish (k = 3), sur les lignes puis sur les colonnes. */
    private fun fish(values: IntArray, cand: IntArray, k: Int): Boolean {
        var progress = false
        for (byRow in listOf(true, false)) for (d in 1..9) {
            val bit = 1 shl d
            val lines = ArrayList<Pair<Int, Int>>() // (ligne, masque des positions)
            for (line in 0 until 9) {
                var mask = 0
                for (i in 0 until 9) {
                    val cell = if (byRow) line * 9 + i else i * 9 + line
                    if (values[cell] == 0 && cand[cell] and bit != 0) mask = mask or (1 shl i)
                }
                val n = Integer.bitCount(mask)
                if (n in 2..k) lines.add(line to mask)
            }
            if (lines.size < k) continue
            combinations(lines.size, k) { pick ->
                var union = 0
                for (i in pick) union = union or lines[i].second
                if (Integer.bitCount(union) == k) {
                    val chosen = pick.map { lines[it].first }
                    for (line in 0 until 9) {
                        if (line in chosen) continue
                        for (i in 0 until 9) {
                            if (union and (1 shl i) == 0) continue
                            val cell = if (byRow) line * 9 + i else i * 9 + line
                            if (values[cell] == 0 && cand[cell] and bit != 0) {
                                cand[cell] = cand[cell] and bit.inv()
                                progress = true
                            }
                        }
                    }
                }
            }
        }
        return progress
    }

    private fun xyWing(values: IntArray, cand: IntArray): Boolean {
        var progress = false
        for (pivot in 0 until 81) {
            if (values[pivot] != 0 || Integer.bitCount(cand[pivot]) != 2) continue
            val wings = peers[pivot].filter {
                values[it] == 0 && Integer.bitCount(cand[it]) == 2 &&
                    Integer.bitCount(cand[it] and cand[pivot]) == 1
            }
            for (i in wings.indices) for (j in i + 1 until wings.size) {
                val a = wings[i]
                val b = wings[j]
                val z = cand[a] and cand[b]
                if (Integer.bitCount(z) != 1 || z and cand[pivot] != 0) continue
                if ((cand[a] or cand[b]) and cand[pivot] != cand[pivot]) continue
                for (cell in peers[a]) {
                    if (cell == b || cell == pivot || values[cell] != 0 || cand[cell] and z == 0) continue
                    if (b in peers[cell]) {
                        cand[cell] = cand[cell] and z.inv()
                        progress = true
                    }
                }
            }
        }
        return progress
    }

    /** Appelle [action] pour chaque combinaison de [k] indices parmi 0 until [n]. */
    private inline fun combinations(n: Int, k: Int, action: (IntArray) -> Unit) {
        val pick = IntArray(k) { it }
        while (true) {
            action(pick)
            var i = k - 1
            while (i >= 0 && pick[i] == n - k + i) i--
            if (i < 0) return
            pick[i]++
            for (j in i + 1 until k) pick[j] = pick[j - 1] + 1
        }
    }
}
