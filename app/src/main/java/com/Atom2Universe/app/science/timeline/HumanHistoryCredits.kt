package com.Atom2Universe.app.science.timeline

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
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
import java.text.Normalizer
import java.util.Locale

/** Only opened from the main hub's About references. */
object HumanHistoryCredits {
    fun show(activity: FragmentActivity) = with(activity) {
        val palette = SciencePalette(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding)
        }
        fun text(parent: LinearLayout, value: String) { parent.addView(TextView(this).apply {
            text = value; textSize = 14f; setTextColor(palette.secondary)
            setPadding(0, padding / 2, 0, padding / 2); setTextIsSelectable(true)
        }) }
        text(body, getString(R.string.ct_history_method))
        text(body, getString(R.string.ct_science_method))
        val input = EditText(this).apply {
            setHint(R.string.ct_history_reference_search); setSingleLine(true)
            setTextColor(palette.text); setHintTextColor(palette.secondary)
        }
        body.addView(input)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        // Index once: filtering references should not repeatedly localize every record.
        val records = HumanHistory.entries.associateWith { searchable(it.indexedText(activity)) }
        val references = HumanSource.entries.map { source ->
            Triple(source, searchable(getString(source.label)), records.keys.filter { it.hasSource(source) })
        }
        fun update(query: String) {
            results.removeAllViews()
            val term = searchable(query)
            references.forEach { (source, label, entries) ->
                val matches = if (term in label) entries else entries.filter { term in records.getValue(it) }
                if (matches.isEmpty()) return@forEach
                results.addView(AppCompatButton(this).apply {
                    setText(source.label); isAllCaps = false; background = null
                    setTextColor(palette.ink(palette.accent))
                    setOnClickListener {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(activity, R.string.ct_no_browser, Toast.LENGTH_LONG).show() }
                    }
                })
                matches.forEach {
                    text(results, getString(R.string.ct_catalog_entry, getString(it.title), it.dateLabel(activity)))
                }
            }
            if (results.childCount == 0) text(results, getString(R.string.ct_search_empty))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update(s.toString())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        update("")
        val dialog = AlertDialog.Builder(this).setTitle(R.string.ct_history_sources)
            .setView(ScrollView(this).apply { addView(body) }).setPositiveButton(R.string.ct_close, null).create()
        dialog.followImmersiveMode(); dialog.show()
    }

    private fun searchable(value: String) =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
