package com.Atom2Universe.app.games.pipetap

enum class PipeTapDifficulty(val gridSize: Int, val label: String) {
    EASY(4, "4×4"),
    MEDIUM(5, "5×5"),
    HARD(6, "6×6"),
    EXPERT(7, "7×7"),
    MASTER(8, "8×8");

    val isHard get() = this == EXPERT || this == MASTER

    companion object {
        fun fromSize(size: Int) = entries.firstOrNull { it.gridSize == size } ?: MEDIUM
    }
}
