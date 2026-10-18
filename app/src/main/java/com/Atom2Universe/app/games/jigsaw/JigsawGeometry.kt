package com.Atom2Universe.app.games.jigsaw

import kotlin.random.Random

data class PuzzleCurve(val start: PuzzlePoint, val control1: PuzzlePoint, val control2: PuzzlePoint, val end: PuzzlePoint) {
    fun reversed() = PuzzleCurve(end, control2, control1, start)
}

/**
 * Tab geometry adapted from Draradech/jigsaw (CC0, except its save function, not used here).
 * https://github.com/Draradech/jigsaw/blob/master/jigsaw.html
 * Every internal edge is generated once, then reused in reverse by the adjoining piece.
 */
class JigsawGeometry(val size: JigsawGrid, seed: Int) {
    constructor(size: JigsawSize, seed: Int) : this(JigsawGrid.portrait(size), seed)
    private val random = Random(seed)
    private val horizontal = Array(size.rows - 1) { row ->
        Array(size.columns) { col -> edge(PuzzlePoint(col * size.cellWidth, (row + 1) * size.cellHeight),
            PuzzlePoint((col + 1) * size.cellWidth, (row + 1) * size.cellHeight)) }
    }
    private val vertical = Array(size.rows) { row ->
        Array(size.columns - 1) { col -> edge(PuzzlePoint((col + 1) * size.cellWidth, row * size.cellHeight),
            PuzzlePoint((col + 1) * size.cellWidth, (row + 1) * size.cellHeight)) }
    }

    private fun edge(origin: PuzzlePoint, end: PuzzlePoint): List<PuzzleCurve> {
        val along = end - origin
        val normal = PuzzlePoint(-along.y, along.x)
        val sign = if (random.nextBoolean()) 1 else -1
        val t = random.nextFloat() * .035f + .085f
        fun jitter() = (random.nextFloat() - .5f) * .065f
        val a = jitter(); val b = jitter(); val c = jitter(); val d = jitter(); val e = jitter()
        fun point(x: Float, y: Float) = PuzzlePoint(origin.x + along.x * x + normal.x * y * sign,
            origin.y + along.y * x + normal.y * y * sign)
        val p = listOf(point(0f, 0f), point(.2f, a), point(.5f + b + d, -t + c),
            point(.5f - t + b, t + c), point(.5f - 2 * t + b - d, 3 * t + c),
            point(.5f + 2 * t + b - d, 3 * t + c), point(.5f + t + b, t + c),
            point(.5f + b + d, -t + c), point(.8f, e), end)
        return (0..2).map { i -> PuzzleCurve(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[i * 3 + 3]) }
    }

    private fun line(a: PuzzlePoint, b: PuzzlePoint) = listOf(PuzzleCurve(a, a, b, b))
    private fun reverse(edge: List<PuzzleCurve>) = edge.asReversed().map { it.reversed() }

    /** Each internal seam exactly once, woven back and forth for the victory reveal. */
    fun seams(): List<List<PuzzleCurve>> = buildList {
        horizontal.forEachIndexed { row, edges ->
            val curves = edges.flatMap { it }
            add(if (row % 2 == 0) curves else reverse(curves))
        }
        for (col in 0 until this@JigsawGeometry.size.columns - 1) {
            val curves = vertical.flatMap { it[col] }
            add(if (col % 2 == 0) curves else reverse(curves))
        }
    }

    fun piece(id: Int): List<PuzzleCurve> {
        val row = id / size.columns; val col = id % size.columns
        val tl = PuzzlePoint(col * size.cellWidth, row * size.cellHeight)
        val tr = PuzzlePoint((col + 1) * size.cellWidth, row * size.cellHeight)
        val bl = PuzzlePoint(col * size.cellWidth, (row + 1) * size.cellHeight)
        val br = PuzzlePoint((col + 1) * size.cellWidth, (row + 1) * size.cellHeight)
        return (if (row == 0) line(tl, tr) else horizontal[row - 1][col]) +
            (if (col == size.columns - 1) line(tr, br) else vertical[row][col]) +
            (if (row == size.rows - 1) line(br, bl) else reverse(horizontal[row][col])) +
            (if (col == 0) line(bl, tl) else reverse(vertical[row][col - 1]))
    }
}
