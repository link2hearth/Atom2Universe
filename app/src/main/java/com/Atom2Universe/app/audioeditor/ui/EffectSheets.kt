package com.Atom2Universe.app.audioeditor.ui

import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.scrollRow
import com.Atom2Universe.app.pixelart.ui.secondaryButton
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Les feuilles des effets et des générateurs. Une seule construction sert pour tous : elle lit la liste de
 * paramètres d'un [EffectDef] ou d'un [GeneratorDef] et fabrique les curseurs, choix et cases qui vont avec.
 */
class EffectUi(private val activity: AppCompatActivity, private val vm: EditorViewModel) {

    // ---- Menus ---------------------------------------------------------------------------------------

    fun showEffects() {
        val items = EffectCatalog.categories.map { cat ->
            SheetItem(cat.icon, activity.getString(cat.title)) { showCategory(cat) }
        }
        activity.actionSheet(activity.getString(R.string.ae_effects), items).show()
    }

    private fun showCategory(cat: EffectCategory) {
        val items = cat.effects.map { def -> SheetItem(cat.icon, activity.getString(def.title)) { showEffect(def) } }
        activity.actionSheet(activity.getString(cat.title), items).show()
    }

    fun showGenerators() {
        val items = EffectCatalog.generators.map { def ->
            SheetItem(R.drawable.ic_px_add, activity.getString(def.title)) { showGenerator(def) }
        }
        activity.actionSheet(activity.getString(R.string.ae_generate), items).show()
    }

    // ---- Effet ---------------------------------------------------------------------------------------

    private fun showEffect(def: EffectDef) {
        val values = Values.defaults(def.params)
        val title = activity.getString(def.title)
        var state = EditorViewModel.PreviewState.IDLE
        lateinit var previewButton: TextView
        lateinit var applyButton: TextView
        var profileButton: TextView? = null

        fun ready() = !def.needsProfile || vm.noiseProfile != null

        fun refresh() {
            previewButton.text = activity.getString(
                when (state) {
                    EditorViewModel.PreviewState.IDLE -> R.string.ae_preview
                    EditorViewModel.PreviewState.RENDERING -> R.string.ae_preview_preparing
                    EditorViewModel.PreviewState.PLAYING -> R.string.ae_preview_stop
                },
            )
            previewButton.alpha = if (ready()) 1f else 0.35f
            applyButton.alpha = if (ready()) 1f else 0.35f
            profileButton?.let {
                it.text = activity.getString(if (vm.noiseProfile != null) R.string.ae_noise_captured else R.string.ae_noise_capture)
                it.isSelected = vm.noiseProfile != null
                it.alpha = if (vm.ui.hasSelection) 1f else 0.35f
            }
        }

        val dialog = activity.bottomSheet(title) { root, dlg ->
            for (p in def.params) root.addView(paramView(p, values))

            if (def.needsProfile) {
                val row = LinearLayout(activity)
                profileButton = activity.secondaryButton(activity.getString(R.string.ae_noise_capture), R.drawable.ic_px_picker) {
                    if (vm.ui.hasSelection) vm.captureNoiseProfile { if (dlg.isShowing) refresh() }
                }
                row.addView(profileButton)
                root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(14) })
            }

            val row = LinearLayout(activity)
            previewButton = activity.secondaryButton(activity.getString(R.string.ae_preview), R.drawable.ic_px_play) {
                if (state == EditorViewModel.PreviewState.IDLE) {
                    if (ready()) def.build(values, vm.noiseProfile)?.let { fx ->
                        vm.previewEffect(fx) { s -> state = s; if (dlg.isShowing) refresh() }
                    }
                } else {
                    vm.stopPreview()
                }
            }
            row.addView(previewButton)
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(14) })

            applyButton = activity.primaryButton(activity.getString(R.string.ae_apply)) {
                if (ready()) def.build(values, vm.noiseProfile)?.let { fx ->
                    dlg.dismiss()
                    vm.applyEffect(def, fx, title)
                }
            }
            root.addView(applyButton)
            refresh()
        }
        dialog.setOnDismissListener { vm.stopPreview() }
        dialog.show()
    }

    // ---- Générateur ----------------------------------------------------------------------------------

    private fun showGenerator(def: GeneratorDef) {
        val values = Values.defaults(def.params)
        val title = activity.getString(def.title)
        // Avec une sélection, la durée de départ est la sienne.
        if (vm.ui.hasSelection) {
            val seconds = (vm.ui.selEnd - vm.ui.selStart).toFloat() / vm.project.sampleRate
            values["duration"] = seconds.coerceIn(0.1f, 3600f)
        }
        activity.bottomSheet(title) { root, dlg ->
            for (p in def.params) root.addView(paramView(p, values))
            root.addView(activity.primaryButton(activity.getString(if (def.insertsSilence) R.string.ae_insert else R.string.ae_generate)) {
                dlg.dismiss()
                vm.generate(def, values, title)
            })
        }.show()
    }

    // ---- Paramètres ----------------------------------------------------------------------------------

    private fun nameOf(p: Param): String = if (p.labelArg != null) activity.getString(p.label, p.labelArg) else activity.getString(p.label)

    private fun paramView(p: Param, values: Values): View = when (p) {
        is SliderParam -> sliderView(p, values)
        is ChoiceParam -> choiceView(p, values)
        is ToggleParam -> toggleView(p, values)
    }

    private fun numberText(v: Float, step: Float): String = when {
        step >= 1f -> v.roundToInt().toString()
        step >= 0.1f -> String.format(Locale.getDefault(), "%.1f", v)
        else -> String.format(Locale.getDefault(), "%.2f", v)
    }

    private fun valueText(p: SliderParam, v: Float): String {
        val n = numberText(v, p.step)
        return if (p.unit.format == 0) n else activity.getString(p.unit.format, n)
    }

    /** Nom et valeur sur une ligne, le curseur pleine largeur dessous : tient sur un petit écran, même avec un long nom. */
    private fun sliderView(p: SliderParam, values: Values): View {
        val steps = 400
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(8), 0, 0)
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val name = activity.label(nameOf(p), 12f)
        val value = activity.label(valueText(p, values.f(p.id)), 12f, true)
        header.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(value)
        // Un curseur dans une zone qui défile interdit au parent de lui voler le geste.
        val bar = object : SeekBar(activity) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
                return super.onTouchEvent(event)
            }
        }
        bar.max = steps
        bar.progress = (p.fractionOf(values.f(p.id)) * steps).roundToInt()
        bar.progressTintList = ColorStateList.valueOf(activity.accentColor())
        bar.thumbTintList = ColorStateList.valueOf(activity.accentColor())
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = p.valueAt(progress.toFloat() / steps)
                values[p.id] = v
                value.text = valueText(p, v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(header)
        box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(36)))
        return box
    }

    private fun choiceView(p: ChoiceParam, values: Values): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(10), 0, 0)
        }
        box.addView(activity.label(nameOf(p), 12f).apply { setPadding(activity.dp(2), 0, 0, activity.dp(6)) })
        val chips = ArrayList<TextView>()
        p.options.forEachIndexed { i, text ->
            chips.add(activity.chip(activity.getString(text)) {
                values[p.id] = i.toFloat()
                chips.forEachIndexed { k, c -> c.isSelected = k == i }
            })
        }
        chips.forEachIndexed { k, c -> c.isSelected = k == values.i(p.id) }
        box.addView(activity.scrollRow(*chips.toTypedArray()))
        return box
    }

    private fun toggleView(p: ToggleParam, values: Values): View {
        val c = activity.chip(nameOf(p), R.drawable.ic_px_check) {
            val on = !values.b(p.id)
            values[p.id] = if (on) 1f else 0f
            it.isSelected = on
        }
        c.isSelected = values.b(p.id)
        return LinearLayout(activity).apply {
            setPadding(0, activity.dp(10), 0, 0)
            addView(c)
        }
    }
}
