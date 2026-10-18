package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port fidèle de l'animation JS du lapin (updateHeaderRabbitAnimation).
 * S'active quand on clique, vitesse proportionnelle au rythme de clic.
 */
class RabbitSpriteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val WIDTH_TO_HEIGHT = 1.8f
        private const val FRAME_H = 16
        private const val FRAME_COUNT = 4
        private const val MIN_CPS = 2f
        private const val MAX_CPS = 20f
        private const val MAX_CLICK_GAP_MS = 500L
        private const val MIN_FRAME_MS = 67L   // ~15 fps au rythme max
        private const val MAX_FRAME_MS = 1000L // 1 fps au rythme min
        private const val CLICK_WINDOW_MS = 500L
        private const val DUST_LIFETIME_MS = 900L
        private const val DUST_INTERVAL_MS = 90L

        // Avancée vers le centre en mode 2 lignes
        private const val FAST_CPS_THRESHOLD = 10f      // seuil pour démarrer l'avancée
        private const val FAST_CPS_MAINTAIN  = 6f       // seuil pour maintenir (anti-lag)
        private const val MOVEMENT_DELAY_SECS = 2f      // délai avant de commencer à bouger
        private const val MOVEMENT_AMPLITUDE = 0.75f    // asymptote : 3/4 du chemin vers centre
        private const val MOVEMENT_K = 0.02475f         // ln(2)/28 : mi-chemin à 30s, 3/4 à ~10min
        private const val RETURN_DURATION_MS = 5000L    // durée du retour smooth
    }

    // Progression d'avancée [0.0 ; 0.5) : 0=origine, 0.5=centre écran (jamais atteint)
    var onProgressUpdate: ((Float) -> Unit)? = null
    private var sustainedFastStart = 0L
    private var isReturning = false
    private var returnStartProgress = 0f
    private var returnStartTime = 0L
    private var currentProgress = 0f

    private val paint = Paint().apply { isAntiAlias = false }
    private val rabbitArt = RabbitCoat.entries.random().animation()
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2DCD0.toInt() }
    private val curl = Path()
    private class Dust {
        var bornAt = -DUST_LIFETIME_MS
        var x = 0f
        var drift = 0f
        var rise = 0f
        var size = 0f
        var phase = 0f
    }
    private val dust = Array(40) { Dust() }
    private var nextDust = 0
    private var lastDustAt = -DUST_INTERVAL_MS
    private var bounce = 0f
    private var bounceVelocity = 0f
    private var lastEffectsAt = 0L

    private val clickHistory = ArrayDeque<Long>()
    private var frameIndex = 0
    private var lastFrameAt = 0L

    private val choreographer = Choreographer.getInstance()
    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Même horloge que registerClick() pour que les comparaisons soient cohérentes
            val now = SystemClock.uptimeMillis()
            updateFrame(now)
            updateEffects(now)
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    /** Appelé à chaque clic enregistré depuis l'activité. */
    fun registerClick() {
        val now = SystemClock.uptimeMillis()
        clickHistory.addLast(now)
        purgeOldClicks(now)
        bounceVelocity = (bounceVelocity + 5f).coerceAtMost(9f)
        emitDust(now)
    }

    private fun emitDust(now: Long) {
        if (now - lastDustAt < DUST_INTERVAL_MS) return
        lastDustAt = now
        repeat(if (currentProgress > 0f && !isReturning) 4 else 3) {
            val particle = dust[nextDust]
            nextDust = (nextDust + 1) % dust.size
            particle.bornAt = now
            particle.x = .04f + Random.nextFloat() * .08f
            particle.drift = .45f + Random.nextFloat() * .22f
            particle.rise = .22f + Random.nextFloat() * .22f
            particle.size = .07f + Random.nextFloat() * .04f
            particle.phase = Random.nextFloat() * 6.28f
        }
    }

    private fun updateEffects(now: Long) {
        val dt = if (lastEffectsAt == 0L) 0f else ((now - lastEffectsAt) / 1000f).coerceIn(0f, .05f)
        lastEffectsAt = now
        if (frameIndex != 0 && clickHistory.lastOrNull()?.let { now - it < MAX_CLICK_GAP_MS } == true) {
            emitDust(now)
        }
        // Damped spring: repeated taps add energy without restarting the jump abruptly.
        bounceVelocity += (-85f * bounce - 16f * bounceVelocity) * dt
        bounce = (bounce + bounceVelocity * dt).coerceAtMost(.8f)
        if (bounce < 0f) {
            bounce = 0f
            bounceVelocity = 0f
        }
        if (bounce < .001f && kotlin.math.abs(bounceVelocity) < .01f) {
            bounce = 0f
            bounceVelocity = 0f
        }
    }

    private fun drawDust(canvas: Canvas, now: Long) {
        val spriteSize = minOf(width, height).toFloat()
        val rabbitLeft = width - spriteSize
        val rabbitTop = (height - spriteSize) / 2f
        for (particle in dust) {
            val age = now - particle.bornAt
            if (age < 0L || age >= DUST_LIFETIME_MS) continue
            val progress = age.toFloat() / DUST_LIFETIME_MS
            val x = rabbitLeft + (particle.x - particle.drift * progress) * spriteSize
            val wave = sin(progress * 6.28f + particle.phase) * .045f * progress
            val y = rabbitTop + (.86f - particle.rise * progress + wave) * spriteSize
            val radius = particle.size * spriteSize * (.65f + 1.5f * progress)
            val fade = 1f - progress
            dustPaint.style = Paint.Style.FILL
            dustPaint.alpha = (fade * fade * 135).toInt()
            canvas.drawCircle(x, y, radius, dustPaint)
            // A bright open curl remains legible against the clicker's black background.
            curl.reset()
            curl.moveTo(x - radius, y)
            curl.cubicTo(x - radius, y - radius, x + radius, y - radius, x + radius, y)
            curl.cubicTo(x + radius, y + radius * .7f, x, y + radius * .7f, x, y)
            dustPaint.style = Paint.Style.STROKE
            dustPaint.strokeWidth = (spriteSize * .025f).coerceAtLeast(1f)
            dustPaint.strokeCap = Paint.Cap.ROUND
            dustPaint.alpha = (fade * 190).toInt()
            canvas.drawPath(curl, dustPaint)
        }
    }

    private fun purgeOldClicks(now: Long) {
        while (clickHistory.isNotEmpty() && now - clickHistory.first() > CLICK_WINDOW_MS) {
            clickHistory.removeFirst()
        }
    }

    private fun resetToIdle() {
        frameIndex = 0
        lastFrameAt = 0L
    }

    private fun updateFrame(now: Long) {
        purgeOldClicks(now)

        val count = clickHistory.size
        val timeSinceLastClick = if (count > 0) now - clickHistory.last() else Long.MAX_VALUE
        val lastGap = if (count >= 2) clickHistory.last() - clickHistory[count - 2] else Long.MAX_VALUE
        val rate = if (count >= 2) count / (CLICK_WINDOW_MS / 1000f) else 0f

        val animActive = count >= 2 &&
                         timeSinceLastClick <= MAX_CLICK_GAP_MS &&
                         lastGap <= MAX_CLICK_GAP_MS &&
                         rate >= MIN_CPS

        // Animation sprite
        if (!animActive) {
            resetToIdle()
        } else {
            val clampedRate = rate.coerceIn(MIN_CPS, MAX_CPS)
            val t = (clampedRate - MIN_CPS) / (MAX_CPS - MIN_CPS)
            val frameDurationMs = (MAX_FRAME_MS - (MAX_FRAME_MS - MIN_FRAME_MS) * t).toLong()
            if (lastFrameAt == 0L) {
                lastFrameAt = now
            } else if (now - lastFrameAt >= frameDurationMs) {
                frameIndex = if (frameIndex == 0) 1
                             else { val next = frameIndex + 1; if (next >= FRAME_COUNT) 1 else next }
                lastFrameAt = now
            }
        }

        // Avancée vers le centre (mode 2 lignes)
        // Hysteresis : 10 CPS pour démarrer, 6 CPS pour maintenir (évite les micro-lags)
        val cpsThreshold = if (sustainedFastStart != 0L) FAST_CPS_MAINTAIN else FAST_CPS_THRESHOLD
        val isFast = timeSinceLastClick <= MAX_CLICK_GAP_MS && rate >= cpsThreshold

        if (isFast) {
            if (isReturning) {
                // Reprendre depuis la position actuelle sans sauter
                isReturning = false
                val equiv = if (currentProgress > 0f && currentProgress < MOVEMENT_AMPLITUDE)
                    -ln(1f - currentProgress / MOVEMENT_AMPLITUDE) / MOVEMENT_K
                else 0f
                sustainedFastStart = now - (equiv * 1000f).toLong()
            } else if (sustainedFastStart == 0L) {
                sustainedFastStart = now
            }
            val sustainedSecs = (now - sustainedFastStart) / 1000f
            val shifted = (sustainedSecs - MOVEMENT_DELAY_SECS).coerceAtLeast(0f)
            currentProgress = MOVEMENT_AMPLITUDE * (1f - exp(-MOVEMENT_K * shifted))
        } else {
            sustainedFastStart = 0L
            if (currentProgress > 0f && !isReturning) {
                isReturning = true
                returnStartProgress = currentProgress
                returnStartTime = now
            }
            if (isReturning) {
                val elapsed = (now - returnStartTime).toFloat()
                val fraction = (elapsed / RETURN_DURATION_MS).coerceIn(0f, 1f)
                currentProgress = returnStartProgress * (1f - fraction)
                if (fraction >= 1f) {
                    currentProgress = 0f
                    isReturning = false
                }
            }
        }

        onProgressUpdate?.invoke(currentProgress)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastEffectsAt = 0L
        choreographer.postFrameCallback(tick)
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(tick)
        bounce = 0f
        bounceVelocity = 0f
        dust.forEach { it.bornAt = -DUST_LIFETIME_MS }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        drawDust(canvas, SystemClock.uptimeMillis())
        val saved = canvas.save()
        val spriteSize = minOf(width, height)
        canvas.translate((width - spriteSize).toFloat(), (height - spriteSize) / 2f)
        // Keep the ears inside the view even at the highest point of the jump.
        canvas.translate(0f, -spriteSize * .09f * bounce)
        val scale = 1f - .12f * bounce
        canvas.scale(scale, scale, spriteSize / 2f, spriteSize.toFloat())
        rabbitArt.draw(canvas, paint, frameIndex, spriteSize, spriteSize)
        canvas.restoreToCount(saved)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(
            (FRAME_H * resources.displayMetrics.density * 2).toInt()
        )
        val w = (h * WIDTH_TO_HEIGHT).toInt() // sprite carré + place pour la fumée derrière lui
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }
}
