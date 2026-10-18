package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Diagnostic culinary plates. Profiles, never taxon IDs, select the organs.
 * Taxon sources are registered in BotanyReferences (FNA, FOC, Coste and NCSU).
 * Bulbs are vegetative organs; the fruit plate always includes the actual capsule.
 */
internal class CulinaryPlantPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val ink=0xff43513a.toInt()
    private val cream=0xfff3e9ce.toInt()
    private val straw=0xffbda269.toInt()
    private val allium=form in setOf(StudyForm.GARLIC_UMBEL,StudyForm.BROAD_ALLIUM,StudyForm.TUFT_ALLIUM,StudyForm.LEEK_UMBEL)
    private val leaf=PlantLeafPainter(c,species,detailed)
    private val flowers=PlantFlowerPainter(c,species,detailed)
    private val plain=species.copy(look=l.copy(study=null))
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,color:Int=green,width:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},color,width)
    private fun shape(p:Path,color:Int=green) {b.fill(p,color);b.stroke(p,ink,.7f)}

    fun draw(view:PlantView) {
        when {
            allium -> when(view) {
                PlantView.HABIT -> alliumHabit()
                PlantView.LEAF -> alliumLeaf()
                PlantView.FLOWER -> b.at(150f,195f){umbel(false)}
                PlantView.FRUIT -> alliumFruit()
            }
            form==StudyForm.COMPOUND_UMBEL && view==PlantView.FLOWER -> b.at(150f,238f){umbel(true)}
            form==StudyForm.COMPOUND_UMBEL && view==PlantView.FRUIT -> mericarps()
            form==StudyForm.AROMATIC_NUTLETS && view==PlantView.FLOWER -> aromaticFlowers()
            form==StudyForm.ROUND_PEPO && view==PlantView.FLOWER -> melonFlowers()
            form==StudyForm.ROUND_PEPO && view==PlantView.FRUIT -> melon()
            view==PlantView.FRUIT && form in setOf(StudyForm.INFLATED_LEGUME,StudyForm.SLENDER_LEGUME) -> legumes()
            view==PlantView.FRUIT && form in setOf(StudyForm.BEAKED_SILIQUE,StudyForm.JOINTED_SILIQUE,StudyForm.WATER_SILIQUES) -> siliques()
            else -> {
                PlantPainter(c,plain,!detailed).draw(view)
                if(form==StudyForm.ROUND_PEPO && view==PlantView.HABIT)for(i in 0..2)b.at(88f+i*55,251f){tendril()}
                if(form==StudyForm.ROUND_PEPO && view==PlantView.LEAF)b.at(247f,259f,0f,1.1f){tendril()}
            }
        }
    }

    private fun blade(length:Float,width:Float,bend:Float=0f) {
        shape(Path().apply{moveTo(-width,0f);quadTo(bend-width,-length*.6f,bend,-length)
            quadTo(bend+width,-length*.6f,width,0f);close()})
        for(i in -2..2)curve(i*width/3,0f,bend+i*width/3,-length*.6f,bend,-length,cream,.45f)
    }
    private fun bulb(cloves:Boolean) {
        if(form==StudyForm.TUFT_ALLIUM || form==StudyForm.BROAD_ALLIUM) {
            shape(Path().apply{moveTo(-8f,-38f);quadTo(-15f,0f,-9f,34f);quadTo(0f,39f,9f,34f);quadTo(15f,0f,8f,-38f);close()},cream)
            for(i in -2..2)curve(i*3f,34f,i*7f,41f,i*6f+3,53f,straw,.6f)
            return
        }
        shape(Path().apply{moveTo(-7f,-38f);quadTo(-8f,-19f,-30f,-8f);quadTo(-39f,30f,0f,34f)
            quadTo(39f,30f,30f,-8f);quadTo(8f,-19f,7f,-38f);close()},cream)
        if(cloves)for(i in -2..2)curve(i*3f,-32f,i*18f,14f,i*3f,31f,straw,.85f)
        for(i in -3..3)curve(i*3f,34f,i*7f,41f,i*6f+3,53f,straw,.6f)
    }
    private fun alliumHabit() {
        b.oval(150f,287f,90f,4f,0x166a6145)
        if(form==StudyForm.TUFT_ALLIUM) {
            for(i in -1..1)b.at(150f+i*27,269f,0f,.45f){bulb(false)}
        } else b.at(150f,265f,0f,.4f){bulb(form==StudyForm.GARLIC_UMBEL)}
        if(form==StudyForm.BROAD_ALLIUM) {
            for(s in listOf(-1,1))b.at(150f,261f,s*39f,.75f){leaf.draw(LeafShape.LANCE,198f)}
        } else {
            for(i in -3..3)b.at(150f+i*6,257f,i*14f,.78f){blade(170f,if(form==StudyForm.LEEK_UMBEL)10f else 5f,i*3f)}
            if(form==StudyForm.LEEK_UMBEL)b.line(150f,272f,150f,213f,cream,9f)
        }
        b.line(150f,257f,150f,75f,green,2f)
        b.at(150f,83f,0f,.43f){umbel(false)}
    }
    private fun alliumLeaf() {
        if(form==StudyForm.BROAD_ALLIUM) {
            b.at(135f,264f){leaf.draw(LeafShape.LANCE,198f)}
            b.at(233f,265f,16f,.5f){leaf.draw(LeafShape.LANCE,198f)}
        } else {
            b.at(122f,265f){blade(198f,if(form==StudyForm.LEEK_UMBEL)22f else 10f,12f)}
            if(form==StudyForm.TUFT_ALLIUM) {
                // Cross-section of a hollow leaf, separate from the main blade scale.
                b.oval(229f,135f,24f,16f,green);b.oval(229f,135f,17f,10f,cream)
            } else {
                b.line(205f,136f,256f,136f,green,5f)
                b.line(205f,135f,256f,135f,cream,.8f)
            }
            b.at(231f,236f,0f,.62f){bulb(form==StudyForm.GARLIC_UMBEL)}
        }
    }
    /** Simple Allium umbels versus compound Apiaceae umbels: the second ray order is explicit. */
    private fun umbel(compound:Boolean) {
        b.line(0f,38f,0f,0f,green,2f)
        val flat=form==StudyForm.BROAD_ALLIUM
        val n=if(compound)11 else if(flat || form==StudyForm.GARLIC_UMBEL)9 else 25
        repeat(n){i->
            val a=if(compound || flat) PI*(1.12+i*.76/(n-1)) else i*2.399963
            val r=if(compound || flat)90f else sqrt((i+1f)/n)*83f
            val x=cos(a).toFloat()*r
            val y=if(compound || flat)sin(a).toFloat()*r-34 else sin(a).toFloat()*r-65
            b.line(0f,0f,x,y,green,.9f)
            if(compound)repeat(7){j->val aa=PI*(1.1+j*.8/6);val xx=x+cos(aa).toFloat()*18;val yy=y+sin(aa).toFloat()*18
                b.line(x,y,xx,yy,green,.5f);b.at(xx,yy,j*20f,.055f){flowers.blossom(FlowerShape.STAR)}}
            else b.at(x,y,i*21f,if(flat).18f else .105f){flowers.blossom(FlowerShape.STAR)}
        }
        if(form==StudyForm.GARLIC_UMBEL)repeat(9){i->
            val a=i*2.399963;val r=sqrt(i/9f)*23f
            b.oval(cos(a).toFloat()*r,-65f+sin(a).toFloat()*r,5f,8f,straw)
        }
        // A single enlarged flower is outside the scale of the main inflorescence.
        if(detailed)b.at(81f,27f,0f,.24f){flowers.blossom(FlowerShape.STAR)}
    }
    private fun alliumFruit() {
        b.at(113f,191f) {
            b.line(0f,57f,0f,30f,green,2f)
            for(i in -1..1) {
                val x=i*22f
                shape(Path().apply{moveTo(0f,32f);quadTo(x-29f,-6f,x,-69f);quadTo(x+29f,-6f,0f,32f);close()},straw)
                curve(0f,32f,x,-14f,x,-63f,cream,1f)
            }
            if(form!=StudyForm.GARLIC_UMBEL)for(i in 0..5)b.oval((i%3-1)*11f,-39f+(i/3)*30f,4f,6f,ink)
        }
        b.at(233f,238f,0f,.77f){bulb(form==StudyForm.GARLIC_UMBEL)}
        if(form==StudyForm.GARLIC_UMBEL) {
            // Bulbils are vegetative propagules, distinct from the seed-bearing capsule.
            for(i in -1..1)b.oval(218f+i*15,82f,7f,11f,straw)
        }
    }
    private fun legumes() {
        val inflated=form==StudyForm.INFLATED_LEGUME
        val w=if(inflated)45f else 9f
        for(open in listOf(false,true))b.at(if(open)226f else 105f,268f,if(open)8f else -5f,if(open).8f else 1f) {
            shape(Path().apply{moveTo(0f,0f);cubicTo(-w,-35f,-w,-142f,0f,-180f)
                cubicTo(w,-145f,w,-31f,0f,0f);close()},if(open)cream else green)
            curve(0f,0f,-w*.7f,-90f,0f,-180f,straw,1f)
            if(open) {
                val count=l.fruitDetail.podSeeds
                repeat(count){i->val y=-25f-i*130f/count
                    b.oval(0f,y,if(inflated)14f else 4f,if(inflated)15f else 4.5f,straw)
                    if(inflated)b.oval(8f,y-8f,6f,5f,straw)
                }
            }
            if(detailed && inflated)repeat(24){i->val y=-15f-i*6;val x=sin(i*2.4f)*30
                b.line(x,y,x+2,y-3,cream,.5f)}
        }
    }
    private fun siliques() {
        val jointed=form==StudyForm.JOINTED_SILIQUE
        val mustard=form==StudyForm.BEAKED_SILIQUE
        for(open in listOf(false,true))b.at(if(open)227f else 105f,268f,if(open)7f else -6f,if(open).85f else 1f) {
            b.line(0f,15f,0f,0f,green,1.3f)
            if(jointed) {
                for(i in 0..5) {
                    val y=-14f-i*24f
                    shape(Path().apply{moveTo(0f,y+12);quadTo(-21f,y,0f,y-12);quadTo(21f,y,0f,y+12);close()},straw)
                    for(s in listOf(-1,1))b.line(s*4f,y+7,s*4f,y-7,cream,.6f)
                }
                shape(Path().apply{moveTo(-4f,-146f);lineTo(0f,-180f);lineTo(4f,-146f);close()},straw)
                // Detached one-seeded segment; the fruit has no opening valves.
                if(open){b.oval(29f,-33f,10f,12f,straw);b.oval(29f,-33f,4f,6f,ink)}
            } else {
                val top=if(mustard)-91f else -180f
                val w=if(mustard)11f else 9f
                shape(Path().apply{moveTo(0f,0f);quadTo(-w,top/2,0f,top);quadTo(w,top/2,0f,0f);close()},if(open)cream else green)
                if(mustard)shape(Path().apply{moveTo(0f,top);quadTo(-19f,-135f,13f,-180f);quadTo(19f,-133f,0f,top);close()},green)
                if(open) {
                    val count=if(mustard)4 else 14
                    for(i in 0 until count)for(s in listOf(-1,1)) {
                        b.oval(s*3f,-10f-i*(-top-20)/count,2f,3f,straw)
                        if(!mustard)b.oval(s*7f,-13f-i*(-top-20)/count,1.5f,2.5f,straw)
                    }
                    for(s in listOf(-1,1))curve(0f,top,s*34f,top/2,s*23f,-5f,straw,2f)
                }
                if(detailed && mustard)for(i in 0..12)for(s in listOf(-1,1))b.line(s*7f,-4f-i*6,s*13f,-8f-i*6,cream,.6f)
            }
        }
    }
    private fun melon() {
        b.oval(110f,174f,60f,67f,l.fruitColor)
        for(i in -2..2)curve(110f,107f,110f+i*30,174f,110f,241f,cream,1f)
        b.line(110f,107f,116f,84f,green,3f)
        b.oval(238f,193f,43f,56f,l.fruitColor)
        b.oval(238f,193f,39f,52f,0xffefb469.toInt())
        b.oval(238f,193f,22f,35f,cream)
        for(i in 0..15){val a=i*2.399963;b.oval(238f+cos(a).toFloat()*14,193f+sin(a).toFloat()*27,3f,5f,straw)}
    }
    private fun tendril() {
        val path=Path().apply{moveTo(0f,0f);quadTo(12f,-9f,18f,-14f)}
        for(i in 0..60){val t=i/60f;val a=t*PI*5;val r=9f*(1-t)
            path.lineTo(18f+cos(a).toFloat()*r,-23f+sin(a).toFloat()*r)}
        b.stroke(path,green,.8f)
    }
    private fun melonFlowers() {
        b.line(108f,265f,108f,192f,green,2f)
        b.at(108f,143f){flowers.blossom(FlowerShape.CUP)}
        b.line(236f,267f,236f,230f,green,2f)
        b.oval(236f,221f,11f,23f,green)
        val female=PlantFlowerPainter(c,species.copy(look=l.copy(flowerDetail=l.flowerDetail.copy(stamens=0,styles=1))),detailed)
        b.at(236f,180f,0f,.45f){
            female.blossom(FlowerShape.CUP)
            // One style ending in three stigma lobes, not three independent styles.
            for(i in -1..1)b.oval(i*3f,-19f-if(i==0)2f else 0f,2.6f,2f,0xffc2ca8d.toInt())
        }
    }
    private fun aromaticFlowers() {
        PlantPainter(c,plain,!detailed).draw(PlantView.FLOWER)
        // Inflorescence inset; imbricate bracts in the dense marjoram profile.
        b.at(54f,257f,0f,.55f) {
            b.line(0f,0f,0f,-166f,green,1.5f)
            for(j in 0..5)for(s in listOf(-1,1)) {
                val y=-30f-j*21f
                if(l.flowerDetail.denseRaceme)b.at(0f,y,s*40f,.3f){leaf.draw(LeafShape.OVAL,65f)}
                b.at(s*11f,y-6,s*35f,.16f){flowers.blossom(FlowerShape.LIPPED)}
            }
        }
    }
    private fun mericarps() {
        // Each half contains one seed. Shape varies independently of the common umbel architecture.
        val slender=l.fruitDetail.elongation>1.4f
        val flat=l.fruitDetail.elongation<1f
        val w=if(slender)13f else if(flat)33f else 23f
        for(side in listOf(-1,1))b.at(150f+side*(w+8f),178f,side*9f) {
            val bend=if(slender)side*17f else 0f
            val outline=Path().apply{moveTo(0f,60f);cubicTo(-w+bend,40f,-w+bend,-43f,0f,-60f)
                cubicTo(w+bend,-43f,w+bend,40f,0f,60f);close()}
            shape(outline,straw)
            c.save();c.clipPath(outline)
            for(i in -2..2)curve(0f,60f,bend+i*w*.65f,0f,0f,-60f,cream,.85f)
            c.restore()
            if(flat)curve(0f,59f,-w*1.4f,0f,0f,-59f,cream,2.5f)
            b.line(0f,60f,-side*6f,92f,green,.8f)
        }
    }
}
