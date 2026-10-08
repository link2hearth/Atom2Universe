package com.Atom2Universe.app.games.caves.world

import kotlin.math.floor
import kotlin.math.pow

/** Shared, bounded progression for the exploration world. Saved items never depend on player Y. */
internal object MineralProgression {
    const val LAYER_HEIGHT = 250
    const val METALS = 9
    const val TIERS = 6
    const val LAST_STAGE = METALS * TIERS - 1

    fun stage(y: Double): Int = floor((-y).coerceAtLeast(0.0) / LAYER_HEIGHT)
        .coerceIn(0.0, LAST_STAGE.toDouble()).toInt()
    fun tier(stage: Int) = stage.coerceIn(0, LAST_STAGE) / METALS + 1
    fun metal(stage: Int) = stage.coerceIn(0, LAST_STAGE) % METALS
    fun scale(stage: Int): Float = 1.18.pow(stage.coerceIn(0, LAST_STAGE)).toFloat()

    /** Recent metals only; iron has its own permanent distribution. */
    fun localMetal(stage: Int, roll: Int): Int {
        val current = metal(stage)
        if (current <= 1) return current
        return when {
            roll < 65 -> current
            roll < 90 -> current - 1
            else -> (current - 2).coerceAtLeast(1)
        }
    }

    /** Iron of the next cycle may be processed to make that cycle's forge reinforcement. */
    fun forgeRequired(stage: Int) = if (metal(stage) == 0) tier(stage) - 1 else tier(stage)
}
