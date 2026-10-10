package com.Atom2Universe.app.science.mycology

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.max

/** Tuile du hub : trois vrais dessins de l'atlas (cèpe, girolle, tue-mouches) dans un sous-bois sombre. */
class MycologyHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val forest = 0xff0f2018.toInt()
        canvas.drawColor(forest)
        ScienceTileArt.glow(canvas, w * .5f, h * .30f, w * .8f, 0xff4fa36a.toInt(), 75)
        val hair = max(1f, w / 280f)
        fun mushroom(id: String, cx: Float, base: Float, pxPerCm: Float) {
            FungusCatalog.get(id)?.look?.let { SpecimenPainter(canvas, it, cx, base, pxPerCm, hair).side() }
        }
        val base = h * 0.70f
        mushroom("edulis", w * .27f, base, h * .029f)
        mushroom("muscaria", w * .73f, base + h * .03f, h * .030f)
        mushroom("cibarius", w * .50f, base + h * .085f, h * .047f)
        ScienceTileArt.bottomShade(canvas, w, h, forest, .58f)
    }
}
