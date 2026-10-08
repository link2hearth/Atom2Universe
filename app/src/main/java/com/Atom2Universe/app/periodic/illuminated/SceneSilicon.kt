package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Silicium — la puce, sur sa carte électronique. Le boîtier est ouvert sur la pastille de
 * silicium, ses cœurs et ses fils d'or ; les signaux courent le long des pistes de cuivre,
 * entrent et sortent par les pattes, et les cœurs s'allument quand ils calculent.
 */
internal class SceneSilicon : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_microchip
    override val noteRes = R.string.card_note_microchip
    override val explainRes = R.string.card_explain_microchip

    /** Les pistes, chacune en quatre points : la patte, le coude, le second coude, le bout. */
    private val traces: Array<FloatArray>
    private val lengths: FloatArray
    private val vias: BooleanArray

    init {
        val list = ArrayList<FloatArray>()
        val ends = ArrayList<Boolean>()
        val dirs = arrayOf(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(0f, -1f))
        for (d in dirs) {
            val px = -d[1]; val py = d[0]
            for (i in 0 until PINS step 2) {
                val pos = (i - (PINS - 1) / 2f) * PITCH
                val sx = CX + d[0] * (HALF + 8f) + px * pos; val sy = CY + d[1] * (HALF + 8f) + py * pos
                val l1 = 6f + (i % 4) * 3f
                val ax = sx + d[0] * l1; val ay = sy + d[1] * l1
                val diag = abs(pos) * .55f
                val sg = sign(pos)
                val bx = ax + (d[0] + px * sg) * diag; val by = ay + (d[1] + py * sg) * diag
                val via = i % 4 == 2
                val l3 = if (via) 14f + (i % 3) * 6f else 140f
                list += floatArrayOf(sx, sy, ax, ay, bx, by, bx + d[0] * l3, by + d[1] * l3)
                ends += via
            }
        }
        traces = list.toTypedArray()
        vias = ends.toBooleanArray()
        lengths = FloatArray(traces.size) { k ->
            val tr = traces[k]
            hypot(tr[2] - tr[0], tr[3] - tr[1]) + hypot(tr[4] - tr[2], tr[5] - tr[3]) + hypot(tr[6] - tr[4], tr[7] - tr[5])
        }
    }

    override fun engrave(b: Burin) {
        board(b)
        parts(b)
        chip(b)
    }

    private fun board(b: Burin) {
        val board = rect(40f, 56f, 320f, 340f)
        b.fill(board, 0xFF2C6A48.toInt())
        b.hatch(board, 0f, 1.7f, .45f, 0xFF143C26.toInt())
        b.hatch(board, 90f, 3.4f, .35f, withAlpha(0xFF143C26.toInt(), 120))
        b.glow(CX, CY, 150f, 0xFF7CCB98.toInt(), 70)
        // Les pistes : du cuivre sous le vernis vert, plus clair que la carte.
        for ((k, tr) in traces.withIndex()) {
            val path = Path().apply { moveTo(tr[0], tr[1]); lineTo(tr[2], tr[3]); lineTo(tr[4], tr[5]); lineTo(tr[6], tr[7]) }
            b.c.drawPath(path, b.pen(0xFF123A24.toInt(), 3.4f).apply { strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER })
            b.c.drawPath(path, b.pen(0xFF5FAA74.toInt(), 2.2f).apply { strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER })
            if (vias[k]) {
                b.c.drawCircle(tr[6], tr[7], 3.4f, b.pen(0xFFD8B866.toInt()))
                b.c.drawCircle(tr[6], tr[7], 1.4f, b.pen(0xFF0E2618.toInt()))
                b.c.drawCircle(tr[6], tr[7], 3.4f, b.pen(Ink.SEPIA, .5f))
            }
        }
        // Un bus de pistes parallèles qui longe le bas de la carte.
        for (k in 0 until 5) {
            val y = 296f + k * 4.4f
            b.c.drawLine(40f, y, 320f, y, b.pen(0xFF123A24.toInt(), 3f))
            b.c.drawLine(40f, y, 320f, y, b.pen(0xFF5FAA74.toInt(), 1.8f))
        }
    }

    private fun parts(b: Burin) {
        val silk = withAlpha(0xFFF4F0E2.toInt(), 200)
        // Résistances et condensateurs montés en surface.
        val rnd = Random(7)
        for ((x, y, vertical) in listOf(Triple(70f, 110f, false), Triple(80f, 244f, true), Triple(288f, 240f, false),
            Triple(272f, 98f, true), Triple(96f, 160f, true), Triple(268f, 180f, false), Triple(120f, 274f, false), Triple(236f, 268f, false))) {
            val w = if (vertical) 6f else 14f; val h = if (vertical) 14f else 6f
            val body = rect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
            val ceramic = rnd.nextBoolean()
            b.body(body, if (ceramic) 0xFFB89A6A.toInt() else 0xFF26262A.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
            val pad = 0xFFD8D8D0.toInt()
            if (vertical) {
                b.body(rect(x - w / 2f, y - h / 2f, x + w / 2f, y - h / 2f + 3f), pad, 0f, outline = .5f, washAlpha = 255)
                b.body(rect(x - w / 2f, y + h / 2f - 3f, x + w / 2f, y + h / 2f), pad, 0f, outline = .5f, washAlpha = 255)
            } else {
                b.body(rect(x - w / 2f, y - h / 2f, x - w / 2f + 3f, y + h / 2f), pad, 0f, outline = .5f, washAlpha = 255)
                b.body(rect(x + w / 2f - 3f, y - h / 2f, x + w / 2f, y + h / 2f), pad, 0f, outline = .5f, washAlpha = 255)
            }
            b.c.drawRect(x - w / 2f - 2f, y - h / 2f - 2f, x + w / 2f + 2f, y + h / 2f + 2f, b.pen(silk, .5f))
        }
        // Le quartz de l'horloge : un boîtier ovale argenté.
        val crystal = Path().apply { addRoundRect(58f, 196f, 92f, 212f, 8f, 8f, Path.Direction.CW) }
        b.body(crystal, 0xFFC8CCD2.toInt(), .3f, 0f, Fade(0f, 196f, 0f, 212f, 0, 230), outline = .8f, washAlpha = 255)
        b.c.drawRect(62f, 199f, 74f, 201f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200)))
        // Un condensateur chimique, vu de dessus : son disque et sa croix d'évent.
        b.body(ellipse(286f, 140f, 14f, 14f), 0xFF2E4A8A.toInt(), .3f, 30f, Fade(272f, 126f, 300f, 154f, 0, 230), outline = .8f, washAlpha = 255)
        b.body(ellipse(286f, 140f, 10f, 10f), 0xFFC8CCD2.toInt(), .2f, 30f, outline = .6f, washAlpha = 255)
        b.line(280f, 134f, 292f, 146f, .7f); b.line(292f, 134f, 280f, 146f, .7f)
        b.c.drawArc(272f, 126f, 300f, 154f, 100f, 70f, false, b.pen(silk, 2.2f))
        // Les trous de fixation aux coins.
        for ((x, y) in listOf(54f to 300f, 306f to 300f)) {
            b.c.drawCircle(x, y, 6f, b.pen(0xFFD8B866.toInt()))
            b.c.drawCircle(x, y, 3.6f, b.pen(PAPER_DARK))
            b.c.drawCircle(x, y, 6f, b.pen(Ink.SEPIA, .6f))
        }
    }

    private fun chip(b: Burin) {
        // Les pattes, sur les quatre côtés.
        for (side in 0 until 4) for (i in 0 until PINS) {
            val pos = (i - (PINS - 1) / 2f) * PITCH
            val pin = when (side) {
                0 -> rect(CX + HALF, CY + pos - 1.5f, CX + HALF + 9f, CY + pos + 1.5f)
                1 -> rect(CX - HALF - 9f, CY + pos - 1.5f, CX - HALF, CY + pos + 1.5f)
                2 -> rect(CX + pos - 1.5f, CY + HALF, CX + pos + 1.5f, CY + HALF + 9f)
                else -> rect(CX + pos - 1.5f, CY - HALF - 9f, CX + pos + 1.5f, CY - HALF)
            }
            b.body(pin, 0xFFD8D8D0.toInt(), .2f, 0f, outline = .5f, washAlpha = 255)
        }
        // L'ombre portée, puis le boîtier noir biseauté.
        b.c.drawRect(CX - HALF + 4f, CY - HALF + 5f, CX + HALF + 5f, CY + HALF + 6f, b.pen(withAlpha(0xFF06140C.toInt(), 150)))
        val pack = rect(CX - HALF, CY - HALF, CX + HALF, CY + HALF)
        b.fill(pack, 0xFF2A2C32.toInt())
        b.stipple(pack, 900, .45f, withAlpha(0xFF5A5E68.toInt(), 200), 2)
        b.fill(poly(CX - HALF, CY - HALF, CX + HALF, CY - HALF, CX + HALF - 5f, CY - HALF + 5f, CX - HALF + 5f, CY - HALF + 5f), withAlpha(0xFFFFFFFF.toInt(), 50))
        b.fill(poly(CX - HALF, CY - HALF, CX - HALF + 5f, CY - HALF + 5f, CX - HALF + 5f, CY + HALF - 5f, CX - HALF, CY + HALF), withAlpha(0xFFFFFFFF.toInt(), 30))
        b.fill(poly(CX + HALF, CY - HALF, CX + HALF, CY + HALF, CX + HALF - 5f, CY + HALF - 5f, CX + HALF - 5f, CY - HALF + 5f), withAlpha(0xFF000000.toInt(), 70))
        b.fill(poly(CX - HALF, CY + HALF, CX + HALF, CY + HALF, CX + HALF - 5f, CY + HALF - 5f, CX - HALF + 5f, CY + HALF - 5f), withAlpha(0xFF000000.toInt(), 90))
        b.stroke(pack, .9f)
        b.c.drawCircle(CX - HALF + 11f, CY + HALF - 11f, 2.6f, b.pen(0xFF1A1B20.toInt()))
        b.c.drawCircle(CX - HALF + 11f, CY + HALF - 11f, 2.6f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 80), .5f))
        // La cavité ouverte, cerclée d'or, et les doigts du cadre de connexion.
        val cavity = rect(CX - 36f, CY - 36f, CX + 36f, CY + 36f)
        b.fill(cavity, 0xFF4A4438.toInt())
        for (side in 0 until 4) for (i in 0 until 10) {
            val pos = (i - 4.5f) * 6.4f
            val f = when (side) {
                0 -> rect(CX + 28f, CY + pos - 1.2f, CX + 35f, CY + pos + 1.2f)
                1 -> rect(CX - 35f, CY + pos - 1.2f, CX - 28f, CY + pos + 1.2f)
                2 -> rect(CX + pos - 1.2f, CY + 28f, CX + pos + 1.2f, CY + 35f)
                else -> rect(CX + pos - 1.2f, CY - 35f, CX + pos + 1.2f, CY - 28f)
            }
            b.gold(f)
        }
        b.goldStroke(Path().apply { addRect(CX - 37f, CY - 37f, CX + 37f, CY + 37f, Path.Direction.CW) }, 1.6f)
        // La pastille de silicium : reflets irisés, cœurs, mémoire, plots.
        val die = rect(CX - DIE, CY - DIE, CX + DIE, CY + DIE)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(CX - DIE, CY - DIE, CX + DIE, CY + DIE,
            intArrayOf(0xFF3A4A8A.toInt(), 0xFF6A4A9A.toInt(), 0xFF8A7A4A.toInt(), 0xFF3A7A7A.toInt(), 0xFF4A3A8A.toInt()),
            null, Shader.TileMode.CLAMP)
        b.c.drawPath(die, b.p)
        val fine = b.pen(withAlpha(0xFF0A0C18.toInt(), 150), .3f)
        var g = CX - DIE
        while (g < CX + DIE) { b.c.drawLine(g, CY - DIE, g, CY + DIE, fine); b.c.drawLine(CX - DIE, g - CX + CY, CX + DIE, g - CX + CY, fine); g += 2f }
        for (core in CORES) {
            val r = rect(core[0], core[1], core[0] + CORE, core[1] + CORE)
            b.wash(r, 0xFF9AB0E0.toInt(), 120)
            b.hatch(r, 45f, 1.2f, .3f, withAlpha(0xFF0A0C18.toInt(), 160))
            b.stroke(r, .5f, withAlpha(0xFFE8ECF8.toInt(), 200))
        }
        val cache = rect(CX - 18f, CY + 6f, CX + 18f, CY + 18f)
        b.wash(cache, 0xFFC8A860.toInt(), 120)
        b.hatchRect(RectF(CX - 18f, CY + 6f, CX + 18f, CY + 18f), 90f, 1f, .35f, withAlpha(0xFF0A0C18.toInt(), 170))
        b.stroke(cache, .5f, withAlpha(0xFFE8ECF8.toInt(), 200))
        b.stroke(die, .8f)
        // Les fils d'or, des plots de la pastille aux doigts du cadre.
        for (side in 0 until 4) for (i in 0 until 10) {
            val pos = (i - 4.5f) * 6.4f
            val padPos = (i - 4.5f) * 4f
            val (x0, y0, x1, y1) = when (side) {
                0 -> listOf(CX + DIE - 1.5f, CY + padPos, CX + 29f, CY + pos)
                1 -> listOf(CX - DIE + 1.5f, CY + padPos, CX - 29f, CY + pos)
                2 -> listOf(CX + padPos, CY + DIE - 1.5f, CX + pos, CY + 29f)
                else -> listOf(CX + padPos, CY - DIE + 1.5f, CX + pos, CY - 29f)
            }
            b.c.drawRect(x0 - 1f, y0 - 1f, x0 + 1f, y0 + 1f, b.pen(0xFFE8D8A0.toInt()))
            b.line(x0, y0, x1, y1, .55f, Gilding.DEEP)
            b.line(x0, y0, x1, y1, .3f, Gilding.LIGHT)
        }
    }

    // ───────────── animation ─────────────

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pts = FloatArray(512)
    private var ax = 0f
    private var ay = 0f

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les signaux : une impulsion par piste, qui entre dans la puce ou en sort.
        var n = 0
        for (k in traces.indices) {
            val len = lengths[k]
            val speed = 46f + (k * 37 % 5) * 9f
            val run = len + 40f
            var d = (t * speed + k * 53f) % run
            if (k % 3 == 1) d = run - d
            val head = d.coerceAtMost(len); val tail = (d - 12f).coerceIn(0f, len)
            if (head <= tail) continue
            point(k, tail); pts[n++] = ax; pts[n++] = ay
            point(k, head); pts[n++] = ax; pts[n++] = ay
        }
        glow.color = withAlpha(0xFF9FF6FF.toInt(), 90); glow.strokeWidth = 5f
        c.drawLines(pts, 0, n, glow)
        core.color = 0xFFE8FFFF.toInt(); core.strokeWidth = 1.6f
        c.drawLines(pts, 0, n, core)
        // Le bus du bas : un train d'impulsions qui file.
        n = 0
        for (k in 0 until 5) {
            val y = 296f + k * 4.4f
            for (j in 0 until 3) {
                val x = (t * (90f + k * 14f) + j * 110f + k * 40f) % 330f + 30f
                pts[n++] = x - 8f; pts[n++] = y; pts[n++] = x; pts[n++] = y
            }
        }
        glow.strokeWidth = 4f
        c.drawLines(pts, 0, n, glow)
        core.strokeWidth = 1.2f
        c.drawLines(pts, 0, n, core)
        // Les cœurs calculent : des cases s'allument et s'éteignent.
        val frame = (t * 10f).toInt()
        for ((q, coreBox) in CORES.withIndex()) {
            val busy = .5f + .5f * sin(t * (1.3f + q * .4f) + q * 1.7f)
            for (i in 0 until 5) for (j in 0 until 5) {
                if (hash(q * 25 + i * 5 + j, frame) > busy * .55f) continue
                cell.color = withAlpha(0xFFB8F4FF.toInt(), 170)
                val x = coreBox[0] + 1f + i * 2.2f; val y = coreBox[1] + 1f + j * 2.2f
                c.drawRect(x, y, x + 1.6f, y + 1.6f, cell)
            }
        }
        // Le quartz bat la mesure.
        cell.color = withAlpha(0xFF9FF6FF.toInt(), (60 + 60 * sin(t * 12f)).toInt().coerceAtLeast(0))
        c.drawRoundRect(56f, 194f, 94f, 214f, 9f, 9f, cell)
    }

    /** Le point à la distance [d] le long de la piste [k], rangé dans ([ax], [ay]). */
    private fun point(k: Int, d: Float) {
        val tr = traces[k]
        var rest = d
        for (s in 0 until 3) {
            val x0 = tr[s * 2]; val y0 = tr[s * 2 + 1]; val x1 = tr[s * 2 + 2]; val y1 = tr[s * 2 + 3]
            val l = hypot(x1 - x0, y1 - y0)
            if (rest <= l || s == 2) {
                val u = if (l > 0f) (rest / l).coerceIn(0f, 1f) else 0f
                ax = x0 + (x1 - x0) * u; ay = y0 + (y1 - y0) * u
                return
            }
            rest -= l
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CX = 180f
        const val CY = 178f
        const val HALF = 56f
        const val PINS = 12
        const val PITCH = 8.4f
        const val DIE = 22f
        const val CORE = 12f
        const val PAPER_DARK = 0xFF0E2618.toInt()
        /** Les quatre cœurs, coin haut-gauche de chacun. */
        val CORES = arrayOf(
            floatArrayOf(CX - 19f, CY - 19f), floatArrayOf(CX + 7f, CY - 19f),
            floatArrayOf(CX - 19f, CY - 6f + 0f), floatArrayOf(CX + 7f, CY - 6f)
        )
    }
}
