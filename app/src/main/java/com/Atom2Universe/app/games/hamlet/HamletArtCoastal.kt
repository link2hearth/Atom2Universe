package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.sin

/** Harbour miniatures and connecting fortifications, built from the same geometry as the village. */
object HamletArtCoastal {
    private val ids = setOf("castlewall", "brickwall", "marblewall", "goldfence", "bamboofence", "flowerfence", "palisade",
        "ctowerBlue", "ctowerRed", "ctowerGreen", "ctowerGold", "ctowerSlate", "ctowerPurple", "gatehouse", "keep", "drawbridge", "portcullis",
        "wRowboat", "wSailRed", "wSailBlue", "wSailWhite", "wSwanBoat", "wLongship", "wBuoy", "wLily", "wDock", "wPier", "wWaterfallMill", "wHouseboat",
        "harbor", "boathouse", "anchor", "netrack", "shipwheel", "dryboat", "seashells", "sandcastle")
    private const val WOOD = 0x8E6945
    private const val STONE = 0xC5C9B9
    private const val IRON = 0x556B70
    fun handles(def: Def) = def.look.kind == "coastal" || def.id in ids
    fun height(def: Def) = when {
        def.id == "keep" -> 4.2f
        def.id.startsWith("lighthouse") || def.id == "wLighthouse" -> 4.8f
        def.id == "wGalleon" -> 3.7f
        def.id.startsWith("ctower") || def.id == "gatehouse" -> 3.3f
        def.id == "harbor" || def.id == "boathouse" -> 2.8f
        def.id == "wLongship" || def.id == "wHouseboat" -> 2.5f
        else -> 2f
    }

    fun draw(p: Iso, def: Def, w: Float, d: Float, variant: Int = 0, movingParts: Boolean = true) {
        val id = def.id
        if (!def.onWater) p.shadow(0f, 0f, w, d, .2f)
        when {
            def.link -> fence(p, id, if (variant <= 0) 3 else variant)
            id.startsWith("ctower") -> tower(p, w / 2, d / 2, .34f, 2f, roof(def))
            id == "keep" -> {
                HamletArtHomes.fortress(p, w, d, STONE, 0x507896, 0xDABD71, false, 4)
                p.box(w * .34f, d * .34f, 1.9f, w * .66f, d * .66f, 2.85f, STONE)
                p.pyramid(w / 2, d / 2, w * .2f, d * .2f, 2.83f, .6f, 0x507896, 4)
                p.line(w / 2, d / 2, 3.43f, w / 2, d / 2, 3.95f, 0xB99B60, 1.8f)
            }
            id == "gatehouse" -> gate(p, w, d)
            id == "portcullis" -> grille(p)
            id.startsWith("lighthouse") || id == "wLighthouse" || id == "wBeacon" -> lighthouse(p, def, w, d)
            id == "bastion" -> { p.box(.1f, .1f, .02f, w - .1f, d - .1f, 1.2f, STONE); for (i in 0..3) { p.box(.1f + i * .47f, d - .3f, 1.2f, .33f + i * .47f, d - .1f, 1.49f, STONE); p.box(w - .3f, .1f + i * .47f, 1.2f, w - .1f, .33f + i * .47f, 1.49f, STONE) }; p.gable(.5f, .4f, w - .5f, d - .4f, 1.2f, .56f, 0x637F85, STONE, true, .05f, 3) }
            id == "ballista" -> ballista(p)
            id == "wellGrand" || id == "grandfountain" || id == "wJet" -> fountain(p, id, w, d)
            id == "drawbridge" -> drawbridge(p)
            id == "wDock" || id == "wPier" -> dock(p, w, d, id == "wPier")
            id == "harbor" || id == "boathouse" -> harbour(p, w, d, id == "harbor")
            id == "fishmarket" -> fishmarket(p, w, d)
            id == "palmhut" || id == "wIsleHut" -> islandHut(p, w, d, id == "wIsleHut")
            id == "parasol" -> parasol(p)
            id == "lobsterpots" -> pots(p)
            id == "wWaterfallMill" -> watermill(p)
            id == "wBuoy" -> {
                p.disc(.5f, .5f, .04f, .31f, 0xAECEC3)
                p.cyl(.5f, .5f, .02f, .2f, .2f, 0xAD5546)
                p.cone(.5f, .5f, .2f, .34f, .19f, 0xE2DED0)
                p.line(.5f, .5f, .4f, .5f, .5f, .91f, IRON, 2f)
                p.dot(.5f, .5f, .85f, 2.5f, 0xE8B954)
            }
            id == "wLily" || id == "wLotus" -> lilies(p)
            id == "wCattail" -> reeds(p)
            id == "wRock" || id == "wStones" || id == "wIceberg" || id == "wTreasure" -> rocks(p, id)
            id in animals -> seaAnimal(p, id, w, d)
            id == "anchor" -> anchor(p, .5f, .52f, .8f)
            id == "netrack" -> net(p)
            id == "shipwheel" -> {
                p.box(.43f, .44f, .04f, .57f, .6f, .55f, WOOD)
                wheel(p, .5f, .53f, .93f, 14f, 0f, false)
            }
            id == "seashells" -> shells(p)
            id == "sandcastle" -> sandcastle(p)
            else -> boat(p, id, w, d)
        }
        if (movingParts) animate(p, def, 0f, false)
    }

    private fun roof(def: Def) = when {
        def.id.endsWith("Red") -> 0xAC594A
        def.id.endsWith("Green") -> 0x59856A
        def.id.endsWith("Gold") -> 0xC9A858
        def.id.endsWith("Slate") -> 0x657A85
        def.id.endsWith("Purple") -> 0x87729C
        else -> 0x4E7A9F
    }

    private fun tower(p: Iso, x: Float, y: Float, r: Float, h: Float, color: Int) {
        p.cyl(x, y, .03f, .2f, r + .09f, 0xA5B19F)
        p.cyl(x, y, .16f, h, r, STONE)
        for (i in 1..5) p.disc(x, y, h * i / 6, r + .005f, 0xACB8AA)
        p.cyl(x, y, h - .17f, h + .03f, r + .07f, 0xCAD2BB)
        p.cone(x, y, h + .02f, .62f, r + .14f, color)
        p.windowY(y + r * .72f, x - r * .4f, h * .64f, .13f, .4f, 0x485D5C, 0xDEDCC4)
        p.windowX(x + r * .8f, y - r * .25f, h * .6f, .13f, .35f, 0x485D5C, 0xDEDCC4)
        p.line(x, y, h + .6f, x, y, h + 1.03f, 0xBBA15F, 1.5f)
    }

    private fun fence(p: Iso, id: String, mask: Int) {
        val color = when (id) { "brickwall" -> 0xB27862; "marblewall" -> 0xE0DFCE; "goldfence" -> 0xC6A552; "bamboofence" -> 0x96A169; "flowerfence" -> 0xB6B999; "palisade" -> 0x95734F; else -> STONE }
        val masonry = id == "castlewall" || id == "brickwall" || id == "marblewall"
        fun segment(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (masonry) {
                p.box(minOf(x0, x1) - .08f, minOf(y0, y1) - .08f, .02f, maxOf(x0, x1) + .08f, maxOf(y0, y1) + .08f, .85f, color)
                for (i in 0..2) {
                    val f = i / 2f; val x = x0 + (x1 - x0) * f; val y = y0 + (y1 - y0) * f
                    p.box(x - .1f, y - .1f, .84f, x + .1f, y + .1f, 1.05f, Iso.shade(color, .08f))
                }
                for (i in 1..3) p.line(x0 + .085f, y0 + .085f, i * .21f, x1 + .085f, y1 + .085f, i * .21f, Iso.shade(color, -.2f), .8f)
            } else {
                p.line(x0, y0, .3f, x1, y1, .3f, Iso.shade(color, -.13f), 2f)
                p.line(x0, y0, .68f, x1, y1, .68f, color, 2f)
                for (i in 0..4) {
                    val f = i / 4f; val x = x0 + (x1 - x0) * f; val y = y0 + (y1 - y0) * f
                    val h = if (id == "palisade") .94f else .82f
                    p.line(x, y, .04f, x, y, h, color, if (id == "goldfence") 1.8f else 3f)
                    if (id == "palisade" || id == "goldfence") p.face(Iso.shade(color, .1f), x - .05f, y, h, x, y, h + .14f, x + .05f, y, h)
                    if (id == "bamboofence") for (z in 1..3) p.dot(x, y, z * .21f, 1.6f, 0xD2D2A0)
                    if (id == "flowerfence" && i % 2 == 0) {
                        p.dot(x, y, .56f, 4f, 0x6D935E); p.dot(x, y, .6f, 2.3f, if (i == 2) 0xE8C474 else 0xCD8E9B)
                    }
                }
            }
        }
        if (mask and 2 != 0) segment(0f, .5f, .5f, .5f)
        if (mask and 8 != 0) segment(.5f, 0f, .5f, .5f)
        if (mask and 1 != 0) segment(.5f, .5f, 1f, .5f)
        if (mask and 4 != 0) segment(.5f, .5f, .5f, 1f)
        if (masonry) p.box(.36f, .36f, .02f, .64f, .64f, 1.16f, color)
    }

    private fun gate(p: Iso, w: Float, d: Float) {
        p.box(.1f, .31f, .02f, .57f, d - .3f, 1.83f, STONE)
        p.box(w - .57f, .31f, .02f, w - .1f, d - .3f, 1.83f, STONE)
        p.box(.5f, .6f, 1.16f, w - .5f, d - .46f, 1.73f, STONE)
        p.face(0xA5B1A4, .54f, d - .455f, 1.17f, w / 2, d - .455f, 1.45f, w - .54f, d - .455f, 1.17f,
            w - .54f, d - .455f, 1.73f, .54f, d - .455f, 1.73f)
        for (x in floatArrayOf(.34f, w - .34f)) {
            p.pyramid(x, d / 2, .34f, d / 2 - .18f, 1.82f, .62f, 0x4E7A9F, 3)
            p.windowY(d - .29f, x, .98f, .15f, .37f, 0x526A68, 0xDFE1CD)
            p.line(x, d / 2, 2.43f, x, d / 2, 2.94f, 0xC5AB6D, 1.6f)
        }
        p.top(.58f, d - .45f, w - .58f, d - .06f, .03f, 0xB9BDA9)
    }

    private fun grille(p: Iso) {
        p.box(.08f, .43f, .03f, .23f, .66f, 1.49f, STONE)
        p.box(.78f, .43f, .03f, .94f, .66f, 1.49f, STONE)
        p.box(.08f, .43f, 1.3f, .94f, .66f, 1.51f, STONE)
        for (i in 0..4) p.line(.28f + i * .11f, .57f, .12f, .28f + i * .11f, .57f, 1.3f, IRON, 1.7f)
        for (i in 0..3) p.line(.25f, .57f, .32f + i * .28f, .77f, .57f, .32f + i * .28f, IRON, 1.5f)
    }

    private fun drawbridge(p: Iso) {
        p.box(.11f, .07f, 0f, .88f, .9f, .13f, WOOD)
        for (i in 0..5) p.line(.13f, .11f + i * .14f, .14f, .86f, .11f + i * .14f, .14f, 0xC4A476, 1.5f)
        for (x in floatArrayOf(.12f, .87f)) {
            p.line(x, .07f, .05f, x, .07f, 1.2f, IRON, 2f)
            p.line(x, .07f, 1.15f, x, .86f, .18f, 0x7F8878, 1f)
        }
    }

    private fun dock(p: Iso, w: Float, d: Float, pier: Boolean) {
        val y0 = if (pier) .31f else .11f
        p.box(.07f, y0, .03f, w - .07f, d - .11f, .22f, 0x8D7755)
        for (i in 0..5) {
            val x = .09f + (w - .18f) * i / 6
            p.line(x, y0 + .015f, .235f, x, d - .12f, .235f, 0xBDA37A, 1.8f)
        }
        for (x in floatArrayOf(.11f, w - .11f)) for (y in floatArrayOf(y0, d - .11f)) {
            p.cyl(x, y, -.08f, .5f, .04f, WOOD)
            p.disc(x, y, .37f, .06f, 0xC9BC96)
        }
    }

    private fun hull(p: Iso, w: Float, d: Float, color: Int) {
        val y = d * .53f; val half = d * .23f
        p.face(Iso.shade(color, -.15f), .07f, y, .13f, .29f, y + half, .21f, w - .3f, y + half, .21f, w - .05f, y, .15f,
            w - .3f, y + half * .5f, -.015f, .29f, y + half * .5f, -.015f)
        p.face(Iso.shade(color, .13f), .07f, y, .13f, .29f, y - half, .21f, w - .3f, y - half, .21f, w - .05f, y, .15f,
            w - .3f, y + half, .21f, .29f, y + half, .21f)
        p.face(0x6C5943, .2f, y, .14f, .35f, y - half * .6f, .19f, w - .35f, y - half * .6f, .19f, w - .19f, y, .14f,
            w - .35f, y + half * .6f, .19f, .35f, y + half * .6f, .19f)
    }

    private fun boat(p: Iso, id: String, w: Float, d: Float) {
        hull(p, w, d, when (id) { "wSwanBoat" -> 0xE9E8D7; "wPirate", "wGondola" -> 0x4B5652; "wMerchant", "wGalleon" -> 0xAB8152; else -> WOOD })
        val cy = d * .53f
        if (id == "dryboat") {
            p.box(.17f, .12f, -.03f, .26f, .85f, .13f, 0x685444)
            p.box(.73f, .12f, -.03f, .82f, .85f, .13f, 0x685444)
        }
        if (id == "wRaft") {
            for (i in 0..4) p.line(.14f, .21f + i * .13f, .18f, .84f, .21f + i * .13f, .18f, 0xBA9968, 4f)
            p.line(.3f, .16f, .23f, .3f, .83f, .23f, 0x705842, 1.1f); p.line(.69f, .16f, .23f, .69f, .83f, .23f, 0x705842, 1.1f)
        } else if (id == "wBarge") {
            for (i in 0..3) p.box(.4f + i % 2 * .53f, .72f + i / 2 * .37f, .23f, .85f + i % 2 * .53f, 1.04f + i / 2 * .37f, .68f, 0xAF9A6F)
        } else if (id == "wSteamer") {
            p.box(.53f, .73f, .23f, 1.39f, 1.29f, .9f, 0xD7D0B4)
            p.box(.5f, .7f, .86f, 1.42f, 1.32f, .95f, 0xA95547)
            p.cyl(.93f, 1f, .96f, 1.55f, .13f, 0x535F60)
            p.disc(.93f, 1f, 1.45f, .15f, 0xA95C4D)
            for (i in 0..2) p.windowY(1.3f, .67f + i * .27f, .61f, .13f, .19f, 0x78A4AF, WOOD)
        } else if (id == "wWreck") {
            p.face(0x766349, .4f, 1.22f, .25f, .77f, 1.21f, .52f, .88f, 1.25f, .15f)
            p.line(.84f, .87f, .19f, 1.43f, .91f, .68f, WOOD, 3f)
            p.face(0xB8AE8C, 1.2f, .91f, .51f, 1.18f, .91f, .16f, 1.43f, .91f, .68f)
        } else if (id == "wHouseboat") {
            p.box(.5f, .65f, .2f, 1.54f, 1.32f, 1.03f, 0xD6C398)
            p.gable(.46f, .6f, 1.57f, 1.36f, 1.02f, .44f, 0x6F9579, 0xBAA780, true, .09f, 3)
            p.windowY(1.34f, .79f, .66f, .25f, .27f, 0xB5D5CF, WOOD)
            p.doorY(1.34f, 1.28f, .26f, .6f, WOOD)
            p.chimney(.64f, .8f, 1.25f, 1.7f, 0x9D7C61)
        } else if (id == "wSwanBoat") {
            p.line(.77f, cy, .2f, .82f, cy, .76f, 0xE9E8D7, 7f)
            p.line(.82f, cy, .76f, .72f, cy, .85f, 0xE9E8D7, 7f)
            p.oval(.72f, cy, .83f, 5f, 5f, 0xF3EFE0)
            p.face(0xD69858, .63f, cy, .87f, .53f, cy, .81f, .63f, cy, .77f)
            p.dot(.68f, cy, .865f, 1f, 0x354C48)
        } else if (id.startsWith("wSail") || id == "wLongship" || id == "wPirate" || id == "wMerchant" || id == "wGalleon") {
            val z = if (w > 2) 2.7f else if (w > 1) 1.75f else 1.34f
            p.line(w * .5f, cy, .18f, w * .5f, cy, z, WOOD, 1.8f)
            val color = when (id) { "wSailRed", "wLongship" -> 0xB76D5B; "wSailBlue" -> 0x6895AD; "wPirate" -> 0x4D5351; else -> 0xEAE6CE }
            p.face(color, w * .5f, cy, z - .06f, w * .15f, cy, .48f, w * .49f, cy, .48f)
            p.face(0xEEE6CD, w * .53f, cy, z * .87f, w * .84f, cy, .49f, w * .53f, cy, .49f)
            p.line(w * .15f, cy, .47f, w * .84f, cy, .47f, WOOD, 1.3f)
            if (id == "wGalleon" || id == "wMerchant") {
                p.line(w * .75f, cy, .22f, w * .75f, cy, z * .82f, WOOD, 2f)
                p.face(0xDDD8BB, w * .75f, cy, z * .8f, w * .62f, cy, z * .4f, w * .9f, cy, z * .4f)
                p.box(w * .1f, cy - .2f, .18f, w * .26f, cy + .2f, .61f, 0xB79863)
            }
            if (id == "wPirate") { p.dot(w * .36f, cy, z * .5f, 3f, 0xE5DCC5); p.line(w * .32f, cy, z * .42f, w * .42f, cy, z * .32f, 0xE5DCC5, 1.1f) }
            if (id == "wLongship") for (i in 0..4) {
                val x = .42f + i * .27f
                p.oval(x, cy + d * .22f, .18f, 5f, 6.5f, if (i % 2 == 0) 0xB45E4E else 0xB7B898)
                p.dot(x, cy + d * .225f, .18f, 1.5f, 0xD9C380)
            }
        } else {
            for (i in 0..1) p.top(.35f + i * .25f, cy - d * .16f, .43f + i * .25f, cy + d * .16f, .22f, 0xC3A077)
            p.line(.37f, cy - .16f, .28f, .7f, cy + .4f, .06f, 0xB89B6E, 2.1f)
            if (id == "wFishing") { p.line(.65f, cy, .23f, .84f, cy, 1.04f, WOOD, 1.1f); p.line(.84f, cy, 1.04f, .97f, cy, .02f, 0xD7D0B8, .6f) }
        }
    }

    private fun harbour(p: Iso, w: Float, d: Float, working: Boolean) {
        dock(p, w, d, false)
        p.box(.2f, .23f, .24f, w - .3f, d * .59f, 1.17f, 0xC4B28C)
        for (i in 0..5) p.line(.23f + i * .24f, d * .596f, .29f, .23f + i * .24f, d * .596f, 1.15f, 0x937B5B, 1f)
        p.gable(.14f, .18f, w - .24f, d * .63f, 1.17f, .64f, if (working) 0x588797 else 0xAA7560, 0xB8A67E, true, .1f, 4)
        p.faceY(d * .6f, .44f, w - .53f, .25f, .99f, 0x555B4B)
        if (!working) {
            val save = p.c.save(); p.c.translate(p.px(.5f, 1.16f) - p.ox, p.py(.5f, 1.16f, .25f) - p.oy)
            hull(p, 1.1f, .75f, 0xAE8C5D); p.c.restoreToCount(save)
        } else {
            p.line(w - .24f, d - .2f, .24f, w - .24f, d - .2f, 1.9f, WOOD, 3.5f)
            p.line(w - .24f, d - .2f, 1.9f, w - .84f, d - .2f, 1.9f, WOOD, 3f)
            p.line(w - .24f, d - .2f, 1.2f, w - .84f, d - .2f, 1.9f, WOOD, 2f)
            p.line(w - .81f, d - .2f, 1.9f, w - .81f, d - .2f, .66f, 0xD7C8A0, .8f)
            p.box(w - 1.03f, d - .45f, .25f, w - .64f, d - .06f, .66f, 0xB59863)
        }
        anchor(p, .3f, d - .3f, .82f)
    }

    private fun watermill(p: Iso) {
        p.box(.12f, .14f, .07f, .65f, .8f, .9f, 0xC6B791)
        p.gable(.08f, .1f, .7f, .85f, .89f, .41f, 0x8D7662, 0xC6B791, false, .06f, 3)
        p.windowY(.81f, .39f, .58f, .18f, .22f, 0xA9D2C8, WOOD)
        p.face(0x8ABFC5, .72f, .23f, .07f, .99f, .23f, .07f, .99f, .48f, -.08f, .72f, .48f, -.08f)
    }

    private fun anchor(p: Iso, x: Float, y: Float, z: Float) {
        p.line(x, y, .1f, x, y, z, IRON, 3f)
        p.oval(x, y, z + .05f, 3.7f, 4.2f, IRON)
        p.dot(x, y, z + .05f, 1.7f, 0xD6C8A6)
        p.line(x - .18f, y, z * .66f, x + .18f, y, z * .66f, IRON, 2f)
        p.line(x, y, .1f, x - .25f, y, .27f, IRON, 3f)
        p.line(x, y, .1f, x + .25f, y, .27f, IRON, 3f)
        p.face(IRON, x - .25f, y, .27f, x - .22f, y, .47f, x - .1f, y, .28f)
        p.face(IRON, x + .25f, y, .27f, x + .22f, y, .47f, x + .1f, y, .28f)
    }

    private fun net(p: Iso) {
        for (x in floatArrayOf(.12f, .88f)) p.line(x, .55f, .02f, x, .55f, 1.18f, WOOD, 2.5f)
        p.line(.12f, .55f, 1.15f, .88f, .55f, 1.15f, WOOD, 2f)
        for (i in 0..6) {
            val x = .17f + i * .11f
            p.line(x, .56f, 1.12f, x, .56f, .32f + sin(i * .52f) * -.1f, 0xA99E7C, .7f)
        }
        for (i in 0..5) p.line(.17f, .56f, .36f + i * .14f, .83f, .56f, .36f + i * .14f, 0xA99E7C, .7f)
        p.oval(.4f, .57f, .57f, 4.7f, 2f, 0x95B4B2)
    }

    private fun lilies(p: Iso) {
        for (i in 0..2) {
            val x = .28f + i * .2f; val y = if (i == 1) .69f else .36f
            p.disc(x, y, .04f, .18f, if (i == 1) 0x799B62 else 0x5D8D62)
            p.line(x, y, .047f, x + .12f, y + .12f, .047f, 0xB6C6A5, .8f)
            if (i == 1) for (k in 0..4) {
                val a = k * 1.2566f
                p.oval(x + cos(a) * .055f, y + sin(a) * .055f, .12f, 2.8f, 3.3f, 0xE8B4BC)
            }
        }
        p.dot(.48f, .69f, .14f, 2f, 0xF2D687)
    }

    private fun shells(p: Iso) {
        for (i in 0..4) {
            val x = .21f + (i % 3) * .27f; val y = .25f + (i / 3) * .39f
            p.face(if (i % 2 == 0) 0xE8C7B1 else 0xDBD9C3, x, y, .2f, x - .12f, y + .13f, .07f, x, y + .18f, .04f, x + .12f, y + .13f, .07f)
            p.line(x, y, .2f, x, y + .17f, .05f, 0xBAA991, .8f)
        }
    }

    private fun sandcastle(p: Iso) {
        p.disc(.5f, .5f, .03f, .43f, 0xD4BB82)
        p.box(.27f, .26f, .03f, .74f, .73f, .57f, 0xDFC58E)
        for (x in floatArrayOf(.23f, .78f)) for (y in floatArrayOf(.22f, .78f)) {
            p.cyl(x, y, .03f, .71f, .115f, 0xDFC58E)
            p.cone(x, y, .71f, .16f, .135f, 0xEAD6A4)
        }
        p.doorY(.741f, .5f, .18f, .3f, 0xAD935B)
        p.line(.5f, .5f, .55f, .5f, .5f, 1.05f, WOOD, 1f)
        p.face(0xC07A68, .5f, .5f, 1.03f, .7f, .5f, .97f, .5f, .5f, .88f)
    }

    private fun lighthouse(p: Iso, def: Def, w: Float, d: Float) {
        val x = w / 2; val y = d / 2
        val small = def.id == "wBeacon"
        val h = if (small) 1.1f else if (w >= 3) 3.45f else 2.5f
        val r = if (small) .22f else w * .19f
        p.disc(x, y, .04f, r + .15f, 0xA9B3A3)
        p.cyl(x, y, .05f, h, r, if (def.id.endsWith("Stone")) 0xB7BCAF else 0xECE6CE)
        val band = if (def.id.endsWith("Blue")) 0x6891A4 else 0xB86C5C
        for (i in 1..3) p.cyl(x, y, h * i / 4 - .12f, h * i / 4 + .09f, r + .005f, band)
        p.cyl(x, y, h, h + .13f, r + .11f, IRON)
        p.cyl(x, y, h + .14f, h + .49f, r * .68f, 0xB5D6CB)
        for (i in 0..5) {
            val a = i * 1.0472f
            p.line(x + cos(a) * r * .7f, y + sin(a) * r * .7f, h + .14f, x + cos(a) * r * .7f, y + sin(a) * r * .7f, h + .51f, IRON, 1.2f)
        }
        p.cone(x, y, h + .5f, .34f, r + .06f, IRON)
        if (w >= 2) p.doorY(y + r * .7f, x - r * .35f, .26f, .56f, WOOD)
    }

    private fun fishmarket(p: Iso, w: Float, d: Float) {
        p.box(.1f, .1f, .02f, w - .1f, d - .1f, .15f, 0xB8B498)
        p.box(.22f, .9f, .15f, w - .2f, d - .21f, .77f, WOOD)
        for (x in floatArrayOf(.18f, w - .18f)) for (y in floatArrayOf(.23f, d - .23f)) p.line(x, y, .15f, x, y, 1.55f, WOOD, 2.5f)
        for (i in 0..5) {
            val xa = .13f + i * (w - .26f) / 6; val xb = xa + (w - .26f) / 6
            p.face(if (i % 2 == 0) 0x649AA1 else 0xE4DBC0, xa, .2f, 1.58f, xb, .2f, 1.58f, xb, d - .18f, 1.37f, xa, d - .18f, 1.37f)
            if (i < 4) {
                val x = .4f + i * .34f
                p.oval(x, 1.43f, .79f, 6f, 2.4f, if (i % 2 == 0) 0x9CBABD else 0xA8B898)
                p.dot(x + .11f, 1.43f, .79f, .8f, 0x425953)
            }
        }
    }

    private fun pots(p: Iso) {
        for (i in 0..2) {
            val x = .24f + i * .25f; val y = .35f + i % 2 * .3f
            p.box(x - .14f, y - .12f, .02f, x + .14f, y + .12f, .33f, 0xA58C5F)
            for (j in 0..2) p.line(x - .1f + j * .1f, y + .123f, .05f, x - .1f + j * .1f, y + .123f, .3f, 0x645C41, .8f)
            p.oval(x, y, .34f, 3f, 1.5f, 0x584F3C)
        }
    }

    private fun parasol(p: Iso) {
        p.line(.5f, .5f, .02f, .5f, .5f, 1.19f, WOOD, 1.7f)
        for (i in 0..5) {
            val a = i * 1.0472f; val b = a + 1.0472f
            p.face(if (i % 2 == 0) 0xD19371 else 0xE9D8B4, .5f, .5f, 1.27f,
                .5f + cos(a) * .49f, .5f + sin(a) * .49f, .97f, .5f + cos(b) * .49f, .5f + sin(b) * .49f, .97f)
        }
        p.top(.2f, .58f, .41f, .89f, .04f, 0x789BA1)
    }

    private fun islandHut(p: Iso, w: Float, d: Float, island: Boolean) {
        if (island) p.disc(w / 2, d / 2, .045f, .45f, 0xD4C18A)
        p.box(.19f, .24f, .07f, .82f, .79f, .66f, 0xB49861)
        p.doorY(.8f, .51f, .22f, .42f, 0x645944)
        p.pyramid(.5f, .52f, .45f, .4f, .63f, .44f, 0xC5B77B, 4)
        p.line(.85f, .2f, .07f, .77f, .17f, 1.65f, WOOD, 3f)
        for (i in 0..4) {
            val a = i * 1.2566f
            p.face(0x659467, .77f, .17f, 1.69f, .77f + cos(a) * .43f, .17f + sin(a) * .43f, 1.44f,
                .77f + cos(a + .35f) * .18f, .17f + sin(a + .35f) * .18f, 1.62f)
        }
    }

    private fun ballista(p: Iso) {
        p.box(.23f, .23f, .02f, .7f, .7f, .2f, WOOD)
        p.line(.47f, .47f, .18f, .47f, .47f, .62f, WOOD, 4f)
        p.line(.11f, .63f, .72f, .47f, .4f, .82f, WOOD, 3f)
        p.line(.47f, .4f, .82f, .87f, .63f, .72f, WOOD, 3f)
        p.line(.11f, .63f, .72f, .87f, .63f, .72f, 0xDCCAA3, .8f)
        p.line(.47f, .15f, .84f, .47f, .89f, .61f, 0xA8B4AD, 2f)
    }

    private fun fountain(p: Iso, id: String, w: Float, d: Float) {
        val x = w / 2; val y = d / 2
        val r = if (id == "grandfountain") 1.16f else .37f
        if (id != "wJet") {
            p.cyl(x, y, .02f, .28f, r, 0xC6C8B2)
            p.disc(x, y, .285f, r * .87f, 0x7EBFC0)
        }
        if (id == "wellGrand") {
            for (xx in floatArrayOf(.15f, .85f)) p.line(xx, .5f, .17f, xx, .5f, 1.4f, WOOD, 2.5f)
            p.gable(.04f, .2f, .97f, .83f, 1.37f, .34f, 0x62867F, 0xD4C8A5, true, .04f, 3)
            p.line(.5f, .5f, 1.39f, .5f, .5f, .45f, 0xB8A36D, .9f)
            p.cyl(.5f, .5f, .45f, .66f, .12f, WOOD)
        } else if (id == "grandfountain") {
            p.cyl(x, y, .28f, .94f, .23f, 0xD6D9C1)
            p.cyl(x, y, .88f, 1.01f, .65f, 0xD6D9C1)
            p.disc(x, y, 1.02f, .56f, 0xA1D5C8)
            p.cyl(x, y, 1.02f, 1.5f, .13f, 0xD6D9C1)
        }
    }

    private fun reeds(p: Iso) {
        for (i in 0..6) {
            val x = .22f + i % 3 * .21f; val y = .26f + i / 3 * .19f
            val h = .56f + i % 3 * .16f
            p.line(x, y, .01f, x + .06f, y, h, 0x6A8C52, 1.2f)
            p.line(x + .06f, y, h - .15f, x + .06f, y, h, 0x8D694A, 3f)
            p.face(0x7CA063, x, y, .02f, x - .08f, y, h * .73f, x + .02f, y, h * .45f)
        }
    }

    private fun rocks(p: Iso, id: String) {
        val ice = id == "wIceberg"
        for (i in 0..2) {
            val x = .26f + i * .23f; val y = .45f + i % 2 * .2f
            val h = if (id == "wStones") .23f else .66f - i * .14f
            p.face(if (ice) 0xC6E5D9 else 0x93A59B, x - .2f, y - .13f, .01f, x - .09f, y, h, x + .03f, y + .18f, .04f, x - .16f, y + .15f, .01f)
            p.face(if (ice) 0xE0F1DF else 0xBCC0A7, x - .09f, y, h, x + .14f, y - .11f, .03f, x + .2f, y + .12f, .01f, x + .03f, y + .18f, .04f)
        }
        if (id == "wTreasure") {
            p.box(.3f, .26f, .21f, .76f, .69f, .52f, 0xAE8250)
            p.box(.28f, .24f, .51f, .78f, .71f, .61f, 0xC5A36A)
            p.faceY(.716f, .48f, .59f, .42f, .57f, 0xE4C876)
        }
    }

    private val animals = setOf("wDucks", "wSwans", "wKoi", "wSerpent", "wWhale", "wMermaid", "wShark", "wDolphin", "wTurtle")
    private fun seaAnimal(p: Iso, id: String, w: Float, d: Float) {
        val x = w / 2; val y = d / 2
        when (id) {
            "wDucks", "wSwans" -> for (i in 0..1) {
                val xx = .27f + i * .42f; val yy = .37f + i * .22f
                val swan = id == "wSwans"; val color = if (swan) 0xE8EAD9 else 0xB2A276
                p.oval(xx, yy, .12f, 6.6f, 3.4f, color)
                val neck = if (swan) .44f else .25f
                p.line(xx + .11f, yy, .1f, xx + .11f, yy, neck, color, 3.5f)
                p.dot(xx + .1f, yy, neck, 3.5f, if (swan) color else 0x62816B)
                p.face(0xC59B55, xx + .15f, yy, neck + .03f, xx + .29f, yy, neck - .02f, xx + .15f, yy, neck - .05f)
                p.dot(xx + .13f, yy, neck + .035f, .8f, 0x364E4D)
            }
            "wTurtle" -> {
                for (i in 0..3) p.oval(x + (if (i % 2 == 0) -.24f else .24f), y + (if (i / 2 == 0) -.16f else .16f), .04f, 4f, 2f, 0x8AA273)
                p.ball(x, y, .14f, .24f, 0x628260, .13f)
                p.oval(x + .3f, y, .09f, 4f, 3f, 0xA8B17F)
                p.line(x - .14f, y, .245f, x + .15f, y, .245f, 0xC5C190, .8f)
                p.dot(x + .36f, y, .12f, .8f, 0x344E46)
            }
            "wSerpent" -> {
                for (i in 0..3) p.oval(.17f + i * .2f, .5f, .1f + sin(i * 1.15f) * .15f, 4f, 8f, 0x6C9B83)
                p.oval(.8f, .5f, .61f, 5f, 7f, 0x78A88A)
                p.dot(.86f, .5f, .66f, 1.1f, 0xD6D093)
            }
            "wMermaid" -> {
                rocks(p, "wStones")
                p.face(0x67A9A3, .3f, .5f, .09f, .58f, .5f, .44f, .71f, .5f, .08f)
                p.line(.48f, .5f, .37f, .48f, .5f, .67f, 0xC3987C, 5f)
                p.ball(.48f, .5f, .78f, .11f, 0xB58064)
                p.oval(.43f, .5f, .8f, 4.4f, 7.4f, 0x975D55)
                p.line(.48f, .5f, .6f, .67f, .5f, .51f, 0xC3987C, 2.5f)
            }
            else -> {
                val color = when (id) { "wWhale" -> 0x688F9B; "wKoi" -> 0xD8A879; "wShark" -> 0x79999C; else -> 0x9BBABD }
                p.oval(x, y, .08f, w * 17f, w * 7f, color)
                p.face(color, x - w * .38f, y, .08f, x - w * .5f, y - .17f, .12f, x - w * .47f, y + .17f, .12f)
                if (id != "wKoi") p.face(Iso.shade(color, -.12f), x - .13f, y, .17f, x + .06f, y, .55f, x + .19f, y, .17f)
                p.dot(x + w * .29f, y, .15f, 1f, 0x344C4B)
                if (id == "wKoi") p.oval(x + .07f, y, .09f, 3f, 3f, 0xEEE4C6)
            }
        }
    }

    private fun wheel(p: Iso, x: Float, y: Float, z: Float, radius: Float, phase: Float, water: Boolean) {
        val cx = p.px(x, y); val cy = p.py(x, y, z)
        for (i in 0..7) {
            val a = phase + i * .785398f
            val b = a + .785398f
            val sx = cx + cos(a) * radius; val sy = cy + sin(a) * radius
            p.flat(if (water) 0xA27C50 else 0x9F7C51, cx, cy - .8f, sx, sy - 1.2f, sx, sy + 1.2f, cx, cy + .8f)
            p.flat(0xBC9A68, sx - 1f, sy - 1f, cx + cos(b) * radius - 1f, cy + sin(b) * radius - 1f,
                cx + cos(b) * radius + 1f, cy + sin(b) * radius + 1f, sx + 1f, sy + 1f)
            if (water) p.flat(0xD0AF78, sx - 3f, sy - 2f, sx + 3f, sy - 2f, sx + 3f, sy + 2f, sx - 3f, sy + 2f)
        }
        p.dot(x, y, z, 3f, IRON)
    }

    fun animate(p: Iso, def: Def, time: Float, effectsEnabled: Boolean = true) {
        val t = if (effectsEnabled) time else 0f
        val id = def.id
        when {
            id == "grandfountain" || id == "wJet" -> {
                val x = def.w / 2f; val y = def.d / 2f
                val base = if (id == "grandfountain") 1.49f else .1f
                for (i in 0..5) {
                    val a = i * 1.0472f
                    val r = if (id == "grandfountain") .55f else .29f
                    p.line(x, y, base, x + cos(a) * r * .5f, y + sin(a) * r * .5f, base + .22f, 0xB7E4DC, 1.5f)
                    p.line(x + cos(a) * r * .5f, y + sin(a) * r * .5f, base + .22f, x + cos(a) * r, y + sin(a) * r, base - .35f, 0xB7E4DC, 1.5f)
                    val phase = (t * .8f + i / 6f) % 1f
                    p.dot(x + cos(a) * r, y + sin(a) * r, base - .35f - phase * .22f, 1f, 0xD4F0E3)
                }
            }
            id.startsWith("lighthouse") || id == "wLighthouse" || id == "wBeacon" -> {
                val h = if (id == "wBeacon") 1.1f else if (def.w >= 3) 3.45f else 2.5f
                p.dot(def.w / 2f, def.d / 2f, h + .31f, 3.8f + sin(t * 1.5f) * .8f, 0xFFE2A4)
            }
            id == "wWhale" -> for (i in 0..2) {
                val phase = (t * .38f + i / 3f) % 1f
                p.dot(def.w / 2f + .17f + sin(i * 2f) * phase * .19f, def.d / 2f, .2f + phase * .76f, 1.8f - phase, 0xC9E6DB)
            }
            id == "wSteamer" && effectsEnabled -> for (i in 0..2) {
                val phase = (t * .3f + i / 3f) % 1f
                p.oval(.93f + phase * .12f, 1f, 1.56f + phase * .5f, 1.5f + phase * 3f, 1f + phase * 2f,
                    Iso.withAlpha(0xD8D9C7, ((1f - phase) * .4f).coerceAtLeast(.008f)))
            }
            id == "wWaterfallMill" -> wheel(p, .79f, .62f, .52f, 15f, t * .7f, true)
            id.startsWith("ctower") -> flag(p, .5f, .5f, 3f, roof(def), t)
            id == "keep" -> flag(p, def.w / 2f, def.d / 2f, 3.92f, 0xB26B56, t)
            id == "gatehouse" -> {
                flag(p, .34f, def.d / 2f, 2.89f, 0xC6AA62, t)
                flag(p, def.w - .34f, def.d / 2f, 2.89f, 0xC6AA62, t + .4f)
            }
        }
    }

    private fun flag(p: Iso, x: Float, y: Float, z: Float, color: Int, t: Float) {
        val v = sin(t * 2.2f) * .04f
        p.face(color, x, y, z, x + .17f, y + v, z - .015f, x + .34f, y - v, z - .04f,
            x + .29f, y - v, z - .23f, x + .15f, y + v, z - .2f, x, y, z - .21f)
    }
}
