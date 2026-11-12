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
    // Crop frame being dragged: which sides follow the finger (-1 left/top, 1 right/bottom, 0 none).
    private var grabbed = false
    private var grabX = 0
    private var grabY = 0
    private val grabStart = RectF()
    private var downX = 0f
    private var downY = 0f
    private val paper = Paint(Paint.FILTER_BITMAP_FLAG)
    private val accent = context.galleryColor(R.attr.a2uMidiAccent)
    private val shade = Paint().apply { color = 0x99000000.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
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

    private fun fit(image: Bitmap, zoom: Float, panX: Float, panY: Float): RectF {
        val scale = minOf(width.toFloat()/image.width,height.toFloat()/image.height)*zoom
        val w = image.width*scale
        val h = image.height*scale
        val x = (width-w)/2+panX
        val y = (height-h)/2+panY
        return RectF(x,y,x+w,y+h)
    }
    private fun frame(image: Bitmap): RectF {
        val key = image.width.toLong() shl 32 or image.height.toLong()
        if (dimensions != key) { zoom = 1f; panX = 0f; panY = 0f; dimensions = key }
        val box = fit(image,zoom,0f,0f)
        val maxX = maxOf(0f,(box.width()-width)/2f)
        val maxY = maxOf(0f,(box.height()-height)/2f)
        panX = panX.coerceIn(-maxX,maxX)
        panY = panY.coerceIn(-maxY,maxY)
        box.offset(panX,panY)
        return box
    }
    private fun strokes(tool: EditTool) = tool == EditTool.ADD || tool == EditTool.SUBTRACT || paints(tool)
    private fun paints(tool: EditTool) = tool == EditTool.BRUSH || tool == EditTool.ERASER || tool == EditTool.CLONE
    private fun isBox(tool: EditTool) = tool == EditTool.RECT || tool == EditTool.OVAL || tool == EditTool.SUBJECT
    private fun trace(points: List<PointF>): Trace {
        val tool = model.tool
        return Trace(points, if (strokes(tool)) model.brushSize/100f else 0f, tool == EditTool.RECT || tool == EditTool.SUBJECT,
            tool == EditTool.OVAL, if (paints(tool)) model.softness/100f else 0f)
    }

    override fun onDraw(canvas: Canvas) {
        val image = model.preview ?: return
        val original = model.original
        if (model.compare && original != null) {
            val box = fit(original,1f,0f,0f)
            canvas.drawRect(box,checker)
            canvas.drawBitmap(original,null,box,paper)
            return
        }
        val box = frame(image)
        val whole = RectF(0f,0f,image.width.toFloat(),image.height.toFloat())
        canvas.save()
        canvas.clipRect(box)
        canvas.drawRect(box,checker)
        canvas.translate(box.left,box.top)
        canvas.scale(box.width()/image.width, box.height()/image.height)
        when (val draft = model.draft) {
            is PhotoEdit.Tone -> PhotoRender.tone(canvas,image,draft)
            is PhotoEdit.Adjust -> canvas.drawBitmap(model.draftImage ?: image,null,whole,paper)
            is PhotoEdit.Frame -> {
                canvas.save()
                canvas.concat(PhotoRender.straighten(whole.right,whole.bottom,draft.angle))
                canvas.drawBitmap(image,null,whole,paper)
                canvas.restore()
            }
            else -> canvas.drawBitmap(image,null,whole,paper)
        }
        if (model.tab == EditTab.SELECT || model.tab == EditTab.CUTOUT) {
            model.selection?.draw(canvas,image.width,image.height,PorterDuff.Mode.SRC_OVER,(accent and 0xffffff) or 0x68000000)
        }
        if (points.isNotEmpty()) {
            val tool = model.tool
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (tool) {
                    EditTool.BRUSH -> (model.ink and 0xffffff) or ((model.opacity*255/100) shl 24)
                    EditTool.ERASER, EditTool.SUBTRACT -> 0xbbe56b62.toInt()
                    EditTool.CLONE -> (accent and 0xffffff) or 0x68000000
                    else -> accent
                }
                if (!strokes(tool)) {
                    style = Paint.Style.STROKE
                    strokeWidth = context.dp(2)*image.width/box.width()
                }
            }
            trace(points).draw(canvas,image.width,image.height,paint)
        }
        canvas.restore()
        if (model.tool == EditTool.CROP) drawCrop(canvas,box)
        if (model.tool == EditTool.CLONE) drawCloneSource(canvas,box)
    }

    private fun cropBox(box: RectF) = model.crop.let {
        RectF(box.left+it.left*box.width(), box.top+it.top*box.height(),
            box.left+it.right*box.width(), box.top+it.bottom*box.height())
    }
    private fun drawCrop(canvas: Canvas, box: RectF) {
        val r = cropBox(box)
        canvas.drawRect(box.left,box.top,box.right,r.top,shade)
        canvas.drawRect(box.left,r.bottom,box.right,box.bottom,shade)
        canvas.drawRect(box.left,r.top,r.left,r.bottom,shade)
        canvas.drawRect(r.right,r.top,box.right,r.bottom,shade)
        line.strokeWidth = context.dp(1).toFloat()
        line.alpha = 150
        for (i in 1..2) {
            val x = r.left+r.width()*i/3
            val y = r.top+r.height()*i/3
            canvas.drawLine(x,r.top,x,r.bottom,line)
            canvas.drawLine(r.left,y,r.right,y,line)
        }
        line.alpha = 255
        canvas.drawRect(r,line)
        // Corner brackets mark where to grab.
        line.strokeWidth = context.dp(4).toFloat()
        val arm = minOf(context.dp(22).toFloat(), r.width()/3, r.height()/3)
        for (sx in intArrayOf(-1,1)) for (sy in intArrayOf(-1,1)) {
            val x = if (sx < 0) r.left else r.right
            val y = if (sy < 0) r.top else r.bottom
            canvas.drawLine(x,y,x-sx*arm,y,line)
            canvas.drawLine(x,y,x,y-sy*arm,line)
        }
    }
    private fun drawCloneSource(canvas: Canvas, box: RectF) {
        val source = model.cloneSource ?: return
        val offset = model.cloneOffset
        // Before the first stroke the mark sits on the chosen point; then it follows the brush.
        val at = when {
            points.isEmpty() -> if (offset == null) source else return
            offset != null -> PointF(points.last().x+offset.x, points.last().y+offset.y)
            else -> PointF(source.x+points.last().x-points.first().x, source.y+points.last().y-points.first().y)
        }
        val x = box.left+at.x*box.width()
        val y = box.top+at.y*box.height()
        val arm = context.dp(10).toFloat()
        for (pass in 0..1) {
            line.color = if (pass == 0) Color.BLACK else Color.WHITE
            line.strokeWidth = context.dp(if (pass == 0) 4 else 2).toFloat()
            canvas.drawLine(x-arm,y,x+arm,y,line)
            canvas.drawLine(x,y-arm,x,y+arm,line)
        }
    }

    private fun point(box: RectF,x: Float,y: Float) =
        PointF(((x-box.left)/box.width()).coerceIn(0f,1f), ((y-box.top)/box.height()).coerceIn(0f,1f))

    /** Width over height of the constrained crop frame in normalized image space, or null when free. */
    private fun cropShape(image: Bitmap): Float? {
        val aspect = image.width.toFloat()/image.height
        return model.ratio.of(aspect)?.let { it/aspect }
    }
    private fun grab(image: Bitmap, box: RectF, x: Float, y: Float) {
        val r = cropBox(box)
        val free = cropShape(image) == null
        var best = context.dp(36).toFloat()
        grabbed = false
        for (sx in -1..1) for (sy in -1..1) {
            if ((sx == 0 && sy == 0) || (!free && (sx == 0 || sy == 0))) continue
            val hx = if (sx < 0) r.left else if (sx > 0) r.right else r.centerX()
            val hy = if (sy < 0) r.top else if (sy > 0) r.bottom else r.centerY()
            val distance = hypot(x-hx,y-hy)
            if (distance < best) { best = distance; grabbed = true; grabX = sx; grabY = sy }
        }
        if (!grabbed && r.contains(x,y)) { grabbed = true; grabX = 0; grabY = 0 }
        grabStart.set(model.crop)
        downX = x; downY = y
    }
    private fun dragCrop(image: Bitmap, box: RectF, x: Float, y: Float) {
        val c = model.crop
        val s = grabStart
        val least = 0.06f
        val p = point(box,x,y)
        val n = cropShape(image)
        if (grabX == 0 && grabY == 0) {
            val dx = ((x-downX)/box.width()).coerceIn(-s.left,1f-s.right)
            val dy = ((y-downY)/box.height()).coerceIn(-s.top,1f-s.bottom)
            c.set(s.left+dx,s.top+dy,s.right+dx,s.bottom+dy)
        } else if (n == null) {
            if (grabX < 0) c.left = p.x.coerceIn(0f,c.right-least)
            if (grabX > 0) c.right = p.x.coerceIn(c.left+least,1f)
            if (grabY < 0) c.top = p.y.coerceIn(0f,c.bottom-least)
            if (grabY > 0) c.bottom = p.y.coerceIn(c.top+least,1f)
        } else {
            // The opposite corner stays put; the frame keeps its shape inside the picture.
            val ax = if (grabX < 0) s.right else s.left
            val ay = if (grabY < 0) s.bottom else s.top
            val room = minOf(if (grabX < 0) ax else 1f-ax, (if (grabY < 0) ay else 1f-ay)*n)
            val w = maxOf(abs(p.x-ax),abs(p.y-ay)*n).coerceAtLeast(least).coerceAtMost(room)
            val h = w/n
            c.set(if (grabX < 0) ax-w else ax, if (grabY < 0) ay-h else ay,
                if (grabX < 0) ax else ax+w, if (grabY < 0) ay else ay+h)
        }
        model.touch()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val image = model.preview ?: return true
        if (model.busy || model.compare) { points.clear(); grabbed = false; return true }
        val box = frame(image)
        val cropping = model.tool == EditTool.CROP
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multi = false
                points.clear()
                grabbed = false
                lastX = e.x; lastY = e.y
                if (cropping) grab(image,box,e.x,e.y)
                else if (model.tool != EditTool.PAN && box.contains(e.x,e.y)) points.add(point(box,e.x,e.y))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true
                points.clear()
                grabbed = false
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
                if (cropping) {
                    if (grabbed) dragCrop(image,box,e.x,e.y)
                    else { panX += e.x-lastX; panY += e.y-lastY }
                } else if (model.tool == EditTool.PAN) {
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
                    val trace = trace(points.toList())
                    val r = trace.bounds()
                    if ((!isBox(model.tool) || (r.width()*box.width()>context.dp(4) && r.height()*box.height()>context.dp(4))) &&
                        (model.tool != EditTool.LASSO || points.size >= 3)) gesture(model.tool,trace)
                }
                points.clear()
                grabbed = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { points.clear(); grabbed = false }
        }
        invalidate()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
