package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.roundToInt

/** Stays above the reading area at every scale. Only the nearest useful context is drawn. */
class TimelineNavigator(
    context: Context,
    private val navigate: (String) -> Unit,
    private val chooseScale: (View) -> Unit,
    private val zoomOut: () -> Unit,
    private val zoomIn: (View) -> Unit,
    private val moveWindow: (Double) -> Unit,
    private val showRecent: () -> Unit,
    private val showCentury: () -> Unit,
    private val seekRecent: (Double) -> Unit
) : LinearLayout(context) {
    private val palette = SciencePalette(context)
    private val dates = TimelineDates(context)
    private val title: Button
    private val rangeLabel: TextView
    private val minus: Button
    private val plus: Button
    private val previous: Button
    private val next: Button
    private val neighbours: LinearLayout
    private val humanControls: LinearLayout
    private val earlier: Button
    private val later: Button
    private val century: Button
    private val recentSlider: SeekBar
    private val contextBand: LinearLayout
    private val contextTitle: TextView
    private val firstDate: TextView
    private val lastDate: TextView
    private var chapter = TimelineChapters.universe
    private var surroundings = chapter
    private var first = chapter.start
    private var last = chapter.end
    private var canGoUp = false
    private var visibleWindow: HumanTimeWindow? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strip = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            if (width <= dp(8)) return
            val inset = dp(4).toFloat()
            val available = (width - 2 * inset).coerceAtLeast(1f)
            fun x(age: Double) = inset + ((age - surroundings.start) /
                (surroundings.end - surroundings.start)).coerceIn(0.0, 1.0).toFloat() * available
            val top = dp(4).toFloat()
            val bottom = height - dp(4).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = palette.raised
            canvas.drawRect(inset, top, width - inset, bottom, paint)
            TimelineChapters.children(surroundings).ifEmpty { listOf(surroundings) }.forEach { period ->
                paint.color = ColorUtils.blendARGB(palette.surface, period.color, .35f)
                canvas.drawRect(x(period.start), top, x(period.end), bottom, paint)
                paint.color = palette.outline; paint.strokeWidth = dp(1).toFloat()
                canvas.drawLine(x(period.start), top, x(period.start), bottom, paint)
            }
            // A small outline remains visible even when the true interval is less than one pixel.
            val left = x(first)
            val right = x(last)
            paint.color = ColorUtils.blendARGB(palette.surface, chapter.color, .8f)
            canvas.drawRect(left, top, right, bottom, paint)
            if (chapter.uncertainRange) {
                val saved = canvas.save()
                canvas.clipRect(left, top, right, bottom)
                paint.color = palette.mark(chapter.color); paint.strokeWidth = dp(1).toFloat()
                var stripe = left - (bottom - top)
                while (stripe < right) {
                    canvas.drawLine(stripe, bottom, stripe + bottom - top, top, paint)
                    stripe += dp(8)
                }
                canvas.restoreToCount(saved)
            }
            val grip = maxOf(dp(4).toFloat(), right - left).coerceAtMost(available)
            val gripLeft = ((left + right - grip) / 2).coerceIn(inset, width - inset - grip)
            paint.color = palette.ink(palette.accent)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat()
            canvas.drawRect(gripLeft, top, gripLeft + grip, bottom, paint)
            paint.style = Paint.Style.FILL
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(2), dp(12), dp(6))
        val controls = row()
        minus = button(R.string.ct_history_minus) { zoomOut() }.apply {
            textSize = 24f; contentDescription = context.getString(R.string.ct_history_zoom_out)
        }
        title = button(R.string.ct_change_scale) { chooseScale(it) }.apply {
            textSize = 15f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLines(2); ellipsize = TextUtils.TruncateAt.END
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
            compoundDrawableTintList = ColorStateList.valueOf(palette.secondary)
        }
        plus = button(R.string.ct_history_plus) { zoomIn(it) }.apply {
            textSize = 24f; contentDescription = context.getString(R.string.ct_history_zoom_in)
        }
        controls.addView(minus, LayoutParams(dp(48), -1))
        controls.addView(title, LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        controls.addView(plus, LayoutParams(dp(48), -1))
        addView(controls)
        rangeLabel = label(12f).apply { gravity = Gravity.CENTER; setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
        addView(rangeLabel, LayoutParams(-1, -2).apply { topMargin = dp(3) })

        contextBand = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4))
            background = palette.shape(palette.surface, 8f)
            isFocusable = true; isClickable = true
            setOnClickListener { if (canGoUp) zoomOut() }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        contextTitle = label(11f).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
        firstDate = label(10f).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
        lastDate = label(10f).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.END }
        contextBand.addView(contextTitle)
        contextBand.addView(strip, LayoutParams(-1, dp(22)))
        recentSlider = SeekBar(context).apply {
            max = 10000
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            layoutDirection = LAYOUT_DIRECTION_LTR
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) seekRecent(progress.toDouble() / max)
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        contextBand.addView(recentSlider, LayoutParams(-1, dp(48)))
        contextBand.addView(row().apply {
            layoutDirection = LAYOUT_DIRECTION_LTR
            addView(firstDate, LayoutParams(0, -2, 1f)); addView(lastDate, LayoutParams(0, -2, 1f))
        })
        addView(contextBand, LayoutParams(-1, -2).apply { topMargin = dp(5) })

        neighbours = row()
        previous = button(R.string.ct_previous) { TimelineChapters.neighbours(chapter, visibleWindow).first?.let { navigate(it.id) } }
        next = button(R.string.ct_next) { TimelineChapters.neighbours(chapter, visibleWindow).second?.let { navigate(it.id) } }
        listOf(previous, next).forEachIndexed { index, view ->
            view.textSize = 12f
            view.setLines(2); view.ellipsize = TextUtils.TruncateAt.END
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(if (index == 0) R.drawable.ic_chevron_left else 0,
                0, if (index == 1) R.drawable.ic_chevron_right else 0, 0)
            view.compoundDrawableTintList = ColorStateList.valueOf(palette.secondary)
            neighbours.addView(view, LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(4) })
        }
        addView(neighbours, LayoutParams(-1, -2).apply { topMargin = dp(5) })

        humanControls = row()
        earlier = button(R.string.ct_history_earlier) { moveWindow(-1.0) }
        later = button(R.string.ct_history_later) { moveWindow(1.0) }
        listOf(earlier, later).forEachIndexed { index, view ->
            view.text = null
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (index == 0) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right, 0, 0, 0)
            view.compoundDrawableTintList = ColorStateList.valueOf(palette.text)
            view.contentDescription = context.getString(if (index == 0) R.string.ct_history_earlier else R.string.ct_history_later)
        }
        century = button(R.string.ct_explorer_century) { showCentury() }.apply {
            textSize = 13f; contentDescription = context.getString(R.string.ct_explorer_century_description)
        }
        humanControls.addView(earlier, LayoutParams(dp(48), -1))
        humanControls.addView(button(R.string.ct_explorer_recent_short) { showRecent() }.apply {
            textSize = 12f; setLines(2); ellipsize = TextUtils.TruncateAt.END
            contentDescription = context.getString(R.string.ct_explorer_recent)
        }, LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        humanControls.addView(century, LayoutParams(0, -1, 1f).apply { marginEnd = dp(4) })
        humanControls.addView(later, LayoutParams(dp(48), -1))
        addView(humanControls, LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }

    fun bind(path: List<String>, window: HumanTimeWindow?) {
        visibleWindow = window
        chapter = requireNotNull(TimelineChapters.get(path.last()))
        val wider = TimelineChapters.wider(path, window)
        val recent = window?.isRecent == true
        surroundings = if (recent) TimelineChapters.recentHistory else wider ?: chapter
        first = if (window != null) CosmicTimeline.PRESENT - HumanHistory.END_YEAR + window.start else chapter.start
        last = if (window != null) CosmicTimeline.PRESENT - HumanHistory.END_YEAR + window.end else chapter.end
        val range = if (window != null) context.getString(R.string.ct_range,
            HumanDate(window.start.roundToInt()).label(context), HumanDate(window.end.roundToInt()).label(context))
        else context.getString(R.string.ct_range, dates.edge(chapter, true), dates.edge(chapter, false))
        val heading = context.getString(R.string.ct_navigation_destination, context.getString(chapter.title), range)
        setText(title, context.getString(chapter.title))
        setText(rangeLabel, range)
        title.contentDescription = context.getString(R.string.ct_explorer_choose, heading)
        canGoUp = wider != null
        enabled(minus, canGoUp)
        enabled(plus, TimelineChapters.zoomTargets(chapter).isNotEmpty() ||
            (window != null && window.span > HumanTimeWindow.MIN_SPAN))
        setText(contextTitle, if (recent) context.getString(R.string.ct_explorer_scrub_hint)
            else context.getString(if (canGoUp) R.string.ct_explorer_context else R.string.ct_explorer_overview,
                context.getString(surroundings.title)))
        setText(firstDate, dates.edge(surroundings, true))
        setText(lastDate, dates.edge(surroundings, false))
        contextBand.contentDescription = if (recent) null else context.getString(R.string.ct_explorer_position,
            context.getString(surroundings.title), range)
        contextBand.isClickable = canGoUp && !recent
        contextBand.isFocusable = !recent
        contextBand.importantForAccessibility = if (recent) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        contextBand.tooltipText = if (canGoUp && !recent) context.getString(R.string.ct_history_zoom_out) else null
        strip.visibility = if (recent) GONE else VISIBLE
        recentSlider.visibility = if (recent) VISIBLE else GONE
        neighbours.visibility = if (window == null) VISIBLE else GONE
        humanControls.visibility = if (window != null) VISIBLE else GONE
        if (window != null) {
            enabled(earlier, window.start > window.navigationMin + .001)
            enabled(later, window.end < HumanTimeWindow.MAX - .001)
            val selected = kotlin.math.abs(window.span - HumanTimeWindow.CENTURY) < .001
            if (century.isSelected != selected) {
                century.isSelected = selected
                century.background = palette.control(selected)
                century.setTextColor(if (selected) palette.onAccent else palette.text)
            }
            val travel = HumanTimeWindow.RECENT_SPAN - window.span
            recentSlider.isEnabled = travel > .001
            recentSlider.contentDescription = context.getString(R.string.ct_explorer_scrub_description, range)
            if (recent && travel > .001) {
                if (!recentSlider.isPressed) recentSlider.progress =
                    ((window.start - HumanTimeWindow.RECENT_MIN) / travel * recentSlider.max).roundToInt()
                recentSlider.keyProgressIncrement = (HumanTimeWindow.CENTURY / travel * recentSlider.max).roundToInt().coerceIn(1, recentSlider.max)
            } else recentSlider.progress = 0
        }
        val (before, after) = TimelineChapters.neighbours(chapter, window)
        listOf(previous to before, next to after).forEachIndexed { index, (view, destination) ->
            val direction = context.getString(if (index == 0) R.string.ct_previous else R.string.ct_next)
            val text = if (destination != null) context.getString(R.string.ct_navigation_destination,
                direction, context.getString(destination.title)) else direction
            setText(view, text)
            view.contentDescription = text
            enabled(view, destination != null)
        }
        strip.invalidate()
    }

    private fun enabled(view: View, value: Boolean) { view.isEnabled = value; view.alpha = if (value) 1f else .35f }
    private fun setText(view: TextView, value: String) { if (view.text.toString() != value) view.text = value }
    private fun label(size: Float) = TextView(context).apply {
        textSize = size; setTextColor(palette.secondary)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(res: Int, action: (View) -> Unit) = Button(context).apply {
        setText(res); isAllCaps = false; minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        setTextColor(palette.text); background = palette.shape(palette.raised, 8f)
        setPadding(dp(6), dp(4), dp(6), dp(4)); setOnClickListener(action)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
