package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.floor

/** Le sol : herbe, chemins, eau, ponts. Peint par morceaux de 8 × 8 tuiles. */
object HamletGround {

    private const val N = HamletArt.CHUNK
    private const val TW = 64f
    private const val TH = 32f

    /** Coin haut-gauche d'un morceau, en pixels du monde (échelle 1). */
    fun left(cx: Int, cy: Int) = ((cx * N - (cy * N + N - 1)) * 32 - 32).toFloat()
    fun top(cx: Int, cy: Int) = ((cx * N + cy * N) * 16).toFloat()

    fun worldX(x: Float, y: Float) = (x - y) * 32f
    fun worldY(x: Float, y: Float) = (x + y) * 16f

    private fun hash(x: Int, y: Int, s: Int = 0): Float {
        var h = x * 374761393 + y * 668265263 + s * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun vnoise(x: Float, y: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val a = hash(xi, yi); val b = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
        val u = smooth(xf); val v = smooth(yf)
        return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v
    }

    fun fbm(x: Float, y: Float): Float = vnoise(x, y) * 0.6f + vnoise(x * 2.1f, y * 2.1f) * 0.3f + vnoise(x * 4.3f, y * 4.3f) * 0.1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    fun grassColor(x: Int, y: Int, inside: Boolean): Int {
        val n = fbm(x * 0.13f, y * 0.13f)
        var c = Iso.mix(0x5BAA32, 0x7CC43F, n)
        c = Iso.mix(c, if (hash(x, y) > 0.5f) 0x6DB838 else 0x66B134, 0.35f)
        if (!inside) c = Iso.mix(c, 0x2F5232, 0.55f)
        return c
    }

    fun paintChunk(cx: Int, cy: Int, game: HamletGame): Bitmap {
        val bmp = Bitmap.createBitmap(512, 262, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val l = left(cx, cy); val t = top(cx, cy)
        val o = game.realmOrigin(); val r = game.realmSize()
        fun inside(x: Int, y: Int) = x >= o && y >= o && x < o + r && y < o + r
        for (ty in cy * N until cy * N + N) for (tx in cx * N until cx * N + N) {
            val px = worldX(tx.toFloat(), ty.toFloat()) - l
            val py = worldY(tx.toFloat(), ty.toFloat()) - t + 16f
            val code = game.pathAt(tx, ty)
            val ins = inside(tx, ty)
            diamond(c, px, py, 0.6f, grassColor(tx, ty, ins))
            if (code == 0) grassDetail(c, px, py, tx, ty, ins)
            else if (code == Hamlet.PATH_WATER) water(c, px, py, tx, ty, game)
            else pathTile(c, px, py, tx, ty, code, game)
            if (ins) boundary(c, px, py, tx, ty, ::inside)
        }
        return bmp
    }

    private fun diamond(c: Canvas, px: Float, py: Float, grow: Float, color: Int) {
        path.reset()
        path.moveTo(px, py - 16f - grow); path.lineTo(px + 32f + grow, py); path.lineTo(px, py + 16f + grow); path.lineTo(px - 32f - grow, py)
        path.close()
        fill.color = Iso.opaque(color)
        c.drawPath(path, fill)
    }

    private fun edge(c: Canvas, px: Float, py: Float, side: Int, color: Int, width: Float) {
        stroke.color = Iso.opaque(color)
        stroke.strokeWidth = width
        when (side) {
            0 -> c.drawLine(px + 32f, py, px, py + 16f, stroke)     // voisin en +x
            1 -> c.drawLine(px - 32f, py, px, py + 16f, stroke)     // voisin en +y
            2 -> c.drawLine(px, py - 16f, px - 32f, py, stroke)     // voisin en -x
            else -> c.drawLine(px, py - 16f, px + 32f, py, stroke)  // voisin en -y
        }
    }

    private val sideOffsets = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1))

    private fun grassDetail(c: Canvas, px: Float, py: Float, x: Int, y: Int, inside: Boolean) {
        val dark = if (inside) 0x4A9528 else 0x2A4A2C
        stroke.color = Iso.withAlpha(dark, 0.55f)
        stroke.strokeWidth = 1.3f
        for (i in 0 until 3) {
            val hx = (hash(x, y, i + 1) - 0.5f) * 40f
            val hy = (hash(x, y, i + 9) - 0.5f) * 18f
            c.drawLine(px + hx, py + hy, px + hx - 1.5f, py + hy - 4f, stroke)
            c.drawLine(px + hx, py + hy, px + hx + 1.5f, py + hy - 4f, stroke)
        }
        if (inside && hash(x, y, 77) > 0.93f) {
            fill.color = if (hash(x, y, 78) > 0.5f) 0xFFF6F0 or (0xFF shl 24) else 0xFFE24A or (0xFF shl 24)
            c.drawCircle(px + (hash(x, y, 79) - 0.5f) * 30f, py + (hash(x, y, 80) - 0.5f) * 12f, 2f, fill)
        }
    }

    private fun boundary(c: Canvas, px: Float, py: Float, x: Int, y: Int, inside: (Int, Int) -> Boolean) {
        for (s in 0 until 4) {
            val nx = x + sideOffsets[s][0]; val ny = y + sideOffsets[s][1]
            if (!inside(nx, ny)) {
                edge(c, px, py, s, Iso.withAlpha(0xFFFFFF, 0.55f), 2f)
            }
        }
    }

    private fun neighborCode(game: HamletGame, x: Int, y: Int, side: Int) =
        game.pathAt(x + sideOffsets[side][0], y + sideOffsets[side][1])

    private fun water(c: Canvas, px: Float, py: Float, x: Int, y: Int, game: HamletGame) {
        val n = fbm(x * 0.2f, y * 0.2f)
        diamond(c, px, py, 0.8f, Iso.mix(0x3A86D2, 0x5AA8E8, n))
        // rives : une bande de terre claire là où l'eau s'arrête
        for (s in 0 until 4) {
            val nc = neighborCode(game, x, y, s)
            if (nc != Hamlet.PATH_WATER && nc != Hamlet.PATH_BRIDGE) edge(c, px, py, s, 0xE0D4A4 or (0xFF shl 24), 3.2f)
        }
        stroke.color = Iso.withAlpha(0xFFFFFF, 0.4f)
        stroke.strokeWidth = 1.4f
        if (hash(x, y, 5) > 0.45f) {
            val wx = px + (hash(x, y, 6) - 0.5f) * 30f; val wy = py + (hash(x, y, 7) - 0.5f) * 10f
            c.drawLine(wx - 6f, wy, wx + 6f, wy, stroke)
        }
    }

    private fun pathTile(c: Canvas, px: Float, py: Float, x: Int, y: Int, code: Int, game: HamletGame) {
        if (code == Hamlet.PATH_BRIDGE) { bridge(c, px, py, x, y, game); return }
        val def = Hamlet.byId[Hamlet.pathById[code] ?: "road"] ?: return
        val look = def.look
        val base = Iso.mix(look.a, Iso.shade(look.a, -0.12f), hash(x, y, 3) * 0.7f)
        diamond(c, px, py, 0.8f, base)
        when (look.n) {
            1 -> { // pierres : joints en croix décalés
                stroke.color = Iso.withAlpha(look.b, 0.75f)
                stroke.strokeWidth = 1.2f
                c.drawLine(px - 16f, py - 8f, px + 16f, py + 8f, stroke)
                c.drawLine(px + 16f, py - 8f, px - 16f, py + 8f, stroke)
                c.drawLine(px, py - 16f, px, py - 6f, stroke)
                c.drawLine(px, py + 6f, px, py + 16f, stroke)
            }
            3 -> { // planches
                stroke.color = Iso.withAlpha(look.b, 0.8f)
                stroke.strokeWidth = 1.3f
                for (i in -2..2) c.drawLine(px + i * 12f - 16f, py + 8f - 0f, px + i * 12f + 16f, py - 8f, stroke)
            }
            4 -> { // tapis : liseré doré
                for (s in 0 until 4) edge(c, px, py, s, Iso.withAlpha(look.b, 0.95f), 3.2f)
            }
            5 -> { // paille
                stroke.color = Iso.withAlpha(look.b, 0.7f)
                stroke.strokeWidth = 1.2f
                for (i in 0 until 6) {
                    val sx = px + (hash(x, y, i + 20) - 0.5f) * 40f; val sy = py + (hash(x, y, i + 30) - 0.5f) * 14f
                    c.drawLine(sx, sy, sx + 5f, sy - 2f, stroke)
                }
            }
            else -> { // terre : gravillons
                fill.color = Iso.withAlpha(look.b, 0.5f)
                for (i in 0 until 5) c.drawCircle(px + (hash(x, y, i + 40) - 0.5f) * 40f, py + (hash(x, y, i + 50) - 0.5f) * 14f, 1.4f, fill)
            }
        }
        // bordure douce là où le chemin rencontre l'herbe
        for (s in 0 until 4) {
            val nc = neighborCode(game, x, y, s)
            if (nc == 0 || nc == Hamlet.PATH_WATER) edge(c, px, py, s, Iso.withAlpha(Iso.shade(look.b, -0.2f), 0.85f), 2.4f)
        }
    }

    private fun bridge(c: Canvas, px: Float, py: Float, x: Int, y: Int, game: HamletGame) {
        diamond(c, px, py, 0.8f, 0x3F8AD6)
        // le pont suit l'axe où se trouvent d'autres ponts ou des chemins
        val alongX = isRoadLike(game.pathAt(x + 1, y)) || isRoadLike(game.pathAt(x - 1, y))
        path.reset()
        if (alongX) { path.moveTo(px - 4f, py - 14f); path.lineTo(px + 30f, py + 2f); path.lineTo(px + 4f, py + 14f); path.lineTo(px - 30f, py - 2f) }
        else { path.moveTo(px - 30f, py + 2f); path.lineTo(px + 4f, py - 14f); path.lineTo(px + 30f, py - 2f); path.lineTo(px - 4f, py + 14f) }
        path.close()
        fill.color = 0xFF9A6A3A.toInt()
        c.drawPath(path, fill)
        stroke.color = 0xFF5A3A1E.toInt()
        stroke.strokeWidth = 1.4f
        for (i in -2..2) {
            if (alongX) c.drawLine(px + i * 11f - 8f, py + i * 5.5f - 7f + 4f, px + i * 11f + 8f, py + i * 5.5f + 7f - 4f + 1f, stroke)
            else c.drawLine(px + i * 11f + 8f, py - i * 5.5f - 7f + 4f, px + i * 11f - 8f, py - i * 5.5f + 7f - 4f + 1f, stroke)
        }
        stroke.strokeWidth = 2.6f
        stroke.color = 0xFF6A4A2A.toInt()
        if (alongX) { c.drawLine(px - 4f, py - 14f, px + 30f, py + 2f, stroke); c.drawLine(px - 30f, py - 2f, px + 4f, py + 14f, stroke) }
        else { c.drawLine(px - 30f, py + 2f, px + 4f, py - 14f, stroke); c.drawLine(px + 30f, py - 2f, px - 4f, py + 14f, stroke) }
    }

    private fun isRoadLike(code: Int) = code == Hamlet.PATH_BRIDGE || Hamlet.walkable(code)
}
