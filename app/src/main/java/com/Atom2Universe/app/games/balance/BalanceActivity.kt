package com.Atom2Universe.app.games.balance

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.abs

/**
 * Jeu d'équilibre : répartir des poids sur une planche posée sur un pivot,
 * puis relâcher le levier pour vérifier que les deux côtés se compensent.
 */
class BalanceActivity : ThemedActivity(), BalanceView.Listener {

    private companion object {
        const val PREFS_NAME = "balance_game"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_BEST_LEVEL = "best_level_"
    }

    private lateinit var gameView: BalanceView
    private lateinit var statusText: TextView
    private lateinit var levelText: TextView
    private lateinit var testButton: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var prefs: SharedPreferences

    private var ignoreSpinnerChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        gameView = findViewById(R.id.balance_view)
        statusText = findViewById(R.id.balance_status)
        levelText = findViewById(R.id.balance_level)
        testButton = findViewById(R.id.balance_btn_test)
        difficultySpinner = findViewById(R.id.balance_difficulty_spinner)

        gameView.listener = this

        findViewById<ImageButton>(R.id.balance_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.balance_btn_new).setOnClickListener { newLevel() }
        findViewById<TextView>(R.id.balance_btn_reset).setOnClickListener { resetPlacement() }
        testButton.setOnClickListener { onTestButton() }

        val saved = prefs.getInt(KEY_DIFFICULTY, 0)
            .coerceIn(0, BalanceGame.Difficulty.entries.size - 1)
        val difficulty = BalanceGame.Difficulty.entries[saved]
        synchronized(gameView.game) { gameView.game.newLevel(difficulty) }
        gameView.syncPhase()

        setupSpinner(saved)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    // ── Barre de difficulté ───────────────────────────────────────────────────

    private fun setupSpinner(selected: Int) {
        val labels = listOf(
            getString(R.string.balance_difficulty_easy),
            getString(R.string.balance_difficulty_medium),
            getString(R.string.balance_difficulty_hard)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter

        ignoreSpinnerChange = true
        difficultySpinner.setSelection(selected, false)
        ignoreSpinnerChange = false

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val diff = BalanceGame.Difficulty.entries[position]
                if (diff == gameView.game.difficulty) return
                prefs.edit { putInt(KEY_DIFFICULTY, position) }
                synchronized(gameView.game) { gameView.game.newLevel(diff) }
                gameView.syncPhase()
                updateUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun newLevel() {
        synchronized(gameView.game) { gameView.game.newLevel() }
        gameView.syncPhase()
        updateUi()
    }

    private fun resetPlacement() {
        synchronized(gameView.game) { gameView.game.resetPlacement() }
        gameView.syncPhase()
        updateUi()
    }

    /** Après un essai raté : on reprend la pose sans tout défaire. */
    private fun resumePlacing() {
        synchronized(gameView.game) { gameView.game.resumePlacing() }
        gameView.syncPhase()
        updateUi()
    }

    private fun onTestButton() {
        val game = gameView.game
        when (game.phase) {
            BalanceGame.Phase.PLACING -> {
                if (!game.canTest()) {
                    statusText.text = getString(R.string.balance_status_place)
                    return
                }
                synchronized(game) { game.startTest() }
                gameView.syncPhase()
            }
            BalanceGame.Phase.WON -> newLevel()
            // Pendant le test aussi : dès qu'on voit que ça part mal, on reprend
            // la main sans attendre le verdict.
            BalanceGame.Phase.LOST, BalanceGame.Phase.TESTING -> resumePlacing()
        }
        updateUi()
    }

    // ── Retours de la vue ─────────────────────────────────────────────────────

    override fun onPlacementChanged() {
        updateUi()
    }

    override fun onResult(won: Boolean) {
        val game = gameView.game
        // Le verdict peut arriver juste après un « Ajuster » : dans ce cas la
        // partie est déjà repassée en pose et il ne faut rien récompenser.
        val expected = if (won) BalanceGame.Phase.WON else BalanceGame.Phase.LOST
        if (game.phase != expected) return

        if (won) {
            val reward = currentReward()
            NeutrinoRepository(this).addBalance(reward)
            val key = KEY_BEST_LEVEL + game.difficulty.ordinal
            val reached = game.level - 1
            if (reached > prefs.getInt(key, 0)) prefs.edit { putInt(key, reached) }
            statusText.text = getString(R.string.balance_status_won, reward)
        } else {
            statusText.text = lossMessage()
        }
        updateUi(keepStatus = true)
    }

    // ── Affichage ─────────────────────────────────────────────────────────────

    private fun updateUi(keepStatus: Boolean = false) {
        val game = gameView.game
        val best = prefs.getInt(KEY_BEST_LEVEL + game.difficulty.ordinal, 0)
        levelText.text = if (best > 0) {
            getString(R.string.balance_level_best, game.level, best)
        } else {
            getString(R.string.balance_level, game.level)
        }

        testButton.text = when (game.phase) {
            BalanceGame.Phase.WON -> getString(R.string.balance_btn_next)
            BalanceGame.Phase.LOST, BalanceGame.Phase.TESTING ->
                getString(R.string.balance_btn_adjust)
            else -> getString(R.string.balance_btn_test)
        }
        testButton.alpha = if (game.phase == BalanceGame.Phase.PLACING && !game.allPlaced) 0.45f else 1f

        if (keepStatus) return
        statusText.text = when (game.phase) {
            BalanceGame.Phase.PLACING -> when (game.notice) {
                BalanceGame.Notice.DEAD_ZONE -> getString(R.string.balance_notice_dead_zone)
                else -> withReward(
                    if (game.allPlaced) {
                        getString(R.string.balance_status_ready)
                    } else {
                        getString(R.string.balance_status_place)
                    }
                )
            }
            BalanceGame.Phase.TESTING -> getString(R.string.balance_status_testing)
            BalanceGame.Phase.WON -> getString(R.string.balance_status_won, currentReward())
            BalanceGame.Phase.LOST -> lossMessage()
        }
    }

    private fun lossMessage(): String {
        val game = gameView.game
        val reason = when (game.notice) {
            BalanceGame.Notice.FELL -> getString(R.string.balance_status_lost_fell)
            BalanceGame.Notice.TIPPED -> getString(R.string.balance_status_lost_tipped)
            else -> getString(
                R.string.balance_status_lost_tilt,
                formatDeg(abs(game.verdictLeanDeg)),
                formatDeg(game.difficulty.toleranceDeg)
            )
        }
        return withReward(reason)
    }

    /** Récompense que vaudrait une réussite maintenant, essais ratés déduits. */
    private fun currentReward(): Int {
        val game = gameView.game
        return NeutrinoRewards.balance(game.difficulty.ordinal, game.failedAttempts)
    }

    /**
     * Ajoute au message la récompense encore en jeu, mais seulement après un
     * premier essai : tant qu'on n'a rien testé, il n'y a rien à annoncer.
     */
    private fun withReward(message: String): String {
        val game = gameView.game
        if (game.testAttempts == 0) return message
        return message + "\n" + getString(R.string.balance_reward_next, currentReward())
    }

    private fun formatDeg(value: Float): String = String.format("%.1f", value)
}
