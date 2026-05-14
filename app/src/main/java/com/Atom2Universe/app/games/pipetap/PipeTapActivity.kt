package com.Atom2Universe.app.games.pipetap

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class PipeTapActivity : AppCompatActivity(), PipeTapView.OnTileRotatedListener {

    companion object {
        private const val PREFS_NAME = "pipetap_save"
        private const val KEY_SAVE = "save_json"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var gameView: PipeTapView
    private lateinit var movesText: TextView
    private lateinit var timeText: TextView
    private lateinit var winOverlay: FrameLayout
    private lateinit var winMessage: TextView
    private lateinit var prefs: SharedPreferences

    private val game = PipeTapGame()
    private val handler = Handler(Looper.getMainLooper())
    private var timerStartMs = 0L
    private var timerRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
            updateStats()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_pipetap)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        gameView  = findViewById(R.id.pipetap_game_view)
        movesText = findViewById(R.id.pipetap_moves)
        timeText  = findViewById(R.id.pipetap_time)
        winOverlay = findViewById(R.id.pipetap_win_overlay)
        winMessage = findViewById(R.id.pipetap_win_message)

        gameView.listener = this

        findViewById<ImageButton>(R.id.pipetap_back_button).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.pipetap_btn_again).setOnClickListener {
            startNewGame(game.difficulty)
        }
        findViewById<MaterialButton>(R.id.pipetap_btn_next).setOnClickListener {
            val entries = PipeTapDifficulty.entries
            val nextDiff = entries.getOrNull(game.difficulty.ordinal + 1) ?: game.difficulty
            startNewGame(nextDiff)
        }

        buildDiffChips()

        val restored = loadGame()
        if (!restored) {
            game.newGame(PipeTapDifficulty.MEDIUM)
        }
        gameView.game = game
        gameView.refresh()
        updateStats()
        updateDiffChips()

        if (game.solved) {
            showWinOverlay()
        } else {
            startTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.solved) startTimer()
    }

    // ── Difficulty chips ──────────────────────────────────────────────────────────
    private val diffChipMap = mutableMapOf<PipeTapDifficulty, MaterialButton>()

    private fun buildDiffChips() {
        val container = findViewById<LinearLayout>(R.id.pipetap_diff_row)
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        for (diff in PipeTapDifficulty.entries) {
            val chip = MaterialButton(
                this, null, android.R.attr.borderlessButtonStyle
            ).apply {
                text = diff.label
                textSize = 12f
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp6 / 2, 0, dp6 / 2, 0) }
                layoutParams = params
                setOnClickListener { startNewGame(diff) }
            }
            container.addView(chip)
            diffChipMap[diff] = chip
        }
    }

    private fun updateDiffChips() {
        val activeColor   = ColorStateList.valueOf(Color.parseColor("#06B6D4"))
        val inactiveColor = ColorStateList.valueOf(Color.parseColor("#1F2937"))
        for ((diff, chip) in diffChipMap) {
            chip.backgroundTintList = if (diff == game.difficulty) activeColor else inactiveColor
        }
    }

    // ── Game control ──────────────────────────────────────────────────────────────
    private fun startNewGame(diff: PipeTapDifficulty) {
        stopTimer()
        winOverlay.visibility = View.GONE
        game.newGame(diff)
        gameView.game = game
        gameView.refresh()
        updateStats()
        updateDiffChips()
        saveGame()
        startTimer()
    }

    override fun onTileRotated(solved: Boolean) {
        updateStats()
        if (solved) {
            stopTimer()
            saveGame()
            awardReward()
            showWinOverlay()
        }
    }

    private fun showWinOverlay() {
        winMessage.text = getString(
            R.string.pipetap_win_message,
            game.moves,
            formatTime(game.elapsedSeconds)
        )
        winOverlay.visibility = View.VISIBLE
    }

    // ── Reward ────────────────────────────────────────────────────────────────────
    private fun awardReward() {
        if (game.rewardClaimed) return
        game.rewardClaimed = true
        if (!game.difficulty.isHard) return
        val repo = NeutrinoRepository(this)
        if (repo.recordPipeTapHardWin()) {
            repo.addPending(1)
            Toast.makeText(this, R.string.clicker_neutrino_awarded_double, Toast.LENGTH_SHORT).show()
        }
        GameStatsRepository(this).recordPipeTapHardWon()
    }

    // ── Timer ─────────────────────────────────────────────────────────────────────
    private fun startTimer() {
        if (timerRunning) return
        timerStartMs = System.currentTimeMillis() - game.elapsedSeconds * 1000
        timerRunning = true
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
        if (timerStartMs > 0) {
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────────
    private fun updateStats() {
        movesText.text = getString(R.string.pipetap_moves, game.moves)
        timeText.text  = formatTime(game.elapsedSeconds)
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    // ── Persistence ───────────────────────────────────────────────────────────────
    private fun saveGame() {
        prefs.edit().putString(KEY_SAVE, game.serialize()).apply()
    }

    private fun loadGame(): Boolean {
        val json = prefs.getString(KEY_SAVE, null) ?: return false
        return game.deserialize(json)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
