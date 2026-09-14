package com.Atom2Universe.app.games.caves.world

import kotlin.math.floor

/** CPU copy of the atlas alpha test, matched to MeshBuilder's two crossed sprite planes. */
internal class SpriteHitMask(val width: Int, val height: Int, pixels: IntArray) {
    private val opaque = BooleanArray(width * height) { (pixels[it] ushr 24) >= 128 }

    // Merge opaque pixels into horizontal runs; reused throughout the mining animation.
    private val runs: List<IntArray> = buildList {
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (!opaque[y * width + x]) { x++; continue }
                val start = x++
                while (x < width && opaque[y * width + x]) x++
                add(intArrayOf(start, x, y))
            }
        }
    }

    fun highlight(x: Float, y: Float, z: Float, margin: Float, spriteHeight: Float,
                  red: Float, green: Float, blue: Float): FloatArray {
        val out = FloatArray(runs.size * 2 * 6 * 6)
        var i = 0
        fun vertex(px: Float, py: Float, pz: Float) {
            out[i++] = px; out[i++] = py; out[i++] = pz
            out[i++] = red; out[i++] = green; out[i++] = blue
        }
        val span = 1f - 2f * margin
        for (r in runs) {
            val a = margin + span * r[0] / width; val b = margin + span * r[1] / width
            val bottom = y + spriteHeight * (1f - (r[2] + 1f) / height)
            val top = y + spriteHeight * (1f - r[2].toFloat() / height)
            for (plane in 0..1) {
                val positive = if (plane == 0) z + .5f < 0 else x + .5f < 0
                val center = .5f + if (positive) .002f else -.002f
                val order = if (positive == (plane == 0)) intArrayOf(0,1,2,0,2,3) else intArrayOf(0,2,1,0,3,2)
                for (corner in order) {
                    val along = if (corner == 0 || corner == 3) a else b
                    val py = if (corner < 2) bottom else top
                    if (plane == 0) vertex(x + along, py, z + center)
                    else vertex(x + center, py, z + along)
                }
            }
        }
        return out
    }

    fun intersects(
        x: Double, y: Double, z: Double,
        dx: Double, dy: Double, dz: Double,
        margin: Double, spriteHeight: Double,
        near: Double, far: Double,
    ): Boolean {
        val span = 1.0 - 2.0 * margin
        if (span <= 0.0 || spriteHeight <= 0.0 || far < near) return false
        fun plane(origin: Double, direction: Double, along: Double, alongDirection: Double): Boolean {
            // A ray parallel to a plane cannot see its surface (including its zero-width edge).
            if (direction == 0.0) return false
            val t = (0.5 - origin) / direction
            if (t < near || t > far) return false
            val u = (along + t * alongDirection - margin) / span
            val v = 1.0 - (y + t * dy) / spriteHeight
            if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) return false
            val px = floor(u * width).toInt().coerceIn(0, width - 1)
            val py = floor(v * height).toInt().coerceIn(0, height - 1)
            return opaque[py * width + px]
        }
        // Test both planes: a transparent pixel on the nearer one must not hide the other.
        return plane(z, dz, x, dx) || plane(x, dx, z, dz)
    }
}
