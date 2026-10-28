package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.Canvas
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Une bestiole debout, cadrée dans la vue : le menu, les cartes du Labo et l'écran d'arrivée. */
class PapattesPortraitView(context: Context) : View(context) {
    private val art = PapattesArt(PapattesSprites.get(context))
    private val pose = PapattesPose()
    var critter: Critter = Critter.STARTER
        set(value) { field = value; invalidate() }
    /** Petit balancement, pour le menu. */
    var animated = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val c = critter
        val legs = c.legs
        val standing = PapattesArt.standingHeight(c)
        val scale = min(height * .92f / standing, width * .9f / 1.3f)
        val bob = if (animated) sin(android.os.SystemClock.uptimeMillis() / 420.0).toFloat() * .02f else 0f
        val hipY = height * .97f - (legs.length * cos(PapattesWalker.START_SPREAD) + legs.footRadius) * scale
        pose.standing(c, width / 2f, hipY - bob * scale, scale, PapattesWalker.START_SPREAD + bob * 2f)
        pose.bodyAngle = bob
        art.critter(canvas, c, pose)
        if (animated && isShown) postInvalidateDelayed(33)
    }
}
