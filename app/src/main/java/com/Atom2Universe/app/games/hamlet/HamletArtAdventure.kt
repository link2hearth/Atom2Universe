package com.Atom2Universe.app.games.hamlet

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Six hand-built miniature sets. Geometry is cached; only the small mechanisms animate. */
object HamletArtAdventure {
    private const val WOOD = 0x73503A
    private const val GOLD = 0xE1BC69
    private const val STONE = 0xB3B3A1
    private const val GLASS = 0xAEDFD7

    fun draw(p: Iso, kind: Int) {
        p.shadow(0f, 0f, 3f, 3f)
        p.box(.08f, .08f, 0f, 2.92f, 2.92f, .10f, 0x8D9682)
        p.top(.15f, .15f, 2.85f, 2.85f, .11f, if (kind == 4) 0xC9AB77 else 0xD1C8AC)
        // Small irregular paving stones remain legible at village scale.
        for (i in 0..7) {
            val x = .22f + (i % 4) * .65f
            val y = 2.4f + (i / 4) * .25f
            p.top(x, y, x + .5f, y + .19f, .12f, if (i % 3 == 0) 0xB8B39C else 0xDDD4B6)
        }
        when (kind) {
            0 -> inn(p)
            1 -> academy(p)
            2 -> forge(p)
            3 -> store(p)
            4 -> yard(p)
            else -> gate(p)
        }
    }

    private fun stoneLines(p: Iso, x0: Float, y0: Float, x1: Float, y1: Float, h: Float, base: Float = .12f) {
        var z = base + .2f
        var row = 0
        while (z < h) {
            p.line(x0, y1 + .004f, z, x1, y1 + .004f, z, 0x8A968E, .8f)
            p.line(x1 + .004f, y0, z, x1 + .004f, y1, z, 0x798981, .8f)
            var x = x0 + .18f + (row % 2) * .2f
            while (x < x1) {
                p.line(x, y1 + .006f, z, x, y1 + .006f, z - .22f, 0x8A968E, .7f)
                x += .43f
            }
            z += .25f; row++
        }
    }

    private fun planter(p: Iso, x: Float, y: Float, hue: Int = 0xD9827D) {
        p.box(x - .18f, y - .1f, .12f, x + .18f, y + .1f, .32f, WOOD)
        for (i in 0..3) {
            p.dot(x - .13f + i * .085f, y, .36f, 3.4f, 0x4D7953)
            p.dot(x - .12f + i * .085f, y, .4f + (i % 2) * .035f, 1.9f, hue)
        }
    }

    private fun barrel(p: Iso, x: Float, y: Float, z: Float = .13f) {
        p.cyl(x, y, z, z + .36f, .18f, 0xA87845)
        p.disc(x, y, z + .12f, .183f, 0x52615A)
        p.disc(x, y, z + .31f, .183f, 0x52615A)
        p.disc(x, y, z + .365f, .15f, 0xC39861)
    }

    private fun crate(p: Iso, x: Float, y: Float, z: Float = .13f) {
        p.box(x, y, z, x + .38f, y + .35f, z + .34f, 0xAE8150)
        p.line(x + .035f, y + .353f, z + .03f, x + .345f, y + .353f, z + .31f, WOOD, 1.5f)
        p.line(x + .38f, y, z + .03f, x + .38f, y + .35f, z + .31f, WOOD, 1.5f)
        p.line(x + .04f, y, z + .35f, x + .04f, y + .35f, z + .35f, 0xD6B680, 1.8f)
    }

    private fun inn(p: Iso) {
        p.box(.28f, .24f, .12f, 2.5f, 2.2f, 1.95f, 0xF0DAB0)
        p.box(.25f, .21f, .12f, 2.54f, 2.23f, .42f, 0x9B9B8B)
        stoneLines(p, .25f, .21f, 2.54f, 2.23f, .4f)
        for (x in floatArrayOf(.31f, 1.42f, 2.47f)) {
            p.faceY(2.212f, x - .045f, x + .045f, .42f, 1.95f, WOOD)
        }
        p.faceY(2.22f, .28f, 2.5f, 1.15f, 1.25f, WOOD)
        p.faceX(2.51f, .24f, 2.2f, 1.15f, 1.25f, WOOD)
        p.line(.38f, 2.22f, 1.28f, 1.33f, 2.22f, 1.89f, WOOD, 3f)
        p.line(1.48f, 2.22f, 1.89f, 2.41f, 2.22f, 1.28f, WOOD, 3f)
        p.doorY(2.24f, 1.4f, .42f, .85f, 0x654C3B)
        for (x in floatArrayOf(.77f, 1.97f)) {
            p.windowY(2.23f, x, .78f, .35f, .35f, GLASS, WOOD)
            p.windowY(2.23f, x, 1.57f, .36f, .38f, GLASS, WOOD)
        }
        for (y in floatArrayOf(.65f, 1.6f)) p.windowX(2.51f, y, 1.54f, .34f, .4f, GLASS, WOOD)
        p.gable(.21f, .18f, 2.57f, 2.28f, 1.95f, .91f, 0x416D96, 0xE5CBA0, true, .16f, 6)
        p.line(.06f, 1.23f, 2.87f, 2.71f, 1.23f, 2.87f, 0xB5CBBF, 2f)
        p.chimney(.63f, .55f, 2.37f, 3.02f, 0xB68966)
        p.box(.51f, .43f, 2.95f, .75f, .67f, 3.05f, 0xD7C5A1)
        // Porch, two planted tubs and a suspended tavern sign.
        p.box(1.03f, 2.24f, .1f, 1.78f, 2.72f, .2f, 0xB5A98A)
        p.gable(1f, 2.15f, 1.81f, 2.65f, 1.06f, .22f, 0x416D96, WOOD, false, .07f, 2)
        p.line(1.02f, 2.62f, .2f, 1.02f, 2.62f, 1.08f, WOOD, 2.7f)
        p.line(1.8f, 2.62f, .2f, 1.8f, 2.62f, 1.08f, WOOD, 2.7f)
        p.line(2.55f, 1.87f, 1.55f, 2.94f, 1.87f, 1.55f, WOOD, 2.3f)
        planter(p, .48f, 2.58f); planter(p, 2.46f, 2.6f)
        barrel(p, 2.71f, .75f)
    }

    private fun academy(p: Iso) {
        p.box(.3f, .3f, .12f, 1.8f, 1.82f, 1.18f, 0xDED9C5)
        p.gable(.27f, .27f, 1.84f, 1.86f, 1.18f, .6f, 0x477F7D, 0xCCC3B0, false, .09f, 4)
        p.doorY(1.84f, .84f, .4f, .75f, WOOD)
        p.box(1.47f, .62f, .12f, 2.48f, 1.76f, 2.63f, 0xD8D9C5)
        stoneLines(p, 1.47f, .62f, 2.48f, 1.76f, 2.63f)
        p.box(1.39f, .54f, 2.49f, 2.56f, 1.84f, 2.64f, 0xABBAA7)
        p.windowY(1.78f, 1.98f, 1.83f, .32f, .65f, 0x79C5BD, 0xAB966C)
        p.windowX(2.49f, 1.18f, 1.83f, .32f, .65f, 0x79C5BD, 0xAB966C)
        p.pyramid(1.98f, 1.18f, .65f, .7f, 2.62f, .86f, 0x3F8584, 5)
        p.line(1.98f, 1.18f, 3.43f, 1.98f, 1.18f, 3.96f, GOLD, 2f)
        p.dot(1.98f, 1.18f, 3.82f, 4f, 0xD5F7DF)
        // Reading desk and a real open book, drawn face by face.
        p.box(.47f, 2.05f, .12f, 1.17f, 2.57f, .55f, WOOD)
        p.face(0xF1E8C7, .49f, 2.09f, .58f, .82f, 2.09f, .65f, .82f, 2.48f, .65f, .49f, 2.48f, .58f)
        p.face(0xD4CBAE, .82f, 2.09f, .65f, 1.14f, 2.09f, .58f, 1.14f, 2.48f, .58f, .82f, 2.48f, .65f)
        for (i in 0..2) p.line(.55f, 2.17f + i * .1f, .61f, .73f, 2.17f + i * .1f, .64f, 0xA19170, .7f)
        planter(p, 2.6f, 2.5f, 0xB899DD)
        p.cyl(1.9f, 2.4f, .12f, .38f, .22f, 0xBFC2AD)
    }

    private fun forge(p: Iso) {
        p.box(.23f, .3f, .12f, 2.4f, 1.64f, 1.31f, 0xB5AA95)
        stoneLines(p, .23f, .3f, 2.4f, 1.64f, 1.31f)
        p.gable(.17f, .26f, 2.46f, 1.69f, 1.3f, .68f, 0xB86149, 0x9D907D, true, .12f, 5)
        p.box(.38f, .49f, 1.56f, .81f, .92f, 2.77f, 0x776F69)
        stoneLines(p, .38f, .49f, .81f, .92f, 2.75f, 1.56f)
        p.box(.29f, .4f, 2.64f, .9f, 1.01f, 2.8f, 0xB2A998)
        p.top(.43f, .54f, .76f, .87f, 2.805f, 0x383D39)
        // Broad furnace opening remains dark so the flame layer glows inside it.
        p.faceY(1.66f, .44f, 1.31f, .15f, 1.04f, 0x453F37)
        p.box(.4f, 1.58f, .12f, 1.35f, 1.9f, .28f, 0xB4A28A)
        p.faceY(1.675f, 1.49f, 2.16f, .44f, 1.03f, WOOD)
        p.line(1.6f, 1.69f, .53f, 2.04f, 1.69f, .94f, 0xB9B8AD, 3f)
        p.line(1.59f, 1.69f, .95f, 2.03f, 1.69f, .53f, 0xB9B8AD, 3f)
        // Anvil, quenching tub and billets on the courtyard floor.
        p.box(1.64f, 2.03f, .12f, 2.17f, 2.5f, .38f, WOOD)
        p.box(1.78f, 2.13f, .38f, 2.03f, 2.41f, .65f, 0x697A7F)
        p.box(1.54f, 2.07f, .62f, 2.31f, 2.48f, .77f, 0x8A989A)
        p.face(0xBEC6BE, 2.31f, 2.07f, .77f, 2.57f, 2.24f, .74f, 2.31f, 2.48f, .77f)
        barrel(p, 2.68f, 1.65f); p.disc(2.68f, 1.65f, .505f, .148f, 0x699AA3)
        crate(p, .27f, 2.35f)
        for (i in 0..2) p.box(.28f + i * .105f, 2.38f, .48f, .36f + i * .105f, 2.64f, .57f, 0x77858C)
    }

    private fun store(p: Iso) {
        p.box(.22f, .28f, .12f, 2.37f, 1.93f, 1.62f, 0xCAAD76)
        for (i in 0..7) p.faceY(1.94f, .24f + i * .27f, .28f + i * .27f, .12f, 1.62f, 0xA58958)
        for (i in 0..5) p.faceX(2.38f, .29f + i * .27f, .32f + i * .27f, .12f, 1.62f, 0x8B774E)
        p.faceY(1.96f, .67f, 1.92f, .13f, 1.34f, 0x655744)
        p.faceY(1.97f, .69f, 1.13f, .14f, 1.32f, 0xA7814B)
        p.faceY(1.97f, 1.49f, 1.9f, .14f, 1.32f, 0xA7814B)
        p.line(.71f, 1.98f, .22f, 1.1f, 1.98f, 1.24f, 0xD0AE72, 3f)
        p.line(1.51f, 1.98f, 1.24f, 1.88f, 1.98f, .22f, 0xD0AE72, 3f)
        p.gable(.16f, .22f, 2.44f, 1.99f, 1.62f, .8f, 0x628369, 0xD8B980, true, .14f, 5)
        p.box(.81f, .61f, 2.24f, 1.41f, 1.17f, 2.51f, 0xD6C291)
        p.windowY(1.18f, 1.11f, 2.39f, .27f, .15f, 0x414A40, WOOD)
        p.gable(.78f, .58f, 1.45f, 1.2f, 2.5f, .26f, 0x628369, 0xD6C291, true, .1f, 2)
        p.box(.54f, 1.98f, .12f, 2.04f, 2.25f, .19f, 0x9A8B6C)
        crate(p, .25f, 2.24f); crate(p, .3f, 2.27f, .49f)
        barrel(p, 2.66f, .75f); barrel(p, 2.65f, 1.2f)
        p.line(2.52f, 1.95f, .13f, 2.52f, 1.95f, 1.3f, WOOD, 3f)
        p.line(2.52f, 1.95f, 1.3f, 2.87f, 1.95f, 1.3f, WOOD, 3f)
    }

    private fun yard(p: Iso) {
        // A low open courtyard rather than another roof, to vary the village silhouette.
        p.box(.13f, .14f, .12f, 2.86f, .37f, .5f, STONE)
        p.box(.14f, .32f, .12f, .35f, 2.7f, .5f, STONE)
        for (i in 0..5) p.box(.16f + i * .48f, .13f, .5f, .34f + i * .48f, .38f, .7f, 0xCAD0B8)
        p.box(.3f, .45f, .12f, 1.22f, 1.25f, 1.04f, 0xCABB98)
        p.gable(.27f, .42f, 1.25f, 1.28f, 1.03f, .5f, 0xAF6553, 0xCABB98, true, .11f, 3)
        p.doorY(1.27f, .77f, .39f, .71f, WOOD)
        // Archery target has concentric rings facing the player.
        p.line(2.25f, .76f, .13f, 2.25f, .76f, 1.24f, WOOD, 3f)
        val sx = p.px(2.25f, .76f); val sy = p.py(2.25f, .76f, 1.18f)
        p.oval(2.25f, .76f, 1.18f, 14f, 16f, 0xDFC999)
        p.oval(2.25f, .76f, 1.18f, 10f, 12f, 0xC96551)
        p.oval(2.25f, .76f, 1.18f, 6f, 8f, 0xE9D9AE)
        p.flat(0x637D84, sx - 2f, sy - 4f, sx + 2f, sy - 4f, sx + 2f, sy + 4f, sx - 2f, sy + 4f)
        p.box(.3f, 2.13f, .12f, .41f, 2.68f, .83f, WOOD)
        for (i in 0..2) {
            val y = 2.2f + i * .18f
            p.line(.46f, y, .14f, .46f, y, 1.03f, 0x8C754E, 1.7f)
            p.face(0xBFC8C6, .46f, y, 1.13f, .4f, y, .94f, .52f, y, .94f)
        }
        p.line(.22f, .25f, .65f, .22f, .25f, 2.11f, GOLD, 2f)
        p.disc(1.65f, 1.95f, .125f, .64f, 0xB89A69)
        p.disc(1.65f, 1.95f, .13f, .53f, 0xD3B581)
    }

    private fun gate(p: Iso) {
        // The empty arch really remains open: two piers and a lintel, no painted black doorway.
        p.box(.24f, .73f, .12f, .92f, 1.73f, 2.35f, 0xCFD1BE)
        p.box(2.08f, .73f, .12f, 2.76f, 1.73f, 2.35f, 0xCFD1BE)
        stoneLines(p, .24f, .73f, .92f, 1.73f, 2.3f)
        stoneLines(p, 2.08f, .73f, 2.76f, 1.73f, 2.3f)
        p.box(.86f, .84f, 1.7f, 2.14f, 1.65f, 2.19f, 0xD9D6BF)
        p.face(0xB0B59E, .9f, 1.66f, 1.69f, 1.23f, 1.66f, 1.96f, 1.5f, 1.66f, 2.02f, 1.77f, 1.66f, 1.96f, 2.11f, 1.66f, 1.69f, 2.11f, 1.66f, 2.2f, .9f, 1.66f, 2.2f)
        p.box(.84f, .79f, 2.16f, 2.16f, 1.71f, 2.28f, 0xB2BCAB)
        for (i in 0..3) p.box(.84f + i * .37f, 1.5f, 2.28f, 1.01f + i * .37f, 1.72f, 2.54f, 0xDADBC6)
        for (x in floatArrayOf(.58f, 2.42f)) {
            p.box(x - .41f, .66f, 2.27f, x + .41f, 1.79f, 2.43f, 0xABB7AB)
            p.pyramid(x, 1.22f, .5f, .65f, 2.43f, .62f, 0x4B7291, 3)
            p.windowY(1.745f, x, 1.53f, .19f, .53f, 0x687D79, 0xE5E1CB)
            p.line(x, 1.22f, 3.03f, x, 1.22f, 3.62f, GOLD, 1.8f)
            p.dot(x, 1.22f, 3.63f, 2.3f, GOLD)
        }
        p.top(1.02f, 1.72f, 1.97f, 2.81f, .14f, 0xAAB3A0)
        for (i in 0..3) p.line(1.04f, 1.83f + i * .28f, .15f, 1.95f, 1.83f + i * .28f, .15f, 0xCCD1B9, 2f)
        planter(p, .48f, 2.49f, 0xE4B767); planter(p, 2.53f, 2.49f, 0xE4B767)
    }

    /** Local coordinates match the static sprite and remain valid when the whole object is mirrored. */
    fun animate(p: Iso, kind: Int, time: Float, daylight: Float, effectsEnabled: Boolean = true) {
        val t = if (effectsEnabled) time else 0f
        val night = (1f - daylight).coerceIn(0f, 1f)
        when (kind) {
            0 -> {
                val x = 2.8f + sin(t * 1.6f) * .035f
                p.line(2.8f, 1.87f, 1.53f, x, 1.87f, 1.35f, 0x4A5048, 1f)
                p.box(x - .17f, 1.86f, 1.01f, x + .17f, 1.92f, 1.38f, 0xCFAD6D)
                HamletGlyphs.draw(p.c, "mug", p.px(x, 1.925f), p.py(x, 1.925f, 1.2f), 11f, 0x5E4835)
                if (night > .08f) for (wx in floatArrayOf(.77f, 1.97f)) {
                    val glass = Iso.mix(GLASS, 0xFFD48A, night * (.89f + .06f * sin(t * 1.8f + wx)))
                    p.windowY(2.231f, wx, 1.57f, .36f, .38f, glass, WOOD)
                }
                if (effectsEnabled) smoke(p, .63f, .55f, 3.06f, t, false)
            }
            1 -> {
                // Orbiting brass armillary above the roof and hovering crystal on its pedestal.
                val a = t * .65f
                for (i in 0..17) {
                    val r = i * .349066f
                    p.line(1.98f + cos(r + a) * .38f, 1.18f + sin(r + a) * .38f, 3.8f + sin(r) * .13f,
                        1.98f + cos(r + a + .349066f) * .38f, 1.18f + sin(r + a + .349066f) * .38f, 3.8f + sin(r + .349066f) * .13f, GOLD, 1f)
                }
                p.dot(1.98f + cos(a) * .38f, 1.18f + sin(a) * .38f, 3.8f, 3.1f, 0xE0F7D6)
                val z = .85f + sin(t * 1.8f) * .055f
                p.face(0x7BCEC8, 1.9f, 2.4f, z + .25f, 1.72f, 2.4f, z, 1.9f, 2.4f, z - .23f)
                p.face(0xB8F2DB, 1.9f, 2.4f, z + .25f, 2.07f, 2.4f, z, 1.9f, 2.4f, z - .23f)
                if (effectsEnabled) p.dot(1.9f + sin(t) * .28f, 2.4f + cos(t) * .28f, z, 1.5f, 0xF1DDA4)
            }
            2 -> {
                for (i in 0..2) {
                    val x = .6f + i * .23f
                    val h = .32f + abs(sin(t * 5f + i * 1.7f)) * .22f
                    p.face(0xDB713E, x - .13f, 1.68f, .3f, x, 1.68f, .3f + h, x + .13f, 1.68f, .3f)
                    p.face(0xFFD579, x - .07f, 1.69f, .3f, x, 1.69f, .3f + h * .63f, x + .07f, 1.69f, .3f)
                }
                // Bellows compresses on the courtyard side; hammer strikes the exposed anvil.
                val h = .16f + (sin(t * 2.8f) + 1f) * .045f
                p.box(.86f, 2.14f, .16f, 1.3f, 2.57f, .16f + h, 0x785841)
                p.box(.83f, 2.11f, .16f + h, 1.33f, 2.6f, .21f + h, 0xBC9767)
                val lift = abs(sin(t * 2.8f)) * .28f
                p.line(2.09f, 2.37f, .93f + lift, 1.73f, 2.37f, .87f + lift, WOOD, 2.4f)
                p.box(1.64f, 2.26f, .79f + lift, 1.83f, 2.46f, .96f + lift, 0xB6C0BB)
                if (effectsEnabled) {
                    smoke(p, .6f, .72f, 2.81f, t * 1.1f, true)
                    for (i in 0..3) {
                        val a = (t * 1.1f + i * .23f) % 1f
                        p.dot(1.78f + sin(i * 2.1f) * a * .4f, 2.33f + cos(i * 2.1f) * a * .22f, .87f + a * .55f,
                            1.5f - a, Iso.withAlpha(0xFFE59A, ((1f - a) * .8f).coerceAtLeast(.008f)))
                    }
                }
            }
            3 -> {
                val slide = sin(t * .8f) * .12f
                val x = 1.83f + slide; val y = 2.63f
                p.dot(x - .28f, y, .25f, 4.7f, 0x4B5147); p.dot(x + .28f, y, .25f, 4.7f, 0x4B5147)
                p.box(x - .39f, y - .28f, .24f, x + .38f, y + .12f, .36f, 0xAC8956)
                crate(p, x - .28f, y - .24f, .36f)
                p.line(x + .38f, y - .15f, .3f, x + .61f, y - .15f, .45f, WOOD, 2f)
                val sway = sin(t * 1.6f) * .04f
                p.line(2.78f, 1.95f, 1.3f, 2.78f + sway, 1.95f, .83f, 0x9F8051, 1.2f)
                p.oval(2.78f + sway, 1.95f, .74f, 6f, 8f, 0xD8BC7B)
            }
            4 -> {
                val sway = sin(t * 2.1f) * .05f
                p.line(1.65f, 1.95f, .14f, 1.65f + sway, 1.95f, 1.24f, WOOD, 3f)
                p.line(1.29f + sway, 1.95f, .88f, 2.01f + sway, 1.95f, .88f, WOOD, 3f)
                p.oval(1.65f + sway, 1.95f, 1.14f, 5f, 7f, 0xC7AA69)
                p.oval(1.65f + sway, 1.95f, .76f, 8f, 11f, 0xC28B63)
                p.line(1.49f + sway, 1.95f, .92f, 1.79f + sway, 1.95f, .61f, 0x916C4A, 1.4f)
                flag(p, .22f, .25f, 2.08f, 0xBC6E53, t)
            }
            else -> {
                flag(p, .58f, 1.22f, 3.55f, 0x4C84AF, t)
                flag(p, 2.42f, 1.22f, 3.55f, 0x4C84AF, t + .6f)
                if (night > .08f) {
                    p.windowY(1.746f, .58f, 1.53f, .19f, .53f, Iso.mix(0x687D79, 0xFFD79A, night), 0xE5E1CB)
                    p.windowY(1.746f, 2.42f, 1.53f, .19f, .53f, Iso.mix(0x687D79, 0xFFD79A, night), 0xE5E1CB)
                }
            }
        }
    }

    private fun flag(p: Iso, x: Float, y: Float, z: Float, color: Int, t: Float) {
        val wave = sin(t * 2.4f) * .045f
        p.face(color, x, y, z, x + .23f, y + wave, z - .025f, x + .47f, y - wave, z - .05f,
            x + .43f, y - wave, z - .3f, x + .23f, y + wave, z - .27f, x, y, z - .25f)
        p.line(x + .02f, y, z - .035f, x + .42f, y - wave, z - .08f, 0xE8CA80, 1f)
    }

    private fun smoke(p: Iso, x: Float, y: Float, z: Float, t: Float, dark: Boolean) {
        for (i in 0..3) {
            val age = (t * .3f + i * .25f) % 1f
            p.oval(x + age * .13f + sin(t + i) * .04f, y - age * .12f, z + age * .7f,
                2f + age * 5f, 1.7f + age * 3f, Iso.withAlpha(if (dark) 0xC4BBA9 else 0xF0EEDD, ((1f - age) * .44f).coerceAtLeast(.008f)))
        }
    }
}
