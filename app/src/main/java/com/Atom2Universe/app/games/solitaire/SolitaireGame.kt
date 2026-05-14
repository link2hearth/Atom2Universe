package com.Atom2Universe.app.games.solitaire

/**
 * Core Solitaire (Klondike) game logic.
 */
class SolitaireGame {

    // Stock pile (face down draw pile)
    val stock = mutableListOf<Card>()

    // Waste pile (cards drawn from stock)
    val waste = mutableListOf<Card>()

    // 4 Foundation piles (build A-K by suit)
    val foundations = Array(4) { mutableListOf<Card>() }

    // 7 Tableau columns
    val tableau = Array(7) { mutableListOf<Card>() }

    // Currently selected cards for moving
    var selectedCards: List<Card> = emptyList()
    var selectedSource: Pair<PileType, Int>? = null // (type, index)

    /**
     * Starts a new game by shuffling and dealing cards.
     */
    fun newGame() {
        // Clear all piles
        stock.clear()
        waste.clear()
        foundations.forEach { it.clear() }
        tableau.forEach { it.clear() }
        clearSelection()

        // Create and shuffle deck
        val deck = Card.createDeck()
        deck.shuffle()

        // Deal to tableau: column i gets i+1 cards, top card face up
        for (col in 0 until 7) {
            for (row in 0..col) {
                val card = deck.removeAt(deck.lastIndex)
                card.faceUp = (row == col)
                tableau[col].add(card)
            }
        }

        // Rest goes to stock
        deck.forEach { card ->
            card.faceUp = false
            stock.add(card)
        }
    }

    /**
     * Draws a card from stock to waste.
     */
    fun drawFromStock() {
        clearSelection()
        if (stock.isNotEmpty()) {
            val card = stock.removeAt(stock.lastIndex)
            card.faceUp = true
            waste.add(card)
        } else if (waste.isNotEmpty()) {
            // Recycle waste back to stock
            while (waste.isNotEmpty()) {
                val card = waste.removeAt(waste.lastIndex)
                card.faceUp = false
                stock.add(card)
            }
        }
    }

    /**
     * Selects a card or stack of cards for moving.
     */
    fun selectCard(pileType: PileType, pileIndex: Int, cardIndex: Int): Boolean {
        val pile = getPile(pileType, pileIndex) ?: return false
        if (cardIndex < 0 || cardIndex >= pile.size) return false

        val card = pile[cardIndex]

        // Can't select face-down cards (except to flip them)
        if (!card.faceUp) {
            // If it's the top card of a tableau column, flip it
            if (pileType == PileType.TABLEAU && cardIndex == pile.lastIndex) {
                card.faceUp = true
                return true
            }
            return false
        }

        // Select cards from this position to end of pile
        when (pileType) {
            PileType.WASTE -> {
                if (cardIndex == pile.lastIndex) {
                    selectedCards = listOf(card)
                    selectedSource = Pair(pileType, pileIndex)
                    return true
                }
            }
            PileType.FOUNDATION -> {
                if (cardIndex == pile.lastIndex) {
                    selectedCards = listOf(card)
                    selectedSource = Pair(pileType, pileIndex)
                    return true
                }
            }
            PileType.TABLEAU -> {
                // Can select multiple cards in tableau if all face up
                val cardsToSelect = pile.subList(cardIndex, pile.size)
                if (cardsToSelect.all { it.faceUp }) {
                    selectedCards = cardsToSelect.toList()
                    selectedSource = Pair(pileType, pileIndex)
                    return true
                }
            }
            else -> return false
        }
        return false
    }

    /**
     * Clears the current selection.
     */
    fun clearSelection() {
        selectedCards = emptyList()
        selectedSource = null
    }

    /**
     * Attempts to move selected cards to a tableau column.
     */
    fun moveToTableau(columnIndex: Int): Boolean {
        if (selectedCards.isEmpty() || selectedSource == null) return false
        if (columnIndex < 0 || columnIndex >= 7) return false

        val (sourceType, sourceIndex) = selectedSource!!
        if (sourceType == PileType.TABLEAU && sourceIndex == columnIndex) return false

        val targetPile = tableau[columnIndex]
        val firstCard = selectedCards.first()

        // Check if move is valid
        val canMove = if (targetPile.isEmpty()) {
            // Empty column: only King can go
            firstCard.rank == Rank.KING
        } else {
            val targetCard = targetPile.last()
            firstCard.canStackOnTableau(targetCard)
        }

        if (!canMove) return false

        // Perform move
        val sourcePile = getPile(sourceType, sourceIndex) ?: return false
        val startIndex = sourcePile.indexOf(selectedCards.first())
        if (startIndex == -1) return false

        // Remove from source
        repeat(selectedCards.size) {
            sourcePile.removeAt(startIndex)
        }

        // Add to target
        selectedCards.forEach { card ->
            targetPile.add(card)
        }

        // Reveal top card of source tableau if needed
        if (sourceType == PileType.TABLEAU && sourcePile.isNotEmpty()) {
            sourcePile.last().faceUp = true
        }

        clearSelection()
        return true
    }

    /**
     * Attempts to move selected card to a foundation.
     */
    fun moveToFoundation(foundationIndex: Int): Boolean {
        if (selectedCards.size != 1 || selectedSource == null) return false
        if (foundationIndex < 0 || foundationIndex >= 4) return false

        val (sourceType, sourceIndex) = selectedSource!!
        if (sourceType == PileType.FOUNDATION && sourceIndex == foundationIndex) return false

        val card = selectedCards.first()
        val foundation = foundations[foundationIndex]
        val topCard = foundation.lastOrNull()

        if (!card.canStackOnFoundation(topCard)) return false

        // Perform move
        val sourcePile = getPile(sourceType, sourceIndex) ?: return false
        sourcePile.remove(card)
        foundation.add(card)

        // Reveal top card of source tableau if needed
        if (sourceType == PileType.TABLEAU && sourcePile.isNotEmpty()) {
            sourcePile.last().faceUp = true
        }

        clearSelection()
        return true
    }

    /**
     * Auto-moves a card to foundation if possible.
     */
    fun autoMoveToFoundation(card: Card, sourceType: PileType, sourceIndex: Int): Boolean {
        if (!card.faceUp) return false

        // Find suitable foundation
        for (i in 0 until 4) {
            val foundation = foundations[i]
            val topCard = foundation.lastOrNull()

            if (card.canStackOnFoundation(topCard)) {
                // Check suit match for non-empty foundation
                if (topCard != null && topCard.suit != card.suit) continue

                // Perform move
                val sourcePile = getPile(sourceType, sourceIndex) ?: return false
                if (sourcePile.lastOrNull() != card) return false

                sourcePile.remove(card)
                foundation.add(card)

                // Reveal top card of source tableau if needed
                if (sourceType == PileType.TABLEAU && sourcePile.isNotEmpty()) {
                    sourcePile.last().faceUp = true
                }

                clearSelection()
                return true
            }
        }
        return false
    }

    /**
     * Checks if the game is won (all cards in foundations).
     */
    fun isGameWon(): Boolean {
        return foundations.all { it.size == 13 }
    }

    /**
     * Checks if auto-finish is possible:
     * - Stock is empty
     * - Waste is empty
     * - All tableau cards are face up
     */
    fun canAutoFinish(): Boolean {
        if (stock.isNotEmpty()) return false
        if (waste.isNotEmpty()) return false

        // Check all tableau cards are face up
        for (column in tableau) {
            if (column.any { !it.faceUp }) return false
        }

        // Also need at least one card not in foundations
        val totalInFoundations = foundations.sumOf { it.size }
        return totalInFoundations < 52
    }

    /**
     * Finds the next card that can be moved to a foundation during auto-finish.
     * Returns the card and its source location, or null if none found.
     */
    fun findNextAutoFinishMove(): Triple<Card, PileType, Int>? {
        // Check tableau columns for movable cards
        for (col in 0 until 7) {
            val column = tableau[col]
            if (column.isEmpty()) continue

            val card = column.last()
            // Check if this card can go to any foundation
            for (i in 0 until 4) {
                val foundation = foundations[i]
                if (card.canStackOnFoundation(foundation.lastOrNull())) {
                    // Check suit match for non-empty foundation
                    if (foundation.isEmpty() || foundation.last().suit == card.suit) {
                        return Triple(card, PileType.TABLEAU, col)
                    }
                }
            }
        }
        return null
    }

    /**
     * Gets a pile by type and index.
     */
    fun getPile(type: PileType, index: Int): MutableList<Card>? {
        return when (type) {
            PileType.STOCK -> stock
            PileType.WASTE -> waste
            PileType.FOUNDATION -> if (index in 0..3) foundations[index] else null
            PileType.TABLEAU -> if (index in 0..6) tableau[index] else null
        }
    }

    /**
     * Gets the top card of a pile, or null if empty.
     */
    fun getTopCard(type: PileType, index: Int): Card? {
        return getPile(type, index)?.lastOrNull()
    }

    /**
     * Checks if a card is selected.
     */
    fun isCardSelected(card: Card): Boolean {
        return selectedCards.contains(card)
    }
}
