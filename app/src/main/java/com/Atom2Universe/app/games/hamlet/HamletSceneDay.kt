package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Region 0: hedged meadows under a summer sky, a turning windmill, butterflies over the grass. */
internal class MeadowScene : ScenePainter(11) {
    private var clouds: Array<Bitmap> = emptyArray()
    private val cloudSeeds = seeds(15)
    private val flySeeds = seeds(9)
    private val moteSeeds = seeds(24)
    private var tuftX = IntArray(0)
    private var tuftY = IntArray(0)
    private var millX = 0
    private var millY = 0
    private val wings = intArrayOf(rgb(0xFFFFFF), rgb(0xFFDD55), rgb(0xF59A4A))

    override fun sky(a: IntArray) = gradient(a, 0, hz + 2, intArrayOf(rgb(0x4A86CF), rgb(0x76AEE3), rgb(0xB4DCEF), rgb(0xE6F3EA)))

    override fun skyShapes(p: ScenePen) {
        val sx = (w * .66f).toInt(); val sy = (hz * .26f).toInt()
        p.disc(sx, sy, 15, argb(0x22, 0xFFF6D0)); p.disc(sx, sy, 11, argb(0x3A, 0xFFF6D0))
        p.disc(sx, sy, 7, rgb(0xFFF0BA)); p.disc(sx - 1, sy - 1, 5, rgb(0xFFFBE8))
    }

    override fun ground(a: IntArray) {
        gradient(a, hz, h, intArrayOf(rgb(0x88BF60), rgb(0x70AB4F), rgb(0x568F3F)), 9)
        val worn = rgb(0x9CC068); val dirt = rgb(0xC9A86E); val dirtDark = rgb(0xAE8E5A)
        val blade = rgb(0x9BD073); val deep = rgb(0x4A8237)
        val flowers = intArrayOf(rgb(0xFFFFFF), rgb(0xFFE36A), rgb(0xF4A6C8), rgb(0xA7C4FF))
        for (y in hz until h) for (x in 0 until w) {
            val depth = (y - hz).toFloat() / (h - hz)
            val f = field(x, y)
            val patch = noise(x * .045f, y * .11f) + ((1 - f) * .4f).coerceIn(0f, .3f)
            // The gate path runs from the fence to the field.
            val path = y < hz + 16 && abs(x - cx) < 2 + (y - hz) * .55f
            when {
                path || patch > .98f -> a[y * w + x] = if (hash(x, y, 1) < .18f) dirtDark else dirt
                patch > .86f -> a[y * w + x] = worn
                hash(x, y, 2) < .045f + depth * .05f -> { a[y * w + x] = blade; if (depth > .45f) put(a, x, y - 1, blade) }
                hash(x, y, 3) < .04f -> a[y * w + x] = deep
                f > 1.05f && hash(x, y, 4) < .014f -> a[y * w + x] = flowers[(hash(x, y, 5) * 4).toInt()]
            }
        }
    }

    override fun land(p: ScenePen) {
        val farTop = IntArray(w) { hills(it, hz - 12, 7f, .018f, 1.3f) }
        for (x in 0 until w) p.rect(x, farTop[x], 1, hz + 2 - farTop[x], rgb(0x9DC4AE))
        for (x in 0 until w) if (hash(x, 9, 6) < .35f) p.dot(x, farTop[x] + 3 + (hash(x, 3, 6) * 4).toInt(), rgb(0x8DB79E))
        // A hilltop village far away: cottages and a steeple.
        val vx = (w * .14f).toInt()
        for (k in 0 until 4) cottage(p, vx + k * 7 + k % 2, farTop[min(w - 1, vx + k * 7 + 2)] + 4)
        val tx = vx + 30
        if (tx < w) {
            val foot = farTop[tx] + 4
            p.rect(tx, foot - 11, 3, 11, rgb(0xE9DFC8)); p.rect(tx + 2, foot - 11, 1, 11, rgb(0xCDBFA4))
            p.rect(tx, foot - 13, 3, 2, rgb(0x8E5A4A)); p.dot(tx + 1, foot - 15, rgb(0x8E5A4A)); p.dot(tx + 1, foot - 14, rgb(0x8E5A4A))
            p.dot(tx + 1, foot - 9, rgb(0x5E6470))
        }
        val nearTop = IntArray(w) { hills(it, hz - 3, 5f, .031f, 4.1f) }
        for (x in 0 until w) { p.rect(x, nearTop[x], 1, hz + 3 - nearTop[x], rgb(0x7BB05D)); p.dot(x, nearTop[x], rgb(0x96C774)) }
        // Hedgerows along the crest.
        for (x in 0 until w step 3) if (hash(x, 1, 5) < .72f) {
            p.disc(x, nearTop[x] + 2, 2, rgb(0x4C8244)); p.dot(x - 1, nearTop[x] + 1, rgb(0x66A157))
        }
        millX = if (w > 220) (w * .86f).toInt() else (w * .8f).toInt()
        val millFoot = nearTop[millX] + 3
        for (r in 0 until 19) {
            val half = 2 + r / 6; val y = millFoot - 19 + r
            p.rect(millX - half, y, half * 2 + 1, 1, rgb(0xEFE6D2)); p.rect(millX + 1, y, half, 1, rgb(0xD2C4A8))
        }
        p.rect(millX - 1, millFoot - 4, 2, 4, rgb(0x6E4A30)); p.dot(millX, millFoot - 12, rgb(0x4E5A66))
        p.rect(millX - 4, millFoot - 22, 9, 3, rgb(0x8A5A3A)); p.rect(millX - 3, millFoot - 23, 7, 1, rgb(0xA8714A))
        millY = millFoot - 21
        // Fence with a gate in the middle; a magpie keeps watch on one post.
        val fy = hz + 6
        for (x in 0 until w) {
            if (abs(x - cx) < 11) continue
            if (x % 6 == 0) { p.rect(x, fy - 6, 1, 7, rgb(0x6E4C2E)); p.dot(x, fy - 6, rgb(0x9B7448)) }
            else { p.dot(x, fy - 4, rgb(0xA27A4C)); p.dot(x, fy - 2, rgb(0x8A6440)) }
        }
        val post = (cx + 11 + 6 * 4) / 6 * 6
        if (post < w - 3) {
            p.rect(post - 1, fy - 9, 3, 3, rgb(0x1E2228)); p.dot(post, fy - 8, rgb(0xF2F2EE)); p.dot(post - 2, fy - 8, rgb(0x2C4A7A))
            p.dot(post + 2, fy - 10, rgb(0x1E2228)); p.dot(post + 1, fy - 8, rgb(0x1E2228))
        }
        haystack(p, (w * .07f).toInt(), hz + 11)
        haystack(p, (w * .93f).toInt(), hz + 10)
        if (w > 150) {
            // A great oak frames the left edge.
            p.rect(2, hz - 26, 6, 40, rgb(0x5A3E2A)); p.rect(2, hz - 26, 2, 40, rgb(0x76563A))
            p.rect(0, hz + 12, 11, 2, rgb(0x4A3424))
            for ((dx, dy, r) in arrayOf(Triple(4, -34, 17), Triple(18, -30, 11), Triple(-6, -22, 12), Triple(12, -44, 10))) {
                p.disc(dx, hz + dy, r, rgb(0x3A6B35)); p.disc(dx - 1, hz + dy - 1, r - 2, rgb(0x4E8A3F))
                p.disc(dx - r / 3, hz + dy - r / 3, r / 3, rgb(0x6BAA4E))
            }
        }
        val tufts = ArrayList<Int>()
        for (i in 0 until 60) {
            val x = random.nextInt(w); val y = hz + 4 + random.nextInt(h - hz - 4)
            if (outside(x, y, .08f) && tufts.size < 48) { tufts.add(x); tufts.add(y) }
        }
        tuftX = IntArray(tufts.size / 2) { tufts[it * 2] }; tuftY = IntArray(tufts.size / 2) { tufts[it * 2 + 1] }
    }

    private fun cottage(p: ScenePen, x: Int, foot: Int) {
        p.rect(x, foot - 3, 5, 3, rgb(0xE9DFC8)); p.rect(x + 3, foot - 3, 2, 3, rgb(0xCDBFA4))
        p.rect(x - 1, foot - 4, 7, 1, rgb(0xB9634A)); p.rect(x, foot - 5, 5, 1, rgb(0xB9634A)); p.rect(x + 1, foot - 6, 3, 1, rgb(0xC97659))
        p.dot(x + 1, foot - 2, rgb(0x6A6A70))
    }

    private fun haystack(p: ScenePen, x: Int, foot: Int) {
        p.oval(x, foot, 8, 2, rgb(0x5E9343))
        p.oval(x, foot - 4, 6, 4, rgb(0xC0913A)); p.oval(x - 1, foot - 5, 5, 3, rgb(0xE2B852))
        for (k in -3..3 step 2) p.dot(x + k, foot - 4 + abs(k) / 2, rgb(0xF4D47E))
        p.dot(x, foot - 9, rgb(0xC0913A))
    }

    override fun sprites(p: ScenePen) {
        clouds = Array(5) { cloud(p, 34 + random.nextInt(24), 12 + random.nextInt(6), rgb(0xFFFFFF), rgb(0xEEF5FA), rgb(0xC6DAEA)) }
    }

    override fun skyMotion(p: ScenePen, t: Float) {
        for (i in clouds.indices) {
            val b = clouds[i]
            p.bitmap(b, wrap(cloudSeeds[i * 3], w, 1.1f + cloudSeeds[i * 3 + 2] * 2f, t, b.width), (hz * (.06f + cloudSeeds[i * 3 + 1] * .42f)).toInt())
        }
        // A small flock crosses now and then.
        val cycle = (t + 8f) % 30f / 30f
        for (k in 0..2) {
            val x = (-14 + cycle * (w + 28)).toInt() - k * 6; val y = (hz * .4f).toInt() + k * 3 - k / 2 * 5
            bird(p, x, y + (sin(t * 2f + k) * 1.5f).toInt(), ((t * 6 + k).toInt() and 1) == 0)
        }
    }

    private fun bird(p: ScenePen, x: Int, y: Int, up: Boolean) {
        val col = rgb(0x36465A)
        p.dot(x, y, col); p.dot(x - 1, y - 1, col); p.dot(x + 1, y - 1, col)
        p.dot(x - 2, if (up) y - 2 else y, col); p.dot(x + 2, if (up) y - 2 else y, col)
    }

    override fun landMotion(p: ScenePen, t: Float) {
        for (k in 0..3) {
            val a = t * .9f + k * 1.5707964f
            val ca = cos(a); val sa = sin(a)
            for (s in 1..12) {
                val x = millX + (ca * s).toInt(); val y = millY + (sa * s).toInt()
                p.dot(x, y, rgb(0x6E4E36))
                if (s >= 4) p.dot(x + (-sa * 1.6f).toInt(), y + (ca * 1.6f).toInt(), rgb(0xF4EEDF))
            }
        }
        p.rect(millX - 1, millY - 1, 2, 2, rgb(0x4A3424))
        for (i in tuftX.indices) {
            val x = tuftX[i]; val y = tuftY[i]
            val tall = 2 + (y - hz) * 4 / max(1, h - hz)
            val sway = (sin(t * 2.2f + x * .13f) * 1.3f).toInt()
            for (b in -1..1) {
                p.rect(x + b * 2, y - tall, 1, tall, rgb(0x5C9A44))
                p.dot(x + b * 2 + sway, y - tall - 1, rgb(0x8CC866))
            }
        }
    }

    override fun front(p: ScenePen, t: Float) {
        for (i in 0..2) {
            val x = cx + (sin(t * .31f + flySeeds[i * 3] * 6f) * reach * .9f).toInt()
            val y = hz + 10 + ((h * .42f) * (.5f + .5f * sin(t * .47f + flySeeds[i * 3 + 1] * 7f))).toInt()
            val open = ((t * 9 + flySeeds[i * 3 + 2] * 10).toInt() and 1) == 0
            val col = wings[i]
            p.dot(x, y, rgb(0x3A2E2A))
            if (open) { p.rect(x - 2, y - 1, 2, 2, col); p.rect(x + 1, y - 1, 2, 2, col) } else { p.dot(x - 1, y - 2, col); p.dot(x + 1, y - 2, col) }
        }
        for (i in 0 until moteSeeds.size / 2) {
            val x = wrap(moteSeeds[i * 2], w, 1.5f, t) + (sin(t + i) * 2).toInt()
            val y = h - wrap(moteSeeds[i * 2 + 1], h - hz, 3f, t) - 4
            p.dot(x, y, argb(0x99, 0xFFF6C8))
        }
    }
}

/** Region 1: a deep wood in mist, light falling through the canopy, glowing mushrooms and an owl. */
internal class ForestScene : ScenePainter(23) {
    private var beams: Bitmap? = null
    private var mistBack: Bitmap? = null
    private var mistFront: Bitmap? = null
    private val vineSeeds = seeds(21)
    private val sporeSeeds = seeds(48)
    private var shroomX = IntArray(0)
    private var shroomY = IntArray(0)
    private var owlX = -1
    private var owlY = 0

    private fun canopy(x: Int) = (hz * .17f + 5 * sin(x * .09f + 1f) + 3 * sin(x * .23f)).toInt()

    override fun sky(a: IntArray) = gradient(a, 0, hz + 6, intArrayOf(rgb(0x284638), rgb(0x4C7A63), rgb(0x8ABDA1), rgb(0xCBE6C8)))

    override fun ground(a: IntArray) {
        gradient(a, hz, h, intArrayOf(rgb(0x5E8C56), rgb(0x4B7846), rgb(0x345C39)), 9)
        val moss = rgb(0x6F9E5C); val litter = intArrayOf(rgb(0xA87A3E), rgb(0x8A5E32), rgb(0xC09050))
        for (y in hz until h) for (x in 0 until w) {
            val f = field(x, y)
            val n = noise(x * .08f, y * .18f)
            val scatter = if (f > 1) .05f else .02f
            when {
                n + (1 - f).coerceIn(0f, .5f) * .7f > .86f -> a[y * w + x] = if (hash(x, y, 1) < .1f) litter[0] else moss
                hash(x, y, 2) < scatter -> a[y * w + x] = litter[(hash(x, y, 3) * 3).toInt()]
                hash(x, y, 4) < .03f -> a[y * w + x] = rgb(0x2E5233)
            }
        }
    }

    override fun land(p: ScenePen) {
        // Three depths of trunks fading into the mist.
        var x = random.nextInt(6)
        while (x < w) { val tw = 2 + random.nextInt(3); p.rect(x, 0, tw, hz + 2, rgb(0x8BB79E)); x += 7 + random.nextInt(9) }
        for (c in 0 until w) p.rect(c, 0, 1, (hz * .22f + 3 * sin(c * .21f)).toInt(), rgb(0x6F9C84))
        x = random.nextInt(10)
        while (x < w) {
            val tw = 4 + random.nextInt(4)
            p.rect(x, 0, tw, hz + 5, rgb(0x5A876C)); p.rect(x, 0, 1, hz + 5, rgb(0x6C9A7E))
            p.rect(x - 1, hz + 3, tw + 2, 2, rgb(0x5A876C)); p.rect(x - 2, hz + 5, tw + 4, 1, rgb(0x52806A))
            if (random.nextBoolean()) p.line(x + tw, hz / 2 + random.nextInt(8), x + tw + 5, hz / 2 - 4, rgb(0x5A876C))
            x += 15 + random.nextInt(16)
        }
        for (c in 0 until w) {
            val d = canopy(c)
            p.rect(c, 0, 1, d, rgb(0x223F30))
            if (c % 4 == 0) { p.disc(c, d, 2 + (hash(c, 0, 2) * 2).toInt(), rgb(0x223F30)); p.dot(c - 1, d - 1, rgb(0x3D6B49)) }
            if (hash(c, 1, 3) < .25f) p.dot(c, (hash(c, 2, 3) * d).toInt(), rgb(0x34583F))
        }
        // Giant trunks at both edges; the right one hides an owl.
        for (side in 0..1) {
            val left = if (side == 0) -6 else w - 16
            for (y in 0 until hz + 18) {
                val flare = if (y > hz + 4) (y - hz - 4) * 2 / 3 else 0
                p.rect(left - flare, y, 22 + flare * 2, 1, rgb(0x3A5644))
            }
            p.rect(left + (if (side == 0) 19 else 1), 0, 2, hz + 6, rgb(0x52735C))
            for (k in 0 until 6) p.rect(left + 3 + k * 3, (hash(k, side, 4) * hz * .6f).toInt(), 1, 8 + (hash(k, side, 5) * 14).toInt(), rgb(0x2C4436))
            for (k in 0..2) p.line(left + 4 + k * 6, hz + 12, left + 4 + k * 6 + (if (side == 0) 1 else -1) * (8 + k * 3), hz + 18, rgb(0x3A5644))
        }
        if (w > 120) {
            owlX = w - 8; owlY = (hz * .55f).toInt()
            p.oval(owlX, owlY, 2, 3, rgb(0x16241C))
        }
        // A mossy fallen log on the left, ferns and glowing mushrooms where nobody stands.
        val lx = max(14, cx - reach / 2 - 22)
        p.rect(lx, hz + 5, 18, 4, rgb(0x6A4B35)); p.rect(lx, hz + 5, 18, 1, rgb(0x86613F))
        for (k in 0 until 18 step 2) p.dot(lx + k, hz + 4, rgb(0x5E8F4E))
        p.oval(lx + 18, hz + 7, 1, 2, rgb(0xC9A57A)); p.dot(lx + 18, hz + 7, rgb(0x8E6C48))
        for (i in 0 until 26) {
            val fx = random.nextInt(w); val fy = hz + 3 + random.nextInt(h - hz - 3)
            if (outside(fx, fy, .05f)) fern(p, fx, fy, 4 + (fy - hz) * 6 / max(1, h - hz))
        }
        val spots = ArrayList<Int>()
        for (i in 0 until 40) {
            val mx = random.nextInt(w); val my = hz + 4 + random.nextInt(h - hz - 4)
            if (outside(mx, my, .02f) && spots.size < 14) { spots.add(mx); spots.add(my); shrooms(p, mx, my) }
        }
        val l = lx + 4
        spots.add(l); spots.add(hz + 5); shrooms(p, l, hz + 5)
        shroomX = IntArray(spots.size / 2) { spots[it * 2] }; shroomY = IntArray(spots.size / 2) { spots[it * 2 + 1] }
    }

    private fun fern(p: ScenePen, x: Int, foot: Int, size: Int) {
        for (k in 0..4) {
            val dir = -1.1f + k * .55f
            val len = size - abs(k - 2)
            for (i in 1..len) {
                val px = x + (sin(dir) * i * 1.1f).toInt(); val py = foot - (cos(dir) * i * .9f).toInt() + i * i / 12
                p.dot(px, py, if (i % 2 == 0) rgb(0x5DA05A) else rgb(0x3F7A44))
            }
        }
    }

    private fun shrooms(p: ScenePen, x: Int, foot: Int) {
        for (k in 0..2) {
            val mx = x + k * 3 - 3; val tall = 2 + (k + 1) % 3
            p.rect(mx, foot - tall, 1, tall, rgb(0xE8E4D0))
            p.rect(mx - 1, foot - tall - 1, 3, 1, rgb(0x6FD6D8)); p.dot(mx, foot - tall - 2, rgb(0x9BF0EC))
        }
    }

    override fun sprites(p: ScenePen) {
        val bh = hz + h / 3
        beams = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888).also { b ->
            p.c = android.graphics.Canvas(b)
            for (k in 0..2) {
                val bx = (w * (.35f + k * .24f)).toInt(); val bw = 5 + k * 2
                for (y in 0 until bh) p.rect(bx - (y * .45f).toInt(), y, bw, 1, argb((0x46 * (1 - y.toFloat() / bh)).toInt(), 0xFFF4D0))
            }
        }
        mistBack = mist(p, 140, 12, argb(0x55, 0xE4F2E8))
        mistFront = mist(p, 180, 16, argb(0x38, 0xEAF6EE))
    }

    override fun landMotion(p: ScenePen, t: Float) {
        beams?.let { p.bitmap(it, 0, 0, (150 + 70 * sin(t * .5f)).toInt()) }
        for (i in 0 until vineSeeds.size / 3) {
            val x0 = (vineSeeds[i * 3] * w).toInt(); val top = canopy(x0); val len = 10 + (vineSeeds[i * 3 + 1] * 16).toInt()
            for (k in 0 until len) {
                val x = x0 + (sin(t * .8f + vineSeeds[i * 3 + 2] * 6 + k * .12f) * k * .07f).toInt()
                p.dot(x, top + k, rgb(0x3F6E44)); if (k % 3 == 2) p.dot(x + 1, top + k, rgb(0x5E9A5A))
            }
        }
        for (i in shroomX.indices) {
            val glow = (0x30 + 0x24 * sin(t * 1.6f + i * 1.3f)).toInt()
            p.oval(shroomX[i], shroomY[i] - 3, 6, 3, argb(glow, 0x8FF0E8))
        }
        if (owlX >= 0) {
            val blink = (t + 1.3f) % 5f < .18f
            if (blink) p.rect(owlX - 1, owlY, 3, 1, rgb(0x0F1A14)) else { p.dot(owlX - 1, owlY, rgb(0xFFD84A)); p.dot(owlX + 1, owlY, rgb(0xFFD84A)) }
        }
        mistBack?.let { scroll(p, it, t * 3f, hz - 4, 200) }
    }

    override fun front(p: ScenePen, t: Float) {
        mistFront?.let { scroll(p, it, -t * 4.5f, (h * .66f).toInt(), 150) }
        for (i in 0 until sporeSeeds.size / 3) {
            val x = (sporeSeeds[i * 3] * w).toInt() + (sin(t * .9f + sporeSeeds[i * 3 + 2] * 9) * 3).toInt()
            val y = h - wrap(sporeSeeds[i * 3 + 1], h - canopy(0), 4f + sporeSeeds[i * 3 + 2] * 3, t)
            p.dot(x, y, argb(0xB0, 0xC8FFE8))
        }
    }
}

/** Region 2: an autumn quarry cut in terraces, a swinging crane load, amber glints and falling leaves. */
internal class QuarryScene : ScenePainter(37) {
    private var clouds: Array<Bitmap> = emptyArray()
    private val cloudSeeds = seeds(9)
    private val leafSeeds = seeds(54)
    private val dustSeeds = seeds(20)
    private var craneX = -1
    private var craneY = 0
    private var amberX = IntArray(0)
    private var amberY = IntArray(0)
    private val leaves = intArrayOf(rgb(0xE07A2E), rgb(0xC4462E), rgb(0xE8B23A))
    private companion object { const val BENCH = 11 }

    override fun sky(a: IntArray) = gradient(a, 0, hz + 2, intArrayOf(rgb(0x6A9CCB), rgb(0x9EC4DA), rgb(0xE7D7AE), rgb(0xF5C88E)))

    override fun skyShapes(p: ScenePen) {
        val sx = (w * .22f).toInt(); val sy = (hz * .58f).toInt()
        p.disc(sx, sy, 16, argb(0x26, 0xFFE9B0)); p.disc(sx, sy, 11, argb(0x48, 0xFFE9B0))
        p.disc(sx, sy, 6, rgb(0xFFF1C8))
    }

    private fun terrace(x: Int): Int {
        val d = (abs(x - cx) - w * .14f) / (w * .3f)
        return if (d <= 0) 0 else min(4, 1 + (d * 4).toInt())
    }
    private fun cliffTop(x: Int) = hz + 3 - terrace(x) * BENCH

    override fun ground(a: IntArray) {
        // Far autumn hills.
        for (x in 0 until w) {
            val top = hills(x, hz - 9, 6f, .022f, .7f)
            for (y in max(0, top) until hz + 2) a[y * w + x] = if (hash(x / 2, y / 2, 1) < .3f && y < hz - 1) rgb(0xC98F62) else rgb(0xC2A388)
        }
        gradient(a, hz, h, intArrayOf(rgb(0xD9BA84), rgb(0xC8A26C), rgb(0xAC8655)), 9)
        for (y in hz until h) for (x in 0 until w) {
            val depth = (y - hz).toFloat() / (h - hz)
            val f = field(x, y)
            val dust = noise(x * .045f, y * .11f) + (1 - f).coerceIn(0f, .5f) * .4f
            when {
                dust > .95f -> a[y * w + x] = rgb(0xE0C592)
                hash(x, y, 2) < .035f -> { a[y * w + x] = rgb(0xE9D3A2); if (depth > .5f) put(a, x + 1, y, rgb(0xE9D3A2)) }
                hash(x, y, 3) < .035f -> { a[y * w + x] = rgb(0x9A7A4E); if (depth > .5f) put(a, x + 1, y, rgb(0x9A7A4E)) }
                f > 1.05f && hash(x, y, 4) < .02f -> a[y * w + x] = if (hash(x, y, 5) < .5f) rgb(0xD8662E) else rgb(0xE8A23A)
            }
        }
        // Quarry walls cut in benches: a lit ledge on each step, layered rock, cracks, shadowed sides.
        for (x in 0 until w) {
            val level = terrace(x)
            if (level == 0) continue
            val top = cliffTop(x)
            val inner = if (x < cx) terrace(x + 1) < level || terrace(x + 2) < level else terrace(x - 1) < level || terrace(x - 2) < level
            for (y in max(0, top) until hz + 4) {
                val above = hz + 3 - y
                val bench = above / BENCH; val inBench = BENCH - 1 - above % BENCH
                val strata = noise(x * .025f, y * .45f, 6)
                a[y * w + x] = when {
                    inBench == 0 -> rgb(0xF2D9A4)
                    inBench == 1 -> rgb(0xE4C48C)
                    inner -> if (inBench == BENCH - 1) rgb(0x8E6A40) else rgb(0xAE8452)
                    inBench == BENCH - 1 -> rgb(0xA67C4C)
                    hash(x, bench, 8) < .07f && inBench > 2 -> rgb(0xA27848)
                    strata > .64f -> rgb(0xC99C64)
                    strata < .3f -> rgb(0xE0BC86)
                    hash(x, y, 6) < .05f -> rgb(0xBC9160)
                    else -> rgb(0xD6AE76)
                }
            }
        }
    }

    override fun land(p: ScenePen) {
        // Autumn trees and shrubs on the benches.
        val dark = intArrayOf(rgb(0xA8521E), rgb(0x8E2E22), rgb(0xB8822A))
        for (i in 0 until w / 9) {
            val x = random.nextInt(w); val level = terrace(x)
            if (level == 0 || terrace(x - 4) != level || terrace(x + 4) != level) continue
            val k = random.nextInt(3)
            if (level >= 2 && random.nextInt(3) > 0) bushyTree(p, x, cliffTop(x) + 1, 4 + random.nextInt(3), leaves[k], mix(leaves[k], rgb(0xFFF0C0), .35f), dark[k])
            else { p.oval(x, cliffTop(x), 3, 1, dark[k]); p.rect(x - 2, cliffTop(x) - 1, 4, 1, leaves[k]) }
        }
        // A mine gallery in the left wall.
        val mine = (cx - w * .14f - w * .3f * .45f).toInt()
        if (mine > 6 && terrace(mine) >= 2) {
            p.rect(mine - 4, hz - 6, 9, 10, rgb(0x2A2018)); p.rect(mine - 3, hz - 5, 7, 9, rgb(0x1A140F))
            p.rect(mine - 5, hz - 7, 11, 2, rgb(0x7A5634)); p.rect(mine - 5, hz - 6, 2, 10, rgb(0x6E4A2E)); p.rect(mine + 4, hz - 6, 2, 10, rgb(0x6E4A2E))
            p.dot(mine - 1, hz - 2, rgb(0xFFB04A))
        }
        // A wooden crane on the right benches.
        val crane = (cx + w * .14f + w * .3f * .55f).toInt()
        if (crane < w - 6 && terrace(crane) > 0) {
            val foot = cliffTop(crane)
            for (k in 0..1) { p.line(crane - 4 + k, foot, crane + k, foot - 20, rgb(0x6E4A2E)); p.line(crane + 4 + k, foot, crane + k, foot - 20, rgb(0x6E4A2E)) }
            p.line(crane - 3, foot - 7, crane + 3, foot - 7, rgb(0x5A3C24)); p.line(crane - 2, foot - 13, crane + 2, foot - 13, rgb(0x5A3C24))
            for (k in 0..1) p.line(crane + 2, foot - 20 + k, crane - 20, foot - 23 + k, rgb(0x7E5636))
            p.line(crane, foot - 15, crane - 11, foot - 22, rgb(0x5A3C24))
            p.rect(crane - 1, foot - 22, 3, 3, rgb(0x4A3424))
            craneX = crane - 20; craneY = foot - 22
            p.rect(craneX - 1, craneY, 3, 2, rgb(0x3A2A1E))
        }
        // Rails and an ore cart on the left.
        val railEnd = max(0, cx - (w * .12f).toInt())
        p.rect(0, hz + 8, railEnd, 1, rgb(0x5A4636)); p.rect(0, hz + 10, railEnd, 1, rgb(0x5A4636))
        for (x in 0 until railEnd step 3) p.rect(x, hz + 8, 1, 3, rgb(0x7A5A3E))
        val cart = (w * .12f).toInt()
        if (cart + 6 < railEnd) {
            p.rect(cart - 5, hz + 3, 11, 5, rgb(0x6F5A48)); p.rect(cart - 6, hz + 3, 13, 1, rgb(0x8A7360))
            p.rect(cart - 3, hz + 2, 7, 1, rgb(0xF0A030)); p.dot(cart, hz + 1, rgb(0xFFD070))
            p.dot(cart - 3, hz + 8, rgb(0x2E2620)); p.dot(cart + 3, hz + 8, rgb(0x2E2620))
        }
        // Cut blocks, amber crystals and leaf piles, away from the fighters.
        val gems = ArrayList<Int>()
        for (i in 0 until 46) {
            val x = random.nextInt(w); val y = hz + 5 + random.nextInt(h - hz - 5)
            if (!outside(x, y, .04f)) continue
            val s = 2 + (y - hz) * 5 / max(1, h - hz)
            when (i % 3) {
                0 -> block(p, x, y, s)
                1 -> if (gems.size < 12) { amber(p, x, y, s + 2); gems.add(x); gems.add(y - s - 2) }
                else -> for (k in 0 until 6) p.dot(x + random.nextInt(7) - 3, y - random.nextInt(2), leaves[random.nextInt(3)])
            }
        }
        amberX = IntArray(gems.size / 2) { gems[it * 2] }; amberY = IntArray(gems.size / 2) { gems[it * 2 + 1] }
    }

    private fun block(p: ScenePen, x: Int, foot: Int, s: Int) {
        p.rect(x - s, foot - s, s * 2, s, rgb(0xCFA670)); p.rect(x + s, foot - s, max(1, s / 2), s, rgb(0xA97E4E))
        p.rect(x - s, foot - s - max(1, s / 2), s * 2 + max(1, s / 2), max(1, s / 2), rgb(0xEED6A2))
    }

    private fun amber(p: ScenePen, x: Int, foot: Int, s: Int) {
        for ((dx, tall) in arrayOf(-2 to s - 2, 0 to s, 2 to s - 1)) {
            p.rect(x + dx, foot - tall, 2, tall, rgb(0xE08A24)); p.rect(x + dx, foot - tall, 1, tall, rgb(0xFFC860))
            p.dot(x + dx, foot - tall - 1, rgb(0xFFE6A0))
        }
        p.rect(x - 3, foot, 7, 1, rgb(0x8E5A1E))
    }

    override fun sprites(p: ScenePen) {
        clouds = Array(3) { cloud(p, 36 + random.nextInt(20), 11 + random.nextInt(5), rgb(0xFFF6EA), rgb(0xF4DCC2), rgb(0xD9B49C)) }
    }

    override fun skyMotion(p: ScenePen, t: Float) {
        for (i in clouds.indices) {
            val b = clouds[i]
            p.bitmap(b, wrap(cloudSeeds[i * 3], w, .9f + cloudSeeds[i * 3 + 2] * 1.4f, t, b.width), (hz * (.08f + cloudSeeds[i * 3 + 1] * .35f)).toInt())
        }
    }

    override fun landMotion(p: ScenePen, t: Float) {
        if (craneX >= 0) {
            val swing = .28f * sin(t * 1.1f)
            val bx = craneX + (sin(swing) * 15).toInt(); val by = craneY + 2 + (cos(swing) * 15).toInt()
            p.line(craneX, craneY + 2, bx, by, rgb(0x4A3A2C))
            p.rect(bx - 4, by, 8, 5, rgb(0xCFA670)); p.rect(bx - 4, by, 8, 1, rgb(0xEED6A2)); p.rect(bx + 3, by + 1, 1, 4, rgb(0xA97E4E))
            p.line(bx - 3, by, bx, by - 2, rgb(0x4A3A2C)); p.line(bx + 3, by, bx, by - 2, rgb(0x4A3A2C))
        }
        for (i in amberX.indices) {
            val phase = (t * .55f + i * .37f) % 1f
            if (phase < .14f) {
                val s = if (phase < .07f) 1 else 2
                p.rect(amberX[i], amberY[i] - s, 1, s * 2 + 1, rgb(0xFFFBE8)); p.rect(amberX[i] - s, amberY[i], s * 2 + 1, 1, rgb(0xFFFBE8))
            }
        }
    }

    override fun front(p: ScenePen, t: Float) {
        for (i in 0 until leafSeeds.size / 3) {
            val s = leafSeeds[i * 3 + 2]
            val y = wrap(leafSeeds[i * 3 + 1], h, 6f + s * 6f, t, 4)
            val x = wrap(leafSeeds[i * 3], w, 3f, t, 4) + (sin(t * .9f + s * 9) * 6).toInt()
            val col = leaves[i % 3]
            when ((t * 3 + s * 7).toInt() % 3) {
                0 -> p.rect(x, y, 2, 1, col)
                1 -> p.dot(x, y, col)
                else -> p.rect(x, y, 1, 2, col)
            }
        }
        for (i in 0 until dustSeeds.size / 2) {
            p.dot(wrap(dustSeeds[i * 2], w, 2f, t), hz + 6 + (dustSeeds[i * 2 + 1] * (h - hz) * .7f).toInt() + (sin(t * .7f + i) * 3).toInt(), argb(0x80, 0xFFF0D0))
        }
    }
}
