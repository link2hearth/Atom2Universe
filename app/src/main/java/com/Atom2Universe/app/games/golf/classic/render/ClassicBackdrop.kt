package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import kotlin.math.*

/** Shared bounds and relief for the apron and its woodland; never used by ball physics. */
internal class ClassicBackdrop(private val hole: ClassicHole) {
    val step = 2.5f
    val nx = ceil((hole.width + 80f) / step).toInt()
    val nz = ceil((hole.length + 110f) / step).toInt()
    val left = -(hole.width + 80f) / 2f
    val right = left + nx * step
    val front = -40f
    val back = front + nz * step

    private fun axis(start: Float, count: Int): List<Float> {
        val end = start + count * step
        return (24 downTo 1).map { start - it * 15f } +
            (0 until count step 6).map { start + it * step } + end +
            (1..24).map { end + it * 15f }
    }
    val xs = axis(left, nx)
    val zs = axis(front, nz)

    fun outward(x: Float, z: Float) = max(max(left-x, x-right), max(front-z, z-back)).coerceAtLeast(0f)

    fun heightAt(x: Float, z: Float): Float {
        val t = ((outward(x,z)-25f)/140f).coerceIn(0f,1f)
        val phase = hole.number * .73f
        val hills = 29f + 15f*sin(x*.012f+z*.009f+phase) +
            10f*cos(z*.024f-x*.006f-phase) + 7f*sin(x*.031f+z*.017f)
        return hole.heightAt(x,z) + t*t*(3f-2f*t)*hills*(if(hole.highlands)1.45f else 1f)
    }

    /** Trees sit on the actual triangles, including where a hill bends between samples. */
    fun surfaceHeight(x: Float, z: Float): Float {
        val ix = (xs.indexOfLast { it <= x }).coerceIn(0,xs.size-2)
        val iz = (zs.indexOfLast { it <= z }).coerceIn(0,zs.size-2)
        val x0=xs[ix]; val x1=xs[ix+1]; val z0=zs[iz]; val z1=zs[iz+1]
        val u=(x-x0)/(x1-x0); val v=(z-z0)/(z1-z0)
        val a=heightAt(x0,z0); val c=heightAt(x1,z1)
        return if(u>=v) a*(1f-u)+heightAt(x1,z0)*(u-v)+c*v
            else a*(1f-v)+c*u+heightAt(x0,z1)*(v-u)
    }
}
