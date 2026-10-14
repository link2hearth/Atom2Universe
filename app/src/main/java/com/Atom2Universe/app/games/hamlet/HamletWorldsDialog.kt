package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight
import java.text.NumberFormat

/** Local islands; travelling changes the terrain and leaves every global progression system intact. */
class HamletWorldsDialog(
    private val context: Context,
    private val game: HamletGame,
    private val onChanged: () -> Unit = {},
    private val onSave: () -> Unit = {},
) {
    private val realms get() = game.realms
    private lateinit var sheet: HamletSheet
    private val rows get() = sheet.body
    private val numbers = NumberFormat.getIntegerInstance()
    private var template = HamletRealms.Template.STARTER
    private fun dp(n: Int) = HamletUi.dp(context, n)
    private fun name(no: Int) = realms.name(no).ifBlank { context.getString(R.string.hamlet_realms_default_name, no) }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun show() {
        sheet = HamletSheet(context, context.getString(R.string.hamlet_realms_title), context.getString(R.string.hamlet_realms_shared), tall = true)
        render(); sheet.show()
    }

    private fun text(parent: LinearLayout, label: CharSequence, title: Boolean = false, small: Boolean = false, color: Int = 0) = TextView(context).apply {
        text = label; textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        setTextColor(if (color != 0) color else if (small) HamletUi.MUTED else HamletUi.INK)
        if (title) setTypeface(null, Typeface.BOLD)
        setPadding(dp(2), dp(4), dp(2), dp(4))
    }.also { parent.addView(it) }

    private fun card(active: Boolean = false): LinearLayout = column().also {
        it.setPadding(dp(13), dp(8), dp(13), dp(10))
        it.background = HamletUi.panel(context, if (active) 0xFFE3ECD3.toInt() else 0xFFFFF8E9.toInt(), 14, if (active) 0xFF8DAA83.toInt() else 0xFFD9CBA9.toInt())
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun action(parent: LinearLayout, label: String, weight: Weight = Weight.NORMAL, glyph: String? = null, enabled: Boolean = true, run: () -> Unit) {
        parent.addView(HamletUi.button(context, label, weight, glyph = glyph, click = run).apply { isEnabled = enabled },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }

    private fun render() {
        rows.removeAllViews()
        for (entry in realms.entries()) {
            val card = card(entry.current)
            val head = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(context).apply {
                text = name(entry.no); textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (entry.current) head.addView(TextView(context).apply {
                setText(R.string.hamlet_realms_current); textSize = HamletUi.SMALL; setTextColor(HamletUi.GOOD); setTypeface(null, Typeface.BOLD)
                setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(context, "tick", 16), null, null, null); compoundDrawablePadding = dp(4)
            })
            card.addView(head)
            text(card, context.getString(R.string.hamlet_realms_summary, entry.no, entry.hall, entry.size, entry.size, entry.objects), small = true)
            val actions = LinearLayout(context)
            if (!entry.current) actions.addView(HamletUi.button(context, context.getString(R.string.hamlet_realms_travel), glyph = "ship") {
                if (realms.switchTo(entry.no)) { onChanged(); onSave(); sheet.dismiss() }
            }, LinearLayout.LayoutParams(0, -2, 1.3f).apply { marginEnd = dp(4) })
            actions.addView(HamletUi.button(context, context.getString(R.string.hamlet_realms_rename), Weight.QUIET, glyph = "brush") { rename(entry.no) },
                LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        val founding = card()
        val next = realms.count + 1
        val requirement = realms.requirement(next)
        val head = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = context.getString(R.string.hamlet_realms_found_title, next); textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(HamletUi.info(context, context.getString(R.string.hamlet_realms_found_title, next), context.getString(R.string.hamlet_realms_found_explain)),
            LinearLayout.LayoutParams(dp(44), -2))
        founding.addView(head)
        text(founding, if (game.sandbox) context.getString(R.string.hamlet_price_free)
            else context.getString(R.string.hamlet_realms_requirement, requirement.level, numbers.format(requirement.cost)), small = true)
        if (game.sandbox) {
            text(founding, context.getString(R.string.hamlet_realms_template), small = true)
            val labels = intArrayOf(R.string.hamlet_realms_template_starter, R.string.hamlet_realms_template_empty, R.string.hamlet_realms_template_showcase)
            for ((i, label) in labels.withIndex()) founding.addView(HamletUi.row(context, context.getString(label), template.ordinal == i) {
                template = HamletRealms.Template.entries[i]; render()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        action(founding, context.getString(R.string.hamlet_realms_found), Weight.MAIN, "castle", realms.canFound()) {
            if (realms.found(if (game.sandbox) template else HamletRealms.Template.STARTER)) { onChanged(); onSave(); sheet.dismiss() }
        }
    }

    private fun rename(no: Int) {
        val input = HamletUi.field(EditText(context)).apply {
            setText(realms.name(no)); hint = context.getString(R.string.hamlet_realms_default_name, no)
            filters = arrayOf(InputFilter.LengthFilter(40)); isSingleLine = true; gravity = Gravity.START
        }
        val dialog = HamletSheet(context, context.getString(R.string.hamlet_realms_rename))
        dialog.body.addView(input)
        dialog.footer(HamletUi.button(context, context.getString(android.R.string.cancel), Weight.QUIET) { dialog.dismiss() } to 1f,
            HamletUi.button(context, context.getString(R.string.hamlet_realms_save), Weight.MAIN, glyph = "brush") {
                if (realms.rename(no, input.text.toString())) { onSave(); render() }
                dialog.dismiss()
            } to 1.5f)
        dialog.show()
    }
}
