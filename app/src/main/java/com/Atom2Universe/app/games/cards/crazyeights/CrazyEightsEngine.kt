package com.Atom2Universe.app.games.cards.crazyeights

import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import kotlin.random.Random

/**
 * Huit américain (crazy_eights d'OpenSpiel). Quatre joueurs, cinq cartes chacun, une carte retournée
 * au milieu. On pose une carte de la même couleur ou de la même hauteur que celle du dessus ; un
 * 8 se pose sur n'importe quoi et son joueur choisit la couleur à suivre. Celui qui ne peut pas
 * jouer pioche, jusqu'à cinq fois ; ensuite il passe. Le premier qui vide sa main gagne la manche
 * et marque la valeur des cartes qui restent aux autres (8 = 50, figures et 10 = 10, As = 1, les
 * autres = leur chiffre).
 *
 * Une partie est une seule manche : courte, et qui se rejoue vite.
 *
 * Coups : une carte quelconque (son numéro, 0 à 51) ; un 8 s'écrit à part pour porter la couleur
 * choisie (`EIGHT_BASE + numéro × 4 + couleur`) ; [DRAW] ; [PASS].
 */
class CrazyEightsEngine private constructor(
    private val seed: Long,
    override val seats: Int,
    dealNow: Boolean,
) : CardEngine() {

    constructor(seed: Long, seats: Int = 4) : this(seed, seats, true)

    val hands: Array<MutableList<PlayingCard>> = Array(seats) { ArrayList() }
    private var stock = ArrayList<PlayingCard>()
    private var discard = ArrayList<PlayingCard>()

    /** La couleur à suivre : celle de la carte du dessus, ou celle qu'un 8 a choisie. */
    var activeSuit: CardSuit = CardSuit.CLUBS
        private set
    private var turn = 0
    private var draws = 0
    private var passesInRow = 0
    private var reshuffles = 0
    var winner = -1
        private set

    /** Les points que la manche rapporte à son vainqueur. */
    var roundPoints = 0
        private set

    init { if (dealNow) deal() }

    override val current: Int get() = if (winner >= 0) -1 else turn

    val stockSize: Int get() = stock.size
    val discardSize: Int get() = discard.size
    val topCard: PlayingCard get() = discard.last()

    /** Les cartes qui restent en main, en points : plus c'est bas, mieux c'est. */
    override val scores: IntArray get() = IntArray(seats) { s -> if (s == winner) roundPoints else 0 }

    fun handValue(seat: Int): Int = hands[seat].sumOf { pointValue(it) }

    private fun deal() {
        val random = Random(seed)
        val deck = Cards.shuffled(Cards.standardDeck(), random)
        repeat(INITIAL_CARDS) { for (s in 0 until seats) hands[s].add(deck.removeAt(deck.size - 1)) }
        // La carte de départ ne doit pas être un 8 : les 8 retournés vont au fond de la pioche.
        val buried = ArrayList<PlayingCard>()
        var start = deck.removeAt(deck.size - 1)
        while (start.rank == EIGHT) { buried.add(start); start = deck.removeAt(deck.size - 1) }
        deck.addAll(0, buried)
        discard.add(start)
        activeSuit = start.suit
        stock = ArrayList(deck)
        turn = random.nextInt(seats)
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    private fun canPlay(card: PlayingCard): Boolean =
        card.rank == EIGHT || card.suit == activeSuit || card.rank == discard.last().rank

    private fun canDraw(): Boolean = draws < MAX_DRAWS && (stock.isNotEmpty() || discard.size > 1)

    override fun legalMoves(): IntArray {
        val out = ArrayList<Int>()
        for (c in hands[turn]) {
            if (!canPlay(c)) continue
            if (c.rank == EIGHT) for (suit in CardSuit.entries) out.add(eightMove(c, suit)) else out.add(c.id)
        }
        if (out.isEmpty()) out.add(if (canDraw()) DRAW else PASS)
        return out.toIntArray()
    }

    override fun apply(move: Int) {
        val seat = turn
        when {
            move == DRAW -> {
                if (stock.isEmpty()) reshuffleDiscard()
                val card = stock.removeAt(stock.size - 1)
                hands[seat].add(card)
                draws++
                emit(CardEvent.Draw(seat, if (seat == 0) card else null))
            }
            move == PASS -> {
                draws = 0
                passesInRow++
                if (passesInRow >= seats) finishBlocked() else turn = (turn + 1) % seats
            }
            else -> {
                val card: PlayingCard
                val suit: CardSuit
                if (move >= EIGHT_BASE) {
                    card = PlayingCard((move - EIGHT_BASE) / 4)
                    suit = CardSuit.entries[(move - EIGHT_BASE) % 4]
                } else {
                    card = PlayingCard(move)
                    suit = card.suit
                }
                hands[seat].remove(card)
                discard.add(card)
                activeSuit = suit
                draws = 0
                passesInRow = 0
                emit(CardEvent.Play(seat, card, toPile = true))
                if (hands[seat].isEmpty()) {
                    winner = seat
                    roundPoints = (0 until seats).filter { it != seat }.sumOf { handValue(it) }
                } else turn = (turn + 1) % seats
            }
        }
    }

    /** La pioche est vide : la défausse, sauf sa carte du dessus, est battue et devient la pioche. */
    private fun reshuffleDiscard() {
        val top = discard.removeAt(discard.size - 1)
        reshuffles++
        // Une graine qui ne dépend que de l'état : rejouer la partie refait le même mélange.
        stock = ArrayList(Cards.shuffled(discard, Random(seed * 1_000_003L + reshuffles)))
        discard = arrayListOf(top)
    }

    /** Tout le monde passe de suite : la manche est bloquée, et gagne celui qui a le moins de points en main. */
    private fun finishBlocked() {
        winner = (0 until seats).minByOrNull { handValue(it) }!!
        roundPoints = (0 until seats).filter { it != winner }.sumOf { handValue(it) } - handValue(winner)
        if (roundPoints < 0) roundPoints = 0
    }

    override fun winners(): Set<Int> = if (winner >= 0) setOf(winner) else emptySet()

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        return when (level) {
            CardLevel.EASY -> moves[random.nextInt(moves.size)]
            CardLevel.MEDIUM -> heuristicMove(moves)
            else -> monteCarloMove(moves, SAMPLES, random, preferred = heuristicMove(moves), margin = MARGIN)
        }
    }

    /**
     * La règle simple : on pose la carte qui laisse le plus de cartes jouables derrière elle (la
     * « souplesse » de la main : celles de la même couleur ou de la même hauteur, et les 8). Les 8
     * se gardent pour le moment où rien d'autre ne se joue, ou pour finir, et choisissent la couleur
     * dont on a le plus.
     */
    private fun heuristicMove(moves: IntArray): Int {
        val hand = hands[turn]
        var best = moves[0]
        var bestScore = Double.NEGATIVE_INFINITY
        for (m in moves) {
            if (m == DRAW || m == PASS) return m
            val card = cardOf(m)!!
            val suit = if (m >= EIGHT_BASE) CardSuit.entries[(m - EIGHT_BASE) % 4] else card.suit
            var flexibility = 0
            var rest = 0
            for (c in hand) {
                if (c == card) continue
                rest++
                if (c.rank == EIGHT || c.suit == suit || c.rank == card.rank) flexibility++
            }
            var score = flexibility * 10.0
            score += if (card.rank == EIGHT) (if (rest <= 1) 1000.0 else -200.0) else pointValue(card) * 0.1
            if (score > bestScore) { bestScore = score; best = m }
        }
        return best
    }

    override fun rolloutMove(random: Random): Int {
        val moves = legalMoves()
        return if (moves.size == 1) moves[0] else heuristicMove(moves)
    }

    override val simulationLimit: Int get() = 120

    /** Une simulation qui n'a pas fini : on regarde qui est le mieux placé (moins de cartes que les autres). */
    override fun utility(seat: Int): Double {
        // Perdre avec peu de cartes en main vaut mieux que perdre avec beaucoup : ça départage les coups.
        if (winner >= 0) return if (winner == seat) 1.0 else -0.5 - hands[seat].size / 20.0
        val others = (0 until seats).filter { it != seat }.map { hands[it].size }.average()
        return ((others - hands[seat].size) / 10.0).coerceIn(-0.9, 0.9)
    }

    override fun determinize(seat: Int, random: Random) {
        val known = BooleanArray(52)
        for (c in hands[seat]) known[c.id] = true
        for (c in discard) known[c.id] = true
        val unknown = (0 until 52).filter { !known[it] }.map { PlayingCard(it) }.toMutableList()
        unknown.shuffle(random)
        for (s in 0 until seats) {
            if (s == seat) continue
            val n = hands[s].size
            hands[s].clear()
            repeat(n) { hands[s].add(unknown.removeAt(unknown.size - 1)) }
        }
        stock = ArrayList(unknown)
    }

    override fun copy(): CrazyEightsEngine {
        val e = CrazyEightsEngine(seed, seats, false)
        for (s in 0 until seats) { e.hands[s].clear(); e.hands[s].addAll(hands[s]) }
        e.stock = ArrayList(stock)
        e.discard = ArrayList(discard)
        e.activeSuit = activeSuit
        e.turn = turn
        e.draws = draws
        e.passesInRow = passesInRow
        e.reshuffles = reshuffles
        e.winner = winner
        e.roundPoints = roundPoints
        return e
    }

    companion object {
        const val DRAW = 500
        const val PASS = 501
        const val EIGHT_BASE = 1000
        const val EIGHT = 8
        const val INITIAL_CARDS = 5
        const val MAX_DRAWS = 5
        private const val SAMPLES = 60
        private const val MARGIN = 0.04

        fun eightMove(card: PlayingCard, suit: CardSuit) = EIGHT_BASE + card.id * 4 + suit.ordinal

        /** La carte jouée par ce coup, ou null pour piocher / passer. */
        fun cardOf(move: Int): PlayingCard? = when {
            move == DRAW || move == PASS -> null
            move >= EIGHT_BASE -> PlayingCard((move - EIGHT_BASE) / 4)
            else -> PlayingCard(move)
        }

        /** Ce que vaut une carte restée en main. */
        fun pointValue(card: PlayingCard): Int = when {
            card.rank == EIGHT -> 50
            card.rank >= 10 -> 10
            else -> card.rank
        }
    }
}
