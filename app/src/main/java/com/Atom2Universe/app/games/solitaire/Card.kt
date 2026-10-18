package com.Atom2Universe.app.games.solitaire

/**
 * Represents a playing card suit.
 */
enum class Suit(val symbol: String, val color: CardColor) {
    HEARTS("♥", CardColor.RED),
    DIAMONDS("♦", CardColor.RED),
    CLUBS("♣", CardColor.BLACK),
    SPADES("♠", CardColor.BLACK)
}

enum class CardColor {
    RED, BLACK
}

/**
 * Represents a playing card rank (1=Ace, 11=Jack, 12=Queen, 13=King).
 */
enum class Rank(val value: Int, val label: String) {
    ACE(1, "A"),
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K")
}

/**
 * Represents a playing card.
 */
data class Card(
    val suit: Suit,
    val rank: Rank,
    var faceUp: Boolean = false
) {
    val id: String get() = "${suit.name}-${rank.value}"
    val color: CardColor get() = suit.color

    fun canStackOnTableau(other: Card): Boolean {
        // Card must be face up and alternating color, rank one less
        return other.faceUp && this.color != other.color && this.rank.value == other.rank.value - 1
    }

    fun canStackOnFoundation(topCard: Card?): Boolean {
        return if (topCard == null) {
            // Empty foundation: only Ace can go
            this.rank == Rank.ACE
        } else {
            // Same suit, next rank
            this.suit == topCard.suit && this.rank.value == topCard.rank.value + 1
        }
    }

    companion object {
        fun createDeck(): MutableList<Card> {
            val deck = mutableListOf<Card>()
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    deck.add(Card(suit, rank, faceUp = false))
                }
            }
            return deck
        }
    }
}

/**
 * Types of piles in Solitaire.
 */
enum class PileType {
    STOCK,      // Draw pile
    WASTE,      // Cards drawn from stock
    FOUNDATION, // 4 piles to build A-K by suit
    TABLEAU     // 7 columns for gameplay
}
