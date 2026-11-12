package com.Atom2Universe.app.gallery

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.hypot

internal class PhotoEditorCanvas(context: Context, private val model: PhotoEditorModel,
    private val gesture: (EditTool, Trace) -> Unit) : View(context) {
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var multi = false
    private var lastX = 0f
    private var lastY = 0f
    private var span = 0f
    private var dimensions = 0L
    private val points = ArrayList<PointF>()
    private val paper = Paint(Paint.FILTER_BITMAP_FLAG)
    private val accent = context.galleryColor(R.attr.a2uMidiAccent)
    private val checker = Paint().apply {
        val side = context.dp(8).coerceAtLeast(1)
        val pattern = Bitmap.createBitmap(side*2,side*2,Bitmap.Config.ARGB_8888)
        Canvas(pattern).apply {
            drawColor(0xff888888.toInt())
            val p = Paint().apply { color = 0xffaaaaaa.toInt() }
            drawRect(0f,0f,side.toFloat(),side.toFloat(),p)
            drawRect(side.toFloat(),side.toFloat(),side*2f,side*2f,p)
        }
        shader = BitmapShader(pattern,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
    }
    init { setBackgroundColor(context.galleryColor(R.attr.a2uSurfaceColor)) }

    private fun frame(image: Bitmap): RectF {
        val key = image.width.toLong() shl 32 or image.height.toLong()
        if (dimensions != key) { zoom = 1f; panX = 0f; panY = 0f; dimensions = key }
        val scale = minOf(width.toFloat()/image.width,height.toFloat()/image.height)*zoom
        val w = image.width*scale
        val h = image.height*scale
        val maxX = maxOf(0f,(w-width)/2f)
        val maxY = maxOf(0f,(h-height)/2f)
        panX = panX.coerceIn(-maxX,maxX)
        panY = panY.coerceIn(-maxY,maxY)
        val x = (width-w)/2+panX
        val y = (height-h)/2+panY
        return RectF(x,y,x+w,y+h)
    }

    override fun onDraw(canvas: Canvas) {
        val image = model.preview ?: return
        val box = frame(image)
        canvas.save()
        canvas.clipRect(box)
        canvas.drawRect(box,checker)
        canvas.translate(box.left,box.top)
        canvas.scale(box.width()/image.width, box.height()/image.height)
        val draft = model.draft
        if (draft != null) PhotoRender.tone(canvas,image,draft)
        else canvas.drawBitmap(image,null,RectF(0f,0f,image.width.toFloat(),image.height.toFloat()),paper)
        if (model.tab == EditTab.SELECT || model.tab == EditTab.CUTOUT) {
            model.selection?.draw(canvas,image.width,image.height,PorterDuff.Mode.SRC_OVER,(accent and 0xffffff) or 0x68000000)
        }
        if (points.isNotEmpty()) {
            val stroke = model.tool in listOf(EditTool.ADD,EditTool.SUBTRACT,EditTool.BRUSH,EditTool.ERASER)
            val trace = Trace(points, if (stroke) model.brushSize/100f else 0f, isBox(model.tool))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (model.tool) {
                    EditTool.BRUSH -> model.ink
                    EditTool.ERASER, EditTool.SUBTRACT -> 0xbbe56b62.toInt()
                    else -> accent
                }
                if (!stroke) {
                    style = Paint.Style.STROKE
                    strokeWidth = context.dp(2)*image.width/box.width()
                }
            }
            trace.draw(canvas,image.width,image.height,paint)
        }
        canvas.restore()
    }
    private fun isBox(tool: EditTool) = tool == EditTool.RECT || tool == EditTool.CROP || tool == EditTool.SUBJECT
    private fun point(box: RectF,x: Float,y: Float) =
        PointF(((x-box.left)/box.width()).coerceIn(0f,1f), ((y-box.top)/box.height()).coerceIn(0f,1f))

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val image = model.preview ?: return true
        if (model.busy) { points.clear(); return true }
        val box = frame(image)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multi = false
                points.clear()
                lastX = e.x; lastY = e.y
                if (model.tool != EditTool.PAN && box.contains(e.x,e.y)) points.add(point(box,e.x,e.y))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true
                points.clear()
                if (e.pointerCount == 2) {
                    lastX = (e.getX(0)+e.getX(1))/2
                    lastY = (e.getY(0)+e.getY(1))/2
                    span = hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0))
                }
            }
            MotionEvent.ACTION_MOVE -> if (multi && e.pointerCount == 2) {
                val x = (e.getX(0)+e.getX(1))/2
                val y = (e.getY(0)+e.getY(1))/2
                val distance = hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0))
                val next = (zoom*distance/span.coerceAtLeast(1f)).coerceIn(1f,12f)
                val factor = next/zoom
                panX = x-width/2f-(lastX-width/2f-panX)*factor
                panY = y-height/2f-(lastY-height/2f-panY)*factor
                zoom = next; span = distance; lastX = x; lastY = y
            } else if (!multi) {
                if (model.tool == EditTool.PAN) {
                    panX += e.x-lastX; panY += e.y-lastY
                } else if (points.isNotEmpty() && (abs(e.x-lastX)+abs(e.y-lastY)>1f)) {
                    if (isBox(model.tool) && points.size>1) points.removeAt(1)
                    points.add(point(box,e.x,e.y))
                }
                lastX=e.x; lastY=e.y
            }
            MotionEvent.ACTION_UP -> {
                if (!multi && points.isNotEmpty()) {
                    val end = point(box,e.x,e.y)
                    if (points.last() != end) points.add(end)
                    val trace = Trace(points.toList(), model.brushSize/100f, isBox(model.tool))
                    val r = trace.bounds()
                    if ((!isBox(model.tool) || (r.width()*box.width()>context.dp(4) && r.height()*box.height()>context.dp(4))) &&
                        (model.tool != EditTool.LASSO || points.size >= 3)) gesture(model.tool,trace)
                }
                points.clear()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> points.clear()
        }
        invalidate()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
