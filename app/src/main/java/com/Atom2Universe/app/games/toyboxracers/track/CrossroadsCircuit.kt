package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.sin

/**
 * Circuit démonstrateur à plusieurs croisements. La trajectoire horizontale est
 * une lemniscate généralisée x = A·sin(θ), z = B·sin(4θ) : le facteur 4 (au lieu
 * de 2 pour le grand huit) fait repasser la courbe exactement par le même point
 * à trois reprises (θ et π−θ donnent toujours le même x, et sin(4θ) s'annule
 * aux mêmes θ que sin(4(π−θ)) change juste de signe) — trois croisements nets,
 * tous alignés sur z = 0, sans qu'aucune coordonnée de contrôle n'ait besoin
 * d'être devinée à la main.
 *
 * Les trois croisements sont utilisés différemment : le premier (centre) est un
 * saut classique façon grand huit, le second (côté positif) est un pont fixe
 * sans aucun vide, le troisième (côté négatif) est un second saut, plus bas.
 */
internal object CrossroadsCircuit {
    private const val AMPLITUDE_X = 95f
    private const val AMPLITUDE_Z = 50f

    private const val BRIDGE_CLIMB_START = 0.125f
    private const val BRIDGE_PEAK_START = 0.220f
    const val BRIDGE_PEAK_END = 0.375f
    private const val BRIDGE_DESCEND_END = 0.430f
    private const val BRIDGE_HEIGHT = 6f

    /** Fraction plate et alignée sur un axe du monde : là où le décor tunnel se pose. */
    const val TUNNEL_FRACTION = 0.5625f

    const val GAP0_START = 0.485f
    const val GAP0_END = 0.515f
    private const val HIGH_A = 8f
    private const val RAMP_A = 1.6f

    private const val JUMP2_CLIMB_START = 0.625f
    const val GAP2_START = 0.800f
    const val GAP2_END = 0.830f
    private const val HIGH_B = 6f
    private const val RAMP_B = 1.3f

    fun point(fraction: Float): Vec3 {
        val theta = fraction * 2f * PI.toFloat()
        val x = AMPLITUDE_X * sin(theta)
        val z = AMPLITUDE_Z * sin(4f * theta)
        return Vec3(x, altitude(fraction), z)
    }

    fun width(fraction: Float): Float {
        val nearGap0 = fraction in (GAP0_START - 0.03f)..(GAP0_END + 0.03f)
        val nearGap2 = fraction in (GAP2_START - 0.03f)..(GAP2_END + 0.03f)
        return if (nearGap0 || nearGap2) 11f else 8.5f
    }

    private fun altitude(fraction: Float): Float = when {
        fraction < BRIDGE_CLIMB_START -> 0f
        fraction < BRIDGE_PEAK_START -> smoothStep(
            (fraction - BRIDGE_CLIMB_START) / (BRIDGE_PEAK_START - BRIDGE_CLIMB_START)
        ) * BRIDGE_HEIGHT
        fraction < BRIDGE_PEAK_END -> BRIDGE_HEIGHT
        fraction < BRIDGE_DESCEND_END -> BRIDGE_HEIGHT * (1f - smoothStep(
            (fraction - BRIDGE_PEAK_END) / (BRIDGE_DESCEND_END - BRIDGE_PEAK_END)
        ))
        fraction < GAP0_START -> smoothStep(
            (fraction - BRIDGE_DESCEND_END) / (GAP0_START - BRIDGE_DESCEND_END)
        ) * (HIGH_A + RAMP_A)
        fraction < GAP0_END -> HIGH_A + RAMP_A
        fraction < JUMP2_CLIMB_START -> 0f
        fraction < GAP2_START -> smoothStep(
            (fraction - JUMP2_CLIMB_START) / (GAP2_START - JUMP2_CLIMB_START)
        ) * (HIGH_B + RAMP_B)
        fraction < GAP2_END -> HIGH_B + RAMP_B
        else -> 0f
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
