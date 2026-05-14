package com.Atom2Universe.app.games.wavesurf

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class WaveSurfActivity : ThemedActivity() {

    private lateinit var gameView: WaveSurfView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_wave_surf)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.wave_surf_view)
        val tvDist = findViewById<TextView>(R.id.wave_surf_hud_distance)
        val tvSpeed = findViewById<TextView>(R.id.wave_surf_hud_speed)
        val tvAlt = findViewById<TextView>(R.id.wave_surf_hud_altitude)

        gameView.onStats = { distM, speedKmh, altitudeM ->
            runOnUiThread {
                tvDist.text = getString(R.string.wave_surf_hud_distance, distM.toInt())
                tvSpeed.text = getString(R.string.wave_surf_hud_speed, speedKmh.toInt())
                tvAlt.text = getString(R.string.wave_surf_hud_altitude, altitudeM.toInt())
            }
        }

        val tvTheme = findViewById<TextView>(R.id.wave_surf_btn_theme)
        fun themeLabel(theme: WaveSurfView.ColorTheme) = getString(when (theme) {
            WaveSurfView.ColorTheme.VIVID      -> R.string.wave_surf_theme_vivid
            WaveSurfView.ColorTheme.PASTEL     -> R.string.wave_surf_theme_pastel
            WaveSurfView.ColorTheme.WEATHERED  -> R.string.wave_surf_theme_old
            WaveSurfView.ColorTheme.GRAYSCALE  -> R.string.wave_surf_theme_gray
        })
        tvTheme.setOnClickListener { tvTheme.text = themeLabel(gameView.cycleTheme()) }

        findViewById<ImageButton>(R.id.wave_surf_btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.wave_surf_btn_restart).setOnClickListener { gameView.resetGame() }
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
