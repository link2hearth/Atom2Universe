package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.games.billiards.core.TableFamily

/** A resource-free table silhouette for the discipline catalogue. */
class BilliardTableBadge(context: Context,private val family: TableFamily,private val felt: Int=0xFF276757.toInt()): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val r=h*.12f
        p.color=0xFF7B6140.toInt(); c.drawRoundRect(w*.08f,h*.18f,w*.92f,h*.82f,r,r,p)
        p.color=felt or 0xFF000000.toInt(); c.drawRoundRect(w*.13f,h*.25f,w*.87f,h*.75f,r*.5f,r*.5f,p)
        if(family!=TableFamily.CAROM) {
            p.color=0xFF091317.toInt()
            for(x in listOf(.13f,.5f,.87f)) for(y in listOf(.26f,.74f)) c.drawCircle(w*x,h*y,h*.055f,p)
        }
        for(i in 0..2) {
            p.color=when(i) { 0->0xFFF3ECD8.toInt(); 1->0xFFE6B954.toInt(); else->0xFFB73F3A.toInt() }
            c.drawCircle(w*(.3f+i*.18f),h*(if(i==1) .44f else .55f),h*.046f,p)
        }
    }
}
