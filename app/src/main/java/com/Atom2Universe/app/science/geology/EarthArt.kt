package com.Atom2Universe.app.science.geology

import android.graphics.*
import kotlin.math.*

/** Procedural educational sections. Coordinates are 1000 × 760; motion is illustrative. */
internal class EarthArt {
    private val terrain = EarthTerrainArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ochre = 0xffdfb780.toInt()
    private val mantle = 0xffb56c58.toInt()
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
    fun render(c: Canvas, scene: EarthScene, stage: Float, phase: Float, trueScale: Boolean) {
        paint.reset(); paint.isAntiAlias = true
        if (scene == EarthScene.GLOBE) globe(c, trueScale, phase)
        else terrain.render(c, scene, stage, phase)
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
        land(c,0xff7fba99.toInt(),54f,226f,68f,211f,97f,205f,114f,184f,143f,194f,166f,201f,
            184f,220f,214f,230f,219f,244f,191f,261f,177f,270f,184f,292f,205f,307f,
            203f,320f,192f,315f,181f,306f,172f,316f,158f,330f,149f,314f,139f,296f,
            117f,292f,106f,278f,89f,271f,77f,251f,66f,242f)
        land(c,0xff87b889.toInt(),165f,348f,185f,341f,211f,353f,223f,365f,251f,371f,
            262f,388f,249f,408f,241f,432f,224f,448f,222f,467f,210f,483f,204f,510f,
            187f,533f,180f,526f,183f,499f,175f,481f,171f,459f,156f,438f,151f,415f,145f,398f,152f,374f)
        // Wispy cloud fronts curve with the sphere and drift without obscuring its geography.
        repeat(7) { front ->
            val x=40f+(front*67%155)+sin(phase*2*PI+front).toFloat()*7
            val y=180f+front*53
            repeat(5) { strand ->
                paint.color=Color.argb(35+strand*13,233,244,237)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=1.1f+strand*.35f
                path.reset()
                for(i in 0..50) {
                    val t=i/50f
                    val xx=x+t*(62+front%3*24)
                    val yy=y+sin(t*4.3f+front)*8+sin(t*12+strand)*1.8f+strand*2
                    if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
                }
                c.drawPath(path,paint)
            }
        }
        paint.style=Paint.Style.FILL
        polygon(c,0xffd9eeeb.toInt(),183f,122f,235f,97f,294f,90f,278f,130f,235f,145f,211f,132f)
        paint.color=-1
        paint.shader=RadialGradient(158f,227f,410f,intArrayOf(0x10d1f5e0,0x0024434d,0xb8082339.toInt()),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
        c.drawRect(25f,90f,300f,640f,paint);paint.shader=null
        c.restore()
        // Mineral stippling: stable positions rather than flickering random texture.
        repeat(500) { i ->
            val a=i*2.39996; val rr=sqrt((i+.5)/500)*radius*.92
            val x=cx+cos(a).toFloat()*rr.toFloat(); val y=cy+sin(a).toFloat()*rr.toFloat()
            if(rr>radius*.55 && x>cx) { paint.color=if(i%2==0) 0x22ffe7be else 0x18794230; c.drawCircle(x,y,.5f+(i%3)*.3f,paint) }
        }
        paint.color=0x8079beca.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=2f
        c.drawCircle(cx,cy,radius+2,paint); paint.style=Paint.Style.FILL
        c.save();c.clipRect(cx,cy-radius,cx+radius,cy+radius)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f
        repeat(22) { i ->
            val r=radius*(.24f+i*.012f)
            paint.color=if(i%2==0) 0x35fff0ad else 0x28a04e38
            c.drawArc(cx-r,cy-r,cx+r,cy+r,-80f+i%4*7,140f,false,paint)
        }
        paint.style=Paint.Style.FILL
        glow(c,cx,cy,radius*.19f,0xffffdf8b.toInt(),phase)
        c.restore()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;paint.color=0x66ffe7bd
        for(i in 1 until radii.size) c.drawArc(cx-radius*radii[i],cy-radius*radii[i],cx+radius*radii[i],cy+radius*radii[i],-90f,180f,false,paint)
        paint.style=Paint.Style.FILL
        // Tiny moving markers in the mantle, not boiling liquid.
        repeat(8) { i ->
            val a=(phase+i/8f)*2*PI; val r=radius*.66f
            paint.color=0x55ffe2a9
            val x=cx+cos(a).toFloat()*r
            if(x>cx) c.drawCircle(x,cy+sin(a).toFloat()*r,3f,paint)
        }
    }
    private fun glow(c:Canvas,x:Float,y:Float,radius:Float,color:Int,phase:Float) {
        val radiusNow=radius*(.94f+.06f*sin(phase*2*PI).toFloat())
        val transparent=color and 0x00ffffff
        paint.alpha=255
        paint.shader=RadialGradient(x,y,radiusNow,intArrayOf((color and 0x00ffffff) or 0x77000000,transparent),null,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,radiusNow,paint);paint.shader=null
    }
    private fun land(c:Canvas,color:Int,vararg vertices:Float) {
        path.reset()
        val count=vertices.size/2
        path.moveTo((vertices[0]+vertices[vertices.size-2])/2,(vertices[1]+vertices.last())/2)
        repeat(count) { i ->
            val next=(i+1)%count
            path.quadTo(vertices[i*2],vertices[i*2+1],(vertices[i*2]+vertices[next*2])/2,(vertices[i*2+1]+vertices[next*2+1])/2)
        }
        path.close();path.computeBounds(bounds,true)
        paint.color=color;paint.shader=LinearGradient(bounds.left,bounds.top,bounds.right,bounds.bottom,tint(color,.18f),shade(color,.66f),Shader.TileMode.CLAMP)
        c.drawPath(path,paint);paint.shader=null
        val coast=Path(path)
        c.save();c.clipPath(coast)
        repeat(260) { i ->
            val fx=(sin(i*127.1)*43758.5453).let { it-floor(it) }.toFloat()
            val fy=(sin(i*311.7)*18345.1337).let { it-floor(it) }.toFloat()
            val x=bounds.left+fx*bounds.width();val y=bounds.top+fy*bounds.height()
            paint.color=if(i%3==0) 0x55798a51 else 0x4049744c
            c.drawOval(x,y,x+2.3f,y+1.2f,paint)
        }
        repeat(13) { ridge ->
            paint.color=if(ridge%2==0) 0x5098805c else 0x46cbbf8c
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.1f
            path.reset()
            repeat(50) { i ->
                val y=bounds.top+i/49f*bounds.height()
                val x=bounds.left+14+ridge*1.7f+sin(y*.035f)*9+sin(y*.15f+ridge)*1.8f
                if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            c.drawPath(path,paint)
        }
        c.restore();paint.style=Paint.Style.STROKE;paint.strokeWidth=.9f;paint.color=0x88c3dfae.toInt()
        c.drawPath(coast,paint);paint.style=Paint.Style.FILL
    }
    private fun tint(color:Int,amount:Float)=Color.rgb(
        (Color.red(color)+(255-Color.red(color))*amount).toInt(),
        (Color.green(color)+(255-Color.green(color))*amount).toInt(),
        (Color.blue(color)+(255-Color.blue(color))*amount).toInt())
    private fun shade(color:Int,factor:Float)=Color.rgb((Color.red(color)*factor).toInt().coerceIn(0,255),(Color.green(color)*factor).toInt().coerceIn(0,255),(Color.blue(color)*factor).toInt().coerceIn(0,255))
}
