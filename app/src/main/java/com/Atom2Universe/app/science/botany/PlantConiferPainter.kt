package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Cord-like foliage, peltate cone scales and exserted bracts are independent drawing traits. */
internal class PlantConiferPainter(private val c:Canvas,private val l:PlantLook,private val detailed:Boolean) {
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val dark=botanicalMix(green,0xff243c30.toInt(),.55f)
    private val cream=0xffddd0a4.toInt()

    fun awlShoot(length:Float) {
        fun cord(h:Float) {
            b.line(0f,0f,0f,-h,green,h*.04f)
            val count=if(detailed)24 else 13
            for(i in 0 until count)for(side in listOf(-1,0,1)) {
                val y=-h*(i+.3f)/count
                val w=h*.039f
                val q=Path().apply{moveTo(0f,y+h*.025f);quadTo(side*w*1.2f,y,side*w*1.55f,y-h*.092f)
                    quadTo(side*w*.3f,y-h*.053f,0f,y-h*.026f);close()}
                val shade=if(side<0)botanicalMix(green,cream,.27f)else green
                b.fill(q,shade);b.stroke(q,dark,h*.003f)
                if(side==0)b.line(0f,y+h*.013f,w*.26f,y-h*.079f,botanicalMix(green,cream,.3f),h*.009f)
            }
        }
        b.line(0f,0f,0f,-length*.93f,l.bark,2f)
        for(i in 1..4)for(side in listOf(-1,1))b.at(0f,-length*i*.16f,side*(30f+i*3)) {cord(length*(.41f-i*.045f))}
        cord(length*.94f)
    }

    /** Coordinates relative to the flower plate centre. Terminal pollen cones are shown enlarged. */
    fun pollen() {
        for(i in 0..2)b.at(0f,98f,(i-1)*24f,.75f) {
            val h=if(i==1)223f else 171f
            if(l.leaf==LeafShape.SCALE)awlShoot(h) else flatShoot(h)
            b.line(0f,-h*.93f,0f,-h-7f,green,2f)
            b.at(0f,-h-15f) {
                b.oval(0f,0f,10f,16f,0xffbba566.toInt())
                repeat(6){j->for(k in -1..1)b.oval(k*4.3f,-11f+j*4.4f+(k%2)*1.2f,3.7f,2.8f,if(j%2==0)0xffd7bf7b.toInt()else 0xffbda066.toInt())}
            }
        }
        if(detailed)b.at(81f,75f,0f,1.5f) {
            b.oval(0f,0f,11f,18f,0xffbba16a.toInt())
            repeat(7){i->for(j in -1..1){b.oval(j*5f,-13f+i*4.2f,4f,2.5f,0xffdcc57e.toInt());b.line(j*5f-2,-12f+i*4.2f,j*5f+2,-12f+i*4.2f,0xff8a784d.toInt(),.55f)}}
        }
    }

    private fun flatShoot(h:Float) {
        b.line(0f,0f,0f,-h,l.bark,2f)
        for(i in 1..18)for(side in listOf(-1,1)) {
            val y=-h*i/19f;val reach=h*.22f*sin(PI*i/20).toFloat()
            val q=Path().apply{moveTo(0f,y);quadTo(side*reach*.4f,y-7f,side*reach,y-12f)
                quadTo(side*reach*.6f,y-2f,0f,y+2f);close()}
            b.fill(q,green);b.stroke(q,dark,.5f)
        }
    }

    /** Peltate scales of redwoods differ structurally from overlapping Pinaceae cone scales. */
    fun cone() {
        val type=l.fruitDetail.cone
        val douglas=type==ConeForm.DOUGLAS
        val h=if(douglas)77f else if(type==ConeForm.SEQUOIA)66f else 48f
        val w=if(douglas)33f else 42f
        b.at(-61f,-87f,84f,.54f){if(l.leaf==LeafShape.SCALE)awlShoot(198f)else flatShoot(198f)}
        b.stroke(Path().apply{moveTo(-7f,-95f);quadTo(-10f,-65f,0f,-h+18f)},l.bark,2.3f)
        b.at(0f,16f) {
            val body=Path().apply{addOval(-w,-h,w,h,Path.Direction.CW)}
            b.wash(body,-w,-h,w,h,intArrayOf(0xffb99a6a.toInt(),l.fruitColor,0xff5e4931.toInt()))
            c.save();c.clipPath(body)
            val rows=if(douglas)12 else if(type==ConeForm.SEQUOIA)8 else 5
            for(i in 0 until rows) {
                val y=-h+7f+i*(2*h-10)/rows
                val rw=w*sqrt((1f-(y/h).pow(2)).coerceAtLeast(0f))
                for(j in -2..2)b.at(j*rw*.44f+(i%2)*5f,y) {
                    val sw=rw*.30f
                    val q=if(douglas)Path().apply{moveTo(-sw,0f);quadTo(-sw,12f,0f,15f);quadTo(sw,12f,sw,0f);close()}
                    else Path().apply{moveTo(-sw,0f);lineTo(-sw*.45f,-6f);lineTo(sw*.6f,-5f);lineTo(sw,3f)
                        lineTo(sw*.48f,12f);lineTo(-sw*.5f,13f);close()}
                    b.wash(q,-sw,0f,sw,9f,intArrayOf(0xffbd9b69.toInt(),l.fruitColor,0xff795537.toInt()))
                    b.stroke(q,0xff574532.toInt(),.8f)
                    if(!douglas) {
                        b.line(-sw*.7f,3f,sw*.72f,4f,0xffd2b583.toInt(),1f)
                        b.oval(0f,5f,2.4f,1.8f,0xff68513c.toInt())
                        if(detailed)for(k in -2..2)b.line(k*sw*.3f,6f,k*sw*.37f,10f,0xff95734a.toInt(),.5f)
                    } else {
                        val bract=Path().apply{moveTo(-2f,2f);lineTo(-6f,10f);lineTo(-8f,19f);lineTo(-2f,15f)
                            lineTo(0f,27f);lineTo(2f,15f);lineTo(8f,19f);lineTo(6f,10f);lineTo(2f,2f);close()}
                        b.fill(bract,0xffc7ac77.toInt());b.stroke(bract,0xff755b39.toInt(),.65f)
                    }
                }
            }
            c.restore();b.stroke(body,0xff584833.toInt(),.8f)
        }
        if(detailed)b.at(82f,82f,-25f,.65f) {
            // Isolated scale / bract makes the diagnostic architecture legible at thumbnail size too.
            if(douglas) {
                val q=Path().apply{moveTo(-4f,-32f);lineTo(-17f,9f);lineTo(-26f,30f);lineTo(-5f,16f)
                    lineTo(0f,52f);lineTo(6f,16f);lineTo(26f,30f);lineTo(17f,9f);lineTo(4f,-32f);close()}
                b.fill(q,0xffc5a776.toInt());b.stroke(q,0xff785e3e.toInt(),1f)
            } else {
                val q=Path().apply{moveTo(-29f,-9f);lineTo(-13f,-26f);lineTo(21f,-23f);lineTo(31f,4f);lineTo(12f,23f);lineTo(-22f,19f);close()}
                b.fill(q,0xffaf8755.toInt());b.stroke(q,0xff705033.toInt(),1f)
                b.line(-23f,-1f,23f,2f,0xffdec18b.toInt(),2f);b.oval(0f,5f,5f,3f,0xff735434.toInt())
            }
        }
    }
}
