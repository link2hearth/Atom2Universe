package com.Atom2Universe.app.games.starbridges

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Constellations (ancien « Star Bridges ») : relier les étoiles par des traits qui ne se
 * croisent pas, chacune avec son nombre de liens, jusqu'à former une seule figure. Chaque
 * figure résolue reçoit un nom et rejoint l'atlas céleste du joueur.
 *
 * Toute configuration valide gagne, pas seulement celle du générateur : il n'y a donc pas
 * de résolveur, et le joueur ne peut pas se retrouver coincé par une seconde solution.
 */
class StarBridgesActivity : ThemedActivity(), StarBridgesBoardView.Listener {

    companion object {
        private const val PREFS = ConstellationAtlas.PREFS
        private const val KEY_SAVE = "save_json"
    }

    private lateinit var boardView: StarBridgesBoardView
    private lateinit var tvTime: TextView
    private lateinit var winOverlay: View
    private lateinit var tvWinName: TextView
    private lateinit var tvWinMsg: TextView

    private val game = StarBridgesGame()
    private val atlas by lazy { ConstellationAtlas(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var timerStartMs = 0L
    private var timerRunning = false

    private val sizeChips = mutableMapOf<Int, MaterialButton>()

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
            updateTime()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_star_bridges)
        enableImmersiveMode()

        boardView  = findViewById(R.id.sb_board)
        tvTime     = findViewById(R.id.sb_stat_time)
        winOverlay = findViewById(R.id.sb_win_overlay)
        tvWinName  = findViewById(R.id.sb_win_name)
        tvWinMsg   = findViewById(R.id.sb_win_message)

        boardView.listener = this

        findViewById<ImageButton>(R.id.sb_back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.sb_restart_button).setOnClickListener { restartPuzzle() }
        findViewById<ImageButton>(R.id.sb_atlas_button).setOnClickListener { openAtlas() }
        findViewById<MaterialButton>(R.id.sb_btn_atlas).setOnClickListener { openAtlas() }
        findViewById<MaterialButton>(R.id.sb_btn_again).setOnClickListener { startNewGame(game.size) }

        buildSizeChips()

        val restored = loadGame()
        if (!restored) game.newGame(StarBridgesGame.DEFAULT_SIZE)

        boardView.game = game
        boardView.refresh()
        updateTime()
        updateSizeChips()

        if (game.solved) {
            boardView.showSolved()
            showWinOverlay()
        } else startTimer()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.solved) startTimer()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun openAtlas() = startActivity(Intent(this, ConstellationAtlasActivity::class.java))

    // ── Tailles ───────────────────────────────────────────────────────────────────
    private fun buildSizeChips() {
        val container = findViewById<LinearLayout>(R.id.sb_size_row)
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        for (s in StarBridgesGame.ALLOWED_SIZES) {
            val chip = MaterialButton(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.starbridges_size, s)
                textSize = 12f
                setTextColor(0xFFE8E4FF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp6 / 2, 0, dp6 / 2, 0) }
                setOnClickListener { startNewGame(s) }
            }
            container.addView(chip)
            sizeChips[s] = chip
        }
    }

    private fun updateSizeChips() {
        val active   = android.content.res.ColorStateList.valueOf(Color.parseColor("#3A2F6E"))
        val inactive = android.content.res.ColorStateList.valueOf(Color.parseColor("#141433"))
        for ((s, chip) in sizeChips) chip.backgroundTintList = if (s == game.size) active else inactive
    }

    // ── Déroulé ───────────────────────────────────────────────────────────────────
    private fun restartPuzzle() {
        val elapsedBefore = game.elapsedSeconds
        stopTimer()
        winOverlay.visibility = View.GONE
        game.resetBridges()
        // Effacer les traits ne remet pas le chrono à zéro : c'est la même grille.
        game.elapsedSeconds = elapsedBefore
        boardView.refresh()
        updateTime()
        saveGame()
        startTimer()
    }

    private fun startNewGame(s: Int) {
        stopTimer()
        winOverlay.visibility = View.GONE
        game.newGame(s)
        boardView.game = game
        boardView.refresh()
        updateTime()
        updateSizeChips()
        saveGame()
        startTimer()
    }

    override fun onBoardChanged() {
        saveGame()
    }

    override fun onSolved() {
        stopTimer()
        updateTime()
        if (!game.rewardClaimed) {
            game.rewardClaimed = true
            if (game.seedWasRandom) NeutrinoRepository(this).addBalance(NeutrinoRewards.starBridges(game.size))
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit { putInt("solved", prefs.getInt("solved", 0) + 1) }
        }
        atlas.add(game, ConstellationNames.nounCount(this), ConstellationNames.adjectiveCount(this))
        saveGame()
    }

    override fun onIgnitionDone() = showWinOverlay()

    private fun showWinOverlay() {
        // Une grille gagnée avant l'atlas n'y est pas encore : elle y entre maintenant.
        val entry = atlas.find(game.seed)
            ?: atlas.add(game, ConstellationNames.nounCount(this), ConstellationNames.adjectiveCount(this))
        tvWinName.text = ConstellationNames.name(this, entry.noun, entry.adjective)
        tvWinMsg.text = getString(R.string.starbridges_win_line, formatTime(game.elapsedSeconds), game.moves)
        winOverlay.visibility = View.VISIBLE
    }

    // ── Chrono ────────────────────────────────────────────────────────────────────
    private fun startTimer() {
        if (timerRunning) return
        timerStartMs = System.currentTimeMillis() - game.elapsedSeconds * 1000
        timerRunning = true
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        if (timerRunning) game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    private fun updateTime() {
        tvTime.text = formatTime(game.elapsedSeconds)
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return getString(R.string.starbridges_time, (s / 60).toInt(), (s % 60).toInt())
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────────
    private fun saveGame() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SAVE, game.serialize()).apply()
    }

    private fun loadGame(): Boolean {
        val json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SAVE, null) ?: return false
        return game.deserialize(json)
    }
}
