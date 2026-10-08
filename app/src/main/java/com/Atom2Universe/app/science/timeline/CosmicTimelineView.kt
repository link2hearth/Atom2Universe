package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Retained zoom level with labelled, directly selectable subdivisions at their real time positions. */
class CosmicTimelineView(
    context: Context,
    private val chapter: CosmicPeriod,
    private val next: CosmicPeriod?,
    private val openChapter: (String) -> Unit,
    landmarks: List<HumanLandmark> = emptyList(),
    openLandmark: (HumanLandmark) -> Unit = {}
) : FrameLayout(context) {
    private val palette = SciencePalette(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val connector = Path()
    private val box = RectF()
    private val landscape = TimelineLandscape()
    private val current = next == null
    private val subdivisions = TimelineChapters.children(chapter)
    private data class Target(val period: CosmicPeriod, val label: Button,
        val labelBounds: RectF = RectF(), val zone: RectF = RectF(), var lane: Int = -1)
    private val targets = mutableListOf<Target>()
    private val parallel = mutableListOf<Target>()
    private var sceneBottom = 0f
    private var downX = 0f
    private var downY = 0f
    private var pressedTarget: Target? = null
    private var clickedTarget: Target? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val labels = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding(dp(10), dp(8), dp(10), dp(8)); minimumHeight = dp(48)
    }

    init {
        setWillNotDraw(false)
        val dates = TimelineDates(context)
        fun label(value: String, size: Float, alignment: Int) = TextView(context).apply {
            text = value; textSize = size; gravity = alignment or Gravity.CENTER_VERTICAL
            setTextColor(palette.text); includeFontPadding = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        labels.addView(label(dates.edge(chapter, true), 11f, Gravity.LEFT), LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(label(context.getString(chapter.title), if (current) 15f else 13f, Gravity.CENTER).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(5), 0, dp(5), 0)
        }, LinearLayout.LayoutParams(0, -2, 1.8f))
        labels.addView(label(dates.edge(chapter, false), 11f, Gravity.RIGHT), LinearLayout.LayoutParams(0, -2, 1f))
        addView(labels, LayoutParams(-1, -2, Gravity.TOP))
        val range = context.getString(R.string.ct_range, dates.edge(chapter, true), dates.edge(chapter, false))
        val description = context.getString(
            if (chapter.datedAncestorId == null) R.string.ct_band_description else R.string.ct_life_band_description,
            context.getString(chapter.title), range)
        if (!current) {
            contentDescription = context.getString(R.string.ct_band_return, description, context.getString(requireNotNull(next).title))
            labels.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = true; isFocusable = true
            setOnClickListener { openChapter(chapter.id) }
            foreground = context.getDrawable(selectableBackground())
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            // Expose the heading and real native buttons, not an inaccessible canvas-only chart.
            labels.contentDescription = description
            labels.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            ViewCompat.setScreenReaderFocusable(labels, true)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            subdivisions.forEach { targets.add(makeTarget(it)) }
            TimelineChapters.related(chapter).forEach { parallel.add(makeTarget(it)) }
            landmarks.forEach { entry ->
                parallel.add(makeTarget(entry.band(chapter)) { openLandmark(entry) })
            }
        }
    }

    private fun makeTarget(period: CosmicPeriod, open: () -> Unit = { openChapter(period.id) }): Target {
        val button = Button(context).apply {
            setText(period.title); isAllCaps = false; textSize = 12f
            if (period.uncertainRange) text = context.getString(R.string.ct_time_approximate, context.getString(period.title))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER; includeFontPadding = false
            minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setTextColor(palette.ink(period.color, labelSurface(period)))
            setBackgroundResource(selectableBackground())
            contentDescription = context.getString(if (period.id.startsWith("history:"))
                R.string.ct_history_entry_description else R.string.ct_zone_description,
                context.getString(period.title), period.dateLabel(context))
            setOnClickListener { open() }
        }
        addView(button, LayoutParams(-2, -2))
        return Target(period, button)
    }

    override fun getAccessibilityClassName(): CharSequence = if (current) FrameLayout::class.java.name else Button::class.java.name

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        labels.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), unspecified)
        val top = labels.measuredHeight.toFloat()
        if (!current) {
            setMeasuredDimension(w, resolveSize(labels.measuredHeight + dp(4), heightMeasureSpec)); return
        }
        val inset = minOf(dp(4).toFloat(), w / 4f)
        val available = (w - inset * 2).toInt().coerceAtLeast(1)
        val laneEnds = mutableListOf<Float>()
        val laneHeights = mutableListOf<Int>()
        var sceneHeight = dp(84)
        targets.forEach { target ->
            val left = x(target.period.start, w); val right = x(target.period.end, w)
            target.label.measure(MeasureSpec.makeMeasureSpec(minOf(available, dp(190)), MeasureSpec.AT_MOST), unspecified)
            val labelWidth = target.label.measuredWidth
            val inline = right - left >= labelWidth + dp(8)
            val labelLeft = ((left + right - labelWidth) / 2).coerceIn(inset, (w - inset - labelWidth).coerceAtLeast(inset))
            target.labelBounds.set(labelLeft, 0f, labelLeft + labelWidth, 0f)
            target.lane = -1
            if (inline) sceneHeight = maxOf(sceneHeight, target.label.measuredHeight + dp(28))
            else {
                var lane = laneEnds.indexOfFirst { it + dp(6) <= labelLeft }
                if (lane < 0) { lane = laneEnds.size; laneEnds.add(0f); laneHeights.add(0) }
                laneEnds[lane] = labelLeft + labelWidth
                laneHeights[lane] = maxOf(laneHeights[lane], target.label.measuredHeight)
                target.lane = lane
            }
        }
        sceneBottom = top + sceneHeight
        val laneTops = mutableListOf<Float>()
        var bottom = sceneBottom
        laneHeights.forEach { height -> laneTops.add(bottom + dp(4)); bottom += height + dp(4) }
        targets.forEach { target ->
            val labelTop = if (target.lane < 0) sceneBottom - target.label.measuredHeight - dp(5) else laneTops[target.lane]
            target.labelBounds.top = labelTop; target.labelBounds.bottom = labelTop + target.label.measuredHeight
            target.zone.set(x(target.period.start, w), top, x(target.period.end, w), sceneBottom)
        }
        if (parallel.isNotEmpty()) bottom += dp(8)
        parallel.forEach { target ->
            val left = x(target.period.start, w); val right = x(target.period.end, w)
            val labelWidth = if (target.period.id.startsWith("history:")) minOf(available, dp(280))
                else (right - left - dp(8)).toInt().coerceIn(minOf(dp(96), available), available)
            target.label.measure(MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.AT_MOST), unspecified)
            val labelLeft = ((left + right - target.label.measuredWidth) / 2).coerceIn(inset,
                (w - inset - target.label.measuredWidth).coerceAtLeast(inset))
            val rowHeight = maxOf(dp(48), target.label.measuredHeight + dp(4))
            target.zone.set(left, bottom, right, bottom + rowHeight)
            val labelTop = bottom + (rowHeight - target.label.measuredHeight) / 2f
            target.labelBounds.set(labelLeft, labelTop, labelLeft + target.label.measuredWidth, labelTop + target.label.measuredHeight)
            bottom += rowHeight + dp(4)
        }
        setMeasuredDimension(w, resolveSize(ceil(bottom + dp(4)).toInt(), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        labels.layout(0, 0, measuredWidth, labels.measuredHeight)
        (targets + parallel).forEach { target ->
            val bounds = target.labelBounds
            target.label.layout(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        box.set(0f, 0f, w, h)
        clip.reset(); clip.addRoundRect(box, dp(8).toFloat(), dp(8).toFloat(), Path.Direction.CW)
        val saved = canvas.save(); canvas.clipPath(clip)
        paint.style = Paint.Style.FILL; paint.color = palette.surface; canvas.drawRect(box, paint)
        val entries = subdivisions.ifEmpty { listOf(chapter) }
        val top = if (current) labels.measuredHeight.toFloat() else 0f
        val end = if (current) sceneBottom else h
        if (current && entries.sumOf { it.end - it.start } < (chapter.end - chapter.start) * .99) {
            landscape.draw(canvas, RectF(0f, top, w, end), chapter, palette)
        }
        entries.forEach { child ->
            val left = x(child.start); val right = x(child.end)
            paint.color = ColorUtils.blendARGB(palette.surface, child.color, if (current) .24f else .14f)
            canvas.drawRect(left, top, right, end, paint)
            if (current && right - left >= dp(2)) landscape.draw(canvas, RectF(left, top, right, end - dp(4)), child, palette)
            paint.color = palette.mark(child.color)
            canvas.drawRect(left, end - dp(4), right, end, paint)
            if (current) {
                paint.strokeWidth = dp(1).toFloat(); paint.color = palette.outline
                canvas.drawLine(left, top, left, end, paint)
            }
        }
        targets.forEach { target ->
            if (target.lane >= 0) {
                paint.color = palette.mark(target.period.color); paint.strokeWidth = dp(1).toFloat(); paint.style = Paint.Style.STROKE
                val anchor = target.zone.centerX().coerceIn(1f, maxOf(1f, w - 1f))
                connector.reset(); connector.moveTo(anchor, sceneBottom - dp(4))
                connector.lineTo(anchor, target.labelBounds.top - dp(2))
                connector.lineTo(target.labelBounds.centerX(), target.labelBounds.top)
                canvas.drawPath(connector, paint); paint.style = Paint.Style.FILL
                canvas.drawCircle(anchor, sceneBottom - dp(2), dp(2).toFloat(), paint)
            }
        }
        targets.forEach { target ->
            paint.color = labelSurface(target.period)
            canvas.drawRoundRect(target.labelBounds, dp(4).toFloat(), dp(4).toFloat(), paint)
        }
        parallel.forEach { target ->
            val history = target.period.id.startsWith("history:")
            paint.color = palette.raised
            canvas.drawRect(0f, target.zone.top, w, target.zone.bottom, paint)
            paint.color = labelSurface(target.period); canvas.drawRect(target.zone, paint)
            if (target.labelBounds.left < target.zone.left || history) {
                paint.color = if (history) palette.raised else labelSurface(target.period)
                canvas.drawRoundRect(target.labelBounds, dp(4).toFloat(), dp(4).toFloat(), paint)
            }
            paint.color = palette.mark(target.period.color)
            canvas.drawRect(target.zone.left, target.zone.bottom - dp(3), target.zone.right, target.zone.bottom, paint)
            paint.strokeWidth = dp(1).toFloat()
            canvas.drawLine(target.zone.left, if (history) target.zone.bottom - dp(8) else target.zone.top,
                target.zone.left, target.zone.bottom, paint)
            if (target.period.start == target.period.end) {
                canvas.drawCircle(target.zone.left, target.zone.bottom - dp(3), dp(3).toFloat(), paint)
            }
            if (target.period.uncertainRange) {
                paint.color = palette.mark(target.period.color)
                var stripe = target.zone.left
                while (stripe < target.zone.right) {
                    canvas.drawLine(stripe, target.zone.bottom - dp(3), minOf(stripe + dp(4), target.zone.right),
                        target.zone.bottom - dp(8), paint)
                    stripe += dp(8)
                }
            }
            if (history) {
                // Small edge marks indicate that a record continues beyond this reading window.
                val y = target.zone.bottom - dp(3)
                if (target.period.start < chapter.start) {
                    canvas.drawLine(dp(5).toFloat(), y - dp(4), 0f, y, paint)
                    canvas.drawLine(dp(5).toFloat(), y + dp(2), 0f, y, paint)
                }
                if (target.period.end > chapter.end) {
                    canvas.drawLine(w - dp(5), y - dp(4), w, y, paint)
                    canvas.drawLine(w - dp(5), y + dp(2), w, y, paint)
                }
            }
        }
        pressedTarget?.let { target ->
            paint.color = ColorUtils.setAlphaComponent(palette.accent, 55); canvas.drawRect(target.zone, paint)
        }
        if (next != null) {
            val left = x(next.start); val right = x(next.end)
            paint.color = ColorUtils.blendARGB(palette.surface, next.color, if (palette.isLight) .4f else .3f)
            canvas.drawRect(left, 0f, right, h, paint)
            if (next.uncertainRange) {
                // A dating uncertainty is visually distinct from a period's duration.
                val rangeClip = canvas.save()
                canvas.clipRect(left, 0f, right, h)
                paint.color = ColorUtils.setAlphaComponent(palette.mark(next.color), 60)
                paint.strokeWidth = dp(1).toFloat()
                var stripe = left - h
                while (stripe < right) {
                    canvas.drawLine(stripe, h, stripe + h, 0f, paint)
                    stripe += dp(10)
                }
                canvas.restoreToCount(rangeClip)
            }
            paint.color = palette.mark(next.color); paint.strokeWidth = dp(2).toFloat()
            val y = h - dp(2)
            canvas.drawLine(left, y, right, y, paint)
            canvas.drawLine(left.coerceAtLeast(1f), h - dp(9), left.coerceAtLeast(1f), y, paint)
            canvas.drawLine(right.coerceAtMost(w - 1f), h - dp(9), right.coerceAtMost(w - 1f), y, paint)
        }
        paint.color = palette.mark(chapter.color)
        canvas.drawRect(0f, 0f, w, dp(2).toFloat(), paint)
        canvas.restoreToCount(saved)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!current) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedTarget = (targets + parallel).firstOrNull { it.zone.contains(event.x, event.y) } ?: return false
                downX = event.x; downY = event.y; invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    pressedTarget = null; invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                clickedTarget = pressedTarget?.takeIf { it.zone.contains(event.x, event.y) }
                pressedTarget = null; invalidate()
                if (clickedTarget != null) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressedTarget = null; clickedTarget = null; invalidate(); return true }
        }
        return false
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        val target = clickedTarget ?: return handled
        clickedTarget = null; target.label.performClick(); return true
    }

    private fun labelSurface(period: CosmicPeriod) = ColorUtils.blendARGB(palette.surface, period.color, if (palette.isLight) .2f else .17f)
    private fun selectableBackground(): Int {
        val attr = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, attr, true)
        return attr.resourceId
    }
    private fun x(age: Double, chartWidth: Int = width) = ((age - chapter.start) / (chapter.end - chapter.start) * chartWidth).toFloat().coerceIn(0f, chartWidth.toFloat())
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
