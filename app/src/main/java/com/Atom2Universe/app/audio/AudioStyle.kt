package com.Atom2Universe.app.audio

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R

/** Couleurs pour les contrôles construits en Kotlin, identiques aux ressources XML audio. */
object AudioStyle {
    fun wrapContext(context: Context): Context = androidx.appcompat.view.ContextThemeWrapper(
        context, com.Atom2Universe.app.AppThemeManager.getSelectedTheme(context).styleRes
    ).apply { theme.applyStyle(R.style.ThemeOverlay_A2U_Audio, true) }

    fun isAudioContext(context: Context): Boolean = TypedValue().let {
        context.theme.resolveAttribute(R.attr.a2uAudioTheme, it, true) && it.data != 0
    }

    fun styleDialog(dialog: android.app.Dialog) {
        dialog.window?.setBackgroundDrawable(panel(dialog.context))
        dialog.findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.apply {
            background = panel(dialog.context)
            clipToOutline = true
        }
    }

    fun accent(context: Context): Int = TypedValue().let {
        context.theme.resolveAttribute(R.attr.a2uMusicAccent, it, true)
        if (it.resourceId != 0) ContextCompat.getColor(context, it.resourceId) else it.data
    }

    fun background(context: Context) = ContextCompat.getColor(context, R.color.audio_background)
    fun surface(context: Context) = ContextCompat.getColor(context, R.color.audio_surface)
    fun secondaryText(context: Context) = ContextCompat.getColor(context, R.color.audio_text_secondary)
    fun selectedSurface(context: Context) = ColorUtils.blendARGB(surface(context), accent(context), 0.18f)

    fun panel(context: Context, radiusDp: Float = 24f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(ColorUtils.blendARGB(surface(context), accent(context), 0.07f), surface(context))
    ).apply {
        val density = context.resources.displayMetrics.density
        cornerRadius = radiusDp * density
        setStroke(density.toInt().coerceAtLeast(1), ColorUtils.blendARGB(surface(context), accent(context), 0.24f))
    }
}
