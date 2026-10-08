package com.Atom2Universe.app.pixelart

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.ClipData
import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.PixelRect
import com.Atom2Universe.app.pixelart.core.SessionListener
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.pixelart.core.Tool
import com.Atom2Universe.app.pixelart.io.CreateImageDocument
import com.Atom2Universe.app.pixelart.io.DeviceImages
import com.Atom2Universe.app.pixelart.io.ImageExport
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.NamedPalette
import com.Atom2Universe.app.pixelart.io.PaletteFormats
import com.Atom2Universe.app.pixelart.io.ReferenceState
import com.Atom2Universe.app.pixelart.io.SourceLink
import com.Atom2Universe.app.pixelart.ui.BackgroundStyle
import com.Atom2Universe.app.pixelart.ui.EditorViewModel
import com.Atom2Universe.app.pixelart.ui.ExportFormat
import com.Atom2Universe.app.pixelart.ui.ExportRequest
import com.Atom2Universe.app.pixelart.ui.ExportTarget
import com.Atom2Universe.app.pixelart.ui.FramesAdapter
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.LayersAdapter
import com.Atom2Universe.app.pixelart.ui.ColorDock
import com.Atom2Universe.app.pixelart.ui.PaletteActions
import com.Atom2Universe.app.pixelart.ui.PaletteAdapter
import com.Atom2Universe.app.pixelart.ui.PixelEditorView
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.SwatchView
import com.Atom2Universe.app.pixelart.ui.ToolOptionsBar
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.shapeIcon
import com.Atom2Universe.app.pixelart.ui.showBackgroundSheet
import com.Atom2Universe.app.pixelart.ui.showCanvasSizeSheet
import com.Atom2Universe.app.pixelart.ui.showColorPicker
import com.Atom2Universe.app.pixelart.ui.showDurationSheet
import com.Atom2Universe.app.pixelart.ui.showExportSheet
import com.Atom2Universe.app.pixelart.ui.showLayerSheet
import com.Atom2Universe.app.pixelart.ui.showPaletteSheet
import com.Atom2Universe.app.pixelart.ui.showShapeSheet
import com.Atom2Universe.app.pixelart.ui.showSheetImportSheet
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * L'éditeur de pixel art. L'activité ne fait que relier trois choses : la vue de dessin
 * ([PixelEditorView]), la session ([EditorSession], où vit toute la logique) et les panneaux
 * (outils, couleurs, calques, images). Le projet et l'historique vivent dans un [EditorViewModel],
 * si bien qu'une rotation d'écran ne perd rien.
 */
class PixelArtEditorActivity : AppCompatActivity(), PixelEditorView.Host {

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val PREFS = "pixel_studio_prefs"

        /** Au-delà, on n'ose pas dupliquer le dessin en mémoire pour l'exporter à part. */
        private const val SNAPSHOT_LIMIT = 96L * 1024 * 1024

        fun intent(context: Context, projectId: String) =
            Intent(context, PixelArtEditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val vm: EditorViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var view: PixelEditorView
    private lateinit var session: EditorSession
    private lateinit var optionsBar: ToolOptionsBar
    private lateinit var layersAdapter: LayersAdapter
    private lateinit var framesAdapter: FramesAdapter
    private lateinit var paletteAdapter: PaletteAdapter
    private lateinit var colorDock: ColorDock

    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var saveDot: View
    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var txtZoom: TextView
    private lateinit var swatchPrimary: SwatchView
    private lateinit var swatchSecondary: SwatchView
    private lateinit var layersPanel: View
    private lateinit var timelinePanel: View
    private lateinit var btnPlay: ImageButton
    private lateinit var btnOnion: ImageButton
    private lateinit var btnDuration: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var optionsScroll: View
    private val toolButtons = LinkedHashMap<Tool, ImageButton>()

    private var playing = false
    private var playIndex = 0
    private var referenceOpacity = 0.5f
    private var backgroundStyle = BackgroundStyle.CHECKER_DARK
    private var pixelGrid = true
    private var tileGrid = 0

    // ---- Fichiers ----------------------------------------------------------------------------------

    private var pendingExport: ExportRequest? = null
    private var pendingPaletteExport: NamedPalette? = null
    private var pendingSheetImport: Pair<IntArray, Pair<Int, Int>>? = null

    private fun saveAsLauncher(mime: String) =
        registerForActivityResult(CreateImageDocument(mime)) { uri -> if (uri != null) pendingExport?.let { writeExportTo(uri, it) } }

    private val saveAsPng = saveAsLauncher("image/png")
    private val saveAsGif = saveAsLauncher("image/gif")
    private val saveAsJpeg = saveAsLauncher("image/jpeg")
    private val saveAsWebp = saveAsLauncher("image/webp")

    private val importLayerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importLayer(it) } }
    private val importSheetLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importSheet(it) } }
    private val importReferenceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importReference(it) } }
    private val importPaletteLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importPalette(it) } }
    private val paletteFromImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { paletteFromImageFile(it) } }
    private val exportPaletteLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { u -> pendingPaletteExport?.let { p -> writePalette(u, p) } }
    }

    // ==== Cycle de vie ============================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_pixel_art_editor)

        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (id == null) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        vm.open(id)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vm.state.collect { st ->
                    when (st) {
                        EditorViewModel.State.READY -> if (!::session.isInitialized || session !== vm.session) start()
                        EditorViewModel.State.FAILED -> finish()
                        EditorViewModel.State.LOADING -> Unit
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Avant que la galerie ne reprenne la main : elle relit les projets à ce moment-là.
        if (::session.isInitialized) {
            stopPlayback()
            vm.flush()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::session.isInitialized) { session.listener = null; session.fallbackSampler = null }
        super.onDestroy()
    }

    private fun start() {
        session = vm.session ?: return
        val project = vm.project ?: return
        // Une rotation en plein geste : le doigt n'existe plus pour le terminer.
        session.settle()

        view = findViewById(R.id.editor_view)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnSave = findViewById(R.id.btn_save)
        saveDot = findViewById(R.id.save_dot)
        txtTitle = findViewById(R.id.txt_title)
        txtSubtitle = findViewById(R.id.txt_subtitle)
        txtZoom = findViewById(R.id.txt_zoom)
        swatchPrimary = findViewById(R.id.swatch_primary)
        swatchSecondary = findViewById(R.id.swatch_secondary)
        layersPanel = findViewById(R.id.layers_panel)
        timelinePanel = findViewById(R.id.timeline_panel)
        btnPlay = findViewById(R.id.btn_play)
        btnOnion = findViewById(R.id.btn_onion)
        btnDuration = findViewById(R.id.btn_frame_duration)
        optionsContainer = findViewById(R.id.options_container)
        optionsScroll = findViewById(R.id.options_scroll)

        loadPrefs()
        view.host = this
        view.bind(session)
        view.background = backgroundStyle
        view.showPixelGrid = pixelGrid
        view.tileGrid = tileGrid
        view.canvasTransparency = prefs.getFloat("canvas_transparency", 0.5f)

        setupTopBar()
        setupTools()
        setupColors()
        setupLayers()
        setupTimeline()
        loadReference(project.meta.reference)

        optionsBar = ToolOptionsBar(this, optionsContainer, { session }, optionsListener)
        selectTool(session.tool)
        refreshChrome()
        onViewportChanged(view.scale)
        session.listener = sessionListener
        session.fallbackSampler = { x, y -> view.referenceColorAt(x, y) }
    }

    // ==== Préférences =============================================================================

    private fun loadPrefs() {
        val o = session.options
        o.size = prefs.getInt("size", 1)
        o.round = prefs.getBoolean("round", true)
        o.pixelPerfect = prefs.getBoolean("pixel_perfect", true)
        o.opacity = prefs.getInt("opacity", 60)
        o.shape = runCatching { ShapeKind.valueOf(prefs.getString("shape", "RECT")!!) }.getOrDefault(ShapeKind.RECT)
        o.shapeFill = runCatching { ShapeFill.valueOf(prefs.getString("shape_fill", "OUTLINE")!!) }.getOrDefault(ShapeFill.OUTLINE)
        o.shapeSquare = prefs.getBoolean("shape_square", false)
        o.tolerance = prefs.getInt("tolerance", 0)
        o.contiguous = prefs.getBoolean("contiguous", true)
        o.sampleAllLayers = prefs.getBoolean("sample_all", false)
        o.mirrorX = prefs.getBoolean("mirror_x", false)
        o.mirrorY = prefs.getBoolean("mirror_y", false)
        backgroundStyle = runCatching { BackgroundStyle.valueOf(prefs.getString("background", "CHECKER_DARK")!!) }.getOrDefault(BackgroundStyle.CHECKER_DARK)
        pixelGrid = prefs.getBoolean("pixel_grid", true)
        tileGrid = prefs.getInt("tile_grid", 0)
        session.tool = runCatching { Tool.valueOf(prefs.getString("tool", "PENCIL")!!) }.getOrDefault(Tool.PENCIL)
            .takeIf { it != Tool.MOVE } ?: Tool.PENCIL
    }

    private fun savePrefs() {
        val o = session.options
        prefs.edit()
            .putInt("size", o.size).putBoolean("round", o.round).putBoolean("pixel_perfect", o.pixelPerfect)
            .putInt("opacity", o.opacity).putString("shape", o.shape.name).putString("shape_fill", o.shapeFill.name)
            .putBoolean("shape_square", o.shapeSquare).putInt("tolerance", o.tolerance)
            .putBoolean("contiguous", o.contiguous).putBoolean("sample_all", o.sampleAllLayers)
            .putBoolean("mirror_x", o.mirrorX).putBoolean("mirror_y", o.mirrorY)
            .putString("tool", session.tool.name)
            .putString("background", backgroundStyle.name).putBoolean("pixel_grid", pixelGrid).putInt("tile_grid", tileGrid)
            .apply()
    }

    // ==== Écoute de la session ====================================================================

    private val thumbRefresh = Runnable {
        if (!::session.isInitialized) return@Runnable
        layersAdapter.refreshThumb(session.activeLayerId)
        framesAdapter.refreshThumb(session.activeFrameId)
    }

    private val sessionListener = object : SessionListener {
        override fun onPixelsChanged(rect: PixelRect) {
            view.refreshRegion(rect)
            handler.removeCallbacks(thumbRefresh)
            handler.postDelayed(thumbRefresh, 300)
        }

        override fun onDocumentChanged() {
            view.refreshAll()
            layersAdapter.refreshAll()
            framesAdapter.refreshAll()
            refreshChrome()
        }

        override fun onSelectionChanged() {
            view.selectionChanged()
            // Le bouton « Désélectionner » n'existe que tant qu'il y a une sélection.
            if (!view.referenceEditing) optionsBar.selectionChanged()
        }
        override fun onFloatingChanged() = view.floatingChanged()
        override fun onOverlayChanged() = view.overlayChanged()

        override fun onColorPicked(color: Int) {
            vm.palettes.addRecent(color)
            refreshColors()
        }

        override fun onHistoryChanged() {
            refreshChrome()
            vm.scheduleAutosave()
        }

        override fun onActiveChanged() {
            layersAdapter.refreshAll()
            framesAdapter.refreshAll()
            view.refreshAll()
            refreshChrome()
        }
    }

    // ==== Barre du haut ==============================================================================

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { onBack() }
        btnUndo.setOnClickListener { stopPlayback(); session.undo() }
        btnRedo.setOnClickListener { stopPlayback(); session.redo() }
        btnSave.setOnClickListener { onSave() }
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener { showMoreSheet() }
        findViewById<View>(R.id.title_block).setOnClickListener { promptRename() }
        txtZoom.setOnClickListener { view.fit() }
        for (b in listOf(btnUndo, btnRedo, btnSave)) TooltipCompat.setTooltipText(b, b.contentDescription)
    }

    private fun refreshChrome() {
        val project = vm.project ?: return
        val doc = session.doc
        btnUndo.isEnabled = session.history.canUndo || session.floating != null
        btnRedo.isEnabled = session.history.canRedo
        txtTitle.text = project.meta.name
        val link = project.meta.link
        val sb = StringBuilder(getString(R.string.px_subtitle_size, doc.width, doc.height))
        if (doc.frames.size > 1) sb.append(getString(R.string.px_subtitle_frames, session.frameIndex + 1, doc.frames.size))
        if (link != null) sb.append(getString(R.string.px_subtitle_file, link.name))
        txtSubtitle.text = sb.toString()
        saveDot.visibility = if (link != null && session.history.modifiedSinceSave) View.VISIBLE else View.GONE
        refreshColors()
        btnDuration.text = getString(R.string.px_ms, session.activeFrame.durationMs)
        btnOnion.isSelected = view.onionEnabled
        updateShapeToolIcon()
    }

    private fun promptRename() {
        val p = vm.project ?: return
        promptText(R.string.px_rename, p.meta.name, R.string.px_ok) { name ->
            if (name.isNotEmpty()) { vm.rename(name); refreshChrome() }
        }
    }

    // ==== Outils =====================================================================================

    private class ToolDef(val tool: Tool, val icon: Int, val label: Int)

    private val toolDefs = listOf(
        ToolDef(Tool.PENCIL, R.drawable.ic_px_pencil, R.string.px_tool_pencil),
        ToolDef(Tool.BRUSH, R.drawable.ic_px_brush, R.string.px_tool_brush),
        ToolDef(Tool.ERASER, R.drawable.ic_px_eraser, R.string.px_tool_eraser),
        ToolDef(Tool.FILL, R.drawable.ic_px_fill, R.string.px_tool_fill),
        ToolDef(Tool.PICKER, R.drawable.ic_px_picker, R.string.px_tool_picker),
        ToolDef(Tool.SHAPE, R.drawable.ic_px_shape, R.string.px_tool_shape),
        ToolDef(Tool.SELECT_RECT, R.drawable.ic_px_sel_rect, R.string.px_tool_select_rect),
        ToolDef(Tool.SELECT_LASSO, R.drawable.ic_px_sel_lasso, R.string.px_tool_select_lasso),
        ToolDef(Tool.SELECT_WAND, R.drawable.ic_px_sel_wand, R.string.px_tool_select_wand),
        ToolDef(Tool.MOVE, R.drawable.ic_px_move, R.string.px_tool_move),
        ToolDef(Tool.HAND, R.drawable.ic_px_hand, R.string.px_tool_hand),
    )

    private fun setupTools() {
        val container = findViewById<LinearLayout>(R.id.tools_container)
        container.removeAllViews()
        toolButtons.clear()
        for (d in toolDefs) {
            val b = ImageButton(this, null, 0, R.style.PxToolButton).apply {
                setImageResource(d.icon)
                contentDescription = getString(d.label)
                setOnClickListener { selectTool(d.tool) }
                if (d.tool == Tool.SHAPE) setOnLongClickListener { showShapeSheet(session.options.shape) { pickShape(it) }; true }
            }
            TooltipCompat.setTooltipText(b, getString(d.label))
            toolButtons[d.tool] = b
            container.addView(b)
        }
    }

    private fun selectTool(tool: Tool) {
        stopPlayback()
        session.tool = tool
        for ((t, b) in toolButtons) b.isSelected = t == tool
        optionsBar.rebuild()
        savePrefs()
    }

    private fun updateShapeToolIcon() {
        toolButtons[Tool.SHAPE]?.setImageResource(shapeIcon(session.options.shape))
    }

    private fun pickShape(kind: ShapeKind) {
        session.options.shape = kind
        updateShapeToolIcon()
        if (session.tool != Tool.SHAPE) selectTool(Tool.SHAPE) else optionsBar.rebuild()
        savePrefs()
    }

    private val optionsListener = object : ToolOptionsBar.Listener {
        override fun onOptionsChanged() {
            savePrefs()
            view.overlayChanged()
        }

        override fun onPickShape() = showShapeSheet(session.options.shape) { pickShape(it) }

        override fun onContentAction(action: ToolOptionsBar.ContentAction) {
            stopPlayback()
            when (action) {
                ToolOptionsBar.ContentAction.SELECT_ALL -> session.selectAll()
                ToolOptionsBar.ContentAction.DESELECT -> session.deselect()
                ToolOptionsBar.ContentAction.INVERT -> session.invertSelection()
                ToolOptionsBar.ContentAction.COPY -> session.copy()
                ToolOptionsBar.ContentAction.CUT -> session.cut()
                ToolOptionsBar.ContentAction.PASTE -> {
                    val (cx, cy) = view.centerPixel()
                    val clip = com.Atom2Universe.app.pixelart.core.PixelClipboard.data
                    if (clip != null && session.paste(cx - clip.w / 2, cy - clip.h / 2)) selectToolSilently(Tool.MOVE)
                }
                ToolOptionsBar.ContentAction.DELETE -> session.deleteSelection()
                ToolOptionsBar.ContentAction.FILL_SELECTION -> session.fillSelection()
                ToolOptionsBar.ContentAction.FLIP_H -> session.flipSelection(true)
                ToolOptionsBar.ContentAction.FLIP_V -> session.flipSelection(false)
                ToolOptionsBar.ContentAction.ROTATE_CW -> session.rotateSelection(true)
                ToolOptionsBar.ContentAction.ROTATE_CCW -> session.rotateSelection(false)
                ToolOptionsBar.ContentAction.COMMIT -> session.commitFloating()
                ToolOptionsBar.ContentAction.CANCEL -> session.cancelFloating()
            }
        }
    }

    /** La session a déjà changé d'outil (collage) : on ne met à jour que l'affichage. */
    private fun selectToolSilently(tool: Tool) {
        for ((t, b) in toolButtons) b.isSelected = t == tool
        optionsBar.rebuild()
    }

    // ==== Couleurs =====================================================================================

    /** La case de la palette que le réglage de couleur modifie en direct (-1 : aucune). */
    private var linkedIndex = -1
    private var lastPaletteId = ""

    private fun setupColors() {
        val list = findViewById<RecyclerView>(R.id.palette_list)
        paletteAdapter = PaletteAdapter(
            onPick = { index, c ->
                pickFromPalette(c)
                linkedIndex = index
                vm.palettes.addRecent(c)
                refreshColors()
            },
            onMenu = { anchor, index, c -> showSwatchMenu(anchor, index, c) },
            onAdd = { addPrimaryToPalette() },
        )
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.adapter = paletteAdapter
        swatchPrimary.radiusDp = 8f
        swatchSecondary.radiusDp = 8f
        colorDock = findViewById(R.id.color_dock)
        colorDock.bind(dockHost)
        swatchPrimary.setOnClickListener { toggleColorDock(false) }
        swatchSecondary.setOnClickListener { toggleColorDock(true) }
        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val t = session.primary; session.primary = session.secondary; session.secondary = t
            linkedIndex = -1
            refreshColors()
        }
        findViewById<ImageButton>(R.id.btn_palette).setOnClickListener { showPalettes() }
        refreshColors()
        if (prefs.getBoolean("color_dock", false)) colorDock.show(false)
    }

    /** Ce que la carte de réglage lit et modifie de l'éditeur. */
    private val dockHost = object : ColorDock.Host {
        override fun colorOf(secondary: Boolean) = if (secondary) session.secondary else session.primary

        override fun editColor(secondary: Boolean, color: Int) {
            val old = colorOf(secondary)
            if (secondary) session.secondary = color else session.primary = color
            // La case de palette qu'on a touchée suit le réglage (jamais une palette intégrée).
            val pal = vm.palettes.current()
            val i = linkedIndex
            if (!pal.builtIn && i in pal.colors.indices && pal.colors[i] == old) {
                vm.palettes.update(pal.id, pal.colors.toMutableList().also { it[i] = color })
            }
            refreshColors()
        }

        override fun pickColor(secondary: Boolean, color: Int) {
            if (secondary) session.secondary = color else session.primary = color
            linkedIndex = -1
            refreshColors()
        }

        override fun recentColors() = vm.palettes.recent()
        override fun hideRequested() = hideColorDock()
        override fun closed(changed: List<Int>) { for (c in changed) vm.palettes.addRecent(c) }
    }

    private fun showColorDock(secondary: Boolean) {
        colorDock.show(secondary)
        prefs.edit().putBoolean("color_dock", true).apply()
    }

    private fun hideColorDock() {
        colorDock.hide()
        prefs.edit().putBoolean("color_dock", false).apply()
    }

    /** Toucher la pastille d'une couleur ouvre le réglage sur elle ; la retoucher le referme. */
    private fun toggleColorDock(secondary: Boolean) {
        if (colorDock.isOpen && colorDock.editingSecondary == secondary) hideColorDock() else showColorDock(secondary)
    }

    /** Une case de la palette devient la couleur réglée (la secondaire si c'est elle que la carte règle). */
    private fun pickFromPalette(c: Int) {
        if (colorDock.isOpen && colorDock.editingSecondary) session.secondary = c else session.primary = c
    }

    private fun refreshColors() {
        swatchPrimary.color = session.primary
        swatchSecondary.color = session.secondary
        val pal = vm.palettes.current()
        if (pal.id != lastPaletteId) { lastPaletteId = pal.id; linkedIndex = -1 }
        paletteAdapter.submit(pal.colors)
        paletteAdapter.ringIndex =
            if (linkedIndex in pal.colors.indices && pal.colors[linkedIndex] == session.primary) linkedIndex
            else pal.colors.indexOf(session.primary)
        if (::colorDock.isInitialized) colorDock.refresh()
    }

    /** La palette qu'on peut modifier : la courante, ou sa copie dans « Ma palette » si elle est intégrée. */
    private fun editablePalette(): NamedPalette {
        val cur = vm.palettes.current()
        if (!cur.builtIn) return cur
        val copy = vm.palettes.create(getString(R.string.px_palette_mine), cur.colors)
        vm.palettes.currentId = copy.id
        lastPaletteId = copy.id
        return copy
    }

    /** Appui long sur une case : les actions qui la concernent. */
    private fun showSwatchMenu(anchor: View, index: Int, color: Int) {
        val menu = com.Atom2Universe.app.util.ImmersiveSupportPopupMenu(this, anchor)
        val edit = menu.menu.add(getString(R.string.px_color_edit))
        val secondary = menu.menu.add(getString(R.string.px_color_as_secondary))
        val duplicate = menu.menu.add(getString(R.string.px_duplicate))
        val delete = menu.menu.add(getString(R.string.px_delete))
        menu.setOnMenuItemClickListener { item ->
            when (item) {
                edit -> {
                    session.primary = color
                    linkedIndex = index
                    vm.palettes.addRecent(color)
                    showColorDock(false)
                    refreshColors()
                }
                secondary -> { session.secondary = color; refreshColors() }
                duplicate -> {
                    val pal = editablePalette()
                    val list = pal.colors.toMutableList()
                    list.add(index + 1, color)
                    vm.palettes.update(pal.id, list)
                    session.primary = color
                    linkedIndex = index + 1
                    refreshColors()
                    findViewById<RecyclerView>(R.id.palette_list).scrollToPosition(index + 1)
                }
                delete -> {
                    val pal = editablePalette()
                    val list = pal.colors.toMutableList()
                    if (index in list.indices) list.removeAt(index)
                    vm.palettes.update(pal.id, list)
                    linkedIndex = -1
                    refreshColors()
                }
            }
            true
        }
        menu.show()
    }

    private fun addPrimaryToPalette() {
        val c = session.primary
        val pal = editablePalette()
        val at = pal.colors.indexOf(c)
        if (at >= 0) {
            linkedIndex = at
        } else {
            vm.palettes.update(pal.id, pal.colors + c)
            linkedIndex = pal.colors.size
        }
        refreshColors()
        findViewById<RecyclerView>(R.id.palette_list).scrollToPosition(linkedIndex)
    }

    private fun showPalettes() {
        val store = vm.palettes
        showPaletteSheet(store.all(), store.currentId, PaletteActions(
            onSelect = { store.currentId = it.id; refreshColors() },
            onNew = {
                promptText(R.string.px_palette_new, "", R.string.px_create) { name ->
                    if (name.isNotEmpty()) { store.currentId = store.create(name, listOf(session.primary)).id; refreshColors() }
                }
            },
            onFromImage = {
                promptText(R.string.px_palette_from_image, getString(R.string.px_palette_mine), R.string.px_create) { name ->
                    val flat = Compositor.compositeFrame(session.doc, session.activeFrameId)
                    val colors = PaletteFormats.uniqueColors(flat, 256)
                    if (colors.isNotEmpty() && name.isNotEmpty()) { store.currentId = store.create(name, colors).id; refreshColors() }
                }
            },
            onImport = { importPaletteLauncher.launch("*/*") },
            onExport = { p -> pendingPaletteExport = p; exportPaletteLauncher.launch("${p.title(this)}.hex") },
            onRename = { p ->
                promptText(R.string.px_rename, p.name, R.string.px_ok) { name -> if (name.isNotEmpty()) { store.rename(p.id, name); refreshColors() } }
            },
            onDelete = { p -> confirm(R.string.px_palette_delete, p.name, R.string.px_delete, true) { store.delete(p.id); refreshColors() } },
        ))
    }

    private fun importPalette(uri: Uri) {
        lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext emptyList()
                    if (bytes.size > 8 && bytes[1].toInt() == 'P'.code && bytes[2].toInt() == 'N'.code) {
                        val d = DeviceImages.decode(this@PixelArtEditorActivity, uri)
                        if (d != null) PaletteFormats.uniqueColors(d.pixels, 256) else emptyList()
                    } else PaletteFormats.parse(String(bytes, Charsets.UTF_8))
                } catch (e: Exception) { emptyList() }
            }
            if (colors.isEmpty()) return@launch
            val base = (DeviceImages.displayName(this@PixelArtEditorActivity, uri) ?: getString(R.string.px_palette_mine)).substringBeforeLast('.')
            promptText(R.string.px_palette_import, base, R.string.px_create) { name ->
                if (name.isNotEmpty()) { vm.palettes.currentId = vm.palettes.create(name, colors).id; refreshColors() }
            }
        }
    }

    private fun paletteFromImageFile(uri: Uri) = importPalette(uri)

    private fun writePalette(uri: Uri, p: NamedPalette) {
        lifecycleScope.launch(Dispatchers.IO) {
            try { contentResolver.openOutputStream(uri, "wt")?.use { it.write(PaletteFormats.toHexLines(p.colors).toByteArray()) } } catch (e: Exception) { }
        }
    }

    // ==== Calques ======================================================================================

    private fun setupLayers() {
        layersPanel.visibility = View.GONE
        val list = findViewById<RecyclerView>(R.id.layers_list)
        layersAdapter = LayersAdapter(
            session = { session },
            onSelect = { session.selectLayer(it) },
            onToggleVisible = { i -> session.editLayer(session.doc.layers[i].id) { it.visible = !it.visible } },
            onToggleLock = { i -> session.editLayer(session.doc.layers[i].id) { it.locked = !it.locked } },
            onOpenProperties = { i -> showLayerSheet(session, i) { refreshChrome() } },
            onReorder = { from, to -> session.moveLayer(from, to) },
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = layersAdapter
        // Pas de fondu à chaque mise à jour d'une vignette : la liste se redessine sobrement.
        (list.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        layersAdapter.touchHelper().attachToRecyclerView(list)
        findViewById<View>(R.id.btn_layers).setOnClickListener {
            layersPanel.visibility = if (layersPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            it.isSelected = layersPanel.visibility == View.VISIBLE
        }
        findViewById<View>(R.id.btn_layer_add).setOnClickListener {
            session.addLayer(getString(R.string.px_layer_name, session.doc.nextLayerId))
        }
        findViewById<View>(R.id.btn_layer_duplicate).setOnClickListener {
            session.duplicateLayer(getString(R.string.px_layer_copy_name, session.activeLayer.name))
        }
        findViewById<View>(R.id.btn_layer_merge).setOnClickListener { session.mergeLayerDown() }
        findViewById<View>(R.id.btn_layer_delete).setOnClickListener {
            if (session.doc.layers.size > 1) {
                confirm(R.string.px_layer_delete, session.activeLayer.name, R.string.px_delete, true) { session.deleteLayer() }
            }
        }
    }

    // ==== Images (animation) =========================================================================

    private fun setupTimeline() {
        timelinePanel.visibility = if (session.doc.frames.size > 1) View.VISIBLE else View.GONE
        val list = findViewById<RecyclerView>(R.id.frames_list)
        framesAdapter = FramesAdapter(session = { session }, onSelect = { i ->
            if (playing) stopPlayback()
            if (i == session.frameIndex) showDurationDialog() else session.selectFrame(i)
        })
        framesAdapter.onMoved = { from, to -> session.moveFrame(from, to) }
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.adapter = framesAdapter
        // Pendant la lecture l'image active change dix fois par seconde : un fondu à chaque fois clignoterait.
        (list.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        framesAdapter.touchHelper().attachToRecyclerView(list)

        findViewById<View>(R.id.btn_timeline).setOnClickListener {
            val show = timelinePanel.visibility != View.VISIBLE
            timelinePanel.visibility = if (show) View.VISIBLE else View.GONE
            it.isSelected = show
            if (!show) stopPlayback()
        }
        btnPlay.setOnClickListener { if (playing) stopPlayback() else startPlayback() }
        btnOnion.setOnClickListener { view.onionEnabled = !view.onionEnabled; btnOnion.isSelected = view.onionEnabled }
        findViewById<View>(R.id.btn_frame_add).setOnClickListener { stopPlayback(); session.addFrame(copyCurrent = false) }
        findViewById<View>(R.id.btn_frame_duplicate).setOnClickListener { stopPlayback(); session.addFrame(copyCurrent = true) }
        findViewById<View>(R.id.btn_frame_delete).setOnClickListener {
            if (session.doc.frames.size > 1) {
                stopPlayback()
                confirm(R.string.px_frame_delete, getString(R.string.px_frame_delete_confirm, session.frameIndex + 1), R.string.px_delete, true) { session.deleteFrame() }
            }
        }
        btnDuration.setOnClickListener { showDurationDialog() }
        for (id in listOf(R.id.btn_play, R.id.btn_onion, R.id.btn_frame_add, R.id.btn_frame_duplicate, R.id.btn_frame_delete, R.id.btn_layers, R.id.btn_timeline, R.id.btn_palette)) {
            findViewById<View>(id).let { TooltipCompat.setTooltipText(it, it.contentDescription) }
        }
    }

    private fun showDurationDialog() {
        showDurationSheet(session.activeFrame.durationMs, session.doc.frames.size) { ms, all ->
            if (all) session.setAllFrameDurations(ms) else session.setFrameDuration(session.activeFrameId, ms)
        }
    }

    private fun startPlayback() {
        session.settle()
        val doc = session.doc
        if (doc.frames.size < 2) return
        playing = true
        playIndex = session.frameIndex
        btnPlay.setImageResource(R.drawable.ic_px_pause)
        handler.post(playTick)
    }

    private val playTick = object : Runnable {
        override fun run() {
            if (!playing) return
            val doc = session.doc
            if (playIndex >= doc.frames.size) playIndex = 0
            val f = doc.frames[playIndex]
            view.showFrame(f.id)
            framesAdapter.playing = playIndex
            playIndex = (playIndex + 1) % doc.frames.size
            handler.postDelayed(this, f.durationMs.toLong())
        }
    }

    private fun stopPlayback() {
        if (!playing) return
        playing = false
        handler.removeCallbacks(playTick)
        view.showFrame(null)
        framesAdapter.playing = -1
        btnPlay.setImageResource(R.drawable.ic_px_play)
    }

    override fun onTouchWhilePlaying() = stopPlayback()

    // ==== Vue =======================================================================================

    override fun onViewportChanged(scale: Float) {
        txtZoom.text = getString(R.string.px_zoom, if (scale >= 10f) scale.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", scale).removeSuffix(".0"))
    }

    override fun onReferenceMoved(scale: Float, x: Float, y: Float) {
        vm.project?.meta?.reference = ReferenceState(scale, x, y, referenceOpacity, view.referenceVisible)
        vm.project?.doc?.markMetaDirty()
        vm.scheduleAutosave()
    }

    // ==== Menu =======================================================================================

    private fun showMoreSheet() {
        val meta = vm.project?.meta ?: return
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_px_export, getString(R.string.px_export)) { openExport() })
        if (meta.link != null) {
            items.add(SheetItem(R.drawable.ic_px_link, getString(R.string.px_unlink, meta.link!!.name)) { vm.setLink(null); refreshChrome() })
        }
        items.add(SheetItem(R.drawable.ic_px_rename, getString(R.string.px_rename)) { promptRename() })
        items.add(SheetItem(R.drawable.ic_px_crop, getString(R.string.px_canvas_title)) {
            showCanvasSizeSheet(session) { view.fit() }
        })
        items.add(SheetItem(R.drawable.ic_px_checker, getString(R.string.px_bg_title)) { showBackground() })
        items.add(SheetItem(R.drawable.ic_px_image, getString(R.string.px_reference)) { showReferenceSheet() })
        items.add(SheetItem(R.drawable.ic_px_import, getString(R.string.px_import_layer)) { importLayerLauncher.launch("image/*") })
        items.add(SheetItem(R.drawable.ic_px_grid, getString(R.string.px_import_sheet)) { importSheetLauncher.launch("image/*") })
        actionSheet(null, items).show()
    }

    private fun showBackground() {
        showBackgroundSheet(backgroundStyle, pixelGrid, tileGrid) { st, pg, tg ->
            backgroundStyle = st; pixelGrid = pg; tileGrid = tg
            view.background = st; view.showPixelGrid = pg; view.tileGrid = tg
            savePrefs()
        }
    }

    // ==== Enregistrer / exporter ====================================================================

    /** « Enregistrer » : écrase le fichier d'origine ; sans fichier lié, propose « Enregistrer sous ». */
    private fun onSave() {
        stopPlayback()
        session.settle()
        val link = vm.project?.meta?.link
        if (link != null && link.writable) overwriteLinked(link) else openExport()
    }

    private fun overwriteLinked(link: SourceLink, onSuccess: () -> Unit = {}) {
        val doc = session.doc
        val frameIndex = session.frameIndex
        lifecycleScope.launch {
            val snapshot = if (doc.pixelBytes() < SNAPSHOT_LIMIT) doc.copy() else doc
            val ok = withContext(Dispatchers.Default) {
                val bytes = DeviceImages.encode(snapshot, link.format, link.scale, frameIndex, ImageExport.GifOptions(scale = link.scale))
                DeviceImages.overwrite(this@PixelArtEditorActivity, Uri.parse(link.uri), bytes)
            }
            if (ok) {
                session.history.markSaved()
                refreshChrome()
                Toast.makeText(this@PixelArtEditorActivity, getString(R.string.px_saved_to, link.name), Toast.LENGTH_SHORT).show()
                onSuccess()
            } else {
                confirm(R.string.px_save_failed_title, getString(R.string.px_save_failed, link.name), R.string.px_save_as) { openExport() }
            }
        }
    }

    private fun openExport() {
        stopPlayback()
        session.settle()
        val link = vm.project?.meta?.link
        val initial = when (link?.format) {
            ImageFormat.GIF -> ExportFormat.GIF
            ImageFormat.JPEG -> ExportFormat.JPEG
            ImageFormat.WEBP -> ExportFormat.WEBP
            else -> if (session.doc.frames.size > 1) ExportFormat.GIF else ExportFormat.PNG
        }
        val doc = session.doc
        val defaultScale = link?.scale ?: (if (maxOf(doc.width, doc.height) <= 64) 8 else 1)
        showExportSheet(
            doc, initial, defaultScale,
            pickBackground = { current, done ->
                showColorPicker(current ?: 0xFFFFFFFF.toInt(), vm.palettes.recent(), vm.palettes.current().colors) { c -> done(c) }
            },
            onExport = { req -> runExport(req) },
        )
    }

    private fun baseName(): String =
        (vm.project?.meta?.name ?: "pixel-art").replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "pixel-art" }

    private fun runExport(req: ExportRequest) {
        val name = if (req.format == ExportFormat.SHEET) "${baseName()}_sheet" else baseName()
        val ext = req.imageFormat.extension
        when (req.target) {
            ExportTarget.FILE -> {
                pendingExport = req
                val file = "$name.$ext"
                when (req.imageFormat) {
                    ImageFormat.PNG -> saveAsPng.launch(file)
                    ImageFormat.GIF -> saveAsGif.launch(file)
                    ImageFormat.JPEG -> saveAsJpeg.launch(file)
                    ImageFormat.WEBP -> saveAsWebp.launch(file)
                }
            }
            ExportTarget.GALLERY -> lifecycleScope.launch {
                val bytes = encode(req) ?: return@launch
                val uri = withContext(Dispatchers.IO) { DeviceImages.saveToGallery(this@PixelArtEditorActivity, bytes, "$name.$ext", req.imageFormat) }
                toastResult(uri != null, R.string.px_saved_gallery)
            }
            ExportTarget.SHARE -> lifecycleScope.launch {
                val bytes = encode(req) ?: return@launch
                val intent = withContext(Dispatchers.IO) { DeviceImages.shareIntent(this@PixelArtEditorActivity, bytes, "$name.$ext", req.imageFormat) }
                startActivity(Intent.createChooser(intent, getString(R.string.px_share)))
            }
        }
    }

    private fun toastResult(ok: Boolean, okText: Int) {
        Toast.makeText(this, if (ok) okText else R.string.px_export_failed, Toast.LENGTH_SHORT).show()
    }

    /** Fabrique les octets de l'export sur un fil de calcul, à partir d'une copie du dessin. */
    private suspend fun encode(req: ExportRequest): ByteArray? {
        session.settle()
        val doc = session.doc
        val frameIndex = session.frameIndex
        val snapshot = if (doc.pixelBytes() < SNAPSHOT_LIMIT) doc.copy() else doc
        return try {
            withContext(Dispatchers.Default) {
                if (req.format == ExportFormat.SHEET) ImageExport.encodeSheetPng(snapshot, req.sheetColumns, 0, req.scale)
                else DeviceImages.encode(snapshot, req.imageFormat, req.scale, frameIndex, req.gif)
            }
        } catch (e: Throwable) {
            Toast.makeText(this, R.string.px_export_failed, Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun writeExportTo(uri: Uri, req: ExportRequest) {
        val writable = DeviceImages.keepAccess(this, uri)
        lifecycleScope.launch {
            val bytes = encode(req) ?: return@launch
            val ok = withContext(Dispatchers.IO) { DeviceImages.overwrite(this@PixelArtEditorActivity, uri, bytes) }
            if (!ok) { Toast.makeText(this@PixelArtEditorActivity, R.string.px_export_failed, Toast.LENGTH_SHORT).show(); return@launch }
            val fileName = DeviceImages.displayName(this@PixelArtEditorActivity, uri) ?: "${baseName()}.${req.imageFormat.extension}"
            if (req.format != ExportFormat.SHEET) {
                // Le fichier devient « l'image qu'on édite » : les prochains Enregistrer l'écrasent.
                vm.setLink(SourceLink(uri.toString(), fileName, req.imageFormat, req.scale, writable))
                session.history.markSaved()
                refreshChrome()
            }
            Toast.makeText(this@PixelArtEditorActivity, getString(R.string.px_saved_to, fileName), Toast.LENGTH_SHORT).show()
        }
    }

    /** Sortie : si le fichier lié a des changements non enregistrés, on demande quoi en faire. */
    private fun onBack() {
        when {
            layersPanel.visibility == View.VISIBLE -> {
                layersPanel.visibility = View.GONE
                findViewById<View>(R.id.btn_layers).isSelected = false
            }
            playing -> stopPlayback()
            view.referenceEditing -> stopReferenceAdjust()
            session.floating != null -> session.commitFloating()
            else -> {
                session.settle()
                val link = vm.project?.meta?.link
                if (link != null && link.writable && session.history.modifiedSinceSave) {
                    com.Atom2Universe.app.util.ImmersiveMaterialAlertDialogBuilder(this)
                        .setTitle(R.string.px_unsaved_title)
                        .setMessage(getString(R.string.px_unsaved_message, link.name))
                        .setPositiveButton(R.string.px_save) { _, _ -> overwriteLinked(link) { finish() } }
                        .setNegativeButton(R.string.px_dont_save) { _, _ ->
                            // Le brouillon du projet s'enregistre tout seul : il faut le ramener à l'état enregistré.
                            session.revertToSaved()
                            vm.flush()
                            finish()
                        }
                        .setNeutralButton(R.string.px_cancel, null)
                        .show()
                } else finish()
            }
        }
    }

    // ==== Importer ====================================================================================

    private fun importLayer(uri: Uri) {
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { DeviceImages.decode(this@PixelArtEditorActivity, uri) } ?: return@launch
            val name = (DeviceImages.displayName(this@PixelArtEditorActivity, uri) ?: getString(R.string.px_layer_name, session.doc.nextLayerId)).substringBeforeLast('.')
            session.addLayer(name)
            val clip = ClipData(d.pixels, ByteArray(d.pixels.size) { 1 }, d.width, d.height)
            if (session.paste((session.doc.width - d.width) / 2, (session.doc.height - d.height) / 2, clip)) selectToolSilently(Tool.MOVE)
        }
    }

    private fun importSheet(uri: Uri) {
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { DeviceImages.decode(this@PixelArtEditorActivity, uri) } ?: return@launch
            showSheetImportSheet(d.width, d.height) { cols, rows ->
                lifecycleScope.launch {
                    val cells = ImageExport.splitSheet(d.pixels, d.width, d.height, cols, rows)
                    if (cells.isEmpty()) return@launch
                    val name = (DeviceImages.displayName(this@PixelArtEditorActivity, uri) ?: getString(R.string.px_untitled)).substringBeforeLast('.')
                    vm.flush()
                    val created = withContext(Dispatchers.IO) { vm.store.createFromFrames(name, d.width / cols, d.height / rows, cells, getString(R.string.px_layer_name, 1)) }
                    startActivity(intent(this@PixelArtEditorActivity, created.meta.id))
                    finish()
                }
            }
        }
    }

    // ==== Image de référence ==============================================================================

    private fun loadReference(state: ReferenceState?) {
        val p = vm.project ?: return
        if (!p.meta.hasReference) return
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeFile(vm.store.referenceFile(p.meta.id).path, android.graphics.BitmapFactory.Options().apply { inScaled = false })
            } ?: return@launch
            referenceOpacity = state?.opacity ?: 0.5f
            view.setReference(bmp, state?.scale ?: 1f, state?.x ?: 0f, state?.y ?: 0f)
            view.referenceOpacity = referenceOpacity
            view.referenceVisible = state?.visible ?: true
        }
    }

    private fun importReference(uri: Uri) {
        val p = vm.project ?: return
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) {
                DeviceImages.decode(this@PixelArtEditorActivity, uri)?.also { vm.store.saveReference(p.meta.id, it.pixels, it.width, it.height) }
            }
            if (d == null) { Toast.makeText(this@PixelArtEditorActivity, R.string.px_open_failed, Toast.LENGTH_SHORT).show(); return@launch }
            val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888).also { it.setPixels(d.pixels, 0, d.width, 0, 0, d.width, d.height) }
            p.meta.hasReference = true
            // Comme dans l'ancien module : la référence couvre la toile, sous elle, et on passe tout de
            // suite en positionnement (glisser et pincer déplacent l'image, la toile ne bouge pas).
            referenceOpacity = 1f
            view.setReference(bmp)
            view.referenceVisible = true
            view.referenceOpacity = 1f
            view.fitReference()
            startReferenceAdjust()
        }
    }

    private fun showReferenceSheet() {
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_px_image, getString(R.string.px_reference_choose)) { importReferenceLauncher.launch("image/*") })
        if (view.hasReference) {
            items.add(SheetItem(if (view.referenceVisible) R.drawable.ic_px_eye_off else R.drawable.ic_px_eye,
                getString(if (view.referenceVisible) R.string.px_reference_hide else R.string.px_reference_show)) {
                view.referenceVisible = !view.referenceVisible
                onReferenceMoved(view.referenceScale, view.referenceX, view.referenceY)
            })
            items.add(SheetItem(R.drawable.ic_px_move, getString(R.string.px_reference_adjust)) { startReferenceAdjust() })
            items.add(SheetItem(R.drawable.ic_px_crop, getString(R.string.px_reference_fit)) { view.fitReference() })
            items.add(SheetItem(R.drawable.ic_px_tune, getString(R.string.px_reference_opacity)) { showReferenceOpacity() })
            items.add(SheetItem(R.drawable.ic_px_delete, getString(R.string.px_reference_remove), destructive = true) {
                view.setReference(null)
                vm.project?.let { p -> p.meta.hasReference = false; p.meta.reference = null; p.doc.markMetaDirty(); vm.store.deleteReference(p.meta.id) }
                vm.scheduleAutosave()
            })
        }
        actionSheet(getString(R.string.px_reference), items).show()
    }

    private fun showReferenceOpacity() {
        bottomSheet(getString(R.string.px_reference_opacity)) { root, _ ->
            root.addView(LabeledSlider(this, getString(R.string.px_opt_opacity), 5, 100, (referenceOpacity * 100).roundToInt(), { getString(R.string.px_opt_percent, it) }) {
                referenceOpacity = it / 100f
                view.referenceOpacity = referenceOpacity
                onReferenceMoved(view.referenceScale, view.referenceX, view.referenceY)
            })
            // La référence est sous la toile : plus le fond de la toile est transparent, mieux on la voit à travers.
            root.addView(LabeledSlider(this, getString(R.string.px_canvas_transparency), 0, 100, (view.canvasTransparency * 100).roundToInt(), { getString(R.string.px_opt_percent, it) }) {
                view.canvasTransparency = it / 100f
                prefs.edit().putFloat("canvas_transparency", it / 100f).apply()
            }.apply { setPadding(0, dp(12), 0, 0) })
        }.show()
    }

    private fun startReferenceAdjust() {
        view.referenceEditing = true
        optionsContainer.removeAllViews()
        optionsContainer.addView(chip(getString(R.string.px_reference_done), R.drawable.ic_px_check) { stopReferenceAdjust() })
        optionsScroll.visibility = View.VISIBLE
    }

    private fun stopReferenceAdjust() {
        view.referenceEditing = false
        optionsBar.rebuild()
        view.invalidate()
    }
}
