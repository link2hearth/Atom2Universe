package com.Atom2Universe.app.games.chess

/**
 * Niveaux de difficulté pour le jeu d'échecs
 * (le temps de réflexion de l'IA est dans ChessAI, la récompense dans ChessActivity)
 */
enum class ChessDifficulty(
    val labelResId: Int,
    val descriptionResId: Int
) {
    /**
     * Mode entraînement - IA facile pour apprendre
     */
    TRAINING(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_training,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_training_desc
    ),

    /**
     * Mode standard - IA équilibrée
     */
    STANDARD(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_standard,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_standard_desc
    ),

    /**
     * Mode expert - IA difficile
     */
    EXPERT(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_expert,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_expert_desc
    ),

    /**
     * Mode deux joueurs - pas d'IA
     */
    TWO_PLAYER(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_two_player,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_two_player_desc
    );

    /**
     * Vérifie si ce mode utilise l'IA
     */
    fun hasAI(): Boolean = this != TWO_PLAYER
}
