package com.Atom2Universe.app.science

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * Cadence d'une GLSurfaceView en RENDERMODE_WHEN_DIRTY : une image quand quelque chose change
 * (geste, réglage), et tant que le rendu en réclame une autre, des images plafonnées.
 * L'écran de la tablette tourne à 120 Hz : sans plafond, une scène lente se payait deux fois.
 * Le plafond tient à 60, 90, 120 et 144 Hz.
 * Rien n'est demandé entre [pause] et [resume].
 */
internal class GLFramePacer(private val view: GLSurfaceView) : Choreographer.FrameCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var posted = false
    private var pendingHz = 0
    /** Échéance de la prochaine image, cumulée : la moyenne tient [pendingHz] quel que soit l'écran. */
    private var deadlineNanos = 0L
    @Volatile private var continueHz = 0
    private val continueRunnable = Runnable { requestFrame(continueHz) }

    /** Fil UI : demande une image, servie au plus tôt selon [hz] (images par seconde au plus). */
    fun requestFrame(hz: Int = INTERACTIVE_HZ) {
        if (!running || hz <= 0) return
        if (hz > pendingHz) pendingHz = hz
        if (!posted) {
            posted = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Fil GL, à la fin d'une image : la suivante est attendue à [hz] (0 = plus rien ne bouge). */
    fun frameDone(hz: Int) {
        if (hz <= 0) return
        continueHz = hz
        mainHandler.post(continueRunnable)
    }

    override fun doFrame(frameTimeNanos: Long) {
        posted = false
        if (!running || pendingHz <= 0) return
        val period = 1_000_000_000L / pendingHz
        // Une demi-période d'écran de tolérance absorbe la gigue des vsync ; l'échéance avance
        // d'une période à chaque image servie, donc 60 i/s à 90 Hz = deux vsync sur trois,
        // à 144 Hz = alternance 2-3, et pas de rafale de rattrapage après une pause.
        val refreshHz = view.display?.refreshRate?.takeIf { it >= 1f } ?: 60f
        val tolerance = (500_000_000.0 / refreshHz).toLong()
        if (frameTimeNanos >= deadlineNanos - tolerance) {
            deadlineNanos = (if (frameTimeNanos - deadlineNanos > period) frameTimeNanos else deadlineNanos) + period
            pendingHz = 0
            view.requestRender()
        } else {
            posted = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun resume() {
        running = true
        requestFrame()
    }

    fun pause() {
        running = false
        pendingHz = 0
        if (posted) {
            Choreographer.getInstance().removeFrameCallback(this)
            posted = false
        }
        mainHandler.removeCallbacks(continueRunnable)
    }

    companion object {
        const val INTERACTIVE_HZ = 60
        /** Seul un lent bruit de surface bouge : la moitié suffit. */
        const val SLOW_HZ = 30
    }
}
