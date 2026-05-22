package com.Atom2Universe.app.games.othello

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import org.json.JSONArray
import org.json.JSONObject

class OthelloActivity : ThemedActivity(), OthelloBoardView.OnCellClickListener {

    private val game = OthelloGame()
    private lateinit var boardView: OthelloBoardView
    private lateinit var scoreText: TextView
    private lateinit var statusText: TextView
    private lateinit var modeButton: Button
    private lateinit var newGameButton: Button

    private var isSoloMode = true
    private val aiHandler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null

    companion object {
        private const val AI_DELAY_MS = 500L
        private const val PREFS = "othello_prefs"
        private const val KEY_STATE = "state"
        private const val KEY_SOLO = "solo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_othello)
        enableImmersiveMode()

        boardView = findViewById(R.id.othello_board)
        scoreText = findViewById(R.id.othello_score)
        statusText = findViewById(R.id.othello_status)
        modeButton = findViewById(R.id.othello_mode_button)
        newGameButton = findViewById(R.id.othello_new_button)

        boardView.cellClickListener = this
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        modeButton.setOnClickListener { toggleMode() }
        newGameButton.setOnClickListener { startNewGame() }

        loadSave()
    }

    private fun toggleMode() {
        isSoloMode = !isSoloMode
        updateModeButton()
        startNewGame()
    }

    private fun updateModeButton() {
        modeButton.text = if (isSoloMode)
            getString(R.string.othello_mode_solo)
        else
            getString(R.string.othello_mode_duo)
    }

    private fun startNewGame() {
        cancelAI()
        game.resetBoard()
        boardView.game = game
        boardView.showValidMoves = true
        boardView.invalidate()
        updateScore()
        beginTurn()
        saveCurrent()
    }

    override fun onCellClick(row: Int, col: Int) {
        if (game.gameOver) return
        if (isSoloMode && game.currentPlayer == OthelloGame.BLACK) return
        val placed = game.placeDisc(row, col)
        if (!placed) {
            statusText.text = getString(R.string.othello_status_invalid)
            return
        }
        boardView.invalidate()
        updateScore()
        if (game.gameOver) { showGameOver(); saveCurrent(); return }
        beginTurn()
        saveCurrent()
    }

    private fun beginTurn() {
        cancelAI()
        if (isSoloMode && game.currentPlayer == OthelloGame.BLACK && !game.gameOver) {
            statusText.text = getString(R.string.othello_status_ai_thinking)
            boardView.showValidMoves = false
            boardView.invalidate()
            val r = Runnable {
                aiRunnable = null
                if (game.gameOver) return@Runnable
                val move = game.chooseAIMove()
                if (move != null) {
                    game.placeDisc(move.first, move.second)
                }
                boardView.showValidMoves = true
                boardView.game = game
                boardView.invalidate()
                updateScore()
                if (game.gameOver) {
                    showGameOver()
                } else {
                    statusText.text = getTurnText()
                    // If AI has to play again (human passed), recurse
                    if (isSoloMode && game.currentPlayer == OthelloGame.BLACK) beginTurn()
                }
                saveCurrent()
            }
            aiRunnable = r
            aiHandler.postDelayed(r, AI_DELAY_MS)
        } else {
            boardView.showValidMoves = true
            boardView.invalidate()
            statusText.text = getTurnText()
        }
    }

    private fun getTurnText(): String = when {
        isSoloMode && game.currentPlayer == OthelloGame.WHITE -> getString(R.string.othello_status_your_turn)
        game.currentPlayer == OthelloGame.BLACK -> getString(R.string.othello_status_black_turn)
        else -> getString(R.string.othello_status_white_turn)
    }

    private fun showGameOver() {
        cancelAI()
        val (black, white) = game.getScore()
        val result = when (game.getWinner()) {
            OthelloGame.BLACK -> getString(R.string.othello_result_black)
            OthelloGame.WHITE -> getString(R.string.othello_result_white)
            else -> getString(R.string.othello_result_draw)
        }
        statusText.text = getString(R.string.othello_status_game_over, black, white, result)
    }

    private fun updateScore() {
        val (black, white) = game.getScore()
        scoreText.text = getString(R.string.othello_score, black, white)
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
        boardView.game = game
        boardView.invalidate()
        updateScore()
        if (game.gameOver) showGameOver() else beginTurn()
    }

    private fun saveCurrent() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_STATE, mapToJson(game.serialize()).toString())
                .putBoolean(KEY_SOLO, isSoloMode)
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
