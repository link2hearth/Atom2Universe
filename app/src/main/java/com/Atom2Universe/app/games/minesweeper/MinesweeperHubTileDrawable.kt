package com.Atom2Universe.app.games.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.Atom2Universe.app.R
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Instantané du plateau Abysses, avec les mêmes balises et mines que le jeu. */
class MinesweeperHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val digits = Array(9) { context.getString(R.string.minesweeper_number, it) }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF071A26.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val rect = RectF()
        val path = Path()
        val cell = min(w / 5.6f, h * .34f)
        val left = (w - cell * 6f) / 2f
        val top = -cell * .12f
        val mines = setOf(0 to 2, 1 to 4, 3 to 1)
        for (r in 0..(h / cell).toInt() + 1) for (c in 0..5) {
            val x = left + c * cell
            val y = top + r * cell
            val flagged = r == 0 && c == 2
            val mine = r == 1 && c == 4
            val hidden = flagged || (c >= 3 && !mine) || r >= 2
            rect.set(x + cell * .045f, y + cell * .045f, x + cell * .955f, y + cell * .955f)
            paint.style = Paint.Style.FILL
            paint.color = if (hidden) MinesweeperArt.navy else 0xFF071A26.toInt()
            canvas.drawRoundRect(rect, cell * .12f, cell * .12f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cell * .015f
            paint.color = if (flagged) MinesweeperArt.amber else 0xFF2B4F60.toInt()
            canvas.drawRoundRect(rect, cell * .12f, cell * .12f, paint)
            paint.style = Paint.Style.FILL
            when {
                flagged -> MinesweeperArt.drawBeacon(canvas, rect, cell, paint, path)
                mine -> MinesweeperArt.drawMine(canvas, rect, cell, false, paint)
                hidden -> {
                    paint.color = 0xFF41697A.toInt()
                    canvas.drawCircle(rect.centerX(), rect.centerY(), cell * .025f, paint)
                }
                else -> {
                    val adjacent = mines.count { (mr, mc) ->
                        kotlin.math.abs(mr - r) <= 1 && kotlin.math.abs(mc - c) <= 1
                    }
                    if (adjacent > 0) {
                        text.color = if (adjacent == 1) MinesweeperArt.teal else 0xFF97C5FF.toInt()
                        text.textSize = cell * .47f
                        canvas.drawText(digits[adjacent], rect.centerX(),
                            rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
                    }
                }
            }
        }
        // Deux fronts d'onde fixes évoquent le sonar sans animer les listes du hub.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cell * .025f
        paint.color = 0x5065E3CD
        val cx = left + cell * 2.5f
        val cy = top + cell * .5f
        canvas.drawCircle(cx, cy, cell * 1.65f, paint)
        paint.color = 0x2865E3CD
        canvas.drawCircle(cx, cy, cell * 2.35f, paint)
        paint.style = Paint.Style.FILL
        // La bande basse laisse respirer le titre, posé par le hub au format habituel.
        paint.shader = LinearGradient(0f, h * .36f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xD9040E19.toInt(), 0xFF040E19.toInt()),
            floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .36f, w, h, paint)
    }
}
