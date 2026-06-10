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
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class MinesweeperActivity : ThemedActivity(), MinesweeperGridView.GameEventListener {

    private lateinit var gridView: MinesweeperGridView
    private lateinit var timerText: TextView
    private lateinit var statusOverlay: FrameLayout
    private lateinit var statusMessage: TextView
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
        statusOverlay = findViewById(R.id.overlay_status)
        statusMessage = findViewById(R.id.tv_status_message)
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
        statusOverlay.setOnClickListener { statusOverlay.visibility = View.GONE }
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupDifficultySpinner() {
        val labels = arrayOf(
            getString(R.string.minesweeper_diff_easy),
            getString(R.string.minesweeper_diff_normal),
            getString(R.string.minesweeper_diff_medium),
            getString(R.string.minesweeper_diff_hard)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            statusOverlay.visibility = View.GONE
            newGameButton.setImageResource(android.R.drawable.ic_menu_recent_history)
            gridView.restoreGame(game)
            startTimer()
        } else {
            startNewGame()
        }
    }

    private fun startNewGame() {
        stopTimer()
        prefs.clearSavedGame()
        elapsedSecs = 0
        timerText.text = formatTime(0)
        statusOverlay.visibility = View.GONE
        newGameButton.setImageResource(android.R.drawable.ic_menu_recent_history)
        gridView.newGame(difficulty.cols, difficulty.mines)
    }

    override fun onGameStarted() {
        if (!timerRunning) startTimer()
    }

    override fun onGameWon() {
        stopTimer()
        prefs.clearSavedGame()
        val isNewBest = prefs.isNewBestTime(difficulty, elapsedSecs)
        if (isNewBest) prefs.saveBestTime(difficulty, elapsedSecs)
        val resultText = if (isNewBest)
            getString(R.string.minesweeper_won_best, formatTime(elapsedSecs))
        else
            getString(R.string.minesweeper_won, formatTime(elapsedSecs))
        newGameButton.setImageResource(android.R.drawable.ic_menu_info_details)
        showScoreboard(resultText)
    }

    override fun onGameLost() {
        stopTimer()
        prefs.clearSavedGame()
        newGameButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        showScoreboard(getString(R.string.minesweeper_lost))
    }

    override fun onFlagsChanged(flags: Int) {}

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
            time.text = if (best > 0) formatTime(best) else getString(R.string.minesweeper_score_none)
            // Highlight current difficulty
            if (diff == difficulty) {
                lbl.setTextColor(0xFF4CAF50.toInt())
                time.setTextColor(0xFF4CAF50.toInt())
            }
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.minesweeper_new_game_btn) { _, _ -> startNewGame() }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun startTimer() {
        timerRunning = true
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun stopTimer() {
        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun formatTime(secs: Int) = "%02d:%02d".format(secs / 60, secs % 60)

    override fun onPause() {
        super.onPause()
        val g = gridView.game ?: return
        prefs.saveGame(difficulty, elapsedSecs, g)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}
