package com.example.atom2univers.pixelart

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/**
 * Custom View for pixel art drawing.
 * Supports zoom, pan, grid display, and basic drawing tools.
 */
class PixelCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Canvas dimensions in pixels
    private var canvasWidth = 32
    private var canvasHeight = 32

    // The actual pixel data (ARGB)
    private var pixelData: IntArray = IntArray(canvasWidth * canvasHeight) { Color.TRANSPARENT }

    // Bitmap for rendering
    private var canvasBitmap: Bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)

    // Paints
    private val bitmapPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val gridPaint = Paint().apply {
        color = Color.argb(80, 128, 128, 128)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val backgroundPaint = Paint().apply {
        color = Color.DKGRAY
    }
    private val checkerPaint1 = Paint().apply {
        color = Color.rgb(200, 200, 200)
    }
    private val checkerPaint2 = Paint().apply {
        color = Color.rgb(150, 150, 150)
    }
    private val previewPaint = Paint().apply {
        color = Color.argb(128, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // View state - NO ZOOM LIMITS!
    private var zoomLevel = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    var showGrid = true
        set(value) {
            field = value
            invalidate()
        }

    // Current drawing color
    var primaryColor = Color.BLACK
    var secondaryColor = Color.WHITE

    // Current tool
    var currentTool: Tool = Tool.PENCIL

    // Shape drawing state (for line, rectangle, circle)
    private var shapeStartX = -1
    private var shapeStartY = -1
    private var shapeEndX = -1
    private var shapeEndY = -1
    private var isDrawingShape = false
    private var previewPixels = mutableListOf<Pair<Int, Int>>()

    // Fill mode for shapes
    var shapeFilled = false

    // History for undo/redo
    private val undoStack = mutableListOf<IntArray>()
    private val redoStack = mutableListOf<IntArray>()
    private val maxHistorySize = 50

    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private var lastCenterX = 0f
    private var lastCenterY = 0f
    private var isPanning = false
    private var isDrawing = false
    private var pointerCount = 0

    // Pending draw state (to avoid drawing when starting a zoom gesture)
    private var pendingDrawX = -1
    private var pendingDrawY = -1
    private var hasMoved = false
    private var wasMultiTouch = false

    // Listeners
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onColorPicked: ((color: Int) -> Unit)? = null
    var onZoomChanged: ((zoom: Float) -> Unit)? = null

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY

                // Adjust offset to zoom around focus point
                offsetX = focusX - (focusX - offsetX) * scaleFactor
                offsetY = focusY - (focusY - offsetY) * scaleFactor

                // NO LIMITS - zoom as much as you want!
                zoomLevel *= scaleFactor
                // Just prevent it from becoming zero or negative
                if (zoomLevel < 0.01f) zoomLevel = 0.01f

                onZoomChanged?.invoke(zoomLevel)
                invalidate()
                return true
            }
        })

        updateBitmap()
    }

    enum class Tool {
        PENCIL,
        ERASER,
        FILL,
        PICKER,
        LINE,
        RECTANGLE,
        CIRCLE
    }

    fun setCanvasSize(width: Int, height: Int) {
        val newWidth = width.coerceIn(8, 512)
        val newHeight = height.coerceIn(8, 512)

        if (newWidth != canvasWidth || newHeight != canvasHeight) {
            saveToHistory()

            val newPixelData = IntArray(newWidth * newHeight) { Color.TRANSPARENT }

            // Copy existing pixels
            for (y in 0 until min(canvasHeight, newHeight)) {
                for (x in 0 until min(canvasWidth, newWidth)) {
                    newPixelData[y * newWidth + x] = pixelData[y * canvasWidth + x]
                }
            }

            canvasWidth = newWidth
            canvasHeight = newHeight
            pixelData = newPixelData
            canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            updateBitmap()
            centerCanvas()
            invalidate()
        }
    }

    fun getCanvasWidth() = canvasWidth
    fun getCanvasHeight() = canvasHeight

    private fun saveToHistory() {
        undoStack.add(pixelData.clone())
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        notifyHistoryChanged()
    }

    private fun notifyHistoryChanged() {
        onHistoryChanged?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    fun undo(): Boolean {
        if (undoStack.isNotEmpty()) {
            redoStack.add(pixelData.clone())
            pixelData = undoStack.removeAt(undoStack.lastIndex)
            updateBitmap()
            invalidate()
            notifyHistoryChanged()
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (redoStack.isNotEmpty()) {
            undoStack.add(pixelData.clone())
            pixelData = redoStack.removeAt(redoStack.lastIndex)
            updateBitmap()
            invalidate()
            notifyHistoryChanged()
            return true
        }
        return false
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun clearCanvas() {
        saveToHistory()
        pixelData.fill(Color.TRANSPARENT)
        updateBitmap()
        invalidate()
    }

    fun setZoom(zoom: Float) {
        zoomLevel = if (zoom < 0.01f) 0.01f else zoom
        onZoomChanged?.invoke(zoomLevel)
        invalidate()
    }

    fun getZoom() = zoomLevel

    fun zoomIn() {
        zoomLevel *= 1.5f
        onZoomChanged?.invoke(zoomLevel)
        invalidate()
    }

    fun zoomOut() {
        zoomLevel /= 1.5f
        if (zoomLevel < 0.01f) zoomLevel = 0.01f
        onZoomChanged?.invoke(zoomLevel)
        invalidate()
    }

    fun centerCanvas() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val canvasDisplayWidth = canvasWidth * zoomLevel
        val canvasDisplayHeight = canvasHeight * zoomLevel

        offsetX = (viewWidth - canvasDisplayWidth) / 2
        offsetY = (viewHeight - canvasDisplayHeight) / 2
        invalidate()
    }

    private fun updateBitmap() {
        canvasBitmap.setPixels(pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
    }

    private fun screenToPixel(screenX: Float, screenY: Float): Pair<Int, Int>? {
        val pixelX = ((screenX - offsetX) / zoomLevel).toInt()
        val pixelY = ((screenY - offsetY) / zoomLevel).toInt()

        return if (pixelX in 0 until canvasWidth && pixelY in 0 until canvasHeight) {
            Pair(pixelX, pixelY)
        } else {
            null
        }
    }

    private fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until canvasWidth && y in 0 until canvasHeight) {
            pixelData[y * canvasWidth + x] = color
            canvasBitmap.setPixel(x, y, color)
        }
    }

    private fun getPixel(x: Int, y: Int): Int {
        return if (x in 0 until canvasWidth && y in 0 until canvasHeight) {
            pixelData[y * canvasWidth + x]
        } else {
            Color.TRANSPARENT
        }
    }

    private fun floodFill(startX: Int, startY: Int, newColor: Int) {
        val targetColor = getPixel(startX, startY)
        if (targetColor == newColor) return

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(Pair(startX, startY))

        val visited = BooleanArray(canvasWidth * canvasHeight)

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) continue

            val index = y * canvasWidth + x
            if (visited[index]) continue
            if (pixelData[index] != targetColor) continue

            visited[index] = true
            pixelData[index] = newColor

            stack.addLast(Pair(x + 1, y))
            stack.addLast(Pair(x - 1, y))
            stack.addLast(Pair(x, y + 1))
            stack.addLast(Pair(x, y - 1))
        }

        updateBitmap()
    }

    // Bresenham's line algorithm
    private fun getLinePixels(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        var dx = abs(x1 - x0)
        var dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0

        while (true) {
            pixels.add(Pair(x, y))
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return pixels
    }

    // Rectangle pixels
    private fun getRectanglePixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        if (filled) {
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pixels.add(Pair(x, y))
                }
            }
        } else {
            // Top and bottom edges
            for (x in minX..maxX) {
                pixels.add(Pair(x, minY))
                pixels.add(Pair(x, maxY))
            }
            // Left and right edges (excluding corners)
            for (y in (minY + 1) until maxY) {
                pixels.add(Pair(minX, y))
                pixels.add(Pair(maxX, y))
            }
        }
        return pixels
    }

    // Midpoint circle algorithm (Bresenham)
    private fun getCirclePixels(cx: Int, cy: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val radius = sqrt(((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy)).toDouble()).toInt()
        if (radius == 0) {
            pixels.add(Pair(cx, cy))
            return pixels
        }

        if (filled) {
            // Filled circle
            for (y in -radius..radius) {
                val halfWidth = sqrt((radius * radius - y * y).toDouble()).toInt()
                for (x in -halfWidth..halfWidth) {
                    pixels.add(Pair(cx + x, cy + y))
                }
            }
        } else {
            // Circle outline using midpoint algorithm
            var x = radius
            var y = 0
            var err = 0

            while (x >= y) {
                pixels.add(Pair(cx + x, cy + y))
                pixels.add(Pair(cx + y, cy + x))
                pixels.add(Pair(cx - y, cy + x))
                pixels.add(Pair(cx - x, cy + y))
                pixels.add(Pair(cx - x, cy - y))
                pixels.add(Pair(cx - y, cy - x))
                pixels.add(Pair(cx + y, cy - x))
                pixels.add(Pair(cx + x, cy - y))

                y++
                err += 1 + 2 * y
                if (2 * (err - x) + 1 > 0) {
                    x--
                    err += 1 - 2 * x
                }
            }
        }
        return pixels.distinct()
    }

    private fun updateShapePreview() {
        previewPixels.clear()
        if (!isDrawingShape || shapeStartX < 0 || shapeEndX < 0) return

        previewPixels.addAll(when (currentTool) {
            Tool.LINE -> getLinePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY)
            Tool.RECTANGLE -> getRectanglePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.CIRCLE -> getCirclePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            else -> emptyList()
        })
    }

    private fun commitShape() {
        if (previewPixels.isEmpty()) return

        for ((x, y) in previewPixels) {
            setPixel(x, y, primaryColor)
        }
        previewPixels.clear()
        isDrawingShape = false
        shapeStartX = -1
        shapeStartY = -1
        shapeEndX = -1
        shapeEndY = -1
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && oldh == 0) {
            // First layout - fit canvas to view
            val scaleX = w.toFloat() / canvasWidth
            val scaleY = h.toFloat() / canvasHeight
            zoomLevel = min(scaleX, scaleY) * 0.8f
            centerCanvas()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(zoomLevel, zoomLevel)

        // Draw transparency checkerboard
        val checkerSize = 4
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                val paint = if ((x / checkerSize + y / checkerSize) % 2 == 0) checkerPaint1 else checkerPaint2
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + 1).toFloat(), (y + 1).toFloat(),
                    paint
                )
            }
        }

        // Draw the pixel art
        canvas.drawBitmap(canvasBitmap, 0f, 0f, bitmapPaint)

        // Draw shape preview
        if (isDrawingShape && previewPixels.isNotEmpty()) {
            previewPaint.color = primaryColor or 0x80000000.toInt() // Semi-transparent primary color
            for ((px, py) in previewPixels) {
                if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
                    canvas.drawRect(
                        px.toFloat(), py.toFloat(),
                        (px + 1).toFloat(), (py + 1).toFloat(),
                        previewPaint
                    )
                }
            }
        }

        // Draw grid
        if (showGrid && zoomLevel >= 4f) {
            for (x in 0..canvasWidth) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), canvasHeight.toFloat(), gridPaint)
            }
            for (y in 0..canvasHeight) {
                canvas.drawLine(0f, y.toFloat(), canvasWidth.toFloat(), y.toFloat(), gridPaint)
            }
        }

        canvas.restore()
    }

    // Calculate center point of all pointers
    private fun getPointerCenter(event: MotionEvent): Pair<Float, Float> {
        var sumX = 0f
        var sumY = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        return Pair(sumX / count, sumY / count)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        pointerCount = event.pointerCount
        val (centerX, centerY) = getPointerCenter(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastCenterX = centerX
                lastCenterY = centerY
                isPanning = false
                isDrawing = false
                hasMoved = false
                wasMultiTouch = false

                val pixel = screenToPixel(event.x, event.y)
                if (pixel != null) {
                    // For shape tools, start immediately (they need drag)
                    if (currentTool in listOf(Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE)) {
                        saveToHistory()
                        isDrawingShape = true
                        shapeStartX = pixel.first
                        shapeStartY = pixel.second
                        shapeEndX = pixel.first
                        shapeEndY = pixel.second
                        updateShapePreview()
                        invalidate()
                    } else {
                        // For pencil/eraser: store pending position, draw on release or move
                        pendingDrawX = pixel.first
                        pendingDrawY = pixel.second
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down - this is now a pan/zoom gesture
                wasMultiTouch = true
                isPanning = true
                isDrawing = false
                pendingDrawX = -1
                pendingDrawY = -1

                // Cancel shape drawing if user puts second finger
                if (isDrawingShape) {
                    isDrawingShape = false
                    previewPixels.clear()
                    // Undo the history save since we're canceling
                    if (undoStack.isNotEmpty()) {
                        undoStack.removeAt(undoStack.lastIndex)
                        notifyHistoryChanged()
                    }
                    invalidate()
                }

                // Update center for pan
                lastCenterX = centerX
                lastCenterY = centerY
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger lifted, update center to avoid jump
                // We need to recalculate excluding the lifted pointer
                val liftedIndex = event.actionIndex
                var sumX = 0f
                var sumY = 0f
                var count = 0
                for (i in 0 until event.pointerCount) {
                    if (i != liftedIndex) {
                        sumX += event.getX(i)
                        sumY += event.getY(i)
                        count++
                    }
                }
                if (count > 0) {
                    lastCenterX = sumX / count
                    lastCenterY = sumY / count
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount >= 2 || isPanning) {
                    // Pan with fingers - use center point
                    val dx = centerX - lastCenterX
                    val dy = centerY - lastCenterY
                    offsetX += dx
                    offsetY += dy
                    lastCenterX = centerX
                    lastCenterY = centerY
                    invalidate()
                } else if (isDrawingShape && pointerCount == 1) {
                    val pixel = screenToPixel(event.x, event.y)
                    if (pixel != null) {
                        shapeEndX = pixel.first
                        shapeEndY = pixel.second
                        updateShapePreview()
                        invalidate()
                    }
                } else if (pointerCount == 1 && !wasMultiTouch) {
                    val pixel = screenToPixel(event.x, event.y)
                    if (pixel != null) {
                        // Check if we moved to a different pixel
                        if (pixel.first != pendingDrawX || pixel.second != pendingDrawY) {
                            hasMoved = true
                            // Start drawing mode - commit pending and continue drawing
                            if (!isDrawing && pendingDrawX >= 0) {
                                saveToHistory()
                                isDrawing = true
                                // Draw the first pending pixel
                                drawPixelWithTool(pendingDrawX, pendingDrawY)
                            }
                            if (isDrawing) {
                                drawPixelWithTool(pixel.first, pixel.second)
                            }
                            pendingDrawX = pixel.first
                            pendingDrawY = pixel.second
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDrawingShape) {
                    commitShape()
                    invalidate()
                } else if (!wasMultiTouch && pendingDrawX >= 0 && !isDrawing) {
                    // Single tap without move - draw single pixel
                    saveToHistory()
                    handleSingleTap(pendingDrawX, pendingDrawY)
                }

                // Reset all states
                isPanning = false
                isDrawing = false
                isDrawingShape = false
                pendingDrawX = -1
                pendingDrawY = -1
                hasMoved = false
                wasMultiTouch = false
            }

            MotionEvent.ACTION_CANCEL -> {
                // Cancel everything without committing
                isDrawingShape = false
                previewPixels.clear()
                isPanning = false
                isDrawing = false
                pendingDrawX = -1
                pendingDrawY = -1
                hasMoved = false
                wasMultiTouch = false
                invalidate()
            }
        }

        return true
    }

    private fun drawPixelWithTool(x: Int, y: Int) {
        when (currentTool) {
            Tool.PENCIL -> {
                setPixel(x, y, primaryColor)
                invalidate()
            }
            Tool.ERASER -> {
                setPixel(x, y, Color.TRANSPARENT)
                invalidate()
            }
            else -> { }
        }
    }

    private fun handleSingleTap(x: Int, y: Int) {
        when (currentTool) {
            Tool.PENCIL -> {
                setPixel(x, y, primaryColor)
                invalidate()
            }
            Tool.ERASER -> {
                setPixel(x, y, Color.TRANSPARENT)
                invalidate()
            }
            Tool.FILL -> {
                floodFill(x, y, primaryColor)
                invalidate()
            }
            Tool.PICKER -> {
                val pickedColor = getPixel(x, y)
                if (pickedColor != Color.TRANSPARENT) {
                    primaryColor = pickedColor
                    onColorPicked?.invoke(pickedColor)
                }
            }
            else -> { }
        }
    }

    fun exportToBitmap(): Bitmap {
        return canvasBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun swapColors() {
        val temp = primaryColor
        primaryColor = secondaryColor
        secondaryColor = temp
    }
}
