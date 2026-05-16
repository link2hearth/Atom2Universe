package com.Atom2Universe.app.games.solitaire

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.Atom2Universe.app.LocaleHelper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.solitaire.data.SolitaireDatabase
import com.Atom2Universe.app.games.solitaire.data.SolitaireSaveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.util.enableImmersiveMode

class SolitaireActivity : AppCompatActivity(), SolitaireView.OnGameActionListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var gameView: SolitaireView
    private lateinit var timerText: TextView
    private lateinit var movesText: TextView
    private lateinit var statusText: TextView
    private lateinit var newGameButton: Button
    private lateinit var autoFinishButton: Button

    private val game = SolitaireGame()
    private var moves = 0
    private var elapsedTimeMs: Long = 0
    private var startTimeMs: Long = 0
    private var isTimerRunning = false
    private var isGameWon = false
    private var isAutoFinishing = false

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

    // Auto-finish runnable
    private val autoFinishRunnable = object : Runnable {
        override fun run() {
            if (!isAutoFinishing) return

            val move = game.findNextAutoFinishMove()
            if (move != null) {
                val (card, pileType, pileIndex) = move
                game.autoMoveToFoundation(card, pileType, pileIndex)
                moves++
                updateMovesDisplay()
                gameView.refresh()

                if (game.isGameWon()) {
                    isAutoFinishing = false
                    onGameWon()
                } else {
                    timerHandler.postDelayed(this, 80) // Fast but visible
                }
            } else {
                isAutoFinishing = false
                updateAutoFinishButton()
            }
        }
    }

    private val database by lazy { SolitaireDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_solitaire)

        initViews()
        setupListeners()
        loadSavedGame()
    }

    private fun initViews() {
        gameView = findViewById(R.id.solitaire_view)
        timerText = findViewById(R.id.timer_text)
        movesText = findViewById(R.id.moves_text)
        statusText = findViewById(R.id.status_text)
        newGameButton = findViewById(R.id.new_game_button)
        autoFinishButton = findViewById(R.id.auto_finish_button)

        gameView.game = game
        gameView.listener = this
    }

    private fun setupListeners() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        newGameButton.setOnClickListener { onNewGameClicked() }
        autoFinishButton.setOnClickListener { startAutoFinish() }
    }

    private fun updateAutoFinishButton() {
        autoFinishButton.visibility = if (game.canAutoFinish() && !isGameWon && !isAutoFinishing) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun startAutoFinish() {
        if (isAutoFinishing || isGameWon) return
        isAutoFinishing = true
        autoFinishButton.visibility = View.GONE
        timerHandler.post(autoFinishRunnable)
    }

    private fun loadSavedGame() {
        lifecycleScope.launch {
            val save = withContext(Dispatchers.IO) {
                database.solitaireDao().getSave()
            }

            if (save != null && !save.isWon) {
                showResumeDialog(save)
            } else {
                startNewGame()
            }
        }
    }

    private fun showResumeDialog(save: SolitaireSaveEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.solitaire_dialog_resume_title)
            .setMessage(R.string.solitaire_dialog_resume_message)
            .setPositiveButton(R.string.sudoku_resume_game) { _, _ ->
                restoreGame(save)
            }
            .setNegativeButton(R.string.sudoku_start_new) { _, _ ->
                startNewGame()
            }
            .setCancelable(false)
            .show()
    }

    private fun restoreGame(save: SolitaireSaveEntity) {
        save.restoreGame(game)
        moves = save.moves
        elapsedTimeMs = save.elapsedTimeMs
        isGameWon = false

        updateMovesDisplay()
        gameView.loadNewCardBack() // Random card back for this session
        gameView.refresh()
        startTimer()
        setStatus(getString(R.string.solitaire_status_restored))
        updateAutoFinishButton()
    }

    private fun onNewGameClicked() {
        if (moves > 0 && !isGameWon) {
            AlertDialog.Builder(this)
                .setTitle(R.string.solitaire_dialog_new_title)
                .setMessage(R.string.solitaire_dialog_new_message)
                .setPositiveButton(R.string.confirm) { _, _ -> startNewGame() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            startNewGame()
        }
    }

    private fun startNewGame() {
        game.newGame()
        moves = 0
        elapsedTimeMs = 0
        isGameWon = false
        isAutoFinishing = false

        updateMovesDisplay()
        gameView.loadNewCardBack() // New random card back design
        gameView.refresh()
        startTimer()
        setStatus(getString(R.string.solitaire_status_new_game))
        saveGame()
        autoFinishButton.visibility = View.GONE
    }

    private fun startTimer() {
        startTimeMs = System.currentTimeMillis() - elapsedTimeMs
        isTimerRunning = true
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun updateTimerDisplay() {
        val seconds = (elapsedTimeMs / 1000) % 60
        val minutes = (elapsedTimeMs / 1000) / 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateMovesDisplay() {
        movesText.text = getString(R.string.solitaire_moves, moves)
    }

    private fun setStatus(message: String, isSuccess: Boolean = false) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSuccess) R.color.sudoku_status_ok else R.color.startup_text_secondary
            )
        )
    }

    // Game action callbacks
    override fun onStockClicked() {
        if (isGameWon) return
        game.drawFromStock()
        moves++
        updateMovesDisplay()
        gameView.refresh()
        saveGame()
        updateAutoFinishButton()
    }

    override fun onCardClicked(pileType: PileType, pileIndex: Int, cardIndex: Int) {
        if (isGameWon) return

        val pile = game.getPile(pileType, pileIndex) ?: return
        if (cardIndex < 0 || cardIndex >= pile.size) return

        // If we have a selection, try to move to this location
        if (game.selectedCards.isNotEmpty()) {
            val targetCard = pile.getOrNull(cardIndex)

            // Try to move to tableau
            if (pileType == PileType.TABLEAU) {
                if (game.moveToTableau(pileIndex)) {
                    moves++
                    updateMovesDisplay()
                    gameView.refresh()
                    checkWin()
                    saveGame()
                    return
                }
            }

            // Try to move to foundation
            if (pileType == PileType.FOUNDATION) {
                if (game.moveToFoundation(pileIndex)) {
                    moves++
                    updateMovesDisplay()
                    gameView.refresh()
                    checkWin()
                    saveGame()
                    return
                }
            }

            // If can't move, clear selection and select new card
            game.clearSelection()
        }

        // Select the clicked card
        game.selectCard(pileType, pileIndex, cardIndex)
        gameView.refresh()
    }

    override fun onPileClicked(pileType: PileType, pileIndex: Int) {
        if (isGameWon) return

        // If we have a selection, try to move to this empty pile
        if (game.selectedCards.isNotEmpty()) {
            when (pileType) {
                PileType.TABLEAU -> {
                    if (game.moveToTableau(pileIndex)) {
                        moves++
                        updateMovesDisplay()
                        gameView.refresh()
                        checkWin()
                        saveGame()
                        return
                    }
                }
                PileType.FOUNDATION -> {
                    if (game.moveToFoundation(pileIndex)) {
                        moves++
                        updateMovesDisplay()
                        gameView.refresh()
                        checkWin()
                        saveGame()
                        return
                    }
                }
                else -> {}
            }
        }

        game.clearSelection()
        gameView.refresh()
    }

    override fun onCardDoubleTapped(card: Card, pileType: PileType, pileIndex: Int) {
        if (isGameWon) return

        // Try to auto-move to foundation
        if (game.autoMoveToFoundation(card, pileType, pileIndex)) {
            moves++
            updateMovesDisplay()
            gameView.refresh()
            checkWin()
            saveGame()
        }
    }

    override fun onCardDragged(
        sourcePileType: PileType,
        sourcePileIndex: Int,
        sourceCardIndex: Int,
        targetPileType: PileType,
        targetPileIndex: Int
    ): Boolean {
        if (isGameWon) return false

        // First, select the cards from source
        game.clearSelection()
        if (!game.selectCard(sourcePileType, sourcePileIndex, sourceCardIndex)) {
            return false
        }

        // Try to move to target
        val success = when (targetPileType) {
            PileType.TABLEAU -> game.moveToTableau(targetPileIndex)
            PileType.FOUNDATION -> game.moveToFoundation(targetPileIndex)
            else -> false
        }

        if (success) {
            moves++
            updateMovesDisplay()
            gameView.refresh()
            checkWin()
            saveGame()
        } else {
            game.clearSelection()
        }

        return success
    }

    private fun checkWin() {
        if (game.isGameWon()) {
            onGameWon()
        } else {
            updateAutoFinishButton()
        }
    }

    private fun onGameWon() {
        isGameWon = true
        isAutoFinishing = false
        stopTimer()
        autoFinishButton.visibility = View.GONE
        setStatus(getString(R.string.solitaire_status_won), isSuccess = true)

        // Delete save on win
        lifecycleScope.launch(Dispatchers.IO) {
            database.solitaireDao().deleteSave()
        }

        NeutrinoRepository(this).addBalance(5)

        // Start victory animation
        gameView.startVictoryAnimation()
    }

    private fun saveGame() {
        if (isGameWon) return

        lifecycleScope.launch(Dispatchers.IO) {
            val save = SolitaireSaveEntity.fromGame(game, moves, elapsedTimeMs)
            database.solitaireDao().saveSave(save)
        }
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!isGameWon && moves > 0) {
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }
}
