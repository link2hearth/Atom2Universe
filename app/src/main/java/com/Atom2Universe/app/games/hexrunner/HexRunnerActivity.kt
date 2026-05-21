package com.Atom2Universe.app.games.hexrunner

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class HexRunnerActivity : ThemedActivity() {

    private lateinit var hexView: HexRunnerView
    private lateinit var overlayStart: View
    private lateinit var overlayGameOver: View
    private lateinit var tvFinalScore: TextView
    private lateinit var tvBestScore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hex_runner)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hexView = findViewById(R.id.hex_runner_view)
        overlayStart = findViewById(R.id.hex_overlay_start)
        overlayGameOver = findViewById(R.id.hex_overlay_game_over)
        tvFinalScore = findViewById(R.id.hex_tv_final_score)
        tvBestScore = findViewById(R.id.hex_tv_best_score)

        val gameStatsRepo = GameStatsRepository(this)
        hexView.game.initBestScore(gameStatsRepo.load().hexRunnerBestMs)

        hexView.onGameOver = {
            runOnUiThread {
                val secs = hexView.game.score / 1000
                val best = hexView.game.bestScore / 1000
                tvFinalScore.text = getString(R.string.hex_runner_score_label, formatTime(secs))
                tvBestScore.text = getString(R.string.hex_runner_best_label, formatTime(best))
                overlayGameOver.visibility = View.VISIBLE
                gameStatsRepo.recordHexRunnerBestTime(hexView.game.bestScore)
            }
        }

        findViewById<Button>(R.id.hex_btn_start).setOnClickListener {
            overlayStart.visibility = View.GONE
            hexView.startGame()
        }
        findViewById<Button>(R.id.hex_btn_back_start).setOnClickListener { finish() }
        findViewById<Button>(R.id.hex_btn_restart).setOnClickListener {
            overlayGameOver.visibility = View.GONE
            hexView.startGame()
        }
        findViewById<Button>(R.id.hex_btn_back_gameover).setOnClickListener { finish() }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        finish()
    }

    private fun formatTime(secs: Long): String {
        val m = secs / 60; val s = secs % 60
        return if (m > 0) "%d:%02d".format(m, s) else "$s s"
    }
}
