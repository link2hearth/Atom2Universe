package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.SwatchView
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.divider
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.iconButton
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.showColorPicker
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch

/**
 * L'éditeur du canvas infini : la toile, la pastille de niveau, et quatre outils (crayon, gomme,
 * main, réalignement de la couche du dessous).
 */
class ZoomCanvasEditorActivity : AppCompatActivity(), ZoomCanvasView.Listener {

    companion object {
        private const val EXTRA_ID = "zoom_canvas_id"
        private const val PREFS = "zoom_canvas_prefs"
        private const val KEY_COLOR = "color"
        private const val KEY_WIDTH = "width_dp"
        private const val KEY_RECENTS = "recents"

        fun intent(context: Context, id: String) = Intent(context, ZoomCanvasEditorActivity::class.java).putExtra(EXTRA_ID, id)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val vm: ZoomCanvasViewModel by viewModels()
    private lateinit var canvasView: ZoomCanvasView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var levelText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var message: TextView
    private lateinit var swatch: SwatchView
    private val toolButtons = HashMap<ZoomCanvasView.Tool, ImageButton>()
    private var widthDp = 4
    private val recents = ArrayList<Int>()
    private var shownLevel = Long.MIN_VALUE
    private val hideMessage = Runnable { message.visibility = View.GONE }

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

        findViewById<View>(R.id.zc_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.zc_title_block).setOnClickListener { askRename() }
        findViewById<View>(R.id.zc_btn_more).setOnClickListener { showMenu() }
        findViewById<View>(R.id.zc_level_pill).setOnClickListener { showLayers() }
        undoBtn.setOnClickListener { if (vm.project?.scene?.undo() == true) onContentChanged() }
        redoBtn.setOnClickListener { if (vm.project?.scene?.redo() == true) onContentChanged() }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        canvasView.color = prefs.getInt(KEY_COLOR, 0xFF1E1E24.toInt())
        widthDp = prefs.getInt(KEY_WIDTH, 4)
        canvasView.penWidthPx = dp(widthDp).toFloat()
        prefs.getString(KEY_RECENTS, null)?.split(',')?.mapNotNull { it.toLongOrNull()?.toInt() }?.let { recents.addAll(it) }
        buildTools()
        canvasView.listener = this

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

    override fun onPause() {
        vm.flush()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_COLOR, canvasView.color)
            .putInt(KEY_WIDTH, widthDp)
            .putString(KEY_RECENTS, recents.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })
            .apply()
        super.onPause()
    }

    override fun onDestroy() {
        vm.thumbnailProvider = null
        super.onDestroy()
    }

    // ---- Retours de la toile ---------------------------------------------------------------

    override fun onContentChanged() {
        canvasView.invalidate()
        refreshChrome()
        vm.scheduleSave(contentChanged = true)
    }

    override fun onCameraChanged() {
        refreshLevel()
        vm.scheduleSave(contentChanged = false)
    }

    override fun onNothingToMove() {
        showMessage(getString(R.string.zc_no_layer_below))
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

    private fun buildTools() {
        val bar = findViewById<LinearLayout>(R.id.zc_tools)
        fun tool(t: ZoomCanvasView.Tool, icon: Int, desc: Int) {
            val b = iconButton(icon, desc, size = 46, pad = 11) { selectTool(t) }
            toolButtons[t] = b
            bar.addView(b)
        }
        tool(ZoomCanvasView.Tool.PEN, R.drawable.ic_px_pencil, R.string.zc_tool_pen)
        tool(ZoomCanvasView.Tool.ERASER, R.drawable.ic_px_eraser, R.string.zc_tool_eraser)
        tool(ZoomCanvasView.Tool.HAND, R.drawable.ic_px_hand, R.string.zc_tool_hand)
        tool(ZoomCanvasView.Tool.MOVE_LAYER, R.drawable.ic_px_layers, R.string.zc_tool_move_layer)
        bar.addView(divider())
        swatch = SwatchView(this).apply {
            color = canvasView.color
            radiusDp = 10f
            contentDescription = getString(R.string.px_color_title)
            setOnClickListener { pickColor() }
        }
        bar.addView(swatch, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(6), 0, dp(6), 0) })
        bar.addView(iconButton(R.drawable.ic_px_brush, R.string.zc_width, size = 46, pad = 11) { pickWidth() })
        selectTool(ZoomCanvasView.Tool.PEN)
    }

    private fun selectTool(t: ZoomCanvasView.Tool) {
        canvasView.tool = t
        for ((k, b) in toolButtons) b.isSelected = k == t
        if (t == ZoomCanvasView.Tool.MOVE_LAYER) showMessage(getString(R.string.zc_move_layer_hint))
    }

    private fun pickColor() {
        showColorPicker(canvasView.color, recents, PALETTE) { c ->
            canvasView.color = c
            swatch.color = c
            recents.remove(c)
            recents.add(0, c)
            while (recents.size > 12) recents.removeAt(recents.size - 1)
            if (canvasView.tool != ZoomCanvasView.Tool.PEN) selectTool(ZoomCanvasView.Tool.PEN)
        }
    }

    private fun pickWidth() {
        bottomSheet(getString(R.string.zc_width)) { root, _ ->
            val preview = object : View(this) {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: android.graphics.Canvas) {
                    paint.color = canvasView.color
                    c.drawCircle(width / 2f, height / 2f, dp(widthDp) / 2f, paint)
                }
            }
            root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))
            root.addView(LabeledSlider(this, getString(R.string.zc_width), 1, 60, widthDp, { "$it" }) { v ->
                widthDp = v
                canvasView.penWidthPx = dp(v).toFloat()
                preview.invalidate()
            })
        }.show()
    }

    // ---- Menus -------------------------------------------------------------------------------

    private fun showMenu() {
        actionSheet(vm.project?.meta?.name, listOf(
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
                val count = s.layer(d)?.strokes?.size ?: 0
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
                row.addView(label(resources.getQuantityString(R.plurals.zc_strokes_count, count, count), 12f))
                row.setOnClickListener {
                    dialog.dismiss()
                    s.jumpTo(d, canvasView.width.toDouble(), canvasView.height.toDouble())
                    canvasView.invalidate()
                    onCameraChanged()
                }
                root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            root.addView(label(getString(R.string.zc_layers_hint, s.ratio.toInt()), 12f).apply { setPadding(dp(4), dp(12), dp(4), 0) })
        }.show()
    }

    private val PALETTE = listOf(
        0xFF1E1E24, 0xFFFFFFFF, 0xFF8A8A8A, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
        0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF6D4C41,
    ).map { it.toInt() }
}
