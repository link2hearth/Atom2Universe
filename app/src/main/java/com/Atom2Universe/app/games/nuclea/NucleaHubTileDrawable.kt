package com.Atom2Universe.app.games.nuclea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

/** Composition fixe des visuels de NucleaView, sans créer sa SurfaceView ni sa partie.
 * Les couleurs et symboles des éléments proviennent directement du catalogue du jeu.
 */
class NucleaHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        fun glow(x: Float, y: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
            paint.shader = null
        }
        canvas.drawColor(0xFF07070F.toInt())
        glow(w * .28f, h * .20f, w * .60f, 0x705B3399)
        glow(w * .78f, h * .27f, w * .42f, 0x503078C8)
        val random = Random(2706)
        repeat(65) {
            paint.color = Color.argb(65 + random.nextInt(130), 220, 218, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h,
                unit * (.002f + random.nextFloat() * .003f), paint)
        }

        // Même corps sombre, halo, anneau et symbole que les atomes du jeu.
        fun atom(tier: Int, x: Float, y: Float, radius: Float) {
            val color = NucleaGame.TIER_COLORS[tier]
            glow(x, y, radius * 1.65f, (color and 0x00FFFFFF) or 0x65000000)
            paint.color = Color.rgb((Color.red(color) * .55f).toInt(),
                (Color.green(color) * .55f).toInt(), (Color.blue(color) * .55f).toInt())
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = unit * .009f
            paint.color = color
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = radius * .85f
            canvas.drawText(NucleaGame.SYMBOLS[tier], x, y + radius * .3f, paint)
        }
        atom(0, w * .13f, h * .27f, unit * .11f)
        atom(1, w * .29f, h * .43f, unit * .13f)
        atom(3, w * .73f, h * .26f, unit * .14f)
        atom(4, w * .88f, h * .47f, unit * .105f)

        // Proto-étoile du joueur, cœur crème et deux boucliers de quarks bleus.
        val x = w * .50f
        val y = h * .28f
        val radius = unit * .12f
        glow(x, y, radius * 2.6f, 0xA0FFDC8C.toInt())
        paint.color = 0xFFFFE9B8.toInt()
        canvas.drawCircle(x, y, radius, paint)
        paint.color = 0xFFFFF7E6.toInt()
        canvas.drawCircle(x, y, radius * .55f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * .009f
        paint.color = 0xFF44CCFF.toInt()
        canvas.drawCircle(x, y, radius * 1.40f, paint)
        paint.color = 0x8044CCFF.toInt()
        canvas.drawCircle(x, y, radius * 1.70f, paint)
        paint.style = Paint.Style.FILL

        // Réserver la partie basse au seul titre natif, identique aux autres tuiles.
        paint.shader = LinearGradient(0f, h * .48f, 0f, h,
            Color.TRANSPARENT, 0xF007070F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .48f, w, h, paint)
    }
}
