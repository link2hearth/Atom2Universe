package com.Atom2Universe.app.science.cosmicscale

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Vue GL : auto-rotation des astres, swipe horizontal pour parcourir l'échelle.
 * Swipe vers la gauche → astres plus grands ; vers la droite → plus petits.
 */
class CosmicScaleGLView(context: Context) : GLSurfaceView(context) {

    val renderer = CosmicScaleRenderer(context)

    /** Notifie l'Activity d'un swipe : +1 = avancer (plus grand), -1 = reculer (plus petit). */
    var onSwipe: ((Int) -> Unit)? = null

    private val detector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                // Seuil en dp/s : en px/s, le geste demandé variait avec la densité de l'écran.
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 400f * resources.displayMetrics.density) {
                    onSwipe?.invoke(if (velocityX < 0) +1 else -1)
                    return true
                }
                return false
            }
        }
    )

    private val pacer = com.Atom2Universe.app.science.GLFramePacer(this)

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        // Le renderer réclame lui-même ses images : 60 Hz pour une planète qui tourne,
        // 30 Hz pour une étoile seule, rien pour un trou noir.
        renderMode = RENDERMODE_WHEN_DIRTY
        renderer.pacer = pacer
    }

    /** Fil UI : les astres ont changé, redessiner. */
    fun requestFrame() = pacer.requestFrame()

    override fun onResume() {
        super.onResume()
        pacer.resume()
    }

    override fun onPause() {
        pacer.pause()
        super.onPause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return true
    }
}
