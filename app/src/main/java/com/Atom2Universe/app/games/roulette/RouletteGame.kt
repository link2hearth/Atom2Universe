package com.Atom2Universe.app.games.roulette

data class RouletteWin(
    val type: WinType,
    val multiplier: Int,
    val cells: List<Pair<Int, Int>>
)

enum class WinType { SYMBOL_MATCH, JOKER_WILD, JOKER_LINE }

class RouletteGame {

    val symbolMatch = 2   // 3 symboles identiques
    val jokerWild1  = 3   // 2 identiques + 1 joker
    val jokerWild2  = 4   // 1 symbole + 2 jokers
    val jokerLine   = 5   // 3 jokers

    val grid: Array<Array<RouletteSymbol>> = Array(3) { Array(3) { RouletteSymbol.SATURN } }

    fun spin() {
        for (r in 0..2) for (c in 0..2) grid[r][c] = RouletteSymbol.random()
    }

    fun evaluate(): Pair<List<RouletteWin>, Int> {
        val wins = mutableListOf<RouletteWin>()
        for (coords in allLines()) {
            val cells = coords.map { (r, c) -> grid[r][c] }
            evaluateLine(cells, coords)?.let { wins += it }
        }
        val totalMult = wins.sumOf { it.multiplier }
        return Pair(wins, totalMult)
    }

    private fun allLines(): List<List<Pair<Int, Int>>> = buildList {
        for (r in 0..2) add(listOf(Pair(r, 0), Pair(r, 1), Pair(r, 2)))   // lignes
        for (c in 0..2) add(listOf(Pair(0, c), Pair(1, c), Pair(2, c)))   // colonnes
        add(listOf(Pair(0, 0), Pair(1, 1), Pair(2, 2)))                    // diagonale \
        add(listOf(Pair(0, 2), Pair(1, 1), Pair(2, 0)))                    // diagonale /
    }

    private fun evaluateLine(cells: List<RouletteSymbol>, coords: List<Pair<Int, Int>>): RouletteWin? {
        if (cells.any { it.isBlackhole }) return null
        val jokerCount = cells.count { it.isJoker }
        val nonJokers  = cells.filter { !it.isJoker }
        if (nonJokers.isEmpty()) return RouletteWin(WinType.JOKER_LINE, jokerLine, coords)
        val base = nonJokers.first()
        if (!nonJokers.all { it == base }) return null
        return when (jokerCount) {
            0 -> RouletteWin(WinType.SYMBOL_MATCH, symbolMatch, coords)
            1 -> RouletteWin(WinType.JOKER_WILD,   jokerWild1,  coords)
            2 -> RouletteWin(WinType.JOKER_WILD,   jokerWild2,  coords)
            else -> null
        }
    }
}
