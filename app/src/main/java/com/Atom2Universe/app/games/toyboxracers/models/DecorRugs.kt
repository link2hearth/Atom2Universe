package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LILAC
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.MINT
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.mesh
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.v
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The pattern tiles share edges: no background face is hidden underneath another pattern face. */
internal object DecorRugs {
    private const val TOP = .065f
    private class Pattern {
        val colors = linkedMapOf<Int, MutableList<Vec3>>()
        fun polygon(color: Int, points: List<Pair<Float,Float>>) {
            val vertices=points.map { (x,z) -> v(x,TOP,z) }
            val triangles=colors.getOrPut(color) { mutableListOf() }
            for(i in 1 until vertices.lastIndex) triangles.addAll(listOf(vertices[0],vertices[i],vertices[i+1]))
        }
        fun rect(color: Int, x0: Float, z0: Float, x1: Float, z1: Float) =
            polygon(color,listOf(x0 to z0,x0 to z1,x1 to z1,x1 to z0))
    }
    private fun rug(id: String, pattern: Pattern.() -> Unit): DecorModel {
        val b=DecorBuilder().apply {
            // A very thin physical backing; motifs and tassels do not create extra obstacles.
            box(0f,.02f,0f,12.8f,.04f,18.8f,CREAM)
            for(s in floatArrayOf(-1f,1f)) for(i in 0..23)
                box(-5.75f+i*.5f,.025f,s*9.65f,.13f,.05f,.65f,CREAM,false)
        }
        val tiles=Pattern().apply {
            rect(CREAM,-6.4f,-9.4f,6.4f,-9f)
            rect(CREAM,-6.4f,9f,6.4f,9.4f)
            rect(CREAM,-6.4f,-9f,-6f,9f)
            rect(CREAM,6f,-9f,6.4f,9f)
            pattern()
        }
        tiles.colors.forEach { (color,triangles) -> b.mesh(color,false) { addAll(triangles) } }
        return DecorModel("kawaii.rug_$id",DecorRoom.LIVING_ROOM,b.parts.toList(),surfacePriority=1)
    }
    val all by lazy { listOf(
        rug("checkerboard") {
            for(x in 0..7) for(z in 0..11)
                rect(if((x+z)%2==0) BLUE else CREAM,-6f+x*1.5f,-9f+z*1.5f,-4.5f+x*1.5f,-7.5f+z*1.5f)
        },
        rug("patchwork") {
            val palette=intArrayOf(ROSE,MINT,LILAC,BLUE,CREAM)
            for(x in 0..7) for(z in 0..11)
                rect(palette[(x*2+z*3)%palette.size],-6f+x*1.5f,-9f+z*1.5f,-4.5f+x*1.5f,-7.5f+z*1.5f)
        },
        rug("stripes") {
            val palette=intArrayOf(BLUE,CREAM,MINT,CREAM)
            for(z in 0..17) rect(palette[z%4],-6f,-9f+z,6f,-8f+z)
        },
        rug("chevrons") {
            // Clip zigzag ribbons at the edge of the carpet, with no overlapping faces.
            fun clip(points: List<Pair<Float,Float>>, edge: Float, keepAbove: Boolean): List<Pair<Float,Float>> {
                val result=mutableListOf<Pair<Float,Float>>()
                if(points.isEmpty()) return result
                var a=points.last()
                for(b in points) {
                    val aIn=if(keepAbove) a.second>=edge else a.second<=edge
                    val bIn=if(keepAbove) b.second>=edge else b.second<=edge
                    if(aIn!=bIn) {
                        val t=(edge-a.second)/(b.second-a.second)
                        result+=(a.first+(b.first-a.first)*t) to edge
                    }
                    if(bIn) result+=b
                    a=b
                }
                return result
            }
            for(column in 0..3) for(row in -2..12) {
                val x0=-6f+column*3f; val x1=x0+3f
                val a=if(column%2==0) 0f else 1.5f
                val b=1.5f-a; val z=-9f+row*1.5f
                val points=listOf(x0 to z+a,x0 to z+a+1.5f,x1 to z+b+1.5f,x1 to z+b)
                polygon(if(Math.floorMod(row,2)==0) ROSE else CREAM,clip(clip(points,-9f,true),9f,false))
            }
        },
        rug("diamonds") {
            for(x in 0..3) for(z in 0..5) {
                val x0=-6f+x*3f; val z0=-9f+z*3f
                val a=x0 to z0; val b=x0 to z0+3f; val c=x0+3f to z0+3f; val d=x0+3f to z0
                val ab=x0 to z0+1.5f; val bc=x0+1.5f to z0+3f
                val cd=x0+3f to z0+1.5f; val da=x0+1.5f to z0
                polygon(if((x+z)%2==0) LILAC else GOLD,listOf(ab,bc,cd,da))
                polygon(CREAM,listOf(a,ab,da)); polygon(CREAM,listOf(ab,b,bc))
                polygon(CREAM,listOf(bc,c,cd)); polygon(CREAM,listOf(cd,d,da))
            }
        },
        rug("medallion") {
            val palette=intArrayOf(GOLD,CREAM,ROSE,CREAM,BLUE)
            val radii=floatArrayOf(0f,1.15f,2f,3.25f,3.6f,4.8f)
            val sides=64
            for(i in 0 until sides) {
                val a=i*2f*PI.toFloat()/sides; val b=(i+1)*2f*PI.toFloat()/sides
                // Elliptical medallion elongated along the long axis.
                fun ellipse(angle: Float,r: Float)=r*cos(angle) to r*sin(angle)*1.5f
                for(j in palette.indices) polygon(palette[j],listOf(ellipse(a,radii[j]),ellipse(b,radii[j]),
                    ellipse(b,radii[j+1]),ellipse(a,radii[j+1])))
                fun edge(angle: Float): Pair<Float,Float> {
                    val r=minOf(6f/abs(cos(angle)).coerceAtLeast(.00001f),6f/abs(sin(angle)).coerceAtLeast(.00001f))
                    return ellipse(angle,r)
                }
                polygon(MINT,listOf(ellipse(a,4.8f),ellipse(b,4.8f),edge(b),edge(a)))
            }
        }
    ) }
    val names=mapOf(
        "kawaii.rug_checkerboard" to R.string.toybox_prop_rug_checkerboard,
        "kawaii.rug_patchwork" to R.string.toybox_prop_rug_patchwork,
        "kawaii.rug_stripes" to R.string.toybox_prop_rug_stripes,
        "kawaii.rug_chevrons" to R.string.toybox_prop_rug_chevrons,
        "kawaii.rug_diamonds" to R.string.toybox_prop_rug_diamonds,
        "kawaii.rug_medallion" to R.string.toybox_prop_rug_medallion
    )
}
