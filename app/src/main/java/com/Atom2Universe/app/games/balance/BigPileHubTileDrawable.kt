package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Carte du mode « Gros tas » : une large balance, deux tas de petites pièces, une pièce qui descend. */
class BigPileHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF070B18.toInt(), 0xFF101A33.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val unit = min(w, h)
        val cx = w * .5f
        val groundY = h * .66f
        val plankY = h * .52f
        val plankW = min(w * .86f, h * 1.8f)
        val plankH = unit * .04f
        val cell = plankW / 14f

        // Grille qui descend, à peine visible.
        paint.color = 0x22A0B8E0
        paint.strokeWidth = unit * .004f
        var x = cx - cell * 8f
        while (x <= cx + cell * 8f + 1f) {
            canvas.drawLine(x, h * .04f, x, plankY, paint)
            x += cell
        }

        // Appui large.
        val support = Path().apply {
            moveTo(cx - unit * .07f, plankY + plankH)
            lineTo(cx + unit * .07f, plankY + plankH)
            lineTo(cx + unit * .19f, groundY)
            lineTo(cx - unit * .19f, groundY)
            close()
        }
        paint.color = 0xFF546E7A.toInt(); canvas.drawPath(support, paint)
        paint.color = 0xFF141C30.toInt(); canvas.drawRect(0f, groundY, w, h, paint)
        paint.color = 0xFF8D6E63.toInt()
        canvas.drawRoundRect(cx - plankW / 2f, plankY, cx + plankW / 2f, plankY + plankH,
            unit * .012f, unit * .012f, paint)

        fun block(col: Float, row: Float, color: Int) {
            val left = cx + col * cell
            val top = plankY - (row + 1f) * cell
            paint.color = color
            canvas.drawRoundRect(left + 1f, top + 1f, left + cell - 1f, top + cell - 1f, cell * .2f, cell * .2f, paint)
            paint.color = 0x40FFFFFF
            canvas.drawRoundRect(left + cell * .15f, top + cell * .15f, left + cell * .85f, top + cell * .38f,
                cell * .1f, cell * .1f, paint)
        }
        val yellow = 0xFFFFF176.toInt(); val green = 0xFF81C784.toInt(); val blue = 0xFF4FC3F7.toInt()
        val red = 0xFFE57373.toInt(); val purple = 0xFFBA68C8.toInt(); val orange = 0xFFFFB74D.toInt()
        // Tas de gauche.
        block(-6f, 0f, blue); block(-5f, 0f, blue); block(-6f, 1f, blue); block(-5f, 1f, blue)
        block(-4f, 0f, red); block(-3f, 0f, red); block(-2f, 0f, red); block(-3f, 1f, red)
        block(-5f, 2f, yellow); block(-4f, 1f, green); block(-4f, 2f, green)
        // Tas de droite.
        block(2f, 0f, purple); block(3f, 0f, purple); block(4f, 0f, purple); block(4f, 1f, purple)
        block(5f, 0f, orange); block(5f, 1f, orange); block(6f, 0f, orange)
        block(2f, 1f, green); block(3f, 1f, green); block(3f, 2f, yellow)
        // Une pièce en chemin.
        block(-1f, 4.5f, red); block(0f, 4.5f, red); block(0f, 5.5f, red); block(1f, 5.5f, red)
    }
}
