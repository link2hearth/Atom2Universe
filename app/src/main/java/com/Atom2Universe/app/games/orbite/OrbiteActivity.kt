package com.Atom2Universe.app.games.orbite

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Orbite : fais tourner l'électron autour du noyau, tape pour inverser son sens,
 * esquive les astéroïdes et capture les quanta d'énergie.
 */
class OrbiteActivity : ThemedActivity() {

    private lateinit var gameView: OrbiteView
    private lateinit var tvScore: TextView
    private lateinit var tvBest: TextView
    private lateinit var overlayStart: View
    private lateinit var overlayGameOver: View
    private lateinit var tvFinalScore: TextView
    private lateinit var tvBestScore: TextView
    private lateinit var tvReward: TextView

    private val prefs by lazy { getSharedPreferences("orbite_save", MODE_PRIVATE) }
    private var best = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orbite)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView        = findViewById(R.id.orbite_view)
        tvScore         = findViewById(R.id.orbite_score)
        tvBest          = findViewById(R.id.orbite_best)
        overlayStart    = findViewById(R.id.orbite_overlay_start)
        overlayGameOver = findViewById(R.id.orbite_overlay_game_over)
        tvFinalScore    = findViewById(R.id.orbite_final_score)
        tvBestScore     = findViewById(R.id.orbite_best_score)
        tvReward        = findViewById(R.id.orbite_reward)

        best = prefs.getInt("best", 0)
        gameView.game.best = best
        updateHud(0)

        gameView.onScoreChanged = { score -> runOnUiThread { updateHud(score) } }
        gameView.onGameOver = { runOnUiThread { showGameOver() } }

        findViewById<Button>(R.id.orbite_btn_start).setOnClickListener { launchGame() }
        findViewById<Button>(R.id.orbite_btn_restart).setOnClickListener { launchGame() }
        findViewById<Button>(R.id.orbite_btn_back_start).setOnClickListener { finish() }
        findViewById<Button>(R.id.orbite_btn_back_gameover).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun launchGame() {
        overlayStart.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        updateHud(0)
        gameView.startGame()
    }

    private fun updateHud(score: Int) {
        tvScore.text = score.toString()
        tvBest.text = getString(R.string.orbite_best_label, if (best > 0) best.toString() else "—")
    }

    private fun showGameOver() {
        val score = gameView.game.score
        val newRecord = score > best
        if (newRecord) {
            best = score
            prefs.edit { putInt("best", best) }
        }
        tvFinalScore.text = getString(R.string.orbite_final_score, score)
        tvBestScore.text = if (newRecord)
            getString(R.string.orbite_new_best)
        else
            getString(R.string.orbite_best_score, best)

        // Récompense : 1 neutrino par tranche de temps de survie (source unique).
        val reward = NeutrinoRewards.perTime(gameView.game.elapsedMs)
        if (reward > 0) {
            NeutrinoRepository(this).addBalance(reward)
            tvReward.text = getString(R.string.orbite_reward, reward)
            tvReward.visibility = View.VISIBLE
        } else {
            tvReward.visibility = View.GONE
        }
        updateHud(score)
        overlayGameOver.visibility = View.VISIBLE
    }
}
