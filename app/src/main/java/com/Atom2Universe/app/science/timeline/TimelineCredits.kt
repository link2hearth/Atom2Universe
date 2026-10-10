package com.Atom2Universe.app.science.timeline

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.FragmentActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.followImmersiveMode

/** Bibliography belongs to the main hub's About page, outside the exploration UI. */
object TimelineCredits {
    fun show(activity: FragmentActivity) = with(activity) {
        val palette = SciencePalette(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun paragraph(value: String) {
            body.addView(TextView(this).apply {
                text = value; textSize = 14f; setTextColor(palette.secondary)
                setPadding(0, padding / 2, 0, padding / 2)
                setTextIsSelectable(true)
            })
        }
        paragraph(getString(R.string.ct_credits_body))
        paragraph(getString(R.string.ct_illustrations))
        paragraph(getString(R.string.ct_geo_source_note))
        paragraph(getString(R.string.ct_read_source_method))
        body.addView(AppCompatButton(this).apply {
            setText(R.string.ct_history_sources); isAllCaps = false
            setOnClickListener { HumanHistoryCredits.show(activity) }
        })
        paragraph(getString(R.string.ct_life_method))
        CosmicSource.entries.forEach { source ->
            body.addView(AppCompatButton(this).apply {
                setText(source.label); isAllCaps = false
                setTextColor(palette.ink(palette.accent)); background = null
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(activity, R.string.ct_no_browser, Toast.LENGTH_LONG).show() }
                }
            })
            val titles = CosmicTimeline.events.filter { source in it.sources }.map { it.title } +
                TimelineChapters.all.filter { source in it.sources || source in TimelineReading.sources(it.id) }.map { it.title } +
                TimelineChapters.all.flatMap { TimelineReading.supplements(it.id) }
                    .filter { it.source == source }.map { it.title }
            paragraph(titles.distinct().joinToString(getString(R.string.ct_credit_separator)) { getString(it) })
        }
        val dialog = com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(R.string.ct_credits_title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.ct_close, null).create()
        dialog.followImmersiveMode(); dialog.show()
    }
}
