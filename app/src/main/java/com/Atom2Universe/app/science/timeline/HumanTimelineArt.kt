package com.Atom2Universe.app.science.timeline

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.min

/** Original thematic symbols, not reconstructions of a particular people, site or artefact. */
class HumanTimelineArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(canvas: Canvas, bounds: RectF, chapter: CosmicPeriod, palette: SciencePalette) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val saved = canvas.save(); canvas.clipRect(bounds)
        val unit = min(bounds.width() / 1.8f, bounds.height())
        canvas.translate(bounds.centerX(), bounds.centerY())
        canvas.scale(unit, unit)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.blendARGB(palette.surface, chapter.color, .15f)
        canvas.drawRect(-bounds.width() / unit, -.6f, bounds.width() / unit, .6f, paint)
        paint.color = palette.mark(chapter.color); paint.strokeWidth = .017f
        paint.strokeCap = Paint.Cap.ROUND
        when {
            chapter.id == "human_foragers" -> {
                // Footprints are symbols of mobility, not traced archaeological specimens.
                for (side in listOf(-1, 1)) {
                    val x = side * .17f; val y = side * -.075f
                    canvas.drawOval(x - .052f, y - .13f, x + .052f, y + .17f, paint)
                    repeat(4) { toe ->
                        val tx = x - .055f + toe * .035f
                        canvas.drawCircle(tx, y - .2f + toe * .008f, .026f - toe * .003f, paint)
                    }
                }
            }
            chapter.id == "human_villages" -> {
                paint.style = Paint.Style.STROKE
                repeat(3) { i ->
                    val x = -.54f + i * .42f
                    canvas.drawRect(x, -.05f, x + .25f, .22f, paint)
                    path.reset(); path.moveTo(x - .02f, -.05f); path.lineTo(x + .125f, -.22f)
                    path.lineTo(x + .27f, -.05f); canvas.drawPath(path, paint)
                }
                repeat(4) { i -> canvas.drawLine(-.65f, .28f + i * .04f, .65f, .28f + i * .04f, paint) }
            }
            chapter.id == "human_oceans" -> {
                paint.style = Paint.Style.STROKE
                path.reset(); path.moveTo(-.43f, .15f); path.lineTo(-.3f, .3f)
                path.lineTo(.28f, .3f); path.lineTo(.43f, .15f); path.close(); canvas.drawPath(path, paint)
                canvas.drawLine(0f, -.35f, 0f, .15f, paint)
                path.reset(); path.moveTo(-.035f, -.32f); path.lineTo(-.035f, .1f)
                path.lineTo(-.33f, .1f); path.close(); canvas.drawPath(path, paint)
                path.reset(); path.moveTo(.035f, -.25f); path.lineTo(.28f, .08f)
                path.lineTo(.035f, .08f); path.close(); canvas.drawPath(path, paint)
                wave(canvas, .39f)
            }
            chapter.id == "human_industry" -> {
                paint.style = Paint.Style.STROKE
                path.reset(); path.moveTo(-.48f, .3f); path.lineTo(-.48f, -.07f)
                path.lineTo(-.18f, -.23f); path.lineTo(-.18f, -.07f)
                path.lineTo(.12f, -.23f); path.lineTo(.12f, -.07f)
                path.lineTo(.4f, -.07f); path.lineTo(.4f, .3f); path.close()
                canvas.drawPath(path, paint)
                canvas.drawRect(.24f, -.35f, .33f, -.07f, paint)
                repeat(4) { i -> canvas.drawRect(-.37f + i * .19f, .055f, -.29f + i * .19f, .16f, paint) }
            }
            chapter.id == "human_wars" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(-.6f, .32f, .6f, .32f, paint)
                path.reset(); path.moveTo(-.43f, .32f); path.lineTo(-.43f, -.24f)
                path.lineTo(-.08f, -.24f); path.lineTo(-.19f, -.07f)
                path.lineTo(-.07f, .045f); path.lineTo(-.2f, .2f); canvas.drawPath(path, paint)
                path.reset(); path.moveTo(.43f, .32f); path.lineTo(.43f, -.1f)
                path.lineTo(.17f, -.1f); path.lineTo(.25f, .07f); canvas.drawPath(path, paint)
                canvas.drawLine(-.52f, .39f, .52f, .39f, paint)
            }
            chapter.id in listOf("human", "human_modern", "human_postwar", "human_connected") -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(0f, 0f, .34f, paint)
                canvas.drawOval(-.17f, -.34f, .17f, .34f, paint)
                canvas.drawOval(-.34f, -.13f, .34f, .13f, paint)
                if (chapter.id != "human_postwar") {
                    canvas.drawLine(-.63f, -.22f, -.26f, -.22f, paint)
                    canvas.drawLine(.28f, .17f, .58f, .26f, paint)
                    canvas.drawLine(.26f, -.22f, .6f, -.3f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(-.63f, -.22f, .04f, paint)
                    canvas.drawCircle(.58f, .26f, .04f, paint)
                    canvas.drawCircle(.6f, -.3f, .04f, paint)
                }
            }
            else -> {
                paint.style = Paint.Style.STROKE
                repeat(5) { i ->
                    val x = -.6f + i * .25f
                    val top = -.05f - (i % 3) * .095f
                    canvas.drawRect(x, top, x + .21f, .3f, paint)
                    canvas.drawLine(x + .07f, .3f, x + .07f, .12f, paint)
                }
                canvas.drawLine(-.68f, .35f, .68f, .35f, paint)
            }
        }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
        canvas.restoreToCount(saved)
    }

    private fun wave(canvas: Canvas, y: Float) {
        path.reset(); path.moveTo(-.7f, y)
        repeat(5) { i ->
            val x = -.7f + i * .28f
            path.quadTo(x + .07f, y - .065f, x + .14f, y)
            path.quadTo(x + .21f, y + .065f, x + .28f, y)
        }
        canvas.drawPath(path, paint)
    }
}

