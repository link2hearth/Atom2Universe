package com.Atom2Universe.app.games.kit

/**
 * Le choix commun des astuces « une case de plus » : d'abord corriger une case fausse (le joueur
 * s'est trompé, c'est ce qui l'aide le plus), sinon révéler une case encore indécise, au hasard.
 * null : tout est déjà juste, il n'y a rien à montrer (et rien n'est payé).
 */
object Hints {
    fun pick(size: Int, isWrong: (Int) -> Boolean, isOpen: (Int) -> Boolean): Int? =
        (0 until size).filter(isWrong).ifEmpty { (0 until size).filter(isOpen) }.randomOrNull()

    /**
     * Pour les jeux où l'on trace sur des arêtes (0 rien, 1 trait, 2 croix) : un trait en trop
     * devient une croix, une croix fautive devient un trait, sinon un trait de la solution est
     * tracé. Rend l'arête et sa nouvelle valeur.
     */
    fun edge(edges: IntArray, solution: IntArray, usable: (Int) -> Boolean): Pair<Int, Int>? {
        val e = pick(edges.size,
            { usable(it) && ((edges[it] == 1 && solution[it] != 1) || (edges[it] == 2 && solution[it] == 1)) },
            { usable(it) && edges[it] == 0 && solution[it] == 1 }) ?: return null
        return e to if (solution[e] == 1) 1 else 2
    }
}
