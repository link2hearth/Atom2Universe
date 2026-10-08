package com.Atom2Universe.app.games.billiards

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Procedural illustration; never initializes the simulation or an EGL context. */
class BilliardHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(c: Canvas,w: Float,h: Float) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,w,h,0xFF131C24.toInt(),0xFF35291F.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p); p.shader=null
        val r=RectF(w*.09f,h*.16f,w*.91f,h*.67f)
        p.color=0xFF68462C.toInt(); c.drawRoundRect(r,w*.035f,w*.035f,p)
        val felt=RectF(r).apply { inset(w*.035f,h*.035f) }
        p.shader=LinearGradient(felt.left,felt.top,felt.right,felt.bottom,0xFF267F66.toInt(),0xFF123C33.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(felt,w*.02f,w*.02f,p); p.shader=null
        p.color=0xFF101518.toInt()
        for(x in floatArrayOf(felt.left,felt.right)) for(y in floatArrayOf(felt.top,felt.bottom)) c.drawCircle(x,y,w*.023f,p)
        for(y in floatArrayOf(felt.top,felt.bottom)) c.drawCircle(w/2,y,w*.023f,p)
        val size=min(w,h)*.042f
        fun ball(x: Float,y: Float,color: Int) {
            p.color=0x880C1514.toInt(); c.drawOval(x-size,y+size*.4f,x+size,y+size*1.4f,p)
            p.shader=RadialGradient(x-size*.3f,y-size*.4f,size*1.8f,intArrayOf(0xFFFFFFFF.toInt(),color,0xFF17201E.toInt()),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
            c.drawCircle(x,y,size,p); p.shader=null
        }
        ball(w*.36f,h*.45f,0xFFE9E2CA.toInt()); ball(w*.68f,h*.34f,0xFFC83E32.toInt()); ball(w*.65f,h*.52f,0xFFDDC138.toInt())
        p.color=0xFFDDC69B.toInt(); p.strokeWidth=size*.30f
        c.drawLine(w*.16f,h*.57f,w*.33f,h*.46f,p)
        p.color=0xAACCE6B6.toInt(); p.strokeWidth=1.5f
        c.drawLine(w*.4f,h*.44f,w*.63f,h*.36f,p)
    }
}
