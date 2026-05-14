package com.Atom2Universe.app.pixelart

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import com.Atom2Universe.app.LocaleHelper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.Atom2Universe.app.R
import com.Atom2Universe.app.SimpleColorPickerDialog
import com.Atom2Universe.app.pixelart.data.FrameData
import com.Atom2Universe.app.pixelart.data.PixelArtRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.Atom2Universe.app.util.enableImmersiveMode

class PixelArtEditorActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var pixelCanvas: PixelCanvas
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnRedo: MaterialButton
    private lateinit var txtZoom: TextView
    private lateinit var btnCanvasSize: MaterialButton
    private lateinit var colorPrimary: View
    private lateinit var colorSecondary: View
    private lateinit var colorPalette: GridLayout
    private lateinit var selectionActionsRow: HorizontalScrollView

    // Tool buttons
    private lateinit var toolPencil: MaterialButton
    private lateinit var toolEraser: MaterialButton
    private lateinit var toolFill: MaterialButton
    private lateinit var toolPicker: MaterialButton
    private lateinit var toolShapesDropdown: MaterialButton  // Single button for all shapes
    private lateinit var toolSelect: MaterialButton
    private lateinit var toolMoveLayer: MaterialButton
    private lateinit var toolMarker: MaterialButton
    private lateinit var toolBrush: MaterialButton
    private val allToolButtons: MutableList<MaterialButton> = mutableListOf()

    // Current selected shape type for the dropdown
    private var currentShapeType = PixelCanvas.Tool.RECTANGLE

    // Shape icons mapping (unicode characters)
    private val shapeIcons = mapOf(
        PixelCanvas.Tool.LINE to "╱",
        PixelCanvas.Tool.DASHED_LINE to "┄",
        PixelCanvas.Tool.RECTANGLE to "▢",
        PixelCanvas.Tool.ROUNDED_RECT to "▢̲",
        PixelCanvas.Tool.CIRCLE to "○",
        PixelCanvas.Tool.TRIANGLE to "△",
        PixelCanvas.Tool.OVAL to "⬭",
        PixelCanvas.Tool.HEXAGON to "⬡",
        PixelCanvas.Tool.DIAMOND to "◇",
        PixelCanvas.Tool.STAR to "☆",
        PixelCanvas.Tool.ARROW to "→",
        PixelCanvas.Tool.PENTAGON to "⬠"
    )

    // Brush size views (vertical slider on left side)
    private lateinit var brushSizeVerticalContainer: LinearLayout
    private lateinit var brushSizeVisualIndicator: View
    private lateinit var txtBrushSizeIndicator: TextView
    private lateinit var sliderBrushSizeVertical: SeekBar
    private lateinit var btnBrushSizePlus: TextView
    private lateinit var btnBrushSizeMinus: TextView

    // Brush size persistence per tool (each tool remembers its own size)
    private val toolBrushSizes = mutableMapOf<PixelCanvas.Tool, Int>().apply {
        put(PixelCanvas.Tool.MARKER, 1)  // Default: 1px for marker
        put(PixelCanvas.Tool.BRUSH, 5)   // Default: 5px for brush
        // Shape tools default to 1px
        put(PixelCanvas.Tool.LINE, 1)
        put(PixelCanvas.Tool.DASHED_LINE, 1)
        put(PixelCanvas.Tool.RECTANGLE, 1)
        put(PixelCanvas.Tool.ROUNDED_RECT, 1)
        put(PixelCanvas.Tool.CIRCLE, 1)
        put(PixelCanvas.Tool.TRIANGLE, 1)
        put(PixelCanvas.Tool.OVAL, 1)
        put(PixelCanvas.Tool.HEXAGON, 1)
        put(PixelCanvas.Tool.DIAMOND, 1)
        put(PixelCanvas.Tool.STAR, 1)
        put(PixelCanvas.Tool.ARROW, 1)
        put(PixelCanvas.Tool.PENTAGON, 1)
    }
    private var brushSliderShowTime: Long = 0
    private val BRUSH_SLIDER_MIN_DURATION = 5000L  // 5 seconds minimum

    // Timeline views
    private lateinit var timelineFrames: LinearLayout
    private lateinit var txtFrameIndicator: TextView
    private lateinit var btnPlayPause: ImageButton

    // Onion skin views (floating slider like brush size)
    private lateinit var btnOnionOpacity: TextView
    private lateinit var onionOpacityVerticalContainer: LinearLayout
    private lateinit var txtOnionOpacity: TextView
    private lateinit var sliderOnionOpacity: SeekBar
    private lateinit var onionOpacityVisualIndicator: View
    private lateinit var btnOnionPlus: TextView
    private lateinit var btnOnionMinus: TextView

    // Layer views
    private lateinit var layerActionsRow: HorizontalScrollView
    private lateinit var txtLayerInfo: TextView
    private lateinit var txtLayerPosition: TextView
    private lateinit var txtLayerScale: TextView

    // Sheet mode views
    private lateinit var btnSheetMode: ImageButton
    private lateinit var sheetConfigPanel: LinearLayout
    private lateinit var spinnerSheetColumns: Spinner
    private lateinit var txtSheetInfo: TextView
    private lateinit var btnFitSheet: ImageButton
    private lateinit var chkSheetGridLines: CheckBox
    private lateinit var timelinePanel: LinearLayout

    // Animation preview window views
    private lateinit var btnSheetPreview: ImageButton
    private lateinit var previewWindow: FrameLayout
    private lateinit var previewHeader: LinearLayout
    private lateinit var previewImage: ImageView
    private lateinit var btnPreviewClose: ImageButton
    private lateinit var btnPreviewPlay: ImageButton
    private lateinit var btnPreviewPrev: ImageButton
    private lateinit var btnPreviewNext: ImageButton

    // Animation preview state
    private var isPreviewPlaying = false
    private var previewFrameIndex = 0
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    // Preview window drag state
    private var previewDragStartX = 0f
    private var previewDragStartY = 0f
    private var previewWindowStartX = 0f
    private var previewWindowStartY = 0f

    // Preview window pinch-to-zoom state
    private var previewScaleGestureDetector: ScaleGestureDetector? = null

    // Default color palette
    private val defaultPalette = intArrayOf(
        Color.BLACK, Color.WHITE,
        Color.rgb(127, 127, 127), // Gray
        Color.rgb(200, 0, 0), // Red
        Color.rgb(0, 200, 0), // Green
        Color.rgb(0, 0, 200), // Blue
        Color.rgb(200, 200, 0), // Yellow
        Color.rgb(200, 0, 200), // Magenta
        Color.rgb(0, 200, 200), // Cyan
        Color.rgb(200, 100, 0), // Orange
        Color.rgb(139, 69, 19), // Brown
        Color.rgb(255, 192, 203), // Pink
        Color.rgb(128, 0, 128), // Purple
        Color.rgb(0, 128, 0), // Dark Green
        Color.rgb(0, 0, 128), // Navy
        Color.rgb(255, 215, 0), // Gold
    )

    // Palette management
    data class NamedPalette(val name: String, val colors: List<Int>)

    private val customPalette = mutableListOf<Int>()
    private val savedPalettes = mutableListOf<NamedPalette>()
    private var currentPaletteName = "Par défaut"

    private val PREFS_NAME = "PixelArtPrefs"
    private val PALETTE_KEY = "custom_palette"
    private val PALETTES_KEY = "saved_palettes"
    private val CURRENT_PALETTE_NAME_KEY = "current_palette_name"

    // Activity result launchers for file operations
    private val importPngLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportPng(it) }
    }

    private val saveProjectLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { handleSaveProject(it) }
    }

    private val loadProjectLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleLoadProject(it) }
    }

    // Launcher for importing image as layer
    private val importImageAsLayerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportImageAsLayer(it) }
    }

    // Launcher for importing image as new project (canvas sized to image)
    private val importImageAsProjectLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportImageAsProject(it) }
    }

    // Launcher for importing reference image
    private val importReferenceImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportReferenceImage(it) }
    }

    // Launcher for importing palette from PNG
    private val importPalettePngLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportPalettePng(it) }
    }

    // Launcher for importing palette from HEX/TXT file
    private val importPaletteHexLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportPaletteHex(it) }
    }

    // Reference panel views
    private lateinit var referencePanel: LinearLayout
    private lateinit var chkReferenceVisible: CheckBox
    private lateinit var referenceAdjustRow: LinearLayout
    private lateinit var btnAdjustReference: MaterialButton
    private lateinit var txtReferenceScale: TextView
    private lateinit var referenceOpacityRow: LinearLayout
    private lateinit var txtReferenceOpacity: TextView
    private lateinit var sliderReferenceOpacity: SeekBar

    // Auto-save file name (legacy, kept for migration)
    private val AUTO_SAVE_FILE = "autosave.pixproj"

    // Room database repository for persistent storage
    private lateinit var pixelArtRepository: PixelArtRepository

    // Coroutine scope for Room operations
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_pixel_art_editor)

        initViews()
        setupToolbar()
        setupTools()
        setupColors()
        setupZoomControls()
        setupTimeline()
        setupSheetMode()
        setupPreviewWindow()
        setupLayers()
        setupReferenceImage()
        setupCanvas()

        // Auto-load last session
        autoLoadProject()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Save current canvas state before recreating views
        val projectData = pixelCanvas.getProjectData()
        val currentTool = pixelCanvas.currentTool
        val isSheetMode = pixelCanvas.isSheetMode()
        val sheetColumns = pixelCanvas.sheetColumns
        val showSheetGridLines = pixelCanvas.showSheetGridLines
        val zoomLevel = pixelCanvas.getCurrentZoom()
        val offsetX = pixelCanvas.getOffsetX()
        val offsetY = pixelCanvas.getOffsetY()

        // Stop any running animation
        stopPreviewAnimation()
        pixelCanvas.stopAnimation()

        // Reload layout for new orientation
        setContentView(R.layout.activity_pixel_art_editor)

        // Reinitialize all views with new layout
        initViews()
        setupToolbar()
        setupTools()
        setupColors()
        setupZoomControls()
        setupTimeline()
        setupSheetMode()
        setupPreviewWindow()
        setupLayers()
        setupReferenceImage()
        setupCanvas()

        // Restore canvas state
        val frameDataList = projectData.frames.map { frame ->
            PixelCanvas.FrameDataForSave(frame.pixelData, frame.duration)
        }
        pixelCanvas.loadProjectData(
            width = projectData.canvasWidth,
            height = projectData.canvasHeight,
            fps = projectData.fps,
            primary = projectData.primaryColor,
            secondary = projectData.secondaryColor,
            frameIndex = projectData.currentFrameIndex,
            frameDataList = frameDataList
        )

        // Restore tool and view mode
        pixelCanvas.currentTool = currentTool
        selectTool(currentTool)

        // Restore sheet mode state
        if (isSheetMode) {
            pixelCanvas.sheetColumns = sheetColumns
            pixelCanvas.showSheetGridLines = showSheetGridLines
            pixelCanvas.toggleViewMode()
            updateSheetModeUI(ViewMode.SHEET)
        }

        // Restore zoom and position
        pixelCanvas.setZoomAndOffset(zoomLevel, offsetX, offsetY)

        // Update UI
        updateCanvasSizeText()
        updateTimelineThumbnails()
        updateFrameIndicator()
        updateColorViews()

        // Update undo/redo buttons
        val canUndo = pixelCanvas.canUndo()
        val canRedo = pixelCanvas.canRedo()
        btnUndo.isEnabled = canUndo
        btnRedo.isEnabled = canRedo
        btnUndo.setTextColor(if (canUndo) Color.WHITE else "#888888".toColorInt())
        btnRedo.setTextColor(if (canRedo) Color.WHITE else "#888888".toColorInt())
    }

    override fun onPause() {
        super.onPause()
        // Save history to persistent storage
        pixelCanvas.saveHistoryToStorage()
        // Auto-save when leaving the app
        autoSaveProject()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending auto-save jobs
        pixelArtRepository.cancel()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()

            // Close onion opacity slider when touching outside of it
            if (onionOpacityVerticalContainer.isVisible) {
                val location = IntArray(2)
                onionOpacityVerticalContainer.getLocationOnScreen(location)
                val sliderRect = android.graphics.Rect(
                    location[0],
                    location[1],
                    location[0] + onionOpacityVerticalContainer.width,
                    location[1] + onionOpacityVerticalContainer.height
                )
                // Also check if touching the onion button
                val btnLocation = IntArray(2)
                btnOnionOpacity.getLocationOnScreen(btnLocation)
                val btnRect = android.graphics.Rect(
                    btnLocation[0],
                    btnLocation[1],
                    btnLocation[0] + btnOnionOpacity.width,
                    btnLocation[1] + btnOnionOpacity.height
                )
                if (!sliderRect.contains(x, y) && !btnRect.contains(x, y)) {
                    hideOnionOpacitySlider()
                }
            }

            // Close brush size slider when touching outside (after 5 seconds minimum)
            if (brushSizeVerticalContainer.isVisible) {
                val timeSinceShow = System.currentTimeMillis() - brushSliderShowTime
                if (timeSinceShow >= BRUSH_SLIDER_MIN_DURATION) {
                    val location = IntArray(2)
                    brushSizeVerticalContainer.getLocationOnScreen(location)
                    val sliderRect = android.graphics.Rect(
                        location[0],
                        location[1],
                        location[0] + brushSizeVerticalContainer.width,
                        location[1] + brushSizeVerticalContainer.height
                    )
                    // Check if touching marker or brush button (should not close)
                    val markerLocation = IntArray(2)
                    toolMarker.getLocationOnScreen(markerLocation)
                    val markerRect = android.graphics.Rect(
                        markerLocation[0],
                        markerLocation[1],
                        markerLocation[0] + toolMarker.width,
                        markerLocation[1] + toolMarker.height
                    )
                    val brushLocation = IntArray(2)
                    toolBrush.getLocationOnScreen(brushLocation)
                    val brushRect = android.graphics.Rect(
                        brushLocation[0],
                        brushLocation[1],
                        brushLocation[0] + toolBrush.width,
                        brushLocation[1] + toolBrush.height
                    )
                    if (!sliderRect.contains(x, y) && !markerRect.contains(x, y) && !brushRect.contains(x, y)) {
                        hideBrushSizeSlider()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideBrushSizeSlider() {
        // Only hide if using marker/brush tool (user can tap button to show again)
        val isBrushTool = pixelCanvas.currentTool == PixelCanvas.Tool.MARKER ||
                          pixelCanvas.currentTool == PixelCanvas.Tool.BRUSH
        if (isBrushTool) {
            brushSizeVerticalContainer.isVisible = false
        }
        // For shapes with brush stroke, don't auto-hide
    }

    private fun autoSaveProject() {
        // Use Room database for persistent auto-save
        activityScope.launch {
            try {
                val projectData = pixelCanvas.getProjectData()
                pixelArtRepository.saveImmediately(
                    canvasWidth = projectData.canvasWidth,
                    canvasHeight = projectData.canvasHeight,
                    fps = projectData.fps,
                    primaryColor = projectData.primaryColor,
                    secondaryColor = projectData.secondaryColor,
                    currentFrameIndex = projectData.currentFrameIndex,
                    frames = projectData.frames.map { frame ->
                        FrameData(frame.pixelData, frame.duration)
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun autoLoadProject() {
        // Use Room database for persistent auto-load
        activityScope.launch {
            try {
                val loadedProject = withContext(Dispatchers.IO) {
                    pixelArtRepository.loadLastProject()
                }

                if (loadedProject != null) {
                    val frameDataList = loadedProject.frames.map { frame ->
                        PixelCanvas.FrameDataForSave(frame.pixelData, frame.duration)
                    }
                    if (pixelCanvas.loadProjectData(
                            width = loadedProject.canvasWidth,
                            height = loadedProject.canvasHeight,
                            fps = loadedProject.fps,
                            primary = loadedProject.primaryColor,
                            secondary = loadedProject.secondaryColor,
                            frameIndex = loadedProject.currentFrameIndex,
                            frameDataList = frameDataList
                        )) {
                        updateCanvasSizeText()
                        updateTimelineThumbnails()
                        updateFrameIndicator()
                        updateColorViews()
                        // Load history from persistent storage after project load
                        pixelCanvas.loadHistoryFromStorage()
                    }
                } else {
                    // Try to load from legacy file if no Room data exists
                    loadLegacyProject()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to legacy file loading
                loadLegacyProject()
            }
        }
    }

    /**
     * Load project from legacy file system (migration support)
     */
    private fun loadLegacyProject() {
        try {
            val file = getFileStreamPath(AUTO_SAVE_FILE)
            if (file.exists()) {
                openFileInput(AUTO_SAVE_FILE).use { inputStream ->
                    val json = inputStream.bufferedReader().readText()
                    if (pixelCanvas.deserializeProject(json)) {
                        updateCanvasSizeText()
                        updateTimelineThumbnails()
                        updateFrameIndicator()
                        updateColorViews()
                        // Load history from persistent storage after project load
                        pixelCanvas.loadHistoryFromStorage()
                        // Migrate to Room database
                        triggerAutoSave()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Triggers debounced auto-save to Room database
     */
    private fun triggerAutoSave() {
        val projectData = pixelCanvas.getProjectData()
        pixelArtRepository.triggerAutoSave(
            canvasWidth = projectData.canvasWidth,
            canvasHeight = projectData.canvasHeight,
            fps = projectData.fps,
            primaryColor = projectData.primaryColor,
            secondaryColor = projectData.secondaryColor,
            currentFrameIndex = projectData.currentFrameIndex,
            frames = projectData.frames.map { frame ->
                FrameData(frame.pixelData, frame.duration)
            }
        )
    }

    private fun initViews() {
        pixelCanvas = findViewById(R.id.pixel_canvas)
        // Initialize history manager for per-frame undo/redo
        pixelCanvas.initHistoryManager(this)

        // Initialize Room database repository for auto-save
        pixelArtRepository = PixelArtRepository(this)

        // Connect canvas modification callback to auto-save
        pixelCanvas.onCanvasModified = {
            triggerAutoSave()
        }

        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        txtZoom = findViewById(R.id.txt_zoom)
        btnCanvasSize = findViewById(R.id.btn_canvas_size)
        colorPrimary = findViewById(R.id.color_primary)
        colorSecondary = findViewById(R.id.color_secondary)
        colorPalette = findViewById(R.id.color_palette)

        // Apply max height to palette ScrollView for small screens
        findViewById<ScrollView>(R.id.palette_scroll)?.let { paletteScroll ->
            val maxHeight = resources.getDimensionPixelSize(R.dimen.palette_max_height)
            paletteScroll.viewTreeObserver.addOnGlobalLayoutListener {
                if (paletteScroll.height > maxHeight) {
                    paletteScroll.layoutParams.height = maxHeight
                    paletteScroll.requestLayout()
                }
            }
        }
        selectionActionsRow = findViewById(R.id.selection_actions_row)

        // Tool buttons
        toolPencil = findViewById(R.id.tool_pencil)
        toolEraser = findViewById(R.id.tool_eraser)
        toolFill = findViewById(R.id.tool_fill)
        toolPicker = findViewById(R.id.tool_picker)
        toolShapesDropdown = findViewById(R.id.tool_shapes_dropdown)
        toolSelect = findViewById(R.id.tool_select)
        toolMoveLayer = findViewById(R.id.tool_move_layer)
        toolMarker = findViewById(R.id.tool_marker)
        toolBrush = findViewById(R.id.tool_brush)

        allToolButtons.addAll(listOf(
            toolPencil, toolEraser, toolFill, toolPicker,
            toolShapesDropdown,
            toolSelect, toolMoveLayer, toolMarker, toolBrush
        ))

        // Brush size views (vertical slider on left side)
        brushSizeVerticalContainer = findViewById(R.id.brush_size_vertical_container)
        brushSizeVisualIndicator = findViewById(R.id.brush_size_visual_indicator)
        txtBrushSizeIndicator = findViewById(R.id.txt_brush_size_indicator)
        sliderBrushSizeVertical = findViewById(R.id.slider_brush_size_vertical)
        btnBrushSizePlus = findViewById(R.id.btn_brush_size_plus)
        btnBrushSizeMinus = findViewById(R.id.btn_brush_size_minus)

        // Timeline views
        timelineFrames = findViewById(R.id.timeline_frames)
        txtFrameIndicator = findViewById(R.id.txt_frame_indicator)
        btnPlayPause = findViewById(R.id.btn_play_pause)

        // Onion skin views (floating slider)
        btnOnionOpacity = findViewById(R.id.btn_onion_opacity)
        onionOpacityVerticalContainer = findViewById(R.id.onion_opacity_vertical_container)
        txtOnionOpacity = findViewById(R.id.txt_onion_opacity)
        sliderOnionOpacity = findViewById(R.id.slider_onion_opacity)
        onionOpacityVisualIndicator = findViewById(R.id.onion_opacity_visual_indicator)
        btnOnionPlus = findViewById(R.id.btn_onion_plus)
        btnOnionMinus = findViewById(R.id.btn_onion_minus)

        // Layer views
        layerActionsRow = findViewById(R.id.layer_actions_row)
        txtLayerInfo = findViewById(R.id.txt_layer_info)
        txtLayerPosition = findViewById(R.id.txt_layer_position)
        txtLayerScale = findViewById(R.id.txt_layer_scale)

        // Sheet mode views
        btnSheetMode = findViewById(R.id.btn_sheet_mode)
        sheetConfigPanel = findViewById(R.id.sheet_config_panel)
        spinnerSheetColumns = findViewById(R.id.spinner_sheet_columns)
        txtSheetInfo = findViewById(R.id.txt_sheet_info)
        btnFitSheet = findViewById(R.id.btn_fit_sheet)
        chkSheetGridLines = findViewById(R.id.chk_sheet_grid_lines)
        timelinePanel = findViewById(R.id.timeline_panel)

        // Animation preview window views
        btnSheetPreview = findViewById(R.id.btn_sheet_preview)
        previewWindow = findViewById(R.id.preview_window)
        previewHeader = findViewById(R.id.preview_header)
        previewImage = findViewById(R.id.preview_image)
        btnPreviewClose = findViewById(R.id.btn_preview_close)
        btnPreviewPlay = findViewById(R.id.btn_preview_play)
        btnPreviewPrev = findViewById(R.id.btn_preview_prev)
        btnPreviewNext = findViewById(R.id.btn_preview_next)

        // Reference panel views
        referencePanel = findViewById(R.id.reference_panel)
        chkReferenceVisible = findViewById(R.id.chk_reference_visible)
        referenceAdjustRow = findViewById(R.id.reference_adjust_row)
        btnAdjustReference = findViewById(R.id.btn_adjust_reference)
        txtReferenceScale = findViewById(R.id.txt_reference_scale)
        referenceOpacityRow = findViewById(R.id.reference_opacity_row)
        txtReferenceOpacity = findViewById(R.id.txt_reference_opacity)
        sliderReferenceOpacity = findViewById(R.id.slider_reference_opacity)
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            showReturnToMenuConfirmation()
        }

        btnUndo.setOnClickListener {
            // Try frame-level undo first, then project-level undo
            if (pixelCanvas.canUndo()) {
                pixelCanvas.undo()
            } else if (pixelCanvas.canUndoProjectChange()) {
                val description = pixelCanvas.getLastProjectChangeDescription()
                if (pixelCanvas.undoProjectChange()) {
                    updateCanvasSizeText()
                    updateTimelineThumbnails()
                    updateFrameIndicator()
                    updateSheetModeUI(pixelCanvas.viewMode)
                    Toast.makeText(this, getString(R.string.pixel_art_undo_action, description), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRedo.setOnClickListener {
            pixelCanvas.redo()
        }

        findViewById<ImageButton>(R.id.btn_clear).setOnClickListener {
            showClearConfirmDialog()
        }

        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener {
            showMainMenu()
        }

        // Layers button in toolbar (more visible)
        findViewById<ImageButton>(R.id.btn_layers_main).setOnClickListener {
            showLayersDialog()
        }

        // Grid toggle button in toolbar
        val btnGridToggle = findViewById<ImageButton>(R.id.btn_grid_toggle)
        btnGridToggle.setOnClickListener {
            pixelCanvas.showGrid = !pixelCanvas.showGrid
            updateGridButtonState(btnGridToggle)
        }
        updateGridButtonState(btnGridToggle)

        // Canvas size button
        btnCanvasSize.setOnClickListener {
            showCanvasSizeDialog()
        }

        // Reference image buttons in toolbar
        findViewById<ImageButton>(R.id.btn_reference_toolbar).setOnClickListener {
            showReferenceImageDialog()
        }

        findViewById<ImageButton>(R.id.btn_reference_visibility).setOnClickListener {
            val isVisible = !pixelCanvas.isReferenceVisible()
            pixelCanvas.updateReferenceVisibility(isVisible)
            updateReferenceToolbarButtons()
        }

        findViewById<ImageButton>(R.id.btn_reference_adjust).setOnClickListener {
            pixelCanvas.isAdjustingReference = !pixelCanvas.isAdjustingReference
            updateReferenceToolbarButtons()
            if (pixelCanvas.isAdjustingReference) {
                Toast.makeText(this, R.string.pixel_art_pinch_to_zoom, Toast.LENGTH_SHORT).show()
            }
        }

        // Background/Grid transparency button in toolbar
        findViewById<ImageButton>(R.id.btn_background_settings).setOnClickListener {
            showBackgroundSettingsDialog()
        }

        updateCanvasSizeText()
    }

    private fun updateGridButtonState(btn: ImageButton) {
        val tintColor = if (pixelCanvas.showGrid) "#4CAF50".toColorInt() else "#666666".toColorInt()
        btn.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun showCanvasSizeDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Preset sizes section
        val txtPresets = TextView(this).apply {
            text = getString(R.string.pixel_art_label_preset_sizes)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }
        dialogView.addView(txtPresets)

        val presetSizes = listOf(
            16 to "16x16 (icône)",
            32 to "32x32 (sprite)",
            64 to "64x64 (grand sprite)",
            128 to "128x128",
            256 to "256x256",
            512 to "512x512 (HD)",
            1024 to "1024x1024 (Full HD)",
            2048 to "2048x2048 (2K)",
            4096 to "4096x4096 (4K)"
        )

        val presetGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((size, label) in presetSizes) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4
                }
                setOnClickListener {
                    pixelCanvas.setCanvasSize(size, size)
                    updateCanvasSizeText()
                    (dialogView.parent as? android.app.Dialog)?.dismiss()
                }
            }
            presetGroup.addView(btn)
        }

        val presetScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300
            )
            addView(presetGroup)
        }
        dialogView.addView(presetScroll)

        // Custom size section
        val txtCustom = TextView(this).apply {
            text = getString(R.string.pixel_art_label_custom_size)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(txtCustom)

        val customRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val editWidth = EditText(this).apply {
            hint = getString(R.string.pixel_art_width_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(pixelCanvas.getCanvasWidth().toString())
        }
        customRow.addView(editWidth)

        val txtX = TextView(this).apply {
            text = " x "
            setTextColor(Color.WHITE)
        }
        customRow.addView(txtX)

        val editHeight = EditText(this).apply {
            hint = getString(R.string.pixel_art_height_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(pixelCanvas.getCanvasHeight().toString())
        }
        customRow.addView(editHeight)

        dialogView.addView(customRow)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_canvas_size_label)
            .setView(dialogView)
            .setPositiveButton(R.string.common_new) { _, _ ->
                val width = editWidth.text.toString().toIntOrNull()?.coerceIn(1, 8192) ?: pixelCanvas.getCanvasWidth()
                val height = editHeight.text.toString().toIntOrNull()?.coerceIn(1, 8192) ?: pixelCanvas.getCanvasHeight()
                pixelCanvas.setCanvasSize(width, height)
                updateCanvasSizeText()
            }
            .setNeutralButton(R.string.common_resize) { _, _ ->
                val width = editWidth.text.toString().toIntOrNull()?.coerceIn(1, 8192) ?: pixelCanvas.getCanvasWidth()
                val height = editHeight.text.toString().toIntOrNull()?.coerceIn(1, 8192) ?: pixelCanvas.getCanvasHeight()
                pixelCanvas.resizeCanvas(width, height)
                updateCanvasSizeText()
                Toast.makeText(this, getString(R.string.pixel_art_canvas_resized, width, height), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        // Update preset buttons to select size (update edit fields)
        for (i in 0 until presetGroup.childCount) {
            val btn = presetGroup.getChildAt(i) as? MaterialButton
            btn?.setOnClickListener {
                val size = presetSizes[i].first
                editWidth.setText(size.toString())
                editHeight.setText(size.toString())
                // Highlight selected button
                for (j in 0 until presetGroup.childCount) {
                    val otherBtn = presetGroup.getChildAt(j) as? MaterialButton
                    otherBtn?.strokeColor = android.content.res.ColorStateList.valueOf(
                        if (i == j) "#4CAF50".toColorInt() else "#666666".toColorInt()
                    )
                    otherBtn?.strokeWidth = if (i == j) 3 else 1
                }
            }
        }

        dialog.show()
    }

    private fun showMainMenu() {
        val options = arrayOf(
            getString(R.string.pixel_art_new_project),
            getString(R.string.pixel_art_import_image_as_project),
            getString(R.string.pixel_art_save_project),
            getString(R.string.pixel_art_load_project),
            getString(R.string.pixel_art_import_png),
            getString(R.string.pixel_art_export_gif),
            getString(R.string.pixel_art_export_spritesheet)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewProjectDialog()
                    1 -> importImageAsProject()
                    2 -> saveProject()
                    3 -> loadProject()
                    4 -> importPng()
                    5 -> showExportGifDialog()
                    6 -> showExportSpriteSheetDialog()
                }
            }
            .show()
    }

    private fun showNewProjectDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_new_project_title)
            .setMessage(R.string.pixel_art_new_project_confirm)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                showNewProjectSizeDialog()
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun showNewProjectSizeDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val txtSize = TextView(this).apply {
            text = getString(R.string.pixel_art_canvas_size_label)
            setTextColor(Color.WHITE)
        }
        dialogView.addView(txtSize)

        val sizeOptions = arrayOf("8x8", "12x12", "16x16", "24x24", "32x32", "64x64", "128x128", getString(R.string.pixel_art_custom_size))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@PixelArtEditorActivity, android.R.layout.simple_spinner_dropdown_item, sizeOptions)
            setSelection(4)  // Default 32x32
        }
        dialogView.addView(spinner)

        // Custom size container (hidden by default)
        val customSizeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = false
            setPadding(0, 16, 0, 0)
        }

        // Width input
        val widthLabel = TextView(this).apply {
            text = getString(R.string.pixel_art_width)
            setTextColor(Color.WHITE)
        }
        customSizeContainer.addView(widthLabel)

        val widthInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("64")
            setTextColor(Color.WHITE)
            hint = getString(R.string.pixel_art_canvas_size_range)
            filters = arrayOf(android.text.InputFilter.LengthFilter(3))
        }
        customSizeContainer.addView(widthInput)

        // Height input
        val heightLabel = TextView(this).apply {
            text = getString(R.string.pixel_art_height)
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 0)
        }
        customSizeContainer.addView(heightLabel)

        val heightInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("64")
            setTextColor(Color.WHITE)
            hint = getString(R.string.pixel_art_canvas_size_range)
            filters = arrayOf(android.text.InputFilter.LengthFilter(3))
        }
        customSizeContainer.addView(heightInput)

        // Info text
        val infoText = TextView(this).apply {
            text = getString(R.string.pixel_art_custom_size_info)
            setTextColor("#888888".toColorInt())
            textSize = 12f
            setPadding(0, 8, 0, 0)
        }
        customSizeContainer.addView(infoText)

        dialogView.addView(customSizeContainer)

        // Show/hide custom inputs based on spinner selection
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customSizeContainer.isVisible = position == 7
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_new_project_title)
            .setView(dialogView)
            .setPositiveButton(R.string.pixel_art_create) { _, _ ->
                val (width, height) = when (spinner.selectedItemPosition) {
                    0 -> 8 to 8
                    1 -> 12 to 12
                    2 -> 16 to 16
                    3 -> 24 to 24
                    4 -> 32 to 32
                    5 -> 64 to 64
                    6 -> 128 to 128
                    7 -> {
                        // Custom size
                        val w = widthInput.text.toString().toIntOrNull()?.coerceIn(8, 512) ?: 64
                        val h = heightInput.text.toString().toIntOrNull()?.coerceIn(8, 512) ?: 64
                        w to h
                    }
                    else -> 32 to 32
                }
                pixelCanvas.newProject(width, height)
                updateCanvasSizeText()
                updateTimelineThumbnails()
                updateFrameIndicator()
            }
            .setNegativeButton(R.string.pixel_art_cancel, null)
            .show()
    }

    private fun saveProject() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        saveProjectLauncher.launch("PixelArt_$timestamp.pixproj")
    }

    private fun handleSaveProject(uri: Uri) {
        try {
            val json = pixelCanvas.serializeProject()
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Toast.makeText(this, R.string.pixel_art_save_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_save_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadProject() {
        loadProjectLauncher.launch("*/*")
    }

    private fun handleLoadProject(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return

            if (pixelCanvas.deserializeProject(json)) {
                updateCanvasSizeText()
                updateTimelineThumbnails()
                updateFrameIndicator()
                updateColorViews()
                Toast.makeText(this, R.string.pixel_art_load_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_load_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_load_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun importPng() {
        importPngLauncher.launch("image/*")
    }

    private fun handleImportPng(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                pixelCanvas.importPngAsFrame(bitmap, resizeToFit = true)
                updateTimelineThumbnails()
                updateFrameIndicator()
                Toast.makeText(this, R.string.pixel_art_import_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showExportGifDialog() {
        val scrollView = ScrollView(this)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        scrollView.addView(dialogView)

        var currentScale = 1
        var currentFps = pixelCanvas.animationFps
        var currentLoopCount = 0  // 0 = infinite
        var currentStackFrames = false
        var currentDirection = 0  // 0=normal, 1=reverse, 2=pingpong

        // ===== SCALE SLIDER =====
        val lblScale = TextView(this).apply {
            text = getString(R.string.pixel_art_export_scale)
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        dialogView.addView(lblScale)

        val txtScale = TextView(this).apply {
            text = getString(R.string.pixel_art_export_scale_value, currentScale)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        dialogView.addView(txtScale)

        val sliderScale = SeekBar(this).apply {
            max = 15  // 1-16x
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentScale = progress + 1
                    txtScale.text = getString(R.string.pixel_art_export_scale_value, currentScale)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderScale)

        // Spacer
        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        })

        // ===== FPS SLIDER =====
        val lblFps = TextView(this).apply {
            text = getString(R.string.pixel_art_export_fps)
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        dialogView.addView(lblFps)

        val txtFps = TextView(this).apply {
            text = getString(R.string.pixel_art_export_fps_value, currentFps)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        dialogView.addView(txtFps)

        val sliderFps = SeekBar(this).apply {
            max = 59  // 1-60 FPS
            progress = currentFps - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentFps = progress + 1
                    txtFps.text = getString(R.string.pixel_art_export_fps_value, currentFps)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderFps)

        // Spacer
        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        })

        // ===== LOOP OPTIONS =====
        val lblLoop = TextView(this).apply {
            text = getString(R.string.pixel_art_export_loop)
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        dialogView.addView(lblLoop)

        val loopGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val rbLoopInfinite = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_loop_infinite)
            setTextColor(Color.WHITE)
            isChecked = true
        }
        loopGroup.addView(rbLoopInfinite)

        val rbLoopOnce = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_loop_once)
            setTextColor(Color.WHITE)
        }
        loopGroup.addView(rbLoopOnce)

        loopGroup.setOnCheckedChangeListener { _, checkedId ->
            currentLoopCount = if (checkedId == rbLoopInfinite.id) 0 else 1
        }
        dialogView.addView(loopGroup)

        // Spacer
        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        })

        // ===== DIRECTION OPTIONS =====
        val lblDirection = TextView(this).apply {
            text = getString(R.string.pixel_art_export_direction)
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        dialogView.addView(lblDirection)

        val directionGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val rbDirNormal = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_direction_normal)
            setTextColor(Color.WHITE)
            isChecked = true
        }
        directionGroup.addView(rbDirNormal)

        val rbDirReverse = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_direction_reverse)
            setTextColor(Color.WHITE)
        }
        directionGroup.addView(rbDirReverse)

        val rbDirPingPong = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_direction_pingpong)
            setTextColor(Color.WHITE)
        }
        directionGroup.addView(rbDirPingPong)

        directionGroup.setOnCheckedChangeListener { _, checkedId ->
            currentDirection = when (checkedId) {
                rbDirReverse.id -> 1
                rbDirPingPong.id -> 2
                else -> 0
            }
        }
        dialogView.addView(directionGroup)

        // Spacer
        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        })

        // ===== FRAME MODE =====
        val lblFrameMode = TextView(this).apply {
            text = getString(R.string.pixel_art_export_frame_mode)
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        dialogView.addView(lblFrameMode)

        val frameModeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val rbReplace = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_frame_replace)
            setTextColor(Color.WHITE)
            isChecked = true
        }
        frameModeGroup.addView(rbReplace)

        val rbStack = RadioButton(this).apply {
            text = getString(R.string.pixel_art_export_frame_stack)
            setTextColor(Color.WHITE)
        }
        frameModeGroup.addView(rbStack)

        frameModeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentStackFrames = checkedId == rbStack.id
        }
        dialogView.addView(frameModeGroup)

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_export_gif)
            .setView(scrollView)
            .setPositiveButton(R.string.pixel_art_export_gif) { _, _ ->
                val direction = when (currentDirection) {
                    1 -> PixelCanvas.GifDirection.REVERSE
                    2 -> PixelCanvas.GifDirection.PINGPONG
                    else -> PixelCanvas.GifDirection.NORMAL
                }
                exportGif(currentScale, currentFps, currentLoopCount, currentStackFrames, direction)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportGif(
        scale: Int,
        fps: Int = 10,
        loopCount: Int = 0,
        stackFrames: Boolean = false,
        direction: PixelCanvas.GifDirection = PixelCanvas.GifDirection.NORMAL
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "PixelArt_$timestamp.gif"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelArt")
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        if (pixelCanvas.exportToGif(outputStream, scale, fps, loopCount, stackFrames, direction)) {
                            Toast.makeText(this, getString(R.string.pixel_art_export_success, filename), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, R.string.pixel_art_export_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val pixelArtDir = File(picturesDir, "PixelArt")
                if (!pixelArtDir.exists()) pixelArtDir.mkdirs()

                val file = File(pixelArtDir, filename)
                FileOutputStream(file).use { outputStream ->
                    if (pixelCanvas.exportToGif(outputStream, scale, fps, loopCount, stackFrames, direction)) {
                        Toast.makeText(this, getString(R.string.pixel_art_export_success, file.absolutePath), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, R.string.pixel_art_export_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_export_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showExportSpriteSheetDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        var currentScale = 1
        var currentColumns = 0  // 0 = auto (single row)

        // Scale
        val txtScale = TextView(this).apply {
            text = getString(R.string.pixel_art_export_scale_value, currentScale)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        dialogView.addView(txtScale)

        val sliderScale = SeekBar(this).apply {
            max = 7  // 1-8x
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentScale = progress + 1
                    txtScale.text = getString(R.string.pixel_art_export_scale_value, currentScale)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderScale)

        // Columns
        val txtColumns = TextView(this).apply {
            text = getString(R.string.pixel_art_spritesheet_columns)
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(txtColumns)

        val frameCount = pixelCanvas.getFrameCount()
        val columnOptions = mutableListOf(getString(R.string.pixel_art_spritesheet_auto))
        for (i in 1..frameCount.coerceAtMost(16)) {
            columnOptions.add("$i")
        }

        val spinnerColumns = Spinner(this).apply {
            adapter = ArrayAdapter(this@PixelArtEditorActivity, android.R.layout.simple_spinner_dropdown_item, columnOptions)
        }
        dialogView.addView(spinnerColumns)

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_export_spritesheet)
            .setView(dialogView)
            .setPositiveButton(R.string.common_export) { _, _ ->
                currentColumns = if (spinnerColumns.selectedItemPosition == 0) 0 else spinnerColumns.selectedItemPosition
                exportSpriteSheet(currentScale, currentColumns)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun exportSpriteSheet(scale: Int, columns: Int) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "PixelArt_SpriteSheet_$timestamp.png"

        try {
            val bitmap = pixelCanvas.exportToSpriteSheet(columns, scale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelArt")
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(this, getString(R.string.pixel_art_export_success, filename), Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val pixelArtDir = File(picturesDir, "PixelArt")
                if (!pixelArtDir.exists()) pixelArtDir.mkdirs()

                val file = File(pixelArtDir, filename)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, getString(R.string.pixel_art_export_success, file.absolutePath), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_export_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupTools() {
        val chkFilled = findViewById<CheckBox>(R.id.chk_filled)
        val chkBend = findViewById<CheckBox>(R.id.chk_bend)

        // Map tools to buttons (excluding shape tools which use the dropdown)
        val toolMap = mapOf(
            toolPencil to PixelCanvas.Tool.PENCIL,
            toolEraser to PixelCanvas.Tool.ERASER,
            toolFill to PixelCanvas.Tool.FILL,
            toolPicker to PixelCanvas.Tool.PICKER,
            toolSelect to PixelCanvas.Tool.SELECT,
            toolMoveLayer to PixelCanvas.Tool.MOVE_LAYER,
            toolMarker to PixelCanvas.Tool.MARKER,
            toolBrush to PixelCanvas.Tool.BRUSH
        )

        for ((button, tool) in toolMap) {
            button.setOnClickListener {
                // Save current brush size for previous tool before switching
                val previousTool = pixelCanvas.currentTool
                if (toolBrushSizes.containsKey(previousTool)) {
                    toolBrushSizes[previousTool] = pixelCanvas.brushSize
                }

                pixelCanvas.currentTool = tool

                // Restore brush size for the selected tool
                toolBrushSizes[tool]?.let { savedSize ->
                    pixelCanvas.brushSize = savedSize
                    sliderBrushSizeVertical.progress = savedSize
                    updateBrushSizeIndicator(savedSize)
                }

                // Update shape stroke style when pencil/marker/brush is selected
                when (tool) {
                    PixelCanvas.Tool.PENCIL -> pixelCanvas.shapeStrokeStyle = PixelCanvas.ShapeStrokeStyle.PENCIL
                    PixelCanvas.Tool.MARKER -> pixelCanvas.shapeStrokeStyle = PixelCanvas.ShapeStrokeStyle.MARKER
                    PixelCanvas.Tool.BRUSH -> pixelCanvas.shapeStrokeStyle = PixelCanvas.ShapeStrokeStyle.BRUSH
                    else -> { /* Keep current stroke style for other tools */ }
                }

                updateToolButtonStates(button)
                updateSelectionActionsVisibility()
                updateLayerActionsVisibility()
                updateBrushSizeVisibility()

                // Show slider and record time when selecting marker or brush
                if (tool == PixelCanvas.Tool.MARKER || tool == PixelCanvas.Tool.BRUSH) {
                    brushSizeVerticalContainer.isVisible = true
                    brushSliderShowTime = System.currentTimeMillis()
                }
            }
        }

        // Setup shape dropdown button
        toolShapesDropdown.setOnClickListener {
            showShapesPopup()
        }

        // Set initial tool state
        updateToolButtonStates(toolPencil)

        chkFilled.setOnCheckedChangeListener { _, isChecked ->
            pixelCanvas.shapeFilled = isChecked
        }

        chkBend.setOnCheckedChangeListener { _, isChecked ->
            pixelCanvas.isBendModeEnabled = isChecked
        }

        // Vertical brush size slider
        sliderBrushSizeVertical.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress.coerceIn(1, 50)
                pixelCanvas.brushSize = size
                updateBrushSizeIndicator(size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save brush size for current tool when user stops sliding
                saveBrushSizeForCurrentTool()
            }
        })

        // Brush size +/- buttons
        btnBrushSizePlus.setOnClickListener {
            val newSize = (sliderBrushSizeVertical.progress + 1).coerceIn(1, 50)
            sliderBrushSizeVertical.progress = newSize
            pixelCanvas.brushSize = newSize
            updateBrushSizeIndicator(newSize)
            saveBrushSizeForCurrentTool()
        }

        btnBrushSizeMinus.setOnClickListener {
            val newSize = (sliderBrushSizeVertical.progress - 1).coerceIn(1, 50)
            sliderBrushSizeVertical.progress = newSize
            pixelCanvas.brushSize = newSize
            updateBrushSizeIndicator(newSize)
            saveBrushSizeForCurrentTool()
        }

        // Onion skin opacity button - toggles floating slider
        btnOnionOpacity.setOnClickListener {
            if (onionOpacityVerticalContainer.isVisible) {
                onionOpacityVerticalContainer.isVisible = false
            } else {
                onionOpacityVerticalContainer.isVisible = true
                // Sync slider with current value
                sliderOnionOpacity.progress = (pixelCanvas.onionSkinOpacity * 100).toInt()
                updateOnionOpacityIndicator((pixelCanvas.onionSkinOpacity * 100).toInt())
            }
        }

        // Onion skin opacity slider (in floating container, controls opacity in real-time)
        sliderOnionOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val opacity = progress.coerceIn(10, 100)
                pixelCanvas.onionSkinOpacity = opacity / 100f
                updateOnionOpacityIndicator(opacity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Onion opacity +/- buttons
        btnOnionPlus.setOnClickListener {
            val newOpacity = (sliderOnionOpacity.progress + 10).coerceIn(10, 100)
            sliderOnionOpacity.progress = newOpacity
            pixelCanvas.onionSkinOpacity = newOpacity / 100f
            updateOnionOpacityIndicator(newOpacity)
        }

        btnOnionMinus.setOnClickListener {
            val newOpacity = (sliderOnionOpacity.progress - 10).coerceIn(10, 100)
            sliderOnionOpacity.progress = newOpacity
            pixelCanvas.onionSkinOpacity = newOpacity / 100f
            updateOnionOpacityIndicator(newOpacity)
        }

        setupSelectionActions()
    }

    /**
     * Affiche un popup avec toutes les formes disponibles
     */
    private fun showShapesPopup() {
        val shapeTools = listOf(
            PixelCanvas.Tool.LINE,
            PixelCanvas.Tool.DASHED_LINE,
            PixelCanvas.Tool.RECTANGLE,
            PixelCanvas.Tool.ROUNDED_RECT,
            PixelCanvas.Tool.CIRCLE,
            PixelCanvas.Tool.OVAL,
            PixelCanvas.Tool.TRIANGLE,
            PixelCanvas.Tool.HEXAGON,
            PixelCanvas.Tool.DIAMOND,
            PixelCanvas.Tool.STAR,
            PixelCanvas.Tool.ARROW,
            PixelCanvas.Tool.PENTAGON
        )

        // Créer le conteneur de la popup
        val gridLayout = GridLayout(this).apply {
            columnCount = 3  // 3 colonnes pour 12 formes (4 lignes verticales)
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.shape_popup_bg)
        }

        // Calculer la taille des boutons pour s'adapter à l'écran
        val buttonSize = (48 * resources.displayMetrics.density).toInt()
        val margin = (3 * resources.displayMetrics.density).toInt()

        // Calculer la largeur totale du popup
        val popupWidth = (buttonSize + margin * 2) * 3 + 24  // 3 colonnes + padding
        val popupHeight = (buttonSize + margin * 2) * 4 + 24  // 4 lignes + padding

        // Créer la PopupWindow avec taille fixe
        val popupWindow = PopupWindow(
            gridLayout,
            popupWidth,
            popupHeight,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
        }

        // Ajouter les boutons de formes
        val selectedColor = "#4CAF50".toColorInt()
        val unselectedColor = "#666666".toColorInt()

        for (shapeTool in shapeTools) {
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(margin, margin, margin, margin)
                }
                text = shapeIcons[shapeTool] ?: "?"
                textSize = 22f
                setTextColor(Color.WHITE)
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                insetTop = 0
                insetBottom = 0

                // Indicateur visuel pour la forme actuellement sélectionnée
                val isSelected = shapeTool == currentShapeType
                strokeColor = android.content.res.ColorStateList.valueOf(
                    if (isSelected) selectedColor else unselectedColor
                )
                strokeWidth = if (isSelected) 3 else 1

                setOnClickListener {
                    // Save brush size for previous tool
                    val previousTool = pixelCanvas.currentTool
                    if (toolBrushSizes.containsKey(previousTool)) {
                        toolBrushSizes[previousTool] = pixelCanvas.brushSize
                    }

                    // Set the shape tool
                    currentShapeType = shapeTool
                    pixelCanvas.currentTool = shapeTool

                    // Restore brush size for this shape tool
                    toolBrushSizes[shapeTool]?.let { savedSize ->
                        pixelCanvas.brushSize = savedSize
                        sliderBrushSizeVertical.progress = savedSize
                        updateBrushSizeIndicator(savedSize)
                    }

                    // Update button icon to show selected shape
                    toolShapesDropdown.text = shapeIcons[shapeTool]

                    // Update visual states
                    updateToolButtonStates(toolShapesDropdown)
                    updateSelectionActionsVisibility()
                    updateLayerActionsVisibility()
                    updateBrushSizeVisibility()

                    popupWindow.dismiss()
                }
            }
            gridLayout.addView(button)
        }

        // Afficher la popup au-dessus du bouton
        val offsetY = -(popupHeight + toolShapesDropdown.height)
        popupWindow.showAsDropDown(toolShapesDropdown, 0, offsetY)
    }

    private fun updateBrushSizeVisibility() {
        val isBrushTool = pixelCanvas.currentTool == PixelCanvas.Tool.MARKER ||
                          pixelCanvas.currentTool == PixelCanvas.Tool.BRUSH

        // Also show for shape tools when using marker or brush stroke style
        val isShapeTool = pixelCanvas.currentTool in listOf(
            PixelCanvas.Tool.LINE, PixelCanvas.Tool.DASHED_LINE, PixelCanvas.Tool.RECTANGLE, PixelCanvas.Tool.ROUNDED_RECT,
            PixelCanvas.Tool.CIRCLE, PixelCanvas.Tool.OVAL, PixelCanvas.Tool.TRIANGLE, PixelCanvas.Tool.HEXAGON,
            PixelCanvas.Tool.DIAMOND, PixelCanvas.Tool.STAR, PixelCanvas.Tool.ARROW, PixelCanvas.Tool.PENTAGON
        )
        val shapeNeedsBrushSize = isShapeTool &&
            pixelCanvas.shapeStrokeStyle != PixelCanvas.ShapeStrokeStyle.PENCIL

        // For marker/brush: visibility is handled by click listener with auto-hide after 5s
        // For shapes with brush stroke: always visible
        // For other tools: hide
        if (shapeNeedsBrushSize) {
            brushSizeVerticalContainer.isVisible = true
            brushSliderShowTime = System.currentTimeMillis()  // Reset timer for shapes too
        } else if (!isBrushTool) {
            brushSizeVerticalContainer.isVisible = false
        }
        // For isBrushTool without shapeNeedsBrushSize: don't change visibility here,
        // it's managed by tool click listener which shows it and dispatchTouchEvent which hides it

        // Update the visual indicator when showing
        if (brushSizeVerticalContainer.isVisible) {
            updateBrushSizeIndicator(pixelCanvas.brushSize)
        }
    }

    private fun updateBrushSizeIndicator(size: Int) {
        // Update the text indicator with "X px" format
        txtBrushSizeIndicator.text = "$size px"

        // Update the visual circle size (min 8dp, max 36dp based on brush size 1-50)
        val minSizeDp = 8f
        val maxSizeDp = 36f
        val sizeDp = minSizeDp + (maxSizeDp - minSizeDp) * (size - 1) / 49f
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()

        brushSizeVisualIndicator.layoutParams = brushSizeVisualIndicator.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        brushSizeVisualIndicator.requestLayout()
    }

    private fun updateOnionOpacityIndicator(opacity: Int) {
        // Update the floating slider text
        txtOnionOpacity.text = "${opacity}%"
        // Update the button text in timeline bar
        btnOnionOpacity.text = "\uD83E\uDDC5 ${opacity}%"
        // Update the visual indicator alpha
        onionOpacityVisualIndicator.alpha = opacity / 100f
    }

    private fun hideOnionOpacitySlider() {
        onionOpacityVerticalContainer.isVisible = false
    }

    private fun saveBrushSizeForCurrentTool() {
        val currentTool = pixelCanvas.currentTool
        if (toolBrushSizes.containsKey(currentTool)) {
            toolBrushSizes[currentTool] = pixelCanvas.brushSize
        }
    }

    private fun updateToolButtonStates(selectedButton: MaterialButton) {
        val selectedColor = "#4CAF50".toColorInt()
        val unselectedColor = "#666666".toColorInt()

        // Check if current tool is a shape tool
        val isShapeTool = pixelCanvas.currentTool in listOf(
            PixelCanvas.Tool.LINE, PixelCanvas.Tool.DASHED_LINE, PixelCanvas.Tool.RECTANGLE, PixelCanvas.Tool.ROUNDED_RECT,
            PixelCanvas.Tool.CIRCLE, PixelCanvas.Tool.OVAL, PixelCanvas.Tool.TRIANGLE, PixelCanvas.Tool.HEXAGON,
            PixelCanvas.Tool.DIAMOND, PixelCanvas.Tool.STAR, PixelCanvas.Tool.ARROW, PixelCanvas.Tool.PENTAGON
        )

        // Get the stroke style button that should also be highlighted for shapes
        val strokeStyleButton: MaterialButton? = if (isShapeTool) {
            when (pixelCanvas.shapeStrokeStyle) {
                PixelCanvas.ShapeStrokeStyle.PENCIL -> toolPencil
                PixelCanvas.ShapeStrokeStyle.MARKER -> toolMarker
                PixelCanvas.ShapeStrokeStyle.BRUSH -> toolBrush
            }
        } else null

        for (button in allToolButtons) {
            // Pour le dropdown des formes, il est sélectionné si c'est un outil forme
            val isSelected = when {
                button == toolShapesDropdown -> isShapeTool
                button == strokeStyleButton -> true
                else -> button == selectedButton
            }
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                if (isSelected) selectedColor else unselectedColor
            )
            button.strokeWidth = if (isSelected) 3 else 1
        }
    }

    private fun selectTool(tool: PixelCanvas.Tool) {
        pixelCanvas.currentTool = tool

        // Check if it's a shape tool
        val isShapeTool = tool in listOf(
            PixelCanvas.Tool.LINE, PixelCanvas.Tool.DASHED_LINE, PixelCanvas.Tool.RECTANGLE, PixelCanvas.Tool.ROUNDED_RECT,
            PixelCanvas.Tool.CIRCLE, PixelCanvas.Tool.OVAL, PixelCanvas.Tool.TRIANGLE, PixelCanvas.Tool.HEXAGON,
            PixelCanvas.Tool.DIAMOND, PixelCanvas.Tool.STAR, PixelCanvas.Tool.ARROW, PixelCanvas.Tool.PENTAGON
        )

        val button = when {
            isShapeTool -> {
                // Update dropdown icon and currentShapeType
                currentShapeType = tool
                toolShapesDropdown.text = shapeIcons[tool]
                toolShapesDropdown
            }
            else -> when (tool) {
                PixelCanvas.Tool.PENCIL -> toolPencil
                PixelCanvas.Tool.ERASER -> toolEraser
                PixelCanvas.Tool.FILL -> toolFill
                PixelCanvas.Tool.PICKER -> toolPicker
                PixelCanvas.Tool.SELECT -> toolSelect
                PixelCanvas.Tool.MOVE_LAYER -> toolMoveLayer
                PixelCanvas.Tool.MOVE_SELECTION -> toolSelect  // MOVE_SELECTION is a temporary mode, use SELECT button
                PixelCanvas.Tool.MARKER -> toolMarker
                PixelCanvas.Tool.BRUSH -> toolBrush
                else -> toolPencil  // Fallback
            }
        }
        updateToolButtonStates(button)
        updateSelectionActionsVisibility()
        updateLayerActionsVisibility()
        updateBrushSizeVisibility()
    }

    private fun setupSelectionActions() {
        // Copy
        findViewById<MaterialButton>(R.id.btn_copy).setOnClickListener {
            // Si une forme est en attente, copier la forme
            if (pixelCanvas.hasPendingShapeToMove()) {
                if (pixelCanvas.copyPendingShape()) {
                    Toast.makeText(this, R.string.pixel_art_shape_copied, Toast.LENGTH_SHORT).show()
                }
            } else if (pixelCanvas.copySelection()) {
                Toast.makeText(this, R.string.pixel_art_selection_copied, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_no_selection, Toast.LENGTH_SHORT).show()
            }
        }

        // Cut
        findViewById<MaterialButton>(R.id.btn_cut).setOnClickListener {
            // Si une forme est en attente, couper la forme (copier + supprimer sans appliquer)
            if (pixelCanvas.hasPendingShapeToMove()) {
                if (pixelCanvas.cutPendingShape()) {
                    updateSelectionActionsVisibility()
                    Toast.makeText(this, R.string.pixel_art_shape_cut, Toast.LENGTH_SHORT).show()
                }
            } else if (pixelCanvas.cutSelection()) {
                Toast.makeText(this, R.string.pixel_art_selection_cut, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_no_selection, Toast.LENGTH_SHORT).show()
            }
        }

        // Paste / Tamponner
        findViewById<MaterialButton>(R.id.btn_paste).setOnClickListener {
            // Si une forme est en attente, tamponner (appliquer au canvas mais garder la forme)
            if (pixelCanvas.hasPendingShapeToMove()) {
                if (pixelCanvas.stampPendingShape()) {
                    Toast.makeText(this, R.string.pixel_art_shape_stamped, Toast.LENGTH_SHORT).show()
                }
            } else if (pixelCanvas.pasteSelection()) {
                Toast.makeText(this, R.string.pixel_art_paste_move_mode, Toast.LENGTH_SHORT).show()
                updateToolButtonStates(toolSelect) // Update UI to show move mode
            } else {
                Toast.makeText(this, R.string.pixel_art_no_clipboard, Toast.LENGTH_SHORT).show()
            }
        }

        // Delete
        findViewById<MaterialButton>(R.id.btn_delete_selection).setOnClickListener {
            // Si une forme est en attente, l'annuler
            if (pixelCanvas.hasPendingShapeToMove()) {
                pixelCanvas.cancelPendingShape()
                updateSelectionActionsVisibility()
                Toast.makeText(this, R.string.pixel_art_shape_cancelled, Toast.LENGTH_SHORT).show()
            } else if (pixelCanvas.deleteSelection()) {
                Toast.makeText(this, R.string.pixel_art_selection_deleted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_no_selection, Toast.LENGTH_SHORT).show()
            }
        }

        // Deselect / Valider la forme
        findViewById<MaterialButton>(R.id.btn_deselect).setOnClickListener {
            // Si une forme est en attente, la valider (appliquer au canvas)
            if (pixelCanvas.hasPendingShapeToMove()) {
                pixelCanvas.finalizePendingShape()
                updateSelectionActionsVisibility()
                Toast.makeText(this, R.string.pixel_art_shape_applied, Toast.LENGTH_SHORT).show()
            } else {
                pixelCanvas.clearSelection()
            }
        }

        // Flip Horizontal
        findViewById<MaterialButton>(R.id.btn_flip_h).setOnClickListener {
            pixelCanvas.flipHorizontal()
        }

        // Flip Vertical
        findViewById<MaterialButton>(R.id.btn_flip_v).setOnClickListener {
            pixelCanvas.flipVertical()
        }

        // Move selection - drag selection content to new position
        findViewById<MaterialButton>(R.id.btn_move_selection).setOnClickListener {
            if (pixelCanvas.hasActiveSelection()) {
                if (pixelCanvas.startMoveSelection()) {
                    Toast.makeText(this, R.string.pixel_art_drag_to_move, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.pixel_art_no_selection, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSelectionActionsVisibility() {
        val isSelectTool = pixelCanvas.currentTool == PixelCanvas.Tool.SELECT ||
                           pixelCanvas.currentTool == PixelCanvas.Tool.MOVE_SELECTION
        val hasPendingShape = pixelCanvas.hasPendingShapeToMove()

        // Afficher les actions si on est en mode sélection OU si on a une forme en attente
        selectionActionsRow.isVisible = isSelectTool || hasPendingShape
    }

    // ========== LAYER MANAGEMENT ==========

    private fun setupLayers() {
        // Layer nudge buttons
        findViewById<MaterialButton>(R.id.btn_layer_nudge_left).setOnClickListener {
            nudgeCurrentLayer(-1, 0)
        }
        findViewById<MaterialButton>(R.id.btn_layer_nudge_right).setOnClickListener {
            nudgeCurrentLayer(1, 0)
        }
        findViewById<MaterialButton>(R.id.btn_layer_nudge_up).setOnClickListener {
            nudgeCurrentLayer(0, -1)
        }
        findViewById<MaterialButton>(R.id.btn_layer_nudge_down).setOnClickListener {
            nudgeCurrentLayer(0, 1)
        }

        // Layer scale buttons
        findViewById<MaterialButton>(R.id.btn_layer_scale_down).setOnClickListener {
            scaleCurrentLayer(-0.1f)
        }
        findViewById<MaterialButton>(R.id.btn_layer_scale_up).setOnClickListener {
            scaleCurrentLayer(0.1f)
        }

        // Reset position button
        findViewById<MaterialButton>(R.id.btn_layer_reset_position).setOnClickListener {
            resetCurrentLayerPosition()
        }

        // Layer selection callback
        pixelCanvas.onLayerSelected = { layer ->
            updateLayerInfoDisplay(layer)
        }

        // Layer change callback
        pixelCanvas.onLayerChanged = { _, _ ->
            updateLayerInfoDisplay(pixelCanvas.getCurrentLayer())
        }
    }

    private fun updateLayerActionsVisibility() {
        val isMoveLayerTool = pixelCanvas.currentTool == PixelCanvas.Tool.MOVE_LAYER
        layerActionsRow.isVisible = isMoveLayerTool && pixelCanvas.getLayerCount() > 0
    }

    private fun updateLayerInfoDisplay(layer: Layer?) {
        if (layer == null) {
            txtLayerInfo.text = getString(R.string.pixel_art_layer_no_layers)
            txtLayerPosition.text = ""
            txtLayerScale.text = ""
            return
        }

        txtLayerInfo.text = layer.name
        txtLayerPosition.text = getString(R.string.pixel_art_layer_position_value, layer.offsetX, layer.offsetY)

        if (layer.type == LayerType.IMAGE) {
            txtLayerScale.text = getString(R.string.pixel_art_layer_scale, layer.scale)
            txtLayerScale.isVisible = true
        } else {
            txtLayerScale.isVisible = false
        }
    }

    private fun nudgeCurrentLayer(dx: Int, dy: Int) {
        val layer = pixelCanvas.getCurrentLayer() ?: return
        pixelCanvas.setLayerOffset(
            pixelCanvas.getCurrentLayerIndex(),
            layer.offsetX + dx,
            layer.offsetY + dy
        )
        updateLayerInfoDisplay(layer)
    }

    private fun scaleCurrentLayer(delta: Float) {
        val layer = pixelCanvas.getCurrentLayer() ?: return
        if (layer.type != LayerType.IMAGE) return
        pixelCanvas.setLayerScale(
            pixelCanvas.getCurrentLayerIndex(),
            layer.scale + delta
        )
        updateLayerInfoDisplay(layer)
    }

    private fun resetCurrentLayerPosition() {
        val index = pixelCanvas.getCurrentLayerIndex()
        val layer = pixelCanvas.getCurrentLayer() ?: return
        pixelCanvas.setLayerOffset(index, 0, 0)
        if (layer.type == LayerType.IMAGE) {
            pixelCanvas.setLayerScale(index, 1f)
        }
        updateLayerInfoDisplay(pixelCanvas.getCurrentLayer())
    }

    private fun showLayersDialog() {
        val options = mutableListOf<String>()
        options.add(getString(R.string.pixel_art_layer_add_image))

        // List existing layers
        val layers = pixelCanvas.getLayers()
        for ((index, layer) in layers.withIndex()) {
            val visibility = if (layer.visible) "👁" else "○"
            val typeIcon = if (layer.type == LayerType.IMAGE) "🖼" else "✏"
            val selected = if (index == pixelCanvas.getCurrentLayerIndex()) "▶ " else "   "
            options.add("$selected$visibility $typeIcon ${layer.name}")
        }

        if (layers.isNotEmpty()) {
            options.add("─────────────")
            options.add(getString(R.string.pixel_art_layer_delete))
            options.add(getString(R.string.pixel_art_layer_duplicate))
            options.add(getString(R.string.pixel_art_layer_move_up))
            options.add(getString(R.string.pixel_art_layer_move_down))
            options.add(getString(R.string.pixel_art_layer_flatten))
            options.add(getString(R.string.pixel_art_layer_settings))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pixel_art_layers_title) + " (${layers.size})")
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> importImageAsLayer()
                    which in 1..layers.size -> {
                        // Select layer
                        pixelCanvas.selectLayer(which - 1)
                        // Toggle visibility on long selection could be added
                    }
                    layers.isNotEmpty() && which == layers.size + 2 -> deleteCurrentLayer()
                    layers.isNotEmpty() && which == layers.size + 3 -> duplicateCurrentLayer()
                    layers.isNotEmpty() && which == layers.size + 4 -> moveLayerUp()
                    layers.isNotEmpty() && which == layers.size + 5 -> moveLayerDown()
                    layers.isNotEmpty() && which == layers.size + 6 -> flattenLayers()
                    layers.isNotEmpty() && which == layers.size + 7 -> showLayerSettingsDialog()
                }
            }
            .show()
    }

    private fun importImageAsLayer() {
        importImageAsLayerLauncher.launch("image/*")
    }

    private fun handleImportImageAsLayer(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Get filename for layer name
                val cursor = contentResolver.query(uri, null, null, null, null)
                var fileName = "Image"
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = it.getString(nameIndex) ?: "Image"
                        }
                    }
                }

                pixelCanvas.addImageLayer(bitmap, fileName)
                updateLayerActionsVisibility()

                // Switch to move layer tool
                selectTool(PixelCanvas.Tool.MOVE_LAYER)

                Toast.makeText(this, R.string.pixel_art_layer_imported, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun importImageAsProject() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_import_image_as_project)
            .setMessage(R.string.pixel_art_import_image_confirm)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                importImageAsProjectLauncher.launch("image/*")
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun handleImportImageAsProject(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Create new project with the image dimensions
                pixelCanvas.newProjectFromBitmap(bitmap)

                // Update UI
                updateCanvasSizeText()
                updateFrameIndicator()

                val msg = getString(R.string.pixel_art_image_imported_as_project, bitmap.width, bitmap.height)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun deleteCurrentLayer() {
        if (pixelCanvas.getLayerCount() == 0) return

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_layer_delete)
            .setMessage(getString(R.string.pixel_art_delete_layer_confirm, pixelCanvas.getCurrentLayer()?.name ?: ""))
            .setPositiveButton(R.string.common_yes) { _, _ ->
                pixelCanvas.deleteLayer(pixelCanvas.getCurrentLayerIndex())
                updateLayerActionsVisibility()
                Toast.makeText(this, R.string.pixel_art_layer_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun duplicateCurrentLayer() {
        if (pixelCanvas.getLayerCount() == 0) return
        pixelCanvas.duplicateLayer(pixelCanvas.getCurrentLayerIndex())
        Toast.makeText(this, R.string.pixel_art_layer_added, Toast.LENGTH_SHORT).show()
    }

    private fun moveLayerUp() {
        pixelCanvas.moveLayerUp(pixelCanvas.getCurrentLayerIndex())
    }

    private fun moveLayerDown() {
        pixelCanvas.moveLayerDown(pixelCanvas.getCurrentLayerIndex())
    }

    private fun flattenLayers() {
        if (pixelCanvas.getLayerCount() == 0) return

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_layer_flatten)
            .setMessage(R.string.pixel_art_flatten_confirm)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                pixelCanvas.flattenLayers()
                updateLayerActionsVisibility()
                Toast.makeText(this, R.string.pixel_art_layer_flattened, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun showLayerSettingsDialog() {
        val layer = pixelCanvas.getCurrentLayer() ?: return
        val index = pixelCanvas.getCurrentLayerIndex()

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Layer name
        val txtNameLabel = TextView(this).apply {
            text = getString(R.string.pixel_art_layer_name)
            setTextColor(Color.WHITE)
        }
        dialogView.addView(txtNameLabel)

        val editName = EditText(this).apply {
            setText(layer.name)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        dialogView.addView(editName)

        // Visibility
        val chkVisible = CheckBox(this).apply {
            text = getString(R.string.pixel_art_layer_visibility)
            isChecked = layer.visible
            setTextColor(Color.WHITE)
        }
        dialogView.addView(chkVisible)

        // Opacity
        val txtOpacity = TextView(this).apply {
            text = getString(R.string.pixel_art_layer_opacity, (layer.opacity * 100).toInt())
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(txtOpacity)

        val sliderOpacity = SeekBar(this).apply {
            max = 100
            progress = (layer.opacity * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    txtOpacity.text = getString(R.string.pixel_art_layer_opacity, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderOpacity)

        // Position (for image layers)
        if (layer.type == LayerType.IMAGE) {
            val txtPosition = TextView(this).apply {
                text = getString(R.string.pixel_art_layer_position)
                setTextColor(Color.WHITE)
                setPadding(0, 24, 0, 8)
            }
            dialogView.addView(txtPosition)

            val positionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val editX = EditText(this).apply {
                hint = getString(R.string.pixel_art_coordinate_x)
                setText(layer.offsetX.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            positionRow.addView(editX)

            val editY = EditText(this).apply {
                hint = getString(R.string.pixel_art_coordinate_y)
                setText(layer.offsetY.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            positionRow.addView(editY)

            dialogView.addView(positionRow)

            // Scale
            val txtScale = TextView(this).apply {
                text = getString(R.string.pixel_art_layer_scale, layer.scale)
                setTextColor(Color.WHITE)
                setPadding(0, 24, 0, 8)
            }
            dialogView.addView(txtScale)

            val sliderScale = SeekBar(this).apply {
                max = 99  // 0.1 to 10.0
                progress = ((layer.scale - 0.1f) * 10).toInt().coerceIn(0, 99)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val scale = 0.1f + progress / 10f
                        txtScale.text = getString(R.string.pixel_art_layer_scale, scale)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }
            dialogView.addView(sliderScale)

            AlertDialog.Builder(this)
                .setTitle(R.string.pixel_art_layer_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    // Apply settings
                    pixelCanvas.renameLayer(index, editName.text.toString())
                    layer.visible = chkVisible.isChecked
                    pixelCanvas.setLayerOpacity(index, sliderOpacity.progress / 100f)
                    val newX = editX.text.toString().toIntOrNull() ?: layer.offsetX
                    val newY = editY.text.toString().toIntOrNull() ?: layer.offsetY
                    pixelCanvas.setLayerOffset(index, newX, newY)
                    val newScale = 0.1f + sliderScale.progress / 10f
                    pixelCanvas.setLayerScale(index, newScale)
                    updateLayerInfoDisplay(layer)
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        } else {
            // For pixel layers (simpler dialog)
            AlertDialog.Builder(this)
                .setTitle(R.string.pixel_art_layer_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    pixelCanvas.renameLayer(index, editName.text.toString())
                    layer.visible = chkVisible.isChecked
                    pixelCanvas.setLayerOpacity(index, sliderOpacity.progress / 100f)
                    updateLayerInfoDisplay(layer)
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        }
    }

    // ========== REFERENCE IMAGE MANAGEMENT ==========

    private fun setupReferenceImage() {
        // Reference handling is now done through toolbar buttons
        // Callback when reference changes
        pixelCanvas.onReferenceChanged = { _ ->
            updateReferenceToolbarButtons()
        }

        // Callback when reference transform changes (during adjustment)
        pixelCanvas.onReferenceTransformChanged = {
            // Could update position display if needed
        }
    }

    private fun updateReferenceToolbarButtons() {
        val hasReference = pixelCanvas.hasReferenceImage()
        val isVisible = pixelCanvas.isReferenceVisible()
        val isAdjusting = pixelCanvas.isAdjustingReference

        val btnRef = findViewById<ImageButton>(R.id.btn_reference_toolbar)
        val btnVisibility = findViewById<ImageButton>(R.id.btn_reference_visibility)
        val btnAdjust = findViewById<ImageButton>(R.id.btn_reference_adjust)

        // Show/hide visibility and adjust buttons based on whether reference is loaded
        btnVisibility.isVisible = hasReference
        btnAdjust.isVisible = hasReference

        // Update main reference button tint
        btnRef.imageTintList = android.content.res.ColorStateList.valueOf(
            if (hasReference) "#FF9800".toColorInt() else "#666666".toColorInt()
        )

        // Update visibility button tint (green when visible, gray when hidden)
        btnVisibility.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isVisible) "#4CAF50".toColorInt() else "#666666".toColorInt()
        )

        // Update adjust button tint (cyan when adjusting, gray otherwise)
        btnAdjust.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isAdjusting) "#00BCD4".toColorInt() else "#666666".toColorInt()
        )
    }

    private fun importReferenceImage() {
        importReferenceImageLauncher.launch("image/*")
    }

    private fun handleImportReferenceImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                pixelCanvas.setReferenceImage(bitmap)
                // Auto-fit to canvas on first load
                pixelCanvas.fitReferenceToCanvas()
                updateReferenceScaleText()

                // Auto-enable adjustment mode so user can position the reference
                pixelCanvas.isAdjustingReference = true
                updateReferenceToolbarButtons()
                Toast.makeText(this,
                    "Image chargée - Pincez pour zoomer, glissez pour déplacer",
                    Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun clearReferenceImage() {
        pixelCanvas.clearReferenceImage()
        pixelCanvas.isAdjustingReference = false
        updateReferenceToolbarButtons()
        Toast.makeText(this, R.string.pixel_art_reference_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun updateReferenceScaleText() {
        val scalePercent = (pixelCanvas.referenceScale * 100).toInt()
        txtReferenceScale.text = "x${String.format("%.1f", pixelCanvas.referenceScale)} ($scalePercent%)"
    }

    private fun showReferenceSettingsDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Visibility
        val chkVisible = CheckBox(this).apply {
            text = getString(R.string.pixel_art_reference_visibility)
            isChecked = pixelCanvas.referenceVisible
            setTextColor(Color.WHITE)
        }
        dialogView.addView(chkVisible)

        // Canvas Transparency (makes checkerboard transparent to see reference)
        val txtTransparency = TextView(this).apply {
            text = "Transparence du canvas: ${(pixelCanvas.canvasTransparency * 100).toInt()}%"
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(txtTransparency)

        val sliderTransparency = SeekBar(this).apply {
            max = 100
            progress = (pixelCanvas.canvasTransparency * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    txtTransparency.text = "Transparence du canvas: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderTransparency)

        // Scale
        val txtScale = TextView(this).apply {
            text = "Échelle: ${String.format("%.2f", pixelCanvas.referenceScale)}x"
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(txtScale)

        val sliderScale = SeekBar(this).apply {
            max = 500  // 0.01 to 5.0 (1 = 0.01, 100 = 1.0, 500 = 5.0)
            progress = (pixelCanvas.referenceScale * 100).toInt().coerceIn(1, 500)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val scale = progress / 100f
                    txtScale.text = "Échelle: ${String.format("%.2f", scale)}x"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        dialogView.addView(sliderScale)

        // Reference image size info
        val refSize = pixelCanvas.getReferenceImageSize()
        if (refSize != null) {
            val scaledSize = pixelCanvas.getScaledReferenceSize()
            val txtSize = TextView(this).apply {
                text = "Image originale: ${refSize.first} x ${refSize.second} px\n" +
                       "Taille actuelle: ${scaledSize?.first?.toInt()} x ${scaledSize?.second?.toInt()} px"
                setTextColor("#888888".toColorInt())
                textSize = 12f
                setPadding(0, 16, 0, 0)
            }
            dialogView.addView(txtSize)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_reference_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                // Apply settings
                pixelCanvas.referenceVisible = chkVisible.isChecked
                pixelCanvas.canvasTransparency = sliderTransparency.progress / 100f
                pixelCanvas.referenceScale = sliderScale.progress / 100f

                // Update UI
                chkReferenceVisible.isChecked = chkVisible.isChecked
                sliderReferenceOpacity.progress = (pixelCanvas.canvasTransparency * 100).toInt()
                txtReferenceOpacity.text = "Transparence: ${sliderReferenceOpacity.progress}%"
                updateReferenceScaleText()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun setupColors() {
        // Load saved palettes and current palette
        loadAllPalettes()
        loadPalette()

        // Build palette UI
        rebuildPaletteUI()

        // Swap colors button
        findViewById<ImageButton>(R.id.btn_swap_colors).setOnClickListener {
            pixelCanvas.swapColors()
            updateColorViews()
        }

        // Palette button - opens palette management dialog
        findViewById<ImageButton>(R.id.btn_palette).setOnClickListener {
            showLoadPaletteDialog()
        }

        // Color picker on primary color click - utilise le color picker partagé avec support alpha
        colorPrimary.setOnClickListener {
            showSharedColorPickerDialog(pixelCanvas.primaryColor) { color ->
                pixelCanvas.primaryColor = color
                updateColorViews()
            }
        }

        colorSecondary.setOnClickListener {
            showSharedColorPickerDialog(pixelCanvas.secondaryColor) { color ->
                pixelCanvas.secondaryColor = color
                updateColorViews()
            }
        }

        updateColorViews()
    }

    private fun loadPalette() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val paletteString = prefs.getString(PALETTE_KEY, null)

        customPalette.clear()
        if (paletteString != null && paletteString.isNotEmpty()) {
            paletteString.split(",").forEach { colorStr ->
                try {
                    customPalette.add(colorStr.toInt())
                } catch (_: NumberFormatException) {
                    // Skip invalid colors
                }
            }
        } else {
            // Use default palette
            customPalette.addAll(defaultPalette.toList())
        }
    }

    private fun savePalette() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val paletteString = customPalette.joinToString(",")
        prefs.edit().putString(PALETTE_KEY, paletteString).apply()
    }

    private fun rebuildPaletteUI() {
        colorPalette.removeAllViews()

        // Wait for layout to get actual width, then rebuild with proper sizing
        colorPalette.post {
            val paletteWidth = colorPalette.width
            if (paletteWidth <= 0 || customPalette.isEmpty()) return@post

            // Calculate optimal number of columns based on available width
            // Target cell size around 52-72px, adjust based on width
            val minCellSize = 52
            val maxCellSize = 72
            val margin = 2

            // How many cells can fit with minimum size?
            val maxColumns = (paletteWidth / (minCellSize + margin * 2)).coerceAtLeast(4)

            // Calculate columns to have 2-3 rows max
            val colorCount = customPalette.size
            val columnsFor2Rows = (colorCount + 1) / 2
            val columnsFor3Rows = (colorCount + 2) / 3

            // Choose column count: prefer 2 rows if cells won't be too small
            val columnCount = when {
                colorCount <= maxColumns -> colorCount  // 1 row if all fit
                columnsFor2Rows <= maxColumns -> columnsFor2Rows  // 2 rows
                else -> columnsFor3Rows.coerceAtMost(maxColumns)  // 3 rows or more
            }

            // Calculate cell size to fill width evenly
            val totalMargins = columnCount * margin * 2
            val cellSize = ((paletteWidth - totalMargins) / columnCount).coerceIn(minCellSize, maxCellSize)

            // Update GridLayout column count
            colorPalette.columnCount = columnCount
            colorPalette.removeAllViews()

            for ((index, color) in customPalette.withIndex()) {
                val row = index / columnCount
                val col = index % columnCount

                val colorView = View(this).apply {
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(row, 1f),
                        GridLayout.spec(col, 1f)
                    ).apply {
                        width = 0  // Use weight
                        height = cellSize
                        setMargins(margin, margin, margin, margin)
                    }
                    // Add a thin border for visibility
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(color)
                        setStroke(2, Color.argb(80, 255, 255, 255))
                        cornerRadius = 6f
                    }
                    setOnClickListener {
                        pixelCanvas.primaryColor = color
                        updateColorViews()
                    }
                    setOnLongClickListener {
                        // Show options: set as secondary or remove
                        showColorOptionsDialog(index, color)
                        true
                    }
                }
                colorPalette.addView(colorView)
            }
        }
    }

    private fun showColorOptionsDialog(index: Int, color: Int) {
        val options = arrayOf("Couleur secondaire", "Supprimer de la palette")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        pixelCanvas.secondaryColor = color
                        updateColorViews()
                    }
                    1 -> {
                        customPalette.removeAt(index)
                        savePalette()
                        rebuildPaletteUI()
                        Toast.makeText(this, R.string.pixel_art_palette_color_removed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun addColorToPalette(color: Int) {
        // Check if color already exists
        if (customPalette.contains(color)) {
            Toast.makeText(this, R.string.pixel_art_color_already_in_palette, Toast.LENGTH_SHORT).show()
            return
        }

        customPalette.add(color)
        savePalette()
        rebuildPaletteUI()
        Toast.makeText(this, R.string.pixel_art_palette_color_added, Toast.LENGTH_SHORT).show()
    }

    private fun resetPalette() {
        customPalette.clear()
        customPalette.addAll(defaultPalette.toList())
        currentPaletteName = "Par défaut"
        savePalette()
        rebuildPaletteUI()
    }

    // ============== Multiple Palette Management ==============

    private fun loadAllPalettes() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        savedPalettes.clear()

        // Load palette name
        currentPaletteName = prefs.getString(CURRENT_PALETTE_NAME_KEY, "Par défaut") ?: "Par défaut"

        // Load saved palettes from JSON
        val palettesJson = prefs.getString(PALETTES_KEY, null)
        if (palettesJson != null) {
            try {
                val jsonArray = org.json.JSONArray(palettesJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val colorsArray = obj.getJSONArray("colors")
                    val colors = mutableListOf<Int>()
                    for (j in 0 until colorsArray.length()) {
                        colors.add(colorsArray.getInt(j))
                    }
                    savedPalettes.add(NamedPalette(name, colors))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveAllPalettes() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Save current palette name
        prefs.edit().putString(CURRENT_PALETTE_NAME_KEY, currentPaletteName).apply()

        // Save palettes as JSON
        try {
            val jsonArray = org.json.JSONArray()
            for (palette in savedPalettes) {
                val obj = org.json.JSONObject()
                obj.put("name", palette.name)
                val colorsArray = org.json.JSONArray()
                for (color in palette.colors) {
                    colorsArray.put(color)
                }
                obj.put("colors", colorsArray)
                jsonArray.put(obj)
            }
            prefs.edit().putString(PALETTES_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSavePaletteDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.pixel_art_palette_name_hint)
            setText(if (currentPaletteName == "Par défaut") "" else currentPaletteName)
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_palette_save)
            .setView(editText)
            .setPositiveButton(R.string.common_save) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.pixel_art_enter_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Check if palette with same name exists
                val existingIndex = savedPalettes.indexOfFirst { it.name == name }
                if (existingIndex >= 0) {
                    savedPalettes[existingIndex] = NamedPalette(name, customPalette.toList())
                } else {
                    savedPalettes.add(NamedPalette(name, customPalette.toList()))
                }

                currentPaletteName = name
                saveAllPalettes()
                Toast.makeText(this, getString(R.string.pixel_art_palette_saved, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showLoadPaletteDialog() {
        var currentDialog: AlertDialog? = null

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        // Action buttons row (Save + Reset)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val btnSavePalette = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pixel_art_palette_save)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener {
                currentDialog?.dismiss()
                showSavePaletteDialog()
            }
        }
        actionRow.addView(btnSavePalette)

        val btnResetPalette = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pixel_art_palette_reset_default)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                AlertDialog.Builder(this@PixelArtEditorActivity)
                    .setMessage(R.string.pixel_art_palette_reset_confirm)
                    .setPositiveButton(R.string.common_yes) { _, _ ->
                        resetPalette()
                        currentDialog?.dismiss()
                    }
                    .setNegativeButton(R.string.common_no, null)
                    .show()
            }
        }
        actionRow.addView(btnResetPalette)
        dialogView.addView(actionRow)

        // Import options
        val txtImport = TextView(this).apply {
            text = getString(R.string.pixel_art_label_import_palette)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        dialogView.addView(txtImport)

        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnImportPng = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pixel_art_label_palette_png)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener {
                currentDialog?.dismiss()
                importPalettePngLauncher.launch("image/png")
            }
        }
        importRow.addView(btnImportPng)

        val btnImportHex = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pixel_art_label_palette_hex)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                currentDialog?.dismiss()
                importPaletteHexLauncher.launch("*/*")
            }
        }
        importRow.addView(btnImportHex)
        dialogView.addView(importRow)

        // Saved palettes section
        if (savedPalettes.isNotEmpty()) {
            val txtSaved = TextView(this).apply {
                text = getString(R.string.pixel_art_label_saved_palettes)
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 24, 0, 8)
            }
            dialogView.addView(txtSaved)

            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    400
                )
            }

            val palettesContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            for ((index, palette) in savedPalettes.withIndex()) {
                val paletteRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor("#333333".toColorInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                }

                // Palette name
                val txtName = TextView(this).apply {
                    text = palette.name
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                paletteRow.addView(txtName)

                // Color preview
                val colorPreview = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 4, 0, 4)
                }
                val maxPreview = minOf(palette.colors.size, 12)
                for (i in 0 until maxPreview) {
                    val colorBox = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                            marginEnd = 2
                        }
                        setBackgroundColor(palette.colors[i])
                    }
                    colorPreview.addView(colorBox)
                }
                if (palette.colors.size > 12) {
                    val txtMore = TextView(this).apply {
                        text = "+${palette.colors.size - 12}"
                        setTextColor(Color.GRAY)
                        textSize = 10f
                    }
                    colorPreview.addView(txtMore)
                }
                paletteRow.addView(colorPreview)

                // Action buttons
                val actionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val btnLoad = Button(this).apply {
                    text = getString(R.string.pixel_art_button_load)
                    textSize = 10f
                    setOnClickListener {
                        customPalette.clear()
                        customPalette.addAll(palette.colors)
                        currentPaletteName = palette.name
                        savePalette()
                        saveAllPalettes()
                        rebuildPaletteUI()
                        currentDialog?.dismiss()
                        Toast.makeText(this@PixelArtEditorActivity, getString(R.string.pixel_art_palette_loaded, palette.name), Toast.LENGTH_SHORT).show()
                    }
                }
                actionRow.addView(btnLoad)

                val btnDelete = Button(this).apply {
                    text = getString(R.string.pixel_art_button_delete)
                    textSize = 10f
                    setTextColor("#FF5722".toColorInt())
                    setOnClickListener {
                        AlertDialog.Builder(this@PixelArtEditorActivity)
                            .setMessage(getString(R.string.pixel_art_palette_delete_confirm, palette.name))
                            .setPositiveButton(R.string.common_yes) { _, _ ->
                                savedPalettes.removeAt(index)
                                saveAllPalettes()
                                (dialogView.parent as? android.app.Dialog)?.dismiss()
                                showLoadPaletteDialog() // Refresh
                            }
                            .setNegativeButton(R.string.common_no, null)
                            .show()
                    }
                }
                actionRow.addView(btnDelete)

                paletteRow.addView(actionRow)
                palettesContainer.addView(paletteRow)
            }

            scrollView.addView(palettesContainer)
            dialogView.addView(scrollView)
        }

        currentDialog = AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_palette)
            .setView(dialogView)
            .setNegativeButton(R.string.common_close, null)
            .create()

        currentDialog?.show()
    }

    private fun handleImportPalettePng(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Toast.makeText(this, R.string.pixel_art_cannot_read_image, Toast.LENGTH_SHORT).show()
                return
            }

            // Extract unique colors from the image
            val colors = mutableSetOf<Int>()
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 0) {
                        colors.add(pixel or 0xFF000000.toInt()) // Ensure fully opaque
                    }
                }
            }

            if (colors.isEmpty()) {
                Toast.makeText(this, R.string.pixel_art_no_colors_found, Toast.LENGTH_SHORT).show()
                return
            }

            // Show naming dialog
            showNamePaletteDialog(colors.toList(), uri.lastPathSegment ?: "Palette importée")

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImportPaletteHex(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val content = inputStream.bufferedReader().readText()
            inputStream.close()

            // Parse hex colors (supports #RRGGBB, RRGGBB, and various formats)
            val hexPattern = Regex("[#]?([0-9A-Fa-f]{6})")
            val matches = hexPattern.findAll(content)

            val colors = mutableListOf<Int>()
            for (match in matches) {
                val hexValue = match.groupValues[1]
                val color = "#$hexValue".toColorInt()
                if (!colors.contains(color)) {
                    colors.add(color)
                }
            }

            if (colors.isEmpty()) {
                Toast.makeText(this, R.string.pixel_art_no_hex_colors_found, Toast.LENGTH_SHORT).show()
                return
            }

            // Show naming dialog
            showNamePaletteDialog(colors, uri.lastPathSegment?.substringBeforeLast('.') ?: "Palette importée")

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.pixel_art_import_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNamePaletteDialog(colors: List<Int>, defaultName: String) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        // Color preview
        val txtPreview = TextView(this).apply {
            text = "${colors.size} couleurs trouvées"
            setTextColor(Color.WHITE)
        }
        dialogView.addView(txtPreview)

        val colorPreview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 16)
        }
        val maxPreview = minOf(colors.size, 16)
        for (i in 0 until maxPreview) {
            val colorBox = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(28, 28).apply {
                    marginEnd = 2
                }
                setBackgroundColor(colors[i])
            }
            colorPreview.addView(colorBox)
        }
        if (colors.size > 16) {
            val txtMore = TextView(this).apply {
                text = "+${colors.size - 16}"
                setTextColor(Color.GRAY)
            }
            colorPreview.addView(txtMore)
        }
        dialogView.addView(colorPreview)

        // Name input
        val editName = EditText(this).apply {
            hint = getString(R.string.pixel_art_palette_name_hint)
            setText(defaultName)
        }
        dialogView.addView(editName)

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_palette_import_title)
            .setView(dialogView)
            .setPositiveButton(R.string.pixel_art_import_and_load) { _, _ ->
                val name = editName.text.toString().trim().ifEmpty { defaultName }

                // Save as new palette
                savedPalettes.add(NamedPalette(name, colors))
                saveAllPalettes()

                // Load it
                customPalette.clear()
                customPalette.addAll(colors)
                currentPaletteName = name
                savePalette()
                rebuildPaletteUI()
                Toast.makeText(this, getString(R.string.pixel_art_palette_imported, name, colors.size), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.pixel_art_save_only) { _, _ ->
                val name = editName.text.toString().trim().ifEmpty { defaultName }
                savedPalettes.add(NamedPalette(name, colors))
                saveAllPalettes()
                Toast.makeText(this, getString(R.string.pixel_art_palette_saved, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun updateColorViews() {
        colorPrimary.setBackgroundColor(pixelCanvas.primaryColor)
        colorSecondary.setBackgroundColor(pixelCanvas.secondaryColor)
    }


    private fun setupZoomControls() {
        findViewById<ImageButton>(R.id.btn_zoom_in).setOnClickListener {
            pixelCanvas.zoomIn()
            updateZoomText()
        }

        findViewById<ImageButton>(R.id.btn_zoom_out).setOnClickListener {
            pixelCanvas.zoomOut()
            updateZoomText()
        }

        findViewById<CheckBox>(R.id.chk_grid).setOnCheckedChangeListener { _, isChecked ->
            pixelCanvas.showGrid = isChecked
        }

        updateZoomText()
    }

    private fun setupTimeline() {
        // Playback controls
        findViewById<ImageButton>(R.id.btn_prev_frame).setOnClickListener {
            pixelCanvas.previousFrame()
        }

        findViewById<ImageButton>(R.id.btn_next_frame).setOnClickListener {
            pixelCanvas.nextFrame()
        }

        btnPlayPause.setOnClickListener {
            if (pixelCanvas.isAnimationPlaying()) {
                pixelCanvas.pauseAnimation()
            } else {
                pixelCanvas.playAnimation()
            }
        }

        // Long press on play to open FPS settings
        btnPlayPause.setOnLongClickListener {
            showFpsSettingsDialog()
            true
        }

        // Frame management
        findViewById<ImageButton>(R.id.btn_add_frame).setOnClickListener {
            pixelCanvas.addFrame()
            Toast.makeText(this, R.string.pixel_art_frame_added, Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btn_duplicate_frame).setOnClickListener {
            pixelCanvas.duplicateFrame()
            Toast.makeText(this, R.string.pixel_art_frame_duplicated, Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btn_delete_frame).setOnClickListener {
            // Check if it's the last frame first
            if (pixelCanvas.getFrameCount() <= 1) {
                Toast.makeText(this, R.string.pixel_art_cannot_delete_last, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show confirmation dialog
            AlertDialog.Builder(this)
                .setTitle(R.string.pixel_art_delete_frame)
                .setMessage(getString(R.string.pixel_art_delete_frame_confirm, pixelCanvas.getCurrentFrameIndex() + 1))
                .setPositiveButton(R.string.common_yes) { _, _ ->
                    if (pixelCanvas.deleteFrame()) {
                        updateTimelineThumbnails()
                        updateFrameIndicator()
                        Toast.makeText(this, R.string.pixel_art_frame_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.common_no, null)
                .show()
        }

        // Initial UI update
        updateFrameIndicator()
        updateTimelineThumbnails()
    }

    private fun setupSheetMode() {
        // Toggle button
        btnSheetMode.setOnClickListener {
            // Check if we should offer to split the sheet
            if (pixelCanvas.getFrameCount() == 1 && !pixelCanvas.isSheetMode()) {
                val splitOptions = pixelCanvas.getSplitOptions()
                if (splitOptions.isNotEmpty()) {
                    showSplitSheetDialog(splitOptions)
                    return@setOnClickListener
                }
            }
            pixelCanvas.toggleViewMode()
        }

        // Callback for mode changes
        pixelCanvas.onViewModeChanged = { mode ->
            updateSheetModeUI(mode)
        }

        pixelCanvas.onSheetActiveFrameChanged = { _ ->
            updateSheetInfo()
        }

        // Column spinner setup
        setupColumnSpinner()

        // Fit button
        btnFitSheet.setOnClickListener {
            pixelCanvas.fitSheetInView()
        }

        // Grid lines checkbox
        chkSheetGridLines.setOnCheckedChangeListener { _, isChecked ->
            pixelCanvas.showSheetGridLines = isChecked
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPreviewWindow() {
        // Toggle preview window visibility
        btnSheetPreview.setOnClickListener {
            if (previewWindow.isVisible) {
                hidePreviewWindow()
            } else {
                showPreviewWindow()
            }
        }

        // Close button
        btnPreviewClose.setOnClickListener {
            hidePreviewWindow()
        }

        // Play/pause button
        btnPreviewPlay.setOnClickListener {
            if (isPreviewPlaying) {
                stopPreviewAnimation()
            } else {
                startPreviewAnimation()
            }
        }

        // Long press on play for FPS settings
        btnPreviewPlay.setOnLongClickListener {
            showFpsSettingsDialog()
            true
        }

        // Previous frame button
        btnPreviewPrev.setOnClickListener {
            if (isPreviewPlaying) stopPreviewAnimation()
            val frameCount = pixelCanvas.getFrameCount()
            if (frameCount > 0) {
                previewFrameIndex = (previewFrameIndex - 1 + frameCount) % frameCount
                updatePreviewImage(previewFrameIndex)
                pixelCanvas.setPreviewPlaybackFrame(previewFrameIndex)
            }
        }

        // Next frame button
        btnPreviewNext.setOnClickListener {
            if (isPreviewPlaying) stopPreviewAnimation()
            val frameCount = pixelCanvas.getFrameCount()
            if (frameCount > 0) {
                previewFrameIndex = (previewFrameIndex + 1) % frameCount
                updatePreviewImage(previewFrameIndex)
                pixelCanvas.setPreviewPlaybackFrame(previewFrameIndex)
            }
        }

        // Make header draggable
        previewHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previewDragStartX = event.rawX
                    previewDragStartY = event.rawY
                    previewWindowStartX = previewWindow.translationX
                    previewWindowStartY = previewWindow.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - previewDragStartX
                    val dy = event.rawY - previewDragStartY
                    previewWindow.translationX = previewWindowStartX + dx
                    previewWindow.translationY = previewWindowStartY + dy
                    true
                }
                else -> false
            }
        }

        // Pinch-to-zoom to resize window (both width and height)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val minSize = (80 * displayMetrics.density).toInt()  // 80dp min
        val maxSize = minOf(screenWidth, screenHeight) * 9 / 10  // 90% of smallest screen dimension

        previewScaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor

                // Scale window width
                val currentWidth = previewWindow.width
                val newWidth = (currentWidth * scaleFactor).toInt().coerceIn(minSize, maxSize)

                // Scale image height proportionally
                val currentImageHeight = previewImage.layoutParams.height
                val newImageHeight = (currentImageHeight * scaleFactor).toInt().coerceIn(minSize, maxSize)

                // Apply new width to window
                val windowParams = previewWindow.layoutParams
                windowParams.width = newWidth
                previewWindow.layoutParams = windowParams

                // Apply new height to image
                val imageParams = previewImage.layoutParams
                imageParams.height = newImageHeight
                previewImage.layoutParams = imageParams

                previewWindow.requestLayout()
                return true
            }
        })

        previewImage.setOnTouchListener { _, event ->
            previewScaleGestureDetector?.onTouchEvent(event)
            true
        }
    }

    private fun showPreviewWindow() {
        previewWindow.isVisible = true
        btnSheetPreview.imageTintList = android.content.res.ColorStateList.valueOf(
            "#4CAF50".toColorInt()  // Green when active
        )
        // Show first frame
        updatePreviewImage(0)
    }

    private fun hidePreviewWindow() {
        stopPreviewAnimation()
        previewWindow.isVisible = false
        btnSheetPreview.imageTintList = android.content.res.ColorStateList.valueOf(
            "#FF9800".toColorInt()  // Orange when inactive
        )
    }

    private fun startPreviewAnimation() {
        isPreviewPlaying = true
        previewFrameIndex = 0
        btnPreviewPlay.setImageResource(android.R.drawable.ic_media_pause)
        btnPreviewPlay.imageTintList = android.content.res.ColorStateList.valueOf(
            "#FF5722".toColorInt()  // Red-orange when playing
        )
        pixelCanvas.startPreviewPlayback()
        scheduleNextFrame()
    }

    private fun stopPreviewAnimation() {
        isPreviewPlaying = false
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        previewRunnable = null
        btnPreviewPlay.setImageResource(android.R.drawable.ic_media_play)
        btnPreviewPlay.imageTintList = android.content.res.ColorStateList.valueOf(
            "#4CAF50".toColorInt()  // Green when paused
        )
        pixelCanvas.stopPreviewPlayback()
    }

    private fun scheduleNextFrame() {
        if (!isPreviewPlaying) return

        val frameCount = pixelCanvas.getFrameCount()
        if (frameCount == 0) return

        // Use shared animationFps setting (same as timeline)
        val frameDuration = (1000 / pixelCanvas.animationFps).toLong().coerceAtLeast(16)

        previewRunnable = Runnable {
            if (isPreviewPlaying) {
                // Update image and highlight
                updatePreviewImage(previewFrameIndex)
                pixelCanvas.setPreviewPlaybackFrame(previewFrameIndex)

                // Move to next frame
                previewFrameIndex = (previewFrameIndex + 1) % frameCount

                // Schedule next
                scheduleNextFrame()
            }
        }
        previewHandler.postDelayed(previewRunnable!!, frameDuration)
    }

    private fun updatePreviewImage(frameIndex: Int) {
        val bitmap = pixelCanvas.getFrameBitmap(frameIndex) ?: return

        // Scale bitmap for display (nearest neighbor for pixel art)
        val displaySize = previewImage.width.coerceAtLeast(100)
        val scale = displaySize / bitmap.width.coerceAtLeast(1)
        val scaledWidth = bitmap.width * scale.coerceAtLeast(1)
        val scaledHeight = bitmap.height * scale.coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
        previewImage.setImageBitmap(scaledBitmap)
    }

    private fun setupColumnSpinner() {
        val frameCount = pixelCanvas.getFrameCount().coerceAtLeast(1)
        val options = mutableListOf(getString(R.string.pixel_art_sheet_auto))
        for (i in 1..frameCount.coerceAtMost(16)) {
            options.add("$i")
        }

        spinnerSheetColumns.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )

        spinnerSheetColumns.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                pixelCanvas.sheetColumns = if (position == 0) 0 else position
                updateSheetInfo()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSheetModeUI(mode: ViewMode) {
        val isSheetMode = mode == ViewMode.SHEET

        // Update button appearance
        btnSheetMode.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isSheetMode) "#FF9800".toColorInt() else "#666666".toColorInt()
        )

        // Show/hide sheet config panel
        sheetConfigPanel.isVisible = isSheetMode

        // Hide timeline in sheet mode (frames visible on canvas)
        timelinePanel.isVisible = !isSheetMode

        if (isSheetMode) {
            setupColumnSpinner()  // Refresh with current frame count
            pixelCanvas.fitSheetInView()
            updateSheetInfo()
        } else {
            // Hide preview window when leaving sheet mode
            hidePreviewWindow()
        }
    }

    private fun updateSheetInfo() {
        if (!pixelCanvas.isSheetMode()) return

        val layout = pixelCanvas.getSheetLayout()
        val activeFrame = pixelCanvas.getSheetActiveFrameIndex() + 1
        val totalFrames = pixelCanvas.getFrameCount()

        txtSheetInfo.text = getString(
            R.string.pixel_art_sheet_info,
            activeFrame,
            totalFrames,
            layout.columns,
            layout.rows
        )
    }

    private fun showSplitSheetDialog(splitOptions: List<PixelCanvas.SplitOption>) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        // Title
        val txtTitle = TextView(this).apply {
            text = getString(R.string.pixel_art_split_sheet_title)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        dialogView.addView(txtTitle)

        // Description
        val txtDesc = TextView(this).apply {
            text = getString(R.string.pixel_art_split_sheet_desc)
            setTextColor("#AAAAAA".toColorInt())
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        dialogView.addView(txtDesc)

        // Options spinner
        val optionLabels = splitOptions.map { it.getLabel() }.toTypedArray()
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@PixelArtEditorActivity, android.R.layout.simple_spinner_dropdown_item, optionLabels)
        }
        dialogView.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_split_sheet_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.pixel_art_split_sheet_confirm) { _, _ ->
                val selectedOption = splitOptions[spinner.selectedItemPosition]
                if (pixelCanvas.splitSheetIntoFrames(selectedOption.cols, selectedOption.rows)) {
                    updateCanvasSizeText()
                    updateTimelineThumbnails()
                    updateFrameIndicator()
                    pixelCanvas.toggleViewMode() // Switch to sheet mode after splitting
                    Toast.makeText(
                        this,
                        getString(R.string.pixel_art_split_success, selectedOption.frameCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNeutralButton(R.string.pixel_art_split_sheet_skip) { _, _ ->
                // Just toggle to sheet mode without splitting
                pixelCanvas.toggleViewMode()
            }
            .setNegativeButton(R.string.pixel_art_split_sheet_cancel, null)
            .show()
    }

    private fun showReferenceImageDialog() {
        val hasReference = pixelCanvas.hasReferenceImage()

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        // Title with icon
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_gallery)
            setColorFilter("#FF9800".toColorInt())
            layoutParams = LinearLayout.LayoutParams(48, 48)
        }
        titleRow.addView(iconView)
        val titleText = TextView(this).apply {
            text = getString(R.string.pixel_art_label_reference_image)
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(16, 0, 0, 0)
        }
        titleRow.addView(titleText)
        dialogView.addView(titleRow)

        // Status text
        val statusText = TextView(this).apply {
            text = if (hasReference) "✓ Image chargée" else "Aucune image"
            setTextColor(if (hasReference) "#4CAF50".toColorInt() else "#888888".toColorInt())
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        dialogView.addView(statusText)

        // Visibility toggle (only if has reference)
        if (hasReference) {
            val visibilityRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 16)
            }
            val chkVisible = CheckBox(this).apply {
                text = getString(R.string.pixel_art_button_show_image)
                isChecked = pixelCanvas.isReferenceVisible()
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf("#FF9800".toColorInt())
                setOnCheckedChangeListener { _, isChecked ->
                    pixelCanvas.updateReferenceVisibility(isChecked)
                }
            }
            visibilityRow.addView(chkVisible)
            dialogView.addView(visibilityRow)

            // Adjustment mode toggle (position/resize reference)
            val adjustRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 16)
            }
            val chkAdjustMode = CheckBox(this).apply {
                text = "📐 Mode positionnement"
                isChecked = pixelCanvas.isAdjustingReference
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf("#4CAF50".toColorInt())
                setOnCheckedChangeListener { _, isChecked ->
                    pixelCanvas.isAdjustingReference = isChecked
                    if (isChecked) {
                        Toast.makeText(this@PixelArtEditorActivity,
                            "Pincez pour zoomer, glissez pour déplacer", Toast.LENGTH_LONG).show()
                    }
                }
            }
            adjustRow.addView(chkAdjustMode)
            dialogView.addView(adjustRow)

            // Help text for adjustment mode
            val adjustHelpText = TextView(this).apply {
                text = getString(R.string.pixel_art_label_reposition_image)
                setTextColor("#888888".toColorInt())
                textSize = 11f
                setPadding(48, 0, 0, 16)
            }
            dialogView.addView(adjustHelpText)

            // Opacity slider for reference
            val opacityLabel = TextView(this).apply {
                text = "Opacité: ${(pixelCanvas.currentReferenceOpacity() * 100).toInt()}%"
                setTextColor("#aaaaaa".toColorInt())
                textSize = 12f
            }
            dialogView.addView(opacityLabel)

            val opacitySlider = SeekBar(this).apply {
                max = 100
                progress = (pixelCanvas.currentReferenceOpacity() * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        pixelCanvas.updateReferenceOpacity(progress / 100f)
                        opacityLabel.text = "Opacité: ${progress}%"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            dialogView.addView(opacitySlider)
        }

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton(R.string.common_close, null)

        if (hasReference) {
            builder.setNeutralButton(R.string.common_delete) { _, _ ->
                pixelCanvas.clearReferenceImage()
                Toast.makeText(this, R.string.pixel_art_reference_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        builder.setPositiveButton(if (hasReference) "Changer" else "Charger") { _, _ ->
            importReferenceImageLauncher.launch("image/*")
        }

        builder.show()
    }

    private fun showBackgroundSettingsDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        // Title with icon
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 20)
        }
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setColorFilter("#9C27B0".toColorInt())
            layoutParams = LinearLayout.LayoutParams(48, 48)
        }
        titleRow.addView(iconView)
        val titleText = TextView(this).apply {
            text = getString(R.string.pixel_art_label_canvas_background)
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(16, 0, 0, 0)
        }
        titleRow.addView(titleText)
        dialogView.addView(titleRow)

        // Canvas transparency slider
        val transparencyLabel = TextView(this).apply {
            val percent = (pixelCanvas.canvasTransparency * 100).toInt()
            text = "Transparence grille: ${percent}%"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        dialogView.addView(transparencyLabel)

        val transparencySlider = SeekBar(this).apply {
            max = 100
            progress = (pixelCanvas.canvasTransparency * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf("#9C27B0".toColorInt())
            thumbTintList = android.content.res.ColorStateList.valueOf("#9C27B0".toColorInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pixelCanvas.canvasTransparency = progress / 100f
                    transparencyLabel.text = "Transparence grille: ${progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        dialogView.addView(transparencySlider)

        // Hint text
        val hintText = TextView(this).apply {
            text = "0% = damier visible\n100% = fond transparent"
            setTextColor("#666666".toColorInt())
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        dialogView.addView(hintText)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFpsSettingsDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        var currentFps = pixelCanvas.animationFps

        // Current FPS value
        val txtFps = TextView(this).apply {
            text = getString(R.string.pixel_art_fps_value, currentFps)
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        dialogView.addView(txtFps)

        // FPS hint
        val txtHint = TextView(this).apply {
            text = getString(R.string.pixel_art_fps_hint)
            textSize = 12f
            setTextColor("#888888".toColorInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        dialogView.addView(txtHint)

        // FPS slider (1-60) - create first so buttons can reference it
        val sliderFps = SeekBar(this).apply {
            max = 59  // 0-59 = 1-60
            progress = currentFps - 1
        }

        // Function to update both display and slider
        fun updateFps(newFps: Int) {
            currentFps = newFps.coerceIn(1, 60)
            txtFps.text = getString(R.string.pixel_art_fps_value, currentFps)
            sliderFps.progress = currentFps - 1
        }

        // +/- buttons row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val btnMinus5 = Button(this).apply {
            text = "-5"
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { updateFps(currentFps - 5) }
        }
        buttonRow.addView(btnMinus5)

        val btnMinus1 = Button(this).apply {
            text = "-1"
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { updateFps(currentFps - 1) }
        }
        buttonRow.addView(btnMinus1)

        val btnPlus1 = Button(this).apply {
            text = "+1"
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { updateFps(currentFps + 1) }
        }
        buttonRow.addView(btnPlus1)

        val btnPlus5 = Button(this).apply {
            text = "+5"
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { updateFps(currentFps + 5) }
        }
        buttonRow.addView(btnPlus5)

        dialogView.addView(buttonRow)

        // Set slider listener
        sliderFps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentFps = progress + 1
                    txtFps.text = getString(R.string.pixel_art_fps_value, currentFps)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        dialogView.addView(sliderFps)

        // Min/Max labels
        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val txtMin = TextView(this).apply {
            text = "1"
            setTextColor("#888888".toColorInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val txtMax = TextView(this).apply {
            text = "60"
            setTextColor("#888888".toColorInt())
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelsRow.addView(txtMin)
        labelsRow.addView(txtMax)
        dialogView.addView(labelsRow)

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_fps_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                pixelCanvas.animationFps = currentFps
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun updateFrameIndicator() {
        val current = pixelCanvas.getCurrentFrameIndex() + 1
        val total = pixelCanvas.getFrameCount()
        txtFrameIndicator.text = getString(R.string.pixel_art_frame_indicator, current, total)
    }

    private fun updateTimelineThumbnails() {
        timelineFrames.removeAllViews()

        val frameCount = pixelCanvas.getFrameCount()
        val currentIndex = pixelCanvas.getCurrentFrameIndex()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        for (i in 0 until frameCount) {
            // Create a simple frame thumbnail view - adapt size and margins for orientation
            val thumbnailSize = if (isLandscape) 56 else 72
            val thumbnailContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbnailSize, thumbnailSize).apply {
                    if (isLandscape) {
                        bottomMargin = 6
                    } else {
                        marginEnd = 6
                    }
                }
                setBackgroundColor(if (i == currentIndex) "#4CAF50".toColorInt() else "#444444".toColorInt())
                setPadding(3, 3, 3, 3)
            }

            val thumbnail = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor("#333333".toColorInt())

                // Get thumbnail bitmap
                val thumbBitmap = pixelCanvas.getFrameThumbnail(i, 66)
                if (thumbBitmap != null) {
                    setImageBitmap(thumbBitmap)
                }
            }

            thumbnailContainer.addView(thumbnail)

            // Blue overlay for frames in onion skin (only for non-current frames)
            val isInOnionSkin = pixelCanvas.isFrameInOnionSkin(i)
            if (isInOnionSkin && i != currentIndex) {
                val onionOverlay = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor("#662196F3".toColorInt())  // Semi-transparent blue
                }
                thumbnailContainer.addView(onionOverlay)
            }

            // Click to toggle onion skin visibility
            thumbnailContainer.setOnClickListener {
                if (i != currentIndex) {
                    pixelCanvas.toggleOnionSkinFrame(i)
                    updateTimelineThumbnails()  // Refresh to show/hide overlay
                }
            }

            // Long click to select frame for editing
            thumbnailContainer.setOnLongClickListener {
                pixelCanvas.goToFrame(i)
                true
            }

            timelineFrames.addView(thumbnailContainer)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun setupCanvas() {
        pixelCanvas.onHistoryChanged = { canUndo, canRedo ->
            // Undo button enabled if frame undo OR project undo available
            val canUndoAny = canUndo || pixelCanvas.canUndoProjectChange()
            btnUndo.isEnabled = canUndoAny
            btnRedo.isEnabled = canRedo
            btnUndo.setTextColor(if (canUndoAny) Color.WHITE else "#888888".toColorInt())
            btnRedo.setTextColor(if (canRedo) Color.WHITE else "#888888".toColorInt())
        }

        pixelCanvas.onColorPicked = { _ ->
            updateColorViews()
            // Switch back to pencil tool after picking
            selectTool(PixelCanvas.Tool.PENCIL)
        }

        pixelCanvas.onZoomChanged = { _ ->
            updateZoomText()
        }

        // Frame callbacks
        pixelCanvas.onFrameChanged = { _, _ ->
            updateFrameIndicator()
            updateTimelineThumbnails()
        }

        pixelCanvas.onPlaybackChanged = { isPlaying ->
            updatePlayPauseButton(isPlaying)
        }

        // Project-level undo callback - update undo button state
        pixelCanvas.onProjectSnapshotChanged = { canUndoProject, _ ->
            // Update undo button to reflect project undo availability
            val canUndoFrame = pixelCanvas.canUndo()
            val canUndoAny = canUndoFrame || canUndoProject
            btnUndo.isEnabled = canUndoAny
            btnUndo.setTextColor(if (canUndoAny) Color.WHITE else "#888888".toColorInt())
        }

        // Callback when a shape is created/removed - show/hide selection actions
        pixelCanvas.onPendingShapeChanged = { _ ->
            runOnUiThread {
                updateSelectionActionsVisibility()
            }
        }
    }

    private fun updateZoomText() {
        val zoomPercent = (pixelCanvas.getZoom() * 100).toInt()
        txtZoom.text = "$zoomPercent%"
    }

    private fun updateCanvasSizeText() {
        btnCanvasSize.text = getString(
            R.string.pixel_art_canvas_size,
            pixelCanvas.getCanvasWidth(),
            pixelCanvas.getCanvasHeight()
        )
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_action_clear)
            .setMessage(R.string.pixel_art_clear_confirm)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                pixelCanvas.clearCanvas()
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    /**
     * Affiche le color picker partagé avec support de la transparence (alpha)
     * Utilise SimpleColorPickerDialog du module Notes avec showAlpha = true
     */
    private fun showSharedColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        // Convertir la couleur Int en format hex #RRGGBB (sans alpha pour le picker)
        val hexColor = String.format("#%06X", 0xFFFFFF and initialColor)
        val initialAlpha = Color.alpha(initialColor)

        val dialog = SimpleColorPickerDialog(
            context = this,
            currentColorHex = hexColor,
            currentTextColorMode = "auto",
            showAlpha = true,
            showTextMode = false,
            initialAlpha = initialAlpha,
            onColorWithAlphaSelected = { colorInt ->
                onColorSelected(colorInt)
            },
            onColorSelected = { _, _ -> } // Non utilisé car on utilise onColorWithAlphaSelected
        )
        dialog.show()
    }

    @Deprecated("Utilisez showSharedColorPickerDialog à la place")
    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)

        val colorPreview = dialogView.findViewById<View>(R.id.color_preview)
        val colorHex = dialogView.findViewById<TextView>(R.id.color_hex)
        val sliderHue = dialogView.findViewById<SeekBar>(R.id.slider_hue)
        val sliderSaturation = dialogView.findViewById<SeekBar>(R.id.slider_saturation)
        val sliderValue = dialogView.findViewById<SeekBar>(R.id.slider_value)
        val sliderAlpha = dialogView.findViewById<SeekBar>(R.id.slider_alpha)
        val txtRgb = dialogView.findViewById<TextView>(R.id.txt_rgb)
        val presetColors = dialogView.findViewById<LinearLayout>(R.id.preset_colors)

        // Current HSV values
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var currentAlpha = Color.alpha(initialColor)

        // Initialize sliders from initial color
        sliderHue.progress = hsv[0].toInt()
        sliderSaturation.progress = (hsv[1] * 100).toInt()
        sliderValue.progress = (hsv[2] * 100).toInt()
        sliderAlpha.progress = currentAlpha

        fun updatePreview() {
            val color = Color.HSVToColor(currentAlpha, hsv)
            colorPreview.setBackgroundColor(color)
            colorHex.text = String.format("#%08X", color)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            txtRgb.text = "R: $r  G: $g  B: $b  A: $currentAlpha"
        }

        updatePreview()

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                when (seekBar.id) {
                    R.id.slider_hue -> hsv[0] = progress.toFloat()
                    R.id.slider_saturation -> hsv[1] = progress / 100f
                    R.id.slider_value -> hsv[2] = progress / 100f
                    R.id.slider_alpha -> currentAlpha = progress
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }

        sliderHue.setOnSeekBarChangeListener(seekBarListener)
        sliderSaturation.setOnSeekBarChangeListener(seekBarListener)
        sliderValue.setOnSeekBarChangeListener(seekBarListener)
        sliderAlpha.setOnSeekBarChangeListener(seekBarListener)

        // Add preset colors
        val presets = intArrayOf(
            Color.BLACK, Color.WHITE, Color.rgb(127, 127, 127),
            Color.RED, Color.rgb(255, 128, 0), Color.YELLOW,
            Color.GREEN, Color.CYAN, Color.BLUE,
            Color.rgb(128, 0, 255), Color.MAGENTA, Color.rgb(255, 192, 203),
            Color.rgb(139, 69, 19), Color.rgb(0, 100, 0), Color.rgb(0, 0, 128)
        )

        for (presetColor in presets) {
            val presetView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 8
                }
                setBackgroundColor(presetColor)
                setOnClickListener {
                    Color.colorToHSV(presetColor, hsv)
                    currentAlpha = Color.alpha(presetColor).let { if (it == 0) 255 else it }
                    sliderHue.progress = hsv[0].toInt()
                    sliderSaturation.progress = (hsv[1] * 100).toInt()
                    sliderValue.progress = (hsv[2] * 100).toInt()
                    sliderAlpha.progress = currentAlpha
                    updatePreview()
                }
            }
            presetColors.addView(presetView)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_color_picker_title)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                onColorSelected(Color.HSVToColor(currentAlpha, hsv))
            }
            .setNeutralButton(R.string.pixel_art_palette_add_to) { _, _ ->
                val selectedColor = Color.HSVToColor(currentAlpha, hsv)
                addColorToPalette(selectedColor)
                onColorSelected(selectedColor)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showReturnToMenuConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_return_menu_title)
            .setMessage(R.string.confirm_return_menu_message)
            .setPositiveButton(R.string.confirm_return_menu_yes) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.confirm_return_menu_no, null)
            .show()
    }
}
