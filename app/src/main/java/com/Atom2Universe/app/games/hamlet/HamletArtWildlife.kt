package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Twenty-four little sculpted animals. Every silhouette and detail is native Canvas geometry. */
object HamletArtWildlife {
    private val fluffyKinds = setOf("sheepd", "lamb", "llama")
    private val tinyKinds = setOf("rabbit", "cat", "lamb")
    private val heavyKinds = setOf("bear", "panda", "elephant")
    private val horseKinds = setOf("horseB", "horseW", "horseK", "donkey")
    private val darkLegKinds = setOf("panda", "sheepd", "lamb", "fox", "horseB", "horseW", "donkey")
    private val pinkEarKinds = setOf("rabbit", "cat", "pig", "fox")
    fun handles(def: Def) = def.look.kind == "animal" || def.id.startsWith("an_")

    fun draw(p: Iso, def: Def, w: Float, d: Float, time: Float = 0f) {
        val kind = def.id.removePrefix("an_")
        val scale = min(w, d).coerceAtLeast(1f) * .88f
        p.c.save()
        p.c.translate(p.px(w * .5f, d * .5f), p.py(w * .5f, d * .5f, 0f))
        p.c.scale(scale, scale)
        val q = Iso(p.c, 0f, 0f)
        if (kind == "hens" || kind == "geese") {
            bird(q, kind, -.18f, -.10f, time)
            bird(q, kind, .12f, .20f, time + .8f)
            bird(q, kind, -.30f, .28f, time + 1.5f)
        } else if (kind == "peacock") bird(q, kind, 0f, 0f, time)
        else mammal(q, kind, time)
        p.c.restore()
    }

    private fun mammal(p: Iso, kind: String, time: Float) {
        val fluffy = kind in fluffyKinds
        val tiny = kind in tinyKinds
        val heavy = kind in heavyKinds
        val horse = kind in horseKinds
        val deer = kind == "deer" || kind == "stag"
        val body = when (kind) {
            "cow", "horseW", "sheepd", "lamb", "llama", "panda" -> 0xF1EBD7
            "pig" -> 0xECA4A1
            "horseB" -> 0x98643C
            "horseK" -> 0x454750
            "goat" -> 0xCABD94
            "deer", "stag" -> 0xB58253
            "fox" -> 0xCF773D
            "rabbit" -> 0xC6AA86
            "cat" -> 0xDEA365
            "hound" -> 0xC29C68
            "bear" -> 0x866748
            "wolf" -> 0xA1A5A3
            "donkey" -> 0x9D9C93
            "lion" -> 0xCDA258
            "elephant" -> 0x949E9D
            else -> 0xA99B7E
        }
        val furDark = Iso.shade(body, -.30f)
        val muzzle = when (kind) { "pig" -> 0xD8838B; "cow" -> 0xCF9F98; "fox", "cat", "wolf", "horseB", "deer", "stag" -> 0xEFE0BA; "panda" -> 0xF6EEDB; else -> Iso.shade(body, .14f) }
        val length = when { tiny -> .31f; heavy -> .49f; horse -> .50f; else -> .42f }
        val width = when { tiny -> .17f; heavy -> .32f; kind == "pig" -> .28f; else -> .23f }
        val bodyZ = when { tiny -> .30f; kind == "pig" -> .34f; heavy -> .60f; horse || deer -> .66f; kind == "llama" -> .62f; else -> .47f }
        val radius = if (heavy) .31f else if (tiny) .18f else .24f
        val breathe = sin(time * 1.6f + kind.hashCode() % 9) * .012f
        val bob = if (time == 0f) 0f else breathe
        val swing = if (time == 0f) 0f else sin(time * 1.4f) * .07f
        p.shadow(-length - .10f, -width - .08f, length + .15f, width + .10f, .18f)
        val darkLegs = kind in darkLegKinds
        for (y in listOf(-width * .70f, width * .70f)) for (x in listOf(-length * .67f, length * .65f)) {
            val leg = if (darkLegs) 0x4C4943 else furDark
            val thickness = if (heavy) .085f else if (tiny) .032f else .045f
            p.box(x - thickness, y - thickness, .025f, x + thickness, y + thickness, bodyZ - radius * .45f + bob, leg)
            p.ball(x + .012f, y + .005f, .045f, thickness * 1.28f, if (horse || deer || kind == "cow" || kind == "goat") 0x3E3A34 else leg, thickness * .60f)
        }
        // The back and chest overlap into a broad body with a softly lit spine.
        p.ball(-length * .35f, 0f, bodyZ + bob, radius, body, radius * .95f)
        p.ball(length * .25f, 0f, bodyZ + bob, radius, body, radius * .93f)
        if (kind == "cow") {
            p.ball(-.18f, .16f, bodyZ + .05f + bob, .11f, 0x51483F, .12f)
            p.ball(.05f, -.02f, bodyZ + .18f + bob, .10f, 0x51483F, .055f)
            p.ball(0f, .11f, bodyZ - .20f, .10f, 0xD6A398, .045f)
        }
        if (fluffy) {
            for (i in 0..7) {
                val angle = i * .7854f
                p.ball(cos(angle) * length * .55f, sin(angle) * width * .63f, bodyZ + .04f + bob, radius * .70f, if (i % 2 == 0) body else Iso.shade(body, .08f), radius * .76f)
            }
        }
        if (deer) for (i in 0..4) p.dot(-length * .45f + (i % 3) * .13f, width * .70f, bodyZ + (i / 3) * .10f, 1.5f, 0xF7EBD0)
        if (kind == "cat") for (i in 0..2) p.line(-.16f + i * .12f, .10f, bodyZ + .13f, -.17f + i * .12f, .16f, bodyZ, 0x9A6B3F, 1.6f)

        var headZ = bodyZ + if (tiny) .16f else .25f
        val headX = length * .85f
        val headY = width * .10f
        val headR = when { heavy -> .21f; tiny -> .14f; horse -> .17f; else -> .17f }
        if (horse || deer || kind == "llama") {
            headZ += if (kind == "llama") .30f else .18f
            p.ball(length * .61f, 0f, bodyZ + .18f + bob, if (deer) .10f else .12f, body, if (kind == "llama") .31f else .26f)
        }
        if (kind == "lion") {
            for (i in 0..7) {
                val angle = i * .7854f
                p.ball(headX + cos(angle) * .10f, headY + sin(angle) * .14f, headZ + sin(angle) * .19f + bob, .14f, if (i % 2 == 0) 0x81532D else 0x986337, .16f)
            }
        }
        if (kind == "wolf") for (i in 0..3) p.ball(headX - .09f, .02f + i * .025f, headZ - .19f + i * .07f, .09f, 0xC4C7BC, .09f)
        val face = if (kind == "sheepd" || kind == "lamb") 0x6F6255 else body
        p.ball(headX, headY, headZ + bob, headR, face, headR * 1.05f)

        // Ears define the animal before its smaller markings are added.
        if (kind == "elephant") {
            p.ball(headX - .10f, -.16f, headZ + bob, .15f, 0x859392, .27f)
            p.ball(headX + .03f, .17f, headZ + bob, .15f, 0xA7ADAB, .27f)
            p.ball(headX + .05f, .18f, headZ + .015f + bob, .10f, 0xB7ABA1, .20f)
        } else if (kind == "hound") {
            p.ball(headX - .02f, -.13f, headZ - .03f + bob, .075f, 0x694B34, .19f)
            p.ball(headX + .05f, .14f, headZ - .03f + bob, .075f, 0x694B34, .19f)
        } else if (kind == "bear" || kind == "panda" || kind == "lion") {
            for (sign in listOf(-1, 1)) p.ball(headX - .02f, headY + sign * .15f, headZ + .16f + bob, .075f, if (kind == "panda") 0x393D3B else furDark, .075f)
        } else {
            val earH = when (kind) { "rabbit" -> .31f; "donkey" -> .27f; "llama" -> .20f; "cat", "fox", "wolf" -> .16f; else -> .10f }
            for (sign in listOf(-1, 1)) {
                val ex = headX - .015f; val ey = headY + sign * headR * .63f
                p.face(body, ex - .045f, ey, headZ + .08f + bob, ex + .035f, ey, headZ + .08f + bob,
                    ex - .018f + swing * .2f, ey + sign * .035f, headZ + earH + .09f + bob)
                if (kind in pinkEarKinds) p.line(ex, ey, headZ + .11f + bob, ex - .008f, ey + sign * .022f, headZ + earH + .05f + bob, 0xCB9290, 1.8f)
            }
        }

        val snoutX = headX + headR * .63f
        val snoutY = headY + .055f
        if (kind == "elephant") {
            p.line(snoutX, snoutY, headZ - .03f + bob, snoutX + .10f, snoutY + .07f, headZ - .27f, 0x879694, 7f)
            p.line(snoutX + .10f, snoutY + .07f, headZ - .27f, snoutX + .20f, snoutY + .06f + swing, headZ - .37f, 0x97A19C, 5.8f)
            for (sign in listOf(-1, 1)) p.line(snoutX - .05f, snoutY + sign * .075f, headZ - .10f, snoutX + .17f, snoutY + sign * .09f, headZ - .19f, 0xF7EFCD, 2.2f)
        } else {
            p.ball(snoutX, snoutY, headZ - headR * .35f + bob, headR * if (kind == "pig") .76f else .65f, muzzle, headR * .44f)
            if (kind == "pig") for (sign in listOf(-1, 1)) p.dot(snoutX + .052f, snoutY + sign * .035f, headZ - .035f + bob, 1.1f, 0x88505D)
            else p.dot(snoutX + .055f, snoutY + .026f, headZ - .035f + bob, if (tiny) 1.25f else 1.7f, 0x3A3731)
        }
        for (sign in listOf(-1, 1)) {
            val ey = headY + sign * headR * .57f
            if (kind == "panda") p.ball(headX + .10f, ey, headZ + .035f + bob, .060f, 0x343B36, .070f)
            p.dot(headX + headR * .72f, ey, headZ + .035f + bob, if (tiny) 1.1f else 1.5f, 0x242C27)
            p.dot(headX + headR * .75f, ey, headZ + .048f + bob, .45f, 0xFFF9E6)
        }
        if (horse) {
            p.line(length * .45f, -.045f, bodyZ + .30f, headX - .06f, -.045f, headZ + .10f, if (kind == "horseW") 0xCBC5AE else 0x443B32, 4f)
            if (kind != "donkey") p.line(headX + .04f, -.055f, headZ + .12f, snoutX + .04f, snoutY, headZ - .03f, 0xEFE2C7, 2f)
        }
        if (kind == "cow" || kind == "goat" || kind == "stag") {
            for (sign in listOf(-1, 1)) {
                val ey = headY + sign * .10f
                val hornTop = headZ + if (kind == "stag") .46f else .24f
                val horn = if (kind == "stag") 0x856846 else 0xDFD0A2
                p.line(headX - .04f, ey, headZ + .10f + bob, headX - .13f, ey + sign * .085f, hornTop + bob, horn, 2.4f)
                if (kind == "stag") for (i in 0..2) {
                    val z = headZ + .23f + i * .085f + bob
                    p.line(headX - .09f, ey + sign * .05f, z, headX - .18f + i * .03f, ey + sign * (.13f + i * .025f), z + .09f, horn, 1.8f)
                }
            }
        }
        if (kind == "goat") p.face(0x67594B, snoutX -.025f, snoutY, headZ -.08f, snoutX + .045f, snoutY, headZ -.08f, snoutX + .01f, snoutY, headZ -.25f)
        if (kind == "cat") for (sign in listOf(-1, 1)) for (i in 0..1)
            p.line(snoutX, snoutY + sign * .025f, headZ - .04f + i * .02f, snoutX + .05f, snoutY + sign * .17f, headZ - .055f + i * .045f, 0xF6E8C7, .8f)

        // A slow swish uses separate joints and keeps paws planted.
        val tailX = -length * .83f
        when (kind) {
            "rabbit", "lamb", "sheepd" -> p.ball(tailX - .055f, 0f, bodyZ - .04f, .065f, 0xF4ECD7, .065f)
            "fox", "wolf" -> {
                p.line(tailX, 0f, bodyZ + .02f, tailX - .16f, -.06f + swing, bodyZ - .15f, body, 6f)
                p.ball(tailX - .19f, -.075f + swing, bodyZ - .17f, .10f, if (kind == "fox") 0xF0E5C7 else furDark, .08f)
            }
            "cat" -> {
                p.line(tailX, 0f, bodyZ, tailX - .11f, .025f, bodyZ + .20f, body, 3f)
                p.line(tailX - .11f, .025f, bodyZ + .20f, tailX - .06f + swing, .07f, bodyZ + .32f, furDark, 2.8f)
            }
            "pig" -> {
                p.line(tailX, 0f, bodyZ, tailX - .13f, .02f, bodyZ + .03f, 0xC67D84, 2f)
                p.ball(tailX - .12f, .02f, bodyZ + .075f, .033f, 0xD98E96, .036f)
            }
            "bear", "panda" -> p.ball(tailX, 0f, bodyZ - .04f, .07f, furDark, .07f)
            else -> {
                p.line(tailX, 0f, bodyZ + .025f, tailX - .13f, swing, bodyZ - .20f, body, 2.4f)
                p.ball(tailX - .13f, swing, bodyZ - .23f, if (horse) .060f else .037f, furDark, if (horse) .13f else .055f)
            }
        }
    }

    private fun bird(p: Iso, kind: String, x: Float, y: Float, time: Float) {
        val goose = kind == "geese"; val peacock = kind == "peacock"
        val body = if (peacock) 0x347F86 else if (goose) 0xEFEFDC else 0xD8B480
        val radius = if (peacock) .18f else .13f
        val bob = if (time == 0f) 0f else sin(time * 2.2f) * .013f
        p.shadow(x - .18f, y - .15f, x + .19f, y + .16f, .14f)
        if (peacock) {
            // Fanned feathers are assembled one by one, each with its own gold and blue eye.
            for (i in 0..8) {
                val angle = -.9f + i * .225f
                val tx = x - .25f - cos(angle) * .15f
                val ty = y + sin(angle) * .49f
                val z = .45f + cos(angle) * .35f
                p.line(x - .12f, y, .20f, tx, ty, z, if (i % 2 == 0) 0x468653 else 0x3A796A, 5.5f)
                p.ball(tx, ty, z, .060f, 0xD7B666, .074f)
                p.ball(tx + .005f, ty + .008f, z + .003f, .033f, 0x2B708D, .043f)
                p.dot(tx + .01f, ty + .01f, z + .009f, 1.2f, 0x173F57)
            }
        }
        for (side in listOf(-1, 1)) {
            p.line(x, y + side * .065f, .035f, x, y + side * .065f, .17f, 0xCF923E, 1.4f)
            p.line(x, y + side * .065f, .025f, x + .075f, y + side * .075f, .025f, 0xCF923E, 1.3f)
        }
        p.ball(x, y, .24f + bob, radius, body, radius * .88f)
        p.ball(x - .025f, y + .09f, .25f + bob, radius * .70f, Iso.shade(body, -.14f), radius * .60f)
        if (!peacock) p.face(Iso.shade(body, -.22f), x - .10f, y, .26f, x - .25f, y - .06f, .39f, x - .22f, y + .10f, .32f)
        val neck = if (goose || peacock) .18f else .035f
        if (neck > .05f) p.ball(x + .10f, y + .035f, .33f + neck * .5f + bob, .06f, body, neck)
        val hx = x + .135f; val hy = y + .035f; val hz = .35f + neck + bob
        p.ball(hx, hy, hz, .073f, body, .076f)
        p.face(0xDBAA43, hx + .04f, hy -.01f, hz, hx + .16f, hy + .03f, hz -.035f, hx + .05f, hy + .07f, hz -.045f)
        p.dot(hx + .044f, hy + .036f, hz + .027f, .9f, 0x2C322A)
        if (peacock) for (i in -1..1) {
            p.line(hx, hy + i * .02f, hz + .06f, hx - .02f, hy + i * .04f, hz + .18f, 0x337783, 1.1f)
            p.ball(hx - .02f, hy + i * .04f, hz + .18f, .020f, 0x2E7999, .023f)
        } else if (!goose) {
            p.ball(hx, hy, hz + .073f, .027f, 0xBA4B3F, .044f)
            p.ball(hx + .04f, hy + .06f, hz -.06f, .022f, 0xBA4B3F, .042f)
        }
    }
}
