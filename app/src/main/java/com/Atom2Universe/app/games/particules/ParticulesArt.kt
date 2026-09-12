package com.Atom2Universe.app.games.particules

import android.graphics.*
import kotlin.math.*

/** Vector artwork in local coordinates; paints, paths and shaders reused by the render thread. */
internal class ParticulesArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val glyph = Path()
    private val face = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(0xFF49667C.toInt(), 0xFF172E43.toInt(), 0xFF0B1728.toInt()),
        floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
    private val shine = LinearGradient(0f, 0f, 0f, 1f,
        0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)

    private fun color(c: Int) { fill.shader = null; fill.color = c }
    private fun stroke(c: Int, w: Float) { line.color = c; line.strokeWidth = w }

    fun brick(c: Canvas, r: RectF, shape: ParticulesView.Shape,
              type: ParticulesView.BType, tint: Int, hits: Int, maxHits: Int, flash: Float) {
        val save = c.save()
        c.translate(r.left, r.top)
        c.scale(r.width(), r.height())
        path.rewind()
        val cut = when (shape) {
            ParticulesView.Shape.DIAMOND -> .30f
            ParticulesView.Shape.HEX -> .18f
            else -> .07f
        }
        if (shape == ParticulesView.Shape.PILL) {
            path.addRoundRect(.025f, .035f, .975f, .965f, .24f, .48f, Path.Direction.CW)
        } else {
            path.moveTo(cut, .035f); path.lineTo(1f - cut, .035f)
            path.lineTo(.975f, .28f); path.lineTo(.975f, .72f)
            path.lineTo(1f - cut, .965f); path.lineTo(cut, .965f)
            path.lineTo(.025f, .72f); path.lineTo(.025f, .28f); path.close()
        }
        fill.shader = face; fill.alpha = 255; c.drawPath(path, fill); fill.shader = null
        stroke(tint, .025f); c.drawPath(path, line)
        val clip = c.save(); c.clipPath(path)
        color((tint and 0xFFFFFF) or 0x38000000); c.drawRect(0f, 0f, 1f, 1f, fill)
        fill.shader = shine; c.drawRect(0f, 0f, 1f, .5f, fill); fill.shader = null
        stroke(0x50FFFFFF, .018f); c.drawLine(.12f, .13f, .88f, .13f, line)
        stroke(0x99050C19.toInt(), .055f); c.drawLine(.1f, .87f, .9f, .87f, line)
        if (type == ParticulesView.BType.INDESTRUCTIBLE) {
            stroke(0x557E94A9, .055f)
            for (i in -2..7) c.drawLine(i * .2f, 1f, i * .2f + .4f, 0f, line)
        }
        c.restoreToCount(clip)
        // Side contacts and a dark medallion keep the glyph readable on every colour.
        color(tint)
        c.drawRoundRect(.12f, .37f, .16f, .63f, .02f, .02f, fill)
        c.drawRoundRect(.84f, .37f, .88f, .63f, .02f, .02f, fill)
        c.restoreToCount(save)
        val radius = min(r.height() * .29f, r.width() * .20f)
        if (type != ParticulesView.BType.SIMPLE) {
            color(0xDD081524.toInt()); c.drawCircle(r.centerX(), r.centerY(), radius * 1.18f, fill)
            icon(c, r.centerX(), r.centerY(), radius, type.ordinal, 0xFFF1FCFF.toInt())
        } else {
            stroke(tint, max(1f, r.height() * .045f))
            c.drawLine(r.centerX() - r.width() * .18f, r.centerY(),
                r.centerX() + r.width() * .18f, r.centerY(), line)
        }
        if (maxHits > 1 && type != ParticulesView.BType.INDESTRUCTIBLE) {
            val count = min(maxHits, 8)
            val step = r.width() * .46f / count
            for (i in 0 until count) {
                color(if (i < ceil(hits.toFloat() / maxHits * count).toInt()) tint else 0xFF08111E.toInt())
                c.drawRect(r.centerX() - r.width() * .23f + i * step,
                    r.bottom - r.height() * .18f,
                    r.centerX() - r.width() * .23f + (i + .7f) * step,
                    r.bottom - r.height() * .10f, fill)
            }
        }
        if (flash > 0f) {
            val f = c.save(); c.translate(r.left, r.top); c.scale(r.width(), r.height())
            color(((flash.coerceIn(0f, 1f) * 160).toInt() shl 24) or 0xFFFFFF)
            c.drawPath(path, fill); c.restoreToCount(f)
        }
    }

    // Brick glyphs: armour, gift, explosive core, lock, repair, snow crystal.
    private fun icon(c: Canvas, x: Float, y: Float, r: Float, kind: Int, tint: Int) {
        val save = c.save(); c.translate(x, y); c.scale(r, r)
        stroke(tint, .15f); color(tint)
        when (kind) {
            1, 4 -> {
                glyph.rewind(); glyph.moveTo(-.65f, -.65f); glyph.lineTo(.65f, -.65f)
                glyph.lineTo(.55f, .25f); glyph.lineTo(0f, .8f); glyph.lineTo(-.55f, .25f); glyph.close()
                c.drawPath(glyph, line)
                if (kind == 4) { c.drawRect(-.2f, -.15f, .2f, .3f, fill) }
                else c.drawLine(-.25f, -.15f, .25f, -.15f, line)
            }
            2 -> {
                c.drawRect(-.65f, -.25f, .65f, .65f, line)
                c.drawLine(-.75f, -.25f, .75f, -.25f, line)
                c.drawLine(0f, -.55f, 0f, .65f, line)
                c.drawOval(-.55f, -.8f, 0f, -.25f, line)
                c.drawOval(0f, -.8f, .55f, -.25f, line)
            }
            3 -> {
                c.drawCircle(0f, .15f, .55f, line)
                c.drawLine(.2f, -.4f, .45f, -.75f, line)
                c.drawLine(.65f, -.8f, .9f, -.8f, line)
                c.drawLine(0f, -.08f, 0f, .25f, line)
                c.drawCircle(0f, .43f, .07f, fill)
            }
            5 -> {
                c.drawArc(-.8f, -.8f, .8f, .8f, 30f, 280f, false, line)
                c.drawLine(.5f, -.65f, .55f, -.2f, line)
                c.drawLine(-.35f, 0f, .35f, 0f, line)
                c.drawLine(0f, -.35f, 0f, .35f, line)
            }
            6 -> repeat(6) { i ->
                val a = i * PI.toFloat() / 3f
                val s = c.save(); c.rotate(a * 180f / PI.toFloat())
                c.drawLine(0f, 0f, 0f, -.85f, line)
                c.drawLine(-.23f, -.45f, 0f, -.65f, line)
                c.drawLine(.23f, -.45f, 0f, -.65f, line); c.restoreToCount(s)
            }
        }
        c.restoreToCount(save)
    }

    fun arena(c: Canvas, w: Float, h: Float, top: Float) {
        stroke(0x122DABBE, max(1f, w * .001f))
        val step = w / 12f
        for (i in 1..11) c.drawLine(i * step, top, i * step, h, line)
        var y = top
        while (y < h) { c.drawLine(0f, y, w, y, line); y += step }
        stroke(0x503CCCD8, max(1f, w * .002f))
        c.drawLine(w * .01f, top, w * .01f, h * .9f, line)
        c.drawLine(w * .99f, top, w * .99f, h * .9f, line)
        color(0xEE081321.toInt()); c.drawRect(0f, 0f, w, top * .85f, fill)
        stroke(0x8850DDE0.toInt(), max(1f, w * .002f))
        c.drawLine(w * .035f, top * .85f, w * .965f, top * .85f, line)
    }

    fun paddle(c: Canvas, r: RectF, tint: Int) {
        val h = r.height()
        color(0x5535DBE4); c.drawRoundRect(r.left - 3f, r.top - 3f, r.right + 3f, r.bottom + 3f, h, h, fill)
        color(0xFF122B40.toInt()); c.drawRoundRect(r, h * .4f, h * .4f, fill)
        stroke(tint, max(1f, h * .08f)); c.drawRoundRect(r, h * .4f, h * .4f, line)
        color(tint)
        c.drawRoundRect(r.left + h * .2f, r.top + h * .16f, r.left + h, r.bottom - h * .16f, h * .2f, h * .2f, fill)
        c.drawRoundRect(r.right - h, r.top + h * .16f, r.right - h * .2f, r.bottom - h * .16f, h * .2f, h * .2f, fill)
        stroke(0xFFE7FFFF.toInt(), max(1f, h * .10f))
        c.drawLine(r.left + h * 1.3f, r.top + h * .24f, r.right - h * 1.3f, r.top + h * .24f, line)
        for (i in -2..2) {
            stroke(tint, max(1f, h * .07f))
            c.drawLine(r.centerX() + i * h * .45f, r.top + h * .5f,
                r.centerX() + i * h * .45f, r.bottom - h * .2f, line)
        }
    }

    fun emblem(c: Canvas, x: Float, y: Float, radius: Float) {
        color(0x183BD9E7); c.drawCircle(x, y, radius * 1.3f, fill)
        stroke(0x665CDBE6, radius * .025f); c.drawCircle(x, y, radius, line)
        val save = c.save(); c.translate(x, y)
        stroke(0xFF65E7EC.toInt(), radius * .045f)
        repeat(3) {
            c.drawOval(-radius * .8f, -radius * .3f, radius * .8f, radius * .3f, line)
            c.rotate(60f)
        }
        color(0xFFF1FFFF.toInt()); c.drawCircle(0f, 0f, radius * .16f, fill)
        color(0xFFFFD166.toInt()); c.drawCircle(radius * .8f, 0f, radius * .10f, fill)
        c.restoreToCount(save)
    }

    fun capsule(c: Canvas, x: Float, y: Float, radius: Float, type: ParticulesView.PType, tint: Int) {
        val save = c.save(); c.translate(x, y); c.scale(radius, radius)
        color((tint and 0xFFFFFF) or 0x30000000)
        c.drawRoundRect(-1.18f, -1.18f, 1.18f, 1.18f, .4f, .4f, fill)
        color(0xFF10273B.toInt()); c.drawRoundRect(-1f, -1f, 1f, 1f, .3f, .3f, fill)
        stroke(tint, .09f); c.drawRoundRect(-1f, -1f, 1f, 1f, .3f, .3f, line)
        stroke(0xFFEDFFFF.toInt(), .12f); color(0xFFEDFFFF.toInt())
        when (type) {
            ParticulesView.PType.MULTIBALL -> {
                c.drawCircle(0f, -.4f, .20f, fill)
                c.drawCircle(-.4f, .3f, .20f, fill); c.drawCircle(.4f, .3f, .20f, fill)
            }
            ParticulesView.PType.EXTEND -> {
                c.drawLine(-.6f, 0f, .6f, 0f, line)
                c.drawLine(-.6f, 0f, -.3f, -.3f, line); c.drawLine(-.6f, 0f, -.3f, .3f, line)
                c.drawLine(.6f, 0f, .3f, -.3f, line); c.drawLine(.6f, 0f, .3f, .3f, line)
            }
            ParticulesView.PType.LASER -> {
                c.drawLine(-.3f, .6f, -.3f, -.6f, line); c.drawLine(.3f, .6f, .3f, -.6f, line)
                c.drawLine(-.55f, -.3f, -.3f, -.6f, line); c.drawLine(.55f, -.3f, .3f, -.6f, line)
            }
            ParticulesView.PType.FLOOR -> {
                c.drawLine(-.65f, .5f, .65f, .5f, line); c.drawCircle(0f, -.25f, .22f, fill)
            }
            ParticulesView.PType.MAGNET -> {
                c.drawArc(-.5f, -.4f, .5f, .6f, 0f, 180f, false, line)
                c.drawLine(-.5f, .1f, -.5f, -.55f, line); c.drawLine(.5f, .1f, .5f, -.55f, line)
            }
            ParticulesView.PType.SHIELD -> icon(c, 0f, 0f, .8f, 1, 0xFFEDFFFF.toInt())
            ParticulesView.PType.SLOW -> {
                c.drawCircle(0f, 0f, .6f, line)
                c.drawLine(0f, -.4f, 0f, 0f, line); c.drawLine(0f, 0f, .3f, .15f, line)
            }
            ParticulesView.PType.FIRE -> {
                glyph.rewind(); glyph.moveTo(0f, -.7f); glyph.lineTo(.5f, .1f)
                glyph.cubicTo(.65f, .8f, -.65f, .8f, -.5f, .1f)
                glyph.lineTo(-.2f, -.25f); glyph.lineTo(-.12f, .15f); glyph.close()
                c.drawPath(glyph, line)
            }
            ParticulesView.PType.PIERCE -> {
                c.drawLine(-.6f, -.1f, .6f, -.1f, line)
                c.drawLine(0f, .65f, 0f, -.65f, line)
                c.drawLine(-.25f, -.35f, 0f, -.65f, line); c.drawLine(.25f, -.35f, 0f, -.65f, line)
            }
            ParticulesView.PType.SPEED -> repeat(2) { i ->
                val dx = i * .55f - .45f
                c.drawLine(dx, -.5f, dx + .3f, 0f, line); c.drawLine(dx + .3f, 0f, dx, .5f, line)
            }
        }
        c.restoreToCount(save)
    }
}
