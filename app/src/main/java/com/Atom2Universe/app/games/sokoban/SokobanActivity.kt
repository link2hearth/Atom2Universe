package com.Atom2Universe.app.games.sokoban

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SokobanActivity : ThemedActivity() {

    companion object {
        private const val PREFS = "sokoban_prefs"
        private const val KEY_BEST = "best_"
        private const val AUTO_NEXT_DELAY_MS = 2200L
    }

    private lateinit var board: SokobanBoardView
    private lateinit var tvMessage: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvMoves: TextView
    private lateinit var tvPushes: TextView
    private lateinit var loadingView: View
    private lateinit var diffRow: LinearLayout
    private lateinit var btnUndo: MaterialButton

    private val game = SokobanGame()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAutoNext: Runnable? = null

    private val diffButtons = mutableMapOf<SokobanDifficulty, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sokoban)
        enableImmersiveMode()

        board       = findViewById(R.id.sokoban_board)
        tvMessage   = findViewById(R.id.sokoban_message)
        tvBest      = findViewById(R.id.sokoban_best)
        tvMoves     = findViewById(R.id.sokoban_moves)
        tvPushes    = findViewById(R.id.sokoban_pushes)
        loadingView = findViewById(R.id.sokoban_loading)
        diffRow     = findViewById(R.id.sokoban_diff_row)
        btnUndo     = findViewById(R.id.sokoban_btn_undo)

        findViewById<ImageButton>(R.id.sokoban_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.sokoban_btn_new).setOnClickListener {
            cancelAutoNext(); startNewPuzzle()
        }
        findViewById<MaterialButton>(R.id.sokoban_btn_reset).setOnClickListener {
            cancelAutoNext(); game.reset(); board.loadGame(game); onBoardChanged()
            tvMessage.text = getString(R.string.sokoban_hint)
        }
        btnUndo.setOnClickListener {
            if (game.undo()) {
                cancelAutoNext()
                board.loadGame(game)
                onBoardChanged()
                tvMessage.text = getString(R.string.sokoban_hint)
            }
        }

        findViewById<MaterialButton>(R.id.sokoban_up).setOnClickListener { board.move(SokobanDir.UP) }
        findViewById<MaterialButton>(R.id.sokoban_down).setOnClickListener { board.move(SokobanDir.DOWN) }
        findViewById<MaterialButton>(R.id.sokoban_left).setOnClickListener { board.move(SokobanDir.LEFT) }
        findViewById<MaterialButton>(R.id.sokoban_right).setOnClickListener { board.move(SokobanDir.RIGHT) }

        board.onChanged = { onBoardChanged() }
        board.onSolved = { onSolved() }

        buildDiffButtons()
        startNewPuzzle()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildDiffButtons() {
        for (diff in SokobanDifficulty.entries) {
            val label = when (diff) {
                SokobanDifficulty.EASY   -> getString(R.string.sokoban_diff_easy)
                SokobanDifficulty.MEDIUM -> getString(R.string.sokoban_diff_medium)
                SokobanDifficulty.HARD   -> getString(R.string.sokoban_diff_hard)
                SokobanDifficulty.EXPERT -> getString(R.string.sokoban_diff_expert)
            }
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                val dp = (4 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(dp, 0, dp, 0) }
                setOnClickListener { setDifficulty(diff) }
            }
            diffRow.addView(btn)
            diffButtons[diff] = btn
        }
        updateDiffButtons()
    }

    private fun setDifficulty(diff: SokobanDifficulty) {
        if (game.difficulty == diff) return
        cancelAutoNext()
        game.difficulty = diff
        updateDiffButtons()
        startNewPuzzle()
    }

    private fun updateDiffButtons() {
        val active = getColor(R.color.sokoban_active)
        val inactive = getColor(R.color.sokoban_inactive)
        for ((diff, btn) in diffButtons) {
            btn.setBackgroundColor(if (diff == game.difficulty) active else inactive)
        }
    }

    private fun startNewPuzzle() {
        loadingView.visibility = View.VISIBLE
        board.visibility = View.INVISIBLE
        val diff = game.difficulty
        lifecycleScope.launch {
            val puzzle = withContext(Dispatchers.Default) {
                SokobanGenerator.generate(diff)
            }
            loadingView.visibility = View.GONE
            board.visibility = View.VISIBLE
            if (puzzle == null) {
                tvMessage.text = getString(R.string.sokoban_error)
                return@launch
            }
            game.load(puzzle)
            board.loadGame(game)
            tvMessage.text = getString(R.string.sokoban_hint)
            onBoardChanged()
            updateBest()
        }
    }

    private fun onBoardChanged() {
        tvMoves.text = getString(R.string.sokoban_moves, game.moves)
        tvPushes.text = getString(R.string.sokoban_pushes, game.pushes)
        btnUndo.isEnabled = game.canUndo
    }

    private fun onSolved() {
        val diff = game.difficulty
        val best = saveBest(diff, game.moves)
        updateBest()
        tvMessage.text = getString(R.string.sokoban_solved, game.moves, best)

        val reward = NeutrinoRewards.sokoban(diff.ordinal)
        NeutrinoRepository(this).addBalance(reward)

        val r = Runnable { pendingAutoNext = null; startNewPuzzle() }
        pendingAutoNext = r
        handler.postDelayed(r, AUTO_NEXT_DELAY_MS)
    }

    private fun bestKey(diff: SokobanDifficulty) = KEY_BEST + diff.name

    private fun saveBest(diff: SokobanDifficulty, moves: Int): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(bestKey(diff), 0)
        val best = if (current == 0 || moves < current) moves else current
        if (best != current) prefs.edit { putInt(bestKey(diff), best) }
        return best
    }

    private fun updateBest() {
        val current = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(bestKey(game.difficulty), 0)
        tvBest.text = if (current > 0) getString(R.string.sokoban_best, current) else ""
    }

    private fun cancelAutoNext() {
        pendingAutoNext?.let { handler.removeCallbacks(it) }
        pendingAutoNext = null
    }
}
