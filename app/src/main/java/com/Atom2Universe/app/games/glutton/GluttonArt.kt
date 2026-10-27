package com.Atom2Universe.app.games.glutton

import android.graphics.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Scene
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
    private val floors = arrayOfNulls<Bitmap>(Scene.entries.size)
    fun floor(scene: Scene): Bitmap = floors[scene.ordinal] ?: Bitmap.createBitmap(700, (700f * scene.height / scene.width).roundToInt(), Bitmap.Config.RGB_565).also {
        val c = Canvas(it); c.scale(700f / scene.width, 700f / scene.width)
        when (scene) { Scene.ROOM -> room(c, scene); Scene.GARDEN -> garden(c, scene); Scene.CITY -> city(c, scene) }
        floors[scene.ordinal] = it
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
        if (atlas?.draw(c, prop.sheet, prop.cell, dst, p) != true) c.drawBitmap(sprite(prop.shape), null, dst, p)
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

    private fun lanes(c: Canvas, scene: Scene, color: Int, width: Float, dash: Int, edge: Int) {
        val w = scene.width; val h = scene.height
        for (lx in floatArrayOf(w * .33f, w * .67f)) {
            if (edge != 0) line(c, lx, 0f, lx, h, edge, width + 26f)
            line(c, lx, 0f, lx, h, color, width)
            if (dash != 0) { var yy = 0f; while (yy < h) { line(c, lx, yy, lx, yy + 40f, dash, 4f); yy += 90f } }
        }
        for (ly in floatArrayOf(h * .3f, h * .7f)) {
            if (edge != 0) line(c, 0f, ly, w, ly, edge, width + 26f)
            line(c, 0f, ly, w, ly, color, width)
            if (dash != 0) { var xx = 0f; while (xx < w) { line(c, xx, ly, xx + 40f, ly, dash, 4f); xx += 90f } }
        }
    }

    private fun room(c: Canvas, scene: Scene) {
        val w = scene.width; val h = scene.height
        c.drawColor(0xFFB7A2C5.toInt())
        round(c, 14f, 22f, w - 14f, h - 8f, 48f, 0xFF8C83A7.toInt())
        round(c, 24f, 12f, w - 24f, h - 28f, 42f, 0xFFEFD9C8.toInt())
        var row = 0
        while (row * 94f < h) {
            val yy = 30f + row * 94f
            line(c, 34f, yy, w - 34f, yy, 0xFFDCC2B4.toInt(), 2f)
            var col = 0
            while (col * 295f < w) {
                val xx = 30f + col * 295f + if (row % 2 == 0) 0f else 147f
                if (xx < w - 40f) line(c, xx, yy, xx, yy + 92f, 0xFFDCC2B4.toInt(), 2f)
                col++
            }
            row++
        }
        // Runner carpets double as the toy cars' tracks.
        lanes(c, scene, 0xFFD9CFE3.toInt(), 46f, 0, 0)
        round(c, w * .32f, h * .34f, w * .68f, h * .66f, 150f, 0x28A2709A)
        round(c, w * .32f - 4f, h * .34f - 10f, w * .68f + 4f, h * .66f - 10f, 150f, 0xFFE8ADC1.toInt())
        round(c, w * .33f, h * .35f, w * .67f, h * .65f - 10f, 137f, 0xFFF5C5CD.toInt())
        for (i in 0..7) {
            val a = i * PI.toFloat() / 4f
            oval(c, w * .5f + cos(a) * 110f - 43f, h * .5f - 8f + sin(a) * 110f - 43f,
                w * .5f + cos(a) * 110f + 43f, h * .5f - 8f + sin(a) * 110f + 43f, 0x55FFEEE1)
        }
        round(c, w * .05f, h * .06f, w * .28f, h * .24f, 80f, 0xFFACCFC6.toInt())
        round(c, w * .06f, h * .07f, w * .27f, h * .23f, 70f, 0xFFCEE4CF.toInt())
        round(c, w * .7f, h * .74f, w * .95f, h * .92f, 90f, 0xFFC3B2D9.toInt())
        for (i in 0..5) star(c, w * .74f + i % 3 * w * .08f, h * .78f + i / 3 * h * .07f, 19f, 0x55F9F3FF)
        round(c, w * .72f, h * .04f, w * .96f, h * .27f, 30f, 0xFFF3E7D5.toInt())
        for (i in 0..5) for (j in 0..5) round(c, w * .73f + i * w * .04f, h * .05f + j * h * .037f,
            w * .73f + i * w * .04f + w * .036f, h * .05f + j * h * .037f + h * .033f, 6f, 0xFFE4D3C1.toInt())
        var x = 90f
        while (x < w - 90f) {
            round(c, x, 12f, x + 75f, 27f, 7f, 0xFFF9EADA.toInt()); round(c, x, h - 30f, x + 75f, h - 15f, 7f, 0xFFAA92B7.toInt()); x += 130f
        }
        var y = 90f
        while (y < h - 90f) {
            round(c, 12f, y, 27f, y + 75f, 7f, 0xFFF9EADA.toInt()); round(c, w - 30f, y, w - 15f, y + 75f, 7f, 0xFFAA92B7.toInt()); y += 130f
        }
    }

    private fun garden(c: Canvas, scene: Scene) {
        val w = scene.width; val h = scene.height
        c.drawColor(0xFFB9D7A0.toInt())
        var i = 0
        while (i * 104f < w) {
            var j = 0
            while (j * 103f < h) {
                oval(c, i * 104f + j % 2 * 30f, j * 103f, i * 104f + j % 2 * 30f + 45f, j * 103f + 25f, 0x1F6AAB85); j++
            }
            i++
        }
        round(c, w * .36f, h * .36f, w * .64f, h * .64f, 120f, 0xFFA5CF8E.toInt())
        lanes(c, scene, 0xFFE3D4AD.toInt(), 70f, 0, 0xFFCDBE93.toInt())
        round(c, w * .05f, h * .05f, w * .27f, h * .25f, 60f, 0xFF8BBFB4.toInt())
        round(c, w * .06f, h * .06f, w * .26f, h * .24f, 50f, 0xFFB5E5D1.toInt())
        for (k in 0..7) line(c, w * .08f + k * w * .022f, h * .12f, w * .09f + k * w * .022f, h * .12f, 0x88F4FFF0.toInt(), 4f)
        round(c, w * .72f, h * .74f, w * .95f, h * .93f, 90f, 0xFFD9C2AE.toInt())
        for (k in 0..8) star(c, w * .75f + k % 3 * w * .07f, h * .78f + k / 3 * h * .05f, 14f, 0x55FFF6DF)
        round(c, w * .72f, h * .05f, w * .96f, h * .26f, 30f, 0xFFC9D9A3.toInt())
        for (k in 0..4) line(c, w * .74f, h * .07f + k * h * .04f, w * .94f, h * .07f + k * h * .04f, 0xFFB0C78B.toInt(), 14f)
        var y = 0f
        while (y < h) {
            round(c, 8f, y, 30f, y + 58f, 6f, 0xFF819D78.toInt()); round(c, w - 30f, y, w - 8f, y + 58f, 6f, 0xFF819D78.toInt()); y += 85f
        }
    }

    private fun city(c: Canvas, scene: Scene) {
        val w = scene.width; val h = scene.height
        c.drawColor(0xFFD9DDD9.toInt())
        for (i in 0..2) for (j in 0..2) {
            val x0 = if (i == 0) 40f else if (i == 1) w * .33f + 95f else w * .67f + 95f
            val x1 = if (i == 0) w * .33f - 95f else if (i == 1) w * .67f - 95f else w - 40f
            val y0 = if (j == 0) 40f else if (j == 1) h * .3f + 95f else h * .7f + 95f
            val y1 = if (j == 0) h * .3f - 95f else if (j == 1) h * .7f - 95f else h - 40f
            val park = (i + j) % 2 == 1
            round(c, x0, y0, x1, y1, 26f, if (park) 0xFFB9D6B1.toInt() else 0xFFE4DCD2.toInt())
            round(c, x0 + 15f, y0 + 15f, x1 - 15f, y1 - 15f, 20f, if (park) 0xFFCDE0BE.toInt() else 0xFFEDE6DC.toInt())
        }
        lanes(c, scene, 0xFF98A4AE.toInt(), 110f, 0xFFE9E9D7.toInt(), 0xFFC8CCCB.toInt())
        for (lx in floatArrayOf(w * .33f, w * .67f)) for (ly in floatArrayOf(h * .3f, h * .7f)) for (k in 0..4) {
            line(c, lx - 80f, ly - 30f + k * 15f, lx - 56f, ly - 30f + k * 15f, 0xFFF5EEE1.toInt(), 6f)
        }
        round(c, w * .06f, h * .05f, w * .27f, h * .24f, 48f, 0xFFE6C6CB.toInt())
        for (k in 0..4) star(c, w * .1f + k % 2 * w * .1f, h * .09f + k / 2 * h * .055f, 18f, 0x55FFF6EA)
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
            Shape.MOUSE -> {
                oval(c, -34f, -10f, 30f, 36f, 0xFFB9B4C8.toInt())
                oval(c, -32f, -16f, 24f, 28f, 0xFFD9D4E6.toInt())
                oval(c, 10f, -30f, 38f, -4f, 0xFFD9D4E6.toInt())
                oval(c, 16f, -42f, 34f, -24f, 0xFFF4B6C3.toInt())
                oval(c, 20f, -38f, 30f, -28f, 0xFFF4B6C3.toInt())
                oval(c, 32f, -14f, 38f, -8f, INK); oval(c, 38f, -16f, 45f, -9f, CORAL)
                line(c, -34f, 12f, -50f, -8f, 0xFFB9B4C8.toInt(), 4f)
            }
            Shape.CAT -> {
                oval(c, -40f, -6f, 36f, 38f, 0xFFF3A860.toInt())
                oval(c, -8f, -42f, 38f, 4f, 0xFFF7B872.toInt())
                path.rewind(); path.moveTo(-6f, -24f); path.lineTo(-2f, -50f); path.lineTo(10f, -34f); path.close()
                fill(0xFFF3A860.toInt()); c.drawPath(path, p)
                path.rewind(); path.moveTo(22f, -36f); path.lineTo(34f, -52f); path.lineTo(36f, -28f); path.close(); c.drawPath(path, p)
                oval(c, 6f, -27f, 13f, -18f, 0xFF4B9A5C.toInt()); oval(c, 22f, -27f, 29f, -18f, 0xFF4B9A5C.toInt())
                oval(c, 15f, -14f, 21f, -10f, CORAL)
                for (i in -1..1) { line(c, 8f, -13f + i * 5f, -8f, -16f + i * 7f, 0xFF8A5B33.toInt(), 2f); line(c, 28f, -13f + i * 5f, 44f, -16f + i * 7f, 0xFF8A5B33.toInt(), 2f) }
                line(c, -38f, 10f, -50f, -22f, 0xFFF3A860.toInt(), 8f)
                for (i in 0..2) line(c, -24f + i * 14f, -2f, -22f + i * 14f, 14f, 0xFFD88A44.toInt(), 5f)
            }
            Shape.VACUUM -> {
                oval(c, -42f, -34f, 42f, 42f, 0xFF3C4455.toInt())
                oval(c, -42f, -42f, 42f, 36f, 0xFF5B6478.toInt())
                oval(c, -30f, -32f, 30f, 24f, 0xFF7D879C.toInt())
                oval(c, -10f, -12f, 10f, 6f, 0xFF53E0A6.toInt())
                oval(c, -26f, -10f, -18f, -2f, CORAL)
                line(c, -36f, 30f, 36f, 30f, 0xFF2D3340.toInt(), 6f)
            }
            Shape.DOG -> {
                oval(c, -42f, -8f, 30f, 38f, 0xFFB07A4C.toInt())
                oval(c, -40f, -14f, 10f, 26f, 0xFFD1976A.toInt())
                oval(c, 2f, -40f, 42f, 2f, 0xFFB07A4C.toInt())
                oval(c, 24f, -26f, 46f, -10f, 0xFF5E3C22.toInt())
                oval(c, -4f, -44f, 14f, -16f, 0xFF8C5A30.toInt())
                oval(c, 16f, -32f, 23f, -24f, INK); oval(c, 38f, -22f, 46f, -14f, INK)
                line(c, -42f, 6f, -50f, -18f, 0xFFB07A4C.toInt(), 7f)
                round(c, 8f, -4f, 36f, 3f, 3f, CORAL)
            }
            Shape.HEN -> {
                oval(c, -38f, -14f, 28f, 36f, 0xFFF3E6D4.toInt())
                oval(c, 0f, -44f, 34f, -10f, 0xFFF3E6D4.toInt())
                for (i in 0..2) oval(c, 8f + i * 6f, -56f + i % 2 * 4f, 16f + i * 6f, -42f, CORAL)
                round(c, 30f, -28f, 46f, -22f, 3f, GOLD)
                oval(c, 20f, -34f, 26f, -28f, INK)
                oval(c, 24f, -22f, 32f, -14f, CORAL)
                line(c, -8f, 30f, -8f, 44f, GOLD, 4f); line(c, 6f, 30f, 6f, 44f, GOLD, 4f)
                oval(c, -42f, -22f, -22f, 4f, 0xFFD8C9B8.toInt())
            }
            Shape.PIGEON -> {
                oval(c, -34f, -10f, 26f, 30f, 0xFF8F93A8.toInt())
                oval(c, -30f, -14f, 10f, 20f, 0xFFB1B5C7.toInt())
                oval(c, 8f, -34f, 34f, -8f, 0xFF8F93A8.toInt())
                oval(c, 10f, -18f, 30f, -4f, 0xFF6FBF9E.toInt())
                round(c, 30f, -24f, 42f, -19f, 2f, GOLD)
                oval(c, 22f, -28f, 27f, -23f, INK)
                line(c, -34f, 8f, -48f, 2f, 0xFF6C7085.toInt(), 6f)
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
            Shape.GOLD -> {
                oval(c, -42f, -36f, 42f, 42f, 0xFFC99A2E.toInt())
                oval(c, -42f, -42f, 42f, 36f, GOLD)
                oval(c, -28f, -32f, 28f, 22f, 0xFFFFE9A6.toInt())
                star(c, 0f, -4f, 24f, 0xFFF2B53A.toInt())
                star(c, 0f, -4f, 11f, 0xFFFFF6D6.toInt())
                oval(c, -30f, -34f, -14f, -22f, 0xAAFFFFFF.toInt())
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
