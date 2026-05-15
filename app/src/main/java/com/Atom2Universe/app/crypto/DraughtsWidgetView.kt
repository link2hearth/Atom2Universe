package com.Atom2Universe.app.crypto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.draughts.DraughtsDifficulty
import com.Atom2Universe.app.games.draughts.DraughtsGame
import com.Atom2Universe.app.games.draughts.DraughtsMove
import com.Atom2Universe.app.games.draughts.DraughtsPieceColor
import com.Atom2Universe.app.games.draughts.DraughtsPos
import com.Atom2Universe.app.games.draughts.DraughtsView
import com.Atom2Universe.app.games.draughts.ai.DraughtsAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.math.hypot

/**
 * Widget dames interactif pour la page clicker.
 * Gestes : glisser le header → déplacer, pincer → zoom, tap plateau → jouer, tap header → ouvre DraughtsActivity.
 */
class DraughtsWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    DraughtsView.DraughtsViewListener,
    DraughtsAI.AIListener {

    private val boardView: DraughtsView
    private lateinit var pieceCountText: TextView
    private lateinit var difficultyOverlay: FrameLayout
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView

    private val game = DraughtsGame()
    private var ai: DraughtsAI? = null
    private var difficulty = DraughtsDifficulty.STANDARD
    private var aiThinking = false

    private val aiScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Zoom ──────────────────────────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next; scaleX = currentScale; scaleY = currentScale
                }
                return true
            }
        })

    // ── Drag depuis le header ─────────────────────────────────────────────────
    private var dragLastX = 0f; private var dragLastY = 0f
    private var dragDownX = 0f; private var dragDownY = 0f
    private var isDragging = false; private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_draughts_widget, this, true)

        boardView = findViewById(R.id.draughts_widget_board)
        boardView.listener = this
        pieceCountText = findViewById(R.id.draughts_piece_count)
        difficultyOverlay = findViewById(R.id.draughts_difficulty_overlay)
        resultOverlay = findViewById(R.id.draughts_result_overlay)
        resultText = findViewById(R.id.draughts_result_text)

        resultOverlay.setOnClickListener { resultOverlay.visibility = View.GONE }

        findViewById<TextView>(R.id.draughts_btn_reset).setOnClickListener {
            difficultyOverlay.visibility = View.VISIBLE
        }
        findViewById<TextView>(R.id.draughts_diff_training).setOnClickListener {
            startNewGame(DraughtsDifficulty.TRAINING)
        }
        findViewById<TextView>(R.id.draughts_diff_standard).setOnClickListener {
            startNewGame(DraughtsDifficulty.STANDARD)
        }
        findViewById<TextView>(R.id.draughts_diff_expert).setOnClickListener {
            startNewGame(DraughtsDifficulty.EXPERT)
        }
        findViewById<TextView>(R.id.draughts_diff_two_player).setOnClickListener {
            startNewGame(DraughtsDifficulty.TWO_PLAYER)
        }
        findViewById<TextView>(R.id.draughts_diff_cancel).setOnClickListener {
            difficultyOverlay.visibility = View.GONE
        }

        reload()

        val headerArea = findViewById<FrameLayout>(R.id.draughts_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
    }

    // ── Pinch-zoom ────────────────────────────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) { scaleDetector.onTouchEvent(ev); return true }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev); return true
    }

    // ── Drag depuis le header ─────────────────────────────────────────────────
    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX; dragDownY = event.rawY
                dragLastX = event.rawX; dragLastY = event.rawY
                isDragging = false; skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_UP -> skipNextDragFrame = true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        dragLastX = event.rawX; dragLastY = event.rawY
                        dragDownX = event.rawX; dragDownY = event.rawY
                        isDragging = false; skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - dragLastX
                            translationY += event.rawY - dragLastY
                        }
                        dragLastX = event.rawX; dragLastY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val intent = Intent(context, com.Atom2Universe.app.games.draughts.DraughtsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    context.startActivity(intent)
                }
                isDragging = false; skipNextDragFrame = false
            }
            MotionEvent.ACTION_CANCEL -> { isDragging = false; skipNextDragFrame = false }
        }
        return true
    }

    // ── Nouvelle partie ───────────────────────────────────────────────────────
    private fun startNewGame(newDifficulty: DraughtsDifficulty) {
        difficultyOverlay.visibility = View.GONE
        resultOverlay.visibility = View.GONE
        ai?.cancel(); ai = null
        difficulty = newDifficulty
        aiThinking = false

        game.newGame()
        ai = if (difficulty.hasAI()) DraughtsAI(difficulty) else null
        boardView.isTwoPlayerMode = (difficulty == DraughtsDifficulty.TWO_PLAYER)
        boardView.selectedPos = null
        boardView.highlightedTargets = emptyList()
        boardView.capturableInSelected = emptyList()
        boardView.mustCapturePositions = emptySet()
        boardView.clearLastMove()
        boardView.refresh()
        updatePieceCount()

        context.getSharedPreferences("draughts_save", Context.MODE_PRIVATE).edit()
            .putString("difficulty", difficulty.name)
            .remove("state")
            .apply()
    }

    // ── Chargement de l'état sauvegardé ──────────────────────────────────────
    fun reload() {
        ai?.cancel(); ai = null; aiThinking = false

        val prefs = context.getSharedPreferences("draughts_save", Context.MODE_PRIVATE)
        val savedDiff = prefs.getString("difficulty", null)
        difficulty = if (savedDiff != null) {
            runCatching { DraughtsDifficulty.valueOf(savedDiff) }.getOrDefault(DraughtsDifficulty.STANDARD)
        } else DraughtsDifficulty.STANDARD

        val state = prefs.getString("state", null)
        if (state != null) runCatching { game.deserialize(state) }

        ai = if (difficulty.hasAI()) DraughtsAI(difficulty) else null
        boardView.isTwoPlayerMode = (difficulty == DraughtsDifficulty.TWO_PLAYER)
        boardView.game = game
        boardView.selectedPos = null
        boardView.highlightedTargets = emptyList()
        boardView.capturableInSelected = emptyList()
        boardView.mustCapturePositions = if (game.hasMandatoryCaptures())
            game.mandatoryPiecePositions() else emptySet()
        boardView.refresh()
        updatePieceCount()

        if (!game.isGameOver && game.currentTurn == DraughtsPieceColor.BLACK && ai != null) {
            triggerAI()
        }
    }

    // ── DraughtsView.DraughtsViewListener ────────────────────────────────────
    override fun onSquareTapped(pos: DraughtsPos) {
        if (game.isGameOver || aiThinking) return
        if (game.currentTurn == DraughtsPieceColor.BLACK && ai != null) return

        val piece = game.getPieceAt(pos)
        val currentSelected = boardView.selectedPos

        if (piece != null && piece.color == game.currentTurn) {
            val movesFrom = game.getLegalMovesFrom(pos)
            if (movesFrom.isNotEmpty()) {
                boardView.selectedPos = pos
                boardView.highlightedTargets = movesFrom.map { it.to }.distinct()
                boardView.capturableInSelected = movesFrom.flatMap { it.captures }.distinct()
                boardView.refresh()
            } else {
                boardView.selectedPos = null
                boardView.highlightedTargets = emptyList()
                boardView.capturableInSelected = emptyList()
                boardView.refresh()
            }
        } else if (currentSelected != null) {
            val move = game.getLegalMovesFrom(currentSelected).find { it.to == pos }
            if (move != null) {
                clearSelection()
                applyMove(move)
            } else {
                clearSelection()
            }
        }
    }

    private fun clearSelection() {
        boardView.selectedPos = null
        boardView.highlightedTargets = emptyList()
        boardView.capturableInSelected = emptyList()
        boardView.refresh()
    }

    private fun applyMove(move: DraughtsMove) {
        game.makeMove(move)
        boardView.setLastMove(move.from, move.to)
        boardView.mustCapturePositions = if (game.hasMandatoryCaptures())
            game.mandatoryPiecePositions() else emptySet()
        boardView.refresh()
        updatePieceCount()
        saveState()
        checkGameOver()
        if (!game.isGameOver) triggerAI()
    }

    private fun saveState() {
        context.getSharedPreferences("draughts_save", Context.MODE_PRIVATE).edit()
            .putString("state", game.serialize())
            .apply()
    }

    private fun triggerAI() {
        val currentAI = ai ?: return
        if (game.isGameOver) return
        if (game.currentTurn != DraughtsPieceColor.BLACK) return
        aiThinking = true
        currentAI.findMove(game, this, aiScope)
    }

    // ── DraughtsAI.AIListener ─────────────────────────────────────────────────
    override fun onAIThinking() { /* pas de feedback dans le widget */ }

    override fun onAIMoveFound(move: DraughtsMove, thinkTimeMs: Long) {
        mainHandler.postDelayed({
            if (game.isGameOver) { aiThinking = false; return@postDelayed }
            aiThinking = false
            applyMove(move)
        }, 250)
    }

    override fun onAIError(error: String) { aiThinking = false }

    // ── Fin de partie ─────────────────────────────────────────────────────────
    private fun checkGameOver() {
        if (!game.isGameOver) return
        val playerWon = difficulty != DraughtsDifficulty.TWO_PLAYER
            && game.winner == DraughtsPieceColor.WHITE

        val (label, color) = when {
            game.winner == null -> context.getString(R.string.draughts_widget_draw) to Color.parseColor("#94A3B8")
            difficulty == DraughtsDifficulty.TWO_PLAYER -> {
                val side = if (game.winner == DraughtsPieceColor.WHITE)
                    context.getString(R.string.draughts_widget_white_wins)
                else
                    context.getString(R.string.draughts_widget_black_wins)
                side to Color.parseColor("#EAB308")
            }
            game.winner == DraughtsPieceColor.WHITE -> context.getString(R.string.draughts_widget_victory) to Color.parseColor("#22C55E")
            else -> context.getString(R.string.draughts_widget_defeat) to Color.parseColor("#EF4444")
        }
        resultText.text = label
        resultText.setTextColor(color)
        resultOverlay.visibility = View.VISIBLE

        if (playerWon) {
            val reward = when (difficulty) {
                DraughtsDifficulty.TRAINING -> 10
                DraughtsDifficulty.STANDARD -> 20
                DraughtsDifficulty.EXPERT   -> 50
                else -> 0
            }
            if (reward > 0) NeutrinoRepository(context).addBalance(reward)
        }
    }

    // ── Affichage du nombre de pièces ─────────────────────────────────────────
    private fun updatePieceCount() {
        val w = game.countPieces(DraughtsPieceColor.WHITE)
        val b = game.countPieces(DraughtsPieceColor.BLACK)
        pieceCountText.text = context.getString(R.string.draughts_widget_piece_count, w, b)
    }

    override fun onDetachedFromWindow() {
        ai?.cancel()
        aiScope.coroutineContext[Job]?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
