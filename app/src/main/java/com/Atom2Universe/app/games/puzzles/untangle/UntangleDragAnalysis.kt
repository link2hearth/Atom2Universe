package com.Atom2Universe.app.games.puzzles.untangle

/**
 * Pendant un glissé, seules les arêtes du point tenu peuvent changer de croisement.
 * Les autres paires sont analysées une fois ; les tableaux servent à tout le geste.
 */
class UntangleDragAnalysis(state: UntangleState, private val point: Int) {
    private val xs = state.xs.copyOf()
    private val ys = state.ys.copyOf()
    private val edges = state.edges
    private val count = edges.size / 2
    private val moving = BooleanArray(count) { edges[it * 2] == point || edges[it * 2 + 1] == point }
    private val movingEdges = (0 until count).filter { moving[it] }.toIntArray()
    private val fixed = BooleanArray(count)
    private val crossed = BooleanArray(count)
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    init {
        for (i in 0 until count) if (!moving[i]) for (j in i + 1 until count) {
            if (!moving[j] && cross(i, j)) { fixed[i] = true; fixed[j] = true }
        }
    }

    private fun cross(i: Int, j: Int) = UntangleState.cross(xs, ys,
        edges[i * 2], edges[i * 2 + 1], edges[j * 2], edges[j * 2 + 1])

    /** Résultat réutilisé : l'appelant le lit sans le modifier ni le conserver comme état de jeu. */
    fun at(x: Float, y: Float): BooleanArray {
        val nx = x.coerceIn(0f, 1f); val ny = y.coerceIn(0f, 1f)
        if (nx == lastX && ny == lastY) return crossed
        lastX = nx; lastY = ny
        xs[point] = nx; ys[point] = ny
        fixed.copyInto(crossed)
        for (i in movingEdges) for (j in 0 until count) {
            if (j == i || (moving[j] && j < i)) continue
            if (cross(i, j)) { crossed[i] = true; crossed[j] = true }
        }
        return crossed
    }
}
