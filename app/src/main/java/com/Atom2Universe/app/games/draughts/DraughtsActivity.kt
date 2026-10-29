package com.Atom2Universe.app.games.draughts

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.draughts.ai.DraughtsAI
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import androidx.core.content.edit

class DraughtsActivity : ThemedActivity(),
    DraughtsView.DraughtsViewListener,
    DraughtsAI.AIListener {

    private val game = DraughtsGame()
    private var ai: DraughtsAI? = null
    private var currentDifficulty = DraughtsDifficulty.STANDARD
    /** La difficulté la plus basse jouée dans cette partie : c'est elle qui paie. */
    private var rewardDifficulty = DraughtsDifficulty.STANDARD

    private lateinit var draughtsView: DraughtsView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var newGameButton: Button

    private var elapsedTimeMs = 0L
    private var startTimeMs = 0L
    private var timerRunning = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                elapsedTimeMs += System.currentTimeMillis() - startTimeMs
                startTimeMs = System.currentTimeMillis()
                timerText.text = formatTime(elapsedTimeMs)
                timerHandler.postDelayed(this, 500)
            }
        }
    }

    private val difficulties = DraughtsDifficulty.values().toList()
    private var aiThinking = false
    private var animating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_draughts)

        enableImmersiveMode()

        draughtsView = findViewById(R.id.draughts_view)
        statusText = findViewById(R.id.draughts_status_text)
        timerText = findViewById(R.id.draughts_timer_text)
        difficultySpinner = findViewById(R.id.draughts_difficulty_spinner)
        newGameButton = findViewById(R.id.draughts_new_game_button)

        draughtsView.game = game
        draughtsView.listener = this

        setupDifficultySpinner()
        setupNewGameButton()
        setupBackButton()

        loadSavedGame()
    }

    private fun setupBackButton() {
        findViewById<View>(R.id.draughts_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.draughts_info_button).setOnClickListener {
            showGameRules(R.string.draughts_title, R.string.draughts_rules)
        }
    }

    private fun setupDifficultySpinner() {
        val labels = difficulties.map { getString(it.labelResId) }
        val adapter = ArrayAdapter(this, com.Atom2Universe.app.R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(com.Atom2Universe.app.R.layout.item_spinner_dropdown)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(difficulties.indexOf(DraughtsDifficulty.STANDARD))

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val selected = difficulties[position]
                if (selected == currentDifficulty) return
                // Comme aux échecs : la partie continue avec la nouvelle difficulté, et la plus
                // basse jouée paie (le mode 2 joueurs ne paie rien). Un coup de l'IA en cours est abandonné.
                ai?.cancel()
                stopAnimation()
                currentDifficulty = selected
                rewardDifficulty = if (game.moveCount == 0) currentDifficulty
                    else rewardDifficulty.lowerForReward(currentDifficulty)
                ai = if (currentDifficulty.hasAI()) DraughtsAI(currentDifficulty) else null
                draughtsView.isTwoPlayerMode = (currentDifficulty == DraughtsDifficulty.TWO_PLAYER)
                draughtsView.refresh()
                updateStatus()
                if (game.moveCount > 0) saveGame()
                if (!game.isGameOver && game.currentTurn == DraughtsPieceColor.BLACK && ai != null) requestAIMove()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupNewGameButton() {
        newGameButton.setOnClickListener {
            if (game.moveCount > 0 && !game.isGameOver) confirmNewGame()
            else startNewGame()
        }
    }

    private fun confirmNewGame() {
        if (game.moveCount == 0 || game.isGameOver) { startNewGame(); return }
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.draughts_confirm_new_title)
            .setMessage(R.string.draughts_confirm_new_message)
            .setPositiveButton(R.string.confirm) { _, _ -> startNewGame() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startNewGame() {
        ai?.cancel()
        stopAnimation()
        game.newGame()
        draughtsView.selectedPos = null
        draughtsView.highlightedTargets = emptyList()
        draughtsView.capturableInSelected = emptyList()
        draughtsView.mustCapturePositions = emptySet()
        draughtsView.clearLastMove()
        draughtsView.animPiece = null
        draughtsView.hiddenSquares = emptySet()
        draughtsView.refresh()
        elapsedTimeMs = 0
        timerText.text = "00:00"
        clearSave()
        ai = if (currentDifficulty.hasAI()) DraughtsAI(currentDifficulty) else null
        rewardDifficulty = currentDifficulty
        draughtsView.isTwoPlayerMode = (currentDifficulty == DraughtsDifficulty.TWO_PLAYER)
        aiThinking = false
        animating = false
        startTimer()
        updateStatus()
        refreshMustCaptureHighlight()
    }

    override fun onSquareTapped(pos: DraughtsPos) {
        if (game.isGameOver || aiThinking || animating) return
        if (currentDifficulty.hasAI() && game.currentTurn == DraughtsPieceColor.BLACK) return

        val piece = game.getPieceAt(pos)
        val currentSelected = draughtsView.selectedPos

        if (piece != null && piece.color == game.currentTurn) {
            val legalMovesFrom = game.getLegalMovesFrom(pos)
            if (legalMovesFrom.isNotEmpty()) {
                draughtsView.selectedPos = pos
                draughtsView.highlightedTargets = legalMovesFrom.map { it.to }.distinct()
                draughtsView.capturableInSelected = legalMovesFrom.flatMap { it.captures }.distinct()
                draughtsView.refresh()
            } else {
                // La pièce ne peut pas jouer : prise obligatoire ailleurs
                draughtsView.selectedPos = null
                draughtsView.highlightedTargets = emptyList()
                draughtsView.capturableInSelected = emptyList()
                draughtsView.refresh()
                if (game.hasMandatoryCaptures()) {
                    Toast.makeText(this, R.string.draughts_mandatory_capture, Toast.LENGTH_SHORT).show()
                }
            }
        } else if (currentSelected != null) {
            val legalMoves = game.getLegalMovesFrom(currentSelected)
            val move = legalMoves.find { it.to == pos }
            if (move != null) {
                clearSelection()
                executeMove(move)
            } else {
                clearSelection()
            }
        }
    }

    private fun clearSelection() {
        draughtsView.selectedPos = null
        draughtsView.highlightedTargets = emptyList()
        draughtsView.capturableInSelected = emptyList()
        draughtsView.refresh()
    }

    private fun executeMove(move: DraughtsMove) {
        game.makeMove(move)
        draughtsView.setLastMove(move.from, move.to)
        draughtsView.refresh()
        refreshMustCaptureHighlight()
        updateStatus()
        saveGame()

        if (game.isGameOver) {
            stopTimer()
            onGameOver()
        } else if (currentDifficulty.hasAI() && game.currentTurn == DraughtsPieceColor.BLACK) {
            requestAIMove()
        }
    }

    private fun requestAIMove() {
        aiThinking = true
        updateStatus()
        ai?.findMove(game, this, lifecycleScope)
    }

    override fun onAIThinking() { updateStatus() }

    override fun onAIMoveFound(move: DraughtsMove, thinkTimeMs: Long) {
        aiThinking = false
        animateAIMove(move)
    }

    override fun onAIError(messageRes: Int) {
        aiThinking = false
        animating = false
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    // ── Animation ──────────────────────────────────────────────────────────────

    private var animJob: Runnable? = null

    private fun animateAIMove(move: DraughtsMove) {
        val path = move.animPath
        if (path.isEmpty()) {
            // Coup simple (pas de capture) : pas d'animation intermédiaire
            executeMoveFinal(move)
            return
        }

        animating = true
        updateStatus()

        val piece = game.getPieceAt(move.from) ?: run { executeMoveFinal(move); return }

        // Masquer la pièce à sa position de départ, la placer en animation
        val hiddenNow = mutableSetOf(move.from)
        draughtsView.hiddenSquares = hiddenNow.toSet()
        draughtsView.animPiece = piece to move.from
        draughtsView.refresh()

        var stepIndex = 0

        fun scheduleNextStep() {
            val r = Runnable {
                if (stepIndex >= path.size) {
                    // Fin de l'animation → appliquer le coup réel
                    draughtsView.animPiece = null
                    draughtsView.hiddenSquares = emptySet()
                    animating = false
                    executeMoveFinal(move)
                    return@Runnable
                }
                val (landPos, capturedPos) = path[stepIndex]
                capturedPos?.let { hiddenNow.add(it) }
                draughtsView.hiddenSquares = hiddenNow.toSet()
                draughtsView.animPiece = piece to landPos
                draughtsView.refresh()
                stepIndex++
                scheduleNextStep()
            }
            animJob = r
            timerHandler.postDelayed(r, ANIM_STEP_MS)
        }

        // Petite pause avant de démarrer (suivie aussi par animJob, pour que stopAnimation l'arrête)
        val first = Runnable { scheduleNextStep() }
        animJob = first
        timerHandler.postDelayed(first, 200)
    }

    private fun stopAnimation() {
        animJob?.let { timerHandler.removeCallbacks(it) }
        animJob = null
        draughtsView.animPiece = null
        draughtsView.hiddenSquares = emptySet()
        animating = false
        aiThinking = false
    }

    private fun executeMoveFinal(move: DraughtsMove) {
        game.makeMove(move)
        draughtsView.setLastMove(move.from, move.to)
        draughtsView.refresh()
        refreshMustCaptureHighlight()
        updateStatus()
        saveGame()

        if (game.isGameOver) {
            stopTimer()
            onGameOver()
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private fun refreshMustCaptureHighlight() {
        draughtsView.mustCapturePositions = if (game.hasMandatoryCaptures())
            game.mandatoryPiecePositions() else emptySet()
        draughtsView.refresh()
    }

    private fun updateStatus() {
        statusText.text = when {
            game.isGameOver -> when (game.winner) {
                DraughtsPieceColor.WHITE -> getString(R.string.draughts_status_game_over, getString(R.string.draughts_color_white))
                DraughtsPieceColor.BLACK -> getString(R.string.draughts_status_game_over, getString(R.string.draughts_color_black))
                null -> getString(R.string.draughts_status_draw)
            }
            aiThinking || animating -> getString(R.string.draughts_status_ai_thinking)
            game.currentTurn == DraughtsPieceColor.WHITE -> getString(R.string.draughts_status_white_turn)
            else -> getString(R.string.draughts_status_black_turn)
        }
    }

    private fun onGameOver() {
        val winner = game.winner
        val statsRepo = GameStatsRepository(this)
        statsRepo.recordDraughtsPlayed()
        if (winner == DraughtsPieceColor.WHITE) statsRepo.recordDraughtsWon()
        val reward = if (winner == DraughtsPieceColor.WHITE && currentDifficulty.hasAI()) {
            NeutrinoRewards.draughts(rewardDifficulty)
        } else 0
        if (reward > 0) {
            NeutrinoRepository(this).addBalance(reward)
            showVictoryDialog(reward)
        } else {
            val message = when (winner) {
                DraughtsPieceColor.WHITE -> getString(R.string.draughts_status_game_over, getString(R.string.draughts_color_white))
                DraughtsPieceColor.BLACK -> getString(R.string.draughts_status_game_over, getString(R.string.draughts_color_black))
                null -> getString(R.string.draughts_status_draw)
            }
            com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
                .setTitle(R.string.draughts_game_over_title)
                .setMessage(message)
                .setPositiveButton(R.string.draughts_new_game) { _, _ -> startNewGame() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showVictoryDialog(reward: Int) {
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.draughts_victory_title)
            .setMessage(getString(R.string.draughts_victory_message, reward))
            .setPositiveButton(R.string.draughts_new_game) { _, _ -> startNewGame() }
            .setCancelable(false)
            .show()
    }

    // ── Timer ──────────────────────────────────────────────────────────────────

    private fun startTimer() {
        if (timerRunning) return
        startTimeMs = System.currentTimeMillis()
        timerRunning = true
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private fun saveGame() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
            putString(KEY_DIFFICULTY, currentDifficulty.name)
            putLong(KEY_ELAPSED, elapsedTimeMs)
            putGame(game, rewardDifficulty)
        }
    }

    private fun clearSave() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit { clear() }
    }

    private fun loadSavedGame() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getString(KEY_STATE, null) == null) { startNewGame(); return }

        val diffName = prefs.getString(KEY_DIFFICULTY, DraughtsDifficulty.STANDARD.name)
        currentDifficulty = try {
            DraughtsDifficulty.valueOf(diffName ?: DraughtsDifficulty.STANDARD.name)
        } catch (e: Exception) { DraughtsDifficulty.STANDARD }
        difficultySpinner.setSelection(difficulties.indexOf(currentDifficulty))
        rewardDifficulty = restoreGame(prefs, game, currentDifficulty)
        elapsedTimeMs = prefs.getLong(KEY_ELAPSED, 0)
        timerText.text = formatTime(elapsedTimeMs)
        ai = if (currentDifficulty.hasAI()) DraughtsAI(currentDifficulty) else null
        draughtsView.isTwoPlayerMode = (currentDifficulty == DraughtsDifficulty.TWO_PLAYER)
        draughtsView.game = game
        draughtsView.refresh()
        refreshMustCaptureHighlight()
        if (!game.isGameOver) startTimer()
        updateStatus()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        ai?.cancel()
        if (timerRunning) {
            elapsedTimeMs += System.currentTimeMillis() - startTimeMs
            timerRunning = false
        }
        timerHandler.removeCallbacksAndMessages(null)
        stopAnimation()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.isGameOver) {
            startTimer()
            if (game.currentTurn == DraughtsPieceColor.BLACK && ai != null) requestAIMove()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ai?.cancel()
        timerHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val ANIM_STEP_MS = 500L

        // Sauvegarde partagée avec le widget flottant (DraughtsWidgetView)
        const val PREFS = "draughts_save"
        const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_STATE = "state"
        private const val KEY_ELAPSED = "elapsed_time"
        // Le plateau seul ne garde ni le compte des coups ni la fin : ces champs ne valent que pour meta_state
        private const val KEY_META_STATE = "meta_state"
        private const val KEY_MOVE_COUNT = "move_count"
        private const val KEY_QUIET_KING_MOVES = "quiet_king_moves"
        private const val KEY_OVER = "over"
        private const val KEY_WINNER = "winner"
        private const val KEY_REWARD_DIFFICULTY = "reward_difficulty"

        /** Écrit la partie et la difficulté qui paie ; la difficulté choisie et le chrono restent à l'appelant. */
        fun SharedPreferences.Editor.putGame(game: DraughtsGame, rewardDifficulty: DraughtsDifficulty) {
            val state = game.serialize()
            putString(KEY_STATE, state)
            putString(KEY_META_STATE, state)
            putInt(KEY_MOVE_COUNT, game.moveCount)
            putInt(KEY_QUIET_KING_MOVES, game.quietKingMoves)
            putBoolean(KEY_OVER, game.isGameOver)
            putString(KEY_WINNER, game.winner?.name)
            putString(KEY_REWARD_DIFFICULTY, rewardDifficulty.name)
        }

        /**
         * Recharge la partie dans [game] (partie neuve sans sauvegarde) ; rend la difficulté qui paie,
         * jamais plus haute que [difficulty].
         */
        fun restoreGame(prefs: SharedPreferences, game: DraughtsGame, difficulty: DraughtsDifficulty): DraughtsDifficulty {
            val state = prefs.getString(KEY_STATE, null)
            if (state == null) { game.newGame(); return difficulty }
            if (prefs.getString(KEY_META_STATE, null) == state) {
                val winner = DraughtsPieceColor.entries.find { it.name == prefs.getString(KEY_WINNER, null) }
                game.restore(state, prefs.getInt(KEY_MOVE_COUNT, 0), prefs.getInt(KEY_QUIET_KING_MOVES, 0),
                    prefs.getBoolean(KEY_OVER, false), winner)
                val savedReward = DraughtsDifficulty.entries.find { it.name == prefs.getString(KEY_REWARD_DIFFICULTY, null) }
                return (savedReward ?: difficulty).lowerForReward(difficulty)
            }
            game.deserialize(state)
            return difficulty
        }
    }
}
