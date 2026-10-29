package com.Atom2Universe.app.games.draughts

import com.Atom2Universe.app.R

enum class DraughtsDifficulty(
    val labelResId: Int,
    val descriptionResId: Int,
    val depth: Int
) {
    TRAINING(
        labelResId = R.string.draughts_difficulty_training,
        descriptionResId = R.string.draughts_difficulty_training_desc,
        depth = 2
    ),
    STANDARD(
        labelResId = R.string.draughts_difficulty_standard,
        descriptionResId = R.string.draughts_difficulty_standard_desc,
        depth = 4
    ),
    EXPERT(
        labelResId = R.string.draughts_difficulty_expert,
        descriptionResId = R.string.draughts_difficulty_expert_desc,
        depth = 6
    ),
    TWO_PLAYER(
        labelResId = R.string.draughts_difficulty_two_player,
        descriptionResId = R.string.draughts_difficulty_two_player_desc,
        depth = 0
    );

    fun hasAI() = this != TWO_PLAYER
}
