package com.Atom2Universe.app.games.kit

/**
 * Un solveur générique pour les puzzles de grille : chaque case prend une valeur parmi
 * 0 until [domain] (-1 = inconnue), et [ok] dit si la grille partielle reste possible après avoir
 * posé la case donnée. Il sert aux générateurs à ne garder que les grilles à solution unique,
 * comme le fait Simon Tatham.
 *
 * À chaque nœud : on essaie chaque valeur de chaque case inconnue ; une case qui n'en admet
 * qu'une la prend (propagation), une case qui n'en admet aucune coupe la branche. Puis on
 * branche sur la case la plus contrainte.
 */
object GridSearch {

    /**
     * Le nombre de solutions, plafonné à [limit]. Avec [budget] = 1, seule la propagation est
     * permise : la grille n'est « unique » que si elle se résout case par case, sans essai —
     * des grilles qui se raisonnent, et une vérification bien plus rapide. Si le [budget] de nœuds est épuisé, on répond
     * [limit] : « je n'ai pas pu prouver l'unicité », ce qui fait rejeter la grille.
     */
    fun count(
        cells: Int,
        domain: Int,
        initial: IntArray,
        limit: Int = 2,
        budget: Int = 20_000,
        complete: (IntArray) -> Boolean = { true },
        ok: (IntArray, Int) -> Boolean,
    ): Int {
        var found = 0
        var nodes = 0
        var exhausted = false

        fun search(grid: IntArray) {
            if (found >= limit || exhausted) return
            if (++nodes > budget) { exhausted = true; return }
            val g = grid.copyOf()
            // Propagation.
            var changed = true
            var bestCell = -1
            var bestOptions = Int.MAX_VALUE
            while (changed) {
                changed = false
                bestCell = -1; bestOptions = Int.MAX_VALUE
                for (c in 0 until cells) {
                    if (g[c] >= 0) continue
                    var options = 0
                    var last = -1
                    for (v in 0 until domain) {
                        g[c] = v
                        if (ok(g, c)) { options++; last = v }
                    }
                    g[c] = -1
                    if (options == 0) return
                    if (options == 1) { g[c] = last; changed = true }
                    else if (options < bestOptions) { bestOptions = options; bestCell = c }
                }
            }
            if (bestCell < 0) {
                if (complete(g)) found++
                return
            }
            for (v in 0 until domain) {
                g[bestCell] = v
                if (ok(g, bestCell)) search(g)
                if (found >= limit || exhausted) return
            }
        }

        search(initial)
        return if (exhausted) limit else found
    }
}
