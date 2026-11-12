package com.Atom2Universe.app.gallery

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.SimpleColorPickerDialog
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode
import java.util.Locale

class PhotoEditorActivity : ThemedActivity() {
    companion object {
        internal const val NAME = "photo_name"
        internal const val PARENT = "photo_parent"
        internal const val RESULT_ID = "edited_id"
    }
    private val model by lazy { ViewModelProvider(this)[PhotoEditorModel::class.java] }
    private val name by lazy { intent.getStringExtra(NAME) ?: getString(R.string.gallery_image) }
    private lateinit var surface: PhotoEditorCanvas
    private lateinit var controls: LinearLayout
    private lateinit var options: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var hint: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var undo: ImageButton
    private lateinit var redo: ImageButton
    private lateinit var save: ImageButton
    private var choosing = false
    private var dialog: AlertDialog? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        choosing = false
        val data = result.data
        val tree = data?.data
        if (result.resultCode == RESULT_OK && tree != null && model.prepared != null) {
            try {
                contentResolver.takePersistableUriPermission(tree, data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                write(DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)))
            } catch (_: SecurityException) { model.error = true; refresh() }
        } else { model.cancelExport(); refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        choosing = savedInstanceState?.getBoolean("choosing") == true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(context)
        }
        val top = panel()
        top.action(R.drawable.ic_arrow_back_24,R.string.back,::leave)
        top.heading().apply { text = name; tooltipText = name }
        undo = top.action(R.drawable.ic_px_undo,R.string.photo_undo) { model.history(-1) }
        redo = top.action(R.drawable.ic_px_redo,R.string.photo_redo) { model.history(1) }
        save = top.action(R.drawable.ic_save,R.string.photo_save_copy) { model.prepare() }
        root.addView(top,-1,-2)
        val stage = FrameLayout(this)
        surface = PhotoEditorCanvas(this,model,::gesture)
        stage.addView(surface,FrameLayout.LayoutParams(-1,-1))
        spinner = ProgressBar(this)
        stage.addView(spinner,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER))
        root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        controls = panel().apply { orientation = LinearLayout.VERTICAL }
        root.addView(controls,-1,-2)
        hint = TextView(this).apply {
            setTextColor(galleryColor(R.attr.a2uSecondaryTextColor))
            textSize = if (tablet()) 16f else 14f
            setPadding(dp(12),dp(4),dp(12),0)
        }
        controls.addView(hint,-1,-2)
        options = scrollRow(controls)
        tools = scrollRow(root).apply { background = AppearanceStyle.drawable(context,R.attr.a2uPanelBackground) }
        setContentView(root)
        enableImmersiveMode()
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        model.updates.observe(this) { refresh() }
        model.open(uri,intent.getStringExtra(PARENT)?.let(Uri::parse))
    }

    private fun panel() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = AppearanceStyle.drawable(context,R.attr.a2uPanelBackground)
    }
    private fun scrollRow(parent: LinearLayout): LinearLayout {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true }
        parent.addView(scroll,-1,-2)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER
            scroll.addView(this)
        }
    }
    private fun item(row: LinearLayout, label: Int, selected: Boolean = false, icon: Int = 0, action: () -> Unit): TextView {
        val color = galleryColor(if (selected) R.attr.a2uMidiAccent else R.attr.a2uTextColor)
        return TextView(this).apply {
            setText(label)
            textSize = if (tablet()) 16f else 14f
            setTextColor(color)
            isSelected = selected
            isFocusable = true
            gravity = Gravity.CENTER
            setPadding(dp(10),dp(4),dp(10),dp(4))
            minHeight = dp(44)
            if (icon != 0) {
                val drawable = requireNotNull(getDrawable(icon)).mutate().apply {
                    setTint(color); setBounds(0,0,dp(20),dp(20))
                }
                setCompoundDrawables(null,drawable,null,null)
            }
            background = AppearanceStyle.drawable(context,androidx.appcompat.R.attr.selectableItemBackground)
            setOnClickListener { if (!model.busy) action() }
            row.addView(this,-2,-2)
        }
    }
    private fun enabled(view: View, value: Boolean) { view.isEnabled = value; view.alpha = if (value) 1f else 0.35f }
    private fun actions() {
        val ready = !model.busy && model.preview != null
        enabled(undo,ready && (model.canUndo || model.draft != null))
        enabled(redo,ready && model.canRedo && model.draft == null)
        enabled(save,ready && model.dirty)
        spinner.visibility = if (model.busy) View.VISIBLE else View.GONE
    }
    private fun refresh() {
        if (isFinishing) return
        model.saved?.let { photo ->
            setResult(RESULT_OK, Intent().putExtra(RESULT_ID,photo.id).putExtra(PARENT,photo.parent?.toString()))
            finish()
            return
        }
        actions()
        surface.invalidate()
        if (model.busy) return
        if (model.error) {
            model.error = false
            val failedLoad = model.preview == null
            show(AlertDialog.Builder(this).setMessage(if (failedLoad) R.string.gallery_unreadable else R.string.gallery_operation_failed)
                .setOnCancelListener { if (failedLoad) finish() else surface.post { refresh() } }
                .setPositiveButton(R.string.ok) { _,_ -> if (failedLoad) finish() else surface.post { refresh() } })
            return
        }
        rebuildTools()
        if (model.prepared != null && !choosing && dialog?.isShowing != true) exportDialog()
    }
    private fun switch(tab: EditTab) {
        if (model.tab == tab) return
        val pending = model.draft
        model.clearDraft()
        model.tab = tab
        (options.parent as HorizontalScrollView).scrollTo(0,0)
        model.tool = when (tab) {
            EditTab.FRAME -> EditTool.CROP
            EditTab.SELECT -> EditTool.RECT
            EditTab.CUTOUT -> EditTool.SUBJECT
            EditTab.DRAW -> EditTool.BRUSH
            else -> EditTool.PAN
        }
        if (pending != null) model.edit(pending) else refresh()
    }
    private fun tool(mode: EditTool, label: Int) = item(options,label,model.tool == mode) {
        model.tool = mode; refresh()
    }
    private fun rebuildTools() {
        tools.removeAllViews()
        fun tab(t: EditTab, label: Int, icon: Int) { item(tools,label,model.tab == t,icon) { switch(t) } }
        tab(EditTab.FRAME,R.string.photo_frame,R.drawable.ic_px_crop)
        tab(EditTab.LIGHT,R.string.photo_adjust,R.drawable.ic_px_tune)
        tab(EditTab.LOOK,R.string.photo_filters,R.drawable.ic_px_palette)
        tab(EditTab.SELECT,R.string.photo_selection,R.drawable.ic_px_sel_lasso)
        tab(EditTab.CUTOUT,R.string.photo_cutout,R.drawable.ic_px_cut)
        tab(EditTab.DRAW,R.string.photo_draw,R.drawable.ic_px_brush)
        options.removeAllViews()
        // The slider, when present, is the only extra row under the options.
        if (controls.childCount > 2) controls.removeViewAt(2)
        when (model.tab) {
            EditTab.FRAME -> {
                tool(EditTool.CROP,R.string.photo_crop)
                item(options,R.string.photo_rotate) { model.edit(PhotoEdit.Rotate,false) }
                item(options,R.string.photo_mirror) { model.edit(PhotoEdit.Mirror,false) }
            }
            EditTab.LIGHT -> {
                val channels = listOf(R.string.photo_light,R.string.photo_contrast,R.string.photo_saturation)
                channels.forEachIndexed { i,label -> item(options,label,model.channel == i) { model.channel = i; refresh() } }
                applyButton()
                slider(-100,100,model.adjustments[model.channel],channels[model.channel]) {
                    model.adjustments[model.channel] = it; actions(); surface.invalidate()
                }
            }
            EditTab.LOOK -> {
                for (look in PhotoLook.entries) item(options,look.label,model.look == look) {
                    model.look = if (model.look == look) null else look; refresh()
                }
                applyButton()
            }
            EditTab.SELECT -> {
                tool(EditTool.RECT,R.string.photo_rect)
                tool(EditTool.LASSO,R.string.photo_lasso)
                tool(EditTool.WAND,R.string.photo_wand)
                refineTools()
                item(options,R.string.photo_clear) { model.selectMask { null } }
            }
            EditTab.CUTOUT -> {
                if (model.selection != null) {
                    item(options,R.string.photo_keep,true) { model.selection?.let { model.edit(PhotoEdit.Cut(it,true),false) } }
                    item(options,R.string.photo_remove) { model.selection?.let { model.edit(PhotoEdit.Cut(it,false),false) } }
                }
                tool(EditTool.SUBJECT,R.string.photo_subject)
                tool(EditTool.BACKGROUND,R.string.photo_background)
                tool(EditTool.LASSO,R.string.photo_lasso)
                refineTools()
            }
            EditTab.DRAW -> {
                tool(EditTool.BRUSH,R.string.photo_brush)
                tool(EditTool.ERASER,R.string.photo_eraser)
                item(options,R.string.photo_color) {
                    SimpleColorPickerDialog(this,String.format(Locale.ROOT,"#%06X",model.ink and 0xffffff),
                        showTextMode = false) { hex,_ -> model.ink = Color.parseColor(hex); refresh() }.show()
                }.apply {
                    val swatch = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(model.ink); setStroke(dp(1),galleryColor(R.attr.a2uOutlineColor))
                        setBounds(0,0,dp(16),dp(16))
                    }
                    compoundDrawablePadding = dp(5)
                    setCompoundDrawablesRelative(swatch,null,null,null)
                }
            }
        }
        when (model.tool) {
            EditTool.WAND, EditTool.BACKGROUND -> slider(0,150,model.tolerance,R.string.photo_tolerance) { model.tolerance = it }
            EditTool.ADD, EditTool.SUBTRACT, EditTool.BRUSH, EditTool.ERASER ->
                slider(1,18,model.brushSize,R.string.photo_size) { model.brushSize = it }
            else -> Unit
        }
        val help = when (model.tool) {
            EditTool.CROP -> R.string.photo_hint_crop
            EditTool.RECT, EditTool.LASSO -> R.string.photo_hint_trace
            EditTool.WAND -> R.string.photo_hint_wand
            EditTool.SUBJECT -> R.string.photo_hint_subject
            EditTool.BACKGROUND -> R.string.photo_hint_background
            EditTool.ADD, EditTool.SUBTRACT -> R.string.photo_hint_refine
            else -> if (model.selection != null) R.string.photo_hint_local else 0
        }
        hint.visibility = if (help != 0) View.VISIBLE else View.GONE
        if (help != 0) hint.setText(help)
    }
    private fun refineTools() {
        tool(EditTool.ADD,R.string.photo_mask_add)
        enabled(tool(EditTool.SUBTRACT,R.string.photo_mask_subtract),model.selection != null)
    }
    private fun applyButton() {
        item(options,R.string.photo_apply) { model.draft?.let { model.clearDraft(); model.edit(it) } }
    }
    private fun slider(min: Int,max: Int,value: Int,label: Int,changed: (Int) -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),0,dp(12),0) }
        if (model.tab != EditTab.LIGHT) row.addView(TextView(this).apply {
            setText(label); setTextColor(galleryColor(R.attr.a2uSecondaryTextColor))
        })
        val readout = TextView(this).apply {
            setTextColor(galleryColor(R.attr.a2uTextColor)); text = getString(R.string.photo_value,value)
        }
        val bar = SeekBar(this).apply {
            this.max = max-min; progress = value-min; contentDescription = getString(label)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                override fun onProgressChanged(seekBar: SeekBar,p: Int,user: Boolean) {
                    if (user && !model.busy) { changed(p+min); readout.text = getString(R.string.photo_value,p+min) }
                }
            })
        }
        row.addView(bar,LinearLayout.LayoutParams(0,dp(40),1f))
        row.addView(readout,dp(46),-2)
        controls.addView(row,-1,-2)
    }
    private fun gesture(tool: EditTool,trace: Trace) {
        val previous = model.selection
        when (tool) {
            EditTool.CROP -> model.edit(PhotoEdit.Crop(trace.bounds()),false)
            EditTool.RECT, EditTool.LASSO -> model.selectMask { PhotoMask(listOf(MaskPart.Shape(trace.copy(width=0f)))) }
            EditTool.WAND -> model.selectMask { PhotoSelections.wand(it,trace.points.first(),model.tolerance) }
            EditTool.SUBJECT -> model.selectMask { PhotoSelections.subject(it,trace.bounds()) }
            EditTool.ADD, EditTool.SUBTRACT -> if (tool == EditTool.ADD || previous != null) model.selectMask {
                PhotoMask(previous?.parts.orEmpty() + MaskPart.Shape(trace,tool == EditTool.SUBTRACT))
            }
            EditTool.BACKGROUND -> model.removeBackground(trace)
            EditTool.BRUSH, EditTool.ERASER -> model.edit(PhotoEdit.Ink(trace,model.ink,tool == EditTool.ERASER,previous))
            EditTool.PAN -> Unit
        }
    }
    private fun show(builder: AlertDialog.Builder) {
        dialog = builder.create().apply { followImmersiveMode(); show() }
    }
    private fun chooseFolder() {
        choosing = true
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            model.writableParent?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI,it) }
        })
    }
    private fun write(parent: Uri) = model.write(parent,getString(R.string.photo_copy_name,name.substringBeforeLast('.',name)))

    private fun exportControls(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val formats = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        content.addView(formats)
        val buttons = PhotoFileFormat.entries.associateWith { format ->
            RadioButton(this).apply {
                id = View.generateViewId()
                setText(when (format) {
                    PhotoFileFormat.WEBP -> R.string.px_format_webp
                    PhotoFileFormat.PNG -> R.string.px_format_png
                })
                formats.addView(this)
            }
        }
        val quality = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(quality)
        val label = TextView(this).apply {
            text = getString(R.string.photo_export_quality, model.exportOptions.quality)
            setTextColor(galleryColor(R.attr.a2uTextColor))
        }
        quality.addView(label)
        quality.addView(SeekBar(this).apply {
            id = View.generateViewId()
            label.labelFor = id
            max = 100
            progress = model.exportOptions.quality
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    model.exportOptions = model.exportOptions.copy(quality = value)
                    label.text = getString(R.string.photo_export_quality, value)
                }
            })
        }, -1, dp(48))
        formats.setOnCheckedChangeListener { _, id ->
            val format = buttons.entries.firstOrNull { it.value.id == id }?.key ?: return@setOnCheckedChangeListener
            model.exportOptions = model.exportOptions.copy(format = format)
            quality.visibility = if (format == PhotoFileFormat.WEBP) View.VISIBLE else View.GONE
        }
        formats.check(buttons.getValue(model.exportOptions.format).id)
        return content
    }

    private fun exportDialog() {
        val image = model.prepared ?: return
        val parent = model.writableParent
        val message = getString(if (model.reduced) R.string.photo_export_reduced else R.string.photo_export_size,image.width,image.height)
        val builder = AlertDialog.Builder(this).setTitle(R.string.photo_save_copy).setMessage(message)
            .setView(exportControls())
            .setOnCancelListener { model.cancelExport() }
            .setNegativeButton(R.string.cancel) { _,_ -> model.cancelExport() }
        if (parent != null) {
            builder.setPositiveButton(R.string.photo_save_copy) { _,_ -> write(parent) }
            builder.setNeutralButton(R.string.gallery_destination) { _,_ -> chooseFolder() }
        } else builder.setPositiveButton(R.string.gallery_destination) { _,_ -> chooseFolder() }
        show(builder)
    }
    private fun leave() {
        if (model.busy) return
        if (!model.dirty) finish()
        else show(AlertDialog.Builder(this).setMessage(R.string.photo_leave_message)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.photo_leave) { _,_ -> finish() })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("choosing",choosing)
    }
}
