package com.Atom2Universe.app.games.mochi

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** Illustrated WebP sprites with live lighting, foliage, ribbons and particles. */
internal class MochiArt(context: Context) {
    private val sprites = MochiSprites.get(context)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val goldGlow = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xB0FFF0B4.toInt(), 0x38FFEBA3, 0x00FFF0B4), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
    private val blueGlow = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xA0D4FFFF.toInt(), 0x287BEDE7, 0x007BEDE7), null, Shader.TileMode.CLAMP)
    private val vignette = RadialGradient(210f, 310f, 510f,
        intArrayOf(0x00233B43, 0x00233B43, 0xA0223445.toInt()), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
    private val fog = LinearGradient(0f, 590f, 0f, 730f, 0x00A7E2D3, 0x6594D8CF, Shader.TileMode.CLAMP)
    private val ribbon = LinearGradient(0f, 0f, 0f, 700f,
        0xCCFFEFAB.toInt(), 0x88F9A4D5.toInt(), Shader.TileMode.CLAMP)

    private fun sprite(c: Canvas, image: Bitmap, x: Float, y: Float, w: Float, h: Float = w, alpha: Int = 255) {
        rect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        p.alpha = alpha
        c.drawBitmap(image, null, rect, p)
        p.alpha = 255
    }

    fun background(c: Canvas, bottom: Float, biome: Int = 0, time: Float = 0f,
        previousBiome: Int = biome, blend: Float = 1f) {
        val drift = sin(bottom * .0007f) * 30f
        val sway = sin(time * .12f) * 3f
        sprite(c, sprites.gardens[if (blend >= 1f) biome % 3 else previousBiome % 3], 210f + sway, 350f + drift, 527f, 790f)
        if (biome != previousBiome && blend < 1f) sprite(c, sprites.gardens[biome % 3], 210f + sway,
            350f + drift, 527f, 790f, (blend.coerceIn(0f, 1f) * 255).toInt())
        p.color = if (biome == 2) 0x0CD5F8FF else 0x12FFF3BE
        for (i in 0..2) {
            val dx = sin(time * .17f + i) * 20f
            path.reset(); path.moveTo(210f + i * 65 + dx, -30f)
            path.lineTo(228f + i * 65 + dx, -30f)
            path.lineTo(80f + i * 85, 730f); path.lineTo(10f + i * 85, 730f); path.close()
            c.drawPath(path, p)
        }
        for (i in 0..23) {
            val xx = (i * 79.7f + sin(time * .45f + i) * 14f + 420f) % 420f
            val yy = (i * 137.3f + bottom * .12f + time * (4f + i % 3)) % 780f - 40f
            val twinkle = .45f + .55f * abs(sin(time * 1.4f + i * 2.1f))
            if (i % 4 == 0) halo(c, xx, yy, 13f, twinkle * .55f, biome == 2)
            p.color = if (biome == 2) 0xFFD6FFFF.toInt() else 0xFFFFF4BB.toInt()
            p.alpha = (twinkle * 185).toInt()
            c.drawCircle(xx, yy, .9f + i % 3 * .55f, p)
        }
        p.alpha = 255
        p.shader = vignette; c.drawRect(0f, 0f, 420f, 700f, p); p.shader = null
    }

    fun foreground(c: Canvas, bottom: Float, time: Float) {
        for (i in -1..4) {
            val yy = i * 205f + bottom * .72f % 205f
            c.save(); c.translate(3f, yy); c.rotate(32f + sin(time * .9f + i) * 4f)
            sprite(c, sprites.leaves, -22f, 0f, 125f); c.restore()
            c.save(); c.translate(419f, yy + 95); c.scale(-1f, 1f)
            c.rotate(29f + sin(time + i) * 3f)
            sprite(c, sprites.leaves, -24f, 0f, 112f); c.restore()
        }
        p.shader = fog; c.drawRect(0f, 590f, 420f, 700f, p); p.shader = null
        for (i in 0..5) {
            val yy = (i * 139f + time * (9 + i) + bottom * .2f) % 760f - 30f
            val xx = 210f + sin(time * .23f + i * 2.9f) * 185f
            c.save(); c.translate(xx, yy); c.rotate(time * 18 + i * 70)
            p.color = if (i % 2 == 0) 0x99F7BED3.toInt() else 0x99FFDBAD.toInt()
            c.drawOval(-2.5f, -5f, 2.5f, 5f, p); c.restore()
        }
    }

    /** [life] de 1 à 0 : la fleur fane sous Mochi ; négatif quand rien ne presse. */
    fun flower(c: Canvas, x: Float, y: Float, visited: Boolean, active: Boolean, time: Float,
        kind: MochiGame.FlowerKind = MochiGame.FlowerKind.NORMAL, life: Float = -1f, bonus: Boolean = false) {
        val edge = if (x < 210f) -15f else 435f
        path.reset(); path.moveTo(edge, y + 105)
        path.cubicTo(edge + (x - edge) * .6f, y + 82, x - 18, y + 62, x, y + 15)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = 0xB5296551.toInt(); p.strokeWidth = 7f; c.drawPath(path, p)
        p.color = 0xB0A0D39B.toInt(); p.strokeWidth = 2f; c.drawPath(path, p)
        p.style = Paint.Style.FILL
        c.save(); c.translate(x - 12, y + 53); c.rotate(-35f)
        sprite(c, sprites.leaves, 0f, 0f, 40f); c.restore()
        val sanctuary = kind == MochiGame.FlowerKind.SANCTUARY
        if (active || sanctuary) halo(c, x, y + 12, if (sanctuary) 70f else 55f, .7f)
        c.save(); c.translate(x, y + 12)
        c.rotate(sin(time * .9f + x) * 3f)
        val wilting = active && life in 0f..1f
        // La fleur se referme et pâlit : la pression se lit sans compteur.
        val droop = if (wilting) (1f - life) * .22f else 0f
        val pulse = 1f + sin(time * 2f + x) * if (sanctuary) .035f else .012f
        c.scale(pulse * (1f - droop), pulse * (1f - droop * 1.6f))
        if (wilting && life < .3f) c.rotate(sin(time * 14f) * (1f - life / .3f) * 4f)
        val size = if (sanctuary) 94f else 79f
        val alpha = if (visited && !active) 215 else if (wilting) (150 + 105 * life).toInt() else 255
        val image = if (bonus && kind == MochiGame.FlowerKind.NORMAL) sprites.bonusFlower else sprites.flowers[kind.ordinal]
        sprite(c, image, 0f, 0f, size, alpha = alpha)
        if (visited && !active) {
            p.color = 0xDD255C51.toInt(); c.drawCircle(21f, 24f, 8f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f; p.color = 0xFFDCF6B6.toInt()
            path.reset(); path.moveTo(17f, 24f); path.lineTo(20f, 27f); path.lineTo(25f, 21f)
            c.drawPath(path, p); p.style = Paint.Style.FILL
        }
        if (wilting) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f
            p.color = 0x883F354C.toInt(); c.drawCircle(0f, 0f, 43f, p)
            p.color = if (life < .3f) 0xFFFF8699.toInt() else 0xFFFFEDC5.toInt()
            c.drawArc(-43f, -43f, 43f, 43f, -90f, life * 360, false, p)
            p.style = Paint.Style.FILL
        }
        if (sanctuary || kind == MochiGame.FlowerKind.SPRING) {
            for (i in 0..2) {
                val a = time * .7f + i * 2.094f
                sparkle(c, cos(a) * 43, sin(a) * 29, 3f + sin(time * 3 + i), 0xFFFEF4B7.toInt())
            }
        }
        c.restore()
    }

    /** Ronces tressées : aucune arête ne ressemble à une plateforme accueillante. */
    fun wall(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, material: MochiWall.Material, id: Int) {
        val soft = material == MochiWall.Material.RUBBER
        p.style = Paint.Style.FILL
        p.color = if (soft) 0xFF254D43.toInt() else 0xFF433E48.toInt()
        c.drawRoundRect(left, top, right, bottom, 5f, 5f, p)
        c.save(); c.clipRect(left, top, right, bottom)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.strokeCap = Paint.Cap.ROUND
        p.color = if (soft) 0xFF76BD91.toInt() else 0xFF957784.toInt()
        if (bottom - top > right - left) {
            for (strand in 0..1) {
                path.reset()
                var yy = top
                while (yy <= bottom + 5f) {
                    val xx = (left + right) * .5f + sin(yy * .15f + id + strand * PI.toFloat()) * (right - left) * .28f
                    if (yy == top) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                    yy += 5f
                }
                c.drawPath(path, p)
            }
        } else {
            for (strand in 0..1) {
                path.reset()
                var xx = left
                while (xx <= right + 5f) {
                    val yy = (top + bottom) * .5f + sin(xx * .15f + id + strand * PI.toFloat()) * (bottom - top) * .28f
                    if (xx == left) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                    xx += 5f
                }
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL
        p.color = if (soft) 0xFFD1E5A4.toInt() else 0xFFE3B3B1.toInt()
        var xx = left + 5f
        while (xx < right) {
            path.reset(); path.moveTo(xx - 3, top + 8); path.lineTo(xx + 2, top); path.lineTo(xx + 4, top + 8)
            path.moveTo(xx - 3, bottom - 8); path.lineTo(xx + 2, bottom); path.lineTo(xx + 4, bottom - 8)
            c.drawPath(path, p); xx += 15f
        }
        var yy = top + 6f
        while (yy < bottom) {
            path.reset(); path.moveTo(left + 8, yy - 3); path.lineTo(left, yy + 2); path.lineTo(left + 8, yy + 4)
            path.moveTo(right - 8, yy - 3); path.lineTo(right, yy + 2); path.lineTo(right - 8, yy + 4)
            c.drawPath(path, p); yy += 15f
        }
        c.restore()
    }
    fun body(c: Canvas, x: Float, y: Float, stretch: Float, tilt: Float, time: Float,
        hurt: Boolean = false, shield: Boolean = false, gaze: Float = 0f, hero: Boolean = false, size: Float = 69f) {
        c.save(); c.translate(x, y)
        c.scale(size / 69f, size / 69f)
        if (shield) {
            halo(c, 0f, -6f, 53f, .65f, true)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.color = 0xA0BBFFF3.toInt()
            c.drawCircle(0f, -6f, 39f + sin(time * 3) * 1.2f, p)
            p.color = 0xECFFFFFF.toInt(); p.strokeWidth = 2.8f
            c.drawArc(-35f, -41f, 35f, 29f, 212f, 32f, false, p)
            p.style = Paint.Style.FILL
            sparkle(c, 28f, -31f, 4f, 0xFFF2FFFF.toInt())
        }
        c.rotate(tilt); c.scale(1f / stretch, stretch)
        val image = if (hero) sprites.hero else sprites.faces[when {
            hurt -> 3
            stretch > 1.11f -> 2
            time % 4.7f > 4.52f -> 1
            else -> 0
        }]
        sprite(c, image, gaze.coerceIn(-1f, 1f) * .7f, -7f, 69f)
        c.restore()
    }

    /** Cible de l'arc : la fleur qui attrapera Mochi si le doigt lâche maintenant. */
    fun target(c: Canvas, x: Float, y: Float, time: Float) {
        val breath = 1f + sin(time * 6f) * .06f
        halo(c, x, y + 12f, 62f * breath, .55f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        p.color = 0xFFFFF3C4.toInt()
        c.drawCircle(x, y + 12f, 46f * breath, p)
        p.style = Paint.Style.FILL
    }

    /** Croix rouge au bout de l'arc : ce tir touche une épine ou une abeille. */
    fun cross(c: Canvas, x: Float, y: Float, time: Float) {
        val r = 11f + sin(time * 8f) * 1.5f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = 0xCC5A2232.toInt(); p.strokeWidth = 7f
        c.drawLine(x - r, y - r, x + r, y + r, p); c.drawLine(x - r, y + r, x + r, y - r, p)
        p.color = 0xFFFF8699.toInt(); p.strokeWidth = 3.5f
        c.drawLine(x - r, y - r, x + r, y + r, p); c.drawLine(x - r, y + r, x + r, y - r, p)
        p.style = Paint.Style.FILL
    }

    /** Bulle de rosée qui retient Mochi ; elle tremble quand elle va éclater. */
    fun bubble(c: Canvas, x: Float, y: Float, time: Float, life: Float) {
        val shiver = if (life < .3f) sin(time * 30f) * (1f - life / .3f) * 2.5f else 0f
        val r = 40f + sin(time * 3f) * 1.5f + shiver
        c.save(); c.translate(x, y - 4f)
        c.scale(1f + sin(time * 4f) * .03f, 1f - sin(time * 4f) * .03f)
        halo(c, 0f, 0f, r * 1.3f, .5f, true)
        p.style = Paint.Style.FILL; p.color = 0x33DDFFFF
        c.drawCircle(0f, 0f, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.color = 0xC8EFFFFF.toInt()
        c.drawCircle(0f, 0f, r, p)
        p.strokeWidth = 3.2f; p.color = 0xF0FFFFFF.toInt()
        c.drawArc(-r * .8f, -r * .8f, r * .8f, r * .8f, 205f, 45f, false, p)
        p.style = Paint.Style.FILL
        sparkle(c, r * .55f, -r * .6f, 4f, 0xFFF6FFFF.toInt())
        c.restore()
    }

    /** Doigt fantôme qui montre le geste avant le premier tir. */
    fun finger(c: Canvas, x: Float, y: Float, fromX: Float, fromY: Float, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFE7B8.toInt(); p.alpha = a / 2
        c.drawLine(fromX, fromY, x, y, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFF4DD.toInt(); p.alpha = a * 2 / 3
        c.drawCircle(x, y, 17f, p)
        p.color = 0xFF2E5A55.toInt(); p.alpha = a
        c.drawCircle(x, y, 6f, p)
        p.alpha = 255
    }

    fun sweet(c: Canvas, x: Float, y: Float, time: Float, golden: Boolean = false) {
        c.save(); c.translate(x, y)
        if (golden) halo(c, 0f, 0f, 31f, .6f + sin(time * 3) * .15f)
        c.rotate(sin(time * 2 + x) * if (golden) 12f else 7f)
        sprite(c, if (golden) sprites.starfruit else sprites.peach, 0f, 0f, if (golden) 31f else 25f)
        if (golden) sparkle(c, 15f, -16f, 3f + sin(time * 4), 0xFFFFF8DB.toInt())
        c.restore()
    }

    fun hazard(c: Canvas, x: Float, y: Float, kind: MochiGame.Danger, time: Float) {
        c.save(); c.translate(x, y)
        if (kind == MochiGame.Danger.THORN) {
            c.rotate(sin(time * 1.2f + x) * 12f)
            sprite(c, sprites.thorn, 0f, 0f, 61f)
        } else {
            p.color = 0x70E6FCFF; p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f
            val flap = sin(time * 42f) * 4
            c.drawArc(-33f, -29f - flap, 1f, 0f, 185f, 95f, false, p)
            p.style = Paint.Style.FILL
            c.rotate(sin(time * 7) * 5)
            sprite(c, sprites.bee, 0f, -3f, 56f)
        }
        c.restore()
    }

    fun trail(c: Canvas, points: ArrayDeque<MochiGame.Point>, bottom: Float) {
        if (points.size < 2) return
        p.strokeCap = Paint.Cap.ROUND
        var last: MochiGame.Point? = null
        points.forEachIndexed { index, point ->
            last?.let {
                p.shader = ribbon; p.alpha = (index * 8).coerceAtMost(110)
                p.strokeWidth = 1.5f + index * .5f
                c.drawLine(it.x, 700f - it.y + bottom, point.x, 700f - point.y + bottom, p)
                p.shader = null
            }
            last = point
        }
        p.alpha = 255
    }

    fun burst(c: Canvas, x: Float, y: Float, progress: Float, blue: Boolean) {
        if (progress >= 1f) return
        halo(c, x, y, 25f + progress * 58f, (1 - progress) * .75f, blue)
        p.style = Paint.Style.STROKE; p.strokeWidth = (1 - progress) * 3f
        p.color = if (blue) 0xFFDBFFF8.toInt() else 0xFFFFF0B0.toInt()
        p.alpha = ((1 - progress) * 185).toInt()
        c.drawCircle(x, y, 10f + progress * 38f, p)
        p.style = Paint.Style.FILL; p.alpha = 255
    }

    fun sparkle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        path.reset()
        for (i in 0..7) {
            val a = i * PI.toFloat() / 4f
            val r = if (i % 2 == 0) radius else radius * .25f
            val xx = x + cos(a) * r; val yy = y + sin(a) * r
            if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        path.close(); p.color = color; c.drawPath(path, p)
    }

    private fun halo(c: Canvas, x: Float, y: Float, radius: Float, alpha: Float = 1f, blue: Boolean = false) {
        c.save(); c.translate(x, y); c.scale(radius, radius)
        p.shader = if (blue) blueGlow else goldGlow
        p.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        c.drawCircle(0f, 0f, 1f, p)
        p.shader = null; p.alpha = 255; c.restore()
    }
}
