package com.Atom2Universe.app.effects

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.Atom2Universe.app.AppEffectPack
import com.Atom2Universe.app.AppEffectsSettings
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.OrnamentDrawable
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioStyle

internal data class EffectsPalette(val light: Boolean, val accent: Int, val corner: Float) {
    companion object {
        fun from(context: Context) = EffectsPalette(
            AppearanceStyle.isLight(context), AudioStyle.accent(context), AppearanceStyle.corner(context, 24f))
    }
}

/**
 * Decoration only: wrap backgrounds, and put small Christmas lights on marked banner edges.
 * Never add child views, alter padding/IDs, intercept gestures, or animate the content itself.
 */
internal class EffectsDecoration(private val root: View) : Runnable, AutoCloseable {
    private data class Slot(val view: View, val banner: Boolean, var background: SceneBackground? = null,
                            var garland: SceneDrawable? = null)
    private val slots = mutableListOf(Slot(root, false))
    private var settings = AppEffectsSettings()
    private var palette = EffectsPalette.from(root.context)
    private var active = false
    private var queued = false
    private var closed = false
    private var lastFrame = 0L
    private var seconds = 0f

    init {
        fun visit(view: View) {
            if (view !== root && view.getTag(R.id.app_effects_scene) != null) return
            if (view.getTag(R.id.app_effects_banner) != null) slots += Slot(view, true)
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }

    fun update(settings: AppEffectsSettings, palette: EffectsPalette) {
        if (closed) return
        this.settings = settings
        this.palette = palette
        if (!settings.enabled || settings.pack == null) {
            restore()
            setActive(false)
            return
        }
        slots.forEach { slot ->
            if (slot.background == null || slot.view.background !== slot.background) {
                slot.background?.release()
                val background = SceneBackground(slot.view.background, slot.view.resources.displayMetrics.density,
                    slot.banner)
                slot.background = background
                slot.view.background = background
                background.bindBaseCallback()
            }
            slot.background?.scene?.update(settings, palette, seconds)
            if (slot.banner && settings.pack == AppEffectPack.CHRISTMAS) {
                val garland = slot.garland ?: SceneDrawable(slot.view.resources.displayMetrics.density,
                    banner = true, foreground = true).also {
                    slot.garland = it
                    slot.view.overlay.add(it)
                }
                garland.setBounds(0, 0, slot.view.width, slot.view.height)
                garland.update(settings, palette, seconds)
            } else {
                slot.garland?.let { slot.view.overlay.remove(it) }
                slot.garland = null
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value && !closed && settings.enabled && settings.pack != null
        if (!active || !settings.animations || !ValueAnimator.areAnimatorsEnabled()) {
            root.removeCallbacks(this)
            queued = false
            lastFrame = 0L
            return
        }
        if (!queued && root.isShown) {
            lastFrame = SystemClock.uptimeMillis()
            queued = true
            root.postDelayed(this, 33L)
        }
    }

    override fun run() {
        queued = false
        if (!active || !root.isShown || !settings.animations || !ValueAnimator.areAnimatorsEnabled()) return
        val now = SystemClock.uptimeMillis()
        seconds += ((now - lastFrame).coerceIn(0L, 100L) / 1000f)
        lastFrame = now
        slots.forEach {
            it.background?.scene?.update(settings, palette, seconds)
            it.garland?.update(settings, palette, seconds)
        }
        queued = true
        root.postDelayed(this, 33L)
    }

    private fun restore() {
        slots.forEach { slot ->
            slot.background?.let {
                if (slot.view.background === it) slot.view.background = it.base
                it.release()
            }
            slot.background = null
            slot.garland?.let { slot.view.overlay.remove(it) }
            slot.garland = null
        }
    }

    override fun close() {
        setActive(false)
        restore()
        closed = true
    }
}

/** Forward the original background's state and padding; removing an effect restores it exactly. */
private class SceneBackground(val base: Drawable?, density: Float, banner: Boolean) : Drawable(), Drawable.Callback {
    val scene = SceneDrawable(density, banner)
    init { scene.callback = this }
    fun bindBaseCallback() { base?.callback = this }
    fun release() {
        if (base?.callback === this) base.callback = null
        scene.callback = null
    }
    override fun draw(canvas: Canvas) {
        base?.draw(canvas)
        val save = canvas.save()
        (base as? OrnamentDrawable)?.let { canvas.clipPath(it.contour.outer) }
        scene.draw(canvas)
        canvas.restoreToCount(save)
    }
    override fun onBoundsChange(bounds: Rect) {
        base?.bounds = bounds
        scene.bounds = bounds
    }
    override fun getPadding(padding: Rect) = base?.getPadding(padding) ?: super.getPadding(padding)
    override fun getOutline(outline: Outline) { base?.getOutline(outline) ?: outline.setEmpty() }
    override fun isStateful() = base?.isStateful == true
    override fun onStateChange(state: IntArray) = base?.setState(state) ?: false
    override fun onLevelChange(level: Int) = base?.setLevel(level) ?: false
    override fun setAlpha(alpha: Int) { base?.alpha = alpha; scene.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { base?.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) = scheduleSelf(what, whenMillis)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}

private class SceneDrawable(density: Float, private val banner: Boolean, private val foreground: Boolean = false) : Drawable() {
    private val painter = EffectsPainter(density)
    private var settings = AppEffectsSettings()
    private var palette = EffectsPalette(false, 0, 0f)
    private var seconds = 0f
    fun update(settings: AppEffectsSettings, palette: EffectsPalette, seconds: Float) {
        if (this.settings == settings && this.palette == palette && this.seconds == seconds) return
        this.settings = settings
        this.palette = palette
        this.seconds = seconds
        invalidateSelf()
    }
    override fun draw(canvas: Canvas) {
        if (settings.enabled) settings.pack?.let {
            painter.draw(canvas, bounds, it, palette, seconds, banner, foreground,
                settings.animations && ValueAnimator.areAnimatorsEnabled())
        }
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
