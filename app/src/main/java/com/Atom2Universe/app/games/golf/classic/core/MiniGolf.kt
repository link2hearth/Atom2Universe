package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** Look of a mini-golf hole: the renderer maps it to felt, rail and lawn colours. */
enum class MiniTheme { MEADOW, CANDY, OCEAN, COSMOS, GARDEN }

/**
 * A playable area or a patch, as a signed distance in metres (negative inside). The distance is
 * exact for discs and boxes and a tight bound for lanes: enough for rails, which only need the
 * zero contour and its direction.
 */
sealed class MiniShape {
    abstract fun distance(x: Float, z: Float): Float
    /** minX, minZ, maxX, maxZ. */
    abstract val bounds: FloatArray
}

class MiniDisc(val x: Float, val z: Float, val r: Float) : MiniShape() {
    override fun distance(x: Float, z: Float) = hypot(x - this.x, z - this.z) - r
    override val bounds get() = floatArrayOf(x - r, z - r, x + r, z + r)
}

/** A rectangle of half-sizes [hx] and [hz] turned by [angle], its corners rounded by [round]. */
class MiniBox(val x: Float, val z: Float, val hx: Float, val hz: Float,
              val angle: Float = 0f, val round: Float = 0f) : MiniShape() {
    private val cosine = cos(angle)
    private val sine = sin(angle)
    override fun distance(x: Float, z: Float): Float {
        val dx = x - this.x; val dz = z - this.z
        val u = dx * cosine + dz * sine
        val v = -dx * sine + dz * cosine
        val qx = abs(u) - (hx - round); val qz = abs(v) - (hz - round)
        return hypot(max(qx, 0f), max(qz, 0f)) + min(max(qx, qz), 0f) - round
    }
    override val bounds: FloatArray get() {
        val ex = abs(hx * cosine) + abs(hz * sine); val ez = abs(hx * sine) + abs(hz * cosine)
        return floatArrayOf(x - ex, z - ez, x + ex, z + ez)
    }
}

data class MiniNode(val x: Float, val z: Float, val width: Float)

/** Chain of tapered capsules: a lane of [MiniNode.width] metres following its nodes. */
class MiniLane(val nodes: List<MiniNode>) : MiniShape() {
    init { require(nodes.isNotEmpty()) }
    override fun distance(x: Float, z: Float): Float {
        if (nodes.size == 1) return hypot(x - nodes[0].x, z - nodes[0].z) - nodes[0].width * .5f
        var best = Float.MAX_VALUE
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]; val b = nodes[i + 1]
            val abx = b.x - a.x; val abz = b.z - a.z
            val length2 = abx * abx + abz * abz
            val t = if (length2 < 1e-9f) 0f else (((x - a.x) * abx + (z - a.z) * abz) / length2).coerceIn(0f, 1f)
            val d = hypot(x - (a.x + abx * t), z - (a.z + abz * t)) - (a.width + (b.width - a.width) * t) * .5f
            if (d < best) best = d
        }
        return best
    }
    override val bounds: FloatArray get() {
        var x0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (n in nodes) {
            val h = n.width * .5f
            x0 = min(x0, n.x - h); z0 = min(z0, n.z - h); x1 = max(x1, n.x + h); z1 = max(z1, n.z + h)
        }
        return floatArrayOf(x0, z0, x1, z1)
    }
}

/** Sand or water inside the playable area. */
class MiniPatch(val shape: MiniShape, val lie: GolfLie)

/**
 * A raised (or sunk) piece of floor: [height] metres inside [shape], easing to zero over [soft]
 * metres outside it, plus an optional tilt. A small disc with a wide [soft] is a hill; a box
 * across the lane is a ramp or a step.
 */
class MiniTerrain(val shape: MiniShape, val height: Float, val soft: Float = 1f,
                  val tiltX: Float = 0f, val tiltZ: Float = 0f, val originX: Float = 0f, val originZ: Float = 0f) {
    fun heightAt(x: Float, z: Float): Float {
        val d = shape.distance(x, z)
        val mask = if (d <= 0f) 1f else 1f - smooth(d / soft)
        if (mask <= 0f) return 0f
        return mask * (height + tiltX * (x - originX) + tiltZ * (z - originZ))
    }
}

/** A round post. [kick] is the least speed in m/s it throws the ball back at (a pinball bumper). */
class MiniPost(val x: Float, val z: Float, val r: Float, val bounce: Float = .78f, val kick: Float = 0f)

/** Turning arms around a hub: [speed] in rad/s, positive from +x towards +z. */
class MiniRotor(val x: Float, val z: Float, val arms: Int, val length: Float, val half: Float,
                val speed: Float, val phase: Float = 0f) {
    fun angle(clock: Float) = phase + speed * clock
}

/** A box sliding to and fro by (dx, dz) metres around (x, z), once every [period] seconds. */
class MiniSlider(val x: Float, val z: Float, val hx: Float, val hz: Float, val angle: Float,
                 val dx: Float, val dz: Float, val period: Float, val phase: Float = 0f) {
    private val omega = 2f * PI.toFloat() / period
    fun centreX(clock: Float) = x + dx * sin(omega * clock + phase)
    fun centreZ(clock: Float) = z + dz * sin(omega * clock + phase)
    fun speedX(clock: Float) = dx * omega * cos(omega * clock + phase)
    fun speedZ(clock: Float) = dz * omega * cos(omega * clock + phase)
}

/**
 * A pipe mouth: the ball that rolls over it comes out at ([toX], [toZ]). With an [exitHeading]
 * (radians, as the aim: 0 is +z) it leaves in that direction, else it keeps its own.
 */
class MiniPortal(val x: Float, val z: Float, val r: Float, val toX: Float, val toZ: Float,
                 val exitHeading: Float? = null, val tint: Int = 0)

/** Pulls the ball in ([strength] > 0, m/s² at the centre) or pushes it away; swallows within [horizon]. */
class MiniWell(val x: Float, val z: Float, val strength: Float, val reach: Float, val horizon: Float = 0f)

enum class MiniZoneKind { CONVEYOR, BOOST, MUD, ICE }

/** A patch of floor that pushes ((ax, az) in m/s²) or changes the friction ([drag] times the turf's). */
class MiniZone(val shape: MiniShape, val kind: MiniZoneKind, val ax: Float = 0f, val az: Float = 0f, val drag: Float = 1f)

/** Planks over a water patch; scenery only, the bridge is the gap left between two patches. */
class MiniBridge(val x: Float, val z: Float, val hx: Float, val hz: Float, val angle: Float = 0f)

/** Per-ball state the rules need: a pipe mouth only works again once the ball has left it. */
class MiniMotion {
    var armed = true
    /** Fastest knock against a rail, post or arm since it was last read, in m/s: the sound of the hole. */
    var impact = 0f
    fun reset() { armed = true; impact = 0f }
}

enum class MiniEvent { NONE, TELEPORTED, SWALLOWED }

/**
 * One mini-golf hole in the tee-at-origin frame of [ClassicHole]: the lanes (union), what is cut
 * out of them, the floor's relief, sand and water, and the obstacles. Pure geometry and rules:
 * the physics of the ball itself stays [GolfBallPhysics].
 */
class MiniLayout(
    val theme: MiniTheme,
    val lanes: List<MiniShape>,
    val solids: List<MiniShape> = emptyList(),
    val patches: List<MiniPatch> = emptyList(),
    val terrain: List<MiniTerrain> = emptyList(),
    val posts: List<MiniPost> = emptyList(),
    val rotors: List<MiniRotor> = emptyList(),
    val sliders: List<MiniSlider> = emptyList(),
    val portals: List<MiniPortal> = emptyList(),
    val wells: List<MiniWell> = emptyList(),
    val zones: List<MiniZone> = emptyList(),
    val bridges: List<MiniBridge> = emptyList(),
    /** Open pockets the renderer fills with rail-high planters. */
    val fills: List<MiniShape> = emptyList(),
    /** Waypoints for the caddie, the last one being the cup. */
    val route: List<MiniPoint> = emptyList()
) {
    init { require(lanes.isNotEmpty()) }

    val hasMovers get() = rotors.isNotEmpty() || sliders.isNotEmpty()

    /** minX, minZ, maxX, maxZ of the lanes. */
    val bounds: FloatArray by lazy {
        val all = lanes.map { it.bounds }
        floatArrayOf(all.minOf { it[0] }, all.minOf { it[1] }, all.maxOf { it[2] }, all.maxOf { it[3] })
    }

    /** Length of the caddie's route in metres: the figure the menus call the hole's length. */
    val pathLength: Float by lazy {
        var sum = 0f
        for (i in 1 until route.size) sum += hypot(route[i].x - route[i - 1].x, route[i].z - route[i - 1].z)
        sum
    }

    /** Signed distance to the playable area: negative inside the rails. */
    fun sdf(x: Float, z: Float): Float {
        var d = Float.MAX_VALUE
        for (lane in lanes) { val v = lane.distance(x, z); if (v < d) d = v }
        for (solid in solids) { val v = -solid.distance(x, z); if (v > d) d = v }
        return d
    }

    /** Floor height without the fade outside the rails (scenery sits on it). */
    fun floorAt(x: Float, z: Float): Float {
        var h = 0f
        for (t in terrain) h += t.heightAt(x, z)
        for (p in patches) if (p.lie == GolfLie.WATER) {
            val d = p.shape.distance(x, z)
            if (d < 0f) h -= WATER_DEPTH * smooth(min(1f, -d / .25f))
        }
        return h
    }

    /** The ball only reaches the rails, whose outside slopes away to the lawn. */
    fun heightAt(x: Float, z: Float): Float {
        val h = floorAt(x, z)
        if (h == 0f) return 0f
        val d = sdf(x, z)
        return if (d <= FADE_START) h else h * (1f - smooth(min(1f, (d - FADE_START) / FADE_LENGTH)))
    }

    fun lieAt(x: Float, z: Float): GolfLie {
        if (sdf(x, z) > OUT_MARGIN) return GolfLie.OUT
        for (p in patches) if (p.shape.distance(x, z) <= 0f) return p.lie
        return GolfLie.GREEN
    }

    /** Friction multiplier of the zones under the ball (mud, ice). */
    fun dragAt(x: Float, z: Float): Float {
        var m = 1f
        for (zone in zones) if (zone.drag != 1f && zone.shape.distance(x, z) <= 0f) m *= zone.drag
        return m
    }

    /** Strength of the pushes under the ball in m/s²: a ball they hold on the spot cannot rest. */
    fun pushAt(x: Float, z: Float): Float {
        var ax = 0f; var az = 0f
        for (zone in zones) if ((zone.ax != 0f || zone.az != 0f) && zone.shape.distance(x, z) <= 0f) { ax += zone.ax; az += zone.az }
        for (well in wells) {
            val r = hypot(well.x - x, well.z - z)
            if (r < well.reach && r > 1e-4f) { val f = well.pull(r); ax += (well.x - x) / r * f; az += (well.z - z) / r * f }
        }
        return hypot(ax, az)
    }

    private fun MiniWell.pull(r: Float): Float { val k = 1f - r / reach; return strength * k * k }

    /**
     * Moves the rules on by [dt] for a ball that has just rolled: pushes, wells, pipe mouths, then
     * rails and obstacles (the moving ones only if [dynamic]).
     */
    fun advance(b: BallState, dt: Float, clock: Float, motion: MiniMotion, dynamic: Boolean = true): MiniEvent {
        var event = MiniEvent.NONE
        for (zone in zones) if ((zone.ax != 0f || zone.az != 0f) && zone.shape.distance(b.px, b.pz) <= 0f) {
            b.vx += zone.ax * dt; b.vz += zone.az * dt
        }
        for (well in wells) {
            val dx = well.x - b.px; val dz = well.z - b.pz
            val r = hypot(dx, dz)
            if (well.horizon > 0f && well.strength > 0f && r < well.horizon) return MiniEvent.SWALLOWED
            if (r < well.reach && r > 1e-4f) { val f = well.pull(r) * dt; b.vx += dx / r * f; b.vz += dz / r * f }
        }
        if (portals.isNotEmpty()) {
            var over: MiniPortal? = null
            for (p in portals) if (hypot(b.px - p.x, b.pz - p.z) < p.r) { over = p; break }
            if (over == null) motion.armed = true
            else if (motion.armed) {
                val speed = max(hypot(b.vx, b.vz) * .95f, MIN_PIPE_SPEED)
                val heading = over.exitHeading ?: atan2(b.vx, b.vz)
                b.px = over.toX; b.pz = over.toZ
                b.vx = sin(heading) * speed; b.vz = cos(heading) * speed
                motion.armed = false
                event = MiniEvent.TELEPORTED
            }
        }
        repeat(2) {
            rails(b, motion)
            collidePosts(b, motion)
            if (dynamic) { collideRotors(b, clock, true, motion); collideSliders(b, clock, true, motion) }
        }
        rails(b, motion)
        return event
    }

    /** A ball that rests while an arm sweeps over it is carried out of the way, without gaining speed. */
    fun nudgeResting(b: BallState, clock: Float): Boolean {
        if (!hasMovers) return false
        val x = b.px; val z = b.pz
        val quiet = MiniMotion()
        repeat(2) { collideRotors(b, clock, false, quiet); collideSliders(b, clock, false, quiet); rails(b, quiet) }
        return b.px != x || b.pz != z
    }

    /** Keeps the ball's centre one radius inside the rails and bounces it off them. */
    private fun rails(b: BallState, motion: MiniMotion) {
        val d = sdf(b.px, b.pz) + R
        if (d <= 0f) return
        val e = .012f
        var nx = sdf(b.px + e, b.pz) - sdf(b.px - e, b.pz)
        var nz = sdf(b.px, b.pz + e) - sdf(b.px, b.pz - e)
        val length = hypot(nx, nz)
        if (length < 1e-6f) return
        nx /= length; nz /= length
        b.px -= nx * d; b.pz -= nz * d
        val vn = b.vx * nx + b.vz * nz
        if (vn > 0f) { b.vx -= (1f + RAIL_BOUNCE) * vn * nx; b.vz -= (1f + RAIL_BOUNCE) * vn * nz; motion.impact = max(motion.impact, vn) }
    }

    private fun collidePosts(b: BallState, motion: MiniMotion) {
        for (p in posts) {
            val dx = b.px - p.x; val dz = b.pz - p.z
            val d = hypot(dx, dz)
            val reach = p.r + R
            if (d >= reach) continue
            val nx = if (d > 1e-5f) dx / d else 1f
            val nz = if (d > 1e-5f) dz / d else 0f
            b.px = p.x + nx * reach; b.pz = p.z + nz * reach
            val vn = b.vx * nx + b.vz * nz
            if (vn < 0f) { b.vx -= (1f + p.bounce) * vn * nx; b.vz -= (1f + p.bounce) * vn * nz; motion.impact = max(motion.impact, -vn) }
            val out = b.vx * nx + b.vz * nz
            if (p.kick > 0f && out < p.kick) { b.vx += (p.kick - out) * nx; b.vz += (p.kick - out) * nz }
        }
    }

    private fun collideRotors(b: BallState, clock: Float, bounce: Boolean, motion: MiniMotion) {
        for (rotor in rotors) {
            val theta = rotor.angle(clock)
            for (k in 0 until rotor.arms) {
                val a = theta + k * 2f * PI.toFloat() / rotor.arms
                val sx = cos(a) * rotor.length; val sz = sin(a) * rotor.length
                val t = (((b.px - rotor.x) * sx + (b.pz - rotor.z) * sz) / (sx * sx + sz * sz)).coerceIn(0f, 1f)
                val qx = rotor.x + sx * t; val qz = rotor.z + sz * t
                val dx = b.px - qx; val dz = b.pz - qz
                val d = hypot(dx, dz)
                val reach = rotor.half + R
                if (d >= reach) continue
                val nx = if (d > 1e-5f) dx / d else -sin(a)
                val nz = if (d > 1e-5f) dz / d else cos(a)
                b.px = qx + nx * reach; b.pz = qz + nz * reach
                if (!bounce) continue
                // The arm's own speed at the contact: the ball is thrown by the blade, not only stopped.
                val vsx = -rotor.speed * (qz - rotor.z); val vsz = rotor.speed * (qx - rotor.x)
                val vn = (b.vx - vsx) * nx + (b.vz - vsz) * nz
                if (vn < 0f) { b.vx -= (1f + ARM_BOUNCE) * vn * nx; b.vz -= (1f + ARM_BOUNCE) * vn * nz; motion.impact = max(motion.impact, -vn) }
            }
        }
    }

    private fun collideSliders(b: BallState, clock: Float, bounce: Boolean, motion: MiniMotion) {
        for (s in sliders) {
            val cx = s.centreX(clock); val cz = s.centreZ(clock)
            val c = cos(s.angle); val sn = sin(s.angle)
            val dx = b.px - cx; val dz = b.pz - cz
            val u = dx * c + dz * sn; val v = -dx * sn + dz * c
            val qu = u.coerceIn(-s.hx, s.hx); val qv = v.coerceIn(-s.hz, s.hz)
            var nu = u - qu; var nv = v - qv
            var d = hypot(nu, nv)
            if (d >= R) continue
            if (d < 1e-6f) {
                // The centre is inside the box: leave by the nearest face.
                val pu = s.hx - abs(u); val pv = s.hz - abs(v)
                if (pu < pv) { nu = sign(u).let { if (it == 0f) 1f else it }; nv = 0f; d = -pu } else { nu = 0f; nv = sign(v).let { if (it == 0f) 1f else it }; d = -pv }
            } else { nu /= d; nv /= d }
            val nx = nu * c - nv * sn; val nz = nu * sn + nv * c
            val push = R - d
            b.px += nx * push; b.pz += nz * push
            if (!bounce) continue
            val vn = (b.vx - s.speedX(clock)) * nx + (b.vz - s.speedZ(clock)) * nz
            if (vn < 0f) { b.vx -= (1f + ARM_BOUNCE) * vn * nx; b.vz -= (1f + ARM_BOUNCE) * vn * nz; motion.impact = max(motion.impact, -vn) }
        }
    }

    /** Is the straight line free of rails (and of what is cut out) for a ball? */
    fun clear(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val length = hypot(x1 - x0, z1 - z0)
        val steps = max(1, (length / .1f).toInt())
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (sdf(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t) > -R - .01f) return false
        }
        return true
    }

    /** Caddie: the cup if it is in view, else the farthest waypoint that is. */
    fun aim(ball: GolfPoint, cup: GolfPoint): GolfPoint {
        if (clear(ball.x, ball.z, cup.x, cup.z)) return cup
        for (i in route.indices.reversed()) {
            val p = route[i]
            if (hypot(p.x - ball.x, p.z - ball.z) < .5f) continue
            if (clear(ball.x, ball.z, p.x, p.z)) return GolfPoint(p.x, heightAt(p.x, p.z), p.z)
        }
        val nearest = route.filter { hypot(it.x - ball.x, it.z - ball.z) > .5f }
            .minByOrNull { hypot(it.x - ball.x, it.z - ball.z) }
        return if (nearest != null) GolfPoint(nearest.x, heightAt(nearest.x, nearest.z), nearest.z) else cup
    }

    companion object {
        const val WATER_DEPTH = .1f
        /** Rails are the ball's walls: it bounces off with this share of its speed. */
        const val RAIL_BOUNCE = .78f
        const val ARM_BOUNCE = .6f
        /** Lawn side: the floor's relief fades out between these distances from the rails. */
        const val FADE_START = .2f
        const val FADE_LENGTH = .35f
        const val OUT_MARGIN = .12f
        const val MIN_PIPE_SPEED = .8f
        private const val R = ClassicHole.BALL_RADIUS
    }
}

data class MiniPoint(val x: Float, val z: Float)

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
