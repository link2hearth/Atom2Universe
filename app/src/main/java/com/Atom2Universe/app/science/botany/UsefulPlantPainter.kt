package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.*

/** Original organ studies, selected by reusable morphology rather than species identifiers. */
internal class UsefulPlantPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val tea=form==StudyForm.TEA_CAPSULE
    private val cotton=form==StudyForm.FIBROUS_BOLL
    private val pomegranate=form==StudyForm.CROWNED_BERRY
    private val avocado=form==StudyForm.SINGLE_SEED_BERRY
    private val bay=form==StudyForm.DIOECIOUS_UMBEL
    private val b=BotanyInk(c)
    private val leaf=PlantLeafPainter(c,species,detailed)
    private val green=l.leafColor
    private val ink=0xff48513a.toInt()
    private val cream=0xfff7edd6.toInt()
    private val brown=0xff936d46.toInt()
    private val red=0xffbd4735.toInt()
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=ink,width:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)
    private fun shape(p:Path,color:Int) {
        val r=RectF();p.computeBounds(r,true)
        b.wash(p,r.left,r.top,r.right,r.bottom,intArrayOf(botanicalMix(color,cream,.32f),color,botanicalMix(color,ink,.18f)))
        b.stroke(p,ink,.7f)
    }
    private fun oval(x:Float,y:Float,rx:Float,ry:Float,color:Int)=shape(Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)},color)
    fun draw(view:PlantView) {when(view) {
        PlantView.HABIT -> if(cotton)cottonHabit() else if(pomegranate)pomegranateHabit() else PlantTreePainter(c,species,detailed).draw()
        PlantView.LEAF -> leaves()
        PlantView.FLOWER -> flowers()
        PlantView.FRUIT -> fruits()
    }}
    private fun drawLeaf() {
        if(!cotton){leaf.draw(l.leaf,198f);return}
        // Broad, acute lobes and a cordate basal sinus, rather than a rounded five-point rosette.
        b.line(0f,0f,0f,-58f,ink,1.8f)
        val p=Path().apply {
            moveTo(0f,-58f);quadTo(-25f,-40f,-42f,-53f);quadTo(-61f,-66f,-77f,-80f)
            quadTo(-63f,-88f,-45f,-94f);quadTo(-61f,-121f,-63f,-151f)
            quadTo(-34f,-141f,-23f,-131f);quadTo(-21f,-173f,0f,-198f)
            quadTo(21f,-173f,23f,-131f);quadTo(34f,-141f,63f,-151f)
            quadTo(61f,-121f,45f,-94f);quadTo(63f,-88f,77f,-80f)
            quadTo(61f,-66f,42f,-53f);quadTo(25f,-40f,0f,-58f);close()
        }
        shape(p,green)
        c.save();c.clipPath(p)
        for((x,y) in listOf(-77f to -80f,-63f to -151f,0f to -198f,63f to -151f,77f to -80f)) {
            b.line(0f,-58f,x,y,0xffc1c98f.toInt(),.85f)
            for(i in 1..5) {
                val t=i/6f;val xx=x*t;val yy=-58f+(y+58)*t
                for(side in listOf(-1,1))curve(xx,yy,xx+side*14,yy-8,xx+side*22,yy-18,0xff8fa575.toInt(),.6f)
            }
        }
        c.restore()
        if(detailed){
            val contour=PathMeasure(p,true);val point=FloatArray(2)
            repeat(65){i->contour.getPosTan(contour.length*i/65,point,null)
                val dx=point[0];val dy=point[1]+120;val norm=hypot(dx,dy).coerceAtLeast(1f)
                b.line(point[0],point[1],point[0]+dx/norm*2,point[1]+dy/norm*2,0xffb4bb8f.toInt(),.45f)
            }
        }
    }
    private fun pomegranateHabit() {
        // Several basal stems, slender lateral shoots and paired leaves leave the crown open.
        for(i in 0..6) {
            val x=49f+i*33;val y=46f+abs(3-i)*18
            val cx=150f+(x-150)*.37f;val cy=135f
            curve(150f,279f,cx,cy,x,y,brown,if(i==3)4.8f else 3.3f)
            for(j in 1..5) {
                val t=j/5f
                val bx=(1-t)*(1-t)*150+2*(1-t)*t*cx+t*t*x
                val by=(1-t)*(1-t)*279+2*(1-t)*t*cy+t*t*y
                val side=if((i+j)%2==0)-1 else 1
                val tx=bx+side*20;val ty=by-19
                b.line(bx,by,tx,ty,brown,1f)
                for(k in 0..2) {
                    val xx=bx+(tx-bx)*(k+1)/3;val yy=by+(ty-by)*(k+1)/3
                    for(s in listOf(-1,1))b.at(xx,yy,s*50f,.15f){leaf.draw(l.leaf,150f)}
                }
                b.line(tx,ty,tx+side*3,ty-7,ink,.75f)
                if(j==4 && i%2==0) {
                    b.line(tx,ty,tx+4,ty+12,brown,.9f)
                    // Rotate the persistent calyx downwards, opposite the attachment to the stalk.
                    b.at(tx+4,ty+22,180f,.14f){pomegranateFruit(false)}
                }
            }
        }
        b.line(120f,282f,181f,282f,0x696a634a,1f)
    }
    private fun cottonHabit() {
        curve(150f,280f,145f,156f,152f,33f,brown,5f)
        for(i in 0..5) {
            val side=if(i%2==0)-1 else 1
            val y=91f+i*28;val x=150f+side*(42f+i*6)
            curve(150f,y+23,150f+side*28,y+7,x,y,brown,2f)
            b.at(x,y,side*35f,.27f){drawLeaf()}
            b.at(150f+side*16,y+11,-side*34f,.19f){drawLeaf()}
            curve(x,y,x+side*13,y-13,x+side*14,y-25,green,1.5f)
            b.at(x+side*14,y-25,0f,.18f){
                if(i<2)blossom() else boll(i>3)
            }
        }
        b.at(152f,55f,-15f,.18f){drawLeaf()}
        b.line(118f,282f,181f,282f,0x696a634a,1f)
    }
    private fun leaves() {
        b.at(if(cotton)140f else 122f,258f){drawLeaf()}
        if(!cotton)b.at(229f,277f,0f,.55f) {
            b.line(0f,0f,0f,-132f,brown,3f)
            for(i in 0..2) {
                val y=-27f-i*33
                val sides=if(pomegranate)listOf(-1,1) else listOf(if(i%2==0)-1 else 1)
                for(side in sides)b.at(0f,y,side*52f){leaf.draw(l.leaf,81f)}
            }
            if(tea) {
                oval(0f,-139f,4f,14f,0xffb4bc81.toInt())
                if(detailed)for(i in -2..2)b.line(i.toFloat(),-145f,i+1f,-132f,cream,.5f)
            }
        }
    }
    private fun petal(angle:Float,color:Int,narrow:Boolean=false,crumpled:Boolean=false) = b.at(0f,0f,angle) {
        val w=if(narrow)14f else 32f
        shape(Path().apply {
            moveTo(-5f,-5f);cubicTo(-w,-22f,-w,-55f,-8f,-59f)
            if(crumpled){lineTo(-4f,-56f);lineTo(0f,-62f);lineTo(5f,-57f);lineTo(10f,-60f)}
            else quadTo(0f,-65f,8f,-59f)
            cubicTo(w,-55f,w,-22f,5f,-5f);close()
        },color)
        if(detailed)for(i in -1..1)curve(i*2f,-10f,i*w*.65f,-33f,i*w*.35f,-51f,botanicalMix(color,ink,.25f),.6f)
    }
    /** A cotton flower has three leafy, laciniate bracts, separate from the small calyx. */
    private fun bracts() {
        repeat(3){i->b.at(0f,0f,i*120f+20f) {
            val p=Path().apply {
                moveTo(0f,8f);lineTo(-27f,-24f)
                for(j in 0..4){lineTo(-27f+j*6,-54f-j*3);lineTo(-21f+j*6,-36f-j*3)}
                lineTo(0f,-78f);lineTo(7f,-51f);lineTo(21f,-66f);lineTo(16f,-40f);lineTo(32f,-49f);lineTo(25f,-21f);close()
            }
            shape(p,green);curve(0f,5f,0f,-30f,0f,-67f,0xffadb77b.toInt(),.8f)
        }}
    }
    private fun stamen(angle:Double,r:Float,glands:Boolean=false) {
        val x=cos(angle).toFloat()*r;val y=sin(angle).toFloat()*r
        b.line(x*.37f,y*.37f,x,y,0xffbfa66a.toInt(),1.2f)
        b.at(x,y,(angle*180/PI).toFloat()) {
            if(avocado) {
                // Persea has four pollen sacs/valves, unlike the two-celled Laurus anther.
                for(side in listOf(-1,1))for(row in listOf(-1,1))
                    oval(side*1.8f,row*2.2f,1.8f,2.1f,0xffd5b746.toInt())
            } else {
                oval(-1.6f,0f,2.1f,3.8f,0xffd5b746.toInt());oval(1.6f,0f,2.1f,3.8f,0xffd5b746.toInt())
            }
        }
        if(glands)for(s in listOf(-1,1)) {
            val dx=-sin(angle).toFloat()*5*s;val dy=cos(angle).toFloat()*5*s
            oval(x*.5f+dx,y*.5f+dy,3f,3f,0xffdca14b.toInt())
        }
    }
    private fun blossom(female:Boolean=false) {
        if(cotton)bracts()
        val count=if(tea)7 else if(cotton)5 else if(bay)4 else 6
        val color=if(pomegranate)red else if(avocado || bay)0xffc8ce84.toInt() else cream
        repeat(count){petal(it*360f/count,color,avocado || bay,pomegranate)}
        oval(0f,0f,13f,13f,if(pomegranate)0xffeab556.toInt() else 0xffb9bb75.toInt())
        when {
            cotton -> {
                // Column of fused filaments, with free anthers; style projects above it.
                shape(Path().apply{moveTo(-7f,15f);lineTo(-5f,-27f);quadTo(0f,-33f,5f,-27f);lineTo(7f,15f);close()},0xffefcc79.toInt())
                repeat(23){i->
                    val x=sin(i*2.4f)*11;val y=12f-i*1.65f
                    b.line(0f,y,x,y-2,cream,.8f);oval(x,y-2,2f,2.5f,0xffd5ac3d.toInt())
                }
                b.line(0f,-23f,0f,-43f,cream,1.7f)
                repeat(4){i->curve(0f,-39f,-8f+i*5,-48f,-7f+i*5,-45f,cream,1.6f)}
            }
            avocado -> {
                // Nine fertile stamens in three whorls; paired glands on the inner three.
                repeat(6){stamen(2*PI*it/6,30f)}
                repeat(3){stamen(2*PI*it/3+PI/6,19f,true)}
                repeat(3){i->b.at(0f,0f,i*120f+90f) {
                    shape(Path().apply{moveTo(0f,-8f);lineTo(-3f,-13f);lineTo(0f,-16f);lineTo(3f,-13f);close()},0xffd4af4f.toInt())
                }}
                oval(0f,1f,5f,7f,green);b.line(0f,0f,0f,-10f,green,1.5f);oval(0f,-10f,3f,2f,green)
            }
            bay -> if(female) {
                oval(0f,1f,9f,11f,green);b.line(0f,0f,0f,-18f,green,2f);oval(0f,-18f,4f,3f,green)
                repeat(4){i->b.at(0f,0f,i*90f){oval(0f,-25f,2f,6f,0xffc6b26b.toInt())}}
            } else {
                // Flora of China: three whorls of four; outer filaments lack glands.
                repeat(3){whorl->repeat(4){i->
                    stamen(i*PI/2+whorl*PI/6,35f-whorl*8f,glands=whorl>0)
                }}
            }
            else -> {
                repeat(if(tea)58 else 46){i->stamen(i*2.39996,17f+sqrt(i.toFloat())*2.2f)}
                b.line(0f,4f,0f,-17f,green,2f)
                if(tea)for(i in -1..1)curve(0f,-12f,i*4f,-19f,i*6f,-23f,green,1.4f)
                else oval(0f,-17f,4f,3f,green)
            }
        }
    }
    private fun flowers() {
        b.at(144f,128f){blossom()}
        when {
            pomegranate -> b.at(222f,260f,0f,.70f) {
                shape(Path().apply {
                    moveTo(-9f,0f);cubicTo(-31f,-12f,-24f,-45f,-34f,-57f)
                    lineTo(-14f,-45f);lineTo(-9f,-65f);lineTo(4f,-46f);lineTo(18f,-64f);lineTo(23f,-45f);lineTo(35f,-55f)
                    cubicTo(23f,-22f,26f,-9f,9f,0f);close()
                },red)
                repeat(3){i->b.at(-20f+i*20,-47f,i*15f-15,.4f){petal(0f,red,crumpled=true)}}
                b.line(0f,0f,0f,18f,green,3f)
            }
            cotton -> b.at(229f,257f,0f,.57f) {
                bracts();shape(Path().apply{moveTo(-6f,4f);quadTo(-32f,-20f,-34f,-46f);quadTo(0f,-65f,34f,-46f);quadTo(30f,-20f,6f,4f);close()},cream)
                curve(0f,2f,-4f,-20f,0f,-52f,0xffc6a56b.toInt(),1f)
            }
            bay -> b.at(230f,243f,0f,.58f){blossom(true)}
        }
        if(!cotton && !pomegranate) {
            b.line(65f,278f,68f,218f,brown,2f)
            if(tea) {
                b.at(68f,249f,-48f,.30f){leaf.draw(l.leaf,120f)}
                curve(68f,237f,88f,222f,95f,242f,green,1.2f)
                b.at(95f,244f,0f,.22f){blossom()}
            } else for(i in 0..4) {
                val x=38f+i*15;val y=220f+abs(2-i)*7
                b.line(68f,if(bay)251f else 260f-i*7,x,y,green,1f)
                b.at(x,y,0f,.105f){blossom()}
                if(avocado)b.at(x+8,y+14,0f,.075f){blossom()}
            }
        }
    }
    private fun cottonSeed() {
        // Individual fibres emerge from the seed coat, never from the pericarp.
        repeat(if(detailed)62 else 22){i->
            val a=i*2.39996;val x=cos(a).toFloat()*8;val y=sin(a).toFloat()*12
            curve(x,y,x+cos(a).toFloat()*18,y-35,x+sin(i.toFloat())*16,y-57,0xffd0caba.toInt(),.65f)
        }
        oval(0f,0f,9f,13f,brown)
        repeat(14){i->curve(-6f+i%4*4,-8f+i/4*5, -10f+i%4*6,-20f,-12f+i%4*8,-29f,cream,.8f)}
    }
    private fun boll(open:Boolean) {
        if(!open) {
            oval(0f,0f,47f,60f,0xff809757.toInt())
            for(i in -1..1)curve(i*6f,-58f,i*43f,-2f,i*7f,58f,ink,1f)
            b.line(0f,60f,0f,72f,brown,3f)
        } else {
            repeat(4){i->b.at(0f,0f,i*90f+45) {
                shape(Path().apply{moveTo(-5f,15f);quadTo(-34f,-8f,-18f,-54f);lineTo(0f,-70f);lineTo(19f,-50f);quadTo(31f,-7f,5f,15f);close()},brown)
                val tuft=Path().apply {
                    for(j in 0..100) {
                        val a=2*PI*j/100;val r=1f+.055f*sin(8*a).toFloat()+.02f*cos(13*a).toFloat()
                        val x=cos(a).toFloat()*28*r;val y=-25f+sin(a).toFloat()*36*r
                        if(j==0)moveTo(x,y) else lineTo(x,y)
                    };close()
                }
                b.wash(tuft,-28f,-58f,28f,10f,intArrayOf(0xfffffdf5.toInt(),0xffeee9dc.toInt()))
                b.stroke(tuft,0xffcbc6b9.toInt(),.45f)
                if(detailed)repeat(33){j->
                    val x=sin(j*2.4f)*22;val y=-25f+cos(j*1.9f)*27
                    curve(x,y,x+sin(j.toFloat())*9,y-8,x+2,y-12,0xffd5d0c2.toInt(),.5f)
                }
                curve(0f,4f,0f,-20f,0f,-61f,0xffbc9868.toInt(),1f)
            }}
        }
    }
    private fun teaCapsule(open:Boolean) {
        if(open)repeat(3){i->b.at(0f,0f,i*120f) {
            shape(Path().apply{moveTo(0f,4f);quadTo(-47f,-12f,-31f,-49f);quadTo(0f,-71f,31f,-49f);quadTo(47f,-12f,0f,4f);close()},brown)
            oval(0f,-28f,19f,22f,0xff684831.toInt());oval(-5f,-35f,5f,8f,0xffa98154.toInt())
        }} else {
            // Three developed lobes are one example; one- and two-seeded capsules also occur.
            val capsule=Path().apply {
                for(i in 0..180) {
                    val a=2*PI*i/180-PI/2;val r=51f+9f*cos(3*(a+PI/2)).toFloat()
                    val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                    if(i==0)moveTo(x,y) else lineTo(x,y)
                };close()
            }
            shape(capsule,0xff8c965b.toInt())
            repeat(3){i->b.at(0f,0f,i*120f){curve(0f,0f,-15f,-26f,0f,-58f,ink,.9f)}}
            oval(0f,0f,4f,4f,brown)
        }
    }
    private fun crown() {
        shape(Path().apply{moveTo(-13f,-53f);lineTo(-23f,-79f);lineTo(-9f,-70f);lineTo(0f,-87f);lineTo(9f,-70f);lineTo(24f,-79f);lineTo(14f,-53f);close()},red)
        oval(0f,-71f,9f,3f,0xff795240.toInt())
    }
    private fun pomegranateFruit(section:Boolean) {
        crown();oval(0f,0f,59f,60f,red)
        if(section) {
            oval(0f,0f,53f,54f,0xffeddaa8.toInt())
            // Irregular chambers: a diagrammatic cut, not a claim that all locules form one whorl.
            val centres=listOf(-22f to -26f,20f to -24f,-29f to 14f,9f to 10f,18f to 37f)
            centres.forEachIndexed{i,(x,y)->
                for(j in 0..10) {
                    val a=j*2.39996;val r=sqrt(j.toFloat())*4.5f
                    val xx=x+cos(a).toFloat()*r;val yy=y+sin(a).toFloat()*r
                    if(xx*xx/2300+yy*yy/2500<.90f)b.at(xx,yy,i*27f+j*12){
                        oval(0f,0f,4.1f,5.1f,0xffb73549.toInt());b.line(-1f,-2f,0f,-3f,0xfff0a194.toInt(),.9f)
                    }
                }
            }
            curve(-5f,-52f,-5f,-7f,-46f,0f,cream,3f)
            curve(-5f,-9f,15f,-3f,48f,-5f,cream,3f)
            curve(-6f,0f,-10f,28f,-28f,44f,cream,3f)
        } else if(detailed)repeat(110){i->
            val a=i*2.39996;val r=sqrt(i/110f)*54
            b.oval(cos(a).toFloat()*r,sin(a).toFloat()*r,.65f,.8f,0x65efb275)
        }
    }
    private fun avocadoOutline()=Path().apply {
        moveTo(0f,-60f);cubicTo(-25f,-65f,-24f,-25f,-42f,0f)
        cubicTo(-68f,43f,-29f,60f,0f,60f);cubicTo(29f,60f,68f,43f,42f,0f)
        cubicTo(24f,-25f,25f,-65f,0f,-60f);close()
    }
    private fun avocadoFruit(section:Boolean) {
        val p=avocadoOutline();shape(p,0xff527642.toInt())
        if(section) {
            b.at(0f,0f,0f,.94f){shape(p,0xffb6c56e.toInt())}
            b.at(0f,0f,0f,.86f){shape(p,0xffecda96.toInt())}
            oval(0f,20f,27f,31f,0xffa17042.toInt())
            curve(-11f,-4f,-29f,14f,-10f,42f,0xffcfac72.toInt(),1.2f)
            if(detailed)repeat(24){i->curve(sin(i*2f)*19,3f+i*1.6f,sin(i*2f)*19+3,6f+i*1.6f,sin(i*2f)*19+1,9f+i*1.6f,0x5574512c,.5f)}
        } else {
            b.line(0f,-60f,2f,-75f,brown,3f)
            if(detailed){c.save();c.clipPath(p);repeat(160){i->
                val x=sin(i*2.39996f)*48;val y=-55f+i%53*2.1f
                b.oval(x,y,1f,1.3f,0x60678b41)
            };c.restore()}
        }
    }
    private fun fruits() {when {
        tea -> {
            b.at(85f,140f){teaCapsule(false)};b.at(224f,151f,25f,.85f){teaCapsule(true)}
            oval(126f,252f,16f,18f,0xff765139.toInt());oval(121f,244f,5f,4f,0xffbeaa7d.toInt())
        }
        cotton -> {
            b.at(76f,125f){boll(false)};b.at(222f,140f,0f,.88f){boll(true)}
            b.at(153f,271f,-12f,.78f){cottonSeed()}
        }
        pomegranate -> {
            b.at(82f,147f){pomegranateFruit(false)};b.at(223f,159f,0f,.88f){pomegranateFruit(true)}
            b.at(141f,263f,-22f){oval(0f,0f,6f,9f,0xffb73549.toInt());oval(0f,0f,2f,5f,0xffe8c295.toInt())}
        }
        avocado -> {b.at(79f,137f){avocadoFruit(false)};b.at(219f,155f,0f,.92f){avocadoFruit(true)}}
        bay -> {
            oval(85f,142f,43f,60f,0xff333d35.toInt());b.line(85f,82f,88f,65f,brown,3f)
            oval(220f,155f,39f,55f,0xff414632.toInt());oval(220f,155f,35f,50f,0xffb2b277.toInt());oval(220f,155f,28f,44f,0xffb2935b.toInt())
            curve(216f,117f,197f,150f,217f,195f,0xffd7bd86.toInt(),1f)
            b.line(79f,280f,102f,239f,brown,2f)
            for(i in 0..2){val x=84f+i*18;val y=249f-i*8;b.line(96f,253f,x,y-6,green,1f);oval(x,y-9,5f,7f,0xff333d35.toInt())}
        }
    }}
}
