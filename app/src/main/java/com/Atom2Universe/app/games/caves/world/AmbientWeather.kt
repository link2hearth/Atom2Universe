package com.Atom2Universe.app.games.caves.world

import kotlin.math.*

/** Weather is a function of the saved world clock and seed: no new save format or wall clock. */
internal class AmbientWeather(private val seed: Long) {
    var cloud = 0f; private set
    var precipitation = 0f; private set
    var snow = 0f; private set
    var wind = 0f; private set
    val light get() = 1f - cloud * .24f

    fun update(timeMs: Long, dt: Float, climate: Int, enabled: Boolean) {
        val period = 210_000L
        val epoch = Math.floorDiv(timeMs, period)
        val phase = Math.floorMod(timeMs, period).toFloat() / period
        // Twenty seconds of transition followed by a stable spell.
        val t = (phase * 10f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val cover = pattern(epoch - 1) * (1f - t) + pattern(epoch) * t
        val targetCloud = if (enabled) cover else 0f
        val blend = (dt * .35f).coerceIn(0f, 1f)
        cloud += (targetCloud - cloud) * blend
        // Arid regions keep passing clouds, but receive no rain.
        val targetRain = if (enabled && climate != 1) ((cover - .55f) / .45f).coerceIn(0f, 1f) else 0f
        precipitation += (targetRain - precipitation) * blend
        snow += ((if (climate == 4) 1f else 0f) - snow) * blend
        wind = (sin(timeMs / 19000.0) * .25 + .4 + cloud * .5).toFloat()
    }

    private fun pattern(epoch: Long): Float {
        var n = seed xor (epoch * -7046029254386353131L)
        n = (n xor (n ushr 30)) * -4658895280553007687L
        n = (n xor (n ushr 27)) * -7723592293110705685L
        return when (((n xor (n ushr 31)) and 7L).toInt()) {
            0, 1, 2 -> .12f
            3, 4 -> .52f
            5, 6 -> .86f
            else -> 1f
        }
    }
}

/** Read only loaded blocks. The analytic floor covers terrain whose chunks are still streaming. */
internal fun weatherRoof(world: World, x: Int, z: Int, eyeY: Double): Int {
    var roof = world.surfaceTopY(x, z)
    val cx = Math.floorDiv(x, 16); val cz = Math.floorDiv(z, 16)
    val eyeCy = floor(eyeY / 16).toInt()
    val lx = Math.floorMod(x, 16); val lz = Math.floorMod(z, 16)
    for (cy in eyeCy + world.renderRadiusYSurface downTo eyeCy - world.renderRadiusYSurface) {
        if ((cy + 1) * 16 <= roof) break
        val chunk = world.getChunk(cx, cy, cz)?.takeIf { it.generated } ?: continue
        for (ly in 15 downTo 0) {
            val y = cy * 16 + ly
            if (y < roof) break
            val b = chunk.blockAt(lx, ly, lz)
            if (b != AIR && !isDecoration(b)) return max(roof, y + 1)
        }
    }
    return roof
}
