package com.Atom2Universe.app.studio.music

/** Répartition euclidienne sur les seize doubles croches d'une mesure. */
internal object BarRhythm {
    const val STEPS = 16

    fun offsetOf(velocity: FloatArray, hits: Int): Int {
        return (0 until STEPS).firstOrNull { offset ->
            val mask = steps(hits, offset)
            mask.indices.all { mask[it] == (velocity[it] > 0f) }
        } ?: 0
    }

    fun steps(hits: Int, offset: Int): BooleanArray {
        require(hits in 1..STEPS)
        val shift = Math.floorMod(offset, STEPS)
        return BooleanArray(STEPS) { step ->
            Math.floorMod((step - shift) * hits, STEPS) < hits
        }
    }
}
