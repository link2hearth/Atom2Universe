package com.Atom2Universe.app.zoomcanvas

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label

/**
 * Un curseur court encadré de deux flèches : le curseur pour aller vite, les flèches pour régler à
 * une unité près (un appui = un cran ; appui tenu = les crans défilent).
 */
@SuppressLint("ViewConstructor")
class StepSlider(
    context: Context,
    name: String,
    private val min: Int,
    private val max: Int,
    value: Int,
    private val format: (Int) -> String,
    private val onChange: (Int) -> Unit,
) : LinearLayout(context) {

    /** Un curseur dans une zone qui défile : il interdit au parent de lui voler le geste. */
    private val bar = object : SeekBar(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            return super.onTouchEvent(event)
        }
    }
    private val valueText = context.label(format(value), 12f, true)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(context.label(name, 12f).apply { setPadding(context.dp(4), 0, context.dp(2), 0) })
        addView(arrow(R.drawable.ic_chevron_left, R.string.zc_less, -1))
        bar.max = max - min
        bar.progress = value - min
        bar.progressTintList = ColorStateList.valueOf(context.accentColor())
        bar.thumbTintList = ColorStateList.valueOf(context.accentColor())
        addView(bar, LayoutParams(context.dp(120), context.dp(40)))
        addView(arrow(R.drawable.ic_chevron_right, R.string.zc_more, 1))
        addView(valueText.apply { minWidth = context.dp(40); gravity = Gravity.CENTER })
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                valueText.text = format(p + min)
                if (fromUser) onChange(p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun step(by: Int) {
        val v = (bar.progress + min + by).coerceIn(min, max)
        if (v == bar.progress + min) return
        bar.progress = v - min
        valueText.text = format(v)
        onChange(v)
    }

    /** Une flèche : un cran à l'appui, puis un cran toutes les 60 ms tant qu'on la tient. */
    @SuppressLint("ClickableViewAccessibility")
    private fun arrow(icon: Int, desc: Int, by: Int) = ImageButton(context, null, 0, R.style.PxIconButton).apply {
        setImageResource(icon)
        contentDescription = context.getString(desc)
        layoutParams = LayoutParams(context.dp(34), context.dp(40))
        setPadding(context.dp(5), context.dp(8), context.dp(5), context.dp(8))
        val repeat = object : Runnable {
            override fun run() {
                step(by)
                postDelayed(this, 60)
            }
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    step(by)
                    v.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(repeat)
                }
            }
            true
        }
    }
}
