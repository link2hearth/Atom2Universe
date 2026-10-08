package com.Atom2Universe.app.games.cards

import kotlin.random.Random

/**
 * Ce que partagent les jeux de plis à manches (Spades, Euchre, Oh Hell) : les mains, le pli en
 * cours, la mémoire de ce qui est tombé, et la façon dont un adversaire « imagine » les cartes qu'il
 * ne voit pas.
 *
 * Un jeu fournit : comment se donne une manche ([dealHand]), qui gagne un pli ([trickWinner]),
 * ce qu'une manche rapporte ([scoreHand]), quand la partie s'arrête ([isGameOver]), et ses propres
 * coups ([legalMoves], [apply] — qui appelle [playCard] pour jouer une carte).
 *
 * Les points d'une manche ne sont comptés qu'une fois : une copie de simulation ([frozen]) s'arrête
 * à la fin de la manche au lieu d'en donner une autre.
 */
abstract class TrickEngine(protected val seed: Long, final override val seats: Int) : CardEngine() {

    val hands: Array<MutableList<PlayingCard>> = Array(seats) { ArrayList() }

    /** Le pli en cours : qui a posé quoi, dans l'ordre. */
    val trick = ArrayList<Pair<Int, PlayingCard>>()

    /** Le numéro de la manche en cours, à partir de 0. */
    var handNo = 0
        protected set

    /** Le nombre de plis déjà ramassés dans cette manche. */
    var trickNo = 0
        protected set

    /** Les plis ramassés par chaque siège dans cette manche. */
    val tricksWon = IntArray(seats)

    protected var turn = 0
    protected var leader = 0

    /** Les cartes déjà posées (et celles qu'on a vues retournées) : on ne les verra plus dans une main. */
    protected val played = BooleanArray(52)

    /** Qui n'a pas fourni la couleur demandée, donc n'en a plus : [siège][couleur]. */
    protected val void = Array(seats) { BooleanArray(4) }

    /** Les cartes qu'on sait dans la main d'un siège (une carte retournée ramassée) : l'imagination ne les déplace pas. */
    protected val known = Array(seats) { HashSet<PlayingCard>() }

    protected var over = false
    protected var frozen = false
    protected var handDone = false

    override val current: Int get() = if (over || (frozen && handDone)) -1 else turn

    // ── À fournir par chaque jeu ────────────────────────────────────────────────────────────

    protected abstract fun dealHand()
    protected abstract fun trickWinner(): Int
    protected abstract fun scoreHand()
    protected abstract fun isGameOver(): Boolean

    /** La couleur qui compte pour suivre : une carte spéciale peut changer de couleur (le valet gauche de l'euchre). */
    protected open fun suitOf(card: PlayingCard): CardSuit = card.suit

    /** Les cartes du jeu (l'euchre n'en utilise que 24). */
    protected open fun inDeck(card: PlayingCard): Boolean = true

    /** Les plis d'une manche, en tout. */
    protected abstract val tricksInHand: Int

    /** Combien de cartes forment un pli (moins quand un joueur reste de côté). */
    protected open val trickSize: Int get() = seats

    /** Le siège qui joue après [seat]. */
    protected open fun nextSeat(seat: Int): Int = (seat + 1) % seats

    /** Le pli est ramassé : le jeu peut compter. */
    protected open fun onTrickTaken(winner: Int) {}

    // ── Jouer une carte ─────────────────────────────────────────────────────────────────────

    /** Pose [card] pour [seat]. Renvoie vrai si cela termine le pli (le jeu décide alors de la suite). */
    protected fun playCard(seat: Int, card: PlayingCard): Boolean {
        hands[seat].remove(card)
        played[card.id] = true
        known[seat].remove(card)
        if (trick.isNotEmpty()) {
            val led = suitOf(trick[0].second)
            if (suitOf(card) != led) void[seat][led.ordinal] = true
        }
        trick.add(seat to card)
        emit(CardEvent.Play(seat, card))
        if (trick.size < trickSize) { turn = nextSeat(seat); return false }
        val winner = trickWinner()
        tricksWon[winner]++
        trick.clear()
        emit(CardEvent.Take(winner))
        trickNo++
        leader = winner
        turn = winner
        onTrickTaken(winner)
        if (trickNo == tricksInHand) finishHand()
        return true
    }

    private fun finishHand() {
        handDone = true
        scoreHand()
        if (frozen) return
        if (isGameOver()) { over = true; return }
        handNo++
        dealHand()
    }

    /** Les cartes qu'un siège doit fournir : celles de la couleur demandée, s'il en a. */
    protected fun followable(hand: List<PlayingCard>): List<PlayingCard> {
        if (trick.isEmpty()) return hand
        val led = suitOf(trick[0].second)
        return hand.filter { suitOf(it) == led }.ifEmpty { hand }
    }

    /** Remet à zéro ce qui appartient à une manche. */
    protected fun resetHandState() {
        trick.clear()
        trickNo = 0
        tricksWon.fill(0)
        played.fill(false)
        for (v in void) v.fill(false)
        for (k in known) k.clear()
        handDone = false
    }

    // ── Simulation ──────────────────────────────────────────────────────────────────────────

    final override fun simulationDone(): Boolean = handDone || over
    override val simulationLimit: Int get() = 120

    /**
     * Rebat les cartes cachées : celles qui ne sont ni dans la main de [seat], ni tombées, ni connues,
     * réparties au hasard entre les autres sièges, sans donner à quelqu'un une couleur qu'il a déjà
     * montré ne plus avoir (sauf si le tirage n'aboutit pas, au dernier essai).
     */
    final override fun determinize(seat: Int, random: Random) {
        frozen = true
        val fixed = Array(seats) { s -> if (s == seat) emptyList() else known[s].filter { it in hands[s] } }
        val taken = HashSet<PlayingCard>()
        for (f in fixed) taken.addAll(f)
        val unseen = (0 until 52).map { PlayingCard(it) }
            .filter { inDeck(it) && !played[it.id] && it !in hands[seat] && it !in taken }
        val need = IntArray(seats) { if (it == seat) 0 else hands[it].size - fixed[it].size }
        repeat(8) { attempt ->
            val pool = unseen.toMutableList().also { it.shuffle(random) }
            val left = need.copyOf()
            val dealt = Array(seats) { ArrayList<PlayingCard>() }
            var ok = true
            for (card in pool) {
                if (left.all { it == 0 }) break
                val eligible = (0 until seats).filter { s ->
                    s != seat && left[s] > 0 && (attempt >= 6 || !void[s][suitOf(card).ordinal])
                }
                if (eligible.isEmpty()) { ok = false; break }
                val to = eligible[random.nextInt(eligible.size)]
                dealt[to].add(card)
                left[to]--
            }
            if (ok && left.all { it == 0 }) {
                for (s in 0 until seats) if (s != seat) { hands[s].clear(); hands[s].addAll(fixed[s]); hands[s].addAll(dealt[s]) }
                return
            }
        }
    }

    /** Copie l'état commun dans [e] (le jeu copie ensuite le sien). */
    protected fun copyBaseTo(e: TrickEngine) {
        for (s in 0 until seats) { e.hands[s].clear(); e.hands[s].addAll(hands[s]) }
        e.trick.addAll(trick)
        e.handNo = handNo
        e.trickNo = trickNo
        for (s in 0 until seats) e.tricksWon[s] = tricksWon[s]
        e.turn = turn
        e.leader = leader
        for (i in 0 until 52) e.played[i] = played[i]
        for (s in 0 until seats) {
            for (c in 0 until 4) e.void[s][c] = void[s][c]
            e.known[s].addAll(known[s])
        }
        e.over = over
        e.frozen = frozen
        e.handDone = handDone
    }

    /** Le siège de gauche de [seat] (le suivant, dans le sens des aiguilles d'une montre). */
    protected fun leftOf(seat: Int) = (seat + 1) % seats
}
