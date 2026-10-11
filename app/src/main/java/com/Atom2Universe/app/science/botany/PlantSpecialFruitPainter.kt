package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Additional fruit architectures. All coordinates are relative to the plate centre. */
internal class PlantSpecialFruitPainter(private val c:Canvas,private val l:PlantLook,private val detailed:Boolean) {
    private val b=BotanyInk(c)
    private val d=l.fruitDetail
    private val ink=0xff62513b.toInt()
    private val cream=0xffe6d7b0.toInt()
    private val brown=0xffa98957.toInt()

    fun draw():Boolean {
        when {
            d.achene==AcheneForm.SILKY_CLUSTER -> silkyCluster()
            l.fruit==FruitShape.INDEHISCENT -> woodyFruit()
            l.fruit==FruitShape.FOLLICLE -> follicle()
            l.fruit==FruitShape.POD && d.podForm!=PodForm.STRAIGHT -> specialPods()
            l.fruit==FruitShape.SAMARA && d.samara==SamaraForm.FOUR_WINGS -> fourWings()
            l.fruit==FruitShape.CAPSULE && d.capsule==CapsuleForm.BEAKED -> beaked()
            l.fruit==FruitShape.CAPSULE && d.capsule==CapsuleForm.INFLATED_CALYX -> inflatedCalyx()
            l.fruit==FruitShape.GRAIN && l.flowerDetail.cluster in setOf(FlowerCluster.BRISTLY_SPIKE,FlowerCluster.PAIRED_RACEMES) -> grassFruit()
            else -> return false
        }
        return true
    }

    private fun wash(q:Path,color:Int=brown) {
        b.wash(q,-40f,-45f,45f,45f,intArrayOf(botanicalMix(color,cream,.5f),color,botanicalMix(color,ink,.35f)))
        b.stroke(q,ink,.7f)
    }
    private fun oval(x:Float,y:Float,rx:Float,ry:Float,color:Int=brown) {
        wash(Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)},color)
    }
    private fun stalk(x:Float,y:Float,xx:Float,yy:Float) {
        b.stroke(Path().apply{moveTo(x,y);quadTo(x-6,(y+yy)/2,xx,yy)},l.leafColor,1.7f)
    }

    private fun woodyFruit() {
        stalk(-25f,-124f,-40f,-63f)
        val q=Path().apply{moveTo(-40f,-67f);cubicTo(-100f,-61f,-108f,80f,-43f,89f)
            cubicTo(21f,83f,22f,-57f,-40f,-67f);close()}
        wash(q,0xff9f926a.toInt())
        c.save();c.clipPath(q)
        repeat(if(detailed)190 else 40){i->val a=i*2.399963;val r=sqrt(i/190f)
            val x=-40+cos(a).toFloat()*51*r;val y=10+sin(a).toFloat()*78*r
            b.line(x,y,x+1,y-3,0xffc1b58b.toInt(),.6f)}
        c.restore()
        // Separate section: brittle wall, dry pale pulp and embedded dark seeds.
        oval(69f,45f,38f,45f,0xffa39060.toInt());oval(69f,45f,32f,39f,0xffe8dfbd.toInt())
        repeat(14){i->val a=i*2.399963;val r=sqrt((i+.5f)/14)*25
            b.at(69+cos(a).toFloat()*r,45+sin(a).toFloat()*r*1.14f,i*41f){
                oval(0f,0f,4f,6f,0xff675440.toInt());b.oval(2f,-1f,1.3f,2f,0xffd8c69b.toInt())}
        }
    }

    private fun specialPods() {
        when(d.podForm) {
            PodForm.SPIRAL,PodForm.CURLED -> {
                val spiral=d.podForm==PodForm.SPIRAL
                repeat(if(spiral)2 else 1){i->b.at(if(spiral)(i-.5f)*88 else 0f,if(spiral)(i-.5f)*38 else 0f,i*31f,if(spiral).66f else 1f){
                    val points=(0..220).map{j->val t=j/220f
                        val a=if(spiral)-PI/2+t*PI*3.7 else -PI*.85+t*PI*1.65
                        val r=if(spiral)15f+t*68 else 61f
                        (cos(a).toFloat()*r) to (sin(a).toFloat()*r*(if(spiral).88f else .82f))}
                    ribbon(points,if(spiral)5f else 14f,if(spiral)0xffa39460.toInt()else 0xffb97641.toInt())
                    val first=points.last();stalk(first.first,first.second-32,first.first,first.second)
                }}
            }
            PodForm.FLAT_CURVED -> repeat(2){i->b.at((i-.5f)*92,-4f,(i-.5f)*25){
                val points=(0..100).map{j->val t=j/100f;val a=-PI*.6+t*PI*.7
                    (cos(a).toFloat()*55-40) to (sin(a).toFloat()*86+17)}
                ribbon(points,23f,0xffb2a075.toInt());stalk(points.first().first,-115f,points.first().first,points.first().second)
            }}
            PodForm.PULPY -> repeat(2){i->b.at((i-.5f)*92,-3f,(i-.5f)*20){
                stalk(-5f,-115f,-5f,-85f)
                val q=Path().apply {
                    for(side in listOf(-1,1))for(j in 0..100){val t=if(side<0)j/100f else 1-j/100f
                        val w=sin(PI*t).toFloat().pow(.28f)*(20f+3f*cos(t*d.podSeeds*2*PI).toFloat())
                        val x=side*w+12*sin(PI*t).toFloat();val y=-86f+t*169
                        if(side<0 && j==0)moveTo(x,y)else lineTo(x,y)}
                    close()
                }
                wash(q,0xffa18355.toInt())
                b.stroke(Path().apply{moveTo(0f,-80f);quadTo(26f,0f,0f,81f)},0xffcfb787.toInt(),1f)
                c.save();c.clipPath(q)
                if(detailed)repeat(100){j->val x=-22f+(j%8)*6;val y=-80f+(j/8)*13
                    b.line(x,y,x+2,y-1,0x667a6147,.6f)}
                c.restore()
            }}
            else -> Unit
        }
    }

    /** A closed ribbon preserves pod thickness through curves, rather than a decorative spiral line. */
    private fun ribbon(points:List<Pair<Float,Float>>,width:Float,color:Int) {
        val q=Path()
        for(side in listOf(-1,1))for(k in points.indices) {
            val i=if(side<0)k else points.lastIndex-k
            val p=points[i];val prev=points[(i-1).coerceAtLeast(0)];val next=points[(i+1).coerceAtMost(points.lastIndex)]
            val dx=next.first-prev.first;val dy=next.second-prev.second;val norm=hypot(dx,dy).coerceAtLeast(.001f)
            val w=width*(.45f+.55f*sin(PI*i/points.lastIndex).toFloat().pow(.20f))
            val x=p.first-side*dy/norm*w;val y=p.second+side*dx/norm*w
            if(side<0 && k==0)q.moveTo(x,y)else q.lineTo(x,y)
        };q.close();wash(q,color)
        val seam=Path();points.forEachIndexed{i,p->if(i==0)seam.moveTo(p.first,p.second)else seam.lineTo(p.first,p.second)}
        b.stroke(seam,botanicalMix(color,cream,.35f),.75f)
        if(detailed)for(i in 8 until points.lastIndex step 13){val p=points[i];val n=points[i+1]
            val a=atan2(n.second-p.second,n.first-p.first)*180/PI.toFloat()
            b.at(p.first,p.second,a){
                b.stroke(Path().apply{moveTo(-width*.28f,-width*.72f);quadTo(width*.39f,0f,-width*.28f,width*.72f)},botanicalMix(color,ink,.22f),.6f)
                b.stroke(Path().apply{moveTo(-width*.35f,-width*.68f);quadTo(width*.21f,0f,-width*.35f,width*.68f)},botanicalMix(color,cream,.18f),.55f)
            }
        }
    }

    private fun follicle() {
        stalk(0f,-121f,-38f,-65f)
        b.at(-44f,4f,-17f){
            val q=Path().apply{moveTo(0f,-83f);cubicTo(-64f,-45f,-49f,56f,0f,73f)
                cubicTo(56f,46f,68f,-29f,0f,-83f);close()}
            wash(q,0xff9aa67b.toInt())
            b.stroke(Path().apply{moveTo(0f,-78f);quadTo(-10f,-3f,0f,68f)},0xff697654.toInt(),1.2f)
            repeat(8){i->val x=(i-3.5f)*8;b.stroke(Path().apply{moveTo(0f,-71f);quadTo(x*1.5f,0f,0f,66f)},0x69939f77,.7f)}
        }
        b.at(64f,40f,19f,.62f){
            val q=Path().apply{moveTo(0f,-90f);cubicTo(-67f,-56f,-61f,56f,0f,79f)
                cubicTo(39f,40f,45f,-41f,0f,-90f);close()}
            wash(q,0xffa19f74.toInt())
            val split=Path().apply{moveTo(0f,-80f);quadTo(-49f,-6f,0f,67f);quadTo(22f,-8f,0f,-80f);close()}
            b.fill(split,0xff72694b.toInt())
            repeat(17){i->val y=-54f+i*6;oval((i%3-1)*8f,y,5f,7f,0xffa67849.toInt())}
        }
        b.at(69f,-63f,-18f){silkySeed(28f)}
    }
    private fun silkySeed(reach:Float) {
        repeat(if(detailed)32 else 12){i->val a=-PI*.92+i*PI*.84/31
            val x=cos(a).toFloat()*reach;val y=sin(a).toFloat()*reach*1.5f
            b.stroke(Path().apply{moveTo(0f,9f);quadTo(x*.23f,y*.9f,x,y)},0xffd4ccad.toInt(),.65f)}
        oval(0f,12f,4f,6f,0xffa0754b.toInt())
    }

    private fun silkyCluster() {
        stalk(0f,124f,0f,3f);oval(0f,0f,10f,8f,0xffa68c6b.toInt())
        repeat(11){i->b.at(0f,0f,i*360f/11){
            val q=Path().apply{moveTo(-2f,0f);quadTo(-7f,-55f,0f,-91f);quadTo(7f,-55f,2f,0f);close()}
            wash(q,0xffb39c8e.toInt());b.line(0f,-85f,0f,-102f,ink,.7f)
            repeat(if(detailed)28 else 12){j->val y=-8f-j*2.7f
                for(side in listOf(-1,1))b.line(side*2f,y,side*(9f+sin(j*1.3f)*3),y-9,0xffd3c8b1.toInt(),.65f)}
        }}
    }

    private fun fourWings() {
        stalk(0f,-117f,-35f,-71f)
        for(i in 0..1)b.at(if(i==0)-35f else 64f,if(i==0)-7f else 60f,if(i==0)-12f else 18f,if(i==0)1f else .55f){
            // Four longitudinal wings seen obliquely: broad side wings and foreshortened front/back wings.
            for(side in listOf(-1,1))for(front in listOf(false,true)) {
                val w=if(front)17f else 60f
                val q=Path().apply{moveTo(0f,-64f);cubicTo(side*w,-73f,side*w*1.18f,61f,0f,69f);close()}
                wash(q,if(front)0xffaa755b.toInt()else 0xffbd8768.toInt())
                if(detailed)repeat(12){j->val y=-55f+j*10
                    b.stroke(Path().apply{moveTo(0f,y);quadTo(side*w*.45f,y-11,side*w*.8f*sin(PI*(j+1)/14).toFloat(),y-10)},0xffd1a084.toInt(),.65f)}
            }
            oval(0f,0f,6f,58f,0xff895c44.toInt())
        }
    }

    private fun beaked() {
        repeat(2){i->b.at((i-.5f)*96,-17f,(i-.5f)*13){
            stalk(0f,97f,0f,64f)
            val q=Path().apply{moveTo(0f,66f);quadTo(-29f,1f,-24f,-44f);quadTo(-6f,-52f,0f,-96f)
                quadTo(6f,-52f,24f,-44f);quadTo(29f,1f,0f,66f);close()}
            wash(q,0xffa19765.toInt())
            for(j in -1..1)b.stroke(Path().apply{moveTo(0f,59f);quadTo(j*18f,-4f,j*10f,-51f)},ink,.7f)
            if(i==1){b.stroke(Path().apply{moveTo(0f,49f);quadTo(9f,-12f,0f,-65f)},0xff5a4b31.toInt(),5f)
                repeat(11){j->b.oval(1f,-45f+j*8,2.3f,3f,0xffc7ae75.toInt())}}
        }}
        // Enlarged seed with membranous extensions at BOTH ends.
        b.at(72f,105f,76f){
            for(side in listOf(-1,1)){val q=Path().apply{moveTo(-6f,side*3f);quadTo(-15f,side*20f,0f,side*31f)
                quadTo(15f,side*20f,6f,side*3f);close()};wash(q,0xffc5b388.toInt())}
            oval(0f,0f,7f,11f,0xff776040.toInt())
        }
    }

    private fun inflatedCalyx() {
        stalk(-29f,117f,-29f,59f)
        b.at(-29f,0f){
            val q=Path().apply{moveTo(0f,63f);cubicTo(-72f,33f,-61f,-53f,-33f,-55f)
                lineTo(-25f,-82f);lineTo(-10f,-57f);lineTo(0f,-87f);lineTo(12f,-57f);lineTo(30f,-79f)
                cubicTo(60f,-49f,69f,32f,0f,63f);close()}
            wash(q,0xffd0c693.toInt());oval(0f,2f,30f,40f,0xffb0a36e.toInt())
            c.save();c.clipPath(q)
            for(j in -4..4){val x=j*12f
                b.stroke(Path().apply{moveTo(0f,61f);cubicTo(x*1.6f,31f,x*1.45f,-33f,x*.6f,-86f)},0xff948560.toInt(),1f)}
            for(y in -5..5)b.stroke(Path().apply{moveTo(-60f,y*12f);quadTo(0f,y*12f+17,60f,y*12f)},0xffb0a77d.toInt(),.6f)
            c.restore()
            repeat(11){i->val a=PI+i*PI/10;b.line(cos(a).toFloat()*13,55f+sin(a).toFloat()*8,cos(a).toFloat()*42,60f+sin(a).toFloat()*12,green(),.9f)}
        }
        b.at(77f,58f,0f,.37f){
            repeat(5){i->b.at(0f,0f,i*72f){wash(Path().apply{moveTo(0f,7f);quadTo(-24f,-38f,0f,-83f);quadTo(24f,-38f,0f,7f);close()})}}
            repeat(12){i->val a=i*2.4;oval(cos(a).toFloat()*19,sin(a).toFloat()*19,4f,5f,0xff726346.toInt())}
        }
    }
    private fun green()=l.leafColor

    private fun grassFruit() {
        // Caryopsis enlarged independently, beside its dispersal unit (not a naked seed).
        b.at(-52f,33f,-16f){
            oval(0f,0f,20f,53f,0xffc5ac76.toInt())
            b.stroke(Path().apply{moveTo(0f,-44f);quadTo(-6f,0f,0f,44f)},0xff8c754b.toInt(),1.5f)
            b.oval(4f,32f,5f,9f,0xffac9564.toInt())
        }
        if(l.flowerDetail.cluster==FlowerCluster.BRISTLY_SPIKE)b.at(57f,8f){
            repeat(3){i->b.at((i-1)*9f,0f,(i-1)*19f){oval(0f,-5f,6f,20f,0xffbaac82.toInt())}}
            repeat(36){i->val a=PI*1.12+i*PI*.78/35;val x=cos(a).toFloat()*58;val y=sin(a).toFloat()*91
                b.stroke(Path().apply{moveTo(0f,26f);quadTo(x*.6f,y*.7f,x,y)},0xffb59c72.toInt(),.75f)
                if(detailed)repeat(5){j->val t=(j+2)/8f;val xx=x*t;val yy=26+(y-26)*t
                    b.line(xx,yy,xx+3,yy-4,0xffcfc1a1.toInt(),.5f)}
            }
            b.oval(0f,26f,7f,3f,0xffa48e65.toInt())
        }else b.at(53f,28f){
            oval(0f,0f,8f,31f,0xffbdb58b.toInt())
            repeat(16){i->for(side in listOf(-1,1))b.line(side*5f,-25f+i*3,side*16f,-40f+i*3,0xffd7cfb5.toInt(),.6f)}
            b.stroke(Path().apply{moveTo(0f,-25f);lineTo(27f,-80f);lineTo(10f,-134f)},0xff927b50.toInt(),1f)
        }
    }
}
