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
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 400f) {
                    onSwipe?.invoke(if (velocityX < 0) +1 else -1)
                    return true
                }
                return false
            }
        }
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return true
    }
}
