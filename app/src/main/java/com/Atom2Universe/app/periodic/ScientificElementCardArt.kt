package com.Atom2Universe.app.periodic

import android.graphics.*
import kotlin.math.*

/** Z=32..42. Native illustrations on the shared 360 × 520 artboard.
 * Material/usage references and review status live in SUIVI_CARTES_ELEMENTS.md.
 * All movement uses the view's phase (including pause and static thumbnails).
 */
internal class ScientificElementCardArt {
    companion object {
        fun supports(z: Int) = z in 32..42
        fun accent(z: Int): Int = when (z) {
            32 -> 0xFF66D9EB; 33 -> 0xFFAFC8DE; 34 -> 0xFFFFCC77
            35 -> 0xFFEF8355; 36 -> 0xFFD3C2FF; 37 -> 0xFFB5A1FF
            38 -> 0xFFFF6277; 39 -> 0xFF80EABC; 40 -> 0xFFC4E9F3
            41 -> 0xFF70C9FF; else -> 0xFFE5B478
        }.toInt()
        fun secondary(z: Int): Int = when (z) {
            32 -> 0xFFED8669; 33 -> 0xFF7795B6; 34 -> 0xFFD66064
            35 -> 0xFFC54452; 36 -> 0xFF83CFF0; 37 -> 0xFFECAD7D
            38 -> 0xFFFFAE68; 39 -> 0xFFB99DFF; 40 -> 0xFF839FC9
            41 -> 0xFF8C94F7; else -> 0xFF9CAFC5
        }.toInt()
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val glass = Path()
    private val silver = 0xFFD9E7EF.toInt()
    private val dark = 0xFF111C2C.toInt()
    private val tau = (2 * PI).toFloat()
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
    private fun box(c: Canvas, l: Float, top: Float, r: Float, b: Float, radius: Float, color: Int, w: Float = 0f) {
        ink(color, w); c.drawRoundRect(l, top, r, b, radius, radius, p)
    }
    private fun poly(c: Canvas, color: Int, vararg xy: Float, w: Float = 0f) {
        path.reset(); path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i+1])
        path.close(); ink(color, w); c.drawPath(path, p)
    }
    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        ink(color); p.shader = RadialGradient(x, y, radius, color, fade(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, p)
    }
    private fun arc(c: Canvas, x: Float, y: Float, r: Float, start: Float, sweep: Float, color: Int, w: Float = 1f) {
        ink(color, w); c.drawArc(x-r, y-r, x+r, y+r, start, sweep, false, p)
    }
    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        line(c, x-r, y, x+r, y, color, 1.2f)
        line(c, x, y-r, x, y+r, color, 1.2f)
    }
    private fun metal(l: Float, r: Float) {
        ink(silver); p.shader = LinearGradient(l, 0f, r, 0f,
            intArrayOf(0xFF435970.toInt(), silver, 0xFF8199B0.toInt(), 0xFFE8F2F5.toInt()), null, Shader.TileMode.CLAMP)
    }

    /** Borders have independent silhouettes; the top stone retains the actual rarity colour.
     * Rails stay outside x=38..322 and corner ornaments outside the header/badges.
     */
    fun frame(c: Canvas, z: Int, rarityColor: Int, t: Float) {
        val a = accent(z); val b = secondary(z)
        for (inset in floatArrayOf(10f, 17f, 26f)) {
            ink(a, if (inset == 10f) 3f else 1f)
            p.shader = LinearGradient(0f, 0f, 360f, 520f,
                intArrayOf(silver, a, fade(b, 150), a), null, Shader.TileMode.CLAMP)
            when (z) {
                32, 34, 37, 41 -> c.drawRoundRect(inset, inset, 360-inset, 520-inset,
                    if (z == 37) 38f else 18f, if (z == 37) 38f else 18f, p)
                35 -> {
                    path.reset(); path.moveTo(56f, inset); path.lineTo(304f, inset)
                    path.quadTo(360-inset, inset, 360-inset, 57f)
                    path.cubicTo(326f, 180f, 354f, 330f, 360-inset, 463f)
                    path.quadTo(360-inset, 520-inset, 304f, 520-inset); path.lineTo(56f, 520-inset)
                    path.quadTo(inset, 520-inset, inset, 463f)
                    path.cubicTo(6f, 330f, 34f, 180f, inset, 57f)
                    path.quadTo(inset, inset, 56f, inset); path.close(); c.drawPath(path, p)
                }
                else -> {
                    val cut = when (z) { 33 -> 13f; 39 -> 42f; 42 -> 8f; else -> 25f }
                    path.reset(); path.moveTo(inset+cut, inset); path.lineTo(360-inset-cut, inset)
                    path.lineTo(360-inset, inset+cut); path.lineTo(360-inset, 520-inset-cut)
                    path.lineTo(360-inset-cut, 520-inset); path.lineTo(inset+cut, 520-inset)
                    path.lineTo(inset, 520-inset-cut); path.lineTo(inset, inset+cut); path.close(); c.drawPath(path, p)
                }
            }
        }
        for (sx in floatArrayOf(1f, -1f)) for (sy in floatArrayOf(1f, -1f)) {
            c.save(); c.translate(if (sx > 0) 0f else 360f, if (sy > 0) 0f else 520f); c.scale(sx, sy)
            when (z) {
                32 -> { // Iris ring and optical calibration marks.
                    arc(c, 34f, 34f, 17f, 0f, 270f, a, 2f)
                    arc(c, 34f, 34f, 10f, t*57.29578f, 220f, silver)
                    for (i in 0..3) line(c, 57f+i*6, 28f, 57f+i*6, 33f, b)
                }
                33 -> for (i in 0..3) {
                    val q = i*7f
                    poly(c, fade(a, 185-i*24), 29f, 69f-q, 29f, 29f, 69f-q, 29f, 59f-q, 37f, 37f, 59f-q, w=1.6f)
                }
                34 -> { // Art deco light-meter rays.
                    for (i in 0..5) {
                        val angle = i*PI.toFloat()/10
                        line(c, 31f+cos(angle)*12, 31f+sin(angle)*12, 31f+cos(angle)*36, 31f+sin(angle)*36, a)
                    }
                    oval(c, 32f, 32f, 7f, 7f, b)
                }
                35 -> {
                    path.reset(); path.moveTo(30f, 85f); path.cubicTo(58f, 69f, 14f, 48f, 56f, 29f)
                    ink(a, 2f); c.drawPath(path, p)
                    poly(c, b, 34f, 32f, 42f, 47f, 34f, 55f, 28f, 46f)
                }
                36 -> poly(c, a, 28f, 76f, 44f, 48f, 34f, 48f, 57f, 28f, 48f, 43f, 60f, 43f, w=2f)
                37 -> {
                    arc(c, 32f, 32f, 23f, 0f, 90f, a, 2f)
                    for (i in 0..5) {
                        val angle = i*PI.toFloat()/10
                        line(c, 32f+cos(angle)*16, 32f+sin(angle)*16, 32f+cos(angle)*21, 32f+sin(angle)*21, silver)
                    }
                    line(c, 32f, 32f, 32f+cos(t)*13, 32f+sin(t)*13, b, 2f)
                }
                38 -> {
                    for (i in 0..4) line(c, 30f+i*5, 30f+i*5, 31f+i*10, 72f-i*8, a, 1.5f)
                    sparkle(c, 33f, 33f, 7f+sin(t*2), silver)
                }
                39 -> {
                    poly(c, fade(b, 120), 28f, 67f, 28f, 46f, 46f, 28f, 67f, 28f, w=2f)
                    poly(c, a, 32f, 60f, 42f, 42f, 60f, 32f, 52f, 53f, w=1f)
                }
                40 -> for (i in 0..2) {
                    box(c, 29f+i*12, 29f, 38f+i*12, 38f, 2f, silver)
                    box(c, 29f, 41f+i*12, 38f, 50f+i*12, 2f, fade(a, 160-i*30))
                }
                41 -> for (i in 0..3) arc(c, 33f, 33f, 9f+i*7, 0f, 90f, if (i%2 == 0) a else b, 2f)
                42 -> {
                    box(c, 28f, 28f, 63f, 39f, 2f, 0xFF455565.toInt())
                    for (i in 0..3) poly(c, a, 29f+i*8, 39f, 33f+i*8, 47f, 37f+i*8, 39f)
                    oval(c, 33f, 33f, 2f, 2f, silver)
                }
            }
            c.restore()
        }
        for (side in floatArrayOf(20f, 340f)) for (i in 0..13) {
            val y = 115f+i*21f
            val shimmer = (90+130*(.5f+.5f*sin(t*2-i*.5f))).toInt()
            val col = fade(a, shimmer)
            when (z) {
                32, 37 -> line(c, side-3, y, side+if (i%3 == 0) 5 else 1, y, col, 1.4f)
                33, 39 -> poly(c, col, side, y-5, side+3, y, side, y+5, side-3, y, w=1f)
                34 -> { line(c, side-3, y+5, side, y-4, col); line(c, side, y-4, side+3, y+5, col) }
                35 -> oval(c, side+sin(t+i)*2, y, 2f, 4f, col)
                36 -> line(c, side-3, y+5, side+3, y-5, col, 2f)
                38 -> sparkle(c, side, y, if (i%3 == 0) 4f else 2f, col)
                40 -> box(c, side-3, y-5, side+3, y+5, 2f, col)
                41 -> oval(c, side, y, 3f, 8f, col, 1.2f)
                42 -> poly(c, col, side-3, y-4, side+4, y, side-3, y+4)
            }
        }
        glow(c, 180f, 20f, 22f, fade(rarityColor, (65+20*sin(t*2)).toInt()))
        poly(c, rarityColor, 180f, 12f, 186f, 20f, 180f, 28f, 174f, 20f)
        line(c, 180f, 13f, 175f, 20f, silver)
    }

    fun draw(c: Canvas, z: Int, t: Float) {
        glow(c, 180f, 209f, 112f, fade(accent(z), 35))
        oval(c, 180f, 301f, 87f, 8f, 0x40121A2C)
        when (z) {
            32 -> infrared(c, t)
            33 -> arsenic(c, t)
            34 -> lightMeter(c, t)
            35 -> bromine(c, t)
            36 -> camera(c, t)
            37 -> clock(c, t)
            38 -> flare(c, t)
            39 -> laser(c, t)
            40 -> ceramicKnife(c, t)
            41 -> superconductingCoil(c, t)
            42 -> drill(c, t)
        }
    }

    private fun infrared(c: Canvas, t: Float) {
        // False-colour rays evoke infrared transmission; Ge is opaque to visible light.
        for (i in 0..3) {
            val y = 173f+i*20
            line(c, 54f, y, 148f, y, 0x55ED8669, 2f)
            val x = 56f+((t/tau*2+i*.2f)%1f)*85
            glow(c, x, y, 8f, 0xAAFFB17C.toInt())
        }
        box(c, 156f, 144f, 252f, 276f, 12f, 0xFF293E53.toInt())
        for (x in 163..247 step 9) line(c, x.toFloat(), 154f, x.toFloat(), 267f, 0xFF637C8F.toInt(), 2f)
        oval(c, 155f, 210f, 46f, 70f, 0xFF0C1723.toInt())
        oval(c, 155f, 210f, 42f, 65f, silver, 2f)
        oval(c, 155f, 210f, 33f, 55f, 0xFF245566.toInt())
        oval(c, 155f, 210f, 27f, 47f, 0xFF111F36.toInt())
        ink(0xFFC4EFF5.toInt(), 2f); c.drawArc(127f, 161f, 183f, 259f, 210f, 73f, false, p)
        oval(c, 158f+sin(t)*9, 196f, 7f, 22f, 0x5566D9EB)
        for (i in 0..3) line(c, 253f, 173f+i*20, 303f, 210f, 0x55ED8669, 1.5f)
        glow(c, 301f, 210f, 16f, 0x88ED8669.toInt())
        box(c, 174f, 277f, 235f, 288f, 3f, 0xFF71869A.toInt())
    }

    private fun arsenic(c: Canvas, t: Float) {
        // A large brittle grey specimen: stable facets, moving reflection only.
        poly(c, 0xFF293746.toInt(), 80f, 272f, 123f, 246f, 232f, 244f, 285f, 279f, 230f, 298f, 129f, 294f)
        for (i in 0..5) {
            val x = 105f+i*14; val y = 269f-i*22; val w = 139f-i*12
            poly(c, 0xFF52687D.toInt(), x, y, x+w, y-9, x+w-15, y+11, x+8, y+19)
            poly(c, if (i%2 == 0) 0xFF9CACBE.toInt() else 0xFF788EA4.toInt(),
                x, y, x+22, y-24, x+w-19, y-35, x+w, y-9)
            line(c, x+1, y, x+w, y-9, silver, 1.6f)
            line(c, x+22, y-24, x+w-19, y-35, 0xFFBAC8D4.toInt())
            line(c, x+35, y-5, x+53, y-17, 0xFF40586D.toInt())
        }
        poly(c, silver, 84f, 286f, 101f, 278f, 111f, 284f, 98f, 293f)
        sparkle(c, 169f, 159f, 2f+2f*(.5f+.5f*sin(t)), fade(silver, (145+70*sin(t)).toInt()))
    }

    private fun lightMeter(c: Canvas, t: Float) {
        // Historic selenium exposure meter: honeycomb cell below an analogue dial.
        val light = .5f+.5f*sin(t*2)
        glow(c, 91f, 139f, 38f, fade(0xFFFFCA70.toInt(), (40+45*light).toInt()))
        oval(c, 91f, 139f, 12f, 12f, 0xFFFFD184.toInt())
        for (i in 0..7) {
            val a = i*tau/8
            line(c, 91f+cos(a)*19, 139f+sin(a)*19, 91f+cos(a)*26, 139f+sin(a)*26, 0xFFDBA160.toInt(), 1.5f)
        }
        box(c, 124f, 154f, 258f, 288f, 14f, 0xFF784448.toInt())
        box(c, 129f, 159f, 253f, 283f, 11f, 0xFF261F2B.toInt())
        box(c, 139f, 168f, 243f, 224f, 8f, 0xFFE9D9B6.toInt())
        arc(c, 191f, 218f, 39f, 204f, 132f, 0xFF76514A.toInt())
        for (i in 0..10) {
            val a = (205f+i*13)*PI.toFloat()/180
            line(c, 191f+cos(a)*32, 218f+sin(a)*32, 191f+cos(a)*37, 218f+sin(a)*37, 0xFF5A4042.toInt())
        }
        val angle = (-2.6f+light*2f)
        line(c, 191f, 216f, 191f+cos(angle)*31, 216f+sin(angle)*31, 0xFFA73A43.toInt(), 2f)
        oval(c, 191f, 216f, 3f, 3f, 0xFF5A4042.toInt())
        for (row in 0..3) for (col in 0..8) {
            val x = 143f+col*11; val y = 236f+row*11
            box(c, x, y, x+8, y+8, 1f, if ((row+col)%2 == 0) 0xFF825749.toInt() else 0xFFAF7954.toInt())
        }
        for (i in 0..2) line(c, 102f+i*10, 166f, 133f+i*20, 230f, fade(0xFFFFD184.toInt(), (35+45*light).toInt()))
        box(c, 170f, 144f, 211f, 154f, 3f, silver)
    }

    private fun bromine(c: Canvas, t: Float) {
        glass.reset(); glass.moveTo(167f, 121f); glass.lineTo(193f, 121f); glass.lineTo(195f, 168f)
        glass.cubicTo(207f, 191f, 242f, 210f, 242f, 252f)
        glass.cubicTo(242f, 309f, 118f, 309f, 118f, 252f)
        glass.cubicTo(118f, 210f, 153f, 191f, 165f, 168f); glass.close()
        ink(0x183D94AE); c.drawPath(glass, p)
        c.save(); c.clipPath(glass)
        ink(0xFFD04D30.toInt()); p.shader = LinearGradient(0f, 225f, 0f, 300f,
            0xFFAD3825.toInt(), 0xFF3F1922.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(111f, 235f, 249f, 307f, p)
        oval(c, 180f, 235f, 64f, 8f, 0xFFCC603E.toInt())
        for (i in 0..5) {
            val y = 228f-i*14
            val x = 181f+sin(t*2+i*.8f)*12
            glow(c, x, y, 18f+i*2, 0x36B85B39)
        }
        ink(0x55F6A06D, 1.5f); c.drawArc(127f, 267f, 232f, 293f, 15f, 145f, false, p)
        c.restore()
        ink(0xFFB6D1DB.toInt(), 2f); c.drawPath(glass, p)
        box(c, 162f, 115f, 198f, 128f, 5f, 0xFF698897.toInt())
        line(c, 169f, 130f, 169f, 162f, 0xAADAF2F6.toInt(), 2f)
        ink(0xAADAF2F6.toInt(), 3f); c.drawArc(130f, 203f, 230f, 291f, 170f, 65f, false, p)
    }

    private fun camera(c: Canvas, t: Float) {
        val light = (.5f+.5f*sin(t*2)).pow(4)
        box(c, 91f, 210f, 270f, 289f, 12f, 0xFF75899B.toInt())
        box(c, 96f, 216f, 265f, 281f, 8f, 0xFF233144.toInt())
        box(c, 110f, 203f, 142f, 211f, 3f, silver)
        box(c, 168f, 184f, 187f, 209f, 3f, 0xFF8DA0B2.toInt())
        box(c, 137f, 126f, 239f, 184f, 8f, silver)
        box(c, 144f, 134f, 232f, 175f, 5f, 0xFFB1B5D6.toInt())
        for (i in 0..7) line(c, 149f+i*11, 139f, 149f+i*11, 170f, 0xFFE1DEEF.toInt(), 2f)
        glow(c, 188f, 154f, 70f, fade(0xFFE4D7FF.toInt(), (25+105*light).toInt()))
        box(c, 149f, 142f, 227f, 166f, 4f, fade(Color.WHITE, (40+160*light).toInt()))
        oval(c, 182f, 250f, 35f, 35f, silver)
        oval(c, 182f, 250f, 29f, 29f, dark)
        oval(c, 182f, 250f, 22f, 22f, 0xFF435986.toInt())
        oval(c, 182f, 250f, 14f, 14f, 0xFF152637.toInt())
        arc(c, 182f, 250f, 22f, 215f, 60f, 0xFFC5DDE9.toInt(), 2f)
        box(c, 232f, 224f, 253f, 236f, 2f, 0xFF8EB4CB.toInt())
        for (i in 0..2) line(c, 109f, 241f+i*8, 132f, 241f+i*8, 0xFF536378.toInt(), 2f)
    }

    private fun clock(c: Canvas, t: Float) {
        // The dial symbolises timekeeping; the inset is the rubidium vapour cell.
        oval(c, 166f, 209f, 72f, 77f, 0xFF53627C.toInt())
        oval(c, 166f, 209f, 65f, 70f, 0xFF121C30.toInt())
        oval(c, 166f, 209f, 60f, 65f, 0xFFB5A1FF.toInt(), 1.5f)
        for (i in 0..59) {
            val a = i*tau/60; val r = if (i%5 == 0) 49f else 54f
            line(c, 166f+cos(a)*r, 209f+sin(a)*r, 166f+cos(a)*58, 209f+sin(a)*58,
                if (i%5 == 0) silver else 0xFF566782.toInt(), if (i%5 == 0) 2f else 1f)
        }
        line(c, 166f, 209f, 145f, 187f, silver, 4f)
        line(c, 166f, 209f, 193f, 190f, silver, 3f)
        line(c, 166f, 209f, 166f+cos(t*2)*46, 209f+sin(t*2)*46, 0xFFECAD7D.toInt(), 1.5f)
        oval(c, 166f, 209f, 4f, 4f, 0xFFECAD7D.toInt())
        box(c, 222f, 228f, 280f, 279f, 6f, 0xFF526C89.toInt())
        box(c, 230f, 236f, 272f, 271f, 4f, 0xFF251D3C.toInt())
        glow(c, 251f, 253f, 20f, fade(0xFFB5A1FF.toInt(), (80+20*sin(t*4)).toInt()))
        line(c, 211f, 253f, 288f, 253f, 0xAADAAAFF.toInt(), 1.5f)
        for (i in 0..3) oval(c, 239f+i*8, 248f+sin(t+i)*7, 1.8f, 1.8f, silver)
        box(c, 126f, 288f, 209f, 296f, 4f, 0xFF7F8FAA.toInt())
    }

    private fun flare(c: Canvas, t: Float) {
        // Marine distress flare, unlike the magnesium card's rocket and white burst.
        for (i in 0..3) {
            val y = 147f-i*12
            glow(c, 193f+sin(t+i)*11, y, 25f+i*5, 0x237E8B9E)
        }
        glow(c, 177f, 184f, 75f, fade(0xFFFF3D51.toInt(), (65+15*sin(t*3)).toInt()))
        path.reset(); path.moveTo(161f, 225f)
        path.cubicTo(122f, 197f, 165f, 175f, 158f+sin(t*3)*6, 150f)
        path.quadTo(178f, 169f, 177f, 183f)
        path.quadTo(201f, 160f, 202f, 140f)
        path.cubicTo(231f, 194f, 195f, 215f, 184f, 231f); path.close()
        ink(0xFFE94455.toInt()); c.drawPath(path, p)
        poly(c, 0xFFFFB077.toInt(), 162f, 221f, 170f, 186f, 178f, 207f, 192f, 181f, 184f, 225f)
        c.save(); c.rotate(13f, 174f, 260f)
        box(c, 162f, 224f, 186f, 295f, 4f, 0xFFCE5E54.toInt())
        box(c, 163f, 231f, 185f, 247f, 1f, 0xFFF3D6BD.toInt())
        for (i in 0..4) line(c, 164f, 260f+i*6, 184f, 260f+i*6, 0xFF6F3842.toInt(), 2f)
        c.restore()
        for (i in 0..11) {
            val q = (t/tau*3+i/12f)%1f
            val x = 179f+sin(i*2.4f)*q*72
            val y = 208f-q*83
            line(c, x, y+4, x+sin(i.toFloat())*2, y, fade(0xFFFFA078.toInt(), (220*(1-q)).toInt()), 1.5f)
        }
    }

    private fun laser(c: Canvas, t: Float) {
        // A doped YAG rod between two mirrors, with schematic false-colour beam.
        box(c, 84f, 271f, 282f, 288f, 4f, 0xFF3E5368.toInt())
        for (x in floatArrayOf(104f, 258f)) {
            box(c, x-5, 187f, x+5, 272f, 3f, 0xFF768C9E.toInt())
            oval(c, x, 207f, 8f, 39f, 0xFFB2D4E0.toInt())
            oval(c, x-2, 207f, 3f, 31f, 0xFFECF8F4.toInt())
        }
        poly(c, 0xFF386B69.toInt(), 132f, 183f, 222f, 183f, 237f, 197f, 237f, 228f, 144f, 228f, 132f, 214f)
        poly(c, 0xFF90CAB7.toInt(), 132f, 183f, 222f, 183f, 237f, 197f, 146f, 197f)
        poly(c, 0xFF5A9C93.toInt(), 146f, 197f, 237f, 197f, 237f, 228f, 146f, 228f)
        line(c, 146f, 197f, 237f, 197f, 0xFFC3FFE2.toInt(), 2f)
        line(c, 77f, 207f, 306f, 207f, 0x337FFFC0, 12f)
        line(c, 77f, 207f, 306f, 207f, 0xCC9FFFD2.toInt(), 2f)
        val x = 112f+((t/tau*4)%1f)*192
        glow(c, x, 207f, 19f, 0x99C3FFE6.toInt())
        for (i in 0..2) {
            val xx = 158f+i*26
            line(c, xx, 147f, xx, 174f, 0xAAAE9AE8.toInt(), 2f)
            poly(c, 0xFFAE9AE8.toInt(), xx-4, 169f, xx+4, 169f, xx, 176f)
        }
    }

    private fun ceramicKnife(c: Canvas, t: Float) {
        c.save(); c.rotate(-30f, 183f, 215f)
        path.reset(); path.moveTo(120f, 217f); path.lineTo(263f, 167f)
        path.cubicTo(252f, 213f, 211f, 238f, 127f, 244f); path.close()
        ink(Color.WHITE); p.shader = LinearGradient(0f, 182f, 0f, 249f,
            intArrayOf(0xFFB4CAD6.toInt(), 0xFFF9FCF1.toInt(), 0xFFCFDFE3.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        ink(0xFF8BA9BC.toInt(), 1.5f); c.drawPath(path, p)
        path.reset(); path.moveTo(132f, 238f); path.quadTo(226f, 225f, 256f, 180f)
        ink(Color.WHITE, 2f); c.drawPath(path, p)
        box(c, 68f, 215f, 130f, 247f, 9f, 0xFF233F53.toInt())
        box(c, 120f, 214f, 132f, 249f, 3f, 0xFF7996A6.toInt())
        for (x in floatArrayOf(82f, 109f)) oval(c, x, 230f, 3f, 3f, silver)
        sparkle(c, 207f+sin(t)*15, 205f, 4f, 0xBFFFFFFF.toInt())
        c.restore()
        // Three small ceramic tiles anchor the material theme.
        for (i in 0..2) poly(c, if (i%2 == 0) 0xFFCEE3E8.toInt() else 0xFF9CB9CD.toInt(),
            183f+i*26, 284f, 195f+i*26, 277f, 207f+i*26, 284f, 195f+i*26, 291f)
    }

    private fun superconductingCoil(c: Canvas, t: Float) {
        // Superconducting alloy winding in a cryostat; field lines are symbolic.
        for (i in 0..3) {
            ink(fade(0xFF70C9FF.toInt(), 60+i*15), 1f)
            c.drawOval(69f-i*5, 163f-i*13, 291f+i*5, 263f+i*13, p)
        }
        for (i in 10 downTo 0) {
            val x = 123f+i*11
            oval(c, x, 214f, 28f, 61f, 0xFF4A6383.toInt(), 6f)
            ink(0xFFB9D8EF.toInt(), 3f); c.drawArc(x-28, 153f, x+28, 275f, 100f, 160f, false, p)
        }
        oval(c, 123f, 214f, 21f, 48f, dark)
        oval(c, 123f, 214f, 21f, 48f, 0xFF80DFFF.toInt(), 2f)
        oval(c, 123f, 214f, 12f, 31f, 0xFF263E61.toInt())
        line(c, 66f, 214f, 298f, 214f, 0x5566C9FF, 2f)
        for (i in 0..2) {
            val a = t*2+i*tau/3
            glow(c, 180f+cos(a)*115, 214f+sin(a)*79, 9f, 0xAA70C9FF.toInt())
        }
        box(c, 111f, 280f, 143f, 293f, 3f, 0xFF7B94A8.toInt())
        box(c, 229f, 280f, 258f, 293f, 3f, 0xFF7B94A8.toInt())
    }

    private fun drill(c: Canvas, t: Float) {
        // A large twist drill: molybdenum-bearing tool steel, not pure Mo.
        c.save(); c.rotate(32f, 182f, 214f)
        metal(165f, 199f); c.drawRoundRect(165f, 111f, 199f, 163f, 3f, 3f, p)
        box(c, 162f, 157f, 202f, 167f, 3f, 0xFF738B9C.toInt())
        metal(164f, 200f); c.drawRect(164f, 167f, 200f, 274f, p)
        c.save(); c.clipRect(164f, 167f, 200f, 274f)
        val shift = (t/tau*4%1f)*28
        for (i in -2..5) {
            val y = 157f+i*28+shift
            path.reset(); path.moveTo(159f, y)
            path.cubicTo(186f, y+7, 177f, y+14, 205f, y+22)
            ink(0xFF31465D.toInt(), 9f); c.drawPath(path, p)
            ink(0xFFF0E2C5.toInt(), 1.6f); c.drawPath(path, p)
        }
        c.restore()
        poly(c, 0xFF778EA2.toInt(), 164f, 274f, 200f, 274f, 182f, 295f)
        poly(c, 0xFFE2EDF0.toInt(), 164f, 274f, 182f, 279f, 182f, 295f)
        line(c, 169f, 117f, 169f, 151f, silver, 2f)
        c.restore()
        for (i in 0..3) {
            ink(0xFFBFA378.toInt(), 2f)
            c.drawArc(235f+i*9, 268f+i*5, 247f+i*9, 276f+i*5, 20f, 270f, false, p)
        }
    }
}
