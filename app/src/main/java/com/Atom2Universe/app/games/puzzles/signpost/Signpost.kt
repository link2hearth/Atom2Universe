package com.Atom2Universe.app.games.puzzles.signpost

import com.Atom2Universe.app.games.kit.Hints
import kotlin.random.Random

/**
 * « Signpost » de Simon Tatham : relier toutes les cases en un seul chemin numéroté de 1 à n.
 * Chaque case porte une flèche qui montre la direction de la suivante (à n'importe quelle
 * distance dans cette direction) ; la dernière porte une étoile. Quelques numéros sont donnés.
 *
 * [dirs] : direction 0..7 de chaque case (-1 pour la dernière). [givens] : 0 = pas de numéro.
 * [next] : liens posés par le joueur (-1 = aucun).
 */
class SignpostState(
    val w: Int, val h: Int, val dirs: IntArray, val givens: IntArray, val next: IntArray,
    /** La case suivante de chaque case dans la solution (-1 pour la dernière), pour les astuces. */
    val solution: IntArray,
) {
    val n get() = w * h

    fun prev(i: Int): Int = next.indexOf(i)

    /** Vrai si [b] est dans la direction de la flèche de [a]. */
    fun onRay(a: Int, b: Int): Boolean = rayOf(w, h, dirs, a).contains(b)

    fun link(a: Int, b: Int): SignpostState? {
        if (a == b || dirs[a] < 0 || !onRay(a, b)) return null
        val nx = next.copyOf()
        val p = nx.indexOf(b)
        if (p >= 0) nx[p] = -1
        nx[a] = b
        // Pas de boucle : on refuse un lien qui referme une chaîne sur elle-même.
        var c = b; var steps = 0
        while (c >= 0 && steps++ <= n) { if (c == a) return null; c = nx[c] }
        return SignpostState(w, h, dirs, givens, nx, solution)
    }

    /**
     * Astuce : un lien faux est d'abord défait ; sinon un lien de la solution est posé (tous
     * les autres étant justes, il ne peut pas fermer de boucle).
     */
    fun hint(): SignpostState? {
        val a = Hints.pick(n, { next[it] >= 0 && next[it] != solution[it] }, { next[it] < 0 && solution[it] >= 0 }) ?: return null
        val nx = next.copyOf()
        if (next[a] >= 0) { nx[a] = -1; return SignpostState(w, h, dirs, givens, nx, solution) }
        val b = solution[a]
        val p = nx.indexOf(b); if (p >= 0) nx[p] = -1
        nx[a] = b
        return SignpostState(w, h, dirs, givens, nx, solution)
    }

    fun unlink(a: Int): SignpostState {
        val nx = next.copyOf(); nx[a] = -1
        val p = nx.indexOf(a); if (p >= 0) nx[p] = -1
        return SignpostState(w, h, dirs, givens, nx, solution)
    }

    /**
     * Le numéro de chaque case quand on peut le déduire d'un numéro donné sur sa chaîne, sinon 0.
     * [chain] reçoit l'identifiant de la chaîne (la case de tête), pour colorier les chaînes libres.
     */
    fun numbering(chain: IntArray? = null): IntArray {
        val out = IntArray(n)
        for (head in 0 until n) {
            if (prev(head) >= 0) continue
            val cells = ArrayList<Int>()
            var c = head
            while (c >= 0 && cells.size <= n) { cells.add(c); c = next[c] }
            val anchor = cells.indexOfFirst { givens[it] > 0 }
            for ((k, cell) in cells.withIndex()) {
                chain?.set(cell, head)
                if (anchor >= 0) out[cell] = givens[cells[anchor]] - anchor + k
            }
        }
        return out
    }

    val isSolved: Boolean
        get() {
            val num = numbering()
            if (num.sorted() != (1..n).toList()) return false
            if ((0 until n).any { givens[it] != 0 && givens[it] != num[it] }) return false
            // Une seule chaîne : chaque numéro mène bien au suivant.
            return (0 until n).all { num[it] == n || (next[it] >= 0 && num[next[it]] == num[it] + 1) }
        }

    fun encode() = "$w,$h:" + dirs.joinToString(",") + ":" + givens.joinToString(",") + ":" + next.joinToString(",") + ":" +
        solution.joinToString(",")

    companion object {
        val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

        fun decode(text: String): SignpostState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            fun ints(s: String) = s.split(',').map { it.toInt() }.toIntArray()
            return SignpostState(w, h, ints(p[1]), ints(p[2]), ints(p[3]), ints(p[4]))
        }

        fun rayOf(w: Int, h: Int, dirs: IntArray, a: Int): List<Int> {
            val d = dirs[a]
            if (d < 0) return emptyList()
            val out = ArrayList<Int>()
            var x = a % w + DX[d]; var y = a / w + DY[d]
            while (x in 0 until w && y in 0 until h) { out.add(y * w + x); x += DX[d]; y += DY[d] }
            return out
        }

        /** Un chemin qui passe par toutes les cases, chaque pas en ligne droite ou en diagonale. */
        private fun randomPath(w: Int, h: Int, random: Random): IntArray? {
            val n = w * h
            val path = IntArray(n)
            val used = BooleanArray(n)
            var nodes = 0
            fun moves(c: Int): List<Int> {
                val out = ArrayList<Int>()
                for (d in 0..7) {
                    var x = c % w + DX[d]; var y = c / w + DY[d]
                    while (x in 0 until w && y in 0 until h) {
                        if (!used[y * w + x]) out.add(y * w + x)
                        x += DX[d]; y += DY[d]
                    }
                }
                return out
            }
            fun extend(k: Int): Boolean {
                if (k == n) return true
                if (++nodes > 20_000) return false
                // Heuristique de Warnsdorff : d'abord les cases qui ont le moins d'issues.
                val options = moves(path[k - 1]).shuffled(random).sortedBy { o -> used[o] = true; val m = moves(o).size; used[o] = false; m }
                for (o in options) {
                    used[o] = true; path[k] = o
                    if (extend(k + 1)) return true
                    used[o] = false
                }
                return false
            }
            path[0] = random.nextInt(n); used[path[0]] = true
            return if (extend(1)) path else null
        }

        /** Nombre de chemins (jusqu'à 2) qui suivent les flèches et respectent les numéros donnés. */
        fun countPaths(w: Int, h: Int, dirs: IntArray, givens: IntArray, budget: Int = 100_000): Int {
            val n = w * h
            val posOf = IntArray(n + 1) { -1 }
            for (i in 0 until n) if (givens[i] > 0) posOf[givens[i]] = i
            val start = posOf[1]
            if (start < 0) return 2
            val used = BooleanArray(n)
            var found = 0; var nodes = 0
            val rays = Array(n) { rayOf(w, h, dirs, it) }
            fun go(c: Int, k: Int) {
                if (found >= 2 || ++nodes > budget) return
                if (k == n) { found++; return }
                val want = posOf[k + 1]
                for (o in rays[c]) {
                    if (used[o]) continue
                    if (want >= 0 && o != want) continue
                    if (givens[o] > 0 && givens[o] != k + 1) continue
                    used[o] = true; go(o, k + 1); used[o] = false
                    if (found >= 2) return
                }
            }
            used[start] = true
            go(start, 1)
            return if (nodes > budget) 2 else found
        }

        fun generate(w: Int, h: Int, random: Random): SignpostState {
            val n = w * h
            while (true) {
                val path = randomPath(w, h, random) ?: continue
                val dirs = IntArray(n) { -1 }
                for (k in 0 until n - 1) {
                    val a = path[k]; val b = path[k + 1]
                    val dx = Integer.signum(b % w - a % w); val dy = Integer.signum(b / w - a / w)
                    dirs[a] = (0..7).first { DX[it] == dx && DY[it] == dy }
                }
                val givens = IntArray(n)
                givens[path[0]] = 1; givens[path[n - 1]] = n
                // On ajoute des numéros au hasard jusqu'à ce qu'un seul chemin reste possible…
                val order = (1 until n - 1).shuffled(random)
                var k = 0
                while (countPaths(w, h, dirs, givens) != 1 && k < order.size) {
                    givens[path[order[k]]] = order[k] + 1; k++
                }
                // … puis on retire ceux qui ne servent plus.
                for (j in (1 until n - 1).shuffled(random)) {
                    val cell = path[j]
                    if (givens[cell] == 0) continue
                    givens[cell] = 0
                    if (countPaths(w, h, dirs, givens) != 1) givens[cell] = j + 1
                }
                val solution = IntArray(n) { -1 }
                for (k in 0 until n - 1) solution[path[k]] = path[k + 1]
                return SignpostState(w, h, dirs, givens, IntArray(n) { -1 }, solution)
            }
        }
    }
}
