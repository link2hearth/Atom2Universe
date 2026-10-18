package com.Atom2Universe.app.games.minesweeper

/**
 * Joue une grille comme un joueur prudent, à la manière du générateur de « Mines » de Simon
 * Tatham : on ne garde que les grilles qu'on peut finir **sans jamais deviner**, à partir de la
 * première case touchée.
 *
 * Les déductions sont celles qu'un joueur fait à l'œil :
 * - un chiffre qui a déjà toutes ses mines libère ses autres voisines, un chiffre qui a autant de
 *   voisines cachées que de mines manquantes les a toutes en mines ;
 * - deux chiffres voisins se comparent (le motif 1-2-1, les sous-ensembles) ;
 * - en fin de partie, le compteur de mines restantes tranche.
 */
internal object MinesweeperSolver {

    private class Constraint(val center: Int, val cells: IntArray, val need: Int)

    fun solvable(cols: Int, rows: Int, mines: BooleanArray, start: Int): Boolean {
        val n = cols * rows
        if (mines[start]) return false
        val total = mines.count { it }
        val neighbors = Array(n) { cell ->
            val r = cell / cols
            val c = cell % cols
            buildList {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols) add(nr * cols + nc)
                }
            }.toIntArray()
        }
        val number = IntArray(n) { cell -> neighbors[cell].count { mines[it] } }
        val revealed = BooleanArray(n)
        val flagged = BooleanArray(n)
        var revealedCount = 0
        val stack = ArrayDeque<Int>()

        fun open(cell: Int) {
            if (revealed[cell] || flagged[cell]) return
            stack.addLast(cell)
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                if (revealed[c]) continue
                revealed[c] = true
                revealedCount++
                if (number[c] == 0) for (nb in neighbors[c]) if (!revealed[nb] && !flagged[nb]) stack.addLast(nb)
            }
        }

        open(start)
        val mark = IntArray(n)
        var stamp = 0
        while (revealedCount < n - total) {
            var progress = false
            val constraints = ArrayList<Constraint>()
            for (cell in 0 until n) {
                if (!revealed[cell] || number[cell] == 0) continue
                var flags = 0
                val unknown = ArrayList<Int>(8)
                for (nb in neighbors[cell]) {
                    if (flagged[nb]) flags++ else if (!revealed[nb]) unknown.add(nb)
                }
                if (unknown.isEmpty()) continue
                constraints.add(Constraint(cell, unknown.toIntArray(), number[cell] - flags))
            }
            // Règles simples : tout sûr, ou tout miné.
            for (k in constraints) {
                if (k.need == 0) {
                    for (cell in k.cells) if (!revealed[cell]) { open(cell); progress = true }
                } else if (k.need == k.cells.size) {
                    for (cell in k.cells) if (!flagged[cell]) { flagged[cell] = true; progress = true }
                }
            }
            if (!progress) {
                // Comparaison de deux chiffres proches : si A ne peut loger ses mines qu'en
                // remplissant la part commune au maximum que B autorise, le reste de B est sûr
                // et le reste de A est miné.
                val byCenter = HashMap<Int, Constraint>(constraints.size * 2)
                for (k in constraints) byCenter[k.center] = k
                outer@ for (a in constraints) {
                    val ar = a.center / cols
                    val ac = a.center % cols
                    for (dr in -2..2) for (dc in -2..2) {
                        if (dr == 0 && dc == 0) continue
                        val br = ar + dr
                        val bc = ac + dc
                        if (br !in 0 until rows || bc !in 0 until cols) continue
                        val b = byCenter[br * cols + bc] ?: continue
                        stamp++
                        for (cell in b.cells) mark[cell] = stamp
                        var inter = 0
                        for (cell in a.cells) if (mark[cell] == stamp) inter++
                        if (inter == 0) continue
                        val onlyA = a.cells.size - inter
                        if (a.need - onlyA != b.need) continue
                        for (cell in b.cells) if (cell !in a.cells && !revealed[cell] && !flagged[cell]) {
                            open(cell); progress = true
                        }
                        for (cell in a.cells) if (mark[cell] != stamp && !flagged[cell]) {
                            flagged[cell] = true; progress = true
                        }
                        if (progress) break@outer
                    }
                }
            }
            if (!progress) {
                // Le compteur de mines : plus aucune, ou autant que de cases cachées.
                val left = total - flagged.count { it }
                val hidden = (0 until n).filter { !revealed[it] && !flagged[it] }
                if (left == 0) { hidden.forEach { open(it) }; progress = hidden.isNotEmpty() }
                else if (left == hidden.size) { hidden.forEach { flagged[it] = true }; progress = true }
            }
            if (!progress) return false
        }
        return true
    }
}
