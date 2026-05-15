package com.Atom2Universe.app.games.roulette

enum class RouletteSymbol(
    val displayName: String,
    val assetPath: String?,
    val weight: Float
) {
    STAR    ("★",  "Assets/Image/terre.png",          1f),
    EARTH   ("🌍", "Assets/Image/jupiter.png",         1f),
    SUN     ("☀",  "Assets/Image/Sun.png",             1f),
    MOON    ("🌕", "Assets/Image/FullMoon2010.png",    1f),
    SATURN  ("🪐", "Assets/Image/saturn.png",          1f),
    BLACKHOLE("⚫","Assets/sprites/blackhole.jpg",     1f),
    JOKER   ("🃏", "Assets/Image/RainbowStar.gif",    0.5f);

    val isJoker: Boolean get() = this == JOKER
    val isBlackhole: Boolean get() = this == BLACKHOLE

    companion object {
        private val totalWeight = entries.sumOf { it.weight.toDouble() }.toFloat()  // 6.5

        fun random(): RouletteSymbol {
            val roll = (Math.random() * totalWeight).toFloat()
            var cumulative = 0f
            for (s in entries) {
                cumulative += s.weight
                if (roll < cumulative) return s
            }
            return BLACKHOLE
        }
    }
}
