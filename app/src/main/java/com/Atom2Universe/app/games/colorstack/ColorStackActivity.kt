package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import androidx.core.content.edit

class ColorStackActivity : ThemedActivity(), ColorStackView.OnMoveListener {

    companion object {
        private const val PREFS_NAME = "color_stack_save"
        private const val KEY_SAVE = "save_json"
        private const val WON_COLOR = 0xFF22C55E.toInt()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var colorStackView: ColorStackView
    private lateinit var movesText: TextView
    private lateinit var levelButtons: List<Pair<ColorStackGame.Difficulty, ImageButton>>

    private val game = ColorStackGame()
    private lateinit var prefs: SharedPreferences

    private var hardGameStartMs = 0L
    private var hardElapsedMs = 0L
    private var timerKnown = false
    private var victoryRecorded = false

    private fun checkpointTimer() {
        if (hardGameStartMs != 0L) {
            hardElapsedMs += android.os.SystemClock.elapsedRealtime() - hardGameStartMs
            hardGameStartMs = 0L
        }
    }

    override fun onResume() {
        super.onResume()
        if (timerKnown && !victoryRecorded && game.difficulty == ColorStackGame.Difficulty.HARD) {
            hardGameStartMs = android.os.SystemClock.elapsedRealtime()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_color_stack)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        colorStackView = findViewById(R.id.color_stack_game_view)
        movesText = findViewById(R.id.color_stack_moves)
        levelButtons = listOf(
            ColorStackGame.Difficulty.EASY to findViewById(R.id.color_stack_level_easy),
            ColorStackGame.Difficulty.MEDIUM to findViewById(R.id.color_stack_level_medium),
            ColorStackGame.Difficulty.HARD to findViewById(R.id.color_stack_level_hard)
        )
        for ((diff, button) in levelButtons) {
            button.setOnClickListener { if (diff != game.difficulty) startNewGame(diff) }
        }

        findViewById<ImageButton>(R.id.color_stack_back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.color_stack_info_button).setOnClickListener {
            showGameRules(R.string.color_stack_title, R.string.color_stack_rules)
        }
        findViewById<TextView>(R.id.color_stack_btn_new).setOnClickListener { startNewGame(game.difficulty) }
        findViewById<ImageButton>(R.id.color_stack_btn_restart).setOnClickListener { restartGame() }
        findViewById<ImageButton>(R.id.color_stack_btn_undo).setOnClickListener { undoMove() }

        colorStackView.game = game
        colorStackView.listener = this

        val restored = loadGame()
        if (!restored) {
            game.newGame(ColorStackGame.Difficulty.EASY)
            GameStatsRepository(this).recordColorStackStarted(game.difficulty.name)
            timerKnown = true
        }

        syncLevelButtons()
        colorStackView.refresh()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        checkpointTimer()
        saveGame()
    }

    /** Le niveau en cours est plein (barres sur l'accent), les deux autres restent discrets. */
    private fun syncLevelButtons() {
        val accent = AppearanceStyle.color(this, R.attr.a2uMusicAccent)
        val onAccent = ContextCompat.getColor(this, R.color.audio_on_accent)
        val text = ContextCompat.getColor(this, R.color.startup_text_primary)
        val radius = 10f * resources.displayMetrics.density
        for ((diff, button) in levelButtons) {
            val current = diff == game.difficulty
            button.setImageDrawable(LevelBarsDrawable(diff.ordinal + 1, if (current) onAccent else text))
            button.background = if (current) GradientDrawable().apply {
                cornerRadius = radius
                setColor(accent)
            } else null
        }
    }

    private fun startNewGame(diff: ColorStackGame.Difficulty) {
        game.newGame(diff)
        colorStackView.game = game
        colorStackView.refresh()
        syncLevelButtons()
        updateUI()
        victoryRecorded = false
        hardElapsedMs = 0L
        timerKnown = true
        hardGameStartMs = if (diff == ColorStackGame.Difficulty.HARD) android.os.SystemClock.elapsedRealtime() else 0L
        GameStatsRepository(this).recordColorStackStarted(diff.name)
        saveGame()
    }

    private fun restartGame() {
        game.restart()
        colorStackView.refresh()
        updateUI()
        saveGame()
    }

    private fun undoMove() {
        if (game.undo()) {
            colorStackView.refresh()
            updateUI()
        }
    }

    override fun onMove(from: Int, to: Int) {
        if (game.move(from, to)) afterMove()
    }

    override fun onMoveGroup(from: Int, to: Int) {
        if (game.moveGroup(from, to) > 0) afterMove()
    }

    private fun afterMove() {
        colorStackView.refresh()
        updateUI()
        if (game.solved) {
            if (!victoryRecorded) {
                checkpointTimer()
                victoryRecorded = true
                val statsRepo = GameStatsRepository(this)
                statsRepo.recordColorStackWon(game.difficulty.name)
                if (game.difficulty == ColorStackGame.Difficulty.HARD && timerKnown && hardElapsedMs > 0L) {
                    statsRepo.recordColorStackHardBestTime(hardElapsedMs)
                }
                NeutrinoRepository(this).addBalance(NeutrinoRewards.colorStack(game.difficulty.ordinal))
            }
        }
        saveGame()
    }

    override fun onColumnSelected(col: Int?) {}

    private fun updateUI() {
        // Victoire : le compteur de coups passe au vert, précédé de 🎉.
        movesText.text = getString(
            if (game.solved) R.string.color_stack_moves_won else R.string.color_stack_moves, game.moves
        )
        movesText.setTextColor(
            if (game.solved) WON_COLOR else ContextCompat.getColor(this, R.color.startup_text_secondary)
        )
    }

    private fun saveGame() {
        val running = if (hardGameStartMs != 0L) android.os.SystemClock.elapsedRealtime() - hardGameStartMs else 0L
        prefs.edit {
            // Inclus dans la partie : un nouveau jeu créé par le widget ne peut pas
            // récupérer le chronomètre ou la victoire de la partie précédente.
            putString(KEY_SAVE, org.json.JSONObject(game.serialize())
                .put("stats_elapsed_ms", hardElapsedMs + running)
                .put("stats_timer_known", timerKnown)
                .put("stats_victory_recorded", victoryRecorded).toString())
        }
    }

    private fun loadGame(): Boolean {
        val json = prefs.getString(KEY_SAVE, null) ?: return false
        if (!game.deserialize(json)) return false
        val saved = org.json.JSONObject(json)
        hardElapsedMs = saved.optLong("stats_elapsed_ms", 0L)
        timerKnown = saved.optBoolean("stats_timer_known", false)
        victoryRecorded = saved.optBoolean("stats_victory_recorded", game.solved)
        return true
    }
}
