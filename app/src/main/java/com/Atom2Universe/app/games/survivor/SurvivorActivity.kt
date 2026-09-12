package com.Atom2Universe.app.games.survivor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
    private lateinit var btnPause: ImageButton
    private val handler = Handler(Looper.getMainLooper())
    private val titleUpdater = object : Runnable {
        override fun run() {
            val g = gameView.game
            tvTitle.text = when (g.phase) {
                GamePhase.PLAYING, GamePhase.LEVEL_UP, GamePhase.PAUSED -> {
                    val sec = g.survivalTime.toInt()
                    getString(R.string.survivor_time_mmss, sec / 60, sec % 60)
                }
                else -> getString(R.string.survivor_title)
            }
            // Un bouton pause n'a de sens qu'en plein combat : ailleurs il ne ferait rien.
            btnPause.visibility = if (g.phase == GamePhase.PLAYING) View.VISIBLE else View.INVISIBLE
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

        // Records et partie en cours sont lus avant que la boucle ne démarre.
        gameView.showHome()

        onBackPressedDispatcher.addCallback(this) {
            if (!gameView.onBackPressed()) finish()
        }

        findViewById<ImageButton>(R.id.survivor_btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        btnPause = findViewById(R.id.survivor_btn_pause)
        btnPause.setOnClickListener { gameView.requestPause() }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
        handler.post(titleUpdater)
    }

    override fun onPause() {
        super.onPause()
        // La boucle est arrêtée d'abord : la sauvegarde parcourt les listes du jeu.
        gameView.pause()
        handler.removeCallbacks(titleUpdater)
        gameView.persist()
    }

}
