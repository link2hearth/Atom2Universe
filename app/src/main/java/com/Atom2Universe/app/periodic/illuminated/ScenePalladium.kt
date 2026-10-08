package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palladium — l'éponge à hydrogène. Le palladium boit l'hydrogène : il peut en absorber près de
 * neuf cents fois son volume. Dans une cuve d'eau traversée par le courant, le fil de platine
 * dégage ses bulles d'oxygène ; en face, la lame de palladium devrait faire des bulles
 * d'hydrogène, mais elle l'avale : elle gonfle et se courbe. Pleine, elle bulle à son tour. Le
 * courant coupé, l'hydrogène ressort en pétillant et la lame se redresse.
 */
internal class ScenePalladium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_hydrogen_sponge
    override val noteRes = R.string.card_note_hydrogen_sponge
    override val explainRes = R.string.card_explain_hydrogen_sponge

    override fun engrave(b: Burin) {
        // Le mur carrelé du laboratoire.
        val wall = rect(40f, 50f, 320f, BENCH)
        b.wash(wall, 0xFFE8E6DE.toInt(), 255)
        val joint = withAlpha(0xFF9A988E.toInt(), 140)
        var y = 50f
        while (y < BENCH) { b.line(40f, y, 320f, y, .6f, joint); y += TILE }
        var x = 40f
        while (x < 320f) { b.line(x, 50f, x, BENCH, .6f, joint); x += TILE }
        b.washGradient(wall, 0xFF2A2A24.toInt(), 0f, 50f, 90, 0f, BENCH, 10)
        // L'étagère et ses flacons.
        b.body(rect(246f, 112f, 314f, 117f), 0xFF8A5A34.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.fill(poly(252f, 117f, 262f, 117f, 252f, 128f), 0xFF6A4228.toInt())
        b.fill(poly(298f, 117f, 308f, 117f, 308f, 128f), 0xFF6A4228.toInt())
        val flask = poly(259f, 92f, 265f, 92f, 265f, 99f, 275f, 112f, 249f, 112f, 259f, 99f)
        b.wash(flask, 0xFFE4F0F0.toInt(), 200)
        b.fill(poly(254f, 105f, 270f, 105f, 275f, 112f, 249f, 112f), withAlpha(0xFF5AA86A.toInt(), 200))
        b.stroke(flask, .8f)
        val bottle = Path().apply { addRoundRect(284f, 94f, 300f, 112f, 3f, 3f, Path.Direction.CW) }
        b.body(bottle, 0xFF6A3A1A.toInt(), .3f, 0f, outline = .8f, washAlpha = 230)
        b.body(rect(288f, 89f, 296f, 94f), 0xFF2A2A2A.toInt(), 0f, outline = .6f, washAlpha = 255)
        b.fill(rect(287f, 100f, 297f, 106f), 0xFFF0E8D0.toInt())
        // La paillasse : le dessus vu en biais, puis le chant.
        b.wash(rect(40f, BENCH, 320f, BENCH + 20f), 0xFF9A6A40.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH, 320f, BENCH + 20f), 0f, 2.6f, .4f, 0xFF5A3A20.toInt())
        b.wash(rect(40f, BENCH + 20f, 320f, 340f), 0xFF6A4228.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH + 20f, 320f, 340f), 90f, 3f, .4f, 0xFF3A2414.toInt(), Fade(0f, BENCH + 20f, 0f, 330f, 60, 200))
        b.line(40f, BENCH + 20f, 320f, BENCH + 20f, 1.2f, Ink.SEPIA)
        // L'alimentation : le boîtier, le cadran de l'ampèremètre, l'interrupteur, les bornes.
        b.fill(ellipse(82f, TANK_B + 1f, 34f, 4f), withAlpha(0xFF2A1A0E.toInt(), 110))
        val box = Path().apply { addRoundRect(52f, 196f, 112f, TANK_B, 4f, 4f, Path.Direction.CW) }
        b.body(box, 0xFF3A4A5A.toInt(), .35f, 0f, Fade(112f, 0f, 52f, 0f, 200, 20), outline = 1f, washAlpha = 255)
        b.fill(rect(60f, 206f, 104f, 236f), 0xFFF4F0E0.toInt())
        b.stroke(rect(60f, 206f, 104f, 236f), .8f)
        b.c.drawArc(64f, 214f, 100f, 250f, 215f, 110f, false, b.pen(Ink.SEPIA, .7f))
        for (k in 0..6) {
            val a = Math.toRadians((-55.0 + k * 15.0)).toFloat()
            b.line(MX + 18f * sin(a), MY - 18f * cos(a), MX + 15f * sin(a), MY - 15f * cos(a), .6f)
        }
        b.body(ellipse(TOGGLE_X, TOGGLE_Y, 4.4f, 4.4f), 0xFF8A9096.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        b.body(ellipse(LED_X, TOGGLE_Y, 3.4f, 3.4f), 0xFF2A2A2A.toInt(), 0f, outline = .7f, washAlpha = 255)
        b.body(rect(62f, 188f, 70f, 196f), 0xFFC83A2A.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        b.body(rect(94f, 188f, 102f, 196f), 0xFF2A2A2E.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        // La cuve de verre : l'ombre, le fond vu à travers, le fil de platine.
        b.fill(ellipse((TANK_L + TANK_R) / 2f, TANK_B + 1f, 88f, 5f), withAlpha(0xFF2A1A0E.toInt(), 100))
        val tank = rect(TANK_L, TANK_T, TANK_R, TANK_B)
        b.wash(tank, 0xFFDDEEF2.toInt(), 80)
        b.line(ANODE_X, TANK_T, ANODE_X, ANODE_B - 16f, 2.2f, 0xFF8A9096.toInt())
        for (k in 0 until 6) {
            val yy = ANODE_B - 16f + k * 3f
            b.line(ANODE_X - 5f, yy, ANODE_X + 5f, yy + 1.5f, 1.4f, 0xFF7A8086.toInt())
        }
        // L'eau, un peu bleue, plus profonde vers le fond.
        val water = rect(TANK_L + 2f, WATER, TANK_R - 2f, TANK_B - 3f)
        b.washGradient(water, 0xFF5A9AC0.toInt(), 0f, WATER, 80, 0f, TANK_B, 150)
        b.line(TANK_L + 2f, WATER, TANK_R - 2f, WATER, 1f, withAlpha(0xFFFFFFFF.toInt(), 210))
        b.line(TANK_L + 2f, WATER + 1.6f, TANK_R - 2f, WATER + 1.6f, .6f, withAlpha(0xFF3A6A8A.toInt(), 150))
        // Les parois : le fond épais, les arêtes, les reflets.
        b.fill(rect(TANK_L, TANK_B - 4f, TANK_R, TANK_B), withAlpha(0xFF8ABAC8.toInt(), 170))
        b.stroke(tank, 1.4f, 0xFF4A6A76.toInt())
        b.line(TANK_L + 5f, TANK_T + 6f, TANK_L + 5f, TANK_B - 8f, 1.2f, withAlpha(0xFFFFFFFF.toInt(), 170))
        b.line(TANK_R - 9f, TANK_T + 10f, TANK_R - 9f, TANK_T + 64f, 2.2f, withAlpha(0xFFFFFFFF.toInt(), 120))
        // La barre posée sur la cuve, et ses deux bornes de laiton.
        b.body(rect(TANK_L - 10f, TANK_T - 8f, TANK_R + 10f, TANK_T), 0xFF8A5A34.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        for (bx in floatArrayOf(ANODE_X, PX)) b.body(rect(bx - 6f, TANK_T - 15f, bx + 6f, TANK_T - 8f), 0xFFC8A04A.toInt(), .25f, 0f, outline = .7f, washAlpha = 255)
        // Les fils : le rouge au platine, le noir au palladium.
        wire(b, Path().apply { moveTo(66f, 190f); cubicTo(62f, 140f, 128f, 104f, ANODE_X, TANK_T - 14f) }, 0xFFC83A2A.toInt())
        wire(b, Path().apply { moveTo(98f, 190f); cubicTo(106f, 118f, 214f, 92f, PX, TANK_T - 14f) }, 0xFF2A2A2E.toInt())
        for (bx in floatArrayOf(ANODE_X, PX)) b.body(ellipse(bx, TANK_T - 15f, 3f, 2f), 0xFFE8C870.toInt(), 0f, outline = .6f, washAlpha = 255)
    }

    private fun wire(b: Burin, path: Path, color: Int) {
        b.stroke(path, 3.6f, Ink.SEPIA)
        b.stroke(path, 2.2f, color)
        b.stroke(path, .6f, withAlpha(0xFFFFFFFF.toInt(), 110))
    }

    // ───────────── animation ─────────────

    private val plate = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        val on = ph < ON
        val bend = smooth(load(ph))
        // L'oxygène : les bulles du fil de platine, tant que le courant passe.
        for (k in 0 until O2) {
            val period = 2.4f + hash(k, 1)
            val off = hash(k, 2) * period
            val n = ((t + off) / period).toInt()
            val birth = n * period - off
            if (phase(birth) >= ON || hash(n, k + 11) < .25f) continue
            val y0 = ANODE_B - 2f - 54f * hash(n, k)
            bubble(c, ANODE_X + (hash(n, k + 3) - .5f) * 6f, y0, t - birth, 1.2f + 1.3f * hash(n, k + 5), 38f + 18f * hash(n, k + 7), k)
        }
        // L'hydrogène qui file vers la lame et disparaît dedans, tant qu'elle n'est pas pleine.
        for (k in 0 until IONS) {
            val off = hash(k, 31) * ION_PERIOD
            val n = ((t + off) / ION_PERIOD).toInt()
            val birth = n * ION_PERIOD - off
            val age = (t - birth) / ION_LIFE
            if (phase(birth) >= FULL || age >= 1f) continue
            val y = WATER + 12f + (PLATE_B - WATER - 18f) * hash(n, k + 32)
            val x0 = ANODE_X + 16f + (PX - ANODE_X - 44f) * hash(n, k + 33)
            val target = centre((y - PLATE_T) / (PLATE_B - PLATE_T), bend) - width(bend) / 2f
            fill.color = withAlpha(0xFFF0F8FF.toInt(), (230 * sin(age * PI.toFloat())).toInt())
            c.drawCircle(x0 + (target - x0) * age * age, y, 1.3f, fill)
        }
        // La lame de palladium : elle gonfle un peu et se courbe en se remplissant.
        val w = width(bend)
        plate.reset()
        for (i in 0..SEG) {
            val u = i / SEG.toFloat()
            val x = centre(u, bend) - w / 2f
            val y = PLATE_T + u * (PLATE_B - PLATE_T)
            if (i == 0) plate.moveTo(x, y) else plate.lineTo(x, y)
        }
        for (i in SEG downTo 0) {
            val u = i / SEG.toFloat()
            plate.lineTo(centre(u, bend) + w / 2f, PLATE_T + u * (PLATE_B - PLATE_T))
        }
        plate.close()
        c.save(); c.clipRect(40f, 50f, 320f, WATER)
        fill.color = PD_DRY; c.drawPath(plate, fill)
        c.restore()
        c.save(); c.clipRect(40f, WATER, 320f, 340f)
        fill.color = PD_WET; c.drawPath(plate, fill)
        c.restore()
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 170); ink.strokeWidth = 1.2f
        for (i in 0 until SEG) {
            val u0 = i / SEG.toFloat(); val u1 = (i + 1) / SEG.toFloat()
            c.drawLine(centre(u0, bend) - w / 2f + 2.2f, PLATE_T + u0 * (PLATE_B - PLATE_T),
                centre(u1, bend) - w / 2f + 2.2f, PLATE_T + u1 * (PLATE_B - PLATE_T), ink)
        }
        ink.color = Ink.SEPIA; ink.strokeWidth = .9f; c.drawPath(plate, ink)
        // Pleine, la lame bulle à gros bouillons ; le courant coupé, l'hydrogène ressort en pétillant.
        for (k in 0 until H2) {
            val period = 3f + hash(k, 21)
            val off = hash(k, 22) * period
            val n = ((t + off) / period).toInt()
            val birth = n * period - off
            val bp = phase(birth)
            val fizz = bp >= ON
            val emit = if (fizz) hash(n, k + 23) < load(bp) else bp >= FULL && hash(n, k + 23) < .8f
            if (!emit) continue
            val y0 = WATER + 8f + (PLATE_B - WATER - 8f) * hash(n, k + 24)
            val then = smooth(load(bp))
            val side = if (hash(n, k + 25) < .5f) -1f else 1f
            val x0 = centre((y0 - PLATE_T) / (PLATE_B - PLATE_T), then) + side * (width(then) / 2f + 1.5f)
            val r = if (fizz) .7f + .8f * hash(n, k + 26) else 1.4f + 1.6f * hash(n, k + 26)
            bubble(c, x0, y0, t - birth, r, 30f + 20f * hash(n, k + 27), k)
        }
        // L'alimentation : l'aiguille de l'ampèremètre, l'interrupteur, le témoin.
        val amp = if (on) (ph / .4f).coerceAtMost(1f) else (1f - (ph - ON) / .4f).coerceAtLeast(0f)
        val a = Math.toRadians(-55.0 + 90.0 * amp).toFloat() + (if (on) .02f * sin(t * 23f) else 0f)
        ink.color = 0xFFA4281B.toInt(); ink.strokeWidth = .9f
        c.drawLine(MX, MY, MX + 17f * sin(a), MY - 17f * cos(a), ink)
        fill.color = Ink.SEPIA; c.drawCircle(MX, MY, 1.6f, fill)
        val lever = if (on) -.5f else .5f
        ink.color = 0xFFD8DCE0.toInt(); ink.strokeWidth = 2f
        c.drawLine(TOGGLE_X, TOGGLE_Y, TOGGLE_X + 8f * sin(lever), TOGGLE_Y - 8f * cos(lever), ink)
        fill.color = 0xFFE8ECF0.toInt(); c.drawCircle(TOGGLE_X + 8f * sin(lever), TOGGLE_Y - 8f * cos(lever), 1.8f, fill)
        if (on) {
            fill.color = withAlpha(0xFF5AE87A.toInt(), 70); c.drawCircle(LED_X, TOGGLE_Y, 6f, fill)
            fill.color = 0xFF7AF89A.toInt(); c.drawCircle(LED_X, TOGGLE_Y, 2.6f, fill)
        }
    }

    /** Une bulle partie de ([x], [y0]) il y a [age] secondes ; arrivée en surface, elle laisse un rond. */
    private fun bubble(c: Canvas, x: Float, y0: Float, age: Float, r: Float, speed: Float, k: Int) {
        val rr = r * (1f + age * .25f)
        val y = y0 - age * speed
        if (y > WATER + rr) {
            val wx = x + 1.4f * sin(age * 8f + k)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), 60); c.drawCircle(wx, y, rr, fill)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), 210); ink.strokeWidth = .6f; c.drawCircle(wx, y, rr, ink)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(wx - rr * .35f, y - rr * .35f, rr * .3f, fill)
        } else {
            val u = (WATER + rr - y) / speed / .3f
            if (u < 1f) {
                ink.color = withAlpha(0xFFFFFFFF.toInt(), (180 * (1f - u)).toInt()); ink.strokeWidth = .7f
                c.drawOval(x - 2f - 8f * u, WATER - 1f - u, x + 2f + 8f * u, WATER + 1f + u, ink)
            }
        }
    }

    /** Le remplissage de la lame au moment [ph] du cycle : 0 vide, 1 pleine. Il revient à 0 en fin de cycle. */
    private fun load(ph: Float) = when {
        ph < FULL -> ph / FULL
        ph < ON -> 1f
        else -> 1f - (ph - ON) / (CYCLE - ON)
    }

    /** L'axe de la lame à la hauteur [u] (0 en haut, 1 en bas) : elle se courbe vers le platine. */
    private fun centre(u: Float, bend: Float) = PX - BEND * bend * u * u

    private fun width(bend: Float) = 8f + 2.5f * bend

    private fun phase(x: Float) = ((x % CYCLE) + CYCLE) % CYCLE

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val TILE = 26f
        const val BENCH = 248f
        /** La cuve, la surface de l'eau. */
        const val TANK_L = 124f
        const val TANK_R = 284f
        const val TANK_T = 150f
        const val TANK_B = 262f
        const val WATER = 176f
        /** Le fil de platine ; la lame de palladium, son axe au repos et sa plus grande courbure. */
        const val ANODE_X = 160f
        const val ANODE_B = 250f
        const val PX = 236f
        const val PLATE_T = 150f
        const val PLATE_B = 248f
        const val BEND = 16f
        const val SEG = 12
        const val PD_DRY = 0xFFD8DCE0.toInt()
        const val PD_WET = 0xFFA8BCC8.toInt()
        /** Le cycle : le courant passe jusqu'à [ON] ; la lame est pleine à [FULL]. */
        const val CYCLE = 12f
        const val FULL = 6f
        const val ON = 8f
        const val O2 = 30
        const val H2 = 36
        const val IONS = 16
        const val ION_PERIOD = 1.6f
        const val ION_LIFE = 1.2f
        /** L'ampèremètre (pivot de l'aiguille), l'interrupteur et le témoin. */
        const val MX = 82f
        const val MY = 232f
        const val TOGGLE_X = 68f
        const val TOGGLE_Y = 250f
        const val LED_X = 96f
    }
}
