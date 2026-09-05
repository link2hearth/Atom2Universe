package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.DARK
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GLASS
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LEAF
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LILAC
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.METAL
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.MINT
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD

/** Bibliothèque procédurale originale. Les modèles ne sont créés qu'au premier accès. */
internal object DecorCatalog {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel(id, room, DecorBuilder().apply(build).parts.toList())

    val kitchen: List<DecorModel> by lazy {
        listOf(
            model("kitchen.fridge", DecorRoom.KITCHEN) {
                box(0f, 0.6f, 0f, 8.8f, 1.2f, 7.8f, DARK)
                box(0f, 9.6f, 0f, 9.4f, 18f, 8f, MINT)
                oval(0f, 18.4f, 0f, 9.4f, 1.2f, 8f, MINT)
                box(0f, 5.8f, 4.1f, 8.8f, 9.5f, 0.6f, CREAM)
                box(0f, 14.7f, 4.1f, 8.8f, 7.8f, 0.6f, CREAM)
                box(-3.1f, 8f, 4.65f, 0.4f, 3.6f, 0.5f, WOOD)
                box(-3.1f, 13.5f, 4.65f, 0.4f, 2.8f, 0.5f, WOOD)
                for (i in 0..2) oval(1f + i * 0.8f, 15.4f - i, 4.48f, 0.6f, 0.6f, 0.15f, if (i == 1) ROSE else BLUE, false)
                box(1.6f, 7f, 4.46f, 2.3f, 3f, 0.06f, GOLD, false)
                box(1.6f, 7.2f, 4.51f, 1.6f, 0.12f, 0.04f, CREAM, false)
            },
            model("kitchen.oven", DecorRoom.KITCHEN) {
                box(0f, 4.5f, 0f, 9f, 9f, 8f, LILAC)
                box(0f, 9.2f, 0f, 9.4f, 0.5f, 8.4f, CREAM)
                box(0f, 4.4f, 4.1f, 7.8f, 5.5f, 0.3f, DARK)
                box(0f, 4.4f, 4.3f, 6.6f, 4.2f, 0.12f, GLASS, false)
                handle(0f, 6.6f, 4.6f, 5f)
                for (x in floatArrayOf(-2.3f, 2.3f)) for (z in floatArrayOf(-2f, 2f)) {
                    cylinder(x, 9.55f, z, 1.45f, 0.2f, DARK)
                    cylinder(x, 9.68f, z, 0.9f, 0.08f, METAL)
                }
                for (i in 0..3) oval(-3f + i * 2f, 8f, 4.2f, 0.75f, 0.75f, 0.4f, CREAM, false)
            },
            model("kitchen.sink", DecorRoom.KITCHEN) {
                box(0f, 4.5f, 0f, 11f, 9f, 8f, BLUE)
                for (x in floatArrayOf(-2.7f, 2.7f)) {
                    box(x, 4.8f, 4.1f, 5.1f, 7.8f, 0.3f, MINT)
                    handle(x, 7.3f, 4.4f)
                }
                box(0f, 9.3f, 0f, 11.5f, 0.6f, 8.5f, WOOD)
                box(0f, 9.65f, 0f, 7.4f, 0.1f, 5.6f, METAL)
                box(0f, 9.72f, 0f, 6.3f, 0.1f, 4.4f, GLASS)
                cylinder(0f, 11.1f, -2.7f, 0.24f, 3f, METAL)
                box(0f, 12.5f, -1.6f, 0.48f, 0.48f, 2.6f, METAL)
                cylinder(0f, 12.2f, -0.4f, 0.24f, 0.8f, METAL)
                for (x in floatArrayOf(-1.5f, 1.5f)) cylinder(x, 10f, -2.7f, 0.4f, 0.6f, CREAM)
            },
            model("kitchen.counter", DecorRoom.KITCHEN) {
                box(0f, 4.5f, 0f, 12f, 9f, 8f, WOOD)
                box(0f, 9.3f, 0f, 12.5f, 0.6f, 8.5f, CREAM)
                repeat(3) { i ->
                    box(0f, 1.8f + i * 2.7f, 4.1f, 11.3f, 2.4f, 0.35f, if (i == 1) ROSE else MINT)
                    handle(0f, 2f + i * 2.7f, 4.5f, 2.5f)
                }
            },
            model("kitchen.island", DecorRoom.KITCHEN) {
                box(0f, 4.5f, 0f, 16f, 9f, 9f, MINT)
                box(0f, 9.5f, 0f, 20f, 1f, 13f, WOOD)
                box(0f, 10.05f, 0f, 20f, 0.15f, 13f, CREAM)
                for (x in floatArrayOf(-5f, 0f, 5f)) {
                    box(x, 4.8f, 4.6f, 4.6f, 7.5f, 0.25f, BLUE)
                    handle(x, 7.4f, 4.85f)
                }
            },
            model("kitchen.toaster", DecorRoom.KITCHEN) {
                box(0f, 0.25f, 0f, 4.8f, 0.5f, 3.4f, DARK)
                oval(0f, 1.8f, 0f, 5.2f, 3.3f, 3.8f, ROSE)
                for (z in floatArrayOf(-0.7f, 0.7f)) {
                    box(0f, 3.22f, z, 3.5f, 0.12f, 0.38f, DARK, false)
                    box(0f, 3.6f, z, 2.7f, 1.1f, 0.25f, GOLD)
                    box(0f, 3.7f, z + 0.14f, 2.3f, 0.7f, 0.04f, CREAM, false)
                }
                handle(2.55f, 1.6f, 0.5f, 0.6f)
            },
            model("kitchen.kettle", DecorRoom.KITCHEN) {
                cylinder(0f, 0.2f, 0f, 1.8f, 0.4f, DARK)
                oval(0f, 1.8f, 0f, 3.6f, 3.2f, 3.6f, BLUE)
                cylinder(0f, 3.2f, 0f, 1f, 0.3f, CREAM)
                oval(0f, 3.6f, 0f, 0.7f, 0.6f, 0.7f, WOOD)
                box(0f, 2.5f, 1.8f, 0.8f, 1.1f, 1.8f, BLUE)
                box(0f, 2.8f, -2f, 0.5f, 0.5f, 1.6f, WOOD)
                box(0f, 1.8f, -2.6f, 0.5f, 2f, 0.5f, WOOD)
                box(0f, 0.9f, -2f, 0.5f, 0.5f, 1.6f, WOOD)
            },
            model("kitchen.fruit_bowl", DecorRoom.KITCHEN) {
                cylinder(0f, 0.25f, 0f, 1.9f, 0.5f, WOOD)
                oval(0f, 0.7f, 0f, 6f, 1.4f, 4.5f, CREAM)
                for (i in 0..4) {
                    val x = (i % 3 - 1) * 1.3f
                    val z = (i / 3 - 0.5f) * 1.4f
                    oval(x, 1.5f, z, 1.5f, 1.5f, 1.5f, if (i % 2 == 0) ROSE else GOLD)
                    cylinder(x, 2.35f, z, 0.07f, 0.35f, WOOD, false)
                }
            }
        )
    }

    val livingRoom: List<DecorModel> by lazy {
        listOf(
            model("living.sofa", DecorRoom.LIVING_ROOM) {
                legs(17f, 6f, 1.5f, 1f)
                box(0f, 2.5f, 0f, 20f, 2.5f, 9f, LILAC)
                oval(0f, 6f, -3.3f, 20f, 7f, 3.4f, LILAC)
                for (x in floatArrayOf(-9f, 9f)) oval(x, 4.5f, 0f, 3f, 5.5f, 9f, LILAC)
                for (x in floatArrayOf(-5f, 0f, 5f)) oval(x, 4f, 0.5f, 4.8f, 1.4f, 6.4f, ROSE)
                oval(-5.5f, 6.4f, -1.5f, 3.8f, 3.8f, 1.5f, CREAM)
                oval(5.5f, 6.4f, -1.5f, 3.8f, 3.8f, 1.5f, MINT)
            },
            model("living.armchair", DecorRoom.LIVING_ROOM) {
                legs(6f, 6f, 1.5f)
                box(0f, 2.7f, 0f, 8.5f, 2.8f, 8.5f, MINT)
                oval(0f, 6f, -3f, 8.5f, 7f, 3f, MINT)
                for (x in floatArrayOf(-3.7f, 3.7f)) oval(x, 4.5f, 0f, 2.2f, 5f, 8f, MINT)
                oval(0f, 4.3f, 0.5f, 5.6f, 1.6f, 6f, CREAM)
                oval(0f, 6.3f, -1.3f, 3.4f, 3.4f, 1.3f, GOLD)
            },
            model("living.coffee_table", DecorRoom.LIVING_ROOM) {
                legs(11f, 6f, 4f)
                box(0f, 4.3f, 0f, 14f, 0.7f, 9f, WOOD)
                box(0f, 4.72f, 0f, 13.8f, 0.15f, 8.8f, CREAM)
                box(0f, 1.7f, 0f, 11f, 0.45f, 6f, MINT)
            },
            model("living.dining_table", DecorRoom.LIVING_ROOM) {
                legs(17f, 10f, 10f, 1.2f)
                box(0f, 10.5f, 0f, 22f, 1f, 14f, WOOD)
                box(0f, 11.05f, 0f, 5f, 0.1f, 13.8f, BLUE)
                for (x in floatArrayOf(-6.5f, 6.5f)) for (z in floatArrayOf(-3.8f, 3.8f)) {
                    cylinder(x, 11.18f, z, 1.65f, 0.2f, CREAM)
                    cylinder(x, 11.3f, z, 1.2f, 0.05f, ROSE, false)
                }
            },
            model("living.dining_chair", DecorRoom.LIVING_ROOM) {
                legs(4.5f, 4.5f, 5.4f, 0.7f)
                box(0f, 5.6f, 0f, 6f, 0.7f, 6f, WOOD)
                oval(0f, 6f, 0.2f, 5.4f, 0.7f, 5.2f, ROSE)
                for (x in floatArrayOf(-2.5f, 2.5f)) box(x, 8f, -2.6f, 0.7f, 5f, 0.7f, WOOD)
                box(0f, 9.4f, -2.6f, 5.6f, 2.5f, 0.75f, MINT)
            },
            model("living.tv_cabinet", DecorRoom.LIVING_ROOM) {
                legs(17f, 5f, 1.2f)
                box(0f, 1.6f, 0f, 20f, 0.7f, 7f, WOOD)
                box(0f, 5.5f, 0f, 20f, 0.7f, 7f, CREAM)
                for (x in floatArrayOf(-9.5f, 0f, 9.5f)) box(x, 3.6f, 0f, 0.7f, 3.3f, 7f, WOOD)
                box(-4.7f, 3.7f, 2.6f, 8f, 2.8f, 0.6f, BLUE)
                handle(-4.7f, 3.8f, 3.05f)
                box(5f, 2.6f, 1f, 5.6f, 0.7f, 3f, LILAC)
                box(0f, 6.05f, 0f, 6f, 0.4f, 3f, DARK)
                box(0f, 7f, 0f, 0.6f, 1.8f, 0.6f, DARK)
                box(0f, 12f, 0f, 17f, 9f, 0.8f, DARK)
                box(0f, 12.1f, 0.45f, 15.7f, 7.6f, 0.12f, GLASS, false)
                // Image abstraite originale, aucun logo ni émission reproduite.
                oval(-3.5f, 13.5f, 0.55f, 2.5f, 2.5f, 0.08f, GOLD, false)
                box(0f, 9.2f, 0.56f, 15.6f, 1.5f, 0.08f, MINT, false)
            },
            model("living.plant", DecorRoom.LIVING_ROOM) {
                cylinder(0f, 1.8f, 0f, 2.3f, 3.6f, ROSE)
                cylinder(0f, 3.7f, 0f, 2.55f, 0.5f, CREAM)
                cylinder(0f, 4f, 0f, 2.1f, 0.12f, WOOD)
                cylinder(0f, 6.5f, 0f, 0.2f, 5f, WOOD)
                for (i in 0..3) {
                    val side = if (i % 2 == 0) -1f else 1f
                    oval(side * 1.2f, 5.3f + i, 0f, 3.4f, 1.6f, 1.8f, if (i % 2 == 0) LEAF else MINT)
                }
                oval(0f, 9.2f, 0f, 1.8f, 3f, 1.8f, LEAF)
            },
            model("living.floor_lamp", DecorRoom.LIVING_ROOM) {
                cylinder(0f, 0.35f, 0f, 2.7f, 0.7f, WOOD)
                cylinder(0f, 6.8f, 0f, 0.25f, 13f, GOLD)
                cone(0f, 11f, 0f, 3.7f, 4.5f, CREAM)
                oval(0f, 15.4f, 0f, 0.75f, 0.6f, 0.75f, WOOD)
            },
            model("living.books", DecorRoom.LIVING_ROOM) {
                for (i in 0..3) {
                    val x = if (i % 2 == 0) -0.3f else 0.3f
                    val c = intArrayOf(ROSE, BLUE, MINT, LILAC)[i]
                    box(x, 0.5f + i, 0f, 6f, 0.95f, 4.3f, c)
                    box(x, 0.5f + i, 2.18f, 5.5f, 0.6f, 0.08f, CREAM, false)
                }
            }
        )
    }

    val garage: List<DecorModel> by lazy {
        listOf(
            model("garage.workbench", DecorRoom.GARAGE) {
                legs(17f, 7f, 9f, 1.3f, BLUE)
                box(0f, 9.5f, 0f, 21f, 1f, 10f, WOOD)
                box(0f, 2f, 0f, 17f, 0.6f, 7f, BLUE)
                box(0f, 15f, -4.5f, 21f, 10f, 0.7f, MINT)
                for (i in 0..8) for (j in 0..3)
                    box(-9f + i * 2.25f, 12f + j * 2f, -4.1f, 0.14f, 0.14f, 0.04f, DARK, false)
                // Outils en bois peint suspendus au panneau.
                for (i in 0..3) {
                    val x = -7f + i * 4.5f
                    box(x, 15f, -3.8f, 0.45f, 4f, 0.5f, if (i % 2 == 0) ROSE else GOLD)
                    box(x, 17f, -3.75f, 2f, 0.8f, 0.7f, METAL)
                }
                box(6.5f, 10.5f, 2f, 4f, 1f, 3f, LILAC)
                for (x in floatArrayOf(5.3f, 7.7f)) box(x, 11.4f, 2f, 0.65f, 1.5f, 3f, METAL)
            },
            model("garage.tool_chest", DecorRoom.GARAGE) {
                for (x in floatArrayOf(-4f, 4f)) for (z in floatArrayOf(-2.6f, 2.6f)) wheel(x, 0.75f, z, 0.75f, 0.5f, DARK)
                box(0f, 5.3f, 0f, 10f, 8f, 7f, ROSE)
                box(0f, 9.5f, 0f, 10.5f, 0.5f, 7.5f, WOOD)
                repeat(4) { i ->
                    box(0f, 2.5f + i * 1.8f, 3.6f, 9f, 1.5f, 0.3f, if (i % 2 == 0) ROSE else LILAC)
                    handle(0f, 2.7f + i * 1.8f, 3.95f, 6f)
                }
            },
            model("garage.storage_rack", DecorRoom.GARAGE) {
                legs(16f, 6f, 20f, 0.9f, METAL)
                for (y in floatArrayOf(1f, 7f, 13f, 19.5f)) box(0f, y, 0f, 17f, 0.6f, 7f, BLUE)
                for (i in 0..2) {
                    box(-4.5f, 3.1f + i * 6f, 0f, 6f, 3.6f, 5.5f, if (i == 1) GOLD else MINT)
                    box(4.5f, 3.1f + i * 6f, 0f, 6f, 3.6f, 5.5f, if (i == 1) ROSE else WOOD)
                    for (x in floatArrayOf(-4.5f, 4.5f)) box(x, 3.5f + i * 6f, 2.81f, 2.3f, 0.8f, 0.08f, CREAM, false)
                }
            },
            model("garage.tires", DecorRoom.GARAGE) {
                for (i in 0..2) {
                    cylinder(0f, 0.9f + i * 1.8f, 0f, 3f, 1.75f, DARK)
                    cylinder(0f, 1.8f + i * 1.8f, 0f, 1.45f, 0.05f, METAL, false)
                    cylinder(0f, 1.84f + i * 1.8f, 0f, 1.05f, 0.04f, GLASS, false)
                    for (x in floatArrayOf(-2.95f, 2.95f))
                        box(x, 0.9f + i * 1.8f, 0f, 0.1f, 0.18f, 1.3f, METAL, false)
                }
            },
            model("garage.toolbox", DecorRoom.GARAGE) {
                box(0f, 1.3f, 0f, 6f, 2.6f, 3.5f, BLUE)
                box(0f, 2.8f, 0f, 6.2f, 0.5f, 3.7f, CREAM)
                for (x in floatArrayOf(-1.3f, 1.3f)) box(x, 3.5f, 0f, 0.3f, 1.2f, 0.4f, WOOD)
                box(0f, 4.1f, 0f, 2.9f, 0.3f, 0.4f, WOOD)
                for (x in floatArrayOf(-1.8f, 1.8f)) box(x, 2.3f, 1.83f, 0.5f, 0.7f, 0.2f, GOLD, false)
            },
            model("garage.cone", DecorRoom.GARAGE) {
                box(0f, 0.25f, 0f, 4.5f, 0.5f, 4.5f, DARK)
                cone(0f, 0.5f, 0f, 1.85f, 5f, ROSE)
                // Collerette étroite, lisible à hauteur de voiture.
                cylinder(0f, 2.4f, 0f, 1.2f, 0.6f, CREAM)
            },
            model("garage.crate", DecorRoom.GARAGE) {
                box(0f, 2.5f, 0f, 6f, 5f, 5.5f, WOOD)
                for (x in floatArrayOf(-2.7f, 2.7f)) for (z in floatArrayOf(-2.8f, 2.8f)) box(x, 2.5f, z, 0.6f, 5.2f, 0.4f, CREAM)
                for (y in floatArrayOf(0.5f, 2.5f, 4.5f)) for (z in floatArrayOf(-2.8f, 2.8f)) box(0f, y, z, 5.5f, 0.6f, 0.4f, CREAM)
                box(0f, 3f, 3.03f, 2.3f, 1.1f, 0.08f, MINT, false)
            },
            model("garage.barrier", DecorRoom.GARAGE) {
                for (x in floatArrayOf(-4f, 4f)) {
                    box(x, 0.35f, 0f, 2f, 0.7f, 4f, DARK)
                    box(x, 2.7f, 0f, 0.7f, 5f, 0.7f, WOOD)
                }
                box(0f, 4.5f, 0f, 12f, 2f, 0.8f, CREAM)
                for (i in 0..4) box(-4.8f + i * 2.4f, 4.5f, 0.45f, 1.1f, 1.8f, 0.08f, ROSE, false)
            }
        )
    }

    val all: List<DecorModel> by lazy {
        (kitchen + livingRoom + garage + DecorExpansion.all + DecorStructures.all + DecorHouseFurniture.all)
            .also { list -> check(list.map { it.id }.distinct().size == list.size) }
    }
    private val byId by lazy { all.associateBy { it.id } }
    fun forRoom(room: DecorRoom): List<DecorModel> = all.filter { it.room == room }
    operator fun get(id: String): DecorModel = requireNotNull(byId[id]) { "Unknown decor: $id" }
}
