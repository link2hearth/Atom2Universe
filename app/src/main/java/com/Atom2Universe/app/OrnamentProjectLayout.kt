package com.Atom2Universe.app

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

/** Project galleries keep their existing padding, thumbnails, gestures and accessibility. */
class OrnamentProjectLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    init {
        if (AppearanceStyle.hasOrnaments(context)) clipToOutline = false
    }

    override fun draw(canvas: Canvas) {
        val frame = background as? OrnamentDrawable
        if (frame == null) {
            super.draw(canvas)
            return
        }
        val position = (parent as? RecyclerView)?.getChildAdapterPosition(this) ?: RecyclerView.NO_POSITION
        if (position != RecyclerView.NO_POSITION && frame.setVariant(position)) invalidateOutline()
        frame.setBounds(0, 0, width, height)
        val save = canvas.save()
        canvas.clipPath(frame.contour.outer)
        super.draw(canvas)
        canvas.restoreToCount(save)
        frame.drawFrame(canvas)
    }
}
