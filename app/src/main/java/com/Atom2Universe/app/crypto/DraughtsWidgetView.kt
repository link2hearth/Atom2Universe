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
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.draughts.DraughtsActivity
import com.Atom2Universe.app.games.draughts.DraughtsActivity.Companion.putGame
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
    // Difficulté la plus basse jouée pendant la partie (partagée avec DraughtsActivity) : c'est elle qui paie
    private var rewardDifficulty = DraughtsDifficulty.STANDARD
    private var aiThinking = false

    private val aiScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Zoom ──────────────────────────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    // La taille affichée fait foi : elle peut être restaurée de l'extérieur.
    private var currentScale: Float
        get() = scaleX
        set(value) { scaleX = value; scaleY = value }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next; scaleX = currentScale; scaleY = currentScale
                }
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) { keepFloatingWidgetOnScreen() }
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

        resultOverlay.setOnClickListener { resultOverlay.visibility = GONE }

        findViewById<TextView>(R.id.draughts_btn_reset).setOnClickListener {
            difficultyOverlay.visibility = VISIBLE
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
            difficultyOverlay.visibility = GONE
        }

        reload()

        val headerArea = findViewById<FrameLayout>(R.id.draughts_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
        keepFloatingWidgetOnScreenOnResize()
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
                keepFloatingWidgetOnScreen()
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false; skipNextDragFrame = false
                keepFloatingWidgetOnScreen()
            }
        }
        return true
    }

    // ── Nouvelle partie ───────────────────────────────────────────────────────
    private fun startNewGame(newDifficulty: DraughtsDifficulty) {
        difficultyOverlay.visibility = GONE
        resultOverlay.visibility = GONE
        ai?.cancel(); ai = null
        mainHandler.removeCallbacksAndMessages(null)
        difficulty = newDifficulty
        aiThinking = false

        game.newGame()
        rewardDifficulty = difficulty
        ai = if (difficulty.hasAI()) DraughtsAI(difficulty) else null
        boardView.isTwoPlayerMode = (difficulty == DraughtsDifficulty.TWO_PLAYER)
        boardView.selectedPos = null
        boardView.highlightedTargets = emptyList()
        boardView.capturableInSelected = emptyList()
        boardView.mustCapturePositions = emptySet()
        boardView.clearLastMove()
        boardView.refresh()
        updatePieceCount()

        // Effacer la partie précédente (chrono de l'activité compris)
        clearSave()
    }

    private fun prefs() = context.getSharedPreferences(DraughtsActivity.PREFS, Context.MODE_PRIVATE)

    /** Partie finie ou abandonnée : seule la difficulté reste. */
    private fun clearSave() {
        prefs().edit().clear().putString(DraughtsActivity.KEY_DIFFICULTY, difficulty.name).apply()
    }

    // ── Chargement de l'état sauvegardé ──────────────────────────────────────
    fun reload() {
        ai?.cancel(); ai = null; aiThinking = false
        mainHandler.removeCallbacksAndMessages(null)

        val prefs = prefs()
        val savedDiff = prefs.getString(DraughtsActivity.KEY_DIFFICULTY, null)
        difficulty = if (savedDiff != null) {
            runCatching { DraughtsDifficulty.valueOf(savedDiff) }.getOrDefault(DraughtsDifficulty.STANDARD)
        } else DraughtsDifficulty.STANDARD

        // Comme l'activité ; une partie finie (ici ou dans l'activité) n'est pas gardée jouable
        rewardDifficulty = runCatching { DraughtsActivity.restoreGame(prefs, game, difficulty) }
            .getOrElse { game.newGame(); difficulty }
        if (game.isGameOver) { game.newGame(); rewardDifficulty = difficulty }
        boardView.clearLastMove()

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
        val editor = prefs().edit()
        editor.putGame(game, rewardDifficulty)
        editor.apply()
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
            // Coup calculé pour une autre position (nouvelle partie, rechargement) : ignoré
            if (ai == null || game.currentTurn != DraughtsPieceColor.BLACK ||
                game.getLegalMovesFrom(move.from).none { it.to == move.to && it.captures == move.captures }
            ) return@postDelayed
            applyMove(move)
        }, 250)
    }

    override fun onAIError(messageRes: Int) { aiThinking = false }

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
        resultOverlay.visibility = VISIBLE
        // Une partie finie ne revient jamais comme partie vivante (ni ici, ni dans l'activité)
        clearSave()

        if (playerWon) {
            val reward = NeutrinoRewards.draughts(rewardDifficulty)
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
