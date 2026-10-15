package com.Atom2Universe.app.games.chess

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser

/** Shared vector silhouettes for the board, captured pieces and hub artwork. */
class ChessPieceArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(canvas: Canvas, piece: Piece, left: Float, top: Float, size: Float) {
        if (size <= 0f) return
        val saved = canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / 100f, size / 100f)
        val white = piece.color == PieceColor.WHITE
        val fill = if (white) 0xFFFFF4DE.toInt() else 0xFF303847.toInt()
        val edge = if (white) 0xFF292F3B.toInt() else 0xFFEFE3C5.toInt()
        val shape = shapes.getValue(piece.type)
        for (path in shape.body) {
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.6f
            paint.color = edge
            canvas.drawPath(path, paint)
        }
        paint.strokeWidth = 2.4f
        canvas.drawPath(shape.detail, paint)
        // Common stepped pedestal keeps the family visually consistent.
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawPath(base, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        paint.color = edge
        canvas.drawPath(base, paint)
        canvas.drawLine(29f, 84f, 71f, 84f, paint)
        canvas.restoreToCount(saved)
    }

    companion object {
        private class Shape(val body: List<Path>, val detail: Path)
        private fun path(data: String): Path = requireNotNull(PathParser.createPathFromPathData(data))
        private fun shape(body: String, detail: String, vararg extra: String) =
            Shape(listOf(path(body)) + extra.map(::path), path(detail))

        private val base = path("M28 78 L72 78 L72 84 L79 88 L79 93 L21 93 L21 88 L28 84 Z")
        private val shapes = mapOf(
            PieceType.PAWN to shape(
                "M40 47 C43 57 38 67 32 73 L32 78 L68 78 L68 73 C62 67 57 57 60 47 Z",
                "M37 54 L63 54 M33 72 L67 72",
                "M63 34 C63 51 37 51 37 34 C37 17 63 17 63 34 Z"
            ),
            PieceType.ROOK to shape(
                "M26 18 L36 18 L36 29 L45 29 L45 18 L55 18 L55 29 L64 29 L64 18 L74 18 " +
                    "L74 39 L65 45 L65 66 L70 72 L70 78 L30 78 L30 72 L35 66 L35 45 L26 39 Z",
                "M29 38 L71 38 M35 47 L65 47 M35 66 L65 66 M32 72 L68 72"
            ),
            PieceType.KNIGHT to shape(
                "M30 78 L30 71 C32 62 38 56 47 50 L41 40 L30 48 L20 43 L22 34 L39 20 " +
                    "L42 9 L50 18 L59 15 C76 28 80 48 70 65 L70 78 Z",
                "M42 28 L45 28 M23 38 L29 39 M50 25 C65 32 68 45 62 58 " +
                    "M36 70 C44 62 51 59 56 58 M32 73 L68 73"
            ),
            PieceType.BISHOP to shape(
                "M42 49 C43 60 38 68 32 72 L32 78 L68 78 L68 72 C62 68 57 60 58 49 Z",
                "M53 22 L44 37 M38 53 L62 53 M34 71 L66 71",
                "M50 12 C43 21 31 30 34 40 C38 54 62 54 66 40 C69 30 57 21 50 12 Z"
            ),
            PieceType.QUEEN to shape(
                "M28 28 L39 40 L42 23 L50 38 L58 23 L61 40 L72 28 L65 56 " +
                    "C62 64 64 69 69 73 L69 78 L31 78 L31 73 C36 69 38 64 35 56 Z",
                "M35 54 L65 54 M37 61 L63 61 M33 72 L67 72",
                "M30 24 C30 29 22 29 22 24 C22 19 30 19 30 24 Z",
                "M46 19 C46 24 38 24 38 19 C38 14 46 14 46 19 Z",
                "M62 19 C62 24 54 24 54 19 C54 14 62 14 62 19 Z",
                "M78 24 C78 29 70 29 70 24 C70 19 78 19 78 24 Z"
            ),
            PieceType.KING to shape(
                "M35 40 C25 30 29 23 39 26 L50 33 L61 26 C71 23 75 30 65 40 " +
                    "L61 56 C60 64 64 69 69 73 L69 78 L31 78 L31 73 C36 69 40 64 39 56 Z",
                "M35 42 L65 42 M39 55 L61 55 M34 72 L66 72",
                "M46 8 L54 8 L54 16 L62 16 L62 23 L54 23 L54 33 L46 33 L46 23 L38 23 L38 16 L46 16 Z"
            )
        )

        /** Rasterize once at the requested size for ImageViews and the board's existing cache. */
        fun createBitmap(piece: Piece, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            ChessPieceArt().draw(Canvas(bitmap), piece, 0f, 0f, bitmap.width.toFloat())
            return bitmap
        }
    }
}
