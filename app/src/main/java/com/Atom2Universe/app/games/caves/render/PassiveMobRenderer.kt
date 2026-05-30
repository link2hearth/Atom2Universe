package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.PassiveMob
import com.Atom2Universe.app.games.caves.entity.PassiveMobState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Rendu 3D cuboid-based pour les mobs passifs (style Minecraft).
 * Texture : 64×32, UV layout standard Minecraft entity.
 *
 * Modèle vache (espace local, Y-up, +Z = avant de la vache) :
 *   Jambes : 0.25×0.65×0.25 aux coins du corps
 *   Corps  : 0.90×0.45×0.60, bas à y=0.65
 *   Tête   : 0.45×0.45×0.45, avant du corps
 */
internal class PassiveMobRenderer {

    private var shader: ShaderProgram? = null
    private var aPos = 0; private var aUv = 0; private var aLight = 0
    private var uMvp = 0; private var uTex = 0; private var uAmbient = 0

    private var cowTex = 0
    private var vbo = 0

    private val vertBuf = FloatArray(MAX_MOBS * FLOATS_PER_MOB)

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        in float a_light;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        out float v_fog;
        out float v_light;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_uv    = a_uv;
            v_light = a_light;
            v_fog   = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        uniform float u_ambient;
        in vec2 v_uv;
        in float v_fog;
        in float v_light;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, v_uv);
            if (col.a < 0.1) discard;
            col.rgb *= clamp(u_ambient * v_light, 0.05, 1.0);
            vec3 fog = vec3(0.008, 0.006, 0.015);
            fragColor = vec4(mix(col.rgb, fog, v_fog), 1.0);
        }
    """.trimIndent()

    // ── Init GL ───────────────────────────────────────────────────────────────

    fun onSurfaceCreated(assets: AssetManager) {
        shader = ShaderProgram(VERT, FRAG).also {
            it.use()
            aPos     = it.attrib("a_pos")
            aUv      = it.attrib("a_uv")
            aLight   = it.attrib("a_light")
            uMvp     = it.uniform("u_mvp")
            uTex     = it.uniform("u_tex")
            uAmbient = it.uniform("u_ambient")
        }
        cowTex = loadTexture(assets, "Cave World/Mobs/cow.png")
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    fun render(
        mobs: List<PassiveMob>,
        camX: Double, camY: Double, camZ: Double,
        vpMatrix: FloatArray,
        ambientLight: Float
    ) {
        if (mobs.isEmpty()) return
        val s = shader ?: return

        val cur = IntArray(1)
        for (mob in mobs) {
            if (mob.hp <= 0) continue
            val ex = (mob.x - camX).toFloat()
            val ey = (mob.y - camY).toFloat()
            val ez = (mob.z - camZ).toFloat()
            buildCowMesh(vertBuf, cur, ex, ey, ez, mob.yaw, mob.animTime,
                mob.state != PassiveMobState.IDLE)
        }

        val floatCount = cur[0]
        if (floatCount == 0) return

        val buf = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(vertBuf, 0, floatCount); buf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, buf, GLES30.GL_DYNAMIC_DRAW)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        s.use()
        GLES30.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cowTex)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1f(uAmbient, ambientLight)

        val byteStride = STRIDE * 4
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos,   3, GLES30.GL_FLOAT, false, byteStride, 0)
        GLES30.glEnableVertexAttribArray(aUv)
        GLES30.glVertexAttribPointer(aUv,    2, GLES30.GL_FLOAT, false, byteStride, 12)
        GLES30.glEnableVertexAttribArray(aLight)
        GLES30.glVertexAttribPointer(aLight, 1, GLES30.GL_FLOAT, false, byteStride, 20)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, floatCount / STRIDE)

        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aUv)
        GLES30.glDisableVertexAttribArray(aLight)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ── Construction du mesh vache ────────────────────────────────────────────

    private fun buildCowMesh(
        buf: FloatArray, cur: IntArray,
        ox: Float, oy: Float, oz: Float,
        yaw: Float, animTime: Float, walking: Boolean
    ) {
        val yRad = Math.toRadians(yaw.toDouble()).toFloat()
        val cosY = cos(yRad); val sinY = sin(yRad)

        val swing = if (walking) sin(animTime * LEG_SPEED) * LEG_AMP else 0f

        // Corps : x[-0.29,0.29]  y[0.48,0.90]  z[-0.28,0.28]
        addCuboid(buf, cur,
            -0.29f, 0.48f, -0.28f,  0.58f, 0.42f, 0.56f,
            UV_BODY, ox, oy, oz, cosY, sinY)

        // Tête : x[-0.19,0.19]  y[0.56,0.94]  z[0.28,0.66]
        addCuboid(buf, cur,
            -0.19f, 0.56f, 0.28f,  0.38f, 0.38f, 0.38f,
            UV_HEAD, ox, oy, oz, cosY, sinY)

        // Jambes avant : z[0.08,0.28], swing +
        addLeg(buf, cur, -0.27f, 0.08f,  swing, ox, oy, oz, cosY, sinY)  // avant-gauche
        addLeg(buf, cur,  0.07f, 0.08f,  swing, ox, oy, oz, cosY, sinY)  // avant-droite
        // Jambes arrière : z[-0.28,-0.08], swing -
        addLeg(buf, cur, -0.27f, -0.28f, -swing, ox, oy, oz, cosY, sinY) // arrière-gauche
        addLeg(buf, cur,  0.07f, -0.28f, -swing, ox, oy, oz, cosY, sinY) // arrière-droite
    }

    // Jambe avec inclinaison bas (simule marche sans rotation 3D complète)
    private fun addLeg(
        buf: FloatArray, cur: IntArray,
        lx0: Float, lz0: Float,
        swing: Float,
        ox: Float, oy: Float, oz: Float,
        cosY: Float, sinY: Float
    ) {
        val lx1 = lx0 + 0.20f
        val ly0 = 0f; val ly1 = 0.48f
        val lz1 = lz0 + 0.20f
        val bz = swing * 0.18f  // décalage Z du bas de jambe = animation

        // TOP (plat, pas de bz)
        putFace(buf, cur, UV_LEG, FACE_TOP, ox, oy, oz, cosY, sinY,
            lx0, ly1, lz0,  lx1, ly1, lz0,  lx1, ly1, lz1,  lx0, ly1, lz1, LIGHT_TOP)
        // BOTTOM (avec décalage bz)
        putFace(buf, cur, UV_LEG, FACE_BOT, ox, oy, oz, cosY, sinY,
            lx0, ly0, lz1+bz,  lx1, ly0, lz1+bz,  lx1, ly0, lz0+bz,  lx0, ly0, lz0+bz, LIGHT_BOTTOM)
        // RIGHT (+X)
        putFace(buf, cur, UV_LEG, FACE_RT, ox, oy, oz, cosY, sinY,
            lx1, ly1, lz0,  lx1, ly1, lz1,  lx1, ly0, lz1+bz,  lx1, ly0, lz0+bz, LIGHT_SIDE)
        // FRONT (+Z)
        putFace(buf, cur, UV_LEG, FACE_FR, ox, oy, oz, cosY, sinY,
            lx0, ly1, lz1,  lx1, ly1, lz1,  lx1, ly0, lz1+bz,  lx0, ly0, lz1+bz, LIGHT_SIDE)
        // LEFT (-X)
        putFace(buf, cur, UV_LEG, FACE_LT, ox, oy, oz, cosY, sinY,
            lx0, ly1, lz1,  lx0, ly1, lz0,  lx0, ly0, lz0+bz,  lx0, ly0, lz1+bz, LIGHT_SIDE)
        // BACK (-Z)
        putFace(buf, cur, UV_LEG, FACE_BK, ox, oy, oz, cosY, sinY,
            lx1, ly1, lz0,  lx0, ly1, lz0,  lx0, ly0, lz0+bz,  lx1, ly0, lz0+bz, LIGHT_SIDE)
    }

    private fun addCuboid(
        buf: FloatArray, cur: IntArray,
        cx0: Float, cy0: Float, cz0: Float,
        w: Float, h: Float, d: Float,
        uvPx: FloatArray,
        ox: Float, oy: Float, oz: Float,
        cosY: Float, sinY: Float
    ) {
        val cx1 = cx0 + w; val cy1 = cy0 + h; val cz1 = cz0 + d

        putFace(buf, cur, uvPx, FACE_TOP, ox, oy, oz, cosY, sinY,
            cx0, cy1, cz0,  cx1, cy1, cz0,  cx1, cy1, cz1,  cx0, cy1, cz1, LIGHT_TOP)
        putFace(buf, cur, uvPx, FACE_BOT, ox, oy, oz, cosY, sinY,
            cx0, cy0, cz1,  cx1, cy0, cz1,  cx1, cy0, cz0,  cx0, cy0, cz0, LIGHT_BOTTOM)
        putFace(buf, cur, uvPx, FACE_RT, ox, oy, oz, cosY, sinY,
            cx1, cy1, cz0,  cx1, cy1, cz1,  cx1, cy0, cz1,  cx1, cy0, cz0, LIGHT_SIDE)
        putFace(buf, cur, uvPx, FACE_FR, ox, oy, oz, cosY, sinY,
            cx0, cy1, cz1,  cx1, cy1, cz1,  cx1, cy0, cz1,  cx0, cy0, cz1, LIGHT_SIDE)
        putFace(buf, cur, uvPx, FACE_LT, ox, oy, oz, cosY, sinY,
            cx0, cy1, cz1,  cx0, cy1, cz0,  cx0, cy0, cz0,  cx0, cy0, cz1, LIGHT_SIDE)
        putFace(buf, cur, uvPx, FACE_BK, ox, oy, oz, cosY, sinY,
            cx1, cy1, cz0,  cx0, cy1, cz0,  cx0, cy0, cz0,  cx1, cy0, cz0, LIGHT_SIDE)
    }

    // Émet un quad (2 triangles CCW) avec UV de la face faceIdx
    private fun putFace(
        buf: FloatArray, cur: IntArray,
        uvPx: FloatArray, faceIdx: Int,
        ox: Float, oy: Float, oz: Float,
        cosY: Float, sinY: Float,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        light: Float
    ) {
        val uOff = faceIdx * 4
        val u0 = uvPx[uOff    ] / TEX_W;  val v0 = uvPx[uOff + 1] / TEX_H
        val u1 = uvPx[uOff + 2] / TEX_W;  val v1 = uvPx[uOff + 3] / TEX_H

        fun put(lx: Float, ly: Float, lz: Float, u: Float, v: Float) {
            var i = cur[0]
            buf[i++] = ox + cosY * lx + sinY * lz
            buf[i++] = oy + ly
            buf[i++] = oz - sinY * lx + cosY * lz
            buf[i++] = u; buf[i++] = v; buf[i++] = light
            cur[0] = i
        }

        put(ax, ay, az, u0, v0); put(bx, by, bz, u1, v0); put(cx, cy, cz, u1, v1)
        put(ax, ay, az, u0, v0); put(cx, cy, cz, u1, v1); put(dx, dy, dz, u0, v1)
    }

    // ── Texture ───────────────────────────────────────────────────────────────

    private fun loadTexture(assets: AssetManager, path: String): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        try {
            val bmp = BitmapFactory.decodeStream(assets.open(path))
            val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
            bmp.copyPixelsToBuffer(buf); buf.position(0)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            bmp.recycle()
        } catch (_: Exception) {
            // Texture manquante → magenta pour debug visuel
            val px = ByteBuffer.allocateDirect(4).apply {
                put(0xFF.toByte()); put(0x00.toByte()); put(0xFF.toByte()); put(0xFF.toByte()); position(0)
            }
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        return tex
    }

    fun destroy() {
        shader?.destroy()
        if (cowTex != 0) GLES30.glDeleteTextures(1, intArrayOf(cowTex), 0)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
    }

    // ── Constantes ────────────────────────────────────────────────────────────

    companion object {
        private const val TEX_W = 64f
        private const val TEX_H = 32f

        // 6 floats par sommet : xyz + uv + light
        private const val STRIDE = 6
        // 6 faces × 6 sommets × 6 floats
        private const val FLOATS_PER_CUBOID = 6 * 6 * STRIDE
        // body + head + 4 jambes
        private const val PARTS_PER_MOB = 6
        private const val FLOATS_PER_MOB = PARTS_PER_MOB * FLOATS_PER_CUBOID
        private const val MAX_MOBS = 10

        private const val LEG_SPEED = 6f
        private const val LEG_AMP   = 0.35f

        private const val LIGHT_TOP    = 1.0f
        private const val LIGHT_BOTTOM = 0.5f
        private const val LIGHT_SIDE   = 0.8f

        // Indices faces dans le tableau UV
        private const val FACE_TOP = 0
        private const val FACE_BOT = 1
        private const val FACE_RT  = 2
        private const val FACE_FR  = 3
        private const val FACE_LT  = 4
        private const val FACE_BK  = 5

        // UV Minecraft 64×32 — [u0, v0, u1, v1] × 6 faces
        // Ordre faces : TOP, BOTTOM, RIGHT(+X), FRONT(+Z), LEFT(-X), BACK(-Z)

        // Tête : offset(0,0), box 8×8×8
        private val UV_HEAD = floatArrayOf(
             8f,  0f, 16f,  8f,   // top
            16f,  0f, 24f,  8f,   // bottom
             0f,  8f,  8f, 16f,   // right
             8f,  8f, 16f, 16f,   // front
            16f,  8f, 24f, 16f,   // left
            24f,  8f, 32f, 16f    // back
        )

        // Corps : offset(18,4), box 10×8×6
        private val UV_BODY = floatArrayOf(
            24f,  4f, 34f, 10f,   // top
            34f,  4f, 44f, 10f,   // bottom
            18f, 10f, 24f, 18f,   // right
            24f, 10f, 34f, 18f,   // front
            34f, 10f, 40f, 18f,   // left
            40f, 10f, 50f, 18f    // back
        )

        // Jambe : offset(0,16), box 4×12×4
        private val UV_LEG = floatArrayOf(
             4f, 16f,  8f, 20f,   // top
             8f, 16f, 12f, 20f,   // bottom
             0f, 20f,  4f, 32f,   // right
             4f, 20f,  8f, 32f,   // front
             8f, 20f, 12f, 32f,   // left
            12f, 20f, 16f, 32f    // back
        )
    }
}
