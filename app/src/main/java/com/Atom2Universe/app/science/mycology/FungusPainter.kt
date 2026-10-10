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
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Papier et encre des planches : fixes, quel que soit le thème, pour que les couleurs du champignon restent justes. */
object PlateColors {
    val PAPER = 0xFFF2E9D4.toInt()
    val PAPER_EDGE = 0xFFE3D6B7.toInt()
    val INK = 0xFF3A2B1F.toInt()
}

internal fun mixColor(a: Int, b: Int, t: Float) = ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f))
internal fun lighten(c: Int, t: Float) = mixColor(c, Color.WHITE, t)
internal fun darken(c: Int, t: Float) = mixColor(c, Color.BLACK, t)
internal fun withAlpha(c: Int, a: Int) = ColorUtils.setAlphaComponent(c, a.coerceIn(0, 255))
private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun hypot2(x: Float, y: Float) = sqrt(x * x + y * y)

/**
 * Dessine un champignon en trois vues. Tout est en centimètres (axe du pied x = 0, sol y = 0, y vers le haut),
 * converti en pixels par [s] (px par cm) autour du point (ox, oy) = pied au sol.
 *
 * Le dessus du chapeau est vu d'un peu en dessous : on voit ainsi le dessous (lames, pores) en ellipse,
 * comme dans une planche de détermination. C'est un schéma, pas une photo.
 */
internal class SpecimenPainter(
    private val c: Canvas,
    private val look: FungusLook,
    private val ox: Float,
    private val oy: Float,
    private val s: Float,
    private val hair: Float
) {
    val anchors = HashMap<Anchor, PointF>()
    private val rnd = Random(look.seed)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = look.capDiam / 2f
    private val cx = look.lean
    private val y0 = look.stipeH - look.edgeDrop
    private val ry = r * look.tilt
    private val outline = withAlpha(darken(look.capMid, 0.62f), 240)
    private val stipeOutline = withAlpha(darken(look.stipeTop, 0.6f), 230)

    private fun px(x: Float) = ox + x * s
    private fun py(y: Float) = oy - y * s
    private fun anchor(a: Anchor, x: Float, y: Float) { anchors[a] = PointF(px(x), py(y)) }

    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, w: Float) = p.apply {
        reset(); isAntiAlias = true; style = Paint.Style.STROKE; this.color = color
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    // ------------------------------------------------------------------ géométrie du pied

    private fun axisAt(y: Float): Float {
        val t = (y / look.stipeH).coerceIn(0f, 1f)
        return look.lean * t * t
    }

    private fun shaftHalf(y: Float): Float {
        val t = (y / look.stipeH).coerceIn(0f, 1f)
        val top = look.stipeW / 2f
        val base = look.stipeBaseW / 2f
        return when (look.stipeForm) {
            StipeForm.CLUB -> top + (base - top) * (1f - t).pow(2.2f)
            StipeForm.FLARED -> top + (base - top) * (1f - t).pow(3f)
            StipeForm.TAPER_DOWN -> base + (top - base) * t.pow(0.8f)
            StipeForm.BARREL -> {
                // un tonneau : large vers le tiers bas, rétréci en haut, base presque plate
                val peak = 0.3f
                val maxW = look.bulbW / 2f
                val f = if (t < peak) lerp(0.72f, 1f, sin(t / peak * PI.toFloat() / 2f))
                else cos((t - peak) / (1f - peak) * PI.toFloat() / 2f).coerceAtLeast(0f).pow(1.3f)
                top + (maxW - top) * f
            }
            else -> lerp(base, top, t)
        }
    }

    /** La plus grande demi-largeur du pied (bulbe compris). */
    private val maxHalf: Float by lazy {
        var m = 0f
        var y = 0f
        while (y <= look.stipeH) { m = max(m, stipeHalf(y)); y += 0.1f }
        m
    }

    private fun stipeHalf(y: Float): Float {
        var w = shaftHalf(y)
        if (look.bulbW > 0f && look.stipeForm != StipeForm.BARREL && y < look.bulbH) {
            val bw = look.bulbW / 2f
            val bh = look.bulbH
            val wb = if (look.stipeForm == StipeForm.MARGINATE) {
                val u = (bh - y) / bh
                bw * sqrt(max(0f, 1f - u * u))
            } else {
                val u = (y - bh / 2f) / (bh / 2f)
                bw * sqrt(max(0f, 1f - u * u))
            }
            w = max(w, wb)
        }
        if (y < 0.2f) w *= max(0.6f, sqrt(max(0f, 1f - ((0.2f - y) / 0.2f).pow(2))))
        return w
    }

    /** Le pied jusqu'à la hauteur [yTop], terminé par l'arc de raccord avec le chapeau (vu d'en dessous). */
    private fun stipePath(yTop: Float, inset: Float = 0f, arc: Boolean = true): Path {
        val path = Path()
        val n = 48
        for (i in 0..n) {
            val y = yTop * i / n
            val x = axisAt(y) - max(0.02f, stipeHalf(y) - inset)
            if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
        }
        val hw = max(0.02f, stipeHalf(yTop) - inset)
        val mid = axisAt(yTop)
        if (arc) {
            for (k in 1..14) {
                val b = PI.toFloat() * k / 14f
                path.lineTo(px(mid - hw * cos(b)), py(yTop + hw * look.tilt * sin(b)))
            }
        } else path.lineTo(px(mid + hw), py(yTop))
        for (i in n downTo 0) {
            val y = yTop * i / n
            path.lineTo(px(axisAt(y) + max(0.02f, stipeHalf(y) - inset)), py(y))
        }
        path.close()
        return path
    }

    // ------------------------------------------------------------------ géométrie du chapeau

    private fun capTopY(x: Float): Float {
        val u = (abs(x) / r).coerceIn(0f, 1f)
        val v = (1f - u.pow(look.capN)).coerceAtLeast(0f).pow(1f / look.capN)
        var y = y0 + (look.capRise + look.edgeDrop) * v
        if (look.umbo > 0f) y += look.umbo * exp(-(x / (0.22f * r)).pow(2))
        if (look.dip > 0f) y -= look.dip * exp(-(x / (0.34f * r)).pow(2))
        return y
    }

    private fun domePath(): Path {
        val path = Path()
        val n = 90
        for (i in 0..n) {
            val a = PI.toFloat() * i / n
            val cs = cos(a)
            val x = r * sign(cs) * abs(cs).pow(2f / look.capN)
            val sx = px(cx + x)
            val sy = py(capTopY(x))
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        for (k in 0..36) {
            val b = PI.toFloat() * k / 36f
            path.lineTo(px(cx - r * cos(b)), py(y0 + ry * sin(b)))
        }
        path.close()
        return path
    }

    /** Un point de la couronne de rayon [rho]·r, à l'angle [t] ∈ [0, π] sur la moitié visible du dôme. */
    private fun ringPoint(rho: Float, t: Float): PointF {
        val rr = rho * r
        val x = rr * cos(t)
        val yc = capTopY(rr)
        val y = yc + look.tilt * 0.55f * rr * sin(t)
        return PointF(px(cx + x), py(y))
    }

    // ------------------------------------------------------------------ vue de côté

    fun side() {
        if (look.capShape == CapShape.BRACKET) { BracketPainter(c, look, ox, oy, s, hair, anchors).side(); return }
        if (look.onWood) { TuftPainter(c, look, ox, oy, s, hair, anchors).side(); return }
        drawGround()
        if (look.capShape == CapShape.BALL) { BallPainter(c, look, ox, oy, s, hair, anchors).side(); return }
        if (look.capShape == CapShape.CORAL) { CoralPainter(c, look, ox, oy, s, hair, anchors).side(); return }
        if (look.capShape == CapShape.FUNNEL) { funnelSide(); return }
        if (look.capShape == CapShape.MOREL) { morelSide(); return }
        if (look.capShape == CapShape.BRAIN) { brainSide(); return }
        if (look.cluster > 1) clusterBehind()
        drawFace()
        drawStipe()
        drawRing()
        drawVolva()
        drawDome()
        sideAnchors()
    }

    /** Le champignon seul, sans sol ni voisins, repères compris : ce que le bouquet sur le tronc dessine pour chacun de ses membres. */
    internal fun drawMember(funnel: Boolean) {
        if (funnel) funnelOne() else { single(); sideAnchors() }
    }

    private fun clusterBehind() {
        val sub = listOf(Triple(-0.20f, 0.82f, -9f), Triple(0.24f, 0.7f, 11f), Triple(-0.05f, 0.62f, 3f))
        for ((dx, k, deg) in sub.take(look.cluster - 1)) {
            c.save()
            val bx = px(dx * look.capDiam)
            c.rotate(deg, bx, oy)
            c.translate(bx - ox, 0f)
            c.scale(k, k, ox, oy)
            val other = SpecimenPainter(c, look.asSingle(), ox, oy, s, hair)
            other.single()
            c.restore()
        }
    }

    private fun single() {
        drawFace(); drawStipe(); drawRing(); drawVolva(); drawDome()
    }

    private fun drawGround() {
        val g = Random(7)
        val w = max(look.capDiam, look.stipeBaseW * 2f + look.bulbW) * 0.6f
        // une ombre de terre sous le pied, qui se fond dans le papier
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(0f), py(-0.1f), w * s,
            intArrayOf(withAlpha(0xFF6B5638.toInt(), 95), withAlpha(0xFF6B5638.toInt(), 40), 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.1f, px(0f), py(-0.1f))
        c.drawCircle(px(0f), py(-0.1f), w * s, p)
        c.restore()
        // la ligne du sol, qui s'efface sur les côtés
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-w), 0f, px(w), 0f,
            intArrayOf(0, withAlpha(0xFF5A4630.toInt(), 170), withAlpha(0xFF5A4630.toInt(), 170), 0), floatArrayOf(0f, 0.25f, 0.75f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE; p.strokeWidth = hair
        c.drawLine(px(-w), py(0f), px(w), py(0f), p)
        // brins d'herbe
        stroke(withAlpha(0xFF6F8048.toInt(), 150), hair * 1.1f)
        repeat(9) {
            val gx = (g.nextFloat() * 2f - 1f) * w * 0.9f
            if (abs(gx) < max(look.stipeBaseW, look.bulbW) * 0.8f) return@repeat
            val h = 0.5f + g.nextFloat() * 0.8f
            c.drawLine(px(gx), py(0f), px(gx + (g.nextFloat() - 0.5f) * 0.5f), py(h), p)
        }
    }

    private fun drawFace() {
        if (look.hymenium == Hymenium.NONE) return
        val rx = r * 0.985f
        val oval = RectF(px(cx - rx), py(y0 + ry), px(cx + rx), py(y0 - ry))
        val base = look.hymColor
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx), py(y0), rx * s,
            intArrayOf(darken(base, 0.4f), darken(base, 0.12f), base), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(oval, p)
        c.save()
        val clip = Path().apply { addOval(oval, Path.Direction.CW) }
        c.clipPath(clip)
        when (look.hymenium) {
            Hymenium.GILLS -> faceGills(rx)
            Hymenium.PORES -> facePores(rx)
            Hymenium.RIDGES -> faceGills(rx)
            Hymenium.TEETH -> facePores(rx)
            Hymenium.NONE -> {}
        }
        c.restore()
        stroke(withAlpha(darken(base, 0.6f), 200), hair)
        c.drawOval(oval, p)
    }

    private fun faceGills(rx: Float) {
        val n = (26 + 30 * look.crowd).roundToInt()
        val hw = stipeHalf(y0)
        val collar = when (look.attach) {
            GillAttach.FREE -> hw + rx * 0.09f
            else -> hw * 0.9f
        }
        val line = withAlpha(darken(look.hymColor, 0.42f), 150)
        val edge = withAlpha(lighten(look.hymColor, 0.35f), 120)
        for (j in 0 until n) {
            val a = 2f * PI.toFloat() * j / n
            val short = j % 2 == 1
            val r0 = if (short) rx * 0.45f else collar
            val ca = cos(a); val sa = sin(a)
            val x1 = cx + r0 * ca; val y1 = y0 + r0 * look.tilt * sa
            val x2 = cx + rx * ca; val y2 = y0 + rx * look.tilt * sa
            stroke(line, hair * 0.75f)
            if (look.forked && !short) {
                val mx = cx + rx * 0.55f * ca; val my = y0 + rx * 0.55f * look.tilt * sa
                c.drawLine(px(x1), py(y1), px(mx), py(my), p)
                for (sgn in intArrayOf(-1, 1)) {
                    val b = a + sgn * 0.07f
                    c.drawLine(px(mx), py(my), px(cx + rx * cos(b)), py(y0 + rx * look.tilt * sin(b)), p)
                }
            } else c.drawLine(px(x1), py(y1), px(x2), py(y2), p)
            stroke(edge, hair * 0.5f)
            val a2 = a + 0.5f * PI.toFloat() * 2f / n * 0.5f
            c.drawLine(px(cx + (r0 + 0.1f) * cos(a2)), py(y0 + (r0 + 0.1f) * look.tilt * sin(a2)),
                px(cx + rx * cos(a2)), py(y0 + rx * look.tilt * sin(a2)), p)
        }
    }

    private fun facePores(rx: Float) {
        val dot = withAlpha(darken(look.hymColor, 0.5f), 150)
        val spacing = max(0.08f, look.capDiam * 0.012f)
        fill(dot)
        var rho = 0.22f
        while (rho < 0.995f) {
            val rr = rho * r
            val count = max(8, (2f * PI.toFloat() * rr / spacing).roundToInt())
            for (j in 0 until count) {
                val a = 2f * PI.toFloat() * (j + (rho * 37f) % 1f) / count
                c.drawCircle(px(cx + rr * cos(a)), py(y0 + rr * look.tilt * sin(a)), hair * 0.55f, p)
            }
            rho += spacing / r
        }
    }

    private fun stipeShade(topY: Float): Paint {
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(0f), 0f, py(topY), look.stipeBottom, look.stipeTop, Shader.TileMode.CLAMP)
        return p
    }

    private fun drawStipe() {
        val path = stipePath(y0)
        c.drawPath(path, stipeShade(y0))
        c.save(); c.clipPath(path)
        stipeDecos()
        decurrentRuns()
        // volume : ombre à droite, lumière à gauche
        val w = maxHalf + 0.1f
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-w), 0f, px(w), 0f,
            intArrayOf(withAlpha(Color.BLACK, 70), 0, withAlpha(Color.WHITE, 55), 0, withAlpha(Color.BLACK, 95)),
            floatArrayOf(0f, 0.14f, 0.34f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-w - 0.5f), py(y0 + 0.5f), px(w + 0.5f), py(-0.1f), p)
        c.restore()
        stroke(stipeOutline, hair * 1.1f)
        c.drawPath(path, p)
        stainsSide()
    }

    private fun decurrentRuns() {
        if (look.attach != GillAttach.DECURRENT || look.hymenium == Hymenium.NONE) return
        val n = 9
        val len = min(look.stipeH * 0.4f, 1.6f)
        stroke(withAlpha(look.hymColor, 220), hair * 1.2f)
        for (j in 0 until n) {
            val t = -1f + 2f * (j + 0.5f) / n
            val hw = stipeHalf(y0)
            val x = axisAt(y0) + t * hw * 0.92f
            c.drawLine(px(x), py(y0), px(x * 0.9f), py(y0 - len * (1f - abs(t) * 0.5f)), p)
        }
    }

    private fun stipeDecos() {
        for (d in look.stipeDecos) when (d) {
            is StipeDeco.Net -> {
                val yTop = y0
                val yLow = y0 - look.stipeH * d.from
                val cell = d.cell
                p.reset(); p.isAntiAlias = true
                p.shader = LinearGradient(0f, py(yTop), 0f, py(yLow), withAlpha(d.color, d.alpha), withAlpha(d.color, 0), Shader.TileMode.CLAMP)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(hair * 0.9f, cell * s * 0.07f)
                val wMax = look.stipeBaseW + (look.bulbW)
                var x = -wMax - (yTop - yLow) * 0.45f
                while (x < wMax + (yTop - yLow) * 0.45f) {
                    c.drawLine(px(x), py(yTop), px(x + (yTop - yLow) * 0.45f), py(yLow), p)
                    c.drawLine(px(x), py(yLow), px(x + (yTop - yLow) * 0.45f), py(yTop), p)
                    x += cell
                }
            }
            is StipeDeco.Bands -> {
                stroke(withAlpha(d.color, d.alpha), max(hair * 1.2f, 0.13f * s))
                val ring = if (look.ring != RingKind.NONE) look.ringAt * y0 else y0
                var y = 0.7f
                val g = Random(look.seed + 3)
                while (y < y0 - 0.2f) {
                    val half = stipeHalf(y)
                    val steps = 6
                    var prevX = axisAt(y) - half; var prevY = y
                    for (k in 1..steps) {
                        val nx = axisAt(y) - half + 2f * half * k / steps
                        val ny = y + (if (k % 2 == 0) 0.17f else -0.17f) * (0.7f + g.nextFloat() * 0.6f)
                        if (g.nextFloat() > 0.12f) c.drawLine(px(prevX), py(prevY), px(nx), py(ny), p)
                        prevX = nx; prevY = ny
                    }
                    y += d.gap
                }
                if (ring < 0f) return
            }
            is StipeDeco.Fibrils -> {
                stroke(withAlpha(d.color, d.alpha), hair * 0.9f)
                val g = Random(look.seed + 5)
                var y = 0.3f
                while (y < y0) {
                    val half = stipeHalf(y)
                    var x = axisAt(y) - half
                    while (x < axisAt(y) + half) {
                        val len = 0.25f + g.nextFloat() * 0.6f
                        val wob = (g.nextFloat() - 0.5f) * 0.18f
                        c.drawLine(px(x), py(y), px(x + len), py(y + wob), p)
                        x += len + g.nextFloat() * 0.25f
                    }
                    y += 0.22f
                }
            }
            is StipeDeco.Scales -> {
                fill(withAlpha(d.color, 215))
                val g = Random(look.seed + 9)
                val top = look.stipeH * d.from
                var y = 0.1f
                while (y < top) {
                    val half = stipeHalf(y)
                    var x = axisAt(y) - half
                    while (x < axisAt(y) + half) {
                        val w = 0.12f + g.nextFloat() * 0.1f
                        c.drawOval(RectF(px(x), py(y + w * 0.9f), px(x + w * 1.6f), py(y)), p)
                        x += w * 1.8f
                    }
                    y += 0.17f
                }
            }
            is StipeDeco.Dots -> {
                val g = Random(look.seed + 7)
                fill(withAlpha(d.color, d.alpha))
                var y = if (d.top < 1f) y0 * (1f - d.top) else 0.2f
                var line = 0
                while (y < y0) {
                    val half = stipeHalf(y)
                    var x = axisAt(y) - half + (if (line % 2 == 0) 0f else d.spacing * 0.5f)
                    while (x < axisAt(y) + half) {
                        val w = d.size * (0.7f + g.nextFloat() * 0.6f)
                        c.drawOval(RectF(px(x), py(y + w * 0.6f), px(x + w), py(y - w * 0.4f)), p)
                        x += d.spacing * (0.8f + g.nextFloat() * 0.4f)
                    }
                    y += d.spacing * 0.8f
                    line++
                }
            }
            is StipeDeco.Furrows -> {
                stroke(withAlpha(d.color, 150), hair * 1.2f)
                for (k in 1..4) {
                    val t = -1f + 2f * k / 5f
                    var prev: PointF? = null
                    var y = 0f
                    while (y <= y0) {
                        val x = axisAt(y) + t * stipeHalf(y) * 0.85f + sin(y * 5f + k) * 0.05f
                        val q = PointF(px(x), py(y))
                        prev?.let { c.drawLine(it.x, it.y, q.x, q.y, p) }
                        prev = q; y += 0.2f
                    }
                }
            }
        }
    }

    private fun stainsSide() {
        for (st in look.stains) {
            when (st.zone) {
                StainZone.STIPE_BASE, StainZone.STIPE_ALL, StainZone.STIPE_TOP -> {
                    val (a, b) = when (st.zone) {
                        StainZone.STIPE_BASE -> 0f to y0 * 0.45f
                        StainZone.STIPE_TOP -> y0 * 0.7f to y0
                        else -> 0f to y0
                    }
                    c.save(); c.clipPath(stipePath(y0))
                    p.reset(); p.isAntiAlias = true
                    val strong = withAlpha(st.color, (st.strength * 150).roundToInt())
                    p.shader = if (st.zone == StainZone.STIPE_BASE)
                        LinearGradient(0f, py(a), 0f, py(b), strong, 0, Shader.TileMode.CLAMP)
                    else LinearGradient(0f, py(a), 0f, py(b), withAlpha(st.color, (st.strength * 110).roundToInt()), withAlpha(st.color, (st.strength * 110).roundToInt()), Shader.TileMode.CLAMP)
                    c.drawRect(px(-3f), py(b), px(3f), py(a), p)
                    c.restore()
                }
                else -> {}
            }
        }
    }

    private fun drawRing() {
        if (look.ring == RingKind.NONE) return
        val yr = look.ringAt * y0
        val hw = stipeHalf(yr)
        val mid = axisAt(yr)
        if (look.ring == RingKind.ZONE) {
            fill(withAlpha(darken(look.stipeBottom, 0.25f), 190))
            c.drawRect(px(mid - hw), py(yr + 0.12f), px(mid + hw), py(yr - 0.12f), p)
            stroke(withAlpha(darken(look.stipeBottom, 0.5f), 200), hair)
            c.drawLine(px(mid - hw), py(yr + 0.12f), px(mid + hw), py(yr + 0.12f), p)
            anchor(Anchor.RING, mid + hw, yr)
            return
        }
        val spread = when (look.ring) {
            RingKind.FUGACIOUS -> look.capDiam * 0.04f
            RingKind.FLOCCOSE -> look.capDiam * 0.125f
            RingKind.SLIDING -> look.capDiam * 0.085f
            else -> look.capDiam * 0.11f
        }
        val drop = when (look.ring) {
            RingKind.FUGACIOUS -> look.capDiam * 0.045f
            RingKind.SLIDING -> look.capDiam * 0.06f
            else -> look.capDiam * 0.115f
        }
        fun skirt(y: Float, extra: Float, dropK: Float) {
            val rw = hw + spread + extra
            val dr = drop * dropK
            val path = Path()
            path.moveTo(px(mid - hw), py(y))
            path.cubicTo(px(mid - hw - spread * 0.3f), py(y - dr * 0.2f), px(mid - rw), py(y - dr * 0.55f), px(mid - rw), py(y - dr))
            path.quadTo(px(mid), py(y - dr - dr * 0.38f), px(mid + rw), py(y - dr))
            path.cubicTo(px(mid + rw), py(y - dr * 0.55f), px(mid + hw + spread * 0.3f), py(y - dr * 0.2f), px(mid + hw), py(y))
            path.quadTo(px(mid), py(y - hw * look.tilt * 1.2f), px(mid - hw), py(y))
            path.close()
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(y), 0f, py(y - dr * 1.3f),
                lighten(look.ringColor, 0.15f), darken(look.ringColor, 0.12f), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
            c.save(); c.clipPath(path)
            if (look.ring == RingKind.STRIATE || look.ring == RingKind.SKIRT) {
                stroke(withAlpha(darken(look.ringColor, 0.4f), if (look.ring == RingKind.STRIATE) 120 else 55), hair * 0.7f)
                val lines = if (look.ring == RingKind.STRIATE) 16 else 7
                for (k in 0..lines) {
                    val t = k / lines.toFloat()
                    val xt = mid + (-rw + 2f * rw * t)
                    c.drawLine(px(mid + (-hw + 2f * hw * t)), py(y), px(xt), py(y - dr * 1.1f), p)
                }
            }
            if (look.ring == RingKind.FLOCCOSE) {
                fill(withAlpha(darken(look.ringColor, 0.28f), 190))
                var x = mid - rw
                while (x < mid + rw) {
                    val q = 1f - ((x - mid) / rw).pow(2)
                    val ly = y - dr - dr * 0.38f * 0.5f * (1f - ((x - mid) / rw).pow(2)) * 2f * 0.5f
                    c.drawOval(RectF(px(x), py(ly + 0.12f * q + 0.05f), px(x + 0.28f), py(ly - 0.1f)), p)
                    x += 0.34f
                }
            }
            c.restore()
            stroke(withAlpha(darken(look.ringColor, 0.58f), 210), hair)
            c.drawPath(path, p)
        }
        if (look.ring == RingKind.SLIDING) {
            skirt(yr + drop * 0.75f, -spread * 0.35f, 0.7f)
            skirt(yr, 0f, 1f)
        } else skirt(yr, 0f, 1f)
        anchor(Anchor.RING, mid + hw + spread, yr - drop * 0.6f)
    }

    private fun drawVolva() {
        if (look.volva == VolvaKind.NONE) return
        when (look.volva) {
            VolvaKind.SAC -> {
                val h = look.volvaH
                fun half(y: Float): Float {
                    val inner = stipeHalf(y)
                    val flare = smoothstep(h * 0.55f, h, y) * look.stipeW * 0.55f
                    return inner + 0.14f + flare * 0.9f + (if (y < h * 0.35f) 0.12f else 0f)
                }
                val path = Path()
                val n = 36
                for (i in 0..n) {
                    val y = h * i / n
                    val lobe = if (y > h * 0.85f) 0.13f * sin(y * 16f) else 0f
                    val x = axisAt(y) - half(y) - lobe
                    if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
                }
                val topHalf = half(h)
                path.quadTo(px(axisAt(h)), py(h - 0.28f), px(axisAt(h) + topHalf), py(h))
                for (i in n downTo 0) {
                    val y = h * i / n
                    val lobe = if (y > h * 0.85f) 0.13f * sin(y * 16f + 2f) else 0f
                    path.lineTo(px(axisAt(y) + half(y) + lobe), py(y))
                }
                path.close()
                p.reset(); p.isAntiAlias = true
                p.shader = LinearGradient(px(-topHalf), 0f, px(topHalf), 0f,
                    intArrayOf(darken(look.volvaColor, 0.1f), lighten(look.volvaColor, 0.15f), darken(look.volvaColor, 0.16f)),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                p.alpha = 245
                c.drawPath(path, p)
                c.save(); c.clipPath(path)
                stroke(withAlpha(darken(look.volvaColor, 0.4f), 90), hair * 0.8f)
                for (k in 1..5) {
                    val t = -1f + 2f * k / 6f
                    c.drawLine(px(t * look.bulbW * 0.4f), py(0.15f), px(t * topHalf * 0.9f), py(h), p)
                }
                c.restore()
                stroke(withAlpha(darken(look.volvaColor, 0.62f), 220), hair)
                c.drawPath(path, p)
                anchor(Anchor.VOLVA, axisAt(h * 0.6f) + half(h * 0.6f), h * 0.55f)
            }
            VolvaKind.RIMS -> {
                // Bulbe orné de bourrelets : une collerette nette, puis un ou deux bracelets en écailles.
                val bh = look.bulbH
                val bw = look.bulbW / 2f
                stroke(withAlpha(look.volvaColor, 245), max(hair * 1.8f, 0.1f * s))
                val levels = floatArrayOf(bh * 0.92f, bh * 1.14f, bh * 1.34f)
                for ((idx, y) in levels.withIndex()) {
                    val half = stipeHalf(y) * (if (idx == 0) 1.12f else 1.05f) + 0.04f
                    val path = Path()
                    path.moveTo(px(-half), py(y + 0.02f))
                    path.quadTo(px(0f), py(y - half * look.tilt * 2.4f), px(half), py(y + 0.02f))
                    c.drawPath(path, p)
                }
                stroke(withAlpha(darken(look.volvaColor, 0.5f), 110), hair * 0.8f)
                for (y in levels) {
                    val half = stipeHalf(y) * 1.06f
                    val path = Path()
                    path.moveTo(px(-half), py(y - 0.06f))
                    path.quadTo(px(0f), py(y - 0.06f - half * look.tilt * 2.4f), px(half), py(y - 0.06f))
                    c.drawPath(path, p)
                }
                anchor(Anchor.VOLVA, bw + 0.05f, bh * 0.95f)
            }
            VolvaKind.NONE -> {}
        }
    }

    private fun drawDome() {
        val path = domePath()
        val apex = look.stipeH + look.capRise
        val rad = sqrt(r * r + look.capRise * look.capRise) * s
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx), py(apex - look.capRise * 0.15f), rad * 1.05f,
            intArrayOf(look.capCenter, look.capMid, look.capEdge), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        capDecos()
        stainsCap()
        // lumière en haut à gauche, ombre vers la marge
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx - r * 0.35f), py(apex - look.capRise * 0.25f), r * s * 0.7f,
            withAlpha(Color.WHITE, 85), 0, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(y0 + ry * 1.9f), 0f, py(y0 - ry * 0.2f), 0, withAlpha(Color.BLACK, 70), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        if (look.inrolled) {
            stroke(withAlpha(lighten(look.capEdge, 0.3f), 150), max(hair * 2.2f, r * s * 0.07f))
            val rim = Path()
            for (k in 0..36) {
                val b = PI.toFloat() * k / 36f
                val x = px(cx - r * 0.97f * cos(b)); val y = py(y0 + ry * 0.97f * sin(b) + 0.02f)
                if (k == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
            }
            c.drawPath(rim, p)
        }
        c.restore()
        stroke(outline, hair * 1.2f)
        c.drawPath(path, p)
        // l'arête de la marge
        stroke(withAlpha(darken(look.capEdge, 0.5f), 200), hair * 1.1f)
        val rim = Path()
        for (k in 0..36) {
            val b = PI.toFloat() * k / 36f
            val x = px(cx - r * cos(b)); val y = py(y0 + ry * sin(b))
            if (k == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
        }
        c.drawPath(rim, p)
    }

    private fun capDecos() {
        for (d in look.capDecos) when (d) {
            is CapDeco.Warts -> {
                val rings = max(2, sqrt(d.count / 2.4f).roundToInt())
                val weights = (0 until rings).map { (it + 0.8f) / (rings + 0.2f) }
                val total = weights.sum()
                val g = Random(look.seed + 11)
                for ((i, rho) in weights.withIndex()) {
                    val n = max(2, (d.count * rho / total).roundToInt())
                    for (j in 0 until n) {
                        val t = PI.toFloat() * (j + 0.2f + g.nextFloat() * 0.6f) / n
                        val pt = ringPoint(min(rho, 0.97f), t)
                        val k = 0.45f + 0.55f * sin(t)
                        val w = d.size * s * k * (0.8f + g.nextFloat() * 0.4f)
                        fill(withAlpha(darken(d.color, 0.18f), 120))
                        c.drawOval(RectF(pt.x - w * 0.5f + hair * 0.5f, pt.y - w * 0.32f + hair * 0.5f, pt.x + w * 0.5f + hair * 0.5f, pt.y + w * 0.32f + hair * 0.5f), p)
                        fill(withAlpha(d.color, 240))
                        c.drawOval(RectF(pt.x - w * 0.5f, pt.y - w * 0.34f, pt.x + w * 0.5f, pt.y + w * 0.3f), p)
                        if (i < 0) break
                    }
                }
            }
            is CapDeco.Scales -> {
                val g = Random(look.seed + 13)
                for (row in 0 until d.rows) {
                    val rho = d.from + (0.97f - d.from) * (if (d.rows == 1) 0f else row / (d.rows - 1f))
                    val n = max(4, (PI.toFloat() * rho * r / (d.size * 1.05f)).roundToInt())
                    for (j in 0 until n) {
                        val t = PI.toFloat() * (j + 0.5f + (g.nextFloat() - 0.5f) * 0.3f) / n
                        val pt = ringPoint(rho, t)
                        val k = 0.5f + 0.5f * sin(t)
                        val w = d.size * s * k * (0.85f + g.nextFloat() * 0.3f)
                        val dir = atan2(-1f, cos(t) * 0.7f)
                        c.save(); c.translate(pt.x, pt.y); c.rotate(Math.toDegrees(dir.toDouble()).toFloat() + 90f)
                        val sc = Path().apply {
                            moveTo(-w * 0.55f, -w * 0.15f); quadTo(0f, -w * 0.55f, w * 0.55f, -w * 0.15f)
                            lineTo(w * 0.2f, w * 0.7f); lineTo(-w * 0.2f, w * 0.7f); close()
                        }
                        fill(withAlpha(d.color, 235)); c.drawPath(sc, p)
                        stroke(withAlpha(darken(d.color, 0.45f), 160), hair * 0.6f); c.drawPath(sc, p)
                        c.restore()
                    }
                }
            }
            is CapDeco.Shaggy -> {
                val g = Random(look.seed + 23)
                val total = look.capRise + look.edgeDrop
                // du sommet vers la marge : chaque étage recouvre la base de celui du dessus
                for (row in d.rows - 1 downTo 0) {
                    val f = (row + 0.5f) / d.rows
                    val v = f * 0.97f
                    val h = y0 + total * v
                    val u = (1f - v.pow(look.capN)).coerceAtLeast(0.02f).pow(1f / look.capN)
                    val n = max(4, (PI.toFloat() * u * r / (d.size * 0.8f)).roundToInt())
                    val col = mixColor(lighten(d.color, 0.5f), d.color, f)
                    for (j in 0 until n) {
                        val t = PI.toFloat() * (j + 0.5f + (g.nextFloat() - 0.5f) * 0.5f) / n
                        val bx = px(cx + u * r * cos(t))
                        val by = py(h + look.tilt * 0.55f * u * r * sin(t))
                        val w = d.size * s * (0.55f + 0.45f * sin(t)) * (0.85f + g.nextFloat() * 0.3f)
                        val out = cos(t) * 0.5f
                        val tuft = Path().apply {
                            moveTo(bx - w * 0.5f, by)
                            quadTo(bx + (out - 0.25f) * w, by - w * 0.7f, bx + (out + 0.3f) * w, by - w * 1.15f)
                            quadTo(bx + (out + 0.15f) * w, by - w * 0.45f, bx + w * 0.5f, by)
                            close()
                        }
                        fill(withAlpha(col, 240)); c.drawPath(tuft, p)
                        stroke(withAlpha(darken(col, 0.4f), 150), hair * 0.6f); c.drawPath(tuft, p)
                    }
                }
            }
            is CapDeco.Fibrils -> {
                val g = Random(look.seed + 17)
                for (j in 0 until d.count) {
                    val t = PI.toFloat() * (j + 0.5f) / d.count
                    stroke(withAlpha(d.color, d.alpha), hair * (0.8f + g.nextFloat() * 0.6f))
                    val path = Path()
                    var rho = 0.06f + g.nextFloat() * 0.12f
                    var first = true
                    while (rho < 0.97f) {
                        val pt = ringPoint(rho, t + (g.nextFloat() - 0.5f) * 0.05f)
                        if (first) { path.moveTo(pt.x, pt.y); first = false } else path.lineTo(pt.x, pt.y)
                        rho += 0.12f
                    }
                    c.drawPath(path, p)
                }
            }
            is CapDeco.Flakes -> {
                val g = Random(look.seed + 19)
                fill(withAlpha(d.color, 215))
                repeat(d.count) {
                    val rho = 0.1f + g.nextFloat() * 0.85f
                    val t = PI.toFloat() * (0.06f + 0.88f * g.nextFloat())
                    val pt = ringPoint(rho, t)
                    val w = d.size * s * (0.4f + g.nextFloat() * 0.8f) * (0.5f + 0.5f * sin(t))
                    val flake = Path()
                    val pts = 6
                    for (k in 0 until pts) {
                        val a = 2f * PI.toFloat() * k / pts
                        val rr = w * (0.35f + g.nextFloat() * 0.35f)
                        val x = pt.x + rr * cos(a); val y = pt.y + rr * 0.7f * sin(a)
                        if (k == 0) flake.moveTo(x, y) else flake.lineTo(x, y)
                    }
                    flake.close(); c.drawPath(flake, p)
                }
            }
            is CapDeco.Cracks -> {
                val g = Random(look.seed + 31)
                stroke(withAlpha(d.color, d.alpha), max(hair * 1.1f, r * s * 0.014f))
                val rings = max(3, (r / d.cell).roundToInt())
                fun rhoOf(k: Int) = 0.97f * k / rings
                // des couronnes irrégulières, reliées par de courts traits : un pavage de plaques
                for (k in 1..rings) {
                    val ring = Path()
                    for (j in 0..40) {
                        val pt = ringPoint(rhoOf(k) + (g.nextFloat() - 0.5f) * 0.03f, PI.toFloat() * j / 40f)
                        if (j == 0) ring.moveTo(pt.x, pt.y) else ring.lineTo(pt.x, pt.y)
                    }
                    c.drawPath(ring, p)
                }
                for (k in 0 until rings) {
                    val n = max(5, (PI.toFloat() * max(rhoOf(k + 1), 0.2f) * r / d.cell).roundToInt())
                    for (j in 0 until n) {
                        val t = PI.toFloat() * (j + 0.5f + (g.nextFloat() - 0.5f) * 0.7f) / n
                        val a = ringPoint(rhoOf(k).coerceAtLeast(0.05f), t)
                        val b = ringPoint(rhoOf(k + 1), t + (g.nextFloat() - 0.5f) * 0.12f)
                        c.drawLine(a.x, a.y, b.x, b.y, p)
                    }
                }
            }
            is CapDeco.Zones -> {
                val band = max(hair * 2.4f, r * s * 0.035f)
                for (k in 1..d.count) {
                    val rho = 0.12f + 0.84f * k / (d.count + 0.5f)
                    stroke(withAlpha(d.color, d.alpha), band * (0.8f + 0.4f * k / d.count))
                    val ring = Path()
                    for (j in 0..40) {
                        val pt = ringPoint(rho, PI.toFloat() * j / 40f)
                        if (j == 0) ring.moveTo(pt.x, pt.y) else ring.lineTo(pt.x, pt.y)
                    }
                    c.drawPath(ring, p)
                }
            }
            is CapDeco.Striate -> {
                stroke(withAlpha(darken(look.capEdge, 0.5f), d.alpha), hair * 0.8f)
                val n = 46
                for (j in 0 until n) {
                    val t = PI.toFloat() * (j + 0.5f) / n
                    val a = ringPoint(0.84f, t); val b = ringPoint(0.995f, t)
                    c.drawLine(a.x, a.y, b.x, b.y, p)
                }
            }
            CapDeco.Sticky -> {
                p.reset(); p.isAntiAlias = true
                p.color = withAlpha(Color.WHITE, 95)
                c.drawOval(RectF(px(cx - r * 0.55f), py(look.stipeH + look.capRise * 0.88f), px(cx - r * 0.05f), py(look.stipeH + look.capRise * 0.52f)), p)
            }
            CapDeco.Felt -> {
                stroke(withAlpha(lighten(look.capEdge, 0.35f), 110), max(hair * 2f, r * s * 0.05f))
                val rim = Path()
                for (k in 0..36) {
                    val b = PI.toFloat() * k / 36f
                    val x = px(cx - r * 0.96f * cos(b)); val y = py(y0 + ry * 0.96f * sin(b) + 0.1f)
                    if (k == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
                }
                c.drawPath(rim, p)
            }
        }
    }

    private fun stainsCap() {
        for (st in look.stains) {
            if (st.zone == StainZone.CAP_EDGE || st.zone == StainZone.CAP_SKIN) {
                val edge = st.zone == StainZone.CAP_EDGE
                val g = Random(look.seed + 23)
                stroke(withAlpha(st.color, (st.strength * 200).roundToInt()), max(hair * 2f, 0.16f * s))
                repeat(if (edge) 3 else 2) {
                    val rho = if (edge) 0.8f + g.nextFloat() * 0.15f else 0.45f + g.nextFloat() * 0.4f
                    val t = PI.toFloat() * (0.2f + 0.6f * g.nextFloat())
                    val a = ringPoint(rho, t); val b = ringPoint(min(0.99f, rho + 0.12f), t + 0.2f)
                    c.drawLine(a.x, a.y, b.x, b.y, p)
                }
            }
        }
    }

    private fun sideAnchors() {
        anchor(Anchor.CAP, cx - r * 0.45f, look.stipeH + look.capRise * 0.62f)
        anchor(Anchor.EDGE, cx + r, y0)
        anchor(Anchor.UMBO, cx, look.stipeH + look.capRise + look.umbo)
        anchor(Anchor.FACE, cx + r * 0.5f, y0 - ry * 0.7f)
        anchor(Anchor.BULB, stipeHalf(look.bulbH * 0.5f) , look.bulbH * 0.5f)
        anchor(Anchor.STIPE_U, axisAt(y0 * 0.82f) + stipeHalf(y0 * 0.82f), y0 * 0.82f)
        anchor(Anchor.STIPE_M, axisAt(y0 * 0.5f) + stipeHalf(y0 * 0.5f), y0 * 0.5f)
        anchor(Anchor.BASE, stipeHalf(0.3f), 0.3f)
    }

    // ------------------------------------------------------------------ chanterelles et entonnoirs

    private fun funnelSide() {
        if (look.cluster > 1) clusterFunnelBehind()
        funnelOne()
    }

    private fun clusterFunnelBehind() {
        val sub = listOf(Triple(-0.22f, 0.8f, -10f), Triple(0.26f, 0.68f, 12f))
        for ((dx, k, deg) in sub.take(look.cluster - 1)) {
            c.save()
            val bx = px(dx * look.capDiam)
            c.rotate(deg, bx, oy)
            c.translate(bx - ox, 0f)
            c.scale(k, k, ox, oy)
            SpecimenPainter(c, look.asSingle(), ox, oy, s, hair).funnelOne()
            c.restore()
        }
    }

    private fun rimY(a: Float) = look.stipeH + look.capRise + r * 0.05f * sin(a * 3f + look.seed)

    private fun rimR(a: Float) = r * (1f + 0.045f * sin(a * 5f + 0.8f * look.seed) + 0.03f * sin(a * 9f + 1.7f))

    internal fun funnelOne() {
        val neckY = look.stipeH
        val hw0 = stipeHalf(neckY)
        val rimTop = look.stipeH + look.capRise
        val rimRy = r * 0.34f
        // pied
        val stem = stipePath(neckY + 0.02f, arc = false)
        c.drawPath(stem, stipeShade(neckY))
        c.save(); c.clipPath(stem)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-hw0), 0f, px(hw0), 0f,
            intArrayOf(withAlpha(Color.BLACK, 70), 0, withAlpha(Color.WHITE, 50), 0, withAlpha(Color.BLACK, 90)),
            floatArrayOf(0f, 0.15f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-hw0 - 1f), py(neckY + 1f), px(hw0 + 1f), py(-0.1f), p)
        c.restore()
        stroke(stipeOutline, hair * 1.1f); c.drawPath(stem, p)

        // corps de l'entonnoir : du pied jusqu'à la marge ondulée
        fun bodyHalf(y: Float): Float {
            val t = ((y - neckY) / look.capRise).coerceIn(0f, 1f)
            return lerp(hw0 * 1.05f, r, t.pow(1.55f))
        }
        val body = Path()
        val steps = 24
        for (i in 0..steps) {
            val y = neckY + look.capRise * i / steps
            val x = cx - bodyHalf(y)
            if (i == 0) body.moveTo(px(x), py(y)) else body.lineTo(px(x), py(y))
        }
        // arête avant de la marge (en ∪), ondulée
        val m = 36
        for (k in 0..m) {
            val a = PI.toFloat() * (1f + k.toFloat() / m)
            val rr = rimR(a)
            body.lineTo(px(cx + rr * cos(a)), py(rimTop + rimRy * sin(a) * (1f + 0.12f * sin(a * 7f + look.seed))))
        }
        for (i in steps downTo 0) {
            val y = neckY + look.capRise * i / steps
            body.lineTo(px(cx + bodyHalf(y)), py(y))
        }
        body.close()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(rimTop), 0f, py(neckY), look.capEdge, look.hymColor, Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        c.save(); c.clipPath(body)
        if (look.hymenium != Hymenium.NONE) funnelRidges(neckY, rimTop, rimRy, ::bodyHalf)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-r), 0f, px(r), 0f,
            intArrayOf(withAlpha(Color.BLACK, 70), 0, withAlpha(Color.WHITE, 40), 0, withAlpha(Color.BLACK, 90)),
            floatArrayOf(0f, 0.2f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-r - 1f), py(rimTop + 1f), px(r + 1f), py(neckY - 0.1f), p)
        c.restore()
        stroke(outline, hair * 1.1f); c.drawPath(body, p)

        // l'intérieur de l'entonnoir, vu d'un peu au-dessus
        val inner = Path()
        for (k in 0..m) {
            val a = 2f * PI.toFloat() * k / m
            val rr = rimR(a)
            val x = px(cx + rr * cos(a)); val y = py(rimTop + rimRy * sin(a) * (1f + 0.12f * sin(a * 7f + look.seed)))
            if (k == 0) inner.moveTo(x, y) else inner.lineTo(x, y)
        }
        inner.close()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx), py(rimTop - rimRy * 0.15f), r * s,
            intArrayOf(darken(look.capCenter, 0.2f), look.capCenter, look.capMid, look.capEdge), floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(inner, p)
        c.save(); c.clipPath(inner)
        for (d in look.capDecos) if (d is CapDeco.Felt) {
            stroke(withAlpha(lighten(look.capEdge, 0.35f), 110), max(hair * 2f, r * s * 0.05f))
            c.drawOval(RectF(px(cx - r * 0.9f), py(rimTop + rimRy * 0.9f), px(cx + r * 0.9f), py(rimTop - rimRy * 0.9f)), p)
        }
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(inner, p)
        stroke(withAlpha(lighten(look.capEdge, 0.4f), 120), hair * 2f)
        val lip = Path()
        for (k in 0..m) {
            val a = PI.toFloat() * (1f + k.toFloat() / m)
            val rr = rimR(a)
            val x = px(cx + rr * cos(a)); val y = py(rimTop + rimRy * sin(a) * (1f + 0.12f * sin(a * 7f + look.seed)) + 0.04f)
            if (k == 0) lip.moveTo(x, y) else lip.lineTo(x, y)
        }
        c.drawPath(lip, p)

        anchor(Anchor.CAP, cx - r * 0.35f, rimTop + rimRy * 0.2f)
        anchor(Anchor.EDGE, cx + r, rimTop)
        anchor(Anchor.FACE, cx + r * 0.45f, neckY + look.capRise * 0.45f)
        anchor(Anchor.STIPE_M, hw0, neckY * 0.55f)
        anchor(Anchor.STIPE_U, hw0, neckY * 0.9f)
        anchor(Anchor.BASE, stipeHalf(0.3f), 0.3f)
        anchor(Anchor.BULB, 0f, 0f)
    }

    private fun funnelRidges(neckY: Float, rimTop: Float, rimRy: Float, bodyHalf: (Float) -> Float) {
        val trueGills = look.hymenium == Hymenium.GILLS
        val n = if (trueGills) 34 else 13
        val g = Random(look.seed + 29)
        val hw0 = stipeHalf(neckY)
        val color = withAlpha(lighten(look.hymColor, if (trueGills) 0.25f else 0.18f), if (trueGills) 190 else 215)
        val shadow = withAlpha(darken(look.hymColor, 0.35f), if (trueGills) 120 else 150)
        for (j in 0 until n) {
            val t = (j + 0.5f + (g.nextFloat() - 0.5f) * 0.4f) / n
            val b = PI.toFloat() * t
            val sx = cx - r * 0.97f * cos(b)
            val sy = rimTop - rimRy * sin(b) * 0.95f
            val ex = (-1f + 2f * t) * hw0 * 0.95f
            val ey = neckY - 0.05f
            val bend = (g.nextFloat() - 0.5f) * r * 0.1f
            val cxm = lerp(sx, ex, 0.5f) + bend
            val cym = lerp(sy, ey, 0.5f)
            for ((color, w) in listOf(shadow to hair * 1.8f, color to hair * 1.1f)) {
                stroke(color, if (trueGills) w * 0.7f else w * 1.35f)
                val path = Path()
                path.moveTo(px(sx), py(sy)); path.quadTo(px(cxm), py(cym), px(ex), py(ey))
                c.drawPath(path, p)
            }
            if (!trueGills || look.forked) {
                // la fourche, à mi-hauteur
                val mx = cxm; val my = cym
                for (sgn in intArrayOf(-1, 1)) {
                    stroke(color, hair * (if (trueGills) 0.8f else 1.4f))
                    val path = Path()
                    path.moveTo(px(mx), py(my)); path.lineTo(px(lerp(mx, sx, 0.9f) + sgn * r * 0.035f), py(lerp(my, sy, 0.9f)))
                    c.drawPath(path, p)
                }
            }
        }
    }

    // ------------------------------------------------------------------ morilles

    private fun morelSide() {
        val capCy = look.stipeH + look.capRise * 0.5f - 0.35f
        val rx = r
        val rY = look.capRise * 0.5f
        // pied granuleux, évasé, creux
        val stem = stipePath(look.stipeH, arc = false)
        c.drawPath(stem, stipeShade(look.stipeH))
        c.save(); c.clipPath(stem)
        fill(withAlpha(darken(look.stipeTop, 0.25f), 80))
        val g = Random(look.seed + 31)
        repeat(90) {
            val y = g.nextFloat() * look.stipeH
            val x = (g.nextFloat() * 2f - 1f) * stipeHalf(y)
            c.drawCircle(px(x), py(y), hair * 0.7f, p)
        }
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-look.stipeBaseW / 2f), 0f, px(look.stipeBaseW / 2f), 0f,
            intArrayOf(withAlpha(Color.BLACK, 65), 0, withAlpha(Color.WHITE, 55), 0, withAlpha(Color.BLACK, 85)),
            floatArrayOf(0f, 0.15f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-look.stipeBaseW), py(look.stipeH + 0.2f), px(look.stipeBaseW), py(-0.1f), p)
        c.restore()
        stroke(stipeOutline, hair * 1.1f); c.drawPath(stem, p)

        // chapeau en œuf, alvéolé
        val cap = Path()
        val n = 80
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val ca = cos(a); val sa = sin(a)
            val yScale = if (sa > 0f) 1.05f else 0.92f
            val x = rx * sign(ca) * abs(ca).pow(0.9f)
            val y = capCy + rY * yScale * sign(sa) * abs(sa).pow(0.95f) * (1f - 0.12f * max(0f, sa) * abs(ca))
            if (i == 0) cap.moveTo(px(cx + x), py(y)) else cap.lineTo(px(cx + x), py(y))
        }
        cap.close()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx - rx * 0.3f), py(capCy + rY * 0.4f), max(rx, rY) * s * 1.4f,
            intArrayOf(lighten(look.capEdge, 0.12f), look.capEdge, darken(look.capEdge, 0.25f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(cap, p)
        c.save(); c.clipPath(cap)
        drawPits(capCy, rx, rY)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(cx - rx), 0f, px(cx + rx), 0f,
            intArrayOf(withAlpha(Color.BLACK, 40), 0, 0, withAlpha(Color.BLACK, 110)), floatArrayOf(0f, 0.2f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(cx - rx - 1f), py(capCy + rY + 1f), px(cx + rx + 1f), py(capCy - rY - 1f), p)
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(cap, p)
        // la gorge où le chapeau rejoint le pied
        stroke(withAlpha(darken(look.stipeTop, 0.5f), 200), hair * 1.4f)
        c.drawLine(px(cx - look.stipeW * 0.4f), py(look.stipeH - 0.18f), px(cx + look.stipeW * 0.4f), py(look.stipeH - 0.18f), p)
        anchor(Anchor.CAP, cx - rx * 0.4f, capCy + rY * 0.5f)
        anchor(Anchor.EDGE, cx + rx * 0.9f, capCy - rY * 0.6f)
        anchor(Anchor.STIPE_M, stipeHalf(look.stipeH * 0.5f), look.stipeH * 0.5f)
        anchor(Anchor.STIPE_U, stipeHalf(look.stipeH * 0.9f), look.stipeH * 0.9f)
        anchor(Anchor.BASE, stipeHalf(0.3f), 0.3f)
        anchor(Anchor.UMBO, cx, capCy + rY)
        anchor(Anchor.FACE, cx + rx * 0.5f, capCy - rY * 0.2f)
        anchor(Anchor.BULB, 0f, 0f)
    }

    private fun drawPits(capCy: Float, rx: Float, rY: Float) {
        val g = Random(look.seed + 37)
        val rows = 9
        for (i in 0..rows) {
            val theta = -PI.toFloat() / 2f + 0.14f + (PI.toFloat() - 0.28f) * i / rows
            val ct = cos(theta)
            val perRow = max(3, (7.5f * ct).roundToInt() + 2)
            for (j in 0 until perRow) {
                val phi = -PI.toFloat() / 2f * 0.96f + PI.toFloat() * 0.96f * (j + 0.5f + (if (i % 2 == 0) 0f else 0.5f)) / perRow
                if (abs(phi) > PI.toFloat() * 0.47f) continue
                val x = rx * ct * sin(phi)
                val y = capCy + rY * sin(theta) * 1.0f
                val fore = cos(phi)
                val pw = rx * 0.34f * ct * (0.62f + 0.38f * fore) / (perRow / 5.5f).coerceAtLeast(0.8f) * (0.9f + g.nextFloat() * 0.25f)
                val ph = rY * 0.2f
                // creux sombre, avec l'arête claire en bas à droite
                fill(withAlpha(Color.WHITE, 75))
                c.drawOval(RectF(px(cx + x - pw * 0.5f) + hair, py(y + ph) + hair, px(cx + x + pw * 0.5f) + hair, py(y - ph) + hair), p)
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(px(cx + x), py(y + ph * 0.3f), max(pw, ph) * s * 0.65f,
                    intArrayOf(darken(look.capCenter, 0.25f), look.capCenter, mixColor(look.capCenter, look.capEdge, 0.4f)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawOval(RectF(px(cx + x - pw * 0.5f), py(y + ph), px(cx + x + pw * 0.5f), py(y - ph)), p)
            }
        }
    }

    // ------------------------------------------------------------------ gyromitre

    private fun brainOutline(capCy: Float, scale: Float = 1f): Path {
        val path = Path()
        val n = 120
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val ca = cos(a); val sa = sin(a)
            val lobe = 1f + 0.11f * sin(3f * a + 0.7f) + 0.06f * sin(7f * a + 1.9f) + 0.04f * sin(11f * a)
            val hang = if (sa < 0f) 1f + 0.35f * abs(sa) * (0.5f + 0.5f * sin(4f * a + 0.3f)) else 1f
            val x = r * 1.0f * scale * lobe * ca * (1f - 0.1f * max(0f, sa))
            val y = capCy + look.capRise * 0.5f * scale * lobe * sa * (if (sa < 0f) 0.62f * hang else 1f)
            if (i == 0) path.moveTo(px(cx + x), py(y)) else path.lineTo(px(cx + x), py(y))
        }
        path.close()
        return path
    }

    private fun brainSide() {
        val capCy = look.stipeH + look.capRise * 0.42f
        // pied court, sillonné
        val stem = stipePath(look.stipeH + 0.3f, arc = false)
        c.drawPath(stem, stipeShade(look.stipeH))
        c.save(); c.clipPath(stem)
        stipeDecos()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-look.stipeBaseW / 2f), 0f, px(look.stipeBaseW / 2f), 0f,
            intArrayOf(withAlpha(Color.BLACK, 65), 0, withAlpha(Color.WHITE, 55), 0, withAlpha(Color.BLACK, 90)),
            floatArrayOf(0f, 0.15f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-look.stipeBaseW), py(look.stipeH + 0.5f), px(look.stipeBaseW), py(-0.1f), p)
        c.restore()
        stroke(stipeOutline, hair * 1.1f); c.drawPath(stem, p)

        val cap = brainOutline(capCy)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(cx - r * 0.3f), py(capCy + look.capRise * 0.3f), r * s * 1.5f,
            intArrayOf(lighten(look.capMid, 0.1f), look.capMid, look.capEdge), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(cap, p)
        c.save(); c.clipPath(cap)
        drawBrainFolds(capCy)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(capCy + look.capRise * 0.5f), 0f, py(capCy - look.capRise * 0.5f), 0, withAlpha(Color.BLACK, 90), Shader.TileMode.CLAMP)
        c.drawRect(px(cx - r * 1.3f), py(capCy + look.capRise), px(cx + r * 1.3f), py(capCy - look.capRise), p)
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(cap, p)
        anchor(Anchor.CAP, cx - r * 0.35f, capCy + look.capRise * 0.2f)
        anchor(Anchor.EDGE, cx + r * 0.95f, capCy - look.capRise * 0.15f)
        anchor(Anchor.STIPE_M, stipeHalf(look.stipeH * 0.4f), look.stipeH * 0.4f)
        anchor(Anchor.STIPE_U, stipeHalf(look.stipeH * 0.8f), look.stipeH * 0.8f)
        anchor(Anchor.BASE, stipeHalf(0.3f), 0.3f)
        anchor(Anchor.UMBO, cx, capCy + look.capRise * 0.5f)
        anchor(Anchor.FACE, cx + r * 0.5f, capCy - look.capRise * 0.3f)
        anchor(Anchor.BULB, 0f, 0f)
    }

    private fun drawBrainFolds(capCy: Float, scale: Float = 1f) {
        val g = Random(look.seed + 41)
        val dark = withAlpha(darken(look.capCenter, 0.45f), 215)
        val light = withAlpha(lighten(look.capEdge, 0.25f), 150)
        repeat(16) {
            var x = (g.nextFloat() * 2f - 1f) * r * 0.85f * scale
            var y = capCy + (g.nextFloat() * 2f - 1f) * look.capRise * 0.4f * scale
            var dir = g.nextFloat() * 2f * PI.toFloat()
            val path = Path(); path.moveTo(px(cx + x), py(y))
            val steps = 9 + g.nextInt(8)
            repeat(steps) {
                dir += (g.nextFloat() - 0.5f) * 1.9f
                x += cos(dir) * r * 0.1f * scale; y += sin(dir) * r * 0.075f * scale
                path.lineTo(px(cx + x), py(y))
            }
            stroke(light, hair * 2.2f); c.save(); c.translate(hair * 0.8f, hair * 0.8f); c.drawPath(path, p); c.restore()
            stroke(dark, hair * 1.8f); c.drawPath(path, p)
        }
    }

    // ------------------------------------------------------------------ vue en coupe

    fun section() {
        if (look.capShape == CapShape.BRACKET) { BracketPainter(c, look, ox, oy, s, hair, anchors).section(); return }
        if (look.onWood) { TuftPainter(c, look, ox, oy, s, hair, anchors).section(); return }
        drawGround()
        sectionBody()
    }

    /** La coupe, sans le sol : ce que le bouquet sur le tronc dessine pour son champignon principal. */
    internal fun sectionBody() {
        when (look.capShape) {
            CapShape.BALL -> { BallPainter(c, look, ox, oy, s, hair, anchors).section(); return }
            CapShape.CORAL -> { CoralPainter(c, look, ox, oy, s, hair, anchors).section(); return }
            CapShape.FUNNEL -> { funnelSection(); return }
            CapShape.MOREL -> { morelSection(); return }
            CapShape.BRAIN -> { brainSection(); return }
            else -> {}
        }
        val thick0 = if (look.fleshThick > 0f) look.fleshThick else look.capRise * 0.42f
        fun thick(x: Float): Float {
            val u = (abs(x) / r).coerceIn(0f, 1f)
            return max(0.12f, thick0 * (1f - u.pow(2.3f)) + 0.1f)
        }
        fun top(x: Float) = capTopY(x)
        fun fleshBottom(x: Float) = top(x) - thick(x)

        // chair du chapeau
        val flesh = Path()
        val nx = 70
        for (i in 0..nx) {
            val x = -r + 2f * r * i / nx
            if (i == 0) flesh.moveTo(px(cx + x), py(top(x))) else flesh.lineTo(px(cx + x), py(top(x)))
        }
        for (i in nx downTo 0) {
            val x = -r + 2f * r * i / nx
            flesh.lineTo(px(cx + x), py(fleshBottom(x)))
        }
        flesh.close()

        // hyménophore (lames ou tubes) sous la chair
        val hymPath = Path()
        val tubes = look.hymenium == Hymenium.PORES
        val hw = stipeHalf(y0)
        val gap = when (look.attach) {
            GillAttach.FREE -> hw + 0.28f
            GillAttach.ADNATE -> hw
            GillAttach.DECURRENT -> hw
        }
        fun hymBottom(x: Float): Float {
            val u = (abs(x) / r).coerceIn(0f, 1f)
            val rim = smoothstep(0f, 0.3f, 1f - u)
            val stip = if (tubes) smoothstep(0.1f, 0.34f, u) else 1f
            return fleshBottom(x) - look.hymDepth * rim * (if (tubes) stip else 1f)
        }
        val hymOn = look.hymenium != Hymenium.NONE && look.hymenium != Hymenium.RIDGES
        if (hymOn) {
            var started = false
            for (i in 0..nx) {
                val x = -r + 2f * r * i / nx
                if (abs(x) < gap) continue
                val yy = fleshBottom(x)
                if (!started) { hymPath.moveTo(px(cx + x), py(yy)); started = true } else hymPath.lineTo(px(cx + x), py(yy))
            }
            // bord inférieur, de droite à gauche, avec l'interruption autour du pied
            for (i in nx downTo 0) {
                val x = -r + 2f * r * i / nx
                if (abs(x) < gap) continue
                hymPath.lineTo(px(cx + x), py(hymBottom(x)))
            }
            hymPath.close()
        }

        // 1) pied, 2) hyménophore, 3) chair du chapeau par-dessus : le pied se fond dans la chair.
        val yTopStipe = fleshBottom(0f) + thick0 * 0.35f
        val stem = stipePath(yTopStipe, arc = false)
        c.drawPath(stem, fill(look.stipeFlesh))
        c.save(); c.clipPath(stem)
        stipeInterior(yTopStipe)
        stainsStipeSection(yTopStipe)
        c.restore()
        stroke(withAlpha(mixColor(look.stipeTop, look.stipeBottom, 0.5f), 255), max(hair * 2f, 0.09f * s))
        c.drawPath(stipePath(yTopStipe, arc = false), p)
        stroke(stipeOutline, hair); c.drawPath(stem, p)

        if (hymOn && look.hymenium == Hymenium.TEETH) {
            // des aiguillons : de petits cônes qui pendent sous la chair
            val step = 0.14f
            var tx = -r + step
            while (tx < r) {
                if (abs(tx) >= gap) {
                    val yb = fleshBottom(tx)
                    val yt = hymBottom(tx)
                    val w = step * 0.55f
                    val tooth = Path().apply {
                        moveTo(px(cx + tx - w), py(yb + 0.02f)); lineTo(px(cx + tx), py(yt)); lineTo(px(cx + tx + w), py(yb + 0.02f)); close()
                    }
                    fill(withAlpha(look.hymInner, 245)); c.drawPath(tooth, p)
                    stroke(withAlpha(darken(look.hymInner, 0.5f), 200), hair * 0.7f); c.drawPath(tooth, p)
                }
                tx += step
            }
        }
        if (hymOn && look.hymenium != Hymenium.TEETH) {
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(y0 + 0.5f), 0f, py(y0 - look.hymDepth - 0.3f),
                lighten(look.hymInner, 0.15f), darken(look.hymInner, 0.1f), Shader.TileMode.CLAMP)
            c.drawPath(hymPath, p)
            c.save(); c.clipPath(hymPath)
            stroke(withAlpha(darken(look.hymInner, if (tubes) 0.38f else 0.32f), if (tubes) 140 else 120), if (tubes) hair * 0.6f else hair * 0.75f)
            val step = if (tubes) 0.075f else max(0.1f, 0.18f - 0.08f * look.crowd)
            var x = -r
            while (x < r) {
                if (abs(x) >= gap) c.drawLine(px(cx + x), py(top(x) - thick(x) + 0.05f), px(cx + x), py(hymBottom(x) - 0.04f), p)
                x += step
            }
            c.restore()
            stroke(withAlpha(darken(look.hymInner, 0.55f), 210), hair)
            c.drawPath(hymPath, p)
        }
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(top(0f)), 0f, py(fleshBottom(0f)), lighten(look.flesh, 0.1f), look.flesh, Shader.TileMode.CLAMP)
        c.drawPath(flesh, p)
        c.save(); c.clipPath(flesh)
        // un liseré d'ombre à l'intérieur : la chair blanche se détache du papier
        stroke(withAlpha(darken(look.flesh, 0.45f), 70), max(hair * 5f, 0.28f * s))
        c.drawPath(flesh, p)
        stainsSection(::fleshBottom, ::top, ::thick)
        c.restore()
        // la peau du chapeau
        stroke(withAlpha(look.capMid, 255), max(hair * 2.2f, 0.11f * s))
        val skin = Path()
        for (i in 0..nx) {
            val x = -r + 2f * r * i / nx
            if (i == 0) skin.moveTo(px(cx + x), py(top(x))) else skin.lineTo(px(cx + x), py(top(x)))
        }
        c.drawPath(skin, p)
        stroke(outline, hair); c.drawPath(flesh, p)
        // le trait du dessous de la chair ne traverse pas le pied : on le recouvre
        val joinW = stipeHalf(yTopStipe) - 0.05f
        c.drawRect(px(-joinW), py(fleshBottom(0f) + 0.07f), px(joinW), py(fleshBottom(0f) - 0.07f), fill(mixColor(look.flesh, look.stipeFlesh, 0.5f)))
        // l'hyménophore décurrent descend le long du pied
        if (look.attach == GillAttach.DECURRENT && hymOn) {
            fill(withAlpha(look.hymInner, 240))
            val len = min(1.4f, look.stipeH * 0.35f)
            for (sgn in intArrayOf(-1, 1)) {
                val w = Path()
                val yy = fleshBottom(0f)
                w.moveTo(px(sgn * stipeHalf(yy)), py(yy))
                w.lineTo(px(sgn * (stipeHalf(yy) + 0.35f)), py(yy - 0.05f))
                w.lineTo(px(sgn * stipeHalf(yy - len)), py(yy - len))
                w.close(); c.drawPath(w, p)
            }
        }
        sectionRing()
        sectionVolva()
        if (look.latex != 0) latexDrops(::hymBottom, gap)
        // repères
        anchor(Anchor.S_FLESH, cx + r * 0.45f, (top(r * 0.45f) + fleshBottom(r * 0.45f)) * 0.5f)
        if (hymOn) anchor(Anchor.S_HYM, cx + r * 0.55f, (fleshBottom(r * 0.55f) + hymBottom(r * 0.55f)) * 0.5f)
        else anchor(Anchor.S_HYM, cx + r * 0.55f, fleshBottom(r * 0.55f) - 0.1f)
        anchor(Anchor.S_STIPE, stipeHalf(y0 * 0.6f) * 0.5f, y0 * 0.62f)
        anchor(Anchor.S_BASE, 0f, 0.25f)
        anchor(Anchor.S_CAVITY, 0f, y0 * 0.5f)
        if (look.ring != RingKind.NONE) anchor(Anchor.S_RING, stipeHalf(look.ringAt * y0) + 0.2f, look.ringAt * y0 - 0.1f)
        if (look.volva != VolvaKind.NONE) anchor(Anchor.S_VOLVA, stipeHalf(0.5f) + 0.3f, look.volvaH.coerceAtLeast(look.bulbH) * 0.5f)
    }

    /** Des gouttes de lait qui perlent sous les lames, de part et d'autre du pied. */
    private fun latexDrops(hymBottom: (Float) -> Float, gap: Float) {
        val dd = max(hair * 2.2f, 0.085f * s)
        val g = Random(look.seed + 61)
        for (sgn in intArrayOf(-1, 1)) for (k in 0 until 4) {
            val x = sgn * (gap + (r - gap) * (0.16f + 0.2f * k + 0.05f * g.nextFloat()))
            val bx = px(cx + x)
            val by = py(hymBottom(x)) + hair * 0.5f
            val len = dd * (2.0f + 0.7f * g.nextFloat())
            val drop = Path().apply {
                moveTo(bx, by)
                cubicTo(bx + dd * 0.95f, by + len * 0.55f, bx + dd * 0.95f, by + len * 0.95f, bx, by + len)
                cubicTo(bx - dd * 0.95f, by + len * 0.95f, bx - dd * 0.95f, by + len * 0.55f, bx, by)
                close()
            }
            fill(withAlpha(look.latex, 240)); c.drawPath(drop, p)
            stroke(withAlpha(darken(look.latex, 0.4f), 190), hair * 0.6f); c.drawPath(drop, p)
            fill(withAlpha(Color.WHITE, 150)); c.drawOval(RectF(bx - dd * 0.35f, by + len * 0.45f, bx - dd * 0.05f, by + len * 0.7f), p)
            if (sgn == 1 && k == 1) anchors[Anchor.S_STAIN] = PointF(bx, by + len * 0.6f)
        }
    }

    private fun stainsSection(fleshBottom: (Float) -> Float, top: (Float) -> Float, thick: (Float) -> Float) {
        for (st in look.stains) when (st.zone) {
            StainZone.CAP_SKIN -> {
                stroke(withAlpha(st.color, (st.strength * 170).roundToInt()), max(hair * 3f, 0.2f * s))
                val path = Path()
                for (i in 0..50) {
                    val x = -r * 0.95f + 1.9f * r * i / 50f
                    val y = top(x) - 0.2f
                    if (i == 0) path.moveTo(px(cx + x), py(y)) else path.lineTo(px(cx + x), py(y))
                }
                c.drawPath(path, p)
                anchor(Anchor.S_STAIN, cx + r * 0.5f, top(r * 0.5f) - 0.2f)
            }
            StainZone.ABOVE_TUBES -> {
                p.reset(); p.isAntiAlias = true
                val path = Path()
                for (i in 0..50) {
                    val x = -r * 0.92f + 1.84f * r * i / 50f
                    if (i == 0) path.moveTo(px(cx + x), py(fleshBottom(x))) else path.lineTo(px(cx + x), py(fleshBottom(x)))
                }
                for (i in 50 downTo 0) {
                    val x = -r * 0.92f + 1.84f * r * i / 50f
                    path.lineTo(px(cx + x), py(fleshBottom(x) + 0.55f * (1f - (abs(x) / r).pow(2)) + 0.12f))
                }
                path.close()
                p.color = withAlpha(st.color, (st.strength * 210).roundToInt())
                c.drawPath(path, p)
                anchor(Anchor.S_STAIN, cx + r * 0.42f, fleshBottom(r * 0.42f) + 0.25f)
            }
            else -> {}
        }
    }

    private fun stainsStipeSection(yTop: Float) {
        for (st in look.stains) {
            val (a, b) = when (st.zone) {
                StainZone.STIPE_BASE -> 0f to yTop * 0.5f
                StainZone.STIPE_TOP -> yTop * 0.65f to yTop
                StainZone.STIPE_ALL, StainZone.STIPE_CUT -> 0f to yTop
                else -> continue
            }
            p.reset(); p.isAntiAlias = true
            val strong = withAlpha(st.color, (st.strength * 230).roundToInt())
            p.shader = if (st.zone == StainZone.STIPE_BASE) LinearGradient(0f, py(a), 0f, py(b), strong, 0, Shader.TileMode.CLAMP)
            else LinearGradient(0f, py(a), 0f, py(b), withAlpha(st.color, (st.strength * 160).roundToInt()), withAlpha(st.color, (st.strength * 160).roundToInt()), Shader.TileMode.CLAMP)
            c.drawRect(px(-3f), py(b), px(3f), py(a), p)
            if (st.zone == StainZone.STIPE_BASE) anchor(Anchor.S_STAIN, 0f, yTop * 0.14f)
            else if (st.zone == StainZone.STIPE_CUT) anchor(Anchor.S_STAIN, 0f, yTop * 0.5f)
        }
    }

    private fun stipeInterior(yTop: Float) {
        val g = Random(look.seed + 43)
        when (look.interior) {
            Interior.SOLID -> {
                stroke(withAlpha(darken(look.stipeFlesh, 0.3f), 60), hair * 0.7f)
                for (k in -3..3) {
                    var prev: PointF? = null
                    var y = 0.1f
                    while (y < yTop) {
                        val q = PointF(px(axisAt(y) + k * stipeHalf(y) * 0.28f), py(y)); prev?.let { c.drawLine(it.x, it.y, q.x, q.y, p) }
                        prev = q; y += 0.4f
                    }
                }
            }
            Interior.STUFFED -> {
                fill(withAlpha(lighten(look.stipeFlesh, 0.35f), 200))
                val cav = Path()
                for (i in 0..24) { val y = 0.25f + (yTop - 0.5f) * i / 24f; val x = axisAt(y) - stipeHalf(y) * 0.5f; if (i == 0) cav.moveTo(px(x), py(y)) else cav.lineTo(px(x), py(y)) }
                for (i in 24 downTo 0) { val y = 0.25f + (yTop - 0.5f) * i / 24f; cav.lineTo(px(axisAt(y) + stipeHalf(y) * 0.5f), py(y)) }
                cav.close(); c.drawPath(cav, p)
                stroke(withAlpha(darken(look.stipeFlesh, 0.35f), 90), hair * 0.6f)
                repeat(40) {
                    val y = g.nextFloat() * yTop
                    val x = axisAt(y) + (g.nextFloat() - 0.5f) * stipeHalf(y)
                    c.drawLine(px(x), py(y), px(x + (g.nextFloat() - 0.5f) * 0.08f), py(y + 0.25f), p)
                }
            }
            Interior.HOLLOW -> {
                val cav = Path()
                for (i in 0..24) { val y = 0.3f + (yTop - 0.4f) * i / 24f; val x = axisAt(y) - stipeHalf(y) * 0.55f; if (i == 0) cav.moveTo(px(x), py(y)) else cav.lineTo(px(x), py(y)) }
                for (i in 24 downTo 0) { val y = 0.3f + (yTop - 0.4f) * i / 24f; cav.lineTo(px(axisAt(y) + stipeHalf(y) * 0.55f), py(y)) }
                cav.close()
                p.reset(); p.isAntiAlias = true
                p.shader = LinearGradient(px(-stipeHalf(0.5f)), 0f, px(stipeHalf(0.5f)), 0f,
                    darken(look.stipeFlesh, 0.35f), lighten(look.stipeFlesh, 0.1f), Shader.TileMode.CLAMP)
                c.drawPath(cav, p)
                stroke(withAlpha(darken(look.stipeFlesh, 0.6f), 190), hair); c.drawPath(cav, p)
            }
            Interior.CHAMBERED -> {
                var y = 0.4f
                while (y < yTop - 0.4f) {
                    val h = 0.5f + g.nextFloat() * 0.8f
                    val w = stipeHalf(y) * (0.35f + g.nextFloat() * 0.25f)
                    p.reset(); p.isAntiAlias = true; p.color = darken(look.stipeFlesh, 0.28f)
                    c.drawOval(RectF(px(axisAt(y) - w), py(y + h), px(axisAt(y) + w), py(y)), p)
                    stroke(withAlpha(darken(look.stipeFlesh, 0.6f), 170), hair * 0.8f)
                    c.drawOval(RectF(px(axisAt(y) - w), py(y + h), px(axisAt(y) + w), py(y)), p)
                    y += h + 0.15f
                }
            }
        }
    }

    private fun sectionRing() {
        if (look.ring == RingKind.NONE || look.ring == RingKind.ZONE) return
        val yr = look.ringAt * y0
        val hw = stipeHalf(yr)
        val fugacious = look.ring == RingKind.FUGACIOUS
        val spread = look.capDiam * (if (fugacious) 0.035f else 0.1f)
        val drop = look.capDiam * (if (fugacious) 0.04f else 0.11f)
        val levels = if (look.ring == RingKind.SLIDING) listOf(yr + drop * 0.45f to 0.7f, yr to 1f) else listOf(yr to 1f)
        for ((y, k) in levels) {
            for (sgn in intArrayOf(-1, 1)) {
                val w = Path()
                w.moveTo(px(sgn * hw), py(y))
                w.quadTo(px(sgn * (hw + spread * 0.9f * k)), py(y - drop * 0.25f * k), px(sgn * (hw + spread * k)), py(y - drop * k))
                w.lineTo(px(sgn * (hw + spread * k * 0.82f)), py(y - drop * k * 1.04f))
                w.quadTo(px(sgn * (hw + spread * k * 0.4f)), py(y - drop * k * 0.3f), px(sgn * hw), py(y - 0.12f))
                w.close()
                c.drawPath(w, fill(look.ringColor))
                c.drawPath(w, stroke(withAlpha(darken(look.ringColor, 0.55f), 210), hair))
            }
        }
    }

    private fun sectionVolva() {
        if (look.volva != VolvaKind.SAC) return
        val h = look.volvaH
        for (sgn in intArrayOf(-1, 1)) {
            val w = Path()
            w.moveTo(px(sgn * (stipeHalf(h) + 0.38f)), py(h + 0.1f))
            val n = 20
            for (i in n downTo 0) {
                val y = h * i / n
                w.lineTo(px(sgn * (stipeHalf(y) + 0.2f + (if (y < h * 0.35f) 0.12f else 0f))), py(y))
            }
            for (i in 0..n) {
                val y = h * i / n
                w.lineTo(px(sgn * (stipeHalf(y) + 0.1f)), py(y + 0.02f))
            }
            w.lineTo(px(sgn * (stipeHalf(h) + 0.08f)), py(h))
            w.close()
            c.drawPath(w, fill(withAlpha(look.volvaColor, 245)))
            c.drawPath(w, stroke(withAlpha(darken(look.volvaColor, 0.6f), 220), hair))
        }
    }

    // --- coupes particulières

    private fun funnelSection() {
        val neckY = look.stipeH
        val rimTop = look.stipeH + look.capRise
        val hw0 = stipeHalf(neckY)
        fun outerHalf(y: Float): Float {
            val t = ((y - neckY) / look.capRise).coerceIn(0f, 1f)
            return lerp(hw0 * 1.05f, r, t.pow(1.55f))
        }
        fun thick(y: Float): Float {
            val t = ((y - neckY) / look.capRise).coerceIn(0f, 1f)
            return lerp(hw0 * 0.9f, look.capRise * 0.2f + 0.15f, t.pow(0.8f))
        }
        val body = Path()
        val steps = 28
        // surface extérieure (gauche) du sol au bord
        for (i in 0..steps) {
            val y = if (i * 1f / steps < 0.5f) (neckY * 2f * i / steps) else neckY + look.capRise * ((i * 1f / steps - 0.5f) * 2f)
            val x = if (y <= neckY) -stipeHalf(y) else -outerHalf(y)
            if (i == 0) body.moveTo(px(x), py(y)) else body.lineTo(px(x), py(y))
        }
        // surface intérieure : creux en entonnoir
        val inner = 24
        for (k in 0..inner) {
            val t = k / inner.toFloat()
            val x = -r + 2f * r * t
            val depth = (look.capRise * 0.34f + look.dip) * (1f - (x / r).pow(2))
            body.lineTo(px(x), py(rimTop - depth))
        }
        for (i in steps downTo 0) {
            val y = if (i * 1f / steps < 0.5f) (neckY * 2f * i / steps) else neckY + look.capRise * ((i * 1f / steps - 0.5f) * 2f)
            val x = if (y <= neckY) stipeHalf(y) else outerHalf(y)
            body.lineTo(px(x), py(y))
        }
        body.close()
        c.drawPath(body, fill(look.flesh))
        c.save(); c.clipPath(body)
        // peau colorée en haut, chair en bas ; pour les entonnoirs pleins, quelques fibres
        stroke(withAlpha(look.stipeFlesh, 90), hair * 0.7f)
        for (k in -3..3) c.drawLine(px(k * hw0 * 0.3f), py(0.1f), px(k * hw0 * 0.3f * 1.5f), py(neckY + look.capRise * 0.6f), p)
        for (st in look.stains) if (st.zone == StainZone.STIPE_ALL) {
            p.reset(); p.color = withAlpha(st.color, (st.strength * 130).roundToInt()); p.isAntiAlias = true
            c.drawRect(px(-r - 1f), py(rimTop + 1f), px(r + 1f), py(-0.1f), p)
            anchor(Anchor.S_STAIN, 0f, neckY * 0.6f)
        }
        if (look.interior == Interior.HOLLOW) {
            // creux d'un bout à l'autre : le canal du pied débouche dans la coupe, entre deux parois minces
            val wall = max(0.1f, hw0 * 0.2f)
            val yTopChannel = max(rimTop - (look.capRise * 0.34f + look.dip), 0.5f) + 0.05f
            fun half(y: Float) = max(0.08f, (if (y <= neckY) stipeHalf(y) else outerHalf(y)) - wall)
            val channel = Path()
            val m = 24
            for (i in 0..m) {
                val y = 0.3f + (yTopChannel - 0.3f) * i / m
                if (i == 0) channel.moveTo(px(-half(y)), py(y)) else channel.lineTo(px(-half(y)), py(y))
            }
            for (i in m downTo 0) {
                val y = 0.3f + (yTopChannel - 0.3f) * i / m
                channel.lineTo(px(half(y)), py(y))
            }
            channel.close()
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(px(-hw0), 0f, px(hw0), 0f,
                intArrayOf(darken(look.flesh, 0.45f), darken(look.flesh, 0.2f), lighten(look.flesh, 0.08f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(channel, p)
            // le contour ne ferme pas le haut : le canal débouche dans la coupe
            val edge = Path()
            for (i in m downTo 0) {
                val y = 0.3f + (yTopChannel - 0.3f) * i / m
                if (i == m) edge.moveTo(px(-half(y)), py(y)) else edge.lineTo(px(-half(y)), py(y))
            }
            for (i in 0..m) edge.lineTo(px(half(0.3f + (yTopChannel - 0.3f) * i / m)), py(0.3f + (yTopChannel - 0.3f) * i / m))
            stroke(withAlpha(darken(look.flesh, 0.62f), 200), hair); c.drawPath(edge, p)
            anchor(Anchor.S_CAVITY, 0f, neckY * 0.55f)
        }
        c.restore()
        // plis sous le chapeau (petites bosses émoussées) le long de la face externe
        if (look.hymenium != Hymenium.NONE) {
            fill(withAlpha(look.hymInner, 235))
            for (sgn in intArrayOf(-1, 1)) {
                var y = neckY + look.capRise * 0.08f
                while (y < rimTop - look.capRise * 0.1f) {
                    val x = sgn * outerHalf(y)
                    val b = if (look.hymenium == Hymenium.GILLS) 0.1f else 0.17f
                    c.drawOval(RectF(px(x - b * 0.4f), py(y + b * 0.8f), px(x + b * 0.4f), py(y - b * 0.2f)), p)
                    y += b * 1.8f
                }
            }
        }
        stroke(withAlpha(mixColor(look.capEdge, look.capMid, 0.5f), 255), max(hair * 2.2f, 0.1f * s))
        val skin = Path()
        for (k in 0..inner) {
            val t = k / inner.toFloat()
            val x = -r + 2f * r * t
            val depth = (look.capRise * 0.34f + look.dip) * (1f - (x / r).pow(2))
            val yy = rimTop - depth
            if (k == 0) skin.moveTo(px(x), py(yy)) else skin.lineTo(px(x), py(yy))
        }
        c.drawPath(skin, p)
        stroke(outline, hair); c.drawPath(body, p)
        if (look.interior == Interior.HOLLOW) {
            // la bouche du canal : le trait du fond de la coupe ne traverse pas l'ouverture
            val mouth = max(0.08f, hw0 - max(0.1f, hw0 * 0.2f))
            val yMouth = rimTop - (look.capRise * 0.34f + look.dip)
            p.reset(); p.isAntiAlias = true
            p.color = darken(look.flesh, 0.22f)
            c.drawRect(px(-mouth + 0.03f), py(yMouth + 0.07f), px(mouth - 0.03f), py(yMouth - 0.09f), p)
        }
        anchor(Anchor.S_FLESH, r * 0.45f, rimTop - look.capRise * 0.18f)
        anchor(Anchor.S_HYM, outerHalf(neckY + look.capRise * 0.6f), neckY + look.capRise * 0.6f)
        anchor(Anchor.S_STIPE, 0f, neckY * 0.6f)
        anchor(Anchor.S_BASE, 0f, 0.25f)
    }

    private fun morelEgg(rx: Float, rY: Float, capCy: Float): Path {
        val path = Path()
        val n = 90
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val ca = cos(a); val sa = sin(a)
            val yScale = if (sa > 0f) 1.05f else 0.92f
            val x = rx * sign(ca) * abs(ca).pow(0.9f)
            val y = capCy + rY * yScale * sign(sa) * abs(sa).pow(0.95f) * (1f - 0.12f * max(0f, sa) * abs(ca))
            if (i == 0) path.moveTo(px(cx + x), py(y)) else path.lineTo(px(cx + x), py(y))
        }
        path.close()
        return path
    }

    private fun morelSection() {
        val capCy = look.stipeH + look.capRise * 0.5f - 0.35f
        val rY = look.capRise * 0.5f
        val wall = max(0.14f, r * 0.075f)
        val outerEgg = morelEgg(r, rY, capCy)
        val innerEgg = morelEgg(r - wall, rY - wall, capCy)
        val stem = stipePath(look.stipeH + 0.3f, arc = false)
        // parois : pied et chapeau d'un seul tenant
        c.drawPath(stem, fill(look.stipeFlesh))
        c.drawPath(outerEgg, fill(look.flesh))
        // cavité commune : pied + bas du chapeau + chapeau, liés par un pont
        val cavity = Path()
        val m = 40
        for (i in 0..m) {
            val y = 0.3f + (look.stipeH + 0.1f) * i / m
            val x = -(stipeHalf(y) - wall).coerceAtLeast(0.12f)
            if (i == 0) cavity.moveTo(px(x), py(y)) else cavity.lineTo(px(x), py(y))
        }
        for (i in m downTo 0) {
            val y = 0.3f + (look.stipeH + 0.1f) * i / m
            cavity.lineTo(px((stipeHalf(y) - wall).coerceAtLeast(0.12f)), py(y))
        }
        cavity.close()
        val bridge = Path()
        val bw = (stipeHalf(look.stipeH) - wall).coerceAtLeast(0.12f)
        bridge.addRect(px(-bw), py(capCy + 0.2f), px(bw), py(look.stipeH - 0.4f), Path.Direction.CW)
        val all = Path(cavity)
        all.op(bridge, Path.Op.UNION)
        all.op(innerEgg, Path.Op.UNION)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-r), 0f, px(r), 0f,
            intArrayOf(darken(look.flesh, 0.42f), darken(look.flesh, 0.18f), lighten(look.flesh, 0.12f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(all, p)
        stroke(withAlpha(darken(look.flesh, 0.62f), 200), hair); c.drawPath(all, p)
        // la paroi alvéolée : un liseré sombre, légèrement ondulé
        c.save(); c.clipPath(outerEgg)
        stroke(withAlpha(look.capCenter, 190), max(hair * 2f, wall * s * 0.45f))
        val skin = Path()
        val n = 90
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val ca = cos(a); val sa = sin(a)
            val yScale = if (sa > 0f) 1.05f else 0.92f
            val wob = 1f - 0.035f * sin(a * 16f)
            val x = r * wob * sign(ca) * abs(ca).pow(0.9f)
            val y = capCy + rY * wob * yScale * sign(sa) * abs(sa).pow(0.95f) * (1f - 0.12f * max(0f, sa) * abs(ca))
            if (i == 0) skin.moveTo(px(cx + x), py(y)) else skin.lineTo(px(cx + x), py(y))
        }
        skin.close(); c.drawPath(skin, p)
        c.restore()
        stroke(outline, hair); c.drawPath(outerEgg, p)
        c.save(); c.clipRect(0f, py(look.stipeH - 0.1f), 100000f, 100000f)
        stroke(stipeOutline, hair); c.drawPath(stem, p)
        c.restore()
        anchor(Anchor.S_FLESH, cx + r - wall * 0.5f, capCy)
        anchor(Anchor.S_CAVITY, 0f, capCy)
        anchor(Anchor.S_STIPE, stipeHalf(look.stipeH * 0.4f) - wall * 0.5f, look.stipeH * 0.4f)
        anchor(Anchor.S_HYM, cx - r + wall * 0.5f, capCy + rY * 0.35f)
        anchor(Anchor.S_BASE, 0f, 0.25f)
    }

    private fun brainSection() {
        val capCy = look.stipeH + look.capRise * 0.42f
        val cap = brainOutline(capCy)
        val stem = stipePath(look.stipeH + 0.5f, arc = false)
        c.drawPath(stem, fill(look.stipeFlesh))
        c.drawPath(cap, fill(look.flesh))
        // intérieur chambré : de grandes cavités irrégulières, séparées par des cloisons minces
        val g = Random(look.seed + 47)
        c.save(); c.clipPath(cap)
        fun blob(x: Float, y: Float, w: Float, h: Float): Path {
            val path = Path()
            val n = 14
            for (i in 0 until n) {
                val a = 2f * PI.toFloat() * i / n
                val k = 0.78f + 0.3f * g.nextFloat()
                val bx = px(cx + x + w * k * cos(a)); val by = py(y + h * k * sin(a))
                if (i == 0) path.moveTo(bx, by) else path.lineTo(bx, by)
            }
            path.close()
            return path
        }
        val spots = listOf(-0.55f to 0.18f, -0.15f to 0.22f, 0.3f to 0.2f, -0.35f to -0.06f, 0.1f to -0.02f, 0.5f to -0.08f, -0.05f to -0.26f)
        for ((fx, fy) in spots) {
            val shape = blob(fx * r, capCy + fy * look.capRise, r * 0.2f, look.capRise * 0.085f)
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(capCy + look.capRise * 0.3f), 0f, py(capCy - look.capRise * 0.3f), darken(look.flesh, 0.5f), darken(look.flesh, 0.12f), Shader.TileMode.CLAMP)
            c.drawPath(shape, p)
            stroke(withAlpha(darken(look.flesh, 0.62f), 190), hair * 0.8f); c.drawPath(shape, p)
        }
        c.restore()
        // la peau brune, plissée
        c.save(); c.clipPath(cap)
        stroke(look.capMid, max(hair * 3f, 0.16f * s)); c.drawPath(cap, p)
        c.restore()
        c.save(); c.clipPath(stem)
        repeat(3) {
            val y = 0.4f + it * look.stipeH * 0.3f
            val w = stipeHalf(y) * 0.3f
            c.drawOval(RectF(px(-w), py(y + 0.5f), px(w), py(y)), fill(darken(look.stipeFlesh, 0.3f)))
        }
        c.restore()
        stroke(outline, hair); c.drawPath(cap, p)
        c.save(); c.clipRect(0f, py(look.stipeH - 0.15f), 100000f, 100000f)
        stroke(stipeOutline, hair); c.drawPath(stem, p)
        c.restore()
        anchor(Anchor.S_FLESH, cx + r * 0.65f, capCy + look.capRise * 0.18f)
        anchor(Anchor.S_CAVITY, cx - r * 0.2f, capCy)
        anchor(Anchor.S_STIPE, 0f, look.stipeH * 0.5f)
        anchor(Anchor.S_HYM, cx + r * 0.85f, capCy - look.capRise * 0.15f)
        anchor(Anchor.S_BASE, 0f, 0.25f)
    }

    // ------------------------------------------------------------------ vue de dessous

    /** Le chapeau vu d'en dessous, centré en (ucx, ucy) pixels, de rayon [ur] pixels. */
    fun under(ucx: Float, ucy: Float, ur: Float) {
        val seedRnd = Random(look.seed + 53)
        if (look.capShape == CapShape.MOREL || look.capShape == CapShape.BRAIN) { top(ucx, ucy, ur); return }
        if (look.capShape == CapShape.BALL) { BallPainter(c, look, ox, oy, s, hair, anchors).top(ucx, ucy, ur); return }
        if (look.capShape == CapShape.BRACKET) { BracketPainter(c, look, ox, oy, s, hair, anchors).under(ucx, ucy, ur); return }
        if (look.capShape == CapShape.CORAL) { CoralPainter(c, look, ox, oy, s, hair, anchors).under(ucx, ucy, ur); return }
        val shape = Path()
        val n = 72
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val rr = ur * (1f + (if (look.capShape == CapShape.FUNNEL) 0.05f * sin(a * 5f + look.seed) + 0.03f * sin(a * 9f) else 0.015f * sin(a * 3f + look.seed)))
            val x = ucx + rr * cos(a); val y = ucy + rr * sin(a)
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        // bord du chapeau
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx, ucy, ur, intArrayOf(look.hymColor, look.hymColor, look.capEdge), floatArrayOf(0f, 0.9f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(shape, p)
        c.save(); c.clipPath(shape)
        val rim = 0.93f
        val hwPx = stipeHalf(y0) / r * ur
        when (look.hymenium) {
            Hymenium.GILLS -> {
                val n2 = (36 + 40 * look.crowd).roundToInt()
                val collar = if (look.attach == GillAttach.FREE) hwPx + ur * 0.1f else hwPx * 0.9f
                val gill = withAlpha(darken(look.hymColor, 0.4f), 170)
                val edge = withAlpha(lighten(look.hymColor, 0.4f), 130)
                for (j in 0 until n2) {
                    val a = 2f * PI.toFloat() * j / n2
                    val short = j % 2 == 1
                    val r0 = if (short) ur * 0.5f else collar
                    stroke(gill, max(hair * 0.8f, ur * 0.011f))
                    if (look.forked && !short) {
                        val mr = ur * 0.55f
                        c.drawLine(ucx + r0 * cos(a), ucy + r0 * sin(a), ucx + mr * cos(a), ucy + mr * sin(a), p)
                        for (sgn in intArrayOf(-1, 1)) {
                            val b = a + sgn * 0.06f
                            c.drawLine(ucx + mr * cos(a), ucy + mr * sin(a), ucx + ur * rim * cos(b), ucy + ur * rim * sin(b), p)
                        }
                    } else c.drawLine(ucx + r0 * cos(a), ucy + r0 * sin(a), ucx + ur * rim * cos(a), ucy + ur * rim * sin(a), p)
                    stroke(edge, hair * 0.6f)
                    val a2 = a + PI.toFloat() / n2
                    c.drawLine(ucx + (r0 + 3f) * cos(a2), ucy + (r0 + 3f) * sin(a2), ucx + ur * rim * cos(a2), ucy + ur * rim * sin(a2), p)
                }
            }
            Hymenium.PORES -> {
                val dot = withAlpha(darken(look.hymColor, 0.55f), 190)
                val sp = max(ur * 0.04f, hair * 3f)
                var rr = ur * 0.2f
                while (rr < ur * rim) {
                    val count = max(10, (2f * PI.toFloat() * rr / sp).roundToInt())
                    for (j in 0 until count) {
                        val a = 2f * PI.toFloat() * (j + (rr * 0.17f) % 1f) / count
                        fill(dot); c.drawCircle(ucx + rr * cos(a), ucy + rr * sin(a), max(hair * 0.7f, sp * 0.22f), p)
                    }
                    rr += sp * 0.87f
                }
                // la dépression autour du pied
                p.reset(); p.isAntiAlias = true; p.color = withAlpha(darken(look.hymColor, 0.25f), 200)
                c.drawCircle(ucx, ucy, hwPx * 1.25f, p)
            }
            Hymenium.RIDGES -> {
                val veins = 20
                val dark = withAlpha(darken(look.hymColor, 0.4f), 210)
                val light = withAlpha(lighten(look.hymColor, 0.35f), 230)
                val wide = max(hair * 2f, ur * 0.034f)
                val thin = max(hair, ur * 0.016f)
                fun vein(a0: Float, from: Float, to: Float, phase: Float): List<PointF> {
                    val pts = ArrayList<PointF>()
                    var rr = from
                    var ang = a0
                    while (rr <= to) {
                        ang = a0 + sin(rr / ur * 5f + phase) * 0.045f
                        pts.add(PointF(ucx + rr * cos(ang), ucy + rr * sin(ang)))
                        rr += ur * 0.06f
                    }
                    return pts
                }
                fun drawLine(pts: List<PointF>, col: Int, w: Float) {
                    stroke(col, w)
                    for (i in 1 until pts.size) c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, p)
                }
                for (j in 0 until veins) {
                    val a = 2f * PI.toFloat() * j / veins + (seedRnd.nextFloat() - 0.5f) * 0.08f
                    val phase = j * 1.7f
                    val main = vein(a, hwPx, ur * rim, phase)
                    val forkR = ur * (0.48f + 0.12f * seedRnd.nextFloat())
                    val tail = main.last { hypot2(it.x - ucx, it.y - ucy) <= forkR }
                    val side = if (j % 2 == 0) 1f else -1f
                    val branchEnd = vein(a + side * 0.13f, forkR, ur * rim, phase + 1f).let { b2 ->
                        listOf(tail) + b2.drop(1)
                    }
                    drawLine(main, dark, wide); drawLine(branchEnd, dark, wide * 0.8f)
                    drawLine(main, light, thin); drawLine(branchEnd, light, thin * 0.9f)
                }
            }
            Hymenium.TEETH -> {
                val sp = max(ur * 0.03f, hair * 2.2f)
                var rr = ur * 0.18f
                while (rr < ur * rim) {
                    val count = max(10, (2f * PI.toFloat() * rr / sp).roundToInt())
                    for (j in 0 until count) {
                        val a = 2f * PI.toFloat() * (j + (rr * 0.23f) % 1f) / count
                        val tx = ucx + rr * cos(a); val ty = ucy + rr * sin(a)
                        fill(withAlpha(darken(look.hymColor, 0.45f), 150)); c.drawCircle(tx + sp * 0.12f, ty + sp * 0.12f, max(hair * 0.8f, sp * 0.3f), p)
                        fill(lighten(look.hymColor, 0.18f)); c.drawCircle(tx, ty, max(hair * 0.7f, sp * 0.24f), p)
                    }
                    rr += sp * 0.8f
                }
                p.reset(); p.isAntiAlias = true; p.color = withAlpha(darken(look.hymColor, 0.25f), 200)
                c.drawCircle(ucx, ucy, hwPx * 1.2f, p)
            }
            Hymenium.NONE -> {}
        }
        if (look.latex != 0) {
            val g = Random(look.seed + 67)
            repeat(9) {
                val a = 2f * PI.toFloat() * g.nextFloat()
                val rr = ur * (0.3f + 0.6f * g.nextFloat())
                val dx = ucx + rr * cos(a); val dy = ucy + rr * sin(a)
                val rad = max(hair * 1.4f, ur * 0.03f)
                fill(withAlpha(look.latex, 235)); c.drawCircle(dx, dy, rad, p)
                stroke(withAlpha(darken(look.latex, 0.4f), 190), hair * 0.6f); c.drawCircle(dx, dy, rad, p)
                fill(withAlpha(Color.WHITE, 160)); c.drawCircle(dx - rad * 0.3f, dy - rad * 0.3f, rad * 0.28f, p)
            }
        }
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(shape, p)
        // le pied, en coupe au centre
        val hr = hwPx
        when (look.interior) {
            Interior.HOLLOW -> {
                c.drawCircle(ucx, ucy, hr, fill(look.stipeFlesh))
                c.drawCircle(ucx, ucy, hr * 0.5f, fill(darken(look.stipeFlesh, 0.4f)))
            }
            Interior.CHAMBERED -> {
                c.drawCircle(ucx, ucy, hr, fill(look.stipeFlesh))
                for (k in 0 until 4) { val a = k * PI.toFloat() / 2f + 0.5f; c.drawCircle(ucx + hr * 0.4f * cos(a), ucy + hr * 0.4f * sin(a), hr * 0.25f, fill(darken(look.stipeFlesh, 0.4f))) }
            }
            else -> {
                c.drawCircle(ucx, ucy, hr, fill(look.stipeFlesh))
                if (look.interior == Interior.STUFFED) c.drawCircle(ucx, ucy, hr * 0.5f, fill(lighten(look.stipeFlesh, 0.3f)))
            }
        }
        c.drawCircle(ucx, ucy, hr, stroke(stipeOutline, hair))
        if (look.ring != RingKind.NONE && look.ring != RingKind.ZONE) {
            c.drawCircle(ucx, ucy, hr * 1.7f, stroke(withAlpha(darken(look.ringColor, 0.5f), 200), hair * 1.2f))
        }
        anchors[Anchor.U_RIM] = PointF(ucx + ur * 0.7f, ucy - ur * 0.7f)
        anchors[Anchor.U_HYM] = PointF(ucx + ur * 0.55f, ucy + ur * 0.5f)
        anchors[Anchor.U_CENTER] = PointF(ucx, ucy)
    }

    /** Morilles et gyromitres n'ont pas de dessous à montrer : on regarde le chapeau par le dessus. */
    private fun top(ucx: Float, ucy: Float, ur: Float) {
        val path = Path()
        val n = 90
        for (i in 0..n) {
            val a = 2f * PI.toFloat() * i / n
            val lobe = if (look.capShape == CapShape.BRAIN) 1f + 0.12f * sin(3f * a + 0.7f) + 0.06f * sin(7f * a + 1.9f) else 1f + 0.02f * sin(a * 5f)
            val x = ucx + ur * lobe * cos(a); val y = ucy + ur * (if (look.capShape == CapShape.BRAIN) 0.88f else 0.86f) * lobe * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx - ur * 0.25f, ucy - ur * 0.25f, ur * 1.4f,
            intArrayOf(lighten(look.capEdge, 0.1f), look.capEdge, darken(look.capEdge, 0.3f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        val g = Random(look.seed + 59)
        if (look.capShape == CapShape.MOREL) {
            // alvéoles en cercles concentriques depuis le sommet
            var rr = ur * 0.1f
            var row = 0
            while (rr < ur * 0.98f) {
                val count = max(5, (2f * PI.toFloat() * rr / (ur * 0.24f)).roundToInt())
                for (j in 0 until count) {
                    val a = 2f * PI.toFloat() * (j + (row % 2) * 0.5f) / count
                    val w = ur * 0.095f * (0.85f + g.nextFloat() * 0.3f)
                    val x = ucx + rr * cos(a); val y = ucy + rr * 0.9f * sin(a)
                    p.reset(); p.isAntiAlias = true; p.color = withAlpha(Color.WHITE, 70)
                    c.drawOval(RectF(x - w + hair, y - w * 0.8f + hair, x + w + hair, y + w * 0.8f + hair), p)
                    p.color = look.capCenter
                    c.drawOval(RectF(x - w, y - w * 0.8f, x + w, y + w * 0.8f), p)
                }
                rr += ur * 0.2f; row++
            }
        } else {
            val k = ur / (r * s)
            c.save(); c.translate(ucx, ucy); c.scale(k, k); c.translate(-px(cx), -py(look.stipeH + look.capRise * 0.42f))
            drawBrainFolds(look.stipeH + look.capRise * 0.42f, 1f)
            c.restore()
        }
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(path, p)
        anchors[Anchor.U_RIM] = PointF(ucx + ur * 0.7f, ucy - ur * 0.6f)
        anchors[Anchor.U_HYM] = PointF(ucx + ur * 0.2f, ucy + ur * 0.2f)
        anchors[Anchor.U_CENTER] = PointF(ucx, ucy)
    }
}

/** Un seul exemplaire (utile pour dessiner les voisins d'une touffe) ; [seedDelta] le distingue, [straight] redresse sa tige. */
internal fun FungusLook.asSingle(seedDelta: Int = 5, straight: Boolean = false): FungusLook = this.let {
    FungusLook(
        capDiam = it.capDiam, capRise = it.capRise, capShape = it.capShape, capN = it.capN, edgeDrop = it.edgeDrop,
        umbo = it.umbo, dip = it.dip, inrolled = it.inrolled, capCenter = it.capCenter, capMid = it.capMid,
        capEdge = it.capEdge, capDecos = it.capDecos, fleshThick = it.fleshThick, stipeH = it.stipeH,
        stipeW = it.stipeW, stipeBaseW = it.stipeBaseW, stipeForm = it.stipeForm, bulbW = it.bulbW,
        bulbH = it.bulbH, lean = if (straight) 0f else it.lean, stipeTop = it.stipeTop, stipeBottom = it.stipeBottom,
        stipeDecos = it.stipeDecos, interior = it.interior, hymenium = it.hymenium, hymColor = it.hymColor,
        hymInner = it.hymInner, attach = it.attach, hymDepth = it.hymDepth, forked = it.forked, crowd = it.crowd,
        ring = it.ring, ringAt = it.ringAt, ringColor = it.ringColor, volva = it.volva, volvaH = it.volvaH,
        volvaColor = it.volvaColor, flesh = it.flesh, stipeFlesh = it.stipeFlesh, stains = it.stains,
        spore = it.spore, latex = it.latex, ball = it.ball, coral = it.coral, cluster = 1, onWood = it.onWood, seed = it.seed + seedDelta
    )
}
