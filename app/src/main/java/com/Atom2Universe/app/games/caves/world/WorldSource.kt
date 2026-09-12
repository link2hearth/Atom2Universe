package com.Atom2Universe.app.games.caves.world

/**
 * D'où viennent les blocs d'un chunk qui n'a encore jamais été chargé.
 *
 * Sans source, [World] utilise sa génération procédurale (biomes, grottes, îles). Une source
 * remplace ce bruit par un lieu préparé à l'avance : c'est la porte d'entrée du mode Assaut
 * (voir [MapSource] et CAVE_WORLD_ASSAUT.md à la racine).
 */
interface WorldSource {

    /**
     * Écrit ses blocs dans [chunk], reçu vide (tout en AIR). Appelé depuis les threads de
     * génération, plusieurs à la fois : ne rien modifier d'autre que le chunk reçu.
     */
    fun fill(chunk: Chunk)

    /**
     * Y monde à partir duquel la colonne ([wx], [wz]) est à ciel ouvert. Sert de repli à la
     * lumière du ciel quand un chunk voisin n'est pas encore chargé.
     */
    fun skyTopY(wx: Int, wz: Int): Int
}
