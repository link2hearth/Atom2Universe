package com.Atom2Universe.app.games.pipetap

enum class PipeTapDifficulty(val gridSize: Int) {
    EASY(4),
    MEDIUM(5),
    HARD(6),
    EXPERT(7),
    MASTER(8);

    val isHard get() = this == EXPERT || this == MASTER

    companion object {
        fun fromSize(size: Int) = entries.firstOrNull { it.gridSize == size } ?: MEDIUM
    }
}
