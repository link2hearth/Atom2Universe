package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.toyboxracers.models.*

/** Reuse Toybox's source models, with Golf's smooth lighting and a single baked batch. */
internal object GolfToyboxModels {
    val mushroom by lazy { convert(DecorKawaiiCollection.figures.first { it.id == "kawaii.mushroom" }) }
    val snowman by lazy { convert(DecorPastelBatchThree.figures.first { it.id == "kawaii.snowman" }) }

    private fun convert(model:DecorModel) = MeshBuilder().apply {
        model.parts.forEach { p ->
            val c=C((p.color shr 16 and 255)/255f,(p.color shr 8 and 255)/255f,(p.color and 255)/255f)
            val bottom=p.y-p.height/2
            when(p.shape) {
                DecorShape.BOX -> box(p.x,bottom,p.z,p.width,p.height,p.depth,c)
                DecorShape.OVAL -> ellipsoid(p.x,p.y,p.z,p.width/2,p.height/2,p.depth/2,c)
                DecorShape.CYLINDER_Y -> cone(p.x,bottom,p.z,p.width/2,p.height,c,16,p.width/2)
                DecorShape.CYLINDER_X -> beam(P(p.x-p.width/2,p.y,p.z),P(p.x+p.width/2,p.y,p.z),p.height/2,c,16)
                DecorShape.CONE_Y -> cone(p.x,bottom,p.z,p.width/2,p.height,c,20)
                DecorShape.GABLE_ROOF -> {
                    val x=p.x; val z=p.z; val w=p.width/2; val d=p.depth/2
                    quad(P(x-w,bottom,z-d),P(x+w,bottom,z-d),P(x+w,bottom+p.height,z),P(x-w,bottom+p.height,z),c)
                    quad(P(x-w,bottom+p.height,z),P(x+w,bottom+p.height,z),P(x+w,bottom,z+d),P(x-w,bottom,z+d),c)
                }
                DecorShape.MESH -> for(i in p.triangles.indices step 3) {
                    fun point(j:Int)=p.triangles[j].let { P(it.x,it.y,it.z) }
                    tri(point(i),point(i+1),point(i+2),c)
                }
            }
        }
    }
}
