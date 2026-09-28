package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.entity.EnemyTarget
import com.Atom2Universe.app.games.caves.entity.MagazineState
import com.Atom2Universe.app.games.caves.entity.PlayerStats
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.DoubleSlabs
import com.Atom2Universe.app.games.caves.node.FarmItems
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
    /** Stable identity for delayed loot; remote players must reuse their saved identity. */
    val id: String = java.util.UUID.randomUUID().toString(),
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

    /** Ajoute des objets. Un objet nouveau prend une case libre de la barre, sans en déloger aucun. */
    fun grant(items: List<Pair<Short, Int>>) {
        for ((id, count) in items) {
            val newStack = (inventory[id] ?: 0) == 0
            inventory[id] = ((inventory[id] ?: 0).toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (newStack && id !in hotbar) {
                val empty = hotbar.indices.firstOrNull { hotbar[it] == null }
                if (empty != null) hotbar[empty] = id
            }
        }
    }

    /** Ramasse ce que donne un bloc cassé : la pierre donne des pavés, une dalle double deux dalles… */
    fun collectBlock(blockType: Short, meta: Byte = 0) {
        DoubleSlabs.materials(blockType, meta)?.let { (lower, upper) ->
            collectBlock(lower)
            collectBlock(upper)
            return
        }
        if (blockType.toInt() in 7020..7023) {
            grant(listOf(FarmItems.seed(0) to 1))
            return
        }
        // Une seule conversion : la pierre donne des pavés, qu'on ne recasse pas à leur tour.
        val (dropType, count) = BlockRegistry.harvestDrop(blockType) ?: return
        grant(listOf(dropType to count))
    }

    /** Hauteur des yeux en ce moment (plus bas quand il est accroupi). */
    val eyeY: Double get() = y - eyeDrop

    // ── Ce qu'il demande, et où il vise ───────────────────────────────────────

    /** Ses gestes pendant cette image : les armes, le minage et la marche ne lisent que ça. */
    val input = PlayerInput()

    // Le rayon de visée : il part de (aimFromX, aimFromY, aimFromZ) dans la direction (aimX, aimY, aimZ).
    // Pour le joueur de l'appareil, c'est la croix au centre de l'écran.
    var aimFromX = 0.0
    var aimFromY = 0.0
    var aimFromZ = 0.0
    var aimX = 0f
    var aimY = 0f
    var aimZ = -1f

    // ── Armes et minage ───────────────────────────────────────────────────────

    /** Le bloc qu'il est en train de creuser, et où il en est (1 = cassé). */
    var mineTarget: RayHit? = null
    var mineDamage = 0f
    /** Balles restantes et recharge de chaque arme à chargeur, par objet. */
    val magazines = mutableMapOf<Short, MagazineState>()
    /** Temps avant de pouvoir tirer à nouveau (s). */
    var weaponAttackCooldown = 0f
    /** Depuis combien de temps il tend l'arc ou l'arbalète (s). */
    var weaponChargeTime = 0f
    /** Depuis combien de temps il arme un jet de caillou (s). */
    var rockChargeTime = 0f
    // Pour reconnaître un nouvel appui sur le tir et un changement d'arme.
    var fireWasDown = false
    var lastFirePresses = 0
    var lastFireWeapon: Short? = null
}

/**
 * Les gestes d'un joueur pendant une image, quel que soit l'appareil qui les a produits
 * (écran tactile, manette, et un jour le réseau).
 */
internal class PlayerInput {
    var moveForward = 0f
    var moveRight = 0f
    /** Sauter, ou monter en vol libre. */
    var up = false
    /** Descendre en vol libre. */
    var down = false
    var crouch = false
    var sprint = false
    /** Bouton d'action tenu : creuser, brandir un outil. */
    var mining = false
    /** Gâchette, de 0 à 1. */
    var fire = 0f
    /** Compte les appuis sur le tir : un appui plus court qu'une image n'est pas perdu. */
    var firePresses = 0
    /** Poser un bloc : reste vrai jusqu'à ce que le jeu s'en occupe. */
    var place = false
    /** Bouton de pose tenu : les blocs suivants se posent en suivant la visée. */
    var placeHeld = false

    fun clear() {
        moveForward = 0f; moveRight = 0f; up = false; down = false
        crouch = false; sprint = false; mining = false; fire = 0f; place = false; placeHeld = false
    }
}
