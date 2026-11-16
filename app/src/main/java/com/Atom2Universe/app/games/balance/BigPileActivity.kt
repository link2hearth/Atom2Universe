package com.Atom2Universe.app.games.balance

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules

/**
 * Le mode « Gros tas » d'Équilibre : la vue fait tout le jeu, l'activité ne montre que le score,
 * le record, et le bilan en fin de partie.
 */
class BigPileActivity : ThemedActivity(), BigPileView.Listener {

    private companion object {
        const val PREFS_NAME = "balance_game"
        const val KEY_BEST = "big_pile_best"
    }

    private lateinit var gameView: BigPileView
    private lateinit var scoreText: TextView
    private lateinit var endPanel: LinearLayout
    private lateinit var endTitle: TextView
    private lateinit var endDetail: TextView
    private val couleurs by lazy { SciencePalette(this) }

    /** Vrai une fois la fin de la partie en cours comptée : on ne paie qu'une fois. */
    private var settled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(this@BigPileActivity)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), 0, px(8), 0)
        }
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_24)
            setColorFilter(couleurs.secondary)
            background = null
            contentDescription = getString(R.string.balance_back)
            setPadding(px(10), px(10), px(10), px(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        scoreText = TextView(this).apply {
            setTextColor(couleurs.text)
            textSize = if (resources.configuration.smallestScreenWidthDp >= 600) 20f else 16f
            setPadding(px(8), 0, 0, 0)
        }
        bar.addView(scoreText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_info)
            setColorFilter(couleurs.accent)
            background = null
            contentDescription = getString(R.string.kit_rules)
            setPadding(px(10), px(10), px(10), px(10))
            setOnClickListener { showGameRules(R.string.balance_menu_pile, R.string.big_pile_rules) }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(52)))

        val stage = FrameLayout(this)
        gameView = BigPileView(this)
        gameView.listener = this
        stage.addView(gameView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        endPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = couleurs.shape(couleurs.surface, 18f)
            setPadding(px(28), px(22), px(28), px(20))
            visibility = View.GONE
            isClickable = true
        }
        endTitle = TextView(this).apply {
            setTextColor(couleurs.accent)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        endDetail = TextView(this).apply {
            setTextColor(couleurs.text)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, px(6), 0, px(16))
        }
        val again = TextView(this).apply {
            setText(R.string.big_pile_again)
            setTextColor(couleurs.onAccent)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = couleurs.shape(couleurs.accent, 22f)
            setPadding(px(26), px(12), px(26), px(12))
            setOnClickListener { newGame() }
        }
        endPanel.addView(endTitle)
        endPanel.addView(endDetail)
        endPanel.addView(again, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        stage.addView(endPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        root.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        newGame()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private fun newGame() {
        settled = false
        endPanel.visibility = View.GONE
        gameView.newGame()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onChanged()
    }

    override fun onChanged() {
        val game = gameView.game
        val (score, phase, ending) = synchronized(game) { Triple(game.score, game.phase, game.ending) }
        val best = prefs.getInt(KEY_BEST, 0)
        val pieces = resources.getQuantityString(R.plurals.big_pile_pieces, score, score)
        scoreText.text = if (best > 0) {
            getString(R.string.big_pile_score_line, pieces, getString(R.string.big_pile_best, best))
        } else {
            pieces
        }
        if (phase == BigPileGame.Phase.OVER && !settled) settle(score, best, ending)
    }

    /** Fin de partie : neutrinos, record, et le bilan par-dessus le tas qui s'écroule. */
    private fun settle(score: Int, best: Int, ending: BigPileGame.Ending) {
        settled = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val reward = NeutrinoRewards.bigPile(score)
        if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        val record = score > best
        if (record) prefs.edit { putInt(KEY_BEST, score) }
        endTitle.setText(
            when (ending) {
                BigPileGame.Ending.DROPPED -> R.string.big_pile_over_dropped
                BigPileGame.Ending.FULL -> R.string.big_pile_over_full
                else -> R.string.big_pile_over_tipped
            }
        )
        val pieces = resources.getQuantityString(R.plurals.big_pile_pieces, score, score)
        var detail = getString(R.string.big_pile_over_detail, pieces, reward)
        if (record && best > 0) detail += "\n" + getString(R.string.big_pile_record)
        endDetail.text = detail
        endPanel.visibility = View.VISIBLE
    }
}
