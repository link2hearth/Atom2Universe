package com.Atom2Universe.app.games.cards.ohhell

import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.TrickEngine
import kotlin.math.abs
import kotlin.random.Random

/**
 * Oh Hell (oh_hell d'OpenSpiel). Quatre joueurs, chacun pour soi. Chaque manche donne moins de cartes
 * que la précédente (7, 6… jusqu'à 1) ; on retourne une carte du paquet : sa couleur est l'atout.
 *
 * Avant de jouer, chacun **annonce exactement** combien de plis il fera, à tour de rôle. Le dernier
 * à parler ne peut pas annoncer le nombre qui ferait coïncider le total des annonces avec le nombre
 * de plis : il y aura forcément quelqu'un qui se trompe (c'est « le crochet »).
 *
 * On fournit la couleur demandée si on le peut ; sinon on joue ce qu'on veut, et l'atout bat tout. Le
 * premier pli est ouvert par le joueur qui a parlé le premier.
 *
 * Points d'une manche : 10 plus l'annonce si on fait **exactement** son annonce, rien sinon. Qui a le
 * plus de points après la dernière manche gagne.
 *
 * Coups : une annonce (`BID_BASE + n`) puis des cartes (leur numéro).
 */
class OhHellEngine private constructor(
    seed: Long,
    dealNow: Boolean,
) : TrickEngine(seed, 4) {

    constructor(seed: Long) : this(seed, true)

    var bidding = true
        private set

    /** Ce que chaque siège a annoncé, ou [NO_BID] tant qu'il n'a pas parlé. */
    val bids = IntArray(4) { NO_BID }

    /** La carte retournée : sa couleur est l'atout. */
    var trumpCard: PlayingCard = PlayingCard(0)
        private set

    val trump: CardSuit get() = trumpCard.suit

    /** Les points de toutes les manches finies. */
    val totals = IntArray(4)

    /** Ce que la dernière manche finie a rapporté à chaque siège. */
    var lastHand = IntArray(4)
        private set

    /** Le nombre de cartes données à chacun dans cette manche. */
    val handSize: Int get() = HAND_SIZES[handNo.coerceAtMost(HAND_SIZES.size - 1)]

    /** Les manches de la partie. */
    val handCount: Int get() = HAND_SIZES.size

    /** Ce qui reste du paquet après la donne (sans la carte retournée). */
    val stockSize: Int get() = 51 - 4 * handSize

    override val tricksInHand: Int get() = handSize

    init { if (dealNow) dealHand() }

    override val scores: IntArray get() = totals.copyOf()

    override fun winners(): Set<Int> {
        val best = totals.max()
        return (0 until 4).filter { totals[it] == best }.toSet()
    }

    override fun dealHand() {
        val size = HAND_SIZES[handNo]
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed * 8_191L + handNo))
        for (s in 0 until 4) { hands[s].clear(); hands[s].addAll(deck.subList(s * size, s * size + size)) }
        trumpCard = deck[4 * size]
        resetHandState()
        played[trumpCard.id] = true
        bids.fill(NO_BID)
        bidding = true
        leader = handNo % 4
        turn = leader
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray {
        if (bidding) {
            val all = (0..handSize).toMutableList()
            // Le dernier à annoncer ne peut pas égaliser le total et le nombre de plis.
            if (bids.count { it != NO_BID } == 3) all.remove(handSize - bids.filter { it != NO_BID }.sum())
            return all.map { BID_BASE + it }.toIntArray()
        }
        return followable(hands[turn]).map { it.id }.toIntArray()
    }

    override fun apply(move: Int) {
        if (bidding) {
            bids[turn] = move - BID_BASE
            if (bids.none { it == NO_BID }) { bidding = false; turn = leader } else turn = nextSeat(turn)
            return
        }
        playCard(turn, PlayingCard(move))
    }

    private fun beats(card: PlayingCard, winner: PlayingCard, led: CardSuit): Boolean = when {
        card.suit == trump -> winner.suit != trump || card.highRank > winner.highRank
        winner.suit == trump -> false
        else -> card.suit == led && winner.suit == led && card.highRank > winner.highRank
    }

    override fun trickWinner(): Int {
        val led = trick[0].second.suit
        var win = trick[0]
        for (p in trick) if (beats(p.second, win.second, led)) win = p
        return win.first
    }

    override fun scoreHand() {
        val delta = IntArray(4) { if (tricksWon[it] == bids[it]) 10 + bids[it] else 0 }
        lastHand = delta
        for (s in 0 until 4) totals[s] += delta[s]
    }

    override fun isGameOver(): Boolean = handNo + 1 >= HAND_SIZES.size

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        if (bidding) {
            val liked = heuristicBid(moves, if (level == CardLevel.EASY) random else null)
            return when (level) {
                CardLevel.HARD -> monteCarloMove(moves, BID_SAMPLES, random, preferred = liked, margin = MARGIN)
                else -> liked
            }
        }
        return when (level) {
            CardLevel.EASY -> moves[random.nextInt(moves.size)]
            CardLevel.MEDIUM -> heuristicPlay(moves)
            else -> monteCarloMove(moves, SAMPLES, random, preferred = heuristicPlay(moves), margin = MARGIN)
        }
    }

    /** Combien de plis cette main devrait faire : les atouts hauts, les as et les rois des autres couleurs. */
    private fun estimate(hand: List<PlayingCard>): Double {
        var t = 0.0
        for (c in hand) {
            t += if (c.suit == trump) 0.15 + 0.8 * (c.highRank - 2) / 12.0 else when (c.highRank) {
                14 -> 0.65
                13 -> 0.35
                12 -> 0.15
                else -> 0.03
            }
        }
        return t * 1.1
    }

    /** L'annonce la plus proche de l'estimation parmi les annonces permises ([noise] : un joueur qui se trompe). */
    private fun heuristicBid(moves: IntArray, noise: Random?): Int {
        var target = estimate(hands[turn])
        if (noise != null) target += noise.nextDouble() * 2.0 - 1.0
        return moves.minByOrNull { abs(it - BID_BASE - target) }!!
    }

    private fun strength(c: PlayingCard) = if (c.suit == trump) 100 + c.highRank else c.highRank

    /**
     * La règle simple : tant qu'il manque des plis à son annonce, on cherche à les prendre au moindre
     * prix ; quand elle est faite, on se faufile sous chaque pli et on se débarrasse des cartes fortes.
     */
    private fun heuristicPlay(moves: IntArray): Int {
        val legal = moves.map { PlayingCard(it) }
        val need = bids[turn] - tricksWon[turn]
        if (trick.isEmpty()) {
            return (if (need > 0) legal.maxByOrNull { strength(it) } else legal.minByOrNull { strength(it) })!!.id
        }
        val led = trick[0].second.suit
        var win = trick[0]
        for (p in trick) if (beats(p.second, win.second, led)) win = p
        val winners = legal.filter { beats(it, win.second, led) }.sortedBy { strength(it) }
        val losers = legal.filter { !beats(it, win.second, led) }
        if (need > 0) return (winners.firstOrNull() ?: legal.minByOrNull { strength(it) }!!).id
        return (losers.maxByOrNull { strength(it) } ?: winners.first()).id
    }

    override fun rolloutMove(random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        return if (bidding) heuristicBid(moves, null) else heuristicPlay(moves)
    }

    override fun utility(seat: Int): Double = lastHand[seat] / 10.0

    override fun copy(): OhHellEngine {
        val e = OhHellEngine(seed, false)
        copyBaseTo(e)
        e.bidding = bidding
        for (s in 0 until 4) { e.bids[s] = bids[s]; e.totals[s] = totals[s] }
        e.trumpCard = trumpCard
        e.lastHand = lastHand.copyOf()
        return e
    }

    companion object {
        /** Une manche aux mains, à l'atout et aux annonces imposés, pour les tests. */
        internal fun forTest(hands: List<List<PlayingCard>>, trumpCard: PlayingCard, bids: IntArray, leader: Int = 0): OhHellEngine {
            val e = OhHellEngine(0, false)
            for (s in 0 until 4) { e.hands[s].clear(); e.hands[s].addAll(hands[s]); e.bids[s] = bids[s] }
            e.resetHandState()
            e.trumpCard = trumpCard
            e.played[trumpCard.id] = true
            e.handNo = HAND_SIZES.size - 1 - (hands[0].size - 1)
            e.bidding = false
            e.leader = leader
            e.turn = leader
            return e
        }

        const val BID_BASE = 100
        const val NO_BID = -1
        val HAND_SIZES = intArrayOf(7, 6, 5, 4, 3, 2, 1)
        private const val SAMPLES = 30
        private const val BID_SAMPLES = 24
        private const val MARGIN = 0.1
    }
}
