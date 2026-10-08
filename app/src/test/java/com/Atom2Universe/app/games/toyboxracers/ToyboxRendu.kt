package com.Atom2Universe.app.games.toyboxracers

import android.graphics.Bitmap
import com.Atom2Universe.app.games.toyboxracers.render.ColoredMesh
import com.Atom2Universe.app.games.toyboxracers.render.MeshBuilder
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Rendu 3D logiciel des vrais maillages du jeu, pour les bancs d'aperçu.
 *
 * Il relit les sommets que le jeu enverrait à OpenGL (position, normale, couleur)
 * et les dessine avec le même éclairage que le shader : lumière fixe, 58 % d'ambiance,
 * 42 % de diffus. Tampon de profondeur, découpe au plan proche, aucune élimination des
 * faces arrière (le jeu n'en fait pas non plus). Lent, mais fidèle : ce qu'il montre,
 * c'est la géométrie que le joueur verra.
 */
internal class ToyboxRendu(val width: Int, val height: Int) {
    private val color = IntArray(width * height)
    private val depth = FloatArray(width * height)
    private val view = FloatArray(16)
    private var focal = 1f
    private val near = .1f

    fun clear(sky: Int = 0xFFBFDCEB.toInt()) {
        color.fill(sky)
        depth.fill(Float.MAX_VALUE)
    }

    /** Caméra perspective, champ vertical en degrés, comme `Matrix.perspectiveM`. */
    fun camera(eye: Vec3, target: Vec3, fovDegrees: Float = 58f) {
        val f = (target - eye).normalized()
        var s = cross(f, Vec3(0f, 1f, 0f))
        if (sqrt(s.x * s.x + s.y * s.y + s.z * s.z) < 1e-4f) s = Vec3(1f, 0f, 0f)
        s = s.normalized()
        val u = cross(s, f)
        view[0] = s.x; view[1] = s.y; view[2] = s.z; view[3] = -dot(s, eye)
        view[4] = u.x; view[5] = u.y; view[6] = u.z; view[7] = -dot(u, eye)
        view[8] = -f.x; view[9] = -f.y; view[10] = -f.z; view[11] = dot(f, eye)
        focal = (height / 2f) / tan(Math.toRadians(fovDegrees / 2.0).toFloat())
    }

    fun draw(mesh: ColoredMesh) = draw(vertices(mesh))
    fun draw(builder: MeshBuilder) = draw(builder.build())

    fun draw(values: FloatArray) {
        val light = Vec3(-0.45f, 0.85f, 0.35f).normalized()
        var i = 0
        val tri = Array(3) { FloatArray(3) }
        while (i + 30 <= values.size) {
            for (k in 0 until 3) {
                val o = i + k * 10
                val x = values[o]; val y = values[o + 1]; val z = values[o + 2]
                tri[k][0] = view[0] * x + view[1] * y + view[2] * z + view[3]
                tri[k][1] = view[4] * x + view[5] * y + view[6] * z + view[7]
                tri[k][2] = view[8] * x + view[9] * y + view[10] * z + view[11]
            }
            val diffuse = max(0f, values[i + 3] * light.x + values[i + 4] * light.y + values[i + 5] * light.z)
            val l = .58f + diffuse * .42f
            val r = (values[i + 6] * l * 255).toInt().coerceIn(0, 255)
            val g = (values[i + 7] * l * 255).toInt().coerceIn(0, 255)
            val b = (values[i + 8] * l * 255).toInt().coerceIn(0, 255)
            val alpha = values[i + 9]
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            clipAndRaster(tri, argb, alpha)
            i += 30
        }
    }

    fun toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(color, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** Position écran d'un point du monde, ou null derrière la caméra. */
    fun project(p: Vec3): Pair<Float, Float>? {
        val x = view[0] * p.x + view[1] * p.y + view[2] * p.z + view[3]
        val y = view[4] * p.x + view[5] * p.y + view[6] * p.z + view[7]
        val z = view[8] * p.x + view[9] * p.y + view[10] * p.z + view[11]
        if (z > -near) return null
        return width / 2f + x / -z * focal to height / 2f - y / -z * focal
    }

    private fun clipAndRaster(tri: Array<FloatArray>, argb: Int, alpha: Float) {
        // Découpe contre z = -near (espace caméra, regard vers -z).
        val inside = tri.count { it[2] <= -near }
        if (inside == 0) return
        if (inside == 3) { raster(tri[0], tri[1], tri[2], argb, alpha); return }
        val poly = ArrayList<FloatArray>(4)
        for (k in 0 until 3) {
            val a = tri[k]; val b = tri[(k + 1) % 3]
            val aIn = a[2] <= -near; val bIn = b[2] <= -near
            if (aIn) poly += a
            if (aIn != bIn) {
                val t = (-near - a[2]) / (b[2] - a[2])
                poly += floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, -near)
            }
        }
        for (k in 1 until poly.size - 1) raster(poly[0], poly[k], poly[k + 1], argb, alpha)
    }

    private fun raster(a: FloatArray, b: FloatArray, c: FloatArray, argb: Int, alpha: Float) {
        val ax = width / 2f + a[0] / -a[2] * focal; val ay = height / 2f - a[1] / -a[2] * focal
        val bx = width / 2f + b[0] / -b[2] * focal; val by = height / 2f - b[1] / -b[2] * focal
        val cx = width / 2f + c[0] / -c[2] * focal; val cy = height / 2f - c[1] / -c[2] * focal
        // Profondeur interpolée en 1/z, exacte en perspective.
        val az = 1f / -a[2]; val bz = 1f / -b[2]; val cz = 1f / -c[2]
        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (kotlin.math.abs(area) < 1e-6f) return
        val minX = max(0, min(ax, min(bx, cx)).toInt())
        val maxX = min(width - 1, max(ax, max(bx, cx)).toInt() + 1)
        val minY = max(0, min(ay, min(by, cy)).toInt())
        val maxY = min(height - 1, max(ay, max(by, cy)).toInt() + 1)
        if (minX > maxX || minY > maxY) return
        for (y in minY..maxY) {
            val py = y + .5f
            for (x in minX..maxX) {
                val px = x + .5f
                val w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area
                val w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area
                val w2 = 1f - w0 - w1
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val inv = w0 * az + w1 * bz + w2 * cz
                val d = 1f / inv
                val index = y * width + x
                if (d >= depth[index]) continue
                if (alpha < .99f) {
                    val under = color[index]
                    color[index] = blend(under, argb, alpha)
                } else {
                    depth[index] = d
                    color[index] = argb
                }
            }
        }
    }

    private fun blend(under: Int, over: Int, alpha: Float): Int {
        fun ch(shift: Int) = ((((under shr shift) and 255) * (1 - alpha)) + (((over shr shift) and 255) * alpha)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun cross(a: Vec3, b: Vec3) = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    private fun dot(a: Vec3, b: Vec3) = a.x * b.x + a.y * b.y + a.z * b.z

    companion object {
        /** Les sommets tels qu'ils partiraient vers OpenGL : 10 flottants par sommet. */
        fun vertices(mesh: ColoredMesh): FloatArray {
            val buffer = ColoredMesh::class.java.getDeclaredField("vertexBuffer")
                .apply { isAccessible = true }.get(mesh) as FloatBuffer
            val copy = FloatArray(buffer.capacity())
            buffer.duplicate().apply { position(0) }.get(copy)
            return copy
        }
    }
}
