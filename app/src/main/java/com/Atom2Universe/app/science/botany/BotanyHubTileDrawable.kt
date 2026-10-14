package com.Atom2Universe.app.science.botany

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.min

class BotanyHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun render(canvas: Canvas,w:Float,h:Float) {
        val forest=0xff12291e.toInt()
        canvas.drawColor(forest)
        ScienceTileArt.glow(canvas,w*.5f,h*.35f,w*.8f,0xff91ab57.toInt(),80)
        fun specimen(id:String,x:Float,y:Float,width:Float,height:Float) {
            val bitmap=PlantHerbarium.decode(PlantImageKey(id,PlantView.HABIT,384,true,true))
            val frame=RectF(x,y,x+width,y+height)
            canvas.drawRoundRect(frame,10f,10f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xfff4f0e3.toInt() })
            val scale=min(width/bitmap.width,height/bitmap.height)
            val drawnWidth=bitmap.width*scale
            val drawnHeight=bitmap.height*scale
            val target=RectF(x+(width-drawnWidth)/2f,y+(height-drawnHeight)/2f,
                x+(width+drawnWidth)/2f,y+(height+drawnHeight)/2f)
            canvas.drawBitmap(bitmap,null,target,platePaint)
        }
        specimen("robur",w*.035f,h*.09f,w*.31f,h*.70f)
        specimen("papaver",w*.345f,h*.09f,w*.31f,h*.70f)
        specimen("leucanthemum",w*.655f,h*.09f,w*.31f,h*.70f)
        ScienceTileArt.bottomShade(canvas,w,h,forest,.62f)
    }
}
