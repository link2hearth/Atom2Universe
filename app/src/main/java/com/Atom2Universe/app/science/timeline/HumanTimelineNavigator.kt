package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compact controls inside the human timeline's card, sharing its current civil-year window. */
class HumanTimelineNavigator(
    context: Context,
    private val window: HumanTimeWindow,
    private val changeWindow: (HumanTimeWindow.() -> Unit) -> Unit,
    chooseScale: (View) -> Unit
) : LinearLayout(context) {
    private val palette = SciencePalette(context)
    private val range = Button(context).apply {
        isAllCaps = false; textSize = 16f
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.text); setPadding(0, dp(4), dp(4), dp(4))
        setBackgroundResource(selectableBackground())
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
        compoundDrawableTintList = ColorStateList.valueOf(palette.secondary)
        setOnClickListener(chooseScale)
    }
    private val century = Button(context).apply {
        setText(R.string.ct_explorer_century); isAllCaps = false; textSize = 13f
        background = null
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        contentDescription = context.getString(R.string.ct_explorer_century_description)
        setOnClickListener { changeWindow { century() } }
    }
    private val earlier = arrow(R.drawable.ic_chevron_left, R.string.ct_history_earlier) {
        changeWindow { pan(-span) }
    }
    private val later = arrow(R.drawable.ic_chevron_right, R.string.ct_history_later) {
        changeWindow { pan(span) }
    }
    private val firstDate = date(Gravity.START)
    private val lastDate = date(Gravity.END)
    private var scrubMinimum: Double? = null
    private val slider = SeekBar(context).apply {
        max = 10000
        layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding(dp(12), 0, dp(12), 0)
        progressTintList = ColorStateList.valueOf(palette.accent)
        thumbTintList = ColorStateList.valueOf(palette.accent)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val minimum = scrubMinimum ?: window.navigationMin
                val travel = (HumanTimeWindow.MAX - minimum - window.span).coerceAtLeast(0.0)
                changeWindow { set(minimum + progress.toDouble() / bar.max * travel, span) }
            }
            override fun onStartTrackingTouch(bar: SeekBar) {
                // Keep the same scale under the finger when crossing into recent history.
                scrubMinimum = window.navigationMin
                bar.parent.requestDisallowInterceptTouchEvent(true)
                changeWindow { }
            }
            override fun onStopTrackingTouch(bar: SeekBar) {
                scrubMinimum = null
                bar.parent.requestDisallowInterceptTouchEvent(false)
                bind()
            }
        })
    }

    init {
        orientation = VERTICAL; layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding(dp(12), dp(2), dp(12), dp(4))
        addView(row().apply {
            addView(range, LayoutParams(0, -2, 1f))
            addView(century, LayoutParams(-2, -2).apply { marginStart = dp(8) })
        })
        addView(row().apply {
            addView(earlier, LayoutParams(dp(48), dp(48)))
            addView(slider, LayoutParams(0, dp(48), 1f))
            addView(later, LayoutParams(dp(48), dp(48)))
        })
        // Align the context dates with the slider's track, not the surrounding arrow controls.
        addView(row().apply {
            setPadding(dp(60), 0, dp(60), 0)
            addView(firstDate, LayoutParams(0, -2, 1f))
            addView(lastDate, LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        })
        bind()
    }

    fun bind() {
        val visibleRange = context.getString(R.string.ct_range,
            HumanDate(window.start.roundToInt()).label(context), HumanDate(window.end.roundToInt()).label(context))
        if (range.text.toString() != visibleRange) range.text = visibleRange
        val chapter = TimelineChapters.humanWindow(window)
        range.contentDescription = context.getString(R.string.ct_explorer_choose,
            context.getString(R.string.ct_navigation_destination, context.getString(chapter.title), visibleRange))
        val minimum = scrubMinimum ?: window.navigationMin
        val first = HumanDate(minimum.roundToInt()).label(context)
        val last = HumanDate(HumanTimeWindow.MAX.roundToInt()).label(context)
        if (firstDate.text.toString() != first) firstDate.text = first
        if (lastDate.text.toString() != last) lastDate.text = last
        slider.contentDescription = context.getString(R.string.ct_explorer_scrub_range,
            context.getString(R.string.ct_range, first, last), visibleRange)
        ViewCompat.setStateDescription(slider, visibleRange)
        val travel = HumanTimeWindow.MAX - minimum - window.span
        slider.isEnabled = travel > .001
        if (scrubMinimum == null) slider.progress = if (travel > .001)
            ((window.start - minimum) / travel * slider.max).roundToInt().coerceIn(0, slider.max) else 0
        slider.keyProgressIncrement = if (travel > .001)
            (window.span / travel * slider.max).roundToInt().coerceIn(1, slider.max) else 1
        enabled(earlier, window.start > window.navigationMin + .001)
        enabled(later, window.end < HumanTimeWindow.MAX - .001)
        val selected = abs(window.span - HumanTimeWindow.CENTURY) < .001
        if (century.background == null || century.isSelected != selected) {
            century.isSelected = selected
            val surface = if (selected) ColorUtils.blendARGB(palette.surface, palette.accent, .16f) else palette.surface
            century.background = InsetDrawable(palette.shape(surface, 8f), 0, dp(8), 0, dp(8))
            century.setPadding(dp(10), dp(10), dp(10), dp(10))
            century.setTextColor(if (selected) palette.ink(palette.accent, surface) else palette.secondary)
        }
    }

    private fun arrow(drawable: Int, description: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(palette.secondary)
        contentDescription = context.getString(description); tooltipText = contentDescription
        setBackgroundResource(selectableBackground()); setOnClickListener { action() }
    }
    private fun date(alignment: Int) = TextView(context).apply {
        textSize = 14f; setTextColor(palette.secondary); gravity = alignment
        includeFontPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun enabled(view: View, value: Boolean) { view.isEnabled = value; view.alpha = if (value) 1f else .35f }
    private fun selectableBackground(): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
