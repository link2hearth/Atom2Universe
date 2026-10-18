package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path

/** Two-valved slender capsules and five-valved woody capsules, with no false silique septum. */
internal class PlantAdditionalFruitPainter(c:Canvas,private val l:PlantLook,private val detailed:Boolean) {
    private val b=BotanyInk(c)
    fun capsule() {
        val slender=l.fruitDetail.capsule==CapsuleForm.LINEAR
        val count=if(slender)2 else 5
        b.line(0f,120f,0f,74f,l.leafColor,2f)
        for(i in 0 until count) {
            val offset=i-(count-1)/2f
            val x=offset*if(slender)33f else 15f
            val q=Path().apply{moveTo(0f,75f)
                cubicTo(x*1.4f,25f,x*1.3f,-56f,x,-100f)
                cubicTo(x*.62f,-56f,x*.55f,25f,0f,75f);close()}
            b.wash(q,x-15,-55f,x+15,60f,intArrayOf(0xffc9b486.toInt(),0xff9b855a.toInt(),0xff69533a.toInt()))
            b.stroke(q,0xff65543a.toInt(),.8f)
            b.stroke(Path().apply{moveTo(0f,71f);quadTo(x*.75f,0f,x,-97f)},0xffd7c69a.toInt(),.7f)
        }
        if(detailed) {
            if(slender)repeat(14){i->b.oval((if(i%2==0)-1 else 1)*9f,-76f+i*10,2.7f,2.6f,0xff6e5740.toInt())}
            // An intact capsule gives context to the opened fruit.
            b.at(76f,30f,14f,.7f){
                val w=if(slender)8f else 18f
                val q=Path().apply{moveTo(0f,80f);quadTo(-w,9f,-w,-55f);quadTo(0f,-92f,w,-55f);quadTo(w,9f,0f,80f);close()}
                b.fill(q,0xffa18e5e.toInt());b.stroke(q,0xff5e583c.toInt(),1f)
                for(i in -1..1)b.line(i*w*.4f,-52f,i*w*.3f,54f,0xffd1bd89.toInt(),.7f)
                b.line(0f,80f,0f,116f,l.leafColor,2f)
            }
        }
    }
}
