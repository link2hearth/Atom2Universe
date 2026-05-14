package com.Atom2Universe.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.view.ColorPickerHueView
import com.Atom2Universe.app.view.ColorPickerSatValView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

/**
 * Dialog pour sélectionner une couleur avec option de couleur de texte
 * Utilisé pour les groupes et les tags
 * @param showAlpha Si true, affiche un slider pour la transparence (utilisé par Pixel Art Editor)
 * @param showTextMode Si true, affiche les options de couleur de texte (désactivé pour Pixel Art)
 * @param initialAlpha Valeur alpha initiale (0-255), utilisé uniquement si showAlpha = true
 * @param onColorWithAlphaSelected Callback optionnel pour les couleurs avec alpha (reçoit colorInt)
 * @param onColorSelected Callback principal pour les couleurs (trailing lambda compatible)
 */
class SimpleColorPickerDialog(
    context: Context,
    private val currentColorHex: String?,
    private val currentTextColorMode: String = "auto",
    private val showAlpha: Boolean = false,
    private val showTextMode: Boolean = true,
    private val initialAlpha: Int = 255,
    private val onColorWithAlphaSelected: ((colorInt: Int) -> Unit)? = null,
    private val onColorSelected: (colorHex: String, textColorMode: String) -> Unit
) : Dialog(context, R.style.Theme_A2U_Dialog) {

    // Quick colors palette
    private val quickColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
        "#795548", "#9E9E9E", "#607D8B", "#FFFFFF",
        "#E0E0E0", "#BDBDBD", "#757575", "#212121"
    )

    private lateinit var satValPicker: ColorPickerSatValView
    private lateinit var huePicker: ColorPickerHueView
    private lateinit var hexInput: TextInputEditText
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewText: TextView
    private lateinit var textModeGroup: RadioGroup
    private lateinit var quickColorGrid: GridLayout
    private lateinit var favoritesSection: LinearLayout
    private lateinit var favoritesGrid: GridLayout
    private lateinit var addFavoriteBtn: MaterialButton

    // Alpha slider views (only used when showAlpha = true)
    private var alphaSection: LinearLayout? = null
    private var alphaSlider: SeekBar? = null
    private var alphaValueText: TextView? = null
    private var textModeSection: LinearLayout? = null

    private val favoritesManager = FavoriteColorsManager(context)
    private var selectedColorHex: String = currentColorHex ?: "#2196F3"
    private var selectedTextColorMode: String = currentTextColorMode

    // Alpha value (0-255, default fully opaque or from initialAlpha)
    private var currentAlpha: Int = if (showAlpha) initialAlpha else 255

    // HSV values
    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f

    private var isUpdatingFromHex = false
    private var isUpdatingFromPicker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_simple_color_picker)

        // Set dialog width
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initViews()
        setupFavorites()
        setupQuickColors()
        setupColorPicker()
        setupHexInput()
        setupAlphaSlider()
        setupTextModeRadios()
        setupButtons()

        // Initialize with current color
        setColorFromHex(selectedColorHex)
        updatePreview()
    }

    private fun initViews() {
        satValPicker = findViewById(R.id.color_satval_picker)
        huePicker = findViewById(R.id.color_hue_picker)
        hexInput = findViewById(R.id.color_hex_input)
        previewCard = findViewById(R.id.color_preview_card)
        previewText = findViewById(R.id.color_preview_text)
        textModeGroup = findViewById(R.id.color_text_mode_group)
        quickColorGrid = findViewById(R.id.color_quick_grid)
        favoritesSection = findViewById(R.id.favorites_section)
        favoritesGrid = findViewById(R.id.color_favorites_grid)
        addFavoriteBtn = findViewById(R.id.btn_add_favorite)

        // Alpha slider views (may be null if layout doesn't have them)
        alphaSection = findViewById(R.id.alpha_section)
        alphaSlider = findViewById(R.id.alpha_slider)
        alphaValueText = findViewById(R.id.alpha_value_text)
        textModeSection = findViewById(R.id.text_mode_section)

        // Show/hide alpha section based on showAlpha parameter
        alphaSection?.visibility = if (showAlpha) View.VISIBLE else View.GONE

        // Show/hide text mode section based on showTextMode parameter
        textModeSection?.visibility = if (showTextMode) View.VISIBLE else View.GONE
    }

    private fun setupFavorites() {
        refreshFavorites()

        addFavoriteBtn.setOnClickListener {
            val added = favoritesManager.addFavorite(selectedColorHex, selectedTextColorMode)
            if (added) {
                Toast.makeText(context, R.string.notes_color_favorite_added, Toast.LENGTH_SHORT).show()
                refreshFavorites()
            } else {
                Toast.makeText(context, R.string.notes_color_favorite_exists, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshFavorites() {
        val favorites = favoritesManager.getFavorites()
        favoritesGrid.removeAllViews()

        if (favorites.isEmpty()) {
            favoritesSection.visibility = View.GONE
            return
        }

        favoritesSection.visibility = View.VISIBLE

        val buttonSize = context.resources.getDimensionPixelSize(R.dimen.color_button_size)
        val margin = context.resources.getDimensionPixelSize(R.dimen.color_button_margin)
        val density = context.resources.displayMetrics.density

        for (favorite in favorites) {
            // Container FrameLayout pour la couleur + X
            val container = FrameLayout(context).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = params
            }

            // Bouton couleur
            val colorButton = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4f * density
                    setColor(Color.parseColor(favorite.colorHex))
                    setStroke((1 * density).toInt(), Color.parseColor("#9E9E9E"))
                }
                background = drawable

                setOnClickListener {
                    setColorFromHex(favorite.colorHex)
                    selectedColorHex = favorite.colorHex
                    // Appliquer aussi le mode de texte du favori
                    selectedTextColorMode = favorite.textColorMode
                    updateTextModeRadio()
                    updatePreview()
                }
            }
            container.addView(colorButton)

            // Petite croix de suppression
            val deleteSize = (14 * density).toInt()
            val deleteIcon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(deleteSize, deleteSize).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                setImageResource(android.R.drawable.ic_delete)
                setColorFilter(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())

                setOnClickListener {
                    showDeleteFavoriteDialog(favorite)
                }
            }
            container.addView(deleteIcon)

            favoritesGrid.addView(container)
        }
    }

    private fun updateTextModeRadio() {
        when (selectedTextColorMode) {
            "auto" -> findViewById<RadioButton>(R.id.radio_text_auto).isChecked = true
            "white" -> findViewById<RadioButton>(R.id.radio_text_white).isChecked = true
            "black" -> findViewById<RadioButton>(R.id.radio_text_black).isChecked = true
            "gray" -> findViewById<RadioButton>(R.id.radio_text_gray).isChecked = true
        }
    }

    private fun showDeleteFavoriteDialog(favorite: FavoriteColorsManager.FavoriteColor) {
        AlertDialog.Builder(context)
            .setTitle(R.string.notes_color_delete_favorite_title)
            .setMessage(R.string.notes_color_delete_favorite_message)
            .setPositiveButton(R.string.notes_action_delete) { _, _ ->
                favoritesManager.removeFavorite(favorite.colorHex, favorite.textColorMode)
                Toast.makeText(context, R.string.notes_color_favorite_deleted, Toast.LENGTH_SHORT).show()
                refreshFavorites()
            }
            .setNegativeButton(R.string.notes_action_cancel, null)
            .show()
    }

    private fun setupQuickColors() {
        quickColorGrid.removeAllViews()

        val buttonSize = context.resources.getDimensionPixelSize(R.dimen.color_button_size)
        val margin = context.resources.getDimensionPixelSize(R.dimen.color_button_margin)

        for (colorHex in quickColors) {
            val button = View(context).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = params

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4f * context.resources.displayMetrics.density
                    setColor(Color.parseColor(colorHex))
                    setStroke(
                        (1 * context.resources.displayMetrics.density).toInt(),
                        Color.parseColor("#9E9E9E")
                    )
                }
                background = drawable

                setOnClickListener {
                    setColorFromHex(colorHex)
                    selectedColorHex = colorHex
                    updatePreview()
                }
            }
            quickColorGrid.addView(button)
        }
    }

    private fun setupColorPicker() {
        huePicker.setOnHueChangedListener { h ->
            hue = h
            satValPicker.setHue(h)
            if (!isUpdatingFromHex) {
                updateColorFromHsv()
            }
        }

        satValPicker.setOnColorChangedListener { s, v ->
            saturation = s
            value = v
            if (!isUpdatingFromHex) {
                updateColorFromHsv()
            }
        }
    }

    private fun setupHexInput() {
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromPicker) return

                val text = s?.toString() ?: return
                if (text.length == 7 && text.startsWith("#")) {
                    try {
                        Color.parseColor(text)
                        isUpdatingFromHex = true
                        setColorFromHex(text)
                        selectedColorHex = text
                        updatePreview()
                        isUpdatingFromHex = false
                    } catch (e: IllegalArgumentException) {
                        // Invalid color, ignore
                    }
                }
            }
        })
    }

    private fun setupAlphaSlider() {
        if (!showAlpha) return

        alphaSlider?.apply {
            max = 255
            progress = currentAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentAlpha = progress
                    alphaValueText?.text = "$progress"
                    updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        alphaValueText?.text = "$currentAlpha"
    }

    private fun setupTextModeRadios() {
        // Set initial selection
        when (selectedTextColorMode) {
            "auto" -> findViewById<RadioButton>(R.id.radio_text_auto).isChecked = true
            "white" -> findViewById<RadioButton>(R.id.radio_text_white).isChecked = true
            "black" -> findViewById<RadioButton>(R.id.radio_text_black).isChecked = true
            "gray" -> findViewById<RadioButton>(R.id.radio_text_gray).isChecked = true
        }

        textModeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedTextColorMode = when (checkedId) {
                R.id.radio_text_auto -> "auto"
                R.id.radio_text_white -> "white"
                R.id.radio_text_black -> "black"
                R.id.radio_text_gray -> "gray"
                else -> "auto"
            }
            updatePreview()
        }
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }

        findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            if (showAlpha && onColorWithAlphaSelected != null) {
                // Return color with alpha as Int
                val colorInt = getSelectedColorWithAlpha()
                onColorWithAlphaSelected.invoke(colorInt)
            } else {
                onColorSelected(selectedColorHex, selectedTextColorMode)
            }
            dismiss()
        }
    }

    /**
     * Get the selected color as an Int including alpha channel
     */
    private fun getSelectedColorWithAlpha(): Int {
        val baseColor = Color.parseColor(selectedColorHex)
        return Color.argb(currentAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }

    private fun setColorFromHex(hex: String) {
        try {
            val color = Color.parseColor(hex)
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)

            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]

            huePicker.setHue(hue)
            satValPicker.setHue(hue)
            satValPicker.setSaturation(saturation)
            satValPicker.setValue(value)

            if (!isUpdatingFromHex) {
                isUpdatingFromPicker = true
                hexInput.setText(hex.uppercase())
                hexInput.setSelection(hex.length)
                isUpdatingFromPicker = false
            }
        } catch (e: IllegalArgumentException) {
            // Invalid color
        }
    }

    private fun updateColorFromHsv() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        selectedColorHex = String.format("#%06X", 0xFFFFFF and color)

        isUpdatingFromPicker = true
        hexInput.setText(selectedColorHex)
        hexInput.setSelection(selectedColorHex.length)
        isUpdatingFromPicker = false

        updatePreview()
    }

    private fun updatePreview() {
        try {
            val baseColor = Color.parseColor(selectedColorHex)
            // Apply alpha if enabled
            val bgColor = if (showAlpha) {
                Color.argb(currentAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            } else {
                baseColor
            }
            previewCard.setCardBackgroundColor(bgColor)

            val textColor = getTextColor(baseColor)
            previewText.setTextColor(textColor)
        } catch (e: IllegalArgumentException) {
            // Invalid color, use default
            previewCard.setCardBackgroundColor(Color.parseColor("#2196F3"))
            previewText.setTextColor(Color.WHITE)
        }
    }

    private fun getTextColor(bgColor: Int): Int {
        return when (selectedTextColorMode) {
            "white" -> Color.WHITE
            "black" -> Color.BLACK
            "gray" -> Color.GRAY
            else -> {
                // Auto mode: calculate based on luminance
                val luminance = ColorUtils.calculateLuminance(bgColor)
                if (luminance > 0.5) Color.BLACK else Color.WHITE
            }
        }
    }

    companion object {
        /**
         * Calculate the appropriate text color for a given background color and mode
         */
        fun calculateTextColor(bgColorHex: String?, textColorMode: String): Int {
            if (bgColorHex == null) {
                return Color.WHITE // Default text color for dark theme
            }

            return try {
                val bgColor = Color.parseColor(bgColorHex)
                when (textColorMode) {
                    "white" -> Color.WHITE
                    "black" -> Color.BLACK
                    "gray" -> Color.GRAY
                    else -> {
                        val luminance = ColorUtils.calculateLuminance(bgColor)
                        if (luminance > 0.5) Color.BLACK else Color.WHITE
                    }
                }
            } catch (e: IllegalArgumentException) {
                Color.WHITE
            }
        }

        /**
         * Calculate subtitle/secondary text color (slightly transparent)
         */
        fun calculateSubtitleColor(bgColorHex: String?, textColorMode: String): Int {
            if (bgColorHex == null) {
                return Color.argb(200, 255, 255, 255)
            }

            return try {
                val bgColor = Color.parseColor(bgColorHex)
                when (textColorMode) {
                    "white" -> Color.argb(200, 255, 255, 255)
                    "black" -> Color.argb(180, 0, 0, 0)
                    "gray" -> Color.argb(200, 128, 128, 128)
                    else -> {
                        val luminance = ColorUtils.calculateLuminance(bgColor)
                        if (luminance > 0.5) Color.argb(180, 0, 0, 0)
                        else Color.argb(200, 255, 255, 255)
                    }
                }
            } catch (e: IllegalArgumentException) {
                Color.argb(200, 255, 255, 255)
            }
        }
    }
}
