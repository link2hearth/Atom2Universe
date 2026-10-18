package com.Atom2Universe.app.games.infernale

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/**
 * Illustration de la tuile Machine infernale : une réaction en chaîne figée à mi-course,
 * dans la caverne du jeu. La bille dévale la rampe, les dominos tombent, la bascule attend.
 *
 * Mêmes couleurs que le tableau ([InfernaleView]) et la réserve ([InfernaleVignette]) : le
 * bois, l'ivoire et le fer de la tuile sont ceux que le joueur retrouve en jeu. Le bas reste
 * libre pour le titre.
 */
class InfernaleHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = min(w, h)

        // La caverne, et la lueur de la torche qui l'éclaire par la droite.
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF080B16.toInt(), 0xFF1B1526.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        val torchX = w * .86f
        val torchY = h * .20f
        p.shader = RadialGradient(torchX, torchY, u * .55f,
            intArrayOf(0x55FFB33C, 0x18FFB33C, 0x00FFB33C), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // La rampe, en haut à gauche, et la bille qui la dévale.
        canvas.save()
        canvas.rotate(18f, w * .24f, h * .24f)
        p.color = 0xFFB27C48.toInt()
        canvas.drawRect(w * .02f, h * .24f - u * .025f, w * .46f, h * .24f + u * .025f, p)
        p.color = 0xFF7C5430.toInt()
        canvas.drawRect(w * .02f, h * .24f, w * .46f, h * .24f + u * .025f, p)
        canvas.restore()
        p.color = 0xFF4E5568.toInt()
        canvas.drawRect(w * .08f, h * .20f, w * .08f + u * .03f, h * .52f, p)
        val ballX = w * .36f
        val ballY = h * .25f
        p.color = 0xFFD8DEEC.toInt()
        canvas.drawCircle(ballX, ballY, u * .06f, p)
        p.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(ballX - u * .02f, ballY - u * .022f, u * .02f, p)

        // Le sol de la machine : une planche où tombent les dominos.
        val floor = h * .56f
        p.color = 0xFF7C5430.toInt()
        canvas.drawRect(w * .30f, floor, w * .96f, floor + u * .03f, p)

        // Les dominos : le premier déjà couché sur le suivant, les autres encore debout.
        val dominoW = u * .045f
        val dominoH = u * .19f
        val tilts = floatArrayOf(58f, 34f, 12f, 0f, 0f)
        tilts.forEachIndexed { i, tilt ->
            val x = w * .40f + i * u * .085f
            canvas.save()
            canvas.rotate(tilt, x + dominoW, floor)
            p.color = 0xFFBFB6A0.toInt()
            canvas.drawRect(x + dominoW * .55f, floor - dominoH, x + dominoW, floor, p)
            p.color = 0xFFE9E2CE.toInt()
            canvas.drawRect(x, floor - dominoH, x + dominoW * .55f, floor, p)
            p.color = 0xFF3A3226.toInt()
            canvas.drawCircle(x + dominoW * .5f, floor - dominoH * .72f, dominoW * .16f, p)
            canvas.drawCircle(x + dominoW * .5f, floor - dominoH * .28f, dominoW * .16f, p)
            canvas.restore()
        }

        // La bascule au bout de la chaîne, et le butoir rouge qu'elle frappera.
        val pivotX = w * .86f
        p.color = 0xFF4E5568.toInt()
        canvas.drawRect(pivotX - u * .02f, floor - u * .10f, pivotX + u * .02f, floor, p)
        canvas.save()
        canvas.rotate(-12f, pivotX, floor - u * .10f)
        p.color = 0xFFB27C48.toInt()
        canvas.drawRect(pivotX - u * .16f, floor - u * .12f, pivotX + u * .16f, floor - u * .09f, p)
        canvas.restore()
        p.color = 0xFFC03A4E.toInt()
        canvas.drawCircle(w * .66f, h * .16f, u * .055f, p)
        p.color = 0xFFFF8676.toInt()
        canvas.drawCircle(w * .66f, h * .16f, u * .034f, p)

        // La torche elle-même.
        p.color = 0xFF4E5568.toInt()
        canvas.drawRect(torchX - u * .015f, torchY, torchX + u * .015f, torchY + u * .14f, p)
        p.color = 0xFFFFB33C.toInt()
        canvas.drawCircle(torchX, torchY - u * .01f, u * .042f, p)
        p.color = 0xFFE9E2CE.toInt()
        canvas.drawCircle(torchX, torchY - u * .02f, u * .02f, p)
    }
}
