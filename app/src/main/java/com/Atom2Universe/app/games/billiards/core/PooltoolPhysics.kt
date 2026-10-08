/*
 * Adapted from Pooltool, Evan Kiefl and contributors, Apache-2.0.
 * Revision 81040e931facada508bbab2e53ab820295bbd90e.
 * See assets/billiards/NOTICE.txt and POOLTOOL-LICENSE.txt.
 * Ported models: physics/evolve, stick_ball/instantaneous_point and squirt,
 * ball_ball/frictional_inelastic, ball_cushion/han_2005.
 */
package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

object PooltoolPhysics {
    const val G = 9.81

    fun transitionTime(b: Ball, c: Cloth): Double = when (b.motion) {
        Motion.SLIDING -> 2 * b.slip().planar().length() / (7 * c.sliding * G)
        Motion.ROLLING -> b.v.planar().length() / (c.rolling * G)
        Motion.SPINNING -> abs(b.w.z) * 2 * b.radius / (5 * c.spin * G)
        else -> Double.POSITIVE_INFINITY
    }

    /** Exact piecewise equations; one call may cross multiple motion transitions. */
    fun evolve(b: Ball, cloth: Cloth, seconds: Double, trackVisualRotation: Boolean = true) {
        var left = seconds
        var guard = 0
        while (left > 1e-12 && guard++ < 8) {
            if (b.motion == Motion.STILL || b.motion == Motion.POCKETED) return
            val oldW = b.w
            if (b.motion == Motion.AIRBORNE) {
                b.p += b.v * left - V3.UP * (.5 * G * left * left)
                b.v -= V3.UP * (G * left)
                if(trackVisualRotation) b.rotateVisual(left, oldW)
                return
            }
            val transition = transitionTime(b, cloth)
            val dt = min(left, transition)
            val wz = decaySpin(b.w.z, b.radius, cloth.spin, dt)
            when (b.motion) {
                Motion.SLIDING -> {
                    val u = b.slip().planar().unit()
                    val acc = u * (-cloth.sliding * G)
                    b.p += b.v * dt + acc * (.5 * dt * dt)
                    b.v += acc * dt
                    b.w += V3.UP.cross(u) * (2.5 * cloth.sliding * G * dt / b.radius)
                    b.w = b.w.copy(z = wz)
                }
                Motion.ROLLING -> {
                    val acc = b.v.planar().unit() * (-cloth.rolling * G)
                    b.p += b.v * dt + acc * (.5 * dt * dt)
                    b.v += acc * dt
                    b.w = V3(-b.v.y / b.radius, b.v.x / b.radius, wz)
                }
                Motion.SPINNING -> b.w = V3(z = wz)
                else -> Unit
            }
            if(trackVisualRotation) b.rotateVisual(dt, (oldW + b.w) * .5)
            left -= dt
            if (dt >= transition - 1e-12) {
                when (b.motion) {
                    Motion.SLIDING -> { b.w = V3(-b.v.y/b.radius, b.v.x/b.radius, b.w.z); b.motion = Motion.ROLLING }
                    Motion.ROLLING -> {
                        b.v = V3.ZERO; b.w = V3(z = b.w.z)
                        // Without side spin there is no spinning phase. A zero-length one
                        // never ends in the headless tail, which then advances by zero.
                        if (abs(b.w.z) > 1e-7) b.motion = Motion.SPINNING else b.stop()
                    }
                    Motion.SPINNING -> b.stop()
                    else -> Unit
                }
            }
        }
    }

    private fun decaySpin(w: Double, r: Double, mu: Double, dt: Double): Double =
        sign(w) * max(0.0, abs(w) - 2.5 * mu * G * dt / r)

    /** Pooltool instantaneous point contact with finite tip offsets and squirt. */
    fun strike(b: Ball, shot: Shot) {
        require(shot.speed.isFinite() && shot.angle.isFinite())
        val norm = hypot(shot.side, shot.top)
        val scale = if (norm > .80) .80 / norm else 1.0
        val a = shot.side * scale
        val top = shot.top * scale
        val theta = shot.elevation.coerceIn(0.0, 80.0) * PI / 180
        val cueC = sqrt(1 - a*a - top*top)
        val c = (cos(theta)*cueC - sin(theta)*top) * b.radius
        val z = (sin(theta)*cueC + cos(theta)*top) * b.radius
        val x = a * b.radius
        val inertiaM = .4 * b.radius * b.radius
        val temp = x*x + (z*cos(theta)).pow(2) + (c*sin(theta)).pow(2) - 2*z*c*cos(theta)*sin(theta)
        val speed = 2 * shot.speed.coerceIn(.05, 8.0) / (1 + b.mass/shot.cueMass + temp/inertiaM)
        val rot = shot.angle + PI/2
        b.v = V3(0.0, -speed*cos(theta), -speed*sin(theta)).rotate(rot)
        b.w = V3(-c*sin(theta)+z*cos(theta), x*sin(theta), -x*cos(theta)).rotate(rot) * (speed/inertiaM)
        val aa = 1-a*a
        val squirt = -atan2(2.5*a*sqrt(aa), 1+b.mass/shot.endMass+2.5*aa)
        b.v = b.v.rotate(squirt)
        // Strike starts on the slate: resolve the downward impulse before free flight.
        if (b.v.z < 0) land(b, .35)
        b.classify()
    }

    /** Vector form of Pooltool's equal-mass frictional inelastic model. */
    fun collide(a: Ball, b: Ball, restitution: Double): Double {
        val n = (b.p - a.p).unit()
        val approach = (a.v - b.v).dot(n)
        if (approach <= 1e-9) return 0.0
        val normalDelta = (1 + restitution) * approach / 2
        val rel = a.v - b.v + (a.w + b.w).cross(n) * a.radius
        val tangent = rel - n * rel.dot(n)
        val mu = .009951 + .108 * exp(-1.088 * tangent.length())
        // Pooltool uses the final relative normal speed (e * incoming speed)
        // for its slip bound; retain that convention in the Kotlin port.
        val delta = tangent.unit() * -min(tangent.length()/7, mu * restitution*approach)
        a.v = a.v - n*normalDelta + delta
        b.v = b.v + n*normalDelta - delta
        val angular = n.cross(delta) * (2.5/a.radius)
        a.w += angular; b.w += angular
        a.classify(); b.classify()
        return approach
    }

    /** Han 2005: normal points out of the table, cushion nose at 1.28R. */
    fun cushion(b: Ball, normal: V3, cloth: Cloth): Double {
        val phi = atan2(normal.y, normal.x)
        val v = b.v.rotate(-phi); val w = b.w.rotate(-phi)
        if (v.x <= 1e-9) return 0.0
        if (b.motion == Motion.AIRBORNE) {
            val normalDelta = normal * ((1 + cloth.cushionRestitution)*b.v.dot(normal))
            b.v -= normalDelta
            return v.x
        }
        val s = .28; val c = sqrt(1 - s*s); val r = b.radius; val m = b.mass
        val sx = v.x*s + r*w.y
        val sy = -v.y - r*w.z*c + r*w.x*s
        val pz = (1 + cloth.cushionRestitution)*v.x*c*m
        val speed = hypot(sx, sy)
        val factor = if (speed < 1e-12) 0.0 else min(m/3.5, cloth.cushionFriction*pz/speed)
        val px = sx*factor; val py = sy*factor
        val ix = -px*s-pz*c; val iz = px*c-pz*s
        b.v = V3(v.x+ix/m, v.y+py/m).rotate(phi)
        val invI = 1/(.4*m*r*r)
        b.w = V3(w.x-r*invI*py*s, w.y+r*invI*(ix*s-iz*c), w.z+r*invI*py*c).rotate(phi)
        b.classify()
        return v.x
    }

    /** Dissipative slate impulse, bounded Coulomb friction; also handles jumps. */
    fun land(b: Ball, restitution: Double = .12) {
        b.p = b.p.copy(z = b.radius)
        val impact = abs(b.v.z)
        val slip = b.slip().planar()
        val dv = slip.unit() * -min(slip.length()/3.5, .20*(1+restitution)*impact)
        b.v = (b.v + dv).copy(z = if (impact > .15) impact*restitution else 0.0)
        b.w += V3(z = -b.radius).cross(dv) * (2.5/(b.radius*b.radius))
        b.classify()
    }
}
