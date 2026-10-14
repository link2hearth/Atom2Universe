package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Region 3: a ruined castle under an ember sky, a smoking volcano, braziers, bats and glowing cracks. */
internal class RuinsScene : ScenePainter(53) {
    private val batSeeds = seeds(12)
    private val emberSeeds = seeds(72)
    private var glow: Bitmap? = null
    private var crack = IntArray(0)
    private var windowX = IntArray(0)
    private var windowY = IntArray(0)
    private var fireX = IntArray(0)
    private var fireY = IntArray(0)
    private var craterX = -1
    private var craterY = 0

    override fun sky(a: IntArray) = gradient(a, 0, hz + 2, intArrayOf(rgb(0x1A1430), rgb(0x352049), rgb(0x6E2C4A), rgb(0xB8503C), rgb(0xEE9448)))

    override fun skyShapes(p: ScenePen) {
        for (i in 0 until w * hz / 60) p.dot(random.nextInt(w), random.nextInt(max(1, (hz * .45f).toInt())), argb(0x50 + random.nextInt(0x90), 0xFFE8D8))
    }

    override fun ground(a: IntArray) {
        gradient(a, hz, h, intArrayOf(rgb(0x564A64), rgb(0x463C52), rgb(0x362E41)), 9)
        // Flagstones growing toward the viewer, joints staggered row by row.
        var y = hz + 1; var row = 0
        while (y < h) {
            val rh = 2 + ((y - hz) * .11f).toInt(); val len = 5 + ((y - hz) * .22f).toInt()
            val off = if (row % 2 == 0) 0 else len / 2
            for (yy in y until min(h, y + rh)) for (x in 0 until w) {
                val i = yy * w + x
                if (yy == y || (x + off) % len == 0) { a[i] = rgb(0x2B2433); continue }
                val tone = hash((x + off) / len, row, 1)
                a[i] = when {
                    yy == y + 1 -> mix(a[i], rgb(0x6E607C), .45f)
                    tone < .3f -> mix(a[i], rgb(0x2F2838), .35f)
                    tone > .8f -> mix(a[i], rgb(0x6E607C), .2f)
                    else -> a[i]
                }
                if (field(x, yy) > 1.1f && noise(x * .1f, yy * .2f, 2) > .62f) a[i] = mix(a[i], rgb(0x2A2530), .55f)
            }
            y += rh; row++
        }
        // Cracks that glow with the fire underneath.
        val points = ArrayList<Int>()
        for (k in 0 until 7) {
            var x = random.nextInt(w); var cy = hz + 6 + random.nextInt(max(1, h - hz - 10))
            val dir = if (random.nextBoolean()) 1 else -1
            for (s in 0 until 12 + random.nextInt(16)) {
                if (random.nextFloat() < .72f) x += dir
                cy += random.nextInt(3) - 1
                if (x !in 0 until w || cy !in hz + 2 until h) break
                a[cy * w + x] = rgb(0x6E2620); points.add(x); points.add(cy)
            }
        }
        crack = points.toIntArray()
    }

    override fun land(p: ScenePen) {
        val sil = rgb(0x2A1C33); val rim = rgb(0x5A3046)
        // The volcano behind the ruins.
        val vx = (w * .74f).toInt(); val height = min(hz - 8, 48)
        for (r in 0 until height) {
            val half = 4 + (r * 1.2f).toInt(); val y = hz + 2 - height + r
            p.rect(vx - half, y, half * 2 + 1, 1, rgb(0x3A2236)); p.rect(vx - half, y, max(1, half / 3), 1, rgb(0x4C2C44))
        }
        craterX = vx; craterY = hz + 2 - height
        p.rect(vx - 4, craterY, 9, 1, rgb(0xE0602A))
        for (s in 0..1) {
            var x = vx + (s * 2 - 1) * 2
            for (k in 0 until (height * .55f).toInt()) {
                x += random.nextInt(3) - 1 + if (k % 4 == 3) s * 2 - 1 else 0
                p.dot(x, craterY + 1 + k, if (k < 5) rgb(0xFFB04A) else rgb(0xB83A28))
            }
        }
        // Towers and broken walls.
        val wins = ArrayList<Int>()
        val towers = if (w > 300) floatArrayOf(.06f, .22f, .4f, .93f) else floatArrayOf(.06f, .24f, .42f)
        var lastRight = -1
        for ((n, f) in towers.withIndex()) {
            val tw = 9 + random.nextInt(5); val x = (w * f).toInt() - tw / 2
            val top = hz - 26 - random.nextInt(min(20, max(1, hz - 34)))
            if (lastRight >= 0 && n < 3 && x - lastRight > 2) {
                val wallTop = hz - 10 - random.nextInt(4)
                for (c in lastRight until x) {
                    val gap = abs(c - (lastRight + x) / 2) < 3
                    val ct = wallTop + if (gap) 5 else 0
                    p.rect(c, ct, 1, hz + 4 - ct, sil)
                    if (!gap && (c - lastRight) % 4 < 2) p.rect(c, ct - 2, 1, 2, sil)
                    p.dot(c, ct, rim)
                }
            }
            for (c in 0 until tw) {
                val broken = if (hash(c, x, 3) < .3f) 1 + (hash(c, x, 4) * 4).toInt() else 0
                val ct = top + broken
                p.rect(x + c, ct, 1, hz + 4 - ct, sil)
                if (broken == 0 && c % 3 < 2) p.rect(x + c, ct - 2, 1, 2, sil)
            }
            p.rect(x, top, 1, hz + 4 - top, rim)
            var wy = top + 5
            while (wy < hz - 6) { val wx = x + tw / 2 - 1; p.rect(wx, wy, 2, 3, rgb(0x8A3A22)); wins.add(wx); wins.add(wy); wy += 11 }
            lastRight = x + tw
        }
        windowX = IntArray(wins.size / 2) { wins[it * 2] }; windowY = IntArray(wins.size / 2) { wins[it * 2 + 1] }
        // Broken pillars at the sides, braziers flanking the plaza.
        for (side in 0..1) {
            val far = cx + (if (side == 0) -1 else 1) * (reach + 6)
            val x = if (far in 4 until w - 4) far else if (side == 0) 5 else w - 6
            val foot = hz + 13; val tall = 18 + random.nextInt(6)
            p.rect(x - 2, foot - tall, 5, tall, rgb(0x5E5266)); p.rect(x - 2, foot - tall, 1, tall, rgb(0x7C6E86)); p.rect(x + 2, foot - tall, 1, tall, rgb(0x463C52))
            p.rect(x - 3, foot - tall - 2, 7, 2, rgb(0x6A5C76)); p.rect(x - 3, foot - 1, 7, 2, rgb(0x4E4460))
            p.rect(x - 1, foot - tall - 4, 2, 2, rgb(0x6A5C76))
        }
        val fires = ArrayList<Int>()
        for (side in -1..1 step 2) {
            val bx = cx + side * 13; val foot = hz + 8
            if (bx !in 3 until w - 3) continue
            p.rect(bx, foot - 7, 1, 7, rgb(0x2A2430)); p.dot(bx - 1, foot, rgb(0x2A2430)); p.dot(bx + 1, foot, rgb(0x2A2430))
            p.rect(bx - 3, foot - 9, 7, 2, rgb(0x4A3F4F)); p.rect(bx - 3, foot - 9, 7, 1, rgb(0x6A5C70))
            fires.add(bx); fires.add(foot - 10)
        }
        fireX = IntArray(fires.size / 2) { fires[it * 2] }; fireY = IntArray(fires.size / 2) { fires[it * 2 + 1] }
        for (i in 0 until 40) {
            val x = random.nextInt(w); val y = hz + 4 + random.nextInt(h - hz - 4)
            if (!outside(x, y, .05f)) continue
            val s = 1 + (y - hz) * 3 / max(1, h - hz)
            p.rect(x, y - s, s + 1, s, rgb(0x5E5266)); p.rect(x, y - s, s + 1, 1, rgb(0x7C6E86))
        }
    }

    override fun sprites(p: ScenePen) {
        glow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            p.c = Canvas(b)
            for (i in 0 until crack.size / 2) {
                val x = crack[i * 2]; val y = crack[i * 2 + 1]
                p.rect(x - 1, y - 1, 3, 3, argb(0x40, 0xFF6A2A)); p.dot(x, y, rgb(0xFF9A3A))
            }
        }
    }

    override fun skyMotion(p: ScenePen, t: Float) {
        if (craterX >= 0) for (i in 0..5) {
            val age = (t * .22f + i / 6f) % 1f
            val r = 2 + (age * 6).toInt()
            p.disc(craterX + (age * 16 + sin(age * 6 + i) * 2).toInt(), craterY - 2 - (age * 30).toInt(), r, argb(((1 - age) * 0x80).toInt(), 0x5A4560))
        }
        for (i in 0 until batSeeds.size / 3) {
            val x = wrap(batSeeds[i * 3], w, 9f + batSeeds[i * 3 + 2] * 8, t, 6)
            val y = (hz * (.18f + batSeeds[i * 3 + 1] * .4f)).toInt() + (sin(t * 2.3f + i) * 5).toInt()
            val up = ((t * 9 + i).toInt() and 1) == 0; val col = rgb(0x140C1C)
            p.rect(x - 1, y, 3, 1, col); p.dot(x, y + 1, col); p.dot(x - 1, y - 1, col); p.dot(x + 1, y - 1, col)
            p.dot(x - 2, if (up) y - 1 else y + 1, col); p.dot(x + 2, if (up) y - 1 else y + 1, col)
        }
    }

    override fun landMotion(p: ScenePen, t: Float) {
        glow?.let { p.bitmap(it, 0, 0, (130 + 90 * sin(t * 1.7f)).toInt()) }
        if (craterX >= 0) p.rect(craterX - 3, craterY, 7, 1, argb((150 + 100 * sin(t * 2f)).toInt(), 0xFFD27A))
        for (i in windowX.indices) {
            val a = 130 + ((sin(t * 7f + i * 2.1f) + sin(t * 3.3f + i)) * 55).toInt()
            p.rect(windowX[i], windowY[i], 2, 3, argb(a, 0xFFC060)); p.dot(windowX[i], windowY[i] - 1, argb(a / 2, 0xFFC060))
        }
        for (i in fireX.indices) {
            val x = fireX[i]; val y = fireY[i]
            p.disc(x, y - 2, 7, argb((0x24 + 0x10 * sin(t * 5f + i)).toInt(), 0xFF8A3A))
            val tick = (t * 10).toInt()
            for (k in -2..2) {
                val tall = 2 + (hash(tick, k, i) * 3).toInt() + (2 - abs(k)) * 2
                p.rect(x + k, y - tall + 1, 1, tall, rgb(0xF0782A))
                if (abs(k) < 2) p.rect(x + k, y - tall + 3, 1, max(0, tall - 3), rgb(0xFFD45A))
            }
            p.dot(x, y, rgb(0xFFF4B0))
        }
    }

    override fun front(p: ScenePen, t: Float) {
        val top = (hz * .3f).toInt()
        for (i in 0 until emberSeeds.size / 3) {
            val s = emberSeeds[i * 3 + 2]
            val y = h - wrap(emberSeeds[i * 3 + 1], h - top, 9f + s * 14f, t)
            val x = (emberSeeds[i * 3] * w).toInt() + (sin(t * 1.3f + s * 9) * 4).toInt()
            val fade = ((y - top).toFloat() / (h - top)).coerceIn(0f, 1f)
            p.dot(x, y, argb((60 + 195 * fade).toInt(), if (((t * 6).toInt() + i) and 1 == 0) 0xFFD27A else 0xFF7A2A))
        }
    }
}

/** Region 4: a marsh at night under a full moon, fireflies, reeds swaying around dark pools. */
internal class MarshScene : ScenePainter(71) {
    private val starSeeds = seeds(36)
    private val flySeeds = seeds(72)
    private var mistLow: Bitmap? = null
    private var poolX = IntArray(0)
    private var poolY = IntArray(0)
    private var poolR = IntArray(0)
    private var reedX = IntArray(0)
    private var reedY = IntArray(0)
    private var moonX = 0
    private var moonY = 0

    override fun sky(a: IntArray) = gradient(a, 0, hz + 2, intArrayOf(rgb(0x0A1028), rgb(0x161F45), rgb(0x283A64), rgb(0x46638A)))

    override fun skyShapes(p: ScenePen) {
        for (i in 0 until w * hz / 40) p.dot(random.nextInt(w), random.nextInt(max(1, (hz * .8f).toInt())), argb(0x40 + random.nextInt(0x80), 0xDDE6FF))
        moonX = (w * .7f).toInt(); moonY = (hz * .3f).toInt()
        p.disc(moonX, moonY, 18, argb(0x14, 0xFFF2D0)); p.disc(moonX, moonY, 13, argb(0x22, 0xFFF2D0))
        p.disc(moonX, moonY, 8, rgb(0xF4EBC8))
        p.rect(moonX - 3, moonY - 2, 2, 2, rgb(0xDCD2B0)); p.dot(moonX + 2, moonY + 2, rgb(0xDCD2B0)); p.rect(moonX + 3, moonY - 4, 2, 1, rgb(0xDCD2B0))
    }

    private fun bank(x: Int) = hz + 11 + (2 * sin(x * .07f) + sin(x * .19f)).toInt()

    override fun ground(a: IntArray) {
        gradient(a, hz, h, intArrayOf(rgb(0x2E4A35), rgb(0x263F2E), rgb(0x1C3124)), 9)
        val pools = ArrayList<Int>()
        for (i in 0 until 40) {
            val x = random.nextInt(w); val y = hz + 16 + random.nextInt(max(1, h - hz - 18))
            val rx = 5 + (y - hz) * 9 / max(1, h - hz)
            if (outside(x, y, .2f) && pools.size < 24) { pools.add(x); pools.add(y); pools.add(rx) }
        }
        poolX = IntArray(pools.size / 3) { pools[it * 3] }; poolY = IntArray(pools.size / 3) { pools[it * 3 + 1] }; poolR = IntArray(pools.size / 3) { pools[it * 3 + 2] }
        val mx = (w * .7f).toInt()
        for (y in hz - 1 until h) for (x in 0 until w) {
            val i = y * w + x
            if (y < bank(x)) {
                a[i] = mix(rgb(0x1B2D4A), rgb(0x24405F), (y - hz + 1) / 12f)
                if (hash(x / 4, y, 1) < .12f) a[i] = rgb(0x2E4A6E)
                if (abs(x - mx) < 4 - (y - hz) / 4 && (y - hz) % 2 == 0) a[i] = rgb(0x5A6E86)
                continue
            }
            if (y == bank(x)) { a[i] = rgb(0x3D5A3E); continue }
            val f = field(x, y)
            val mud = noise(x * .045f, y * .11f, 3)
            when {
                f < .85f && mud < .38f -> a[i] = rgb(0x32533A)
                mud > .76f -> a[i] = if (hash(x, y, 2) < .2f) rgb(0x2B2B22) else rgb(0x34352A)
                hash(x, y, 3) < .03f -> { a[i] = rgb(0x4C7A4C); put(a, x, y - 1, rgb(0x3F6844)) }
            }
        }
        for (k in poolX.indices) {
            val px = poolX[k]; val py = poolY[k]; val rx = poolR[k]; val ry = max(2, rx / 3)
            for (dy in -ry..ry) for (dx in -rx..rx) {
                val e = (dx * dx).toFloat() / (rx * rx) + (dy * dy).toFloat() / (ry * ry)
                if (e > 1) continue
                put(a, px + dx, py + dy, if (dy == -ry || e > .75f && dy < 0) rgb(0x34557A) else if (hash(px + dx, py + dy, 4) < .08f) rgb(0x2A4466) else rgb(0x1A2E48))
            }
        }
    }

    override fun land(p: ScenePen) {
        val sil = rgb(0x14233A)
        for (x in 0 until w) { val top = hills(x, hz - 4, 2f, .06f, .4f); p.rect(x, top, 1, hz - top, sil) }
        var x = random.nextInt(8)
        while (x < w) {
            if (random.nextInt(3) == 0) {
                p.oval(x, hz - 9, 8, 5, sil)
                for (s in -7..7 step 2) p.rect(x + s, hz - 8, 1, 6 + random.nextInt(4), sil)
            } else {
                val tall = 10 + random.nextInt(13)
                p.oval(x, hz - 2 - tall / 2, 2, tall / 2, sil); p.rect(x - 1, hz - 3, 3, 3, sil)
            }
            x += 8 + random.nextInt(13)
        }
        // Gnarled dead trees at the edges.
        for (side in 0..1) {
            val tx = if (side == 0) 4 else w - 6
            p.rect(tx, hz - 18, 3, 32, rgb(0x1C2A26)); p.rect(tx - 2, hz + 12, 7, 2, rgb(0x1C2A26))
            p.line(tx + 1, hz - 10, tx + (if (side == 0) 9 else -8), hz - 17, rgb(0x1C2A26))
            p.line(tx + 1, hz - 15, tx + (if (side == 0) -4 else 5), hz - 22, rgb(0x1C2A26))
            p.line(tx + 1, hz - 4, tx + (if (side == 0) 7 else -6), hz - 6, rgb(0x1C2A26))
        }
        for (k in poolX.indices) {
            val rx = poolR[k]
            for (j in 0 until 1 + rx / 4) {
                val lx = poolX[k] + random.nextInt(rx) - rx / 2; val ly = poolY[k] + random.nextInt(max(1, rx / 3)) - rx / 6
                p.oval(lx, ly, 2, 1, rgb(0x3F7A4A)); p.dot(lx + 1, ly - 1, rgb(0x1A2E48))
                if (random.nextInt(3) == 0) p.dot(lx - 1, ly - 1, rgb(0xF2A6C8))
            }
        }
        val reeds = ArrayList<Int>()
        for (k in poolX.indices) { reeds.add(poolX[k] + poolR[k] - 1); reeds.add(poolY[k] + 1) }
        for (i in 0 until 30) {
            val rx = random.nextInt(w); val ry = hz + 13 + random.nextInt(max(1, h - hz - 13))
            if (outside(rx, ry, .1f) && reeds.size < 40) { reeds.add(rx); reeds.add(ry) }
        }
        reedX = IntArray(reeds.size / 2) { reeds[it * 2] }; reedY = IntArray(reeds.size / 2) { reeds[it * 2 + 1] }
    }

    override fun sprites(p: ScenePen) { mistLow = mist(p, 160, 10, argb(0x46, 0xB8CCE8)) }

    override fun skyMotion(p: ScenePen, t: Float) {
        for (i in 0 until starSeeds.size / 3) {
            val b = sin(t * 2.5f + starSeeds[i * 3 + 2] * 20) * .5f + .5f
            if (b < .2f) continue
            val x = (starSeeds[i * 3] * w).toInt(); val y = (starSeeds[i * 3 + 1] * hz * .7f).toInt()
            p.dot(x, y, argb((b * 255).toInt(), 0xFFFFFF))
            if (b > .85f) { p.dot(x - 1, y, argb(0x80, 0xFFFFFF)); p.dot(x + 1, y, argb(0x80, 0xFFFFFF)); p.dot(x, y - 1, argb(0x80, 0xFFFFFF)); p.dot(x, y + 1, argb(0x80, 0xFFFFFF)) }
        }
        val cycle = (t + 3f) % 17f
        if (cycle < .8f) {
            val k = cycle / .8f
            val sx = (w * (.15f + k * .35f)).toInt(); val sy = (hz * (.12f + k * .25f)).toInt()
            for (j in 0..5) p.dot(sx - j * 2, sy - j, argb(((1 - k) * (255 - j * 40)).toInt(), 0xFFFFFF))
        }
    }

    override fun landMotion(p: ScenePen, t: Float) {
        for (k in 0..5) {
            val width = 2 + ((sin(t * 2.3f + k * 1.7f) + 1) * 2).toInt() - k / 3
            p.rect(moonX - width / 2 + sin(t * 1.5f + k).toInt(), hz + k * 2, width, 1, argb(200 - k * 25, 0xF0E8C0))
        }
        mistLow?.let { scroll(p, it, t * 2.5f, hz - 4, 255) }
        for (i in poolX.indices) {
            val cycle = (t * .5f + i * .29f) % 1f
            val r = (cycle * poolR[i] * .8f).toInt()
            if (r > 0) p.ring(poolX[i], poolY[i], r, max(1, r / 3), argb(((1 - cycle) * 0x90).toInt(), 0x9FC0E0))
        }
        for (i in reedX.indices) {
            val x = reedX[i]; val y = reedY[i]
            val sway = sin(t * 1.4f + x * .2f) * 1.2f
            for (s in 0..3) {
                val tall = 7 + (hash(x, s, 5) * 6).toInt()
                val bend = (sway * (1 + s % 2)).toInt()
                p.rect(x + s * 2 - 3, y - tall + 4, 1, tall - 4, rgb(0x4E7A44))
                p.rect(x + s * 2 - 3 + bend, y - tall, 1, 4, if (s % 2 == 0) rgb(0x6A4A2E) else rgb(0x5E8A4E))
            }
        }
    }

    override fun front(p: ScenePen, t: Float) {
        for (i in 0 until flySeeds.size / 3) {
            val s = flySeeds[i * 3 + 2]
            val x = (flySeeds[i * 3] * w + sin(t * .5f + s * 6) * 14 + sin(t * 1.3f + s * 11) * 4).toInt()
            val y = (hz + 8 + flySeeds[i * 3 + 1] * h * .6f + cos(t * .7f + s * 5) * 6).toInt()
            val b = sin(t * 2.2f + s * 20) * .5f + .5f
            if (b < .3f) continue
            p.rect(x - 1, y, 3, 1, argb((b * 0x70).toInt(), 0xD8FF6A)); p.rect(x, y - 1, 1, 3, argb((b * 0x70).toInt(), 0xD8FF6A))
            p.rect(x - 2, y - 2, 5, 5, argb((b * 0x22).toInt(), 0xD8FF6A)); p.dot(x, y, argb((b * 255).toInt(), 0xF8FFB0))
        }
    }
}

/** Region 5: frozen peaks at dusk under an aurora, snowy pines, ice, wind and falling snow. */
internal class PeaksScene : ScenePainter(89) {
    private var aurora: Bitmap? = null
    private val flakeSeeds = seeds(150)
    private val gustSeeds = seeds(18)
    private var iceX = IntArray(0)
    private var iceY = IntArray(0)

    override fun sky(a: IntArray) = gradient(a, 0, hz + 2, intArrayOf(rgb(0x0F1A3C), rgb(0x203668), rgb(0x4874A2), rgb(0xA2C3DB), rgb(0xE0EDF4)))

    override fun skyShapes(p: ScenePen) {
        for (i in 0 until w * hz / 70) p.dot(random.nextInt(w), random.nextInt(max(1, (hz * .4f).toInt())), argb(0x50 + random.nextInt(0x90), 0xFFFFFF))
    }

    override fun ground(a: IntArray) {
        gradient(a, hz, h, intArrayOf(rgb(0xEEF5FA), rgb(0xDDE9F3), rgb(0xC6D7E7)), 9)
        val far = IntArray(w) { hz - 16 - (40 * peaks(it, .011f, .4f)).toInt() }
        for (x in 0 until w) for (y in max(0, far[x]) until hz + 1) {
            val lit = far[min(w - 1, x + 3)] < far[max(0, x - 3)]
            a[y * w + x] = mix(if (lit) rgb(0xC6D7E8) else rgb(0x9AB0CD), rgb(0xD8E6F0), (y - far[x]).toFloat() / max(1, hz - far[x]) * .5f)
        }
        val near = IntArray(w) { hz - 3 - (38 * peaks(it, .021f, 1.7f) * (.3f + .7f * min(1f, abs(it - cx) / (w * .5f)))).toInt() }
        for (x in 0 until w) {
            val lit = near[min(w - 1, x + 3)] < near[max(0, x - 3)]
            val cap = 3 + (noise(x * .15f, 0f, 1) * 5).toInt() + (hz - near[x]) / 4
            for (y in max(0, near[x]) until hz + 2) {
                val snow = near[x] < hz - 6 && (y < near[x] + cap || y < near[x] + cap * 2 && hash(x, y, 2) < .14f)
                a[y * w + x] = if (snow) { if (lit) rgb(0xF4F8FC) else rgb(0xC5D5E8) } else if (lit) rgb(0x7385A6) else rgb(0x4D5D7E)
            }
        }
        val ice = ArrayList<Int>()
        for (y in hz + 2 until h) for (x in 0 until w) {
            val i = y * w + x
            val f = field(x, y)
            val v = noise(x * .018f, y * .2f, 3)
            when {
                f > .95f && v in .56f..0.585f -> a[i] = rgb(0xC9D9E9)
                f > .95f && v in .585f..0.6f -> a[i] = rgb(0xFFFFFF)
                f < .9f && hash(x, y, 4) < .025f -> { a[i] = rgb(0xB6C9DC); put(a, x + 2, y + 1, rgb(0xB6C9DC)) }
                f < .9f -> a[i] = mix(a[i], rgb(0xD3E1EE), ((.9f - f) * 1.2f).coerceIn(0f, .4f))
            }
        }
        for (k in 0 until 30) {
            val x = random.nextInt(w); val y = hz + 8 + random.nextInt(max(1, h - hz - 10))
            if (!outside(x, y, .15f) || ice.size >= 16) continue
            val rx = 4 + (y - hz) * 7 / max(1, h - hz); val ry = max(1, rx / 3)
            for (dy in -ry..ry) for (dx in -rx..rx) if ((dx * dx).toFloat() / (rx * rx) + (dy * dy).toFloat() / (ry * ry) <= 1)
                put(a, x + dx, y + dy, if (dy == -ry / 2 && dx < 0) rgb(0xEAF8FC) else rgb(0xBFE2F0))
            ice.add(x); ice.add(y - ry)
        }
        iceX = IntArray(ice.size / 2) { ice[it * 2] }; iceY = IntArray(ice.size / 2) { ice[it * 2 + 1] }
    }

    override fun land(p: ScenePen) {
        for (i in 0 until w / 5) {
            val x = random.nextInt(w); val foot = hz + 2 + random.nextInt(9)
            if (outside(x, foot)) pine(p, x, foot, 9 + random.nextInt(8), rgb(0x2A4A56), rgb(0x1C3640), rgb(0xE8F2F8))
        }
        for (i in 0 until 30) {
            val x = random.nextInt(w); val y = hz + 6 + random.nextInt(h - hz - 6)
            if (!outside(x, y, .08f)) continue
            val s = 2 + (y - hz) * 5 / max(1, h - hz); val ry = max(1, s / 2)
            p.oval(x, y - ry, s, ry, rgb(0x56627C)); p.oval(x - 1, y - ry - 1, max(1, s - 1), max(1, ry - 1), rgb(0x6E7C98))
            p.rect(x - s + 1, y - ry * 2, s * 2 - 2, 1, rgb(0xF4F8FC))
        }
    }

    override fun sprites(p: ScenePen) {
        val ah = max(8, (hz * .5f).toInt())
        val px = IntArray(w * ah)
        for (x in 0 until w) {
            val inten = (.55f + .45f * sin(x * .045f) * sin(x * .017f + 1.3f)) * (.6f + .4f * hash(x / 2, 0, 9))
            for (y in 0 until ah) {
                val k = y.toFloat() / ah
                px[y * w + x] = if (k > .35f) argb((inten * k * k * (if (y >= ah - 2) 230 else 170)).toInt(), 0x7CF2B0)
                    else argb((inten * (1 - k / .35f) * 70).toInt(), 0xE58AD6)
            }
        }
        aurora = Bitmap.createBitmap(px, w, ah, Bitmap.Config.ARGB_8888)
    }

    override fun skyMotion(p: ScenePen, t: Float) {
        aurora?.let { b ->
            val alpha = (150 + 60 * sin(t * .4f)).toInt(); val top = (hz * .1f).toInt()
            var x = 0
            while (x < w) {
                val dy = (sin(x * .03f + t * .5f) * 5 + sin(x * .011f - t * .3f) * 4).toInt()
                p.slice(b, x, min(2, w - x), x, top + dy, alpha); x += 2
            }
        }
        if (hz > 40) {
            val ex = (w * .3f + cos(t * .3f) * w * .12f).toInt(); val ey = (hz * .42f + sin(t * .3f) * 5).toInt()
            val col = rgb(0x2A3044)
            p.rect(ex - 2, ey, 5, 1, col); p.dot(ex - 3, ey - 1, col); p.dot(ex + 3, ey - 1, col); p.dot(ex, ey + 1, col)
        }
    }

    override fun landMotion(p: ScenePen, t: Float) {
        for (i in iceX.indices) {
            val phase = (t * .45f + i * .41f) % 1f
            if (phase < .12f) { val s = if (phase < .06f) 1 else 2; p.rect(iceX[i], iceY[i] - s, 1, s * 2 + 1, rgb(0xFFFFFF)); p.rect(iceX[i] - s, iceY[i], s * 2 + 1, 1, rgb(0xFFFFFF)) }
        }
        for (i in 0 until gustSeeds.size / 3) {
            if ((t * .2f + gustSeeds[i * 3 + 2]) % 1f > .5f) continue
            val x = wrap(gustSeeds[i * 3], w, 42f + gustSeeds[i * 3 + 2] * 20, t, 20)
            val y = hz + 6 + (gustSeeds[i * 3 + 1] * (h - hz) * .8f).toInt()
            for (k in 0 until 8) p.dot(x - k * 2, y + sin(k * .8f + t * 3).toInt(), argb(0xB0 - k * 0x14, 0xFFFFFF))
        }
    }

    override fun front(p: ScenePen, t: Float) {
        for (i in 0 until flakeSeeds.size / 3) {
            val s = flakeSeeds[i * 3 + 2]
            val y = wrap(flakeSeeds[i * 3 + 1], h, 7f + s * 10f, t, 2)
            val x = wrap(flakeSeeds[i * 3], w, 4f + s * 3f, t, 2) + (sin(t * 1.1f + s * 9) * 2).toInt()
            if (i % 4 == 0) p.rect(x, y, 2, 2, argb(0xF0, 0xFFFFFF)) else p.dot(x, y, argb(0xC8, 0xFFFFFF))
        }
    }
}
