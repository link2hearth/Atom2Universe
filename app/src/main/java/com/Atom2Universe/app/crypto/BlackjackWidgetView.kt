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
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.blackjack.BlackjackGame
import com.Atom2Universe.app.games.blackjack.BlackjackView
import com.Atom2Universe.app.games.blackjack.GamePhase
import com.Atom2Universe.app.games.blackjack.PayoutResult
import com.Atom2Universe.app.games.blackjack.PlayerAction
import kotlin.math.hypot

class BlackjackWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var cardView: MaterialCardView
    private lateinit var bjView: BlackjackView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private lateinit var resetOverlay: FrameLayout
    private lateinit var neutrinoText: TextView
    private lateinit var btnBetCycle: TextView

    private val baseCardColor = Color.parseColor("#0F172A")
    private val game = BlackjackGame()
    private val handler = Handler(Looper.getMainLooper())
    private val neutrinoRepo by lazy { NeutrinoRepository(context) }

    // Niveaux de mise (en neutrinos) — 0 = mode gratuit
    private val betLevels = listOf(0, 1, 2, 5, 10, 25)
    private var betLevelIndex = 0

    // ── Zoom ──────────────────────────────────────────────────────────────────────
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

    init {
        LayoutInflater.from(context).inflate(R.layout.view_blackjack_widget, this, true)
        cardView = findViewById(R.id.bj_widget_card)
        bjView = findViewById(R.id.bj_widget_view)
        resultOverlay = findViewById(R.id.bj_widget_result_overlay)
        resultText = findViewById(R.id.bj_widget_result_text)
        resetOverlay = findViewById(R.id.bj_widget_reset_overlay)
        neutrinoText = findViewById(R.id.bj_widget_neutrinos)
        btnBetCycle = findViewById(R.id.bj_widget_btn_bet_cycle)

        bjView.widgetMode = true
        game.setupPlayers(0)
        syncBalanceFromRepo()
        bjView.game = game

        // Clic sur l'overlay de résultat → fermer et revenir au DEAL
        resultOverlay.setOnClickListener {
            resultOverlay.visibility = View.GONE
            showBettingUI()
            bjView.refresh()
        }

        // Reset
        findViewById<TextView>(R.id.bj_widget_btn_reset).setOnClickListener {
            resultOverlay.visibility = View.GONE
            resetOverlay.visibility = View.VISIBLE
        }
        findViewById<TextView>(R.id.bj_widget_reset_confirm).setOnClickListener {
            resetOverlay.visibility = View.GONE
            startNewGame()
        }
        findViewById<TextView>(R.id.bj_widget_reset_cancel).setOnClickListener {
            resetOverlay.visibility = View.GONE
        }

        // Cycle de mise : FREE → 1 → 2 → 5 → 10 → 25 → FREE …
        btnBetCycle.setOnClickListener {
            val balance = game.humanPlayer.balance
            var nextIndex = (betLevelIndex + 1) % betLevels.size
            // Sauter les paliers supérieurs au solde (sauf 0 = gratuit, toujours disponible)
            var iterations = 0
            while (betLevels[nextIndex] > balance && betLevels[nextIndex] > 0 && iterations < betLevels.size) {
                nextIndex = (nextIndex + 1) % betLevels.size
                iterations++
            }
            betLevelIndex = nextIndex
            updateBetCycleButton()
        }

        // Action buttons
        findViewById<TextView>(R.id.bj_widget_btn_deal).setOnClickListener { onWidgetDeal() }
        findViewById<TextView>(R.id.bj_widget_btn_hit).setOnClickListener { onWidgetAction(PlayerAction.HIT) }
        findViewById<TextView>(R.id.bj_widget_btn_stand).setOnClickListener { onWidgetAction(PlayerAction.STAND) }

        // Header drag
        val header = findViewById<FrameLayout>(R.id.bj_widget_header)
        header.setOnTouchListener { _, ev -> handleHeaderTouch(ev) }

        showBettingUI()
        bjView.refresh()
    }

    // ── Neutrinos ─────────────────────────────────────────────────────────────────
    private fun syncBalanceFromRepo() {
        game.humanPlayer.balance = neutrinoRepo.getBalance()
    }

    private fun updateNeutrinoDisplay() {
        neutrinoText.text = "⚛ ${game.humanPlayer.balance}"
    }

    private fun updateBetCycleButton() {
        val bet = betLevels[betLevelIndex]
        game.pendingBet = bet
        btnBetCycle.text = if (bet == 0) "FREE +" else "⚛$bet +"
    }

    // ── Pinch zoom ────────────────────────────────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) { scaleDetector.onTouchEvent(ev); return true }
        return false
    }
    override fun onTouchEvent(ev: MotionEvent): Boolean { scaleDetector.onTouchEvent(ev); return true }

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

    // ── UI helpers ────────────────────────────────────────────────────────────────
    private fun showBettingUI() {
        val dealArea = findViewById<View>(R.id.bj_widget_deal_area)
        val actionArea = findViewById<View>(R.id.bj_widget_action_area)
        dealArea?.visibility = View.VISIBLE
        actionArea?.visibility = View.GONE

        syncBalanceFromRepo()
        updateNeutrinoDisplay()

        val balance = game.humanPlayer.balance
        // Si solde vide → mode gratuit automatique
        if (balance <= 0) {
            betLevelIndex = 0
        } else if (betLevels[betLevelIndex] > balance) {
            betLevelIndex = 0
        }
        updateBetCycleButton()
    }

    private fun showActionUI() {
        val dealArea = findViewById<View>(R.id.bj_widget_deal_area)
        val actionArea = findViewById<View>(R.id.bj_widget_action_area)
        dealArea?.visibility = View.GONE
        actionArea?.visibility = View.VISIBLE
    }

    // ── Game logic ────────────────────────────────────────────────────────────────
    private fun startNewGame() {
        handler.removeCallbacksAndMessages(null)
        resultOverlay.visibility = View.GONE
        resetOverlay.visibility = View.GONE
        syncBalanceFromRepo()
        betLevelIndex = 0
        updateBetCycleButton()
        showBettingUI()
        bjView.game = game
        bjView.refresh()
    }

    private fun onWidgetDeal() {
        val balance = game.humanPlayer.balance
        game.pendingBet = betLevels[betLevelIndex].coerceAtMost(if (balance > 0) balance else 0)
        game.startRound()
        bjView.game = game
        bjView.refresh()
        showActionUI()
        handler.postDelayed({ processWidgetTurn() }, 400L)
    }

    private fun processWidgetTurn() {
        when {
            game.phase == GamePhase.PAYOUT -> processRoundEnd()
            game.phase == GamePhase.DEALER_TURN -> scheduleDealerStep()
            game.isHumanTurn -> showActionUI()
            else -> scheduleDealerStep()
        }
    }

    private fun onWidgetAction(action: PlayerAction) {
        val movedToDealer = when (action) {
            PlayerAction.HIT -> game.hit()
            PlayerAction.STAND -> game.stand()
            else -> game.stand()
        }
        bjView.refresh()
        handler.postDelayed({
            if (movedToDealer) scheduleDealerStep() else processWidgetTurn()
        }, 150L)
    }

    private fun scheduleDealerStep() {
        val dealArea = findViewById<View>(R.id.bj_widget_deal_area)
        val actionArea = findViewById<View>(R.id.bj_widget_action_area)
        dealArea?.visibility = View.GONE
        actionArea?.visibility = View.GONE

        handler.postDelayed({
            val done = game.dealerStep()
            bjView.refresh()
            if (done) {
                game.calculatePayouts()
                handler.postDelayed({ processRoundEnd() }, 300L)
            } else scheduleDealerStep()
        }, 600L)
    }

    private fun processRoundEnd() {
        val hand = game.humanPlayer.hands.firstOrNull() ?: return
        // calculatePayouts() a déjà mis à jour game.humanPlayer.balance
        val netChange = when (hand.payoutResult) {
            PayoutResult.WIN -> hand.bet
            PayoutResult.WIN_BJ -> (hand.bet * 1.5f).toInt()
            PayoutResult.LOSE -> -hand.bet
            else -> 0
        }

        // Synchroniser le vrai solde neutrinos (sauf mode gratuit)
        if (hand.bet > 0 && netChange != 0) {
            neutrinoRepo.setBalance(game.humanPlayer.balance)
            if (netChange > 0) GameStatsRepository(context).recordBlackjackWon()
        }

        val rText = if (hand.bet == 0) {
            when (hand.payoutResult) {
                PayoutResult.WIN, PayoutResult.WIN_BJ -> "WIN"
                PayoutResult.LOSE -> "LOSE"
                else -> "="
            }
        } else {
            when (hand.payoutResult) {
                PayoutResult.WIN -> "+${hand.bet} ⚛"
                PayoutResult.WIN_BJ -> "BJ!\n+${(hand.bet * 1.5f).toInt()} ⚛"
                PayoutResult.LOSE -> "-${hand.bet} ⚛"
                else -> "="
            }
        }
        val rColor = when (hand.payoutResult) {
            PayoutResult.WIN, PayoutResult.WIN_BJ -> Color.parseColor("#66BB6A")
            PayoutResult.LOSE -> Color.parseColor("#EF5350")
            else -> Color.parseColor("#90A4AE")
        }

        resultText.text = rText
        resultText.setTextColor(rColor)
        resultOverlay.visibility = View.VISIBLE
        bjView.refresh()
        updateNeutrinoDisplay()
    }

    /** Appelé par l'activité quand l'état ViewModel change (source de vérité pour le solde). */
    fun setNeutrinoBalance(n: Int) {
        if (game.phase == GamePhase.BETTING) {
            game.humanPlayer.balance = n
            neutrinoRepo.setBalance(n)
            updateNeutrinoDisplay()
            showBettingUI()
        }
    }

    // ── Appelé par MainClickerActivity quand le widget devient visible ─────────────
    fun reload() {
        handler.removeCallbacksAndMessages(null)
        resultOverlay.visibility = View.GONE
        resetOverlay.visibility = View.GONE
        syncBalanceFromRepo()
        bjView.game = game
        showBettingUI()
        bjView.refresh()
    }

    fun applyBackgroundOpacity(percent: Int) {
        val alpha = (percent.coerceIn(0, 100) / 100f * 255f).toInt()
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseCardColor, alpha))
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
