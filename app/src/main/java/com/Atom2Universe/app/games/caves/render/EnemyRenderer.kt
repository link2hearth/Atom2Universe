package com.Atom2Universe.app.games.caves.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal class EnemyRenderer {

    private var spriteShader: ShaderProgram? = null
    private var colorShader:  ShaderProgram? = null

    private var spriteAPos = 0; private var spriteAUv   = 0
    private var spriteUMvp = 0; private var spriteUTex  = 0
    private var colorAPos  = 0; private var colorAColor = 0
    private var colorUMvp  = 0

    private var atlasTex  = 0
    private var digitTex  = 0
    private var spriteVbo = 0
    private var colorVbo  = 0
    private var digitVbo  = 0

    // 5 floats/vtx × 6 vtx/quad × MAX quads
    private val spV = FloatArray(MAX_ENEMIES * 6 * 5)
    // 6 floats/vtx × 12 vtx/enemy (2 quads HP bar)
    private val coV = FloatArray(MAX_ENEMIES * 12 * 6)
    // 5 floats/vtx × 6 vtx/chiffre × MAX_DIGITS chiffres × MAX enemies
    private val diV = FloatArray(MAX_ENEMIES * MAX_DIGITS * 6 * 5)

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT_SPRITE = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        out float v_fog;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_uv  = a_uv;
            v_fog = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG_SPRITE = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        in vec2 v_uv;
        in float v_fog;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, v_uv);
            if (col.a < 0.1) discard;
            vec3 fog = vec3(0.008, 0.006, 0.015);
            fragColor = vec4(mix(col.rgb, fog, v_fog), 1.0);
        }
    """.trimIndent()

    private val VERT_COLOR = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
        }
    """.trimIndent()

    private val FRAG_COLOR = """
        #version 300 es
        precision mediump float;
        in vec3 v_color;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_color, 1.0);
        }
    """.trimIndent()

    // ── Init GL ───────────────────────────────────────────────────────────────

    fun onSurfaceCreated() {
        spriteShader = ShaderProgram(VERT_SPRITE, FRAG_SPRITE).also {
            it.use()
            spriteAPos  = it.attrib("a_pos")
            spriteAUv   = it.attrib("a_uv")
            spriteUMvp  = it.uniform("u_mvp")
            spriteUTex  = it.uniform("u_tex")
        }
        colorShader = ShaderProgram(VERT_COLOR, FRAG_COLOR).also {
            it.use()
            colorAPos   = it.attrib("a_pos")
            colorAColor = it.attrib("a_color")
            colorUMvp   = it.uniform("u_mvp")
        }
        atlasTex = buildSpriteAtlas()
        digitTex = buildDigitAtlas()

        val ids = IntArray(3); GLES30.glGenBuffers(3, ids, 0)
        spriteVbo = ids[0]; colorVbo = ids[1]; digitVbo = ids[2]
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    fun render(
        enemies: List<Enemy>,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float,
        vpMatrix: FloatArray
    ) {
        if (enemies.none { it.hp > 0 }) return

        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rightX = cos(yawRad).toFloat()
        val rightZ = (-sin(yawRad)).toFloat()

        var si = 0; var ci = 0; var di = 0

        for (e in enemies) {
            if (e.hp <= 0) continue

            val ex = (e.x - camX).toFloat()
            val ey = (e.y - camY).toFloat()
            val ez = (e.z - camZ).toFloat()

            val hw = e.type.spriteWidth  * 0.5f
            val h  = e.type.spriteHeight
            val au0 = e.type.ordinal * SPRITE_W.toFloat() / ATLAS_SIZE
            val au1 = (e.type.ordinal + 1) * SPRITE_W.toFloat() / ATLAS_SIZE
            val av1 = SPRITE_H.toFloat() / ATLAS_SIZE   // av0 = 0f

            // ── Sprite billboard ──────────────────────────────────────────────
            fun sv(rx: Float, ry: Float, u: Float, v: Float) {
                spV[si++] = ex + rightX * rx; spV[si++] = ey + ry; spV[si++] = ez + rightZ * rx
                spV[si++] = u; spV[si++] = v
            }
            sv(-hw, h,  au0, 0f); sv(-hw, 0f, au0, av1); sv( hw, 0f, au1, av1)
            sv(-hw, h,  au0, 0f); sv( hw, 0f, au1, av1); sv( hw, h,  au1, 0f)

            // ── HP bar ────────────────────────────────────────────────────────
            val barW   = e.type.spriteWidth
            val barY0  = ey + h + 0.12f
            val barY1  = barY0 + 0.14f
            val hpFrac = e.hp.toFloat() / e.maxHp.coerceAtLeast(1)
            val fgX1   = -barW * 0.5f + barW * hpFrac
            val (bgR, bgG, bgB) = levelBgColor(e.level)

            fun cv(rx: Float, ry: Float, r: Float, g: Float, b: Float) {
                coV[ci++] = ex + rightX * rx; coV[ci++] = ry; coV[ci++] = ez + rightZ * rx
                coV[ci++] = r; coV[ci++] = g; coV[ci++] = b
            }
            val bx0 = -barW * 0.5f; val bx1 = barW * 0.5f
            // fond (couleur niveau)
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx0, barY0, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB)
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB); cv(bx1, barY1, bgR, bgG, bgB)
            // avant (vert→rouge)
            val gr = 1f - hpFrac; val gg = hpFrac * 0.85f
            cv(bx0, barY1, gr, gg, 0f); cv(bx0, barY0, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f)
            cv(bx0, barY1, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f); cv(fgX1, barY1, gr, gg, 0f)

            // ── Numéro de niveau ──────────────────────────────────────────────
            val levelStr = e.level.toString()
            val digitW   = 0.16f
            val digitH   = 0.22f
            val gap      = 0.02f
            val totalW   = levelStr.length * (digitW + gap) - gap
            val digitY0  = barY1 + 0.06f
            val digitY1  = digitY0 + digitH
            val (dr, dg, db) = levelDigitColor(e.level)

            fun dv(rx: Float, ry: Float, u: Float, v: Float) {
                diV[di++] = ex + rightX * rx; diV[di++] = ry; diV[di++] = ez + rightZ * rx
                diV[di++] = u; diV[di++] = v
            }
            var curX = -totalW * 0.5f
            for (ch in levelStr) {
                val d = ch - '0'
                val du0 = d * DIGIT_W.toFloat() / DIGIT_ATLAS_W
                val du1 = (d + 1) * DIGIT_W.toFloat() / DIGIT_ATLAS_W
                dv(curX,          digitY1, du0, 0f)
                dv(curX,          digitY0, du0, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX + digitW, digitY0, du1, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX,          digitY1, du0, 0f)
                dv(curX + digitW, digitY0, du1, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX + digitW, digitY1, du1, 0f)
                curX += digitW + gap
            }
        }

        val spriteCount = si / 5
        val colorCount  = ci / 6
        val digitCount  = di / 5
        if (spriteCount == 0) return

        // ── Draw sprites ──────────────────────────────────────────────────────
        uploadAndBind(spriteVbo, spV, si)
        spriteShader?.use()
        GLES30.glUniformMatrix4fv(spriteUMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTex)
        GLES30.glUniform1i(spriteUTex, 0)
        bindSpriteAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, spriteCount)
        disableSpriteAttribs()

        // ── Draw HP bars ──────────────────────────────────────────────────────
        if (colorCount > 0) {
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-1f, -1f)
            uploadAndBind(colorVbo, coV, ci)
            colorShader?.use()
            GLES30.glUniformMatrix4fv(colorUMvp, 1, false, vpMatrix, 0)
            bindColorAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, colorCount)
            disableColorAttribs()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        }

        // ── Draw level numbers ────────────────────────────────────────────────
        if (digitCount > 0) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-2f, -2f)
            uploadAndBind(digitVbo, diV, di)
            spriteShader?.use()
            GLES30.glUniformMatrix4fv(spriteUMvp, 1, false, vpMatrix, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, digitTex)
            GLES30.glUniform1i(spriteUTex, 0)
            bindSpriteAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, digitCount)
            disableSpriteAttribs()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ── Helpers GL ────────────────────────────────────────────────────────────

    private fun uploadAndBind(vbo: Int, data: FloatArray, floatCount: Int) {
        val buf = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data, 0, floatCount); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, buf, GLES30.GL_DYNAMIC_DRAW)
    }

    private fun bindSpriteAttribs() {
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(spriteAPos)
        GLES30.glVertexAttribPointer(spriteAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(spriteAUv)
        GLES30.glVertexAttribPointer(spriteAUv,  2, GLES30.GL_FLOAT, false, stride, 12)
    }
    private fun disableSpriteAttribs() {
        GLES30.glDisableVertexAttribArray(spriteAPos); GLES30.glDisableVertexAttribArray(spriteAUv)
    }
    private fun bindColorAttribs() {
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(colorAPos)
        GLES30.glVertexAttribPointer(colorAPos,   3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(colorAColor)
        GLES30.glVertexAttribPointer(colorAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
    }
    private fun disableColorAttribs() {
        GLES30.glDisableVertexAttribArray(colorAPos); GLES30.glDisableVertexAttribArray(colorAColor)
    }

    fun destroy() {
        spriteShader?.destroy(); colorShader?.destroy()
        val texIds = intArrayOf(atlasTex, digitTex).filter { it != 0 }.toIntArray()
        if (texIds.isNotEmpty()) GLES30.glDeleteTextures(texIds.size, texIds, 0)
        GLES30.glDeleteBuffers(3, intArrayOf(spriteVbo, colorVbo, digitVbo), 0)
    }

    // ── Couleurs selon niveau ─────────────────────────────────────────────────

    private fun levelBgColor(level: Int): Triple<Float, Float, Float> = when {
        level <= 1  -> Triple(0.20f, 0.20f, 0.20f)   // gris
        level <= 3  -> Triple(0.05f, 0.10f, 0.40f)   // bleu nuit
        level <= 5  -> Triple(0.05f, 0.30f, 0.05f)   // vert sombre
        level <= 8  -> Triple(0.35f, 0.30f, 0.00f)   // jaune sombre
        level <= 11 -> Triple(0.40f, 0.18f, 0.00f)   // orange sombre
        level <= 14 -> Triple(0.40f, 0.02f, 0.02f)   // rouge sombre
        else        -> Triple(0.30f, 0.00f, 0.35f)   // violet
    }

    private fun levelDigitColor(level: Int): Triple<Float, Float, Float> = when {
        level <= 1  -> Triple(0.85f, 0.85f, 0.85f)   // gris clair
        level <= 3  -> Triple(0.50f, 0.75f, 1.00f)   // bleu clair
        level <= 5  -> Triple(0.40f, 1.00f, 0.40f)   // vert clair
        level <= 8  -> Triple(1.00f, 0.95f, 0.20f)   // jaune
        level <= 11 -> Triple(1.00f, 0.60f, 0.10f)   // orange
        level <= 14 -> Triple(1.00f, 0.25f, 0.25f)   // rouge
        else        -> Triple(0.90f, 0.30f, 1.00f)   // violet clair
    }

    // ── Atlas sprites personnages ─────────────────────────────────────────────

    private fun buildSpriteAtlas(): Int {
        val bmp = Bitmap.createBitmap(ATLAS_SIZE, ATLAS_SIZE, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); val p = Paint().apply { isAntiAlias = false }
        drawZombie  (cv, p, 0)
        drawSkeleton(cv, p, SPRITE_W)
        drawBat     (cv, p, SPRITE_W * 2)
        return uploadTex2D(bmp, nearest = true)
    }

    // ── Atlas chiffres 0–9 ────────────────────────────────────────────────────

    private fun buildDigitAtlas(): Int {
        val bmp = Bitmap.createBitmap(DIGIT_ATLAS_W, DIGIT_ATLAS_H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize  = DIGIT_H * 0.78f
            textAlign = Paint.Align.CENTER
            color = 0xFFFFFFFF.toInt()
        }
        for (d in 0..9) {
            cv.drawText(d.toString(), (d * DIGIT_W + DIGIT_W * 0.5f), DIGIT_H * 0.82f, p)
        }
        return uploadTex2D(bmp, nearest = false)
    }

    private fun uploadTex2D(bmp: Bitmap, nearest: Boolean): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf); buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        val filter = if (nearest) GLES30.GL_NEAREST else GLES30.GL_LINEAR
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        bmp.recycle()
        return tex
    }

    // ── Pixel art personnages ─────────────────────────────────────────────────

    private fun rect(cv: Canvas, p: Paint, ox: Int, l: Int, t: Int, r: Int, b: Int, col: Int) {
        p.color = col
        cv.drawRect((ox + l).toFloat(), t.toFloat(), (ox + r).toFloat(), b.toFloat(), p)
    }

    private fun drawZombie(cv: Canvas, p: Paint, ox: Int) {
        fun r(l: Int, t: Int, r: Int, b: Int, c: Int) = rect(cv, p, ox, l, t, r, b, c)
        r(20, 2, 44,  9,  0xFF3D2B0F.toInt())
        r(20, 9, 44,  26, 0xFF7BAD4D.toInt())
        r(24, 13, 30, 19, 0xFFFF2200.toInt()); r(25, 14, 29, 18, 0xFF8B0000.toInt())
        r(34, 13, 40, 19, 0xFFFF2200.toInt()); r(35, 14, 39, 18, 0xFF8B0000.toInt())
        r(26, 22, 38, 25, 0xFF3D2B0F.toInt())
        r(28, 21, 30, 23, 0xFF3D2B0F.toInt()); r(34, 21, 36, 23, 0xFF3D2B0F.toInt())
        r(28, 26, 36, 30, 0xFF6A9B40.toInt())
        r(22, 30, 42, 54, 0xFF2A1A0A.toInt()); r(24, 32, 40, 52, 0xFF3A2A10.toInt())
        r(26, 36, 28, 50, 0xFF1A0A00.toInt()); r(36, 38, 38, 52, 0xFF1A0A00.toInt())
        r(10, 24, 22, 46, 0xFF7BAD4D.toInt())
        r(42, 30, 54, 52, 0xFF7BAD4D.toInt())
        r(22, 54, 42, 58, 0xFF1A0A00.toInt()); r(30, 53, 34, 59, 0xFFAA8800.toInt())
        r(22, 58, 32, 90, 0xFF1A2B3F.toInt()); r(32, 58, 42, 90, 0xFF253040.toInt())
        r(20, 88, 32, 95, 0xFF0A0A0A.toInt()); r(32, 88, 44, 95, 0xFF0A0A0A.toInt())
    }

    private fun drawSkeleton(cv: Canvas, p: Paint, ox: Int) {
        fun r(l: Int, t: Int, r: Int, b: Int, c: Int) = rect(cv, p, ox, l, t, r, b, c)
        val BONE = 0xFFE5DEC8.toInt(); val BONE2 = 0xFFB8AF96.toInt()
        val DARK = 0xFF0A0A0A.toInt(); val GLOW  = 0xFF44AAFF.toInt()
        r(20, 2, 44, 22, BONE)
        r(22, 9, 30, 18, DARK); r(23, 10, 29, 17, GLOW)
        r(34, 9, 42, 18, DARK); r(35, 10, 41, 17, GLOW)
        r(30, 14, 34, 18, DARK)
        r(23, 20, 27, 25, BONE); r(28, 20, 32, 25, DARK)
        r(32, 20, 36, 25, BONE); r(36, 20, 40, 25, DARK); r(40, 20, 43, 25, BONE)
        r(30, 25, 34, 30, BONE2)
        r(22, 30, 42, 50, BONE)
        r(22, 34, 42, 35, DARK); r(22, 39, 42, 40, DARK); r(22, 44, 42, 45, DARK)
        r(30, 30, 34, 50, BONE2)
        r(22, 50, 42, 56, BONE); r(26, 52, 38, 54, DARK)
        r(14, 30, 22, 50, BONE); r(14, 44, 22, 46, BONE2)
        r(42, 30, 50, 50, BONE); r(42, 44, 50, 46, BONE2)
        r(12, 48, 16, 58, BONE2)
        r(22, 56, 32, 78, BONE); r(22, 68, 32, 70, BONE2)
        r(32, 56, 42, 78, BONE); r(32, 68, 42, 70, BONE2)
        r(20, 78, 32, 84, BONE2); r(32, 78, 44, 84, BONE2)
    }

    private fun drawBat(cv: Canvas, p: Paint, ox: Int) {
        fun r(l: Int, t: Int, r: Int, b: Int, c: Int) = rect(cv, p, ox, l, t, r, b, c)
        val WING = 0xFF2F1545.toInt(); val WING2 = 0xFF3D1E5C.toInt()
        val BODY = 0xFF1F0D2E.toInt(); val EYE  = 0xFFFF6600.toInt()
        val FANG = 0xFFE5DEC8.toInt()
        r(0, 28, 22, 62, WING); r(2, 22, 18, 28, WING2); r(4, 16, 14, 22, WING2)
        r(0, 58, 10, 70, WING2)
        r(42, 28, 64, 62, WING); r(46, 22, 62, 28, WING2); r(50, 16, 60, 22, WING2)
        r(54, 58, 64, 70, WING2)
        r(4, 30, 6, 60, WING2); r(10, 30, 12, 58, WING2); r(16, 30, 18, 56, WING2)
        r(46, 30, 48, 60, WING2); r(52, 30, 54, 58, WING2); r(58, 30, 60, 56, WING2)
        r(22, 22, 42, 64, BODY)
        r(22, 6, 42, 26, BODY)
        r(18, 4, 24, 16, BODY); r(40, 4, 46, 16, BODY)
        r(20, 2, 24, 6, BODY);  r(40, 2, 44, 6, BODY)
        r(25, 16, 31, 22, EYE); r(33, 16, 39, 22, EYE)
        r(27, 24, 30, 30, FANG); r(34, 24, 37, 30, FANG)
        r(26, 62, 30, 70, BODY); r(34, 62, 38, 70, BODY)
        r(24, 68, 28, 72, BODY); r(30, 69, 34, 73, BODY); r(36, 68, 40, 72, BODY)
    }

    companion object {
        private const val ATLAS_SIZE   = 256
        private const val SPRITE_W     = 64
        private const val SPRITE_H     = 96
        private const val DIGIT_ATLAS_W = 160   // 10 chiffres × 16 px
        private const val DIGIT_ATLAS_H = 20
        private const val DIGIT_W      = 16
        private const val DIGIT_H      = 20
        private const val MAX_ENEMIES  = 32
        private const val MAX_DIGITS   = 8      // niveau max affiché : 99 999 999
    }
}
