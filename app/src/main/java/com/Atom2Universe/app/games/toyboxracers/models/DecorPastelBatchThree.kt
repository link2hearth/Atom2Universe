package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.DARK
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GLASS
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LEAF
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LILAC
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.METAL
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.MINT
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.lathe
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.mesh
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.quad
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.rod
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.slice
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.tube
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.v
import kotlin.math.*

/** Sculpted pastel toys. IDs are stable across the prototype and revised collection. */
internal object DecorPastelBatchThree {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit): DecorModel {
        val parts = DecorBuilder().apply(build).parts
        val bottom = parts.minOf { part ->
            if (part.triangles.isEmpty()) part.y - part.height / 2 else part.triangles.minOf { it.y }
        }
        return DecorModel("kawaii.$id", room, parts.map { part ->
            part.copy(y = part.y - bottom, triangles = part.triangles.map { it.copy(y = it.y - bottom) })
        })
    }

    private fun DecorBuilder.eye(x: Float, y: Float, z: Float, size: Float = .25f) {
        oval(x,y,z,size,size*1.3f,.14f,DARK,false)
        oval(x-size*.16f,y+size*.2f,z+.065f,size*.28f,size*.28f,.035f,CREAM,false)
    }
    private fun DecorBuilder.bowl(x: Float, z: Float, bottom: Float, radius: Float, height: Float, color: Int) {
        lathe(x,z,listOf(0f to bottom, radius*.42f to bottom, radius*.72f to bottom+height*.23f,
            radius*.94f to bottom+height*.7f, radius to bottom+height,
            radius-.15f to bottom+height, radius*.86f to bottom+height*.7f,
            radius*.62f to bottom+height*.28f, 0f to bottom+.18f),color)
    }
    private fun DecorBuilder.ring(x: Float, y: Float, z: Float, radius: Float, thickness: Float, color: Int) {
        lathe(x,z,(0..10).map {
            val a = -PI.toFloat()/2 + it*2f*PI.toFloat()/10
            (radius+thickness*cos(a)) to (y+thickness*sin(a))
        },color)
    }

    val toys by lazy { listOf(
        model("alphabet_block", DecorRoom.BEDROOM) {
            box(0f,2f,0f,4f,4f,4f,MINT)
            for(s in floatArrayOf(-1f,1f)) {
                box(0f,2f,s*2.01f,3.5f,3.5f,.08f,CREAM,false)
                box(s*2.01f,2f,0f,.08f,3.5f,3.5f,BLUE,false)
                rod(v(-.8f,1f,s*2.08f),v(0f,3.1f,s*2.08f),.16f,ROSE,false)
                rod(v(0f,3.1f,s*2.08f),v(.8f,1f,s*2.08f),.16f,ROSE,false)
                box(0f,1.8f,s*2.09f,1f,.25f,.08f,ROSE,false)
                for(z in floatArrayOf(-.7f,.7f)) oval(s*2.08f,2f,z,.1f,.5f,.5f,GOLD,false)
            }
            box(0f,4.03f,0f,3.5f,.08f,3.5f,CREAM,false)
            ring(0f,4.12f,0f,.85f,.12f,LILAC)
        },
        model("train", DecorRoom.BEDROOM) {
            // Engine and wagons run along Z; their axles run along X.
            for(i in 0..3) {
                val z=-5.4f+i*3.5f
                box(0f,.95f,z,2.9f,.45f,3f,WOOD)
                for(x in floatArrayOf(-1.5f,1.5f)) for(dz in floatArrayOf(-.95f,.95f)) {
                    wheel(x,.65f,z+dz,.65f,.32f,DARK)
                    wheel(x*1.12f,.65f,z+dz,.34f,.06f,CREAM)
                }
                if(i>0) {
                    val c=intArrayOf(BLUE,ROSE,GOLD,LILAC)[i]
                    box(0f,1.65f,z,2.6f,1.1f,2.6f,c)
                    for(x in floatArrayOf(-1.2f,1.2f)) box(x,2.35f,z,.22f,.5f,2.65f,c)
                    for(dz in floatArrayOf(-1.2f,1.2f)) box(0f,2.35f,z+dz,2.6f,.5f,.22f,c)
                    oval(0f,2.3f,z,1.7f,1.1f,1.7f,CREAM)
                    rod(v(0f,1f,z-1.8f),v(0f,1f,z-1.4f),.18f,METAL)
                }
            }
            rod(v(0f,1.85f,-6.65f),v(0f,1.85f,-5f),.85f,BLUE)
            box(0f,2.2f,-4.65f,2.4f,2f,1.15f,MINT)
            box(0f,3.3f,-4.65f,2.8f,.3f,1.5f,ROSE)
            for(x in floatArrayOf(-1.22f,1.22f)) box(x,2.65f,-4.65f,.08f,.7f,.72f,GLASS,false)
            cylinder(0f,2.95f,-6.1f,.32f,1f,DARK)
            cylinder(0f,3.48f,-6.1f,.48f,.18f,ROSE)
            oval(0f,1.85f,-6.72f,.65f,.65f,.13f,GOLD,false)
        },
        model("yo_yo", DecorRoom.BEDROOM) {
            for(s in floatArrayOf(-1f,1f)) {
                wheel(s*.53f,1.65f,0f,1.65f,.75f,if(s<0) ROSE else BLUE)
                wheel(s*.95f,1.65f,0f,1.3f,.12f,CREAM)
                wheel(s*1.03f,1.65f,0f,.55f,.12f,GOLD)
            }
            wheel(0f,1.65f,0f,.35f,.4f,WOOD)
            tube(listOf(v(0f,2f,0f),v(0f,3.6f,0f),v(.35f,4.35f,0f),v(.8f,4.35f,0f),
                v(1f,4f,0f),v(.7f,3.8f,0f),v(.35f,4.1f,0f)),List(7){.055f},CREAM,false)
        },
        model("dinosaur", DecorRoom.BEDROOM) {
            oval(0f,2f,0f,4.6f,2.7f,2.2f,MINT)
            tube(listOf(v(1.3f,2.2f,0f),v(2f,3f,0f),v(2.25f,4f,0f)),listOf(.85f,.7f,.65f),MINT)
            oval(2.65f,4.05f,0f,2f,1.3f,1.5f,MINT)
            tube(listOf(v(-1.7f,2f,0f),v(-2.8f,1.8f,0f),v(-3.9f,2.25f,0f)),listOf(.7f,.4f,.06f),LEAF)
            for(x in floatArrayOf(-1.2f,1.1f)) for(z in floatArrayOf(-.75f,.75f)) {
                oval(x,.85f,z,.75f,1.4f,.75f,LEAF)
            }
            for(x in floatArrayOf(-1.45f,1.4f)) for(z in floatArrayOf(-1.15f,1.15f))
                rod(v(x,.45f,z-.15f),v(x,.45f,z+.15f),.45f,WOOD)
            for(i in 0..3) cone(-1.45f+i*.72f,3.05f,0f,.32f,.85f,GOLD)
            for(s in floatArrayOf(-1f,1f)) eye(2.8f,4.23f,s*.71f,.22f)
        }
    ) }

    val tools by lazy { listOf(
        model("pliers", DecorRoom.GARAGE) {
            for(s in floatArrayOf(-1f,1f)) {
                tube(listOf(v(s*1.1f,.38f,3.2f),v(s*.85f,.38f,1.6f),v(0f,.38f,0f),v(-s*.7f,.38f,-1.2f),
                    v(-s*.65f,.38f,-2.2f),v(-s*.25f,.38f,-2.5f)),listOf(.3f,.3f,.28f,.27f,.25f,.18f),METAL)
                tube(listOf(v(s*1.1f,.38f,3.2f),v(s*1f,.38f,2.7f),v(s*.85f,.38f,1.6f)),List(3){.38f},if(s<0) BLUE else ROSE)
                for(i in 0..2) box(s*.33f,.4f,-1.75f-i*.2f,.22f,.4f,.09f,DARK,false)
            }
            cylinder(0f,.72f,0f,.43f,.22f,GOLD,false)
        },
        model("tape_measure", DecorRoom.GARAGE) {
            box(0f,1.2f,0f,2.8f,2.4f,2.4f,GOLD)
            oval(0f,1.5f,0f,3.5f,2.6f,2.45f,GOLD)
            oval(0f,1.55f,1.23f,2.35f,1.8f,.15f,DARK,false)
            oval(0f,1.55f,1.33f,1.85f,1.4f,.1f,CREAM,false)
            box(0f,2.7f,0f,.85f,.22f,1.1f,BLUE)
            box(3f,.45f,0f,3.6f,.12f,.85f,CREAM)
            for(i in 0..10) box(1.45f+i*.3f,.52f,-.16f,.055f,.025f,if(i%5==0) .65f else .3f,DARK,false)
            box(4.8f,.32f,0f,.16f,.5f,1f,METAL)
        },
        model("paint_roller", DecorRoom.GARAGE) {
            wheel(0f,.85f,-1.6f,.85f,4.5f,MINT)
            for(s in floatArrayOf(-1f,1f)) wheel(s*2.3f,.85f,-1.6f,.64f,.13f,CREAM)
            tube(listOf(v(2.45f,.85f,-1.6f),v(2.85f,.85f,-1.6f),v(2.85f,.85f,.3f),
                v(0f,.85f,.3f),v(0f,.45f,1.3f)),List(5){.12f},METAL)
            rod(v(0f,.45f,1.2f),v(0f,.45f,3.8f),.4f,ROSE)
            oval(0f,.45f,3.8f,.8f,.8f,.8f,ROSE)
        }
    ) }

    val kitchen by lazy { listOf(
        model("whisk", DecorRoom.KITCHEN) {
            rod(v(0f,.9f,1.2f),v(0f,.9f,4.2f),.3f,WOOD)
            rod(v(0f,.9f,.8f),v(0f,.9f,1.35f),.34f,METAL)
            for(i in 0..3) {
                val angle=i*PI.toFloat()/4
                val points=(0..24).map { j ->
                    val a=j*2f*PI.toFloat()/24
                    val r=.85f*sin(a)
                    v(r*cos(angle),.9f+r*sin(angle),-.95f+1.8f*cos(a))
                }
                tube(points,List(points.size){.065f},METAL,false)
            }
        },
        model("ladle", DecorRoom.KITCHEN) {
            bowl(0f,-1.5f,0f,1.5f,1.1f,BLUE)
            tube(listOf(v(0f,.85f,-.2f),v(0f,1.15f,.7f),v(0f,1.35f,2f)),List(3){.16f},METAL)
            rod(v(0f,1.35f,1.8f),v(0f,1.35f,4.1f),.3f,WOOD)
        },
        model("colander", DecorRoom.KITCHEN) {
            lathe(0f,0f,listOf(0f to .15f,1.05f to .15f,1.8f to .65f,
                1.65f to .65f,.95f to .32f,0f to .32f),MINT)
            // Real perforations, with a short tunnel through the thickness of each wall panel.
            for(row in 0..2) for(i in 0 until 20) mesh(MINT) {
                val y0=.65f+row*.5f
                val r0=floatArrayOf(1.8f,2.15f,2.37f)[row]
                val r1=floatArrayOf(2.15f,2.37f,2.5f)[row]
                fun p(u: Float, t: Float, inside: Boolean): com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3 {
                    val angle=(i+u)*2f*PI.toFloat()/20
                    val radius=r0+(r1-r0)*t-if(inside) .15f else 0f
                    return v(radius*cos(angle),y0+t*.5f,radius*sin(angle))
                }
                val boundary=listOf(0f to 0f,0f to 1f,1f to 1f,1f to 0f)
                val aperture=listOf(.32f to .32f,.32f to .68f,.68f to .68f,.68f to .32f)
                for(j in 0..3) {
                    val k=(j+1)%4
                    val a=boundary[j]; val b=boundary[k]; val c=aperture[k]; val d=aperture[j]
                    quad(p(a.first,a.second,false),p(b.first,b.second,false),p(c.first,c.second,false),p(d.first,d.second,false))
                    quad(p(a.first,a.second,true),p(d.first,d.second,true),p(c.first,c.second,true),p(b.first,b.second,true))
                    quad(p(d.first,d.second,false),p(c.first,c.second,false),p(c.first,c.second,true),p(d.first,d.second,true))
                }
                if(row==2) quad(p(0f,1f,false),p(0f,1f,true),p(1f,1f,true),p(1f,1f,false))
            }
            ring(0f,2.15f,0f,2.43f,.14f,CREAM)
            for(s in floatArrayOf(-1f,1f)) {
                tube(listOf(v(s*2.25f,1.85f,-.55f),v(s*3.15f,1.85f,-.55f),v(s*3.35f,1.85f,0f),
                    v(s*3.15f,1.85f,.55f),v(s*2.25f,1.85f,.55f)),List(5){.14f},WOOD)
            }
            cylinder(0f,.12f,0f,1.05f,.24f,MINT)
        },
        model("mug", DecorRoom.KITCHEN) {
            lathe(0f,0f,listOf(0f to 0f,1.3f to 0f,1.55f to .2f,1.65f to 2.8f,
                1.48f to 2.8f,1.38f to .35f,0f to .35f),ROSE)
            ring(0f,2.8f,0f,1.56f,.1f,CREAM)
            tube((0..16).map {
                val a=-PI.toFloat()/2+it*PI.toFloat()/16
                v(1.45f+1.1f*cos(a),1.5f+sin(a),0f)
            },List(17){.23f},ROSE)
            oval(-.5f,1.7f,1.59f,.6f,.6f,.12f,GOLD,false)
        }
    ) }

    val gaming by lazy { listOf(
        model("arcade_cabinet", DecorRoom.BEDROOM) {
            box(0f,1.65f,0f,4.3f,3.3f,3.1f,LILAC)
            box(0f,4.45f,-.9f,4.3f,3f,1.25f,BLUE)
            for(s in floatArrayOf(-1f,1f)) box(s*2.1f,4.55f,0f,.3f,3.1f,2.8f,LILAC)
            box(0f,4.8f,-.21f,3.65f,2.3f,.15f,DARK,false)
            box(0f,4.8f,-.1f,3.2f,1.9f,.08f,GLASS,false)
            for(i in 0..4) box(-1.2f+i*.6f,4.2f+(i%3)*.38f,-.035f,.35f,.25f,.04f,if(i%2==0) GOLD else MINT,false)
            box(0f,3.35f,1f,4.5f,.35f,2f,CREAM)
            cylinder(-1.1f,3.8f,1.25f,.12f,.6f,METAL,false)
            oval(-1.1f,4.13f,1.25f,.5f,.5f,.5f,ROSE,false)
            for(i in 0..2) cylinder(.2f+i*.55f,3.6f,1.2f,.2f,.15f,intArrayOf(MINT,GOLD,BLUE)[i],false)
            box(0f,6.25f,0f,4.7f,.85f,3.1f,BLUE)
            box(0f,6.25f,1.58f,3.9f,.5f,.1f,GOLD,false)
            box(0f,1.6f,1.59f,.9f,.8f,.08f,DARK,false)
            box(0f,1.75f,1.65f,.5f,.09f,.06f,METAL,false)
            box(0f,.15f,0f,4.5f,.3f,3.3f,DARK)
        },
        model("joystick", DecorRoom.BEDROOM) {
            box(0f,.38f,0f,4.8f,.76f,3.8f,BLUE)
            box(0f,.82f,0f,4.5f,.15f,3.5f,CREAM)
            ring(-.95f,1f,.2f,.55f,.13f,DARK)
            cylinder(-.95f,1.6f,.2f,.16f,1.3f,METAL)
            oval(-.95f,2.4f,.2f,.85f,.85f,.85f,ROSE)
            for(i in 0..2) cylinder(.5f+i*.57f,1f,-.45f+(i%2)*.6f,.25f,.2f,intArrayOf(BLUE,GOLD,MINT)[i],false)
            for(x in floatArrayOf(-1.85f,1.85f)) for(z in floatArrayOf(-1.35f,1.35f)) cylinder(x,.95f,z,.08f,.03f,METAL,false)
        }
    ) }

    val books by lazy { listOf(
        model("open_book", DecorRoom.OFFICE) {
            for(s in floatArrayOf(-1f,1f)) {
                box(s*1.5f,.13f,0f,3f,.26f,4.8f,LILAC)
                mesh(CREAM) {
                    val a=v(s*.12f,.42f,-2.25f); val b=v(s*1.1f,.75f,-2.25f); val c=v(s*2.85f,.4f,-2.25f)
                    val d=v(s*.12f,.42f,2.25f); val e=v(s*1.1f,.75f,2.25f); val f=v(s*2.85f,.4f,2.25f)
                    if(s>0) { quad(a,d,e,b); quad(b,e,f,c) } else { quad(a,b,e,d); quad(b,c,f,e) }
                    fun end(z: Float, front: Boolean) {
                        val base=v(s*.12f,.27f,z)
                        val outline=listOf(v(s*.12f,.42f,z),v(s*1.1f,.75f,z),
                            v(s*2.85f,.4f,z),v(s*2.85f,.27f,z))
                        for(i in 0 until outline.lastIndex) {
                            if(front == (s>0)) addAll(listOf(base,outline[i+1],outline[i]))
                            else addAll(listOf(base,outline[i],outline[i+1]))
                        }
                    }
                    end(-2.25f,false); end(2.25f,true)
                    val cb=v(s*2.85f,.27f,-2.25f); val fb=v(s*2.85f,.27f,2.25f)
                    val ab=v(s*.12f,.27f,-2.25f); val db=v(s*.12f,.27f,2.25f)
                    if(s>0) { quad(c,f,fb,cb); quad(a,ab,db,d); quad(ab,cb,fb,db) }
                    else { quad(c,cb,fb,f); quad(a,d,db,ab); quad(ab,db,fb,cb) }
                }
                for(i in 0..4) rod(v(s*1.2f,.738f,-1.55f+i*.65f),v(s*2.55f,.468f,-1.55f+i*.65f),.025f,WOOD,false)
            }
            box(0f,.35f,0f,.12f,.12f,4.6f,WOOD,false)
            box(.35f,.3f,2.5f,.32f,.06f,.8f,ROSE,false)
        },
        model("notebook", DecorRoom.OFFICE) {
            box(0f,.12f,0f,4.6f,.24f,6f,BLUE)
            box(.1f,.4f,0f,4.25f,.35f,5.65f,CREAM)
            box(0f,.64f,0f,4.6f,.15f,6f,BLUE)
            for(i in 0..6) {
                val z=-2.45f+i*.8f
                tube((0..16).map { j ->
                    val a=j*2f*PI.toFloat()/16
                    v(-2.05f+.38f*cos(a),.5f+.43f*sin(a),z)
                },List(17){.065f},METAL,false)
            }
            box(.25f,.73f,-.5f,2.5f,.04f,2.1f,CREAM,false)
            for(i in 0..2) box(.25f,.76f,-1.05f+i*.5f,1.8f,.02f,.06f,WOOD,false)
            box(1.5f,.74f,0f,.2f,.05f,6f,ROSE,false)
        },
        model("sharpener", DecorRoom.OFFICE) {
            box(0f,.65f,0f,2.8f,1.3f,2f,MINT)
            box(0f,1.35f,0f,2.9f,.16f,2.1f,CREAM)
            oval(0f,.7f,1.03f,1.1f,.95f,.08f,DARK,false)
            oval(0f,.7f,1.08f,.67f,.58f,.05f,WOOD,false)
            box(0f,1.46f,0f,.4f,.05f,1.6f,DARK,false)
            box(.35f,1.5f,0f,.46f,.08f,1.7f,METAL,false)
            cylinder(.35f,1.56f,-.45f,.14f,.05f,DARK,false)
            box(.35f,1.6f,-.45f,.18f,.02f,.035f,CREAM,false)
        },
        model("ruler", DecorRoom.OFFICE) {
            box(0f,.12f,0f,1.6f,.24f,8.5f,GOLD)
            for(i in 0..20) {
                val len=if(i%5==0) .75f else if(i%2==0) .48f else .28f
                box(-.78f+len/2,.255f,-3.8f+i*.38f,len,.025f,.04f,DARK,false)
            }
            box(.55f,.255f,0f,.04f,.025f,7.6f,CREAM,false)
        }
    ) }

    val figures by lazy { listOf(
        model("astronaut", DecorRoom.BEDROOM) {
            box(0f,2.4f,-.75f,1.7f,1.9f,.8f,METAL)
            for(s in floatArrayOf(-1f,1f)) {
                oval(s*.62f,.4f,.23f,1.05f,.8f,1.5f,CREAM)
                box(s*.62f,.12f,.23f,1f,.24f,1.4f,BLUE)
                rod(v(s*.62f,.7f,0f),v(s*.62f,1.75f,0f),.43f,CREAM)
                cylinder(s*.62f,1f,0f,.45f,.22f,BLUE)
                oval(s*1.12f,2.85f,0f,.9f,.95f,1f,CREAM)
                rod(v(s*1.2f,2.7f,0f),v(s*1.55f,1.9f,.18f),.34f,CREAM)
                oval(s*1.57f,1.75f,.2f,.72f,.7f,.8f,BLUE)
            }
            oval(0f,2.3f,0f,2.15f,2.35f,1.6f,CREAM)
            cylinder(0f,3.3f,0f,.83f,.25f,METAL)
            oval(0f,4.3f,0f,2.65f,2.45f,2.45f,CREAM)
            oval(0f,4.33f,.87f,2.2f,1.85f,.83f,GOLD,false)
            oval(0f,4.34f,1.02f,1.95f,1.58f,.7f,0x354D69,false)
            oval(-.43f,4.72f,1.29f,.58f,.28f,.065f,0xBFE4EA,false)
            box(0f,2.55f,.79f,1.1f,.8f,.16f,BLUE,false)
            for(i in 0..2) oval(-.3f+i*.3f,2.4f,.9f,.14f,.14f,.06f,intArrayOf(ROSE,GOLD,MINT)[i],false)
            rod(v(.65f,3f,-.9f),v(.65f,4.4f,-.9f),.065f,METAL,false)
            oval(.65f,4.45f,-.9f,.2f,.2f,.2f,ROSE,false)
        },
        model("frog", DecorRoom.OUTDOOR) {
            oval(0f,1.3f,0f,3.5f,2.3f,2.8f,MINT)
            oval(0f,1.13f,1.02f,2.3f,1.5f,.65f,CREAM,false)
            for(s in floatArrayOf(-1f,1f)) {
                oval(s*1.1f,2.45f,.5f,1.25f,1.25f,1.2f,MINT)
                oval(s*1.1f,2.55f,1f,.75f,.8f,.35f,CREAM,false)
                eye(s*1.1f,2.57f,1.17f,.35f)
                oval(s*1.65f,.65f,-.15f,1.35f,1.2f,1.8f,LEAF)
                oval(s*1.5f,.24f,1f,1.2f,.48f,1.8f,MINT)
                for(i in 0..2) oval(s*1.5f+(i-1)*.32f,.15f,1.8f,.35f,.3f,.65f,MINT)
            }
            tube(listOf(v(-.65f,1.8f,1.15f),v(0f,1.65f,1.37f),v(.65f,1.8f,1.15f)),List(3){.055f},DARK,false)
        },
        model("ghost", DecorRoom.BEDROOM) {
            lathe(0f,0f,listOf(0f to .25f,1.65f to .25f,1.9f to .65f,1.7f to 2.7f,
                1.35f to 3.7f,.75f to 4.2f,0f to 4.4f),CREAM)
            for(i in 0..7) {
                val a=i*PI.toFloat()/4
                oval(1.4f*cos(a),.4f,1.4f*sin(a),.9f,.8f,.9f,CREAM)
            }
            for(s in floatArrayOf(-1f,1f)) {
                oval(s*1.8f,2.15f,0f,1.2f,.7f,.9f,CREAM)
                eye(s*.55f,2.95f,1.53f,.35f)
            }
            oval(0f,2.4f,1.73f,.4f,.5f,.1f,DARK,false)
        },
        model("snowman", DecorRoom.OUTDOOR) {
            oval(0f,1.3f,0f,3.5f,2.6f,3.5f,CREAM)
            oval(0f,3f,0f,2.7f,2.5f,2.7f,CREAM)
            oval(0f,4.65f,0f,2.15f,2.05f,2.1f,CREAM)
            for(s in floatArrayOf(-1f,1f)) {
                eye(s*.43f,4.9f,.94f,.2f)
                rod(v(s*1f,3.2f,0f),v(s*2.3f,3.6f,0f),.12f,WOOD)
                rod(v(s*1.95f,3.5f,0f),v(s*2.1f,4f,0f),.08f,WOOD)
            }
            tube(listOf(v(0f,4.58f,1f),v(0f,4.52f,1.95f)),listOf(.2f,.015f),0xECA16A)
            ring(0f,3.85f,0f,.89f,.17f,ROSE)
            box(.65f,3.28f,1.12f,.42f,1.15f,.22f,ROSE,false)
            for(y in floatArrayOf(2.4f,3f)) oval(0f,y,1.28f,.2f,.2f,.15f,DARK,false)
            cylinder(0f,5.65f,0f,1.2f,.18f,BLUE)
            cylinder(0f,6.12f,0f,.8f,.85f,BLUE)
            cylinder(0f,5.85f,0f,.82f,.2f,ROSE,false)
        }
    ) }

    val fruit by lazy { listOf(
        model("banana", DecorRoom.KITCHEN) {
            val points=(0..16).map { i ->
                val a=(-155f+i*130f/16)*PI.toFloat()/180
                v(2.75f*cos(a),3.25f+2.6f*sin(a),0f)
            }
            val radii=(0..16).map { .12f+.55f*sin(it*PI.toFloat()/16).coerceAtLeast(0f).pow(.65f) }
            tube(points,radii,GOLD,sides=10)
            tube(listOf(points.first()+v(-.18f,.35f,0f),points.first()),listOf(.15f,.13f),WOOD)
            oval(points.last().x,points.last().y,0f,.28f,.28f,.28f,WOOD)
        },
        model("watermelon", DecorRoom.KITCHEN) {
            slice(2.48f,2.7f,1.5f,LEAF)
            slice(2.25f,2.48f,1.51f,0xDDE9AF)
            slice(0f,2.25f,1.52f,0xED8795)
            for(s in floatArrayOf(-1f,1f)) for(row in 0..1) for(i in 0..4) {
                val a=PI.toFloat()+(.22f+i*.155f)*PI.toFloat()
                val r=if(row==0) 1.75f else 1.05f
                oval(r*cos(a),2.7f+r*sin(a),s*.78f,.14f,.24f,.07f,DARK,false)
            }
        },
        model("orange", DecorRoom.KITCHEN) {
            oval(0f,1.65f,0f,3.4f,3.3f,3.4f,0xEDB36B)
            cylinder(0f,3.28f,0f,.22f,.15f,WOOD,false)
            oval(.48f,3.36f,0f,1.1f,.14f,.5f,LEAF,false)

        },
        model("cupcake", DecorRoom.KITCHEN) {
            lathe(0f,0f,listOf(0f to 0f,1.2f to 0f,1.6f to 1.8f,0f to 1.8f),BLUE)
            for(i in 0 until 16) {
                val a=i*PI.toFloat()/8
                rod(v(1.22f*cos(a),.1f,1.22f*sin(a)),v(1.6f*cos(a),1.7f,1.6f*sin(a)),.045f,CREAM,false)
            }
            oval(0f,1.9f,0f,3.4f,1f,3.4f,WOOD)
            lathe(0f,0f,listOf(0f to 2f,1.15f to 2f,1.15f to 2.3f,.8f to 2.7f,
                .35f to 3.15f,0f to 3.5f),ROSE)
            val points=(0..64).map { i ->
                val t=i/64f; val a=t*5f*PI.toFloat()
                v((1.3f*(1-t)+.04f)*cos(a),2.05f+1.3f*t,(1.3f*(1-t)+.04f)*sin(a))
            }
            tube(points,(0..64).map { .4f*(1-it/105f) },ROSE,sides=8)
            oval(0f,3.6f,0f,.5f,.5f,.5f,0xCB7088)
        },
        model("donut", DecorRoom.KITCHEN) {
            // Closed torus: the centre is empty in both geometry and collision strips.
            lathe(0f,0f,(0..16).map {
                val a=-PI.toFloat()/2+it*2f*PI.toFloat()/16
                (1.65f+.75f*cos(a)) to (.68f+.68f*sin(a))
            },0xD8A16D,segments=32)
            for(i in 0 until 32) mesh(ROSE,false) {
                fun p(k: Int, j: Int): com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3 {
                    val u=k*2f*PI.toFloat()/32
                    val edge=.15f+.17f*sin(5*u)+.1f*sin(9*u)
                    val a=edge+(PI.toFloat()-.2f-edge)*j/8
                    return v((1.65f+.77f*cos(a))*cos(u),.68f+.71f*sin(a),(1.65f+.77f*cos(a))*sin(u))
                }
                for(j in 0..7) quad(p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j))
            }
            for(i in 0 until 28) {
                val u=i*2.399963f; val a=.7f+(i%5)*.4f
                val r=1.65f+.77f*cos(a); val y=.68f+.71f*sin(a)+.045f
                val dx=.11f*cos(u+.8f); val dz=.11f*sin(u+.8f)
                rod(v(r*cos(u)-dx,y,r*sin(u)-dz),v(r*cos(u)+dx,y,r*sin(u)+dz),.045f,
                    intArrayOf(CREAM,BLUE,GOLD,MINT,LILAC)[i%5],false)
            }
        },
        model("ice_cream", DecorRoom.KITCHEN) {
            lathe(0f,0f,listOf(0f to 0f,.12f to .1f,1.15f to 2.8f,0f to 2.8f),WOOD)
            for(s in floatArrayOf(-1f,1f)) for(i in 0..9) {
                val points=(0..12).map { j ->
                    val t=.12f+j*.86f/12; val a=i*PI.toFloat()/5+s*t*2.8f
                    v((.12f+1.03f*t)*cos(a),.1f+2.7f*t,(.12f+1.03f*t)*sin(a))
                }
                tube(points,List(13){.025f},GOLD,false,sides=4)
            }
            ring(0f,2.72f,0f,1.13f,.1f,GOLD)
            oval(0f,3.3f,0f,2.9f,2.35f,2.9f,ROSE)
            for(i in 0..7) {
                val a=i*PI.toFloat()/4
                oval(cos(a),2.72f,sin(a),.85f,.65f,.85f,ROSE)
            }
            oval(.1f,4.65f,0f,2.2f,1.8f,2.2f,CREAM)
        }
    ) }

    val trackside by lazy { listOf(
        model("toy_pine", DecorRoom.OUTDOOR) {
            cylinder(0f,.65f,0f,.45f,1.3f,WOOD)
            cone(0f,.85f,0f,2.2f,2.7f,LEAF)
            cone(0f,2.3f,0f,1.65f,2.4f,MINT)
            cone(0f,3.65f,0f,1.1f,2.3f,LEAF)
            oval(0f,5.94f,0f,.38f,.45f,.38f,GOLD)
        },
        model("hay_bale", DecorRoom.OUTDOOR) {
            box(0f,1.1f,0f,5f,2.2f,3.4f,GOLD)
            for(x in floatArrayOf(-1.45f,1.45f)) {
                box(x,2.23f,0f,.16f,.1f,3.5f,CREAM,false)
                for(z in floatArrayOf(-1.73f,1.73f)) box(x,1.1f,z,.16f,2.2f,.1f,CREAM,false)
            }
            for(i in 0..8) {
                for(z in floatArrayOf(-1.71f,1.71f)) box(0f,.2f+i*.22f,z,4.8f,.035f,.025f,WOOD,false)
                box(0f,2.215f,-1.4f+i*.35f,4.8f,.025f,.035f,WOOD,false)
            }
        },
        model("seashell", DecorRoom.OUTDOOR) {
            // Scallop fan: ribs radiate from the hinge and end in a scalloped lip.
            for(i in 0 until 12) mesh(if(i%2==0) CREAM else 0xEBCDBD) {
                fun p(k: Int, j: Int, top: Boolean): com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3 {
                    val a=(-75f+k*150f/24)*PI.toFloat()/180; val t=j/8f
                    val r=3.7f*t*(if(k%2==0) .95f else 1f)
                    return v(r*sin(a),.14f+(if(top) .22f+.75f*sin(t*PI.toFloat()) else 0f),-1.6f+r*cos(a))
                }
                for(k in i*2..i*2+1) for(j in 0..7) {
                    quad(p(k,j,true),p(k,j+1,true),p(k+1,j+1,true),p(k+1,j,true))
                    quad(p(k,j,false),p(k+1,j,false),p(k+1,j+1,false),p(k,j+1,false))
                    if(j==7) quad(p(k,8,true),p(k,8,false),p(k+1,8,false),p(k+1,8,true))
                    if(k==0) quad(p(k,j,true),p(k,j,false),p(k,j+1,false),p(k,j+1,true))
                    if(k==23) quad(p(24,j,true),p(24,j+1,true),p(24,j+1,false),p(24,j,false))
                }
            }
            oval(0f,.25f,-1.45f,1.2f,.5f,.85f,ROSE)
        },
        model("light_beacon", DecorRoom.OUTDOOR) {
            cylinder(0f,.25f,0f,1.35f,.5f,DARK)
            cylinder(0f,2.3f,0f,.23f,4.1f,METAL)
            cylinder(0f,4.45f,0f,1.15f,.25f,CREAM)
            cylinder(0f,5.35f,0f,.52f,1.6f,GOLD,false)
            for(x in floatArrayOf(-.72f,.72f)) for(z in floatArrayOf(-.72f,.72f))
                rod(v(x,4.5f,z),v(x,6.15f,z),.095f,BLUE)
            cylinder(0f,6.15f,0f,1.15f,.2f,CREAM)
            cone(0f,6.25f,0f,1.35f,.85f,ROSE)
            oval(0f,7.15f,0f,.24f,.3f,.24f,GOLD)
        }
    ) }

    val names = mapOf(
        "kawaii.alphabet_block" to R.string.toybox_prop_alphabet_block,
        "kawaii.train" to R.string.toybox_prop_train,
        "kawaii.yo_yo" to R.string.toybox_prop_yo_yo,
        "kawaii.dinosaur" to R.string.toybox_prop_dinosaur,
        "kawaii.pliers" to R.string.toybox_prop_pliers,
        "kawaii.tape_measure" to R.string.toybox_prop_tape_measure,
        "kawaii.paint_roller" to R.string.toybox_prop_paint_roller,
        "kawaii.whisk" to R.string.toybox_prop_whisk,
        "kawaii.ladle" to R.string.toybox_prop_ladle,
        "kawaii.colander" to R.string.toybox_prop_colander,
        "kawaii.mug" to R.string.toybox_prop_mug,
        "kawaii.arcade_cabinet" to R.string.toybox_prop_arcade_cabinet,
        "kawaii.joystick" to R.string.toybox_prop_joystick,
        "kawaii.open_book" to R.string.toybox_prop_open_book,
        "kawaii.notebook" to R.string.toybox_prop_notebook,
        "kawaii.sharpener" to R.string.toybox_prop_sharpener,
        "kawaii.ruler" to R.string.toybox_prop_ruler,
        "kawaii.astronaut" to R.string.toybox_prop_astronaut,
        "kawaii.frog" to R.string.toybox_prop_frog,
        "kawaii.ghost" to R.string.toybox_prop_ghost,
        "kawaii.snowman" to R.string.toybox_prop_snowman,
        "kawaii.banana" to R.string.toybox_prop_banana,
        "kawaii.watermelon" to R.string.toybox_prop_watermelon,
        "kawaii.orange" to R.string.toybox_prop_orange,
        "kawaii.cupcake" to R.string.toybox_prop_cupcake,
        "kawaii.donut" to R.string.toybox_prop_donut,
        "kawaii.ice_cream" to R.string.toybox_prop_ice_cream,
        "kawaii.toy_pine" to R.string.toybox_prop_toy_pine,
        "kawaii.hay_bale" to R.string.toybox_prop_hay_bale,
        "kawaii.seashell" to R.string.toybox_prop_seashell,
        "kawaii.light_beacon" to R.string.toybox_prop_light_beacon
    )

    val all by lazy { toys + tools + kitchen + gaming + books + figures + fruit + trackside }
}
