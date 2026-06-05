package com.Atom2Universe.app.games.starswar

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
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
        findViewById<ImageButton>(R.id.stars_war_btn_pause).setOnClickListener { gameView.togglePause() }

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
