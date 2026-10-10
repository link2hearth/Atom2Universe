package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.roundToInt

/** Human detail chart within the same hierarchy and navigation as the cosmic chapters. */
class HumanHistoryExplorer(
    context: Context,
    val window: HumanTimeWindow,
    initialRegion: HumanRegion?,
    initialTopic: HistoryTopic,
    selectedId: String?,
    private val stateChanged: (HumanRegion?, HistoryTopic, String?) -> Unit,
    chooseScale: (View) -> Unit,
    private val readPeriod: (CosmicPeriod) -> Unit
) : LinearLayout(context) {
    private val palette = SciencePalette(context)
    private val status = TextView(context).apply { textSize = 12f; setTextColor(palette.secondary) }
    val timeline: HumanHistoryTimelineView
    // Le repère touché s'affiche ici, sous la frise, plutôt que dans une fenêtre en plus.
    private val panel = HumanLandmarkPanel(context, { entries -> focus(entries) }, { entry -> timeline.selectedId = entry?.id })
    private val navigation = HumanTimelineNavigator(context, window, ::changeWindow, chooseScale)
    private val filter: Button
    var region = initialRegion
        private set
    var topic = initialTopic
        private set

    init {
        orientation = VERTICAL
        timeline = HumanHistoryTimelineView(context, window, ::update, ::openMark)
        addView(LinearLayout(context).apply {
            orientation = VERTICAL; background = palette.shape(palette.surface, 10f); clipToOutline = true
            addView(navigation, LayoutParams(-1, -2))
            addView(timeline, LayoutParams(-1, -2))
        }, LayoutParams(-1, -2))
        addView(panel, LayoutParams(-1, -2).apply { topMargin = dp(10) })
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
            readPeriod(TimelineChapters.humanWindow(window))
        }, LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        addView(options)
        addView(status, LayoutParams(-1, -2).apply { topMargin = dp(10) })
        // Assign after controls exist because these setters notify the containing screen.
        timeline.region = region; timeline.topic = topic; timeline.selectedId = selectedId
        HumanHistory.entries.firstOrNull { it.id == selectedId }?.let { panel.showEntry(it) }
        update()
    }

    fun focus(period: CosmicPeriod) {
        val range = requireNotNull(period.human)
        timeline.stopMotion(); window.focus(range.first.year.toDouble(), range.last.year.toDouble())
        timeline.selectedId = null
        panel.hide()
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
        panel.hide()
        entries.singleOrNull()?.let { panel.showEntry(it); panel.reveal() }
    }

    private fun openMark(entries: List<HumanLandmark>) {
        val only = entries.singleOrNull()
        if (only != null) panel.showEntry(only) else if (entries.isNotEmpty()) panel.showCluster(entries) else return
        panel.reveal()
    }

    fun zoomIn() { timeline.stopMotion(); window.zoom(2.0); timeline.refresh() }

    fun changeWindow(change: HumanTimeWindow.() -> Unit) {
        timeline.stopMotion()
        window.change()
        panel.hide()
        timeline.selectedId = null
    }

    private fun update() {
        navigation.bind()
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
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
