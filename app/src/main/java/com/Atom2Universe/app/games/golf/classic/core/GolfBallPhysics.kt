package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** Mutable ball state: metres, metres per second and radians per second. No allocation per step. */
class BallState {
    var px = 0f; var py = 0f; var pz = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var wx = 0f; var wy = 0f; var wz = 0f
    fun stopMotion() { vx = 0f; vy = 0f; vz = 0f; wx = 0f; wy = 0f; wz = 0f }
}

/**
 * Golf ball: real drag and Magnus lift in flight, a readable turf landing (see [bounce]), then
 * rolling with slope and turf drag.
 */
object GolfBallPhysics {
    const val RADIUS = .02135f
    const val GRAVITY = 9.81f
    const val STEP = 1f / 120f
    /** Air density × cross-section / (2 × mass) for a 45.9 g, 42.7 mm ball. */
    private const val AIR = .0191f
    private const val DRAG = .21f
    private const val DRAG_SPIN = .30f
    private const val LIFT = .04f
    private const val LIFT_SPIN = .95f
    private const val LIFT_MAX = .38f
    private val SPIN_DECAY = exp(-STEP / 24f)
    /** Below this rebound speed the ball stops hopping and rolls. */
    const val ROLL_THRESHOLD = .6f

    /**
     * At the landing, the ball keeps [keep] / (1 + [bite] × spin ratio) of its speed along the
     * ground (spin ratio = backspin contact speed / landing speed). On a green, a ratio above
     * [SPIN_BACK_START], which only a low contact with a wedge reaches, pulls it back by
     * [spinBack] × excess × landing speed, at most [backRoll].
     */
    private class Turf(val restitution: Float, val friction: Float, val keep: Float, val bite: Float,
                       val spinBack: Float, val rolling: Float, val backRoll: Float)
    private val green = Turf(.95f, .45f, .34f, 1.8f, .32f, .55f, 1.5f)
    private val fairway = Turf(1f, .42f, .45f, .54f, 0f, 1.7f, 1.5f)
    private val rough = Turf(.7f, .55f, .3f, .5f, 0f, 3.8f, .6f)
    private val semiRough = Turf(.85f, .49f, .37f, .52f, 0f, 2.6f, .9f)
    private val fringe = Turf(.98f, .44f, .40f, 1.1f, .12f, 1.05f, 1.5f)
    private val sand = Turf(.25f, .8f, .12f, 0f, 0f, 9f, .2f)
    private const val SPIN_BACK_START = .95f
    private fun turf(lie: GolfLie) = when (lie) {
        GolfLie.GREEN -> green
        GolfLie.ROUGH -> rough
        GolfLie.SEMI_ROUGH -> semiRough
        GolfLie.FRINGE -> fringe
        GolfLie.BUNKER -> sand
        else -> fairway
    }
    /** Constant rolling deceleration; the green value is a stimpmeter reading of about 3 m. */
    fun rolling(lie: GolfLie): Float = turf(lie).rolling

    fun flightStep(b: BallState, windX: Float, windZ: Float, dt: Float = STEP) {
        val rx = b.vx - windX; val ry = b.vy; val rz = b.vz - windZ
        val speed = sqrt(rx * rx + ry * ry + rz * rz)
        var ax = 0f; var ay = -GRAVITY; var az = 0f
        if (speed > 1e-4f) {
            val spin = sqrt(b.wx * b.wx + b.wy * b.wy + b.wz * b.wz)
            val ratio = RADIUS * spin / speed
            val drag = AIR * (DRAG + DRAG_SPIN * ratio) * speed
            ax -= drag * rx; ay -= drag * ry; az -= drag * rz
            if (spin > 1e-3f) {
                val lift = AIR * min(LIFT_MAX, LIFT + LIFT_SPIN * ratio) * speed / spin
                ax += lift * (b.wy * rz - b.wz * ry)
                ay += lift * (b.wz * rx - b.wx * rz)
                az += lift * (b.wx * ry - b.wy * rx)
            }
        }
        b.vx += ax * dt; b.vy += ay * dt; b.vz += az * dt
        b.px += b.vx * dt; b.py += b.vy * dt; b.pz += b.vz * dt
        val decay = if (dt == STEP) SPIN_DECAY else exp(-dt / 24f)
        b.wx *= decay; b.wy *= decay; b.wz *= decay
    }

    /**
     * One contact with a surface of normal (nx, ny, nz). The first contact of a shot ([landing]) is
     * the turf's: the share of speed the ball keeps along the ground depends on the turf and the
     * backspin only, never on how steep the fall is, so a slope merely shifts the split between
     * bounce and run. The grass takes the spin and the ball leaves rolling. Later hops are plain
     * Coulomb friction at the contact point.
     */
    fun bounce(b: BallState, nx: Float, ny: Float, nz: Float, lie: GolfLie, landing: Boolean) {
        val t = turf(lie)
        val speed = sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz)
        if (speed < 1e-5f) return
        val vn = b.vx * nx + b.vy * ny + b.vz * nz
        if (vn >= 0f) return
        val tx = b.vx - vn * nx; val ty = b.vy - vn * ny; val tz = b.vz - vn * nz
        val restitution = t.restitution * if (speed <= 20f) .510f - .0375f * speed + .000903f * speed * speed else .12f
        if (landing) { land(b, nx, ny, nz, tx, ty, tz, vn, speed, restitution, t); return }
        // Slip of the contact point r = -R n: v_t + ω × r.
        val sx = tx - RADIUS * (b.wy * nz - b.wz * ny)
        val sy = ty - RADIUS * (b.wz * nx - b.wx * nz)
        val sz = tz - RADIUS * (b.wx * ny - b.wy * nx)
        val slip = sqrt(sx * sx + sy * sy + sz * sz)
        b.vx = tx - restitution * vn * nx; b.vy = ty - restitution * vn * ny; b.vz = tz - restitution * vn * nz
        if (slip > 1e-5f) {
            val ux = sx / slip; val uy = sy / slip; val uz = sz / slip
            val impulse = min(t.friction * (1f + restitution) * -vn, slip * 2f / 7f)
            b.vx -= impulse * ux; b.vy -= impulse * uy; b.vz -= impulse * uz
            val k = 5f * impulse / (2f * RADIUS)
            b.wx += k * (ny * uz - nz * uy); b.wy += k * (nz * ux - nx * uz); b.wz += k * (nx * uy - ny * ux)
        }
    }

    private fun land(b: BallState, nx: Float, ny: Float, nz: Float, tx: Float, ty: Float, tz: Float,
                     vn: Float, speed: Float, restitution: Float, t: Turf) {
        val tangent = sqrt(tx * tx + ty * ty + tz * tz)
        var ux = 0f; var uy = 0f; var uz = 0f; var along = 0f
        if (tangent > 1e-5f) {
            ux = tx / tangent; uy = ty / tangent; uz = tz / tangent
            // Contact-point speed that backspin adds along the run: -R (ω × n)·u.
            val backspin = -RADIUS * ((b.wy * nz - b.wz * ny) * ux + (b.wz * nx - b.wx * nz) * uy + (b.wx * ny - b.wy * nx) * uz)
            val ratio = max(0f, backspin) / speed
            along = tangent * t.keep / (1f + t.bite * ratio) - t.spinBack * max(0f, ratio - SPIN_BACK_START) * speed
            // Never more energy than it came with: keep ≤ 0.8, and a return needs a spin ratio near 1.
            along = max(along, -min(t.backRoll, .8f * speed))
        }
        b.vx = ux * along - restitution * vn * nx
        b.vy = uy * along - restitution * vn * ny
        b.vz = uz * along - restitution * vn * nz
        val roll = along / RADIUS
        b.wx = (ny * uz - nz * uy) * roll; b.wy = (nz * ux - nx * uz) * roll; b.wz = (nx * uy - ny * ux) * roll
    }

    /**
     * The last hop ends in pure rolling: angular momentum about the contact point gives
     * v = v_t − 2/7 × slip. A ball spun back by the contact is held to the turf's return speed.
     */
    fun startRolling(b: BallState, nx: Float, ny: Float, nz: Float, lie: GolfLie) {
        val vn = b.vx * nx + b.vy * ny + b.vz * nz
        val tx = b.vx - vn * nx; val ty = b.vy - vn * ny; val tz = b.vz - vn * nz
        val sx = tx - RADIUS * (b.wy * nz - b.wz * ny)
        val sz = tz - RADIUS * (b.wx * ny - b.wy * nx)
        var vx = tx - sx * 2f / 7f
        var vz = tz - sz * 2f / 7f
        val forward = hypot(tx, tz)
        val returning = forward < 1e-4f || vx * tx + vz * tz < 0f
        val speed = hypot(vx, vz)
        val limit = turf(lie).backRoll
        if (returning && speed > limit) { val scale = limit / speed; vx *= scale; vz *= scale }
        b.vx = vx; b.vy = 0f; b.vz = vz
        b.wx = 0f; b.wy = 0f; b.wz = 0f
    }

    /** Downhill pull on a rolling solid sphere (5/7 g sin θ) from the terrain gradient. */
    fun rollingStep(b: BallState, gradientX: Float, gradientZ: Float, lie: GolfLie, dt: Float = STEP) {
        val slope = sqrt(1f + gradientX * gradientX + gradientZ * gradientZ)
        val pull = 5f / 7f * GRAVITY / slope
        b.vx -= pull * gradientX * dt
        b.vz -= pull * gradientZ * dt
        val speed = hypot(b.vx, b.vz)
        val retention = if (speed > 0f) max(0f, 1f - rolling(lie) * dt / speed) else 0f
        b.vx *= retention; b.vz *= retention
        b.px += b.vx * dt; b.pz += b.vz * dt
    }

    /** True when the turf can hold the ball still on this slope. */
    fun canRest(gradientX: Float, gradientZ: Float, lie: GolfLie): Boolean {
        val g2 = gradientX * gradientX + gradientZ * gradientZ
        return 5f / 7f * GRAVITY * sqrt(g2 / (1f + g2)) < rolling(lie) * .9f
    }

    /** Launch from (0, R, 0) along +z on flat ground with no wind; returns the carry in metres. */
    fun flatCarry(speed: Float, launchDegrees: Float, spinRpm: Float): Float =
        flatLanding(speed, launchDegrees, spinRpm, descent = false)

    /** Same flat shot; returns the angle of descent in degrees at the first landing. */
    fun flatDescent(speed: Float, launchDegrees: Float, spinRpm: Float): Float =
        flatLanding(speed, launchDegrees, spinRpm, descent = true)

    private fun flatLanding(speed: Float, launchDegrees: Float, spinRpm: Float, descent: Boolean): Float {
        val b = BallState()
        launch(b, 0f, RADIUS, 0f, speed, launchDegrees, spinRpm, 0f, 0f)
        var previousY = b.py; var previousZ = b.pz
        repeat(30 * 120) {
            previousY = b.py; previousZ = b.pz
            flightStep(b, 0f, 0f)
            if (b.py <= RADIUS && b.vy < 0f) {
                if (descent) return Math.toDegrees(atan2(-b.vy, hypot(b.vx, b.vz)).toDouble()).toFloat()
                val t = ((previousY - RADIUS) / (previousY - b.py).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
                return previousZ + (b.pz - previousZ) * t
            }
        }
        return if (descent) launchDegrees else b.pz
    }

    /**
     * Places a launched ball. The spin axis starts horizontal (pure backspin) and is tilted by
     * [tiltDegrees]: positive tilts curve the ball to the player's right.
     */
    fun launch(b: BallState, x: Float, y: Float, z: Float, speed: Float, launchDegrees: Float,
               spinRpm: Float, heading: Float, tiltDegrees: Float) {
        val pitch = Math.toRadians(launchDegrees.toDouble()).toFloat()
        val dx = sin(heading); val dz = cos(heading)
        b.px = x; b.py = y; b.pz = z
        b.vx = dx * cos(pitch) * speed; b.vy = sin(pitch) * speed; b.vz = dz * cos(pitch) * speed
        // Backspin axis = direction × up = player's right; tilting it towards down curves right.
        val tilt = Math.toRadians(tiltDegrees.toDouble()).toFloat()
        val omega = spinRpm * 2f * PI.toFloat() / 60f
        b.wx = -dz * cos(tilt) * omega
        b.wy = -sin(tilt) * omega
        b.wz = dx * cos(tilt) * omega
    }
}

/**
 * Per-club calibration: a full swing carries exactly the club's nominal distance on flat ground,
 * and a given power carries that fraction of it. Ball speed and spin both scale with the swing.
 *
 * A shorter swing launches higher, so that every shot with a club comes down at the full swing's
 * angle and runs like it. With the club's own launch, a slow ball gets little lift and lands flat
 * (a 7 iron at 40 % came in at 23° instead of 46°) and ran much further than a full swing.
 */
internal object GolfCalibration {
    private class Table(val fullSpeed: Float, val carries: FloatArray, val launches: FloatArray)
    private const val SAMPLES = 32
    private val tables = arrayOfNulls<Table>(GolfClub.entries.size)

    private fun table(club: GolfClub): Table = synchronized(tables) {
        tables[club.ordinal] ?: build(club).also { tables[club.ordinal] = it }
    }

    private fun build(club: GolfClub): Table {
        var lo = 5f; var hi = 140f
        repeat(36) {
            val mid = (lo + hi) * .5f
            if (GolfBallPhysics.flatCarry(mid, club.launch, club.spinRpm) < club.carry) lo = mid else hi = mid
        }
        val full = (lo + hi) * .5f
        val descent = GolfBallPhysics.flatDescent(full, club.launch, club.spinRpm)
        val carries = FloatArray(SAMPLES + 1)
        val launches = FloatArray(SAMPLES + 1)
        // Without speed there is no air: the arc is symmetric and leaves at its landing angle.
        launches[0] = descent
        for (i in 1..SAMPLES) {
            val speed = full * i / SAMPLES
            val spin = club.spinRpm * i / SAMPLES
            // The air only steepens the fall, so the launch lies between the club's and the last one.
            var low = club.launch
            var high = max(low, launches[i - 1])
            if (i < SAMPLES && GolfBallPhysics.flatDescent(speed, low, spin) < descent) {
                if (GolfBallPhysics.flatDescent(speed, high, spin) < descent) high = 80f
                repeat(16) {
                    val mid = (low + high) * .5f
                    if (GolfBallPhysics.flatDescent(speed, mid, spin) < descent) low = mid else high = mid
                }
                launches[i] = (low + high) * .5f
            } else launches[i] = club.launch
            carries[i] = GolfBallPhysics.flatCarry(speed, launches[i], spin)
        }
        return Table(full, carries, launches)
    }

    /** Launch angle in degrees of a swing fraction (ball speed / full speed). */
    fun launch(club: GolfClub, swing: Float): Float {
        val t = table(club)
        val x = (if (swing.isFinite()) swing.coerceIn(0f, 1f) else 0f) * SAMPLES
        val i = min(x.toInt(), SAMPLES - 1)
        return t.launches[i] + (t.launches[i + 1] - t.launches[i]) * (x - i)
    }

    /** Swing fraction (ball speed / full speed) whose flat carry is [power] × nominal carry. */
    fun swing(club: GolfClub, power: Float): Float {
        val t = table(club)
        val target = power.coerceIn(0f, 1f) * t.carries[SAMPLES]
        for (i in 1..SAMPLES) if (t.carries[i] >= target) {
            val a = t.carries[i - 1]; val b = t.carries[i]
            val local = if (b - a > 1e-5f) (target - a) / (b - a) else 0f
            return (i - 1 + local) / SAMPLES
        }
        return 1f
    }

    fun fullSpeed(club: GolfClub): Float = table(club).fullSpeed
}
