package com.Atom2Universe.app.games.solitaire.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.Atom2Universe.app.games.solitaire.*

/**
 * Room entity for persisting Solitaire game state.
 */
@Entity(tableName = "solitaire_saves")
data class SolitaireSaveEntity(
    @PrimaryKey
    val id: Int = 1,
    val stockJson: String,
    val wasteJson: String,
    val foundationsJson: String,
    val tableauJson: String,
    val isWon: Boolean,
    val moves: Int,
    val elapsedTimeMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromGame(game: SolitaireGame, moves: Int, elapsedTimeMs: Long): SolitaireSaveEntity {
            return SolitaireSaveEntity(
                stockJson = serializeCardList(game.stock),
                wasteJson = serializeCardList(game.waste),
                foundationsJson = serializeFoundations(game.foundations),
                tableauJson = serializeTableau(game.tableau),
                isWon = game.isGameWon(),
                moves = moves,
                elapsedTimeMs = elapsedTimeMs
            )
        }

        private fun serializeCard(card: Card): String {
            return "${card.suit.name},${card.rank.name},${if (card.faceUp) "1" else "0"}"
        }

        private fun serializeCardList(cards: List<Card>): String {
            return cards.joinToString(";") { serializeCard(it) }
        }

        private fun serializeFoundations(foundations: Array<MutableList<Card>>): String {
            return foundations.joinToString("|") { pile ->
                serializeCardList(pile)
            }
        }

        private fun serializeTableau(tableau: Array<MutableList<Card>>): String {
            return tableau.joinToString("|") { column ->
                serializeCardList(column)
            }
        }

        private fun deserializeCard(data: String): Card? {
            val parts = data.split(",")
            if (parts.size != 3) return null
            return try {
                val suit = Suit.valueOf(parts[0])
                val rank = Rank.valueOf(parts[1])
                val faceUp = parts[2] == "1"
                Card(suit, rank, faceUp)
            } catch (e: Exception) {
                null
            }
        }

        private fun deserializeCardList(data: String): MutableList<Card> {
            if (data.isBlank()) return mutableListOf()
            return data.split(";")
                .mapNotNull { deserializeCard(it) }
                .toMutableList()
        }
    }

    fun restoreGame(game: SolitaireGame) {
        game.stock.clear()
        game.stock.addAll(deserializeCardList(stockJson))

        game.waste.clear()
        game.waste.addAll(deserializeCardList(wasteJson))

        // Foundations
        val foundationParts = foundationsJson.split("|")
        for (i in 0 until 4) {
            game.foundations[i].clear()
            if (i < foundationParts.size) {
                game.foundations[i].addAll(deserializeCardList(foundationParts[i]))
            }
        }

        // Tableau
        val tableauParts = tableauJson.split("|")
        for (i in 0 until 7) {
            game.tableau[i].clear()
            if (i < tableauParts.size) {
                game.tableau[i].addAll(deserializeCardList(tableauParts[i]))
            }
        }

        game.clearSelection()
    }
}
