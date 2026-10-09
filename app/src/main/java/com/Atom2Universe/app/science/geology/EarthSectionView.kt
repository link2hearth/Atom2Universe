package com.Atom2Universe.app.science.geology

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.hypot

/** Labels and their markers are one action, exposed as virtual buttons to accessibility. */
class EarthSectionView(context: Context, val scene: EarthScene, private val onTopic: (String) -> Unit) : View(context) {
    private val palette = SciencePalette(context)
    private val art = EarthArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePoints by lazy { computeAnchors() }
    private val names = scene.ids.map { id -> GeologyCatalog.get(id)?.let { context.getString(it.title) }.orEmpty() }
    private var nameLayouts = emptyList<StaticLayout>()
    private var labelRects = emptyList<RectF>()
    private var markerRects = emptyList<RectF>()
    private var scale = 1f
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    var phase = 0f
        set(value) { field = value; invalidate() }
    var stage = .65f
        set(value) { field = value; invalidate() }
    var trueScale = true
        set(value) { field = value; invalidate() }
    var labels = true
        set(value) { field = value; accessibility.invalidateRoot(); invalidate() }

    private val accessibility = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = hitTopic(x, y).takeIf { it >= 0 } ?: INVALID_ID
        override fun getVisibleVirtualViews(ids: MutableList<Int>) { ids.addAll(scene.ids.indices) }
        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            node.text = names[id]
            node.contentDescription = context.getString(R.string.geo_bubble, names[id])
            node.className = android.widget.Button::class.java.name
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            val bounds = Rect()
            val rect = if (labels) labelRects.getOrNull(id) else markerRects.getOrNull(id)
            (rect ?: RectF(0f, 0f, 1f, 1f)).let {
                RectF(it.left * scale, it.top * scale, it.right * scale, it.bottom * scale).roundOut(bounds)
            }
            node.setBoundsInParent(bounds)
        }
        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || id !in scene.ids.indices) return false
            onTopic(scene.ids[id]); return true
        }
        override fun onVirtualViewKeyboardFocusChanged(id: Int, focused: Boolean) { invalidate() }
    }
    init {
        isClickable = true
        isFocusable = true
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val extra = if (scene.ids.size > 3 && scene != EarthScene.GLOBE)
            36f * resources.displayMetrics.density * (resources.configuration.fontScale - 1f).coerceAtLeast(0f) else 0f
        setMeasuredDimension(w, resolveSize((w * .82f + extra).toInt(), heightMeasureSpec))
    }
    private fun computeAnchors():List<PointF> = when(scene) {
        EarthScene.GLOBE -> scene.ids.indices.map { PointF(635f,85f+it*145f) }
        EarthScene.SHELL -> listOf(PointF(160f,280f),PointF(740f,235f),PointF(570f,410f),PointF(850f,480f),PointF(230f,570f))
        EarthScene.RIDGE -> listOf(PointF(500f,240f),PointF(760f,305f),PointF(265f,440f),PointF(155f,340f))
        EarthScene.SUBDUCTION -> listOf(PointF(620f,480f),PointF(430f,255f),PointF(770f,170f),PointF(510f,355f),PointF(885f,310f))
        EarthScene.COLLISION,EarthScene.FOLDS -> listOf(PointF(250f,400f),PointF(390f,300f),PointF(500f,605f),PointF(685f,240f),PointF(815f,330f)).take(scene.ids.size)
        EarthScene.TRANSFORM -> listOf(PointF(495f,250f),PointF(495f,430f),PointF(760f,370f))
        EarthScene.HOTSPOT -> listOf(PointF(770f,550f),PointF(645f,255f),PointF(280f,375f))
        EarthScene.FAULTS -> listOf(PointF(365f,385f),PointF(530f,280f),PointF(775f,465f))
        EarthScene.LANDSCAPE -> listOf(PointF(580f,440f),PointF(355f,330f),PointF(360f,590f),PointF(180f,465f))
        EarthScene.STRATA -> listOf(PointF(820f,205f),PointF(190f,415f),PointF(720f,355f),PointF(200f,505f),PointF(420f,390f))
        EarthScene.ROCK_CYCLE -> listOf(PointF(485f,580f),PointF(210f,465f),PointF(275f,290f),PointF(740f,595f),PointF(600f,375f))
        EarthScene.ARCHIVE -> listOf(PointF(220f,460f),PointF(770f,365f),PointF(790f,255f),PointF(685f,450f))
        EarthScene.AGES -> emptyList()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = w / 1000f
        if (scale <= 0f) return
        textPaint.textSize = 12f * resources.displayMetrics.scaledDensity / scale
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val labelWidth = if (scene == EarthScene.GLOBE) 270 else 275
        nameLayouts = names.map { name ->
            StaticLayout.Builder.obtain(name, 0, name.length, textPaint, labelWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
        }
        val touchRadius = 24 * resources.displayMetrics.density / scale
        markerRects = bubblePoints.map { p -> RectF(p.x - touchRadius, p.y - touchRadius, p.x + touchRadius, p.y + touchRadius) }
        labelRects = nameLayouts.mapIndexed { index, layout ->
            val x: Float
            val y: Float
            if (scene == EarthScene.GLOBE) {
                x = 680f; y = bubblePoints[index].y - layout.height / 2f
            } else if (index < 3) {
                x = 35f + index * 330f; y = 28f
            } else {
                x = if (index == 3) 190f else 560f; y = 698f
            }
            RectF(x - 10f, y - 12f, x + labelWidth + 10f, y + maxOf(layout.height.toFloat() + 12f, touchRadius * 2 - 12f))
        }
        accessibility.invalidateRoot()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scale <= 0f) return
        canvas.save(); canvas.scale(scale, scale)
        paint.color = palette.surface
        canvas.drawRoundRect(0f, 0f, 1000f, height / scale, 32f, 32f, paint)
        art.render(canvas, scene, stage, phase, trueScale)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f * resources.displayMetrics.scaledDensity / scale
        textPaint.color = palette.text
        bubblePoints.forEachIndexed { index, p ->
            if (labels) {
                val box = labelRects.getOrNull(index) ?: return@forEachIndexed
                val startX = (box.centerX()).coerceIn(box.left + 20f, box.right - 20f)
                val startY = if (scene == EarthScene.GLOBE) box.centerY() else if (index < 3) box.bottom + 8f else box.top - 8f
                paint.color = palette.secondary; paint.alpha = 100
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                val target = if (scene == EarthScene.GLOBE) globeTargets[index] else p
                val leader = Path().apply {
                    if (scene == EarthScene.GLOBE) { moveTo(box.left - 8f, startY); lineTo(615f, startY) }
                    else { moveTo(startX, startY); lineTo(startX, (startY + target.y) / 2) }
                    lineTo(target.x, target.y)
                }
                canvas.drawPath(leader, paint); paint.alpha = 255; paint.style = Paint.Style.FILL
                paint.color = palette.surface; paint.alpha = 240
                canvas.drawRoundRect(box, 12f, 12f, paint); paint.alpha = 255
                if (accessibility.keyboardFocusedVirtualViewId == index || accessibility.accessibilityFocusedVirtualViewId == index) {
                    paint.color = palette.accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                    canvas.drawRoundRect(box, 12f, 12f, paint); paint.style = Paint.Style.FILL
                }
                canvas.save(); canvas.translate(box.left + 10f, box.top + 12f)
                nameLayouts[index].draw(canvas); canvas.restore()
            }
            val target = if (scene == EarthScene.GLOBE && labels) globeTargets[index] else p
            paint.color = palette.surface; canvas.drawCircle(target.x, target.y, 9f, paint)
            paint.color = palette.accent; canvas.drawCircle(target.x, target.y, 5f, paint)
        }
        canvas.restore()
    }
    private fun hitTopic(x: Float, y: Float): Int {
        if (scale <= 0f) return -1
        val sx = x / scale; val sy = y / scale
        if (labels) labelRects.indexOfFirst { it.contains(sx, sy) }.takeIf { it >= 0 }?.let { return it }
        if (labels && scene == EarthScene.GLOBE) {
            return globeTargets.indexOfFirst { hypot(sx-it.x,sy-it.y) <= 24*resources.displayMetrics.density/scale }
        }
        return markerRects.indexOfFirst { it.contains(sx, sy) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                if (hypot(event.x - downX, event.y - downY) <= slop) {
                    val id = hitTopic(event.x, event.y)
                    if (id >= 0) { performClick(); onTopic(scene.ids[id]) }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }
    override fun dispatchHoverEvent(event: MotionEvent) = accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    override fun dispatchKeyEvent(event: KeyEvent) = accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private val globeTargets = listOf(PointF(462f,145f),PointF(530f,230f),PointF(450f,390f),PointF(370f,430f),PointF(310f,395f))
}
