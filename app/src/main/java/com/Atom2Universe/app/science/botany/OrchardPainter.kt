package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

/** Diagnostic organ plates. Profiles, not taxon IDs, drive all geometry.
 * Main organs follow StudyForm scales; smaller comparisons are independent enlargements.
 */
internal class OrchardPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val olive=form==StudyForm.SILVERY_DRUPE
    private val coffee=form==StudyForm.AXILLARY_COFFEE
    private val peach=form==StudyForm.LANCE_STONE_FRUIT
    private val apricot=form==StudyForm.CORDATE_STONE_FRUIT
    private val citrus=form==StudyForm.WINGED_CITRUS
    private val b=BotanyInk(c)
    private val leaf=PlantLeafPainter(c,species,detailed)
    private val ink=0xff4b5139.toInt()
    private val cream=0xfff5ecd5.toInt()
    private val green=l.leafColor
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=ink,width:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)
    private fun shape(p:Path,color:Int) {
        val r=RectF();p.computeBounds(r,true)
        b.wash(p,r.left,r.top,r.right,r.bottom,intArrayOf(botanicalMix(color,cream,.35f),color,botanicalMix(color,ink,.18f)))
        b.stroke(p,ink,.7f)
    }
    private fun ellipse(x:Float,y:Float,rx:Float,ry:Float,color:Int)=shape(Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)},color)
    fun draw(view:PlantView) {when(view) {
        PlantView.HABIT -> if(coffee)coffeeHabit() else PlantTreePainter(c,species,detailed).draw()
        PlantView.LEAF -> leaves()
        PlantView.FLOWER -> flowers()
        PlantView.FRUIT -> fruits()
    }}
    private fun coffeeHabit() {
        curve(151f,279f,148f,148f,151f,31f,0xff75614b.toInt(),7f)
        for(level in 0..5)for(side in listOf(-1,1)) {
            val y=80f+level*31f;val span=35f+level*10f
            curve(150f,y,150f+side*span*.6f,y-7,150f+side*span,y+13,0xff75614b.toInt(),2.4f)
            for(j in 1..4) {
                val x=150f+side*span*j/4;val yy=y+13*j/4
                for(angle in listOf(-35f,105f))b.at(x,yy,side*angle,.19f){leaf.draw(l.leaf,115f)}
                if(level>2 && j<4)for(k in 0..2)b.oval(x+k*2,yy+4+k*3,2.3f,3f,l.fruitColor)
            }
        }
        b.at(151f,49f,-22f,.22f){leaf.draw(l.leaf,105f)}
        b.line(120f,281f,181f,281f,0x696a634a,1f)
    }
    private fun leaves() {
        b.at(if(apricot)139f else 127f,258f){
            leaf.draw(l.leaf,198f)
            if(citrus) {
                // Petiole wings lie below the articulated blade, not on the lamina.
                shape(Path().apply{moveTo(0f,-4f);quadTo(-23f,-15f,-13f,-44f);lineTo(0f,-48f);lineTo(13f,-44f);quadTo(23f,-15f,0f,-4f);close()},green)
                b.line(0f,-4f,0f,-47f,cream,.8f)
                if(detailed)for(i in 0..55) {
                    val y=-64f-i*2;val x=sin(i*2.4f)*22f
                    b.oval(x,y,.7f,.7f,0xffb0bb71.toInt())
                }
            }
        }
        if(olive) {
            val reverse=PlantLeafPainter(c,species.copy(look=l.copy(leafColor=0xffbac0ab.toInt())),detailed)
            b.at(228f,210f,12f,.66f){reverse.draw(l.leaf,198f)}
        }
        if(olive || coffee) b.at(230f,276f,0f,.48f) {
            b.line(0f,0f,0f,-115f,ink,3f)
            for(y in listOf(-32f,-89f))for(side in listOf(-1,1))b.at(0f,y,side*58f){leaf.draw(l.leaf,76f)}
            if(coffee)shape(Path().apply{moveTo(-9f,-34f);lineTo(0f,-56f);lineTo(9f,-34f);close()},0xffa49463.toInt())
        }
    }
    private fun blossom() {
        val count=if(olive)4 else 5
        // One joined outline for fused olive/coffee corollas, separate petals for Prunus/Citrus.
        if(olive || coffee) {
            val p=Path()
            for(i in 0 until count) {
                val a=2*PI*i/count-PI/2;val next=a+2*PI/count
                fun point(r:Double,t:Double)=Pair((r*cos(t)).toFloat(),(r*sin(t)).toFloat())
                val base=point(12.0,a-.30);val left=point(66.0,a-.27);val tip=point(62.0,a);val right=point(66.0,a+.27);val end=point(12.0,next-.30)
                if(i==0)p.moveTo(base.first,base.second)
                p.cubicTo(left.first,left.second,tip.first,tip.second,tip.first,tip.second)
                p.cubicTo(right.first,right.second,end.first,end.second,end.first,end.second)
            }
            p.close();shape(p,cream)
        } else repeat(count) {i->b.at(0f,0f,i*72f) {
            shape(Path().apply{moveTo(-6f,-7f);cubicTo(-35f,-27f,-28f,-62f,0f,-62f);cubicTo(28f,-62f,35f,-27f,6f,-7f);close()},l.color.ink)
            if(detailed)for(j in -1..1)curve(j*3f,-15f,j*15f,-35f,j*12f,-52f,0x657e7360,.6f)
        }}
        b.oval(0f,0f,12f,12f,0xffc7c38a.toInt())
        if(citrus)repeat(5){group->
            // Pummelo staminal filaments are joined basally in small bundles (NTBG).
            val a=2*PI*group/5
            val bx=cos(a).toFloat()*12;val by=sin(a).toFloat()*12
            b.line(bx*.35f,by*.35f,bx,by,0xffbdae80.toInt(),2.4f)
            repeat(5){i->
                val angle=a+(i-2)*.115
                val r=27f+(i%2)*4
                val x=cos(angle).toFloat()*r;val y=sin(angle).toFloat()*r
                b.line(bx,by,x,y,0xffbdae80.toInt(),1f)
                b.at(x,y,(angle*180/PI).toFloat()){ellipse(0f,0f,2.4f,3.8f,0xffd3b354.toInt())}
            }
        } else {
            val stamens=if(olive)2 else if(coffee)5 else 28
            repeat(stamens){i->
                val a=2*PI*i/stamens;val r=if(olive)17f else if(coffee)23f else 20f+(i%3)*6
                val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                b.line(x*.35f,y*.35f,x,y,0xffbdae80.toInt(),1f)
                b.at(x,y,(a*180/PI).toFloat()){ellipse(0f,0f,if(olive)5f else 2.6f,if(olive)9f else 4f,0xffd3b354.toInt())}
            }
        }
        b.line(0f,4f,0f,-9f,green,2f)
        if(coffee){b.line(0f,-9f,-5f,-15f,green,1.5f);b.line(0f,-9f,5f,-15f,green,1.5f)}
        else b.oval(0f,-9f,if(citrus)5f else 3f,3f,green)
    }
    private fun flowers() {
        b.at(150f,140f){blossom()}
        if(coffee) b.at(221f,252f) {
            shape(Path().apply{moveTo(-5f,0f);lineTo(-7f,-44f);lineTo(-29f,-62f);quadTo(0f,-49f,29f,-62f);lineTo(7f,-44f);lineTo(5f,0f);close()},cream)
            b.line(0f,-45f,0f,-69f,green,1f)
            b.line(0f,-69f,-4f,-74f,green,1f);b.line(0f,-69f,4f,-74f,green,1f)
            b.oval(0f,4f,5f,8f,green)
        }
        else if(olive) {
            b.line(75f,268f,80f,218f,green,1.5f)
            for(i in 0..4) {
                val y=254f-i*9
                for(side in listOf(-1,1)) {
                    val x=78f+side*(22-i*3)
                    b.line(78f,y,x,y-9,green,1f)
                    b.at(x,y-9,0f,.10f){blossom()}
                }
            }
        } else {
            curve(61f,270f,72f,242f,77f,209f,0xff80634c.toInt(),3f)
            b.at(77f,225f,15f,.25f){blossom()}
            b.at(65f,247f,-15f){ellipse(0f,0f,5f,8f,botanicalMix(l.color.ink,0xffb65e70.toInt(),.3f))}
        }
    }
    private fun fruitBody(x:Float,y:Float,section:Boolean,scale:Float=1f) = b.at(x,y,0f,scale) {
        val rx=if(olive || coffee)43f else 59f
        val outline=Path().apply {
            if(peach || apricot) {
                moveTo(0f,-53f)
                cubicTo(-30f,-78f,-74f,-22f,-55f,21f)
                cubicTo(-43f,51f,-12f,61f,0f,60f)
                cubicTo(21f,60f,51f,44f,57f,14f)
                cubicTo(68f,-25f,29f,-75f,0f,-53f);close()
            } else addOval(-rx,-60f,rx,60f,Path.Direction.CW)
        }
        shape(outline,l.fruitColor)
        if(peach && !section) {
            b.cloud(outline,30f,-18f,87f,intArrayOf(0xbbb84239.toInt(),0x66cf6442,0x00cf6442))
            b.stroke(outline,ink,.7f)
        }
        if(section) {
            ellipse(0f,0f,rx-4,56f,if(citrus)cream else if(olive)0xffc2bf73.toInt() else if(coffee)0xffeed8a8.toInt() else 0xfff2be65.toInt())
            if(citrus) {
                // Thick albedo surrounds twelve illustrative locules, each filled with juice vesicles.
                repeat(12) {i->b.at(0f,0f,i*30f) {
                    shape(Path().apply{moveTo(0f,-5f);lineTo(-8f,-38f);quadTo(0f,-43f,8f,-38f);close()},0xffe7bda3.toInt())
                    if(detailed)for(j in 0..5)curve(-3f+j,-10f,-5f+j*2,-24f,-5f+j*2,-35f,0xfff6dac2.toInt(),.65f)
                }}
                b.oval(0f,0f,5f,5f,cream)
            } else if(coffee) {
                for(side in listOf(-1,1)) {
                    // Two plano-convex seeds with the flat faces towards the septum.
                    shape(Path().apply{moveTo(side*2f,-37f);cubicTo(side*37f,-42f,side*39f,34f,side*2f,37f);close()},0xffb4b486.toInt())
                    curve(side*10f,-25f,side*15f,0f,side*10f,25f,0xff77774e.toInt(),1f)
                }
                b.line(0f,-40f,0f,40f,cream,2f)
            } else stone(0f,0f,if(olive).8f else 1f)
        } else {
            if(peach || apricot)curve(3f,-54f,-15f,-5f,4f,55f,0xffae743e.toInt(),1.5f)
            if(detailed)repeat(if(citrus)150 else 65){i->
                val angle=i*2.39996;val radius=sqrt((i+.5)/150)*48
                val xx=(cos(angle)*radius).toFloat();val yy=(sin(angle)*radius).toFloat()
                if(citrus)b.oval(xx,yy,.8f,.8f,0xff89904f.toInt())
                else if(peach || apricot)b.line(xx,yy,xx+1,yy-2,0x66fff2cd,.55f)
            }
            b.line(0f,-60f,4f,-71f,0xff736442.toInt(),2f)
        }
    }
    private fun stone(x:Float,y:Float,scale:Float)=b.at(x,y,0f,scale) {
        ellipse(0f,0f,if(olive)16f else 23f,37f,0xffaa8251.toInt())
        if(peach) {
            // Irregular deep pits and branching longitudinal grooves, not parallel ribs.
            for(i in 0..27) {
                val y0=-27f+(i%7)*8f;val x0=-15f+(i/7)*10f+sin(i*3.1f)*2f
                if(x0*x0/400f+y0*y0/1100f<1) {
                    b.at(x0,y0,sin(i*2f)*28f){
                        b.oval(0f,0f,1.5f,3.7f,0xff76502f.toInt())
                        curve(-2f,-4f,-5f,0f,-2f,5f,0xffd2a466.toInt(),.8f)
                    }
                }
            }
            curve(0f,-34f,5f,-2f,-1f,34f,0xff78542f.toInt(),1.3f)
        } else {
            curve(0f,-34f,-5f,0f,0f,34f,0xff735c3c.toInt(),1.2f)
            if(detailed)repeat(25){i->b.oval(sin(i*2.3f)*11f,cos(i*1.7f)*27f,.7f,.7f,0xff8a6c45.toInt())}
        }
    }
    private fun fruits() {
        fruitBody(90f,139f,false)
        fruitBody(225f,157f,true,.88f)
        if(coffee)b.at(106f,253f,-22f) {
            ellipse(0f,0f,17f,25f,0xffb4b486.toInt())
            curve(0f,-21f,-9f,0f,1f,21f,0xff696c44.toInt(),2f)
        } else if(!citrus)stone(160f,254f,.65f)
    }
}
