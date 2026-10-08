package com.Atom2Universe.app.games.kit

/**
 * Le journal des coups d'un puzzle mélangé par des coups au hasard (Sixteen, Netslide, Twiddle) :
 * depuis la grille rangée, le mélange puis chaque coup du joueur. Le rembobiner résout la grille,
 * c'est la démonstration de l'astuce.
 *
 * Un coup est une paire d'entiers (quoi, combien) rangée à plat dans un IntArray. Deux coups qui
 * portent sur la même chose s'additionnent ([modulo] : au bout d'un tour complet, il ne reste
 * rien), pour que le rembobinage ne refasse pas les allers-retours.
 */
object MoveLog {
    fun push(log: IntArray, what: Int, amount: Int, modulo: Int): IntArray {
        val n = log.size
        if (n >= 2 && log[n - 2] == what) {
            val sum = Math.floorMod(log[n - 1] + amount, modulo)
            if (sum == 0) return log.copyOf(n - 2)
            return log.copyOf().also { it[n - 1] = sum }
        }
        val a = Math.floorMod(amount, modulo)
        if (a == 0) return log
        return log.copyOf(n + 2).also { it[n] = what; it[n + 1] = a }
    }

    fun encode(log: IntArray) = log.joinToString(",")
    fun decode(text: String): IntArray = if (text.isEmpty()) IntArray(0) else text.split(',').map { it.toInt() }.toIntArray()
}
