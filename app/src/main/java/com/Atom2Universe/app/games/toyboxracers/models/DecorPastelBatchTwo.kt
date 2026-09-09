package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.DARK
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LEAF
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LILAC
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.METAL
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.MINT
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD

/** Second toy-box batch. Faces belong to animal figures only, never everyday objects. */
internal object DecorPastelBatchTwo {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel("kawaii.$id", room, DecorBuilder().apply(build).parts.toList())

    val toys by lazy { listOf(
        model("rocket", DecorRoom.BEDROOM) {
            cylinder(0f,3.4f,0f,1.35f,4.6f,CREAM)
            cone(0f,5.7f,0f,1.35f,2.4f,ROSE)
            cylinder(0f,1.2f,0f,1.15f,.6f,METAL)
            for (s in floatArrayOf(-1f,1f)) {
                box(s*1.45f,1.15f,0f,.65f,2.3f,1.5f,MINT)
                box(0f,1.15f,s*1.45f,1.5f,2.3f,.65f,MINT)
            }
            oval(0f,4.4f,1.26f,1.65f,1.65f,.38f,BLUE,false)
            oval(0f,4.4f,1.49f,1.15f,1.15f,.16f,DARK,false)
            box(-.23f,4.65f,1.6f,.22f,.42f,.04f,CREAM,false)
            for (i in 0..2) box(-.45f+i*.45f,2.5f,1.3f,.18f,.38f,.12f,GOLD,false)
        },
        model("stacking_rings", DecorRoom.BEDROOM) {
            cylinder(0f,.3f,0f,2.55f,.6f,WOOD)
            cylinder(0f,2.8f,0f,.27f,5f,CREAM)
            val colors = intArrayOf(BLUE,MINT,LILAC,ROSE,GOLD)
            for (i in 0..4) {
                val radius = 2.2f-i*.33f
                cylinder(0f,1f+i*.78f,0f,radius,.62f,colors[i])
                cylinder(0f,1.32f+i*.78f,0f,.39f,.035f,WOOD,false)
            }
            oval(0f,5.35f,0f,.85f,.7f,.85f,CREAM)
        }
    ) }

    val tools by lazy { listOf(
        model("wrench", DecorRoom.GARAGE) {
            box(0f,.35f,1.6f,.9f,.7f,5.3f,BLUE)
            oval(0f,.35f,3.7f,1.4f,.7f,1.35f,BLUE)
            box(0f,.35f,-1.1f,2.8f,.7f,.8f,METAL)
            for (s in floatArrayOf(-1f,1f)) box(s*1.05f,.35f,-2f,.7f,.7f,2.5f,METAL)
            box(0f,.72f,1.4f,.34f,.05f,2.8f,CREAM,false)
        },
        model("paintbrush", DecorRoom.GARAGE) {
            oval(0f,.4f,2.2f,1.25f,.8f,4.6f,LILAC)
            box(0f,.4f,-.25f,1.9f,.72f,1.2f,METAL)
            box(0f,.32f,-2f,2.1f,.64f,2.5f,CREAM)
            for (i in 0..6) {
                box(-.9f+i*.3f,.66f,-2.1f,.045f,.025f,1.7f,WOOD,false)
                box(-.9f+i*.3f,.34f,-3.25f-(i%3)*.12f,.28f,.68f,.55f,MINT)
            }
        }
    ) }

    val kitchen by lazy { listOf(
        model("wooden_spoon", DecorRoom.KITCHEN) {
            oval(0f,.32f,-1.5f,2.7f,.64f,3.8f,WOOD)
            oval(0f,.59f,-1.65f,1.9f,.12f,2.65f,GOLD,false)
            box(0f,.35f,2f,.55f,.45f,4.8f,WOOD)
            oval(0f,.35f,4.2f,.85f,.7f,1.2f,MINT)
        },
        model("saucepan", DecorRoom.KITCHEN) {
            box(0f,.2f,0f,4.2f,.4f,4.2f,BLUE)
            for (s in floatArrayOf(-1f,1f)) {
                box(s*2f,1.35f,0f,.4f,2.3f,4.2f,BLUE)
                box(0f,1.35f,s*2f,4.2f,2.3f,.4f,BLUE)
                box(s*2f,2.52f,0f,.48f,.16f,4.3f,CREAM)
                box(0f,2.52f,s*2f,4.3f,.16f,.48f,CREAM)
                for (z in floatArrayOf(-.7f,.7f)) box(s*2.7f,1.8f,z,1.5f,.4f,.4f,WOOD)
                box(s*3.35f,1.8f,0f,.4f,.4f,1.8f,WOOD)
            }
            cylinder(6f,.18f,0f,2.3f,.36f,CREAM)
            oval(6f,.4f,0f,4.4f,.65f,4.4f,BLUE)
            cylinder(6f,.92f,0f,.5f,.5f,WOOD)
        }
    ) }

    val gaming by lazy { listOf(
        model("gamepad", DecorRoom.BEDROOM) {
            box(0f,.8f,0f,5.4f,1.3f,2.6f,LILAC)
            for (s in floatArrayOf(-1f,1f)) oval(s*2.7f,.85f,.45f,2.6f,1.7f,3.6f,LILAC)
            box(-1.9f,1.63f,0f,1.55f,.18f,.45f,DARK,false)
            box(-1.9f,1.63f,0f,.45f,.18f,1.55f,DARK,false)
            for (i in 0..3) {
                val x = 1.85f + if (i == 0) -.55f else if (i == 1) .55f else 0f
                val z = if (i == 2) -.55f else if (i == 3) .55f else 0f
                cylinder(x,1.65f,z,.25f,.18f,intArrayOf(MINT,ROSE,BLUE,GOLD)[i],false)
            }
            for (x in floatArrayOf(-.35f,.35f)) box(x,1.5f,.5f,.4f,.12f,.2f,CREAM,false)
        },
        model("floppy_disk", DecorRoom.OFFICE) {
            box(0f,2.8f,0f,5.6f,5.6f,.65f,LILAC)
            box(0f,4.65f,.38f,3.6f,1.6f,.12f,METAL,false)
            box(.6f,4.65f,.46f,.7f,1.15f,.055f,DARK,false)
            box(0f,1.9f,.38f,4.4f,2.65f,.12f,CREAM,false)
            box(0f,2.85f,.47f,4.1f,.5f,.05f,MINT,false)
            for (i in 0..2) box(-.15f,1.1f+i*.5f,.47f,3.2f,.08f,.05f,BLUE,false)
            for (s in floatArrayOf(-1f,1f)) box(s*2.2f,.45f,.36f,.5f,.4f,.1f,DARK,false)
        }
    ) }

    val books by lazy { listOf(
        model("pencil", DecorRoom.OFFICE) {
            cylinder(0f,.55f,0f,.63f,1.1f,ROSE)
            cylinder(0f,1.3f,0f,.66f,.45f,METAL)
            cylinder(0f,4.05f,0f,.6f,5.1f,MINT)
            cone(0f,6.6f,0f,.6f,1.6f,WOOD)
            cone(0f,7.75f,0f,.17f,.45f,DARK)
            box(0f,4.1f,.59f,.17f,3.4f,.055f,CREAM,false)
        },
        model("eraser", DecorRoom.OFFICE) {
            box(-1.2f,.65f,0f,2.4f,1.3f,2.3f,ROSE)
            box(1.2f,.65f,0f,2.4f,1.3f,2.3f,BLUE)
            box(0f,.68f,0f,1.65f,1.4f,2.4f,CREAM)
            for (i in 0..2) box(-.45f+i*.45f,1.405f,0f,.12f,.035f,1.4f,MINT,false)
        }
    ) }

    val figures by lazy { listOf(
        model("rabbit", DecorRoom.BEDROOM) {
            oval(0f,1.65f,0f,3f,3.3f,2.8f,CREAM)
            for (s in floatArrayOf(-1f,1f)) {
                oval(s*.85f,.4f,.8f,1.3f,.8f,1.8f,CREAM)
                oval(s*.72f,5.8f,0f,.9f,3.5f,1.1f,CREAM)
                oval(s*.72f,5.9f,.49f,.44f,2.7f,.18f,ROSE,false)
            }
            oval(0f,3.7f,.15f,3.4f,2.8f,2.9f,CREAM)
            for (s in floatArrayOf(-1f,1f)) {
                oval(s*.63f,3.95f,1.47f,.22f,.35f,.24f,DARK,false)
                oval(s*.9f,3.5f,1.39f,.4f,.2f,.22f,ROSE,false)
            }
            oval(0f,3.62f,1.62f,.3f,.2f,.2f,ROSE,false)
            box(0f,3.35f,1.61f,.08f,.28f,.08f,DARK,false)
            oval(0f,1.35f,-1.5f,1.1f,1.1f,1.1f,CREAM)
        },
        model("penguin", DecorRoom.OUTDOOR) {
            for (s in floatArrayOf(-1f,1f)) {
                oval(s*.68f,.25f,.65f,1.2f,.5f,1.65f,GOLD)
                oval(s*1.4f,2.2f,0f,.7f,2.8f,1.1f,BLUE)
            }
            oval(0f,2.45f,0f,3f,4.5f,2.8f,BLUE)
            oval(0f,1.95f,1.1f,2.1f,2.75f,.7f,CREAM,false)
            oval(0f,3.55f,1f,2.3f,1.7f,.7f,CREAM,false)
            for (s in floatArrayOf(-1f,1f)) oval(s*.55f,3.85f,1.27f,.24f,.32f,.24f,DARK,false)
            oval(0f,3.4f,1.55f,.65f,.35f,.55f,GOLD,false)
            box(0f,2.9f,1.2f,1.9f,.35f,.55f,ROSE,false)
            box(.55f,2.4f,1.4f,.45f,1.1f,.2f,ROSE,false)
        }
    ) }

    val fruit by lazy { listOf(
        model("pear", DecorRoom.KITCHEN) {
            oval(0f,1.65f,0f,3.5f,3.3f,3.3f,GOLD)
            oval(0f,3.05f,0f,2.1f,2.6f,2.1f,GOLD)
            cylinder(0f,4.5f,0f,.16f,.65f,WOOD)
            oval(.55f,4.65f,0f,1.4f,.2f,.7f,LEAF,false)
            for (i in 0..4) oval(-.8f+i*.4f,1.8f+(i%2)*.4f,1.57f,.065f,.065f,.12f,WOOD,false)
        },
        model("cherries", DecorRoom.KITCHEN) {
            for (s in floatArrayOf(-1f,1f)) {
                oval(s*1.2f,1.15f,0f,2.5f,2.3f,2.5f,ROSE)
                cylinder(s*1.2f,3.25f,0f,.12f,2.3f,LEAF,false)
                oval(s*1.1f,1.7f,.95f,.4f,.5f,.15f,CREAM,false)
            }
            box(0f,4.38f,0f,2.5f,.22f,.22f,LEAF,false)
            oval(.5f,4.52f,0f,2f,.25f,.85f,MINT,false)
        }
    ) }

    val trackside by lazy { listOf(
        model("potted_cactus", DecorRoom.OUTDOOR) {
            cylinder(0f,.95f,0f,1.6f,1.9f,ROSE)
            cylinder(0f,1.85f,0f,1.8f,.45f,CREAM)
            cylinder(0f,2.1f,0f,1.5f,.08f,WOOD,false)
            cylinder(0f,3.85f,0f,.65f,3.5f,MINT)
            oval(0f,5.58f,0f,1.3f,1.1f,1.3f,MINT)
            for (s in floatArrayOf(-1f,1f)) {
                box(s*.8f,3.3f,0f,1.3f,.7f,.9f,MINT)
                cylinder(s*1.35f,3.85f,0f,.45f,1.4f,MINT)
                oval(s*1.35f,4.55f,0f,.9f,.65f,.9f,MINT)
            }
            for (i in 0..4) box(0f,2.8f+i*.5f,.65f,.055f,.18f,.09f,CREAM,false)
            oval(.2f,6.08f,0f,.7f,.55f,.7f,LILAC,false)
        },
        model("sandcastle", DecorRoom.OUTDOOR) {
            box(0f,.18f,0f,8.5f,.36f,7.4f,0xEBD5AD)
            // Open gate: side pillars and a lintel instead of a solid central block.
            for (s in floatArrayOf(-1f,1f)) box(s*1.85f,1.7f,0f,1.4f,2.7f,4.8f,GOLD)
            box(0f,3.1f,0f,5.1f,.7f,4.8f,GOLD)
            for (x in floatArrayOf(-3f,3f)) for (z in floatArrayOf(-2.3f,2.3f)) {
                cylinder(x,2f,z,.95f,3.65f,0xE4C795)
                for (i in 0..3) {
                    val angle = i*kotlin.math.PI.toFloat()*.5f
                    box(x+kotlin.math.cos(angle)*.65f,4.05f,z+kotlin.math.sin(angle)*.65f,.5f,.65f,.5f,0xE4C795)
                }
            }
            cylinder(0f,4.6f,0f,.09f,2.5f,WOOD,false)
            box(.65f,5.5f,0f,1.3f,.7f,.08f,MINT,false)
        }
    ) }

    val names = mapOf(
        "kawaii.rocket" to R.string.toybox_prop_rocket,
        "kawaii.stacking_rings" to R.string.toybox_prop_stacking_rings,
        "kawaii.wrench" to R.string.toybox_prop_wrench,
        "kawaii.paintbrush" to R.string.toybox_prop_paintbrush,
        "kawaii.wooden_spoon" to R.string.toybox_prop_wooden_spoon,
        "kawaii.saucepan" to R.string.toybox_prop_saucepan,
        "kawaii.gamepad" to R.string.toybox_prop_gamepad,
        "kawaii.floppy_disk" to R.string.toybox_prop_floppy_disk,
        "kawaii.pencil" to R.string.toybox_prop_pencil,
        "kawaii.eraser" to R.string.toybox_prop_eraser,
        "kawaii.rabbit" to R.string.toybox_prop_rabbit,
        "kawaii.penguin" to R.string.toybox_prop_penguin,
        "kawaii.pear" to R.string.toybox_prop_pear,
        "kawaii.cherries" to R.string.toybox_prop_cherries,
        "kawaii.potted_cactus" to R.string.toybox_prop_potted_cactus,
        "kawaii.sandcastle" to R.string.toybox_prop_sandcastle
    )
}
