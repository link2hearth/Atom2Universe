package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.ToggleButton
import androidx.appcompat.widget.AppCompatButton

/** A precision setting accepts a complete tap, never a swipe across its control. */
class BilliardTapToggle(context: Context): AppCompatButton(context), Checkable {
    private var checked=false
    private var tracking=false
    private var tap=false
    private var downX=0f
    private var downY=0f
    private val slop=ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    override fun isChecked()=checked
    override fun setChecked(value: Boolean) {
        checked=value; isSelected=value; refreshDrawableState()
    }
    override fun toggle() { isChecked=!isChecked }
    override fun performClick(): Boolean {
        if(!isEnabled) return false
        toggle()
        super.performClick()
        return true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            tracking=true; tap=isEnabled && event.pointerCount==1
            downX=event.rawX; downY=event.rawY; isPressed=tap
        }
        if(tracking) {
            val dx=event.rawX-downX; val dy=event.rawY-downY
            if(!isEnabled || event.pointerCount!=1 || dx*dx+dy*dy>slop*slop ||
                event.x<0 || event.x>=width || event.y<0 || event.y>=height) {
                tap=false; isPressed=false
            }
            when(event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val clicked=tap; tracking=false; tap=false; isPressed=false
                    if(clicked) performClick()
                }
                MotionEvent.ACTION_CANCEL -> { tracking=false; tap=false; isPressed=false }
            }
        }
        // Allow the enclosing ScrollView to intercept drags; its CANCEL also cancels the tap.
        return true
    }
    override fun onCreateDrawableState(extraSpace: Int): IntArray =
        super.onCreateDrawableState(extraSpace+1).also {
            if(checked) mergeDrawableStates(it,intArrayOf(android.R.attr.state_checked))
        }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className=ToggleButton::class.java.name; info.isCheckable=true; info.isChecked=checked
    }
}
