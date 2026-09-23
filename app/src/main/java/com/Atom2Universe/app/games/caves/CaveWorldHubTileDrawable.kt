package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.games.caves.node.MeadowTextures
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import org.json.JSONObject

/**
 * Décor de tuile pour le hub des jeux : les mêmes blocs et textures procédurales que Cave World,
 * composés simplement — un sol de blocs colorés et un arbre bien visible sur le côté.
 */
class CaveWorldHubTileDrawable(private val appContext: Context) : CachedHubArtworkDrawable() {
    private val vivid = CaveVisualStyle.current(appContext) == CaveVisualStyle.Theme.VIVID
    private val gray = CaveVisualStyle.current(appContext) == CaveVisualStyle.Theme.GRAYSCALE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val tiles = HashMap<String, Bitmap>()

    private data class Material(val top: Bitmap, val side: Bitmap)
    private fun material(name: String): Material {
        val definition = appContext.assets.open("caves/blocks/$name.json").bufferedReader()
            .use { JSONObject(it.readText()) }
        fun tile(key: String): Bitmap {
            val recipe = definition.getString(key)
            return tiles.getOrPut(recipe) { MeadowTextures.texture(recipe, 32, vivid = vivid) }
        }
        return Material(tile("texture_top"), tile("texture_side"))
    }

    private val grass = material("grass")
    private val dirt = material("dirt")
    private val wood = material("wood")
    private val leaves = material("leaves")
    private val flower = material("cornflower").side
    private val tuft = material("grass_tuft_short").side

    private fun filter(shade: Float): ColorMatrixColorFilter {
        val color = ColorMatrix().apply { setSaturation(if (gray) 0f else 1f) }
        color.postConcat(ColorMatrix(floatArrayOf(
            shade, 0f, 0f, 0f, 0f, 0f, shade, 0f, 0f, 0f, 0f, 0f, shade, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        return ColorMatrixColorFilter(color)
    }
    private val topFilter = filter(1f)
    private val leftFilter = filter(.82f)
    private val rightFilter = filter(.64f)

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val unit = h * .24f
        val sky = when { vivid -> Color.rgb(57, 126, 175); gray -> Color.rgb(120, 120, 126); else -> Color.rgb(102, 151, 161) }
        paint.shader = LinearGradient(0f, 0f, 0f, h, sky, Color.rgb(28, 48, 55), Shader.TileMode.CLAMP)
        paint.colorFilter = null
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Two staggered rows of grass blocks, plus a hint of tilled earth for color variety.
        val footprint = unit * 1.75f
        val groundY = h * .86f
        val columns = (w / footprint).toInt() + 2
        for (col in 0 until columns) {
            val mat = if (col % 4 == 1) dirt else grass
            cube(canvas, (col - .5f) * footprint, groundY, unit, mat)
        }
        for (col in 0 until columns) cube(canvas, col * footprint, groundY - unit * .95f, unit, grass)

        // A single, clearly readable tree on the left side.
        val treeX = w * .2f
        cube(canvas, treeX, groundY - unit * .55f, unit * .6f, wood)
        cube(canvas, treeX, groundY - unit * 1.15f, unit * .6f, wood)
        cube(canvas, treeX - unit, groundY - unit * 1.7f, unit * 1.05f, leaves)
        cube(canvas, treeX + unit, groundY - unit * 1.7f, unit * 1.05f, leaves)
        cube(canvas, treeX, groundY - unit * 1.7f, unit * 1.05f, leaves)
        cube(canvas, treeX, groundY - unit * 2.75f, unit * 1.15f, leaves)

        // A couple of the game's own ground sprites for a splash of color.
        sprite(canvas, flower, w * .55f, groundY + unit * .1f, unit * .9f)
        sprite(canvas, tuft, w * .72f, groundY + unit * .08f, unit * .8f)
        sprite(canvas, flower, w * .88f, groundY - unit * .85f + unit * .1f, unit * .8f)

        // Shade the bottom band so the title stays readable, as on the other illustrated tiles.
        shadePaint.shader = LinearGradient(0f, h * .62f, 0f, h, 0x00101820, 0xD9101820.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .62f, w, h, shadePaint)
        shadePaint.shader = null
    }

    private fun sprite(canvas: Canvas, bitmap: Bitmap, x: Float, bottom: Float, size: Float) {
        paint.colorFilter = topFilter
        canvas.drawBitmap(bitmap, null, RectF(x - size / 2, bottom - size, x + size / 2, bottom), paint)
    }

    private fun cube(canvas: Canvas, x: Float, y: Float, size: Float, mat: Material) {
        fun face(bitmap: Bitmap, filter: ColorMatrixColorFilter, vararg points: Float) {
            matrix.setPolyToPoly(floatArrayOf(0f, 0f, 32f, 0f, 0f, 32f), 0, points, 0, 3)
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        face(mat.side, leftFilter, x - size, y - size / 2, x, y, x - size, y + size / 2)
        face(mat.side, rightFilter, x, y, x + size, y - size / 2, x, y + size)
        face(mat.top, topFilter, x, y - size, x + size, y - size / 2, x - size, y - size / 2)
    }
}
