package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Original botanical plates: corolla symmetry, insertion, stamens and inflorescence are separate traits.
 * Morphology references: PlantNET (Papaver, Poaceae), World Flora Online (Viola, Digitalis, Primula).
 * Small inset flowers are enlarged independently; the plate's scale remains indicative.
 */
internal class PlantFlowerPainter(private val c:Canvas,species:PlantSpecies,private val detailed:Boolean=true) {
    private val l=species.look
    private val d=l.flowerDetail
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val ink=0xff48523a.toInt()
    private val cream=0xfffff9e9.toInt()
    private val color=l.color.ink
    private val edge=botanicalMix(color,0xff66474d.toInt(),.48f)
    private val gold=0xffcfaa39.toInt()

    fun plate() {
        val pendant=d.corolla in listOf(CorollaForm.FOXGLOVE,CorollaForm.HAREBELL,CorollaForm.URN,CorollaForm.BAOBAB,CorollaForm.SESAME)
        if(l.flower !in listOf(FlowerShape.CATKIN,FlowerShape.CONIFER,FlowerShape.GRASS,FlowerShape.SPIKE,FlowerShape.PANICLE,FlowerShape.RACEME)
            && d.cluster !in listOf(FlowerCluster.UNILATERAL,FlowerCluster.AXILLARY,FlowerCluster.RACEMOSE,FlowerCluster.PENDULOUS_UMBEL) && !pendant) {
            val offset=if(d.corolla==CorollaForm.COWSLIP)-25f else 0f
            b.stroke(Path().apply {moveTo(145f+offset,277f);quadTo(150f+offset,230f,150f+offset,if(l.flower==FlowerShape.APETALOUS)151f else 184f)},green,2.5f)
            b.line(146f+offset,269f,149f+offset,219f,botanicalMix(green,cream,.4f),.6f)
        }
        b.at(150f,151f) { head() }
    }

    fun head() {
        if(d.cluster==FlowerCluster.TERMINAL_TRUSS){
            for(i in 0..6){val a=i*2.399963;val r=if(i==0)0f else 55f;val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
                b.line(0f,99f,x,y,green,1.1f);b.at(x,y,i*21f,.52f){blossom()}
            };return
        }
        if(d.cluster==FlowerCluster.PENDULOUS_UMBEL){
            b.line(-70f,-100f,70f,-80f,l.bark,3f)
            repeat(9){i->val x=(i-4)*20f;val y=12f+(i%3)*25f
                b.stroke(Path().apply{moveTo(0f,-89f);quadTo(x,-45f,x,y)},green,.9f)
                b.at(x,y,180f,.18f){nakedFlower()}
            };return
        }
        if(d.cluster==FlowerCluster.BRUSH_SPIKE){brushSpike();return}
        if(d.cluster==FlowerCluster.AXILLARY){
            b.line(-84f,79f,76f,-92f,l.bark,3f)
            repeat(4){i->val x=-53f+i*34;val y=49f-i*37
                repeat(3){j->val xx=x+(j-1)*22;val yy=y-20f-abs(j-1)*6
                    b.line(x,y,xx,yy,green,1f);b.at(xx,yy,j*25f,.22f){blossom()}
                }
            };return
        }
        when(l.flower) {
            FlowerShape.GLOBOSE_HEAD -> globoseHead()
            FlowerShape.CATKIN -> catkins()
            FlowerShape.CONIFER -> pollenCones()
            FlowerShape.CORYMB,FlowerShape.UMBEL -> flatCluster()
            FlowerShape.PANICLE -> panicle()
            FlowerShape.RACEME -> raceme()
            FlowerShape.SPIKE -> spike()
            FlowerShape.GRASS -> grassHead()
            FlowerShape.PEA -> when(d.cluster) {
                FlowerCluster.RACEMOSE -> raceme()
                FlowerCluster.CLOVER -> {
                    for(i in 0..45) {val a=i*2.399963;val r=sqrt(i/45f)*58
                        b.at(cos(a).toFloat()*r,sin(a).toFloat()*r,atan2(sin(a),cos(a)).toFloat()*180f/PI.toFloat()+90,.24f){pea()}
                    }
                }
                FlowerCluster.UNILATERAL -> {
                    b.stroke(Path().apply{moveTo(-15f,100f);quadTo(-25f,0f,-17f,-98f)},green,2f)
                    repeat(12){i->val y=-86f+i*14; b.line(-18f,y-3,3f,y,green,.8f);b.at(7f,y,73f,.30f){pea()}}
                }
                FlowerCluster.SMALL_UMBEL -> {
                    repeat(5){i->val x=(i-2)*29f;val y=-25f+abs(i-2)*17
                        b.line(0f,65f,x,y+15,green,1.4f);b.at(x,y,(i-2)*18f,.46f){pea()}}
                }
                else -> pea()
            }
            else -> blossom()
        }
    }

    fun blossom(shape:FlowerShape=l.flower) {
        when(d.corolla) {
            CorollaForm.BAOBAB,CorollaForm.TAMARIND,CorollaForm.SENNA,CorollaForm.CORONA,
            CorollaForm.HIBISCUS,CorollaForm.SESAME -> PlantCorollaPainter(c,l,detailed).draw()
            CorollaForm.GOLDEN_CUP,CorollaForm.FUNNEL -> PlantAdditionalFlowerPainter(c,l,detailed).corolla()
            CorollaForm.POPPY -> poppy()
            CorollaForm.VIOLET -> violet()
            CorollaForm.COWSLIP -> cowslip()
            CorollaForm.FOXGLOVE -> foxglove()
            CorollaForm.HAREBELL -> bell(false)
            CorollaForm.HEATHER -> heather()
            CorollaForm.URN -> bell(true)
            CorollaForm.BIFID -> bifid()
            CorollaForm.MALLOW -> mallow()
            CorollaForm.SPEEDWELL -> speedwell()
            CorollaForm.RADIAL -> when(shape) {
                FlowerShape.DAISY -> daisy()
                FlowerShape.LIGULATE -> ligulate()
                FlowerShape.THISTLE -> thistle()
                FlowerShape.PEA -> pea()
                FlowerShape.BELL -> bell(false)
                FlowerShape.LIPPED -> lipped()
                FlowerShape.APETALOUS -> nakedFlower()
                else -> radial()
            }
        }
    }

    /** Each petal carries a translucent wash and diverging veins, clipped to its own outline. */
    private fun petal(outline:Path,shade:Int=color,tipY:Float=-68f,veins:Int=7) {
        b.wash(outline,-23f,tipY,30f,12f,intArrayOf(botanicalMix(shade,cream,.62f),shade,botanicalMix(shade,edge,.42f)))
        b.stroke(outline,botanicalMix(shade,edge,.65f),.65f)
        if(detailed) {
            c.save();c.clipPath(outline)
            repeat(veins){i->val x=(i-(veins-1)/2f)*7
                b.stroke(Path().apply {moveTo(0f,5f);cubicTo(x*.25f,-16f,x*.85f,tipY*.72f,x,tipY-8)},botanicalMix(shade,edge,.35f),.42f)
                b.stroke(Path().apply {moveTo(x*.35f,tipY*.35f);quadTo(x*.7f+4,tipY*.6f,x+9,tipY-4)},botanicalMix(shade,cream,.37f),.4f)
            };c.restore()
        }
    }

    private fun sepal(length:Float=40f,width:Float=7f) {
        val q=Path().apply{moveTo(-3f,5f);quadTo(-width,-length*.5f,0f,-length);quadTo(width,-length*.5f,3f,5f);close()}
        b.wash(q,-width,0f,width,-length,intArrayOf(green,botanicalMix(green,cream,.5f)))
        b.stroke(q,ink,.55f);b.line(0f,2f,0f,-length+4,ink,.5f)
    }

    private fun radial(sterile:Boolean=false) {
        if(l.petals==0){nakedFlower();return}
        repeat(l.petals){i->b.at(0f,0f,360f*(i+.5f)/l.petals){sepal(45f,8f)}}
        repeat(l.petals){i->b.at(0f,0f,360f*i/l.petals){
            val w=d.petalWidth
            val q=Path().apply{moveTo(0f,7f);cubicTo(-w,-6f,-w*1.2f,-51f,-7f,-64f)
                quadTo(-1f,-67f,0f,-63f);quadTo(6f,-69f,12f,-62f);cubicTo(w*1.3f,-44f,w,-6f,0f,7f);close()}
            petal(q)
            if(d.throatMark)b.oval(0f,-21f,6f,11f,0xffdfae42.toInt())
        }}
        if(!sterile){
            stamens(17f,d.stamens)
            b.oval(0f,0f,8f,8f,0xff9f9d4d.toInt())
            if(d.styles>0)repeat(d.styles){i->val x=(i-(d.styles-1)/2f)*8
                b.stroke(Path().apply{moveTo(x*.3f,3f);quadTo(x,-6f,x,-19f)},0xffc2ca8d.toInt(),1.6f)
                b.oval(x,-19f,2.6f,2f,0xffd9dca0.toInt())
            }else repeat(7){i->val a=i*2.4;b.oval(cos(a).toFloat()*5,sin(a).toFloat()*5,1.4f,1.6f,0xffd6cf7c.toInt())}
        }
    }

    private fun stamens(radius:Float,count:Int,anther:Int=gold,filament:Int=0xffafa56e.toInt()) {
        repeat(count){i->val a=2*PI*i/count
            val shorter=d.shortStamens==2 && count==6 && i%3==0
            val r=radius*(if(shorter).60f else .88f+(i%3)*.06f)
            val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
            b.stroke(Path().apply{moveTo(x*.2f,y*.2f);quadTo(x*.65f-2,y*.7f,x,y)},filament,.65f)
            b.at(x,y,i*360f/count){b.oval(0f,0f,1.6f,2.6f,anther);b.line(0f,-1.5f,0f,1.5f,botanicalMix(anther,ink,.35f),.4f)}
        }
    }

    private fun bifid() {
        repeat(5){i->b.at(0f,0f,i*72f+36){sepal(45f,7f)}}
        repeat(5){i->b.at(0f,0f,i*72f){
            val q=Path().apply{moveTo(-3f,5f);lineTo(-6f,-29f)
                cubicTo(-37f,-45f,-32f,-78f,-16f,-73f)
                lineTo(0f,-74f*(1-d.notchDepth));lineTo(16f,-73f)
                cubicTo(32f,-78f,37f,-45f,6f,-29f);lineTo(3f,5f);close()}
            petal(q,tipY=-75f)
        }}
        if(d.female)repeat(5){i->b.at(0f,0f,i*72f){
            b.stroke(Path().apply{moveTo(0f,0f);quadTo(-7f,-11f,0f,-24f)},cream,1.6f)
        }}else stamens(18f,d.stamens)
        if(d.fusedCalyx && detailed)b.at(86f,83f,-25f,.48f){
            val q=Path().apply{moveTo(-13f,34f);cubicTo(-35f,12f,-26f,-26f,-17f,-37f)
                lineTo(18f,-37f);cubicTo(26f,-16f,33f,12f,13f,34f);close()}
            b.fill(q,botanicalMix(green,color,.3f));b.stroke(q,ink,.8f)
            for(i in -3..3)b.stroke(Path().apply{moveTo(i*3f,32f);quadTo(i*9f,-4f,i*5f,-36f)},edge,.6f)
            repeat(5){i->b.at((i-2)*7f,-35f,(i-2)*20f,.35f){radial(true)}}
        }
    }

    private fun mallow() {
        repeat(5){i->b.at(0f,0f,i*72f){
            petal(Path().apply{moveTo(0f,8f);cubicTo(-26f,-7f,-49f,-66f,-24f,-72f)
                quadTo(-8f,-80f,0f,-61f);quadTo(10f,-80f,27f,-71f)
                cubicTo(48f,-60f,24f,-5f,0f,8f);close()},tipY=-74f)
            for(j in -1..1)b.stroke(Path().apply{moveTo(j*2f,-3f);quadTo(j*10f,-27f,j*15f,-58f)},0xff803c7e.toInt(),1.2f)
        }}
        // Fused staminal column with anthers along its upper part.
        b.stroke(Path().apply{moveTo(0f,10f);quadTo(-8f,-6f,0f,-26f)},cream,8f)
        repeat(22){i->val y=4f-i*1.4f;val x=sin(i*2.4).toFloat()*7
            b.line(0f,y,x,y-3,cream,.7f);b.oval(x,y-3,2f,1.8f,0xffcab8ae.toInt())}
        repeat(7){i->b.line(0f,-25f,(i-3)*2.5f,-33f-abs(i-3),cream,.7f)}
    }

    private fun speedwell() {
        for((i,a) in listOf(0f,90f,180f,270f).withIndex())b.at(0f,0f,a,if(i==2).65f else 1f){
            petal(Path().apply{moveTo(0f,6f);cubicTo(-31f,-5f,-43f,-60f,-7f,-66f)
                cubicTo(33f,-76f,42f,-20f,0f,6f);close()})
            for(j in -2..2)b.stroke(Path().apply{moveTo(0f,0f);quadTo(j*6f,-24f,j*9f,-51f)},edge,.8f)
        }
        b.oval(0f,0f,13f,10f,cream)
        for(s in listOf(-1,1)){b.stroke(Path().apply{moveTo(0f,0f);quadTo(s*20f,-9f,s*26f,-27f)},cream,1.6f)
            b.oval(s*26f,-27f,4f,2.5f,0xff775d8a.toInt())}
        b.line(0f,3f,1f,27f,cream,1.1f)
    }

    private fun ligulate() {
        // Each strap is one five-toothed floret; there is no central disc of tubular florets.
        repeat(2){row->val n=if(row==0)13 else 7
            repeat(n){i->b.at(0f,0f,i*360f/n+row*17f,if(row==0)1f else .58f){
                val q=Path().apply{moveTo(-2f,5f);lineTo(-8f,-30f);quadTo(-19f,-48f,-18f,-73f)
                    for(j in 0..4){lineTo(-18f+j*9,-79f);lineTo(-14f+j*9,-73f)}
                    quadTo(21f,-48f,8f,-30f);lineTo(2f,5f);close()}
                petal(q,tipY=-78f,veins=5)
                b.line(0f,-5f,0f,-25f,cream,1.1f)
                b.stroke(Path().apply{moveTo(0f,-25f);quadTo(-8f,-32f,-5f,-35f)},cream,.9f)
                b.stroke(Path().apply{moveTo(0f,-25f);quadTo(8f,-32f,5f,-35f)},cream,.9f)
            }}
        }
    }

    private fun raceme() {
        if(d.denseRaceme) {
            b.line(0f,113f,0f,-107f,green,2f)
            for(i in 0..15)for(side in listOf(-1,1)) {
                val y=-91f+i*12f;val x=side*(12f+i*.6f)
                b.line(0f,y+5,x,y,green,.7f)
                b.at(x,y,side*24f,.15f+i*.004f){blossom()}
            };return
        }
        val pendant=d.cluster==FlowerCluster.PENDANT_RACEME
        b.stroke(Path().apply{moveTo(0f,if(pendant)-108f else 111f);quadTo(-10f,0f,0f,if(pendant)100f else -102f)},green,2f)
        repeat(9){i->val y=-78f+i*20;val side=if(i%2==0)-1 else 1
            val x=side*(24f+(i%3)*7);val yy=y+(if(pendant)12f else -14f)
            b.line(-3f,y,x,yy,green,1f)
            b.at(x,yy,side*15f,if(l.flower==FlowerShape.PEA).28f else .29f){blossom(if(l.flower==FlowerShape.RACEME)FlowerShape.STAR else l.flower)}
        }
    }

    private fun poppy() {
        // Four broad crumpled petals, rather than a radial rosette of narrow lobes.
        repeat(4){i->b.at(0f,0f,45f+i*90f){
            val q=Path().apply{moveTo(0f,14f);cubicTo(-40f,-4f,-60f,-40f,-47f,-62f)
                cubicTo(-42f,-70f,-35f,-64f,-29f,-73f);quadTo(-14f,-83f,0f,-75f)
                cubicTo(17f,-84f,27f,-70f,36f,-73f);cubicTo(64f,-53f,44f,-7f,0f,14f);close()}
            petal(q,0xffdf493a.toInt(),-78f,11)
            petal(Path().apply{moveTo(0f,10f);quadTo(-26f,-3f,-20f,-19f);quadTo(0f,-12f,20f,-19f);quadTo(26f,-3f,0f,10f);close()},0xff413232.toInt(),-18f,3)
        }}
        stamens(27f,46,0xff40363c.toInt(),0xff574847.toInt())
        val disc=Path().apply{addOval(-15f,-12f,15f,12f,Path.Direction.CW)}
        b.cloud(disc,-5f,-5f,23f,intArrayOf(0xffc6be87.toInt(),0xff7c8355.toInt()))
        repeat(10){i->val a=i*PI/5;b.line(0f,0f,cos(a).toFloat()*14,sin(a).toFloat()*11,0xff525b3d.toInt(),1f)}
        b.oval(0f,0f,3f,2f,0xffb3ae75.toInt())
    }

    private fun violet() {
        // Exactly five unequal petals: two upper, two lateral, one lower with nectar guides.
        for(a in listOf(-27f,27f, -83f,83f)) b.at(0f,0f,a,if(abs(a)>50).9f else 1f) {
            petal(Path().apply{moveTo(0f,8f);cubicTo(-27f,-9f,-30f,-65f,-5f,-70f);cubicTo(29f,-73f,24f,-14f,0f,8f);close()})
        }
        b.at(0f,4f,180f){petal(Path().apply{moveTo(0f,0f);cubicTo(-32f,-6f,-48f,-48f,-23f,-59f)
            quadTo(-8f,-68f,0f,-60f);quadTo(11f,-68f,25f,-58f);cubicTo(49f,-41f,28f,-5f,0f,0f);close()})
            for(i in -2..2)b.stroke(Path().apply{moveTo(i*2f,-5f);quadTo(i*5f,-22f,i*9f,-38f)},0xff603969.toInt(),1f)
        }
        b.oval(0f,4f,8f,6f,0xfff4edca.toInt());b.oval(0f,1f,4f,3f,gold)
        // Lateral inset exposes the nectar spur hidden in a face-on view.
        if(detailed)b.at(80f,78f,-25f,.42f){
            petal(Path().apply{moveTo(-9f,-8f);quadTo(-56f,-15f,-48f,0f);quadTo(-27f,12f,-8f,9f)
                cubicTo(12f,43f,37f,24f,23f,2f);cubicTo(40f,-31f,8f,-39f,-9f,-8f);close()})
            b.line(-8f,9f,0f,48f,green,2f)
        }
    }

    private fun daisy() {
        if(d.discHeight>0f){PlantAdditionalFlowerPainter(c,l,detailed).raisedHead();return}
        repeat(d.rays){i->b.at(0f,0f,i*360f/d.rays+(i%2)*1.7f){
            val len=61f+(i%3)*3
            val w=if(d.rays<10)12f else 6f
            val q=Path().apply{moveTo(-2f,-16f);cubicTo(-w,-30f,-w-2,-len+6,-w*.65f,-len)
                lineTo(-2f,-len-2);lineTo(0f,-len);lineTo(2f,-len-2);lineTo(w*.7f,-len)
                cubicTo(w+2,-len+7,w,-30f,2f,-16f);close()}
            petal(q,if(i%3==0)botanicalMix(color,0xffd69fa8.toInt(),.10f)else color,-len,3)
        }}
        val disc=Path().apply{addCircle(0f,0f,d.discRadius,Path.Direction.CW)}
        b.cloud(disc,-8f,-9f,36f,intArrayOf(botanicalMix(d.discColor,cream,.25f),botanicalMix(d.discColor,ink,.20f)))
        repeat(if(detailed)170 else 50){i->val n=if(detailed)170f else 50f;val a=i*2.399963;val r=sqrt(i/n)*(d.discRadius-1f)
            b.at(cos(a).toFloat()*r,sin(a).toFloat()*r){
                b.oval(0f,0f,1.7f,1.6f,botanicalMix(d.discColor,ink,.22f))
                if(detailed)repeat(5){j->val t=j*2*PI/5;b.oval(cos(t).toFloat(),sin(t).toFloat(),.65f,.65f,botanicalMix(d.discColor,cream,.45f))}
                b.oval(0f,0f,.6f,.7f,0xff7f752e.toInt())
            }
        }
        if(detailed && d.rays>10)b.at(73f,87f,-16f,.34f){
            for(i in -3..3)b.at(i*6f,3f,i*12f){sepal(24f,6f)}
            b.oval(0f,-16f,25f,8f,gold)
            for(i in -5..5)b.stroke(Path().apply{moveTo(i*4f,-13f);quadTo(i*9f,-14f,i*11f,-3f)},color,4f)
            b.line(0f,12f,0f,47f,green,3f)
        }
        if(d.hollowReceptacle && detailed)b.at(-83f,85f,0f,.48f){
            // Longitudinal section of the hollow, conical receptacle of Matricaria.
            val q=Path().apply{moveTo(-32f,11f);quadTo(-28f,-16f,0f,-60f);quadTo(28f,-16f,32f,11f);close()}
            b.fill(q,gold);b.stroke(q,ink,1f)
            val hollow=Path().apply{moveTo(-21f,10f);quadTo(-17f,-16f,0f,-43f);quadTo(17f,-16f,21f,10f);close()}
            b.fill(hollow,cream);b.stroke(hollow,edge,.7f)
            b.line(0f,11f,0f,44f,green,2f)
        }
    }

    private fun pea() {
        for(i in -2..2)b.at(0f,30f,i*25f){sepal(41f,7f)}
        petal(Path().apply{moveTo(0f,8f);cubicTo(-73f,1f,-65f,-68f,-16f,-60f);quadTo(0f,-51f,16f,-60f)
            cubicTo(65f,-68f,73f,1f,0f,8f);close()},color,-62f,9)
        for(side in listOf(-1,1))b.at(side*9f,13f,side*68f){
            petal(Path().apply{moveTo(0f,0f);cubicTo(-19f,-12f,-28f,-52f,-5f,-48f);cubicTo(20f,-42f,24f,-8f,0f,0f);close()},color,-49f,4)
        }
        petal(Path().apply{moveTo(-22f,8f);cubicTo(-27f,45f,11f,59f,32f,22f);quadTo(37f,8f,18f,7f);quadTo(0f,14f,-22f,8f);close()},botanicalMix(color,0xffb78855.toInt(),.14f),45f,4)
        b.stroke(Path().apply{moveTo(-14f,21f);quadTo(8f,40f,29f,21f)},edge,.7f)
    }

    private fun foxglove() {
        b.line(-8f,-91f,0f,-59f,green,2f)
        for(i in -2..2)b.at(0f,-54f,180f+i*21f,.65f){sepal(37f,9f)}
        val tube=Path().apply{moveTo(-10f,-62f);cubicTo(-39f,-49f,-51f,12f,-43f,47f)
            quadTo(-32f,74f,-2f,68f);cubicTo(34f,68f,52f,42f,39f,17f);cubicTo(34f,-30f,14f,-66f,-10f,-62f);close()}
        petal(tube,color,-63f,0);tubeVeins(tube,39f,64f)
        val mouth=Path().apply{moveTo(-35f,35f);cubicTo(-25f,-8f,22f,-14f,32f,32f)
            quadTo(40f,58f,3f,63f);quadTo(-31f,71f,-35f,35f);close()}
        b.wash(mouth,0f,0f,0f,63f,intArrayOf(0xff673650.toInt(),0xffdca3bf.toInt(),0xfff4dbe1.toInt()))
        b.stroke(mouth,edge,.7f)
        c.save();c.clipPath(mouth)
        repeat(31){i->val a=i*2.399963;val r=sqrt(i/31f);val x=cos(a).toFloat()*27*r;val y=34f+sin(a).toFloat()*29*r
            val size=1.3f+(i%4)*.3f
            b.oval(x,y,size+1.1f,size+1.2f,0xfff7e5e7.toInt());b.oval(x+.3f,y,size,size+.15f,0xff813857.toInt())}
        c.restore()
        b.stroke(Path().apply{moveTo(-32f,47f);quadTo(-10f,76f,26f,53f)},0xffedc6d9.toInt(),3f)
        b.stroke(Path().apply{moveTo(-3f,3f);quadTo(7f,17f,6f,26f)},0xffe4d2b1.toInt(),1.2f)
    }

    private fun bell(urn:Boolean) {
        b.line(0f,-99f,0f,-65f,green,2f)
        repeat(if(urn)l.petals else 5){i->b.at(0f,-58f,180f+(i-2)*23f,.6f){sepal(42f,5f)}}
        val q=if(urn)Path().apply{moveTo(-9f,-66f);cubicTo(-56f,-39f,-49f,35f,-15f,48f)
            lineTo(-10f,60f);quadTo(0f,56f,10f,60f);lineTo(15f,48f);cubicTo(49f,35f,56f,-39f,9f,-66f);close()}
        else Path().apply{moveTo(-9f,-64f);cubicTo(-31f,-48f,-23f,0f,-57f,46f)
            quadTo(-34f,33f,-26f,54f);quadTo(-7f,37f,0f,57f);quadTo(15f,37f,28f,54f);quadTo(39f,32f,57f,46f)
            cubicTo(23f,0f,31f,-48f,9f,-64f);close()}
        petal(q,color,-66f,0);tubeVeins(q,if(urn)23f else 45f,if(urn)49f else 44f)
        b.oval(0f,if(urn)54f else 45f,if(urn)10f else 31f,if(urn)4f else 7f,botanicalMix(color,edge,.75f))
        if(urn)repeat(l.petals){i->val offset=i-(l.petals-1)/2f
            b.at(offset*6,55f,offset*24f,.20f){petal(Path().apply{moveTo(-9f,0f);quadTo(-19f,28f,0f,23f);quadTo(19f,28f,9f,0f);close()})}}
        else {
            b.line(0f,37f,0f,62f,0xffd4d7a3.toInt(),1.5f)
            for(i in -1..1)b.stroke(Path().apply{moveTo(0f,61f);quadTo(i*8f,68f,i*10f,61f)},0xffc0c586.toInt(),1.3f)
        }
    }

    private fun tubeVeins(outline:Path,width:Float,bottom:Float) {
        if(!detailed)return
        c.save();c.clipPath(outline)
        for(i in -3..3){val x=i*width/3
            b.stroke(Path().apply{moveTo(i*2f,-63f);cubicTo(x*.6f,-38f,x*.72f,12f,x,bottom)},botanicalMix(color,cream,.26f),.65f)
            b.stroke(Path().apply{moveTo(i*2f+1,-63f);cubicTo(x*.6f+2,-38f,x*.72f+2,12f,x+2,bottom)},botanicalMix(color,edge,.27f),.4f)
        };c.restore()
    }

    private fun heather() {
        repeat(4){i->b.at(0f,0f,45f+i*90f){petal(Path().apply{moveTo(0f,12f);quadTo(-28f,-24f,0f,-65f);quadTo(28f,-24f,0f,12f);close()},color,-65f,5)}}
        repeat(4){i->b.at(0f,0f,i*90f,.52f){petal(Path().apply{moveTo(0f,9f);quadTo(-25f,-23f,0f,-53f);quadTo(25f,-23f,0f,9f);close()},0xffe9b1cd.toInt(),-53f,3)}}
        stamens(15f,8,0xff865051.toInt());b.line(0f,0f,4f,-25f,0xffe9d4c3.toInt(),1.3f)
    }

    private fun cowslip() {
        // An umbel plus one enlarged corolla showing the five orange throat marks.
        repeat(5){i->val x=(i-2)*18f;val y=-45f+abs(i-2)*5
            b.stroke(Path().apply{moveTo(-25f,65f);quadTo(x-22f,-64f,x-17f,y)},green,1.4f)
            b.at(x-17f,y,if(i<2)-15f else 12f,.38f){
                for(j in -2..2)b.at(0f,-20f,180f+j*18f){sepal(37f,10f)}
                petal(Path().apply{moveTo(-8f,-25f);lineTo(-14f,25f);quadTo(-34f,49f,-14f,51f);quadTo(0f,65f,14f,51f);quadTo(34f,49f,14f,25f);lineTo(8f,-25f);close()},color,-25f,5)
            }
        }
        b.at(37f,51f,9f,.68f){
            repeat(5){i->b.at(0f,0f,i*72f){petal(Path().apply{moveTo(-4f,3f);cubicTo(-25f,-13f,-26f,-40f,-8f,-44f)
                quadTo(0f,-39f,8f,-44f);cubicTo(26f,-40f,25f,-13f,4f,3f);close()},color,-44f,4)
                b.oval(0f,-12f,4f,7f,0xffce7d28.toInt())}}
            b.oval(0f,0f,6f,6f,0xff939345.toInt())
        }
    }

    private fun lipped() {
        for(i in -2..2)b.at(0f,29f,180f+i*27f,.8f){sepal(31f,6f)}
        petal(Path().apply{moveTo(-12f,29f);cubicTo(-30f,1f,-58f,-48f,-12f,-70f)
            cubicTo(26f,-83f,48f,-49f,19f,-23f);quadTo(11f,-10f,14f,25f);close()},color,-72f,9)
        petal(Path().apply{moveTo(-13f,3f);cubicTo(-57f,9f,-52f,38f,-22f,36f)
            cubicTo(-31f,70f,4f,79f,16f,51f);cubicTo(48f,56f,66f,19f,14f,4f);close()},color,64f,7)
        b.stroke(Path().apply{moveTo(-2f,20f);quadTo(-8f,-8f,6f,-44f)},edge,2f)
        repeat(d.stamens){i->b.stroke(Path().apply{moveTo(3f,-15f);quadTo(20f,-45f,13f+i*3,-46f+i*3)},0xffd9c6a2.toInt(),.9f);b.oval(13f+i*3,-46f+i*3,2.2f,1.3f,gold)}
        b.stroke(Path().apply{moveTo(6f,-10f);quadTo(37f,-55f,39f,-33f)},0xffbac389.toInt(),1f)
        if(detailed)for(i in -2..2)b.stroke(Path().apply{moveTo(3f,21f);quadTo(i*7f,30f,i*9f,46f)},botanicalMix(color,edge,.7f),.9f)
    }

    private fun thistle() {
        val base=Path().apply{addOval(-29f,0f,29f,59f,Path.Direction.CW)}
        b.wash(base,-30f,0f,30f,0f,intArrayOf(0xffa4a777.toInt(),green,ink))
        for(row in 0..4)for(j in -2..2)b.at(j*10f+(row%2)*4,12f+row*9f){
            val q=Path().apply{moveTo(-7f,3f);lineTo(0f,-12f);lineTo(7f,3f);quadTo(0f,11f,-7f,3f);close()}
            b.fill(q,0xff9d986a.toInt());b.stroke(q,ink,.6f)
            if(detailed)for(i in -3..3)b.line(i*1.5f,-7f+abs(i),i*3f,-12f+abs(i)*2,0xff67564b.toInt(),.7f)
        }
        repeat(28){i->val a=i*2.399963;val x=cos(a).toFloat()*sqrt(i/28f)*43;val y=-25f+sin(a).toFloat()*18
            b.stroke(Path().apply{moveTo(x*.48f,15f);quadTo(x*.8f,-15f,x,y-16)},color,2.2f)
            for(j in -2..2)b.stroke(Path().apply{moveTo(x,y-15);quadTo(x+j*3,y-26,x+j*5,y-33-abs(j)*2)},botanicalMix(color,cream,(j+2)*.12f),1.3f)
        }
    }

    private fun flatCluster() {
        if(d.cluster==FlowerCluster.SIMPLE_UMBEL){
            repeat(11){i->val a=PI*(.04+.92*i/10);val x=cos(a).toFloat()*77;val y=20f-sin(a).toFloat()*64
                b.line(0f,85f,x,y,green,1.1f);b.at(x,y,i*21f,.24f){radial()}}
            return
        }
        if(d.cluster==FlowerCluster.FEW_FLOWERS) {
            repeat(4){i->val x=(i-1.5f)*43f;val y=-27f+(i%2)*28
                b.stroke(Path().apply{moveTo(0f,80f);quadTo(x*.7f,13f,x,y)},green,1.5f)
                b.at(x,y,i*19f,.46f){radial()}}
            return
        }
        if(d.cluster==FlowerCluster.LINDEN) {
            b.at(10f,76f,35f,.72f){sepal(202f,28f)}
            repeat(5){i->val x=(i-2)*35f;val y=-3f+(i%2)*28
                b.line(5f,46f,x,y,green,1.2f);b.at(x,y,0f,.32f){radial()}}
            return
        }
        val umbel=l.flower==FlowerShape.UMBEL
        if(!umbel)b.line(0f,82f,0f,-43f,green,1.8f)
        repeat(10){i->val a=PI*(.10+.80*i/9);val x=cos(a).toFloat()*90;val y=-sin(a).toFloat()*49
            val insertion=if(umbel)81f else -24f+abs(x)*1.12f
            b.stroke(Path().apply{moveTo(0f,insertion);quadTo(x*.55f,(insertion+y)/2,x,y)},green,1.15f)
            repeat(if(l.flower==FlowerShape.UMBEL)7 else 5){j->val aa=PI*(.06+.88*j/(if(l.flower==FlowerShape.UMBEL)6 else 4))
                val xx=x+cos(aa).toFloat()*16;val yy=y-sin(aa).toFloat()*18
                b.line(x,y,xx,yy,green,.55f)
                val scale=if(d.cluster==FlowerCluster.STERILE_RIM && (i==0||i==9)) .28f else if(l.flower==FlowerShape.UMBEL).095f else .13f
                b.at(xx,yy,j*29f,scale){if(l.flower==FlowerShape.CORYMB && d.rays<10)daisy()else radial(sterile=scale==.28f)}
            }
        }
        if(l.flower==FlowerShape.UMBEL) {
            for(i in -3..3)b.at(0f,80f,140f+i*13f,.35f){sepal(80f,4f)}
            if(d.darkCentre)b.at(0f,-65f,0f,.13f){repeat(5){i->b.at(0f,0f,i*72f){b.oval(0f,-10f,8f,12f,0xff753746.toInt())}}}
        }
    }

    private fun panicle() {
        val pendant=d.cluster==FlowerCluster.PENDANT_RACEME
        if(pendant){
            b.stroke(Path().apply{moveTo(-13f,-111f);cubicTo(13f,-67f,-18f,27f,5f,106f)},green,1.8f)
            repeat(13){i->val y=-81f+i*14;val reach=15f+sin(i*.23f)*8
                for(side in listOf(-1,1))repeat(3){j->val x=side*(reach-j*5)
                    b.line(0f,y-12,x,y,green,.65f)
                    b.at(x,y+j*4,j*31f,.13f){if(l.petals==0)nakedFlower()else radial()}}
            }
            return
        }
        b.at(0f,0f){
            b.line(0f,98f,0f,-99f,green,2f)
            repeat(8){i->val y=-85f+i*23;val reach=12f+i*6
                for(side in listOf(-1,1)){
                    b.line(0f,y+15,side*reach,y,green,1f)
                    repeat(5){j->b.at(side*reach*(.28f+j*.18f),y+13f-j*3f,j*16f,.16f){if(l.petals==0)nakedFlower()else radial()}}
                }
            }
        }
    }

    private fun nakedFlower() {
        if(d.perianth)repeat(d.perianthLobes){i->b.at(0f,0f,i*360f/d.perianthLobes,.6f){sepal(31f,10f)}}
        stamens(39f,d.stamens,0xff8b6650.toInt())
        b.oval(0f,0f,7f,11f,0xff9caa68.toInt());b.line(0f,-8f,-6f,-23f,0xffc4c886.toInt(),1.5f);b.line(0f,-8f,6f,-23f,0xffc4c886.toInt(),1.5f)
    }

    private fun spike() {
        val h=d.spikeLength;val rows=(h/4).toInt()
        b.line(0f,111f,0f,-h/2,green,2f)
        repeat(rows){i->val y=-h/2+i*4;val w=if(h>100)7f else 14f*sin(PI*(i+2)/(rows+3)).toFloat()
            for(j in -1..1)b.at(j*w*.48f,y){
                b.oval(0f,0f,w*.44f,3.4f,botanicalMix(0xff807247.toInt(),cream,j*.1f+.1f))
                b.line(-2f,1f,0f,-2f,ink,.5f)
                if(i in rows/3..rows/3+3){val x=if(j<0)-27f else 27f
                    b.stroke(Path().apply{moveTo(0f,0f);quadTo(x*.5f,-4f,x,-11f)},0xffc4c1a0.toInt(),.7f)
                    b.oval(x,-11f,3f,1.5f,cream)}
            }
        }
    }

    private fun catkins() {
        if(d.cluster==FlowerCluster.WILLOW){willowCatkins();return}
        b.stroke(Path().apply{moveTo(-102f,-66f);quadTo(0f,-57f,99f,-85f)},l.bark,4f)
        b.line(-92f,-66f,72f,-77f,0xffb09d77.toInt(),.6f)
        repeat(3){i->val x=-65f+i*51
            b.at(x,-59f,(i-1)*9f){
                if(d.cluster==FlowerCluster.GLOBOSE_CATKIN){
                    b.line(0f,0f,0f,54f,green,1.2f)
                    repeat(22){j->val a=j*2.4;val r=sqrt(j/22f)*22;b.at(cos(a).toFloat()*r,68f+sin(a).toFloat()*r,0f,.16f){stamens(22f,8)}}
                }else b.at(0f,0f,if(d.cluster==FlowerCluster.UPRIGHT_CATKIN)165f else 0f,if(d.cluster==FlowerCluster.UPRIGHT_CATKIN).60f else 1f){
                    b.stroke(Path().apply{moveTo(0f,0f);quadTo(-8f,70f,4f,143f)},green,1f)
                    repeat(28){j->val y=12f+j*4.2f;val w=8f*sin(PI*(j+2)/31).toFloat()
                        for(k in -1..1){val xx=k*w*.65f-2*sin(y/60)
                            val q=Path().apply{moveTo(xx-w*.6f,y);quadTo(xx,y+8,xx+w*.6f,y);quadTo(xx,y-2,xx-w*.6f,y);close()}
                            b.fill(q,botanicalMix(color,0xffaa8b4e.toInt(),(j%3)*.19f));b.stroke(q,0xff817249.toInt(),.4f)
                            if(detailed)for(t in -1..1){b.line(xx,y+2,xx+t*3,y+5,0xffd1bb6f.toInt(),.5f);b.oval(xx+t*3,y+5,1f,1.2f,gold)}
                        }
                    }
                }
            }
        }
        if(d.cluster==FlowerCluster.HAZEL)b.at(75f,-64f,20f,1.25f){
            b.oval(0f,0f,7f,11f,0xff8d7651.toInt())
            for(i in -2..2){b.stroke(Path().apply{moveTo(i*2f,3f);quadTo(i*3f,-7f,i*2f,-9f)},0xffbc9b66.toInt(),1f)
                b.stroke(Path().apply{moveTo(0f,-8f);quadTo(i*5f,-25f,i*7f,-21f)},0xffb54953.toInt(),1.2f)}
        }
    }

    private fun willowCatkins() {
        b.line(-91f,91f,78f,-67f,l.bark,4f)
        val h=d.spikeLength
        repeat(3){i->val x=-54f+i*54;val y=55f-i*48
            b.at(x,y,(i-1)*15f){
                b.line(0f,9f,0f,-h,green,1.2f)
                repeat(if(detailed)100 else 36){j->val n=if(detailed)100 else 36;val t=(j+.5f)/n
                    val yy=-h*t;val w=sin(PI*t).toFloat()*(if(h>80)10 else 23)
                    val xx=sin(j*2.4).toFloat()*w
                    b.oval(xx,yy,2.7f,3.2f,0xffb3ad91.toInt())
                    b.line(xx,yy,xx+3,yy-6,cream,.65f)
                    b.line(xx,yy,xx-2,yy-5,cream,.65f)
                    b.oval(xx+3,yy-6,1.8f,1.3f,gold);b.oval(xx-2,yy-5,1.8f,1.3f,gold)
                }
            }
        }
    }

    private fun pollenCones() {
        if(d.terminalPollen){PlantConiferPainter(c,l,detailed).pollen();return}
        b.line(-83f,87f,69f,-83f,l.bark,4f)
        repeat(10){i->val x=-68f+i*13;val y=70f-i*14
            for(side in listOf(-1,1))b.line(x,y,x+side*28,y-17,green,1.5f)
            if(i%2==0)b.at(x+9,y+4,48f,.56f){
                b.oval(0f,0f,12f,27f,0xffbf9b5d.toInt())
                repeat(10){j->val yy=-22f+j*5;for(k in -1..1){b.oval(k*6f,yy,3.7f,2.5f,0xffdec579.toInt());b.line(k*6f-2,yy+1,k*6f+2,yy+1,0xff907844.toInt(),.5f)}}
            }
        }
    }

    private fun grassHead() {
        if(d.cluster==FlowerCluster.BRISTLY_SPIKE){bristlySpike();return}
        if(d.cluster==FlowerCluster.PAIRED_RACEMES){pairedRacemes();return}
        b.stroke(Path().apply{moveTo(0f,117f);quadTo(-10f,-18f,0f,-115f)},green,1.8f)
        if(d.cluster==FlowerCluster.RYEGRASS)repeat(13){i->val y=-105f+i*13;val side=if(i%2==0)-1 else 1
            b.at(side*7f,y,side*19f,.65f){spikelet(false)}
        } else repeat(10){i->val y=-94f+i*16;val side=if(i%2==0)-1 else 1;val reach=20f+(i%4)*14
            val x=side*reach
            b.stroke(Path().apply{moveTo(-3f,y+23);quadTo(x*.6f,y-16,x,y)},green,.9f)
            if(d.cluster==FlowerCluster.QUAKING_GRASS){
                b.stroke(Path().apply{moveTo(x,y);quadTo(x+side*13,y+2,x+side*11,y+20)},green,.6f)
                b.at(x+side*11,y+30,side*12f,.82f){spikelet(true)}
            } else repeat(5){j->b.at(x+(j%2)*7,y+(j/2)*7,-side*25f,.5f){spikelet(false)}}
        }
    }

    private fun globoseHead() {
        repeat(3){i->val x=(i-1)*61f;val y=if(i==1)-39f else 14f
            b.line(0f,105f,x,y,green,1.3f)
            b.at(x,y){
                b.oval(0f,0f,27f,26f,botanicalMix(color,green,.28f))
                repeat(if(detailed)100 else 36){j->val a=j*2.399963;val r=sqrt((j+.5f)/100)*38
                    val xx=cos(a).toFloat()*r;val yy=sin(a).toFloat()*r
                    b.line(xx*.5f,yy*.5f,xx,yy,0xffd2c79d.toInt(),.8f)
                    b.oval(xx,yy,1.5f,1.5f,cream)
                }
            }
        }
    }

    private fun brushSpike() {
        b.stroke(Path().apply{moveTo(0f,117f);quadTo(-13f,0f,0f,-106f)},green,2f)
        repeat(24){i->val y=-93f+i*6.7f;val x=-5f+sin(i*.8f)*3
            val r=if(d.stamens>10)21f else 13f
            b.at(x,y){
                repeat(if(detailed)12 else 6){j->val a=j*2.399963
                    val xx=cos(a).toFloat()*r;val yy=sin(a).toFloat()*r*.45f
                    b.line(0f,0f,xx,yy,0xffccc3a0.toInt(),.65f);b.oval(xx,yy,1.5f,1.3f,cream)
                }
                if(d.stamens<=10)b.at(0f,0f,i*37f,.07f){radial()}
            }
        }
        // Enlarged individual flower distinguishes small petals from protruding stamens.
        if(d.stamens<=10)b.at(76f,66f,0f,.36f){radial()}
    }

    private fun bristlySpike() {
        b.line(0f,121f,0f,-111f,green,1.8f)
        repeat(23){i->val y=-99f+i*7.7f;val reach=22f*sin(PI*(i+1)/25).toFloat()
            for(side in listOf(-1,1)) {
                b.oval(side*5f,y,5f,7f,0xffad899e.toInt())
                repeat(if(detailed)9 else 4){j->val x=side*(reach+8+j*.9f);val yy=y-13f-j*2.3f
                    b.stroke(Path().apply{moveTo(side*4f,y+6);quadTo(x*.8f,y-2,x,yy)},0xffb9959e.toInt(),.6f)
                    if(detailed && j%3==0)repeat(4){k->val t=(k+2)/6f;val bx=side*4+(x-side*4)*t;val by=y+6+(yy-y-6)*t
                        b.line(bx,by,bx+side*3,by-3,0xffcdb7a8.toInt(),.45f)}
                }
            }
        }
    }

    private fun pairedRacemes() {
        b.line(0f,121f,0f,-107f,green,1.7f)
        repeat(5){i->val side=if(i%2==0)-1 else 1;val x=side*(23f+i*7);val y=-70f+i*28
            b.line(0f,y+27,x,y+16,green,.9f)
            b.at(x,y,side*14f,.68f){
                val spathe=Path().apply{moveTo(-5f,32f);quadTo(-16f,-4f,-5f,-24f);quadTo(6f,7f,5f,32f);close()}
                b.fill(spathe,0xffb39171.toInt());b.stroke(spathe,ink,.6f)
                for(s in listOf(-1,1))b.at(s*3f,7f,s*17f){
                    b.line(0f,7f,0f,-62f,green,.8f)
                    repeat(6){j->val yy=-j*10f
                        b.oval(0f,yy,3.5f,7f,0xffb7b394.toInt())
                        for(h in -2..2)b.line(h.toFloat(),yy+3,h*3f,yy-12,0xffd8d4bf.toInt(),.5f)
                        b.stroke(Path().apply{moveTo(0f,yy-4);lineTo(s*15f,yy-24);lineTo(s*9f,yy-51)},0xff927a54.toInt(),.65f)
                    }
                }
            }
        }
    }

    private fun spikelet(round:Boolean) {
        if(round){
            val q=Path().apply{moveTo(0f,-17f);cubicTo(-25f,-23f,-29f,10f,0f,25f);cubicTo(29f,10f,25f,-23f,0f,-17f);close()}
            b.wash(q,-20f,-8f,20f,18f,intArrayOf(0xffa5ab79.toInt(),0xffbca1a0.toInt(),0xff88776c.toInt()));b.stroke(q,ink,.6f)
            for(i in 0..5){val y=-13f+i*6;val w=18f*(1-i*.12f)
                b.stroke(Path().apply{moveTo(-w,y);quadTo(0f,y+12,w,y)},0xffe0ce9d.toInt(),1f)}
        }else {
            b.line(0f,22f,0f,-27f,green,.8f)
            for(i in 0..6)b.at((if(i%2==0)-1 else 1)*3f,15f-i*6,if(i%2==0)-24f else 24f){
                val q=Path().apply{moveTo(0f,7f);quadTo(-9f,-1f,0f,-19f);quadTo(10f,-1f,0f,7f);close()}
                b.wash(q,-6f,0f,6f,0f,intArrayOf(0xffc1bba0.toInt(),green));b.stroke(q,ink,.45f)
                b.line(0f,3f,0f,-15f,0xffd7caa3.toInt(),.6f)
            }
        }
    }
}
