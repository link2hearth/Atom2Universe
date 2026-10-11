package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.*

/** Reusable crop architectures and diagnostic organs, all drawn in the 300-unit plate. */
internal class FoodCropPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val onion=form==StudyForm.HOLLOW_BULB_UMBEL
    private val cabbage=form==StudyForm.WAXY_ROSETTE_SILIQUES
    private val cucumber=form==StudyForm.TENDRIL_PEPO
    private val peanut=form==StudyForm.GEOCARPIC_POD
    private val b=BotanyInk(c)
    private val leaf=PlantLeafPainter(c,species,detailed)
    private val flowers=PlantFlowerPainter(c,species,detailed)
    private val green=l.leafColor
    private val ink=0xff44563b.toInt()
    private val cream=0xfff3e9c9.toInt()
    private val straw=0xffbda26a.toInt()
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=ink,width:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)
    private fun shape(p:Path,color:Int=green) {
        val r=RectF();p.computeBounds(r,true)
        b.wash(p,r.left,r.top,r.right,r.bottom,intArrayOf(botanicalMix(color,cream,.28f),color,botanicalMix(color,ink,.15f)))
        b.stroke(p,ink,.65f)
    }
    private fun oval(x:Float,y:Float,rx:Float,ry:Float,color:Int)=shape(Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)},color)
    fun draw(view:PlantView) {when(view) {
        PlantView.HABIT -> habit()
        PlantView.LEAF -> leaves()
        PlantView.FLOWER -> flowerPlate()
        PlantView.FRUIT -> fruitPlate()
    }}
    private fun tube(length:Float,bend:Float=0f,width:Float=8f) {
        shape(Path().apply{moveTo(-width,0f);quadTo(bend-width,-length*.55f,bend,-length);quadTo(bend+width,-length*.55f,width,0f);close()})
        curve(0f,0f,bend+2,-length*.5f,bend,-length+2,0xffb0c2a0.toInt(),.7f)
    }
    private fun bulb(section:Boolean) {
        shape(Path().apply{moveTo(-8f,-45f);cubicTo(-8f,-24f,-42f,-25f,-41f,9f);quadTo(-42f,36f,0f,38f);quadTo(43f,36f,41f,8f);cubicTo(41f,-25f,8f,-24f,8f,-45f);close()},if(section)cream else 0xffc79b54.toInt())
        for(i in 1..5)for(side in listOf(-1,1))curve(side*4f,-38f,side*i*14f,18f,side*2f,34f,if(section)0xffb8b28c.toInt() else 0xff99753f.toInt(),.7f)
        b.line(-13f,37f,13f,37f,0xff87704e.toInt(),3f)
        for(i in -3..3)curve(i*3f,39f,i*8f,50f,i*7f+4,63f,straw,.7f)
    }
    private fun tendril() {
        val p=Path().apply{moveTo(0f,0f);quadTo(10f,-12f,20f,-9f)}
        for(i in 0..100){val t=i/100f;val a=t*PI*5;val r=10f*(1-t);p.lineTo(20f+cos(a).toFloat()*r,-19f+sin(a).toFloat()*r)}
        b.stroke(p,ink,1f)
    }
    private fun foodLeaf(length:Float) {
        if(peanut) {
            // Two opposite leaflet pairs; the rachis ends between the distal pair.
            b.line(0f,0f,0f,-length*.67f,ink,1.8f)
            for(t in listOf(.36f,.67f))for(side in listOf(-1,1))b.at(0f,-length*t,side*47f) {
                leaf.simple(LeafShape.OVAL,length*.44f,LeafMargin.ENTIRE,
                    LeafDetail(width=.29f,base=LeafBase.ROUNDED,tip=LeafTip.ROUNDED,veinPairs=8,ciliate=true),.08f)
            }
            for(side in listOf(-1,1))shape(Path().apply{moveTo(0f,-10f);lineTo(side*12f,-44f);lineTo(side*4f,-14f);close()},0xff8b9d60.toInt())
        } else if(cucumber) {
            val h=length*.77f
            b.line(0f,0f,0f,-length*.23f,green,2f)
            b.at(0f,-length*.23f) {
                val outline=Path().apply {
                    moveTo(0f,0f)
                    cubicTo(-h*.12f,h*.13f,-h*.39f,h*.12f,-h*.46f,-h*.15f)
                    quadTo(-h*.43f,-h*.25f,-h*.37f,-h*.29f)
                    quadTo(-h*.47f,-h*.32f,-h*.53f,-h*.48f)
                    cubicTo(-h*.43f,-h*.64f,-h*.28f,-h*.74f,-h*.18f,-h*.64f)
                    quadTo(-h*.11f,-h*.87f,0f,-h)
                    quadTo(h*.12f,-h*.86f,h*.18f,-h*.64f)
                    cubicTo(h*.28f,-h*.74f,h*.43f,-h*.64f,h*.53f,-h*.48f)
                    quadTo(h*.47f,-h*.32f,h*.37f,-h*.29f)
                    quadTo(h*.43f,-h*.25f,h*.46f,-h*.15f)
                    cubicTo(h*.39f,h*.12f,h*.12f,h*.13f,0f,0f);close()
                }
                val measure=PathMeasure(outline,true);val xy=FloatArray(2);val tangent=FloatArray(2)
                val p=Path()
                for(i in 0..300) {
                    measure.getPosTan(measure.length*i/300f,xy,tangent)
                    val tooth=if(i%3==1)h*.004f else 0f
                    val x=xy[0]+tangent[1]*tooth;val y=xy[1]-tangent[0]*tooth
                    if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
                }
                p.close();shape(p)
                c.save();c.clipPath(p)
                for((x,y) in listOf(-.46f to -.15f,-.53f to -.48f,0f to -1f,.53f to -.48f,.46f to -.16f)) {
                    curve(0f,0f,x*h*.45f,y*h*.38f,x*h,y*h,0xffbfce95.toInt(),1.3f)
                    for(j in 2..5)for(side in listOf(-1,1)) {
                        val t=j/7f
                        curve(x*h*t,y*h*t,x*h*t+side*9,y*h*t-9,x*h*t+side*17,y*h*t-15,0xffa5bb80.toInt(),.6f)
                    }
                }
                if(detailed)for(i in 0..32) {
                    val x=sin(i*2.4f)*h*.22f;val y=-h*(.15f+(i%7)*.09f)
                    b.line(x,y,x+1,y-2,0xffcad2a5.toInt(),.45f)
                }
                c.restore()
            }
        } else leaf.draw(l.leaf,length)
    }
    private fun leaves() {
        if(onion) {
            b.at(105f,258f){tube(198f,12f,9f)}
            oval(177f,111f,18f,12f,green);oval(177f,111f,12f,7f,cream)
            b.at(225f,210f,0f,.78f){bulb(true)}
        } else {
            b.at(144f,265f){foodLeaf(198f)}
            if(cucumber)b.at(231f,270f,0f,1.2f){tendril()}
        }
    }
    private fun umbel(radius:Float) {
        for(i in 0..52) {
            val a=i*2.39996;val r=sqrt((i+.5)/53)*radius
            val x=(cos(a)*r).toFloat();val y=(sin(a)*r).toFloat()
            b.line(0f,radius*.65f,x,y,0xff82996b.toInt(),.65f)
            b.at(x,y,0f,.065f){radialFlower()}
        }
    }
    private fun habit() {
        if(onion) {
            b.at(147f,235f,0f,.70f){bulb(false)}
            for(i in -3..3)b.at(148f,211f,i*8f){tube(100f+(3-abs(i))*16f,i*8f,3.7f)}
            curve(150f,209f,161f,132f,161f,69f,green,4f)
            b.at(161f,57f){umbel(23f)}
        } else if(cabbage) {
            curve(166f,280f,171f,178f,160f,37f,green,4f)
            for(i in 0..6) {
                val y=241f-i*24;val side=if(i%2==0)-1 else 1
                b.at(168f,y,side*60f,.38f+(6-i)*.025f){foodLeaf(150f)}
                if(i>2) {
                    curve(165f,y,165f+side*27,y-18,165f+side*37,y-50,green,1.2f)
                    for(j in 0..3)b.at(165f+side*(30+j*3),y-38-j*8,0f,.085f){radialFlower()}
                }
            }
            for(i in 0..4)b.at(160f+(i%2)*11,34f+i*9,0f,.09f){radialFlower()}
            b.at(63f,229f,0f,.72f){
                for(i in -2..2)b.at(0f,30f,i*39f,.53f){foodLeaf(92f)}
                oval(0f,-5f,37f,33f,0xffa4b88a.toInt())
                for(i in -3..3)curve(i*4f,23f,i*19f,-9f,i*3f,-35f,0xffe1e4bb.toInt(),1f)
                curve(-33f,-12f,0f,6f,28f,-25f,0xff718e62.toInt(),1f)
            }
        } else if(cucumber) {
            b.line(187f,280f,187f,34f,0x66877967,2f)
            curve(134f,280f,173f,184f,180f,32f,green,2.8f)
            for(i in 0..5) {
                val y=245f-i*36;val x=146f+i*6;val side=if(i%2==0)-1 else 1
                b.at(x,y,side*53f,.34f){foodLeaf(155f)}
                b.at(x,y,-20f,.47f){tendril()}
                if(i==2 || i==4)b.at(x-9,y+8,0f,.20f){cucumberFruit(false)}
                if(i==1 || i==3)b.at(x+16,y-4,0f,.12f){radialFlower()}
            }
        } else if(peanut) {
            b.fill(Path().apply{addRect(30f,220f,275f,285f,Path.Direction.CW)},0x199b8151)
            b.line(28f,220f,277f,220f,0xffa89b79.toInt(),.8f)
            curve(147f,220f,146f,248f,152f,280f,straw,1.4f)
            for(i in -2..2)curve(149f,241f,149f+i*12,260f,149f+i*15,273f,straw,.7f)
            for(i in -2..2) {
                val x=148f+i*42;val top=42f+abs(i)*42
                curve(147f,220f,147f+i*35,185f,x,top,green,2f)
                for(j in 0..2)b.at(x+(147-x)*j*.24f,top+27+j*37,i*8f,.27f){foodLeaf(156f)}
                if(i!=0) {
                    b.at(x,177f,0f,.10f){flowers.blossom(FlowerShape.PEA)}
                    // Gynophore grows from an aerial node; its ovary is at the buried tip.
                    val angle=Math.toRadians(33.0)
                    val tipX=x+5f+sin(angle).toFloat()*180f*.17f
                    val tipY=264f-cos(angle).toFloat()*180f*.17f
                    curve(x,177f,x+13f,221f,tipX,tipY,0xffa36d49.toInt(),1.5f)
                    b.at(x+5f,264f,33f,.17f){legume(false)}
                }
            }
        } else {
            curve(151f,280f,145f,160f,151f,34f,green,3f)
            for(i in 0..6) {
                val y=250f-i*29;val side=if(i%2==0)-1 else 1;val x=150f+side*(29+(6-i)*4)
                curve(150f,y+13,150f+side*16,y,x,y-12,green,1.5f)
                b.at(x,y-8,side*16f,.35f){foodLeaf(166f)}
                if(i<4)b.at(150f+side*10,y+22,side*23f,.17f){legume(false)}
                else b.at(151f+side*8,y,0f,.075f){flowers.blossom(FlowerShape.PEA)}
            }
            b.at(151f,89f,0f,.28f){foodLeaf(166f)}
        }
    }
    private fun radialFlower() {
        if(cucumber) {
            val p=Path()
            for(i in 0..100) {
                val a=2*PI*i/100-PI/2;val r=48f+14f*cos(5*(a+PI/2)).toFloat()
                val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
            }
            p.close();shape(p,l.color.ink)
            repeat(5){i->b.at(0f,0f,i*72f){curve(0f,-7f,-4f,-32f,0f,-58f,0xffc9a63c.toInt(),1f)}}
            repeat(3){i->b.at(0f,0f,i*120f){oval(0f,-7f,6f,9f,0xffc4bb5c.toInt())}}
        } else {
            repeat(l.petals){i->b.at(0f,0f,i*360f/l.petals) {
                shape(Path().apply{moveTo(-4f,0f);cubicTo(-22f,-23f,-22f,-58f,0f,-62f);cubicTo(22f,-58f,22f,-23f,4f,0f);close()},l.color.ink)
                curve(0f,-4f,-2f,-34f,0f,-56f,if(onion)green else 0xffbca547.toInt(),.8f)
            }}
            val stamens=if(onion)(0..5).map { val a=it*PI/3;cos(a).toFloat()*25 to sin(a).toFloat()*25 }
                else listOf(-10f to -24f,10f to -24f,-10f to 24f,10f to 24f,-18f to 0f,18f to 0f)
            for((x,y) in stamens){b.line(x*.2f,y*.2f,x,y,cream,1.3f);oval(x,y,2.8f,4f,if(onion)cream else 0xffb7a14c.toInt())}
            oval(0f,0f,7f,8f,green)
            b.line(0f,0f,1f,-15f,0xffa9ba8c.toInt(),1.5f)
        }
    }
    private fun flowerPlate() {
        b.at(146f,133f) {if(onion || cabbage || cucumber)radialFlower() else flowers.blossom(FlowerShape.PEA)}
        if(onion) {b.line(80f,278f,80f,230f,green,2f);b.at(80f,221f){umbel(26f)}}
        else if(cucumber)b.at(225f,244f) {
            oval(0f,-7f,8f,25f,green)
            for(i in -1..1)b.line(i*4f,-27f,i*4f,13f,0xffabb779.toInt(),.6f)
            b.line(0f,18f,0f,38f,green,1.5f)
            b.at(0f,-40f,0f,.25f){radialFlower()}
        }
        else if(peanut) {
            b.line(215f,228f,241f,211f,green,2f)
            curve(231f,218f,231f,247f,219f,268f,0xff9f704d.toInt(),2f)
            oval(219f,270f,3f,5f,0xffa99866.toInt())
            b.line(197f,251f,263f,251f,straw,.8f)
        } else {
            b.line(72f,277f,72f,218f,green,2f)
            for(i in 0..4)b.at(72f+(if(i%2==0)-9 else 9),258f-i*8,0f,.10f){if(cabbage)radialFlower() else flowers.blossom(FlowerShape.PEA)}
        }
    }
    /** Pod from -180 to 0. Open peanut is a deliberate dissection, not natural dehiscence. */
    private fun legume(open:Boolean) {
        val p=Path().apply {
            moveTo(0f,-180f)
            if(peanut){cubicTo(-38f,-185f,-38f,-130f,-23f,-103f);quadTo(-15f,-90f,-24f,-77f);cubicTo(-40f,-42f,-30f,0f,0f,0f);cubicTo(31f,0f,40f,-42f,24f,-77f);quadTo(15f,-90f,23f,-103f);cubicTo(38f,-130f,38f,-185f,0f,-180f)}
            else {cubicTo(-26f,-171f,-25f,-129f,-22f,-109f);cubicTo(-27f,-70f,-25f,-25f,0f,0f);cubicTo(26f,-25f,26f,-70f,22f,-109f);cubicTo(25f,-145f,24f,-175f,0f,-180f)}
            close()
        }
        shape(p,if(open)cream else straw)
        c.save();c.clipPath(p)
        if(!open && peanut) {
            for(i in -4..4)curve(i*9f,-183f,i*13f,-90f,i*9f,3f,0xff8d764d.toInt(),.8f)
            for(i in 0..17)curve(-40f,-i*11f,0f,-i*11f+8,40f,-i*11f-3,0xff8d764d.toInt(),.8f)
        } else if(!open && detailed)for(i in 0..90) {
            val x=sin(i*2.4f)*25;val y=-5f-(i%29)*6
            b.line(x,y,x+3,y-5,0xffdfc797.toInt(),.75f)
        }
        if(open)for(y in if(peanut)listOf(-135f,-45f) else listOf(-146f,-90f,-34f)) {
            oval(0f,y,if(peanut)19f else 17f,if(peanut)31f else 22f,if(peanut)0xffb77b63.toInt() else 0xffd4bc79.toInt())
            if(!peanut)oval(7f,y,2.1f,7f,0xff80683e.toInt())
        }
        c.restore()
        if(!peanut)curve(0f,-177f,31f,-95f,0f,-3f,0xff7d724b.toInt(),1f)
    }
    private fun silique(open:Boolean) {
        b.line(0f,10f,0f,-181f,green,1.5f)
        val valve=Path().apply{moveTo(0f,-180f);quadTo(-12f,-130f,-9f,-22f);quadTo(0f,4f,9f,-22f);quadTo(12f,-130f,0f,-180f);close()}
        shape(valve,if(open)cream else straw)
        if(open) {
            b.at(-11f,0f,-13f){shape(valve,straw)}
            b.at(11f,0f,13f){shape(valve,straw)}
            for(i in 0..11){val y=-17f-i*12;val side=if(i%2==0)-1 else 1;b.line(0f,y,side*5f,y-2,green,.5f);oval(side*5f,y-2,3.4f,3.8f,0xff755532.toInt())}
        } else b.line(0f,-173f,0f,-9f,0xff82794d.toInt(),.8f)
    }
    private fun capsule(open:Boolean) {
        if(!open) {
            val p=Path()
            for(i in 0..180) {
                val a=2*PI*i/180-PI/2;val r=54f+6f*cos(3*(a+PI/2)).toFloat()
                val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
            }
            p.close();shape(p,straw)
            repeat(3){i->b.at(0f,0f,i*120f+60f){curve(0f,0f,9f,-22f,0f,-48f,0xff83734c.toInt(),1.1f)}}
            oval(0f,0f,3f,3f,0xff8b8054.toInt())
            return
        }
        repeat(3){i->b.at(0f,0f,i*120f) {
            shape(Path().apply{moveTo(0f,12f);cubicTo(-40f,2f,-35f,-56f,0f,-60f);cubicTo(35f,-56f,40f,2f,0f,12f);close()},if(open)cream else straw)
            curve(0f,10f,0f,-30f,0f,-57f,0xff8b7d55.toInt(),.8f)
            if(open)for(j in 0..1)shape(Path().apply{moveTo(-8f,-18f-j*18);lineTo(-1f,-27f-j*18);lineTo(9f,-21f-j*18);lineTo(6f,-10f-j*18);close()},0xff38392d.toInt())
        }}
    }
    private fun cucumberFruit(ripe:Boolean) {
        val p=Path().apply{moveTo(0f,-90f);cubicTo(-35f,-94f,-36f,-59f,-33f,0f);cubicTo(-31f,73f,-23f,89f,0f,90f);cubicTo(27f,89f,34f,62f,33f,0f);cubicTo(34f,-64f,31f,-89f,0f,-90f);close()}
        shape(p,if(ripe)0xffc3aa4c.toInt() else 0xff527845.toInt())
        for(i in -2..2)curve(i*9f,-83f,i*18f,0f,i*8f,83f,if(ripe)0xffdcc277.toInt() else 0xff82955c.toInt(),.9f)
        if(detailed)for(i in 0..58){val x=sin(i*2.4f)*27;val y=-76f+(i%20)*8;b.oval(x,y,1.1f,1.8f,0xffb3b681.toInt())}
        b.line(0f,-90f,3f,-101f,green,2f)
    }
    private fun fruitPlate() {
        if(onion) {
            b.at(111f,130f){capsule(false)}
            b.at(226f,223f,0f,.75f){capsule(true)}
            for(i in 0..2)b.at(69f+i*15,254f,i*25f){shape(Path().apply{moveTo(-5f,0f);lineTo(-4f,-10f);lineTo(3f,-13f);lineTo(7f,-5f);lineTo(4f,3f);close()},0xff33352a.toInt())}
        } else if(cabbage) {
            b.at(91f,262f,-6f){silique(false)};b.at(217f,260f,6f,.91f){silique(true)}
        } else if(cucumber) {
            b.at(100f,163f){cucumberFruit(false)}
            b.at(229f,129f,-8f,.56f){cucumberFruit(true)}
            b.at(225f,254f) {
                oval(0f,0f,37f,31f,green);oval(0f,0f,33f,27f,0xffdce1b9.toInt())
                repeat(3){i->b.at(0f,0f,i*120f) {
                    oval(0f,-11f,11f,13f,0xffb5c89a.toInt())
                    for(j in -1..1)b.at(j*5f,-14f+abs(j)*4,j*18f){oval(0f,0f,2.7f,5.3f,cream)}
                }}
            }
        } else {b.at(91f,266f,-7f){legume(false)};b.at(219f,256f,9f,.86f){legume(true)}}
    }
}
