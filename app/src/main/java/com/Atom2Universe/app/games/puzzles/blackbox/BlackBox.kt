package com.Atom2Universe.app.games.puzzles.blackbox

import kotlin.random.Random

/**
 * « Black Box » de Simon Tatham : des boules sont cachées dans la boîte. On tire des rayons
 * depuis les bords et on observe ce qu'ils deviennent :
 * - absorbé (« H ») s'il rencontre une boule de face ;
 * - dévié d'un quart de tour quand une boule frôle sa route en diagonale, renvoyé s'il y en a
 *   deux ; renvoyé aussi (« R ») si une boule frôle son point d'entrée ;
 * - sinon il ressort par un autre point du bord (les deux points prennent le même numéro).
 *
 * Les ports sont numérotés tout autour : haut (0..w-1), droite, bas, gauche.
 * [results] : pour chaque port, [UNFIRED], [HIT], [REFLECT] ou le numéro de la paire.
 */
class BlackBoxState(
    val w: Int,
    val h: Int,
    val balls: BooleanArray,
    val guesses: BooleanArray,
    val results: IntArray,
    val pairs: Int,
    val checked: Boolean,
) {
    val ports get() = 2 * (w + h)
    val ballCount get() = balls.count { it }

    fun fire(port: Int): BlackBoxState? {
        if (results[port] != UNFIRED || checked) return null
        val out = results.copyOf()
        var p = pairs
        when (val end = trace(port)) {
            HIT -> out[port] = HIT
            REFLECT -> out[port] = REFLECT
            else -> {
                if (end == port) out[port] = REFLECT
                else { p++; out[port] = p; out[end] = p }
            }
        }
        return BlackBoxState(w, h, balls, guesses, out, p, checked)
    }

    fun toggleGuess(i: Int): BlackBoxState? {
        if (checked) return null
        val g = guesses.copyOf(); g[i] = !g[i]
        return BlackBoxState(w, h, balls, g, results, pairs, checked)
    }

    fun check() = BlackBoxState(w, h, balls, guesses, results, pairs, true)

    /** Astuce : un noyau supposé à tort est retiré, sinon un vrai noyau est posé. */
    fun hint(): BlackBoxState? {
        if (checked) return null
        val i = com.Atom2Universe.app.games.kit.Hints.pick(w * h, { guesses[it] && !balls[it] }, { !guesses[it] && balls[it] }) ?: return null
        return toggleGuess(i)
    }

    /** Un tirage ne peut rien dire de plus que la vraie disposition : est-elle indiscernable ? */
    val isSolved: Boolean get() = checked && consistentWith(guesses)
    val isLost: Boolean get() = checked && !consistentWith(guesses)

    /** Vrai si les boules supposées donnent les mêmes résultats sur tous les ports. */
    fun consistentWith(guess: BooleanArray): Boolean {
        if (guess.count { it } != ballCount) return false
        val other = BlackBoxState(w, h, guess, guess, IntArray(ports) { UNFIRED }, 0, false)
        for (port in 0 until ports) {
            val a = trace(port); val b = other.trace(port)
            if (a != b) return false
        }
        return true
    }

    /** Position de départ (hors de la boîte) et direction d'un port. */
    private fun entry(port: Int): IntArray = when {
        port < w -> intArrayOf(port, -1, 0, 1)
        port < w + h -> intArrayOf(w, port - w, -1, 0)
        port < 2 * w + h -> intArrayOf(w - 1 - (port - w - h), h, 0, -1)
        else -> intArrayOf(-1, h - 1 - (port - 2 * w - h), 1, 0)
    }

    private fun portAt(x: Int, y: Int): Int = when {
        y < 0 -> x
        x >= w -> w + y
        y >= h -> w + h + (w - 1 - x)
        else -> 2 * w + h + (h - 1 - y)
    }

    private fun ball(x: Int, y: Int) = x in 0 until w && y in 0 until h && balls[y * w + x]

    /** Le port de sortie du rayon, ou [HIT] / [REFLECT]. */
    fun trace(port: Int): Int {
        val (sx, sy, sdx, sdy) = entry(port)
        var x = sx; var y = sy; var dx = sdx; var dy = sdy
        var first = true
        var guard = 0
        while (guard++ < 4 * (w + h) * (w + h)) {
            val ax = x + dx; val ay = y + dy
            val inside = x in 0 until w && y in 0 until h
            if (!first && !inside) return portAt(x, y)
            if (ax !in -1..w || ay !in -1..h) return portAt(x, y)
            if (ball(ax, ay)) return HIT
            // Les deux cases en diagonale devant.
            val lx = ax + dy; val ly = ay - dx
            val rx = ax - dy; val ry = ay + dx
            val bl = ball(lx, ly); val br = ball(rx, ry)
            if (bl || br) {
                if (first) return REFLECT
                if (bl && br) { dx = -dx; dy = -dy }
                else if (bl) { val t = dx; dx = -dy; dy = t }
                else { val t = dx; dx = dy; dy = -t }
                continue
            }
            x = ax; y = ay
            first = false
            if (x !in 0 until w || y !in 0 until h) return portAt(x, y)
        }
        return REFLECT
    }

    fun encode() = "$w,$h,$pairs,${if (checked) 1 else 0}:" + balls.joinToString("") { if (it) "1" else "0" } + ":" +
        guesses.joinToString("") { if (it) "1" else "0" } + ":" + results.joinToString(",")

    companion object {
        const val UNFIRED = 0
        const val HIT = -1
        const val REFLECT = -2

        fun decode(text: String): BlackBoxState {
            val p = text.split(':')
            val (w, h, pairs, checked) = p[0].split(',').map { it.toInt() }
            return BlackBoxState(w, h, BooleanArray(w * h) { p[1][it] == '1' }, BooleanArray(w * h) { p[2][it] == '1' },
                p[3].split(',').map { it.toInt() }.toIntArray(), pairs, checked == 1)
        }

        fun generate(w: Int, h: Int, balls: Int, random: Random): BlackBoxState {
            val b = BooleanArray(w * h)
            (0 until w * h).shuffled(random).take(balls).forEach { b[it] = true }
            return BlackBoxState(w, h, b, BooleanArray(w * h), IntArray(2 * (w + h)), 0, false)
        }
    }
}
