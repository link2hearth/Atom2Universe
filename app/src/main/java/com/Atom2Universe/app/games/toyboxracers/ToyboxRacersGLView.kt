package com.Atom2Universe.app.games.toyboxracers

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder

internal class ToyboxRacersGLView(
    context: Context,
    renderer: ToyboxRacersRenderer
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(renderer)
        // À la demande : le renderer réclame lui-même l'image suivante tant que
        // quelque chose bouge, et s'endort dans l'éditeur au repos ou à l'arrivée.
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        renderer.renderRequester = { requestRender() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        // La simulation avance à pas fixe de 1/60 s : sur un écran 120 Hz, une
        // image sur deux n'était qu'une interpolation. 60 images suffisent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { holder.surface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }
}
