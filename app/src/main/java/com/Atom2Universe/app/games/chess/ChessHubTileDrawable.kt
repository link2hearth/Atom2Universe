package com.Atom2Universe.app.games.chess

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class ChessHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val assets = context.applicationContext.assets

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cell = min(w / 3.6f, h * .43f)
        val left = -cell * .20f
        val top = -cell * .12f
        // Gros plan continu : les cases débordent des quatre côtés de la tuile.
        for (row in 0..((h - top) / cell).toInt()) for (col in 0..((w - left) / cell).toInt()) {
            paint.color = if ((row + col) % 2 == 0) 0xFFD3C6AC.toInt() else 0xFF657276.toInt()
            canvas.drawRect(left + col * cell, top + row * cell,
                left + (col + 1) * cell, top + (row + 1) * cell, paint)
        }
        fun piece(type: PieceType, color: PieceColor, col: Int, row: Int) {
            val bitmap = assets.open(Piece(type, color).getSpritePath()).use { BitmapFactory.decodeStream(it) }
                ?: return
            val x = left + col * cell
            val y = top + row * cell
            // Pas de lissage : conserver les pixels des pièces originales.
            canvas.drawBitmap(bitmap, null, RectF(x, y, x + cell, y + cell), paint)
            bitmap.recycle()
        }
        piece(PieceType.KING, PieceColor.WHITE, 2, 1)
        piece(PieceType.KNIGHT, PieceColor.BLACK, 3, 0)
        piece(PieceType.ROOK, PieceColor.WHITE, 0, 1)
        piece(PieceType.PAWN, PieceColor.BLACK, 1, 0)
        // Le damier reste visible sous le titre blanc, avec un voile progressif pour le contraste.
        paint.shader = LinearGradient(0f, h * .5f, 0f, h,
            0x0011141D, 0xC011141D.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .5f, w, h, paint)
    }
}
