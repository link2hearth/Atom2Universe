package com.Atom2Universe.app.science.botany

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

class BotanyHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas,w:Float,h:Float) {
        val forest=0xff12291e.toInt()
        canvas.drawColor(forest)
        ScienceTileArt.glow(canvas,w*.5f,h*.35f,w*.8f,0xff91ab57.toInt(),80)
        fun specimen(id:String,mode:PlantView,x:Float,y:Float,scale:Float) {
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale)
            BotanyCatalog.get(id)?.let { PlantPainter(canvas,it,compact=true).draw(mode) };canvas.restore()
        }
        specimen("robur",PlantView.LEAF,-w*.09f,-h*.04f,w/440f)
        specimen("papaver",PlantView.FLOWER,w*.34f,h*.05f,w/570f)
        specimen("leucanthemum",PlantView.FLOWER,w*.13f,h*.29f,w/650f)
        ScienceTileArt.bottomShade(canvas,w,h,forest,.62f)
    }
}
