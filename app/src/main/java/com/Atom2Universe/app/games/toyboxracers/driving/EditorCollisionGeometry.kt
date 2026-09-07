package com.Atom2Universe.app.games.toyboxracers.driving

import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.VolumePoint
import kotlin.math.abs
import kotlin.math.floor

/** Immutable triangle coefficients prepared on world changes, never per wheel contact. */
internal class EditorSurfaceTriangle(a: VolumePoint, b: VolumePoint, c: VolumePoint) {
    private val cx = c.x
    private val cz = c.z
    private val cy = c.y
    private val ux = b.z - c.z
    private val uz = c.x - b.x
    private val vx = c.z - a.z
    private val vz = a.x - c.x
    private val determinant = ux * (a.x - c.x) + uz * (a.z - c.z)
    private val ay = a.y - c.y
    private val by = b.y - c.y

    fun heightAt(x: Float, z: Float): Float? {
        if (abs(determinant) < 0.000001f) return null
        val u = (ux * (x - cx) + uz * (z - cz)) / determinant
        val v = (vx * (x - cx) + vz * (z - cz)) / determinant
        if (u < -0.0001f || v < -0.0001f || u + v > 1.0001f) return null
        return cy + u * ay + v * by
    }
}

internal class EditorVolumeCollider(val volume: ToyboxVolume) {
    private val corners = buildList {
        for (x in listOf(-volume.width / 2, volume.width / 2))
            for (y in listOf(-volume.height / 2, volume.height / 2))
                for (z in listOf(-volume.depth / 2, volume.depth / 2)) add(volume.worldPoint(x, y, z))
    }
    val left = corners.minOf { it.x }
    val right = corners.maxOf { it.x }
    val back = corners.minOf { it.z }
    val front = corners.maxOf { it.z }
    val bottom = corners.minOf { it.y }
    val top = corners.maxOf { it.y }
    val yawCos = volume.yawCos
    val yawSin = volume.yawSin
    private val ramp = volume.kind == ToyboxVolumeKind.RAMP
    private val a = volume.worldPoint(-volume.width / 2, if (ramp) -volume.height / 2 else volume.height / 2, -volume.depth / 2)
    private val b = volume.worldPoint(-volume.width / 2, volume.height / 2, volume.depth / 2)
    private val c = volume.worldPoint(volume.width / 2, volume.height / 2, volume.depth / 2)
    private val d = volume.worldPoint(volume.width / 2, if (ramp) -volume.height / 2 else volume.height / 2, -volume.depth / 2)
    private val surfaceA = EditorSurfaceTriangle(a, b, c)
    private val surfaceB = EditorSurfaceTriangle(a, c, d)
    private val undersideA = EditorSurfaceTriangle(corners[0], corners[1], corners[5])
    private val undersideB = EditorSurfaceTriangle(corners[0], corners[5], corners[4])
    var queryStamp = 0L

    fun heightAt(x: Float, z: Float) = surfaceA.heightAt(x, z) ?: surfaceB.heightAt(x, z)
    fun ceilingAt(x: Float, z: Float) = undersideA.heightAt(x, z) ?: undersideB.heightAt(x, z)
}

/** Broad phase: query nearby cells without allocating a set/list at every physics step. */
internal class EditorVolumeIndex(volumes: List<ToyboxVolume>) {
    private val buckets = HashMap<Long, MutableList<EditorVolumeCollider>>()
    private val large = ArrayList<EditorVolumeCollider>()
    private var stamp = 0L

    init {
        volumes.filter { it.solid }.forEach { volume ->
            val collider = EditorVolumeCollider(volume)
            val lx = cell(collider.left)
            val rx = cell(collider.right)
            val bz = cell(collider.back)
            val fz = cell(collider.front)
            if ((rx.toLong() - lx + 1) * (fz.toLong() - bz + 1) > 4096) large.add(collider)
            else for (x in lx..rx) for (z in bz..fz)
                buckets.getOrPut(key(x, z)) { ArrayList() }.add(collider)
        }
    }

    fun visit(x: Float, z: Float, margin: Float, action: (EditorVolumeCollider) -> Unit) {
        val query = ++stamp
        fun consider(c: EditorVolumeCollider) {
            if (c.queryStamp == query) return
            c.queryStamp = query
            if (x >= c.left - margin && x <= c.right + margin && z >= c.back - margin && z <= c.front + margin) action(c)
        }
        for (cx in cell(x - margin)..cell(x + margin)) for (cz in cell(z - margin)..cell(z + margin))
            buckets[key(cx, cz)]?.forEach(::consider)
        large.forEach(::consider)
    }

    private fun cell(value: Float) = floor(value / 18f).toInt()
    private fun key(x: Int, z: Int) = (x.toLong() shl 32) xor (z.toLong() and 0xFFFFFFFFL)
}
