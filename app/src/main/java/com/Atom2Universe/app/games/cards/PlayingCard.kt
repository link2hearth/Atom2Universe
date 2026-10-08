package com.Atom2Universe.app.games.cards

import kotlin.random.Random

/**
 * Les couleurs d'un jeu de 52 cartes, dans l'ordre du bridge (trèfle < carreau < cœur < pique).
 * Les symboles sont des signes, pas du texte à traduire.
 */
enum class CardSuit(val symbol: String, val isRed: Boolean) {
    CLUBS("♣", false),
    DIAMONDS("♦", true),
    HEARTS("♥", true),
    SPADES("♠", false),
}

/**
 * Une carte, réduite à un entier de 0 à 51 : `couleur × 13 + (hauteur − 1)`. Un simple entier se
 * range, se compare, se sauvegarde et se met dans un ensemble sans coût ; la classe n'est qu'une
 * façon lisible de le questionner.
 *
 * [rank] va de 1 (l'As) à 13 (le Roi). [highRank] fait de l'As la plus forte (14) : les jeux de
 * plis l'utilisent, ceux de rami ou de cribbage gardent [rank].
 */
@JvmInline
value class PlayingCard(val id: Int) {
    val suit: CardSuit get() = CardSuit.entries[id / 13]
    val rank: Int get() = id % 13 + 1
    val highRank: Int get() = if (rank == ACE) 14 else rank

    override fun toString(): String = "${rankLetter(rank)}${suit.symbol}"

    companion object {
        const val ACE = 1
        const val JACK = 11
        const val QUEEN = 12
        const val KING = 13

        fun of(suit: CardSuit, rank: Int) = PlayingCard(suit.ordinal * 13 + rank - 1)

        /** Une lettre de repère pour les journaux et les tests (jamais affichée au joueur). */
        private fun rankLetter(rank: Int) = when (rank) {
            1 -> "A"; 10 -> "T"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
            else -> rank.toString()
        }
    }
}

/** Le paquet : fabrication, battage, et l'ordre d'une main bien rangée. */
object Cards {

    /** Les 52 cartes, dans l'ordre. */
    fun standardDeck(): List<PlayingCard> = List(52) { PlayingCard(it) }

    /** Un paquet réduit aux hauteurs données (le 9 à l'As de l'euchre, par exemple). */
    fun deckOf(ranks: (Int) -> Boolean): List<PlayingCard> = standardDeck().filter { ranks(it.rank) }

    fun shuffled(cards: List<PlayingCard>, random: Random): MutableList<PlayingCard> =
        cards.toMutableList().apply { shuffle(random) }

    /**
     * L'ordre d'une main : les couleurs alternent noir / rouge (pique, cœur, trèfle, carreau) pour
     * qu'on ne confonde pas deux séries voisines, et chaque série va de la plus faible à la plus forte.
     */
    private val handSuitOrder = intArrayOf(
        CardSuit.SPADES.ordinal, CardSuit.HEARTS.ordinal, CardSuit.CLUBS.ordinal, CardSuit.DIAMONDS.ordinal)

    fun handKey(card: PlayingCard): Int = handSuitOrder.indexOf(card.suit.ordinal) * 20 + card.highRank

    fun sortedForHand(cards: Collection<PlayingCard>): List<PlayingCard> = cards.sortedBy { handKey(it) }
}
