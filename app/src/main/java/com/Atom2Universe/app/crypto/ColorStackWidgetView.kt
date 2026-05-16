package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.colorstack.ColorStackGame
import com.Atom2Universe.app.games.colorstack.ColorStackView
import kotlin.math.hypot
import androidx.core.content.edit

class ColorStackWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    ColorStackView.OnMoveListener {

    companion object {
        private const val PREFS_NAME = "color_stack_save"
        private const val KEY_SAVE = "save_json"

        private val DIFFICULTIES = listOf(
            ColorStackGame.Difficulty.EASY,
            ColorStackGame.Difficulty.MEDIUM,
            ColorStackGame.Difficulty.HARD
        )
        private val DIFFICULTY_LABELS = listOf("Facile", "Moyen", "Difficile")
        private val DIFFICULTY_COLORS = listOf("#4AB3FF", "#F6B93B", "#FF6B6B")
    }

    private lateinit var cardView: MaterialCardView
    private lateinit var colorStackView: ColorStackView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private lateinit var resetOverlay: FrameLayout
    private lateinit var difficultyLabel: TextView
    private val baseCardColor = Color.parseColor("#0F172A")

    private val game = ColorStackGame()

    // Timer pour la partie Hard : ms depuis le début, 0 si annulé ou hors Hard
    private var hardGameStartMs = 0L

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
        LayoutInflater.from(context).inflate(R.layout.view_color_stack_widget, this, true)

        cardView = findViewById(R.id.color_stack_widget_card)
        colorStackView = findViewById(R.id.color_stack_widget_view)
        resultOverlay = findViewById(R.id.color_stack_result_overlay)
        resultText = findViewById(R.id.color_stack_result_text)
        resetOverlay = findViewById(R.id.color_stack_reset_overlay)
        difficultyLabel = findViewById(R.id.color_stack_widget_difficulty)

        colorStackView.game = game
        colorStackView.listener = this

        // Tap résultat overlay → fermer
        resultOverlay.setOnClickListener { resultOverlay.visibility = View.GONE }

        // Bouton "Nouvelle partie" dans l'overlay victoire → ouvre le sélecteur
        findViewById<TextView>(R.id.color_stack_btn_new_game).setOnClickListener {
            resultOverlay.visibility = View.GONE
            resetOverlay.visibility = View.VISIBLE
        }

        // Bouton undo (↩) dans le header
        findViewById<TextView>(R.id.color_stack_btn_undo).setOnClickListener {
            if (game.undo()) {
                colorStackView.refresh()
                persistGame()
            }
        }

        // Bouton reset (↺) dans le header → ouvre le sélecteur de difficulté
        findViewById<TextView>(R.id.color_stack_btn_reset).setOnClickListener {
            resetOverlay.visibility = View.VISIBLE
        }

        // Boutons de difficulté dans l'overlay
        findViewById<TextView>(R.id.color_stack_diff_easy).setOnClickListener {
            resetOverlay.visibility = View.GONE
            startNewGame(ColorStackGame.Difficulty.EASY)
        }
        findViewById<TextView>(R.id.color_stack_diff_medium).setOnClickListener {
            resetOverlay.visibility = View.GONE
            startNewGame(ColorStackGame.Difficulty.MEDIUM)
        }
        findViewById<TextView>(R.id.color_stack_diff_hard).setOnClickListener {
            resetOverlay.visibility = View.GONE
            startNewGame(ColorStackGame.Difficulty.HARD)
        }

        // Overlay reset : annuler
        findViewById<TextView>(R.id.color_stack_reset_cancel).setOnClickListener {
            resetOverlay.visibility = View.GONE
        }

        val headerArea = findViewById<FrameLayout>(R.id.color_stack_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
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

    private fun startNewGame(diff: ColorStackGame.Difficulty) {
        resultOverlay.visibility = View.GONE
        resetOverlay.visibility = View.GONE
        game.newGame(diff)
        colorStackView.game = game
        colorStackView.refresh()
        updateDifficultyLabel()
        persistGame()
        hardGameStartMs = if (diff == ColorStackGame.Difficulty.HARD) System.currentTimeMillis() else 0L
        if (diff == ColorStackGame.Difficulty.HARD) GameStatsRepository(context).recordColorStackHardStarted()
    }

    private fun updateDifficultyLabel() {
        val idx = DIFFICULTIES.indexOf(game.difficulty).coerceAtLeast(0)
        difficultyLabel.text = DIFFICULTY_LABELS[idx]
        difficultyLabel.setTextColor(Color.parseColor(DIFFICULTY_COLORS[idx]))
    }

    override fun onMove(from: Int, to: Int) {
        if (game.move(from, to)) {
            colorStackView.refresh()
            if (game.solved) {
                resultText.text = "🎉 Trié !"
                resultOverlay.visibility = View.VISIBLE
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit { remove(KEY_SAVE) }
                when (game.difficulty) {
                    ColorStackGame.Difficulty.HARD -> {
                        val statsRepo = GameStatsRepository(context)
                        statsRepo.recordColorStackHardWon()
                        if (hardGameStartMs > 0L) {
                            statsRepo.recordColorStackHardBestTime(System.currentTimeMillis() - hardGameStartMs)
                        }
                        hardGameStartMs = 0L
                        NeutrinoRepository(context).addBalance(3)
                    }
                    ColorStackGame.Difficulty.MEDIUM -> NeutrinoRepository(context).addBalance(1)
                    else -> Unit
                }
            } else {
                persistGame()
            }
        }
    }

    override fun onColumnSelected(col: Int?) {}

    private fun persistGame() {
        if (game.solved) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_SAVE, game.serialize()) }
    }

    fun reload() {
        resultOverlay.visibility = View.GONE
        resetOverlay.visibility = View.GONE
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAVE, null)
        val restored = json != null && game.deserialize(json)
        if (!restored) {
            game.newGame(ColorStackGame.Difficulty.EASY)
            persistGame()
        }
        colorStackView.game = game
        colorStackView.refresh()
        updateDifficultyLabel()
    }

    fun applyBackgroundOpacity(percent: Int) {
        val fraction = percent.coerceIn(0, 100) / 100f
        val alpha = (fraction * 255f).toInt()
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseCardColor, alpha))
        colorStackView.alpha = fraction
    }

    /** Annule le timer Hard en cours (appelé par l'activité sur onPause). */
    fun cancelTimer() {
        hardGameStartMs = 0L
    }
}
