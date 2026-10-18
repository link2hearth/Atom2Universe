package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Magnésium — un feu d'artifice sur le fleuve, la nuit. En poudre, le magnésium brûle d'une
 * lumière blanche éblouissante : les fusées partent d'une barge, éclatent en pivoines
 * blanches, puis les étoiles crépitent avant de s'éteindre. L'eau renvoie chaque éclat.
 */
internal class SceneMagnesium : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_fireworks
    override val noteRes = R.string.card_note_fireworks
    override val explainRes = R.string.card_explain_fireworks

    override fun engrave(b: Burin) {
        val lit = ArrayList<Float>()
        skyline(b, lit)
        river(b, lit)
        barge(b)
        crowd(b)
    }

    private fun skyline(b: Burin, lit: MutableList<Float>) {
        val rnd = Random(12)
        val town = Path()
        val windows = ArrayList<RectF>()
        fun add(p: Path) { town.op(p, Path.Op.UNION) }
        var x = 40f
        while (x < 320f) {
            val w = 9f + rnd.nextFloat() * 13f
            val h = 12f + rnd.nextFloat() * 20f
            add(rect(x, BANK - h, x + w, BANK))
            if (rnd.nextInt(3) == 0) add(poly(x, BANK - h, x + w / 2f, BANK - h - w * .5f, x + w, BANK - h))
            var wy = BANK - h + 3.5f
            while (wy < BANK - 4f) {
                var wx = x + 2.5f
                while (wx < x + w - 3f) {
                    if (rnd.nextFloat() < .2f) windows += RectF(wx, wy, wx + 1.6f, wy + 2.4f)
                    wx += 4f
                }
                wy += 5.5f
            }
            x += w
        }
        // La cathédrale à gauche, deux tours et leurs flèches ; le dôme à droite.
        add(rect(72f, BANK - 52f, 84f, BANK)); add(rect(108f, BANK - 52f, 120f, BANK))
        add(poly(72f, BANK - 52f, 78f, BANK - 72f, 84f, BANK - 52f))
        add(poly(108f, BANK - 52f, 114f, BANK - 72f, 120f, BANK - 52f))
        add(rect(84f, BANK - 38f, 108f, BANK)); add(poly(84f, BANK - 38f, 96f, BANK - 50f, 108f, BANK - 38f))
        add(rect(244f, BANK - 30f, 268f, BANK))
        add(Path().apply { addArc(244f, BANK - 42f, 268f, BANK - 18f, 180f, 180f); close() })
        add(rect(254.5f, BANK - 49f, 257.5f, BANK - 40f))

        b.fill(town, 0xFF121828.toInt())
        b.hatch(town, 90f, 1.8f, .45f, 0xFF04060D.toInt())
        b.stroke(town, .7f, withAlpha(0xFF8A98BC.toInt(), 150))
        val warm = 0xFFF2C46A.toInt()
        for (w in windows) {
            b.c.drawRect(w, b.pen(withAlpha(warm, 215)))
            if (w.left.toInt() % 3 == 0) lit += w.centerX()
        }
        b.c.drawCircle(96f, BANK - 28f, 3.4f, b.pen(withAlpha(0xFFE9A85A.toInt(), 200)))
        b.c.drawCircle(96f, BANK - 28f, 3.4f, b.pen(0xFF04060D.toInt(), .6f))
        for (tx in floatArrayOf(78f, 114f)) b.c.drawRect(tx - 1.2f, BANK - 44f, tx + 1.2f, BANK - 38f, b.pen(withAlpha(warm, 160)))
    }

    private fun river(b: Burin, lit: List<Float>) {
        b.body(rect(40f, BANK, 320f, BANK + 6f), 0xFF2A3350.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        val water = rect(40f, BANK + 6f, 320f, PARAPET + 4f)
        b.fill(water, 0xFF141C33.toInt())
        b.hatch(water, 0f, 1.6f, .5f, 0xFF070C1A.toInt(), wobble = .4f)
        // Les fenêtres allumées se reflètent en traits tremblés.
        val rnd = Random(5)
        for (x in lit) for (k in 0 until 3) {
            val y = BANK + 9f + k * 4f + rnd.nextFloat() * 2f
            val l = 1.5f + rnd.nextFloat() * 2.5f
            b.c.drawLine(x - l, y, x + l, y, b.pen(withAlpha(0xFFF2C46A.toInt(), 110 - k * 30), .8f))
        }
    }

    /** La barge des artificiers, au milieu du fleuve, et ses mortiers. */
    private fun barge(b: Burin) {
        val hull = poly(146f, 280f, 218f, 280f, 212f, 288f, 152f, 288f)
        b.body(hull, 0xFF1C2234.toInt(), .5f, 0f, outline = .7f, washAlpha = 255)
        for (x in LAUNCHES) {
            b.body(poly(x - 2.2f, 280f, x - 1.6f, 272f, x + 1.6f, 272f, x + 2.2f, 280f), 0xFF3A4258.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
        }
        b.line(146f, 280f, 218f, 280f, .6f, withAlpha(0xFF8A98BC.toInt(), 160))
        // Son reflet sombre.
        b.c.drawRect(152f, 289f, 212f, 292f, b.pen(withAlpha(0xFF04060D.toInt(), 160)))
    }

    /** La foule sur le quai, en ombres chinoises, et le parapet devant elle. */
    private fun crowd(b: Burin) {
        val rnd = Random(8)
        val ink = 0xFF06080F.toInt()
        val rim = withAlpha(0xFFC8D0F0.toInt(), 90)
        var x = 42f
        while (x < 322f) {
            val s = .9f + rnd.nextFloat() * .35f
            val y = PARAPET + 2f + rnd.nextFloat() * 2f
            b.c.drawOval(x - 5.5f * s, y - 3f * s, x + 5.5f * s, y + 12f * s, b.pen(ink))
            b.c.drawCircle(x, y - 6f * s, 2.9f * s, b.pen(ink))
            b.c.drawArc(x - 2.9f * s, y - 8.9f * s, x + 2.9f * s, y - 3.1f * s, 200f, 140f, false, b.pen(rim, .6f))
            if (rnd.nextFloat() < .16f) {
                val side = if (rnd.nextBoolean()) 1f else -1f
                b.taper(x + side * 3f * s, y - 1f, x + side * 5f * s, y - 7f, x + side * 6f * s, y - 11f, x + side * 6.5f * s, y - 15f * s, 2.6f * s, ink)
            }
            x += 7.5f + rnd.nextFloat() * 4f
        }
        val wall = rect(40f, PARAPET + 8f, 320f, 340f)
        b.body(wall, 0xFF1F2638.toInt(), .45f, 0f, outline = 0f, washAlpha = 255)
        b.line(40f, PARAPET + 8f, 320f, PARAPET + 8f, .8f, withAlpha(0xFF8A98BC.toInt(), 150))
        var jx = 40f
        while (jx < 320f) { b.line(jx, PARAPET + 8f, jx, 340f, .5f, 0xFF0A0E18.toInt()); jx += 26f }
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(GLOW, RectF(-60f, -60f, 60f, 60f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 60f,
                intArrayOf(0xCCFFFFFF.toInt(), 0x55E8EEFF, 0x00E8EEFF), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 60f, b.p)
        },
        Sprite(SMOKE, RectF(-44f, -30f, 44f, 30f)) { b ->
            val rnd = Random(3)
            val puff = Path()
            repeat(7) {
                val px = (rnd.nextFloat() - .5f) * 50f; val py = (rnd.nextFloat() - .5f) * 22f
                puff.op(ellipse(px, py, 12f + rnd.nextFloat() * 8f, 8f + rnd.nextFloat() * 5f), Path.Op.UNION)
            }
            b.wash(puff, 0xFF9AA0B8.toInt(), 110)
            b.hatch(puff, 20f, 2.4f, .45f, withAlpha(0xFFD8DCEC.toInt(), 140), Fade(0f, -25f, 0f, 25f, 200, 0))
        }
    )

    // ───────────── animation ─────────────

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pts = FloatArray(SPARKS * 4)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        for (k in SHELLS.indices) {
            val sh = SHELLS[k]
            val clock = t + sh[4]
            val u = clock % PERIOD
            // Chaque tir éclate un peu ailleurs que le précédent.
            val cycle = (clock / PERIOD).toInt()
            val x = sh[0] + (hash(k, cycle, 1) - .5f) * 36f
            val y = sh[1] + (hash(k, cycle, 2) - .5f) * 20f
            val launch = LAUNCHES[k]
            if (u < RISE) rocket(c, k, launch, x, y, u / RISE, t)
            else burst(c, s, k, x, y, sh[3], u - RISE, t)
        }
    }

    /** La fusée qui monte, sa traînée dorée et les flammèches qui s'en détachent. */
    private fun rocket(c: Canvas, k: Int, lx: Float, x: Float, y: Float, q: Float, t: Float) {
        val e1 = 1f - (1f - q) * (1f - q)
        val q0 = (q - .2f).coerceAtLeast(0f)
        val e0 = 1f - (1f - q0) * (1f - q0)
        val x1 = lx + (x - lx) * e1 + 1.2f * sin(q * 30f); val y1 = LAUNCH_Y + (y - LAUNCH_Y) * e1
        val x0 = lx + (x - lx) * e0; val y0 = LAUNCH_Y + (y - LAUNCH_Y) * e0
        glow.color = withAlpha(0xFFFFB45A.toInt(), 90); glow.strokeWidth = 3f
        c.drawLine(x0, y0, x1, y1, glow)
        core.color = withAlpha(0xFFFFE6B8.toInt(), 230); core.strokeWidth = 1f
        c.drawLine(x0, y0, x1, y1, core)
        val frame = (t * 14f).toInt()
        core.strokeWidth = 1.3f
        for (j in 1..5) {
            val v = q - j * .05f
            if (v < 0f) break
            val e = 1f - (1f - v) * (1f - v)
            core.color = withAlpha(0xFFFFD08A.toInt(), 200 - j * 34)
            c.drawPoint(lx + (x - lx) * e + (hash(k, j, frame) - .5f) * 4f,
                LAUNCH_Y + (y - LAUNCH_Y) * e + j * 1.6f, core)
        }
        core.color = 0xFFFFFFFF.toInt(); core.strokeWidth = 2f
        c.drawPoint(x1, y1, core)
        // Le départ : un éclair sur la barge.
        if (q < .12f) {
            glow.color = withAlpha(0xFFFFC27A.toInt(), (160 * (1f - q / .12f)).toInt()); glow.strokeWidth = 5f
            c.drawPoint(lx, LAUNCH_Y, glow)
        }
        reflection(c, x1, .35f, 0xFFFFC27A.toInt(), t)
    }

    /** L'éclatement : l'éclair, la pivoine qui s'ouvre et retombe, puis les étoiles qui crépitent. */
    private fun burst(c: Canvas, s: Sprites, k: Int, x: Float, y: Float, radius: Float, a: Float, t: Float) {
        if (a > LIFE) return
        val fade = 1f - a / LIFE
        val color = COLORS[k]
        // La fumée reste et dérive avec le vent.
        val sc = (.7f + a * .22f) * radius / 50f
        c.save(); c.translate(x + a * 5f, y + 6f + a * 4f); c.scale(sc, sc)
        s.draw(c, SMOKE, alpha = (95f * fade * (a * 2f).coerceAtMost(1f)).toInt())
        c.restore()
        if (a < .6f) {
            val f = 1f - a / .6f
            val g = radius * 2.1f / 60f
            c.save(); c.translate(x, y); c.scale(g, g)
            s.draw(c, GLOW, alpha = (255f * f * f).toInt())
            c.restore()
        }
        val light = fade.pow(1.1f)
        val crackle = a > CRACKLE
        val frame = (t * 15f).toInt()
        val base = k * SPARKS
        // Chaque étoile laisse une traînée : quatre tronçons, du plus vif au plus pâle.
        for (j in 0 until TRAIL) {
            val head = a - j * SEG
            if (head <= 0f) break
            val tail = (head - SEG).coerceAtLeast(0f)
            val dh = radius * (1f - exp(-2.6f * head)); val dt = radius * (1f - exp(-2.6f * tail))
            val gh = DROOP * head * head; val gt = DROOP * tail * tail
            var n = 0
            for (i in 0 until SPARKS) {
                val dx = DIR[(base + i) * 2]; val dy = DIR[(base + i) * 2 + 1]
                pts[n++] = x + dx * dt; pts[n++] = y + dy * dt + gt
                pts[n++] = x + dx * dh; pts[n++] = y + dy * dh + gh
            }
            val seg = light * (1f - j / TRAIL.toFloat()) * (if (crackle) .45f else 1f)
            if (j == 0) {
                glow.color = withAlpha(color, (110 * seg).toInt()); glow.strokeWidth = 4f
                c.drawLines(pts, 0, n, glow)
            }
            core.color = withAlpha(if (j == 0) 0xFFFFFFFF.toInt() else color, (255 * seg).toInt())
            core.strokeWidth = 1.3f - j * .22f
            c.drawLines(pts, 0, n, core)
        }
        // Puis les étoiles crépitent : des points qui s'allument et s'éteignent au hasard.
        if (crackle) {
            val d = radius * (1f - exp(-2.6f * a)); val g = DROOP * a * a
            var n = 0
            for (i in 0 until SPARKS) {
                if (hash(i, k, frame) < .4f) continue
                pts[n++] = x + DIR[(base + i) * 2] * d; pts[n++] = y + DIR[(base + i) * 2 + 1] * d + g
            }
            glow.color = withAlpha(color, (130 * light).toInt()); glow.strokeWidth = 5f
            c.drawPoints(pts, 0, n, glow)
            core.color = withAlpha(0xFFFFFFFF.toInt(), (255 * light).toInt()); core.strokeWidth = 2.2f
            c.drawPoints(pts, 0, n, core)
        }
        reflection(c, x, light * (if (a < .6f) 1f else .55f), color, t)
    }

    /** Le reflet d'un éclat sur le fleuve : des traits qui tremblent sous la lumière. */
    private fun reflection(c: Canvas, x: Float, light: Float, color: Int, t: Float) {
        if (light < .03f) return
        var n = 0
        for (row in 0 until REFL_ROWS) {
            val y = BANK + 9f + row * 3.6f
            val w = REFL[row] * (1f + row * .14f)
            val jx = 2.2f * sin(t * 3.1f + row * 1.7f)
            pts[n++] = x - w + jx; pts[n++] = y; pts[n++] = x + w + jx; pts[n++] = y
        }
        core.color = withAlpha(color, (170 * light).toInt()); core.strokeWidth = 1f
        c.drawLines(pts, 0, n, core)
    }

    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val BANK = 256f
        const val PARAPET = 300f
        const val LAUNCH_Y = 272f
        const val PERIOD = 4.8f
        const val RISE = .9f
        const val LIFE = 3f
        const val CRACKLE = 1.5f
        const val DROOP = 9f
        const val SPARKS = 64
        const val TRAIL = 4
        const val SEG = .1f
        const val REFL_ROWS = 7
        const val GLOW = 1
        const val SMOKE = 2
        val LAUNCHES = floatArrayOf(160f, 200f, 180f, 170f)
        /** Les tirs : x, y d'éclatement, (inutilisé), rayon, décalage dans le temps. */
        val SHELLS = arrayOf(
            floatArrayOf(112f, 148f, 0f, 62f, 0f),
            floatArrayOf(242f, 136f, 0f, 70f, 1.2f),
            floatArrayOf(180f, 178f, 0f, 46f, 2.4f),
            floatArrayOf(168f, 118f, 0f, 56f, 3.6f)
        )
        val COLORS = intArrayOf(0xFFEEF2FF.toInt(), 0xFFFFEBC0.toInt(), 0xFFD8E4FF.toInt(), 0xFFFFF4E0.toInt())
        val REFL = floatArrayOf(5f, 7f, 4f, 8f, 6f, 9f, 5f)
        /** Les directions des étoiles : une sphère vue de face, plus serrée au bord. */
        val DIR = FloatArray(4 * SPARKS * 2).also { d ->
            val rnd = Random(21)
            for (k in 0 until 4) for (i in 0 until SPARKS) {
                val th = (i + rnd.nextFloat() * .6f) / SPARKS * 2f * PI.toFloat()
                val z = rnd.nextFloat() * 2f - 1f
                val rho = sqrt(1f - z * z).coerceAtLeast(.35f)
                d[(k * SPARKS + i) * 2] = cos(th) * rho
                d[(k * SPARKS + i) * 2 + 1] = sin(th) * rho
            }
        }
    }
}
