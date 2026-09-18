package com.Atom2Universe.app.games.roguelike

/**
 * Laisse jouer tous les ennemis jusqu'au prochain tour du héros, chacun à son tour de jauge
 * (voir [Combat]) : la parade est [parry] à chaque coup. Renvoie leurs débuts de tour mis bout
 * à bout, comme un seul « tour ennemi ».
 */
fun Combat.passEnemyTurns(parry: Timing = Timing.MISS): EnemyTurnStart {
    val turns = mutableListOf<EnemyTurnStart>()
    while (phase == CombatPhase.ENEMY_TURN) {
        val t = startEnemyTurn()
        for (a in t.attackers) if (phase == CombatPhase.ENEMY_TURN) resolveStrike(a, parry)
        endEnemyTurn()
        turns += t
    }
    return EnemyTurnStart(
        turns.flatMap { it.ticks }, turns.flatMap { it.attackers }, turns.flatMap { it.stopped },
        turns.flatMap { it.saves }, turns.flatMap { it.enraged },
    )
}
