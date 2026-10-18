package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.Atom2Universe.app.games.billiards.core.V3
import com.Atom2Universe.app.games.billiards.render.BilliardRenderer
import com.Atom2Universe.app.games.billiards.render.BilliardCameraMode
import kotlin.math.abs
import kotlin.math.roundToInt

class BilliardSurface(context: Context,val scene: BilliardRenderer) : GLSurfaceView(context) {
    var touch: ((V3,Boolean)->Unit)?=null
    var finished: (()->Unit)?=null
    var cameraChanging: (()->Unit)?=null
    var cameraAdjusted: (()->Unit)?=null
    var selectingTarget=false
    var targetTapped: ((Float,Float)->Unit)?=null
    var placing=false
    var shotEditable=true
    private enum class Gesture { PLACE, CAMERA, SELECT }
    private var gesture=Gesture.CAMERA
    private var animating=false
    private val animate=object: Runnable {
        override fun run() {
            requestRender()
            if(scene.camera.settling || scene.camera.showcase) postOnAnimation(this) else animating=false
        }
    }
    fun wakeCamera() {
        requestRender()
        if(!animating) { animating=true; postDelayed(animate,32) }
    }
    override fun onPause() { removeCallbacks(animate); animating=false; super.onPause() }
    override fun onDetachedFromWindow() { removeCallbacks(animate); animating=false; super.onDetachedFromWindow() }
    private var multi=false
    private var panning=false
    private var multiX=0f; private var multiY=0f
    private var started=false
    private var downX=0f; private var downY=0f
    private var lastX=0f; private var lastY=0f
    private val scale=ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cameraChanging?.invoke()
            scene.camera.dolly(detector.scaleFactor); cameraAdjusted?.invoke(); wakeCamera(); return true
        }
    })
    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(8,8,8,8,24,0)
        setRenderer(scene); preserveEGLContextOnPause=true; renderMode=RENDERMODE_WHEN_DIRTY
    }
    // The 3D is drawn at about 1.75 pixels per dp, then enlarged by the display hardware.
    // On a 400 dpi tablet the GPU fills half the pixels; text and controls stay sharp.
    private val renderScale=(1.75f/resources.displayMetrics.density).coerceIn(.5f,1f)
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        scene.viewSize(w,h)
        val bufferW=(w*renderScale).roundToInt().coerceAtLeast(1); val bufferH=(h*renderScale).roundToInt().coerceAtLeast(1)
        // setFixedSize requests a layout: posted so it is never called from inside one.
        post { holder.setFixedSize(bufferW,bufferH) }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scale.onTouchEvent(e)
        if(e.actionMasked==MotionEvent.ACTION_DOWN) {
            multi=false; started=false; downX=e.x; downY=e.y
            gesture=when {
                !shotEditable || scene.frame?.moving==true -> Gesture.CAMERA
                selectingTarget -> Gesture.SELECT
                placing -> Gesture.PLACE
                else -> Gesture.CAMERA
            }
            lastX=e.x; lastY=e.y
            parent.requestDisallowInterceptTouchEvent(true)
        } else if(e.pointerCount>=2) {
            val x=(e.getX(0)+e.getX(1))/2; val y=(e.getY(0)+e.getY(1))/2
            if(!multi) { multiX=x; multiY=y; panning=false }
            if(multi && e.actionMasked==MotionEvent.ACTION_MOVE) {
                if(abs(x-multiX)+abs(y-multiY)>8*resources.displayMetrics.density) panning=true
                if(panning) pan(lastX,lastY,x,y)
            }
            multi=true; lastX=x; lastY=y; wakeCamera()
        } else if(e.actionMasked==MotionEvent.ACTION_MOVE && !multi) {
            if(!started && abs(e.x-downX)+abs(e.y-downY)>8*resources.displayMetrics.density) {
                started=true
                if(gesture==Gesture.PLACE && shotEditable && scene.frame?.moving!=true)
                    scene.tablePoint(downX,downY)?.let { touch?.invoke(it,true) }
            }
            if(started) {
                val density=resources.displayMetrics.density
                if(gesture==Gesture.CAMERA || gesture==Gesture.SELECT || !shotEditable || scene.frame?.moving==true) {
                    if(scene.camera.mode==BilliardCameraMode.TOP) pan(lastX,lastY,e.x,e.y)
                    else { cameraChanging?.invoke(); scene.camera.orbit((e.x-lastX)/density,(e.y-lastY)/density); cameraAdjusted?.invoke() }
                } else if(gesture==Gesture.PLACE) scene.tablePoint(e.x,e.y)?.let { touch?.invoke(it,false) }
                wakeCamera()
            }
            lastX=e.x; lastY=e.y
        }
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            parent.requestDisallowInterceptTouchEvent(false)
            if(!multi && e.actionMasked==MotionEvent.ACTION_UP) {
                if(!started && shotEditable && scene.frame?.moving!=true) {
                    if(gesture==Gesture.SELECT && selectingTarget) targetTapped?.invoke(e.x,e.y)
                    else if(gesture!=Gesture.CAMERA && gesture!=Gesture.SELECT)
                        scene.tablePoint(e.x,e.y)?.let { touch?.invoke(it,true) }
                }
                performClick()
            }
            if(e.actionMasked==MotionEvent.ACTION_UP) finished?.invoke()
        }
        return true
    }
    private fun pan(fromX: Float,fromY: Float,toX: Float,toY: Float) {
        val a=scene.tablePoint(fromX,fromY); val b=scene.tablePoint(toX,toY)
        if(a!=null && b!=null) {
            cameraChanging?.invoke()
            scene.camera.pan((a.x-b.x).toFloat().coerceIn(-.35f,.35f),(a.y-b.y).toFloat().coerceIn(-.35f,.35f))
            cameraAdjusted?.invoke()
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
