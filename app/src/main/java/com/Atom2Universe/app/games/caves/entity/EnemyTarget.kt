package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.PlayerNode

/**
 * Un joueur tel que les monstres le voient : une position à poursuivre et une santé à entamer.
 *
 * [EnemyManager] reçoit une liste de cibles ; chaque monstre poursuit la plus proche. En solo,
 * la liste ne contient que le joueur de l'appareil.
 */
internal interface EnemyTarget {
    val x: Double
    /** Hauteur des yeux debout (pas des pieds). */
    val y: Double
    val z: Double
    /** De combien les yeux sont descendus (accroupi) : les coups visent plus bas. */
    val eyeDrop: Double
    /** Points de vie et bouclier. */
    val node: PlayerNode
    /** Après un coup, quelques instants où aucun monstre ne peut toucher cette cible (s). */
    var hitCooldown: Float
}
