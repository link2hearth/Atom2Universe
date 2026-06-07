package com.Atom2Universe.app.games.particules

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.particules.data.ParticulesDatabase
import com.Atom2Universe.app.games.particules.data.ParticulesMetaEntity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activité Particules – casse-briques avec rogue-like (reliques, méta-progression Room).
 */
class ParticulesActivity : AppCompatActivity() {

    private lateinit var gameView: ParticulesView
    private lateinit var pauseButton: ImageButton
    private lateinit var music: ParticulesMusic
    private val db by lazy { ParticulesDatabase.getInstance(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_particules)

        music = ParticulesMusic(lifecycleScope)
        music.start()

        gameView = ParticulesView(this)
        gameView.music = music
        val container = findViewById<FrameLayout>(R.id.particules_container)
        container.addView(gameView)

        pauseButton = findViewById(R.id.pause_button)

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }

        pauseButton.setOnClickListener {
            when {
                gameView.isPaused() -> {
                    gameView.resume()
                    pauseButton.setImageResource(R.drawable.ic_pause)
                    pauseButton.contentDescription = getString(R.string.particules_pause)
                }
                gameView.isPlaying() -> {
                    gameView.pause()
                    pauseButton.setImageResource(R.drawable.ic_play)
                    pauseButton.contentDescription = getString(R.string.particules_resume)
                }
            }
        }

        // Load meta progression, then wire callback for saves
        lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) { db.metaDao().getMeta() }
            val snap = entity?.let {
                ParticulesView.MetaSnapshot(
                    highScore = it.highScore,
                    highestLevel = it.highestLevel,
                    runsPlayed = it.runsPlayed,
                    totalGold = it.totalGold,
                    totalXp = it.totalXp,
                    bestCombo = it.bestCombo,
                    shopExtraLives = it.shopExtraLives,
                    shopSlowBall = it.shopSlowBall,
                    shopWidePaddle = it.shopWidePaddle,
                    shopStartShield = it.shopStartShield,
                    shopGoldMagnet = it.shopGoldMagnet,
                    shopMultiStart = it.shopMultiStart,
                    shopStackTimers = it.shopStackTimers
                )
            } ?: ParticulesView.MetaSnapshot()
            gameView.setMeta(snap)
        }

        gameView.onMetaUpdate = { snap ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.metaDao().upsert(ParticulesMetaEntity(
                        id = 1,
                        highScore = snap.highScore,
                        highestLevel = snap.highestLevel,
                        runsPlayed = snap.runsPlayed,
                        totalGold = snap.totalGold,
                        totalXp = snap.totalXp,
                        bestCombo = snap.bestCombo,
                        shopExtraLives = snap.shopExtraLives,
                        shopSlowBall = snap.shopSlowBall,
                        shopWidePaddle = snap.shopWidePaddle,
                        shopStartShield = snap.shopStartShield,
                        shopGoldMagnet = snap.shopGoldMagnet,
                        shopMultiStart = snap.shopMultiStart,
                        shopStackTimers = snap.shopStackTimers
                    ))
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        music.pause()
        pauseButton.setImageResource(R.drawable.ic_play)
    }

    override fun onResume() {
        super.onResume()
        music.resumeBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        music.stop()
    }
}
