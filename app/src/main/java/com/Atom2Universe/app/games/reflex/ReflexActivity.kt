package com.Atom2Universe.app.games.reflex

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.addCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Collisionneur (ancien « Réflexes ») : des particules apparaissent, un anneau se resserre sur chacune, et
 * il faut les toucher au moment où l'anneau touche le cœur. L'antimatière, elle,
 * ne se touche pas. Un seul mode, qui accélère tout seul.
 *
 * L'activité ne fait que tenir la vue, le son, le record et les neutrinos : tout
 * le jeu vit dans [ReflexGame] (les règles) et [ReflexView] (l'affichage).
 */
class ReflexActivity : ThemedActivity() {

    companion object {
        const val PREFS = "reflex_save"
        const val KEY_BEST_SCORE = "best_score"
        const val KEY_BEST_COMBO = "best_combo"
    }

    private lateinit var gameView: ReflexView
    private val sfx = ReflexSoundEngine()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reflex)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.reflex_view)
        gameView.sound = sfx
        gameView.bestScore = prefs.getLong(KEY_BEST_SCORE, 0L)
        gameView.onGameFinished = { score, bestCombo ->
            runOnUiThread { saveResult(score, bestCombo) }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!gameView.requestBack()) finish()
        }
        findViewById<View>(R.id.back_button).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun saveResult(score: Long, bestCombo: Int) {
        val edit = prefs.edit()
        if (score > prefs.getLong(KEY_BEST_SCORE, 0L)) edit.putLong(KEY_BEST_SCORE, score)
        if (bestCombo > prefs.getInt(KEY_BEST_COMBO, 0)) edit.putInt(KEY_BEST_COMBO, bestCombo)
        edit.apply()

        val reward = NeutrinoRewards.reflex(score)
        if (reward > 0) NeutrinoRepository(this).addBalance(reward)
    }

    override fun onResume() {
        super.onResume()
        sfx.start()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        sfx.stop()
    }
}
