package com.Atom2Universe.app.games.roguelike

import kotlin.math.roundToInt

/** HP pacing only; damage and cadence are unchanged. */
internal object HitPointBalance {
    const val PLATEAU_FLOOR = 250
    private fun progress(floor: Int) = (floor - 1).coerceIn(0, PLATEAU_FLOOR - 1).toDouble() / (PLATEAU_FLOOR - 1)
    fun playerMultiplier(floor: Int) = 2.5 + 1.5 * progress(floor)
    fun enemyMultiplier(floor: Int) = 2.5 + 2.5 * progress(floor)
    fun playerHp(base: Int, floor: Int) = (base * playerMultiplier(floor)).roundToInt()
    fun enemyHp(base: Int, floor: Int) = (base * enemyMultiplier(floor)).roundToInt()

    /** Old saves may contain no multiplier (1), fixed 5, or the current fractional value. */
    fun restore(hp: Int, savedMultiplier: Double, floor: Int): Int =
        (hp * playerMultiplier(floor) / savedMultiplier.coerceAtLeast(1.0))
            .coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt()
}
