package com.Atom2Universe.app.games.wavesurf

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*
import kotlin.random.Random

class WaveSurfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    enum class ColorTheme { VIVID, PASTEL, WEATHERED, GRAYSCALE }

    companion object {
        private const val PIXELS_PER_METER = 60f
        private const val PLAYER_SCREEN_X = 0.20f
        private const val TERRAIN_ANCHOR_Y = 150f
        private const val TERRAIN_SCREEN_Y = 0.68f
        private const val TRAIL_AGE = 0.4f
    }

    private data class Size(val width: Float, val height: Float)
    private data class TrailPoint(val x: Float, val y: Float, val time: Float)
    private enum class Input { PRESS, RELEASE, CANCEL }

    private val physics = WaveSurfPhysics()
    private val terrain get() = physics.terrain
    private val pendingPress = ConcurrentLinkedQueue<Input>()
    private var touchPressed = false
    private val pendingReset = AtomicBoolean(true)
    private val pendingSize = AtomicReference<Size?>(null)
    @Volatile private var running = false
    private var resumed = false
    private var surfaceReady = false
    private var thread: Thread? = null
    @Volatile private var renderHandler: Handler? = null
    private var hardwareCanvas = true
    @Volatile var currentTheme = ColorTheme.VIVID
        private set

    private var vw = 1f
    private var vh = 1f
    private var camX = 0f
    private var camY = 0f
    private var camS = 1f
    private var renderX = 0f
    private var renderY = 0f
    private var cameraZoom = 0f
    private var cameraZoomVelocity = 0f
    private var elapsed = 0f
    private var lastTrail = -1f
    private var lastStats = -1f
    private val trail = ArrayDeque<TrailPoint>()
    private val prefs = context.getSharedPreferences("wave_surf_save", Context.MODE_PRIVATE)
    private var bestSpeed = prefs.getInt("best_speed", 0)
    private var bestAltitude = prefs.getInt("best_altitude", 0)
    private var savedSpeed = bestSpeed
    private var savedAltitude = bestAltitude

    private val skyPaint = Paint()
    private val terrFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrainPath = Path()
    private val surfacePath = Path()
    private val stars = Array(45) { Triple(Random.nextFloat(), Random.nextFloat() * 0.55f, 0.7f + Random.nextFloat() * 1.1f) }

    var onStats: ((distM: Float, speedKmh: Float, altitudeM: Float) -> Unit)? = null

    init { holder.addCallback(this); isFocusable = true }

    // Le fil de jeu possède le terrain et la physique. Le fil UI dépose des
    // commandes, même pour recommencer : aucune mutation pendant le dessin.
    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        pendingSize.set(Size(width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f)))
        surfaceReady = true
        startLoopIfReady()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopLoop()
    }

    fun resume() {
        resumed = true
        startLoopIfReady()
    }

    fun pause() {
        resumed = false
        stopLoop()
    }

    private fun startLoopIfReady() {
        if (!resumed || !surfaceReady || running) return
        running = true
        thread = Thread(this, "WaveSurf").apply { start() }
    }

    private fun stopLoop() {
        running = false
        renderHandler?.post { Looper.myLooper()?.quit() }
        thread?.join()
        thread = null
        pendingPress.clear()
        touchPressed = false
        physics.cancelPress()
        saveRecords()
    }

    fun cycleTheme(): ColorTheme {
        currentTheme = ColorTheme.entries[(currentTheme.ordinal + 1) % ColorTheme.entries.size]
        return currentTheme
    }

    fun resetGame() {
        touchPressed = false
        pendingPress.clear()
        pendingReset.set(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> setTouchPressed(true, newPress = true)
            // Le relâchement du dernier doigt libère la balle, y compris après
            // un appui à plusieurs doigts. Une annulation libère toujours l'appui.
            MotionEvent.ACTION_POINTER_UP -> setTouchPressed(event.pointerCount > 1)
            MotionEvent.ACTION_UP -> {
                setTouchPressed(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                touchPressed = false
                pendingPress.add(Input.CANCEL)
            }
        }
        return true
    }

    private fun setTouchPressed(pressed: Boolean, newPress: Boolean = false) {
        if (!running || (touchPressed == pressed && !newPress)) return
        touchPressed = pressed
        // Préserve même un appui bref entièrement situé entre deux images.
        pendingPress.add(if (pressed) Input.PRESS else Input.RELEASE)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun run() {
        // La surface est dessinée au rythme réel de l'écran (60/90/120 Hz),
        // au lieu d'un sleep à 60 Hz qui dérive par rapport à sa synchronisation.
        Looper.prepare()
        val looper = checkNotNull(Looper.myLooper())
        val choreographer = Choreographer.getInstance()
        renderHandler = Handler(looper)
        if (!running) {
            renderHandler = null
            return
        }
        var lastNs = 0L
        var accumulator = 0f
        var previousX = physics.x
        var previousY = physics.y
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(now: Long) {
                if (!running) return
                val frameDt = if (lastNs == 0L) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.1f)
                lastNs = now
                pendingSize.getAndSet(null)?.let { size ->
                    vw = size.width; vh = size.height
                    camS = baseScale() * exp(cameraZoom)
                    // Une nouvelle surface ne remet pas la partie à zéro.
                    snapCamera()
                }
                if (pendingReset.getAndSet(false)) {
                    saveRecords()
                    physics.reset()
                    previousX = physics.x; previousY = physics.y
                    renderX = physics.x; renderY = physics.y
                    trail.clear(); elapsed = 0f; lastTrail = -1f; lastStats = -1f
                    cameraZoom = 0f; cameraZoomVelocity = 0f
                    camS = baseScale()
                    snapCamera()
                    accumulator = 0f
                }
                while (true) {
                    when (pendingPress.poll() ?: break) {
                        Input.PRESS -> physics.setPress(true)
                        Input.RELEASE -> physics.setPress(false)
                        Input.CANCEL -> physics.cancelPress()
                    }
                }
                accumulator += frameDt
                while (accumulator >= WaveSurfPhysics.STEP) {
                    previousX = physics.x; previousY = physics.y
                    physics.step()
                    elapsed += WaveSurfPhysics.STEP
                    accumulator -= WaveSurfPhysics.STEP
                    captureTrail()
                }
                // Même interpolation pour la bille et la caméra.
                val fraction = accumulator / WaveSurfPhysics.STEP
                renderX = previousX + (physics.x - previousX) * fraction
                renderY = previousY + (physics.y - previousY) * fraction
                updateCamera(frameDt)
                terrain.ensure(camX + vw / camS + 200f)
                terrain.prune(camX - 1600f)
                publishStats()
                val canvas = try { lockFrame() } catch (_: Exception) { null }
                if (canvas != null) {
                    try {
                        drawSky(canvas)
                        drawTerrain(canvas)
                        drawBall(canvas)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
                if (running) choreographer.postFrameCallback(this)
            }
        }
        try {
            choreographer.postFrameCallback(callback)
            Looper.loop()
        } finally {
            choreographer.removeFrameCallback(callback)
            renderHandler = null
            running = false
        }
    }

    /** Redessine toute la surface sur le GPU, avec repli logiciel si nécessaire. */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Exception) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    private fun baseScale() = min(vw / 2600f, vh / 1000f)

    private fun snapCamera() {
        camX = renderX - vw / camS * PLAYER_SCREEN_X
        camY = TERRAIN_ANCHOR_Y - vh / camS * TERRAIN_SCREEN_Y
    }

    private fun updateCamera(dt: Float) {
        // Suit la hauteur courante, avec une courte anticipation de la montée.
        // Aucun sommet mémorisé, verrou pendant le vol ni délai après réception.
        val predictedY = renderY + min(physics.vy, 0f) * 0.16f
        val height = (TERRAIN_ANCHOR_Y - predictedY).coerceAtLeast(0f)
        // Marge fixe de cadrage : sa taille ne doit pas rétroagir sur le zoom.
        val topSpace = (vh * (TERRAIN_SCREEN_Y - 0.10f) - baseBallRadius() * 2f).coerceAtLeast(1f)
        val availableHeight = topSpace / baseScale()
        val zoomStart = min(400f, availableHeight * 0.45f)
        val zoomRange = min(1600f, availableHeight * 0.5f)
        val excess = (height - zoomStart).coerceAtLeast(0f) / zoomRange
        // La courbe démarre avec une dérivée nulle : pas de cran à son entrée.
        val targetZoom = -0.5f * ln(1f + excess * excess)

        // Ressort amorti critique en échelle logarithmique : la vitesse du zoom
        // reste continue, y compris quand on clique, relâche ou touche le sol.
        // Solution analytique indépendante de la fréquence de l'écran.
        val response = 9f
        val error = cameraZoom - targetZoom
        val motion = cameraZoomVelocity + response * error
        val decay = exp(-response * dt)
        cameraZoom = targetZoom + (error + motion * dt) * decay
        cameraZoomVelocity = (cameraZoomVelocity - response * motion * dt) * decay
        camS = baseScale() * exp(cameraZoom)
        // Pivot fixe sur la bille interpolée : pas de second filtre horizontal
        // qui retarde le décor puis le fait rattraper à chaque changement de zoom.
        snapCamera()
    }

    private fun publishStats() {
        val speedKmh = physics.speed / PIXELS_PER_METER * 3.6f
        val altitudeM = physics.altitude / PIXELS_PER_METER
        bestSpeed = max(bestSpeed, speedKmh.toInt())
        bestAltitude = max(bestAltitude, altitudeM.toInt())
        if (elapsed - lastStats < 0.1f) return
        lastStats = elapsed
        saveRecords()
        onStats?.invoke(physics.x.coerceAtLeast(0f) / PIXELS_PER_METER, speedKmh, altitudeM)
    }

    private fun saveRecords() {
        if (bestSpeed <= savedSpeed && bestAltitude <= savedAltitude) return
        prefs.edit().putInt("best_speed", bestSpeed).putInt("best_altitude", bestAltitude).apply()
        savedSpeed = bestSpeed; savedAltitude = bestAltitude
    }

    private fun baseBallRadius() = 8f * resources.displayMetrics.density

    // La bille, son halo et sa traînée changent d'échelle avec le terrain.
    // À zoom normal, on conserve la taille habituelle ; à zoom moitié, le rayon aussi.
    private fun ballRadius() = baseBallRadius() * (camS / baseScale())

    private fun captureTrail() {
        if (elapsed - lastTrail >= 0.025f) {
            trail.addLast(TrailPoint(physics.x, physics.y, elapsed))
            lastTrail = elapsed
        }
        while (trail.isNotEmpty() && elapsed - trail.first().time > TRAIL_AGE) trail.removeFirst()
    }

    private fun drawSky(canvas: Canvas) {
        skyPaint.shader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.rgb(5, 8, 16), Color.rgb(4, 6, 13), Color.rgb(1, 1, 3)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, vw, vh, skyPaint)
        stars.forEach { (rx, ry, r) ->
            starPaint.color = Color.argb((90 + (rx * 130).toInt()).coerceIn(60, 210), 255, 255, 255)
            canvas.drawCircle(rx * vw, ry * vh, r, starPaint)
        }
    }

    private fun drawTerrain(canvas: Canvas) {
        terrainPath.rewind(); surfacePath.rewind()
        terrainPath.moveTo(-8f, vh)
        var screenX = -8f
        while (screenX <= vw + 8f) {
            val screenY = (terrain.height(camX + screenX / camS) - camY) * camS
            terrainPath.lineTo(screenX, screenY)
            if (screenX == -8f) surfacePath.moveTo(screenX, screenY) else surfacePath.lineTo(screenX, screenY)
            screenX += 4f
        }
        terrainPath.lineTo(vw + 8f, vh); terrainPath.close()
        val colors = IntArray(8) { getTerrainColor(camX + vw / camS * it / 7f) }
        terrFill.shader = LinearGradient(0f, 0f, vw, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawPath(terrainPath, terrFill)
        terrDark.shader = LinearGradient(0f, 0f, 0f, vh,
            intArrayOf(Color.TRANSPARENT, Color.argb(175, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(terrainPath, terrDark)
        terrStroke.color = Color.argb(130, 255, 255, 255)
        terrStroke.strokeWidth = resources.displayMetrics.density * 1.2f
        canvas.drawPath(surfacePath, terrStroke)
    }

    private fun drawBall(canvas: Canvas) {
        val r = ballRadius()
        val bx = (renderX - camX) * camS
        // Même décalage en glisse et en vol : le centre ne saute pas au décollage.
        val by = (renderY - camY) * camS - r * 0.8f
        val hue = 210f * (1f - (physics.speed / 1500f).coerceIn(0f, 1f))
        val color = Color.HSVToColor(floatArrayOf(hue, 0.78f, 1f))
        for (point in trail) {
            val life = (1f - (elapsed - point.time) / TRAIL_AGE).coerceIn(0f, 1f)
            trailPaint.color = Color.argb((life * 100).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle((point.x - camX) * camS, (point.y - camY) * camS - r * 0.8f, r * (0.25f + life * 0.65f), trailPaint)
        }
        ballPaint.shader = RadialGradient(bx, by, r * 2.2f,
            intArrayOf(Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, r * 2.2f, ballPaint)
        ballPaint.shader = RadialGradient(bx - r * 0.25f, by - r * 0.25f, r,
            intArrayOf(Color.WHITE, color), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, r, ballPaint)
    }

    private fun getTerrainColor(worldX: Float): Int = when (currentTheme) {
        ColorTheme.VIVID -> Color.HSVToColor(floatArrayOf((worldX / 28000f * 360f + 36000f) % 360f, 0.82f, 0.74f))
        ColorTheme.PASTEL -> Color.HSVToColor(floatArrayOf((worldX / 28000f * 360f + 36000f) % 360f, 0.28f, 0.97f))
        ColorTheme.WEATHERED -> Color.HSVToColor(floatArrayOf((worldX / 40000f * 360f + 36015f) % 360f, 0.4f, 0.5f))
        ColorTheme.GRAYSCALE -> {
            val wave = (sin(worldX / 7000f) * 0.55f + sin(worldX / 2800f + 1.4f) * 0.45f + 1f) / 2f
            val gray = ((0.17f + 0.62f * wave).coerceIn(0.12f, 0.88f) * 255f).toInt()
            Color.rgb(gray, gray, gray)
        }
    }
}
