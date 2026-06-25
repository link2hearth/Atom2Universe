package com.Atom2Universe.app.games.blackjack

enum class GamePhase { BETTING, PLAYER_TURNS, DEALER_TURN, PAYOUT }
enum class ActionState { PLAYING, STOOD, BUST, BLACKJACK, DOUBLED }
enum class PayoutResult { WIN, WIN_BJ, LOSE, PUSH }
enum class PlayerAction { HIT, STAND, DOUBLE, SPLIT }

class BlackjackHand(initialBet: Int = 0) {
    val cards = mutableListOf<BlackjackCard>()
    var bet: Int = initialBet
    var actionState: ActionState = ActionState.PLAYING
    var payoutResult: PayoutResult? = null

    private fun calcValue(cardList: List<BlackjackCard>): Int {
        var total = 0; var aces = 0
        for (c in cardList) { if (c.isAce) aces++ else total += c.value }
        repeat(aces) { total += if (total + 11 <= 21) 11 else 1 }
        return total
    }

    val value: Int get() = calcValue(cards)
    val displayValue: Int get() = calcValue(cards.filter { it.faceUp })

    val isBust: Boolean get() = value > 21
    val isBlackjack: Boolean get() = cards.size == 2 && value == 21
    val isSoft: Boolean get() {
        var t = 0; var a = 0
        for (c in cards) { if (c.isAce) a++ else t += c.value }
        return a > 0 && t + 11 <= 21
    }
    val canSplit: Boolean get() = cards.size == 2 && cards[0].value == cards[1].value && actionState == ActionState.PLAYING
    val canDouble: Boolean get() = cards.size == 2 && actionState == ActionState.PLAYING
}

data class BJPlayer(
    val id: Int,
    val name: String,
    val isHuman: Boolean,
    val isDealer: Boolean = false,
    var balance: Int = 100
) {
    val hands = mutableListOf<BlackjackHand>()
    var currentHandIndex: Int = 0
    val currentHand: BlackjackHand? get() = hands.getOrNull(currentHandIndex)

    fun resetForRound(bet: Int) {
        hands.clear()
        hands.add(BlackjackHand(bet))
        currentHandIndex = 0
    }
}

class BlackjackGame {
    companion object {
        const val NUM_DECKS = 6
        private const val RESHUFFLE_THRESHOLD = 52
        val AI_NAMES = listOf("Ryu ⚡", "Hana ✿", "Taro ★", "Kira ◈")
    }

    private val shoe = mutableListOf<BlackjackCard>()

    val dealer = BJPlayer(-1, "Dealer", isHuman = false, isDealer = true)
    val humanPlayer = BJPlayer(0, "YOU", isHuman = true)
    val aiPlayers = mutableListOf<BJPlayer>()

    var phase: GamePhase = GamePhase.BETTING
    var activePlayerIndex: Int = 0

    val players: List<BJPlayer> get() = aiPlayers + listOf(humanPlayer)
    val activePlayer: BJPlayer? get() = players.getOrNull(activePlayerIndex)
    val isHumanTurn: Boolean get() = activePlayer?.isHuman == true && phase == GamePhase.PLAYER_TURNS
    val isAITurn: Boolean get() = phase == GamePhase.PLAYER_TURNS &&
            activePlayer != null && !activePlayer!!.isHuman

    var pendingBet: Int = 10

    init { buildShoe() }

    fun setupPlayers(numAI: Int) {
        aiPlayers.clear()
        for (i in 0 until numAI.coerceIn(0, 4)) {
            aiPlayers.add(BJPlayer(i + 1, AI_NAMES[i], isHuman = false))
        }
    }

    private fun buildShoe() {
        shoe.clear()
        repeat(NUM_DECKS) {
            for (suit in 0..3) for (rank in 1..13) shoe.add(BlackjackCard(rank, suit))
        }
        shoe.shuffle()
    }

    private fun checkReshuffle() {
        if (shoe.size < RESHUFFLE_THRESHOLD) buildShoe()
    }

    private fun drawCard(faceUp: Boolean = true): BlackjackCard {
        checkReshuffle()
        return shoe.removeAt(0).copy(faceUp = faceUp)
    }

    fun startRound() {
        for (p in players) {
            val bet = if (p.isHuman) {
                if (pendingBet == 0) 0 else pendingBet.coerceIn(1, p.balance.coerceAtLeast(1))
            } else randomAIBet(p)
            p.resetForRound(bet)
        }
        dealer.resetForRound(0)

        // Deal 2 rounds: all players left→right, then dealer
        for (p in players) p.currentHand!!.cards.add(drawCard())
        dealer.currentHand!!.cards.add(drawCard())
        for (p in players) p.currentHand!!.cards.add(drawCard())
        dealer.currentHand!!.cards.add(drawCard(faceUp = false)) // hole card

        // Mark blackjacks immediately
        for (p in players) {
            if (p.currentHand!!.isBlackjack) p.currentHand!!.actionState = ActionState.BLACKJACK
        }

        phase = GamePhase.PLAYER_TURNS
        activePlayerIndex = 0
        skipDoneHands()
    }

    private fun randomAIBet(player: BJPlayer): Int {
        val max = minOf(20, player.balance)
        if (max <= 0) return 0
        val min = minOf(5, max)
        return (min..max).random()
    }

    // Skip hands that are already resolved; returns true if moved to dealer
    private fun skipDoneHands(): Boolean {
        while (activePlayerIndex < players.size) {
            val player = players[activePlayerIndex]
            val handIdx = player.hands.indexOfFirst { it.actionState == ActionState.PLAYING }
            if (handIdx >= 0) {
                player.currentHandIndex = handIdx
                return false
            }
            activePlayerIndex++
        }
        startDealerTurn()
        return true
    }

    private fun advanceHand(): Boolean {
        val player = players.getOrNull(activePlayerIndex) ?: run { startDealerTurn(); return true }
        // Try next hand for this player (after split)
        val nextIdx = player.currentHandIndex + 1
        if (nextIdx < player.hands.size && player.hands[nextIdx].actionState == ActionState.PLAYING) {
            player.currentHandIndex = nextIdx
            return false
        }
        // Move to next player
        activePlayerIndex++
        players.getOrNull(activePlayerIndex)?.currentHandIndex = 0
        return skipDoneHands()
    }

    // Returns true if moved to dealer turn
    fun hit(): Boolean {
        val hand = activePlayer?.currentHand ?: return false
        hand.cards.add(drawCard())
        return when {
            hand.isBust -> { hand.actionState = ActionState.BUST; advanceHand() }
            hand.value == 21 -> { hand.actionState = ActionState.STOOD; advanceHand() }
            else -> false
        }
    }

    fun stand(): Boolean {
        val hand = activePlayer?.currentHand ?: return false
        hand.actionState = ActionState.STOOD
        return advanceHand()
    }

    fun doubleDown(): Boolean {
        val player = activePlayer ?: return false
        val hand = player.currentHand ?: return false
        if (!hand.canDouble) return false
        val extra = minOf(hand.bet, (player.balance - hand.bet).coerceAtLeast(0))
        hand.bet += extra
        hand.cards.add(drawCard())
        hand.actionState = if (hand.isBust) ActionState.BUST else ActionState.DOUBLED
        return advanceHand()
    }

    fun split(): Boolean {
        val player = activePlayer ?: return false
        val hand = player.currentHand ?: return false
        if (!hand.canSplit || player.balance < hand.bet * 2) return false
        val newHand = BlackjackHand(hand.bet)
        newHand.cards.add(hand.cards.removeAt(1))
        hand.cards.add(drawCard())
        newHand.cards.add(drawCard())
        player.hands.add(player.currentHandIndex + 1, newHand)
        return false
    }

    // Basic strategy complète (6 decks, dealer stands on soft 17)
    fun getAIAction(player: BJPlayer): PlayerAction {
        val hand = player.currentHand ?: return PlayerAction.STAND
        val dealerUp = dealer.currentHand!!.cards.firstOrNull { it.faceUp }?.value ?: 7

        // Splits — vérifiés en premier
        if (hand.canSplit) {
            val pairVal = hand.cards[0].value
            val isAce = hand.cards[0].rank == 1
            when {
                isAce || pairVal == 8 -> return PlayerAction.SPLIT
                pairVal == 9 && dealerUp !in listOf(7, 10, 1) -> return PlayerAction.SPLIT
                pairVal == 7 && dealerUp in 2..7 -> return PlayerAction.SPLIT
                pairVal == 6 && dealerUp in 2..6 -> return PlayerAction.SPLIT
                pairVal == 4 && dealerUp in 5..6 -> return PlayerAction.SPLIT
                pairVal in 2..3 && dealerUp in 2..7 -> return PlayerAction.SPLIT
                // 10s et 5s : ne pas splitter, traiter comme main dure
            }
        }

        return if (hand.isSoft) aiSoftAction(hand, dealerUp, hand.value)
        else aiHardAction(hand, dealerUp, hand.value)
    }

    private fun aiSoftAction(hand: BlackjackHand, dealerUp: Int, v: Int): PlayerAction = when {
        v >= 20 -> PlayerAction.STAND
        v == 19 -> if (hand.canDouble && dealerUp == 6) PlayerAction.DOUBLE else PlayerAction.STAND
        v == 18 -> when {                                          // As+7
            hand.canDouble && dealerUp in 3..6 -> PlayerAction.DOUBLE
            dealerUp in listOf(9, 10, 1) -> PlayerAction.HIT
            else -> PlayerAction.STAND                             // vs 2,7,8
        }
        v == 17 -> if (hand.canDouble && dealerUp in 3..6) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 16 -> if (hand.canDouble && dealerUp in 4..6) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 15 -> if (hand.canDouble && dealerUp in 4..6) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 14 -> if (hand.canDouble && dealerUp in 5..6) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 13 -> if (hand.canDouble && dealerUp in 5..6) PlayerAction.DOUBLE else PlayerAction.HIT
        else -> PlayerAction.HIT
    }

    private fun aiHardAction(hand: BlackjackHand, dealerUp: Int, v: Int): PlayerAction = when {
        v >= 17 -> PlayerAction.STAND
        v in 13..16 -> if (dealerUp in 2..6) PlayerAction.STAND else PlayerAction.HIT
        v == 12 -> if (dealerUp in 4..6) PlayerAction.STAND else PlayerAction.HIT
        v == 11 -> if (hand.canDouble && dealerUp != 1) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 10 -> if (hand.canDouble && dealerUp in 2..9) PlayerAction.DOUBLE else PlayerAction.HIT
        v == 9 -> if (hand.canDouble && dealerUp in 3..6) PlayerAction.DOUBLE else PlayerAction.HIT
        else -> PlayerAction.HIT
    }

    // Returns true if moved to dealer turn
    fun executeAIAction(): Boolean {
        val player = activePlayer ?: return false
        return when (getAIAction(player)) {
            PlayerAction.HIT -> hit()
            PlayerAction.STAND -> stand()
            PlayerAction.DOUBLE -> if (activePlayer?.currentHand?.canDouble == true) doubleDown() else hit()
            PlayerAction.SPLIT -> { split(); false }
        }
    }

    private fun startDealerTurn() {
        phase = GamePhase.DEALER_TURN
        dealer.currentHand!!.cards.forEach { it.faceUp = true }
    }

    // Returns true when dealer is done playing
    fun dealerStep(): Boolean {
        val hand = dealer.currentHand ?: return true
        return if (hand.value < 17) {
            hand.cards.add(drawCard())
            false
        } else true
    }

    fun calculatePayouts() {
        val dHand = dealer.currentHand!!
        val dBJ = dHand.isBlackjack
        val dBust = dHand.isBust
        val dVal = dHand.value

        for (player in players) {
            for (hand in player.hands) {
                val result: PayoutResult = when {
                    hand.isBlackjack && dBJ -> PayoutResult.PUSH
                    hand.isBlackjack -> PayoutResult.WIN_BJ
                    dBJ -> PayoutResult.LOSE
                    hand.actionState == ActionState.BUST -> PayoutResult.LOSE
                    dBust -> PayoutResult.WIN
                    hand.value > dVal -> PayoutResult.WIN
                    hand.value < dVal -> PayoutResult.LOSE
                    else -> PayoutResult.PUSH
                }
                hand.payoutResult = result
                when (result) {
                    PayoutResult.WIN -> player.balance += hand.bet
                    PayoutResult.WIN_BJ -> player.balance += hand.bet * 2
                    PayoutResult.LOSE -> player.balance -= hand.bet
                    PayoutResult.PUSH -> Unit
                }
            }
        }
        phase = GamePhase.PAYOUT
    }
}
