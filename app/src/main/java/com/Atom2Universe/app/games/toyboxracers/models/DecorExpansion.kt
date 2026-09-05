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

/** Deuxième collection : maison quotidienne et premiers éléments de jardin. */
internal object DecorExpansion {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel(id, room, DecorBuilder().apply(build).parts.toList())

    val office by lazy { listOf(
        model("office.pc_tower", DecorRoom.OFFICE) {
            box(0f, 0.35f, 0f, 4.3f, 0.7f, 6.6f, DARK)
            box(0f, 4.7f, 0f, 4.8f, 8.7f, 7f, CREAM)
            box(0f, 4.8f, 3.6f, 4.1f, 7.9f, 0.3f, LILAC)
            for (y in floatArrayOf(2.5f, 5.3f)) {
                oval(0f, y, 3.8f, 2.2f, 2.2f, 0.18f, DARK, false)
                oval(0f, y, 3.92f, 1.7f, 1.7f, 0.12f, MINT, false)
                oval(0f, y, 4f, 0.55f, 0.55f, 0.08f, CREAM, false)
            }
            oval(1.25f, 8.1f, 3.82f, 0.4f, 0.4f, 0.1f, BLUE, false)
            for (x in floatArrayOf(-1.3f, -0.5f)) box(x, 8.1f, 3.81f, 0.5f, 0.18f, 0.08f, DARK, false)
            for (i in 0..5) box(2.43f, 2.6f + i * 0.7f, 0f, 0.06f, 0.12f, 4f, METAL, false)
        },
        model("office.computer", DecorRoom.OFFICE) {
            box(0f, 0.25f, -1f, 4.5f, 0.5f, 3f, WOOD)
            box(0f, 2f, -1f, 0.55f, 3.5f, 0.55f, METAL)
            box(0f, 5.6f, -1f, 10f, 6.2f, 0.6f, CREAM)
            box(0f, 5.7f, -0.65f, 9.2f, 5.3f, 0.12f, GLASS, false)
            box(-1.6f, 5.6f, -0.56f, 4.8f, 3.5f, 0.06f, BLUE, false)
            for (i in 0..2) box(-1.6f, 6.5f - i * 0.7f, -0.51f, 3.5f, 0.15f, 0.04f, CREAM, false)
            box(0f, 0.25f, 3f, 8f, 0.5f, 2.5f, LILAC)
            for (i in 0..9) for (j in 0..2) box(-3.4f + i * 0.75f, 0.54f, 2.2f + j * 0.7f, 0.55f, 0.08f, 0.45f, CREAM, false)
            oval(5.6f, 0.4f, 3f, 1.4f, 0.8f, 2f, MINT)
        },
        model("office.exercise_bike", DecorRoom.OFFICE) {
            for (z in floatArrayOf(-4f, 4f)) {
                box(0f, 0.5f, z, 7f, 1f, 1.3f, METAL)
                for (x in floatArrayOf(-3.2f, 3.2f)) box(x, 0.5f, z, 0.9f, 1f, 1.5f, DARK)
            }
            box(0f, 1.3f, 0f, 1.6f, 1.6f, 8f, BLUE)
            oval(0f, 3f, 0f, 2.6f, 4.4f, 7f, MINT)
            wheel(0f, 3.1f, 1.8f, 1.8f, 3f, DARK)
            wheel(0f, 3.1f, 1.8f, 1.4f, 3.1f, CREAM)
            box(0f, 5.7f, -2.3f, 0.8f, 5.3f, 0.8f, METAL)
            oval(0f, 8.5f, -2.5f, 3.8f, 1f, 3f, DARK)
            box(0f, 6.8f, 3.4f, 0.8f, 7f, 0.8f, METAL)
            box(0f, 10.4f, 3.4f, 5.4f, 0.6f, 0.6f, DARK)
            for (x in floatArrayOf(-2.4f, 2.4f)) box(x, 10.4f, 2.7f, 0.6f, 0.6f, 2f, DARK)
            box(0f, 10.7f, 3.3f, 2.5f, 1.2f, 0.7f, LILAC)
            box(0f, 10.8f, 3.71f, 1.8f, 0.7f, 0.08f, GLASS, false)
            wheel(0f, 2.8f, -0.5f, 0.4f, 5.2f, METAL)
            for (x in floatArrayOf(-2.6f, 2.6f)) box(x, 2.8f, -0.5f, 1.4f, 0.4f, 2f, DARK)
        },
        model("office.dresser", DecorRoom.OFFICE) {
            legs(10f, 5f, 1.4f)
            box(0f, 6f, 0f, 13f, 9.5f, 7f, WOOD)
            box(0f, 11f, 0f, 13.6f, 0.5f, 7.6f, CREAM)
            repeat(3) { i ->
                box(0f, 3.2f + i * 3f, 3.6f, 12f, 2.6f, 0.35f, intArrayOf(MINT, BLUE, LILAC)[i])
                for (x in floatArrayOf(-3f, 3f)) handle(x, 3.2f + i * 3f, 4f)
            }
        },
        model("kitchen.microwave", DecorRoom.KITCHEN) {
            box(0f, 2.8f, 0f, 9f, 5.6f, 6f, CREAM)
            box(-1f, 2.9f, 3.1f, 6.4f, 4.6f, 0.3f, DARK)
            box(-1.1f, 2.9f, 3.3f, 5.3f, 3.5f, 0.1f, GLASS, false)
            box(2f, 2.9f, 3.55f, 0.4f, 3.4f, 0.4f, WOOD)
            box(3.4f, 4.3f, 3.15f, 1.3f, 0.7f, 0.12f, MINT, false)
            for (y in floatArrayOf(1.5f, 2.8f)) oval(3.4f, y, 3.2f, 0.8f, 0.8f, 0.3f, LILAC, false)
        }
    ) }

    val bathroom by lazy { listOf(
        model("bathroom.mirror", DecorRoom.BATHROOM) {
            // Face plane distincte : un futur shader de réflexion pourra remplacer cet aplat.
            box(0f, 6f, 0f, 10f, 12f, 0.65f, WOOD)
            box(0f, 6f, 0.4f, 8.7f, 10.7f, 0.15f, 0xBBDDE4, false)
            box(-2.8f, 7f, 0.51f, 0.2f, 7.6f, 0.04f, CREAM, false)
            box(-2.2f, 9f, 0.51f, 0.1f, 3.8f, 0.04f, CREAM, false)
        },
        model("bathroom.shower", DecorRoom.BATHROOM) {
            box(0f, 0.5f, 0f, 12f, 1f, 12f, CREAM)
            box(0f, 9.5f, -5.7f, 12f, 18f, 0.6f, BLUE)
            box(-5.7f, 9.5f, 0f, 0.6f, 18f, 12f, MINT)
            // Cabine ouverte : pas de faux vitrage opaque devant l'entrée.
            for (x in floatArrayOf(-5.5f, 5.5f)) cylinder(x, 10f, 5.5f, 0.18f, 19f, METAL)
            box(0f, 19.5f, 5.5f, 11f, 0.35f, 0.35f, METAL)
            cylinder(0f, 12f, -5f, 0.2f, 12f, METAL)
            box(0f, 17.8f, -3.5f, 0.4f, 0.4f, 3.4f, METAL)
            cylinder(0f, 17.6f, -1.8f, 1.3f, 0.35f, CREAM)
            box(0f, 7f, -5.1f, 3f, 0.6f, 0.5f, METAL)
            cylinder(0f, 1.04f, 0f, 0.6f, 0.06f, METAL, false)
            for (y in floatArrayOf(4f, 8f, 12f, 16f)) box(0f, y, -5.35f, 11.2f, 0.08f, 0.03f, CREAM, false)
        },
        model("bathroom.vanity", DecorRoom.BATHROOM) {
            legs(9f, 5f, 1.4f)
            box(0f, 5f, 0f, 11f, 7.2f, 7f, MINT)
            for (x in floatArrayOf(-2.6f, 2.6f)) {
                box(x, 5f, 3.6f, 4.8f, 6.4f, 0.3f, LILAC)
                handle(x, 7f, 3.95f)
            }
            box(0f, 9f, 0f, 12f, 0.8f, 8f, WOOD)
            oval(0f, 9.8f, 0f, 8f, 1.8f, 5.8f, CREAM)
            oval(0f, 10.7f, 0.2f, 6f, 0.16f, 3.8f, BLUE, false)
            cylinder(0f, 10.8f, -2.9f, 0.22f, 2.8f, METAL)
            box(0f, 12.2f, -2f, 0.45f, 0.4f, 2f, METAL)
        },
        model("bathroom.towel_rack", DecorRoom.BATHROOM) {
            for (x in floatArrayOf(-3f, 3f)) {
                box(x, 0.3f, 0f, 1.6f, 0.6f, 4f, WOOD)
                cylinder(x, 5f, 0f, 0.25f, 10f, METAL)
            }
            box(0f, 10f, 0f, 6.5f, 0.4f, 0.4f, METAL)
            box(-0.5f, 7f, 0.35f, 4.4f, 6f, 0.25f, ROSE)
            box(-0.5f, 4.5f, 0.51f, 4.2f, 0.3f, 0.04f, CREAM, false)
            box(-0.5f, 8.8f, -0.35f, 4.4f, 2.4f, 0.25f, ROSE)
        },
        model("bathroom.bathtub", DecorRoom.BATHROOM) {
            legs(7f, 13f, 1.3f, 1.2f, WOOD)
            // Fond et quatre parois : la cavité reste réellement ouverte.
            box(0f, 1.5f, 0f, 10f, 0.8f, 18f, CREAM)
            for (x in floatArrayOf(-4.8f, 4.8f)) box(x, 3.3f, 0f, 1f, 3.8f, 18f, CREAM)
            for (z in floatArrayOf(-8.5f, 8.5f)) box(0f, 3.3f, z, 9f, 3.8f, 1f, CREAM)
            box(0f, 1.95f, 0f, 8.4f, 0.08f, 15.6f, BLUE, false)
            for (x in floatArrayOf(-4.8f, 4.8f)) box(x, 5.3f, 0f, 1.3f, 0.3f, 18.4f, WOOD)
            cylinder(0f, 6f, -8.4f, 0.22f, 2.3f, METAL)
            box(0f, 7f, -7.5f, 0.45f, 0.4f, 2f, METAL)
            box(0f, 5.7f, -1f, 11f, 0.45f, 2f, MINT)
        },
        model("bathroom.toilet", DecorRoom.BATHROOM) {
            oval(0f, 0.5f, 0f, 4.6f, 1f, 6.4f, CREAM)
            oval(0f, 2f, 0f, 3.5f, 3.4f, 4.8f, CREAM)
            oval(0f, 3.4f, 0.5f, 5f, 2.7f, 7f, CREAM)
            oval(0f, 4.5f, 0.8f, 5.2f, 0.5f, 6.6f, WOOD)
            oval(0f, 4.77f, 0.8f, 3.6f, 0.13f, 4.7f, GLASS, false)
            box(0f, 5.9f, -2.6f, 5.2f, 5f, 2.4f, CREAM)
            box(0f, 8.6f, -2.6f, 5.5f, 0.4f, 2.7f, CREAM)
            cylinder(1.3f, 8.86f, -2.6f, 0.4f, 0.12f, METAL, false)
        },
        model("bathroom.toilet_paper", DecorRoom.BATHROOM) {
            cylinder(0f, 0.2f, 0f, 1.5f, 0.4f, WOOD)
            cylinder(0f, 3f, 0f, 0.18f, 5.6f, METAL)
            wheel(0f, 5.5f, 0f, 0.2f, 3.5f, METAL)
            wheel(0f, 5.5f, 0f, 1.1f, 2.3f, CREAM)
            wheel(0f, 5.5f, 0f, 0.4f, 2.34f, WOOD)
            box(0f, 4.1f, 1.02f, 2.1f, 2.4f, 0.08f, CREAM, false)
            for (y in floatArrayOf(3.5f, 4.3f)) box(0f, y, 1.07f, 1.8f, 0.035f, 0.02f, WOOD, false)
        },
        model("bathroom.washer", DecorRoom.BATHROOM) {
            box(0f, 4.5f, 0f, 9f, 9f, 8f, CREAM)
            box(0f, 9.2f, 0f, 9.4f, 0.4f, 8.4f, WOOD)
            oval(0f, 4f, 4.15f, 6.2f, 6.2f, 0.45f, METAL)
            oval(0f, 4f, 4.44f, 4.9f, 4.9f, 0.15f, GLASS, false)
            oval(-0.8f, 3.6f, 4.55f, 2.7f, 2.2f, 0.08f, LILAC, false)
            box(-2.5f, 7.7f, 4.13f, 3f, 1f, 0.18f, BLUE, false)
            oval(2.5f, 7.7f, 4.15f, 1f, 1f, 0.3f, MINT, false)
        },
        model("bathroom.laundry_basket", DecorRoom.BATHROOM) {
            box(0f, 0.3f, 0f, 6f, 0.6f, 5f, WOOD)
            for (x in floatArrayOf(-2.8f, 2.8f)) box(x, 3f, 0f, 0.5f, 6f, 5f, MINT)
            for (z in floatArrayOf(-2.3f, 2.3f)) box(0f, 3f, z, 6f, 6f, 0.5f, MINT)
            for (i in 0..3) for (z in floatArrayOf(-2.58f, 2.58f)) box(0f, 1f + i * 1.3f, z, 5.2f, 0.2f, 0.05f, CREAM, false)
            oval(0f, 5.8f, 0f, 5.3f, 1.8f, 4.4f, LILAC)
            box(1.6f, 4.8f, 2.7f, 1.7f, 3.3f, 0.3f, ROSE)
        }
    ) }

    val outdoor by lazy { listOf(
        model("outdoor.house", DecorRoom.OUTDOOR) {
            box(0f, 1f, 0f, 40f, 2f, 28f, WOOD)
            box(0f, 15f, 0f, 38f, 28f, 26f, CREAM)
            roof(0f, 29f, 0f, 43f, 11f, 31f, LILAC)
            box(11f, 36f, -5f, 4f, 12f, 4f, ROSE)
            box(11f, 42.2f, -5f, 4.8f, 0.5f, 4.8f, WOOD)
            box(0f, 9f, 13.2f, 7f, 16f, 0.5f, MINT)
            box(2.2f, 9f, 13.6f, 0.4f, 0.4f, 0.4f, GOLD, false)
            for (x in floatArrayOf(-12f, 12f)) {
                box(x, 17f, 13.2f, 8f, 10f, 0.5f, WOOD)
                box(x, 17f, 13.52f, 6.8f, 8.8f, 0.12f, BLUE, false)
                box(x, 17f, 13.65f, 0.4f, 8.8f, 0.12f, CREAM, false)
                box(x, 17f, 13.65f, 6.8f, 0.4f, 0.12f, CREAM, false)
                box(x, 11.6f, 14f, 9f, 0.5f, 2f, WOOD)
            }
            // Les quatre côtés sont finis pour pouvoir faire le tour de la maison.
            for (x in floatArrayOf(-19.1f, 19.1f)) for (z in floatArrayOf(-6f, 6f)) {
                box(x, 17f, z, 0.3f, 9f, 7f, WOOD)
                box(x * 1.012f, 17f, z, 0.15f, 7.8f, 5.8f, BLUE, false)
            }
            box(0f, 16f, -13.2f, 12f, 9f, 0.4f, BLUE)
            box(0f, 16f, -13.5f, 0.5f, 9f, 0.2f, CREAM, false)
        },
        model("outdoor.porch", DecorRoom.OUTDOOR) {
            box(0f, 0.5f, 2f, 14f, 1f, 8f, WOOD)
            box(0f, 1.5f, 0f, 14f, 1f, 4f, CREAM)
            for (x in floatArrayOf(-6f, 6f)) box(x, 10f, -1f, 0.9f, 18f, 0.9f, MINT)
            roof(0f, 19f, -1f, 16f, 4f, 9f, LILAC)
        },
        model("outdoor.garage_door", DecorRoom.OUTDOOR) {
            box(0f, 9f, 0f, 23f, 18f, 0.8f, WOOD)
            box(0f, 8.7f, 0.5f, 21f, 17f, 0.4f, BLUE)
            for (i in 0..6) box(0f, 1f + i * 2.5f, 0.75f, 20.6f, 0.12f, 0.08f, CREAM, false)
            for (x in floatArrayOf(-6f, 0f, 6f)) box(x, 14.6f, 0.8f, 4f, 1.7f, 0.12f, GLASS, false)
            handle(0f, 3f, 1f, 3f)
        },
        model("outdoor.fence", DecorRoom.OUTDOOR) {
            for (x in floatArrayOf(-6f, 6f)) box(x, 4.5f, 0f, 1f, 9f, 1f, WOOD)
            for (y in floatArrayOf(2.5f, 6f)) box(0f, y, 0f, 13f, 0.6f, 0.7f, MINT)
            for (i in 0..5) {
                val x = -5f + i * 2f
                box(x, 4.4f, 0.55f, 1.2f, 7.8f, 0.5f, CREAM)
                roof(x, 8.3f, 0.55f, 1.2f, 0.7f, 0.5f, CREAM)
            }
        },
        model("outdoor.tree", DecorRoom.OUTDOOR) {
            cylinder(0f, 5.5f, 0f, 1.1f, 11f, WOOD)
            oval(0f, 14f, 0f, 13f, 13f, 12f, LEAF)
            oval(-4f, 11.5f, 0f, 8f, 8f, 8f, MINT)
            oval(4f, 12f, 1f, 8f, 9f, 8f, LEAF)
            for (x in floatArrayOf(-3f, 2f)) oval(x, 12f, 5.3f, 1.2f, 1.2f, 1.2f, ROSE, false)
        },
        model("outdoor.hedge", DecorRoom.OUTDOOR) {
            box(0f, 0.4f, 0f, 15f, 0.8f, 5.5f, WOOD)
            for (x in floatArrayOf(-5f, 0f, 5f)) oval(x, 3.8f, 0f, 7f, 7f, 6f, if (x == 0f) MINT else LEAF)
        },
        model("outdoor.bench", DecorRoom.OUTDOOR) {
            legs(11f, 4f, 4.5f, 0.8f, METAL)
            for (z in floatArrayOf(-2f, 0f, 2f)) box(0f, 4.8f, z, 14f, 0.6f, 1.7f, WOOD)
            for (x in floatArrayOf(-5.5f, 5.5f)) box(x, 6.5f, -2f, 0.6f, 5f, 0.6f, METAL)
            for (y in floatArrayOf(6.8f, 8.5f)) box(0f, y, -2.2f, 14f, 1.3f, 0.5f, MINT)
        },
        model("outdoor.mailbox", DecorRoom.OUTDOOR) {
            box(0f, 0.3f, 0f, 3f, 0.6f, 3f, WOOD)
            box(0f, 4.8f, 0f, 0.8f, 9f, 0.8f, WOOD)
            box(0f, 9.8f, 0f, 4.5f, 3.5f, 5f, BLUE)
            roof(0f, 11.55f, 0f, 5f, 1.7f, 5.5f, LILAC)
            box(0f, 10.1f, 2.58f, 3.2f, 0.3f, 0.08f, DARK, false)
            handle(0f, 8.8f, 2.8f)
            box(2.55f, 11f, 0f, 0.25f, 3f, 0.5f, WOOD)
            box(2.55f, 12.1f, 0.65f, 0.3f, 1f, 1.5f, ROSE)
        },
        model("outdoor.family_car", DecorRoom.OUTDOOR) {
            box(0f, 2.3f, 0f, 9f, 2f, 19f, MINT)
            box(0f, 4f, 0f, 9.5f, 2f, 19f, MINT)
            box(0f, 6.4f, -1f, 8f, 3.4f, 10f, CREAM)
            box(0f, 8.25f, -1f, 8.3f, 0.35f, 10.3f, CREAM)
            box(0f, 6.7f, 4.1f, 7f, 2.2f, 0.15f, GLASS, false)
            box(0f, 6.7f, -6.1f, 7f, 2.2f, 0.15f, GLASS, false)
            for (x in floatArrayOf(-4.05f, 4.05f)) for (z in floatArrayOf(-3.5f, 1.5f)) {
                box(x, 6.7f, z, 0.15f, 2.2f, 4f, GLASS, false)
                box(x * 1.17f, 4.6f, z, 0.15f, 0.2f, 1.2f, CREAM, false)
            }
            for (x in floatArrayOf(-4.7f, 4.7f)) for (z in floatArrayOf(-5.8f, 5.8f)) {
                wheel(x, 1.9f, z, 1.9f, 1.2f, DARK)
                wheel(x * 1.13f, 1.9f, z, 1f, 0.15f, CREAM)
            }
            for (x in floatArrayOf(-3.3f, 3.3f)) {
                box(x, 4f, 9.6f, 1.6f, 0.9f, 0.2f, GOLD, false)
                box(x, 4f, -9.6f, 1.4f, 0.7f, 0.2f, ROSE, false)
            }
            for (z in floatArrayOf(-9.6f, 9.6f)) box(0f, 2.7f, z, 9.5f, 0.6f, 0.6f, WOOD)
        },
        model("outdoor.planter", DecorRoom.OUTDOOR) {
            box(0f, 1.6f, 0f, 10f, 3.2f, 4f, WOOD)
            box(0f, 3.3f, 0f, 10.5f, 0.3f, 4.5f, CREAM)
            box(0f, 3.5f, 0f, 9.5f, 0.12f, 3.5f, WOOD, false)
            for (i in 0..3) {
                val x = -3.6f + i * 2.4f
                cylinder(x, 4.5f, 0f, 0.1f, 2.4f, LEAF)
                oval(x + 0.45f, 4.4f, 0f, 1.3f, 0.55f, 0.7f, LEAF, false)
                oval(x, 5.8f, 0f, 1.8f, 1.8f, 1.8f, if (i % 2 == 0) ROSE else LILAC)
                oval(x, 5.8f, 0.8f, 0.6f, 0.6f, 0.3f, GOLD, false)
            }
        }
    ) }

    val all by lazy { office + bathroom + outdoor }
}
