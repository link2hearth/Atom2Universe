package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * Rubidium — le GPS. Les satellites GPS emportent des horloges atomiques au rubidium : c'est leur
 * heure, d'une précision folle, qui permet au téléphone de savoir où il est. Au crépuscule, un
 * randonneur sur une crête regarde son téléphone ; des satellites passent dans le ciel, leurs
 * signaux arrivent en ondes, et le point bleu pulse sur la carte.
 */
internal class SceneRubidium : EngravedScene() {

    override val sky = Sky.DUSK
    override val nameRes = R.string.card_scene_gps
    override val noteRes = R.string.card_note_gps
    override val explainRes = R.string.card_explain_gps

    override fun engrave(b: Burin) {
        // Les chaînes de montagnes, de plus en plus sombres vers nous.
        val far = Path().apply {
            moveTo(40f, 200f); lineTo(76f, 168f); lineTo(104f, 184f); lineTo(146f, 150f); lineTo(186f, 182f)
            lineTo(222f, 160f); lineTo(262f, 186f); lineTo(296f, 164f); lineTo(320f, 178f); lineTo(320f, 240f); lineTo(40f, 240f); close()
        }
        b.body(far, 0xFF8A7A9A.toInt(), .25f, 120f, Fade(0f, 150f, 0f, 236f, 20, 160), outline = .6f, washAlpha = 255)
        val mid = Path().apply {
            moveTo(40f, 236f); cubicTo(90f, 206f, 130f, 222f, 170f, 214f); cubicTo(220f, 204f, 270f, 230f, 320f, 216f)
            lineTo(320f, 280f); lineTo(40f, 280f); close()
        }
        b.body(mid, 0xFF5A4A6A.toInt(), .35f, 110f, Fade(0f, 206f, 0f, 276f, 40, 200), outline = .6f, washAlpha = 255)
        // La crête du premier plan où se tient le randonneur.
        val ridge = Path().apply {
            moveTo(40f, 300f); cubicTo(90f, 276f, 140f, 280f, 180f, 284f); cubicTo(230f, 288f, 280f, 270f, 320f, 282f)
            lineTo(320f, 340f); lineTo(40f, 340f); close()
        }
        b.body(ridge, 0xFF2A2234.toInt(), .5f, 60f, outline = .8f, washAlpha = 255)
        b.stroke(Path().apply { moveTo(40f, 300f); cubicTo(90f, 276f, 140f, 280f, 180f, 284f); cubicTo(230f, 288f, 280f, 270f, 320f, 282f) }, 1f, withAlpha(0xFFFFB890.toInt(), 160))
        hiker(b)
    }

    /** Le randonneur de dos, en contre-jour : bonnet, sac, le bras qui tient le téléphone. */
    private fun hiker(b: Burin) {
        val dark = 0xFF1C1626.toInt()
        val legs = Path().apply {
            addRoundRect(HX - 13f, 250f, HX - 3f, 288f, 4f, 4f, Path.Direction.CW)
            addRoundRect(HX + 2f, 250f, HX + 12f, 286f, 4f, 4f, Path.Direction.CW)
        }
        b.fill(legs, dark)
        val coat = Path().apply {
            moveTo(HX - 20f, 200f); cubicTo(HX - 24f, 230f, HX - 20f, 250f, HX - 16f, 258f); lineTo(HX + 16f, 258f)
            cubicTo(HX + 20f, 250f, HX + 24f, 230f, HX + 20f, 200f); cubicTo(HX + 10f, 192f, HX - 10f, 192f, HX - 20f, 200f); close()
        }
        b.body(coat, 0xFF3A2A4A.toInt(), .45f, 70f, outline = 1f, washAlpha = 255)
        // Le bras droit levé vers le téléphone.
        b.c.drawPath(limb(HX + 16f, 204f, PHONE_X - 6f, PHONE_Y + 18f, 9f, 7f), b.pen(0xFF34263E.toInt()))
        b.stroke(limb(HX + 16f, 204f, PHONE_X - 6f, PHONE_Y + 18f, 9f, 7f), .8f)
        // Le sac à dos.
        val pack = Path().apply { addRoundRect(HX - 17f, 202f, HX + 13f, 246f, 8f, 8f, Path.Direction.CW) }
        b.body(pack, 0xFFA8442A.toInt(), .45f, 70f, outline = 1f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(HX - 13f, 224f, HX + 9f, 242f, 5f, 5f, Path.Direction.CW) }, 0xFF8A3420.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(HX - 22f, 196f, HX + 18f, 204f, 4f, 4f, Path.Direction.CW) }, 0xFF4A6A8A.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // La tête et le bonnet.
        b.body(ellipse(HX, 186f, 11f, 12f), 0xFF5A3A2A.toInt(), .4f, 0f, outline = .9f, washAlpha = 255)
        b.body(Path().apply { moveTo(HX - 12f, 184f); cubicTo(HX - 12f, 168f, HX + 12f, 168f, HX + 12f, 184f); close() }, 0xFFC8A040.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.fill(ellipse(HX, 169f, 4f, 3.4f), 0xFFC8A040.toInt())
        // Le liseré du couchant sur l'épaule gauche.
        b.stroke(Path().apply { moveTo(HX - 20f, 202f); cubicTo(HX - 23f, 220f, HX - 22f, 240f, HX - 17f, 256f) }, 1.4f, withAlpha(0xFFFFB890.toInt(), 200))
        // Le téléphone, l'écran vers nous.
        val phone = Path().apply { addRoundRect(PHONE_X - 15f, PHONE_Y - 26f, PHONE_X + 15f, PHONE_Y + 26f, 5f, 5f, Path.Direction.CW) }
        b.body(phone, 0xFF1A1A20.toInt(), 0f, outline = 1f, washAlpha = 255)
        // La carte : prés, lac, courbes de niveau, le sentier en pointillé.
        b.c.save(); b.c.clipRect(SCR_L, SCR_T, SCR_R, SCR_B)
        b.fill(rect(SCR_L, SCR_T, SCR_R, SCR_B), 0xFFE8ECD8.toInt())
        b.fill(ellipse(PHONE_X + 8f, PHONE_Y - 14f, 8f, 5f), 0xFF9AC8E8.toInt())
        for (k in 0 until 4) {
            b.c.drawOval(PHONE_X - 18f + k * 3f, PHONE_Y - 4f + k * 3f, PHONE_X + 6f - k * 3f, PHONE_Y + 24f - k * 3f, b.pen(withAlpha(0xFFB89A6A.toInt(), 200), .6f))
        }
        val path = Path().apply { moveTo(PHONE_X - 12f, PHONE_Y + 24f); cubicTo(PHONE_X - 4f, PHONE_Y + 10f, PHONE_X + 8f, PHONE_Y + 8f, PHONE_X, PHONE_Y - 4f); cubicTo(PHONE_X - 6f, PHONE_Y - 12f, PHONE_X + 2f, PHONE_Y - 20f, PHONE_X + 10f, PHONE_Y - 24f) }
        b.stroke(path, 1.4f, 0xFFC84A3A.toInt())
        b.c.restore()
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les satellites traversent le ciel lentement ; chacun envoie son signal par ondes.
        for (k in 0 until 3) {
            val period = 40f + k * 9f
            val u = ((t + k * 13f) % period) / period
            val x = 30f + u * 300f
            val y = 82f + k * 22f + 10f * sin(u * 3f + k)
            satellite(c, x, y)
            // Trois fronts d'onde qui descendent vers le téléphone.
            for (w in 0 until 3) {
                val f = ((t * .6f + w / 3f + k * .21f) % 1f)
                val wx = x + (PHONE_X - x) * f; val wy = y + (PHONE_Y - y) * f
                ink.color = withAlpha(0xFFFFF0C8.toInt(), (150 * (1f - f)).toInt()); ink.strokeWidth = 1f
                val r = 5f + 5f * f
                c.drawArc(wx - r, wy - r, wx + r, wy + r, 20f, 140f, false, ink)
            }
        }
        // Le point bleu du téléphone et son halo qui pulse ; la flèche de direction.
        val pulse = (t * .8f) % 1f
        val dx = PHONE_X; val dy = PHONE_Y - 4f
        fill.color = withAlpha(0xFF3A8AFF.toInt(), (110 * (1f - pulse)).toInt())
        c.drawCircle(dx, dy, 3f + 8f * pulse, fill)
        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(dx, dy, 3.4f, fill)
        fill.color = 0xFF2A7AFF.toInt(); c.drawCircle(dx, dy, 2.4f, fill)
        // La lueur de l'écran sur le visage et la main.
        fill.color = withAlpha(0xFFD8E8FF.toInt(), (30 + 10 * sin(t * 2f)).toInt())
        c.drawCircle(PHONE_X, PHONE_Y, 34f, fill)
    }

    private fun satellite(c: Canvas, x: Float, y: Float) {
        fill.color = 0xFFD8AE4E.toInt(); c.drawRect(x - 2.4f, y - 2.4f, x + 2.4f, y + 2.4f, fill)
        fill.color = 0xFF2A4A9A.toInt()
        c.drawRect(x - 12f, y - 1.6f, x - 3.4f, y + 1.6f, fill)
        c.drawRect(x + 3.4f, y - 1.6f, x + 12f, y + 1.6f, fill)
        ink.color = withAlpha(0xFFE8ECF0.toInt(), 200); ink.strokeWidth = .6f
        c.drawLine(x - 12f, y, x + 12f, y, ink)
    }

    private companion object {
        const val HX = 150f
        const val PHONE_X = 206f
        const val PHONE_Y = 170f
        const val SCR_L = PHONE_X - 13f
        const val SCR_R = PHONE_X + 13f
        const val SCR_T = PHONE_Y - 22f
        const val SCR_B = PHONE_Y + 22f
    }
}
