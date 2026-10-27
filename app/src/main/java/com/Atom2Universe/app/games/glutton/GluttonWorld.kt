package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import kotlin.math.*
import kotlin.random.Random

/**
 * One procedural world in metres: three square rings (the house, its suburb, the city) nested around
 * the start, each an open terrain of broad terraces. Cliffs are rolled down freely and climbed once your
 * radius exceeds the rise; stairs let a small gobbler up. Everything too big to swallow is in the way.
 */
class GluttonWorld(val seed: Long) {
    enum class Kind { STAIRS, CLIFF }
    /** Between two cells of different height. offset = position of the stairs along the edge (0..1); width in metres. */
    class Edge(val kind: Kind, val offset: Float, val width: Float)
    /** [from] and [to]: gobbler radius expected on entering and on leaving the ring; terraces grade food between them. */
    class Ring(val index: Int, val theme: Theme, val side: Float, val count: Int, val levels: Int,
        val step: Float, val base: Float, val from: Float, val to: Float) {
        val cell get() = side / count
        val half get() = side / 2f
        /** Collision padding at the ends of a flight of stairs. */
        val thickness get() = min(cell * .025f, from * 1.5f)
        /** Stairs between terraces are this wide; a gobbler fits while its body is narrower (then it climbs anyway). */
        val stairs get() = cell * .45f
        /** Half-width of the two ways out of the ring. */
        val exitHalf get() = cell * .25f
        lateinit var cells: Array<Cell>
        /** vertical[c + r * (count + 1)]: edge at x = -half + c * cell, between cells (c - 1, r) and (c, r); null = open ground. */
        lateinit var vertical: Array<Edge?>
        /** horizontal[c + r * count]: edge at y = -half + r * cell, between cells (c, r - 1) and (c, r); null = open ground. */
        lateinit var horizontal: Array<Edge?>
        /** Two ways out through the boundary: side (0 top, 1 right, 2 bottom, 3 left) and offset 0..1. */
        val exits = ArrayList<Pair<Int, Float>>(2)
        fun cell(col: Int, row: Int) = cells[col + row * count]
        fun vertical(c: Int, r: Int) = vertical[c + r * (count + 1)]
        fun horizontal(c: Int, r: Int) = horizontal[c + r * count]
    }
    class Cell(val ring: Ring, val col: Int, val row: Int, var level: Int) {
        val x0 get() = -ring.half + col * ring.cell
        val y0 get() = -ring.half + row * ring.cell
        val x1 get() = x0 + ring.cell
        val y1 get() = y0 + ring.cell
        val height get() = ring.base + level * ring.step
        /** The centre cell of an outer ring wraps the whole previous ring. */
        var inner = false
        /** Expected gobbler radius for which this cell's food is sized. */
        val tier get() = ring.from * (ring.to / ring.from).pow((level + .3f) / ring.levels)
    }
    class Placement(val prop: Prop, val x: Float, val y: Float, val radius: Float, val cell: Cell)

    val rings: List<Ring> = listOf(
        Ring(0, Theme.ROOM, 16f, 5, 3, .25f, 0f, .015f, .6f),
        Ring(1, Theme.SUBURB, 140f, 7, 3, 1.5f, 1f, .6f, 6f),
        Ring(2, Theme.CITY, 2100f, 7, 4, 8f, 6f, 6f, 120f))
    val placements = ArrayList<Placement>(2400)
    val movers = ArrayList<Placement>(120)
    val hourglasses = ArrayList<Placement>(80)
    val startX = 0f
    /** Share of a cell's area covered by food footprints (each item claims about four radii around it). */
    private val FILL = 1f
    val startY = 0f
    private val random = Random(seed)

    init {
        for (ring in rings) build(ring)
        for (ring in rings) furnish(ring)
    }

    fun ringAt(x: Float, y: Float): Ring? = rings.firstOrNull { abs(x) < it.half && abs(y) < it.half }
    fun cellAt(x: Float, y: Float): Cell? {
        val ring = ringAt(x, y) ?: return null
        val col = ((x + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
        val row = ((y + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
        return ring.cell(col, row)
    }
    /** Height the gobbler must have to roll from [from] into its neighbour across [edge]: nothing on open ground or downhill. */
    fun climb(from: Cell, to: Cell?, edge: Edge?): Float {
        if (to == null) return Float.MAX_VALUE
        if (edge == null) return 0f
        return max(0f, to.height - from.height)
    }
    fun neighbour(cell: Cell, side: Int): Cell? {
        val c = cell.col + if (side == 1) 1 else if (side == 3) -1 else 0
        val r = cell.row + if (side == 2) 1 else if (side == 0) -1 else 0
        if (c < 0 || r < 0 || c >= cell.ring.count || r >= cell.ring.count) return null
        return cell.ring.cell(c, r)
    }
    fun edge(cell: Cell, side: Int): Edge? = when (side) {
        0 -> cell.ring.horizontal(cell.col, cell.row)
        1 -> cell.ring.vertical(cell.col + 1, cell.row)
        2 -> cell.ring.horizontal(cell.col, cell.row + 1)
        else -> cell.ring.vertical(cell.col, cell.row)
    }
    /** Height to climb out of a ring over its boundary from [cell]; the city has no way out. */
    fun exitClimb(cell: Cell): Float {
        val next = rings.getOrNull(cell.ring.index + 1) ?: return Float.MAX_VALUE
        return max(0f, next.base - cell.height)
    }

    private fun build(ring: Ring) {
        val n = ring.count; val centre = n / 2
        // Terraces rise away from the start in broad regions: distance plus a slow wave, never a checkerboard.
        val phase = random.nextFloat() * 2f * PI.toFloat(); val phase2 = random.nextFloat() * 2f * PI.toFloat()
        ring.cells = Array(n * n) { i ->
            val col = i % n; val row = i / n
            val distance = max(abs(col - centre), abs(row - centre)) / centre.toFloat()
            val wave = .22f * sin(col * .9f + phase) * cos(row * .8f + phase2)
            var level = floor((distance + wave) * (ring.levels - .3f)).toInt().coerceIn(0, ring.levels - 1)
            if (col == centre && row == centre) level = 0
            Cell(ring, col, row, level)
        }
        if (ring.index > 0) ring.cell(centre, centre).inner = true
        ring.vertical = arrayOfNulls(((n + 1) * n)); ring.horizontal = arrayOfNulls(n * (n + 1))
        // Same height: open ground. Different heights: a cliff, with stairs wherever the small would otherwise be
        // cut off (spanning tree over the terraces) and a few more for loops.
        val parent = IntArray(n * n) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        class Link(val a: Int, val b: Int, val verticalEdge: Boolean, val weight: Float)
        val links = ArrayList<Link>(2 * n * n)
        for (row in 0 until n) for (col in 0 until n) {
            if (col + 1 < n) links += Link(col + row * n, col + 1 + row * n, true, random.nextFloat())
            if (row + 1 < n) links += Link(col + row * n, col + (row + 1) * n, false, random.nextFloat())
        }
        for (link in links) if (ring.cells[link.a].level == ring.cells[link.b].level) parent[find(link.a)] = find(link.b)
        links.sortBy { it.weight }
        for (link in links) {
            val a = ring.cells[link.a]; val b = ring.cells[link.b]
            if (a.level == b.level) continue
            val ra = find(link.a); val rb = find(link.b)
            val edge = if (ra != rb || random.nextFloat() < .25f) {
                parent[ra] = rb
                Edge(Kind.STAIRS, .25f + random.nextFloat() * .5f, ring.stairs)
            } else Edge(Kind.CLIFF, 0f, 0f)
            if (link.verticalEdge) ring.vertical[(a.col + 1) + a.row * (n + 1)] = edge
            else ring.horizontal[a.col + (a.row + 1) * n] = edge
        }
        // Two ways out of the ring, on opposite sides.
        val first = random.nextInt(4)
        ring.exits += first to (.2f + random.nextFloat() * .6f)
        ring.exits += ((first + 2) % 4) to (.2f + random.nextFloat() * .6f)
    }

    private fun furnish(ring: Ring) {
        val inner = rings.getOrNull(ring.index - 1)
        val themed = GluttonCatalog.forTheme(ring.theme).filter { !it.moves }
        val next = rings.getOrNull(ring.index + 1)?.let { GluttonCatalog.forTheme(it.theme).filter { p -> !p.moves } } ?: emptyList()
        val sorted = (themed + next).sortedBy { it.radius }
        for (cell in ring.cells) if (random.nextFloat() < .4f) {
            place(cell, GluttonCatalog.hourglass, cell.tier * .5f, inner)?.let { hourglasses += it }
        }
        if (ring.index == 0) {
            // A welcome of crumbs within reach of the very first second.
            val start = ring.cell(ring.count / 2, ring.count / 2)
            repeat(24) {
                val prop = pick(sorted, ring.from * (.3f + random.nextFloat() * .4f)) ?: return@repeat
                val angle = random.nextFloat() * 2f * PI.toFloat(); val distance = ring.from * (5f + random.nextFloat() * 15f)
                val x = startX + cos(angle) * distance; val y = startY + sin(angle) * distance
                if (placements.none { it.cell === start && (x - it.x).pow(2) + (y - it.y).pow(2) < (prop.radius + it.radius).pow(2) })
                    placements += Placement(prop, x, y, prop.radius, start)
            }
        }
        for (cell in ring.cells) {
            // Enough food that its spacing stays a few bodies apart for the gobbler the cell is meant for,
            // plus a second helping of smaller things so there is always something to nibble on the way.
            val count = (FILL * (ring.cell / (cell.tier * 4f)).pow(2)).roundToInt().coerceIn(8, 120)
            repeat(count * 2) { i ->
                val tier = if (i < count) cell.tier else cell.tier * .3f
                val jackpot = i < count && random.nextFloat() < .15f
                // Mostly bites a gobbler of this terrace can already take, a few things to grow into.
                val target = tier * if (jackpot) 1f + random.nextFloat() else .3f * 3f.pow(random.nextFloat())
                val prop = pick(sorted, target) ?: return@repeat
                place(cell, prop, prop.radius, inner)?.let { placements += it }
            }
        }
        for (prop in GluttonCatalog.forTheme(ring.theme).filter { it.moves }) {
            repeat(if (prop.motion == Motion.PATROL) 3 else 2) {
                val cell = ring.cells[random.nextInt(ring.cells.size)]
                if (ring.index == 0 && cell.col == ring.count / 2 && cell.row == ring.count / 2) return@repeat
                if (prop.radius > ring.cell * .25f) return@repeat
                place(cell, prop, prop.radius, inner)?.let { movers += it }
            }
        }
    }

    /** The prop whose real size is closest to [target] (log scale), with a little variety. */
    private fun pick(sorted: List<Prop>, target: Float): Prop? {
        if (sorted.isEmpty()) return null
        var index = sorted.binarySearch { it.radius.compareTo(target) }
        if (index < 0) index = -index - 1
        val candidates = (max(0, index - 2)..min(sorted.size - 1, index + 1)).map { sorted[it] }
            .filter { abs(ln(it.radius / target)) < 1.1f }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    private fun place(cell: Cell, prop: Prop, radius: Float, inner: Ring?): Placement? {
        val ring = cell.ring
        val margin = ring.cell * .08f + radius
        if (margin * 2 >= ring.cell) return null
        repeat(30) {
            val x = cell.x0 + margin + random.nextFloat() * (ring.cell - 2 * margin)
            val y = cell.y0 + margin + random.nextFloat() * (ring.cell - 2 * margin)
            if (cell.inner && inner != null && abs(x) < inner.half + radius + ring.cell * .05f && abs(y) < inner.half + radius + ring.cell * .05f) return@repeat
            // Nothing too big to swallow sits right where you wake up; crumbs keep a small distance too.
            if (ring.index == 0 && hypot(x - startX, y - startY) < radius + ring.from * (if (radius > ring.from * .8f) 20f else 4f)) return@repeat
            var free = true
            for (other in placements) if (other.cell === cell) {
                val gap = radius + other.radius + ring.cell * .01f
                if ((x - other.x).pow(2) + (y - other.y).pow(2) < gap * gap) { free = false; break }
            }
            if (free) return Placement(prop, x, y, radius, cell)
        }
        return null
    }
}
