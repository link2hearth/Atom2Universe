package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

/** Original Solanaceae plates. Continuous fused corollas, anther cones and fruit sections
 * are separate from foliage. Profiles are reusable; no species ID is consulted.
 */
internal class NightshadePainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val f=requireNotNull(l.study)
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val ink=0xff40503b.toInt()
    private val pale=0xffd4d9a2.toInt()
    private val cream=0xfff5e8bc.toInt()
    private val gold=0xffd5ae36.toInt()
    private val compound=f==StudyForm.PINNATE_BERRY || f==StudyForm.TUBEROUS_BERRY
    private fun curve(x:Float,y:Float,xx:Float,yy:Float,xxx:Float,yyy:Float,color:Int=green,w:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(xx,yy,xxx,yyy)},color,w)
    private fun shade(p:Path,color:Int) {
        val r=RectF();p.computeBounds(r,true)
        b.wash(p,r.left,r.top,r.right,r.bottom,intArrayOf(botanicalMix(color,cream,.28f),color,botanicalMix(color,ink,.40f)))
        b.stroke(p,botanicalMix(color,ink,.65f),.65f)
    }
    private fun ellipse(x:Float,y:Float,rx:Float,ry:Float,color:Int)=shade(Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)},color)

    fun draw(view:PlantView) {
        when(view) {
            PlantView.HABIT -> habit()
            PlantView.LEAF -> leaves()
            PlantView.FLOWER -> flowers()
            PlantView.FRUIT -> fruits()
        }
    }

    private fun habit() {
        b.oval(150f,285f,104f,4f,0x166a6145)
        val base=if(f==StudyForm.TUBEROUS_BERRY)251f else 278f
        for(i in -1..1) {
            val x=150f+i*55f
            val top=50f+abs(i)*29f
            curve(150f,base,150f+i*22f,153f,x,top,green,2.4f)
            for(j in 0..4) {
                val t=(j+1)/6f;val xx=150+(x-150)*t;val yy=base+(top-base)*t
                val side=if((j+i)%2==0)-1 else 1
                b.at(xx,yy,side*57f,if(compound).33f else .31f){wholeLeaf(198f)}
                if(detailed)for(k in 0..2)b.line(xx,yy-k*4,xx+2.4f,yy-k*4-2,pale,.4f)
                if(j==3 && i!=0)b.at(xx-side*9,yy+20f,side*12f,if(f==StudyForm.SMALL_BERRY_CYME).18f else .23f){
                    if(f==StudyForm.SMALL_BERRY_CYME)cluster(false) else fruit(false)
                }
            }
            curve(x,top+20f,x+21f,top+6f,x+19f,top-2f,green,1f)
            if(f==StudyForm.SMALL_BERRY_CYME)b.at(x+19f,top-2f,0f,.30f){cluster(true)}
            else b.at(x+19f,top-2f,0f,.16f){corolla()}
            if(compound)b.at(x+2f,top+9f,24f,.13f){corolla()}
        }
        if(f==StudyForm.TUBEROUS_BERRY) {
            // Cutaway soil horizon, with tubers on lateral stolons rather than on roots.
            b.line(55f,254f,251f,254f,0xffa99a76.toInt(),.8f)
            for(s in listOf(-1,1)) {
                curve(150f,249f,150f+s*14,271f,150f+s*51,271f,l.bark,1.1f)
                b.at(150f+s*57,273f,s*12f,.34f){tuber()}
            }
        }
    }

    private fun leaves() {
        b.at(154f,265f,2f){wholeLeaf(198f)}
        // A side shoot makes alternate insertion visible independently from the main leaf.
        b.at(47f,270f,0f,.48f){
            b.line(0f,0f,0f,-192f,green,2.5f)
            for(j in 0..3)b.at(0f,-22f-j*42f,if(j%2==0)-52f else 52f,.44f){wholeLeaf(145f)}
        }
        if(f==StudyForm.LOBED_BERRY && detailed)b.at(253f,261f){
            // Enlarged stellate hair, characteristic of eggplant pubescence.
            b.line(0f,0f,0f,-13f,ink,1.2f)
            for(i in 0..6){val a=i*2*PI/7;b.line(0f,-13f,cos(a).toFloat()*17,-13f+sin(a).toFloat()*17,ink,.8f)}
        }
    }

    private fun wholeLeaf(length:Float) {
        if(!compound){
            val lobing=when(f){StudyForm.LOBED_BERRY->.18f;StudyForm.SMALL_BERRY_CYME->.08f;else->0f}
            blade(length,if(f==StudyForm.LOBED_BERRY).32f else .25f,lobing,f==StudyForm.LOBED_BERRY)
            return
        }
        b.line(0f,0f,0f,-length*.77f,green,2f)
        b.line(-.4f,0f,-.4f,-length*.77f,pale,.6f)
        val tomato=f==StudyForm.PINNATE_BERRY
        for(j in 0..2)for(s in listOf(-1,1)) {
            val y=-length*(.23f+j*.21f)
            b.at(0f,y+s*length*.009f,s*(57f-j*4f)){
                blade(length*(.31f-j*.035f),.30f,if(tomato).27f else .02f,true)
            }
            if(j<2)b.at(0f,y-length*.10f,s*73f){blade(length*.12f,.27f,if(tomato).17f else 0f,true)}
        }
        b.at(0f,-length*.72f){blade(length*.28f,.30f,if(tomato).24f else .02f,true)}
    }

    /** Blade length includes petiole. Broad rounded lobes are kept distinct from small teeth. */
    private fun blade(length:Float,width:Float,lobing:Float,hairy:Boolean) {
        val h=length*.83f
        b.line(0f,0f,0f,-length*.20f,green,1.5f)
        b.at(0f,-length*.17f){
            val p=Path().apply{
                moveTo(0f,0f)
                for(s in listOf(-1,1))for(i in 0..100){
                    val t=if(s<0)i/100f else 1-i/100f
                    val envelope=sin(PI*t).toFloat().coerceAtLeast(0f).pow(.80f)
                    val lobes=1-lobing*.5f+lobing*.5f*cos(t*7*PI+s*.18f).toFloat()
                    lineTo(s*h*width*envelope*lobes,-h*t)
                };close()
            }
            shade(p,green)
            c.save();c.clipPath(p)
            b.line(0f,0f,0f,-h,pale,.85f)
            for(j in 1..7)for(s in listOf(-1,1)) {
                val t=j/9f;val w=h*width*sin(PI*t).toFloat()*.88f
                curve(0f,-h*t,s*w*.6f,-h*(t+.07f),s*w,-h*(t+.13f),pale,.50f)
                if(detailed)for(k in 1..2)curve(s*w*k/3,-h*(t+.04f*k),s*w*(k+.3f)/3,-h*(t+.08f*k),s*w*(k+.65f)/3,-h*(t+.11f*k),0x707e9468,.35f)
            }
            if(hairy && detailed)for(j in 0..45){
                val x=sin(j*2.399963).toFloat()*h*.31f;val y=-h*(j+.5f)/46
                b.line(x,y,x+1.5f,y-2.8f,0x99d0d4ad.toInt(),.45f)
                if(f==StudyForm.LOBED_BERRY){b.line(x-1.6f,y-1.4f,x+1.7f,y-1.4f,0x99d0d4ad.toInt(),.4f)}
            }
            c.restore()
        }
    }

    private fun flowers() {
        curve(144f,275f,128f,220f,150f,169f,green,2.2f)
        b.at(150f,139f){corolla()}
        if(f==StudyForm.HOLLOW_BERRY)b.at(235f,243f,28f,.40f){corolla()}
        else b.at(54f,249f,0f,.42f){cluster(true)}
    }

    private fun corolla() {
        val pepper=f==StudyForm.HOLLOW_BERRY
        val broad=f==StudyForm.TUBEROUS_BERRY || f==StudyForm.LOBED_BERRY
        // One continuous outline: the five petals are fused at the base.
        val p=Path().apply{
            for(i in 0..250){val a=-PI/2+i*2*PI/250
                val phase=(i/250f*5)%1f
                val peak=if(pepper)(.5+.5*cos((a+PI/2)*5)).toFloat() else 1f-2f*min(phase,1f-phase)
                val r=(if(broad)37f else 24f)+(if(broad)25f else 38f)*peak.pow(.85f)
                val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                if(i==0)moveTo(x,y)else lineTo(x,y)
            };close()
        }
        for(i in 0..4)b.at(0f,0f,i*72f+36f){
            shade(Path().apply{moveTo(-5f,4f);quadTo(-15f,-25f,0f,-46f);quadTo(15f,-25f,5f,4f);close()},green)
        }
        shade(p,l.color.ink)
        c.save();c.clipPath(p)
        for(i in 0..4)b.at(0f,0f,i*72f){for(j in -2..2)curve(0f,0f,j*9f,-34f,j*8f,-67f,botanicalMix(l.color.ink,ink,.22f),.55f)}
        c.restore()
        if(pepper) {
            for(i in 0..4)b.at(0f,0f,i*72f){
                b.line(0f,-4f,0f,-19f,pale,1.1f)
                ellipse(0f,-20f,3.4f,6f,0xff827b8e.toInt());b.line(0f,-25f,0f,-15f,ink,.45f)
            }
        } else {
            val long=f==StudyForm.PINNATE_BERRY
            for(i in -2..2) {
                val x=i*5f;val tipX=x*.42f;val tipY=(if(long)-32f else -21f)+abs(i)*1.2f
                val a=Path().apply{moveTo(x-3.5f,13f);quadTo(x-6f,-4f,tipX-1.8f,tipY)
                    quadTo(tipX,tipY-2f,tipX+1.8f,tipY);quadTo(x+6f,-4f,x+3.5f,13f);close()}
                shade(a,gold);b.line(x,10f,tipX,tipY+3f,0xffae8130.toInt(),.6f)
                if(!long)b.oval(tipX,tipY+1f,1.1f,.8f,ink)
            }
        }
        b.line(0f,5f,2f,if(pepper)-11f else -35f,green,1.5f)
        b.oval(2f,if(pepper)-12f else -36f,2f,1.3f,green)
    }

    private fun cluster(flowers:Boolean) {
        b.line(-13f,29f,0f,-19f,green,1.4f)
        for(i in 0..4) {
            val x=(i-2)*20f;val y=-43f+abs(i-2)*16
            curve(-2f,-11f,x,y-15f,x,y,green,1f)
            if(flowers)b.at(x,y,0f,.22f){corolla()}
            else {ellipse(x,y+12f,11f,11f,l.fruitColor);b.at(x,y+2f,0f,.22f){calyx(5)}}
        }
    }

    private fun fruits() {
        when(f) {
            StudyForm.PINNATE_BERRY -> {b.at(108f,173f){fruit(false)};b.at(232f,217f,0f,.68f){fruit(true)}}
            StudyForm.TUBEROUS_BERRY -> {
                b.at(107f,160f){fruit(false)}
                b.at(236f,241f,0f,.82f){tuber()}
                curve(205f,214f,219f,212f,225f,223f,l.bark,1.4f)
            }
            StudyForm.LOBED_BERRY,StudyForm.HOLLOW_BERRY -> {
                b.at(102f,170f){fruit(false)};b.at(234f,218f,0f,.70f){fruit(true)}
            }
            StudyForm.SMALL_BERRY_CYME -> {b.at(107f,174f){fruit(false)};b.at(238f,224f,0f,.8f){cluster(false)}}
            else -> error("Unsupported nightshade profile")
        }
    }

    private fun calyx(lobes:Int) {
        for(i in 0 until lobes)b.at(0f,0f,i*360f/lobes){
            shade(Path().apply{moveTo(-6f,4f);quadTo(-14f,-11f,0f,-34f);quadTo(11f,-10f,6f,4f);close()},green)
            b.line(0f,0f,0f,-28f,pale,.55f)
        }
    }

    private fun fruit(cut:Boolean) {
        when(f) {
            StudyForm.LOBED_BERRY -> {
                val p=Path().apply{moveTo(0f,-60f);cubicTo(-26f,-58f,-48f,-6f,-31f,37f)
                    cubicTo(-17f,71f,26f,65f,35f,33f);cubicTo(42f,1f,29f,-45f,0f,-60f);close()}
                shade(p,if(cut)cream else l.fruitColor)
                if(cut){
                    for(s in listOf(-1,1))for(i in 0..13){val y=-31f+i*6f;val x=s*(9f+sin(i*.5f)*6)
                        b.oval(x,y,2.2f,3f,0xffa7884d.toInt())}
                }else curve(-13f,-43f,-39f,5f,-19f,34f,0x508e719b,4f)
                b.at(0f,-51f,0f,.78f){calyx(5)}
                curve(0f,-57f,10f,-74f,20f,-81f,green,4f)
            }
            StudyForm.HOLLOW_BERRY -> pepper(cut)
            else -> {
                val tomato=f==StudyForm.PINNATE_BERRY
                ellipse(0f,0f,60f,60f,if(cut)0xffe5764d.toInt() else l.fruitColor)
                if(cut) {
                    // Two locules separated by a fleshy septum; a small-fruited form is illustrated.
                    for(s in listOf(-1,1)) {
                        ellipse(s*25f,0f,21f,43f,0xffb77f3f.toInt())
                        for(i in 0..10){val a=i*2*PI/11;val x=s*25+cos(a).toFloat()*13;val y=sin(a).toFloat()*32
                            b.at(x,y,i*32f){b.oval(0f,0f,2.5f,4f,cream)}}
                    }
                    b.line(0f,-46f,0f,46f,0xfff0b078.toInt(),9f)
                } else {
                    if(tomato)for(i in -2..2)curve(i*9f,-50f,i*22f,-8f,i*12f,49f,0x30882e2a,1.3f)
                    b.at(0f,-43f,0f,if(f==StudyForm.SMALL_BERRY_CYME).43f else .8f){calyx(5)}
                    curve(0f,-47f,1f,-83f,17f,-87f,green,2.4f)
                    b.oval(-22f,-18f,10f,5f,if(f==StudyForm.SMALL_BERRY_CYME)0x257e8092 else 0x35fff6d0)
                    if(f==StudyForm.TUBEROUS_BERRY && detailed)for(i in 0..70){val a=i*2.399963;val r=sqrt(i/71f)*51
                        b.oval(cos(a).toFloat()*r,sin(a).toFloat()*r,1f,1f,0x40879f61)}
                }
            }
        }
    }

    private fun pepper(cut:Boolean) {
        val p=Path().apply{moveTo(0f,-51f);cubicTo(-24f,-78f,-58f,-45f,-45f,-15f)
            cubicTo(-40f,13f,-41f,58f,-21f,57f);quadTo(-7f,52f,0f,60f);quadTo(10f,54f,25f,58f)
            cubicTo(48f,50f,44f,11f,49f,-18f);cubicTo(58f,-52f,19f,-74f,0f,-51f);close()}
        shade(p,l.fruitColor)
        if(cut) {
            val cavity=Path().apply{moveTo(0f,-39f);cubicTo(-29f,-60f,-41f,-36f,-34f,-7f)
                lineTo(-21f,44f);quadTo(0f,29f,22f,46f);lineTo(36f,-17f);quadTo(37f,-52f,0f,-39f);close()}
            shade(cavity,0xff805834.toInt())
            shade(Path().apply{moveTo(-10f,-48f);quadTo(-21f,-15f,-9f,14f);quadTo(2f,24f,12f,11f)
                quadTo(22f,-23f,10f,-48f);close()},cream)
            for(i in 0..20){val a=i*2.399963;val r=sqrt(i/21f)
                b.oval(cos(a).toFloat()*12*r,-17f+sin(a).toFloat()*26*r,2.5f,3f,0xffe2c27c.toInt())}
            curve(0f,8f,2f,29f,20f,42f,cream,2f)
        }else for(s in listOf(-1,1))curve(s*14f,-38f,s*22f,17f,s*14f,50f,0x608d382d,1.5f)
        ellipse(0f,-53f,15f,7f,green);curve(0f,-53f,-4f,-84f,13f,-85f,green,5f)
    }

    private fun tuber() {
        shade(Path().apply{moveTo(-45f,-5f);cubicTo(-41f,-31f,13f,-35f,37f,-13f)
            cubicTo(61f,15f,5f,38f,-24f,21f);quadTo(-49f,13f,-45f,-5f);close()},0xffbb9869.toInt())
        for(i in 0..8){val a=i*2.4;val x=cos(a).toFloat()*31;val y=sin(a).toFloat()*16
            curve(x-3,y,x,y+2,x+3,y,l.bark,.9f);b.oval(x,y-1,1.1f,.7f,l.bark)}
        if(detailed)for(i in 0..36){val a=i*2.399963;val r=sqrt(i/37f)
            b.oval(cos(a).toFloat()*36*r,sin(a).toFloat()*20*r,.65f,.55f,0x60745b3b)}
    }
}
