package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Reusable cup, funnel and raised composite-head profiles; never dispatch on a species ID. */
internal class PlantAdditionalFlowerPainter(private val c:Canvas,private val l:PlantLook,private val detailed:Boolean) {
    private val b=BotanyInk(c)
    private val d=l.flowerDetail
    private val color=l.color.ink
    private val cream=0xffffe7ad.toInt()
    private val edge=botanicalMix(color,0xff674239.toInt(),.5f)
    private fun petal(q:Path) {
        b.wash(q,-35f,-50f,35f,30f,intArrayOf(botanicalMix(color,cream,.5f),color,edge))
        b.stroke(q,edge,.7f)
        if(detailed){c.save();c.clipPath(q)
            for(i in -5..5)b.stroke(Path().apply{moveTo(0f,29f);quadTo(i*7f,-8f,i*9f,-88f)},edge,.45f)
            c.restore()}
    }
    fun corolla() {
        if(d.corolla==CorollaForm.GOLDEN_CUP) {
            b.oval(0f,39f,17f,5f,l.leafColor)
            // Four broad petals overlap in a shallow side view; no persistent poppy stigmatic disc.
            for(i in 0..3)b.at(0f,24f,(i-1.5f)*21f) {
                val q=Path().apply{moveTo(-3f,16f);cubicTo(-32f,1f,-67f,-75f,-29f,-81f)
                    quadTo(0f,-95f,28f,-79f);cubicTo(65f,-69f,31f,3f,3f,16f);close()}
                petal(q)
            }
            repeat(d.stamens){i->val a=i*2.399963;val r=12f+sqrt(i.toFloat()/d.stamens)*17f
                val x=cos(a).toFloat()*r;val y=-8f+sin(a).toFloat()*r*.5f
                b.line(0f,20f,x,y,0xffe2b246.toInt(),.75f);b.oval(x,y,2f,3f,0xffc78e1e.toInt())}
            for(i in -1..2)b.line(0f,18f,i*2f,-11f,0xffd3c85c.toInt(),1f)
        } else {
            // One continuous five-lobed corolla, with ten exserted stamens and a longer style.
            val q=Path()
            for(i in 0..200){val a=i*2*PI/200;val r=47f+13f*cos(a*5).toFloat()
                val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                if(i==0)q.moveTo(x,y)else q.lineTo(x,y)}
            q.close();petal(q)
            b.oval(0f,7f,13f,18f,botanicalMix(color,0xff4d3157.toInt(),.43f))
            for(i in 0 until 10){val x=(i-4.5f)*6;val y=22f+sin(i*.8f)*10
                b.stroke(Path().apply{moveTo(0f,9f);quadTo(x*.7f,24f,x,y)},0xffe3c6d3.toInt(),.8f)
                b.oval(x,y,2f,3f,0xff975834.toInt())}
            b.stroke(Path().apply{moveTo(0f,11f);quadTo(16f,38f,36f,36f)},0xffefdebc.toInt(),1.4f)
            b.oval(36f,36f,2.5f,2f,0xff859258.toInt())
            repeat(20){i->b.oval(sin(i*2.4f)*14,-19f-(i%5)*4f,1.3f,1.7f,0xffb09341.toInt())}
        }
    }

    fun raisedHead() {
        val height=d.discHeight
        // Elliptical insertion ring places rays behind and below a raised disc in oblique view.
        for(i in 0 until d.rays) {
            val a=i*2*PI/d.rays;val x=cos(a).toFloat();val y=sin(a).toFloat()
            val sx=x*25f;val sy=y*12f
            val ex=x*83f;val ey=y*40f+d.rayDroop
            val nx=-y*7f;val ny=x*5f
            val q=Path().apply{moveTo(sx,sy);cubicTo(sx+nx+x*30,sy+ny+8,ex+nx,ey+ny,ex,ey)
                cubicTo(ex-nx,ey-ny,sx-nx+x*30,sy-ny+8,sx,sy);close()}
            petal(q);b.stroke(Path().apply{moveTo(sx,sy);quadTo(x*56,sy+18,ex,ey)},edge,.5f)
        }
        val dome=Path().apply{moveTo(-30f,5f);cubicTo(-28f,-height*.5f,-17f,-height,0f,-height)
            cubicTo(17f,-height,28f,-height*.5f,30f,5f);quadTo(0f,26f,-30f,5f);close()}
        b.wash(dome,-23f,-height,28f,15f,intArrayOf(botanicalMix(d.discColor,cream,.3f),d.discColor,0xff3b3028.toInt()))
        b.stroke(dome,0xff503c2b.toInt(),.8f)
        c.save();c.clipPath(dome)
        repeat(if(detailed)240 else 70){i->val n=if(detailed)240f else 70f;val a=i*2.399963;val r=sqrt(i/n)
            val x=cos(a).toFloat()*31*r;val y=-height*.40f+sin(a).toFloat()*(height*.68f+5)*r
            b.line(x,y,x*.98f,y-4.5f,botanicalMix(d.discColor,cream,.55f),.8f)
            b.oval(x,y,1.1f,1.2f,0xff58402c.toInt())}
        c.restore()
    }
}
