package com.Atom2Universe.app.games.jigsaw

import kotlin.math.sin

/** Presentation only: the solved groups and reward receipt never change during the animation. */
class JigsawVictoryMotion(private val size: JigsawGrid) {
    constructor(size: JigsawSize) : this(JigsawGrid.portrait(size))
    fun lift(id: Int, progress: Float): Float {
        val wave = (id % size.columns + id / size.columns).toFloat() / (size.columns + size.rows - 2)
        val local = ((progress - .035f - wave * .045f) / .285f).coerceIn(0f, 1f)
        if (local == 0f || local == 1f) return 0f
        val height = sin(local * Math.PI).toFloat()
        return height * height
    }

    companion object {
        const val DURATION_MS = 5400L
        const val FUSED_AT = .37f
        fun ease(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        fun camera(progress: Float) = ease(progress / .14f)
        fun unlace(progress: Float) = ease((progress - .40f) / .54f)
        fun frameAlpha(progress: Float) = 1f - ease((progress - .37f) / .30f)
    }
}
