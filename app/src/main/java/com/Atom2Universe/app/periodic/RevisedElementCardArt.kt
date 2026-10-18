package com.Atom2Universe.app.periodic

import android.graphics.*
import kotlin.math.*

/** Readable object silhouettes for revised cards and the Z=21..30 pass. */
internal class RevisedElementCardArt {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private fun ink(color: Int,w: Float=0f) {
        p.reset(); p.isAntiAlias=true; p.color=color; p.strokeWidth=w
        p.style=if(w>0) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
    }
    private fun line(c: Canvas,x: Float,y: Float,xx: Float,yy: Float,color: Int,w: Float=1f) {
        ink(color,w); c.drawLine(x,y,xx,yy,p)
    }
    private fun oval(c: Canvas,x: Float,y: Float,rx: Float,ry: Float,color: Int,w: Float=0f) {
        ink(color,w); c.drawOval(x-rx,y-ry,x+rx,y+ry,p)
    }
    private fun poly(c: Canvas,color: Int,vararg xy: Float) {
        path.reset(); path.moveTo(xy[0],xy[1]); for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1])
        path.close(); ink(color); c.drawPath(path,p)
    }
    private fun glow(c: Canvas,x: Float,y: Float,r: Float,color: Int) {
        ink(color); p.shader=RadialGradient(x,y,r,color,color and 0xffffff,Shader.TileMode.CLAMP); c.drawCircle(x,y,r,p)
    }
    fun draw(c: Canvas,z: Int,t: Float) {
        when(z) {
            11 -> saltShaker(c,t)
            12 -> fireworks(c,t)
            18 -> bulb(c,t)
            19 -> banana(c,t)
            21 -> bicycle(c,t)
            23 -> spring(c,t)
            25 -> dryCell(c,t)
            27 -> ceramic(c,t)
        }
    }

    private fun saltShaker(c: Canvas,t: Float) {
        // Table salt is NaCl, not sodium metal. A familiar shaker replaces the lattice.
        oval(c,169f,291f,82f,9f,0x33435566)
        path.reset(); path.moveTo(133f,161f); path.lineTo(211f,161f)
        path.lineTo(230f,268f); path.quadTo(231f,283f,214f,285f)
        path.lineTo(129f,285f); path.quadTo(112f,281f,115f,267f); path.close()
        ink(0x333FA7BD); c.drawPath(path,p)
        c.save(); c.clipPath(path)
        path.reset(); path.moveTo(111f,239f); path.quadTo(157f,214f,196f,231f)
        path.lineTo(232f,229f); path.lineTo(236f,287f); path.lineTo(108f,287f); path.close()
        ink(0xFFF0ECDC.toInt()); c.drawPath(path,p)
        for(i in 0..36) {
            val x=122f+(i*29%100); val y=239f+(i*17%38)
            ink(if(i%2==0) 0xFFFFFFFF.toInt() else 0xFFBCBFAE.toInt()); c.drawRect(x,y,x+2,y+2,p)
        }
        c.restore()
        ink(0xFFA3CBD4.toInt(),1.8f); path.reset(); path.moveTo(133f,161f); path.lineTo(115f,267f)
        path.quadTo(112f,283f,129f,285f); path.lineTo(214f,285f)
        path.quadTo(231f,283f,230f,268f); path.lineTo(211f,161f); c.drawPath(path,p)
        ink(Color.WHITE); p.shader=LinearGradient(127f,0f,216f,0f,
            intArrayOf(0xFF6D8195.toInt(),0xFFE4EBE7.toInt(),0xFF9AAEBB.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(127f,141f,217f,168f,6f,6f,p)
        oval(c,172f,141f,45f,10f,0xFFD4DEDF.toInt())
        for(i in 0..4) oval(c,149f+i*11,139f+(i%2)*4,2.4f,1.5f,0xFF455769.toInt())
        line(c,132f,181f,123f,237f,0xAFFFFFFF.toInt(),3f)
        for(i in 0..6) {
            val x=243f+(i*13%35); val y=272f+(i*7%19)
            poly(c,0xFFF6F4E7.toInt(),x,y-4,x+5,y-1,x+4,y+4,x-2,y+2)
        }
        glow(c,154f+sin(t)*10,157f,21f,0x22FFFFFF)
    }

    private fun fireworks(c: Canvas,t: Float) {
        // Rocket silhouette and a large white burst; the magnesium effect is the light.
        c.save(); c.rotate(24f,139f,259f)
        line(c,139f,277f,139f,311f,0xFFB79C74.toInt(),2f)
        poly(c,0xFFF1E6D4.toInt(),125f,236f,153f,236f,153f,280f,125f,280f)
        poly(c,0xFFD87765.toInt(),125f,236f,139f,214f,153f,236f)
        poly(c,0xFFD87765.toInt(),125f,245f,153f,260f,153f,272f,125f,257f)
        poly(c,0xFF945E5B.toInt(),125f,266f,115f,287f,125f,282f)
        poly(c,0xFF945E5B.toInt(),153f,266f,163f,287f,153f,282f)
        c.restore()
        val x=211f; val y=168f
        glow(c,x,y,65f,0x33F9F3D6)
        for(i in 0..15) {
            val angle=i*PI.toFloat()/8
            val r=43f+(i%3)*9+sin(t*2+i)*3
            ink(if(i%2==0) 0xFFFFF8E4.toInt() else 0xFFC8E2EC.toInt(),1.8f)
            path.reset(); path.moveTo(x+cos(angle)*13,y+sin(angle)*13)
            path.quadTo(x+cos(angle)*r*.75f,y+sin(angle)*r*.7f,x+cos(angle)*r,y+sin(angle)*r+5)
            c.drawPath(path,p)
            oval(c,x+cos(angle)*(r+6),y+sin(angle)*(r+6)+6,1.7f,1.7f,0xFFFFF7DE.toInt())
        }
        glow(c,x,y,19f,0xBFFFFFF0.toInt())
    }

    private fun bulb(c: Canvas,t: Float) {
        // Incandescent bulb: argon protects the hot filament; it is not the light source.
        val strength=(45+12*sin(t)).toInt()
        glow(c,180f,189f,91f,(strength shl 24) or 0xFFE6A0)
        path.reset(); path.moveTo(154f,251f)
        path.cubicTo(154f,230f,116f,218f,116f,173f)
        path.cubicTo(116f,104f,244f,104f,244f,173f)
        path.cubicTo(244f,218f,206f,230f,206f,251f); path.close()
        ink(0x224DA2C0); c.drawPath(path,p); ink(0xFFAFD0D9.toInt(),2f); c.drawPath(path,p)
        line(c,172f,249f,159f,181f,0xFFB7BEC1.toInt(),1.3f)
        line(c,188f,249f,201f,181f,0xFFB7BEC1.toInt(),1.3f)
        ink(0xFFFFD68C.toInt(),2f); path.reset(); path.moveTo(159f,181f)
        for(i in 0..12) path.lineTo(159f+i*3.5f,181f+(if(i%2==0) -3 else 3))
        c.drawPath(path,p); glow(c,180f,181f,27f,0x55FFD790)
        ink(0xFF9DA8B5.toInt()); c.drawRoundRect(153f,251f,207f,288f,5f,5f,p)
        for(i in 0..4) line(c,154f,256f+i*6,206f,251f+i*6,0xFF45576C.toInt(),2.3f)
        oval(c,180f,289f,15f,5f,0xFF2F3B4D.toInt())
        ink(0xAAFFFFFF.toInt(),3f); c.drawArc(128f,132f,231f,226f,186f,68f,false,p)
    }

    private fun banana(c: Canvas,t: Float) {
        // One large peeled banana: a clear contour instead of overlapping crescents.
        c.save(); c.rotate(-13f+sin(t)*.6f,183f,218f)
        oval(c,182f,298f,77f,7f,0x22343B48)
        path.reset(); path.moveTo(166f,224f)
        path.cubicTo(157f,196f,162f,144f,180f,132f)
        path.cubicTo(198f,117f,210f,134f,207f,154f)
        path.lineTo(195f,235f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(163f,0f,207f,0f,
            intArrayOf(0xFFD9C9A0.toInt(),0xFFFFF1CC.toInt(),0xFFF2DFB4.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        line(c,184f,151f,177f,210f,0xFFDFD0A9.toInt(),1f)
        // Main yellow body and three falling peel lobes.
        path.reset(); path.moveTo(164f,212f); path.lineTo(200f,213f)
        path.cubicTo(211f,250f,193f,280f,166f,292f)
        path.lineTo(155f,282f); path.quadTo(184f,248f,164f,212f); path.close()
        ink(0xFFE8BB37.toInt()); c.drawPath(path,p)
        for(s in floatArrayOf(-1f,1f)) {
            path.reset(); path.moveTo(181f+s*15,211f)
            path.cubicTo(181f+s*43,203f,181f+s*64,226f,181f+s*67,258f)
            path.cubicTo(181f+s*49,239f,181f+s*32,235f,181f+s*15,211f); path.close()
            ink(if(s<0) 0xFFFFD953.toInt() else 0xFFF5C339.toInt()); c.drawPath(path,p)
            ink(0xFFFFE79B.toInt(),2f); path.reset(); path.moveTo(181f+s*18,215f)
            path.quadTo(181f+s*49,218f,181f+s*61,249f); c.drawPath(path,p)
        }
        line(c,181f,224f,172f,273f,0xFFFBE187.toInt(),2.5f)
        line(c,157f,284f,166f,292f,0xFF706443.toInt(),5f)
        c.restore()
    }

    private fun bicycle(c: Canvas,t: Float) {
        val frame=0xFFB6D2DF.toInt()
        for(x in floatArrayOf(110f,253f)) {
            oval(c,x,245f,43f,43f,0xFF111B28.toInt(),6f)
            oval(c,x,245f,39f,39f,0xFF859BAC.toInt(),1.5f)
            for(i in 0..9) {
                val a=i*PI.toFloat()/5+t*.15f
                line(c,x,245f,x+cos(a)*37,245+sin(a)*37,0x558EA5B9,.8f)
            }
            oval(c,x,245f,3f,3f,0xFFE0E9EB.toInt())
        }
        val points=floatArrayOf(110f,245f,145f,184f,179f,245f,110f,245f,221f,179f,179f,245f,145f,184f,221f,179f,253f,245f)
        for(i in 0 until points.size-2 step 2) line(c,points[i],points[i+1],points[i+2],points[i+3],frame,4f)
        line(c,145f,184f,140f,171f,0xFF8198AC.toInt(),3f)
        line(c,126f,169f,152f,169f,0xFF354655.toInt(),6f)
        line(c,221f,179f,217f,156f,frame,3f)
        line(c,217f,156f,239f,151f,frame,3f)
        ink(frame,3f); c.drawArc(233f,150f,250f,170f,-90f,190f,false,p)
        oval(c,179f,245f,9f,9f,0xFF71889D.toInt(),2f)
        line(c,179f,245f,193f,254f,frame,2f); line(c,189f,254f,201f,254f,0xFF51667D.toInt(),3f)
    }

    private fun spring(c: Canvas,t: Float) {
        // A suspension spring on a damper, a use of vanadium-bearing steels.
        val compression=sin(t)*3
        line(c,180f,134f,180f,277f,0xFF8B9DB0.toInt(),15f)
        line(c,176f,137f,176f,274f,0xFFD8E6EC.toInt(),2f)
        for(i in 0..6) {
            val y=151f+i*(18f+compression*.25f)
            ink(0xFF566D88.toInt(),7f); c.drawArc(139f,y-8,221f,y+14,0f,180f,false,p)
        }
        for(i in 0..6) {
            val y=151f+i*(18f+compression*.25f)
            ink(0xFFB6CCD7.toInt(),7f); c.drawArc(139f,y-8,221f,y+14,180f,180f,false,p)
            line(c,221f,y+3,139f,y+21+compression*.25f,0xFFB6CCD7.toInt(),6f)
            line(c,213f,y+7,149f,y+20,0xFFE8F1EC.toInt(),1f)
        }
        for(y in floatArrayOf(133f,283f)) {
            ink(0xFF788CA0.toInt()); c.drawRoundRect(143f,y-7,217f,y+7,4f,4f,p)
            line(c,148f,y-5,212f,y-5,0xFFD6E2E9.toInt(),1.5f)
        }
        oval(c,180f,114f,10f,10f,0xFF99ADBC.toInt(),5f)
        oval(c,180f,303f,10f,10f,0xFF99ADBC.toInt(),5f)
    }

    private fun dryCell(c: Canvas,t: Float) {
        // Recognisable cylindrical cell, distinct from the lithium battery pack.
        ink(0xFFBBC8CB.toInt()); c.drawRoundRect(167f,122f,193f,140f,3f,3f,p)
        ink(Color.WHITE); p.shader=LinearGradient(134f,0f,226f,0f,
            intArrayOf(0xFF324052.toInt(),0xFF697889.toInt(),0xFF263649.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(134f,137f,226f,283f,8f,8f,p)
        ink(0xFFDFAD67.toInt()); c.drawRoundRect(134f,137f,226f,181f,8f,8f,p)
        line(c,142f,147f,142f,273f,0x88FFFFFF.toInt(),2f)
        oval(c,180f,283f,46f,6f,0xFF95A7B5.toInt())
        line(c,172f,157f,188f,157f,0xFF453B35.toInt(),2f)
        line(c,180f,149f,180f,165f,0xFF453B35.toInt(),2f)
        poly(c,0xFFFFDC94.toInt(),185f,194f,161f,232f,180f,232f,170f,262f,202f,220f,183f,220f)
        glow(c,182f,220f,29f,((18+5*sin(t)).toInt() shl 24) or 0xFFE1A1)
    }

    private fun ceramic(c: Canvas,t: Float) {
        // Glazed cobalt-blue vase; the pigment contains cobalt compounds.
        path.reset(); path.moveTo(153f,142f); path.lineTo(207f,142f)
        path.cubicTo(204f,169f,228f,185f,237f,220f)
        path.cubicTo(250f,264f,224f,286f,180f,286f)
        path.cubicTo(136f,286f,110f,264f,123f,220f)
        path.cubicTo(132f,185f,156f,169f,153f,142f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(119f,0f,240f,0f,
            intArrayOf(0xFF192F70.toInt(),0xFF4775CC.toInt(),0xFF244AA2.toInt(),0xFF102456.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        c.save(); c.clipPath(path)
        for(y in floatArrayOf(169f,260f)) line(c,117f,y,243f,y,0xFFEBDDB9.toInt(),3f)
        for(i in 0..4) {
            val x=135f+i*23
            ink(0xFFD5E5EB.toInt(),1.3f); path.reset(); path.moveTo(x,249f)
            path.cubicTo(x-17,232f,x+18,218f,x,194f); c.drawPath(path,p)
            oval(c,x-3,207f,4f,7f,0xFFD5E5EB.toInt())
            oval(c,x+4,232f,4f,7f,0xFFD5E5EB.toInt())
        }
        c.restore()
        oval(c,180f,142f,28f,7f,0xFFE0D0AF.toInt())
        oval(c,180f,142f,23f,4f,0xFF15285F.toInt())
        line(c,153f,196f,142f,228f,0x55FFFFFF,3f)
        glow(c,158f+sin(t)*3,194f,21f,0x19FFFFFF)
    }
}
