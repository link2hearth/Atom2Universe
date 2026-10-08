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

        /** Pull fraction → power. Putts start finer so that short distances are easy to dose. */
        fun power(pull: Float, club: GolfClub): Float {
            val t = if (pull.isFinite()) pull.coerceIn(0f, 1f) else 0f
            return if (club == GolfClub.PUTTER) t.pow(1.6f) else t
        }

        /** Seconds for a left-right-left sweep: faster with a long swing and from a bad lie. */
        fun period(club: GolfClub, power: Float, lie: GolfLie): Float {
            val base = (if (club == GolfClub.PUTTER) 2.2f else 1.9f) - .5f * power.coerceIn(0f, 1f)
            return base * when (lie) { GolfLie.ROUGH -> .85f; GolfLie.SEMI_ROUGH -> .94f; GolfLie.BUNKER -> .8f; else -> 1f }
        }

        /** Signed miss felt by the ball: none inside the perfect band, then up to ±1 at the edges. */
        fun deviation(position: Float): Float {
            if (!position.isFinite()) return 1f
            val off = abs(position).coerceAtMost(1f)
            return if (off <= PERFECT) 0f else sign(position) * (off - PERFECT) / (1f - PERFECT)
        }
    }
}
