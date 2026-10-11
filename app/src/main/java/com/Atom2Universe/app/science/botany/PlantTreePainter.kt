package com.Atom2Universe.app.science.botany

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.*
import kotlin.random.Random

/** A branching skeleton carries layered foliage. No photo assets or per-frame random geometry. */
internal class PlantTreePainter(private val canvas:Canvas,private val species:PlantSpecies,private val detailed:Boolean=true) {
    private val look=species.look
    private val brush=BotanyInk(canvas)
    private val random=Random(look.seed)
    private val shrub=look.crown==Crown.SHRUB
    private val pine=look.crown==Crown.PINE
    private val form=look.architecture ?: TreeArchitecture(
        radius=if(look.crown==Crown.COLUMN) 68f else 92f,
        top=if(shrub) 72f else 38f,bottom=if(shrub) 251f else 205f,
        fork=if(shrub) 267f else 195f,trunkWidth=if(shrub) 6f else 16f)
    private val bark=look.bark
    private val darkBark=botanicalMix(bark,0xff302c24.toInt(),.63f)
    private val green=look.leafColor
    private data class Bough(val x:Float,val y:Float,val cx:Float,val cy:Float,val xx:Float,val yy:Float,val width:Float)
    private data class Foliage(val x:Float,val y:Float,val rx:Float,val ry:Float,val seed:Int)
    private val branches=mutableListOf<Bough>()
    private val foliage=mutableListOf<Foliage>()

    fun draw() {
        if(form.bottleTrunk)bottleHabit() else if(form.roundedConifer) matureConifer() else if(look.crown==Crown.CONICAL) conifer() else broadleaf()
        // Root collar and a broken ground line finish the plate like an ink herbarium drawing.
        brush.stroke(Path().apply { moveTo(118f,282f); quadTo(147f,278f,179f,282f) },0x696a634a,.7f)
        repeat(12) { i ->
            val x=112f+i*7f+random.nextFloat()*3f
            brush.line(x,284f,x+4f,283f,0x426b7552,.5f)
        }
    }

    private fun axis(y:Float)=150f+form.crooked*sin((279f-y)/92f)*sin((279f-y)/210f)

    private fun broadleaf() {
        val middle=(form.top+form.bottom)*.5f
        val height=(form.bottom-form.top)*.5f
        // Different fork heights, reach, crown depth and branch curvature belong to the catalogue.
        val count=if(shrub) 9 else if(pine) 7 else 8
        for(i in 0 until count) {
            val a=PI*(.07f+.86f*i/(count-1))
            val side=if(cos(a)<0) -1 else 1
            val x=150f+cos(a).toFloat()*form.radius*(.78f+random.nextFloat()*.13f)
            val y=if(form.umbrella) middle-abs(sin(a).toFloat())*height*.25f+(random.nextFloat()-.5f)*9f
                else middle-sin(a).toFloat()*height*.88f+(random.nextFloat()-.5f)*15f
            val startY=if(shrub) 277f else form.fork-abs(sin(a).toFloat())*(if(pine) 18f else 29f)+random.nextFloat()*22f
            val startX=if(shrub) 146f+random.nextFloat()*9 else axis(startY)
            val cx=startX+(x-startX)*.42f+side*form.crooked*.4f
            val cy=if(pine) y+35f else startY+(y-startY)*.26f
            branches+=Bough(startX,startY,cx,cy,x,y,if(shrub) 3.4f else form.trunkWidth*(.29f+random.nextFloat()*.12f))
            for(j in 0..3) {
                val t=.38f+j*.18f
                val bx=(1-t).pow(2)*startX+2*(1-t)*t*cx+t*t*x
                val by=(1-t).pow(2)*startY+2*(1-t)*t*cy+t*t*y
                val tx=(bx+side*(12f+random.nextFloat()*20f)).coerceIn(150f-form.radius+10f,150f+form.radius-10f)
                val ty=(by-18f-random.nextFloat()*22f).coerceAtLeast(form.top+12f)
                branches+=Bough(bx,by,bx+(tx-bx)*.7f,by-9f,tx,ty,if(shrub) 1.5f else 2.5f)
                val radius=if(pine) 15f+random.nextFloat()*9f else 16f+random.nextFloat()*12f
                foliage+=Foliage(tx,ty,radius,if(pine) radius*.58f else radius*.84f,random.nextInt())
            }
            foliage+=Foliage(x,y,if(pine) 25f else 24f,if(pine) 15f else 22f,random.nextInt())
        }
        // Lower shoulders of the crown must exist as branches, not disconnected decorative blobs.
        if(!pine && !form.umbrella)for(side in listOf(-1,1))for(i in 0..1) {
            val y=middle+height*(.34f+i*.31f)
            val reach=form.radius*sqrt((1-((y-middle)/height).pow(2)).coerceAtLeast(0f))*.76f
            val x=150f+side*reach
            val sy=if(shrub) 273f else (y+28f).coerceAtMost(form.fork+12)
            branches+=Bough(axis(sy),sy,150f+side*reach*.64f,y+15f,x,y,if(shrub) 2f else 3.2f)
            foliage+=Foliage(x,y,23f,20f,random.nextInt())
        }
        // Interior sprays are borne on short branches and occlude the scaffold naturally.
        // Without them the visible crown would be only a hollow ring around the outer tips.
        for(row in 0..4)for(column in -2..2) {
            if(row==4 && column==0 && !shrub)continue
            val dy=-.66f+row*.33f
            val dx=column*.31f+(random.nextFloat()-.5f)*.16f
            if(dx*dx+dy*dy>.88f)continue
            val x=150f+dx*form.radius
            val y=middle+dy*height+(random.nextFloat()-.5f)*14f
            val sy=(y+33f).coerceAtMost(form.fork)
            branches+=Bough(axis(sy),sy,axis(sy)+(x-axis(sy))*.65f,y+12,x,y,1.5f)
            foliage+=Foliage(x,y,if(pine) 19f else 23f,if(pine) 12f else 24f,random.nextInt())
        }
        // Fit spray centres inside the crown envelope, leaving the leaf tips free and irregular.
        // Hard-clamping every high shoot to the same Y would produce a flat, pollarded-looking top.
        for(i in foliage.indices) {
            val f=foliage[i]
            val rx=(form.radius-f.rx*.6f).coerceAtLeast(25f)
            val ry=(height-f.ry*.65f).coerceAtLeast(15f)
            val dx=(f.x-150f)/rx;val dy=(f.y-middle)/ry
            val norm=hypot(dx,dy)
            if(norm>1f)foliage[i]=f.copy(x=150f+(f.x-150f)/norm,y=middle+(f.y-middle)/norm)
        }
        for(f in foliage) crownMass(f,true)
        if(!shrub && !form.bottleTrunk) trunk(form.top+24f)
        for(b in branches) branch(b)
        if(form.bottleTrunk)bottleTrunk()
        // A rear wash ties the crown together; lighted leaf sprays conceal only the small tips.
        for((i,f) in foliage.withIndex()) {
            if(form.droop>0f) hangingTwigs(f)
            crownMass(f,false)
            if(look.thorny && i%3==0) brush.line(f.x,f.y+12,f.x+6,f.y+6,darkBark,.65f)
        }
    }

    private fun bottleHabit() {
        val tips=listOf(61f to 111f,83f to 71f,117f to 51f,152f to 61f,185f to 49f,220f to 76f,237f to 115f)
        val sprays=tips.mapIndexed{i,(x,y)->Foliage(x,y,27f,23f,look.seed*43+i)}
        sprays.forEach{crownMass(it,true)}
        tips.forEachIndexed{i,(x,y)->
            val sx=135f+i*5;val sy=180f+(i%2)*9
            branch(Bough(sx,sy,sx+(x-sx)*.27f,y+62f,x,y,18f-(abs(i-3))*2))
            for(side in listOf(-1,1))branch(Bough(x,y+22,x+side*15,y+3,x+side*22,y-4,3.8f))
        }
        bottleTrunk()
        sprays.forEach{crownMass(it,false)}
    }

    private fun bottleTrunk() {
        val q=Path().apply {
            moveTo(103f,280f);cubicTo(120f,263f,107f,207f,120f,172f)
            quadTo(126f,155f,124f,144f);quadTo(142f,160f,151f,168f)
            quadTo(167f,161f,178f,145f);quadTo(174f,165f,182f,181f)
            cubicTo(196f,218f,180f,261f,196f,280f);quadTo(151f,275f,103f,280f);close()
        }
        brush.wash(q,105f,220f,197f,220f,intArrayOf(0xffb5aa95.toInt(),bark,darkBark))
        brush.stroke(q,darkBark,.8f)
        canvas.save();canvas.clipPath(q)
        repeat(if(detailed)90 else 20){i->val y=174f+random.nextFloat()*106;val x=111f+random.nextFloat()*80
            brush.stroke(Path().apply{moveTo(x,y);quadTo(x-2,y+4,x+1,y+8)},0x456a6757,.55f)
            if(i%7==0)brush.line(x-3,y,x+4,y-1,0x647f7562,.6f)
        }
        canvas.restore()
        for(side in listOf(-1,1))brush.stroke(Path().apply{moveTo(150f+side*23,227f);quadTo(150f+side*26,263f,150f+side*49,279f)},0xff786f5b.toInt(),.8f)
    }

    private fun trunk(top:Float) {
        val half=form.trunkWidth*.5f
        val trunk=Path().apply {
            moveTo(150f-half-6f,280f)
            cubicTo(axis(256f)-half,258f,axis(190f)-half*.65f,192f,axis(top)-1f,top)
            lineTo(axis(top)+1.6f,top)
            cubicTo(axis(190f)+half*.63f,193f,axis(256f)+half*.9f,258f,150f+half+8f,280f)
            quadTo(150f,277f,150f-half-6f,280f);close()
        }
        brush.wash(trunk,150f-half,0f,150f+half,0f,intArrayOf(
            botanicalMix(bark,0xffe9dcc0.toInt(),.45f),bark,darkBark))
        brush.stroke(trunk,darkBark,.7f)
        canvas.save();canvas.clipPath(trunk)
        for(i in 0..(if(detailed) 65 else 16)) {
            val y=top+random.nextFloat()*(280-top)
            val x=axis(y)+(random.nextFloat()-.5f)*form.trunkWidth
            when(form.barkSurface) {
                BarkSurface.PAPERY -> {
                    val w=1f+random.nextFloat()*5
                    brush.stroke(Path().apply { moveTo(x-w,y);quadTo(x,y+1f,x+w,y-.5f) },darkBark,if(i%4==0) 1.4f else .6f)
                    if(y>253)brush.line(x,y,x-2,y+10,darkBark,1.5f)
                }
                BarkSurface.LENTICELS -> brush.line(x-3f,y,x+4f,y-.5f,0xffc5b391.toInt(),.7f)
                BarkSurface.SMOOTH -> brush.line(x,y,x+1f,y+5f,0x39727263,.4f)
                BarkSurface.FLUTED -> brush.stroke(Path().apply { moveTo(x,y);quadTo(x-3f,y+15,x+2f,y+35) },0x43605d50,1.1f)
                BarkSurface.PLATED -> {
                    brush.stroke(Path().apply { moveTo(x-3f,y);lineTo(x-4f,y+8f);lineTo(x,y+11f);lineTo(x+3f,y+8) },darkBark,.7f)
                }
                BarkSurface.FURROWED -> brush.stroke(Path().apply { moveTo(x,y);lineTo(x-1.5f,y+4);lineTo(x+.5f,y+9);lineTo(x-.5f,y+17) },darkBark,.65f)
            }
        }
        canvas.restore()
        for(side in listOf(-1,1))brush.stroke(Path().apply {
            moveTo(150f+side*half*.5f,266f);quadTo(150f+side*half,277f,150f+side*(half+12),280f)
        },darkBark,.8f)
    }

    private fun branch(b:Bough) {
        // A tapered ribbon avoids the blunt constant-width ends of the former fan-shaped limbs.
        val dx=b.xx-b.x;val dy=b.yy-b.y;val norm=hypot(dx,dy).coerceAtLeast(1f)
        val nx=-dy/norm;val ny=dx/norm
        val shape=Path().apply {
            moveTo(b.x+nx*b.width*.5f,b.y+ny*b.width*.5f)
            quadTo(b.cx+nx*b.width*.32f,b.cy+ny*b.width*.32f,b.xx,b.yy)
            quadTo(b.cx-nx*b.width*.26f,b.cy-ny*b.width*.26f,b.x-nx*b.width*.5f,b.y-ny*b.width*.5f);close()
        }
        brush.fill(shape,bark);brush.stroke(shape,darkBark,.45f)
        brush.stroke(Path().apply { moveTo(b.x-nx*b.width*.2f,b.y-ny*b.width*.2f);quadTo(b.cx,b.cy,b.xx,b.yy) },
            botanicalMix(bark,0xffe5d2ac.toInt(),.3f),b.width*.22f)
    }

    private fun crownMass(f:Foliage,back:Boolean) {
        val rnd=Random(f.seed)
        val density=form.density
        val outline=Path()
        val scale=if(back) 1f else .84f
        for(i in 0..100) {
            val a=i*2*PI/100
            val r=scale*(1+.055f*sin(a*11)+.045f*sin(a*19+f.seed%19))
            val x=f.x+cos(a).toFloat()*f.rx*r.toFloat()
            val y=f.y+sin(a).toFloat()*f.ry*r.toFloat()
            if(i==0)outline.moveTo(x,y) else outline.lineTo(x,y)
        }
        outline.close()
        if(density>.7f || back) {
            val depth=((f.y-form.top)/(form.bottom-form.top)).coerceIn(0f,1f)
            val base=botanicalMix(green,0xff243e2d.toInt(),if(back) .36f else .04f+depth*.23f)
            val alpha=if(density<.7f) 145 else 245
            fun transparent(color:Int)=(color and 0xffffff) or (alpha shl 24)
            brush.cloud(outline,f.x-f.rx*.32f,f.y-f.ry*.4f,f.rx*1.8f,intArrayOf(
                transparent(botanicalMix(base,0xffc5c480.toInt(),if(back) .08f else .27f)),transparent(base),
                transparent(botanicalMix(base,0xff193c30.toInt(),.27f))))
        }
        if(back)return
        repeat(((if(detailed) 100 else 22)*density).toInt()) { i ->
            val a=rnd.nextFloat()*2*PI;val r=sqrt(rnd.nextFloat())
            val x=f.x+cos(a).toFloat()*r*f.rx;val y=f.y+sin(a).toFloat()*r*f.ry
            val lit=((f.y-y)/f.ry*.18f+.16f+rnd.nextFloat()*.18f).coerceIn(0f,.43f)
            val depth=((f.y-form.top)/(form.bottom-form.top)).coerceIn(0f,1f)
            val localGreen=botanicalMix(green,0xff304e35.toInt(),depth*.24f)
            val tone=botanicalMix(localGreen,if(i%4==0)0xff254535.toInt() else 0xffbac278.toInt(),lit)
            if(pine) {
                for(j in -2..2)brush.stroke(Path().apply { moveTo(x,y+2);quadTo(x+j*1.8f,y-1,x+j*2.1f,y-6-rnd.nextFloat()*3) },tone,.6f)
            } else {
                val leafSize=if(look.leaf==LeafShape.PALMATE) 3.7f else if(look.leaf==LeafShape.PINNATE) 2.1f else 2.8f
                val tilt=rnd.nextFloat()*140-70
                brush.at(x,y,tilt) {
                    val leaf=Path().apply { moveTo(0f,leafSize);quadTo(-leafSize,-.7f,0f,-leafSize)
                        quadTo(leafSize,-.5f,0f,leafSize);close() }
                    brush.fill(leaf,tone)
                    if(i%3==0)brush.line(0f,leafSize*.7f,0f,-leafSize*.7f,0x5871834d,.35f)
                }
            }
        }
        // Broken contour strokes describe foliage planes without outlining every clump as a bubble.
        repeat(9) {
            val a=rnd.nextFloat()*PI*2;val x=f.x+cos(a).toFloat()*f.rx*.8f;val y=f.y+sin(a).toFloat()*f.ry*.8f
            brush.stroke(Path().apply { moveTo(x-2,y);quadTo(x,y-2,x+3,y-1) },0x6b3e5a38,.45f)
        }
    }

    private fun hangingTwigs(f:Foliage) {
        for(i in 0..3) {
            val x=f.x+(i-1.5f)*7f;val y=f.y+f.ry*.32f
            val drop=form.droop*(.6f+random.nextFloat()*.4f)
            val side=if(x<150) -1 else 1
            val twig=Path().apply { moveTo(x,y);cubicTo(x+side*8,y+8,x+side*10,y+drop*.7f,x+side*7,y+drop) }
            brush.stroke(twig,darkBark,.48f)
            for(j in 1..7) {
                val t=j/8f
                val xx=x+side*(24*t*(1-t).pow(2)+30*t*t*(1-t)+7*t.pow(3))
                val yy=y+24*t*(1-t).pow(2)+3*drop*.7f*t*t*(1-t)+drop*t.pow(3)
                brush.oval(xx+if(j%2==0) 1.3f else -1.3f,yy,1.8f,2.7f,botanicalMix(green,0xffbec483.toInt(),j%3*.12f))
            }
        }
    }

    private fun matureConifer() {
        val sprays=mutableListOf<Foliage>()
        for(i in 0..7)for(side in listOf(-1,1)) {
            val t=(i+1f)/9
            val y=form.top+15+t*(form.bottom-form.top-15)
            val reach=form.radius*sin(PI*(.04f+.87f*t)).toFloat().pow(.7f)
            val endX=150f+side*reach*(.75f+random.nextFloat()*.15f)
            val endY=y-8f-random.nextFloat()*12
            branches+=Bough(axis(y+17),y+17,150f+side*reach*.7f,y+14,endX,endY,2f+t*4.5f)
            for(j in 0..3) {
                val u=.24f+j*.24f
                sprays+=Foliage(150f+(endX-150)*u,endY+8f*(1-u),13f+random.nextFloat()*5,19f+random.nextFloat()*7,random.nextInt())
            }
        }
        val central=(0..4).map{i->Foliage(150f+sin(i*2.1f)*7,form.top+20f+i*29f,18f,24f,random.nextInt())}
        fun spray(f:Foliage,front:Boolean) {
            val rnd=Random(f.seed)
            val q=Path()
            for(i in 0..90){val a=i*2*PI/90;val r=1f+.10f*sin(a*11).toFloat()+.05f*sin(a*19).toFloat()
                val x=f.x+cos(a).toFloat()*f.rx*r;val y=f.y+sin(a).toFloat()*f.ry*r
                if(i==0)q.moveTo(x,y)else q.lineTo(x,y)}
            q.close()
            val depth=((f.y-form.top)/(form.bottom-form.top)).coerceIn(0f,1f)
            val shade=botanicalMix(green,0xff27483a.toInt(),if(front).10f+depth*.24f else .44f)
            brush.cloud(q,f.x-7,f.y-11,38f,intArrayOf(botanicalMix(shade,0xffaabc86.toInt(),.35f),shade,botanicalMix(shade,0xff254537.toInt(),.3f)))
            if(front)repeat(if(detailed)30 else 10) {
                val a=rnd.nextFloat()*2*PI;val r=sqrt(rnd.nextFloat())
                val x=f.x+cos(a).toFloat()*r*f.rx;val y=f.y+sin(a).toFloat()*r*f.ry
                brush.at(x,y,rnd.nextFloat()*80-40,.05f){PlantConiferPainter(canvas,look,false).awlShoot(110f)}
            }
        }
        sprays.forEach{spray(it,false)}
        trunk(form.top)
        branches.forEach(::branch)
        // Irregular foliage hides some branches, while gaps expose the thick upper limbs.
        sprays.filterIndexed{i,_->i%4!=0}.forEach{spray(it,true)}
        central.forEach{spray(it,true)}
        for(i in 0..4)brush.at(150f+(i-2)*3f,form.top+28f, (i-2)*13f,.15f){PlantConiferPainter(canvas,look,false).awlShoot(160f)}
    }

    private fun conifer() {
        trunk(form.top)
        val type=form.spray
        val tiers=if(type==ConiferSpray.TUFTED) 12 else 15
        for(i in 0 until tiers) {
            val t=(i+1f)/tiers
            val tierY=form.top+14+t*(form.bottom-form.top-22)
            val reach=form.radius*if(form.roundedConifer) sin(PI*(.05f+.87f*t)).toFloat().pow(.6f) else t.pow(.82f)*(1f-.16f*t)
            for(side in listOf(-1,1)) {
                val y=tierY+(random.nextFloat()-.5f)*8f
                val x=axis(y)
                val endY=y+when(type) {ConiferSpray.PENDULOUS->14f*t;ConiferSpray.TUFTED->6f*t;else->-5f*t}+(random.nextFloat()-.5f)*8f
                val endX=x+side*reach*(.92f+random.nextFloat()*.13f)
                val cy=y+if(type==ConiferSpray.PENDULOUS) 10f else 3f
                branch(Bough(x,y,x+side*reach*.6f,cy,endX,endY,1.4f+t*if(form.roundedConifer) form.trunkWidth*.12f else 1.6f))
                // Shorter foreshortened sprays fill the rear and front of each whorl.
                for(layer in 0..1) {
                    val r=reach*if(layer==0) .66f else 1f
                    val samples=(r/4).toInt().coerceAtLeast(3)
                    for(j in 1..samples) {
                        val u=j.toFloat()/samples
                        val xx=x+side*r*u
                        val yy=y+(endY-y)*u+(if(layer==0) -4f else 0f)+(random.nextFloat()-.5f)*4f
                        val size=(7f+9*t)*(1-u*.3f)*(.68f+random.nextFloat()*.65f)
                        if(type==ConiferSpray.AWL) {
                            brush.at(xx,yy,side*24f,.10f){PlantConiferPainter(canvas,look,detailed).awlShoot(size*8f)}
                        } else if(type==ConiferSpray.TUFTED) {
                            val drop=size*(.7f+random.nextFloat()*.5f)
                            brush.stroke(Path().apply { moveTo(xx,yy);quadTo(xx+side*4,yy+drop*.7f,xx+side*3,yy+drop) },bark,.45f)
                            for(k in 0..3) {
                                val by=yy+k*drop/3;val bx=xx+side*k
                                for(n in -2..2)brush.line(bx,by,bx+n*1.4f,by-4.2f,botanicalMix(green,0xffcad08c.toInt(),random.nextFloat()*.25f),.6f)
                            }
                        } else {
                            val down=type==ConiferSpray.PENDULOUS
                            val sx=xx+side*if(down) 4f else size*(.5f+random.nextFloat()*.4f)
                            val sy=yy+if(down) size*1.5f else -size*(.35f+random.nextFloat()*.65f)
                            brush.stroke(Path().apply { moveTo(xx,yy);quadTo(sx,yy+if(down) size*.6f else -2f,sx,sy) },darkBark,.52f)
                            for(k in 0..8) {
                                val v=k/9f;val bx=xx+(sx-xx)*v;val by=yy+(sy-yy)*v
                                val needle=(3.8f+(1-v)*2.6f)*(.8f+random.nextFloat()*.4f)
                                val color=botanicalMix(green,if(layer==0) 0xff233e30.toInt() else 0xffbac786.toInt(),if(layer==0) .3f else random.nextFloat()*.28f)
                                if(down) {
                                    brush.line(bx,by,bx-needle*.7f,by-needle*.7f,color,.78f)
                                    brush.line(bx,by,bx+needle*.7f,by-needle*.5f,color,.78f)
                                } else {
                                    brush.line(bx,by,bx+side*1.5f,by-needle,color,.85f)
                                    brush.line(bx,by,bx-side*1.2f,by+needle*.65f,color,.85f)
                                }
                            }
                        }
                    }
                }
            }
        }
        // The leader is visible above the first whorl and tapers naturally.
        for(i in 0..10)for(side in listOf(-1,1)) {
            val y=form.top+i*2.5f
            brush.line(axis(y),y,axis(y)+side*(2f+i*.42f),y-4,green,.7f)
        }
    }
}
