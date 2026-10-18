package com.Atom2Universe.app.games.kit

import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton

/** Difficulty, size and mode selectors share the app palette; board/data colors stay separate. */
object GameControlStyle {
    fun choice(button: MaterialButton, selected: Boolean) {
        val palette = KitPalette.from(button.context)
        button.isSelected = selected
        button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.accent else palette.raised)
        button.strokeColor = ColorStateList.valueOf(palette.outline)
        button.setTextColor(if (selected) palette.onAccent else palette.text)
    }
}
