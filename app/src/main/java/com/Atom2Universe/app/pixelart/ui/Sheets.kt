package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.BlendMode
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.pixelart.io.ImageExport
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.NamedPalette

/** Bouton plein, couleur d'accent : l'action principale d'une feuille. */
fun Context.primaryButton(text: CharSequence, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    setTextColor(onAccentColor())
    textSize = 15f
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    background = GradientDrawable().apply { setColor(accentColor()); cornerRadius = dpf(14f) }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(18) }
    setOnClickListener { onClick() }
}

fun Context.secondaryButton(text: CharSequence, icon: Int = 0, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
    textSize = 14f
    gravity = Gravity.CENTER
    background = ContextCompat.getDrawable(context, R.drawable.bg_px_chip)
    if (icon != 0) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawablePadding = dp(8)
        compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.px_icon_tint)
        compoundDrawablesRelative[0]?.setBounds(0, 0, dp(20), dp(20))
        setCompoundDrawablesRelative(compoundDrawablesRelative[0], null, null, null)
    }
    setPadding(dp(12), 0, dp(12), 0)
    layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
    setOnClickListener { onClick() }
}

private fun Context.sectionLabel(text: Int): TextView = label(getString(text), 12f).apply { setPadding(dp(2), dp(16), 0, dp(6)) }

/** Un groupe de puces exclusives : retourne la ligne et une fonction qui lit / change la sélection. */
private class ChoiceRow<T>(context: Context, val options: List<Pair<T, CharSequence>>, initial: T, val onChange: (T) -> Unit) {
    var value: T = initial
        private set
    private val chips = ArrayList<TextView>()
    val view = context.scrollRow().also { row ->
        val inner = row.getChildAt(0) as LinearLayout
        for ((v, text) in options) {
            val ch = context.chip(text) { select(v) }
            ch.isSelected = v == initial
            chips.add(ch)
            inner.addView(ch)
        }
    }

    private fun select(v: T) {
        value = v
        options.forEachIndexed { i, o -> chips[i].isSelected = o.first == v }
        onChange(v)
    }
}

// ---- Nouveau projet ----------------------------------------------------------------------------

fun Context.showNewProjectSheet(defaultName: String, onCreate: (name: String, w: Int, h: Int) -> Unit) {
    val presets = listOf(16, 32, 48, 64, 128, 256)
    var w = 32
    var h = 32
    lateinit var wField: EditText
    lateinit var hField: EditText
    var custom = false

    val dialog = bottomSheet(getString(R.string.px_new_title)) { root, dlg ->
        val nameField = EditText(this).apply {
            setText(defaultName)
            setSingleLine()
            hint = getString(R.string.px_name_hint)
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
            setSelectAllOnFocus(true)
        }
        root.addView(nameField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(sectionLabel(R.string.px_canvas_size))
        val customBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        fun numberField(initial: Int) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initial.toString())
            gravity = Gravity.CENTER
            setSingleLine()
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
        }
        wField = numberField(w); hField = numberField(h)
        customBox.addView(wField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        customBox.addView(label("×", 18f, true).apply { setPadding(dp(12), 0, dp(12), 0) })
        customBox.addView(hField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val choices = presets.map { it to "$it×$it" as CharSequence } + (-1 to getString(R.string.px_custom) as CharSequence)
        val row = ChoiceRow(this, choices, 32) { v ->
            custom = v == -1
            customBox.visibility = if (custom) View.VISIBLE else View.GONE
            if (!custom) { w = v; h = v; wField.setText(v.toString()); hField.setText(v.toString()) }
        }
        root.addView(row.view)
        root.addView(customBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(primaryButton(getString(R.string.px_create)) {
            val cw = (if (custom) wField.text.toString().toIntOrNull() else w) ?: 32
            val ch = (if (custom) hField.text.toString().toIntOrNull() else h) ?: 32
            dlg.dismiss()
            onCreate(nameField.text.toString().trim().ifEmpty { defaultName }, cw.coerceIn(1, Document.MAX_SIDE), ch.coerceIn(1, Document.MAX_SIDE))
        })
    }
    dialog.show()
}

// ---- Taille de la toile ------------------------------------------------------------------------------

fun Context.showCanvasSizeSheet(session: EditorSession, onApplied: () -> Unit) {
    val doc = session.doc
    var scaleMode = false
    var ax = 1
    var ay = 1
    val dialog = bottomSheet(getString(R.string.px_canvas_title)) { root, dlg ->
        fun numberField(initial: Int) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initial.toString())
            gravity = Gravity.CENTER
            setSingleLine()
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
        }
        val wField = numberField(doc.width)
        val hField = numberField(doc.height)
        val sizeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sizeRow.addView(wField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sizeRow.addView(label("×", 18f, true).apply { setPadding(dp(12), 0, dp(12), 0) })
        sizeRow.addView(hField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(sizeRow)

        val anchors = GridLayout(this).apply { rowCount = 3; columnCount = 3 }
        val cells = ArrayList<TextView>()
        for (i in 0 until 9) {
            val cell = TextView(this).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_px_chip)
                isSelected = i == 4
                layoutParams = GridLayout.LayoutParams().apply { width = dp(44); height = dp(44); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener {
                    ax = i % 3; ay = i / 3
                    cells.forEachIndexed { k, c -> c.isSelected = k == i }
                }
            }
            cells.add(cell)
            anchors.addView(cell)
        }
        val anchorBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        anchorBox.addView(sectionLabel(R.string.px_canvas_anchor))
        anchorBox.addView(anchors)

        root.addView(sectionLabel(R.string.px_canvas_mode))
        val modes = ChoiceRow(
            this,
            listOf(false to getString(R.string.px_canvas_crop) as CharSequence, true to getString(R.string.px_canvas_scale) as CharSequence),
            false,
        ) { v -> scaleMode = v; anchorBox.visibility = if (v) View.GONE else View.VISIBLE }
        root.addView(modes.view)
        root.addView(anchorBox)

        root.addView(primaryButton(getString(R.string.px_apply)) {
            val nw = (wField.text.toString().toIntOrNull() ?: doc.width).coerceIn(1, Document.MAX_SIDE)
            val nh = (hField.text.toString().toIntOrNull() ?: doc.height).coerceIn(1, Document.MAX_SIDE)
            dlg.dismiss()
            if (scaleMode) session.scaleCanvas(nw, nh) else session.resizeCanvas(nw, nh, ax, ay)
            onApplied()
        })
        // Transformations de toute la toile, sans changer sa taille.
        root.addView(sectionLabel(R.string.px_canvas_transform))
        val transforms = LinearLayout(this)
        transforms.addView(secondaryButton(getString(R.string.px_flip_h), R.drawable.ic_px_flip_h) { dlg.dismiss(); session.flipCanvas(true); onApplied() })
        transforms.addView(secondaryButton(getString(R.string.px_flip_v), R.drawable.ic_px_flip_v) { dlg.dismiss(); session.flipCanvas(false); onApplied() })
        root.addView(transforms)
        val rot = LinearLayout(this)
        rot.addView(secondaryButton(getString(R.string.px_rotate_ccw), R.drawable.ic_px_rot_ccw) { dlg.dismiss(); session.rotateCanvas(false); onApplied() })
        rot.addView(secondaryButton(getString(R.string.px_rotate_cw), R.drawable.ic_px_rot_cw) { dlg.dismiss(); session.rotateCanvas(true); onApplied() })
        root.addView(rot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }
    dialog.show()
}

// ---- Fond et quadrillage -----------------------------------------------------------------------------------

fun Context.showBackgroundSheet(
    style: BackgroundStyle,
    pixelGrid: Boolean,
    tileGrid: Int,
    onChange: (BackgroundStyle, Boolean, Int) -> Unit,
) {
    var st = style
    var pg = pixelGrid
    var tg = tileGrid
    val dialog = bottomSheet(getString(R.string.px_bg_title)) { root, _ ->
        root.addView(sectionLabel(R.string.px_bg_style))
        val styles = listOf(
            BackgroundStyle.CHECKER_DARK to R.string.px_bg_checker_dark,
            BackgroundStyle.CHECKER_LIGHT to R.string.px_bg_checker_light,
            BackgroundStyle.WHITE to R.string.px_bg_white,
            BackgroundStyle.GRAY to R.string.px_bg_gray,
            BackgroundStyle.BLACK to R.string.px_bg_black,
        ).map { it.first to getString(it.second) as CharSequence }
        root.addView(ChoiceRow(this, styles, st) { st = it; onChange(st, pg, tg) }.view)

        root.addView(sectionLabel(R.string.px_bg_pixel_grid))
        root.addView(ChoiceRow(
            this,
            listOf(true to getString(R.string.px_on) as CharSequence, false to getString(R.string.px_off) as CharSequence),
            pg,
        ) { pg = it; onChange(st, pg, tg) }.view)

        root.addView(sectionLabel(R.string.px_bg_tile_grid))
        root.addView(ChoiceRow(
            this,
            listOf(0, 8, 16, 32, 64).map { it to (if (it == 0) getString(R.string.px_off) else "$it") as CharSequence },
            tg,
        ) { tg = it; onChange(st, pg, tg) }.view)
    }
    dialog.show()
}

// ---- Forme -------------------------------------------------------------------------------------------------------

fun Context.showShapeSheet(current: ShapeKind, onPick: (ShapeKind) -> Unit) {
    val dialog = bottomSheet(getString(R.string.px_shape_title)) { root, dlg ->
        val grid = GridLayout(this).apply { columnCount = 5 }
        for ((kind, icon) in SHAPE_ICONS) {
            val ch = TextView(this).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_px_chip)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0)
                compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.px_icon_tint)
                text = getString(shapeLabel(kind))
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.audio_text_secondary))
                setPadding(dp(2), dp(10), dp(2), dp(8))
                isSelected = kind == current
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f),
                ).apply { width = 0; setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener { dlg.dismiss(); onPick(kind) }
            }
            grid.addView(ch)
        }
        root.addView(grid)
    }
    dialog.show()
}

// ---- Réglages d'un calque ---------------------------------------------------------------------------------------

fun Context.showLayerSheet(session: EditorSession, layerIndex: Int, onDone: () -> Unit) {
    val layer = session.doc.layers.getOrNull(layerIndex) ?: return
    var name = layer.name
    var opacity = layer.opacity * 100 / 255
    var blend = layer.blend
    val dialog = bottomSheet(getString(R.string.px_layer_props)) { root, dlg ->
        val nameField = EditText(this).apply {
            setText(name)
            setSingleLine()
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
        }
        root.addView(nameField)
        root.addView(LabeledSlider(this, getString(R.string.px_opt_opacity), 0, 100, opacity, { getString(R.string.px_opt_percent, it) }) { opacity = it }
            .apply { setPadding(0, dp(12), 0, 0) })
        root.addView(sectionLabel(R.string.px_blend))
        val blends = listOf(
            BlendMode.NORMAL to R.string.px_blend_normal,
            BlendMode.MULTIPLY to R.string.px_blend_multiply,
            BlendMode.SCREEN to R.string.px_blend_screen,
            BlendMode.ADD to R.string.px_blend_add,
        ).map { it.first to getString(it.second) as CharSequence }
        root.addView(ChoiceRow(this, blends, blend) { blend = it }.view)
        root.addView(primaryButton(getString(R.string.px_apply)) {
            dlg.dismiss()
            name = nameField.text.toString().trim().ifEmpty { layer.name }
            session.editLayer(layer.id) {
                it.name = name
                it.opacity = (opacity * 255 / 100).coerceIn(0, 255)
                it.blend = blend
            }
            onDone()
        })
    }
    dialog.show()
}

// ---- Export ------------------------------------------------------------------------------------------------------------

enum class ExportFormat { PNG, GIF, JPEG, WEBP, SHEET }
enum class ExportTarget { FILE, GALLERY, SHARE }

class ExportRequest(
    val format: ExportFormat,
    val scale: Int,
    val gif: ImageExport.GifOptions,
    val sheetColumns: Int,
    val target: ExportTarget,
) {
    val imageFormat: ImageFormat get() = when (format) {
        ExportFormat.GIF -> ImageFormat.GIF
        ExportFormat.JPEG -> ImageFormat.JPEG
        ExportFormat.WEBP -> ImageFormat.WEBP
        else -> ImageFormat.PNG
    }
}

fun Context.showExportSheet(
    doc: Document,
    initialFormat: ExportFormat,
    initialScale: Int,
    pickBackground: (Int?, (Int?) -> Unit) -> Unit,
    onExport: (ExportRequest) -> Unit,
) {
    var format = initialFormat
    var scale = initialScale.coerceIn(1, ImageExport.maxScaleFor(doc.width, doc.height))
    var loops = 0
    var direction = ImageExport.GifDirection.FORWARD
    var background: Int? = null
    var speed = 100
    var columns = if (doc.frames.size > 1) minOf(doc.frames.size, 8) else 1
    lateinit var sizeText: TextView
    lateinit var gifBox: LinearLayout
    lateinit var sheetBox: LinearLayout
    lateinit var scaleSlider: LabeledSlider

    fun refresh() {
        val maxScale = ImageExport.maxScaleFor(doc.width, doc.height)
        scale = scale.coerceIn(1, maxScale)
        val (w, h) = if (format == ExportFormat.SHEET) {
            val cols = columns.coerceIn(1, doc.frames.size)
            val rows = (doc.frames.size + cols - 1) / cols
            doc.width * cols * scale to doc.height * rows * scale
        } else doc.width * scale to doc.height * scale
        sizeText.text = getString(R.string.px_export_size, w, h)
        gifBox.visibility = if (format == ExportFormat.GIF) View.VISIBLE else View.GONE
        sheetBox.visibility = if (format == ExportFormat.SHEET) View.VISIBLE else View.GONE
    }

    val dialog = bottomSheet(getString(R.string.px_export_title)) { root, dlg ->
        root.addView(sectionLabel(R.string.px_export_format))
        val formats = listOf(
            ExportFormat.PNG to "PNG", ExportFormat.GIF to "GIF", ExportFormat.SHEET to getString(R.string.px_export_sheet),
            ExportFormat.JPEG to "JPEG", ExportFormat.WEBP to "WebP",
        ).map { it.first to it.second as CharSequence }
        root.addView(ChoiceRow(this, formats, format) { format = it; refresh() }.view)

        scaleSlider = LabeledSlider(this, getString(R.string.px_export_scale), 1, ImageExport.maxScaleFor(doc.width, doc.height).coerceAtLeast(2), scale, { "×$it" }) {
            scale = it; refresh()
        }
        scaleSlider.setPadding(0, dp(14), 0, 0)
        root.addView(scaleSlider)
        sizeText = label("", 12f)
        root.addView(sizeText)

        gifBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gifBox.addView(sectionLabel(R.string.px_gif_loops))
        gifBox.addView(ChoiceRow(this, listOf(0 to getString(R.string.px_gif_loop_forever) as CharSequence, 1 to getString(R.string.px_gif_loop_once) as CharSequence), loops) { loops = it }.view)
        gifBox.addView(sectionLabel(R.string.px_gif_direction))
        gifBox.addView(ChoiceRow(
            this,
            listOf(
                ImageExport.GifDirection.FORWARD to getString(R.string.px_gif_forward) as CharSequence,
                ImageExport.GifDirection.REVERSE to getString(R.string.px_gif_reverse) as CharSequence,
                ImageExport.GifDirection.PING_PONG to getString(R.string.px_gif_pingpong) as CharSequence,
            ),
            direction,
        ) { direction = it }.view)
        gifBox.addView(LabeledSlider(this, getString(R.string.px_gif_speed), 25, 300, speed, { getString(R.string.px_opt_percent, it) }) { speed = it }
            .apply { setPadding(0, dp(12), 0, 0) })
        gifBox.addView(sectionLabel(R.string.px_gif_background))
        val bgRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val swatch = SwatchView(this).apply { color = 0xFFFFFFFF.toInt(); radiusDp = 8f }
        val transparentChip = chip(getString(R.string.px_gif_bg_transparent)) { }
        transparentChip.isSelected = true
        transparentChip.setOnClickListener {
            background = null
            transparentChip.isSelected = true
            swatch.ring = false
        }
        swatch.setOnClickListener {
            pickBackground(background ?: swatch.color) { c ->
                if (c != null) {
                    background = c
                    swatch.color = c
                    swatch.ring = true
                    transparentChip.isSelected = false
                }
            }
        }
        bgRow.addView(transparentChip)
        bgRow.addView(swatch, LinearLayout.LayoutParams(dp(38), dp(34)).apply { marginStart = dp(6) })
        gifBox.addView(bgRow)
        root.addView(gifBox)

        sheetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheetBox.addView(LabeledSlider(this, getString(R.string.px_sheet_columns), 1, doc.frames.size.coerceAtLeast(2), columns, { "$it" }) {
            columns = it; refresh()
        }.apply { setPadding(0, dp(12), 0, 0) })
        root.addView(sheetBox)

        fun go(t: ExportTarget) {
            dlg.dismiss()
            onExport(ExportRequest(format, scale, ImageExport.GifOptions(scale, loops, direction, background, speed), columns.coerceIn(1, doc.frames.size), t))
        }
        root.addView(primaryButton(getString(R.string.px_save_as)) { go(ExportTarget.FILE) })
        val row = LinearLayout(this)
        row.addView(secondaryButton(getString(R.string.px_to_gallery), R.drawable.ic_px_image) { go(ExportTarget.GALLERY) })
        row.addView(secondaryButton(getString(R.string.px_share), R.drawable.ic_px_share) { go(ExportTarget.SHARE) })
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        refresh()
    }
    dialog.show()
}

// ---- Palettes ------------------------------------------------------------------------------------------------------------

class PaletteActions(
    val onSelect: (NamedPalette) -> Unit,
    val onNew: () -> Unit,
    val onFromImage: () -> Unit,
    val onImport: () -> Unit,
    val onExport: (NamedPalette) -> Unit,
    val onRename: (NamedPalette) -> Unit,
    val onDelete: (NamedPalette) -> Unit,
)

fun Context.showPaletteSheet(palettes: List<NamedPalette>, currentId: String, actions: PaletteActions) {
    val dialog = bottomSheet(getString(R.string.px_palette_title)) { root, dlg ->
        for (p in palettes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_px_selected_item)
                isSelected = p.id == currentId
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
            }
            val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(label(p.title(this@showPaletteSheet), 14f, true).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(label("${p.colors.size}", 12f))
            if (!p.builtIn) {
                head.addView(iconButton(R.drawable.ic_px_rename, R.string.px_rename, 34, 8) { dlg.dismiss(); actions.onRename(p) })
                head.addView(iconButton(R.drawable.ic_px_delete, R.string.px_delete, 34, 8) { dlg.dismiss(); actions.onDelete(p) })
            }
            head.addView(iconButton(R.drawable.ic_px_export, R.string.px_export_palette, 34, 8) { dlg.dismiss(); actions.onExport(p) })
            row.addView(head)
            val strip = LinearLayout(this).apply { setPadding(0, dp(6), 0, 0) }
            for (c in p.colors.take(24)) {
                strip.addView(SwatchView(this).apply { color = c; radiusDp = 3f }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(2) })
            }
            row.addView(scrollRow(strip))
            row.setOnClickListener { dlg.dismiss(); actions.onSelect(p) }
            root.addView(row)
        }
        val row = LinearLayout(this)
        row.addView(secondaryButton(getString(R.string.px_palette_new), R.drawable.ic_px_add) { dlg.dismiss(); actions.onNew() })
        row.addView(secondaryButton(getString(R.string.px_palette_from_image), R.drawable.ic_px_image) { dlg.dismiss(); actions.onFromImage() })
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val row2 = LinearLayout(this)
        row2.addView(secondaryButton(getString(R.string.px_palette_import), R.drawable.ic_px_import) { dlg.dismiss(); actions.onImport() })
        root.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }
    dialog.show()
}

// ---- Durée d'une image -----------------------------------------------------------------------------------------------

fun Context.showDurationSheet(currentMs: Int, frameCount: Int, onApply: (ms: Int, all: Boolean) -> Unit) {
    var ms = currentMs
    val dialog = bottomSheet(getString(R.string.px_duration_title)) { root, dlg ->
        root.addView(LabeledSlider(this, getString(R.string.px_duration), 20, 1000, ms.coerceIn(20, 1000), { getString(R.string.px_ms, it) }) { ms = it })
        val fps = ChoiceRow(this, listOf(6, 8, 10, 12, 15, 24).map { it to getString(R.string.px_fps_value, it) as CharSequence }, -1) { f ->
            ms = 1000 / f
        }
        root.addView(sectionLabel(R.string.px_fps))
        root.addView(fps.view)
        val row = LinearLayout(this)
        row.addView(secondaryButton(getString(R.string.px_apply_this)) { dlg.dismiss(); onApply(ms, false) })
        if (frameCount > 1) row.addView(secondaryButton(getString(R.string.px_apply_all)) { dlg.dismiss(); onApply(ms, true) })
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })
    }
    dialog.show()
}

// ---- Planche de sprites --------------------------------------------------------------------------------------------------

fun Context.showSheetImportSheet(imageW: Int, imageH: Int, onImport: (cols: Int, rows: Int) -> Unit) {
    val colChoices = ImageExport.divisors(imageW)
    val rowChoices = ImageExport.divisors(imageH)
    var cols = colChoices.getOrNull(colChoices.size / 2) ?: 1
    var rows = 1
    lateinit var info: TextView
    fun refresh() { info.text = getString(R.string.px_sheet_cell_info, imageW / cols, imageH / rows, cols * rows) }
    val dialog = bottomSheet(getString(R.string.px_sheet_import_title)) { root, dlg ->
        root.addView(label(getString(R.string.px_sheet_image_size, imageW, imageH), 12f))
        root.addView(sectionLabel(R.string.px_sheet_columns))
        root.addView(ChoiceRow(this, colChoices.map { it to "$it" as CharSequence }, cols) { cols = it; refresh() }.view)
        root.addView(sectionLabel(R.string.px_sheet_rows))
        root.addView(ChoiceRow(this, rowChoices.map { it to "$it" as CharSequence }, rows) { rows = it; refresh() }.view)
        info = label("", 13f, true).apply { setPadding(dp(2), dp(14), 0, 0) }
        root.addView(info)
        root.addView(primaryButton(getString(R.string.px_create)) { dlg.dismiss(); onImport(cols, rows) })
        refresh()
    }
    dialog.show()
}
