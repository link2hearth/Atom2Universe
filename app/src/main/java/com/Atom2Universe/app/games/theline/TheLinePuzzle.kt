package com.Atom2Universe.app.games.theline

val THE_LINE_COLORS = listOf(
    0xFFF7B731.toInt(), // amber
    0xFF34D1FF.toInt(), // azure
    0xFFA162F7.toInt(), // orchid
    0xFFFF6B6B.toInt(), // coral
    0xFF7BD88F.toInt(), // emerald
    0xFFFF8AD6.toInt(), // rose
    0xFFFFC371.toInt()  // citrus
)

val THE_LINE_SINGLE_COLOR = 0xFF7AD3FF.toInt()

enum class TheLineDifficulty {
    EASY, MEDIUM, HARD;

    val gridSizes: List<Pair<Int, Int>> get() = when (this) {
        EASY   -> listOf(5 to 5, 5 to 6, 6 to 6)
        MEDIUM -> listOf(7 to 6, 7 to 7, 8 to 6, 8 to 7)
        HARD   -> listOf(8 to 8, 9 to 7, 9 to 8, 9 to 9)
    }
    val holeMin: Int get() = when (this) { EASY -> 2; MEDIUM -> 5; HARD -> 10 }
    val holeMax: Int get() = when (this) { EASY -> 5; MEDIUM -> 12; HARD -> 20 }
    val minTurns: Int get() = when (this) { EASY -> 6; MEDIUM -> 10; HARD -> 14 }
    val multiPairsMin: Int get() = when (this) { EASY -> 2; MEDIUM -> 2; HARD -> 3 }
    val multiPairsMax: Int get() = when (this) { EASY -> 3; MEDIUM -> 4; HARD -> 5 }
}

data class TLCoord(val x: Int, val y: Int)

data class TLSegment(
    val colorValue: Int,
    val cells: List<TLCoord>,
    val start: TLCoord,
    val end: TLCoord
)

data class TheLinePuzzle(
    val mode: TheLineMode,
    val width: Int,
    val height: Int,
    val blockedIndices: Set<Int>,
    val path: List<TLCoord>,
    val endpoints: Pair<TLCoord, TLCoord>? = null,
    val segments: List<TLSegment> = emptyList()
)

enum class TheLineMode { SINGLE, MULTI }
