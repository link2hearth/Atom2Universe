package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.random.Random

/** Une miniature du vrai village et de ses héros, au format commun aux décors du hub. */
class HamletHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    override fun render(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, w * .35f, h,
            intArrayOf(0xFF254C48.toInt(), 0xFF4F8060.toInt(), 0xFF132F32.toInt()),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .58f, h * .27f, maxOf(w, h) * .60f,
            0x8CE9D9A4.toInt(), 0x00E9D9A4, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val scale = minOf(w / 690f, h / 700f)
        canvas.save()
        canvas.translate(w / 2f, h * .27f)
        canvas.scale(scale, scale)
        val iso = Iso(canvas, 0f, 0f)
        iso.shadow(-.3f, -.3f, 10.3f, 10.3f)
        iso.box(0f, 0f, -.65f, 10f, 10f, 0f, 0x6A9859)
        iso.outline = false
        for (y in 0..9) for (x in 0..9) {
            iso.top(x.toFloat(), y.toFloat(), x + 1f, y + 1f, .01f, HamletGround.grassColor(x, y, true))
        }
        // Un bassin, un petit pont et les chemins relient le décor, comme dans le village.
        iso.top(8f, 0f, 10f, 3.6f, .03f, 0x4C9A9D)
        iso.top(8.12f, .1f, 9.88f, 3.45f, .04f, 0x7DBDB2)
        for (i in 0..5) iso.top(8.30f, .38f + i * .48f, 9.42f, .41f + i * .48f, .05f, 0xB4DCCC)
        iso.top(3.6f, 4.7f, 5.3f, 9.7f, .03f, 0xD7BF83)
        iso.top(.2f, 6.3f, 9.7f, 7.4f, .03f, 0xD7BF83)
        for (i in 0..6) iso.top(7.6f + i * .33f, 3.0f, 7.85f + i * .33f, 4.2f, .10f, 0xBB8C51)

        val art = HamletArt()
        // A presentation-only model selects the actual hall sprite; it never starts or loads a game.
        val display = HamletGame({ 0L }, Random(0)).apply { hall = 2 }
        data class Piece(val type: String, val x: Int, val y: Int, val crop: String? = null)
        val pieces = listOf(
            Piece("treePine", 0, 0), Piece("treeRound", 1, 0), Piece("treePine", 0, 3),
            Piece("hall", 2, 1), Piece("bluecottage", 6, 1),
            Piece("treeApple", 9, 4), Piece("cottage", 0, 5),
            Piece("adventureForge", 6, 4), Piece("flowersRed", 0, 7),
            Piece("field", 1, 8, "carrots"), Piece("field", 3, 8, "corn"),
            Piece("flowersYellow", 5, 9), Piece("treeRound", 8, 8))
        for (piece in pieces.sortedBy { it.x + it.y }) {
            val sprite = if (piece.type == "hall") art.forObj(Obj(0, "hall", 0, 0), display)
                else art.forDef(Hamlet.byId.getValue(piece.type), cropId = piece.crop, stage = 2)
            val k = 1f / HamletArt.S
            val left = (piece.x - piece.y) * 32f - sprite.ox * k
            val top = (piece.x + piece.y) * 16f - sprite.oy * k
            destination.set(left, top, left + sprite.bmp.width * k, top + sprite.bmp.height * k)
            canvas.drawBitmap(sprite.bmp, null, destination, paint)
        }
        art.clear()
        canvas.restore()

        // Shared character bitmaps are immutable; the background worker uses its own paint/rect.
        val heroSize = minOf(w * .34f, h * .31f)
        for ((hero, x) in listOf(1 to .37f, 0 to .61f)) {
            val foot = h * if (hero == 1) .735f else .715f
            paint.color = 0x52202C26
            canvas.drawOval(w * x - heroSize * .26f, foot - heroSize * .045f,
                w * x + heroSize * .26f, foot + heroSize * .018f, paint)
            val sprite = HamletCharacterArt.bitmap(true, hero)
            HamletCharacterArt.bounds(w * x, foot, heroSize, destination)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawBitmap(sprite, null, destination, paint)
        }

        // The hub supplies the localized title; keep its lower band calm and high contrast.
        paint.shader = LinearGradient(0f, h * .72f, 0f, h,
            intArrayOf(0x00152F2D, 0xB8152F2D.toInt(), 0xFF102623.toInt()),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .72f, w, h, paint)
        paint.shader = null
    }
}
