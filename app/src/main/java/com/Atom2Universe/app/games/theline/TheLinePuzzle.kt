package com.Atom2Universe.app.games.theline

/** Couleurs des gaines, dans l'ordre où les fils les reçoivent (mode Câblage). */
val THE_LINE_COLORS = listOf(
    0xFFE5484D.toInt(), // rouge
    0xFF3B8EEA.toInt(), // bleu
    0xFFF2C94C.toInt(), // jaune
    0xFF3FBF6F.toInt(), // vert
    0xFFF2994A.toInt(), // orange
    0xFF9B6BDF.toInt(), // violet
    0xFF3CC8D8.toInt()  // cyan
)

/** Le cuivre de la piste (mode Piste). */
val THE_LINE_SINGLE_COLOR = 0xFFB8703A.toInt()

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

    /**
     * Nombre de bornes numérotées en mode Piste, départ et arrivée compris. Peu de bornes
     * laissent beaucoup de chemins possibles ; beaucoup de bornes guident la main : les
     * deux extrêmes sont faciles, la difficulté vient surtout de la taille de la grille.
     */
    val checkpointsMin: Int get() = when (this) { EASY -> 4; MEDIUM -> 5; HARD -> 6 }
    val checkpointsMax: Int get() = when (this) { EASY -> 5; MEDIUM -> 7; HARD -> 9 }
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
    /** Le chemin du générateur, qui passe par toutes les cases libres : une solution parmi d'autres. */
    val path: List<TLCoord>,
    val endpoints: Pair<TLCoord, TLCoord>? = null,
    val segments: List<TLSegment> = emptyList(),
    /**
     * Mode Piste : les bornes 1, 2, 3… dans l'ordre où la piste doit les traverser. La
     * première est le départ, la dernière l'arrivée ; toutes sont prises sur [path].
     */
    val checkpoints: List<TLCoord> = emptyList()
)

/** SINGLE : une seule piste qui passe par les bornes dans l'ordre. MULTI : des fils à relier par paires. */
enum class TheLineMode { SINGLE, MULTI }
