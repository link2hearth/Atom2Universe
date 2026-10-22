package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Cinq éprouvettes, un tube plein d'une seule couleur, et un sixième qui verse dans le tube vide. */
class LabSortHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        p.shader = LinearGradient(0f, 0f, w, h,
            0xFF1E2F4F.toInt(), 0xFF0A1424.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = 0x1260D0E0
        canvas.drawCircle(w * .12f, h * .22f, unit * .30f, p)
        p.color = 0x12A070F0
        canvas.drawCircle(w * .92f, h * .60f, unit * .26f, p)

        fun hex(i: Int) = LabLiquids.ALL[i].hex
        val tubeW = unit * .13f
        val tubeH = unit * .36f
        val bottom = h * .70f
        val layerH = tubeH * .225f
        fun tubeX(i: Int) = w * .5f + (i - 2) * unit * .19f

        fun tubePath(): Path = Path().apply {
            addRoundRect(RectF(0f, 0f, tubeW, tubeH), floatArrayOf(
                tubeW * .1f, tubeW * .1f, tubeW * .1f, tubeW * .1f,
                tubeW / 2, tubeW / 2, tubeW / 2, tubeW / 2
            ), Path.Direction.CW)
        }

        fun drawTube(layers: List<Int>, outline: Int) {
            val path = tubePath()
            p.style = Paint.Style.FILL
            p.color = 0x22FFFFFF
            canvas.drawPath(path, p)
            canvas.save()
            canvas.clipPath(path)
            layers.forEachIndexed { row, id ->
                p.color = hex(id)
                canvas.drawRect(0f, tubeH - (row + 1) * layerH - unit * .01f,
                    tubeW, tubeH - row * layerH, p)
            }
            canvas.restore()
            p.style = Paint.Style.STROKE
            p.strokeWidth = unit * .006f
            p.color = outline
            canvas.drawPath(path, p)
            p.style = Paint.Style.FILL
        }

        val tubes = listOf(
            listOf(0, 1, 0, 2),
            listOf(3, 3, 3, 3),
            listOf(2, 1, 3),
            emptyList(),
            listOf(4, 5, 4)
        )
        tubes.forEachIndexed { i, layers ->
            canvas.save()
            canvas.translate(tubeX(i) - tubeW / 2, bottom - tubeH)
            drawTube(layers, if (i == 1) 0xFF4ADE80.toInt() else 0x88C7D2FE.toInt())
            canvas.restore()
        }

        // Le tube qui verse : penché au-dessus du tube vide, avec son filet.
        val lipX = tubeX(3) + unit * .015f
        val lipY = bottom - tubeH - unit * .05f
        p.style = Paint.Style.STROKE
        p.strokeWidth = unit * .016f
        p.strokeCap = Paint.Cap.ROUND
        p.color = hex(4)
        canvas.drawLine(lipX, lipY + unit * .01f, lipX, bottom - layerH * 1.1f, p)
        p.style = Paint.Style.FILL
        canvas.save()
        canvas.translate(lipX, lipY)
        canvas.rotate(62f)
        canvas.translate(-tubeW, 0f)
        drawTube(listOf(4, 5), 0xFFFFFFFF.toInt())
        canvas.restore()

        p.shader = LinearGradient(0f, h * .62f, 0f, h,
            0x000A1424, 0xEF0A1424.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .62f, w, h, p)
    }
}
