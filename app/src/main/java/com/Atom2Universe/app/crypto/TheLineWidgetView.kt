package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.theline.TheLineBoardView
import com.Atom2Universe.app.games.theline.TheLineDifficulty
import com.Atom2Universe.app.games.theline.TheLineGame
import com.Atom2Universe.app.games.theline.TheLineGenerator
import com.Atom2Universe.app.games.theline.TheLineMode
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import kotlin.math.hypot

class TheLineWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val PREFS_NAME = "the_line_widget_save"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_MODE = "mode"

        private val DIFFICULTY_LABELS = listOf("Facile", "Moyen", "Difficile")
        private val DIFFICULTY_COLORS = listOf("#4AB3FF", "#F6B93B", "#FF6B6B")
        private val DIFFICULTIES = listOf(
            TheLineDifficulty.EASY, TheLineDifficulty.MEDIUM, TheLineDifficulty.HARD
        )

        private const val COLOR_MODE_ACTIVE = "#7AD3FF"
        private const val COLOR_MODE_INACTIVE = "#64748B"
    }

    private lateinit var cardView: MaterialCardView
    private lateinit var boardView: TheLineBoardView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resetOverlay: FrameLayout
    private lateinit var difficultyLabel: TextView
    private lateinit var loadingView: ProgressBar
    private lateinit var btnModeSingle: TextView
    private lateinit var btnModeMulti: TextView

    private val game = TheLineGame()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val baseCardColor = Color.parseColor("#0F172A")

    // Mode sélectionné dans l'overlay (pas encore appliqué)
    private var pendingMode = TheLineMode.SINGLE

    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next
                    scaleX = currentScale
                    scaleY = currentScale
                }
                return true
            }
        })

    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_the_line_widget, this, true)

        cardView        = findViewById(R.id.the_line_widget_card)
        boardView       = findViewById(R.id.the_line_widget_board)
        resultOverlay   = findViewById(R.id.the_line_widget_result_overlay)
        resetOverlay    = findViewById(R.id.the_line_widget_reset_overlay)
        difficultyLabel = findViewById(R.id.the_line_widget_difficulty)
        loadingView     = findViewById(R.id.the_line_widget_loading)
        btnModeSingle   = findViewById(R.id.the_line_widget_mode_single)
        btnModeMulti    = findViewById(R.id.the_line_widget_mode_multi)

        resultOverlay.setOnClickListener { /* absorb */ }
        findViewById<TextView>(R.id.the_line_widget_btn_new_game).setOnClickListener {
            resultOverlay.visibility = GONE
            openResetOverlay()
        }
        findViewById<TextView>(R.id.the_line_widget_btn_reset).setOnClickListener {
            openResetOverlay()
        }

        btnModeSingle.setOnClickListener { setPendingMode(TheLineMode.SINGLE) }
        btnModeMulti.setOnClickListener  { setPendingMode(TheLineMode.MULTI) }

        findViewById<TextView>(R.id.the_line_widget_diff_easy).setOnClickListener {
            resetOverlay.visibility = GONE
            startNewGame(TheLineDifficulty.EASY)
        }
        findViewById<TextView>(R.id.the_line_widget_diff_medium).setOnClickListener {
            resetOverlay.visibility = GONE
            startNewGame(TheLineDifficulty.MEDIUM)
        }
        findViewById<TextView>(R.id.the_line_widget_diff_hard).setOnClickListener {
            resetOverlay.visibility = GONE
            startNewGame(TheLineDifficulty.HARD)
        }
        findViewById<TextView>(R.id.the_line_widget_reset_cancel).setOnClickListener {
            resetOverlay.visibility = GONE
        }

        boardView.onBoardChanged = {
            if (game.isComplete()) showResultOverlay()
        }
        boardView.onPathCompleted = { _ ->
            if (game.isComplete()) showResultOverlay()
        }

        val headerArea = findViewById<FrameLayout>(R.id.the_line_widget_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
    }

    private fun openResetOverlay() {
        pendingMode = game.mode
        updateModeButtons()
        resetOverlay.visibility = VISIBLE
    }

    private fun setPendingMode(mode: TheLineMode) {
        pendingMode = mode
        updateModeButtons()
    }

    private fun updateModeButtons() {
        btnModeSingle.setTextColor(Color.parseColor(
            if (pendingMode == TheLineMode.SINGLE) COLOR_MODE_ACTIVE else COLOR_MODE_INACTIVE
        ))
        btnModeMulti.setTextColor(Color.parseColor(
            if (pendingMode == TheLineMode.MULTI) COLOR_MODE_ACTIVE else COLOR_MODE_INACTIVE
        ))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return true
    }

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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false; skipNextDragFrame = false
            }
        }
        return true
    }

    private fun showResultOverlay() {
        resultOverlay.visibility = VISIBLE
        val reward = when (game.difficulty) {
            TheLineDifficulty.EASY   -> 1
            TheLineDifficulty.MEDIUM -> 2
            TheLineDifficulty.HARD   -> 3
        }
        NeutrinoRepository(context).addBalance(reward)
    }

    private fun startNewGame(diff: TheLineDifficulty) {
        game.mode = pendingMode
        game.difficulty = diff
        savePrefs()
        updateDifficultyLabel()
        generateAndLoad()
    }

    private fun updateDifficultyLabel() {
        val idx = DIFFICULTIES.indexOf(game.difficulty).coerceAtLeast(0)
        val modePrefix = if (game.mode == TheLineMode.MULTI) "M · " else ""
        difficultyLabel.text = "$modePrefix${DIFFICULTY_LABELS[idx]}"
        difficultyLabel.setTextColor(Color.parseColor(DIFFICULTY_COLORS[idx]))
    }

    private fun generateAndLoad() {
        loadingView.visibility = VISIBLE
        boardView.visibility = INVISIBLE
        val mode = game.mode
        val diff = game.difficulty
        Thread {
            val puzzle = TheLineGenerator.generate(mode, diff)
            mainHandler.post {
                if (puzzle != null) {
                    game.loadPuzzle(puzzle)
                    boardView.loadGame(game)
                }
                loadingView.visibility = GONE
                boardView.visibility = VISIBLE
            }
        }.start()
    }

    private fun savePrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIFFICULTY, game.difficulty.name)
            .putString(KEY_MODE, game.mode.name)
            .apply()
    }

    fun reload() {
        resultOverlay.visibility = GONE
        resetOverlay.visibility = GONE
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        game.difficulty = TheLineDifficulty.entries.find { it.name == prefs.getString(KEY_DIFFICULTY, null) }
            ?: TheLineDifficulty.EASY
        game.mode = TheLineMode.entries.find { it.name == prefs.getString(KEY_MODE, null) }
            ?: TheLineMode.SINGLE
        pendingMode = game.mode
        updateDifficultyLabel()
        generateAndLoad()
    }

    fun applyBackgroundOpacity(percent: Int) {
        val fraction = percent.coerceIn(0, 100) / 100f
        val alpha = (fraction * 255f).toInt()
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseCardColor, alpha))
        boardView.alpha = fraction
    }
}
