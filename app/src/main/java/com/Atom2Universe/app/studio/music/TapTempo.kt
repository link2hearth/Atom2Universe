package com.Atom2Universe.app.studio.music

import kotlin.math.roundToInt

/** La médiane des derniers intervalles amortit un battement légèrement en avance ou en retard. */
internal class TapTempo {
    private val intervals = ArrayDeque<Long>()
    private var last: Long? = null

    fun tap(nowMs: Long): Int? {
        val previous = last
        last = nowMs
        if (previous == null) return null
        val dt = nowMs - previous
        if (dt !in 250L..1650L) {
            intervals.clear()
            return null
        }
        if (intervals.size == 5) intervals.removeFirst()
        intervals.addLast(dt)
        val sorted = intervals.sorted()
        val median = (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) * 0.5
        return (60_000 / median).roundToInt().coerceIn(40, 220)
    }
}
