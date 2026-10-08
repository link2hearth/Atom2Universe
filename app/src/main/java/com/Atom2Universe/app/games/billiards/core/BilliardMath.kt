package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

/** SI units; XY is the cloth, Z points up, matching Pooltool. */
data class V3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    operator fun plus(v: V3) = V3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: V3) = V3(x - v.x, y - v.y, z - v.z)
    operator fun unaryMinus() = V3(-x, -y, -z)
    operator fun times(s: Double) = V3(x * s, y * s, z * s)
    operator fun div(s: Double) = times(1.0 / s)
    fun dot(v: V3) = x * v.x + y * v.y + z * v.z
    fun cross(v: V3) = V3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun length() = sqrt(dot(this))
    fun unit() = if (length() > 1e-12) this / length() else ZERO
    fun rotate(a: Double) = V3(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a), z)
    fun planar() = V3(x, y)
    companion object { val ZERO = V3(); val UP = V3(z = 1.0) }
}

enum class Motion { STILL, SLIDING, ROLLING, SPINNING, AIRBORNE, POCKETED }

data class Ball(
    val id: Int, var p: V3, val radius: Double = .028575, val mass: Double = .170,
    var v: V3 = V3.ZERO, var w: V3 = V3.ZERO, var motion: Motion = Motion.STILL,
    /** Unit quaternion, XYZW, used only by rendering. */
    var q: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
) {
    fun copyDeep() = copy(q = q.copyOf())
    fun slip() = v + w.cross(V3(z = -radius))
    fun energy() = .5 * mass * v.dot(v) + .2 * mass * radius * radius * w.dot(w)
    fun stop() { v = V3.ZERO; w = V3.ZERO; if (motion != Motion.POCKETED) motion = Motion.STILL }
    fun classify() {
        motion = when {
            motion == Motion.POCKETED -> Motion.POCKETED
            p.z > radius + 1e-7 || v.z > 1e-6 -> Motion.AIRBORNE
            slip().planar().length() > 1e-7 -> Motion.SLIDING
            v.planar().length() > 1e-7 -> Motion.ROLLING
            abs(w.z) > 1e-7 -> Motion.SPINNING
            else -> Motion.STILL
        }
    }
    fun rotateVisual(dt: Double, angular: V3) {
        val speed = angular.length()
        if (speed < 1e-9) return
        val s = sin(speed * dt / 2) / speed
        val r = angular * s; val c = cos(speed * dt / 2)
        val x = q[0]; val y = q[1]; val z = q[2]; val a = q[3]
        val out = doubleArrayOf(c*x+r.x*a+r.y*z-r.z*y, c*y-r.x*z+r.y*a+r.z*x,
            c*z+r.x*y-r.y*x+r.z*a, c*a-r.x*x-r.y*y-r.z*z)
        val n = sqrt(out.sumOf { it * it }); q = DoubleArray(4) { out[it] / n }
    }
}

data class Cloth(val sliding: Double = .20, val rolling: Double = .010,
                 val spin: Double = .012, val restitution: Double = .95,
                 val cushionRestitution: Double = .85, val cushionFriction: Double = .20)

data class Shot(val angle: Double, val speed: Double = 2.0, val side: Double = 0.0,
                val top: Double = 0.0, val elevation: Double = 0.0,
                val cueMass: Double = .567, val endMass: Double = .012)

/** First root with approaching motion. Stable quadratic evaluation avoids cancellation. */
internal fun impactTime(p: V3, v: V3, radius: Double): Double {
    val c = p.dot(p) - radius * radius
    val b = p.dot(v)
    if (b >= 0.0) return Double.POSITIVE_INFINITY
    if (c <= 1e-12) return 0.0
    val a = v.dot(v); val disc = b * b - a * c
    if (a < 1e-20 || disc < 0.0) return Double.POSITIVE_INFINITY
    return c / (-b + sqrt(disc))
}
