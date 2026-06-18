package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.Atom2Universe.app.science.solarsystem.OrbitalCalculator.lerp
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
import kotlin.math.sqrt
import kotlin.random.Random

class SolarSystemRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // ── Shaders ──────────────────────────────────────────────────────
    private companion object {
        const val STACKS = 32; const val SLICES = 48
        const val ORBIT_SEGS = 128; const val RING_SEGS = 96; const val STAR_COUNT = 1800

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
              float d = max(dot(vNorm, L), 0.0);
              vec4 c = texture2D(uTex, vUV);
              gl_FragColor = vec4(c.rgb * (uAmbient + d * (1.0 - uAmbient)), c.a);
            }""".trimIndent()

        val SUN_VERT = """
            attribute vec4 aPos; attribute vec2 aUV;
            uniform mat4 uMVP; uniform mat4 uM;
            varying vec2 vUV;
            void main(){ gl_Position = uMVP * aPos; vUV = aUV; }""".trimIndent()

        val SUN_FRAG = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUV;
            void main(){
              vec4 c = texture2D(uTex, vUV);
              gl_FragColor = vec4(c.rgb * 1.15, c.a);
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

        val RINGS_VERT = """
            attribute vec4 aPos; attribute vec2 aUV;
            uniform mat4 uMVP;
            varying vec2 vUV;
            void main(){ gl_Position = uMVP * aPos; vUV = aUV; }""".trimIndent()

        val RINGS_FRAG = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUV;
            void main(){
              vec4 c = texture2D(uTex, vUV);
              float brightness = c.r + c.g + c.b;
              if(brightness < 0.12) discard;
              gl_FragColor = vec4(c.rgb, min(brightness * 0.8, 0.85));
            }""".trimIndent()
    }

    // ── Programs (cached uniform/attrib locations) ────────────────
    private var planetProg = 0; private var pMVP=0; private var pM=0
    private var pSunPos=0; private var pAmbient=0; private var pTex=0
    private var pPos=0; private var pUV=0; private var pNorm=0

    private var sunProg = 0; private var sMVP=0; private var sM=0
    private var sTex=0; private var sPos=0; private var sUV=0

    private var lineProg = 0; private var lMVP=0; private var lColor=0; private var lPos=0
    private var starProg = 0; private var stMVP=0; private var stPos=0
    private var ringsProg = 0; private var rMVP=0; private var rTex=0; private var rPos=0; private var rUV=0

    // ── GPU buffers ───────────────────────────────────────────────
    private var sphereVBO = 0; private var sphereEBO = 0; private var sphereIdxCount = 0
    private var orbitVBO = 0; private var ringsVBO = 0; private var ringsVertCount = 0
    private var starsVBO = 0

    // ── Textures ──────────────────────────────────────────────────
    private val planetTexIds = IntArray(8)
    private var sunTexId = 0; private var ringsTexId = 0

    // ── Matrices ──────────────────────────────────────────────────
    private var screenAspect = 1f
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val pv = FloatArray(16); private val model = FloatArray(16)
    private val mvp = FloatArray(16); private val tmp = FloatArray(16)

    // ── Camera (mutable from UI thread via GL thread access) ──────
    @Volatile var cameraYaw = 30f
    @Volatile var cameraPitch = 55f
    @Volatile var cameraDistance = 25f
    @Volatile var panX = 0f; @Volatile var panY = 0f

    // ── Simulation ────────────────────────────────────────────────
    @Volatile var paused = false
    @Volatile var speedDaysPerSec = 1.0
    @Volatile var targetModeBlend = 0f  // 0=CLOSE,1=COMPRESSED,2=REALISTIC
    @Volatile var currentModeBlend = 0f
    @Volatile var elapsedSimDays = 0.0
    private var lastFrameMs = -1L

    // ── Focus caméra ──────────────────────────────────────────────
    // -1 = Soleil/origine, 0..7 = planète
    @Volatile var focusPlanetIdx = -1
    private val orbitCenter = FloatArray(3)  // position lissée du point d'orbite

    // ── Planet world positions (set during draw, read by GLView for picking) ──
    val planetWorldPos = Array(8) { FloatArray(3) }
    var screenW = 1f; var screenH = 1f

    var onPlanetTapped: ((Int) -> Unit)? = null       // -1 = Sun
    var onPlanetLongPressed: ((Int) -> Unit)? = null  // -1 = Sun

    /** Distance minimum de zoom adaptée au mode courant. */
    val minZoomDistance: Float
        get() = when {
            targetModeBlend >= 2f -> 0.005f
            targetModeBlend >= 1f -> lerp(1.5f, 0.005f, targetModeBlend - 1f)
            else -> 1.5f
        }

    /** Distance caméra recommandée pour focaliser sur cette planète. */
    fun recommendedDistance(planetIdx: Int): Float {
        if (planetIdx < 0) return when {
            targetModeBlend <= 1f -> 25f
            else                  -> 220f
        }
        val p = SolarSystemData.planets[planetIdx]
        val pr = OrbitalCalculator.getPlanetRadius(p, targetModeBlend)
        return (pr * 12f).coerceAtLeast(minZoomDistance)
    }

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

        // Smooth mode blend
        val blendSpeed = 3f
        currentModeBlend += (targetModeBlend - currentModeBlend).let {
            it.coerceIn(-blendSpeed * dtSec, blendSpeed * dtSec)
        }

        // Mise à jour du centre d'orbite (suivi de la planète focalisée)
        val targetCX: Float; val targetCY: Float; val targetCZ: Float
        if (focusPlanetIdx in 0..7) {
            targetCX = planetWorldPos[focusPlanetIdx][0]
            targetCY = planetWorldPos[focusPlanetIdx][1]
            targetCZ = planetWorldPos[focusPlanetIdx][2]
        } else {
            targetCX = 0f; targetCY = 0f; targetCZ = 0f
        }
        val lerpT = (6f * dtSec).coerceIn(0f, 1f)
        orbitCenter[0] += (targetCX - orbitCenter[0]) * lerpT
        orbitCenter[1] += (targetCY - orbitCenter[1]) * lerpT
        orbitCenter[2] += (targetCZ - orbitCenter[2]) * lerpT

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Projection dynamique : ratio near/far ≈ 200 000 constant quelle que soit l'échelle,
        // ce qui évite le clipping et les artefacts de depth buffer en vue réaliste.
        val near = (cameraDistance * 0.005f).coerceAtLeast(0.000001f)
        val far  = (near * 200000f).coerceIn(200f, 10000f)
        Matrix.perspectiveM(proj, 0, 45f, screenAspect, near, far)

        buildViewMatrix()
        Matrix.multiplyMM(pv, 0, proj, 0, view, 0)

        renderStars()
        renderOrbits()
        renderSun()
        renderPlanets()
    }

    // ─────────────────────────────────────────────────────────────
    // Rendu du Soleil
    private fun renderSun() {
        val sunR = OrbitalCalculator.getSunRadius(currentModeBlend)
        Matrix.setIdentityM(model, 0)
        Matrix.scaleM(model, 0, sunR, sunR, sunR)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glUseProgram(sunProg)
        GLES20.glUniformMatrix4fv(sMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(sM, 1, false, model, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sunTexId)
        GLES20.glUniform1i(sTex, 0)
        drawSphere(sPos, sUV, -1)
    }

    // ─────────────────────────────────────────────────────────────
    // Rendu de toutes les planètes
    private fun renderPlanets() {
        val blend = currentModeBlend
        val sunPos = floatArrayOf(0f, 0f, 0f)

        SolarSystemData.planets.forEach { planet ->
            val orbitR = OrbitalCalculator.getOrbitRadius(planet, blend)
            val planetR = OrbitalCalculator.getPlanetRadius(planet, blend)
            val selfRot = OrbitalCalculator.selfRotationDeg(planet, elapsedSimDays)
            val pos3D = OrbitalCalculator.orbitPosition3D(planet, orbitR, elapsedSimDays)
            val wx = pos3D[0]; val wy = pos3D[1]; val wz = pos3D[2]

            planetWorldPos[planet.id][0] = wx
            planetWorldPos[planet.id][1] = wy
            planetWorldPos[planet.id][2] = wz

            // Sphère planète — axe orienté dans le repère écliptique :
            // azimut (longitude du pôle) puis obliquité, puis rotation propre autour de l'axe.
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, wx, wy, wz)
            Matrix.rotateM(model, 0, 90f - planet.axisEclLonDeg, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, planet.axisObliquityEclDeg, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, selfRot, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, planetR, planetR, planetR)
            Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

            GLES20.glUseProgram(planetProg)
            GLES20.glUniformMatrix4fv(pMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(pM, 1, false, model, 0)
            GLES20.glUniform3fv(pSunPos, 1, sunPos, 0)
            GLES20.glUniform3f(pAmbient, 0.06f, 0.06f, 0.06f)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, planetTexIds[planet.id])
            GLES20.glUniform1i(pTex, 0)
            drawSphere(pPos, pUV, pNorm)

            // Anneaux de Saturne
            if (planet.hasRings) renderRings(planet, wx, wy, wz, planetR, sunPos)
            // La Lune n'est pas affichée ici : une vue Terre-Lune dédiée (EarthMoonActivity) s'en charge.
        }
    }

    private fun renderRings(planet: PlanetDef, cx: Float, cy: Float, cz: Float, planetR: Float, sunPos: FloatArray) {
        val innerR = planetR * planet.ringsInnerFactor
        val outerR = planetR * planet.ringsOuterFactor

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, cz)
        Matrix.rotateM(model, 0, 90f - planet.axisEclLonDeg, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, planet.axisObliquityEclDeg, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, planetR, planetR, planetR)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)

        GLES20.glDepthMask(false)
        GLES20.glUseProgram(ringsProg)
        GLES20.glUniformMatrix4fv(rMVP, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ringsTexId)
        GLES20.glUniform1i(rTex, 0)

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

    private fun renderOrbits() {
        val blend = currentModeBlend
        GLES20.glUseProgram(lineProg)
        GLES20.glUniform4f(lColor, 1f, 1f, 1f, lerp(0.35f, 0.18f, blend / 2f))

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, orbitVBO)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 12, 0)

        SolarSystemData.planets.forEach { planet ->
            val r = OrbitalCalculator.getOrbitRadius(planet, blend)
            // Inclinaison du plan orbital : rotation autour de l'axe du nœud ascendant
            val omegaRad = Math.toRadians(planet.ascendingNodeDeg.toDouble()).toFloat()
            val nodeAxisX = cos(omegaRad); val nodeAxisZ = sin(omegaRad)
            Matrix.setIdentityM(model, 0)
            // Signe négatif : la rotation Rodrigues autour du nœud donne Y = -r·sin(u)·sin(i),
            // mais la formule orbitale 3D donne Y = +r·sin(u)·sin(i). On inverse pour coïncider.
            Matrix.rotateM(model, 0, -planet.orbitalInclinationDeg, nodeAxisX, 0f, nodeAxisZ)
            Matrix.scaleM(model, 0, r, 1f, r)
            Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
            GLES20.glUniformMatrix4fv(lMVP, 1, false, mvp, 0)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, ORBIT_SEGS)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun renderStars() {
        // Vue rotation seulement (pas de translation → étoiles à l'infini)
        val rotView = FloatArray(16)
        System.arraycopy(view, 0, rotView, 0, 16)
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

    // ── Utilitaires sphère ────────────────────────────────────────
    private fun drawSphere(posAttr: Int, uvAttr: Int, normAttr: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 3, GLES20.GL_FLOAT, false, stride, 0)
        if (uvAttr >= 0) {
            GLES20.glEnableVertexAttribArray(uvAttr)
            GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, stride, 12)
        }
        if (normAttr >= 0) {
            GLES20.glEnableVertexAttribArray(normAttr)
            GLES20.glVertexAttribPointer(normAttr, 3, GLES20.GL_FLOAT, false, stride, 20)
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    // ── Matrice de vue caméra sphérique ───────────────────────────
    // La caméra orbite autour de (orbitCenter + pan), ce qui permet
    // de suivre n'importe quelle planète tout en conservant le pan libre.
    private fun buildViewMatrix() {
        val yawR = Math.toRadians(cameraYaw.toDouble()).toFloat()
        val pitR = Math.toRadians(cameraPitch.toDouble().coerceIn(-89.0, 89.0)).toFloat()
        val cx = orbitCenter[0] + panX
        val cy = orbitCenter[1] + panY
        val cz = orbitCenter[2]
        val eyeX = cx + cameraDistance * cos(pitR) * sin(yawR)
        val eyeY = cy + cameraDistance * sin(pitR)
        val eyeZ = cz + cameraDistance * cos(pitR) * cos(yawR)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, cx, cy, cz, 0f, 1f, 0f)
    }

    // ── Projection de position monde → écran ─────────────────────
    fun projectToScreen(wx: Float, wy: Float, wz: Float): FloatArray {
        val clip = FloatArray(4)
        val world = floatArrayOf(wx, wy, wz, 1f)
        Matrix.multiplyMV(clip, 0, pv, 0, world, 0)
        if (abs(clip[3]) < 1e-6f) return floatArrayOf(-1f, -1f)
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return floatArrayOf(
            (ndcX + 1f) / 2f * screenW,
            (1f - (ndcY + 1f) / 2f) * screenH
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Compilation des shaders
    private fun compilePrograms() {
        planetProg = link(PLANET_VERT, PLANET_FRAG)
        pMVP = ul(planetProg, "uMVP"); pM = ul(planetProg, "uM")
        pSunPos = ul(planetProg, "uSunPos"); pAmbient = ul(planetProg, "uAmbient")
        pTex = ul(planetProg, "uTex")
        pPos = al(planetProg, "aPos"); pUV = al(planetProg, "aUV"); pNorm = al(planetProg, "aNorm")

        sunProg = link(SUN_VERT, SUN_FRAG)
        sMVP = ul(sunProg, "uMVP"); sM = ul(sunProg, "uM"); sTex = ul(sunProg, "uTex")
        sPos = al(sunProg, "aPos"); sUV = al(sunProg, "aUV")

        lineProg = link(LINE_VERT, LINE_FRAG)
        lMVP = ul(lineProg, "uMVP"); lColor = ul(lineProg, "uColor"); lPos = al(lineProg, "aPos")

        starProg = link(STAR_VERT, LINE_FRAG)
        stMVP = ul(starProg, "uMVP"); stPos = al(starProg, "aPos")

        ringsProg = link(RINGS_VERT, RINGS_FRAG)
        rMVP = ul(ringsProg, "uMVP"); rTex = ul(ringsProg, "uTex")
        rPos = al(ringsProg, "aPos"); rUV = al(ringsProg, "aUV")
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

    private fun ul(prog: Int, name: String) = GLES20.glGetUniformLocation(prog, name)
    private fun al(prog: Int, name: String) = GLES20.glGetAttribLocation(prog, name)

    // ─────────────────────────────────────────────────────────────
    // Construction de la géométrie
    private fun buildGeometry() {
        val vbos = IntArray(4); GLES20.glGenBuffers(4, vbos, 0)
        sphereVBO = vbos[0]; orbitVBO = vbos[1]; ringsVBO = vbos[2]; starsVBO = vbos[3]
        val ebo = IntArray(1); GLES20.glGenBuffers(1, ebo, 0); sphereEBO = ebo[0]

        // Sphère
        val (sv, si, ic) = buildSphere(); sphereIdxCount = ic
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sv.capacity() * 4, sv, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereEBO)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, si.capacity() * 2, si, GLES20.GL_STATIC_DRAW)

        // Cercle unité pour les orbites
        val orbitBuf = buildUnitCircle(ORBIT_SEGS)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, orbitVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, orbitBuf.capacity() * 4, orbitBuf, GLES20.GL_STATIC_DRAW)

        // Anneau (inner=ringsInnerFactor, outer=ringsOuterFactor pour Saturne)
        val satPlanet = SolarSystemData.planets[5]
        val ringBuf = buildRingStrip(satPlanet.ringsInnerFactor, satPlanet.ringsOuterFactor, RING_SEGS)
        ringsVertCount = (RING_SEGS + 1) * 2
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringsVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, ringBuf.capacity() * 4, ringBuf, GLES20.GL_STATIC_DRAW)

        // Étoiles
        val starBuf = buildStars(STAR_COUNT)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starsVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, starBuf.capacity() * 4, starBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
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
        vb.put(verts); vb.position(0)
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx); ib.position(0)
        return Triple(vb, ib, idx.size)
    }

    private fun buildUnitCircle(n: Int): FloatBuffer {
        val v = FloatArray(n * 3)
        for (i in 0 until n) {
            val t = (2.0 * Math.PI * i / n).toFloat()
            v[i * 3] = cos(t); v[i * 3 + 1] = 0f; v[i * 3 + 2] = sin(t)
        }
        return ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(v); it.position(0) }
    }

    private fun buildRingStrip(innerR: Float, outerR: Float, n: Int): FloatBuffer {
        val v = FloatArray((n + 1) * 2 * 5)
        var vi = 0
        for (i in 0..n) {
            val t = (2.0 * Math.PI * i / n).toFloat()
            val c = cos(t); val s = sin(t)
            v[vi++]=innerR*c; v[vi++]=0f; v[vi++]=innerR*s; v[vi++]=0f; v[vi++]=0.5f
            v[vi++]=outerR*c; v[vi++]=0f; v[vi++]=outerR*s; v[vi++]=1f; v[vi++]=0.5f
        }
        return ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(v); it.position(0) }
    }

    private fun buildStars(count: Int): FloatBuffer {
        val rng = Random(42)
        val v = FloatArray(count * 3)
        val r = 500f
        for (i in 0 until count) {
            val u = rng.nextDouble(); val t = rng.nextDouble() * 2 * Math.PI
            val phi = acos(1.0 - 2.0 * u)
            v[i * 3]   = (r * sin(phi) * cos(t)).toFloat()
            v[i * 3+1] = (r * cos(phi)).toFloat()
            v[i * 3+2] = (r * sin(phi) * sin(t)).toFloat()
        }
        return ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(v); it.position(0) }
    }

    // ─────────────────────────────────────────────────────────────
    // Chargement des textures
    private fun loadTextures() {
        sunTexId = loadTex(SolarSystemData.SUN_TEXTURE, SolarSystemData.SUN_FALLBACK_COLOR)
        SolarSystemData.planets.forEach { p ->
            planetTexIds[p.id] = loadTex(p.textureAsset, p.fallbackColor)
        }
        ringsTexId = loadTex("textures/planets/saturn_ring.jpg", 0x88C8B08C.toInt())
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
        } catch (_: IOException) {
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bmp.setPixel(0, 0, fallbackArgb)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
        }
        return id
    }
}
