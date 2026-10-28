package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Role
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import com.Atom2Universe.app.games.glutton.GluttonWorld.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Lays out one ring the way the real thing is laid out, so that it reads at a glance and is pleasant to
 * roll through: rooms with furniture against the walls and crumbs under the table, streets of houses on
 * their lots, blocks of buildings in rows, parks and squares, fields edged with hedgerows, woods,
 * mountain ranges, continents, orbits, star clusters, spiral arms and the cosmic web. Sizes always follow
 * the ring's tier (the gobbler size the spot is meant for), so the food grows as you head outwards.
 */
internal class GluttonLayout(private val world: GluttonWorld, private val ring: Ring, private val random: Random) {
    private class R(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        val w get() = x1 - x0
        val h get() = y1 - y0
        val cx get() = (x0 + x1) / 2
        val cy get() = (y0 + y1) / 2
        val short get() = min(w, h)
        fun inset(d: Double) = R(x0 + d, y0 + d, x1 - d, y1 - d)
        fun contains(x: Double, y: Double) = x in x0..x1 && y in y0..y1
    }
    private val index = GluttonGrid<Placement>(ring.half, 96)
    /**
     * Rich corners: a few spots per ring (an angle around the centre and a distance on a log scale) where food
     * piles up; elsewhere it is thin. They sit somewhere else in every ring, so growing means looking for the next one.
     */
    private val spots = if (ring.index == 0) emptyList() else List(4) { doubleArrayOf(random.nextDouble() * 2 * PI, random.nextDouble()) }
    /** Things that belong to this ring's era and its neighbours, never across the land / space divide. */
    private val pool: List<Prop>
    private val movers: List<Prop>

    init {
        val space = ring.theme.ordinal >= Theme.PLANETS.ordinal
        val themes = Theme.entries.filter {
            abs(it.ordinal - ring.theme.ordinal) <= 1 && (it.ordinal >= Theme.PLANETS.ordinal) == space && (it == ring.theme || it != Theme.ROOM)
        }.toSet()
        val all = GluttonCatalog.props.filter { it.theme in themes && it.id !in SPECIAL }
        pool = all.filter { !it.moves }.sortedBy { it.radius }
        movers = all.filter { it.moves && it.motion != Motion.ORBIT }
    }

    fun build() {
        when (ring.theme) {
            Theme.ROOM -> house()
            Theme.SUBURB -> suburb()
            Theme.CITY -> city()
            Theme.COUNTRY -> country()
            Theme.MOUNTAINS -> mountains()
            Theme.EARTH -> earth()
            Theme.PLANETS -> planets()
            Theme.STARS -> stars()
            Theme.GALAXY -> galaxy()
            Theme.COSMOS -> cosmos()
        }
        thin()
        hourglasses()
    }

    /**
     * Loose things (crumbs, garden and street bits, bodies in space) never crowd: a share of them is left out and
     * none stands closer to another than [SPACING] of its own radius, so food is something to go and look for.
     * Furniture, buildings, trees and landmarks stay where the layout put them. Only on land: the Earth and space
     * are sparse by design already.
     */
    private fun thin() {
        if (ring.theme.ordinal >= Theme.EARTH.ordinal) return
        val kept = GluttonGrid<Placement>(ring.half, 96)
        val (loose, fixed) = ring.placements.partition { it.prop.role in LOOSE }
        ring.placements.clear()
        for (p in fixed) { ring.placements += p; kept.add(p, p.x, p.y, p.radius) }
        // The house is small and its crumbs are all there is to grow on: a little apart, never left out.
        val spacing = if (ring.index == 0) HOUSE_SPACING else SPACING
        for (p in loose.sortedByDescending { it.radius }) {
            if (ring.index > 0 && chance(THIN)) continue
            val reach = p.radius * (1 + spacing)
            var free = true
            kept.query(p.x - reach, p.y - reach, p.x + reach, p.y + reach) { o ->
                if (free) {
                    val need = p.radius + o.radius + min(p.radius, o.radius) * spacing
                    if ((p.x - o.x).pow(2) + (p.y - o.y).pow(2) < need * need) free = false
                }
            }
            if (free) { ring.placements += p; kept.add(p, p.x, p.y, p.radius) }
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private fun rand(a: Double, b: Double) = a + random.nextDouble() * (b - a)
    /** How much food a spot gets: from 0.15 far from every rich corner to 1 in the middle of one. */
    private fun richness(x: Double, y: Double): Double {
        if (spots.isEmpty()) return 1.0
        val angle = atan2(y, x)
        val start = max(ring.inner, 1e-9)
        val depth = (ln(ring.reach(x, y).coerceAtLeast(start) / start) / ln(ring.half / start)).coerceIn(0.0, 1.0)
        var sum = 0.0
        for (spot in spots) {
            var da = abs(angle - spot[0]) % (2 * PI); if (da > PI) da = 2 * PI - da
            val dd = depth - spot[1]
            sum += exp(-(da / .55).pow(2) - (dd / .3).pow(2))
        }
        return (.15 + sum).coerceAtMost(1.0)
    }
    /** Keeps a piece of food at (x, y) with the probability of its richness. */
    private fun rich(x: Double, y: Double) = random.nextDouble() < richness(x, y)
    private fun gauss() = (random.nextDouble() + random.nextDouble() + random.nextDouble() - 1.5) * 1.15
    private fun chance(p: Double) = random.nextDouble() < p
    private fun patch(ground: Ground, r: R, round: Boolean = false) { if (r.w > 0 && r.h > 0) ring.patches += Patch(ground, r.x0, r.y0, r.x1, r.y1, round) }

    /** A prop of one of [roles] whose size range holds [target] (or comes closest), with a little variety; returns it with its radius. */
    private fun pick(roles: Collection<Role>, target: Double, filter: (Prop) -> Boolean = { true }): Pair<Prop, Double>? {
        var best = Double.MAX_VALUE
        val distances = ArrayList<Pair<Prop, Double>>(8)
        for (prop in pool) {
            if (prop.role !in roles || !filter(prop)) continue
            val lo = prop.radius / prop.spread; val hi = prop.radius * prop.spread
            val d = abs(ln(target / target.coerceIn(lo, hi)))
            if (d < 1.1) { distances += prop to d; best = min(best, d) }
        }
        val near = distances.filter { it.second <= best + .3 }
        if (near.isEmpty()) return null
        val prop = near[random.nextInt(near.size)].first
        return prop to target.coerceIn(prop.radius / prop.spread, prop.radius * prop.spread)
    }

    /** True when a thing of radius [r] fits at (x, y): inside this ring, clear of the inner ring, of a cover and of everything placed. */
    private fun fits(x: Double, y: Double, r: Double, gap: Double): Boolean {
        if (ring.index == 0) { if (abs(x) > ring.half - r * .6 || abs(y) > ring.half - r * .6) return false }
        else {
            if (ring.reach(x, y) > ring.half - r) return false
            if (!world.outsideInner(ring, x, y, r * .9)) return false
            if (ring.cover != null && hypot(x, y) < ring.coverRadius * 1.08 + r) return false
        }
        var free = true
        index.query(x - r * (1 + gap), y - r * (1 + gap), x + r * (1 + gap), y + r * (1 + gap)) { o ->
            if (free) {
                val need = r + o.radius + max(r, o.radius) * gap
                val dx = x - o.x; val dy = y - o.y
                if (dx * dx + dy * dy < need * need) free = false
            }
        }
        return free
    }

    private fun put(prop: Prop, x: Double, y: Double, r: Double, gap: Double = .08, tilt: Float = 0f): Placement? {
        if (!fits(x, y, r, gap)) return null
        val angle = if (tilt > 0f) (random.nextFloat() * 2f - 1f) * tilt else 0f
        val p = Placement(prop, x, y, r, ring, angle, random.nextBoolean())
        ring.placements += p; index.add(p, x, y, r)
        return p
    }

    private fun place(roles: Collection<Role>, target: Double, x: Double, y: Double, gap: Double = .08, tilt: Float = 0f, filter: (Prop) -> Boolean = { true }): Placement? {
        val (prop, r) = pick(roles, target, filter) ?: return null
        return put(prop, x, y, r, gap, tilt)
    }

    /** A little heap of [count] things from [lo] to [hi] radius around a point, the way things pile up. */
    private fun heap(cx: Double, cy: Double, spread: Double, lo: Double, hi: Double, count: Int, roles: Collection<Role>,
        gap: Double = .25, everywhere: Boolean = false, filter: (Prop) -> Boolean = { true }) {
        repeat(count) {
            if (!everywhere && !rich(cx, cy)) return@repeat
            val target = lo * (hi / lo).pow(random.nextDouble())
            repeat(6) { if (place(roles, target, cx + gauss() * spread, cy + gauss() * spread, gap, 20f, filter) != null) return@repeat }
        }
    }

    /** Things of [roles] spread over a rectangle at a spacing of about [spacing] tiers. */
    private fun scatter(r: R, roles: Collection<Role>, spacing: Double, lo: Double = .25, hi: Double = 1.0, tilt: Float = 12f) {
        val t = ring.tier(r.cx, r.cy)
        val count = (r.w * r.h / (spacing * t).pow(2)).roundToInt().coerceIn(0, 400)
        repeat(count) {
            val x = rand(r.x0, r.x1); val y = rand(r.y0, r.y1)
            if (rich(x, y)) place(roles, t * rand(lo, hi), x, y, .3, tilt)
        }
    }

    private fun mover(prop: Prop, x: Double, y: Double, r: Double): Placement? {
        if (!fits(x, y, r, .2)) return null
        return Placement(prop, x, y, r, ring, 0f, random.nextBoolean()).also { ring.movers += it }
    }

    /** One vehicle that suits [lane] (narrow enough for the road, sized for the spot) driving along it. */
    private fun patrol(lane: Lane, width: Double, target: Double) {
        val candidates = movers.filter { it.motion == Motion.PATROL && it.radius < width * .45 && abs(ln(it.radius / target)) < 1.6 }
        if (candidates.isEmpty()) return
        val prop = candidates[random.nextInt(candidates.size)]
        val at = random.nextDouble() * lane.length
        val out = DoubleArray(4); lane.at(at, out)
        mover(prop, out[0], out[1], prop.radius)?.apply { this.lane = lane; laneAt = at }
    }

    /** A wanderer or chaser of a size close to [target] that keeps to [box]. */
    private fun roamer(box: R, target: Double, motion: Motion) {
        val candidates = movers.filter { it.motion == motion && abs(ln(it.radius / target)) < 1.2 && it.radius * 3 < box.short }
        if (candidates.isEmpty()) return
        val prop = candidates[random.nextInt(candidates.size)]
        repeat(8) {
            val p = mover(prop, rand(box.x0 + prop.radius, box.x1 - prop.radius), rand(box.y0 + prop.radius, box.y1 - prop.radius), prop.radius)
            if (p != null) { p.box = doubleArrayOf(box.x0, box.y0, box.x1, box.y1); return }
        }
    }

    /** A straight road from one rectangle: asphalt, a dashed centre line and a lane along it. */
    private fun road(r: R, traffic: Double = .35) {
        patch(Ground.ASPHALT, r)
        val horizontal = r.w >= r.h
        val width = r.short
        val points = if (horizontal) doubleArrayOf(r.x0, r.cy, r.x1, r.cy) else doubleArrayOf(r.cx, r.y0, r.cx, r.y1)
        ring.markings += Marking(points[0], points[1], points[2], points[3], width * .05)
        val lane = Lane(points, false)
        ring.lanes += lane
        if (chance(traffic)) patrol(lane, width, ring.tier(r.cx, r.cy) * .8)
    }

    /** A square ring road of width [w] just outside the half-size [a], with its loop lane and a few vehicles. */
    private fun ringRoad(a: Double, w: Double, vehicles: Int = 3) {
        val b = a + w; val m = a + w / 2
        patch(Ground.ASPHALT, R(-b, -b, b, -a)); patch(Ground.ASPHALT, R(-b, a, b, b))
        patch(Ground.ASPHALT, R(-b, -a, -a, a)); patch(Ground.ASPHALT, R(a, -a, b, a))
        ring.markings += Marking(-m, -m, m, -m, w * .05); ring.markings += Marking(m, -m, m, m, w * .05)
        ring.markings += Marking(m, m, -m, m, w * .05); ring.markings += Marking(-m, m, -m, -m, w * .05)
        val lane = Lane(doubleArrayOf(-m, -m, m, -m, m, m, -m, m), true)
        ring.lanes += lane
        repeat(vehicles) { patrol(lane, w, ring.tier(m, 0.0) * .8) }
    }

    /**
     * The square band between half-sizes [a] and [b], cut into blocks about [length] long along its four
     * sides with cross streets of width [street] between them; [fill] lays out each block.
     */
    private fun band(a: Double, b: Double, length: () -> Double, street: Double, fill: (R) -> Unit) {
        val sides = listOf(R(-b, -b, b, -a), R(-b, a, b, b), R(-b, -a, -a, a), R(a, -a, b, a))
        for (side in sides) {
            val alongX = side.w >= side.h
            val start = if (alongX) side.x0 else side.y0
            val end = if (alongX) side.x1 else side.y1
            var p = start
            while (p < end - 1e-9) {
                val l = length()
                var e = min(p + l, end)
                if (end - e < l * .5) e = end
                fill(if (alongX) R(p, side.y0, e, side.y1) else R(side.x0, p, side.x1, e))
                if (e >= end) break
                val s = min(e + street, end)
                if (street > 0) road(if (alongX) R(e, side.y0, s, side.y1) else R(side.x0, e, side.x1, s))
                p = s
            }
        }
    }

    /**
     * The round band between radii [a] and [b], cut into blocks about [length] long around it with gaps of
     * [street] between them; [fill] lays out each block (a square standing in the band, at a slight angle to it).
     */
    private fun roundBand(a: Double, b: Double, length: () -> Double, street: Double, fill: (R) -> Unit) {
        val mid = (a + b) / 2; val depth = b - a
        val around = 2 * PI * mid
        val start = random.nextDouble() * 2 * PI
        var p = 0.0
        while (p < around - depth * .5) {
            val l = min(length(), around - p)
            val angle = start + (p + l / 2) / mid
            // The largest square whose corners stay in the band whatever its angle to the radius.
            val side = min(depth * .7, l * .9)
            val cx = cos(angle) * mid; val cy = sin(angle) * mid
            fill(R(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2))
            p += l + street
        }
    }

    /** A road going right round the centre at [radius], [width] wide, with a few vehicles on its loop. */
    private fun roundRoad(radius: Double, width: Double, vehicles: Int = 2) {
        ring.annuli += Annulus(Ground.ASPHALT, radius, width)
        val n = 96
        val points = DoubleArray(n * 2) { i -> val a = 2 * PI * (i / 2) / n; if (i % 2 == 0) cos(a) * radius else sin(a) * radius }
        val lane = Lane(points, true)
        ring.lanes += lane
        repeat(vehicles) { patrol(lane, width, ring.tier(radius, 0.0) * .8) }
    }

    /** Street furniture and parked cars along the edges of a block, just inside the pavement. */
    private fun kerb(r: R, inset: Double, spacing: Double, t: Double, cars: Boolean = true) {
        val edges = listOf(
            doubleArrayOf(r.x0, r.y0 + inset, r.x1, r.y0 + inset), doubleArrayOf(r.x0, r.y1 - inset, r.x1, r.y1 - inset),
            doubleArrayOf(r.x0 + inset, r.y0, r.x0 + inset, r.y1), doubleArrayOf(r.x1 - inset, r.y0, r.x1 - inset, r.y1))
        for (e in edges) {
            val length = hypot(e[2] - e[0], e[3] - e[1])
            var s = spacing * rand(.3, 1.0)
            var k = 0
            while (s < length - spacing * .3) {
                val f = s / length
                val x = e[0] + (e[2] - e[0]) * f; val y = e[1] + (e[3] - e[1]) * f
                if (!rich(x, y)) { s += spacing * rand(.7, 1.3); k++; continue }
                if (cars && k % 3 == 2) place(listOf(Role.VEHICLE), t * rand(.35, .7), x, y, .15)
                else place(listOf(Role.STREET), t * rand(.2, .5), x, y, .15)
                s += spacing * rand(.7, 1.3); k++
            }
        }
    }

    /** A spot in this ring's band of space, at a distance spread evenly on a log scale (as many things per decade). */
    private fun spaceSpot(): DoubleArray {
        val d = ring.inner * (ring.half / ring.inner).pow(random.nextDouble())
        if (ring.round) { val a = random.nextDouble() * 2 * PI; return doubleArrayOf(cos(a) * d, sin(a) * d) }
        val along = rand(-d, d)
        return when (random.nextInt(4)) {
            0 -> doubleArrayOf(along, -d); 1 -> doubleArrayOf(along, d); 2 -> doubleArrayOf(-d, along); else -> doubleArrayOf(d, along)
        }
    }

    /** Free-floating things all over the ring, about [spacing] tiers apart wherever you are. */
    private fun spaceScatter(spacing: Double, roles: Collection<Role> = listOf(Role.BODY), filter: (Prop) -> Boolean = { true }) {
        // Twice the tries: the rich corners keep most of them, the rest of space stays thin.
        val count = (2 * 8.0 * 169.0 / (spacing * spacing) * ln(ring.half / ring.inner)).roundToInt()
        repeat(count) {
            val (x, y) = spaceSpot().let { it[0] to it[1] }
            if (!rich(x, y)) return@repeat
            val t = ring.tier(x, y)
            // Spots are spread for a ring whose tier is about a thirteenth of the distance; where it is smaller
            // (the wide solar system), several things share the spot so the food stays as dense for you.
            val d = ring.reach(x, y)
            val share = (d / (13 * t)).pow(2).coerceIn(1.0, 12.0)
            repeat(share.toInt() + if (chance(share % 1.0)) 1 else 0) { k ->
                val target = if (chance(.1)) t * rand(1.1, 1.9) else t * rand(.2, 1.0)
                val spread = if (k == 0) 0.0 else t * 6
                place(roles, target, x + gauss() * spread, y + gauss() * spread, .5, 0f, filter)
            }
        }
    }

    private fun hourglasses() {
        val count = if (ring.index == 0) 3 else 4
        var tries = 0
        while (ring.hourglasses.size < count && tries++ < count * 30) {
            val x: Double; val y: Double
            if (ring.index == 0) { x = rand(-7.0, 7.0); y = rand(-7.0, 7.0) }
            else if (ring.theme.ordinal >= Theme.PLANETS.ordinal) { val s = spaceSpot(); x = s[0]; y = s[1] }
            else { val s = spaceSpot(); x = s[0]; y = s[1] }
            val r = ring.tier(x, y) * .45
            if (hypot(x, y) < .4) continue
            if (!fits(x, y, r, .3)) continue
            if (ring.walls.any { w -> abs((if (w.horizontal) y else x) - w.line) < r + w.thickness && (if (w.horizontal) x else y) in w.from - r..w.until + r }) continue
            ring.hourglasses += Placement(GluttonCatalog.hourglass, x, y, r, ring)
        }
    }

    // ───────────────────────────── your house ─────────────────────────────

    private class Room(val r: R) {
        var type = 'K'
        var rank = 0
        var tier = .02
        val links = ArrayList<Room>()
    }

    /**
     * Your house, 16 m square: rooms cut by walls with doorways, two ways out, floors per room, furniture
     * against the walls, a table and chairs, and heaps of small things graded from the crumbs around you
     * to the toys and books of the far rooms.
     */
    private fun house() {
        val rooms = ArrayList<Room>()
        fun split(r: R, depth: Int) {
            val big = r.w * r.h > 50 || max(r.w, r.h) > 7.6
            if (!big || depth > 4) { rooms += Room(r); return }
            val alongX = if (abs(r.w - r.h) < 1.0) random.nextBoolean() else r.w > r.h
            val lo = (if (alongX) r.x0 else r.y0) + 3.2; val hi = (if (alongX) r.x1 else r.y1) - 3.2
            if (lo >= hi) { rooms += Room(r); return }
            var line = rand(lo + (hi - lo) * .2, hi - (hi - lo) * .2)
            // Keep the start (the origin) well clear of any wall.
            if (abs(line) < 1.4) line = if (line >= 0 && 1.4 <= hi) 1.4 else if (-1.4 >= lo) -1.4 else if (1.4 <= hi) 1.4 else line
            if (alongX) { split(R(r.x0, r.y0, line, r.y1), depth + 1); split(R(line, r.y0, r.x1, r.y1), depth + 1) }
            else { split(R(r.x0, r.y0, r.x1, line), depth + 1); split(R(r.x0, line, r.x1, r.y1), depth + 1) }
        }
        split(R(-ring.half, -ring.half, ring.half, ring.half), 0)
        val kitchen = rooms.first { it.r.contains(0.0, 0.0) }
        // Shared walls long enough for a doorway.
        class Shared(val a: Room, val b: Room, val horizontal: Boolean, val line: Double, val from: Double, val until: Double)
        val shared = ArrayList<Shared>()
        for (i in rooms.indices) for (j in i + 1 until rooms.size) {
            val a = rooms[i].r; val b = rooms[j].r
            if (abs(a.x1 - b.x0) < 1e-6 || abs(b.x1 - a.x0) < 1e-6) {
                val from = max(a.y0, b.y0); val until = min(a.y1, b.y1)
                if (until - from >= 2.0) shared += Shared(rooms[i], rooms[j], false, if (abs(a.x1 - b.x0) < 1e-6) a.x1 else a.x0, from, until)
            } else if (abs(a.y1 - b.y0) < 1e-6 || abs(b.y1 - a.y0) < 1e-6) {
                val from = max(a.x0, b.x0); val until = min(a.x1, b.x1)
                if (until - from >= 2.0) shared += Shared(rooms[i], rooms[j], true, if (abs(a.y1 - b.y0) < 1e-6) a.y1 else a.y0, from, until)
            }
        }
        for (s in shared) { s.a.links += s.b; s.b.links += s.a }
        // Rooms by distance from the kitchen: the further, the bigger the things lying about.
        val order = ArrayList<Room>(); val queue = ArrayDeque<Room>()
        val parent = HashMap<Room, Room>()
        queue += kitchen; order += kitchen
        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            for (next in room.links.shuffled(random)) if (next !in order) { order += next; parent[next] = room; queue += next }
        }
        for (room in rooms) if (room !in order) order += room
        val types = "LCOTBLB"
        for ((rank, room) in order.withIndex()) {
            room.rank = rank
            room.type = if (rank == 0) 'K' else types[(rank - 1) % types.length]
            room.tier = .03 * (.42 / .03).pow(rank / max(1.0, order.size - 1.0))
        }
        // Doorways: every room reachable from the kitchen, plus a few loops.
        val gaps = HashMap<Pair<Boolean, Double>, ArrayList<DoubleArray>>()
        fun door(horizontal: Boolean, line: Double, from: Double, until: Double, width: Double): Double {
            val centre = rand(from + width / 2 + .4, until - width / 2 - .4)
            gaps.getOrPut(horizontal to line) { ArrayList() } += doubleArrayOf(centre - width / 2, centre + width / 2)
            return centre
        }
        for (s in shared) if (parent[s.a] === s.b || parent[s.b] === s.a || chance(.3)) door(s.horizontal, s.line, s.from, s.until, 1.5)
        // Two ways out, on different sides, never from the kitchen if another room touches the outside.
        val outside = ArrayList<Triple<Room, Int, Pair<Double, Double>>>()
        for (room in rooms) {
            val r = room.r
            if (r.y0 <= -ring.half + 1e-6) outside += Triple(room, 0, r.x0 to r.x1)
            if (r.x1 >= ring.half - 1e-6) outside += Triple(room, 1, r.y0 to r.y1)
            if (r.y1 >= ring.half - 1e-6) outside += Triple(room, 2, r.x0 to r.x1)
            if (r.x0 <= -ring.half + 1e-6) outside += Triple(room, 3, r.y0 to r.y1)
        }
        val exits = outside.filter { it.third.second - it.third.first >= 2.6 }.shuffled(random).sortedBy { if (it.first === kitchen) 1 else 0 }
        val used = HashSet<Int>()
        for ((_, side, span) in exits) {
            if (side in used || used.size >= 2) continue
            used += side
            val line = if (side == 0 || side == 3) -ring.half else ring.half
            val centre = door(side == 0 || side == 2, line, span.first, span.second, 1.7)
            val out = line + (if (line < 0) -1.0 else 1.0)
            ring.doors += if (side == 0 || side == 2) doubleArrayOf(centre, out) else doubleArrayOf(out, centre)
        }
        ring.exits.clear(); ring.exits += used
        // Walls: every room side, merged per line, minus the doorways.
        val lines = HashMap<Pair<Boolean, Double>, ArrayList<DoubleArray>>()
        for (room in rooms) {
            val r = room.r
            lines.getOrPut(true to r.y0) { ArrayList() } += doubleArrayOf(r.x0, r.x1)
            lines.getOrPut(true to r.y1) { ArrayList() } += doubleArrayOf(r.x0, r.x1)
            lines.getOrPut(false to r.x0) { ArrayList() } += doubleArrayOf(r.y0, r.y1)
            lines.getOrPut(false to r.x1) { ArrayList() } += doubleArrayOf(r.y0, r.y1)
        }
        for ((key, spans) in lines) {
            spans.sortBy { it[0] }
            val merged = ArrayList<DoubleArray>()
            for (s in spans) if (merged.isNotEmpty() && s[0] <= merged.last()[1] + 1e-6) merged.last()[1] = max(merged.last()[1], s[1]) else merged += s.copyOf()
            val cuts = gaps[key].orEmpty().sortedBy { it[0] }
            for (m in merged) {
                var from = m[0]
                for (c in cuts) if (c[1] > from && c[0] < m[1]) { if (c[0] > from) ring.walls += Wall(key.first, key.second, from, c[0], WALL, WALL_HEIGHT); from = c[1] }
                if (from < m[1]) ring.walls += Wall(key.first, key.second, from, m[1], WALL, WALL_HEIGHT)
            }
        }
        for (room in rooms) patch(FLOORS[room.type] ?: Ground.WOOD, room.r)
        val doorways = gaps.flatMap { (key, list) -> list.map { Triple(key.first, key.second, it) } }
        fun nearDoor(x: Double, y: Double, r: Double) = doorways.any { (horizontal, line, span) ->
            val across = abs((if (horizontal) y else x) - line); val along = if (horizontal) x else y
            across < r + 1.0 && along > span[0] - r - .3 && along < span[1] + r + .3
        }
        for (room in order) furnish(room, ::nearDoor)
        // Crumbs within reach of the very first second, then heaps graded room by room.
        heap(0.0, 0.0, .3, .002, .008, 34, FOOD_ROLES, .6, everywhere = true) { 'K' in it.rooms }
        heap(0.0, 0.0, .7, .003, .014, 30, FOOD_ROLES, .5, everywhere = true) { 'K' in it.rooms }
        for (room in order) {
            val r = room.r.inset(.25)
            val heaps = 3 + (r.w * r.h / 9).roundToInt()
            val anchors = ring.placements.filter { room.r.contains(it.x, it.y) && it.radius > .3 }
            repeat(heaps) { k ->
                val a = if (anchors.isNotEmpty() && k % 2 == 0) anchors[random.nextInt(anchors.size)] else null
                val cx = a?.x ?: rand(r.x0, r.x1); val cy = a?.y ?: rand(r.y0, r.y1)
                val lo = room.tier * .25; val hi = room.tier * 1.3
                heap(cx, cy, .3 + room.tier * 3, lo, hi, 14, FOOD_ROLES, .4, everywhere = true) { room.type in it.rooms }
            }
        }
        // Things that move about.
        for (room in order) {
            val box = room.r.inset(.3)
            when (room.type) {
                'C' -> {
                    roamer(box, .1, Motion.WANDER); roamer(box, .19, Motion.CHASE)
                    for (k in 0..1) {
                        val y = rand(box.y0 + .3, box.y1 - .3)
                        val lane = Lane(doubleArrayOf(box.x0 + .2, y, box.x1 - .2, y), false)
                        val prop = movers.filter { it.motion == Motion.PATROL && it.theme == Theme.ROOM }.randomOrNull(random) ?: continue
                        val at = random.nextDouble() * lane.length; val out = DoubleArray(4); lane.at(at, out)
                        mover(prop, out[0], out[1], prop.radius)?.apply { this.lane = lane; laneAt = at }
                    }
                }
                'L' -> roamer(box, .16, Motion.WANDER)
                'K', 'B' -> if (room.rank > 0) roamer(box, .17, Motion.CHASE)
            }
        }
    }

    /** Furniture of a room's kind along its walls, never in front of a doorway; tables and beds where they belong. */
    private fun furnish(room: Room, nearDoor: (Double, Double, Double) -> Boolean) {
        val r = room.r
        val list = FURNITURE[room.type] ?: return
        val centres = list.filter { GluttonCatalog.byId.getValue(it).role == Role.CENTRE }
        if (room.type == 'K') {
            // The table stands a little away from where you wake up, its chairs around it.
            val table = GluttonCatalog.byId.getValue(if (r.w * r.h > 18 && chance(.5)) "kitchen_island" else "dining_table")
            repeat(20) {
                val x = rand(r.x0 + table.radius + .8, r.x1 - table.radius - .8); val y = rand(r.y0 + table.radius + .8, r.y1 - table.radius - .8)
                if (hypot(x, y) < table.radius + 1.0 || nearDoor(x, y, table.radius)) return@repeat
                val p = put(table, x, y, table.radius) ?: return@repeat
                val seat = GluttonCatalog.byId.getValue(if (table.id == "dining_table") "dining_chair" else "wooden_stool")
                for ((dx, dy) in listOf(-1.0 to 0.0, 1.0 to 0.0, 0.0 to -1.0, 0.0 to 1.0)) {
                    val d = p.radius + seat.radius * .75
                    put(seat, x + dx * d, y + dy * d, seat.radius, .02)
                }
                return@repeat
            }
        } else for (id in centres) {
            val prop = GluttonCatalog.byId.getValue(id)
            repeat(12) {
                val x = rand(r.x0 + prop.radius + .5, r.x1 - prop.radius - .5); val y = rand(r.y0 + prop.radius + .3, r.y0 + prop.radius * 1.3 + .4)
                if (!nearDoor(x, y, prop.radius) && put(prop, x, y, prop.radius) != null) return@repeat
            }
        }
        for (id in list) {
            val prop = GluttonCatalog.byId.getValue(id)
            if (prop.role != Role.FURNITURE) continue
            val rad = prop.radius
            var done = false
            for (side in (0..3).shuffled(random)) {
                if (done) break
                val horizontal = side == 0 || side == 2
                val from = (if (horizontal) r.x0 else r.y0) + rad + .15
                val until = (if (horizontal) r.x1 else r.y1) - rad - .15
                if (until <= from) continue
                val off = rad * .75 + WALL + .05
                val across = when (side) { 0 -> r.y0 + off; 1 -> r.x1 - off; 2 -> r.y1 - off; else -> r.x0 + off }
                var along = rand(from, until)
                for (k in 0 until 6) {
                    val x = if (horizontal) along else across; val y = if (horizontal) across else along
                    if (!nearDoor(x, y, rad) && put(prop, x, y, rad, .03) != null) { done = true; break }
                    along = rand(from, until)
                }
            }
        }
    }

    // ───────────────────────────── suburb and town ─────────────────────────────

    /** Your garden around the house, then streets of houses on their lots, a park here and there. */
    private fun suburb() {
        val garden = 18.0
        heap(0.0, -11.0, 2.0, .25, .9, 10, listOf(Role.YARD), everywhere = true); heap(0.0, 11.0, 2.0, .25, .9, 10, listOf(Role.YARD), everywhere = true)
        heap(-11.0, 0.0, 2.0, .25, .9, 10, listOf(Role.YARD), everywhere = true); heap(11.0, 0.0, 2.0, .25, .9, 10, listOf(Role.YARD), everywhere = true)
        for (k in 0 until 12) heap(rand(-garden + 2, garden - 2), rand(-garden + 2, garden - 2), 1.4, .25, 1.2, 6, listOf(Role.YARD), everywhere = true)
        for ((sx, sy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) place(listOf(Role.TREE), rand(2.0, 3.5), sx * 14.5, sy * 14.5)
        // A path from each way out of the house to the street.
        for (side in world.rings[0].exits) {
            val r = when (side) { 0 -> R(-1.0, -garden, 1.0, -ring.inner); 1 -> R(ring.inner, -1.0, garden, 1.0); 2 -> R(-1.0, ring.inner, 1.0, garden); else -> R(-garden, -1.0, -ring.inner, 1.0) }
            patch(Ground.PAVEMENT, r)
        }
        ringRoad(garden, 7.0, 2)
        band(25.0, 53.0, { rand(38.0, 60.0) }, 7.0) { suburbBlock(it) }
        ringRoad(53.0, 7.0, 3)
        band(60.0, ring.half, { rand(30.0, 50.0) }, 6.0) { suburbBlock(it) }
    }

    private fun suburbBlock(block: R) {
        val t = ring.tier(block.cx, block.cy)
        patch(Ground.PAVEMENT, block)
        val inner = block.inset(1.8)
        patch(Ground.GRASS, inner)
        kerb(block, .9, rand(9.0, 14.0), t, cars = true)
        val roll = random.nextDouble()
        when {
            roll < .7 -> lots(inner, t)
            roll < .85 -> park(inner, t)
            roll < .93 -> playground(inner, t)
            else -> shops(inner, t)
        }
        if (chance(.35)) roamer(inner, .31, Motion.WANDER)
        scatter(inner, listOf(Role.YARD, Role.STREET, Role.TREE, Role.VEHICLE), 3.2, .2, .9)
    }

    /** Lots side by side along the long edges: a house near the street, its garden behind, a car in the drive. */
    private fun lots(r: R, t: Double) {
        val alongX = r.w >= r.h
        val length = if (alongX) r.w else r.h
        val depth = if (alongX) r.h else r.w
        val rows = if (depth >= 20) 2 else 1
        val rowDepth = depth / rows
        val count = max(1, (length / rand(13.0, 18.0)).toInt())
        val lot = length / count
        for (row in 0 until rows) {
            val front = if (row == 0) (if (alongX) r.y0 else r.x0) else (if (alongX) r.y1 else r.x1)
            val dir = if (row == 0) 1.0 else -1.0
            fun at(along: Double, across: Double) = if (alongX) (r.x0 + along) to (front + dir * across) else (front + dir * across) to (r.y0 + along)
            for (i in 0 until count) {
                val mid = lot * (i + .5)
                val houseR = min(lot * .34, rowDepth * .36)
                val (hx, hy) = at(mid, 1.5 + houseR)
                place(listOf(Role.BUILDING), houseR, hx, hy, .05) { it.radius > houseR * .55 }
                val (bx, by) = at(mid + lot * .38, .8)
                place(listOf(Role.STREET), t * rand(.25, .45), bx, by, .1)
                if (chance(.4)) { val (cx, cy) = at(mid - lot * .36, 2.8); place(listOf(Role.VEHICLE), t * rand(.6, 1.0), cx, cy, .05) }
                val (gx, gy) = at(mid, rowDepth * .78)
                heap(gx, gy, lot * .2, t * .2, t * .9, 5, listOf(Role.YARD))
                if (chance(.45)) { val (tx, ty) = at(mid + lot * rand(-.35, .35), rowDepth * .9); place(listOf(Role.TREE), t * rand(1.0, 2.2), tx, ty, .1) }
            }
        }
    }

    /** Trees in loose rows around two crossing paths, a centrepiece where they meet, benches and lamps along them. */
    private fun park(r: R, t: Double, fountain: Boolean = true) {
        val path = r.short * .1
        patch(Ground.PAVEMENT, R(r.cx - path / 2, r.y0, r.cx + path / 2, r.y1)); patch(Ground.PAVEMENT, R(r.x0, r.cy - path / 2, r.x1, r.cy + path / 2))
        if (fountain) place(listOf(Role.LANDMARK), min(t * rand(.9, 1.6), r.short * .18), r.cx, r.cy, .1)
        val (tree, treeR) = pick(listOf(Role.TREE), t * rand(.9, 2.0)) ?: (null to t)
        val step = max(treeR * 2.6, t * 2.5)
        var y = r.y0 + step / 2
        while (y < r.y1) {
            var x = r.x0 + step / 2
            while (x < r.x1) {
                if (abs(x - r.cx) > path + treeR && abs(y - r.cy) > path + treeR) {
                    val jx = x + rand(-.2, .2) * step; val jy = y + rand(-.2, .2) * step
                    if (tree != null && chance(.8)) put(tree, jx, jy, treeR * rand(.85, 1.15), .05)
                    else heap(jx, jy, step * .2, t * .2, t * .8, 3, listOf(Role.YARD))
                }
                x += step
            }
            y += step
        }
        for (k in 0 until 6) {
            val along = rand(.1, .9)
            if (k % 2 == 0) place(listOf(Role.YARD, Role.STREET), t * rand(.3, .6), r.x0 + r.w * along, r.cy + path * .9, .1)
            else place(listOf(Role.YARD, Role.STREET), t * rand(.3, .6), r.cx + path * .9, r.y0 + r.h * along, .1)
        }
    }

    private fun playground(r: R, t: Double) {
        val step = t * 3.2
        var y = r.y0 + step / 2
        while (y < r.y1) { var x = r.x0 + step / 2; while (x < r.x1) { place(listOf(Role.YARD), t * rand(.5, 1.3), x + rand(-.3, .3) * step, y + rand(-.3, .3) * step, .15); x += step }; y += step }
    }

    private fun shops(r: R, t: Double) {
        patch(Ground.ASPHALT, r.inset(r.short * .05))
        val alongX = r.w >= r.h
        val size = min(r.short * .3, t * 2.2)
        var p = (if (alongX) r.x0 else r.y0) + size
        val end = if (alongX) r.x1 else r.y1
        while (p < end - size) {
            val placed = if (alongX) place(listOf(Role.BUILDING, Role.STREET), size * rand(.6, 1.0), p, r.y0 + size * 1.1, .05)
                else place(listOf(Role.BUILDING, Role.STREET), size * rand(.6, 1.0), r.x0 + size * 1.1, p, .05)
            p += (placed?.radius ?: size * .5) * 2.2
        }
        parking(R(if (alongX) r.x0 else r.x0 + size * 2.4, if (alongX) r.y0 + size * 2.4 else r.y0, r.x1, r.y1), t, patchIt = false)
    }

    private fun parking(r: R, t: Double, patchIt: Boolean = true) {
        if (patchIt) patch(Ground.ASPHALT, r.inset(r.short * .04))
        val (car, carR) = pick(listOf(Role.VEHICLE), t * rand(.5, .9)) ?: return
        val stepX = carR * 2.4; val stepY = carR * 3.2
        var y = r.y0 + stepY * .6
        while (y < r.y1 - carR) {
            var x = r.x0 + stepX * .6
            while (x < r.x1 - carR) {
                if (chance(.75)) { val other = pick(listOf(Role.VEHICLE), carR * rand(.8, 1.2)); put(other?.first ?: car, x, y, other?.second ?: carR, .02) }
                x += stepX
            }
            y += stepY
        }
    }

    /** The town: ring roads and avenues, blocks of buildings in rows, squares, parks and car parks, bigger and bigger outwards. */
    private fun city() {
        var a = ring.inner
        var street = 12.0
        ringRoad(a, street, 3); a += street
        while (a < ring.half - 1) {
            val t = ring.tier(a, 0.0)
            val w = (4.6 * t).coerceIn(36.0, 280.0)
            var b = min(a + w, ring.half)
            if (ring.half - b < w * .45) b = ring.half
            street = (w * .17).coerceIn(10.0, 40.0)
            band(a, b, { w * rand(1.1, 2.1) }, street) { cityBlock(it) }
            if (b >= ring.half - 1) break
            if (b + street >= ring.half) break
            ringRoad(b, street, 3)
            a = b + street
        }
    }

    private fun cityBlock(block: R) {
        val t = ring.tier(block.cx, block.cy)
        val walk = (block.short * .06).coerceIn(2.0, 10.0)
        kerb(block, walk * .5, t * rand(3.0, 4.5), t, cars = true)
        val r = block.inset(walk)
        val roll = random.nextDouble()
        when {
            roll < .56 -> buildings(r, t)
            roll < .68 -> { patch(Ground.GRASS, r); park(r, t) }
            roll < .8 -> plaza(r, t)
            roll < .9 -> parking(r, t)
            else -> civic(r, t)
        }
        if (chance(.12)) roamer(r, t * .5, Motion.CHASE)
    }

    /** Buildings in a row along every edge of the block, facing the street; a courtyard behind them. */
    private fun buildings(r: R, t: Double) {
        val size = min(t * rand(.6, 1.3), r.short * .22)
        for (side in 0..3) {
            val horizontal = side == 0 || side == 2
            var along = (if (horizontal) r.x0 else r.y0) + size * .5
            val end = if (horizontal) r.x1 else r.y1
            while (along < end - size * .5) {
                val target = size * rand(.7, 1.15)
                val off = target * 1.02
                val x = when (side) { 1 -> r.x1 - off; 3 -> r.x0 + off; else -> along + target }
                val y = when (side) { 0 -> r.y0 + off; 2 -> r.y1 - off; else -> along + target }
                val p = place(listOf(Role.BUILDING), target, x, y, .04) { it.radius > target * .5 }
                along += (p?.radius ?: target * .5) * 2.1 + size * .08
            }
        }
        val court = r.inset(size * 2.3)
        if (court.w > 0 && court.h > 0) {
            patch(Ground.GRASS, court)
            heap(court.cx, court.cy, court.short * .25, t * .15, t * .6, 6, listOf(Role.YARD, Role.STREET))
            if (chance(.5)) place(listOf(Role.TREE), min(t * .9, court.short * .3), court.cx + rand(-.3, .3) * court.w, court.cy + rand(-.3, .3) * court.h, .1)
        }
    }

    /** A paved square: a centrepiece, market stalls in rows, benches and lamps. */
    private fun plaza(r: R, t: Double) {
        patch(Ground.COBBLES, r)
        place(listOf(Role.LANDMARK), min(t * rand(1.0, 1.8), r.short * .2), r.cx, r.cy, .1)
        val step = t * 1.6
        for (row in listOf(r.y0 + step, r.y1 - step)) {
            var x = r.x0 + step
            while (x < r.x1 - step) { place(listOf(Role.YARD, Role.STREET, Role.VEHICLE), t * rand(.3, .6), x, row, .15); x += step * rand(.9, 1.3) }
        }
        heap(r.cx, r.cy, r.short * .3, t * .15, t * .5, 8, listOf(Role.YARD, Role.STREET))
    }

    /** One great building or monument alone on its lawn, trees at the corners: a jackpot for the right size. */
    private fun civic(r: R, t: Double) {
        patch(Ground.GRASS, r)
        place(listOf(Role.BUILDING, Role.LANDMARK), min(r.short * .36, t * rand(1.4, 2.2)), r.cx, r.cy, .05)
        for ((fx, fy) in listOf(.12 to .12, .88 to .12, .12 to .88, .88 to .88)) place(listOf(Role.TREE), t * rand(.6, 1.2), r.x0 + r.w * fx, r.y0 + r.h * fy, .1)
    }

    // ───────────────────────────── countryside and mountains ─────────────────────────────

    /** Fields edged with hedgerows, woods, villages at the crossroads, lakes, an airport or a racetrack. */
    private fun country() {
        var a = ring.inner
        while (a < ring.half - 1) {
            val t = ring.tier(a, 0.0)
            val w = 5.5 * t
            var b = min(a + w, ring.half)
            if (ring.half - b < w * .45) b = ring.half
            val lane = max(12.0, w * .03)
            roundBand(a, b, { w * rand(1.0, 2.0) }, w * rand(.05, .2)) { countryBlock(it) }
            if (b >= ring.half - 1 || b + lane >= ring.half) break
            roundRoad(b + lane / 2, lane, 2)
            a = b + lane
        }
    }

    private fun countryBlock(r: R) {
        val t = ring.tier(r.cx, r.cy)
        val roll = random.nextDouble()
        when {
            roll < .42 -> field(r, t)
            roll < .67 -> wood(r, t)
            roll < .79 -> village(r, t)
            roll < .88 -> lake(r, t)
            else -> place(listOf(Role.LANDMARK, Role.YARD, Role.BUILDING), min(r.short * .38, t * rand(1.2, 2.2)), r.cx, r.cy, .05).also { scatter(r, listOf(Role.TREE, Role.YARD), 3.5) }
        }
    }

    /** A field of wheat or meadow with a hedgerow of copses along its edges and a farm in a corner. */
    private fun field(r: R, t: Double) {
        val inner = r.inset(r.short * .04)
        patch(if (chance(.55)) Ground.WHEAT else Ground.MEADOW, inner)
        val (hedge, hedgeR) = pick(listOf(Role.TREE), t * rand(.35, .7)) ?: return
        for (side in 0..3) {
            val horizontal = side == 0 || side == 2
            var along = (if (horizontal) r.x0 else r.y0) + hedgeR
            val end = if (horizontal) r.x1 else r.y1
            while (along < end - hedgeR) {
                val x = when (side) { 1 -> r.x1 - hedgeR; 3 -> r.x0 + hedgeR; else -> along }
                val y = when (side) { 0 -> r.y0 + hedgeR; 2 -> r.y1 - hedgeR; else -> along }
                if (chance(.8)) put(hedge, x, y, hedgeR * rand(.8, 1.2), .05)
                along += hedgeR * rand(2.2, 3.4)
            }
        }
        val corner = if (random.nextBoolean()) r.x0 + r.w * .2 to r.y0 + r.h * .2 else r.x1 - r.w * .2 to r.y1 - r.h * .2
        heap(corner.first, corner.second, t * .8, t * .3, t * .9, 4, listOf(Role.BUILDING))
        if (chance(.45)) {
            // Rows of an orchard, a vineyard or solar panels.
            val (row, rowR) = pick(listOf(Role.YARD), t * rand(.4, .8)) ?: return
            var y = inner.y0 + inner.h * .45
            while (y < inner.y1 - rowR) { var x = inner.x0 + inner.w * .4; while (x < inner.x1 - rowR) { put(row, x, y, rowR, .05); x += rowR * 2.6 }; y += rowR * 2.8 }
        }
        scatter(inner, listOf(Role.BODY, Role.YARD, Role.BUILDING), 4.0, .2, .8)
    }

    /** A wood: copses packed close, maybe a ruin in a clearing. */
    private fun wood(r: R, t: Double) {
        patch(Ground.FOREST, r.inset(r.short * .03))
        if (chance(.3)) place(listOf(Role.LANDMARK), t * rand(.6, 1.1), r.cx, r.cy, .3)
        val count = (r.w * r.h / (t * 1.3).pow(2)).roundToInt().coerceAtMost(160)
        repeat(count) { place(listOf(Role.TREE), t * rand(.25, 1.2), rand(r.x0, r.x1), rand(r.y0, r.y1), .02) }
    }

    /** A village along its crossroads: buildings in rows along the two streets, a monument in the middle. */
    private fun village(r: R, t: Double) {
        val street = r.short * .06
        road(R(r.x0, r.cy - street / 2, r.x1, r.cy + street / 2), 0.0); road(R(r.cx - street / 2, r.y0, r.cx + street / 2, r.y1), 0.0)
        place(listOf(Role.LANDMARK, Role.BUILDING), t * rand(.6, 1.1), r.cx + street * 2.5, r.cy + street * 2.5, .1)
        val size = t * rand(.35, .6)
        for (sign in listOf(-1.0, 1.0)) {
            var x = r.x0 + size
            while (x < r.x1 - size) { place(listOf(Role.BUILDING), size * rand(.7, 1.2), x, r.cy + sign * (street / 2 + size * 1.1), .05); x += size * rand(2.1, 2.6) }
            var y = r.y0 + size
            while (y < r.y1 - size) { place(listOf(Role.BUILDING), size * rand(.7, 1.2), r.cx + sign * (street / 2 + size * 1.1), y, .05); y += size * rand(2.1, 2.6) }
        }
        scatter(r, listOf(Role.YARD, Role.TREE, Role.BODY), 4.0, .2, .7)
    }

    /** A lake with a marina on its shore and a few boats; copses around. */
    private fun lake(r: R, t: Double) {
        val water = r.inset(r.short * .12)
        patch(Ground.WATER, water, round = true)
        place(listOf(Role.LANDMARK), t * rand(.4, .7), water.cx, water.y0, .1) { it.shape == Shape.FOUNTAIN }
        repeat(3) { place(listOf(Role.VEHICLE), t * rand(.4, .9), water.cx + rand(-.25, .25) * water.w, water.cy + rand(-.25, .25) * water.h, .2) }
        scatter(r, listOf(Role.TREE, Role.BODY), 3.5, .2, .8)
    }

    /** Alternating bands of mountain ranges (peaks in a row along the band) and green valleys; clouds over all. */
    private fun mountains() {
        var a = ring.inner
        var range = false
        while (a < ring.half - 1) {
            val t = ring.tier(a, 0.0)
            val w = 5.0 * t
            var b = min(a + w, ring.half)
            if (ring.half - b < w * .45) b = ring.half
            if (range) ranges(a, b) else roundBand(a, b, { w * rand(1.0, 2.0) }, w * rand(.0, .15)) { valley(it) }
            range = !range
            a = b
        }
        repeat(70) {
            val s = spaceSpot(); val t = ring.tier(s[0], s[1])
            place(listOf(Role.BODY), t * rand(.3, 1.0), s[0], s[1], .4) { it.shape == Shape.CLOUD }
        }
        repeat(6) { val s = spaceSpot(); val t = ring.tier(s[0], s[1]); roamerAt(s, t, Motion.CHASE) }
        repeat(5) { val s = spaceSpot(); val t = ring.tier(s[0], s[1]); roamerAt(s, t, Motion.WANDER) }
    }

    private fun roamerAt(s: DoubleArray, t: Double, motion: Motion) {
        val box = R(s[0] - t * 12, s[1] - t * 12, s[0] + t * 12, s[1] + t * 12)
        roamer(box, t * .7, motion)
    }

    /** A mountain range going right round between radii [a] and [b]: peaks in a winding row, smaller summits on the flanks. */
    private fun ranges(a: Double, b: Double) {
        val mid = (a + b) / 2; val depth = b - a
        ring.annuli += Annulus(Ground.ROCK, mid, depth)
        val around = 2 * PI * mid
        val start = random.nextDouble() * 2 * PI
        var p = 0.0
        while (p < around) {
            val angle = start + p / mid
            val ux = cos(angle); val uy = sin(angle)
            val t = ring.tier(ux * mid, uy * mid)
            val target = min(t * rand(.6, 1.5), depth * .3)
            val off = mid + gauss() * depth * .12
            val placed = place(listOf(Role.BODY), target, ux * off, uy * off, .02) { it.shape == Shape.PEAK }
            // Smaller summits and glaciers on the flanks.
            if (chance(.6)) {
                val o = mid + depth * rand(.25, .4) * (if (random.nextBoolean()) 1 else -1)
                val along = rand(-1.0, 1.0) * target
                place(listOf(Role.BODY), target * rand(.3, .6), ux * o - uy * along, uy * o + ux * along, .05)
            }
            p += (placed?.radius ?: target * .6) * rand(1.5, 2.0)
        }
    }

    private fun valley(r: R) {
        val t = ring.tier(r.cx, r.cy)
        val roll = random.nextDouble()
        when {
            roll < .25 -> { val water = r.inset(r.short * .15); patch(Ground.WATER, water, round = true); place(listOf(Role.BODY, Role.LANDMARK), min(t, water.short * .2), water.cx, water.cy, .1) { it.shape == Shape.LAKE } }
            roll < .55 -> { patch(Ground.FOREST, r.inset(r.short * .05)); repeat(30) { place(listOf(Role.TREE), t * rand(.3, 1.0), rand(r.x0, r.x1), rand(r.y0, r.y1), .05) } }
            roll < .75 -> heap(r.cx, r.cy, r.short * .2, t * .3, t * 1.1, 6, listOf(Role.BUILDING))
            else -> patch(Ground.MEADOW, r)
        }
        scatter(r, listOf(Role.BODY, Role.LANDMARK, Role.TREE, Role.BUILDING), 3.6, .2, .9)
    }

    // ───────────────────────────── the Earth ─────────────────────────────

    /** Oceans with continents around the land you came from; islands in the sea, ranges, deserts and forests on land, storms. */
    private fun earth() {
        val discs = ArrayList<DoubleArray>()
        fun blob(cx: Double, cy: Double, size: Double, pieces: Int) {
            repeat(pieces) {
                val a = random.nextDouble() * 2 * PI; val d = size * rand(0.0, .7)
                val x = cx + cos(a) * d; val y = cy + sin(a) * d; val r = size * rand(.35, .7)
                val ground = when { abs(y) > ring.half * .75 -> Ground.ROCK; chance(.3) -> Ground.SAND; chance(.4) -> Ground.FOREST; else -> Ground.MEADOW }
                patch(ground, R(x - r, y - r, x + r, y + r), round = true); discs += doubleArrayOf(x, y, r)
            }
        }
        // Your own continent first: it carries the mountains and everything inside.
        val home = ring.inner * 1.9
        patch(Ground.MEADOW, R(-home, -home, home, home), round = true); discs += doubleArrayOf(0.0, 0.0, home)
        blob(0.0, 0.0, home * 1.4, 7)
        repeat(11) {
            val s = spaceSpot()
            val d = ring.reach(s[0], s[1])
            if (d < ring.inner * 3) return@repeat
            blob(s[0], s[1], d * rand(.15, .4), 6 + random.nextInt(6))
        }
        fun land(x: Double, y: Double) = discs.any { hypot(x - it[0], y - it[1]) < it[2] }
        val count = (2.5 * 8.0 * 169.0 / 9.0 * ln(ring.half / ring.inner)).roundToInt()
        repeat(count) {
            val s = spaceSpot(); val x = s[0]; val y = s[1]; val t = ring.tier(x, y)
            if (!rich(x, y)) return@repeat
            val target = if (chance(.1)) t * rand(1.1, 1.9) else t * rand(.2, 1.0)
            if (land(x, y)) place(listOf(Role.BODY, Role.TREE, Role.BUILDING, Role.LANDMARK), target, x, y, .4) { it.id in LAND || it.theme == Theme.MOUNTAINS }
            else place(listOf(Role.BODY), target, x, y, .4) { it.id !in LAND && it.theme == Theme.EARTH && (it.id != "iceberg" || abs(y) > ring.half * .5) }
        }
        repeat(4) { val s = spaceSpot(); if (!land(s[0], s[1])) roamerAt(s, ring.tier(s[0], s[1]), Motion.CHASE) }
        repeat(4) { val s = spaceSpot(); if (!land(s[0], s[1])) roamerAt(s, ring.tier(s[0], s[1]), Motion.WANDER) }
    }

    // ───────────────────────────── space ─────────────────────────────

    /** The solar system: the Sun off to one side, the planets on their orbits with their moons, the asteroid belt, comets. */
    private fun planets() {
        val sunX = -1.8e9; val sunY = 0.0
        val sun = GluttonCatalog.byId.getValue("sun")
        ring.placements += Placement(sun, sunX, sunY, sun.radius, ring).also { index.add(it, sunX, sunY, sun.radius) }
        ring.circles += doubleArrayOf(sunX, sunY, -sunX)
        val orbits = listOf("mercury" to (1.0e9 to 50.0), "venus" to (1.35e9 to 80.0), "mars" to (2.35e9 to 140.0),
            "jupiter" to (3.3e9 to 260.0), "saturn" to (4.0e9 to 380.0), "uranus" to (4.6e9 to 520.0), "neptune" to (5.1e9 to 700.0))
        val planets = HashMap<String, Placement>()
        for ((id, spec) in orbits) {
            val prop = GluttonCatalog.byId.getValue(id)
            val (radius, period) = spec
            ring.circles += doubleArrayOf(sunX, sunY, radius)
            val phase = random.nextDouble() * 2 * PI
            val p = Placement(prop, sunX + cos(phase) * radius, sunY + sin(phase) * radius, prop.radius, ring)
            p.orbit = Orbit(sunX, sunY, radius, period, phase); ring.movers += p; planets[id] = p
        }
        val moons = mapOf("mars" to listOf("phobos", "deimos"), "jupiter" to listOf("io", "europa", "ganymede", "callisto"),
            "saturn" to listOf("titan"), "neptune" to listOf("triton"))
        for ((planet, list) in moons) {
            val host = planets.getValue(planet)
            for ((k, id) in list.withIndex()) {
                val prop = GluttonCatalog.byId.getValue(id)
                val p = Placement(prop, host.x, host.y, prop.radius, ring)
                p.orbit = Orbit(0.0, 0.0, host.radius * (2.2 + k * 1.2) + prop.radius * 4, 12.0 + k * 6, random.nextDouble() * 2 * PI, host)
                ring.movers += p
            }
        }
        // The Moon goes round the Earth you have just left.
        val moon = GluttonCatalog.byId.getValue("moon")
        Placement(moon, 4.5e7, 0.0, moon.radius, ring).also { it.orbit = Orbit(0.0, 0.0, 4.5e7, 40.0, random.nextDouble() * 2 * PI); ring.movers += it }
        // A first helping of small rocks around the Earth.
        repeat(140) {
            val a = random.nextDouble() * 2 * PI; val d = rand(9e6, 9e7)
            place(listOf(Role.BODY), rand(1.5e5, 1.6e6), cos(a) * d, sin(a) * d, .6) { it.shape == Shape.BLOB }
        }
        repeat(320) {
            val a = random.nextDouble() * 2 * PI; val d = rand(2.75e9, 3.0e9)
            val x = sunX + cos(a) * d; val y = sunY + sin(a) * d
            place(listOf(Role.BODY), ring.tier(x, y) * rand(.25, 1.0), x, y, .3) { it.shape == Shape.BLOB }
        }
        for (id in listOf("pluto", "charon", "eris", "ceres", "vesta")) {
            val prop = GluttonCatalog.byId.getValue(id)
            repeat(10) { val s = spaceSpot(); if (put(prop, s[0], s[1], prop.radius, .5) != null) return@repeat }
        }
        // Asteroids, then meteors and rocks adrift of every size: no star before the stars ring. The named moons and
        // dwarf planets exist once each.
        spaceScatter(1.25) { it.theme == Theme.PLANETS && (it.shape != Shape.PLANET || it.spread > 1.0) }
        repeat(8) {
            val s = spaceSpot(); val a = random.nextDouble() * 2 * PI; val l = ring.reach(s[0], s[1]) * .6
            val lane = Lane(doubleArrayOf(s[0] - cos(a) * l, s[1] - sin(a) * l, s[0] + cos(a) * l, s[1] + sin(a) * l), false)
            val prop = movers.filter { it.motion == Motion.PATROL && it.theme == Theme.PLANETS }.randomOrNull(random) ?: return@repeat
            val at = random.nextDouble() * lane.length; val out = DoubleArray(4); lane.at(at, out)
            val r = ring.tier(out[0], out[1]) * rand(.2, .5)
            mover(prop, out[0], out[1], r)?.apply { this.lane = lane; laneAt = at }
        }
    }

    /** Stars gathered in clusters with nebulae between them, and loose stars everywhere. */
    private fun stars() {
        repeat(32) {
            val c = spaceSpot(); val t = ring.tier(c[0], c[1])
            if (!rich(c[0], c[1]) && !rich(c[0], c[1])) return@repeat
            val n = 10 + random.nextInt(20); val sigma = t * rand(4.0, 8.0)
            repeat(n) { place(listOf(Role.BODY), t * rand(.2, 1.2), c[0] + gauss() * sigma, c[1] + gauss() * sigma, .5) }
        }
        spaceScatter(1.35)
    }

    /** The Milky Way: loose stars and clouds, crowded along the spiral arms; its core in the outermost ring. */
    private fun galaxy() {
        val (gx, gy) = world.galaxyCentre.let { it[0] to it[1] }
        val pitch = tan(13.0 * PI / 180)
        for (arm in 0 until 4) {
            val start = arm * PI / 2
            repeat(900) {
                val rho = 1e17 * (3e20 / 1e17).pow(random.nextDouble())
                val theta = start + ln(rho / 1e17) / pitch
                val x = gx + cos(theta) * rho; val y = gy + sin(theta) * rho
                if (!ring.contains(x, y) || !world.outsideInner(ring, x, y)) return@repeat
                val t = ring.tier(x, y)
                place(listOf(Role.BODY), t * rand(.2, 1.2), x + gauss() * t * 3, y + gauss() * t * 3, .4)
            }
        }
        if (ring.contains(gx, gy) && world.outsideInner(ring, gx, gy)) {
            for (id in listOf("sagittarius_a", "galactic_bulge")) { val prop = GluttonCatalog.byId.getValue(id); put(prop, gx + rand(-1.0, 1.0) * prop.radius * 2, gy, prop.radius, .1) }
        }
        spaceScatter(1.7)
    }

    /** The cosmic web: knots of galaxy groups and clusters joined by filaments of galaxies, voids in between. */
    private fun cosmos() {
        val nodes = List(34) { spaceSpot() }
        for (node in nodes) {
            val t = ring.tier(node[0], node[1])
            place(listOf(Role.BODY), t * rand(1.0, 1.8), node[0], node[1], .3) { it.shape == Shape.CLUSTER }
            val nearest = nodes.filter { it !== node }.sortedBy { hypot(it[0] - node[0], it[1] - node[1]) }.take(2)
            for (other in nearest) {
                val length = hypot(other[0] - node[0], other[1] - node[1])
                var s = 0.0
                while (s < length) {
                    val f = s / length
                    val x = node[0] + (other[0] - node[0]) * f; val y = node[1] + (other[1] - node[1]) * f
                    val tt = ring.tier(x, y)
                    if (rich(x, y) || chance(.3)) place(listOf(Role.BODY), tt * rand(.2, 1.0), x + gauss() * tt, y + gauss() * tt, .4)
                    s += tt * rand(1.6, 2.6)
                }
            }
        }
        spaceScatter(2.0)
    }

    companion object {
        const val WALL = .07
        /** A gobbler this round rolls over the house walls (and the doorways stop fitting a little before). */
        const val WALL_HEIGHT = .7
        /** Roles that [thin] spaces out, the share of them it leaves out, and the gap each keeps (in its own radii). */
        private val LOOSE = setOf(Role.FOOD, Role.CLUTTER, Role.YARD, Role.STREET, Role.BODY)
        private const val THIN = .3
        private const val SPACING = 1.5
        private const val HOUSE_SPACING = .7
        private val FOOD_ROLES = listOf(Role.FOOD, Role.CLUTTER)
        /** Placed by hand (the Sun, the Moon) or used as a cover (your house is also a house among others). */
        private val SPECIAL = setOf("earth", "milky_way", "sun", "moon", "hourglass")
        /** Earth things that sit on land; the rest of the Earth theme is islands, reefs and ice out at sea. */
        private val LAND = setOf("megacity", "sand_dunes", "grand_canyon", "great_lake", "rainforest", "alps", "himalaya")
        private val FLOORS = mapOf('K' to Ground.TILES, 'L' to Ground.WOOD, 'B' to Ground.CARPET, 'T' to Ground.BATH, 'O' to Ground.WOOD, 'C' to Ground.CARPET)
        private val FURNITURE = mapOf(
            'K' to listOf("fridge", "microwave", "fire_extinguisher"),
            'L' to listOf("sofa", "armchair", "flat_screen_tv", "bookshelf", "floor_lamp", "houseplant", "upright_piano", "grandfather_clock", "christmas_tree"),
            'B' to listOf("double_bed", "wardrobe", "nightstand", "chest_of_drawers", "suitcase", "floor_fan"),
            'T' to listOf("bathtub", "upright_vacuum", "wastepaper_basket"),
            'O' to listOf("desk", "computer_monitor", "desk_lamp", "bookshelf", "exercise_bike", "wastepaper_basket", "houseplant"),
            'C' to listOf("beanbag", "desk", "wardrobe", "chest_of_drawers"))
    }
}
