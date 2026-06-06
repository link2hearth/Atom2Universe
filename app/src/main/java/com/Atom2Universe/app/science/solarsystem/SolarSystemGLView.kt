package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.sqrt

class SolarSystemGLView(context: Context) : GLSurfaceView(context) {

    val renderer = SolarSystemRenderer(context)

    // Gestes
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var prevX = 0f; private var prevY = 0f
    private var pointerCount = 0
    private var prevMidX = 0f; private var prevMidY = 0f
    private var tapStartX = 0f; private var tapStartY = 0f
    private var longPressHandled = false

    private val longPressDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressHandled = true
                handleLongPress(e.x, e.y)
            }
        }
    )

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
                longPressHandled = false
                pointerCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                prevMidX = (event.getX(0) + event.getX(1)) / 2f
                prevMidY = (event.getY(0) + event.getY(1)) / 2f
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (pointerCount >= 2 && event.pointerCount >= 2) {
                    // Two-finger pan
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    val dx = (midX - prevMidX) * 0.015f * renderer.cameraDistance / 20f
                    val dy = (midY - prevMidY) * 0.015f * renderer.cameraDistance / 20f
                    renderer.panX -= dx
                    renderer.panY += dy
                    prevMidX = midX; prevMidY = midY
                } else if (pointerCount == 1 && event.pointerCount == 1) {
                    // Single-finger rotate
                    val dx = event.x - prevX
                    val dy = event.y - prevY
                    renderer.cameraYaw -= dx * 0.4f
                    renderer.cameraPitch = (renderer.cameraPitch + dy * 0.4f).coerceIn(5f, 88f)
                    prevX = event.x; prevY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pointerCount = event.pointerCount - 1
                if (event.pointerCount > 1) {
                    val remainIdx = if (event.actionIndex == 0) 1 else 0
                    prevX = event.getX(remainIdx); prevY = event.getY(remainIdx)
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
        val idx = nearestBody(x, y, threshold = 80f)
        if (idx >= -1) renderer.onPlanetTapped?.invoke(idx)
    }

    private fun handleLongPress(x: Float, y: Float) {
        val idx = nearestBody(x, y, threshold = 120f)
        val target = if (idx >= -1) idx else -1  // -1 par défaut = retour Soleil
        renderer.focusPlanetIdx = target
        renderer.panX = 0f; renderer.panY = 0f
        renderer.cameraDistance = renderer.recommendedDistance(target)
        renderer.onPlanetLongPressed?.invoke(target)
    }

    private fun nearestBody(x: Float, y: Float, threshold: Float): Int {
        var bestDist = Float.MAX_VALUE
        var bestIdx = -2

        val sunScreen = renderer.projectToScreen(0f, 0f, 0f)
        val dSun = dist(x, y, sunScreen[0], sunScreen[1])
        if (dSun < threshold && dSun < bestDist) { bestDist = dSun; bestIdx = -1 }

        SolarSystemData.planets.forEach { planet ->
            val wp = renderer.planetWorldPos[planet.id]
            val screen = renderer.projectToScreen(wp[0], wp[1], wp[2])
            val d = dist(x, y, screen[0], screen[1])
            if (d < threshold && d < bestDist) { bestDist = d; bestIdx = planet.id }
        }
        return bestIdx
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.cameraDistance = (renderer.cameraDistance / detector.scaleFactor)
                .coerceIn(1.5f, 600f)
            return true
        }
    }
}
