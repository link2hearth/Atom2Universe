package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.R

/** Passive HUD: the existing Action button/trigger controls the float without stealing movement touches. */
internal class FishingGaugeView(context: Context): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val fishPath=Path()
    private var gauge=FishingLine.Gauge(false)
    private val title=context.getString(R.string.cave_fishing_reel)
    private val hint=context.getString(R.string.cave_fishing_gauge_hint)
    init { visibility=GONE;isClickable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription=hint }
    fun update(next: FishingLine.Gauge) {
        gauge=next;visibility=if(next.active) VISIBLE else GONE;invalidate()
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d=resources.displayMetrics.density
        p.color=0xEE172C2B.toInt();c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),18*d,18*d,p)
        p.textAlign=Paint.Align.CENTER;p.textSize=14*d;p.typeface=Typeface.DEFAULT_BOLD;p.color=CaveUiStyle.TEXT
        c.drawText(title,width*.5f,24*d,p)
        val top=42*d;val bottom=height-45*d;val track=bottom-top
        val left=width*.24f;val right=width*.66f
        p.color=0xFF0D1E24.toInt();c.drawRoundRect(left,top,right,bottom,10*d,10*d,p)
        val mid=bottom-gauge.zone*track;val half=gauge.radius*track
        p.color=if(gauge.tracking) 0xFF82D3A2.toInt() else 0xFF587E88.toInt()
        c.drawRoundRect(left+3*d,mid-half,right-3*d,mid+half,7*d,7*d,p)
        p.color=0xFF0B1920.toInt();c.drawRoundRect(right+10*d,top,right+19*d,bottom,4*d,4*d,p)
        p.color=0xFFF0C979.toInt();c.drawRoundRect(right+10*d,bottom-gauge.progress*track,right+19*d,bottom,4*d,4*d,p)
        val fy=bottom-gauge.fish*track;val fx=(left+right)/2
        p.color=0xFFF9EEE1.toInt();c.drawOval(fx-10*d,fy-6*d,fx+10*d,fy+6*d,p)
        fishPath.reset();fishPath.moveTo(fx-8*d,fy);fishPath.lineTo(fx-16*d,fy-7*d);fishPath.lineTo(fx-16*d,fy+7*d);fishPath.close();c.drawPath(fishPath,p)
        p.color=0xFF223B40.toInt();c.drawCircle(fx+5*d,fy-1*d,1.6f*d,p)
        p.color=CaveUiStyle.TEXT;p.typeface=Typeface.DEFAULT;p.textSize=11*d
        c.drawText(hint,width*.5f,height-19*d,p)
    }
}
