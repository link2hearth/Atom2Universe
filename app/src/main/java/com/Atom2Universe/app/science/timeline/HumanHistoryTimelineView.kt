package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.abs
import kotlin.math.roundToInt

/** One pannable chart: native accessible targets above a persistent, graduated date axis. */
class HumanHistoryTimelineView(
    context: Context,
    val window: HumanTimeWindow,
    private val changed: () -> Unit,
    private val openEntries: (List<HumanLandmark>) -> Unit
) : FrameLayout(context) {
    private val palette = SciencePalette(context)
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val art = HumanTimelineArt()
    private val scroller = OverScroller(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var flingX = 0
    private var dragging = false
    private var pinched = false
    private var verticalGesture = false
    private var touching = false
    private var axisY = 0f
    private val plotTop get() = dp(8).toFloat()
    private var recordsTop = 0
    private var rowHeight = 0
    private var rowCount = 1
    private val inset get() = dp(12).toFloat()
    private val plotWidth get() = (width - inset * 2).coerceAtLeast(1f)
    // Assign tracks from the complete chronology, never from the current viewport or filter.
    // Disjoint records can share a track; labels that meet when zooming out group on that track.
    private val laneById = run {
        val ends = mutableListOf<Int>()
        HumanHistory.entries.sortedWith(compareBy<HumanLandmark> { it.range.first.year }
            .thenByDescending { it.range.last.year }.thenBy { it.id }).associate { entry ->
            val lane = ends.indexOfFirst { it < entry.range.first.year }.let { if (it < 0) ends.size else it }
            if (lane == ends.size) ends.add(entry.range.last.year) else ends[lane] = entry.range.last.year
            entry.id to lane
        }
    }
    var region: HumanRegion? = null
        set(value) { field = value; refresh() }
    var topic: HistoryTopic = HistoryTopic.ALL
        set(value) { field = value; refresh() }
    var selectedId: String? = null
        set(value) { field = value; refresh() }

    private data class Target(val button: Button, var entries: List<HumanLandmark> = emptyList(),
        val bounds: RectF = RectF(), var anchor: Float = 0f)
    private data class Group(val entries: List<HumanLandmark>, val lane: Int, val anchor: Float,
        val left: Float, val occupiedStart: Float, val occupiedEnd: Float)
    private val targets = linkedMapOf<String, Target>()
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinched = true; dragging = true; parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            window.zoom(detector.scaleFactor.toDouble(), ((detector.focusX - inset) / plotWidth).toDouble())
            refresh(); return true
        }
    })

    init {
        setWillNotDraw(false)
        layoutDirection = LAYOUT_DIRECTION_LTR
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Each record owns its native target, including when it represents a group on its track.
        HumanHistory.entries.forEach { entry ->
            val button = Button(context).apply {
                isAllCaps = false; textSize = 12f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(8), dp(5), dp(8), dp(5)); setTextColor(palette.text)
                background = palette.shape(palette.raised, 6f)
            }
            val target = Target(button)
            button.setOnClickListener { stopMotion(); openEntries(target.entries) }
            targets[entry.id] = target; addView(button)
        }
    }

    fun visibleEntries() = HumanHistory.entries.filter {
        (region == null || region in it.regions) && topic.matches(it) &&
            window.intersects(it.range.first.year, it.range.last.year)
    }

    fun refresh() { requestLayout(); invalidate(); changed() }
    fun stopMotion() { scroller.forceFinished(true); requestLayout() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val available = (w - dp(24)).coerceAtLeast(1)
        val fontScale = resources.configuration.fontScale.coerceAtLeast(1f)
        rowHeight = dp((58 * fontScale).roundToInt())
        val entries = visibleEntries()
        val neededRows = entries.maxOfOrNull { laneById.getValue(it.id) }?.plus(1) ?: 1
        // Only trim unused tracks at the bottom: compacting holes would move surviving records.
        // Defer shrinking until a gesture/fling ends so the chart cannot slip under the finger.
        rowCount = if (touching || !scroller.isFinished) maxOf(rowCount, neededRows) else neededRows
        recordsTop = dp(8) + dp((24 * fontScale).roundToInt())
        axisY = (recordsTop + dp(4) + rowHeight * rowCount).toFloat()
        val h = axisY.roundToInt() + dp((45 * fontScale).roundToInt())
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
        val labelWidth = minOf(dp(158), (available * .58f).roundToInt()).coerceAtLeast(1)
        fun group(records: List<HumanLandmark>, lane: Int): Group {
            val first = maxOf(window.start, records.first().range.first.year.toDouble())
            val last = minOf(window.end, records.last().range.last.year.toDouble())
            val lineStart = (inset + (first - window.start) / window.span * available).toFloat()
            val lineEnd = (inset + (last - window.start) / window.span * available).toFloat()
            val anchor = (lineStart + lineEnd) / 2
            val left = (anchor - labelWidth / 2).coerceIn(inset, maxOf(inset, w - inset - labelWidth))
            return Group(records, lane, anchor, left, minOf(left, lineStart), maxOf(left + labelWidth, lineEnd))
        }
        val groups = entries.groupBy { laneById.getValue(it.id) }.flatMap { (lane, records) ->
            val packed = mutableListOf<Group>()
            for (entry in records.sortedBy { it.range.first.year }) {
                var next = group(listOf(entry), lane)
                // Merging can widen a label to its left: recheck the previous neighbour too.
                while (packed.isNotEmpty() && packed.last().occupiedEnd + dp(8) > next.occupiedStart) {
                    next = group(packed.removeAt(packed.lastIndex).entries + next.entries, lane)
                }
                packed.add(next)
            }
            packed
        }
        val activeIds = groups.mapTo(mutableSetOf()) { it.entries.first().id }
        targets.forEach { (id, target) ->
            if (id !in activeIds) { target.entries = emptyList(); target.button.visibility = GONE }
        }
        groups.forEach { group ->
            val target = targets.getValue(group.entries.first().id)
            target.entries = group.entries; target.button.visibility = VISIBLE
            val entry = group.entries.singleOrNull()
            target.button.text = if (entry != null) context.getString(entry.title)
                else context.getString(R.string.ct_history_cluster, group.entries.size)
            target.button.contentDescription = if (entry != null)
                context.getString(R.string.ct_history_entry_description, context.getString(entry.title), entry.dateLabel(context))
                else context.getString(R.string.ct_history_cluster_description, group.entries.size,
                    HumanDate(group.entries.first().range.first.year).label(context),
                    HumanDate(group.entries.last().range.last.year).label(context))
            target.button.background = palette.shape(if (group.entries.any { it.id == selectedId })
                ColorUtils.blendARGB(palette.raised, palette.accent, .22f) else palette.raised, 6f)
            val top = (recordsTop + group.lane * rowHeight).toFloat()
            target.bounds.set(group.left, top, group.left + labelWidth, top + rowHeight - dp(10))
            target.anchor = group.anchor
            target.button.measure(MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(target.bounds.height().roundToInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        targets.values.filter { it.entries.isNotEmpty() }.forEach {
            val b = it.bounds; it.button.layout(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        }
    }

    private fun x(year: Double) = (inset + (year - window.start) / window.span * plotWidth).toFloat()
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, align: Paint.Align = Paint.Align.LEFT) {
        paint.color = palette.secondary; paint.textAlign = align; canvas.drawText(value, x, y, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return
        paint.style = Paint.Style.FILL; paint.strokeWidth = dp(1).toFloat()
        paint.textSize = 12f * resources.displayMetrics.scaledDensity
        // The dates and scrubber share this card's native header; do not repeat them on the canvas.
        val saved = canvas.save(); canvas.clipRect(inset, plotTop, width - inset, axisY)
        // Low-contrast period colour and code-drawn motifs supply context without more navigation layers.
        val leaves = HumanHistory.periods.filter { TimelineChapters.children(it).isEmpty() }
        leaves.forEach { period ->
            val range = requireNotNull(period.human)
            val left = x(range.first.year.toDouble()).coerceAtLeast(inset)
            val right = x(range.last.year.toDouble()).coerceAtMost(width - inset)
            if (right > left) {
                paint.color = ColorUtils.blendARGB(palette.surface, period.color, .09f)
                canvas.drawRect(left, plotTop, right, axisY, paint)
                paint.color = palette.mark(period.color)
                canvas.drawRect(left, plotTop, right, plotTop + dp(3), paint)
                if (right - left > dp(90)) {
                    val alpha = canvas.saveLayerAlpha(left, axisY - dp(62), right, axisY, 38)
                    art.draw(canvas, RectF(left, axisY - dp(62), right, axisY), period, palette)
                    canvas.restoreToCount(alpha)
                    val caption = TextUtils.ellipsize(context.getString(period.title), paint,
                        right - left - dp(8), TextUtils.TruncateAt.END).toString()
                    text(canvas, caption, left + dp(4), plotTop + dp(7) - paint.fontMetrics.top)
                }
            }
        }
        val labels = (plotWidth / (130 * resources.displayMetrics.scaledDensity)).toInt().coerceAtLeast(2)
        val ticks = window.ticks(labels)
        ticks.forEach { year ->
            paint.color = palette.outline
            canvas.drawLine(x(year.toDouble()), plotTop + dp(3), x(year.toDouble()), axisY, paint)
        }
        paint.color = ColorUtils.setAlphaComponent(palette.outline, 100)
        for (lane in 1 until rowCount) {
            val y = (recordsTop - dp(3) + lane * rowHeight).toFloat()
            canvas.drawLine(inset, y, width - inset, y, paint)
        }
        targets.values.filter { it.entries.isNotEmpty() }.forEach { target ->
            val y = target.bounds.bottom + dp(3)
            // Even a grouped label leaves every date/range drawn on its own permanent track.
            target.entries.forEach { entry ->
                paint.color = palette.mark(entry.regions.first().color)
                val first = x(entry.range.first.year.toDouble()).coerceIn(inset, width - inset)
                val last = x(entry.range.last.year.toDouble()).coerceIn(inset, width - inset)
                canvas.drawLine(first, y, last, y, paint)
                canvas.drawCircle(first, y, dp(2).toFloat(), paint)
                canvas.drawCircle(last, y, dp(2).toFloat(), paint)
                if (entry.uncertainRange) {
                    var mark = first
                    while (mark <= last) { canvas.drawLine(mark, y - dp(3), minOf(mark + dp(3), last), y + dp(3), paint); mark += dp(8) }
                }
                if (entry.range.first.year < window.start) canvas.drawLine(first, y, first + dp(5), y - dp(4), paint)
                if (entry.range.last.year > window.end) canvas.drawLine(last, y, last - dp(5), y - dp(4), paint)
            }
            paint.color = ColorUtils.setAlphaComponent(palette.secondary, 90)
            canvas.drawLine(target.anchor, y + dp(4), target.anchor, axisY, paint)
        }
        canvas.restoreToCount(saved)
        // Dates stay at the base of the chart while the content moves horizontally.
        paint.color = palette.secondary
        canvas.drawLine(inset, axisY, width - inset, axisY, paint)
        paint.textSize = 13f * resources.displayMetrics.scaledDensity
        var lastLabelRight = -1f
        ticks.forEach { year ->
            val px = x(year.toDouble())
            paint.color = palette.secondary; canvas.drawLine(px, axisY, px, axisY + dp(6), paint)
            val date = HumanDate(year).label(context)
            val labelWidth = paint.measureText(date)
            val left = (px - labelWidth / 2).coerceIn(inset, maxOf(inset, width - inset - labelWidth))
            if (left > lastLabelRight + dp(6)) {
                text(canvas, date, left, axisY + dp(10) - paint.fontMetrics.top)
                lastLabelRight = left + labelWidth
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touching = true
            stopMotion(); velocity?.recycle(); velocity = VelocityTracker.obtain()
            downX = event.x; downY = event.y; lastX = event.x
            dragging = false; pinched = false; verticalGesture = false
            // Decide the direction before the surrounding ScrollView can steal a horizontal drag.
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        velocity?.addMovement(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            pinched = true; parent?.requestDisallowInterceptTouchEvent(true)
        }
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !pinched && !verticalGesture) {
            if (!dragging && abs(event.y - downY) > slop && abs(event.y - downY) > abs(event.x - downX)) {
                verticalGesture = true; parent?.requestDisallowInterceptTouchEvent(false)
            }
            if (!dragging && abs(event.x - downX) > slop && abs(event.x - downX) > abs(event.y - downY)) {
                dragging = true; parent?.requestDisallowInterceptTouchEvent(true)
            }
            if (dragging) {
                window.pan((lastX - event.x) / plotWidth * window.span)
                refresh()
            }
            lastX = event.x
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (dragging && !pinched) {
                velocity?.computeCurrentVelocity(1000, ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat())
                val speed = -(velocity?.xVelocity ?: 0f).roundToInt()
                if (abs(speed) > ViewConfiguration.get(context).scaledMinimumFlingVelocity) {
                    flingX = 0; scroller.fling(0, 0, speed, 0, -1000000, 1000000, 0, 0)
                    postInvalidateOnAnimation()
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            velocity?.recycle(); velocity = null; parent?.requestDisallowInterceptTouchEvent(false)
            dragging = false; pinched = false; touching = false
            requestLayout()
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = dragging || pinched
    override fun onTouchEvent(event: MotionEvent): Boolean = true
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val before = window.start
            window.pan((scroller.currX - flingX) / plotWidth * window.span); flingX = scroller.currX
            refresh()
            if (before == window.start) stopMotion() else postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopMotion(); velocity?.recycle(); velocity = null; super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
