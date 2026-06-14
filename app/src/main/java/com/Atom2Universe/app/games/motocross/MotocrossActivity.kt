package com.Atom2Universe.app.games.motocross

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

class MotocrossActivity : ThemedActivity() {

    private lateinit var gameView: MotocrossView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_motocross)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.motocross_view)
        val tvDist  = findViewById<TextView>(R.id.motocross_hud_distance)
        val tvSpeed = findViewById<TextView>(R.id.motocross_hud_speed)

        gameView.onStats = { distM, speedKmh ->
            runOnUiThread {
                tvDist.text  = getString(R.string.motocross_hud_distance, distM.toInt())
                tvSpeed.text = getString(R.string.motocross_hud_speed, speedKmh.toInt())
            }
        }

        gameView.onGameOver = { distM ->
            // 1 neutrino par tranche de 500 m parcourus
            val reward = NeutrinoRewards.perDistance(distM)
            if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        }

        findViewById<ImageButton>(R.id.motocross_btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.motocross_btn_restart).setOnClickListener { gameView.resetGame() }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }
}
