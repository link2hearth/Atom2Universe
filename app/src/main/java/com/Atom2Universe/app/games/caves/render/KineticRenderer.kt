package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.node.FrontierItems
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Rotating mechanical parts. One mesh per part type, built once; every placed part is an instance
 * (position, axis, angle, light), so each part type costs a single draw call. The rotation is
 * applied in the vertex shader: the CPU only uploads six floats per visible part.
 */
internal class KineticRenderer {
    private var shader: ShaderProgram? = null
    private var vao = 0
    private var meshVbo = 0
    private var instanceVbo = 0
    private val meshFirst = IntArray(TYPES)
    private val meshCount = IntArray(TYPES)
    private var vpLocation = 0
    private var fogLocation = 0
    private val instances = FloatArray(FrontierWorkshops.MAX_KINETICS * INSTANCE_FLOATS)
    private val nativeInstances: FloatBuffer = ByteBuffer.allocateDirect(instances.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val groupStart = IntArray(TYPES + 1)

    fun onSurfaceCreated() {
        // The previous context and its handles are gone: rebuild everything on resume.
        shader = ShaderProgram(VERTEX, FRAGMENT).also {
            vpLocation = it.uniform("uVp")
            fogLocation = it.uniform("uCaveFog")
        }
        val meshes = listOf(shaftMesh(), cogMesh(large = false), cogMesh(large = true), crankMesh())
        var first = 0
        for ((type, mesh) in meshes.withIndex()) {
            meshFirst[type] = first; meshCount[type] = mesh.size / MESH_FLOATS; first += meshCount[type]
        }
        val all = meshes.fold(FloatArray(0)) { acc, m -> acc + m }
        val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0)
        meshVbo = ids[0]; instanceVbo = ids[1]
        val v = IntArray(1); GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVbo)
        val meshBuffer = ByteBuffer.allocateDirect(all.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        meshBuffer.put(all).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, all.size * 4, meshBuffer, GLES30.GL_STATIC_DRAW)
        val stride = MESH_FLOATS * 4
        for ((location, offset) in listOf(0 to 0, 1 to 3, 2 to 6)) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, 3, GLES30.GL_FLOAT, false, stride, offset * 4)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instances.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        for (location in 3..4) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribDivisor(location, 1)
        }
        bindInstances(0)
        // Cave's other renderers use the default VAO: never leave this one bound.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Points the per-instance attributes at the group starting at [first] (GLES 3.0 has no base instance). */
    private fun bindInstances(first: Int) {
        val instanceStride = INSTANCE_FLOATS * 4
        GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instanceStride, first * instanceStride)
        GLES30.glVertexAttribPointer(4, 3, GLES30.GL_FLOAT, false, instanceStride, first * instanceStride + 12)
    }

    private fun type(block: Short) = when (block) {
        FrontierItems.COGWHEEL -> 1
        FrontierItems.LARGE_COGWHEEL -> 2
        FrontierItems.CRANK -> 3
        else -> 0
    }

    fun draw(parts: List<FrontierWorkshops.Kinetic>, camera: Camera, caveBlend: Float, fogEnd: Float) {
        val s = shader ?: return
        if (parts.isEmpty()) return
        // Instances grouped by part type, one contiguous range per mesh.
        var n = 0
        for (type in 0 until TYPES) {
            groupStart[type] = n
            for (k in parts) {
                if (n >= FrontierWorkshops.MAX_KINETICS) break
                if (type(k.block) != type) continue
                val o = n * INSTANCE_FLOATS
                instances[o] = (k.pos.x - camera.x).toFloat()
                instances[o + 1] = (k.pos.y - camera.y).toFloat()
                instances[o + 2] = (k.pos.z - camera.z).toFloat()
                instances[o + 3] = k.axis.toFloat()
                instances[o + 4] = k.angle + phase(k)
                instances[o + 5] = k.light
                n++
            }
        }
        groupStart[TYPES] = n
        if (n == 0) return
        nativeInstances.clear(); nativeInstances.put(instances, 0, n * INSTANCE_FLOATS).position(0)
        s.use()
        GLES30.glUniformMatrix4fv(vpLocation, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform3f(fogLocation, caveBlend, fogEnd * .55f, fogEnd)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * INSTANCE_FLOATS * 4, nativeInstances)
        for (type in 0 until TYPES) {
            val count = groupStart[type + 1] - groupStart[type]
            if (count == 0) continue
            bindInstances(groupStart[type])
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, meshFirst[type], meshCount[type], count)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Neighbouring small cogwheels turn opposite ways: offset every other one by half a tooth so they mesh. */
    private fun phase(k: FrontierWorkshops.Kinetic): Float {
        if (k.block != FrontierItems.COGWHEEL) return 0f
        val parity = when (k.axis) { 0 -> k.pos.y + k.pos.z; 1 -> k.pos.x + k.pos.z; else -> k.pos.x + k.pos.y }
        return if (Math.floorMod(parity, 2) == 1) (PI / COG_TEETH).toFloat() else 0f
    }

    fun destroy() {
        shader?.destroy(); shader = null
        if (meshVbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(meshVbo, instanceVbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        meshVbo = 0; instanceVbo = 0; vao = 0
    }

    // ── Meshes, all along local Z and centred on the origin ──────────────────

    private class MeshOut { val data = ArrayList<Float>() }

    /** A box turned by [angle] around local Z: half sizes [hx], [hy] around ([cx], [cy]), from z0 to z1. */
    private fun MeshOut.prism(cx: Float, cy: Float, hx: Float, hy: Float, z0: Float, z1: Float, angle: Float, color: Int) {
        val c = cos(angle); val s = sin(angle)
        fun corner(x: Float, y: Float) = floatArrayOf(cx + x * c - y * s, cy + x * s + y * c)
        val p = arrayOf(corner(-hx, -hy), corner(hx, -hy), corner(hx, hy), corner(-hx, hy))
        val r = (color shr 16 and 255) / 255f; val g = (color shr 8 and 255) / 255f; val b = (color and 255) / 255f
        fun vertex(xy: FloatArray, z: Float, nx: Float, ny: Float, nz: Float) {
            data += xy[0]; data += xy[1]; data += z; data += nx; data += ny; data += nz; data += r; data += g; data += b
        }
        fun quad(a: FloatArray, za: Float, bb: FloatArray, zb: Float, cc: FloatArray, zc: Float, d: FloatArray, zd: Float,
                 nx: Float, ny: Float, nz: Float) {
            for ((xy, z) in listOf(a to za, bb to zb, cc to zc, a to za, cc to zc, d to zd)) vertex(xy, z, nx, ny, nz)
        }
        // Four sides, counter-clockwise seen from outside, then both ends.
        for (i in 0..3) {
            val a = p[i]; val bb = p[(i + 1) % 4]
            val ex = bb[0] - a[0]; val ey = bb[1] - a[1]; val len = kotlin.math.sqrt(ex * ex + ey * ey)
            quad(a, z0, bb, z0, bb, z1, a, z1, ey / len, -ex / len, 0f)
        }
        quad(p[0], z1, p[1], z1, p[2], z1, p[3], z1, 0f, 0f, 1f)
        quad(p[3], z0, p[2], z0, p[1], z0, p[0], z0, 0f, 0f, -1f)
    }

    private fun MeshOut.rod(color: Int, keyColor: Int) {
        prism(0f, 0f, ROD, ROD, -.5f, .5f, 0f, color)
        // A darker strip along one side: without it a turning square rod hardly reads as turning.
        prism(ROD + .25f / 16f, 0f, .25f / 16f, .6f / 16f, -.5f, .5f, 0f, keyColor)
    }

    private fun shaftMesh() = MeshOut().apply { rod(WOOD, DARK) }.data.toFloatArray()

    private fun cogMesh(large: Boolean) = MeshOut().apply {
        rod(WOOD, DARK)
        val teeth = if (large) LARGE_TEETH else COG_TEETH
        val rim = if (large) .80f else .36f
        val thick = 2.5f / 16f
        // Two squares 45° apart make an octagonal wheel; a darker hub; teeth all around.
        for (a in 0..1) prism(0f, 0f, rim, rim, -thick, thick, a * (PI / 4).toFloat(), LIGHT)
        prism(0f, 0f, rim * tan(PI / 8).toFloat() * 1.2f, rim * tan(PI / 8).toFloat() * 1.2f, -thick - .01f, thick + .01f, 0f, DARK)
        val toothRadius = rim + (if (large) .1f else .08f)
        for (i in 0 until teeth) {
            val angle = (i * 2 * PI / teeth).toFloat()
            prism(cos(angle) * toothRadius, sin(angle) * toothRadius, if (large) .09f else .07f, if (large) .06f else .055f,
                -thick * .8f, thick * .8f, angle, DARK)
        }
    }.data.toFloatArray()

    private fun crankMesh() = MeshOut().apply {
        rod(WOOD, DARK)
        // Arm out from the axle near its front end, then a handle parallel to the axle.
        prism(.2f, 0f, .2f, 1.3f / 16f, .28f, .38f, 0f, DARK)
        prism(.4f, 0f, 1.2f / 16f, 1.2f / 16f, .28f, .48f, 0f, LIGHT)
    }.data.toFloatArray()

    companion object {
        private const val TYPES = 4
        private const val MESH_FLOATS = 9
        private const val INSTANCE_FLOATS = 6
        private const val COG_TEETH = 8
        private const val LARGE_TEETH = 16
        private const val ROD = 2.5f / 16f
        private const val WOOD = 0xC4A06A
        private const val LIGHT = 0xB8894F
        private const val DARK = 0x6E5436
        private const val VERTEX = """#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec3 aColor;
            layout(location=3) in vec3 iOffset;
            layout(location=4) in vec3 iAxisAngleLight;
            uniform mat4 uVp;
            out vec3 vNormal;
            out vec3 vColor;
            out float vLight;
            out float vDistance;
            // Local Z becomes the part's axis: 0 = X, 1 = Y, 2 = Z (both are proper rotations).
            vec3 orient(vec3 p, float axis) {
                if (axis < 0.5) return vec3(p.z, p.y, -p.x);
                if (axis < 1.5) return vec3(p.x, p.z, -p.y);
                return p;
            }
            void main() {
                float c = cos(iAxisAngleLight.y);
                float s = sin(iAxisAngleLight.y);
                vec3 p = vec3(aPosition.x * c - aPosition.y * s, aPosition.x * s + aPosition.y * c, aPosition.z);
                vec3 n = vec3(aNormal.x * c - aNormal.y * s, aNormal.x * s + aNormal.y * c, aNormal.z);
                vec3 world = orient(p, iAxisAngleLight.x) + vec3(0.5) + iOffset;
                gl_Position = uVp * vec4(world, 1.0);
                vNormal = orient(n, iAxisAngleLight.x);
                vColor = aColor;
                vLight = iAxisAngleLight.z;
                vDistance = length(world);
            }
        """
        private const val FRAGMENT = """#version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec3 vColor;
            in float vLight;
            in float vDistance;
            uniform vec3 uCaveFog;
            out vec4 fragColor;
            void main() {
                float diffuse = max(dot(normalize(vNormal), normalize(vec3(-0.45, 0.85, 0.35))), 0.0);
                vec3 rgb = vColor * (0.55 + diffuse * 0.45) * (0.12 + vLight * 0.88);
                float fog = smoothstep(uCaveFog.y, uCaveFog.z, vDistance) * uCaveFog.x;
                fragColor = vec4(mix(rgb, vec3(0.0), fog), 1.0);
            }
        """
    }
}
