package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.math.*
import kotlin.random.Random

/** Blade, petiole and venation share one contour, including at the smaller twig scale. */
internal class PlantLeafPainter(private val canvas: Canvas, private val species: PlantSpecies,private val detailed:Boolean=true) {
    private val look = species.look
    private val brush = BotanyInk(canvas)
    private val green = look.leafColor
    private val edge = botanicalMix(green,0xff263d2e.toInt(),.65f)
    private val light = botanicalMix(green,0xffd9d39a.toInt(),.48f)

    fun draw(shape: LeafShape, length: Float, margin: LeafMargin = look.margin, detail:LeafDetail?=null) {
        when (shape) {
            LeafShape.SCALE -> if(look.leafDetail.awlShoot) PlantConiferPainter(canvas,look,detailed).awlShoot(length)
                else simple(shape,length,margin,detail ?: look.leafDetail)
            LeafShape.NEEDLE -> needles(length)
            LeafShape.PINNATE, LeafShape.PALMATE, LeafShape.TRIFOLIATE, LeafShape.BIFOLIATE -> compound(shape,length,margin,detail ?: look.leafDetail)
            LeafShape.BIPINNATE -> bipinnate(length,detail ?: look.leafDetail)
            LeafShape.DIVIDED -> divided(length)
            else -> simple(shape,length,margin,detail ?: if(shape==look.leaf)look.leafDetail else LeafDetail())
        }
    }

    fun simple(shape: LeafShape, length: Float, margin: LeafMargin,
               detail: LeafDetail = if(shape == look.leaf) look.leafDetail else LeafDetail(),
               petiole: Float = look.petiole) {
        val stalk = length * petiole.coerceIn(.015f,.5f)
        val h = length-stalk
        val stalkPath = Path().apply { moveTo(0f,0f); quadTo(-h*.018f,-stalk*.55f,0f,-stalk) }
        brush.stroke(stalkPath,edge,1.7f)
        brush.stroke(stalkPath,light,.65f)
        if((look.leafDetail.petioleGlands || species.id=="prunus_avium") && shape==look.leaf) {
            for(side in listOf(-1,1)) brush.oval(side*1.6f,-stalk*.84f,1.5f,1.9f,0xff975342.toInt())
        }
        brush.at(0f,-stalk) {
            val outline = when(shape) {
                LeafShape.MAPLE -> maple(h,detail,margin)
                LeafShape.PALMATILOBED -> palmatilobed(h,detail,margin)
                LeafShape.OAK -> oak(h,detail)
                LeafShape.HEART -> heart(h,detail,margin)
                LeafShape.OVAL, LeafShape.ROUND -> if(detail.base==LeafBase.CORDATE) heart(h,detail,margin) else blade(shape,h,margin,detail)
                else -> blade(shape,h,margin,detail)
            }
            brush.wash(outline,-h*.45f,-h*.6f,h*.42f,-h*.25f,intArrayOf(
                botanicalMix(green,0xffc1c47d.toInt(),.28f),green,botanicalMix(green,0xff243f31.toInt(),.30f)))
            canvas.save(); canvas.clipPath(outline)
            // A restrained wash and tiny broken pen marks, rather than a flat plastic surface.
            val random = Random(look.seed*113+shape.ordinal)
            repeat(if(detailed) 115 else 0) {
                val x=(random.nextFloat()-.5f)*h*1.3f; val y=-random.nextFloat()*h
                brush.line(x,y,x+1.1f,y-2.4f,0x1862753e,.35f)
            }
            if(detail.glossy) {
                val shine=Path().apply { moveTo(-h*.035f,-h*.1f); quadTo(-h*.31f,-h*.43f,-h*.045f,-h*.89f)
                    quadTo(-h*.17f,-h*.43f,-h*.035f,-h*.1f) }
                brush.fill(shine,0x28e0e4b2)
            }
            veins(shape,h,margin,detail)
            canvas.restore()
            brush.stroke(outline,edge,.68f)
            brush.stroke(outline,0x35798554,.25f)
            if(detail.ciliate) {
                // Attach hairs to the actual contour, including the sinuses of lobed blades.
                val contour=PathMeasure(outline,true);val point=FloatArray(2)
                repeat(if(detailed)64 else 24){i->
                    contour.getPosTan(contour.length*(i+.5f)/(if(detailed)64 else 24),point,null)
                    val dx=point[0];val dy=point[1]+h*.5f;val norm=hypot(dx,dy).coerceAtLeast(1f)
                    brush.line(point[0],point[1],point[0]+dx/norm*1.7f,point[1]+dy/norm*1.7f,0xffb3b38b.toInt(),.42f)
                }
            }
        }
    }

    private fun halfWidth(shape: LeafShape, t: Float, h: Float, d: LeafDetail, margin: LeafMargin): Float {
        val q=t.coerceIn(.00001f,.99999f)
        val width=d.width ?: when(shape) {
            LeafShape.LANCE -> .19f; LeafShape.LINEAR -> .035f; LeafShape.SCALE -> .23f
            LeafShape.ROUND -> .47f; LeafShape.HEART -> .47f; LeafShape.TRIANGLE -> .42f
            LeafShape.OAK -> .29f; else -> .32f
        }
        var envelope=when(shape) {
            LeafShape.TRIANGLE -> if(q<.18f) (q/.18f).pow(.58f) else ((1-q)/.82f).pow(1.12f)
            LeafShape.HEART -> sin(PI*q).toFloat().pow(.55f)*(1.25f-.56f*q)
            LeafShape.ROUND -> sin(PI*q).toFloat().pow(.57f)
            LeafShape.OAK -> sin(PI*(.12f+.83f*q)).toFloat().pow(.7f)*(.83f+.17f*q)
            else -> sin(PI*q).toFloat().pow(if(d.base==LeafBase.ROUNDED) .68f else .92f)
        }
        if(d.leafletWidestAboveMiddle) envelope=sin(PI*q.pow(1.5f)).toFloat().pow(.82f)
        if(d.tip==LeafTip.ACUMINATE && q>.72f) envelope*=1f-.45f*(q-.72f)/.28f
        if(d.tip==LeafTip.ROUNDED && shape!=LeafShape.OAK) envelope=sin(PI*q).toFloat().pow(.52f)
        if(d.tip==LeafTip.NOTCHED) envelope=sin(PI*q*.91f).toFloat().pow(.68f)*(1f+.15f*q)
        if(shape==LeafShape.OAK) {
            envelope*=1f-d.lobeDepth*.5f+d.lobeDepth*.5f*cos(2*PI*(q*d.lobePairs-.35)).toFloat()
            if(q>.91f) envelope*=sqrt(((1f-q)/.09f).coerceAtLeast(0f))
            if(q<.12f) envelope*=if(d.base==LeafBase.AURICLED) .5f+.5f*sin(PI*q/.12).toFloat() else q/.12f
        }
        var w=h*width*envelope
        val teeth = when {
            d.teeth==LeafTeeth.WAVY -> .018f*sin(q*d.veinPairs*2*PI).toFloat()
            d.teeth==LeafTeeth.CRENATE -> .035f*sin(q*11*2*PI).toFloat()
            margin==LeafMargin.SERRATE -> {
                val phase=(q*d.veinPairs*2)%1f
                val saw=if(phase<.72f) phase/.72f else (1f-phase)/.28f
                val secondary=if(d.teeth==LeafTeeth.DOUBLE) .007f*sin(q*d.veinPairs*6*PI).toFloat() else 0f
                .020f*(saw-.45f)+secondary
            }
            margin==LeafMargin.SPINY -> -.10f+.16f*max(0f,cos(q*12*PI).toFloat()).pow(5)
            else -> 0f
        }
        w+=h*teeth*sin(PI*q).toFloat()
        return w
    }

    private fun blade(shape: LeafShape,h: Float,margin: LeafMargin,d: LeafDetail): Path {
        val path=Path(); path.moveTo(0f,0f)
        for(side in listOf(-1,1)) for(i in 0..200) {
            val t=if(side<0) i/200f else 1-i/200f
            var w=halfWidth(shape,t,h,d,margin)
            var y=-h*t
            if(shape==LeafShape.HEART || d.base==LeafBase.CORDATE) {
                y+=h*.20f*sin(2*PI*t).toFloat()*max(0f,1-t*1.35f)
            }
            if(shape==LeafShape.OAK) {
                y+=h*.026f*sin(2*PI*(t*d.lobePairs-.35)).toFloat()*sin(PI*t).toFloat()
                if(d.base==LeafBase.AURICLED && t<.13f) y+=h*.07f*sin(PI*t/.13).toFloat()
            }
            if(d.tip==LeafTip.NOTCHED && t>.93f) {
                w*=sqrt(((1f-t)/.07f).coerceAtLeast(0f))
                y+=h*.055f*((t-.93f)/.07f).pow(3)
            }
            val asym=1+side*d.asymmetry*sin(PI*t).toFloat()
            val bend=h*.019f*sin(PI*t).toFloat()
            path.lineTo(side*w*asym+bend,y+side*d.asymmetry*h*.18f*sin(PI*t).toFloat()+
                if(side>0) d.basalOffset*h*sin(PI*t).toFloat()*(1-t) else 0f)
        }
        path.close(); return path
    }

    private fun palmatilobed(h:Float,d:LeafDetail,margin:LeafMargin):Path {
        val n=d.palmateSegments
        return Path().apply {
            moveTo(0f,0f)
            for(i in 0..240) {
                val t=i/240f;val a=PI*(.07+1.86*t)
                val lobe=(.5f-.5f*cos(t*n*2*PI).toFloat())
                val secondary=if(d.subdividedLobes) 1f-.27f*lobe*(.5f+.5f*cos(t*n*12*PI).toFloat()) else 1f
                val r=h*.47f*(1-d.lobeDepth+d.lobeDepth*lobe)*secondary
                val tooth=if(margin==LeafMargin.SERRATE) 1f+.018f*sin(t*70*PI).toFloat() else 1f
                lineTo(sin(a).toFloat()*r*tooth,-h*.46f+cos(a).toFloat()*r*tooth)
            }
            close()
        }
    }

    private fun oak(h:Float,d:LeafDetail):Path {
        if(!d.roundedLobes) return pinnatifid(h,d)
        val half=Path().apply {
            moveTo(0f,0f)
            if(d.base==LeafBase.AURICLED) {
                cubicTo(-h*.06f,h*.005f,-h*.17f,h*.055f,-h*.16f,-h*.012f)
                quadTo(-h*.15f,-h*.043f,-h*.08f,-h*.06f)
            } else quadTo(-h*.045f,-h*.035f,-h*.10f,-h*.09f)
            val step=.79f/d.lobePairs
            for(i in 0 until d.lobePairs) {
                val t=.08f+i*step
                val w=(d.width ?: .29f)*(.56f+.44f*sin(PI*(i+.8f)/(d.lobePairs+.5f)).toFloat())
                val sinus=w*(1-d.lobeDepth-.16f)
                // Each lobe is a rounded projection followed by a rounded sinus.
                cubicTo(-h*w*.81f,-h*(t-.018f),-h*w*1.20f,-h*(t+.004f),-h*w,-h*(t+step*.46f))
                cubicTo(-h*w*.91f,-h*(t+step*.76f),-h*sinus,-h*(t+step*.63f),-h*sinus,-h*(t+step*.91f))
            }
            cubicTo(-h*.13f,-h*.91f,-h*.11f,-h,h*.005f,-h)
        }
        val measure=PathMeasure(half,false);val xy=FloatArray(2);val result=Path()
        for(side in 0..1)for(i in 0..220) {
            val t=if(side==0) i/220f else 1-i/220f
            measure.getPosTan(measure.length*t,xy,null)
            val x=if(side==0)xy[0] else -xy[0]*(1+d.asymmetry*sin(PI*t).toFloat())
            val y=xy[1]+if(side==1)h*d.asymmetry*.35f*sin(PI*t).toFloat() else 0f
            if(side==0 && i==0)result.moveTo(x,y) else result.lineTo(x,y)
        }
        result.close();return result
    }

    private fun pinnatifid(h:Float,d:LeafDetail):Path {
        val half=mutableListOf(0f to 0f,-.04f to -.06f)
        for(i in 0 until d.lobePairs) {
            val step=.82f/d.lobePairs;val t=.07f+i*step
            val w=(d.width ?: .3f)*(.55f+.45f*sin(PI*(i+.7f)/(d.lobePairs+.2f)).toFloat())
            half+=(-w*.72f) to -(t+.025f)
            if(d.bristleTips)half+=(-w-.018f) to -(t+step*.36f+.012f)
            half+=(-w) to -(t+step*.36f)
            half+=(-w*.76f) to -(t+step*.39f)
            half+=(-w*.85f) to -(t+step*.61f)
            half+=(-w*(1-d.lobeDepth)) to -(t+step*.84f)
        }
        half+=(-.08f to -.91f);half+=(0f to -1f)
        val points=half+half.dropLast(1).asReversed().map {(-it.first*(1+d.asymmetry)) to (it.second+.018f*sin(-PI*it.second).toFloat())}
        return Path().apply {
            for((i,point) in points.withIndex())if(i==0)moveTo(point.first*h,point.second*h) else lineTo(point.first*h,point.second*h)
            close()
        }
    }

    private fun heart(h:Float,d:LeafDetail,margin:LeafMargin):Path {
        val base=Path().apply {
            moveTo(0f,0f)
            cubicTo(-h*.12f,h*.13f,-h*.43f,h*.10f,-h*.47f,-h*.17f)
            cubicTo(-h*.57f,-h*.48f,-h*.18f,-h*.70f,0f,-h)
            cubicTo(h*.16f,-h*.72f,h*.57f,-h*.53f,h*.46f,-h*.18f)
            cubicTo(h*.43f,h*.065f,h*.13f,h*.105f,0f,0f);close()
        }
        val measure=PathMeasure(base,true);val xy=FloatArray(2);val result=Path()
        for(i in 0..300) {
            val t=i/300f;measure.getPosTan(measure.length*t,xy,null)
            val teeth=if(margin==LeafMargin.SERRATE) (if(i%3==0) .007f else -.003f)*h else 0f
            val rad=hypot(xy[0],xy[1]+h*.4f).coerceAtLeast(1f)
            val x=(xy[0]*(if(xy[0]>0) 1+d.asymmetry else 1f)+teeth*xy[0]/rad)*(d.width ?: .47f)/.47f
            val y=xy[1]+teeth*(xy[1]+h*.4f)/rad+if(xy[0]>0) h*d.asymmetry*.25f else 0f
            if(i==0)result.moveTo(x,y) else result.lineTo(x,y)
        }
        result.close();return result
    }

    private fun maple(h: Float,d: LeafDetail,margin: LeafMargin): Path {
        // Five (or three) lobes with actual sinuses and secondary teeth; not a regular radial star.
        val half=if(d.lobePairs==1) listOf(
            0f to 0f,-.29f to -.04f,-.53f to -.32f,-.58f to -.65f,-.35f to -.53f,
            -.22f to -.42f,-.12f to -.73f,0f to -1f)
        else if(d.roundedLobes) listOf(
            0f to 0f,-.22f to .015f,-.44f to -.10f,-.48f to -.28f,-.27f to -.29f,
            -.38f to -.46f,-.61f to -.47f,-.60f to -.67f,-.43f to -.64f,
            -.40f to -.81f,-.25f to -.73f,-.14f to -.47f,-.14f to -.79f,
            -.19f to -.88f,-.08f to -.89f,0f to -1f)
        else listOf(
            0f to 0f,-.22f to .018f,-.42f to -.09f,-.51f to -.29f,-.30f to -.25f,
            -.36f to -.40f,-.56f to -.43f,-.52f to -.52f,-.67f to -.69f,
            -.48f to -.61f,-.40f to -.80f,-.28f to -.67f,-.16f to -(1-d.lobeDepth),
            -.11f to -.74f,-.18f to -.80f,-.075f to -.82f,0f to -1f)
        val points=half+half.dropLast(1).drop(1).asReversed().map{(-it.first*.96f) to (it.second*1.02f)}
        val refined=mutableListOf<Pair<Float,Float>>()
        for(i in points.indices) {
            val a=points[i];val b=points[(i+1)%points.size]
            refined+=a
            if(margin==LeafMargin.SERRATE) for(k in 1..4) {
                val t=k/5f; val bulge=if(k%2==0) 1.025f else .987f
                refined+=((a.first+(b.first-a.first)*t)*bulge) to ((a.second+(b.second-a.second)*t)*bulge)
            }
        }
        val path=Path()
        for(i in refined.indices) {
            val prev=refined[(i+refined.size-1)%refined.size]; val p=refined[i]; val next=refined[(i+1)%refined.size]
            val cross=(p.first-prev.first)*(next.second-p.second)-(p.second-prev.second)*(next.first-p.first)
            val round=if(d.tip==LeafTip.ACUMINATE && cross>0f).035f else if(d.roundedLobes).32f else .055f
            val x=(p.first*(1-round)+prev.first*round)*h; val y=(p.second*(1-round)+prev.second*round)*h
            if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
            path.quadTo(p.first*h,p.second*h,(p.first*(1-round)+next.first*round)*h,(p.second*(1-round)+next.second*round)*h)
        }
        path.close(); return path
    }

    private fun veins(shape: LeafShape,h: Float,margin: LeafMargin,d: LeafDetail) {
        fun rib(x:Float,y:Float,xx:Float,yy:Float,curve:Float=0f,width:Float=.65f) {
            val rib=Path().apply { moveTo(x,y); quadTo((x+xx)*.5f+curve,y+(yy-y)*.33f,xx,yy) }
            brush.stroke(rib,0x2e263e28,width*2.8f)
            brush.stroke(rib,light,width)
            // Tertiary veins join the lateral ribs as a loose reticulum.
            for(j in 2..5) {
                val t=j/7f; val px=x+(xx-x)*t; val py=y+(yy-y)*t
                val dx=(xx-x)*.16f; val dy=h*.034f
                brush.stroke(Path().apply { moveTo(px,py); quadTo(px+dx*.6f,py-dy,px+dx,py-dy*1.55f) },0x637f995c,.32f)
            }
        }
        when {
            d.venation==Venation.THREE_BASAL -> for(side in listOf(-1,1)) {
                val w=(d.width ?: .32f)*.88f
                brush.stroke(Path().apply { moveTo(0f,0f);cubicTo(side*h*w,-h*.2f,side*h*w,-h*.65f,0f,-h*.94f) },light,1.25f)
                for(i in 1..7)rib(0f,-h*i/9,side*h*.22f*sin(PI*i/9).toFloat(),-h*(i+.45f)/9,width=.45f)
            }
            shape==LeafShape.OAK -> for(i in 0 until d.lobePairs) {
                val step=.79f/d.lobePairs
                val t=.08f+i*step+step*.46f
                val w=(d.width ?: .29f)*(.56f+.44f*sin(PI*(i+.8f)/(d.lobePairs+.5f)).toFloat())
                for(side in listOf(-1,1))rib(0f,-h*(t-.085f),side*h*w*.96f,-h*t,side*h*.015f)
            }
            d.venation==Venation.PARALLEL -> for(side in listOf(-1,1)) for(i in 1..3) {
                brush.stroke(Path().apply { moveTo(0f,0f); cubicTo(side*h*.16f*i,-h*.28f,side*h*.13f*i,-h*.68f,0f,-h) },light,.68f)
            }
            shape==LeafShape.PALMATILOBED -> {
                repeat(d.palmateSegments){i->
                    val a=PI*(.07+1.86*(i+.5)/d.palmateSegments)
                    rib(0f,0f,sin(a).toFloat()*h*.45f,-h*.46f+cos(a).toFloat()*h*.45f,width=.9f)
                }
            }
            shape==LeafShape.MAPLE || d.venation==Venation.PALMATE -> {
                val tips=if(d.lobePairs==1) listOf(-.57f to -.65f,0f to -1f,.55f to -.66f)
                    else listOf(-.48f to -.28f,-.62f to -.68f,0f to -1f,.59f to -.69f,.46f to -.28f)
                for((x,y) in tips) {
                    rib(0f,0f,x*h,y*h,width=1f)
                    for(j in 2..6) {
                        val t=j/8f; val xx=x*h*t; val yy=y*h*t
                        for(side in listOf(-1,1)) rib(xx,yy,xx+side*h*.13f,yy-h*.17f,width=.45f)
                    }
                }
            }
            else -> for(i in 0 until d.veinPairs) {
                val t=.10f+i*.76f/d.veinPairs
                for(side in listOf(-1,1)) {
                    val endT=(t+.12f).coerceAtMost(.97f)
                    val w=halfWidth(shape,endT,h,d,margin)*.96f
                    if(d.venation==Venation.ARCUATE) {
                        brush.stroke(Path().apply { moveTo(0f,-h*t); cubicTo(side*w,-h*(t+.04f),side*w*1.1f,-h*(t+.23f),side*w*.62f,-h*(t+.32f)) },light,.75f)
                    } else rib(h*.012f,-h*t,side*w,-h*endT,side*h*.025f)
                }
            }
        }
        if(shape==LeafShape.HEART || d.base==LeafBase.CORDATE) for(side in listOf(-1,1)) {
            brush.stroke(Path().apply { moveTo(0f,0f);cubicTo(side*h*.15f,-h*.025f,side*h*.34f,-h*.12f,side*h*.41f,-h*.32f) },light,.8f)
            rib(0f,0f,side*h*.32f,-h*.075f,side*h*.05f,.55f)
        }
        val midrib=Path().apply { moveTo(-.65f,0f); quadTo(h*.022f,-h*.45f,0f,-h*.988f)
            quadTo(h*.004f,-h*.45f,1.1f,0f); close() }
        brush.fill(midrib,light)
    }

    private fun compound(shape: LeafShape,length: Float,margin: LeafMargin,d:LeafDetail) {
        val leaflets=d.copy(width=d.leafletWidth,base=LeafBase.TAPERED,venation=Venation.PINNATE,
            tip=d.leafletTip,asymmetry=.045f)
        if(shape==LeafShape.PINNATE) {
            val terminal=look.leaflets%2==1; val pairs=look.leaflets/2
            val end=if(terminal) .76f else if(d.terminalTendril) .93f else if(d.leafletAngle!=null) .87f else .72f
            brush.line(0f,0f,0f,-length*end,edge,1.8f)
            brush.line(-.4f,0f,-.4f,-length*end,light,.65f)
            repeat(pairs) { i ->
                val t=if(pairs==1) .45f else i/(pairs-1f)
                val y=-length*(.23f+t*if(d.leafletAngle!=null).62f else .48f)
                val leafletLength=length*(.28f-.055f*t)*(if(pairs>5) .8f else 1f)
                for(side in listOf(-1,1)) brush.at(0f,y+side*length*.006f,side*(d.leafletAngle ?: (62f-13*t))) {
                    simple(d.leafletShape,leafletLength,margin,leaflets,d.leafletPetiole)
                }
            }
            if(terminal)brush.at(0f,-length*.72f) { simple(d.leafletShape,length*.28f,margin,leaflets,d.leafletPetiole) }
            if(d.terminalTendril)brush.stroke(Path().apply { moveTo(0f,-length*.91f); cubicTo(19f,-length*1.04f,22f,-length*.88f,8f,-length*.95f) },green,1.1f)
        } else {
            val n=if(shape==LeafShape.TRIFOLIATE) 3 else if(shape==LeafShape.BIFOLIATE) 2 else look.leaflets
            val junction=-length*.29f
            brush.line(0f,0f,0f,junction,edge,2f);brush.line(-.5f,0f,-.5f,junction,light,.65f)
            repeat(n) { i ->
                val relative=i-(n-1)/2f; val angle=if(d.radialLeaflets)relative*300f/(n-1) else relative*if(n==2) 72f else if(n==3) 53f else 28f
                val size=if(d.radialLeaflets)length*.45f else length*(.71f-abs(relative)*if(n==3) .13f else .08f)
                brush.at(0f,junction,angle) {
                    val dl=if(n==3)leaflets.copy(width=.36f,tip=LeafTip.ROUNDED) else leaflets
                    // Horse chestnut leaflets broaden towards their distal half, tapering to the junction.
                    if(d.leafletWidestAboveMiddle) {
                        simple(LeafShape.OVAL,size,margin,dl,.025f)
                    } else simple(if(n==3 || n==2)LeafShape.OVAL else d.leafletShape,size,margin,dl,d.leafletPetiole)
                    if(species.id.startsWith("trifolium")) {
                        brush.stroke(Path().apply { moveTo(-size*.15f,-size*.43f); quadTo(-size*.06f,-size*.53f,0f,-size*.58f)
                            quadTo(size*.08f,-size*.53f,size*.15f,-size*.43f) },0xffb6c696.toInt(),size*.035f)
                    }
                }
            }
        }
    }

    private fun bipinnate(length:Float,d:LeafDetail) {
        brush.line(0f,0f,0f,-length*.91f,edge,1.7f)
        brush.line(-.5f,0f,-.5f,-length*.91f,light,.65f)
        repeat(d.pinnaPairs){i->val t=i/(d.pinnaPairs-1f).coerceAtLeast(1f)
            val y=-length*(.22f+t*.64f)
            for(side in listOf(-1,1))brush.at(0f,y,side*63f) {
                val reach=length*(.39f-.12f*t)
                brush.line(0f,0f,0f,-reach,edge,.7f)
                repeat(d.pinnulePairs){j->val u=(j+1f)/(d.pinnulePairs+1)
                    for(s in listOf(-1,1))brush.at(0f,-reach*u,s*77f) {
                        simple(LeafShape.OVAL,reach*.22f,LeafMargin.ENTIRE,
                            LeafDetail(width=.19f,tip=LeafTip.ROUNDED,veinPairs=3),.04f)
                    }
                }
            }
        }
    }

    private fun divided(length:Float) {
        if(look.leafDetail.finelyDivided) {
            brush.line(0f,0f,0f,-length*.91f,green,1.5f)
            for(i in 1..5)for(side in listOf(-1,1))brush.at(0f,-length*i*.15f,side*54f){
                val reach=length*(.39f-i*.035f)
                brush.line(0f,0f,0f,-reach,green,1.3f)
                for(j in 1..3)for(s in listOf(-1,1))brush.at(0f,-reach*j*.23f,s*40f){
                    brush.line(0f,0f,0f,-reach*.40f,green,1.1f)
                    for(k in -1..1)brush.at(0f,-reach*.25f,k*35f){simple(LeafShape.LINEAR,reach*.33f,LeafMargin.ENTIRE,LeafDetail(width=.09f,tip=LeafTip.ROUNDED),.02f)}
                }
            };return
        }
        if(species.id=="papaver") {
            simple(LeafShape.OAK,length,LeafMargin.LOBED,LeafDetail(width=.24f,lobePairs=5,lobeDepth=.67f,roundedLobes=false),.12f)
            return
        }
        brush.line(0f,0f,0f,-length,green,1.5f)
        val triangular=species.id=="daucus" || species.id=="anthriscus"
        for(i in 1..9) {
            val t=i/10f; val y=-length*t
            val reach=length*if(triangular) .39f*(1-t) else .19f*sin(PI*t).toFloat()
            for(side in listOf(-1,1)) {
                brush.line(0f,y,side*reach,y-length*.08f,green,.9f)
                for(j in 1..6) {
                    val u=j/7f; val x=side*reach*u;val yy=y-length*.08f*u
                    for(s in listOf(-1,1))brush.at(x,yy,side*(if(s<0) 40f else 125f)) {
                        simple(LeafShape.LANCE,length*(if(species.id=="anthriscus") .08f else .055f)*(1-u*.45f),
                            LeafMargin.ENTIRE,LeafDetail(width=.16f,veinPairs=3),.035f)
                    }
                }
            }
        }
    }

    fun needles(length:Float,count:Int=look.needleCount) {
        val n=if(count>2) 22 else count
        repeat(n) { i ->
            val angle=if(n==2) (i-.5f)*13f else if(n==1) 0f else -61f+122f*i/(n-1)
            brush.at(0f,0f,angle) {
                val h=length*(if(n>2) .77f+.23f*sin(PI*(i+1)/(n+1)).toFloat() else 1f)
                val w=length*if(n==1) { look.leafDetail.needleWidth ?: if(look.leafDetail.tip==LeafTip.NOTCHED) .032f else .016f } else .0065f
                val flat=look.leafDetail.tip==LeafTip.NOTCHED
                val outline=Path().apply { moveTo(-.7f,0f); cubicTo(-w,-h*.25f,-w,-h*.85f,-w*.8f,-h)
                    if(flat) { quadTo(-w*.2f,-h+2f,0f,-h+1.5f);quadTo(w*.2f,-h+2f,w*.8f,-h) }
                    else lineTo(0f,-h)
                    cubicTo(w,-h*.85f,w,-h*.25f,.7f,0f);close() }
                brush.wash(outline,-w,0f,w,0f,intArrayOf(light,green,edge));brush.stroke(outline,edge,.35f)
                brush.line(0f,-3f,0f,-h+3f,0x8395ae79.toInt(),.5f)
                if(look.leafDetail.stomatalBands)for(side in listOf(-1,1))brush.line(side*w*.45f,-h*.18f,side*w*.45f,-h*.88f,0xffced9b7.toInt(),w*.26f)
            }
        }
        if(count==2) {
            brush.stroke(Path().apply { moveTo(-1.8f,-7f); lineTo(-1.4f,0f);lineTo(1.4f,0f);lineTo(1.8f,-7f) },0xff927951.toInt(),1.5f)
        } else if(count>2) brush.oval(0f,0f,3f,2f,0xff99724a.toInt())
    }
}
