package com.Atom2Universe.app.games.jigsaw

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

class JigsawHubTileDrawable(private val context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val artwork = JigsawMysteryDrawable(KitPalette.artwork(0xFF6C94EF.toInt()), context.getString(R.string.jigsaw_mystery_mark))
        artwork.setBounds(0, 0, w.toInt(), h.toInt())
        artwork.draw(canvas)
    }
}
