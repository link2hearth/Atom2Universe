package com.Atom2Universe.app.games.solitaire

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

/** Cartes uniquement face visible : aucun dos du Memory n'est chargé. */
class SolitaireHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h,
            0xFF258052.toInt(), 0xFF104B32.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val random = Random(514)
        paint.color = 0x127FE5A8
        repeat(240) {
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, .6f, paint)
        }
        val cw = min(w * .18f, h * .255f)
        val ch = cw * 1.42f
        val gap = cw * .24f
        val left = (w - cw * 4f - gap * 3f) / 2f
        fun card(x: Float, y: Float, rank: Rank, suit: Suit) {
            val corner = cw * .09f
            paint.color = 0x45000000
            canvas.drawRoundRect(x + 1f, y + 2f, x + cw + 1f, y + ch + 2f, corner, corner, paint)
            paint.color = 0xFFFFFCF1.toInt()
            canvas.drawRoundRect(x, y, x + cw, y + ch, corner, corner, paint)
            paint.color = if (suit.color == CardColor.RED) 0xFFBD3540.toInt() else 0xFF202938.toInt()
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = cw * .24f
            canvas.drawText(rank.label + suit.symbol, x + cw * .10f, y + cw * .27f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = cw * .63f
            canvas.drawText(suit.symbol, x + cw * .50f, y + ch * .69f, paint)
        }
        val ranks = arrayOf(Rank.NINE, Rank.EIGHT, Rank.SEVEN)
        // Trois colonnes de cartes alternées et une fondation à l'as.
        for (col in 0..2) for (row in 0..col) {
            val suit = if ((col + row) % 2 == 0) Suit.SPADES else Suit.HEARTS
            card(left + col * (cw + gap), h * .075f + row * ch * .23f, ranks[row], suit)
        }
        card(left + 3f * (cw + gap), h * .075f, Rank.ACE, Suit.DIAMONDS)
        paint.shader = LinearGradient(0f, h * .55f, 0f, h,
            Color.TRANSPARENT, 0xC5082C1C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .55f, w, h, paint)
    }
}
