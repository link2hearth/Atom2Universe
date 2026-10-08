package com.Atom2Universe.app.games.match3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Illustration plein cadre avec les mêmes pièces Canvas que le jeu. */
class Match3HubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    private val art = ForgeArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun render(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xff29434b.toInt(), 0xff13232b.toInt(), 0xff38291f.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .5f, h * .42f, maxOf(w, h) * .65f,
            0x8073ded8.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val tile = min(w / 4.5f, h / 3.9f)
        val left = (w - tile * 4) / 2
        val top = h * .07f
        val rim = tile * .12f
        paint.color = 0xff94714a.toInt()
        canvas.drawRoundRect(left - rim, top - rim, left + tile * 4 + rim, top + tile * 3 + rim, rim, rim, paint)
        paint.color = 0xff102029.toInt()
        canvas.drawRoundRect(left - rim * .45f, top - rim * .45f,
            left + tile * 4 + rim * .45f, top + tile * 3 + rim * .45f, rim * .5f, rim * .5f, paint)

        val pieces = intArrayOf(0, 3, 2, 4, 1, 5, 4, 2, 3, 2, 0, 1)
        for (i in pieces.indices) {
            val x = left + i % 4 * tile
            val y = top + i / 4 * tile
            paint.color = if (i % 2 == 0) 0xff263d45.toInt() else 0xff1d323a.toInt()
            canvas.drawRoundRect(x + tile * .025f, y + tile * .025f,
                x + tile * .975f, y + tile * .975f, tile * .07f, tile * .07f, paint)
            if (pieces[i] == ForgeGame.CORE) {
                paint.shader = RadialGradient(x + tile / 2, y + tile / 2, tile * .7f,
                    0x997dffff.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawCircle(x + tile / 2, y + tile / 2, tile * .7f, paint)
                paint.shader = null
            }
            art.piece(canvas, pieces[i], x, y, tile)
        }
        paint.color = 0xffffc779.toInt()
        for (x in floatArrayOf(left - rim / 2, left + tile * 4 + rim / 2)) {
            for (y in floatArrayOf(top - rim / 2, top + tile * 3 + rim / 2)) {
                canvas.drawCircle(x, y, rim * .24f, paint)
            }
        }
        // Le hub ajoute son titre standard dans cette zone sombre.
        paint.shader = LinearGradient(0f, h * .5f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xe6101922.toInt(), 0xff101922.toInt()),
            floatArrayOf(0f, .75f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .5f, w, h, paint)
        paint.shader = null
    }
}
