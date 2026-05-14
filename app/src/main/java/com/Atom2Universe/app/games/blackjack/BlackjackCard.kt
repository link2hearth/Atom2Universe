package com.Atom2Universe.app.games.blackjack

data class BlackjackCard(
    val rank: Int,       // 1=A, 2-10, 11=J, 12=Q, 13=K
    val suit: Int,       // 0=♠, 1=♥, 2=♦, 3=♣
    var faceUp: Boolean = true
) {
    val value: Int get() = if (rank > 10) 10 else rank
    val isAce: Boolean get() = rank == 1

    val rankLabel: String get() = when (rank) {
        1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
        else -> rank.toString()
    }

    val suitSymbol: String get() = when (suit) {
        0 -> "♠"; 1 -> "♥"; 2 -> "♦"; else -> "♣"
    }

    val isRed: Boolean get() = suit == 1 || suit == 2
}
