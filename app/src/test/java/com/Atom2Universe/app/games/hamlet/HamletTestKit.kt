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

/**
 * Le village d'avant la nouvelle ouverture : deux champs, une chaumière, 250 pièces et un camp garni. Les tests de
 * systèmes (économie, ateliers, équipement...) partent de là ; seuls les tests d'ouverture jouent le vrai début.
 */
internal fun HamletGame.established(): HamletGame {
    val hall = objs.values.first { it.t == "hall" }
    val field = objs.values.first { it.t == "field" }
    place("field", field.x + 2, field.y)
    if (objs.values.none { it.t == "cottage" }) place("cottage", hall.x + 5, hall.y + 9)?.t0 = now()
    coins = 250; xp = 45 // le niveau 2 : le maïs est ouvert, comme dans les tests d'avant
    longArrayOf(180, 80, 80, 35, 30).forEachIndexed { i, n -> adventure.bag[i] = n }
    return this
}

