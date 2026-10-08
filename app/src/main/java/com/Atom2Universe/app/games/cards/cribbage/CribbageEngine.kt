package com.Atom2Universe.app.games.cards.cribbage

import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import kotlin.random.Random

/**
 * Le Cribbage (cribbage d'OpenSpiel), à deux joueurs : le premier à [limit] points (121) gagne.
 *
 * Une manche, c'est :
 * 1. **La donne** : six cartes chacun. Chacun en met deux de côté dans le **crib**, une main de plus
 *    qui reviendra au donneur.
 * 2. **La retournée** : une carte du paquet est retournée. Si c'est un valet, le donneur marque 2.
 * 3. **Le décompte en jouant** (« pegging ») : à tour de rôle, en commençant par le non-donneur, on
 *    pose une carte sur une pile dont le total ne doit pas dépasser 31. On marque 2 en faisant 15 ou
 *    31, des points pour une paire, un brelan ou un carré, et pour une suite. Qui ne peut pas poser
 *    dit « go » ; quand personne ne peut plus, le dernier à avoir posé marque 1, la pile est remise
 *    à zéro, et on recommence avec les cartes qui restent.
 * 4. **Le compte des mains** : le non-donneur compte sa main de quatre cartes avec la retournée, puis
 *    le donneur la sienne, puis le crib (voir [CribbageScoring]).
 *
 * Si quelqu'un atteint 121, la partie s'arrête sur-le-champ, même au milieu d'un décompte.
 *
 * Coups : mettre deux cartes dans le crib ([discardMove]) ; poser une carte (son numéro) ; [CONTINUE]
 * pour passer d'un compte de main au suivant. Dire « go » se fait tout seul.
 */
class CribbageEngine private constructor(
    private val seed: Long,
    val limit: Int,
    dealNow: Boolean,
) : CardEngine() {

    constructor(seed: Long, limit: Int = DEFAULT_LIMIT) : this(seed, limit, true)

    override val seats: Int get() = 2

    /** Les cartes en main : six au début, puis ce qu'il reste à poser pendant le décompte. */
    val hands: Array<MutableList<PlayingCard>> = Array(2) { ArrayList() }

    /** Les quatre cartes gardées par chacun, pour le compte des mains. */
    val kept: Array<List<PlayingCard>> = Array(2) { emptyList() }

    val crib = ArrayList<PlayingCard>()

    var starter: PlayingCard? = null
        private set

    val totals = IntArray(2)

    /** Les points marqués par chacun depuis le début de la manche, pour l'affichage. */
    val gained = IntArray(2)

    /** Où en est la manche : [DISCARD], [PEG] ou [SHOW]. */
    var phase = DISCARD
        private set

    var handNo = 0
        private set

    /** Les cartes posées depuis la dernière remise à zéro : qui a posé quoi, dans l'ordre. */
    val pile = ArrayList<Pair<Int, PlayingCard>>()

    /** Le total de la pile. */
    var count = 0
        private set

    /** Au compte des mains : 0 le non-donneur, 1 le donneur, 2 le crib. */
    var showStage = 0
        private set

    /** Les points du compte en cours, pour l'affichage. */
    var showPoints = 0
        private set

    private var turn = 0
    private var over = false
    private var stockLeft = 40
    private var cut: PlayingCard? = null

    val dealer: Int get() = (handNo + 1) % 2

    val stockSize: Int get() = if (starter == null) stockLeft else stockLeft - 1

    init { if (dealNow) dealHand() }

    override val current: Int get() = if (over) -1 else if (phase == SHOW) 0 else turn

    override val scores: IntArray get() = totals.copyOf()

    override fun winners(): Set<Int> {
        val best = totals.max()
        return (0 until 2).filter { totals[it] == best }.toSet()
    }

    private fun dealHand() {
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed * 3_011L + handNo))
        for (s in 0 until 2) { hands[s].clear(); hands[s].addAll(deck.subList(s * 6, s * 6 + 6)) }
        cut = deck[12]
        stockLeft = 40
        starter = null
        crib.clear()
        for (s in 0 until 2) kept[s] = emptyList()
        pile.clear()
        count = 0
        gained.fill(0)
        showStage = 0
        showPoints = 0
        phase = DISCARD
        turn = 0
    }

    private fun award(seat: Int, points: Int) {
        if (points <= 0 || over) return
        totals[seat] += points
        gained[seat] += points
        if (totals[seat] >= limit) over = true
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray = when (phase) {
        DISCARD -> {
            val ids = hands[turn].map { it.id }.sorted()
            val out = ArrayList<Int>()
            for (a in 0 until ids.size - 1) for (b in a + 1 until ids.size) out.add(DISCARD_BASE + ids[a] * 52 + ids[b])
            out.toIntArray()
        }
        PEG -> playable(turn).map { it.id }.toIntArray()
        else -> intArrayOf(CONTINUE)
    }

    private fun playable(seat: Int): List<PlayingCard> = hands[seat].filter { CribbageScoring.value(it) + count <= 31 }

    override fun apply(move: Int) {
        when (phase) {
            DISCARD -> discard(move)
            PEG -> peg(PlayingCard(move))
            else -> nextShow()
        }
    }

    private fun discard(move: Int) {
        val code = move - DISCARD_BASE
        val a = PlayingCard(code / 52)
        val b = PlayingCard(code % 52)
        hands[turn].remove(a)
        hands[turn].remove(b)
        crib.add(a)
        crib.add(b)
        if (turn == 0) { turn = 1; return }
        // Les deux ont écarté : on retourne la carte, et le non-donneur ouvre le décompte.
        for (s in 0 until 2) kept[s] = hands[s].toList()
        starter = cut
        if (starter!!.rank == PlayingCard.JACK) award(dealer, 2)
        phase = PEG
        turn = 1 - dealer
    }

    private fun peg(card: PlayingCard) {
        val seat = turn
        hands[seat].remove(card)
        pile.add(seat to card)
        count += CribbageScoring.value(card)
        emit(CardEvent.Play(seat, card, toRow = true))
        award(seat, CribbageScoring.pegPoints(pile.map { it.second }, count))
        if (over) return
        if (count == 31) { sweep(seat); return }
        val other = 1 - seat
        when {
            playable(other).isNotEmpty() -> turn = other
            playable(seat).isNotEmpty() -> turn = seat
            else -> { award(seat, 1); if (!over) sweep(seat) } // « go » : le dernier à poser marque 1
        }
    }

    /** La pile est ramassée ; on repart avec les cartes qui restent, ou on passe au compte des mains. */
    private fun sweep(last: Int) {
        emit(CardEvent.Take(last))
        pile.clear()
        count = 0
        if (hands[0].isEmpty() && hands[1].isEmpty()) { startShow(); return }
        val next = 1 - last
        turn = if (hands[next].isNotEmpty()) next else last
        // Si le joueur qui doit ouvrir ne peut rien poser (cartes trop fortes : impossible à 0), c'est l'autre.
    }

    private fun startShow() {
        phase = SHOW
        showStage = 0
        scoreStage()
    }

    /** Compte la main ou le crib du moment, l'affiche au centre, et marque ses points. */
    private fun scoreStage() {
        val owner = showOwner()
        val cards = if (showStage == 2) crib.toList() else kept[owner]
        showPoints = CribbageScoring.scoreHand(cards, starter!!, isCrib = showStage == 2)
        emit(CardEvent.Lay(showRow()))
        award(owner, showPoints)
    }

    /** Les cartes du compte en cours, rangées, avec la retournée au bout : ce qu'on montre au centre. */
    fun showRow(): List<Pair<Int, PlayingCard>> {
        val owner = showOwner()
        val cards = if (showStage == 2) crib.toList() else kept[owner]
        return (cards.sortedWith(compareBy({ it.rank }, { it.suit })) + starter!!).map { owner to it }
    }

    private fun nextShow() {
        if (showStage < 2) { showStage++; scoreStage(); return }
        emit(CardEvent.Lay(emptyList()))
        if (handNo + 1 >= MAX_HANDS) { over = true; return }
        handNo++
        dealHand()
    }

    /** À qui appartient la main (ou le crib) qu'on est en train de compter. */
    fun showOwner(): Int = if (showStage == 0) 1 - dealer else dealer

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        if (level == CardLevel.EASY) return moves[random.nextInt(moves.size)]
        return if (phase == DISCARD) chooseDiscard(level) else choosePeg(level, moves)
    }

    /** Les deux cartes à mettre au crib : celles dont l'absence laisse la meilleure main (et le crib le plus utile à son propriétaire). */
    private fun chooseDiscard(level: Int): Int {
        val hand = hands[turn]
        val mine = turn == dealer
        val unseen = (0 until 52).map { PlayingCard(it) }.filter { it !in hand }
        var best = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 0 until hand.size - 1) for (j in i + 1 until hand.size) {
            val keep = hand.filterIndexed { k, _ -> k != i && k != j }
            val a = hand[i]
            val b = hand[j]
            val handValue = if (level == CardLevel.HARD) {
                // La moyenne sur toutes les retournées possibles.
                unseen.sumOf { CribbageScoring.scoreHand(keep, it).toDouble() } / unseen.size
            } else quickHandValue(keep)
            val cribValue = cribHeuristic(a, b)
            val score = handValue + if (mine) cribValue else -cribValue
            if (score > bestScore) {
                bestScore = score
                best = DISCARD_BASE + minOf(a.id, b.id) * 52 + maxOf(a.id, b.id)
            }
        }
        return best
    }

    /** Une valeur rapide d'une main de quatre cartes sans retournée : 15, paires, suites, couleur. */
    private fun quickHandValue(keep: List<PlayingCard>): Double {
        var p = 0.0
        for (subset in 1 until (1 shl keep.size)) {
            var sum = 0
            for (i in keep.indices) if (subset and (1 shl i) != 0) sum += CribbageScoring.value(keep[i])
            if (sum == 15) p += 2
        }
        for (i in keep.indices) for (j in i + 1 until keep.size) if (keep[i].rank == keep[j].rank) p += 2
        val ranks = keep.map { it.rank }.sorted().distinct()
        var run = 1
        for (i in 1 until ranks.size) {
            if (ranks[i] == ranks[i - 1] + 1) run++ else { if (run >= 3) p += run; run = 1 }
        }
        if (run >= 3) p += run
        if (keep.all { it.suit == keep[0].suit }) p += 4
        return p
    }

    /** Ce que deux cartes valent dans le crib : des 5, des paires, des cartes qui font 15 ou se suivent. */
    private fun cribHeuristic(a: PlayingCard, b: PlayingCard): Double {
        var v = 0.0
        if (a.rank == b.rank) v += 2.0
        if (CribbageScoring.value(a) + CribbageScoring.value(b) == 15) v += 2.0
        if (a.rank == 5 || b.rank == 5) v += 0.7
        if (Math.abs(a.rank - b.rank) == 1) v += 0.8
        if (a.suit == b.suit) v += 0.2
        // Une carte isolée, loin de tout (un roi et un 8) ne sert à rien.
        return v
    }

    /** Le coup du décompte : le plus de points tout de suite, sans offrir un 15 ou un 31 à l'autre (niveau difficile). */
    private fun choosePeg(level: Int, moves: IntArray): Int {
        val hand = hands[turn]
        fun score(move: Int): Double {
            val card = PlayingCard(move)
            val newCount = count + CribbageScoring.value(card)
            val points = CribbageScoring.pegPoints(pile.map { it.second } + card, newCount).toDouble()
            var s = points * 10.0
            if (level == CardLevel.HARD) {
                // Après ce coup, l'autre trouve-t-il facilement 15 ou 31 ?
                s -= when (newCount) { 5 -> 3.0; 10 -> 1.0; 21 -> 3.0; 26 -> 1.0; else -> 0.0 }
                // Poser une carte dont on a la jumelle prépare une paire, mais en donner une à l'autre est risqué.
                if (pile.isEmpty()) s -= if (card.rank == 5) 1.0 else 0.0
                // Garder les petites cartes pour finir le décompte : poser d'abord les fortes quand rien ne marque.
                s += CribbageScoring.value(card) * 0.1
            } else {
                s += CribbageScoring.value(card) * 0.05
            }
            // Un 31 qu'on peut atteindre se prend toujours.
            if (newCount == 31) s += 5.0
            return s
        }
        return moves.maxByOrNull { score(it) }!!
    }

    override fun copy(): CribbageEngine {
        val e = CribbageEngine(seed, limit, false)
        for (s in 0 until 2) {
            e.hands[s].addAll(hands[s])
            e.kept[s] = kept[s]
            e.totals[s] = totals[s]
            e.gained[s] = gained[s]
        }
        e.crib.addAll(crib)
        e.starter = starter
        e.cut = cut
        e.phase = phase
        e.handNo = handNo
        e.pile.addAll(pile)
        e.count = count
        e.showStage = showStage
        e.showPoints = showPoints
        e.turn = turn
        e.over = over
        e.stockLeft = stockLeft
        return e
    }

    companion object {
        const val DEFAULT_LIMIT = 121
        private const val MAX_HANDS = 40

        const val DISCARD = 0
        const val PEG = 1
        const val SHOW = 2

        const val DISCARD_BASE = 1000
        const val CONTINUE = 700

        /** Le coup « mettre ces deux cartes au crib ». */
        fun discardMove(cards: List<PlayingCard>): Int {
            val ids = cards.map { it.id }.sorted()
            require(ids.size == 2) { "il faut écarter deux cartes" }
            return DISCARD_BASE + ids[0] * 52 + ids[1]
        }

        /** Une manche aux mains, au crib et à la retournée imposés, au début du décompte, pour les tests. */
        internal fun forTestPeg(hands: List<List<PlayingCard>>, starter: PlayingCard, crib: List<PlayingCard> = emptyList(), dealer: Int = 1): CribbageEngine {
            val e = CribbageEngine(0, DEFAULT_LIMIT, false)
            e.handNo = (dealer + 1) % 2 // le donneur est (handNo + 1) % 2
            for (s in 0 until 2) { e.hands[s].addAll(hands[s]); e.kept[s] = hands[s].toList() }
            e.crib.addAll(crib)
            e.starter = starter
            e.phase = PEG
            e.turn = 1 - e.dealer
            return e
        }
    }
}
