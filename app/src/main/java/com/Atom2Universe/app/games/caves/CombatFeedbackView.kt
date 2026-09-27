package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View

/** A small charge ring around the reticle and a brief marker only on confirmed melee contact. */
internal class CombatFeedbackView(context: Context) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND }
    private val ring=RectF()
    private val density=resources.displayMetrics.density
    private var charge=0
    private var hitUntil=0L
    private var heavy=false
    init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun charge(percent: Int) { if(charge!=percent) { charge=percent;invalidate() } }
    fun hit(strong: Boolean) {
        heavy=strong;hitUntil=SystemClock.uptimeMillis()+180
        performHapticFeedback(if(strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x=width*.5f;val y=height*.5f
        if(charge>0) {
            val radius=23*density
            ring.set(x-radius,y-radius,x+radius,y+radius)
            paint.strokeWidth=3*density;paint.color=0x557C847A;paint.alpha=170
            canvas.drawOval(ring,paint)
            paint.color=if(charge>=100) 0xFFFFD06B.toInt() else 0xFFDDEBC8.toInt();paint.alpha=255
            canvas.drawArc(ring,-90f,charge*3.6f,false,paint)
        }
        val remaining=hitUntil-SystemClock.uptimeMillis()
        if(remaining<=0) return
        paint.color=if(heavy) 0xFFFFC158.toInt() else 0xFFFFFBE8.toInt()
        paint.alpha=(remaining/180f*255).toInt().coerceIn(0,255)
        paint.strokeWidth=(if(heavy) 3f else 2f)*density
        val inner=7*density;val outer=(if(heavy) 17f else 13f)*density
        for(sx in intArrayOf(-1,1)) for(sy in intArrayOf(-1,1))
            canvas.drawLine(x+inner*sx,y+inner*sy,x+outer*sx,y+outer*sy,paint)
        postInvalidateOnAnimation()
    }
}
