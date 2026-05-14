package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView qui couvre toute la vue (comme centerCrop) avec deux modes d'ancrage :
 *  - centerAlign = false (défaut) : bas de l'image ancré en bas de la vue
 *  - centerAlign = true           : image centrée verticalement
 */
class BottomCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var centerAlign = false

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setAlignCenter(center: Boolean) {
        if (centerAlign == center) return
        centerAlign = center
        applyCropMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCropMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        applyCropMatrix()
    }

    private fun applyCropMatrix() {
        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return
        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        // minOf = fit-inside, l'image est affichée entièrement (barres noires si nécessaire)
        val scale = minOf(viewW / imgW, viewH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale

        val dx = (viewW - scaledW) / 2f                             // centré horizontalement
        val dy = if (centerAlign) (viewH - scaledH) / 2f            // centré verticalement
                 else viewH - scaledH                               // ancré en bas

        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(dx, dy)
        imageMatrix = m
    }
}
