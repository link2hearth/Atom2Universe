package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.Atom2Universe.app.games.chess.Piece
import com.Atom2Universe.app.games.chess.PieceColor
import com.Atom2Universe.app.games.chess.PieceType

/**
 * Affiche les pièces capturées dans le header du widget Échecs.
 * Les pièces noires (prises par les blancs) sont montrées à gauche,
 * les pièces blanches (prises par les noirs) à droite.
 * Revient au texte "♟ Échecs" tant qu'aucune pièce n'a été capturée.
 */
class ChessCapturedPiecesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var capturedPieces: List<Piece> = emptyList()

    // Sprites chargés et redimensionnés à la taille du header
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private var bitmapsLoaded = false

    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
    }

    fun update(pieces: List<Piece>) {
        capturedPieces = pieces.toList()
        invalidate()
    }

    // Chargement paresseux : on attend d'avoir une taille réelle
    private fun ensureBitmaps() {
        if (bitmapsLoaded || height <= 0) return
        bitmapsLoaded = true

        val targetSize = (height * 0.72f).toInt().coerceAtLeast(1)

        for (color in PieceColor.values()) {
            for (type in PieceType.values()) {
                val piece = Piece(type, color)
                val path = piece.getSpritePath()
                runCatching {
                    context.assets.open(path).use { stream ->
                        val full = BitmapFactory.decodeStream(stream) ?: return@use
                        bitmaps[piece.notation] =
                            Bitmap.createScaledBitmap(full, targetSize, targetSize, true)
                        if (full !== bitmaps[piece.notation]) full.recycle()
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Si la taille change (premier layout), on force le rechargement
        if (h != oldh && bitmapsLoaded) {
            bitmaps.values.forEach { it.recycle() }
            bitmaps.clear()
            bitmapsLoaded = false
        }
        fallbackPaint.textSize = h * 0.40f
    }

    override fun onDraw(canvas: Canvas) {
        ensureBitmaps()

        val blacks = capturedPieces.filter { it.color == PieceColor.BLACK }  // prises par blancs
        val whites = capturedPieces.filter { it.color == PieceColor.WHITE }  // prises par noirs

        if (blacks.isEmpty() && whites.isEmpty()) {
            val ty = height / 2f - (fallbackPaint.descent() + fallbackPaint.ascent()) / 2f
            canvas.drawText("♟ Échecs", width / 2f, ty, fallbackPaint)
            return
        }

        val pieceSize = (height * 0.72f)
        val gap = 2f * resources.displayMetrics.density       // espace entre pièces
        val groupGap = 8f * resources.displayMetrics.density  // espace entre les deux groupes

        val blacksW = blacks.size * pieceSize + (blacks.size - 1).coerceAtLeast(0) * gap
        val whitesW = whites.size * pieceSize + (whites.size - 1).coerceAtLeast(0) * gap
        val sepW = if (blacks.isNotEmpty() && whites.isNotEmpty()) groupGap else 0f
        val totalW = blacksW + sepW + whitesW

        var x = ((width - totalW) / 2f).coerceAtLeast(2f)
        val y = (height - pieceSize) / 2f

        for (piece in blacks) {
            bitmaps[piece.notation]?.let { canvas.drawBitmap(it, x, y, null) }
            x += pieceSize + gap
        }
        if (sepW > 0f) x += groupGap - gap  // annuler le gap en trop, ajouter le séparateur

        for (piece in whites) {
            bitmaps[piece.notation]?.let { canvas.drawBitmap(it, x, y, null) }
            x += pieceSize + gap
        }
    }

    override fun onDetachedFromWindow() {
        bitmaps.values.forEach { it.recycle() }
        bitmaps.clear()
        bitmapsLoaded = false
        super.onDetachedFromWindow()
    }
}
