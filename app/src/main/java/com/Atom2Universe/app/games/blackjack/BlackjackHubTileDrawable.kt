package com.Atom2Universe.app.games.blackjack

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class BlackjackHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h, 0xFF20634F.toInt(), 0xFF08291F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val cw = min(w * .17f, h * .29f)
        val ch = cw * 1.42f
        fun card(x: Float, y: Float, rank: Int, suit: Int, angle: Float) {
            val card = BlackjackCard(rank, suit)
            val save = canvas.save()
            canvas.rotate(angle, x + cw / 2f, y + ch / 2f)
            paint.color = 0x65000000
            canvas.drawRoundRect(x + 2f, y + 3f, x + cw + 2f, y + ch + 3f, cw * .08f, cw * .08f, paint)
            paint.color = 0xFFFFFBEF.toInt()
            canvas.drawRoundRect(x, y, x + cw, y + ch, cw * .08f, cw * .08f, paint)
            paint.color = if (card.isRed) 0xFFB82C3A.toInt() else 0xFF192331.toInt()
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = cw * .30f
            canvas.drawText(card.rankLabel, x + cw * .09f, y + cw * .34f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = cw * .65f
            canvas.drawText(card.suitSymbol, x + cw * .53f, y + ch * .76f, paint)
            canvas.restoreToCount(save)
        }
        // Deux mains : blackjack naturel et dix + huit = dix-huit.
        val y = h * .12f
        card(w * .27f - cw, y, 1, 0, -10f)
        card(w * .27f - cw * .08f, y + h * .035f, 11, 1, 8f)
        card(w * .73f - cw, y, 10, 3, -8f)
        card(w * .73f - cw * .08f, y + h * .035f, 8, 2, 10f)
        paint.shader = LinearGradient(0f, h * .60f, 0f, h, Color.TRANSPARENT, 0xC008211A.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .60f, w, h, paint)
    }
}
