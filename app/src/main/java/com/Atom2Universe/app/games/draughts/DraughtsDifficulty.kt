package com.Atom2Universe.app.games.draughts

import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards

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

    /** La moins payée des deux : changer de difficulté en cours de partie ne fait jamais monter le gain. */
    fun lowerForReward(other: DraughtsDifficulty): DraughtsDifficulty =
        if (NeutrinoRewards.draughts(other) < NeutrinoRewards.draughts(this)) other else this
}
