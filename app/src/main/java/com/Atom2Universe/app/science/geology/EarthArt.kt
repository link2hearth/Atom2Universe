package com.Atom2Universe.app.science.geology

import android.graphics.*
import kotlin.math.*

/** Procedural educational sections. Coordinates are 1000 × 760; motion is illustrative. */
internal class EarthArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ochre = 0xffd7a66d.toInt()
    private val mantle = 0xffad614c.toInt()
    private val dark = 0xff394b59.toInt()
    private val water = 0xff599cb0.toInt()
    private val lava = 0xffffc078.toInt()
    private val bounds = RectF()

    private fun fill(c: Canvas, color: Int, points: List<Pair<Float, Float>>) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
        path.reset(); points.forEachIndexed { i, (x,y) -> if (i == 0) path.moveTo(x,y) else path.lineTo(x,y) }
        path.close(); path.computeBounds(bounds, true)
        paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(tint(color, .13f), color, shade(color, .72f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, paint); paint.shader = null
        c.save(); c.clipPath(path); paint.color = 0x18fff0d3
        repeat(52) { i ->
            val x = bounds.left + ((i * 137 + 29) % 997) / 997f * bounds.width()
            val y = bounds.top + ((i * 83 + 17) % 991) / 991f * bounds.height()
            c.drawCircle(x, y, 1.1f + i % 3 * .5f, paint)
        }
        c.restore(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f
        paint.color = tint(color, .28f); paint.alpha = 115
        c.drawPath(path, paint); paint.alpha = 255; paint.style = Paint.Style.FILL
    }
    private fun polygon(c: Canvas, color: Int, vararg p: Float) =
        fill(c, color, p.toList().chunked(2).map { it[0] to it[1] })
    private fun line(c: Canvas, color: Int, width: Float, vararg p: Float) {
        paint.shader = null; paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        path.reset(); p.toList().chunked(2).forEachIndexed { i, a -> if (i == 0) path.moveTo(a[0],a[1]) else path.lineTo(a[0],a[1]) }
        c.drawPath(path,paint); paint.style = Paint.Style.FILL
    }
    private fun arrow(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, phase: Float) {
        val drift = sin(phase * 2 * PI).toFloat() * 5
        val endX = x + dx + drift; val endY = y + dy
        val a = atan2(dy,dx); val size=14f
        line(c, lava, 3f,x+drift,y,endX,endY)
        line(c,lava,3f,endX-cos(a-.6f)*size,endY-sin(a-.6f)*size,endX,endY,endX-cos(a+.6f)*size,endY-sin(a+.6f)*size)
    }
    fun render(c: Canvas, scene: EarthScene, stage: Float, phase: Float, trueScale: Boolean) {
        when(scene) {
            EarthScene.GLOBE -> globe(c,trueScale,phase)
            EarthScene.SHELL -> shell(c,phase)
            EarthScene.RIDGE -> ridge(c,phase)
            EarthScene.SUBDUCTION -> subduction(c,phase)
            EarthScene.COLLISION, EarthScene.FOLDS -> folds(c,stage,phase)
            EarthScene.TRANSFORM -> transform(c,stage,phase)
            EarthScene.HOTSPOT -> hotspot(c,phase)
            EarthScene.FAULTS -> faults(c,stage,phase)
            EarthScene.LANDSCAPE -> landscape(c,stage,phase)
            EarthScene.STRATA -> strata(c,stage,false)
            EarthScene.ARCHIVE -> strata(c,stage,true)
            EarthScene.ROCK_CYCLE -> cycle(c,phase)
            EarthScene.AGES -> Unit
        }
    }
    private fun globe(c:Canvas, real:Boolean, phase:Float) {
        val cx=300f; val cy=365f; val radius=275f
        paint.shader=RadialGradient(cx,cy,radius+35f,intArrayOf(0x30577b88,0x00577b88),floatArrayOf(.83f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,radius+35f,paint);paint.shader=null
        val radii = if(real) floatArrayOf(1f,.992f,.896f,.545f,.192f) else floatArrayOf(1f,.94f,.79f,.53f,.24f)
        val colors=intArrayOf(ochre,0xffc68155.toInt(),mantle,0xfff0ab57.toInt(),0xffffd995.toInt())
        for(i in radii.indices) {
            val r=radius*radii[i]; paint.style=Paint.Style.FILL
            paint.shader=RadialGradient(cx-r*.28f,cy-r*.3f,r*1.4f,colors[i],shade(colors[i],.7f),Shader.TileMode.CLAMP)
            c.drawCircle(cx,cy,r,paint)
        }
        paint.shader=null
        c.save()
        val disc=Path().apply { addCircle(cx,cy,radius,Path.Direction.CW) }
        c.clipPath(disc);c.clipRect(cx-radius,cy-radius,cx,cy+radius)
        paint.shader=RadialGradient(cx-120,cy-140,radius*1.7f,0xff78b6c0.toInt(),0xff163b56.toInt(),Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,radius,paint);paint.shader=null
        fill(c,0xff6b9174.toInt(),listOf(54f to 226f,105f to 191f,175f to 209f,205f to 244f,
            177f to 272f,205f to 310f,162f to 337f,137f to 300f,110f to 293f,82f to 262f))
        fill(c,0xff789978.toInt(),listOf(156f to 365f,215f to 347f,259f to 377f,242f to 420f,
            218f to 451f,208f to 497f,178f to 532f,170f to 480f,148f to 423f))
        paint.shader=LinearGradient(65f,0f,300f,0f,0x0024434d,0x9924434d.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(25f,90f,300f,640f,paint);paint.shader=null
        c.restore()
        // Mineral stippling: stable positions rather than flickering random texture.
        repeat(150) { i ->
            val a=i*2.39996; val rr=sqrt((i+.5)/150)*radius*.92
            val x=cx+cos(a).toFloat()*rr.toFloat(); val y=cy+sin(a).toFloat()*rr.toFloat()
            if(rr>radius*.55 && x>cx) { paint.color=0x22ffe7be; c.drawCircle(x,y,1f+(i%2),paint) }
        }
        paint.color=0xff79beca.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=4f
        c.drawCircle(cx,cy,radius+5,paint); paint.style=Paint.Style.FILL
        // Tiny moving markers in the mantle, not boiling liquid.
        repeat(8) { i ->
            val a=(phase+i/8f)*2*PI; val r=radius*.66f
            paint.color=0x55ffe2a9
            val x=cx+cos(a).toFloat()*r
            if(x>cx) c.drawCircle(x,cy+sin(a).toFloat()*r,3f,paint)
        }
    }
    private fun base(c:Canvas, top:Float=290f) {
        paint.shader=null;paint.color=0x1540312b
        c.drawRoundRect(57f,top+18,969f,681f,22f,22f,paint)
        polygon(c,0xff6e4744.toInt(),45f,top,955f,top,974f,top+18,974f,672f,955f,665f,45f,665f)
        paint.shader=LinearGradient(0f,top,0f,650f,0xffc98864.toInt(),0xff713f42.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(45f,top,955f,665f,24f,24f,paint); paint.shader=null
        repeat(75) { i -> paint.color=0x25ffe1b2; c.drawCircle(65f+(i*127%865),top+20+(i*71%max(1,(630-top).toInt())),2f,paint) }
    }
    private fun shell(c:Canvas,phase:Float) {
        base(c,340f)
        polygon(c,water,45f,230f,470f,230f,470f,290f,45f,290f)
        polygon(c,dark,45f,290f,470f,290f,470f,320f,45f,320f)
        polygon(c,ochre,470f,285f,550f,225f,620f,245f,705f,180f,785f,235f,955f,245f,955f,405f,640f,455f,470f,380f)
        polygon(c,0xff63564d.toInt(),45f,320f,470f,320f,470f,380f,640f,455f,955f,405f,955f,500f,45f,500f)
        line(c,0xfffce1ae.toInt(),3f,45f,320f,470f,320f,470f,380f,640f,455f,955f,405f)
        repeat(4) { i -> convection(c,160f+i*210f,570f,phase+i*.21f) }
        line(c,water,3f,70f,248f,430f,248f)
        waves(c,70f,435f,241f,phase);snow(c,705f,180f,42f)
    }
    private fun convection(c:Canvas,x:Float,y:Float,phase:Float) {
        paint.color=0x35ffd098; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f
        c.drawOval(x-65,y-38,x+65,y+38,paint); paint.style=Paint.Style.FILL
        val a=phase*2*PI; paint.color=0xffeebc8b.toInt()
        c.drawCircle(x+cos(a).toFloat()*65,y+sin(a).toFloat()*38,5f,paint)
    }
    private fun ridge(c:Canvas,phase:Float) {
        base(c,350f)
        polygon(c,water,45f,195f,955f,195f,955f,310f,570f,295f,500f,240f,430f,295f,45f,310f)
        polygon(c,dark,45f,310f,430f,295f,494f,250f,494f,350f,45f,390f)
        polygon(c,dark,506f,250f,570f,295f,955f,310f,955f,390f,506f,350f)
        for(i in 0..5) {
            val x=90f+i*63f; line(c,0xff7c929c.toInt(),3f,x,320f,x-15,378f)
            line(c,0xff7c929c.toInt(),3f,1000-x,320f,1015-x,378f)
        }
        polygon(c,lava,487f,550f,493f,265f,507f,265f,513f,550f)
        arrow(c,390f,425f,-110f,0f,phase); arrow(c,610f,425f,110f,0f,phase)
        arrow(c,500f,570f,0f,-105f,phase)
        convection(c,260f,565f,phase); convection(c,740f,565f,-phase)
        waves(c,65f,935f,204f,phase)
    }
    private fun subduction(c:Canvas,phase:Float) {
        base(c,355f)
        polygon(c,water,45f,225f,485f,225f,485f,310f,45f,280f)
        polygon(c,ochre,510f,325f,580f,270f,685f,280f,755f,190f,790f,220f,825f,280f,955f,260f,955f,405f,565f,390f)
        polygon(c,dark,45f,280f,460f,295f,515f,340f,720f,540f,695f,570f,485f,375f,440f,335f,45f,325f)
        line(c,0xffe1b781.toInt(),3f,45f,282f,460f,297f,515f,342f,720f,542f)
        polygon(c,lava,750f,465f,761f,238f,775f,222f,780f,460f)
        paint.color=lava;c.drawOval(727f,442f,812f,487f,paint)
        repeat(7) { i -> val t=(phase+i/7f)%1f; paint.color=0xffefd6a4.toInt(); c.drawCircle(505+t*165,352+t*165,4f,paint) }
        arrow(c,215f,375f,95f,0f,phase);arrow(c,865f,425f,-75f,0f,phase)
        repeat(3) { i -> arrow(c,590f+i*45,460f+i*15,12f,-40f,phase) }
        waves(c,65f,435f,237f,phase)
        line(c,0xffead0a3.toInt(),2f,755f,192f,737f,242f,710f,277f)
        line(c,0xff946543.toInt(),2f,789f,222f,798f,260f,826f,280f)
    }
    private fun folds(c:Canvas,stage:Float,phase:Float) {
        base(c,470f)
        val peak=100f+stage*100f
        polygon(c,dark,310f,490f,430f,605f,520f,632f,645f,490f)
        fun surface(x:Float,layer:Int):Float {
            val hump=exp(-((x-500)/220).pow(2))*peak
            return 345f+layer*39-hump+sin(x/62)*stage*20
        }
        for(layer in 4 downTo 0) {
            val points=mutableListOf<Pair<Float,Float>>()
            for(i in 0..100) {
                val x=45f+i*9.1f
                points += x to surface(x,layer)
            }
            for(i in 100 downTo 0) { val x=45f+i*9.1f;points+=x to surface(x,layer+1) }
            fill(c,intArrayOf(ochre,0xffac7e5c.toInt(),0xffd4b58a.toInt(),0xff8c6a61.toInt(),dark)[layer],points)
        }
        line(c,0xffffdab0.toInt(),3f,370f,485f,605f,285f)
        arrow(c,170f,455f,100f,0f,phase);arrow(c,830f,455f,-100f,0f,phase)
        snow(c,500f,surface(500f,0),43f)
    }
    private fun faults(c:Canvas,stage:Float,phase:Float) {
        base(c,410f)
        val drop=stage*105
        for(i in 3 downTo 0) {
            val color=intArrayOf(ochre,0xffb38163.toInt(),0xffd7bb91.toInt(),dark)[i]
            val y=235f+i*38;val b=y+38
            val leftTop=325f+(y-235)*100/215;val leftBottom=325f+(b-235)*100/215
            val rightTop=675f-(y-235)*85/215;val rightBottom=675f-(b-235)*85/215
            polygon(c,color,45f,y,leftTop,y,leftBottom,b,45f,b)
            polygon(c,color,leftTop,y+drop,rightTop,y+drop,rightBottom,b+drop,leftBottom,b+drop)
            polygon(c,color,rightTop,y,955f,y,955f,b,rightBottom,b)
        }
        line(c,lava,3f,325f,235f,425f,450f);line(c,lava,3f,675f,235f,590f,450f)
        arrow(c,200f,505f,-90f,0f,phase);arrow(c,800f,505f,90f,0f,phase)
    }
    private fun transform(c:Canvas,stage:Float,phase:Float) {
        base(c,465f)
        val shift=(stage-.5f)*100
        polygon(c,ochre,100f,290f-shift,485f,205f-shift,485f,440f-shift,100f,525f-shift)
        polygon(c,0xffbc8d72.toInt(),510f,205f+shift,900f,290f+shift,900f,525f+shift,510f,440f+shift)
        for(i in 0..4) {
            line(c,0xffece0b6.toInt(),3f,120f,335f+i*32-shift,470f,258f+i*32-shift)
            line(c,0xffece0b6.toInt(),3f,525f,258f+i*32+shift,880f,335f+i*32+shift)
        }
        line(c,lava,4f,497f,190f,497f,460f)
        arrow(c,405f,395f,0f,-100f,phase);arrow(c,585f,285f,0f,100f,phase)
    }
    private fun hotspot(c:Canvas,phase:Float) {
        base(c,370f)
        polygon(c,water,45f,230f,955f,230f,955f,310f,45f,310f)
        polygon(c,dark,45f,310f,955f,310f,955f,375f,45f,375f)
        for(i in 0..4) {
            val x=230f+i*135;val peak=50f+i*12
            polygon(c,shade(ochre,.6f+i*.09f),x-55,310f,x,310f-peak,x+55,310f)
        }
        paint.shader=RadialGradient(770f,580f,110f,lava,0x00ffc078,Shader.TileMode.CLAMP)
        c.drawCircle(770f,580f,110f,paint);paint.shader=null
        polygon(c,lava,743f,570f,758f,240f,773f,225f,792f,570f)
        arrow(c,480f,418f,-120f,0f,phase);arrow(c,767f,560f,0f,-100f,phase)
        waves(c,65f,935f,241f,phase)
    }
    private fun landscape(c:Canvas,stage:Float,phase:Float) {
        base(c,515f)
        val height=215f-stage*115
        polygon(c,ochre,45f,515f,210f,435f,350f,515f-height,480f,435f-stage*40,610f,515f-height*.8f,755f,460f,955f,500f,955f,590f,45f,590f)
        polygon(c,0xffad7c61.toInt(),45f,560f,350f,495f-height*.3f,470f,535f,610f,525f-height*.3f,955f,550f,955f,590f,45f,590f)
        snow(c,350f,515f-height,44f);snow(c,610f,515f-height*.8f,32f)
        line(c,0xffebe2cc.toInt(),2f,350f,515f-height,329f,460f,296f,482f)
        line(c,0xff986c50.toInt(),3f,610f,515f-height*.8f,656f,450f,695f,466f)
        line(c,water,6f,490f,422f-stage*32,570f,492f,675f,522f,890f,530f)
        repeat(15) { i -> val t=(phase+i/15f)%1f;paint.color=ochre;c.drawCircle(530f+t*340,470f+t*70,3f,paint) }
        arrow(c,350f,620f,0f,-35f,phase)
    }
    private fun strata(c:Canvas,stage:Float,archive:Boolean) {
        base(c,550f)
        val colors=intArrayOf(0xff7f6c63.toInt(),0xffc0a889.toInt(),0xffb48460.toInt(),0xffd6c5a0.toInt(),0xff8b8171.toInt())
        for(i in 0..4) {
            val reveal=if(archive) 1f else ((stage*5-i).coerceIn(0f,1f))
            if(reveal==0f) continue
            val bottom=570f-i*67; val top=bottom-62*reveal
            val slope=if(archive && i<3) 55f else 0f
            polygon(c,colors[i],65f,top+slope,930f,top-slope,930f,bottom-slope,65f,bottom+slope)
            repeat(28) { n -> paint.color=0x44fff0cc;c.drawCircle(85f+n*30,top+(bottom-top)*.5f+slope*(1-n/14f),2.5f,paint) }
        }
        if(archive) {
            // Younger beds truncate the tilted older sequence along a horizontal surface.
            polygon(c,0xffd6c5a0.toInt(),65f,285f,930f,285f,930f,366f,65f,366f)
            polygon(c,0xff8b8171.toInt(),65f,230f,930f,230f,930f,285f,65f,285f)
            line(c,lava,3f,65f,366f,930f,366f)
            polygon(c,0xffd47350.toInt(),700f,570f,655f,310f,672f,310f,720f,570f)
            line(c,0xffefd9b3.toInt(),3f,410f,570f,480f,395f)
        } else {
            paint.color=water;paint.alpha=90;c.drawRect(65f,190f,930f,570f-stage*335,paint);paint.alpha=255
            if(stage>.58f) {
                paint.color=0xff6c665d.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
                c.drawOval(330f,383f,375f,411f,paint)
                c.drawArc(338f,386f,368f,408f,0f,300f,false,paint);paint.style=Paint.Style.FILL
            }
        }
    }
    private fun cycle(c:Canvas,phase:Float) {
        base(c,500f)
        polygon(c,ochre,50f,490f,260f,230f,345f,340f,475f,440f,650f,460f,950f,470f,950f,530f,50f,530f)
        polygon(c,water,670f,350f,950f,350f,950f,470f,670f,470f)
        polygon(c,0xffaaa198.toInt(),115f,510f,155f,410f,255f,420f,300f,510f)
        polygon(c,lava,420f,650f,450f,520f,490f,495f,535f,530f,560f,650f)
        polygon(c,0xff7f706b.toInt(),615f,555f,850f,555f,910f,630f,650f,630f)
        repeat(7) { i ->line(c,0xffbbab9a.toInt(),3f,650f,564f+i*8,860f,579f+i*8) }
        arrow(c,355f,350f,100f,80f,phase);arrow(c,790f,485f,0f,58f,phase)
        arrow(c,635f,637f,-70f,-5f,phase);arrow(c,180f,600f,0f,-65f,phase)
        snow(c,260f,230f,48f);waves(c,690f,930f,361f,phase)
    }
    private fun waves(c:Canvas,left:Float,right:Float,y:Float,phase:Float) {
        paint.shader=null;paint.color=0x669fe3e6;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
        path.reset()
        for(i in 0..60) {
            val x=left+(right-left)*i/60;val yy=y+sin(i*.48f+phase*PI.toFloat()*2)*2.5f
            if(i==0) path.moveTo(x,yy) else path.lineTo(x,yy)
        }
        c.drawPath(path,paint);paint.style=Paint.Style.FILL
    }
    private fun snow(c:Canvas,x:Float,y:Float,size:Float) {
        polygon(c,0xffe9f0e9.toInt(),x-size,y+size*.9f,x,y,x+size,y+size*.85f,
            x+size*.42f,y+size*.66f,x+size*.12f,y+size*.75f,x-size*.22f,y+size*.58f,x-size*.5f,y+size*.8f)
        polygon(c,0xffb5c9c9.toInt(),x,y,x+size,y+size*.85f,x+size*.42f,y+size*.66f,x+size*.12f,y+size*.75f)
    }
    private fun tint(color:Int,amount:Float)=Color.rgb(
        (Color.red(color)+(255-Color.red(color))*amount).toInt(),
        (Color.green(color)+(255-Color.green(color))*amount).toInt(),
        (Color.blue(color)+(255-Color.blue(color))*amount).toInt())
    private fun shade(color:Int,factor:Float)=Color.rgb((Color.red(color)*factor).toInt().coerceIn(0,255),(Color.green(color)*factor).toInt().coerceIn(0,255),(Color.blue(color)*factor).toInt().coerceIn(0,255))
}
