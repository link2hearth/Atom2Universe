package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*

/** Original diagnostic plates driven by reusable morphology, with independently enlarged insets.
 * Botanical references and explanations belong to the corresponding sourced catalog records.
 */
internal class MeadowPlantPainter(private val c:Canvas,private val species:PlantSpecies,private val detailed:Boolean) {
    private val l=species.look
    private val form=requireNotNull(l.study)
    private val b=BotanyInk(c)
    private val green=l.leafColor
    private val cream=0xfff8f1d9.toInt()
    private val brown=0xff956d43.toInt()
    private val hair=0xff9eaa8c.toInt()
    private val color=l.color.ink
    private val leaf=PlantLeafPainter(c,species,detailed)
    private fun fallback(v:PlantView)=PlantPainter(c,species.copy(look=l.copy(study=null)),!detailed).draw(v)
    private fun curve(x:Float,y:Float,cx:Float,cy:Float,xx:Float,yy:Float,col:Int=green,w:Float=1f)=
        b.stroke(Path().apply{moveTo(x,y);quadTo(cx,cy,xx,yy)},col,w)
    private fun shape(p:Path,col:Int) { b.fill(p,col);b.stroke(p,botanicalMix(col,brown,.35f),.7f) }
    fun draw(v:PlantView) {
        when(v) {
            PlantView.HABIT -> when(form) {
                StudyForm.SNAPDRAGON_POUCH,StudyForm.TOADFLAX_SPUR -> {
                    b.line(150f,279f,150f,62f,green,2f)
                    for(i in 0..7) {
                        val y=265f-i*17
                        if(l.herbDetail?.lowerLeavesOpposite==true && i<3)for(side in listOf(-1,1))b.at(150f,y,side*60f,.20f){leaf.draw(l.leaf,198f)}
                        else b.at(150f,y,if(i%2==0)-55f else 55f,.20f){leaf.draw(l.leaf,198f)}
                    }
                    for(i in 0..7)b.at(150f+if(i%2==0)-10 else 10,70f+i*12,if(i%2==0)-15f else 15f,.17f){lipped(form==StudyForm.TOADFLAX_SPUR,false)}
                }
                StudyForm.MEADOW_REFERENCE -> if(l.flowerDetail.corolla==CorollaForm.FOXGLOVE) {
                    for(i in -3..3)b.at(150f,279f,i*24f,.32f){leaf.draw(l.leaf,198f)}
                    b.line(150f,278f,150f,53f,green,2f)
                    for(i in 0..6){val y=69f+i*22;b.at(164f,y,scale=.17f){PlantFlowerPainter(c,species,detailed).blossom()};if(i%2==0)b.at(150f,y+18,-55f,.18f){leaf.draw(l.leaf,198f)}}
                } else fallback(v)
                StudyForm.FRINGED_ROSE -> {
                    for(i in -2..2) {
                        val x=150f+i*48;val top=70f+abs(i)*18
                        val q=Path().apply{moveTo(150f,278f);cubicTo(150f+i*9,top,x,top-28,x,top+68)}
                        b.stroke(q,brown,2.4f)
                        for(j in 1..6){val t=j/7f;val y=278f-(278-top)*t;val xx=150f+i*35*t;b.at(xx,y,i*22f,.15f){leaf.draw(LeafShape.PINNATE,198f)};shape(Path().apply{moveTo(xx,y+4);lineTo(xx+7,y-1);lineTo(xx,y-2);close()},brown)}
                        b.at(x,top+65,scale=.12f){PlantFlowerPainter(c,species,detailed).head()}
                    }
                }
                StudyForm.COLTSFOOT_SEASONS -> {
                    curve(140f,279f,132f,185f,154f,91f,w=3f)
                    repeat(12){i->val y=260f-i*13;b.at(140f+(260-y)*.07f,y,if(i%2==0)-45f else 45f){shape(Path().apply{moveTo(0f,0f);quadTo(-8f,-12f,0f,-23f);quadTo(8f,-9f,0f,0f)},brown)}}
                    b.at(154f,91f,scale=.43f){PlantFlowerPainter(c,species,detailed).blossom(FlowerShape.DAISY)}
                    b.at(236f,267f,scale=.36f){leaf.draw(l.leaf,198f)}
                }
                StudyForm.ANEMONE_INVOLUCRE,StudyForm.PASQUE_PLUMOSE -> {
                    for(i in -2..2)b.at(143f,277f,i*32f,.40f){leaf.draw(l.leaf,198f)}
                    curve(147f,275f,160f,167f,150f,90f,w=2f)
                    for(i in -1..1)b.at(154f,167f,i*65f,.25f){leaf.draw(l.leaf,145f)}
                    b.at(150f,90f,scale=.40f){if(form==StudyForm.PASQUE_PLUMOSE)pasque() else PlantFlowerPainter(c,species,detailed).blossom()}
                    if(form==StudyForm.PASQUE_PLUMOSE) {
                        b.line(249f,278f,249f,169f,green,1f)
                        b.at(249f,163f,scale=.24f){plumoseCluster()}
                    }
                }
                StudyForm.BUGLE_SPIKE -> {
                    curve(72f,276f,137f,263f,227f,277f,w=2f)
                    for(x in listOf(82f,156f,220f)) {
                        for(i in -2..2)b.at(x,277f,i*40f,.22f){leaf.draw(l.leaf,198f)}
                        b.line(x,275f,x,102f,green,2f)
                        for(y in listOf(243f,219f))for(side in listOf(-1,1))b.at(x,y,side*60f,.14f){leaf.draw(l.leaf,198f)}
                        for(y in listOf(124f,149f,175f,201f))for(side in listOf(-1,1))b.at(x+side*5,y,side*25f,.15f){lipped(false,true)}
                    }
                }
                else -> fallback(v)
            }
            PlantView.LEAF -> when(form) {
                StudyForm.WHITEBEAM_LOBES -> {
                    b.at(147f,267f){leaf.draw(LeafShape.OVAL,198f,LeafMargin.LOBED,l.leafDetail)}
                    b.at(254f,257f,scale=.36f){PlantLeafPainter(c,species.copy(look=l.copy(leafColor=0xffbfc8ad.toInt())),detailed).draw(LeafShape.OVAL,198f,LeafMargin.LOBED,l.leafDetail)}
                }
                StudyForm.FRINGED_ROSE,StudyForm.AGRIMONY_BURR -> {
                    b.at(150f,269f){leaf.draw(l.leaf,198f)}
                    if(form==StudyForm.FRINGED_ROSE)for(side in listOf(-1,1)) {
                        b.line(150f+side*5,263f,150f+side*7,231f,green,3f)
                        for(i in 0..9)b.line(150f+side*6,263f-i*3,150f+side*(12+i%3),259f-i*3,green,.7f)
                    } else for(i in 1..4)for(side in listOf(-1,1))b.at(150f,263f-i*35,side*62f,.16f){leaf.draw(LeafShape.OVAL,80f)}
                }
                else -> fallback(v)
            }
            PlantView.FLOWER -> when(form) {
                StudyForm.MEADOW_REFERENCE -> {
                    fallback(v)
                    if(l.flower==FlowerShape.PANICLE)b.at(246f,247f,scale=.35f){PlantFlowerPainter(c,species,detailed).blossom(FlowerShape.STAR)}
                }
                StudyForm.SNAPDRAGON_POUCH,StudyForm.TOADFLAX_SPUR,StudyForm.BUGLE_SPIKE -> {
                    b.at(150f,150f){lipped(form==StudyForm.TOADFLAX_SPUR,form==StudyForm.BUGLE_SPIKE)}
                    if(form==StudyForm.BUGLE_SPIKE) {
                        b.line(247f,275f,247f,221f,green,1f)
                        for(y in listOf(229f,246f,263f))for(side in listOf(-1,1))b.at(247f,y,side*30f,.12f){lipped(false,true)}
                    }
                }
                StudyForm.AGRIMONY_BURR -> {
                    b.at(150f,150f){PlantFlowerPainter(c,species,detailed).blossom(FlowerShape.STAR)}
                    b.line(247f,280f,247f,219f,green,1f)
                    for(i in 0..6)b.at(247f+if(i%2==0)-8 else 8,226f+i*7,scale=.08f){PlantFlowerPainter(c,species,detailed).blossom(FlowerShape.STAR)}
                }
                StudyForm.PASQUE_PLUMOSE -> {b.at(150f,150f){pasque()};b.at(242f,251f,scale=.36f){PlantFlowerPainter(c,species,detailed).blossom(FlowerShape.CUP)}}
                StudyForm.CORNFLOWER_HEAD -> b.at(150f,150f){cornflower()}
                StudyForm.WHITE_CAMPION -> {
                    b.at(150f,144f){PlantFlowerPainter(c,species.copy(look=l.copy(flowerDetail=l.flowerDetail.copy(fusedCalyx=false))),detailed).blossom()}
                    b.at(239f,252f,scale=.38f){ribbedCalyx()}
                }
                else -> fallback(v)
            }
            PlantView.FRUIT -> when(form) {
                StudyForm.SNAPDRAGON_POUCH -> {
                    b.oval(125f,150f,43f,60f,brown)
                    curve(125f,207f,132f,148f,125f,91f,cream)
                    for(x in listOf(110f,140f))b.oval(x,107f,8f,5f,0xff493c2e.toInt())
                    b.oval(125f,94f,5f,4f,0xff493c2e.toInt())
                    for(i in 0..7)b.oval(227f+i%3*14,231f+i/3*14,3f,4f,brown)
                }
                StudyForm.WHITE_CAMPION -> {b.at(125f,150f){ribbedCalyx()};for(i in 0..9){val x=98f+i*6;b.line(x,100f,x+(i-4.5f)*2,88f,brown,2f)}}
                StudyForm.PASQUE_PLUMOSE -> {
                    b.oval(125f,221f,4f,9f,brown);curve(125f,214f,139f,145f,133f,51f,brown,1.2f)
                    for(i in 0..25){val y=207f-i*5.7f;for(side in listOf(-1,1))curve(131f,y,131f+side*14,y-9,131f+side*23,y-18,hair,.9f)}
                    b.at(240f,247f,scale=.32f){plumoseCluster()}
                }
                StudyForm.COLTSFOOT_SEASONS,StudyForm.CORNFLOWER_HEAD -> {
                    b.oval(125f,166f,7f,26f,brown)
                    for(i in -12..12){val x=125f+i*3.4f;val y=if(form==StudyForm.CORNFLOWER_HEAD)118f+abs(i)*.7f else 56f+abs(i)*2f;b.line(125f,140f,x,y,hair,1f)}
                    b.line(122f,187f,122f,145f,cream,.7f)
                }
                StudyForm.AGRIMONY_BURR -> {
                    b.oval(125f,162f,38f,60f,brown)
                    for(i in -3..3)curve(125f+i*6,219f,125f+i*11,160f,125f+i*7,108f,cream,.8f)
                    for(i in 0..19){val a=i*2*PI/20;val x=125f+cos(a).toFloat()*34;val y=110f+sin(a).toFloat()*9;curve(x,y,x+cos(a).toFloat()*15,y-23,x+cos(a).toFloat()*10,y-17,brown,1.5f)}
                    shape(Path().apply{moveTo(210f,260f);quadTo(198f,217f,231f,209f);quadTo(266f,217f,258f,260f);lineTo(251f,256f);quadTo(256f,224f,231f,220f);quadTo(211f,224f,217f,256f);close()},brown)
                    b.oval(221f,240f,7f,13f,brown);b.oval(239f,240f,7f,13f,brown)
                }
                StudyForm.BUR_MEDICK -> {
                    b.oval(125f,150f,60f,60f,brown)
                    for(r in listOf(48f,34f,20f))b.stroke(Path().apply{addCircle(125f,150f,r,Path.Direction.CW)},cream,1.3f)
                    for(i in 0..27){val a=i*2*PI/28;val x=125f+cos(a).toFloat()*59;val y=150f+sin(a).toFloat()*59;curve(x,y,x+cos(a).toFloat()*16,y+sin(a).toFloat()*16,x+cos(a+.25).toFloat()*12,y+sin(a+.25).toFloat()*12,brown,1.2f)}
                }
                StudyForm.ANEMONE_INVOLUCRE -> {
                    b.oval(125f,150f,23f,60f,brown)
                    for(i in 0..29){val a=i*2*PI/30;b.line(125f+cos(a).toFloat()*23,150f+sin(a).toFloat()*60,125f+cos(a).toFloat()*29,150f+sin(a).toFloat()*66,hair,.8f)}
                    for(i in 0..23){val a=i*2.399963;val r=sqrt(i/24f)*25;val x=237f+cos(a).toFloat()*r;val y=241f+sin(a).toFloat()*r;b.oval(x,y,4f,6f,brown);for(j in 0..7){val t=j*2*PI/8;b.line(x+cos(t).toFloat()*4,y+sin(t).toFloat()*6,x+cos(t).toFloat()*7,y+sin(t).toFloat()*9,cream,.7f)}}
                }
                else -> fallback(v)
            }
        }
    }
    private fun ribbedCalyx() {
        b.oval(0f,8f,44f,60f,green)
        for(i in -9..9){val x=i*4f;curve(x*.4f,65f,x*1.4f,8f,x*.6f,-47f,botanicalMix(green,cream,.55f),.7f)}
        for(i in -2..2)shape(Path().apply{moveTo(i*12f-5,-43f);lineTo(i*14f,-65f);lineTo(i*12f+5,-43f);close()},green)
    }
    private fun plumoseCluster() {
        for(i in 0..11)b.at(0f,0f,i*30f) {
            b.oval(0f,-7f,3f,7f,brown);b.line(0f,-14f,0f,-88f,brown,1f)
            for(j in 0..10)for(side in listOf(-1,1))b.line(0f,-18f-j*6,side*12f,-28f-j*6,hair,.8f)
        }
    }
    private fun lipped(spur:Boolean,bugle:Boolean) {
        b.line(-26f,87f,-30f,29f,green,2f)
        if(spur)shape(Path().apply{moveTo(-37f,15f);quadTo(-108f,31f,-113f,74f);quadTo(-81f,31f,-23f,31f);close()},color)
        else if(!bugle)b.oval(-39f,27f,18f,18f,color)
        if(!bugle)shape(Path().apply{moveTo(-41f,28f);quadTo(-52f,-15f,-9f,-46f);quadTo(20f,-61f,31f,-30f);quadTo(45f,-48f,61f,-26f);quadTo(82f,-8f,49f,16f);lineTo(31f,37f);close()},color)
        if(bugle) {
            shape(Path().apply{moveTo(-9f,-23f);lineTo(15f,-23f);lineTo(24f,31f);lineTo(-24f,31f);close()},color)
            b.oval(4f,-23f,18f,7f,color)
        }
        shape(Path().apply{moveTo(-31f,24f);quadTo(-45f,54f,-20f,65f);quadTo(-5f,90f,13f,68f);quadTo(44f,79f,44f,53f);quadTo(66f,49f,36f,23f);close()},color)
        if(!bugle)b.oval(13f,23f,31f,15f,if(spur)0xffe5a137.toInt() else botanicalMix(color,cream,.7f))
        else for(i in 0..3){b.line(-8f+i*8,-11f,-5f+i*8,14f,cream,1f);b.oval(-5f+i*8,14f,2f,2f,brown)}
    }
    private fun pasque() {
        curve(-22f,-90f,-36f,-55f,-4f,-42f,w=2f)
        for(i in 0..5){val x=(i-2.5f)*13;shape(Path().apply{moveTo(-4f,-43f);quadTo(x-27,-5f,x-19,53f);quadTo(x-29,70f,x-35,58f);quadTo(x-19,88f,x+7,57f);quadTo(x+23,12f,-4f,-43f);close()},botanicalMix(color,cream,(i%3)*.12f))}
        for(i in 0..19){val x=(i%10-4.5f)*5;b.line(x,39f,x,62f,cream,.8f);b.oval(x,62f,1.5f,2f,0xffcfb33a.toInt())}
        if(detailed)for(i in 0..18)b.line(-18f+i*2,-39f,-23f+i*2,-46f,cream,.7f)
    }
    private fun cornflower() {
        b.oval(0f,52f,42f,38f,green)
        for(i in -3..3){curve(i*9f,80f,i*14f,57f,i*10f,30f,brown,2f);for(j in 0..5)b.line(i*10f,38f+j*5,i*10f+5,35f+j*5,brown,.8f)}
        for(i in 0..9)b.at(0f,0f,i*36f){shape(Path().apply{moveTo(-5f,-28f);lineTo(-23f,-72f);lineTo(-19f,-83f);lineTo(-8f,-73f);lineTo(0f,-91f);lineTo(8f,-73f);lineTo(19f,-83f);lineTo(23f,-72f);lineTo(5f,-28f);close()},color)}
        for(i in 0..36){val a=i*2.399963;val r=sqrt(i/37f)*30;b.at(cos(a).toFloat()*r,sin(a).toFloat()*r){b.line(0f,9f,0f,-9f,color,3f);for(j in -2..2)b.line(0f,-8f,j*2f,-14f,color,1f)}}
    }
}
