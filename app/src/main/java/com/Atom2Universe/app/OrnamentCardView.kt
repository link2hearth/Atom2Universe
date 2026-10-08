package com.Atom2Universe.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/** Keeps the existing CardView behaviour, but clips its entire artwork/ripple to the frame. */
class OrnamentCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.cardview.R.attr.cardViewStyle
) : CardView(context, attrs, defStyleAttr), OrnamentalCard {
    private var ornament: OrnamentCardRenderer? = null
    init {
        ornament = OrnamentCardRenderer(this)
        refreshOrnament(context)
    }
    override fun setRadius(radius: Float) {
        ornament?.normalRadius = radius
        super.setRadius(if (ornament?.enabled == true) 0f else radius)
    }
    override fun refreshOrnament(context: Context?) {
        val renderer = ornament ?: return
        renderer.refresh(context)
        val radius = if (renderer.enabled) 0f else renderer.normalRadius
        if (super.getRadius() != radius) super.setRadius(radius)
    }
    override fun draw(canvas: Canvas) {
        val renderer = ornament
        if (renderer?.enabled != true) super.draw(canvas)
        else {
            val save = renderer.clip(canvas)
            super.draw(canvas)
            renderer.finish(canvas, save)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshOrnament() }
}

class OrnamentMaterialCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr), OrnamentalCard {
    private var ornament: OrnamentCardRenderer? = null
    private var normalStroke = 0
    init {
        normalStroke = strokeWidth
        ornament = OrnamentCardRenderer(this)
        refreshOrnament(context)
    }
    override fun setRadius(radius: Float) {
        ornament?.normalRadius = radius
        super.setRadius(if (ornament?.enabled == true) 0f else radius)
    }
    override fun setStrokeWidth(strokeWidth: Int) {
        normalStroke = strokeWidth
        super.setStrokeWidth(if (ornament?.enabled == true) 0 else strokeWidth)
    }
    override fun refreshOrnament(context: Context?) {
        val renderer = ornament ?: return
        renderer.refresh(context)
        val radius = if (renderer.enabled) 0f else renderer.normalRadius
        val stroke = if (renderer.enabled) 0 else normalStroke
        if (super.getRadius() != radius) super.setRadius(radius)
        if (super.getStrokeWidth() != stroke) super.setStrokeWidth(stroke)
        // MaterialCardView updates clipToOutline in setRadius; our concave contour uses Canvas.
        if (renderer.enabled) clipToOutline = false
    }
    override fun draw(canvas: Canvas) {
        val renderer = ornament
        if (renderer?.enabled != true) super.draw(canvas)
        else {
            val save = renderer.clip(canvas)
            super.draw(canvas)
            renderer.finish(canvas, save)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshOrnament() }
}

internal interface OrnamentalCard {
    fun refreshOrnament(context: Context? = null)
}

private class OrnamentCardRenderer(private val view: CardView) {
    var normalRadius = view.radius
    var enabled = false
        private set
    private var frame: OrnamentDrawable? = null
    private var palette = 0L
    private var themeContext = view.context
    private val normalOutline = view.outlineProvider
    private val normalClip = view.clipToOutline
    private val ornamentalOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            resize(notifyOutline = false)
            frame?.getOutline(outline) ?: outline.setEmpty()
        }
    }

    fun refresh(context: Context?) {
        // Material widgets may wrap a snapshot of the activity theme. Live previews use the
        // refreshed activity theme, and recycled holders retain that context on reattachment.
        if (context != null) themeContext = context
        val wasEnabled = enabled
        enabled = AppearanceStyle.hasOrnaments(themeContext)
        if (enabled) {
            val accent = AppearanceStyle.color(themeContext, R.attr.a2uMusicAccent)
            val surface = AppearanceStyle.color(themeContext, R.attr.a2uSurfaceColor)
            val key = (accent.toLong() shl 32) xor (surface.toLong() and 0xffffffffL)
            if (frame == null || key != palette) {
                palette = key
                frame = OrnamentDrawable(themeContext, null)
                view.invalidate()
            }
            if (view.outlineProvider !== ornamentalOutline) view.outlineProvider = ornamentalOutline
            view.clipToOutline = false
            resize()
        } else if (wasEnabled) {
            frame = null
            view.outlineProvider = normalOutline
            view.clipToOutline = normalClip
            view.invalidate()
        }
    }

    private fun resize(notifyOutline: Boolean = true) {
        val border = frame ?: return
        // A stable four-form rhythm within each list, updated when a holder is recycled.
        val list = view.parent as? RecyclerView
        val position = list?.getChildAdapterPosition(view) ?: RecyclerView.NO_POSITION
        val variantChanged = position != RecyclerView.NO_POSITION && border.setVariant(position)
        val sizeChanged = border.bounds.width() != view.width || border.bounds.height() != view.height
        border.setBounds(0, 0, view.width, view.height)
        if (notifyOutline && (variantChanged || sizeChanged)) view.invalidateOutline()
    }

    fun clip(canvas: Canvas): Int {
        resize()
        val save = canvas.save()
        frame?.let { canvas.clipPath(it.contour.outer) }
        return save
    }

    fun finish(canvas: Canvas, save: Int) {
        canvas.restoreToCount(save)
        frame?.drawFrame(canvas)
    }
}
