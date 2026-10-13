package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import com.Atom2Universe.app.R
import java.text.NumberFormat

/** Local islands; travelling changes the terrain and leaves every global progression system intact. */
class HamletWorldsDialog(
    private val context: Context,
    private val game: HamletGame,
    private val onChanged: () -> Unit = {},
    private val onSave: () -> Unit = {},
) {
    private val realms get() = game.realms
    private lateinit var dialog: AlertDialog
    private lateinit var rows: LinearLayout
    private val numbers = NumberFormat.getIntegerInstance()
    private var template = HamletRealms.Template.STARTER
    private fun dp(n: Int) = (context.resources.displayMetrics.density * n).toInt()
    private fun name(no: Int) = realms.name(no).ifBlank { context.getString(R.string.hamlet_realms_default_name, no) }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun show() {
        rows = column().apply { setPadding(dp(14), dp(4), dp(14), dp(16)); setBackgroundColor(0xFFF4ECD9.toInt()) }
        val scroll = ScrollView(context).apply { addView(rows); isFillViewport = true }
        dialog = AlertDialog.Builder(context).setTitle(R.string.hamlet_realms_title)
            .setView(scroll).setNegativeButton(R.string.hamlet_realms_close, null).create()
        dialog.show()
        dialog.window?.setLayout(-1, minOf(context.resources.displayMetrics.heightPixels * 85 / 100, dp(720)))
        render()
    }

    private fun text(parent: LinearLayout, label: String, title: Boolean = false) {
        parent.addView(TextView(context).apply {
            text = label; textSize = if (title) 18f else 14f; setTextColor(0xFF304B43.toInt())
            if (title) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        })
    }

    private fun card(active: Boolean = false): LinearLayout = column().also {
        it.setPadding(dp(14), dp(10), dp(14), dp(10))
        it.background = GradientDrawable().apply {
            setColor(if (active) 0xFFE0EBD8.toInt() else 0xFFECE2CC.toInt())
            cornerRadius = dp(15).toFloat(); setStroke(dp(1), if (active) 0xFF8DAA83.toInt() else 0xFFD9CBA9.toInt())
        }
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun action(parent: LinearLayout, label: String, enabled: Boolean = true, run: () -> Unit) {
        parent.addView(Button(context).apply {
            text = label; textSize = 14f; isAllCaps = false; isEnabled = enabled; minHeight = dp(48)
            setOnClickListener { run() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun render() {
        rows.removeAllViews()
        text(rows, context.getString(R.string.hamlet_realms_shared))
        for (entry in realms.entries()) {
            val card = card(entry.current)
            text(card, name(entry.no), true)
            text(card, context.getString(R.string.hamlet_realms_summary, entry.no, entry.hall, entry.size, entry.size, entry.objects))
            if (entry.current) text(card, context.getString(R.string.hamlet_realms_current))
            else action(card, context.getString(R.string.hamlet_realms_travel)) {
                if (realms.switchTo(entry.no)) { onChanged(); onSave(); dialog.dismiss() }
            }
            action(card, context.getString(R.string.hamlet_realms_rename)) { rename(entry.no) }
        }
        val founding = card()
        val next = realms.count + 1
        val requirement = realms.requirement(next)
        text(founding, context.getString(R.string.hamlet_realms_found_title, next), true)
        text(founding, context.getString(R.string.hamlet_realms_found_explain))
        text(founding, if (game.sandbox) context.getString(R.string.hamlet_realms_creative_free)
            else context.getString(R.string.hamlet_realms_requirement, requirement.level, numbers.format(requirement.cost)))
        if (game.sandbox) {
            text(founding, context.getString(R.string.hamlet_realms_template))
            founding.addView(Spinner(context).apply {
                val labels = intArrayOf(R.string.hamlet_realms_template_starter, R.string.hamlet_realms_template_empty, R.string.hamlet_realms_template_showcase)
                    .map { context.getString(it) }
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
                contentDescription = context.getString(R.string.hamlet_realms_template)
                setSelection(template.ordinal)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { template = HamletRealms.Template.entries[position] }
                }
            })
        }
        action(founding, context.getString(R.string.hamlet_realms_found), realms.canFound()) {
            if (realms.found(if (game.sandbox) template else HamletRealms.Template.STARTER)) { onChanged(); onSave(); dialog.dismiss() }
        }
    }

    private fun rename(no: Int) {
        val input = EditText(context).apply {
            setText(realms.name(no)); hint = context.getString(R.string.hamlet_realms_default_name, no)
            filters = arrayOf(InputFilter.LengthFilter(40)); isSingleLine = true
            setPadding(dp(18), dp(12), dp(18), dp(12)); gravity = Gravity.START
        }
        AlertDialog.Builder(context).setTitle(R.string.hamlet_realms_rename).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.hamlet_realms_save) { _, _ ->
                if (realms.rename(no, input.text.toString())) { onSave(); render() }
            }.show()
    }
}
