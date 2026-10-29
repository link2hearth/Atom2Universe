package com.Atom2Universe.app.games.hexrunner

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules

class HexRunnerActivity : ThemedActivity() {

    private lateinit var hexView: HexRunnerView
    private lateinit var overlayStart: View
    private lateinit var overlayGameOver: View
    private lateinit var tvFinalScore: TextView
    private lateinit var tvBestScore: TextView
    private lateinit var btnModeStart: Button
    private lateinit var btnModeGameOver: Button

    private val prefs by lazy { getSharedPreferences("hexrunner_save", MODE_PRIVATE) }
    private var numFaces = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hex_runner)
        enableImmersiveMode()
        // L'écran reste allumé pendant une partie seulement (HexRunnerView.keepScreenOn).

        hexView        = findViewById(R.id.hex_runner_view)
        overlayStart   = findViewById(R.id.hex_overlay_start)
        overlayGameOver = findViewById(R.id.hex_overlay_game_over)
        tvFinalScore   = findViewById(R.id.hex_tv_final_score)
        tvBestScore    = findViewById(R.id.hex_tv_best_score)
        btnModeStart   = findViewById(R.id.hex_btn_mode_start)
        btnModeGameOver = findViewById(R.id.hex_btn_mode_gameover)

        setupBackNavigation()
        numFaces = prefs.getInt("num_faces", 6)

        val gameStatsRepo = GameStatsRepository(this)
        hexView.game.initBestScore(gameStatsRepo.load().hexRunnerBestMs)

        updateModeButtons()

        btnModeStart.setOnClickListener   { toggleMode() }
        btnModeGameOver.setOnClickListener { toggleMode() }

        hexView.onGameOver = {
            runOnUiThread {
                val secs = hexView.game.score / 1000
                val best = hexView.game.bestScore / 1000
                tvFinalScore.text = getString(R.string.hex_runner_score_label, HexRunnerView.formatTime(this, secs))
                tvBestScore.text  = getString(R.string.hex_runner_best_label,  HexRunnerView.formatTime(this, best))
                overlayGameOver.visibility = View.VISIBLE
                gameStatsRepo.recordHexRunnerBestTime(hexView.game.bestScore)
                // 1 neutrino par tranche de 15 s de jeu ; la part déjà versée en quittant n'est pas repayée.
                payUnpaidReward()
            }
        }

        findViewById<Button>(R.id.hex_btn_start).setOnClickListener {
            overlayStart.visibility = View.GONE
            hexView.startGame(numFaces)
        }
        findViewById<Button>(R.id.hex_btn_back_start).setOnClickListener { finish() }
        findViewById<Button>(R.id.hex_btn_restart).setOnClickListener {
            overlayGameOver.visibility = View.GONE
            hexView.startGame(numFaces)
        }
        findViewById<Button>(R.id.hex_btn_back_gameover).setOnClickListener { finish() }
        findViewById<View>(R.id.hex_info_button).setOnClickListener {
            hexView.held = true
            showGameRules(R.string.hex_runner_title, R.string.hex_runner_rules) { hexView.held = false }
        }
    }

    private fun toggleMode() {
        numFaces = if (numFaces == 6) 8 else 6
        prefs.edit().putInt("num_faces", numFaces).apply()
        updateModeButtons()
    }

    private fun updateModeButtons() {
        val label = getString(
            if (numFaces == 6) R.string.hex_runner_shape_hexagon
            else               R.string.hex_runner_shape_octagon
        )
        btnModeStart.text    = label
        btnModeGameOver.text = label
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun payUnpaidReward() {
        val reward = hexView.collectUnpaidReward()
        if (reward > 0) NeutrinoRepository(this).addBalance(reward)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Partie figée à la perte du focus ; un toucher la relance.
        if (!hasFocus) hexView.held = true
    }

    override fun onPause() {
        super.onPause()
        hexView.held = true
        payUnpaidReward()
    }
}
