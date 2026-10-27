package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonWorld.Cell
import com.Atom2Universe.app.games.glutton.GluttonWorld.Kind
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.math.*

/**
 * A route-finding player used to measure the game: it plans over the cell graph (doors, stairs, climbable
 * walls for its current size), heads for the richest nearby cell, eats the best bites there, vacuums when
 * charged, and writes a report plus a picture of its run to app/build/outputs/glutton/.
 */
object GluttonRobot {
    private class Plan(val cell: Cell?, val path: List<Cell>)
    /** Diagnostics: called six times a second with the robot's position, plan and steering. */
    var trace: ((String) -> Unit)? = null

    fun play(g: GluttonGame, seconds: Float, report: StringBuilder? = null): Float {
        var t = 0f; var replanAt = 0f; var blockedFor = 0f; var knocks = 0
        var plan = Plan(null, emptyList()); var lastCell: Cell? = null
        val avoided = HashMap<Cell, Float>()
        val trail = ArrayList<Pair<Float, Float>>()
        while (t < seconds && !g.finished) {
            if (trail.isEmpty() || (t * 2).toInt() > trail.size) trail += g.x to g.y
            if (g.cell !== lastCell || t >= replanAt) { plan = plan(g, avoided, t); replanAt = t + .7f; lastCell = g.cell }
            var sx = 0f; var sy = 0f
            if (plan.path.size > 1) {
                val (wx, wy) = waypoint(g, plan.path[0], plan.path[1])
                sx = wx - g.x; sy = wy - g.y
            } else {
                val food = bestFood(g, g.cell)
                if (food != null) { sx = food.x - g.x; sy = food.y - g.y }
            }
            val d = hypot(sx, sy)
            if (d > 1e-6f) {
                sx /= d; sy /= d
                // Things too big to eat that sit ahead push the steering gently aside.
                for (o in g.items) if (o.active && !o.prop.bonus && !g.canEat(o)) {
                    val ox = g.x - o.x; val oy = g.y - o.y
                    if (abs(ox) > g.radius * 6f || abs(oy) > g.radius * 6f) continue
                    val od = hypot(ox, oy).coerceAtLeast(1e-6f); val gap = od - o.radius - g.radius
                    if (gap < g.radius * 3f && ox * sx + oy * sy < 0f) { val w = (1f - gap / (g.radius * 3f)).coerceIn(0f, 1f) * .8f; sx += ox / od * w; sy += oy / od * w }
                }
                g.steer(sx, sy)
            } else g.steer(0f, 0f)
            blockedFor = if (hypot(g.vx, g.vy) < g.moveSpeed * .2f && d > g.radius * 2f) blockedFor + 1f / 60 else 0f
            if (blockedFor > 1.5f) { plan.path.getOrNull(1)?.let { avoided[it] = t + 8f }; replanAt = 0f; blockedFor = 0f }
            if (g.charge >= 1f && g.combo > 3) g.activateBurst()
            trace?.let { if ((t * 60).toInt() % 10 == 0) {
                val (wx, wy) = if (plan.path.size > 1) waypoint(g, plan.path[0], plan.path[1]) else (0f to 0f)
                it("t=%.1f pos=%.2f,%.2f v=%.2f,%.2f r=%.2f cell=%d:%d,%d lvl=%d plan=%s wp=%.1f,%.1f steer=%.2f,%.2f d=%.2f".format(t, g.x, g.y, g.vx, g.vy, g.radius, g.cell.ring.index, g.cell.col, g.cell.row, g.cell.level, plan.path.joinToString { "${it.ring.index}:${it.col},${it.row}" }, wx, wy, sx, sy, d))
            } }
            g.update(1f / 60); t += 1f / 60
            for (e in g.events) if (e.effect == GluttonGame.Effect.KNOCK) knocks++
        }
        report?.append("${g.mode} seed=${g.seed} t=${"%.0f".format(t)} size=${"%.2f".format(g.diameterMetres)}m ring=${g.ringReached} eaten=${g.eaten} bonus=${"%.0f".format(g.bonusTime)}s knocks=$knocks finished=${g.finished}\n")
        if (report != null) snapshot(g, trail, "robot_${g.mode.name.lowercase()}_${g.seed}")
        return t
    }

    /** Whether the gobbler can get from [cell] to its neighbour across [side] right now. */
    private fun passable(g: GluttonGame, cell: Cell, side: Int): Boolean {
        val world = g.world; val r = g.radius
        val to = world.neighbour(cell, side)
        if (to == null) {
            if (r >= world.exitClimb(cell)) return true
            return exitGap(cell, side) != null && cell.ring.exitHalf * 2f >= r * 1.9f
        }
        val edge = world.edge(cell, side) ?: return true
        if (r >= world.climb(cell, to, edge)) return true
        return edge.kind == Kind.STAIRS && edge.width >= r * 1.9f
    }
    private fun exitGap(cell: Cell, side: Int): Float? {
        val ring = cell.ring
        for ((exitSide, offset) in ring.exits) if (exitSide == side) {
            val centre = -ring.half + offset * ring.side
            val from = if (side == 0 || side == 2) cell.x0 else cell.y0
            if (centre >= from && centre <= from + ring.cell) return centre
        }
        return null
    }
    private fun outward(g: GluttonGame, cell: Cell, side: Int): Cell? {
        val world = g.world
        world.neighbour(cell, side)?.let { return it }
        val next = world.rings.getOrNull(cell.ring.index + 1) ?: return null
        return next.cell(next.count / 2, next.count / 2)
    }
    private fun value(g: GluttonGame, cell: Cell): Float {
        var v = 0f
        for (item in g.items) if (item.active && item.cell === cell && !item.moves && g.canEat(item)) {
            if (item.prop.bonus) { if (g.mode == GluttonGame.Mode.FRINGALE) v += if (g.remaining < 12f) 3f else .3f; continue }
            val ratio = item.radius / g.radius
            if (ratio >= .3f) v += min(ratio, 1f) * min(ratio, 1f)
        }
        return v
    }
    private fun plan(g: GluttonGame, avoided: Map<Cell, Float>, now: Float): Plan {
        val start = g.cell
        if (value(g, start) >= 1.2f) return Plan(start, listOf(start))
        val parent = HashMap<Cell, Cell>(); val depth = HashMap<Cell, Int>()
        val queue = ArrayDeque<Cell>(); queue += start; depth[start] = 0
        var best: Cell? = null; var bestScore = 0f
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst(); val d = depth.getValue(cell)
            if (cell !== start) {
                val score = value(g, cell) / (1f + d * .8f) + cell.level * .05f + cell.ring.index * .4f
                if (score > bestScore) { bestScore = score; best = cell }
            }
            if (d >= 7) continue
            for (side in 0..3) {
                if (!passable(g, cell, side)) continue
                val to = outward(g, cell, side) ?: continue
                if (to.ring.index < cell.ring.index) continue
                if ((avoided[to] ?: 0f) > now || depth.containsKey(to)) continue
                depth[to] = d + 1; parent[to] = cell; queue += to
            }
        }
        if (best == null) return Plan(start, listOf(start))
        val path = ArrayList<Cell>(); var c: Cell? = best
        while (c != null) { path.add(0, c); c = parent[c] }
        return Plan(best, path)
    }
    private fun waypoint(g: GluttonGame, from: Cell, to: Cell): Pair<Float, Float> {
        val (wx, wy) = edgePoint(g, from, to)
        if (!from.inner) return wx to wy
        // The cell wraps the previous ring: go round it by its corners instead of straight back through it.
        val inner = g.world.rings[from.ring.index - 1]
        fun crosses(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            // Within half a body of the footprint counts as through it (grazing a corner flips you back inside);
            // the starting point itself never counts, you may stand on the edge.
            val limit = inner.half + g.radius * .5f
            for (i in 2..24) { val f = i / 24f; if (abs(x0 + (x1 - x0) * f) < limit && abs(y0 + (y1 - y0) * f) < limit) return true }
            return false
        }
        // Even when tall enough to roll over it, cutting through would make the planner turn back at its edge.
        if (!crosses(g.x, g.y, wx, wy)) return wx to wy
        // Otherwise walk round the house corner by corner, hugging it (the band to the cell's walls is narrow).
        val fit = from.ring.cell / 2f - .1f - g.radius * .85f - from.ring.thickness
        val m = min(inner.half + g.radius * .7f, fit).coerceAtLeast(inner.half + .05f)
        val quarter = PI.toFloat() / 2f
        fun sector(a: Float) = Math.floorMod(floor(a / quarter + .5f).toInt(), 4)
        val a0 = atan2(g.y, g.x); val a1 = atan2(wy, wx)
        var da = a1 - a0
        while (da > PI) da -= 2f * PI.toFloat()
        while (da < -PI) da += 2f * PI.toFloat()
        if (sector(a0) == sector(a1)) return wx to wy
        val corner = sector(a0) * quarter + sign(da) * quarter / 2f
        return m * sign(cos(corner)) to m * sign(sin(corner))
    }
    private fun edgePoint(g: GluttonGame, from: Cell, to: Cell): Pair<Float, Float> {
        val world = g.world
        val side = (0..3).firstOrNull { world.neighbour(from, it) === to } ?: (0..3).first { world.neighbour(from, it) == null && outward(g, from, it) === to }
        val horizontal = side == 0 || side == 2
        val line = when (side) { 0 -> from.y0; 1 -> from.x1; 2 -> from.y1; else -> from.x0 }
        val edge = world.edge(from, side)
        val neighbour = world.neighbour(from, side)
        val climbable = if (neighbour == null) g.radius >= world.exitClimb(from) else g.radius >= world.climb(from, neighbour, edge)
        val beyond = (if (side == 1 || side == 2) 1f else -1f) * from.ring.cell * .12f
        val lo = (if (horizontal) from.x0 else from.y0) + g.radius; val hi = (if (horizontal) from.x1 else from.y1) - g.radius
        val along = if (climbable) (if (lo < hi) (if (horizontal) g.x else g.y).coerceIn(lo, hi) else (lo + hi) / 2f)
            else if (neighbour == null) exitGap(from, side) ?: (if (horizontal) (from.x0 + from.x1) / 2 else (from.y0 + from.y1) / 2)
            else (if (horizontal) from.x0 else from.y0) + (edge?.offset ?: .5f) * from.ring.cell
        return if (horizontal) along to line + beyond else line + beyond to along
    }
    private fun bestFood(g: GluttonGame, cell: Cell): GluttonGame.Item? {
        var best: GluttonGame.Item? = null; var cost = Float.MAX_VALUE
        for (item in g.items) if (item.active && item.cell === cell && !item.moves && g.canEat(item)) {
            val ratio = if (item.prop.bonus) (if (g.mode == GluttonGame.Mode.FRINGALE) (if (g.remaining < 12f) .9f else .45f) else 0f) else item.radius / g.radius
            if (ratio < .2f) continue
            val c = hypot(item.x - g.x, item.y - g.y) / (ratio + .05f)
            if (c < cost) { cost = c; best = item }
        }
        return best
    }

    /** Picture of the ring the robot ended in: terraces, walls, food (green = edible now) and the trail in red. */
    private fun snapshot(g: GluttonGame, trail: List<Pair<Float, Float>>, name: String) {
        val ring = g.cell.ring
        val size = 760; val scale = size / ring.side
        val px = IntArray(size * size)
        fun plot(x: Float, y: Float, color: Int) {
            val ix = ((x + ring.half) * scale).toInt(); val iy = ((y + ring.half) * scale).toInt()
            if (ix in 0 until size && iy in 0 until size) px[iy * size + ix] = color
        }
        fun disc(cx: Float, cy: Float, radius: Float, color: Int) {
            val r = (radius * scale).coerceAtLeast(1f)
            val x0 = ((cx + ring.half) * scale - r).toInt(); val x1 = ((cx + ring.half) * scale + r).toInt()
            val y0 = ((cy + ring.half) * scale - r).toInt(); val y1 = ((cy + ring.half) * scale + r).toInt()
            for (y in y0..y1) for (x in x0..x1) if (x in 0 until size && y in 0 until size &&
                hypot(x - (cx + ring.half) * scale, y - (cy + ring.half) * scale) <= r) px[y * size + x] = color
        }
        val palette = intArrayOf(0xD9C4AE, 0xE6D4C2, 0xF1E3D4, 0xF7ECE0, 0xFBF4EC)
        for (cell in ring.cells) {
            val color = palette[cell.level.coerceAtMost(palette.size - 1)]
            for (y in ((cell.y0 + ring.half) * scale).toInt() until ((cell.y1 + ring.half) * scale).toInt())
                for (x in ((cell.x0 + ring.half) * scale).toInt() until ((cell.x1 + ring.half) * scale).toInt())
                    if (x in 0 until size && y in 0 until size) px[y * size + x] = color
        }
        for (cell in ring.cells) for (side in 1..2) {
            val edge = g.world.edge(cell, side) ?: continue
            val horizontal = side == 2
            val line = if (horizontal) cell.y1 else cell.x1
            val from = if (horizontal) cell.x0 else cell.y0
            val steps = (ring.cell * scale).toInt()
            for (i in 0..steps) {
                val along = from + ring.cell * i / steps
                val inGap = edge.kind == Kind.STAIRS && abs(along - (from + edge.offset * ring.cell)) < edge.width / 2
                val color = when { edge.kind == Kind.CLIFF -> 0x9A8A7A; inGap && edge.kind == Kind.STAIRS -> 0xE0A050; inGap -> continue; else -> 0x4A4458 }
                if (horizontal) plot(along, line, color) else plot(line, along, color)
            }
        }
        for (item in g.items) if (item.active && item.cell?.ring === ring)
            disc(item.x, item.y, item.radius, if (item.prop.bonus) 0xF0C040 else if (g.canEat(item)) 0x50B478 else if (item.moves) 0xDC5A5A else 0x786E8C)
        for (i in 1 until trail.size) {
            val steps = 24
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                plot(trail[i - 1].first + (trail[i].first - trail[i - 1].first) * t, trail[i - 1].second + (trail[i].second - trail[i - 1].second) * t, 0xC82828)
            }
        }
        disc(g.x, g.y, g.radius, 0x1E3C5A)
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until size) { z.write(0); for (x in 0 until size) { val c = px[y * size + x]; z.write(c shr 16 and 255); z.write(c shr 8 and 255); z.write(c and 255) } }
        }
        val out = ByteArrayOutputStream(); val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream()
        DataOutputStream(hdr).apply { writeInt(size); writeInt(size); write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", hdr.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
        val dir = File("build/outputs/glutton").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(out.toByteArray())
    }
}
