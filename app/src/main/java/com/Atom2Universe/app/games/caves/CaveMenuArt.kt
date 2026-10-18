package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.games.caves.node.MeadowTextures
import org.json.JSONObject

/** Static diorama using the game's actual block definitions and texture recipes. */
internal class CaveMenuArt(context: Context, private val variant: Int) : View(context) {
    private val theme = CaveVisualStyle.current(context)
    private val vivid = theme == CaveVisualStyle.Theme.VIVID
    private val gray = theme == CaveVisualStyle.Theme.GRAYSCALE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
    private val matrix = Matrix()
    private val tiles = HashMap<String, Bitmap>()
    private data class Material(val top: Bitmap, val side: Bitmap)
    private fun material(name: String): Material {
        val definition = context.assets.open("caves/blocks/$name.json").bufferedReader().use { JSONObject(it.readText()) }
        fun tile(key: String): Bitmap {
            val recipe = definition.getString(key)
            return tiles.getOrPut(recipe) { MeadowTextures.texture(recipe, 32, vivid = vivid) }
        }
        return Material(tile("texture_top"), tile("texture_side"))
    }
    private val grass = material("grass")
    private val wood = material("wood")
    private val leaves = material("leaves")
    private val rock = material("stone")
    private val tuft = material("grass_tuft_short").side
    private val flower = material("cornflower").side
    private val mushroom = material("mushroom_brown").side
    private fun filter(shade: Float): ColorMatrixColorFilter {
        val color = ColorMatrix().apply { setSaturation(if (gray) 0f else 1f) }
        color.postConcat(ColorMatrix(floatArrayOf(
            shade,0f,0f,0f,0f, 0f,shade,0f,0f,0f, 0f,0f,shade,0f,0f, 0f,0f,0f,1f,0f)))
        return ColorMatrixColorFilter(color)
    }
    private val topFilter = filter(1f)
    private val leftFilter = filter(.82f)
    private val rightFilter = filter(.64f)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.scale(width / 480f, height / 132f)
        paint.colorFilter = topFilter
        val sky = when { vivid -> Color.rgb(57,126,175); variant == 2 -> Color.rgb(100,85,89); else -> Color.rgb(102,151,161) }
        paint.shader = LinearGradient(0f,0f,0f,132f,sky,Color.rgb(28,48,55),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,480f,132f,paint)
        paint.shader = null
        paint.color = Color.rgb(242,232,198)
        canvas.drawCircle(425f,25f,12f,paint)
        paint.color = Color.rgb(66,98,108)
        for (i in 0..12) canvas.drawRect(i*40f,55f+(i*13%25),i*40f+41f,132f,paint)
        // Painter's order, back to front; only a handful of tiny textures are retained.
        for (row in 0..2) for (col in 0..5)
            cube(canvas,253f+col*27f-row*14f,74f+row*15f,14f,grass)
        val treeX = if (vivid) 330f else 301f
        cube(canvas,treeX,65f,9f,wood)
        cube(canvas,treeX,48f,9f,wood)
        cube(canvas,treeX-16f,35f,17f,leaves)
        cube(canvas,treeX+16f,35f,17f,leaves)
        cube(canvas,treeX,19f,18f,leaves)
        cube(canvas,treeX,43f,18f,leaves)
        val rockX = if (vivid) 266f else 363f
        cube(canvas,rockX,82f,12f,rock)
        cube(canvas,rockX+16f,88f,8f,rock)
        if (gray || variant == 2) cube(canvas,rockX,65f,9f,rock)
        sprite(canvas,tuft,244f,90f,23f)
        sprite(canvas,tuft,344f,102f,24f)
        sprite(canvas,flower,if (vivid) 288f else 323f,94f,25f)
        sprite(canvas,flower,392f,88f,21f)
        sprite(canvas,mushroom,treeX-19f,85f,18f)
        canvas.restoreToCount(checkpoint)
    }
    private fun sprite(canvas: Canvas, bitmap: Bitmap, x: Float, bottom: Float, size: Float) {
        paint.colorFilter = topFilter
        canvas.drawBitmap(bitmap,null,RectF(x-size/2,bottom-size,x+size/2,bottom),paint)
    }
    private fun cube(canvas: Canvas, x: Float, y: Float, size: Float, material: Material) {
        fun face(bitmap: Bitmap, filter: ColorMatrixColorFilter, vararg points: Float) {
            matrix.setPolyToPoly(floatArrayOf(0f,0f,32f,0f,0f,32f),0,points,0,3)
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap,matrix,paint)
        }
        face(material.side,leftFilter,x-size,y-size/2,x,y,x-size,y+size/2)
        face(material.side,rightFilter,x,y,x+size,y-size/2,x,y+size)
        face(material.top,topFilter,x,y-size,x+size,y-size/2,x-size,y-size/2)
    }
}
