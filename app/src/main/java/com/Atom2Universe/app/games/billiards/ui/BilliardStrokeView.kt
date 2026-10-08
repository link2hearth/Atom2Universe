package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** An explicitly armed cue: upward stroke speed, rather than distance, sets power. */
class BilliardStrokeView(context: Context): View(context) {
    var fired: ((Float)->Unit)?=null
    private val stroke=BilliardStrokeGesture()
    private val d=resources.displayMetrics.density
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var originY=0f
    private var offset=0f
    private var power=0f
    init {
        contentDescription=context.getString(R.string.billiard_stroke_hint)
        isFocusable=true
    }
    fun reset() { stroke.cancel(); offset=0f; power=0f; invalidate() }
    override fun onDetachedFromWindow() { reset(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val cx=width/2f
        p.color=0xED101B22.toInt(); c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),20*d,20*d,p)
        p.color=0xFFB2C5C4.toInt(); p.strokeWidth=2*d
        c.drawLine(cx,38*d,cx,58*d,p); c.drawLine(cx,38*d,cx-7*d,46*d,p); c.drawLine(cx,38*d,cx+7*d,46*d,p)
        val top=height*.3f+offset; val bottom=height*.82f+offset
        p.color=0xFFE4C488.toInt(); c.drawRoundRect(cx-4*d,top,cx+4*d,bottom,4*d,4*d,p)
        p.color=0xFF6A4430.toInt(); c.drawRoundRect(cx-5*d,top+(bottom-top)*.65f,cx+5*d,bottom,4*d,4*d,p)
        p.color=0xFF72AFBD.toInt(); c.drawRect(cx-4*d,top,cx+4*d,top+7*d,p)
        p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.create("sans-serif-medium",0)
        p.textSize=12*resources.displayMetrics.scaledDensity; p.color=0xFFF1EBDD.toInt()
        c.drawText(context.getString(R.string.billiard_stroke_slide),cx,22*d,p)
        c.drawText(context.getString(R.string.billiard_power_percent,(power*100).roundToInt()),cx,height-14*d,p)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(!isEnabled) return false
        if(e.pointerCount!=1 || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            reset(); parent.requestDisallowInterceptTouchEvent(false); return true
        }
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset(); originY=e.y; stroke.begin(e.y/d,e.eventTime)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if(stroke.active) {
                for(i in 0 until e.historySize) stroke.move(e.getHistoricalY(i)/d,e.getHistoricalEventTime(i))
                power=stroke.move(e.y/d,e.eventTime)
                offset=(e.y-originY).coerceIn(-height*.18f,height*.12f); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val chosen=stroke.release(e.y/d,e.eventTime)
                reset(); parent.requestDisallowInterceptTouchEvent(false); performClick()
                if(chosen!=null) fired?.invoke(chosen)
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
