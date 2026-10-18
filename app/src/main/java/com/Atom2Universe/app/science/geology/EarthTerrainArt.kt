package com.Atom2Universe.app.science.geology

import android.graphics.*
import kotlin.math.*

/** Landscape cutaways: shared profiles keep water, shorelines and rock contacts connected. */
internal class EarthTerrainArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private val box = RectF()
    private val sand = 0xffbda078.toInt()
    private val basalt = 0xff425861.toInt()
    private val heat = 0xffa76853.toInt()
    private val gold = 0xffffc18a.toInt()
    private val teal = 0xff90d1cd.toInt()
    private enum class Grain { MASSIVE, BEDDED, CRYSTAL, MANTLE, NONE }
    private fun noise(i:Int,salt:Int=0):Float {
        var bits=i*374761393+salt*668265263
        bits=(bits xor (bits ushr 13))*1274126177
        return ((bits xor (bits ushr 16)) ushr 8)/16777215f
    }

    private fun ink(color: Int, width: Float = 1f) {
        p.shader = null; p.color = color; p.strokeWidth = width
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.ROUND
    }
    private fun shade(color: Int, f: Float) = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255))

    /** Smooth interpolated profile with no separate coastline geometry to drift out of alignment. */
    private fun profile(vararg xy: Float): (Float) -> Float = { x ->
        var i = 0
        while (i < xy.size - 4 && x > xy[i + 2]) i += 2
        val t = ((x - xy[i]) / (xy[i + 2] - xy[i])).coerceIn(0f, 1f)
        val y0 = xy[(i - 1).coerceAtLeast(1)]
        val y1 = xy[i + 1]; val y2 = xy[i + 3]
        val y3 = xy[(i + 5).coerceAtMost(xy.lastIndex)]
        .5f * ((2*y1) + (-y0+y2)*t + (2*y0-5*y1+4*y2-y3)*t*t + (-y0+3*y1-3*y2+y3)*t*t*t)
    }
    private fun rough(x: Float, seed: Float = 0f) =
        sin(x*.033f+seed)*2.4f + sin(x*.091f+seed*2)*1.2f + sin(x*.217f+seed)*.55f

    private fun outline(top: (Float)->Float, bottom: (Float)->Float, left:Float=42f, right:Float=958f): Path {
        path.reset(); path.moveTo(left, top(left))
        for (i in 1..160) { val x=left+(right-left)*i/160; path.lineTo(x,top(x)) }
        for (i in 160 downTo 0) { val x=left+(right-left)*i/160; path.lineTo(x,bottom(x)) }
        path.close(); return path
    }
    private fun material(c:Canvas, shape:Path, color:Int, grain:Grain=Grain.MASSIVE) {
        shape.computeBounds(box,true); clip.set(shape)
        ink(color)
        p.shader=LinearGradient(box.left,box.top,box.right,box.bottom,
            intArrayOf(shade(color,1.15f),color,shade(color,.68f)),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
        c.drawPath(clip,p);p.shader=null
        c.save();c.clipPath(clip)
        val left=box.left;val top=box.top;val w=box.width();val h=box.height()
        if(grain!=Grain.NONE) {
            // Fixed fine grain, not animated noise. Different structures identify different materials.
            repeat(if(grain==Grain.CRYSTAL) 150 else 220) { i ->
                val x=left+noise(i,3)*w;val y=top+noise(i,7)*h
                if(grain==Grain.CRYSTAL) {
                    val r=.8f+noise(i,11)*2.8f
                    ink(if(i%3==0) 0x48504d4c else 0x42e7d8bd)
                    path.reset();path.moveTo(x-r,y);path.lineTo(x,y-r*.8f);path.lineTo(x+r*.7f,y+1);path.lineTo(x-1,y+r);path.close();c.drawPath(path,p)
                } else {
                    ink(if(i%3==0) 0x20512f24 else 0x20f6e4c2)
                    c.drawOval(x,y,x+.7f+noise(i,17)*1.7f,y+.7f+noise(i,19),p)
                }
            }
        }
        when(grain) {
            Grain.BEDDED -> repeat((h/9).toInt().coerceAtMost(60)) { n ->
                trace(c,0x38efe0c2, .8f,left,left+w) { x -> top+n*9+sin(x*.024f+n)*2f }
                repeat(7) { i ->
                    val x=left+(i*151+n*37)%max(1,w.toInt())
                    stroke(c,0x26403025,.8f,x,top+n*9,x+8,top+n*9+7)
                }
            }
            Grain.MASSIVE -> repeat(34) { i ->
                val x=left+noise(i,31)*w;val y=top+noise(i,29)*h
                stroke(c,0x36403631,.9f,x-12,y-14,x-3,y-2,x+4,y+1,x+11,y+19)
                stroke(c,0x28e3d5bb,.8f,x+4,y+1,x+20,y-7)
            }
            Grain.MANTLE -> repeat(14) { i ->
                trace(c,0x18ffdca0,2f,left,left+w) { x -> top+h*.2f+i*h*.055f+sin(x*.008f+i*.3f)*18 }
            }
            else -> Unit
        }
        c.restore();ink(0x38f0d9b0,.8f);p.style=Paint.Style.STROKE;c.drawPath(clip,p);p.style=Paint.Style.FILL
    }
    private fun band(c:Canvas,top:(Float)->Float,bottom:(Float)->Float,color:Int,grain:Grain=Grain.MASSIVE,left:Float=42f,right:Float=958f) {
        material(c,outline(top,bottom,left,right),color,grain)
    }
    private fun poly(c:Canvas,color:Int,vararg xy:Float,grain:Grain=Grain.NONE) {
        path.reset();path.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1]);path.close()
        material(c,path,color,grain)
    }
    private fun trace(c:Canvas,color:Int,width:Float,left:Float=42f,right:Float=958f,y:(Float)->Float) {
        ink(color,width);p.style=Paint.Style.STROKE
        path.reset();path.moveTo(left,y(left));for(i in 1..140) { val x=left+(right-left)*i/140;path.lineTo(x,y(x)) }
        c.drawPath(path,p);p.style=Paint.Style.FILL
    }
    private fun stroke(c:Canvas,color:Int,width:Float,vararg xy:Float) {
        ink(color,width);p.style=Paint.Style.STROKE;path.reset();path.moveTo(xy[0],xy[1])
        for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1])
        c.drawPath(path,p);p.style=Paint.Style.FILL
    }
    private fun halo(c:Canvas,x:Float,y:Float,r:Float,color:Int,alpha:Int) {
        ink(-1)
        p.shader=RadialGradient(x,y,r,intArrayOf((color and 0xffffff) or (alpha shl 24),color and 0xffffff),null,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,p);p.shader=null
    }
    private fun backdrop(c:Canvas,phase:Float,mountains:Boolean) {
        halo(c,520f,290f,410f,0xff739fa0.toInt(),24)
        if(mountains) {
            for(layer in 0..2) {
                val ridge=profile(30f,390f,145f,285f+layer*30,270f,325f,410f,210f+layer*40,565f,330f,710f,260f,850f,350f,970f,400f)
                outline({x->ridge(x)+rough(x,layer.toFloat())*2},{480f})
                ink(Color.argb(14+layer*7,125,167,164));c.drawPath(path,p)
            }
        }
        // Soft, drifting cloud banks; no repeated opaque discs.
        repeat(3) { cloud ->
            val x=175f+cloud*310+sin(phase*2*PI).toFloat()*10
            repeat(5) { j -> halo(c,x+j*18,135f+cloud%2*32+sin(j*1.4f)*5,28f+j%3*9,0xffd1e0d9.toInt(),10) }
        }
    }
    private fun foundation(c:Canvas,top:(Float)->Float) {
        band(c,top,{665f},heat,Grain.MANTLE)
    }
    /** Ocean surface is a real wave contour, clipped against the same seabed used by the rocks. */
    private fun ocean(c:Canvas,bed:(Float)->Float,level:Float,phase:Float,left:Float=42f,right:Float=958f) {
        fun surface(x:Float)=level+sin(x*.039f-phase*2*PI.toFloat())*2.1f+sin(x*.082f+phase*4*PI.toFloat())*.8f
        path.reset()
        var open=false
        var start=left
        val segments=200
        for(i in 0..segments+1) {
            val x=left+(right-left)*i.coerceAtMost(segments)/segments
            val wet=i<=segments && bed(x)>surface(x)
            if(wet && !open) { start=x;path.moveTo(x,surface(x));open=true }
            else if(wet) path.lineTo(x,surface(x))
            if(!wet && open) {
                val end=left+(right-left)*(i-1)/segments
                for(k in 80 downTo 0) { val xx=start+(end-start)*k/80;path.lineTo(xx,bed(xx)+.8f) }
                path.close();open=false
            }
        }
        clip.set(path);ink(-1)
        p.shader=LinearGradient(0f,level,0f,level+170,
            intArrayOf(0xcc80c7c5.toInt(),0xd3358195.toInt(),0xe3224a65.toInt()),floatArrayOf(0f,.22f,1f),Shader.TileMode.CLAMP)
        c.drawPath(clip,p);p.shader=null
        c.save();c.clipPath(clip)
        repeat(10) { row ->
            val y=level+5+row*7f
            repeat(8) { i ->
                val x=left+(i*131+row*47)%max(1,(right-left).toInt())
                val length=10f+(i*19+row*3)%43
                trace(c,Color.argb((72-row*6).coerceAtLeast(9),195,240,230),.7f,x,(x+length).coerceAtMost(right)) { xx -> y+sin(xx*.05f-phase*2*PI.toFloat()+row)*1.4f }
            }
        }
        trace(c,0xb5d8f7e9.toInt(),1.4f,left,right,::surface)
        // Underwater caustics are faint and fade with depth.
        repeat(26) { i ->
            val x=left+(i*97%max(1,(right-left).toInt()));val y=level+24+(i*31%80)
            stroke(c,0x1484e2e0,1f,x-10,y,x,y+3,x+14,y-2)
        }
        c.restore()
    }
    private fun surfaceDetail(c:Canvas,top:(Float)->Float,sea:Float=Float.POSITIVE_INFINITY,left:Float=42f,right:Float=958f,alpine:Boolean=false) {
        // Fractured faces beneath the skyline, with lighting that changes across each ridge.
        val face=Path(outline(top,{x->top(x)+65f},left,right))
        c.save();c.clipPath(face)
        repeat(32) { i ->
            val x=left+(right-left)*noise(i,43)
            val y=top(x)
            if(y>=sea-2) return@repeat
            val slope=top(x+4)-top(x-4)
            if(abs(slope)<2.5f) return@repeat
            val reach=18+noise(i,47)*47
            ink(if(slope>0) 0x34423935 else 0x24eee0b9)
            path.reset();path.moveTo(x,y)
            path.quadTo(x-slope*1.1f,y+reach*.4f,x-slope*2.6f-8,y+reach)
            path.quadTo(x-slope*.4f+8,y+reach*.6f,x+7,y+12)
            path.close();c.drawPath(path,p)
            stroke(c,0x314b4c40,.75f,x,y+2,x-slope,y+reach*.45f,x-slope*2.6f-8,y+reach)
        }
        c.restore()
        trace(c,0x90ddcbb0.toInt(),1.2f,left,right,top)
        // Soil and vegetation follow the actual surface, including on sloping ground.
        for(i in 0..85) {
            val x=left+(right-left)*(i+noise(i,59)*.8f)/86;val y=top(x)
            if(y<sea && (!alpine || y>260f)) {
                stroke(c,0xff667b5b.toInt(),2f,x,y-1,x+4,y+rough(x)*.3f)
                if(noise(i,61)>.64f && abs(top(x+6)-top(x-6))<8) tree(c,x,y,8f+noise(i,67)*14)
            }
        }
    }
    private fun tree(c:Canvas,x:Float,y:Float,h:Float) {
        stroke(c,0xff4c5140.toInt(),1.1f,x,y,x,y-h*.8f)
        repeat(4) { tier ->
            val yy=y-h+tier*h*.19f;val w=h*(.12f+tier*.065f)
            ink(if(tier%2==0) 0xff466c58.toInt() else 0xff70917a.toInt())
            path.reset();path.moveTo(x,yy-h*.16f);path.lineTo(x-w,yy+h*.22f);path.lineTo(x-1,yy+h*.14f);path.lineTo(x+w,yy+h*.22f);path.close();c.drawPath(path,p)
        }
    }
    private fun snowfield(c:Canvas,top:(Float)->Float,left:Float,right:Float,depth:Float) {
        band(c,top,{x->top(x)+depth*(.55f+.25f*sin(x*.095f)+.12f*sin(x*.24f))},0xffdbe7df.toInt(),Grain.NONE,left,right)
        repeat(8) { i ->
            val x=left+(right-left)*i/8
            stroke(c,0x6076979e,1f,x,top(x)+3,x+4,top(x)+depth*.6f)
        }
    }
    private fun arrow(c:Canvas,x:Float,y:Float,dx:Float,dy:Float,phase:Float) {
        val a=atan2(dy,dx);val drift=sin(phase*2*PI).toFloat()*3
        val xx=x+dx+drift;val yy=y+dy
        stroke(c,0x664c3529,4f,x+drift,y+1,xx,yy+1)
        stroke(c,gold,2f,x+drift,y,xx,yy)
        stroke(c,gold,2f,xx-cos(a-.5f)*12,yy-sin(a-.5f)*12,xx,yy,xx-cos(a+.5f)*12,yy-sin(a+.5f)*12)
    }
    private fun seismic(c:Canvas,x:Float,y:Float,phase:Float,r:Float=26f) {
        ink(gold);c.drawCircle(x,y,2.4f,p)
        ink(Color.argb(((1-phase)*110).toInt(),255,211,157),1f);p.style=Paint.Style.STROKE
        c.drawCircle(x,y,3+phase*r,p);p.style=Paint.Style.FILL
    }
    private fun plume(c:Canvas,x:Float,y:Float,phase:Float) {
        repeat(12) { i ->
            val t=(phase+i/12f)%1f
            halo(c,x+t*35+sin(t*8)*6,y-t*100,7+t*19,0xffc5ccc5.toInt(),(70*sin(t*PI)).toInt())
        }
    }
    private fun chamber(c:Canvas,x:Float,y:Float,rx:Float,ry:Float) {
        path.reset()
        repeat(65) { i ->
            val a=i*2*PI/64;val r=1+.1*sin(a*3)+.05*cos(a*7)
            val xx=x+cos(a).toFloat()*rx*r.toFloat();val yy=y+sin(a).toFloat()*ry*r.toFloat()
            if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
        }
        path.close();ink(-1);p.shader=RadialGradient(x-rx*.25f,y-ry*.2f,rx*1.4f,
            intArrayOf(0xffffd58f.toInt(),0xffec8958.toInt(),0xff9f5043.toInt()),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
        c.drawPath(path,p);p.shader=null
    }
    private fun conduit(c:Canvas,phase:Float,vararg xy:Float) {
        stroke(c,0x30ff9a56,15f,*xy);stroke(c,0xffd37751.toInt(),7f,*xy);stroke(c,0xffffc07a.toInt(),2.5f,*xy)
        particles(c,phase,0xffffe4ad.toInt(),7,*xy)
    }
    private fun particles(c:Canvas,phase:Float,color:Int,count:Int,vararg xy:Float) {
        val lengths=FloatArray(xy.size/2-1) { i->hypot(xy[i*2+2]-xy[i*2],xy[i*2+3]-xy[i*2+1]) }
        val total=lengths.sum()
        repeat(count) { n ->
            val t=((phase+n.toFloat()/count)%1f+1f)%1f;var d=t*total;var i=0
            while(i<lengths.lastIndex && d>lengths[i]) { d-=lengths[i];i++ }
            val f=d/lengths[i].coerceAtLeast(.01f);val j=i*2
            ink(color);p.alpha=(sin(t*PI)*235).toInt()
            c.drawCircle(xy[j]+(xy[j+2]-xy[j])*f,xy[j+1]+(xy[j+3]-xy[j+1])*f,2.2f,p)
        }
        p.alpha=255
    }

    private fun circulation(c:Canvas,x:Float,y:Float,phase:Float,reverse:Boolean=false) {
        fun xx(t:Float)=x+cos(t*2*PI).toFloat()*76
        fun yy(t:Float)=y+sin(t*2*PI).toFloat()*34+sin(t*4*PI).toFloat()*9
        ink(0x28f5c9a0,1.1f);p.style=Paint.Style.STROKE;path.reset()
        for(i in 0..80) { val t=i/80f;if(i==0) path.moveTo(xx(t),yy(t)) else path.lineTo(xx(t),yy(t)) }
        c.drawPath(path,p);p.style=Paint.Style.FILL
        repeat(12) { i ->
            val t=(if(reverse) -phase else phase)-i*.009f
            ink(Color.argb(170-i*12,255,205,149));c.drawCircle(xx(t),yy(t),2.8f-i*.13f,p)
        }
    }

    private fun shell(c:Canvas,phase:Float) {
        val terrain=profile(42f,325f,230f,333f,370f,321f,435f,304f,490f,276f,540f,252f,590f,235f,630f,242f,702f,175f,735f,194f,780f,232f,865f,247f,958f,253f)
        val top={x:Float->terrain(x)+rough(x)*.65f}
        val moho=profile(42f,355f,250f,362f,420f,362f,505f,405f,580f,439f,690f,470f,735f,464f,845f,425f,958f,420f)
        foundation(c,{510f})
        band(c,moho,{510f},0xff655d52.toInt())
        band(c,top,moho,sand,Grain.CRYSTAL)
        // The thinner oceanic crust is mineralogically darker; the boundary is gradational in this sketch.
        c.save()
        path.reset();path.moveTo(42f,100f);path.lineTo(467f,100f);path.lineTo(458f,300f);path.lineTo(426f,420f);path.lineTo(42f,420f);path.close()
        c.clipPath(path)
        band(c,top,moho,basalt,Grain.MASSIVE)
        c.restore()
        surfaceDetail(c,top,260f,alpine=true)
        snowfield(c,top,682f,738f,18f)
        ocean(c,top,260f,phase)
        trace(c,0xbedbccad.toInt(),1.7f,y=moho)
        for(i in 0..3) circulation(c,165f+i*220,575f,phase+i*.17f,i%2==1)
    }

    private fun ridge(c:Canvas,phase:Float) {
        val terrain=profile(42f,355f,170f,349f,275f,335f,355f,307f,410f,291f,455f,258f,480f,250f,497f,270f,515f,249f,545f,264f,605f,300f,690f,328f,815f,345f,958f,352f)
        val top={x:Float->terrain(x)+rough(x)*1.4f}
        val bottom={x:Float->top(x)+35f+abs(x-500)*.06f}
        foundation(c,bottom);band(c,top,bottom,basalt)
        c.save();c.clipPath(Path(outline(top,bottom)))
        for(i in 0..10) {
            val d=(i+phase)/11*470
            for(sign in listOf(-1,1)) {
                val x=500+sign*d
                stroke(c,0x448fb1ad,3f,x,top(x),x+sign*12,bottom(x))
            }
        }
        c.restore()
        chamber(c,500f,435f,34f,47f)
        conduit(c,phase,500f,455f,493f,411f,506f,368f,497f,321f,501f,269f)
        conduit(c,phase+.25f,502f,373f,524f,345f,540f,307f)
        ocean(c,top,200f,phase)
        arrow(c,365f,438f,-90f,0f,phase);arrow(c,635f,438f,90f,0f,phase)
        circulation(c,255f,569f,phase);circulation(c,755f,569f,phase,true)
        arrow(c,500f,580f,0f,-63f,phase)
    }

    private fun subduction(c:Canvas,phase:Float) {
        val terrain=profile(42f,317f,235f,320f,365f,329f,413f,349f,438f,371f,470f,340f,507f,297f,551f,279f,603f,259f,649f,266f,690f,246f,721f,205f,754f,176f,762f,184f,775f,177f,803f,227f,846f,259f,904f,246f,958f,241f)
        val top={x:Float->terrain(x)+rough(x)*.65f}
        val slab=profile(42f,317f,250f,320f,375f,336f,435f,371f,506f,417f,580f,473f,659f,544f,735f,608f)
        foundation(c,top)
        val moho=profile(435f,407f,555f,434f,715f,449f,835f,409f,958f,405f)
        band(c,top,moho,sand,Grain.CRYSTAL,left=435f)
        band(c,slab,{x->slab(x)+36f},basalt,left=42f,right=735f)
        trace(c,0xa1e7d2a5.toInt(),2f,42f,735f,slab)
        // Small imbricated sediment wedges at the trench, not a sheer vertical water edge.
        band(c,top,{x->min(top(x)+31,moho(x))},0xffa99372.toInt(),Grain.BEDDED,438f,555f)
        c.save();c.clipPath(Path(outline(top,{x->min(top(x)+31,moho(x))},438f,555f)))
        repeat(5) { i ->
            val x=435f+i*22
            stroke(c,0x78e0c89d,1.1f,x,top(x)+28,x+15,top(x)+12,x+31,top(x)-3)
        }
        c.restore()
        surfaceDetail(c,top,250f,left=490f,alpine=true)
        ocean(c,top,250f,phase)
        chamber(c,772f,440f,40f,24f)
        conduit(c,phase,772f,441f,761f,407f,776f,371f,765f,333f,776f,295f,764f,256f,767f,184f)
        conduit(c,phase+.3f,767f,343f,738f,325f,725f,298f)
        conduit(c,phase+.6f,776f,379f,810f,358f,823f,341f)
        stroke(c,0xff594f42.toInt(),2.5f,752f,176f,761f,185f,775f,177f)
        plume(c,765f,175f,phase)
        repeat(6) { i ->
            val x=476f+i*41;seismic(c,x,slab(x)+16,(phase*2+i*.17f)%1f,22f)
        }
        for(i in 0..2) {
            val x=605f+i*39
            particles(c,phase+i*.2f,teal,4,x,slab(x)-4,x+8,slab(x)-44,x+23,slab(x)-73)
        }
        arrow(c,191f,409f,84f,0f,phase);arrow(c,886f,468f,-65f,0f,phase)
    }

    private fun mountains(c:Canvas,stage:Float,phase:Float,folded:Boolean) {
        val skyline=profile(42f,355f,150f,335f,225f,320f,280f,291f,330f,309f,378f,243f,425f,270f,478f,178f,512f,144f,550f,207f,586f,228f,626f,203f,680f,276f,730f,264f,780f,318f,865f,329f,958f,355f)
        val top={x:Float->355f+(skyline(x)-355f)*(.42f+stage*.58f)+rough(x)*(.5f+stage)}
        fun bed(x:Float,layer:Int):Float {
            if(layer==0) return top(x)
            val foldedY=345f+layer*37-exp(-((x-510)/235).pow(2))*(85+stage*75)+sin(x/(if(folded) 42f else 80f)+layer*.17f)*stage*23
            // Blend the eroded surface into the deeper structural folds without inverting beds.
            return max(top(x)+layer*18,foldedY)
        }
        foundation(c,{x->bed(x,5)})
        val root={x:Float->bed(x,5)+exp(-((x-505)/128).pow(2))*(70+stage*126)}
        band(c,{x->bed(x,5)},root,basalt,Grain.CRYSTAL)
        val colors=intArrayOf(0xffb9a588.toInt(),0xff8f8275.toInt(),0xffc8b99a.toInt(),0xff997b64.toInt(),0xff696f70.toInt())
        for(i in 4 downTo 0) band(c,{x->bed(x,i)},{x->bed(x,i+1)},colors[i],if(i%2==0) Grain.BEDDED else Grain.MASSIVE)
        surfaceDetail(c,top,alpine=true)
        snowfield(c,top,482f,546f,12f+stage*16)
        snowfield(c,top,610f,648f,8f+stage*7)
        // Thin thrust trace is distinct from the magmatic plumbing in volcanic scenes.
        stroke(c,0xb6ead2a3.toInt(),1.6f,363f,498f,422f,439f,488f,375f,555f,310f,603f,274f)
        arrow(c,168f,467f,80f,0f,phase);arrow(c,843f,467f,-80f,0f,phase)
    }

    private fun hotspot(c:Canvas,phase:Float) {
        val top={x:Float ->
            var y=348f+rough(x)*.8f
            for(i in 0..4) {
                val center=195f+i*140
                val h=34f+i*28
                val d=abs((x-center)/(45+i*5f))
                y-=h*exp(-d.pow(1.65f))
            }
            y
        }
        foundation(c,{420f});band(c,top,{420f},basalt)
        surfaceDetail(c,top,248f,alpine=true)
        ocean(c,top,248f,phase)
        // Broad warm upwelling narrows into branching melt pathways beneath the active edifice.
        halo(c,760f,601f,101f,gold,54)
        halo(c,757f,523f,58f,gold,39)
        halo(c,760f,460f,54f,gold,33)
        for(i in 0..3) {
            val x=724f+i*23
            particles(c,phase+i*.12f,0xffdca26e.toInt(),5,x,643f,x+sin(i.toFloat())*10,571f,754f+(i-1.5f)*12,476f,760f+(i-1.5f)*19,414f)
        }
        conduit(c,phase,754f,389f,760f,349f,751f,300f,755f,top(755f))
        chamber(c,759f,369f,32f,17f)
        conduit(c,phase+.3f,758f,365f,725f,331f,707f,310f)
        plume(c,755f,top(755f)-3,phase)
        arrow(c,460f,454f,-92f,0f,phase)
    }

    private fun faults(c:Canvas,stage:Float,phase:Float) {
        val drop=stage*110
        foundation(c,{388f})
        val colors=intArrayOf(0xffb6a689.toInt(),0xff917b66.toInt(),0xffc1b397.toInt(),basalt)
        for(layer in 3 downTo 0) {
            val y=250f+layer*38;val b=y+38
            val l=325+(y-250)*.48f;val lb=325+(b-250)*.48f
            val r=680-(y-250)*.4f;val rb=680-(b-250)*.4f
            val topLeft={x:Float->y+if(layer==0) rough(x)*2 else sin(x*.025f+layer)*2}
            band(c,topLeft,{b},colors[layer],Grain.BEDDED,42f,l)
            poly(c,colors[layer],l,y,lb,b,l,b,grain=Grain.BEDDED)
            poly(c,colors[layer],l+drop*.48f,y+drop,r-drop*.4f,y+drop,rb-drop*.4f,b+drop,lb+drop*.48f,b+drop,grain=Grain.BEDDED)
            band(c,{x->y+if(layer==0) rough(x)*2 else sin(x*.025f+layer)*2},{b},colors[layer],Grain.BEDDED,r,958f)
            poly(c,colors[layer],r,y,r,b,rb,b,grain=Grain.BEDDED)
        }
        surfaceDetail(c,{x->250+rough(x)*2},left=42f,right=324f)
        surfaceDetail(c,{x->250+rough(x)*2},left=681f,right=958f)
        surfaceDetail(c,{250+drop},left=328+drop*.48f,right=677-drop*.4f)
        stroke(c,0xc9e0cba9.toInt(),1.5f,325f,250f,433f,475f)
        stroke(c,0xc9e0cba9.toInt(),1.5f,680f,250f,590f,475f)
        arrow(c,214f,509f,-78f,0f,phase);arrow(c,785f,509f,78f,0f,phase)
        arrow(c,500f,267f+drop,0f,44f,phase)
    }

    private fun transform(c:Canvas,stage:Float,phase:Float) {
        val shift=(stage-.5f)*80
        fun plate(left:Boolean) {
            c.save()
            val direction=if(left) -1 else 1
            c.translate(direction*shift*.48f,direction*shift)
            val xy=if(left) floatArrayOf(80f,260f,435f,210f,585f,485f,230f,535f)
                else floatArrayOf(452f,208f,790f,160f,940f,435f,602f,483f)
            poly(c,0xff756457.toInt(),xy[4],xy[5],xy[6],xy[7],xy[6],xy[7]+58,xy[4],xy[5]+58,grain=Grain.BEDDED)
            if(!left) poly(c,basalt,790f,160f,940f,435f,940f,493f,790f,218f)
            poly(c,if(left) 0xffa6a58a.toInt() else 0xffb6a484.toInt(),*xy,grain=Grain.MASSIVE)
            val face=Path(clip);c.save();c.clipPath(face)
            for(i in 0..8) trace(c,0x568c8468,1f,65f,960f) { x->218+i*29+sin(x*.012f+i*.25f)*20-x*.14f }
            for(i in 0..2) trace(c,0x9ccbb795.toInt(),7f,65f,960f) { x->315+i*34-x*.1f+sin(x*.018f)*10 }
            val river=floatArrayOf(98f,344f,198f,354f,275f,326f,367f,340f,441f,320f,526f,337f,620f,314f,716f,325f,842f,295f,930f,305f)
            stroke(c,0x6dc6d2b4,12f,*river);stroke(c,0xff5299a0.toInt(),6f,*river);stroke(c,0xaec1e7df.toInt(),1.4f,*river)
            for(i in 0..38) tree(c,125f+(i*127%780),225f+(i*67%280),9f+i%5)
            c.restore();c.restore()
        }
        plate(true);plate(false)
        stroke(c,0xd5e7cd9f.toInt(),2f,444f,208f,478f,270f,502f,318f,542f,384f,593f,485f)
        arrow(c,398f,396f,-33f,-62f,phase);arrow(c,641f,299f,33f,62f,phase)
        seismic(c,520f,350f,(phase*2)%1f,54f)
    }

    private fun landscape(c:Canvas,stage:Float,phase:Float) {
        val skyline=profile(42f,491f,140f,458f,234f,383f,293f,365f,349f,261f,384f,307f,422f,384f,465f,423f,505f,425f,553f,357f,610f,302f,663f,378f,719f,440f,818f,468f,958f,491f)
        val top={x:Float->skyline(x)+exp(-((x-420)/270).pow(2))*stage*83+rough(x)*(1.5f-stage)}
        foundation(c,{585f})
        band(c,top,{585f},0xff9f937d.toInt(),Grain.MASSIVE)
        band(c,{x->top(x)+45},{x->min(top(x)+68,585f)},0xffb9a488.toInt(),Grain.BEDDED)
        surfaceDetail(c,top,alpine=true)
        snowfield(c,top,329f,391f,26f-stage*9)
        snowfield(c,top,582f,645f,17f-stage*6)
        val ice=floatArrayOf(369f,top(369f)+15,386f,top(386f)+20,415f,top(415f)+14,445f,top(445f)+7,482f,top(482f)+3)
        stroke(c,0xff7ea8b3.toInt(),22f,*ice);stroke(c,0xffc0dedf.toInt(),16f,*ice);stroke(c,0xffedf0dd.toInt(),4f,*ice)
        for(i in 0..6) {
            val x=389f+i*12;stroke(c,0x8e6996a1.toInt(),1f,x,top(x)+8,x+5,top(x)+20)
        }
        val river=floatArrayOf(482f,top(482f)+3,525f,478f,581f,496f,629f,501f,666f,520f,736f,527f,786f,548f,860f,547f,926f,563f)
        stroke(c,0xff6f806e.toInt(),11f,*river);stroke(c,0xff4e9ca4.toInt(),6f,*river);stroke(c,0xa3d0e9dc.toInt(),1.5f,*river)
        particles(c,phase,0xffe0c29a.toInt(),15,*river)
        for(i in 0..5) trace(c,0x85cab18d.toInt(),2f,795f+i*8,948f) {x->558f+i*5+(x-870)*.02f+sin(x*.04f)*2}
        arrow(c,350f,632f,0f,-38f,phase)
    }

    private fun fossil(c:Canvas,x:Float,y:Float,r:Float) {
        ink(0xff645d51.toInt(),1.4f);p.style=Paint.Style.STROKE;path.reset()
        repeat(100) { i ->
            val a=i*.15f;val rr=r*i/100
            val xx=x+cos(a)*rr;val yy=y+sin(a)*rr*.72f
            if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
        }
        c.drawPath(path,p);p.style=Paint.Style.FILL
        for(i in 0..13) {
            val a=i*.43f;stroke(c,0x9861584c.toInt(),.8f,x+cos(a)*r*.73f,y+sin(a)*r*.5f,x+cos(a)*r,y+sin(a)*r*.72f)
        }
    }

    private fun strata(c:Canvas,stage:Float,phase:Float,archive:Boolean) {
        val colors=intArrayOf(0xff7e8179.toInt(),0xffbeaa85.toInt(),0xffa17a5e.toInt(),0xffc9bc9a.toInt(),0xff8c8e81.toInt())
        val erosion={x:Float->358f+sin(x*.019f)*5+rough(x)*.6f}
        foundation(c,{x->if(archive) 568-(x-65)*.135f else 570f})
        if(archive) {
            c.save();c.clipPath(Path(outline(erosion,{665f},65f,940f)))
            for(i in 0..5) {
                val top={x:Float->568-i*58-(x-65)*.135f+sin(x*.021f+i*.3f)*3}
                band(c,top,{x->top(x)+58},colors[i%5],Grain.BEDDED,65f,940f)
            }
            c.restore()
            band(c,{285f},erosion,colors[3],Grain.BEDDED,65f,940f)
            val surface={x:Float->232+rough(x)*1.5f}
            band(c,surface,{285f},colors[4],Grain.MASSIVE,65f,940f)
            surfaceDetail(c,surface,left=65f,right=940f)
            trace(c,0xb6dcc699.toInt(),1.4f,65f,940f,erosion)
            poly(c,0xff906953.toInt(),691f,607f,678f,518f,660f,455f,670f,416f,650f,327f,663f,323f,687f,414f,682f,451f,699f,513f,711f,607f,grain=Grain.CRYSTAL)
            stroke(c,0xaaa4b19f.toInt(),1.3f,376f,601f,425f,505f,480f,398f)
            fossil(c,278f,310f,13f);fossil(c,773f,331f,10f)
        } else {
            val bed=570-stage*335
            for(i in 0..4) {
                val reveal=(stage*5-i).coerceIn(0f,1f)
                if(reveal<=0) continue
                val bottom=570-i*67
                band(c,{x->bottom-67*reveal+sin(x*.018f+i)*2},{x->bottom+sin(x*.018f+i+1)*2},colors[i],Grain.BEDDED,65f,940f)
            }
            val shore=profile(65f,171f,97f,215f,135f,335f,190f,502f,240f,579f,775f,579f,840f,510f,891f,355f,920f,245f,940f,173f)
            band(c,shore,{665f},0xff8e9382.toInt(),Grain.MASSIVE,65f,240f)
            band(c,shore,{665f},0xff8e9382.toInt(),Grain.MASSIVE,775f,940f)
            surfaceDetail(c,shore,194f,65f,240f)
            surfaceDetail(c,shore,194f,775f,940f)
            val bottom={x:Float->min(bed+sin(x*.018f)*2,shore(x))}
            ocean(c,bottom,194f,phase,65f,940f)
            repeat(30) { i ->
                val t=(phase+i*.137f)%1f
                ink(0xffdec6a0.toInt());p.alpha=(sin(t*PI)*190).toInt()
                val x=160f+(i*137%680)
                if(bottom(x)>201) c.drawCircle(x+sin(t*8)*3,200f+t*(bottom(x)-201),1.3f+i%2,p)
            }
            p.alpha=255
            if(stage>.6f) { fossil(c,355f,400f,17f);fossil(c,690f,422f,11f) }
        }
    }

    private fun cycle(c:Canvas,phase:Float) {
        val skyline=profile(42f,440f,120f,407f,179f,313f,223f,281f,264f,225f,300f,273f,340f,315f,405f,360f,471f,407f,530f,426f,600f,439f,670f,451f,744f,478f,847f,508f,958f,519f)
        val top={x:Float->skyline(x)+rough(x)}
        foundation(c,{560f});band(c,top,{560f},sand,Grain.BEDDED)
        surfaceDetail(c,top,454f,alpine=true);snowfield(c,top,245f,292f,21f)
        band(c,top,{x->top(x)+10f},basalt,Grain.MASSIVE,282f,336f)
        // A coast that slopes continuously into the basin, not a blue rectangle perched on land.
        ocean(c,top,454f,phase)
        val graniteTop=profile(123f,541f,150f,468f,184f,442f,226f,451f,266f,483f,285f,541f)
        band(c,graniteTop,{548f},0xffaaa497.toInt(),Grain.CRYSTAL,123f,285f)
        chamber(c,485f,595f,52f,48f)
        conduit(c,phase,488f,591f,478f,551f,489f,521f,477f,480f)
        conduit(c,phase+.3f,482f,548f,448f,525f,426f,523f)
        val metamorphic={x:Float->570+sin(x*.025f)*8}
        band(c,metamorphic,{x->metamorphic(x)+64},0xff666d70.toInt(),Grain.CRYSTAL,650f,895f)
        repeat(8) { i ->trace(c,0x99baa997.toInt(),2f,650f,895f) {x->metamorphic(x)+6+i*7+sin(x*.055f+i*.4f)*2} }
        for(i in 0..4) trace(c,0x9499a693.toInt(),1.5f,742f,951f) {x->top(x)+6+i*7}
        arrow(c,340f,371f,80f,44f,phase);arrow(c,817f,517f,0f,39f,phase)
        arrow(c,640f,636f,-70f,-4f,phase);arrow(c,196f,602f,0f,-44f,phase)
        particles(c,phase,0xffe6c99a.toInt(),11,339f,348f,425f,388f,536f,432f,654f,452f,793f,490f,917f,512f)
    }

    fun render(c:Canvas,scene:EarthScene,stage:Float,phase:Float) {
        backdrop(c,phase,scene in listOf(EarthScene.SHELL,EarthScene.SUBDUCTION,EarthScene.COLLISION,EarthScene.FOLDS,EarthScene.LANDSCAPE,EarthScene.ROCK_CYCLE))
        when(scene) {
            EarthScene.SHELL -> shell(c,phase)
            EarthScene.RIDGE -> ridge(c,phase)
            EarthScene.SUBDUCTION -> subduction(c,phase)
            EarthScene.COLLISION,EarthScene.FOLDS -> mountains(c,stage,phase,scene==EarthScene.FOLDS)
            EarthScene.TRANSFORM -> transform(c,stage,phase)
            EarthScene.HOTSPOT -> hotspot(c,phase)
            EarthScene.FAULTS -> faults(c,stage,phase)
            EarthScene.LANDSCAPE -> landscape(c,stage,phase)
            EarthScene.STRATA,EarthScene.ARCHIVE -> strata(c,stage,phase,scene==EarthScene.ARCHIVE)
            EarthScene.ROCK_CYCLE -> cycle(c,phase)
            else -> Unit
        }
    }
}
