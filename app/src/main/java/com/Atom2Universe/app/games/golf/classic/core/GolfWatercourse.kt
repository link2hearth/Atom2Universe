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

    fun signedDistance(x:Float,z:Float):Float {
        val outside=max(abs(x)-halfWidth,abs(z)-halfDepth)
        // Nothing beyond the 12 m bank can affect grading, lies or shore rendering.
        if(outside>12f) return outside
        var best=Float.POSITIVE_INFINITY
        for(i in 0 until samples.lastIndex) {
            val a=samples[i]; val b=samples[i+1]
            val r=max(a.radius,b.radius)
            if(max(max(min(a.x,b.x)-x,x-max(a.x,b.x)),max(min(a.z,b.z)-z,z-max(a.z,b.z)))-r>best) continue
            val dx=b.x-a.x; val dz=b.z-a.z
            val t=(((x-a.x)*dx+(z-a.z)*dz)/(dx*dx+dz*dz)).coerceIn(0f,1f)
            best=min(best,hypot(x-a.x-dx*t,z-a.z-dz*t)-(a.radius+(b.radius-a.radius)*t))
        }
        return best
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
