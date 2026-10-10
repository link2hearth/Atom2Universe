package com.Atom2Universe.app.science.timeline

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Continuous civil-time viewport. Calculations stay in years, away from cosmic rounding. */
class HumanTimeWindow(start: Double = RECENT_MIN, span: Double = RECENT_SPAN) {
    var start = RECENT_MIN
        private set
    var span = RECENT_SPAN
        private set
    val end get() = start + span
    val center get() = start + span / 2
    val isRecent get() = start >= RECENT_MIN && span <= RECENT_SPAN
    val navigationMin get() = if (isRecent) RECENT_MIN else MIN

    init { set(start, span) }

    fun set(first: Double, duration: Double) {
        if (!first.isFinite() || !duration.isFinite()) return
        span = duration.coerceIn(MIN_SPAN, MAX - MIN)
        start = first.coerceIn(MIN, MAX - span)
    }

    fun pan(years: Double) = set((start + years).coerceIn(navigationMin, MAX - span), span)

    fun recentOverview() = set(RECENT_MIN, RECENT_SPAN)

    fun century() {
        val first = if (!isRecent || span >= RECENT_SPAN - .001) MAX - CENTURY
            else (center - CENTURY / 2).coerceIn(RECENT_MIN, MAX - CENTURY)
        set(first, CENTURY)
    }

    /** The scrubber moves the window across recent history without changing its duration. */
    fun seekRecent(fraction: Double) {
        if (!fraction.isFinite()) return
        val duration = minOf(span, RECENT_SPAN)
        set(RECENT_MIN + fraction.coerceIn(0.0, 1.0) * (RECENT_SPAN - duration), duration)
    }

    /** Anchor remains under the same finger unless a domain boundary is reached. */
    fun zoom(factor: Double, fraction: Double = .5) {
        if (!factor.isFinite() || factor <= 0) return
        val anchor = fraction.coerceIn(0.0, 1.0)
        val minimum = navigationMin
        val duration = (span / factor).coerceIn(MIN_SPAN, MAX - minimum)
        set((start + anchor * (span - duration)).coerceIn(minimum, MAX - duration), duration)
    }

    fun focus(first: Double, last: Double, padding: Boolean = false) {
        // Nearby scientific milestones need a useful close-up when located from search.
        val duration = maxOf(if (padding) 12.0 else MIN_SPAN, (last - first) * if (padding) 1.3 else 1.0)
        set((first + last - duration) / 2, duration)
    }

    fun intersects(first: Int, last: Int): Boolean = last.toDouble() >= start && first.toDouble() <= end

    /** Nice civil-year ticks on both sides of the era boundary, never a displayed year zero. */
    fun ticks(maxLabels: Int): List<Int> {
        // Leave room for edge ticks: rounding to a nice step must not reduce a whole view to one date.
        val rough = span / (maxLabels.coerceAtLeast(2) + 1)
        val magnitude = 10.0.pow(floor(log10(rough)))
        val step = (listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= rough } * magnitude).toInt().coerceAtLeast(1)
        val result = mutableListOf<Int>()
        val firstCe = maxOf(1, ceil(start / step).toInt())
        val lastCe = floor(end / step).toInt()
        if (firstCe <= lastCe) for (n in firstCe..lastCe) result.add(n * step)
        val firstBce = maxOf(1, ceil((1 - end) / step).toInt())
        val lastBce = floor((1 - start) / step).toInt()
        if (firstBce <= lastBce) for (n in firstBce..lastBce) result.add(1 - n * step)
        // At close range, show the boundary as 1 CE; 1 BCE is astronomical year 0.
        if (start <= 1 && end >= 1 && result.none { kotlin.math.abs(it - 1) < step * .6 }) result.add(1)
        return result.sorted()
    }

    companion object {
        const val MIN = -297974.0 // 300,000 years before the fixed editorial endpoint.
        const val MAX = 2026.0
        const val MIN_SPAN = 2.0
        const val RECENT_MIN = -3999.0 // 4000 BCE in astronomical year numbering.
        const val RECENT_SPAN = MAX - RECENT_MIN
        const val CENTURY = 100.0
    }
}
