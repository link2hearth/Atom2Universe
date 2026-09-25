package com.Atom2Universe.app.games.caves

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button

internal object CaveUiStyle {
    const val SURFACE = 0xFF23332D.toInt()
    const val CARD = 0xFF30453B.toInt()
    const val SELECTED = 0xFF4C6651.toInt()
    const val TEXT = 0xFFF5F1E7.toInt()
    const val MUTED = 0xFFBDCCBF.toInt()
    const val ACCENT = 0xFFC5DDB2.toInt()
    const val BORDER = 0xFF577263.toInt()
    const val WARNING = 0xFFE8B5A5.toInt()
    fun dp(context: Context, n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun panel(context: Context, color: Int = CARD, stroke: Int = BORDER, selected: Boolean = false) =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(context, 12).toFloat()
            setStroke(dp(context, if (selected) 2 else 1), stroke)
        }
    fun button(button: Button, primary: Boolean = false) {
        button.isAllCaps = false; button.textSize = 13f
        button.minWidth = 0; button.minimumWidth = 0
        button.setPadding(dp(button.context, 10), 0, dp(button.context, 10), 0)
        button.setTextColor(if (primary) SURFACE else TEXT)
        button.backgroundTintList = null
        button.background = RippleDrawable(ColorStateList.valueOf(0x337DAD8B),
            panel(button.context, if (primary) ACCENT else CARD), null)
    }
    fun accessible(view: View, label: String) { view.contentDescription = label; view.isFocusable = true }
    fun icon(button: Button,kind: String,label: String,active: Boolean=false) {
        button(button, false);button.text="";button.contentDescription=label;button.tooltipText=label
        val glyph=android.graphics.drawable.InsetDrawable(CaveActionDrawable(kind),dp(button.context,11))
        button.background=RippleDrawable(ColorStateList.valueOf(0x337DAD8B),android.graphics.drawable.LayerDrawable(arrayOf(
            panel(button.context,if(active) SELECTED else CARD,if(active) ACCENT else BORDER,active),glyph)),null)
        button.isSelected=active
    }
    fun count(context: Context,n: Int): String = when {
        n>=1_000_000 -> context.getString(com.Atom2Universe.app.R.string.cave_count_million,n/1_000_000.0)
        n>=10_000 -> context.getString(com.Atom2Universe.app.R.string.cave_count_thousand,n/1000.0)
        else -> java.text.NumberFormat.getIntegerInstance().format(n)
    }
}
