package com.Atom2Universe.app.games.enamel

import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.bigger.BiggerSoundEngine
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder

class EnamelActivity : ThemedActivity() {
    companion object { const val PREFS = "enamel_save" }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val colors by lazy { SciencePalette(this) }
    private val sound = BiggerSoundEngine()
    private lateinit var board: EnamelView
    private lateinit var score: TextView
    private var restartDialog: AlertDialog? = null
    private var readingRules = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dp = resources.displayMetrics.density
        fun px(n: Int) = (n * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = px(56) }
        fun button(icon: Int, title: Int, action: () -> Unit) = ImageButton(this).apply {
            setImageResource(icon); setColorFilter(colors.text); background = null
            contentDescription = getString(title); setPadding(px(12), px(12), px(12), px(12))
            setOnClickListener { action() }
        }
        bar.addView(button(R.drawable.ic_arrow_back, R.string.back) { finish() }, LinearLayout.LayoutParams(px(48), px(48)))
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@EnamelActivity).apply {
                setText(R.string.enamel_title); setTextColor(colors.text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textSize = 23f
            })
        }
        score = TextView(this).apply { setTextColor(colors.ink(colors.accent, colors.background)); textSize = 14f }
        title.addView(score)
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(button(android.R.drawable.ic_menu_rotate, R.string.enamel_restart) { askRestart() }, LinearLayout.LayoutParams(px(48), px(48)))
        bar.addView(button(R.drawable.ic_info, R.string.kit_rules) {
            readingRules = true
            board.pause()
            showGameRules(R.string.enamel_title, getString(R.string.enamel_rules, NeutrinoRewards.ENAMEL_POINTS_PER_NEUTRINO)) {
                readingRules = false
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) board.resume()
            }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        board = EnamelView(this).apply {
            sound = this@EnamelActivity.sound
            bestScore = prefs.getInt("best_score", 0)
            discoveredTier = prefs.getInt("discovered_tier", -1)
            prefs.getString("run_state", null)?.let { game.restoreState(it) }
            onStateChanged = { checkpoint() }
            onGameOver = { finishRun(); updateHud() }
        }
        root.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        updateHud()
    }

    private fun updateHud() {
        board.bestScore = maxOf(board.bestScore, board.game.score)
        score.text = getString(R.string.enamel_hud, board.game.score, board.bestScore)
    }

    private fun checkpoint() {
        val state = board.game.saveState()
        prefs.edit().apply {
            if (state == null) remove("run_state") else putString("run_state", state)
            putInt("best_score", maxOf(prefs.getInt("best_score", 0), board.game.score))
            putInt("discovered_tier", maxOf(prefs.getInt("discovered_tier", -1), board.discoveredTier))
        }.apply()
        updateHud()
    }

    private fun finishRun() {
        // Retire la partie avant le versement : revenir après une sortie ne la repaie pas.
        prefs.edit().remove("run_state")
            .putInt("best_score", maxOf(prefs.getInt("best_score", 0), board.game.score))
            .putInt("discovered_tier", maxOf(prefs.getInt("discovered_tier", -1), board.discoveredTier)).commit()
        val reward = NeutrinoRewards.enamel(board.game.score)
        if (reward > 0) NeutrinoRepository(this).addBalance(reward)
    }

    private fun askRestart() {
        if (board.game.shots == 0 || board.game.over) { board.reset(); return }
        board.pause()
        restartDialog = ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.enamel_restart)
            .setMessage(R.string.enamel_restart_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.enamel_restart) { _, _ -> finishRun(); board.reset() }
            .create().also { dialog ->
                dialog.setOnDismissListener {
                    restartDialog = null
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) board.resume()
                }
                dialog.show()
            }
    }

    override fun onResume() {
        super.onResume(); sound.start()
        if (restartDialog?.isShowing != true && !readingRules) board.resume()
    }

    override fun onPause() {
        board.pause(); checkpoint(); sound.stop()
        super.onPause()
    }
}
