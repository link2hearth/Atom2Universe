package com.Atom2Universe.app.games.bigger

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Accrétion (ancien « Bigger ») : on lâche des astres dans un bocal, deux jumeaux qui se
 * touchent fusionnent en l'astre suivant, de la poussière jusqu'au trou noir.
 *
 * L'activité ne tient que la vue, le son, les records, les neutrinos et la sauvegarde de
 * la partie en cours : les règles vivent dans [BiggerGame], l'affichage dans [BiggerView].
 */
class BiggerActivity : ThemedActivity() {

    companion object {
        const val PREFS = "bigger_save"
        const val KEY_BEST_SCORE = "best_score"
        const val KEY_DISCOVERED = "discovered_tier"
        const val KEY_RUN = "run_state"
    }

    private lateinit var gameView: BiggerView
    private val sfx = BiggerSoundEngine()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bigger)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.bigger_view)
        gameView.sound = sfx
        gameView.bestScore = prefs.getInt(KEY_BEST_SCORE, 0)
        gameView.discoveredTier = prefs.getInt(KEY_DISCOVERED, -1)
        prefs.getString(KEY_RUN, null)?.let { synchronized(gameView.game) { gameView.game.restoreState(it) } }
        gameView.startRun()
        gameView.onRunEnded = { score, _ -> runOnUiThread { saveResult(score) } }
        val tvScore = findViewById<TextView>(R.id.bigger_tv_score)
        val tvBest = findViewById<TextView>(R.id.bigger_tv_best)
        val ivNext = findViewById<ImageView>(R.id.bigger_iv_next)
        gameView.onHud = { hud ->
            runOnUiThread {
                tvScore.text = getString(R.string.bigger_score, hud.score)
                tvBest.text = getString(R.string.bigger_best, hud.best)
                ivNext.setImageBitmap(hud.nextIcon)
            }
        }
        gameView.onDiscovered = { tier ->
            runOnUiThread {
                if (tier > prefs.getInt(KEY_DISCOVERED, -1)) prefs.edit().putInt(KEY_DISCOVERED, tier).apply()
            }
        }

        findViewById<ImageButton>(R.id.bigger_btn_back).setOnClickListener {
            @Suppress("DEPRECATION") onBackPressed()
        }
        findViewById<ImageButton>(R.id.bigger_btn_restart).setOnClickListener { askRestart(it) }
    }

    /**
     * Le bouton est petit et loin du jeu, mais une partie d'Accrétion vaut des minutes :
     * une bulle sous le bouton demande confirmation. Toucher à côté la referme. Sans partie
     * en cours, il n'y a rien à perdre, on repart directement.
     */
    private fun askRestart(anchor: View) {
        if (!synchronized(gameView.game) { gameView.game.drops > 0 && !gameView.game.over }) {
            gameView.restart()
            return
        }
        val dp = resources.displayMetrics.density
        val label = TextView(this).apply {
            text = getString(R.string.bigger_restart_confirm)
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())
            setBackgroundResource(R.drawable.bg_popup_dark)
            setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_rotate, 0, 0, 0)
            compoundDrawablePadding = (8 * dp).toInt()
        }
        val bubble = PopupWindow(label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = 8 * dp
        }
        label.setOnClickListener {
            bubble.dismiss()
            gameView.restart()
        }
        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        // Alignée sur le bord droit du bouton, pour ne pas sortir de l'écran.
        bubble.showAsDropDown(anchor, anchor.width - label.measuredWidth, (4 * dp).toInt())
    }

    private fun saveResult(score: Int) {
        val edit = prefs.edit().remove(KEY_RUN)
        if (score > prefs.getInt(KEY_BEST_SCORE, 0)) edit.putInt(KEY_BEST_SCORE, score)
        edit.apply()
        gameView.bestScore = maxOf(gameView.bestScore, score)
        val reward = NeutrinoRewards.bigger(score)
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
        // La partie en cours survit à une sortie : une partie d'Accrétion dure longtemps.
        val state = synchronized(gameView.game) { gameView.game.saveState() }
        prefs.edit().apply { if (state != null) putString(KEY_RUN, state) else remove(KEY_RUN) }.apply()
    }
}
