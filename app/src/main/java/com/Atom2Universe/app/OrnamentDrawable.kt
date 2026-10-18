package com.Atom2Universe.app

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.core.graphics.ColorUtils
import org.xmlpull.v1.XmlPullParser

/** The fill and the two continuous ornamental rails follow the same responsive contour. */
@Keep // Framework XML drawable inflation uses the no-argument constructor.
class OrnamentDrawable() : Drawable(), Drawable.Callback {
    internal val contour = OrnamentContour()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var base: Drawable? = null
    private var density = 1f
    private var ink = 0
    private var underlay = 0
    private var opacity = 255
    private var padding = Rect()
    private var form = 0

    internal constructor(context: Context, fill: Drawable?, variant: Int = 0) : this() {
        configure(context.resources, context.theme, fill, variant)
    }

    private fun configure(resources: Resources, theme: Resources.Theme?, fill: Drawable?, variant: Int) {
        density = resources.displayMetrics.density
        form = Math.floorMod(variant, 4)
        theme?.let(::readPalette)
        fill?.getPadding(padding)
        base = (fill?.constantState?.newDrawable(resources, theme) ?: fill)?.mutate()?.also {
            it.callback = this
        }
        flattenBase()
    }

    private fun readPalette(theme: Resources.Theme) {
        // Generated styleable indices keep the attribute IDs sorted as required by Android.
        val colors = theme.obtainStyledAttributes(R.styleable.OrnamentPalette)
        try {
            ink = ColorUtils.blendARGB(
                colors.getColor(R.styleable.OrnamentPalette_a2uMusicAccent, 0),
                colors.getColor(R.styleable.OrnamentPalette_a2uTextColor, 0), .2f)
            underlay = colors.getColor(R.styleable.OrnamentPalette_a2uSurfaceColor, 0)
        } finally {
            colors.recycle()
        }
    }

    private fun flattenBase() {
        // The old rounded rectangle must not remain visible under the new contour.
        (base as? GradientDrawable)?.apply {
            cornerRadii = null
            cornerRadius = 0f
            setStroke(0, 0)
        }
    }

    // Resources may inflate XML without a theme and apply it afterwards. The wrapper must
    // participate too: otherwise the child keeps unresolved colours (including magenta).
    override fun canApplyTheme() = true

    override fun applyTheme(theme: Resources.Theme) {
        super.applyTheme(theme)
        base?.applyTheme(theme)
        readPalette(theme)
        // Applying the child theme can restore its XML corners and stroke.
        flattenBase()
        base?.getPadding(padding)
        invalidateSelf()
    }

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        val a = theme?.obtainStyledAttributes(attrs, R.styleable.OrnamentDrawable, 0, 0)
            ?: r.obtainAttributes(attrs, R.styleable.OrnamentDrawable)
        try {
            configure(r, theme, a.getDrawable(R.styleable.OrnamentDrawable_android_drawable),
                a.getInt(R.styleable.OrnamentDrawable_ornamentForm, 0))
        } finally {
            a.recycle()
        }
    }

    internal fun setVariant(variant: Int): Boolean {
        val value = Math.floorMod(variant, 4)
        if (form == value) return false
        form = value
        onBoundsChange(bounds)
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        base?.bounds = bounds
        contour.resize(RectF(bounds), density, form)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(contour.outer)
        base?.draw(canvas)
        canvas.restoreToCount(save)
        drawFrame(canvas)
    }

    internal fun drawFrame(canvas: Canvas) {
        if (bounds.isEmpty) return
        // A surface-coloured thread keeps the contour legible over illustrated hub cards too.
        paint.color = underlay
        paint.alpha = opacity
        paint.strokeWidth = 3.2f * density
        canvas.drawPath(contour.outer, paint)
        paint.color = ink
        paint.alpha = opacity
        paint.strokeWidth = 1.35f * density
        canvas.drawPath(contour.outer, paint)
        paint.alpha = (opacity * .65f).toInt()
        paint.strokeWidth = .7f * density
        canvas.drawPath(contour.inner, paint)
    }

    override fun getOutline(outline: Outline) {
        // Concave clipping is handled by Canvas, including on Android 8–10.
        if (Build.VERSION.SDK_INT >= 30 && !contour.outer.isEmpty) outline.setPath(contour.outer)
        else outline.setEmpty()
        outline.alpha = opacity / 255f
    }
    override fun getPadding(out: Rect): Boolean {
        out.set(padding)
        return padding.left != 0 || padding.top != 0 || padding.right != 0 || padding.bottom != 0
    }
    override fun isStateful() = base?.isStateful == true
    override fun onStateChange(state: IntArray) = base?.setState(state) ?: false
    override fun onLevelChange(level: Int) = base?.setLevel(level) ?: false
    override fun setAlpha(alpha: Int) { opacity = alpha; base?.alpha = alpha; invalidateSelf() }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        base?.colorFilter = colorFilter
        invalidateSelf()
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) = scheduleSelf(what, whenMillis)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
