package com.Atom2Universe.app.games.survivor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.addCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class SurvivorActivity : ThemedActivity() {

    private lateinit var gameView: SurvivorView
    private lateinit var tvTitle: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val titleUpdater = object : Runnable {
        override fun run() {
            val g = gameView.game
            tvTitle.text = when (g.phase) {
                GamePhase.PLAYING, GamePhase.LEVEL_UP, GamePhase.PAUSED -> {
                    val sec = g.survivalTime.toInt()
                    "Lv.${g.player.level}   %d:%02d   ${g.player.kills}".format(sec / 60, sec % 60)
                }
                else -> getString(R.string.survivor_title)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survivor)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.survivor_view)
        tvTitle  = findViewById(R.id.survivor_tv_title)

        val prefs = getSharedPreferences("survivor_save", MODE_PRIVATE)
        gameView.game.bestTime  = prefs.getFloat("best_time", 0f)
        gameView.game.bestKills = prefs.getInt("best_kills", 0)

        onBackPressedDispatcher.addCallback(this) {
            if (gameView.game.phase == GamePhase.WEAPON_SELECT) finish()
            else gameView.requestMenu()
        }

        findViewById<ImageButton>(R.id.survivor_btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<ImageButton>(R.id.survivor_btn_pause).setOnClickListener {
            gameView.requestPause()
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
        handler.post(titleUpdater)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        handler.removeCallbacks(titleUpdater)
        val g = gameView.game
        if (g.bestTime > 0f) {
            getSharedPreferences("survivor_save", MODE_PRIVATE).edit()
                .putFloat("best_time", g.bestTime)
                .putInt("best_kills", g.bestKills)
                .apply()
        }
    }

}
