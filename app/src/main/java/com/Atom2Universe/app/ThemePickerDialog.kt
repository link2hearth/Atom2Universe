package com.Atom2Universe.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.audio.AudioDialog
import com.Atom2Universe.app.audio.AudioStyle

/**
 * Le choix de la couleur de l'appli : les thèmes prédéfinis, ou n'importe quelle teinte de la
 * barre arc-en-ciel dans l'une des trois intensités.
 *
 * Rien n'est appliqué avant « Appliquer » : un thème ne se pose qu'à la création d'un écran, il
 * faut donc recréer l'activité. D'ici là, seul l'aperçu change, avec les couleurs exactes du
 * thème visé (celles de [SpectrumThemes], ou lues dans le style du thème prédéfini).
 */
class ThemePickerDialog(
    context: Context,
    private val onApplied: () -> Unit
) : AudioDialog(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    // Le choix en cours : un thème prédéfini, ou (preset == null) une couleur libre.
    private var preset: AppTheme? = AppThemeManager.getSelectedPreset(context)
    private var spectrum: AppThemeManager.Spectrum =
        AppThemeManager.getSelectedSpectrum(context) ?: AppThemeManager.Spectrum(0, 0)

    private val presetSwatches = mutableListOf<Pair<AppTheme, View>>()
    private val levelChips = mutableListOf<TextView>()
    private lateinit var hueBar: HueBarView
    private lateinit var preview: LinearLayout
    private lateinit var previewButton: TextView
    private lateinit var previewTile: View

    private val background = ContextCompat.getColor(context, R.color.audio_background)
    private val surface = ContextCompat.getColor(context, R.color.audio_surface)
    private val textPrimary = ContextCompat.getColor(context, R.color.audio_text_primary)
    private val textSecondary = ContextCompat.getColor(context, R.color.audio_text_secondary)
    private val onAccent = ContextCompat.getColor(context, R.color.audio_on_accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(20f), dp(20f), dp(12f))
        }
        content.addView(TextView(context).apply {
            setText(R.string.theme_select_title)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            paint.isFakeBoldText = true
        })

        content.addView(sectionLabel(R.string.theme_section_presets))
        content.addView(buildPresetRow())

        content.addView(sectionLabel(R.string.theme_section_spectrum))
        hueBar = HueBarView(context).apply {
            contentDescription = context.getString(R.string.theme_hue_bar)
            onHuePicked = { hue ->
                preset = null
                spectrum = spectrum.copy(hue = hue)
                refresh()
            }
        }
        content.addView(hueBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)))

        content.addView(sectionLabel(R.string.theme_section_intensity))
        content.addView(buildLevelRow())

        content.addView(buildPreview())
        content.addView(buildActions())

        setContentView(ScrollView(context).apply { addView(content) })
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.9f).toInt()
            .coerceAtMost(dp(460f)), ViewGroup.LayoutParams.WRAP_CONTENT)
        refresh()
    }

    private fun sectionLabel(res: Int) = TextView(context).apply {
        setText(res)
        setTextColor(textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(18f), 0, dp(8f))
    }

    private fun buildPresetRow(): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        AppThemeManager.getAvailableThemes().forEach { theme ->
            val swatch = View(context).apply {
                contentDescription = context.getString(theme.labelRes)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    preset = theme
                    refresh()
                }
            }
            row.addView(swatch, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginEnd = dp(10f) })
            presetSwatches += theme to swatch
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun buildLevelRow(): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        AppThemeManager.spectrumLevelLabels.forEachIndexed { level, label ->
            val chip = TextView(context).apply {
                setText(label)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                minHeight = dp(44f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    preset = null
                    spectrum = spectrum.copy(level = level)
                    refresh()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (level > 0) marginStart = dp(8f)
            })
            levelChips += chip
        }
        return row
    }

    private fun buildPreview(): View {
        preview = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        }
        previewTile = View(context)
        preview.addView(previewTile, LinearLayout.LayoutParams(dp(56f), dp(56f)))
        previewButton = TextView(context).apply {
            setText(R.string.theme_preview)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(onAccent)
            minHeight = dp(48f)
            setPadding(dp(20f), 0, dp(20f), 0)
        }
        preview.addView(previewButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16f)
        })
        return preview.also {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20f) }
        }
    }

    private fun buildActions(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12f), 0, 0)
        }
        val current = AudioStyle.accent(context)
        fun action(res: Int, onClick: () -> Unit) = TextView(context).apply {
            setText(res)
            setTextColor(current)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            minHeight = dp(48f)
            setPadding(dp(16f), 0, dp(16f), 0)
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }
        row.addView(action(android.R.string.cancel) { dismiss() })
        row.addView(action(R.string.theme_apply) { apply() })
        return row
    }

    private fun apply() {
        val chosenPreset = preset
        val changed = if (chosenPreset != null) {
            AppThemeManager.getSelectedPreset(context) != chosenPreset
        } else {
            AppThemeManager.getSelectedSpectrum(context) != spectrum
        }
        if (changed) {
            if (chosenPreset != null) AppThemeManager.setSelectedTheme(context, chosenPreset)
            else AppThemeManager.setSelectedSpectrum(context, spectrum)
        }
        dismiss()
        if (changed) onApplied()
    }

    /** Couleur d'accent et couleur « dim » d'un thème prédéfini, lues une fois dans son style. */
    private val presetColorCache = mutableMapOf<AppTheme, Pair<Int, Int>>()
    private fun presetColors(theme: AppTheme): Pair<Int, Int> = presetColorCache.getOrPut(theme) {
        val themed = androidx.appcompat.view.ContextThemeWrapper(context, theme.styleRes)
        fun attr(id: Int): Int = TypedValue().let {
            themed.theme.resolveAttribute(id, it, true)
            if (it.resourceId != 0) ContextCompat.getColor(themed, it.resourceId) else it.data
        }
        attr(R.attr.a2uMusicAccent) to attr(R.attr.a2uMusicAccentDim)
    }

    private fun refresh() {
        val chosenPreset = preset
        val (accent, dim) = if (chosenPreset != null) presetColors(chosenPreset) else {
            val a = SpectrumThemes.accent(spectrum.hue, spectrum.level)
            // Même mélange que le générateur de thèmes (tools/theme/generate_spectrum_themes.py).
            a to ColorUtils.blendARGB(background, a, 0.24f)
        }

        presetSwatches.forEach { (theme, view) ->
            val selected = theme == chosenPreset
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(presetColors(theme).first)
                setStroke(dp(if (selected) 3f else 1f), if (selected) textPrimary else
                    ContextCompat.getColor(context, R.color.audio_outline))
            }
        }

        hueBar.level = spectrum.level
        hueBar.selectedHue = if (chosenPreset == null) spectrum.hue else -1

        levelChips.forEachIndexed { level, chip ->
            val selected = chosenPreset == null && level == spectrum.level
            val chipAccent = SpectrumThemes.accent(spectrum.hue, level)
            chip.setTextColor(if (selected) textPrimary else textSecondary)
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(if (selected) ColorUtils.blendARGB(background, chipAccent, 0.24f) else surface)
                setStroke(dp(1f), if (selected) chipAccent else ContextCompat.getColor(context, R.color.audio_outline))
            }
        }

        preview.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(dim, surface)).apply {
            cornerRadius = dp(20f).toFloat()
            setStroke(dp(1f), ColorUtils.setAlphaComponent(accent, 90))
        }
        previewTile.background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            setColor(surface)
            setStroke(dp(1f), ColorUtils.setAlphaComponent(accent, 60))
        }
        previewButton.background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            setColor(accent)
        }
    }

    /**
     * La barre arc-en-ciel : une case par teinte disponible, dans l'intensité choisie. On y voit
     * donc exactement les couleurs qu'on peut obtenir, et le doigt s'arrête sur l'une d'elles.
     */
    private class HueBarView(context: Context) : View(context) {
        var level = 0
            set(value) { field = value; invalidate() }
        var selectedHue = -1
            set(value) { field = value; invalidate() }
        var onHuePicked: ((Int) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 3f * resources.displayMetrics.density
        }
        private val rect = RectF()

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val bar = h * 0.6f
            val top = (h - bar) / 2f
            val step = width.toFloat() / SpectrumThemes.HUES
            val radius = bar / 2f
            canvas.save()
            rect.set(0f, top, width.toFloat(), top + bar)
            val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clip)
            for (hue in 0 until SpectrumThemes.HUES) {
                paint.color = SpectrumThemes.accent(hue, level)
                // Un pixel de recouvrement : pas de fil sombre entre deux cases.
                canvas.drawRect(hue * step, top, (hue + 1) * step + 1f, top + bar, paint)
            }
            canvas.restore()
            if (selectedHue >= 0) {
                val cx = (selectedHue + 0.5f) * step
                paint.color = SpectrumThemes.accent(selectedHue, level)
                canvas.drawCircle(cx, h / 2f, h * 0.42f, paint)
                canvas.drawCircle(cx, h / 2f, h * 0.42f, ring)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Un glissé horizontal ne doit pas faire défiler le dialogue.
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val hue = (event.x / width * SpectrumThemes.HUES).toInt()
                        .coerceIn(0, SpectrumThemes.HUES - 1)
                    if (hue != selectedHue) onHuePicked?.invoke(hue)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean = super.performClick()
    }
}
