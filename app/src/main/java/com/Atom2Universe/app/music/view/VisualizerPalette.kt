package com.Atom2Universe.app.music.view

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R

/** Separate ink and colored surfaces: a glow on black needs a defined trace on paper. */
internal class VisualizerPalette(context: Context) {
    val isLight = AppearanceStyle.isLight(context)
    val background = AppearanceStyle.color(context, R.attr.a2uBackgroundColor)
    val surface = AppearanceStyle.color(context, R.attr.a2uSurfaceColor)
    val text = AppearanceStyle.color(context, R.attr.a2uTextColor)
    val secondary = AppearanceStyle.color(context, R.attr.a2uSecondaryTextColor)
    private val scratch = FloatArray(3)

    // Calculate contrast limits once, against the darkest possible light player surface.
    // Rendering then needs only a table lookup, including animated hues and particle colors.
    private val lightValueLimits = if (isLight) {
        val reference = listOf(background, surface,
            AppearanceStyle.color(context, R.attr.a2uPanelStartColor))
            .minBy { ColorUtils.calculateLuminance(it) }
        FloatArray(360) { hue ->
            scratch[0] = hue.toFloat()
            scratch[1] = 0.5f
            var low = 0f
            var high = 1f
            repeat(10) {
                val mid = (low + high) * 0.5f
                scratch[2] = mid
                if (ColorUtils.calculateContrast(Color.HSVToColor(scratch), reference) >= 4.5)
                    low = mid else high = mid
            }
            low
        }
    } else FloatArray(0)

    fun hsv(hsv: FloatArray): Int = hsv(255, hsv)

    fun hsv(alpha: Int, hsv: FloatArray): Int {
        if (!isLight) return Color.HSVToColor(alpha, hsv)
        val hue = ((hsv[0] % 360f) + 360f) % 360f
        scratch[0] = hue
        scratch[1] = hsv[1].coerceIn(0.5f, 1f)
        scratch[2] = hsv[2].coerceIn(0f, 1f) * lightValueLimits[hue.toInt()]
        return Color.HSVToColor(alpha.coerceIn(0, 255), scratch)
    }

    fun ink(color: Int): Int {
        if (!isLight) return color
        Color.colorToHSV(color, scratch)
        return hsv(Color.alpha(color), scratch)
    }

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ink(Color.argb(alpha, red, green, blue))

    /** Opaque mosaics use colored paper instead of turning the entire light screen dark. */
    fun fill(alpha: Int, hsv: FloatArray): Int {
        if (!isLight) return Color.HSVToColor(alpha, hsv)
        val color = ColorUtils.blendARGB(surface, Color.HSVToColor(hsv), 0.5f)
        return ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))
    }

    fun neutral(alpha: Int, darkGray: Int): Int = if (isLight)
        ColorUtils.setAlphaComponent(text, alpha.coerceIn(0, 255))
    else Color.argb(alpha, darkGray, darkGray, darkGray)
}
