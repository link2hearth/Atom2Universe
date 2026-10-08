package com.Atom2Universe.app.games.cards.gofish

import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import kotlin.random.Random

/**
 * La Pêche (go_fish d'OpenSpiel), à trois joueurs. On forme des **carrés** : quatre cartes de la même
 * hauteur. Chacun commence avec 7 cartes.
 *
 * À son tour, on choisit une hauteur qu'on a en main et on la demande à un autre joueur. S'il en a,
 * il les donne toutes et on rejoue. Sinon c'est « Pêche ! » : on pioche une carte, et on ne rejoue que
 * si elle est de la hauteur demandée. Un carré complet est posé aussitôt. Un joueur sans carte pioche
 * une carte au début de son tour. La partie s'arrête quand les treize carrés sont faits ; le plus
 * de carrés gagne (égalité possible).
 *
 * Ce qu'on demande est public : un joueur qui a demandé un 7 en a forcément un, tant qu'il ne l'a pas
 * donné. Les adversaires malins s'en souviennent.
 *
 * Coup : `cible × 13 + hauteur − 1` (la cible est un siège, la hauteur de 1 à 13).
 */
class GoFishEngine private constructor(
    private val seed: Long,
    dealNow: Boolean,
) : CardEngine() {

    constructor(seed: Long) : this(seed, true)

    override val seats: Int get() = 3

    val hands: Array<MutableList<PlayingCard>> = Array(3) { ArrayList() }

    /** La pioche : la carte du dessus est la dernière. */
    val stock = ArrayList<PlayingCard>()

    /** Les carrés posés par chaque siège. */
    val books = IntArray(3)

    private var turn = 0
    private var over = false

    /** Les hauteurs qu'un siège a demandées et doit donc encore avoir : [siège][hauteur 1 à 13]. */
    private val holds = Array(3) { BooleanArray(14) }

    /** La dernière hauteur demandée par chaque siège : un joueur moyen ne se souvient que de celle-là. */
    private val lastAsked = IntArray(3)

    // ── Ce qui vient de se passer, pour l'affichage ─────────────────────────────────────────

    var lastAsker = -1
        private set
    var lastTarget = -1
        private set
    var lastRank = 0
        private set

    /** Combien de cartes le dernier demandé a données (0 : « Pêche »). */
    var lastGiven = 0
        private set

    init { if (dealNow) deal() }

    override val current: Int get() = if (over) -1 else turn

    override val scores: IntArray get() = books.copyOf()

    override fun winners(): Set<Int> {
        val best = books.max()
        return (0 until 3).filter { books[it] == best }.toSet()
    }

    private fun deal() {
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed))
        for (s in 0 until 3) hands[s].addAll(deck.subList(s * 7, s * 7 + 7))
        stock.addAll(deck.subList(21, 52))
        for (s in 0 until 3) layBooks(s)
        settleTurn()
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray {
        val ranks = hands[turn].map { it.rank }.distinct()
        val out = ArrayList<Int>()
        for (target in 0 until 3) {
            if (target == turn || hands[target].isEmpty()) continue
            for (r in ranks) out.add(target * 13 + r - 1)
        }
        return out.toIntArray()
    }

    override fun apply(move: Int) {
        val seat = turn
        val target = move / 13
        val rank = move % 13 + 1
        lastAsker = seat
        lastTarget = target
        lastRank = rank
        holds[seat][rank] = true
        lastAsked[seat] = rank

        val matching = hands[target].filter { it.rank == rank }
        var again = false
        if (matching.isNotEmpty()) {
            hands[target].removeAll(matching.toSet())
            hands[seat].addAll(matching)
            holds[target][rank] = false
            lastGiven = matching.size
            emit(CardEvent.Give(target, seat, matching))
            again = true
        } else {
            lastGiven = 0
            if (stock.isNotEmpty()) {
                val card = stock.removeAt(stock.size - 1)
                hands[seat].add(card)
                emit(CardEvent.Draw(seat, if (seat == 0) card else null))
                again = card.rank == rank
            }
        }
        layBooks(seat)
        if (books.sum() == 13) { over = true; return }
        // On rejoue (en piochant si on n'a plus de carte) ; sinon la main passe au siège suivant.
        settleTurn(if (again) seat else nextSeatFrom(seat))
    }

    /** Pose les carrés complets de [seat]. */
    private fun layBooks(seat: Int) {
        for (rank in 1..13) {
            val same = hands[seat].filter { it.rank == rank }
            if (same.size == 4) {
                hands[seat].removeAll(same.toSet())
                books[seat]++
            }
        }
        for (rank in 1..13) if (holds[seat][rank] && hands[seat].none { it.rank == rank }) holds[seat][rank] = false
    }

    private fun nextSeatFrom(seat: Int) = (seat + 1) % 3

    /**
     * Donne la main à [from] — ou au premier siège suivant qui peut jouer. Quelqu'un sans carte pioche
     * une carte ; s'il n'y a plus de pioche, il passe. Si personne ne peut jouer, la partie est finie.
     */
    private fun settleTurn(from: Int = 0) {
        for (i in 0 until 3) {
            val s = (from + i) % 3
            if (hands[s].isEmpty() && stock.isNotEmpty()) {
                val card = stock.removeAt(stock.size - 1)
                hands[s].add(card)
                emit(CardEvent.Draw(s, if (s == 0) card else null))
                layBooks(s)
                if (books.sum() == 13) { over = true; return }
            }
            if (hands[s].isNotEmpty() && (0 until 3).any { it != s && hands[it].isNotEmpty() }) {
                turn = s
                return
            }
        }
        over = true
    }

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    /**
     * Facile : au hasard. Moyen : il se souvient de la dernière demande de chaque joueur (qui a
     * demandé un 9 en a un) ; sinon il demande ce qui a le plus de chances de lui rapporter des cartes.
     * Difficile : il retient toutes les demandes, et un carré à finir pèse plus lourd.
     */
    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1 || level == CardLevel.EASY) return moves[random.nextInt(moves.size)]
        val hand = hands[turn]
        val unseen = (0 until 3).sumOf { if (it == turn) 0 else hands[it].size } + stock.size
        fun value(move: Int): Double {
            val target = move / 13
            val rank = move % 13 + 1
            val mine = hand.count { it.rank == rank }
            // Une demande dont on est sûr : le joueur a demandé cette hauteur et ne l'a pas donnée depuis.
            if (holds[target][rank] && (level == CardLevel.HARD || lastAsked[target] == rank)) return 10.0 + mine
            // Sinon : les cartes de cette hauteur encore dehors, et la part qu'en détient ce joueur.
            var v = (4 - mine) * hands[target].size / maxOf(1, unseen).toDouble()
            if (level == CardLevel.HARD && mine == 3) v *= 1.4
            return v + random.nextDouble() * 0.001
        }
        return moves.maxByOrNull { value(it) }!!
    }

    override fun copy(): GoFishEngine {
        val e = GoFishEngine(seed, false)
        for (s in 0 until 3) {
            e.hands[s].addAll(hands[s])
            e.books[s] = books[s]
            for (r in 0..13) e.holds[s][r] = holds[s][r]
        }
        e.stock.addAll(stock)
        for (s in 0 until 3) e.lastAsked[s] = lastAsked[s]
        e.turn = turn
        e.over = over
        e.lastAsker = lastAsker
        e.lastTarget = lastTarget
        e.lastRank = lastRank
        e.lastGiven = lastGiven
        return e
    }

    companion object {
        /** Une partie aux mains et à la pioche imposées, pour les tests. */
        internal fun forTest(hands: List<List<PlayingCard>>, stock: List<PlayingCard> = emptyList()): GoFishEngine {
            val e = GoFishEngine(0, false)
            for (s in 0 until 3) e.hands[s].addAll(hands[s])
            e.stock.addAll(stock)
            for (s in 0 until 3) e.layBooks(s)
            e.settleTurn()
            return e
        }

        fun move(target: Int, rank: Int) = target * 13 + rank - 1
    }
}
