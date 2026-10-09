package com.Atom2Universe.app.science.biology

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

/** Shared by the hub's About page and the anatomy module. */
object AnatomyJourneyCredits {
    fun show(activity: FragmentActivity) = with(activity) {
        val palette = SciencePalette(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun paragraph(value: String) {
            content.addView(TextView(this).apply {
                text = value; textSize = 14f; setTextColor(palette.text)
                setTextIsSelectable(true); setPadding(0, padding / 2, 0, padding / 2)
            })
        }
        paragraph(getString(R.string.bio_j_source_method))
        AnatomyJourneys.all.forEach { journey ->
            paragraph(getString(journey.title))
            journey.steps.forEach { step ->
                paragraph(getString(step.title))
                step.sources.forEach { source ->
                    content.addView(AppCompatButton(this).apply {
                        setText(source.label); isAllCaps = false
                        setOnClickListener {
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
                            catch (_: ActivityNotFoundException) {
                                Toast.makeText(activity, R.string.bio_link_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bio_j_sources)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.bio_close, null).create()
        dialog.followImmersiveMode(); dialog.show()
    }
}
