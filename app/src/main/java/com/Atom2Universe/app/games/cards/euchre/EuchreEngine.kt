package com.Atom2Universe.app.games.cards.euchre

import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.TrickEngine
import kotlin.random.Random

/**
 * Euchre (euchre d'OpenSpiel). Quatre joueurs en deux équipes (toi et le siège d'en face), vingt-quatre
 * cartes : du 9 à l'as. Chacun reçoit cinq cartes ; une carte est retournée.
 *
 * **Choix de l'atout.** Au premier tour, chacun à son tour passe ou « prend » : la couleur de la carte
 * retournée devient l'atout, et le donneur la ramasse en écartant une carte. Si tout le monde passe,
 * au second tour on peut nommer n'importe quelle autre couleur ; le donneur est obligé d'en nommer une.
 * Celui qui fait l'atout peut jouer **seul** : son partenaire pose ses cartes, et gagner les cinq plis
 * rapporte alors 4 points.
 *
 * **Les valets** sont les cartes les plus fortes : le valet de l'atout (le « bower droit »), puis le
 * valet de la couleur de même couleur (le « bower gauche », qui compte comme un atout), puis l'as, le
 * roi, la dame, le dix, le neuf. On doit fournir la couleur demandée, bower gauche compris dans
 * l'atout.
 *
 * Points d'une manche : l'équipe qui a fait l'atout marque 1 point avec trois ou quatre plis, 2 avec
 * les cinq (4 si elle jouait seule) ; si elle en fait moins de trois, l'autre équipe marque 2. La
 * partie s'arrête à [limit] points.
 *
 * Coups : passer, prendre, prendre seul, nommer une couleur (seul ou non), écarter une carte, jouer
 * une carte (voir les constantes).
 */
class EuchreEngine private constructor(
    seed: Long,
    val limit: Int,
    dealNow: Boolean,
) : TrickEngine(seed, 4) {

    constructor(seed: Long, limit: Int = DEFAULT_LIMIT) : this(seed, limit, true)

    /** Où en est la manche : [ROUND1], [ROUND2], [DISCARD] ou [PLAY]. */
    var phase = ROUND1
        private set

    /** La carte retournée. */
    var upcard: PlayingCard = PlayingCard(0)
        private set

    var trump: CardSuit? = null
        private set

    /** Qui a fait l'atout, ou -1 tant qu'il n'est pas choisi. */
    var maker = -1
        private set

    var alone = false
        private set

    /** Le siège qui reste de côté quand quelqu'un joue seul, ou -1. */
    var sitting = -1
        private set

    val teamScore = IntArray(2)

    /** Ce que la dernière manche a rapporté à chaque équipe. */
    var lastHand = IntArray(2)
        private set

    val dealer: Int get() = (handNo + 3) % 4

    override val tricksInHand: Int get() = 5
    override val trickSize: Int get() = if (sitting >= 0) 3 else 4

    init { if (dealNow) dealHand() }

    override fun nextSeat(seat: Int): Int {
        var next = (seat + 1) % 4
        if (next == sitting) next = (next + 1) % 4
        return next
    }

    override fun inDeck(card: PlayingCard) = card.rank == PlayingCard.ACE || card.rank >= 9

    /** Le valet de la couleur de même couleur que [t] change de couleur : il compte comme un atout. */
    private fun sameColor(t: CardSuit) = CardSuit.entries[3 - t.ordinal]

    override fun suitOf(card: PlayingCard): CardSuit {
        val t = trump ?: return card.suit
        return if (card.rank == PlayingCard.JACK && card.suit == sameColor(t)) t else card.suit
    }

    /** La force d'une carte quand [t] est l'atout : l'atout l'emporte sur tout, les bowers sur l'atout. */
    private fun power(card: PlayingCard, t: CardSuit): Int = when {
        card.rank == PlayingCard.JACK && card.suit == t -> 300
        card.rank == PlayingCard.JACK && card.suit == sameColor(t) -> 299
        card.suit == t -> 200 + card.highRank
        else -> card.highRank
    }

    /** La couleur d'une carte pour le jeu (le bower gauche compte pour l'atout) : l'écran range la main avec. */
    fun effectiveSuit(card: PlayingCard): CardSuit = suitOf(card)

    /** La force d'une carte pour ranger la main (sans atout choisi : sa hauteur). */
    fun strengthOf(card: PlayingCard): Int = trump?.let { power(card, it) } ?: card.highRank

    override val scores: IntArray get() = IntArray(4) { teamScore[it % 2] }

    override fun winners(): Set<Int> {
        val best = teamScore.max()
        return (0 until 4).filter { teamScore[it % 2] == best }.toSet()
    }

    override fun dealHand() {
        val deck = Cards.shuffled(Cards.deckOf { it == PlayingCard.ACE || it >= 9 }, Random(seed * 4_099L + handNo))
        for (s in 0 until 4) { hands[s].clear(); hands[s].addAll(deck.subList(s * 5, s * 5 + 5)) }
        upcard = deck[20]
        resetHandState()
        played[upcard.id] = true
        phase = ROUND1
        trump = null
        maker = -1
        alone = false
        sitting = -1
        leader = handNo % 4
        turn = leader
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray = when (phase) {
        ROUND1 -> intArrayOf(PASS, ORDER, ORDER_ALONE)
        ROUND2 -> {
            val out = ArrayList<Int>()
            // Le donneur est obligé de nommer : il ne peut pas passer.
            if (turn != dealer) out.add(PASS)
            for (s in CardSuit.entries) if (s != upcard.suit) { out.add(NAME_BASE + s.ordinal); out.add(NAME_ALONE_BASE + s.ordinal) }
            out.toIntArray()
        }
        DISCARD -> hands[turn].map { it.id }.toIntArray()
        else -> followable(hands[turn]).map { it.id }.toIntArray()
    }

    override fun apply(move: Int) {
        when (phase) {
            ROUND1 -> when (move) {
                PASS -> if (turn == dealer) { phase = ROUND2; turn = leader } else turn = nextSeat(turn)
                else -> call(upcard.suit, turn, move == ORDER_ALONE)
            }
            ROUND2 -> when {
                move == PASS -> turn = nextSeat(turn)
                move >= NAME_ALONE_BASE -> call(CardSuit.entries[move - NAME_ALONE_BASE], turn, true)
                else -> call(CardSuit.entries[move - NAME_BASE], turn, false)
            }
            DISCARD -> {
                val card = PlayingCard(move)
                hands[turn].remove(card)
                played[card.id] = true
                startPlay()
            }
            else -> playCard(turn, PlayingCard(move))
        }
    }

    /** [seat] fait [suit] atout, seul ou non. Au premier tour, le donneur ramasse la carte retournée. */
    private fun call(suit: CardSuit, seat: Int, solo: Boolean) {
        trump = suit
        maker = seat
        alone = solo
        if (solo) {
            sitting = (seat + 2) % 4
            hands[sitting].clear()
        }
        // La couleur de l'atout est connue : les trousses d'avant (voids) ne portent que sur le jeu.
        if (phase == ROUND1 && sitting != dealer) {
            hands[dealer].add(upcard)
            known[dealer].add(upcard)
            played[upcard.id] = false
            emit(CardEvent.Draw(dealer, upcard, fromDiscard = true))
            phase = DISCARD
            turn = dealer
            return
        }
        startPlay()
    }

    private fun startPlay() {
        phase = PLAY
        leader = handNo % 4
        if (leader == sitting) leader = nextSeat(leader)
        turn = leader
    }

    /** Un nombre qui permet de comparer deux cartes d'un pli dont [led] est la couleur demandée. */
    private fun key(card: PlayingCard, led: CardSuit, t: CardSuit): Int = when {
        suitOf(card) == t -> 1000 + power(card, t)
        suitOf(card) == led -> card.highRank
        else -> 0
    }

    override fun trickWinner(): Int {
        val t = trump!!
        val led = suitOf(trick[0].second)
        return trick.maxByOrNull { key(it.second, led, t) }!!.first
    }

    override fun scoreHand() {
        val team = maker % 2
        val tricks = tricksWon[team] + tricksWon[team + 2]
        val delta = IntArray(2)
        if (tricks >= 3) delta[team] = if (tricks == 5) (if (alone) 4 else 2) else 1 else delta[1 - team] = 2
        lastHand = delta
        for (t in 0..1) teamScore[t] += delta[t]
    }

    override fun isGameOver(): Boolean = teamScore.max() >= limit

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        if (level == CardLevel.EASY && phase == PLAY) return moves[random.nextInt(moves.size)]
        val liked = heuristic(moves, if (level == CardLevel.EASY) random else null)
        return if (level == CardLevel.HARD) monteCarloMove(moves, SAMPLES, random, preferred = liked, margin = MARGIN) else liked
    }

    private fun heuristic(moves: IntArray, noise: Random?): Int = when (phase) {
        ROUND1, ROUND2 -> heuristicBid(moves, noise)
        DISCARD -> heuristicDiscard(moves)
        else -> heuristicPlay(moves)
    }

    /** Combien de plis cette main devrait faire (sur cinq) avec [t] pour atout. */
    private fun estimate(hand: List<PlayingCard>, t: CardSuit): Double {
        var e = 0.0
        var trumps = 0
        val joined = sameColor(t)
        for (c in hand) {
            when {
                c.rank == PlayingCard.JACK && c.suit == t -> { e += 1.0; trumps++ }
                c.rank == PlayingCard.JACK && c.suit == joined -> { e += 0.9; trumps++ }
                c.suit == t -> {
                    trumps++
                    e += when (c.highRank) { 14 -> 0.8; 13 -> 0.55; 12 -> 0.4; 10 -> 0.28; else -> 0.22 }
                }
                c.highRank == 14 -> e += 0.5
                c.highRank == 13 -> e += 0.12
            }
        }
        // Une couleur absente avec de l'atout en main : on pourra couper.
        if (trumps > 0) for (s in CardSuit.entries) {
            if (s == t) continue
            val has = hand.any { c -> c.suit == s && !(c.rank == PlayingCard.JACK && s == joined) }
            if (!has) e += 0.3
        }
        return e
    }

    private fun heuristicBid(moves: IntArray, noise: Random?): Int {
        val me = turn
        val partner = (me + 2) % 4
        val jitter = if (noise == null) 0.0 else noise.nextDouble() - 0.5
        if (phase == ROUND1) {
            val t = upcard.suit
            var est = if (me == dealer) estimate(hands[me] + upcard, t) - 0.15
                else estimate(hands[me], t) + if (partner == dealer) 0.35 else -0.3
            est += jitter
            return when {
                est >= ALONE_AT -> ORDER_ALONE
                est >= ORDER_AT -> ORDER
                else -> PASS
            }
        }
        // Second tour : la meilleure couleur parmi celles qu'on a le droit de nommer.
        val allowed = CardSuit.entries.filter { it != upcard.suit }
        val best = allowed.maxByOrNull { estimate(hands[me], it) }!!
        val est = estimate(hands[me], best) + jitter
        val mustName = me == dealer
        if (!mustName && est < NAME_AT) return PASS
        return if (est >= ALONE_AT) NAME_ALONE_BASE + best.ordinal else NAME_BASE + best.ordinal
    }

    /** Le donneur écarte de préférence une carte seule d'une couleur sans as : il pourra y couper. */
    private fun heuristicDiscard(moves: IntArray): Int {
        val t = trump!!
        val hand = moves.map { PlayingCard(it) }
        val offSuit = hand.filter { suitOf(it) != t }
        if (offSuit.isEmpty()) return hand.minByOrNull { power(it, t) }!!.id
        fun score(c: PlayingCard): Int {
            val same = offSuit.count { it.suit == c.suit }
            return c.highRank + (if (same == 1 && c.highRank != 14) -6 else 0)
        }
        return offSuit.minByOrNull { score(it) }!!.id
    }

    private fun heuristicPlay(moves: IntArray): Int {
        val legal = moves.map { PlayingCard(it) }
        val t = trump!!
        val me = turn
        val partner = (me + 2) % 4
        if (trick.isEmpty()) {
            val makerSide = maker % 2 == me % 2
            val trumps = legal.filter { suitOf(it) == t }
            if (makerSide && trumps.isNotEmpty() && (me == maker || trumps.size >= 2)) return trumps.maxByOrNull { power(it, t) }!!.id
            legal.firstOrNull { suitOf(it) != t && it.highRank == 14 }?.let { return it.id }
            return (legal.filter { suitOf(it) != t }.minByOrNull { power(it, t) } ?: legal.minByOrNull { power(it, t) }!!).id
        }
        val led = suitOf(trick[0].second)
        val win = trick.maxByOrNull { key(it.second, led, t) }!!
        if (win.first == partner) return legal.minByOrNull { power(it, t) }!!.id
        val beating = legal.filter { key(it, led, t) > key(win.second, led, t) }.sortedBy { key(it, led, t) }
        return (beating.firstOrNull() ?: legal.minByOrNull { power(it, t) }!!).id
    }

    override fun rolloutMove(random: Random): Int {
        val moves = legalMoves()
        return if (moves.size == 1) moves[0] else heuristic(moves, null)
    }

    /** Ce que vaut la manche simulée pour l'équipe de [seat] : l'écart de points avec l'autre. */
    override fun utility(seat: Int): Double {
        val t = seat % 2
        return (lastHand[t] - lastHand[1 - t]).toDouble()
    }

    override fun copy(): EuchreEngine {
        val e = EuchreEngine(seed, limit, false)
        copyBaseTo(e)
        e.phase = phase
        e.upcard = upcard
        e.trump = trump
        e.maker = maker
        e.alone = alone
        e.sitting = sitting
        for (t in 0..1) e.teamScore[t] = teamScore[t]
        e.lastHand = lastHand.copyOf()
        return e
    }

    companion object {
        /** Une manche déjà en jeu, aux mains et à l'atout imposés, pour les tests. */
        internal fun forTest(hands: List<List<PlayingCard>>, trump: CardSuit, maker: Int, alone: Boolean = false, leader: Int = 0): EuchreEngine {
            val e = EuchreEngine(0, DEFAULT_LIMIT, false)
            for (s in 0 until 4) { e.hands[s].clear(); e.hands[s].addAll(hands[s]) }
            e.resetHandState()
            e.trump = trump
            e.maker = maker
            e.alone = alone
            if (alone) { e.sitting = (maker + 2) % 4; e.hands[e.sitting].clear() }
            e.phase = PLAY
            e.leader = leader
            e.turn = leader
            return e
        }

        const val DEFAULT_LIMIT = 10

        const val ROUND1 = 0
        const val ROUND2 = 1
        const val DISCARD = 2
        const val PLAY = 3

        const val PASS = 500
        const val ORDER = 501
        const val ORDER_ALONE = 502
        const val NAME_BASE = 510
        const val NAME_ALONE_BASE = 520

        private const val ORDER_AT = 2.15
        private const val NAME_AT = 2.0
        private const val ALONE_AT = 4.0
        private const val SAMPLES = 40
        private const val MARGIN = 0.05
    }
}
