package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.Projectile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

internal class ProjectileRenderer {

    private var shader: ShaderProgram? = null
    private var aPos = 0; private var aUv = 0
    private var uMvp = 0; private var uTex = 0

    private val textures = IntArray(8)
    private var vbo = 0

    private val MAX_PROJ = 256
    private val vBuf = FloatArray(MAX_PROJ * 6 * 5)

    private val VERT = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_uv = a_uv;
        }
    """.trimIndent()

    private val FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            vec4 c = texture(u_tex, v_uv);
            if (c.a < 0.05) discard;
            fragColor = c;
        }
    """.trimIndent()

    fun onSurfaceCreated(assets: AssetManager) {
        shader = ShaderProgram(VERT, FRAG).also {
            it.use()
            aPos = it.attrib("a_pos"); aUv  = it.attrib("a_uv")
            uMvp = it.uniform("u_mvp"); uTex = it.uniform("u_tex")
        }
        // Ordre : WHITE_SQUARE, WHITE_SWIRL, BLUE_SQUARE, BLUE_SWIRL, ORANGE_SQUARE, ORANGE_SWIRL, RED_SQUARE, RED_SWIRL
        val files = listOf(
            "square_white.png", "swirl_white.png",
            "square_blue.png",  "swirl_blue.png",
            "square_orange.png","swirl_orange.png",
            "square_red.png",   "swirl_red.png"
        )
        val ids = IntArray(8); GLES30.glGenTextures(8, ids, 0)
        files.forEachIndexed { i, name ->
            textures[i] = ids[i]
            val bmp = BitmapFactory.decodeStream(assets.open("Cave World/Particles/$name"))
            val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
            bmp.copyPixelsToBuffer(buf); buf.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            bmp.recycle()
        }
        val vboIds = IntArray(1); GLES30.glGenBuffers(1, vboIds, 0); vbo = vboIds[0]
    }

    fun render(
        projectiles: List<Projectile>,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float, vpMatrix: FloatArray
    ) {
        if (projectiles.isEmpty()) return
        shader?.use() ?: return

        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rX = (-cos(yawRad)).toFloat()
        val rZ = sin(yawRad).toFloat()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uTex, 0)

        val byTex = projectiles.groupBy { it.weapon.texIndex }
        for ((texIdx, group) in byTex) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[texIdx.coerceIn(0, 7)])
            var si = 0
            for (p in group) {
                val px = (p.x - camX).toFloat()
                val py = (p.y - camY).toFloat()
                val pz = (p.z - camZ).toFloat()
                val hw = 0.20f
                fun sv(rx: Float, ry: Float, u: Float, v: Float) {
                    vBuf[si++] = px + rX * rx; vBuf[si++] = py + ry; vBuf[si++] = pz + rZ * rx
                    vBuf[si++] = u;             vBuf[si++] = v
                }
                sv(-hw,  hw, 0f, 0f); sv(-hw, -hw, 0f, 1f); sv( hw, -hw, 1f, 1f)
                sv(-hw,  hw, 0f, 0f); sv( hw, -hw, 1f, 1f); sv( hw,  hw, 1f, 0f)
            }
            val count = si / 5
            if (count == 0) continue
            val fb = ByteBuffer.allocateDirect(si * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            fb.put(vBuf, 0, si); fb.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, si * 4, fb, GLES30.GL_DYNAMIC_DRAW)
            val stride = 5 * 4
            GLES30.glEnableVertexAttribArray(aPos)
            GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(aUv)
            GLES30.glVertexAttribPointer(aUv,  2, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
            GLES30.glDisableVertexAttribArray(aPos)
            GLES30.glDisableVertexAttribArray(aUv)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun destroy() {
        shader?.destroy()
        if (textures.any { it != 0 }) GLES30.glDeleteTextures(8, textures, 0)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
    }
}
