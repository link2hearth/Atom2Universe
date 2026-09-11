package com.Atom2Universe.app.games.farm

/** Stable parcel order is part of the save format. Append land; never reorder existing IDs. */
object FarmLayout {
    /**
     * [price] is the unlock cost. It is written out rather than computed: what a parcel really sells
     * is the seed it unlocks (one new crop per parcel), so the price follows the seed ladder, not the
     * surface. A formula on the capacity alone produced a non-increasing curve.
     */
    data class Land(val x: Float, val y: Float, val columns: Int, val rows: Int, val price: Int) {
        val capacity get() = columns * rows
        val width get() = 48f + columns * 78f
        val height get() = 108f + rows * 74f
    }
    val lands = listOf(
        Land(850f, 180f, 4, 3, 0), Land(1460f, 80f, 3, 4, 500),
        Land(1900f, 280f, 6, 3, 1_500), Land(300f, 140f, 4, 4, 4_000),
        Land(180f, 850f, 5, 4, 7_000), Land(790f, 740f, 4, 5, 15_000),
        Land(1530f, 720f, 3, 2, 18_000), Land(1970f, 1070f, 6, 5, 28_000),
        Land(80f, 1550f, 5, 3, 45_000), Land(640f, 1470f, 7, 4, 70_000),
        Land(1420f, 1320f, 4, 6, 110_000), Land(1920f, 1850f, 6, 6, 200_000),
        // The southern clearings. They sit below every existing bed, on either side of the spine
        // trail, so no road had to be rerouted - only two lanes added to reach their gates.
        Land(110f, 2030f, 6, 3, 350_000), Land(1420f, 2070f, 4, 4, 600_000)
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
