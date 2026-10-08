package com.Atom2Universe.app.games.solitaire

import android.content.res.ColorStateList
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.kit.KitPalette
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.solitaire.data.SolitaireDatabase
import com.Atom2Universe.app.games.solitaire.data.SolitaireSaveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.util.enableImmersiveMode

class SolitaireActivity : ThemedActivity(), SolitaireView.OnGameActionListener {

    private companion object {
        const val MAX_UNDO = 200
    }

    private lateinit var palette: KitPalette
    private lateinit var gameView: SolitaireView
    private lateinit var timerText: TextView
    private lateinit var movesText: TextView
    private lateinit var statusText: TextView
    private lateinit var newGameButton: Button
    private lateinit var autoFinishButton: Button
    private lateinit var undoButton: Button

    /** Les coups annulables : la photo des piles avant le coup et le compteur d'alors. */
    private val history = ArrayDeque<Pair<SolitaireGame.Snapshot, Int>>()

    /** Photo prise avant de tenter un coup ; on ne la garde que si le coup a eu lieu. */
    private var pending: Pair<SolitaireGame.Snapshot, Int>? = null

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
        palette = KitPalette.from(this)
        findViewById<View>(R.id.solitaire_root).setTag(R.id.app_effects_scene, true)

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
        undoButton = findViewById(R.id.undo_button)

        gameView.game = game
        gameView.listener = this
        gameView.palette = palette
        styleButtons()
    }

    /** Les boutons aux couleurs du thème, comme ceux des autres jeux : pleins pour l'action principale, en relief sinon. */
    private fun styleButtons() {
        fun style(button: Button, primary: Boolean) {
            val b = button as com.google.android.material.button.MaterialButton
            b.backgroundTintList = ColorStateList.valueOf(if (primary) palette.accent else palette.raised)
            b.setTextColor(if (primary) palette.onAccent else palette.text)
            b.cornerRadius = AppearanceStyle.corner(this, 14f).toInt()
            b.strokeColor = ColorStateList.valueOf(if (primary) palette.accent else palette.outline)
            b.strokeWidth = if (primary) 0 else resources.displayMetrics.density.toInt().coerceAtLeast(1)
        }
        style(newGameButton, false)
        style(undoButton, false)
        style(autoFinishButton, true)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        newGameButton.setOnClickListener { onNewGameClicked() }
        autoFinishButton.setOnClickListener { startAutoFinish() }
        undoButton.setOnClickListener { undo() }
        updateUndoButton()
    }

    private fun beforeMove() {
        pending = game.snapshot() to moves
    }

    private fun afterMove(done: Boolean) {
        val snap = pending
        pending = null
        if (done && snap != null) {
            history.addLast(snap)
            if (history.size > MAX_UNDO) history.removeFirst()
        }
        updateUndoButton()
    }

    private fun undo() {
        if (isGameWon || isAutoFinishing) return
        val (snapshot, previousMoves) = history.removeLastOrNull() ?: return
        game.restore(snapshot)
        moves = previousMoves
        updateMovesDisplay()
        gameView.refresh()
        updateAutoFinishButton()
        updateUndoButton()
        saveGame()
    }

    private fun updateUndoButton() {
        undoButton.isEnabled = history.isNotEmpty() && !isGameWon && !isAutoFinishing
        undoButton.alpha = if (undoButton.isEnabled) 1f else 0.4f
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
        updateUndoButton()
        autoFinishButton.visibility = View.GONE
        timerHandler.post(autoFinishRunnable)
    }

    private fun loadSavedGame() {
        lifecycleScope.launch {
            val save = withContext(Dispatchers.IO) {
                database.solitaireDao().getSave()
            }

            // On reprend directement la partie sauvegardée, sans rien demander.
            if (save != null && !save.isWon) {
                restoreGame(save)
            } else {
                startNewGame()
            }
        }
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
        history.clear()
        updateUndoButton()
        game.newGame()
        GameStatsRepository(this).recordSolitaireStarted()
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
        timerText.text = getString(R.string.solitaire_time_format, minutes, seconds)
    }

    private fun updateMovesDisplay() {
        movesText.text = getString(R.string.solitaire_moves, moves)
    }

    private fun setStatus(message: String, isSuccess: Boolean = false) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        statusText.setTextColor(if (isSuccess) palette.success else palette.secondary)
    }

    // Game action callbacks
    override fun onStockClicked() {
        if (isGameWon) return
        if (game.stock.isEmpty() && game.waste.isEmpty()) return
        beforeMove()
        game.drawFromStock()
        afterMove(true)
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
            pile.getOrNull(cardIndex)

            // Try to move to tableau
            if (pileType == PileType.TABLEAU) {
                beforeMove()
                val done = game.moveToTableau(pileIndex)
                afterMove(done)
                if (done) {
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
                beforeMove()
                val done = game.moveToFoundation(pileIndex)
                afterMove(done)
                if (done) {
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
                    beforeMove()
                    val done = game.moveToTableau(pileIndex)
                    afterMove(done)
                    if (done) {
                        moves++
                        updateMovesDisplay()
                        gameView.refresh()
                        checkWin()
                        saveGame()
                        return
                    }
                }
                PileType.FOUNDATION -> {
                    beforeMove()
                    val done = game.moveToFoundation(pileIndex)
                    afterMove(done)
                    if (done) {
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
        beforeMove()
        val done = game.autoMoveToFoundation(card, pileType, pileIndex)
        afterMove(done)
        if (done) {
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
        beforeMove()
        if (!game.selectCard(sourcePileType, sourcePileIndex, sourceCardIndex)) {
            afterMove(false)
            return false
        }

        // Try to move to target
        val success = when (targetPileType) {
            PileType.TABLEAU -> game.moveToTableau(targetPileIndex)
            PileType.FOUNDATION -> game.moveToFoundation(targetPileIndex)
            else -> false
        }
        afterMove(success)

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
        if (isGameWon) return
        GameStatsRepository(this).recordSolitaireWon()
        isGameWon = true
        isAutoFinishing = false
        history.clear()
        updateUndoButton()
        stopTimer()
        autoFinishButton.visibility = View.GONE
        setStatus(getString(R.string.solitaire_status_won), isSuccess = true)

        // Delete save on win
        lifecycleScope.launch(Dispatchers.IO) {
            database.solitaireDao().deleteSave()
        }

        NeutrinoRepository(this).addBalance(NeutrinoRewards.SOLITAIRE_WIN)

        // Start victory animation
        gameView.startVictoryAnimation()
    }

    private fun saveGame() {
        if (isGameWon) return

        // Les piles se lisent ici, sur le fil de l'interface : lues depuis le fil d'écriture,
        // elles pouvaient changer en pleine copie si le joueur jouait vite.
        val save = SolitaireSaveEntity.fromGame(game, moves, elapsedTimeMs)
        lifecycleScope.launch(Dispatchers.IO) {
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
