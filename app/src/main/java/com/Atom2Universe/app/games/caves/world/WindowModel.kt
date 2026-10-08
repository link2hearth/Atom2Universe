package com.Atom2Universe.app.games.caves.world

/** One-block fixed windows. Only the wood is drawn; the clear glazing still stops movement. */
internal object WindowModel {
    private const val N = 32
    private const val DEPTH = 3f / 16f
    private const val INSET = (1f - DEPTH) / 2f
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, List<PartialBlockModel.Face>>()

    private fun wood(shape: Int, x: Int, y: Int): Boolean {
        if (x !in 0 until N || y !in 0 until N) return false
        if (x < 3 || x > 28 || y < 3 || y > 28) return true
        val dx = kotlin.math.abs(2 * x - 31)
        val dy = kotlin.math.abs(2 * y - 31)
        return when (shape) {
            1 -> dx + dy > 26 // Diamond.
            2 -> x in 15..16 || y in 15..16 // Four panes.
            3 -> dx * dx + dy * dy > 26 * 26 // Round.
            4 -> x in 10..12 || x in 19..21 // Three narrow panes.
            5 -> y < 16 && dx * dx + dy * dy > 26 * 26 || x in 15..16 // Arch.
            6 -> false // Large clear pane.
            7 -> y in 10..12 || y in 19..21 // Three horizontal panes.
            8 -> x in 10..12 || x in 19..21 || y in 15..16 // Six panes.
            9 -> dx + dy > 40 // Clipped corners.
            else -> x in 15..16 // Twin panes.
        }
    }

    private fun point(x: Float, y: Float, z: Float, facing: Int): FloatArray = when (facing) {
        0 -> floatArrayOf(x, y, INSET + z)
        1 -> floatArrayOf(1f - INSET - z, y, x)
        2 -> floatArrayOf(1f - x, y, 1f - INSET - z)
        else -> floatArrayOf(INSET + z, y, 1f - x)
    }

    private val collision = Array(4) { facing ->
        val a = point(0f, 0f, 0f, facing); val b = point(1f, 1f, DEPTH, facing)
        listOf(PartialBlockModel.Box(minOf(a[0], b[0]), 0f, minOf(a[2], b[2]),
            kotlin.math.abs(b[0] - a[0]), 1f, kotlin.math.abs(b[2] - a[2])))
    }
    fun boxes(meta: Byte) = collision[meta.toInt() and 3]
    fun faces(shape: Int, meta: Byte): List<PartialBlockModel.Face> {
        val facing = meta.toInt() and 3
        return cache.getOrPut(shape * 4 + facing) { build(shape, facing) }
    }

    private fun build(shape: Int, facing: Int): List<PartialBlockModel.Face> = buildList {
        fun face(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, direction: Int) {
            val a = point(x0, y0, z0, facing); val b = point(x1, y1, z1, facing)
            val normal = when (direction) {
                2 -> point(1f, 0f, 0f, facing)
                3 -> point(-1f, 0f, 0f, facing)
                4 -> point(0f, 0f, 1f, facing)
                else -> point(0f, 0f, -1f, facing)
            }
            val origin = point(0f, 0f, 0f, facing)
            val rotated = when {
                direction < 2 -> direction
                normal[0] - origin[0] > .5f -> 2
                normal[0] - origin[0] < -.5f -> 3
                normal[2] - origin[2] > .5f -> 4
                else -> 5
            }
            val corners = Array(8) { i -> FloatArray(3) { axis ->
                if (i and (1 shl axis) == 0) minOf(a[axis], b[axis]) else maxOf(a[axis], b[axis])
            } }
            val vertices = TorchModel.faces[rotated].map { corners[it] }.toTypedArray()
            add(PartialBlockModel.Face(rotated, vertices, 0))
        }
        // Greedy rectangles on the front and back avoid one quad per pixel of frame.
        val remaining = BooleanArray(N * N) { wood(shape, it % N, it / N) }
        for (y in 0 until N) for (x in 0 until N) {
            if (!remaining[x + y * N]) continue
            var w = 1
            while (x + w < N && remaining[x + w + y * N]) w++
            var h = 1
            while (y + h < N && (0 until w).all { remaining[x + it + (y + h) * N] }) h++
            for (dy in 0 until h) for (dx in 0 until w) remaining[x + dx + (y + dy) * N] = false
            for (back in listOf(false, true)) {
                val z = if (back) DEPTH else 0f
                face(x / 32f, 1f - (y + h) / 32f, z, (x + w) / 32f, 1f - y / 32f, z, if (back) 4 else 5)
            }
        }
        // All outer edges and inner glazing edges join the two faces, including the mullions.
        for (vertical in listOf(false, true)) for (line in 0..N) {
            fun boundary(pos: Int): Int {
                val a = if (vertical) wood(shape, line - 1, pos) else wood(shape, pos, line - 1)
                val b = if (vertical) wood(shape, line, pos) else wood(shape, pos, line)
                return if (a == b) 0 else if (a) 1 else -1
            }
            var pos = 0
            while (pos < N) {
                val sign = boundary(pos)
                if (sign == 0) { pos++; continue }
                val start = pos++
                while (pos < N && boundary(pos) == sign) pos++
                if (vertical) face(line / 32f, 1f - pos / 32f, 0f, line / 32f, 1f - start / 32f,
                    DEPTH, if (sign > 0) 2 else 3)
                else face(start / 32f, 1f - line / 32f, 0f, pos / 32f, 1f - line / 32f,
                    DEPTH, if (sign > 0) 1 else 0)
            }
        }
    }
}
