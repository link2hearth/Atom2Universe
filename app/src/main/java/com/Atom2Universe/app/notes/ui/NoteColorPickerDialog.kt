package com.Atom2Universe.app.notes.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.ui.view.ColorPickerHueView
import com.Atom2Universe.app.notes.ui.view.ColorPickerSatValView

class NoteColorPickerDialog(
    context: Context,
    private val initialColorHex: String?,
    private val initialTextMode: String,
    private val onConfirm: (colorHex: String?, textColorMode: String) -> Unit
) : Dialog(context, R.style.Theme_A2U_Dialog) {

    private val favoritesManager = FavoriteColorsManager(context)
    private var selectedColor: String? = initialColorHex
    private var selectedTextMode = initialTextMode

    private lateinit var hueView: ColorPickerHueView
    private lateinit var satValView: ColorPickerSatValView
    private lateinit var hexInput: EditText
    private lateinit var previewSwatch: View
    private lateinit var favoritesRow: LinearLayout
    private lateinit var quickColorsRow: LinearLayout
    private lateinit var textModeGroup: RadioGroup
    private var suppressHexUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_notes_color_picker)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        hueView = findViewById(R.id.color_picker_hue)
        satValView = findViewById(R.id.color_picker_sat_val)
        hexInput = findViewById(R.id.color_hex_input)
        previewSwatch = findViewById(R.id.color_preview_swatch)
        favoritesRow = findViewById(R.id.color_favorites_row)
        quickColorsRow = findViewById(R.id.color_quick_colors_row)
        textModeGroup = findViewById(R.id.text_mode_radio_group)

        setupQuickColors()
        setupFavorites()
        setupHsvPicker()
        setupHexInput()
        setupTextMode()

        initialColorHex?.let { applyHexColor(it) }

        findViewById<Button>(R.id.btn_add_favorite).setOnClickListener {
            selectedColor?.let { favoritesManager.addFavorite(it, selectedTextMode) }
            setupFavorites()
        }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            onConfirm(selectedColor, selectedTextMode)
            dismiss()
        }
        findViewById<View>(R.id.btn_no_color).setOnClickListener {
            selectedColor = null
            previewSwatch.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun setupHsvPicker() {
        satValView.onColorChanged = { color ->
            selectedColor = colorToHex(color)
            suppressHexUpdate = true
            hexInput.setText(selectedColor?.removePrefix("#"))
            suppressHexUpdate = false
            previewSwatch.setBackgroundColor(color)
        }
        hueView.onHueChanged = { hue -> satValView.setHue(hue) }
    }

    private fun setupHexInput() {
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressHexUpdate) return
                val hex = s?.toString()?.trim() ?: return
                if (hex.length == 6) {
                    try {
                        val color = Color.parseColor("#$hex")
                        applyHexColor("#$hex")
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun applyHexColor(hex: String) {
        try {
            val color = Color.parseColor(hex)
            selectedColor = hex
            previewSwatch.setBackgroundColor(color)
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hueView.setHue(hsv[0])
            satValView.setHsv(hsv[0], hsv[1], hsv[2])
            suppressHexUpdate = true
            hexInput.setText(hex.removePrefix("#"))
            suppressHexUpdate = false
        } catch (_: Exception) {}
    }

    private fun setupTextMode() {
        val radioAuto = findViewById<RadioButton>(R.id.radio_text_auto)
        val radioWhite = findViewById<RadioButton>(R.id.radio_text_white)
        val radioBlack = findViewById<RadioButton>(R.id.radio_text_black)
        when (selectedTextMode) {
            "white" -> radioWhite.isChecked = true
            "black" -> radioBlack.isChecked = true
            else -> radioAuto.isChecked = true
        }
        textModeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedTextMode = when (checkedId) {
                R.id.radio_text_white -> "white"
                R.id.radio_text_black -> "black"
                else -> "auto"
            }
        }
    }

    private fun setupQuickColors() {
        val quickColors = listOf(
            "#EF4444","#F97316","#EAB308","#22C55E","#3B82F6",
            "#8B5CF6","#EC4899","#14B8A6","#F59E0B","#6B7280"
        )
        quickColorsRow.removeAllViews()
        quickColors.forEach { hex ->
            val swatch = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).also { it.marginEnd = 6.dp }
                setBackgroundColor(Color.parseColor(hex))
                setOnClickListener { applyHexColor(hex) }
            }
            quickColorsRow.addView(swatch)
        }
    }

    private fun setupFavorites() {
        val favorites = favoritesManager.getFavorites()
        favoritesRow.removeAllViews()
        favorites.forEach { fav ->
            try {
                val color = Color.parseColor(fav.colorHex)
                val swatch = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).also { it.marginEnd = 6.dp }
                    setBackgroundColor(color)
                    setOnClickListener { applyHexColor(fav.colorHex) }
                }
                favoritesRow.addView(swatch)
            } catch (_: Exception) {}
        }
        favoritesRow.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    private fun colorToHex(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    companion object {
        fun calculateTextColor(backgroundHex: String?): Int {
            if (backgroundHex == null) return Color.WHITE
            return try {
                val color = Color.parseColor(backgroundHex)
                val r = Color.red(color) / 255.0
                val g = Color.green(color) / 255.0
                val b = Color.blue(color) / 255.0
                val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
                if (luminance > 0.5) Color.BLACK else Color.WHITE
            } catch (_: Exception) { Color.WHITE }
        }

        fun calculateSubtitleColor(backgroundHex: String?): Int {
            val textColor = calculateTextColor(backgroundHex)
            return if (textColor == Color.WHITE) Color.parseColor("#B0BEC5") else Color.parseColor("#78909C")
        }
    }
}
