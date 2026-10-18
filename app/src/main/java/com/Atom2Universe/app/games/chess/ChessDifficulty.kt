package com.Atom2Universe.app.games.chess

/**
 * Niveaux de difficulté pour le jeu d'échecs
 */
enum class ChessDifficulty(
    val labelResId: Int,
    val descriptionResId: Int,
    val depth: Int,              // Profondeur de recherche de l'IA
    val timeLimitMs: Long,       // Limite de temps pour la recherche
    val gachaTickets: Int,       // Récompense en tickets gacha
    val boostMultiplier: Int,    // Multiplicateur de boost
    val boostDurationSeconds: Int // Durée du boost en secondes
) {
    /**
     * Mode entraînement - IA facile pour apprendre
     */
    TRAINING(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_training,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_training_desc,
        depth = 3,
        timeLimitMs = 1000,
        gachaTickets = 0,
        boostMultiplier = 0,
        boostDurationSeconds = 0
    ),

    /**
     * Mode standard - IA équilibrée
     */
    STANDARD(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_standard,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_standard_desc,
        depth = 5,
        timeLimitMs = 2500,
        gachaTickets = 50,
        boostMultiplier = 100,
        boostDurationSeconds = 300
    ),

    /**
     * Mode expert - IA difficile
     */
    EXPERT(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_expert,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_expert_desc,
        depth = 7,
        timeLimitMs = 4000,
        gachaTickets = 100,
        boostMultiplier = 100,
        boostDurationSeconds = 600
    ),

    /**
     * Mode deux joueurs - pas d'IA
     */
    TWO_PLAYER(
        labelResId = com.Atom2Universe.app.R.string.chess_difficulty_two_player,
        descriptionResId = com.Atom2Universe.app.R.string.chess_difficulty_two_player_desc,
        depth = 0,
        timeLimitMs = 0,
        gachaTickets = 0,
        boostMultiplier = 0,
        boostDurationSeconds = 0
    );

    /**
     * Vérifie si ce mode utilise l'IA
     */
    fun hasAI(): Boolean = this != TWO_PLAYER
}
