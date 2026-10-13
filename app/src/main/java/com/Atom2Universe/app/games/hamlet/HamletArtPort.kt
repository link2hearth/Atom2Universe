package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.sin

/** Original small props and landforms for the extended catalogue, drawn as shaded volumes. */
object HamletArtPort {
    private const val WOOD = 0xAB8256
    private const val DARK = 0x68503A
    private const val CREAM = 0xEEDDC0
    private const val GOLD = 0xD8B965
    private const val IRON = 0x687680
    fun draw(p: Iso, def: Def, w: Float, d: Float) {
        if (def.look.kind == "port_nature") { nature(p, def, w, d); return }
        val x = w / 2; val y = d / 2; val id = def.id
        p.shadow(.08f, .08f, w - .08f, d - .08f, .2f)
        if (id.length == 4 && id.startsWith("pot") && id.last() in '0'..'5') {
            val color = intArrayOf(0xBD7954, 0x628BA5, 0xE1DFD1, 0x6B9C79, 0xBE8395, 0xCEAF65)[id.last().digitToInt()]
            p.cyl(x, y, .02f, .32f, .22f, color); p.cyl(x, y, .29f, .37f, .25f, Iso.shade(color, .14f))
            p.disc(x, y, .375f, .2f, 0x63543A)
            for (i in 0..4) { val a = i * 1.256f; val xx = x + cos(a) * .16f; val yy = y + sin(a) * .16f
                p.line(x, y, .35f, xx, yy, .6f, 0x58824A, 2f); p.ball(xx, yy, .65f, .075f, intArrayOf(0xE7BB70, 0xCB7E92, 0xAF92C1)[i % 3], .07f)
            }; return
        }
        if (id.startsWith("plant")) {
            val color = intArrayOf(0xCE655D, 0xDCC265, 0x6A9CB9, 0x7DA571, 0xDFA1BA)[id.last().digitToInt()]
            poles(p, x, y, 1.5f)
            for (i in -1..1) { val xx = x + i * .3f; p.line(xx, y, 1.45f, xx, y, 1.2f, DARK, 1f)
                p.ball(xx, y, 1.03f, .15f, color, .2f); p.cyl(xx, y, .8f, .87f, .06f, GOLD); p.cyl(xx, y, 1.2f, 1.24f, .07f, GOLD)
                for (j in -1..1) p.line(xx + j * .04f, y, .8f, xx + j * .04f, y, .65f, color, 1f)
            }; return
        }
        when (id) {
            "cake" -> {
                p.disc(x, y, .04f, .4f, CREAM)
                for (i in 0..2) { val z = .07f + i * .19f; val r = .32f - i * .075f
                    p.cyl(x, y, z, z + .18f, r, if (i % 2 == 0) 0xDBA6AF else CREAM)
                    p.cyl(x, y, z + .14f, z + .18f, r + .015f, CREAM)
                    for (j in 0..5) { val a = j * 1.047f; p.ball(x + cos(a) * r, y + sin(a) * r, z + .17f, .035f, 0xBD6573, .035f) }
                }
                p.line(x, y, .63f, x, y, .82f, GOLD, 2f); p.ball(x, y, .86f, .045f, 0xF4CF6C, .09f)
            }
            "roastspit" -> {
                for (i in -1..1) p.line(x - .3f, y + i * .12f, .06f, x + .3f, y + i * .12f, .06f, DARK, 4f)
                for (i in -1..1) p.cone(x + i * .12f, y, .05f, .32f, .1f, if (i == 0) 0xF4C45F else 0xD37A45)
                poles(p, x, y, .7f); p.line(x - .48f, y, .67f, x + .48f, y, .67f, IRON, 2f)
                p.ball(x, y, .63f, .27f, 0xB77549, .18f); p.line(x + .3f, y, .63f, x + .46f, y, .66f, CREAM, 3f)
            }
            "kegstand", "butterchurn", "milkcans" -> {
                if (id == "kegstand") table(p, x, y, .2f)
                val count = if (id == "milkcans") 3 else 1
                for (i in 0 until count) { val xx = x + (i - (count - 1) / 2f) * .22f; val z = if (id == "kegstand") .2f else .02f
                    val r = if (count == 1) .25f else .12f; val color = if (id == "milkcans") 0xA4B4B5 else WOOD
                    p.cyl(xx, y, z, z + .5f, r, color)
                    for (h in floatArrayOf(.12f, .39f)) p.cyl(xx, y, z + h, z + h + .04f, r + .01f, IRON)
                    p.disc(xx, y, z + .51f, r * .9f, color)
                    if (id == "butterchurn") p.line(xx, y, .52f, xx + .04f, y, .9f, DARK, 3f)
                    if (id == "milkcans") p.cyl(xx, y, .52f, .6f, .07f, color)
                }
                if (id == "kegstand") p.line(x, y + .24f, .46f, x, y + .37f, .46f, GOLD, 3f)
            }
            "wagon", "sleigh" -> {
                p.box(x - .4f, y - .26f, .16f, x + .4f, y + .26f, .45f, WOOD)
                if (id == "wagon") {
                    for (xx in floatArrayOf(x - .26f, x + .26f)) for (yy in floatArrayOf(y - .29f, y + .29f)) wheel(p, xx, yy, .17f, .16f)
                    p.ball(x, y, .6f, .42f, CREAM, .28f)
                    for (i in -1..1) p.line(x + i * .22f, y + .31f, .44f, x + i * .22f, y + .16f, .85f, 0xC2AF8B, 1.4f)
                } else for (yy in floatArrayOf(y - .28f, y + .28f)) { p.line(x - .42f, yy, .06f, x + .45f, yy, .06f, IRON, 3f); p.line(x + .45f, yy, .06f, x + .53f, yy, .23f, IRON, 3f) }
            }
            "wheel", "grindstone", "spinningwheel" -> {
                val r = if (id == "wheel") .38f else .28f
                if (id != "wheel") table(p, x, y, .2f)
                wheel(p, x, y, if (id == "wheel") r else .55f, r, if (id == "grindstone") 0xA9A99B else WOOD)
                if (id == "spinningwheel") { p.line(x + .3f, y, .2f, x + .3f, y, .85f, DARK, 3f); p.ball(x + .3f, y, .85f, .06f, CREAM, .12f) }
            }
            "spellbook", "stocksGold" -> {
                p.box(x - .19f, y - .16f, 0f, x + .19f, y + .16f, .58f, id.let { if (it == "stocksGold") GOLD else WOOD })
                if (id == "spellbook") {
                    p.face(0xDBCBA2, x - .3f, y - .19f, .7f, x, y - .19f, .62f, x, y + .19f, .62f, x - .3f, y + .19f, .7f)
                    p.face(CREAM, x, y - .19f, .62f, x + .3f, y - .19f, .7f, x + .3f, y + .19f, .7f, x, y + .19f, .62f)
                    for (i in -1..1) p.line(x + .06f, y + i * .08f, .65f, x + .23f, y + i * .08f, .69f, 0x7B6F70, 1f)
                } else { p.cyl(x, y, .58f, .83f, .08f, GOLD); p.ball(x, y, .9f, .2f, GOLD, .13f); p.disc(x, y, 1.01f, .15f, 0x947542) }
            }
            "potionshelf", "toolrack", "loom", "ladder" -> {
                val tall = if (id == "ladder") 1.6f else 1.15f
                poles(p, x, y, tall)
                for (i in 1..4) { val z = i * tall / 5; p.line(x - .4f, y, z, x + .4f, y, z, WOOD, if (id == "ladder") 3f else 4f)
                    if (id == "potionshelf") for (j in -1..1) { val col = intArrayOf(0x9AAF77, 0xAA83AC, 0x79ADC1)[(i + j + 3) % 3]
                        p.ball(x + j * .23f, y + .06f, z + .09f, .075f, col, .09f); p.cyl(x + j * .23f, y + .06f, z + .15f, z + .21f, .025f, GOLD)
                    }
                }
                if (id == "loom") { for (i in 0..8) p.line(x - .3f + i * .075f, y + .04f, .2f, x - .3f + i * .075f, y + .04f, 1.03f, if (i % 2 == 0) CREAM else 0xB58F9C, 1.5f) }
                if (id == "toolrack") for (i in -1..1) { val xx = x + i * .24f; p.line(xx, y + .07f, .2f, xx, y + .07f, .9f, DARK, 2f); p.box(xx - .09f, y + .04f, .8f, xx + .09f, y + .1f, .95f, IRON) }
            }
            "dragonegg", "eastereggs" -> {
                p.disc(x, y, .04f, .42f, 0x9D8660)
                val count = if (id == "dragonegg") 1 else 5
                for (i in 0 until count) { val a = i * 1.256f; val xx = x + if (count == 1) 0f else cos(a) * .23f; val yy = y + if (count == 1) 0f else sin(a) * .23f
                    p.ball(xx, yy, .26f, if (count == 1) .26f else .105f, intArrayOf(0xA3B48A, 0xDDA0AA, 0xB69BD0, 0xD7BF74, 0x8CB7C0)[i], if (count == 1) .34f else .16f)
                    p.dot(xx, yy + .07f, .34f, 2f, CREAM)
                }
            }
            "fairylights", "windchime" -> {
                poles(p, x, y, 1.3f)
                for (i in -2..2) { val xx = x + i * .16f; val z = 1.15f - (2 - kotlin.math.abs(i)) * .04f
                    p.line(xx, y, 1.28f, xx, y, z - .22f, GOLD, 1f)
                    if (id == "fairylights") p.ball(xx, y, z - .25f, .055f, intArrayOf(0xEED184, 0xD1ABC3, 0xACD0BF)[(i + 3) % 3], .07f)
                    else p.cyl(xx, y, z - .3f, z, .025f, 0xA8B7B6)
                }
            }
            "igloo" -> {
                p.ball(x, y, .12f, .48f, 0xD6E5E8, .55f)
                for (z in floatArrayOf(.15f, .3f, .45f)) p.line(x - .32f, y + .27f, z, x + .32f, y + .27f, z, 0x9DBBC3, 1f)
                p.ball(x + .29f, y + .22f, .13f, .24f, 0xDDE9E8, .28f); p.oval(x + .34f, y + .35f, .15f, 6f, 9f, 0x658595)
            }
            "icesculpture" -> {
                p.cyl(x, y, 0f, .13f, .3f, 0xB1DCE4)
                for (i in -1..1) { val xx = x + i * .17f; val h = if (i == 0) 1.2f else .8f
                    p.face(0xA0CEE0, xx - .12f, y, .1f, xx + .12f, y, .1f, xx + .06f, y, h); p.face(0xDAF0ED, xx + .12f, y, .1f, xx + .06f, y + .14f, .1f, xx + .06f, y, h)
                }
            }
            "candycane" -> for (i in -1..1) {
                val xx = x + i * .23f; p.line(xx, y, 0f, xx, y, .9f, CREAM, 5f)
                p.ball(xx + .07f, y, .91f, .12f, CREAM, .1f)
                for (z in floatArrayOf(.12f, .35f, .58f, .81f)) p.line(xx, y, z, xx, y, z + .08f, 0xBD6871, 4f)
            }
            "giftpile" -> for (i in 0..4) {
                val xx = x - .3f + (i % 3) * .25f; val yy = y + (i / 3) * .2f - .12f; val z = if (i == 4) .32f else 0f
                val col = intArrayOf(0xB96A76, 0x77A391, 0x8798BC, 0xC3A565, 0xBB91BB)[i]
                p.box(xx, yy, z, xx + .24f, yy + .23f, z + .29f, col)
                p.box(xx + .1f, yy - .005f, z, xx + .14f, yy + .235f, z + .3f, CREAM)
            }
            "ghost" -> {
                p.ball(x, y, .76f, .28f, 0xE8ECE0, .32f)
                p.cone(x, y, .15f, .6f, .37f, 0xE8ECE0)
                p.dot(x - .08f, y + .22f, .79f, 2.5f, 0x667078); p.dot(x + .08f, y + .22f, .79f, 2.5f, 0x667078)
                for (i in -1..1) p.ball(x + i * .2f, y + .06f, .18f, .13f, 0xE8ECE0, .07f)
            }
            "coffin" -> { p.box(x - .23f, y - .43f, .03f, x + .23f, y + .43f, .25f, 0x71555A); p.top(x - .19f, y - .39f, x + .19f, y + .39f, .26f, 0x986D75); p.line(x, y - .2f, .28f, x, y + .2f, .28f, GOLD, 2f); p.line(x - .12f, y, .28f, x + .12f, y, .28f, GOLD, 2f) }
            "spiderweb" -> {
                poles(p, x, y, 1.2f)
                for (i in 0..7) { val a = i * .7854f; p.line(x, y, .65f, x + cos(a) * .4f, y, .65f + sin(a) * .43f, CREAM, 1f) }
                for (r in floatArrayOf(.14f, .27f, .4f)) for (i in 0..7) { val a = i * .7854f; val b = a + .7854f; p.line(x + cos(a) * r, y, .65f + sin(a) * r, x + cos(b) * r, y, .65f + sin(b) * r, CREAM, 1f) }
            }
            "fireworks" -> for (i in -1..1) { val xx = x + i * .2f; p.line(xx, y, 0f, xx, y, .45f, DARK, 1.5f); p.cyl(xx, y, .4f, .85f + i * .05f, .08f, intArrayOf(0xB87078, 0x7197B4, 0xC1A26B)[i + 1]); p.cone(xx, y, .85f + i * .05f, .16f, .1f, GOLD) }
            "bell" -> { poles(p, x, y, 1.2f); p.cyl(x, y, .65f, .7f, .27f, GOLD); p.ball(x, y, .85f, .22f, GOLD, .22f); p.line(x, y, 1.2f, x, y, .95f, IRON, 2f); p.ball(x, y, .61f, .05f, DARK, .07f) }
            "gallows" -> { poles(p, x, y, 1.15f); p.box(x - .43f, y - .04f, .4f, x + .43f, y + .04f, 1.12f, WOOD); for (i in -1..1) p.faceY(y + .045f, x + i * .22f - .08f, x + i * .22f + .08f, .55f, .94f, CREAM) }
            "telescope" -> {
                for (i in 0..2) { val a = i * 2.094f; p.line(x + cos(a) * .32f, y + sin(a) * .32f, 0f, x, y, .65f, DARK, 3f) }
                p.line(x - .25f, y, .6f, x + .35f, y, 1.04f, GOLD, 8f); p.line(x + .27f, y, .98f, x + .38f, y, 1.06f, IRON, 10f)
            }
            "globe" -> { p.cyl(x, y, 0f, .48f, .075f, GOLD); p.disc(x, y, .03f, .25f, DARK); p.ball(x, y, .82f, .34f, 0x82B5C0, .34f); for (i in 0..8) p.ball(x + sin(i * 2f) * .2f, y + .2f, .7f + i % 3 * .11f, .08f, 0x98B786, .065f); p.line(x - .15f, y, .4f, x + .15f, y, 1.18f, GOLD, 2f) }
            "hourglass" -> {
                for (xx in floatArrayOf(x - .23f, x + .23f)) p.line(xx, y, .08f, xx, y, 1.05f, GOLD, 3f)
                p.disc(x, y, .1f, .3f, WOOD); p.disc(x, y, 1.04f, .3f, WOOD)
                p.face(0xA8D5D4, x - .2f, y, .97f, x + .2f, y, .97f, x, y, .58f, x + .2f, y, .17f, x - .2f, y, .17f, x, y, .58f)
                p.cone(x, y, .16f, .26f, .17f, 0xDDC17C); p.line(x, y, .62f, x, y, .37f, GOLD, 1f)
            }
            "harp" -> { p.line(x - .35f, y, .03f, x + .3f, y, .03f, GOLD, 4f); p.line(x + .3f, y, .03f, x + .2f, y, 1.15f, GOLD, 4f); p.line(x - .35f, y, .03f, x - .25f, y, .95f, GOLD, 4f); p.line(x - .25f, y, .95f, x + .2f, y, 1.15f, GOLD, 4f); for (i in 0..5) p.line(x - .2f + i * .075f, y, .08f, x - .2f + i * .075f, y, .96f + i * .03f, CREAM, 1f) }
            "drum" -> { p.cyl(x, y, .06f, .58f, .31f, 0xB9716B); p.disc(x, y, .59f, .32f, CREAM); for (i in 0..7) { val a = i * .7854f; p.line(x + cos(a) * .3f, y + sin(a) * .3f, .08f, x + cos(a + .2f) * .3f, y + sin(a + .2f) * .3f, .55f, GOLD, 1f) }; p.line(x - .25f, y - .2f, .62f, x + .25f, y + .2f, .65f, DARK, 2f) }
            "chessset" -> {
                for (i in 0..7) for (j in 0..7) { val xx = .12f + i * (w - .24f) / 8; val yy = .12f + j * (d - .24f) / 8
                    p.top(xx, yy, xx + (w - .24f) / 8, yy + (d - .24f) / 8, .05f, if ((i + j) % 2 == 0) CREAM else 0x586D69)
                    if (j == 0 || j == 7) { p.cyl(xx + .11f, yy + .11f, .05f, .25f, .05f, if (j == 0) CREAM else 0x55616A); p.ball(xx + .11f, yy + .11f, .28f, .07f, if (j == 0) CREAM else 0x55616A, .08f) }
                }
            }
            "maze" -> {
                p.top(.03f, .03f, w - .03f, d - .03f, .01f, 0xBDAB7C)
                for (i in 0..3) { val xx = .1f + i * .8f; p.box(xx, .1f, .02f, xx + .18f, d - .2f - (i % 2) * .5f, .43f, 0x71925D) }
                p.box(.1f, .1f, .02f, w - .2f, .3f, .43f, 0x71925D); p.box(.1f, d - .4f, .02f, w - .9f, d - .2f, .43f, 0x71925D)
            }
            "swing" -> { poles(p, x, y, 1.4f); for (xx in floatArrayOf(x - .22f, x + .22f)) p.line(xx, y, 1.35f, xx, y + .08f, .42f, CREAM, 1.2f); p.box(x - .3f, y - .05f, .34f, x + .3f, y + .2f, .41f, WOOD) }
            "seesaw" -> { p.cone(x, y, 0f, .36f, .18f, 0xB78662); p.line(x - .48f, y, .2f, x + .48f, y, .53f, WOOD, 6f); p.box(x + .3f, y - .14f, .52f, x + .5f, y + .14f, .57f, 0xB76970); p.box(x - .5f, y - .14f, .17f, x - .3f, y + .14f, .23f, 0x6C9DAC) }
            "sandbox" -> { p.box(.04f, .04f, .01f, w - .04f, d - .04f, .18f, WOOD); p.top(.14f, .14f, w - .14f, d - .14f, .19f, 0xD9C797); p.cone(x, y, .2f, .19f, .2f, 0xCBB581); p.cyl(x + .25f, y + .15f, .2f, .38f, .08f, 0xBA6869) }
            "kite" -> { p.line(x, y, .05f, x + .1f, y, 1.25f, CREAM, 1f); p.face(0xCF8A88, x + .1f, y, 1.9f, x + .45f, y, 1.55f, x + .1f, y, 1.2f, x - .2f, y, 1.55f); p.face(0xE3C673, x + .1f, y, 1.9f, x + .1f, y, 1.2f, x - .2f, y, 1.55f); for (i in 0..3) p.dot(x + sin(i.toFloat()) * .07f, y, .35f + i * .22f, 2f, 0x79A8B4) }
            "birdhouse" -> { p.line(x, y, 0f, x, y, 1.1f, DARK, 4f); p.box(x - .2f, y - .15f, .75f, x + .2f, y + .15f, 1.12f, WOOD); p.gable(x - .24f, y - .19f, x + .24f, y + .19f, 1.12f, .24f, 0x739795, WOOD, alongX = true); p.oval(x, y + .16f, .96f, 3f, 4f, DARK) }
            "weathervane" -> { p.line(x, y, 0f, x, y, 1.35f, IRON, 2.5f); p.line(x - .4f, y, 1.04f, x + .4f, y, 1.04f, GOLD, 2f); p.face(GOLD, x + .28f, y, 1.18f, x + .52f, y, 1.05f, x + .28f, y, .94f); p.ball(x - .1f, y, 1.43f, .16f, GOLD, .12f); p.ball(x + .04f, y, 1.57f, .075f, GOLD, .08f) }
            "redcarpetroll" -> { p.top(.08f, .03f, w - .08f, d - .03f, .025f, 0xAA5262); for (xx in floatArrayOf(.14f, w - .14f)) p.line(xx, .06f, .04f, xx, d - .06f, .04f, GOLD, 2f) }
            "pillarMarble", "pillarBroken", "urn" -> {
                p.box(x - .28f, y - .25f, .01f, x + .28f, y + .25f, .16f, 0xC1BDAA)
                val h = if (id == "pillarBroken") .65f else if (id == "urn") .4f else 1.3f
                p.cyl(x, y, .16f, h, .18f, 0xD4D1BF)
                if (id == "urn") { p.ball(x, y, .64f, .3f, 0xC8C0AB, .25f); p.cyl(x, y, .81f, .94f, .2f, 0xD8D1BC); p.disc(x, y, .95f, .15f, 0x7C8270) }
                else { for (i in -1..1) p.line(x + i * .08f, y + .16f, .18f, x + i * .08f, y + .16f, h - .05f, 0xABAFA0, 1f); p.box(x - .25f, y - .25f, h, x + .25f, y + .25f, h + .13f, 0xE1DBC9) }
            }
            "anvil" -> { p.cyl(x, y, .01f, .3f, .27f, WOOD); p.box(x - .19f, y - .13f, .3f, x + .19f, y + .13f, .57f, IRON); p.box(x - .35f, y - .17f, .56f, x + .27f, y + .17f, .67f, 0x9BA6A7); p.face(0x798991, x + .27f, y - .14f, .6f, x + .51f, y, .64f, x + .27f, y + .14f, .6f) }
        }
    }

    private fun poles(p: Iso, x: Float, y: Float, h: Float) {
        for (xx in floatArrayOf(x - .42f, x + .42f)) p.box(xx - .035f, y - .035f, 0f, xx + .035f, y + .035f, h, DARK)
        p.line(x - .46f, y, h, x + .46f, y, h, WOOD, 3f)
    }
    private fun table(p: Iso, x: Float, y: Float, h: Float) {
        for (xx in floatArrayOf(x - .24f, x + .24f)) for (yy in floatArrayOf(y - .16f, y + .16f)) p.box(xx - .035f, yy - .035f, 0f, xx + .035f, yy + .035f, h, DARK)
        p.box(x - .35f, y - .25f, h, x + .35f, y + .25f, h + .06f, WOOD)
    }
    private fun wheel(p: Iso, x: Float, y: Float, z: Float, r: Float, color: Int = WOOD) {
        for (i in 0..15) { val a = i * .3927f; val b = a + .3927f
            p.line(x + cos(a) * r, y, z + sin(a) * r, x + cos(b) * r, y, z + sin(b) * r, color, 3f)
            if (i % 2 == 0) p.line(x, y, z, x + cos(a) * r, y, z + sin(a) * r, Iso.shade(color, -.15f), 1.5f)
        }; p.ball(x, y, z, .045f, IRON, .045f)
    }
    private fun nature(p: Iso, def: Def, w: Float, d: Float) {
        val x = w / 2; val y = d / 2
        p.shadow(.08f, .08f, w - .08f, d - .08f, .18f)
        when (def.id) {
            "crystalYellow", "crystalPink", "crystalWhite", "crystalBlack" -> {
                for (i in -1..1) { val xx = x + i * .15f; val h = if (i == 0) .9f else .6f
                    p.face(def.look.a, xx - .12f, y, .04f, xx + .12f, y, .04f, xx + .12f, y, h * .7f, xx, y, h, xx - .12f, y, h * .7f)
                    p.face(Iso.shade(def.look.a, .28f), xx, y, h, xx + .12f, y, h * .7f, xx + .08f, y + .1f, .04f, xx, y + .12f, .04f)
                }
            }
            "palmPair", "coconut", "bananatree" -> {
                val count = if (def.id == "palmPair") 2 else 1
                for (i in 0 until count) { val xx = x + (i - (count - 1) / 2f) * .4f; val yy = y + i * .12f
                    p.line(xx, yy, 0f, xx + .07f, yy, 1.32f, WOOD, 5f)
                    for (j in 0..6) { val a = j * .8976f; val ex = xx + cos(a) * .5f; val ey = yy + sin(a) * .5f
                        p.face(0x72945D, xx + .07f, yy, 1.35f, ex, ey, 1.1f, xx + cos(a + .2f) * .32f, yy + sin(a + .2f) * .32f, 1.3f)
                    }
                    for (j in -1..1) p.ball(xx + j * .07f, yy + .07f, 1.12f, if (def.id == "bananatree") .035f else .07f, if (def.id == "bananatree") GOLD else DARK, if (def.id == "bananatree") .14f else .07f)
                }
            }
            "hill" -> { p.ball(x, y, .08f, .88f, 0x85A368, .42f); for (i in 0..7) p.ball(x + sin(i * 1.6f) * .5f, y + cos(i * 1.6f) * .4f, .35f, .07f, if (i % 2 == 0) CREAM else 0xD7B878, .06f) }
            "volcano" -> { p.cone(x, y, .02f, 1.15f, .78f, 0x8D8075); p.cyl(x, y, .88f, 1.08f, .25f, 0x7A6A66); p.disc(x, y, 1.1f, .19f, 0xED9C59); p.line(x + .12f, y + .13f, 1.01f, x + .43f, y + .39f, .21f, 0xCF7850, 5f); p.smoke(x, y, 1.2f) }
            "geyser", "hotspring" -> {
                p.disc(x, y, .03f, if (w > 1) .73f else .38f, 0xA3AC99); p.disc(x, y, .045f, if (w > 1) .59f else .29f, 0x80BDC6)
                for (i in 0..7) { val a = i * .7854f; val r = if (w > 1) .67f else .34f; p.ball(x + cos(a) * r, y + sin(a) * r, .08f, .12f, 0xB9B9A5, .09f) }
                if (def.id == "geyser") { p.line(x, y, .05f, x, y, 1.18f, 0xA1D6DA, 5f); for (i in -1..1) p.line(x, y, 1.16f, x + i * .2f, y, .95f, 0xD3E9E2, 2f) }
                else { p.smoke(x - .2f, y, .05f); p.smoke(x + .17f, y, .1f) }
            }
            "cliff", "cave", "mesa" -> {
                val sand = def.id == "mesa"; val col = if (sand) 0xBD9376 else 0xAAA894
                for (i in 0..2) { val pad = .08f + i * .18f; val z = i * .28f
                    p.box(pad, pad, z, w - pad, d - pad, z + .3f, Iso.shade(col, i * .035f))
                    for (j in 0..3) p.line(pad + .1f + j * .3f, d - pad + .01f, z, pad + .13f + j * .3f, d - pad + .01f, z + .25f, Iso.shade(col, -.15f), 1f)
                }
                if (def.id == "cave") { p.oval(x, d - .09f, .3f, 17f, 19f, 0x484B43); p.oval(x, d - .08f, .27f, 12f, 15f, 0x333F38) }
                else p.top(.44f, .44f, w - .44f, d - .44f, .91f, if (sand) 0xD1AF86 else 0x88A26A)
            }
        }
    }
}
