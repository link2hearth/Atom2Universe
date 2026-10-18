package com.Atom2Universe.app.science.parentes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

/** Symbolic branching motif, not a scientific topology or a species illustration. */
class ParentesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xff102a27.toInt())
        ScienceTileArt.glow(canvas,w*.5f,h*.32f,w*.55f,0xff439a79.toInt(),65)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=w*.01f; strokeCap=Paint.Cap.ROUND }
        fun branch(x:Float,y:Float,spread:Float,depth:Int) {
            if (depth == 0) {
                paint.style=Paint.Style.FILL; paint.color=0xffe7c67e.toInt()
                canvas.drawCircle(x,y,w*.018f,paint)
                return
            }
            for (direction in listOf(-1,1)) {
                val nx=x+spread*direction; val ny=y-h*.15f
                val path=Path().apply { moveTo(x,y); cubicTo(x,y-h*.1f,nx,ny+h*.06f,nx,ny) }
                paint.style=Paint.Style.STROKE; paint.color=0xff80d9b1.toInt()
                canvas.drawPath(path,paint)
                branch(nx,ny,spread*.48f,depth-1)
            }
        }
        branch(w*.5f,h*.68f,w*.23f,3)
        ScienceTileArt.bottomShade(canvas,w,h,0xff102a27.toInt(),.6f)
    }
}
