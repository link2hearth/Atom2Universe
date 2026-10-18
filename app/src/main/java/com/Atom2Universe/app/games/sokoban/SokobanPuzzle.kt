package com.Atom2Universe.app.games.sokoban

/** Une direction de déplacement sur la grille. */
enum class SokobanDir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0)
}

/**
 * Niveau de difficulté. Pilote la taille de grille, le nombre de caisses, le
 * seuil de longueur de solution (en poussées) visé par le générateur, et la
 * densité de murs internes.
 */
enum class SokobanDifficulty {
    EASY, MEDIUM, HARD, EXPERT;

    val gridSizes: List<Pair<Int, Int>>
        get() = when (this) {
            EASY   -> listOf(6 to 6, 7 to 6, 7 to 7)
            MEDIUM -> listOf(7 to 7, 8 to 7, 8 to 8)
            HARD   -> listOf(8 to 8, 9 to 8, 9 to 9)
            EXPERT -> listOf(9 to 9, 10 to 9, 10 to 10)
        }

    /** Bornes (incluses) du nombre de caisses tirées au hasard pour un niveau. */
    val boxRange: IntRange
        get() = when (this) {
            EASY   -> 1..2
            MEDIUM -> 2..3
            HARD   -> 3..4
            EXPERT -> 4..5
        }

    /**
     * Profondeur (poussées optimales) visée. Seuil *souple* : si le générateur
     * l'atteint il s'arrête, sinon il garde le niveau le plus dur trouvé dans le
     * budget. Volontairement modeste — la difficulté ressentie vient surtout de
     * la taille de grille et du nombre de caisses.
     */
    val targetDepth: Int
        get() = when (this) {
            EASY -> 5; MEDIUM -> 9; HARD -> 12; EXPERT -> 15
        }

    /** Plafond de nœuds explorés par le BFS pour cette difficulté. */
    val nodeBudget: Int
        get() = when (this) {
            EASY -> 4000; MEDIUM -> 12000; HARD -> 25000; EXPERT -> 40000
        }

    /** Probabilité qu'une case intérieure devienne un mur (obstacle). */
    val wallProbability: Double
        get() = when (this) {
            EASY -> 0.06; MEDIUM -> 0.09; HARD -> 0.11; EXPERT -> 0.13
        }

    fun randomBoxCount() = boxRange.random()
}

/**
 * Description immuable d'un niveau généré.
 *
 * Les cases sont indexées par `y * width + x`. La solvabilité est garantie par
 * construction (génération inverse), et [optimalPushes] est la vraie longueur de
 * la solution optimale mesurée par le solveur BFS.
 */
data class SokobanPuzzle(
    val width: Int,
    val height: Int,
    val walls: Set<Int>,
    val goals: Set<Int>,
    val boxes: List<Int>,
    val player: Int,
    val optimalPushes: Int
) {
    fun index(x: Int, y: Int) = y * width + x
    fun isWall(index: Int) = index in walls
}
