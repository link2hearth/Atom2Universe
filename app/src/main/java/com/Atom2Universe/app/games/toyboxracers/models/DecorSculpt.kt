package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.*

/** Small, closed faceted surfaces. Each strip has its own collision bounds so holes stay open. */
internal object DecorSculpt {
    fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)
    private fun cross(a: Vec3, b: Vec3) = v(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x)

    fun DecorBuilder.mesh(color: Int, solid: Boolean = true, build: MutableList<Vec3>.() -> Unit) {
        val vertices = mutableListOf<Vec3>().apply(build)
        require(vertices.size % 3 == 0)
        require(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        val clean = vertices.chunked(3).filter {
            val n = cross(it[1]-it[0], it[2]-it[0])
            n.x*n.x+n.y*n.y+n.z*n.z > 1e-12f
        }.flatten()
        if (clean.isEmpty()) return
        val lo = v(clean.minOf { it.x }, clean.minOf { it.y }, clean.minOf { it.z })
        val hi = v(clean.maxOf { it.x }, clean.maxOf { it.y }, clean.maxOf { it.z })
        parts += DecorPart(DecorShape.MESH, (lo.x+hi.x)/2, (lo.y+hi.y)/2, (lo.z+hi.z)/2,
            (hi.x-lo.x).coerceAtLeast(.001f), (hi.y-lo.y).coerceAtLeast(.001f),
            (hi.z-lo.z).coerceAtLeast(.001f), color, solid, clean)
    }
    fun MutableList<Vec3>.quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3) { addAll(listOf(a,b,c,a,c,d)) }

    /** Profile travels from the bottom centre, around the outside, then down the inside. */
    fun DecorBuilder.lathe(x: Float, z: Float, profile: List<Pair<Float, Float>>, color: Int,
                           solid: Boolean = true, segments: Int = 24) {
        for (i in 0 until segments) mesh(color, solid) {
            fun point(j: Int, k: Int): Vec3 {
                val a = k * 2f * PI.toFloat() / segments
                return v(x+profile[j].first*cos(a), profile[j].second, z+profile[j].first*sin(a))
            }
            for (j in 0 until profile.lastIndex)
                quad(point(j,i),point(j+1,i),point(j+1,i+1),point(j,i+1))
        }
    }

    fun DecorBuilder.tube(points: List<Vec3>, radii: List<Float>, color: Int, solid: Boolean = true,
                          sides: Int = 8) {
        require(points.size == radii.size && points.size >= 2)
        require(radii.all { it.isFinite() && it >= 0f })
        // One fixed reference prevents visible frame flips along a curved wire.
        val tangents = points.indices.map { i ->
            (points[min(i+1,points.lastIndex)]-points[max(i-1,0)]).normalized()
        }
        val reference = listOf(v(1f,0f,0f),v(0f,1f,0f),v(0f,0f,1f)).minBy { axis ->
            tangents.maxOf { abs(it.x*axis.x+it.y*axis.y+it.z*axis.z) }
        }
        val rings = points.indices.map { i ->
            val tangent = tangents[i]
            val u = cross(tangent,reference).normalized()
            val w = cross(tangent,u).normalized()
            List(sides) { j ->
                val a = j * 2f * PI.toFloat()/sides
                points[i] + (u*cos(a)+w*sin(a))*radii[i]
            }
        }
        for (i in 0 until points.lastIndex) mesh(color,solid) {
            for (j in 0 until sides) {
                val k = (j+1)%sides
                quad(rings[i][j],rings[i][k],rings[i+1][k],rings[i+1][j])
                if (i == 0) addAll(listOf(points[0],rings[0][k],rings[0][j]))
                if (i == points.lastIndex-1) addAll(listOf(points.last(),rings.last()[j],rings.last()[k]))
            }
        }
    }
    fun DecorBuilder.rod(a: Vec3, b: Vec3, radius: Float, color: Int, solid: Boolean = true) =
        tube(listOf(a,b),listOf(radius,radius),color,solid)

    /** Annular arc in XY, extruded along Z; used for the rind and flesh of a real slice. */
    fun DecorBuilder.slice(inner: Float, outer: Float, depth: Float, color: Int) {
        val count = 24
        fun p(r: Float, i: Int, z: Float): Vec3 {
            val a = PI.toFloat() + i * PI.toFloat()/count
            return v(r*cos(a), 2.7f+r*sin(a), z)
        }
        for (i in 0 until count) mesh(color) {
            val a=p(inner,i,depth/2); val b=p(outer,i,depth/2)
            val c=p(outer,i+1,depth/2); val d=p(inner,i+1,depth/2)
            val e=p(inner,i,-depth/2); val f=p(outer,i,-depth/2)
            val g=p(outer,i+1,-depth/2); val h=p(inner,i+1,-depth/2)
            quad(a,b,c,d); quad(e,h,g,f); quad(b,f,g,c); quad(a,d,h,e)
            if(i==0) quad(a,e,f,b)
            if(i==count-1) quad(d,c,g,h)
        }
    }
}
