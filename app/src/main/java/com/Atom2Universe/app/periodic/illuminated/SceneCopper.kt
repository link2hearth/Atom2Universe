package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Cuivre — le métal de l'électricité. Sur l'établi d'un électricien : un moteur ouvert, les
 * bobines de fil de cuivre autour de ses pôles, une bobine de fil neuf, et un interrupteur à
 * couteau dont la lame de cuivre ferme le circuit. Au mur, la tuyauterie de cuivre.
 *
 * Le couteau fermé, le courant court dans le fil, le rotor prend sa vitesse et les balais
 * crépitent. On l'ouvre : un arc jaillit, le rotor ralentit sur sa lancée ; puis on referme.
 */
internal class SceneCopper : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_electric_motor
    override val noteRes = R.string.card_note_electric_motor
    override val explainRes = R.string.card_explain_electric_motor

    override fun engrave(b: Burin) {
        wall(b)
        pipes(b)
        bench(b)
        // Tout ce qui est posé sur l'établi est dessiné l'établi en [TOP], puis descendu de [DY].
        b.c.save(); b.c.translate(0f, DY)
        supply(b)
        spool(b)
        switchBase(b)
        wire(b, Path().apply { moveTo(174f, 230f); cubicTo(186f, 228f, 168f, 204f, 165f, 182f) })
        motor(b)
        b.c.restore()
    }

    // ───────────── le mur et la tuyauterie ─────────────

    private fun wall(b: Burin) {
        val wall = rect(40f, 50f, 320f, BENCH)
        b.wash(wall, 0xFFB9BCAA.toInt(), 255)
        b.glow(110f, 120f, 190f, 0xFFF4E2B8.toInt(), 120)
        b.washGradient(wall, 0xFF4E4A3A.toInt(), 0f, 70f, 0, 0f, BENCH, 120)
        b.hatchRect(RectF(40f, 50f, 320f, BENCH), 0f, 2.8f, .4f, Ink.BROWN, Fade(0f, 70f, 0f, BENCH, 30, 150))
    }

    private fun pipes(b: Burin) {
        val run = Path().apply {
            moveTo(30f, PIPE_Y); lineTo(PIPE_X - 12f, PIPE_Y)
            quadTo(PIPE_X, PIPE_Y, PIPE_X, PIPE_Y + 12f); lineTo(PIPE_X, BENCH + 4f)
        }
        // L'ombre portée sur le mur, puis le tuyau.
        b.c.save(); b.c.translate(3f, 4f); b.stroke(run, 8f, withAlpha(0xFF3A3428.toInt(), 70)); b.c.restore()
        b.stroke(run, 8.8f, Ink.SEPIA)
        b.stroke(run, 7f, COPPER)
        b.c.save(); b.c.translate(1.6f, 1.6f); b.stroke(run, 2.4f, withAlpha(0xFF6A2E10.toInt(), 200)); b.c.restore()
        b.c.save(); b.c.translate(-1.3f, -1.3f); b.stroke(run, 1.6f, withAlpha(0xFFFFE0B8.toInt(), 235)); b.c.restore()
        // Les manchons soudés, puis les colliers qui tiennent le tuyau au mur.
        for (x in floatArrayOf(118f, 214f)) collar(b, x - 4.5f, PIPE_Y - 5.2f, x + 4.5f, PIPE_Y + 5.2f)
        collar(b, PIPE_X - 5.2f, 196f, PIPE_X + 5.2f, 205f)
        for (x in floatArrayOf(70f, 166f, 252f)) clamp(b, x, PIPE_Y, true)
        for (y in floatArrayOf(120f, 222f)) clamp(b, PIPE_X, y, false)
        valve(b)
    }

    private fun collar(b: Burin, l: Float, t: Float, r: Float, bt: Float) {
        val shape = rect(l, t, r, bt)
        b.wash(shape, COPPER, 255)
        b.washGradient(shape, 0xFFFFE0B8.toInt(), l, t, 200, r, bt, 0)
        b.hatch(shape, 90f, 1.4f, .4f, Ink.SEPIA, Fade(r, bt, l, t, 220, 0))
        b.stroke(shape, .8f)
    }

    private fun clamp(b: Burin, x: Float, y: Float, horizontal: Boolean) {
        val strap = if (horizontal) rect(x - 2f, y - 6.4f, x + 2f, y + 6.4f) else rect(x - 6.4f, y - 2f, x + 6.4f, y + 2f)
        b.body(strap, 0xFF7E858C.toInt(), 0f, outline = .7f, washAlpha = 255)
        val screw = b.pen(Ink.SEPIA)
        if (horizontal) { b.c.drawCircle(x, y - 8.4f, 1.4f, screw); b.c.drawCircle(x, y + 8.4f, 1.4f, screw) }
        else { b.c.drawCircle(x - 8.4f, y, 1.4f, screw); b.c.drawCircle(x + 8.4f, y, 1.4f, screw) }
    }

    /** La vanne : un corps de laiton sur le tuyau, son volant rouge à quatre rayons. */
    private fun valve(b: Burin) {
        val y = VALVE_Y
        val body = Path().apply { addRoundRect(PIPE_X - 6.5f, y - 10f, PIPE_X + 6.5f, y + 10f, 3f, 3f, Path.Direction.CW) }
        b.wash(body, BRASS, 255)
        b.hatch(body, 90f, 1.4f, .4f, Ink.SEPIA, Fade(PIPE_X + 6f, 0f, PIPE_X - 2f, 0f, 220, 0))
        b.stroke(body, .8f)
        val wheel = b.pen(Ink.SEPIA, 3.6f)
        b.c.drawCircle(PIPE_X, y, 9f, wheel)
        for (k in 0 until 4) {
            val a = k * PI.toFloat() / 2f + PI.toFloat() / 4f
            b.line(PIPE_X, y, PIPE_X + cos(a) * 8.6f, y + sin(a) * 8.6f, 2.6f)
        }
        val red = b.pen(0xFFB0402A.toInt(), 2.4f)
        b.c.drawCircle(PIPE_X, y, 9f, red)
        for (k in 0 until 4) {
            val a = k * PI.toFloat() / 2f + PI.toFloat() / 4f
            b.c.drawLine(PIPE_X, y, PIPE_X + cos(a) * 8.6f, y + sin(a) * 8.6f, b.pen(0xFFB0402A.toInt(), 1.4f))
        }
        b.c.drawArc(PIPE_X - 9f, y - 9f, PIPE_X + 9f, y + 9f, 200f, 60f, false, b.pen(0xFFF0A890.toInt(), .8f))
        b.c.drawCircle(PIPE_X, y, 2.8f, b.pen(BRASS))
        b.c.drawCircle(PIPE_X, y, 2.8f, b.pen(Ink.SEPIA, .7f))
    }

    // ───────────── le fil, l'établi, la bobine ─────────────

    /** Un fil de cuivre nu : contour, cuivre, et le reflet sur le dessus. */
    private fun wire(b: Burin, path: Path) {
        b.stroke(path, 2.6f, Ink.SEPIA)
        b.stroke(path, 1.6f, COPPER)
        b.c.save(); b.c.translate(-.35f, -.45f); b.stroke(path, .55f, 0xFFFFD8A8.toInt()); b.c.restore()
    }

    /** L'arrivée du courant : le fil court sur le mur, tenu par des isolateurs de porcelaine. */
    private fun supply(b: Burin) {
        wire(b, Path().apply {
            moveTo(30f, SUPPLY_Y); lineTo(HINGE_X - 6f, SUPPLY_Y)
            quadTo(HINGE_X, SUPPLY_Y, HINGE_X, SUPPLY_Y + 6f); lineTo(HINGE_X, 212f)
        })
        for ((x, y) in listOf(60f to SUPPLY_Y, 98f to SUPPLY_Y, HINGE_X to 150f, HINGE_X to 188f)) {
            val knob = ellipse(x, y, 4f, 4f)
            b.wash(knob, 0xFFF4F0E4.toInt(), 255)
            b.hatch(knob, 45f, 1.2f, .35f, Ink.SEPIA, Fade(x + 4f, y + 4f, x - 1f, y - 1f, 220, 0))
            b.stroke(knob, .7f)
            b.c.drawCircle(x - 1.3f, y - 1.3f, .9f, b.pen(0xFFFFFFFF.toInt()))
        }
    }

    private fun bench(b: Burin) {
        val top = rect(40f, BENCH, 320f, BENCH + 8f)
        b.wash(top, 0xFFC09060.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH, 320f, BENCH + 8f), 0f, 1.6f, .4f, Ink.BROWN, Fade(0f, BENCH, 0f, BENCH + 8f, 60, 170))
        val front = rect(40f, BENCH + 8f, 320f, 340f)
        b.wash(front, 0xFF7A5232.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH + 8f, 320f, 340f), 2f, 2.2f, .5f, Ink.SEPIA, Fade(0f, BENCH + 8f, 0f, 320f, 140, 240))
        b.line(40f, BENCH, 320f, BENCH, .9f)
        b.line(40f, BENCH + 8f, 320f, BENCH + 8f, 1f)
        // Le tiroir et sa poignée de laiton.
        val drawer = rect(132f, BENCH + 16f, 232f, BENCH + 52f)
        b.c.save(); b.c.translate(1.5f, 2f); b.fill(drawer, withAlpha(0xFF2A1A0E.toInt(), 90)); b.c.restore()
        b.wash(drawer, 0xFF8A5E3A.toInt(), 255)
        b.hatch(drawer, 2f, 2.2f, .45f, Ink.SEPIA, Fade(0f, BENCH + 16f, 0f, BENCH + 52f, 90, 200))
        b.stroke(drawer, 1f)
        val pull = Path().apply { addRoundRect(170f, BENCH + 27f, 194f, BENCH + 33f, 3f, 3f, Path.Direction.CW) }
        b.wash(pull, BRASS, 255)
        b.washGradient(pull, 0xFFFFF0C0.toInt(), 0f, BENCH + 27f, 220, 0f, BENCH + 33f, 0)
        b.stroke(pull, .8f)
    }

    /** La bobine de fil neuf, couchée : deux joues de bois, les spires de cuivre entre elles. */
    private fun spool(b: Burin) {
        flange(b, 62f)
        val drum = rect(62f, 180f, 108f, 232f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 180f, 0f, 232f,
            intArrayOf(0xFF8A4420.toInt(), 0xFFFFD2A0.toInt(), 0xFFD8844A.toInt(), 0xFFA65A2C.toInt(), 0xFF5A2810.toInt()),
            floatArrayOf(0f, .16f, .35f, .7f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(drum, b.p)
        val turn = b.pen(withAlpha(0xFF5A2810.toInt(), 150), .5f)
        var x = 62.5f
        while (x < 108f) { b.c.drawLine(x, 180.5f, x + 1.2f, 231.5f, turn); x += 1.4f }
        b.hatch(drum, 0f, 1.6f, .45f, Ink.SEPIA, Fade(0f, 232f, 0f, 208f, 200, 0))
        b.line(62f, 180f, 108f, 180f, .8f); b.line(62f, 232f, 108f, 232f, .8f)
        flange(b, 108f)
        b.body(ellipse(109f, 206f, 2.4f, 6.5f), 0xFF2A1C12.toInt(), 0f, outline = .7f, washAlpha = 255)
        // Le bout de fil libre, qui retombe sur l'établi.
        wire(b, Path().apply {
            moveTo(70f, 181f); cubicTo(66f, 164f, 46f, 166f, 50f, 186f)
            cubicTo(53f, 204f, 50f, 224f, 54f, 238f); cubicTo(58f, 248f, 76f, 250f, 82f, 241f)
        })
    }

    private fun flange(b: Burin, x: Float) {
        val disc = ellipse(x, 206f, 7f, 30f)
        b.wash(disc, 0xFFB98C52.toInt(), 255)
        b.hatch(disc, 90f, 1.5f, .45f, Ink.BROWN, Fade(x + 7f, 0f, x - 5f, 0f, 230, 40))
        b.stroke(disc, 1f)
    }

    // ───────────── l'interrupteur à couteau ─────────────

    /** Le socle d'ardoise, les plaques arrière de la charnière et de la mâchoire. */
    private fun switchBase(b: Burin) {
        val base = rect(112f, 227f, 182f, TOP)
        b.body(base, 0xFF4E565C.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.line(113f, 228f, 181f, 228f, .7f, 0xFFA8B0B4.toInt())
        for (x in floatArrayOf(116f, 178f)) b.c.drawCircle(x, 231.5f, 1.3f, b.pen(0xFFC8CCD0.toInt()))
        plate(b, HINGE_X)
        plate(b, JAW_X)
        b.c.drawCircle(174f, 231.5f, 2f, b.pen(BRASS)); b.c.drawCircle(174f, 231.5f, 2f, b.pen(Ink.SEPIA, .6f))
    }

    /** Une plaque de laiton debout : la charnière ou la mâchoire. */
    private fun plate(b: Burin, x: Float) {
        val shape = Path().apply { addRoundRect(x - 4.5f, 210f, x + 4.5f, 228f, 1.5f, 1.5f, Path.Direction.CW) }
        b.wash(shape, BRASS, 255)
        b.washGradient(shape, 0xFFFFF0C0.toInt(), x - 4f, 210f, 200, x + 4f, 228f, 0)
        b.hatch(shape, 90f, 1.4f, .4f, Ink.SEPIA, Fade(x + 4.5f, 0f, x - 1f, 0f, 220, 0))
        b.stroke(shape, .8f)
    }

    // ───────────── le moteur ─────────────

    private fun motor(b: Burin) {
        // Le bornier, accroché au flanc gauche, et son presse-étoupe.
        b.body(rect(154f, 150f, 178f, 178f), 0xFF8A9096.toInt(), .3f, 90f, outline = .9f, washAlpha = 255)
        b.line(154f, 155f, 178f, 155f, .6f)
        b.c.drawCircle(158.5f, 152.5f, 1.2f, b.pen(Ink.SEPIA))
        b.c.drawCircle(158.5f, 174.5f, 1.2f, b.pen(Ink.SEPIA))
        b.body(rect(161f, 177f, 169f, 183f), BRASS, .3f, 0f, outline = .7f, washAlpha = 255)
        // Le pied de fonte, boulonné à l'établi.
        val foot = poly(178f, TOP, 266f, TOP, 254f, 204f, 190f, 204f)
        b.body(foot, IRON, .45f, 60f, outline = 1f, washAlpha = 255)
        for (x in floatArrayOf(186f, 258f)) {
            b.c.drawCircle(x, 231f, 2.4f, b.pen(0xFFA8AEB4.toInt())); b.c.drawCircle(x, 231f, 2.4f, b.pen(Ink.SEPIA, .6f))
        }
        // La carcasse et ses ailettes de refroidissement.
        for (k in 0 until 30) {
            b.c.save(); b.c.rotate(k * 12f, CX, CY)
            val fin = rect(CX - 1.7f, CY - RADIUS - 3.6f, CX + 1.7f, CY - RADIUS + 1f)
            b.fill(fin, IRON); b.stroke(fin, .6f)
            b.c.restore()
        }
        val shell = ellipse(CX, CY, RADIUS, RADIUS)
        b.wash(shell, IRON, 255)
        b.washGradient(shell, 0xFFE8ECEE.toInt(), CX - RADIUS, CY - RADIUS, 170, CX + RADIUS * .3f, CY + RADIUS * .3f, 0)
        b.hatch(shell, 30f, 1.8f, .45f, Ink.SEPIA, Fade(CX + RADIUS, CY + RADIUS, CX, CY, 230, 0))
        b.stroke(shell, 1.1f)
        for (k in 0 until 4) {
            val a = PI.toFloat() / 4f + k * PI.toFloat() / 2f
            val x = CX + cos(a) * 46.5f; val y = CY + sin(a) * 46.5f
            b.c.drawCircle(x, y, 2f, b.pen(0xFFB8BEC4.toInt())); b.c.drawCircle(x, y, 2f, b.pen(Ink.SEPIA, .6f))
        }
        // L'intérieur : le stator de tôles, ses six pôles, et une bobine de cuivre sur chacun.
        b.fill(ellipse(CX, CY, 43f, 43f), 0xFF1C1612.toInt())
        val stator = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(CX, CY, 43f, Path.Direction.CW); addCircle(CX, CY, 35f, Path.Direction.CW)
        }
        b.wash(stator, STEEL, 255)
        b.hatch(stator, 0f, 1.3f, .35f, Ink.SEPIA, Fade(CX, CY + 43f, CX, CY - 43f, 210, 40))
        b.stroke(stator, .7f)
        for (k in 0 until 6) {
            b.c.save(); b.c.translate(CX, CY); b.c.rotate(k * 60f)
            val tooth = rect(-3.6f, -36f, 3.6f, -26f)
            b.body(tooth, STEEL, 0f, outline = .6f, washAlpha = 255)
            val shoe = Path().apply { addRoundRect(-9.5f, -27.5f, 9.5f, -24.4f, 1.5f, 1.5f, Path.Direction.CW) }
            b.body(shoe, STEEL, .3f, 0f, outline = .7f, washAlpha = 255)
            coil(b)
            b.c.restore()
        }
    }

    /** Une bobine sur son pôle (repère du pôle, pointe vers le haut) : le fil enroulé, spire après spire. */
    private fun coil(b: Burin) {
        val shape = Path().apply { addRoundRect(-8.6f, -35f, 8.6f, -28.2f, 2.4f, 2.4f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(-8.6f, 0f, 8.6f, 0f,
            intArrayOf(0xFF6A3014.toInt(), 0xFFE89A5A.toInt(), 0xFFFFD8A8.toInt(), 0xFFC06A34.toInt(), 0xFF5A2810.toInt()),
            floatArrayOf(0f, .3f, .45f, .7f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(shape, b.p)
        val turn = b.pen(withAlpha(0xFF4A200C.toInt(), 170), .45f)
        var y = -34.4f
        while (y < -28.5f) { b.c.drawLine(-8.2f, y, 8.2f, y, turn); y += 1.15f }
        b.stroke(shape, .7f)
    }

    // ───────────── les pièces mobiles ─────────────

    override fun sprites(): List<Sprite> = listOf(
        Sprite(ROTOR, RectF(-24f, -24f, 24f, 24f)) { b -> rotor(b) },
        Sprite(BRUSHES, RectF(-25f, -6f, 25f, 6f)) { b ->
            for (side in floatArrayOf(-1f, 1f)) {
                val holder = rect(if (side < 0) -23.5f else 17f, -3.8f, if (side < 0) -17f else 23.5f, 3.8f)
                b.body(holder, BRASS, .3f, 0f, outline = .7f, washAlpha = 255)
                val carbon = rect(if (side < 0) -17.4f else 10.8f, -2.6f, if (side < 0) -10.8f else 17.4f, 2.6f)
                b.body(carbon, 0xFF34383A.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
            }
        },
        Sprite(BLADE, RectF(-5f, -22f, 48f, 5f)) { b ->
            val bar = rect(0f, -1.9f, 44f, 1.9f)
            b.wash(bar, COPPER, 255)
            b.washGradient(bar, 0xFFFFE0B8.toInt(), 0f, -1.9f, 230, 0f, 1.9f, 0)
            b.stroke(bar, .7f)
            val grip = rect(28.4f, -15f, 31.6f, -1.9f)
            b.body(grip, 0xFF2A2020.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
            b.c.drawCircle(30f, -17.5f, 3.8f, b.pen(0xFF2A2020.toInt()))
            b.c.drawCircle(30f, -17.5f, 3.8f, b.pen(Ink.SEPIA, .7f))
            b.c.drawCircle(28.8f, -18.8f, 1f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200)))
        },
        Sprite(FRONT, RectF(112f, 206f, 182f, 232f)) { b ->
            plate(b, HINGE_X)
            b.c.drawCircle(HINGE_X, PIVOT_Y, 2f, b.pen(0xFFD8DCDE.toInt())); b.c.drawCircle(HINGE_X, PIVOT_Y, 2f, b.pen(Ink.SEPIA, .6f))
            plate(b, JAW_X)
        },
        Sprite(GLOW, RectF(-24f, -24f, 24f, 24f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 24f,
                intArrayOf(0xEEFFFFFF.toInt(), 0x88A8D8FF.toInt(), 0x00A8D8FF), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 24f, b.p)
        }
    )

    /** Le rotor vu de bout : tôles, dix encoches garnies de cuivre, collecteur, arbre et sa clavette. */
    private fun rotor(b: Burin) {
        val disc = ellipse(0f, 0f, 22f, 22f)
        b.wash(disc, STEEL, 255)
        b.hatch(disc, 0f, 1.4f, .35f, Ink.SEPIA, Fade(0f, 22f, 0f, -22f, 150, 20))
        for (k in 0 until 10) {
            b.c.save(); b.c.rotate(k * 36f)
            val slot = Path().apply { addRoundRect(-2.6f, -21f, 2.6f, -13.5f, 1.2f, 1.2f, Path.Direction.CW) }
            b.fill(slot, COPPER)
            b.line(-1f, -20f, -1f, -14.5f, .6f, 0xFFFFD2A0.toInt())
            b.stroke(slot, .55f)
            b.c.restore()
        }
        b.stroke(disc, .9f)
        val comm = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(0f, 0f, 10.5f, Path.Direction.CW); addCircle(0f, 0f, 6.5f, Path.Direction.CW)
        }
        b.fill(comm, COPPER)
        for (k in 0 until 10) {
            b.c.save(); b.c.rotate(k * 36f + 18f); b.line(0f, -6.5f, 0f, -10.5f, .6f); b.c.restore()
        }
        b.stroke(comm, .6f)
        b.c.drawCircle(0f, 0f, 5.6f, b.pen(0xFFD8DCDE.toInt()))
        b.c.drawCircle(0f, 0f, 5.6f, b.pen(Ink.SEPIA, .7f))
        b.fill(rect(-1.3f, -5.6f, 1.3f, -3.4f), 0xFF2A2420.toInt())
    }

    // ───────────── animation ─────────────

    /** L'angle du rotor tout au long d'un cycle, calculé une fois : il accélère, puis ralentit sur sa lancée. */
    private val spin = FloatArray(STEPS + 1)
    private var spinPerCycle = 0f

    /** Le chemin du courant, échantillonné à pas réguliers : arrivée, lame, fil du moteur. */
    private val wireX = FloatArray(WIRE_N)
    private val wireY = FloatArray(WIRE_N)

    init {
        val dt = CYCLE / STEPS
        var w = 0f
        repeat(3) { pass ->
            var a = 0f
            for (i in 0..STEPS) {
                if (pass == 2) spin[i] = a
                if (i == STEPS) break
                val on = bladeAngle(i * dt) < CONTACT_DEG
                w += if (on) (OMEGA - w) * (1f - exp(-dt / SPIN_UP)) else -w * (1f - exp(-dt / SPIN_DOWN))
                a += w * dt
            }
            spinPerCycle = a % 360f
        }
        val path = Path().apply {
            moveTo(40f, SUPPLY_Y); lineTo(HINGE_X - 6f, SUPPLY_Y); quadTo(HINGE_X, SUPPLY_Y, HINGE_X, SUPPLY_Y + 6f)
            lineTo(HINGE_X, PIVOT_Y); lineTo(JAW_X + 2f, PIVOT_Y); lineTo(174f, 230f)
            cubicTo(186f, 228f, 168f, 204f, 165f, 182f)
        }
        val pm = PathMeasure(path, false)
        val pos = FloatArray(2)
        for (i in 0 until WIRE_N) {
            pm.getPosTan(pm.length * i / (WIRE_N - 1), pos, null)
            wireX[i] = pos[0]; wireY[i] = pos[1]
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-8f, -8f, 36f, intArrayOf(0x00000000, 0x00000000, 0x77000000), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }
    private val arc = FloatArray(28)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val n = floor(t / CYCLE)
        val u = t - n * CYCLE
        val deg = bladeAngle(u)
        val on = deg < CONTACT_DEG
        val frame = (t * 30f).toInt()

        // Un reflet qui glisse le long du tuyau, accroché au mur.
        val g = (t / GLINT_PERIOD) % 1f
        val gx = 50f + g * (PIPE_X - 62f)
        fill.color = withAlpha(0xFFFFF4E0.toInt(), (230 * sin(g * PI.toFloat())).toInt())
        c.drawOval(gx - 8f, PIPE_Y - 2.6f, gx + 8f, PIPE_Y - .6f, fill)

        // Le reste est posé sur l'établi.
        c.save(); c.translate(0f, DY)

        // Le rotor, son ombre fixe par-dessus, puis les balais.
        val dt = CYCLE / STEPS
        val i = (u / dt).toInt().coerceAtMost(STEPS - 1)
        val f = u / dt - i
        val angle = (n * spinPerCycle + spin[i] + (spin[i + 1] - spin[i]) * f) % 360f
        val speed = (spin[i + 1] - spin[i]) / dt / OMEGA
        c.save(); c.translate(CX, CY)
        c.rotate(angle); s.draw(c, ROTOR); c.rotate(-angle)
        c.drawCircle(0f, 0f, 22f, shade)
        s.draw(c, BRUSHES)
        // Les balais crépitent quand le moteur tourne vite.
        if (on && speed > .3f) {
            fill.color = 0xFFDDF0FF.toInt()
            for (k in 0 until 4) {
                val side = if (k % 2 == 0) -1f else 1f
                val h = hash(frame, k, 1)
                if (h < .35f) continue
                val x = side * (10.6f + 2f * hash(frame, k, 2)); val y = -2.6f + 5.2f * hash(frame, k, 3)
                c.drawCircle(x, y, .5f + .7f * h, fill)
            }
        }
        c.restore()

        // La lame du couteau, entre ses plaques.
        c.save(); c.translate(HINGE_X, PIVOT_Y); c.rotate(-deg)
        s.draw(c, BLADE)
        c.restore()
        s.draw(c, FRONT)

        // Le courant qui court dans le fil.
        if (on) {
            for (k in 0 until PULSES) {
                val p = ((t * PULSE_SPEED + k.toFloat() / PULSES) % 1f) * (WIRE_N - 1)
                val j = p.toInt().coerceAtMost(WIRE_N - 2)
                val g = p - j
                val x = wireX[j] + (wireX[j + 1] - wireX[j]) * g
                val y = wireY[j] + (wireY[j + 1] - wireY[j]) * g
                c.save(); c.translate(x, y); c.scale(.3f, .3f)
                s.draw(c, GLOW, alpha = 200)
                c.restore()
                fill.color = 0xFFFFF6D8.toInt()
                c.drawCircle(x, y, 1.3f, fill)
            }
        }

        // L'arc électrique quand la lame quitte la mâchoire, l'étincelle quand elle y rentre.
        val opening = u - OPEN_AT
        val closing = u - (CLOSE_AT + CLOSE_DUR)
        if (opening in 0f..ARC_TIME) {
            val a = Math.toRadians(deg.toDouble()).toFloat()
            val tipX = HINGE_X + 42f * cos(a); val tipY = PIVOT_Y - 42f * sin(a)
            val life = 1f - opening / ARC_TIME
            c.save(); c.translate((tipX + JAW_X) / 2f, (tipY + 212f) / 2f); c.scale(.9f, .9f)
            s.draw(c, GLOW, alpha = (255 * life).toInt())
            c.restore()
            var px = JAW_X; var py = 212f
            for (k in 0 until 7) {
                val q = (k + 1) / 7f
                val nx = if (k == 6) tipX else JAW_X + (tipX - JAW_X) * q + 3f * (hash(frame, k, 4) - .5f)
                val ny = if (k == 6) tipY else 212f + (tipY - 212f) * q + 3f * (hash(frame, k, 5) - .5f)
                arc[k * 4] = px; arc[k * 4 + 1] = py; arc[k * 4 + 2] = nx; arc[k * 4 + 3] = ny
                px = nx; py = ny
            }
            ink.color = withAlpha(0xFFBFE4FF.toInt(), (255 * life).toInt()); ink.strokeWidth = 1.6f
            c.drawLines(arc, 0, 28, ink)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * life).toInt()); ink.strokeWidth = .6f
            c.drawLines(arc, 0, 28, ink)
        } else if (closing in -.04f..SPARK_TIME) {
            val life = 1f - (closing + .04f) / (SPARK_TIME + .04f)
            c.save(); c.translate(JAW_X, 213f); c.scale(.5f, .5f)
            s.draw(c, GLOW, alpha = (255 * life).toInt())
            c.restore()
        }

        // Des éclats qui scintillent sur les spires de la bobine.
        val slot = (t / TWINKLE).toInt()
        val life = (t / TWINKLE) - slot
        val tw = sin(life * PI.toFloat())
        for (k in 0 until 2) {
            val x = 66f + 38f * hash(slot, k, 6); val y = 184f + 6f * hash(slot, k, 7)
            val r = 3.4f * tw
            ink.color = withAlpha(0xFFFFF6E0.toInt(), (240 * tw).toInt()); ink.strokeWidth = .8f
            c.drawLine(x - r, y, x + r, y, ink); c.drawLine(x, y - r, x, y + r, ink)
        }
        c.restore()
    }

    /** L'ouverture du couteau, en degrés, à l'instant [u] du cycle. */
    private fun bladeAngle(u: Float): Float {
        val f = when {
            u < OPEN_AT -> 0f
            u < OPEN_AT + OPEN_DUR -> smooth((u - OPEN_AT) / OPEN_DUR)
            u < CLOSE_AT -> 1f
            u < CLOSE_AT + CLOSE_DUR -> 1f - smooth((u - CLOSE_AT) / CLOSE_DUR)
            else -> 0f
        }
        return f * OPEN_DEG
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** L'établi, tel que la scène le montre ; [TOP] est sa hauteur dans le dessin des objets, avant [DY]. */
        const val BENCH = 258f
        const val TOP = 236f
        const val DY = BENCH - TOP
        const val PIPE_Y = 84f
        const val PIPE_X = 294f
        const val VALVE_Y = 152f
        const val SUPPLY_Y = 110f
        const val HINGE_X = 126f
        const val JAW_X = 166f
        const val PIVOT_Y = 218f
        const val CX = 222f
        const val CY = 164f
        const val RADIUS = 50f

        const val COPPER = 0xFFC8743A.toInt()
        const val BRASS = 0xFFC9A04A.toInt()
        const val IRON = 0xFF6A7076.toInt()
        const val STEEL = 0xFF9AA0A6.toInt()

        const val ROTOR = 1
        const val BRUSHES = 2
        const val BLADE = 3
        const val FRONT = 4
        const val GLOW = 5

        /** Le cycle : fermé, on ouvre, ouvert, on referme (secondes). */
        const val CYCLE = 8f
        const val OPEN_AT = 4.6f
        const val OPEN_DUR = .4f
        const val CLOSE_AT = 6.6f
        const val CLOSE_DUR = .4f
        /** Ouverture du couteau : au-delà de 45°, la poignée irait toucher le fil d'arrivée. */
        const val OPEN_DEG = 45f
        /** En dessous de cet angle, la lame touche encore la mâchoire. */
        const val CONTACT_DEG = 3f
        const val ARC_TIME = .16f
        const val SPARK_TIME = .1f

        /** Vitesse du rotor en régime (degrés par seconde), constantes de temps du démarrage et de l'arrêt. */
        const val OMEGA = 420f
        const val SPIN_UP = .5f
        const val SPIN_DOWN = 1.1f
        const val STEPS = 800

        const val WIRE_N = 160
        const val PULSES = 7
        /** Tours de circuit par seconde. */
        const val PULSE_SPEED = .22f
        const val GLINT_PERIOD = 6f
        const val TWINKLE = 1.3f
    }
}
