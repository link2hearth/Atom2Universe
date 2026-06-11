package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.sqrt

class EarthMoonGLView(context: Context) : GLSurfaceView(context) {

    val renderer = EarthMoonRenderer(context)

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var prevX = 0f; private var prevY = 0f
    private var pointerCount = 0
    private var tapStartX = 0f; private var tapStartY = 0f
    private var longPressHandled = false

    private val longPressDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressHandled = true
                handleLongPress(e.x, e.y)
            }
        })

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        longPressDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                prevX = event.x; prevY = event.y
                tapStartX = event.x; tapStartY = event.y
                longPressHandled = false; pointerCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> pointerCount = event.pointerCount
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (pointerCount == 1 && event.pointerCount == 1) {
                    renderer.cameraYaw   -= (event.x - prevX) * 0.4f
                    renderer.cameraPitch  = (renderer.cameraPitch + (event.y - prevY) * 0.4f).coerceIn(-88f, 88f)
                    prevX = event.x; prevY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pointerCount = event.pointerCount - 1
                if (event.pointerCount > 1) {
                    val r = if (event.actionIndex == 0) 1 else 0
                    prevX = event.getX(r); prevY = event.getY(r)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!longPressHandled) {
                    val dx = event.x - tapStartX; val dy = event.y - tapStartY
                    if (sqrt(dx * dx + dy * dy) < 20f) handleTap(event.x, event.y)
                }
                pointerCount = 0
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val idx = nearestBody(x, y, 80f)
        if (idx >= 0) renderer.onBodyTapped?.invoke(idx)
    }

    private fun handleLongPress(x: Float, y: Float) {
        val idx = nearestBody(x, y, 120f).coerceAtLeast(0)
        renderer.focusBody = idx
        renderer.cameraDistance = renderer.recommendedDistance(idx)
        renderer.onBodyTapped?.invoke(idx)
    }

    // 0=Terre, 1=Lune
    private fun nearestBody(x: Float, y: Float, threshold: Float): Int {
        val earthScreen = renderer.projectToScreen(0f, 0f, 0f)
        val moonScreen  = renderer.projectToScreen(
            renderer.moonWorldPos[0], renderer.moonWorldPos[1], renderer.moonWorldPos[2])
        val dE = dist(x, y, earthScreen[0], earthScreen[1])
        val dM = dist(x, y, moonScreen[0],  moonScreen[1])
        return when {
            dE < threshold && dE <= dM -> 0
            dM < threshold             -> 1
            else                       -> -1
        }
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer.cameraDistance = (renderer.cameraDistance / d.scaleFactor)
                .coerceIn(renderer.minZoomDistance, 100f)
            return true
        }
    }
}
