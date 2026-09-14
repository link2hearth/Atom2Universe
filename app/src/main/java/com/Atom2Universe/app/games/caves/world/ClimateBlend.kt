package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.MeadowTextures
import kotlin.math.floor

/** World-aligned color field. A straight boundary fades across TWO whole blocks.
 * At the shared edge: 50/50. One block away on either side: the unmixed color.
 * No UV coordinates or chunk-local randomness enter the calculation.
 */
internal class ClimateBlend(
    private val originX: Int, private val originZ: Int, private val vivid: Boolean,
    private val climateAt: (Int, Int) -> Int,
) {
    private val raw = IntArray(18 * 18) { -1 }
    private val ready = BooleanArray(17 * 17)
    private val corners = FloatArray(17 * 17 * 3)
    private val reference = MeadowTextures.climateColor(0, vivid)

    private fun color(x: Int, z: Int): Int {
        val i = x + 1 + (z + 1) * 18
        if (raw[i] == -1) raw[i] = climateAt(originX + x, originZ + z)
        return MeadowTextures.climateColor(raw[i], vivid)
    }

    private fun corner(x: Int, z: Int): Int {
        val i = x + z * 17
        if (!ready[i]) {
            for (dz in -1..0) for (dx in -1..0) {
                val c = color(x + dx, z + dz)
                for (channel in 0..2) {
                    val shift = (2 - channel) * 8
                    corners[i * 3 + channel] += (c ushr shift and 255) / 4f
                }
            }
            for (channel in 0..2) {
                val shift = (2 - channel) * 8
                corners[i * 3 + channel] = corners[i * 3 + channel] / (reference ushr shift and 255) - 1f
            }
            ready[i] = true
        }
        return i * 3
    }

    fun writeDelta(x: Float, z: Float, out: FloatArray, offset: Int) {
        val px = x.coerceIn(0f, 16f); val pz = z.coerceIn(0f, 16f)
        val x0 = floor(px).toInt(); val z0 = floor(pz).toInt()
        val x1 = minOf(x0 + 1, 16); val z1 = minOf(z0 + 1, 16)
        val a = corner(x0, z0); val b = corner(x1, z0)
        val c = corner(x0, z1); val d = corner(x1, z1)
        val tx = px - x0; val tz = pz - z0
        for (channel in 0..2) {
            val top = corners[a + channel] * (1 - tx) + corners[b + channel] * tx
            val bottom = corners[c + channel] * (1 - tx) + corners[d + channel] * tx
            out[offset + channel] = top * (1 - tz) + bottom * tz
        }
    }
}
