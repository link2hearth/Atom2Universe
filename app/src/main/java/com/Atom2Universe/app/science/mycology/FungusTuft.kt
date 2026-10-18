package com.Atom2Universe.app.science.mycology

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Le cadre d'une planche de bouquet : largeur, hauteur totale, part sous la ligne y = 0 et distance du bord gauche à la face du tronc (cm). */
internal class TuftFrame(val width: Float, val height: Float, val below: Float, val originX: Float)

/**
 * Une place dans le bouquet : décalage de l'attache sur le tronc ([dy], en cm pour un chapeau de 5 cm), cap de départ
 * en degrés au-dessus de l'horizontale, échelle, et plan (-1 derrière le tronc, 0 le champignon principal, 1 devant le tronc).
 */
private class Slot(val dy: Float, val beta: Float, val k: Float, val layer: Int)

/**
 * La courbe d'une tige : attache, cap de départ [beta] (rad), bout de la courbe, et la courbe elle-même échantillonnée
 * ([xs], [ys] depuis l'attache, [ths] = le cap) à [TuftPainter.SAMPLES] + 1 points régulièrement répartis sur la longueur.
 */
private class Geo(val k: Float, val beta: Float, val baseX: Float, val baseY: Float, val split: Float,
                  val tipX: Float, val tipY: Float, val xs: FloatArray, val ys: FloatArray, val ths: FloatArray)

/**
 * Un bouquet qui sort du flanc d'un tronc debout (touffes sur le bois) : le tronc est dessiné à gauche, ses tiges en
 * partent à l'horizontale ou presque, puis se redressent, chaque chapeau au bout de la sienne.
 *
 * Chaque champignon est d'abord dessiné droit par [SpecimenPainter] dans un bitmap, puis déformé : le bas, jusqu'à
 * la naissance du chapeau, est plié le long d'un arc de cercle (cap de départ [Slot.beta], arrivée verticale) ; le
 * chapeau et le haut de la tige, eux, sont simplement posés au bout de l'arc. Ainsi les écailles, les anneaux et les
 * couleurs de la tige suivent la courbe sans être redessinés.
 *
 * Repère : cm, x = 0 à la face du tronc (le tronc est à x < 0), y = 0 en bas du dessin visible.
 */
internal class TuftPainter(
    private val c: Canvas,
    private val look: FungusLook,
    private val ox: Float,
    private val oy: Float,
    private val s: Float,
    private val hair: Float,
    private val anchors: MutableMap<Anchor, PointF>
) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val funnel = look.capShape == CapShape.FUNNEL
    private val tw = trunkWidth(look)

    private fun px(x: Float) = ox + x * s
    private fun py(y: Float) = oy - y * s
    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, w: Float) = p.apply {
        reset(); isAntiAlias = true; style = Paint.Style.STROKE; this.color = color
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    companion object {
        /** Bois visible sous la ligne y = 0, qui s'efface dans le papier. */
        private const val BELOW = 1.6f
        /** Tronc au-dessus du plus haut chapeau. */
        private const val HEAD = 0.6f
        /** Pas du maillage, en cm du champignon droit. */
        private const val STEP = 0.2f
        /** Nombre de tronçons de la courbe d'une tige. */
        const val SAMPLES = 40
        /** Force du crochet : plus c'est grand, plus la tige se redresse vite après sa sortie du tronc. */
        private const val HOOK = 2.2f

        private val SLOTS = listOf(
            Slot(0f, 10f, 1f, 0),
            Slot(-1.0f, 0f, 0.86f, -1),
            Slot(0.9f, 28f, 0.78f, -1),
            Slot(0.35f, 18f, 0.58f, 1),
            Slot(-1.3f, 2f, 0.5f, 1),
            Slot(1.9f, 36f, 0.64f, -1)
        )

        fun trunkWidth(look: FungusLook) = (look.capDiam * 0.55f).coerceIn(2.4f, 5f)

        /** Hauteur (cm du champignon droit) où la tige finit de se plier : sous le chapeau, qui reste rigide. */
        private fun splitOf(look: FungusLook): Float =
            if (look.capShape == CapShape.FUNNEL) look.stipeH - 0.1f
            else max(0.5f, look.stipeH - look.edgeDrop - look.capDiam / 2f * look.tilt - 0.15f)

        private fun topOf(look: FungusLook): Float =
            if (look.capShape == CapShape.FUNNEL) look.stipeH + look.capRise + look.capDiam * 0.36f + 0.3f
            else look.stipeH + look.capRise + look.umbo + 0.8f

        private fun halfWidthOf(look: FungusLook): Float =
            if (look.capShape == CapShape.FUNNEL) look.capDiam / 2f * 1.12f + 0.3f
            else max(look.capDiam / 2f * 1.06f, look.bulbW / 2f * 1.3f) + 0.3f

        private fun geometry(look: FungusLook, n: Int): List<Pair<Slot, Geo>> {
            val used = SLOTS.take(n)
            val d = (look.capDiam / 5f).coerceIn(0.8f, 1.6f)
            val anchorY = 1.2f - min(0f, used.minOf { it.dy } * d)
            val split = splitOf(look)
            return used.map { sl ->
                val b = Math.toRadians(sl.beta.toDouble()).toFloat()
                val bx = if (sl.layer == 0) -0.15f else -0.6f
                val by = anchorY + sl.dy * d
                // le cap passe de [b] à la verticale, vite au début (le crochet) puis plus doucement
                val xs = FloatArray(SAMPLES + 1)
                val ys = FloatArray(SAMPLES + 1)
                val ths = FloatArray(SAMPLES + 1)
                val ds = split * sl.k / SAMPLES
                fun heading(t: Float) = b + (PI.toFloat() / 2f - b) * (1f - (1f - t).pow(HOOK))
                ths[0] = b
                for (j in 0 until SAMPLES) {
                    val mid = heading((j + 0.5f) / SAMPLES)
                    xs[j + 1] = xs[j] + ds * cos(mid)
                    ys[j + 1] = ys[j] + ds * sin(mid)
                    ths[j + 1] = heading((j + 1f) / SAMPLES)
                }
                sl to Geo(sl.k, b, bx, by, split, bx + xs[SAMPLES], by + ys[SAMPLES], xs, ys, ths)
            }
        }

        /** Haut du tronc (cm) pour ces champignons. */
        private fun trunkTop(look: FungusLook, geos: List<Pair<Slot, Geo>>): Float =
            geos.maxOf { (_, g) -> g.tipY + (topOf(look) - 0.8f - g.split) * g.k } + HEAD

        /** Le cadre de la planche : [section] = un seul champignon (la coupe), sinon tout le bouquet. */
        fun frame(look: FungusLook, section: Boolean): TuftFrame {
            val geos = geometry(look, if (section) 1 else look.cluster.coerceIn(1, SLOTS.size))
            val xMax = geos.maxOf { (_, g) -> g.tipX + halfWidthOf(look) * g.k }
            return TuftFrame(trunkWidth(look) + xMax, trunkTop(look, geos) + BELOW, BELOW, trunkWidth(look))
        }
    }

    fun side() = render(look.cluster.coerceIn(1, SLOTS.size), section = false)

    fun section() = render(1, section = true)

    private fun render(n: Int, section: Boolean) {
        val geos = geometry(look, n)
        // derrière le tronc, puis le tronc, puis le principal et les petits qui sortent de sa face
        geos.forEachIndexed { i, (sl, g) -> if (sl.layer < 0) member(g, i, section) }
        drawTrunk(trunkTop(look, geos))
        geos.forEachIndexed { i, (sl, g) ->
            if (sl.layer == 0) {
                baseShadow(g)
                anchors.putAll(member(g, i, section))
            }
        }
        geos.forEachIndexed { i, (sl, g) -> if (sl.layer > 0) { baseShadow(g); member(g, i, section) } }
    }

    // ------------------------------------------------------------------ la tige pliée

    /** Où va le point (lx, ly) du champignon droit (cm, ly = hauteur au-dessus de son pied) une fois la tige pliée. */
    private fun place(g: Geo, lx: Float, ly: Float): PointF {
        val k = g.k
        if (ly >= g.split) return PointF(g.tipX + lx * k, g.tipY + (ly - g.split) * k)
        if (ly <= 0f) {
            // sous le pied : le prolongement de la tige dans le bois
            val d = ly * k
            return PointF(g.baseX + d * cos(g.beta) + lx * k * sin(g.beta), g.baseY + d * sin(g.beta) - lx * k * cos(g.beta))
        }
        val t = ly / g.split * SAMPLES
        val j = t.toInt().coerceIn(0, SAMPLES - 1)
        val fr = t - j
        val th = g.ths[j] + (g.ths[j + 1] - g.ths[j]) * fr
        val x = g.baseX + g.xs[j] + (g.xs[j + 1] - g.xs[j]) * fr + lx * k * sin(th)
        val y = g.baseY + g.ys[j] + (g.ys[j + 1] - g.ys[j]) * fr - lx * k * cos(th)
        return PointF(x, y)
    }

    /** Dessine un champignon droit dans un bitmap, le déforme sur la courbe, et renvoie ses repères à leur place. */
    private fun member(g: Geo, index: Int, section: Boolean): Map<Anchor, PointF> {
        val sk = s * g.k
        val half = halfWidthOf(look)
        val top = topOf(look)
        val below = 0.5f
        val bw = ceil(2f * half * sk).toInt().coerceAtLeast(2)
        val bh = ceil((top + below) * sk).toInt().coerceAtLeast(2)
        val halfW = bw / (2f * sk)
        val hEff = bh / sk
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val obx = bw / 2f
        val oby = (hEff - below) * sk
        val painter = SpecimenPainter(Canvas(bmp), look.asSingle(if (index == 0) 0 else 5 + 7 * index, straight = true), obx, oby, sk, hair)
        if (section) painter.sectionBody() else painter.drawMember(funnel)

        val rows = ceil(hEff / STEP).toInt().coerceAtLeast(4)
        val verts = FloatArray((rows + 1) * 4)
        for (j in 0..rows) {
            val ly = (hEff - below) - hEff * j / rows
            val l = place(g, -halfW, ly)
            val r = place(g, halfW, ly)
            verts[j * 4] = px(l.x); verts[j * 4 + 1] = py(l.y)
            verts[j * 4 + 2] = px(r.x); verts[j * 4 + 3] = py(r.y)
        }
        c.drawBitmapMesh(bmp, 1, rows, verts, 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()

        val out = HashMap<Anchor, PointF>()
        for ((a, pt) in painter.anchors) {
            val w = place(g, (pt.x - obx) / sk, (oby - pt.y) / sk)
            out[a] = PointF(px(w.x), py(w.y))
        }
        return out
    }

    /** Une ombre au point où la tige sort du bois. */
    private fun baseShadow(g: Geo) {
        val cx = px(g.baseX + 0.25f)
        val cy = py(g.baseY)
        val rad = 0.8f * s * g.k + 2f
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, rad,
            intArrayOf(withAlpha(0xFF160E08.toInt(), 160), withAlpha(0xFF160E08.toInt(), 60), 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad, p)
    }

    // ------------------------------------------------------------------ le tronc

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = RectF(px(cx - rx), py(cy + ry), px(cx + rx), py(cy - ry))

    /**
     * Un morceau de tronc debout : écorce sillonnée, nœuds, un peu de mousse, le haut scié avec ses cernes, le bas qui
     * s'efface dans le papier comme sur une planche de croquis.
     */
    private fun drawTrunk(top: Float) {
        val g = Random(look.seed + 211)
        val ryT = tw * 0.12f
        val bottom = -BELOW
        val pad = 4f
        val bounds = RectF(px(-tw) - pad, py(top + ryT) - pad, px(0f) + pad, py(bottom) + pad)
        val layer = c.saveLayer(bounds, null)

        // le fût : un cylindre dont le haut suit la moitié avant de la face sciée
        val body = Path()
        body.moveTo(px(-tw), py(top))
        body.lineTo(px(-tw), py(bottom))
        body.lineTo(px(0f), py(bottom))
        body.lineTo(px(0f), py(top))
        for (k in 1..24) {
            val a = PI.toFloat() * k / 24f
            body.lineTo(px(-tw / 2f + tw / 2f * cos(a)), py(top - ryT * sin(a)))
        }
        body.close()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(px(-tw), 0f, px(0f), 0f,
            intArrayOf(0xFF372A1E.toInt(), 0xFF6B543E.toInt(), 0xFF8C7358.toInt(), 0xFF5C4733.toInt(), 0xFF33271C.toInt()),
            floatArrayOf(0f, 0.28f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(body, p)

        c.save(); c.clipPath(body)
        // sillons de l'écorce, avec un éclat clair à leur droite
        val furrows = (tw * 3.2f).toInt().coerceAtLeast(7)
        for (k in 0 until furrows) {
            val x = -tw + tw * (k + 0.5f + (g.nextFloat() - 0.5f) * 0.55f) / furrows
            val u = (x + tw / 2f) / (tw / 2f)
            val yTop = top - ryT * sqrt(max(0f, 1f - u * u))
            val dark = Path(); val light = Path()
            dark.moveTo(px(x), py(yTop)); light.moveTo(px(x + 0.09f), py(yTop))
            var y = yTop
            var xx = x
            while (y > bottom) {
                y -= 0.4f + g.nextFloat() * 0.6f
                xx += (g.nextFloat() - 0.5f) * 0.12f
                val yy = max(y, bottom)
                dark.lineTo(px(xx), py(yy)); light.lineTo(px(xx + 0.09f), py(yy))
            }
            stroke(withAlpha(0xFF1E140C.toInt(), 150 + g.nextInt(70)), hair * (0.9f + g.nextFloat() * 0.8f)); c.drawPath(dark, p)
            stroke(withAlpha(0xFFB09474.toInt(), 70), hair * 0.7f); c.drawPath(light, p)
        }
        // quelques fentes en travers
        stroke(withAlpha(0xFF1E140C.toInt(), 120), hair * 0.9f)
        repeat((top * 0.9f).toInt().coerceAtLeast(4)) {
            val x = -tw + (0.1f + g.nextFloat() * 0.8f) * tw
            val y = bottom + g.nextFloat() * (top - bottom - ryT)
            c.drawLine(px(x), py(y), px(x + 0.2f + g.nextFloat() * 0.4f), py(y - 0.12f), p)
        }
        // deux nœuds : d'anciennes attaches de branches
        for ((kx, ky) in listOf(-tw * 0.7f to top * 0.72f, -tw * 0.3f to top * 0.18f)) {
            c.drawOval(oval(kx, ky, 0.2f, 0.34f), fill(0xFF2A1D13.toInt()))
            stroke(withAlpha(0xFFA88B68.toInt(), 150), hair)
            c.drawOval(oval(kx, ky, 0.3f, 0.48f), p)
        }
        // de la mousse, par plaques
        for ((mx, my, mr, a) in listOf(listOf(-tw * 0.75f, top * 0.46f, tw * 0.5f, 75f), listOf(-tw * 0.3f, top * 0.1f, tw * 0.55f, 60f),
            listOf(-tw * 0.2f, top * 0.86f, tw * 0.4f, 55f))) {
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(px(mx), py(my), mr * s,
                intArrayOf(withAlpha(0xFF78903F.toInt(), a.toInt()), withAlpha(0xFF5D7433.toInt(), (a * 0.5f).toInt()), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.save(); c.scale(0.7f, 1f, px(mx), py(my)); c.drawCircle(px(mx), py(my), mr * s, p); c.restore()
        }
        c.restore()

        // le haut scié : l'écorce en anneau, puis le bois avec ses cernes et quelques fentes
        c.drawOval(oval(-tw / 2f, top, tw / 2f, ryT), fill(0xFF4A3A2A.toInt()))
        val wood = oval(-tw / 2f, top + ryT * 0.06f, tw / 2f * 0.9f, ryT * 0.88f)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(px(-tw * 0.55f), py(top), tw * s,
            intArrayOf(0xFFE6D0A2.toInt(), 0xFFCDAF7C.toInt(), 0xFFA68558.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(wood, p)
        c.save(); c.clipPath(Path().apply { addOval(wood, Path.Direction.CW) })
        for (i in 1..4) {
            val f = i / 4.6f
            stroke(withAlpha(0xFF7D5E38.toInt(), 110), hair * 0.8f)
            c.drawOval(oval(-tw / 2f, top + ryT * 0.06f, tw / 2f * 0.9f * f, ryT * 0.88f * f), p)
        }
        stroke(withAlpha(0xFF4E3820.toInt(), 160), hair)
        for (a in listOf(0.35f, 2.2f, 3.9f)) {
            c.drawLine(px(-tw / 2f + tw * 0.05f * cos(a)), py(top + ryT * 0.1f * sin(a)),
                px(-tw / 2f + tw * 0.42f * cos(a)), py(top + ryT * 0.8f * sin(a)), p)
        }
        c.restore()
        stroke(withAlpha(0xFF2A1F15.toInt(), 230), hair * 1.2f); c.drawOval(oval(-tw / 2f, top, tw / 2f, ryT), p)

        // le bas s'efface dans le papier
        p.reset(); p.isAntiAlias = true
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        p.shader = LinearGradient(0f, py(0.8f), 0f, py(bottom), 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(bounds, p)
        p.xfermode = null
        c.restoreToCount(layer)
    }
}
