package com.Atom2Universe.app.science.mycology

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Les champignons en boule : vesses-de-loup, sclérodermes, œufs d'amanite. Même repère que [SpecimenPainter]
 * (centimètres, sol à y = 0, axe à x = 0). La boule est une superellipse d'exposant `capN`, posée sur un pied conique
 * stérile si `stipeH` n'est pas nul. Le sol est déjà dessiné par l'appelant.
 */
internal class BallPainter(
    private val c: Canvas,
    private val look: FungusLook,
    private val ox: Float,
    private val oy: Float,
    private val s: Float,
    private val hair: Float,
    private val anchors: MutableMap<Anchor, PointF>
) {
    private val spec = look.ball ?: error("BallPainter sans BallSpec")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = look.capDiam / 2f
    private val rb = look.capRise / 2f
    private val yc = look.stipeH + rb
    private val hasStalk = look.stipeH > 0.25f
    private val outline = withAlpha(darken(look.capMid, 0.62f), 240)
    private val stalkOutline = withAlpha(darken(look.stipeTop, 0.6f), 230)

    private fun px(x: Float) = ox + x * s
    private fun py(y: Float) = oy - y * s
    private fun anchor(a: Anchor, x: Float, y: Float) { anchors[a] = PointF(px(x), py(y)) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, w: Float) = p.apply {
        reset(); isAntiAlias = true; style = Paint.Style.STROKE; this.color = color
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Le contour de la boule, [inset] cm plus à l'intérieur. */
    private fun ballPath(inset: Float = 0f): Path {
        val a = max(0.05f, r - inset)
        val b = max(0.05f, rb - inset)
        val path = Path()
        val n = 96
        for (i in 0..n) {
            val t = 2f * PI.toFloat() * i / n
            val ct = cos(t); val st = sin(t)
            val x = a * sign(ct) * abs(ct).pow(2f / look.capN)
            val y = yc + b * sign(st) * abs(st).pow(2f / look.capN)
            if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
        }
        path.close()
        return path
    }

    private fun stalkHalf(y: Float): Float {
        val t = (y / yc).coerceIn(0f, 1f)
        return lerp(look.stipeBaseW / 2f, look.stipeW / 2f, t.pow(0.85f))
    }

    /** Le pied conique, du sol jusqu'au milieu de la boule. */
    private fun stalkPath(): Path {
        val path = Path()
        val n = 24
        for (i in 0..n) {
            val y = yc * i / n
            val x = -stalkHalf(y)
            if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
        }
        for (i in n downTo 0) {
            val y = yc * i / n
            path.lineTo(px(stalkHalf(y)), py(y))
        }
        path.close()
        return path
    }

    // ------------------------------------------------------------------ vue de côté

    fun side() {
        if (spec.cords) cords()
        if (hasStalk) {
            val stalk = stalkPath()
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(0f), 0f, py(yc), look.stipeBottom, look.stipeTop, Shader.TileMode.CLAMP)
            c.drawPath(stalk, p)
            c.save(); c.clipPath(stalk)
            stalkDecos()
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(px(-r), 0f, px(r), 0f,
                intArrayOf(withAlpha(Color.BLACK, 75), 0, withAlpha(Color.WHITE, 45), 0, withAlpha(Color.BLACK, 95)),
                floatArrayOf(0f, 0.2f, 0.4f, 0.65f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(px(-r), py(yc), px(r), py(0f), p)
            c.restore()
            stroke(stalkOutline, hair * 1.1f); c.drawPath(stalk, p)
        }
        val body = ballPath()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(-0.28f * r), py(yc + 0.3f * rb), max(r, rb) * s * 1.6f,
            intArrayOf(look.capCenter, look.capMid, look.capEdge), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        c.save(); c.clipPath(body)
        decos()
        // ombre vers le bas, lumière en haut à gauche
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(yc + rb * 0.2f), 0f, py(yc - rb), 0, withAlpha(Color.BLACK, 80), Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(-0.35f * r), py(yc + 0.4f * rb), r * s * 0.6f, withAlpha(Color.WHITE, 95), 0, Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(body, p)

        anchor(Anchor.CAP, -0.4f * r, yc + 0.35f * rb)
        anchor(Anchor.EDGE, r * 0.97f, yc)
        anchor(Anchor.BASE, 0f, 0.15f)
        if (hasStalk) anchor(Anchor.STIPE_M, stalkHalf(yc * 0.5f), yc * 0.5f)
        else anchor(Anchor.STIPE_M, 0.55f * r, yc - 0.85f * rb)
    }

    private fun stalkDecos() {
        for (d in look.stipeDecos) if (d is StipeDeco.Dots) {
            val g = Random(look.seed + 9)
            fill(withAlpha(d.color, d.alpha))
            var y = 0.2f
            var line = 0
            while (y < yc) {
                val half = stalkHalf(y)
                var x = -half + (if (line % 2 == 0) 0f else d.spacing * 0.5f)
                while (x < half) {
                    val w = d.size * (0.7f + g.nextFloat() * 0.6f)
                    c.drawOval(RectF(px(x), py(y + w * 0.6f), px(x + w), py(y - w * 0.4f)), p)
                    x += d.spacing * (0.8f + g.nextFloat() * 0.4f)
                }
                y += d.spacing * 0.8f
                line++
            }
        }
    }

    /** Des cordons de mycélium qui descendent sous la boule (scléroderme). */
    private fun cords() {
        val g = Random(look.seed + 83)
        stroke(withAlpha(look.stipeBottom, 190), max(hair * 1.2f, 0.07f * s))
        for (k in 0 until 6) {
            val x0 = (-0.5f + k / 5f) * r * 0.9f
            val path = Path()
            path.moveTo(px(x0), py(0.3f))
            path.quadTo(px(x0 * 1.4f + (g.nextFloat() - 0.5f) * 0.5f), py(-0.3f), px(x0 * 1.9f + (g.nextFloat() - 0.5f) * 0.6f), py(-0.85f))
            c.drawPath(path, p)
        }
    }

    // ------------------------------------------------------------------ décors : pointes, plaques, flocons

    private fun decos() {
        val g = Random(look.seed + 71)
        for (d in look.capDecos) when (d) {
            is CapDeco.Warts -> {
                var placed = 0
                var tries = 0
                while (placed < d.count && tries < d.count * 25) {
                    tries++
                    val u = g.nextFloat() * 2f - 1f
                    val v = g.nextFloat() * 2f - 1f
                    val q = u * u + v * v
                    if (q > 0.97f) continue
                    val depth = sqrt(1f - q)
                    spine(px(u * r), py(yc + v * rb), d.size * s * (0.3f + 0.7f * depth), d.color)
                    placed++
                }
            }
            is CapDeco.Cracks -> {
                stroke(withAlpha(d.color, d.alpha), max(hair * 1.2f, r * s * 0.016f))
                val rings = max(3, (r / d.cell).toInt() + 1)
                fun rho(k: Int) = 0.99f * k / rings
                for (k in 1..rings) {
                    // un anneau fait d'arcs séparés par des trous : la peau ne se fend pas en cercle parfait
                    var j = 0
                    while (j < 48) {
                        val len = 4 + g.nextInt(9)
                        val arc = Path()
                        for (q in 0..len) {
                            val t = 2f * PI.toFloat() * (j + q) / 48f
                            val rr = rho(k) + (g.nextFloat() - 0.5f) * 0.07f
                            val x = px(rr * r * cos(t)); val y = py(yc + rr * rb * sin(t))
                            if (q == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
                        }
                        c.drawPath(arc, p)
                        j += len + 1 + g.nextInt(3)
                    }
                }
                for (k in 0 until rings) {
                    val n = max(6, (2f * PI.toFloat() * rho(k + 1) * r / d.cell).toInt())
                    for (j in 0 until n) {
                        val t = 2f * PI.toFloat() * (j + 0.5f + (g.nextFloat() - 0.5f) * 1.1f) / n
                        val a = rho(k).coerceAtLeast(0.05f)
                        val b = rho(k + 1) * (0.7f + 0.3f * g.nextFloat())
                        c.drawLine(px(a * r * cos(t)), py(yc + a * rb * sin(t)), px(b * r * cos(t + (g.nextFloat() - 0.5f) * 0.2f)), py(yc + b * rb * sin(t)), p)
                    }
                }
            }
            is CapDeco.Flakes -> {
                fill(withAlpha(d.color, 215))
                repeat(d.count) {
                    val u = g.nextFloat() * 2f - 1f
                    val v = g.nextFloat() * 2f - 1f
                    if (u * u + v * v > 0.95f) return@repeat
                    val depth = sqrt(1f - (u * u + v * v))
                    val w = d.size * s * (0.4f + g.nextFloat() * 0.8f) * (0.4f + 0.6f * depth)
                    val flake = Path()
                    val pts = 6
                    for (k in 0 until pts) {
                        val a = 2f * PI.toFloat() * k / pts
                        val rr = w * (0.35f + g.nextFloat() * 0.35f)
                        val x = px(u * r) + rr * cos(a); val y = py(yc + v * rb) + rr * 0.7f * sin(a)
                        if (k == 0) flake.moveTo(x, y) else flake.lineTo(x, y)
                    }
                    flake.close(); c.drawPath(flake, p)
                }
            }
            else -> {}
        }
    }

    /** Une pointe vue de face : une perle claire avec son ombre. */
    private fun spine(x: Float, y: Float, w: Float, color: Int) {
        fill(withAlpha(darken(color, 0.3f), 130)); c.drawCircle(x + w * 0.12f, y + w * 0.15f, w * 0.5f, p)
        fill(withAlpha(color, 240)); c.drawCircle(x, y, w * 0.5f, p)
        fill(withAlpha(Color.WHITE, 140)); c.drawCircle(x - w * 0.14f, y - w * 0.17f, w * 0.16f, p)
    }

    // ------------------------------------------------------------------ coupe

    fun section() {
        val outer = ballPath()
        val inner = ballPath(spec.skin)
        val skinWidth = max(hair * 1.8f, spec.skin * s)
        when (spec.inside) {
            BallInside.SOLID -> {
                if (hasStalk) {
                    val stalk = stalkPath()
                    c.drawPath(stalk, fill(spec.accent))
                    c.save(); c.clipPath(stalk)
                    stroke(withAlpha(darken(spec.accent, 0.35f), 120), hair * 0.7f)
                    for (k in -3..3) c.drawLine(px(k * look.stipeW * 0.14f), py(0.2f), px(k * look.stipeW * 0.2f), py(yc), p)
                    c.restore()
                    stroke(stalkOutline, hair); c.drawPath(stalk, p)
                }
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(px(-0.2f * r), py(yc + 0.25f * rb), max(r, rb) * s * 1.4f,
                    intArrayOf(lighten(spec.gleba, 0.4f), spec.gleba, darken(spec.gleba, 0.08f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(outer, p)
                c.save(); c.clipPath(outer)
                // la limite entre la chair pleine et la base stérile (un dôme bas), les alvéoles de la base, puis un grain fin
                if (hasStalk) {
                    val base = Path()
                    val yLimit = { x: Float -> yc - rb * 0.5f + 0.3f * rb * (1f - (x / r) * (x / r)) }
                    for (i in 0..32) {
                        val x = -r + 2f * r * i / 32f
                        if (i == 0) base.moveTo(px(x), py(yLimit(x))) else base.lineTo(px(x), py(yLimit(x)))
                    }
                    base.lineTo(px(r), py(0f)); base.lineTo(px(-r), py(0f)); base.close()
                    c.drawPath(base, fill(withAlpha(spec.accent, 245)))
                    c.save(); c.clipPath(base)
                    val cells = Random(look.seed + 89)
                    fill(withAlpha(darken(spec.accent, 0.3f), 70))
                    repeat(46) {
                        val x = (cells.nextFloat() * 2f - 1f) * r * 0.8f
                        val y = yc - rb * 0.95f + cells.nextFloat() * rb * 0.95f
                        c.drawOval(RectF(px(x), py(y + 0.07f), px(x + 0.16f), py(y - 0.07f)), p)
                    }
                    c.restore()
                    stroke(withAlpha(darken(spec.gleba, 0.4f), 150), hair * 0.9f)
                    val limit = Path()
                    for (i in 0..32) {
                        val x = -r + 2f * r * i / 32f
                        if (i == 0) limit.moveTo(px(x), py(yLimit(x))) else limit.lineTo(px(x), py(yLimit(x)))
                    }
                    c.drawPath(limit, p)
                }
                val g = Random(look.seed + 91)
                fill(withAlpha(darken(spec.gleba, 0.25f), 45))
                repeat(110) {
                    c.drawCircle(px((g.nextFloat() * 2f - 1f) * r * 0.9f), py(yc + (g.nextFloat() * 2f - 1f) * rb * 0.9f), max(hair * 0.5f, 0.025f * s), p)
                }
                c.restore()
                stroke(withAlpha(spec.skinColor, 255), skinWidth); c.drawPath(outer, p)
                stroke(outline, hair); c.drawPath(outer, p)
                anchor(Anchor.S_FLESH, 0.25f * r, yc + 0.3f * rb)
                anchor(Anchor.S_VOLVA, r - spec.skin * 0.4f, yc + 0.35f * rb)
                anchor(Anchor.S_BASE, 0f, yc * 0.4f)
            }
            BallInside.SPORE_MASS -> {
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(px(-0.3f * r), py(yc + 0.3f * rb), max(r, rb) * s * 1.5f,
                    intArrayOf(lighten(spec.skinColor, 0.15f), spec.skinColor, darken(spec.skinColor, 0.25f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(outer, p)
                c.drawPath(inner, fill(spec.gleba))
                c.save(); c.clipPath(inner)
                val g = Random(look.seed + 97)
                stroke(withAlpha(spec.accent, 235), max(hair * 1.4f, 0.06f * s))
                var veinX = 0f
                var veinY = 0f
                for (k in 0 until 16) {
                    var u = (g.nextFloat() * 2f - 1f) * 0.8f
                    var v = (g.nextFloat() * 2f - 1f) * 0.8f
                    var dir = 2f * PI.toFloat() * g.nextFloat()
                    val vein = Path()
                    vein.moveTo(px(u * r), py(yc + v * rb))
                    for (j in 0 until 10) {
                        dir += (g.nextFloat() - 0.5f) * 1.1f
                        u += 0.085f * cos(dir)
                        v += 0.085f * sin(dir)
                        if (u * u + v * v > 0.9f) break
                        vein.lineTo(px(u * r), py(yc + v * rb))
                    }
                    c.drawPath(vein, p)
                    if (k == 3) { veinX = px(u * r); veinY = py(yc + v * rb) }
                }
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(px(-0.3f * r), py(yc + 0.3f * rb), r * s * 0.9f, withAlpha(Color.WHITE, 40), 0, Shader.TileMode.CLAMP)
                c.drawPath(inner, p)
                c.restore()
                stroke(withAlpha(darken(spec.skinColor, 0.45f), 220), hair * 0.9f); c.drawPath(inner, p)
                stroke(outline, hair); c.drawPath(outer, p)
                anchor(Anchor.S_FLESH, -0.35f * r, yc - 0.2f * rb)
                anchor(Anchor.S_VOLVA, r - spec.skin * 0.5f, yc + 0.25f * rb)
                anchors[Anchor.S_CAVITY] = PointF(veinX, veinY)
            }
            BallInside.YOUNG_AMANITA -> {
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(px(-0.3f * r), py(yc + 0.3f * rb), max(r, rb) * s * 1.5f,
                    intArrayOf(lighten(spec.skinColor, 0.25f), spec.skinColor, darken(spec.skinColor, 0.12f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(outer, p)
                c.drawPath(inner, fill(spec.gleba))
                c.save(); c.clipPath(inner)
                val ri = r - spec.skin
                val yi0 = look.stipeH + spec.skin
                val hb = look.capRise - 2f * spec.skin
                // pied et bulbe
                val stemHalf = 0.12f * ri
                val yBase = yi0 + 0.6f * hb
                val bulb = RectF(px(-0.3f * ri), py(yi0 + 0.34f * hb), px(0.3f * ri), py(yi0 + 0.02f * hb))
                c.drawOval(bulb, fill(look.stipeTop))
                stroke(stalkOutline, hair); c.drawOval(bulb, p)
                val stem = RectF(px(-stemHalf), py(yBase + 0.02f * hb), px(stemHalf), py(yi0 + 0.2f * hb))
                c.drawRect(stem, fill(look.stipeTop))
                stroke(stalkOutline, hair); c.drawRect(stem, p)
                // lames, sous le chapeau
                stroke(withAlpha(lighten(look.hymColor, 0.2f), 255), max(hair, 0.04f * s))
                for (k in -6..6) {
                    val x = k * 0.085f * ri
                    c.drawLine(px(x), py(yBase), px(x * 0.97f), py(yBase - 0.1f * hb), p)
                }
                // chapeau : un dôme qui remplit le haut de l'œuf
                val dome = Path()
                val n = 40
                for (i in 0..n) {
                    val a = PI.toFloat() * i / n
                    val x = 0.66f * ri * cos(a)
                    val y = yBase + 0.3f * hb * sin(a)
                    if (i == 0) dome.moveTo(px(x), py(y)) else dome.lineTo(px(x), py(y))
                }
                dome.close()
                p.reset(); p.isAntiAlias = true
                p.shader = LinearGradient(0f, py(yBase + 0.3f * hb), 0f, py(yBase), lighten(spec.accent, 0.12f), darken(spec.accent, 0.12f), Shader.TileMode.CLAMP)
                c.drawPath(dome, p)
                stroke(withAlpha(darken(spec.accent, 0.5f), 230), hair); c.drawPath(dome, p)
                c.restore()
                stroke(withAlpha(darken(spec.skinColor, 0.4f), 220), hair * 0.9f); c.drawPath(inner, p)
                stroke(outline, hair); c.drawPath(outer, p)
                anchor(Anchor.S_VOLVA, r - spec.skin * 0.5f, yc + 0.3f * rb)
                anchor(Anchor.S_CAVITY, 0.3f * ri, yBase + 0.18f * hb)
                anchor(Anchor.S_FLESH, -0.6f * ri, yi0 + 0.3f * hb)
                anchor(Anchor.S_STIPE, 0f, yi0 + 0.4f * hb)
            }
        }
    }

    // ------------------------------------------------------------------ vue de dessus

    /** La boule vue d'en haut, centrée en (ucx, ucy) pixels, de rayon [ur] pixels. */
    fun top(ucx: Float, ucy: Float, ur: Float) {
        val disc = Path().apply { addOval(RectF(ucx - ur, ucy - ur, ucx + ur, ucy + ur), Path.Direction.CW) }
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx - 0.3f * ur, ucy - 0.3f * ur, ur * 1.5f,
            intArrayOf(look.capCenter, look.capMid, look.capEdge), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(disc, p)
        c.save(); c.clipPath(disc)
        val g = Random(look.seed + 101)
        for (d in look.capDecos) when (d) {
            is CapDeco.Warts -> {
                var placed = 0
                var tries = 0
                while (placed < d.count * 1.4f && tries < d.count * 40) {
                    tries++
                    val u = g.nextFloat() * 2f - 1f
                    val v = g.nextFloat() * 2f - 1f
                    val q = u * u + v * v
                    if (q > 0.97f) continue
                    val depth = sqrt(1f - q)
                    spine(ucx + u * ur, ucy + v * ur, d.size * s * (0.3f + 0.7f * depth), d.color)
                    placed++
                }
            }
            is CapDeco.Cracks -> {
                stroke(withAlpha(d.color, d.alpha), max(hair * 1.2f, ur * 0.016f))
                val rings = max(3, (r / d.cell).toInt() + 1)
                for (k in 1..rings) {
                    val rr = ur * 0.99f * k / rings
                    val ring = Path()
                    for (j in 0..48) {
                        val t = 2f * PI.toFloat() * j / 48f
                        val jr = rr * (1f + (g.nextFloat() - 0.5f) * 0.03f)
                        if (j == 0) ring.moveTo(ucx + jr * cos(t), ucy + jr * sin(t)) else ring.lineTo(ucx + jr * cos(t), ucy + jr * sin(t))
                    }
                    ring.close(); c.drawPath(ring, p)
                }
                for (k in 0 until rings) {
                    val a = ur * 0.99f * k.coerceAtLeast(0) / rings
                    val b = ur * 0.99f * (k + 1) / rings
                    val n = max(6, (2f * PI.toFloat() * b / (d.cell * s)).toInt())
                    for (j in 0 until n) {
                        val t = 2f * PI.toFloat() * (j + 0.5f + (g.nextFloat() - 0.5f) * 0.7f) / n
                        c.drawLine(ucx + max(a, ur * 0.05f) * cos(t), ucy + max(a, ur * 0.05f) * sin(t), ucx + b * cos(t), ucy + b * sin(t), p)
                    }
                }
            }
            is CapDeco.Flakes -> {
                fill(withAlpha(d.color, 215))
                repeat(d.count) {
                    val u = g.nextFloat() * 2f - 1f
                    val v = g.nextFloat() * 2f - 1f
                    if (u * u + v * v > 0.95f) return@repeat
                    val w = d.size * s * (0.4f + g.nextFloat() * 0.8f)
                    val flake = Path()
                    for (k in 0 until 6) {
                        val a = 2f * PI.toFloat() * k / 6
                        val rr = w * (0.35f + g.nextFloat() * 0.35f)
                        val x = ucx + u * ur + rr * cos(a); val y = ucy + v * ur + rr * 0.8f * sin(a)
                        if (k == 0) flake.moveTo(x, y) else flake.lineTo(x, y)
                    }
                    flake.close(); c.drawPath(flake, p)
                }
            }
            else -> {}
        }
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx - 0.35f * ur, ucy - 0.35f * ur, ur * 0.7f, withAlpha(Color.WHITE, 95), 0, Shader.TileMode.CLAMP)
        c.drawPath(disc, p)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx, ucy, ur, intArrayOf(0, 0, withAlpha(Color.BLACK, 70)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(disc, p)
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(disc, p)
        anchors[Anchor.U_RIM] = PointF(ucx + ur * 0.72f, ucy - ur * 0.68f)
        anchors[Anchor.U_HYM] = PointF(ucx - ur * 0.3f, ucy + ur * 0.35f)
        anchors[Anchor.U_CENTER] = PointF(ucx, ucy)
    }
}
