package com.Atom2Universe.app.games.cards.hearts

import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import kotlin.random.Random

/**
 * Dame de pique (hearts d'OpenSpiel). Quatre joueurs, treize cartes chacun. Il s'agit d'éviter les
 * points : chaque cœur vaut 1, la dame de pique 13. Celui qui les ramasse tous (les 26 points) les
 * donne à tous les autres et ne prend rien : c'est « prendre la lune ».
 *
 * Une manche, c'est : le **passage** de trois cartes (à gauche, à droite, en face, puis pas de
 * passage, et on recommence), puis treize plis. Le 2 de trèfle ouvre le premier ; on doit fournir la
 * couleur demandée ; on ne peut pas demander cœur tant qu'aucun cœur n'est tombé (sauf main de
 * cœurs) ; et on ne se débarrasse pas de points au premier pli sans y être obligé. Le plus fort de
 * la couleur demandée ramasse le pli, et ouvre le suivant.
 *
 * La partie s'arrête quand quelqu'un atteint [limit] points ; le moins chargé gagne.
 *
 * Coups : une carte (son numéro) ; au passage, trois cartes d'un coup (`PASS_BASE + …`, voir [passMove]).
 */
class HeartsEngine private constructor(
    private val seed: Long,
    val limit: Int,
    dealNow: Boolean,
) : CardEngine() {

    constructor(seed: Long, limit: Int = DEFAULT_LIMIT) : this(seed, limit, true)

    override val seats: Int get() = 4

    val hands: Array<MutableList<PlayingCard>> = Array(4) { ArrayList() }

    /** Le numéro de la manche en cours, à partir de 0. */
    var handNo = 0
        private set

    /** Vrai pendant le passage des cartes, avant les plis. */
    var passing = false
        private set

    private val chosenPass: Array<List<PlayingCard>?> = arrayOfNulls(4)

    /** Les cartes qu'on vient de recevoir au passage (pour les montrer au joueur). */
    var received: Set<PlayingCard> = emptySet()
        private set

    private var turn = 0
    private var leader = 0

    /** Le nombre de plis déjà ramassés dans cette manche. */
    var trickNo = 0
        private set
    var heartsBroken = false
        private set

    /** Le pli en cours : qui a posé quoi, dans l'ordre. */
    val trick = ArrayList<Pair<Int, PlayingCard>>()

    /** Les points ramassés dans cette manche, avant l'éventuelle « lune ». */
    private val handPoints = IntArray(4)

    /** Les points de toutes les manches finies. */
    val totals = IntArray(4)

    /** Ce que la dernière manche finie a rapporté à chaque siège (lune comprise). */
    var lastHand = IntArray(4)
        private set

    /** Qui a pris la lune à la dernière manche, ou -1. */
    var moonShooter = -1
        private set

    private val played = BooleanArray(52)

    /** Qui ne peut plus avoir de cette couleur (il n'a pas fourni) : [seat][couleur]. */
    private val void = Array(4) { BooleanArray(4) }
    private var over = false

    /** Une copie de simulation s'arrête à la fin de la manche au lieu d'en distribuer une autre. */
    private var frozen = false
    private var handDone = false

    init { if (dealNow) dealHand() }

    override val current: Int get() = if (over || (frozen && handDone)) -1 else turn

    override val scores: IntArray get() = totals.copyOf()

    /** Où vont les cartes passées : +1 à gauche (le siège suivant), -1 à droite, 2 en face, 0 pas de passage. */
    val passShift: Int get() = PASS_SHIFTS[handNo % 4]

    private fun dealHand() {
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed * 7_919L + handNo))
        for (s in 0 until 4) { hands[s].clear(); hands[s].addAll(deck.subList(s * 13, s * 13 + 13)) }
        trick.clear()
        handPoints.fill(0)
        played.fill(false)
        for (v in void) v.fill(false)
        heartsBroken = false
        trickNo = 0
        handDone = false
        received = emptySet()
        chosenPass.fill(null)
        if (passShift == 0) startPlay() else { passing = true; turn = 0 }
    }

    private fun startPlay() {
        passing = false
        leader = (0 until 4).first { PlayingCard(TWO_OF_CLUBS) in hands[it] }
        turn = leader
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray {
        val hand = hands[turn]
        if (passing) {
            val out = ArrayList<Int>()
            val ids = hand.map { it.id }.sorted()
            for (a in 0 until ids.size - 2) for (b in a + 1 until ids.size - 1) for (c in b + 1 until ids.size)
                out.add(PASS_BASE + (ids[a] * 52 + ids[b]) * 52 + ids[c])
            return out.toIntArray()
        }
        return playable(hand).map { it.id }.toIntArray()
    }

    private fun playable(hand: List<PlayingCard>): List<PlayingCard> {
        if (trick.isEmpty()) {
            if (trickNo == 0) return listOf(PlayingCard(TWO_OF_CLUBS))
            if (!heartsBroken) hand.filter { it.suit != CardSuit.HEARTS }.takeIf { it.isNotEmpty() }?.let { return it }
            return hand
        }
        val led = trick[0].second.suit
        val follow = hand.filter { it.suit == led }
        if (follow.isNotEmpty()) return follow
        if (trickNo == 0) hand.filter { !isPoint(it) }.takeIf { it.isNotEmpty() }?.let { return it }
        return hand
    }

    override fun apply(move: Int) {
        if (passing) { applyPass(move); return }
        val seat = turn
        val card = PlayingCard(move)
        hands[seat].remove(card)
        played[card.id] = true
        if (trick.isNotEmpty() && card.suit != trick[0].second.suit) void[seat][trick[0].second.suit.ordinal] = true
        if (card.suit == CardSuit.HEARTS) heartsBroken = true
        trick.add(seat to card)
        emit(CardEvent.Play(seat, card, toPile = false))
        if (trick.size < 4) { turn = (turn + 1) % 4; return }

        // Le pli est complet : le plus fort de la couleur demandée le ramasse et ouvre le suivant.
        val led = trick[0].second.suit
        val winner = trick.filter { it.second.suit == led }.maxByOrNull { it.second.highRank }!!.first
        handPoints[winner] += trick.sumOf { points(it.second) }
        trick.clear()
        emit(CardEvent.Take(winner))
        trickNo++
        leader = winner
        turn = winner
        if (trickNo == 13) endHand()
    }

    private fun applyPass(move: Int) {
        val code = move - PASS_BASE
        chosenPass[turn] = listOf(PlayingCard(code / (52 * 52)), PlayingCard(code / 52 % 52), PlayingCard(code % 52))
        if (turn < 3) { turn++; return }
        // Les quatre ont choisi : chacun reçoit les trois cartes de son voisin.
        val incoming = Array(4) { emptyList<PlayingCard>() }
        for (s in 0 until 4) {
            val to = Math.floorMod(s + passShift, 4)
            incoming[to] = chosenPass[s]!!
            hands[s].removeAll(chosenPass[s]!!.toSet())
        }
        for (s in 0 until 4) hands[s].addAll(incoming[s])
        received = incoming[0].toSet()
        startPlay()
    }

    private fun endHand() {
        val delta = handPoints.copyOf()
        moonShooter = (0 until 4).firstOrNull { handPoints[it] == 26 } ?: -1
        if (moonShooter >= 0) for (s in 0 until 4) delta[s] = if (s == moonShooter) 0 else 26
        lastHand = delta
        for (s in 0 until 4) totals[s] += delta[s]
        handDone = true
        if (frozen) return
        if (totals.any { it >= limit }) { over = true; return }
        handNo++
        dealHand()
    }

    override fun winners(): Set<Int> {
        val best = totals.min()
        return (0 until 4).filter { totals[it] == best }.toSet()
    }

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        if (passing) {
            if (level == CardLevel.EASY) return moves[random.nextInt(moves.size)]
            return passMove(choosePass(hands[turn]))
        }
        return when (level) {
            CardLevel.EASY -> moves[random.nextInt(moves.size)]
            CardLevel.MEDIUM -> heuristicPlay(moves, random)
            else -> monteCarloMove(moves, SAMPLES, random, preferred = heuristicPlay(moves, random), margin = MARGIN)
        }
    }

    /** Les trois cartes dont on se défait : la dame de pique et les gros piques si on les garde mal, les gros cœurs, les plus hautes. */
    private fun choosePass(hand: List<PlayingCard>): List<PlayingCard> {
        val spades = hand.count { it.suit == CardSuit.SPADES }
        fun danger(c: PlayingCard): Int = when {
            c == QUEEN_OF_SPADES -> if (spades <= 4) 200 else 40
            c.suit == CardSuit.SPADES && c.highRank >= 13 -> if (spades <= 4) 150 else 10
            c.suit == CardSuit.HEARTS -> 30 + c.highRank * 3
            else -> c.highRank
        }
        return hand.sortedByDescending { danger(it) }.take(3)
    }

    /**
     * La règle simple. À l'ouverture : une petite carte d'une couleur sans danger. En suivant : la
     * plus grosse carte qui ne prend pas le pli, sinon la plus petite. Sans la couleur : on se
     * défait de la dame de pique, des gros piques, des gros cœurs.
     */
    private fun heuristicPlay(moves: IntArray, random: Random): Int {
        val legal = moves.map { PlayingCard(it) }
        if (trick.isEmpty()) return lead(legal, random).id
        val led = trick[0].second.suit
        if (legal.all { it.suit == led }) return follow(legal, led).id
        return discard(legal).id
    }

    private fun lead(legal: List<PlayingCard>, random: Random): PlayingCard {
        val hand = hands[turn]
        val queenOut = played[QUEEN_OF_SPADES.id]
        val holdsHighSpade = hand.any { it.suit == CardSuit.SPADES && it.highRank >= 12 }
        fun score(c: PlayingCard): Double {
            var s = c.highRank.toDouble()
            when (c.suit) {
                CardSuit.SPADES -> s += if (!queenOut && holdsHighSpade) 40 else if (!queenOut) -3 else 0
                CardSuit.HEARTS -> s += 20
                else -> {}
            }
            return s + random.nextDouble() * 0.5
        }
        return legal.minByOrNull { score(it) }!!
    }

    private fun follow(legal: List<PlayingCard>, led: CardSuit): PlayingCard {
        val winning = trick.filter { it.second.suit == led }.maxOf { it.second.highRank }
        val lower = legal.filter { it.highRank < winning }
        if (lower.isNotEmpty()) return lower.maxByOrNull { it.highRank }!!
        // On prendra le pli : sans la dame de pique si on peut l'éviter.
        val safe = legal.filter { it != QUEEN_OF_SPADES }.ifEmpty { legal }
        val lastToPlay = trick.size == 3
        val trickPoints = trick.sumOf { points(it.second) }
        return if (lastToPlay && trickPoints == 0) safe.maxByOrNull { it.highRank }!! else safe.minByOrNull { it.highRank }!!
    }

    private fun discard(legal: List<PlayingCard>): PlayingCard {
        val hand = hands[turn]
        val counts = IntArray(4)
        for (c in hand) counts[c.suit.ordinal]++
        val queenOut = played[QUEEN_OF_SPADES.id]
        fun score(c: PlayingCard): Int = when {
            c == QUEEN_OF_SPADES -> 1000
            c.suit == CardSuit.SPADES && c.highRank >= 13 && !queenOut -> 500 + c.highRank
            c.suit == CardSuit.HEARTS -> 300 + c.highRank
            else -> c.highRank * 3 + (4 - counts[c.suit.ordinal].coerceAtMost(4)) * 10
        }
        return legal.maxByOrNull { score(it) }!!
    }

    override fun rolloutMove(random: Random): Int {
        val moves = legalMoves()
        return if (moves.size == 1) moves[0] else heuristicPlay(moves, random)
    }

    override fun simulationDone(): Boolean = handDone || over
    override val simulationLimit: Int get() = 60

    /** Ce que vaut la manche simulée pour [seat] : peu de points pour lui, un peu plus pour les autres. */
    override fun utility(seat: Int): Double {
        val mine = lastHand[seat]
        val others = (0 until 4).filter { it != seat }.map { lastHand[it] }.average()
        return -mine + 0.3 * others
    }

    override fun determinize(seat: Int, random: Random) {
        frozen = true
        val unseen = (0 until 52).filter { !played[it] && PlayingCard(it) !in hands[seat] }
            .map { PlayingCard(it) }
            // Les cartes déjà posées dans le pli en cours sont comptées comme jouées ci-dessus.
        val sizes = IntArray(4) { hands[it].size }
        repeat(8) { attempt ->
            val pool = unseen.toMutableList().also { it.shuffle(random) }
            val need = sizes.copyOf()
            val dealt = Array(4) { ArrayList<PlayingCard>() }
            var ok = true
            // Les couleurs rares d'abord : un siège sans cœur ne peut recevoir que d'autres couleurs.
            for (card in pool) {
                val eligible = (0 until 4).filter { s ->
                    s != seat && need[s] > 0 && (attempt >= 6 || !void[s][card.suit.ordinal])
                }
                if (eligible.isEmpty()) { ok = false; break }
                val to = eligible[random.nextInt(eligible.size)]
                dealt[to].add(card)
                need[to]--
            }
            if (ok) {
                for (s in 0 until 4) if (s != seat) { hands[s].clear(); hands[s].addAll(dealt[s]) }
                return
            }
        }
    }

    override fun copy(): HeartsEngine {
        val e = HeartsEngine(seed, limit, false)
        for (s in 0 until 4) { e.hands[s].clear(); e.hands[s].addAll(hands[s]) }
        e.handNo = handNo
        e.passing = passing
        for (s in 0 until 4) e.chosenPass[s] = chosenPass[s]
        e.received = received
        e.turn = turn
        e.leader = leader
        e.trickNo = trickNo
        e.heartsBroken = heartsBroken
        e.trick.addAll(trick)
        for (s in 0 until 4) { e.handPoints[s] = handPoints[s]; e.totals[s] = totals[s] }
        e.lastHand = lastHand.copyOf()
        e.moonShooter = moonShooter
        for (i in 0 until 52) e.played[i] = played[i]
        for (s in 0 until 4) for (c in 0 until 4) e.void[s][c] = void[s][c]
        e.over = over
        e.frozen = frozen
        e.handDone = handDone
        return e
    }

    /** Les points de la manche en cours (avant la lune) : pour l'affichage et les tests. */
    fun handPointsOf(seat: Int): Int = handPoints[seat]

    companion object {
        /**
         * Une manche aux mains imposées, sans passage de cartes : pour les tests, qui veulent
         * rejouer une situation précise (la lune, une règle du premier pli).
         */
        internal fun forTest(hands: List<List<PlayingCard>>, limit: Int = DEFAULT_LIMIT): HeartsEngine {
            val e = HeartsEngine(0, limit, false)
            for (s in 0 until 4) { e.hands[s].clear(); e.hands[s].addAll(hands[s]) }
            e.handNo = 3 // la quatrième manche d'un cycle : pas de passage
            e.startPlay()
            return e
        }

        const val DEFAULT_LIMIT = 50
        const val PASS_BASE = 10_000
        const val TWO_OF_CLUBS = 1 // trèfle (0 à 12), hauteur 2 : l'As est le numéro 0
        private const val SAMPLES = 40
        private const val MARGIN = 0.15
        private val PASS_SHIFTS = intArrayOf(1, -1, 2, 0)
        val QUEEN_OF_SPADES = PlayingCard.of(CardSuit.SPADES, PlayingCard.QUEEN)

        /** Un coup de passage : trois cartes, rangées par numéro. */
        fun passMove(cards: List<PlayingCard>): Int {
            val ids = cards.map { it.id }.sorted()
            require(ids.size == 3) { "il faut passer trois cartes" }
            return PASS_BASE + (ids[0] * 52 + ids[1]) * 52 + ids[2]
        }

        fun isPoint(card: PlayingCard) = card.suit == CardSuit.HEARTS || card == QUEEN_OF_SPADES

        fun points(card: PlayingCard) = when {
            card.suit == CardSuit.HEARTS -> 1
            card == QUEEN_OF_SPADES -> 13
            else -> 0
        }
    }
}
