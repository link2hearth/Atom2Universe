package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where everyone stands on the battle ground. The scene keeps this ground clear and the battle view
 * places fighters on it: two front rows near the centre, four staggered back rows behind, two vanguard
 * spots for summons and one spot for the pet. Farther rows stand higher and a little smaller.
 */
object HamletArena {
    /** Where the ground meets the sky, as a fraction of the height. */
    const val HORIZON = .36f
    private val BACK = floatArrayOf(.42f, .54f, .66f, .78f)
    private val FRONT = floatArrayOf(.50f, .70f)
    private val VANGUARD = floatArrayOf(.58f, .74f)
    private const val FAREST = .42f
    private const val DEPTH = .36f
    /** Height of an ordinary fighter; one art pixel is a 56th of it. */
    fun size(width: Int, height: Int) = minOf(width * .21f, height * .27f)
    fun pixel(width: Int, height: Int) = (size(width, height) / 56f).coerceAtLeast(1f)
    /** Broad screens push the camps apart instead of leaving them huddled in the middle. */
    private fun spread(width: Int, height: Int) = (width / 2f / (2.45f * size(width, height))).coerceIn(1f, 1.5f)
    /** Half the width covered by the camps, measured from the centre. */
    fun reach(width: Int, height: Int) = (1.85f * spread(width, height) + .45f) * size(width, height)
    private fun depth(foot: Float) = .9f + .16f * ((foot - FAREST) / DEPTH)
    private fun set(enemy: Boolean, foot: Float, column: Float, width: Int, height: Int, out: PointF): Float {
        val depth = depth(foot)
        val d = column * spread(width, height) * size(width, height) * depth
        out.set(width / 2f + if (enemy) d else -d, foot * height)
        return depth
    }
    /** Feet of a fighter in [out]; returns its depth scale. [vanguard] is the summon's place (0 or 1), or -1. */
    fun place(enemy: Boolean, slot: Int, vanguard: Int, width: Int, height: Int, out: PointF): Float = when {
        vanguard >= 0 -> set(enemy, VANGUARD[vanguard % 2], .36f, width, height, out)
        slot < 2 -> set(enemy, FRONT[slot], .78f, width, height, out)
        else -> {
            val row = (slot - 2) % 4
            set(enemy, BACK[row], 1.6f + if (row % 2 == 0) .22f else -.22f, width, height, out)
        }
    }
    /** The pet waits between the heroes' two lines. */
    fun pet(width: Int, height: Int, out: PointF) = set(false, .62f, 1.05f, width, height, out)
}

/**
 * Animated pixel-art grounds, one per region, painted in code at the fighters' own pixel size.
 * The still parts are painted once per size into two small bitmaps (sky, then land with transparent sky);
 * clouds, flames, water and particles move between and over them without allocating.
 */
class HamletScene(region: Int) {
    private val painter = when (Math.floorMod(region, 6)) {
        0 -> MeadowScene(); 1 -> ForestScene(); 2 -> QuarryScene()
        3 -> RuinsScene(); 4 -> MarshScene(); else -> PeaksScene()
    }
    private var sky: Bitmap? = null
    private var land: Bitmap? = null
    private var width = 0
    private var height = 0
    private var scale = 1f
    private val pen = ScenePen()
    private val paint = Paint().apply { isFilterBitmap = false }

    fun layout(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width == this.width && height == this.height) return
        this.width = width; this.height = height
        scale = HamletArena.pixel(width, height)
        val w = ceil(width / scale).toInt(); val h = ceil(height / scale).toInt()
        painter.frame(w, h, (HamletArena.reach(width, height) / scale).toInt())
        val pixels = IntArray(w * h)
        painter.sky(pixels)
        sky = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
            pen.c = Canvas(it); painter.skyShapes(pen)
        }
        pixels.fill(0)
        painter.ground(pixels)
        land = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
            pen.c = Canvas(it); painter.land(pen)
        }
        painter.sprites(pen)
    }

    /** Sky, land and everything that moves behind the fighters. */
    fun drawBack(c: Canvas, time: Float) {
        val sky = sky ?: return; val land = land ?: return
        c.save(); c.scale(scale, scale)
        pen.c = c
        c.drawBitmap(sky, 0f, 0f, paint)
        painter.skyMotion(pen, time)
        c.drawBitmap(land, 0f, 0f, paint)
        painter.landMotion(pen, time)
        c.restore()
    }

    /** Snow, embers, leaves, fireflies and mist that drift in front of the fighters. */
    fun drawFront(c: Canvas, time: Float) {
        if (land == null) return
        c.save(); c.scale(scale, scale)
        pen.c = c
        painter.front(pen, time)
        c.restore()
    }
}

/** Whole pixels on the scene grid: the canvas is either a scene bitmap or a view scaled to one art pixel. */
internal class ScenePen {
    lateinit var c: Canvas
    private val p = Paint().apply { isAntiAlias = false }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val src = Rect()
    private val dst = RectF()
    fun rect(x: Int, y: Int, w: Int, h: Int, col: Int) {
        if (w <= 0 || h <= 0) return
        p.color = col; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    fun dot(x: Int, y: Int, col: Int) = rect(x, y, 1, 1, col)
    fun oval(x: Int, y: Int, rx: Int, ry: Int, col: Int) {
        for (dy in -ry..ry) {
            val k = dy / (ry + .5f)
            val half = ((rx + .5f) * sqrt(1 - k * k)).toInt()
            rect(x - half, y + dy, half * 2 + 1, 1, col)
        }
    }
    fun disc(x: Int, y: Int, r: Int, col: Int) = oval(x, y, r, r, col)
    /** A one-pixel ring, eight-way symmetric. */
    fun ring(x: Int, y: Int, rx: Int, ry: Int, col: Int) {
        val steps = max(8, (rx + ry) * 3)
        for (i in 0 until steps) {
            val a = i * 6.2831855f / steps
            dot(x + (kotlin.math.cos(a) * rx).toInt(), y + (sin(a) * ry).toInt(), col)
        }
    }
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, col: Int) {
        val n = max(abs(x1 - x0), abs(y1 - y0))
        if (n == 0) { dot(x0, y0, col); return }
        for (i in 0..n) dot(x0 + floor((x1 - x0) * i / n.toFloat() + .5f).toInt(), y0 + floor((y1 - y0) * i / n.toFloat() + .5f).toInt(), col)
    }
    fun bitmap(b: Bitmap, x: Int, y: Int, alpha: Int = 255) {
        bitmapPaint.alpha = alpha.coerceIn(0, 255); c.drawBitmap(b, x.toFloat(), y.toFloat(), bitmapPaint)
    }
    /** One column of [b] shifted down by [dy], for waving curtains. */
    fun slice(b: Bitmap, sx: Int, sw: Int, x: Int, y: Int, alpha: Int) {
        src.set(sx, 0, sx + sw, b.height); dst.set(x.toFloat(), y.toFloat(), (x + sw).toFloat(), (y + b.height).toFloat())
        bitmapPaint.alpha = alpha.coerceIn(0, 255); c.drawBitmap(b, src, dst, bitmapPaint)
    }
}

/**
 * One region's ground. Still layers are painted twice: first as raw pixels (dithered gradients, textures),
 * then with shapes. Motion is drawn every frame in grid pixels from [time] alone, so it needs no state.
 */
internal abstract class ScenePainter(private val seed: Int) {
    protected val random = Random(seed.toLong())
    private val salt = seed * 1013904223
    var w = 0; protected set
    var h = 0; protected set
    /** Horizon row, middle column and how far the camps reach from it. */
    protected var hz = 0
    protected var cx = 0
    protected var reach = 0

    fun frame(w: Int, h: Int, reach: Int) {
        this.w = w; this.h = h; this.reach = reach
        hz = (h * HamletArena.HORIZON).toInt(); cx = w / 2
        random.setSeed(seed.toLong())
    }
    abstract fun sky(a: IntArray)
    open fun skyShapes(p: ScenePen) {}
    abstract fun ground(a: IntArray)
    abstract fun land(p: ScenePen)
    /** Small moving pieces (clouds, mist) painted once per size. */
    open fun sprites(p: ScenePen) {}
    open fun skyMotion(p: ScenePen, t: Float) {}
    open fun landMotion(p: ScenePen, t: Float) {}
    open fun front(p: ScenePen, t: Float) {}

    protected fun rgb(v: Int) = v or 0xFF000000.toInt()
    protected fun argb(alpha: Int, v: Int) = (alpha.coerceIn(0, 255) shl 24) or (v and 0xFFFFFF)
    protected fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a ushr s) and 255) + (((b ushr s) and 255) - ((a ushr s) and 255)) * k).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
    /** Repeatable noise in [0, 1) for a pixel. */
    protected fun hash(x: Int, y: Int, k: Int = 0): Float {
        var n = x * 374761393 + y * 668265263 + k * 1442695041 + salt
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 0xFFFFFF) / 16777216f
    }
    /** Smooth value noise in [0, 1). */
    protected fun noise(x: Float, y: Float, k: Int = 0): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix; val fy = y - iy
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = hash(ix, iy, k); val b = hash(ix + 1, iy, k); val c = hash(ix, iy + 1, k); val d = hash(ix + 1, iy + 1, k)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }
    protected fun sample(stops: IntArray, t: Float): Int {
        val v = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = min(stops.size - 2, v.toInt())
        return mix(stops[i], stops[i + 1], v - i)
    }
    /** Bands of colour with a short ordered dither between them. */
    protected fun gradient(a: IntArray, y0: Int, y1: Int, stops: IntArray, band: Int = 7) {
        val span = max(1, y1 - y0); val levels = max(1, span / band)
        for (y in max(0, y0) until min(h, y1)) {
            val v = (y - y0).toFloat() / span * levels
            val base = v.toInt(); val d = (v - base - .55f) / .45f
            val lo = sample(stops, base.toFloat() / levels); val hi = sample(stops, (base + 1f) / levels)
            for (x in 0 until w) a[y * w + x] = if (d > BAYER[(y and 3) * 4 + (x and 3)]) hi else lo
        }
    }
    /** Below 1 inside the field where fighters stand; slightly ragged at the edge. */
    protected fun field(x: Int, y: Int): Float {
        val dx = (x - cx) / (reach + 8f); val dy = (y - h * .61f) / (h * .25f)
        return dx * dx + dy * dy + (hash(x, y, 7) - .5f) * .12f
    }
    protected fun outside(x: Int, y: Int, margin: Float = 0f) = field(x, y) > 1 + margin
    protected fun put(a: IntArray, x: Int, y: Int, col: Int) { if (x in 0 until w && y in 0 until h) a[y * w + x] = col }
    /** Smooth rolling outline around [base], within ±[amp]. */
    protected fun hills(x: Int, base: Int, amp: Float, f: Float, phase: Float) = base - (amp * (.6f * sin(x * f + phase) +
        .3f * sin(x * f * 2.3f + phase * 1.7f) + .1f * sin(x * f * 5.1f + phase * 2.9f))).toInt()
    /** Sharp peaks from two triangle waves, between 0 and 1. */
    protected fun peaks(x: Int, f: Float, phase: Float): Float {
        fun tri(u: Float) = 1 - 2 * abs(u - floor(u) - .5f)
        val a = tri(x * f + phase); val b = tri(x * f * 2.7f + phase * 3.1f)
        return a * a * .75f + b * .25f
    }
    /** A silhouette from [top] down to [bottom]. */
    protected fun ridge(p: ScenePen, bottom: Int, col: Int, top: (Int) -> Int) {
        for (x in 0 until w) { val y = top(x); p.rect(x, y, 1, bottom - y, col) }
    }
    protected fun pine(p: ScenePen, x: Int, foot: Int, height: Int, col: Int, dark: Int, snow: Int = 0) {
        p.rect(x, foot - 2, 1, 2, rgb(0x4A3426))
        val tiers = 3; val th = max(2, (height - 2) / tiers)
        for (r in 0 until height - 2) {
            val tier = r / th; val local = r % th
            val half = (local * (2 + tier)) / th + tier / 2
            val y = foot - height + r
            p.rect(x - half, y, half * 2 + 1, 1, col); p.rect(x + 1, y, half, 1, dark)
            if (snow != 0 && local == 0) p.rect(x - half, y, half + 1, 1, snow)
        }
    }
    protected fun roundTree(p: ScenePen, x: Int, foot: Int, r: Int, trunk: Int, leaf: Int, light: Int, dark: Int) {
        p.rect(x - 1, foot - trunk, 2, trunk, rgb(0x5A3E2A)); p.dot(x, foot - trunk, rgb(0x3E2A1E))
        val cy = foot - trunk - r + 1
        p.disc(x, cy, r, dark); p.disc(x - 1, cy - 1, r - 1, leaf)
        p.disc(x - r / 3, cy - r / 3, max(1, r / 3), light)
        p.disc(x + r - 1, cy + 1, max(1, r / 3), dark)
    }
    /** A soft cloud: puffs of different sizes on a flat base, lit on top and shaded underneath. */
    protected fun cloud(p: ScenePen, cw: Int, ch: Int, light: Int, mid: Int, shade: Int): Bitmap {
        val b = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        p.c = Canvas(b)
        val lobes = 3 + random.nextInt(3)
        val xs = IntArray(lobes); val rs = IntArray(lobes)
        for (i in 0 until lobes) {
            val t = (i + .5f) / lobes
            xs[i] = (cw * (.16f + .68f * t)).toInt() + random.nextInt(3) - 1
            rs[i] = max(2, (ch * (.34f + .3f * (1 - abs(t - .42f) * 1.8f).coerceAtLeast(0f))).toInt())
        }
        val base = ch - 1
        for (i in 0 until lobes) p.disc(xs[i], base - rs[i] / 2, rs[i], shade)
        for (i in 0 until lobes) p.disc(xs[i], base - rs[i] / 2 - 1, rs[i] - 1, mid)
        for (i in 0 until lobes) p.disc(xs[i] - rs[i] / 3, base - rs[i] + 1, max(1, rs[i] / 2), light)
        p.rect(xs[0] - rs[0] / 2, base, xs[lobes - 1] - xs[0] + rs[0], 1, shade)
        return b
    }
    /** A bushy crown of three puffs on a short trunk. */
    protected fun bushyTree(p: ScenePen, x: Int, foot: Int, r: Int, leaf: Int, light: Int, dark: Int) {
        p.rect(x - 1, foot - r, 2, r, rgb(0x5A3E2A))
        val cy = foot - r - r / 2
        val side = max(2, r * 2 / 3)
        p.disc(x - r / 2 - 1, cy + 1, side, dark); p.disc(x + r / 2 + 1, cy + 1, side, dark); p.disc(x, cy - r / 3, r, dark)
        p.disc(x - r / 2 - 1, cy, side - 1, leaf); p.disc(x + r / 2, cy, side - 1, leaf); p.disc(x - 1, cy - r / 3 - 1, r - 1, leaf)
        p.disc(x - r / 2, cy - r / 2 - 1, max(1, r / 3), light); p.dot(x + r / 2, cy - 1, light)
    }
    /** A strip of mist that tiles horizontally. */
    protected fun mist(p: ScenePen, tile: Int, mh: Int, col: Int): Bitmap {
        val b = Bitmap.createBitmap(tile, mh, Bitmap.Config.ARGB_8888)
        p.c = Canvas(b)
        for (i in 0 until tile / 6) {
            val x = random.nextInt(tile); val y = mh / 2 + random.nextInt(max(1, mh / 3)) - mh / 6
            val rx = 6 + random.nextInt(10); val ry = max(1, mh / 4 + random.nextInt(max(1, mh / 4)))
            for (shift in intArrayOf(-tile, 0, tile)) p.oval(x + shift, y, rx, ry, col)
        }
        return b
    }
    /** Draws a tiling strip scrolled by [offset]. */
    protected fun scroll(p: ScenePen, b: Bitmap, offset: Float, y: Int, alpha: Int) {
        val start = -Math.floorMod(offset.toInt(), b.width)
        var x = start
        while (x < w) { p.bitmap(b, x, y, alpha); x += b.width }
    }
    /** Where a drifting particle is: wraps from [x0], [y0] in [0, 1) at a speed in pixels per second. */
    protected fun wrap(v0: Float, size: Int, speed: Float, t: Float, margin: Int = 0): Int {
        val span = size + margin * 2
        return Math.floorMod((v0 * span + speed * t).toInt(), span) - margin
    }
    protected fun seeds(n: Int) = FloatArray(n) { random.nextFloat() }

    companion object {
        private val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f).also {
            for (i in it.indices) it[i] = (it[i] + .5f) / 16f
        }
    }
}
