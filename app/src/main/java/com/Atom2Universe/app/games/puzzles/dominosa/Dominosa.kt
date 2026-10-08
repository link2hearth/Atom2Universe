package com.Atom2Universe.app.games.puzzles.dominosa

import com.Atom2Universe.app.games.kit.Hints
import kotlin.random.Random

/**
 * « Dominosa » de Simon Tatham : la grille contient un jeu complet de dominos double-[n], posés
 * côte à côte puis effacés ; il faut retrouver où passait chaque domino. Chaque paire de
 * nombres n'apparaît qu'une fois.
 *
 * [partner] : la case jumelle de chaque case dans un domino posé par le joueur, ou -1.
 */
class DominosaState(
    val n: Int, val w: Int, val h: Int, val numbers: IntArray, val partner: IntArray,
    /** La case jumelle de chaque case dans la solution, gardée pour les astuces. */
    val solution: IntArray,
) {

    private fun key(a: Int, b: Int) = minOf(numbers[a], numbers[b]) * 16 + maxOf(numbers[a], numbers[b])

    /** Les cases des dominos posés en double. */
    fun duplicates(): Set<Int> {
        val seen = HashMap<Int, MutableList<Int>>()
        for (i in partner.indices) if (partner[i] > i) seen.getOrPut(key(i, partner[i])) { ArrayList() }.addAll(listOf(i, partner[i]))
        return seen.values.filter { it.size > 2 }.flatten().toSet()
    }

    val isSolved: Boolean get() = partner.all { it >= 0 } && duplicates().isEmpty()

    /** Pose (ou retire, s'il y est déjà) le domino a-b ; les dominos qu'il chevauche s'en vont. */
    fun toggle(a: Int, b: Int): DominosaState {
        val next = partner.copyOf()
        if (next[a] == b) { next[a] = -1; next[b] = -1 }
        else {
            for (c in intArrayOf(a, b)) if (next[c] >= 0) { next[next[c]] = -1; next[c] = -1 }
            next[a] = b; next[b] = a
        }
        return DominosaState(n, w, h, numbers, next, solution)
    }

    /** Astuce : un domino mal posé remplacé par le bon, sinon un domino de la solution posé. */
    fun hint(): DominosaState? {
        val i = Hints.pick(partner.size, { partner[it] >= 0 && partner[it] != solution[it] }, { partner[it] < 0 }) ?: return null
        return toggle(i, solution[i])
    }

    fun encode() = "$n,$w,$h:" + numbers.joinToString(",") + ":" + partner.joinToString(",") + ":" + solution.joinToString(",")

    companion object {
        fun decode(text: String): DominosaState {
            val p = text.split(':')
            val (n, w, h) = p[0].split(',').map { it.toInt() }
            return DominosaState(n, w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].split(',').map { it.toInt() }.toIntArray(),
                p[3].split(',').map { it.toInt() }.toIntArray())
        }

        /** Un pavage par dominos au hasard (recherche à rebours, voisins dans le désordre). */
        private fun tiling(w: Int, h: Int, random: Random): IntArray? {
            val mate = IntArray(w * h) { -1 }
            fun fill(): Boolean {
                val c = mate.indexOfFirst { it < 0 }
                if (c < 0) return true
                val options = listOfNotNull(if (c % w < w - 1 && mate[c + 1] < 0) c + 1 else null,
                    if (c / w < h - 1 && mate[c + w] < 0) c + w else null).shuffled(random)
                for (o in options) {
                    mate[c] = o; mate[o] = c
                    if (fill()) return true
                    mate[c] = -1; mate[o] = -1
                }
                return false
            }
            return if (fill()) mate else null
        }

        fun countSolutions(n: Int, w: Int, h: Int, numbers: IntArray, limit: Int = 2): Int {
            val mate = IntArray(w * h) { -1 }
            val used = BooleanArray(16 * 16)
            var found = 0
            var nodes = 0
            fun key(a: Int, b: Int) = minOf(numbers[a], numbers[b]) * 16 + maxOf(numbers[a], numbers[b])
            fun options(c: Int): List<Int> = listOfNotNull(
                if (c % w > 0) c - 1 else null, if (c % w < w - 1) c + 1 else null,
                if (c >= w) c - w else null, if (c < w * h - w) c + w else null
            ).filter { mate[it] < 0 && !used[key(c, it)] }
            fun search() {
                if (found >= limit || ++nodes > 300_000) return
                var best = -1; var bestOpts: List<Int> = emptyList()
                for (c in mate.indices) {
                    if (mate[c] >= 0) continue
                    val o = options(c)
                    if (o.isEmpty()) return
                    if (best < 0 || o.size < bestOpts.size) { best = c; bestOpts = o; if (o.size == 1) break }
                }
                if (best < 0) { found++; return }
                for (o in bestOpts) {
                    val k = key(best, o)
                    mate[best] = o; mate[o] = best; used[k] = true
                    search()
                    mate[best] = -1; mate[o] = -1; used[k] = false
                    if (found >= limit) return
                }
            }
            search()
            return if (nodes > 300_000) limit else found
        }

        fun generate(n: Int, random: Random): DominosaState {
            val w = n + 2; val h = n + 1
            while (true) {
                val mate = tiling(w, h, random) ?: continue
                val pairs = ArrayList<Pair<Int, Int>>()
                for (a in 0..n) for (b in a..n) pairs.add(a to b)
                pairs.shuffle(random)
                val numbers = IntArray(w * h)
                var k = 0
                for (c in 0 until w * h) if (mate[c] > c) {
                    val (a, b) = pairs[k++]
                    if (random.nextBoolean()) { numbers[c] = a; numbers[mate[c]] = b } else { numbers[c] = b; numbers[mate[c]] = a }
                }
                if (countSolutions(n, w, h, numbers) == 1) return DominosaState(n, w, h, numbers, IntArray(w * h) { -1 }, mate)
            }
        }
    }
}
