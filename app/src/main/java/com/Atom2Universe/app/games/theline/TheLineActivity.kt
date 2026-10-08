package com.Atom2Universe.app.games.theline

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

/**
 * Circuit (ancien « The Line ») : tracer une piste de cuivre qui passe par les bornes
 * 1, 2, 3… dans l'ordre et par toutes les cases de la carte (Piste), ou relier des
 * paires de connecteurs par des fils qui remplissent la carte (Câblage).
 *
 * La grille en cours et les niveaux atteints sont sauvegardés : on reprend là où on
 * s'est arrêté.
 */
class TheLineActivity : ThemedActivity() {

    companion object {
        private const val PREFS = "the_line_save"
        private const val KEY_SAVE = "save"
        private const val KEY_MODE = "mode"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_LEVEL = "level_"
    }

    private lateinit var board: TheLineBoardView
    private lateinit var tvLevel: TextView
    private lateinit var tvMessage: TextView
    private lateinit var loadingView: View
    private lateinit var winOverlay: View

    private val game = TheLineGame()
    private val modeChips = mutableMapOf<TheLineMode, MaterialButton>()
    private val diffChips = mutableMapOf<TheLineDifficulty, MaterialButton>()
    /** Niveau que la dernière victoire a bouclé, pour le message de fin. */
    private var solvedLevel = 0
    private var generating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_the_line)
        enableImmersiveMode()

        board       = findViewById(R.id.the_line_board)
        tvLevel     = findViewById(R.id.the_line_level)
        tvMessage   = findViewById(R.id.the_line_message)
        loadingView = findViewById(R.id.the_line_loading)
        winOverlay  = findViewById(R.id.the_line_win_overlay)

        board.drawBackdrop = true
        board.onBoardChanged = { saveGame() }
        board.onSolved = { onSolved() }
        board.onPowered = { showWin() }

        findViewById<ImageButton>(R.id.the_line_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.the_line_btn_reset).setOnClickListener { clearTraces() }
        findViewById<MaterialButton>(R.id.the_line_btn_next).setOnClickListener { startNewPuzzle() }

        loadLevels()
        buildChips()

        if (loadGame()) {
            board.loadGame(game)
            updateLevel()
            updateChips()
            if (game.isComplete()) {
                solvedLevel = game.currentLevel - 1
                board.showSolved()
                showWin()
            }
        } else {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            game.mode = TheLineMode.entries.find { it.name == prefs.getString(KEY_MODE, null) } ?: TheLineMode.SINGLE
            game.difficulty = TheLineDifficulty.entries.find { it.name == prefs.getString(KEY_DIFFICULTY, null) }
                ?: TheLineDifficulty.EASY
            updateChips()
            startNewPuzzle()
        }
    }

    override fun onPause() {
        super.onPause()
        saveGame()
    }

    // ── Choix du mode et de la difficulté ─────────────────────────────────────────
    private fun buildChips() {
        val row = findViewById<LinearLayout>(R.id.the_line_chip_row)
        val dp = resources.displayMetrics.density
        fun chip(label: String, onClick: () -> Unit) =
            MaterialButton(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTextColor(0xFFE6F2EA.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins((3 * dp).toInt(), 0, (3 * dp).toInt(), 0) }
                setOnClickListener { onClick() }
                row.addView(this)
            }
        modeChips[TheLineMode.SINGLE] = chip(getString(R.string.the_line_mode_single)) { setMode(TheLineMode.SINGLE) }
        modeChips[TheLineMode.MULTI] = chip(getString(R.string.the_line_mode_multi)) { setMode(TheLineMode.MULTI) }
        row.addView(View(this).apply {
            setBackgroundColor(0x40B9D3C4)
            layoutParams = LinearLayout.LayoutParams((1 * dp).toInt().coerceAtLeast(1), (22 * dp).toInt())
                .also { it.setMargins((6 * dp).toInt(), 0, (6 * dp).toInt(), 0) }
        })
        diffChips[TheLineDifficulty.EASY] = chip(getString(R.string.the_line_diff_easy)) { setDifficulty(TheLineDifficulty.EASY) }
        diffChips[TheLineDifficulty.MEDIUM] = chip(getString(R.string.the_line_diff_medium)) { setDifficulty(TheLineDifficulty.MEDIUM) }
        diffChips[TheLineDifficulty.HARD] = chip(getString(R.string.the_line_diff_hard)) { setDifficulty(TheLineDifficulty.HARD) }
    }

    private fun updateChips() {
        val active = ColorStateList.valueOf(0xFF8A5326.toInt())
        val inactive = ColorStateList.valueOf(0xFF0C4230.toInt())
        for ((m, chip) in modeChips) chip.backgroundTintList = if (m == game.mode) active else inactive
        for ((d, chip) in diffChips) chip.backgroundTintList = if (d == game.difficulty) active else inactive
    }

    private fun setMode(mode: TheLineMode) {
        if (game.mode == mode || generating) return
        game.mode = mode
        updateChips()
        startNewPuzzle()
    }

    private fun setDifficulty(diff: TheLineDifficulty) {
        if (game.difficulty == diff || generating) return
        game.difficulty = diff
        updateChips()
        startNewPuzzle()
    }

    private fun updateLevel() {
        tvLevel.text = getString(R.string.the_line_level, game.currentLevel)
    }

    // ── Déroulé ───────────────────────────────────────────────────────────────────
    private fun startNewPuzzle() {
        winOverlay.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        board.visibility = View.INVISIBLE
        generating = true
        val mode = game.mode
        val diff = game.difficulty
        updateLevel()
        lifecycleScope.launch {
            val puzzle = withContext(Dispatchers.Default) { TheLineGenerator.generate(mode, diff) }
            generating = false
            loadingView.visibility = View.GONE
            board.visibility = View.VISIBLE
            // Le générateur a un chemin de secours sans trou, il ne rend rien de vide ; et
            // si le mode a changé pendant la génération, c'est la dernière demande qui gagne.
            if (puzzle == null || mode != game.mode || diff != game.difficulty) { startNewPuzzle(); return@launch }
            game.loadPuzzle(puzzle)
            board.loadGame(game)
            saveGame()
        }
    }

    private fun clearTraces() {
        if (generating || game.puzzle == null) return
        winOverlay.visibility = View.GONE
        game.clearAll()
        board.refresh()
        saveGame()
    }

    private fun onSolved() {
        // Une grille déjà payée (effacée puis refaite) ne rapporte rien et ne compte pas deux fois.
        if (!game.rewardClaimed) {
            game.rewardClaimed = true
            solvedLevel = game.currentLevel
            game.onLevelCompleted()
            NeutrinoRepository(this).addBalance(NeutrinoRewards.theLine(game.difficulty.ordinal))
            saveLevels()
        }
        saveGame()
    }

    private fun showWin() {
        tvMessage.text = getString(R.string.the_line_completed, solvedLevel.coerceAtLeast(1))
        updateLevel()
        winOverlay.visibility = View.VISIBLE
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────────
    private fun saveGame() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
            putString(KEY_SAVE, game.serialize())
            putString(KEY_MODE, game.mode.name)
            putString(KEY_DIFFICULTY, game.difficulty.name)
        }
    }

    private fun loadGame(): Boolean {
        val line = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SAVE, null) ?: return false
        return game.deserialize(line)
    }

    private fun saveLevels() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
            game.levels.forEachIndexed { i, v -> putInt(KEY_LEVEL + i, v) }
        }
    }

    private fun loadLevels() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        for (i in game.levels.indices) game.levels[i] = prefs.getInt(KEY_LEVEL + i, 1).coerceAtLeast(1)
    }
}
