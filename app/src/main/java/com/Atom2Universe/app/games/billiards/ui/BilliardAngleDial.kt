package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

/** One dial for the whole aiming range, with an explicit precision setting. */
class BilliardAngleDial(context: Context): View(context) {
    var adjusted: ((Double)->Unit)?=null
    var precision=false
        set(value) { field=value; pendingTravel=0f; invalidate() }
    private var multiTouch=false
    private var last=0f
    private var pendingTravel=0f
    private var angle=0.0
    private val density=resources.displayMetrics.density
    private val normalDegreesPerDp=.1
    private val normalMarkSpacingDp=50f
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription=context.getString(R.string.billiard_dial_hint); isFocusable=true }
    fun setAngle(value: Double) { angle=value; contentDescription=context.getString(R.string.billiard_angle,angle*180/PI); invalidate() }
    override fun onDraw(c: Canvas) {
        val cx=width/2f
        paint.color=0xFF17262E.toInt(); c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),8*density,8*density,paint)
        paint.textAlign=Paint.Align.CENTER; paint.textSize=10*density; paint.strokeWidth=density
        val degrees=angle*180/PI
        val step=if(precision) .2 else normalDegreesPerDp*normalMarkSpacingDp
        val spacing=(if(precision) 80f else normalMarkSpacingDp)*density
        val base=floor(degrees/step)*step
        val offset=((degrees-base)/step*spacing).toFloat()
        val marks=(width/spacing/2).toInt()+2
        for(i in -marks..marks) {
            val x=cx+i*spacing-offset
            paint.color=0xFF8EA8AD.toInt(); c.drawLine(x,height*.52f,x,height*.82f,paint)
            c.drawText(context.getString(if(precision) R.string.billiard_dial_value_precise else R.string.billiard_dial_value,base+i*step),x,height*.40f,paint)
            val divisions=if(precision) 20 else 5
            for(j in 1 until divisions) {
                val tick=x+j*spacing/divisions
                c.drawLine(tick,height*(if(precision && j%5==0) .58f else .68f),tick,height*.82f,paint)
            }
        }
        paint.color=0xFFE4C488.toInt(); paint.strokeWidth=2*density
        c.drawLine(cx,0f,cx,height.toFloat(),paint)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(!isEnabled) return false
        if(e.actionMasked==MotionEvent.ACTION_DOWN) multiTouch=false
        if(e.pointerCount>1) multiTouch=true
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { last=e.x; pendingTravel=0f; parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> if(!multiTouch) {
                val travel=(e.x-last)/density; last=e.x
                if(precision) {
                    // Retain fractional finger travel: small moves accumulate into exact 0.01° steps.
                    pendingTravel+=travel
                    val ticks=(pendingTravel/4f).toInt()
                    if(ticks!=0) { pendingTravel-=ticks*4f; adjusted?.invoke(ticks*.01*PI/180) }
                } else if(abs(travel)>0f) adjusted?.invoke(travel*normalDegreesPerDp*PI/180)
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(isEnabled && keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT)) {
            nudge(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT); return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if(isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        if(isEnabled && action in listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            nudge(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); return true
        }
        return super.performAccessibilityAction(action,arguments)
    }
    fun nudge(forward: Boolean) {
        if(isEnabled) adjusted?.invoke((if(forward) 1 else -1)*(if(precision) .01 else .1)*PI/180)
    }
}
