package com.Atom2Universe.app.science.geology

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** Uses exactly the same native artwork as the interactive view, without an animator. */
internal class EarthPreviewView(context: Context, private val scene: EarthScene) : View(context) {
    private val art = EarthArt()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val artWidth = if (scene == EarthScene.GLOBE) 650f else 1000f
        val scale = minOf(width / artWidth, height / 720f)
        canvas.translate((width - artWidth * scale) / 2, (height - 720f * scale) / 2)
        canvas.scale(scale, scale)
        art.render(canvas, if (scene == EarthScene.AGES) EarthScene.ARCHIVE else scene, .8f, .15f, false)
        canvas.restore()
    }
}
