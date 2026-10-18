package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

/** Levier et briques dans la palette du jeu, sans instance du moteur physique. */
class BalanceHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF070B18.toInt(), 0xFF101A33.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val random = Random(42)
        paint.color = 0x709FBCDE
        repeat(35) { canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h * .62f, .8f, paint) }
        val unit = min(w, h)
        val cx = w * .5f
        val plankY = h * .40f
        val plankW = min(w * .80f, h * 1.65f)
        val plankH = unit * .05f
        val path = Path().apply {
            moveTo(cx, plankY + plankH * .3f)
            lineTo(cx - unit * .105f, h * .62f)
            lineTo(cx + unit * .105f, h * .62f)
            close()
        }
        paint.color = 0xFF546E7A.toInt(); canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = unit * .008f
        paint.color = 0xFF90A4AE.toInt(); canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF8D6E63.toInt()
        canvas.drawRoundRect(cx - plankW / 2f, plankY, cx + plankW / 2f, plankY + plankH,
            unit * .015f, unit * .015f, paint)
        paint.color = 0xFFDEC8AF.toInt(); paint.strokeWidth = unit * .004f
        for (i in -6..6) {
            val x = cx + i * plankW / 14f
            canvas.drawLine(x, plankY + plankH, x, plankY + plankH + unit * .02f, paint)
        }
        // Trois blocs contre trois blocs, aux mêmes distances : une pose réellement équilibrée.
        val block = unit * .105f
        fun box(x: Float, y: Float, color: Int) {
            paint.color = color
            canvas.drawRoundRect(x, y, x + block, y + block, block * .12f, block * .12f, paint)
            paint.color = 0x40FFFFFF
            canvas.drawRoundRect(x + block * .10f, y + block * .10f, x + block * .90f,
                y + block * .30f, block * .05f, block * .05f, paint)
        }
        for (side in intArrayOf(-1, 1)) {
            val x = cx + side * plankW * .28f - block / 2f
            box(x, plankY - block, if (side < 0) 0xFF4FC3F7.toInt() else 0xFFFFB74D.toInt())
            box(x, plankY - block * 2f, if (side < 0) 0xFFBA68C8.toInt() else 0xFF81C784.toInt())
            box(cx + side * plankW * .12f - block / 2f, plankY - block, 0xFFE57373.toInt())
        }
    }
}
