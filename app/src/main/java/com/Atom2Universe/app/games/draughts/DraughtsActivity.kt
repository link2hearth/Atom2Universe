package com.Atom2Universe.app.games.draughts

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
import com.Atom2Universe.app.games.draughts.ai.DraughtsAI
import com.Atom2Universe.app.util.enableImmersiveMode
import androidx.core.content.edit

class DraughtsActivity : ThemedActivity(),
    DraughtsView.DraughtsViewListener,
    DraughtsAI.AIListener {

    private val game = DraughtsGame()
    private var ai: DraughtsAI? = null
    private var currentDifficulty = DraughtsDifficulty.STANDARD

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
    }

    private fun setupDifficultySpinner() {
        val labels = difficulties.map { getString(it.labelResId) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(difficulties.indexOf(DraughtsDifficulty.STANDARD))

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val selected = difficulties[position]
                if (selected != currentDifficulty) {
                    currentDifficulty = selected
                    ai?.cancel()
                    ai = if (currentDifficulty.hasAI()) DraughtsAI(currentDifficulty) else null
                    draughtsView.isTwoPlayerMode = (currentDifficulty == DraughtsDifficulty.TWO_PLAYER)
                    confirmNewGame()
                }
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
        AlertDialog.Builder(this)
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

    override fun onAIError(error: String) {
        aiThinking = false
        animating = false
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
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

        // Petite pause avant de démarrer
        timerHandler.postDelayed({ scheduleNextStep() }, 200)
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
            game.isGameOver -> {
                val w = if (game.winner == DraughtsPieceColor.WHITE)
                    getString(R.string.draughts_color_white) else getString(R.string.draughts_color_black)
                getString(R.string.draughts_status_game_over, w)
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
        if (winner == DraughtsPieceColor.WHITE && currentDifficulty.hasAI()) {
            val reward = when (currentDifficulty) {
                DraughtsDifficulty.TRAINING -> 10
                DraughtsDifficulty.STANDARD -> 20
                DraughtsDifficulty.EXPERT   -> 50
                else -> 0
            }
            if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        }
        if (winner == DraughtsPieceColor.WHITE && currentDifficulty.hasAI() && currentDifficulty.gachaTickets > 0) {
            showVictoryDialog()
        } else {
            val winnerStr = when (winner) {
                DraughtsPieceColor.WHITE -> getString(R.string.draughts_color_white)
                DraughtsPieceColor.BLACK -> getString(R.string.draughts_color_black)
                null -> getString(R.string.draughts_draw)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.draughts_game_over_title)
                .setMessage(getString(R.string.draughts_status_game_over, winnerStr))
                .setPositiveButton(R.string.draughts_new_game) { _, _ -> startNewGame() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showVictoryDialog() {
        val tickets = currentDifficulty.gachaTickets
        val boost = currentDifficulty.boostMultiplier
        val duration = currentDifficulty.boostDurationSeconds / 60
        AlertDialog.Builder(this)
            .setTitle(R.string.draughts_victory_title)
            .setMessage(getString(R.string.draughts_victory_message, tickets, boost, duration))
            .setPositiveButton(R.string.draughts_new_game) { _, _ -> startNewGame() }
            .setCancelable(false)
            .show()
    }

    // ── Timer ──────────────────────────────────────────────────────────────────

    private fun startTimer() {
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
        if (game.moveCount == 0) return
        getSharedPreferences("draughts_save", MODE_PRIVATE).edit {
            putString("state", game.serialize())
            putString("difficulty", currentDifficulty.name)
            putLong("elapsed_time", elapsedTimeMs)
        }
    }

    private fun clearSave() {
        getSharedPreferences("draughts_save", MODE_PRIVATE).edit { clear() }
    }

    private fun loadSavedGame() {
        val prefs = getSharedPreferences("draughts_save", MODE_PRIVATE)
        val state = prefs.getString("state", null)
        if (state == null) { startNewGame(); return }

        AlertDialog.Builder(this)
            .setTitle(R.string.draughts_resume_title)
            .setMessage(R.string.draughts_resume_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val diffName = prefs.getString("difficulty", DraughtsDifficulty.STANDARD.name)
                currentDifficulty = try {
                    DraughtsDifficulty.valueOf(diffName ?: DraughtsDifficulty.STANDARD.name)
                } catch (e: Exception) { DraughtsDifficulty.STANDARD }
                difficultySpinner.setSelection(difficulties.indexOf(currentDifficulty))
                game.deserialize(state)
                elapsedTimeMs = prefs.getLong("elapsed_time", 0)
                timerText.text = formatTime(elapsedTimeMs)
                ai = if (currentDifficulty.hasAI()) DraughtsAI(currentDifficulty) else null
                draughtsView.isTwoPlayerMode = (currentDifficulty == DraughtsDifficulty.TWO_PLAYER)
                draughtsView.game = game
                draughtsView.refresh()
                refreshMustCaptureHighlight()
                if (!game.isGameOver) startTimer()
                updateStatus()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> clearSave(); startNewGame() }
            .setCancelable(false)
            .show()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
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
        if (!game.isGameOver && game.moveCount > 0) {
            startTimeMs = System.currentTimeMillis()
            timerRunning = true
            timerHandler.post(timerRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ai?.cancel()
        timerHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val ANIM_STEP_MS = 500L
    }
}
