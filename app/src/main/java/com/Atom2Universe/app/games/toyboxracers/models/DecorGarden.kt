package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.mesh
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.tube
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.v
import kotlin.math.*

/** Shared folded leaves and individual petals; no spherical flowers. */
internal object DecorGarden {
    private val greens = intArrayOf(0x365C36, 0x4F783E, 0x668C47, 0x7E9F55)
    private fun DecorBuilder.leaf(x: Float, y: Float, z: Float,
        dx: Float, dy: Float, dz: Float, width: Float, color: Int, petal: Boolean = false) {
        val root=v(x,y,z); val tip=root+v(dx,dy,dz); val mid=root+v(dx,dy,dz)*.48f
        // Petals spread in the tilted flower plane; foliage spreads horizontally.
        val side=if(petal) v(-dy,dx,-dx*.56f).normalized()*width
            else if(abs(dx)+abs(dz)<.01f) v(width,0f,0f) else v(-dz,0f,dx).normalized()*width
        val left=mid+side; val right=mid-side
        val ridge=mid+v(0f,width*.36f,.04f); val under=mid-v(0f,width*.12f,.02f)
        mesh(color,false) {
            addAll(listOf(root,left,ridge,left,tip,ridge,tip,right,ridge,right,root,ridge,
                root,under,left,left,under,tip,tip,under,right,right,under,root))
        }
    }

    fun hedge(b: DecorBuilder) = with(b) {
        for(i in 0..4) {
            val x=-6f+i*3f; val z=if(i%2==0) -.35f else .4f
            val h=floatArrayOf(5.7f,6.5f,6.7f,6.2f,5.5f)[i]
            cylinder(x,.9f,z,.22f,1.8f,0x75604A)
            // Uneven faceted canopies with a broken outline; shared closed collision pieces.
            for(j in 0..9) {
                fun point(ring: Int, k: Int): com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3 {
                    val a=k%10*(2f*PI.toFloat()/10f)
                    val r=floatArrayOf(1.35f,2.35f,1.9f,.3f)[ring]*(1f+.1f*sin(k%10*2.7f+i))
                    return v(x+cos(a)*r,.6f+floatArrayOf(0f,.38f,.78f,1f)[ring]*h,z+sin(a)*r)
                }
                mesh(greens[(i+j)%4]) {
                    for(ring in 0..2) {
                        val a=point(ring,j); val c=point(ring+1,j+1)
                        addAll(listOf(a,point(ring+1,j),c,a,c,point(ring,j+1)))
                    }
                    addAll(listOf(point(3,j),v(x,.6f+h,z),point(3,j+1)))
                    addAll(listOf(point(0,j),point(0,j+1),v(x,.6f,z)))
                }
            }
            for(j in 0..13) {
                val a=j*2.39996f+i; val high=j%3; val r=if(high==2) 1.25f else 2f
                leaf(x+cos(a)*r,.6f+h*(.35f+high*.24f),z+sin(a)*r,
                    cos(a)*.95f,.25f,sin(a)*.95f,.32f,greens[(i+j+1)%4])
            }
        }
    }

    fun planter(b: DecorBuilder) = with(b) {
        val wood=0xB78960; val edge=0xD2AD7F
        for(x in floatArrayOf(-3.8f,3.8f)) box(x,.25f,0f,.8f,.5f,3.8f,0x73523C)
        box(0f,.6f,0f,10f,.5f,4f,wood)
        for(z in floatArrayOf(-1.85f,1.85f)) {
            for(row in 0..2) box(0f,1.05f+row*.72f,z,10f,.65f,.3f,wood)
            box(0f,3.15f,z,10.5f,.4f,.6f,edge)
            for(x in floatArrayOf(-4.5f,4.5f)) box(x,1.9f,z*1.09f,.32f,2.5f,.16f,edge,false)
        }
        for(x in floatArrayOf(-4.85f,4.85f)) {
            box(x,1.9f,0f,.3f,2.3f,3.5f,wood)
            box(x,3.15f,0f,.6f,.4f,4.2f,edge)
        }
        box(0f,2.85f,0f,9.4f,.24f,3.35f,0x594536)
        for(i in 0..4) {
            val x=-3.65f+i*1.8f; val z=if(i%2==0) -.55f else .5f
            val h=floatArrayOf(5.6f,6.1f,5.25f,6.25f,5.65f)[i]
            tube(listOf(v(x,2.95f,z),v(x-.15f,4.1f,z+.1f),v(x+.18f,h,z)),
                listOf(.07f,.065f,.045f),0x4F763D,false,6)
            leaf(x-.1f,3.65f,z,-.75f,.55f,.35f,.3f,greens[1])
            leaf(x,4.25f,z,.7f,.4f,-.5f,.28f,greens[2])
            val color=intArrayOf(0xF1D9AC,0xD591AC,0xC1AFD6,0xF1D9AC,0xD591AC)[i]
            for(p in 0..6) {
                val a=p*2f*PI.toFloat()/7f
                leaf(x+.18f,h,z,cos(a)*.75f,sin(a)*.57f,-sin(a)*.32f+.12f,.22f,color,petal=true)
            }
            oval(x+.18f,h+.06f,z+.17f,.36f,.3f,.26f,0xD4A144,false)
        }
    }
}
