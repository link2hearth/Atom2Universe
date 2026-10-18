package com.Atom2Universe.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.Atom2Universe.app.audio.AudioDialog
import com.Atom2Universe.app.audio.AudioStyle
import com.Atom2Universe.app.effects.EffectsDecoration
import com.Atom2Universe.app.effects.EffectsPalette
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/** Live settings: keep the dialog, hub, scroll position and playback controller alive. */
class ThemePickerDialog(
    context: Context,
    private val onChanged: () -> Unit
) : AudioDialog(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private var preset = AppThemeManager.getSelectedPreset(context)
    private var appearance = AppThemeManager.getSelectedAppearance(context)
    private var brightness = AppThemeManager.getSelectedBrightness(context)
    private var tintedSurfaces = AppThemeManager.hasTintedSurfaces(context)
    private var effects = AppThemeManager.getEffects(context)
    // Removing the preset swatches must not silently change an existing user's accent.
    private var spectrum = AppThemeManager.getSelectedSpectrum(context) ?: nearestSpectrum()
    private var updatingUi = false
    private var effectsExpanded = false

    private lateinit var content: LinearLayout
    private lateinit var hueBar: SeekBar
    private lateinit var effectsDetails: LinearLayout
    private lateinit var effectsButton: MaterialButton
    private lateinit var soundsSwitch: SwitchMaterial
    private lateinit var animationsSwitch: SwitchMaterial
    private var decoration: EffectsDecoration? = null
    private lateinit var closeButton: MaterialButton
    private val labels = mutableListOf<Pair<TextView, Boolean>>()
    private val switches = mutableListOf<SwitchMaterial>()
    private val appearanceButtons = mutableListOf<Pair<AppAppearance, MaterialButton>>()
    private val levelButtons = mutableListOf<MaterialButton>()
    private val effectButtons = mutableListOf<Pair<AppEffectPack?, MaterialButton>>()
    private val lightColors = mutableMapOf<Pair<Int, Int>, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = column().apply { setPadding(dp(20), dp(16), dp(20), dp(8)) }
        content.addView(label(R.string.settings_theme_title, heading = true).apply {
            textSize = 20f
            setPadding(0, 0, 0, dp(12))
        })
        val body = column()
        body.addView(buildAppearanceRow())
        body.addView(switch(R.string.theme_dark_mode, brightness == AppBrightness.DARK) {
            brightness = if (it) AppBrightness.DARK else AppBrightness.LIGHT
            applyChange()
        })
        body.addView(switch(R.string.theme_tinted_surfaces, tintedSurfaces) {
            tintedSurfaces = it
            applyChange()
        })
        body.addView(label(R.string.theme_section_color).apply { setPadding(0, dp(8), 0, 0) })
        hueBar = SeekBar(context).apply {
            max = SpectrumThemes.HUES - 1
            progress = spectrum.hue
            contentDescription = context.getString(R.string.theme_hue_bar)
            splitTrack = false
            setPadding(dp(12), 0, dp(12), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updatingUi) return
                    if (preset == null && spectrum.hue == progress) return
                    preset = null
                    spectrum = spectrum.copy(hue = progress)
                    applyChange()
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        body.addView(hueBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        body.addView(buildLevelRow())
        effectsDetails = column()
        effectsDetails.addView(buildEffectsRow())
        animationsSwitch = switch(R.string.theme_effect_animations, effects.animations) {
            effects = effects.copy(animations = it)
            applyChange()
        }
        effectsDetails.addView(animationsSwitch)
        soundsSwitch = switch(R.string.theme_effect_sounds, effects.sounds) {
            effects = effects.copy(sounds = it)
            applyChange()
        }
        effectsDetails.addView(soundsSwitch)
        effectsDetails.visibility = View.GONE
        effectsButton = button(R.string.theme_section_effects).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                effectsExpanded = !effectsExpanded
                effectsDetails.visibility = if (effectsExpanded) View.VISIBLE else View.GONE
                updateEffectsDisclosure()
            }
        }
        body.addView(effectsButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        body.addView(effectsDetails)

        // Bound only the settings area; the title and Close action stay reachable in landscape.
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = (resources.displayMetrics.heightPixels * .86f).toInt()
                closeButton.measure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val fixed = content.paddingTop + content.paddingBottom +
                    content.getChildAt(0).measuredHeight + closeButton.measuredHeight + dp(4)
                val limit = (available - fixed).coerceAtLeast(dp(48))
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    minOf(limit, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: limit),
                    MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(body)
        }
        closeButton = button(R.string.close).apply {
            setOnClickListener { dismiss() }
        }
        content.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(closeButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END; topMargin = dp(4) })
        setContentView(content)
        refreshControls()
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setLayout((context.resources.displayMetrics.widthPixels * .9f).toInt()
                .coerceAtMost(dp(400)), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(.18f)
        }
        decoration = EffectsDecoration(content).also {
            it.update(effects, EffectsPalette.from(context))
            it.setActive(true)
        }
    }

    override fun onStop() {
        decoration?.close()
        decoration = null
        super.onStop()
    }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun label(res: Int, heading: Boolean = false) = TextView(context).apply {
        setText(res)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        if (heading) {
            paint.isFakeBoldText = true
            ViewCompat.setAccessibilityHeading(this, true)
        }
        labels += this to heading
    }

    private fun button(res: Int) = MaterialButton(context).apply {
        setText(res)
        isAllCaps = false
        textSize = 14f
        minimumWidth = 0
        minimumHeight = dp(48)
        insetTop = dp(3)
        insetBottom = dp(3)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        stateListAnimator = null
    }

    private fun buildAppearanceRow() = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val weights = AppAppearance.entries.map { if (it == AppAppearance.ILLUMINATED) 1.5f else 1f }
            val unit = (0 until childCount).maxOfOrNull { index ->
                val button = getChildAt(index) as MaterialButton
                (button.paint.measureText(button.text.toString()) + button.paddingLeft +
                    button.paddingRight + dp(4)) / weights[index]
            } ?: 0f
            val required = unit * weights.sum() + dp(6) * (childCount - 1)
            val stacked = available > 0 && required > available
            val desiredOrientation = if (stacked) VERTICAL else HORIZONTAL
            if (orientation != desiredOrientation) {
                orientation = desiredOrientation
                for (index in 0 until childCount) {
                    getChildAt(index).layoutParams = LayoutParams(
                        if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, if (stacked) 0f else weights[index]
                    ).apply {
                        if (index > 0) {
                            if (stacked) topMargin = dp(2) else marginStart = dp(6)
                        }
                    }
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        contentDescription = context.getString(R.string.theme_section_appearance)
        AppAppearance.entries.forEachIndexed { index, option ->
            val control = button(option.labelRes).apply {
                textSize = 13f
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener {
                    if (appearance != option) {
                        appearance = option
                        applyChange()
                    }
                }
            }
            addView(control, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                if (option == AppAppearance.ILLUMINATED) 1.5f else 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
            appearanceButtons += option to control
        }
    }

    private fun buildLevelRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        contentDescription = context.getString(R.string.theme_section_intensity)
        AppThemeManager.spectrumLevelLabels.forEachIndexed { level, res ->
            val control = button(res).apply {
                setOnClickListener {
                    if (preset != null || spectrum.level != level) {
                        preset = null
                        spectrum = spectrum.copy(level = level)
                        applyChange()
                    }
                }
            }
            addView(control, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (level > 0) marginStart = dp(6)
            })
            levelButtons += control
        }
    }

    private fun buildEffectsRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        (listOf<AppEffectPack?>(null) + AppEffectPack.entries).forEachIndexed { index, option ->
            val control = button(option?.labelRes ?: R.string.theme_effect_none).apply {
                setOnClickListener {
                    effects = effects.copy(enabled = option != null, pack = option ?: effects.pack)
                    applyChange()
                }
            }
            addView(control, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
            effectButtons += option to control
        }
    }

    private fun switch(res: Int, checked: Boolean, changed: (Boolean) -> Unit) =
        SwitchMaterial(context).apply {
            setText(res)
            textSize = 15f
            minimumHeight = dp(48)
            switchPadding = dp(12)
            setPadding(0, dp(4), 0, dp(4))
            setUseMaterialThemeColors(false)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> if (!updatingUi) changed(value) }
            switches += this
        }

    private fun applyChange() {
        AppThemeManager.setSelection(context, appearance, brightness, tintedSurfaces, effects,
            preset, spectrum, synchronous = false)
        onChanged()
        AppThemeManager.refreshContextTheme(context, dialog = true)
        refreshControls()
    }

    private fun refreshControls() {
        updatingUi = true
        val accent = AudioStyle.accent(context)
        val primary = AudioStyle.primaryText(context)
        val secondary = AudioStyle.secondaryText(context)
        val surface = AudioStyle.surface(context)
        val outline = ContextCompat.getColor(context, R.color.audio_outline)
        val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 40))
        content.background = AudioStyle.panel(context)
        content.clipToOutline = true
        window?.let { dialogWindow ->
            dialogWindow.setBackgroundDrawable(AudioStyle.panel(context))
            androidx.core.view.WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
                isAppearanceLightStatusBars = AppearanceStyle.isLight(context)
                isAppearanceLightNavigationBars = AppearanceStyle.isLight(context)
            }
        }
        labels.forEach { (label, heading) -> label.setTextColor(if (heading) primary else secondary) }
        fun styleButton(button: MaterialButton, selected: Boolean, plain: Boolean = false) {
            button.isSelected = selected
            button.cornerRadius = AppearanceStyle.corner(context, 14f).toInt()
            button.backgroundTintList = ColorStateList.valueOf(
                if (plain) Color.TRANSPARENT else if (selected) accent else surface)
            button.setTextColor(if (selected) AudioStyle.onAccent(context) else if (plain) accent else primary)
            button.strokeWidth = if (plain) 0 else dp(1).coerceAtLeast(1)
            button.strokeColor = ColorStateList.valueOf(if (selected) accent else outline)
            button.rippleColor = ripple
            button.elevation = if (selected) AppearanceStyle.elevation(context, 2f) else 0f
        }
        appearanceButtons.forEach { (option, control) -> styleButton(control, option == appearance) }
        levelButtons.forEachIndexed { level, control ->
            styleButton(control, preset == null && level == spectrum.level)
        }
        effectButtons.forEach { (option, control) ->
            val current = effects.pack.takeIf { effects.enabled }
            styleButton(control, option == current)
        }
        styleButton(effectsButton, false, plain = true)
        styleButton(closeButton, false, plain = true)
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked), intArrayOf())
        val disabled = ColorUtils.blendARGB(surface, secondary, .35f)
        animationsSwitch.isEnabled = effects.enabled && effects.pack != null
        soundsSwitch.visibility = if (effects.enabled && effects.pack?.hasSounds == true) View.VISIBLE else View.GONE
        soundsSwitch.isEnabled = effects.enabled && effects.pack?.hasSounds == true
        switches.forEach {
            it.setTextColor(if (it.isEnabled) primary else secondary)
            it.thumbTintList = ColorStateList(states, intArrayOf(disabled, accent, secondary))
            it.trackTintList = ColorStateList(states, intArrayOf(
                ColorUtils.setAlphaComponent(secondary, 35),
                ColorUtils.setAlphaComponent(accent, 90),
                ColorUtils.setAlphaComponent(secondary, 65)))
            it.background = AppearanceStyle.drawable(context, android.R.attr.selectableItemBackground)
        }
        val track = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            IntArray(SpectrumThemes.HUES) { hue -> spectrumAccent(hue) }).apply {
            cornerRadius = AppearanceStyle.corner(context, 8f)
            setSize(dp(240), dp(16))
        }
        hueBar.progressTintList = null
        hueBar.progressBackgroundTintList = null
        hueBar.progressDrawable = LayerDrawable(arrayOf(track)).apply {
            setLayerGravity(0, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)
            setLayerHeight(0, dp(16))
        }
        hueBar.thumbTintList = null
        hueBar.thumb = GradientDrawable().apply {
            shape = AppearanceStyle.controlShape(context)
            setColor(accent)
            setSize(dp(28), dp(28))
            setStroke(dp(2).coerceAtLeast(1), primary)
        }
        hueBar.thumbOffset = dp(14)
        hueBar.progress = spectrum.hue
        updateEffectsDisclosure()
        decoration?.update(effects, EffectsPalette.from(context))
        decoration?.setActive(true)
        updatingUi = false
    }

    private fun updateEffectsDisclosure() {
        effectsButton.text = context.getString(R.string.theme_effects_current,
            context.getString(effects.pack?.takeIf { effects.enabled }?.labelRes ?: R.string.theme_effect_none))
        effectsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
            if (effectsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0)
        effectsButton.compoundDrawableTintList = ColorStateList.valueOf(AudioStyle.secondaryText(context))
        ViewCompat.setStateDescription(effectsButton, context.getString(
            if (effectsExpanded) R.string.theme_options_expanded else R.string.theme_options_collapsed))
    }

    private fun spectrumAccent(hue: Int): Int = if (brightness == AppBrightness.DARK) {
        SpectrumThemes.accent(hue, spectrum.level)
    } else lightColors.getOrPut(hue to spectrum.level) {
        val style = SpectrumThemes.style(hue, spectrum.level)
        val themed = androidx.appcompat.view.ContextThemeWrapper(context, style)
        themed.theme.applyStyle(LightAccentThemes.overlay(style), true)
        AudioStyle.accent(themed)
    }

    /** Keep the closest hue when an older preset first switches to the color slider. */
    private fun nearestSpectrum(): AppThemeManager.Spectrum {
        val themed = androidx.appcompat.view.ContextThemeWrapper(context,
            preset?.styleRes ?: AppTheme.DEFAULT.styleRes)
        val color = AudioStyle.accent(themed)
        fun distance(candidate: Int): Int {
            val r = Color.red(candidate) - Color.red(color)
            val g = Color.green(candidate) - Color.green(color)
            val b = Color.blue(candidate) - Color.blue(color)
            return r * r + g * g + b * b
        }
        return (0 until SpectrumThemes.HUES).flatMap { hue ->
            AppThemeManager.spectrumLevelLabels.indices.map { AppThemeManager.Spectrum(hue, it) }
        }.minBy { distance(SpectrumThemes.accent(it.hue, it.level)) }
    }
}
