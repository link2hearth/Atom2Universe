package com.Atom2Universe.app.graphics

import kotlin.random.Random

/** Horloge propre à chaque soleil, partagée par Canvas et OpenGL. */
internal class SunAnimationState {
    private var elapsed = 0L
    private var eruptionStart = -1L
    private var eruptionDuration = 0L
    private var nextEruption = Random.nextLong(25_000L, 55_001L)
    val shape = FloatArray(4)
    val detail = FloatArray(4)
    val phase: Float get() = elapsed.toFloat() / 3000f
    var eruptionAge = 0f
        private set

    fun advance(deltaMs: Long) {
        elapsed += deltaMs.coerceAtLeast(0L)
        if (eruptionStart >= 0L && elapsed - eruptionStart >= eruptionDuration) {
            eruptionStart = -1L
            nextEruption = elapsed + Random.nextLong(25_000L, 55_001L)
        }
        if (eruptionStart < 0L && elapsed >= nextEruption) {
            eruptionStart = elapsed
            eruptionDuration = Random.nextLong(8_000L, 14_001L)
            shape[0] = Random.nextFloat() * 6.283185f
            shape[1] = 0.16f + Random.nextFloat() * 0.20f
            shape[2] = 0.07f + Random.nextFloat() * 0.19f
            shape[3] = (Random.nextFloat() - 0.5f) * 1.3f
            detail[0] = 0.35f + Random.nextFloat() * 1.8f
            detail[1] = 8f + Random.nextFloat() * 19f
            detail[2] = 0.009f + Random.nextFloat() * 0.023f
            detail[3] = Random.nextFloat() * 100f
        }
        eruptionAge = if (eruptionStart < 0L) 0f else (elapsed - eruptionStart).toFloat() / eruptionDuration
    }
}
