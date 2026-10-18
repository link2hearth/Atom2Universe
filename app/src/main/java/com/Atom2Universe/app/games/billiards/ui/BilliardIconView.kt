package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.*
import android.view.View

/** Small vector controls that belong to the scene, with 48 dp touch targets. */
class BilliardIconView(context: Context,val icon: Icon): View(context) {
    enum class Icon { MENU, CUE, ORBIT, TOP, SPIN, PLACE, BACK }
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var active=false; set(value) { field=value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val scale=minOf(width,height)/48f
        canvas.save(); canvas.scale(scale,scale)
        p.style=Paint.Style.FILL; p.color=if(active) 0xE6E5C384.toInt() else 0xD9111C23.toInt()
        canvas.drawRoundRect(1f,1f,47f,47f,15f,15f,p)
        p.color=if(active) 0xFF17252A.toInt() else 0xFFEDE4D0.toInt()
        p.style=Paint.Style.STROKE; p.strokeWidth=1.7f; p.strokeCap=Paint.Cap.ROUND
        fun line(x: Float,y: Float,a: Float,b: Float)=canvas.drawLine(x,y,a,b,p)
        when(icon) {
            Icon.MENU -> for(y in listOf(17f,24f,31f)) line(15f,y,33f,y)
            Icon.BACK -> { line(29f,15f,20f,24f); line(20f,24f,29f,33f) }
            Icon.TOP -> { canvas.drawRoundRect(12f,17f,36f,31f,2f,2f,p); canvas.drawCircle(27f,23f,2f,p) }
            Icon.CUE -> { canvas.drawCircle(29f,20f,5f,p); line(13f,35f,26f,23f); line(15f,37f,28f,25f) }
            Icon.SPIN -> { canvas.drawCircle(24f,24f,11f,p); line(24f,16f,24f,32f); line(16f,24f,32f,24f); p.style=Paint.Style.FILL; canvas.drawCircle(28f,20f,3f,p) }
            Icon.PLACE -> { canvas.drawCircle(24f,24f,5f,p); line(24f,10f,24f,16f); line(24f,32f,24f,38f); line(10f,24f,16f,24f); line(32f,24f,38f,24f) }
            Icon.ORBIT -> { canvas.drawOval(11f,18f,37f,31f,p); line(32f,15f,37f,20f); line(37f,20f,31f,22f); canvas.drawCircle(24f,23f,4f,p) }
        }
        p.style=Paint.Style.FILL; canvas.restore()
    }
}
