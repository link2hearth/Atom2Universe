package com.Atom2Universe.app.games.infernale.art

import kotlin.math.*

internal const val INK = 0xFF59637D.toInt()
internal const val SHADE = 0xFF91A4BD.toInt()
internal const val METAL = 0xFFBFDCEC.toInt()
internal const val LIGHT = 0xFFFFF6E7.toInt()
internal const val WOOD = 0xFFF1BE93.toInt()
internal const val MINT = 0xFFACDDC5.toInt()
internal const val LILAC = 0xFFC9BCE9.toInt()
internal const val CORAL = 0xFFF1A4AA.toInt()
internal const val GOLD = 0xFFF3D58B.toInt()

/** Raster commun Android / planches de contrôle : exactement les mêmes pixels. */
internal class InfernalePixels {
    val pixels = IntArray(64 * 64)
    var a = 0f; private set
    var t = 0f; private set
    var level = 0.5f; private set
    var phase = 0f; private set
    var active = false; private set

    fun render(id: String, angle: Float, travel: Float, fill: Float, on: Boolean, cycle: Float): IntArray {
        pixels.fill(0)
        a = if (angle.isFinite()) (angle % (2 * PI)).toFloat() else 0f
        t = if (travel.isFinite()) travel.coerceIn(0f, 1f) else 0f
        level = if (fill.isFinite()) fill.coerceIn(0f, 1f) else 0f
        phase = if (cycle.isFinite()) ((cycle % 1f) + 1f) % 1f else 0f
        active = on
        val name = id.substringAfter('.')
        when (id.substringBefore('.')) {
            "structure" -> structure(name)
            "rotary" -> rotary(name)
            "transmission" -> transmission(name)
            "linear" -> linear(name)
            "pneumatic" -> pneumatic(name)
            "hydraulic" -> hydraulic(name)
            "projectile" -> projectile(name)
            "energy" -> energy(name)
            "control" -> control(name)
            "sensor" -> sensor(name)
            "safety" -> safety(name)
            else -> error("Unknown Infernale sprite: $id")
        }
        return pixels
    }

    fun rect(l: Int, top: Int, r: Int, b: Int, color: Int) {
        for (y in top.coerceAtLeast(0) until b.coerceAtMost(64))
            for (x in l.coerceAtLeast(0) until r.coerceAtMost(64)) pixels[y * 64 + x] = color
    }
    fun panel(l: Int, top: Int, r: Int, b: Int, color: Int) {
        rect(l, top, r, b, INK); rect(l + 2, top + 2, r - 2, b - 2, color)
        rect(l + 2, top + 2, r - 2, top + 4, LIGHT)
        rect(l + 2, b - 4, r - 2, b - 2, SHADE)
    }
    fun disk(x: Int, y: Int, radius: Int, color: Int) {
        if (radius < 0) return
        for (dy in -radius..radius) {
            val half = sqrt((radius * radius - dy * dy).toFloat()).toInt()
            rect(x - half, y + dy, x + half + 1, y + dy + 1, color)
        }
    }
    fun ring(x: Int, y: Int, r: Int, color: Int, hole: Int = r - 5) {
        disk(x, y, r, INK); disk(x, y, r - 2, color); disk(x, y, hole, INK); disk(x, y, hole - 2, 0)
    }
    fun line(x: Int, y: Int, ex: Int, ey: Int, color: Int, width: Int = 2) {
        val n = max(abs(ex - x), abs(ey - y)).coerceAtLeast(1)
        for (i in 0..n) {
            val px = x + (ex - x) * i / n; val py = y + (ey - y) * i / n
            rect(px - width / 2, py - width / 2, px - width / 2 + width, py - width / 2 + width, color)
        }
    }
    fun bar(x: Int, y: Int, ex: Int, ey: Int, color: Int = METAL, width: Int = 7) {
        line(x, y, ex, ey, INK, width); line(x, y, ex, ey, color, (width - 4).coerceAtLeast(1))
    }
    fun bolt(x: Int, y: Int) { disk(x, y, 3, INK); rect(x - 1, y - 2, x + 2, y, LIGHT) }
    fun port(x: Int, y: Int, color: Int = METAL) { panel(x - 4, y - 5, x + 5, y + 6, color) }
    fun poly(color: Int, vararg xy: Int) {
        val count = xy.size / 2
        for (y in 0..63) for (x in 0..63) {
            var inside = false; var j = count - 1
            for (i in 0 until count) {
                val xi = xy[i * 2]; val yi = xy[i * 2 + 1]; val xj = xy[j * 2]; val yj = xy[j * 2 + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi).toFloat() * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            if (inside) pixels[y * 64 + x] = color
        }
    }
    fun px(x: Int, r: Int, angle: Float = a) = x + (cos(angle) * r).roundToInt()
    fun py(y: Int, r: Int, angle: Float = a) = y + (sin(angle) * r).roundToInt()
    fun spokes(x: Int, y: Int, r: Int, count: Int = 4, angle: Float = a, color: Int = LIGHT) {
        for (i in 0 until count) { val v = angle + i * (2 * PI / count).toFloat(); line(x, y, px(x, r, v), py(y, r, v), color, 3) }
        bolt(x, y)
    }
    fun gear(x: Int, y: Int, r: Int, teeth: Int = 12, color: Int = METAL, angle: Float = a) {
        for (i in 0 until teeth) { val v = angle + i * (2 * PI / teeth).toFloat(); val tx = px(x, r, v); val ty = py(y, r, v); panel(tx - 3, ty - 3, tx + 4, ty + 4, color) }
        disk(x, y, r - 1, INK); disk(x, y, r - 3, color); spokes(x, y, r - 6, 3, angle, SHADE)
    }
    fun spring(x: Int, y: Int, length: Int, color: Int = MINT, turns: Int = 7, height: Int = 7) {
        var oldX = x; var oldY = y
        for (i in 1..turns * 2) {
            val nx = x + length * i / (turns * 2); val ny = if (i == turns * 2) y else y + if (i % 2 == 0) -height else height
            bar(oldX, oldY, nx, ny, color, 4); oldX = nx; oldY = ny
        }
    }
    fun gauge(x: Int, y: Int, r: Int, color: Int = LILAC) {
        disk(x, y, r, INK); disk(x, y, r - 2, color); disk(x, y, r - 4, LIGHT)
        for (i in 0..4) { val v = 2.4f + i * 1.15f; disk(px(x, r - 6, v), py(y, r - 6, v), 1, SHADE) }
        val v = 2.4f + level * 4.6f; line(x, y, px(x, r - 7, v), py(y, r - 7, v), CORAL, 2); bolt(x, y)
    }
    fun foot(x: Int, y: Int) { panel(x - 6, y, x + 7, y + 5, SHADE) }
    fun lamp(x: Int, y: Int) { disk(x, y, 3, INK); disk(x, y, 1, if (active) MINT else CORAL) }
}
