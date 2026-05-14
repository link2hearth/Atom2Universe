package com.Atom2Universe.app.games.memory

enum class MemoryDifficulty(val cols: Int, val rows: Int, val label: String) {
    EASY(4, 4, "4×4"),
    NORMAL(5, 5, "5×5"),
    MEDIUM(6, 6, "6×6"),
    PRO(7, 7, "7×7"),
    HARD(8, 8, "8×8");

    val pairCount get() = cols * rows / 2
}
