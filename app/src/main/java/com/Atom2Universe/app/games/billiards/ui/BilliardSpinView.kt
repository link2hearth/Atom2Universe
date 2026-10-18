package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import com.Atom2Universe.app.R
import kotlin.math.*

class BilliardSpinView(context: Context) : View(context) {
    var side=0.0; private set
    var top=0.0; private set
    var changed: ((Double,Double)->Unit)?=null
    var precision=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private var touching=false
    private var startX=0f; private var startY=0f
    private var startSide=0.0; private var startTop=0.0
    private val ballRadius get()=(min(width,height)*.42f).coerceAtLeast(1f)
    init { isFocusable=true }
    fun set(side: Double,top: Double) {
        val scale=if(hypot(side,top)>.8) .8/hypot(side,top) else 1.0
        this.side=side*scale; this.top=top*scale; updateDescription(); invalidate()
    }
    private fun updateDescription() { contentDescription=context.getString(R.string.billiard_spin_value,side,top) }
    override fun onDraw(c: Canvas) {
        val r=ballRadius; val cx=width/2f; val cy=height/2f
        paint.shader=RadialGradient(cx-r*.3f,cy-r*.4f,r*1.9f,intArrayOf(0xFFFFF9E8.toInt(),0xFFD3CAB5.toInt(),0xFF796F60.toInt()),null,Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,r,paint); paint.shader=null
        paint.color=0x33807568; paint.strokeWidth=d
        for(i in -3..3) {
            val offset=r*i*.2f; val extent=sqrt(r*r*.64f-offset*offset)
            c.drawLine(cx-extent,cy+offset,cx+extent,cy+offset,paint)
            c.drawLine(cx+offset,cy-extent,cx+offset,cy+extent,paint)
        }
        paint.color=0x88736558.toInt(); paint.strokeWidth=1.5f*d
        c.drawLine(cx-r*.8f,cy,cx+r*.8f,cy,paint); c.drawLine(cx,cy-r*.8f,cx,cy+r*.8f,paint)
        paint.style=Paint.Style.STROKE; c.drawCircle(cx,cy,r*.8f,paint); paint.style=Paint.Style.FILL
        // Positive side is the player's right along the cue, as in the physics and 3D cue.
        val x=cx+side.toFloat()*r; val y=cy-top.toFloat()*r
        paint.color=0xFF182B30.toInt(); paint.strokeWidth=2*d
        c.drawLine(x-12*d,y,x+12*d,y,paint); c.drawLine(x,y-12*d,x,y+12*d,paint)
        paint.color=Color.WHITE; c.drawCircle(x,y,6*d,paint)
        paint.color=0xFFCF563C.toInt(); c.drawCircle(x,y,3*d,paint)
        if(touching) {
            // A detached magnifier keeps the selected contact visible above the finger.
            val mx=34*d; val my=34*d; val mr=27*d
            c.save(); c.clipPath(Path().apply { addCircle(mx,my,mr,Path.Direction.CW) })
            paint.color=0xFFF5EDD9.toInt(); c.drawCircle(mx,my,mr,paint)
            paint.color=0x55736558; paint.strokeWidth=d
            for(i in -4..4) {
                val gx=mx+(i*.2f-side.toFloat())*r*3
                val gy=my+(i*.2f+top.toFloat())*r*3
                c.drawLine(gx,my-mr,gx,my+mr,paint); c.drawLine(mx-mr,gy,mx+mr,gy,paint)
            }
            paint.color=0xFF182B30.toInt(); c.drawLine(mx-12*d,my,mx+12*d,my,paint); c.drawLine(mx,my-12*d,mx,my+12*d,paint)
            paint.color=0xFFCF563C.toInt(); c.drawCircle(mx,my,3*d,paint); c.restore()
            paint.style=Paint.Style.STROKE; paint.strokeWidth=2*d; paint.color=0xFFE4C488.toInt()
            c.drawCircle(mx,my,mr,paint); paint.style=Paint.Style.FILL
        }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(!isEnabled) return false
        if(e.pointerCount>1 || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            touching=false; parent.requestDisallowInterceptTouchEvent(false); invalidate(); return true
        }
        if(e.actionMasked==MotionEvent.ACTION_DOWN) {
            touching=true; startX=e.x; startY=e.y; startSide=side; startTop=top
        }
        if(touching && (e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_MOVE)) {
            parent.requestDisallowInterceptTouchEvent(true)
            placeTouch(e); return true
        }
        if(e.actionMasked==MotionEvent.ACTION_UP) {
            if(touching) placeTouch(e)
            touching=false; invalidate(); performClick(); parent.requestDisallowInterceptTouchEvent(false); return true
        }
        return true
    }
    private fun placeTouch(e: MotionEvent) {
        val r=ballRadius
        val a=if(precision) startSide+(e.x-startX)/r*.25 else (e.x-width/2f)/r.toDouble()
        val b=if(precision) startTop-(e.y-startY)/r*.25 else -(e.y-height/2f)/r.toDouble()
        set(a,b); changed?.invoke(side,top)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(!isEnabled) return super.onKeyDown(keyCode,event)
        when(keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> set(side-.01,top)
            KeyEvent.KEYCODE_DPAD_RIGHT -> set(side+.01,top)
            KeyEvent.KEYCODE_DPAD_UP -> set(side,top+.01)
            KeyEvent.KEYCODE_DPAD_DOWN -> set(side,top-.01)
            KeyEvent.KEYCODE_DPAD_CENTER -> set(0.0,0.0)
            else -> return super.onKeyDown(keyCode,event)
        }
        changed?.invoke(side,top); return true
    }
}
