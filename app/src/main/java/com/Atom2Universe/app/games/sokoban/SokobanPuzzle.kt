package com.Atom2Universe.app.games.sokoban

import kotlin.random.Random

/** Une direction de déplacement sur la grille. */
enum class SokobanDir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0)
}

/**
 * Difficulté du mode aléatoire. La tablette fabrique son niveau avec le même moteur que la
 * fabrique du PC ([SokobanForge]) : une pièce plus petite et quelques secondes de calcul.
 */
enum class SokobanDifficulty {
    EASY, MEDIUM, HARD, EXPERT;

    /** La pièce (intérieur), les caisses, le sol permis et le plafond d'exploration. */
    internal fun spec(random: Random): SokobanForgeSpec {
        val turn = random.nextBoolean()
        return when (this) {
            EASY   -> SokobanForgeSpec(5, 5, 2, 10, 17, 20_000)
            MEDIUM -> if (turn) SokobanForgeSpec(6, 6, 3, 14, 24, 60_000) else SokobanForgeSpec(7, 5, 3, 14, 24, 60_000)
            HARD   -> if (turn) SokobanForgeSpec(7, 7, 4, 18, 30, 120_000) else SokobanForgeSpec(8, 6, 4, 18, 30, 120_000)
            EXPERT -> if (turn) SokobanForgeSpec(8, 7, 5, 22, 36, 200_000) else SokobanForgeSpec(7, 8, 5, 22, 36, 200_000)
        }
    }

    /** Temps de calcul accordé sur la tablette ; le niveau suivant se prépare pendant la partie. */
    val budgetMs: Long
        get() = when (this) {
            EASY -> 600; MEDIUM -> 1200; HARD -> 2200; EXPERT -> 3500
        }
}

/**
 * Description immuable d'un niveau.
 *
 * Les cases sont indexées par `y * width + x`. [walls] contient aussi le dehors (ce qui n'est
 * pas du sol). [optimalPushes] est la vraie longueur de la solution optimale en poussées.
 */
data class SokobanPuzzle(
    val width: Int,
    val height: Int,
    val walls: Set<Int>,
    val goals: Set<Int>,
    val boxes: List<Int>,
    val player: Int,
    val optimalPushes: Int,
    /** Une solution optimale en poussées, en LURD (minuscule = pas, majuscule = poussée). */
    val solution: String = ""
) {
    fun index(x: Int, y: Int) = y * width + x
    fun isWall(index: Int) = index in walls
}
