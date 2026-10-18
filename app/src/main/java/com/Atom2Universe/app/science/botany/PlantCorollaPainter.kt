package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Reusable corolla architectures: organ numbers and symmetry, never species identifiers. */
internal class PlantCorollaPainter(private val c:Canvas,private val l:PlantLook,private val detailed:Boolean) {
    private val b=BotanyInk(c)
    private val ink=0xff665044.toInt()
    private val cream=0xfffffae6.toInt()
    private val green=l.leafColor
    private val purple=0xff81386e.toInt()

    fun draw()=when(l.flowerDetail.corolla) {
        CorollaForm.BAOBAB -> baobab()
        CorollaForm.TAMARIND -> caesalpinioid(true)
        CorollaForm.SENNA -> caesalpinioid(false)
        CorollaForm.CORONA -> corona()
        CorollaForm.HIBISCUS -> hibiscus()
        CorollaForm.SESAME -> sesame()
        else -> Unit
    }

    private fun petal(q:Path,color:Int,veins:Int=0xffbaac85.toInt()) {
        b.wash(q,-40f,-60f,40f,25f,intArrayOf(botanicalMix(color,cream,.7f),color,botanicalMix(color,ink,.18f)))
        b.stroke(q,botanicalMix(color,ink,.45f),.7f)
        if(detailed){c.save();c.clipPath(q)
            for(i in -3..3)b.stroke(Path().apply{moveTo(0f,6f);cubicTo(i*4f,-18f,i*11f,-39f,i*13f,-85f)},veins,.55f)
            c.restore()
        }
    }
    private fun blade(w:Float,h:Float)=Path().apply {
        moveTo(0f,5f);cubicTo(-w,-10f,-w*1.12f,-h*.85f,-w*.25f,-h)
        quadTo(w*.08f,-h-5,w*.40f,-h+2);cubicTo(w*1.2f,-h*.77f,w,-10f,0f,5f);close()
    }

    private fun baobab() {
        b.stroke(Path().apply{moveTo(9f,-128f);quadTo(-16f,-95f,0f,-48f)},green,3f)
        // Five reflexed petals surround the base of the pendant staminal column.
        for(i in 0..4)b.at(0f,-32f,(i-2)*59f){
            val q=blade(33f,72f)
            petal(q,cream,0xffcecbb1.toInt())
        }
        b.stroke(Path().apply{moveTo(0f,-30f);quadTo(-8f,5f,0f,35f)},0xffdad3ac.toInt(),8f)
        b.line(-1f,-24f,-2f,27f,cream,2f)
        repeat(if(detailed)95 else 40){i->
            val a=i*2.399963;val r=sqrt((i+.5f)/95)*65
            val x=cos(a).toFloat()*r;val y=54f+sin(a).toFloat()*r*.55f
            b.stroke(Path().apply{moveTo(0f,10f);quadTo(x*.4f,45f,x,y)},0xffc9c298.toInt(),.75f)
            b.oval(x,y,2f,1.5f,0xffbea951.toInt())
        }
        b.stroke(Path().apply{moveTo(3f,-12f);quadTo(9f,50f,4f,100f)},0xffafa780.toInt(),1.5f)
        repeat(5){i->b.line(4f,97f,(i-2)*3f+4,104f,0xff8e855f.toInt(),1f)}
    }

    private fun caesalpinioid(tamarind:Boolean) {
        if(tamarind)repeat(4){i->b.at(0f,0f,i*90f+45f){petal(blade(15f,55f),0xffdce0a1.toInt())}}
        val angles=if(tamarind)listOf(-60f,0f,60f)else listOf(0f,72f,144f,216f,288f)
        for((i,a) in angles.withIndex())b.at(0f,0f,a){
            val h=if(tamarind)if(i==1)79f else 68f else if(i==0)65f else 72f
            petal(blade(if(tamarind)29f else 32f,h),if(tamarind)0xffede39e.toInt() else 0xffeac637.toInt(),
                if(tamarind)0xffa75b57.toInt() else 0xffc39c35.toInt())
        }
        if(tamarind)for(side in listOf(-1,1))b.at(side*8f,8f,side*130f,.20f){petal(blade(14f,35f),0xffe1bf72.toInt())}
        val count=if(tamarind)3 else 7
        repeat(count){i->val x=(i-(count-1)/2f)*if(tamarind)15f else 8f
            val y=if(tamarind)46f-abs(x)*.25f else 17f+abs(x)*.85f
            b.stroke(Path().apply{moveTo(0f,3f);quadTo(x*.4f,y+10,x,y)},0xffad874d.toInt(),1.3f)
            b.at(x,y,if(tamarind)70f else x*1.2f){
                b.stroke(Path().apply{moveTo(-2f,7f);quadTo(5f,-2f,0f,-11f)},0xff765b30.toInt(),4f)
                b.line(0f,5f,1f,-8f,0xffd6b654.toInt(),.8f)
            }
        }
        if(!tamarind)repeat(3){i->b.oval((i-1)*10f,-8f,2f,4f,0xff82693c.toInt())}
        b.stroke(Path().apply{moveTo(2f,0f);quadTo(-16f,46f,-33f,49f)},green,1.7f)
    }

    private fun corona() {
        repeat(5){i->b.at(0f,0f,i*72f){
            val q=Path().apply{moveTo(-10f,7f);quadTo(-36f,-20f,0f,-82f);quadTo(36f,-20f,10f,7f);close()}
            petal(q,0xffe8d8df.toInt(),0xffb3a2b2.toInt())
            val mark=Path().apply{moveTo(-7f,0f);quadTo(-24f,-20f,0f,-67f);quadTo(24f,-20f,7f,0f);close()}
            b.wash(mark,-15f,-30f,13f,-10f,intArrayOf(0xffac6e9d.toInt(),purple));b.stroke(mark,0xff8b587e.toInt(),.4f)
        }}
        // Five fleshy hoods belong to the corona; they are not a second whorl of petals.
        repeat(5){i->b.at(0f,0f,i*72f+36f){
            val q=Path().apply{moveTo(-6f,1f);cubicTo(-19f,-13f,-16f,-35f,0f,-35f)
                cubicTo(16f,-35f,19f,-13f,6f,1f);quadTo(0f,8f,-6f,1f);close()}
            petal(q,cream);b.stroke(Path().apply{moveTo(0f,-4f);quadTo(-5f,-19f,0f,-26f)},0xffb6a4b7.toInt(),1.2f)
        }}
        b.oval(0f,0f,10f,10f,0xffa089ab.toInt())
        repeat(5){i->val a=i*2*PI/5;b.line(0f,0f,cos(a).toFloat()*10,sin(a).toFloat()*10,cream,.8f)}
    }

    private fun hibiscus() {
        repeat(5){i->b.at(0f,0f,i*72f+12){
            petal(blade(47f,82f),0xfff2e8ac.toInt())
            val eye=Path().apply{moveTo(0f,9f);quadTo(-24f,-7f,-18f,-27f);quadTo(0f,-34f,22f,-24f);quadTo(26f,-7f,0f,9f);close()}
            b.fill(eye,0xff5e2946.toInt())
        }}
        b.line(0f,4f,12f,-38f,0xffcfaa4a.toInt(),8f)
        repeat(30){i->val y=2f-i*1.2f;val x=-y*.28f+(if(i%2==0)-1 else 1)*6f
            b.oval(x,y,2.3f,2f,0xffe3b03d.toInt())}
        repeat(5){i->val x=10f+(i-2)*6f;b.line(10f,-31f,x,-44f,0xffb59759.toInt(),1.1f);b.oval(x,-44f,2.8f,2.6f,purple)}
    }

    private fun sesame() {
        b.line(-60f,96f,-23f,36f,green,2.2f)
        // Oblique tubular corolla, with a larger lower lip and dark nectar-guide lines.
        b.at(-18f,37f,27f){
            val q=Path().apply{moveTo(-7f,9f);cubicTo(-16f,-20f,-32f,-55f,-40f,-69f)
                quadTo(-24f,-84f,0f,-76f);quadTo(29f,-89f,42f,-70f)
                cubicTo(28f,-47f,16f,-18f,7f,9f);close()}
            petal(q,0xffdc96b2.toInt(),0xffb85d95.toInt())
            b.oval(0f,-65f,33f,13f,0xff8b4a83.toInt())
            for(i in 0..4)b.at((i-2)*14f,-64f,(i-2)*19f,if(i==2).8f else .55f){
                val lip=blade(20f,31f);petal(lip,0xffe8a2bd.toInt(),purple)
            }
            repeat(3){i->b.stroke(Path().apply{moveTo((i-1)*6f,-44f);quadTo((i-1)*9f,-61f,(i-1)*11f,-78f)},purple,1.3f)}
        }
    }
}
