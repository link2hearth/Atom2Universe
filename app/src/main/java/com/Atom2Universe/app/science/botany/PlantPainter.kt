package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.*
import kotlin.random.Random

/** Original vector plates. The same botanical traits drive drawing, legend and query facets. */
class PlantPainter(private val c: Canvas, private val species: PlantSpecies, private val compact:Boolean=false) {
    private val l = species.look
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val green = l.leafColor
    private val ink = 0xff374b37.toInt()
    private val random = Random(l.seed)

    private fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int = ink, width: Float = 1f) {
        p.color = color; p.style = Paint.Style.STROKE; p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, xx, yy, p)
    }
    private fun path(path: Path, color: Int, border: Int? = ink, width: Float = .7f) {
        p.style = Paint.Style.FILL; p.color = color; c.drawPath(path, p)
        if (border != null) { p.style = Paint.Style.STROKE; p.color = border; p.strokeWidth = width; c.drawPath(path, p) }
    }
    private fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int, border: Int? = null) {
        path(Path().apply { addOval(RectF(x-rx,y-ry,x+rx,y+ry), Path.Direction.CW) }, color, border)
    }
    private inline fun at(x: Float, y: Float, angle: Float = 0f, size: Float = 1f, block: () -> Unit) {
        c.save(); c.translate(x,y); c.rotate(angle); c.scale(size,size); block(); c.restore()
    }

    fun draw(mode: PlantView) {
        when (mode) {
            PlantView.HABIT -> habit()
            PlantView.LEAF -> leaves()
            PlantView.FLOWER -> flowers()
            PlantView.FRUIT -> fruits()
        }
    }

    private fun habit() {
        oval(150f,287f,105f,5f,0x166a6145)
        if(l.crown==Crown.SHRUB && l.herbDetail!=null){profiledHerb(l.herbDetail);return}
        if(l.fineShrub || species.id in setOf("cytisus","ulex","calluna","erica","rosa")) { fineShrub(); return }
        when (l.crown) {
            Crown.HERB -> herb()
            Crown.GRASS -> grass(160f,277f,.88f)
            else -> PlantTreePainter(c,species,!compact).draw()
        }
    }

    private fun fineShrub() {
        val heather=species.id=="calluna" || species.id=="erica"
        val rose=species.id=="rosa"
        val count=if(heather) 15 else 11
        repeat(count){i->
            val side=(i-(count-1)/2f)/((count-1)/2f)
            val x=150f+side*88f;val top=70f+abs(side)*65f+random.nextFloat()*20
            val controlX=if(rose) x+side*28 else 150f+side*37
            val stem=Path().apply{moveTo(150f,280f);quadTo(controlX,top-15,x,top)}
            p.style=Paint.Style.STROKE;p.color=if(rose)l.bark else green;p.strokeWidth=if(heather)1.8f else 2.7f;c.drawPath(stem,p)
            repeat(12){j->val t=(j+1)/13f;val u=1-t
                val xx=u*u*150f+2*u*t*controlX+t*t*x;val yy=u*u*280f+2*u*t*(top-15)+t*t*top
                if(species.id=="ulex") for(s in listOf(-1,1))line(xx,yy,xx+s*(10f+random.nextFloat()*9),yy-14f,green,1.6f)
                else if(heather)for(s in listOf(-1,1))line(xx,yy,xx+s*5f,yy-5f,green,1.7f)
                else if(j%3==0)at(xx,yy,if(j%2==0)-55f else 55f,.17f){leaf(l.leaf,90f)}
                if((heather && j>5) || (!heather && j%4==1))at(xx+if(j%2==0)4f else -4f,yy,0f,if(rose).13f else if(heather).055f else .07f){blossom(l.flower)}
            }
        }
    }

    private fun herb() {
        if(l.herbDetail!=null) { profiledHerb(l.herbDetail); return }
        if(species.id=="trifolium_repens") {
            line(62f,273f,243f,273f,green,2.2f)
            for(i in 0..3){val x=78f+i*48
                at(x,272f,(i-1)*8f,.36f){leaf(LeafShape.TRIFOLIATE,105f)}
                line(x,272f,x+5f,126f+i%2*23f,green,1.5f)
                at(x+5f,126f+i%2*23f,0f,.24f){flowerHead()}
            };return
        }
        val rosette=l.arrangement==LeafArrangement.ROSETTE || species.id in setOf("digitalis","salvia","campanula")
        if(rosette) for(i in 0..6) at(150f,274f,(i-3)*22f,.42f) { leaf(l.leaf,70f) }
        val tall=species.id=="digitalis"
        val stems=if(tall) 1 else 3
        repeat(stems) { i ->
            val x=120f+i*30f; val top=if(tall) 49f else 90f+i%2*25f
            line(150f,273f,x,top,green,2.1f)
            if(!rosette || tall) repeat(4) { j ->
                val t=(j+1)/5f;val yy=273f+(top-273f)*t;val xx=150f+(x-150f)*t
                at(xx,yy,if(j%2==0) -62f else 62f,.36f) { leaf(l.leaf,80f) }
                if(l.arrangement==LeafArrangement.OPPOSITE) at(xx,yy,if(j%2==0) 62f else -62f,.36f) { leaf(l.leaf,80f) }
            }
            if(tall) repeat(11) { j -> at(149f,65f+j*13f,65f,.28f) { blossom(FlowerShape.BELL) } }
            else if(species.id=="primula") {
                repeat(5){j->val xx=x+(j-2)*8;val yy=top-11+abs(j-2)*4;line(x,top+12,xx,yy,green,.8f);at(xx,yy,15f,.10f){blossom(FlowerShape.BELL)}}
            } else at(x,top,0f,.24f) { flowerHead() }
        }
    }

    private fun profiledHerb(form:HerbArchitecture) {
        if(form.woodyMat) {
            val brush=BotanyInk(c)
            for(row in 0..2) {
                val y=274f-row*23f
                brush.stroke(Path().apply{moveTo(48f,y+4);quadTo(149f,y-25f,248f,y)},l.bark,2.5f)
                for(i in 0..7){val x=56f+i*26;val yy=y-12f+sin(i*.8f)*5
                    line(x,yy+7,x+6,yy-25,l.bark,1.2f)
                    for(j in 0..2)at(x+2+j*2,yy-j*9,(if(j%2==0)-1 else 1)*58f,.37f){leaf(l.leaf,73f)}
                    if(i%3==row%3)at(x+6,yy-24,0f,.10f){flowerHead()}
                }
            };return
        }
        if(form.creeping) {
            line(50f,274f,247f,274f,green,2f)
            repeat(4){i->val x=66f+i*55
                at(x,271f,(i-1)*8f,.48f){leaf(l.leaf,120f)}
                line(x,274f,x+14f,165f+i%2*20,green,1.2f)
                at(x+14f,165f+i%2*20,0f,form.flowerScale){flowerHead()}
                line(x,274f,x-4f,282f,l.bark,.6f)
            };return
        }
        form.basalLeaf?.takeIf{form.basalRosette}?.let { shape ->
            repeat(7){i->at(150f,275f,(i-3)*24f,.63f){leaf(shape,104f,detail=form.basalDetail)}}
        }
        repeat(if(form.branches)3 else 1){i->
            val x=if(form.branches)103f+i*47 else 150f
            val top=65f+(i%2)*24
            line(150f,274f,x,top,green,1.8f)
            repeat(4){j->val t=(j+1)/5f;val xx=150+(x-150)*t;val yy=274+(top-274)*t
                val lower=!form.basalRosette && j<2 && form.basalLeaf!=null
                val shape=if(lower)form.basalLeaf!! else form.stemLeaf ?: l.leaf
                val sides=if(l.arrangement==LeafArrangement.OPPOSITE || lower && form.lowerLeavesOpposite)listOf(-1,1)else listOf(if(j%2==0)-1 else 1)
                for(side in sides)at(xx,yy,side*62f,.40f){leaf(shape,95f,detail=if(lower)form.basalDetail else null)}
                if(form.branches && j in 1..2){val side=if(j%2==0)-1 else 1
                    line(xx,yy,xx+side*28,yy-27,green,.9f)
                    at(xx+side*28,yy-27,0f,form.flowerScale*.85f){flowerHead()}
                }
            }
            at(x,top,0f,form.flowerScale){flowerHead()}
        }
    }

    private fun leaves() {
        // One large diagnostic leaf plus a smaller twig showing insertion; each has an independent composition.
        at(162f,260f,5f) { leaf(l.leaf,if(l.leaf==LeafShape.NEEDLE && l.needleCount>2) 145f else 198f) }
        at(51f,275f,-14f,.54f) { twig() }
        l.herbDetail?.let{form->form.basalLeaf?.takeIf{it!=l.leaf || form.basalDetail!=null}?.let{shape->
            at(247f,275f,12f,.51f){leaf(shape,112f,detail=form.basalDetail)}
        }}
        if(species.id=="campanula") at(244f,257f,18f,.52f) { leaf(LeafShape.ROUND,75f) }
        if(species.id=="abies") {
            at(245f,237f,12f) {
                line(0f,0f,0f,-80f,0xffc4d1a7.toInt(),8f)
                line(-2f,-5f,-2f,-76f,0xffeeeadd.toInt(),1.8f);line(2f,-5f,2f,-76f,0xffeeeadd.toInt(),1.8f)
            }
        }
        if(species.id=="picea") {
            path(Path().apply{moveTo(240f,219f);lineTo(253f,233f);lineTo(240f,247f);lineTo(227f,233f);close()},green,ink)
        }
    }

    private fun twig() {
        if(l.leafDetail.awlShoot){PlantConiferPainter(c,l,!compact).awlShoot(175f);return}
        if(l.arrangement==LeafArrangement.ROSETTE) {
            for(i in 0..7) at(0f,-80f,i*45f,.7f) {leaf(l.leaf,65f)}
            return
        }
        val twigBrush=BotanyInk(c)
        val wood=Path().apply { moveTo(-2.5f,5f);quadTo(2f,-95f,-.8f,-175f);lineTo(.7f,-175f);quadTo(4f,-95f,2.2f,5f);close() }
        twigBrush.wash(wood,-2f,0f,3f,0f,intArrayOf(ColorUtils.blendARGB(l.bark,0xffead8ad.toInt(),.4f),l.bark,ink))
        if(l.leaf==LeafShape.NEEDLE) {
            if(l.needleCount>2) for(y in listOf(-25f,-80f,-135f)) needleBundle(0f,y,28f,l.needleCount)
            else for(i in 0..11) for(side in listOf(-1,1)) {
                val y=-i*13f
                if(l.needleCount==2) needleBundle(0f,y,42f,2,side*65f)
                else {
                    val peg=if(species.id=="picea") 3f else 1f
                    line(0f,y,side*peg,y-2f,l.bark,1.4f)
                    at(side*peg,y-2f,side*(if(species.id=="abies") 73f else l.leafDetail.needleAngle)) { leafPainter.needles(35f,1) }
                    if(species.id=="picea" && i%2==0) at(0f,y,side*145f) {leafPainter.needles(20f,1)}
                }
            }
            return
        }
        for(i in 0..3) {
            val yy=-25f-i*37f
            val both=l.arrangement==LeafArrangement.OPPOSITE || l.arrangement==LeafArrangement.WHORLED
            val sides=if(both) listOf(-1,1) else listOf(if(i%2==0) -1 else 1)
            for(side in sides) at(0f,yy,side*65f,.31f) { leaf(l.leaf,100f) }
            if(l.arrangement==LeafArrangement.WHORLED) at(0f,yy,0f,.24f) { leaf(l.leaf,80f) }
            if(l.thorny) line(0f,yy+8,-10f,yy-3,l.bark,2f)
            for(side in sides) bud(side*2f,yy-4f,side*24f)
        }
        bud(0f,-173f,0f)
        if(l.leaf==LeafShape.OAK && species.sectionIds.contains("trees")) {
            bud(-3f,-168f,-27f);bud(3f,-168f,27f)
        }
    }

    private fun bud(x:Float,y:Float,angle:Float) = at(x,y,angle) {
        val h=if(species.id=="fagus") 15f else 8f
        val color=if(species.id=="fraxinus") 0xff302c30.toInt() else 0xff936c43.toInt()
        val outline=Path().apply {moveTo(0f,0f);quadTo(-5f,-h*.45f,0f,-h);quadTo(5f,-h*.45f,0f,0f);close()}
        path(outline,color,0xff584936.toInt(),.5f)
        for(i in 1..3)line(-1.8f,-h*i/5f,1.8f,-h*(i+.7f)/5f,0xffbaa477.toInt(),.4f)
    }

    private val leafPainter = PlantLeafPainter(c,species,!compact)

    private fun needleBundle(x:Float,y:Float,length:Float,count:Int,angle:Float=0f) = at(x,y,angle) {
        leafPainter.needles(length,count)
    }

    private fun leaf(shape:LeafShape,length:Float,margin:LeafMargin=l.margin,detail:LeafDetail?=null) = leafPainter.draw(shape,length,margin,detail)

    private val flowerPainter = PlantFlowerPainter(c,species,!compact)
    private fun flowers() = flowerPainter.plate()
    private fun flowerHead() = flowerPainter.head()
    private fun blossom(shape:FlowerShape) = flowerPainter.blossom(shape)

    private fun grass(x: Float,y: Float,size: Float) = at(x,y,0f,size) {
        repeat(5) {i ->
            val xx=(i-2)*21f;val yy=-170f-(i%3)*23f
            line(0f,0f,xx,yy,green,1.5f)
            val blade=Path().apply{moveTo(0f,0f);quadTo(xx*2-40,-100f,xx*3-40,-150f);quadTo(xx*2-23,-75f,4f,0f);close()};path(blade,green,null)
            if(l.flowerDetail.cluster in setOf(FlowerCluster.BRISTLY_SPIKE,FlowerCluster.PAIRED_RACEMES)) {
                at(xx,yy+19,0f,.37f){flowerHead()}
            } else if(species.id=="lolium") repeat(12){j->val sy=yy+j*7;val side=if(j%2==0) -1 else 1;line(xx,sy,xx+side*11,sy-7,green,.8f);spikelet(xx+side*8,sy-4,4f,10f)}
            else repeat(9){j->val sy=yy+j*9;val side=if(j%2==0) -1 else 1;val reach=10f+j*2
                line(xx,sy+20,xx+side*reach,sy,green,.7f)
                if(species.id=="briza"){line(xx+side*reach,sy,xx+side*(reach+5),sy+12,green,.6f);spikelet(xx+side*(reach+5),sy+19,5f,7f)}
                else repeat(4){k->spikelet(xx+side*(reach+k*3),sy+k*3,3f,6f)}
            }
        }
    }
    private fun spikelet(x:Float,y:Float,w:Float,h:Float){
        oval(x,y,w,h,ColorUtils.blendARGB(green,0xffba9d9c.toInt(),.35f),ink)
        repeat(4){j->line(x-w*.7f,y-h+j*h*.5f,x+w*.7f,y-h*.7f+j*h*.5f,0xffd4c69e.toInt(),.6f)}
    }

    private fun fruits() = PlantFruitPainter(c,species,!compact).plate()
}
