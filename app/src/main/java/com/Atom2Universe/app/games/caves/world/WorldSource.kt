package com.Atom2Universe.app.games.caves.world

/**
 * D'où viennent les blocs d'un chunk qui n'a encore jamais été chargé.
 *
 * Sans source, [World] utilise sa génération procédurale (biomes, grottes, îles). Une source
 * permet de remplacer ce bruit par un lieu préparé à l'avance : c'est la porte d'entrée du futur
 * mode Assaut (voir CAVE_WORLD_ASSAUT.md à la racine).
 *
 * [fill] reçoit un chunk vide (tout en AIR) et y écrit ses blocs. Il est appelé depuis les threads
 * de génération, plusieurs à la fois : une source ne doit rien modifier d'autre que le chunk reçu.
 */
fun interface WorldSource {
    fun fill(chunk: Chunk)
}
