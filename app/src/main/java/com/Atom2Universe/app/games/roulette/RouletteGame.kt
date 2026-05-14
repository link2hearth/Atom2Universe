package com.Atom2Universe.app.games.roulette

data class RouletteWin(
    val type: WinType,
    val multiplier: Int,
    val cells: List<Pair<Int, Int>>   // (row, col)
)

enum class WinType { SUIT_LINE, SUIT_DIAGONAL, COLOR_LINE, COLOR_DIAGONAL, JOKER_ROW }

class RouletteGame {

    // Multiplicateurs
    val suitLine        = 5
    val suitDiagonal    = 5
    val colorLine       = 2
    val colorDiagonal   = 2
    val jokerRowMid     = 25
    val jokerRowEdge    = 10

    val grid: Array<Array<RouletteSymbol>> = Array(3) { Array(3) { RouletteSymbol.VOID } }

    fun spin() {
        for (r in 0..2) for (c in 0..2) grid[r][c] = RouletteSymbol.random()
    }

    fun evaluate(): Pair<List<RouletteWin>, Int> {
        val wins = mutableListOf<RouletteWin>()

        // Jokers sur lignes entières (priorité max, avant couleur/enseigne)
        for (r in 0..2) {
            val row = listOf(grid[r][0], grid[r][1], grid[r][2])
            if (row.all { it.isJoker }) {
                val mult = if (r == 1) jokerRowMid else jokerRowEdge
                wins += RouletteWin(WinType.JOKER_ROW, mult,
                    listOf(Pair(r, 0), Pair(r, 1), Pair(r, 2)))
            }
        }

        val diagonals = listOf(
            listOf(Pair(0,0), Pair(1,1), Pair(2,2)),
            listOf(Pair(0,2), Pair(1,1), Pair(2,0))
        )

        // Diagonales
        for (coords in diagonals) {
            val cells = coords.map { (r, c) -> grid[r][c] }
            uniformSuit(cells)?.let {
                wins += RouletteWin(WinType.SUIT_DIAGONAL, suitDiagonal, coords)
            }
            uniformColor(cells)?.let {
                wins += RouletteWin(WinType.COLOR_DIAGONAL, colorDiagonal, coords)
            }
        }

        // Lignes horizontales
        for (r in 0..2) {
            val coords = listOf(Pair(r,0), Pair(r,1), Pair(r,2))
            val cells  = coords.map { (row, col) -> grid[row][col] }
            // On saute si déjà compté comme ligne de jokers
            if (cells.all { it.isJoker }) continue
            uniformSuit(cells)?.let  { wins += RouletteWin(WinType.SUIT_LINE,  suitLine,  coords) }
            uniformColor(cells)?.let { wins += RouletteWin(WinType.COLOR_LINE, colorLine, coords) }
        }

        // Colonnes verticales
        for (c in 0..2) {
            val coords = listOf(Pair(0,c), Pair(1,c), Pair(2,c))
            val cells  = coords.map { (row, col) -> grid[row][col] }
            uniformSuit(cells)?.let  { wins += RouletteWin(WinType.SUIT_LINE,  suitLine,  coords) }
            uniformColor(cells)?.let { wins += RouletteWin(WinType.COLOR_LINE, colorLine, coords) }
        }

        val totalMult = wins.sumOf { it.multiplier }
        return Pair(wins, totalMult)
    }

    /** Retourne l'enseigne commune si toutes les cellules ont la même (le Joker est joker, le Void bloque). */
    private fun uniformSuit(cells: List<RouletteSymbol>): RouletteSymbol? {
        var base: RouletteSymbol? = null
        for (s in cells) {
            if (s.isVoid) return null
            if (s.isJoker) continue
            if (base == null) base = s else if (s != base) return null
        }
        return base  // null si que des jokers → ne compte pas comme enseigne
    }

    /** Retourne la couleur commune (RED/BLACK) si toutes les cellules l'ont (Joker = wildcard, Void bloque). */
    private fun uniformColor(cells: List<RouletteSymbol>): RouletteSymbol.ColorType? {
        var base: RouletteSymbol.ColorType? = null
        for (s in cells) {
            val ct = s.colorType
            if (ct == RouletteSymbol.ColorType.VOID) return null
            if (ct == RouletteSymbol.ColorType.JOKER) continue
            if (base == null) base = ct else if (ct != base) return null
        }
        return if (base == RouletteSymbol.ColorType.RED || base == RouletteSymbol.ColorType.BLACK) base else null
    }
}
