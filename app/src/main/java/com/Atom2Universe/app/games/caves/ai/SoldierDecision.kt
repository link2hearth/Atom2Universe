package com.Atom2Universe.app.games.caves.ai

/** Choix tactique sans accès à la position réelle du joueur ni au rendu. */
internal object SoldierDecision {
    fun choose(seesTarget: Boolean, remembersTarget: Boolean, healthFraction: Float,
               recentHit: Boolean, canSeekCover: Boolean): Soldier.State {
        var action = Soldier.State.PATROL
        var score = 1f
        if (remembersTarget) { action = Soldier.State.SEARCH; score = 25f }
        if (seesTarget) { action = Soldier.State.ENGAGE; score = 60f }
        // La santé seule ne provoque pas une fuite permanente : il faut une menace récente.
        if (remembersTarget && recentHit && canSeekCover) {
            val coverScore = 45f + (1f - healthFraction.coerceIn(0f, 1f)) * 55f
            if (coverScore > score) action = Soldier.State.COVER
        }
        return action
    }
}
