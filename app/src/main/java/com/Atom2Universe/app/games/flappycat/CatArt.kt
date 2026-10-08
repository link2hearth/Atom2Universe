package com.Atom2Universe.app.games.flappycat

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.R
import kotlin.math.*

/** Illustrations vectorielles animées, sans sprites ni décodage de PNG. */
internal class CatArt(private val context: Context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ink = Color.rgb(54, 43, 72)
    private val cream = Color.rgb(255, 236, 195)
    private val orange = Color.rgb(246, 166, 91)
    private val sky = LinearGradient(0f, 0f, 0f, 560f, intArrayOf(0xff444674.toInt(), 0xffce8197.toInt(), 0xffffce9a.toInt()), null, Shader.TileMode.CLAMP)
    private fun paint(color: Int, stroke: Float = 0f): Paint {
        p.shader = null; p.color = color; p.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth = stroke; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        return p
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) { c.drawOval(x-rx,y-ry,x+rx,y+ry,paint(color)) }
    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, width: Float = 2f) { c.drawLine(x,y,xx,yy,paint(color,width)) }
    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        path.reset(); path.moveTo(points[0],points[1]); var i = 2
        while (i < points.size) { path.lineTo(points[i], points[i+1]); i += 2 }; path.close(); c.drawPath(path,paint(color))
    }
    fun background(c: Canvas, distance: Float, time: Float) {
        paint(Color.WHITE); p.shader = sky; c.drawRect(0f,0f,420f,560f,p); p.shader = null
        oval(c,326f,125f,49f,49f,0x18fff3ce); oval(c,326f,125f,37f,37f,0xffffe3b2.toInt())
        repeat(17) { i -> val x = (i * 73f + 25f) % 420f; val y = 27f + (i * 37f) % 145f
            oval(c,x,y,1.2f,1.2f,Color.argb((100 + sin(time + i) * 65).toInt(),255,239,222)) }
        repeat(6) { i -> val x = ((i * 103f - distance * .12f) % 620f + 620f) % 620f - 100f; val y = 165f + i % 3 * 44f
            oval(c,x,y,49f,8f,0x30ffe2ce); oval(c,x+13f,y-6f,22f,11f,0x30ffe2ce) }
        repeat(2) { layer ->
            val spacing = if (layer == 0) 58f else 83f
            val offset = distance * if (layer == 0) .18f else .4f
            repeat(10) { i ->
                val index = floor(offset / spacing).toInt() + i
                val x = i * spacing - offset % spacing - spacing
                val roof = (if (layer == 0) 351f else 410f) - ((index * 31) % 67)
                val color = if (layer == 0) 0xff77718c.toInt() else 0xff504e70.toInt()
                c.drawRect(x,roof,x+spacing-5f,510f,paint(color))
                polygon(c,color,x-4f,roof,x+spacing/2f,roof-18f,x+spacing,roof)
                repeat(3) { row -> repeat(3) { col ->
                    if ((index+row+col)%3 != 0) c.drawRoundRect(x+9f+col*15f,roof+13f+row*24f,x+15f+col*15f,roof+24f+row*24f,2f,2f,paint(if (layer == 0) 0x55ffdbab else 0xbbffd39a.toInt()))
                } }
                c.drawRect(x+9f,roof-22f,x+18f,roof,paint(color))
            }
        }
        c.drawRect(0f,501f,420f,560f,paint(0xff30334e.toInt()))
        c.drawRect(0f,501f,420f,508f,paint(0xfff4b49b.toInt()))
        repeat(17) { i -> val x = i*30f-distance%30f; line(c,x,510f,x-10f,560f,0xff49445f.toInt(),2f) }
        line(c,0f,534f,420f,534f,0xff49445f.toInt())
    }
    fun cat(c: Canvas, x: Float, y: Float, time: Float, velocity: Float, jump: Float, electric: Boolean, immune: Boolean) {
        c.save(); c.translate(x,y)
        c.rotate(if (electric) sin(time*70f)*7f else (velocity*.035f).coerceIn(-16f,22f))
        c.scale(1f-jump*.12f,1f+jump*.16f)
        if (immune) oval(c,0f,0f,37f+sin(time*12f)*2f,32f,0x35fff4cf)
        path.reset(); path.moveTo(-17f,6f); path.cubicTo(-43f,12f,-43f,-23f,-30f,-13f+sin(time*8f)*6f)
        c.drawPath(path,paint(ink,9f)); c.drawPath(path,paint(orange,6f))
        val fur = if (electric && (time*20f).toInt()%2 == 0) cream else orange
        oval(c,-4f,4f,23f,17f,ink); oval(c,-4f,3f,21f,15f,fur)
        oval(c,2f,8f,13f,10f,cream)
        val feet = sin(time*12f)*3f
        oval(c,-14f,18f+feet,7f,4f,ink); oval(c,12f,17f-feet,7f,4f,ink)
        oval(c,-14f,17f+feet,5f,3f,fur); oval(c,12f,16f-feet,5f,3f,fur)
        polygon(c,ink,-11f,-10f,-12f,-32f,3f,-23f,17f,-30f,24f,-9f)
        polygon(c,fur,-9f,-10f,-9f,-28f,4f,-19f,16f,-26f,21f,-9f)
        polygon(c,0xffdb8290.toInt(),-7f,-23f,-6f,-13f,0f,-18f)
        polygon(c,0xffdb8290.toInt(),15f,-22f,11f,-16f,19f,-12f)
        oval(c,7f,-7f,20f,17f,ink); oval(c,7f,-8f,18f,15f,fur)
        oval(c,11f,0f,12f,7f,cream)
        for (ex in floatArrayOf(2f,17f)) {
            oval(c,ex,-9f,5.5f,6.5f,Color.WHITE)
            if (electric) { line(c,ex-3f,-12f,ex+3f,-6f,ink); line(c,ex+3f,-12f,ex-3f,-6f,ink) }
            else { oval(c,ex+1.5f,-8f,2.8f,4f,ink); oval(c,ex+2f,-10f,1f,1.3f,Color.WHITE) }
        }
        polygon(c,0xff9d5466.toInt(),8f,-2f,14f,-2f,11f,1f)
        line(c,11f,1f,11f,4f,ink,1.3f)
        line(c,-7f,-1f,-17f,-4f,ink,1.2f); line(c,-7f,2f,-18f,3f,ink,1.2f)
        line(c,24f,-1f,32f,-4f,ink,1.2f); line(c,24f,2f,33f,3f,ink,1.2f)
        // Foulard et pans flottants.
        line(c,-8f,9f,20f,11f,0xff62c9c3.toInt(),5f)
        polygon(c,0xff62c9c3.toInt(),-8f,9f,-31f,13f+sin(time*11f)*4f,-25f,4f,-8f,6f)
        if (electric) {
            repeat(9) { i -> val a = i * .698f + time*2f; val xx = cos(a); val yy = sin(a)
                path.reset(); path.moveTo(xx*28f,yy*26f); path.lineTo(xx*40f-yy*5f,yy*38f+xx*5f); path.lineTo(xx*36f+yy*4f,yy*34f-xx*4f); path.lineTo(xx*48f,yy*46f)
                c.drawPath(path,paint(0xff79f4ff.toInt(),3f)); c.drawPath(path,paint(Color.WHITE,1f)) }
            repeat(5) { i -> line(c,-18f+i*9f,-24f,-22f+i*10f,-37f,cream,2f) }
        }
        c.restore()
    }
    fun bird(c: Canvas, x: Float, y: Float, time: Float, variant: Int) {
        val color = when(variant) { 0 -> 0xff9e9edc.toInt(); 1 -> 0xffda8b9e.toInt(); else -> 0xff72b5bb.toInt() }
        c.save(); c.translate(x,y); c.rotate(sin(time*3f)*7f)
        polygon(c,ink,16f,4f,36f,-7f,30f,8f,35f,14f,14f,12f)
        polygon(c,color,17f,5f,31f,-3f,26f,7f,30f,11f,17f,10f)
        oval(c,0f,0f,24f,20f,ink); oval(c,0f,-1f,22f,18f,color)
        oval(c,-7f,6f,14f,11f,cream)
        c.save(); c.translate(7f,0f); c.rotate(sin(time*13f)*48f-20f)
        oval(c,10f,-8f,10f,22f,ink); oval(c,10f,-9f,8f,20f,color)
        line(c,10f,-20f,10f,-5f,0x88604d86.toInt()); line(c,15f,-16f,14f,-4f,0x88604d86.toInt()); c.restore()
        polygon(c,color,-4f,-15f,0f,-28f,5f,-22f,10f,-27f,12f,-14f)
        // Sourcil abaissé côté bec : l'ancienne pente donnait un air soucieux.
        oval(c,-12f,-5f,8f,8f,Color.WHITE)
        oval(c,-16f,-3f,3.4f,4.5f,ink); oval(c,-17f,-5f,1f,1.2f,Color.WHITE)
        polygon(c,color,-22f,-16f,-3f,-20f,-4f,-14f,-21f,-6f)
        line(c,-22f,-7f,-5f,-16f,ink,4f)
        line(c,-3f,-18f,1f,-20f,ink,2f)
        // Bec entrouvert et commissure relevée : un petit grognement de défi.
        polygon(c,ink,-21f,-1f,-37f,4f,-23f,7f,-32f,12f,-18f,10f)
        polygon(c,0xffffc468.toInt(),-22f,1f,-32f,4f,-21f,5f)
        polygon(c,0xffed9a43.toInt(),-22f,8f,-28f,11f,-19f,9f)
        line(c,-21f,7f,-16f,5f,ink,1.6f)
        oval(c,-17f,8f,4f,2f,0x99ef9aa6.toInt())
        line(c,-5f,18f,-9f,23f,ink); line(c,6f,18f,2f,23f,ink)
        c.restore()
    }
    fun cable(c: Canvas, cable: FlappyCatView.Cable, time: Float) {
        for (top in listOf(true, false)) {
            path.reset()
            for (i in 0..16) {
                val t = i / 16f
                if (i == 0) path.moveTo(cable.x(t), cable.y(t, top))
                else path.lineTo(cable.x(t), cable.y(t, top))
            }
            c.drawPath(path,paint(0x3379f4ff,12f))
            c.drawPath(path,paint(ink,6f))
            c.drawPath(path,paint(0xff74cbd1.toInt(),2f))
            path.reset()
            for (i in 0..32) {
                val t = i / 32f
                val arc = if (i == 0 || i == 32) 0f else sin(i * 2.3f + time * 25f) * 3f
                val y = cable.y(t, top) + arc
                if (i == 0) path.moveTo(cable.x(t), y) else path.lineTo(cable.x(t), y)
            }
            c.drawPath(path,paint(0xffb3fbff.toInt(),1.8f))
            val pulse = (time * .8f + if (top) 0f else .5f) % 1f
            val x = cable.x(pulse); val y = cable.y(pulse, top)
            oval(c,x,y,5f,5f,0x55a4faff)
            line(c,x-5f,y-7f,x+1f,y-2f,Color.WHITE,1.5f)
            line(c,x+1f,y-2f,x-2f,y+3f,Color.WHITE,1.5f)
            line(c,x-2f,y+3f,x+5f,y+8f,Color.WHITE,1.5f)
        }
    }
    fun pole(c: Canvas, x: Float, gap: Float) {
        for (top in listOf(true,false)) {
            val from = if(top) 78f else gap+94f; val to = if(top) gap-94f else 500f
            line(c,x,from,x,to,ink,10f); line(c,x-2f,from,x-2f,to,0xffdc9980.toInt(),3f)
            val end = if(top) to else from
            c.drawRoundRect(x-12f,end-8f,x+12f,end+8f,4f,4f,paint(ink))
            line(c,x-8f,end-3f,x+8f,end-3f,0xff76d9d7.toInt(),3f)
            line(c,x-8f,end+3f,x+8f,end+3f,0xff76d9d7.toInt(),3f)
        }
        // Repères du passage sûr : ils suivent exactement les limites de collision.
        for (y in floatArrayOf(gap-77f,gap+77f)) { line(c,x-8f,y,x,y+4f,0x997be0d2.toInt()); line(c,x,y+4f,x+8f,y,0x997be0d2.toInt()) }
    }
    fun particles(c: Canvas, sparks: List<FlappyCatView.Spark>) {
        for(s in sparks) { val color = (s.color and 0x00ffffff) or ((min(s.life*2f,1f)*255).toInt() shl 24)
            oval(c,s.x,s.y,2.5f,2.5f,color) }
    }
    private fun text(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int = cream, center: Boolean = true) {
        paint(color); p.typeface = Typeface.create("sans-serif-rounded",Typeface.BOLD); p.textAlign = if(center) Paint.Align.CENTER else Paint.Align.LEFT
        p.textSize = size; c.drawText(text,x,y,p)
    }
    fun hud(c: Canvas, score: Int, best: Int, hearts: Int) {
        c.drawRoundRect(14f,12f,406f,69f,19f,19f,paint(0xcc30334e.toInt()))
        text(c,context.getString(R.string.cat_score,score),29f,37f,20f,center=false)
        text(c,context.getString(R.string.cat_best,best),29f,55f,11f,0xffc8bbd5.toInt(),false)
        repeat(3) { i -> val x = 323f+i*27f; val color = if(i < hearts) 0xffff9ea7.toInt() else 0xff655c79.toInt()
            oval(c,x-4f,36f,6f,6f,color); oval(c,x+4f,36f,6f,6f,color); polygon(c,color,x-10f,37f,x+10f,37f,x,49f) }
    }
    fun panel(c: Canvas, title: Int, body: Int, action: Int, extra: String? = null) {
        c.drawRect(0f,78f,420f,560f,paint(0x44302d50))
        c.drawRoundRect(29f,309f,391f,480f,24f,24f,paint(0xef30334e.toInt()))
        text(c,context.getString(title),210f,342f,26f)
        val lines = context.getString(body).split('\n')
        lines.forEachIndexed { i, s -> text(c,s,210f,369f+i*20f,14f,0xffe0d2df.toInt()) }
        if(extra != null) text(c,extra,210f,412f,17f)
        c.drawRoundRect(49f,430f,371f,465f,16f,16f,paint(0xff81dbce.toInt()))
        text(c,context.getString(action),210f,453f,16f,ink)
    }
}
