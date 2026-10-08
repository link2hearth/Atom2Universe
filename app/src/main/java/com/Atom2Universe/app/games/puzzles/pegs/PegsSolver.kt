package com.Atom2Universe.app.games.puzzles.pegs

/**
 * Le solveur du solitaire à fiches, pour la démonstration : une recherche en profondeur qui
 * retient les positions déjà reconnues sans issue. Deux choses la rendent assez rapide :
 * - une position se code sur un entier de 64 bits (un bit par trou du plateau) ;
 * - une position et ses reflets (le plateau tourné ou retourné, s'il est symétrique) sont la même
 *   position : on ne retient que la plus petite, ce qui divise jusqu'à huit fois le travail.
 * Au-delà de [budget] positions, on abandonne (null). Plateaux de plus de 64 trous : null.
 * La croix et les plateaux au hasard se résolvent vite ; l'octogone en début de partie est trop
 * vaste (des dizaines de millions de positions), il ne se résout qu'une fois bien entamé.
 */
object PegsSolver {

    /** Un saut : de [from] vers [to], par-dessus la fiche du milieu. */
    class Jump(val from: Int, val to: Int)

    fun solve(state: PegsState, budget: Int = 300_000): List<Jump>? {
        val w = state.w; val h = state.h
        val holes = state.cells.indices.filter { state.cells[it] != PegsState.WALL }
        if (holes.size > 64) return null
        val bit = IntArray(w * h) { -1 }
        holes.forEachIndexed { k, c -> bit[c] = k }

        // Les sauts possibles du plateau.
        val jumps = ArrayList<IntArray>()
        for (a in holes) for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val x = a % w; val y = a / w
            val bx = x + 2 * dx; val by = y + 2 * dy
            if (bx !in 0 until w || by !in 0 until h) continue
            val m = (y + dy) * w + x + dx; val b = by * w + bx
            if (bit[m] < 0 || bit[b] < 0) continue
            jumps.add(intArrayOf(bit[a], bit[m], bit[b], a, b))
        }
        val js = jumps.toTypedArray()

        // Les symétries du carré qui laissent le plateau inchangé, en permutations de bits.
        val perms = ArrayList<IntArray>()
        if (w == h) for (t in 0 until 8) {
            val perm = IntArray(holes.size)
            var ok = true
            for ((k, c) in holes.withIndex()) {
                val x = c % w; val y = c / w
                val (nx, ny) = when (t) {
                    0 -> x to y; 1 -> (w - 1 - y) to x; 2 -> (w - 1 - x) to (h - 1 - y); 3 -> y to (w - 1 - x)
                    4 -> (w - 1 - x) to y; 5 -> x to (h - 1 - y); 6 -> y to x; else -> (w - 1 - y) to (h - 1 - x)
                }
                val target = bit[ny * w + nx]
                if (target < 0) { ok = false; break }
                perm[k] = target
            }
            if (ok && t > 0) perms.add(perm)
        }
        fun canonical(pos: Long): Long {
            var best = pos
            for (perm in perms) {
                var q = 0L
                var p = pos
                while (p != 0L) {
                    val k = java.lang.Long.numberOfTrailingZeros(p)
                    q = q or (1L shl perm[k])
                    p = p and (p - 1)
                }
                if (q < best) best = q
            }
            return best
        }

        var pos = 0L
        for (c in holes) if (state.cells[c] == PegsState.PEG) pos = pos or (1L shl bit[c])
        val dead = HashSet<Long>()
        val path = ArrayList<Jump>()
        var nodes = 0

        fun search(): Boolean {
            if (java.lang.Long.bitCount(pos) == 1) return true
            if (++nodes > budget) return false
            val key = canonical(pos)
            if (key in dead) return false
            for (j in js) {
                val a = 1L shl j[0]; val m = 1L shl j[1]; val b = 1L shl j[2]
                if (pos and a == 0L || pos and m == 0L || pos and b != 0L) continue
                pos = pos xor a xor m xor b
                path.add(Jump(j[3], j[4]))
                if (search()) return true
                path.removeAt(path.size - 1)
                pos = pos xor a xor m xor b
                if (nodes > budget) return false
            }
            dead.add(key)
            return false
        }
        return if (search()) path else null
    }
}
