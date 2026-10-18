package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Erbium — le câble sous la mer. Internet traverse les océans en éclairs de lumière dans des
 * fibres de verre ; tous les quatre-vingts kilomètres, une fibre dopée à l'erbium leur rend leur
 * éclat. Sur le fond de la mer, le câble ouvert montre les éclairs qui pâlissent, passent dans la
 * bobine rose du répéteur et en ressortent éclatants.
 */
internal class SceneErbium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_undersea_cable
    override val noteRes = R.string.card_note_undersea_cable
    override val explainRes = R.string.card_explain_undersea_cable

    override fun engrave(b: Burin) {
        // L'eau, claire en haut, noire au fond.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 300f,
            0xFF2A7AA0.toInt(), 0xFF061A2C.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 340f, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, 270f), 0f, 4f, .4f, withAlpha(0xFF000000.toInt(), 70))
        // Le fond de sable, ses rides et ses cailloux.
        val sand = Path().apply { moveTo(40f, 270f); quadTo(120f, 258f, 200f, 266f); quadTo(270f, 272f, 320f, 262f); lineTo(320f, 340f); lineTo(40f, 340f); close() }
        b.body(sand, 0xFF8A7A5A.toInt(), .35f, 10f, outline = .8f, washAlpha = 255)
        for (k in 0 until 6) b.c.drawPath(Path().apply { val y = 284f + k * 9f; moveTo(40f, y); quadTo(110f, y - 5f, 180f, y); quadTo(250f, y + 5f, 320f, y) }, b.pen(withAlpha(0xFF3A3020.toInt(), 120), .7f))
        for ((x, r) in listOf(70f to 8f, 252f to 10f, 292f to 6f)) b.body(ellipse(x, 276f, r, r * .6f), 0xFF4A4A44.toInt(), .45f, 30f, outline = .7f, washAlpha = 255)
        // Le câble : gaine noire, ouverte sur toute sa longueur, et le répéteur au milieu.
        b.body(rect(40f, CY - 9f, 320f, CY + 9f), 0xFF1A1C20.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
        b.wash(rect(40f, CY - 5f, 320f, CY + 5f), 0xFF0A1420.toInt(), 255)
        b.body(Path().apply { addRoundRect(RX - 26f, CY - 15f, RX + 26f, CY + 15f, 12f, 12f, Path.Direction.CW) }, 0xFF5A6068.toInt(), .3f, 90f, outline = 1f, washAlpha = 255)
        b.wash(Path().apply { addRoundRect(RX - 22f, CY - 10f, RX + 22f, CY + 10f, 8f, 8f, Path.Direction.CW) }, 0xFF101820.toInt(), 255)
        b.line(40f, CY - 9f, 320f, CY - 9f, .7f, 0xFF4A4E56.toInt())
        // Les fibres de verre, fines lignes dans la gaine.
        for (y in FIBRES) {
            b.line(40f, y, RX - 22f, y, .5f, 0xFF4A6A80.toInt())
            b.line(RX + 22f, y, 320f, y, .5f, 0xFF4A6A80.toInt())
        }
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ray = Path()
    private val fish = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les rais de lumière qui descendent de la surface.
        for (k in 0 until 4) {
            val x = 70f + k * 70f + 10f * sin(t * .3f + k)
            ray.reset()
            ray.moveTo(x - 6f, 50f); ray.lineTo(x + 6f, 50f); ray.lineTo(x + 40f, 250f); ray.lineTo(x + 18f, 250f); ray.close()
            fill.color = withAlpha(0xFFBFEFFF.toInt(), (22 + 12 * sin(t * .8f + k * 2f)).toInt())
            c.drawPath(ray, fill)
        }
        // Les poissons passent.
        for (k in 0 until 3) {
            val speed = 16f + 6f * k
            val dir = if (k == 1) -1f else 1f
            val raw = (t * speed + k * 110f) % 340f
            val x = if (dir > 0f) 20f + raw else 340f - raw
            val y = 110f + k * 42f + 6f * sin(t * .6f + k)
            val tail = 3f * sin(t * 8f + k)
            fish.reset()
            fish.moveTo(x + dir * 12f, y)
            fish.quadTo(x, y - 6f, x - dir * 8f, y); fish.quadTo(x, y + 6f, x + dir * 12f, y)
            fish.moveTo(x - dir * 7f, y); fish.lineTo(x - dir * 15f, y - 5f + tail); fish.lineTo(x - dir * 15f, y + 5f + tail); fish.close()
            fill.color = withAlpha(0xFF0A2030.toInt(), 170); c.drawPath(fish, fill)
        }

        // La bobine d'erbium du répéteur luit en rose, pompée par son laser.
        val pump = .75f + .25f * sin(t * 6f)
        fill.color = withAlpha(0xFFFF5AA0.toInt(), (70 * pump).toInt()); c.drawCircle(RX, CY, 20f, fill)
        line.color = withAlpha(0xFFFF8AC0.toInt(), (230 * pump).toInt()); line.strokeWidth = 1.2f
        for (k in 0 until 5) c.drawOval(RX - 16f + k * 4f, CY - 7f, RX - 8f + k * 4f, CY + 7f, line)
        fill.color = 0xFFFF3A6A.toInt(); c.drawCircle(RX + 17f, CY - 6f, 1.8f, fill)

        // Les éclairs de lumière : ils pâlissent le long du câble et repartent éclatants après le répéteur.
        for (j in FIBRES.indices) {
            val y = FIBRES[j]
            for (k in 0 until 6) {
                val x = 40f + ((t * 70f + k * 47f + j * 19f) % 280f)
                if (x > RX - 24f && x < RX + 24f) continue
                val bright = if (x < RX) 1f - .7f * (x - 40f) / (RX - 64f) else 1f - .7f * (x - RX - 24f) / (296f - RX)
                line.color = withAlpha(0xFFE8FFFF.toInt(), (255 * bright.coerceIn(.2f, 1f)).toInt()); line.strokeWidth = 1.6f
                c.drawLine(x - 5f, y, x + 5f, y, line)
                fill.color = withAlpha(0xFFBFF4FF.toInt(), (70 * bright.coerceIn(.2f, 1f)).toInt()); c.drawCircle(x, y, 4f, fill)
            }
        }
        // Une algue ondule près du câble.
        line.color = 0xFF2A6A3A.toInt(); line.strokeWidth = 2.4f
        for (k in 0 until 3) {
            val bx = 96f + k * 7f
            ray.reset(); ray.moveTo(bx, 274f)
            ray.quadTo(bx + 8f * sin(t * 1.1f + k), 250f, bx + 12f * sin(t * .9f + k * 1.7f), 222f - k * 6f)
            c.drawPath(ray, line)
        }
    }

    private companion object {
        const val CY = 232f
        const val RX = 190f
        val FIBRES = floatArrayOf(228.5f, 231f, 233.5f, 236f)
    }
}
