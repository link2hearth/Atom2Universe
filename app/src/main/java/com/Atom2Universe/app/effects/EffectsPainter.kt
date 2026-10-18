package com.Atom2Universe.app.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.AppEffectPack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Procedural native artwork. All particles/paths are bounded; no bitmap or per-frame allocation. */
internal class EffectsPainter(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private val crescent = Path()
    private val moonCut = Path()
    private val oval = RectF()
    private val random = Random(20481225)
    // x, y, radius, phase, speed; stable positions across redraws and rotations.
    private val particles = Array(48) {
        floatArrayOf(random.nextFloat(), random.nextFloat(), .6f + random.nextFloat() * 1.9f,
            random.nextFloat() * 6.28f, .025f + random.nextFloat() * .03f)
    }
    private val bulbs = intArrayOf(0xFFF26E77.toInt(), 0xFFEDC56A.toInt(), 0xFF62BA93.toInt(),
        0xFF69ADDF.toInt(), 0xFFC293D8.toInt())
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedLight = false
    private var cachedBanner = false
    private var halo: Shader? = null
    private var frost: Shader? = null
    private var meteor: Shader? = null
    private var radius = 0f
    private var cx = 0f
    private var cy = 0f
    private var trail = 0f

    fun draw(canvas: Canvas, bounds: Rect, pack: AppEffectPack, palette: EffectsPalette,
             seconds: Float, banner: Boolean, foreground: Boolean, animated: Boolean) {
        if (bounds.isEmpty) return
        prepare(bounds.width(), bounds.height(), palette.light, banner)
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        clip.rewind()
        oval.set(0f, 0f, w, h)
        clip.addRoundRect(oval, palette.corner, palette.corner, Path.Direction.CW)
        canvas.clipPath(clip)
        val time = if (animated) seconds else 0f
        if (foreground) {
            if (pack == AppEffectPack.CHRISTMAS) garland(canvas, w, palette.light, time)
        } else when (pack) {
            AppEffectPack.SKY -> {
                if (palette.light) daylight(canvas, w, h, time, banner)
                else night(canvas, w, h, time, banner, animated)
            }
            AppEffectPack.CHRISTMAS -> winter(canvas, w, h, palette.light, time, banner)
        }
        canvas.restoreToCount(saved)
    }

    private fun prepare(width: Int, height: Int, light: Boolean, banner: Boolean) {
        if (width == cachedWidth && height == cachedHeight && light == cachedLight && banner == cachedBanner) return
        cachedWidth = width
        cachedHeight = height
        cachedLight = light
        cachedBanner = banner
        radius = if (banner) min(height * .35f, 24f * density) else min(width * .115f, 54f * density)
        // Large celestial bodies sit at the lower left; keep the small banner decorations in place.
        cx = if (banner) width * .79f else maxOf(width * .18f, radius * 1.7f)
        cy = if (banner) height * .52f else height - maxOf(height * .13f, radius * 1.7f)
        halo = RadialGradient(cx, cy, radius * 3f,
            if (light) intArrayOf(0x48FFD16B, 0x14F9C77B, Color.TRANSPARENT)
            else intArrayOf(0x266C9CDB, 0x0D7199CB, Color.TRANSPARENT),
            floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP)
        frost = LinearGradient(0f, 0f, 0f, min(height * .12f, 32f * density),
            if (light) 0x407AB7CA else 0x6081CBEA, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        trail = min(width * .24f, 110f * density)
        meteor = LinearGradient(-trail, 0f, 0f, 0f,
            intArrayOf(Color.TRANSPARENT, 0x809EBEEE.toInt(), 0xEEEDF6FF.toInt()),
            null, Shader.TileMode.CLAMP)
        crescent.rewind()
        crescent.addCircle(cx, cy, radius, Path.Direction.CW)
        moonCut.rewind()
        moonCut.addCircle(cx + radius * .5f, cy - radius * .23f, radius * .93f, Path.Direction.CW)
        crescent.op(moonCut, Path.Op.DIFFERENCE)
    }

    private fun fill(color: Int, alpha: Int = 255) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
    }

    private fun stroke(color: Int, alpha: Int, width: Float) {
        fill(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * density
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun glow(canvas: Canvas, banner: Boolean) {
        fill(Color.WHITE, if (banner) 150 else 255)
        paint.shader = halo
        canvas.drawCircle(cx, cy, radius * 3f, paint)
        paint.shader = null
    }

    private fun night(canvas: Canvas, w: Float, h: Float, time: Float, banner: Boolean, animated: Boolean) {
        glow(canvas, banner)
        fill(0xFFDBE8F7.toInt(), if (banner) 72 else 196)
        canvas.drawPath(crescent, paint)
        // The crescent is genuinely transparent; it never paints a dark disk over the chosen gradient.
        val save = canvas.save()
        canvas.clipPath(crescent)
        fill(0xFF7996BC.toInt(), if (banner) 20 else 50)
        canvas.drawCircle(cx - radius * .41f, cy + radius * .21f, radius * .17f, paint)
        canvas.drawCircle(cx - radius * .26f, cy - radius * .39f, radius * .12f, paint)
        canvas.drawCircle(cx + radius * .02f, cy + radius * .7f, radius * .09f, paint)
        canvas.restoreToCount(save)
        val count = if (banner) 14 else particles.size
        for (i in 0 until count) {
            val p = particles[i]
            val x = p[0] * w
            val y = p[1] * h
            val pulse = .72f + .28f * sin(time * .6f + p[3])
            val alpha = ((if (banner) 75 else 190) * pulse).toInt()
            val r = p[2] * density * if (banner) .5f else .64f
            fill(0xFFE3EDFF.toInt(), alpha)
            canvas.drawCircle(x, y, r, paint)
            if (i % 9 == 0) {
                stroke(0xFFCEDFFF.toInt(), alpha / 2, .7f)
                canvas.drawLine(x - r * 2.7f, y, x + r * 2.7f, y, paint)
                canvas.drawLine(x, y - r * 2.7f, x, y + r * 2.7f, paint)
            }
        }
        if (!banner && animated) {
            val phase = time % 11f
            if (phase in 1.8f..3.2f) {
                val progress = (phase - 1.8f) / 1.4f
                val saveMeteor = canvas.save()
                canvas.translate(w * (.08f + .9f * progress), h * (.1f + .26f * progress))
                canvas.rotate(28f)
                stroke(Color.WHITE, (sin(progress * PI).toFloat() * 230).toInt(), 1.6f)
                paint.shader = meteor
                canvas.drawLine(-trail, 0f, 0f, 0f, paint)
                paint.shader = null
                fill(Color.WHITE, (sin(progress * PI).toFloat() * 220).toInt())
                canvas.drawCircle(0f, 0f, density * 1.8f, paint)
                canvas.restoreToCount(saveMeteor)
            }
        }
    }

    private fun daylight(canvas: Canvas, w: Float, h: Float, time: Float, banner: Boolean) {
        glow(canvas, banner)
        val strength = if (banner) .5f else 1f
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(time * .7f)
        stroke(0xFFE9B446.toInt(), (85 * strength).toInt(), 1.7f)
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            canvas.drawLine(cos(a) * radius * 1.25f, sin(a) * radius * 1.25f,
                cos(a) * radius * 1.57f, sin(a) * radius * 1.57f, paint)
        }
        canvas.restoreToCount(save)
        fill(0xFFF6CA66.toInt(), (175 * strength).toInt())
        canvas.drawCircle(cx, cy, radius, paint)
        fill(0xFFFFECA8.toInt(), (200 * strength).toInt())
        canvas.drawCircle(cx - radius * .06f, cy - radius * .07f, radius * .77f, paint)
        val drift = sin(time * .025f) * w * .05f
        cloud(canvas, w * .14f + drift, h * if (banner) .68f else .16f,
            min(w * .3f, 150f * density), strength)
        cloud(canvas, w * .7f - drift, h * if (banner) .95f else .31f,
            min(w * .24f, 115f * density), strength * .85f)
        if (!banner) cloud(canvas, w * .26f - drift * .6f, h * .73f,
            min(w * .33f, 170f * density), .55f)
    }

    private fun cloud(canvas: Canvas, x: Float, y: Float, width: Float, strength: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        val unit = width / 5f
        // Merge lobes into one cotton silhouette to avoid overlapping translucent circle seams.
        path.rewind()
        path.moveTo(-unit * 2.2f, unit * .42f)
        path.cubicTo(-unit * 3f, -unit * .15f, -unit * 2.2f, -unit * 1.2f, -unit * 1.42f, -unit * .85f)
        path.cubicTo(-unit * 1.2f, -unit * 2.2f, unit * .28f, -unit * 2f, unit * .6f, -unit * .91f)
        path.cubicTo(unit * 1.55f, -unit * 1.5f, unit * 2.7f, -unit * .6f, unit * 2.32f, unit * .15f)
        path.cubicTo(unit * 2.15f, unit * .75f, -unit * 1.1f, unit * .7f, -unit * 2.2f, unit * .42f)
        path.close()
        fill(0xFFA9BCD0.toInt(), (75 * strength).toInt())
        canvas.drawPath(path, paint)
        canvas.translate(0f, -unit * .14f)
        fill(Color.WHITE, (190 * strength).toInt())
        canvas.drawPath(path, paint)
        canvas.restoreToCount(saved)
    }

    private fun winter(canvas: Canvas, w: Float, h: Float, light: Boolean, time: Float, banner: Boolean) {
        fill(Color.WHITE)
        paint.shader = frost
        canvas.drawRect(0f, 0f, w, min(h * .12f, 32f * density), paint)
        val saved = canvas.save()
        canvas.translate(w, h)
        canvas.rotate(180f)
        canvas.drawRect(0f, 0f, w, min(h * .12f, 32f * density), paint)
        canvas.restoreToCount(saved)
        paint.shader = null
        frostSprig(canvas, 0f, 0f, 1f, 1f, light, banner)
        frostSprig(canvas, w, h, -1f, -1f, light, banner)
        if (banner) return
        for (i in particles.indices) {
            val p = particles[i]
            val x = ((p[0] + sin(time * .22f + p[3]) * .035f + 1f) % 1f) * w
            val y = ((p[1] + time * p[4]) % 1f) * (h + 12f * density) - 6f * density
            val r = p[2] * density
            if (i % 7 == 0) snowCrystal(canvas, x, y, r * 2f, light, time * .05f + p[3])
            else {
                fill(if (light) 0xFF628FAD.toInt() else 0xFFE5F3FF.toInt(), if (light) 95 else 168)
                canvas.drawCircle(x, y, r, paint)
                fill(Color.WHITE, if (light) 170 else 130)
                canvas.drawCircle(x - r * .15f, y - r * .2f, r * .45f, paint)
            }
        }
    }

    private fun frostSprig(canvas: Canvas, x: Float, y: Float, sx: Float, sy: Float, light: Boolean, banner: Boolean) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(sx, sy)
        val size = (if (banner) 24f else 58f) * density
        stroke(if (light) 0xFF6BA7BD.toInt() else 0xFFCEECFF.toInt(), if (banner) 100 else 110, .85f)
        canvas.drawLine(0f, 0f, size, size * .7f, paint)
        for (i in 1..6) {
            val t = i / 7f
            val px = size * t
            val py = px * .7f
            val length = size * (1f - t) * .3f
            canvas.drawLine(px, py, px - length * .15f, py + length, paint)
            canvas.drawLine(px, py, px + length, py - length * .45f, paint)
        }
        canvas.restoreToCount(saved)
    }

    private fun snowCrystal(canvas: Canvas, x: Float, y: Float, radius: Float, light: Boolean, angle: Float) {
        stroke(if (light) 0xFF729FB7.toInt() else 0xFFE0F4FF.toInt(), if (light) 145 else 175, .8f)
        for (arm in 0 until 6) {
            val a = angle + arm * PI.toFloat() / 3f
            val dx = cos(a)
            val dy = sin(a)
            canvas.drawLine(x, y, x + dx * radius, y + dy * radius, paint)
            val bx = x + dx * radius * .6f
            val by = y + dy * radius * .6f
            canvas.drawLine(bx, by, bx + cos(a - .7f) * radius * .3f, by + sin(a - .7f) * radius * .3f, paint)
            canvas.drawLine(bx, by, bx + cos(a + .7f) * radius * .3f, by + sin(a + .7f) * radius * .3f, paint)
        }
    }

    private fun garland(canvas: Canvas, w: Float, light: Boolean, time: Float) {
        // A narrow band at the very top; no decoration crosses labels, back arrows or controls.
        val top = density * 2f
        val dip = density * 4f
        stroke(if (light) 0xFF558A76.toInt() else 0xFF638F80.toInt(), 180, 1.2f)
        path.rewind()
        path.moveTo(0f, top)
        path.quadTo(w * .25f, top + dip * 2f, w * .5f, top)
        path.quadTo(w * .75f, top + dip * 2f, w, top)
        canvas.drawPath(path, paint)
        val count = (w / (24f * density)).toInt().coerceIn(5, 28)
        for (i in 0 until count) {
            val x = w * (i + .5f) / count
            val phase = ((i + .5f) / count * 2f) % 1f
            val y = top + 4f * dip * phase * (1f - phase)
            val pulse = .55f + .45f * (.5f + .5f * sin(time * 1.25f + (i % 2) * PI.toFloat()))
            val color = bulbs[i % bulbs.size]
            stroke(0xFF577464.toInt(), 190, .7f)
            canvas.drawLine(x, y, x, y + density * 2f, paint)
            fill(color, (32 * pulse).toInt())
            canvas.drawCircle(x, y + density * 3.5f, density * 5f, paint)
            fill(color, (230 * pulse).toInt())
            oval.set(x - density * 1.7f, y + density, x + density * 1.7f, y + density * 6f)
            canvas.drawOval(oval, paint)
            fill(Color.WHITE, (160 * pulse).toInt())
            canvas.drawCircle(x - density * .4f, y + density * 2.5f, density * .55f, paint)
        }
    }
}
