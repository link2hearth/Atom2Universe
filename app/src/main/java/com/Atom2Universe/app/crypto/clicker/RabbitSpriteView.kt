package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.exp
import kotlin.math.ln

/**
 * Port fidèle de l'animation JS du lapin (updateHeaderRabbitAnimation).
 * S'active quand on clique, vitesse proportionnelle au rythme de clic.
 */
class RabbitSpriteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val FRAME_W = 16
        private const val FRAME_H = 16
        private const val FRAME_COUNT = 4
        private const val MIN_CPS = 2f
        private const val MAX_CPS = 20f
        private const val MAX_CLICK_GAP_MS = 500L
        private const val MIN_FRAME_MS = 67L   // ~15 fps au rythme max
        private const val MAX_FRAME_MS = 1000L // 1 fps au rythme min
        private const val CLICK_WINDOW_MS = 500L

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

    private val bitmap: Bitmap? = try {
        context.assets.open("Assets/sprites/Lapin.png").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    private val clickHistory = ArrayDeque<Long>()
    private var frameIndex = 0
    private var lastFrameAt = 0L

    private val choreographer = Choreographer.getInstance()
    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Même horloge que registerClick() pour que les comparaisons soient cohérentes
            updateFrame(System.currentTimeMillis())
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    /** Appelé à chaque clic enregistré depuis l'activité. */
    fun registerClick() {
        val now = System.currentTimeMillis()
        clickHistory.addLast(now)
        purgeOldClicks(now)
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
        choreographer.postFrameCallback(tick)
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        src.set(frameIndex * FRAME_W, 0, (frameIndex + 1) * FRAME_W, FRAME_H)
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(
            (FRAME_H * resources.displayMetrics.density * 2).toInt()
        )
        val w = h // sprite carré 16×16
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }
}
