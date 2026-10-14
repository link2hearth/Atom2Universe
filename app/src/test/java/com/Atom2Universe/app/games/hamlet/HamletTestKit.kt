package com.Atom2Universe.app.games.hamlet

/**
 * Les quatre héros d'avant le scénario d'ouverture : les tests de systèmes n'ont pas besoin de rejouer l'ouverture.
 * Gardien, duelliste et soigneuse tiennent dans l'équipe de départ (trois places) ; l'archère reste au banc.
 */
internal fun HamletGame.withStarters(): HamletGame {
    for (id in intArrayOf(1, 7, 3, 0)) adventure.heroes[id].owned = true
    intArrayOf(1, 7, 3).forEachIndexed { slot, id -> adventure.teams[0][slot] = id }
    return this
}
