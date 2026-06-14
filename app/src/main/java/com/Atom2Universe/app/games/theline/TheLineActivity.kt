package com.Atom2Universe.app.games.theline

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TheLineActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_NEXT_DELAY_MS = 2000L
    }

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))

    private lateinit var board: TheLineBoardView
    private lateinit var tvMessage: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var btnReset: MaterialButton
    private lateinit var loadingView: View
    private lateinit var modeRow: LinearLayout
    private lateinit var diffRow: LinearLayout

    private val game = TheLineGame()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAutoNext: Runnable? = null

    private val modeButtons = mutableMapOf<TheLineMode, MaterialButton>()
    private val diffButtons = mutableMapOf<TheLineDifficulty, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_the_line)

        board       = findViewById(R.id.the_line_board)
        tvMessage   = findViewById(R.id.the_line_message)
        tvLevel     = findViewById(R.id.the_line_level)
        tvRemaining = findViewById(R.id.the_line_remaining)
        btnReset    = findViewById(R.id.the_line_btn_reset)
        loadingView = findViewById(R.id.the_line_loading)
        modeRow     = findViewById(R.id.the_line_mode_row)
        diffRow     = findViewById(R.id.the_line_diff_row)

        findViewById<ImageButton>(R.id.the_line_back).setOnClickListener { finish() }
        btnReset.setOnClickListener { cancelAutoNext(); startNewPuzzle() }

        buildModeButtons()
        buildDiffButtons()

        board.onPathCompleted = { _ -> checkCompletion() }
        board.onBoardChanged = { updateRemaining() }

        startNewPuzzle()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildModeButtons() {
        for (mode in TheLineMode.entries) {
            val label = if (mode == TheLineMode.SINGLE)
                getString(R.string.the_line_mode_single)
            else getString(R.string.the_line_mode_multi)
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(dp8 / 2, 0, dp8 / 2, 0) }
                setOnClickListener { setMode(mode) }
            }
            modeRow.addView(btn)
            modeButtons[mode] = btn
        }
        updateModeButtons()
    }

    private fun buildDiffButtons() {
        for (diff in TheLineDifficulty.entries) {
            val label = when (diff) {
                TheLineDifficulty.EASY   -> getString(R.string.the_line_diff_easy)
                TheLineDifficulty.MEDIUM -> getString(R.string.the_line_diff_medium)
                TheLineDifficulty.HARD   -> getString(R.string.the_line_diff_hard)
            }
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(dp8 / 2, 0, dp8 / 2, 0) }
                setOnClickListener { setDifficulty(diff) }
            }
            diffRow.addView(btn)
            diffButtons[diff] = btn
        }
        updateDiffButtons()
    }

    private fun setMode(mode: TheLineMode) {
        if (game.mode == mode) return
        cancelAutoNext()
        game.mode = mode
        updateModeButtons()
        updateLevelDisplay()
        startNewPuzzle()
    }

    private fun setDifficulty(diff: TheLineDifficulty) {
        if (game.difficulty == diff) return
        cancelAutoNext()
        game.difficulty = diff
        updateDiffButtons()
        updateLevelDisplay()
        startNewPuzzle()
    }

    private fun updateModeButtons() {
        val active = getColor(R.color.the_line_active)
        val inactive = getColor(R.color.the_line_inactive)
        for ((mode, btn) in modeButtons) {
            btn.setBackgroundColor(if (mode == game.mode) active else inactive)
        }
    }

    private fun updateDiffButtons() {
        val active = getColor(R.color.the_line_active)
        val inactive = getColor(R.color.the_line_inactive)
        for ((diff, btn) in diffButtons) {
            btn.setBackgroundColor(if (diff == game.difficulty) active else inactive)
        }
    }

    private fun updateLevelDisplay() {
        tvLevel.text = getString(R.string.the_line_level, game.currentLevel)
    }

    private fun updateRemaining() {
        tvRemaining.text = getString(R.string.the_line_remaining, maxOf(0, game.cellsRemaining))
    }

    private fun startNewPuzzle() {
        loadingView.visibility = View.VISIBLE
        board.visibility = View.INVISIBLE
        val mode = game.mode
        val diff = game.difficulty
        lifecycleScope.launch {
            val puzzle = withContext(Dispatchers.Default) {
                TheLineGenerator.generate(mode, diff)
            }
            loadingView.visibility = View.GONE
            board.visibility = View.VISIBLE
            if (puzzle == null) {
                tvMessage.text = getString(R.string.the_line_error)
                return@launch
            }
            game.loadPuzzle(puzzle)
            board.loadGame(game)
            updateLevelDisplay()
            updateRemaining()
            tvMessage.text = if (mode == TheLineMode.MULTI)
                getString(R.string.the_line_hint_multi)
            else getString(R.string.the_line_hint_single)
        }
    }

    private fun checkCompletion() {
        if (!game.isComplete()) return
        val level = game.currentLevel
        game.onLevelCompleted()
        updateLevelDisplay()
        tvMessage.text = getString(R.string.the_line_completed, level)

        val reward = NeutrinoRewards.theLine(game.difficulty.ordinal)
        NeutrinoRepository(this).addBalance(reward)

        val r = Runnable {
            pendingAutoNext = null
            startNewPuzzle()
        }
        pendingAutoNext = r
        handler.postDelayed(r, AUTO_NEXT_DELAY_MS)
    }

    private fun cancelAutoNext() {
        pendingAutoNext?.let { handler.removeCallbacks(it) }
        pendingAutoNext = null
    }
}
