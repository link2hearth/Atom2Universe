package com.Atom2Universe.app.games.memory

/** Source persistante sans assets : lettres, chiffres, puis nombres pour les grandes grilles. */
object MemorySymbols {
    const val FOLDER = "__symbols__"

    fun labels(count: Int): List<String> = List(count) { index ->
        if (index < 26) ('A' + index).toString() else (index - 26).toString()
    }

    fun forDifficulty(difficulty: MemoryDifficulty): List<String> =
        labels(maxOf(36, difficulty.pairCount))
}
