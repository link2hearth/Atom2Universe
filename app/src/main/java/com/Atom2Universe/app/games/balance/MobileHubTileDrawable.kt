package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/**
 * Un petit mobile de Calder sur papier, pour la carte du menu : trois tiges, quatre disques, tout
 * juste — 2 au cran 2 contre 4 au cran 1, puis 6 contre 3 + 3, à crans égaux.
 */
class MobileHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFFF4EEDF.toInt(), 0xFFE6DCC6.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val u = min(w, h * 1.3f) / 7f
        val ink = 0xFF211C19.toInt()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = ink
        }
        p.color = ink
        canvas.drawRect(0f, 0f, w, h * 0.04f, p)

        fun disque(x: Float, y: Float, masse: Int, couleur: Int) {
            val r = u * 0.28f * kotlin.math.sqrt(masse.toFloat())
            line.strokeWidth = u * 0.03f
            canvas.drawLine(x, y, x, y + u * 0.55f, line)
            p.color = couleur
            canvas.drawCircle(x, y + u * 0.55f + r, r, p)
        }

        /** Une tige au nœud ([nx], [ny]), bras gauche et droit en crans ; rend les deux bouts. */
        fun tige(nx: Float, ny: Float, gauche: Int, droite: Int, cran: Float): FloatArray {
            val y = ny + u * 0.45f
            line.strokeWidth = u * 0.03f
            canvas.drawLine(nx, ny, nx - u * 0.18f, y, line)
            canvas.drawLine(nx, ny, nx + u * 0.18f, y, line)
            // En maillons, un par cran, comme dans le jeu.
            line.strokeWidth = u * 0.1f
            val jeu = u * 0.08f
            for (k in -gauche until droite) {
                val x0 = nx + k * cran + if (k == -gauche) 0f else jeu
                val x1 = nx + (k + 1) * cran - if (k + 1 == droite) 0f else jeu
                canvas.drawLine(x0, y, x1, y, line)
            }
            p.color = 0xFFF4EEDF.toInt()
            canvas.drawCircle(nx, ny, u * 0.1f, p)
            line.strokeWidth = u * 0.035f
            canvas.drawCircle(nx, ny, u * 0.1f, line)
            return floatArrayOf(nx - gauche * cran, y, nx + droite * cran, y)
        }

        val cx = w * 0.5f
        val top = h * 0.04f
        line.strokeWidth = u * 0.03f
        canvas.drawLine(cx, top, cx, top + u * 0.5f, line)
        val haut = tige(cx, top + u * 0.5f, 1, 1, u * 1.55f)
        // À gauche : 2 au cran 2 contre 4 au cran 1.
        line.strokeWidth = u * 0.03f
        canvas.drawLine(haut[0], haut[1], haut[0], haut[1] + u * 0.5f, line)
        val g = tige(haut[0], haut[1] + u * 0.5f, 2, 1, u * 0.45f)
        disque(g[0], g[1], 2, 0xFFD63A2F.toInt())
        disque(g[2], g[3], 4, 0xFF2F5DA8.toInt())
        // À droite : 3 contre 3.
        line.strokeWidth = u * 0.03f
        canvas.drawLine(haut[2], haut[3], haut[2], haut[3] + u * 0.5f, line)
        val d = tige(haut[2], haut[3] + u * 0.5f, 1, 1, u * 0.65f)
        disque(d[0], d[1], 3, 0xFFF2B705.toInt())
        disque(d[2], d[3], 3, ink)
    }
}
