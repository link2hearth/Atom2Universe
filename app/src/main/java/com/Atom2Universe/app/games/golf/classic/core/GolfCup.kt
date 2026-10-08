package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The hole: a course-sized cylinder cut 10 cm into the green, with a sharp lip, a liner wall, a flat
 * bottom and the flagstick on its axis. The turf stops carrying a ball as soon as its centre is over
 * the opening: it pivots on the lip and falls. Too fast, it meets the far lip while still high and
 * hops out; slow enough, it drops, knocks the liner and settles on the bottom.
 */
internal class GolfCup(private val hole: ClassicHole) {
    enum class Result { INSIDE, ROLLING, AIRBORNE, SETTLED }

    private val OPENING = hole.cupRadius
    private val cx = hole.cup.x
    private val cz = hole.cup.z
    private val top = hole.cup.y
    private val bottom = top - ClassicHole.CUP_DEPTH + R
    private var captured = false
    private var inside = 0f

    fun reset() { captured = false; inside = 0f }

    /** First fraction of the move from (ax, az) to (bx, bz) whose centre is over the opening, or -1. */
    fun entry(ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = bx - ax; val dz = bz - az
        val fx = ax - cx; val fz = az - cz
        val a = dx * dx + dz * dz
        val c = fx * fx + fz * fz - OPENING * OPENING
        if (c <= 0f) return 0f
        if (a < 1e-12f) return -1f
        val b = fx * dx + fz * dz
        val discriminant = b * b - a * c
        if (discriminant < 0f) return -1f
        val t = (-b - sqrt(discriminant)) / a
        return if (t in 0f..1f) t else -1f
    }

    /** Is (x, z) over the opening? */
    fun over(x: Float, z: Float) = hypot(x - cx, z - cz) < OPENING

    /** Free flight of a ball near the hole for [dt] seconds, in small sub-steps. */
    fun step(b: BallState, dt: Float, flagIn: Boolean): Result {
        val n = 8
        val h = dt / n
        repeat(n) {
            val result = sub(b, h, flagIn)
            if (result != Result.INSIDE) return result
        }
        return Result.INSIDE
    }

    private fun sub(b: BallState, h: Float, flagIn: Boolean): Result {
        b.vy -= GolfBallPhysics.GRAVITY * h
        b.px += b.vx * h; b.py += b.vy * h; b.pz += b.vz * h
        var dx = b.px - cx; var dz = b.pz - cz
        var d = hypot(dx, dz)
        val ux = if (d > 1e-6f) dx / d else 1f
        val uz = if (d > 1e-6f) dz / d else 0f
        val rimX = cx + ux * OPENING; val rimZ = cz + uz * OPENING
        val rimY = hole.heightAt(rimX, rimZ)

        if (d >= OPENING) {
            // Over the turf: the green carries the ball again, or it is on its way out.
            val ground = hole.heightAt(b.px, b.pz) + R
            if (b.py <= ground) {
                b.py = ground
                if (b.vy < -.35f) { b.vy = -b.vy * .3f; b.vx *= .9f; b.vz *= .9f } else b.vy = 0f
            }
            if (d > OPENING + R + .01f) return if (b.py <= ground + .001f) Result.ROLLING else Result.AIRBORNE
            // Resting on the turf right at the lip, centre off the opening: back to ordinary rolling.
            if (b.py <= ground + .001f && hypot(b.vx, b.vz) < .05f) return Result.ROLLING
        }

        // Flagstick (left in for chips, taken out to putt): it stops a ball still above the lip,
        // which then drops freely. Lower down it is ignored, so a ball never wedges against it.
        if (flagIn && b.py > rimY + R && b.py < top + ClassicHole.PIN_HEIGHT && d < ClassicHole.PIN_RADIUS + R) {
            val ox = if (d > 1e-6f) ux else -b.vx / max(1e-6f, hypot(b.vx, b.vz))
            val oz = if (d > 1e-6f) uz else -b.vz / max(1e-6f, hypot(b.vx, b.vz))
            b.px = cx + ox * (ClassicHole.PIN_RADIUS + R); b.pz = cz + oz * (ClassicHole.PIN_RADIUS + R)
            val radial = b.vx * ox + b.vz * oz
            if (radial < 0f) { b.vx -= 1.1f * radial * ox; b.vz -= 1.1f * radial * oz }
            b.vx *= .6f; b.vz *= .6f
            dx = b.px - cx; dz = b.pz - cz; d = hypot(dx, dz)
        }

        // Sharp lip: a circular edge the ball can pivot on, or strike from inside.
        val ex = b.px - rimX; val ey = b.py - rimY; val ez = b.pz - rimZ
        val gap = sqrt(ex * ex + ey * ey + ez * ez)
        if (gap < R && gap > 1e-6f && d < OPENING + R) {
            val nx = ex / gap; val ny = ey / gap; val nz = ez / gap
            b.px = rimX + nx * R; b.py = rimY + ny * R; b.pz = rimZ + nz * R
            val vn = b.vx * nx + b.vy * ny + b.vz * nz
            if (vn < 0f) {
                // A ball resting on the lip rolls over it; a striking one rebounds softly.
                val restitution = if (-vn < .3f) 0f else LIP_RESTITUTION
                b.vx -= (1f + restitution) * vn * nx; b.vy -= (1f + restitution) * vn * ny; b.vz -= (1f + restitution) * vn * nz
                b.vx *= .985f; b.vy *= .985f; b.vz *= .985f
            }
            dx = b.px - cx; dz = b.pz - cz; d = hypot(dx, dz)
        }

        // Liner wall, below the lip.
        if (b.py < rimY && d > OPENING - R) {
            val wx = if (d > 1e-6f) dx / d else 1f
            val wz = if (d > 1e-6f) dz / d else 0f
            b.px = cx + wx * (OPENING - R); b.pz = cz + wz * (OPENING - R)
            val radial = b.vx * wx + b.vz * wz
            if (radial > 0f) { b.vx -= 1.3f * radial * wx; b.vz -= 1.3f * radial * wz; b.vy *= .9f }
        }

        // Bottom of the cup.
        if (b.py < bottom) {
            b.py = bottom
            if (b.vy < 0f) b.vy = -b.vy * .25f
            b.vx *= .7f; b.vz *= .7f
        }

        if (!captured && b.py + R < rimY - .003f) captured = true
        if (captured) {
            inside += h
            val still = b.py < bottom + .002f && hypot(b.vx, b.vz) < .05f && kotlin.math.abs(b.vy) < .1f
            if (still || inside > .9f) return Result.SETTLED
        }
        return Result.INSIDE
    }

    companion object {
        private const val R = ClassicHole.BALL_RADIUS
        private const val LIP_RESTITUTION = .35f
    }
}
