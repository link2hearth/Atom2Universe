package com.Atom2Universe.app.games.caves.world

import kotlin.math.floor

/** CPU copy of the atlas alpha test, matched to MeshBuilder's two crossed sprite planes. */
internal class SpriteHitMask(val width: Int, val height: Int, pixels: IntArray) {
    private val opaque = BooleanArray(width * height) { (pixels[it] ushr 24) >= 128 }

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
