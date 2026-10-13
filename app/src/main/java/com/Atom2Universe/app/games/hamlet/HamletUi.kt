package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button

/** Shared native controls: illustrated glyphs, generous touch targets and warm parchment cards. */
internal object HamletUi {
    const val FOREST = 0xFF213D38.toInt()
    const val INK = 0xFF293D37.toInt()
    const val PAPER = 0xFFF6EDD9.toInt()
    const val GOLD = 0xFFEAC779.toInt()

    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun panel(context: Context, color: Int, radius: Int = 14, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
        if (stroke != 0) setStroke(dp(context, 1).coerceAtLeast(1), stroke)
    }
    fun control(context: Context, selected: Boolean = false, quiet: Boolean = false): Drawable {
        val normal = when { selected -> GOLD; quiet -> 0xFF294942.toInt(); else -> 0xFF376250.toInt() }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), panel(context, 0xFFD5D9CE.toInt(), 10))
            addState(intArrayOf(), panel(context, normal, 10, if (selected) 0xFFFFDEA0.toInt() else 0xFF527867.toInt()))
        }
        return RippleDrawable(ColorStateList.valueOf(0x32FFFFFF), states, null)
    }
    fun button(context: Context, label: String, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 12f
        minWidth = 0; minimumWidth = 0; minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        gravity = Gravity.CENTER; setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
        backgroundTintList = null; background = control(context)
        elevation = 0f; stateListAnimator = null
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(0xFF6C786C.toInt(), PAPER)))
        setOnClickListener { click() }
    }
    fun navigation(context: Context, label: String, glyph: String, click: () -> Unit) = button(context, label, click).apply {
        textSize = 10.5f; maxLines = 2; setPadding(dp(context, 2), dp(context, 7), dp(context, 2), dp(context, 5))
        setCompoundDrawablesWithIntrinsicBounds(null, icon(context, glyph, 22), null, null)
        compoundDrawablePadding = dp(context, 3)
        background = control(context, quiet = true)
    }
    fun icon(context: Context, glyph: String, size: Int = 20): Drawable = object : Drawable() {
        private val side = dp(context, size)
        override fun getIntrinsicWidth() = side
        override fun getIntrinsicHeight() = side
        override fun draw(canvas: Canvas) {
            HamletGlyphs.draw(canvas, glyph, bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()).toFloat() * .9f, 0xDDAE68)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }.apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
}
