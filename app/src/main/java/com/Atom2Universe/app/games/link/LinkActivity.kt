package com.Atom2Universe.app.games.link

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class LinkActivity : ThemedActivity(), LinkBoardView.Listener {

    private val game = LinkGame()

    private lateinit var boardView: LinkBoardView
    private lateinit var messageText: TextView
    private lateinit var movesValue: TextView
    private lateinit var btnUndo: Button
    private lateinit var btnRestart: Button
    private lateinit var btnNewLevel: Button
    private lateinit var modeSpinner: Spinner
    private lateinit var difficultySpinner: Spinner
    private lateinit var pairsSpinner: Spinner
    private lateinit var linkLengthSpinner: Spinner
    private lateinit var victoryOverlay: FrameLayout
    private lateinit var victoryMovesText: TextView
    private lateinit var perfectCounter: TextView

    private var ignoreSpinnerEvents = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link_game)
        enableImmersiveMode()

        boardView         = findViewById(R.id.link_board_view)
        messageText       = findViewById(R.id.link_message)
        movesValue        = findViewById(R.id.link_moves_value)
        btnUndo           = findViewById(R.id.link_btn_undo)
        btnRestart        = findViewById(R.id.link_btn_restart)
        btnNewLevel       = findViewById(R.id.link_btn_new_level)
        modeSpinner       = findViewById(R.id.link_mode_spinner)
        difficultySpinner = findViewById(R.id.link_difficulty_spinner)
        pairsSpinner      = findViewById(R.id.link_pairs_spinner)
        linkLengthSpinner = findViewById(R.id.link_length_spinner)
        victoryOverlay    = findViewById(R.id.link_victory_overlay)
        victoryMovesText  = findViewById(R.id.link_victory_moves)
        perfectCounter    = findViewById(R.id.link_perfect_counter)

        boardView.game     = game
        boardView.listener = this

        findViewById<Button>(R.id.link_victory_new_btn).setOnClickListener { newLevel() }

        setupModeSpinner()
        setupDifficultySpinner()
        setupPairsSpinner()
        setupLinkLengthSpinner()
        setupButtons()

        game.generateLevel()
        boardView.notifyBoardChanged()
        updateUI()
        showNewLevelMessage()
    }

    // --- Spinners ---

    private fun setupModeSpinner() {
        val labels = listOf(
            getString(R.string.link_mode_classic),
            getString(R.string.link_mode_perfect)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter
        modeSpinner.setSelection(0)
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerEvents) return
                val newMode = if (pos == 1) LinkGame.GameMode.PERFECT else LinkGame.GameMode.CLASSIC
                if (game.gameMode != newMode) { game.gameMode = newMode; newLevel() }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupDifficultySpinner() {
        val labels = listOf(
            getString(R.string.link_difficulty_easy),
            getString(R.string.link_difficulty_medium),
            getString(R.string.link_difficulty_hard)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(1)
        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerEvents) return
                val newDiff = when (pos) {
                    0    -> LinkGame.Difficulty.EASY
                    2    -> LinkGame.Difficulty.HARD
                    else -> LinkGame.Difficulty.MEDIUM
                }
                if (game.difficulty != newDiff) { game.difficulty = newDiff; newLevel() }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupPairsSpinner() {
        val labels = listOf(
            getString(R.string.link_pairs_few),
            getString(R.string.link_pairs_normal),
            getString(R.string.link_pairs_many)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        pairsSpinner.adapter = adapter
        pairsSpinner.setSelection(1)
        pairsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerEvents) return
                val lvl = when (pos) {
                    0    -> LinkGame.PairsLevel.FEW
                    2    -> LinkGame.PairsLevel.MANY
                    else -> LinkGame.PairsLevel.NORMAL
                }
                if (game.pairsLevel != lvl) { game.pairsLevel = lvl; newLevel() }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupLinkLengthSpinner() {
        val labels = LinkGame.LINK_LENGTHS.map { it.toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        linkLengthSpinner.adapter = adapter
        linkLengthSpinner.setSelection(1)
        linkLengthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerEvents) return
                val newLen = LinkGame.LINK_LENGTHS[pos]
                if (game.linkLength != newLen) { game.linkLength = newLen; newLevel() }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // --- Buttons ---

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.link_back_button).setOnClickListener { finish() }
        btnUndo.setOnClickListener { undoMove() }
        btnRestart.setOnClickListener { restartLevel() }
        btnNewLevel.setOnClickListener { newLevel() }
    }

    private fun undoMove() {
        val affected = game.undoLastMove()
        if (affected != null) {
            boardView.notifyBoardChanged()
            updateUI()
            setMessage(R.string.link_msg_undo)
        } else {
            setMessage(R.string.link_msg_nothing_to_undo)
        }
    }

    private fun restartLevel() {
        hideVictoryOverlay()
        game.restartLevel()
        boardView.notifyBoardChanged()
        updateUI()
        setMessage(R.string.link_msg_restart)
    }

    private fun newLevel() {
        hideVictoryOverlay()
        game.generateLevel()
        boardView.notifyBoardChanged()
        updateUI()
        showNewLevelMessage()
    }

    // --- LinkBoardView.Listener ---

    override fun onMoveApplied(affectedCoords: List<LinkGame.Coord>) {
        updateUI()
        val (normal, plus) = game.countRemaining()
        messageText.text = getString(R.string.link_msg_progress, normal, plus)
    }

    override fun onMoveFailed() {
        setMessage(R.string.link_msg_invalid_pattern)
    }

    override fun onVictory() {
        updateUI()
        showVictoryOverlay()
    }

    // --- Victory overlay ---

    private fun showVictoryOverlay() {
        victoryMovesText.text = getString(R.string.link_victory_moves, game.moves)
        victoryOverlay.visibility = View.VISIBLE
        victoryOverlay.alpha = 0f
        victoryOverlay.animate().alpha(1f).setDuration(400).start()
    }

    private fun hideVictoryOverlay() {
        victoryOverlay.visibility = View.GONE
        victoryOverlay.alpha = 0f
    }

    // --- UI helpers ---

    private fun updateUI() {
        movesValue.text = game.moves.toString()
        val inGame = !game.isVictory
        btnUndo.isEnabled = game.canUndo && inGame
        btnRestart.isEnabled = inGame
        updatePerfectCounter()
    }

    private fun updatePerfectCounter() {
        if (game.gameMode == LinkGame.GameMode.PERFECT && game.solutionMoveCount > 0) {
            perfectCounter.text = "${game.moves} / ${game.solutionMoveCount}"
            perfectCounter.visibility = View.VISIBLE
        } else {
            perfectCounter.visibility = View.GONE
        }
    }

    private fun setMessage(resId: Int) {
        messageText.text = getString(resId)
    }

    private fun showNewLevelMessage() {
        val diff = difficultyLabel()
        messageText.text = if (game.gameMode == LinkGame.GameMode.PERFECT) {
            getString(R.string.link_msg_new_level_perfect, diff, game.solutionMoveCount)
        } else {
            getString(R.string.link_msg_new_level, diff, game.linkLength)
        }
    }

    private fun difficultyLabel(): String = when (game.difficulty) {
        LinkGame.Difficulty.EASY   -> getString(R.string.link_difficulty_easy)
        LinkGame.Difficulty.MEDIUM -> getString(R.string.link_difficulty_medium)
        LinkGame.Difficulty.HARD   -> getString(R.string.link_difficulty_hard)
    }
}
