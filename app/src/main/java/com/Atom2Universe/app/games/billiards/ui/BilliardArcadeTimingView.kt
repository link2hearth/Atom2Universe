package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R

/** Separate precision challenge, sampled at the actual press timestamp. */
class BilliardArcadeTimingView(context: Context): View(context) {
    var stopped: ((Long)->Unit)?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private var started=0L
    private var active=false
    private var cursor=0f
    private val animate=object: Runnable {
        override fun run() {
            if(!active) return
            cursor=billiardTimingPower(SystemClock.uptimeMillis()-started)
            invalidate(); postOnAnimation(this)
        }
    }
    init {
        isClickable=true; isFocusable=true
        contentDescription=context.getString(R.string.billiard_arcade_timing_hint)
        setOnClickListener { if(active) stopped?.invoke(SystemClock.uptimeMillis()) }
    }
    fun start() {
        removeCallbacks(animate); started=SystemClock.uptimeMillis(); cursor=0f; active=true
        invalidate(); postOnAnimation(animate)
    }
    fun stop(atMillis: Long = SystemClock.uptimeMillis()): Double {
        if(active) cursor=billiardTimingPower(atMillis-started)
        active=false; removeCallbacks(animate); invalidate()
        return billiardArcadeQuality(cursor)
    }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN && active && event.pointerCount==1 && isEnabled)
            stopped?.invoke(event.eventTime)
        return true
    }
    override fun onDraw(canvas: Canvas) {
        val left=16*d; val right=width-16*d; val span=(right-left).coerceAtLeast(1f)
        val top=30*d; val bottom=height-12*d
        paint.color=0xFF34414A.toInt()
        canvas.drawRoundRect(left,top,right,bottom,6*d,6*d,paint)
        fun band(radius: Float,color: Int) {
            paint.color=color
            canvas.drawRoundRect(left+span*(ARCADE_TIMING_TARGET-radius),top,
                left+span*(ARCADE_TIMING_TARGET+radius),bottom,4*d,4*d,paint)
        }
        band(ARCADE_TIMING_REACH,0xFF8D7950.toInt())
        band(ARCADE_TIMING_PERFECT,0xFF6ED0B3.toInt())
        paint.color=0xFFF1EBDD.toInt(); paint.textSize=12*resources.displayMetrics.scaledDensity
        paint.textAlign=Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.billiard_arcade_precision),left,18*d,paint)
        paint.textAlign=Paint.Align.CENTER; paint.color=0xFF6ED0B3.toInt()
        canvas.drawText(context.getString(R.string.billiard_arcade_perfect),left+span*ARCADE_TIMING_TARGET,18*d,paint)
        val x=left+span*cursor
        paint.color=0xFFF4E6C3.toInt()
        canvas.drawRoundRect(x-3*d,top-5*d,x+3*d,bottom+5*d,3*d,3*d,paint)
    }
}
