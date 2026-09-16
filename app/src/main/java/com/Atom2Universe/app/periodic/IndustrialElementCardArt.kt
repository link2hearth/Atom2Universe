package com.Atom2Universe.app.periodic

import android.graphics.*
import kotlin.math.*

/** Six subjects for Z=43..49, preserving the existing silver card at Z=47.
 * Coordinates and phase follow ProceduralElementCardView. See SUIVI_CARTES_ELEMENTS.md
 * for sources: applications are stylised, never literal bulk samples of the elements.
 */
internal class IndustrialElementCardArt {
    companion object {
        fun supports(z: Int) = z in 43..46 || z == 48 || z == 49
        fun accent(z: Int): Int = when (z) {
            43 -> 0xFFA998FF; 44 -> 0xFF66E3C0; 45 -> 0xFFF4AF79
            46 -> 0xFF7CDBEF; 48 -> 0xFFFFD15A; else -> 0xFF9EA8FF
        }.toInt()
        fun secondary(z: Int): Int = when (z) {
            43 -> 0xFFFFB969; 44 -> 0xFFE5BC76; 45 -> 0xFFDB7C91
            46 -> 0xFFBBA8EF; 48 -> 0xFFEC6554; else -> 0xFF6FE6D8
        }.toInt()
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mask = Path()
    private val tau = (2 * PI).toFloat()
    private val silver = 0xFFD4E5EF.toInt()
    private val dark = 0xFF111D2E.toInt()
    private fun fade(color: Int, a: Int) = (color and 0xffffff) or (a.coerceIn(0, 255) shl 24)
    private fun ink(color: Int, w: Float = 0f) {
        p.reset(); p.isAntiAlias = true; p.color = color; p.strokeWidth = w
        p.style = if (w > 0) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }
    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, w: Float = 1f) {
        ink(color, w); c.drawLine(x, y, xx, yy, p)
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, w: Float = 0f) {
        ink(color, w); c.drawOval(x-rx, y-ry, x+rx, y+ry, p)
    }
    private fun box(c: Canvas, l: Float, top: Float, r: Float, b: Float, rad: Float, color: Int, w: Float = 0f) {
        ink(color, w); c.drawRoundRect(l, top, r, b, rad, rad, p)
    }
    private fun poly(c: Canvas, color: Int, vararg xy: Float, w: Float = 0f) {
        path.reset(); path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i+1])
        path.close(); ink(color, w); c.drawPath(path, p)
    }
    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        ink(color); p.shader = RadialGradient(x, y, r, color, fade(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
    private fun arc(c: Canvas, x: Float, y: Float, r: Float, start: Float, sweep: Float, color: Int, w: Float = 1f) {
        ink(color, w); c.drawArc(x-r, y-r, x+r, y+r, start, sweep, false, p)
    }
    private fun hex(c: Canvas, x: Float, y: Float, r: Float, color: Int, w: Float = 1f) {
        path.reset()
        for (i in 0..5) {
            val a = i*tau/6
            if (i == 0) path.moveTo(x+cos(a)*r, y+sin(a)*r) else path.lineTo(x+cos(a)*r, y+sin(a)*r)
        }
        path.close(); ink(color, w); c.drawPath(path, p)
    }
    private fun metal(l: Float, r: Float) {
        ink(silver); p.shader = LinearGradient(l, 0f, r, 0f,
            intArrayOf(0xFF42586F.toInt(), silver, 0xFF7894A7.toInt(), 0xFFF1F5ED.toInt()), null, Shader.TileMode.CLAMP)
    }
    private fun molecule(c: Canvas, x: Float, y: Float, angle: Float, color: Int, opacity: Int = 255) {
        val dx = cos(angle)*5f; val dy = sin(angle)*5f
        line(c, x-dx, y-dy, x+dx, y+dy, fade(color, opacity), 2f)
        oval(c, x-dx, y-dy, 3.6f, 3.6f, fade(color, opacity))
        oval(c, x+dx, y+dy, 3.6f, 3.6f, fade(color, opacity))
    }

    fun frame(c: Canvas, z: Int, rarityColor: Int, t: Float) {
        val a = accent(z); val b = secondary(z)
        for (inset in floatArrayOf(10f, 17f, 26f)) {
            ink(a, if (inset == 10f) 3f else 1f)
            p.shader = LinearGradient(0f, 0f, 360f, 520f, intArrayOf(silver, a, b, fade(a, 160)), null, Shader.TileMode.CLAMP)
            when (z) {
                43, 45 -> {
                    val cut = if (z == 43) 33f else 23f
                    path.reset(); path.moveTo(inset+cut, inset); path.lineTo(360-inset-cut, inset)
                    path.lineTo(360-inset, inset+cut); path.lineTo(360-inset, 520-inset-cut)
                    path.lineTo(360-inset-cut, 520-inset); path.lineTo(inset+cut, 520-inset)
                    path.lineTo(inset, 520-inset-cut); path.lineTo(inset, inset+cut); path.close(); c.drawPath(path, p)
                }
                44 -> { // Stepped PCB outline.
                    path.reset(); path.moveTo(inset+16, inset); path.lineTo(344-inset, inset)
                    path.lineTo(344-inset, inset+13); path.lineTo(360-inset, inset+13)
                    path.lineTo(360-inset, 507-inset); path.lineTo(344-inset, 507-inset)
                    path.lineTo(344-inset, 520-inset); path.lineTo(inset+16, 520-inset)
                    path.lineTo(inset+16, 507-inset); path.lineTo(inset, 507-inset)
                    path.lineTo(inset, inset+13); path.lineTo(inset+16, inset+13); path.close(); c.drawPath(path, p)
                }
                48 -> { // Gently irregular, brushed moulding.
                    path.reset(); path.moveTo(42f, inset); path.cubicTo(111f, inset+5, 245f, inset-2, 318f, inset)
                    path.quadTo(350f, inset, 360-inset, 43f); path.cubicTo(348f, 191f, 360-inset, 371f, 360-inset, 477f)
                    path.quadTo(351f, 520-inset, 318f, 520-inset); path.lineTo(42f, 520-inset)
                    path.quadTo(inset, 510f, inset, 477f); path.cubicTo(inset+5, 359f, inset-2, 166f, inset, 43f)
                    path.quadTo(inset, inset, 42f, inset); path.close(); c.drawPath(path, p)
                }
                else -> c.drawRoundRect(inset, inset, 360-inset, 520-inset, if (z == 49) 32f else 45f, if (z == 49) 32f else 45f, p)
            }
        }
        for (sx in floatArrayOf(1f, -1f)) for (sy in floatArrayOf(1f, -1f)) {
            c.save(); c.translate(if (sx > 0) 0f else 360f, if (sy > 0) 0f else 520f); c.scale(sx, sy)
            when (z) {
                43 -> {
                    arc(c, 43f, 43f, 16f, 12f, 245f, a, 2f)
                    arc(c, 43f, 43f, 9f, -65f, 245f, b, 1.5f)
                    oval(c, 43f+cos(t)*16, 43f+sin(t)*16, 2f, 2f, silver)
                    line(c, 28f, 72f, 28f, 91f, b, 2f)
                }
                44 -> {
                    for (i in 0..2) {
                        val d = i*9f
                        line(c, 30f+d, 84f-d, 30f+d, 41f+d, a, 1.3f)
                        line(c, 30f+d, 41f+d, 41f+d, 30f+d, a, 1.3f)
                        line(c, 41f+d, 30f+d, 77f, 30f+d, a, 1.3f)
                        oval(c, 30f+d, 84f-d, 2f, 2f, b)
                    }
                }
                45 -> { hex(c, 42f, 42f, 11f, a, 1.5f); hex(c, 62f, 32f, 7f, b); hex(c, 32f, 62f, 7f, b) }
                46 -> {
                    for (i in 0..3) {
                        val angle = i*PI.toFloat()/6
                        molecule(c, 34f+cos(angle)*25, 34f+sin(angle)*25, angle, if (i%2 == 0) a else b)
                    }
                }
                48 -> {
                    for (i in 0..3) line(c, 30f+i*5, 71f-i*10, 45f+i*7, 30f, if (i%2 == 0) a else b, 3f)
                    oval(c, 34f, 34f, 6f, 5f, 0xFFFFE887.toInt())
                }
                49 -> {
                    box(c, 29f, 29f, 66f, 66f, 11f, fade(a, 70), 1.3f)
                    for (i in 0..2) box(c, 34f+i*9, 34f, 39f+i*9, 39f, 1f, if (i%2 == 0) a else b)
                    arc(c, 48f, 49f, 9f, t*57.29578f, 240f, b, 1.5f)
                }
            }
            c.restore()
        }
        for (side in floatArrayOf(20f, 340f)) for (i in 0..13) {
            val y = 113f+i*21f
            val col = fade(a, (110+100*(.5f+.5f*sin(t*2-i*.6f))).toInt())
            when (z) {
                43 -> { line(c, side-3, y-5, side+3, y+5, col, 1.5f); oval(c, side, y, 1.7f, 1.7f, b) }
                44 -> { line(c, side, y-8, side, y+5, col); oval(c, side, y+6, 2.5f, 2.5f, col, 1f) }
                45 -> hex(c, side, y, 4f, col)
                46 -> molecule(c, side, y, PI.toFloat()/2+sin(t+i)*.3f, col)
                48 -> line(c, side-2, y+4, side+2, y-4, if (i%3 == 0) fade(b, 200) else col, 3f)
                49 -> box(c, side-2, y-4, side+2, y+4, 1f, col)
            }
        }
        glow(c, 180f, 20f, 23f, fade(rarityColor, (70+20*sin(t*2)).toInt()))
        poly(c, rarityColor, 180f, 12f, 186f, 20f, 180f, 28f, 174f, 20f)
        line(c, 180f, 13f, 175f, 20f, silver)
    }

    fun draw(c: Canvas, z: Int, t: Float) {
        glow(c, 180f, 210f, 113f, fade(accent(z), 32))
        oval(c, 180f, 300f, 87f, 7f, 0x35102030)
        when (z) {
            43 -> cyclotron(c, t)
            44 -> contacts(c, t)
            45 -> converter(c, t)
            46 -> membrane(c, t)
            48 -> pigments(c, t)
            49 -> touchscreen(c, t)
        }
    }

    private fun cyclotron(c: Canvas, t: Float) {
        // Historical research motif: technetium was identified in irradiated molybdenum.
        // A stylised cutaway, not a literal reconstruction of the Berkeley cyclotron.
        box(c, 139f, 280f, 218f, 297f, 4f, 0xFF61758C.toInt())
        oval(c, 170f, 205f, 83f, 83f, 0xFF536378.toInt())
        oval(c, 170f, 205f, 77f, 77f, dark)
        oval(c, 170f, 205f, 77f, 77f, 0xFFB5B2E7.toInt(), 2f)
        // Two D-shaped electrodes separated by a vertical accelerating gap.
        ink(0xFF6F719F.toInt()); c.drawArc(100f, 135f, 234f, 275f, 90f, 180f, true, p)
        ink(0xFF927B67.toInt()); c.drawArc(106f, 135f, 240f, 275f, -90f, 180f, true, p)
        line(c, 167f, 138f, 167f, 272f, 0xFFD7CBF4.toInt(), 1.5f)
        line(c, 173f, 138f, 173f, 272f, 0xFFFFCE8B.toInt(), 1.5f)
        path.reset()
        for (i in 0..160) {
            val q = i/160f; val angle = q*tau*3
            val x = 170f+cos(angle)*(5+q*58); val y = 205f+sin(angle)*(5+q*58)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        ink(0x88F7D898.toInt(), 1.2f); c.drawPath(path, p)
        val q = (t/tau*2)%1f; val angle = q*tau*3; val r = 5+q*58
        val opacity = (220*sin(PI.toFloat()*q)).toInt()
        glow(c, 170f+cos(angle)*r, 205f+sin(angle)*r, 10f, fade(0xFFFFDCA0.toInt(), opacity))
        // Extraction pipe and molybdenum target foil.
        line(c, 235f, 206f, 285f, 206f, 0xFF546B80.toInt(), 14f)
        line(c, 235f, 206f, 285f, 206f, 0xFFEDBD7F.toInt(), 2f)
        poly(c, silver, 279f, 180f, 291f, 188f, 291f, 232f, 279f, 224f)
        glow(c, 282f, 206f, 16f, fade(0xFFFFBD76.toInt(), (45+40*(.5f+.5f*sin(t*2))).toInt()))
        for (i in 0..7) {
            val a = i*tau/8
            oval(c, 170f+cos(a)*82, 205f+sin(a)*82, 3f, 3f, silver)
        }
    }

    private fun contacts(c: Canvas, t: Float) {
        // Large pivoting switch contacts; only the contact alloy is associated with Ru.
        val opening = max(0f, sin(t*2))
        box(c, 87f, 257f, 274f, 285f, 7f, 0xFF304D4D.toInt())
        box(c, 93f, 260f, 268f, 279f, 5f, 0xFF4C7B70.toInt(), 1f)
        for (x in floatArrayOf(112f, 246f)) {
            metal(x-11, x+11); c.drawRoundRect(x-11, 216f, x+11, 258f, 3f, 3f, p)
            oval(c, x, 258f, 16f, 5f, 0xFFD9B570.toInt())
            line(c, x, 284f, x, 296f, silver, 4f)
        }
        // Fixed right terminal and the moving silver contact at the blade's tip.
        box(c, 228f, 204f, 265f, 218f, 4f, 0xFF9BB6C3.toInt())
        oval(c, 240f, 203f, 11f, 4f, silver)
        c.save(); c.rotate(-opening*27, 112f, 209f)
        poly(c, 0xFFB38B4E.toInt(), 109f, 200f, 246f, 193f, 248f, 204f, 111f, 214f)
        line(c, 122f, 202f, 244f, 197f, 0xFFF3D89E.toInt(), 2f)
        oval(c, 240f, 203f, 10f, 4f, silver)
        box(c, 135f, 182f, 174f, 199f, 5f, 0xFF294E50.toInt())
        c.restore()
        oval(c, 112f, 209f, 14f, 14f, 0xFF718B9C.toInt())
        oval(c, 112f, 209f, 6f, 6f, silver)
        line(c, 108f, 209f, 116f, 209f, dark, 1.5f)
        // Current cue lights only while the contacts are actually closed.
        val closed = (1f-opening*18).coerceIn(0f, 1f)
        line(c, 72f, 273f, 87f, 273f, 0xFF8EB9B6.toInt(), 3f)
        line(c, 274f, 273f, 297f, 273f, 0xFF8EB9B6.toInt(), 3f)
        glow(c, 240f, 202f, 21f, fade(0xFF6AECCA.toInt(), (100*closed).toInt()))
        oval(c, 187f, 269f, 4f, 4f, if (closed > .5f) 0xFF79E6C2.toInt() else 0xFF2B4C4D.toInt())
        for (x in floatArrayOf(99f, 262f)) oval(c, x, 269f, 3f, 3f, silver)
    }

    private fun converter(c: Canvas, t: Float) {
        // Open casing reveals the ceramic honeycomb carrying catalytic coatings.
        metal(75f, 287f); c.drawRoundRect(64f, 206f, 111f, 234f, 4f, 4f, p)
        c.drawRoundRect(254f, 206f, 300f, 234f, 4f, 4f, p)
        poly(c, 0xFF7C91A1.toInt(), 101f, 203f, 133f, 171f, 237f, 171f, 267f, 203f,
            267f, 239f, 237f, 269f, 133f, 269f, 101f, 238f)
        poly(c, 0xFFC1D3DA.toInt(), 101f, 203f, 133f, 171f, 237f, 171f, 253f, 188f, 129f, 190f, 113f, 210f)
        box(c, 125f, 193f, 243f, 251f, 8f, 0xFF3D3034.toInt())
        for (row in 0..3) for (col in 0..6) {
            val x = 135f+col*16; val y = 201f+row*13.5f+(col%2)*6.5f
            if (y < 246f) hex(c, x, y, 8f, 0xFFE5AC77.toInt(), 1.4f)
        }
        for (i in 0..2) {
            val y = 206f+i*13
            val q = (t/tau*3+i*.31f)%1f
            val opacity = (170*sin(PI.toFloat()*q)).toInt()
            val x = 58f+q*250
            glow(c, x, y, 8f, fade(if (x < 190) 0xFFEF966F.toInt() else 0xFF9ACFDA.toInt(), opacity))
        }
        line(c, 137f, 264f, 235f, 264f, silver, 2f)
        for (x in floatArrayOf(119f, 253f)) {
            line(c, x, 195f, x, 246f, 0xFFCEDBE1.toInt(), 2f)
            oval(c, x, 219f, 3f, 3f, 0xFF546779.toInt())
        }
        // Small exhaust silhouette above the cutaway anchors the use, without text.
        path.reset(); path.moveTo(115f, 149f); path.lineTo(132f, 145f); path.lineTo(147f, 127f)
        path.lineTo(199f, 127f); path.lineTo(221f, 145f); path.lineTo(244f, 149f)
        ink(0xFF7E8E9F.toInt(), 2f); c.drawPath(path, p)
        line(c, 117f, 155f, 243f, 155f, 0xFF7E8E9F.toInt(), 2f)
        oval(c, 140f, 154f, 8f, 8f, 0xFF91A4B3.toInt(), 2f)
        oval(c, 218f, 154f, 8f, 8f, 0xFF91A4B3.toInt(), 2f)
    }

    private fun membrane(c: Canvas, t: Float) {
        // Cutaway purifier: paired hydrogen outside, schematic single atoms in Pd.
        box(c, 78f, 166f, 282f, 264f, 27f, 0x153DCEE0)
        box(c, 78f, 166f, 282f, 264f, 27f, 0xFF7BB9CA.toInt(), 1.5f)
        for (x in floatArrayOf(78f, 282f)) {
            line(c, x, 178f, x, 251f, silver, 4f)
            line(c, x+(if (x < 180) -22 else 22), 215f, x, 215f, silver, 6f)
        }
        metal(160f, 201f); c.drawRoundRect(160f, 144f, 201f, 285f, 6f, 6f, p)
        box(c, 168f, 154f, 193f, 276f, 3f, 0xFF6B8FAD.toInt())
        for (row in 0..9) for (col in 0..1) {
            val x = 174f+col*12; val y = 165f+row*11
            oval(c, x, y, 2.5f, 2.5f, 0xFFD2EAF2.toInt())
        }
        for (i in 0..2) {
            val q = (t/tau*2+i/3f)%1f
            val x = 86f+q*185; val y = 191f+i*23
            val opacity = (220*sin(PI.toFloat()*q)).toInt()
            if (x < 158f || x > 204f) molecule(c, x, y, t+i, 0xFF91E5EC.toInt(), opacity)
            else {
                oval(c, x, y-4f, 2.7f, 2.7f, fade(0xFFECF9F3.toInt(), opacity))
                oval(c, x+6, y+4f, 2.7f, 2.7f, fade(0xFFECF9F3.toInt(), opacity))
            }
        }
        // Other gas remains on the inlet side, never passes through the membrane.
        for (i in 0..2) oval(c, 101f+i*16+sin(t+i)*3, 238f-i*26, 4f, 4f, 0xFFB99EE8.toInt(), 1.5f)
        line(c, 100f, 172f, 149f, 172f, 0x88D7F2F5.toInt(), 2f)
        line(c, 212f, 172f, 260f, 172f, 0x88D7F2F5.toInt(), 2f)
        for (x in floatArrayOf(116f, 244f)) box(c, x-14, 267f, x+14, 284f, 3f, 0xFF597990.toInt())
    }

    private fun pigments(c: Canvas, t: Float) {
        // Historic cadmium pigments, not the silvery elemental metal.
        c.save(); c.rotate(-12f, 177f, 217f)
        path.reset(); path.moveTo(103f, 174f)
        path.cubicTo(153f, 130f, 248f, 146f, 266f, 194f)
        path.cubicTo(285f, 240f, 252f, 283f, 197f, 285f)
        path.cubicTo(172f, 286f, 181f, 255f, 157f, 251f)
        path.cubicTo(141f, 246f, 133f, 275f, 111f, 262f)
        path.cubicTo(77f, 243f, 76f, 199f, 103f, 174f); path.close()
        ink(0xFFAE7647.toInt()); p.shader = LinearGradient(90f, 150f, 260f, 280f,
            intArrayOf(0xFFE3B780.toInt(), 0xFFBA8756.toInt(), 0xFF805339.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        ink(0xFFE9C593.toInt(), 1.5f); c.drawPath(path, p)
        oval(c, 126f, 227f, 15f, 21f, dark)
        oval(c, 128f, 226f, 16f, 21f, 0xFF75472F.toInt(), 2f)
        oval(c, 129f, 181f, 20f, 13f, 0xFFFFD63E.toInt())
        oval(c, 181f, 167f, 19f, 12f, 0xFFF4AB2F.toInt())
        oval(c, 228f, 188f, 19f, 15f, 0xFFF57839.toInt())
        oval(c, 239f, 231f, 20f, 15f, 0xFFCF493F.toInt())
        oval(c, 204f, 262f, 15f, 10f, 0xFF9F3540.toInt())
        for (i in 0..3) {
            val y = 202f+i*8
            ink(0xADEF994A.toInt(), 4f); c.drawArc(157f, y, 213f, y+18, 185f, 140f, false, p)
        }
        oval(c, 125f+sin(t)*3, 178f, 9f, 2f, 0x88FFF4BD.toInt())
        oval(c, 229f+sin(t)*3, 185f, 7f, 2f, 0x66FFD9A7)
        c.restore()
        // Brush laid diagonally across the palette, with only a gentle light sweep.
        c.save(); c.rotate(35f, 196f, 215f)
        poly(c, 0xFF233D58.toInt(), 190f, 139f, 202f, 139f, 199f, 281f, 194f, 295f)
        line(c, 195f, 151f, 196f, 270f, 0xFF7CA5BA.toInt(), 1.5f)
        metal(188f, 204f); c.drawRect(188f, 130f, 204f, 165f, p)
        path.reset(); path.moveTo(188f, 131f); path.quadTo(185f, 111f, 196f, 104f)
        path.quadTo(207f, 116f, 204f, 131f); path.close(); ink(0xFFFFCF48.toInt()); c.drawPath(path, p)
        line(c, 192f, 122f, 195f, 111f, 0xFFFFED96.toInt())
        c.restore()
    }

    private fun touchscreen(c: Canvas, t: Float) {
        // Transparent conductive ITO layer shown as an offset sheet above a display.
        c.save(); c.rotate(-9f, 176f, 209f)
        box(c, 108f, 116f, 244f, 292f, 16f, 0xFF718AA5.toInt())
        box(c, 113f, 120f, 239f, 287f, 13f, 0xFF0C182B.toInt())
        ink(Color.WHITE); p.shader = LinearGradient(119f, 137f, 231f, 270f,
            intArrayOf(0xFF394799.toInt(), 0xFF376D9A.toInt(), 0xFF369786.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(119f, 137f, 233f, 269f, 7f, 7f, p)
        line(c, 163f, 128f, 192f, 128f, 0xFF708CAB.toInt(), 3f)
        line(c, 165f, 278f, 190f, 278f, 0xFFB7D5E6.toInt(), 2f)
        for (row in 0..2) for (col in 0..2) {
            val x = 131f+col*32; val y = 150f+row*32
            box(c, x, y, x+21, y+21, 5f, if ((row+col)%2 == 0) 0x66BEDDF8 else 0x555DE3C4)
        }
        mask.reset(); mask.addRoundRect(127f, 142f, 245f, 276f, 7f, 7f, Path.Direction.CW)
        ink(0x1276EDDF); c.drawPath(mask, p)
        ink(0xBBA6EEE6.toInt(), 1.4f); c.drawPath(mask, p)
        c.save(); c.clipPath(mask)
        for (x in 136..244 step 18) line(c, x.toFloat(), 144f, x.toFloat(), 274f, 0x258FE8E4)
        for (y in 152..275 step 18) line(c, 129f, y.toFloat(), 243f, y.toFloat(), 0x258FE8E4)
        for (i in 0..2) {
            val q = (t/tau*2+i/3f)%1f
            val opacity = (180*sin(PI.toFloat()*q)).toInt()
            oval(c, 200f, 222f, 7+q*32, 7+q*32, fade(0xFFB5FFF2.toInt(), opacity), 1.5f)
        }
        c.restore()
        c.restore()
        // A gloved fingertip makes the touch action readable at thumbnail size.
        path.reset(); path.moveTo(247f, 299f); path.lineTo(230f, 283f)
        path.quadTo(220f, 278f, 219f, 266f); path.lineTo(198f, 232f)
        path.cubicTo(190f, 218f, 203f, 211f, 211f, 223f)
        path.lineTo(231f, 247f); path.quadTo(242f, 239f, 250f, 248f)
        path.quadTo(263f, 245f, 267f, 260f); path.lineTo(277f, 282f); path.close()
        ink(0xFFB2D3DE.toInt()); c.drawPath(path, p)
        ink(0xFF7598B6.toInt(), 1.5f); c.drawPath(path, p)
        line(c, 231f, 248f, 242f, 266f, 0xFF7EABC0.toInt(), 1.5f)
        line(c, 248f, 251f, 257f, 268f, 0xFF7EABC0.toInt(), 1.5f)
        line(c, 248f, 299f, 277f, 283f, 0xFF5B7F9F.toInt(), 6f)
    }
}
