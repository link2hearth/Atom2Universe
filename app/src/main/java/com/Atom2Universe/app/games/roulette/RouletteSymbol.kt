package com.Atom2Universe.app.games.roulette

enum class RouletteSymbol(
    val label: String,
    val colorType: ColorType,
    val weight: Float
) {
    HEARTS("♥", ColorType.RED, 1f),
    DIAMONDS("♦", ColorType.RED, 1f),
    CLUBS("♣", ColorType.BLACK, 1f),
    SPADES("♠", ColorType.BLACK, 1f),
    JOKER("🃏", ColorType.JOKER, 0.5f),
    VOID("●", ColorType.VOID, 2f);

    enum class ColorType { RED, BLACK, JOKER, VOID }

    val isJoker: Boolean get() = this == JOKER
    val isVoid: Boolean get() = this == VOID
    val isSuit: Boolean get() = this == HEARTS || this == DIAMONDS || this == CLUBS || this == SPADES

    companion object {
        private val totalWeight: Float = entries.sumOf { it.weight.toDouble() }.toFloat()

        fun random(): RouletteSymbol {
            val roll = (Math.random() * totalWeight).toFloat()
            var cumulative = 0f
            for (s in entries) {
                cumulative += s.weight
                if (roll < cumulative) return s
            }
            return VOID
        }
    }
}
