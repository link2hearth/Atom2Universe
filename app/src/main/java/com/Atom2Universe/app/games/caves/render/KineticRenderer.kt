package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Rotating mechanical parts. One mesh per part, built once; every shaft is an instance
 * (position, axis, angle, light), so all shafts cost a single draw call. The rotation is
 * applied in the vertex shader: the CPU only uploads six floats per visible shaft.
 */
internal class KineticRenderer {
    private var shader: ShaderProgram? = null
    private var vao = 0
    private var meshVbo = 0
    private var instanceVbo = 0
    private var vertexCount = 0
    private var vpLocation = 0
    private var fogLocation = 0
    private val instances = FloatArray(FrontierWorkshops.MAX_KINETICS * INSTANCE_FLOATS)
    private val nativeInstances: FloatBuffer = ByteBuffer.allocateDirect(instances.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun onSurfaceCreated() {
        // The previous context and its handles are gone: rebuild everything on resume.
        shader = ShaderProgram(VERTEX, FRAGMENT).also {
            vpLocation = it.uniform("uVp")
            fogLocation = it.uniform("uCaveFog")
        }
        val mesh = shaftMesh()
        vertexCount = mesh.size / MESH_FLOATS
        val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0)
        meshVbo = ids[0]; instanceVbo = ids[1]
        val v = IntArray(1); GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVbo)
        val meshBuffer = ByteBuffer.allocateDirect(mesh.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        meshBuffer.put(mesh).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, meshBuffer, GLES30.GL_STATIC_DRAW)
        val stride = MESH_FLOATS * 4
        for ((location, offset) in listOf(0 to 0, 1 to 3, 2 to 6)) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, 3, GLES30.GL_FLOAT, false, stride, offset * 4)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instances.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val instanceStride = INSTANCE_FLOATS * 4
        for ((location, offset) in listOf(3 to 0, 4 to 3)) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, 3, GLES30.GL_FLOAT, false, instanceStride, offset * 4)
            GLES30.glVertexAttribDivisor(location, 1)
        }
        // Cave's other renderers use the default VAO: never leave this one bound.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(parts: List<FrontierWorkshops.Kinetic>, camera: Camera, caveBlend: Float, fogEnd: Float) {
        val s = shader ?: return
        if (parts.isEmpty()) return
        var n = 0
        for (k in parts) {
            if (n >= FrontierWorkshops.MAX_KINETICS) break
            val o = n * INSTANCE_FLOATS
            instances[o] = (k.pos.x - camera.x).toFloat()
            instances[o + 1] = (k.pos.y - camera.y).toFloat()
            instances[o + 2] = (k.pos.z - camera.z).toFloat()
            instances[o + 3] = k.axis.toFloat()
            instances[o + 4] = k.angle
            instances[o + 5] = k.light
            n++
        }
        nativeInstances.clear(); nativeInstances.put(instances, 0, n * INSTANCE_FLOATS).position(0)
        s.use()
        GLES30.glUniformMatrix4fv(vpLocation, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform3f(fogLocation, caveBlend, fogEnd * .55f, fogEnd)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * INSTANCE_FLOATS * 4, nativeInstances)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, vertexCount, n)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun destroy() {
        shader?.destroy(); shader = null
        if (meshVbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(meshVbo, instanceVbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        meshVbo = 0; instanceVbo = 0; vao = 0
    }

    /** A wooden rod along local Z, centred on the origin, with a darker key strip so the turn reads. */
    private fun shaftMesh(): FloatArray {
        val out = ArrayList<Float>()
        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) {
            val r = (color shr 16 and 255) / 255f; val g = (color shr 8 and 255) / 255f; val b = (color and 255) / 255f
            // Six faces, two triangles each, counter-clockwise seen from outside.
            val faces = arrayOf(
                floatArrayOf(1f, 0f, 0f) to arrayOf(floatArrayOf(x1, y0, z1), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1)),
                floatArrayOf(-1f, 0f, 0f) to arrayOf(floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0)),
                floatArrayOf(0f, 1f, 0f) to arrayOf(floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0)),
                floatArrayOf(0f, -1f, 0f) to arrayOf(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1)),
                floatArrayOf(0f, 0f, 1f) to arrayOf(floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1)),
                floatArrayOf(0f, 0f, -1f) to arrayOf(floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0), floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0)))
            for ((normal, quad) in faces) for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
                val p = quad[i]
                out += p[0]; out += p[1]; out += p[2]
                out += normal[0]; out += normal[1]; out += normal[2]
                out += r; out += g; out += b
            }
        }
        val h = 2.5f / 16f
        box(-h, -h, -.5f, h, h, .5f, 0xC4A06A)
        box(h, -.6f / 16f, -.5f, h + .5f / 16f, .6f / 16f, .5f, 0x6E5436)
        return out.toFloatArray()
    }

    companion object {
        private const val MESH_FLOATS = 9
        private const val INSTANCE_FLOATS = 6
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
            // Local Z becomes the shaft axis: 0 = X, 1 = Y, 2 = Z (both are proper rotations).
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
