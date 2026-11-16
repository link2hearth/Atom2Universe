package com.Atom2Universe.app.games.enamel

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

/**
 * Jauge de charge posée sur la limite de la base, en unités de planche (largeur 10).
 * Un rail creusé dans le bois (lèvre claire dessous, bord sombre, fond creux) reçoit un
 * remplissage vernis qui passe du jaune au rouge : la couleur de la pointe dit la force.
 */
class PowerBarPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Le dégradé couvre tout le rail : la couleur de la pointe dit la force.
    private val fill = LinearGradient(L, 0f, R, 0f, intArrayOf(YELLOW, AMBER, ORANGE, RED),
        floatArrayOf(0f, .35f, .7f, 1f), Shader.TileMode.CLAMP)
    // Fond du rail, plus sombre en haut : une rainure creusée dans le bois.
    private val trough = LinearGradient(0f, 0f, 0f, 1f, 0xFF031012.toInt(), 0xFF1E4649.toInt(), Shader.TileMode.CLAMP)
    // Vernis bombé du remplissage : reflet clair en haut, ombre chaude en bas.
    private val gloss = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(0xB0FFFFFF.toInt(), 0x38FFFFFF, 0x00FFFFFF, 0x00300000, 0x60300000),
        floatArrayOf(0f, .28f, .48f, .6f, 1f), Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private var shadedY = Float.NaN

    fun draw(c: Canvas, y: Float, power: Float) {
        val k = power.coerceIn(0f, 1f)
        val top = y - HEIGHT / 2; val bottom = y + HEIGHT / 2
        if (y != shadedY) {
            // Les dégradés verticaux suivent la jauge ; on ne les recale que si elle bouge.
            shadedY = y
            matrix.setScale(1f, HEIGHT); matrix.postTranslate(0f, top)
            trough.setLocalMatrix(matrix); gloss.setLocalMatrix(matrix)
        }
        val tip = maxOf(L + (R - L) * k, L + HEIGHT)
        p.style = Paint.Style.FILL; p.shader = null

        // Rail : lèvre claire sous la rainure, bord sombre net, fond creux.
        val outer = HEIGHT / 2 + RIM
        p.color = LIP; c.drawRoundRect(L - RIM, top - RIM + .035f, R + RIM, bottom + RIM + .035f, outer, outer, p)
        p.color = RAIL; c.drawRoundRect(L - RIM, top - RIM, R + RIM, bottom + RIM, outer, outer, p)
        p.shader = trough; p.color = WHITE
        c.drawRoundRect(L, top, R, bottom, HEIGHT / 2, HEIGHT / 2, p)
        p.shader = null
        // Graduations claires dans le creux (le remplissage les recouvre en avançant).
        p.color = 0x40FFFFFF
        for (i in 1..3) tick(c, i, top, bottom)
        if (k <= 0f) return

        // Remplissage vernis.
        p.color = WHITE; p.shader = fill
        c.drawRoundRect(L, top, tip, bottom, HEIGHT / 2, HEIGHT / 2, p)
        p.shader = gloss
        c.drawRoundRect(L, top, tip, bottom, HEIGHT / 2, HEIGHT / 2, p)
        p.shader = null
        // Graduations sombres sous le remplissage.
        p.color = 0x50300800
        for (i in 1..3) if (L + (R - L) * i / 4f < tip - HEIGHT / 2) tick(c, i, top, bottom)

        // Pointe : un trait blanc net au bout du remplissage.
        p.color = WHITE; p.alpha = 240
        c.drawRoundRect(tip - .075f, top + .04f, tip - .03f, bottom - .04f, .02f, .02f, p)
        p.alpha = 255
    }

    private fun tick(c: Canvas, i: Int, top: Float, bottom: Float) {
        val x = L + (R - L) * i / 4f
        c.drawRect(x - .012f, top + .05f, x + .012f, bottom - .05f, p)
    }

    private companion object {
        /** Épaisseur du remplissage, en unités de planche. */
        const val HEIGHT = .26f
        /** Épaisseur du bord sombre autour du remplissage. */
        const val RIM = .05f
        const val L = .2f
        const val R = 9.8f
        val WHITE = 0xFFFFFFFF.toInt()
        val RAIL = 0xFF0C2426.toInt()
        val LIP = 0xB0FFF8E6.toInt()
        val YELLOW = 0xFFFFE34A.toInt()
        val AMBER = 0xFFFFB321.toInt()
        val ORANGE = 0xFFFF6A1A.toInt()
        val RED = 0xFFE5231B.toInt()
    }
}
