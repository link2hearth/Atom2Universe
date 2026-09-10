package com.Atom2Universe.app.games.farm

/** Stable parcel order is part of the save format. Append land; never reorder existing IDs. */
object FarmLayout {
    data class Land(val x: Float, val y: Float, val columns: Int, val rows: Int) {
        val capacity get() = columns * rows
        val width get() = 48f + columns * 78f
        val height get() = 108f + rows * 74f
    }
    val lands = listOf(
        Land(850f, 180f, 4, 3), Land(1460f, 80f, 3, 4),
        Land(1900f, 280f, 6, 3), Land(300f, 140f, 4, 4),
        Land(180f, 850f, 5, 4), Land(790f, 740f, 4, 5),
        Land(1530f, 720f, 3, 2), Land(1970f, 1070f, 6, 5),
        Land(80f, 1550f, 5, 3), Land(640f, 1470f, 7, 4),
        Land(1420f, 1320f, 4, 6), Land(1920f, 1850f, 6, 6)
    )
    private val offsets = lands.runningFold(0) { total, land -> total + land.capacity }
    val cellCount = offsets.last()
    // Northern scenery extends the map without changing any saved planting indices.
    const val worldTop = -850f
    val worldWidth = lands.maxOf { it.x + it.width } + 180f
    val worldHeight = lands.maxOf { it.y + it.height } + 180f
    fun cells(parcel: Int): IntRange = offsets[parcel] until offsets[parcel + 1]
    fun parcelOf(cell: Int): Int {
        require(cell in 0 until cellCount)
        return lands.indices.first { cell < offsets[it + 1] }
    }
    fun localCell(cell: Int): Int = cell - offsets[parcelOf(cell)]
}
