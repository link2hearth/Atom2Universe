package com.Atom2Universe.app.periodic

import android.graphics.*
import kotlin.math.*

/** Recognisable uses and biological associations for the first pass, Z=1..20.
 * Notes in the studio distinguish elements, compounds and alloys.
 * Material colours belong to the subject, independently of the rarity frame.
 */
internal class EarlyElementCardArt {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private fun ink(color: Int,stroke: Float=0f) {
        p.reset(); p.isAntiAlias=true; p.color=color; p.strokeWidth=stroke
        p.style=if(stroke>0) Paint.Style.STROKE else Paint.Style.FILL
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
    private fun shine(c: Canvas,x: Float,y: Float,r: Float,color: Int) {
        ink(color); p.shader=RadialGradient(x,y,r,color,color and 0xffffff,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,p)
    }
    fun draw(c: Canvas,z: Int,t: Float) {
        when(z) {
            2 -> balloon(c,t)
            4 -> telescope(c,t)
            7 -> wheat(c,t)
            8 -> lungs(c,t)
            9 -> tooth(c,t)
            12 -> flash(c,t)
            13 -> foil(c,t)
            17 -> water(c,t)
            18 -> welding(c,t)
            19 -> fruit(c,t)
        }
    }

    private fun balloon(c: Canvas,t: Float) {
        val x=180f+sin(t)*8; val y=187f+cos(t)*5
        path.reset(); path.moveTo(x,y+67)
        path.cubicTo(x-84,y+22,x-60,y-69,x,y-68)
        path.cubicTo(x+60,y-69,x+84,y+22,x,y+67); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(x-55,y-30,x+55,y+40,
            intArrayOf(0xFF7F62B0.toInt(),0xFFEBC2F4.toInt(),0xFF8A65B8.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        oval(c,x-24,y-30,10f,20f,0x66FFFFFF)
        poly(c,0xFFB79AD9.toInt(),x,y+64,x-5,y+73,x+5,y+73)
        ink(0xFFB9B4C9.toInt(),1.2f); path.reset(); path.moveTo(x,y+74)
        path.cubicTo(x+18,y+86,x-17,293f,180f,312f); c.drawPath(path,p)
    }

    private fun telescope(c: Canvas,t: Float) {
        // Gold-coated beryllium mirror segments, as used by the Webb telescope.
        val radius=24f
        for(row in -2..2) for(col in -2..2) {
            if(abs(row+col)>2 || (row==0 && col==0)) continue
            val x=180f+(col+row*.5f)*42f; val y=207f+row*36f
            path.reset()
            for(i in 0..5) {
                val a=(30+i*60)*PI.toFloat()/180
                val xx=x+cos(a)*radius; val yy=y+sin(a)*radius
                if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
            }
            path.close(); ink(Color.WHITE)
            p.shader=LinearGradient(x-20,y-20,x+20,y+20,
                intArrayOf(0xFF8D6F35.toInt(),0xFFF6DB85.toInt(),0xFFD6AA4F.toInt()),null,Shader.TileMode.CLAMP)
            c.drawPath(path,p); ink(0xFF594A3D.toInt(),2f); c.drawPath(path,p)
        }
        for(angle in floatArrayOf(-90f,30f,150f)) {
            val a=angle*PI.toFloat()/180
            line(c,180f,207f,180+cos(a)*97,207+sin(a)*97,0xFF535662.toInt(),3f)
        }
        oval(c,180f,207f,12f,12f,0xFF777776.toInt()); oval(c,180f,207f,7f,7f,0xFF292D39.toInt())
        shine(c,158+sin(t)*50,163f,30f,0x22FFF0BB)
    }

    private fun wheat(c: Canvas,t: Float) {
        for(i in 0..2) {
            c.save(); c.rotate((i-1)*17f+sin(t+i)*2,180f,290f)
            val top=139f+abs(i-1)*18
            line(c,180f,291f,180f,top-12,0xFFD3B973.toInt(),2f)
            for(j in 0..5) {
                val y=top+j*16
                for(s in floatArrayOf(-1f,1f)) {
                    path.reset(); path.moveTo(180f,y+15)
                    path.quadTo(180+s*28,y+3,180+s*18,y-10)
                    path.quadTo(180+s*4,y-7,180f,y+15)
                    ink(if(j%2==0) 0xFFE2BD67.toInt() else 0xFFF2D583.toInt()); c.drawPath(path,p)
                    line(c,180+s*18,y-9,180+s*24,y-23,0xFFB9A268.toInt(),.7f)
                }
            }
            c.restore()
        }
        path.reset(); path.moveTo(180f,278f); path.quadTo(114f,267f,106f,228f)
        path.quadTo(156f,238f,180f,278f); ink(0xFF74AD72.toInt()); c.drawPath(path,p)
        oval(c,180f,301f,69f,6f,0x334D8565)
    }

    private fun lungs(c: Canvas,t: Float) {
        val inflate=1f+sin(t*2)*.025f
        c.save(); c.scale(inflate,inflate,180f,217f)
        for(s in floatArrayOf(-1f,1f)) {
            c.save(); c.translate(180f,0f); c.scale(s,1f)
            path.reset(); path.moveTo(12f,166f)
            path.cubicTo(22f,117f,66f,137f,82f,180f)
            path.cubicTo(100f,221f,97f,273f,77f,282f)
            path.cubicTo(53f,288f,23f,276f,14f,260f)
            path.cubicTo(4f,239f,9f,203f,12f,166f); path.close()
            ink(Color.WHITE); p.shader=LinearGradient(9f,170f,90f,230f,
                intArrayOf(0xFFB87983.toInt(),0xFFEAA6A1.toInt(),0xFFC48191.toInt()),null,Shader.TileMode.CLAMP)
            c.drawPath(path,p)
            for(i in 0..3) {
                val y=186f+i*19; val x=24f+i*9
                line(c,14f,179f,x,y+30,0xFFF4D4B9.toInt(),3f)
                line(c,x,y+15,x+23,y,0xFFEAC1B4.toInt(),1.6f)
                line(c,x,y+20,x+24,y+32,0xFFEAC1B4.toInt(),1.5f)
            }
            c.restore()
        }
        line(c,180f,120f,180f,169f,0xFFEAC8AD.toInt(),10f)
        for(i in 0..6) line(c,175f,126f+i*6,185f,126f+i*6,0xFFA3827D.toInt(),1f)
        line(c,180f,168f,166f,184f,0xFFEAC8AD.toInt(),6f)
        line(c,180f,168f,194f,184f,0xFFEAC8AD.toInt(),6f)
        c.restore()
    }

    private fun tooth(c: Canvas,t: Float) {
        path.reset(); path.moveTo(180f,145f)
        path.cubicTo(140f,113f,105f,146f,124f,196f)
        path.cubicTo(136f,223f,132f,277f,151f,287f)
        path.cubicTo(165f,292f,166f,240f,180f,237f)
        path.cubicTo(194f,240f,195f,292f,209f,287f)
        path.cubicTo(228f,277f,224f,223f,236f,196f)
        path.cubicTo(255f,146f,220f,113f,180f,145f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(125f,160f,236f,240f,
            intArrayOf(0xFFB7D1D5.toInt(),0xFFFFFDF0.toInt(),0xFFE0E9E1.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        ink(0xFFBCD0CB.toInt(),2f); path.reset(); path.moveTo(151f,157f)
        path.quadTo(169f,180f,180f,161f); path.quadTo(194f,179f,210f,157f); c.drawPath(path,p)
        val shineX=143f+sin(t)*3
        line(c,shineX-7,170f,shineX+7,170f,Color.WHITE,2f)
        line(c,shineX,163f,shineX,177f,Color.WHITE,2f)
        ink(0x776CBDC8,1.3f); c.drawArc(94f,105f,266f,306f,185f,165f,false,p)
    }

    private fun flash(c: Canvas,t: Float) {
        // A bright pyrotechnic burst, not electrons or an atomic nucleus.
        val x=180f; val y=198f
        line(c,145f,299f,178f,214f,0xFF748897.toInt(),5f)
        shine(c,x,y,75f,0x55F9F3D7)
        for(i in 0..23) {
            val angle=i*PI.toFloat()/12
            val length=39+((i*17)%47)+sin(t*3+i)*6
            line(c,x+cos(angle)*15,y+sin(angle)*15,x+cos(angle)*length,y+sin(angle)*length,
                if(i%2==0) 0xFFFFF8E1.toInt() else 0xFFBBD7E6.toInt(),if(i%3==0) 2f else 1f)
        }
        shine(c,x,y,23f,0xDDFDF6E7.toInt())
        for(i in 0..8) {
            val xx=125f+i*14; val yy=229f+sin(t*3+i)*9+i%3*12
            line(c,xx,yy,xx-3,yy+6,0xAAFFF2D0.toInt(),1.4f)
        }
    }

    private fun foil(c: Canvas,t: Float) {
        // Roll and folded sheet, with reflection travelling across the metal.
        poly(c,0xFF8395A7.toInt(),116f,171f,252f,157f,262f,281f,227f,265f,198f,284f,113f,276f)
        poly(c,0xFFD5E2E7.toInt(),116f,171f,166f,166f,160f,283f,113f,276f)
        poly(c,0xFFF0F3EA.toInt(),166f,166f,217f,160f,227f,265f,198f,284f,160f,283f)
        line(c,166f,166f,160f,283f,0xFF60788D.toInt())
        line(c,217f,160f,227f,265f,0xFF778FA4.toInt())
        ink(Color.WHITE); p.shader=LinearGradient(0f,128f,0f,181f,
            intArrayOf(0xFF758799.toInt(),0xFFF7FAEE.toInt(),0xFF9DADBA.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(106f,130f,253f,180f,8f,8f,p)
        oval(c,252f,155f,11f,25f,0xFFB3C1CB.toInt()); oval(c,253f,155f,5f,16f,0xFF334253.toInt())
        line(c,112f,139f,242f,139f,0xCCFFFFFF.toInt(),2f)
        shine(c,155+sin(t)*45,216f,34f,0x22FFFFFF)
    }

    private fun water(c: Canvas,t: Float) {
        // Water treatment: a droplet above a clean reservoir, not elemental liquid chlorine.
        path.reset(); path.moveTo(180f,121f)
        path.cubicTo(170f,148f,134f,172f,139f,200f)
        path.cubicTo(145f,247f,215f,247f,221f,200f)
        path.cubicTo(226f,172f,190f,148f,180f,121f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(140f,140f,221f,233f,
            intArrayOf(0xFFB8EAEB.toInt(),0xFF68AFCF.toInt(),0xFF397AA2.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        ink(0xAADDFFFF.toInt(),3f); c.drawArc(148f,163f,209f,225f,115f,62f,false,p)
        for(i in 0..2) {
            path.reset()
            for(j in 0..32) {
                val x=86+j*6f; val y=259+i*13f+sin(j*.4f+t*2)*3
                if(j==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            ink(0xFF6DACC6.toInt(),2f); c.drawPath(path,p)
        }
        line(c,240f,181f,254f,181f,0xFFE6FAF5.toInt())
        line(c,247f,174f,247f,188f,0xFFE6FAF5.toInt())
    }

    private fun welding(c: Canvas,t: Float) {
        poly(c,0xFF8798A9.toInt(),85f,276f,179f,256f,180f,278f,93f,301f)
        poly(c,0xFFBAC5CC.toInt(),181f,256f,274f,276f,265f,301f,180f,278f)
        // Nozzle above the joint, transparent gas envelope surrounding the arc.
        poly(c,0xFF53677D.toInt(),188f,136f,210f,144f,189f,218f,170f,213f)
        poly(c,0xFFC6B3A2.toInt(),170f,211f,191f,217f,182f,239f,165f,233f)
        path.reset(); path.moveTo(165f,231f); path.quadTo(134f,251f,144f,270f)
        path.quadTo(180f,291f,215f,270f); path.quadTo(209f,243f,182f,237f); path.close()
        ink(0x2258BFD9); c.drawPath(path,p); ink(0x447FCCDF,1f); c.drawPath(path,p)
        line(c,176f,238f,180f,267f,0xFFE3FAFF.toInt(),2f)
        shine(c,180f,267f,24f,0x99B9EAFF.toInt())
        for(i in 0..8) {
            val a=(-160+i*20)*PI.toFloat()/180; val r=20+sin(t*4+i)*8
            line(c,180f,264f,180+cos(a)*r,264+sin(a)*r,0xDDE3F9FF.toInt())
        }
        line(c,180f,280f,180f,291f,0xFFD1DADE.toInt(),3f)
    }

    private fun fruit(c: Canvas,t: Float) {
        // Bananas illustrate dietary potassium ions, not potassium metal.
        c.save(); c.rotate(sin(t)*1.2f,180f,215f)
        for(i in 0..2) {
            val x=i*14f; val y=i*10f
            path.reset(); path.moveTo(99f+x,159f+y)
            path.cubicTo(102f+x,248f+y,208f+x,278f+y,242f+x,173f+y)
            path.cubicTo(244f+x,304f+y,105f+x,307f+y,87f+x,166f+y); path.close()
            ink(if(i==1) 0xFFF3CE53.toInt() else 0xFFE7B832.toInt()); c.drawPath(path,p)
            ink(0xFFFBE79A.toInt(),2f); path.reset(); path.moveTo(97f+x,179f+y)
            path.cubicTo(117f+x,273f+y,214f+x,278f+y,238f+x,194f+y); c.drawPath(path,p)
            line(c,89f+x,162f+y,101f+x,156f+y,0xFF7A784A.toInt(),5f)
        }
        c.restore()
    }
}
