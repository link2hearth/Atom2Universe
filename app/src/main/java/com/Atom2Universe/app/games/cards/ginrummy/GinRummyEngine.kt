package com.Atom2Universe.app.games.cards.ginrummy

import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardEvent
import com.Atom2Universe.app.games.cards.CardLevel
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import kotlin.random.Random

/**
 * Le Gin rami (gin_rummy d'OpenSpiel), à deux joueurs. Chacun reçoit dix cartes ; une carte est
 * retournée sur la défausse. Le but est d'assembler ses cartes en **combinaisons** (voir [GinMelds]) et
 * d'avoir le moins possible de **bois mort**.
 *
 * Un tour : piocher (à la pioche, ou la carte du dessus de la défausse), puis écarter une carte. Au
 * tout début, le joueur qui n'a pas donné peut prendre la carte retournée, sinon le donneur le peut,
 * sinon le non-donneur pioche à la pioche.
 *
 * On peut **frapper** au lieu d'écarter simplement, si le bois mort de ses dix cartes est de 10 points
 * ou moins. L'autre pose alors ses combinaisons et charge ses cartes sur celles du frappeur (ici,
 * automatiquement). Si le frappeur a moins de bois mort : il marque l'écart. Avec **gin** (aucun bois
 * mort) il marque l'écart et 20 de plus, sans charge possible. Si l'autre a autant ou moins de bois
 * mort, c'est une **contre** : il marque l'écart et 10 de plus.
 *
 * Quand il ne reste plus que deux cartes à la pioche, la manche est nulle. La partie s'arrête à
 * [limit] points.
 *
 * Coups : écarter une carte (son numéro) ; frapper en écartant une carte (`KNOCK_BASE` plus son
 * numéro) ; [DRAW_STOCK], [DRAW_DISCARD], [PASS] ; et [CONTINUE] pour passer du décompte à la donne suivante.
 */
class GinRummyEngine private constructor(
    private val seed: Long,
    val limit: Int,
    dealNow: Boolean,
) : CardEngine() {

    constructor(seed: Long, limit: Int = DEFAULT_LIMIT) : this(seed, limit, true)

    override val seats: Int get() = 2

    val hands: Array<MutableList<PlayingCard>> = Array(2) { ArrayList() }

    /** La pioche et la défausse : la carte du dessus est la dernière. */
    val stock = ArrayList<PlayingCard>()
    val discard = ArrayList<PlayingCard>()

    val totals = IntArray(2)

    /** Où en est la manche : [FIRST], [DRAW], [DISCARD] ou [SHOWDOWN]. */
    var phase = FIRST
        private set

    var handNo = 0
        private set

    private var turn = 0
    private var passes = 0
    private var stockOnly = false
    private var over = false

    /** La carte qu'on vient de prendre sur la défausse : on ne peut pas la rejeter aussitôt. */
    private var justTaken: PlayingCard? = null

    /** Les cartes prises sur la défausse par chaque joueur et pas encore rejetées : tout le monde les a vues. */
    private val taken = Array(2) { HashSet<PlayingCard>() }

    // ── Le décompte de la dernière manche ───────────────────────────────────────────────────

    /** Ce que la dernière manche a rapporté à chaque joueur. */
    var lastHand = IntArray(2)
        private set

    /** Comment la dernière manche s'est finie : [KIND_KNOCK], [KIND_GIN], [KIND_UNDERCUT] ou [KIND_DRAW]. */
    var lastKind = 0
        private set

    var knocker = -1
        private set

    /** Le bois mort du joueur 1 au décompte (après charge s'il défendait), pour l'affichage. */
    var shownDeadwood = 0
        private set

    /** La main du joueur 1 rangée (combinaisons puis bois mort), montrée au décompte. */
    var shown: List<PlayingCard> = emptyList()
        private set

    val dealer: Int get() = (handNo + 1) % 2

    init { if (dealNow) dealHand() }

    override val current: Int get() = if (over) -1 else if (phase == SHOWDOWN) 0 else turn

    override val scores: IntArray get() = totals.copyOf()

    override fun winners(): Set<Int> {
        val best = totals.max()
        return (0 until 2).filter { totals[it] == best }.toSet()
    }

    private fun dealHand() {
        val deck = Cards.shuffled(Cards.standardDeck(), Random(seed * 5_023L + handNo))
        for (s in 0 until 2) { hands[s].clear(); hands[s].addAll(deck.subList(s * 10, s * 10 + 10)) }
        discard.clear()
        discard.add(deck[20])
        stock.clear()
        stock.addAll(deck.subList(21, 52))
        for (t in taken) t.clear()
        phase = FIRST
        passes = 0
        stockOnly = false
        justTaken = null
        turn = (dealer + 1) % 2
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    override fun legalMoves(): IntArray = when (phase) {
        FIRST -> intArrayOf(DRAW_DISCARD, PASS)
        DRAW -> if (stockOnly) intArrayOf(DRAW_STOCK) else intArrayOf(DRAW_STOCK, DRAW_DISCARD)
        SHOWDOWN -> intArrayOf(CONTINUE)
        else -> {
            val out = ArrayList<Int>()
            val mask = GinMelds.maskOf(hands[turn])
            for (card in hands[turn]) {
                if (card == justTaken) continue
                out.add(card.id)
                if (GinMelds.deadwood(mask and GinMelds.bit(card).inv()) <= KNOCK_LIMIT) out.add(KNOCK_BASE + card.id)
            }
            out.toIntArray()
        }
    }

    override fun apply(move: Int) {
        when (phase) {
            FIRST -> if (move == DRAW_DISCARD) takeDiscard() else {
                passes++
                if (passes == 1) turn = dealer else { turn = (dealer + 1) % 2; phase = DRAW; stockOnly = true }
            }
            DRAW -> if (move == DRAW_STOCK) drawStock() else takeDiscard()
            DISCARD -> discardCard(move)
            else -> next()
        }
    }

    private fun takeDiscard() {
        val card = discard.removeAt(discard.size - 1)
        hands[turn].add(card)
        taken[turn].add(card)
        justTaken = card
        emit(CardEvent.Draw(turn, card, fromDiscard = true))
        phase = DISCARD
    }

    private fun drawStock() {
        val card = stock.removeAt(stock.size - 1)
        hands[turn].add(card)
        justTaken = null
        stockOnly = false
        emit(CardEvent.Draw(turn, if (turn == 0) card else null))
        phase = DISCARD
    }

    private fun discardCard(move: Int) {
        val knock = move >= KNOCK_BASE
        val card = PlayingCard(if (knock) move - KNOCK_BASE else move)
        hands[turn].remove(card)
        discard.add(card)
        for (t in taken) t.remove(card)
        emit(CardEvent.Play(turn, card, toPile = true))
        justTaken = null
        if (knock) { showdown(turn); return }
        if (stock.size <= STOCK_FLOOR) { lastHand = IntArray(2); lastKind = KIND_DRAW; knocker = -1; shown = emptyList(); phase = SHOWDOWN; return }
        turn = 1 - turn
        phase = DRAW
    }

    /** Le décompte : [who] a frappé avec dix cartes en main. */
    private fun showdown(who: Int) {
        knocker = who
        val other = 1 - who
        val mine = GinMelds.best(GinMelds.maskOf(hands[who]))
        val theirs = GinMelds.best(GinMelds.maskOf(hands[other]))
        val delta = IntArray(2)
        var theirDead = theirs.deadwood
        if (mine.deadwood == 0) {
            delta[who] = theirDead + GIN_BONUS
            lastKind = KIND_GIN
        } else {
            // L'autre charge ce qu'il peut sur les combinaisons du frappeur.
            theirDead = GinMelds.rawDeadwood(GinMelds.layOffAll(mine.melds, theirs.deadwoodMask))
            if (mine.deadwood < theirDead) {
                delta[who] = theirDead - mine.deadwood
                lastKind = KIND_KNOCK
            } else {
                delta[other] = mine.deadwood - theirDead + UNDERCUT_BONUS
                lastKind = KIND_UNDERCUT
            }
        }
        shownDeadwood = if (who == 1) mine.deadwood else theirDead
        lastHand = delta
        for (s in 0 until 2) totals[s] += delta[s]
        shown = arranged(hands[1])
        emit(CardEvent.Lay(shown.map { 1 to it }))
        phase = SHOWDOWN
    }

    /** Une main rangée : les combinaisons d'abord, le bois mort ensuite. */
    fun arranged(hand: List<PlayingCard>): List<PlayingCard> {
        val layout = GinMelds.best(GinMelds.maskOf(hand))
        val out = ArrayList<PlayingCard>()
        for (meld in layout.melds.sortedBy { java.lang.Long.numberOfTrailingZeros(it) }) out.addAll(GinMelds.cardsOf(meld))
        out.addAll(GinMelds.cardsOf(layout.deadwoodMask).sortedBy { it.rank })
        return out
    }

    /** Les cartes de [hand] qui ne font partie d'aucune combinaison (le bois mort), pour les montrer. */
    fun deadwoodCards(hand: List<PlayingCard>): List<PlayingCard> =
        GinMelds.cardsOf(GinMelds.best(GinMelds.maskOf(hand)).deadwoodMask)

    fun deadwoodOf(hand: List<PlayingCard>): Int = GinMelds.deadwood(GinMelds.maskOf(hand))

    /** Le bois mort de [seat] : avec une carte de trop en main, celui qu'il aurait en écartant la meilleure. */
    fun deadwoodNow(seat: Int): Int {
        val mask = maskOf(seat)
        return if (hands[seat].size > 10) bestAfterDiscard(mask, 0L) else GinMelds.deadwood(mask)
    }

    private fun next() {
        emit(CardEvent.Lay(emptyList()))
        shown = emptyList()
        if (totals.max() >= limit || handNo + 1 >= MAX_HANDS) { over = true; return }
        handNo++
        dealHand()
    }

    // ── Adversaires ─────────────────────────────────────────────────────────────────────────

    override fun aiMove(level: Int, random: Random): Int {
        val moves = legalMoves()
        if (moves.size == 1) return moves[0]
        if (level == CardLevel.EASY && random.nextDouble() < 0.5) return moves[random.nextInt(moves.size)]
        return when (phase) {
            FIRST -> if (wantsDiscard(level)) DRAW_DISCARD else PASS
            DRAW -> if (DRAW_DISCARD in moves && wantsDiscard(level)) DRAW_DISCARD else DRAW_STOCK
            else -> chooseDiscard(moves, level)
        }
    }

    private fun maskOf(seat: Int) = GinMelds.maskOf(hands[seat])

    /** Le plus petit bois mort possible après avoir écarté une carte de [mask] (sauf [forbidden]). */
    private fun bestAfterDiscard(mask: Long, forbidden: Long): Int {
        var best = Int.MAX_VALUE
        for (card in GinMelds.cardsOf(mask)) {
            val b = GinMelds.bit(card)
            if (b and forbidden != 0L) continue
            best = minOf(best, GinMelds.deadwood(mask and b.inv()))
        }
        return best
    }

    /** La carte du dessus de la défausse améliore-t-elle la main ? */
    private fun wantsDiscard(level: Int): Boolean {
        val top = discard.lastOrNull() ?: return false
        val mask = maskOf(turn)
        val now = GinMelds.deadwood(mask)
        val b = GinMelds.bit(top)
        val after = bestAfterDiscard(mask or b, b)
        if (after < now) return true
        // Un joueur avisé prend aussi une carte qui forme une paire ou une suite presque faite quand le gain est nul.
        return level == CardLevel.HARD && after == now && stock.size > 14 && links(top, hands[turn]) >= 3.0
    }

    /** À quel point [card] se rapproche d'une combinaison avec [others] : des paires, des cartes voisines de même couleur. */
    private fun links(card: PlayingCard, others: Collection<PlayingCard>): Double {
        var v = 0.0
        for (o in others) {
            if (o == card) continue
            if (o.rank == card.rank) v += 2.0
            else if (o.suit == card.suit) {
                val gap = Math.abs(o.rank - card.rank)
                if (gap == 1) v += 1.5 else if (gap == 2) v += 0.7
            }
        }
        return v
    }

    private fun shouldKnock(deadwood: Int, level: Int): Boolean {
        if (deadwood == 0) return true
        return when (level) {
            CardLevel.HARD -> deadwood <= 6 || (stock.size <= 10 && deadwood <= KNOCK_LIMIT)
            else -> deadwood <= 4 || (stock.size <= 6 && deadwood <= KNOCK_LIMIT)
        }
    }

    private fun chooseDiscard(moves: IntArray, level: Int): Int {
        val mask = maskOf(turn)
        val me = turn
        val knocks = moves.filter { it >= KNOCK_BASE }
        if (knocks.isNotEmpty()) {
            val bestKnock = knocks.minByOrNull { GinMelds.deadwood(mask and GinMelds.bit(PlayingCard(it - KNOCK_BASE)).inv()) }!!
            val dw = GinMelds.deadwood(mask and GinMelds.bit(PlayingCard(bestKnock - KNOCK_BASE)).inv())
            if (shouldKnock(dw, level)) return bestKnock
        }
        val options = moves.filter { it < 52 }
        val seen = HashSet<PlayingCard>()
        seen.addAll(hands[me]); seen.addAll(discard); seen.addAll(taken[1 - me])
        val unseen = (0 until 52).map { PlayingCard(it) }.filter { it !in seen }

        fun score(move: Int): Double {
            val card = PlayingCard(move)
            val rest = mask and GinMelds.bit(card).inv()
            val dw = GinMelds.deadwood(rest)
            val restCards = hands[me].filter { it != card }
            var s = -dw * 100.0 + restCards.sumOf { links(it, restCards) } * 2.0 + GinMelds.deadwoodValue(card) * 0.5
            if (level == CardLevel.HARD) {
                // Les cartes qui feraient baisser le bois mort : plus il y en a, mieux la main se développe.
                val outs = unseen.count { x -> bestAfterDiscard(rest or GinMelds.bit(x), 0L) < dw }
                s += outs * 4.0
                // Ne pas nourrir l'adversaire : une carte proche de ce qu'il a ramassé lui sert.
                s -= taken[1 - me].sumOf { links(card, listOf(it)) } * 6.0
            }
            return s
        }
        return options.maxByOrNull { score(it) }!!
    }

    override fun copy(): GinRummyEngine {
        val e = GinRummyEngine(seed, limit, false)
        for (s in 0 until 2) { e.hands[s].addAll(hands[s]); e.totals[s] = totals[s]; e.taken[s].addAll(taken[s]) }
        e.stock.addAll(stock)
        e.discard.addAll(discard)
        e.phase = phase
        e.handNo = handNo
        e.turn = turn
        e.passes = passes
        e.stockOnly = stockOnly
        e.over = over
        e.justTaken = justTaken
        e.lastHand = lastHand.copyOf()
        e.lastKind = lastKind
        e.knocker = knocker
        e.shownDeadwood = shownDeadwood
        e.shown = shown
        return e
    }

    companion object {
        /** Une manche aux mains et à la défausse imposées, en phase de pioche, pour les tests. */
        internal fun forTest(
            hands: List<List<PlayingCard>>,
            discard: List<PlayingCard>,
            stock: List<PlayingCard>,
            turn: Int = 0,
            limit: Int = DEFAULT_LIMIT,
        ): GinRummyEngine {
            val e = GinRummyEngine(0, limit, false)
            for (s in 0 until 2) e.hands[s].addAll(hands[s])
            e.discard.addAll(discard)
            e.stock.addAll(stock)
            e.phase = DRAW
            e.turn = turn
            return e
        }

        const val DEFAULT_LIMIT = 100
        const val KNOCK_LIMIT = 10
        const val GIN_BONUS = 20
        const val UNDERCUT_BONUS = 10
        private const val STOCK_FLOOR = 2
        private const val MAX_HANDS = 30

        const val FIRST = 0
        const val DRAW = 1
        const val DISCARD = 2
        const val SHOWDOWN = 3

        const val KIND_KNOCK = 1
        const val KIND_GIN = 2
        const val KIND_UNDERCUT = 3
        const val KIND_DRAW = 4

        const val KNOCK_BASE = 100
        const val DRAW_STOCK = 500
        const val DRAW_DISCARD = 501
        const val PASS = 502
        const val CONTINUE = 503
    }
}
