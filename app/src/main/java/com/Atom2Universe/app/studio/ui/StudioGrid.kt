package com.Atom2Universe.app.studio.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import kotlin.math.floor

/** Pas le plus proche, dans des groupes de quatre séparés par un espace. */
internal fun gridStep(x: Float, cellWidth: Float, gap: Float, steps: Int): Int {
    if (cellWidth <= 0f || steps <= 0) return -1
    val groupWidth = 4 * cellWidth + gap
    val group = floor((x + gap / 2f) / groupWidth).toInt()
    val local = floor((x - group * groupWidth) / cellWidth).toInt().coerceIn(0, 3)
    return (4 * group + local).coerceIn(0, steps - 1)
}

internal fun ghostOutline(density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 2.5f * density
    pathEffect = DashPathEffect(floatArrayOf(6 * density, 3 * density), 0f)
}

internal const val GHOST_LIGHT = 0xFF794500.toInt()
internal const val GHOST_DARK = 0xFFFFCF65.toInt()
