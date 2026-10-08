package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

enum class GolfState { READY, FLYING, ROLLING, HOLED }

/**
 * Nominal shot guide: clean contact and no wind. [roll] is the run after the landing, simulated
 * with the real hops and slopes; a putt's whole path is in [flight]. Wind and trees are not drawn.
 */
data class ShotPreview(
    val flight: List<GolfPoint>,
    val landing: GolfPoint?,
    val roll: List<GolfPoint>,
    val hazard: Boolean,
    val carry: Float,
) {
    companion object { val NONE = ShotPreview(emptyList(), null, emptyList(), false, 0f) }
}

/** Pure deterministic golf simulation, owned by the UI thread. Rendering never advances physics. */
class ClassicGame(val hole: ClassicHole) {
    var ball: GolfPoint = hole.tee
        private set
    var strokes: Int = 0
        private set
    var club: GolfClub = GolfClub.DRIVER
    /** Contact point on the ball: +x curves right, +y is struck high (lower flight, half the backspin, longer run). */
    var spinX: Float = 0f
        private set
    var spinY: Float = 0f
        private set
    var aimAngle: Float = 0f
        set(value) { field = if (value.isFinite()) (value % (2f * PI.toFloat())) else 0f }
    var state: GolfState = GolfState.READY
        private set
    private val windSpeed = run {
        val seed = sin(hole.number * 12.9898f) * 43758.547f
        .6f + 3.4f * (seed - floor(seed))
    }
    var windX: Float = sin(hole.number * 2.4f) * windSpeed
    var windZ: Float = cos(hole.number * 2.4f) * windSpeed
    var lastPenalty: Int = 0
        private set
    val lie: GolfLie get() = hole.lieAt(ball.x, ball.z)
    val distanceToCup: Float get() = hypot(hole.cup.x - ball.x, hole.cup.z - ball.z)
    val velocity: GolfPoint get() = GolfPoint(b.vx, b.vy, b.vz)
    // An interrupted shot is counted, but restores a safe position; reopening cannot erase a stroke.
    val saveBall: GolfPoint get() = if (state == GolfState.FLYING || state == GolfState.ROLLING) shotStart else ball
    val saveStrokes: Int get() = strokes
    private var shotStart = ball
    private val b = BallState()
    private val sim = BallState()
    private val cup = GolfCup(hole)
    /** The turf no longer carries the ball: it is over the opening or inside the cup. */
    private var inCup = false
    /** As on a real course, the flagstick is taken out for putts and left in for every other shot. */
    private var flagIn = true
    /** The ball has touched the ground since the strike: later contacts are hops, not the landing. */
    private var landed = false
    private var accumulator = 0.0
    private var shotSeconds = 0f
    private var previewKey: List<Any>? = null
    private var previewValue = ShotPreview.NONE
    private val treeGround by lazy { hole.trees.map { hole.heightAt(it.x, it.z) } }

    init { prepareNextShot() }

    fun setSpin(x: Float, y: Float) {
        val sx = if (x.isFinite()) x else 0f
        val sy = if (y.isFinite()) y else 0f
        val length = hypot(sx, sy)
        val scale = if (length > 1f) 1f / length else 1f
        spinX = sx * scale; spinY = sy * scale
    }

    fun reset() {
        ball = hole.tee
        strokes = 0
        lastPenalty = 0
        stop(GolfState.READY)
        shotStart = ball
        prepareNextShot()
    }

    fun restore(position: GolfPoint, count: Int) {
        val safe = position.x.isFinite() && position.z.isFinite() &&
            hole.lieAt(position.x, position.z) !in listOf(GolfLie.WATER, GolfLie.OUT)
        ball = if (safe) GolfPoint(position.x, hole.heightAt(position.x, position.z) + RADIUS, position.z) else hole.tee
        strokes = count.coerceIn(0, 999)
        shotStart = ball
        stop(GolfState.READY)
        prepareNextShot()
    }

    /** [power] is the fraction of the club's carry; [error] is the needle position at release. */
    fun hit(power: Float, error: Float = 0f): Boolean {
        if (state != GolfState.READY) return false
        val p = if (power.isFinite()) power.coerceIn(0f, 1f) else 0f
        if (p <= 0f) return false
        shotStart = ball
        strokes++
        shotSeconds = 0f
        accumulator = 0.0
        val miss = GolfSwing.deviation(error)
        flagIn = club != GolfClub.PUTTER
        landed = false
        if (club == GolfClub.PUTTER) {
            val speed = puttSpeed(p) * (1f - .06f * miss * miss)
            val heading = aimAngle - miss * .03f
            b.px = ball.x; b.py = ball.y; b.pz = ball.z
            b.stopMotion()
            b.vx = sin(heading) * speed; b.vz = cos(heading) * speed
            state = GolfState.ROLLING
        } else {
            launch(b, p, miss)
            state = GolfState.FLYING
        }
        return true
    }

    fun update(dt: Float) {
        if (state != GolfState.FLYING && state != GolfState.ROLLING) return
        if (!dt.isFinite() || dt <= 0f) return
        // Ignore suspend gaps; regular frame grouping still produces exactly the same integration.
        accumulator += dt.coerceAtMost(.25f).toDouble()
        while (accumulator + 1e-9 >= STEP && (state == GolfState.FLYING || state == GolfState.ROLLING)) {
            accumulator -= STEP
            step(STEP.toFloat())
        }
    }

    /** Nominal guide for [power]: flight to the first landing, then the flat-turf run. */
    fun preview(power: Float): ShotPreview {
        if (state != GolfState.READY) return ShotPreview.NONE
        val p = if (power.isFinite()) power.coerceIn(0f, 1f) else 0f
        val key = listOf(ball, club, p, aimAngle, spinX, spinY)
        if (key == previewKey) return previewValue
        previewKey = key
        previewValue = if (club == GolfClub.PUTTER) puttPreview(p) else flightPreview(p)
        return previewValue
    }

    /** The putt rolls on the real slopes: the line bends and stops where the ball would. */
    private fun puttPreview(power: Float): ShotPreview {
        val speed = puttSpeed(power)
        sim.px = ball.x; sim.py = ball.y; sim.pz = ball.z
        sim.stopMotion()
        sim.vx = sin(aimAngle) * speed; sim.vz = cos(aimAngle) * speed
        val (points, hazard) = runOut(Contact.ROLL)
        return ShotPreview(points, points.last(), emptyList(), hazard, power * club.carry)
    }

    private fun flightPreview(power: Float): ShotPreview {
        launch(sim, power, 0f)
        val points = ArrayList<GolfPoint>(64)
        points.add(ball)
        var landing: GolfPoint? = null
        var contact = Contact.HOP
        var steps = 0
        while (steps++ < 20 * 120) {
            val fromX = sim.px; val fromY = sim.py; val fromZ = sim.pz
            GolfBallPhysics.flightStep(sim, 0f, 0f)
            val floor = hole.heightAt(sim.px, sim.pz) + RADIUS
            if (sim.py <= floor && sim.vy < 0f) {
                contact = simContact(fromX, fromY, fromZ, true)
                landing = GolfPoint(sim.px, hole.heightAt(sim.px, sim.pz) + RADIUS, sim.pz)
                break
            }
            if (steps % 4 == 0) points.add(GolfPoint(sim.px, sim.py, sim.pz))
        }
        val end = landing ?: GolfPoint(sim.px, hole.heightAt(sim.px, sim.pz) + RADIUS, sim.pz)
        points.add(end)
        if (landing == null) return ShotPreview(points, end, emptyList(), isHazard(end.x, end.z), hypot(end.x - ball.x, end.z - ball.z))
        // After the landing, the same hops and the same slopes as the real ball (no wind, no trees).
        val (run, hazard) = runOut(contact)
        val roll = if (contact == Contact.LOST) emptyList() else run
        return ShotPreview(points, end, roll, hazard, hypot(end.x - ball.x, end.z - ball.z))
    }

    private enum class Contact { HOP, ROLL, LOST, CUP }

    /** [touchDown] for the guide ball: where the [sim] ball meets the ground, and what it does next. */
    private fun simContact(fromX: Float, fromY: Float, fromZ: Float, landing: Boolean): Contact {
        val t = contactFraction(sim, fromX, fromY, fromZ)
        sim.px = fromX + (sim.px - fromX) * t
        sim.pz = fromZ + (sim.pz - fromZ) * t
        sim.py = hole.heightAt(sim.px, sim.pz) + RADIUS
        val gx = gradientX(sim.px, sim.pz); val gz = gradientZ(sim.px, sim.pz)
        val length = sqrt(1f + gx * gx + gz * gz)
        val nx = -gx / length; val ny = 1f / length; val nz = -gz / length
        if (sim.vx * nx + sim.vy * ny + sim.vz * nz >= 0f) return Contact.HOP
        val surface = hole.lieAt(sim.px, sim.pz)
        if (surface == GolfLie.WATER || surface == GolfLie.OUT) return Contact.LOST
        if (cup.over(sim.px, sim.pz)) return Contact.CUP
        GolfBallPhysics.bounce(sim, nx, ny, nz, surface, landing)
        if (sim.vx * nx + sim.vy * ny + sim.vz * nz < GolfBallPhysics.ROLL_THRESHOLD) {
            GolfBallPhysics.startRolling(sim, nx, ny, nz, surface)
            return Contact.ROLL
        }
        return Contact.HOP
    }

    /**
     * Carries the [sim] ball on from [start] as [step] would (hops, then rolling with slope and turf
     * drag) until it rests, falls in the cup or is lost. Returns the path on the ground and whether it
     * ends in a hazard.
     */
    private fun runOut(start: Contact): Pair<List<GolfPoint>, Boolean> {
        val points = ArrayList<GolfPoint>(48)
        fun mark() { points.add(GolfPoint(sim.px, hole.heightAt(sim.px, sim.pz) + RADIUS, sim.pz)) }
        mark()
        if (start == Contact.LOST) return points to true
        if (start == Contact.CUP) return points to false
        var rolling = start == Contact.ROLL
        var steps = 0
        while (steps++ < 20 * 120) {
            val fromX = sim.px; val fromY = sim.py; val fromZ = sim.pz
            if (!rolling) {
                GolfBallPhysics.flightStep(sim, 0f, 0f)
                if (sim.py <= hole.heightAt(sim.px, sim.pz) + RADIUS && sim.vy < 0f) {
                    when (simContact(fromX, fromY, fromZ, false)) {
                        Contact.LOST -> { mark(); return points to true }
                        Contact.CUP -> { mark(); return points to false }
                        Contact.ROLL -> rolling = true
                        Contact.HOP -> Unit
                    }
                }
            } else {
                GolfBallPhysics.rollingStep(sim, gradientX(sim.px, sim.pz), gradientZ(sim.px, sim.pz), hole.lieAt(sim.px, sim.pz))
                val entry = cup.entry(fromX, fromZ, sim.px, sim.pz)
                if (entry >= 0f) {
                    sim.px = fromX + (sim.px - fromX) * entry; sim.pz = fromZ + (sim.pz - fromZ) * entry
                    mark(); return points to false
                }
                sim.py = hole.heightAt(sim.px, sim.pz) + RADIUS; sim.vy = 0f
                if (isHazard(sim.px, sim.pz)) { mark(); return points to true }
                if (hypot(sim.vx, sim.vz) < .03f && GolfBallPhysics.canRest(gradientX(sim.px, sim.pz), gradientZ(sim.px, sim.pz), hole.lieAt(sim.px, sim.pz))) break
            }
            if (steps % 6 == 0) mark()
        }
        mark()
        return points to false
    }

    private fun step(dt: Float) {
        shotSeconds += dt
        val fromX = b.px; val fromY = b.py; val fromZ = b.pz
        if (state == GolfState.FLYING) {
            GolfBallPhysics.flightStep(b, windX, windZ, dt)
            hitFlagstick()
            if (!collideWithTrees(fromX, fromY, fromZ)) {
                val floor = hole.heightAt(b.px, b.pz) + RADIUS
                if (b.py <= floor) touchDown(fromX, fromY, fromZ) else ball = GolfPoint(b.px, b.py, b.pz)
            } else ball = GolfPoint(b.px, b.py, b.pz)
            if (state != GolfState.FLYING && state != GolfState.ROLLING) return
        } else if (inCup) {
            val result = cup.step(b, dt, flagIn)
            ball = GolfPoint(b.px, b.py, b.pz)
            when (result) {
                GolfCup.Result.SETTLED -> { inCup = false; stop(GolfState.HOLED); return }
                GolfCup.Result.ROLLING -> { inCup = false; b.vy = 0f }
                GolfCup.Result.AIRBORNE -> { inCup = false; state = GolfState.FLYING }
                GolfCup.Result.INSIDE -> Unit
            }
        } else {
            val surface = hole.lieAt(b.px, b.pz)
            if (surface == GolfLie.WATER || surface == GolfLie.OUT) { penalty(); return }
            GolfBallPhysics.rollingStep(b, gradientX(b.px, b.pz), gradientZ(b.px, b.pz), surface, dt)
            // Swept test: the moment the centre passes over the opening, the turf stops carrying it.
            val entry = cup.entry(fromX, fromZ, b.px, b.pz)
            if (entry >= 0f) {
                b.px = fromX + (b.px - fromX) * entry; b.pz = fromZ + (b.pz - fromZ) * entry
                b.py = hole.heightAt(b.px, b.pz) + RADIUS; b.vy = 0f
                ball = GolfPoint(b.px, b.py, b.pz)
                enterCup()
                return
            }
            b.py = hole.heightAt(b.px, b.pz) + RADIUS
            collideWithTrees(fromX, fromY, fromZ)
            b.vy = 0f
            ball = GolfPoint(b.px, b.py, b.pz)
            if (isHazard(b.px, b.pz)) { penalty(); return }
            val speed = hypot(b.vx, b.vz)
            if (speed < .03f && GolfBallPhysics.canRest(gradientX(b.px, b.pz), gradientZ(b.px, b.pz), hole.lieAt(b.px, b.pz))) {
                stop(GolfState.READY); prepareNextShot(); return
            }
        }
        if (shotSeconds > 30f) {
            if (isHazard(ball.x, ball.z)) penalty()
            else { ball = ball.copy(y = hole.heightAt(ball.x, ball.z) + RADIUS); stop(GolfState.READY); prepareNextShot() }
        }
    }

    /** The flying ball reached the ground between the previous and the current step. */
    private fun touchDown(fromX: Float, fromY: Float, fromZ: Float) {
        val t = contactFraction(b, fromX, fromY, fromZ)
        b.px = fromX + (b.px - fromX) * t
        b.pz = fromZ + (b.pz - fromZ) * t
        b.py = hole.heightAt(b.px, b.pz) + RADIUS
        ball = GolfPoint(b.px, b.py, b.pz)
        val gx = gradientX(b.px, b.pz); val gz = gradientZ(b.px, b.pz)
        val length = sqrt(1f + gx * gx + gz * gz)
        val nx = -gx / length; val ny = 1f / length; val nz = -gz / length
        if (b.vx * nx + b.vy * ny + b.vz * nz >= 0f) return
        val landing = !landed
        landed = true
        val surface = hole.lieAt(b.px, b.pz)
        if (surface == GolfLie.WATER || surface == GolfLie.OUT) { penalty(); return }
        // Landing over the opening: nothing to bounce on, the ball goes on into the cup.
        if (cup.over(b.px, b.pz)) { state = GolfState.ROLLING; enterCup(); return }
        GolfBallPhysics.bounce(b, nx, ny, nz, surface, landing)
        if (b.vx * nx + b.vy * ny + b.vz * nz < GolfBallPhysics.ROLL_THRESHOLD) {
            GolfBallPhysics.startRolling(b, nx, ny, nz, surface)
            state = GolfState.ROLLING
        }
    }

    /** Fraction of the last step at which the ball met the terrain (linear in height above it). */
    private fun contactFraction(ball: BallState, fromX: Float, fromY: Float, fromZ: Float): Float {
        val before = fromY - (hole.heightAt(fromX, fromZ) + RADIUS)
        val after = ball.py - (hole.heightAt(ball.px, ball.pz) + RADIUS)
        return if (before <= 0f) 0f else (before / (before - after).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    }

    private fun gradientX(x: Float, z: Float) = (hole.heightAt(x + .15f, z) - hole.heightAt(x - .15f, z)) / .3f
    private fun gradientZ(x: Float, z: Float) = (hole.heightAt(x, z + .15f) - hole.heightAt(x, z - .15f)) / .3f
    private fun isHazard(x: Float, z: Float) = hole.lieAt(x, z).let { it == GolfLie.WATER || it == GolfLie.OUT }

    private fun collideWithTrees(fromX: Float, fromY: Float, fromZ: Float): Boolean {
        var first: GolfTreeCollision.Contact? = null
        val from = GolfPoint(fromX, fromY, fromZ)
        val to = GolfPoint(b.px, b.py, b.pz)
        val movementVelocity = GolfPoint(b.vx, b.vy, b.vz)
        for (i in hole.trees.indices) {
            val contact = GolfTreeCollision.collide(hole.trees[i], treeGround[i], from, to, movementVelocity) ?: continue
            if (contact.time < (first?.time ?: 2f)) first = contact
        }
        val contact = first ?: return false
        b.px = contact.point.x; b.py = contact.point.y; b.pz = contact.point.z
        b.vx = contact.velocity.x; b.vy = contact.velocity.y; b.vz = contact.velocity.z
        b.wx *= .3f; b.wy *= .3f; b.wz *= .3f
        return true
    }

    private fun enterCup() { inCup = true; cup.reset() }

    /** A flying ball that meets the flagstick loses most of its pace and often drops. */
    private fun hitFlagstick() {
        if (!flagIn) return
        val dx = b.px - hole.cup.x; val dz = b.pz - hole.cup.z
        val d = hypot(dx, dz)
        val reach = ClassicHole.PIN_RADIUS + RADIUS
        if (d >= reach || d < 1e-6f || b.py < hole.cup.y + RADIUS || b.py > hole.cup.y + ClassicHole.PIN_HEIGHT) return
        val ux = dx / d; val uz = dz / d
        b.px = hole.cup.x + ux * reach; b.pz = hole.cup.z + uz * reach
        val radial = b.vx * ux + b.vz * uz
        // A flagstick soaks up nearly all of the pace.
        if (radial < 0f) { b.vx -= 1.1f * radial * ux; b.vz -= 1.1f * radial * uz }
        b.vx *= .4f; b.vz *= .4f
        b.wx *= .3f; b.wy *= .3f; b.wz *= .3f
    }

    /** Test hook: the ball leaves [position] in free flight at [velocity], without spin. */
    internal fun throwBall(position: GolfPoint, velocity: GolfPoint) {
        ball = position; shotStart = position
        b.px = position.x; b.py = position.y; b.pz = position.z
        b.stopMotion(); b.vx = velocity.x; b.vy = velocity.y; b.vz = velocity.z
        inCup = false; flagIn = true; landed = false; accumulator = 0.0; shotSeconds = 0f
        state = GolfState.FLYING
    }

    private fun penalty() {
        strokes++
        lastPenalty++
        ball = shotStart
        stop(GolfState.READY)
        prepareNextShot()
    }

    private fun stop(next: GolfState) {
        state = next; b.stopMotion(); accumulator = 0.0; inCup = false
        b.px = ball.x; b.py = ball.y; b.pz = ball.z
    }

    /** Caddie: aims at the safe landing area or the flag and hands over the matching club. */
    private fun prepareNextShot() {
        val target = hole.recommendedLanding(ball)
        aimAngle = atan2(target.x - ball.x, target.z - ball.z)
        club = suggestedClub(hypot(target.x - ball.x, target.z - ball.z))
        spinX = 0f; spinY = 0f
    }

    private fun suggestedClub(distance: Float): GolfClub {
        val lie = lie
        if (lie == GolfLie.GREEN) return GolfClub.PUTTER
        // The driver needs a tee; long shots from the grass start with the 3 wood.
        val bag = GolfClub.entries.filter { it != GolfClub.PUTTER && (it != GolfClub.DRIVER || lie == GolfLie.TEE) }
        return bag.lastOrNull { it.carry * lieEfficiency(lie, it) >= distance } ?: bag.first()
    }

    private fun puttSpeed(power: Float): Float =
        sqrt(2f * GolfBallPhysics.rolling(GolfLie.GREEN) * power * GolfClub.PUTTER.carry)

    private fun launch(into: BallState, power: Float, miss: Float) {
        val lie = lie
        val swing = GolfCalibration.swing(club, power)
        val speed = swing * GolfCalibration.fullSpeed(club) * lieEfficiency(lie, club) * (1f - .1f * miss * miss)
        val spin = club.spinRpm * swing * (1f - .5f * spinY) * when (lie) {
            GolfLie.ROUGH -> .55f; GolfLie.SEMI_ROUGH -> .8f; GolfLie.BUNKER -> .7f; else -> 1f
        }
        GolfBallPhysics.launch(into, ball.x, ball.y, ball.z, speed, GolfCalibration.launch(club, swing) - 1.5f * spinY, spin,
            aimAngle - miss * .035f, spinX * 15f + miss * 24f)
    }

    companion object {
        private const val RADIUS = ClassicHole.BALL_RADIUS
        private const val STEP = 1.0 / 120.0
        private fun lieEfficiency(lie: GolfLie, club: GolfClub): Float = when {
            club == GolfClub.PUTTER -> 1f
            lie == GolfLie.ROUGH -> if (club.ordinal < GolfClub.IRON5.ordinal) .79f else .89f
            lie == GolfLie.SEMI_ROUGH -> if (club.ordinal < GolfClub.IRON5.ordinal) .92f else .96f
            lie == GolfLie.BUNKER -> if (club.loft >= 49f) .82f else .5f
            else -> 1f
        }
    }
}
