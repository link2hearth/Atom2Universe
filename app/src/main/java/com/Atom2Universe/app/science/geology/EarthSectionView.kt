package com.Atom2Universe.app.science.geology

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);textAlign=Paint.Align.CENTER }
    private val bubblePoints get() = computeAnchors()
    private val names = scene.ids.map { id -> GeologyCatalog.get(id)?.let { context.getString(it.title) }.orEmpty() }
    private val numbers=scene.ids.indices.map { context.getString(R.string.geo_marker_number,it+1) }
    private var labelWidthPixels=0
    private var nameLayouts = emptyList<StaticLayout>()
    private var labelRects = emptyList<RectF>()
    private var scale = 1f
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    var phase = 0f
        set(value) { field = value; invalidate() }
    var stage = .65f
        set(value) { field = value; invalidate() }
    var trueScale = true
        set(value) { field = value; requestLayout(); invalidate() }
    var labels = true
        set(value) { field = value; requestLayout(); accessibility.invalidateRoot(); invalidate() }

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
            val rect = if (labels) labelRects.getOrNull(id) else bubblePoints.getOrNull(id)?.let { p ->
                val r=24*resources.displayMetrics.density/scale
                RectF(p.x-r,p.y-r,p.x+r,p.y+r)
            }
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
        scale=w/1000f
        if(scale<=0f) { setMeasuredDimension(w,0);return }
        if(labelWidthPixels!=w) { layoutLabels(w);labelWidthPixels=w }
        val bottom=if(labels) (labelRects.lastOrNull()?.bottom ?: 700f)+20f else 710f
        setMeasuredDimension(w,resolveSize((bottom*scale).toInt(),heightMeasureSpec))
    }
    private fun computeAnchors():List<PointF> = when(scene) {
        EarthScene.GLOBE -> {
            val radii=if(trueScale) floatArrayOf(.996f,.944f,.72f,.37f,0f) else floatArrayOf(.97f,.865f,.66f,.39f,0f)
            val angles=floatArrayOf(-54f,-28f,8f,57f,0f)
            radii.indices.map { i ->
                val angle=Math.toRadians(angles[i].toDouble())
                PointF(500f+kotlin.math.cos(angle).toFloat()*275*radii[i],365f+kotlin.math.sin(angle).toFloat()*275*radii[i])
            }
        }
        EarthScene.SHELL -> listOf(PointF(160f,343f),PointF(740f,300f),PointF(570f,435f),PointF(850f,480f),PointF(230f,570f))
        EarthScene.RIDGE -> listOf(PointF(500f,266f),PointF(760f,375f),PointF(265f,440f),PointF(155f,365f))
        EarthScene.SUBDUCTION -> listOf(PointF(647f,548f),PointF(438f,367f),PointF(770f,170f),PointF(535f,451f),PointF(885f,310f))
        EarthScene.COLLISION,EarthScene.FOLDS -> listOf(PointF(250f,360f-stage*30),PointF(390f,335f-stage*90),PointF(500f,450f+stage*120),PointF(685f,305f-stage*60),PointF(815f,330f)).take(scene.ids.size)
        EarthScene.TRANSFORM -> listOf(PointF(487f,284f),PointF(548f,402f),PointF(760f,370f))
        EarthScene.HOTSPOT -> listOf(PointF(770f,550f),PointF(615f,247f),PointF(280f,375f))
        EarthScene.FAULTS -> listOf(PointF(365f,330f),PointF(530f,280f+stage*105),PointF(775f,465f))
        EarthScene.LANDSCAPE -> listOf(PointF(580f,490f),PointF(390f,360f+stage*100),PointF(360f,590f),PointF(180f,465f))
        EarthScene.STRATA -> listOf(PointF(820f,205f),PointF(190f,415f),PointF(720f,355f),PointF(200f,505f),PointF(355f,400f))
        EarthScene.ROCK_CYCLE -> listOf(PointF(485f,595f),PointF(210f,465f),PointF(312f,286f),PointF(740f,595f),PointF(600f,425f))
        EarthScene.ARCHIVE -> listOf(PointF(220f,460f),PointF(770f,365f),PointF(790f,255f),PointF(685f,450f))
        EarthScene.AGES -> emptyList()
    }

    private fun layoutLabels(w:Int) {
        textPaint.textSize = 13f * resources.displayMetrics.scaledDensity / scale
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textPaint.color=palette.text
        val columns=if(w/resources.displayMetrics.density<300 || resources.configuration.fontScale>1.35f) 1 else 2
        val cellWidth=(960f-(columns-1)*16f)/columns
        val inset=12f*resources.displayMetrics.density/scale
        val labelWidth=(cellWidth-inset*2).toInt().coerceAtLeast(1)
        nameLayouts = names.mapIndexed { i,name ->
            val title=context.getString(R.string.geo_marker_title,i+1,name)
            StaticLayout.Builder.obtain(title, 0, title.length, textPaint, labelWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        }
        val touchRadius = 24 * resources.displayMetrics.density / scale
        val rects=mutableListOf<RectF>()
        var y=720f
        nameLayouts.chunked(columns).forEach { row ->
            val rowHeight=maxOf(touchRadius*2,row.maxOf { it.height }.toFloat()+inset*2)
            row.indices.forEach { col ->
                val x=20f+col*(cellWidth+16f)
                rects+=RectF(x,y,x+cellWidth,y+rowHeight)
            }
            y+=rowHeight+16f
        }
        labelRects=rects
        accessibility.invalidateRoot()
    }

    // The fixed rock (thousands of Canvas calls per frame) is baked into a bitmap on a background
    // thread; the drifting sky goes under it and the motion over it, every frame. While the
    // stage keeps changing (slider, evolution) the scene is drawn whole, as before.
    private data class BakeKey(val width: Int, val stage: Float, val trueScale: Boolean)
    private val bakeArt = EarthArt()
    private val rockFollowsStage = scene in listOf(EarthScene.COLLISION, EarthScene.FOLDS, EarthScene.TRANSFORM,
        EarthScene.FAULTS, EarthScene.LANDSCAPE, EarthScene.STRATA)
    private var baked: Bitmap? = null
    private var bakedKey: BakeKey? = null
    private var pendingKey: BakeKey? = null
    private var bakeGeneration = 0
    private val bakedDst = RectF()
    private val bakedPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val startBake = Runnable { bake() }

    private fun currentKey() = BakeKey(width, if (rockFollowsStage) stage else 0f, scene == EarthScene.GLOBE && trueScale)

    private fun scheduleBake(key: BakeKey) {
        if (key == pendingKey) return
        pendingKey = key
        removeCallbacks(startBake)
        postDelayed(startBake, BAKE_DELAY_MS)
    }

    private fun bake() {
        val key = pendingKey ?: return
        if (key != currentKey() || width <= 0 || scale <= 0f) { pendingKey = null; invalidate(); return }
        val generation = ++bakeGeneration
        val s = scale
        val w = width
        val h = kotlin.math.ceil(700f * s).toInt().coerceAtLeast(1)
        val target = scene
        BAKER.execute {
            val bitmap = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { null }
                ?: return@execute
            val c = Canvas(bitmap)
            c.scale(s, s); c.clipRect(0f, 0f, 1000f, 700f)
            if (target == EarthScene.GLOBE) c.translate(200f, 0f)
            bakeArt.render(c, target, key.stage, 0f, key.trueScale, ArtLayer.BASE)
            post {
                if (generation != bakeGeneration || !isAttachedToWindow) return@post
                baked = bitmap; bakedKey = key; pendingKey = null
                bakedDst.set(0f, 0f, w / s, h / s)
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(startBake)
        bakeGeneration++
        // No recycle(): the last frame handed to the render thread may still use it.
        baked = null; bakedKey = null; pendingKey = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scale <= 0f) return
        canvas.save(); canvas.scale(scale, scale)
        paint.color = palette.surface
        canvas.drawRoundRect(0f, 0f, 1000f, height / scale, 32f, 32f, paint)
        canvas.save()
        canvas.clipRect(0f,0f,1000f,700f)
        val shift = if(scene==EarthScene.GLOBE) 200f else 0f
        canvas.translate(shift,0f)
        val key = currentKey()
        val rock = baked?.takeIf { bakedKey == key }
        if (rock == null) {
            art.render(canvas, scene, stage, phase, trueScale)
            scheduleBake(key)
        } else {
            art.render(canvas, scene, stage, phase, trueScale, ArtLayer.UNDER)
            canvas.translate(-shift, 0f)
            canvas.drawBitmap(rock, null, bakedDst, bakedPaint)
            canvas.translate(shift, 0f)
            art.render(canvas, scene, stage, phase, trueScale, ArtLayer.TOP)
        }
        canvas.restore()
        bubblePoints.forEachIndexed { index, p ->
            if (labels) {
                val box = labelRects.getOrNull(index) ?: return@forEachIndexed
                // Quiet legend: full-size touch targets without another grid of filled buttons.
                paint.color = palette.outline;paint.strokeWidth=1f
                canvas.drawLine(box.left+12f,box.bottom,box.right-12f,box.bottom,paint)
                if (accessibility.keyboardFocusedVirtualViewId == index || accessibility.accessibilityFocusedVirtualViewId == index) {
                    paint.color = palette.accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                    canvas.drawRoundRect(box, 12f, 12f, paint); paint.style = Paint.Style.FILL
                }
                val inset=12f*resources.displayMetrics.density/scale
                canvas.save(); canvas.translate(box.left + inset, box.centerY()-nameLayouts[index].height/2f)
                nameLayouts[index].draw(canvas); canvas.restore()
            }
            val r=11f*resources.displayMetrics.density/scale
            paint.color=0x24000000;canvas.drawCircle(p.x,p.y+3f,r+4f,paint)
            paint.color=scene.chapter.color;canvas.drawCircle(p.x,p.y,r+2f,paint)
            paint.color=0xff182c32.toInt();canvas.drawCircle(p.x,p.y,r,paint)
            markerPaint.color=0xfff5eee1.toInt();markerPaint.textSize=12f*resources.displayMetrics.density/scale
            canvas.drawText(numbers[index],p.x,p.y-(markerPaint.ascent()+markerPaint.descent())/2,markerPaint)
            if(!labels && (accessibility.keyboardFocusedVirtualViewId==index || accessibility.accessibilityFocusedVirtualViewId==index)) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.color=palette.accent
                canvas.drawCircle(p.x,p.y,r+8f,paint);paint.style=Paint.Style.FILL
            }
        }
        canvas.restore()
    }
    private fun hitTopic(x: Float, y: Float): Int {
        if (scale <= 0f) return -1
        val sx = x / scale; val sy = y / scale
        if (labels) labelRects.indexOfFirst { it.contains(sx, sy) }.takeIf { it >= 0 }?.let { return it }
        // Choose the closest marker when accessible 48dp hit areas overlap on a phone.
        val points=bubblePoints
        val radius=24*resources.displayMetrics.density/scale
        return points.indices.filter { hypot(sx-points[it].x,sy-points[it].y)<=radius }
            .minByOrNull { hypot(sx-points[it].x,sy-points[it].y) } ?: -1
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

    private companion object {
        /** The stage must rest this long before the rock is baked again. */
        const val BAKE_DELAY_MS = 250L
        val BAKER: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "GeologyBake").apply { isDaemon = true }
        }
    }
}
