package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.entity.EnemyTarget
import com.Atom2Universe.app.games.caves.entity.PlayerStats
import com.Atom2Universe.app.games.caves.node.PhysicsNode
import com.Atom2Universe.app.games.caves.node.PlayerNode

/**
 * Un joueur de Cave World : où il est, où il regarde, ce qu'il porte et sa santé.
 *
 * Le monde en garde la liste ([CaveSimulation.players]). Pour l'instant elle ne contient qu'un
 * joueur, celui de l'appareil ; le multijoueur y ajouterait les autres. La caméra ne fait que
 * suivre un joueur : elle n'est pas le joueur.
 *
 * Rien ici ne dessine. Ce qui ne sert qu'à l'écran (bras en main, balancement de la marche,
 * animations d'armes) reste dans [CaveRenderer].
 */
internal class CavePlayer(
    /** Collisions, gravité, nage : le corps du joueur dans le monde. */
    val physics: PhysicsNode,
) : EnemyTarget {
    // Position dans le monde. Attention : y est la hauteur des yeux debout, pas celle des pieds.
    override var x = 0.0
    override var y = 0.0
    override var z = 0.0

    /** Direction du regard, en degrés. */
    var yaw = 0f
    var pitch = 0f

    var mode = PlayerMode.WALK
    var isCreative = false

    /** Points de vie et bouclier. */
    override val node = PlayerNode()
    /** Accroupi, les yeux descendent ; en vol libre, jamais. */
    override val eyeDrop: Double get() = if (mode == PlayerMode.WALK) physics.eyeDrop else 0.0
    override var hitCooldown = 0f
    /** Niveau et expérience. */
    val stats = PlayerStats()

    val inventory = mutableMapOf<Short, Int>()
    /** Barre de raccourcis : un objet par case, ou rien. */
    val hotbar = arrayOfNulls<Short>(CaveActivity.ACTIVE_SIZE)
    var selectedSlot = 0

    /** L'objet en main, s'il en reste au moins un dans l'inventaire. */
    val heldItem: Short? get() = hotbar[selectedSlot]?.takeIf { (inventory[it] ?: 0) > 0 }
}
