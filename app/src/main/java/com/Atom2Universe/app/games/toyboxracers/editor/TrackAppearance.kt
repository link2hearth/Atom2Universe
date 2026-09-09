package com.Atom2Universe.app.games.toyboxracers.editor

import com.Atom2Universe.app.R

internal enum class TrackStyle(val label: Int, val tint: Int, val suggestedWidth: Float, val accent: Int) {
    CLASSIC(R.string.toybox_style_classic, 0xFF6F7B91.toInt(), 9f, 0xFFFFE7A8.toInt()),
    DIRT(R.string.toybox_style_dirt, 0xFF99643D.toInt(), 7f, 0xFFD2A571.toInt()),
    GRASS(R.string.toybox_style_grass, 0xFF508446.toInt(), 6f, 0xFFA3BC62.toInt()),
    SAND(R.string.toybox_style_sand, 0xFFDEC18A.toInt(), 12f, 0xFFFFE5AD.toInt()),
    ICE(R.string.toybox_style_ice, 0xFF94DDE9.toInt(), 10f, 0xFFE9FFFF.toInt()),
    WOOD(R.string.toybox_style_wood, 0xFFB97C49.toInt(), 5f, 0xFF5B3929.toInt()),
    NEON(R.string.toybox_style_neon, 0xFF292642.toInt(), 8f, 0xFF44FFDD.toInt());

    companion object {
        fun parse(name: String) = entries.firstOrNull { it.name == name } ?: CLASSIC
        const val BARRIER_HEIGHT = 1.3f
        const val BARRIER_THICKNESS = 0.22f
    }
}
