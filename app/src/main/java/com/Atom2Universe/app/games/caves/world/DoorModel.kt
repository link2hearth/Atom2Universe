package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockDef
import com.Atom2Universe.app.games.caves.node.DoorTextures

/** Two cells share facing (0: -Z, 1: +X, 2: +Z, 3: -X), opening and hinge. */
internal object DoorModel {
    const val OPEN = 4
    const val RIGHT = 8
    const val UPPER = 16
    private const val THICKNESS = 3f / 16f
    fun upper(meta: Byte) = meta.toInt() and UPPER != 0
    fun partnerY(y: Int, meta: Byte) = y + if (upper(meta)) -1 else 1

    private fun point(x: Float, y: Float, z: Float, meta: Int): FloatArray {
        val right = meta and RIGHT != 0
        var px = x
        var pz = z
        if (meta and OPEN != 0) {
            px = if (right) 1f - z else z
            pz = x
        } else if (right) px = 1f - x
        return when (meta and 3) {
            0 -> floatArrayOf(px, y, pz)
            1 -> floatArrayOf(1f - pz, y, px)
            2 -> floatArrayOf(1f - px, y, 1f - pz)
            else -> floatArrayOf(pz, y, 1f - px)
        }
    }

    private val shapes = Array(32) { meta ->
        val corners = Array(8) { i -> point(if (i and 1 == 0) 0f else 1f,
            if (i and 2 == 0) 0f else 1f, if (i and 4 == 0) 0f else THICKNESS, meta) }
        val minX = corners.minOf { it[0] }; val minZ = corners.minOf { it[2] }
        listOf(PartialBlockModel.Box(minX, 0f, minZ, corners.maxOf { it[0] } - minX,
            1f, corners.maxOf { it[2] } - minZ))
    }
    private val surfaces = Array(32) { meta ->
        val b = shapes[meta][0]
        val corners = Array(8) { i -> floatArrayOf(b.x + if (i and 1 == 0) 0f else b.width,
            if (i and 2 == 0) 0f else 1f, b.z + if (i and 4 == 0) 0f else b.depth) }
        TorchModel.faces.mapIndexedNotNull { direction, indices ->
            // Both cells form one leaf: their touching caps are internal, not wooden
            // shelves across the glazing. This also applies to the held item and icon.
            val internalCap = if (meta and UPPER != 0) 1 else 0
            if (direction == internalCap) return@mapIndexedNotNull null
            val vertices = indices.map { corners[it] }.toTypedArray()
            val origin = point(0f, 0f, 0f, meta)
            val end = point(1f, 0f, 0f, meta)
            val broad = if (b.width > b.depth) direction >= 4 else direction in 2..3
            val uv = vertices.map { v ->
                val u = (v[0] - origin[0]) * (end[0] - origin[0]) +
                    (v[2] - origin[2]) * (end[2] - origin[2])
                floatArrayOf(if (broad) u else if (direction < 2) v[0] else v[0] + v[2],
                    if (broad) (2f - v[1] - (if (meta and UPPER != 0) 1f else 0f)) / 2f
                    else if (direction < 2) v[2] else 1f - v[1])
            }.toTypedArray()
            PartialBlockModel.Face(direction, vertices, if (broad) 2 else 0, uv)
        }
    }
    fun boxes(meta: Byte) = shapes[meta.toInt() and 31]
    private val framedSurfaces = java.util.concurrent.ConcurrentHashMap<Int, List<PartialBlockModel.Face>>()
    private val icons = java.util.concurrent.ConcurrentHashMap<Int, List<PartialBlockModel.Face>>()

    fun faces(def: BlockDef, meta: Byte): List<PartialBlockModel.Face> {
        val state = meta.toInt() and 31
        val style = DoorTextures.style(def)
        return framedSurfaces.getOrPut(style * 32 + state) {
            surfaces[state] + windowEdges(style, state)
        }
    }

    /** Extrude every opaque/transparent boundary through the leaf's thickness.
     * Merge straight pixel runs; read the complete door mask across the cell seam. */
    private fun windowEdges(style: Int, meta: Int): List<PartialBlockModel.Face> = buildList {
        val firstRow = if (meta and UPPER != 0) 0 else 32
        val half = if (meta and UPPER != 0) 1f else 0f
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float, normalX: Int, normalY: Int) {
            val a = point(x0 / 32f, 2f - y0 / 32f - half, 0f, meta)
            val b = point(x1 / 32f, 2f - y1 / 32f - half, THICKNESS, meta)
            val origin = point(0f, 0f, 0f, meta)
            val normal = point(normalX.toFloat(), normalY.toFloat(), 0f, meta)
            val direction = when {
                normalY > 0 -> 0
                normalY < 0 -> 1
                normal[0] - origin[0] > .5f -> 2
                normal[0] - origin[0] < -.5f -> 3
                normal[2] - origin[2] > .5f -> 4
                else -> 5
            }
            val corners = Array(8) { i -> FloatArray(3) { axis ->
                if (i and (1 shl axis) == 0) minOf(a[axis], b[axis]) else maxOf(a[axis], b[axis])
            } }
            val vertices = TorchModel.faces[direction].map { corners[it] }.toTypedArray()
            val uv = vertices.map { v ->
                floatArrayOf(if (direction in 2..3) v[2] else v[0],
                    if (direction < 2) v[2] else 1f - v[1])
            }.toTypedArray()
            add(PartialBlockModel.Face(direction, vertices, 0, uv))
        }
        fun boundary(ax: Int, ay: Int, bx: Int, by: Int): Int {
            val a = DoorTextures.transparent(style, ax, ay)
            val b = DoorTextures.transparent(style, bx, by)
            return if (a == b) 0 else if (b) 1 else -1
        }
        for (x in 1..31) {
            var y = firstRow
            while (y < firstRow + 32) {
                val sign = boundary(x - 1, y, x, y)
                if (sign == 0) { y++; continue }
                val start = y++
                while (y < firstRow + 32 && boundary(x - 1, y, x, y) == sign) y++
                edge(x.toFloat(), start.toFloat(), x.toFloat(), y.toFloat(), sign, 0)
            }
        }
        for (y in maxOf(1, firstRow) until firstRow + 32) {
            var x = 0
            while (x < 32) {
                val sign = boundary(x, y - 1, x, y)
                if (sign == 0) { x++; continue }
                val start = x++
                while (x < 32 && boundary(x, y - 1, x, y) == sign) x++
                edge(start.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 0, -sign)
            }
        }
    }

    fun itemFaces(def: BlockDef): List<PartialBlockModel.Face> = icons.getOrPut(DoorTextures.style(def)) {
        (0..1).flatMap { half -> faces(def, (half * UPPER).toByte()).map { face ->
            face.copy(vertices = face.vertices.map { v ->
                floatArrayOf(.25f + v[0] * .5f, (v[1] + half) * .5f, v[2] * .5f + .4f)
            }.toTypedArray())
        } }
    }
}
