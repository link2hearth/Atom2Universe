package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

/** Trait-driven original Canvas plates. Main organs have measured dimensions; diagnostic
 * insets are independently enlarged. Bracts, accessory tissues and seeds stay distinct.
 * Morphology references are attached to the species records, not encoded as species-ID tests.
 */
internal class StructuralPlantPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val ink=0xff43533a.toInt()
    private val cream=0xfff7efd7.toInt()
    private val brown=0xff9b7043.toInt()
    private val red=0xffc74b39.toInt()
    private val palms=setOf(StudyForm.COCONUT_PALM,StudyForm.DATE_PALM,StudyForm.ARECA_PALM)
    private val monocots=setOf(StudyForm.ALOE_ROSETTE,StudyForm.PINEAPPLE_SYNCARP,StudyForm.BANANA_PSEUDOSTEM,
        StudyForm.GINGER_RHIZOME,StudyForm.CLIVIA_FAN,StudyForm.LILY_RACEME,StudyForm.BOWIEA_BULB)+palms
    private fun fallback(view:PlantView)=PlantPainter(c,species.copy(look=l.copy(study=null)),!detailed).draw(view)
    private fun outline(q:Path,color:Int=green) {
        val box=RectF();q.computeBounds(box,true)
        b.wash(q,box.left,box.top,box.right,box.bottom,intArrayOf(botanicalMix(color,cream,.38f),color,botanicalMix(color,ink,.18f)))
        b.stroke(q,botanicalMix(color,ink,.4f),.7f)
    }
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=green,width:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)

    fun draw(view:PlantView) {
        when(view) {
            PlantView.HABIT -> if(form in monocots)habit() else fallback(view)
            PlantView.LEAF -> leaves()
            PlantView.FLOWER -> flowers()
            PlantView.FRUIT -> fruits()
        }
    }

    /** Origin at blade base. Length is the projected midrib; margin and veins are traits. */
    private fun blade(length:Float,width:Float,spiny:Boolean=false,round:Boolean=false,tear:Boolean=false) {
        val q=Path().apply {
            moveTo(0f,0f)
            cubicTo(-width,-length*.22f,-width,-length*.8f,if(round)-width*.35f else 0f,-length)
            if(round)quadTo(0f,-length-2,width*.35f,-length)
            cubicTo(width,-length*.8f,width,-length*.22f,0f,0f);close()
        }
        outline(q)
        curve(0f,0f,0f,-length*.55f,0f,-length,botanicalMix(green,cream,.6f),1f)
        if(detailed) {
            c.save();c.clipPath(q)
            if(tear)for(i in 1..14) {
                val y=-length*i/15
                b.line(0f,y,width,y+length*.04f,botanicalMix(green,cream,.5f),.7f)
                b.line(0f,y,-width,y+length*.04f,botanicalMix(green,cream,.5f),.7f)
            } else for(i in listOf(-2,-1,1,2))
                curve(0f,0f,i*width*.6f,-length*.5f,0f,-length,botanicalMix(green,cream,.43f),.6f)
            if(tear)for(i in 1..5) {
                val y=-length*i/6
                b.stroke(Path().apply{moveTo(width,y);lineTo(width*.23f,y+6)},cream,2f)
            }
            c.restore()
        }
        if(spiny)for(side in listOf(-1,1))for(i in 1..11) {
            val t=i/12f;val x=side*width*sin(PI*t).toFloat()*.75f
            outline(Path().apply{moveTo(x,-length*t);lineTo(x+side*4,-length*t-3);lineTo(x,-length*t-6);close()},cream)
        }
    }
    private fun frond(length:Float=198f) {
        val bend=if(form==StudyForm.DATE_PALM)0f else length*.12f
        curve(0f,0f,bend,-length*.45f,bend,-length,green,2.2f)
        val pairs=if(form==StudyForm.ARECA_PALM)12 else 18
        for(i in 1..pairs) {
            val t=i/(pairs+1f);val y=-length*t;val x=bend*(2*t-t*t)
            val extent=length*.23f*sin(PI*t).toFloat()
            val bladeWidth=if(form==StudyForm.ARECA_PALM)9f else 3f
            for(side in listOf(-1,1)) {
                if(form==StudyForm.DATE_PALM && i<4)b.line(x,y,x+side*extent*.6f,y-length*.10f,ink,1.4f)
                else outline(Path().apply{moveTo(x,y);quadTo(x+side*extent*.6f,y-length*.15f,x+side*extent,y-length*.1f)
                    quadTo(x+side*extent*.55f,y-length*.06f+bladeWidth,x,y+bladeWidth);close()})
            }
        }
    }
    private fun bulb(x:Float,y:Float,size:Float) {
        b.oval(x,y,size,size*.8f,botanicalMix(green,cream,.38f))
        for(i in -2..2)curve(x+i*size*.26f,y+size*.7f,x+i*size*.54f,y,x,y-size*.77f,green,.9f)
        for(i in -2..2)curve(x+i*3,y+size*.77f,x+i*6,y+size*.93f,x+i*7,y+size*1.03f,brown,.8f)
    }
    private fun branching(x:Float,y:Float,h:Float) {
        curve(x,y,x-12,y-h*.5f,x+10,y-h,green,1.7f)
        for(i in 1..6) {
            val yy=y-h*i/7
            for(side in listOf(-1,1)) {
                val xx=x+side*h*.28f*sin(i*1.2f)
                curve(x,yy,x+side*h*.2f,yy-h*.14f,xx,yy-h*.26f,green,1f)
                curve(xx,yy-h*.26f,xx+side*12,yy-h*.38f,xx+side*6,yy-h*.44f,green,.65f)
            }
        }
    }

    private fun habit() {
        b.oval(150f,287f,91f,4f,0x166a6145)
        when {
            form in palms -> {
                val shaft=form==StudyForm.ARECA_PALM
                outline(Path().apply{moveTo(141f,282f);lineTo(147f,109f);lineTo(157f,109f);lineTo(161f,282f);close()},brown)
                for(i in 0..13)b.line(143f+i*.1f,275f-i*11,159f-i*.1f,273f-i*11,ink,.8f)
                if(shaft)outline(Path().apply{moveTo(147f,154f);lineTo(146f,107f);lineTo(157f,107f);lineTo(158f,154f);close()})
                for(a in listOf(-106f,-72f,-37f,0f,38f,76f,109f))
                    b.at(151f,119f,a,if(form==StudyForm.DATE_PALM).48f else .5f){frond()}
                if(form==StudyForm.DATE_PALM)for(a in listOf(-45f,22f,57f))b.at(117f,277f,a,.2f){frond()}
                if(form!=StudyForm.DATE_PALM)for(i in -1..1)b.oval(171f+i*9,if(shaft)173f else 139f,if(shaft)4f else 8f,if(shaft)6f else 11f,if(shaft)red else green)
            }
            form==StudyForm.BANANA_PSEUDOSTEM -> {
                outline(Path().apply{moveTo(138f,280f);quadTo(145f,198f,143f,125f);lineTo(157f,125f);quadTo(161f,205f,163f,280f);close()},0xff91a16a.toInt())
                for(i in -2..2)curve(150f+i*4,280f,146f+i*3,211f,150f+i*2,131f,green,1f)
                for((i,a) in listOf(-78f,-50f,-22f,23f,54f,80f).withIndex())
                    b.at(150f,126f+i%2*14,a,.5f){blade(190f,42f,tear=true)}
                curve(154f,137f,222f,124f,214f,171f,green,2f)
                b.at(218f,184f,0f,.28f){bananaHand()}
                b.at(109f,279f,-27f,.27f){blade(180f,33f)}
            }
            form==StudyForm.BOWIEA_BULB -> {
                bulb(150f,256f,28f);branching(150f,233f,168f)
                b.at(142f,246f,-42f,.4f){blade(98f,3f)}
            }
            form==StudyForm.GINGER_RHIZOME -> {
                b.at(145f,272f,0f,.5f){rhizome()}
                for(i in -1..1) {
                    val x=146f+i*40
                    b.line(x,262f,x,70f+abs(i)*20,green,2.2f)
                    for(j in 0..5)b.at(x,240f-j*28f,if(j%2==0)-60f else 60f,.38f){blade(112f,13f)}
                }
                b.line(215f,272f,215f,209f,green,2f);b.at(215f,195f,0f,.36f){bractedSpike(false)}
            }
            form==StudyForm.LILY_RACEME -> {
                b.at(142f,276f,-23f,.93f){blade(198f,31f)}
                b.at(155f,276f,30f,.82f){blade(198f,31f)}
                raceme(180f,246f,.74f,false,true)
                b.line(117f,281f,175f,281f,brown,3f)
            }
            else -> {
                val fan=form==StudyForm.CLIVIA_FAN
                for(i in -3..3)b.at(150f,278f,i*if(fan)23f else 21f,if(fan).73f else .68f){
                    blade(165f,if(form==StudyForm.ALOE_ROSETTE)21f else if(fan)14f else 8f,
                        spiny=form in setOf(StudyForm.ALOE_ROSETTE,StudyForm.PINEAPPLE_SYNCARP),round=fan)
                }
                when(form) {
                    StudyForm.ALOE_ROSETTE -> {
                        b.line(160f,256f,165f,56f,green,2f)
                        for(i in 0..12)b.at(165f,60f+i*8,if(i%2==0)-27f else 24f,.16f){tube(6,false)}
                        b.at(83f,279f,-34f,.27f){blade(120f,19f,true)}
                    }
                    StudyForm.CLIVIA_FAN -> {
                        b.line(158f,258f,159f,108f,green,3f)
                        for(i in -3..3) {
                            b.line(159f,110f,159f+i*15,78f+abs(i)*4,green,1f)
                            b.at(159f+i*15,78f+abs(i)*4,i*17f,.2f){tube(6,true)}
                        }
                    }
                    StudyForm.PINEAPPLE_SYNCARP -> {
                        b.line(150f,244f,150f,172f,green,4f)
                        b.at(150f,135f,0f,.53f){pineapple()}
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun leaves() {
        when {
            form in palms -> b.at(140f,249f){frond()}
            form==StudyForm.BANANA_PSEUDOSTEM -> b.at(150f,249f){blade(198f,47f,tear=true)}
            form==StudyForm.ALOE_ROSETTE -> b.at(150f,249f){blade(198f,31f,true)}
            form==StudyForm.PINEAPPLE_SYNCARP -> b.at(150f,249f){blade(198f,13f,true)}
            form==StudyForm.CLIVIA_FAN -> b.at(150f,249f){blade(198f,20f,round=true)}
            form==StudyForm.LILY_RACEME -> b.at(150f,249f){blade(198f,44f)}
            form==StudyForm.GINGER_RHIZOME -> b.at(150f,249f){blade(198f,22f)}
            form==StudyForm.BOWIEA_BULB -> {
                b.at(106f,249f){blade(198f,4f)};branching(224f,231f,125f)
            }
            form==StudyForm.JUNIPER_FLESHY_CONE -> {
                b.line(149f,255f,150f,51f,brown,2f)
                for(y in listOf(94f,154f,215f)) {
                    for(a in listOf(-62f,0f,62f))b.at(150f,y,a,.43f) {
                        blade(115f,6f);b.line(0f,-4f,0f,-111f,cream,2.1f)
                    }
                }
            }
            else -> fallback(PlantView.LEAF)
        }
    }

    private fun radial(count:Int,stamens:Int,r:Float=62f,color:Int=l.color.ink) {
        for(i in 0 until count)b.at(0f,0f,i*360f/count) {
            outline(Path().apply{moveTo(0f,4f);quadTo(-r*.35f,-r*.5f,0f,-r);quadTo(r*.35f,-r*.5f,0f,4f);close()},color)
        }
        repeat(stamens){i->
            val a=i*2*PI/stamens;val x=cos(a).toFloat()*r*.32f;val y=sin(a).toFloat()*r*.32f
            b.line(0f,0f,x,y,cream,1.1f);b.oval(x,y,2f,3f,0xffbea33f.toInt())
        }
        if(l.flowerDetail.styles>0)b.oval(0f,0f,3f,3f,green)
    }
    /** Fused tube in side view; end-view inset carries the full merosity. */
    private fun tube(lobes:Int,funnel:Boolean,color:Int=l.color.ink) {
        val w=if(funnel)35f else 16f
        outline(Path().apply{moveTo(-5f,-62f);quadTo(-10f,15f,-w,49f)
            quadTo(0f,70f,w,49f);quadTo(10f,15f,5f,-62f);close()},color)
        b.oval(0f,49f,w,w*.24f,botanicalMix(color,ink,.25f))
        for(i in 0 until lobes) {
            val a=i*2*PI/lobes
            b.oval(cos(a).toFloat()*w,sin(a).toFloat()*w*.23f+49f,4f,2.5f,color)
        }
        if(detailed)for(i in -1..1)b.line(i*4f,-50f,i*11f,44f,botanicalMix(color,cream,.5f),.65f)
    }
    private fun bell(lobes:Int) {
        outline(Path().apply{moveTo(-9f,-62f);cubicTo(-54f,-47f,-48f,27f,-35f,49f)
            quadTo(0f,68f,35f,49f);cubicTo(48f,27f,54f,-47f,9f,-62f);close()},l.color.ink)
        b.oval(0f,49f,35f,8f,botanicalMix(l.color.ink,ink,.32f))
        repeat(lobes){i->val a=i*2*PI/lobes
            b.oval(cos(a).toFloat()*35f,49f+sin(a).toFloat()*8f,6f,4f,l.color.ink)}
    }
    /** Continuous corolla outline keeps fused bells distinct from free radial petals. */
    private fun fusedEnd(lobes:Int,stamens:Int) {
        val q=Path()
        repeat(121){i->val a=i*2*PI/120;val r=56f+6f*cos(lobes*a).toFloat()
            val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r
            if(i==0)q.moveTo(x,y)else q.lineTo(x,y)}
        q.close();outline(q,l.color.ink)
        repeat(stamens){i->val a=i*2*PI/stamens;val x=cos(a).toFloat()*22f;val y=sin(a).toFloat()*22f
            b.line(0f,0f,x,y,cream,1f);b.oval(x,y,2f,3f,brown)}
        b.oval(0f,0f,3f,3f,green)
    }
    private fun bananaFlower() {
        tube(5,false,cream)
        // Five tepals form the compound tubular piece; a sixth tepal stays free.
        outline(Path().apply{moveTo(5f,-57f);quadTo(40f,-12f,32f,37f)
            quadTo(10f,8f,5f,-57f);close()},cream)
    }
    private fun bractedSpike(banana:Boolean) {
        if(banana) {
            b.line(0f,-98f,0f,91f,green,3f)
            for(i in 0..4) {
                val y=-65f+i*31
                outline(Path().apply{moveTo(0f,y-28f);quadTo(-51f,y-10f,-30f,y+29f);quadTo(0f,y+49f,31f,y+26f)
                    quadTo(45f,y-10f,0f,y-28f);close()},0xff95466c.toInt())
                if(i<3)for(j in -2..2)b.at(j*10f,y+27f,0f,.15f){bananaFlower()}
            }
        } else {
            b.line(0f,75f,0f,-87f,green,3f)
            for(i in 0..6) {
                val y=48f-i*20;val w=37f*(1-i*.08f)
                outline(Path().apply{moveTo(-w,y);quadTo(-w-8,y-22,0f,y-30);quadTo(w+8,y-22,w,y);quadTo(0f,y+13,-w,y);close()},0xff9caa66.toInt())
            }
        }
    }
    private fun raceme(x:Float,y:Float,scale:Float,berries:Boolean,oneSide:Boolean=false) = b.at(x,y,0f,scale) {
        curve(0f,30f,5f,-57f,-12f,-127f,green,2f)
        for(i in 0..5) {
            val yy=-105f+i*23;val side=if(oneSide || i%2==0)1 else -1
            curve(-3f,yy,side*24f,yy-8,side*29f,yy+9,green,1.1f)
            if(berries)b.oval(side*29f,yy+17,9f,10f,red)
            else b.at(side*29f,yy+14,0f,.24f){bell(if(form==StudyForm.LINGONBERRY_RACEME)4 else 6)}
        }
    }
    private fun panicle() {
        b.line(0f,88f,0f,-95f,green,2.3f)
        for(i in 0..7)for(side in listOf(-1,1)) {
            val y=65f-i*20
            curve(0f,y,side*34f,y-25,side*58f,y-45,green,1.1f)
            for(j in 1..5)b.oval(side*j*11f,y-j*8f,3f,3f,cream)
            if(form!=StudyForm.DATE_PALM)b.oval(side*12f,y-7,5f,5.5f,cream)
        }
    }
    private fun flowers() {
        when {
            form in palms -> b.at(137f,153f){panicle()}
            form==StudyForm.ALOE_ROSETTE || form==StudyForm.CLIVIA_FAN -> {
                b.at(125f,146f){tube(6,form==StudyForm.CLIVIA_FAN)}
                b.at(239f,114f,0f,.65f){if(form==StudyForm.ALOE_ROSETTE)fusedEnd(6,6)else radial(6,6)}
                if(form==StudyForm.CLIVIA_FAN) {
                    for(i in -2..2){b.line(236f,251f,236f+i*12,218f+abs(i)*6,green,1f)
                        b.at(236f+i*12,218f+abs(i)*6,i*20f,.14f){tube(6,true)}}
                } else {
                    b.line(222f,264f,222f,217f,green,1f)
                    for(i in 0..3)b.at(222f,218f+i*10,if(i%2==0)-25f else 20f,.12f){tube(6,false)}
                }
            }
            form==StudyForm.LILY_RACEME || form==StudyForm.LINGONBERRY_RACEME -> {
                b.at(117f,145f){bell(l.petals)}
                b.at(236f,115f,0f,.6f){fusedEnd(l.petals,l.flowerDetail.stamens)}
                raceme(236f,265f,.4f,false,form==StudyForm.LILY_RACEME)
            }
            form==StudyForm.BOWIEA_BULB -> b.at(150f,150f){radial(6,6)}
            form==StudyForm.PINEAPPLE_SYNCARP -> {
                b.at(101f,167f,0f,.75f){bractedSpike(false)}
                for(i in 0..4)b.at(101f+(if(i%2==0)-1 else 1)*20,186f-i*25,0f,.15f){radial(3,6,62f,l.color.ink)}
                b.at(230f,135f,0f,.85f){radial(3,6)}
            }
            form==StudyForm.BANANA_PSEUDOSTEM -> {
                b.at(112f,147f){bractedSpike(true)}
                b.at(239f,145f,0f,.75f){bananaFlower()}
            }
            form==StudyForm.GINGER_RHIZOME -> {
                b.at(98f,157f){bractedSpike(false)}
                b.at(229f,140f) {
                    for(a in listOf(-65f,0f,65f))b.at(0f,0f,a){outline(Path().apply{moveTo(0f,0f);quadTo(-9f,-35f,0f,-60f);quadTo(9f,-35f,0f,0f);close()},cream)}
                    outline(Path().apply{moveTo(0f,0f);cubicTo(-52f,11f,-47f,74f,0f,56f);cubicTo(45f,74f,49f,11f,0f,0f);close()},0xff94516d.toInt())
                    b.line(0f,10f,0f,47f,0xffe4c75d.toInt(),3f)
                    b.line(0f,0f,0f,-15f,cream,2f);b.oval(0f,-17f,3f,7f,brown)
                }
            }
            form==StudyForm.FIG_SYCONIUM -> b.at(147f,151f){figSection(false)}
            form==StudyForm.CURRANT_RACEME -> {
                b.line(87f,251f,93f,63f,green,2f)
                for(i in 0..5){val y=84f+i*25;b.line(90f,y,115f,y+10,green,1f);b.at(120f,y+12,0f,.13f){radial(5,5)}}
                b.at(227f,143f){radial(5,5)}
            }
            form==StudyForm.WALNUT_HUSK -> {
                fallback(PlantView.FLOWER)
                b.at(246f,226f) {
                    b.oval(0f,0f,10f,15f,green)
                    for(side in listOf(-1,1))curve(0f,-10f,side*17f,-31f,side*12f,-37f,red,4f)
                }
            }
            else -> fallback(PlantView.FLOWER)
        }
    }

    private fun capsule(two:Boolean=false) {
        val count=if(two)2 else 3
        b.line(0f,73f,0f,52f,green,2f)
        for(i in 0 until count)b.at(0f,52f,(i-(count-1)/2f)*if(two)44f else 35f) {
            outline(Path().apply{moveTo(0f,0f);quadTo(-19f,-49f,0f,-120f);quadTo(19f,-49f,0f,0f);close()},
                if(form==StudyForm.GINGER_RHIZOME)red else brown)
        }
        for(i in 0..5) {
            val x=85f+(i%2)*20f;val y=-44f+i*15f
            b.oval(x,y,3.5f,5f,ink)
            if(two)for(a in -3..3)curve(x,y-2f,x+a*5f,y-17f,x+a*7f,y-29f,0xffc6c2a8.toInt(),.6f)
        }
    }
    private fun rhizome() {
        for((i,x) in listOf(-45f,-12f,22f,49f).withIndex()) {
            b.oval(x,i%2*11f,23f,15f,0xffc3a47a.toInt())
            for(j in -1..1)b.line(x+j*6,-9f+i%2*11,x+j*6+2,11f+i%2*11,brown,.7f)
            curve(x,13f,x-8f,26f,x-3f,39f,brown,.8f)
        }
        b.oval(-16f,-15f,12f,23f,0xffb8a078.toInt());b.oval(-15f,-36f,5f,4f,green)
    }
    private fun pineapple() {
        val q=Path().apply{addOval(-39f,-60f,39f,60f,Path.Direction.CW)}
        outline(q,0xffc4a347.toInt())
        c.save();c.clipPath(q)
        for(i in -4..4)for(j in -3..3) {
            val x=j*21f+(i%2)*10f;val y=i*17f
            val eye=Path().apply{moveTo(x,y-11);lineTo(x+11,y);lineTo(x,y+11);lineTo(x-11,y);close()}
            b.stroke(eye,brown,1f);b.oval(x,y,2f,2f,brown)
        }
        c.restore()
        for(i in -2..2)b.at(0f,-53f,i*24f,.4f){blade(130f,10f)}
    }
    private fun bananaHand() {
        for(i in -2..2)b.at(i*18f,0f,i*12f) {
            outline(Path().apply{moveTo(-7f,-53f);cubicTo(-28f,-15f,-22f,46f,8f,61f)
                quadTo(18f,65f,23f,54f);cubicTo(-4f,28f,-6f,-13f,3f,-53f);close()},0xffdbc653.toInt())
            b.line(-4f,-57f,-2f,-50f,green,3f)
        }
    }
    private fun figSection(mature:Boolean) {
        val q=Path().apply{moveTo(-8f,-62f);cubicTo(-9f,-36f,-57f,-30f,-49f,22f)
            cubicTo(-44f,71f,44f,71f,49f,22f);cubicTo(57f,-30f,9f,-36f,8f,-62f);close()}
        outline(q,if(mature)0xffa67680.toInt() else green)
        b.oval(0f,8f,37f,45f,if(mature)0xffe5ad95.toInt() else cream)
        // A narrow bract-lined ostiole communicates with the internal flower-bearing cavity.
        b.line(0f,-62f,0f,-31f,cream,5f)
        for(side in listOf(-1,1))for(i in 0..2)
            b.line(side*6f,-58f+i*7,side*1f,-53f+i*7,ink,.85f)
        for(i in 0..23) {
            val a=i*2*PI/24;val x=cos(a).toFloat()*32f;val y=8f+sin(a).toFloat()*39f
            if(mature)b.oval(x,y,2f,3f,brown)
            else {b.line(x,y,x*.68f,8f+(y-8f)*.68f,green,1.1f);b.oval(x*.68f,8f+(y-8f)*.68f,2f,3f,red)}
        }
    }
    private fun pear(medlar:Boolean,section:Boolean=false) {
        val q=if(medlar)Path().apply{addOval(-54f,-60f,54f,60f,Path.Direction.CW)}
        else Path().apply{moveTo(-12f,-60f);cubicTo(-17f,-12f,-52f,-5f,-48f,35f)
            cubicTo(-43f,69f,43f,69f,48f,35f);cubicTo(52f,-5f,17f,-12f,12f,-60f);close()}
        outline(q,if(section)cream else if(medlar)brown else 0xffb0bb67.toInt())
        b.line(0f,-60f,4f,-79f,brown,3f)
        if(medlar) {
            b.oval(0f,0f,20f,16f,ink)
            for(i in 0..4)b.at(0f,0f,i*72f){outline(Path().apply{moveTo(-5f,-15f);lineTo(0f,-47f);lineTo(5f,-15f);close()},brown)}
        } else if(section) {
            b.oval(0f,14f,13f,32f,0xffddc79e.toInt())
            for(side in listOf(-1,1))for(y in listOf(0f,20f))b.oval(side*6f,y,3f,5f,brown)
        } else for(i in 0..4)b.line(0f,57f,(i-2)*3f,62f,ink,.8f)
    }
    private fun nutCluster() {
        for((i,x) in listOf(-29f,0f,29f).withIndex())b.at(x,if(i==1)23f else -10f) {
            for(j in 0..6)b.at(0f,14f,(j-3)*36f) {
                val p=Path().apply{moveTo(-5f,0f);lineTo(-8f,-25f);lineTo(-13f,-40f);lineTo(-7f,-35f)
                    lineTo(-4f,-56f);lineTo(0f,-43f);lineTo(4f,-60f);lineTo(8f,-30f);lineTo(5f,0f);close()}
                outline(p);if(detailed)b.line(0f,0f,0f,-45f,cream,.5f)
            }
            b.oval(0f,0f,13f,19f,brown);b.oval(0f,14f,11f,5f,0xffc3a674.toInt())
        }
    }
    private fun fruits() {
        when(form) {
            StudyForm.PINEAPPLE_SYNCARP -> b.at(125f,160f){pineapple()}
            StudyForm.BANANA_PSEUDOSTEM -> b.at(130f,151f){bananaHand()}
            StudyForm.ALOE_ROSETTE,StudyForm.BOWIEA_BULB -> b.at(120f,151f){capsule()}
            StudyForm.GINGER_RHIZOME -> {
                b.at(100f,126f,0f,.75f){capsule()}
                b.at(197f,236f,0f,.76f){rhizome()}
            }
            StudyForm.CREEPING_WILLOW -> b.at(120f,151f){capsule(true)}
            StudyForm.CLIVIA_FAN -> {
                for(i in -2..2){b.line(142f,238f,142f+i*28,140f+abs(i)*8,green,2f)
                    b.oval(142f+i*28,140f+abs(i)*8,18f,20f,red)}
            }
            StudyForm.LILY_RACEME,StudyForm.LINGONBERRY_RACEME -> {
                raceme(130f,223f,1f,true,form==StudyForm.LILY_RACEME)
                b.oval(239f,151f,30f,30f,red);b.oval(231f,140f,8f,5f,botanicalMix(red,cream,.48f))
            }
            StudyForm.CURRANT_RACEME -> {
                raceme(130f,223f,1f,true)
                b.oval(239f,151f,30f,30f,red)
            }
            StudyForm.COCONUT_PALM -> {
                b.oval(93f,151f,47f,60f,0xffb5a46a.toInt())
                if(detailed)for(i in -3..3)curve(93f+i*8,94f,93f+i*12,151f,93f+i*6,208f,brown,.7f)
                b.oval(224f,151f,48f,59f,0xffb6a071.toInt())
                b.oval(224f,151f,34f,45f,brown);b.oval(224f,151f,29f,39f,cream);b.oval(224f,151f,20f,29f,0xffd0d8c3.toInt())
            }
            StudyForm.DATE_PALM -> {
                for(i in -1..1) {
                    b.line(116f,73f,116f+i*29,112f,green,2f)
                    b.oval(116f+i*29,164f+abs(i)*5,15f,60f,0xffaa7044.toInt())
                }
                b.oval(240f,166f,12f,35f,brown);b.line(240f,137f,240f,195f,ink,1.5f)
            }
            StudyForm.ARECA_PALM -> {
                for(i in -1..1)b.oval(111f+i*28,151f+abs(i)*12,19f,42f,0xffc97842.toInt())
                b.oval(233f,151f,31f,43f,0xffc99a65.toInt());b.oval(233f,153f,20f,30f,brown)
                if(detailed)for(i in -2..2)curve(233f+i*5,126f,232f+i*7,152f,233f+i*4,180f,cream,.8f)
            }
            StudyForm.PEAR_POME -> {b.at(102f,158f){pear(false)};b.at(233f,161f,0f,.75f){pear(false,true)}}
            StudyForm.MEDLAR_POME -> b.at(135f,151f){pear(true)}
            StudyForm.FIG_SYCONIUM -> {
                b.at(95f,151f){outline(Path().apply{moveTo(-8f,-62f);cubicTo(-10f,-25f,-48f,-19f,-42f,25f)
                    cubicTo(-37f,72f,37f,72f,42f,25f);cubicTo(48f,-19f,10f,-25f,8f,-62f);close()},0xff996c80.toInt())}
                b.at(227f,151f,0f,.9f){figSection(true)}
            }
            StudyForm.WALNUT_HUSK -> {
                b.oval(87f,151f,45f,60f,green)
                b.oval(217f,141f,43f,50f,brown)
                curve(217f,91f,207f,140f,217f,191f,ink,1.5f)
                for(i in -2..2)curve(217f+i*12,100f,199f+i*17,137f,217f+i*10,180f,ink,.7f)
                for(side in listOf(-1,1)) {
                    b.oval(217f+side*13,245f,17f,22f,0xffc7ad75.toInt())
                    for(i in -1..1)curve(217f+side*13+i*7,228f,217f+side*8+i*13,244f,217f+side*13+i*6,261f,brown,1.5f)
                }
            }
            StudyForm.JUNIPER_FLESHY_CONE -> {
                for(i in -1..1)b.oval(107f+i*31,151f+abs(i)*16,23f,25f,0xff68798f.toInt())
                b.oval(240f,153f,30f,30f,0xffc4b5a0.toInt())
                for(i in 0..2)b.at(240f,153f,i*120f){b.oval(0f,-11f,5f,9f,brown)}
            }
            StudyForm.TREE_HAZEL -> b.at(145f,151f,0f,1.1f){nutCluster()}
            else -> fallback(PlantView.FRUIT)
        }
    }
}
