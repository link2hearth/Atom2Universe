package com.Atom2Universe.app.games.farm

/**
 * Where every parcel of the home map stands.
 *
 * Parcel ORDER is part of the save format: planting cells are saved by index, parcel after parcel,
 * so an existing parcel never changes its columns or rows and new land is only ever appended. Its
 * POSITION is not saved, which is what lets the whole map be laid out again.
 *
 * Parcels are no longer placed by hand. Each one names a clearing on a grid - a column and a band -
 * and its coordinates follow from the widest parcel of its column and the tallest one of its band.
 * Hand-written coordinates had to be checked against every neighbour, and every new parcel meant
 * rerouting a road by hand; here a new parcel is one line, and the roads find their own way.
 *
 * Inside a band every parcel stands on the same ground line, so all its gates open onto one lane.
 * The two middle columns are pulled apart to leave room for the spine road between them.
 */
object FarmLayout {
    /** A plain rectangle in world units. This file stays free of Android so the tests can read it. */
    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX get() = (left + right) / 2
    }

    /**
     * [price] is the unlock cost. It is written out rather than computed: what a parcel really sells
     * is the seed it unlocks (one new crop per parcel), so the price follows the seed ladder, not the
     * surface. [band] is the row of clearings the parcel stands in.
     */
    data class Land(val x: Float, val y: Float, val columns: Int, val rows: Int, val price: Int, val band: Int) {
        val capacity get() = columns * rows
        val width get() = landWidth(columns)
        val height get() = landHeight(rows)
    }

    private class Clearing(val column: Int, val band: Int, val columns: Int, val rows: Int, val price: Int)

    const val COLUMNS = 4
    const val BANDS = 6
    /** How far below its band's ground line the lane runs - clear of the gates as they swing open. */
    const val LANE_DROP = 130f
    private const val SIDE_MARGIN = 150f
    /** Room above the first band for the banners and for the treasure bush over parcel 1. */
    private const val TOP_MARGIN = 220f
    private const val BOTTOM_MARGIN = 170f
    private const val COLUMN_GAP = 160f
    private const val SPINE_GAP = 260f
    private const val BAND_GAP = 240f
    private const val YARD_WIDTH = 380f
    private const val YARD_HEIGHT = 180f

    private fun landWidth(columns: Int) = 48f + columns * 78f
    private fun landHeight(rows: Int) = 108f + rows * 74f

    /**
     * Purchase order spirals out from the middle of the top band: first the two columns beside the
     * spine, then the outer ones, then down a band. The first parcels stay within a glance of each
     * other, and the land still to buy always lies further out.
     *
     * The first fourteen keep the sizes they had before the redesign - see the note at the top.
     */
    private val clearings = listOf(
        Clearing(1, 0, 4, 3, 0), Clearing(2, 0, 3, 4, 500),
        Clearing(0, 0, 6, 3, 1_500), Clearing(3, 0, 4, 4, 4_000),
        Clearing(1, 1, 5, 4, 7_000), Clearing(2, 1, 4, 5, 15_000),
        Clearing(0, 1, 3, 2, 18_000), Clearing(3, 1, 6, 5, 28_000),
        Clearing(1, 2, 5, 3, 45_000), Clearing(2, 2, 7, 4, 70_000),
        Clearing(0, 2, 4, 6, 110_000), Clearing(3, 2, 6, 6, 200_000),
        Clearing(1, 3, 6, 3, 350_000), Clearing(2, 3, 4, 4, 600_000),
        // The second half of the ladder. Each price is about two to three and a half days of the
        // income the farm makes just before buying it - the same curve as the first fourteen, which
        // went from half a day to two, carried on so the late parcels stay goals and not errands.
        Clearing(0, 3, 4, 4, 700_000), Clearing(3, 3, 5, 3, 1_600_000),
        Clearing(1, 4, 4, 5, 2_000_000), Clearing(2, 4, 5, 4, 4_500_000),
        Clearing(0, 4, 6, 3, 5_500_000), Clearing(3, 4, 5, 4, 12_000_000),
        Clearing(1, 5, 4, 6, 14_000_000), Clearing(2, 5, 6, 4, 22_000_000),
        Clearing(0, 5, 5, 5, 26_000_000)
    )
    /** The one clearing without a parcel: the shed and the well stand there. */
    private const val YARD_COLUMN = 3
    private const val YARD_BAND = BANDS - 1

    private val columnWidths = FloatArray(COLUMNS) { c ->
        maxOf(clearings.filter { it.column == c }.maxOf { landWidth(it.columns) },
            if (c == YARD_COLUMN) YARD_WIDTH else 0f)
    }
    private val bandHeights = FloatArray(BANDS) { b ->
        maxOf(clearings.filter { it.band == b }.maxOf { landHeight(it.rows) },
            if (b == YARD_BAND) YARD_HEIGHT else 0f)
    }
    private val columnLefts = FloatArray(COLUMNS).also { lefts ->
        var x = SIDE_MARGIN
        for (c in 0 until COLUMNS) { lefts[c] = x; x += columnWidths[c] + if (c == 1) SPINE_GAP else COLUMN_GAP }
    }
    /** The ground line of each band: where its fences stand and its gates open. */
    private val bandBottoms = FloatArray(BANDS).also { bottoms ->
        var y = TOP_MARGIN
        for (b in 0 until BANDS) { y += bandHeights[b]; bottoms[b] = y; y += BAND_GAP }
    }

    val lands = clearings.mapIndexed { i, c ->
        // A few units of sideways nudge, so the parcels of a column do not line up like a
        // spreadsheet. Far smaller than the column gap: no two parcels can come near each other.
        val nudge = ((i * 7) % 5 - 2) * 9f
        val left = columnLefts[c.column] + (columnWidths[c.column] - landWidth(c.columns)) / 2 + nudge
        Land(left, bandBottoms[c.band] - landHeight(c.rows), c.columns, c.rows, c.price, c.band)
    }
    private val offsets = lands.runningFold(0) { total, land -> total + land.capacity }
    val cellCount = offsets.last()
    val worldWidth = columnLefts[COLUMNS - 1] + columnWidths[COLUMNS - 1] + SIDE_MARGIN
    val worldHeight = bandBottoms[BANDS - 1] + LANE_DROP + BOTTOM_MARGIN
    /** The x the spine road winds around, halfway between the two middle columns. */
    val spineX = columnLefts[1] + columnWidths[1] + SPINE_GAP / 2
    val yard = (columnLefts[YARD_COLUMN] + (columnWidths[YARD_COLUMN] - YARD_WIDTH) / 2).let { left ->
        Area(left, bandBottoms[YARD_BAND] - YARD_HEIGHT, left + YARD_WIDTH, bandBottoms[YARD_BAND])
    }
    /** The bush hiding the daily coin pile, in the grass just above parcel 1. */
    val treasure = lands[0].let { first ->
        val centerX = first.x + first.width / 2
        Area(centerX - 65f, first.y - 180f, centerX + 65f, first.y - 50f)
    }

    fun laneY(band: Int): Float = bandBottoms[band] + LANE_DROP
    /** The gate stands in the second fence segment of the front side, where FarmScenery draws it. */
    fun gateX(parcel: Int): Float = lands[parcel].let { it.x + it.width / it.columns * 1.5f }
    fun gateY(parcel: Int): Float = lands[parcel].let { it.y + it.height + 14f }
    fun cells(parcel: Int): IntRange = offsets[parcel] until offsets[parcel + 1]
    fun parcelOf(cell: Int): Int {
        require(cell in 0 until cellCount)
        return lands.indices.first { cell < offsets[it + 1] }
    }
    fun localCell(cell: Int): Int = cell - offsets[parcelOf(cell)]
}
