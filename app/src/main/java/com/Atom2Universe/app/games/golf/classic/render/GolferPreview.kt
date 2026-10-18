package com.Atom2Universe.app.games.golf.classic.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** The wardrobe displays the actual in-game mesh and lighting, with touch rotation. */
@SuppressLint("ViewConstructor")
internal class GolferPreview(context: Context, initial: GolferAppearance) : GLSurfaceView(context) {
    private val scene=PreviewRenderer(initial)
    var appearance: GolferAppearance
        get()=scene.look
        set(value) { scene.look=value;requestRender() }
    private var lastX=0f
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,0,16,0)
        setRenderer(scene)
        preserveEGLContextOnPause=true
        renderMode=RENDERMODE_WHEN_DIRTY
        isClickable=true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX=event.x;parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> { scene.yaw+=(event.x-lastX)*.5f;lastX=event.x;requestRender() }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false);performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    fun release() { queueEvent { scene.release() } }

    private class PreviewRenderer(initial: GolferAppearance): Renderer {
        @Volatile var look=initial
        @Volatile var yaw=-22f
        private val golfer=GolferRenderer()
        private val projection=FloatArray(16)
        private val view=FloatArray(16)
        private val vp=FloatArray(16)
        private val model=FloatArray(16)
        private var released=false
        override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
            released=false;golfer.create()
            GL.glClearColor(.10f,.18f,.21f,1f)
            GL.glEnable(GL.GL_DEPTH_TEST)
        }
        override fun onSurfaceChanged(gl: GL10?,w: Int,h: Int) {
            GL.glViewport(0,0,w,h)
            Matrix.perspectiveM(projection,0,34f,w.toFloat()/h.coerceAtLeast(1),.1f,20f)
            Matrix.setLookAtM(view,0,0f,1.5f,4.1f,0f,.91f,0f,0f,1f,0f)
            Matrix.multiplyMM(vp,0,projection,0,view,0)
        }
        override fun onDrawFrame(gl: GL10?) {
            if(released)return
            GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
            val appearance=look
            golfer.pose.update(-1f,1f,false,0f,appearance.female,showcase=true)
            Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,yaw,0f,1f,0f)
            golfer.draw(appearance,false,true,model,vp,0f,1.5f,4.1f)
        }
        fun release() { released=true;golfer.release() }
    }
}
