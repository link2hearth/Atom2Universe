package com.Atom2Universe.app.periodic

import android.graphics.*
import kotlin.math.*

/** Familiar silhouettes with material colours independent of the rarity frame. */
internal class MetalElementCardArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val silver = 0xFFD3E1EA.toInt()
    private fun ink(color: Int, width: Float = 0f) {
        p.reset(); p.isAntiAlias = true; p.color = color
        p.style = if (width > 0) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, w: Float = 0f) {
        ink(color,w); c.drawOval(x-rx,y-ry,x+rx,y+ry,p)
    }
    private fun line(c: Canvas, x: Float,y: Float,xx: Float,yy: Float,color: Int,w: Float=2f) {
        ink(color,w); c.drawLine(x,y,xx,yy,p)
    }
    private fun metal(left: Float,right: Float,gold: Boolean=false) {
        ink(Color.WHITE)
        p.shader=LinearGradient(left,0f,right,0f,if(gold) intArrayOf(0xFF91602B.toInt(),0xFFFFE8A0.toInt(),0xFFC08A35.toInt())
            else intArrayOf(0xFF52677B.toInt(),0xFFF0F7F5.toInt(),0xFF8197A8.toInt()),null,Shader.TileMode.CLAMP)
    }
    fun draw(c: Canvas,z: Int,t: Float) {
        when(z) {
            19 -> flame(c,t)
            31 -> melting(c,t)
            47 -> coins(c,t)
            50 -> can(c,t)
            78 -> ring(c,t)
            79 -> bars(c,t)
        }
    }
    private fun flame(c: Canvas,t: Float) {
        // Lilac flame test of potassium salts, above a laboratory burner.
        val sway=sin(t*2)*5
        ink(0x559D67FF); p.shader=RadialGradient(180f,191f,86f,0x559D67FF,0x009D67FF,Shader.TileMode.CLAMP)
        c.drawCircle(180f,191f,86f,p)
        path.reset(); path.moveTo(180f,245f)
        path.cubicTo(121f,230f,143f,189f,159f,169f)
        path.quadTo(154f,195f,169f,195f)
        path.cubicTo(189f,171f,169f,144f,190f+sway,119f)
        path.cubicTo(180f,162f,226f,188f,214f,220f)
        path.quadTo(207f,242f,180f,245f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(0f,124f,0f,247f,0xFFE0B4FF.toInt(),0xFF8355D8.toInt(),Shader.TileMode.CLAMP); c.drawPath(path,p)
        path.reset(); path.moveTo(179f,240f); path.cubicTo(161f,224f,190f,208f,184f+sway,187f)
        path.cubicTo(211f,215f,195f,239f,179f,240f); ink(0xFFF2DFFF.toInt()); c.drawPath(path,p)
        metal(167f,193f); c.drawRoundRect(167f,245f,193f,286f,3f,3f,p)
        oval(c,180f,246f,15f,4f,0xFF43506B.toInt())
        oval(c,180f,291f,44f,9f,0xFF8094A6.toInt())
        line(c,192f,275f,213f,275f,silver,5f)
        ink(0xFF354354.toInt(),4f); path.reset(); path.moveTo(214f,275f); path.quadTo(244f,266f,260f,292f); c.drawPath(path,p)
    }
    private fun melting(c: Canvas,t: Float) {
        // A sculpted sample softening into a pool: gallium melts at about 29.8 °C.
        oval(c,180f,281f,87f,17f,0xFF293D51.toInt())
        metal(97f,265f); c.drawOval(97f,254f,264f,289f,p)
        path.reset(); path.moveTo(143f,256f); path.lineTo(133f,190f); path.lineTo(176f,160f)
        path.lineTo(222f,179f); path.lineTo(227f,229f)
        path.cubicTo(232f,261f,209f,255f,210f,271f)
        path.cubicTo(190f,282f,174f,263f,143f,256f); path.close()
        metal(135f,228f); c.drawPath(path,p)
        line(c,135f,191f,180f,208f,0xFFFFFFFF.toInt(),1.5f)
        line(c,180f,208f,220f,181f,silver,1.5f)
        line(c,180f,208f,183f,250f,0xFF7B90A3.toInt(),1.4f)
        oval(c,236f,248f+sin(t)*4,5f,7f,silver)
        ink(0x88FFFFFF.toInt(),1.5f); c.drawArc(110f,259f,249f,283f,15f,125f,false,p)
    }
    private fun coins(c: Canvas,t: Float) {
        for(i in 0..4) {
            val y=275f-i*10
            metal(105f,203f); c.drawRoundRect(105f,y-8,203f,y+5,3f,3f,p)
            oval(c,154f,y-8,49f,12f,silver)
            oval(c,154f,y-8,40f,8f,0xFF8199AB.toInt(),1f)
            for(j in 0..8) line(c,112f+j*10,y-3,112f+j*10,y+2,0xFF6D8298.toInt(),1f)
        }
        c.save(); c.rotate(-18f,223f,222f)
        metal(180f,263f); c.drawOval(181f,164f,265f,277f,p)
        oval(c,223f,220f,35f,49f,0xFF607A90.toInt(),2f)
        // Embossed laurel branches rather than a tiny unreadable inscription.
        for(s in floatArrayOf(-1f,1f)) {
            ink(0xFF6F879B.toInt(),2f); path.reset(); path.moveTo(223f,254f)
            path.quadTo(223f+s*36,227f,223f+s*17,188f); c.drawPath(path,p)
            for(i in 0..4) oval(c,223f+s*(16+sin(i*.55f)*9),243f-i*11,4f,7f,silver)
        }
        oval(c,223f,217f,8f,12f,0xFF9DB1BF.toInt()); c.restore()
        line(c,231f,174f,237f,181f,Color.WHITE,1.5f)
    }
    private fun can(c: Canvas,t: Float) {
        metal(119f,241f); c.drawRect(119f,166f,241f,278f,p)
        oval(c,180f,278f,61f,15f,0xFF91A6B8.toInt())
        for(y in floatArrayOf(186f,201f,246f,262f)) {
            ink(0xFF5A7187.toInt(),2f); c.drawArc(119f,y-12,241f,y+12,0f,180f,false,p)
            ink(0xFFC5D6DF.toInt(),1f); c.drawArc(120f,y-9,240f,y+15,0f,180f,false,p)
        }
        oval(c,180f,166f,61f,17f,silver)
        oval(c,180f,166f,54f,12f,0xFF6F8598.toInt(),1.5f)
        oval(c,180f,166f,19f,6f,0xFF455C72.toInt(),3f)
        line(c,180f,161f,180f,174f,silver,4f)
        line(c,130f,212f,130f,237f,0xBBFFFFFF.toInt(),2f)
    }
    private fun ring(c: Canvas,t: Float) {
        c.save(); c.rotate(-24f,180f,223f)
        oval(c,180f,225f,60f,69f,0xFF53687F.toInt(),15f)
        oval(c,180f,225f,60f,69f,silver,9f)
        oval(c,180f,225f,65f,74f,0xFFF5F9F7.toInt(),1.5f)
        for(x in floatArrayOf(161f,199f)) line(c,x,167f,x,148f,silver,4f)
        path.reset(); path.moveTo(151f,142f); path.lineTo(166f,124f); path.lineTo(194f,124f)
        path.lineTo(209f,142f); path.lineTo(180f,175f); path.close()
        ink(0xFFC4E5EB.toInt()); c.drawPath(path,p)
        line(c,151f,142f,209f,142f,Color.WHITE,1f)
        line(c,166f,124f,180f,175f,Color.WHITE,1f)
        line(c,194f,124f,180f,175f,Color.WHITE,1f)
        c.restore()
        val r=5f+sin(t)*2; line(c,238f-r,204f,238f+r,204f,Color.WHITE,1f)
        line(c,238f,204f-r,238f,204f+r,Color.WHITE,1f)
    }
    private fun bars(c: Canvas,t: Float) {
        fun bar(x: Float,y: Float) {
            path.reset(); path.moveTo(x,y); path.lineTo(x+88,y); path.lineTo(x+103,y+26)
            path.lineTo(x-15,y+26); path.close(); metal(x-15,x+103,true); c.drawPath(path,p)
            path.reset(); path.moveTo(x,y); path.lineTo(x+19,y-21); path.lineTo(x+74,y-21)
            path.lineTo(x+88,y); path.close(); ink(0xFFEFC96C.toInt()); c.drawPath(path,p)
            line(c,x,y,x+88,y,0xFFFFEAB2.toInt(),1.5f)
            oval(c,x+44,y-8,12f,4f,0xFFB78938.toInt(),1f)
        }
        bar(86f,250f); bar(192f,250f); bar(140f,207f)
        val x=179f+sin(t)*22; line(c,x-5,186f,x+5,186f,Color.WHITE,1f)
        line(c,x,181f,x,191f,Color.WHITE,1f)
    }
}
