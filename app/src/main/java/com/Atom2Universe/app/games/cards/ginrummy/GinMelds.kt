package com.Atom2Universe.app.games.cards.ginrummy

import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.PlayingCard

/**
 * Les combinaisons du Gin rami : un **brelan** (trois ou quatre cartes de la même hauteur) ou une
 * **suite** (trois cartes ou plus qui se suivent dans la même couleur ; l'as ne vaut que 1, donc
 * A-2-3 est une suite et Q-K-A n'en est pas une).
 *
 * Les cartes qui ne sont dans aucune combinaison sont du **bois mort** : un as vaut 1 point, un
 * chiffre sa valeur, une figure 10. Gagner, c'est avoir le moins de bois mort.
 *
 * Une main est un ensemble de cartes, rangé dans un `Long` (un bit par carte) : c'est ce qui rend la
 * recherche du meilleur arrangement assez rapide pour qu'une IA l'essaie des centaines de fois.
 */
object GinMelds {

    /** La valeur d'une carte au bois mort. */
    fun deadwoodValue(card: PlayingCard): Int = minOf(card.rank, 10)

    fun bit(card: PlayingCard): Long = 1L shl card.id

    fun maskOf(cards: Collection<PlayingCard>): Long = cards.fold(0L) { m, c -> m or bit(c) }

    fun cardsOf(mask: Long): List<PlayingCard> = (0 until 52).filter { mask and (1L shl it) != 0L }.map { PlayingCard(it) }

    /** Toutes les combinaisons possibles dans un jeu de 52 cartes (329), chacune sous forme de masque. */
    private val allMelds: LongArray = buildList {
        for (rank in 1..13) {
            val ofRank = CardSuit.entries.map { 1L shl PlayingCard.of(it, rank).id }
            // Les quatre brelans de trois cartes, et le brelan de quatre.
            for (skip in 0 until 4) add(ofRank.filterIndexed { i, _ -> i != skip }.fold(0L) { a, b -> a or b })
            add(ofRank.fold(0L) { a, b -> a or b })
        }
        for (suit in CardSuit.entries) for (length in 3..13) for (start in 1..(14 - length)) {
            add((start until start + length).fold(0L) { m, r -> m or (1L shl PlayingCard.of(suit, r).id) })
        }
    }.toLongArray()

    /** Le meilleur arrangement d'une main : les combinaisons posées, et le bois mort qui reste. */
    class Layout(val melds: List<Long>, val deadwoodMask: Long, val deadwood: Int)

    private fun valueOf(mask: Long): Int {
        var total = 0
        var m = mask
        while (m != 0L) {
            val id = java.lang.Long.numberOfTrailingZeros(m)
            total += minOf(id % 13 + 1, 10)
            m = m and (m - 1)
        }
        return total
    }

    /** Le meilleur arrangement de [mask] : celui qui laisse le moins de bois mort. */
    fun best(mask: Long): Layout {
        val candidates = allMelds.filter { it and mask == it }
        var bestDead = valueOf(mask)
        var bestMelds: List<Long> = emptyList()
        var bestUsed = 0L
        val chosen = ArrayList<Long>()

        fun search(from: Int, used: Long) {
            val dead = valueOf(mask and used.inv())
            if (dead < bestDead) { bestDead = dead; bestMelds = chosen.toList(); bestUsed = used }
            if (bestDead == 0) return
            for (i in from until candidates.size) {
                val meld = candidates[i]
                if (meld and used != 0L) continue
                chosen.add(meld)
                search(i + 1, used or meld)
                chosen.removeAt(chosen.size - 1)
                if (bestDead == 0) return
            }
        }
        search(0, 0L)
        return Layout(bestMelds, mask and bestUsed.inv(), bestDead)
    }

    fun deadwood(mask: Long): Int = best(mask).deadwood

    /** Les points de bois mort de toutes les cartes de [mask], sans chercher de combinaison. */
    fun rawDeadwood(mask: Long): Int = valueOf(mask)

    /**
     * Ce qui reste de [hand] quand on a chargé sur [melds] toutes les cartes qui s'y ajoutent (une carte
     * chargée ouvre parfois la place à la suivante : 5-6-7 reçoit le 8, puis le 9).
     */
    fun layOffAll(melds: List<Long>, hand: Long): Long {
        val laid = melds.toMutableList()
        var rest = hand
        while (true) {
            var progressed = false
            for (i in laid.indices) {
                val extra = layoffs(listOf(laid[i]), rest)
                if (extra != 0L) {
                    val one = extra and -extra
                    laid[i] = laid[i] or one
                    rest = rest and one.inv()
                    progressed = true
                    break
                }
            }
            if (!progressed) return rest
        }
    }

    /**
     * Les cartes de [hand] qui s'ajoutent à l'une des combinaisons [melds] (ce qu'on appelle « charger » :
     * une carte de plus dans un brelan de trois, ou au bout d'une suite).
     */
    fun layoffs(melds: List<Long>, hand: Long): Long {
        var out = 0L
        for (meld in melds) {
            val cards = cardsOf(meld)
            val sameRank = cards.all { it.rank == cards[0].rank }
            if (sameRank) {
                if (cards.size == 3) {
                    // La quatrième carte du brelan.
                    for (suit in CardSuit.entries) {
                        val c = PlayingCard.of(suit, cards[0].rank)
                        if (bit(c) and meld == 0L && bit(c) and hand != 0L) out = out or bit(c)
                    }
                }
            } else {
                val suit = cards[0].suit
                val low = cards.minOf { it.rank }
                val high = cards.maxOf { it.rank }
                if (low > 1) PlayingCard.of(suit, low - 1).let { if (bit(it) and hand != 0L) out = out or bit(it) }
                if (high < 13) PlayingCard.of(suit, high + 1).let { if (bit(it) and hand != 0L) out = out or bit(it) }
            }
        }
        return out
    }
}
