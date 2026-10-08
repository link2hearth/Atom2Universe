package com.Atom2Universe.app.games.golf.classic.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfPoint
import com.Atom2Universe.app.games.golf.classic.core.GolfClub
import com.Atom2Universe.app.games.golf.classic.core.ShotPreview
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Free aerial camera: the ground point looked at, the distance to it and the heading of the
 * screen's up direction. Shared by the renderer and the touch code that picks ground points.
 */
data class OverviewView(val x: Float, val z: Float, val distance: Float, val yaw: Float) {
    /** Nearly vertical from far away, oblique up close so that the height of an arc reads. */
    val pitch: Float get() {
        val t = ((distance - 15f) / 350f).coerceIn(0f, 1f)
        return Math.toRadians((42f + 36f * t * t * (3f - 2f * t)).toDouble()).toFloat()
    }
    fun look(hole: ClassicHole) = GolfPoint(x, hole.heightAt(x, z), z)
    fun eye(hole: ClassicHole): GolfPoint {
        val look = look(hole)
        val horizontal = distance * cos(pitch)
        val ex = x - sin(yaw) * horizontal
        val ez = z - cos(yaw) * horizontal
        return GolfPoint(ex, max(look.y + distance * sin(pitch), hole.heightAt(ex, ez) + 2f), ez)
    }

    companion object {
        const val MIN_DISTANCE = 6f
        const val MAX_DISTANCE = 900f
    }
}

/** A snapshot crosses the UI/GL boundary; the renderer never advances the game. */
data class ClassicFrame(
    val ball: GolfPoint,
    val aimAngle: Float,
    val flying: Boolean,
    val preview: ShotPreview,
    /** Free aerial camera, or null for the player's views. */
    val overview: OverviewView? = null,
    val zoom: Float = 1f,
    val club: GolfClub = GolfClub.DRIVER,
    /** -1 at rest, up to .35 at the top of the backswing, then the strike and follow-through. */
    val swingProgress: Float = -1f,
    /** Size of the swing: a putt or a chip only takes the club back a little. */
    val swingPower: Float = 1f,
    /** The player is drawing the shot back: the guide is live. */
    val aiming: Boolean = false,
    val golfer: GolferAppearance = GolferAppearance(),
)

@SuppressLint("ViewConstructor")
class ClassicSurface(context: Context, hole: ClassicHole) : GLSurfaceView(context) {
    var onReady: (() -> Unit)? = null
    private val golfRenderer = ClassicRenderer(hole).apply { onReady = { post { this@ClassicSurface.onReady?.invoke() } } }
    private val renderScale = (1.65f / resources.displayMetrics.density).coerceIn(.55f, 1f)
    private var lastRenderRequest = 0L

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(DepthConfig())
        setRenderer(golfRenderer)
        preserveEGLContextOnPause = true
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /**
     * At most 60 images a second; 30 when [calm] (nothing has changed for a while): the grass,
     * water, clouds and board lights move slowly enough not to show it, and the GPU works half as much.
     */
    fun submit(frame: ClassicFrame, calm: Boolean, vsyncNanos: Long) {
        golfRenderer.frame = frame
        // Paced on the display's own ticks, with wide margins: one image per 60 Hz tick (every
        // other one at 120 Hz), every second tick when calm. A clock read at submit time jitters,
        // and a threshold as tight as the tick itself skipped one now and then.
        if(vsyncNanos-lastRenderRequest>=if(calm) 28_000_000L else 12_000_000L) {
            lastRenderRequest=vsyncNanos
            requestRender()
        }
    }
    fun release() { queueEvent { golfRenderer.release() } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = (w * renderScale).roundToInt().coerceAtLeast(1)
        val bh = (h * renderScale).roundToInt().coerceAtLeast(1)
        post { if (width == w && height == h) holder.setFixedSize(bw, bh) }
    }

    private class DepthConfig : EGLConfigChooser {
        override fun chooseConfig(egl:EGL10,display:EGLDisplay):EGLConfig {
            for(depth in intArrayOf(24,16)) {
                val attributes=intArrayOf(EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,
                    EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_DEPTH_SIZE,depth,
                    EGL10.EGL_RENDERABLE_TYPE,4,EGL10.EGL_NONE)
                val configs=arrayOfNulls<EGLConfig>(1)
                val count=IntArray(1)
                if(egl.eglChooseConfig(display,attributes,configs,1,count)&&count[0]>0) configs[0]?.let { return it }
            }
            error("OpenGL ES 2 configuration unavailable")
        }
    }
}
