package com.Atom2Universe.app.audioeditor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.secondaryButton
import kotlinx.coroutines.Job
import java.util.Locale
import kotlin.math.ln
import kotlin.math.log10

/**
 * La feuille d'analyse : mesures de la plage (durée, crêtes, niveau efficace, saturations, décalage continu) et courbe du
 * spectre moyen. Le calcul se fait en tâche de fond (il peut durer sur une longue plage) ; fermer la feuille l'annule.
 */
class AnalysisUi(private val activity: AppCompatActivity, private val vm: EditorViewModel) {

    fun show() {
        var job: Job? = null
        val dialog = activity.bottomSheet(activity.getString(R.string.ae_an_title)) { root, dlg ->
            val waiting = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(24), 0, activity.dp(24))
                addView(ProgressBar(activity).apply {
                    isIndeterminate = true
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(activity.accentColor())
                })
                addView(activity.label(activity.getString(R.string.ae_an_computing), 13f).apply { setPadding(0, activity.dp(8), 0, 0) })
            }
            root.addView(waiting)
            job = vm.analyze { r ->
                if (!dlg.isShowing) return@analyze
                if (r == null) { dlg.dismiss(); return@analyze }
                root.removeView(waiting)
                fill(root, r, dlg)
            }
        }
        dialog.setOnDismissListener { job?.cancel() }
        dialog.show()
    }

    private fun clock(frames: Long, rate: Int): String {
        val c = TimelineMath.clock(frames, rate)
        return if (c.hours > 0) activity.getString(R.string.ae_clock_hms_cs, c.hours, c.minutes, c.seconds, c.centis)
        else activity.getString(R.string.ae_time_mss_cs, c.minutes, c.seconds, c.centis)
    }

    /** Un niveau linéaire en dB, avec une décimale ; « Silence » en dessous de −120 dB. */
    private fun db(v: Float): String =
        if (v < 1e-6f) activity.getString(R.string.ae_an_silence)
        else activity.getString(R.string.ae_unit_db, String.format(Locale.getDefault(), "%.1f", 20 * log10(v)))

    private fun row(root: LinearLayout, name: Int, value: String) {
        val r = LinearLayout(activity).apply { setPadding(activity.dp(2), activity.dp(5), activity.dp(2), activity.dp(5)) }
        r.addView(activity.label(activity.getString(name), 13f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(activity.label(value, 13f, true))
        root.addView(r)
    }

    private fun fill(root: LinearLayout, r: EditorViewModel.AnalysisResult, dlg: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val s = r.stats
        val rate = r.sampleRate
        row(root, R.string.ae_an_range, activity.getString(R.string.ae_an_range_values, clock(r.from, rate), clock(r.to, rate)))
        row(root, R.string.ae_an_length, clock(r.to - r.from, rate))
        row(root, R.string.ae_an_peak, activity.getString(R.string.ae_an_pair, db(s.peakL), db(s.peakR)))
        row(root, R.string.ae_an_rms, db(s.rms))
        row(root, R.string.ae_an_clipped, s.clipped.toString())
        row(root, R.string.ae_an_dc, activity.getString(R.string.ae_unit_percent, String.format(Locale.getDefault(), "%.2f", s.dc * 100)))

        root.addView(activity.label(activity.getString(R.string.ae_an_spectrum), 12f).apply { setPadding(activity.dp(2), activity.dp(14), 0, activity.dp(6)) })
        root.addView(SpectrumPlotView(activity, r.spectrum, rate), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(190)))

        if (s.clipRuns.isNotEmpty()) {
            val row = LinearLayout(activity)
            row.addView(activity.secondaryButton(activity.getString(R.string.ae_an_mark_clips)) {
                dlg.dismiss()
                vm.addMarkers(s.clipRuns.map { r.from + it }) { n -> activity.getString(R.string.ae_an_clip_marker, n) }
            })
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(14) })
        }
    }
}

/** La courbe du spectre : fréquences en échelle logique (20 Hz → moitié de la fréquence d'échantillonnage), niveaux de −100 à 0 dB. */
private class SpectrumPlotView(context: Context, private val spectrum: FloatArray, private val rate: Int) : View(context) {

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = ContextCompat.getColor(context, R.color.audio_outline)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(1).toFloat(); color = context.accentColor() }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (context.accentColor() and 0x00FFFFFF) or 0x40000000 }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.audio_text_secondary)
        textSize = context.dp(10).toFloat()
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val left = context.dp(40).toFloat()
        val bottom = height - context.dp(18).toFloat()
        val top = context.dp(4).toFloat()
        val w = width - left - context.dp(6)
        val h = bottom - top
        val nyquist = rate / 2.0
        val f0 = 20.0
        val fftSize = (spectrum.size - 1) * 2
        fun x(f: Double) = (left + (ln(f / f0) / ln(nyquist / f0)) * w).toFloat()
        fun y(db: Float) = (top + (1f - ((db + 100f) / 100f).coerceIn(0f, 1f)) * h)

        // Grille : un trait tous les 20 dB, puis 100 Hz, 1 kHz, 10 kHz.
        text.textAlign = Paint.Align.RIGHT
        for (d in 0 downTo -100 step 20) {
            canvas.drawLine(left, y(d.toFloat()), width.toFloat(), y(d.toFloat()), grid)
            canvas.drawText(context.getString(R.string.ae_unit_db, d.toString()), left - context.dp(4), y(d.toFloat()) + context.dp(3), text)
        }
        text.textAlign = Paint.Align.CENTER
        for (f in listOf(100.0, 1000.0, 10000.0)) {
            if (f >= nyquist) continue
            val px = x(f)
            canvas.drawLine(px, top, px, bottom, grid)
            val name = if (f >= 1000) context.getString(R.string.ae_band_khz, (f / 1000).toInt().toString()) else context.getString(R.string.ae_band_hz, f.toInt().toString())
            canvas.drawText(name, px, height - context.dp(4).toFloat(), text)
        }

        // La courbe : une valeur par case de la FFT au-dessus de 20 Hz ; dessous, une surface pleine jusqu'au bas du cadre.
        path.reset()
        var firstX = left
        var lastX = left
        var started = false
        for (k in 1 until spectrum.size) {
            val f = k.toDouble() * rate / fftSize
            if (f < f0) continue
            val px = x(f)
            val py = y(spectrum[k])
            if (!started) { path.moveTo(px, py); firstX = px; started = true } else path.lineTo(px, py)
            lastX = px
        }
        if (!started) return
        canvas.drawPath(path, line)
        path.lineTo(lastX, bottom)
        path.lineTo(firstX, bottom)
        path.close()
        canvas.drawPath(path, area)
    }
}
