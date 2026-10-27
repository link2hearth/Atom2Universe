package com.Atom2Universe.app.games.glutton

import android.graphics.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape
import kotlin.math.*

/** Code-drawn stand-ins baked once per shape; WebP sheets take over whenever the atlas has them. */
internal class GluttonArt(var atlas: GluttonAtlas? = null) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val dst = RectF()
    private val fur = Path().apply {
        val count = 80
        for (i in 0..count) {
            val a = i * (2f * PI.toFloat() / count)
            val r = 43f + 3f * cos(a * 16f)
            val x = cos(a) * r; val y = sin(a) * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val bodyGradient = RadialGradient(-16f, -24f, 85f,
        intArrayOf(0xFFD6FFF0.toInt(), 0xFF70DFCB.toInt(), 0xFF28A9AB.toInt()),
        floatArrayOf(0f, .53f, 1f), Shader.TileMode.CLAMP)
    private val sprites = arrayOfNulls<Bitmap>(Shape.entries.size)
    private fun sprite(shape: Shape): Bitmap = sprites[shape.ordinal] ?: Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.translate(80f, 74f); canvas.scale(1.32f, 1.32f)
        prop(canvas, shape)
        sprites[shape.ordinal] = it
    }
    private fun fill(color: Int) { p.shader = null; p.color = color; p.style = Paint.Style.FILL }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill(color); c.drawOval(l, t, r, b, p)
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill(color); c.drawRoundRect(l, t, r, b, radius, radius, p)
    }
    private fun line(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, color: Int, width: Float) {
        fill(color); p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND; c.drawLine(ax, ay, bx, by, p)
    }

    fun item(c: Canvas, prop: Prop, x: Float, y: Float, radius: Float, angle: Float, alpha: Int = 255) {
        c.save(); c.translate(x, y); c.rotate(angle)
        val extent = radius * 1.48f
        dst.set(-extent, -extent, extent, extent)
        fill(Color.WHITE); p.alpha = alpha
        val theme = prop.theme
        if (theme == null || atlas?.draw(c, theme, prop.page, prop.cell, dst, p) != true) c.drawBitmap(sprite(prop.shape), null, dst, p)
        p.alpha = 255; c.restore()
    }
    fun shape(c: Canvas, shape: Shape, x: Float, y: Float, radius: Float, angle: Float) {
        c.save(); c.translate(x, y); c.rotate(angle)
        val extent = radius * 1.48f
        dst.set(-extent, -extent, extent, extent)
        fill(Color.WHITE); c.drawBitmap(sprite(shape), null, dst, p); c.restore()
    }

    fun body(c: Canvas, x: Float, y: Float, radius: Float, time: Float, lookX: Float = 0f,
        lookY: Float = 0f, pop: Float = 0f, hungry: Boolean = false, hurt: Boolean = false,
        fever: Boolean = false) {
        c.save(); c.translate(x, y)
        val wobble = sin(time * 8f) * .025f
        c.scale(radius / 45f * (1f + pop * .18f + wobble), radius / 45f * (1f - pop * .14f - wobble))
        oval(c, -40f, 23f, 44f, 48f, 0x253E656D)
        oval(c, -41f, -47f, -15f, -14f, 0xFF47BEB5.toInt())
        oval(c, 15f, -47f, 41f, -14f, 0xFF47BEB5.toInt())
        oval(c, -35f, -41f, -21f, -24f, 0xFFFFB9C1.toInt())
        oval(c, 21f, -41f, 35f, -24f, 0xFFFFB9C1.toInt())
        oval(c, -31f, 29f, -8f, 47f, 0xFF219B9F.toInt())
        oval(c, 8f, 29f, 31f, 47f, 0xFF219B9F.toInt())
        fill(Color.WHITE); p.shader = bodyGradient; c.drawPath(fur, p); p.shader = null
        oval(c, -29f, -29f, 7f, -12f, 0x6BEEFFF4)
        line(c, -7f, -40f, -11f, -52f, 0xFFB9F9D7.toInt(), 5f)
        line(c, -6f, -40f, 4f, -50f, 0xFFB9F9D7.toInt(), 5f)
        val dx = lookX * 4f; val dy = lookY * 3f
        oval(c, -33f, 2f, -14f, 14f, 0xC9FFA5B8.toInt())
        oval(c, 14f, 2f, 33f, 14f, 0xC9FFA5B8.toInt())
        if (hurt) {
            for (i in 0..1) {
                val side = if (i == 0) -1 else 1
                line(c, side * 16f - 5f, -11f, side * 16f + 5f, -1f, INK, 3.5f)
                line(c, side * 16f + 5f, -11f, side * 16f - 5f, -1f, INK, 3.5f)
            }
        } else {
            oval(c, -25f, -19f, -7f, 1f, Color.WHITE)
            oval(c, 7f, -19f, 25f, 1f, Color.WHITE)
            oval(c, -21f + dx, -16f + dy, -10f + dx, -2f + dy, INK)
            oval(c, 10f + dx, -16f + dy, 21f + dx, -2f + dy, INK)
            oval(c, -19f + dx, -15f + dy, -15f + dx, -10f + dy, Color.WHITE)
            oval(c, 12f + dx, -15f + dy, 16f + dx, -10f + dy, Color.WHITE)
        }
        if (hungry) {
            oval(c, -14f, 4f, 14f, 28f, INK)
            oval(c, -9f, 19f, 9f, 28f, 0xFFFF96B1.toInt())
        } else {
            fill(INK); p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(-10f, 3f, 10f, 18f, 5f, 170f, false, p); p.style = Paint.Style.FILL
        }
        round(c, -27f, 28f, 28f, 37f, 5f, if (fever) GOLD else CORAL)
        c.save(); c.rotate(-16f); round(c, 15f, 29f, 28f, 55f, 4f, if (fever) GOLD else CORAL); c.restore()
        if (fever) { star(c, -34f, -36f, 9f, GOLD); star(c, 36f, -29f, 7f, GOLD) }
        c.restore()
    }

    /** Alarm bubble above a creature that is hunting you or running from you. */
    fun alarm(c: Canvas, x: Float, y: Float, size: Float, hunting: Boolean) {
        oval(c, x - size * .45f, y - size, x + size * .45f, y, if (hunting) CORAL else 0xFFFFF0BD.toInt())
        if (hunting) {
            line(c, x, y - size * .78f, x, y - size * .45f, Color.WHITE, size * .13f)
            oval(c, x - size * .08f, y - size * .3f, x + size * .08f, y - size * .14f, Color.WHITE)
        } else {
            fill(INK); p.style = Paint.Style.STROKE; p.strokeWidth = size * .11f; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(x - size * .22f, y - size * .85f, x + size * .22f, y - size * .4f, 200f, 200f, false, p)
            p.style = Paint.Style.FILL; oval(c, x - size * .07f, y - size * .3f, x + size * .07f, y - size * .16f, INK)
        }
    }

    fun star(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        path.rewind()
        for (i in 0..9) {
            val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f
            val r = if (i % 2 == 0) radius else radius * .45f
            val px = x + cos(a) * r; val py = y + sin(a) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); fill(color); c.drawPath(path, p)
    }

    /** Each stand-in is painted in a shared -50…50 space then baked into its sprite. */
    private fun prop(c: Canvas, kind: Shape) {
        oval(c, -43f, 18f, 46f, 46f, 0x243A405B)
        when (kind) {
            Shape.CRUMB -> {
                path.rewind(); path.moveTo(-35f, 22f); path.lineTo(-23f, -30f)
                path.quadTo(-9f, -43f, 8f, -28f); path.lineTo(36f, 19f); path.close()
                fill(0xFFD08B42.toInt()); c.drawPath(path, p)
                c.save(); c.translate(-2f, -6f); c.scale(.85f, .75f)
                fill(0xFFFFD88A.toInt()); c.drawPath(path, p); c.restore()
                oval(c, -17f, -8f, -7f, 2f, 0xFFD29650.toInt())
                oval(c, 3f, 8f, 11f, 16f, 0xFFD29650.toInt())
            }
            Shape.BERRY -> {
                oval(c, -37f, -29f, 37f, 36f, 0xFFD24E7E.toInt())
                oval(c, -33f, -32f, 28f, 23f, 0xFFF8799E.toInt())
                oval(c, -25f, -27f, -6f, -16f, 0x99FFD1DA.toInt())
                for (i in 0..4) oval(c, -21f + i * 9f, 3f + i % 2 * 12f, -17f + i * 9f, 9f + i % 2 * 12f, GOLD)
                star(c, 0f, -29f, 22f, 0xFF509D77.toInt())
            }
            Shape.COOKIE -> {
                oval(c, -40f, -34f, 40f, 40f, 0xFFB77D4E.toInt())
                oval(c, -40f, -40f, 40f, 31f, 0xFFF1BF77.toInt())
                oval(c, -31f, -32f, 29f, 23f, 0xFFFBD698.toInt())
                for (i in 0..6) {
                    val a = i * 2.4f; val r = 10f + i * 3f
                    oval(c, cos(a) * r - 5f, sin(a) * r - 9f, cos(a) * r + 5f, sin(a) * r + 1f, 0xFF846052.toInt())
                }
            }
            Shape.DONUT -> {
                oval(c, -42f, -34f, 42f, 40f, 0xFFCA8A5C.toInt())
                oval(c, -42f, -41f, 42f, 30f, 0xFFF497B7.toInt())
                oval(c, -31f, -34f, 31f, 21f, 0xFFFFB9D0.toInt())
                oval(c, -13f, -16f, 13f, 8f, 0xFFBD815B.toInt())
                oval(c, -10f, -12f, 10f, 7f, 0xFFE9C6B1.toInt())
                for (i in 0..7) {
                    val a = i * .79f
                    line(c, cos(a) * 28f, sin(a) * 24f - 7f, cos(a) * 28f + 5f, sin(a) * 24f - 3f,
                        if (i % 2 == 0) GOLD else 0xFF62C8BA.toInt(), 3f)
                }
            }
            Shape.SOCK -> {
                path.rewind(); path.moveTo(-21f, -40f); path.lineTo(19f, -40f); path.lineTo(19f, 3f)
                path.quadTo(48f, 10f, 36f, 30f); path.quadTo(18f, 47f, -15f, 31f)
                path.quadTo(-27f, 22f, -21f, -40f); path.close()
                fill(0xFF8E86CB.toInt()); c.drawPath(path, p)
                round(c, -24f, -43f, 23f, -25f, 5f, 0xFFDAD5FA.toInt())
                line(c, -14f, -12f, 12f, -12f, 0xFFBFB6EE.toInt(), 8f)
                star(c, -1f, 10f, 11f, GOLD)
            }
            Shape.DUCK -> {
                oval(c, -41f, -6f, 34f, 35f, 0xFFE8AE45.toInt())
                oval(c, -40f, -13f, 35f, 28f, GOLD)
                oval(c, -3f, -43f, 37f, -3f, 0xFFFFDF80.toInt())
                round(c, 25f, -24f, 48f, -13f, 5f, CORAL)
                oval(c, 18f, -31f, 24f, -23f, INK)
                oval(c, -25f, -3f, 5f, 17f, 0xFFF4B950.toInt())
                line(c, -35f, -5f, -46f, -19f, GOLD, 9f)
            }
            Shape.POTPLANT -> {
                line(c, 0f, 11f, 0f, -37f, 0xFF398E7C.toInt(), 5f)
                c.save(); c.rotate(-35f); oval(c, -29f, -37f, 1f, -1f, 0xFF63C19B.toInt()); c.restore()
                c.save(); c.rotate(38f); oval(c, -1f, -45f, 30f, -9f, 0xFF459E82.toInt()); c.restore()
                oval(c, -10f, -51f, 10f, -21f, 0xFF83D6A2.toInt())
                round(c, -24f, 7f, 24f, 38f, 9f, CORAL)
                round(c, -28f, 2f, 28f, 14f, 5f, 0xFFFFB3AA.toInt())
                line(c, -15f, 21f, -15f, 30f, 0x88FFE9CE.toInt(), 4f)
            }
            Shape.LAMP -> {
                oval(c, -28f, 20f, 28f, 39f, 0xFFB18BA5.toInt())
                line(c, 0f, -11f, 0f, 27f, 0xFFB9909D.toInt(), 8f)
                path.rewind(); path.moveTo(-22f, -43f); path.lineTo(22f, -43f)
                path.lineTo(40f, -2f); path.quadTo(0f, 17f, -40f, -2f); path.close()
                fill(0xFFE5B755.toInt()); c.drawPath(path, p)
                line(c, -21f, -35f, -31f, -7f, 0xFFFFE49B.toInt(), 8f)
                oval(c, -22f, -49f, 22f, -38f, 0xFFFFDF87.toInt())
            }
            Shape.CHAIR -> {
                round(c, -29f, 16f, -19f, 44f, 4f, 0xFF8B7A96.toInt())
                round(c, 19f, 16f, 29f, 44f, 4f, 0xFF8B7A96.toInt())
                round(c, -37f, -42f, 37f, 27f, 15f, 0xFF658FBC.toInt())
                round(c, -31f, -38f, 31f, 0f, 12f, 0xFF9CCEE0.toInt())
                round(c, -33f, -1f, 33f, 27f, 10f, 0xFFBCE1E8.toInt())
                round(c, -42f, -10f, -30f, 25f, 6f, 0xFF7EB4CA.toInt())
                round(c, 30f, -10f, 42f, 25f, 6f, 0xFF7EB4CA.toInt())
            }
            Shape.TABLE -> {
                round(c, -33f, -14f, -23f, 40f, 4f, 0xFFB58677.toInt())
                round(c, 23f, -14f, 33f, 40f, 4f, 0xFFB58677.toInt())
                oval(c, -46f, -35f, 46f, 29f, 0xFFC59479.toInt())
                oval(c, -46f, -43f, 46f, 20f, 0xFFF3CE9C.toInt())
                oval(c, -35f, -36f, 35f, 12f, 0xFFFFDEAE.toInt())
                oval(c, -14f, -26f, 14f, -2f, 0xFFEAD5D8.toInt())
                oval(c, -8f, -21f, 8f, -7f, 0xFF907283.toInt())
            }
            Shape.SOFA -> {
                round(c, -37f, 20f, -27f, 41f, 3f, 0xFF927180.toInt())
                round(c, 27f, 20f, 37f, 41f, 3f, 0xFF927180.toInt())
                round(c, -44f, -37f, 44f, 30f, 14f, 0xFFB979A6.toInt())
                round(c, -38f, -34f, -2f, 0f, 10f, 0xFFE5A7CA.toInt())
                round(c, 2f, -34f, 38f, 0f, 10f, 0xFFE5A7CA.toInt())
                round(c, -37f, 0f, 37f, 23f, 8f, 0xFFF3C2D8.toInt())
                round(c, -49f, -14f, -35f, 28f, 6f, 0xFFD296B8.toInt())
                round(c, 35f, -14f, 49f, 28f, 6f, 0xFFD296B8.toInt())
                c.save(); c.rotate(-12f); round(c, -24f, -25f, -4f, -5f, 5f, GOLD); c.restore()
            }
            Shape.FRIDGE -> {
                round(c, -32f, -48f, 35f, 40f, 9f, 0xFF73ADA9.toInt())
                round(c, -33f, -48f, 28f, -14f, 7f, 0xFFCAEBDE.toInt())
                round(c, -33f, -10f, 28f, 38f, 7f, 0xFFE4F1DA.toInt())
                round(c, 14f, -38f, 19f, -22f, 2f, 0xFF75AFA4.toInt())
                round(c, 14f, -1f, 19f, 17f, 2f, 0xFF75AFA4.toInt())
                star(c, -12f, 2f, 9f, CORAL)
                oval(c, -17f, -34f, -5f, -22f, GOLD)
            }
            Shape.FLOWERS -> {
                oval(c, -40f, -5f, 40f, 35f, 0xFF609978.toInt())
                for (i in 0..4) {
                    val xx = -25f + i * 12f; val yy = -12f + i % 2 * 12f
                    line(c, xx, yy, xx, 16f, 0xFF4F9472.toInt(), 4f)
                    star(c, xx, yy, 15f, if (i % 2 == 0) CORAL else 0xFFDCC7FF.toInt())
                    oval(c, xx - 4f, yy - 4f, xx + 4f, yy + 4f, GOLD)
                }
            }
            Shape.BIN -> {
                round(c, -28f, -36f, 28f, 38f, 7f, 0xFF65AAA5.toInt())
                round(c, -34f, -42f, 34f, -30f, 5f, 0xFF91D1BE.toInt())
                for (i in -1..1) line(c, i * 17f, -19f, i * 17f, 24f, 0xFF9AD8C3.toInt(), 4f)
            }
            Shape.BIKE, Shape.SCOOTER -> {
                val bike = kind == Shape.BIKE
                for (i in -1..1 step 2) {
                    oval(c, i * 29f - 16f, 8f, i * 29f + 16f, 40f, INK)
                    oval(c, i * 29f - 10f, 14f, i * 29f + 10f, 34f, 0xFFECE8D7.toInt())
                }
                line(c, -29f, 24f, 3f, -12f, CORAL, 6f); line(c, 3f, -12f, 29f, 24f, CORAL, 6f)
                line(c, -29f, 24f, 12f, 24f, CORAL, 5f)
                if (bike) { line(c, -8f, -10f, 12f, 24f, CORAL, 5f); line(c, -17f, -11f, 0f, -11f, INK, 6f) }
                else round(c, -23f, -15f, 18f, 18f, 8f, 0xFFDDA7D0.toInt())
                line(c, 3f, -12f, 12f, -30f, INK, 5f); line(c, 12f, -30f, 27f, -30f, INK, 5f)
            }
            Shape.WHEELBARROW -> {
                oval(c, 10f, 12f, 40f, 42f, INK); oval(c, 17f, 19f, 33f, 35f, GOLD)
                line(c, -40f, -25f, 25f, 25f, 0xFFA78268.toInt(), 7f)
                round(c, -34f, -32f, 35f, 10f, 12f, CORAL)
                round(c, -29f, -34f, 30f, -18f, 9f, 0xFFFFC0A4.toInt())
            }
            Shape.BENCH -> {
                for (i in -1..1 step 2) line(c, i * 30f, -15f, i * 30f, 39f, INK, 5f)
                for (i in 0..2) round(c, -44f, -35f + i * 16f, 44f, -24f + i * 16f, 4f, 0xFFD6A477.toInt())
                round(c, -45f, 13f, 45f, 27f, 4f, 0xFFF0C998.toInt())
            }
            Shape.PARASOL -> {
                line(c, 0f, -13f, 0f, 40f, 0xFFB19580.toInt(), 7f)
                for (i in 0..5) {
                    fill(if (i % 2 == 0) CORAL else 0xFFFFE2AF.toInt())
                    c.drawArc(-45f, -46f, 45f, 26f, i * 60f, 60f, true, p)
                }
                oval(c, -6f, -16f, 6f, -4f, GOLD)
            }
            Shape.TREE -> {
                round(c, -10f, -4f, 11f, 44f, 5f, 0xFFAE8B6A.toInt())
                oval(c, -44f, -48f, 44f, 23f, 0xFF589C7A.toInt())
                oval(c, -37f, -47f, 31f, 10f, 0xFF89C78B.toInt())
                oval(c, -20f, -42f, 10f, -23f, 0xFFAFDD99.toInt())
                oval(c, 15f, -6f, 25f, 4f, CORAL)
            }
            Shape.PLAYHOUSE, Shape.SHED, Shape.HOUSE -> {
                val color = if (kind == Shape.HOUSE) 0xFFD9B3DA.toInt() else 0xFFF1CA90.toInt()
                round(c, -35f, -15f, 35f, 40f, 5f, color)
                path.rewind(); path.moveTo(-46f, -12f); path.lineTo(0f, -50f); path.lineTo(46f, -12f); path.close()
                fill(if (kind == Shape.SHED) 0xFF81B8A5.toInt() else CORAL); c.drawPath(path, p)
                round(c, -9f, 7f, 10f, 40f, 3f, 0xFF8B849E.toInt())
                round(c, -28f, -3f, -14f, 13f, 3f, 0xFFDFEFF1.toInt())
                round(c, 15f, -3f, 29f, 13f, 3f, 0xFFDFEFF1.toInt())
                oval(c, 3f, 23f, 7f, 27f, GOLD)
            }
            Shape.VAN, Shape.CARAVAN, Shape.CAR, Shape.BUS -> {
                val color = when (kind) { Shape.BUS -> GOLD; Shape.VAN -> MINT
                    Shape.CARAVAN -> 0xFFDCB3DC.toInt(); else -> CORAL }
                for (i in -1..1 step 2) oval(c, i * 28f - 10f, 17f, i * 28f + 10f, 37f, INK)
                round(c, -47f, -26f, 47f, 26f, 10f, color)
                round(c, -30f, -37f, 31f, 2f, 9f, color)
                for (i in 0..2) round(c, -24f + i * 19f, -29f, -9f + i * 19f, -6f, 4f, 0xFFD9F0EA.toInt())
                round(c, -44f, 9f, -35f, 17f, 3f, 0xFFFFF6D9.toInt())
                round(c, 35f, 9f, 44f, 17f, 3f, 0xFFFFF6D9.toInt())
            }
            Shape.GREENHOUSE -> {
                round(c, -40f, -25f, 40f, 39f, 4f, 0xFF82C1B6.toInt())
                path.rewind(); path.moveTo(-46f, -24f); path.lineTo(0f, -51f); path.lineTo(46f, -24f); path.close()
                fill(0xFFB5E9D0.toInt()); c.drawPath(path, p)
                for (i in -1..1) {
                    round(c, i * 25f - 10f, -18f, i * 25f + 10f, 28f, 3f, 0xFFBEEDD7.toInt())
                    line(c, i * 25f, -15f, i * 25f, 29f, Color.WHITE, 2f)
                }
                for (i in -1..1 step 2) oval(c, i * 24f - 8f, 7f, i * 24f + 8f, 28f, 0xFF79B693.toInt())
            }
            Shape.CONE -> {
                round(c, -41f, 26f, 41f, 40f, 5f, CORAL)
                path.rewind(); path.moveTo(-31f, 28f); path.lineTo(-7f, -43f); path.lineTo(7f, -43f); path.lineTo(31f, 28f); path.close()
                fill(0xFFFFAF82.toInt()); c.drawPath(path, p)
                line(c, -14f, -13f, 14f, -13f, Color.WHITE, 10f)
                line(c, -23f, 12f, 23f, 12f, Color.WHITE, 10f)
            }
            Shape.KIOSK -> {
                round(c, -35f, -20f, 35f, 38f, 4f, 0xFFB3BDDE.toInt())
                round(c, -42f, -35f, 42f, -14f, 5f, CORAL)
                for (i in 0..4) round(c, -34f + i * 16f, -35f, -26f + i * 16f, -14f, 2f, 0xFFFFE8B2.toInt())
                round(c, -25f, -5f, 25f, 17f, 4f, 0xFFE7EEE6.toInt())
                round(c, -29f, 19f, 29f, 27f, 3f, GOLD)
            }
            Shape.FOUNTAIN -> {
                oval(c, -47f, -1f, 47f, 40f, 0xFF9EAAC5.toInt())
                oval(c, -40f, -1f, 40f, 28f, 0xFFB7E8E3.toInt())
                line(c, 0f, -29f, 0f, 20f, 0xFFBEC7D5.toInt(), 10f)
                oval(c, -24f, -29f, 24f, -9f, 0xFFE2E1DC.toInt())
                for (i in -1..1) line(c, 0f, -37f, i * 19f, -17f, 0xFF9BE4E4.toInt(), 4f)
            }
            Shape.APARTMENT, Shape.TOWER, Shape.CITYHALL, Shape.CLOCKTOWER -> {
                val clock = kind == Shape.CLOCKTOWER
                val civic = kind == Shape.CITYHALL
                val color = if (clock || civic) 0xFFF0C591.toInt() else 0xFFB8B2D9.toInt()
                round(c, -33f, -48f, 33f, 40f, 5f, color)
                round(c, 23f, -43f, 39f, 40f, 4f, if (clock) 0xFFBE9279.toInt() else 0xFF8F92BD.toInt())
                round(c, -38f, -51f, 37f, -42f, 4f, if (clock || civic) CORAL else 0xFFE4D9EB.toInt())
                if (clock) {
                    oval(c, -22f, -35f, 22f, 9f, 0xFFFFF4D8.toInt())
                    line(c, 0f, -13f, 0f, -29f, INK, 3f); line(c, 0f, -13f, 12f, -7f, INK, 3f)
                    round(c, -9f, 15f, 9f, 40f, 6f, 0xFF9B8A9C.toInt())
                } else for (i in 0..2) for (j in 0..3) round(c, -24f + i * 17f, -34f + j * 18f,
                    -14f + i * 17f, -23f + j * 18f, 2f, if (j % 2 == 0) 0xFFDAEDF0.toInt() else GOLD)
                if (civic) star(c, 0f, -43f, 13f, GOLD)
            }
            Shape.STADIUM -> {
                oval(c, -49f, -34f, 49f, 38f, 0xFFA5ADC9.toInt())
                oval(c, -42f, -32f, 42f, 24f, 0xFFE6D8DC.toInt())
                round(c, -30f, -22f, 30f, 15f, 10f, 0xFF8CC79B.toInt())
                fill(Color.WHITE); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
                c.drawRect(-22f, -17f, 22f, 9f, p); c.drawCircle(0f, -4f, 7f, p); p.style = Paint.Style.FILL
            }
            Shape.BALL -> {
                oval(c, -38f, -36f, 38f, 40f, 0xFFD9684A.toInt())
                oval(c, -38f, -40f, 38f, 33f, 0xFFF48E6B.toInt())
                oval(c, -24f, -30f, -4f, -14f, 0x99FFE6D6.toInt())
                fill(0xFFFFD7B8.toInt()); p.style = Paint.Style.STROKE; p.strokeWidth = 4f
                c.drawArc(-38f, -40f, 38f, 33f, 20f, 60f, false, p); p.style = Paint.Style.FILL
            }
            Shape.BOX -> {
                round(c, -40f, -30f, 40f, 42f, 8f, 0xFF7B8FC7.toInt())
                round(c, -40f, -40f, 40f, 30f, 8f, 0xFFA3B6E6.toInt())
                round(c, -30f, -31f, 30f, -8f, 5f, 0xFFD7E2FA.toInt())
                line(c, -40f, 0f, 40f, 0f, 0xFF7B8FC7.toInt(), 5f)
                line(c, 0f, -40f, 0f, 30f, 0xFF7B8FC7.toInt(), 5f)
            }
            Shape.DISC -> {
                oval(c, -42f, -30f, 42f, 40f, 0xFF8E8E9D.toInt())
                oval(c, -42f, -38f, 42f, 32f, 0xFFD7D9E8.toInt())
                oval(c, -30f, -28f, 30f, 22f, 0xFFF2F3FB.toInt())
                oval(c, -8f, -10f, 8f, 4f, 0xFF8E8E9D.toInt())
            }
            Shape.CAN -> {
                round(c, -27f, -40f, 27f, 42f, 10f, 0xFF4EA8B5.toInt())
                round(c, -27f, -40f, 10f, 42f, 10f, 0xFF78CBD5.toInt())
                oval(c, -27f, -47f, 27f, -32f, 0xFFB7EEF2.toInt())
                round(c, -20f, -10f, 20f, 14f, 4f, 0xFFFFF4D9.toInt())
            }
            Shape.VACUUM -> {
                oval(c, -42f, -34f, 42f, 42f, 0xFF3C4455.toInt())
                oval(c, -42f, -42f, 42f, 36f, 0xFF5B6478.toInt())
                oval(c, -30f, -32f, 30f, 24f, 0xFF7D879C.toInt())
                oval(c, -10f, -12f, 10f, 6f, 0xFF53E0A6.toInt())
                oval(c, -26f, -10f, -18f, -2f, CORAL)
                line(c, -36f, 30f, 36f, 30f, 0xFF2D3340.toInt(), 6f)
            }
            Shape.MOWER -> {
                round(c, -40f, -10f, 34f, 30f, 8f, 0xFF4FA36A.toInt())
                round(c, -34f, -18f, 20f, 6f, 6f, 0xFF7ED08F.toInt())
                line(c, 20f, -10f, 46f, -46f, INK, 6f); line(c, 36f, -48f, 50f, -40f, INK, 6f)
                for (i in -1..1 step 2) oval(c, i * 24f - 12f, 18f, i * 24f + 12f, 42f, INK)
                for (i in -1..1 step 2) oval(c, i * 24f - 5f, 25f, i * 24f + 5f, 35f, 0xFFECE8D7.toInt())
            }
            Shape.TRAIN -> {
                round(c, -48f, -22f, 48f, 24f, 8f, 0xFFE05C5C.toInt())
                round(c, -40f, -30f, 20f, 0f, 6f, 0xFFF7B0A4.toInt())
                for (i in 0..3) round(c, -36f + i * 14f, -26f, -26f + i * 14f, -10f, 2f, 0xFFD9F0EA.toInt())
                round(c, 24f, -36f, 40f, -20f, 3f, 0xFF3C4455.toInt())
                for (i in -1..1) oval(c, i * 28f - 9f, 18f, i * 28f + 9f, 36f, INK)
                round(c, -50f, 10f, 50f, 16f, 2f, 0xFF3C4455.toInt())
            }
            Shape.GNOME -> {
                round(c, -18f, 4f, 18f, 40f, 8f, 0xFF4F7DBA.toInt())
                oval(c, -16f, -22f, 16f, 10f, 0xFFF6CDB2.toInt())
                oval(c, -14f, -8f, 14f, 14f, Color.WHITE)
                path.rewind(); path.moveTo(-20f, -16f); path.lineTo(0f, -52f); path.lineTo(20f, -16f); path.close()
                fill(CORAL); c.drawPath(path, p)
                oval(c, -8f, -16f, -3f, -11f, INK); oval(c, 3f, -16f, 8f, -11f, INK)
                oval(c, -3f, -10f, 3f, -5f, 0xFFE89A8A.toInt())
                round(c, -26f, 20f, 26f, 30f, 3f, 0xFF3F5E8C.toInt())
            }
            Shape.STATUE -> {
                round(c, -34f, 24f, 34f, 42f, 4f, 0xFF8E97A6.toInt())
                round(c, -26f, 14f, 26f, 28f, 3f, 0xFFB6BDC9.toInt())
                round(c, -12f, -22f, 12f, 16f, 6f, 0xFFC9CFD9.toInt())
                oval(c, -10f, -44f, 10f, -22f, 0xFFC9CFD9.toInt())
                line(c, -12f, -14f, -34f, -34f, 0xFFC9CFD9.toInt(), 7f)
                line(c, 12f, -14f, 30f, 2f, 0xFFC9CFD9.toInt(), 7f)
                star(c, -36f, -40f, 8f, GOLD)
            }
            Shape.TEDDY -> {
                oval(c, -34f, -8f, 34f, 42f, 0xFFC98C5B.toInt())
                oval(c, -22f, 2f, 22f, 36f, 0xFFE8B98E.toInt())
                oval(c, -26f, -44f, 26f, 6f, 0xFFC98C5B.toInt())
                oval(c, -32f, -50f, -12f, -30f, 0xFFC98C5B.toInt()); oval(c, 12f, -50f, 32f, -30f, 0xFFC98C5B.toInt())
                oval(c, -28f, -46f, -16f, -34f, 0xFFE8B98E.toInt()); oval(c, 16f, -46f, 28f, -34f, 0xFFE8B98E.toInt())
                oval(c, -10f, -22f, 10f, -8f, 0xFFE8B98E.toInt())
                oval(c, -4f, -20f, 4f, -14f, INK)
                oval(c, -14f, -30f, -8f, -24f, INK); oval(c, 8f, -30f, 14f, -24f, INK)
                round(c, -28f, -2f, 28f, 6f, 4f, CORAL)
            }
            Shape.BALLOON -> {
                oval(c, -40f, -50f, 40f, 20f, 0xFFF18488.toInt())
                for (i in 0..2) { fill(if (i % 2 == 0) GOLD else 0xFF62C8BA.toInt()); c.drawArc(-40f, -50f, 40f, 20f, 240f + i * 20f, 20f, true, p) }
                oval(c, -24f, -42f, -4f, -22f, 0x66FFFFFF)
                line(c, -22f, 14f, -12f, 36f, INK, 2f); line(c, 22f, 14f, 12f, 36f, INK, 2f)
                round(c, -16f, 34f, 16f, 46f, 3f, 0xFFA87B4F.toInt())
            }
            Shape.HOURGLASS -> {
                oval(c, -34f, -14f, 34f, 46f, 0x33FFD46F)
                round(c, -30f, -48f, 30f, -38f, 4f, 0xFFB47A3A.toInt()); round(c, -30f, 30f, 30f, 40f, 4f, 0xFFB47A3A.toInt())
                path.rewind(); path.moveTo(-24f, -38f); path.lineTo(24f, -38f); path.lineTo(4f, -4f); path.lineTo(24f, 30f); path.lineTo(-24f, 30f); path.lineTo(-4f, -4f); path.close()
                fill(0xFFCFEFF5.toInt()); c.drawPath(path, p)
                path.rewind(); path.moveTo(-16f, -30f); path.lineTo(16f, -30f); path.lineTo(2f, -8f); path.lineTo(-2f, -8f); path.close()
                fill(GOLD); c.drawPath(path, p)
                path.rewind(); path.moveTo(-20f, 30f); path.lineTo(20f, 30f); path.lineTo(0f, 10f); path.close(); c.drawPath(path, p)
                line(c, 0f, -8f, 0f, 14f, GOLD, 3f)
                star(c, 30f, -40f, 7f, GOLD); star(c, -30f, 30f, 5f, GOLD)
            }
        }
    }

    companion object {
        const val INK = 0xFF344E60.toInt()
        const val CORAL = 0xFFF18488.toInt()
        const val GOLD = 0xFFFFD46F.toInt()
        const val MINT = 0xFF55C7AF.toInt()
    }
}
