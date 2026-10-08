package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Yttrium — la découpe laser. Le grenat d'yttrium et d'aluminium (le cristal « YAG ») est le
 * cœur des lasers qui découpent l'acier. Sur sa table, la tête de découpe court sur son portique
 * et trace une étoile dans la tôle : le trait rougeoie derrière elle puis noircit, et une gerbe
 * d'étincelles jaillit sous le faisceau.
 */
internal class SceneYttrium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_laser_cutter
    override val noteRes = R.string.card_note_laser_cutter
    override val explainRes = R.string.card_explain_laser_cutter

    /** Le tracé de l'étoile, échantillonné à pas réguliers : en coordonnées de la tôle puis de l'écran. */
    private val pu = FloatArray(SAMPLES)
    private val pv = FloatArray(SAMPLES)
    private val px = FloatArray(SAMPLES)
    private val py = FloatArray(SAMPLES)
    private val cutPath = Path()

    init {
        // Les dix sommets de l'étoile, puis un échantillonnage régulier le long des côtés.
        val vu = FloatArray(11); val vv = FloatArray(11)
        for (k in 0..10) {
            val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
            val r = if (k % 2 == 0) .34f else .15f
            vu[k] = .5f + r * cos(a); vv[k] = .5f + r * sin(a) * 1.1f
        }
        var total = 0f
        val seg = FloatArray(10)
        for (k in 0 until 10) { seg[k] = hypot(vu[k + 1] - vu[k], vv[k + 1] - vv[k]); total += seg[k] }
        var k = 0; var acc = 0f
        for (i in 0 until SAMPLES) {
            val d = total * i / (SAMPLES - 1)
            while (k < 9 && d > acc + seg[k]) { acc += seg[k]; k++ }
            val f = ((d - acc) / seg[k]).coerceIn(0f, 1f)
            pu[i] = vu[k] + (vu[k + 1] - vu[k]) * f
            pv[i] = vv[k] + (vv[k + 1] - vv[k]) * f
            px[i] = sx(pu[i], pv[i]); py[i] = sy(pv[i])
        }
        cutPath.moveTo(px[0], py[0])
        for (i in 1 until SAMPLES) cutPath.lineTo(px[i], py[i])
    }

    /** De la tôle (u de gauche à droite, v du fond vers nous) à l'écran. */
    private fun sx(u: Float, v: Float): Float {
        val l = BL + (FL - BL) * v; val r = BR + (FR - BR) * v
        return l + (r - l) * u
    }
    private fun sy(v: Float): Float = BACK + (FRONT - BACK) * v

    override fun engrave(b: Burin) {
        // L'atelier sombre.
        val wall = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            intArrayOf(0xFF2A2C30.toInt(), 0xFF1A1A1E.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(wall, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 90f, 3f, .35f, 0xFF0A0A0C.toInt())
        // Le bâti de la machine, le lit à lamelles sous la tôle.
        val frame = poly(BL - 14f, BACK - 6f, BR + 14f, BACK - 6f, FR + 18f, FRONT + 26f, FL - 18f, FRONT + 26f)
        b.body(frame, 0xFF4A5A6A.toInt(), .35f, 0f, outline = 1f, washAlpha = 255)
        b.body(poly(FL - 18f, FRONT + 26f, FR + 18f, FRONT + 26f, FR + 18f, 340f, FL - 18f, 340f), 0xFF3A4856.toInt(), .45f, 0f, outline = 1f, washAlpha = 255)
        b.fill(rect(FL - 18f, FRONT + 30f, FR + 18f, FRONT + 36f), 0xFFE8B83A.toInt())
        var x = FL - 14f
        while (x < FR + 14f) { b.line(x, FRONT + 30f, x + 6f, FRONT + 36f, 2.4f, 0xFF1A1A1A.toInt()); x += 10f }
        // La tôle d'acier, son chant, le reflet brossé.
        val plate = poly(BL, BACK, BR, BACK, FR, FRONT, FL, FRONT)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, BACK, 0f, FRONT,
            intArrayOf(0xFF9AA2AC.toInt(), 0xFFC8D0D8.toInt(), 0xFF8A929C.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(plate, b.p)
        b.hatch(plate, 0f, 1.6f, .35f, withAlpha(0xFF5A626C.toInt(), 160))
        b.stroke(plate, 1f)
        b.body(poly(FL, FRONT, FR, FRONT, FR, FRONT + 4f, FL, FRONT + 4f), 0xFF6A727C.toInt(), 0f, outline = .8f, washAlpha = 255)
        // L'étoile déjà découpée par les passes précédentes : un trait sombre.
        b.c.drawPath(cutPath, b.pen(0xFF2A2420.toInt(), 1.4f))
        // Les rails du portique, le long de la table.
        b.line(BL - 8f, BACK - 18f, FL - 12f, FRONT - 6f, 4f, 0xFF5A6A7A.toInt())
        b.line(BR + 8f, BACK - 18f, FR + 12f, FRONT - 6f, 4f, 0xFF5A6A7A.toInt())
        b.line(BL - 8f, BACK - 19.4f, FL - 12f, FRONT - 7.4f, .8f, 0xFFB8C4D0.toInt())
        b.line(BR + 8f, BACK - 19.4f, FR + 12f, FRONT - 7.4f, .8f, 0xFFB8C4D0.toInt())
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val pos = (t % CYCLE) / CYCLE * (SAMPLES - 1)
        val i = pos.toInt().coerceAtMost(SAMPLES - 2)
        val f = pos - i
        val hu = pu[i] + (pu[i + 1] - pu[i]) * f
        val hv = pv[i] + (pv[i + 1] - pv[i]) * f
        val hx = sx(hu, hv); val hy = sy(hv)
        // Le trait encore chaud derrière la tête : blanc, jaune, rouge, puis il rejoint le noir.
        for (k in 1 until TRAIL) {
            val a = i - k
            if (a < 0) break
            val heat = 1f - k / TRAIL.toFloat()
            ink.strokeWidth = 1.4f + heat * 1.4f
            ink.color = when {
                heat > .8f -> 0xFFFFF4D0.toInt()
                heat > .5f -> withAlpha(0xFFFFB040.toInt(), 255)
                else -> withAlpha(0xFFE0401A.toInt(), (255 * heat / .5f).toInt())
            }
            c.drawLine(px[a], py[a], px[a + 1], py[a + 1], ink)
        }
        // Les étincelles : chacune naît sous le faisceau et part en cloche.
        for (k in 0 until SPARKS) {
            val life = .55f
            val slot = ((t + k * life / SPARKS) / life).toInt()
            val age = (t + k * life / SPARKS) % life
            val dir = hash(slot, k) * 2f - 1f
            val speed = 60f + 80f * hash(slot, k + 50)
            val x = hx + dir * speed * age
            val y = hy + 3f + (-30f * hash(slot, k + 90)) * age + 260f * age * age
            val heat = 1f - age / life
            ink.color = withAlpha(if (heat > .5f) 0xFFFFF0B0.toInt() else 0xFFFF9030.toInt(), (255 * heat).toInt())
            ink.strokeWidth = 1.1f
            c.drawLine(x, y, x - dir * speed * .02f, y - 2.4f, ink)
        }
        // Le portique à la hauteur de la tête, le chariot, la buse et le faisceau.
        val lx = BL - 8f + (FL - 12f - BL + 8f) * hv; val rx = BR + 8f + (FR + 12f - BR - 8f) * hv
        val gy = BACK - 18f + (FRONT - 6f - BACK + 18f) * hv - 20f
        fill.color = 0xFFE8B83A.toInt(); c.drawRect(lx, gy - 6f, rx, gy + 6f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .9f; c.drawRect(lx, gy - 6f, rx, gy + 6f, ink)
        ink.color = withAlpha(0xFFFFF0C0.toInt(), 200); ink.strokeWidth = .8f; c.drawLine(lx, gy - 5f, rx, gy - 5f, ink)
        for (k in 0 until 2) {
            val side = if (k == 0) lx else rx
            fill.color = 0xFF4A5A6A.toInt(); c.drawRect(side - 5f, gy - 8f, side + 5f, gy + 22f, fill)
            ink.color = Ink.SEPIA; c.drawRect(side - 5f, gy - 8f, side + 5f, gy + 22f, ink)
        }
        fill.color = 0xFF3A3E44.toInt(); c.drawRect(hx - 9f, gy - 10f, hx + 9f, gy + 10f, fill)
        ink.color = Ink.SEPIA; c.drawRect(hx - 9f, gy - 10f, hx + 9f, gy + 10f, ink)
        fill.color = 0xFF9AA2AC.toInt(); c.drawRect(hx - 4f, gy + 10f, hx + 4f, hy - 8f, fill)
        c.drawRect(hx - 4f, gy + 10f, hx + 4f, hy - 8f, ink)
        fill.color = 0xFFC8B070.toInt()
        c.drawRect(hx - 2.4f, hy - 8f, hx + 2.4f, hy - 3f, fill)
        beam.color = withAlpha(0xFFE8D8FF.toInt(), 230); beam.strokeWidth = 1.4f
        c.drawLine(hx, hy - 3f, hx, hy, beam)
        // L'éclat au point d'impact.
        val flick = .8f + .2f * sin(t * 40f)
        fill.color = withAlpha(0xFFFFE8B0.toInt(), (110 * flick).toInt()); c.drawCircle(hx, hy, 11f, fill)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 255); c.drawCircle(hx, hy, 2.4f, fill)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** La tôle : bord du fond (gauche, droite, hauteur) et bord avant. */
        const val BL = 94f
        const val BR = 266f
        const val BACK = 150f
        const val FL = 60f
        const val FR = 300f
        const val FRONT = 272f
        const val SAMPLES = 240
        const val TRAIL = 30
        const val CYCLE = 12f
        const val SPARKS = 34
    }
}
