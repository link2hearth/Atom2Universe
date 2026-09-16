package com.Atom2Universe.app.games.minesweeper

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

class MinesweeperActivity : ThemedActivity(), MinesweeperGridView.GameEventListener {

    private lateinit var gridView: MinesweeperGridView
    private lateinit var timerText: TextView
    private lateinit var sonarStatus: TextView
    private lateinit var minesText: TextView
    private lateinit var exploredText: TextView
    private lateinit var explorationProgress: ProgressBar
    private var pendingResult: String? = null
    private val resultRunnable = Runnable {
        pendingResult?.let { showScoreboard(it) }
        pendingResult = null
    }
    private lateinit var difficultySpinner: Spinner
    private lateinit var newGameButton: ImageButton
    private lateinit var gridContainer: FrameLayout

    private val prefs by lazy { MinesweeperPrefs(this) }
    private var difficulty = MinesweeperDifficulty.EASY
    private var elapsedSecs = 0
    private var timerRunning = false
    private var spinnerReady = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                elapsedSecs++
                timerText.text = formatTime(elapsedSecs)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_minesweeper)

        timerText = findViewById(R.id.tv_timer)
        sonarStatus = findViewById(R.id.tv_sonar_status)
        minesText = findViewById(R.id.tv_mines)
        exploredText = findViewById(R.id.tv_explored)
        explorationProgress = findViewById(R.id.exploration_progress)
        difficultySpinner = findViewById(R.id.spinner_difficulty)
        newGameButton = findViewById(R.id.btn_new_game)
        gridContainer = findViewById(R.id.grid_container)

        gridView = MinesweeperGridView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            listener = this@MinesweeperActivity
        }
        gridContainer.addView(gridView)

        setupDifficultySpinner()
        newGameButton.setOnClickListener { startNewGame() }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupDifficultySpinner() {
        val labels = arrayOf(
            getString(R.string.minesweeper_diff_easy),
            getString(R.string.minesweeper_diff_normal),
            getString(R.string.minesweeper_diff_medium),
            getString(R.string.minesweeper_diff_hard)
        )
        val adapter = ArrayAdapter(this, R.layout.minesweeper_spinner_item, labels)
        adapter.setDropDownViewResource(R.layout.minesweeper_spinner_item)
        difficultySpinner.adapter = adapter

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val newDiff = MinesweeperDifficulty.entries[pos]
                if (!spinnerReady) {
                    spinnerReady = true
                    difficulty = newDiff
                    tryRestoreOrNewGame()
                    return
                }
                if (newDiff != difficulty) {
                    prefs.saveGame(difficulty, elapsedSecs, gridView.game ?: return)
                    difficulty = newDiff
                    startNewGame()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun tryRestoreOrNewGame() {
        val saved = prefs.loadSavedGame(difficulty)
        if (saved != null) {
            val (elapsed, game) = saved
            elapsedSecs = elapsed
            timerText.text = formatTime(elapsed)
            sonarStatus.setText(R.string.minesweeper_ready)
            newGameButton.setImageResource(R.drawable.ic_refresh)
            gridView.restoreGame(game)
            if (game.gameState == GameState.PLAYING) startTimer()
        } else {
            startNewGame()
        }
    }

    private fun startNewGame() {
        timerHandler.removeCallbacks(resultRunnable)
        pendingResult = null
        stopTimer()
        prefs.clearSavedGame()
        elapsedSecs = 0
        timerText.text = formatTime(0)
        sonarStatus.setText(R.string.minesweeper_ready)
        newGameButton.setImageResource(R.drawable.ic_refresh)
        gridView.newGame(difficulty.cols, difficulty.mines)
    }

    override fun onGameStarted() {
        if (!timerRunning) startTimer()
    }

    override fun onGameWon() {
        stopTimer()
        prefs.clearSavedGame()
        // EASY=5, NORMAL=10, MEDIUM=15, HARD=20
        NeutrinoRepository(this).addBalance(NeutrinoRewards.minesweeper(difficulty.ordinal))
        val isNewBest = prefs.isNewBestTime(difficulty, elapsedSecs)
        if (isNewBest) prefs.saveBestTime(difficulty, elapsedSecs)
        val resultText = if (isNewBest)
            getString(R.string.minesweeper_won_best, formatTime(elapsedSecs))
        else
            getString(R.string.minesweeper_won, formatTime(elapsedSecs))
        sonarStatus.setText(R.string.minesweeper_secured)
        scheduleScoreboard(resultText)
    }

    override fun onGameLost() {
        stopTimer()
        prefs.clearSavedGame()
        sonarStatus.setText(R.string.minesweeper_contact)
        scheduleScoreboard(getString(R.string.minesweeper_lost))
    }

    override fun onFlagsChanged(flags: Int) {
        val game = gridView.game ?: return
        minesText.text = getString(R.string.minesweeper_remaining, game.mineCount - flags)
        val safe = (game.rows * game.cols - game.mineCount).coerceAtLeast(1)
        val percent = game.revealedCount * 100 / safe
        exploredText.text = getString(R.string.minesweeper_explored, percent)
        explorationProgress.progress = percent
        sonarStatus.setText(when (game.gameState) {
            GameState.IDLE -> R.string.minesweeper_ready
            GameState.PLAYING -> R.string.minesweeper_scanning
            GameState.WON -> R.string.minesweeper_secured
            GameState.LOST -> R.string.minesweeper_contact
        })
    }

    private fun scheduleScoreboard(result: String) {
        pendingResult = result
        timerHandler.removeCallbacks(resultRunnable)
        val delay = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 1450L else 0L
        timerHandler.postDelayed(resultRunnable, delay)
    }

    private fun showScoreboard(resultText: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_minesweeper_scores, null)
        view.findViewById<TextView>(R.id.tv_result).text = resultText

        val diffRows = mapOf(
            MinesweeperDifficulty.EASY   to Pair(R.id.lbl_easy,   R.id.time_easy),
            MinesweeperDifficulty.NORMAL to Pair(R.id.lbl_normal, R.id.time_normal),
            MinesweeperDifficulty.MEDIUM to Pair(R.id.lbl_medium, R.id.time_medium),
            MinesweeperDifficulty.HARD   to Pair(R.id.lbl_hard,   R.id.time_hard)
        )
        val diffLabels = mapOf(
            MinesweeperDifficulty.EASY   to getString(R.string.minesweeper_diff_easy),
            MinesweeperDifficulty.NORMAL to getString(R.string.minesweeper_diff_normal),
            MinesweeperDifficulty.MEDIUM to getString(R.string.minesweeper_diff_medium),
            MinesweeperDifficulty.HARD   to getString(R.string.minesweeper_diff_hard)
        )

        for ((diff, ids) in diffRows) {
            val lbl = view.findViewById<TextView>(ids.first)
            val time = view.findViewById<TextView>(ids.second)
            lbl.text = diffLabels[diff]
            val best = prefs.getBestTime(diff)
            time.text = if (best >= 0) formatTime(best) else getString(R.string.minesweeper_score_none)
            // Highlight current difficulty
            if (diff == difficulty) {
                lbl.setTextColor(0xFF65E3CD.toInt())
                time.setTextColor(0xFF65E3CD.toInt())
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.minesweeper_new_game_btn) { _, _ -> startNewGame() }
            .setNegativeButton(android.R.string.ok, null)
            .show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.minesweeper_panel)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF65E3CD.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFB4D7DE.toInt())
    }

    private fun startTimer() {
        if (timerRunning) return
        timerRunning = true
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun stopTimer() {
        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun formatTime(secs: Int) = getString(R.string.minesweeper_time_format, secs / 60, secs % 60)

    override fun onPause() {
        super.onPause()
        stopTimer()
        timerHandler.removeCallbacks(resultRunnable)
        val g = gridView.game ?: return
        prefs.saveGame(difficulty, elapsedSecs, g)
    }

    override fun onResume() {
        super.onResume()
        if (gridView.game?.gameState == GameState.PLAYING) startTimer()
        if (pendingResult != null) timerHandler.post(resultRunnable)
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(resultRunnable)
        super.onDestroy()
        stopTimer()
    }
}
