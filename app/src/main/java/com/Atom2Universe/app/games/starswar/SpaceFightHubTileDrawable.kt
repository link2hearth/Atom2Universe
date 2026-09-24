package com.Atom2Universe.app.games.starswar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.min
import kotlin.random.Random

/** Décor statique : rendu du jeu utilisé une fois, puis seule l'image est conservée.
 * Cache partagé entre les hubs et leurs recréations, borné à 2 Mio, sans contexte d'activité.
 */
class SpaceFightHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) :
    com.Atom2Universe.app.hub.CachedHubArtworkDrawable() {

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = SpaceFightArt()
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = min(w, h)
        canvas.save()
        canvas.scale(w / 480f, h / 720f)
        art.background(canvas, 1)
        canvas.restore()

        val random = Random(7319)
        repeat(65) { i ->
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            brush.color = SpaceFightArt.alpha(SpaceFightArt.IVORY, 65 + random.nextInt(130))
            canvas.drawCircle(x, y, size * if (i % 7 == 0) .004f else .002f, brush)
        }
        art.sparkle(canvas, w * .12f, h * .19f, size * .018f, SpaceFightArt.MINT)
        art.sparkle(canvas, w * .88f, h * .62f, size * .014f, SpaceFightArt.GOLD)

        // Les silhouettes encadrent le titre central, y compris sur les raccourcis étroits.
        art.enemy(canvas, 1, w * .20f, h * .23f, size * .25f, .5f, 0f, 0f)
        art.enemy(canvas, 0, w * .73f, h * .20f, size * .22f, 1f, 0f, 0f)
        art.enemy(canvas, 4, w * .86f, h * .39f, size * .19f, 2f, 0f, 0f)
        art.player(canvas, w * .43f, h * .28f, size * .43f, -16f, true)

        // Bande douce derrière le libellé : aucun texte n'est intégré au bitmap.
        brush.shader = LinearGradient(0f, h * .35f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xC0141C31.toInt(), 0xE0141C31.toInt()),
            floatArrayOf(0f, .40f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .35f, w, h, brush)
    }

}
