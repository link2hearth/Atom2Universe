package com.Atom2Universe.app.science.mycology

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.max
import kotlin.math.min

/** Compose une planche : le champignon à l'échelle, ses repères, la réglette et la sporée. */
object FungusPlate {

    /** Étendue du dessin en cm (largeur, hauteur, avec 0,9 cm sous le sol). */
    private fun extent(look: FungusLook): Pair<Float, Float> {
        if (look.capShape == CapShape.BRACKET) return (look.capDiam + BracketPainter.BARK) * 1.04f to (BracketPainter.totalHeight(look) + 0.9f)
        val widthCm = when (look.capShape) {
            CapShape.FUNNEL -> look.capDiam * 1.12f
            else -> max(look.capDiam * 1.06f, look.bulbW * 1.3f)
        } * (if (look.cluster > 1) 1.55f else 1f) + look.lean * 2f
        val topCm = when (look.capShape) {
            CapShape.FUNNEL -> look.stipeH + look.capRise + look.capDiam * 0.36f
            else -> look.stipeH + look.capRise + look.umbo + 0.5f
        }
        return widthCm to (topCm + 0.9f)
    }

    private fun box(w: Float, h: Float, d: Float, compact: Boolean): RectF {
        val pad = (if (compact) 6f else 12f) * d
        val footer = if (compact) 0f else 44f * d
        return RectF(pad, pad, w - pad, h - pad - footer)
    }

    /** Pixels par cm qui font tenir ce champignon dans la planche (vue de côté ou en coupe). */
    fun fitScale(look: FungusLook, w: Float, h: Float, d: Float, compact: Boolean, mode: PlateMode = PlateMode.SIDE): Float {
        val b = box(w, h, d, compact)
        if (mode == PlateMode.UNDER) return min(b.width(), b.height()) / 2f * 0.9f / (look.capDiam / 2f)
        val (wc, hc) = extent(look)
        return min(b.width() * 0.96f / wc, b.height() * 0.98f / hc)
    }

    /** Dessine la planche et renvoie l'échelle utilisée (px par cm). */
    fun draw(
        canvas: Canvas, text: (Int) -> String, look: FungusLook, mode: PlateMode,
        w: Float, h: Float, d: Float, showMarks: Boolean, compact: Boolean, fixedScale: Float? = null
    ): Float {
        paper(canvas, w, h, d)
        val b = box(w, h, d, compact)
        val hair = max(1f, d * 0.9f)
        var used: Float
        val painter: SpecimenPainter
        val marks: List<Mark>
        when (mode) {
            PlateMode.UNDER -> {
                used = fixedScale ?: fitScale(look, w, h, d, compact, mode)
                val ur = used * look.capDiam / 2f
                painter = SpecimenPainter(canvas, look, 0f, 0f, used, hair)
                painter.under(b.centerX(), b.centerY(), ur)
                marks = look.marksUnder
            }
            else -> {
                val (wc, hc) = extent(look)
                val s = fixedScale ?: fitScale(look, w, h, d, compact)
                used = s
                val top = b.top + (b.height() - hc * s) / 2f
                val oy = top + (hc - 0.9f) * s
                val ox = b.centerX() - look.lean * s / 2f
                painter = SpecimenPainter(canvas, look, ox, oy, s, hair)
                if (mode == PlateMode.SIDE) painter.side() else painter.section()
                marks = if (mode == PlateMode.SIDE) look.marksSide else look.marksSection
            }
        }
        if (!compact) {
            if (showMarks) drawMarks(canvas, text, painter.anchors, marks, b, d)
            footer(canvas, text, look, used, w, h, d)
        }
        return used
    }

    private fun paper(canvas: Canvas, w: Float, h: Float, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 14f * d, 14f * d, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        p.shader = RadialGradient(w * 0.5f, h * 0.42f, max(w, h) * 0.75f,
            intArrayOf(lighten(PlateColors.PAPER, 0.35f), PlateColors.PAPER, PlateColors.PAPER_EDGE), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        canvas.restore()
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, d); p.color = withAlpha(PlateColors.INK, 70)
        canvas.drawRoundRect(RectF(d, d, w - d, h - d), 14f * d, 14f * d, p)
    }

    private fun textPaint(d: Float, sp: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PlateColors.INK
        textSize = sp * d
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawMarks(canvas: Canvas, text: (Int) -> String, anchors: Map<Anchor, PointF>, marks: List<Mark>, b: RectF, d: Float) {
        val tp = textPaint(d, 10.5f)
        val items = marks.mapNotNull { m -> anchors[m.at]?.let { m to it } }
        val midX = b.centerX()
        val gap = 17f * d
        fun place(list: List<Pair<Mark, PointF>>, left: Boolean) {
            var last = Float.NEGATIVE_INFINITY
            for ((m, pt) in list.sortedBy { it.second.y }) {
                val label = text(m.label)
                var ly = pt.y.coerceIn(b.top + 9f * d, b.bottom - 6f * d)
                if (ly < last + gap) ly = last + gap
                last = ly
                val tw = tp.measureText(label)
                val lx = if (left) b.left + 2f * d else b.right - 2f * d - tw
                val pill = RectF(lx - 5f * d, ly - 10.5f * d, lx + tw + 5f * d, ly + 5.5f * d)
                val ex = if (left) pill.right else pill.left
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(PlateColors.INK, 190); strokeWidth = max(1f, d * 0.8f); style = Paint.Style.STROKE
                }
                canvas.drawLine(ex, (pill.top + pill.bottom) / 2f, pt.x, pt.y, line)
                canvas.drawCircle(pt.x, pt.y, 2.6f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PlateColors.INK })
                canvas.drawCircle(pt.x, pt.y, 1.3f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PlateColors.PAPER })
                canvas.drawRoundRect(pill, 6f * d, 6f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(PlateColors.PAPER, 235) })
                canvas.drawRoundRect(pill, 6f * d, 6f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(PlateColors.INK, 90); style = Paint.Style.STROKE; strokeWidth = max(1f, d * 0.7f)
                })
                canvas.drawText(label, lx, ly, tp)
            }
        }
        place(items.filter { it.second.x < midX }, true)
        place(items.filter { it.second.x >= midX }, false)
    }

    private fun footer(canvas: Canvas, text: (Int) -> String, look: FungusLook, s: Float, w: Float, h: Float, d: Float) {
        val y = h - 18f * d
        // réglette : 1, 2, 5, 10 ou 20 cm
        val target = w * 0.22f
        val choices = floatArrayOf(1f, 2f, 5f, 10f, 20f)
        val len = choices.lastOrNull { it * s <= target * 1.25f } ?: choices.first()
        val px = len * s
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PlateColors.INK; strokeWidth = max(1.4f, d * 1.2f); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val x0 = 16f * d
        canvas.drawLine(x0, y, x0 + px, y, ink)
        canvas.drawLine(x0, y - 4f * d, x0, y + 4f * d, ink)
        canvas.drawLine(x0 + px, y - 4f * d, x0 + px, y + 4f * d, ink)
        val tp = textPaint(d, 11f)
        canvas.drawText(text(R.string.myco_scale_cm).format(len.toInt()), x0, y - 8f * d, tp)

        // sporée : moitié noire, moitié blanche, pour que toute couleur se lise
        val size = 30f * d
        val cx = w - 18f * d - size / 2f
        val cy = h - 20f * d
        val card = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        val clip = Path().apply { addRoundRect(card, 7f * d, 7f * d, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        canvas.drawRect(card.left, card.top, card.centerX(), card.bottom, Paint().apply { color = 0xFF262626.toInt() })
        canvas.drawRect(card.centerX(), card.top, card.right, card.bottom, Paint().apply { color = 0xFFF7F7F2.toInt() })
        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.shader = RadialGradient(cx, cy, size * 0.36f, intArrayOf(withAlpha(look.spore, 255), withAlpha(look.spore, 235), withAlpha(look.spore, 120)),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, size * 0.36f, sp)
        canvas.restore()
        canvas.drawRoundRect(card, 7f * d, 7f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(PlateColors.INK, 160); style = Paint.Style.STROKE; strokeWidth = max(1f, d * 0.9f)
        })
        val label = text(R.string.myco_spores)
        val lp = textPaint(d, 11f)
        canvas.drawText(label, card.left - 8f * d - lp.measureText(label), cy + 4f * d, lp)
    }
}

/** Une planche : un champignon, une vue. Le dessin est mis en cache tant que rien ne change. */
class FungusPlateView(context: Context) : View(context) {
    var look: FungusLook? = null
        set(v) { field = v; invalidateCache() }
    var mode: PlateMode = PlateMode.SIDE
        set(v) { field = v; invalidateCache() }
    var showMarks: Boolean = true
        set(v) { field = v; invalidateCache() }
    var compact: Boolean = false
        set(v) { field = v; invalidateCache() }
    /** Échelle commune (px par cm) pour comparer plusieurs champignons côte à côte. */
    var fixedScale: Float? = null
        set(v) { field = v; invalidateCache() }
    /** Les autres champignons de la comparaison : l'échelle est celle qui les fait tous tenir. */
    var sharedScaleWith: List<FungusLook>? = null
        set(v) { field = v; invalidateCache() }

    private var cache: Bitmap? = null
    private val density = resources.displayMetrics.density

    private fun invalidateCache() { cache?.recycle(); cache = null; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { invalidateCache() }

    override fun onDraw(canvas: Canvas) {
        val l = look ?: return
        if (width == 0 || height == 0) return
        val bmp = cache ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val scale = fixedScale ?: sharedScaleWith?.minOfOrNull { FungusPlate.fitScale(it, width.toFloat(), height.toFloat(), density, compact, mode) }
            FungusPlate.draw(Canvas(bitmap), { resources.getString(it) }, l, mode, width.toFloat(), height.toFloat(), density, showMarks, compact, scale)
            cache = bitmap
        }
        canvas.drawBitmap(bmp, 0f, 0f, null)
    }

    override fun onDetachedFromWindow() { cache?.recycle(); cache = null; super.onDetachedFromWindow() }
}
