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
}
