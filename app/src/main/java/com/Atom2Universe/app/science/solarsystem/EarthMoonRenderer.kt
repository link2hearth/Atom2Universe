package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class EarthMoonRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private companion object {
        const val STACKS = 32; const val SLICES = 48
        const val ORBIT_SEGS = 128; const val STAR_COUNT = 1800

        // 1 unité de scène = KM_PER_UNIT km → orbite lunaire moyenne = 10 unités
        const val KM_PER_UNIT     = LunarCalculator.MEAN_DISTANCE_KM / 10.0   // 38 440 km/u
        const val EARTH_R_CLOSE   = 1.0f
        const val EARTH_R_REAL    = (6_371.0 / KM_PER_UNIT).toFloat()         // ≈ 0.1658
        const val MOON_R_CLOSE    = EARTH_R_CLOSE * (1_737f / 6_371f)         // ≈ 0.2725
        const val MOON_R_REAL     = (1_737.0 / KM_PER_UNIT).toFloat()         // ≈ 0.0452
        const val MOON_INCL_DEG   = 5.145f
        const val MOON_ORBIT_REAL_U  = 10f    // rayon orbital moyen physique (= 384 400 km / KM_PER_UNIT)
        const val MOON_ORBIT_CLOSE_U = 3.5f   // rayon orbital moyen resserré en mode rapproché

        val PLANET_VERT = """
            attribute vec4 aPos; attribute vec2 aUV; attribute vec3 aNorm;
            uniform mat4 uMVP; uniform mat4 uM;
            varying vec2 vUV; varying vec3 vNorm; varying vec3 vPos;
            void main(){
              gl_Position = uMVP * aPos;
              vUV = aUV;
              vNorm = normalize(mat3(uM) * aNorm);
              vPos = vec3(uM * aPos);
            }""".trimIndent()

        val PLANET_FRAG = """
            precision mediump float;
            uniform sampler2D uTex; uniform vec3 uSunPos; uniform vec3 uAmbient;
            varying vec2 vUV; varying vec3 vNorm; varying vec3 vPos;
            void main(){
              vec3 L = normalize(uSunPos - vPos);
              float d = max(dot(vNorm, L), 0.0) * 0.52;
              vec4 c = texture2D(uTex, vUV);
              gl_FragColor = vec4(c.rgb * (uAmbient + d), c.a);
            }""".trimIndent()

        val LINE_VERT = """
            attribute vec4 aPos; uniform mat4 uMVP;
            void main(){ gl_Position = uMVP * aPos; }""".trimIndent()

        val LINE_FRAG = """
            precision mediump float; uniform vec4 uColor;
            void main(){ gl_FragColor = uColor; }""".trimIndent()

        val STAR_VERT = """
            attribute vec4 aPos; uniform mat4 uMVP;
            void main(){ gl_Position = uMVP * aPos; gl_PointSize = 1.6; }""".trimIndent()

        // Shader émissif pour le Soleil (pas de calcul d'éclairage)
        val SUN_VERT = """
            attribute vec4 aPos; attribute vec2 aUV;
            uniform mat4 uMVP;
            varying vec2 vUV;
            void main(){ gl_Position = uMVP * aPos; vUV = aUV; }""".trimIndent()

        val SUN_FRAG = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUV;
            void main(){
              vec4 c = texture2D(uTex, vUV);
              gl_FragColor = vec4(c.rgb * 1.2, c.a);
            }""".trimIndent()

        // Distance du repère solaire en unités de scène (non physique, juste pour visibilité)
        const val SUN_INDICATOR_DIST = 80f
        const val SUN_INDICATOR_RADIUS = 3.5f
    }

    // Programs
    private var planetProg = 0
    private var pMVP = 0; private var pM = 0; private var pSunPos = 0
    private var pAmbient = 0; private var pTex = 0
    private var pPos = 0; private var pUV = 0; private var pNorm = 0

    private var sunProg = 0; private var sMVP = 0; private var sTex = 0
    private var sPos = 0; private var sUV = 0

    private var lineProg = 0; private var lMVP = 0; private var lColor = 0; private var lPos = 0
    private var starProg = 0; private var stMVP = 0; private var stPos = 0

    // GPU buffers
    private var sphereVBO = 0; private var sphereEBO = 0; private var sphereIdxCount = 0
    private var orbitVBO = 0; private var starsVBO = 0

    // Textures
    private var earthTexId = 0; private var moonTexId = 0; private var sunTexId = 0

    // Matrices
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val pv = FloatArray(16); private val model = FloatArray(16)
    private val mvp = FloatArray(16); private val tmp = FloatArray(16)

    // Camera
    @Volatile var cameraYaw = 30f
    @Volatile var cameraPitch = 25f
    @Volatile var cameraDistance = 15f
    private var screenAspect = 1f

    // Simulation
    @Volatile var paused = false
    @Volatile var speedDaysPerSec = 1.0
    @Volatile var elapsedSimDays = 0.0
    @Volatile var targetBlend = 0f   // 0=RAPPROCHÉ, 1=SEMI-RÉALISTE
    @Volatile var currentBlend = 0f
    private var lastFrameMs = -1L

    // Focus
    @Volatile var focusBody = 0  // 0=Terre, 1=Lune
    private val focusPos = FloatArray(3)

    // Positions monde (lues par la GLView pour le picking)
    val moonWorldPos = FloatArray(3)
    var screenW = 1f; var screenH = 1f

    var onBodyTapped: ((Int) -> Unit)? = null  // 0=Terre, 1=Lune

    val minZoomDistance: Float
        get() = if (targetBlend >= 1f) 0.002f else 0.5f

    fun recommendedDistance(bodyIdx: Int): Float {
        val r = if (bodyIdx == 1) getMoonR(targetBlend) else getEarthR(targetBlend)
        return (r * 8f).coerceAtLeast(minZoomDistance)
    }

    private fun getEarthR(b: Float) = lerp(EARTH_R_CLOSE, EARTH_R_REAL, b)
    private fun getMoonR(b: Float)  = lerp(MOON_R_CLOSE,  MOON_R_REAL,  b)
    /** Resserrement de l'orbite lunaire : rapprochée en mode CLOSE, distance réelle en mode REAL. */
    private fun moonOrbitScale(b: Float) = lerp(MOON_ORBIT_CLOSE_U / MOON_ORBIT_REAL_U, 1f, b)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    // ─────────────────────────────────────────────────────────────
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        compilePrograms()
        buildGeometry()
        loadTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        screenW = w.toFloat(); screenH = h.toFloat()
        screenAspect = w.toFloat() / h.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowMs = System.currentTimeMillis()
        if (lastFrameMs < 0) lastFrameMs = nowMs
        val dtRealMs = (nowMs - lastFrameMs).coerceAtMost(100)
        lastFrameMs = nowMs

        if (!paused) elapsedSimDays += dtRealMs / 1000.0 * speedDaysPerSec

        val dtSec = dtRealMs / 1000f
        currentBlend += (targetBlend - currentBlend).let { it.coerceIn(-3f * dtSec, 3f * dtSec) }

        // Position Lune (Meeus) — resserrée vers la Terre en mode rapproché
        val lunarPos = LunarCalculator.position(elapsedSimDays)
        val moonXyz  = LunarCalculator.position3D(lunarPos, KM_PER_UNIT)
        val oScale = moonOrbitScale(currentBlend)
        moonXyz[0] *= oScale; moonXyz[1] *= oScale; moonXyz[2] *= oScale
        moonWorldPos[0] = moonXyz[0]; moonWorldPos[1] = moonXyz[1]; moonWorldPos[2] = moonXyz[2]

        // Direction du Soleil (Terre héliocentrique → Soleil = direction opposée)
        val earthLong = OrbitalCalculator.orbitAngleDeg(SolarSystemData.planets[2], elapsedSimDays)
        val sunLongRad = Math.toRadians(((earthLong + 180.0) % 360.0))
        val sunPos = floatArrayOf(
            (1000f * cos(sunLongRad)).toFloat(), 0f,
            (1000f * sin(sunLongRad)).toFloat()
        )

        // Suivi focus
        val targetFX = if (focusBody == 1) moonXyz[0] else 0f
        val targetFY = if (focusBody == 1) moonXyz[1] else 0f
        val targetFZ = if (focusBody == 1) moonXyz[2] else 0f
        val lt = (6f * dtSec).coerceIn(0f, 1f)
        focusPos[0] += (targetFX - focusPos[0]) * lt
        focusPos[1] += (targetFY - focusPos[1]) * lt
        focusPos[2] += (targetFZ - focusPos[2]) * lt

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val near = (cameraDistance * 0.005f).coerceAtLeast(0.000001f)
        val far  = (near * 200000f).coerceIn(50f, 5000f)
        Matrix.perspectiveM(proj, 0, 45f, screenAspect, near, far)
        buildViewMatrix()
        Matrix.multiplyMM(pv, 0, proj, 0, view, 0)

        renderStars()
        renderMoonOrbit(lunarPos)
        renderSunIndicator(sunPos)
        renderEarth(sunPos)
        renderMoon(moonXyz, lunarPos.longitude, sunPos)
    }

    // ─────────────────────────────────────────────────────────────
    private fun renderEarth(sunPos: FloatArray) {
        val r = getEarthR(currentBlend)
        // Auto-rotation Terre : ~1 tr/jour sidéral (0.9973 j)
        val rot = ((elapsedSimDays / 0.9973) * 360.0).mod(360.0).toFloat()

        Matrix.setIdentityM(model, 0)
        // Axe terrestre fixe dans l'espace : projeté vers la longitude écliptique 90°
        // (= direction du Soleil au solstice de juin), donc pôle nord incliné vers +Z.
        Matrix.rotateM(model, 0, 23.44f, 1f, 0f, 0f)   // inclinaison axiale (vers +Z)
        Matrix.rotateM(model, 0, rot, 0f, 1f, 0f)       // rotation propre
        Matrix.scaleM(model, 0, r, r, r)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glUseProgram(planetProg)
        GLES20.glUniformMatrix4fv(pMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(pM,   1, false, model, 0)
        GLES20.glUniform3fv(pSunPos,  1, sunPos, 0)
        GLES20.glUniform3f(pAmbient, 0.025f, 0.025f, 0.04f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTexId)
        GLES20.glUniform1i(pTex, 0)
        drawSphere()
    }

    private fun renderMoon(xyz: FloatArray, lunarLongDeg: Double, sunPos: FloatArray) {
        val r = getMoonR(currentBlend)
        // Verrou de marée : face avant toujours vers la Terre (= origine)
        val moonFaceRot = (lunarLongDeg + 180.0).mod(360.0).toFloat()

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, xyz[0], xyz[1], xyz[2])
        Matrix.rotateM(model, 0, moonFaceRot, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, r, r, r)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glUseProgram(planetProg)
        GLES20.glUniformMatrix4fv(pMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(pM,   1, false, model, 0)
        GLES20.glUniform3fv(pSunPos,  1, sunPos, 0)
        GLES20.glUniform3f(pAmbient, 0.015f, 0.015f, 0.015f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, moonTexId)
        GLES20.glUniform1i(pTex, 0)
        drawSphere()
    }

    private fun renderSunIndicator(sunPos: FloatArray) {
        // Sphère non éclairée dans la direction du Soleil, à distance fixe (repère visuel)
        val dist = SUN_INDICATOR_DIST
        val r    = SUN_INDICATOR_RADIUS
        val sx = sunPos[0] / 1000f * dist
        val sy = sunPos[1] / 1000f * dist
        val sz = sunPos[2] / 1000f * dist

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, sx, sy, sz)
        Matrix.scaleM(model, 0, r, r, r)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glUseProgram(sunProg)
        GLES20.glUniformMatrix4fv(sMVP, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sunTexId)
        GLES20.glUniform1i(sTex, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(sPos)
        GLES20.glVertexAttribPointer(sPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(sUV)
        GLES20.glVertexAttribPointer(sUV, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun renderMoonOrbit(lunarPos: LunarCalculator.LunarPosition) {
        // Rayon moyen = 10 unités, orbite inclinée de MOON_INCL_DEG autour du nœud ascendant
        val omegaRad = Math.toRadians(lunarPos.ascendingNodeDeg).toFloat()
        val nodeX = cos(omegaRad); val nodeZ = sin(omegaRad)

        Matrix.setIdentityM(model, 0)
        // Même convention signe que pour les planètes (Rodrigues inversé)
        Matrix.rotateM(model, 0, -MOON_INCL_DEG, nodeX, 0f, nodeZ)
        val orbitR = MOON_ORBIT_REAL_U * moonOrbitScale(currentBlend)
        Matrix.scaleM(model, 0, orbitR, 1f, orbitR)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glUseProgram(lineProg)
        GLES20.glUniformMatrix4fv(lMVP, 1, false, mvp, 0)
        GLES20.glUniform4f(lColor, 1f, 1f, 1f, 0.22f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, orbitVBO)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, ORBIT_SEGS)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun renderStars() {
        val rotView = view.copyOf()
        rotView[12] = 0f; rotView[13] = 0f; rotView[14] = 0f; rotView[15] = 1f
        Matrix.multiplyMM(tmp, 0, proj, 0, rotView, 0)

        GLES20.glUseProgram(starProg)
        GLES20.glUniformMatrix4fv(stMVP, 1, false, tmp, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starsVBO)
        GLES20.glEnableVertexAttribArray(stPos)
        GLES20.glVertexAttribPointer(stPos, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, STAR_COUNT)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ─────────────────────────────────────────────────────────────
    private fun drawSphere() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(pPos)
        GLES20.glVertexAttribPointer(pPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(pUV)
        GLES20.glVertexAttribPointer(pUV, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(pNorm)
        GLES20.glVertexAttribPointer(pNorm, 3, GLES20.GL_FLOAT, false, stride, 20)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun buildViewMatrix() {
        val yR = Math.toRadians(cameraYaw.toDouble()).toFloat()
        val pR = Math.toRadians(cameraPitch.toDouble().coerceIn(-89.0, 89.0)).toFloat()
        val cx = focusPos[0]; val cy = focusPos[1]; val cz = focusPos[2]
        val eyeX = cx + cameraDistance * cos(pR) * sin(yR)
        val eyeY = cy + cameraDistance * sin(pR)
        val eyeZ = cz + cameraDistance * cos(pR) * cos(yR)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, cx, cy, cz, 0f, 1f, 0f)
    }

    fun projectToScreen(wx: Float, wy: Float, wz: Float): FloatArray {
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, pv, 0, floatArrayOf(wx, wy, wz, 1f), 0)
        if (abs(clip[3]) < 1e-6f) return floatArrayOf(-1f, -1f)
        val ndcX = clip[0] / clip[3]; val ndcY = clip[1] / clip[3]
        return floatArrayOf(
            (ndcX + 1f) / 2f * screenW,
            (1f - (ndcY + 1f) / 2f) * screenH
        )
    }

    // ─────────────────────────────────────────────────────────────
    private fun compilePrograms() {
        planetProg = link(PLANET_VERT, PLANET_FRAG)
        pMVP = ul(planetProg, "uMVP"); pM = ul(planetProg, "uM")
        pSunPos = ul(planetProg, "uSunPos"); pAmbient = ul(planetProg, "uAmbient")
        pTex = ul(planetProg, "uTex")
        pPos = al(planetProg, "aPos"); pUV = al(planetProg, "aUV"); pNorm = al(planetProg, "aNorm")

        sunProg = link(SUN_VERT, SUN_FRAG)
        sMVP = ul(sunProg, "uMVP"); sTex = ul(sunProg, "uTex")
        sPos = al(sunProg, "aPos"); sUV = al(sunProg, "aUV")

        lineProg = link(LINE_VERT, LINE_FRAG)
        lMVP = ul(lineProg, "uMVP"); lColor = ul(lineProg, "uColor"); lPos = al(lineProg, "aPos")

        starProg = link(STAR_VERT, LINE_FRAG)
        stMVP = ul(starProg, "uMVP"); stPos = al(starProg, "aPos")
    }

    private fun link(v: String, f: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, v))
            GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, f))
            GLES20.glLinkProgram(p)
        }
    }

    private fun ul(p: Int, n: String) = GLES20.glGetUniformLocation(p, n)
    private fun al(p: Int, n: String) = GLES20.glGetAttribLocation(p, n)

    // ─────────────────────────────────────────────────────────────
    private fun buildGeometry() {
        val vbos = IntArray(3); GLES20.glGenBuffers(3, vbos, 0)
        sphereVBO = vbos[0]; orbitVBO = vbos[1]; starsVBO = vbos[2]
        val ebo = IntArray(1); GLES20.glGenBuffers(1, ebo, 0); sphereEBO = ebo[0]

        val (sv, si, ic) = buildSphere(); sphereIdxCount = ic
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sv.capacity() * 4, sv, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, si.capacity() * 2, si, GLES20.GL_STATIC_DRAW)

        val orbitBuf = buildUnitCircle(ORBIT_SEGS)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, orbitVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, orbitBuf.capacity() * 4, orbitBuf, GLES20.GL_STATIC_DRAW)

        val starBuf = buildStars(STAR_COUNT)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starsVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, starBuf.capacity() * 4, starBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun buildSphere(): Triple<FloatBuffer, ShortBuffer, Int> {
        val verts = FloatArray((STACKS + 1) * (SLICES + 1) * 8)
        val idx   = ShortArray(STACKS * SLICES * 6)
        var vi = 0; var ii = 0
        for (s in 0..STACKS) {
            val phi = (Math.PI * s / STACKS).toFloat()
            val y = cos(phi); val sinPhi = sin(phi); val v = s.toFloat() / STACKS
            for (sl in 0..SLICES) {
                val theta = (2.0 * Math.PI * sl / SLICES).toFloat()
                val x = sinPhi * cos(theta); val z = sinPhi * sin(theta)
                val u = 1f - sl.toFloat() / SLICES
                verts[vi++]=x; verts[vi++]=y; verts[vi++]=z
                verts[vi++]=u; verts[vi++]=v
                verts[vi++]=x; verts[vi++]=y; verts[vi++]=z
            }
        }
        for (s in 0 until STACKS) for (sl in 0 until SLICES) {
            val a = (s * (SLICES + 1) + sl).toShort()
            val b = ((s + 1) * (SLICES + 1) + sl).toShort()
            val c = (s * (SLICES + 1) + sl + 1).toShort()
            val d = ((s + 1) * (SLICES + 1) + sl + 1).toShort()
            idx[ii++]=a; idx[ii++]=b; idx[ii++]=c
            idx[ii++]=b; idx[ii++]=d; idx[ii++]=c
        }
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(verts); it.position(0) }
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            .also { it.put(idx); it.position(0) }
        return Triple(vb, ib, idx.size)
    }

    private fun buildUnitCircle(n: Int): FloatBuffer {
        val v = FloatArray(n * 3)
        for (i in 0 until n) {
            val t = (2.0 * Math.PI * i / n).toFloat()
            v[i*3]=cos(t); v[i*3+1]=0f; v[i*3+2]=sin(t)
        }
        return ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(v); it.position(0) }
    }

    private fun buildStars(count: Int): FloatBuffer {
        val rng = Random(99)
        val v = FloatArray(count * 3)
        val r = 500f
        for (i in 0 until count) {
            val u = rng.nextDouble(); val t = rng.nextDouble() * 2 * Math.PI
            val phi = acos(1.0 - 2.0 * u)
            v[i*3]  =(r * sin(phi) * cos(t)).toFloat()
            v[i*3+1]=(r * cos(phi)).toFloat()
            v[i*3+2]=(r * sin(phi) * sin(t)).toFloat()
        }
        return ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(v); it.position(0) }
    }

    private fun loadTextures() {
        earthTexId = loadTex("textures/earth.jpg",         0xFF2244AA.toInt())
        moonTexId  = loadTex("textures/moon.jpg",          0xFFCCCCCC.toInt())
        sunTexId   = loadTex(SolarSystemData.SUN_TEXTURE,  SolarSystemData.SUN_FALLBACK_COLOR)
    }

    private fun loadTex(asset: String, fallback: Int): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        try {
            context.assets.open(asset).use { s ->
                val bmp = BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inScaled = false })!!
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                bmp.recycle()
            }
        } catch (_: IOException) {
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { it.setPixel(0, 0, fallback) }
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
        }
        return id
    }
}
