package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Oxygène — la respiration, en planche d'anatomie gravée : le larynx et la trachée annelée, les
 * deux poumons et leurs lobes, l'arbre des bronches, le cœur logé dans l'échancrure du poumon
 * gauche. Les poumons se gonflent et se vident, l'air entre et sort par la trachée, le cœur bat.
 */
internal class SceneOxygen : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_breathing
    override val noteRes = R.string.card_note_breathing
    override val explainRes = R.string.card_explain_breathing

    override fun engrave(b: Burin) {
        // Le papier de la planche, à peine teinté, et l'ombre portée des poumons.
        b.glow(180f, 210f, 150f, 0xFFB9A88C.toInt(), 70)
    }

    // ───────────── les pièces ─────────────

    override fun sprites() = listOf(
        Sprite(HEART, RectF(156f, 150f, 244f, 298f)) { b -> heart(b) },
        Sprite(LUNG_R, RectF(48f, 90f, 176f, 304f)) { b -> lung(b, rightLung, 56f, rightFissures, rightRoots, 4) },
        Sprite(LUNG_L, RectF(186f, 90f, 314f, 304f)) { b -> lung(b, leftLung, 306f, leftFissures, leftRoots, 7) },
        Sprite(AIRWAY, RectF(138f, 58f, 230f, 186f)) { b -> airway(b) }
    )

    /** Le poumon droit (à gauche de l'image) : trois lobes. */
    private val rightLung = Path().apply {
        moveTo(128f, 98f)
        cubicTo(146f, 98f, 160f, 118f, 164f, 140f)
        cubicTo(168f, 170f, 165f, 210f, 170f, 250f)
        cubicTo(172f, 270f, 166f, 286f, 150f, 288f)
        cubicTo(130f, 282f, 100f, 268f, 78f, 280f)
        lineTo(61f, 297f)
        cubicTo(52f, 250f, 54f, 180f, 72f, 140f)
        cubicTo(84f, 116f, 104f, 98f, 128f, 98f)
        close()
    }

    /** Le poumon gauche : deux lobes, et l'échancrure où se loge le cœur. */
    private val leftLung = Path().apply {
        moveTo(232f, 98f)
        cubicTo(214f, 98f, 200f, 118f, 196f, 140f)
        cubicTo(193f, 165f, 196f, 186f, 202f, 200f)
        cubicTo(222f, 214f, 228f, 248f, 200f, 268f)
        cubicTo(214f, 284f, 252f, 264f, 282f, 278f)
        lineTo(299f, 296f)
        cubicTo(308f, 250f, 306f, 180f, 288f, 140f)
        cubicTo(276f, 116f, 256f, 98f, 232f, 98f)
        close()
    }

    private val rightFissures = listOf(
        Path().apply { moveTo(56f, 204f); cubicTo(90f, 196f, 130f, 200f, 166f, 192f) },
        Path().apply { moveTo(57f, 206f); cubicTo(80f, 236f, 104f, 262f, 124f, 284f) }
    )
    private val leftFissures = listOf(
        Path().apply { moveTo(304f, 182f); cubicTo(280f, 214f, 256f, 248f, 238f, 278f) }
    )

    /** Les bronches lobaires : point de départ, direction (radians), longueur, épaisseur. */
    private val rightRoots = listOf(
        floatArrayOf(150f, 178f, -2.25f, 24f, 6f),
        floatArrayOf(148f, 182f, 3.0f, 20f, 5.5f),
        floatArrayOf(150f, 184f, 1.95f, 30f, 6.5f)
    )
    private val leftRoots = listOf(
        floatArrayOf(214f, 174f, -.9f, 24f, 6f),
        floatArrayOf(216f, 178f, .45f, 20f, 5.5f),
        floatArrayOf(214f, 180f, 1.2f, 30f, 6.5f)
    )

    private fun lung(b: Burin, shape: Path, lateral: Float, fissures: List<Path>, roots: List<FloatArray>, seed: Int) {
        val medial = 180f
        b.wash(shape, LUNG, 250)
        // Le grain du tissu : des lobules en petites mailles, des marbrures.
        val rnd = Random(seed)
        b.c.save(); b.c.clipPath(shape)
        val mesh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = .4f; color = withAlpha(0xFF8A4A42.toInt(), 70)
        }
        repeat(420) {
            val x = lateral + (medial - lateral) * rnd.nextFloat()
            val y = 96f + rnd.nextFloat() * 204f
            val r = 2.4f + rnd.nextFloat() * 2.2f
            b.c.drawCircle(x, y, r, mesh)
        }
        b.c.restore()
        b.stipple(shape, 900, .5f, withAlpha(0xFF6E3530.toInt(), 110), seed = seed)
        // Le modelé : ombre sur le flanc et vers la base, lumière au milieu.
        b.hatch(shape, if (lateral < medial) 62f else 118f, 2f, .5f, Ink.SEPIA, Fade(lateral, 0f, (lateral + medial) / 2f, 0f, 230, 0))
        b.hatch(shape, if (lateral < medial) -28f else 28f, 2.6f, .45f, Ink.SEPIA, Fade(0f, 298f, 0f, 236f, 210, 0))
        b.wash(ellipse((lateral * .55f + medial * .45f), 150f, 22f, 34f), 0xFFFFF0E8.toInt(), 70)
        // L'arbre des bronches, comme si le tissu était transparent.
        b.c.save(); b.c.clipPath(shape)
        val segments = ArrayList<FloatArray>()
        val tree = Random(seed * 13)
        for (r in roots) branch(r[0], r[1], r[2], r[3], r[4], 6, tree, segments)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        pen.color = withAlpha(Ink.SEPIA, 210)
        for (s in segments) { pen.strokeWidth = s[4] + 1.1f; b.c.drawLine(s[0], s[1], s[2], s[3], pen) }
        pen.color = BRONCHUS
        for (s in segments) { pen.strokeWidth = s[4]; b.c.drawLine(s[0], s[1], s[2], s[3], pen) }
        b.c.restore()
        // Les scissures entre les lobes.
        b.c.save(); b.c.clipPath(shape)
        for (f in fissures) {
            b.stroke(f, .9f, withAlpha(Ink.SEPIA, 200))
            b.c.save(); b.c.translate(.8f, -.6f); b.stroke(f, .5f, withAlpha(0xFFFFFFFF.toInt(), 140)); b.c.restore()
        }
        b.c.restore()
        b.stroke(shape, 1.3f)
    }

    /** Une bronche qui se divise en deux, encore et encore, en s'amincissant. */
    private fun branch(x: Float, y: Float, angle: Float, len: Float, w: Float, depth: Int, rnd: Random, out: MutableList<FloatArray>) {
        val x2 = x + cos(angle) * len; val y2 = y + sin(angle) * len
        out += floatArrayOf(x, y, x2, y2, w)
        if (depth == 0) return
        for (side in floatArrayOf(-1f, 1f)) {
            val a = angle + side * (.34f + rnd.nextFloat() * .22f) + (rnd.nextFloat() - .5f) * .12f
            branch(x2, y2, a, len * (.7f + rnd.nextFloat() * .1f), w * .72f, depth - 1, rnd, out)
        }
    }

    private fun airway(b: Burin) {
        // Les bronches souches, de la carène aux poumons.
        val right = limb(176f, 150f, 150f, 178f, 12f, 9f)
        val left = limb(184f, 150f, 214f, 174f, 11f, 8.5f)
        val tubes = Path().apply {
            op(right, left, Path.Op.UNION)
            op(Path().apply { addCircle(180f, 149f, 8f, Path.Direction.CW) }, Path.Op.UNION)
        }
        b.body(tubes, CARTILAGE, 0f, outline = 0f, washAlpha = 255)
        b.hatch(tubes, 50f, 1.8f, .45f, Ink.SEPIA, Fade(0f, 180f, 0f, 150f, 200, 30))
        for ((x0, y0, x1, y1) in listOf(floatArrayOf(176f, 150f, 150f, 178f), floatArrayOf(184f, 150f, 214f, 174f))) {
            val n = 5
            for (i in 1 until n) {
                val f = i / n.toFloat()
                val cx = x0 + (x1 - x0) * f; val cy = y0 + (y1 - y0) * f
                val dx = (x1 - x0) / 30f; val dy = (y1 - y0) / 30f
                val w = 5.5f - f * 1.4f
                b.line(cx - dy * w, cy + dx * w, cx + dy * w, cy - dx * w, .55f, withAlpha(Ink.SEPIA, 200))
            }
        }
        b.stroke(tubes, 1f)
        // La trachée : des anneaux de cartilage, le cylindre ombré sur les bords.
        val trachea = Path().apply { addRoundRect(171f, 94f, 189f, 152f, 4f, 4f, Path.Direction.CW) }
        b.wash(trachea, 0xFFD9A79A.toInt(), 255)
        var y = 97f
        while (y < 148f) {
            val ring = Path().apply {
                moveTo(171f, y); quadTo(180f, y + 2f, 189f, y)
                lineTo(189f, y + 3.2f); quadTo(180f, y + 5.2f, 171f, y + 3.2f); close()
            }
            b.fill(ring, CARTILAGE)
            b.stroke(ring, .45f)
            y += 5f
        }
        b.hatch(trachea, 90f, 1.6f, .4f, Ink.SEPIA, Fade(171f, 0f, 178f, 0f, 220, 0))
        b.hatch(trachea, 90f, 1.6f, .4f, Ink.SEPIA, Fade(189f, 0f, 183f, 0f, 220, 0))
        b.stroke(trachea, 1f)
        // Le larynx : le bouclier du cartilage thyroïde, puis l'anneau cricoïde.
        val larynx = Path().apply {
            moveTo(167f, 66f); lineTo(177f, 66f); lineTo(180f, 72f); lineTo(183f, 66f); lineTo(193f, 66f)
            cubicTo(194f, 77f, 189f, 86f, 184f, 90f)
            lineTo(176f, 90f); cubicTo(171f, 86f, 166f, 77f, 167f, 66f); close()
        }
        b.body(larynx, CARTILAGE, .3f, 70f, Fade(164f, 0f, 180f, 0f, 220, 0), outline = 1f, washAlpha = 255)
        b.line(180f, 72f, 180f, 88f, .6f)
        val cricoid = Path().apply { addRoundRect(170f, 89.5f, 190f, 95.5f, 2.5f, 2.5f, Path.Direction.CW) }
        b.body(cricoid, CARTILAGE, .35f, 90f, outline = .8f, washAlpha = 255)
    }

    private fun heart(b: Burin) {
        // La veine cave et l'aorte, puis le cœur posé sur leurs racines.
        val cava = limb(172f, 160f, 172f, 216f, 8f, 9f)
        b.body(cava, 0xFF5A6E9A.toInt(), .35f, 90f, Fade(167f, 0f, 176f, 0f, 220, 0), outline = .8f, washAlpha = 245)
        val aorta = Path().apply {
            moveTo(194f, 210f); cubicTo(188f, 186f, 190f, 170f, 202f, 165f)
            cubicTo(214f, 160f, 223f, 168f, 223f, 184f)
        }
        b.c.drawPath(aorta, b.pen(Ink.SEPIA, 12.6f))
        b.c.drawPath(aorta, b.pen(0xFFC0574A.toInt(), 10.4f))
        b.c.save(); b.c.translate(-2.2f, -.8f)
        b.c.drawPath(aorta, b.pen(withAlpha(0xFFFFFFFF.toInt(), 90), 2f))
        b.c.restore()
        val heart = Path().apply {
            moveTo(168f, 214f)
            cubicTo(174f, 200f, 198f, 194f, 212f, 204f)
            cubicTo(226f, 214f, 228f, 244f, 220f, 268f)
            cubicTo(216f, 280f, 206f, 288f, 194f, 286f)
            cubicTo(176f, 280f, 162f, 244f, 168f, 214f)
            close()
        }
        b.wash(heart, 0xFFB24A3C.toInt(), 250)
        b.hatch(heart, 60f, 1.9f, .5f, Ink.SEPIA, Fade(172f, 0f, 204f, 0f, 220, 0))
        b.hatch(heart, -30f, 2.4f, .45f, Ink.SEPIA, Fade(0f, 290f, 0f, 250f, 200, 0))
        b.wash(ellipse(206f, 226f, 9f, 13f), 0xFFFFE4D8.toInt(), 60)
        // Les coronaires, dans leur sillon.
        b.taper(204f, 204f, 212f, 230f, 210f, 258f, 202f, 285f, 2.4f, 0xFF7A221A.toInt())
        b.taper(208f, 228f, 216f, 234f, 220f, 242f, 222f, 250f, 1.4f, 0xFF7A221A.toInt())
        b.taper(208f, 254f, 198f, 262f, 190f, 268f, 182f, 272f, 1.2f, 0xFF7A221A.toInt())
        b.stroke(heart, 1.1f)
    }

    // ───────────── animation ─────────────

    private val molecule = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .5f; color = 0xFF2F5C92.toInt() }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val breath = breath(t)
        val beat = heartbeat(t)
        c.save(); c.scale(1f + .035f * beat, 1f + .035f * beat, 200f, 248f)
        s.draw(c, HEART)
        c.restore()
        c.save(); c.scale(1f + .035f * breath, 1f + .05f * breath, 112f, 96f)
        s.draw(c, LUNG_R)
        c.restore()
        c.save(); c.scale(1f + .035f * breath, 1f + .05f * breath, 248f, 96f)
        s.draw(c, LUNG_L)
        c.restore()
        s.draw(c, AIRWAY)
        // L'air : des molécules d'O₂ qui descendent à l'inspiration et remontent à l'expiration.
        for ((p, path) in AIR.withIndex()) {
            for (i in 0 until 7) {
                val u = i / 7f * .42f + breath * .58f
                along(path, u)
                val x = ax; val y = ay
                val a = when {
                    u < .06f -> u / .06f
                    u > .9f -> (1f - u) / .1f
                    else -> 1f
                }
                val tilt = (i * 1.7f + p + t * 1.3f)
                val dx = cos(tilt) * 1.25f; val dy = sin(tilt) * 1.25f
                molecule.color = withAlpha(0xFFCFE8FF.toInt(), (240 * a).toInt())
                rim.alpha = (230 * a).toInt()
                for (k in floatArrayOf(-1f, 1f)) {
                    c.drawCircle(x + dx * k, y + dy * k, 1.5f, molecule)
                    c.drawCircle(x + dx * k, y + dy * k, 1.5f, rim)
                }
            }
        }
    }

    /** 0 = poumons vides, 1 = pleins. Inspiration courte, expiration plus longue. */
    private fun breath(t: Float): Float {
        val p = (t % 4.8f) / 4.8f
        fun smooth(x: Float) = x * x * (3 - 2 * x)
        return if (p < .4f) smooth(p / .4f) else 1f - smooth((p - .4f) / .6f)
    }

    /** Les deux bruits du cœur, « poum-poum », à 70 battements par minute. */
    private fun heartbeat(t: Float): Float {
        val q = t % .86f
        return max(exp(-((q - .05f) / .05f).let { it * it }), .6f * exp(-((q - .3f) / .05f).let { it * it }))
    }

    private var ax = 0f
    private var ay = 0f

    /** Le point à la fraction [u] d'une ligne brisée, rangé dans ([ax], [ay]). */
    private fun along(path: FloatArray, u: Float) {
        var total = 0f
        for (i in 0 until path.size / 2 - 1) total += hypot(path[i * 2 + 2] - path[i * 2], path[i * 2 + 3] - path[i * 2 + 1])
        var left = u.coerceIn(0f, 1f) * total
        for (i in 0 until path.size / 2 - 1) {
            val l = hypot(path[i * 2 + 2] - path[i * 2], path[i * 2 + 3] - path[i * 2 + 1])
            if (left <= l) {
                val f = left / l
                ax = path[i * 2] + (path[i * 2 + 2] - path[i * 2]) * f
                ay = path[i * 2 + 1] + (path[i * 2 + 3] - path[i * 2 + 1]) * f
                return
            }
            left -= l
        }
        ax = path[path.size - 2]; ay = path[path.size - 1]
    }

    private companion object {
        const val HEART = 1
        const val LUNG_R = 2
        const val LUNG_L = 3
        const val AIRWAY = 4
        const val LUNG = 0xFFE3A79C.toInt()
        const val CARTILAGE = 0xFFF1E6D6.toInt()
        const val BRONCHUS = 0xFFF6EDE2.toInt()
        val AIR = listOf(
            floatArrayOf(180f, 66f, 180f, 150f, 150f, 178f, 126f, 204f, 108f, 236f),
            floatArrayOf(180f, 70f, 180f, 150f, 214f, 174f, 238f, 202f, 252f, 236f)
        )
    }
}
