package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD

/** Mobilier propre au mode Maison : l'étagère-escabeau et l'armoire qui closent
 * le parcours signature. Un volume par pièce solide, comme tout le reste du
 * catalogue — les plateaux servent de marches, jamais une boîte englobante. */
internal object DecorHouseFurniture {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel(id, room, DecorBuilder().apply(build).parts.toList())

    /** Quatre marches montantes vers +X, calées sur la hauteur de la commode
     * voisine (sommet vers 14, échelle 1,25 comprise) : chacune sert de plateau. */
    val shelfLadder by lazy { model("office.shelf_ladder", DecorRoom.OFFICE) {
        box(5f, 12f, 0f, 6f, 4f, 8f, WOOD)
        box(11f, 16f, 0f, 6f, 4f, 8f, WOOD)
        box(17f, 20f, 0f, 6f, 4f, 8f, WOOD)
        box(24f, 24f, 0f, 8f, 4f, 10f, WOOD)
        box(24f, 26.3f, 0f, 8.4f, 0.6f, 10.4f, CREAM)
    } }

    /** Armoire d'arrivée : plateau nettement plus bas que le sommet de
     * l'étagère, pour un saut vers l'avant et vers le bas, franchissable
     * au premier essai à vitesse normale. */
    val wardrobe by lazy { model("bedroom.wardrobe", DecorRoom.BEDROOM) {
        box(0f, 9f, 0f, 14f, 18f, 8f, WOOD)
        box(0f, 18.5f, 0f, 14.6f, 1f, 8.6f, CREAM)
        for (x in floatArrayOf(-3.5f, 3.5f)) box(x, 9f, 4.05f, 6.5f, 17f, 0.1f, CREAM, false)
        for (x in floatArrayOf(-1.2f, 1.2f)) handle(x, 9f, 4.1f)
        box(0f, 19.1f, 0f, 4f, 0.6f, 1.4f, GOLD, false)
    } }

    val all by lazy { listOf(shelfLadder, wardrobe) }
}
