package com.Atom2Universe.app.games.chess

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/** Optional PNG pack; each missing or unreadable piece uses the native drawing instead. */
object ChessPieceImages {
    fun load(assets: AssetManager, piece: Piece, size: Int): Bitmap {
        val name = when (piece.type) {
            PieceType.PAWN -> "pawn"
            PieceType.KNIGHT -> "knight"
            PieceType.BISHOP -> "bishop"
            PieceType.ROOK -> "rook"
            PieceType.QUEEN -> "queen"
            PieceType.KING -> "king"
        }
        val suffix = if (piece.color == PieceColor.BLACK) "1" else ""
        val original = try {
            assets.open("Assets/Chess/$name$suffix.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: IOException) {
            null
        } ?: return ChessPieceArt.createBitmap(piece, size)

        val target = size.coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, target, target, true)
        if (scaled !== original) original.recycle()
        return scaled
    }
}
