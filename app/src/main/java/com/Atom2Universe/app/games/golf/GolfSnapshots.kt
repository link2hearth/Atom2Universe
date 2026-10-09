package com.Atom2Universe.app.games.golf

import android.content.Context
import android.graphics.*
import android.opengl.EGL14 as EGL
import android.opengl.GLES20 as GL
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.Atom2Universe.app.games.golf.classic.ClassicCourses
import com.Atom2Universe.app.games.golf.classic.core.GolfClub
import com.Atom2Universe.app.games.golf.classic.core.GolfPoint
import com.Atom2Universe.app.games.golf.classic.core.ShotPreview
import com.Atom2Universe.app.games.golf.classic.render.ClassicFrame
import com.Atom2Universe.app.games.golf.classic.render.ClassicRenderer
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.atan2

/** Still frames from the game's renderers, made once off the UI thread. No live scene in menus. */
internal object GolfSnapshots {
    private const val VERSION = 1
    // Invalidate only changed scenery; retain the other course tiles on disk.
    private fun snapshotVersion(key: String) = when(key) {
        "heather", "vertigo", "snow_peaks", "wild_detours" -> 3
        "gardens" -> 2 // New rabbit model.
        else -> VERSION
    }
    private const val WIDTH = 640
    private const val HEIGHT = 480
    private val cache = LruCache<String, Bitmap>(4)
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "golf-snapshots").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun image(context: Context, key: String) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(0xFF346449.toInt())
        cache.get(key)?.let { setImageBitmap(it); return@apply }
        val target = WeakReference(this)
        val app = context.applicationContext
        worker.execute {
            val bitmap = runCatching { load(app, key) }.getOrNull()
            main.post { if (bitmap != null) target.get()?.setImageBitmap(bitmap) }
        }
    }

    /** Also called by the hub's background worker; concurrent requests share the same frame. */
    @Synchronized fun load(context: Context, key: String): Bitmap {
        cache.get(key)?.let { return it }
        val file = File(context.cacheDir, "golf-views/v${snapshotVersion(key)}-$key.png")
        BitmapFactory.decodeFile(file.path)?.let { cache.put(key, it); return it }
        val bitmap = capture(context, key)
        cache.put(key, bitmap)
        runCatching {
            file.parentFile?.mkdirs()
            val pending = File(file.parentFile, file.name + ".tmp")
            pending.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!pending.renameTo(file)) pending.delete()
        }
        return bitmap
    }

    private fun capture(context: Context, key: String): Bitmap {
        val display = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
        check(display != EGL.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL.eglInitialize(display, version, 0, version, 1))
        var glContext = EGL.EGL_NO_CONTEXT
        var surface = EGL.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT,
                EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT,
                EGL.EGL_RED_SIZE, 8, EGL.EGL_GREEN_SIZE, 8, EGL.EGL_BLUE_SIZE, 8,
                EGL.EGL_ALPHA_SIZE, 8, EGL.EGL_DEPTH_SIZE, 16, EGL.EGL_STENCIL_SIZE, 0, EGL.EGL_NONE)
            check(EGL.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val config = checkNotNull(configs[0])
            glContext = EGL.eglCreateContext(display, config, EGL.EGL_NO_CONTEXT,
                intArrayOf(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE), 0)
            check(glContext != EGL.EGL_NO_CONTEXT)
            surface = EGL.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL.EGL_WIDTH, WIDTH, EGL.EGL_HEIGHT, HEIGHT, EGL.EGL_NONE), 0)
            check(surface != EGL.EGL_NO_SURFACE)
            check(EGL.eglMakeCurrent(display, surface, surface, glContext))
            val course = ClassicCourses.find(key)
            val hole = course.holes[when (course.id) { "heather" -> 4; "minigolf" -> 5; "minigolf_crazy" -> 14; else -> 2 }]
            // Mini-golf: the putter at the tee, looking down the lane the caddie would pick.
            val mini = hole.mini != null
            val z = if (mini) hole.tee.z else hole.cup.z - 24f
            val x = if (mini) hole.tee.x else hole.fairwayCenter(z)
            val ball = GolfPoint(x, hole.heightAt(x, z) + .022f, z)
            val target = if (mini) hole.recommendedLanding(ball) else hole.cup
            val renderer = ClassicRenderer(hole)
            renderer.frame = ClassicFrame(ball, atan2(target.x - x, target.z - z), false,
                ShotPreview.NONE, club = if (mini) GolfClub.PUTTER else GolfClub.SW)
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, WIDTH, HEIGHT)
            renderer.onDrawFrame(null)
            val pixels = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
            GL.glReadPixels(0, 0, WIDTH, HEIGHT, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels)
            check(GL.glGetError() == GL.GL_NO_ERROR)
            val colors = IntArray(WIDTH * HEIGHT)
            for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
                val offset = (y * WIDTH + x) * 4
                colors[(HEIGHT - y - 1) * WIDTH + x] = Color.rgb(
                    pixels.get(offset).toInt() and 255, pixels.get(offset + 1).toInt() and 255, pixels.get(offset + 2).toInt() and 255)
            }
            return Bitmap.createBitmap(colors, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        } finally {
            // This isolated context owns every GPU object, including partially created scenes.
            EGL.eglMakeCurrent(display, EGL.EGL_NO_SURFACE, EGL.EGL_NO_SURFACE, EGL.EGL_NO_CONTEXT)
            if (surface != EGL.EGL_NO_SURFACE) EGL.eglDestroySurface(display, surface)
            if (glContext != EGL.EGL_NO_CONTEXT) EGL.eglDestroyContext(display, glContext)
            // EGL_DEFAULT_DISPLAY is shared with GLSurfaceView. Terminating it here would
            // invalidate a game/wardrobe surface opened while this background capture runs.
            // Only the pbuffer and context above belong to this capture.
            EGL.eglReleaseThread()
        }
    }
}
