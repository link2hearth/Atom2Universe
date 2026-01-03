package com.example.atom2univers.pixelart

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.atom2univers.R
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PixelArtEditorActivity : AppCompatActivity() {

    private lateinit var pixelCanvas: PixelCanvas
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var txtZoom: TextView
    private lateinit var txtCanvasSize: TextView
    private lateinit var colorPrimary: View
    private lateinit var colorSecondary: View
    private lateinit var colorPalette: LinearLayout
    private lateinit var toolGroup: RadioGroup

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

    // Current custom palette
    private val customPalette = mutableListOf<Int>()
    private val PREFS_NAME = "PixelArtPrefs"
    private val PALETTE_KEY = "custom_palette"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pixel_art_editor)

        initViews()
        setupToolbar()
        setupTools()
        setupColors()
        setupSizePresets()
        setupZoomControls()
        setupCanvas()
    }

    private fun initViews() {
        pixelCanvas = findViewById(R.id.pixel_canvas)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        txtZoom = findViewById(R.id.txt_zoom)
        txtCanvasSize = findViewById(R.id.txt_canvas_size)
        colorPrimary = findViewById(R.id.color_primary)
        colorSecondary = findViewById(R.id.color_secondary)
        colorPalette = findViewById(R.id.color_palette)
        toolGroup = findViewById(R.id.tool_group)
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnUndo.setOnClickListener {
            pixelCanvas.undo()
        }

        btnRedo.setOnClickListener {
            pixelCanvas.redo()
        }

        findViewById<ImageButton>(R.id.btn_clear).setOnClickListener {
            showClearConfirmDialog()
        }

        findViewById<ImageButton>(R.id.btn_export).setOnClickListener {
            exportImage()
        }

        updateCanvasSizeText()
    }

    private fun setupTools() {
        val chkFilled = findViewById<CheckBox>(R.id.chk_filled)

        toolGroup.setOnCheckedChangeListener { _, checkedId ->
            pixelCanvas.currentTool = when (checkedId) {
                R.id.tool_pencil -> PixelCanvas.Tool.PENCIL
                R.id.tool_eraser -> PixelCanvas.Tool.ERASER
                R.id.tool_fill -> PixelCanvas.Tool.FILL
                R.id.tool_picker -> PixelCanvas.Tool.PICKER
                R.id.tool_line -> PixelCanvas.Tool.LINE
                R.id.tool_rectangle -> PixelCanvas.Tool.RECTANGLE
                R.id.tool_circle -> PixelCanvas.Tool.CIRCLE
                else -> PixelCanvas.Tool.PENCIL
            }
        }

        chkFilled.setOnCheckedChangeListener { _, isChecked ->
            pixelCanvas.shapeFilled = isChecked
        }
    }

    private fun setupColors() {
        // Load saved palette
        loadPalette()

        // Build palette UI
        rebuildPaletteUI()

        // Swap colors button
        findViewById<ImageButton>(R.id.btn_swap_colors).setOnClickListener {
            pixelCanvas.swapColors()
            updateColorViews()
        }

        // Add color to palette button
        findViewById<ImageButton>(R.id.btn_add_color).setOnClickListener {
            addColorToPalette(pixelCanvas.primaryColor)
        }

        // Reset palette button
        findViewById<ImageButton>(R.id.btn_reset_palette).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.pixel_art_palette_reset_confirm)
                .setPositiveButton("Oui") { _, _ ->
                    resetPalette()
                }
                .setNegativeButton("Non", null)
                .show()
        }

        // Color picker on primary color click
        colorPrimary.setOnClickListener {
            showColorPickerDialog(pixelCanvas.primaryColor) { color ->
                pixelCanvas.primaryColor = color
                updateColorViews()
            }
        }

        colorSecondary.setOnClickListener {
            showColorPickerDialog(pixelCanvas.secondaryColor) { color ->
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
                } catch (e: NumberFormatException) {
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

        for ((index, color) in customPalette.withIndex()) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                    marginEnd = 4
                }
                setBackgroundColor(color)
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
            Toast.makeText(this, "Cette couleur est deja dans la palette", Toast.LENGTH_SHORT).show()
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
        savePalette()
        rebuildPaletteUI()
    }

    private fun updateColorViews() {
        colorPrimary.setBackgroundColor(pixelCanvas.primaryColor)
        colorSecondary.setBackgroundColor(pixelCanvas.secondaryColor)
    }

    private fun setupSizePresets() {
        val btn16 = findViewById<MaterialButton>(R.id.btn_size_16)
        val btn32 = findViewById<MaterialButton>(R.id.btn_size_32)
        val btn64 = findViewById<MaterialButton>(R.id.btn_size_64)
        val btn128 = findViewById<MaterialButton>(R.id.btn_size_128)

        val buttons = listOf(btn16 to 16, btn32 to 32, btn64 to 64, btn128 to 128)

        buttons.forEach { (button, size) ->
            button.setOnClickListener {
                pixelCanvas.setCanvasSize(size, size)
                updateCanvasSizeText()
                updateSizeButtonStates(size)
            }
        }
    }

    private fun updateSizeButtonStates(activeSize: Int) {
        val btn16 = findViewById<MaterialButton>(R.id.btn_size_16)
        val btn32 = findViewById<MaterialButton>(R.id.btn_size_32)
        val btn64 = findViewById<MaterialButton>(R.id.btn_size_64)
        val btn128 = findViewById<MaterialButton>(R.id.btn_size_128)

        val buttons = listOf(btn16 to 16, btn32 to 32, btn64 to 64, btn128 to 128)

        buttons.forEach { (button, size) ->
            val strokeColor = if (size == activeSize) Color.parseColor("#4CAF50") else Color.parseColor("#666666")
            button.strokeColor = android.content.res.ColorStateList.valueOf(strokeColor)
        }
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

    private fun setupCanvas() {
        pixelCanvas.onHistoryChanged = { canUndo, canRedo ->
            btnUndo.isEnabled = canUndo
            btnRedo.isEnabled = canRedo
            btnUndo.imageTintList = android.content.res.ColorStateList.valueOf(
                if (canUndo) Color.WHITE else Color.parseColor("#888888")
            )
            btnRedo.imageTintList = android.content.res.ColorStateList.valueOf(
                if (canRedo) Color.WHITE else Color.parseColor("#888888")
            )
        }

        pixelCanvas.onColorPicked = { color ->
            updateColorViews()
            // Switch back to pencil tool after picking
            toolGroup.check(R.id.tool_pencil)
        }

        pixelCanvas.onZoomChanged = { zoom ->
            updateZoomText()
        }
    }

    private fun updateZoomText() {
        val zoomPercent = (pixelCanvas.getZoom() * 100).toInt()
        txtZoom.text = "$zoomPercent%"
    }

    private fun updateCanvasSizeText() {
        txtCanvasSize.text = getString(
            R.string.pixel_art_canvas_size,
            pixelCanvas.getCanvasWidth(),
            pixelCanvas.getCanvasHeight()
        )
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pixel_art_action_clear)
            .setMessage("Effacer tout le dessin ?")
            .setPositiveButton("Oui") { _, _ ->
                pixelCanvas.clearCanvas()
            }
            .setNegativeButton("Non", null)
            .show()
    }

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
            .setPositiveButton("OK") { _, _ ->
                onColorSelected(Color.HSVToColor(currentAlpha, hsv))
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun exportImage() {
        val bitmap = pixelCanvas.exportToBitmap()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "PixelArt_$timestamp.png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelArt")
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(this, "Image sauvegardee: $filename", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Legacy storage for older Android versions
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val pixelArtDir = File(picturesDir, "PixelArt")
                if (!pixelArtDir.exists()) {
                    pixelArtDir.mkdirs()
                }

                val file = File(pixelArtDir, filename)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, "Image sauvegardee: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de la sauvegarde: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
