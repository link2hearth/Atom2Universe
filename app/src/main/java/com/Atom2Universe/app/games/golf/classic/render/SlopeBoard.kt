package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import com.Atom2Universe.app.games.golf.classic.core.GolfPoint
import kotlin.math.*

/**
 * The slope board, as in Everybody's Golf: squares fixed on the ground, their lines running along
 * the world axes through the hole, shown only inside a window: the stretch from the ball to the hole
 * for a putt, the area the camera looks at from above. The window is cut along whole squares: when
 * it moves, the squares it leaves fade out a row at a time while those it reaches fade in.
 * GroundShader draws the lines, tinted progressively by height: red above the reference (the ball,
 * or the middle of the view from above), green below; [lights] glide along them downhill.
 */
internal data class SlopeBoard(
    /** The window: every square whose centre lies within [radius] of the segment from (fromX, fromZ) to (toX, toZ). */
    val fromX: Float, val fromZ: Float, val toX: Float, val toZ: Float, val radius: Float,
    /** Reference ground height and the rise shown in full colour. */
    val baseY: Float = 0f, val relief: Float = 1f) {
    val middleX get() = (fromX + toX) * .5f
    val middleZ get() = (fromZ + toZ) * .5f

    /** How much of the square of [size] metres centred on ([x], [z]) shows: GroundShader's boardWindow. */
    fun weight(x: Float, z: Float, size: Float): Float {
        val fade = min(FADE * size, radius * .5f)
        val v = ((distance(x, z) - radius + fade) / fade).coerceIn(0f, 1f)
        return 1f - v * v * (3f - 2f * v)
    }

    private fun distance(x: Float, z: Float): Float {
        val ax = toX - fromX; val az = toZ - fromZ
        val t = (((x - fromX) * ax + (z - fromZ) * az) / max(ax * ax + az * az, 1e-6f)).coerceIn(0f, 1f)
        return hypot(x - fromX - ax * t, z - fromZ - az * t)
    }

    /**
     * One light per side between two squares of [cell] metres that show, gliding from its high end
     * to its low end: the steeper the side, the faster. Flat sides have none.
     * Nine floats each: high end, low end, speed (sides per second), phase, unused.
     */
    fun lights(hole: ClassicHole, cell: Float): FloatArray {
        val ox = hole.cup.x; val oz = hole.cup.z
        val reach = radius + cell
        val i0 = floor((min(fromX, toX) - reach - ox) / cell).toInt(); val i1 = ceil((max(fromX, toX) + reach - ox) / cell).toInt()
        val j0 = floor((min(fromZ, toZ) - reach - oz) / cell).toInt(); val j1 = ceil((max(fromZ, toZ) + reach - oz) / cell).toInt()
        fun x(i: Int) = ox + i * cell
        fun z(j: Int) = oz + j * cell
        val heights = Array(i1 - i0 + 1) { i -> FloatArray(j1 - j0 + 1) { j -> hole.heightAt(x(i + i0), z(j + j0)) } }
        fun h(i: Int, j: Int) = heights[i - i0][j - j0]
        // The square whose lowest corner is node (i, j).
        val shown = Array(i1 - i0) { i -> FloatArray(j1 - j0) { j -> weight(x(i + i0) + cell * .5f, z(j + j0) + cell * .5f, cell) } }
        fun square(i: Int, j: Int) = shown[i - i0][j - j0]
        val out = ArrayList<FloatArray>()
        fun side(ia: Int, ja: Int, ib: Int, jb: Int) {
            val xa = x(ia); val za = z(ja); val xb = x(ib); val zb = z(jb)
            val mx = (xa + xb) * .5f; val mz = (za + zb) * .5f
            if (hypot(mx - hole.cup.x, mz - hole.cup.z) < .4f) return
            if (hole.hazards.any { it.lie == GolfLie.WATER && it.contains(mx, mz) }) return
            val ha = h(ia, ja); val hb = h(ib, jb)
            val slope = abs(hb - ha) / cell
            if (slope < .004f) return
            val phase = abs(sin(mx * 12.9898f + mz * 78.233f) * 43758.547f) % 1f
            // Nearly in proportion to the slope, and calm: a gentle break must not look like a strong one.
            val speed = .1f + slope * 11f
            out += if (ha > hb) floatArrayOf(xa, ha, za, xb, hb, zb, speed, phase, 0f)
            else floatArrayOf(xb, hb, zb, xa, ha, za, speed, phase, 0f)
        }
        // Inner sides only: the window's edge fades out without lights.
        for (i in i0 + 1 until i1) for (j in j0 until j1) if (min(square(i - 1, j), square(i, j)) >= .5f) side(i, j, i, j + 1)
        for (j in j0 + 1 until j1) for (i in i0 until i1) if (min(square(i, j - 1), square(i, j)) >= .5f) side(i, j, i + 1, j)
        val kept = if (out.size > LIGHTS) out.filterIndexed { index, _ -> index % ((out.size + LIGHTS - 1) / LIGHTS) == 0 } else out
        return FloatArray(kept.size * 9).also { array -> kept.forEachIndexed { k, light -> light.copyInto(array, k * 9) } }
    }

    companion object {
        const val LIGHTS = 2000
        /** Squares over which the window's edge fades (also in GroundShader). */
        private const val FADE = 2.5f

        /**
         * For a putt: from the ball to the hole or, when the guide runs further, to its [end], with
         * room around both, so that where the ball would stop can be read too.
         */
        fun forPutt(hole: ClassicHole, ball: GolfPoint, end: GolfPoint?): SlopeBoard {
            val cup = hole.cup
            val far = if (end != null && hypot(end.x - ball.x, end.z - ball.z) > hypot(cup.x - ball.x, cup.z - ball.z)) end else cup
            val other = if (far === cup) end else cup
            val length = hypot(far.x - ball.x, far.z - ball.z)
            val reach = (length * .25f + 1.2f).coerceIn(1.6f, 4.5f)
            val line = SlopeBoard(ball.x, ball.z, far.x, far.z, reach)
            val radius = if (other == null) reach else max(reach, min(line.distance(other.x, other.z) + 1.2f, 8f))
            return line.copy(radius = radius).shaded(hole, hole.heightAt(ball.x, ball.z))
        }

        /**
         * From above: around the point the camera looks at, [distance] metres away, over most of
         * the screen's height. Heights are read from [base], or from that point when null.
         */
        fun around(hole: ClassicHole, x: Float, z: Float, distance: Float, base: Float?): SlopeBoard =
            SlopeBoard(x, z, x, z, distance * .45f).shaded(hole, base ?: hole.heightAt(x, z))

        /**
         * Colours rise with height above [base], reaching full red (or green below) at the
         * window's largest difference, but never for less than a few centimetres: a flat board
         * stays white.
         */
        private fun SlopeBoard.shaded(hole: ClassicHole, base: Float): SlopeBoard {
            var largest = 0f
            val x0 = min(fromX, toX) - radius; val x1 = max(fromX, toX) + radius
            val z0 = min(fromZ, toZ) - radius; val z1 = max(fromZ, toZ) + radius
            for (i in 0..8) for (j in 0..8) {
                val x = x0 + (x1 - x0) * i / 8f; val z = z0 + (z1 - z0) * j / 8f
                if (distance(x, z) <= radius) largest = max(largest, abs(hole.heightAt(x, z) - base))
            }
            val span = hypot(toX - fromX, toZ - fromZ) + 2f * radius
            return copy(baseY = base, relief = max(largest, .06f + .003f * span))
        }

        /**
         * Square size for a camera [distance] metres away: about 7 % of the screen height, in
         * powers of two from half a metre, and never so large that the window holds fewer than six
         * squares across. The second value is the weight of the half-size lines, which fade out as
         * the camera pulls back.
         */
        fun cell(distance: Float, board: SlopeBoard): Pair<Float, Float> {
            val wanted = distance * 2f * tan(Math.toRadians(25.0).toFloat()) * .07f
            val largest = board.radius / 3f
            val level = min(log2(wanted.coerceAtLeast(1e-3f)), log2(largest.coerceAtLeast(1e-3f))).coerceIn(-1f, 6f)
            val base = floor(level)
            return 2f.pow(base) to 1f - (level - base)
        }
    }
}

/** Round, softly edged points for the board lights; a separate program keeps `discard` out of the scene's. */
internal class BoardLightShader {
    private var program = 0
    private var mvpLoc = 0; private var sizeLoc = 0

    fun create() {
        fun shader(type: Int, source: String): Int {
            val id = GL.glCreateShader(type)
            GL.glShaderSource(id, source); GL.glCompileShader(id)
            val result = IntArray(1); GL.glGetShaderiv(id, GL.GL_COMPILE_STATUS, result, 0)
            check(result[0] != 0) { GL.glGetShaderInfoLog(id) }
            return id
        }
        val vertex = shader(GL.GL_VERTEX_SHADER, """
            uniform mat4 uMvp;
            uniform float uSize;
            attribute vec3 aPosition;
            attribute vec3 aColour;
            varying vec3 vColour;
            void main() {
                vColour = aColour;
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = clamp(uSize / gl_Position.w, 2.0, 12.0);
            }
        """.trimIndent())
        val fragment = shader(GL.GL_FRAGMENT_SHADER, """
            precision mediump float;
            varying vec3 vColour;
            void main() {
                vec2 q = gl_PointCoord - .5;
                float r = dot(q, q);
                if (r > .25) discard;
                gl_FragColor = vec4(mix(vColour, vColour * .55, smoothstep(.12, .25, r)), 1.0);
            }
        """.trimIndent())
        program = GL.glCreateProgram()
        GL.glAttachShader(program, vertex); GL.glAttachShader(program, fragment)
        GL.glBindAttribLocation(program, 0, "aPosition"); GL.glBindAttribLocation(program, 1, "aColour")
        GL.glLinkProgram(program)
        GL.glDeleteShader(vertex); GL.glDeleteShader(fragment)
        val result = IntArray(1); GL.glGetProgramiv(program, GL.GL_LINK_STATUS, result, 0)
        check(result[0] != 0) { GL.glGetProgramInfoLog(program) }
        mvpLoc = GL.glGetUniformLocation(program, "uMvp")
        sizeLoc = GL.glGetUniformLocation(program, "uSize")
    }

    /** [count] points (position, colour) from [buffer]; [size] is the pixel diameter one metre away. */
    fun draw(buffer: Int, count: Int, viewProjection: FloatArray, size: Float) {
        GL.glUseProgram(program)
        GL.glUniformMatrix4fv(mvpLoc, 1, false, viewProjection, 0)
        GL.glUniform1f(sizeLoc, size)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer)
        GL.glEnableVertexAttribArray(0); GL.glEnableVertexAttribArray(1)
        GL.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0)
        GL.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12)
        GL.glDrawArrays(GL.GL_POINTS, 0, count)
    }

    fun release() {
        if (program != 0) GL.glDeleteProgram(program)
        program = 0
    }
}
