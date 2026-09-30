package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.io.NamedPalette
import com.Atom2Universe.app.pixelart.io.PaletteFormats
import com.Atom2Universe.app.pixelart.io.PaletteStore
import com.Atom2Universe.app.pixelart.ui.ColorDock
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.PaletteActions
import com.Atom2Universe.app.pixelart.ui.PaletteAdapter
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.SwatchView
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.divider
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.showPaletteSheet
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * L'éditeur du canvas infini, sur le modèle du pixel art : barre d'outils et barre de couleurs en
 * bas (palettes partagées avec le pixel art), réglages de l'outil dans une barre flottante en haut,
 * pastille de niveau en bas à gauche.
 */
class ZoomCanvasEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ID = "zoom_canvas_id"
        private const val PREFS = "zoom_canvas_prefs"
        /** Côté maximal d'une image importée (pixels) : au-delà, elle est réduite à l'import. */
        private const val MAX_IMAGE_SIDE = 2048

        fun intent(context: Context, id: String) = Intent(context, ZoomCanvasEditorActivity::class.java).putExtra(EXTRA_ID, id)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val vm: ZoomCanvasViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val palettes by lazy { PaletteStore(this) }

    private lateinit var canvasView: ZoomCanvasView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var levelText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var message: TextView
    private lateinit var optionsScroll: View
    private lateinit var options: LinearLayout
    private lateinit var swatchPrimary: SwatchView
    private lateinit var swatchSecondary: SwatchView
    private lateinit var colorDock: ColorDock
    private lateinit var paletteAdapter: PaletteAdapter
    private val toolButtons = HashMap<ZoomCanvasView.Tool, ImageButton>()

    private var primary = 0xFF1E1E24.toInt()
    private var secondary = 0xFFFFFFFF.toInt()
    /** Opacité du crayon, en pourcentage. */
    private var penOpacity = 100
    private var linkedIndex = -1
    private var lastPaletteId = ""
    private var shownLevel = Long.MIN_VALUE
    private val hideMessage = Runnable { message.visibility = View.GONE }

    /** Les images du projet déjà décodées (le reste se charge en arrière-plan). */
    private val bitmaps = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loading = HashSet<String>()

    private val importImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importImageFrom(it) } }
    private val importPaletteLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importPalette(it) } }
    private var pendingPaletteExport: NamedPalette? = null
    private val exportPaletteLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val p = pendingPaletteExport
        if (uri != null && p != null) writePalette(uri, p)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_zoom_canvas_editor)

        canvasView = findViewById(R.id.zc_canvas)
        title = findViewById(R.id.zc_title)
        subtitle = findViewById(R.id.zc_subtitle)
        undoBtn = findViewById(R.id.zc_btn_undo)
        redoBtn = findViewById(R.id.zc_btn_redo)
        levelText = findViewById(R.id.zc_level_text)
        levelBar = findViewById(R.id.zc_level_bar)
        message = findViewById(R.id.zc_message)
        optionsScroll = findViewById(R.id.zc_options_scroll)
        options = findViewById(R.id.zc_options)
        swatchPrimary = findViewById(R.id.zc_swatch_primary)
        swatchSecondary = findViewById(R.id.zc_swatch_secondary)

        findViewById<View>(R.id.zc_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.zc_title_block).setOnClickListener { askRename() }
        findViewById<View>(R.id.zc_btn_more).setOnClickListener { showMenu() }
        findViewById<View>(R.id.zc_level_pill).setOnClickListener { showLayers() }
        undoBtn.setOnClickListener { if (vm.project?.scene?.undo() == true) drawingChanged() }
        redoBtn.setOnClickListener { if (vm.project?.scene?.redo() == true) drawingChanged() }

        loadPrefs()
        canvasView.listener = canvasListener
        canvasView.imageProvider = ::bitmapFor
        setupTools()
        setupColors()

        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) { finish(); return }
        vm.thumbnailProvider = { canvasView.thumbnail() }
        vm.open(id)
        lifecycleScope.launch {
            vm.state.collect { st ->
                when (st) {
                    ZoomCanvasViewModel.State.READY -> bind()
                    ZoomCanvasViewModel.State.FAILED -> {
                        Toast.makeText(this@ZoomCanvasEditorActivity, R.string.zc_open_failed, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    ZoomCanvasViewModel.State.LOADING -> Unit
                }
            }
        }
    }

    private fun bind() {
        val p = vm.project ?: return
        canvasView.scene = p.scene
        title.text = p.meta.name
        subtitle.text = getString(R.string.zc_ratio_value, p.scene.ratio.toInt())
        refreshChrome()
    }

    /** Une fenêtre (menu, feuille, dialogue) vient de se fermer : on recache les barres système. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBarsVisibility()
    }

    override fun onPause() {
        vm.flush()
        savePrefs()
        super.onPause()
    }

    override fun onDestroy() {
        vm.thumbnailProvider = null
        super.onDestroy()
    }

    private fun loadPrefs() {
        primary = prefs.getInt("primary", primary)
        secondary = prefs.getInt("secondary", secondary)
        penOpacity = prefs.getInt("pen_opacity", 100)
        canvasView.penSize = prefs.getInt("pen_size", 6).toFloat()
        canvasView.eraserSize = prefs.getInt("eraser_size", 24).toFloat()
        applyPenColor()
    }

    private fun savePrefs() {
        prefs.edit()
            .putInt("primary", primary)
            .putInt("secondary", secondary)
            .putInt("pen_opacity", penOpacity)
            .putInt("pen_size", canvasView.penSize.toInt())
            .putInt("eraser_size", canvasView.eraserSize.toInt())
            .putString("tool", canvasView.tool.name)
            .apply()
    }

    /** Le crayon trace la couleur principale, avec l'opacité réglée. */
    private fun applyPenColor() {
        val a = ((primary ushr 24) * penOpacity / 100).coerceIn(0, 255)
        canvasView.color = (a shl 24) or (primary and 0xFFFFFF)
    }

    // ---- Retours de la toile ---------------------------------------------------------------

    private val canvasListener = object : ZoomCanvasView.Listener {
        override fun onDrawingChanged() = drawingChanged()
        override fun onViewMoved() = viewMoved()
        override fun onNothingToMove() = showMessage(getString(R.string.zc_no_layer_below))
        override fun onImageSelectionChanged() = rebuildOptions()
    }

    private fun drawingChanged() {
        canvasView.invalidate()
        refreshChrome()
        if (canvasView.tool == ZoomCanvasView.Tool.SELECT) rebuildOptions()
        vm.scheduleSave(contentChanged = true)
    }

    private fun viewMoved() {
        refreshLevel()
        vm.scheduleSave(contentChanged = false)
    }

    private fun refreshChrome() {
        val s = vm.project?.scene ?: return
        undoBtn.isEnabled = s.canUndo
        undoBtn.alpha = if (s.canUndo) 1f else 0.35f
        redoBtn.isEnabled = s.canRedo
        redoBtn.alpha = if (s.canRedo) 1f else 0.35f
        refreshLevel()
    }

    /** La pastille : numéro de la couche de travail et avancée du zoom dans celle-ci. */
    private fun refreshLevel() {
        val s = vm.project?.scene ?: return
        if (s.depth != shownLevel) {
            shownLevel = s.depth
            levelText.text = getString(R.string.zc_level, s.depth.toString())
        }
        levelBar.progress = (s.progress() * 1000).toInt()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        message.removeCallbacks(hideMessage)
        message.postDelayed(hideMessage, 2600)
    }

    // ---- Outils ------------------------------------------------------------------------------

    private class ToolDef(val tool: ZoomCanvasView.Tool, val icon: Int, val label: Int)

    private val toolDefs = listOf(
        ToolDef(ZoomCanvasView.Tool.PEN, R.drawable.ic_px_pencil, R.string.zc_tool_pen),
        ToolDef(ZoomCanvasView.Tool.ERASER, R.drawable.ic_px_eraser, R.string.zc_tool_eraser),
        ToolDef(ZoomCanvasView.Tool.SELECT, R.drawable.ic_px_move, R.string.zc_tool_select),
        ToolDef(ZoomCanvasView.Tool.HAND, R.drawable.ic_px_hand, R.string.zc_tool_hand),
        ToolDef(ZoomCanvasView.Tool.MOVE_LAYER, R.drawable.ic_px_layers, R.string.zc_tool_move_layer),
    )

    private fun setupTools() {
        val bar = findViewById<LinearLayout>(R.id.zc_tools)
        for (d in toolDefs) {
            val b = ImageButton(this, null, 0, R.style.PxToolButton).apply {
                setImageResource(d.icon)
                contentDescription = getString(d.label)
                setOnClickListener { selectTool(d.tool) }
            }
            TooltipCompat.setTooltipText(b, getString(d.label))
            toolButtons[d.tool] = b
            bar.addView(b)
        }
        bar.addView(divider())
        val importBtn = ImageButton(this, null, 0, R.style.PxToolButton).apply {
            setImageResource(R.drawable.ic_px_image)
            contentDescription = getString(R.string.zc_import_image)
            setOnClickListener { importImage.launch("image/*") }
        }
        TooltipCompat.setTooltipText(importBtn, getString(R.string.zc_import_image))
        bar.addView(importBtn)
        val saved = prefs.getString("tool", null)?.let { n -> ZoomCanvasView.Tool.values().firstOrNull { it.name == n } }
        selectTool(if (saved == ZoomCanvasView.Tool.MOVE_LAYER || saved == null) ZoomCanvasView.Tool.PEN else saved, quiet = true)
    }

    private fun selectTool(t: ZoomCanvasView.Tool, quiet: Boolean = false) {
        canvasView.tool = t
        for ((k, b) in toolButtons) b.isSelected = k == t
        rebuildOptions()
        if (!quiet) {
            when (t) {
                ZoomCanvasView.Tool.MOVE_LAYER -> showMessage(getString(R.string.zc_move_layer_hint))
                ZoomCanvasView.Tool.SELECT -> if (canvasView.selectedImage == null) showMessage(getString(R.string.zc_select_hint))
                else -> Unit
            }
        }
    }

    /** La barre flottante des réglages : seulement ce qui concerne l'outil courant. */
    private fun rebuildOptions() {
        options.removeAllViews()
        when (canvasView.tool) {
            ZoomCanvasView.Tool.PEN -> {
                options.addView(LabeledSlider(this, getString(R.string.px_opt_size), 1, 120, canvasView.penSize.toInt(), { getString(R.string.px_opt_size_value, it) }) {
                    canvasView.penSize = it.toFloat()
                })
                options.addView(LabeledSlider(this, getString(R.string.px_opt_opacity), 1, 100, penOpacity, { getString(R.string.px_opt_percent, it) }) {
                    penOpacity = it
                    applyPenColor()
                })
            }
            ZoomCanvasView.Tool.ERASER -> {
                options.addView(LabeledSlider(this, getString(R.string.px_opt_size), 2, 240, canvasView.eraserSize.toInt(), { getString(R.string.px_opt_size_value, it) }) {
                    canvasView.eraserSize = it.toFloat()
                })
            }
            ZoomCanvasView.Tool.SELECT -> {
                options.addView(chip(getString(R.string.zc_import_image), R.drawable.ic_px_image) { importImage.launch("image/*") })
                val sel = canvasView.selectedImage
                if (sel != null) {
                    options.addView(divider())
                    options.addView(chip(getString(R.string.zc_deselect), R.drawable.ic_px_close) { canvasView.selectImage(null) })
                    options.addView(chip(getString(R.string.zc_image_delete), R.drawable.ic_px_delete) {
                        vm.project?.scene?.deleteImage(sel)
                        canvasView.selectImage(null)
                        drawingChanged()
                    })
                }
            }
            ZoomCanvasView.Tool.HAND, ZoomCanvasView.Tool.MOVE_LAYER -> Unit
        }
        optionsScroll.visibility = if (options.childCount > 0) View.VISIBLE else View.GONE
    }

    // ---- Images ------------------------------------------------------------------------------

    private fun bitmapFor(key: String): Bitmap? {
        bitmaps.get(key)?.let { return it }
        val p = vm.project ?: return null
        if (loading.add(key)) {
            val f = vm.store.imageFile(p.meta.id, key)
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { if (f.isFile) BitmapFactory.decodeFile(f.path) else null }
                loading.remove(key)
                if (bmp != null) {
                    bitmaps.put(key, bmp)
                    canvasView.invalidate()
                }
            }
        }
        return null
    }

    /** Importe une image de l'appareil dans la couche de travail, au centre de l'écran, et la sélectionne. */
    private fun importImageFrom(uri: Uri) {
        val p = vm.project ?: return
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) { decodeForImport(uri) }
            if (prepared == null) {
                Toast.makeText(this@ZoomCanvasEditorActivity, R.string.zc_import_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (bmp, bytes, ext) = prepared
            val key = UUID.randomUUID().toString() + ext
            val ok = withContext(Dispatchers.IO) {
                try { vm.store.saveImage(p.meta.id, key, bytes); true } catch (e: Exception) { false }
            }
            if (!ok) {
                Toast.makeText(this@ZoomCanvasEditorActivity, R.string.zc_import_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            bitmaps.put(key, bmp)
            val item = p.scene.addImage(key, bmp.width, bmp.height, canvasView.width * 0.6, canvasView.height * 0.6)
            selectTool(ZoomCanvasView.Tool.SELECT, quiet = true)
            canvasView.selectImage(item.id)
            drawingChanged()
        }
    }

    /** Décode (réduit à [MAX_IMAGE_SIDE]) et prépare les octets à ranger : PNG si transparente, JPEG sinon. */
    private fun decodeForImport(uri: Uri): Triple<Bitmap, ByteArray, String>? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) null else {
                val out = ByteArrayOutputStream()
                val png = bmp.hasAlpha()
                bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
                Triple(bmp, out.toByteArray(), if (png) ".png" else ".jpg")
            }
        }
    } catch (e: Throwable) {
        null
    }

    // ---- Couleurs (comme le pixel art) --------------------------------------------------------

    private fun setupColors() {
        val list = findViewById<RecyclerView>(R.id.zc_palette_list)
        paletteAdapter = PaletteAdapter(
            onPick = { index, c ->
                if (colorDock.isOpen && colorDock.editingSecondary) secondary = c else primary = c
                linkedIndex = index
                palettes.addRecent(c)
                colorsChanged()
            },
            onMenu = { anchor, index, c -> showSwatchMenu(anchor, index, c) },
            onAdd = { addPrimaryToPalette() },
        )
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.adapter = paletteAdapter
        swatchPrimary.radiusDp = 8f
        swatchSecondary.radiusDp = 8f
        colorDock = findViewById(R.id.zc_color_dock)
        colorDock.bind(dockHost)
        swatchPrimary.setOnClickListener { toggleColorDock(false) }
        swatchSecondary.setOnClickListener { toggleColorDock(true) }
        findViewById<View>(R.id.zc_btn_swap).setOnClickListener {
            val t = primary; primary = secondary; secondary = t
            linkedIndex = -1
            colorsChanged()
        }
        findViewById<View>(R.id.zc_btn_palette).setOnClickListener { showPalettes() }
        colorsChanged()
    }

    private val dockHost = object : ColorDock.Host {
        override fun colorOf(secondary: Boolean) = if (secondary) this@ZoomCanvasEditorActivity.secondary else primary

        override fun editColor(secondary: Boolean, color: Int) {
            val old = colorOf(secondary)
            if (secondary) this@ZoomCanvasEditorActivity.secondary = color else primary = color
            // La case de palette qu'on a touchée suit le réglage (jamais une palette intégrée).
            val pal = palettes.current()
            val i = linkedIndex
            if (!pal.builtIn && i in pal.colors.indices && pal.colors[i] == old) {
                palettes.update(pal.id, pal.colors.toMutableList().also { it[i] = color })
            }
            colorsChanged()
        }

        override fun pickColor(secondary: Boolean, color: Int) {
            if (secondary) this@ZoomCanvasEditorActivity.secondary = color else primary = color
            linkedIndex = -1
            colorsChanged()
        }

        override fun recentColors() = palettes.recent()
        override fun hideRequested() = colorDock.hide()
        override fun closed(changed: List<Int>) { for (c in changed) palettes.addRecent(c) }
    }

    private fun toggleColorDock(secondary: Boolean) {
        if (colorDock.isOpen && colorDock.editingSecondary == secondary) colorDock.hide() else colorDock.show(secondary)
    }

    private fun colorsChanged() {
        swatchPrimary.color = primary
        swatchSecondary.color = secondary
        applyPenColor()
        val pal = palettes.current()
        if (pal.id != lastPaletteId) { lastPaletteId = pal.id; linkedIndex = -1 }
        paletteAdapter.submit(pal.colors)
        paletteAdapter.ringIndex =
            if (linkedIndex in pal.colors.indices && pal.colors[linkedIndex] == primary) linkedIndex
            else pal.colors.indexOf(primary)
        if (::colorDock.isInitialized) colorDock.refresh()
    }

    /** La palette qu'on peut modifier : la courante, ou sa copie dans « Ma palette » si elle est intégrée. */
    private fun editablePalette(): NamedPalette {
        val cur = palettes.current()
        if (!cur.builtIn) return cur
        val copy = palettes.create(getString(R.string.px_palette_mine), cur.colors)
        palettes.currentId = copy.id
        lastPaletteId = copy.id
        return copy
    }

    private fun showSwatchMenu(anchor: View, index: Int, color: Int) {
        val menu = androidx.appcompat.widget.PopupMenu(this, anchor)
        val edit = menu.menu.add(getString(R.string.px_color_edit))
        val asSecondary = menu.menu.add(getString(R.string.px_color_as_secondary))
        val delete = menu.menu.add(getString(R.string.px_delete))
        menu.setOnMenuItemClickListener { item ->
            when (item) {
                edit -> { primary = color; linkedIndex = index; colorDock.show(false); colorsChanged() }
                asSecondary -> { secondary = color; colorsChanged() }
                delete -> {
                    val pal = editablePalette()
                    val l = pal.colors.toMutableList()
                    if (index in l.indices) l.removeAt(index)
                    palettes.update(pal.id, l)
                    linkedIndex = -1
                    colorsChanged()
                }
            }
            true
        }
        menu.show()
    }

    private fun addPrimaryToPalette() {
        val pal = editablePalette()
        val at = pal.colors.indexOf(primary)
        if (at >= 0) linkedIndex = at else {
            palettes.update(pal.id, pal.colors + primary)
            linkedIndex = pal.colors.size
        }
        colorsChanged()
        findViewById<RecyclerView>(R.id.zc_palette_list).scrollToPosition(linkedIndex)
    }

    private fun showPalettes() {
        showPaletteSheet(palettes.all(), palettes.currentId, PaletteActions(
            onSelect = { palettes.currentId = it.id; colorsChanged() },
            onNew = {
                promptText(R.string.px_palette_new, "", R.string.px_create) { name ->
                    if (name.isNotEmpty()) { palettes.currentId = palettes.create(name, listOf(primary)).id; colorsChanged() }
                }
            },
            // Palette tirée du dessin : les couleurs des traits du projet.
            onFromImage = {
                val colors = vm.project?.scene?.allLayers()?.flatMap { l -> l.strokes.map { it.color or (0xFF shl 24) } }?.distinct()?.take(256).orEmpty()
                if (colors.isNotEmpty()) promptText(R.string.px_palette_from_image, getString(R.string.px_palette_mine), R.string.px_create) { name ->
                    if (name.isNotEmpty()) { palettes.currentId = palettes.create(name, colors).id; colorsChanged() }
                }
            },
            onImport = { importPaletteLauncher.launch("*/*") },
            onExport = { p -> pendingPaletteExport = p; exportPaletteLauncher.launch("${p.title(this)}.hex") },
            onRename = { p ->
                promptText(R.string.px_rename, p.name, R.string.px_ok) { name -> if (name.isNotEmpty()) { palettes.rename(p.id, name); colorsChanged() } }
            },
            onDelete = { p -> confirm(R.string.px_palette_delete, p.name, R.string.px_delete, true) { palettes.delete(p.id); colorsChanged() } },
        ))
    }

    private fun importPalette(uri: Uri) {
        lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext emptyList()
                    PaletteFormats.parse(String(bytes, Charsets.UTF_8))
                } catch (e: Exception) { emptyList() }
            }
            if (colors.isEmpty()) return@launch
            promptText(R.string.px_palette_import, getString(R.string.px_palette_mine), R.string.px_create) { name ->
                if (name.isNotEmpty()) { palettes.currentId = palettes.create(name, colors).id; colorsChanged() }
            }
        }
    }

    private fun writePalette(uri: Uri, p: NamedPalette) {
        lifecycleScope.launch(Dispatchers.IO) {
            try { contentResolver.openOutputStream(uri, "wt")?.use { it.write(PaletteFormats.toHexLines(p.colors).toByteArray()) } } catch (e: Exception) { }
        }
    }

    // ---- Menus -------------------------------------------------------------------------------

    private fun showMenu() {
        actionSheet(vm.project?.meta?.name, listOf(
            SheetItem(R.drawable.ic_px_image, getString(R.string.zc_import_image)) { importImage.launch("image/*") },
            SheetItem(R.drawable.ic_px_layers, getString(R.string.zc_layers_title)) { showLayers() },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.px_rename)) { askRename() },
        )).show()
    }

    private fun askRename() {
        val p = vm.project ?: return
        promptText(R.string.px_rename, p.meta.name, R.string.px_ok) { name ->
            if (name.isNotEmpty()) {
                vm.rename(name)
                title.text = name
            }
        }
    }

    /** Les couches dessinées, de la plus haute à la plus profonde : un appui y emmène. */
    private fun showLayers() {
        val s = vm.project?.scene ?: return
        val depths = s.nonEmptyDepths()
        bottomSheet(getString(R.string.zc_layers_title)) { root, dialog ->
            if (depths.isEmpty()) {
                root.addView(label(getString(R.string.zc_layers_none), 14f).apply { setPadding(dp(4), dp(8), dp(4), dp(16)) })
                return@bottomSheet
            }
            for (d in depths) {
                val l = s.layer(d) ?: continue
                val count = l.strokes.size + l.images.size
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(48)
                    setPadding(dp(8), 0, dp(8), 0)
                    background = androidx.core.content.res.ResourcesCompat.getDrawable(resources, R.drawable.bg_px_tool, theme)
                    isSelected = d == s.depth
                }
                row.addView(label(getString(R.string.zc_level, d.toString()), 15f, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(label(resources.getQuantityString(R.plurals.zc_items_count, count, count), 12f))
                row.setOnClickListener {
                    dialog.dismiss()
                    s.jumpTo(d, canvasView.width.toDouble(), canvasView.height.toDouble())
                    canvasView.selectImage(null)
                    canvasView.invalidate()
                    viewMoved()
                }
                root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            root.addView(label(getString(R.string.zc_layers_hint, s.ratio.toInt()), 12f).apply { setPadding(dp(4), dp(12), dp(4), 0) })
        }.show()
    }
}
