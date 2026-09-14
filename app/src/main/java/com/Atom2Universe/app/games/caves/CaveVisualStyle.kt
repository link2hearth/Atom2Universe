package com.Atom2Universe.app.games.caves

import android.content.Context

internal object CaveVisualStyle {
    fun isVivid(context: Context): Boolean =
        context.getSharedPreferences("cave_visual_style", Context.MODE_PRIVATE).getBoolean("vivid", false)

    fun setVivid(context: Context, vivid: Boolean) {
        context.getSharedPreferences("cave_visual_style", Context.MODE_PRIVATE).edit().putBoolean("vivid", vivid).apply()
    }
}
