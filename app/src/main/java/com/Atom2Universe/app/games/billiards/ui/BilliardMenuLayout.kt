package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration

/** Menu chrome only. The GL surface stays attached to its separate parent behind this view. */
internal class BilliardMenuLayout(context: Context,private val viewport: (Rect)->Unit): ViewGroup(context) {
    private var heading: View?=null
    private var panel: View?=null
    private var panelRect=Rect()
    private var lastViewport=Rect()
    var panelVisible=true; private set
    var visibilityChanged: ((Boolean)->Unit)?=null
    private var downX=0f; private var downY=0f; private var moved=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private fun dp(n: Int)=(n*resources.displayMetrics.density+.5f).toInt()

    fun content(header: View,body: View) {
        removeAllViews(); heading=header; panel=body; panelVisible=true
        addView(header); addView(body); visibilityChanged?.invoke(true); requestLayout()
    }
    fun toggle() { setPanelVisible(!panelVisible) }
    fun setPanelVisible(visible: Boolean) {
        if(panelVisible==visible) return
        panelVisible=visible; panel?.visibility=if(visible) VISIBLE else GONE
        visibilityChanged?.invoke(visible); requestLayout()
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec); val height=MeasureSpec.getSize(heightMeasureSpec)
        val margin=dp(12); val inner=(width-2*margin).coerceAtLeast(0)
        heading?.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height/3,MeasureSpec.AT_MOST))
        val top=margin+(heading?.measuredHeight ?: 0)+dp(8)
        val available=(height-top-margin).coerceAtLeast(0)
        val landscape=width>height && width>=dp(560)
        val panelWidth=if(landscape) minOf(dp(420),(width*.49f).toInt()) else inner
        // Keep actual table space; the menu itself scrolls instead of covering the whole scene.
        val panelHeight=if(landscape) available else (available*.60f).toInt()
        panelRect=Rect(width-margin-panelWidth,height-margin-panelHeight,width-margin,height-margin)
        panel?.measure(MeasureSpec.makeMeasureSpec(panelWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(panelHeight,MeasureSpec.EXACTLY))
        setMeasuredDimension(width,height)
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        val margin=dp(12); val top=margin+(heading?.measuredHeight ?: 0)+dp(8)
        heading?.layout(margin,margin,width-margin,top-dp(8))
        panel?.layout(panelRect.left,panelRect.top,panelRect.right,panelRect.bottom)
        val landscape=width>height && width>=dp(560)
        val area=Rect(margin,top,
            if(panelVisible && landscape) panelRect.left-dp(8) else width-margin,
            if(panelVisible && !landscape) panelRect.top-dp(8) else height-margin)
        if(area!=lastViewport) { lastViewport=Rect(area); viewport(area) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Outside taps close on UP. The entire gesture stays here, away from the table.
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; moved=false }
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> moved=true
            MotionEvent.ACTION_MOVE -> if(kotlin.math.abs(event.x-downX)+kotlin.math.abs(event.y-downY)>slop) moved=true
            MotionEvent.ACTION_UP -> if(!moved) { performClick(); setPanelVisible(false) }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
}
