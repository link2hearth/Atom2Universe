package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.PI
import kotlin.math.floor

/** Broad bays and headlands, rather than a repeated scallop around an ellipse.
 * Radii stay inside the authored footprint. Periodic, monotone cubic interpolation keeps
 * shores smooth without overshooting into a neighbouring landing area. Phase and the
 * hazard's rotation/aspect give each instance its own orientation and proportions.
 */
enum class GolfShoreline(private vararg val radii: Float) {
    COVE(1f, .98f, .87f, .66f, .60f, .71f, .94f, 1f,
        .97f, .93f, .90f, .94f, 1f, .94f, .87f, .93f),
    HEADLAND(1f, .97f, .80f, .72f, .80f, .96f, .99f, .87f,
        .76f, .84f, .98f, .93f, .76f, .64f, .72f, .93f),
    LAGOON(.97f, 1f, .95f, .80f, .71f, .78f, .94f, .98f,
        .88f, .70f, .61f, .74f, .96f, 1f, .91f, .89f),
    TAPER(.99f, .98f, .92f, .84f, .75f, .70f, .73f, .85f,
        .99f, 1f, .96f, .86f, .70f, .66f, .77f, .93f);

    private val slopes = FloatArray(radii.size) { i ->
        val before = radii[i] - radii[(i + radii.size - 1) % radii.size]
        val after = radii[(i + 1) % radii.size] - radii[i]
        if (before * after <= 0f) 0f else 2f * before * after / (before + after)
    }

    fun radius(angle: Float): Float {
        val turns = angle / (2f * PI.toFloat())
        val position = (turns - floor(turns)) * radii.size
        val i = position.toInt().coerceAtMost(radii.lastIndex)
        val j = (i + 1) % radii.size
        val t = position - i
        val t2 = t * t; val t3 = t2 * t
        return (2f * t3 - 3f * t2 + 1f) * radii[i] + (t3 - 2f * t2 + t) * slopes[i] +
            (-2f * t3 + 3f * t2) * radii[j] + (t3 - t2) * slopes[j]
    }

    companion object {
        fun at(x: Float, z: Float): GolfShoreline =
            entries[Math.floorMod((x * 3f + z * 7f).toInt(), entries.size)]
    }
}
