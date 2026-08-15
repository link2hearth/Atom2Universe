package com.Atom2Universe.app.games.cosmorun

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

class CosmoRunActivity : ThemedActivity() {

    private lateinit var runView: CosmoRunView
    private lateinit var overlayStart: View
    private lateinit var overlayGameOver: View
    private lateinit var tvFinalScore: TextView
    private lateinit var tvRunStats: TextView
    private lateinit var tvBestScore: TextView

    private val prefs by lazy { getSharedPreferences("cosmo_run_save", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cosmo_run)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        runView         = findViewById(R.id.cosmo_run_view)
        overlayStart    = findViewById(R.id.cosmo_overlay_start)
        overlayGameOver = findViewById(R.id.cosmo_overlay_game_over)
        tvFinalScore    = findViewById(R.id.cosmo_tv_final_score)
        tvRunStats      = findViewById(R.id.cosmo_tv_run_stats)
        tvBestScore     = findViewById(R.id.cosmo_tv_best_score)

        setupBackNavigation()
        runView.game.initBestScore(prefs.getInt("best_score", 0))

        runView.onGameOver = {
            runOnUiThread {
                val game = runView.game
                tvFinalScore.text = getString(R.string.cosmo_run_score_label, game.score)
                tvRunStats.text = getString(
                    R.string.cosmo_run_stats_label, game.distance.toInt(), game.atomsCollected
                )
                tvBestScore.text = getString(R.string.cosmo_run_best_label, game.bestScore)
                overlayGameOver.visibility = View.VISIBLE
                prefs.edit { putInt("best_score", game.bestScore) }
                // 1 neutrino par tranche de 500 m parcourus (comme Motocross)
                val reward = NeutrinoRewards.perDistance(game.distance)
                if (reward > 0) NeutrinoRepository(this).addBalance(reward)
            }
        }

        findViewById<Button>(R.id.cosmo_btn_start).setOnClickListener {
            overlayStart.visibility = View.GONE
            runView.startGame()
        }
        findViewById<Button>(R.id.cosmo_btn_back_start).setOnClickListener { finish() }
        findViewById<Button>(R.id.cosmo_btn_restart).setOnClickListener {
            overlayGameOver.visibility = View.GONE
            runView.startGame()
        }
        findViewById<Button>(R.id.cosmo_btn_back_gameover).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        runView.resume()
    }

    override fun onPause() {
        super.onPause()
        runView.pause()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }
}
