package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.os.SystemClock
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** A stationary power slider: the chosen strength stays put until the player shoots. */
class BilliardPowerView(context: Context): View(context) {
    var value=.5f; private set
    var changed: ((Float)->Unit)?=null
    var timingStopped: ((Long)->Unit)?=null
    var timing=false
        private set
    private var timingStart=0L
    private val animate=object: Runnable {
        override fun run() {
            if(!timing) return
            setPower(billiardTimingPower(SystemClock.uptimeMillis()-timingStart))
            postOnAnimation(this)
        }
    }
    fun startTiming() {
        removeCallbacks(animate); timingStart=SystemClock.uptimeMillis(); timing=true
        setPower(0f); postOnAnimation(animate)
    }
    fun stopTiming(atMillis: Long = SystemClock.uptimeMillis()): Float {
        val result=if(timing) billiardTimingPower(atMillis-timingStart) else value
        timing=false; removeCallbacks(animate); setPower(result)
        return result
    }
    override fun onDetachedFromWindow() { stopTiming(); super.onDetachedFromWindow() }
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val labelPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=14*resources.displayMetrics.scaledDensity; textAlign=Paint.Align.RIGHT
        typeface=Typeface.create("sans-serif-medium",0); color=0xFFF4E6C3.toInt()
    }
    private val trackTop get()=18*d
    private val trackBottom get()=(height-18*d).coerceAtLeast(trackTop+d)
    init { isFocusable=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun setPower(v: Float) {
        value=v.coerceIn(0f,1f); contentDescription=context.getString(R.string.billiard_power,(value*100).roundToInt()); invalidate()
    }
    override fun onDraw(c: Canvas) {
        val cx=width-24*d; val top=trackTop; val bottom=trackBottom
        p.color=0xD9111C23.toInt(); c.drawRoundRect(cx-19*d,0f,width.toFloat(),height.toFloat(),18*d,18*d,p)
        p.color=0xFF34414A.toInt(); c.drawRoundRect(cx-5*d,top,cx+5*d,bottom,5*d,5*d,p)
        val y=bottom-(bottom-top)*value
        p.shader=LinearGradient(0f,top,0f,bottom,0xFFF1CE85.toInt(),0xFF639A8B.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(cx-5*d,y,cx+5*d,bottom,5*d,5*d,p); p.shader=null
        p.color=0xFFF3D89D.toInt(); c.drawRoundRect(cx-13*d,y-4*d,cx+13*d,y+4*d,4*d,4*d,p)
        p.color=0xFF718086.toInt(); p.strokeWidth=d
        for(i in 0..10) { val tick=bottom-(bottom-top)*i/10; c.drawLine(cx+12*d,tick,cx+17*d,tick,p) }
        val label=context.getString(R.string.billiard_power_percent,(value*100).roundToInt())
        val baseline=y-(labelPaint.ascent()+labelPaint.descent())/2
        p.color=0xE5111C23.toInt()
        c.drawRoundRect(cx-22*d-labelPaint.measureText(label)-10*d,y-14*d,cx-20*d,y+14*d,8*d,8*d,p)
        c.drawText(label,cx-25*d,baseline,labelPaint)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(timing) {
            if(e.actionMasked==MotionEvent.ACTION_DOWN && e.pointerCount==1) timingStopped?.invoke(e.eventTime)
            return true
        }
        if(!isEnabled) return false
        if(e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_MOVE) {
            parent.requestDisallowInterceptTouchEvent(true)
            setPower((trackBottom-e.y)/(trackBottom-trackTop)); changed?.invoke(value)
        } else {
            parent.requestDisallowInterceptTouchEvent(false)
            if(e.actionMasked==MotionEvent.ACTION_UP) performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun adjust(value: Float) { setPower(value); changed?.invoke(this.value) }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(!isEnabled) return super.onKeyDown(keyCode,event)
        return when(keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT -> { adjust(value+.01f); true }
            KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT -> { adjust(value-.01f); true }
            else -> super.onKeyDown(keyCode,event)
        }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className=android.widget.SeekBar::class.java.name
        info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0f,100f,value*100)
        if(isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        if(isEnabled) when(action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { adjust(value+.05f); return true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { adjust(value-.05f); return true }
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val progress=arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                if(!progress.isFinite()) return false
                adjust(progress/100); return true
            }
        }
        return super.performAccessibilityAction(action,arguments)
    }
}
