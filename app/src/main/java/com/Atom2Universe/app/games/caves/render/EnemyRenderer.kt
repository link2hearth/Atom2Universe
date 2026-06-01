package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.Enemy
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

    private val spV = FloatArray(MAX_VISIBLE * 6 * 5)
    private val coV = FloatArray(MAX_VISIBLE * 12 * 6)
    private val diV = FloatArray(MAX_VISIBLE * MAX_DIGITS * 6 * 5)

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
        uniform float u_flash;
        in vec2 v_uv;
        in float v_fog;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, v_uv);
            if (col.a < 0.1) discard;
            col.rgb = mix(col.rgb, vec3(1.0, 0.15, 0.15), u_flash * 0.7);
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

    private var spriteUFlash = 0

    fun onSurfaceCreated(assets: AssetManager, familyPool: List<String>) {
        spriteShader = ShaderProgram(VERT_SPRITE, FRAG_SPRITE).also {
            it.use()
            spriteAPos   = it.attrib("a_pos")
            spriteAUv    = it.attrib("a_uv")
            spriteUMvp   = it.uniform("u_mvp")
            spriteUTex   = it.uniform("u_tex")
            spriteUFlash = it.uniform("u_flash")
        }
        colorShader = ShaderProgram(VERT_COLOR, FRAG_COLOR).also {
            it.use()
            colorAPos   = it.attrib("a_pos")
            colorAColor = it.attrib("a_color")
            colorUMvp   = it.uniform("u_mvp")
        }
        atlasTex = buildSpriteAtlas(assets, familyPool)
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

        // La matrice view d'Android inverse l'axe X (eye.x = -world.x),
        // donc le vecteur "droite caméra" monde doit être négé pour pointer
        // vers la droite écran.
        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rightX = (-cos(yawRad)).toFloat()
        val rightZ = sin(yawRad).toFloat()

        var si = 0; var ci = 0; var di = 0
        var count = 0

        for (e in enemies) {
            if (e.hp <= 0) continue
            if (count >= MAX_VISIBLE) break
            count++

            val ex = (e.x - camX).toFloat()
            val ey = (e.y - camY).toFloat()
            val ez = (e.z - camZ).toFloat()

            val scale = e.baseScale * 2f
            val hw = scale * 0.5f
            val h  = scale

            val col = dirFrame(cameraYaw, e.yaw, e.animTime)
            val row = e.familyRow
            val au0 = col.toFloat() * SPRITE_SIZE / ATLAS_W
            val au1 = (col + 1).toFloat() * SPRITE_SIZE / ATLAS_W
            val av0 = row.toFloat() * SPRITE_SIZE / ATLAS_H
            val av1 = (row + 1).toFloat() * SPRITE_SIZE / ATLAS_H

            fun sv(rx: Float, ry: Float, u: Float, v: Float) {
                spV[si++] = ex + rightX * rx; spV[si++] = ey + ry; spV[si++] = ez + rightZ * rx
                spV[si++] = u; spV[si++] = v
            }
            sv(-hw, h,  au0, av0); sv(-hw, 0f, au0, av1); sv( hw, 0f, au1, av1)
            sv(-hw, h,  au0, av0); sv( hw, 0f, au1, av1); sv( hw, h,  au1, av0)

            // ── HP bar ────────────────────────────────────────────────────────
            val barW   = scale
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
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx0, barY0, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB)
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB); cv(bx1, barY1, bgR, bgG, bgB)
            val gr = 1f - hpFrac; val gg = hpFrac * 0.85f
            cv(bx0, barY1, gr, gg, 0f); cv(bx0, barY0, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f)
            cv(bx0, barY1, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f); cv(fgX1, barY1, gr, gg, 0f)

            // ── Numéro de niveau ──────────────────────────────────────────────
            val levelStr = if (e.isBoss) "BOSS" else e.level.toString()
            val digitW   = if (e.isBoss) 0.20f else 0.16f
            val digitH   = if (e.isBoss) 0.28f else 0.22f
            val gap      = 0.02f
            val totalW   = levelStr.length * (digitW + gap) - gap
            val digitY0  = barY1 + 0.06f
            val digitY1  = digitY0 + digitH
            val (dr, dg, db) = if (e.isBoss) Triple(1.0f, 0.6f, 0.0f) else levelDigitColor(e.level)

            fun dv(rx: Float, ry: Float, u: Float, v: Float) {
                diV[di++] = ex + rightX * rx; diV[di++] = ry; diV[di++] = ez + rightZ * rx
                diV[di++] = u; diV[di++] = v
            }
            var curX = -totalW * 0.5f
            for (ch in levelStr) {
                val d = if (ch.isDigit()) ch - '0' else 10 + (ch - 'A').coerceIn(0, 5)
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

        // ── Draw sprites par ennemi (flash individuel) ────────────────────────
        spriteShader?.use()
        GLES30.glUniformMatrix4fv(spriteUMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTex)
        GLES30.glUniform1i(spriteUTex, 0)

        var vertexOffset = 0
        for (e in enemies) {
            if (e.hp <= 0) continue
            GLES30.glUniform1f(spriteUFlash, e.hitFlash.coerceIn(0f, 1f))
            uploadAndBind(spriteVbo, spV, vertexOffset * 5, 6 * 5)
            bindSpriteAttribs()
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            disableSpriteAttribs()
            vertexOffset += 6
        }

        // ── Draw HP bars ──────────────────────────────────────────────────────
        if (colorCount > 0) {
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-1f, -1f)
            uploadAndBind(colorVbo, coV, 0, ci)
            colorShader?.use()
            GLES30.glUniformMatrix4fv(colorUMvp, 1, false, vpMatrix, 0)
            bindColorAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, colorCount)
            disableColorAttribs()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glDepthFunc(GLES30.GL_LESS)
        }

        // ── Draw level labels ─────────────────────────────────────────────────
        if (digitCount > 0) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-2f, -2f)
            uploadAndBind(digitVbo, diV, 0, di)
            spriteShader?.use()
            GLES30.glUniformMatrix4fv(spriteUMvp, 1, false, vpMatrix, 0)
            GLES30.glUniform1f(spriteUFlash, 0f)
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

    // ── Direction / animation ─────────────────────────────────────────────────

    private fun dirFrame(cameraYaw: Float, enemyYaw: Float, animTime: Float): Int {
        val rel = ((enemyYaw - cameraYaw) % 360f + 360f) % 360f
        val dirBase = when {
            rel < 45f || rel >= 315f -> 0   // bk
            rel < 135f               -> 4   // lf
            rel < 225f               -> 2   // fr
            else                     -> 6   // rt
        }
        return dirBase + ((animTime / ANIM_FRAME_DURATION).toInt() % 2)
    }

    // ── Helpers GL ────────────────────────────────────────────────────────────

    private fun uploadAndBind(vbo: Int, data: FloatArray, offset: Int, floatCount: Int) {
        val buf = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data, offset, floatCount); buf.position(0)
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
        level <= 1  -> Triple(0.20f, 0.20f, 0.20f)
        level <= 3  -> Triple(0.05f, 0.10f, 0.40f)
        level <= 5  -> Triple(0.05f, 0.30f, 0.05f)
        level <= 8  -> Triple(0.35f, 0.30f, 0.00f)
        level <= 11 -> Triple(0.40f, 0.18f, 0.00f)
        level <= 14 -> Triple(0.40f, 0.02f, 0.02f)
        else        -> Triple(0.30f, 0.00f, 0.35f)
    }

    private fun levelDigitColor(level: Int): Triple<Float, Float, Float> = when {
        level <= 1  -> Triple(0.85f, 0.85f, 0.85f)
        level <= 3  -> Triple(0.50f, 0.75f, 1.00f)
        level <= 5  -> Triple(0.40f, 1.00f, 0.40f)
        level <= 8  -> Triple(1.00f, 0.95f, 0.20f)
        level <= 11 -> Triple(1.00f, 0.60f, 0.10f)
        level <= 14 -> Triple(1.00f, 0.25f, 0.25f)
        else        -> Triple(0.90f, 0.30f, 1.00f)
    }

    // ── Atlas sprites (GIF assets) ────────────────────────────────────────────

    private fun buildSpriteAtlas(assets: AssetManager, familyPool: List<String>): Int {
        val bmp = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = false }

        for ((row, family) in familyPool.withIndex()) {
            for ((col, suffix) in FRAME_SUFFIXES.withIndex()) {
                val path = "Cave World/Tiles/foes/${family}_${suffix}.gif"
                try {
                    val stream = assets.open(path)
                    val raw = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (raw != null) {
                        val frame = makeWhiteTransparent(raw)
                        val dst = android.graphics.Rect(
                            col * SPRITE_SIZE, row * SPRITE_SIZE,
                            (col + 1) * SPRITE_SIZE, (row + 1) * SPRITE_SIZE
                        )
                        cv.drawBitmap(frame, null, dst, paint)
                        frame.recycle()
                    }
                } catch (_: Exception) {
                    paint.color = 0xFFFF00FF.toInt()
                    cv.drawRect(
                        (col * SPRITE_SIZE).toFloat(), (row * SPRITE_SIZE).toFloat(),
                        ((col + 1) * SPRITE_SIZE).toFloat(), ((row + 1) * SPRITE_SIZE).toFloat(),
                        paint
                    )
                }
            }
        }
        return uploadTex2D(bmp, nearest = true)
    }

    // Remplace les pixels blancs/quasi-blancs par du transparent (fond GIF)
    private fun makeWhiteTransparent(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        for (i in pixels.indices) {
            val c = pixels[i]
            if ((c shr 24) and 0xFF == 0) continue          // déjà transparent
            val r = (c shr 16) and 0xFF
            val g = (c shr 8)  and 0xFF
            val b =  c         and 0xFF
            if (r > 230 && g > 230 && b > 230) pixels[i] = 0
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // ── Atlas chiffres 0–9 + A–F (pour "BOSS") ───────────────────────────────

    private fun buildDigitAtlas(): Int {
        val chars = "0123456789ABCDEF"
        val atlasW = chars.length * DIGIT_W
        val bmp = Bitmap.createBitmap(atlasW, DIGIT_ATLAS_H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize  = DIGIT_H * 0.78f
            textAlign = Paint.Align.CENTER
            color = 0xFFFFFFFF.toInt()
        }
        for ((i, c) in chars.withIndex()) {
            cv.drawText(c.toString(), (i * DIGIT_W + DIGIT_W * 0.5f), DIGIT_H * 0.82f, p)
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

    companion object {
        private const val SPRITE_SIZE = 32
        private const val ATLAS_W     = 256   // 8 cols × 32 px
        private const val ATLAS_H     = 1024  // 32 rows × 32 px
        private const val ANIM_FRAME_DURATION = 0.35f

        // Ordre des colonnes dans l'atlas : bk1, bk2, fr1, fr2, lf1, lf2, rt1, rt2
        private val FRAME_SUFFIXES = arrayOf("bk1", "bk2", "fr1", "fr2", "lf1", "lf2", "rt1", "rt2")

        private const val DIGIT_ATLAS_W = 256   // 16 chars × 16 px
        private const val DIGIT_ATLAS_H = 20
        private const val DIGIT_W       = 16
        private const val DIGIT_H       = 20
        private const val MAX_VISIBLE   = 32
        private const val MAX_DIGITS    = 8
    }
}
