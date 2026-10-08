package com.Atom2Universe.app.science

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R

/** Theme colors cached outside simulation draw loops; data colors keep their meaning. */
class SciencePalette(private val context: Context) {
    val isLight = AppearanceStyle.isLight(context)
    val background = AppearanceStyle.color(context, R.attr.a2uBackgroundColor)
    val surface = AppearanceStyle.color(context, R.attr.a2uSurfaceColor)
    val raised = AppearanceStyle.color(context, R.attr.a2uRaisedColor)
    val text = AppearanceStyle.color(context, R.attr.a2uTextColor)
    val secondary = AppearanceStyle.color(context, R.attr.a2uSecondaryTextColor)
    val outline = AppearanceStyle.color(context, R.attr.a2uOutlineColor)
    val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
    val onAccent = contrastingText(accent)
    val grid = ColorUtils.blendARGB(background, secondary, if (isLight) 0.25f else 0.35f)

    fun control(selected: Boolean = false): GradientDrawable = shape(if (selected) accent else raised)

    fun shape(color: Int, radiusDp: Float = 16f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = AppearanceStyle.corner(context, radiusDp)
        setStroke(context.resources.displayMetrics.density.toInt().coerceAtLeast(1), outline)
    }

    /** Text on a plain surface, preserving the hue of scientific/category labels. */
    fun ink(color: Int, on: Int = surface): Int = ensureContrast(color, on, 4.5)

    /** Small colored marks need stronger ink on paper; dark-mode colors stay unchanged. */
    fun mark(color: Int): Int = if (isLight) ensureContrast(color, background, 4.5) else color

    fun categorySurface(color: Int): Int =
        if (isLight) ColorUtils.blendARGB(surface, color, 0.32f) else color

    companion object {
        fun contrastingText(background: Int): Int =
            if (ColorUtils.calculateContrast(Color.BLACK, background) >=
                ColorUtils.calculateContrast(Color.WHITE, background)) Color.BLACK else Color.WHITE

        private fun ensureContrast(color: Int, background: Int, minimum: Double): Int {
            val opaque = ColorUtils.setAlphaComponent(color, 255)
            if (ColorUtils.calculateContrast(opaque, background) >= minimum) return opaque
            val target = contrastingText(background)
            var low = 0f
            var high = 1f
            repeat(12) {
                val mid = (low + high) * 0.5f
                if (ColorUtils.calculateContrast(ColorUtils.blendARGB(opaque, target, mid), background) >= minimum)
                    high = mid else low = mid
            }
            return ColorUtils.blendARGB(opaque, target, high)
        }
    }
}
