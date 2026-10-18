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
import kotlin.math.floor
import kotlin.math.sin

/**
 * Césium — la seconde. La seconde est définie par les atomes de césium : les horloges atomiques
 * les plus précises sont des « fontaines » de césium. Le tube est dessiné ouvert : en bas, six
 * faisceaux laser refroidissent un petit nuage d'atomes presque jusqu'à l'immobilité ; il est
 * lancé vers le haut, traverse la cavité où les micro-ondes l'interrogent, retombe, et on mesure
 * sa lueur au retour. Au mur, l'horloge bat les secondes que le césium définit.
 */
internal class SceneCesium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_cesium_fountain
    override val noteRes = R.string.card_note_cesium_fountain
    override val explainRes = R.string.card_explain_cesium_fountain

    override fun engrave(b: Burin) {
        // Le mur du laboratoire, le sol.
        val wall = rect(40f, 50f, 320f, TABLE_T)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, TABLE_T,
            intArrayOf(0xFFD8DEE4.toInt(), 0xFFA8B0B8.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(wall, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, TABLE_T), 90f, 3.6f, .3f, Ink.BROWN, Fade(0f, 60f, 0f, TABLE_T, 20, 110))
        b.wash(rect(40f, 300f, 320f, 340f), 0xFF6A6E74.toInt(), 255)
        b.hatchRect(RectF(40f, 300f, 320f, 340f), 0f, 2.4f, .4f, 0xFF2A2C30.toInt())
        // L'horloge murale : cadran, chiffres remplacés par des traits, aiguilles des heures et des minutes.
        b.body(ellipse(CLOCK_X, CLOCK_Y, 25f, 25f), 0xFF2A2C30.toInt(), 0f, outline = 1f, washAlpha = 255)
        b.body(ellipse(CLOCK_X, CLOCK_Y, 21f, 21f), 0xFFF8F6F0.toInt(), .1f, 0f, outline = .7f, washAlpha = 255)
        for (k in 0 until 60) {
            val a = k * PI.toFloat() / 30f
            val r0 = if (k % 5 == 0) 16f else 18.5f
            b.line(CLOCK_X + r0 * sin(a), CLOCK_Y - r0 * cos(a), CLOCK_X + 20f * sin(a), CLOCK_Y - 20f * cos(a), if (k % 5 == 0) 1.4f else .5f)
        }
        b.line(CLOCK_X, CLOCK_Y, CLOCK_X + 9f * sin(5.2f), CLOCK_Y - 9f * cos(5.2f), 2.2f)
        b.line(CLOCK_X, CLOCK_Y, CLOCK_X + 15f * sin(1.1f), CLOCK_Y - 15f * cos(1.1f), 1.5f)
        // La table d'optique, ses trous, ses pieds.
        b.body(rect(56f, TABLE_T + 16f, 70f, 300f), 0xFF4A4E54.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(rect(290f, TABLE_T + 16f, 304f, 300f), 0xFF4A4E54.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(rect(44f, TABLE_T, 316f, TABLE_T + 16f), 0xFF5A5E66.toInt(), .3f, 0f, Fade(0f, TABLE_T + 16f, 0f, TABLE_T, 200, 30), outline = 1f, washAlpha = 255)
        var x = 52f
        while (x < 312f) { b.c.drawCircle(x, TABLE_T + 4f, .9f, b.pen(0xFF2A2C30.toInt())); x += 8f }
        // Les deux lasers et leurs miroirs.
        b.body(Path().apply { addRoundRect(60f, TABLE_T - 18f, 104f, TABLE_T, 3f, 3f, Path.Direction.CW) }, 0xFFE8E4DC.toInt(), .25f, 0f, outline = .9f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(256f, TABLE_T - 18f, 300f, TABLE_T, 3f, 3f, Path.Direction.CW) }, 0xFFE8E4DC.toInt(), .25f, 0f, outline = .9f, washAlpha = 255)
        b.fill(rect(68f, TABLE_T - 12f, 80f, TABLE_T - 8f), 0xFFC8281E.toInt())
        b.fill(rect(280f, TABLE_T - 12f, 292f, TABLE_T - 8f), 0xFFC8281E.toInt())
        for (mx in floatArrayOf(124f, 236f)) {
            b.body(rect(mx - 2f, TABLE_T - 14f, mx + 2f, TABLE_T), 0xFF8A9096.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
            b.body(ellipse(mx, TABLE_T - 18f, 6f, 6f), 0xFFC8CCD0.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        }
        // La fontaine en coupe : la chambre basse, le long tube et ses blindages, la cavité, le haut.
        val chamber = Path().apply { addRoundRect(CX - 30f, CHAMBER_T, CX + 30f, TABLE_T, 8f, 8f, Path.Direction.CW) }
        b.body(chamber, 0xFF8A9096.toInt(), .35f, 0f, Fade(CX + 30f, 0f, CX - 30f, 0f, 200, 20), outline = 1f, washAlpha = 255)
        b.fill(rect(CX - 20f, CHAMBER_T + 8f, CX + 20f, TABLE_T - 8f), 0xFF14181E.toInt())
        for (wx in floatArrayOf(CX - 30f, CX + 30f)) b.body(ellipse(wx, BALL_Y, 6f, 8f), 0xFFB8C0C8.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        val tube = rect(CX - 16f, TOP, CX + 16f, CHAMBER_T)
        b.body(tube, 0xFFA8B0B8.toInt(), .3f, 0f, Fade(CX + 16f, 0f, CX - 16f, 0f, 200, 20), outline = 1f, washAlpha = 255)
        b.fill(rect(CX - 9f, TOP + 6f, CX + 9f, CHAMBER_T + 2f), 0xFF14181E.toInt())
        for (y in floatArrayOf(TOP + 18f, 150f, 190f, CHAMBER_T - 8f)) {
            b.body(rect(CX - 20f, y - 3f, CX + 20f, y + 3f), 0xFF6A7078.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        }
        b.body(rect(CX - 16f, CAV_T, CX - 9f, CAV_B), 0xFFC8843A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(rect(CX + 9f, CAV_T, CX + 16f, CAV_B), 0xFFC8843A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(CX - 20f, TOP - 10f, CX + 20f, TOP, 4f, 4f, Path.Direction.CW) }, 0xFF6A7078.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        // Les câbles qui descendent vers la baie électronique.
        b.stroke(Path().apply { moveTo(CX + 16f, CAV_T + 8f); cubicTo(220f, 150f, 236f, 200f, 244f, TABLE_T - 18f) }, 1.4f, 0xFF2A2A2E.toInt())
        b.stroke(Path().apply { moveTo(CX - 16f, CHAMBER_T + 14f); cubicTo(130f, 230f, 116f, 220f, 110f, TABLE_T - 18f) }, 1.4f, 0xFF2A2A2E.toInt())
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        // Le refroidissement : les six faisceaux se croisent, le nuage se forme.
        val cooling = ph < COOL
        if (cooling) {
            val shimmer = 150 + (40 * sin(t * 13f)).toInt()
            ink.color = withAlpha(0xFFFF3A2A.toInt(), shimmer); ink.strokeWidth = 1.2f
            c.drawLine(80f, TABLE_T - 10f, 124f, TABLE_T - 18f, ink)
            c.drawLine(124f, TABLE_T - 18f, CX - 30f, BALL_Y, ink)
            c.drawLine(280f, TABLE_T - 10f, 236f, TABLE_T - 18f, ink)
            c.drawLine(236f, TABLE_T - 18f, CX + 30f, BALL_Y, ink)
            c.save(); c.clipRect(CX - 20f, CHAMBER_T + 8f, CX + 20f, TABLE_T - 8f)
            c.drawLine(CX - 20f, BALL_Y, CX + 20f, BALL_Y, ink)
            c.drawLine(CX - 20f, BALL_Y - 20f, CX + 20f, BALL_Y + 20f, ink)
            c.drawLine(CX - 20f, BALL_Y + 20f, CX + 20f, BALL_Y - 20f, ink)
            c.restore()
        }
        // Le nuage d'atomes : il se forme, monte, ralentit, retombe ; on le voit à travers la coupe.
        val y: Float; val size: Float; val glow: Float
        when {
            cooling -> { y = BALL_Y; size = ph / COOL; glow = .5f + .5f * size }
            ph < COOL + FLIGHT -> {
                val u = (ph - COOL) / FLIGHT
                y = BALL_Y - (BALL_Y - APEX) * 4f * u * (1f - u); size = 1f + .4f * u; glow = .45f
            }
            else -> {
                val u = (ph - COOL - FLIGHT) / (CYCLE - COOL - FLIGHT)
                y = BALL_Y; size = 1.4f; glow = 1f - u
            }
        }
        // Dans la cavité, les micro-ondes font luire faiblement le tube au passage du nuage.
        val inCavity = y in CAV_T..CAV_B && !cooling
        if (inCavity) {
            fill.color = withAlpha(0xFF8A6AFF.toInt(), 70); c.drawRect(CX - 9f, CAV_T, CX + 9f, CAV_B, fill)
        }
        // Au retour, le faisceau de lecture fait briller le nuage : c'est la mesure.
        if (!cooling && ph >= COOL + FLIGHT) {
            ink.color = withAlpha(0xFF5AE8FF.toInt(), (200 * glow).toInt()); ink.strokeWidth = 1.2f
            c.drawLine(CX - 30f, BALL_Y + 4f, CX + 30f, BALL_Y + 4f, ink)
        }
        fill.color = withAlpha(0xFFFFE8B0.toInt(), (60 * glow).toInt()); c.drawCircle(CX, y, 7f * size + 2f, fill)
        fill.color = withAlpha(0xFFFFF4D8.toInt(), (200 * glow).toInt()); c.drawCircle(CX, y, 3f * size, fill)
        // L'horloge : la trotteuse saute d'un cran à chaque seconde, avec un petit rebond.
        val sec = floor(t)
        val bounce = (t - sec).let { if (it < .12f) sin(it / .12f * PI.toFloat()) * .025f else 0f }
        val a = (sec % 60f) * PI.toFloat() / 30f + bounce
        ink.color = 0xFFC8281E.toInt(); ink.strokeWidth = .9f
        c.drawLine(CLOCK_X - 5f * sin(a), CLOCK_Y + 5f * cos(a), CLOCK_X + 19f * sin(a), CLOCK_Y - 19f * cos(a), ink)
        fill.color = 0xFFC8281E.toInt(); c.drawCircle(CLOCK_X, CLOCK_Y, 1.8f, fill)
    }

    private companion object {
        const val TABLE_T = 262f
        /** La fontaine : son axe, le haut du tube, la chambre basse, la cavité, la hauteur du nuage et de son apogée. */
        const val CX = 180f
        const val TOP = 84f
        const val CHAMBER_T = 214f
        const val CAV_T = 162f
        const val CAV_B = 182f
        const val BALL_Y = 238f
        const val APEX = 98f
        /** Un lancer : refroidir, voler, mesurer. */
        const val CYCLE = 2.6f
        const val COOL = .8f
        const val FLIGHT = 1.4f
        const val CLOCK_X = 82f
        const val CLOCK_Y = 110f
    }
}
