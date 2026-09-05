package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD

/** Grands décors de piste : tunnel traversable et rambardes de pont.
 * Un volume par pièce solide, comme partout ailleurs — jamais une boîte
 * englobante qui bloquerait le passage sous ou à travers la structure. */
internal object DecorStructures {
    private fun model(id: String, build: DecorBuilder.() -> Unit) =
        DecorModel(id, DecorRoom.STRUCTURE, DecorBuilder().apply(build).parts.toList())

    /** Tube traversable dont l'axe est +X par défaut : les deux murs longent la
     * route, le toit les recouvre, le milieu reste libre pour la voiture. */
    val tunnelArch by lazy { model("structure.tunnel_arch") {
        val length = 34f
        val wallThickness = 2.2f
        val wallHeight = 9f
        val halfSpan = 7.2f
        val roofThickness = 2.4f
        for (side in floatArrayOf(-1f, 1f)) {
            box(0f, wallHeight / 2f, side * (halfSpan + wallThickness / 2f), length, wallHeight, wallThickness, BLUE)
        }
        box(0f, wallHeight + roofThickness / 2f, 0f, length, roofThickness, halfSpan * 2f + wallThickness * 2f, CREAM)
        // Bandeau décoratif pour lire l'entrée du tunnel de loin.
        for (x in floatArrayOf(-length / 2f + 1.2f, length / 2f - 1.2f))
            box(x, wallHeight + roofThickness + 0.6f, 0f, 1.4f, 1.2f, halfSpan * 2f + wallThickness * 2f, GOLD, false)
    } }

    /** Segment de garde-corps posé sur le tablier d'un pont, axe long +Z par défaut. */
    val bridgeRail by lazy { model("structure.bridge_rail") {
        val length = 16f
        val postHeight = 2.4f
        for (z in floatArrayOf(-length / 2f, 0f, length / 2f))
            box(0f, postHeight / 2f, z, 0.6f, postHeight, 0.6f, WOOD)
        box(0f, postHeight, 0f, 0.5f, 0.3f, length, WOOD)
        box(0f, postHeight * 0.45f, 0f, 0.3f, 0.25f, length, CREAM, false)
    } }

    val all by lazy { listOf(tunnelArch, bridgeRail) }
}
