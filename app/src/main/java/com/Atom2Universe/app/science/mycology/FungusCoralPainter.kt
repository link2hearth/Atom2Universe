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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les champignons ramifiés, sans chapeau : [CoralKind.LOBES] (sparassis : un bloc de lobes plats et crépus, posés en
 * écailles les uns sur les autres) et [CoralKind.BRANCHES] (clavaire : un buisson de branches dressées qui se
 * divisent). Même repère que [SpecimenPainter] (centimètres, sol à y = 0, axe à x = 0) ; le sol est déjà dessiné par
 * l'appelant. `capDiam` = largeur, `capRise` = hauteur de la couronne, `stipeH` = hauteur de la base épaisse.
 * La vue de dessous montre le corail par en dessous : la base coupée au centre, les lobes ou les branches qui en partent.
 */
internal class CoralPainter(
    private val c: Canvas,
    private val look: FungusLook,
    private val ox: Float,
    private val oy: Float,
    private val s: Float,
    private val hair: Float,
    private val anchors: MutableMap<Anchor, PointF>
) {
    private val spec = look.coral ?: error("CoralPainter sans CoralSpec")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = look.capDiam / 2f
    private val baseH = look.stipeH
    private val crown = look.capRise
    private val stipeOutline = withAlpha(darken(look.stipeTop, 0.55f), 230)

    private fun px(x: Float) = ox + x * s
    private fun py(y: Float) = oy - y * s
    private fun anchor(a: Anchor, x: Float, y: Float) { anchors[a] = PointF(px(x), py(y)) }
    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, w: Float) = p.apply {
        reset(); isAntiAlias = true; style = Paint.Style.STROKE; this.color = color
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** De l'ombre à la lumière : t = 0 la couleur du cœur, 1 celle du bord. */
    private fun tone(t: Float) = if (t < 0.5f) mixColor(look.capCenter, look.capMid, t * 2f) else mixColor(look.capMid, look.capEdge, (t - 0.5f) * 2f)

    fun side() {
        if (spec.kind == CoralKind.LOBES) lobesSide() else branchesSide()
    }

    fun section() {
        if (spec.kind == CoralKind.LOBES) lobesSection() else branchesSection()
    }

    fun under(ucx: Float, ucy: Float, ur: Float) {
        if (spec.kind == CoralKind.LOBES) lobesUnder(ucx, ucy, ur) else branchesUnder(ucx, ucy, ur)
    }

    // ------------------------------------------------------------------ la base

    private fun stubPath(): Path {
        val bw = look.stipeBaseW / 2f
        val tw = look.stipeW / 2f
        val path = Path()
        path.moveTo(px(-bw), py(0f))
        path.cubicTo(px(-bw * 0.9f), py(baseH * 0.4f), px(-tw), py(baseH * 0.6f), px(-tw), py(baseH * 1.05f))
        path.quadTo(px(0f), py(baseH * (if (spec.kind == CoralKind.BRANCHES) 1.35f else 1.05f)), px(tw), py(baseH * 1.05f))
        path.cubicTo(px(tw), py(baseH * 0.6f), px(bw * 0.9f), py(baseH * 0.4f), px(bw), py(0f))
        path.close()
        return path
    }

    /** La base épaisse, vue de côté. */
    private fun drawStub() {
        val path = stubPath()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(0f), 0f, py(baseH), look.stipeBottom, look.stipeTop, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-look.stipeW / 2f), 0f, px(look.stipeW / 2f), 0f,
            intArrayOf(withAlpha(Color.BLACK, 80), 0, withAlpha(Color.WHITE, 60), 0, withAlpha(Color.BLACK, 90)),
            floatArrayOf(0f, 0.2f, 0.4f, 0.65f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-look.stipeW), py(baseH * 1.1f), px(look.stipeW), py(-0.1f), p)
        c.restore()
        stroke(stipeOutline, hair * 1.1f); c.drawPath(path, p)
    }

    /** La base coupée en deux : la chair, des fibres verticales, et un cœur plus coloré si l'espèce en a un. */
    private fun drawStubCut() {
        val path = stubPath()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, py(0f), 0f, py(baseH), darken(look.flesh, 0.06f), lighten(look.flesh, 0.08f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        if (look.stipeFlesh != look.flesh) {
            val core = RectF(px(-look.stipeW * 0.2f), py(baseH * 1.1f), px(look.stipeW * 0.2f), py(-0.1f))
            c.drawRoundRect(core, 0.3f * s, 0.3f * s, fill(withAlpha(look.stipeFlesh, 200)))
        }
        stroke(withAlpha(darken(look.flesh, 0.35f), 110), hair * 0.8f)
        for (k in -3..3) c.drawLine(px(k * look.stipeW * 0.13f), py(baseH * 1.05f), px(k * look.stipeBaseW * 0.14f), py(0f), p)
        c.restore()
        stroke(withAlpha(darken(look.flesh, 0.55f), 230), hair * 1.2f); c.drawPath(path, p)
    }

    // ------------------------------------------------------------------ lobes : une forme de lobe, en pixels

    private fun lobePoint(cx: Float, cy: Float, size: Float, rot: Float, phase: Float, t: Float, scale: Float): PointF {
        val rho = 1f + 0.15f * sin(5f * t + phase) + 0.06f * sin(8f * t + phase * 1.7f)
        val lx = size * scale * rho * cos(t) + size * 0.35f
        val ly = size * scale * 0.72f * rho * sin(t)
        return PointF(cx + lx * cos(rot) - ly * sin(rot), cy + lx * sin(rot) + ly * cos(rot))
    }

    private fun lobePath(cx: Float, cy: Float, size: Float, rot: Float, phase: Float, scale: Float = 1f): Path {
        val path = Path()
        val n = 60
        for (k in 0..n) {
            val pt = lobePoint(cx, cy, size, rot, phase, 2f * PI.toFloat() * k / n, scale)
            if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        path.close()
        return path
    }

    /** Un lobe : une feuille plate à bord ondulé, des nervures concentriques, un liseré qui brunit au bord. */
    private fun drawLobe(cx: Float, cy: Float, size: Float, rot: Float, phase: Float, color: Int) {
        val path = lobePath(cx, cy, size, rot, phase)
        c.drawPath(path, fill(color))
        c.save(); c.clipPath(path)
        stroke(withAlpha(darken(color, 0.4f), 80), hair * 0.8f)
        c.drawPath(lobePath(cx, cy, size, rot, phase, 0.72f), p)
        c.drawPath(lobePath(cx, cy, size, rot, phase, 0.46f), p)
        c.restore()
        val rim = Path()
        for (k in 0..14) {
            val pt = lobePoint(cx, cy, size, rot, phase, -1.1f + 2.2f * k / 14f, 1f)
            if (k == 0) rim.moveTo(pt.x, pt.y) else rim.lineTo(pt.x, pt.y)
        }
        stroke(withAlpha(spec.tip, 150), hair * 2f); c.drawPath(rim, p)
        stroke(withAlpha(darken(color, 0.5f), 210), hair * 1.1f); c.drawPath(path, p)
    }

    // ------------------------------------------------------------------ lobes : vue de côté, coupe, dessous

    private class Lobe(val x: Float, val y: Float, val z: Float, val size: Float, val rot: Float, val light: Float, val phase: Float)

    private fun lobesSide() {
        val a = r
        val b = crown / 2f
        val yc = baseH + b * 0.9f
        drawStub()
        // le dôme plein, pour qu'aucun jour ne laisse voir le papier entre les lobes
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(0f), py(yc), a * s,
            intArrayOf(darken(look.capCenter, 0.25f), look.capCenter), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP)
        val g = Random(look.seed + 301)
        val lobes = ArrayList<Lobe>()
        val golden = 2.39996f
        // les centres des lobes tiennent dans une ellipse plus petite : un lobe déborde de son centre d'environ 1,6 fois sa taille
        val ext = 0.38f * r
        val ap = a - ext
        val bp = max(b * 0.3f, b - ext)
        c.drawOval(RectF(px(-(ap + ext * 0.55f)), py(yc + bp + ext * 0.55f), px(ap + ext * 0.55f), py(yc - bp * 0.55f - ext * 0.55f)), p)
        // une jupe de lobes qui descend jusqu'au pied, pour que la masse ne flotte pas au-dessus de lui
        for (i in 0 until 12) {
            val ang = 2f * PI.toFloat() * i / 12f
            val x = ap * 0.5f * sin(ang)
            val y = baseH + ext * 0.5f + g.nextFloat() * 0.6f
            val size = r * 0.2f * (0.8f + 0.4f * g.nextFloat())
            lobes += Lobe(x, y, -0.6f + 0.3f * cos(ang), size, atan2(-0.8f, x / (0.5f * ap + 0.01f)), 0.12f + 0.1f * g.nextFloat(), g.nextFloat() * 6.28f)
        }
        for (i in 0 until spec.density) {
            // une spirale dorée sur la moitié haute d'une sphère aplatie : les lobes se répartissent sans trou
            val t = (i + 0.5f) / spec.density
            val phi = asin((-0.55f + 1.55f * t).coerceIn(-1f, 1f))
            val cp = cos(phi)
            val theta = i * golden
            val x = ap * cp * sin(theta)
            val y = yc + bp * sin(phi)
            val z = cp * cos(theta)
            if (z < -0.3f) continue
            val size = r * 0.2f * (0.8f + 0.4f * g.nextFloat()) * (0.8f + 0.2f * cp)
            val rot = atan2(y - yc, x) + (g.nextFloat() - 0.5f) * 0.7f
            val light = (0.35f + 0.35f * sin(phi) + 0.3f * z + (g.nextFloat() - 0.5f) * 0.12f).coerceIn(0f, 1f)
            lobes += Lobe(x, y, z, size, rot, light, g.nextFloat() * 6.28f)
        }
        lobes.sortBy { it.z }
        for (l in lobes) drawLobe(px(l.x), py(l.y), l.size * s, -l.rot, l.phase, tone(l.light))
        anchor(Anchor.CAP, -a * 0.3f, yc + b * 0.55f)
        anchor(Anchor.EDGE, a * 0.8f, yc + b * 0.1f)
        anchor(Anchor.BASE, look.stipeW / 2f * 0.95f, baseH * 0.4f)
    }

    private fun lobesSection() {
        val a = r
        val b = crown / 2f
        val yc = baseH + b * 0.9f
        drawStubCut()
        // le dôme coupé : un contour ondulé, rempli de chair
        val dome = Path()
        val n = 120
        for (i in 0..n) {
            val t = 2f * PI.toFloat() * i / n
            val x = a * 0.92f * cos(t) * (1f + 0.04f * sin(11f * t))
            val y = yc + b * 0.95f * sin(t) * (1f + 0.04f * sin(11f * t + 1f))
            if (i == 0) dome.moveTo(px(x), py(y)) else dome.lineTo(px(x), py(y))
        }
        dome.close()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(0f), py(yc), a * s,
            intArrayOf(lighten(look.flesh, 0.1f), look.flesh, darken(look.flesh, 0.08f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(dome, p)
        c.save(); c.clipPath(dome)
        // les lobes coupés : des feuilles ondulées qui partent de la base
        val g = Random(look.seed + 302)
        val sheets = 18
        for (k in 0 until sheets) {
            val ang = (-1.35f + 2.7f * k / (sheets - 1)) * (0.92f + 0.16f * g.nextFloat())
            val sx = 0f
            val sy = baseH + 0.3f
            val tx = a * 0.95f * sin(ang)
            val ty = yc + b * 0.97f * cos(ang * 0.8f).coerceAtLeast(-0.2f)
            val amp = 0.07f * r * (0.7f + 0.6f * g.nextFloat())
            val waves = 3f + 2f * g.nextFloat()
            val line = Path()
            for (j in 0..30) {
                val u = j / 30f
                val bx = sx + (tx - sx) * u
                val by = sy + (ty - sy) * u
                val len = max(0.01f, kotlin.math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy)))
                val nx = -(ty - sy) / len
                val ny = (tx - sx) / len
                val off = amp * sin(u * waves * PI.toFloat() + k) * u
                if (j == 0) line.moveTo(px(bx + nx * off), py(by + ny * off)) else line.lineTo(px(bx + nx * off), py(by + ny * off))
            }
            stroke(withAlpha(darken(look.flesh, 0.38f), 175), hair * 1.3f); c.drawPath(line, p)
            stroke(withAlpha(lighten(look.flesh, 0.3f), 150), hair * 0.7f); c.drawPath(line, p)
        }
        c.restore()
        stroke(withAlpha(darken(look.flesh, 0.55f), 235), hair * 1.3f); c.drawPath(dome, p)
        anchor(Anchor.S_FLESH, a * 0.35f, yc + b * 0.2f)
        anchor(Anchor.S_BASE, 0f, baseH * 0.4f)
    }

    private fun lobesUnder(ucx: Float, ucy: Float, ur: Float) {
        val g = Random(look.seed + 303)
        val scale = ur / r
        // le fond sombre, pour que les lobes du dessous paraissent plus profonds
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx, ucy, ur, intArrayOf(darken(look.capCenter, 0.4f), darken(look.capCenter, 0.15f), 0),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ucx, ucy, ur, p)
        // des anneaux de lobes, du bord vers le centre
        val rings = listOf(Triple(0.62f, 18, 0.2f), Triple(0.42f, 12, 0.18f), Triple(0.22f, 8, 0.15f))
        for ((ri, ring) in rings.withIndex()) {
            val (rad, count, size) = ring
            for (i in 0 until count) {
                val ang = 2f * PI.toFloat() * (i + 0.5f * (ri % 2)) / count + (g.nextFloat() - 0.5f) * 0.15f
                val cx = ucx + ur * rad * cos(ang)
                val cy = ucy + ur * rad * sin(ang)
                drawLobe(cx, cy, size * ur, ang, g.nextFloat() * 6.28f, tone((0.2f + 0.7f * (1f - rad)).coerceIn(0f, 1f) + (g.nextFloat() - 0.5f) * 0.1f))
            }
        }
        // la base coupée, au centre
        val sr = look.stipeBaseW / 2f * scale
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx, ucy, sr, intArrayOf(lighten(look.stipeBottom, 0.1f), look.stipeBottom), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ucx, ucy, sr, p)
        stroke(stipeOutline, hair * 1.2f); c.drawCircle(ucx, ucy, sr, p)
        anchors[Anchor.U_CENTER] = PointF(ucx, ucy)
        anchors[Anchor.U_RIM] = PointF(ucx + ur * 0.62f * cos(0.6f), ucy + ur * 0.62f * sin(0.6f))
    }

    // ------------------------------------------------------------------ branches

    /** Un tronçon de branche, dans un repère sans unité : de (x0, y0) à (x1, y1), courbé vers (cx, cy). */
    private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val cx: Float, val cy: Float, val w: Float, val depth: Int)

    /** L'arbre des branches, en unités où une branche principale mesure 1 ; il est ensuite mis à l'échelle de la couronne. */
    private fun tree(): List<Seg> {
        val g = Random(look.seed + 311)
        val segs = ArrayList<Seg>()
        fun branch(x0: Float, y0: Float, ang: Float, len: Float, w: Float, depth: Int) {
            val bend = (g.nextFloat() - 0.5f) * 0.25f
            val x1 = x0 + len * sin(ang + bend)
            val y1 = y0 + len * cos(ang + bend)
            segs += Seg(x0, y0, x1, y1, x0 + len * 0.55f * sin(ang), y0 + len * 0.55f * cos(ang), w, depth)
            if (depth >= 3) return
            val kids = if (depth == 0) 2 else if (g.nextFloat() < 0.35f) 3 else 2
            for (k in 0 until kids) {
                val side = if (kids == 2) (if (k == 0) -1f else 1f) else (k - 1f)
                branch(x1, y1, ang * 0.75f + side * (0.35f + 0.1f * g.nextFloat()), len * (0.66f + 0.1f * g.nextFloat()), w * 0.66f, depth + 1)
            }
        }
        val n = spec.density
        for (i in 0 until n) {
            val ang = -0.55f + 1.1f * i / max(1, n - 1) + (g.nextFloat() - 0.5f) * 0.1f
            branch(0f, 0f, ang, 1f, 0.2f, 0)
        }
        return segs
    }

    /** L'échelle (cm par unité) qui fait tenir l'arbre dans la couronne et la largeur de l'espèce. */
    private fun treeScale(segs: List<Seg>): Float {
        var maxY = 0.01f
        var maxX = 0.01f
        for (sg in segs) {
            maxY = max(maxY, sg.y1 + sg.w / 2f)
            maxX = max(maxX, max(kotlin.math.abs(sg.x1), kotlin.math.abs(sg.cx)) + sg.w / 2f)
        }
        return min(crown / maxY, r / maxX)
    }

    private fun branchColor(depth: Int) = tone((depth / 3f).coerceIn(0f, 1f))

    private fun quadPoint(sg: Seg, t: Float, k: Float): PointF {
        val u = 1f - t
        val x = u * u * sg.x0 + 2f * u * t * sg.cx + t * t * sg.x1
        val y = u * u * sg.y0 + 2f * u * t * sg.cy + t * t * sg.y1
        return PointF(k * x, baseH * 0.97f + k * y)
    }

    private fun segPath(sg: Seg, k: Float): Path {
        val a = quadPoint(sg, 0f, k)
        val cc = PointF(k * sg.cx, baseH * 0.97f + k * sg.cy)
        val b = quadPoint(sg, 1f, k)
        return Path().apply { moveTo(px(a.x), py(a.y)); quadTo(px(cc.x), py(cc.y), px(b.x), py(b.y)) }
    }

    private fun branchesSide() {
        drawStub()
        val segs = tree()
        val k = treeScale(segs)
        for (sg in segs) {
            val path = segPath(sg, k)
            val wpx = max(hair, sg.w * k * s)
            stroke(withAlpha(darken(branchColor(sg.depth), 0.5f), 235), wpx + hair * 2f); c.drawPath(path, p)
            stroke(branchColor(sg.depth), wpx); c.drawPath(path, p)
            if (sg.depth == 3) {
                val a = quadPoint(sg, 0.55f, k)
                val b = quadPoint(sg, 1f, k)
                stroke(spec.tip, wpx); c.drawLine(px(a.x), py(a.y), px(b.x), py(b.y), p)
            }
        }
        val left = segs.filter { it.depth == 1 }.minByOrNull { it.x1 }
        if (left != null) { val m = quadPoint(left, 0.5f, k); anchor(Anchor.CAP, m.x, m.y) }
        anchor(Anchor.BASE, look.stipeW / 2f * 0.95f, baseH * 0.4f)
    }

    private fun branchesSection() {
        drawStubCut()
        val segs = tree()
        val k = treeScale(segs)
        for (sg in segs) {
            val path = segPath(sg, k)
            val wpx = max(hair, sg.w * k * s)
            stroke(withAlpha(darken(look.flesh, 0.5f), 235), wpx + hair * 2f); c.drawPath(path, p)
            stroke(look.flesh, wpx); c.drawPath(path, p)
            if (look.stipeFlesh != look.flesh) { stroke(withAlpha(look.stipeFlesh, 210), wpx * 0.4f); c.drawPath(path, p) }
        }
        anchor(Anchor.S_FLESH, look.stipeW * 0.15f, baseH * 0.5f)
    }

    private fun branchesUnder(ucx: Float, ucy: Float, ur: Float) {
        val g = Random(look.seed + 313)
        val scale = ur / r
        val n = max(7, spec.density + 2)

        fun branch(x0: Float, y0: Float, ang: Float, len: Float, w: Float, depth: Int) {
            val x1 = x0 + len * cos(ang)
            val y1 = y0 + len * sin(ang)
            val col = branchColor(depth)
            val wpx = max(hair, w)
            stroke(withAlpha(darken(col, 0.5f), 235), wpx + hair * 2f); c.drawLine(x0, y0, x1, y1, p)
            stroke(col, wpx); c.drawLine(x0, y0, x1, y1, p)
            if (depth == 3) { stroke(spec.tip, wpx); c.drawLine(x0 + (x1 - x0) * 0.55f, y0 + (y1 - y0) * 0.55f, x1, y1, p) }
            if (depth >= 3) return
            for (k in 0 until 2) branch(x1, y1, ang + (if (k == 0) -1f else 1f) * (0.4f + 0.1f * g.nextFloat()), len * (0.66f + 0.1f * g.nextFloat()), w * 0.66f, depth + 1)
        }
        for (i in 0 until n) {
            val ang = 2f * PI.toFloat() * i / n + (g.nextFloat() - 0.5f) * 0.12f
            branch(ucx + ur * 0.1f * cos(ang), ucy + ur * 0.1f * sin(ang), ang, ur * 0.4f, ur * 0.07f, 0)
        }
        val sr = look.stipeBaseW / 2f * scale
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ucx, ucy, sr, intArrayOf(lighten(look.stipeBottom, 0.1f), look.stipeBottom), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ucx, ucy, sr, p)
        stroke(stipeOutline, hair * 1.2f); c.drawCircle(ucx, ucy, sr, p)
        anchors[Anchor.U_CENTER] = PointF(ucx, ucy)
        anchors[Anchor.U_RIM] = PointF(ucx + ur * 0.6f * cos(0.4f), ucy + ur * 0.6f * sin(0.4f))
    }
}
