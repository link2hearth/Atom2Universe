package com.Atom2Universe.app.games.toyboxracers.driving

import kotlin.math.exp
import kotlin.math.pow

/** Même moteur et même enveloppe de turbo pour le joueur et les adversaires. */
internal object RacerTuning {
    const val MAX_SPEED = 22f
    const val ENGINE_ACCELERATION = 10.5f
    const val CORNERING_GRIP = 26f
    const val TURBO_ACCELERATION = 14f
    const val TURBO_RELEASE_IMPULSE = 6.5f
    const val FULL_CHARGE_SECONDS = 6.5f
    const val MIN_BOOST_DURATION = .12f
    const val MAX_BOOST_PEAK = 1.73f
    const val OVERSPEED_DECELERATION = 9f

    fun boostDuration(effectiveSeconds: Float): Float {
        if (effectiveSeconds <= 0f) return 0f
        return (4.2f * (1f - exp(-(effectiveSeconds / 4f).pow(1.3f))) + effectiveSeconds * .12f)
            .coerceAtMost(4.2f)
    }

    fun boostPeak(effectiveSeconds: Float): Float =
        (1.18f + .55f * (1f - exp(-effectiveSeconds.coerceAtLeast(0f) / 2.3f)))
            .coerceAtMost(MAX_BOOST_PEAK)

    /** Plateau franc, puis extinction douce sur le dernier cinquième. */
    fun boostMultiplier(peak: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val sustain = 1f + (peak - 1f) * .72f
        val t = if (p < .78f) p / .78f else (p - .78f) / .22f
        val eased = t * t * (3f - 2f * t)
        return if (p < .78f) peak + (sustain - peak) * eased else sustain + (1f - sustain) * eased
    }
}
