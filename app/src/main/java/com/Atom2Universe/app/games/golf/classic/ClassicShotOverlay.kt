package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.Atom2Universe.app.games.golf.classic.core.GolfSwing
import kotlin.math.*

/**
 * Whole-scene gesture layer. A sideways swipe turns the aim, a pinch zooms, and drawing a finger
 * downwards from anywhere loads the shot: the pull is the power, and the release takes the needle.
 * In the aerial view two fingers move, zoom and turn the camera; one finger still fine-tunes the aim.
 */
internal class ClassicShotOverlay(context: Context, private val swing: GolfSwing, private val listener: Listener) : View(context) {
    interface Listener {
        fun canShoot(): Boolean
        fun aim(radians: Float)
        fun zoom(factor: Float)
        fun pull(fraction: Float)
        fun release(fraction: Float, needle: Float)
        fun cancel()
        /** Horizontal field of view of the scene, so that the aim follows the finger one to one. */
        fun fieldOfView(): Float
        /** True while the free aerial camera is shown. */
        fun overview(): Boolean
        /** One finger on the aerial view, in pixels: the landing point follows it. */
        fun aimOnMap(dx: Float, dy: Float)
        fun pan(dx: Float, dy: Float)
        fun rotate(radians: Float)
        fun manualFlightCamera(): Boolean
        fun cameraRotate(dx: Float, dy: Float)
        fun cameraMove(dx: Float, dy: Float, zoom: Float)
        fun cameraDoubleTap()
    }

    private enum class Mode { NONE, PENDING, AIM, PULL, PINCH }
    private var mode = Mode.NONE
    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var twist = 0f
    private var middleX = 0f; private var middleY = 0f
    private var fingerX = 0f; private var fingerY = 0f
    /** Distance between the two fingers; zero until the pair is anchored again. */
    private var pinch = 0f
    private var cameraGesture=false
    private var cameraFingers=0
    private val touchConfig=ViewConfiguration.get(context)
    private var cameraTap=false
    private var cameraTapX=0f
    private var cameraTapY=0f
    private var previousTapTime=0L
    private var previousTapX=0f
    private var previousTapY=0f
    private var secondCameraTap=false
    var pullFraction = 0f
        private set
    val pulling get() = mode == Mode.PULL
    /** A finger rests on the scene: while the ball travels, this fast-forwards it. */
    val holding get() = mode != Mode.NONE && !cameraGesture
    var readout = ""
    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
    private val bounds = RectF()

    private fun span() = min(height * .36f, 280f * density).coerceAtLeast(80f * density)
    private fun spread(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun angle(e: MotionEvent) = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN)cameraGesture=listener.manualFlightCamera()
        // Keep ownership until release, even if the ball stops under the finger.
        if(cameraGesture) {
            cameraTouch(e)
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.PENDING
                startX = e.x; startY = e.y; lastX = e.x; lastY = e.y; fingerX = e.x; fingerY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.PULL) abandon()
                mode = Mode.PINCH; pinch = 0f
            }
            MotionEvent.ACTION_POINTER_UP -> pinch = 0f
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.PINCH -> if (e.pointerCount >= 2) twoFingers(e)
                Mode.PENDING -> {
                    val dx = e.x - startX; val dy = e.y - startY
                    if (hypot(dx, dy) > 10f * density) {
                        if (!listener.overview() && dy > abs(dx) && listener.canShoot()) {
                            mode = Mode.PULL
                            swing.start()
                            follow(e)
                        } else {
                            mode = Mode.AIM
                            turn(e)
                        }
                    }
                }
                Mode.AIM -> turn(e)
                Mode.PULL -> follow(e)
                Mode.NONE -> Unit
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.PULL) {
                    val needle = swing.release()
                    val fraction = pullFraction
                    pullFraction = 0f
                    if (fraction >= MIN_PULL) listener.release(fraction, needle) else listener.cancel()
                }
                mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> { if (mode == Mode.PULL) abandon(); mode = Mode.NONE }
        }
        invalidate()
        return true
    }

    private fun cameraTouch(e:MotionEvent) {
        val count=e.pointerCount
        val x=if(count>=2)(e.getX(0)+e.getX(1))*.5f else e.x
        val y=if(count>=2)(e.getY(0)+e.getY(1))*.5f else e.y
        val d=if(count>=2)spread(e) else 0f
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cameraTap=true;cameraTapX=x;cameraTapY=y
                secondCameraTap=previousTapTime!=0L &&
                    e.eventTime-previousTapTime in 0..ViewConfiguration.getDoubleTapTimeout().toLong() &&
                    hypot(x-previousTapX,y-previousTapY)<=touchConfig.scaledDoubleTapSlop
                previousTapTime=0L
            }
            MotionEvent.ACTION_MOVE -> if(hypot(x-cameraTapX,y-cameraTapY)>touchConfig.scaledTouchSlop)resetCameraTaps()
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> resetCameraTaps()
            MotionEvent.ACTION_UP -> {
                if(cameraTap && e.eventTime-e.downTime<=ViewConfiguration.getDoubleTapTimeout() && listener.manualFlightCamera()) {
                    if(secondCameraTap){listener.cameraDoubleTap();resetCameraTaps()}
                    else {previousTapTime=e.eventTime;previousTapX=cameraTapX;previousTapY=cameraTapY}
                } else resetCameraTaps()
                cameraTap=false;secondCameraTap=false
            }
        }
        if(e.actionMasked==MotionEvent.ACTION_MOVE && cameraFingers==count && listener.manualFlightCamera()) {
            if(count==1)listener.cameraRotate(x-lastX,y-lastY)
            else if(pinch>0f && d>0f)listener.cameraMove(x-lastX,y-lastY,pinch/d)
        }
        lastX=x;lastY=y;pinch=d
        cameraFingers=if(e.actionMasked==MotionEvent.ACTION_POINTER_UP)0 else count
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            cameraGesture=false;cameraFingers=0;pinch=0f;mode=Mode.NONE
        }
    }

    /** A new shot, a camera change or a suspend cannot complete an older double tap. */
    fun resetCameraTaps() { cameraTap=false;secondCameraTap=false;previousTapTime=0L }

    /** Pinch zooms everywhere; in the aerial view the pair also drags and turns the camera. */
    private fun twoFingers(e: MotionEvent) {
        val d = spread(e)
        val a = angle(e)
        val mx = (e.getX(0) + e.getX(1)) * .5f; val my = (e.getY(0) + e.getY(1)) * .5f
        if (pinch > 0f && d > 0f) {
            listener.zoom(pinch / d)
            if (listener.overview()) {
                listener.pan(mx - middleX, my - middleY)
                var turn = a - twist
                if (turn > PI) turn -= 2f * PI.toFloat() else if (turn < -PI) turn += 2f * PI.toFloat()
                listener.rotate(turn)
            }
        }
        pinch = d; twist = a; middleX = mx; middleY = my
    }

    private fun turn(e: MotionEvent) {
        if (listener.overview()) listener.aimOnMap(e.x - lastX, e.y - lastY)
        else if (width > 0) listener.aim(-(e.x - lastX) / width * listener.fieldOfView())
        lastX = e.x; lastY = e.y
    }

    private fun follow(e: MotionEvent) {
        fingerX = e.x; fingerY = e.y
        pullFraction = ((e.y - startY) / span()).coerceIn(0f, 1f)
        listener.pull(pullFraction)
    }

    private fun abandon() {
        swing.cancel(); pullFraction = 0f; listener.cancel()
    }

    override fun onDraw(c: Canvas) {
        if (mode != Mode.PULL) return
        val d = density
        val radius = (if (swing.easy) 84f else 64f) * d
        val ax = startX.coerceIn(radius + 12f * d, width - radius - 12f * d)
        val ay = startY.coerceIn(radius + 52f * d, height - 12f * d)
        // Pull cord from the anchor to the finger.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3f * d; p.pathEffect = dash
        p.color = 0xB4FFFFFF.toInt()
        c.drawLine(ax, ay, fingerX, fingerY, p)
        p.pathEffect = null
        c.drawCircle(ax, ay, 7f * d, p)
        // Accuracy arc: dark track, red ends, green band and the gold centre.
        bounds.set(ax - radius, ay - radius, ax + radius, ay + radius)
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = 15f * d
        fun band(from: Float, to: Float, colour: Int) {
            p.color = colour
            c.drawArc(bounds, 270f + from * SWEEP, (to - from) * SWEEP, false, p)
        }
        band(-1f, 1f, 0xC8142D38.toInt())
        band(-1f, -swing.miss, 0xFFD9574A.toInt())
        band(swing.miss, 1f, 0xFFD9574A.toInt())
        band(-swing.good, swing.good, 0xFF6FBF73.toInt())
        band(-swing.perfect, swing.perfect, 0xFFFFD25C.toInt())
        // Needle.
        val angle = Math.toRadians((270f + swing.position * SWEEP).toDouble()).toFloat()
        val tipX = ax + cos(angle) * (radius + 11f * d)
        val tipY = ay + sin(angle) * (radius + 11f * d)
        p.color = Color.WHITE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3.5f * d
        c.drawLine(ax + cos(angle) * radius * .45f, ay + sin(angle) * radius * .45f, tipX, tipY, p)
        p.style = Paint.Style.FILL
        c.drawCircle(tipX, tipY, 4.5f * d, p)
        // Power readout above the arc.
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = 18f * resources.displayMetrics.scaledDensity
        p.setShadowLayer(4f * d, 0f, d, 0xAA000000.toInt())
        c.drawText(readout, ax, ay - radius - 22f * d, p)
        p.clearShadowLayer()
        p.typeface = Typeface.DEFAULT
    }

    companion object {
        private const val SWEEP = 80f
        private const val MIN_PULL = .02f
    }
}

/** The ball seen from behind: touch where the club meets it. Top runs, bottom bites, sides curve. */
internal class ClassicSpinPad(context: Context, description: String, private val onChange: (Float, Float) -> Unit) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    // Redrawn only on a real change: the HUD refreshes these several times a second.
    var spinX = 0f
        set(value) { if (field != value) { field = value; invalidate() } }
    var spinY = 0f
        set(value) { if (field != value) { field = value; invalidate() } }

    init { contentDescription = description; isClickable = true; isFocusable = true }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val r = min(width, height) * .4f
                var x = (e.x - width / 2f) / r
                var y = -(e.y - height / 2f) / r
                val length = hypot(x, y)
                if (length > 1f) { x /= length; y /= length }
                if (length < .18f) { x = 0f; y = 0f }
                onChange(x, y)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) * .4f
        val alpha = if (isEnabled) 255 else 90
        p.style = Paint.Style.FILL
        p.color = 0xFFF6F3E8.toInt(); p.alpha = alpha
        c.drawCircle(cx, cy, r, p)
        // A few dimples so it reads as a golf ball.
        p.color = 0xFFD9D4C4.toInt(); p.alpha = alpha
        for (j in -2..2) for (i in -2..2) {
            val x = cx + i * r * .36f + (j and 1) * r * .18f; val y = cy + j * r * .32f
            if (hypot(x - cx, y - cy) < r * .86f) c.drawCircle(x, y, r * .07f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = density
        p.color = 0xFF9AA6A6.toInt(); p.alpha = alpha
        c.drawLine(cx - r, cy, cx + r, cy, p); c.drawLine(cx, cy - r, cx, cy + r, p)
        p.strokeWidth = 1.5f * density; p.color = 0xFF163F3E.toInt(); p.alpha = alpha
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFE0473A.toInt(); p.alpha = alpha
        c.drawCircle(cx + spinX * r * .78f, cy - spinY * r * .78f, max(4.5f * density, r * .2f), p)
    }
}
