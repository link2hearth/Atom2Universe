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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.pipetap.PipeTapDifficulty
import com.Atom2Universe.app.games.pipetap.PipeTapGame
import com.Atom2Universe.app.games.pipetap.PipeTapView
import kotlin.math.hypot

class PipeTapWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var gameView: PipeTapView
    private lateinit var statsText: TextView
    private lateinit var winOverlay: LinearLayout
    private lateinit var winMovesText: TextView
    private lateinit var newGamePanel: LinearLayout

    private val game = PipeTapGame()
    private val handler = Handler(Looper.getMainLooper())

    private var timerStartMs = 0L
    private var timerRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
            updateStats()
            handler.postDelayed(this, 500L)
        }
    }

    // ── Scale (pinch) ─────────────────────────────────────────────────────────────
    private val minScale = 0.35f
    private val maxScale = 3.0f
    private var currentScale = 1f
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                scaleX = currentScale
                scaleY = currentScale
                return true
            }
        })

    // ── Drag (header only) ────────────────────────────────────────────────────────
    private var dragLastX = 0f; private var dragLastY = 0f
    private var dragDownX = 0f; private var dragDownY = 0f
    private var isDragging = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    // ── Difficulty chips ──────────────────────────────────────────────────────────
    private val chipMap = mutableMapOf<PipeTapDifficulty, MaterialButton>()
    private var pendingDifficulty = PipeTapDifficulty.MEDIUM

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pipetap_widget, this, true)
        gameView     = findViewById(R.id.pipetap_widget_view)
        statsText    = findViewById(R.id.pipetap_widget_stats)
        winOverlay   = findViewById(R.id.pipetap_win_overlay)
        winMovesText = findViewById(R.id.pipetap_win_moves)
        newGamePanel = findViewById(R.id.pipetap_new_game_panel)

        val header = findViewById<FrameLayout>(R.id.pipetap_widget_header)
        header.setOnTouchListener { _, ev -> handleHeaderTouch(ev) }

        findViewById<TextView>(R.id.pipetap_widget_btn_new).setOnClickListener {
            stopTimer()
            pendingDifficulty = game.difficulty
            updateNewGameChips()
            newGamePanel.visibility = VISIBLE
        }

        findViewById<MaterialButton>(R.id.pipetap_widget_btn_cancel).setOnClickListener {
            newGamePanel.visibility = GONE
            if (!game.solved) startTimer()
        }

        findViewById<MaterialButton>(R.id.pipetap_widget_btn_confirm).setOnClickListener {
            newGamePanel.visibility = GONE
            startNewGame(pendingDifficulty)
        }

        gameView.listener = object : PipeTapView.OnTileRotatedListener {
            override fun onTileRotated(solved: Boolean) {
                updateStats()
                if (solved) {
                    stopTimer()
                    showWinOverlay()
                    if (!game.rewardClaimed) {
                        game.rewardClaimed = true
                        val reward = (game.difficulty.ordinal + 1) * 2
                        NeutrinoRepository(context).addBalance(reward)
                        GameStatsRepository(context).recordPipeTapHardWon()
                    }
                }
            }
        }

        startNewGame(PipeTapDifficulty.MEDIUM)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        buildDiffChips()
    }

    private fun buildDiffChips() {
        val row = findViewById<LinearLayout>(R.id.pipetap_new_game_diff_row)
        val dp4 = (4 * context.resources.displayMetrics.density).toInt()
        for (diff in PipeTapDifficulty.entries) {
            val chip = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = diff.label
                textSize = 9f
                setPadding(dp4 * 2, 0, dp4 * 2, 0)
                val params = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    (20 * context.resources.displayMetrics.density).toInt()
                )
                params.setMargins(dp4 / 2, 0, dp4 / 2, 0)
                layoutParams = params
                minWidth = 0
                minimumWidth = 0
                setOnClickListener {
                    pendingDifficulty = diff
                    updateNewGameChips()
                }
            }
            row.addView(chip)
            chipMap[diff] = chip
        }
    }

    private fun updateNewGameChips() {
        val activeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#06B6D4"))
        val inactiveColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1F2937"))
        for ((diff, chip) in chipMap) {
            chip.backgroundTintList = if (diff == pendingDifficulty) activeColor else inactiveColor
        }
    }

    // ── Game ──────────────────────────────────────────────────────────────────────
    private fun showWinOverlay() {
        winMovesText.text = "${game.moves} mvt"
        winOverlay.visibility = VISIBLE
    }

    private fun startNewGame(diff: PipeTapDifficulty) {
        stopTimer()
        winOverlay.visibility = GONE
        game.newGame(diff)
        gameView.game = game
        gameView.refresh()
        updateStats()
        startTimer()
    }

    private fun updateStats() {
        statsText.text = "${game.difficulty.label} · ${game.moves} mvt"
    }

    // ── Timer ─────────────────────────────────────────────────────────────────────
    private fun startTimer() {
        if (timerRunning) return
        timerStartMs = System.currentTimeMillis() - game.elapsedSeconds * 1000
        timerRunning = true
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
        if (timerStartMs > 0) game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
    }

    // ── Opacity ───────────────────────────────────────────────────────────────────
    fun applyBackgroundOpacity(percent: Int) {
        alpha = percent.coerceIn(0, 100) / 100f
    }

    // ── Pinch intercept ───────────────────────────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) { scaleDetector.onTouchEvent(ev); return true }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return true
    }

    // ── Header drag ───────────────────────────────────────────────────────────────
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

    fun reload() {
        handler.removeCallbacks(timerRunnable)
        timerRunning = false
        if (game.solved) {
            winOverlay.visibility = VISIBLE
        } else {
            winOverlay.visibility = GONE
            startTimer()
        }
        gameView.refresh()
        updateStats()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
