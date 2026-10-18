package com.Atom2Universe.app.games.jigsaw

import android.graphics.*
import com.Atom2Universe.app.games.kit.KitPalette
import kotlin.math.roundToInt

/** Cached paths and one image shader; no bitmap copies or offscreen layers per piece. */
class JigsawVictoryRenderer(
    private val game: JigsawGame,
    private val image: Bitmap,
    private val texture: Shader,
    private val pieces: List<Path>,
    private val palette: KitPalette,
    private val density: Float,
) {
    private val motion = JigsawVictoryMotion(game.grid)
    private val centers = pieces.indices.map { game.center(it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frame = RectF(0f, 0f, game.grid.width, game.grid.height)
    private val remaining = Path()
    private val tip = FloatArray(2)
    private val seams = JigsawGeometry(game.grid, game.seed).seams().map { curves ->
        val path = Path().apply {
            moveTo(curves.first().start.x, curves.first().start.y)
            curves.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
        }
        PathMeasure(path, false)
    }
    private val seamLength = seams.sumOf { it.length.toDouble() }.toFloat()

    fun draw(canvas: Canvas, progress: Float, scale: Float) {
        if (progress < JigsawVictoryMotion.FUSED_AT) drawPieces(canvas, progress, scale)
        else {
            paint.shader = null; paint.style = Paint.Style.FILL; paint.alpha = 255
            canvas.drawBitmap(image, null, frame, paint)
            drawSeams(canvas, progress, scale)
        }
    }

    private fun drawPieces(canvas: Canvas, progress: Float, scale: Float) {
        pieces.forEachIndexed { id, path ->
            val center = centers[id]
            val lift = motion.lift(id, progress)
            val dx = (center.x - game.grid.width / 2f) * .045f * lift
            val dy = ((center.y - game.grid.height / 2f) * .032f - game.grid.cell * .085f) * lift
            val tilt = (if ((id % game.grid.columns + id / game.grid.columns) % 2 == 0) 1f else -1f) * 1.4f * lift
            canvas.save()
            canvas.translate(dx, dy)
            canvas.rotate(tilt, center.x, center.y)
            if (lift > 0f) {
                canvas.save()
                canvas.translate(0f, 3f * density * lift / scale)
                paint.shader = null; paint.style = Paint.Style.FILL
                paint.color = Color.argb((65f * lift).roundToInt(), 0, 0, 0)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            paint.shader = texture; paint.style = Paint.Style.FILL; paint.alpha = 255
            canvas.drawPath(path, paint)
            stroke(canvas, path, scale, 1f)
            canvas.restore()
        }
    }

    private fun drawSeams(canvas: Canvas, progress: Float, scale: Float) {
        val unlace = JigsawVictoryMotion.unlace(progress)
        if (unlace >= 1f) return
        var removed = unlace * seamLength
        val fade = 1f - JigsawVictoryMotion.ease((unlace - .84f) / .16f)
        var hasTip = false
        remaining.rewind()
        seams.forEach { seam ->
            val length = seam.length
            if (removed >= length) removed -= length
            else {
                seam.getSegment(removed, length, remaining, true)
                if (!hasTip && unlace > 0f) hasTip = seam.getPosTan(removed, tip, null)
                removed = 0f
            }
        }
        stroke(canvas, remaining, scale, fade)
        if (hasTip) {
            paint.shader = null; paint.style = Paint.Style.FILL
            paint.color = palette.accent; paint.alpha = (42f * fade).roundToInt()
            canvas.drawCircle(tip[0], tip[1], 9f * density / scale, paint)
            paint.alpha = (110f * fade).roundToInt()
            canvas.drawCircle(tip[0], tip[1], 4f * density / scale, paint)
            paint.color = Color.WHITE; paint.alpha = (235f * fade).roundToInt()
            canvas.drawCircle(tip[0], tip[1], 1.7f * density / scale, paint)
            paint.alpha = 255
        }
    }

    private fun stroke(canvas: Canvas, path: Path, scale: Float, alpha: Float) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.4f * density / scale
        paint.color = Color.argb((180f * alpha).roundToInt(), 0, 0, 0)
        canvas.drawPath(path, paint)
        paint.strokeWidth = .85f * density / scale
        paint.color = Color.argb((170f * alpha).roundToInt(), 255, 255, 255)
        canvas.drawPath(path, paint)
    }
}
