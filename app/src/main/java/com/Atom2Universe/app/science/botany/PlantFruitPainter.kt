package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Fruit walls, accessory envelopes and seeds are drawn separately: a burr is not a nut shell.
 * References: Woodland Trust tree profiles; PlantNET and WFO floras; conifers.org/pi/.
 * Dehiscent fruits show one representative stage of opening, not every stage simultaneously.
 */
internal class PlantFruitPainter(private val c:Canvas,species:PlantSpecies,private val detailed:Boolean=true) {
    private val l=species.look
    private val d=l.fruitDetail
    private val b=BotanyInk(c)
    private val ink=0xff554e37.toInt()
    private val green=l.leafColor
    private val cream=0xffefe1b8.toInt()
    private val brown=0xff9b713f.toInt()

    fun plate()=b.at(150f,151f){
        if(PlantSpecialFruitPainter(c,l,detailed).draw())return@at
        when(l.fruit){
            FruitShape.INDEHISCENT,FruitShape.FOLLICLE -> Unit // handled by reusable special-fruit profiles
            FruitShape.ACORN -> acorns()
            FruitShape.NUT -> hazelnuts()
            FruitShape.BEECH -> beech()
            FruitShape.CHESTNUT -> chestnut(false)
            FruitShape.HORSECHESTNUT -> chestnut(true)
            FruitShape.DOUBLE_SAMARA -> pairedWings()
            FruitShape.SAMARA -> samaras()
            FruitShape.CONE -> cones()
            FruitShape.DRUPE,FruitShape.POME,FruitShape.HIP,FruitShape.BERRY -> fleshy()
            FruitShape.POD -> pods()
            FruitShape.CAPSULE -> capsule()
            FruitShape.SCHIZOCARP -> when(d.schizocarp){
                SchizocarpForm.PAIRED -> mericarps()
                SchizocarpForm.CRANESBILL -> cranesbill()
                SchizocarpForm.WHEEL -> wheel()
            }
            FruitShape.SILIQUE -> silique()
            FruitShape.SILICLE -> silicles()
            FruitShape.GRAIN -> grain()
            FruitShape.ACHENE -> achenes()
        }
    }

    private fun outline(q:Path,color:Int= brown,vertical:Boolean=false) {
        b.wash(q,-33f,-43f,if(vertical) 15f else 48f,if(vertical) 64f else -7f,
            intArrayOf(botanicalMix(color,cream,.58f),color,botanicalMix(color,ink,.48f)))
        b.stroke(q,botanicalMix(color,ink,.65f),.75f)
    }
    private fun body(x:Float,y:Float,rx:Float,ry:Float,color:Int) {
        val q=Path().apply{addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)}
        b.cloud(q,x-rx*.38f,y-ry*.4f,max(rx,ry)*1.85f,intArrayOf(botanicalMix(color,cream,.42f),color,botanicalMix(color,ink,.55f)))
        b.stroke(q,botanicalMix(color,ink,.6f),.65f)
    }
    private fun stalk(x:Float,y:Float,xx:Float,yy:Float,width:Float=2f) {
        b.stroke(Path().apply{moveTo(x,y);quadTo((x+xx)/2-6,(y+yy)/2,xx,yy)},green,width)
        if(detailed)b.stroke(Path().apply{moveTo(x-.4f,y);quadTo((x+xx)/2-6.4f,(y+yy)/2,xx-.4f,yy)},botanicalMix(green,cream,.5f),width*.25f)
    }
    private fun ribs(q:Path,count:Int,h:Float,w:Float,color:Int=0xffc2ad78.toInt()) {
        if(!detailed)return
        c.save();c.clipPath(q)
        repeat(count){i->val x=(i-(count-1)/2f)*w/count
            b.stroke(Path().apply{moveTo(x*.6f,-h);cubicTo(x*1.2f,-h*.4f,x*1.25f,h*.5f,x*.7f,h)},color,.55f)}
        c.restore()
    }
    private fun star(x:Float,y:Float,r:Float=8f,color:Int=ink) {
        repeat(5){i->val a=i*2*PI/5
            val q=Path().apply{moveTo(x-2,y);lineTo(x+cos(a).toFloat()*r,y+sin(a).toFloat()*r*.65f);lineTo(x+2,y+2);close()}
            b.fill(q,color)}
        b.oval(x,y,2f,1.7f,botanicalMix(color,cream,.4f))
    }

    private fun acorns() {
        b.line(-85f,-94f,66f,-72f,l.bark,4f)
        for(side in listOf(-1,1)){
            val x=side*35f;val y=-62f+l.fruitStalk*70f
            stalk(side*8f,-84f,x,y+20f,3f)
            b.at(x,y){
                val nut=Path().apply{moveTo(-25f,18f);cubicTo(-31f,48f,-23f,84f,0f,89f)
                    cubicTo(23f,84f,31f,48f,25f,18f);close()}
                outline(nut,0xffad854d.toInt());ribs(nut,13,100f,50f)
                b.line(0f,87f,0f,92f,ink,1.3f)
                val cup=Path().apply{moveTo(-29f,17f);quadTo(-29f,48f,0f,49f);quadTo(29f,48f,29f,17f);quadTo(0f,7f,-29f,17f);close()}
                outline(cup,0xff93815a.toInt())
                c.save();c.clipPath(cup)
                for(i in 0..5)for(j in -4..4)b.at(j*8f+(i%2)*4,15f+i*6){
                    val scale=Path().apply{moveTo(-4f,0f);quadTo(-4f,5f,0f,7f);quadTo(4f,5f,4f,0f);close()}
                    b.fill(scale,botanicalMix(0xffa99568.toInt(),cream,(i%2)*.12f));b.stroke(scale,0xff746847.toInt(),.55f)
                };c.restore()
                b.stroke(Path().apply{moveTo(-28f,17f);quadTo(0f,28f,28f,17f)},0xff776543.toInt(),1.2f)
            }
        }
    }

    private fun hazelnuts() {
        stalk(0f,-91f,0f,38f,3f)
        for(side in listOf(-1,1))stalk(0f,29f,side*27f,38f,2f)
        for(side in listOf(-1,1))b.at(side*36f,10f,side*20f){
            val shell=Path().apply{moveTo(0f,-49f);cubicTo(-45f,-29f,-42f,43f,-17f,51f)
                quadTo(0f,59f,19f,49f);cubicTo(42f,30f,40f,-28f,0f,-49f);close()}
            outline(shell,0xffb08249.toInt());ribs(shell,15,60f,60f,0xffcfaa74.toInt())
            body(0f,46f,20f,7f,0xffcdb389.toInt())
            // The leafy involucre is irregularly toothed and open around the nut.
            for(s in listOf(-1,1))b.at(s*21f,38f,s*18f){
                val husk=Path().apply{moveTo(0f,12f);quadTo(-18f,-4f,-16f,-30f)
                    lineTo(-20f,-52f);lineTo(-10f,-43f);lineTo(-8f,-65f);lineTo(0f,-50f);lineTo(9f,-71f)
                    lineTo(12f,-44f);lineTo(22f,-55f);quadTo(24f,-11f,0f,12f);close()}
                outline(husk,0xff91a367.toInt());ribs(husk,6,70f,32f,0xff657d48.toInt())
            }
        }
    }

    private fun beech() {
        stalk(0f,-103f,0f,18f,2.5f)
        // Four valves of the cupule, opened around the two three-sided nuts.
        for(i in 0..3)b.at(0f,12f,45f+i*90f){
            val valve=Path().apply{moveTo(0f,8f);quadTo(-50f,-40f,-17f,-77f);quadTo(0f,-98f,17f,-77f);quadTo(50f,-40f,0f,8f);close()}
            outline(valve,0xffa38b63.toInt())
            for(j in 0..18){val y=-18f-j*3.2f;val x=sin(j*2.4).toFloat()*22
                b.line(x,y,x+sin(j*1.7).toFloat()*6,y-8,0xff766348.toInt(),.7f)}
            b.stroke(Path().apply{moveTo(0f,6f);quadTo(-6f,-43f,0f,-78f)},0xffd7bc88.toInt(),1.4f)
        }
        for(side in listOf(-1,1))b.at(side*22f,9f,side*12f){
            val nut=Path().apply{moveTo(0f,-53f);lineTo(-24f,49f);quadTo(0f,59f,24f,49f);close()}
            outline(nut,0xff805837.toInt());b.line(0f,-51f,3f,51f,0xffdfba7a.toInt(),1.1f)
            ribs(nut,10,56f,45f,0xffbc9159.toInt())
        }
    }

    private fun chestnut(horse:Boolean) {
        val shellColor=if(horse)0xff94a36a.toInt() else 0xffa29553.toInt()
        body(0f,0f,76f,75f,shellColor)
        // Horse chestnut has sparse broad spines, sweet chestnut a dense, fine, branching burr.
        val n=if(horse)24 else if(detailed)240 else 90
        repeat(n){i->val a=i*2.399963;val r=sqrt(i.toFloat()/n)*75
            val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
            val length=if(horse)8f else 14f+(i%4)*2
            val dx=cos(a).toFloat()*length;val dy=sin(a).toFloat()*length
            if(horse){val q=Path().apply{moveTo(x-3,y+2);lineTo(x+dx,y+dy);lineTo(x+3,y-2);close()};outline(q,0xff6f834b.toInt())}
            else {b.line(x,y,x+dx,y+dy,0xff6d7140.toInt(),.85f);b.line(x,y,x+dx*.5f-4,y+dy*.7f,0xffc1b272.toInt(),.65f)}
        }
        val opening=Path().apply{moveTo(-52f,-31f);quadTo(0f,-57f,53f,-24f);lineTo(48f,47f);quadTo(0f,79f,-47f,46f);close()}
        b.fill(opening,0xff574b30.toInt());b.stroke(opening,0xffe3d6a7.toInt(),5f)
        if(horse){
            glossyNut(0f,16f,43f,43f,true)
            b.at(80f,81f,-25f,.36f){glossyNut(0f,0f,43f,43f,true)}
        }else {
            for(i in 0..2)b.at((i-1)*28f,17f,(i-1)*11f){
                val nut=Path().apply{moveTo(0f,-42f);cubicTo(-16f,-15f,-21f,13f,-20f,35f);quadTo(0f,47f,21f,33f)
                    cubicTo(24f,11f,15f,-23f,0f,-42f);close()}
                outline(nut,0xff98552f.toInt());ribs(nut,9,43f,34f,0xffbd8555.toInt())
                body(0f,33f,17f,7f,0xffc6a875.toInt())
                for(j in -2..2)b.line(0f,-41f,j*2f,-47f-abs(j),ink,.5f)
            }
        }
    }

    private fun glossyNut(x:Float,y:Float,rx:Float,ry:Float,scar:Boolean) {
        body(x,y,rx,ry,0xff8c492b.toInt())
        b.stroke(Path().apply{moveTo(x-rx*.6f,y-ry*.4f);quadTo(x-rx*.4f,y-ry*.85f,x+rx*.05f,y-ry*.79f)},0xffe0b780.toInt(),2.2f)
        if(scar){body(x+rx*.2f,y+ry*.42f,rx*.55f,ry*.31f,0xffcab186.toInt())
            if(detailed)repeat(35){i->val a=i*2.4;val r=sqrt(i/35f);b.oval(x+rx*.2f+cos(a).toFloat()*rx*.45f*r,y+ry*.42f+sin(a).toFloat()*ry*.24f*r,.7f,.6f,0xff927548.toInt())}}
    }

    private fun wing(length:Float=103f,width:Float=26f) {
        val q=Path().apply{moveTo(0f,5f);cubicTo(-width*.75f,-12f,-width*1.5f,-length*.86f,-width*.6f,-length)
            cubicTo(width*.45f,-length-8,width*.65f,-length*.63f,5f,-7f);close()}
        outline(q,0xffb9b57e.toInt())
        if(detailed){c.save();c.clipPath(q)
            for(i in 0..10)b.stroke(Path().apply{moveTo(0f,-4f);cubicTo(-6f-i*1.9f,-length*.23f,6f-i*3.8f,-length*.7f,10f-i*3.2f,-length-6)},0xff898d5a.toInt(),.55f)
            for(i in 1..7)b.stroke(Path().apply{moveTo(-width,i*-length/8);quadTo(-8f,i*-length/8+9,width,i*-length/8+3)},0xff989967.toInt(),.3f)
            c.restore()}
        body(0f,1f,11f,14f,0xff9a8658.toInt())
    }
    private fun pairedWings() {
        stalk(0f,-106f,0f,-22f,2f)
        for(side in listOf(-1,1))b.at(side*9f,-14f,180f+side*l.samaraAngle){wing()}
    }
    private fun samaras() {
        when(d.samara){
            SamaraForm.FOUR_WINGS -> Unit // drawn by PlantSpecialFruitPainter before dispatch
            SamaraForm.ORBICULAR -> for(i in -1..1)b.at(i*64f,abs(i)*28f,i*17f,.78f){
                stalk(0f,74f,0f,50f,1.6f)
                val q=Path().apply{moveTo(0f,51f);cubicTo(-62f,42f,-65f,-58f,-15f,-58f)
                    quadTo(-5f,-54f,0f,-37f);quadTo(5f,-54f,15f,-58f)
                    cubicTo(65f,-58f,62f,42f,0f,51f);close()}
                outline(q,0xffc5c393.toInt())
                if(detailed){c.save();c.clipPath(q)
                    repeat(26){j->val a=j*2*PI/26;b.stroke(Path().apply{moveTo(0f,-18f);quadTo(cos(a).toFloat()*28,-18f+sin(a).toFloat()*24,cos(a).toFloat()*63,sin(a).toFloat()*63)},0xff95956a.toInt(),.55f)}
                    c.restore()}
                body(0f,-18f,12f,17f,0xff9a9a69.toInt())
            }
            SamaraForm.TWO_WINGS -> {
                for(i in -1..1)b.at(i*75f,i%2*22f,i*17f,.85f){
                    for(side in listOf(-1,1)){
                        val q=Path().apply{moveTo(0f,-22f);cubicTo(side*43f,-57f,side*52f,12f,side*17f,30f);quadTo(side*7f,32f,0f,22f);close()}
                        outline(q,0xffc7b58a.toInt())
                        if(detailed)for(j in 0..8)b.stroke(Path().apply{moveTo(0f,-14f+j*4);quadTo(side*19f,-19f+j*5,side*36f,-22f+j*5)},0xffaa9e70.toInt(),.4f)
                    };body(0f,0f,8f,23f,0xff947148.toInt())
                    b.line(-3f,-20f,-6f,-28f,ink,.7f);b.line(3f,-20f,6f,-28f,ink,.7f)
                }
            }
            SamaraForm.THREE_LOBED_BRACT -> for(side in listOf(-1,1))b.at(side*41f,-4f,side*18f){
                stalk(0f,-87f,0f,-44f,1.5f)
                val q=Path().apply{moveTo(0f,-48f);quadTo(-8f,-42f,-38f,-23f);quadTo(-42f,-4f,-15f,13f)
                    quadTo(-24f,61f,0f,97f);quadTo(27f,45f,17f,6f);quadTo(43f,-11f,32f,-31f);quadTo(16f,-40f,0f,-48f);close()}
                outline(q,0xffb5b779.toInt());b.line(0f,-46f,0f,89f,green,.8f)
                for(i in 0..9){val y=-31f+i*11;for(s in listOf(-1,1))b.line(0f,y,s*(if(i<4)24f else 17f),y-14,0xff939859.toInt(),.6f)}
                body(0f,-34f,10f,14f,0xffa18957.toInt());for(i in -2..2)b.line(i*3f,-44f,i*3f,-23f,0xffdbc18a.toInt(),.6f)
            }
            SamaraForm.SINGLE -> repeat(3){i->b.at((i-1)*43f,3f,(i-1)*18f){
                stalk(0f,-92f,0f,-77f,1f)
                val q=Path().apply{moveTo(0f,-79f);quadTo(-27f,-53f,-12f,56f);quadTo(0f,86f,11f,54f);quadTo(27f,-49f,0f,-79f);close()}
                outline(q,0xffb4a36f.toInt());ribs(q,10,88f,33f,0xff8e8753.toInt());body(0f,45f,7f,23f,0xff927448.toInt())
            }}
        }
    }

    private fun cones() {
        if(d.cone==ConeForm.ALDER){
            b.line(-83f,-86f,74f,43f,l.bark,3f)
            repeat(3){i->b.at(-43f+i*43f,-35f+i*30f,20f,.47f){stalk(-8f,-49f,0f,-30f,3f);cone()}}
        }else {
            val y=if(l.coneUpright)95f else -103f
            b.line(-88f,y,88f,y-9,l.bark,4f)
            repeat(if(l.needleCount>2)5 else 10){i->val x=-78f+i*(if(l.needleCount>2)34 else 16);val baseY=y-(x+88f)*9/176
                if(l.needleCount>2)repeat(9){j->val a=PI*(.06+.88*j/8)
                    b.line(x,baseY,x+cos(a).toFloat()*19,baseY-sin(a).toFloat()*22,green,.85f)
                }else for(side in listOf(-1,1))repeat(l.needleCount){j->
                    b.stroke(Path().apply{moveTo(x,baseY);quadTo(x+6f,baseY+side*9,x+9f+(i%3)+j*5,baseY+side*(19f+(i%3)*2))},green,1.2f)}
            }
            val h=coneHalfLength()
            val centre=if(l.coneUpright)82f-h else -89f+h
            stalk(0f,y-4.5f,0f,if(l.coneUpright)centre+h-2 else centre-h+2,2.5f)
            b.at(0f,centre,if(l.coneUpright)180f else 0f){cone()}
        }
    }
    private fun coneHalfLength()=when(d.cone){ConeForm.PINE->59f;ConeForm.LARCH->42f;ConeForm.ALDER->38f;else->83f}
    private fun cone() {
        val h=coneHalfLength()
        val w=if(d.cone==ConeForm.FIR)29f else 35f
        val silhouette=Path().apply{addOval(-w,-h,w,h,Path.Direction.CW)}
        body(0f,0f,w,h,l.fruitColor)
        c.save();c.clipPath(silhouette)
        val rows=when(d.cone){ConeForm.LARCH,ConeForm.ALDER->7;ConeForm.PINE->9;else->15}
        for(i in 0 until rows){val y=-h+7+i*(2*h-12)/rows;val rw=sqrt((1-(y/h).pow(2)).coerceAtLeast(0f))*w
            for(j in -2..2){val x=j*rw*.37f+(i%2)*3f
                b.at(x,y){val sw=rw*.31f;val sh=if(d.cone==ConeForm.PINE)15f else 12f
                    val q=if(d.cone==ConeForm.PINE)Path().apply{moveTo(-sw,0f);lineTo(0f,-6f);lineTo(sw,0f);lineTo(sw*.6f,sh);lineTo(-sw*.6f,sh);close()}
                    else Path().apply{moveTo(-sw,0f);quadTo(-sw,sh,0f,sh+1);quadTo(sw,sh,sw,0f);quadTo(0f,-4f,-sw,0f);close()}
                    outline(q,botanicalMix(l.fruitColor,cream,(i%3)*.08f))
                    if(d.cone==ConeForm.PINE){b.line(-sw,2f,sw,2f,0xffd1b17c.toInt(),.9f);body(0f,3f,2.4f,2f,0xff66513b.toInt())}
                    else if(d.cone==ConeForm.FIR){b.stroke(Path().apply{moveTo(0f,1f);quadTo(-2f,11f,2f,16f)},0xffc7b37b.toInt(),1.4f)}
                    else if(detailed)for(k in -1..1)b.stroke(Path().apply{moveTo(k*2f,1f);quadTo(k*3f,6f,k*4f,sh-1)},0xffc6a375.toInt(),.5f)
                }
            }
        }
        c.restore();b.stroke(silhouette,ink,.65f)
    }

    private fun fleshy() {
        if(d.raceme){
            b.line(0f,-112f,0f,102f,green,2f)
            repeat(9){i->val y=-80f+i*21;val x=(if(i%2==0)-1 else 1)*(28f+(i%3)*8)
                stalk(0f,y-15f,x,y,1f);body(x,y,15f,15f*d.elongation,l.fruitColor)
                b.oval(x-4,y-5,3f,2f,0x77eee1c1)
            };return
        }
        val count=d.count;val large=count<=3;val radius=if(large)28f else 16f
        repeat(count){i->val a=i*2.399963;val r=if(large)46f else sqrt(i.toFloat()/count)*68
            val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r+22f
            stalk(0f,-96f,x,y-radius*d.elongation,if(large)1.8f else 1.1f)
        }
        repeat(count){i->val a=i*2.399963;val r=if(large)46f else sqrt(i.toFloat()/count)*68
            val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r+22f
            body(x,y,radius,radius*d.elongation,l.fruitColor)
            if(d.bloom){
                b.oval(x-radius*.18f,y-radius*.25f,radius*.76f,radius*.70f,0x487e8ca7)
                if(detailed)repeat(45){j->val t=j*2.4;val rr=sqrt(j/45f)*radius*.85f
                    b.oval(x+cos(t).toFloat()*rr,y+sin(t).toFloat()*rr,.6f,.65f,0x508f9aaf)}
            }else b.stroke(Path().apply{moveTo(x-radius*.52f,y-radius*.28f);quadTo(x-radius*.48f,y-radius*.62f,x-radius*.18f,y-radius*.62f)},botanicalMix(l.fruitColor,0xffffecd3.toInt(),.6f),1.6f)
            if(l.fruit==FruitShape.POME || d.persistentCalyx)star(x,y+radius*.7f,radius*.34f)
            // Rosa canina sheds its reflexed sepals as the hip ripens: retain the apical scar.
            if(l.fruit==FruitShape.HIP){body(x,y+radius*d.elongation,7f,3.5f,0xff8b6847.toInt())
                b.oval(x,y+radius*d.elongation,2.2f,1.5f,ink)}
            b.oval(x,y-radius*d.elongation,2f,1.1f,ink)
        }
        if(l.fruit==FruitShape.HIP && detailed)b.at(90f,91f,-20f,.65f){
            body(0f,0f,25f,43f,l.fruitColor);body(0f,0f,18f,36f,0xffe9bf86.toInt())
            repeat(11){i->val a=i*2.4;val r=sqrt(i/11f);val x=cos(a).toFloat()*10*r;val y=sin(a).toFloat()*27*r
                for(j in -1..1)b.line(x,y,x+j*5,y-9,0xffd5d0a1.toInt(),.55f)
                body(x,y,3.6f,5.5f,0xffac8b53.toInt())}
        }
    }

    private fun pods() {
        if(d.shortPod){shortPods();return}
        val w=d.podWidth
        repeat(3){i->b.at((i-1)*48f,-1f,(i-1)*19f){
            stalk(0f,-103f,0f,-80f,1.2f)
            val q=Path().apply{moveTo(0f,-83f);cubicTo(-w,-47f,-w*1.35f,37f,0f,89f)
                cubicTo(w*1.2f,45f,w,-46f,0f,-83f);close()}
            outline(q,if(i==2)0xff9e8150.toInt()else 0xffaaa06c.toInt());ribs(q,9,90f,w*1.6f)
            b.stroke(Path().apply{moveTo(0f,-80f);quadTo(-w*.35f,10f,0f,87f)},0xff6c7044.toInt(),1f)
            if(i==2){
                val open=Path().apply{moveTo(0f,-67f);quadTo(-w,0f,0f,74f);quadTo(w*.95f,0f,0f,-67f);close()}
                b.fill(open,0xff655a3c.toInt());b.stroke(open,0xffd5bb86.toInt(),1.3f)
            }
            repeat(d.podSeeds){j->val y=if(d.podSeeds==1)0f else -53f+j*106f/(d.podSeeds-1)
                body(if(i==2)0f else w*.19f,y,w*.48f,min(9f,45f/d.podSeeds),if(i==2)0xffad814e.toInt()else 0xffb7ae7a.toInt())
                if(i==2)b.line(-2f,y-2,2f,y-2,0xffd7bd86.toInt(),.7f)}
        }}
    }

    private fun shortPods() {
        // Clover pods are small and enclosed in the persistent calyx, not long pea-like legumes.
        for(side in listOf(-1,1))b.at(side*50f,0f,side*14f){
            if(side<0)for(j in -2..2){
                val q=Path().apply{moveTo(j*7f,48f);quadTo(j*20f,-3f,j*14f,-75f+abs(j)*10);quadTo(j*11f,2f,j*7f+5,48f);close()}
                outline(q,0xff9a9364.toInt())}
            val pod=Path().apply{moveTo(0f,-45f);cubicTo(-39f,-37f,-39f,33f,-4f,48f);cubicTo(33f,33f,40f,-29f,0f,-45f);close()}
            outline(pod,0xffc0aa78.toInt());ribs(pod,8,50f,50f)
            if(side>0){
                body(0f,0f,21f,34f,0xff766242.toInt())
                repeat(d.podSeeds){j->val y=if(d.podSeeds==1)0f else -22f+j*44f/(d.podSeeds-1)
                    body(0f,y,if(d.podSeeds==1)15f else 10f,if(d.podSeeds==1)23f else 8f,0xffb99953.toInt())}
            }
        }
    }

    private fun capsule() {
        when(d.capsule){
            CapsuleForm.HEART -> {
                stalk(0f,116f,0f,49f,1.8f)
                val q=Path().apply{moveTo(0f,49f);cubicTo(-73f,26f,-58f,-63f,-20f,-45f)
                    quadTo(-5f,-39f,0f,-20f);quadTo(5f,-39f,20f,-45f)
                    cubicTo(58f,-63f,73f,26f,0f,49f);close()}
                outline(q,0xffa1a470.toInt());ribs(q,15,60f,100f)
                b.line(0f,45f,0f,-19f,ink,1f);b.line(0f,-20f,0f,-53f,green,1.5f)
            }
            CapsuleForm.FOUR_LOBES -> {
                stalk(0f,-112f,0f,-50f,2f)
                repeat(4){i->b.at(0f,0f,i*90f){
                    val q=Path().apply{moveTo(0f,8f);cubicTo(-44f,-6f,-40f,-58f,0f,-72f);cubicTo(40f,-58f,44f,-6f,0f,8f);close()}
                    outline(q,0xffcb778c.toInt());b.stroke(Path().apply{moveTo(0f,-5f);quadTo(-12f,-31f,0f,-61f)},0xfff1b0b6.toInt(),2f)
                    body(0f,-31f,14f,22f,0xffdf8b32.toInt());b.oval(-4f,-39f,4f,8f,0xfff4be5a.toInt())
                }}
            }
            CapsuleForm.THREE_VALVES -> {
                repeat(3){i->b.at(0f,0f,i*120f){
                    val q=Path().apply{moveTo(0f,0f);quadTo(-46f,-48f,0f,-96f);quadTo(46f,-48f,0f,0f);close()}
                    outline(q,0xffb4a174.toInt());b.stroke(Path().apply{moveTo(0f,-5f);quadTo(-4f,-47f,0f,-88f)},0xff6f7046.toInt(),1f)
                    repeat(10){j->body((j%2-.5f)*17,-19f-j*6.5f,5.4f,6.6f,0xffab8451.toInt())}
                }}
            }
            CapsuleForm.PYXIS -> {
                val cup=Path().apply{moveTo(-43f,7f);quadTo(-45f,65f,0f,69f);quadTo(45f,65f,43f,7f);close()}
                outline(cup,0xffab9b6a.toInt());ribs(cup,18,70f,82f)
                body(0f,7f,43f,13f,0xff6e6340.toInt())
                val count=d.count
                repeat(count){i->val a=i*2.4;val r=sqrt(i.toFloat()/count)*27;body(cos(a).toFloat()*r,7f+sin(a).toFloat()*r*.29f,6f,5f,0xff826242.toInt())}
                b.at(12f,-38f,-14f){
                    val lid=Path().apply{moveTo(-44f,8f);quadTo(-30f,-34f,0f,-38f);quadTo(30f,-34f,44f,8f);quadTo(0f,31f,-44f,8f);close()}
                    outline(lid,0xffbcaa76.toInt());ribs(lid,12,43f,90f);b.stroke(Path().apply{moveTo(-43f,8f);quadTo(0f,23f,43f,8f)},ink,.7f)
                }
            }
            CapsuleForm.COTTON -> {
                stalk(0f,-105f,0f,77f,2f)
                repeat(5){i->b.at((if(i%2==0)-1 else 1)*21f,-55f+i*27f,(if(i%2==0)-1 else 1)*40f,.50f){
                    for(side in listOf(-1,1)){val q=Path().apply{moveTo(0f,30f);quadTo(side*45f,0f,side*21f,-42f);quadTo(side*12f,-7f,0f,30f);close()};outline(q,0xffa3a172.toInt())}
                    repeat(if(detailed)65 else 20){j->val a=j*2.399963;val r=35+sqrt(j/65f)*46
                        val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                        b.stroke(Path().apply{moveTo(0f,0f);quadTo(x*.35f+8,y*.7f,x,y)},0xffd8d0b7.toInt(),.7f)}
                    repeat(7){j->body((j%3-1)*7f,(j/3)*9f,3f,5f,0xff8c6a45.toInt())}
                }}
            }
            CapsuleForm.BASAL_PORES -> b.at(0f,0f,180f){closedCapsule()}
            else -> closedCapsule()
        }
    }
    private fun closedCapsule() {
        if(d.globoseCapsule)c.scale(1f,.72f)
        stalk(0f,118f,0f,61f,2.8f)
        val poppy=d.capsule==CapsuleForm.PORICIDAL_DISC
        val h=if(poppy)58f else 66f
        val q=if(poppy)Path().apply{moveTo(-42f,-48f);cubicTo(-58f,17f,-25f,61f,0f,63f);cubicTo(25f,61f,58f,17f,42f,-48f);close()}
        else Path().apply{moveTo(0f,-h);cubicTo(-24f,-54f,-55f,-14f,-39f,39f);quadTo(0f,84f,39f,39f);cubicTo(55f,-14f,24f,-54f,0f,-h);close()}
        outline(q,0xffb0a675.toInt());ribs(q,19,71f,80f,0xff827f4c.toInt())
        if(poppy){
            body(0f,-50f,44f,12f,0xff9e9865.toInt())
            repeat(12){i->val a=i*PI/6;b.line(0f,-50f,cos(a).toFloat()*43,-50f+sin(a).toFloat()*11,0xff686e47.toInt(),1.1f)}
            for(i in -3..3){b.oval(i*11f,-36f,3f,3.6f,ink);b.line(i*11f-4,-40f,i*11f+4,-40f,0xffdbca92.toInt(),.8f)}
        }else when(d.capsule){
            CapsuleForm.THREE_HORNS -> {
                for(i in -1..1)b.stroke(Path().apply{moveTo(i*17f,-40f);quadTo(i*32f,-62f,i*27f,-83f)},0xff7b7950.toInt(),3.3f)
                for(i in -1..1)b.stroke(Path().apply{moveTo(i*17f,-38f);quadTo(i*33f,0f,i*15f,54f)},ink,1.1f)
            }
            CapsuleForm.BASAL_PORES -> {
                for(x in listOf(-25f,0f,25f)){body(x,37f,4f,7f,0xff554f32.toInt());b.line(x-5,42f,x+4,44f,cream,.7f)}
                for(i in -2..2)b.stroke(Path().apply{moveTo(i*3f,-59f);quadTo(i*13f,-76f,i*15f,-66f)},green,1.6f)
            }
            CapsuleForm.APICAL_TEETH -> {
                body(0f,-51f,18f,9f,ink)
                repeat(d.capsuleTeeth){i->val a=i*2*PI/d.capsuleTeeth;val x=cos(a).toFloat()*19;val y=-51f+sin(a).toFloat()*8
                    val tooth=Path().apply{moveTo(x-4,y+2)
                        if(d.reflexedTeeth)quadTo(x*1.9f,y-20f,x*1.6f,y+6)else lineTo(x*1.4f,y-14)
                        lineTo(x+4,y+2);close()};outline(tooth,0xffbcb07a.toInt())}
            }
            else -> {
                // Persistent five-lobed calyx around an ovoid capsule (e.g. Digitalis).
                val sepals=l.petals.coerceIn(4,5)
                repeat(sepals){i->val side=i-(sepals-1)/2f;b.at(side*11f,56f,side*22f){
                    val sepal=Path().apply{moveTo(-5f,3f);quadTo(-15f,-24f,0f,-68f);quadTo(15f,-24f,5f,3f);close()}
                    outline(sepal,0xff8d965d.toInt())}}
                b.stroke(Path().apply{moveTo(0f,-65f);quadTo(-6f,-15f,0f,49f)},ink,1.1f)
            }
        }
        if(d.globoseCapsule)c.scale(1f,1f/.72f)
    }

    private fun mericarps() {
        for(side in listOf(-1,1))b.at(side*27f,0f,side*5f){
            val q=Path().apply{moveTo(0f,-78f);cubicTo(-29f,-55f,-27f,54f,0f,78f);cubicTo(27f,54f,29f,-55f,0f,-78f);close()}
            outline(q,0xffa79261.toInt())
            for(i in -2..2)b.stroke(Path().apply{moveTo(i*3f,-71f);cubicTo(i*10f,-31f,i*10f,31f,i*3f,71f)},0xffdbbd82.toInt(),1.4f)
            if(d.hookedRibs)for(row in 0..17)for(s in listOf(-1,1)){
                val y=-59f+row*7;val x=s*(18f-abs(y)*.09f)
                b.stroke(Path().apply{moveTo(x,y);quadTo(x+s*18,y-8,x+s*12,y-10)},0xff6d6740.toInt(),.9f)
            }
        }
        stalk(0f,112f,0f,69f,1.6f);b.line(0f,69f,-15f,-62f,0xff9c9861.toInt(),.8f);b.line(0f,69f,15f,-62f,0xff9c9861.toInt(),.8f)
    }

    private fun silique() {
        // Two valves separate from the persistent seed-bearing septum (replum).
        stalk(0f,117f,0f,91f,2f)
        val septum=Path().apply{moveTo(0f,-101f);quadTo(-12f,-50f,-10f,90f);quadTo(0f,100f,10f,90f);quadTo(12f,-50f,0f,-101f);close()}
        outline(septum,0xffcfbf86.toInt())
        for(s in listOf(-1,1)){
            val valve=Path().apply{moveTo(s*9f,87f);cubicTo(s*31f,26f,s*52f,-81f,s*36f,-97f)
                cubicTo(s*69f,-65f,s*41f,40f,s*9f,87f);close()}
            outline(valve,0xff939364.toInt())
            repeat(14){i->body(s*5f,-74f+i*11.5f,4f,3.3f,0xffa47a43.toInt())}
        }
        b.line(0f,-103f,0f,93f,0xff7b8150.toInt(),.7f)
    }

    private fun silicles() {
        b.line(0f,-109f,0f,115f,green,2f)
        repeat(5){i->val side=if(i%2==0)-1 else 1;val y=-68f+i*35
            b.line(0f,y+25f,side*48f,y,green,1.3f)
            b.at(side*48f,y,side*28f,.62f){
                val q=Path().apply{moveTo(0f,0f);lineTo(-36f,-54f);quadTo(-38f,-73f,-16f,-70f)
                    quadTo(-5f,-68f,0f,-60f);quadTo(5f,-68f,16f,-70f)
                    quadTo(38f,-73f,36f,-54f);close()}
                outline(q,0xffb3b47b.toInt());b.line(0f,0f,0f,-59f,green,1f)
                for(s in listOf(-1,1))for(j in 1..5){val yy=-12f-j*8
                    b.line(0f,yy+10,s*(j*3+2f),yy,0xffd8d4a1.toInt(),.7f)
                    body(s*(j*3+2f),yy,3.5f,2.4f,0xffb09b66.toInt())}
            }
        }
    }

    private fun cranesbill() {
        stalk(0f,116f,0f,61f,2f)
        for(i in -2..2)b.at(i*9f,47f,i*18f){
            val q=Path().apply{moveTo(-4f,10f);quadTo(-22f,-12f,0f,-36f);quadTo(22f,-12f,4f,10f);close()}
            outline(q,0xff929b63.toInt())
        }
        // Five mericarps remain at the base of a long, narrow beak before release.
        for(i in -2..2){body(i*12f,39f,9f,18f,0xffa68859.toInt())
            b.stroke(Path().apply{moveTo(i*12f,29f);quadTo(i*4f,-35f,0f,-102f)},0xff9b8d60.toInt(),2.2f)
            b.line(i*12f,28f,i*12f,48f,cream,.55f)}
        b.line(0f,-107f,0f,18f,ink,.7f)
    }

    private fun wheel() {
        repeat(5){i->b.at(0f,0f,i*72f){
            val q=Path().apply{moveTo(-10f,5f);quadTo(-42f,-35f,0f,-82f);quadTo(42f,-35f,10f,5f);close()}
            outline(q,0xff8e9761.toInt())}}
        repeat(11){i->b.at(0f,0f,i*360f/11){
            val q=Path().apply{moveTo(-5f,-14f);lineTo(-17f,-54f);quadTo(0f,-65f,17f,-54f);lineTo(5f,-14f);close()}
            outline(q,0xffb39c6c.toInt());ribs(q,6,62f,27f)
            if(detailed)for(j in 0..4)b.line(-12f,-29f-j*5,12f,-29f-j*5,0xff998252.toInt(),.5f)
        }}
        body(0f,0f,14f,14f,0xffcdc399.toInt())
    }
    private fun grain() {
        c.save();c.scale(1f/d.elongation,1f)
        val q=Path().apply{moveTo(0f,-89f);cubicTo(-45f,-77f,-41f,58f,-10f,80f);quadTo(0f,95f,12f,79f);cubicTo(42f,52f,39f,-74f,0f,-89f);close()}
        outline(q,0xffc0a26b.toInt());ribs(q,23,95f,63f,0xffad8f5b.toInt())
        b.stroke(Path().apply{moveTo(0f,-76f);cubicTo(-9f,-28f,6f,27f,0f,78f)},0xff846638.toInt(),3f)
        b.stroke(Path().apply{moveTo(2f,-72f);cubicTo(-5f,-28f,9f,27f,3f,75f)},0xffe4cc93.toInt(),.9f)
        body(-12f,65f,6f,10f,0xffd7b980.toInt())
        c.restore()
    }

    private fun achene(size:Float=1f,beak:Boolean=false) = b.at(0f,0f,0f,size){
        val w=d.acheneWidth
        val q=Path().apply{moveTo(-w*.58f,-24f);cubicTo(-w*1.3f,-10f,-w,21f,0f,28f)
            cubicTo(w,19f,w*1.3f,-12f,w*.58f,-24f);quadTo(0f,-27f,-w*.58f,-24f);close()}
        outline(q,0xffaa9563.toInt());ribs(q,d.acheneRibs,31f,w*2,0xffd3bd86.toInt())
        if(beak)b.stroke(Path().apply{moveTo(1f,-24f);quadTo(6f,-40f,14f,-33f)},ink,1.5f)
        if(d.pappusScales)for(i in -3..3){
            val q=Path().apply{moveTo(i*2.5f-1,-23f);lineTo(i*3f,-29f);lineTo(i*2.5f+1,-23f);close()}
            b.fill(q,cream);b.stroke(q,ink,.35f)
        }
    }
    private fun achenes() {
        when(d.achene){
            AcheneForm.LINDEN -> {
                val bract=Path().apply{moveTo(20f,88f);cubicTo(-2f,49f,31f,-66f,74f,-101f)
                    cubicTo(107f,-98f,91f,-28f,20f,88f);close()}
                outline(bract,0xffb2ad73.toInt());b.stroke(Path().apply{moveTo(20f,87f);quadTo(48f,-9f,76f,-94f)},green,.9f)
                repeat(4){i->val x=-75f+i*30;val y=10f+i%2*32;stalk(40f,-30f,x,y,1.4f);body(x,y,16f,18f,0xffa69464.toInt())
                    if(d.prominentRibs)for(j in -2..2)b.stroke(Path().apply{moveTo(x+j*2f,y-16f);quadTo(x+j*8f,y,x+j*2f,y+16f)},0xff776b42.toInt(),1.1f)
                }
            }
            AcheneForm.AGGREGATE -> {
                stalk(0f,118f,0f,51f,2.2f);body(0f,0f,51f,51f,0xff8e9558.toInt())
                repeat(29){i->val a=i*2.399963;val r=sqrt(i/29f)*51
                    b.at(cos(a).toFloat()*r,sin(a).toFloat()*r,i*27f,.46f){achene(beak=d.beak)}}
                if(detailed)b.at(85f,85f,22f,1.05f){achene(beak=d.beak)}
            }
            AcheneForm.FOUR_NUTLETS -> {
                repeat(5){i->b.at(0f,0f,i*72f){
                    val calyx=Path().apply{moveTo(-17f,6f);quadTo(-32f,-41f,0f,-83f);quadTo(32f,-41f,17f,6f);close()}
                    outline(calyx,0xffa0a06a.toInt());b.line(0f,0f,0f,-72f,green,.8f)}}
                for(x in listOf(-18f,18f))for(y in listOf(-23f,23f))body(x,y,15f,22f,0xff886a44.toInt())
            }
            else -> repeat(5){i->b.at((i-2)*39f,(i%2)*21f,(i-2)*15f){achene(1.1f)
                if(d.achene==AcheneForm.PAPPUS)repeat(24){j->val a=PI*(1.08+.84*j/23);val x=cos(a).toFloat()*29;val y=-24f+sin(a).toFloat()*35
                    b.line(0f,-25f,x,y,0xffb6a887.toInt(),.65f)}
            }}
        }
    }
}
