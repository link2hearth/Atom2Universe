package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.*

class AtomSpringView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val normalAtomFiles = listOf(
        "Atom.png", "Atom0.png", "Atom1.png", "Atom2.png", "Atom3.png",
        "Atom4.png", "Atom5.png", "Atom6.png", "Atom7.png", "Atom8.png",
        "Atom9.png", "Atom10.png", "Atom11.png", "Atom12.png"
    )

    private val nsfwAtomFiles = (1..41).map { "nsfw ($it).png" }

    private var isNsfwMode = false
    private val activeFiles get() = if (isNsfwMode) nsfwAtomFiles else normalAtomFiles

    private var currentIndex = 0
    private var currentBitmap: Bitmap? = null

    private fun loadBitmap(index: Int): Bitmap? = try {
        val folder = if (isNsfwMode) "Assets/Image/Atom2" else "Assets/Image/Atom low"
        context.assets.open("$folder/${activeFiles[index]}").use {
            BitmapFactory.decodeStream(it)
        }
    } catch (_: Exception) { null }

    fun setNsfwMode(enabled: Boolean) {
        isNsfwMode = enabled
        currentIndex = currentIndex.coerceIn(0, activeFiles.lastIndex)
        currentBitmap = loadBitmap(currentIndex)
        invalidate()
    }

    fun setAtomIndex(index: Int) {
        val start = index.coerceIn(0, activeFiles.lastIndex)
        var i = start
        var bmp = loadBitmap(i)
        while (bmp == null && i < activeFiles.lastIndex) {
            i++
            bmp = loadBitmap(i)
        }
        if (bmp == null) {
            i = 0
            bmp = loadBitmap(i)
        }
        currentIndex = i
        currentBitmap = bmp
        invalidate()
    }

    /** Passe au variant suivant, retourne le nouvel index pour le persister. */
    fun cycleToNext(): Int {
        val next = (currentIndex + 1) % activeFiles.size
        setAtomIndex(next)
        return next
    }

    // ── État du ressort ───────────────────────────────────────────────────────
    private var posX = 0f
    private var posY = 0f
    private var velX = 0f
    private var velY = 0f
    private var tilt = 0f
    private var tiltVel = 0f
    private var squash = 0f
    private var squashVel = 0f
    private var intensity = 0f
    private var noisePhase = (Math.random() * PI * 2).toFloat()
    private var noiseOffset = (Math.random() * PI * 2).toFloat()
    private var spinPhase = (Math.random() * PI * 2).toFloat()
    private var impulseTimer = 0f
    private var lastInputIntensity = 0f
    private var lastFrameNs = 0L

    // ── Historique de clics ───────────────────────────────────────────────────
    private val clickHistory = ArrayDeque<Long>()
    private var targetStrength = 0f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    private val choreographer = Choreographer.getInstance()
    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val delta = if (lastFrameNs == 0L) 0f
                        else ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNs = frameTimeNanos
            updateClickStrength()
            updateSpring(delta, targetStrength)
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    fun registerClick() {
        val now = System.currentTimeMillis()
        clickHistory.addLast(now)
        purgeOldClicks(now)
        injectImpulse()
    }

    private fun purgeOldClicks(now: Long) {
        while (clickHistory.isNotEmpty() && now - clickHistory.first() > 500L)
            clickHistory.removeFirst()
    }

    private fun updateClickStrength() {
        val now = System.currentTimeMillis()
        purgeOldClicks(now)
        val count = clickHistory.size
        if (count < 2) { targetStrength = 0f; return }
        val rate = count / 0.5f
        val normalized = (rate / 20f).coerceIn(0f, 1f)
        val curved = 1f - exp(-normalized * 3.8f)
        targetStrength = curved.coerceIn(0f, 1f)
    }

    private val motionFactor get() = if (isNsfwMode || currentIndex == normalAtomFiles.lastIndex) 1f / 3f else 1f

    private fun injectImpulse() {
        val f = motionFactor
        val energy = intensity.pow(0.6f)
        val angle = (Math.random() * PI * 2).toFloat()
        val strength = (180f + energy * 520f) * f
        velX += cos(angle) * strength
        velY += sin(angle) * strength
        velX -= posX * (20f + energy * 60f) * f
        velY -= posY * (20f + energy * 60f) * 1.05f * f
        tiltVel += (Math.random().toFloat() - 0.5f) * (220f + energy * 320f) * f
        squashVel += (Math.random().toFloat() - 0.35f) * (160f + energy * 220f) * f
        spinPhase += (Math.random().toFloat() - 0.5f) * (0.45f + energy * 1.2f) * f
        intensity = (intensity + (0.25f + targetStrength * 0.4f) * f).coerceAtMost(1f)
        impulseTimer = impulseTimer.coerceAtMost(0.08f)
    }

    private fun updateSpring(delta: Float, drive: Float) {
        if (delta <= 0f) return
        val f = motionFactor

        intensity += ((drive - intensity) * delta * 9f).coerceIn(-1f, 1f)
        val energy = intensity.pow(0.65f)

        val rangeX = 6f + energy * 34f + intensity * 6f
        val rangeY = 7f + energy * 40f + intensity * 8f
        val centerPull = 10f + energy * 24f
        val damping = 4.8f + energy * 16f
        val maxSpeed = 120f + energy * 420f

        // Dérive de bruit sinusoïdal (flottement)
        noisePhase += delta * (1.2f + energy * 6.4f)
        val noiseX = sin(noisePhase * 1.35f + noiseOffset) * (0.34f + energy * 0.9f) +
                     cos(noisePhase * 2.35f + noiseOffset * 1.7f) * (0.22f + energy * 0.55f)
        val noiseY = cos(noisePhase * 1.55f + noiseOffset * 0.4f) * (0.32f + energy * 0.82f) +
                     sin(noisePhase * 2.1f + noiseOffset) * (0.2f + energy * 0.5f)
        val noiseStr = (12f + energy * 210f + intensity * 30f) * f
        velX += noiseX * noiseStr * delta
        velY += noiseY * noiseStr * delta

        // Rappel vers le centre + amortissement
        velX -= (posX / rangeX.coerceAtLeast(1f)) * centerPull * delta
        velY -= (posY / rangeY.coerceAtLeast(1f)) * centerPull * delta
        velX -= velX * damping * delta
        velY -= velY * damping * delta

        // Impulsion périodique automatique
        impulseTimer -= delta
        if (impulseTimer <= 0f) {
            val burstAngle = (Math.random() * PI * 2).toFloat()
            val burstStr = (16f + energy * 240f + intensity * 120f) * f
            velX += cos(burstAngle) * burstStr
            velY += sin(burstAngle) * burstStr
            impulseTimer = (0.42f - energy * 0.32f - intensity * 0.1f).coerceAtLeast(0.085f)
        }

        // Coup sur gain d'intensité
        val gain = (drive - lastInputIntensity).coerceAtLeast(0f)
        if (gain > 0.001f) {
            val gainAngle = (Math.random() * PI * 2).toFloat()
            val gainStr = (38f + gain * 480f + intensity * 140f) * f
            velX += cos(gainAngle) * gainStr
            velY += sin(gainAngle) * gainStr
        }
        lastInputIntensity = drive

        posX += velX * delta
        posY += velY * delta

        // Rebond sur les bords
        val restitution = 0.46f + energy * 0.42f
        if (posX > rangeX) { posX = rangeX; velX = -abs(velX) * restitution }
        else if (posX < -rangeX) { posX = -rangeX; velX = abs(velX) * restitution }
        if (posY > rangeY) { posY = rangeY; velY = -abs(velY) * restitution }
        else if (posY < -rangeY) { posY = -rangeY; velY = abs(velY) * restitution }

        val speed = hypot(velX, velY)
        if (speed > maxSpeed) { val s = maxSpeed / speed; velX *= s; velY *= s }

        // Rotation de spin
        spinPhase += delta * (2.4f + energy * 14.5f) * f
        if (spinPhase > PI.toFloat() * 100f) spinPhase -= PI.toFloat() * 2f

        // Inclinaison (tilt)
        val tiltTarget = (posX / rangeX.coerceAtLeast(1f)) * (10f + energy * 18f) +
                         (velX / maxSpeed.coerceAtLeast(1f)) * (34f + energy * 30f)
        tiltVel += (tiltTarget - tilt) * (24f + energy * 32f) * delta
        tiltVel -= tiltVel * (7f + energy * 14f) * delta
        tilt += tiltVel * delta

        // Squash & stretch
        val squashTarget = (-(velY / maxSpeed.coerceAtLeast(1f)) * (1.6f + energy * 1.25f)).coerceIn(-1f, 1f)
        squashVel += (squashTarget - squash) * (22f + energy * 28f) * delta
        squashVel -= squashVel * (9f + energy * 12f) * delta
        squash += squashVel * delta

        // Assainissement
        if (!posX.isFinite()) posX = 0f
        if (!posY.isFinite()) posY = 0f
        if (!velX.isFinite()) velX = 0f
        if (!velY.isFinite()) velY = 0f
        if (!tilt.isFinite()) tilt = 0f
        if (!squash.isFinite()) squash = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.postFrameCallback(tick)
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = currentBitmap ?: return
        val cx = width / 2f
        val cy = height / 2f
        // Taille fixe de l'atome : 160dp, indépendant des dimensions de la vue
        val dp = resources.displayMetrics.density
        val atomSize = 160f * dp
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val sizeMultiplier = if (isNsfwMode || currentIndex == normalAtomFiles.lastIndex) 5f else 1f
        val baseScale = atomSize / maxOf(bmpW, bmpH) * sizeMultiplier
        val energy = intensity.pow(0.65f)
        // Rotation légère, pas de squash/stretch pour éviter la distorsion
        val f = motionFactor
        val spin = sin(spinPhase) * (2f + energy * 8f) * f

        matrix.reset()
        matrix.postTranslate(-bmpW / 2f, -bmpH / 2f)
        matrix.postScale(baseScale, baseScale)
        matrix.postRotate((tilt * 0.5f + spin) * f)
        matrix.postTranslate(cx + posX * dp * f, cy + posY * dp * f)

        canvas.drawBitmap(bmp, matrix, paint)
    }
}
