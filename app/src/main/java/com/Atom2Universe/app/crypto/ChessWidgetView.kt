package com.Atom2Universe.app.crypto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.chess.ChessDifficulty
import com.Atom2Universe.app.games.chess.ChessGame
import com.Atom2Universe.app.games.chess.ChessView
import com.Atom2Universe.app.games.chess.Move
import com.Atom2Universe.app.games.chess.Piece
import com.Atom2Universe.app.games.chess.PieceColor
import com.Atom2Universe.app.games.chess.PieceType
import com.Atom2Universe.app.games.chess.Square
import com.Atom2Universe.app.games.chess.ai.ChessAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.math.hypot

/**
 * Widget affichant un échiquier interactif, avec IA selon la difficulté sauvegardée.
 *
 * Gestes :
 *  - Glisser le header   → déplacer le widget
 *  - Pincer (2 doigts)   → zoom/dézoom
 *  - Toucher le plateau  → interagir avec les pièces
 *  - Tap sur le header   → ouvre ChessActivity
 */
class ChessWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    ChessView.ChessViewListener,
    ChessAI.AIListener {

    private val chessView: ChessView
    private lateinit var capturedPiecesView: ChessCapturedPiecesView
    private lateinit var difficultyOverlay: FrameLayout
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private val game = ChessGame()
    private var ai: ChessAI? = null
    private var difficulty = ChessDifficulty.STANDARD

    // Scope pour les recherches IA (annulé quand le widget est détaché)
    private val aiScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Zoom ──────────────────────────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next
                    scaleX = currentScale
                    scaleY = currentScale
                }
                return true
            }
        })

    // ── Drag du widget (header uniquement) ────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chess_widget, this, true)

        chessView = findViewById(R.id.chess_widget_board)
        chessView.listener = this
        capturedPiecesView = findViewById(R.id.chess_captured_pieces)
        difficultyOverlay = findViewById(R.id.chess_difficulty_overlay)
        resultOverlay = findViewById(R.id.chess_result_overlay)
        resultText = findViewById(R.id.chess_result_text)

        resultOverlay.setOnClickListener {
            resultOverlay.visibility = View.GONE
        }

        // Bouton reset : affiche l'overlay de sélection de difficulté
        findViewById<TextView>(R.id.chess_btn_reset).setOnClickListener {
            difficultyOverlay.visibility = View.VISIBLE
        }

        // Boutons de l'overlay
        findViewById<TextView>(R.id.chess_diff_training).setOnClickListener {
            startNewGame(ChessDifficulty.TRAINING)
        }
        findViewById<TextView>(R.id.chess_diff_standard).setOnClickListener {
            startNewGame(ChessDifficulty.STANDARD)
        }
        findViewById<TextView>(R.id.chess_diff_expert).setOnClickListener {
            startNewGame(ChessDifficulty.EXPERT)
        }
        findViewById<TextView>(R.id.chess_diff_two_player).setOnClickListener {
            startNewGame(ChessDifficulty.TWO_PLAYER)
        }
        findViewById<TextView>(R.id.chess_diff_cancel).setOnClickListener {
            difficultyOverlay.visibility = View.GONE
        }

        reload()

        val headerArea = findViewById<FrameLayout>(R.id.chess_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
    }

    // ── Pinch-zoom : intercepter les gestes 2 doigts avant les enfants ────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return true
    }

    // ── Drag depuis le header ─────────────────────────────────────────────────
    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX
                dragDownY = event.rawY
                dragLastX = event.rawX
                dragLastY = event.rawY
                isDragging = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_UP -> skipNextDragFrame = true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - dragLastX
                            translationY += event.rawY - dragLastY
                        }
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val intent = Intent(context, com.Atom2Universe.app.games.chess.ChessActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    context.startActivity(intent)
                }
                isDragging = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                skipNextDragFrame = false
            }
        }
        return true
    }

    // ── Nouvelle partie avec difficulté choisie ───────────────────────────────
    private fun startNewGame(newDifficulty: ChessDifficulty) {
        difficultyOverlay.visibility = View.GONE
        resultOverlay.visibility = View.GONE

        ai?.cancel()
        ai = null
        difficulty = newDifficulty

        game.newGame()
        GameStatsRepository(context).recordChessStarted()

        if (difficulty.hasAI()) {
            ai = ChessAI(game, difficulty, context)
        }

        chessView.isTwoPlayerMode = (difficulty == ChessDifficulty.TWO_PLAYER)
        chessView.setSelectedSquare(null)
        chessView.setLastMove(null, null)
        chessView.refresh()
        capturedPiecesView.update(game.capturedPieces)

        // Sauvegarder la nouvelle difficulté et effacer la partie précédente
        context.getSharedPreferences("chess_save", Context.MODE_PRIVATE).edit()
            .putString("difficulty", difficulty.name)
            .remove("fen")
            .remove("captured_pieces")
            .apply()
    }

    // ── Chargement de la partie et des réglages sauvegardés ──────────────────
    fun reload() {
        ai?.cancel()
        ai = null

        val prefs = context.getSharedPreferences("chess_save", Context.MODE_PRIVATE)

        // Lire la difficulté sauvegardée par ChessActivity
        val savedDiff = prefs.getString("difficulty", null)
        difficulty = if (savedDiff != null) {
            runCatching { ChessDifficulty.valueOf(savedDiff) }.getOrDefault(ChessDifficulty.STANDARD)
        } else {
            ChessDifficulty.STANDARD
        }

        // Restaurer la position FEN
        val fen = prefs.getString("fen", null)
        if (fen != null) {
            runCatching { game.fromFEN(fen) }
        }

        // Restaurer les pièces capturées
        val capturedJson = prefs.getString("captured_pieces", "")
        game.capturedPieces.clear()
        if (!capturedJson.isNullOrEmpty()) {
            capturedJson.split(",").forEach { pieceStr ->
                if (pieceStr.isNotEmpty()) {
                    val notation = pieceStr.substring(0, 1)
                    val colorName = pieceStr.substring(1)
                    val type = PieceType.values().find { it.notation == notation }
                    val color = PieceColor.values().find { it.name == colorName }
                    if (type != null && color != null) {
                        game.capturedPieces.add(Piece(type, color))
                    }
                }
            }
        }

        // Créer l'IA selon la difficulté (null en mode 2 joueurs)
        if (difficulty.hasAI()) {
            ai = ChessAI(game, difficulty, context)
        }

        // Activer le flip des pièces noires uniquement en mode 2 joueurs
        chessView.isTwoPlayerMode = (difficulty == ChessDifficulty.TWO_PLAYER)
        chessView.game = game
        chessView.setSelectedSquare(null)
        chessView.refresh()

        capturedPiecesView.update(game.capturedPieces)

        // Si c'est déjà le tour de l'IA après chargement, la faire jouer
        triggerAIIfNeeded()
    }

    // ── ChessView.ChessViewListener ───────────────────────────────────────────

    override fun onSquareClicked(square: Square) {
        if (game.isGameOver()) return
        // Bloquer l'interaction si c'est le tour de l'IA
        if (game.currentTurn == PieceColor.BLACK && ai != null) return

        val piece = game.getPieceAt(square)
        if (piece != null && piece.color == game.currentTurn) {
            val legalMoves = game.getLegalMoves(square).map { it.to }
            chessView.setSelectedSquare(square, legalMoves)
        } else {
            val selected = chessView.selectedSquare
            if (selected != null) attemptMove(selected, square)
            chessView.setSelectedSquare(null)
        }
    }

    override fun onPieceDragged(from: Square, to: Square): Boolean {
        if (game.isGameOver()) return false
        if (game.currentTurn == PieceColor.BLACK && ai != null) return false
        return attemptMove(from, to)
    }

    private fun attemptMove(from: Square, to: Square): Boolean {
        val legalMoves = game.getLegalMoves(from)
        val move = legalMoves.find { it.to == to } ?: return false

        // Promotion : auto-dame dans le widget
        val finalMove = if (move.piece.type == PieceType.PAWN &&
            (to.row == 0 || to.row == 7) &&
            move.promotionType == null
        ) move.copy(promotionType = PieceType.QUEEN) else move

        game.makeMove(finalMove)
        chessView.setLastMove(finalMove.from, finalMove.to)
        chessView.setSelectedSquare(null)
        chessView.refresh()
        capturedPiecesView.update(game.capturedPieces)

        // Sauvegarder le FEN et les pièces capturées
        saveCapturedAndFen()

        checkGameOver()
        triggerAIIfNeeded()
        return true
    }

    private fun saveCapturedAndFen() {
        val capturedJson = game.capturedPieces.joinToString(",") { "${it.type.notation}${it.color.name}" }
        context.getSharedPreferences("chess_save", Context.MODE_PRIVATE).edit()
            .putString("fen", game.toFEN())
            .putString("captured_pieces", capturedJson)
            .apply()
    }

    private fun triggerAIIfNeeded() {
        val currentAI = ai ?: return
        if (game.isGameOver()) return
        if (game.currentTurn != PieceColor.BLACK) return
        currentAI.findMove(listener = this, scope = aiScope)
    }

    // ── ChessAI.AIListener ────────────────────────────────────────────────────

    override fun onAIThinking() {
        // Pas de feedback visuel dans le widget (trop petit)
    }

    override fun onAIMoveFound(move: Move, thinkTimeMs: Long) {
        // Petit délai pour que le coup ne soit pas instantané (comme dans ChessActivity)
        mainHandler.postDelayed({
            if (game.isGameOver()) return@postDelayed

            val finalMove = if (move.piece.type == PieceType.PAWN &&
                (move.to.row == 0 || move.to.row == 7) &&
                move.promotionType == null
            ) move.copy(promotionType = PieceType.QUEEN) else move

            game.makeMove(finalMove)
            chessView.setLastMove(finalMove.from, finalMove.to)
            chessView.setSelectedSquare(null)
            chessView.refresh()
            capturedPiecesView.update(game.capturedPieces)

            saveCapturedAndFen()
            checkGameOver()
        }, 200)
    }

    private fun checkGameOver() {
        if (!game.isGameOver()) return
        val playerWon = !game.isStalemate
            && difficulty != ChessDifficulty.TWO_PLAYER
            && game.currentTurn == PieceColor.BLACK
        val (label, color) = when {
            game.isStalemate -> "Pat !" to Color.parseColor("#94A3B8")
            difficulty == ChessDifficulty.TWO_PLAYER -> "Mat !" to Color.parseColor("#EAB308")
            // vs IA : currentTurn = couleur qui vient d'être matée
            game.currentTurn == PieceColor.WHITE -> "Mat •\nDéfaite" to Color.parseColor("#EF4444")
            else -> "Mat •\nVictoire" to Color.parseColor("#22C55E")
        }
        resultText.text = label
        resultText.setTextColor(color)
        resultOverlay.visibility = View.VISIBLE
        if (playerWon) {
            val reward = when (difficulty) {
                ChessDifficulty.TRAINING -> 10
                ChessDifficulty.STANDARD -> 20
                ChessDifficulty.EXPERT   -> 50
                else -> 0
            }
            if (reward > 0) NeutrinoRepository(context).addPending(reward)
            GameStatsRepository(context).recordChessWon()
        }
    }

    override fun onAIError(error: String) {
        // Erreur silencieuse dans le widget
    }

    override fun onDetachedFromWindow() {
        ai?.cancel()
        aiScope.coroutineContext[Job]?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
