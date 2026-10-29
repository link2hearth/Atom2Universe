package com.Atom2Universe.app.games.chess

import android.content.Context
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.Atom2Universe.app.LocaleHelper
import android.widget.*
import com.Atom2Universe.app.ThemedActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.chess.ai.ChessAI
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import androidx.core.content.edit
import androidx.core.view.doOnLayout

/**
 * Activité principale du jeu d'échecs
 * Gère l'orchestration entre la logique, l'IA, l'UI et la persistence
 */
class ChessActivity : ThemedActivity(),
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
    // Créée au premier coup de l'IA puis gardée (avec sa table) pour toute l'activité
    private var ai: ChessAI? = null
    private var currentDifficulty = ChessDifficulty.STANDARD
    // La difficulté la plus basse utilisée pendant la partie : c'est elle qui fixe la récompense
    private var rewardDifficulty = ChessDifficulty.STANDARD
    // Coup de l'IA qui attend son petit délai avant d'être joué
    private var pendingAIMove: Runnable? = null

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
        // Les images des pièces n'existent qu'une fois l'échiquier mesuré
        chessView.doOnLayout { updateCapturedPieces() }
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

        val adapter = ArrayAdapter(this, com.Atom2Universe.app.R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(com.Atom2Universe.app.R.layout.item_spinner_dropdown)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(difficulties.indexOf(currentDifficulty))

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (currentDifficulty == difficulties[position]) return
                cancelAI()
                currentDifficulty = difficulties[position]
                // Aucun coup joué : la nouvelle difficulté fait foi ; sinon la plus basse reste
                rewardDifficulty = if (game.moveHistory.isEmpty() && game.startFen == ChessGame.START_FEN) {
                    currentDifficulty
                } else {
                    rewardDifficulty.lowerForReward(currentDifficulty)
                }
                // Activer le flip des pièces noires en mode 2 joueurs
                chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
                updateCapturedPieces()
                updateStatus()
                if (!game.isGameOver() && game.currentTurn == PieceColor.BLACK && aiPlays()) requestAIMove()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun aiPlays(): Boolean = currentDifficulty.hasAI()

    /**
     * Configure les listeners
     */
    private fun setupListeners() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        newGameButton.setOnClickListener { onNewGameClicked() }
        findViewById<View>(R.id.info_button).setOnClickListener {
            showGameRules(R.string.chess_title, R.string.chess_rules)
        }
    }

    /**
     * Démarre une nouvelle partie
     */
    private fun startNewGame() {
        // L'ancienne recherche ne doit ni continuer ni jouer son coup dans la nouvelle partie
        cancelAI()
        ai?.clearTranspositionTable()
        game.newGame()
        rewardDifficulty = currentDifficulty
        GameStatsRepository(this).recordChessStarted()
        stopTimer()
        elapsedTimeMs = 0

        chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
        chessView.setSelectedSquare(null)
        chessView.setLastMove(null, null)
        chessView.refresh()

        // Supprimer l'ancienne sauvegarde
        deleteSave()

        startTimer()
        updateTimerDisplay()
        updateStatus()
        updateCapturedPieces()
    }

    /**
     * Gère le clic sur "Nouvelle partie"
     */
    private fun onNewGameClicked() {
        if (game.moveHistory.isNotEmpty()) {
            com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
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
        if (isTimerRunning) return
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
        timerText.text = getString(R.string.chess_time_format, minutes, seconds)
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
            game.drawReason == ChessGame.DrawReason.REPETITION -> getString(R.string.chess_status_draw_repetition)
            game.drawReason == ChessGame.DrawReason.FIFTY_MOVES -> getString(R.string.chess_status_draw_fifty)
            game.drawReason == ChessGame.DrawReason.MATERIAL -> getString(R.string.chess_status_draw_material)
            game.isInCheck -> getString(R.string.chess_status_check)
            game.currentTurn == PieceColor.BLACK && aiPlays() ->
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
        if (game.currentTurn == PieceColor.BLACK && aiPlays()) return // Tour de l'IA

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
        if (game.currentTurn == PieceColor.BLACK && aiPlays()) return false

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

        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
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
        // Coup illégal dans la position actuelle : rien ne se passe
        if (!game.makeMove(move)) return
        chessView.setLastMove(move.from, move.to)
        chessView.setSelectedSquare(null)
        chessView.refresh()

        updateStatus()
        updateCapturedPieces()

        if (game.isGameOver()) {
            onGameOver()
        } else {
            // Sauvegarder la partie après chaque coup
            saveGame()
            if (game.currentTurn == PieceColor.BLACK && aiPlays()) requestAIMove()
        }
    }

    /**
     * Demande un coup à l'IA
     */
    private fun requestAIMove() {
        updateStatus() // Afficher "IA réfléchit..."
        val engine = ai ?: ChessAI(game, currentDifficulty).also { ai = it }
        engine.setDifficulty(currentDifficulty)
        engine.findMove(this, lifecycleScope)
    }

    /**
     * Arrête la recherche de l'IA et oublie un coup trouvé pas encore joué
     */
    private fun cancelAI() {
        ai?.cancel()
        pendingAIMove?.let { timerHandler.removeCallbacks(it) }
        pendingAIMove = null
    }

    // ========== ChessAI.AIListener ==========

    override fun onAIThinking() {
        updateStatus()
    }

    override fun onAIMoveFound(move: Move, thinkTimeMs: Long) {
        pendingAIMove?.let { timerHandler.removeCallbacks(it) }
        val revision = game.revision
        // Délai léger pour que le coup ne soit pas instantané
        val play = Runnable {
            pendingAIMove = null
            // Partie changée entre-temps (nouvelle partie, mode 2 joueurs) : coup oublié
            if (game.revision != revision || game.isGameOver() ||
                game.currentTurn != PieceColor.BLACK || !aiPlays()) return@Runnable
            executeMove(move)
        }
        pendingAIMove = play
        timerHandler.postDelayed(play, 200)
    }

    override fun onAIError(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    /**
     * Gère la fin de partie
     */
    private fun onGameOver() {
        stopTimer()

        // Supprimer la sauvegarde (partie terminée)
        deleteSave()

        // Victoire du joueur contre l'IA
        if (game.isCheckmate && game.currentTurn == PieceColor.BLACK && aiPlays()) {
            GameStatsRepository(this).recordChessWon()
            Toast.makeText(this, R.string.chess_victory_title, Toast.LENGTH_SHORT).show()
            val reward = NeutrinoRewards.chess(rewardDifficulty)
            if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        }
    }

    // ========== Sauvegarde ==========

    /**
     * Sauvegarde la partie en cours : position de départ + coups, rejoués au chargement
     * (répétitions et historique reviennent). Le widget flottant écrit le même format.
     */
    private fun saveGame() {
        // Une partie finie ne revient jamais comme partie vivante
        if (game.isGameOver()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit {
            putString(KEY_START_FEN, game.startFen)
            putString(KEY_MOVES, game.movesInLongNotation().joinToString(" "))
            putString(KEY_FEN, game.toFEN())
            putString(KEY_DIFFICULTY, currentDifficulty.name)
            putString(KEY_REWARD_DIFFICULTY, rewardDifficulty.name)
            putLong(KEY_ELAPSED, elapsedTimeMs)

            val capturedPiecesJson = game.capturedPieces.joinToString(",") { piece ->
                "${piece.type.notation}${piece.color.name}"
            }
            putString(KEY_CAPTURED, capturedPiecesJson)
        }
    }

    /**
     * Charge la partie sauvegardée si elle existe
     */
    private fun loadSavedGame() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val startFen = prefs.getString(KEY_START_FEN, null)
        val fen = prefs.getString(KEY_FEN, null)

        // Pas de sauvegarde (ou illisible), démarrer une nouvelle partie
        if (startFen == null || fen == null || fen.split(" ").size < 4) {
            startNewGame()
            return
        }

        // Restaurer la partie
        currentDifficulty = ChessDifficulty.values().find { it.name == prefs.getString(KEY_DIFFICULTY, null) }
            ?: ChessDifficulty.STANDARD
        difficultySpinner.setSelection(ChessDifficulty.values().indexOf(currentDifficulty))

        val moves = prefs.getString(KEY_MOVES, null).orEmpty().split(" ").filter { it.isNotEmpty() }
        // Sauvegarde illisible en rejeu : on reprend la position seule
        val replayed = game.replay(startFen, moves) && game.toFEN() == fen
        if (!replayed) game.fromFEN(fen)
        if (game.isGameOver()) {
            startNewGame()
            return
        }

        val savedReward = ChessDifficulty.values().find { it.name == prefs.getString(KEY_REWARD_DIFFICULTY, null) }
        rewardDifficulty = (savedReward ?: currentDifficulty).lowerForReward(currentDifficulty)
        elapsedTimeMs = prefs.getLong(KEY_ELAPSED, 0)

        // Restaurer les pièces capturées
        game.capturedPieces.clear()
        val capturedPiecesJson = prefs.getString(KEY_CAPTURED, "")
        if (!capturedPiecesJson.isNullOrEmpty()) {
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

        chessView.isTwoPlayerMode = (currentDifficulty == ChessDifficulty.TWO_PLAYER)
        chessView.setSelectedSquare(null)
        val lastMove = game.moveHistory.lastOrNull()
        chessView.setLastMove(lastMove?.from, lastMove?.to)
        chessView.refresh()

        startTimer()
        updateTimerDisplay()
        updateStatus()
        updateCapturedPieces()
    }

    /**
     * Supprime la sauvegarde
     */
    private fun deleteSave() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit { clear() }
    }

    // ========== Lifecycle ==========

    override fun onPause() {
        super.onPause()
        stopTimer()
        cancelAI()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.isGameOver()) {
            startTimer()
            if (game.currentTurn == PieceColor.BLACK && aiPlays()) requestAIMove()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
        cancelAI()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rafraîchir l'affichage après le changement d'orientation
        chessView.refresh()
        updateCapturedPieces()
    }

    companion object {
        // Partagé avec le widget flottant (ChessWidgetView), qui lit et écrit les mêmes clés
        const val PREFS = "chess_save"
        const val KEY_START_FEN = "start_fen"
        const val KEY_MOVES = "moves"
        const val KEY_FEN = "fen"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_REWARD_DIFFICULTY = "reward_difficulty"
        const val KEY_ELAPSED = "elapsed_time"
        const val KEY_CAPTURED = "captured_pieces"
    }
}
