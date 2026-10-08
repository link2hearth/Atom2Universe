package com.Atom2Universe.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.annotation.StringRes
import androidx.annotation.StyleRes

/**
 * Appearance is independent of the saved color (including the legacy "classic" color ID).
 * Shapes, elevations and static ornamentation. Brightness, surface tint and effects are independent.
 */
enum class AppAppearance(
    val id: String,
    @param:StyleRes val overlayRes: Int,
    @param:StringRes val labelRes: Int
) {
    CLASSIC("classic", R.style.ThemeOverlay_A2U_Appearance_Classic, R.string.appearance_classic),
    ROUNDED("rounded", R.style.ThemeOverlay_A2U_Appearance_Rounded, R.string.appearance_rounded),
    ILLUMINATED("illuminated", R.style.ThemeOverlay_A2U_Appearance_Illuminated, R.string.appearance_illuminated)
}

enum class AppBrightness(val id: String, @param:StyleRes val overlayRes: Int, @param:StringRes val labelRes: Int) {
    DARK("dark", R.style.ThemeOverlay_A2U_Palette_Dark, R.string.theme_brightness_dark),
    LIGHT("light", R.style.ThemeOverlay_A2U_Palette_Light, R.string.theme_brightness_light)
}

/** Same tokens as XML, for controls constructed in Kotlin. Reads the screen's themed context. */
object AppearanceStyle {
    fun hasOrnaments(context: Context): Boolean = TypedValue().let {
        context.theme.resolveAttribute(R.attr.a2uOrnaments, it, true) && it.data != 0
    }

    fun isLight(context: Context): Boolean = TypedValue().let {
        context.theme.resolveAttribute(R.attr.a2uLightPalette, it, true) && it.data != 0
    }

    private fun scale(context: Context, attr: Int): Float = TypedValue().let {
        if (context.theme.resolveAttribute(attr, it, true)) it.float else 1f
    }

    fun corner(context: Context, roundedDp: Float): Float =
        roundedDp * context.resources.displayMetrics.density * scale(context, R.attr.a2uCornerScale)

    fun elevation(context: Context, roundedDp: Float): Float =
        roundedDp * context.resources.displayMetrics.density * scale(context, R.attr.a2uElevationScale)

    fun hasGradients(context: Context): Boolean = TypedValue().let {
        !context.theme.resolveAttribute(R.attr.a2uSurfaceGradients, it, true) || it.data != 0
    }

    fun color(context: Context, attr: Int): Int =
        context.obtainStyledAttributes(intArrayOf(attr)).let {
            try { it.getColor(0, 0) } finally { it.recycle() }
        }

    fun controlShape(context: Context): Int = TypedValue().let {
        if (context.theme.resolveAttribute(R.attr.a2uControlShape, it, true)) it.data
        else GradientDrawable.OVAL
    }

    fun drawable(context: Context, attr: Int): android.graphics.drawable.Drawable? =
        context.obtainStyledAttributes(intArrayOf(attr)).let {
            try { it.getDrawable(0) } finally { it.recycle() }
        }

    fun screen(context: Context) = drawable(context, R.attr.a2uScreenBackground)
}
