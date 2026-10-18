package com.Atom2Universe.app.games.puzzles.galaxies

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Galaxies » de Simon Tatham (Tentai Show) : découper la grille en galaxies, chacune contenant
 * un seul point, autour duquel elle est symétrique (un demi-tour la laisse inchangée).
 *
 * Les points sont en coordonnées doublées : (2x + 1, 2y + 1) est le centre de la case (x, y), un
 * point sur un côté ou un coin a une coordonnée paire. [edges] : murs tracés par le joueur.
 */
class GalaxiesState(val w: Int, val h: Int, val dotX: IntArray, val dotY: IntArray, val edges: IntArray,
    /** Les traits de la solution (1), gardés pour les astuces ; vide si inconnue. */
    val solution: IntArray = IntArray(0),
) {
    val grid by lazy { EdgeGrid(w, h) }

    fun border(e: Int) = grid.cells(e).any { it < 0 }
    fun wall(e: Int) = border(e) || edges[e] == 1

    fun set(list: Collection<Int>, v: Int): GalaxiesState? {
        val next = edges.copyOf()
        var changed = false
        for (e in list) if (!border(e) && next[e] != v) { next[e] = v; changed = true }
        return if (changed) GalaxiesState(w, h, dotX, dotY, next, solution) else null
    }

    /** Astuce : un trait en trop barré, une croix fautive tracée, sinon un trait de la solution. */
    fun hint(): GalaxiesState? {
        if (solution.size != edges.size) return null
        val (e, v) = Hints.edge(edges, solution) { !border(it) && !crossesDot(it) } ?: return null
        val next = edges.copyOf(); next[e] = v
        return GalaxiesState(w, h, dotX, dotY, next, solution)
    }

    /** Une arête qui passe sous un point ne peut pas être un mur. */
    fun crossesDot(e: Int): Boolean {
        val (a, b) = grid.ends(e)
        val mx = (a % (w + 1)) + (b % (w + 1)); val my = (a / (w + 1)) + (b / (w + 1))
        return dotX.indices.any { dotX[it] == mx && dotY[it] == my }
    }

    fun regions(): IntArray {
        val id = IntArray(w * h) { -1 }
        var next = 0
        for (s in 0 until w * h) {
            if (id[s] >= 0) continue
            val stack = ArrayDeque<Int>()
            stack.add(s); id[s] = next
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                for (e in grid.around(c)) {
                    if (wall(e)) continue
                    val (a, b) = grid.cells(e)
                    val o = if (a == c) b else a
                    if (o >= 0 && id[o] < 0) { id[o] = next; stack.add(o) }
                }
            }
            next++
        }
        return id
    }

    /** Les régions fermées qui sont de vraies galaxies (un point, symétriques), pour les colorier. */
    fun goodRegions(): Map<Int, Int> {
        val id = regions()
        val out = HashMap<Int, Int>()
        for (d in dotX.indices) {
            val core = coreCells(w, h, dotX[d], dotY[d])
            val r = id[core[0]]
            if (core.any { id[it] != r }) continue
            val cells = id.indices.filter { id[it] == r }
            val dots = dotX.indices.count { k -> coreCells(w, h, dotX[k], dotY[k]).all { id[it] == r } }
            if (dots != 1) continue
            if (cells.all { c -> mirror(w, h, dotX[d], dotY[d], c).let { m -> m >= 0 && id[m] == r } }) out[r] = d
        }
        return out
    }

    val isSolved: Boolean
        get() {
            val id = regions()
            val good = goodRegions()
            if (!id.all { it in good }) return false
            // Pas de mur qui pend à l'intérieur d'une galaxie.
            for (e in 0 until grid.count) if (!border(e) && edges[e] == 1) {
                val (a, b) = grid.cells(e)
                if (id[a] == id[b]) return false
            }
            return true
        }

    fun encode() = "$w,$h:" + dotX.joinToString(",") + ":" + dotY.joinToString(",") + ":" + edges.joinToString("") + ":" +
        solution.joinToString("")

    companion object {
        fun decode(text: String): GalaxiesState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return GalaxiesState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].split(',').map { it.toInt() }.toIntArray(),
                p[3].map { it - '0' }.toIntArray(), p[4].map { it - '0' }.toIntArray())
        }

        /** Les cases que touche un point (1, 2 ou 4). */
        fun coreCells(w: Int, h: Int, dx: Int, dy: Int): IntArray {
            val xs = if (dx % 2 == 1) listOf((dx - 1) / 2) else listOf(dx / 2 - 1, dx / 2)
            val ys = if (dy % 2 == 1) listOf((dy - 1) / 2) else listOf(dy / 2 - 1, dy / 2)
            return ys.flatMap { y -> xs.map { x -> y * w + x } }.toIntArray()
        }

        /** La case symétrique de [c] autour du point, ou -1 hors de la grille. */
        fun mirror(w: Int, h: Int, dx: Int, dy: Int, c: Int): Int {
            val mx = (2 * dx - (2 * (c % w) + 1) - 1) / 2
            val my = (2 * dy - (2 * (c / w) + 1) - 1) / 2
            return if (mx in 0 until w && my in 0 until h) my * w + mx else -1
        }

        /** g : galaxie de chaque case (-1 inconnue). */
        fun ok(w: Int, h: Int, dotX: IntArray, dotY: IntArray, g: IntArray, c: Int): Boolean {
            val d = g[c]
            if (d >= 0) {
                val m = mirror(w, h, dotX[d], dotY[d], c)
                if (m < 0 || (g[m] >= 0 && g[m] != d)) return false
                // Dans l'autre sens : si la case symétrique autour d'un autre point lui appartient,
                // cette case-ci lui appartient forcément.
                for (k in dotX.indices) {
                    if (k == d) continue
                    val o = mirror(w, h, dotX[k], dotY[k], c)
                    if (o >= 0 && g[o] == k) return false
                }
            }
            // Chaque galaxie touchée doit rester d'un seul tenant (en passant par les inconnues).
            val around = listOfNotNull(if (c % w > 0) c - 1 else null, if (c % w < w - 1) c + 1 else null,
                if (c >= w) c - w else null, if (c < w * h - w) c + w else null)
            for (k in (around + c).map { g[it] }.filter { it >= 0 }.distinct()) {
                val core = coreCells(w, h, dotX[k], dotY[k])
                val seen = BooleanArray(w * h)
                val stack = ArrayDeque<Int>()
                for (s in core) { seen[s] = true; stack.add(s) }
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    for (n in listOfNotNull(if (i % w > 0) i - 1 else null, if (i % w < w - 1) i + 1 else null,
                            if (i >= w) i - w else null, if (i < w * h - w) i + w else null)) {
                        if (!seen[n] && (g[n] == k || g[n] < 0)) { seen[n] = true; stack.add(n) }
                    }
                }
                if (g.indices.any { g[it] == k && !seen[it] }) return false
            }
            return true
        }

        /** Nombre de solutions (plafonné à 2) ; [budget] = nœuds de recherche permis. */
        fun count(w: Int, h: Int, dotX: IntArray, dotY: IntArray, init: IntArray, budget: Int) =
            GridSearch.count(w * h, dotX.size, init, budget = budget,
                complete = { g -> g.indices.all { ok(w, h, dotX, dotY, g, it) } }) { g, c -> ok(w, h, dotX, dotY, g, c) }

        fun generate(w: Int, h: Int, random: Random): GalaxiesState {
            var attempts = 0
            while (true) {
                attempts++
                val owner = IntArray(w * h) { -1 }
                val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
                while (true) {
                    val free = owner.indices.filter { owner[it] < 0 }
                    if (free.isEmpty()) break
                    val c = free.random(random)
                    val x = c % w; val y = c / w
                    // Point au centre de la case, sur un côté ou sur un coin, si les cases sont libres.
                    val choices = listOf(2 * x + 1 to 2 * y + 1, 2 * x + 2 to 2 * y + 1, 2 * x + 1 to 2 * y + 2, 2 * x + 2 to 2 * y + 2)
                        .filter { (dx, dy) -> dx < 2 * w && dy < 2 * h && coreCells(w, h, dx, dy).all { it in owner.indices && owner[it] < 0 } }
                    val (dx, dy) = choices.random(random)
                    val d = xs.size
                    xs.add(dx); ys.add(dy)
                    val cells = coreCells(w, h, dx, dy).toMutableList()
                    cells.forEach { owner[it] = d }
                    val target = 2 + random.nextInt(8)
                    var tries = 0
                    while (cells.size < target && tries++ < 30) {
                        val from = cells.random(random)
                        val n = listOfNotNull(if (from % w > 0) from - 1 else null, if (from % w < w - 1) from + 1 else null,
                            if (from >= w) from - w else null, if (from < w * h - w) from + w else null).random(random)
                        val m = mirror(w, h, dx, dy, n)
                        if (owner[n] >= 0 || m < 0 || owner[m] >= 0) continue
                        owner[n] = d; owner[m] = d; cells.add(n); if (m != n) cells.add(m)
                    }
                }
                val dotX = xs.toIntArray(); val dotY = ys.toIntArray()
                val init = IntArray(w * h) { -1 }
                for (d in dotX.indices) coreCells(w, h, dotX[d], dotY[d]).forEach { init[it] = d }
                val n = count(w, h, dotX, dotY, init, 300)
                if (n == 1 || attempts > 40) {
                    val grid = EdgeGrid(w, h)
                    val walls = IntArray(grid.count) { e -> grid.cells(e).let { (a, b) -> if (a >= 0 && b >= 0 && owner[a] != owner[b]) 1 else 0 } }
                    return GalaxiesState(w, h, dotX, dotY, IntArray(grid.count), walls)
                }
            }
        }
    }
}
