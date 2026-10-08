package com.Atom2Universe.app.games.billiards.render

import com.Atom2Universe.app.games.billiards.BilliardRoom
import com.Atom2Universe.app.games.toyboxracers.render.MeshBuilder
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.*

/** Static, metre-scale scenery, uploaded once. The playing area remains unobstructed. */
internal object BilliardEnvironmentMesh {
    private fun c(rgb: Int)=floatArrayOf((rgb shr 16 and 255)/255f,(rgb shr 8 and 255)/255f,(rgb and 255)/255f,1f)

    fun interior(b: MeshBuilder, room: BilliardRoom) = with(b) {
        val wood=c(room.wood); val dark=c(0x253036); val brass=c(0xB49A68)
        // Shallow bookcase along the west wall, clear of the cueing space.
        box(-4.13f,1.05f,.2f,.13f,2.1f,2.1f,wood)
        for(z in floatArrayOf(-.9f,1.3f)) box(-3.96f,1.05f,z,.46f,2.1f,.065f,wood)
        for(y in floatArrayOf(.15f,.65f,1.15f,1.65f,2.10f)) box(-3.96f,y,.2f,.46f,.045f,2.25f,wood)
        val spines=intArrayOf(0x82755B,0x465E5C,0x713F36,0xB69A6A,0x34465C)
        for(shelf in 0..3) for(i in 0..16) {
            if((i+shelf*3)%9==0) continue
            val h=.22f+(i*7+shelf*3)%5*.031f
            box(-3.92f,.18f+shelf*.50f+h/2,-.76f+i*.115f,.25f,h,.077f,c(spines[(i+shelf)%spines.size]))
            box(-3.789f,.21f+shelf*.50f,-.76f+i*.115f,.003f,.014f,.062f,brass)
        }
        // Console and refreshments on the opposite side from the sofa.
        for(x in floatArrayOf(-1.08f,1.08f)) box(x,.34f,2.99f,.07f,.68f,.36f,wood)
        box(0f,.56f,2.99f,2.5f,.30f,.44f,wood)
        box(0f,.75f,2.99f,2.62f,.085f,.52f,c(0xC9B897))
        for(x in floatArrayOf(-.63f,.63f)) {
            box(x,.56f,2.76f,1.15f,.23f,.018f,dark)
            box(x,.59f,2.741f,.14f,.015f,.016f,brass)
        }
        box(-.56f,.803f,2.98f,.65f,.018f,.29f,brass)
        for(i in 0..2) {
            cylinderY(-.78f+i*.16f,.92f,2.98f,.22f,.039f,12,c(if(i==1) 0x84522F else 0x294C3C))
            cylinderY(-.78f+i*.16f,1.063f,2.98f,.067f,.018f,12,brass)
        }
        for(i in 0..2) cylinderY(.22f+i*.13f,.84f,2.99f,.10f,.034f,12,c(0xB6CDCD))
        // A large triptych above the console reads from every camera angle.
        for(i in -1..1) {
            val x=i*.82f
            box(x,1.67f,3.27f,.69f,.87f,.04f,brass)
            box(x,1.67f,3.24f,.61f,.79f,.015f,c(if(room==BilliardRoom.NEON) 0x343450 else 0xD0C5AB))
            for(j in 0..3) box(x-.19f+j*.12f,1.49f+j*.08f,3.23f,.085f,.31f,.008f,c(spines[(i+j+2)%spines.size]))
        }
        if(room!=BilliardRoom.LOFT) {
            // Bench below the artwork on the east wall.
            for(z in floatArrayOf(-.65f,.65f)) box(3.96f,.22f,z,.35f,.44f,.06f,wood)
            box(3.96f,.46f,0f,.48f,.13f,1.65f,dark)
            box(4.13f,.69f,0f,.10f,.42f,1.65f,dark)
        }
        // Door and its trim fill the remaining west-wall corner.
        box(-4.28f,1.07f,2.29f,.05f,2.14f,.98f,brass)
        box(-4.245f,1.04f,2.29f,.027f,2.08f,.88f,wood)
        for(y in floatArrayOf(.59f,1.52f)) box(-4.225f,y,2.29f,.015f,.69f,.65f,dark)
        box(-4.18f,1.04f,2.58f,.07f,.022f,.11f,brass)
    }

    fun lawn(b: MeshBuilder) = with(b) {
        box(0f,-.135f,0f,120f,.11f,120f,c(0x68834C))
    }

    fun sky(b: MeshBuilder) = with(b) {
        // The renderer centres this dome on the eye, so even a wide orbit sees a horizon.
        lowPolyEllipsoid(0f,0f,0f,75f,75f,75f,16,32,c(0xFFFFFF))
    }

    fun water(b: MeshBuilder) = with(b) {
        quad(Vec3(-9.95f,.006f,-2.45f),Vec3(-9.95f,.006f,4.45f),
            Vec3(-6.05f,.006f,4.45f),Vec3(-6.05f,.006f,-2.45f),c(0x278EA8))
    }

    fun garden(b: MeshBuilder) = with(b) {
        val stone=c(0xD3C6AE); val timber=c(0x8D7050); val soil=c(0x615140)
        // Terraces and paths connect the pavilion, pool and house.
        box(-8f,-.049f,1f,6.5f,.07f,10.5f,stone)
        for(x in -11..-5) for(z in -4..6) {
            box(x.toFloat(),-.010f,z.toFloat(),.975f,.007f,.975f,c(if((x+z)%3==0) 0xC3B89F else 0xD8CBB1))
        }
        box(5.8f,-.039f,-1.6f,2.7f,.08f,8f,stone)
        for(i in 0..6) box(0f,-.049f,4.5f+i*.77f,1.25f,.075f,.60f,stone)
        for(i in 0..5) box(5f+i*.8f,-.049f,3.7f, .65f,.075f,1f,stone)
        // Pool coping sits above a dark throat and the separate reflective water surface.
        box(-8f,-.020f,1f,4.12f,.034f,7.12f,c(0x174D60))
        for(x in floatArrayOf(-10.12f,-5.88f)) box(x,.043f,1f,.28f,.11f,7.55f,c(0xEEE2CC))
        for(z in floatArrayOf(-2.64f,4.64f)) box(-8f,.043f,z,4.02f,.11f,.28f,c(0xEEE2CC))
        for(z in floatArrayOf(.7f,1.25f)) {
            cylinderY(-5.85f,.31f,z,.60f,.025f,12,c(0xB6C4C3))
            box(-6.04f,.605f,z,.40f,.045f,.045f,c(0xB6C4C3))
            cylinderY(-6.22f,.33f,z,.55f,.025f,12,c(0xB6C4C3))
        }
        for(y in floatArrayOf(.11f,.26f)) box(-6.22f,y,.975f,.06f,.035f,.55f,c(0xB6C4C3))
        for(x in floatArrayOf(-8.85f,-7.15f)) lounger(this,x,5.25f)
        cylinderY(-10.3f,1.12f,5.05f,2.3f,.027f,12,timber)
        cylinderY(-10.3f,.008f,5.05f,.04f,.26f,20,stone)
        coneY(-10.3f,2.12f,5.05f,.36f,1.03f,24,c(0xDBD4BD))
        // House to the east: stone plinth, plaster walls, tiled pitched roof and glazed bays.
        box(10.3f,.01f,-2.8f,6.2f,.18f,8.4f,stone)
        box(10.3f,1.55f,-2.8f,5.9f,2.95f,8.1f,c(0xE6D9BF))
        val left=7.15f; val right=13.45f; val back=-7.12f; val front=1.52f
        val ridge=10.3f
        triangle(Vec3(left,3.02f,front),Vec3(right,3.02f,front),Vec3(ridge,4.45f,front),c(0xDBCCB1))
        triangle(Vec3(right,3.02f,back),Vec3(left,3.02f,back),Vec3(ridge,4.45f,back),c(0xDBCCB1))
        for(side in intArrayOf(-1,1)) for(row in 0..7) for(tile in 0..27) {
            val t0=row/8f; val t1=(row+1)/8f
            val x0=ridge+side*3.29f*t0; val x1=ridge+side*3.29f*t1
            val y0=4.49f-1.46f*t0; val y1=4.49f-1.46f*t1
            val z0=back-.12f+tile*.32f
            val a=Vec3(x0,y0,z0); val d=Vec3(x0,y0,z0+.31f)
            val e=Vec3(x1,y1,z0+.31f); val f=Vec3(x1,y1,z0)
            val roof=c(if((tile+row)%3==0) 0x956B56 else 0xB48064)
            if(side<0) quad(a,f,e,d,roof) else quad(f,a,d,e,roof)
        }
        box(11.75f,4.1f,-5.1f,.52f,1.55f,.62f,c(0xC9BBA3))
        box(11.75f,4.89f,-5.1f,.66f,.12f,.75f,c(0x746D60))
        for(z in floatArrayOf(-5.1f,-2.3f,.25f)) {
            box(7.33f,1.47f,z,.10f,2.45f,1.96f,timber)
            box(7.265f,1.48f,z,.025f,2.24f,1.75f,c(0x709AA0))
            box(7.244f,1.48f,z,.02f,2.25f,.045f,stone)
            box(7.242f,1.2f,z,.02f,.035f,1.75f,stone)
            // Pale reflection strip across each window, no transparency sorting needed.
            box(7.239f,2.28f,z,.009f,.19f,1.73f,c(0xAAC2BE))
        }
        for(x in floatArrayOf(8.5f,12f)) {
            box(x,1.78f,1.27f,1.4f,1.45f,.08f,timber)
            box(x,1.78f,1.32f,1.24f,1.28f,.035f,c(0x7D9E9E))
            box(x,1.78f,1.35f,.045f,1.28f,.015f,stone)
            box(x,1.06f,1.44f,1.54f,.10f,.3f,stone)
        }
        // Narrow terrace awning and supports remain well outside the billiard pavilion.
        box(6.35f,2.77f,-2.8f,2.1f,.10f,8.4f,timber)
        for(z in floatArrayOf(-6.7f,1.1f)) box(5.37f,1.33f,z,.09f,2.76f,.09f,timber)
        for(i in 0..16) box(6.35f,2.84f,-6.8f+i*.50f,2.05f,.018f,.34f,c(0xD5C8AB))
        // Low beds, lavender, shrubs and paths around all four sides of the pavilion.
        for(z in floatArrayOf(-5.2f,7.2f)) for(x in floatArrayOf(-2.75f,2.75f)) {
            box(x,.01f,z,2.5f,.18f,1.0f,stone)
            box(x,.107f,z,2.32f,.025f,.82f,soil)
            for(i in 0..6) {
                val px=x-1f+i*.33f
                lowPolyEllipsoid(px,.30f,z,.20f,.23f,.28f,5,8,c(0x55734C))
                for(j in 0..2) {
                    val pz=z-.18f+j*.18f; val y=.50f+(i+j)%3*.045f
                    cylinderY(px,y-.10f,pz,.24f,.008f,5,c(0x697B42))
                    lowPolyEllipsoid(px,y+.035f,pz,.031f,.10f,.032f,4,6,c(if(z<0) 0x9A86A5 else 0xCEC596))
                }
            }
        }
        // Garden seats at the far end, facing back towards the table.
        for(x in floatArrayOf(-2.2f,2.2f)) {
            for(px in floatArrayOf(x-.65f,x+.65f)) box(px,.19f,10f,.065f,.52f,.42f,timber)
            for(i in 0..3) box(x,.44f,9.78f+i*.13f,1.65f,.06f,.10f,timber)
            for(px in floatArrayOf(x-.65f,x+.65f)) box(px,.68f,10.23f,.055f,.49f,.055f,timber)
            for(y in floatArrayOf(.69f,.88f)) box(x,y,10.23f,1.65f,.13f,.045f,timber)
        }
        // Hedges enclose a real garden; irregular crowns and distant hills close the horizon.
        for(side in intArrayOf(-1,1)) for(i in 0..13) {
            val a=-16.5f+i*2.5f
            lowPolyEllipsoid(a,.56f,side*17.5f,1.6f,.93f,.85f,6,10,c(if(i%2==0) 0x496440 else 0x536F46))
            lowPolyEllipsoid(side*17.5f,.56f,a,.85f,.93f,1.6f,6,10,c(if(i%2==0) 0x496440 else 0x536F46))
        }
        for(i in 0..23) {
            val angle=i*2*PI/24
            val distance=20f+(i%4)*1.8f
            tree(this,(cos(angle)*distance).toFloat(),(sin(angle)*distance).toFloat(),.8f+(i%5)*.12f,i)
        }
        tree(this,-3.5f,-10.5f,1.05f,2); tree(this,4.5f,11.5f,.9f,1)
        tree(this,-12.8f,-6.5f,1.12f,0); tree(this,-11.4f,10f,.9f,3)
        for(i in 0..15) {
            val a=i*2*PI/16
            lowPolyEllipsoid((cos(a)*42).toFloat(),-.6f,(sin(a)*42).toFloat(),10f,3.5f+(i%3),9f,5,10,c(if(i%2==0) 0x829981 else 0x92A38E))
        }
    }

    private fun lounger(b: MeshBuilder,x: Float,z: Float) = with(b) {
        val wood=c(0x8A7054); val cloth=c(0xD9CEAC)
        for(dx in floatArrayOf(-.27f,.27f)) for(dz in floatArrayOf(-.66f,.44f)) box(x+dx,.15f,z+dz,.045f,.33f,.045f,wood)
        box(x,.32f,z-.16f,.64f,.07f,1.38f,wood)
        box(x,.37f,z-.16f,.58f,.045f,1.30f,cloth)
        quad(Vec3(x-.29f,.40f,z+.51f),Vec3(x-.29f,.81f,z+.93f),
            Vec3(x+.29f,.81f,z+.93f),Vec3(x+.29f,.40f,z+.51f),cloth)
        box(x,.77f,z+.86f,.46f,.09f,.18f,c(0x6B8782))
    }

    private fun tree(b: MeshBuilder,x: Float,z: Float,scale: Float,variant: Int) = with(b) {
        cylinderY(x,1.05f*scale-.08f,z,2.1f*scale,.13f*scale,9,c(0x71604A))
        val leaves=c(if(variant%2==0) 0x4A6A44 else 0x627D4D)
        lowPolyEllipsoid(x,3.35f*scale-.08f,z,1.50f*scale,1.95f*scale,1.38f*scale,8,12,leaves)
        lowPolyEllipsoid(x-.85f*scale,2.65f*scale,z+.24f*scale,.95f*scale,1.15f*scale,.96f*scale,7,10,leaves)
        lowPolyEllipsoid(x+.90f*scale,2.98f*scale,z-.18f*scale,.95f*scale,1.22f*scale,.92f*scale,7,10,c(0x567749))
        // Contact shade under foliage, embedded just above the lawn.
        cylinderY(x,-.077f,z,.002f,1.35f*scale,20,c(0x526E40))
    }
}
