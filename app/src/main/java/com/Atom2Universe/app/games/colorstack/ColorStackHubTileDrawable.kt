package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Piles de jetons carrés : même palette et même lecture de bas en haut que le jeu. */
class ColorStackHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        p.shader = LinearGradient(0f, 0f, w, h,
            0xFF303258.toInt(), 0xFF0C192B.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = 0x125EBAD5
        canvas.drawCircle(w * .90f, h * .18f, unit * .34f, p)
        p.color = 0x127F78CF
        canvas.drawCircle(w * .05f, h * .55f, unit * .28f, p)

        val colors = ColorStackGame.COLOR_PALETTE.map { Color.parseColor(it.second) }
        val size = unit * .105f
        val step = unit * .121f
        val bottom = h * .66f
        fun columnX(index: Int) = w * .5f + (index - 1.5f) * unit * .17f
        fun token(x: Float, y: Float, color: Int) {
            val r = size * .13f
            p.color = 0x55040A17
            canvas.drawRoundRect(x - size / 2, y + unit * .009f,
                x + size / 2, y + size + unit * .009f, r, r, p)
            p.color = color
            canvas.drawRoundRect(x - size / 2, y, x + size / 2, y + size, r, r, p)
            p.color = 0x38FFFFFF
            canvas.drawRoundRect(x - size * .43f, y + size * .06f,
                x + size * .43f, y + size * .34f, r * .7f, r * .7f, p)
        }
        // À gauche, couleurs mélangées ; à droite, piles presque triées.
        val board = listOf(listOf(1, 0, 3, 1), listOf(3, 1, 0), listOf(2, 2, 2, 2), listOf(0, 0))
        board.forEachIndexed { index, pile ->
            val x = columnX(index)
            val left = x - size * .65f
            val right = x + size * .65f
            val top = bottom - 4 * step - unit * .008f
            p.color = if (index == 2) 0xFF14382F.toInt() else 0xFF0F172A.toInt()
            canvas.drawRoundRect(left, top, right, bottom + unit * .016f,
                size * .18f, size * .18f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = unit * .004f
            p.color = if (index == 2) 0xFF4ADE80.toInt() else 0xFF41516F.toInt()
            canvas.drawRoundRect(left, top, right, bottom + unit * .016f,
                size * .18f, size * .18f, p)
            p.style = Paint.Style.FILL
            pile.forEachIndexed { row, color -> token(x, bottom - size - row * step, colors[color]) }
        }

        // Un jeton rouge rejoint la pile rouge : aperçu du geste de tri.
        val movingX = columnX(2) + unit * .04f
        val movingY = bottom - 4 * step - unit * .135f
        val arrow = Path().apply {
            moveTo(columnX(1), bottom - 3 * step - unit * .025f)
            cubicTo(columnX(1), movingY - unit * .025f, columnX(3), movingY - unit * .025f,
                columnX(3), bottom - 2 * step - unit * .07f)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = unit * .007f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0x99FFCFCC.toInt()
        canvas.drawPath(arrow, p)
        val endY = bottom - 2 * step - unit * .07f
        canvas.drawLine(columnX(3) - unit * .021f, endY - unit * .025f, columnX(3), endY, p)
        canvas.drawLine(columnX(3) + unit * .021f, endY - unit * .025f, columnX(3), endY, p)
        p.style = Paint.Style.FILL
        canvas.save()
        canvas.rotate(12f, movingX, movingY + size / 2)
        token(movingX, movingY, colors[0])
        canvas.restore()

        p.shader = LinearGradient(0f, h * .60f, 0f, h,
            Color.TRANSPARENT, 0xEF0C1226.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .60f, w, h, p)
    }
}
