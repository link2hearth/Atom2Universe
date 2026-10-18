package com.Atom2Universe.app.games.jigsaw

import android.graphics.*
import android.graphics.drawable.Drawable
import com.Atom2Universe.app.games.kit.KitPalette
import kotlin.math.max
import kotlin.math.min

/** Abstract puzzle artwork, never a sample of a playable image. */
class JigsawMysteryDrawable(private val palette: KitPalette, private val mark: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = JigsawGeometry(JigsawSize.MINI, 91).let { geometry ->
        (0 until JigsawSize.MINI.count).map { id -> Path().apply {
            val curves = geometry.piece(id)
            moveTo(curves.first().start.x, curves.first().start.y)
            curves.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
            close()
        } }
    }
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val radius = min(w, h) * .10f
        val panel = RectF(0f, 0f, w, h)
        val clip = Path().apply { addRoundRect(panel, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        paint.shader = LinearGradient(0f, 0f, w, h, palette.raised, palette.accentDim, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(panel, paint)
        canvas.save()
        val scale = max(w / 600f, h / 900f) * 1.10f
        canvas.translate((w - 600f * scale) / 2f, (h - 900f * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.rotate(-9f, 300f, 450f)
        paths.forEachIndexed { id, path ->
            paint.shader = null; paint.style = Paint.Style.FILL
            paint.color = palette.blend(palette.raised, palette.accent, if (id % 3 == 0) .24f else .08f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = palette.withAlpha(palette.text, .12f)
            paint.strokeWidth = 1.1f / scale
            canvas.drawPath(path, paint)
        }
        canvas.restore()
        val badge = min(w, h) * .26f
        paint.style = Paint.Style.FILL; paint.color = palette.withAlpha(palette.surface, .92f)
        canvas.drawCircle(w / 2f, h / 2f, badge, paint)
        paint.color = palette.accent; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = badge * 1.35f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(mark, w / 2f, h / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
