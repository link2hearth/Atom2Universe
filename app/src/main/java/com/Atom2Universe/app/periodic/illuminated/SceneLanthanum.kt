package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lanthane — l'objectif photo. Le verre au lanthane dévie fort la lumière sans brouiller les
 * couleurs : les objectifs en sont faits. On regarde droit dans l'objectif d'un appareil : au fond,
 * les lamelles du diaphragme se ferment et se rouvrent, la bague de mise au point tourne, les
 * reflets colorés glissent sur le verre, le témoin rouge clignote.
 */
internal class SceneLanthanum : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_camera_lens
    override val noteRes = R.string.card_note_camera_lens
    override val explainRes = R.string.card_explain_camera_lens

    override fun engrave(b: Burin) {
        // Le boîtier : simili-cuir noir grené, une poignée qui s'arrondit à droite.
        val body = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(40f, 50f, 320f, 340f,
            0xFF2C2C30.toInt(), 0xFF121214.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(body, b.p)
        b.stipple(body, 1600, .6f, 0xFF3C3C42.toInt(), 3)
        b.stipple(body, 900, .5f, 0xFF050506.toInt(), 4)
        b.washGradient(rect(280f, 50f, 320f, 340f), 0xFF000000.toInt(), 280f, 0f, 0, 320f, 0f, 170)
        b.body(ellipse(LAMP_X, LAMP_Y, 6f, 6f), 0xFF3A1010.toInt(), 0f, outline = .6f, washAlpha = 255)
        // La monture chromée.
        b.pen(0xFF000000.toInt()).shader = SweepGradient(CX, CY,
            intArrayOf(0xFFE4E6EA.toInt(), 0xFF5A5E64.toInt(), 0xFFCDD1D6.toInt(), 0xFF4A4E54.toInt(), 0xFFE4E6EA.toInt()), null)
        b.c.drawCircle(CX, CY, 124f, b.p)
        b.stroke(ellipse(CX, CY, 124f, 124f), .8f)
        // La bague de mise au point, en caoutchouc (ses stries tournent dans l'animation).
        b.c.drawCircle(CX, CY, 118f, b.pen(0xFF161618.toInt()))
        // La bague avant, avec son filet doré.
        b.c.drawCircle(CX, CY, 104f, b.pen(0xFF202124.toInt()))
        b.goldStroke(ellipse(CX, CY, 100f, 100f), 1.2f)
        // Le pas de vis du filtre, puis le verre, profond.
        b.c.drawCircle(CX, CY, 96f, b.pen(0xFF1A1B1E.toInt()))
        for (r in floatArrayOf(93f, 90.5f, 88f)) b.c.drawCircle(CX, CY, r, b.pen(0xFF3A3C42.toInt(), .6f))
        b.pen(0xFF000000.toInt()).shader = RadialGradient(CX, CY, 86f,
            intArrayOf(0xFF141A26.toInt(), 0xFF0A0D14.toInt(), 0xFF040508.toInt()), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        b.c.drawCircle(CX, CY, 86f, b.p)
        for (r in floatArrayOf(76f, 68f)) b.c.drawCircle(CX, CY, r, b.pen(0xFF2A3242.toInt(), .8f))
        // Le capteur, tout au fond, renvoie une lueur verdâtre quand le diaphragme est ouvert.
        b.glow(CX, CY, 34f, 0xFF3A7A64.toInt(), 110)
    }

    override fun sprites() = listOf(
        // Les reflets du traitement des lentilles, posés sur le verre.
        Sprite(GLARE, RectF(CX - 86f, CY - 86f, CX + 86f, CY + 86f)) { b ->
            b.c.save(); b.c.clipPath(ellipse(CX, CY, 86f, 86f))
            val crescent = Path().apply {
                addCircle(CX, CY, 84f, Path.Direction.CW)
                op(Path().apply { addCircle(CX + 14f, CY + 16f, 82f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            }
            b.wash(crescent, Ink.WHITE, 60)
            b.wash(Path().apply { addRoundRect(CX - 52f, CY - 62f, CX - 20f, CY - 40f, 4f, 4f, Path.Direction.CW) }, Ink.WHITE, 46)
            b.line(CX - 36f, CY - 62f, CX - 36f, CY - 40f, 1.2f, withAlpha(0xFF000000.toInt(), 120))
            b.line(CX - 52f, CY - 51f, CX - 20f, CY - 51f, 1.2f, withAlpha(0xFF000000.toInt(), 120))
            b.c.drawCircle(CX - 26f, CY - 18f, 11f, b.pen(withAlpha(0xFFB070FF.toInt(), 120), 2.2f))
            b.c.drawCircle(CX + 24f, CY + 28f, 7f, b.pen(withAlpha(0xFF60FF9A.toInt(), 110), 1.8f))
            b.c.drawCircle(CX + 40f, CY + 46f, 3.5f, b.pen(withAlpha(0xFFFFB050.toInt(), 150)))
            b.c.drawCircle(CX + 8f, CY - 44f, 4.5f, b.pen(withAlpha(0xFF8AB0FF.toInt(), 120), 1.4f))
            b.c.drawCircle(CX - 50f, CY + 30f, 2.5f, b.pen(withAlpha(0xFFB070FF.toInt(), 150)))
            b.c.restore()
        }
    )

    // ───────────── animation ─────────────

    private val blades = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = SweepGradient(CX, CY, intArrayOf(0xFF2E3034.toInt(), 0xFF16171A.toInt(), 0xFF3A3C42.toInt(),
            0xFF16171A.toInt(), 0xFF2E3034.toInt()), null)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .9f; color = 0xFF55595F.toInt() }
    private val ribs = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; color = 0xFF3A3A40.toInt() }
    private val lamp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iris = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val well = ellipse(CX, CY, IRIS_R, IRIS_R)
    private val vx = FloatArray(BLADES)
    private val vy = FloatArray(BLADES)
    private val ribLines = FloatArray(RIBS * 4)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les stries de la bague de mise au point, qui va et vient.
        val turn = 10f * sin(t * .45f) * DEG
        for (k in 0 until RIBS) {
            val a = turn + k * TWO_PI / RIBS
            val ca = cos(a); val sa = sin(a)
            ribLines[k * 4] = CX + 106f * ca; ribLines[k * 4 + 1] = CY + 106f * sa
            ribLines[k * 4 + 2] = CX + 116f * ca; ribLines[k * 4 + 3] = CY + 116f * sa
        }
        c.drawLines(ribLines, ribs)

        // Le diaphragme : un trou à sept pans qui se ferme en tournant un peu, puis se rouvre.
        val k = .5f - .5f * cos(t * TWO_PI / IRIS_CYCLE)
        val open = 52f - 38f * k
        val rot = (-90f + 26f * k) * DEG
        iris.reset()
        iris.addCircle(CX, CY, IRIS_R, Path.Direction.CW)
        for (i in 0 until BLADES) {
            val a = rot + i * TWO_PI / BLADES
            vx[i] = CX + open * cos(a); vy[i] = CY + open * sin(a)
            if (i == 0) iris.moveTo(vx[i], vy[i]) else iris.lineTo(vx[i], vy[i])
        }
        iris.close()
        c.drawPath(iris, blades)
        // Chaque bord de lamelle se prolonge au-delà du coin, jusqu'au bord du puits.
        c.save(); c.clipPath(well)
        for (i in 0 until BLADES) {
            val j = (i + 1) % BLADES
            val ex = vx[j] - vx[i]; val ey = vy[j] - vy[i]
            c.drawLine(vx[j], vy[j], vx[j] + ex * 3f, vy[j] + ey * 3f, edge)
        }
        c.restore()

        // Les reflets glissent quand l'appareil bouge un peu dans la main.
        s.draw(c, GLARE, 2f * sin(t * .3f), 1.5f * sin(t * .37f), 6f * sin(t * .23f), CX, CY)

        // Le témoin rouge du retardateur.
        val blink = max(0f, sin(t * 4f))
        lamp.color = withAlpha(0xFFFF2A1A.toInt(), (230 * blink).toInt())
        c.drawCircle(LAMP_X, LAMP_Y, 4.5f, lamp)
        lamp.color = withAlpha(0xFFFF2A1A.toInt(), (60 * blink).toInt())
        c.drawCircle(LAMP_X, LAMP_Y, 12f, lamp)
    }

    private companion object {
        const val CX = 180f
        const val CY = 190f
        const val IRIS_R = 62f
        const val IRIS_CYCLE = 7f
        const val BLADES = 7
        const val RIBS = 72
        const val GLARE = 1
        const val LAMP_X = 74f
        const val LAMP_Y = 112f
        const val DEG = .017453292f
        const val TWO_PI = 6.2831855f
    }
}
