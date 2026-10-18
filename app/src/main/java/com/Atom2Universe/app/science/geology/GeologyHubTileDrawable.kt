package com.Atom2Universe.app.science.geology

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

class GeologyHubTileDrawable(@Suppress("UNUSED_PARAMETER") context:Context):CachedHubArtworkDrawable() {
    override fun render(canvas:Canvas,w:Float,h:Float) {
        canvas.drawColor(0xff15262c.toInt())
        canvas.save();canvas.translate(w*.13f,-h*.03f);canvas.scale(w/750f,h/800f)
        EarthArt().render(canvas,EarthScene.GLOBE,.65f,0f,false)
        canvas.restore()
        ScienceTileArt.bottomShade(canvas,w,h,0xff15262c.toInt(),.58f)
    }
}
