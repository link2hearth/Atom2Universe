package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

/** Original Canvas plates of model herbs and cereals. Axillary ears, spikelet arrangement,
 * ligules, stipules and reproductive organs are drawn explicitly, not simulated by foliage noise.
 */
internal class StudyPlantPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val ink=0xff43533a.toInt()
    private val light=0xffc8d296.toInt()
    private val straw=0xffc6a45c.toInt()
    private val cream=0xfff5edd2.toInt()
    private val leaf=PlantLeafPainter(c,species,detailed)
    private val flower=PlantFlowerPainter(c,species,detailed)
    private fun outlined(p:Path,color:Int=green) {
        val bounds=RectF();p.computeBounds(bounds,true)
        b.wash(p,bounds.left,bounds.top,bounds.right,bounds.bottom,intArrayOf(botanicalMix(color,cream,.35f),color,botanicalMix(color,ink,.2f)))
        b.stroke(p,ink,.65f)
    }
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=green,width:Float=1f) =
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)

    fun draw(view:PlantView) {
        if(form.foodCrop){FoodCropPainter(c,species,detailed).draw(view);return}
        if(form.orchard){OrchardPainter(c,species,detailed).draw(view);return}
        if(form.nightshade){NightshadePainter(c,species,detailed).draw(view);return}
        when(view) {
            PlantView.HABIT -> habit()
            PlantView.LEAF -> leaves()
            PlantView.FLOWER -> flowers()
            PlantView.FRUIT -> fruits()
        }
    }

    private fun habit() {
        b.oval(150f,287f,94f,4f,0x166a6145)
        when(form) {
            StudyForm.ROSETTE_SILIQUES -> {
                for(i in -3..3)b.at(150f,279f,i*29f,.42f){leaf.draw(LeafShape.OVAL,110f)}
                for(i in -1..1) {
                    val x=150f+i*57f;val top=42f+abs(i)*28f
                    curve(150f,279f,153f+i*26,145f,x,top,green,1.6f)
                    for(j in 0..5) {
                        val y=top+32+j*24;val side=if(j%2==0)-1 else 1
                        val xx=x+(150-x)*(y-top)/(279-top)
                        b.at(xx,y,side*30f,.17f){silique(false)}
                        if(j>3)b.at(xx,y,side*61f,.24f){leaf.draw(LeafShape.LANCE,74f)}
                    }
                    for(j in -1..1)b.at(x+j*5,top+abs(j)*8,0f,.075f){flower.blossom(FlowerShape.STAR)}
                }
            }
            StudyForm.TENDRIL_LEGUME -> {
                curve(147f,281f,120f,148f,161f,39f,green,2.2f)
                for(i in 0..4) {
                    val y=246f-i*43f;val x=140f+(if(i>2)i*4 else 0)
                    b.at(x,y,if(i%2==0)-64f else 64f,.43f){peaLeaf()}
                    if(i in 1..3) {
                        curve(x,y,x+27f,y-36f,x+40f,y-25f,green,1f)
                        b.at(x+40f,y-25,18f,.22f){flower.blossom(FlowerShape.PEA)}
                    }
                }
                b.at(131f,191f,-19f,.35f){pod(false)}
            }
            else -> {
                val maize=form==StudyForm.MONOECIOUS_CEREAL
                val count=if(maize)1 else 5
                repeat(count){i->
                    val x=if(maize)150f else 94f+i*27
                    val top=if(maize)95f else 109f+(i%2)*17
                    b.line(150f+(i-count/2)*6,281f,x,top,green,if(maize)5f else 1.8f)
                    for(j in 0..4) {
                        val y=255f-j*31f;val xx=x+(150-x)*(y-top)/(281-top)
                        b.line(xx-3,y,xx+3,y,light,if(maize)2f else 1f)
                        b.at(xx,y,if(j%2==0)-64f else 64f,if(maize).78f else .48f){blade(135f,if(maize)13f else 4f,true)}
                    }
                    if(maize){
                        b.at(x,top,0f,.48f){tassel()}
                        b.at(154f,214f,24f,.46f){ear(false)}
                        for(j in -2..2)curve(150f,270f,150f+j*8,279f,150f+j*14,285f,l.bark,1.6f)
                    }else b.at(x,top,if(form==StudyForm.CEREAL_PANICLE)17f else (i-2)*4f,.42f){cerealHead(false)}
                }
            }
        }
    }

    private fun leaves() {
        when(form) {
            StudyForm.ROSETTE_SILIQUES -> {
                b.at(145f,263f,3f){leaf.draw(LeafShape.OVAL,198f)}
                b.at(57f,256f,0f,.36f){for(i in 0..7)b.at(0f,0f,i*45f){leaf.draw(LeafShape.OVAL,94f)}}
                b.at(238f,257f,10f,.65f){leaf.draw(LeafShape.LANCE,124f,LeafMargin.ENTIRE)}
            }
            StudyForm.TENDRIL_LEGUME -> b.at(150f,265f){peaLeaf()}
            else -> {
                val maize=form==StudyForm.MONOECIOUS_CEREAL
                b.at(131f,266f){blade(198f,if(maize)22f else 6f,false)}
                // Enlarged sheath/blade junction: membranous ligule and lateral auricles.
                b.at(238f,248f){
                    val sheath=Path().apply{moveTo(-12f,22f);lineTo(-13f,-42f);lineTo(12f,-42f);lineTo(10f,22f);close()}
                    outlined(sheath);b.line(0f,20f,0f,-52f,light,.8f)
                    b.at(0f,-40f,-32f){blade(76f,10f,false)}
                    val h=if(form==StudyForm.CEREAL_PANICLE)35f else 10f
                    outlined(Path().apply{moveTo(-10f,-41f);quadTo(-4f,-42f-h,1f,-41f-h);quadTo(7f,-45f,10f,-41f);close()},cream)
                    if(!maize)for(s in listOf(-1,1)) {
                        curve(s*9f,-42f,s*24f,-42f,s*8f,-31f,light,2.6f)
                        for(k in 0..4)b.line(s*(12f+k),-37f-k,s*(18f+k),-36f-k,ink,.45f)
                    }
                }
            }
        }
    }

    /** Flattened blade with clipped longitudinal venation; bent tip only in habit views. */
    private fun blade(length:Float,width:Float,bent:Boolean) {
        val tipX=if(bent)length*.32f else length*.06f
        val p=Path().apply{moveTo(0f,0f);cubicTo(-width,-length*.38f,-width,-length*.86f,tipX,-length)
            cubicTo(width,-length*.75f,width,-length*.25f,0f,0f);close()}
        outlined(p)
        c.save();c.clipPath(p)
        for(i in -(if(detailed)5 else 2)..(if(detailed)5 else 2))curve(i*width/6,0f,i*width/6,-length*.72f,tipX,-length,if(i==0)light else botanicalMix(green,light,.3f),if(i==0)1f else .4f)
        c.restore()
    }

    private fun peaLeaf() {
        // Stipules belong at the leaf insertion, not at the ends of the rachis.
        for(s in listOf(-1,1))b.at(0f,-8f){
            c.scale(s.toFloat(),1f)
            outlined(Path().apply{moveTo(0f,0f);cubicTo(-5f,-16f,8f,-52f,31f,-54f)
                quadTo(45f,-19f,28f,-1f);quadTo(24f,4f,20f,-1f);quadTo(16f,8f,11f,3f);quadTo(7f,10f,0f,0f);close()})
            curve(0f,0f,16f,-24f,31f,-54f,light,.8f)
        }
        leaf.draw(LeafShape.PINNATE,198f)
        for(s in listOf(-1,1))b.stroke(Path().apply{moveTo(0f,-153f);cubicTo(s*32f,-170f,s*52f,-205f,s*33f,-198f)
            cubicTo(s*21f,-193f,s*31f,-182f,s*38f,-191f)},green,1.1f)
    }

    private fun flowers() {
        when(form) {
            StudyForm.ROSETTE_SILIQUES -> {
                b.line(150f,272f,150f,176f,green,2f)
                b.at(150f,145f){flower.blossom(FlowerShape.STAR)}
                // Raceme inset, with older siliques below the growing apex.
                b.at(51f,255f,0f,.45f){
                    b.line(0f,0f,0f,-165f,green,2f)
                    for(j in 0..3)b.at(0f,-25f-j*28,if(j%2==0)-30f else 30f,.20f){silique(false)}
                    for(j in -1..1)b.at(j*12f,-158f+abs(j)*12,0f,.14f){flower.blossom(FlowerShape.STAR)}
                }
            }
            StudyForm.TENDRIL_LEGUME -> {
                curve(150f,272f,117f,203f,150f,172f,green,2f)
                b.at(150f,144f){flower.blossom(FlowerShape.PEA)}
                b.at(238f,246f,-35f,.32f){flower.blossom(FlowerShape.PEA)}
            }
            StudyForm.MONOECIOUS_CEREAL -> {
                b.at(98f,234f){tassel()}
                b.at(235f,271f,0f,.69f){ear(false)}
            }
            else -> {
                b.at(104f,246f){cerealHead(false)}
                b.at(233f,199f,0f,.8f){floret(form==StudyForm.CEREAL_PANICLE)}
            }
        }
    }

    private fun cerealHead(ripe:Boolean) {
        val rice=form==StudyForm.CEREAL_PANICLE
        val color=if(ripe)straw else green
        if(rice) {
            curve(0f,0f,-15f,-147f,18f,-180f,color,1.6f)
            for(j in 0..10) {
                val s=if(j%2==0)-1 else 1
                val y=-150f+j*13f;val reach=20f+(j%5)*7
                curve(0f,y+20f,s*reach,y-42f,s*(reach+8f),y-10f,color,1f)
                for(k in 0..4){
                    val t=(k+2)/7f;val u=1-t
                    val xx=2*u*t*s*reach+t*t*s*(reach+8f)
                    val yy=u*u*(y+20)+2*u*t*(y-42)+t*t*(y-10)
                    b.line(xx,yy,xx+s*4,yy+4,color,.55f)
                    b.at(xx+s*4,yy+4,s*153f,.23f){grainHull(color)}
                }
            }
        } else {
            b.line(0f,8f,0f,-177f,color,2f)
            for(j in 0..17) {
                val y=-16f-j*8.1f;val s=if(j%2==0)-1 else 1
                b.at(s*3f,y,s*16f,.67f){
                    for(k in 0..2)b.at((k-1)*6f,-k*3f,(k-1)*10f){grainHull(color)}
                }
            }
            b.at(0f,-151f,0f,.46f){grainHull(color)}
        }
    }

    private fun grainHull(color:Int) {
        val hull=Path().apply{moveTo(0f,0f);quadTo(-15f,-18f,0f,-42f);quadTo(15f,-22f,0f,0f);close()}
        outlined(hull,color)
        for(i in -2..2)curve(0f,0f,i*6f,-24f,0f,-42f,botanicalMix(color,cream,.55f),.55f)
        if(form==StudyForm.CEREAL_SPIKE)b.line(0f,-42f,1f,-48f,color,.6f)
    }

    private fun floret(rice:Boolean) {
        // Open lemma and palea around one fertile flower; no invented petals.
        b.at(-4f,0f,-24f){grainHull(green)};b.at(4f,0f,24f){grainHull(light)}
        b.oval(0f,-17f,7f,9f,green)
        val n=if(rice)6 else 3
        repeat(n){i->val a=(i+1)*PI/(n+1);val x=(cos(a)*36).toFloat();val y=-50f+(sin(a)*20).toFloat()
            curve(0f,-17f,x*.75f,-63f,x,y,cream,1.2f)
            b.oval(x,y,2.6f,7f,straw);b.line(x,y-6,x,y+6,ink,.45f)
        }
        for(s in listOf(-1,1)) {
            b.line(0f,-22f,s*16f,-64f,light,1f)
            for(j in 0..6){val x=s*(4f+j*1.8f);val y=-29f-j*5
                b.line(x,y,x+s*9,y-7,light,.8f);b.line(x,y,x-s*6,y-8,light,.8f)}
        }
    }

    private fun tassel() {
        b.line(0f,5f,0f,-180f,green,1.8f)
        for(s in listOf(-1,1))for(j in 0..3){
            val x=s*(24f+j*13f);val y=-141f+j*22
            b.line(0f,-30f-j*10,x,y,green,1f)
            for(k in 0..7){val t=(k+1)/9f;val xx=x*t;val yy=(-30f-j*10)*(1-t)+y*t
                for(side in listOf(-1,1)){b.at(xx+side*2,yy,side*25f,.13f){grainHull(green)}
                    if(detailed){b.line(xx+side*3,yy,xx+side*6,yy+5,cream,.5f);b.oval(xx+side*6,yy+6,1f,2.7f,straw)}}}
        }
        for(j in 0..12)b.at(if(j%2==0)-2f else 2f,-58f-j*9,if(j%2==0)-30f else 30f,.14f){grainHull(green)}
    }

    /** Ear wrapped in husks: each silk is a style/stigma, never a male stamen. */
    private fun ear(ripe:Boolean) {
        val p=Path().apply{moveTo(0f,0f);cubicTo(-35f,-37f,-26f,-133f,0f,-153f);cubicTo(26f,-136f,36f,-35f,0f,0f);close()}
        outlined(p,if(ripe)straw else green)
        if(ripe){
            c.save();c.clipPath(p)
            for(row in 0..15)for(col in -2..2){val x=col*9f;val y=-8f-row*9f
                b.oval(x,y,4.4f,4.6f,if(col%2==0)0xffe1b849.toInt() else 0xfff0cb66.toInt())
                b.oval(x-1,y-1,1.5f,2f,0xfff5d987.toInt())}
            c.restore()
        }
        for(s in listOf(-1,1)) {
            val husk=Path().apply{moveTo(0f,0f);quadTo(s*48f,-34f,s*(if(ripe)49f else 10f),-123f)
                quadTo(s*23f,-55f,0f,0f);close()}
            outlined(husk,if(ripe)light else green)
            curve(0f,0f,s*28f,-51f,s*(if(ripe)49f else 10f),-122f,light,.65f)
        }
        if(!ripe)for(i in 0..13)curve((i-7)*1.2f,-126f,(i-7)*3f,-173f,(i-7)*3.5f,-143f+abs(i-7)*2,0xffbc9973.toInt(),.7f)
    }

    private fun fruits() {
        when(form) {
            StudyForm.ROSETTE_SILIQUES -> {b.at(112f,260f){silique(false)};b.at(207f,255f,9f,.84f){silique(true)}}
            StudyForm.TENDRIL_LEGUME -> {b.at(105f,269f,-8f){pod(false)};b.at(226f,264f,9f,.81f){pod(true)}}
            StudyForm.MONOECIOUS_CEREAL -> {
                b.at(111f,266f,0f,1.085f){ear(true)}
                b.at(232f,235f){
                    outlined(Path().apply{moveTo(-15f,-39f);quadTo(-20f,-8f,0f,0f);quadTo(19f,-8f,17f,-39f);quadTo(0f,-52f,-15f,-39f);close()},straw)
                    b.oval(0f,-14f,7f,12f,cream)
                }
            }
            else -> {
                // Main caryopsis magnified; mature inflorescence is a smaller inset.
                val rice=form==StudyForm.CEREAL_PANICLE
                b.at(118f,262f){
                    val w=if(rice)21f else 35f
                    outlined(Path().apply{moveTo(0f,0f);cubicTo(-w,-12f,-w,-149f,0f,-166f)
                        cubicTo(w,-147f,w,-15f,0f,0f);close()},if(rice)cream else straw)
                    if(!rice){curve(0f,-8f,-10f,-89f,0f,-158f,ink,1.1f)
                        for(i in -7..7)b.line(i*.9f,-155f,i*1.5f,-165f-abs(i%3),cream,.6f)}
                    else for(i in -2..2)curve(i*2f,-5f,i*7f,-92f,0f,-159f,light,.5f)
                }
                b.at(235f,258f,0f,.55f){cerealHead(true)}
                if(rice)b.at(231f,99f,0f,.9f){grainHull(straw)}
            }
        }
    }

    private fun silique(open:Boolean) {
        b.line(0f,0f,0f,-180f,green,1.2f)
        val w=if(open)11f else 5f
        outlined(Path().apply{moveTo(0f,0f);quadTo(-w,-90f,0f,-180f);quadTo(w,-90f,0f,0f);close()},if(open)cream else green)
        b.line(0f,-5f,0f,-176f,light,.7f)
        if(open) {
            for(s in listOf(-1,1)){curve(0f,-178f,s*34f,-95f,s*22f,-12f,straw,2.4f)
                for(i in 0..17)b.oval(s*3.2f,-11f-i*9,1.6f,2.5f,0xff91603b.toInt())}
        }
    }
    private fun pod(open:Boolean) {
        val p=Path().apply{moveTo(0f,0f);cubicTo(-29f,-30f,-33f,-133f,0f,-180f)
            cubicTo(29f,-149f,35f,-31f,0f,0f);close()}
        outlined(p,if(open)light else green)
        curve(0f,-2f,-20f,-86f,0f,-178f,light,1f)
        for(i in 0..6){val y=-23f-i*21f
            if(open){b.oval(2f,y,9f,9f,green);b.oval(-1f,y-2,3.5f,3.5f,light);b.line(-14f,y-2,-5f,y,cream,.8f)}
            else curve(-12f,y+6,5f,y-9,16f,y+4,botanicalMix(green,light,.3f),.6f)
        }
        b.line(0f,-180f,2f,-193f,green,1.2f)
    }
}
