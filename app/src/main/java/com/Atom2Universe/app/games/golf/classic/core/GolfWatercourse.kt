package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** A lake follows a curved spine with changing width. The spine may double back, making
 * inlets, peninsulas and S bends that cannot be represented by a radial oval contour.
 */
data class GolfWaterNode(val x: Float, val z: Float, val radius: Float)

class GolfWatercourse(val nodes: List<GolfWaterNode>) {
    init {
        require(nodes.size >= 2 && nodes.all { it.x.isFinite() && it.z.isFinite() && it.radius > 0f && it.radius.isFinite() })
        require(nodes.zipWithNext().all { (a,b) -> hypot(a.x-b.x,a.z-b.z) > .01f })
    }

    // Sample once, not during collision or terrain meshing. Hermite tangents use chord
    // lengths so a short bend cannot overshoot because of the preceding long reach.
    val samples: List<GolfWaterNode> = buildList {
        fun tangent(i: Int): Pair<Float,Float> {
            val a=nodes[max(0,i-1)]; val b=nodes[min(nodes.lastIndex,i+1)]
            val length=hypot(b.x-a.x,b.z-a.z).coerceAtLeast(.01f)
            return (b.x-a.x)/length to (b.z-a.z)/length
        }
        for (i in 0 until nodes.lastIndex) {
            val a=nodes[i]; val b=nodes[i+1]
            val span=hypot(b.x-a.x,b.z-a.z)
            val (ax,az)=tangent(i); val (bx,bz)=tangent(i+1)
            val steps=ceil(span/2.5f).toInt().coerceAtLeast(4)
            for (j in 0 until steps) {
                val t=j.toFloat()/steps; val t2=t*t; val t3=t2*t
                fun curve(a:Float,b:Float,ta:Float,tb:Float)=
                    (2*t3-3*t2+1)*a+(t3-2*t2+t)*span*ta+(-2*t3+3*t2)*b+(t3-t2)*span*tb
                add(GolfWaterNode(curve(a.x,b.x,ax,bx),curve(a.z,b.z,az,bz),
                    a.radius+(b.radius-a.radius)*t2*(3-2*t)))
            }
        }
        add(nodes.last())
    }
    val halfWidth = samples.maxOf { abs(it.x)+it.radius }
    val halfDepth = samples.maxOf { abs(it.z)+it.radius }

    private class Segment(val a: GolfWaterNode, val b: GolfWaterNode) {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val lengthSquared = dx * dx + dz * dz
        val radiusDelta = b.radius - a.radius
        val left = min(a.x - a.radius, b.x - b.radius)
        val right = max(a.x + a.radius, b.x + b.radius)
        val front = min(a.z - a.radius, b.z - b.radius)
        val back = max(a.z + a.radius, b.z + b.radius)

        fun distance(x: Float, z: Float): Float {
            val t = (((x - a.x) * dx + (z - a.z) * dz) / lengthSquared).coerceIn(0f, 1f)
            return hypot(x - a.x - dx * t, z - a.z - dz * t) - (a.radius + radiusDelta * t)
        }
    }

    /** Immutable spatial tree: exact shore queries visit nearby reaches, not the entire lake.
     * Bounds include the varying radius; their signed L-infinity distance is a lower bound
     * even inside water, so pruning preserves the original projected-segment calculation.
     */
    private class Branch(segments: List<Segment>) {
        val left = segments.minOf { it.left }
        val right = segments.maxOf { it.right }
        val front = segments.minOf { it.front }
        val back = segments.maxOf { it.back }
        val leaf: Array<Segment>?
        val first: Branch?
        val second: Branch?
        init {
            if (segments.size <= 4) {
                leaf = segments.toTypedArray(); first = null; second = null
            } else {
                val ordered = if (right - left > back - front) segments.sortedBy { it.a.x + it.b.x }
                    else segments.sortedBy { it.a.z + it.b.z }
                val middle = ordered.size / 2
                leaf = null
                first = Branch(ordered.subList(0, middle))
                second = Branch(ordered.subList(middle, ordered.size))
            }
        }

        fun lowerBound(x: Float, z: Float) = max(max(left - x, x - right), max(front - z, z - back))

        fun nearest(x: Float, z: Float, limit: Float): Float {
            var best = limit
            val segments = leaf
            if (segments != null) {
                for (segment in segments) best = min(best, segment.distance(x, z))
            } else {
                val a = first!!; val b = second!!
                val da = a.lowerBound(x, z); val db = b.lowerBound(x, z)
                if (da <= db) {
                    if (da <= best) best = a.nearest(x, z, best)
                    if (db <= best) best = b.nearest(x, z, best)
                } else {
                    if (db <= best) best = b.nearest(x, z, best)
                    if (da <= best) best = a.nearest(x, z, best)
                }
            }
            return best
        }
    }

    private val index = Branch(samples.zipWithNext { a, b -> Segment(a, b) })

    fun signedDistance(x:Float,z:Float):Float {
        val outside=max(abs(x)-halfWidth,abs(z)-halfDepth)
        // Nothing beyond the 12 m bank can affect grading, lies or shore rendering.
        if(outside>12f) return outside
        return index.nearest(x, z, Float.POSITIVE_INFINITY)
    }

    companion object {
        /** World-space design knots; the first knot anchors the local coordinate system. */
        fun lake(vararg nodes: GolfWaterNode): GolfHazard {
            val origin=nodes.first()
            val course=GolfWatercourse(nodes.map { it.copy(x=it.x-origin.x,z=it.z-origin.z) })
            return GolfHazard(origin.x,origin.z,course.halfWidth,course.halfDepth,GolfLie.WATER,
                shape=0f,watercourse=course)
        }

        /** Compact lakes retain their existing envelope, but gain two genuinely opposing bends. */
        fun meander(x:Float,z:Float,rx:Float,rz:Float,rotation:Float):GolfHazard {
            val long=max(rx,rz); val short=min(rx,rz)
            fun point(lateral:Float,along:Float,radius:Float):GolfWaterNode {
                val u=if(rz>=rx) lateral*short else along*long
                val v=if(rz>=rx) along*long else lateral*short
                return GolfWaterNode(x+u*cos(rotation)-v*sin(rotation),z+u*sin(rotation)+v*cos(rotation),short*radius)
            }
            val spine=listOf(point(.10f,-.73f,.32f),point(.43f,-.38f,.48f),point(0f,0f,.68f),
                point(-.43f,.38f,.51f),point(-.10f,.73f,.34f))
            // Retain the original centre as the local origin, independent of the end cap.
            val course=GolfWatercourse(spine.map { it.copy(x=it.x-x,z=it.z-z) })
            return GolfHazard(x,z,course.halfWidth,course.halfDepth,GolfLie.WATER,shape=0f,watercourse=course)
        }
    }
}
