package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class ColorStackActivity : AppCompatActivity(), ColorStackView.OnMoveListener {

    companion object {
        private const val PREFS_NAME = "color_stack_save"
        private const val KEY_SAVE = "save_json"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var colorStackView: ColorStackView
    private lateinit var movesText: TextView
    private lateinit var statusText: TextView
    private lateinit var difficultySpinner: Spinner

    private val game = ColorStackGame()
    private lateinit var prefs: SharedPreferences
    private var ignoreSpinnerChange = false

    // Timer Hard : ms depuis le début, 0 si annulé ou hors Hard
    private var hardGameStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_color_stack)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        colorStackView = findViewById(R.id.color_stack_game_view)
        movesText = findViewById(R.id.color_stack_moves)
        statusText = findViewById(R.id.color_stack_status)
        difficultySpinner = findViewById(R.id.color_stack_difficulty_spinner)

        findViewById<ImageButton>(R.id.color_stack_back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.color_stack_btn_new).setOnClickListener { startNewGame(game.difficulty) }
        findViewById<TextView>(R.id.color_stack_btn_restart).setOnClickListener { restartGame() }
        findViewById<TextView>(R.id.color_stack_btn_undo).setOnClickListener { undoMove() }

        colorStackView.game = game
        colorStackView.listener = this

        val restored = loadGame()
        if (!restored) {
            game.newGame(ColorStackGame.Difficulty.EASY)
        }

        ignoreSpinnerChange = true
        setupSpinner()
        syncSpinnerToDifficulty()
        ignoreSpinnerChange = false

        colorStackView.refresh()
        updateUI()
        if (!restored) statusText.text = getString(R.string.color_stack_status_new)
    }

    override fun onPause() {
        super.onPause()
        saveGame()
        hardGameStartMs = 0L
    }

    private fun setupSpinner() {
        val labels = listOf(
            getString(R.string.color_stack_difficulty_easy),
            getString(R.string.color_stack_difficulty_medium),
            getString(R.string.color_stack_difficulty_hard)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val diff = when (position) {
                    0 -> ColorStackGame.Difficulty.EASY
                    1 -> ColorStackGame.Difficulty.MEDIUM
                    else -> ColorStackGame.Difficulty.HARD
                }
                if (diff != game.difficulty) startNewGame(diff)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun syncSpinnerToDifficulty() {
        val pos = when (game.difficulty) {
            ColorStackGame.Difficulty.EASY -> 0
            ColorStackGame.Difficulty.MEDIUM -> 1
            ColorStackGame.Difficulty.HARD -> 2
        }
        difficultySpinner.setSelection(pos, false)
    }

    private fun startNewGame(diff: ColorStackGame.Difficulty) {
        game.newGame(diff)
        colorStackView.game = game
        colorStackView.refresh()
        ignoreSpinnerChange = true
        syncSpinnerToDifficulty()
        ignoreSpinnerChange = false
        updateUI()
        statusText.text = getString(R.string.color_stack_status_new)
        saveGame()
        hardGameStartMs = if (diff == ColorStackGame.Difficulty.HARD) System.currentTimeMillis() else 0L
        if (diff == ColorStackGame.Difficulty.HARD) GameStatsRepository(this).recordColorStackHardStarted()
    }

    private fun restartGame() {
        game.restart()
        colorStackView.refresh()
        updateUI()
        statusText.text = getString(R.string.color_stack_status_restarted)
        saveGame()
    }

    private fun undoMove() {
        if (game.undo()) {
            colorStackView.refresh()
            updateUI()
        }
    }

    override fun onMove(from: Int, to: Int) {
        if (game.move(from, to)) {
            colorStackView.refresh()
            updateUI()
            if (game.solved) {
                statusText.text = getString(R.string.color_stack_status_won)
                when (game.difficulty) {
                    ColorStackGame.Difficulty.HARD -> {
                        val statsRepo = GameStatsRepository(this)
                        statsRepo.recordColorStackHardWon()
                        if (hardGameStartMs > 0L) {
                            statsRepo.recordColorStackHardBestTime(System.currentTimeMillis() - hardGameStartMs)
                        }
                        hardGameStartMs = 0L
                        NeutrinoRepository(this).addBalance(3)
                    }
                    ColorStackGame.Difficulty.MEDIUM -> NeutrinoRepository(this).addBalance(1)
                    else -> Unit
                }
            }
            saveGame()
        }
    }

    override fun onColumnSelected(col: Int?) {}

    private fun updateUI() {
        movesText.text = getString(R.string.color_stack_moves, game.moves)
        if (game.solved) statusText.text = getString(R.string.color_stack_status_won)
    }

    private fun saveGame() {
        prefs.edit().putString(KEY_SAVE, game.serialize()).apply()
    }

    private fun loadGame(): Boolean {
        val json = prefs.getString(KEY_SAVE, null) ?: return false
        return game.deserialize(json)
    }
}
