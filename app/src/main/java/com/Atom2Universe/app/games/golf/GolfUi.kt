package com.Atom2Universe.app.games.golf

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.roundToInt

/** Les briques de l'interface du golf, aux couleurs du thème de l'appli. */
internal class GolfUi(private val context: Context) {
    val palette = SciencePalette(context)
    private val density = context.resources.displayMetrics.density

    fun dp(v: Int) = (v * density).roundToInt()
    fun dpf(v: Float) = v * density

    fun corner(radius: Float) = AppearanceStyle.corner(context, radius)

    fun shape(color: Int, radius: Float = 16f, stroke: Int? = palette.outline, strokeWidth: Int = 1) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = corner(radius)
        if (stroke != null) setStroke(dp(strokeWidth).coerceAtLeast(1), stroke)
    }

    fun text(value: CharSequence, size: Float, color: Int = palette.text, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        includeFontPadding = false
    }

    private fun ripple(radius: Float) = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, 0x30)), null, shape(Color.WHITE, radius, null))

    /** Le bouton principal : plein, couleur d'accent. */
    fun primary(label: String, action: () -> Unit) = text(label, 16f, palette.onAccent, bold = true).apply {
        gravity = Gravity.CENTER
        background = shape(palette.accent, 24f, null)
        foreground = ripple(24f)
        minHeight = dp(50)
        minWidth = dp(120)
        setPadding(dp(22), dp(12), dp(22), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    /** Le bouton secondaire : surface en relief et contour. */
    fun secondary(label: String, action: () -> Unit) = text(label, 15f, palette.text, bold = true).apply {
        gravity = Gravity.CENTER
        background = shape(palette.raised, 24f)
        foreground = ripple(24f)
        minHeight = dp(50)
        minWidth = dp(110)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    /** Pastille d'information posée sur la vue 3D. */
    fun pill() = text("", 16f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(SCENE_PLATE); cornerRadius = dpf(22f) }
        minHeight = dp(44)
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun gap(width: Int = 0, height: Int = 0) = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(width), dp(height)) }

    companion object {
        const val SCENE_PLATE = 0xA6101814.toInt()
    }
}
