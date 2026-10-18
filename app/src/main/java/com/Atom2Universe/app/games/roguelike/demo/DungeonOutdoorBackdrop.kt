package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Deux lieux, palettes jour/nuit et détails fixes indépendants de la fréquence d'affichage. */
internal object DungeonOutdoorBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        val path = Path()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    fun draw(c: Canvas, floor: Float, height: Float, jungle: Boolean, night: Boolean) {
        fun tone(day: Long, dark: Long) = (if (night) dark else day).toInt()
        val sky = tone(0xFF96BEC0, 0xFF202B47)
        c.drawColor(sky)
        rect(c, 0f, floor * .5f, 240f, floor * .5f, tone(0xFFBAD1C0, 0xFF303B58))
        if (night) {
            for (i in 0..27) rect(c, (i * 47 % 236 + 2).toFloat(), (i * 17 % 45 + 3).toFloat(),
                1f, 1f, 0xFFB7CAD7.toInt())
            rect(c, 183f, 33f, 13f, 13f, 0xFFE4DFC2.toInt())
            rect(c, 188f, 31f, 11f, 12f, sky)
        } else {
            rect(c, 184f, 33f, 12f, 12f, 0xFFF4DE99.toInt())
            for (i in 0..2) {
                val x = 14f + i * 71
                rect(c, x, 37f + i * 4, 28f, 5f, 0xFFDDE4D0.toInt())
                rect(c, x + 7, 34f + i * 4, 15f, 4f, 0xFFDDE4D0.toInt())
            }
        }
        polygon(c, tone(0xFF789F92, 0xFF354A60), 0f, floor - 8, 34f, floor - 24,
            72f, floor - 12, 115f, floor - 28, 172f, floor - 13, 215f, floor - 27, 240f, floor - 9,
            240f, floor + 5, 0f, floor + 5)
        polygon(c, tone(0xFF587F71, 0xFF293F51), 0f, floor, 50f, floor - 10,
            97f, floor - 3, 150f, floor - 14, 210f, floor - 2, 240f, floor - 8, 240f, floor + 9, 0f, floor + 9)
        val ground = tone(if (jungle) 0xFF456A59 else 0xFF7F9867, if (jungle) 0xFF243E40 else 0xFF344C50)
        rect(c, 0f, floor + 3, 240f, height, ground)
        // Le sentier appartient à la prairie ; la jungle garde un tapis végétal continu.
        if (!jungle) polygon(c, tone(0xFFB4A67C, 0xFF4F5860),
            103f, floor + 3, 130f, floor + 3, 157f, height * .65f, 194f, height,
            55f, height, 81f, height * .65f)
        for (i in 0..135) {
            val x = (i * 61 % 236).toFloat()
            val y = floor + 6 + i * 37 % (height - floor - 9).toInt().coerceAtLeast(1)
            // Touffes surtout sur les bords du chemin, pas de grands objets devant les acteurs.
            if (jungle || x < 65 || x > 175) {
                rect(c, x, y, 4f, 1f, tone(0xFF537750, 0xFF2D4949))
                rect(c, x + 1, y - 2, 1f, 3f, tone(0xFFA3B57A, 0xFF55726B))
                if (!jungle && i % 5 == 0) {
                    rect(c, x, y - 3, 3f, 2f, tone(if (i % 2 == 0) 0xFFE3C386 else 0xFFC6AAC3, 0xFF9B9CB9))
                }
            }
        }
        if (jungle) {
            // Troncs sur les marges, larges feuilles découpées au-dessus de la scène.
            for (side in 0..1) {
                c.save()
                if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
                polygon(c, tone(0xFF554B42, 0xFF292B38), 3f, 0f, 17f, 0f,
                    14f, floor + 28, 22f, floor + 37, 0f, floor + 37)
                rect(c, 7f, 8f, 3f, floor + 17, tone(0xFF998367, 0xFF566363))
                for (j in 0..3) {
                    val y = 15f + j * 11
                    polygon(c, tone(0xFF315F50, 0xFF1B3540), 10f, y, 30f, y - 9,
                        55f, y - 2, 39f, y + 2, 46f, y + 6, 29f, y + 5, 32f, y + 10, 16f, y + 5)
                    rect(c, 14f, y, 23f, 1f, tone(0xFF85A46B, 0xFF497569))
                }
                for (j in 0..2) {
                    rect(c, 26f + j * 8, 0f, 1f, 29f + j * 5, tone(0xFF547C5C, 0xFF345253))
                }
                // Massifs tropicaux : feuilles de bananier, fougères et fleurs contrastées.
                val count = ((height - floor) / 32).toInt().coerceAtLeast(1)
                for (j in 0..count) {
                    val y = floor - 9 + j * 32
                    val spread = if (j == 0) 31f else 15f
                    val green = tone(if (j % 2 == 0) 0xFF3F8468 else 0xFF487C84, 0xFF315C62)
                    val light = tone(0xFF9ABF75, 0xFF679788)
                    polygon(c, green, 4f, y + 14, 1f, y - 6, 8f, y - 14,
                        13f, y - 5, 9f, y + 8, spread, y - 10, spread + 5, y - 5,
                        spread - 3, y + 5, 10f, y + 16)
                    rect(c, 6f, y - 7, 1f, 20f, light)
                    for (leaf in 0..3) {
                        val ly = y + leaf * 3
                        polygon(c, light, 7f, ly + 4, 11f + leaf * 2, ly - 2,
                            17f + leaf * 2, ly - 1, 10f, ly + 5)
                    }
                    val fx = if (j == 0) 24f else 8f
                    val petal = tone(when (j % 3) {
                        0 -> 0xFFE28AA9
                        1 -> 0xFFEDAA64
                        else -> 0xFFAB96DE
                    }, when (j % 3) {
                        0 -> 0xFFAD739C
                        1 -> 0xFFBE946D
                        else -> 0xFF938FC4
                    })
                    rect(c, fx + 2, y + 3, 1f, 10f, green)
                    rect(c, fx, y - 4, 5f, 9f, petal)
                    rect(c, fx - 2, y - 2, 9f, 5f, petal)
                    rect(c, fx + 1, y - 1, 3f, 3f, tone(0xFFFFDB85, 0xFFE2C68F))
                }
                c.restore()
            }
            for (i in 0..6) {
                val x = 27f + i * 29
                rect(c, x, floor + 6, 2f, 7f, tone(0xFF406352, 0xFF334E51))
                rect(c, x - 2, floor + 4, 6f, 3f, tone(0xFFCD8B9F, 0xFF87AEAA))
                rect(c, x, floor + 4, 2f, 2f, tone(0xFFF0CF90, 0xFFC2DEC2))
            }
            // Branche centrale : l'oiseau animé reste au-dessus de la zone de combat.
            polygon(c, tone(0xFF624C43, 0xFF383A48), 90f, floor - 5, 110f, floor - 8,
                131f, floor - 8, 150f, floor - 13, 150f, floor - 9, 132f, floor - 4,
                110f, floor - 4, 90f, floor - 2)
            rect(c, 106f, floor - 7, 29f, 1f, tone(0xFFAB8865, 0xFF69777A))
        } else {
            // Clôture rustique au fond, avec passage central.
            for (start in intArrayOf(0, 157)) {
                val wood = tone(0xFF9B8060, 0xFF5B6267)
                rect(c, start.toFloat(), floor - 3, 83f, 2f, wood)
                rect(c, start.toFloat(), floor + 3, 83f, 2f, wood)
                for (x in start..start + 80 step 16) {
                    rect(c, x.toFloat(), floor - 8, 3f, 18f, wood)
                    rect(c, x.toFloat(), floor - 8, 1f, 17f, tone(0xFFC0AC83, 0xFF82908C))
                }
            }
        }
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float, jungle: Boolean, night: Boolean) {
        if (jungle) parrot(c, floor, clock, night) else rabbit(c, floor, clock, night)
        val tick = (clock / 220).toInt()
        for (i in 0..5) {
            val x = 24f + i * 35 + (tick + i) % 5
            val y = floor + 14 + (i * 17 % 42)
            val color = if (night) 0xFFD6DCA0.toInt() else if (jungle) 0xFFD9A5BC.toInt() else 0xFFF0D9A4.toInt()
            if (!night || (tick + i) % 7 < 4) {
                rect(c, x, y, 1f, 1f, color)
                if (!night) rect(c, x + 2, y - tick % 2, 1f, 2f, color)
            }
        }
    }

    private fun rabbit(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        fun tone(day: Long, dark: Long) = (if (night) dark else day).toInt()
        val outline = 0xFF303443.toInt()
        val fur = tone(0xFFE4D5BC, 0xFFB1BDCA)
        val light = tone(0xFFFFEBD0, 0xFFD4DCE0)
        val shade = tone(0xFFB6A48D, 0xFF7D8FA6)
        val pink = tone(0xFFDFA5AA, 0xFFBB95B0)
        val tick = (clock / 180).toInt()
        val nibble = tick % 32 in 14..23
        val chew = if (nibble) tick % 2 else 0
        val ear = if (tick % 29 in 20..23) 1f else 0f
        val y = floor - 17
        // Assis au centre, juste devant la clôture ; seules les petites articulations bougent.
        rect(c, 108f, floor + 2, 26f, 2f, tone(0xFF5A7354, 0xFF283E45))
        rect(c, 127f, y + 10, 6f, 6f, outline)
        rect(c, 128f, y + 11, 4f, 4f, light)
        rect(c, 117f, y + 6, 10f, 2f, outline)
        rect(c, 114f, y + 8, 16f, 9f, outline)
        rect(c, 116f, y + 7, 10f, 9f, fur)
        rect(c, 115f, y + 10, 14f, 6f, fur)
        rect(c, 122f, y + 11, 6f, 6f, shade)
        rect(c, 124f, y + 12, 3f, 4f, fur)
        rect(c, 113f, y + 17, 16f, 2f, outline)
        rect(c, 114f, y + 17, 5f, 1f, light)
        rect(c, 122f, y + 17, 6f, 1f, light)
        // Grandes oreilles à intérieur rose, avec un bref pivot de l'oreille arrière.
        rect(c, 114f + ear, y - 8, 4f, 13f, outline)
        rect(c, 115f + ear, y - 7, 2f, 11f, fur)
        rect(c, 115f + ear, y - 5, 1f, 8f, pink)
        rect(c, 109f, y - 6, 4f, 12f, outline)
        rect(c, 110f, y - 5, 2f, 10f, light)
        rect(c, 111f, y - 3, 1f, 7f, pink)
        rect(c, 108f, y + 2, 12f, 10f, outline)
        rect(c, 109f, y + 3, 10f, 8f, fur)
        rect(c, 106f, y + 7, 8f, 5f, outline)
        rect(c, 107f, y + 7, 7f, 4f, light)
        rect(c, 110f, y + 5, 2f, if (tick % 25 == 0) 1f else 2f, outline)
        if (tick % 25 != 0) rect(c, 110f, y + 5, 1f, 1f, light)
        rect(c, 106f, y + 8 + (if (tick % 7 == 0) 1 else 0), 2f, 1f, pink)
        rect(c, 109f, y + 11 + chew, 3f, 1f, shade)
        // Petite feuille tenue entre les pattes pendant le grignotage.
        val green = tone(0xFF83A762, 0xFF719783)
        rect(c, 109f, y + 14 - chew, 2f, 4f, green)
        rect(c, 106f, y + 12 - chew, if (nibble) 4f else 6f, 2f, green)
        rect(c, 111f, y + 13, 4f, 3f, outline)
        rect(c, 111f, y + 13, 3f, 2f, light)
    }

    private fun parrot(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        fun tone(day: Long, dark: Long) = (if (night) dark else day).toInt()
        val outline = 0xFF202939.toInt()
        val red = tone(0xFFE77E79, 0xFFB36D85)
        val yellow = tone(0xFFF1CE75, 0xFFC3B586)
        val blue = tone(0xFF569CB4, 0xFF538499)
        val green = tone(0xFF79AC80, 0xFF669080)
        val tick = (clock / 180).toInt()
        val flutter = if (tick % 27 in 18..23) tick % 3 else 0
        val tail = intArrayOf(0, 0, 1, 1, 0, -1, -1, 0)[tick % 8]
        val y = floor - 26
        // Queue et aile seules bougent, les pattes restent accrochées à la branche.
        rect(c, 121f + tail, y + 13, 4f, 12f, outline)
        rect(c, 122f + tail, y + 13, 2f, 10f, blue)
        rect(c, 119f, y + 6, 9f, 12f, outline)
        rect(c, 118f, y + 8, 9f, 8f, red)
        rect(c, 119f, y + 14, 6f, 3f, yellow)
        rect(c, 123f, y + 7 - flutter, 5f + flutter * 2, 7f, outline)
        rect(c, 124f, y + 8 - flutter, 3f + flutter * 2, 4f, green)
        rect(c, 125f, y + 12 - flutter, 3f + flutter, 3f, blue)
        val head = if (tick % 32 in 20..27) -1f else 0f
        rect(c, 116f + head, y, 10f, 9f, outline)
        rect(c, 117f + head, y - 1, 7f, 2f, outline)
        rect(c, 117f + head, y + 1, 8f, 7f, red)
        rect(c, 116f + head, y + 3, 5f, 5f, tone(0xFFF1E2C2, 0xFFC1C7BB))
        rect(c, 118f + head, y + 3, 2f, if (tick % 23 == 0) 1f else 2f, outline)
        rect(c, 112f + head, y + 4, 4f, 3f, yellow)
        rect(c, 113f + head, y + 7, 3f, 2f, outline)
        rect(c, 120f, floor - 9, 1f, 3f, yellow)
        rect(c, 124f, floor - 9, 1f, 3f, yellow)
        rect(c, 119f, floor - 7, 3f, 1f, yellow)
        rect(c, 123f, floor - 7, 3f, 1f, yellow)
    }
}
