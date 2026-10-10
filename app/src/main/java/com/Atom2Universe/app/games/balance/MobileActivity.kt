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

/**
 * Le mobile : accrocher les objets aux crochets pour que toutes les tiges pendent droit.
 * Voir [MobileGame].
 */
class MobileActivity : ThemedActivity(), MobileView.Listener {

    private companion object {
        const val PREFS_NAME = "mobile_game"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_BEST_LEVEL = "best_level_"
        const val KEY_LEVEL = "level_"
    }

    /** Le niveau où reprendre une difficulté. */
    private fun niveauSauve(diff: MobileGame.Difficulty): Int = prefs.getInt(KEY_LEVEL + diff.ordinal, 1)

    private lateinit var gameView: MobileView
    private lateinit var levelText: TextView
    private lateinit var newButton: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var prefs: SharedPreferences
    private lateinit var sfx: MobileSfx

    private var ignoreSpinnerChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        gameView = findViewById(R.id.mobile_view)
        levelText = findViewById(R.id.mobile_level)
        newButton = findViewById(R.id.mobile_btn_new)
        difficultySpinner = findViewById(R.id.mobile_difficulty_spinner)

        gameView.listener = this
        sfx = MobileSfx(this)
        gameView.sfx = sfx

        findViewById<ImageButton>(R.id.mobile_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.mobile_btn_restart).setOnClickListener {
            synchronized(gameView.game) { gameView.game.recommencer() }
            gameView.syncLevel()
            updateUi()
        }
        newButton.setOnClickListener { newLevel() }

        val saved = prefs.getInt(KEY_DIFFICULTY, 0)
            .coerceIn(0, MobileGame.Difficulty.entries.size - 1)
        val diff = MobileGame.Difficulty.entries[saved]
        synchronized(gameView.game) { gameView.game.newLevel(diff, niveauSauve(diff)) }
        gameView.syncLevel()

        setupSpinner(saved)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        sfx.start()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        sfx.release()
    }

    private fun setupSpinner(selected: Int) {
        val labels = listOf(
            getString(R.string.mobile_difficulty_easy),
            getString(R.string.mobile_difficulty_medium),
            getString(R.string.mobile_difficulty_hard),
            getString(R.string.mobile_difficulty_expert)
        )
        val adapter = ArrayAdapter(this, com.Atom2Universe.app.R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(com.Atom2Universe.app.R.layout.item_spinner_dropdown)
        difficultySpinner.adapter = adapter

        ignoreSpinnerChange = true
        difficultySpinner.setSelection(selected, false)
        ignoreSpinnerChange = false

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val diff = MobileGame.Difficulty.entries[position]
                if (diff == gameView.game.difficulty) return
                prefs.edit { putInt(KEY_DIFFICULTY, position) }
                synchronized(gameView.game) { gameView.game.newLevel(diff, niveauSauve(diff)) }
                gameView.syncLevel()
                updateUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun newLevel() {
        synchronized(gameView.game) { gameView.game.newLevel() }
        gameView.syncLevel()
        updateUi()
    }

    override fun onWon() {
        val game = gameView.game
        val ordinal = game.difficulty.ordinal
        // Un tableau gagné, recommencé puis regagné ne paie qu'une fois.
        val dejaPaye = synchronized(game) { game.recompense.also { game.recompense = true } }
        if (!dejaPaye) {
            val reward = NeutrinoRewards.mobile(ordinal)
            NeutrinoRepository(this).addBalance(reward)
            gameView.gain = getString(R.string.mobile_gain, reward)
        }
        val key = KEY_BEST_LEVEL + ordinal
        prefs.edit {
            if (game.level > prefs.getInt(key, 0)) putInt(key, game.level)
            // La partie reprend au tableau suivant, même après avoir quitté l'appli.
            putInt(KEY_LEVEL + ordinal, game.level + 1)
        }
        updateUi()
    }

    private fun updateUi() {
        val game = gameView.game
        val best = prefs.getInt(KEY_BEST_LEVEL + game.difficulty.ordinal, 0)
        levelText.text = if (best > 0) {
            getString(R.string.balance_level_best, game.level, best)
        } else {
            getString(R.string.balance_level, game.level)
        }
        newButton.text = if (game.gagne) {
            getString(R.string.balance_btn_next)
        } else {
            getString(R.string.balance_btn_new)
        }
    }
}
