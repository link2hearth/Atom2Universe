package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Vanadium — le ressort de suspension. Un peu de vanadium rend l'acier dur et souple à la fois :
 * c'est l'acier des ressorts et des bons outils. Une vieille voiture roule ; au-dessus de la roue,
 * l'aile est dessinée en transparence, comme sur une planche d'atelier. À chaque bosse, la roue
 * saute, le ressort se comprime puis se détend, et la caisse ne bouge pas.
 */
internal class SceneVanadium : EngravedScene() {

    override val nameRes = R.string.card_scene_car_spring
    override val noteRes = R.string.card_note_car_spring
    override val explainRes = R.string.card_explain_car_spring

    private val shell = Path().apply {
        moveTo(38f, 136f)
        cubicTo(120f, 126f, 232f, 124f, 284f, 132f)
        cubicTo(306f, 136f, 316f, 152f, 320f, 180f)
        lineTo(322f, SHELL_BOTTOM); lineTo(38f, SHELL_BOTTOM); close()
    }
    private val arch = ellipse(HUB_X, HUB_Y + 2f, ARCH_R, ARCH_R)
    /** La fenêtre de transparence au-dessus de la roue. */
    private val window = Path().apply { addRoundRect(HUB_X - 34f, GHOST_TOP, HUB_X + 34f, HUB_Y, 8f, 8f, Path.Direction.CW) }
    /** Tout ce qu'on voit sous la tôle : la fenêtre et le passage de roue. */
    private val see = Path().apply { op(window, arch, Path.Op.UNION) }
    /** La tôle dessinée en transparence : la fenêtre, moins le passage de roue (là, il n'y a rien). */
    private val ghost = Path().apply { op(window, arch, Path.Op.DIFFERENCE) }
    /** Où la roue et le ressort se voient : sous la tôle, et sous la caisse jusqu'à la route. */
    private val wheelClip = Path().apply {
        op(see, rect(HUB_X - ARCH_R - 4f, SHELL_BOTTOM - 2f, HUB_X + ARCH_R + 4f, 330f), Path.Op.UNION)
    }
    /** Le pare-brise, à gauche : le ciel s'y voit, pas les arbres qui passent derrière. */
    private val glass = poly(38f, 60f, 86f, 60f, 64f, 135f, 38f, 137f)
    private val treeClip = Path().apply { op(rect(40f, 50f, 320f, 135f), glass, Path.Op.DIFFERENCE) }

    override fun engrave(b: Burin) {
        // Le paysage au loin, et la route qu'on voit sous la caisse.
        val hills = Path().apply {
            moveTo(40f, 128f); cubicTo(100f, 108f, 160f, 118f, 220f, 112f); cubicTo(260f, 108f, 290f, 100f, 320f, 108f)
            lineTo(320f, 150f); lineTo(40f, 150f); close()
        }
        b.body(hills, 0xFF98AE9C.toInt(), .22f, 0f, Fade(0f, 104f, 0f, 146f, 40, 170), outline = .6f, washAlpha = 150)
        val road = rect(40f, 262f, 320f, 340f)
        b.wash(road, 0xFF6A6A6C.toInt(), 255)
        b.hatchRect(RectF(40f, 262f, 320f, 340f), 0f, 1.7f, .45f, Ink.SEPIA, Fade(0f, 280f, 0f, 330f, 220, 120))
        // L'ombre de la voiture sur la route.
        b.washGradient(rect(40f, SHELL_BOTTOM, 320f, ROAD + 2f), 0xFF14100C.toInt(), 0f, SHELL_BOTTOM, 220, 0f, ROAD + 2f, 60)
        // Sous la tôle : le passage de roue sombre, et la coupelle haute de la jambe de force.
        val well = Path().apply { op(see, shell, Path.Op.INTERSECT) }
        b.c.save(); b.c.clipPath(well)
        b.fill(rect(100f, 120f, 260f, SHELL_BOTTOM), 0xFF2A2420.toInt())
        b.hatchRect(RectF(100f, 120f, 260f, SHELL_BOTTOM), 60f, 2.2f, .4f, 0xFF4A4440.toInt())
        b.glow(HUB_X, 170f, 46f, 0xFF7A6E62.toInt(), 120)
        b.c.restore()
        b.body(Path().apply { addRoundRect(HUB_X - 24f, TOP_SEAT - 9f, HUB_X + 24f, TOP_SEAT, 3f, 3f, Path.Direction.CW) },
            0xFF5A5E62.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        for (x in floatArrayOf(HUB_X - 17f, HUB_X + 17f)) b.c.drawCircle(x, TOP_SEAT - 4.5f, 1.8f, b.pen(0xFFB8BEC4.toInt()))
        // Le pare-brise : le ciel, un reflet, le volant derrière.
        b.washGradient(glass, 0xFF8AA4B4.toInt(), 0f, 60f, 140, 0f, 136f, 220)
        b.c.save(); b.c.clipPath(glass)
        b.c.drawOval(30f, 104f, 66f, 132f, b.pen(0xFF2A2420.toInt(), 3.4f))
        b.line(52f, 66f, 40f, 100f, 5f, withAlpha(0xFFFFFFFF.toInt(), 90))
        b.line(66f, 66f, 50f, 112f, 2f, withAlpha(0xFFFFFFFF.toInt(), 80))
        b.c.restore()
        b.line(86f, 60f, 64f, 135f, 3.4f, 0xFF8A9096.toInt())
        b.line(86f, 60f, 64f, 135f, 1.2f, 0xFFF0F2F4.toInt())
        // La caisse, percée de la fenêtre et du passage de roue.
        paintBody(b, Path().apply { op(shell, see, Path.Op.DIFFERENCE) })
        // Le phare rond, et le pare-chocs.
        b.body(ellipse(298f, 176f, 13f, 14f), 0xFFE8ECEE.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
        b.body(ellipse(298f, 176f, 9f, 10f), 0xFFF8F4D8.toInt(), 0f, outline = .7f, washAlpha = 255)
        b.c.drawCircle(295f, 172f, 2.4f, b.pen(0xFFFFFFFF.toInt()))
        val bumper = Path().apply { addRoundRect(272f, 262f, 330f, 276f, 6f, 6f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 262f, 0f, 276f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A9096.toInt(), 0xFFE8ECEE.toInt(), 0xFF4A5056.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(bumper, b.p)
        b.stroke(bumper, .9f)
        // Le rebord du passage de roue, hors de la transparence.
        b.c.save(); b.c.clipPath(shell); b.c.clipOutPath(window)
        archRim(b)
        b.c.restore()
    }

    /** La peinture de la caisse dans [body] : rouge dégradé, reflet du capot, porte, ouïes, jonc chromé. */
    private fun paintBody(b: Burin, body: Path) {
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 126f, 0f, SHELL_BOTTOM,
            intArrayOf(0xFFE07A62.toInt(), 0xFFB0342C.toInt(), 0xFF7A1E18.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(body, b.p)
        b.hatch(body, 0f, 2f, .45f, Ink.SEPIA, Fade(0f, SHELL_BOTTOM, 0f, 190f, 210, 0))
        b.c.save(); b.c.clipPath(body)
        b.stroke(Path().apply { moveTo(90f, 138f); cubicTo(150f, 132f, 230f, 131f, 280f, 138f) }, 1.6f, withAlpha(0xFFFFE0D0.toInt(), 220))
        b.line(64f, 156f, 300f, 154f, .6f, withAlpha(Ink.SEPIA, 180))
        // La porte, sa poignée.
        b.line(64f, 137f, 62f, SHELL_BOTTOM, .9f, withAlpha(Ink.SEPIA, 220))
        b.line(46f, 174f, 56f, 174f, 2.6f, 0xFFE8ECEE.toInt())
        // Les ouïes du capot.
        for (k in 0 until 6) {
            val x = 92f + k * 8f
            b.line(x, 170f, x + 4f, 196f, 2.2f, 0xFF5A1410.toInt())
            b.line(x + 1.6f, 170f, x + 5.6f, 196f, .7f, withAlpha(0xFFFFC8B8.toInt(), 200))
        }
        // Le jonc chromé sur le flanc.
        b.line(40f, 228f, 320f, 226f, 2.4f, 0xFFE8ECEE.toInt())
        b.line(40f, 229.4f, 320f, 227.4f, .6f, 0xFF5A6066.toInt())
        b.c.restore()
        b.stroke(body, 1.1f)
    }

    private fun archRim(b: Burin) {
        b.c.drawCircle(HUB_X, HUB_Y + 2f, ARCH_R, b.pen(Ink.SEPIA, 2.4f))
        b.c.drawCircle(HUB_X, HUB_Y + 2f, ARCH_R + 2.4f, b.pen(withAlpha(0xFFFFC8B8.toInt(), 160), 1f))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(WHEEL, RectF(-TIRE_R - 2f, -TIRE_R - 2f, TIRE_R + 2f, TIRE_R + 2f)) { b ->
            // Le pneu et ses sculptures.
            b.c.drawCircle(0f, 0f, TIRE_R - 6f, b.pen(0xFF1E1E20.toInt(), 12f))
            val tread = b.pen(0xFF4A4A4C.toInt(), 1.4f)
            for (k in 0 until 36) {
                val a = k * PI.toFloat() / 18f
                b.c.drawLine(cos(a) * (TIRE_R - 4f), sin(a) * (TIRE_R - 4f), cos(a + .06f) * (TIRE_R - .5f), sin(a + .06f) * (TIRE_R - .5f), tread)
            }
            b.c.drawCircle(0f, 0f, TIRE_R, b.pen(Ink.SEPIA, 1f))
            // Le flanc blanc, la jante rouge, l'enjoliveur chromé.
            b.c.drawCircle(0f, 0f, TIRE_R - 10f, b.pen(0xFFF2EEE4.toInt(), 5f))
            b.c.drawCircle(0f, 0f, TIRE_R - 13f, b.pen(0xFFA8302A.toInt()))
            b.c.drawCircle(0f, 0f, TIRE_R - 13f, b.pen(Ink.SEPIA, .8f))
            for (k in 0 until 5) {
                val a = k * 2f * PI.toFloat() / 5f
                b.c.drawCircle(cos(a) * 24f, sin(a) * 24f, 2.2f, b.pen(0xFF5A1814.toInt()))
            }
            b.pen(0xFF000000.toInt()).shader = LinearGradient(-18f, -18f, 18f, 18f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFF9AA0A6.toInt(), 0xFFF0F2F4.toInt(), 0xFF4A5056.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 17f, b.p)
            b.c.drawCircle(0f, 0f, 17f, b.pen(Ink.SEPIA, .8f))
            b.c.drawCircle(0f, 0f, 5f, b.pen(0xFFA8302A.toInt()))
            b.c.drawCircle(0f, 0f, 5f, b.pen(Ink.SEPIA, .6f))
        },
        Sprite(TREE, RectF(-12f, -40f, 12f, 2f)) { b ->
            b.line(0f, 0f, 0f, -10f, 2f, Ink.BROWN)
            val crown = Path().apply { addCircle(0f, -22f, 11f, Path.Direction.CW) }
            b.body(crown, 0xFF5A8A44.toInt(), .45f, 60f, Fade(10f, -14f, -6f, -30f, 230, 10), outline = .7f, washAlpha = 255)
        },
        // La tôle en transparence : la même peinture, posée par-dessus le ressort, à peine visible.
        Sprite(GHOST, RectF(HUB_X - 36f, GHOST_TOP - 2f, HUB_X + 36f, HUB_Y + 2f)) { b ->
            paintBody(b, Path().apply { op(ghost, shell, Path.Op.INTERSECT) })
            b.c.save(); b.c.clipPath(window)
            archRim(b)
            b.c.restore()
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.1f; color = Ink.SEPIA
        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }
    private val hump = Path()
    private val back = FloatArray(SEGMENTS * 4)
    private val front = FloatArray(SEGMENTS * 4)
    private var nBack = 0
    private var nFront = 0

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val road = t * SPEED
        // Les arbres défilent au-dessus du capot.
        val step = 64f
        val first = floor(road * .35f / step).toInt()
        c.save(); c.clipPath(treeClip)
        for (i in first..first + 6) s.draw(c, TREE, dx = 30f + i * step - road * .35f + 18f * hash(i, 1), dy = 140f + 4f * hash(i, 2))
        c.restore()
        // Les dos-d'âne de la route, et la roue qui les suit.
        val lift = lift(road)
        for (k in -1..1) {
            val x = HUB_X + k * GAP - (road % GAP) + GAP * .5f
            if (x < 20f || x > 340f) continue
            hump.reset()
            hump.moveTo(x - HUMP_W / 2f, ROAD)
            hump.cubicTo(x - HUMP_W * .2f, ROAD - HUMP_H * 1.3f, x + HUMP_W * .2f, ROAD - HUMP_H * 1.3f, x + HUMP_W / 2f, ROAD)
            hump.close()
            fill.color = 0xFF7A7A7C.toInt(); c.drawPath(hump, fill)
            ink.color = 0xFFE0B030.toInt(); ink.strokeWidth = 1.6f
            c.drawLine(x - 6f, ROAD - HUMP_H * .9f, x + 6f, ROAD - HUMP_H * .9f, ink)
            ink.color = Ink.SEPIA; ink.strokeWidth = 1f; c.drawPath(hump, ink)
        }
        val hubY = HUB_Y - lift
        val seat = hubY - TIRE_R - 6f
        c.save(); c.clipPath(wheelClip)
        // La jambe de force : le ressort derrière, l'amortisseur, le ressort devant, la coupelle, puis la roue.
        spring(c, seat)
        drawCoils(c, back, 3.4f, 0xFF2A3A4A.toInt())
        ink.color = Ink.SEPIA; ink.strokeWidth = 5f
        c.drawLine(HUB_X, TOP_SEAT, HUB_X, seat - 20f, ink)
        ink.color = 0xFFE8ECEE.toInt(); ink.strokeWidth = 3f
        c.drawLine(HUB_X, TOP_SEAT, HUB_X, seat - 20f, ink)
        fill.color = 0xFF3A3E42.toInt()
        c.drawRect(HUB_X - 8f, seat - 22f, HUB_X + 8f, hubY, fill)
        drawCoils(c, front, 4.4f, 0xFF4A6A8A.toInt())
        fill.color = 0xFF5A5E62.toInt()
        c.drawRoundRect(HUB_X - COIL_R - 5f, seat - 1f, HUB_X + COIL_R + 5f, seat + 5f, 2f, 2f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .8f
        c.drawRoundRect(HUB_X - COIL_R - 5f, seat - 1f, HUB_X + COIL_R + 5f, seat + 5f, 2f, 2f, ink)
        s.draw(c, WHEEL, dx = HUB_X, dy = hubY, deg = road / TIRE_R * 180f / PI.toFloat())
        c.restore()
        // La tôle en transparence par-dessus, et son bord en pointillé.
        s.draw(c, GHOST, alpha = 80)
        c.drawPath(ghost, dash)
        // Un peu de poussière quand la roue retombe.
        if (lift > 1f) {
            for (k in 0 until 4) {
                val a = hash((t * 20f).toInt(), k)
                fill.color = withAlpha(0xFFB8AE9C.toInt(), (160 * lift / HUMP_H).toInt())
                c.drawCircle(HUB_X - 30f - a * 20f, ROAD - 2f - a * 6f, 1.4f + a, fill)
            }
        }
    }

    /** Le ressort, en hélice de la coupelle basse [low] à la coupelle haute, rangé en moitié arrière et avant. */
    private fun spring(c: Canvas, low: Float) {
        var nb = 0; var nf = 0
        var px = HUB_X; var py = low
        for (i in 1..SEGMENTS) {
            val u = i / SEGMENTS.toFloat()
            val a = u * TURNS * 2f * PI.toFloat()
            val x = HUB_X + COIL_R * sin(a)
            val y = low + (TOP_SEAT - low) * u
            val arr = if (cos(a) >= 0f) front else back
            val n = if (arr === front) nf++ else nb++
            arr[n * 4] = px; arr[n * 4 + 1] = py; arr[n * 4 + 2] = x; arr[n * 4 + 3] = y
            px = x; py = y
        }
        nBack = nb; nFront = nf
    }

    private fun drawCoils(c: Canvas, arr: FloatArray, w: Float, color: Int) {
        val n = if (arr === front) nFront else nBack
        ink.color = Ink.SEPIA; ink.strokeWidth = w + 1.6f
        c.drawLines(arr, 0, n * 4, ink)
        ink.color = color; ink.strokeWidth = w
        c.drawLines(arr, 0, n * 4, ink)
        if (arr === front) {
            ink.color = withAlpha(0xFFC8D8E8.toInt(), 200); ink.strokeWidth = w * .3f
            c.drawLines(arr, 0, n * 4, ink)
        }
    }

    /** La hauteur dont la roue est soulevée : le profil des bosses, un peu élargi (la roue est ronde). */
    private fun lift(road: Float): Float {
        // La bosse passe sous la roue quand road % GAP vaut GAP / 2 (voir le dessin des bosses).
        val d = (road % GAP) - GAP * .5f
        val w = HUMP_W * .75f
        return if (abs(d) < w) HUMP_H * cos(d / w * PI.toFloat() / 2f).let { it * it } else 0f
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val ROAD = 300f
        const val SHELL_BOTTOM = 286f
        const val HUB_X = 180f
        const val TIRE_R = 50f
        const val HUB_Y = ROAD - TIRE_R
        const val ARCH_R = 60f
        const val GHOST_TOP = 134f
        const val TOP_SEAT = 148f
        const val COIL_R = 18f
        const val TURNS = 6f
        const val SEGMENTS = 144
        const val SPEED = 120f
        const val GAP = 300f
        const val HUMP_W = 46f
        const val HUMP_H = 14f
        const val WHEEL = 1
        const val TREE = 2
        const val GHOST = 3
    }
}
