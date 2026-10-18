package com.Atom2Universe.app.games.caves

import android.content.Context

internal object CaveVisualStyle {
    enum class Theme(val label: Int) {
        PASTEL(com.Atom2Universe.app.R.string.cave_palette_pastel),
        VIVID(com.Atom2Universe.app.R.string.cave_palette_vivid),
        GRAYSCALE(com.Atom2Universe.app.R.string.cave_palette_grayscale)
    }

    fun current(context: Context): Theme {
        val prefs = context.getSharedPreferences("cave_visual_style", Context.MODE_PRIVATE)
        return Theme.entries.firstOrNull { it.name == prefs.getString("theme", null) }
            ?: if (prefs.getBoolean("vivid", false)) Theme.VIVID else Theme.PASTEL
    }

    fun isVivid(context: Context) = current(context) == Theme.VIVID

    fun cycle(context: Context) {
        val theme = Theme.entries[(current(context).ordinal + 1) % Theme.entries.size]
        context.getSharedPreferences("cave_visual_style", Context.MODE_PRIVATE).edit()
            .putString("theme", theme.name).apply()
    }

    fun setVivid(context: Context, vivid: Boolean) {
        context.getSharedPreferences("cave_visual_style", Context.MODE_PRIVATE).edit()
            .putString("theme", (if (vivid) Theme.VIVID else Theme.PASTEL).name).apply()
    }
}
