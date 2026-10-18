package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.roundToInt

/**
 * Le repère touché sur la frise d'histoire humaine : sa fiche s'affiche juste en dessous, dans la page, sans fenêtre
 * en plus. Un groupe de repères montre la liste ; toucher un repère de la liste affiche sa fiche à la place.
 * Il n'y a ni « situer sur la frise » (on vient de la frise) ni précédent / suivant.
 */
class HumanLandmarkPanel(
    context: Context,
    private val zoomTo: (List<HumanLandmark>) -> Unit,
    private val selected: (HumanLandmark?) -> Unit
) : LinearLayout(context) {
    private val palette = SciencePalette(context)

    init {
        orientation = VERTICAL
        visibility = GONE
        background = palette.shape(ColorUtils.blendARGB(palette.surface, palette.accent, .08f), 12f)
        setPadding(dp(14), dp(6), dp(6), dp(14))
    }

    /** La fiche d'un repère. */
    fun showEntry(entry: HumanLandmark) {
        reset()
        val head = line()
        head.addView(text(entry.dateLabel(context), 13f, palette.secondary), LayoutParams(0, -2, 1f))
        head.addView(closeButton(), LayoutParams(dp(44), dp(44)))
        addView(head)
        val body = column()
        if (entry.uncertainRange) body.addView(text(context.getString(R.string.ct_history_estimate), 14f, bold = true))
        body.addView(text(context.getString(entry.title), 20f, bold = true), margin(4))
        val metadata = listOfNotNull(entry.science?.let { context.getString(it.label) }) + entry.regions.map { context.getString(it.label) }
        body.addView(text(metadata.joinToString(context.getString(R.string.ct_credit_separator)), 12f, palette.secondary))
        body.addView(text(context.getString(entry.body), 15f, spacing = true), margin(10))
        body.addView(text(context.getString(R.string.ct_precision), 14f, bold = true), margin(12))
        body.addView(text(context.getString(entry.precision), 14f, palette.secondary, spacing = true), margin(2))
        addView(body, LayoutParams(-1, -2).apply { marginEnd = dp(8) })
        visibility = VISIBLE
        selected(entry)
    }

    /** Plusieurs repères trop proches pour être lus séparément : la liste, et de quoi zoomer dessus. */
    fun showCluster(entries: List<HumanLandmark>) {
        reset()
        val head = line()
        head.addView(text(context.getString(R.string.ct_history_cluster, entries.size), 18f, bold = true), LayoutParams(0, -2, 1f))
        head.addView(closeButton(), LayoutParams(dp(44), dp(44)))
        addView(head)
        val list = column()
        list.addView(button(context.getString(R.string.ct_history_zoom_here)) { zoomTo(entries) }, margin(4))
        entries.forEach { entry ->
            list.addView(button(context.getString(R.string.ct_catalog_entry, context.getString(entry.title), entry.dateLabel(context))) {
                showEntry(entry)
                reveal()
            }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, margin(8))
        }
        addView(list, LayoutParams(-1, -2).apply { marginEnd = dp(8) })
        visibility = VISIBLE
        selected(null)
    }

    fun hide() {
        reset()
        visibility = GONE
    }

    /** Fait défiler la page, si besoin, pour que le début de la fiche soit visible sous la frise. */
    fun reveal() {
        doOnLayout { requestRectangleOnScreen(Rect(0, 0, width, minOf(height, dp(120))), false) }
    }

    private fun reset() { removeAllViews() }

    private fun closeButton() = ImageButton(context).apply {
        setImageResource(R.drawable.ic_close)
        imageTintList = ColorStateList.valueOf(palette.text)
        background = null
        contentDescription = context.getString(R.string.ct_close)
        tooltipText = contentDescription
        setOnClickListener { hide(); selected(null) }
    }

    private fun line() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun margin(top: Int) = LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun text(value: String, size: Float, color: Int = palette.text, bold: Boolean = false, spacing: Boolean = false) =
        TextView(context).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (spacing) setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(48)
        setTextColor(palette.text); background = palette.shape(palette.raised)
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
