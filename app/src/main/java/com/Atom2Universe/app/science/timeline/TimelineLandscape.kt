package com.Atom2Universe.app.science.timeline

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.min

/** Small original schematics, drawn once per invalidation; no bitmaps or animation loop. */
class TimelineLandscape {
    private val art = CosmicArt()
    private val humanArt = HumanTimelineArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()

    fun draw(canvas: Canvas, box: RectF, chapter: CosmicPeriod, palette: SciencePalette) {
        if (box.width() <= 0 || box.height() <= 0) return
        if (chapter.human != null) {
            humanArt.draw(canvas, box, chapter, palette)
            return
        }
        if (chapter.datedAncestorId != null) {
            drawAncestry(canvas, box, chapter, palette)
            return
        }
        val forest = chapter.id in listOf("triassic", "jurassic", "cretaceous", "paleogene", "devonian", "carboniferous")
        val ground = forest || chapter.id in listOf("permian", "neogene", "cenozoic", "quaternary")
        if (!ground) {
            art.draw(canvas, box, chapter.art, background = !chapter.art.geological)
            return
        }
        val saved = canvas.save(); canvas.clipRect(box); canvas.translate(box.left, box.top)
        val w = box.width(); val h = box.height(); val u = min(w, h)
        fun color(strength: Float) = ColorUtils.blendARGB(palette.surface, chapter.color, strength)
        paint.style = Paint.Style.FILL; paint.color = color(.16f)
        canvas.drawRect(0f, 0f, w, h, paint)
        for (layer in 0..2) {
            paint.color = color(.26f + layer * .13f)
            shape.reset(); shape.moveTo(0f, h * (.44f + layer * .13f))
            shape.cubicTo(w * .3f, h * (.2f + layer * .16f), w * .6f, h * (.7f + layer * .05f), w, h * (.4f + layer * .18f))
            shape.lineTo(w, h); shape.lineTo(0f, h); shape.close(); canvas.drawPath(shape, paint)
        }
        if (chapter.id == "quaternary") {
            repeat(3) { i ->
                val x = w * (.2f + i * .3f)
                shape.reset(); shape.moveTo(x - u * .4f, h); shape.lineTo(x, h * (.12f + (i % 2) * .12f))
                shape.lineTo(x + u * .5f, h); shape.close()
                paint.color = color(.72f); canvas.drawPath(shape, paint)
            }
        } else if (forest) {
            val count = (w / (h * .26f)).toInt().coerceIn(3, 20)
            for (layer in 0..1) repeat(count) { i ->
                val x = w * (i + .3f + layer * .25f) / count
                val base = h * (.77f + layer * .2f)
                val size = h * (.33f + ((i * 7) % 5) * .055f)
                paint.color = ColorUtils.blendARGB(color(.65f), palette.text, if (layer == 0) .08f else .28f)
                if (chapter.id in listOf("devonian", "carboniferous")) fern(canvas, x, base, size)
                else tree(canvas, x, base, size)
                if (chapter.id in listOf("cretaceous", "paleogene") && i % 3 == 1) {
                    paint.color = color(.96f)
                    repeat(5) { petal ->
                        val angle = petal * 1.2566f
                        canvas.drawCircle(x + kotlin.math.cos(angle) * u * .035f,
                            base - size + kotlin.math.sin(angle) * u * .035f, u * .024f, paint)
                    }
                }
            }
            if (chapter.id == "jurassic" && w > h * .65f) {
                paint.color = ColorUtils.blendARGB(chapter.color, palette.text, .48f)
                sauropod(canvas, w * .51f, h * .91f, min(w * .76f, h * 1.2f))
            }
        } else if (chapter.id in listOf("neogene", "cenozoic")) {
            paint.color = ColorUtils.blendARGB(chapter.color, palette.text, .3f)
            repeat(16) { i -> fern(canvas, w * i / 15f, h, h * (.15f + (i % 3) * .035f)) }
        }
        canvas.restoreToCount(saved)
    }

    /** An abstract branching symbol, never a reconstruction or dated phylogram. */
    private fun drawAncestry(canvas: Canvas, box: RectF, chapter: CosmicPeriod, palette: SciencePalette) {
        val saved = canvas.save(); canvas.clipRect(box)
        val cx = box.centerX(); val cy = box.top + box.height() * .63f
        val u = min(box.width() * .7f, box.height() * 1.8f)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = u * .014f
        paint.strokeCap = Paint.Cap.ROUND; paint.color = palette.mark(chapter.color)
        val top = cy - u * .25f
        for (side in listOf(-1, 1)) {
            val fork = cx + side * u * .18f
            shape.reset(); shape.moveTo(cx, cy)
            shape.cubicTo(cx, cy - u * .14f, fork, cy - u * .05f, fork, cy - u * .17f)
            canvas.drawPath(shape, paint)
            for (tip in listOf(-1, 1)) {
                val x = fork + tip * u * .08f
                shape.reset(); shape.moveTo(fork, cy - u * .17f)
                shape.quadTo(fork, top, x, top - u * .07f)
                canvas.drawPath(shape, paint)
                canvas.drawCircle(x, top - u * .1f, u * .027f, paint)
            }
        }
        canvas.drawLine(cx, cy, cx, cy + u * .13f, paint)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(palette.mark(chapter.color), 45)
        canvas.drawCircle(cx, cy, u * .085f, paint)
        paint.color = palette.mark(chapter.color)
        canvas.drawCircle(cx, cy, u * .025f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        canvas.restoreToCount(saved)
    }

    private fun tree(canvas: Canvas, x: Float, base: Float, size: Float) {
        paint.strokeWidth = size * .027f
        canvas.drawLine(x, base, x, base - size, paint)
        repeat(4) { tier ->
            val y = base - size + tier * size * .18f
            val half = size * (.11f + tier * .052f)
            shape.reset(); shape.moveTo(x, y); shape.lineTo(x + half, y + size * .27f)
            shape.lineTo(x - half, y + size * .27f); shape.close(); canvas.drawPath(shape, paint)
        }
    }

    private fun fern(canvas: Canvas, x: Float, base: Float, size: Float) {
        paint.strokeWidth = size * .025f
        canvas.drawLine(x, base, x, base - size, paint)
        repeat(7) { i ->
            val y = base - size * (.1f + i * .12f)
            val reach = size * (.24f - i * .027f)
            canvas.drawLine(x, y, x - reach, y - size * .15f, paint)
            canvas.drawLine(x, y, x + reach, y - size * .15f, paint)
        }
    }

    /** A generic sauropod silhouette, not a reconstruction of a named species. */
    private fun sauropod(canvas: Canvas, x: Float, base: Float, size: Float) {
        val saved = canvas.save(); canvas.translate(x, base); canvas.scale(size, size)
        shape.reset(); shape.moveTo(-.24f, -.14f)
        shape.cubicTo(-.34f, -.16f, -.49f, -.22f, -.57f, -.27f)
        shape.cubicTo(-.4f, -.22f, -.27f, -.23f, -.2f, -.25f)
        shape.cubicTo(-.08f, -.42f, .12f, -.4f, .2f, -.27f)
        shape.cubicTo(.27f, -.33f, .24f, -.55f, .34f, -.64f)
        shape.cubicTo(.39f, -.67f, .46f, -.66f, .47f, -.61f)
        shape.cubicTo(.46f, -.57f, .38f, -.62f, .37f, -.55f)
        shape.cubicTo(.35f, -.4f, .39f, -.21f, .19f, -.13f)
        shape.lineTo(.16f, 0f); shape.lineTo(.1f, 0f); shape.lineTo(.09f, -.13f)
        shape.lineTo(-.1f, -.14f); shape.lineTo(-.11f, 0f); shape.lineTo(-.18f, 0f)
        shape.lineTo(-.21f, -.13f); shape.close(); canvas.drawPath(shape, paint)
        canvas.restoreToCount(saved)
    }
}
