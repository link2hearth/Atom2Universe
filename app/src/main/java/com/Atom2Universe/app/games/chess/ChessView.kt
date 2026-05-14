package com.Atom2Universe.app.games.chess

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min

/**
 * Vue personnalisée pour afficher et interagir avec l'échiquier
 * Gère le rendu Canvas et le drag & drop des pièces
 */
class ChessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Interface pour les événements de la vue
     */
    interface ChessViewListener {
        fun onSquareClicked(square: Square)
        fun onPieceDragged(from: Square, to: Square): Boolean
    }

    var game: ChessGame? = null
        set(value) {
            field = value
            invalidate()
        }

    var listener: ChessViewListener? = null

    // Mode 2 joueurs (pour flip des pièces noires)
    var isTwoPlayerMode: Boolean = false
        set(value) {
            field = value
            // Recharger les bitmaps si changement de mode
            if (pieceBitmaps.isNotEmpty()) {
                scalePieceBitmaps()
                invalidate()
            }
        }

    // Dimensions du plateau
    private var boardSize = 0f
    private var squareSize = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f

    // État visuel
    var selectedSquare: Square? = null
        private set
    private var legalMoveSquares = emptyList<Square>()
    private var lastMoveFrom: Square? = null
    private var lastMoveTo: Square? = null

    // État du drag & drop
    private var isDragging = false
    private var draggedPiece: Piece? = null
    private var draggedFrom: Square? = null
    private var dragX = 0f
    private var dragY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val dragThreshold = 20f

    // Bitmaps des pièces
    private val pieceBitmaps = mutableMapOf<String, Bitmap>()
    private val scaledPieceBitmaps = mutableMapOf<String, Bitmap>()

    // Paints pour le rendu
    private val lightSquarePaint = Paint().apply {
        color = Color.parseColor("#F0D9B5")
    }

    private val darkSquarePaint = Paint().apply {
        color = Color.parseColor("#B58863")
    }

    private val selectedSquarePaint = Paint().apply {
        color = Color.parseColor("#90EE90")
        alpha = 180
    }

    private val legalMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#696969")
        alpha = 120
        style = Paint.Style.FILL
    }

    private val legalMoveCaptureStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#696969")
        alpha = 120
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val lastMovePaint = Paint().apply {
        color = Color.parseColor("#FFFF00")
        alpha = 100
    }

    private val checkHighlightPaint = Paint().apply {
        color = Color.parseColor("#EF4444")
        alpha = 180
    }

    private val coordinateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    init {
        // Désactiver le clipping pour permettre le drag en dehors du plateau
        clipToOutline = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions(w, h)
        scalePieceBitmaps()
    }

    /**
     * Calcule les dimensions du plateau
     */
    private fun calculateDimensions(width: Int, height: Int) {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom

        // Plateau carré qui rentre dans l'espace disponible
        boardSize = min(availableWidth, availableHeight).toFloat()
        squareSize = boardSize / 8f

        // Centrer le plateau
        boardOffsetX = paddingLeft + (availableWidth - boardSize) / 2f
        boardOffsetY = paddingTop + (availableHeight - boardSize) / 2f

        coordinateTextPaint.textSize = squareSize * 0.15f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBoard(canvas)
        drawCheckHighlight(canvas)
        drawLastMoveHighlight(canvas)
        drawSelectedSquare(canvas)
        drawLegalMoveIndicators(canvas)
        drawPieces(canvas)
        drawCoordinates(canvas)

        if (isDragging && draggedPiece != null) {
            drawDraggedPiece(canvas)
        }
    }

    /**
     * Dessine le plateau (cases alternées)
     */
    private fun drawBoard(canvas: Canvas) {
        for (row in 0..7) {
            for (col in 0..7) {
                val x = boardOffsetX + col * squareSize
                val y = boardOffsetY + row * squareSize

                val paint = if ((row + col) % 2 == 0) lightSquarePaint else darkSquarePaint
                canvas.drawRect(x, y, x + squareSize, y + squareSize, paint)
            }
        }
    }

    /**
     * Surligne en rouge la case du roi si celui-ci est en échec
     */
    private fun drawCheckHighlight(canvas: Canvas) {
        val g = game ?: return
        if (!g.isInCheck) return
        for (row in 0..7) {
            for (col in 0..7) {
                val square = Square(row, col)
                val piece = g.getPieceAt(square) ?: continue
                if (piece.type == PieceType.KING && piece.color == g.currentTurn) {
                    val x = boardOffsetX + col * squareSize
                    val y = boardOffsetY + row * squareSize
                    canvas.drawRect(x, y, x + squareSize, y + squareSize, checkHighlightPaint)
                    return
                }
            }
        }
    }

    /**
     * Surligne le dernier coup joué
     */
    private fun drawLastMoveHighlight(canvas: Canvas) {
        lastMoveFrom?.let { from ->
            val x = boardOffsetX + from.col * squareSize
            val y = boardOffsetY + from.row * squareSize
            canvas.drawRect(x, y, x + squareSize, y + squareSize, lastMovePaint)
        }

        lastMoveTo?.let { to ->
            val x = boardOffsetX + to.col * squareSize
            val y = boardOffsetY + to.row * squareSize
            canvas.drawRect(x, y, x + squareSize, y + squareSize, lastMovePaint)
        }
    }

    /**
     * Surligne la case sélectionnée
     */
    private fun drawSelectedSquare(canvas: Canvas) {
        selectedSquare?.let { square ->
            val x = boardOffsetX + square.col * squareSize
            val y = boardOffsetY + square.row * squareSize
            canvas.drawRect(x, y, x + squareSize, y + squareSize, selectedSquarePaint)
        }
    }

    /**
     * Dessine les indicateurs de coups légaux
     */
    private fun drawLegalMoveIndicators(canvas: Canvas) {
        for (square in legalMoveSquares) {
            val centerX = boardOffsetX + (square.col + 0.5f) * squareSize
            val centerY = boardOffsetY + (square.row + 0.5f) * squareSize

            val g = game
            val isCapture = g != null && g.getPieceAt(square) != null

            if (isCapture) {
                // Anneau pour les captures
                val radius = squareSize * 0.4f
                canvas.drawCircle(centerX, centerY, radius, legalMoveCaptureStrokePaint)
            } else {
                // Point pour les cases vides
                val radius = squareSize * 0.15f
                canvas.drawCircle(centerX, centerY, radius, legalMovePaint)
            }
        }
    }

    /**
     * Dessine toutes les pièces sur le plateau
     */
    private fun drawPieces(canvas: Canvas) {
        val g = game ?: return

        // Offset pour centrer les sprites (70% de la case)
        val pieceOffset = squareSize * 0.15f

        for (row in 0..7) {
            for (col in 0..7) {
                val square = Square(row, col)

                // Ne pas dessiner la pièce en cours de drag
                if (isDragging && square == draggedFrom) continue

                val piece = g.getPieceAt(square) ?: continue
                val bitmap = getScaledPieceBitmap(piece) ?: continue

                val x = boardOffsetX + col * squareSize + pieceOffset
                val y = boardOffsetY + row * squareSize + pieceOffset

                canvas.drawBitmap(bitmap, x, y, null)
            }
        }
    }

    /**
     * Dessine la pièce en cours de drag
     */
    private fun drawDraggedPiece(canvas: Canvas) {
        val piece = draggedPiece ?: return
        val bitmap = getScaledPieceBitmap(piece) ?: return

        // Centrer sous le doigt (sprite à 70% de la case)
        val pieceSize = squareSize * 0.7f
        val x = dragX - pieceSize / 2
        val y = dragY - pieceSize / 2

        // Ombre légère pour la pièce draggée
        val shadowPaint = Paint().apply {
            alpha = 100
        }
        canvas.drawBitmap(bitmap, x + 4, y + 4, shadowPaint)
        canvas.drawBitmap(bitmap, x, y, null)
    }

    /**
     * Dessine les coordonnées (a-h, 1-8)
     */
    private fun drawCoordinates(canvas: Canvas) {
        // Files (a-h) en bas
        for (col in 0..7) {
            val file = ('a' + col).toString()
            val x = boardOffsetX + (col + 0.5f) * squareSize
            val y = boardOffsetY + boardSize + coordinateTextPaint.textSize * 1.5f
            canvas.drawText(file, x, y, coordinateTextPaint)
        }

        // Rangs (1-8) à droite
        for (row in 0..7) {
            val rank = (8 - row).toString()
            val x = boardOffsetX + boardSize + coordinateTextPaint.textSize * 2f
            val y = boardOffsetY + (row + 0.6f) * squareSize
            canvas.drawText(rank, x, y, coordinateTextPaint)
        }
    }

    /**
     * Charge les sprites des pièces depuis les assets
     */
    private fun loadPieceBitmaps() {
        for (color in PieceColor.values()) {
            for (type in PieceType.values()) {
                val piece = Piece(type, color)
                val path = piece.getSpritePath()
                try {
                    context.assets.open(path).use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            pieceBitmaps[piece.notation] = bitmap
                        } else {
                            pieceBitmaps[piece.notation] = createFallbackBitmap(piece)
                        }
                    }
                } catch (e: IOException) {
                    pieceBitmaps[piece.notation] = createFallbackBitmap(piece)
                }
            }
        }
    }

    /**
     * Crée un bitmap de fallback avec le symbole de la pièce
     */
    private fun createFallbackBitmap(piece: Piece): Bitmap {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fond de la pièce
        val bgPaint = Paint().apply {
            color = if (piece.color == PieceColor.WHITE) Color.WHITE else Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, bgPaint)

        // Bordure
        val borderPaint = Paint().apply {
            color = if (piece.color == PieceColor.WHITE) Color.BLACK else Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, borderPaint)

        // Texte de la pièce
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.color == PieceColor.WHITE) Color.BLACK else Color.WHITE
            textSize = size / 2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val symbol = when (piece.type) {
            PieceType.KING -> "K"
            PieceType.QUEEN -> "Q"
            PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"
            PieceType.KNIGHT -> "N"
            PieceType.PAWN -> "P"
        }
        val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(symbol, size / 2f, textY, textPaint)

        return bitmap
    }

    /**
     * Redimensionne les sprites à 70% de la taille des cases
     */
    private fun scalePieceBitmaps() {
        if (squareSize <= 0) return

        // Libérer les anciens bitmaps
        scaledPieceBitmaps.values.forEach { it.recycle() }
        scaledPieceBitmaps.clear()

        // Sprites à 70% de la taille de la case
        val pieceSize = (squareSize * 0.7f).toInt()

        for ((key, original) in pieceBitmaps) {
            var scaled = Bitmap.createScaledBitmap(
                original,
                pieceSize,
                pieceSize,
                true
            )

            // En mode 2 joueurs, retourner les pièces noires de 180°
            if (isTwoPlayerMode && key.first().isLowerCase()) {
                val matrix = Matrix().apply {
                    postRotate(180f, pieceSize / 2f, pieceSize / 2f)
                }
                val rotated = Bitmap.createBitmap(
                    scaled,
                    0, 0,
                    scaled.width, scaled.height,
                    matrix,
                    true
                )
                if (rotated != scaled) scaled.recycle()
                scaled = rotated
            }

            scaledPieceBitmaps[key] = scaled
        }
    }

    /**
     * Récupère le bitmap redimensionné d'une pièce
     */
    fun getScaledPieceBitmap(piece: Piece): Bitmap? {
        if (pieceBitmaps.isEmpty()) {
            loadPieceBitmaps()
            scalePieceBitmaps()
        }
        return scaledPieceBitmaps[piece.notation]
    }

    /**
     * Définit la case sélectionnée et les coups légaux
     */
    fun setSelectedSquare(square: Square?, legalMoves: List<Square> = emptyList()) {
        selectedSquare = square
        legalMoveSquares = legalMoves
        invalidate()
    }

    /**
     * Définit le dernier coup joué (pour le surlignage)
     */
    fun setLastMove(from: Square?, to: Square?) {
        lastMoveFrom = from
        lastMoveTo = to
        invalidate()
    }

    /**
     * Force le rafraîchissement de la vue
     */
    fun refresh() {
        invalidate()
    }

    /**
     * Gestion des événements tactiles (tap et drag & drop)
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y

                val square = getTouchedSquare(event.x, event.y)
                if (square != null) {
                    val piece = game?.getPieceAt(square)
                    if (piece != null && piece.color == game?.currentTurn) {
                        // Préparer le drag potentiel
                        draggedPiece = piece
                        draggedFrom = square
                        dragX = event.x
                        dragY = event.y
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggedPiece != null && !isDragging) {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)

                    if (dx > dragThreshold || dy > dragThreshold) {
                        isDragging = true
                        dragX = event.x
                        dragY = event.y
                        invalidate()
                    }
                } else if (isDragging) {
                    dragX = event.x
                    dragY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    // Gérer le drop
                    val dropSquare = getTouchedSquare(event.x, event.y)
                    if (dropSquare != null && draggedFrom != null) {
                        listener?.onPieceDragged(draggedFrom!!, dropSquare)
                    }
                    isDragging = false
                    draggedPiece = null
                    draggedFrom = null
                    invalidate()
                } else {
                    // Gérer le tap
                    val square = getTouchedSquare(event.x, event.y)
                    if (square != null) {
                        listener?.onSquareClicked(square)
                    }
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Convertit les coordonnées d'écran en case de l'échiquier
     */
    private fun getTouchedSquare(x: Float, y: Float): Square? {
        val col = ((x - boardOffsetX) / squareSize).toInt()
        val row = ((y - boardOffsetY) / squareSize).toInt()

        return if (row in 0..7 && col in 0..7) {
            Square(row, col)
        } else {
            null
        }
    }
}
