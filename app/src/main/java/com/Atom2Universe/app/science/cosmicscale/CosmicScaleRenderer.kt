package com.Atom2Universe.app.science.cosmicscale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rendu comparatif de deux astres côte à côte (un par moitié d'écran).
 * Briques GL (sphère, shaders, chargement de texture) adaptées de [SolarSystemRenderer].
 */
class CosmicScaleRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private companion object {
        const val STACKS = 32; const val SLICES = 48
        const val BG_STAR_COUNT = 260
        /** Fraction de la plus petite demi-dimension de la moitié d'écran occupée par l'astre de référence. */
        const val FILL_FRACTION = 0.80f
        /** En deçà de ce rayon écran (px), l'astre est dessiné comme un point lumineux. */
        const val MIN_PIXEL_RADIUS = 1.6f
        // Calibration de l'image de trou noir (Assets/sprites/blackhole.jpg, 296×139) :
        // le bord externe de l'anneau de photons occupe 0.66 de la demi-hauteur de l'image.
        // On l'aligne sur rWorld → la taille visible correspond au rayon de Schwarzschild.
        const val BH_ASPECT = 296f / 139f
        const val BH_RING_FRAC = 0.66f

        val PLANET_VERT = """
            attribute vec4 aPos; attribute vec2 aUV; attribute vec3 aNorm;
            uniform mat4 uMVP; uniform mat4 uRot;
            varying vec2 vUV; varying vec3 vNorm;
            void main(){
              gl_Position = uMVP * aPos;
              vUV = aUV;
              vNorm = normalize(mat3(uRot) * aNorm);
            }""".trimIndent()

        // Lumière directionnelle fixe (haut-gauche, vers la caméra) — pas de Soleil dans la scène.
        val PLANET_FRAG = """
            precision mediump float;
            uniform sampler2D uTex; uniform vec3 uTint;
            varying vec2 vUV; varying vec3 vNorm;
            const vec3 L = vec3(-0.45, 0.55, 0.70);
            void main(){
              vec3 n = normalize(vNorm);
              float d = max(dot(n, normalize(L)), 0.0);
              float lit = 0.10 + 0.90 * d;
              vec4 c = texture2D(uTex, vUV);
              gl_FragColor = vec4(c.rgb * uTint * lit, c.a);
            }""".trimIndent()

        // Étoile : auto-émissive, teintée par température, assombrissement centre-bord (limb darkening).
        val STAR_FRAG = """
            precision mediump float;
            uniform sampler2D uTex; uniform vec3 uTint;
            varying vec2 vUV; varying vec3 vNorm;
            void main(){
              vec3 n = normalize(vNorm);
              float mu = clamp(n.z, 0.0, 1.0);              // angle vers la caméra (+Z)
              float limb = 0.45 + 0.55 * pow(mu, 0.45);     // plus sombre au limbe
              vec3 surf = texture2D(uTex, vUV).rgb;
              vec3 col = surf * uTint * limb * 1.25;
              gl_FragColor = vec4(col, 1.0);
            }""".trimIndent()

        // Halo / couronne : quad additif, dégradé radial.
        val GLOW_VERT = """
            attribute vec4 aPos; attribute vec2 aUV;
            uniform mat4 uMVP; varying vec2 vUV;
            void main(){ gl_Position = uMVP * aPos; vUV = aUV; }""".trimIndent()

        val GLOW_FRAG = """
            precision mediump float;
            uniform vec3 uColor; uniform float uStrength;
            varying vec2 vUV;
            void main(){
              vec2 p = vUV * 2.0 - 1.0;
              float r = length(p);
              float a = smoothstep(1.0, 0.0, r);
              a = pow(a, 2.2) * uStrength;
              gl_FragColor = vec4(uColor * a, a);
            }""".trimIndent()

        // Trou noir : billboard texturé sur fond noir → blend additif (le noir se fond, l'anneau ressort).
        val BH_FRAG = """
            precision mediump float;
            uniform sampler2D uTex; varying vec2 vUV;
            void main(){
              gl_FragColor = vec4(texture2D(uTex, vUV).rgb * 1.18, 1.0);
            }""".trimIndent()

        // Disque/anneau procédural (fallback trou noir sans texture).
        val BHPROC_FRAG = """
            precision mediump float;
            uniform float uTime; varying vec2 vUV;
            void main(){
              vec2 p = vUV * 2.0 - 1.0;
              float r = length(p);
              float horizon = smoothstep(0.34, 0.30, r);          // disque noir central
              float ring = smoothstep(0.30, 0.42, r) * smoothstep(0.95, 0.55, r);
              float ang = atan(p.y, p.x);
              float beam = 0.65 + 0.35 * sin(ang + uTime);         // dissymétrie Doppler
              vec3 hot = mix(vec3(1.0,0.55,0.12), vec3(1.0,0.9,0.6), ring);
              vec3 col = hot * ring * beam;
              float a = max(ring, 0.0) * (1.0 - horizon);
              gl_FragColor = vec4(col, a);
            }""".trimIndent()

        val RINGS_FRAG = """
            precision mediump float;
            uniform sampler2D uTex; varying vec2 vUV;
            void main(){
              vec4 c = texture2D(uTex, vUV);
              float b = c.r + c.g + c.b;
              if(b < 0.12) discard;
              gl_FragColor = vec4(c.rgb, min(b * 0.8, 0.85));
            }""".trimIndent()

        // Champ d'étoiles statique en espace écran (NDC).
        val BG_VERT = """
            attribute vec2 aPos; attribute float aBright;
            varying float vB;
            void main(){ gl_Position = vec4(aPos, 0.0, 1.0); gl_PointSize = 1.5; vB = aBright; }""".trimIndent()

        val BG_FRAG = """
            precision mediump float; varying float vB;
            void main(){ gl_FragColor = vec4(vec3(vB), 1.0); }""".trimIndent()

        // Point unique (astre sous-pixel en échelle réelle) + ligne séparatrice.
        val POINT_VERT = """
            attribute vec4 aPos; uniform float uSize;
            void main(){ gl_Position = aPos; gl_PointSize = uSize; }""".trimIndent()

        val SOLID_FRAG = """
            precision mediump float; uniform vec4 uColor;
            void main(){ gl_FragColor = uColor; }""".trimIndent()
    }

    // ── Programmes (locations en cache) ──────────────────────────────
    private var planetProg = 0; private var pMVP = 0; private var pRot = 0; private var pTex = 0; private var pTint = 0
    private var pPos = 0; private var pUV = 0; private var pNorm = 0

    private var starProg = 0; private var stMVP = 0; private var stRot = 0; private var stTex = 0; private var stTint = 0
    private var stPos = 0; private var stUV = 0; private var stNorm = 0

    private var glowProg = 0; private var gMVP = 0; private var gColor = 0; private var gStrength = 0; private var gPos = 0; private var gUV = 0
    private var bhProg = 0; private var bhMVP = 0; private var bhTex = 0; private var bhPos = 0; private var bhUV = 0
    private var bhpProg = 0; private var bhpMVP = 0; private var bhpTime = 0; private var bhpPos = 0; private var bhpUV = 0
    private var ringsProg = 0; private var rMVP = 0; private var rTex = 0; private var rPos = 0; private var rUV = 0
    private var bgProg = 0; private var bgPos = 0; private var bgBright = 0
    private var pointProg = 0; private var ptSize = 0; private var ptColor = 0; private var ptPos = 0

    // ── Buffers ──────────────────────────────────────────────────────
    private var sphereVBO = 0; private var sphereEBO = 0; private var sphereIdxCount = 0
    private var quadVBO = 0; private var ringsVBO = 0; private var ringsVertCount = 0
    private var bgVBO = 0; private var pointVBO = 0; private var dividerVBO = 0

    // ── Textures (cache par chemin d'asset) ─────────────────────────
    private val texCache = HashMap<String, Int>()

    // ── Matrices ─────────────────────────────────────────────────────
    private val proj = FloatArray(16); private val model = FloatArray(16)
    private val rot = FloatArray(16); private val mvp = FloatArray(16)

    private var surfaceW = 1; private var surfaceH = 1
    private var startMs = -1L

    // ── État pilotable depuis l'UI ──────────────────────────────────
    @Volatile var leftBody: CosmicBody = CosmicScaleData.byId("sun")
    @Volatile var rightBody: CosmicBody = CosmicScaleData.byId("earth")

    // ─────────────────────────────────────────────────────────────────
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.01f, 0.03f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        texCache.clear()
        compilePrograms()
        buildGeometry()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        surfaceW = w; surfaceH = h
    }

    override fun onDrawFrame(gl: GL10?) {
        if (startMs < 0) startMs = System.currentTimeMillis()
        val tSec = (System.currentTimeMillis() - startMs) / 1000f
        val spin = (tSec * 8f) % 360f

        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        renderBackground()
        renderDivider()

        val halfW = surfaceW / 2
        val maxKm = max(leftBody.radiusKm, rightBody.radiusKm)
        drawHalf(0, halfW, surfaceH, leftBody, maxKm, spin, tSec)
        drawHalf(halfW, surfaceW - halfW, surfaceH, rightBody, maxKm, spin, tSec)
    }

    /** Rend un astre centré dans la moitié d'écran [x, x+vw]. */
    private fun drawHalf(x: Int, vw: Int, vh: Int, body: CosmicBody, maxKm: Double, spin: Float, tSec: Float) {
        GLES20.glViewport(x, 0, vw, vh)
        val aspect = vw.toFloat() / vh.toFloat()
        val halfWWorld = 0.5f * aspect
        // Ortho : hauteur monde = 1.0 (−0.5..0.5), donc 1 unité monde = vh pixels.
        Matrix.orthoM(proj, 0, -halfWWorld, halfWWorld, -0.5f, 0.5f, -10f, 10f)

        // Borne le rayon de référence par la plus petite demi-dimension (largeur ou hauteur)
        // pour éviter la troncature latérale quand la moitié d'écran est plus étroite que haute.
        // Échelle réelle : le plus grand des deux astres remplit, l'autre est à sa taille relative.
        val ref = FILL_FRACTION * min(halfWWorld, 0.5f)
        val rWorld = (ref * (body.radiusKm / maxKm)).toFloat()
        val pixelRadius = rWorld * vh

        if (pixelRadius < MIN_PIXEL_RADIUS) {
            drawTinyPoint(body)
            return
        }

        when (body.kind) {
            BodyKind.STAR -> drawStar(body, rWorld, spin)
            BodyKind.BLACK_HOLE -> drawBlackHole(body, rWorld, tSec)
            else -> drawPlanet(body, rWorld, spin)
        }
    }

    // ── Astre minuscule (sous-pixel) : point lumineux ────────────────
    private fun drawTinyPoint(body: CosmicBody) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(pointProg)
        GLES20.glUniform1f(ptSize, 3.5f)
        val c = body.tintColor
        GLES20.glUniform4f(ptColor, Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f, 1f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointVBO)
        GLES20.glEnableVertexAttribArray(ptPos)
        GLES20.glVertexAttribPointer(ptPos, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    // ── Planète / Lune (sphère éclairée) ─────────────────────────────
    private fun drawPlanet(body: CosmicBody, rWorld: Float, spin: Float) {
        buildModel(rWorld, spin, tiltDeg = 18f)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)

        GLES20.glUseProgram(planetProg)
        GLES20.glUniformMatrix4fv(pMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(pRot, 1, false, rot, 0)
        GLES20.glUniform3f(pTint, 1f, 1f, 1f)
        bindTex(pTex, body.textureAsset, body.tintColor)
        drawSphere(pPos, pUV, pNorm)

        if (body.hasRings) drawRings(rWorld, spin)
    }

    // ── Étoile (couronne additive + sphère émissive) ─────────────────
    private fun drawStar(body: CosmicBody, rWorld: Float, spin: Float) {
        val c = body.tintColor
        val cr = Color.red(c) / 255f; val cg = Color.green(c) / 255f; val cb = Color.blue(c) / 255f

        // Couronne (quad additif derrière la sphère)
        GLES20.glDepthMask(false)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        drawGlowQuad(rWorld * 2.1f, cr, cg, cb, strength = 0.9f)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(true)

        // Sphère stellaire
        buildModel(rWorld, spin, tiltDeg = 12f)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
        GLES20.glUseProgram(starProg)
        GLES20.glUniformMatrix4fv(stMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(stRot, 1, false, rot, 0)
        GLES20.glUniform3f(stTint, cr, cg, cb)
        bindTexFor(stTex, body.textureAsset, c)
        drawSphere(stPos, stUV, stNorm)
    }

    // ── Trou noir (billboard image, fallback procédural) ────────────
    // L'horizon n'émet rien : on rend l'image de l'anneau de photons + disque, calée pour
    // que l'anneau externe = rWorld (cohérent avec le rayon dessiné des planètes/étoiles).
    private fun drawBlackHole(body: CosmicBody, rWorld: Float, tSec: Float) {
        val asset = body.textureAsset
        val hasRealTex = asset != null && assetLoaded[asset] == true
        GLES20.glDepthMask(false)

        if (hasRealTex) {
            val sy = rWorld / BH_RING_FRAC
            val sx = sy * BH_ASPECT
            buildBillboard(sx, sy)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)   // additif : le noir de l'image disparaît
            GLES20.glUseProgram(bhProg)
            GLES20.glUniformMatrix4fv(bhMVP, 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, resolveTex(asset, body.tintColor))
            GLES20.glUniform1i(bhTex, 0)
            drawQuad(bhPos, bhUV)
        } else {
            val s = rWorld / 0.42f   // l'anneau procédural occupe ~0.42 du quad
            buildBillboard(s, s)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            GLES20.glUseProgram(bhpProg)
            GLES20.glUniformMatrix4fv(bhpMVP, 1, false, mvp, 0)
            GLES20.glUniform1f(bhpTime, tSec)
            drawQuad(bhpPos, bhpUV)
        }
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(true)
    }

    // ── Anneaux de Saturne ───────────────────────────────────────────
    private fun drawRings(rWorld: Float, spin: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, 72f, 1f, 0f, 0f)   // forte inclinaison pour voir l'ellipse
        Matrix.rotateM(model, 0, spin * 0.3f, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, rWorld, rWorld, rWorld)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)

        GLES20.glDepthMask(false)
        GLES20.glUseProgram(ringsProg)
        GLES20.glUniformMatrix4fv(rMVP, 1, false, mvp, 0)
        bindTex(rTex, "textures/planets/saturn_ring.jpg", 0x88C8B08C.toInt())
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringsVBO)
        val stride = 5 * 4
        GLES20.glEnableVertexAttribArray(rPos)
        GLES20.glVertexAttribPointer(rPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(rUV)
        GLES20.glVertexAttribPointer(rUV, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringsVertCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(true)
    }

    private fun drawGlowQuad(scale: Float, r: Float, g: Float, b: Float, strength: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.scaleM(model, 0, scale, scale, scale)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
        GLES20.glUseProgram(glowProg)
        GLES20.glUniformMatrix4fv(gMVP, 1, false, mvp, 0)
        GLES20.glUniform3f(gColor, r, g, b)
        GLES20.glUniform1f(gStrength, strength)
        drawQuad(gPos, gUV)
    }

    // ── Fond + séparateur ────────────────────────────────────────────
    private fun renderBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(bgProg)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bgVBO)
        GLES20.glEnableVertexAttribArray(bgPos)
        GLES20.glVertexAttribPointer(bgPos, 2, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glEnableVertexAttribArray(bgBright)
        GLES20.glVertexAttribPointer(bgBright, 1, GLES20.GL_FLOAT, false, 12, 8)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, BG_STAR_COUNT)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun renderDivider() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(pointProg)
        GLES20.glUniform1f(ptSize, 1f)
        GLES20.glUniform4f(ptColor, 1f, 1f, 1f, 0.12f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dividerVBO)
        GLES20.glEnableVertexAttribArray(ptPos)
        GLES20.glVertexAttribPointer(ptPos, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    // ── Construction de matrices modèle ──────────────────────────────
    private fun buildModel(rWorld: Float, spin: Float, tiltDeg: Float) {
        Matrix.setIdentityM(rot, 0)
        Matrix.rotateM(rot, 0, tiltDeg, 1f, 0f, 0f)
        Matrix.rotateM(rot, 0, spin, 0f, 1f, 0f)
        System.arraycopy(rot, 0, model, 0, 16)
        Matrix.scaleM(model, 0, rWorld, rWorld, rWorld)
    }

    private fun buildBillboard(sx: Float, sy: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.scaleM(model, 0, sx, sy, 1f)
    }

    // ── Helpers de dessin ────────────────────────────────────────────
    private fun drawSphere(posAttr: Int, uvAttr: Int, normAttr: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(uvAttr)
        GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(normAttr)
        GLES20.glVertexAttribPointer(normAttr, 3, GLES20.GL_FLOAT, false, stride, 20)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawQuad(posAttr: Int, uvAttr: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVBO)
        val stride = 4 * 4
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(uvAttr)
        GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, stride, 8)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun bindTex(uniform: Int, asset: String?, fallback: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, resolveTex(asset, fallback))
        GLES20.glUniform1i(uniform, 0)
    }

    private fun bindTexFor(uniform: Int, asset: String?, fallback: Int) = bindTex(uniform, asset, fallback)

    // ── Textures ─────────────────────────────────────────────────────
    private var fallbackTexId = 0
    private val assetLoaded = HashMap<String, Boolean>()  // asset → texture réelle bien chargée ?

    private fun resolveTex(asset: String?, fallbackArgb: Int): Int {
        if (asset == null) return fallbackTexId
        texCache[asset]?.let { return it }
        val id = loadTex(asset, fallbackArgb)
        texCache[asset] = id
        return id
    }

    private fun loadTex(asset: String, fallbackArgb: Int): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        try {
            context.assets.open(asset).use { stream ->
                val opts = BitmapFactory.Options().apply { inScaled = false }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)!!
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                bmp.recycle()
            }
            assetLoaded[asset] = true
        } catch (_: IOException) {
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bmp.setPixel(0, 0, fallbackArgb)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            assetLoaded[asset] = false
        }
        return id
    }

    // ── Compilation des shaders ──────────────────────────────────────
    private fun compilePrograms() {
        planetProg = link(PLANET_VERT, PLANET_FRAG)
        pMVP = ul(planetProg, "uMVP"); pRot = ul(planetProg, "uRot"); pTex = ul(planetProg, "uTex"); pTint = ul(planetProg, "uTint")
        pPos = al(planetProg, "aPos"); pUV = al(planetProg, "aUV"); pNorm = al(planetProg, "aNorm")

        starProg = link(PLANET_VERT, STAR_FRAG)
        stMVP = ul(starProg, "uMVP"); stRot = ul(starProg, "uRot"); stTex = ul(starProg, "uTex"); stTint = ul(starProg, "uTint")
        stPos = al(starProg, "aPos"); stUV = al(starProg, "aUV"); stNorm = al(starProg, "aNorm")

        glowProg = link(GLOW_VERT, GLOW_FRAG)
        gMVP = ul(glowProg, "uMVP"); gColor = ul(glowProg, "uColor"); gStrength = ul(glowProg, "uStrength")
        gPos = al(glowProg, "aPos"); gUV = al(glowProg, "aUV")

        bhProg = link(GLOW_VERT, BH_FRAG)
        bhMVP = ul(bhProg, "uMVP"); bhTex = ul(bhProg, "uTex"); bhPos = al(bhProg, "aPos"); bhUV = al(bhProg, "aUV")

        bhpProg = link(GLOW_VERT, BHPROC_FRAG)
        bhpMVP = ul(bhpProg, "uMVP"); bhpTime = ul(bhpProg, "uTime"); bhpPos = al(bhpProg, "aPos"); bhpUV = al(bhpProg, "aUV")

        ringsProg = link(GLOW_VERT, RINGS_FRAG)
        rMVP = ul(ringsProg, "uMVP"); rTex = ul(ringsProg, "uTex"); rPos = al(ringsProg, "aPos"); rUV = al(ringsProg, "aUV")

        bgProg = link(BG_VERT, BG_FRAG)
        bgPos = al(bgProg, "aPos"); bgBright = al(bgProg, "aBright")

        pointProg = link(POINT_VERT, SOLID_FRAG)
        ptSize = ul(pointProg, "uSize"); ptColor = ul(pointProg, "uColor"); ptPos = al(pointProg, "aPos")

        // Texture de repli 1×1 noire (utilisée si une texture manque).
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); fallbackTexId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fallbackTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.BLACK) }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
    }

    private fun link(v: String, f: String): Int {
        fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, v))
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, f))
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun ul(p: Int, n: String) = GLES20.glGetUniformLocation(p, n)
    private fun al(p: Int, n: String) = GLES20.glGetAttribLocation(p, n)

    // ── Géométrie ────────────────────────────────────────────────────
    private fun buildGeometry() {
        val vbos = IntArray(5); GLES20.glGenBuffers(5, vbos, 0)
        sphereVBO = vbos[0]; quadVBO = vbos[1]; ringsVBO = vbos[2]; bgVBO = vbos[3]; pointVBO = vbos[4]
        val ebo = IntArray(1); GLES20.glGenBuffers(1, ebo, 0); sphereEBO = ebo[0]
        val dv = IntArray(1); GLES20.glGenBuffers(1, dv, 0); dividerVBO = dv[0]

        val (sv, si, ic) = buildSphere(); sphereIdxCount = ic
        bindArray(sphereVBO, sv)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, si.capacity() * 2, si, GLES20.GL_STATIC_DRAW)

        bindArray(quadVBO, buildQuad())
        val ringBuf = buildRingStrip(1.25f, 2.35f, 96); ringsVertCount = (96 + 1) * 2
        bindArray(ringsVBO, ringBuf)
        bindArray(bgVBO, buildBackgroundStars())
        bindArray(pointVBO, floatBuffer(floatArrayOf(0f, 0f)))
        bindArray(dividerVBO, floatBuffer(floatArrayOf(0f, -1f, 0f, 1f)))

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun bindArray(vbo: Int, buf: FloatBuffer) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES20.GL_STATIC_DRAW)
    }

    private fun buildSphere(): Triple<FloatBuffer, ShortBuffer, Int> {
        val verts = FloatArray((STACKS + 1) * (SLICES + 1) * 8)
        val idx = ShortArray(STACKS * SLICES * 6)
        var vi = 0; var ii = 0
        for (s in 0..STACKS) {
            val phi = (Math.PI * s / STACKS).toFloat()
            val y = cos(phi); val sinPhi = sin(phi)
            val v = s.toFloat() / STACKS
            for (sl in 0..SLICES) {
                val theta = (2.0 * Math.PI * sl / SLICES).toFloat()
                val xx = sinPhi * cos(theta); val zz = sinPhi * sin(theta)
                val u = 1f - sl.toFloat() / SLICES
                verts[vi++] = xx; verts[vi++] = y; verts[vi++] = zz
                verts[vi++] = u; verts[vi++] = v
                verts[vi++] = xx; verts[vi++] = y; verts[vi++] = zz
            }
        }
        for (s in 0 until STACKS) for (sl in 0 until SLICES) {
            val a = (s * (SLICES + 1) + sl).toShort()
            val b = ((s + 1) * (SLICES + 1) + sl).toShort()
            val c = (s * (SLICES + 1) + sl + 1).toShort()
            val d = ((s + 1) * (SLICES + 1) + sl + 1).toShort()
            idx[ii++] = a; idx[ii++] = b; idx[ii++] = c
            idx[ii++] = b; idx[ii++] = d; idx[ii++] = c
        }
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts); vb.position(0)
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx); ib.position(0)
        return Triple(vb, ib, idx.size)
    }

    /** Quad [-1,1]² avec UV [0,1]² (TRIANGLE_STRIP : BL, BR, TL, TR). */
    private fun buildQuad(): FloatBuffer = floatBuffer(floatArrayOf(
        -1f, -1f, 0f, 0f,
         1f, -1f, 1f, 0f,
        -1f,  1f, 0f, 1f,
         1f,  1f, 1f, 1f
    ))

    private fun buildRingStrip(innerR: Float, outerR: Float, n: Int): FloatBuffer {
        val v = FloatArray((n + 1) * 2 * 5); var vi = 0
        for (i in 0..n) {
            val t = (2.0 * Math.PI * i / n).toFloat(); val c = cos(t); val s = sin(t)
            v[vi++] = innerR * c; v[vi++] = 0f; v[vi++] = innerR * s; v[vi++] = 0f; v[vi++] = 0.5f
            v[vi++] = outerR * c; v[vi++] = 0f; v[vi++] = outerR * s; v[vi++] = 1f; v[vi++] = 0.5f
        }
        return floatBuffer(v)
    }

    private fun buildBackgroundStars(): FloatBuffer {
        val rng = Random(7)
        val v = FloatArray(BG_STAR_COUNT * 3)
        for (i in 0 until BG_STAR_COUNT) {
            v[i * 3] = rng.nextFloat() * 2f - 1f
            v[i * 3 + 1] = rng.nextFloat() * 2f - 1f
            v[i * 3 + 2] = 0.25f + rng.nextFloat() * 0.6f
        }
        return floatBuffer(v)
    }

    private fun floatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(arr); it.position(0) }
}
