package com.Atom2Universe.app.gallery

import android.graphics.*
import kotlin.math.roundToInt

/** Coordinates stay in image space (0..1) so the same gesture works on preview and export. */
internal data class Trace(val points: List<PointF>, val width: Float = 0f, val rectangle: Boolean = false) {
    fun bounds(): RectF {
        val a = points.first()
        val b = points.last()
        return RectF(minOf(a.x,b.x), minOf(a.y,b.y), maxOf(a.x,b.x), maxOf(a.y,b.y))
    }
    fun draw(canvas: Canvas, w: Int, h: Int, paint: Paint) {
        val start = points.first()
        if (rectangle) {
            val rect = bounds()
            canvas.drawRect(rect.left*w, rect.top*h, rect.right*w, rect.bottom*h, paint)
            return
        }
        if (width > 0f) {
            paint.strokeWidth = (width * minOf(w,h)).coerceAtLeast(1f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            if (points.size == 1) {
                canvas.drawCircle(start.x*w, start.y*h, paint.strokeWidth/2, paint)
                return
            }
            paint.style = Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(start.x*w, start.y*h)
            for (i in 1 until points.size) lineTo(points[i].x*w, points[i].y*h)
            if (width == 0f) close()
        }
        canvas.drawPath(path, paint)
    }
}

internal sealed interface MaskPart {
    data class Shape(val trace: Trace, val subtract: Boolean = false) : MaskPart
    data class Region(val alpha: Bitmap) : MaskPart
}

internal data class PhotoMask(val parts: List<MaskPart>) {
    /** Isolate the mask so subtractive strokes only erase mask coverage. */
    fun draw(canvas: Canvas, w: Int, h: Int, blend: PorterDuff.Mode, tint: Int = Color.WHITE) {
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val layer = canvas.saveLayer(rect, Paint().apply {
            xfermode = PorterDuffXfermode(blend)
            alpha = Color.alpha(tint)
        })
        val color = tint or Color.BLACK
        for (part in parts) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).also { it.color = color }
            when (part) {
                is MaskPart.Shape -> {
                    if (part.subtract) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    part.trace.draw(canvas, w, h, paint)
                }
                is MaskPart.Region -> canvas.drawBitmap(part.alpha, null, rect, paint)
            }
        }
        canvas.restoreToCount(layer)
    }
}

internal enum class PhotoLook(val label: Int) {
    MONO(com.Atom2Universe.app.R.string.photo_mono), SEPIA(com.Atom2Universe.app.R.string.photo_sepia),
    VIVID(com.Atom2Universe.app.R.string.photo_vivid), COOL(com.Atom2Universe.app.R.string.photo_cool),
    WARM(com.Atom2Universe.app.R.string.photo_warm);

    fun matrix(): ColorMatrix = when (this) {
        MONO -> ColorMatrix().apply { setSaturation(0f) }
        VIVID -> toneMatrix(0, 12, 36)
        WARM -> ColorMatrix().apply { setScale(1.09f, 1.01f, 0.90f, 1f) }
        COOL -> ColorMatrix().apply { setScale(0.91f, 1.01f, 1.09f, 1f) }
        SEPIA -> ColorMatrix(floatArrayOf(.393f,.769f,.189f,0f,0f, .349f,.686f,.168f,0f,0f,
            .272f,.534f,.131f,0f,0f, 0f,0f,0f,1f,0f))
    }
}

internal fun toneMatrix(light: Int, contrast: Int, saturation: Int): ColorMatrix {
    val slope = 1f + contrast / 100f
    val offset = light * 1.5f + 128f * (1f - slope)
    return ColorMatrix().apply {
        setSaturation(1f + saturation / 100f)
        postConcat(ColorMatrix(floatArrayOf(slope,0f,0f,0f,offset, 0f,slope,0f,0f,offset,
            0f,0f,slope,0f,offset, 0f,0f,0f,1f,0f)))
    }
}

internal sealed interface PhotoEdit {
    data class Tone(val matrix: ColorMatrix, val mask: PhotoMask?) : PhotoEdit
    data class Ink(val trace: Trace, val color: Int, val erase: Boolean, val mask: PhotoMask?) : PhotoEdit
    data class Cut(val mask: PhotoMask, val keep: Boolean) : PhotoEdit
    data class Crop(val rect: RectF) : PhotoEdit
    data object Rotate : PhotoEdit
    data object Mirror : PhotoEdit
}

internal object PhotoRender {
    fun tone(canvas: Canvas, src: Bitmap, tone: PhotoEdit.Tone, isolate: Boolean = true) {
        val rect = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(tone.matrix) }
        val mask = tone.mask
        if (mask == null) {
            canvas.drawBitmap(src, null, rect, paint)
            return
        }
        // Add complementary premultiplied contributions, including on semi-transparent edges.
        val isolated = if (isolate) canvas.saveLayer(rect, null) else canvas.save()
        canvas.drawBitmap(src, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        mask.draw(canvas, src.width, src.height, PorterDuff.Mode.DST_OUT)
        val filtered = canvas.saveLayer(rect, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
        canvas.drawBitmap(src, null, rect, paint)
        mask.draw(canvas, src.width, src.height, PorterDuff.Mode.DST_IN)
        canvas.restoreToCount(filtered)
        canvas.restoreToCount(isolated)
    }

    fun apply(src: Bitmap, edit: PhotoEdit): Bitmap {
        val w = src.width
        val h = src.height
        when (edit) {
            is PhotoEdit.Crop -> {
                val r = edit.rect
                val x = (r.left*w).roundToInt().coerceIn(0,w-1)
                val y = (r.top*h).roundToInt().coerceIn(0,h-1)
                return Bitmap.createBitmap(src, x, y, ((r.right*w).roundToInt()-x).coerceIn(1,w-x),
                    ((r.bottom*h).roundToInt()-y).coerceIn(1,h-y))
            }
            PhotoEdit.Rotate, PhotoEdit.Mirror -> {
                val m = Matrix().apply { if (edit == PhotoEdit.Rotate) setRotate(90f) else setScale(-1f,1f) }
                return Bitmap.createBitmap(src, 0, 0, w, h, m, true)
            }
            else -> Unit
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (edit is PhotoEdit.Tone) tone(canvas, src, edit, isolate = false)
        else {
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawBitmap(src, null, rect, null)
            when (edit) {
                is PhotoEdit.Cut -> edit.mask.draw(canvas, w, h, if (edit.keep) PorterDuff.Mode.DST_IN else PorterDuff.Mode.DST_OUT)
                is PhotoEdit.Ink -> {
                    val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (edit.erase) Color.WHITE else edit.color
                    }
                    if (edit.mask == null) {
                        if (edit.erase) brush.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                        edit.trace.draw(canvas,w,h,brush)
                        return result
                    }
                    val layer = canvas.saveLayer(rect, Paint().apply {
                        if (edit.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    })
                    edit.trace.draw(canvas,w,h,brush)
                    edit.mask.draw(canvas,w,h,PorterDuff.Mode.DST_IN)
                    canvas.restoreToCount(layer)
                }
                else -> Unit
            }
        }
        return result
    }
}
