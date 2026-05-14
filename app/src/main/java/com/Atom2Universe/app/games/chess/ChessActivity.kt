package com.Atom2Universe.app.games.chess

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.Atom2Universe.app.LocaleHelper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.chess.ai.ChessAI
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Activité principale du jeu d'échecs
 * Gère l'orchestration entre la logique, l'IA, l'UI et la persistence
 */
class ChessActivity : AppCompatActivity(),
    ChessView.ChessViewListener,
    ChessAI.AIListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    // Vues
    private lateinit var chessView: ChessView
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var newGameButton: Button
    private lateinit var capturedPiecesContainer: LinearLayout

    // Logique du jeu
    private val game = ChessGame()
    private var ai: ChessAI? = null
    private var currentDifficulty = ChessDifficulty.STANDARD

    // Timer
    private var elapsedTimeMs: Long = 0
    private var startTimeMs: Long = 0
    private var isTimerRunning = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                elapsedTimeMs = System.currentTimeMillis() - startTimeMs
                updateTimerDisplay()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_chess)

        initViews()
        setupListeners()

        // Charger la partie sauvegardée ou démarrer une nouvelle partie
        loadSavedGame()
    }

    /**
     * Initialise les vues
     */
    private fun initViews() {
        chessView = findViewById(R.id.chess_view)
        timerText = findViewById(R.id.timer_text)
        statusText = findViewById(R.id.status_text)
        difficultySpinner = findViewById(R.id.difficulty_spinner)
        newGameButton = findViewById(R.id.new_game_button)
        capturedPiecesContainer = findViewById(R.id.captured_pieces_container)

        chessView.game = game
        chessView.listener = this

        setupDifficultySpinner()
    }

    /**
     * Configure le spinner de difficulté
     */
    private fun setupDifficultySpinner() {
        val difficulties = ChessDifficulty.values()
        val labels = difficulties.map { getString(it.labelResId) }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(difficulties.indexOf(currentDifficulty))

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentDifficulty = difficulties[position]
                ai = if (currentDifficulty.hasAI()) {
                    ChessAI(game, currentDifficulty, this@ChessActivity)
                } else {
                    null
                }
                // Activer le flip des pièces noires en mode 2 joueurs
                chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Configure les listeners
     */
    private fun setupListeners() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        newGameButton.setOnClickListener { onNewGameClicked() }
    }

    /**
     * Démarre une nouvelle partie
     */
    private fun startNewGame() {
        game.newGame()
        elapsedTimeMs = 0

        ai = if (currentDifficulty.hasAI()) {
            ChessAI(game, currentDifficulty, this)
        } else {
            null
        }

        chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
        chessView.setSelectedSquare(null)
        chessView.setLastMove(null, null)
        chessView.refresh()

        // Supprimer l'ancienne sauvegarde
        deleteSave()

        startTimer()
        updateStatus()
        updateCapturedPieces()
    }

    /**
     * Gère le clic sur "Nouvelle partie"
     */
    private fun onNewGameClicked() {
        if (game.moveHistory.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.chess_confirm_new_title)
                .setMessage(R.string.chess_confirm_new_message)
                .setPositiveButton(R.string.confirm) { _, _ -> startNewGame() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            startNewGame()
        }
    }

    /**
     * Démarre le timer
     */
    private fun startTimer() {
        startTimeMs = System.currentTimeMillis() - elapsedTimeMs
        isTimerRunning = true
        timerHandler.post(timerRunnable)
    }

    /**
     * Arrête le timer
     */
    private fun stopTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    /**
     * Met à jour l'affichage du timer
     */
    private fun updateTimerDisplay() {
        val seconds = (elapsedTimeMs / 1000) % 60
        val minutes = (elapsedTimeMs / 1000) / 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Met à jour le texte de statut
     */
    private fun updateStatus() {
        val status = when {
            game.isCheckmate -> {
                val winner = if (game.currentTurn == PieceColor.WHITE)
                    getString(R.string.chess_color_black)
                else
                    getString(R.string.chess_color_white)
                getString(R.string.chess_status_checkmate, winner)
            }
            game.isStalemate -> getString(R.string.chess_status_stalemate)
            game.isInCheck -> getString(R.string.chess_status_check)
            game.currentTurn == PieceColor.BLACK && ai != null ->
                getString(R.string.chess_status_ai_thinking)
            game.currentTurn == PieceColor.WHITE ->
                getString(R.string.chess_status_your_turn)
            else -> getString(R.string.chess_status_black_turn)
        }
        statusText.text = status
    }

    /**
     * Met à jour l'affichage des pièces capturées
     */
    private fun updateCapturedPieces() {
        capturedPiecesContainer.removeAllViews()

        val capturedPieces = game.capturedPieces
        if (capturedPieces.isEmpty()) return

        val pieceSize = (32 * resources.displayMetrics.density).toInt() // 32dp en pixels

        for (piece in capturedPieces) {
            val bitmap = chessView.getScaledPieceBitmap(piece) ?: continue

            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                layoutParams = LinearLayout.LayoutParams(pieceSize, pieceSize).apply {
                    marginEnd = (4 * resources.displayMetrics.density).toInt() // 4dp spacing
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            capturedPiecesContainer.addView(imageView)
        }
    }

    // ========== ChessView.ChessViewListener ==========

    override fun onSquareClicked(square: Square) {
        if (game.isGameOver()) return
        if (game.currentTurn == PieceColor.BLACK && ai != null) return // Tour de l'IA

        val piece = game.getPieceAt(square)

        if (piece != null && piece.color == game.currentTurn) {
            // Sélectionner la pièce
            val legalMoves = game.getLegalMoves(square).map { it.to }
            chessView.setSelectedSquare(square, legalMoves)
        } else {
            // Tenter de déplacer la pièce sélectionnée
            val selectedSquare = chessView.selectedSquare
            if (selectedSquare != null) {
                attemptMove(selectedSquare, square)
            }
            chessView.setSelectedSquare(null)
        }
    }

    override fun onPieceDragged(from: Square, to: Square): Boolean {
        if (game.isGameOver()) return false
        if (game.currentTurn == PieceColor.BLACK && ai != null) return false

        return attemptMove(from, to)
    }

    /**
     * Tente de faire un coup
     */
    private fun attemptMove(from: Square, to: Square): Boolean {
        val legalMoves = game.getLegalMoves(from)
        val move = legalMoves.find { it.to == to }

        if (move != null) {
            // Gestion de la promotion
            if (move.piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7)) {
                showPromotionDialog(move)
                return true
            }

            executeMove(move)
            return true
        }

        return false
    }

    /**
     * Affiche le dialogue de promotion du pion
     */
    private fun showPromotionDialog(move: Move) {
        val options = arrayOf(
            getString(R.string.chess_promotion_queen),
            getString(R.string.chess_promotion_rook),
            getString(R.string.chess_promotion_bishop),
            getString(R.string.chess_promotion_knight)
        )

        val promotionTypes = arrayOf(
            PieceType.QUEEN,
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.KNIGHT
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.chess_promotion_title)
            .setItems(options) { _, which ->
                val promotionMove = move.copy(promotionType = promotionTypes[which])
                executeMove(promotionMove)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Exécute un coup
     */
    private fun executeMove(move: Move) {
        game.makeMove(move)
        chessView.setLastMove(move.from, move.to)
        chessView.setSelectedSquare(null)
        chessView.refresh()

        updateStatus()
        updateCapturedPieces()

        // Sauvegarder la partie après chaque coup
        saveGame()

        if (game.isGameOver()) {
            onGameOver()
        } else if (game.currentTurn == PieceColor.BLACK && ai != null) {
            requestAIMove()
        }
    }

    /**
     * Demande un coup à l'IA
     */
    private fun requestAIMove() {
        updateStatus() // Afficher "IA réfléchit..."
        ai?.findMove(this, lifecycleScope)
    }

    // ========== ChessAI.AIListener ==========

    override fun onAIThinking() {
        runOnUiThread {
            updateStatus()
        }
    }

    override fun onAIMoveFound(move: Move, thinkTimeMs: Long) {
        // Délai léger pour que le coup ne soit pas instantané
        timerHandler.postDelayed({
            // Auto-promotion à dame pour l'IA
            if (move.piece.type == PieceType.PAWN &&
                (move.to.row == 0 || move.to.row == 7) &&
                move.promotionType == null) {
                executeMove(move.copy(promotionType = PieceType.QUEEN))
            } else {
                executeMove(move)
            }
        }, 200)
    }

    override fun onAIError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Gère la fin de partie
     */
    private fun onGameOver() {
        stopTimer()

        // Supprimer la sauvegarde (partie terminée)
        deleteSave()

        // Victoire du joueur contre l'IA
        if (game.isCheckmate && game.currentTurn == PieceColor.BLACK && ai != null) {
            Toast.makeText(this, R.string.chess_victory_title, Toast.LENGTH_SHORT).show()
            NeutrinoRepository(this).addPending(1)
            Toast.makeText(this, R.string.clicker_neutrino_awarded, Toast.LENGTH_SHORT).show()
        }
    }

    // ========== Sauvegarde ==========

    /**
     * Sauvegarde la partie en cours
     */
    private fun saveGame() {
        if (game.moveHistory.isEmpty()) return // Pas de partie à sauvegarder

        val prefs = getSharedPreferences("chess_save", MODE_PRIVATE)
        prefs.edit().apply {
            putString("fen", game.toFEN())
            putString("difficulty", currentDifficulty.name)
            putLong("elapsed_time", elapsedTimeMs)
            putBoolean("is_game_over", game.isGameOver())

            // Sauvegarder les pièces capturées
            val capturedPiecesJson = game.capturedPieces.joinToString(",") { piece ->
                "${piece.type.notation}${piece.color.name}"
            }
            putString("captured_pieces", capturedPiecesJson)

            apply()
        }
    }

    /**
     * Charge la partie sauvegardée si elle existe
     */
    private fun loadSavedGame() {
        val prefs = getSharedPreferences("chess_save", MODE_PRIVATE)
        val fen = prefs.getString("fen", null)

        // Pas de sauvegarde, démarrer une nouvelle partie
        if (fen == null) {
            startNewGame()
            return
        }

        // Demander confirmation
        AlertDialog.Builder(this)
            .setTitle(R.string.chess_resume_title)
            .setMessage(R.string.chess_resume_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                // Restaurer la partie
                val difficultyName = prefs.getString("difficulty", ChessDifficulty.STANDARD.name)
                val savedDifficulty = ChessDifficulty.values().find { it.name == difficultyName }
                    ?: ChessDifficulty.STANDARD

                currentDifficulty = savedDifficulty
                difficultySpinner.setSelection(ChessDifficulty.values().indexOf(currentDifficulty))

                game.fromFEN(fen)
                elapsedTimeMs = prefs.getLong("elapsed_time", 0)

                // Restaurer les pièces capturées
                val capturedPiecesJson = prefs.getString("captured_pieces", "")
                if (!capturedPiecesJson.isNullOrEmpty()) {
                    game.capturedPieces.clear()
                    capturedPiecesJson.split(",").forEach { pieceStr ->
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

                ai = if (currentDifficulty.hasAI()) {
                    ChessAI(game, currentDifficulty, this@ChessActivity)
                } else {
                    null
                }

                chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
                chessView.setSelectedSquare(null)
                chessView.setLastMove(null, null)
                chessView.refresh()

                if (!game.isGameOver()) {
                    startTimer()
                }
                updateStatus()
                updateCapturedPieces()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                deleteSave()
                startNewGame()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Supprime la sauvegarde
     */
    private fun deleteSave() {
        getSharedPreferences("chess_save", MODE_PRIVATE).edit().clear().apply()
    }

    // ========== Lifecycle ==========

    override fun onPause() {
        super.onPause()
        stopTimer()
        ai?.cancel()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.isGameOver() && game.moveHistory.isNotEmpty()) {
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
        ai?.cancel()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rafraîchir l'affichage après le changement d'orientation
        chessView.refresh()
        updateCapturedPieces()
    }
}
