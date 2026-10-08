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

/** First themed toy-box batch. Original silhouettes, shared pastels, no external assets. */
internal object DecorKawaiiCollection {
    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel("kawaii.$id", room, DecorBuilder().apply(build).parts.toList())

    private fun DecorBuilder.face(y: Float, z: Float, scale: Float = 1f) {
        for (sign in floatArrayOf(-1f,1f)) {
            box(sign*.48f*scale,y,z,.2f*scale,.3f*scale,.16f*scale,DARK,false)
            box(sign*.48f*scale-.025f*scale,y+.07f*scale,z+.085f*scale,.06f*scale,.06f*scale,.02f*scale,CREAM,false)
            oval(sign*.8f*scale,y-.23f*scale,z-.04f*scale,.32f*scale,.17f*scale,.2f*scale,ROSE,false)
        }
        box(0f,y-.25f*scale,z+.02f*scale,.25f*scale,.09f*scale,.1f*scale,DARK,false)
    }

    private fun DecorBuilder.book(x: Float, bottom: Float, z: Float, color: Int, width: Float = 4.6f) {
        box(x,bottom+.12f,z,width,.24f,5.7f,color)
        box(x,bottom+.64f,z,width-.22f,.8f,5.35f,CREAM)
        box(x,bottom+1.16f,z,width,.24f,5.7f,color)
        box(x-width*.5f+.12f,bottom+.64f,z,.24f,1.04f,5.7f,color)
        for (i in 0..2) box(x,bottom+.4f+i*.22f,z+2.68f,width-.4f,.025f,.035f,WOOD,false)
        box(x+.7f,bottom+1.31f,z,1.2f,.045f,2.1f,GOLD,false)
        box(x+.9f,bottom+.65f,z+3.1f,.48f,.05f,1f,ROSE,false)
    }

    val toys by lazy { listOf(
        model("duck", DecorRoom.BATHROOM) {
            oval(0f,1.35f,0f,4.6f,2.7f,5.2f,GOLD)
            oval(0f,3.2f,1.2f,3.2f,3.2f,3f,GOLD)
            oval(0f,2.95f,2.75f,1.8f,.55f,1.25f,0xEEAD86)
            for (s in floatArrayOf(-1f,1f)) {
                oval(s*2f,1.6f,-.2f,.75f,1.3f,2.5f,CREAM)
                box(s*.65f,3.65f,2.55f,.22f,.35f,.22f,DARK,false)
            }
            oval(0f,1.7f,-2.3f,1.2f,1.6f,1.5f,GOLD)
        },
        model("spinning_top", DecorRoom.BEDROOM) {
            cylinder(0f,.18f,0f,.24f,.36f,WOOD)
            oval(0f,1.2f,0f,4.8f,2f,4.8f,LILAC)
            cylinder(0f,1.4f,0f,2.35f,.34f,ROSE)
            cylinder(0f,2.05f,0f,1.55f,.3f,MINT)
            cylinder(0f,2.75f,0f,.35f,1.3f,WOOD)
            oval(0f,3.48f,0f,1f,.6f,1f,GOLD)
        },
        model("robot", DecorRoom.BEDROOM) {
            for (s in floatArrayOf(-1f,1f)) {
                box(s*.85f,.35f,.25f,1.25f,.7f,1.9f,BLUE)
                cylinder(s*.85f,1f,0f,.33f,.7f,METAL)
                box(s*1.8f,2.2f,0f,.65f,1.7f,.85f,MINT)
                oval(s*1.8f,1.3f,.1f,.9f,.85f,1f,CREAM)
            }
            box(0f,2.25f,0f,2.8f,2.4f,1.8f,LILAC)
            box(0f,2.4f,.97f,1.8f,1.1f,.15f,CREAM,false)
            for (i in 0..2) oval(-.55f+i*.55f,2.4f,1.09f,.3f,.3f,.15f,intArrayOf(ROSE,GOLD,MINT)[i],false)
            box(0f,4.35f,0f,3.2f,1.8f,2f,MINT)
            face(4.5f,1.06f)
            cylinder(0f,5.7f,0f,.12f,.9f,METAL)
            oval(0f,6.2f,0f,.65f,.65f,.65f,ROSE)
        }
    ) }

    val tools by lazy { listOf(
        model("hammer", DecorRoom.GARAGE) {
            box(0f,.7f,1.1f,.8f,.75f,5.8f,WOOD)
            oval(0f,.7f,3f,1.25f,1.15f,2.2f,MINT)
            box(0f,1.05f,-1.8f,3.8f,2.1f,1.8f,BLUE)
            for (s in floatArrayOf(-1f,1f)) box(s*2f,1.05f,-1.8f,.45f,2.25f,2f,CREAM)
        },
        model("screwdriver", DecorRoom.GARAGE) {
            oval(0f,.65f,1.6f,1.7f,1.3f,3.9f,ROSE)
            for (s in floatArrayOf(-1f,1f)) box(s*.64f,.76f,1.5f,.22f,.3f,2.5f,CREAM,false)
            box(0f,.65f,-1.9f,.32f,.32f,3.5f,METAL)
            box(0f,.65f,-3.75f,.72f,.17f,.65f,BLUE)
            cylinder(0f,.68f,3.2f,.27f,.08f,GOLD,false)
        }
    ) }

    val kitchen by lazy { listOf(
        model("spatula", DecorRoom.KITCHEN) {
            box(0f,.4f,1.5f,.6f,.45f,5f,WOOD)
            oval(0f,.4f,3.5f,1.1f,.8f,1.5f,ROSE)
            box(0f,.28f,-1.2f,2.8f,.4f,.55f,MINT)
            box(0f,.28f,-3.3f,2.8f,.4f,.4f,MINT)
            for (i in -2..2) box(i*.6f,.28f,-2.25f,.35f,.4f,2f,MINT)
        },
        model("rolling_pin", DecorRoom.KITCHEN) {
            wheel(0f,1f,0f,1f,5.2f,WOOD)
            for (s in floatArrayOf(-1f,1f)) {
                wheel(s*3.35f,1f,0f,.38f,1.5f,ROSE)
                wheel(s*2.6f,1f,0f,.95f,.18f,CREAM)
            }
        }
    ) }

    val gaming by lazy { listOf(
        model("game_cartridge", DecorRoom.BEDROOM) {
            box(0f,2.6f,0f,4.8f,5.2f,1.25f,LILAC)
            box(0f,5.4f,0f,3.9f,.45f,1.25f,LILAC)
            box(0f,3.1f,.69f,3.65f,2.6f,.15f,CREAM,false)
            box(0f,3.4f,.79f,2.65f,1.6f,.08f,MINT,false)
            box(-.5f,3.4f,.86f,.55f,1f,.06f,CREAM,false)
            box(.45f,3.2f,.86f,.8f,.18f,.06f,GOLD,false)
            for (i in 0..5) box(-1.5f+i*.6f,.42f,.66f,.3f,.65f,.12f,GOLD,false)
            for (s in floatArrayOf(-1f,1f)) for (i in 0..2)
                box(s*2.1f,3.9f+i*.45f,.66f,.28f,.12f,.08f,CREAM,false)
        },
        model("handheld_console", DecorRoom.BEDROOM) {
            box(0f,3.3f,0f,4.6f,6.6f,1.4f,MINT)
            oval(0f,6.5f,0f,4.6f,.45f,1.4f,MINT)
            box(0f,4.6f,.79f,3.8f,3f,.22f,CREAM,false)
            box(0f,4.6f,.94f,3.05f,2.3f,.12f,BLUE,false)
            // A tiny platform game on the screen, without an anthropomorphic face.
            box(0f,3.7f,1.02f,2.8f,.22f,.04f,MINT,false)
            box(.65f,4.35f,1.02f,.9f,.15f,.04f,CREAM,false)
            box(-.7f,4f,1.02f,.32f,.42f,.04f,GOLD,false)
            box(-1.15f,1.8f,.82f,1.25f,.38f,.24f,DARK,false)
            box(-1.15f,1.8f,.82f,.38f,1.25f,.24f,DARK,false)
            for (i in 0..1) oval(.8f+i*.65f,1.5f+i*.55f,.87f,.5f,.5f,.28f,ROSE,false)
            for (i in 0..2) box(.6f+i*.45f,.55f,.76f,.15f,.45f,.12f,CREAM,false)
        }
    ) }

    val books by lazy { listOf(
        model("storybook", DecorRoom.OFFICE) { book(0f,0f,0f,LILAC) },
        model("book_stack", DecorRoom.OFFICE) {
            book(0f,0f,0f,BLUE,5.4f)
            book(.45f,1.28f,-.2f,ROSE,4.8f)
            book(-.3f,2.56f,.35f,MINT,4.3f)
        }
    ) }

    val figures by lazy { listOf(
        model("cat", DecorRoom.BEDROOM) {
            oval(0f,1.7f,0f,3f,3.4f,2.8f,LILAC)
            for (s in floatArrayOf(-1f,1f)) oval(s*.85f,.4f,.8f,1.1f,.8f,1.5f,CREAM)
            oval(0f,3.8f,.15f,3.8f,3f,3f,LILAC)
            for (s in floatArrayOf(-1f,1f)) {
                cone(s*1.12f,4.55f,.15f,.74f,1.9f,LILAC)
                oval(s*1.12f,5.25f,.68f,.65f,.8f,.2f,ROSE,false)
            }
            face(3.95f,1.58f,1.15f)
            oval(1.55f,1.6f,-.8f,1.2f,2.8f,1.1f,LILAC)
        },
        model("mushroom", DecorRoom.OUTDOOR) {
            cylinder(0f,1.3f,0f,1.15f,2.6f,CREAM)
            oval(0f,3.1f,0f,5.2f,2.3f,5.2f,ROSE)
            for (x in floatArrayOf(-1.1f,1.1f)) for (z in floatArrayOf(-.7f,.7f))
                oval(x,3.95f,z,.85f,.32f,.85f,CREAM,false)
            oval(0f,4.23f,0f,1.1f,.18f,1.1f,CREAM,false)
        }
    ) }

    val fruit by lazy { listOf(
        model("apple", DecorRoom.KITCHEN) {
            oval(-.65f,1.9f,0f,2.9f,3.8f,3.6f,ROSE)
            oval(.65f,1.9f,0f,2.9f,3.8f,3.6f,ROSE)
            cylinder(0f,3.85f,0f,.18f,.8f,WOOD)
            oval(.6f,4.1f,0f,1.5f,.25f,.75f,LEAF,false)
        },
        model("strawberry", DecorRoom.KITCHEN) {
            oval(0f,1.1f,0f,2.3f,2.2f,2.3f,ROSE)
            oval(0f,2.2f,0f,3.8f,2.8f,3.4f,ROSE)
            for (i in -1..1) {
                oval(i*.72f,3.5f,0f,1.6f,.28f,.8f,MINT,false)
                for (j in 0..1) oval(i*.85f,1.8f+j*.65f,1.6f,.14f,.25f,.2f,GOLD,false)
            }
            cylinder(0f,3.7f,0f,.15f,.5f,LEAF,false)
        }
    ) }

    val trackside by lazy { listOf(
        model("ice_crystal", DecorRoom.OUTDOOR) {
            oval(0f,.22f,0f,5.8f,.44f,4.3f,CREAM)
            cylinder(0f,2.4f,0f,1.15f,4.4f,BLUE)
            cone(0f,4.6f,0f,1.15f,2.5f,0xC5E8EA)
            cylinder(-1.65f,1.25f,.3f,.75f,2.1f,LILAC)
            cone(-1.65f,2.3f,.3f,.75f,1.6f,0xD7CCE9)
            cylinder(1.5f,1.7f,-.35f,.85f,3f,MINT)
            cone(1.5f,3.2f,-.35f,.85f,1.7f,0xBFE4D6)
        }
    ) }

    val groups by lazy { listOf(
        R.string.toybox_props_toys to (toys + DecorPastelBatchTwo.toys + DecorPastelBatchThree.toys),
        R.string.toybox_props_tools to (tools + DecorPastelBatchTwo.tools + DecorPastelBatchThree.tools),
        R.string.toybox_props_kitchen to (kitchen + DecorPastelBatchTwo.kitchen + DecorPastelBatchThree.kitchen),
        R.string.toybox_props_gaming to (gaming + DecorPastelBatchTwo.gaming + DecorPastelBatchThree.gaming + DecorGameRoom.all),
        R.string.toybox_props_books to (books + DecorPastelBatchTwo.books + DecorPastelBatchThree.books),
        R.string.toybox_props_figures to (figures + DecorPastelBatchTwo.figures + DecorPastelBatchThree.figures),
        R.string.toybox_props_fruit to (fruit + DecorPastelBatchTwo.fruit + DecorPastelBatchThree.fruit),
        R.string.toybox_props_trackside to (trackside + DecorPastelBatchTwo.trackside + DecorPastelBatchThree.trackside + DecorRugs.all)
    ) }
    val all by lazy { groups.flatMap { it.second } }
    val names = mapOf(
        "kawaii.duck" to R.string.toybox_prop_duck, "kawaii.spinning_top" to R.string.toybox_prop_spinning_top,
        "kawaii.robot" to R.string.toybox_prop_robot, "kawaii.hammer" to R.string.toybox_prop_hammer,
        "kawaii.screwdriver" to R.string.toybox_prop_screwdriver, "kawaii.spatula" to R.string.toybox_prop_spatula,
        "kawaii.rolling_pin" to R.string.toybox_prop_rolling_pin, "kawaii.game_cartridge" to R.string.toybox_prop_game_cartridge,
        "kawaii.handheld_console" to R.string.toybox_prop_handheld_console, "kawaii.storybook" to R.string.toybox_prop_storybook,
        "kawaii.book_stack" to R.string.toybox_prop_book_stack, "kawaii.cat" to R.string.toybox_prop_cat,
        "kawaii.mushroom" to R.string.toybox_prop_mushroom, "kawaii.apple" to R.string.toybox_prop_apple,
        "kawaii.strawberry" to R.string.toybox_prop_strawberry, "kawaii.ice_crystal" to R.string.toybox_prop_ice_crystal
    ) + DecorPastelBatchTwo.names + DecorPastelBatchThree.names + DecorGameRoom.names + DecorRugs.names
}
