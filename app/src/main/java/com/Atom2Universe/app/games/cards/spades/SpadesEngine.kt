package com.Atom2Universe.app.games.cards.spades

import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.TrickEngine
import kotlin.random.Random

/**
 * Spades (spades d'OpenSpiel). Quatre joueurs en deux équipes : toi et le siège d'en face contre les
 * deux autres. Le pique est l'atout.
 *
 * Chaque manche commence par les **annonces** : chacun dit combien de plis il pense faire (0 est un
 * « nil » : il s'engage à n'en faire aucun). L'équipe doit ramasser au moins le total de ses annonces.
 * On joue ensuite treize plis : fournir la couleur demandée, sinon n'importe quelle carte (un pique
 * coupe). On ne peut pas ouvrir à pique avant qu'un pique soit tombé, sauf main de piques.
 *
 * Points d'une manche : 10 par pli annoncé s'il est fait (1 de plus par pli en trop, qu'on appelle
 * « sac » : dix sacs coûtent 100), moins 10 par pli annoncé sinon ; un nil réussi vaut 100, raté −100.
 *
 * La partie s'arrête quand une équipe atteint [limit] points (la plus haute gagne), ou tombe à −200. Trois cents plutôt que cinq cents : une partie de téléphone.
 *
 * Coups : une annonce (`BID_BASE + n`) puis des cartes (leur numéro).
 */
class SpadesEngine private constructor(
    seed: Long,
    val limit: Int,
    dealNow: Boolean,
) : TrickEngine(seed, 4) {

    constructor(seed: Long, limit: Int = DEFAULT_LIMIT) : this(seed, limit, true)

    /** Vrai pendant les annonces, avant les plis. */
    var bidding = true
        private set

    /** Ce que chaque siège a annoncé (0 : nil), ou [NO_BID] tant qu'il n'a pas parlé. */
    val bids = IntArray(4) { NO_BID }

    var spadesBroken = false
        private set

    /** Les points et les sacs de chaque équipe : l'équipe 0 est toi et ton partenaire. */
    val teamScore = IntArray(2)
    val teamBags = IntArray(2)

    /** Ce que la dernière manche a rapporté à chaque équipe. */
    var lastHand = IntArray(2)
        private set

    override val tricksInHand: Int get() = 13

    init { if (dealNow) dealHand() }

    override val scores: IntArray get() = IntArray(4) { teamScore[it % 2] }

    override fun winners(): Set<Int> {
        val best = teamScore.max()
        return (0 until 4).filter { teamScore[it % 2] == best }.toSet()
    }

    override fun dealHand() {
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed * 6_007L + handNo))
        for (s in 0 until 4) { hands[s].clear(); hands[s].addAll(deck.subList(s * 13, s * 13 + 13)) }
        resetHandState()
        bids.fill(NO_BID)
        spadesBroken = false
        bidding = true
        leader = handNo % 4
        turn = leader
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray {
        if (bidding) return IntArray(14) { BID_BASE + it }
        val hand = hands[turn]
        if (trick.isEmpty() && !spadesBroken) {
            hand.filter { it.suit != CardSuit.SPADES }.takeIf { it.isNotEmpty() }?.let { return it.map { c -> c.id }.toIntArray() }
        }
        return followable(hand).map { it.id }.toIntArray()
    }

    override fun apply(move: Int) {
        if (bidding) {
            bids[turn] = move - BID_BASE
            if (bids.none { it == NO_BID }) { bidding = false; turn = leader } else turn = nextSeat(turn)
            return
        }
        val card = PlayingCard(move)
        if (card.suit == CardSuit.SPADES) spadesBroken = true
        playCard(turn, card)
    }

    private fun beats(card: PlayingCard, winner: PlayingCard, led: CardSuit): Boolean = when {
        card.suit == CardSuit.SPADES -> winner.suit != CardSuit.SPADES || card.highRank > winner.highRank
        winner.suit == CardSuit.SPADES -> false
        else -> card.suit == led && winner.suit == led && card.highRank > winner.highRank
    }

    override fun trickWinner(): Int {
        val led = trick[0].second.suit
        var win = trick[0]
        for (p in trick) if (beats(p.second, win.second, led)) win = p
        return win.first
    }

    override fun scoreHand() {
        val delta = IntArray(2)
        for (t in 0..1) {
            val a = t
            val b = t + 2
            val tricks = tricksWon[a] + tricksWon[b]
            var bid = 0
            for (s in intArrayOf(a, b)) {
                if (bids[s] == 0) {
                    delta[t] += if (tricksWon[s] == 0) NIL_BONUS else -NIL_BONUS
                } else {
                    bid += bids[s]
                }
            }
            if (bid > 0) {
                if (tricks >= bid) {
                    val extra = tricks - bid
                    delta[t] += 10 * bid + extra
                    teamBags[t] += extra
                    if (teamBags[t] >= 10) { teamBags[t] -= 10; delta[t] -= 100 }
                } else delta[t] -= 10 * bid
            }
        }
        lastHand = delta
        for (t in 0..1) teamScore[t] += delta[t]
    }

    override fun isGameOver(): Boolean {
        val top = teamScore.max()
        return (top >= limit && teamScore[0] != teamScore[1]) || teamScore.min() <= LOSS || handNo + 1 >= MAX_HANDS
    }

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        if (bidding) return BID_BASE + chooseBid(level, random)
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        return when (level) {
            CardLevel.EASY -> moves[random.nextInt(moves.size)]
            CardLevel.MEDIUM -> heuristicPlay(moves)
            else -> monteCarloMove(moves, SAMPLES, random, preferred = heuristicPlay(moves), margin = MARGIN)
        }
    }

    /** Combien de plis cette main devrait faire : les as et les rois qui passent, les piques longs, les coupes. */
    private fun estimateTricks(hand: List<PlayingCard>): Double {
        val ns = hand.count { it.suit == CardSuit.SPADES }
        var t = 0.0
        for (c in hand) {
            val len = hand.count { it.suit == c.suit }
            t += if (c.suit == CardSuit.SPADES) when (c.highRank) {
                14 -> 1.0
                13 -> if (ns >= 2) 0.9 else 0.4
                12 -> if (ns >= 3) 0.7 else 0.25
                11 -> if (ns >= 4) 0.45 else 0.1
                else -> 0.0
            } else when (c.highRank) {
                14 -> 0.85
                13 -> if (len in 2..4) 0.55 else if (len >= 5) 0.35 else 0.15
                12 -> if (len in 3..4) 0.2 else 0.0
                else -> 0.0
            }
        }
        if (ns > 4) t += (ns - 4) * 0.8
        for (suit in CardSuit.entries) {
            if (suit == CardSuit.SPADES) continue
            when (hand.count { it.suit == suit }) {
                0 -> t += minOf(ns, 3) * 0.45
                1 -> t += minOf(maxOf(ns - 1, 0), 2) * 0.25
            }
        }
        return t
    }

    private fun chooseBid(level: Int, random: Random): Int {
        val hand = hands[turn]
        val t = estimateTricks(hand)
        val weakSpades = hand.none { it.suit == CardSuit.SPADES && it.highRank >= 11 }
        if (level != CardLevel.EASY && t < 0.9 && weakSpades && hand.none { it.highRank == 14 }) return 0
        var bid = Math.round(t * 1.25).toInt()
        if (level == CardLevel.EASY) bid += random.nextInt(3) - 1
        return bid.coerceIn(1, 13)
    }

    private fun strength(c: PlayingCard) = if (c.suit == CardSuit.SPADES) 100 + c.highRank else c.highRank

    /**
     * La règle simple. Sur un nil, on se faufile sous le pli. Pour un partenaire en nil, on prend le
     * pli à sa place. Sinon : prendre le pli au moindre prix, laisser passer quand le partenaire le
     * tient ou que le contrat de l'équipe est déjà rempli (un pli de trop est un sac).
     */
    private fun heuristicPlay(moves: IntArray): Int {
        val legal = moves.map { PlayingCard(it) }
        val me = turn
        val partner = (me + 2) % 4
        val myNil = bids[me] == 0
        if (trick.isEmpty()) return lead(legal, myNil).id

        val led = trick[0].second.suit
        var win = trick[0]
        for (p in trick) if (beats(p.second, win.second, led)) win = p
        val canWin = legal.filter { beats(it, win.second, led) }.sortedBy { strength(it) }
        val cannot = legal.filter { !beats(it, win.second, led) }
        if (myNil) return (cannot.maxByOrNull { strength(it) } ?: legal.minByOrNull { strength(it) }!!).id

        val partnerNil = bids[partner] == 0
        if (partnerNil && win.first == partner && canWin.isNotEmpty()) return canWin.first().id

        val team = me % 2
        val bid = bids[team].coerceAtLeast(0) + bids[team + 2].coerceAtLeast(0)
        val made = bid > 0 && tricksWon[team] + tricksWon[team + 2] >= bid
        if (win.first == partner && !partnerNil) return legal.minByOrNull { strength(it) }!!.id
        if (made) return (cannot.maxByOrNull { strength(it) } ?: canWin.last()).id
        return (canWin.firstOrNull() ?: legal.minByOrNull { strength(it) }!!).id
    }

    private fun lead(legal: List<PlayingCard>, myNil: Boolean): PlayingCard {
        if (myNil) return legal.minByOrNull { strength(it) }!!
        val hand = hands[turn]
        legal.filter { it.suit != CardSuit.SPADES && it.highRank == 14 }
            .maxByOrNull { c -> hand.count { it.suit == c.suit } }?.let { return it }
        val spades = legal.filter { it.suit == CardSuit.SPADES }
        if (spadesBroken && spades.size >= 3 && spades.any { it.highRank >= 13 }) return spades.maxByOrNull { it.highRank }!!
        val others = legal.filter { it.suit != CardSuit.SPADES }
        if (others.isNotEmpty()) return others.minWithOrNull(compareBy({ it.highRank }, { c -> hand.count { it.suit == c.suit } }))!!
        return spades.minByOrNull { it.highRank }!!
    }

    override fun rolloutMove(random: Random): Int {
        if (bidding) return BID_BASE + chooseBid(CardLevel.MEDIUM, random)
        val moves = legalMoves()
        return if (moves.size == 1) moves[0] else heuristicPlay(moves)
    }

    /** Ce que vaut la manche simulée pour l'équipe de [seat] : l'écart de points avec l'autre, en plis. */
    override fun utility(seat: Int): Double {
        val t = seat % 2
        return (lastHand[t] - lastHand[1 - t]) / 10.0
    }

    override fun copy(): SpadesEngine {
        val e = SpadesEngine(seed, limit, false)
        copyBaseTo(e)
        e.bidding = bidding
        for (s in 0 until 4) e.bids[s] = bids[s]
        e.spadesBroken = spadesBroken
        for (t in 0..1) { e.teamScore[t] = teamScore[t]; e.teamBags[t] = teamBags[t] }
        e.lastHand = lastHand.copyOf()
        return e
    }

    /** Les plis annoncés par l'équipe de [seat] (nil non compté), pour l'affichage. */
    fun teamBid(seat: Int): Int = bids[seat % 2].coerceAtLeast(0) + bids[seat % 2 + 2].coerceAtLeast(0)

    fun teamTricks(seat: Int): Int = tricksWon[seat % 2] + tricksWon[seat % 2 + 2]

    companion object {
        /** Une manche aux mains et aux annonces imposées, pour les tests (les règles du jeu, le décompte). */
        internal fun forTest(hands: List<List<PlayingCard>>, bids: IntArray, leader: Int = 0): SpadesEngine {
            val e = SpadesEngine(0, DEFAULT_LIMIT, false)
            for (s in 0 until 4) { e.hands[s].clear(); e.hands[s].addAll(hands[s]); e.bids[s] = bids[s] }
            e.resetHandState()
            e.bidding = false
            e.leader = leader
            e.turn = leader
            return e
        }

        const val DEFAULT_LIMIT = 300
        const val BID_BASE = 100
        const val NO_BID = -1
        private const val NIL_BONUS = 100
        private const val LOSS = -200
        private const val MAX_HANDS = 30
        private const val SAMPLES = 40
        private const val MARGIN = 0.08
    }
}
