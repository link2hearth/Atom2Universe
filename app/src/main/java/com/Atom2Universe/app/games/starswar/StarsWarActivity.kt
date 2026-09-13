package com.Atom2Universe.app.games.starswar

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class StarsWarActivity : ThemedActivity() {

    private lateinit var gameView: StarsWarView
    private val sfx   by lazy { StarsWarSoundEngine() }
    private val music by lazy { StarsWarProceduralMusic(lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_stars_war)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.stars_war_view)
        findViewById<ImageButton>(R.id.stars_war_btn_back).setOnClickListener { finish() }
        val pauseButton = findViewById<ImageButton>(R.id.stars_war_btn_pause)
        val magnetButton = findViewById<Button>(R.id.sw_magnet_button)
        val novaButton = findViewById<Button>(R.id.sw_nova_button)
        pauseButton.setOnClickListener { gameView.togglePause() }
        magnetButton.setOnClickListener { gameView.requestMagnet() }
        novaButton.setOnClickListener { gameView.requestNova() }
        // Ces commandes sont hors de l'aire de combat : aucun projectile n'est masqué.
        gameView.onControlsChanged = { mag, nova, active, paused ->
            gameView.post {
                if (!isDestroyed) {
                    fun status(value: Int): String = when {
                        value == -2 -> getString(R.string.sw_power_locked)
                        value == -1 -> getString(R.string.sw_power_ready)
                        value < -2 -> getString(R.string.sw_power_active, -value - 3)
                        value > 0 -> getString(R.string.sw_power_cooldown, value)
                        else -> getString(R.string.sw_power_used)
                    }
                    magnetButton.text = getString(R.string.sw_power_button, getString(R.string.sw_power_magnet), status(mag))
                    novaButton.text = getString(R.string.sw_power_button, getString(R.string.sw_power_nova), status(nova))
                    magnetButton.isEnabled = active && mag == -1
                    novaButton.isEnabled = active && nova == -1
                    magnetButton.alpha = if (mag == -2) .55f else 1f
                    novaButton.alpha = if (nova == -2) .55f else 1f
                    pauseButton.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
                    pauseButton.contentDescription = getString(if (paused) R.string.sw_resume_action else R.string.sw_pause_action)
                }
            }
        }

        gameView.onPlayerShot     = { sfx.onPlayerShot() }
        gameView.onEnemyDestroyed = { sfx.onEnemyDestroyed() }
        gameView.onBossDestroyed  = { sfx.onBossDestroyed() }
        gameView.onPlayerHitCb    = { sfx.onPlayerHit() }
        gameView.onGameOverCb     = { sfx.onGameOver() }
        gameView.onNewWaveCb      = { n -> sfx.onNewWave(); music.onWaveChanged(n) }
        gameView.onMeteorPhaseCb  = { sfx.onMeteorPhase() }
    }

    override fun onResume() {
        super.onResume()
        sfx.start()
        music.start(1)
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        sfx.stop()
        music.stop()
    }
}
