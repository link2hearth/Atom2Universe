package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.Atom2Universe.app.R
import java.util.Date

internal class HamletChronicleDialog(private val context: Context, private val game: HamletGame) {
    private fun dp(value: Int) = HamletUi.dp(context, value)
    private fun named(prefix: String, key: String): String {
        val id = context.resources.getIdentifier(prefix + key, "string", context.packageName)
        return if (id != 0) context.getString(id) else context.getString(R.string.hamlet_title)
    }
    fun show() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)); setBackgroundColor(HamletUi.PAPER) }
        val dateFormat = android.text.format.DateFormat.getDateFormat(context)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        if (game.chronicle.rows.isEmpty()) content.addView(TextView(context).apply { setText(R.string.hamlet_chronicle_empty); setTextColor(HamletUi.INK) })
        for (entry in game.chronicle.rows) {
            val actor = if (entry.subject.startsWith("neighbor:")) named("hamlet_neighbors_name_", entry.subject.removePrefix("neighbor:")) else entry.actor
            val message = when (entry.kind) {
                HamletChronicle.Kind.WELCOME -> context.getString(R.string.hamlet_chronicle_welcome)
                HamletChronicle.Kind.LEVEL -> context.getString(R.string.hamlet_chronicle_level, entry.amount)
                HamletChronicle.Kind.DELIVERY -> context.getString(R.string.hamlet_chronicle_delivery, entry.actor, named("hamlet_n_", entry.subject))
                HamletChronicle.Kind.GIFT -> context.getString(R.string.hamlet_chronicle_gift)
                HamletChronicle.Kind.EXPANSION -> context.getString(R.string.hamlet_chronicle_expansion, entry.amount)
                HamletChronicle.Kind.HALL -> context.getString(R.string.hamlet_chronicle_hall, entry.amount)
                HamletChronicle.Kind.REALM -> context.getString(R.string.hamlet_chronicle_realm, entry.subject)
                HamletChronicle.Kind.VISIT -> context.getString(R.string.hamlet_chronicle_visit, actor)
                HamletChronicle.Kind.ORDER -> context.getString(R.string.hamlet_chronicle_order, actor)
                HamletChronicle.Kind.PICKUP -> context.getString(R.string.hamlet_chronicle_pickup, actor)
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; background = HamletUi.panel(context, 0xFFFFF8E9.toInt(), 12, 0xFFE1D4B8.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            card.addView(TextView(context).apply { text = message; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(HamletUi.INK) })
            val date = Date(entry.at)
            card.addView(TextView(context).apply {
                text = context.getString(R.string.hamlet_chronicle_date, dateFormat.format(date), timeFormat.format(date))
                textSize = 11f; setTextColor(0xFF71806D.toInt()); setPadding(0, dp(5), 0, 0)
            })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        AlertDialog.Builder(context).setTitle(R.string.hamlet_chronicle_title)
            .setView(ScrollView(context).apply { addView(content) }).setPositiveButton(android.R.string.ok, null).show()
    }
}
