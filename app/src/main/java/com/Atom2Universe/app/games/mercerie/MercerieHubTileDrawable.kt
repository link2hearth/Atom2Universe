package com.Atom2Universe.app.games.mercerie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.max
import kotlin.math.min

/**
 * La tuile du hub : un vrai plateau d'alignements en feutrine de nuit (images de
 * `assets/mercerie/dark/`), posé sur le patchwork du soir. Le titre s'écrit en blanc par-dessus.
 *
 * Elle se cuit hors du fil d'interface : elle lit ses images elle-même et ne touche pas au thème
 * commun du jeu ([Felt.theme]), que la partie en cours pourrait être en train d'utiliser.
 */
class MercerieHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val context = context.applicationContext

    private fun image(name: String): Bitmap? = try {
        context.assets.open("mercerie/dark/$name.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Le patchwork du soir, en couverture.
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = 0xFF3A3F55.toInt() })
        image("fond")?.let { quilt ->
            val k = max(w, h) / quilt.width * 1.3f
            canvas.drawRect(0f, 0f, w, h, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(quilt, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                    setLocalMatrix(Matrix().apply { setScale(k, k) })
                }
            })
            quilt.recycle()
        }

        // Le plateau : cinq rangées, autant de colonnes que la largeur en accueille.
        val rows = PATTERN.size
        val cell = h * .86f / (rows + .4f)
        val cols = ((w * .92f / cell - .4f).toInt()).coerceIn(3, PATTERN[0].length)
        val first = (PATTERN[0].length - cols) / 2
        val ox = (w - cell * cols) / 2
        val oy = (h - cell * rows) / 2
        val pad = cell * .2f
        val unit = cell * 1.2f
        Felt.panel(canvas, ox - pad, oy - pad, ox + cell * cols + pad, oy + cell * rows + pad, cell * .34f,
            FRAME, unit, threadColor = THREAD)
        image("plateau")?.let { cloth ->
            val k = cell * 4f / cloth.width
            canvas.drawRoundRect(ox - pad, oy - pad, ox + cell * cols + pad, oy + cell * rows + pad, cell * .34f, cell * .34f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    shader = BitmapShader(cloth, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                        setLocalMatrix(Matrix().apply { setScale(k, k) })
                    }
                })
            cloth.recycle()
            val inset = pad * .45f
            val seam = Path().apply {
                addRoundRect(ox - pad + inset, oy - pad + inset, ox + cell * cols + pad - inset, oy + cell * rows + pad - inset,
                    cell * .28f, cell * .28f, Path.Direction.CW)
            }
            Felt.stitches(canvas, seam, THREAD, unit)
        }

        val pieces = Array(6) { image("piece_$it") }
        val scissors = image("scissors")
        val box = RectF()
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = ox + c * cell
            val y = oy + r * cell
            val m = cell * .035f
            val tile = Path().apply { addRoundRect(x + m, y + m, x + cell - m, y + cell - m, cell * .16f, cell * .16f, Path.Direction.CW) }
            Felt.shape(canvas, tile, TILES[(r + c) % 2], cell, stitch = false, shadow = false, plush = false)
            val color = PATTERN[r][first + c] - '0'
            val piece = pieces[color] ?: continue
            fit(box, piece, x, y, cell, .78f)
            canvas.drawBitmap(piece, null, box, smooth)
            if (r == BOBBIN_ROW && first + c == BOBBIN_COL && scissors != null) {
                fit(box, scissors, x, y, cell, .5f)
                canvas.drawBitmap(scissors, null, box, smooth)
            }
        }
        pieces.forEach { it?.recycle() }
        scissors?.recycle()
    }

    /** Pose [src] au milieu de la case, à [scale] de sa largeur. */
    private fun fit(out: RectF, src: Bitmap, x: Float, y: Float, cell: Float, scale: Float) {
        val k = min(cell / src.width, cell / src.height) * scale
        val hw = src.width * k / 2
        val hh = src.height * k / 2
        out.set(x + cell / 2 - hw, y + cell / 2 - hh, x + cell / 2 + hw, y + cell / 2 + hh)
    }

    private companion object {
        /**
         * Une partie en cours : aucune ligne de trois toute faite, mais des paires qui n'attendent
         * qu'un échange (les deux cœurs de la deuxième rangée), et une bobine au milieu.
         */
        val PATTERN = listOf(
            "1420351",
            "2003042",
            "5314523",
            "4152405",
            "3241234"
        )
        const val BOBBIN_ROW = 2
        const val BOBBIN_COL = 3
        val FRAME = 0xFF5E5568.toInt()
        val THREAD = 0xFFEDE3D3.toInt()
        val TILES = intArrayOf(0xFF555B6E.toInt(), 0xFF4C5163.toInt())
    }
}
