package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.ClassicCourse
import com.Atom2Universe.app.games.golf.classic.render.ClassicBackdrop
import com.Atom2Universe.app.games.golf.classic.render.ClassicLandscape
import com.Atom2Universe.app.games.golf.classic.render.GroundBuilder
import com.Atom2Universe.app.games.golf.classic.render.GroundMesh
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class ClassicBackdropTest {
    @Test fun apronKeepsClosedSeamsWithABoundedTriangleCount() {
        val hole=ClassicCourse.holes[0]
        val bounds=ClassicBackdrop(hole)
        val mesh=ClassicLandscape(hole).terrain()
        val vertices=GroundMesh::class.java.getDeclaredField("vertices").apply { isAccessible=true }.get(mesh) as FloatArray
        val stride=GroundBuilder.STRIDE
        val edges=HashMap<String,Int>()
        var apronTriangles=0
        fun key(i:Int)=(0..2).joinToString(",") { (vertices[i+it]*1000f).roundToInt().toString() }
        fun seam(a:Int,b:Int):Boolean {
            val ax=vertices[a]; val az=vertices[a+2]; val bx=vertices[b]; val bz=vertices[b+2]
            val side=abs(ax-bx)<.0001f && (abs(ax-bounds.left)<.0001f || abs(ax-bounds.right)<.0001f) &&
                az in bounds.front..bounds.back && bz in bounds.front..bounds.back
            val end=abs(az-bz)<.0001f && (abs(az-bounds.front)<.0001f || abs(az-bounds.back)<.0001f) &&
                ax in bounds.left..bounds.right && bx in bounds.left..bounds.right
            return side || end
        }
        for(i in vertices.indices step stride*3) {
            val x=(vertices[i]+vertices[i+stride]+vertices[i+stride*2])/3f
            val z=(vertices[i+2]+vertices[i+stride+2]+vertices[i+stride*2+2])/3f
            if(bounds.outward(x,z)>0f) apronTriangles++
            for(k in 0..2) {
                val a=i+k*stride; val b=i+(k+1)%3*stride
                if(seam(a,b)) {
                    val edge=listOf(key(a),key(b)).sorted().joinToString("/")
                    edges[edge]=(edges[edge] ?: 0)+1
                }
            }
        }
        assertTrue("The full course perimeter must be checked",edges.size>=2*(bounds.nx+bounds.nz))
        assertTrue("Each seam segment must have a triangle on both sides",edges.values.all { it==2 })
        assertTrue("The distant apron must stay lightweight: $apronTriangles triangles",apronTriangles<13000)
    }
}
