package com.Atom2Universe.app.games.toyboxracers

import android.content.Context
import android.opengl.GLSurfaceView

internal class ToyboxRacersGLView(
    context: Context,
    renderer: ToyboxRacersRenderer
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}
