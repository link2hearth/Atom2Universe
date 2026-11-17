package com.Atom2Universe.app.games.sokoban

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.Landscape
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private lateinit var diffSpinner: Spinner
    private lateinit var btnUndo: View
    private lateinit var btnReset: View

    private var generationJob: Job? = null
    private var rewarded = false
    private var generating = false
    private var loadedDifficulty = SokobanDifficulty.EASY

    private val game = SokobanGame()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAutoNext: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sokoban)
        Landscape.bindSidePanel(this)
        enableImmersiveMode()

        board       = findViewById(R.id.sokoban_board)
        tvMessage   = findViewById(R.id.sokoban_message)
        tvBest      = findViewById(R.id.sokoban_best)
        tvMoves     = findViewById(R.id.sokoban_moves)
        tvPushes    = findViewById(R.id.sokoban_pushes)
        loadingView = findViewById(R.id.sokoban_loading)
        diffSpinner = findViewById(R.id.sokoban_difficulty_spinner)
        btnUndo     = findViewById(R.id.sokoban_btn_undo)
        btnReset    = findViewById(R.id.sokoban_btn_reset)

        findViewById<ImageButton>(R.id.sokoban_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.sokoban_info_button).setOnClickListener {
            showGameRules(R.string.sokoban_title, R.string.sokoban_rules)
        }
        findViewById<View>(R.id.sokoban_btn_new).setOnClickListener {
            cancelAutoNext(); startNewPuzzle()
        }
        btnReset.setOnClickListener {
            if (generating) return@setOnClickListener
            cancelAutoNext(); game.reset(); board.loadGame(game); onBoardChanged()
            showMessage(null)
        }
        btnUndo.setOnClickListener {
            if (!generating && game.undo()) {
                cancelAutoNext()
                board.loadGame(game)
                onBoardChanged()
                showMessage(null)
            }
        }

        findViewById<MaterialButton>(R.id.sokoban_up).setOnClickListener { board.move(SokobanDir.UP) }
        findViewById<MaterialButton>(R.id.sokoban_down).setOnClickListener { board.move(SokobanDir.DOWN) }
        findViewById<MaterialButton>(R.id.sokoban_left).setOnClickListener { board.move(SokobanDir.LEFT) }
        findViewById<MaterialButton>(R.id.sokoban_right).setOnClickListener { board.move(SokobanDir.RIGHT) }

        board.onChanged = { onBoardChanged() }
        board.onSolved = { onSolved() }

        buildDifficultySpinner()
        startNewPuzzle()
    }

    /** Couché : le dépôt au milieu, réglages et flèches de part et d'autre ; le niveau ne change pas. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Landscape.swapLayout(this, R.layout.activity_sokoban)
    }

    /** Le message de fin de niveau, par-dessus le dépôt ; rien pendant la partie. */
    private fun showMessage(text: String?) {
        tvMessage.text = text
        tvMessage.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Une icône inactive s'estompe (une icône seule ne change pas d'allure quand on la désactive). */
    private fun setActive(view: View, active: Boolean) {
        view.isEnabled = active
        view.alpha = if (active) 1f else 0.35f
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** La difficulté : un seul bouton qui montre la valeur choisie et déroule les autres sous le doigt. */
    private fun buildDifficultySpinner() {
        val labels = SokobanDifficulty.entries.map { diff ->
            when (diff) {
                SokobanDifficulty.EASY   -> getString(R.string.sokoban_diff_easy)
                SokobanDifficulty.MEDIUM -> getString(R.string.sokoban_diff_medium)
                SokobanDifficulty.HARD   -> getString(R.string.sokoban_diff_hard)
                SokobanDifficulty.EXPERT -> getString(R.string.sokoban_diff_expert)
            }
        }
        val adapter = ArrayAdapter(this, R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        diffSpinner.adapter = adapter
        diffSpinner.setSelection(game.difficulty.ordinal, false)
        diffSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                setDifficulty(SokobanDifficulty.entries[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setDifficulty(diff: SokobanDifficulty) {
        if (game.difficulty == diff) return
        cancelAutoNext()
        game.difficulty = diff
        updateDiffButtons()
        startNewPuzzle()
    }

    private fun updateDiffButtons() {
        if (diffSpinner.selectedItemPosition != game.difficulty.ordinal) diffSpinner.setSelection(game.difficulty.ordinal)
    }

    private fun startNewPuzzle() {
        cancelAutoNext()
        generationJob?.cancel()
        generating = true
        board.isEnabled = false
        setActive(btnUndo, false)
        setActive(btnReset, false)
        showMessage(null)
        loadingView.visibility = View.VISIBLE
        board.visibility = View.INVISIBLE
        val diff = game.difficulty
        generationJob = lifecycleScope.launch {
            val puzzle = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                SokobanGenerator.generate(diff) { context.ensureActive() }
            }
            generating = false
            board.isEnabled = true
            setActive(btnReset, true)
            loadingView.visibility = View.GONE
            board.visibility = View.VISIBLE
            if (puzzle == null) {
                if (game.puzzle != null) {
                    game.difficulty = loadedDifficulty
                    updateDiffButtons()
                    updateBest()
                }
                onBoardChanged()
                showMessage(getString(R.string.sokoban_error))
                return@launch
            }
            rewarded = false
            loadedDifficulty = diff
            game.load(puzzle)
            board.loadGame(game)
            onBoardChanged()
            updateBest()
        }
    }

    private fun onBoardChanged() {
        tvMoves.text = getString(R.string.sokoban_moves, game.moves)
        tvPushes.text = getString(R.string.sokoban_pushes, game.pushes)
        setActive(btnUndo, game.canUndo && !generating)
        val puzzle = game.puzzle
        findViewById<TextView>(R.id.sokoban_delivery).text = if (puzzle == null) "" else
            getString(R.string.sokoban_delivery, game.boxes.count { it in puzzle.goals }, puzzle.goals.size)
        findViewById<TextView>(R.id.sokoban_target).text = if (puzzle == null) "" else
            getString(R.string.sokoban_target, puzzle.optimalPushes)
    }

    private fun onSolved() {
        val diff = game.difficulty
        val best = saveBest(diff, game.moves)
        updateBest()
        showMessage(getString(R.string.sokoban_solved, game.moves, best))

        val reward = NeutrinoRewards.sokoban(diff.ordinal)
        if (!rewarded) {
            NeutrinoRepository(this).addBalance(reward)
            rewarded = true
        }

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
