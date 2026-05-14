package com.Atom2Universe.app.games.starbridges

import android.content.Context
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
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class StarBridgesActivity : ThemedActivity(), StarBridgesBoardView.Listener {

    companion object {
        private const val PREFS = "starbridges_save"
        private const val KEY_SAVE = "save_json"
    }

    private lateinit var boardView: StarBridgesBoardView
    private lateinit var tvBridges: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvHint: TextView
    private lateinit var winOverlay: FrameLayout
    private lateinit var tvWinMsg: TextView

    private val game = StarBridgesGame()
    private val handler = Handler(Looper.getMainLooper())
    private var timerStartMs = 0L
    private var timerRunning = false

    private val sizeChips = mutableMapOf<Int, MaterialButton>()

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
        setContentView(R.layout.activity_star_bridges)
        enableImmersiveMode()

        boardView  = findViewById(R.id.sb_board)
        tvBridges  = findViewById(R.id.sb_stat_bridges)
        tvTime     = findViewById(R.id.sb_stat_time)
        tvHint     = findViewById(R.id.sb_hint)
        winOverlay = findViewById(R.id.sb_win_overlay)
        tvWinMsg   = findViewById(R.id.sb_win_message)

        boardView.listener = this

        findViewById<ImageButton>(R.id.sb_back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.sb_restart_button).setOnClickListener { restartPuzzle() }
        findViewById<MaterialButton>(R.id.sb_btn_again).setOnClickListener {
            startNewGame(game.size)
        }
        findViewById<MaterialButton>(R.id.sb_btn_next).setOnClickListener {
            val idx = StarBridgesGame.ALLOWED_SIZES.indexOf(game.size)
            val next = StarBridgesGame.ALLOWED_SIZES.getOrElse(idx + 1) { game.size }
            startNewGame(next)
        }

        buildSizeChips()

        val restored = loadGame()
        if (!restored) game.newGame(StarBridgesGame.DEFAULT_SIZE)

        boardView.game = game
        boardView.refresh()
        updateStats()
        updateHint()
        updateSizeChips()

        if (game.solved) showWinOverlay()
        else startTimer()
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ── Size chips ────────────────────────────────────────────────────────────────
    private fun buildSizeChips() {
        val container = findViewById<LinearLayout>(R.id.sb_size_row)
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        for (s in StarBridgesGame.ALLOWED_SIZES) {
            val chip = MaterialButton(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = "${s}×${s}"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp6 / 2, 0, dp6 / 2, 0) }
                setOnClickListener { startNewGame(s) }
            }
            container.addView(chip)
            sizeChips[s] = chip
        }
    }

    private fun updateSizeChips() {
        val active   = android.content.res.ColorStateList.valueOf(Color.parseColor("#4F46E5"))
        val inactive = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E1B4B"))
        for ((s, chip) in sizeChips) chip.backgroundTintList = if (s == game.size) active else inactive
    }

    // ── Game control ──────────────────────────────────────────────────────────────
    private fun restartPuzzle() {
        val wasRunning = timerRunning
        val elapsedBefore = game.elapsedSeconds
        stopTimer()
        winOverlay.visibility = View.GONE
        game.resetBridges()
        // Restore elapsed time — reset only removes bridges, not progress time
        game.elapsedSeconds = elapsedBefore
        boardView.refresh()
        updateStats()
        updateHint()
        saveGame()
        if (wasRunning || !game.solved) startTimer()
    }

    private fun startNewGame(s: Int) {
        stopTimer()
        winOverlay.visibility = View.GONE
        game.newGame(s)
        boardView.game = game
        boardView.refresh()
        updateStats()
        updateHint()
        updateSizeChips()
        saveGame()
        startTimer()
    }

    // ── Board listener ────────────────────────────────────────────────────────────
    override fun onBoardChanged() {
        updateStats()
        updateHint()
        saveGame()
    }

    override fun onSolved() {
        stopTimer()
        updateStats()
        awardReward()
        saveGame()
        showWinOverlay()
    }

    private fun showWinOverlay() {
        tvWinMsg.text = getString(
            R.string.starbridges_win_message,
            formatTime(game.elapsedSeconds),
            game.moves,
            game.totalBridgesPlaced()
        )
        winOverlay.visibility = View.VISIBLE
    }

    // ── Reward ────────────────────────────────────────────────────────────────────
    private fun awardReward() {
        if (game.rewardClaimed || !game.seedWasRandom) { game.rewardClaimed = true; return }
        game.rewardClaimed = true
        val tickets = when (game.size) { 6 -> 5; 7 -> 10; else -> 15 }
        NeutrinoRepository(this).addPending(tickets)
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
        if (timerStartMs > 0) game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
    }

    // ── UI ────────────────────────────────────────────────────────────────────────
    private fun updateStats() {
        val placed = game.totalBridgesPlaced()
        val required = game.totalBridgesRequired()
        tvBridges.text = getString(R.string.starbridges_bridges, placed, required)
        tvTime.text = formatTime(game.elapsedSeconds)
    }

    private fun updateHint() {
        tvHint.text = when {
            game.solved -> getString(R.string.starbridges_solved)
            game.selectedNodeId != null -> getString(R.string.starbridges_hint_target)
            else -> getString(R.string.starbridges_hint_source)
        }
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    // ── Persistence ───────────────────────────────────────────────────────────────
    private fun saveGame() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAVE, game.serialize()).apply()
    }

    private fun loadGame(): Boolean {
        val json = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SAVE, null) ?: return false
        return game.deserialize(json)
    }
}
