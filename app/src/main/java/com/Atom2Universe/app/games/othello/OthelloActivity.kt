package com.Atom2Universe.app.games.othello

import com.Atom2Universe.app.util.Landscape
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import org.json.JSONArray
import org.json.JSONObject

class OthelloActivity : ThemedActivity(), OthelloBoardView.OnCellClickListener {

    private val game = OthelloGame()
    private lateinit var boardView: OthelloBoardView
    private lateinit var scoreView: OthelloScoreView
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var modeButton: ImageButton

    private var isSoloMode = true
    private val aiHandler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null

    companion object {
        private const val AI_DELAY_MS = 500L
        /** L'IA attend que les pions du coup précédent aient fini de se retourner. */
        private const val AI_AFTER_FLIPS_MS = 180L
        private const val PREFS = "othello_prefs"
        private const val KEY_STATE = "state"
        private const val KEY_SOLO = "solo"
        private const val KEY_LAST = "last"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_othello)
        Landscape.bindSidePanel(this)
        enableImmersiveMode()

        boardView = findViewById(R.id.othello_board)
        scoreView = findViewById(R.id.othello_score)
        statusText = findViewById(R.id.othello_status)
        titleText = findViewById(R.id.othello_title_text)
        modeButton = findViewById(R.id.othello_mode_button)

        boardView.cellClickListener = this
        boardView.onCountsShown = { black, white -> scoreView.setCounts(black, white) }
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.othello_info_button).setOnClickListener {
            showGameRules(R.string.othello_title, R.string.othello_rules)
        }
        modeButton.setOnClickListener { toggleMode() }
        findViewById<View>(R.id.othello_new_button).setOnClickListener { startNewGame() }

        arrange()
        loadSave()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Landscape.swapLayout(this, R.layout.activity_othello)
        arrange()
    }

    /**
     * Ce que la mise en page ne dit pas : couché, les plaquettes du score s'empilent et le message
     * passe à la ligne sous elles ; au-delà de 600 dp, les textes grandissent.
     */
    private fun arrange() {
        val land = Landscape.isLandscape(this)
        scoreView.vertical = land
        statusText.maxWidth = if (land) scoreView.chipWidth else Int.MAX_VALUE
        statusText.maxLines = if (land) 3 else 2
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            titleText.textSize = 22f
            statusText.textSize = 19f
        }
        showStatus(statusText.text)
    }

    /** Le message du moment (un joueur passe, la fin de partie) ; couché, la ligne vide disparaît. */
    private fun showStatus(text: CharSequence) {
        statusText.text = text
        statusText.visibility =
            if (text.isEmpty() && Landscape.isLandscape(this)) View.GONE else View.VISIBLE
    }

    private fun toggleMode() {
        isSoloMode = !isSoloMode
        updateModeButton()
        startNewGame()
    }

    private fun updateModeButton() {
        modeButton.setImageResource(if (isSoloMode) R.drawable.othello_ic_vs_ai else R.drawable.othello_ic_duo)
        modeButton.contentDescription =
            getString(if (isSoloMode) R.string.othello_mode_solo else R.string.othello_mode_duo)
        scoreView.solo = isSoloMode
    }

    private fun startNewGame() {
        cancelAI()
        game.resetBoard()
        boardView.lastMove = -1
        boardView.bind(game)
        scoreView.winner = null
        showStatus("")
        beginTurn()
        saveCurrent()
    }

    override fun onCellClick(row: Int, col: Int) {
        if (game.gameOver) return
        if (isSoloMode && game.currentPlayer == OthelloGame.BLACK) return
        val mover = game.currentPlayer
        // Une case injouable ne fait rien.
        if (!game.placeDisc(row, col)) return
        afterMove(mover)
    }

    /** Après le coup de [mover] : animation, fin de partie ou tour suivant (qui peut lui revenir). */
    private fun afterMove(mover: Int) {
        boardView.sync(animate = true)
        if (game.gameOver) {
            showGameOver(awardReward = true)
            saveCurrent()
            return
        }
        showStatus(if (game.currentPlayer == mover) passText(-mover) else "")
        beginTurn()
        saveCurrent()
    }

    private fun passText(player: Int): String = getString(when {
        isSoloMode && player == OthelloGame.WHITE -> R.string.othello_pass_you
        isSoloMode -> R.string.othello_pass_ai
        player == OthelloGame.BLACK -> R.string.othello_pass_black
        else -> R.string.othello_pass_white
    })

    private fun beginTurn() {
        cancelAI()
        scoreView.current = game.currentPlayer
        if (isSoloMode && game.currentPlayer == OthelloGame.BLACK && !game.gameOver) {
            boardView.showValidMoves = false
            scoreView.thinking = true
            val r = Runnable {
                aiRunnable = null
                scoreView.thinking = false
                if (game.gameOver) return@Runnable
                val move = game.chooseAIMove()
                if (move == null) {
                    boardView.showValidMoves = true
                    return@Runnable
                }
                val mover = game.currentPlayer
                game.placeDisc(move.first, move.second)
                afterMove(mover)
            }
            aiRunnable = r
            aiHandler.postDelayed(r, maxOf(AI_DELAY_MS, boardView.animationRemainingMs() + AI_AFTER_FLIPS_MS))
        } else {
            scoreView.thinking = false
            boardView.showValidMoves = true
        }
    }

    private fun showGameOver(awardReward: Boolean = false) {
        cancelAI()
        scoreView.thinking = false
        boardView.showValidMoves = false
        val winner = game.getWinner()
        // 15 neutrinos en solo quand le joueur humain (blanc) gagne contre l'IA
        if (awardReward && isSoloMode && winner == OthelloGame.WHITE) {
            NeutrinoRepository(this).addBalance(NeutrinoRewards.OTHELLO_WIN)
        }
        if (awardReward && isSoloMode) {
            val statsRepo = GameStatsRepository(this)
            statsRepo.recordOthelloPlayed()
            if (winner == OthelloGame.WHITE) statsRepo.recordOthelloWon()
        }
        scoreView.winner = winner
        showStatus(getString(when {
            winner == OthelloGame.EMPTY -> R.string.othello_result_draw
            isSoloMode && winner == OthelloGame.WHITE -> R.string.othello_result_you_win
            isSoloMode -> R.string.othello_result_ai_wins
            winner == OthelloGame.BLACK -> R.string.othello_result_black
            else -> R.string.othello_result_white
        }))
    }

    private fun cancelAI() {
        aiRunnable?.let { aiHandler.removeCallbacks(it) }
        aiRunnable = null
    }

    // === Sauvegarde ===

    private fun loadSave() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        isSoloMode = prefs.getBoolean(KEY_SOLO, true)
        updateModeButton()
        val json = prefs.getString(KEY_STATE, null)
        var restored = false
        if (json != null) {
            try { restored = game.deserialize(jsonToMap(JSONObject(json))) } catch (e: Exception) { /* ignore */ }
        }
        if (!restored) game.resetBoard()
        boardView.bind(game)
        val last = prefs.getInt(KEY_LAST, -1)
        boardView.lastMove = if (restored && last in 0 until OthelloGame.BOARD_SIZE * OthelloGame.BOARD_SIZE) last else -1
        showStatus("")
        if (game.gameOver) showGameOver() else {
            scoreView.winner = null
            beginTurn()
        }
    }

    private fun saveCurrent() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_STATE, mapToJson(game.serialize()).toString())
                .putBoolean(KEY_SOLO, isSoloMode)
                .putInt(KEY_LAST, boardView.lastMove)
                .apply()
        } catch (e: Exception) { /* ignore */ }
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) when (v) {
            is List<*> -> { val arr = JSONArray(); v.forEach { arr.put(it) }; obj.put(k, arr) }
            else -> obj.put(k, v)
        }
        return obj
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (k in obj.keys()) map[k] = when (val v = obj.get(k)) {
            is JSONArray -> (0 until v.length()).map { v.getInt(it) }
            else -> v
        }
        return map
    }

    override fun onPause() {
        super.onPause()
        saveCurrent()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAI()
    }
}
