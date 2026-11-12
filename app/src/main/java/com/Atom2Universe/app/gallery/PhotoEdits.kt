package com.Atom2Universe.app.gallery

import android.graphics.*
import com.Atom2Universe.app.R
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Coordinates stay in image space (0..1) so the same gesture works on preview and export. */
internal data class Trace(val points: List<PointF>, val width: Float = 0f, val rectangle: Boolean = false,
    val oval: Boolean = false, val soft: Float = 0f) {
    fun bounds(): RectF {
        val a = points.first()
        val b = points.last()
        return RectF(minOf(a.x,b.x), minOf(a.y,b.y), maxOf(a.x,b.x), maxOf(a.y,b.y))
    }
    fun draw(canvas: Canvas, w: Int, h: Int, paint: Paint) {
        val start = points.first()
        if (rectangle || oval) {
            val rect = bounds()
            rect.set(rect.left*w, rect.top*h, rect.right*w, rect.bottom*h)
            if (oval) canvas.drawOval(rect, paint) else canvas.drawRect(rect, paint)
            return
        }
        if (width > 0f) {
            val full = (width * minOf(w,h)).coerceAtLeast(1f)
            // A soft brush keeps about the same footprint: thinner core, blurred rim.
            paint.strokeWidth = full * (1f - soft * 0.3f)
            if (soft > 0f) paint.maskFilter = BlurMaskFilter((full * soft * 0.15f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
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
    /** Flips everything selected so far; later parts apply on top. */
    data object Invert : MaskPart
}

/** [feather] is a blur radius relative to the short side of the image. */
internal data class PhotoMask(val parts: List<MaskPart>, val feather: Float = 0f) {
    private var soft: Bitmap? = null
    private var softKey = 0L

    /** Isolate the mask so subtractive strokes only erase mask coverage. */
    fun draw(canvas: Canvas, w: Int, h: Int, blend: PorterDuff.Mode, tint: Int = Color.WHITE) {
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val layer = canvas.saveLayer(rect, Paint().apply {
            xfermode = PorterDuffXfermode(blend)
            alpha = Color.alpha(tint)
        })
        val color = tint or Color.BLACK
        if (feather > 0f) canvas.drawBitmap(softened(w, h), null, rect, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }) else paint(canvas, w, h, color)
        canvas.restoreToCount(layer)
    }

    private fun paint(canvas: Canvas, w: Int, h: Int, color: Int) {
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        for (part in parts) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).also { it.color = color }
            when (part) {
                is MaskPart.Shape -> {
                    if (part.subtract) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    part.trace.draw(canvas, w, h, paint)
                }
                is MaskPart.Region -> canvas.drawBitmap(part.alpha, null, rect, paint)
                MaskPart.Invert -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
                    canvas.drawRect(rect, paint)
                }
            }
        }
    }

    /** The blurred coverage is computed small, once per target size, then stretched. */
    @Synchronized private fun softened(w: Int, h: Int): Bitmap {
        val key = w.toLong() shl 32 or h.toLong()
        soft?.takeIf { softKey == key }?.let { return it }
        val radius = feather * minOf(w, h)
        val scale = minOf(1f, 3f / radius, 1024f / maxOf(w, h))
        val sw = (w * scale).roundToInt().coerceAtLeast(1)
        val sh = (h * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        paint(Canvas(small), sw, sh, Color.WHITE)
        PhotoRender.boxBlur(small, (radius * scale).roundToInt().coerceAtLeast(1))
        soft = small
        softKey = key
        return small
    }
}

internal enum class PhotoLook(val label: Int) {
    MONO(R.string.photo_mono), NOIR(R.string.photo_noir), SEPIA(R.string.photo_sepia),
    VIVID(R.string.photo_vivid), DRAMA(R.string.photo_drama), FADE(R.string.photo_fade),
    COOL(R.string.photo_cool), WARM(R.string.photo_warm), NEGATIVE(R.string.photo_negative);

    fun matrix(): ColorMatrix = when (this) {
        MONO -> ColorMatrix().apply { setSaturation(0f) }
        NOIR -> toneMatrix(-6, 45, -100)
        VIVID -> toneMatrix(0, 12, 36)
        DRAMA -> toneMatrix(-4, 38, -25)
        FADE -> toneMatrix(14, -22, -18)
        WARM -> ColorMatrix().apply { setScale(1.09f, 1.01f, 0.90f, 1f) }
        COOL -> ColorMatrix().apply { setScale(0.91f, 1.01f, 1.09f, 1f) }
        SEPIA -> ColorMatrix(floatArrayOf(.393f,.769f,.189f,0f,0f, .349f,.686f,.168f,0f,0f,
            .272f,.534f,.131f,0f,0f, 0f,0f,0f,1f,0f))
        NEGATIVE -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
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

/** Blend between the untouched image (0) and the full effect (1). */
internal fun ColorMatrix.faded(strength: Float): ColorMatrix {
    if (strength >= 1f) return this
    val identity = ColorMatrix().array
    val full = array
    return ColorMatrix(FloatArray(20) { identity[it] + (full[it] - identity[it]) * strength })
}

/** Sliders of the Adjust tab. [min] is -100 for two-way settings and 0 for one-way effects. */
internal enum class AdjustChannel(val label: Int, val min: Int = -100) {
    LIGHT(R.string.photo_light), CONTRAST(R.string.photo_contrast), HIGHLIGHTS(R.string.photo_highlights),
    SHADOWS(R.string.photo_shadows), SATURATION(R.string.photo_saturation), WARMTH(R.string.photo_warmth),
    TINT(R.string.photo_tint), SHARPNESS(R.string.photo_sharpness, 0), BLUR(R.string.photo_blur, 0),
    VIGNETTE(R.string.photo_vignette)
}

internal sealed interface PhotoEdit {
    data class Tone(val matrix: ColorMatrix, val mask: PhotoMask?) : PhotoEdit
    /** [values] follow [AdjustChannel]; [black] and [white] stretch the levels first. */
    data class Adjust(val values: List<Int>, val mask: PhotoMask?, val black: Int = 0, val white: Int = 255) : PhotoEdit
    data class Ink(val trace: Trace, val color: Int, val erase: Boolean, val mask: PhotoMask?, val opacity: Float = 1f) : PhotoEdit
    /** Paints with the image itself, read at [offset] (source minus destination) from the stroke. */
    data class Clone(val trace: Trace, val offset: PointF, val mask: PhotoMask?, val opacity: Float = 1f) : PhotoEdit
    /** Erases what lies under the stroke by continuing its surroundings. */
    data class Heal(val trace: Trace, val mask: PhotoMask?) : PhotoEdit
    data class Fill(val color: Int, val mask: PhotoMask, val opacity: Float = 1f) : PhotoEdit
    data class Cut(val mask: PhotoMask, val keep: Boolean) : PhotoEdit
    /** Straighten by [angle] degrees (the picture is enlarged to stay full), then crop to [rect]. */
    data class Frame(val angle: Float, val rect: RectF) : PhotoEdit
    data object Rotate : PhotoEdit
    data object Mirror : PhotoEdit
}

internal object PhotoRender {
    private fun whole(src: Bitmap) = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())

    /** Draws [src] with [filtered] replacing it inside [mask] (everywhere without a mask). */
    private fun blend(canvas: Canvas, src: Bitmap, mask: PhotoMask?, isolate: Boolean, filtered: (Canvas) -> Unit) {
        if (mask == null) return filtered(canvas)
        val rect = whole(src)
        // Add complementary premultiplied contributions, including on semi-transparent edges.
        val isolated = if (isolate) canvas.saveLayer(rect, null) else canvas.save()
        canvas.drawBitmap(src, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        mask.draw(canvas, src.width, src.height, PorterDuff.Mode.DST_OUT)
        val layer = canvas.saveLayer(rect, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
        filtered(canvas)
        mask.draw(canvas, src.width, src.height, PorterDuff.Mode.DST_IN)
        canvas.restoreToCount(layer)
        canvas.restoreToCount(isolated)
    }

    fun tone(canvas: Canvas, src: Bitmap, tone: PhotoEdit.Tone, isolate: Boolean = true) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(tone.matrix) }
        blend(canvas, src, tone.mask, isolate) { it.drawBitmap(src, null, whole(src), paint) }
    }

    /** Rotation about the centre, enlarged just enough to leave no empty corner. */
    fun straighten(w: Float, h: Float, angle: Float) = Matrix().apply {
        val rad = Math.toRadians(abs(angle).toDouble())
        // A hair more than the exact fit, so no half-covered pixel shows on the border.
        val scale = (cos(rad) + maxOf(w/h, h/w) * sin(rad)).toFloat() * 1.004f
        setRotate(angle, w/2, h/2)
        postScale(scale, scale, w/2, h/2)
    }

    private fun frame(src: Bitmap, edit: PhotoEdit.Frame): Bitmap {
        val w = src.width
        val h = src.height
        val level = if (edit.angle == 0f) src else Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { concat(straighten(w.toFloat(), h.toFloat(), edit.angle)) }
                .drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val r = edit.rect
        val x = (r.left*w).roundToInt().coerceIn(0,w-1)
        val y = (r.top*h).roundToInt().coerceIn(0,h-1)
        val cw = ((r.right*w).roundToInt()-x).coerceIn(1,w-x)
        val ch = ((r.bottom*h).roundToInt()-y).coerceIn(1,h-y)
        if (cw == w && ch == h) return level
        val cut = Bitmap.createBitmap(level, x, y, cw, ch)
        if (level !== src && level !== cut) level.recycle()
        return cut
    }

    /** One brush-like layer: [body] paints coverage and colour, the selection then limits it. */
    private fun stroke(canvas: Canvas, src: Bitmap, mask: PhotoMask?, opacity: Float, erase: Boolean, body: () -> Unit) {
        val layer = canvas.saveLayer(whole(src), Paint().apply {
            alpha = (opacity * 255).roundToInt().coerceIn(0, 255)
            if (erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        })
        body()
        mask?.draw(canvas, src.width, src.height, PorterDuff.Mode.DST_IN)
        canvas.restoreToCount(layer)
    }

    fun apply(src: Bitmap, edit: PhotoEdit): Bitmap {
        val w = src.width
        val h = src.height
        when (edit) {
            is PhotoEdit.Frame -> return frame(src, edit)
            PhotoEdit.Rotate, PhotoEdit.Mirror -> {
                val m = Matrix().apply { if (edit == PhotoEdit.Rotate) setRotate(90f) else setScale(-1f,1f) }
                return Bitmap.createBitmap(src, 0, 0, w, h, m, true)
            }
            is PhotoEdit.Adjust -> if (edit.mask == null) return adjusted(src, edit)
            is PhotoEdit.Heal -> if (edit.mask == null) return healed(src, edit)
            else -> Unit
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val rect = whole(src)
        when (edit) {
            is PhotoEdit.Tone -> tone(canvas, src, edit, isolate = false)
            is PhotoEdit.Adjust -> {
                val filtered = adjusted(src, edit)
                blend(canvas, src, edit.mask, false) { it.drawBitmap(filtered, 0f, 0f, null) }
                filtered.recycle()
            }
            is PhotoEdit.Heal -> {
                val filtered = healed(src, edit)
                blend(canvas, src, edit.mask, false) { it.drawBitmap(filtered, 0f, 0f, null) }
                filtered.recycle()
            }
            else -> {
                canvas.drawBitmap(src, null, rect, null)
                val brush = Paint(Paint.ANTI_ALIAS_FLAG)
                when (edit) {
                    is PhotoEdit.Cut -> edit.mask.draw(canvas, w, h, if (edit.keep) PorterDuff.Mode.DST_IN else PorterDuff.Mode.DST_OUT)
                    is PhotoEdit.Ink -> stroke(canvas, src, edit.mask, edit.opacity, edit.erase) {
                        brush.color = if (edit.erase) Color.WHITE else edit.color or Color.BLACK
                        edit.trace.draw(canvas, w, h, brush)
                    }
                    is PhotoEdit.Clone -> stroke(canvas, src, edit.mask, edit.opacity, false) {
                        val dx = -edit.offset.x * w
                        val dy = -edit.offset.y * h
                        // Outside the shifted source there is nothing to copy: paint nothing there.
                        canvas.save()
                        canvas.clipRect(dx, dy, dx + w, dy + h)
                        brush.color = Color.WHITE
                        edit.trace.draw(canvas, w, h, brush)
                        canvas.drawBitmap(src, dx, dy, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
                        canvas.restore()
                    }
                    is PhotoEdit.Fill -> stroke(canvas, src, edit.mask, edit.opacity, false) {
                        canvas.drawColor(edit.color or Color.BLACK)
                    }
                    else -> Unit
                }
            }
        }
        return result
    }

    private class HealLevel(val w: Int, val h: Int) {
        val r = FloatArray(w*h)
        val g = FloatArray(w*h)
        val b = FloatArray(w*h)
        val weight = FloatArray(w*h)
    }

    /**
     * Removes what lies under the stroke by continuing the surroundings into it: colours are
     * averaged from coarse to fine, relaxed into a smooth blend, then given the grain measured around.
     */
    fun healed(src: Bitmap, edit: PhotoEdit.Heal): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val reach = edit.trace.width * minOf(w, h) / 2f + 2f
        val around = reach + maxOf(12f, reach)
        val points = edit.trace.points
        val left = (points.minOf { it.x } * w - around).toInt().coerceIn(0, w-1)
        val top = (points.minOf { it.y } * h - around).toInt().coerceIn(0, h-1)
        val rw = ((points.maxOf { it.x } * w + around).toInt().coerceIn(left, w-1)) - left + 1
        val rh = ((points.maxOf { it.y } * h + around).toInt().coerceIn(top, h-1)) - top + 1
        // The fill is smooth by nature: solving it on a reduced copy of the area costs nothing visible.
        val scale = minOf(1f, kotlin.math.sqrt(400_000f / (rw.toFloat() * rh)))
        val sw = (rw * scale).roundToInt().coerceAtLeast(1)
        val sh = (rh * scale).roundToInt().coerceAtLeast(1)
        val n = sw * sh
        fun brush() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cover = IntArray(n)
        Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).let { sheet ->
            Canvas(sheet).apply { scale(sw.toFloat()/rw, sh.toFloat()/rh); translate(-left.toFloat(), -top.toFloat()) }
                .let { edit.trace.draw(it, w, h, brush()) }
            sheet.getPixels(cover, 0, sw, 0, 0, sw, sh)
            sheet.recycle()
        }
        val px = IntArray(n)
        Bitmap.createBitmap(src, left, top, rw, rh).let { area ->
            val small = if (sw == rw && sh == rh) area else Bitmap.createScaledBitmap(area, sw, sh, true)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== area && small !== src) small.recycle()
            if (area !== src) area.recycle()
        }
        var holes = 0
        for (i in 0 until n) { cover[i] = cover[i] ushr 24; if (cover[i] != 0) holes++ }
        if (holes == 0 || holes == n) return out

        // Pull: each coarser level averages whatever is known below it.
        val levels = ArrayList<HealLevel>()
        levels.add(HealLevel(sw, sh).also {
            for (i in 0 until n) if (cover[i] == 0) {
                val c = px[i]
                it.r[i] = ((c shr 16) and 255).toFloat(); it.g[i] = ((c shr 8) and 255).toFloat()
                it.b[i] = (c and 255).toFloat(); it.weight[i] = 1f
            }
        })
        while (levels.last().w > 1 || levels.last().h > 1) {
            val fine = levels.last()
            val coarse = HealLevel((fine.w+1)/2, (fine.h+1)/2)
            for (y in 0 until fine.h) for (x in 0 until fine.w) {
                val i = y*fine.w + x
                val weight = fine.weight[i]
                if (weight == 0f) continue
                val j = (y/2)*coarse.w + x/2
                coarse.r[j] += fine.r[i]*weight; coarse.g[j] += fine.g[i]*weight
                coarse.b[j] += fine.b[i]*weight; coarse.weight[j] += weight
            }
            for (j in coarse.weight.indices) {
                val weight = coarse.weight[j]
                if (weight == 0f) continue
                coarse.r[j] /= weight; coarse.g[j] /= weight; coarse.b[j] /= weight
                coarse.weight[j] = minOf(1f, weight)
            }
            levels.add(coarse)
        }
        // Push: what a level lacks comes from the level above, smoothly enlarged.
        for (k in levels.size-2 downTo 0) {
            val fine = levels[k]
            val coarse = levels[k+1]
            for (y in 0 until fine.h) for (x in 0 until fine.w) {
                val i = y*fine.w + x
                val weight = fine.weight[i]
                if (weight >= 1f) continue
                val fx = ((x + 0.5f)/2f - 0.5f).coerceIn(0f, coarse.w - 1f)
                val fy = ((y + 0.5f)/2f - 0.5f).coerceIn(0f, coarse.h - 1f)
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = minOf(x0+1, coarse.w-1)
                val y1 = minOf(y0+1, coarse.h-1)
                val tx = fx - x0
                val ty = fy - y0
                val a = y0*coarse.w + x0
                val b = y0*coarse.w + x1
                val c = y1*coarse.w + x0
                val d = y1*coarse.w + x1
                val wa = (1f-tx)*(1f-ty); val wb = tx*(1f-ty); val wc = (1f-tx)*ty; val wd = tx*ty
                val rest = 1f - weight
                fine.r[i] = fine.r[i]*weight + rest*(coarse.r[a]*wa + coarse.r[b]*wb + coarse.r[c]*wc + coarse.r[d]*wd)
                fine.g[i] = fine.g[i]*weight + rest*(coarse.g[a]*wa + coarse.g[b]*wb + coarse.g[c]*wc + coarse.g[d]*wd)
                fine.b[i] = fine.b[i]*weight + rest*(coarse.b[a]*wa + coarse.b[b]*wb + coarse.b[c]*wc + coarse.b[d]*wd)
                fine.weight[i] = 1f
            }
        }
        val fill = levels[0]
        val hole = IntArray(holes)
        var count = 0
        for (i in 0 until n) if (cover[i] != 0) hole[count++] = i
        // Relax: every filled pixel settles on the mean of its neighbours, which erases the seams.
        for (channel in arrayOf(fill.r, fill.g, fill.b)) repeat(48) {
            for (i in hole) {
                val x = i % sw
                val y = i / sw
                channel[i] = (channel[if (x > 0) i-1 else i] + channel[if (x < sw-1) i+1 else i] +
                    channel[if (y > 0) i-sw else i] + channel[if (y < sh-1) i+sw else i]) / 4f
            }
        }
        // Grain of the untouched surroundings, so the patch does not look polished.
        var energy = 0.0
        var samples = 0
        fun luma(i: Int) = ((px[i] shr 16) and 255)*0.3f + ((px[i] shr 8) and 255)*0.59f + (px[i] and 255)*0.11f
        for (y in 1 until sh-1) for (x in 1 until sw-1) {
            val i = y*sw + x
            if (cover[i] != 0 || cover[i-1] != 0 || cover[i+1] != 0 || cover[i-sw] != 0 || cover[i+sw] != 0) continue
            val d = luma(i) - (luma(i-1) + luma(i+1) + luma(i-sw) + luma(i+sw)) / 4f
            energy += d*d
            samples++
        }
        val grain = if (samples == 0) 0f else kotlin.math.sqrt(energy / samples / 1.25).toFloat().coerceAtMost(8f)
        for (i in hole) px[i] = (px[i] and 0xff000000.toInt()) or (fill.r[i].roundToInt().coerceIn(0, 255) shl 16) or
            (fill.g[i].roundToInt().coerceIn(0, 255) shl 8) or fill.b[i].roundToInt().coerceIn(0, 255)
        val filled = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val area = RectF(left.toFloat(), top.toFloat(), (left + rw).toFloat(), (top + rh).toFloat())
        val layer = canvas.saveLayer(area, null)
        edit.trace.draw(canvas, w, h, brush())
        canvas.drawBitmap(filled, null, area, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        canvas.restoreToCount(layer)
        filled.recycle()
        if (grain <= 0.4f) return out
        // Grain is added at full size, in bands, wherever the stroke covers.
        val random = java.util.Random(points.hashCode().toLong())
        val rows = (262144 / rw).coerceIn(1, rh)
        val band = IntArray(rw * rows)
        val covered = IntArray(rw * rows)
        val sheet = Bitmap.createBitmap(rw, rows, Bitmap.Config.ARGB_8888)
        val drawer = Canvas(sheet)
        var y = 0
        while (y < rh) {
            val count = minOf(rows, rh - y)
            sheet.eraseColor(0)
            drawer.save()
            drawer.translate(-left.toFloat(), -(top + y).toFloat())
            edit.trace.draw(drawer, w, h, brush())
            drawer.restore()
            sheet.getPixels(covered, 0, rw, 0, 0, rw, count)
            out.getPixels(band, 0, rw, left, top + y, rw, count)
            for (i in 0 until rw * count) {
                val amount = covered[i] ushr 24
                if (amount == 0) continue
                val c = band[i]
                val noise = (random.nextGaussian() * grain * amount / 255.0).roundToInt()
                band[i] = (c and 0xff000000.toInt()) or ((((c shr 16) and 255) + noise).coerceIn(0, 255) shl 16) or
                    ((((c shr 8) and 255) + noise).coerceIn(0, 255) shl 8) or ((c and 255) + noise).coerceIn(0, 255)
            }
            out.setPixels(band, 0, rw, left, top + y, rw, count)
            y += count
        }
        sheet.recycle()
        return out
    }

    /** Levels that stretch the darkest and brightest parts of the image to full range, if worth it. */
    fun autoLevels(src: Bitmap, mask: PhotoMask?): PhotoEdit.Adjust? {
        val scale = minOf(1f, 256f / maxOf(src.width, src.height))
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val pixels = IntArray(w*h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val histogram = IntArray(256)
        var count = 0
        for (c in pixels) if (c ushr 24 > 16) {
            histogram[(((c shr 16) and 255)*77 + ((c shr 8) and 255)*150 + (c and 255)*29) shr 8]++
            count++
        }
        if (count == 0) return null
        val clip = count / 200
        var black = 0
        var seen = histogram[0]
        while (seen <= clip && black < 255) seen += histogram[++black]
        var white = 255
        seen = histogram[255]
        while (seen <= clip && white > 0) seen += histogram[--white]
        if (white - black < 24 || (black < 4 && white > 251)) return null
        return PhotoEdit.Adjust(emptyList(), mask, black, white)
    }

    private fun toneCurve(edit: PhotoEdit.Adjust, value: (AdjustChannel) -> Int): IntArray {
        val light = value(AdjustChannel.LIGHT) / 100f
        val contrast = value(AdjustChannel.CONTRAST) / 100f
        val highlights = value(AdjustChannel.HIGHLIGHTS) / 100f
        val shadows = value(AdjustChannel.SHADOWS) / 100f
        val slope = 1f + contrast * if (contrast < 0f) 0.75f else 1f
        val gamma = 2f.pow(-light * 0.9f)
        val range = (edit.white - edit.black).coerceAtLeast(1)
        return IntArray(256) { i ->
            var x = ((i - edit.black).toFloat() / range).coerceIn(0f, 1f)
            // Bumps centred on the bright and dark thirds; black and white points stay put.
            x += 0.88f * (highlights * x * x * (1f - x) + shadows * x * (1f - x) * (1f - x))
            if (light != 0f) x = x.coerceAtLeast(0f).pow(gamma)
            x = (x - 0.5f) * slope + 0.5f
            (x * 255f).roundToInt().coerceIn(0, 255)
        }
    }

    /** Whole-image version of an adjustment; the caller blends it through the selection. */
    fun adjusted(src: Bitmap, edit: PhotoEdit.Adjust): Bitmap {
        val w = src.width
        val h = src.height
        val side = minOf(w, h)
        val value = { c: AdjustChannel -> edit.values.getOrElse(c.ordinal) { 0 } }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = whole(src)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        val blur = value(AdjustChannel.BLUR)
        if (blur > 0) {
            val low = soften(src, blur / 100f * side * 0.03f)
            canvas.drawBitmap(low, null, rect, smooth)
            low.recycle()
        }
        val sharp = if (blur > 0) 0 else value(AdjustChannel.SHARPNESS)
        val curve = toneCurve(edit, value)
        val warmth = value(AdjustChannel.WARMTH) / 100f
        val tint = value(AdjustChannel.TINT) / 100f
        val gainR = ((1f + 0.18f*warmth) * (1f + 0.05f*tint) * 256).roundToInt()
        val gainG = ((1f - 0.14f*tint) * 256).roundToInt()
        val gainB = ((1f - 0.18f*warmth) * (1f + 0.05f*tint) * 256).roundToInt()
        val saturation = ((1f + value(AdjustChannel.SATURATION) / 100f) * 256).roundToInt()
        val colour = gainR != 256 || gainG != 256 || gainB != 256 || saturation != 256
        val curved = curve.indices.any { curve[it] != it }
        if (sharp > 0 || colour || curved) {
            val base = if (blur > 0) out else src
            val low = if (sharp > 0) {
                val small = soften(src, (side * 0.0015f).coerceAtLeast(1f))
                if (small.width == w && small.height == h) small
                else Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawBitmap(small, null, rect, smooth)
                    small.recycle()
                }
            } else null
            val detail = sharp * 384 / 100
            // Bands keep the working arrays small on full-size exports.
            val rows = (262144 / w).coerceIn(1, h)
            val px = IntArray(w * rows)
            val lo = if (low != null) IntArray(w * rows) else null
            var y = 0
            while (y < h) {
                val n = minOf(rows, h - y)
                base.getPixels(px, 0, w, 0, y, w, n)
                if (lo != null) low?.getPixels(lo, 0, w, 0, y, w, n)
                for (i in 0 until w * n) {
                    val c = px[i]
                    val r0 = (c shr 16) and 255
                    val g0 = (c shr 8) and 255
                    val b0 = c and 255
                    var r = curve[r0]
                    var g = curve[g0]
                    var b = curve[b0]
                    if (colour) {
                        r = r * gainR shr 8
                        g = g * gainG shr 8
                        b = b * gainB shr 8
                        val luma = (r*77 + g*150 + b*29) shr 8
                        r = luma + ((r - luma) * saturation shr 8)
                        g = luma + ((g - luma) * saturation shr 8)
                        b = luma + ((b - luma) * saturation shr 8)
                    }
                    if (lo != null) {
                        val l = lo[i]
                        r += (r0 - ((l shr 16) and 255)) * detail shr 8
                        g += (g0 - ((l shr 8) and 255)) * detail shr 8
                        b += (b0 - (l and 255)) * detail shr 8
                    }
                    if (r < 0) r = 0 else if (r > 255) r = 255
                    if (g < 0) g = 0 else if (g > 255) g = 255
                    if (b < 0) b = 0 else if (b > 255) b = 255
                    px[i] = (c and 0xff000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                out.setPixels(px, 0, w, 0, y, w, n)
                y += n
            }
            low?.recycle()
        } else if (blur == 0) canvas.drawBitmap(src, 0f, 0f, null)
        val vignette = value(AdjustChannel.VIGNETTE)
        if (vignette != 0) {
            val rgb = if (vignette > 0) 0 else 0xffffff
            val edge = (abs(vignette) * 2.2f).toInt().coerceAtMost(255) shl 24 or rgb
            canvas.drawRect(rect, Paint(Paint.DITHER_FLAG).apply {
                shader = RadialGradient(w/2f, h/2f, hypot(w/2f, h/2f), intArrayOf(rgb, rgb, edge),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            })
        }
        return out
    }

    /** Blurred copy, possibly smaller than [src]: shrinking first keeps wide blurs cheap. */
    private fun soften(src: Bitmap, radius: Float): Bitmap {
        val factor = maxOf(1f, radius / 3f)
        val tw = (src.width / factor).roundToInt().coerceAtLeast(1)
        val th = (src.height / factor).roundToInt().coerceAtLeast(1)
        var current = src
        fun replace(next: Bitmap) {
            if (current !== src && current !== next) current.recycle()
            current = next
        }
        // Halving averages every pixel; a single big bilinear step would skip most of them.
        while (current.width >= tw*2 && current.height >= th*2) {
            replace(Bitmap.createScaledBitmap(current, (current.width+1)/2, (current.height+1)/2, true))
        }
        if (current.width != tw || current.height != th) replace(Bitmap.createScaledBitmap(current, tw, th, true))
        if (current === src || !current.isMutable || current.config != Bitmap.Config.ARGB_8888) {
            replace(current.copy(Bitmap.Config.ARGB_8888, true))
        }
        boxBlur(current, (radius / factor).roundToInt().coerceAtLeast(1))
        return current
    }

    /** In-place blur of a mutable ARGB_8888 bitmap, on its premultiplied bytes. */
    fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val stride = bitmap.rowBytes
        val a = ByteArray(stride * h)
        val b = ByteArray(a.size)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(a))
        repeat(2) {
            blurPass(a, b, w, h, stride, radius.coerceAtMost(w), true)
            blurPass(b, a, w, h, stride, radius.coerceAtMost(h), false)
        }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(a))
    }

    private fun blurPass(src: ByteArray, dst: ByteArray, w: Int, h: Int, stride: Int, r: Int, horizontal: Boolean) {
        val n = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 4 else stride
        val window = 2*r + 1
        for (line in 0 until lines) for (channel in 0 until 4) {
            val base = (if (horizontal) line*stride else line*4) + channel
            var sum = 0
            for (k in -r..r) sum += src[base + k.coerceIn(0,n-1)*step].toInt() and 255
            for (i in 0 until n) {
                dst[base + i*step] = (sum / window).toByte()
                sum += (src[base + (i+r+1).coerceAtMost(n-1)*step].toInt() and 255) -
                    (src[base + (i-r).coerceAtLeast(0)*step].toInt() and 255)
            }
        }
    }
}
