package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
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
import com.Atom2Universe.app.pixelart.canvas.FontManager
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.pixelart.io.DeviceImages
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.NamedPalette
import com.Atom2Universe.app.pixelart.io.PaletteFormats
import com.Atom2Universe.app.pixelart.io.PaletteStore
import com.Atom2Universe.app.pixelart.ui.ColorDock
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
import com.Atom2Universe.app.pixelart.ui.shapeIcon
import com.Atom2Universe.app.pixelart.ui.shapeLabel
import com.Atom2Universe.app.pixelart.ui.showShapeSheet
import com.Atom2Universe.app.pixelart.ui.showPaletteSheet
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.zoomcanvas.core.OrderMove
import com.Atom2Universe.app.zoomcanvas.core.ShapeItem
import com.Atom2Universe.app.zoomcanvas.core.StrokeBox
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * L'éditeur du canvas infini, sur le modèle du pixel art : barre d'outils et barre de couleurs en
 * bas (palettes partagées avec le pixel art), réglages de l'outil choisi juste à côté de son bouton,
 * pastille de niveau en bas à gauche, cadenas du zoom en haut.
 *
 * Crayon, pinceau, feutre, gomme, formes et texte ont chacun leur taille (et leur opacité, sauf la
 * gomme), gardées d'une séance à l'autre. La sélection prend n'importe quel élément (trait, image,
 * forme, texte) : elle le déplace, le redimensionne, le duplique, le supprime, et un petit widget le
 * monte ou le descend dans la pile des éléments de la couche.
 *
 * Le même éditeur sert le « Canvas » (une seule couche, [ZoomScene.single]) : la pastille du haut y
 * montre le zoom en pourcent plutôt que le niveau, et un appui dessus ouvre le menu de la vue
 * (zoom à 100 %, ajuster au dessin, grille, exporter l'image) à la place de la liste des couches.
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
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var levelText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var message: TextView
    private lateinit var toolsScroll: HorizontalScrollView
    private lateinit var lockBtn: ImageButton
    /** Le widget de pile : visible quand un élément est sélectionné. */
    private lateinit var orderPill: View
    private lateinit var orderText: TextView
    private val orderButtons = HashMap<OrderMove, View>()
    /** Les réglages de l'outil choisi, placés dans la barre d'outils juste après son bouton. */
    private lateinit var options: LinearLayout
    private lateinit var swatchPrimary: SwatchView
    private lateinit var swatchSecondary: SwatchView
    private lateinit var colorDock: ColorDock
    private lateinit var paletteAdapter: PaletteAdapter
    private val toolButtons = HashMap<ZoomCanvasView.Tool, ImageButton>()

    private var primary = 0xFF1E1E24.toInt()
    private var secondary = 0xFFFFFFFF.toInt()
    /** Épaisseur (pixels d'écran) et opacité (%) de chaque outil de dessin. */
    private val sizes = HashMap<ZoomCanvasView.Tool, Int>()
    private val opacities = HashMap<ZoomCanvasView.Tool, Int>()
    private var shapeKind = ShapeKind.RECT
    private var shapeFill = ShapeFill.OUTLINE
    private var shapeSquare = false
    private var textStyle = 0
    private var textFont = ""
    private val typefaces = HashMap<String, Typeface>()
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isLinearText = true }
    private var linkedIndex = -1
    private var lastPaletteId = ""
    private var shownLevel = Long.MIN_VALUE
    /** Le projet ouvert est un « Canvas » (une seule couche) : pas de niveaux, pas de réalignement. */
    private var single = false
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
        undoBtn = findViewById(R.id.zc_btn_undo)
        redoBtn = findViewById(R.id.zc_btn_redo)
        levelText = findViewById(R.id.zc_level_text)
        levelBar = findViewById(R.id.zc_level_bar)
        message = findViewById(R.id.zc_message)
        toolsScroll = findViewById(R.id.zc_tools_scroll)
        lockBtn = findViewById(R.id.zc_btn_lock)
        orderPill = findViewById(R.id.zc_order_pill)
        orderText = findViewById(R.id.zc_order_text)
        orderButtons[OrderMove.TO_BACK] = findViewById(R.id.zc_order_back)
        orderButtons[OrderMove.BACKWARD] = findViewById(R.id.zc_order_down)
        orderButtons[OrderMove.FORWARD] = findViewById(R.id.zc_order_up)
        orderButtons[OrderMove.TO_FRONT] = findViewById(R.id.zc_order_front)
        for ((move, button) in orderButtons) button.setOnClickListener { reorder(move) }
        options = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(6), 0)
        }
        swatchPrimary = findViewById(R.id.zc_swatch_primary)
        swatchSecondary = findViewById(R.id.zc_swatch_secondary)

        findViewById<View>(R.id.zc_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.zc_title_block).setOnClickListener { askRename() }
        findViewById<View>(R.id.zc_level_pill).setOnClickListener { if (single) showViewMenu() else showLayers() }
        undoBtn.setOnClickListener { if (vm.project?.scene?.undo() == true) drawingChanged() }
        redoBtn.setOnClickListener { if (vm.project?.scene?.redo() == true) drawingChanged() }
        lockBtn.setOnClickListener { setZoomLocked(!canvasView.zoomLocked) }

        FontManager.init(this)
        loadPrefs()
        canvasView.listener = canvasListener
        canvasView.imageProvider = ::bitmapFor
        canvasView.typefaceProvider = ::typefaceFor
        setupTools()
        setupColors()

        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) { finish(); return }
        vm.thumbnailProvider = { canvasView.thumbnail() }
        vm.onLayersLoaded = { canvasView.invalidate() }
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
        single = p.scene.single
        canvasView.scene = p.scene
        canvasView.showGrid = single && prefs.getBoolean("grid", false)
        // Une seule couche : rien à réaligner dessous.
        toolButtons[ZoomCanvasView.Tool.MOVE_LAYER]?.visibility = if (single) View.GONE else View.VISIBLE
        title.text = p.meta.name
        shownLevel = Long.MIN_VALUE
        refreshChrome()
    }

    /** Une fenêtre (menu, feuille, dialogue) vient de se fermer : on recache les barres système. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBarsVisibility()
    }

    override fun onPause() {
        vm.flush(force = true)
        savePrefs()
        super.onPause()
    }

    override fun onDestroy() {
        vm.thumbnailProvider = null
        vm.onLayersLoaded = null
        super.onDestroy()
    }

    /** Les réglages d'un outil de dessin : épaisseur par défaut, bornes, opacité réglable ou non. */
    private class DrawDef(val size: Int, val min: Int, val max: Int, val hasOpacity: Boolean = true)

    private val drawDefs = mapOf(
        ZoomCanvasView.Tool.PEN to DrawDef(6, 1, 120),
        ZoomCanvasView.Tool.BRUSH to DrawDef(16, 2, 200),
        ZoomCanvasView.Tool.MARKER to DrawDef(12, 2, 160),
        ZoomCanvasView.Tool.ERASER to DrawDef(24, 2, 240, hasOpacity = false),
        // Pour une forme : l'épaisseur du contour. Pour un texte : la taille de la police.
        ZoomCanvasView.Tool.SHAPE to DrawDef(4, 1, 60),
        ZoomCanvasView.Tool.TEXT to DrawDef(32, 8, 300),
    )

    private fun loadPrefs() {
        primary = prefs.getInt("primary", primary)
        secondary = prefs.getInt("secondary", secondary)
        for ((t, d) in drawDefs) {
            sizes[t] = prefs.getInt("size_${t.name}", d.size).coerceIn(d.min, d.max)
            opacities[t] = prefs.getInt("opacity_${t.name}", 100).coerceIn(1, 100)
        }
        shapeKind = runCatching { ShapeKind.valueOf(prefs.getString("shape", "RECT")!!) }.getOrDefault(ShapeKind.RECT)
        shapeFill = runCatching { ShapeFill.valueOf(prefs.getString("shape_fill", "OUTLINE")!!) }.getOrDefault(ShapeFill.OUTLINE)
        shapeSquare = prefs.getBoolean("shape_square", false)
        textStyle = prefs.getInt("text_style", 0)
        textFont = prefs.getString("text_font", "") ?: ""
        setZoomLocked(prefs.getBoolean("zoom_locked", false))
    }

    private fun savePrefs() {
        val e = prefs.edit()
            .putInt("primary", primary)
            .putInt("secondary", secondary)
            .putString("tool", canvasView.tool.name)
            .putBoolean("zoom_locked", canvasView.zoomLocked)
            .putString("shape", shapeKind.name)
            .putString("shape_fill", shapeFill.name)
            .putBoolean("shape_square", shapeSquare)
            .putInt("text_style", textStyle)
            .putString("text_font", textFont)
        for (t in drawDefs.keys) {
            e.putInt("size_${t.name}", sizes[t]!!)
            e.putInt("opacity_${t.name}", opacities[t]!!)
        }
        e.apply()
    }

    /** Une couleur avec son opacité réglée à [percent] % de celle qu'elle avait. */
    private fun withOpacity(color: Int, percent: Int): Int {
        val a = ((color ushr 24) * percent / 100).coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    /**
     * L'outil choisi trace la couleur principale, avec sa taille et son opacité. Une forme remplie
     * prend la couleur principale, une forme « les deux » remplit avec la couleur secondaire.
     */
    private fun applyPenColor() {
        val t = canvasView.tool
        sizes[t]?.let { canvasView.strokeSize = it.toFloat() }
        val pct = opacities[t] ?: 100
        canvasView.color = withOpacity(primary, pct)
        canvasView.fillColor = if (shapeFill == ShapeFill.FILL) canvasView.color else withOpacity(secondary, pct)
        canvasView.shapeKind = shapeKind
        canvasView.shapeFill = shapeFill
        canvasView.shapeSquare = shapeSquare
        canvasView.textStyle = textStyle
        canvasView.textFont = textFont
    }

    /** Le cadenas fermé : le zoom reste où il est, deux doigts ne font plus que déplacer la vue. */
    private fun setZoomLocked(locked: Boolean) {
        canvasView.zoomLocked = locked
        lockBtn.isSelected = locked
        lockBtn.setImageResource(if (locked) R.drawable.ic_px_lock else R.drawable.ic_px_unlock)
        val desc = getString(if (locked) R.string.zc_zoom_unlock else R.string.zc_zoom_lock)
        lockBtn.contentDescription = desc
        TooltipCompat.setTooltipText(lockBtn, desc)
    }

    // ---- Retours de la toile ---------------------------------------------------------------

    private val canvasListener = object : ZoomCanvasView.Listener {
        override fun onDrawingChanged() = drawingChanged()
        override fun onViewMoved() = viewMoved()
        override fun onNothingToMove() = showMessage(getString(R.string.zc_no_layer_below))
        override fun onSelectionChanged() = rebuildOptions()
        override fun onTextRequested(id: Long?, sx: Double, sy: Double) = askText(id, sx, sy)
    }

    private fun drawingChanged() {
        canvasView.invalidate()
        refreshChrome()
        if (canvasView.tool == ZoomCanvasView.Tool.SELECT || canvasView.tool == ZoomCanvasView.Tool.ERASER_EDIT) rebuildOptions()
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
        if (single) {
            levelText.text = getString(R.string.px_opt_percent, Math.round(s.zoom * 100).toInt())
        } else if (s.depth != shownLevel) {
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
        ToolDef(ZoomCanvasView.Tool.BRUSH, R.drawable.ic_px_brush, R.string.zc_tool_brush),
        ToolDef(ZoomCanvasView.Tool.MARKER, R.drawable.ic_zc_marker, R.string.zc_tool_marker),
        ToolDef(ZoomCanvasView.Tool.ERASER, R.drawable.ic_px_eraser, R.string.zc_tool_eraser),
        ToolDef(ZoomCanvasView.Tool.SHAPE, R.drawable.ic_px_shape, R.string.px_tool_shape),
        ToolDef(ZoomCanvasView.Tool.TEXT, R.drawable.ic_zc_text, R.string.zc_tool_text),
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
                // Appui long sur les formes : la feuille des dix formes, comme dans le pixel art.
                if (d.tool == ZoomCanvasView.Tool.SHAPE) setOnLongClickListener { showShapeSheet(shapeKind) { pickShape(it) }; true }
                // Appui long sur la gomme : le mode d'édition des gommes (un second appui long en sort).
                if (d.tool == ZoomCanvasView.Tool.ERASER) setOnLongClickListener {
                    selectTool(if (canvasView.tool == ZoomCanvasView.Tool.ERASER_EDIT) ZoomCanvasView.Tool.ERASER else ZoomCanvasView.Tool.ERASER_EDIT)
                    true
                }
            }
            TooltipCompat.setTooltipText(b, getString(d.label))
            toolButtons[d.tool] = b
            bar.addView(b)
        }
        updateShapeToolIcon()
        val saved = prefs.getString("tool", null)?.let { n -> ZoomCanvasView.Tool.values().firstOrNull { it.name == n } }
        selectTool(when (saved) {
            null, ZoomCanvasView.Tool.MOVE_LAYER -> ZoomCanvasView.Tool.PEN
            // On ne rouvre pas le canvas estompé : le mode d'édition des gommes se choisit à chaque fois.
            ZoomCanvasView.Tool.ERASER_EDIT -> ZoomCanvasView.Tool.ERASER
            else -> saved
        }, quiet = true)
    }

    private fun selectTool(t: ZoomCanvasView.Tool, quiet: Boolean = false) {
        canvasView.tool = t
        // Le mode d'édition des gommes n'a pas de bouton à lui : c'est celui de la gomme qui reste allumé.
        for ((k, b) in toolButtons) b.isSelected = k == t || (k == ZoomCanvasView.Tool.ERASER && t == ZoomCanvasView.Tool.ERASER_EDIT)
        applyPenColor()
        rebuildOptions()
        if (!quiet && t == ZoomCanvasView.Tool.MOVE_LAYER) showMessage(getString(R.string.zc_move_layer_hint))
    }

    /** Monte ou descend l'élément sélectionné dans la pile de la couche. */
    private fun reorder(move: OrderMove) {
        val sel = canvasView.selectedItem ?: return
        if (vm.project?.scene?.reorder(sel, move) == true) drawingChanged()
    }

    /** Le widget de pile : rang de l'élément sélectionné parmi ceux de la couche, et ce qu'on peut encore faire. */
    private fun refreshOrder() {
        val info = canvasView.selectedItem?.let { vm.project?.scene?.orderInfo(it) }
        orderPill.visibility = if (info == null) View.GONE else View.VISIBLE
        if (info == null) return
        orderText.text = getString(R.string.zc_order_position, info.rank, info.count)
        for ((move, on) in listOf(
            OrderMove.TO_BACK to info.canToBack, OrderMove.BACKWARD to info.canBackward,
            OrderMove.FORWARD to info.canForward, OrderMove.TO_FRONT to info.canToFront,
        )) {
            orderButtons[move]?.apply { isEnabled = on; alpha = if (on) 1f else 0.35f }
        }
    }

    /** Le bouton des formes montre la forme choisie. */
    private fun updateShapeToolIcon() {
        toolButtons[ZoomCanvasView.Tool.SHAPE]?.setImageResource(shapeIcon(shapeKind))
    }

    private fun pickShape(kind: ShapeKind) {
        shapeKind = kind
        updateShapeToolIcon()
        if (canvasView.tool != ZoomCanvasView.Tool.SHAPE) selectTool(ZoomCanvasView.Tool.SHAPE) else { applyPenColor(); rebuildOptions() }
    }

    /**
     * Les réglages de l'outil courant, glissés dans la barre d'outils juste après son bouton (la
     * barre défile pour les montrer).
     */
    private fun rebuildOptions() {
        options.removeAllViews()
        refreshOrder()
        val t = canvasView.tool
        val draw = drawDefs[t]
        // Les choix propres à l'outil d'abord (forme, remplissage, police…), puis ses curseurs.
        when (t) {
            ZoomCanvasView.Tool.SHAPE -> {
                options.addView(chip(getString(shapeLabel(shapeKind)), shapeIcon(shapeKind)) { showShapeSheet(shapeKind) { pickShape(it) } })
                if (shapeKind != ShapeKind.LINE) options.addView(fillChip(shapeFill) { shapeFill = it; applyPenColor() })
                if (shapeKind != ShapeKind.LINE && shapeKind != ShapeKind.ARROW) {
                    options.addView(chip(getString(R.string.px_opt_square), R.drawable.ic_px_square_lock) {
                        shapeSquare = !shapeSquare
                        it.isSelected = shapeSquare
                        applyPenColor()
                    }.apply { isSelected = shapeSquare; contentDescription = getString(R.string.px_opt_square) })
                }
            }
            ZoomCanvasView.Tool.TEXT -> {
                options.addView(fontChip(textFont) { textFont = it; applyPenColor(); rebuildOptions() })
                options.addView(styleChip(R.drawable.ic_zc_bold, R.string.zc_bold, textStyle and TextItem.BOLD != 0) {
                    textStyle = textStyle xor TextItem.BOLD
                    applyPenColor()
                })
                options.addView(styleChip(R.drawable.ic_zc_italic, R.string.zc_italic, textStyle and TextItem.ITALIC != 0) {
                    textStyle = textStyle xor TextItem.ITALIC
                    applyPenColor()
                })
            }
            else -> Unit
        }
        if (draw != null) {
            options.addView(StepSlider(this, getString(R.string.px_opt_size), draw.min, draw.max, sizes[t]!!, { getString(R.string.px_opt_size_value, it) }) {
                sizes[t] = it
                applyPenColor()
            })
            if (draw.hasOpacity) options.addView(StepSlider(this, getString(R.string.px_opt_opacity), 1, 100, opacities[t]!!, { getString(R.string.px_opt_percent, it) }) {
                opacities[t] = it
                applyPenColor()
            })
        }
        if (t == ZoomCanvasView.Tool.ERASER_EDIT) {
            options.addView(chip(getString(R.string.zc_eraser_edit_done), R.drawable.ic_px_check) { selectTool(ZoomCanvasView.Tool.ERASER) })
        }
        if (t == ZoomCanvasView.Tool.SELECT || t == ZoomCanvasView.Tool.ERASER_EDIT) {
            if (t == ZoomCanvasView.Tool.SELECT) options.addView(chip(getString(R.string.zc_import_image), R.drawable.ic_px_image) { importImage.launch("image/*") })
            val sel = canvasView.selectedItem
            val item = sel?.let { vm.project?.scene?.box(it) }
            if (sel != null && item != null) {
                options.addView(divider())
                when (item) {
                    is TextItem -> selectedTextOptions(item)
                    is ShapeItem -> selectedShapeOptions(item)
                    // Un coup de gomme n'a pas de couleur : on le déplace, le redimensionne, le monte ou le supprime.
                    is StrokeBox -> if (!item.stroke.isEraser) options.addView(chip(getString(R.string.zc_recolor), R.drawable.ic_px_palette) {
                        vm.project?.scene?.changeBox(item.recolored(primary))
                        drawingChanged()
                    })
                    else -> Unit
                }
                options.addView(chip(getString(R.string.zc_duplicate), R.drawable.ic_px_copy) {
                    val copy = vm.project?.scene?.duplicateBox(sel, dp(16).toDouble())
                    if (copy != null) canvasView.selectItem(copy.id)
                    drawingChanged()
                })
                options.addView(chip(getString(R.string.zc_deselect), R.drawable.ic_px_close) { canvasView.selectItem(null) })
                options.addView(chip(getString(R.string.px_delete), R.drawable.ic_px_delete) {
                    vm.project?.scene?.deleteBox(sel)
                    canvasView.selectItem(null)
                    drawingChanged()
                })
            }
        }
        (options.parent as? ViewGroup)?.removeView(options)
        val button = toolButtons[if (t == ZoomCanvasView.Tool.ERASER_EDIT) ZoomCanvasView.Tool.ERASER else t] ?: return
        if (options.childCount == 0) return
        val bar = button.parent as ViewGroup
        bar.addView(options, bar.indexOfChild(button) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        options.post {
            // Montrer le bouton et ses réglages ensemble, sans faire défiler plus que nécessaire.
            val left = button.left
            val right = options.right
            val x = toolsScroll.scrollX
            val w = toolsScroll.width
            val to = when {
                right - x > w -> minOf(left, right - w)
                left < x -> left
                else -> x
            }
            if (to != x) toolsScroll.smoothScrollTo(to, 0)
        }
    }

    // ---- Texte -------------------------------------------------------------------------------

    /** Appelée aussi par les fils qui cuisent un cache raster : d'où le verrou (le gestionnaire de polices n'en a pas). */
    private fun typefaceFor(font: String, style: Int): Typeface = synchronized(typefaces) {
        typefaces.getOrPut("$font|$style") {
            val base = if (font.isEmpty()) Typeface.DEFAULT else FontManager.getTypeface(this, font)
            val bold = style and TextItem.BOLD != 0
            val italic = style and TextItem.ITALIC != 0
            Typeface.create(base, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
        }
    }

    /** Largeur de la ligne la plus longue de [text] pour une police de taille 1 (la scène en déduit la boîte). */
    private fun unitWidth(text: String, font: String, style: Int): Double {
        measurePaint.typeface = typefaceFor(font, style)
        measurePaint.textSize = 100f
        return text.split('\n').maxOf { measurePaint.measureText(it) } / 100.0
    }

    /** Un appui avec l'outil texte : écrire un nouveau texte à cet endroit, ou modifier celui qu'on a touché. */
    private fun askText(id: Long?, sx: Double, sy: Double) {
        val scene = vm.project?.scene ?: return
        val existing = id?.let { scene.box(it) as? TextItem }
        promptMultiline(R.string.zc_text_title, existing?.text.orEmpty()) { text -> applyText(existing, text, sx, sy) }
    }

    private fun applyText(existing: TextItem?, text: String, sx: Double, sy: Double) {
        val scene = vm.project?.scene ?: return
        if (existing != null) {
            // Vider un texte le supprime.
            if (text.isBlank()) scene.deleteBox(existing.id)
            else scene.changeBox(existing.restyled(text = text, unitWidth = unitWidth(text, existing.font, existing.style)))
        } else {
            scene.addText(text, sx, sy, (sizes[ZoomCanvasView.Tool.TEXT] ?: 32).toDouble(), canvasView.color, textStyle, textFont, unitWidth(text, textFont, textStyle))
        }
        drawingChanged()
    }

    private fun fillIcon(f: ShapeFill) = when (f) {
        ShapeFill.OUTLINE -> R.drawable.ic_px_fill_outline
        ShapeFill.FILL -> R.drawable.ic_px_fill_solid
        ShapeFill.BOTH -> R.drawable.ic_px_fill_both
    }

    private fun fillLabel(f: ShapeFill) = getString(when (f) {
        ShapeFill.OUTLINE -> R.string.px_fill_outline
        ShapeFill.FILL -> R.string.px_fill_solid
        ShapeFill.BOTH -> R.string.px_fill_both
    })

    /** Contour, remplissage ou les deux : un appui passe au suivant. */
    private fun fillChip(initial: ShapeFill, onPick: (ShapeFill) -> Unit): TextView {
        var current = initial
        return chip(fillLabel(current), fillIcon(current)) { v ->
            current = ShapeFill.values()[(current.ordinal + 1) % ShapeFill.values().size]
            v.text = fillLabel(current)
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(fillIcon(current), 0, 0, 0)
            v.compoundDrawablesRelative[0]?.setBounds(0, 0, dp(18), dp(18))
            onPick(current)
        }.apply { contentDescription = getString(R.string.px_opt_fill_mode) }
    }

    private fun fontChip(font: String, onPick: (String) -> Unit): TextView =
        chip(font.ifEmpty { getString(R.string.zc_font_default) }, R.drawable.ic_zc_text) { showFontSheet(font, onPick) }
            .apply { contentDescription = getString(R.string.zc_font) }

    /** Gras ou italique : une puce à bascule, allumée quand le style est actif. */
    private fun styleChip(@androidx.annotation.DrawableRes icon: Int, @androidx.annotation.StringRes description: Int, on: Boolean, onToggle: () -> Unit): TextView =
        chip(null, icon) { v ->
            onToggle()
            v.isSelected = !v.isSelected
        }.apply { isSelected = on; contentDescription = getString(description) }

    /** Les réglages d'un texte sélectionné : modifier, police, gras, italique, couleur. */
    private fun selectedTextOptions(item: TextItem) {
        val scene = vm.project?.scene ?: return
        /** Même texte, autre style : la largeur change, le coin haut-gauche reste. */
        fun restyle(style: Int = item.style, font: String = item.font) {
            scene.changeBox(item.restyled(style = style, font = font, unitWidth = unitWidth(item.text, font, style)))
            drawingChanged()
        }
        options.addView(chip(getString(R.string.zc_text_edit), R.drawable.ic_px_rename) { askText(item.id, 0.0, 0.0) })
        options.addView(fontChip(item.font) { restyle(font = it) })
        options.addView(styleChip(R.drawable.ic_zc_bold, R.string.zc_bold, item.bold) { restyle(style = item.style xor TextItem.BOLD) })
        options.addView(styleChip(R.drawable.ic_zc_italic, R.string.zc_italic, item.italic) { restyle(style = item.style xor TextItem.ITALIC) })
        options.addView(chip(getString(R.string.zc_recolor), R.drawable.ic_px_palette) {
            scene.changeBox(item.copy(color = primary))
            drawingChanged()
        })
    }

    /** Les réglages d'une forme sélectionnée : contour / remplissage, couleurs. */
    private fun selectedShapeOptions(item: ShapeItem) {
        val scene = vm.project?.scene ?: return
        if (item.kind != ShapeKind.LINE) {
            options.addView(fillChip(item.fill) { f ->
                // Plein : la couleur du trait ; les deux : la couleur secondaire ; contour seul : rien ne change.
                val fillColor = when (f) { ShapeFill.FILL -> item.strokeColor; ShapeFill.BOTH -> secondary; ShapeFill.OUTLINE -> item.fillColor }
                scene.changeBox(item.copy(fill = f, fillColor = fillColor))
                drawingChanged()
            })
        }
        options.addView(chip(getString(R.string.zc_recolor), R.drawable.ic_px_palette) {
            scene.changeBox(item.copy(strokeColor = primary, fillColor = if (item.fill == ShapeFill.FILL) primary else secondary))
            drawingChanged()
        })
    }

    private fun showFontSheet(current: String, onPick: (String) -> Unit) {
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_zc_text, getString(R.string.zc_font_default), checked = current.isEmpty()) { onPick("") })
        for (name in FontManager.getAvailableFontNames()) {
            items.add(SheetItem(R.drawable.ic_zc_text, name, checked = name == current) { onPick(name) })
        }
        actionSheet(getString(R.string.zc_font), items).show()
    }

    // ---- Images ------------------------------------------------------------------------------

    /** Appelée aussi par les fils qui cuisent un cache raster : le cache de bitmaps est sûr, le suivi des chargements est verrouillé. */
    private fun bitmapFor(key: String): Bitmap? {
        bitmaps.get(key)?.let { return it }
        val p = vm.project ?: return null
        val start = synchronized(loading) { loading.add(key) }
        if (start) {
            val f = vm.store.imageFile(p.meta.id, key)
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { if (f.isFile) BitmapFactory.decodeFile(f.path) else null }
                synchronized(loading) { loading.remove(key) }
                if (bmp != null) {
                    bitmaps.put(key, bmp)
                    // Les caches raster qui ont cuit un cadre gris à sa place sont à refaire.
                    canvasView.invalidateCaches()
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
            canvasView.selectItem(item.id)
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
                val colors = vm.project?.scene?.allLayers()?.flatMap { l -> l.strokes.filter { !it.isEraser }.map { it.color or (0xFF shl 24) } }?.distinct()?.take(256).orEmpty()
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

    private fun askRename() {
        val p = vm.project ?: return
        promptText(R.string.px_rename, p.meta.name, R.string.px_ok) { name ->
            if (name.isNotEmpty()) {
                vm.rename(name)
                title.text = name
            }
        }
    }

    /** Le menu de la vue du « Canvas » : zoom, cadrage, grille, export. */
    private fun showViewMenu() {
        val s = vm.project?.scene ?: return
        actionSheet(getString(R.string.cv_view_title), listOf(
            SheetItem(R.drawable.ic_px_flip_h, getString(R.string.cv_zoom_reset)) {
                s.zoomAt(1.0 / s.zoom, 0.0, 0.0)
                canvasView.invalidate()
                viewMoved()
            },
            SheetItem(R.drawable.ic_px_crop, getString(R.string.cv_fit_content)) {
                s.jumpTo(s.depth, canvasView.width.toDouble(), canvasView.height.toDouble(), ZoomScene.SINGLE_FIT_MAX_ZOOM)
                canvasView.invalidate()
                viewMoved()
            },
            SheetItem(R.drawable.ic_px_grid, getString(R.string.cv_grid), checked = canvasView.showGrid) {
                canvasView.showGrid = !canvasView.showGrid
                prefs.edit().putBoolean("grid", canvasView.showGrid).apply()
            },
            SheetItem(R.drawable.ic_px_export, getString(R.string.cv_export_view)) { exportView() },
        )).show()
    }

    /** Range ce qu'on voit à l'écran, en taille réelle, dans la galerie de l'appareil. */
    private fun exportView() {
        val bmp = canvasView.snapshot() ?: return
        val base = (vm.project?.meta?.name ?: getString(R.string.creative_hub_canvas_title)).replace(Regex("[\\/:*?\"<>|]"), "_")
        val fileName = base + " " + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
        val app = applicationContext
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                bmp.recycle()
                DeviceImages.saveToGallery(app, bytes, fileName, ImageFormat.PNG) != null
            }
            Toast.makeText(this@ZoomCanvasEditorActivity, if (saved) R.string.px_saved_gallery else R.string.px_export_failed, Toast.LENGTH_SHORT).show()
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
                val count = l.itemCount
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
                    canvasView.selectItem(null)
                    canvasView.invalidate()
                    viewMoved()
                }
                root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            root.addView(label(getString(R.string.zc_layers_hint, s.ratio.toInt()), 12f).apply { setPadding(dp(4), dp(12), dp(4), 0) })
        }.show()
    }
}
