package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** Choix de la tonique. Un glissé ne transpose le morceau qu'au relâchement du doigt. */
internal class StudioTonicKeyboard(context: Context) : ViewGroup(context) {
    var onKeySelected: ((Int) -> Unit)? = null
    var keyPc: Int = 0
        set(value) {
            if (value !in keys.indices || field == value) return
            keys[field].isSelected = false
            field = value
            keys[field].isSelected = true
        }

    private val whites = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val names = resources.getStringArray(R.array.studio_key_names)
    private val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
    private val accentInk = if (ColorUtils.calculateContrast(Color.BLACK, accent) >
        ColorUtils.calculateContrast(Color.WHITE, accent)) Color.BLACK else Color.WHITE
    private val keys: Array<Button> = Array(12) { pitch ->
        val white = pitch in whites
        val paper = if (white) 0xfff0f0f5.toInt() else 0xff282931.toInt()
        val ink = if (white) 0xff383943.toInt() else 0xffeeeef4.toInt()
        Button(context, null, 0).apply {
            id = View.generateViewId()
            text = names[pitch]
            textSize = if (white) 13f else 11f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            isSingleLine = true
            isFocusable = true
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(5))
            setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(accentInk, ink)))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected, android.R.attr.state_focused), cap(accent, accentInk, 2))
                addState(intArrayOf(android.R.attr.state_selected), cap(accent, accentInk, 1))
                addState(intArrayOf(android.R.attr.state_pressed), cap(paper, accent, 2))
                addState(intArrayOf(android.R.attr.state_focused), cap(paper, accent, 2))
                addState(intArrayOf(), cap(paper, 0xff858690.toInt(), 1))
            }
            setOnClickListener {
                if (keyPc != pitch) {
                    keyPc = pitch
                    onKeySelected?.invoke(pitch)
                }
            }
        }
    }
    private var pointer = -1
    private var pressedKey: View? = null
        set(value) {
            if (field === value) return
            field?.isPressed = false
            field = value
            value?.isPressed = true
        }

    init {
        for (pitch in whites) addView(keys[pitch])
        for (pitch in keys.indices) if (pitch !in whites) addView(keys[pitch])
        keys[keyPc].isSelected = true
        for (pitch in 1 until keys.size) {
            val previous = keys[pitch - 1]
            val next = keys[pitch]
            previous.nextFocusForwardId = next.id
            previous.nextFocusRightId = next.id
            next.nextFocusLeftId = previous.id
            next.accessibilityTraversalAfter = previous.id
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun cap(fill: Int, edge: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = AppearanceStyle.corner(context, 4f)
        setColor(fill)
        setStroke(dp(stroke), edge)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(294), widthMeasureSpec)
        val h = resolveSize(dp(72), heightMeasureSpec)
        setMeasuredDimension(w, h)
        for (pitch in keys.indices) {
            val white = pitch in whites
            val keyWidth = if (white) {
                val slot = whites.indexOf(pitch)
                (slot + 1) * w / 7 - slot * w / 7
            } else (w * .64f / 7).roundToInt()
            keys[pitch].measure(MeasureSpec.makeMeasureSpec(keyWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(if (white) h else (h * .62f).roundToInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (pitch in keys.indices) {
            val key = keys[pitch]
            val white = whites.indexOf(pitch)
            val x = if (white >= 0) white * width / 7
                else (whites.indexOf(pitch - 1) + 1) * width / 7 - key.measuredWidth / 2
            key.layout(x, 0, x + key.measuredWidth, key.measuredHeight)
        }
    }

    private fun keyAt(x: Float, y: Float): View? {
        // Les noires sont par-dessus les blanches dans la partie haute.
        for (i in childCount - 1 downTo 0) {
            val key = getChildAt(i)
            if (x >= key.left && x < key.right && y >= key.top && y < key.bottom) return key
        }
        return null
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0)
                parent.requestDisallowInterceptTouchEvent(true)
                pressedKey = keyAt(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(pointer)
                if (i >= 0) pressedKey = keyAt(event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                if (event.getPointerId(i) == pointer) {
                    val key = keyAt(event.getX(i), event.getY(i))
                    endTouch()
                    key?.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> endTouch()
        }
        return true
    }

    private fun endTouch() {
        pointer = -1
        pressedKey = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
