package com.Atom2Universe.app.games.jigsaw

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ceil
import kotlin.random.Random
import java.util.UUID

/** Image-space coordinates, independently of the screen. */
data class PuzzlePoint(val x: Float, val y: Float) {
    operator fun plus(other: PuzzlePoint) = PuzzlePoint(x + other.x, y + other.y)
    operator fun minus(other: PuzzlePoint) = PuzzlePoint(x - other.x, y - other.y)
    fun rotated(turns: Int): PuzzlePoint = when ((turns % 4 + 4) % 4) {
        1 -> PuzzlePoint(-y, x)
        2 -> PuzzlePoint(-x, -y)
        3 -> PuzzlePoint(y, -x)
        else -> this
    }
}

enum class JigsawSize(val columns: Int, val rows: Int) {
    MINI(4, 6), EASY(6, 9), MEDIUM(8, 12), HARD(10, 15), EXPERT(12, 18), MASTER(14, 21);
    val count get() = columns * rows
    val cell get() = 600f / columns
}

/** The frame follows the image exactly; grid cells are chosen to be nearly square. */
data class JigsawGrid(val columns: Int, val rows: Int, val width: Float, val height: Float) {
    init {
        require(columns in 1..64 && rows in 1..64 && columns * rows in 2..400)
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f && width <= 900f && height <= 900f)
    }
    val count get() = columns * rows
    val cellWidth get() = width / columns
    val cellHeight get() = height / rows
    val cell get() = minOf(cellWidth, cellHeight)
    val span get() = maxOf(cellWidth, cellHeight)

    companion object {
        /** The 2:3 portrait frame, as for a 1200 × 1800 image (hub artwork, tests). */
        fun portrait(size: JigsawSize) = JigsawGrid(size.columns, size.rows, 600f, 900f)

        fun forImage(size: JigsawSize, imageWidth: Int, imageHeight: Int): JigsawGrid {
            require(imageWidth > 0 && imageHeight > 0)
            val ratio = imageWidth.toDouble() / imageHeight
            val candidates = (1..64).flatMap { columns -> (1..64).map { rows -> columns to rows } }
                .filter { (columns, rows) -> columns * rows in 2..400 &&
                    columns * rows >= size.count * .65 && columns * rows <= size.count * 1.45 }
            val (columns, rows) = candidates.minBy { (columns, rows) ->
                val shape = kotlin.math.ln(columns.toDouble() / rows / ratio)
                val amount = kotlin.math.ln(columns.toDouble() * rows / size.count)
                8 * shape * shape + amount * amount
            }
            val width = if (imageWidth >= imageHeight) 900f else 900f * (imageWidth.toFloat() / imageHeight)
            val height = if (imageHeight >= imageWidth) 900f else 900f * (imageHeight.toFloat() / imageWidth)
            return JigsawGrid(columns, rows, width, height)
        }
    }
}

enum class JigsawLayout { TRAY, TABLE }

data class PuzzleBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun including(other: PuzzleBounds) = PuzzleBounds(minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom))
}

enum class TrayIntent { PENDING, SCROLL, EXTRACT }

/** Once a horizontal swipe starts, drifting upwards must not take a piece out of the tray. */
class JigsawTrayGesture(private val slop: Float) {
    var intent = TrayIntent.PENDING
        private set
    fun move(dx: Float, dy: Float, canExtract: Boolean): TrayIntent {
        if (intent == TrayIntent.PENDING) {
            if (abs(dx) > slop && abs(dx) >= abs(dy)) intent = TrayIntent.SCROLL
            else if (canExtract && dy < -slop && abs(dy) > abs(dx)) intent = TrayIntent.EXTRACT
        }
        return intent
    }
}

data class PieceGroup(
    val ids: MutableList<Int>, var offset: PuzzlePoint = PuzzlePoint(0f, 0f),
    var turns: Int = 0, var inTray: Boolean = true, var locked: Boolean = false
) {
    fun copyGroup() = copy(ids = ids.toMutableList())
}

class JigsawGame(
    val imagePath: String, val size: JigsawSize, val seed: Int,
    val rotation: Boolean, val sessionId: String = UUID.randomUUID().toString(),
    val layout: JigsawLayout = JigsawLayout.TRAY,
    val grid: JigsawGrid = JigsawGrid.portrait(size)
) {
    val groups = mutableListOf<PieceGroup>()
    var elapsedMs = 0L
    var guideUnlocked = false
    var guideVisible = false
    var rewardHandled = false
    /** Read several times per frame: counted in place, without allocating. */
    val placed: Int get() {
        var count = 0
        for (i in groups.indices) groups[i].let { if (it.locked) count += it.ids.size }
        return count
    }
    val solved get() = placed == grid.count

    init {
        val random = Random(seed)
        (0 until grid.count).shuffled(random).forEach {
            groups += PieceGroup(mutableListOf(it), turns = if (rotation) random.nextInt(4) else 0)
        }
        if (layout == JigsawLayout.TABLE) {
            val scatter = Random(seed xor 0x51A77E)
            val centers = mutableListOf<PuzzlePoint>()
            groups.forEachIndexed { index, group ->
                var position = PuzzlePoint(0f, 0f)
                var bestDistance = -1f
                // Keep a clear margin around the frame, and try to avoid hiding pieces under each other.
                for (attempt in 0 until 16) {
                    val side = if (index < 4) index else scatter.nextInt(10).let {
                        when (it) { 0, 1 -> 0; 2, 3, 4 -> 1; 5, 6 -> 2; else -> 3 }
                    }
                    val distance = grid.cell * 1.25f + scatter.nextFloat() * 240f
                    val along = scatter.nextFloat()
                    val candidate = when (side) {
                        0 -> PuzzlePoint(along * grid.width, -distance)
                        1 -> PuzzlePoint(grid.width + distance, along * grid.height)
                        2 -> PuzzlePoint(along * grid.width, grid.height + distance)
                        else -> PuzzlePoint(-distance, along * grid.height)
                    }
                    val nearest = centers.minOfOrNull { hypot(candidate.x - it.x, candidate.y - it.y) } ?: Float.MAX_VALUE
                    if (nearest > bestDistance) { position = candidate; bestDistance = nearest }
                    if (nearest >= grid.cell * 1.1f) break
                }
                centers += position
                group.inTray = false
                group.offset = position - center(group.ids.single()).rotated(group.turns)
            }
        }
    }

    fun center(id: Int) = PuzzlePoint(
        (id % grid.columns + .5f) * grid.cellWidth, (id / grid.columns + .5f) * grid.cellHeight
    )

    fun groupCenter(group: PieceGroup): PuzzlePoint {
        val points = group.ids.map { center(it).rotated(group.turns) }
        return PuzzlePoint((points.minOf { it.x } + points.maxOf { it.x }) / 2,
            (points.minOf { it.y } + points.maxOf { it.y }) / 2) + group.offset
    }

    fun bounds(group: PieceGroup): PuzzleBounds {
        val box = FloatArray(4)
        bounds(group, box)
        return PuzzleBounds(box[0], box[1], box[2], box[3])
    }

    /** Left, top, right, bottom into [out], without allocating: the board culls every group per frame. */
    fun bounds(group: PieceGroup, out: FloatArray) {
        val turns = (group.turns % 4 + 4) % 4
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        for (i in group.ids.indices) {
            val id = group.ids[i]
            val cx = (id % grid.columns + .5f) * grid.cellWidth
            val cy = (id / grid.columns + .5f) * grid.cellHeight
            // Same quarter turns as PuzzlePoint.rotated.
            val x = when (turns) { 1 -> -cy; 2 -> -cx; 3 -> cy; else -> cx } + group.offset.x
            val y = when (turns) { 1 -> cx; 2 -> -cy; 3 -> -cx; else -> cy } + group.offset.y
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        val radius = grid.span * .85f // Covers the tabs as well as the square body.
        out[0] = left - radius; out[1] = top - radius; out[2] = right + radius; out[3] = bottom + radius
    }

    fun tableBounds(): PuzzleBounds {
        val box = FloatArray(4)
        var left = 0f; var top = 0f; var right = grid.width; var bottom = grid.height
        for (i in groups.indices) {
            val group = groups[i]
            if (group.inTray || group.locked) continue
            bounds(group, box)
            left = minOf(left, box[0]); top = minOf(top, box[1])
            right = maxOf(right, box[2]); bottom = maxOf(bottom, box[3])
        }
        return PuzzleBounds(left, top, right, bottom)
    }

    /** A release keeps a piece on the table, regardless of where it was dropped. */
    fun release(group: PieceGroup, tolerance: Float, frameTolerance: Float? = null): Boolean {
        group.inTray = false
        return snap(group, tolerance, frameTolerance)
    }

    fun returnToTray(group: PieceGroup): Boolean {
        if (layout != JigsawLayout.TRAY || group !in groups || group.inTray || group.locked || group.ids.size != 1) return false
        group.inTray = true
        return true
    }

    fun rotate(group: PieceGroup) {
        if (!rotation || group.locked) return
        val pivot = groupCenter(group)
        group.offset = pivot + (group.offset - pivot).rotated(1)
        group.turns = (group.turns + 1) % 4
    }

    fun adjacent(a: Int, b: Int): Boolean =
        abs(a % grid.columns - b % grid.columns) + abs(a / grid.columns - b / grid.columns) == 1

    /** Only actual neighbours with the same orientation and aligned image coordinates join. */
    fun snap(group: PieceGroup, tolerance: Float, frameTolerance: Float? = null): Boolean {
        if (group.inTray || group.locked || group !in groups) return false
        val radius = tolerance.coerceIn(0f, grid.cell * (if (layout == JigsawLayout.TABLE) .12f else .20f))
        // A stationary frame can be easier to land on without increasing attraction between loose pieces.
        val frameRadius = frameTolerance?.coerceIn(0f, grid.cell * .32f) ?: radius
        var changed = false
        // Free-table piles must not be harvested by sweeping a piece over several neighbours.
        // Each deliberate release chooses one target: the frame, or a single neighbouring group.
        if (group.turns == 0 && hypot(group.offset.x, group.offset.y) <= frameRadius) {
            group.offset = PuzzlePoint(0f, 0f)
            group.locked = true
            changed = true
        }
        var searching = !group.locked
        while (searching) {
            searching = false
            val other = groups.filter { candidate ->
                candidate !== group && !candidate.inTray && candidate.turns == group.turns &&
                    hypot(candidate.offset.x - group.offset.x, candidate.offset.y - group.offset.y) <= radius &&
                    group.ids.any { a -> candidate.ids.any { b -> adjacent(a, b) } }
            }.minByOrNull { hypot(it.offset.x - group.offset.x, it.offset.y - group.offset.y) }
            if (other != null) {
                group.offset = other.offset
                group.ids += other.ids
                group.locked = other.locked
                groups.remove(other)
                changed = true
                searching = !group.locked && layout != JigsawLayout.TABLE
            }
        }
        if ((!changed || layout != JigsawLayout.TABLE) && group.turns == 0 && hypot(group.offset.x, group.offset.y) <= frameRadius) {
            group.offset = PuzzlePoint(0f, 0f)
            group.locked = true
            changed = true
        }
        if (group.locked) {
            // All fixed pieces form one group, even when placed far apart on the board.
            groups.filter { it !== group && it.locked }.toList().forEach {
                group.ids += it.ids
                groups.remove(it)
            }
        }
        return changed
    }

    /** Explicit tidy command: return singles to the carousel, or spread them around the frame. */
    fun tidy() {
        val singles = groups.filter { !it.locked && it.ids.size == 1 }
        if (layout == JigsawLayout.TRAY) singles.forEach { it.inTray = true }
        else {
            val step = grid.cell * 1.75f
            val columns = ceil(grid.width / step).toInt()
            val rows = ceil(grid.height / step).toInt()
            val positions = mutableListOf<PuzzlePoint>()
            var ring = 1
            while (positions.size < singles.size) {
                for (x in -ring..columns + ring) {
                    positions += PuzzlePoint(x * step, -ring * step)
                    positions += PuzzlePoint(x * step, (rows + ring) * step)
                }
                for (y in -ring + 1 until rows + ring) {
                    positions += PuzzlePoint(-ring * step, y * step)
                    positions += PuzzlePoint((columns + ring) * step, y * step)
                }
                ring++
            }
            singles.forEachIndexed { i, group ->
                group.inTray = false
                group.offset = positions[i] - center(group.ids.single()).rotated(group.turns)
            }
        }
    }

    fun valid(): Boolean {
        val ids = groups.flatMap { it.ids }
        return ids.size == grid.count && ids.toSet() == (0 until grid.count).toSet() &&
            groups.all { it.ids.isNotEmpty() && it.turns in 0..3 &&
                (rotation || it.turns == 0) && (!it.inTray || it.ids.size == 1) &&
                (layout == JigsawLayout.TRAY || !it.inTray) &&
                it.offset.x.isFinite() && it.offset.y.isFinite() &&
                abs(it.offset.x) < 10000 && abs(it.offset.y) < 10000 &&
                (!it.locked || (!it.inTray && it.turns == 0 && it.offset == PuzzlePoint(0f, 0f))) } &&
            elapsedMs >= 0 && (!guideVisible || guideUnlocked)
    }
}
