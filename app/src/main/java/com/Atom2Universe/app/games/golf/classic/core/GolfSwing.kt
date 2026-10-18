package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Accuracy needle of the draw-back shot: it sweeps edge to edge at constant speed while the
 * player holds; releasing near the centre gives a clean contact, off-centre bends the ball.
 */
class GolfSwing {
    var active = false
        private set
    /** −1 far left, 0 centre, +1 far right. */
    var position = 0f
        private set
    private var direction = 1f
    private var starts = 0
    /** Easy mode: wider bands and a slower needle (see the constants below). */
    var easy = false
    val perfect get() = if (easy) EASY_PERFECT else PERFECT
    val good get() = if (easy) EASY_GOOD else GOOD
    val miss get() = if (easy) EASY_MISS else MISS

    /** Starts from an edge, alternating sides, so an instant release is never a clean strike. */
    fun start() {
        val fromRight = starts++ % 2 == 1
        position = if (fromRight) 1f else -1f
        direction = if (fromRight) -1f else 1f
        active = true
    }

    fun update(dt: Float, period: Float) {
        if (!active || !dt.isFinite() || dt <= 0f || !(period > 0f)) return
        var travel = dt * 4f / period
        while (travel > 0f) {
            val edge = if (direction > 0f) 1f - position else position + 1f
            if (travel < edge) { position += direction * travel; travel = 0f }
            else { position += direction * edge; travel -= edge; direction = -direction }
        }
    }

    fun release(): Float { active = false; return position }
    fun cancel() { active = false }

    companion object {
        const val PERFECT = .07f
        const val GOOD = .3f
        const val MISS = .8f
        const val EASY_PERFECT = .22f
        const val EASY_GOOD = .55f
        const val EASY_MISS = .95f
        /** Share of the miss the ball really feels in easy mode: a poor release still flies nearly straight. */
        private const val EASY_FEEL = .4f
        private const val EASY_SLOWER = 1.6f

        /** Pull fraction → power. Putts start finer so that short distances are easy to dose. */
        fun power(pull: Float, club: GolfClub): Float {
            val t = if (pull.isFinite()) pull.coerceIn(0f, 1f) else 0f
            return if (club == GolfClub.PUTTER) t.pow(1.6f) else t
        }

        /** Seconds for a left-right-left sweep: faster with a long swing and from a bad lie. */
        fun period(club: GolfClub, power: Float, lie: GolfLie, easy: Boolean = false): Float {
            val base = (if (club == GolfClub.PUTTER) 2.2f else 1.9f) - .5f * power.coerceIn(0f, 1f)
            return base * (if (easy) EASY_SLOWER else 1f) *
                when (lie) { GolfLie.ROUGH -> .85f; GolfLie.SEMI_ROUGH -> .94f; GolfLie.BUNKER -> .8f; else -> 1f }
        }

        /** Signed miss felt by the ball: none inside the perfect band, then up to ±1 at the edges. */
        fun deviation(position: Float, easy: Boolean = false): Float {
            if (!position.isFinite()) return if (easy) EASY_FEEL else 1f
            val off = abs(position).coerceAtMost(1f)
            val perfect = if (easy) EASY_PERFECT else PERFECT
            val raw = if (off <= perfect) 0f else sign(position) * (off - perfect) / (1f - perfect)
            return if (easy) raw * EASY_FEEL else raw
        }
    }
}
