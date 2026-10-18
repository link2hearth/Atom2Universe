package com.Atom2Universe.app.pixelart.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Reçoit les pixels d'une forme, un par un. Peut être appelé hors du dessin : à l'appelant de rogner. */
fun interface PointSink {
    fun plot(x: Int, y: Int)
}

/**
 * Rasterisation de formes en pixels. Rien ne s'alloue par pixel : les formes sortent dans un
 * [PointSink], à l'ancienne liste de paires (un objet par pixel) succède un simple appel.
 */
object Raster {

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, sink: PointSink) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            sink.plot(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Trait en pointillés : [on] pixels tracés, [off] pixels sautés. */
    fun dashedLine(x0: Int, y0: Int, x1: Int, y1: Int, on: Int, off: Int, sink: PointSink) {
        var n = 0
        val period = max(1, on + off)
        line(x0, y0, x1, y1) { x, y ->
            if (n % period < on) sink.plot(x, y)
            n++
        }
    }

    fun rect(xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink) {
        val x0 = min(xa, xb); val x1 = max(xa, xb)
        val y0 = min(ya, yb); val y1 = max(ya, yb)
        if (filled) {
            for (y in y0..y1) for (x in x0..x1) sink.plot(x, y)
        } else {
            for (x in x0..x1) { sink.plot(x, y0); if (y1 != y0) sink.plot(x, y1) }
            for (y in y0 + 1 until y1) { sink.plot(x0, y); if (x1 != x0) sink.plot(x1, y) }
        }
    }

    /**
     * Ellipse inscrite dans le rectangle de coins (xa,ya)-(xb,yb), bornes incluses.
     * Algorithme de Zingl : exact pour les rectangles pairs comme impairs. Le remplissage
     * se déduit du contour, donc les deux se recouvrent toujours au pixel près.
     */
    fun ellipse(xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink) {
        if (!filled) {
            plotEllipseOutline(xa, ya, xb, yb, sink)
            return
        }
        val top = min(ya, yb)
        val h = abs(yb - ya) + 1
        val lo = IntArray(h) { Int.MAX_VALUE }
        val hi = IntArray(h) { Int.MIN_VALUE }
        plotEllipseOutline(xa, ya, xb, yb) { x, y ->
            val r = y - top
            if (r in 0 until h) {
                if (x < lo[r]) lo[r] = x
                if (x > hi[r]) hi[r] = x
            }
        }
        for (r in 0 until h) {
            if (lo[r] > hi[r]) continue
            for (x in lo[r]..hi[r]) sink.plot(x, top + r)
        }
    }

    private fun plotEllipseOutline(xa: Int, ya: Int, xb: Int, yb: Int, sink: PointSink) {
        var x0 = xa; var y0 = ya; var x1 = xb; var y1 = yb
        var a = abs(x1 - x0).toLong()
        val b = abs(y1 - y0).toLong()
        var b1 = b and 1L
        var dx = 4 * (1 - a) * b * b
        var dy = 4 * (b1 + 1) * a * a
        var err = dx + dy + b1 * a * a
        if (x0 > x1) { x0 = x1; x1 += a.toInt() }
        if (y0 > y1) y0 = y1
        y0 += ((b + 1) / 2).toInt()
        y1 = y0 - b1.toInt()
        a = 8 * a * a
        b1 = 8 * b * b
        do {
            sink.plot(x1, y0); sink.plot(x0, y0); sink.plot(x0, y1); sink.plot(x1, y1)
            val e2 = 2 * err
            if (e2 <= dy) { y0++; y1--; dy += a; err += dy }
            if (e2 >= dx || 2 * err > dy) { x0++; x1--; dx += b1; err += dx }
        } while (x0 <= x1)
        // Ellipses très plates : l'arrêt anticipé laisse deux pointes à combler.
        while (y0 - y1 < b) {
            sink.plot(x0 - 1, y0); sink.plot(x1 + 1, y0++)
            sink.plot(x0 - 1, y1); sink.plot(x1 + 1, y1--)
        }
    }

    /**
     * Polygone donné en coordonnées de « centres de pixels » (le pixel i a son centre en i).
     * Rempli par balayage pair-impair, puis le contour est toujours tracé par-dessus pour qu'aucune
     * arête fine ne disparaisse.
     */
    fun polygon(xs: FloatArray, ys: FloatArray, filled: Boolean, sink: PointSink) {
        val n = xs.size
        if (n == 0) return
        if (filled && n >= 3) fillPolygon(xs, ys, sink)
        for (i in 0 until n) {
            val j = (i + 1) % n
            line(xs[i].roundPx(), ys[i].roundPx(), xs[j].roundPx(), ys[j].roundPx(), sink)
        }
    }

    private fun Float.roundPx(): Int = floor(this + 0.5f).toInt()

    private fun fillPolygon(xs: FloatArray, ys: FloatArray, sink: PointSink) {
        val n = xs.size
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (y in ys) { if (y < minY) minY = y; if (y > maxY) maxY = y }
        val crossings = FloatArray(n)
        for (row in ceil(minY).toInt()..floor(maxY).toInt()) {
            val y = row.toFloat()
            var c = 0
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = ys[i]; val yb = ys[j]
                if (ya == yb) continue
                val lo = min(ya, yb); val hi = max(ya, yb)
                if (y < lo || y >= hi) continue
                crossings[c++] = xs[i] + (y - ya) / (yb - ya) * (xs[j] - xs[i])
            }
            java.util.Arrays.sort(crossings, 0, c)
            var k = 0
            while (k + 1 < c) {
                for (x in ceil(crossings[k]).toInt()..floor(crossings[k + 1]).toInt()) sink.plot(x, row)
                k += 2
            }
        }
    }

    /** Points d'un polygone régulier normalisés pour remplir exactement une boîte 0..1 × 0..1. */
    private fun regularPolygon(sides: Int, startDeg: Double): Array<FloatArray> {
        val raw = Array(sides) { k ->
            val t = (startDeg + 360.0 * k / sides) * PI / 180.0
            floatArrayOf(cos(t).toFloat(), sin(t).toFloat())
        }
        return normalizeToUnitBox(raw)
    }

    private fun star(points: Int, inner: Float): Array<FloatArray> {
        val raw = Array(points * 2) { k ->
            val t = (-90.0 + 180.0 * k / points) * PI / 180.0
            val r = if (k % 2 == 0) 1f else inner
            floatArrayOf((cos(t) * r).toFloat(), (sin(t) * r).toFloat())
        }
        return normalizeToUnitBox(raw)
    }

    private fun normalizeToUnitBox(p: Array<FloatArray>): Array<FloatArray> {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (v in p) {
            minX = min(minX, v[0]); maxX = max(maxX, v[0])
            minY = min(minY, v[1]); maxY = max(maxY, v[1])
        }
        val w = max(1e-6f, maxX - minX)
        val h = max(1e-6f, maxY - minY)
        return Array(p.size) { floatArrayOf((p[it][0] - minX) / w, (p[it][1] - minY) / h) }
    }

    private val TRIANGLE = arrayOf(floatArrayOf(0.5f, 0f), floatArrayOf(1f, 1f), floatArrayOf(0f, 1f))
    private val DIAMOND = arrayOf(
        floatArrayOf(0.5f, 0f), floatArrayOf(1f, 0.5f), floatArrayOf(0.5f, 1f), floatArrayOf(0f, 0.5f),
    )
    private val PENTAGON = regularPolygon(5, -90.0)
    private val HEXAGON = regularPolygon(6, 0.0)
    private val STAR = star(5, 0.4f)

    /**
     * Forme inscrite dans la boîte de coins (xa,ya)-(xb,yb). La flèche, elle, va du premier
     * point au second : elle suit le geste plutôt que la boîte.
     */
    fun shape(kind: ShapeKind, xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink) {
        when (kind) {
            ShapeKind.LINE -> line(xa, ya, xb, yb, sink)
            ShapeKind.RECT -> rect(xa, ya, xb, yb, filled, sink)
            ShapeKind.ELLIPSE -> ellipse(xa, ya, xb, yb, filled, sink)
            ShapeKind.ROUNDED_RECT -> roundedRect(xa, ya, xb, yb, filled, sink)
            ShapeKind.ARROW -> arrow(xa, ya, xb, yb, filled, sink)
            ShapeKind.TRIANGLE -> unitPolygon(TRIANGLE, xa, ya, xb, yb, filled, sink)
            ShapeKind.DIAMOND -> unitPolygon(DIAMOND, xa, ya, xb, yb, filled, sink)
            ShapeKind.PENTAGON -> unitPolygon(PENTAGON, xa, ya, xb, yb, filled, sink)
            ShapeKind.HEXAGON -> unitPolygon(HEXAGON, xa, ya, xb, yb, filled, sink)
            ShapeKind.STAR -> unitPolygon(STAR, xa, ya, xb, yb, filled, sink)
        }
    }

    private fun unitPolygon(
        unit: Array<FloatArray>, xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink,
    ) {
        val x0 = min(xa, xb); val x1 = max(xa, xb)
        val y0 = min(ya, yb); val y1 = max(ya, yb)
        val w = (x1 - x0).toFloat()
        val h = (y1 - y0).toFloat()
        val xs = FloatArray(unit.size) { x0 + unit[it][0] * w }
        val ys = FloatArray(unit.size) { y0 + unit[it][1] * h }
        polygon(xs, ys, filled, sink)
    }

    private fun roundedRect(xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink) {
        val x0 = min(xa, xb).toFloat(); val x1 = max(xa, xb).toFloat()
        val y0 = min(ya, yb).toFloat(); val y1 = max(ya, yb).toFloat()
        val r = max(1f, min(x1 - x0, y1 - y0) / 4f)
        val steps = max(3, r.toInt() * 2)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        // Quatre coins, chacun un quart de cercle, dans le sens horaire depuis en haut à gauche.
        val corners = arrayOf(
            floatArrayOf(x0 + r, y0 + r, 180f),
            floatArrayOf(x1 - r, y0 + r, 270f),
            floatArrayOf(x1 - r, y1 - r, 0f),
            floatArrayOf(x0 + r, y1 - r, 90f),
        )
        for (c in corners) {
            for (s in 0..steps) {
                val t = (c[2] + 90f * s / steps) * PI / 180.0
                xs.add(c[0] + (cos(t) * r).toFloat())
                ys.add(c[1] + (sin(t) * r).toFloat())
            }
        }
        polygon(xs.toFloatArray(), ys.toFloatArray(), filled, sink)
    }

    private fun arrow(xa: Int, ya: Int, xb: Int, yb: Int, filled: Boolean, sink: PointSink) {
        val dx = (xb - xa).toFloat()
        val dy = (yb - ya).toFloat()
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) { sink.plot(xa, ya); return }
        val ux = dx / len; val uy = dy / len
        val nx = -uy; val ny = ux
        val shaft = max(0.8f, len * 0.09f)
        val head = min(len * 0.55f, max(3f, len * 0.32f))
        val headHalf = shaft * 2.4f + 0.6f
        val bx = xb - ux * head
        val by = yb - uy * head
        val xs = floatArrayOf(
            xa + nx * shaft, bx + nx * shaft, bx + nx * headHalf, xb.toFloat(),
            bx - nx * headHalf, bx - nx * shaft, xa - nx * shaft,
        )
        val ys = floatArrayOf(
            ya + ny * shaft, by + ny * shaft, by + ny * headHalf, yb.toFloat(),
            by - ny * headHalf, by - ny * shaft, ya - ny * shaft,
        )
        polygon(xs, ys, filled, sink)
    }

    // ---- Pinceaux ------------------------------------------------------------------------

    private val footprints = HashMap<Int, IntArray>()

    /**
     * Empreinte d'un pinceau de [size] pixels : paires (dx, dy) relatives au pixel visé.
     * Ronde, les petits diamètres donnent des disques nets (3 → une croix, 5 → un disque plein).
     */
    fun footprint(size: Int, round: Boolean): IntArray {
        val n = size.coerceIn(1, 256)
        val key = n * 2 + if (round) 1 else 0
        return synchronized(footprints) {
            footprints.getOrPut(key) { buildFootprint(n, round) }
        }
    }

    private fun buildFootprint(n: Int, round: Boolean): IntArray {
        val c = (n - 1) / 2f
        val r2 = (n / 2f) * (n / 2f) - n / 4f
        val out = ArrayList<Int>()
        val origin = n / 2
        for (j in 0 until n) for (i in 0 until n) {
            val ddx = i - c
            val ddy = j - c
            if (!round || n <= 2 || ddx * ddx + ddy * ddy <= r2 + 1e-4f) {
                out.add(i - origin)
                out.add(j - origin)
            }
        }
        return out.toIntArray()
    }

    /** Pose l'empreinte du pinceau centrée sur (cx, cy). */
    fun stamp(cx: Int, cy: Int, size: Int, round: Boolean, sink: PointSink) {
        if (size <= 1) { sink.plot(cx, cy); return }
        val f = footprint(size, round)
        var i = 0
        while (i < f.size) {
            sink.plot(cx + f[i], cy + f[i + 1])
            i += 2
        }
    }
}

enum class ShapeKind { LINE, RECT, ROUNDED_RECT, ELLIPSE, TRIANGLE, DIAMOND, PENTAGON, HEXAGON, STAR, ARROW }
