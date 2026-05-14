package com.Atom2Universe.app.games.blackjack

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class BlackjackActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var tableView: BlackjackView
    private lateinit var header: LinearLayout
    private lateinit var balanceText: TextView
    private lateinit var aiLabel: TextView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private lateinit var betBar: LinearLayout
    private lateinit var actionBar: LinearLayout
    private lateinit var waitingBar: LinearLayout
    private lateinit var waitingText: TextView
    private lateinit var betAmountText: TextView
    private lateinit var btnBetMinus: Button
    private lateinit var btnBetPlus: Button
    private lateinit var btnDeal: Button
    private lateinit var btnHit: Button
    private lateinit var btnStand: Button
    private lateinit var btnDouble: Button
    private lateinit var btnSplit: Button

    private val game = BlackjackGame()
    private val handler = Handler(Looper.getMainLooper())
    private val statsRepo by lazy { GameStatsRepository(this) }
    private val neutrinoRepo by lazy { NeutrinoRepository(this) }
    private var numAI = 0

    // Manga bubble texts by AI action
    private val bubblesByAction = mapOf(
        PlayerAction.HIT to listOf("!!", "Hit me!", "★", "Go go!"),
        PlayerAction.STAND to listOf("✋", "Good enough", "Nope", "I'll stay"),
        PlayerAction.DOUBLE to listOf("DOUBLE !!", "×2 !!", "All in !", "★★"),
        PlayerAction.SPLIT to listOf("SPLIT!!", "Two chances!", "!!!")
    )
    private val bustBubbles = listOf("×_×", "BUST!!", "orz", "Nooo...")
    private val bjBubbles = listOf("NATURAL!!", "★★★", "Lucky!!", "BJ !!!")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_blackjack)
        bindViews()
        setupListeners()
        showSettingsDialog(firstTime = true)
    }

    private fun bindViews() {
        tableView = findViewById(R.id.blackjack_table)
        header = findViewById(R.id.bj_header)
        balanceText = findViewById(R.id.bj_balance)
        aiLabel = findViewById(R.id.bj_ai_label)
        resultOverlay = findViewById(R.id.bj_result_overlay)
        resultText = findViewById(R.id.bj_result_text)
        betBar = findViewById(R.id.bj_bet_bar)
        actionBar = findViewById(R.id.bj_action_bar)
        waitingBar = findViewById(R.id.bj_waiting_bar)
        waitingText = findViewById(R.id.bj_waiting_text)
        betAmountText = findViewById(R.id.bj_bet_amount)
        btnBetMinus = findViewById(R.id.bj_bet_minus)
        btnBetPlus = findViewById(R.id.bj_bet_plus)
        btnDeal = findViewById(R.id.bj_deal)
        btnHit = findViewById(R.id.bj_hit)
        btnStand = findViewById(R.id.bj_stand)
        btnDouble = findViewById(R.id.bj_double)
        btnSplit = findViewById(R.id.bj_split)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.bj_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.bj_settings).setOnClickListener { showSettingsDialog(firstTime = false) }

        btnBetMinus.setOnClickListener {
            val step = betStep()
            game.pendingBet = (game.pendingBet - step).coerceAtLeast(0)
            updateBetDisplay()
        }
        btnBetPlus.setOnClickListener {
            val step = betStep()
            val max = game.humanPlayer.balance
            game.pendingBet = (game.pendingBet + step).coerceAtMost(max.coerceAtLeast(1))
            updateBetDisplay()
        }
        btnDeal.setOnClickListener { onDealClicked() }
        btnHit.setOnClickListener { onHumanAction(PlayerAction.HIT) }
        btnStand.setOnClickListener { onHumanAction(PlayerAction.STAND) }
        btnDouble.setOnClickListener { onHumanAction(PlayerAction.DOUBLE) }
        btnSplit.setOnClickListener { onHumanAction(PlayerAction.SPLIT) }
        resultOverlay.setOnClickListener { onResultDismissed() }
    }

    private fun betStep(): Int = when {
        game.pendingBet < 10 -> 1
        game.pendingBet < 50 -> 5
        else -> 10
    }

    // ── Settings dialog ──────────────────────────────────────────────────────────
    private fun showSettingsDialog(firstTime: Boolean) {
        var selectedAI = numAI
        val options = Array(5) { i -> "$i ${getString(R.string.blackjack_ia_short)}" }
        options[0] = "0 IA"

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.blackjack_settings_title))
            .setSingleChoiceItems(options, selectedAI) { _, which -> selectedAI = which }
            .setPositiveButton(if (firstTime) R.string.blackjack_start else android.R.string.ok) { _, _ ->
                numAI = selectedAI
                game.setupPlayers(numAI)
                game.humanPlayer.balance = neutrinoRepo.getWidgetBalance().coerceAtLeast(0)
                for (ai in game.aiPlayers) ai.balance = 100
                aiLabel.text = if (numAI > 0) getString(R.string.blackjack_ai_players_label, numAI) else ""
                game.pendingBet = game.pendingBet.coerceIn(1, game.humanPlayer.balance.coerceAtLeast(1))
                tableView.game = game
                showBettingPhase()
            }
            .setCancelable(!firstTime)
        if (!firstTime) builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    // ── Phase display ────────────────────────────────────────────────────────────
    private fun showBettingPhase() {
        betBar.visibility = View.VISIBLE
        actionBar.visibility = View.GONE
        waitingBar.visibility = View.GONE
        resultOverlay.visibility = View.GONE
        tableView.isThinking = false
        updateBetDisplay()
        updateBalanceDisplay()
        tableView.refresh()

        // Mode gratuit automatique si plus de neutrinos
        if (game.humanPlayer.balance <= 0) {
            game.pendingBet = 0
            updateBetDisplay()
        }
    }

    private fun updateBetDisplay() {
        betAmountText.text = if (game.pendingBet == 0) getString(R.string.blackjack_bet_free) else game.pendingBet.toString()
        btnBetMinus.isEnabled = game.pendingBet > 0
        btnBetPlus.isEnabled = game.pendingBet < game.humanPlayer.balance
    }

    private fun updateBalanceDisplay() {
        balanceText.text = getString(R.string.blackjack_neutrino_balance, game.humanPlayer.balance)
    }

    // ── Deal ─────────────────────────────────────────────────────────────────────
    private fun onDealClicked() {
        statsRepo.recordBlackjackStarted()
        game.startRound()
        tableView.game = game
        tableView.refresh()

        betBar.visibility = View.GONE
        resultOverlay.visibility = View.GONE

        // Check if any player got blackjack → brief visual pause then process
        handler.postDelayed({ processCurrentTurn() }, 600L)
    }

    // ── Turn processing ──────────────────────────────────────────────────────────
    private fun processCurrentTurn() {
        when {
            game.phase == GamePhase.PAYOUT -> showResults()
            game.phase == GamePhase.DEALER_TURN -> executeDealerTurn()
            game.isHumanTurn -> showHumanActions()
            game.isAITurn -> executeAIStep()
            else -> executeDealerTurn()
        }
    }

    private fun showHumanActions() {
        tableView.isThinking = false
        waitingBar.visibility = View.GONE
        actionBar.visibility = View.VISIBLE
        betBar.visibility = View.GONE
        val hand = game.humanPlayer.currentHand
        btnDouble.isEnabled = hand?.canDouble == true && game.humanPlayer.balance >= (hand.bet * 2)
        btnSplit.isEnabled = hand?.canSplit == true && game.humanPlayer.balance >= (hand.bet * 2)
        tableView.startAnimating() // for highlight glow
        tableView.refresh()
    }

    private fun onHumanAction(action: PlayerAction) {
        actionBar.visibility = View.GONE
        tableView.isThinking = false
        tableView.stopAnimating()

        val movedToDealer = when (action) {
            PlayerAction.HIT -> game.hit()
            PlayerAction.STAND -> game.stand()
            PlayerAction.DOUBLE -> game.doubleDown()
            PlayerAction.SPLIT -> game.split()
        }
        tableView.refresh()

        handler.postDelayed({
            if (movedToDealer) {
                handler.postDelayed({ executeDealerTurn() }, 300L)
            } else {
                processCurrentTurn()
            }
        }, 200L)
    }

    private fun executeAIStep() {
        if (!game.isAITurn) { processCurrentTurn(); return }

        val player = game.activePlayer ?: return
        val action = game.getAIAction(player)

        // Show thinking indicator
        tableView.isThinking = true
        waitingBar.visibility = View.VISIBLE
        waitingText.text = "${player.name}..."
        actionBar.visibility = View.GONE
        betBar.visibility = View.GONE

        // Add manga bubble (thinking)
        val thinkBubble = listOf("...", "Hmm", "★", "?").random()
        tableView.addBubble(thinkBubble, game.activePlayerIndex)
        tableView.refresh()

        val delay = (700L..1700L).random()
        handler.postDelayed({
            tableView.isThinking = false

            // Action bubble
            val actionBubble = (bubblesByAction[action] ?: listOf("!")).random()
            tableView.addBubble(actionBubble, game.activePlayerIndex)

            val movedToDealer = game.executeAIAction()
            tableView.refresh()

            // Show BJ / Bust bubble
            val hand = player.currentHand
            when {
                hand?.isBlackjack == true -> tableView.addBubble(bjBubbles.random(), game.activePlayerIndex - 1)
                hand?.actionState == ActionState.BUST -> tableView.addBubble(bustBubbles.random(), game.activePlayerIndex - 1)
                else -> Unit
            }

            handler.postDelayed({
                if (movedToDealer) {
                    executeDealerTurn()
                } else {
                    processCurrentTurn()
                }
            }, 250L)
        }, delay)
    }

    private fun executeDealerTurn() {
        tableView.isThinking = false
        actionBar.visibility = View.GONE
        betBar.visibility = View.GONE
        waitingBar.visibility = View.VISIBLE
        waitingText.text = getString(R.string.blackjack_dealer_thinking)
        tableView.refresh()

        scheduleDealerStep()
    }

    private fun scheduleDealerStep() {
        handler.postDelayed({
            val done = game.dealerStep()
            tableView.refresh()
            if (done) {
                game.calculatePayouts()
                handler.postDelayed({ showResults() }, 400L)
            } else {
                scheduleDealerStep()
            }
        }, 700L)
    }

    // ── Results ──────────────────────────────────────────────────────────────────
    private fun showResults() {
        waitingBar.visibility = View.GONE
        tableView.refresh()

        // Compute human result
        val humanHands = game.humanPlayer.hands
        val netChange = humanHands.sumOf { h ->
            when (h.payoutResult) {
                PayoutResult.WIN -> h.bet
                PayoutResult.WIN_BJ -> (h.bet * 1.5f).toInt()
                PayoutResult.LOSE -> -h.bet
                PayoutResult.PUSH -> 0
                null -> 0
            }
        }

        // Sync neutrino balance (sauf mode gratuit)
        val isFreeBet = humanHands.all { it.bet == 0 }
        if (!isFreeBet) {
            when {
                netChange > 0 -> {
                    neutrinoRepo.addPending(netChange)
                    neutrinoRepo.setWidgetBalance(game.humanPlayer.balance)
                    statsRepo.recordBlackjackWon()
                }
                netChange < 0 -> {
                    neutrinoRepo.addDebit(-netChange)
                    neutrinoRepo.setWidgetBalance(game.humanPlayer.balance)
                }
            }
        }

        updateBalanceDisplay()

        // Build result text
        val rText = if (isFreeBet) {
            when {
                humanHands.any { it.payoutResult == PayoutResult.WIN_BJ } -> getString(R.string.blackjack_result_bj_free)
                humanHands.any { it.payoutResult == PayoutResult.WIN } -> getString(R.string.blackjack_result_win_free)
                humanHands.any { it.payoutResult == PayoutResult.LOSE } -> getString(R.string.blackjack_result_lose_free)
                else -> getString(R.string.blackjack_result_push)
            }
        } else {
            when {
                humanHands.any { it.payoutResult == PayoutResult.WIN_BJ } ->
                    getString(R.string.blackjack_result_bj, (humanHands.filter { it.payoutResult == PayoutResult.WIN_BJ }.sumOf { (it.bet * 1.5f).toInt() }))
                netChange > 0 -> getString(R.string.blackjack_result_win, netChange)
                netChange < 0 -> getString(R.string.blackjack_result_lose, -netChange)
                else -> getString(R.string.blackjack_result_push)
            }
        }

        resultText.text = rText
        resultOverlay.visibility = View.VISIBLE

        // AI reaction bubbles
        for ((idx, ai) in game.aiPlayers.withIndex()) {
            val aiResult = ai.hands.firstOrNull()?.payoutResult
            val bubble = when (aiResult) {
                PayoutResult.WIN, PayoutResult.WIN_BJ -> bjBubbles.random()
                PayoutResult.LOSE -> bustBubbles.random()
                else -> listOf("=", "...", "~").random()
            }
            handler.postDelayed({ tableView.addBubble(bubble, idx) }, idx * 300L)
        }
    }

    private fun onResultDismissed() {
        resultOverlay.visibility = View.GONE
        showBettingPhase()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        neutrinoRepo.setWidgetBalance(game.humanPlayer.balance)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tableView.stopAnimating()
    }
}

// Extension to stop animation on BlackjackView
fun BlackjackView.stopAnimating() {
    isThinking = false
}
