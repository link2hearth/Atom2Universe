package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.roundToInt

/** Human history has one continuous navigation surface, independent of geological chapter layers. */
class HumanHistoryExplorer(
    context: Context,
    val window: HumanTimeWindow,
    initialRegion: HumanRegion?,
    initialTopic: HistoryTopic,
    selectedId: String?,
    private val stateChanged: (HumanRegion?, HistoryTopic, String?) -> Unit,
    openEntries: (List<HumanLandmark>) -> Unit,
    private val readPeriod: (CosmicPeriod) -> Unit
) : LinearLayout(context) {
    private val palette = SciencePalette(context)
    private val dates = TextView(context).apply { textSize = 13f; setTextColor(palette.secondary) }
    private val status = TextView(context).apply { textSize = 12f; setTextColor(palette.secondary) }
    val timeline: HumanHistoryTimelineView
    private val earlier: ImageButton
    private val later: ImageButton
    private val minus: Button
    private val plus: Button
    private val filter: Button
    var region = initialRegion
        private set
    var topic = initialTopic
        private set

    init {
        orientation = VERTICAL
        val heading = line()
        heading.addView(TextView(context).apply {
            setText(R.string.ct_history_human_title); textSize = 21f; setTextColor(palette.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LayoutParams(0, -2, 1f))
        heading.addView(button(R.string.ct_history_period_menu) { anchor ->
            PopupMenu(context, anchor).apply {
                HumanHistory.periods.forEachIndexed { index, period ->
                    menu.add(0, index, index, context.getString(R.string.ct_catalog_entry,
                        context.getString(period.title), requireNotNull(period.human).label(context)))
                }
                setOnMenuItemClickListener { item -> focus(HumanHistory.periods[item.itemId]); true }
                show()
            }
        }, LayoutParams(-2, dp(48)))
        addView(heading)
        addView(dates, LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) })
        timeline = HumanHistoryTimelineView(context, window, ::update, openEntries)
        addView(timeline, LayoutParams(-1, -2))
        val controls = line()
        earlier = arrow(R.drawable.ic_chevron_left, R.string.ct_history_earlier) { move(-.75) }
        later = arrow(R.drawable.ic_chevron_right, R.string.ct_history_later) { move(.75) }
        minus = button(R.string.ct_history_minus) { zoom(.5) }.apply {
            contentDescription = context.getString(R.string.ct_history_zoom_out); textSize = 22f
        }
        plus = button(R.string.ct_history_plus) { zoom(2.0) }.apply {
            contentDescription = context.getString(R.string.ct_history_zoom_in); textSize = 22f
        }
        controls.addView(earlier, LayoutParams(dp(48), dp(48)))
        controls.addView(minus, LayoutParams(dp(48), dp(48)))
        controls.addView(button(R.string.ct_history_overview) {
            timeline.stopMotion(); window.set(HumanTimeWindow.MIN, HumanTimeWindow.MAX - HumanTimeWindow.MIN); timeline.refresh()
        }, LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        controls.addView(plus, LayoutParams(dp(48), dp(48)))
        controls.addView(later, LayoutParams(dp(48), dp(48)))
        addView(controls, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        addView(TextView(context).apply {
            setText(R.string.ct_history_gesture_hint); textSize = 12f; setTextColor(palette.secondary)
            setPadding(dp(2), dp(8), dp(2), dp(8))
        })
        val options = line()
        filter = button(R.string.ct_history_all_regions) { anchor ->
            PopupMenu(context, anchor).apply {
                HistoryTopic.entries.forEachIndexed { index, candidate ->
                    menu.add(0, index, index, candidate.label).apply {
                        isCheckable = true; isChecked = candidate == topic
                    }
                }
                menu.setGroupCheckable(0, true, true)
                val regions = menu.addSubMenu(2, -1, HistoryTopic.entries.size, R.string.ct_history_region_menu)
                regions.add(1, 0, 0, R.string.ct_history_all_regions).apply {
                    isCheckable = true; isChecked = region == null
                }
                HumanRegion.entries.forEachIndexed { index, candidate ->
                    regions.add(1, index + 1, index + 1, candidate.label).apply {
                        isCheckable = true; isChecked = candidate == region
                    }
                }
                regions.setGroupCheckable(1, true, true)
                setOnMenuItemClickListener { item ->
                    when (item.groupId) {
                        0 -> { topic = HistoryTopic.entries[item.itemId]; timeline.stopMotion(); timeline.topic = topic; true }
                        1 -> { region = HumanRegion.entries.getOrNull(item.itemId - 1); timeline.stopMotion(); timeline.region = region; true }
                        else -> false
                    }
                }
                show()
            }
        }
        options.addView(filter, LayoutParams(0, -2, 1f))
        options.addView(button(R.string.ct_history_read_window) {
            val period = HumanHistory.periods.filter {
                val bounds = requireNotNull(it.human)
                bounds.first.year <= window.center && bounds.last.year >= window.center &&
                    bounds.last.year - bounds.first.year >= window.span * .65
            }.minByOrNull { it.end - it.start } ?: HumanHistory.periods.first()
            readPeriod(period)
        }, LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        addView(options)
        addView(status, LayoutParams(-1, -2).apply { topMargin = dp(10) })
        // Assign after controls exist because these setters notify the containing screen.
        timeline.region = region; timeline.topic = topic; timeline.selectedId = selectedId
        update()
    }

    fun focus(period: CosmicPeriod) {
        val range = requireNotNull(period.human)
        timeline.stopMotion(); window.focus(range.first.year.toDouble(), range.last.year.toDouble())
        timeline.selectedId = null
    }

    fun focus(entries: List<HumanLandmark>) {
        if (entries.isEmpty()) return
        timeline.stopMotion()
        // Locating a result must reveal it, but keep compatible filters when zooming a cluster.
        if (entries.any { region != null && region !in it.regions }) region = null
        if (entries.any { !topic.matches(it) }) topic = HistoryTopic.ALL
        timeline.region = region; timeline.topic = topic
        window.focus(entries.minOf { it.range.first.year }.toDouble(), entries.maxOf { it.range.last.year }.toDouble(), true)
        timeline.selectedId = entries.singleOrNull()?.id
    }

    private fun zoom(factor: Double) { timeline.stopMotion(); window.zoom(factor); timeline.refresh() }
    private fun move(fraction: Double) { timeline.stopMotion(); window.pan(window.span * fraction); timeline.refresh() }

    private fun update() {
        dates.text = context.getString(R.string.ct_range, HumanDate(window.start.roundToInt()).label(context),
            HumanDate(window.end.roundToInt()).label(context))
        earlier.isEnabled = window.start > HumanTimeWindow.MIN
        later.isEnabled = window.end < HumanTimeWindow.MAX
        minus.isEnabled = window.span < HumanTimeWindow.MAX - HumanTimeWindow.MIN
        plus.isEnabled = window.span > HumanTimeWindow.MIN_SPAN
        listOf(earlier, later, minus, plus).forEach { it.alpha = if (it.isEnabled) 1f else .35f }
        filter.text = if (region == null) context.getString(topic.label)
            else context.getString(R.string.ct_history_filter_summary, context.getString(topic.label), context.getString(region!!.label))
        status.setText(if (timeline.visibleEntries().isEmpty()) R.string.ct_history_window_empty else R.string.ct_history_window_note)
        stateChanged(region, topic, timeline.selectedId)
    }

    private fun line() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(label: Int, action: (Button) -> Unit) = Button(context).apply {
        setText(label); isAllCaps = false; textSize = 13f; minHeight = dp(48); minimumWidth = 0; minWidth = 0
        setTextColor(palette.text); background = palette.shape(palette.raised, 8f)
        setPadding(dp(8), dp(6), dp(8), dp(6)); setOnClickListener { action(this) }
    }
    private fun arrow(icon: Int, description: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon); imageTintList = ColorStateList.valueOf(palette.text)
        contentDescription = context.getString(description); tooltipText = contentDescription
        background = palette.shape(palette.raised, 8f); setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
