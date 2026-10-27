package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import kotlin.math.*
import kotlin.random.Random

/**
 * One procedural world in metres (doubles: it spans a sesame seed to the great walls of galaxies), made
 * of square rings nested around the start: your house, its suburb, the town, the countryside, the
 * mountains, the Earth, the solar system, the stars, the Milky Way and the universe. Each ring is laid
 * out like the real thing (rooms, streets of houses, fields and woods, mountain ranges, orbits, spiral
 * arms, the cosmic web) and is only built when the gobbler gets near it.
 */
class GluttonWorld(val seed: Long) {
    /** Floor and ground textures (assets/glutton/ground_<name>.webp); the view tints SPACE per era. */
    enum class Ground { WOOD, TILES, CARPET, BATH, GRASS, ASPHALT, PAVEMENT, COBBLES, WHEAT, MEADOW, FOREST, WATER, ROCK, SAND, OCEAN, SPACE }
    /** A piece of ground drawn under everything: a rectangle, or the disc inscribed in it when [round]. */
    class Patch(val ground: Ground, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val round: Boolean = false)
    /** A wall along x (horizontal) or y; it stops the gobbler until its radius reaches [height]. */
    class Wall(val horizontal: Boolean, val line: Double, val from: Double, val until: Double, val thickness: Double, val height: Double)
    /** Painted line on a road: dashes along a segment. */
    class Marking(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val width: Double)
    /** A route that vehicles drive along: a polyline, looping when [closed]. */
    class Lane(val points: DoubleArray, val closed: Boolean) {
        private val lengths = DoubleArray(points.size / 2)
        val length: Double
        init {
            var total = 0.0
            val n = points.size / 2
            for (i in 0 until n) {
                val j = if (i + 1 < n) i + 1 else if (closed) 0 else i
                total += hypot(points[j * 2] - points[i * 2], points[j * 2 + 1] - points[i * 2 + 1]); lengths[i] = total
            }
            length = total
        }
        /** Position at distance [s] along the lane into [out] (x, y, direction x, direction y). */
        fun at(s: Double, out: DoubleArray) {
            val n = points.size / 2
            var i = 0
            while (i < n - 1 && lengths[i] < s) i++
            val j = if (i + 1 < n) i + 1 else if (closed) 0 else i
            val start = if (i == 0) 0.0 else lengths[i - 1]
            val ax = points[i * 2]; val ay = points[i * 2 + 1]; val bx = points[j * 2]; val by = points[j * 2 + 1]
            val seg = hypot(bx - ax, by - ay).coerceAtLeast(1e-9)
            val f = ((s - start) / seg).coerceIn(0.0, 1.0)
            out[0] = ax + (bx - ax) * f; out[1] = ay + (by - ay) * f; out[2] = (bx - ax) / seg; out[3] = (by - ay) / seg
        }
    }
    /** Circular motion around a point or around another placement (a moon around its planet). */
    class Orbit(val cx: Double, val cy: Double, val radius: Double, val period: Double, val phase: Double, val parent: Placement? = null)
    class Placement(val prop: Prop, val x: Double, val y: Double, val radius: Double, val ring: Ring, val angle: Float = 0f, val flip: Boolean = false) {
        var lane: Lane? = null
        var laneAt = 0.0
        /** Area a wandering or chasing thing keeps to: x0, y0, x1, y1. */
        var box: DoubleArray? = null
        var orbit: Orbit? = null
    }
    /**
     * [from] and [to]: gobbler radius expected on entering and on leaving the ring; food is graded between
     * them by distance from the centre. A ring with a [cover] hides the rings [coverFrom]..index - 1 behind
     * one big thing (your house, the Earth, the Milky Way) once the gobbler has reached it. [pace] scales
     * how fast the gobbler digests there, so that each era lasts about as long as its riches deserve.
     */
    class Ring(val index: Int, val theme: Theme, val half: Double, val from: Double, val to: Double, val ground: Ground, val pace: Double = 1.0) {
        var inner = 0.0
        val patches = ArrayList<Patch>()
        val walls = ArrayList<Wall>()
        val markings = ArrayList<Marking>()
        val lanes = ArrayList<Lane>()
        /** Orbits drawn as thin circles: cx, cy, radius. */
        val circles = ArrayList<DoubleArray>()
        val placements = ArrayList<Placement>()
        val movers = ArrayList<Placement>()
        val hourglasses = ArrayList<Placement>()
        /** Sides (0 top, 1 right, 2 bottom, 3 left) with a way out of the house. */
        val exits = ArrayList<Int>()
        /** Just outside each way out of the house: x, y. */
        val doors = ArrayList<DoubleArray>()
        var cover: Prop? = null
        var coverRadius = 0.0
        var coverFrom = 0
        var built = false
        fun contains(x: Double, y: Double) = abs(x) < half && abs(y) < half
        /** Gobbler radius this spot's food is sized for: from the inner edge (from) to the outer edge (to). */
        fun tier(x: Double, y: Double): Double {
            val start = if (inner > 0.0) inner else .4
            val d = max(abs(x), abs(y)).coerceIn(start, half)
            val f = ln(d / start) / ln(half / start)
            return from * (to / from).pow(f)
        }
    }

    val rings: List<Ring> = listOf(
        Ring(0, Theme.ROOM, 8.0, .015, .6, Ground.WOOD),
        Ring(1, Theme.SUBURB, 70.0, .6, 6.0, Ground.GRASS),
        Ring(2, Theme.CITY, 1050.0, 6.0, 120.0, Ground.PAVEMENT, .6),
        Ring(3, Theme.COUNTRY, 1.5e4, 120.0, 1500.0, Ground.MEADOW, .45),
        Ring(4, Theme.MOUNTAINS, 2.2e5, 1500.0, 2.2e4, Ground.MEADOW, .5),
        Ring(5, Theme.EARTH, 4.5e6, 2.2e4, 1.1e6, Ground.OCEAN),
        Ring(6, Theme.PLANETS, 7e9, 1.1e6, 1.6e8, Ground.SPACE, 1.3),
        Ring(7, Theme.STARS, 8.4e10, 1.6e8, 5.6e9, Ground.SPACE, 1.3),
        Ring(8, Theme.STARS, 1.2e12, 5.6e9, 8e10, Ground.SPACE, 1.3),
        Ring(9, Theme.STARS, 1.8e13, 8e10, 1.2e12, Ground.SPACE, 1.3),
        Ring(10, Theme.STARS, 2.7e14, 1.2e12, 1.8e13, Ground.SPACE, 1.3),
        Ring(11, Theme.STARS, 4e15, 1.8e13, 2.7e14, Ground.SPACE, 1.3),
        Ring(12, Theme.STARS, 6e16, 2.7e14, 4e15, Ground.SPACE, 1.3),
        Ring(13, Theme.GALAXY, 9e17, 4e15, 6e16, Ground.SPACE, 1.3),
        Ring(14, Theme.GALAXY, 1.35e19, 6e16, 9e17, Ground.SPACE, 1.3),
        Ring(15, Theme.GALAXY, 2e20, 9e17, 1.35e19, Ground.SPACE, 1.3),
        Ring(16, Theme.COSMOS, 3e21, 1.35e19, 2e20, Ground.SPACE, 1.3),
        Ring(17, Theme.COSMOS, 4.5e22, 2e20, 3e21, Ground.SPACE, 1.3),
        Ring(18, Theme.COSMOS, 6.8e23, 3e21, 4.5e22, Ground.SPACE, 1.3),
        Ring(19, Theme.COSMOS, 1e25, 4.5e22, 6.8e23, Ground.SPACE, 1.3),
        Ring(20, Theme.COSMOS, 1.5e26, 6.8e23, 1e25, Ground.SPACE, 1.3)).also { list ->
        for (i in 1 until list.size) list[i].inner = list[i - 1].half
        list[1].apply { cover = GluttonCatalog.house; coverRadius = list[0].half * 1.05; coverFrom = 0 }
        list[6].apply { cover = GluttonCatalog.byId.getValue("earth"); coverRadius = 6.371e6; coverFrom = 0 }
        list[16].apply { cover = GluttonCatalog.byId.getValue("milky_way"); coverRadius = list[15].half * 1.2; coverFrom = 6 }
    }
    val startX = 0.0
    val startY = 0.0
    /** The galactic centre the spiral arms of the Milky Way rings wind around. */
    val galaxyCentre = doubleArrayOf(-1.1e20, .5e20)

    init { ensure(1) }

    fun ringAt(x: Double, y: Double): Ring? {
        for (ring in rings) if (ring.contains(x, y)) return ring
        return null
    }
    /** Builds every ring up to [index]; returns the rings built by this call. */
    fun ensure(index: Int): List<Ring> {
        val built = ArrayList<Ring>(2)
        for (ring in rings) {
            if (ring.index > index) break
            if (ring.built) continue
            GluttonLayout(this, ring, Random(seed * 1_000_003L + ring.index * 7919L)).build()
            ring.built = true; built += ring
        }
        return built
    }
    /** Every placement of the built rings (tests and the robot's maps). */
    val placements get() = rings.filter { it.built }.flatMap { it.placements }
}
