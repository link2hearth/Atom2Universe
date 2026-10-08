package com.Atom2Universe.app.games.farm

import android.graphics.*
import kotlin.math.*

/** Pixel scenery is drawn once into a cache; no atlas or PNG is used. */
object LivestockHabitatArt {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val assets = mutableMapOf<String, Bitmap>()
    private val shelters = mapOf("chicken_coop" to LivestockKind.CHICKENS, "sheep_shelter" to LivestockKind.SHEEP,
        "pig_shelter" to LivestockKind.PIGS, "cattle_shelter" to LivestockKind.CATTLE)
    fun supports(id: String) = id in shelters || id == "feed_trough" || id == "water_trough"
    fun asset(canvas: Canvas, id: String, target: RectF) {
        val b = assets.getOrPut(id) {
            val bitmap = Bitmap.createBitmap(144, 110, Bitmap.Config.ARGB_8888)
            val p = Pixel(Canvas(bitmap))
            shelters[id]?.let { p.shelter(it, 12, 8) }
                ?: if (id == "water_trough") p.water(38, 58, 66, false) else p.feeder(LivestockKind.CATTLE, 38, 55)
            bitmap
        }
        val s = min(target.width() / b.width, target.height() / b.height)
        canvas.drawBitmap(b, null, RectF(target.centerX() - b.width * s / 2, target.bottom - b.height * s,
            target.centerX() + b.width * s / 2, target.bottom), paint)
    }
    fun bake(kind: LivestockKind, height: Int): Bitmap {
        val b = Bitmap.createBitmap(320, height, Bitmap.Config.ARGB_8888)
        val p = Pixel(Canvas(b)); p.ground(kind, height)
        p.shelter(kind, 26, 24)
        p.feeder(kind, 204, 92)
        p.water(216, 139, if (kind == LivestockKind.CATTLE) 65 else 48, kind == LivestockKind.PIGS)
        if (kind == LivestockKind.PIGS) {
            p.oval(70, height - 59, 39, 18, 0xB29379); p.oval(70, height - 60, 34, 14, 0xC5A58A)
            p.r(50, height - 64, 15, 2, 0xDDC0A1); p.r(76, height - 54, 12, 2, 0xA88770)
        }
        p.fence(height - 18)
        return b
    }
    private class Pixel(val c: Canvas) {
        fun r(x: Int, y: Int, w: Int, h: Int, color: Int) {
            paint.color = color or 0xFF000000.toInt()
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        fun oval(x: Int, y: Int, rx: Int, ry: Int, color: Int) {
            for (dy in -ry..ry) { val half = (sqrt(max(0.0, 1.0 - dy * dy.toDouble() / (ry * ry))) * rx).roundToInt()
                r(x - half, y + dy, max(1, half * 2), 1, color) }
        }
        fun poly(points: List<Pair<Int, Int>>, color: Int) {
            for (y in points.minOf { it.second }..points.maxOf { it.second }) {
                val hits = mutableListOf<Double>()
                points.indices.forEach { i -> val a = points[i]; val b = points[(i + 1) % points.size]
                    if ((a.second <= y && b.second > y) || (b.second <= y && a.second > y))
                        hits.add(a.first + (y - a.second).toDouble() * (b.first - a.first) / (b.second - a.second)) }
                hits.sort(); for (i in 0 until hits.size - 1 step 2)
                    r(ceil(hits[i]).toInt(), y, max(1, floor(hits[i + 1]).toInt() - ceil(hits[i]).toInt()), 1, color)
            }
        }
        fun ground(kind: LivestockKind, h: Int) {
            val base = when (kind) { LivestockKind.SHEEP -> 0xACCC94; LivestockKind.PIGS -> 0xB5C790; else -> 0xA1CA8B }
            r(0, 0, 320, h, base)
            val random = java.util.Random(780L + kind.ordinal)
            repeat(h * 3) { r(random.nextInt(320), random.nextInt(h), 2 + random.nextInt(4), 1 + random.nextInt(2),
                if (it % 2 == 0) 0xBBD99D else 0x98BD80) }
            for (x in -8..328 step 23) {
                oval(x, 13, 20, 16, 0x60946C); oval(x - 3, 8, 18, 13, 0x82B086)
                oval(x - 5, 4, 10, 7, 0xA4C991)
            }
            // A few sparse flower borders.
            repeat(28) {
                val x = if (it % 2 == 0) 7 + random.nextInt(14) else 296 + random.nextInt(18)
                val y = 48 + random.nextInt(max(1, h - 78))
                r(x, y, 1, 6, 0x6D9D70); r(x - 2, y + 3, 3, 1, 0x87AE78)
                val flower = if (it % 3 == 0) 0xE8C5D8 else 0xFFF0CD
                r(x - 2, y - 2, 5, 3, flower); r(x - 1, y - 3, 3, 5, flower); r(x, y - 1, 1, 1, 0xDFBD77)
            }
            // Fence posts at the sides, with a muted stone at each entrance corner.
            for (y in 45 until h - 25 step 35) { r(4, y, 5, 23, 0xBD9B74); r(5, y, 2, 23, 0xE9CCA0); r(311, y, 5, 23, 0xBD9B74) }
        }
        fun fence(y: Int) {
            for (start in listOf(5, 188)) {
                r(start, y - 6, 126, 3, 0xA48769); r(start, y - 7, 126, 1, 0xE4C79C)
                r(start, y + 2, 126, 3, 0xBA9A73)
                for (x in start until start + 126 step 31) { r(x, y - 13, 5, 23, 0xB59470); r(x + 1, y - 14, 3, 2, 0xEAD3AC); r(x + 1, y - 11, 2, 19, 0xDDC096) }
            }
        }
        fun shelter(kind: LivestockKind, x: Int, y: Int) {
            val poultry = kind == LivestockKind.CHICKENS; val pig = kind == LivestockKind.PIGS
            val sheep = kind == LivestockKind.SHEEP
            val width = if (poultry) 84 else 110
            oval(x + width / 2, y + 83, width / 2 + 4, 6, 0x88A477)
            val wall = if (pig) 0xD9BEAB else if (sheep) 0xD5C5A1 else 0xE4C99D
            r(x + 7, y + 34, width - 14, 47, 0xA58B71); r(x + 8, y + 34, width - 16, 44, wall)
            for (xx in x + 14 until x + width - 12 step 9) r(xx, y + 38, 1, 37, 0xBEA486)
            if (pig) for (yy in y + 44..y + 73 step 9) r(x + 9, yy, width - 18, 1, 0xC2A694)
            val roof = when (kind) { LivestockKind.CHICKENS -> 0xB78282; LivestockKind.SHEEP -> 0x8AA99B; LivestockKind.PIGS -> 0xBB9EB1; else -> 0xA77477 }
            if (sheep) {
                // Open-sided lean-to, visibly distinct from the enclosed barns.
                poly(listOf(x to (y + 17), (x + width - 8) to (y + 6), (x + width) to (y + 36), x to (y + 36)), 0x71887C)
                poly(listOf((x + 3) to (y + 19), (x + width - 10) to (y + 9), (x + width - 4) to (y + 32), (x + 3) to (y + 32)), roof)
                for (xx in x + 9 until x + width - 9 step 12) r(xx, y + 20, 2, 11, 0xB8CCAF)
            } else {
                val peak = if (pig) 15 else 0
                poly(listOf(x to (y + 37), (x + width / 2) to (y + peak), (x + width) to (y + 37)), 0x806E69)
                poly(listOf((x + 3) to (y + 33), (x + width / 2) to (y + peak + 3), (x + width - 3) to (y + 33)), roof)
                for (row in 0..2) { val yy = y + peak + 7 + row * (if (pig) 4 else 8)
                    val inset = (width / 2 * (1 - (yy - y - peak).toDouble() / (37 - peak))).toInt()
                    r(x + inset + 2, yy, max(1, width - inset * 2 - 4), 1, 0xD1A9A3) }
            }
            r(x + 1, y + 34, width - 2, 4, 0x927769); r(x + 2, y + 34, width - 4, 1, 0xE4C39C)
            if (poultry) {
                r(x + 14, y + 73, 5, 17, 0x94795E); r(x + 65, y + 73, 5, 17, 0x94795E)
                r(x + 32, y + 49, 23, 27, 0x816857); r(x + 35, y + 52, 17, 24, 0x665B4E)
                poly(listOf((x + 34) to (y + 74), (x + 53) to (y + 74), (x + 64) to (y + 97), (x + 29) to (y + 97)), 0xBB9C70)
                for (i in 0..4) r(x + 32 - i / 2, y + 78 + i * 4, 23 + i * 2, 2, 0xE6CCA0)
                r(x + 12, y + 44, 12, 14, 0x8F7970); r(x + 14, y + 46, 8, 10, 0xA8C8C1); r(x + 17, y + 46, 1, 10, 0xF6DEB5)
            } else {
                val opening = if (sheep) 68 else 46
                r(x + width / 2 - opening / 2, y + 44, opening, 36, 0x776953)
                r(x + width / 2 - opening / 2 + 3, y + 47, opening - 6, 32, 0x897955)
                for (i in 0..8) r(x + width / 2 - opening / 2 + 3 + i * (opening - 6) / 9, y + 76 + i % 3, 5, 1, 0xE3C779)
                if (!sheep) { r(x + 10, y + 43, 18, 35, roof); r(x + width - 28, y + 43, 18, 35, roof)
                    for (i in 0..16) { r(x + 11 + i, y + 46 + i, 2, 2, 0xEDCEAD); r(x + width - 27 + i, y + 62 - i, 2, 2, 0xEDCEAD) } }
            }
        }
        fun feeder(kind: LivestockKind, x: Int, y: Int) {
            when (kind) {
                LivestockKind.CHICKENS -> {
                    oval(x + 24, y + 19, 23, 6, 0xA08671); oval(x + 24, y + 17, 21, 5, 0xDFC494)
                    poly(listOf((x + 15) to (y - 12), (x + 32) to (y - 12), (x + 38) to (y + 15), (x + 9) to (y + 15)), 0xB69DA1)
                    r(x + 15, y - 12, 17, 3, 0xE6C4BE); r(x + 18, y - 7, 3, 19, 0xD8BBC1)
                    for (i in 0..9) r(x + 5 + i * 4, y + 17 + i % 2, 2, 1, 0xF4DB89)
                }
                LivestockKind.SHEEP -> {
                    r(x + 2, y + 5, 48, 16, 0xB69C75); r(x + 5, y - 9, 42, 22, 0xD8C280)
                    for (i in 0..13) r(x + 7 + i * 3, y - 7 + i % 3, 1, 17, 0xF2DEA0)
                    for (i in 0..5) r(x + 3 + i * 9, y - 9, 3, 32, 0x927B61)
                    r(x + 1, y - 10, 51, 3, 0xD2B88D); r(x + 3, y + 13, 48, 3, 0x9C835F)
                }
                else -> {
                    val stone = kind == LivestockKind.PIGS
                    r(x, y + 3, 63, 19, if (stone) 0xAAA6A2 else 0xAA8C6B)
                    r(x + 2, y + 1, 59, 6, if (stone) 0xD0C9BC else 0xDEBF92)
                    r(x + 5, y + 4, 53, 4, 0x8E795D)
                    for (i in 0..17) r(x + 6 + i * 3, y + 4 + i % 2, 2, 1, 0xEBD092)
                    for (i in 0..3) r(x + 7 + i * 15, y + 9, 2, 11, if (stone) 0xBAB3A8 else 0xD0AE7D)
                    r(x + 3, y + 22, 5, 4, 0x8F806A); r(x + 55, y + 22, 5, 4, 0x8F806A)
                }
            }
        }
        fun water(x: Int, y: Int, width: Int, stone: Boolean) {
            oval(x + width / 2, y + 10, width / 2, 10, if (stone) 0xA7AAA3 else 0x88A5A3)
            r(x + 2, y, width - 4, 10, if (stone) 0xB9BDB0 else 0xABC5BC)
            oval(x + width / 2, y, width / 2, 7, 0xD5DBCB)
            oval(x + width / 2, y, width / 2 - 4, 4, 0x709FA6)
            oval(x + width / 2 - 2, y - 1, width / 2 - 6, 2, 0x9CC9C9)
            r(x + 9, y - 2, 11, 1, 0xD8EBDB); r(x + width - 18, y + 1, 7, 1, 0xC3E1D7)
        }
    }
}
