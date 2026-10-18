package com.Atom2Universe.app.games.memory

import org.junit.Assert.*
import org.junit.Test

class MemorySymbolsTest {
    @Test fun everyGridHasEnoughDistinctSymbolsAndExactlyTwoOfEachCard() {
        for (difficulty in MemoryDifficulty.entries) {
            val labels = MemorySymbols.forDifficulty(difficulty)
            assertEquals(labels.size, labels.toSet().size)
            assertTrue(labels.size >= difficulty.pairCount)
            val game = MemoryGame()
            game.newGame(difficulty, MemorySymbols.FOLDER, labels)
            assertTrue(game.usesSymbols)
            assertEquals(difficulty.pairCount * 2, game.cards.size)
            val pairs = game.cards.groupingBy { it.imageIndex }.eachCount()
            assertEquals(difficulty.pairCount, pairs.size)
            assertTrue(pairs.values.all { it == 2 })
            assertTrue(game.cards.all { labels.getOrNull(it.imageIndex) != null })
        }
    }

    @Test fun missingImagesKeepTheSavedBoardAndProgressIncludingHighImageIndices() {
        val game = MemoryGame()
        game.newGame(MemoryDifficulty.entries.maxBy { it.pairCount }, "rainbow", List(64) { "$it.jpg" })
        game.flips = 12
        game.elapsedSeconds = 90
        val first = game.cards.first()
        game.cards.filter { it.imageIndex == first.imageIndex }.forEach { it.state = CardState.MATCHED }
        game.matchedPairs = 1
        val board = game.cards.map { Triple(it.id, it.imageIndex, it.state) }
        game.useSymbols()
        assertTrue(game.usesSymbols)
        assertEquals(board, game.cards.map { Triple(it.id, it.imageIndex, it.state) })
        assertEquals(12, game.flips)
        assertEquals(90L, game.elapsedSeconds)
        assertEquals(1, game.matchedPairs)
        assertEquals(64, game.imageFiles.toSet().size)
        assertTrue(game.cards.all { game.imageFiles.getOrNull(it.imageIndex) != null })
    }
}
