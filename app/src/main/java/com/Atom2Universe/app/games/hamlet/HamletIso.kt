package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * Le pinceau isométrique : on lui donne des coordonnées du monde (x vers la droite-bas, y vers la
 * gauche-bas, z vers le haut, en tuiles) et il dessine des volumes ombrés. Tout le village est
 * dessiné avec ça, une fois, dans des bitmaps mis en cache.
 */
class Iso(val c: Canvas, var ox: Float, var oy: Float) {

    companion object {
        /** Demi-largeur et demi-hauteur d'une tuile, hauteur d'une unité en z. */
        const val HW = 32f
        const val HH = 16f
        const val ZH = 36f

        fun shade(color: Int, k: Float): Int {
            val r = (color shr 16) and 255
            val g = (color shr 8) and 255
            val b = color and 255
            return if (k >= 0) {
                rgb(r + (255 - r) * k, g + (255 - g) * k, b + (255 - b) * k)
            } else {
                rgb(r * (1 + k), g * (1 + k), b * (1 + k))
            }
        }

        fun mix(a: Int, b: Int, t: Float): Int {
            val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
            val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
            return rgb(ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t)
        }

        fun rgb(r: Float, g: Float, b: Float): Int =
            (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

        /** Les couleurs écrites en 0xRRGGBB n'ont pas d'alpha : on les rend opaques. */
        fun opaque(c: Int): Int = if ((c ushr 24) == 0) c or (0xFF shl 24) else c

        fun withAlpha(color: Int, a: Float): Int = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
    }

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    /** Contour fin autour de chaque face : donne un aspect dessiné. */
    var outline = true

    fun px(x: Float, y: Float) = ox + (x - y) * HW
    fun py(x: Float, y: Float, z: Float) = oy + (x + y) * HH - z * ZH

    private fun edge(color: Int) = withAlpha(shade(opaque(color), -0.5f), 0.55f)

    // ------------------------------------------------------------------ faces

    /** Un polygone dont les sommets sont donnés en x, y, z successifs. */
    fun face(color: Int, vararg v: Float) {
        path.reset()
        var i = 0
        while (i + 2 < v.size) {
            val sx = px(v[i], v[i + 1]); val sy = py(v[i], v[i + 1], v[i + 2])
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            i += 3
        }
        path.close()
        fillP.shader = null
        fillP.color = opaque(color)
        c.drawPath(path, fillP)
        if (outline) { lineP.color = edge(color); c.drawPath(path, lineP) }
    }

    /** Polygone en coordonnées d'écran (pour les détails plats). */
    fun flat(color: Int, vararg v: Float) {
        path.reset()
        var i = 0
        while (i + 1 < v.size) {
            if (i == 0) path.moveTo(v[i], v[i + 1]) else path.lineTo(v[i], v[i + 1])
            i += 2
        }
        path.close()
        fillP.shader = null
        fillP.color = opaque(color)
        c.drawPath(path, fillP)
    }

    fun top(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) =
        face(color, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z)

    /** Face du côté +y (celle qu'on voit à gauche). */
    fun faceY(y: Float, x0: Float, x1: Float, z0: Float, z1: Float, color: Int) =
        face(color, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1)

    /** Face du côté +x (celle qu'on voit à droite). */
    fun faceX(x: Float, y0: Float, y1: Float, z0: Float, z1: Float, color: Int) =
        face(color, x, y0, z0, x, y1, z0, x, y1, z1, x, y0, z1)

    fun left(c: Int) = shade(c, -0.06f)
    fun right(c: Int) = shade(c, -0.30f)
    fun topc(c: Int) = shade(c, 0.12f)

    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) {
        faceY(y1, x0, x1, z0, z1, left(color))
        faceX(x1, y0, y1, z0, z1, right(color))
        top(x0, y0, x1, y1, z1, topc(color))
    }

    /** Une boîte dont les trois faces ont leurs propres couleurs. */
    fun box3(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, l: Int, r: Int, t: Int) {
        faceY(y1, x0, x1, z0, z1, l)
        faceX(x1, y0, y1, z0, z1, r)
        top(x0, y0, x1, y1, z1, t)
    }

    // ------------------------------------------------------------------ toits

    /** Toit à deux pans, faîtage le long de x ([alongX]) ou de y. */
    fun gable(
        x0: Float, y0: Float, x1: Float, y1: Float, zb: Float, h: Float, color: Int, wall: Int,
        alongX: Boolean, ov: Float = 0.1f, lines: Int = 0,
    ) {
        if (alongX) {
            val ym = (y0 + y1) / 2
            face(wall, x1, y0, zb, x1, y1, zb, x1, ym, zb + h)
            face(left(color), x0 - ov, y1 + ov, zb - 0.03f, x1 + ov, y1 + ov, zb - 0.03f, x1 + ov, ym, zb + h, x0 - ov, ym, zb + h)
            shingles(lines, x0 - ov, y1 + ov, zb - 0.03f, x0 - ov, ym, zb + h, x1 + ov, y1 + ov, zb - 0.03f, x1 + ov, ym, zb + h, color)
        } else {
            val xm = (x0 + x1) / 2
            face(wall, x0, y1, zb, x1, y1, zb, xm, y1, zb + h)
            face(right(color), x1 + ov, y0 - ov, zb - 0.03f, x1 + ov, y1 + ov, zb - 0.03f, xm, y1 + ov, zb + h, xm, y0 - ov, zb + h)
            shingles(lines, x1 + ov, y0 - ov, zb - 0.03f, xm, y0 - ov, zb + h, x1 + ov, y1 + ov, zb - 0.03f, xm, y1 + ov, zb + h, color)
        }
    }

    /** Traits parallèles au bord pour suggérer des tuiles. */
    private fun shingles(n: Int, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, color: Int) {
        if (n <= 0) return
        lineP.color = withAlpha(shade(color, -0.45f), 0.4f)
        for (i in 1..n) {
            val t = i / (n + 1f)
            c.drawLine(
                px(lerp(ax, bx, t), lerp(ay, by, t)), py(lerp(ax, bx, t), lerp(ay, by, t), lerp(az, bz, t)),
                px(lerp(cx, dx, t), lerp(cy, dy, t)), py(lerp(cx, dx, t), lerp(cy, dy, t), lerp(cz, dz, t)), lineP,
            )
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Pyramide sur une base rectangulaire centrée. */
    fun pyramid(cx: Float, cy: Float, hx: Float, hy: Float, zb: Float, h: Float, color: Int, lines: Int = 0) {
        face(left(color), cx - hx, cy + hy, zb, cx + hx, cy + hy, zb, cx, cy, zb + h)
        face(right(color), cx + hx, cy - hy, zb, cx + hx, cy + hy, zb, cx, cy, zb + h)
        if (lines > 0) {
            lineP.color = withAlpha(shade(color, -0.45f), 0.4f)
            for (i in 1..lines) {
                val t = i / (lines + 1f)
                val z = zb + h * t
                val k = 1 - t
                c.drawLine(px(cx - hx * k, cy + hy * k), py(cx - hx * k, cy + hy * k, z), px(cx + hx * k, cy + hy * k), py(cx + hx * k, cy + hy * k, z), lineP)
                c.drawLine(px(cx + hx * k, cy - hy * k), py(cx + hx * k, cy - hy * k, z), px(cx + hx * k, cy + hy * k), py(cx + hx * k, cy + hy * k, z), lineP)
            }
        }
    }

    // ------------------------------------------------------------------ volumes ronds

    private fun ellipseR(r: Float) = r * HW * 1.4142f

    fun disc(cx: Float, cy: Float, z: Float, r: Float, color: Int) {
        val sx = px(cx, cy); val sy = py(cx, cy, z)
        rect.set(sx - ellipseR(r), sy - ellipseR(r) / 2, sx + ellipseR(r), sy + ellipseR(r) / 2)
        fillP.shader = null
        fillP.color = opaque(color)
        c.drawOval(rect, fillP)
        if (outline) { lineP.color = edge(color); c.drawOval(rect, lineP) }
    }

    fun cyl(cx: Float, cy: Float, z0: Float, z1: Float, r: Float, color: Int) {
        val sx = px(cx, cy)
        val yb = py(cx, cy, z0); val yt = py(cx, cy, z1)
        val rx = ellipseR(r); val ry = rx / 2
        fillP.shader = LinearGradient(sx - rx, 0f, sx + rx, 0f, shade(color, 0.12f), shade(color, -0.38f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(sx - rx, yt)
        path.lineTo(sx - rx, yb)
        rect.set(sx - rx, yb - ry, sx + rx, yb + ry)
        path.arcTo(rect, 180f, -180f)
        path.lineTo(sx + rx, yt)
        path.close()
        c.drawPath(path, fillP)
        if (outline) { lineP.color = edge(color); c.drawPath(path, lineP) }
        fillP.shader = null
        fillP.color = topc(color)
        rect.set(sx - rx, yt - ry, sx + rx, yt + ry)
        c.drawOval(rect, fillP)
        if (outline) { lineP.color = edge(color); c.drawOval(rect, lineP) }
    }

    fun cone(cx: Float, cy: Float, z0: Float, h: Float, r: Float, color: Int) {
        val sx = px(cx, cy)
        val yb = py(cx, cy, z0); val ya = py(cx, cy, z0 + h)
        val rx = ellipseR(r); val ry = rx / 2
        fillP.shader = LinearGradient(sx - rx, 0f, sx + rx, 0f, shade(color, 0.14f), shade(color, -0.40f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(sx - rx, yb)
        path.lineTo(sx, ya)
        path.lineTo(sx + rx, yb)
        rect.set(sx - rx, yb - ry, sx + rx, yb + ry)
        path.arcTo(rect, 0f, 180f)
        path.close()
        c.drawPath(path, fillP)
        if (outline) { lineP.color = edge(color); c.drawPath(path, lineP) }
        fillP.shader = null
    }

    /** Boule ombrée ; [rz] aplatit ou allonge verticalement. */
    fun ball(cx: Float, cy: Float, cz: Float, r: Float, color: Int, rz: Float = r) {
        val sx = px(cx, cy); val sy = py(cx, cy, cz)
        val rpx = r * 42f
        val rpy = rz * 42f
        rect.set(sx - rpx, sy - rpy, sx + rpx, sy + rpy)
        fillP.shader = RadialGradient(sx - rpx * 0.35f, sy - rpy * 0.4f, max(rpx, rpy) * 1.35f, shade(color, 0.3f), shade(color, -0.32f), Shader.TileMode.CLAMP)
        c.drawOval(rect, fillP)
        fillP.shader = null
        if (outline) { lineP.color = edge(color); c.drawOval(rect, lineP) }
    }

    /** Petite tache de couleur plate (feuilles, fruits, fleurs). */
    fun dot(cx: Float, cy: Float, cz: Float, rpx: Float, color: Int) {
        val sx = px(cx, cy); val sy = py(cx, cy, cz)
        fillP.shader = null
        fillP.color = opaque(color)
        c.drawCircle(sx, sy, rpx, fillP)
    }

    /** Ovale plat à l'écran (reflets, feuillage). */
    fun oval(cx: Float, cy: Float, cz: Float, rpx: Float, rpy: Float, color: Int) {
        val sx = px(cx, cy); val sy = py(cx, cy, cz)
        rect.set(sx - rpx, sy - rpy, sx + rpx, sy + rpy)
        fillP.shader = null
        fillP.color = opaque(color)
        c.drawOval(rect, fillP)
    }

    fun line(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int, width: Float = 1.6f) {
        lineP.color = opaque(color)
        val w = lineP.strokeWidth
        lineP.strokeWidth = width
        c.drawLine(px(x0, y0), py(x0, y0, z0), px(x1, y1), py(x1, y1, z1), lineP)
        lineP.strokeWidth = w
    }

    /** Ombre portée douce au sol sous une empreinte. */
    fun shadow(x0: Float, y0: Float, x1: Float, y1: Float, alpha: Float = 0.28f) {
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
        val sx = px(cx, cy); val sy = py(cx, cy, 0f)
        val rx = ((x1 - x0) + (y1 - y0)) * HW * 0.5f * 1.08f
        val ry = rx * 0.5f
        rect.set(sx - rx, sy - ry, sx + rx, sy + ry)
        fillP.shader = RadialGradient(sx, sy, rx, withAlpha(0x1A2A10, alpha), withAlpha(0x1A2A10, 0f), Shader.TileMode.CLAMP)
        c.drawOval(rect, fillP)
        fillP.shader = null
    }

    // ------------------------------------------------------------------ détails de façade

    /** Fenêtre sur la face +y, centrée en [u] (position le long de x). */
    fun windowY(y: Float, u: Float, zc: Float, w: Float, h: Float, glass: Int = 0xBFE3F2, frame: Int = 0xF4F0E4) {
        faceY(y + 0.003f, u - w / 2 - 0.025f, u + w / 2 + 0.025f, zc - h / 2 - 0.025f, zc + h / 2 + 0.025f, frame)
        faceY(y + 0.006f, u - w / 2, u + w / 2, zc - h / 2, zc + h / 2, glass)
    }

    fun windowX(x: Float, u: Float, zc: Float, w: Float, h: Float, glass: Int = 0xBFE3F2, frame: Int = 0xF4F0E4) {
        faceX(x + 0.003f, u - w / 2 - 0.025f, u + w / 2 + 0.025f, zc - h / 2 - 0.025f, zc + h / 2 + 0.025f, shade(frame, -0.2f))
        faceX(x + 0.006f, u - w / 2, u + w / 2, zc - h / 2, zc + h / 2, shade(glass, -0.18f))
    }

    fun doorY(y: Float, u: Float, w: Float, h: Float, color: Int) {
        faceY(y + 0.004f, u - w / 2, u + w / 2, 0.04f, 0.04f + h, color)
        dot(u + w * 0.28f, y + 0.01f, 0.04f + h * 0.45f, 1.4f, 0xF2D060)
    }

    fun doorX(x: Float, u: Float, w: Float, h: Float, color: Int) {
        faceX(x + 0.004f, u - w / 2, u + w / 2, 0.04f, 0.04f + h, shade(color, -0.2f))
    }

    fun chimney(x: Float, y: Float, z0: Float, z1: Float, color: Int = 0xA8654A) {
        box(x - 0.08f, y - 0.08f, z0, x + 0.08f, y + 0.08f, z1, color)
    }

    /** Un petit nuage de fumée. */
    fun smoke(x: Float, y: Float, z: Float) {
        oval(x, y, z + 0.1f, 5f, 4f, 0x66FFFFFF)
        oval(x + 0.05f, y, z + 0.3f, 7f, 5f, 0x55FFFFFF)
    }

    fun clampMin(a: Float, b: Float) = min(a, b)
}
