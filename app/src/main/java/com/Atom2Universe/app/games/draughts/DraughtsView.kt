package com.Atom2Universe.app.games.draughts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class DraughtsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface DraughtsViewListener {
        fun onSquareTapped(pos: DraughtsPos)
    }

    var game: DraughtsGame? = null
    var listener: DraughtsViewListener? = null
    var isTwoPlayerMode = false

    var selectedPos: DraughtsPos? = null
    var highlightedTargets: List<DraughtsPos> = emptyList()
    var capturableInSelected: List<DraughtsPos> = emptyList()
    var mustCapturePositions: Set<DraughtsPos> = emptySet()

    // Animation IA
    var animPiece: Pair<DraughtsPiece, DraughtsPos>? = null
    var hiddenSquares: Set<DraughtsPos> = emptySet()

    private var lastMoveFrom: DraughtsPos? = null
    private var lastMoveTo: DraughtsPos? = null

    // Couleurs du damier
    private val paintLight = Paint().apply { color = Color.parseColor("#F5DEB3") }
    private val paintDark  = Paint().apply { color = Color.parseColor("#8B6343") }

    // Surlignages (format Android ARGB correct)
    private val paintSelected  = Paint().apply { color = Color.argb(110, 80, 200, 80);  isAntiAlias = true }
    private val paintLastMove  = Paint().apply { color = Color.argb(80,  160, 160, 160); isAntiAlias = true }  // gris
    private val paintTarget    = Paint().apply { color = Color.argb(100, 60,  180, 60);  isAntiAlias = true }
    private val paintCapture   = Paint().apply { color = Color.argb(120, 210, 50,  50);  isAntiAlias = true }
    private val paintMustCapt  = Paint().apply {
        color = Color.argb(200, 220, 130, 0)
        style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
    }
    private val paintAnimHighlight = Paint().apply { color = Color.argb(130, 80, 200, 80); isAntiAlias = true }

    // Pièces
    private val paintWhitePiece = Paint().apply { color = Color.argb(255, 240, 235, 220); isAntiAlias = true }
    private val paintBlackPiece = Paint().apply { color = Color.argb(255, 80,  80,  80);  isAntiAlias = true }
    private val paintOutline    = Paint().apply {
        color = Color.argb(180, 60, 60, 60)
        style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
    }
    private val paintKingRing   = Paint().apply {
        color = Color.argb(255, 215, 175, 0)   // or doré opaque
        style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
    }
    private val paintKingFill   = Paint().apply { color = Color.argb(70, 215, 175, 0); isAntiAlias = true }
    private val paintDotTarget  = Paint().apply { color = Color.argb(170, 40, 160, 40); isAntiAlias = true }

    private var cellSize = 0f
    private val boardRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h).toFloat()
        cellSize = size / 10f
        val offsetX = (w - size) / 2f
        val offsetY = (h - size) / 2f
        boardRect.set(offsetX, offsetY, offsetX + size, offsetY + size)
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        drawBoard(canvas)
        drawHighlights(canvas, g)
        drawPieces(canvas, g)
        animPiece?.let { (piece, pos) -> drawPieceAt(canvas, piece, pos) }
    }

    private fun drawBoard(canvas: Canvas) {
        for (r in 0..9) for (c in 0..9) {
            canvas.drawRect(cellRect(r, c), if ((r + c) % 2 == 0) paintLight else paintDark)
        }
    }

    private fun drawHighlights(canvas: Canvas, g: DraughtsGame) {
        lastMoveFrom?.let { canvas.drawRect(cellRect(it.row, it.col), paintLastMove) }
        lastMoveTo?.let { canvas.drawRect(cellRect(it.row, it.col), paintLastMove) }

        selectedPos?.let { canvas.drawRect(cellRect(it.row, it.col), paintSelected) }

        for (pos in capturableInSelected) canvas.drawRect(cellRect(pos.row, pos.col), paintCapture)

        for (pos in highlightedTargets) {
            if (g.getPieceAt(pos) != null) {
                canvas.drawRect(cellRect(pos.row, pos.col), paintTarget)
            } else {
                val rect = cellRect(pos.row, pos.col)
                canvas.drawCircle(rect.centerX(), rect.centerY(), cellSize * 0.18f, paintDotTarget)
            }
        }

        // Position courante de l'animation
        animPiece?.let { (_, pos) ->
            canvas.drawRect(cellRect(pos.row, pos.col), paintAnimHighlight)
        }
    }

    private fun drawPieces(canvas: Canvas, g: DraughtsGame) {
        for (r in 0..9) for (c in 0..9) {
            val pos = DraughtsPos(r, c)
            if (pos in hiddenSquares) continue          // caché pendant animation
            if (selectedPos == pos) continue            // la pièce sélectionnée est redessinée en dernier
            val piece = g.board[r][c] ?: continue
            drawPieceAt(canvas, piece, pos)
        }
        // Pièce sélectionnée par-dessus
        selectedPos?.let { sel ->
            if (sel !in hiddenSquares) {
                g.board[sel.row][sel.col]?.let { drawPieceAt(canvas, it, sel) }
            }
        }
        // Indicateur prise obligatoire
        for (pos in mustCapturePositions) {
            val rect = cellRect(pos.row, pos.col)
            canvas.drawCircle(rect.centerX(), rect.centerY(), cellSize * 0.40f, paintMustCapt)
        }
    }

    private fun drawPieceAt(canvas: Canvas, piece: DraughtsPiece, pos: DraughtsPos) {
        val rect = cellRect(pos.row, pos.col)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = cellSize * 0.38f
        val fill = if (piece.color == DraughtsPieceColor.WHITE) paintWhitePiece else paintBlackPiece
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, paintOutline)
        if (piece.isKing()) {
            canvas.drawCircle(cx, cy, radius * 0.55f, paintKingFill)
            canvas.drawCircle(cx, cy, radius * 0.55f, paintKingRing)
        }
    }

    private fun cellRect(row: Int, col: Int): RectF {
        // En mode 2 joueurs, on retourne le plateau pour le joueur noir
        val flip = isTwoPlayerMode && game?.currentTurn == DraughtsPieceColor.BLACK
        val dispRow = if (flip) 9 - row else row
        val dispCol = if (flip) 9 - col else col
        val l = boardRect.left + dispCol * cellSize
        val t = boardRect.top + dispRow * cellSize
        return RectF(l, t, l + cellSize, t + cellSize)
    }

    private fun posFromTouch(x: Float, y: Float): DraughtsPos? {
        if (x < boardRect.left || x > boardRect.right || y < boardRect.top || y > boardRect.bottom) return null
        val rawCol = ((x - boardRect.left) / cellSize).toInt().coerceIn(0, 9)
        val rawRow = ((y - boardRect.top)  / cellSize).toInt().coerceIn(0, 9)
        val flip = isTwoPlayerMode && game?.currentTurn == DraughtsPieceColor.BLACK
        return DraughtsPos(if (flip) 9 - rawRow else rawRow, if (flip) 9 - rawCol else rawCol)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            posFromTouch(event.x, event.y)?.let { listener?.onSquareTapped(it) }
        }
        return true
    }

    fun setLastMove(from: DraughtsPos, to: DraughtsPos) { lastMoveFrom = from; lastMoveTo = to }
    fun clearLastMove() { lastMoveFrom = null; lastMoveTo = null }
    fun refresh() { invalidate() }
}
