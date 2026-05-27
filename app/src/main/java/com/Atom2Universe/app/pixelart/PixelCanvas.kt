package com.Atom2Universe.app.pixelart

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.set
import kotlin.math.*

/**
 * Data class representing a single animation frame.
 */
data class Frame(
    val id: Int,
    var pixelData: IntArray,
    var duration: Int = 100, // Duration in milliseconds
    @Transient var cachedBitmap: Bitmap? = null // Cached bitmap for rendering (not serialized)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return id == other.id
    }

    override fun hashCode(): Int = id
}

/**
 * View modes for the pixel art editor.
 */
enum class ViewMode {
    FRAME,  // Single frame view (default)
    SHEET   // Sprite sheet view (all frames in grid)
}

/**
 * Layout configuration for sheet mode.
 */
data class SheetLayout(
    val columns: Int,           // Number of columns in grid
    val rows: Int,              // Number of rows (calculated)
    val autoLayout: Boolean,    // True = auto-calculate optimal layout
    val frameWidth: Int,        // Width of each frame in canvas pixels
    val frameHeight: Int,       // Height of each frame in canvas pixels
    val sheetWidth: Int,        // Total sheet width in canvas pixels
    val sheetHeight: Int        // Total sheet height in canvas pixels
)

/**
 * Layer types for the pixel art editor.
 */
enum class LayerType {
    PIXEL,  // Editable pixel layer
    IMAGE   // Imported image layer (not directly editable)
}

/**
 * Data class representing a layer in the editor.
 */
data class Layer(
    val id: Int,
    var name: String,
    var type: LayerType,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var opacity: Float = 1f,  // 0.0 to 1.0
    var offsetX: Int = 0,     // Position offset in pixels
    var offsetY: Int = 0,
    var scale: Float = 1f,    // Scale for image layers
    // For PIXEL type: pixelData contains the drawing
    var pixelData: IntArray? = null,
    // For IMAGE type: bitmap contains the imported image
    var imageBitmap: Bitmap? = null,
    // Original image dimensions (for IMAGE type)
    var originalWidth: Int = 0,
    var originalHeight: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Layer) return false
        return id == other.id
    }

    override fun hashCode(): Int = id

    /**
     * Gets the scaled width of the layer.
     */
    @Suppress("unused")
    fun getScaledWidth(): Int {
        return when (type) {
            LayerType.PIXEL -> pixelData?.let { sqrt(it.size.toDouble()).toInt() } ?: 0
            LayerType.IMAGE -> (originalWidth * scale).toInt()
        }
    }

    /**
     * Gets the scaled height of the layer.
     */
    @Suppress("unused")
    fun getScaledHeight(): Int {
        return when (type) {
            LayerType.PIXEL -> pixelData?.let { sqrt(it.size.toDouble()).toInt() } ?: 0
            LayerType.IMAGE -> (originalHeight * scale).toInt()
        }
    }
}

/**
 * Types of project-level changes that can be undone.
 */
@Suppress("unused")
enum class ProjectChangeType {
    SPLIT_SHEET,     // Split sheet into multiple frames
    MERGE_FRAMES,    // Merge frames into a single sheet (future)
    RESIZE_CANVAS,   // Canvas resize operation
    NEW_PROJECT      // New project (optional - might not want to undo this)
}

/**
 * Snapshot of the entire project state for undo of major operations.
 */
data class ProjectSnapshot(
    val changeType: ProjectChangeType,
    val description: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val frameDataList: List<FrameSnapshot>,
    val currentFrameIndex: Int,
    val viewMode: ViewMode
)

/**
 * Snapshot of a single frame's data.
 */
data class FrameSnapshot(
    val id: Int,
    val pixelData: IntArray,
    val duration: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameSnapshot) return false
        return id == other.id && pixelData.contentEquals(other.pixelData) && duration == other.duration
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + pixelData.contentHashCode()
        result = 31 * result + duration
        return result
    }
}

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
    private var pixelData: IntArray = IntArray(canvasWidth * canvasHeight)

    // Bitmap for rendering
    private var canvasBitmap: Bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)

    // Paints
    private val bitmapPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val gridPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0) // Semi-transparent dark
        style = Paint.Style.STROKE
        isAntiAlias = false
    }
    private val backgroundPaint = Paint().apply {
        color = Color.DKGRAY
    }
    private val checkerPaint1 = Paint().apply {
        color = Color.argb(255, 200, 200, 200)
    }
    private val checkerPaint2 = Paint().apply {
        color = Color.argb(255, 150, 150, 150)
    }
    // Pre-allocated paint for low-zoom checkerboard (avoid allocation in onDraw)
    private val checkerAvgPaint = Paint().apply {
        color = Color.rgb(175, 175, 175)
    }
    // Pre-allocated paint for onion skin
    private val onionPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    // Cached checkerboard tile bitmap for performance
    private var checkerboardTile: Bitmap? = null
    private var checkerboardTileSize = 32 // Size of the tile in canvas pixels
    private val checkerboardShader = Paint().apply {
        isFilterBitmap = false
    }

    private fun updateCheckerboardAlpha() {
        // canvasTransparency: 0 = opaque checkerboard, 1 = fully transparent
        val alpha = ((1f - canvasTransparency) * 255).toInt()
        checkerPaint1.alpha = alpha
        checkerPaint2.alpha = alpha
        checkerAvgPaint.alpha = alpha
        checkerboardShader.alpha = alpha
    }

    // Create a small checkerboard tile bitmap that can be repeated
    private fun createCheckerboardTile(): Bitmap {
        val tileSize = checkerboardTileSize
        val checkerSize = 4
        val tile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        for (y in 0 until tileSize) {
            for (x in 0 until tileSize) {
                val color = if ((x / checkerSize + y / checkerSize) % 2 == 0)
                    Color.rgb(200, 200, 200) else Color.rgb(150, 150, 150)
                tile[x, y] = color
            }
        }
        return tile
    }

    // Calculate visible canvas bounds (for viewport culling)
    private fun getVisibleCanvasBounds(): Rect {
        // Convert screen bounds to canvas coordinates
        val screenLeft = -offsetX / zoomLevel
        val screenTop = -offsetY / zoomLevel
        val screenRight = (width - offsetX) / zoomLevel
        val screenBottom = (height - offsetY) / zoomLevel

        // Clamp to canvas bounds with small margin
        val margin = 1
        return Rect(
            maxOf(0, screenLeft.toInt() - margin),
            maxOf(0, screenTop.toInt() - margin),
            minOf(canvasWidth, screenRight.toInt() + margin),
            minOf(canvasHeight, screenBottom.toInt() + margin)
        )
    }
    private val previewPaint = Paint().apply {
        color = Color.argb(128, 255, 255, 255)
        style = Paint.Style.FILL
    }
    // Selection paints - black and white dashed lines (marching ants)
    private val selectionPaintBlack = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val selectionPaintWhite = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    private val selectionFillPaint = Paint().apply {
        color = Color.argb(30, 100, 150, 255)
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
        set(value) {
            // If switching away from the pending shape's tool, finalize the shape
            if (hasPendingShape && value != pendingShapeTool) {
                finalizePendingShape()
            }
            field = value
        }

    // Shape drawing state (for line, rectangle, circle)
    private var shapeStartX = -1
    private var shapeStartY = -1
    private var shapeEndX = -1
    private var shapeEndY = -1
    private var isDrawingShape = false
    private var previewPixels = mutableListOf<Pair<Int, Int>>()

    // Pending shape state (shape waiting to be finalized, can be moved)
    private var hasPendingShape = false
    private var pendingShapePixels = mutableListOf<Pair<Int, Int>>()
    private var pendingShapeTool: Tool? = null  // Which tool created the pending shape
    private var pendingShapeOffsetX = 0  // Offset from original position
    private var pendingShapeOffsetY = 0
    private var isDraggingPendingShape = false
    private var pendingShapeDragStartX = 0f
    private var pendingShapeDragStartY = 0f
    private var pendingShapeOriginalOffsetX = 0
    private var pendingShapeOriginalOffsetY = 0
    // For the marching ants animation
    private var marchingAntsPhase = 0f
    private val marchingAntsHandler = Handler(Looper.getMainLooper())
    private val marchingAntsRunnable = object : Runnable {
        override fun run() {
            if (hasPendingShape) {
                marchingAntsPhase = (marchingAntsPhase + 2f) % 8f
                invalidate()
                marchingAntsHandler.postDelayed(this, 100)
            }
        }
    }

    // Bend mode state (for deforming shapes)
    var isBendModeEnabled = false
    private var isBendingShape = false
    private var bendControlX = 0f  // Control point for Bezier curve (in canvas coords)
    private var bendControlY = 0f
    private var pendingShapeStartX = 0  // Original line endpoints for bend calculation
    private var pendingShapeStartY = 0
    private var pendingShapeEndX = 0
    private var pendingShapeEndY = 0
    private var bendDragStartX = 0f
    private var bendDragStartY = 0f

    // Multi-point deformation for all shapes
    data class DeformationPoint(
        val anchorX: Float,  // Where the user started touching (in canvas coords)
        val anchorY: Float,
        var offsetX: Float,  // How much it's been moved
        var offsetY: Float,
        val influence: Float = 50f  // Radius of influence
    )
    private val deformationPoints = mutableListOf<DeformationPoint>()
    private var originalShapePixels = mutableListOf<Pair<Int, Int>>()  // Pixels before deformation
    private var currentDeformationPoint: DeformationPoint? = null  // Currently being dragged

    // Callback to notify when pending shape state changes (for UI updates)
    var onPendingShapeChanged: ((hasPending: Boolean) -> Unit)? = null

    // Fill mode for shapes
    var shapeFilled = false

    // Stroke style for shapes (PENCIL = 1px, MARKER = thick solid, BRUSH = thick with soft edges)
    enum class ShapeStrokeStyle { PENCIL, MARKER, BRUSH }
    var shapeStrokeStyle = ShapeStrokeStyle.PENCIL

    // Brush/Marker size (in pixels) - also used for shape stroke when style is MARKER or BRUSH
    var brushSize = 3
        set(value) {
            field = value.coerceIn(1, 50)
        }

    // Selection state
    private var selectionStartX = -1
    private var selectionStartY = -1
    private var selectionEndX = -1
    private var selectionEndY = -1
    private var hasSelection = false
    private var isSelectingArea = false

    // Clipboard for copy/paste
    private var clipboardData: IntArray? = null
    private var clipboardWidth = 0
    private var clipboardHeight = 0

    // Move selection state
    private var isMovingSelection = false
    private var moveSelectionData: IntArray? = null
    private var moveSelectionWidth = 0
    private var moveSelectionHeight = 0
    private var moveSelectionOrigX = 0
    private var moveSelectionOrigY = 0
    private var moveSelectionCurrentX = 0
    private var moveSelectionCurrentY = 0
    private var moveStartTouchX = 0f
    private var moveStartTouchY = 0f

    // Listener for selection changes
    var onSelectionChanged: ((hasSelection: Boolean) -> Unit)? = null

    // History for undo/redo - managed by PixelArtHistoryManager
    private var historyManager: PixelArtHistoryManager? = null

    // Legacy simple stacks (fallback if historyManager not initialized)
    private val undoStackLegacy = mutableListOf<IntArray>()
    private val redoStackLegacy = mutableListOf<IntArray>()
    private val maxHistorySize = 50

    // Project-level snapshot stack for major operations (split, merge, resize)
    private val projectSnapshotStack = mutableListOf<ProjectSnapshot>()
    private val maxProjectSnapshots = 5  // Keep last 5 major operations
    var onProjectSnapshotChanged: ((canUndo: Boolean, lastChangeDescription: String?) -> Unit)? = null

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
    private var hasPendingPickerPosition = false  // For PICKER tool outside canvas bounds
    private var hasMoved = false
    private var wasMultiTouch = false

    // Delta tracking for large canvas optimization
    // Tracks pixel changes during a stroke to avoid full canvas clone
    private val strokeChangedPixels = mutableMapOf<Int, Int>() // index -> original color
    private var isTrackingDelta = false

    // Listeners
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onColorPicked: ((color: Int) -> Unit)? = null
    var onZoomChanged: ((zoom: Float) -> Unit)? = null
    var onCanvasModified: (() -> Unit)? = null  // Called after any canvas modification (for auto-save)

    // Zoom and offset getters/setters for configuration change support
    fun getCurrentZoom(): Float = zoomLevel
    fun getOffsetX(): Float = offsetX
    fun getOffsetY(): Float = offsetY

    fun setZoomAndOffset(zoom: Float, offX: Float, offY: Float) {
        zoomLevel = zoom
        offsetX = offX
        offsetY = offY
        invalidate()
    }

    // ========== FRAME MANAGEMENT ==========
    private val frames = mutableListOf<Frame>()
    private var currentFrameIndex = 0
    private var nextFrameId = 1

    // Animation playback
    private var isPlaying = false
    var animationFps = 4  // Default 4 FPS
        set(value) {
            field = value.coerceIn(1, 60)
        }
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && frames.size > 1) {
                nextFrame()
                val delay = (1000 / animationFps).toLong()
                animationHandler.postDelayed(this, delay)
            }
        }
    }

    // Onion skinning - now supports any frame, not just prev/next
    private val onionSkinFrames = mutableSetOf<Int>()  // Set of frame indices to show as onion skin
    var onionSkinOpacity = 0.5f   // Opacity for onion skin layers (0.0 to 1.0)
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Legacy compatibility - keep for settings dialog
    @Suppress("unused")
    var onionSkinEnabled = false
        set(value) {
            field = value
            invalidate()
        }
    @Suppress("unused")
    var onionSkinPrevious = true
    @Suppress("unused")
    var onionSkinNext = false

    /**
     * Toggle onion skin visibility for a specific frame
     */
    fun toggleOnionSkinFrame(frameIndex: Int) {
        if (frameIndex == currentFrameIndex) return  // Can't show current frame as onion skin
        if (onionSkinFrames.contains(frameIndex)) {
            onionSkinFrames.remove(frameIndex)
        } else {
            onionSkinFrames.add(frameIndex)
        }
        invalidate()
    }

    /**
     * Check if a frame is shown as onion skin
     */
    fun isFrameInOnionSkin(frameIndex: Int): Boolean {
        return onionSkinFrames.contains(frameIndex)
    }

    /**
     * Get all frames shown as onion skin
     */
    @Suppress("unused")
    fun getOnionSkinFrames(): Set<Int> = onionSkinFrames.toSet()

    /**
     * Clear all onion skin frames
     */
    @Suppress("unused")
    fun clearOnionSkinFrames() {
        onionSkinFrames.clear()
        invalidate()
    }

    // Frame change listener
    var onFrameChanged: ((currentIndex: Int, totalFrames: Int) -> Unit)? = null
    var onPlaybackChanged: ((isPlaying: Boolean) -> Unit)? = null

    // ========== SHEET MODE ==========
    // Current view mode (frame or sheet)
    var viewMode: ViewMode = ViewMode.FRAME
        set(value) {
            if (field != value) {
                // Save current frame data before switching modes
                saveCurrentFrameData()
                field = value
                if (value == ViewMode.SHEET) {
                    calculateSheetLayout()
                    sheetActiveFrameIndex = currentFrameIndex
                }
                onViewModeChanged?.invoke(value)
                invalidate()
            }
        }

    // User preference for columns in sheet mode (0 = auto)
    var sheetColumns: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            if (viewMode == ViewMode.SHEET) {
                calculateSheetLayout()
                invalidate()
            }
        }

    // Show grid lines between frames in sheet mode
    var showSheetGridLines: Boolean = true
        set(value) {
            field = value
            if (viewMode == ViewMode.SHEET) invalidate()
        }

    // Currently active frame in sheet mode
    private var sheetActiveFrameIndex: Int = 0

    // Calculated sheet layout
    private var sheetLayout: SheetLayout = SheetLayout(1, 1, true, 32, 32, 32, 32)

    // Sheet mode callbacks
    var onViewModeChanged: ((ViewMode) -> Unit)? = null
    var onSheetActiveFrameChanged: ((frameIndex: Int) -> Unit)? = null

    // Paint for sheet grid lines
    private val sheetGridPaint = Paint().apply {
        color = Color.parseColor("#00BCD4")  // Cyan
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Paint for active frame highlight in sheet mode
    private val sheetActiveFramePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")  // Green
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Animation preview playback state
    private var previewPlaybackFrameIndex: Int = -1  // -1 = not playing
    var isPreviewPlaying: Boolean = false
        private set

    // Paint for animation preview highlight (different from active frame)
    private val previewHighlightPaint = Paint().apply {
        color = Color.parseColor("#FF9800")  // Orange
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // ========== LAYER MANAGEMENT ==========
    private val layers = mutableListOf<Layer>()
    private var currentLayerIndex = 0
    private var nextLayerId = 1

    // Layer manipulation state
    private var isMovingLayer = false
    private var layerDragStartX = 0f
    private var layerDragStartY = 0f
    private var layerOriginalOffsetX = 0
    private var layerOriginalOffsetY = 0

    // Layer resize handles
    private var isResizingLayer = false
    private var resizeCorner = -1  // 0=TL, 1=TR, 2=BL, 3=BR
    private var layerOriginalScale = 1f

    // Paint for layer bounds
    private val layerBoundsPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val layerHandlePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val handleSize = 12f  // Size of resize handles in screen pixels

    // Layer change listener
    var onLayerChanged: ((currentIndex: Int, totalLayers: Int) -> Unit)? = null
    var onLayerSelected: ((layer: Layer?) -> Unit)? = null

    // ========== REFERENCE/BACKGROUND IMAGE ==========
    private var referenceImage: Bitmap? = null

    // Canvas transparency - makes the checkerboard transparent to see reference through it
    // 0 = checkerboard fully visible (opaque), 1 = checkerboard fully transparent
    var canvasTransparency: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateCheckerboardAlpha()
            invalidate()
        }

    var referenceVisible: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var referenceOpacity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Helper methods for reference image (using different names to avoid conflict with property accessors)
    fun isReferenceVisible(): Boolean = referenceVisible
    fun updateReferenceVisibility(visible: Boolean) { referenceVisible = visible }
    fun currentReferenceOpacity(): Float = referenceOpacity
    fun updateReferenceOpacity(opacity: Float) { referenceOpacity = opacity }

    // Reference image transformation (independent of canvas zoom)
    // The reference can be freely scaled and positioned UNDER the canvas
    var referenceScale: Float = 1f  // Scale of reference image
        set(value) {
            field = value.coerceIn(0.01f, 50f)
            onReferenceTransformChanged?.invoke()
            invalidate()
        }
    var referenceOffsetX: Float = 0f  // Position of reference in canvas pixel space
        set(value) {
            field = value
            onReferenceTransformChanged?.invoke()
            invalidate()
        }
    var referenceOffsetY: Float = 0f
        set(value) {
            field = value
            onReferenceTransformChanged?.invoke()
            invalidate()
        }

    // Mode for adjusting reference position/scale
    var isAdjustingReference: Boolean = false
        set(value) {
            if (value && !field) {
                // Entering adjustment mode - save current canvas view
                savedZoomLevel = zoomLevel
                savedOffsetX = offsetX
                savedOffsetY = offsetY
            }
            field = value
            invalidate()
        }

    // Saved canvas view state when adjusting reference (canvas stays fixed)
    private var savedZoomLevel = 1f
    private var savedOffsetX = 0f
    private var savedOffsetY = 0f

    // Reference image paint (always full opacity)
    private val referencePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    // Reference border paint (shown when adjusting)
    private val referenceBorderPaint = Paint().apply {
        color = Color.parseColor("#FF9800")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    // Callbacks for reference changes
    var onReferenceChanged: ((hasReference: Boolean) -> Unit)? = null
    var onReferenceTransformChanged: (() -> Unit)? = null

    // Gesture state for reference adjustment (two-finger zoom+pan)
    private var refLastFocusX = 0f
    private var refLastFocusY = 0f
    private var refLastSpan = 0f
    private var refPointerCount = 0

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

        // Initialize with one frame
        initializeFrames()
        updateBitmap()
    }

    private fun initializeFrames() {
        frames.clear()
        frames.add(Frame(nextFrameId++, pixelData.clone()))
        currentFrameIndex = 0
    }

    enum class Tool {
        PENCIL,
        ERASER,
        FILL,
        PICKER,
        LINE,
        DASHED_LINE,     // Ligne pointillée
        RECTANGLE,
        ROUNDED_RECT,    // Rectangle arrondi
        CIRCLE,
        TRIANGLE,
        OVAL,
        HEXAGON,
        DIAMOND,         // Losange
        STAR,            // Étoile à 5 branches
        ARROW,           // Flèche
        PENTAGON,        // Pentagone
        SELECT,
        MOVE_LAYER,      // Tool for moving/resizing layers
        MOVE_SELECTION,  // Tool for dragging selection content in real-time
        MARKER,          // Marker tool with configurable size (100% fill)
        BRUSH            // Brush tool with configurable size (gradient/soft edges)
    }

    fun getCanvasWidth() = canvasWidth
    fun getCanvasHeight() = canvasHeight

    /**
     * Initialize the history manager for per-frame undo/redo
     */
    fun initHistoryManager(context: Context) {
        if (historyManager == null) {
            historyManager = PixelArtHistoryManager(context)
            historyManager?.onHistoryChanged = { canUndo, canRedo ->
                onHistoryChanged?.invoke(canUndo, canRedo)
            }
        }
    }

    /**
     * Set project ID for history persistence
     */
    @Suppress("unused")
    fun setHistoryProjectId(projectId: String) {
        historyManager?.setProjectId(projectId)
    }

    /**
     * Get the current frame ID for history tracking
     */
    private fun getCurrentFrameId(): Int {
        return frames.getOrNull(currentFrameIndex)?.id ?: 0
    }

    private fun saveToHistory() {
        saveToHistory(ActionType.DRAW)
    }

    private fun saveToHistory(actionType: ActionType) {
        val manager = historyManager
        if (manager != null) {
            // Start delta tracking for large canvases (drawing operations)
            if (actionType == ActionType.DRAW && isLargeCanvas()) {
                startDeltaTracking()
            } else {
                // Full snapshot for non-drawing operations or small canvases
                manager.saveToHistory(getCurrentFrameId(), pixelData, actionType)
                // Notify canvas modified for auto-save (delta tracking notifies on finalize)
                notifyCanvasModified()
            }
        } else {
            // Fallback to legacy behavior
            undoStackLegacy.add(pixelData.clone())
            if (undoStackLegacy.size > maxHistorySize) {
                undoStackLegacy.removeAt(0)
            }
            redoStackLegacy.clear()
            notifyHistoryChanged()
            notifyCanvasModified()
        }
    }

    /**
     * Check if current canvas is considered "large" for optimization purposes
     */
    private fun isLargeCanvas(): Boolean {
        return canvasWidth * canvasHeight >= 512 * 512
    }

    /**
     * Start tracking pixel changes for delta-based history
     */
    private fun startDeltaTracking() {
        strokeChangedPixels.clear()
        isTrackingDelta = true
    }

    /**
     * Stop delta tracking and save changes to history
     */
    private fun finalizeDeltaTracking(actionType: ActionType = ActionType.DRAW) {
        if (!isTrackingDelta) return
        isTrackingDelta = false

        val manager = historyManager
        if (manager != null && strokeChangedPixels.isNotEmpty()) {
            // Save delta to history
            manager.saveDeltaToHistory(getCurrentFrameId(), strokeChangedPixels.toMap(), actionType)
        }
        strokeChangedPixels.clear()

        // Notify canvas was modified for auto-save
        notifyCanvasModified()
    }

    /**
     * Notify that canvas was modified (triggers auto-save)
     */
    private fun notifyCanvasModified() {
        onCanvasModified?.invoke()
    }

    /**
     * Cancel delta tracking without saving
     */
    private fun cancelDeltaTracking() {
        isTrackingDelta = false
        strokeChangedPixels.clear()
    }

    /**
     * Revert all pixel changes made during current stroke and cancel tracking.
     * Called when user adds a second finger (indicating zoom/pan intent, not drawing).
     */
    private fun revertAndCancelStroke() {
        if (strokeChangedPixels.isNotEmpty()) {
            // Restore all pixels to their original colors
            for ((index, originalColor) in strokeChangedPixels) {
                if (index in pixelData.indices) {
                    pixelData[index] = originalColor
                }
            }
            updateBitmap()
        }
        isTrackingDelta = false
        strokeChangedPixels.clear()
    }

    private fun notifyHistoryChanged() {
        val manager = historyManager
        if (manager != null) {
            onHistoryChanged?.invoke(manager.canUndo(getCurrentFrameId()), manager.canRedo(getCurrentFrameId()))
        } else {
            onHistoryChanged?.invoke(undoStackLegacy.isNotEmpty(), redoStackLegacy.isNotEmpty())
        }
    }

    fun undo(): Boolean {
        val manager = historyManager
        if (manager != null) {
            val previousState = manager.undo(getCurrentFrameId(), pixelData)
            if (previousState != null) {
                pixelData = previousState
                updateBitmap()
                invalidate()
                notifyCanvasModified()
                return true
            }
            return false
        } else {
            // Fallback to legacy behavior
            if (undoStackLegacy.isNotEmpty()) {
                redoStackLegacy.add(pixelData.clone())
                pixelData = undoStackLegacy.removeAt(undoStackLegacy.lastIndex)
                updateBitmap()
                invalidate()
                notifyHistoryChanged()
                notifyCanvasModified()
                return true
            }
            return false
        }
    }

    fun redo(): Boolean {
        val manager = historyManager
        if (manager != null) {
            val nextState = manager.redo(getCurrentFrameId(), pixelData)
            if (nextState != null) {
                pixelData = nextState
                updateBitmap()
                invalidate()
                notifyCanvasModified()
                return true
            }
            return false
        } else {
            // Fallback to legacy behavior
            if (redoStackLegacy.isNotEmpty()) {
                undoStackLegacy.add(pixelData.clone())
                pixelData = redoStackLegacy.removeAt(redoStackLegacy.lastIndex)
                updateBitmap()
                invalidate()
                notifyHistoryChanged()
                notifyCanvasModified()
                return true
            }
            return false
        }
    }

    fun canUndo(): Boolean {
        val manager = historyManager
        return if (manager != null) {
            manager.canUndo(getCurrentFrameId())
        } else {
            undoStackLegacy.isNotEmpty()
        }
    }

    fun canRedo(): Boolean {
        val manager = historyManager
        return if (manager != null) {
            manager.canRedo(getCurrentFrameId())
        } else {
            redoStackLegacy.isNotEmpty()
        }
    }

    /**
     * Get undo count for current frame
     */
    @Suppress("unused")
    fun getUndoCount(): Int {
        return historyManager?.getUndoCount(getCurrentFrameId()) ?: undoStackLegacy.size
    }

    /**
     * Get redo count for current frame
     */
    @Suppress("unused")
    fun getRedoCount(): Int {
        return historyManager?.getRedoCount(getCurrentFrameId()) ?: redoStackLegacy.size
    }

    /**
     * Save history to persistent storage
     */
    fun saveHistoryToStorage() {
        historyManager?.saveToStorage()
    }

    /**
     * Load history from persistent storage
     */
    fun loadHistoryFromStorage() {
        historyManager?.loadFromStorage()
        notifyHistoryChanged()
    }

    // ========== PROJECT-LEVEL SNAPSHOT METHODS ==========

    /**
     * Save a snapshot of the entire project state before a major operation.
     */
    private fun saveProjectSnapshot(changeType: ProjectChangeType, description: String) {
        saveCurrentFrameData()

        val frameSnapshots = frames.map { frame ->
            FrameSnapshot(
                id = frame.id,
                pixelData = frame.pixelData.clone(),
                duration = frame.duration
            )
        }

        val snapshot = ProjectSnapshot(
            changeType = changeType,
            description = description,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            frameDataList = frameSnapshots,
            currentFrameIndex = currentFrameIndex,
            viewMode = viewMode
        )

        projectSnapshotStack.add(snapshot)

        // Limit stack size
        while (projectSnapshotStack.size > maxProjectSnapshots) {
            projectSnapshotStack.removeAt(0)
        }

        notifyProjectSnapshotChanged()
    }

    /**
     * Undo the last major project operation.
     * @return true if successful
     */
    fun undoProjectChange(): Boolean {
        if (projectSnapshotStack.isEmpty()) return false

        val snapshot = projectSnapshotStack.removeAt(projectSnapshotStack.lastIndex)

        // Restore canvas dimensions
        canvasWidth = snapshot.canvasWidth
        canvasHeight = snapshot.canvasHeight

        // Restore frames
        frames.clear()
        var maxId = 0
        snapshot.frameDataList.forEach { frameSnapshot ->
            val frame = Frame(
                id = frameSnapshot.id,
                pixelData = frameSnapshot.pixelData.clone(),
                duration = frameSnapshot.duration
            )
            frames.add(frame)
            if (frameSnapshot.id > maxId) maxId = frameSnapshot.id
        }
        nextFrameId = maxId + 1

        // Restore current frame
        currentFrameIndex = snapshot.currentFrameIndex.coerceIn(0, frames.lastIndex)
        pixelData = IntArray(canvasWidth * canvasHeight)
        frames[currentFrameIndex].pixelData.copyInto(pixelData)

        // Recreate canvas bitmap
        canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        updateBitmap()

        // Clear cached graphics that depend on canvas size
        checkerboardTile?.recycle()
        checkerboardTile = null
        checkerboardShader.shader = null

        // Clear cached bitmaps
        frames.forEach { it.cachedBitmap = null }

        // Update history manager canvas size
        historyManager?.setCanvasSize(canvasWidth, canvasHeight)

        // Restore view mode
        viewMode = snapshot.viewMode

        // Reset view
        centerCanvas()
        invalidate()

        // Notify callbacks
        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        onViewModeChanged?.invoke(viewMode)
        notifyHistoryChanged()
        notifyProjectSnapshotChanged()
        notifyCanvasModified()

        return true
    }

    /**
     * Check if there's a project-level change that can be undone.
     */
    fun canUndoProjectChange(): Boolean = projectSnapshotStack.isNotEmpty()

    /**
     * Get the description of the last project change that can be undone.
     */
    fun getLastProjectChangeDescription(): String? {
        return projectSnapshotStack.lastOrNull()?.description
    }

    /**
     * Clear all project snapshots (e.g., when starting a new project).
     */
    fun clearProjectSnapshots() {
        projectSnapshotStack.clear()
        notifyProjectSnapshotChanged()
    }

    private fun notifyProjectSnapshotChanged() {
        val canUndo = projectSnapshotStack.isNotEmpty()
        val description = projectSnapshotStack.lastOrNull()?.description
        onProjectSnapshotChanged?.invoke(canUndo, description)
    }

    fun clearCanvas() {
        saveToHistory(ActionType.CLEAR)
        pixelData.fill(Color.TRANSPARENT)
        updateBitmap()
        invalidate()
    }

    @Suppress("unused")
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

    /**
     * Converts screen coordinates to canvas pixel coordinates without boundary checks.
     * Used for color picker to sample colors from reference images and layers
     * that may extend beyond the main canvas bounds.
     */
    private fun screenToPixelUnbounded(screenX: Float, screenY: Float): Pair<Int, Int> {
        val pixelX = ((screenX - offsetX) / zoomLevel).toInt()
        val pixelY = ((screenY - offsetY) / zoomLevel).toInt()
        return Pair(pixelX, pixelY)
    }

    /**
     * Converts screen coordinates to frame-local pixel coordinates in sheet mode.
     * Returns Triple(frameIndex, localX, localY) or null if outside valid area.
     */
    private fun screenToPixelSheet(screenX: Float, screenY: Float): Triple<Int, Int, Int>? {
        val sheetX = (screenX - offsetX) / zoomLevel
        val sheetY = (screenY - offsetY) / zoomLevel
        return sheetCoordsToFramePixel(sheetX, sheetY)
    }

    private fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until canvasWidth && y in 0 until canvasHeight) {
            val index = y * canvasWidth + x
            // Track original color for delta history if tracking is enabled
            if (isTrackingDelta && !strokeChangedPixels.containsKey(index)) {
                strokeChangedPixels[index] = pixelData[index]
            }
            pixelData[index] = color
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

    /**
     * Gets pixel color from a specific layer at canvas coordinates (x, y).
     * Takes into account the layer's offset and scale.
     * Returns Color.TRANSPARENT if the point is outside the layer or the layer is not visible.
     */
    private fun getPixelFromLayer(layer: Layer, x: Int, y: Int): Int {
        if (!layer.visible) return Color.TRANSPARENT

        when (layer.type) {
            LayerType.PIXEL -> {
                val data = layer.pixelData ?: return Color.TRANSPARENT
                val size = sqrt(data.size.toDouble()).toInt()

                // Convert canvas coordinates to layer coordinates
                val layerX = x - layer.offsetX
                val layerY = y - layer.offsetY

                if (layerX in 0 until size && layerY in 0 until size) {
                    return data[layerY * size + layerX]
                }
            }
            LayerType.IMAGE -> {
                val bitmap = layer.imageBitmap ?: return Color.TRANSPARENT
                val scaledWidth = (layer.originalWidth * layer.scale).toInt()
                val scaledHeight = (layer.originalHeight * layer.scale).toInt()

                // Convert canvas coordinates to layer coordinates
                val layerX = x - layer.offsetX
                val layerY = y - layer.offsetY

                if (layerX in 0 until scaledWidth && layerY in 0 until scaledHeight) {
                    // Map to original bitmap coordinates
                    val bitmapX = (layerX / layer.scale).toInt().coerceIn(0, bitmap.width - 1)
                    val bitmapY = (layerY / layer.scale).toInt().coerceIn(0, bitmap.height - 1)
                    return bitmap.getPixel(bitmapX, bitmapY)
                }
            }
        }
        return Color.TRANSPARENT
    }

    /**
     * Gets pixel color from the reference image at canvas coordinates (x, y).
     * Takes into account the reference's offset and scale.
     * Returns Color.TRANSPARENT if the point is outside the reference or reference is not visible.
     */
    private fun getPixelFromReference(x: Int, y: Int): Int {
        if (!referenceVisible) return Color.TRANSPARENT
        val ref = referenceImage ?: return Color.TRANSPARENT

        val scaledWidth = ref.width * referenceScale
        val scaledHeight = ref.height * referenceScale

        // Convert canvas coordinates to reference coordinates
        val refX = x - referenceOffsetX
        val refY = y - referenceOffsetY

        if (refX >= 0 && refX < scaledWidth && refY >= 0 && refY < scaledHeight) {
            // Map to original bitmap coordinates
            val bitmapX = (refX / referenceScale).toInt().coerceIn(0, ref.width - 1)
            val bitmapY = (refY / referenceScale).toInt().coerceIn(0, ref.height - 1)
            return ref.getPixel(bitmapX, bitmapY)
        }
        return Color.TRANSPARENT
    }

    /**
     * Picks a color from all visible sources at canvas coordinates (x, y).
     * Checks in visual order (top to bottom):
     * 1. Layers (from top to bottom)
     * 2. Main canvas (current frame)
     * 3. Reference image
     * Returns the first non-transparent color found, or Color.TRANSPARENT if none.
     */
    private fun pickColorFromAllSources(x: Int, y: Int): Int {
        // Check layers from top to bottom (last to first in the list)
        for (i in layers.indices.reversed()) {
            val color = getPixelFromLayer(layers[i], x, y)
            if (color != Color.TRANSPARENT) {
                return color
            }
        }

        // Check main canvas
        val canvasColor = getPixel(x, y)
        if (canvasColor != Color.TRANSPARENT) {
            return canvasColor
        }

        // Check reference image
        return getPixelFromReference(x, y)
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
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
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

    // Dashed line pixels (alternating segments)
    private fun getDashedLinePixels(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val allPixels = getLinePixels(x0, y0, x1, y1)
        val dashLength = 4  // pixels per dash
        val gapLength = 3   // pixels per gap

        return allPixels.filterIndexed { index, _ ->
            val cyclePosition = index % (dashLength + gapLength)
            cyclePosition < dashLength
        }
    }

    // Rounded rectangle pixels
    private fun getRoundedRectPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val width = maxX - minX
        val height = maxY - minY
        // Corner radius is proportional to the smallest dimension
        val radius = (min(width, height) / 4).coerceAtLeast(1).coerceAtMost(10)

        if (filled) {
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    // Check if point is inside rounded rectangle
                    val inCorner = when {
                        x < minX + radius && y < minY + radius -> {
                            // Top-left corner
                            val dx = x - (minX + radius)
                            val dy = y - (minY + radius)
                            dx * dx + dy * dy <= radius * radius
                        }
                        x > maxX - radius && y < minY + radius -> {
                            // Top-right corner
                            val dx = x - (maxX - radius)
                            val dy = y - (minY + radius)
                            dx * dx + dy * dy <= radius * radius
                        }
                        x < minX + radius && y > maxY - radius -> {
                            // Bottom-left corner
                            val dx = x - (minX + radius)
                            val dy = y - (maxY - radius)
                            dx * dx + dy * dy <= radius * radius
                        }
                        x > maxX - radius && y > maxY - radius -> {
                            // Bottom-right corner
                            val dx = x - (maxX - radius)
                            val dy = y - (maxY - radius)
                            dx * dx + dy * dy <= radius * radius
                        }
                        else -> true // Inside main rectangle
                    }
                    if (inCorner) {
                        pixels.add(Pair(x, y))
                    }
                }
            }
        } else {
            // Draw outline with rounded corners
            // Top edge (excluding corners)
            for (x in (minX + radius)..(maxX - radius)) {
                pixels.add(Pair(x, minY))
            }
            // Bottom edge (excluding corners)
            for (x in (minX + radius)..(maxX - radius)) {
                pixels.add(Pair(x, maxY))
            }
            // Left edge (excluding corners)
            for (y in (minY + radius)..(maxY - radius)) {
                pixels.add(Pair(minX, y))
            }
            // Right edge (excluding corners)
            for (y in (minY + radius)..(maxY - radius)) {
                pixels.add(Pair(maxX, y))
            }

            // Draw corner arcs using circle algorithm
            fun addCornerArc(cx: Int, cy: Int, startAngle: Int, endAngle: Int) {
                for (angle in startAngle until endAngle) {
                    val rad = angle * PI / 180
                    val px = (cx + radius * cos(rad)).roundToInt()
                    val py = (cy + radius * sin(rad)).roundToInt()
                    pixels.add(Pair(px, py))
                }
            }

            // Top-left corner (180° to 270°)
            addCornerArc(minX + radius, minY + radius, 180, 270)
            // Top-right corner (270° to 360°)
            addCornerArc(maxX - radius, minY + radius, 270, 360)
            // Bottom-right corner (0° to 90°)
            addCornerArc(maxX - radius, maxY - radius, 0, 90)
            // Bottom-left corner (90° to 180°)
            addCornerArc(minX + radius, maxY - radius, 90, 180)
        }

        return pixels.distinct()
    }

    /**
     * Quadratic Bezier curve pixels.
     * Uses De Casteljau's algorithm to generate smooth curve points.
     * @param x0, y0 - Start point
     * @param cx, cy - Control point (the bend point)
     * @param x1, y1 - End point
     */
    private fun getBezierPixels(x0: Int, y0: Int, cx: Float, cy: Float, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()

        // Calculate approximate curve length to determine step count
        val dist1 = sqrt((cx - x0).pow(2) + (cy - y0).pow(2))
        val dist2 = sqrt((x1 - cx).pow(2) + (y1 - cy).pow(2))
        val steps = max(((dist1 + dist2) * 2).toInt(), 20)

        var lastX = Int.MIN_VALUE
        var lastY = Int.MIN_VALUE

        for (i in 0..steps) {
            val t = i.toFloat() / steps

            // Quadratic Bezier formula: B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
            val oneMinusT = 1 - t
            val px = oneMinusT * oneMinusT * x0 + 2 * oneMinusT * t * cx + t * t * x1
            val py = oneMinusT * oneMinusT * y0 + 2 * oneMinusT * t * cy + t * t * y1

            val pixelX = px.roundToInt()
            val pixelY = py.roundToInt()

            // Add pixel if it's different from the last one
            if (pixelX != lastX || pixelY != lastY) {
                pixels.add(Pair(pixelX, pixelY))
                lastX = pixelX
                lastY = pixelY
            }
        }

        // Fill gaps using Bresenham between consecutive points
        val filledPixels = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until pixels.size - 1) {
            val (px1, py1) = pixels[i]
            val (px2, py2) = pixels[i + 1]
            if (abs(px2 - px1) > 1 || abs(py2 - py1) > 1) {
                filledPixels.addAll(getLinePixels(px1, py1, px2, py2))
            } else {
                filledPixels.add(pixels[i])
            }
        }
        if (pixels.isNotEmpty()) {
            filledPixels.add(pixels.last())
        }

        return filledPixels.distinct()
    }

    /**
     * Recalculates the pending shape pixels based on the current bend control point.
     * For lines, uses Bezier curves.
     * For other shapes, uses point deformation.
     */
    private fun recalculateBentShape() {
        if (!hasPendingShape) return

        pendingShapePixels.clear()

        if (pendingShapeTool == Tool.LINE && deformationPoints.isEmpty()) {
            // Legacy Bezier approach for lines without deformation points
            val basePixels = getBezierPixels(
                pendingShapeStartX, pendingShapeStartY,
                bendControlX, bendControlY,
                pendingShapeEndX, pendingShapeEndY
            )
            pendingShapePixels.addAll(expandShapePixels(basePixels))
        } else {
            // Apply deformation to all shapes
            val deformedPixels = applyDeformation(originalShapePixels)
            pendingShapePixels.addAll(expandShapePixels(deformedPixels))
        }
        invalidate()
    }

    /**
     * Applies all deformation points to the given pixels.
     * Each deformation point influences nearby pixels based on distance.
     */
    private fun applyDeformation(pixels: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (deformationPoints.isEmpty()) return pixels

        return pixels.map { (px, py) ->
            var totalDx = 0f
            var totalDy = 0f
            var totalWeight = 0f

            for (point in deformationPoints) {
                // Calculate distance from pixel to deformation anchor
                val dx = px - point.anchorX
                val dy = py - point.anchorY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance < point.influence) {
                    // Smooth falloff using cosine interpolation
                    val t = distance / point.influence
                    val weight = (1 + cos(PI * t)).toFloat() / 2f  // Smooth 0 to 1 to 0

                    totalDx += point.offsetX * weight
                    totalDy += point.offsetY * weight
                    totalWeight += weight
                }
            }

            // Apply weighted deformation
            val newX = px + totalDx.roundToInt()
            val newY = py + totalDy.roundToInt()
            Pair(newX, newY)
        }.distinct()
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

    // Triangle pixels (equilateral-ish triangle based on bounding box)
    private fun getTrianglePixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        // Triangle points: top-center, bottom-left, bottom-right
        val topX = (minX + maxX) / 2
        val topY = minY
        val bottomLeftX = minX
        val bottomLeftY = maxY
        val bottomRightX = maxX
        val bottomRightY = maxY

        if (filled) {
            // Fill triangle using scanline
            for (y in minY..maxY) {
                val progress = if (maxY == minY) 1f else (y - minY).toFloat() / (maxY - minY)
                val leftX = (topX + (bottomLeftX - topX) * progress).roundToInt()
                val rightX = (topX + (bottomRightX - topX) * progress).roundToInt()
                for (x in min(leftX, rightX)..max(leftX, rightX)) {
                    pixels.add(Pair(x, y))
                }
            }
        } else {
            // Draw three edges
            pixels.addAll(getLinePixels(topX, topY, bottomLeftX, bottomLeftY))
            pixels.addAll(getLinePixels(bottomLeftX, bottomLeftY, bottomRightX, bottomRightY))
            pixels.addAll(getLinePixels(bottomRightX, bottomRightY, topX, topY))
        }
        return pixels.distinct()
    }

    // Oval/Ellipse pixels using midpoint ellipse algorithm
    private fun getOvalPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val a = (maxX - minX) / 2  // horizontal radius
        val b = (maxY - minY) / 2  // vertical radius

        if (a == 0 && b == 0) {
            pixels.add(Pair(cx, cy))
            return pixels
        }

        if (filled) {
            // Filled ellipse
            for (y in -b..b) {
                val halfWidth = if (b == 0) a else (a * sqrt(1.0 - (y.toDouble() * y) / (b.toDouble() * b))).roundToInt()
                for (x in -halfWidth..halfWidth) {
                    pixels.add(Pair(cx + x, cy + y))
                }
            }
        } else {
            // Ellipse outline using parametric form
            val steps = max(4 * (a + b), 32)
            var lastX = Int.MIN_VALUE
            var lastY = Int.MIN_VALUE

            for (i in 0..steps) {
                val angle = 2 * PI * i / steps
                val px = (cx + a * cos(angle)).roundToInt()
                val py = (cy + b * sin(angle)).roundToInt()

                if (px != lastX || py != lastY) {
                    if (lastX != Int.MIN_VALUE) {
                        // Connect with line to avoid gaps
                        if (abs(px - lastX) > 1 || abs(py - lastY) > 1) {
                            pixels.addAll(getLinePixels(lastX, lastY, px, py))
                        } else {
                            pixels.add(Pair(px, py))
                        }
                    } else {
                        pixels.add(Pair(px, py))
                    }
                    lastX = px
                    lastY = py
                }
            }
        }
        return pixels.distinct()
    }

    // Hexagon pixels (regular hexagon based on bounding box)
    private fun getHexagonPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f  // horizontal radius
        val ry = (maxY - minY) / 2f  // vertical radius

        if (rx < 1 && ry < 1) {
            pixels.add(Pair(cx.roundToInt(), cy.roundToInt()))
            return pixels
        }

        // Calculate 6 vertices of hexagon
        val vertices = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 6) {
            val angle = PI / 6 + i * PI / 3  // Start from 30 degrees for flat-top hexagon
            val vx = (cx + rx * cos(angle)).roundToInt()
            val vy = (cy + ry * sin(angle)).roundToInt()
            vertices.add(Pair(vx, vy))
        }

        if (filled) {
            // Fill hexagon using scanline
            for (y in minY..maxY) {
                val intersections = mutableListOf<Int>()
                for (i in 0 until 6) {
                    val (x1v, y1v) = vertices[i]
                    val (x2v, y2v) = vertices[(i + 1) % 6]

                    if ((y1v <= y && y2v > y) || (y2v <= y && y1v > y)) {
                        val t = (y - y1v).toFloat() / (y2v - y1v)
                        val xIntersect = (x1v + t * (x2v - x1v)).roundToInt()
                        intersections.add(xIntersect)
                    }
                }
                intersections.sort()
                for (j in 0 until intersections.size - 1 step 2) {
                    for (x in intersections[j]..intersections[j + 1]) {
                        pixels.add(Pair(x, y))
                    }
                }
            }
        } else {
            // Draw 6 edges
            for (i in 0 until 6) {
                val (x1v, y1v) = vertices[i]
                val (x2v, y2v) = vertices[(i + 1) % 6]
                pixels.addAll(getLinePixels(x1v, y1v, x2v, y2v))
            }
        }
        return pixels.distinct()
    }

    // Diamond pixels (losange - rotated square)
    private fun getDiamondPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2

        // 4 vertices of diamond (top, right, bottom, left)
        val top = Pair(cx, minY)
        val right = Pair(maxX, cy)
        val bottom = Pair(cx, maxY)
        val left = Pair(minX, cy)

        if (filled) {
            // Fill diamond using scanline
            for (y in minY..maxY) {
                val halfWidth = if (y <= cy) {
                    ((y - minY).toFloat() / (cy - minY + 1) * (maxX - cx)).toInt()
                } else {
                    ((maxY - y).toFloat() / (maxY - cy + 1) * (maxX - cx)).toInt()
                }
                for (x in (cx - halfWidth)..(cx + halfWidth)) {
                    pixels.add(Pair(x, y))
                }
            }
        } else {
            // Draw 4 edges
            pixels.addAll(getLinePixels(top.first, top.second, right.first, right.second))
            pixels.addAll(getLinePixels(right.first, right.second, bottom.first, bottom.second))
            pixels.addAll(getLinePixels(bottom.first, bottom.second, left.first, left.second))
            pixels.addAll(getLinePixels(left.first, left.second, top.first, top.second))
        }
        return pixels.distinct()
    }

    // Star pixels (5-pointed star)
    private fun getStarPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val outerRadius = min(maxX - minX, maxY - minY) / 2f
        val innerRadius = outerRadius * 0.4f

        if (outerRadius < 2) {
            pixels.add(Pair(cx.roundToInt(), cy.roundToInt()))
            return pixels
        }

        // Calculate 10 vertices (alternating outer and inner points)
        val vertices = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 10) {
            val angle = -PI / 2 + i * PI / 5  // Start from top
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val vx = (cx + radius * cos(angle)).roundToInt()
            val vy = (cy + radius * sin(angle)).roundToInt()
            vertices.add(Pair(vx, vy))
        }

        if (filled) {
            // Fill star using scanline
            for (y in minY..maxY) {
                val intersections = mutableListOf<Int>()
                for (i in 0 until 10) {
                    val (x1v, y1v) = vertices[i]
                    val (x2v, y2v) = vertices[(i + 1) % 10]

                    if ((y1v <= y && y2v > y) || (y2v <= y && y1v > y)) {
                        val t = (y - y1v).toFloat() / (y2v - y1v)
                        val xIntersect = (x1v + t * (x2v - x1v)).roundToInt()
                        intersections.add(xIntersect)
                    }
                }
                intersections.sort()
                for (j in 0 until intersections.size - 1 step 2) {
                    for (x in intersections[j]..intersections[j + 1]) {
                        pixels.add(Pair(x, y))
                    }
                }
            }
        } else {
            // Draw 10 edges
            for (i in 0 until 10) {
                val (x1v, y1v) = vertices[i]
                val (x2v, y2v) = vertices[(i + 1) % 10]
                pixels.addAll(getLinePixels(x1v, y1v, x2v, y2v))
            }
        }
        return pixels.distinct()
    }

    // Arrow pixels (pointing right by default, based on direction from start to end)
    private fun getArrowPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()

        // Line from start to end
        pixels.addAll(getLinePixels(x0, y0, x1, y1))

        // Calculate arrow head
        val dx = x1 - x0
        val dy = y1 - y0
        val length = sqrt((dx * dx + dy * dy).toDouble())

        if (length < 3) return pixels

        val headLength = (length * 0.3).coerceAtLeast(5.0).coerceAtMost(20.0)
        val headWidth = headLength * 0.6

        // Normalize direction
        val dirX = dx / length
        val dirY = dy / length

        // Perpendicular direction
        val perpX = -dirY
        val perpY = dirX

        // Arrow head points
        val headBase = Pair(
            (x1 - dirX * headLength).roundToInt(),
            (y1 - dirY * headLength).roundToInt()
        )
        val headLeft = Pair(
            (headBase.first + perpX * headWidth / 2).roundToInt(),
            (headBase.second + perpY * headWidth / 2).roundToInt()
        )
        val headRight = Pair(
            (headBase.first - perpX * headWidth / 2).roundToInt(),
            (headBase.second - perpY * headWidth / 2).roundToInt()
        )

        if (filled) {
            // Fill arrow head triangle
            val headVertices = listOf(Pair(x1, y1), headLeft, headRight)
            val minHY = headVertices.minOf { it.second }
            val maxHY = headVertices.maxOf { it.second }
            for (y in minHY..maxHY) {
                val intersections = mutableListOf<Int>()
                for (i in 0 until 3) {
                    val (x1v, y1v) = headVertices[i]
                    val (x2v, y2v) = headVertices[(i + 1) % 3]
                    if ((y1v <= y && y2v > y) || (y2v <= y && y1v > y)) {
                        val t = (y - y1v).toFloat() / (y2v - y1v)
                        val xIntersect = (x1v + t * (x2v - x1v)).roundToInt()
                        intersections.add(xIntersect)
                    }
                }
                intersections.sort()
                for (j in 0 until intersections.size - 1 step 2) {
                    for (x in intersections[j]..intersections[j + 1]) {
                        pixels.add(Pair(x, y))
                    }
                }
            }
        } else {
            // Draw arrow head lines
            pixels.addAll(getLinePixels(x1, y1, headLeft.first, headLeft.second))
            pixels.addAll(getLinePixels(x1, y1, headRight.first, headRight.second))
        }

        return pixels.distinct()
    }

    // Pentagon pixels (5-sided regular polygon)
    private fun getPentagonPixels(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        val minX = min(x0, x1)
        val maxX = max(x0, x1)
        val minY = min(y0, y1)
        val maxY = max(y0, y1)

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f
        val ry = (maxY - minY) / 2f

        if (rx < 1 && ry < 1) {
            pixels.add(Pair(cx.roundToInt(), cy.roundToInt()))
            return pixels
        }

        // Calculate 5 vertices of pentagon
        val vertices = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 5) {
            val angle = -PI / 2 + i * 2 * PI / 5  // Start from top
            val vx = (cx + rx * cos(angle)).roundToInt()
            val vy = (cy + ry * sin(angle)).roundToInt()
            vertices.add(Pair(vx, vy))
        }

        if (filled) {
            // Fill pentagon using scanline
            for (y in minY..maxY) {
                val intersections = mutableListOf<Int>()
                for (i in 0 until 5) {
                    val (x1v, y1v) = vertices[i]
                    val (x2v, y2v) = vertices[(i + 1) % 5]

                    if ((y1v <= y && y2v > y) || (y2v <= y && y1v > y)) {
                        val t = (y - y1v).toFloat() / (y2v - y1v)
                        val xIntersect = (x1v + t * (x2v - x1v)).roundToInt()
                        intersections.add(xIntersect)
                    }
                }
                intersections.sort()
                for (j in 0 until intersections.size - 1 step 2) {
                    for (x in intersections[j]..intersections[j + 1]) {
                        pixels.add(Pair(x, y))
                    }
                }
            }
        } else {
            // Draw 5 edges
            for (i in 0 until 5) {
                val (x1v, y1v) = vertices[i]
                val (x2v, y2v) = vertices[(i + 1) % 5]
                pixels.addAll(getLinePixels(x1v, y1v, x2v, y2v))
            }
        }
        return pixels.distinct()
    }

    /**
     * Expands shape pixels based on the current stroke style and brush size.
     * For PENCIL: returns pixels as-is (1px stroke)
     * For MARKER/BRUSH: expands each pixel into a filled circle of brushSize
     */
    private fun expandShapePixels(pixels: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (shapeFilled || shapeStrokeStyle == ShapeStrokeStyle.PENCIL || brushSize <= 1) {
            return pixels
        }

        val expandedPixels = mutableSetOf<Pair<Int, Int>>()
        val radius = brushSize / 2

        for ((cx, cy) in pixels) {
            // Add pixels in a circular area around each point
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        expandedPixels.add(Pair(cx + dx, cy + dy))
                    }
                }
            }
        }

        return expandedPixels.toList()
    }

    private fun updateShapePreview() {
        previewPixels.clear()
        if (!isDrawingShape || shapeStartX < 0 || shapeEndX < 0) return

        val basePixels = when (currentTool) {
            Tool.LINE -> getLinePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY)
            Tool.DASHED_LINE -> getDashedLinePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY)
            Tool.RECTANGLE -> getRectanglePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.ROUNDED_RECT -> getRoundedRectPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.CIRCLE -> getCirclePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.TRIANGLE -> getTrianglePixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.OVAL -> getOvalPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.HEXAGON -> getHexagonPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.DIAMOND -> getDiamondPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.STAR -> getStarPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.ARROW -> getArrowPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            Tool.PENTAGON -> getPentagonPixels(shapeStartX, shapeStartY, shapeEndX, shapeEndY, shapeFilled)
            else -> emptyList()
        }

        previewPixels.addAll(expandShapePixels(basePixels))
    }

    private fun commitShape() {
        if (previewPixels.isEmpty()) return

        // Save endpoints for bend functionality (before resetting)
        pendingShapeStartX = shapeStartX
        pendingShapeStartY = shapeStartY
        pendingShapeEndX = shapeEndX
        pendingShapeEndY = shapeEndY

        // Initialize bend control point at the midpoint of the line
        bendControlX = (shapeStartX + shapeEndX) / 2f
        bendControlY = (shapeStartY + shapeEndY) / 2f
        isBendingShape = false

        // Instead of applying directly, put shape in pending mode for repositioning
        pendingShapePixels.clear()
        pendingShapePixels.addAll(previewPixels)
        pendingShapeTool = currentTool
        pendingShapeOffsetX = 0
        pendingShapeOffsetY = 0
        hasPendingShape = true

        // Store original pixels for deformation
        originalShapePixels.clear()
        originalShapePixels.addAll(previewPixels)
        deformationPoints.clear()
        currentDeformationPoint = null

        // Start marching ants animation
        marchingAntsHandler.removeCallbacks(marchingAntsRunnable)
        marchingAntsHandler.post(marchingAntsRunnable)

        // Notify UI that pending shape state changed
        onPendingShapeChanged?.invoke(true)

        previewPixels.clear()
        isDrawingShape = false
        shapeStartX = -1
        shapeStartY = -1
        shapeEndX = -1
        shapeEndY = -1
    }

    /**
     * Finalizes the pending shape by applying it to the canvas at its current position.
     * Called when user switches to a different tool.
     */
    fun finalizePendingShape() {
        if (!hasPendingShape || pendingShapePixels.isEmpty()) return

        for ((x, y) in pendingShapePixels) {
            val finalX = x + pendingShapeOffsetX
            val finalY = y + pendingShapeOffsetY
            setPixel(finalX, finalY, primaryColor)
        }

        clearPendingShape()
        notifyCanvasModified()
    }

    /**
     * Cancels the pending shape without applying it.
     * Ne fait PAS de undo - la forme pending est juste un overlay, pas sur le canvas.
     */
    fun cancelPendingShape() {
        if (!hasPendingShape) return
        clearPendingShape()
    }

    /**
     * Clears pending shape state.
     */
    private fun clearPendingShape() {
        hasPendingShape = false
        pendingShapePixels.clear()
        pendingShapeTool = null
        pendingShapeOffsetX = 0
        pendingShapeOffsetY = 0
        isDraggingPendingShape = false
        isBendingShape = false
        // Clear deformation state
        deformationPoints.clear()
        originalShapePixels.clear()
        currentDeformationPoint = null
        marchingAntsHandler.removeCallbacks(marchingAntsRunnable)
        onPendingShapeChanged?.invoke(false)
        invalidate()
    }

    /**
     * Returns true if there's a pending shape that can be moved.
     */
    fun hasPendingShapeToMove(): Boolean = hasPendingShape

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

        // Dispatch to sheet mode rendering if active
        if (viewMode == ViewMode.SHEET) {
            drawSheetMode(canvas)
            canvas.restore()
            return
        }

        // Draw reference image BEHIND everything (if set)
        drawReferenceImage(canvas)

        // Get visible bounds for viewport culling (optimization for large canvases)
        val visibleBounds = getVisibleCanvasBounds()

        // Draw transparency checkerboard - heavily optimized based on zoom level
        val pixelScreenSize = zoomLevel // Size of one canvas pixel on screen

        if (pixelScreenSize >= 2f && canvasWidth <= 512 && canvasHeight <= 512) {
            // High zoom + small canvas: draw detailed checkerboard using tile shader
            if (checkerboardTile == null) {
                checkerboardTile = createCheckerboardTile()
                checkerboardShader.shader = BitmapShader(
                    checkerboardTile!!,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT
                )
            }
            updateCheckerboardAlpha()
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerboardShader)
        } else if (pixelScreenSize >= 4f) {
            // Medium-high zoom + large canvas: draw checkerboard only in visible area
            val checkerSize = 4
            updateCheckerboardAlpha()
            val startBlockX = (visibleBounds.left / checkerSize).coerceAtLeast(0)
            val endBlockX = ((visibleBounds.right + checkerSize - 1) / checkerSize).coerceAtMost((canvasWidth + checkerSize - 1) / checkerSize)
            val startBlockY = (visibleBounds.top / checkerSize).coerceAtLeast(0)
            val endBlockY = ((visibleBounds.bottom + checkerSize - 1) / checkerSize).coerceAtMost((canvasHeight + checkerSize - 1) / checkerSize)

            for (blockY in startBlockY until endBlockY) {
                for (blockX in startBlockX until endBlockX) {
                    val paint = if ((blockX + blockY) % 2 == 0) checkerPaint1 else checkerPaint2
                    val x1 = (blockX * checkerSize).toFloat()
                    val y1 = (blockY * checkerSize).toFloat()
                    val x2 = minOf((blockX + 1) * checkerSize, canvasWidth).toFloat()
                    val y2 = minOf((blockY + 1) * checkerSize, canvasHeight).toFloat()
                    canvas.drawRect(x1, y1, x2, y2, paint)
                }
            }
        } else {
            // Low zoom: draw a single solid color (no allocation, use pre-allocated paint)
            updateCheckerboardAlpha()
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerAvgPaint)
        }

        // Draw onion skin - optimized: reuse paint, avoid bitmap allocation when possible
        if (onionSkinFrames.isNotEmpty() && !isPlaying) {
            onionPaint.alpha = (onionSkinOpacity * 255).toInt().coerceIn(0, 255)

            // Draw each frame in the onion skin set (sorted for consistent layering)
            for (frameIndex in onionSkinFrames.sorted()) {
                if (frameIndex != currentFrameIndex && frameIndex in 0 until frames.size) {
                    val frame = frames[frameIndex]
                    // Reuse bitmap if already cached, otherwise create one
                    if (frame.cachedBitmap == null || frame.cachedBitmap?.isRecycled == true) {
                        frame.cachedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                        frame.cachedBitmap?.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
                    }
                    frame.cachedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, onionPaint) }
                }
            }
        }

        // Draw the pixel art (current frame)
        canvas.drawBitmap(canvasBitmap, 0f, 0f, bitmapPaint)

        // Draw all visible layers
        drawLayers(canvas)

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

        // Draw pending shape (waiting to be finalized, can be moved)
        if (hasPendingShape && pendingShapePixels.isNotEmpty()) {
            // Draw the shape pixels with the current offset
            previewPaint.color = primaryColor
            for ((px, py) in pendingShapePixels) {
                val drawX = px + pendingShapeOffsetX
                val drawY = py + pendingShapeOffsetY
                canvas.drawRect(
                    drawX.toFloat(), drawY.toFloat(),
                    (drawX + 1).toFloat(), (drawY + 1).toFloat(),
                    previewPaint
                )
            }

            // Calculate bounding box of the pending shape
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            for ((px, py) in pendingShapePixels) {
                val drawX = px + pendingShapeOffsetX
                val drawY = py + pendingShapeOffsetY
                if (drawX < minX) minX = drawX
                if (drawY < minY) minY = drawY
                if (drawX > maxX) maxX = drawX
                if (drawY > maxY) maxY = drawY
            }

            // Draw marching ants border (animated black/white dashed line)
            val borderLeft = minX.toFloat() - 0.1f
            val borderTop = minY.toFloat() - 0.1f
            val borderRight = maxX.toFloat() + 1.1f
            val borderBottom = maxY.toFloat() + 1.1f

            // Stroke width that's thin but visible
            val strokeWidth = 2f / zoomLevel

            // White dashed line (background)
            val whitePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = false
            }
            canvas.drawRect(borderLeft, borderTop, borderRight, borderBottom, whitePaint)

            // Black dashed line (foreground, animated)
            val blackPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                pathEffect = DashPathEffect(
                    floatArrayOf(4f / zoomLevel, 4f / zoomLevel),
                    marchingAntsPhase / zoomLevel
                )
                isAntiAlias = false
            }
            canvas.drawRect(borderLeft, borderTop, borderRight, borderBottom, blackPaint)

            // Draw bend control point if bend mode is enabled and it's a line
            if (isBendModeEnabled && pendingShapeTool == Tool.LINE) {
                val controlX = bendControlX + pendingShapeOffsetX
                val controlY = bendControlY + pendingShapeOffsetY
                val handleRadius = 4f / zoomLevel

                // Draw control point handle (green circle with white border)
                val handleFillPaint = Paint().apply {
                    color = Color.parseColor("#4CAF50")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val handleStrokePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    this.strokeWidth = 1.5f / zoomLevel
                    isAntiAlias = true
                }
                canvas.drawCircle(controlX, controlY, handleRadius, handleFillPaint)
                canvas.drawCircle(controlX, controlY, handleRadius, handleStrokePaint)

                // Draw lines from endpoints to control point (to visualize the Bezier)
                val guidePaint = Paint().apply {
                    color = Color.parseColor("#4CAF5080")  // Semi-transparent green
                    style = Paint.Style.STROKE
                    this.strokeWidth = 1f / zoomLevel
                    pathEffect = DashPathEffect(floatArrayOf(3f / zoomLevel, 3f / zoomLevel), 0f)
                    isAntiAlias = true
                }
                val startX = pendingShapeStartX.toFloat() + pendingShapeOffsetX
                val startY = pendingShapeStartY.toFloat() + pendingShapeOffsetY
                val endX = pendingShapeEndX.toFloat() + pendingShapeOffsetX
                val endY = pendingShapeEndY.toFloat() + pendingShapeOffsetY
                canvas.drawLine(startX, startY, controlX, controlY, guidePaint)
                canvas.drawLine(controlX, controlY, endX, endY, guidePaint)
            }
        }

        // Draw grid - thin lines that stay 1 screen pixel regardless of zoom
        if (showGrid && zoomLevel >= 3f) {
            // Adjust stroke width so lines are always ~1 pixel on screen
            gridPaint.strokeWidth = 1f / zoomLevel

            // Draw vertical lines
            for (x in 0..canvasWidth) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), canvasHeight.toFloat(), gridPaint)
            }
            // Draw horizontal lines
            for (y in 0..canvasHeight) {
                canvas.drawLine(0f, y.toFloat(), canvasWidth.toFloat(), y.toFloat(), gridPaint)
            }
        }

        // Draw selection rectangle
        if (hasSelection || isSelectingArea) {
            val minX = min(selectionStartX, selectionEndX).toFloat()
            val maxX = max(selectionStartX, selectionEndX).toFloat() + 1f
            val minY = min(selectionStartY, selectionEndY).toFloat()
            val maxY = max(selectionStartY, selectionEndY).toFloat() + 1f

            // Fill the selected area
            canvas.drawRect(minX, minY, maxX, maxY, selectionFillPaint)
        }

        // Draw move selection preview (floating selection being dragged)
        if (isMovingSelection && moveSelectionData != null) {
            val previewPaint = Paint().apply {
                alpha = 200  // Slightly transparent to show it's being moved
            }
            for (y in 0 until moveSelectionHeight) {
                for (x in 0 until moveSelectionWidth) {
                    val color = moveSelectionData!![y * moveSelectionWidth + x]
                    if (color != Color.TRANSPARENT) {
                        val drawX = (moveSelectionCurrentX + x).toFloat()
                        val drawY = (moveSelectionCurrentY + y).toFloat()
                        previewPaint.color = color
                        canvas.drawRect(drawX, drawY, drawX + 1f, drawY + 1f, previewPaint)
                    }
                }
            }
            // Draw selection border around the moving content
            val moveMinX = moveSelectionCurrentX.toFloat()
            val moveMaxX = (moveSelectionCurrentX + moveSelectionWidth).toFloat()
            val moveMinY = moveSelectionCurrentY.toFloat()
            val moveMaxY = (moveSelectionCurrentY + moveSelectionHeight).toFloat()
            canvas.drawRect(moveMinX, moveMinY, moveMaxX, moveMaxY, selectionFillPaint)
        }

        canvas.restore()

        // Draw selection border OUTSIDE the canvas transform (in screen space)
        // This way the dashed line width stays constant regardless of zoom
        if (hasSelection || isSelectingArea) {
            val minX = min(selectionStartX, selectionEndX).toFloat()
            val maxX = max(selectionStartX, selectionEndX).toFloat() + 1f
            val minY = min(selectionStartY, selectionEndY).toFloat()
            val maxY = max(selectionStartY, selectionEndY).toFloat() + 1f

            // Convert to screen coordinates - border on outer edge of selection
            val screenLeft = offsetX + minX * zoomLevel
            val screenTop = offsetY + minY * zoomLevel
            val screenRight = offsetX + maxX * zoomLevel
            val screenBottom = offsetY + maxY * zoomLevel

            // Draw black base line first, then white dashed on top (marching ants)
            canvas.drawRect(screenLeft, screenTop, screenRight, screenBottom, selectionPaintBlack)
            canvas.drawRect(screenLeft, screenTop, screenRight, screenBottom, selectionPaintWhite)
        }

        // Draw layer bounds and handles when MOVE_LAYER tool is active
        drawLayerBounds(canvas)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle reference adjustment mode first
        if (isAdjustingReference && handleReferenceTouch(event)) {
            return true
        }

        scaleDetector.onTouchEvent(event)

        // Handle sheet mode touch separately
        if (viewMode == ViewMode.SHEET) {
            return handleSheetModeTouch(event)
        }

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

                // Handle MOVE_LAYER tool first
                if (currentTool == Tool.MOVE_LAYER) {
                    if (handleLayerTouch(event)) {
                        return true
                    }
                }

                // Handle MOVE_SELECTION mode
                if (currentTool == Tool.MOVE_SELECTION && isMovingSelection) {
                    moveStartTouchX = event.x
                    moveStartTouchY = event.y
                    return true
                }

                // Handle pending shape movement or bending
                if (hasPendingShape && currentTool == pendingShapeTool) {
                    if (isBendModeEnabled) {
                        // Start deformation - create a new deformation point at touch position
                        val canvasX = (event.x - offsetX) / zoomLevel
                        val canvasY = (event.y - offsetY) / zoomLevel

                        // Calculate influence radius based on shape size
                        val influence = maxOf(
                            abs(pendingShapeEndX - pendingShapeStartX),
                            abs(pendingShapeEndY - pendingShapeStartY),
                            20
                        ).toFloat()

                        currentDeformationPoint = DeformationPoint(
                            anchorX = canvasX,
                            anchorY = canvasY,
                            offsetX = 0f,
                            offsetY = 0f,
                            influence = influence
                        )
                        deformationPoints.add(currentDeformationPoint!!)
                        isBendingShape = true
                        bendDragStartX = event.x
                        bendDragStartY = event.y

                        // For lines, also update legacy bend control for backwards compatibility
                        if (pendingShapeTool == Tool.LINE) {
                            bendControlX = canvasX
                            bendControlY = canvasY
                        }
                    } else {
                        // Start dragging the pending shape
                        isDraggingPendingShape = true
                        pendingShapeDragStartX = event.x
                        pendingShapeDragStartY = event.y
                        pendingShapeOriginalOffsetX = pendingShapeOffsetX
                        pendingShapeOriginalOffsetY = pendingShapeOffsetY
                    }
                    return true
                }

                val pixel = screenToPixel(event.x, event.y)
                if (pixel != null) {
                    // For shape tools, start immediately (they need drag)
                    if (currentTool in listOf(Tool.LINE, Tool.DASHED_LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.TRIANGLE, Tool.OVAL, Tool.HEXAGON, Tool.DIAMOND, Tool.STAR, Tool.ARROW, Tool.PENTAGON)) {
                        // If there's a pending shape from a different tool, finalize it first
                        if (hasPendingShape && pendingShapeTool != currentTool) {
                            finalizePendingShape()
                        }
                        saveToHistory()
                        isDrawingShape = true
                        shapeStartX = pixel.first
                        shapeStartY = pixel.second
                        shapeEndX = pixel.first
                        shapeEndY = pixel.second
                        updateShapePreview()
                        invalidate()
                    } else if (currentTool == Tool.SELECT) {
                        // Start selection
                        isSelectingArea = true
                        selectionStartX = pixel.first
                        selectionStartY = pixel.second
                        selectionEndX = pixel.first
                        selectionEndY = pixel.second
                        hasSelection = false
                        invalidate()
                    } else {
                        // For pencil/eraser: store pending position, draw on release or move
                        pendingDrawX = pixel.first
                        pendingDrawY = pixel.second
                    }
                } else if (currentTool == Tool.PICKER) {
                    // PICKER can work outside the canvas grid (for reference/layers)
                    val unboundedPixel = screenToPixelUnbounded(event.x, event.y)
                    pendingDrawX = unboundedPixel.first
                    pendingDrawY = unboundedPixel.second
                    hasPendingPickerPosition = true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down - this is now a pan/zoom gesture
                wasMultiTouch = true
                isPanning = true
                isDrawing = false
                pendingDrawX = -1
                pendingDrawY = -1
                hasPendingPickerPosition = false

                // Revert any pixels drawn by the first finger before second finger was added
                // This handles the case where user started drawing then decided to zoom/pan
                revertAndCancelStroke()

                // Cancel shape drawing if user puts second finger
                if (isDrawingShape) {
                    isDrawingShape = false
                    previewPixels.clear()
                    // Undo the history save since we're canceling
                    // Note: With historyManager, we just undo the last action
                    undo()
                    invalidate()
                }

                // Cancel selection if user puts second finger
                if (isSelectingArea) {
                    isSelectingArea = false
                    selectionStartX = -1
                    selectionStartY = -1
                    selectionEndX = -1
                    selectionEndY = -1
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
                // Handle MOVE_LAYER tool
                if (currentTool == Tool.MOVE_LAYER && (isMovingLayer || isResizingLayer)) {
                    if (handleLayerTouch(event)) {
                        return true
                    }
                }

                // Handle MOVE_SELECTION mode
                if (currentTool == Tool.MOVE_SELECTION && isMovingSelection && pointerCount == 1) {
                    val deltaScreenX = event.x - moveStartTouchX
                    val deltaScreenY = event.y - moveStartTouchY
                    // Convert screen delta to pixel delta
                    val deltaPixelX = (deltaScreenX / zoomLevel).toInt()
                    val deltaPixelY = (deltaScreenY / zoomLevel).toInt()
                    updateMoveSelectionPosition(deltaPixelX, deltaPixelY)
                    return true
                }

                // Handle pending shape dragging
                if (isDraggingPendingShape && pointerCount == 1) {
                    val deltaX = ((event.x - pendingShapeDragStartX) / zoomLevel).toInt()
                    val deltaY = ((event.y - pendingShapeDragStartY) / zoomLevel).toInt()
                    pendingShapeOffsetX = pendingShapeOriginalOffsetX + deltaX
                    pendingShapeOffsetY = pendingShapeOriginalOffsetY + deltaY
                    invalidate()
                    return true
                }

                // Handle bending (moving control/deformation point)
                if (isBendingShape && pointerCount == 1) {
                    // Calculate offset from original touch position
                    val deltaX = (event.x - bendDragStartX) / zoomLevel
                    val deltaY = (event.y - bendDragStartY) / zoomLevel

                    // Update current deformation point
                    currentDeformationPoint?.let {
                        it.offsetX = deltaX
                        it.offsetY = deltaY
                    }

                    // Also update legacy bend control for lines
                    val canvasX = (event.x - offsetX) / zoomLevel
                    val canvasY = (event.y - offsetY) / zoomLevel
                    bendControlX = canvasX
                    bendControlY = canvasY

                    recalculateBentShape()
                    return true
                }

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
                } else if (isSelectingArea && pointerCount == 1) {
                    val pixel = screenToPixel(event.x, event.y)
                    if (pixel != null) {
                        selectionEndX = pixel.first
                        selectionEndY = pixel.second
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
                // Handle MOVE_LAYER tool
                if (currentTool == Tool.MOVE_LAYER) {
                    handleLayerTouch(event)
                }

                // Handle MOVE_SELECTION mode - keep floating until user clicks "Deselect" button
                if (currentTool == Tool.MOVE_SELECTION && isMovingSelection) {
                    // Don't finalize here - let the user reposition multiple times
                    // Finalization happens when user clicks "Deselect" button (clearSelection)
                    // Update origin to current position so next drag is relative to current position
                    moveSelectionOrigX = moveSelectionCurrentX
                    moveSelectionOrigY = moveSelectionCurrentY
                    // Also update the selection bounds to match current position
                    selectionStartX = moveSelectionCurrentX
                    selectionStartY = moveSelectionCurrentY
                    selectionEndX = moveSelectionCurrentX + moveSelectionWidth - 1
                    selectionEndY = moveSelectionCurrentY + moveSelectionHeight - 1
                    return true
                }

                // Handle pending shape drag end - keep shape in pending state
                if (isDraggingPendingShape) {
                    isDraggingPendingShape = false
                    invalidate()
                    return true
                }

                // Handle bend end - keep shape in pending state with accumulated deformations
                if (isBendingShape) {
                    isBendingShape = false
                    // Clear current deformation point reference (it stays in the list)
                    currentDeformationPoint = null
                    // Update original pixels to include this deformation for next iteration
                    originalShapePixels.clear()
                    originalShapePixels.addAll(pendingShapePixels)
                    // Clear deformation points for clean state (deformation is now baked in)
                    deformationPoints.clear()
                    invalidate()
                    return true
                }

                if (isDrawingShape) {
                    commitShape()
                    invalidate()
                } else if (isSelectingArea) {
                    // Finalize selection
                    isSelectingArea = false
                    // Check if selection is valid (at least 1 pixel)
                    if (selectionStartX >= 0 && selectionEndX >= 0) {
                        hasSelection = true
                        onSelectionChanged?.invoke(true)
                    }
                    invalidate()
                } else if (!wasMultiTouch && (pendingDrawX >= 0 || hasPendingPickerPosition) && !isDrawing) {
                    // Single tap without move - draw single pixel or pick color
                    if (currentTool != Tool.PICKER) {
                        saveToHistory()
                    }
                    handleSingleTap(pendingDrawX, pendingDrawY)
                }

                // Finalize delta tracking for large canvas drawing
                if (isTrackingDelta) {
                    finalizeDeltaTracking()
                }

                // Reset all states
                isPanning = false
                isDrawing = false
                isDrawingShape = false
                isSelectingArea = false
                pendingDrawX = -1
                pendingDrawY = -1
                hasPendingPickerPosition = false
                hasMoved = false
                wasMultiTouch = false
            }

            MotionEvent.ACTION_CANCEL -> {
                // Cancel delta tracking without saving on cancel
                cancelDeltaTracking()
                // Cancel everything without committing
                isDrawingShape = false
                previewPixels.clear()
                isSelectingArea = false
                isPanning = false
                isDrawing = false
                pendingDrawX = -1
                pendingDrawY = -1
                hasPendingPickerPosition = false
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
            Tool.MARKER -> {
                drawMarker(x, y, primaryColor)
                invalidate()
            }
            Tool.BRUSH -> {
                drawBrush(x, y, primaryColor)
                invalidate()
            }
            else -> { }
        }
    }

    /**
     * Draws a filled circle of pixels (marker tool) - 100% opacity
     */
    private fun drawMarker(centerX: Int, centerY: Int, color: Int) {
        val radius = brushSize / 2f
        val radiusSq = radius * radius

        for (dy in -brushSize..brushSize) {
            for (dx in -brushSize..brushSize) {
                val distSq = dx * dx + dy * dy
                if (distSq <= radiusSq) {
                    val px = centerX + dx
                    val py = centerY + dy
                    if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
                        setPixel(px, py, color)
                    }
                }
            }
        }
    }

    /**
     * Draws a soft brush with gradient edges (brush tool)
     * Center pixels are fully opaque, edge pixels are partially transparent
     */
    private fun drawBrush(centerX: Int, centerY: Int, color: Int) {
        val radius = brushSize / 2f
        if (radius < 0.5f) {
            // For very small brush, just draw a single pixel
            setPixel(centerX, centerY, color)
            return
        }

        val colorR = Color.red(color)
        val colorG = Color.green(color)
        val colorB = Color.blue(color)
        val colorA = Color.alpha(color)

        for (dy in -brushSize..brushSize) {
            for (dx in -brushSize..brushSize) {
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= radius) {
                    val px = centerX + dx
                    val py = centerY + dy
                    if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
                        // Calculate opacity based on distance from center (1.0 at center, 0.0 at edge)
                        val opacity = (1f - (dist / radius)).coerceIn(0f, 1f)
                        // Apply a curve for smoother falloff
                        val smoothOpacity = opacity * opacity

                        // Get existing pixel color for blending
                        val existingColor = getPixel(px, py)
                        val existingA = Color.alpha(existingColor)
                        val existingR = Color.red(existingColor)
                        val existingG = Color.green(existingColor)
                        val existingB = Color.blue(existingColor)

                        // Blend the colors based on brush opacity
                        val blendFactor = smoothOpacity * (colorA / 255f)
                        val newA = (existingA + (255 - existingA) * blendFactor).toInt().coerceIn(0, 255)
                        val newR = (existingR + (colorR - existingR) * blendFactor).toInt().coerceIn(0, 255)
                        val newG = (existingG + (colorG - existingG) * blendFactor).toInt().coerceIn(0, 255)
                        val newB = (existingB + (colorB - existingB) * blendFactor).toInt().coerceIn(0, 255)

                        setPixel(px, py, Color.argb(newA, newR, newG, newB))
                    }
                }
            }
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
                // Pick color from all sources: layers, canvas, and reference image
                val pickedColor = pickColorFromAllSources(x, y)
                if (pickedColor != Color.TRANSPARENT) {
                    primaryColor = pickedColor
                    onColorPicked?.invoke(pickedColor)
                }
            }
            Tool.MARKER -> {
                drawMarker(x, y, primaryColor)
                invalidate()
            }
            Tool.BRUSH -> {
                drawBrush(x, y, primaryColor)
                invalidate()
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

    // ========== SELECTION FUNCTIONS ==========

    fun hasActiveSelection(): Boolean = hasSelection

    fun getSelectionBounds(): Rect? {
        if (!hasSelection) return null
        val minX = min(selectionStartX, selectionEndX)
        val maxX = max(selectionStartX, selectionEndX)
        val minY = min(selectionStartY, selectionEndY)
        val maxY = max(selectionStartY, selectionEndY)
        return Rect(minX, minY, maxX, maxY)
    }

    fun clearSelection() {
        // If we're in move mode, finalize the move first (paste the floating selection)
        if (isMovingSelection && moveSelectionData != null) {
            finalizeMoveSelection()
        }

        hasSelection = false
        selectionStartX = -1
        selectionStartY = -1
        selectionEndX = -1
        selectionEndY = -1
        onSelectionChanged?.invoke(false)
        invalidate()
    }

    @Suppress("unused")
    fun selectAll() {
        selectionStartX = 0
        selectionStartY = 0
        selectionEndX = canvasWidth - 1
        selectionEndY = canvasHeight - 1
        hasSelection = true
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    fun copySelection(): Boolean {
        if (!hasSelection) return false

        val bounds = getSelectionBounds() ?: return false
        clipboardWidth = bounds.width() + 1
        clipboardHeight = bounds.height() + 1
        clipboardData = IntArray(clipboardWidth * clipboardHeight)

        for (y in 0 until clipboardHeight) {
            for (x in 0 until clipboardWidth) {
                val srcX = bounds.left + x
                val srcY = bounds.top + y
                clipboardData!![y * clipboardWidth + x] = getPixel(srcX, srcY)
            }
        }
        return true
    }

    fun cutSelection(): Boolean {
        if (!copySelection()) return false
        deleteSelection()
        return true
    }

    fun pasteSelection(): Boolean {
        if (clipboardData == null || clipboardWidth == 0 || clipboardHeight == 0) return false

        saveToHistory(ActionType.PASTE)

        // Paste at current selection start or at center of visible area
        val pasteX = if (hasSelection) min(selectionStartX, selectionEndX) else 0
        val pasteY = if (hasSelection) min(selectionStartY, selectionEndY) else 0

        // Instead of drawing directly, enter move mode so user can position the paste
        moveSelectionOrigX = pasteX
        moveSelectionOrigY = pasteY
        moveSelectionCurrentX = pasteX
        moveSelectionCurrentY = pasteY
        moveSelectionWidth = clipboardWidth
        moveSelectionHeight = clipboardHeight

        // Copy clipboard data to move selection data
        moveSelectionData = clipboardData!!.copyOf()

        // Update selection to pasted area
        selectionStartX = pasteX
        selectionStartY = pasteY
        selectionEndX = pasteX + clipboardWidth - 1
        selectionEndY = pasteY + clipboardHeight - 1
        hasSelection = true

        // Activate move mode so user can reposition before finalizing
        isMovingSelection = true
        currentTool = Tool.MOVE_SELECTION
        onSelectionChanged?.invoke(true)

        invalidate()
        return true
    }

    fun deleteSelection(): Boolean {
        if (!hasSelection) return false

        saveToHistory(ActionType.CUT)
        val bounds = getSelectionBounds() ?: return false

        for (y in bounds.top..bounds.bottom) {
            for (x in bounds.left..bounds.right) {
                setPixel(x, y, Color.TRANSPARENT)
            }
        }
        invalidate()
        return true
    }

    @Suppress("unused")
    fun hasClipboardData(): Boolean = clipboardData != null && clipboardWidth > 0 && clipboardHeight > 0

    // ========== PENDING SHAPE CLIPBOARD FUNCTIONS ==========

    /**
     * Copie la forme en attente dans le presse-papier.
     * La forme reste affichée.
     */
    fun copyPendingShape(): Boolean {
        if (!hasPendingShape || pendingShapePixels.isEmpty()) return false

        // Calculer les bounds de la forme
        val minX = pendingShapePixels.minOfOrNull { it.first + pendingShapeOffsetX } ?: return false
        val maxX = pendingShapePixels.maxOfOrNull { it.first + pendingShapeOffsetX } ?: return false
        val minY = pendingShapePixels.minOfOrNull { it.second + pendingShapeOffsetY } ?: return false
        val maxY = pendingShapePixels.maxOfOrNull { it.second + pendingShapeOffsetY } ?: return false

        clipboardWidth = maxX - minX + 1
        clipboardHeight = maxY - minY + 1
        clipboardData = IntArray(clipboardWidth * clipboardHeight)

        // Copier les pixels de la forme avec la couleur primaire
        for ((px, py) in pendingShapePixels) {
            val x = px + pendingShapeOffsetX - minX
            val y = py + pendingShapeOffsetY - minY
            if (x in 0 until clipboardWidth && y in 0 until clipboardHeight) {
                clipboardData!![y * clipboardWidth + x] = primaryColor
            }
        }
        return true
    }

    /**
     * Coupe la forme en attente : copie dans le presse-papier et supprime de l'affichage.
     * Ne l'applique PAS au canvas.
     */
    fun cutPendingShape(): Boolean {
        if (!copyPendingShape()) return false
        cancelPendingShape()
        return true
    }

    /**
     * Tamponne la forme en attente : applique au canvas mais garde la forme en attente.
     * Permet de "tamponner" plusieurs copies de la forme.
     */
    fun stampPendingShape(): Boolean {
        if (!hasPendingShape || pendingShapePixels.isEmpty()) return false

        saveToHistory(ActionType.DRAW)

        // Appliquer les pixels de la forme au canvas
        for ((px, py) in pendingShapePixels) {
            val finalX = px + pendingShapeOffsetX
            val finalY = py + pendingShapeOffsetY
            setPixel(finalX, finalY, primaryColor)
        }

        invalidate()
        return true
    }

    // ========== MOVE SELECTION FUNCTIONS ==========

    /**
     * Starts the move selection mode. Cuts the selection content and allows
     * the user to drag it to a new position with real-time preview.
     * @return true if move mode was started successfully
     */
    fun startMoveSelection(): Boolean {
        if (!hasSelection) return false

        val bounds = getSelectionBounds() ?: return false

        // Store original position
        moveSelectionOrigX = bounds.left
        moveSelectionOrigY = bounds.top
        moveSelectionCurrentX = bounds.left
        moveSelectionCurrentY = bounds.top
        moveSelectionWidth = bounds.width() + 1
        moveSelectionHeight = bounds.height() + 1

        // Copy selection data
        moveSelectionData = IntArray(moveSelectionWidth * moveSelectionHeight)
        for (y in 0 until moveSelectionHeight) {
            for (x in 0 until moveSelectionWidth) {
                val srcX = bounds.left + x
                val srcY = bounds.top + y
                moveSelectionData!![y * moveSelectionWidth + x] = getPixel(srcX, srcY)
            }
        }

        // Save to history before deleting
        saveToHistory(ActionType.MOVE)

        // Delete original pixels
        for (y in bounds.top..bounds.bottom) {
            for (x in bounds.left..bounds.right) {
                setPixel(x, y, Color.TRANSPARENT)
            }
        }

        // Activate move mode
        isMovingSelection = true
        currentTool = Tool.MOVE_SELECTION

        invalidate()
        return true
    }

    /**
     * Updates the move selection preview position.
     */
    fun updateMoveSelectionPosition(deltaX: Int, deltaY: Int) {
        if (!isMovingSelection || moveSelectionData == null) return
        moveSelectionCurrentX = moveSelectionOrigX + deltaX
        moveSelectionCurrentY = moveSelectionOrigY + deltaY
        invalidate()
    }

    /**
     * Finalizes the move selection by pasting at the current position.
     */
    fun finalizeMoveSelection() {
        if (!isMovingSelection || moveSelectionData == null) return

        // Paste at current position
        for (y in 0 until moveSelectionHeight) {
            for (x in 0 until moveSelectionWidth) {
                val destX = moveSelectionCurrentX + x
                val destY = moveSelectionCurrentY + y
                val color = moveSelectionData!![y * moveSelectionWidth + x]
                if (color != Color.TRANSPARENT && destX in 0 until canvasWidth && destY in 0 until canvasHeight) {
                    setPixel(destX, destY, color)
                }
            }
        }

        // Update selection bounds to new position
        selectionStartX = moveSelectionCurrentX
        selectionStartY = moveSelectionCurrentY
        selectionEndX = moveSelectionCurrentX + moveSelectionWidth - 1
        selectionEndY = moveSelectionCurrentY + moveSelectionHeight - 1

        // Clean up
        isMovingSelection = false
        moveSelectionData = null
        currentTool = Tool.SELECT

        invalidate()
    }

    /**
     * Cancels the move selection and restores the original position.
     */
    @Suppress("unused")
    fun cancelMoveSelection() {
        if (!isMovingSelection || moveSelectionData == null) return

        // Restore at original position
        for (y in 0 until moveSelectionHeight) {
            for (x in 0 until moveSelectionWidth) {
                val destX = moveSelectionOrigX + x
                val destY = moveSelectionOrigY + y
                val color = moveSelectionData!![y * moveSelectionWidth + x]
                if (color != Color.TRANSPARENT && destX in 0 until canvasWidth && destY in 0 until canvasHeight) {
                    setPixel(destX, destY, color)
                }
            }
        }

        // Clean up
        isMovingSelection = false
        moveSelectionData = null
        currentTool = Tool.SELECT

        invalidate()
    }

    /**
     * Returns true if currently in move selection mode.
     */
    @Suppress("unused")
    fun isInMoveSelectionMode(): Boolean = isMovingSelection

    // ========== MIRROR FUNCTIONS ==========

    fun flipHorizontal() {
        if (hasPendingShape && pendingShapePixels.isNotEmpty()) {
            // Flip only pending shape horizontally
            val minX = pendingShapePixels.minOfOrNull { it.first } ?: return
            val maxX = pendingShapePixels.maxOfOrNull { it.first } ?: return

            pendingShapePixels = pendingShapePixels.map { (x, y) ->
                Pair(maxX - (x - minX), y)
            }.toMutableList()
            invalidate()
            return
        }

        saveToHistory(ActionType.FLIP_H)

        if (hasSelection) {
            // Flip only selection
            val bounds = getSelectionBounds() ?: return
            val width = bounds.width() + 1
            val height = bounds.height() + 1
            val temp = IntArray(width * height)

            // Copy selection to temp
            for (y in 0 until height) {
                for (x in 0 until width) {
                    temp[y * width + x] = getPixel(bounds.left + x, bounds.top + y)
                }
            }

            // Paste flipped
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(bounds.left + x, bounds.top + y, temp[y * width + (width - 1 - x)])
                }
            }
        } else {
            // Flip entire canvas
            val temp = pixelData.clone()
            for (y in 0 until canvasHeight) {
                for (x in 0 until canvasWidth) {
                    pixelData[y * canvasWidth + x] = temp[y * canvasWidth + (canvasWidth - 1 - x)]
                }
            }
            updateBitmap()
        }
        invalidate()
    }

    fun flipVertical() {
        if (hasPendingShape && pendingShapePixels.isNotEmpty()) {
            // Flip only pending shape vertically
            val minY = pendingShapePixels.minOfOrNull { it.second } ?: return
            val maxY = pendingShapePixels.maxOfOrNull { it.second } ?: return

            pendingShapePixels = pendingShapePixels.map { (x, y) ->
                Pair(x, maxY - (y - minY))
            }.toMutableList()
            invalidate()
            return
        }

        saveToHistory(ActionType.FLIP_V)

        if (hasSelection) {
            // Flip only selection
            val bounds = getSelectionBounds() ?: return
            val width = bounds.width() + 1
            val height = bounds.height() + 1
            val temp = IntArray(width * height)

            // Copy selection to temp
            for (y in 0 until height) {
                for (x in 0 until width) {
                    temp[y * width + x] = getPixel(bounds.left + x, bounds.top + y)
                }
            }

            // Paste flipped
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(bounds.left + x, bounds.top + y, temp[(height - 1 - y) * width + x])
                }
            }
        } else {
            // Flip entire canvas
            val temp = pixelData.clone()
            for (y in 0 until canvasHeight) {
                for (x in 0 until canvasWidth) {
                    pixelData[y * canvasWidth + x] = temp[(canvasHeight - 1 - y) * canvasWidth + x]
                }
            }
            updateBitmap()
        }
        invalidate()
    }

    @Suppress("unused")
    fun rotate90CW() {
        if (hasPendingShape && pendingShapePixels.isNotEmpty()) {
            // Rotate pending shape 90° clockwise
            val minX = pendingShapePixels.minOfOrNull { it.first } ?: return
            val maxX = pendingShapePixels.maxOfOrNull { it.first } ?: return
            val minY = pendingShapePixels.minOfOrNull { it.second } ?: return
            val maxY = pendingShapePixels.maxOfOrNull { it.second } ?: return
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            pendingShapePixels = pendingShapePixels.map { (x, y) ->
                val relX = x - centerX
                val relY = y - centerY
                // CW: (x, y) -> (y, -x)
                Pair(centerX + relY, centerY - relX)
            }.toMutableList()
            invalidate()
            return
        }

        if (hasSelection) {
            // Rotate only selection - this is complex, skip for now
            return
        }

        saveToHistory(ActionType.ROTATE_CW)

        // Only works for square canvas or we need to resize
        if (canvasWidth != canvasHeight) return

        val temp = pixelData.clone()
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                pixelData[x * canvasWidth + (canvasHeight - 1 - y)] = temp[y * canvasWidth + x]
            }
        }
        updateBitmap()
        invalidate()
    }

    @Suppress("unused")
    fun rotate90CCW() {
        if (hasPendingShape && pendingShapePixels.isNotEmpty()) {
            // Rotate pending shape 90° counter-clockwise
            val minX = pendingShapePixels.minOfOrNull { it.first } ?: return
            val maxX = pendingShapePixels.maxOfOrNull { it.first } ?: return
            val minY = pendingShapePixels.minOfOrNull { it.second } ?: return
            val maxY = pendingShapePixels.maxOfOrNull { it.second } ?: return
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            pendingShapePixels = pendingShapePixels.map { (x, y) ->
                val relX = x - centerX
                val relY = y - centerY
                // CCW: (x, y) -> (-y, x)
                Pair(centerX - relY, centerY + relX)
            }.toMutableList()
            invalidate()
            return
        }

        if (hasSelection) return
        if (canvasWidth != canvasHeight) return

        saveToHistory(ActionType.ROTATE_CCW)

        val temp = pixelData.clone()
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                pixelData[(canvasWidth - 1 - x) * canvasWidth + y] = temp[y * canvasWidth + x]
            }
        }
        updateBitmap()
        invalidate()
    }

    // ========== FRAME MANAGEMENT FUNCTIONS ==========

    fun getFrameCount(): Int = frames.size

    fun getCurrentFrameIndex(): Int = currentFrameIndex

    @Suppress("unused")
    fun getCurrentFrame(): Frame? = frames.getOrNull(currentFrameIndex)

    @Suppress("unused")
    fun getFrames(): List<Frame> = frames.toList()

    private fun saveCurrentFrameData() {
        frames.getOrNull(currentFrameIndex)?.let {
            it.pixelData = pixelData.clone()
        }
    }

    private fun loadFrameData(index: Int) {
        frames.getOrNull(index)?.let { frame ->
            pixelData = frame.pixelData.clone()
            updateBitmap()
            invalidate()
        }
    }

    fun goToFrame(index: Int) {
        if (index < 0 || index >= frames.size) return
        if (index == currentFrameIndex) return

        saveCurrentFrameData()
        currentFrameIndex = index
        loadFrameData(currentFrameIndex)
        clearSelection()
        // History is now per-frame, managed by historyManager - don't clear
        // For legacy mode, clear the stacks
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()
        onFrameChanged?.invoke(currentFrameIndex, frames.size)
    }

    fun nextFrame() {
        val nextIndex = (currentFrameIndex + 1) % frames.size
        goToFrame(nextIndex)
    }

    fun previousFrame() {
        val prevIndex = if (currentFrameIndex > 0) currentFrameIndex - 1 else frames.size - 1
        goToFrame(prevIndex)
    }

    fun addFrame(): Int {
        saveCurrentFrameData()

        // Create new blank frame
        val newPixelData = IntArray(canvasWidth * canvasHeight)
        val newFrame = Frame(nextFrameId++, newPixelData)

        // Insert after current frame
        val insertIndex = currentFrameIndex + 1
        frames.add(insertIndex, newFrame)

        // Switch to new frame
        currentFrameIndex = insertIndex
        pixelData = newPixelData.clone()
        updateBitmap()
        clearSelection()
        // New frame starts with empty history (managed per-frame by historyManager)
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()
        invalidate()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        return insertIndex
    }

    fun duplicateFrame(): Int {
        saveCurrentFrameData()

        // Create duplicate of current frame
        val currentFrame = frames[currentFrameIndex]
        val newFrame = Frame(nextFrameId++, currentFrame.pixelData.clone(), currentFrame.duration)

        // Insert after current frame
        val insertIndex = currentFrameIndex + 1
        frames.add(insertIndex, newFrame)

        // Switch to new frame
        currentFrameIndex = insertIndex
        loadFrameData(currentFrameIndex)
        clearSelection()
        // New duplicated frame starts with empty history
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        return insertIndex
    }

    fun deleteFrame(): Boolean {
        if (frames.size <= 1) return false  // Keep at least one frame

        // Get the frame ID before deleting for history cleanup
        val deletedFrameId = frames[currentFrameIndex].id

        frames.removeAt(currentFrameIndex)

        // Notify history manager about deleted frame
        historyManager?.onFrameDeleted(deletedFrameId)

        // Adjust current index
        if (currentFrameIndex >= frames.size) {
            currentFrameIndex = frames.size - 1
        }

        loadFrameData(currentFrameIndex)
        clearSelection()
        // History for deleted frame is already cleaned up by historyManager
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        return true
    }

    @Suppress("unused")
    fun moveFrame(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex < 0 || fromIndex >= frames.size) return false
        if (toIndex < 0 || toIndex >= frames.size) return false
        if (fromIndex == toIndex) return false

        saveCurrentFrameData()

        val frame = frames.removeAt(fromIndex)
        frames.add(toIndex, frame)

        // Update current index if it was affected
        currentFrameIndex = when {
            currentFrameIndex == fromIndex -> toIndex
            fromIndex < currentFrameIndex && toIndex >= currentFrameIndex -> currentFrameIndex - 1
            fromIndex > currentFrameIndex && toIndex <= currentFrameIndex -> currentFrameIndex + 1
            else -> currentFrameIndex
        }

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        return true
    }

    @Suppress("unused")
    fun setFrameDuration(index: Int, duration: Int) {
        frames.getOrNull(index)?.duration = duration.coerceIn(10, 10000)
    }

    @Suppress("unused")
    fun getFrameDuration(index: Int): Int {
        return frames.getOrNull(index)?.duration ?: 100
    }

    // ========== SHEET MODE RENDERING ==========

    /**
     * Draw all frames in grid layout for sheet mode.
     */
    private fun drawSheetMode(canvas: Canvas) {
        // Draw reference image behind (if applicable)
        drawReferenceImage(canvas)

        // Draw checkerboard for entire sheet area
        drawSheetCheckerboard(canvas)

        // Draw each frame
        for ((index, frame) in frames.withIndex()) {
            val col = index % sheetLayout.columns
            val row = index / sheetLayout.columns

            val frameOffsetX = col * canvasWidth
            val frameOffsetY = row * canvasHeight

            canvas.save()
            canvas.translate(frameOffsetX.toFloat(), frameOffsetY.toFloat())

            // Draw frame content
            // For active frame, use canvasBitmap (current editing data)
            // For other frames, use cached bitmap from frame.pixelData
            if (index == sheetActiveFrameIndex) {
                canvas.drawBitmap(canvasBitmap, 0f, 0f, bitmapPaint)
                // Draw active frame highlight
                sheetActiveFramePaint.strokeWidth = 3f / zoomLevel
                canvas.drawRect(
                    0f, 0f,
                    canvasWidth.toFloat(), canvasHeight.toFloat(),
                    sheetActiveFramePaint
                )
            } else {
                val frameBitmap = getOrCreateFrameBitmap(frame)
                canvas.drawBitmap(frameBitmap, 0f, 0f, bitmapPaint)
            }

            canvas.restore()
        }

        // Draw grid lines separating frames
        if (showSheetGridLines) {
            drawSheetGridLines(canvas)
        }

        // Draw animation preview highlight (orange border on current playback frame)
        if (isPreviewPlaying && previewPlaybackFrameIndex >= 0 && previewPlaybackFrameIndex < frames.size) {
            val col = previewPlaybackFrameIndex % sheetLayout.columns
            val row = previewPlaybackFrameIndex / sheetLayout.columns
            val frameOffsetX = col * canvasWidth
            val frameOffsetY = row * canvasHeight

            previewHighlightPaint.strokeWidth = 4f / zoomLevel
            canvas.drawRect(
                frameOffsetX.toFloat(),
                frameOffsetY.toFloat(),
                (frameOffsetX + canvasWidth).toFloat(),
                (frameOffsetY + canvasHeight).toFloat(),
                previewHighlightPaint
            )
        }

        // Draw pixel grid if enabled and zoomed enough
        if (showGrid && zoomLevel >= 3f) {
            drawSheetPixelGrid(canvas)
        }

        // Draw shape preview if drawing in sheet mode
        if (isDrawingShape && previewPixels.isNotEmpty()) {
            val col = sheetActiveFrameIndex % sheetLayout.columns
            val row = sheetActiveFrameIndex / sheetLayout.columns
            val frameOffsetX = col * canvasWidth
            val frameOffsetY = row * canvasHeight

            previewPaint.color = primaryColor or 0x80000000.toInt()
            for ((px, py) in previewPixels) {
                if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
                    canvas.drawRect(
                        (frameOffsetX + px).toFloat(),
                        (frameOffsetY + py).toFloat(),
                        (frameOffsetX + px + 1).toFloat(),
                        (frameOffsetY + py + 1).toFloat(),
                        previewPaint
                    )
                }
            }
        }

        // Draw selection rectangle if active
        if (hasSelection || isSelectingArea) {
            val col = sheetActiveFrameIndex % sheetLayout.columns
            val row = sheetActiveFrameIndex / sheetLayout.columns
            val frameOffsetX = col * canvasWidth
            val frameOffsetY = row * canvasHeight

            val minX = min(selectionStartX, selectionEndX).toFloat() + frameOffsetX
            val maxX = max(selectionStartX, selectionEndX).toFloat() + 1f + frameOffsetX
            val minY = min(selectionStartY, selectionEndY).toFloat() + frameOffsetY
            val maxY = max(selectionStartY, selectionEndY).toFloat() + 1f + frameOffsetY

            canvas.drawRect(minX, minY, maxX, maxY, selectionFillPaint)

            // Selection border
            selectionPaintBlack.strokeWidth = 2f / zoomLevel
            selectionPaintWhite.strokeWidth = 2f / zoomLevel
            canvas.drawRect(minX, minY, maxX, maxY, selectionPaintBlack)
            canvas.drawRect(minX, minY, maxX, maxY, selectionPaintWhite)
        }
    }

    /**
     * Draw checkerboard pattern for the entire sheet area.
     */
    private fun drawSheetCheckerboard(canvas: Canvas) {
        val sheetW = sheetLayout.sheetWidth.toFloat()
        val sheetH = sheetLayout.sheetHeight.toFloat()

        val pixelScreenSize = zoomLevel

        if (pixelScreenSize >= 2f && sheetLayout.sheetWidth <= 1024 && sheetLayout.sheetHeight <= 1024) {
            // High zoom: use tiled checkerboard
            if (checkerboardTile == null) {
                checkerboardTile = createCheckerboardTile()
                checkerboardShader.shader = BitmapShader(
                    checkerboardTile!!,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT
                )
            }
            updateCheckerboardAlpha()
            canvas.drawRect(0f, 0f, sheetW, sheetH, checkerboardShader)
        } else if (pixelScreenSize >= 4f) {
            // Medium zoom: draw blocks
            val checkerSize = 4
            updateCheckerboardAlpha()
            val endBlockX = (sheetLayout.sheetWidth + checkerSize - 1) / checkerSize
            val endBlockY = (sheetLayout.sheetHeight + checkerSize - 1) / checkerSize

            for (blockY in 0 until endBlockY) {
                for (blockX in 0 until endBlockX) {
                    val paint = if ((blockX + blockY) % 2 == 0) checkerPaint1 else checkerPaint2
                    val x1 = (blockX * checkerSize).toFloat()
                    val y1 = (blockY * checkerSize).toFloat()
                    val x2 = minOf((blockX + 1) * checkerSize, sheetLayout.sheetWidth).toFloat()
                    val y2 = minOf((blockY + 1) * checkerSize, sheetLayout.sheetHeight).toFloat()
                    canvas.drawRect(x1, y1, x2, y2, paint)
                }
            }
        } else {
            // Low zoom: solid color
            updateCheckerboardAlpha()
            canvas.drawRect(0f, 0f, sheetW, sheetH, checkerAvgPaint)
        }
    }

    /**
     * Draw grid lines between frames in sheet mode.
     */
    private fun drawSheetGridLines(canvas: Canvas) {
        sheetGridPaint.strokeWidth = 2f / zoomLevel

        // Vertical lines between columns
        for (col in 1 until sheetLayout.columns) {
            val x = (col * canvasWidth).toFloat()
            canvas.drawLine(x, 0f, x, sheetLayout.sheetHeight.toFloat(), sheetGridPaint)
        }

        // Horizontal lines between rows
        for (row in 1 until sheetLayout.rows) {
            val y = (row * canvasHeight).toFloat()
            canvas.drawLine(0f, y, sheetLayout.sheetWidth.toFloat(), y, sheetGridPaint)
        }
    }

    /**
     * Draw pixel grid for entire sheet area.
     */
    private fun drawSheetPixelGrid(canvas: Canvas) {
        gridPaint.strokeWidth = 1f / zoomLevel

        // Draw vertical lines
        for (x in 0..sheetLayout.sheetWidth) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), sheetLayout.sheetHeight.toFloat(), gridPaint)
        }
        // Draw horizontal lines
        for (y in 0..sheetLayout.sheetHeight) {
            canvas.drawLine(0f, y.toFloat(), sheetLayout.sheetWidth.toFloat(), y.toFloat(), gridPaint)
        }
    }

    // ========== SHEET MODE CALCULATIONS ==========

    /**
     * Calculates optimal grid layout for sheet mode.
     * Called when entering sheet mode, frame count changes, or user changes column preference.
     */
    private fun calculateSheetLayout() {
        val frameCount = frames.size
        if (frameCount == 0) return

        val cols = if (sheetColumns <= 0) {
            // Auto-calculate: prefer square-ish layout
            // For 4 frames -> 2x2, for 6 -> 3x2, for 9 -> 3x3
            val sqrtCount = sqrt(frameCount.toDouble()).toInt()
            when {
                sqrtCount * sqrtCount == frameCount -> sqrtCount
                sqrtCount * (sqrtCount + 1) >= frameCount -> sqrtCount + 1
                else -> sqrtCount + 1
            }.coerceIn(1, frameCount)
        } else {
            sheetColumns.coerceIn(1, frameCount)
        }

        val rows = (frameCount + cols - 1) / cols

        // Calculate total sheet dimensions
        val sheetWidthPx = canvasWidth * cols
        val sheetHeightPx = canvasHeight * rows

        sheetLayout = SheetLayout(
            columns = cols,
            rows = rows,
            autoLayout = sheetColumns <= 0,
            frameWidth = canvasWidth,
            frameHeight = canvasHeight,
            sheetWidth = sheetWidthPx,
            sheetHeight = sheetHeightPx
        )
    }

    /**
     * Get frame index from canvas coordinates in sheet mode.
     * Returns -1 if coordinates are outside all frames.
     */
    private fun getFrameIndexFromSheetCoords(canvasX: Float, canvasY: Float): Int {
        if (viewMode != ViewMode.SHEET) return currentFrameIndex

        val col = (canvasX / canvasWidth).toInt()
        val row = (canvasY / canvasHeight).toInt()

        if (col < 0 || col >= sheetLayout.columns || row < 0 || row >= sheetLayout.rows) {
            return -1
        }

        val index = row * sheetLayout.columns + col
        return if (index < frames.size) index else -1
    }

    /**
     * Convert sheet coordinates to frame-local pixel coordinates.
     * Returns Triple(frameIndex, localX, localY) or null if outside valid frame area.
     */
    private fun sheetCoordsToFramePixel(canvasX: Float, canvasY: Float): Triple<Int, Int, Int>? {
        val frameIndex = getFrameIndexFromSheetCoords(canvasX, canvasY)
        if (frameIndex < 0) return null

        val col = frameIndex % sheetLayout.columns
        val row = frameIndex / sheetLayout.columns

        val frameOffsetX = col * canvasWidth
        val frameOffsetY = row * canvasHeight

        val localX = (canvasX - frameOffsetX).toInt()
        val localY = (canvasY - frameOffsetY).toInt()

        if (localX < 0 || localX >= canvasWidth || localY < 0 || localY >= canvasHeight) {
            return null
        }

        return Triple(frameIndex, localX, localY)
    }

    /**
     * Switch the active frame in sheet mode.
     */
    private fun switchToSheetFrame(frameIndex: Int) {
        if (frameIndex < 0 || frameIndex >= frames.size) return
        if (frameIndex == sheetActiveFrameIndex) return

        // Save current frame data
        frames.getOrNull(sheetActiveFrameIndex)?.let {
            it.pixelData = pixelData.clone()
            updateFrameCachedBitmap(sheetActiveFrameIndex)
        }

        // Load new frame
        sheetActiveFrameIndex = frameIndex
        currentFrameIndex = frameIndex
        frames.getOrNull(frameIndex)?.let { frame ->
            pixelData = frame.pixelData.clone()
            updateBitmap()
        }

        onSheetActiveFrameChanged?.invoke(frameIndex)
        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        invalidate()
    }

    /**
     * Update a frame's cached bitmap after editing.
     * Also syncs pixelData to frame.pixelData for the active frame.
     */
    private fun updateFrameCachedBitmap(frameIndex: Int) {
        frames.getOrNull(frameIndex)?.let { frame ->
            // If this is the active frame, sync pixelData to frame.pixelData first
            if (frameIndex == sheetActiveFrameIndex) {
                frame.pixelData = pixelData.clone()
            }
            // Update cached bitmap from frame's own pixelData
            if (frame.cachedBitmap == null || frame.cachedBitmap?.isRecycled == true) {
                frame.cachedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            }
            frame.cachedBitmap?.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
        }
    }

    /**
     * Sync current pixelData to active frame and update its cached bitmap.
     * Call this during drawing to keep the sheet view updated in real-time.
     */
    private fun syncActiveFrameBitmap() {
        if (viewMode != ViewMode.SHEET) return
        frames.getOrNull(sheetActiveFrameIndex)?.let { frame ->
            // Copy current pixelData to frame
            System.arraycopy(pixelData, 0, frame.pixelData, 0, pixelData.size)
            // Update cached bitmap
            if (frame.cachedBitmap == null || frame.cachedBitmap?.isRecycled == true) {
                frame.cachedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            }
            frame.cachedBitmap?.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
        }
    }

    /**
     * Get or create cached bitmap for a frame.
     */
    private fun getOrCreateFrameBitmap(frame: Frame): Bitmap {
        if (frame.cachedBitmap == null || frame.cachedBitmap?.isRecycled == true) {
            frame.cachedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            frame.cachedBitmap?.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
        }
        return frame.cachedBitmap!!
    }

    // ========== SHEET MODE TOUCH HANDLING ==========

    /**
     * Handle touch events in sheet mode.
     * Determines which frame the user is touching and applies drawing.
     */
    private fun handleSheetModeTouch(event: MotionEvent): Boolean {
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

                // Determine which frame was touched
                val frameInfo = screenToPixelSheet(event.x, event.y)

                if (frameInfo != null) {
                    val (frameIndex, localX, localY) = frameInfo

                    // Auto-switch to the touched frame
                    if (frameIndex != sheetActiveFrameIndex) {
                        switchToSheetFrame(frameIndex)
                    }

                    // Handle shape tools
                    if (currentTool in listOf(Tool.LINE, Tool.DASHED_LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.TRIANGLE, Tool.OVAL, Tool.HEXAGON, Tool.DIAMOND, Tool.STAR, Tool.ARROW, Tool.PENTAGON)) {
                        saveToHistory()
                        isDrawingShape = true
                        shapeStartX = localX
                        shapeStartY = localY
                        shapeEndX = localX
                        shapeEndY = localY
                        updateShapePreview()
                        invalidate()
                    } else if (currentTool == Tool.SELECT) {
                        isSelectingArea = true
                        selectionStartX = localX
                        selectionStartY = localY
                        selectionEndX = localX
                        selectionEndY = localY
                        hasSelection = false
                        invalidate()
                    } else if (currentTool == Tool.PICKER) {
                        // Color picker in sheet mode
                        val color = pixelData[localY * canvasWidth + localX]
                        onColorPicked?.invoke(color)
                    } else {
                        // For pencil/eraser: store pending position
                        pendingDrawX = localX
                        pendingDrawY = localY
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch = pan/zoom
                wasMultiTouch = true
                isPanning = true
                isDrawing = false
                pendingDrawX = -1
                pendingDrawY = -1
                lastCenterX = centerX
                lastCenterY = centerY

                // Revert any pixels drawn by the first finger before second finger was added
                revertAndCancelStroke()

                // Cancel shape drawing if user puts second finger
                if (isDrawingShape) {
                    isDrawingShape = false
                    previewPixels.clear()
                    undo()
                    invalidate()
                }

                // Cancel selection
                if (isSelectingArea) {
                    isSelectingArea = false
                    selectionStartX = -1
                    selectionStartY = -1
                    selectionEndX = -1
                    selectionEndY = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount >= 2 || isPanning) {
                    // Pan/zoom
                    val dx = centerX - lastCenterX
                    val dy = centerY - lastCenterY
                    offsetX += dx
                    offsetY += dy
                    lastCenterX = centerX
                    lastCenterY = centerY
                    invalidate()
                } else if (pointerCount == 1 && !wasMultiTouch) {
                    // Drawing in sheet mode
                    val frameInfo = screenToPixelSheet(event.x, event.y)

                    if (frameInfo != null) {
                        val (frameIndex, localX, localY) = frameInfo

                        // Check if moved to a different frame
                        if (frameIndex != sheetActiveFrameIndex) {
                            // Finalize any pending drawing on old frame
                            if (isDrawing) {
                                finalizeDeltaTracking()
                                updateFrameCachedBitmap(sheetActiveFrameIndex)
                            }
                            switchToSheetFrame(frameIndex)
                        }

                        // Continue drawing
                        if (isDrawingShape) {
                            shapeEndX = localX
                            shapeEndY = localY
                            updateShapePreview()
                            invalidate()
                        } else if (isSelectingArea) {
                            selectionEndX = localX
                            selectionEndY = localY
                            invalidate()
                        } else if (localX != pendingDrawX || localY != pendingDrawY) {
                            hasMoved = true
                            if (!isDrawing && pendingDrawX >= 0) {
                                saveToHistory()
                                startDeltaTracking()
                                isDrawing = true
                                drawPixelWithTool(pendingDrawX, pendingDrawY)
                                syncActiveFrameBitmap()
                            }
                            if (isDrawing) {
                                drawPixelWithTool(localX, localY)
                                syncActiveFrameBitmap()
                            }
                            pendingDrawX = localX
                            pendingDrawY = localY
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Finalize drawing
                if (isDrawing) {
                    finalizeDeltaTracking()
                    updateFrameCachedBitmap(sheetActiveFrameIndex)
                    isDrawing = false
                    onCanvasModified?.invoke()
                }

                if (isDrawingShape) {
                    // Apply shape pixels directly to canvas in sheet mode
                    for ((px, py) in previewPixels) {
                        if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
                            setPixel(px, py, primaryColor)
                        }
                    }
                    previewPixels.clear()
                    isDrawingShape = false
                    shapeStartX = -1
                    shapeStartY = -1
                    shapeEndX = -1
                    shapeEndY = -1
                    updateFrameCachedBitmap(sheetActiveFrameIndex)
                    onCanvasModified?.invoke()
                    invalidate()
                }

                if (isSelectingArea) {
                    isSelectingArea = false
                    if (selectionStartX != selectionEndX || selectionStartY != selectionEndY) {
                        hasSelection = true
                    }
                    invalidate()
                }

                // Handle single tap (no movement)
                if (!hasMoved && !wasMultiTouch && pendingDrawX >= 0 && pendingDrawY >= 0) {
                    saveToHistory()
                    drawPixelWithTool(pendingDrawX, pendingDrawY)
                    syncActiveFrameBitmap()
                    onCanvasModified?.invoke()
                    invalidate()
                }

                pendingDrawX = -1
                pendingDrawY = -1
                isPanning = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Recalculate center after finger lifted
                lastCenterX = centerX
                lastCenterY = centerY
            }
        }
        return true
    }

    // ========== SHEET MODE PUBLIC API ==========

    /**
     * Check if currently in sheet mode.
     */
    fun isSheetMode(): Boolean = viewMode == ViewMode.SHEET

    /**
     * Toggle between frame mode and sheet mode.
     */
    fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.FRAME) ViewMode.SHEET else ViewMode.FRAME
    }

    /**
     * Get the current sheet layout configuration.
     */
    fun getSheetLayout(): SheetLayout = sheetLayout

    /**
     * Get the currently active frame index in sheet mode.
     */
    fun getSheetActiveFrameIndex(): Int = sheetActiveFrameIndex

    /**
     * Set the preview playback frame for highlighting during animation preview.
     * @param frameIndex The frame index to highlight, or -1 to clear
     */
    fun setPreviewPlaybackFrame(frameIndex: Int) {
        previewPlaybackFrameIndex = frameIndex
        if (viewMode == ViewMode.SHEET) {
            invalidate()
        }
    }

    /**
     * Start animation preview playback (shows highlight on canvas).
     */
    fun startPreviewPlayback() {
        isPreviewPlaying = true
        previewPlaybackFrameIndex = 0
        if (viewMode == ViewMode.SHEET) {
            invalidate()
        }
    }

    /**
     * Stop animation preview playback.
     */
    fun stopPreviewPlayback() {
        isPreviewPlaying = false
        previewPlaybackFrameIndex = -1
        if (viewMode == ViewMode.SHEET) {
            invalidate()
        }
    }

    /**
     * Get bitmap for a specific frame (for preview window).
     */
    fun getFrameBitmap(frameIndex: Int): Bitmap? {
        if (frameIndex < 0 || frameIndex >= frames.size) return null

        // For current editing frame, return canvasBitmap
        if (frameIndex == currentFrameIndex) {
            return canvasBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        // For other frames, return cached or create new
        val frame = frames[frameIndex]
        return getOrCreateFrameBitmap(frame).copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Center and fit the entire sheet in view.
     */
    fun fitSheetInView() {
        if (viewMode != ViewMode.SHEET) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val scaleX = viewWidth / sheetLayout.sheetWidth
        val scaleY = viewHeight / sheetLayout.sheetHeight
        zoomLevel = minOf(scaleX, scaleY) * 0.9f  // 90% to leave margin

        offsetX = (viewWidth - sheetLayout.sheetWidth * zoomLevel) / 2
        offsetY = (viewHeight - sheetLayout.sheetHeight * zoomLevel) / 2

        onZoomChanged?.invoke(zoomLevel)
        invalidate()
    }

    /**
     * Export sprite sheet as a single bitmap.
     * Uses current layout configuration.
     */
    fun exportSpriteSheet(): Bitmap {
        // Save current frame data first
        saveCurrentFrameData()

        // Ensure layout is calculated
        calculateSheetLayout()

        val result = Bitmap.createBitmap(
            sheetLayout.sheetWidth,
            sheetLayout.sheetHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)

        // Draw each frame in grid position
        for ((index, frame) in frames.withIndex()) {
            val col = index % sheetLayout.columns
            val row = index / sheetLayout.columns

            val offsetX = col * canvasWidth
            val offsetY = row * canvasHeight

            val frameBitmap = getOrCreateFrameBitmap(frame)
            canvas.drawBitmap(frameBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
        }

        return result
    }

    /**
     * Export sprite sheet with custom scale.
     */
    @Suppress("unused")
    fun exportSpriteSheetScaled(scale: Int): Bitmap {
        val baseSheet = exportSpriteSheet()
        if (scale <= 1) return baseSheet

        return Bitmap.createScaledBitmap(
            baseSheet,
            sheetLayout.sheetWidth * scale,
            sheetLayout.sheetHeight * scale,
            false
        )
    }

    // ========== ANIMATION PLAYBACK ==========

    fun isAnimationPlaying(): Boolean = isPlaying

    fun playAnimation() {
        if (frames.size <= 1) return
        if (isPlaying) return

        saveCurrentFrameData()
        isPlaying = true
        onPlaybackChanged?.invoke(true)

        val delay = (1000 / animationFps).toLong()
        animationHandler.postDelayed(animationRunnable, delay)
    }

    fun pauseAnimation() {
        isPlaying = false
        animationHandler.removeCallbacks(animationRunnable)
        onPlaybackChanged?.invoke(false)
    }

    fun stopAnimation() {
        pauseAnimation()
        goToFrame(0)
    }

    // ========== FRAME THUMBNAILS ==========

    fun getFrameThumbnail(index: Int, maxSize: Int = 64): Bitmap? {
        val frame = frames.getOrNull(index) ?: return null

        val srcBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        srcBitmap.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

        // Scale to thumbnail size while maintaining aspect ratio
        val scale = minOf(maxSize.toFloat() / canvasWidth, maxSize.toFloat() / canvasHeight)
        val thumbWidth = (canvasWidth * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (canvasHeight * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(srcBitmap, thumbWidth, thumbHeight, false)
    }

    // ========== EXPORT FUNCTIONS ==========

    /**
     * Direction options for GIF export
     */
    enum class GifDirection {
        NORMAL,     // Play frames in order
        REVERSE,    // Play frames in reverse order
        PINGPONG    // Play forward then backward
    }

    /**
     * Exports all frames as an animated GIF.
     * @param outputStream The output stream to write the GIF to
     * @param scale Scale factor for the output (1-16)
     * @param fps Frames per second (1-60)
     * @param loopCount Number of loops (0 = infinite, 1 = once, n = n times)
     * @param stackFrames If true, frames are stacked (cumulative), otherwise replaced
     * @param direction Playback direction (NORMAL, REVERSE, PINGPONG)
     * @return true if successful
     */
    fun exportToGif(
        outputStream: java.io.OutputStream,
        scale: Int = 1,
        fps: Int = 10,
        loopCount: Int = 0,
        stackFrames: Boolean = false,
        direction: GifDirection = GifDirection.NORMAL
    ): Boolean {
        saveCurrentFrameData()

        val encoder = AnimatedGifEncoder()
        // setRepeat: 0 = infinite, -1 = no repeat, n = repeat n times
        encoder.setRepeat(if (loopCount == 1) -1 else if (loopCount == 0) 0 else loopCount - 1)

        if (!encoder.start(outputStream)) {
            return false
        }

        val actualScale = scale.coerceIn(1, 16)
        val outputWidth = canvasWidth * actualScale
        val outputHeight = canvasHeight * actualScale
        val frameDelay = 1000 / fps.coerceIn(1, 60)

        // Build frame list based on direction
        val frameList = when (direction) {
            GifDirection.NORMAL -> frames.toList()
            GifDirection.REVERSE -> frames.reversed()
            GifDirection.PINGPONG -> {
                if (frames.size > 1) {
                    frames.toList() + frames.subList(1, frames.size - 1).reversed()
                } else {
                    frames.toList()
                }
            }
        }

        // For stacked frames, we accumulate pixels
        val accumulatedPixels = if (stackFrames) {
            IntArray(canvasWidth * canvasHeight)
        } else null

        for (frame in frameList) {
            val framePixels = if (stackFrames && accumulatedPixels != null) {
                // Stack: draw new frame on top of accumulated
                for (i in frame.pixelData.indices) {
                    val newColor = frame.pixelData[i]
                    if (newColor != Color.TRANSPARENT) {
                        accumulatedPixels[i] = newColor
                    }
                }
                accumulatedPixels
            } else {
                frame.pixelData
            }

            // Create bitmap for frame
            val frameBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            frameBitmap.setPixels(framePixels, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

            // Scale if needed
            val scaledBitmap = if (actualScale > 1) {
                Bitmap.createScaledBitmap(frameBitmap, outputWidth, outputHeight, false)
            } else {
                frameBitmap
            }

            encoder.setDelay(frameDelay)
            encoder.addFrame(scaledBitmap)
        }

        return encoder.finish()
    }

    /**
     * Exports all frames as a sprite sheet (horizontal strip or grid).
     * @param columns Number of columns in the grid (0 = single row)
     * @param scale Scale factor for the output
     * @return Bitmap containing all frames
     */
    fun exportToSpriteSheet(columns: Int = 0, scale: Int = 1): Bitmap {
        saveCurrentFrameData()

        val frameCount = frames.size
        val cols = if (columns <= 0) frameCount else columns
        val rows = (frameCount + cols - 1) / cols

        val frameWidth = canvasWidth * scale
        val frameHeight = canvasHeight * scale
        val sheetWidth = cols * frameWidth
        val sheetHeight = rows * frameHeight

        val spriteSheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(spriteSheet)

        for ((index, frame) in frames.withIndex()) {
            val col = index % cols
            val row = index / cols

            // Create bitmap for frame
            val frameBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            frameBitmap.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

            // Scale if needed
            val scaledBitmap = if (scale > 1) {
                Bitmap.createScaledBitmap(frameBitmap, frameWidth, frameHeight, false)
            } else {
                frameBitmap
            }

            val x = col * frameWidth
            val y = row * frameHeight
            canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), null)
        }

        return spriteSheet
    }

    /**
     * Data class for split options.
     */
    data class SplitOption(
        val cols: Int,
        val rows: Int,
        val frameWidth: Int,
        val frameHeight: Int,
        val frameCount: Int
    ) {
        fun getLabel(): String = "${frameCount} frames de ${frameWidth}x${frameHeight} (${cols}x${rows})"
    }

    /**
     * Returns available split options for the current canvas.
     * Only returns valid options where both dimensions divide evenly.
     */
    fun getSplitOptions(): List<SplitOption> {
        if (frames.size != 1) return emptyList() // Only works with single frame

        val options = mutableListOf<SplitOption>()
        val minSize = 8 // Minimum frame size

        // Find all valid divisors for both dimensions
        val possibleCols = mutableListOf<Int>()
        val possibleRows = mutableListOf<Int>()

        for (div in 2..canvasWidth / minSize) {
            if (canvasWidth % div == 0 && canvasWidth / div >= minSize) {
                possibleCols.add(div)
            }
        }

        for (div in 2..canvasHeight / minSize) {
            if (canvasHeight % div == 0 && canvasHeight / div >= minSize) {
                possibleRows.add(div)
            }
        }

        // Generate all valid combinations
        for (cols in possibleCols) {
            for (rows in possibleRows) {
                val frameW = canvasWidth / cols
                val frameH = canvasHeight / rows
                // Only include if frame is square or at least 8x8
                if (frameW >= minSize && frameH >= minSize) {
                    options.add(SplitOption(cols, rows, frameW, frameH, cols * rows))
                }
            }
        }

        // Sort by frame count (ascending)
        return options.sortedBy { it.frameCount }
    }

    /**
     * Splits the current single-frame sheet into multiple frames.
     * @param cols Number of columns to split into
     * @param rows Number of rows to split into
     * @return true if successful
     */
    fun splitSheetIntoFrames(cols: Int, rows: Int): Boolean {
        if (frames.size != 1) return false
        if (cols < 1 || rows < 1) return false

        val newFrameWidth = canvasWidth / cols
        val newFrameHeight = canvasHeight / rows

        // Verify even division
        if (newFrameWidth * cols != canvasWidth || newFrameHeight * rows != canvasHeight) {
            return false
        }

        // Save project snapshot before the split operation
        val totalFrames = cols * rows
        saveProjectSnapshot(
            ProjectChangeType.SPLIT_SHEET,
            "Split ${canvasWidth}x${canvasHeight} → ${totalFrames} frames ${newFrameWidth}x${newFrameHeight}"
        )

        // Save current frame data
        saveCurrentFrameData()
        val originalPixelData = frames[0].pixelData.clone()

        // Create new frames by extracting regions from the original
        val newFrames = mutableListOf<Frame>()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val newPixelData = IntArray(newFrameWidth * newFrameHeight)

                // Copy pixels from the region
                for (y in 0 until newFrameHeight) {
                    for (x in 0 until newFrameWidth) {
                        val srcX = col * newFrameWidth + x
                        val srcY = row * newFrameHeight + y
                        val srcIndex = srcY * canvasWidth + srcX
                        val destIndex = y * newFrameWidth + x
                        newPixelData[destIndex] = originalPixelData[srcIndex]
                    }
                }

                val newFrame = Frame(nextFrameId++, newPixelData)
                newFrames.add(newFrame)
            }
        }

        // Update canvas dimensions
        canvasWidth = newFrameWidth
        canvasHeight = newFrameHeight

        // Update history manager canvas size
        historyManager?.setCanvasSize(canvasWidth, canvasHeight)

        // Clear cached graphics that depend on canvas size
        checkerboardTile?.recycle()
        checkerboardTile = null
        checkerboardShader.shader = null

        // Replace frames list
        frames.clear()
        frames.addAll(newFrames)

        // Reset to first frame
        currentFrameIndex = 0
        pixelData = IntArray(canvasWidth * canvasHeight)
        frames[0].pixelData.copyInto(pixelData)

        // Recreate canvas bitmap
        canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        updateBitmap()

        // Clear cached bitmaps (wrong size now)
        frames.forEach { it.cachedBitmap = null }

        // Clear history (dimensions changed)
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()

        // Reset view
        centerCanvas()
        invalidate()

        // Notify callbacks
        onFrameChanged?.invoke(currentFrameIndex, frames.size)

        return true
    }

    /**
     * Imports a PNG image as a new frame.
     * @param bitmap The bitmap to import
     * @param resizeToFit If true, resize the image to fit the canvas; if false, crop/pad as needed
     * @return Index of the new frame
     */
    fun importPngAsFrame(bitmap: Bitmap, resizeToFit: Boolean = true): Int {
        saveCurrentFrameData()

        val importedBitmap = if (resizeToFit && (bitmap.width != canvasWidth || bitmap.height != canvasHeight)) {
            Bitmap.createScaledBitmap(bitmap, canvasWidth, canvasHeight, false)
        } else {
            bitmap
        }

        // Create new pixel data from bitmap
        val newPixelData = IntArray(canvasWidth * canvasHeight)
        val srcWidth = min(importedBitmap.width, canvasWidth)
        val srcHeight = min(importedBitmap.height, canvasHeight)

        val tempPixels = IntArray(srcWidth * srcHeight)
        importedBitmap.getPixels(tempPixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)

        for (y in 0 until srcHeight) {
            for (x in 0 until srcWidth) {
                newPixelData[y * canvasWidth + x] = tempPixels[y * srcWidth + x]
            }
        }

        // Create new frame
        val newFrame = Frame(nextFrameId++, newPixelData)

        // Insert after current frame
        val insertIndex = currentFrameIndex + 1
        frames.add(insertIndex, newFrame)

        // Switch to new frame
        currentFrameIndex = insertIndex
        pixelData = newPixelData.clone()
        updateBitmap()
        clearSelection()
        // New frame starts with empty history
        if (historyManager == null) {
            undoStackLegacy.clear()
            redoStackLegacy.clear()
        }
        notifyHistoryChanged()
        invalidate()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        return insertIndex
    }

    // ========== PROJECT SAVE/LOAD ==========

    /**
     * Serializes the project to a JSON string for saving.
     * @return JSON string containing all project data
     */
    fun serializeProject(): String {
        saveCurrentFrameData()

        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"version\":1,")
        sb.append("\"width\":$canvasWidth,")
        sb.append("\"height\":$canvasHeight,")
        sb.append("\"fps\":$animationFps,")
        sb.append("\"primaryColor\":$primaryColor,")
        sb.append("\"secondaryColor\":$secondaryColor,")
        sb.append("\"frames\":[")

        for ((i, frame) in frames.withIndex()) {
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":${frame.id},")
            sb.append("\"duration\":${frame.duration},")
            sb.append("\"data\":\"")
            // Encode pixel data as base64
            val byteBuffer = java.nio.ByteBuffer.allocate(frame.pixelData.size * 4)
            for (pixel in frame.pixelData) {
                byteBuffer.putInt(pixel)
            }
            sb.append(android.util.Base64.encodeToString(byteBuffer.array(), android.util.Base64.NO_WRAP))
            sb.append("\"}")
        }

        sb.append("]}")
        return sb.toString()
    }

    /**
     * Deserializes a project from a JSON string.
     * @param json JSON string containing project data
     * @return true if successful
     */
    fun deserializeProject(json: String): Boolean {
        try {
            val jsonObj = org.json.JSONObject(json)
            @Suppress("UNUSED_VARIABLE")
            val version = jsonObj.optInt("version", 1)  // Reserved for future format migrations
            val newWidth = jsonObj.getInt("width")
            val newHeight = jsonObj.getInt("height")
            val fps = jsonObj.optInt("fps", 10)
            val primary = jsonObj.optInt("primaryColor", Color.BLACK)
            val secondary = jsonObj.optInt("secondaryColor", Color.WHITE)
            val framesArray = jsonObj.getJSONArray("frames")

            // Validate dimensions
            if (newWidth < 8 || newWidth > 512 || newHeight < 8 || newHeight > 512) {
                return false
            }

            // Clear existing state
            frames.clear()
            // Clear all history for fresh project load
            historyManager?.clearAllHistory()
            undoStackLegacy.clear()
            redoStackLegacy.clear()
            nextFrameId = 1

            // Load frames
            for (i in 0 until framesArray.length()) {
                val frameObj = framesArray.getJSONObject(i)
                val id = frameObj.getInt("id")
                val duration = frameObj.optInt("duration", 100)
                val dataBase64 = frameObj.getString("data")

                val byteArray = android.util.Base64.decode(dataBase64, android.util.Base64.NO_WRAP)
                val byteBuffer = java.nio.ByteBuffer.wrap(byteArray)
                val pixelData = IntArray(newWidth * newHeight)
                for (j in pixelData.indices) {
                    pixelData[j] = byteBuffer.getInt()
                }

                frames.add(Frame(id, pixelData, duration))
                if (id >= nextFrameId) {
                    nextFrameId = id + 1
                }
            }

            // If no frames were loaded, create a blank one
            if (frames.isEmpty()) {
                frames.add(Frame(nextFrameId++, IntArray(newWidth * newHeight)))
            }

            // Apply settings
            canvasWidth = newWidth
            canvasHeight = newHeight
            animationFps = fps
            primaryColor = primary
            secondaryColor = secondary
            currentFrameIndex = 0

            // Update internal state
            pixelData = frames[0].pixelData.clone()
            canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            updateBitmap()
            clearSelection()
            centerCanvas()
            notifyHistoryChanged()
            invalidate()

            onFrameChanged?.invoke(currentFrameIndex, frames.size)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // ========== ROOM DATABASE PERSISTENCE ==========

    /**
     * Data class for Room database persistence
     */
    data class ProjectData(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val fps: Int,
        val primaryColor: Int,
        val secondaryColor: Int,
        val currentFrameIndex: Int,
        val frames: List<FrameDataForSave>
    )

    data class FrameDataForSave(
        val pixelData: IntArray,
        val duration: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FrameDataForSave) return false
            return pixelData.contentEquals(other.pixelData) && duration == other.duration
        }
        override fun hashCode(): Int = pixelData.contentHashCode() * 31 + duration
    }

    /**
     * Gets all project data for Room database saving.
     * Call this to get current state for auto-save.
     */
    fun getProjectData(): ProjectData {
        saveCurrentFrameData()
        return ProjectData(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            fps = animationFps,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            currentFrameIndex = currentFrameIndex,
            frames = frames.map { FrameDataForSave(it.pixelData.clone(), it.duration) }
        )
    }

    /**
     * Loads project data from Room database.
     * @return true if successful
     */
    fun loadProjectData(
        width: Int,
        height: Int,
        fps: Int,
        primary: Int,
        secondary: Int,
        frameIndex: Int,
        frameDataList: List<FrameDataForSave>
    ): Boolean {
        try {
            // Validate dimensions
            if (width < 8 || width > 8192 || height < 8 || height > 8192) {
                return false
            }

            // Clear existing state
            frames.clear()
            historyManager?.clearAllHistory()
            undoStackLegacy.clear()
            redoStackLegacy.clear()
            nextFrameId = 1

            // Load frames
            for (frameData in frameDataList) {
                frames.add(Frame(nextFrameId++, frameData.pixelData.clone(), frameData.duration))
            }

            // If no frames were loaded, create a blank one
            if (frames.isEmpty()) {
                frames.add(Frame(nextFrameId++, IntArray(width * height)))
            }

            // Apply settings
            canvasWidth = width
            canvasHeight = height
            animationFps = fps
            primaryColor = primary
            secondaryColor = secondary
            currentFrameIndex = frameIndex.coerceIn(0, frames.lastIndex)

            // Notify history manager of canvas size
            historyManager?.setCanvasSize(canvasWidth, canvasHeight)

            // Update internal state
            pixelData = frames[currentFrameIndex].pixelData.clone()
            canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            updateBitmap()
            clearSelection()
            centerCanvas()
            notifyHistoryChanged()
            invalidate()

            onFrameChanged?.invoke(currentFrameIndex, frames.size)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Creates a new project from an imported bitmap image.
     * The canvas is sized to match the image dimensions.
     */
    fun newProjectFromBitmap(bitmap: Bitmap) {
        val newWidth = bitmap.width.coerceIn(1, 8192)
        val newHeight = bitmap.height.coerceIn(1, 8192)

        canvasWidth = newWidth
        canvasHeight = newHeight

        // Notify history manager of new canvas size for adaptive optimization
        historyManager?.setCanvasSize(canvasWidth, canvasHeight)

        pixelData = IntArray(canvasWidth * canvasHeight)
        canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)

        // Scale bitmap if needed (if it was coerced)
        val scaledBitmap = if (bitmap.width != newWidth || bitmap.height != newHeight) {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false)
        } else {
            bitmap
        }

        // Copy pixels from bitmap to canvas
        scaledBitmap.getPixels(pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

        frames.clear()
        frames.add(Frame(nextFrameId++, pixelData.clone()))
        currentFrameIndex = 0

        // Clear layers
        layers.clear()
        currentLayerIndex = -1

        // Clear all history for new project
        historyManager?.clearAllHistory()
        undoStackLegacy.clear()
        redoStackLegacy.clear()
        clearSelection()
        updateBitmap()
        centerCanvas()
        notifyHistoryChanged()
        invalidate()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
    }

    /**
     * Creates a new empty project with specified dimensions.
     */
    fun newProject(width: Int, height: Int) {
        val newWidth = width.coerceIn(8, 4096)
        val newHeight = height.coerceIn(8, 4096)

        canvasWidth = newWidth
        canvasHeight = newHeight

        // Notify history manager of new canvas size for adaptive optimization
        historyManager?.setCanvasSize(canvasWidth, canvasHeight)

        pixelData = IntArray(canvasWidth * canvasHeight)
        canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)

        frames.clear()
        frames.add(Frame(nextFrameId++, pixelData.clone()))
        currentFrameIndex = 0

        // Clear all history for new project
        historyManager?.clearAllHistory()
        undoStackLegacy.clear()
        redoStackLegacy.clear()
        clearSelection()

        // Clear cached graphics that depend on canvas size
        checkerboardTile?.recycle()
        checkerboardTile = null
        checkerboardShader.shader = null

        // Reset view mode to frame mode
        viewMode = ViewMode.FRAME

        // Clear project snapshots
        clearProjectSnapshots()

        updateBitmap()
        centerCanvas()
        notifyHistoryChanged()
        invalidate()

        onFrameChanged?.invoke(currentFrameIndex, frames.size)
        onViewModeChanged?.invoke(viewMode)
    }

    // ========== CANVAS SIZE WITH FRAMES ==========

    fun setCanvasSize(width: Int, height: Int) {
        val newWidth = width.coerceIn(8, 8192)
        val newHeight = height.coerceIn(8, 8192)

        if (newWidth != canvasWidth || newHeight != canvasHeight) {
            saveCurrentFrameData()
            saveToHistory(ActionType.RESIZE)

            // Resize all frames (crop/extend without scaling)
            for (frame in frames) {
                val newPixelData = IntArray(newWidth * newHeight)

                // Copy existing pixels
                for (y in 0 until min(canvasHeight, newHeight)) {
                    for (x in 0 until min(canvasWidth, newWidth)) {
                        newPixelData[y * newWidth + x] = frame.pixelData[y * canvasWidth + x]
                    }
                }

                frame.pixelData = newPixelData
            }

            canvasWidth = newWidth
            canvasHeight = newHeight

            // Notify history manager of new canvas size for adaptive optimization
            historyManager?.setCanvasSize(canvasWidth, canvasHeight)

            // Clear cached graphics that depend on canvas size
            checkerboardTile?.recycle()
            checkerboardTile = null
            checkerboardShader.shader = null

            // Clear frame cached bitmaps (wrong size now)
            frames.forEach { it.cachedBitmap = null }

            pixelData = frames[currentFrameIndex].pixelData.clone()
            canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            updateBitmap()
            centerCanvas()
            invalidate()
        }
    }

    /**
     * Resize canvas with content scaling (stretches/shrinks the artwork)
     */
    fun resizeCanvas(newWidth: Int, newHeight: Int) {
        val targetWidth = newWidth.coerceIn(8, 8192)
        val targetHeight = newHeight.coerceIn(8, 8192)

        if (targetWidth == canvasWidth && targetHeight == canvasHeight) return

        saveCurrentFrameData()
        saveToHistory(ActionType.RESIZE)

        // Resize all frames with scaling
        for (frame in frames) {
            // Create bitmap from current frame data
            val sourceBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            sourceBitmap.setPixels(frame.pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

            // Scale bitmap to new size (nearest neighbor for pixel art)
            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, false)

            // Extract pixels from scaled bitmap
            val newPixelData = IntArray(targetWidth * targetHeight)
            scaledBitmap.getPixels(newPixelData, 0, targetWidth, 0, 0, targetWidth, targetHeight)

            frame.pixelData = newPixelData
        }

        canvasWidth = targetWidth
        canvasHeight = targetHeight

        // Notify history manager of new canvas size for adaptive optimization
        historyManager?.setCanvasSize(canvasWidth, canvasHeight)

        // Clear cached graphics that depend on canvas size
        checkerboardTile?.recycle()
        checkerboardTile = null
        checkerboardShader.shader = null

        // Clear frame cached bitmaps (wrong size now)
        frames.forEach { it.cachedBitmap = null }

        pixelData = frames[currentFrameIndex].pixelData.clone()
        canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        updateBitmap()
        centerCanvas()
        invalidate()
    }

    // ========== LAYER MANAGEMENT FUNCTIONS ==========

    fun getLayerCount(): Int = layers.size

    fun getCurrentLayerIndex(): Int = currentLayerIndex

    fun getCurrentLayer(): Layer? = layers.getOrNull(currentLayerIndex)

    fun getLayers(): List<Layer> = layers.toList()

    @Suppress("unused")
    fun getLayer(index: Int): Layer? = layers.getOrNull(index)

    /**
     * Adds a new pixel layer.
     * @param name Name of the layer
     * @return Index of the new layer
     */
    @Suppress("unused")
    fun addPixelLayer(name: String = "Calque ${nextLayerId}"): Int {
        val newLayer = Layer(
            id = nextLayerId++,
            name = name,
            type = LayerType.PIXEL,
            pixelData = IntArray(canvasWidth * canvasHeight)
        )
        layers.add(newLayer)
        currentLayerIndex = layers.size - 1
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        onLayerSelected?.invoke(newLayer)
        invalidate()
        return currentLayerIndex
    }

    /**
     * Imports an image as a new layer.
     * @param bitmap The bitmap to import
     * @param name Name for the layer
     * @return Index of the new layer
     */
    fun addImageLayer(bitmap: Bitmap, name: String = "Image ${nextLayerId}"): Int {
        val newLayer = Layer(
            id = nextLayerId++,
            name = name,
            type = LayerType.IMAGE,
            imageBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false),
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            scale = 1f,
            offsetX = 0,
            offsetY = 0
        )
        layers.add(newLayer)
        currentLayerIndex = layers.size - 1
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        onLayerSelected?.invoke(newLayer)
        invalidate()
        return currentLayerIndex
    }

    /**
     * Deletes a layer by index.
     */
    fun deleteLayer(index: Int): Boolean {
        if (index < 0 || index >= layers.size) return false

        layers.removeAt(index)

        // Adjust current index
        if (currentLayerIndex >= layers.size) {
            currentLayerIndex = (layers.size - 1).coerceAtLeast(0)
        }

        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        onLayerSelected?.invoke(layers.getOrNull(currentLayerIndex))
        invalidate()
        return true
    }

    /**
     * Selects a layer by index.
     */
    fun selectLayer(index: Int) {
        if (index < 0 || index >= layers.size) return
        currentLayerIndex = index
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        onLayerSelected?.invoke(layers[currentLayerIndex])
        invalidate()
    }

    /**
     * Moves a layer up (toward front).
     */
    fun moveLayerUp(index: Int): Boolean {
        if (index < 0 || index >= layers.size - 1) return false
        val layer = layers.removeAt(index)
        layers.add(index + 1, layer)
        if (currentLayerIndex == index) currentLayerIndex = index + 1
        else if (currentLayerIndex == index + 1) currentLayerIndex = index
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        invalidate()
        return true
    }

    /**
     * Moves a layer down (toward back).
     */
    fun moveLayerDown(index: Int): Boolean {
        if (index <= 0 || index >= layers.size) return false
        val layer = layers.removeAt(index)
        layers.add(index - 1, layer)
        if (currentLayerIndex == index) currentLayerIndex = index - 1
        else if (currentLayerIndex == index - 1) currentLayerIndex = index
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        invalidate()
        return true
    }

    /**
     * Toggles layer visibility.
     */
    @Suppress("unused")
    fun toggleLayerVisibility(index: Int) {
        layers.getOrNull(index)?.let {
            it.visible = !it.visible
            onLayerChanged?.invoke(currentLayerIndex, layers.size)
            invalidate()
        }
    }

    /**
     * Sets layer opacity.
     */
    fun setLayerOpacity(index: Int, opacity: Float) {
        layers.getOrNull(index)?.let {
            it.opacity = opacity.coerceIn(0f, 1f)
            invalidate()
        }
    }

    /**
     * Sets layer position offset.
     */
    fun setLayerOffset(index: Int, offsetX: Int, offsetY: Int) {
        layers.getOrNull(index)?.let {
            it.offsetX = offsetX
            it.offsetY = offsetY
            invalidate()
        }
    }

    /**
     * Sets layer scale (for image layers).
     */
    fun setLayerScale(index: Int, scale: Float) {
        layers.getOrNull(index)?.let {
            if (it.type == LayerType.IMAGE) {
                it.scale = scale.coerceIn(0.1f, 10f)
                invalidate()
            }
        }
    }

    /**
     * Duplicates a layer.
     */
    fun duplicateLayer(index: Int): Int {
        val original = layers.getOrNull(index) ?: return -1

        val duplicate = Layer(
            id = nextLayerId++,
            name = "${original.name} (copie)",
            type = original.type,
            visible = original.visible,
            opacity = original.opacity,
            offsetX = original.offsetX + 10,  // Slight offset to show it's a copy
            offsetY = original.offsetY + 10,
            scale = original.scale,
            pixelData = original.pixelData?.clone(),
            imageBitmap = original.imageBitmap?.copy(Bitmap.Config.ARGB_8888, false),
            originalWidth = original.originalWidth,
            originalHeight = original.originalHeight
        )

        layers.add(index + 1, duplicate)
        currentLayerIndex = index + 1
        onLayerChanged?.invoke(currentLayerIndex, layers.size)
        onLayerSelected?.invoke(duplicate)
        invalidate()
        return currentLayerIndex
    }

    /**
     * Renames a layer.
     */
    fun renameLayer(index: Int, newName: String) {
        layers.getOrNull(index)?.let {
            it.name = newName
            onLayerChanged?.invoke(currentLayerIndex, layers.size)
        }
    }

    /**
     * Gets a thumbnail for a layer.
     */
    @Suppress("unused")
    fun getLayerThumbnail(index: Int, maxSize: Int = 64): Bitmap? {
        val layer = layers.getOrNull(index) ?: return null

        val srcBitmap = when (layer.type) {
            LayerType.PIXEL -> {
                val data = layer.pixelData ?: return null
                val size = sqrt(data.size.toDouble()).toInt()
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                bmp.setPixels(data, 0, size, 0, 0, size, size)
                bmp
            }
            LayerType.IMAGE -> layer.imageBitmap ?: return null
        }

        val scale = minOf(maxSize.toFloat() / srcBitmap.width, maxSize.toFloat() / srcBitmap.height)
        val thumbWidth = (srcBitmap.width * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (srcBitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(srcBitmap, thumbWidth, thumbHeight, false)
    }

    /**
     * Clears all layers.
     */
    fun clearAllLayers() {
        layers.clear()
        currentLayerIndex = 0
        nextLayerId = 1
        onLayerChanged?.invoke(0, 0)
        onLayerSelected?.invoke(null)
        invalidate()
    }

    // ========== LAYER DRAWING ==========

    /**
     * Draws all visible layers on the canvas.
     */
    private fun drawLayers(canvas: Canvas) {
        for ((_, layer) in layers.withIndex()) {
            if (!layer.visible) continue

            val layerPaint = Paint().apply {
                alpha = (layer.opacity * 255).toInt()
                isAntiAlias = false
                isFilterBitmap = false
            }

            when (layer.type) {
                LayerType.PIXEL -> {
                    layer.pixelData?.let { data ->
                        val size = sqrt(data.size.toDouble()).toInt()
                        val layerBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        layerBitmap.setPixels(data, 0, size, 0, 0, size, size)
                        canvas.drawBitmap(
                            layerBitmap,
                            layer.offsetX.toFloat(),
                            layer.offsetY.toFloat(),
                            layerPaint
                        )
                    }
                }
                LayerType.IMAGE -> {
                    layer.imageBitmap?.let { bitmap ->
                        val scaledWidth = (layer.originalWidth * layer.scale).toInt()
                        val scaledHeight = (layer.originalHeight * layer.scale).toInt()

                        if (scaledWidth > 0 && scaledHeight > 0) {
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
                            canvas.drawBitmap(
                                scaledBitmap,
                                layer.offsetX.toFloat(),
                                layer.offsetY.toFloat(),
                                layerPaint
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws the selection/bounds of the current layer when MOVE_LAYER tool is active.
     */
    private fun drawLayerBounds(canvas: Canvas) {
        if (currentTool != Tool.MOVE_LAYER) return
        val layer = layers.getOrNull(currentLayerIndex) ?: return

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        when (layer.type) {
            LayerType.PIXEL -> {
                val size = layer.pixelData?.let { sqrt(it.size.toDouble()).toInt() } ?: return
                left = offsetX + layer.offsetX * zoomLevel
                top = offsetY + layer.offsetY * zoomLevel
                right = left + size * zoomLevel
                bottom = top + size * zoomLevel
            }
            LayerType.IMAGE -> {
                val scaledWidth = layer.originalWidth * layer.scale
                val scaledHeight = layer.originalHeight * layer.scale
                left = offsetX + layer.offsetX * zoomLevel
                top = offsetY + layer.offsetY * zoomLevel
                right = left + scaledWidth * zoomLevel
                bottom = top + scaledHeight * zoomLevel
            }
        }

        // Draw bounds rectangle
        canvas.drawRect(left, top, right, bottom, layerBoundsPaint)

        // Draw resize handles for image layers
        if (layer.type == LayerType.IMAGE) {
            // Top-left
            canvas.drawRect(left - handleSize/2, top - handleSize/2, left + handleSize/2, top + handleSize/2, layerHandlePaint)
            // Top-right
            canvas.drawRect(right - handleSize/2, top - handleSize/2, right + handleSize/2, top + handleSize/2, layerHandlePaint)
            // Bottom-left
            canvas.drawRect(left - handleSize/2, bottom - handleSize/2, left + handleSize/2, bottom + handleSize/2, layerHandlePaint)
            // Bottom-right
            canvas.drawRect(right - handleSize/2, bottom - handleSize/2, right + handleSize/2, bottom + handleSize/2, layerHandlePaint)
        }
    }

    /**
     * Checks if a point hits a resize handle.
     * @return Handle index (0-3) or -1 if no hit
     */
    private fun hitTestResizeHandle(screenX: Float, screenY: Float): Int {
        val layer = layers.getOrNull(currentLayerIndex) ?: return -1
        if (layer.type != LayerType.IMAGE) return -1

        val scaledWidth = layer.originalWidth * layer.scale
        val scaledHeight = layer.originalHeight * layer.scale
        val left = offsetX + layer.offsetX * zoomLevel
        val top = offsetY + layer.offsetY * zoomLevel
        val right = left + scaledWidth * zoomLevel
        val bottom = top + scaledHeight * zoomLevel

        val hitRadius = handleSize

        // Check each corner
        if (abs(screenX - left) < hitRadius && abs(screenY - top) < hitRadius) return 0  // TL
        if (abs(screenX - right) < hitRadius && abs(screenY - top) < hitRadius) return 1  // TR
        if (abs(screenX - left) < hitRadius && abs(screenY - bottom) < hitRadius) return 2  // BL
        if (abs(screenX - right) < hitRadius && abs(screenY - bottom) < hitRadius) return 3  // BR

        return -1
    }

    /**
     * Checks if a point is inside the current layer bounds.
     */
    private fun isPointInLayerBounds(screenX: Float, screenY: Float): Boolean {
        val layer = layers.getOrNull(currentLayerIndex) ?: return false

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        when (layer.type) {
            LayerType.PIXEL -> {
                val size = layer.pixelData?.let { sqrt(it.size.toDouble()).toInt() } ?: return false
                left = offsetX + layer.offsetX * zoomLevel
                top = offsetY + layer.offsetY * zoomLevel
                right = left + size * zoomLevel
                bottom = top + size * zoomLevel
            }
            LayerType.IMAGE -> {
                val scaledWidth = layer.originalWidth * layer.scale
                val scaledHeight = layer.originalHeight * layer.scale
                left = offsetX + layer.offsetX * zoomLevel
                top = offsetY + layer.offsetY * zoomLevel
                right = left + scaledWidth * zoomLevel
                bottom = top + scaledHeight * zoomLevel
            }
        }

        return screenX >= left && screenX <= right && screenY >= top && screenY <= bottom
    }

    /**
     * Handles touch for MOVE_LAYER tool.
     */
    private fun handleLayerTouch(event: MotionEvent): Boolean {
        val layer = layers.getOrNull(currentLayerIndex) ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check for resize handle hit first (for image layers)
                val handleHit = hitTestResizeHandle(event.x, event.y)
                if (handleHit >= 0) {
                    isResizingLayer = true
                    resizeCorner = handleHit
                    layerOriginalScale = layer.scale
                    layerDragStartX = event.x
                    layerDragStartY = event.y
                    return true
                }

                // Check if touching inside layer bounds
                if (isPointInLayerBounds(event.x, event.y)) {
                    isMovingLayer = true
                    layerDragStartX = event.x
                    layerDragStartY = event.y
                    layerOriginalOffsetX = layer.offsetX
                    layerOriginalOffsetY = layer.offsetY
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMovingLayer) {
                    val dx = (event.x - layerDragStartX) / zoomLevel
                    val dy = (event.y - layerDragStartY) / zoomLevel
                    layer.offsetX = layerOriginalOffsetX + dx.toInt()
                    layer.offsetY = layerOriginalOffsetY + dy.toInt()
                    onLayerSelected?.invoke(layer)
                    invalidate()
                    return true
                }

                if (isResizingLayer && layer.type == LayerType.IMAGE) {
                    // Calculate scale based on drag distance from corner
                    val dx = event.x - layerDragStartX
                    val dy = event.y - layerDragStartY
                    val dragDistance = sqrt(dx * dx + dy * dy)
                    val sign = if (dx + dy > 0) 1 else -1
                    val scaleDelta = (sign * dragDistance / 100f)
                    layer.scale = (layerOriginalScale + scaleDelta).coerceIn(0.1f, 10f)
                    onLayerSelected?.invoke(layer)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isMovingLayer = false
                isResizingLayer = false
                resizeCorner = -1
            }
        }

        return false
    }

    /**
     * Flattens all layers into the main canvas.
     */
    fun flattenLayers(): Boolean {
        if (layers.isEmpty()) return false

        saveToHistory()

        // Create a new bitmap to composite all layers
        val compositeBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val compositeCanvas = Canvas(compositeBitmap)

        // First draw the current pixel data
        compositeCanvas.drawBitmap(canvasBitmap, 0f, 0f, null)

        // Then draw all visible layers
        for (layer in layers) {
            if (!layer.visible) continue

            val layerPaint = Paint().apply {
                alpha = (layer.opacity * 255).toInt()
                isAntiAlias = false
                isFilterBitmap = false
            }

            when (layer.type) {
                LayerType.PIXEL -> {
                    layer.pixelData?.let { data ->
                        val size = sqrt(data.size.toDouble()).toInt()
                        val layerBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        layerBitmap.setPixels(data, 0, size, 0, 0, size, size)
                        compositeCanvas.drawBitmap(
                            layerBitmap,
                            layer.offsetX.toFloat(),
                            layer.offsetY.toFloat(),
                            layerPaint
                        )
                    }
                }
                LayerType.IMAGE -> {
                    layer.imageBitmap?.let { bitmap ->
                        val scaledWidth = (layer.originalWidth * layer.scale).toInt()
                        val scaledHeight = (layer.originalHeight * layer.scale).toInt()

                        if (scaledWidth > 0 && scaledHeight > 0) {
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
                            compositeCanvas.drawBitmap(
                                scaledBitmap,
                                layer.offsetX.toFloat(),
                                layer.offsetY.toFloat(),
                                layerPaint
                            )
                        }
                    }
                }
            }
        }

        // Copy composite back to pixel data
        compositeBitmap.getPixels(pixelData, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
        updateBitmap()

        // Clear layers
        clearAllLayers()

        invalidate()
        return true
    }

    // ========== REFERENCE IMAGE FUNCTIONS ==========

    /**
     * Sets a reference/background image.
     * The canvas will be drawn on top of this image.
     */
    fun setReferenceImage(bitmap: Bitmap) {
        referenceImage = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        // Reset transform to center the reference under the canvas
        referenceScale = 1f
        referenceOffsetX = 0f
        referenceOffsetY = 0f
        onReferenceChanged?.invoke(true)
        invalidate()
    }

    /**
     * Clears the reference image.
     */
    fun clearReferenceImage() {
        referenceImage = null
        referenceScale = 1f
        referenceOffsetX = 0f
        referenceOffsetY = 0f
        onReferenceChanged?.invoke(false)
        invalidate()
    }

    /**
     * Returns true if a reference image is set.
     */
    fun hasReferenceImage(): Boolean = referenceImage != null

    /**
     * Gets the reference image dimensions.
     */
    fun getReferenceImageSize(): Pair<Int, Int>? {
        val ref = referenceImage ?: return null
        return Pair(ref.width, ref.height)
    }

    /**
     * Gets the scaled reference image dimensions.
     */
    fun getScaledReferenceSize(): Pair<Float, Float>? {
        val ref = referenceImage ?: return null
        return Pair(ref.width * referenceScale, ref.height * referenceScale)
    }

    /**
     * Sets reference transformation.
     */
    @Suppress("unused")
    fun setReferenceTransform(scale: Float, offsetX: Float, offsetY: Float) {
        referenceScale = scale.coerceIn(0.01f, 50f)
        referenceOffsetX = offsetX
        referenceOffsetY = offsetY
    }

    /**
     * Fits the reference image to fill the canvas area.
     */
    fun fitReferenceToCanvas() {
        val ref = referenceImage ?: return
        val scaleX = canvasWidth.toFloat() / ref.width
        val scaleY = canvasHeight.toFloat() / ref.height
        referenceScale = maxOf(scaleX, scaleY)
        // Center the reference
        val scaledWidth = ref.width * referenceScale
        val scaledHeight = ref.height * referenceScale
        referenceOffsetX = (canvasWidth - scaledWidth) / 2f
        referenceOffsetY = (canvasHeight - scaledHeight) / 2f
    }

    /**
     * Draws the reference image behind everything.
     * The reference can be freely scaled and positioned under the canvas.
     * Always at full opacity - the canvas transparency is what lets you see through.
     */
    private fun drawReferenceImage(canvas: Canvas) {
        if (!referenceVisible) return
        val ref = referenceImage ?: return

        referencePaint.alpha = (referenceOpacity * 255).toInt().coerceIn(0, 255)

        // Calculate destination rectangle based on scale and offset
        val scaledWidth = ref.width * referenceScale
        val scaledHeight = ref.height * referenceScale

        val destRect = RectF(
            referenceOffsetX,
            referenceOffsetY,
            referenceOffsetX + scaledWidth,
            referenceOffsetY + scaledHeight
        )

        canvas.drawBitmap(ref, null, destRect, referencePaint)

        // Draw border when adjusting
        if (isAdjustingReference) {
            canvas.drawRect(destRect, referenceBorderPaint)
        }
    }

    /**
     * Handles touch events when in reference adjustment mode.
     * Two-finger gestures simultaneously zoom AND pan the reference image.
     * One-finger drag also pans the reference.
     * The canvas view stays FIXED during adjustment.
     */
    private fun handleReferenceTouch(event: MotionEvent): Boolean {
        if (!isAdjustingReference || referenceImage == null) return false

        // Ensure canvas stays at saved position during adjustment
        if (zoomLevel != savedZoomLevel || offsetX != savedOffsetX || offsetY != savedOffsetY) {
            zoomLevel = savedZoomLevel
            offsetX = savedOffsetX
            offsetY = savedOffsetY
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                refPointerCount = 1
                refLastFocusX = event.x
                refLastFocusY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                refPointerCount = event.pointerCount
                if (event.pointerCount >= 2) {
                    // Initialize for two-finger gesture
                    refLastFocusX = (event.getX(0) + event.getX(1)) / 2
                    refLastFocusY = (event.getY(0) + event.getY(1)) / 2
                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)
                    refLastSpan = sqrt(dx * dx + dy * dy)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // Two-finger gesture: simultaneous zoom AND pan
                    val focusX = (event.getX(0) + event.getX(1)) / 2
                    val focusY = (event.getY(0) + event.getY(1)) / 2
                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)
                    val span = sqrt(dx * dx + dy * dy)

                    if (refLastSpan > 10f && span > 10f) {
                        // Calculate zoom factor
                        val scaleFactor = span / refLastSpan

                        // Convert focus point to canvas coordinates (using saved canvas state)
                        val canvasFocusX = (focusX - savedOffsetX) / savedZoomLevel
                        val canvasFocusY = (focusY - savedOffsetY) / savedZoomLevel

                        // Calculate new scale
                        val newScale = (referenceScale * scaleFactor).coerceIn(0.01f, 50f)
                        val actualScaleFactor = newScale / referenceScale

                        // Adjust offset to scale around focus point
                        referenceOffsetX = canvasFocusX - (canvasFocusX - referenceOffsetX) * actualScaleFactor
                        referenceOffsetY = canvasFocusY - (canvasFocusY - referenceOffsetY) * actualScaleFactor
                        referenceScale = newScale
                    }

                    // Pan: move reference based on focus movement
                    val panDx = (focusX - refLastFocusX) / savedZoomLevel
                    val panDy = (focusY - refLastFocusY) / savedZoomLevel
                    referenceOffsetX += panDx
                    referenceOffsetY += panDy

                    refLastFocusX = focusX
                    refLastFocusY = focusY
                    refLastSpan = span
                    return true

                } else if (event.pointerCount == 1) {
                    // Single finger: pan only
                    val panDx = (event.x - refLastFocusX) / savedZoomLevel
                    val panDy = (event.y - refLastFocusY) / savedZoomLevel
                    referenceOffsetX += panDx
                    referenceOffsetY += panDy

                    refLastFocusX = event.x
                    refLastFocusY = event.y
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                refPointerCount = 0
                refLastSpan = 0f
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                refPointerCount = event.pointerCount - 1
                // Reset tracking for remaining finger(s)
                if (refPointerCount == 1) {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex < event.pointerCount) {
                        refLastFocusX = event.getX(remainingIndex)
                        refLastFocusY = event.getY(remainingIndex)
                    }
                }
                refLastSpan = 0f
                return true
            }
        }
        return false
    }

    /**
     * Applies a simple box blur to a bitmap.
     * Used for onion skin blur effect.
     */
    @Suppress("unused")
    private fun applySimpleBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height)

        // Simple box blur - horizontal pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var count = 0

                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        val pixel = pixels[y * width + nx]
                        a += Color.alpha(pixel)
                        r += Color.red(pixel)
                        g += Color.green(pixel)
                        b += Color.blue(pixel)
                        count++
                    }
                }

                if (count > 0) {
                    result[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
                }
            }
        }

        // Vertical pass
        val finalResult = IntArray(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var count = 0

                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        val pixel = result[ny * width + x]
                        a += Color.alpha(pixel)
                        r += Color.red(pixel)
                        g += Color.green(pixel)
                        b += Color.blue(pixel)
                        count++
                    }
                }

                if (count > 0) {
                    finalResult[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
                }
            }
        }

        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(finalResult, 0, width, 0, 0, width, height)
        return blurred
    }

}
