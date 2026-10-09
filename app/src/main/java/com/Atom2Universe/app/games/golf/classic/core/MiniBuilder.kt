package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.PI

internal fun miniDisc(x: Float, z: Float, r: Float) = MiniDisc(x, z, r)
internal fun miniBox(x: Float, z: Float, hx: Float, hz: Float, angle: Float = 0f, round: Float = 0f) =
    MiniBox(x, z, hx, hz, angle, round)

/**
 * Writes a mini-golf hole the way one draws it: lanes with a width, obstacles with a place. The tee
 * is the origin and +z runs away from the player; +x is the player's left. All in metres.
 */
internal class MiniBuilder(private val theme: MiniTheme) {
    private val lanes = ArrayList<MiniShape>()
    private val solids = ArrayList<MiniShape>()
    private val patches = ArrayList<MiniPatch>()
    private val terrain = ArrayList<MiniTerrain>()
    private val posts = ArrayList<MiniPost>()
    private val rotors = ArrayList<MiniRotor>()
    private val sliders = ArrayList<MiniSlider>()
    private val portals = ArrayList<MiniPortal>()
    private val wells = ArrayList<MiniWell>()
    private val zones = ArrayList<MiniZone>()
    private val bridges = ArrayList<MiniBridge>()
    private val fills = ArrayList<MiniShape>()
    private val route = ArrayList<MiniPoint>()
    private var firstLane: List<MiniPoint> = emptyList()

    /** A lane of constant [width] through [points]; round at both ends. */
    fun lane(width: Float, vararg points: Pair<Float, Float>) {
        lanes += MiniLane(points.map { MiniNode(it.first, it.second, width) })
        if (firstLane.isEmpty()) firstLane = points.map { MiniPoint(it.first, it.second) }
    }

    /** A lane whose width changes from node to node. */
    fun taper(vararg nodes: MiniNode) {
        lanes += MiniLane(nodes.toList())
        if (firstLane.isEmpty()) firstLane = nodes.map { MiniPoint(it.x, it.z) }
    }

    /** A lane following an already computed chain. */
    fun chain(width: Float, points: List<Pair<Float, Float>>) = lane(width, *points.toTypedArray())

    fun room(x: Float, z: Float, hx: Float, hz: Float, angle: Float = 0f, round: Float = .3f) {
        lanes += MiniBox(x, z, hx, hz, angle, round)
        if (firstLane.isEmpty()) firstLane = listOf(MiniPoint(x, z))
    }

    fun disc(x: Float, z: Float, r: Float) { lanes += MiniDisc(x, z, r) }

    /** A block cut out of the lanes: a wall standing in the way. */
    fun block(x: Float, z: Float, hx: Float, hz: Float, angle: Float = 0f, round: Float = .1f) {
        solids += MiniBox(x, z, hx, hz, angle, round)
    }

    fun pillar(x: Float, z: Float, r: Float) { solids += MiniDisc(x, z, r) }

    fun post(x: Float, z: Float, r: Float = .2f, bounce: Float = .78f, kick: Float = 0f) { posts += MiniPost(x, z, r, bounce, kick) }

    fun rotor(x: Float, z: Float, arms: Int, length: Float, speed: Float, phase: Float = 0f, half: Float = .06f) {
        rotors += MiniRotor(x, z, arms, length, half, speed, phase)
    }

    fun slider(x: Float, z: Float, hx: Float, hz: Float, dx: Float, dz: Float, period: Float,
               phase: Float = 0f, angle: Float = 0f) {
        sliders += MiniSlider(x, z, hx, hz, angle, dx, dz, period, phase)
    }

    fun water(shape: MiniShape) { patches += MiniPatch(shape, GolfLie.WATER) }
    fun sand(shape: MiniShape) { patches += MiniPatch(shape, GolfLie.BUNKER) }
    fun bridge(x: Float, z: Float, hx: Float, hz: Float, angle: Float = 0f) { bridges += MiniBridge(x, z, hx, hz, angle) }

    /** Floor raised by [height] inside [shape], easing out over [soft] metres. */
    fun terrace(shape: MiniShape, height: Float, soft: Float = 1f, tiltX: Float = 0f, tiltZ: Float = 0f) {
        terrain += MiniTerrain(shape, height, soft, tiltX, tiltZ)
    }

    fun hill(x: Float, z: Float, height: Float, radius: Float = .25f, soft: Float = 1.1f) {
        terrain += MiniTerrain(MiniDisc(x, z, radius), height, soft)
    }

    /** One-way pipe from (x, z) to (toX, toZ); [heading] is the exit direction (0 runs along +z). */
    fun pipe(x: Float, z: Float, toX: Float, toZ: Float, heading: Float? = null, r: Float = .16f, tint: Int = 0) {
        portals += MiniPortal(x, z, r, toX, toZ, heading, tint)
    }

    /** Two mouths that lead to each other. */
    fun twin(ax: Float, az: Float, bx: Float, bz: Float, headingA: Float? = null, headingB: Float? = null,
             r: Float = .16f, tint: Int = 0) {
        pipe(ax, az, bx, bz, headingB, r, tint)
        pipe(bx, bz, ax, az, headingA, r, tint)
    }

    fun well(x: Float, z: Float, strength: Float, reach: Float, horizon: Float = 0f) { wells += MiniWell(x, z, strength, reach, horizon) }

    fun conveyor(shape: MiniShape, ax: Float, az: Float) { zones += MiniZone(shape, MiniZoneKind.CONVEYOR, ax, az) }
    fun boost(shape: MiniShape, ax: Float, az: Float) { zones += MiniZone(shape, MiniZoneKind.BOOST, ax, az) }
    fun mud(shape: MiniShape, drag: Float = 4f) { zones += MiniZone(shape, MiniZoneKind.MUD, drag = drag) }
    fun ice(shape: MiniShape, drag: Float = .12f) { zones += MiniZone(shape, MiniZoneKind.ICE, drag = drag) }

    fun fill(shape: MiniShape) { fills += shape }

    /** The caddie's waypoints, in order; the cup is added at the end. */
    fun route(vararg points: Pair<Float, Float>) { route += points.map { MiniPoint(it.first, it.second) } }

    fun build(cupX: Float, cupZ: Float): MiniLayout {
        val waypoints = (if (route.isEmpty()) firstLane else route) + MiniPoint(cupX, cupZ)
        return MiniLayout(theme, lanes, solids, patches, terrain, posts, rotors, sliders, portals, wells, zones,
            bridges, fills, waypoints)
    }
}

/** Mini-golf holes share one cup, one putter and one carpet: only their layout changes. */
internal fun miniHole(number: Int, par: Int, cupX: Float, cupZ: Float, theme: MiniTheme,
                      setup: MiniBuilder.() -> Unit): ClassicHole {
    val layout = MiniBuilder(theme).apply(setup).build(cupX, cupZ)
    return ClassicHole(number, par, cupZ, finishX = cupX, width = 60f, cupRadius = MINI_CUP_RADIUS,
        puttRange = MINI_PUTT_RANGE, rollScale = MINI_ROLL_SCALE, mini = layout)
}

/** A 15 cm cup, a little more than a regulation one: the ball is slow, the lanes narrow. */
internal const val MINI_CUP_RADIUS = .075f
/** The longest putt rolls this far on a flat carpet. */
internal const val MINI_PUTT_RANGE = 14f
/** Carpet holds the ball back more than a green does. */
internal const val MINI_ROLL_SCALE = 1.6f
internal const val QUARTER_TURN = (PI / 2).toFloat()
