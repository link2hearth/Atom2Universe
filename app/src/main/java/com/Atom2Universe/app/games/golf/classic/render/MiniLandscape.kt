package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.*
import kotlin.math.*
import kotlin.random.Random

/** Colours of a mini-golf theme: felt, rails, and what lies beyond them. */
internal class MiniLook(theme: MiniTheme) {
    val felt: C
    val rail: C
    val railAlt: C
    val lawn: C
    val island: C
    val crown: C
    val mat: C

    init {
        when (theme) {
            MiniTheme.MEADOW -> { felt = C(.25f, .58f, .28f); rail = C(.58f, .38f, .21f); railAlt = rail
                lawn = C(.30f, .46f, .21f); island = C(.27f, .47f, .22f); crown = C(.24f, .46f, .22f); mat = C(.14f, .16f, .15f) }
            MiniTheme.GARDEN -> { felt = C(.17f, .50f, .25f); rail = C(.93f, .93f, .88f); railAlt = rail
                lawn = C(.34f, .52f, .26f); island = C(.30f, .52f, .28f); crown = C(.28f, .50f, .24f); mat = C(.13f, .17f, .14f) }
            MiniTheme.CANDY -> { felt = C(.36f, .78f, .70f); rail = C(.90f, .27f, .33f); railAlt = C(.97f, .96f, .93f)
                lawn = C(.42f, .62f, .34f); island = C(.88f, .62f, .72f); crown = C(.96f, .66f, .78f); mat = C(.20f, .24f, .24f) }
            MiniTheme.OCEAN -> { felt = C(.18f, .50f, .72f); rail = C(.96f, .96f, .93f); railAlt = C(.18f, .34f, .56f)
                lawn = C(.82f, .76f, .58f); island = C(.74f, .66f, .45f); crown = C(.26f, .55f, .36f); mat = C(.12f, .20f, .30f) }
            MiniTheme.COSMOS -> { felt = C(.21f, .17f, .40f); rail = C(.35f, .93f, .96f); railAlt = C(.82f, .36f, .96f)
                lawn = C(.10f, .09f, .20f); island = C(.16f, .13f, .30f); crown = C(.55f, .40f, .95f); mat = C(.42f, .40f, .55f) }
        }
    }

    fun rail(index: Int) = if (index % 2 == 0) rail else railAlt
}

/** Marching squares on the lane distance field: the rails' ground plan as closed loops. */
internal class MiniContour(private val layout: MiniLayout, val step: Float = .08f) {
    class Loop(val x: FloatArray, val z: FloatArray, val nx: FloatArray, val nz: FloatArray)

    val x0: Float
    val z0: Float
    val cellsX: Int
    val cellsZ: Int
    /** Distance field at the grid points, (cellsX + 1) × (cellsZ + 1). */
    val grid: FloatArray

    init {
        val b = layout.bounds
        x0 = b[0] - .7f; z0 = b[1] - .7f
        cellsX = ceil((b[2] + .7f - x0) / step).toInt()
        cellsZ = ceil((b[3] + .7f - z0) / step).toInt()
        val w = cellsX + 1
        grid = FloatArray(w * (cellsZ + 1)) { layout.sdf(x0 + (it % w) * step, z0 + (it / w) * step) }
    }

    private val w get() = cellsX + 1

    fun loops(): List<Loop> {
        // Each edge crossed by the contour is a node; the contour links the two nodes of a cell.
        val links = HashMap<Int, IntArray>()
        fun link(a: Int, b: Int) {
            for ((p, q) in listOf(a to b, b to a)) {
                val l = links.getOrPut(p) { intArrayOf(-1, -1) }
                if (l[0] < 0) l[0] = q else l[1] = q
            }
        }
        fun horizontal(i: Int, j: Int) = 2 * (j * w + i)
        fun vertical(i: Int, j: Int) = 2 * (j * w + i) + 1
        for (j in 0 until cellsZ) for (i in 0 until cellsX) {
            val c0 = grid[j * w + i]; val c1 = grid[j * w + i + 1]
            val c2 = grid[(j + 1) * w + i + 1]; val c3 = grid[(j + 1) * w + i]
            val in0 = c0 < 0f; val in1 = c1 < 0f; val in2 = c2 < 0f; val in3 = c3 < 0f
            val bottom = horizontal(i, j); val right = vertical(i + 1, j)
            val top = horizontal(i, j + 1); val left = vertical(i, j)
            val crossed = ArrayList<Int>(4)
            if (in0 != in1) crossed += bottom
            if (in1 != in2) crossed += right
            if (in3 != in2) crossed += top
            if (in0 != in3) crossed += left
            when (crossed.size) {
                2 -> link(crossed[0], crossed[1])
                4 -> {
                    val centre = (c0 + c1 + c2 + c3) * .25f < 0f
                    // The saddle: which pair of corners the middle joins decides how the contour is cut.
                    if (in0 == centre) { link(bottom, left); link(right, top) } else { link(bottom, right); link(top, left) }
                }
            }
        }
        val seen = HashSet<Int>()
        val result = ArrayList<Loop>()
        for (start in links.keys) {
            if (start in seen) continue
            val chain = ArrayList<Int>()
            var previous = -1; var current = start
            var closed = false
            while (true) {
                chain += current; seen += current
                val l = links[current] ?: break
                val next = if (l[0] != previous && l[0] >= 0) l[0] else l[1]
                if (next < 0) break
                if (next == start) { closed = true; break }
                if (next in seen) break
                previous = current; current = next
            }
            if (!closed || chain.size < 6) continue
            val xs = FloatArray(chain.size); val zs = FloatArray(chain.size)
            val nxs = FloatArray(chain.size); val nzs = FloatArray(chain.size)
            chain.forEachIndexed { k, id ->
                val idx = id / 2
                val i = idx % w; val j = idx / w
                val a = grid[idx]
                val c = if (id % 2 == 0) grid[idx + 1] else grid[idx + w]
                val t = if (abs(a - c) < 1e-9f) .5f else a / (a - c)
                xs[k] = x0 + (if (id % 2 == 0) i + t else i.toFloat()) * step
                zs[k] = z0 + (if (id % 2 == 0) j.toFloat() else j + t) * step
                val e = .01f
                var gx = layout.sdf(xs[k] + e, zs[k]) - layout.sdf(xs[k] - e, zs[k])
                var gz = layout.sdf(xs[k], zs[k] + e) - layout.sdf(xs[k], zs[k] - e)
                val g = hypot(gx, gz).coerceAtLeast(1e-6f)
                gx /= g; gz /= g
                nxs[k] = gx; nzs[k] = gz
            }
            result += Loop(xs, zs, nxs, nzs)
        }
        return result
    }
}

/** A closed prism on four ground-plan corners: a block, an arm, a plank. */
internal fun MeshBuilder.prism(xs: FloatArray, zs: FloatArray, y0: Float, y1: Float, side: C, top: C) {
    quad(P(xs[0], y1, zs[0]), P(xs[1], y1, zs[1]), P(xs[2], y1, zs[2]), P(xs[3], y1, zs[3]), top)
    for (k in 0 until 4) {
        val n = (k + 1) % 4
        quad(P(xs[k], y0, zs[k]), P(xs[n], y0, zs[n]), P(xs[n], y1, zs[n]), P(xs[k], y1, zs[k]), side)
    }
}

/** An upright rectangle of half-sizes [hx], [hz] turned by [angle] around ([cx], [cz]). */
internal fun MeshBuilder.slab(cx: Float, cz: Float, hx: Float, hz: Float, angle: Float, y0: Float, y1: Float, side: C, top: C) {
    val c = cos(angle); val s = sin(angle)
    val xs = FloatArray(4); val zs = FloatArray(4)
    val u = floatArrayOf(-hx, hx, hx, -hx); val v = floatArrayOf(-hz, -hz, hz, hz)
    for (k in 0 until 4) { xs[k] = cx + u[k] * c - v[k] * s; zs[k] = cz + u[k] * s + v[k] * c }
    prism(xs, zs, y0, y1, side, top)
}

/**
 * The ground and the scenery of a mini-golf hole: carpet inside the rails, lawn outside, the rails
 * themselves drawn along the zero contour of the lane field, and the fixed obstacles.
 */
internal class MiniLandscape(private val hole: ClassicHole, private val landscape: ClassicLandscape) {
    private val mini = hole.mini!!
    private val look = MiniLook(mini.theme)
    private val b0 = mini.bounds
    private val x0 = b0[0] - MARGIN
    private val z0 = b0[1] - MARGIN
    private val x1 = b0[2] + MARGIN
    private val z1 = b0[3] + MARGIN
    private val sand = C(.83f, .76f, .58f)
    private val water = C(.15f, .40f, .43f)

    private fun zoneColour(kind: MiniZoneKind) = when (kind) {
        MiniZoneKind.CONVEYOR -> C(.33f, .34f, .38f)
        MiniZoneKind.BOOST -> C(.98f, .78f, .20f)
        MiniZoneKind.MUD -> C(.42f, .30f, .18f)
        MiniZoneKind.ICE -> C(.78f, .93f, .98f)
    }

    private fun sampleAt(x: Float, z: Float): GroundSample {
        val d = mini.sdf(x, z)
        var colour = look.lawn
        var green = 0f
        if (d < .3f) {
            // Soft across a few centimetres: the rail stands on that edge and hides it.
            green = ((.12f - d) / .24f).coerceIn(0f, 1f)
            colour = colour.mix(look.felt, green)
        }
        var sandWeight = 0f; var waterWeight = 0f
        for (patch in mini.patches) {
            val pd = patch.shape.distance(x, z)
            if (pd >= .3f) continue
            if (patch.lie == GolfLie.BUNKER) {
                val w = ((.05f - pd) / .2f).coerceIn(0f, 1f)
                colour = colour.mix(sand, w); sandWeight = max(sandWeight, w)
            } else {
                val w = ((.04f - pd) / .15f).coerceIn(0f, 1f)
                colour = colour.mix(water, w); waterWeight = max(waterWeight, w)
            }
        }
        val hazards = min(1f, sandWeight + waterWeight)
        green *= 1f - hazards
        if (green > 0f) for (zone in mini.zones) {
            val w = ((.05f - zone.shape.distance(x, z)) / .12f).coerceIn(0f, 1f) * green
            if (w > 0f) colour = colour.mix(zoneColour(zone.kind), w * .85f)
        }
        val gx = (hole.heightAt(x + .4f, z) - hole.heightAt(x - .4f, z)) / .8f
        val gz = (hole.heightAt(x, z + .4f) - hole.heightAt(x, z - .4f)) / .8f
        val light = .77f + .23f * ((gx * .35f + .86f + gz * .36f) / sqrt(1f + gx * gx + gz * gz)).coerceIn(0f, 1f)
        return GroundSample(colour.shade(light), Turf(0f, green, sandWeight, waterWeight), 1000f, d, light, 0f)
    }

    fun terrain(): GroundMesh {
        val b = GroundBuilder(1 shl 16)
        val nx = ceil((x1 - x0) / STEP).toInt(); val nz = ceil((z1 - z0) / STEP).toInt()
        val w = nx + 1
        val heights = FloatArray(w * (nz + 1)) { hole.heightAt(x0 + (it % w) * STEP, z0 + (it / w) * STEP) }
        val samples = arrayOfNulls<GroundSample>(heights.size)
        fun sample(i: Int, j: Int) = samples[j * w + i] ?: sampleAt(x0 + i * STEP, z0 + j * STEP).also { samples[j * w + i] = it }
        fun point(i: Int, j: Int) = P(x0 + i * STEP, heights[j * w + i], z0 + j * STEP)
        // The cells the hole touches are left out and replaced by one patch with the hole cut in it.
        val cx = hole.cup.x; val cz = hole.cup.z
        val reach = hole.cupRadius + .004f
        var cupLeft = Float.MAX_VALUE; var cupRight = -Float.MAX_VALUE
        var cupBack = Float.MAX_VALUE; var cupFront = -Float.MAX_VALUE
        for (j in 0 until nz) for (i in 0 until nx) {
            val x = x0 + i * STEP; val z = z0 + j * STEP
            if (x < cx + reach && x + STEP > cx - reach && z < cz + reach && z + STEP > cz - reach) {
                cupLeft = min(cupLeft, x); cupRight = max(cupRight, x + STEP)
                cupBack = min(cupBack, z); cupFront = max(cupFront, z + STEP)
                continue
            }
            b.triangle(point(i, j), point(i + 1, j), point(i + 1, j + 1), sample(i, j), sample(i + 1, j), sample(i + 1, j + 1))
            b.triangle(point(i, j), point(i + 1, j + 1), point(i, j + 1), sample(i, j), sample(i + 1, j + 1), sample(i, j + 1))
        }
        if (cupLeft < cupRight) landscape.cupPatch(b, cupLeft, cupBack, cupRight, cupFront, ::sampleAt)
        // The lawn goes on to the horizon, shaded as the flat ground of the carpet's own mesh.
        val lawn = look.lawn.shade(.77f + .23f * .86f)
        val rough = Turf.ROUGH
        fun apron(xa: Float, za: Float, xb: Float, zb: Float) =
            b.quad(P(xa, 0f, za), P(xb, 0f, za), P(xb, 0f, zb), P(xa, 0f, zb), lawn, rough, lit = false)
        val grid = x0 + nx * STEP; val gridEnd = z0 + nz * STEP
        apron(-FAR, -FAR, x0, FAR); apron(grid, -FAR, FAR, FAR)
        apron(x0, -FAR, grid, z0); apron(x0, gridEnd, grid, FAR)
        return b.build()
    }

    fun scenery(): List<ClassicMesh> {
        val b = MeshBuilder()
        val contour = MiniContour(mini)
        rails(b, contour)
        planters(b, contour)
        tee(b)
        posts(b)
        zones(b)
        portals(b)
        wells(b)
        bridges(b)
        landscape.cup(b)
        trees(b)
        return listOf(b.build())
    }

    /** The rails: a ribbon along every contour loop, standing on the lane's edge and leaning outward. */
    private fun rails(b: MeshBuilder, contour: MiniContour) {
        for (loop in contour.loops()) {
            val n = loop.x.size
            var travelled = 0f
            for (k in 0 until n) {
                val m = (k + 1) % n
                val colour = look.rail((travelled / STRIPE).toInt())
                val top = colour.shade(1.12f)
                val inner = look.rail((travelled / STRIPE).toInt()).shade(.92f)
                val ya = mini.floorAt(loop.x[k], loop.z[k]); val yb = mini.floorAt(loop.x[m], loop.z[m])
                val ax = loop.x[k]; val az = loop.z[k]; val bx = loop.x[m]; val bz = loop.z[m]
                val ox = loop.nx[k] * RAIL_THICKNESS; val oz = loop.nz[k] * RAIL_THICKNESS
                val px = loop.nx[m] * RAIL_THICKNESS; val pz = loop.nz[m] * RAIL_THICKNESS
                // Top, the face towards the lane, and the face towards the lawn.
                b.quad(P(ax, ya + RAIL_HEIGHT, az), P(bx, yb + RAIL_HEIGHT, bz),
                    P(bx + px, yb + RAIL_HEIGHT, bz + pz), P(ax + ox, ya + RAIL_HEIGHT, az + oz), top)
                b.quad(P(ax, ya, az), P(bx, yb, bz), P(bx, yb + RAIL_HEIGHT, bz), P(ax, ya + RAIL_HEIGHT, az), inner)
                b.quad(P(ax + ox, ya, az + oz), P(bx + px, yb, bz + pz),
                    P(bx + px, yb + RAIL_HEIGHT, bz + pz), P(ax + ox, ya + RAIL_HEIGHT, az + oz), colour.shade(.8f))
                travelled += hypot(bx - ax, bz - az)
            }
        }
    }

    /** What the rails fence in without being lane: enclosed islands and the pockets the hole asks to fill. */
    private fun planters(b: MeshBuilder, contour: MiniContour) {
        val w = contour.cellsX + 1
        val nx = contour.cellsX; val nz = contour.cellsZ
        val step = contour.step
        fun centre(i: Int, j: Int) = (contour.grid[j * w + i] + contour.grid[j * w + i + 1] +
            contour.grid[(j + 1) * w + i] + contour.grid[(j + 1) * w + i + 1]) * .25f
        val lane = BooleanArray(nx * nz) { centre(it % nx, it / nx) < 0f }
        // Everything the outside reaches through non-lane cells is lawn; the rest is an island.
        val outside = BooleanArray(nx * nz)
        val queue = ArrayDeque<Int>()
        fun reach(i: Int, j: Int) {
            if (i !in 0 until nx || j !in 0 until nz) return
            val id = j * nx + i
            if (lane[id] || outside[id]) return
            outside[id] = true; queue.addLast(id)
        }
        for (i in 0 until nx) { reach(i, 0); reach(i, nz - 1) }
        for (j in 0 until nz) { reach(0, j); reach(nx - 1, j) }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst(); val i = id % nx; val j = id / nx
            reach(i + 1, j); reach(i - 1, j); reach(i, j + 1); reach(i, j - 1)
        }
        val filled = BooleanArray(nx * nz) { !lane[it] && (!outside[it] ||
            mini.fills.any { shape -> shape.distance(contour.x0 + (it % nx + .5f) * step, contour.z0 + (it / nx + .5f) * step) <= 0f }) }
        val top = look.island
        for (j in 0 until nz) {
            var i = 0
            while (i < nx) {
                if (!filled[j * nx + i]) { i++; continue }
                var end = i
                while (end + 1 < nx && filled[j * nx + end + 1]) end++
                val xa = contour.x0 + i * step; val xb = contour.x0 + (end + 1) * step
                val za = contour.z0 + j * step; val zb = za + step
                val y = mini.floorAt((xa + xb) * .5f, (za + zb) * .5f) + RAIL_HEIGHT - .003f
                b.quad(P(xa, y, za), P(xb, y, za), P(xb, y, zb), P(xa, y, zb), top, false)
                i = end + 1
            }
        }
    }

    private fun tee(b: MeshBuilder) {
        b.box(0f, mini.floorAt(0f, 0f) + .001f, 0f, .55f, .006f, .8f, look.mat)
    }

    private fun posts(b: MeshBuilder) {
        for (p in mini.posts) {
            val y = mini.floorAt(p.x, p.z)
            val colour = if (p.kick > 0f) look.railAlt.mix(C(1f, 1f, 1f), .2f) else look.rail
            b.cone(p.x, y, p.z, p.r, POST_HEIGHT, colour.shade(.9f), 14, p.r)
            b.disc(p.x, y + POST_HEIGHT + .001f, p.z, p.r, p.r, colour.shade(1.15f), 14)
            if (p.kick > 0f) {
                b.ring(p.x, y + .004f, p.z, p.r + .02f, .06f, look.rail, 18)
                b.ring(p.x, y + .004f, p.z, p.r + .1f, .03f, look.railAlt, 18)
            }
        }
    }

    /** Pushing zones carry chevrons that point where they push. */
    private fun zones(b: MeshBuilder) {
        for (zone in mini.zones) {
            if (zone.kind != MiniZoneKind.CONVEYOR && zone.kind != MiniZoneKind.BOOST) continue
            val length = hypot(zone.ax, zone.az)
            if (length < 1e-4f) continue
            val dx = zone.ax / length; val dz = zone.az / length
            val colour = if (zone.kind == MiniZoneKind.BOOST) C(1f, .96f, .62f) else C(.78f, .80f, .86f)
            val bb = zone.shape.bounds
            var z = bb[1]
            while (z < bb[3]) {
                var x = bb[0]
                while (x < bb[2]) {
                    val cx = x + .2f; val cz = z + .2f
                    if (zone.shape.distance(cx, cz) < -.14f && mini.sdf(cx, cz) < -.1f) {
                        val y = mini.floorAt(cx, cz) + .005f
                        val nx = -dz; val nz = dx
                        b.tri(P(cx + dx * .15f, y, cz + dz * .15f), P(cx - dx * .1f + nx * .12f, y, cz - dz * .1f + nz * .12f),
                            P(cx - dx * .02f, y, cz - dz * .02f), colour, false)
                        b.tri(P(cx + dx * .15f, y, cz + dz * .15f), P(cx - dx * .02f, y, cz - dz * .02f),
                            P(cx - dx * .1f - nx * .12f, y, cz - dz * .1f - nz * .12f), colour, false)
                    }
                    x += .45f
                }
                z += .45f
            }
        }
    }

    private fun tint(index: Int) = when (index % 4) {
        0 -> C(1f, .62f, .12f); 1 -> C(.25f, .88f, 1f); 2 -> C(1f, .35f, .78f); else -> C(.62f, 1f, .35f)
    }

    private fun portals(b: MeshBuilder) {
        for (p in mini.portals) {
            val colour = tint(p.tint)
            val y = mini.floorAt(p.x, p.z)
            b.disc(p.x, y + .003f, p.z, p.r, p.r, C(.03f, .03f, .08f), 20)
            b.ring(p.x, y + .004f, p.z, p.r * .55f, p.r * .12f, colour.mix(C(1f, 1f, 1f), .5f), 20)
            // The mouth of a pipe stands a little proud of the carpet: an open collar, wall and lip.
            for (k in 0 until 24) {
                val a = k * 2f * PI.toFloat() / 24f; val c = (k + 1) * 2f * PI.toFloat() / 24f
                val outer = p.r + .035f
                b.quad(P(p.x + cos(a) * outer, y, p.z + sin(a) * outer), P(p.x + cos(c) * outer, y, p.z + sin(c) * outer),
                    P(p.x + cos(c) * outer, y + .04f, p.z + sin(c) * outer), P(p.x + cos(a) * outer, y + .04f, p.z + sin(a) * outer), colour.shade(.6f))
            }
            b.ring(p.x, y + .04f, p.z, p.r, .035f, colour, 24)
            // The outlet: a ring and an arrow along the exit direction.
            val ey = mini.floorAt(p.toX, p.toZ)
            val heading = p.exitHeading ?: 0f
            val hx = sin(heading); val hz = cos(heading)
            b.ring(p.toX, ey + .005f, p.toZ, p.r * .8f, .03f, colour, 20)
            b.tri(P(p.toX + hx * p.r * 1.1f, ey + .006f, p.toZ + hz * p.r * 1.1f),
                P(p.toX - hx * p.r * .3f - hz * p.r * .6f, ey + .006f, p.toZ - hz * p.r * .3f + hx * p.r * .6f),
                P(p.toX - hx * p.r * .3f + hz * p.r * .6f, ey + .006f, p.toZ - hz * p.r * .3f - hx * p.r * .6f), colour, false)
        }
    }

    private fun wells(b: MeshBuilder) {
        for (w in mini.wells) {
            val y = mini.floorAt(w.x, w.z)
            val pulls = w.strength > 0f
            val colour = if (pulls) C(.55f, .35f, 1f) else C(1f, .5f, .25f)
            val core = if (w.horizon > 0f) w.horizon else .18f
            b.disc(w.x, y + .004f, w.z, w.reach * .5f, w.reach * .5f, colour.shade(.35f), 28)
            b.ring(w.x, y + .005f, w.z, w.reach * .5f, .05f, colour, 28)
            b.ring(w.x, y + .005f, w.z, w.reach * .32f, .03f, colour.shade(.8f), 28)
            b.disc(w.x, y + .007f, w.z, core, core, C(0f, 0f, 0f), 18)
        }
    }

    private fun bridges(b: MeshBuilder) {
        val wood = C(.62f, .43f, .24f)
        for (br in mini.bridges) {
            val c = cos(br.angle); val s = sin(br.angle)
            val y = mini.floorAt(br.x, br.z)
            b.slab(br.x, br.z, br.hx, br.hz, br.angle, y, y + .02f, wood.shade(.8f), wood)
            // Planks across the bridge's length (its +z side, turned by the angle), and a low rail down each side.
            val planks = (br.hz * 2f / .16f).toInt().coerceAtLeast(2)
            for (k in 0 until planks) {
                val v = -br.hz + (k + .5f) * 2f * br.hz / planks
                b.slab(br.x - v * s, br.z + v * c, br.hx, br.hz / planks * .42f, br.angle, y + .02f, y + .026f, wood.shade(.7f), wood.shade(1.1f))
            }
            for (side in intArrayOf(-1, 1)) {
                val u = side * (br.hx - .02f)
                b.slab(br.x + u * c, br.z + u * s, .02f, br.hz, br.angle, y + .02f, y + .09f, wood.shade(.75f), wood.shade(1.05f))
            }
        }
    }

    /** Scenery beyond the rails, out of the way. */
    private fun trees(b: MeshBuilder) {
        val random = Random(hole.number * 977 + mini.theme.ordinal * 131)
        val mx = (b0[0] + b0[2]) * .5f; val mz = (b0[1] + b0[3]) * .5f
        val reach = max(b0[2] - b0[0], b0[3] - b0[1]) * .5f + 4f
        var placed = 0; var tries = 0
        while (placed < 26 && tries++ < 200) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val r = reach + random.nextFloat() * 26f
            val x = mx + cos(a) * r * 1.2f; val z = mz + sin(a) * r
            if (x > x0 - 2f && x < x1 + 2f && z > z0 - 2f && z < z1 + 2f) continue
            placed++
            val size = .8f + random.nextFloat() * .7f
            when (mini.theme) {
                MiniTheme.COSMOS -> {
                    b.cone(x, 0f, z, .6f * size, 3.4f * size, look.crown, 6, 0f)
                    b.cone(x + .7f * size, 0f, z + .3f, .35f * size, 2f * size, look.railAlt.shade(.8f), 5, 0f)
                }
                MiniTheme.OCEAN -> {
                    b.cone(x, 0f, z, .22f * size, 3.2f * size, C(.52f, .38f, .24f), 6, .14f * size)
                    for (leaf in 0 until 5) {
                        val la = leaf * 2f * PI.toFloat() / 5f + size
                        b.tri(P(x, 3.2f * size, z), P(x + cos(la) * 1.7f * size, 2.7f * size, z + sin(la) * 1.7f * size),
                            P(x + cos(la + .5f) * .9f * size, 3.1f * size, z + sin(la + .5f) * .9f * size), look.crown)
                    }
                }
                else -> {
                    b.cone(x, 0f, z, .24f * size, 1.6f * size, C(.40f, .27f, .17f), 6, .17f * size)
                    b.organic(x, 2.5f * size, z, 1.25f * size, 1.3f * size, 1.25f * size, look.crown, x + z, 9, 5)
                }
            }
        }
    }

    private companion object {
        const val STEP = .12f
        const val MARGIN = 1.6f
        const val FAR = 700f
        const val RAIL_HEIGHT = .11f
        const val RAIL_THICKNESS = .1f
        const val STRIPE = .45f
        const val POST_HEIGHT = .2f
    }
}

/**
 * The obstacles that move: each gets a mesh built around its own origin, which the renderer places
 * and turns every frame from the game's clock.
 */
internal class MiniScene(private val hole: ClassicHole) {
    private val mini = hole.mini!!
    private val look = MiniLook(mini.theme)

    val rotors: List<Pair<MiniRotor, ClassicMesh>> = mini.rotors.map { it to rotorMesh(it) }
    val sliders: List<Pair<MiniSlider, ClassicMesh>> = mini.sliders.map { it to sliderMesh(it) }
    val wells: List<Pair<MiniWell, ClassicMesh>> = mini.wells.map { it to wellMesh(it) }

    val meshes: List<ClassicMesh> get() = rotors.map { it.second } + sliders.map { it.second } + wells.map { it.second }

    private fun rotorMesh(r: MiniRotor): ClassicMesh {
        val b = MeshBuilder()
        b.cone(0f, 0f, 0f, r.half * 2.3f, ARM_HEIGHT + .06f, look.railAlt.shade(.8f), 12, r.half * 2f)
        b.disc(0f, ARM_HEIGHT + .061f, 0f, r.half * 2f, r.half * 2f, look.railAlt.shade(1.1f), 12)
        for (k in 0 until r.arms) {
            val a = k * 2f * PI.toFloat() / r.arms
            val dx = cos(a); val dz = sin(a)
            // Alternate bands along the arm, as on a sail.
            val bands = 4
            for (band in 0 until bands) {
                val from = r.half + (r.length - r.half) * band / bands
                val to = r.half + (r.length - r.half) * (band + 1) / bands
                val colour = if (band % 2 == 0) look.rail else look.railAlt
                val mx = (from + to) * .5f
                b.slab(dx * mx, dz * mx, (to - from) * .5f, r.half, a, 0f, ARM_HEIGHT, colour.shade(.85f), colour.shade(1.12f))
            }
        }
        return b.build()
    }

    private fun sliderMesh(s: MiniSlider): ClassicMesh {
        val b = MeshBuilder()
        val colour = look.railAlt
        b.slab(0f, 0f, s.hx, s.hz, s.angle, 0f, ARM_HEIGHT, colour.shade(.85f), colour.shade(1.15f))
        // Stripes across the top say that it moves.
        val stripes = max(2, (s.hx * 2f / .25f).toInt())
        val c = cos(s.angle); val sn = sin(s.angle)
        for (k in 0 until stripes step 2) {
            val u = -s.hx + (k + .5f) * 2f * s.hx / stripes
            b.slab(u * c, u * sn, s.hx / stripes, s.hz * 1.001f, s.angle, ARM_HEIGHT, ARM_HEIGHT + .004f, look.rail, look.rail)
        }
        return b.build()
    }

    /** Three spiral arms that turn, flat on the carpet. */
    private fun wellMesh(w: MiniWell): ClassicMesh {
        val b = MeshBuilder()
        val colour = if (w.strength > 0f) C(.85f, .75f, 1f) else C(1f, .85f, .6f)
        val outer = w.reach * .5f
        for (arm in 0 until 3) {
            val base = arm * 2f * PI.toFloat() / 3f
            for (k in 0 until 12) {
                val r0 = .12f + (outer - .12f) * k / 12f; val r1 = .12f + (outer - .12f) * (k + 1) / 12f
                val a0 = base + k * .26f; val a1 = base + (k + 1) * .26f
                val width = .045f * (1f - k / 14f)
                b.quad(P(cos(a0) * (r0 - width), .012f, sin(a0) * (r0 - width)), P(cos(a0) * (r0 + width), .012f, sin(a0) * (r0 + width)),
                    P(cos(a1) * (r1 + width), .012f, sin(a1) * (r1 + width)), P(cos(a1) * (r1 - width), .012f, sin(a1) * (r1 - width)), colour, false)
            }
        }
        return b.build()
    }

    private companion object { const val ARM_HEIGHT = .16f }
}
