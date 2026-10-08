package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gadolinium — l'IRM avec produit de contraste. Injecté dans le sang, le gadolinium fait briller
 * les vaisseaux sur l'image d'IRM. À l'écran, l'appareil parcourt le cerveau coupe après coupe
 * (le trait sur le profil montre où) ; la seringue pousse le produit, et les vaisseaux s'allument
 * en blanc dans chaque coupe.
 */
internal class SceneGadolinium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_mri_contrast
    override val noteRes = R.string.card_note_mri_contrast
    override val explainRes = R.string.card_explain_mri_contrast

    override fun engrave(b: Burin) {
        // L'écran de la console, deux panneaux.
        b.wash(rect(40f, 50f, 320f, 340f), 0xFF1A1E24.toInt(), 255)
        b.body(rect(PANEL.left, PANEL.top, PANEL.right, PANEL.bottom), 0xFF050608.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.body(rect(52f, 98f, 136f, 232f), 0xFF050608.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.body(rect(52f, 240f, 136f, 300f), 0xFF0C1016.toInt(), 0f, outline = .8f, washAlpha = 255)
        // Le profil de la tête, en coupe : crâne clair, cerveau gris.
        val head = Path().apply {
            moveTo(80f, 210f); quadTo(66f, 170f, 74f, 140f); quadTo(86f, 108f, 112f, 110f); quadTo(130f, 114f, 128f, 140f)
            lineTo(130f, 156f); lineTo(126f, 160f); lineTo(126f, 172f); quadTo(126f, 186f, 116f, 190f); lineTo(112f, 214f); close()
        }
        b.wash(head, 0xFF4A4A4E.toInt(), 255)
        b.c.drawPath(head, b.pen(0xFFD8D8D8.toInt(), 2.2f))
        b.wash(ellipse(98f, HEAD_CY, 22f, HEAD_R), 0xFF9A9A9A.toInt(), 255)
        b.wash(ellipse(96f, HEAD_CY + 4f, 13f, 14f), 0xFFBABABA.toInt(), 200)
        b.line(84f, 176f, 96f, 196f, 3f, 0xFF7A7A7A.toInt())
        // La seringue du produit de contraste.
        b.body(rect(70f, 262f, 118f, 276f), 0xFFDDE6EE.toInt(), 0f, outline = .8f, washAlpha = 200)
        b.line(118f, 269f, 130f, 269f, 1.2f, 0xFFC8CCD2.toInt())
        for (k in 1..4) b.line(72f + k * 9f, 262f, 72f + k * 9f, 266f, .6f)
        // Les repères de l'écran : graduations sur le bord du grand panneau.
        for (k in 0..10) {
            val y = PANEL.top + 12f + k * 21f
            b.line(PANEL.left + 2f, y, PANEL.left + (if (k % 5 == 0) 8f else 5f), y, .7f, 0xFF8A9AAA.toInt())
        }
    }

    override fun sprites() = listOf(
        Sprite(BRAIN, RectF(AX - 74f, AY - 84f, AX + 74f, AY + 84f)) { b ->
            b.c.drawOval(AX - 70f, AY - 80f, AX + 70f, AY + 80f, b.pen(0xFFE8E8E8.toInt(), 6f))
            b.c.drawOval(AX - 65f, AY - 75f, AX + 65f, AY + 75f, b.pen(0xFF101010.toInt(), 4f))
            b.wash(ellipse(AX, AY, 62f, 72f), 0xFF8A8A8E.toInt(), 255)
            b.wash(ellipse(AX, AY + 2f, 42f, 52f), 0xFFB4B4B8.toInt(), 220)
            // Les sillons du cortex.
            for (k in 0 until 26) {
                val a = k * 6.2831855f / 26f + .1f
                val ca = cos(a); val sn = sin(a)
                val wob = if (k % 2 == 0) 6f else -6f
                b.taper(AX + 60f * ca, AY + 70f * sn, AX + 52f * ca - wob * sn, AY + 60f * sn + wob * ca,
                    AX + 48f * ca + wob * sn, AY + 54f * sn - wob * ca, AX + 42f * ca, AY + 48f * sn, 2f, 0xFF3A3A3E.toInt())
            }
            b.line(AX, AY - 72f, AX, AY - 40f, 1.4f, 0xFF2A2A2E.toInt())
            b.line(AX, AY + 44f, AX, AY + 72f, 1.4f, 0xFF2A2A2E.toInt())
        },
        Sprite(VENTRICLES, RectF(AX - 24f, AY - 30f, AX + 24f, AY + 30f)) { b ->
            for (side in floatArrayOf(-1f, 1f)) {
                b.wash(Path().apply {
                    moveTo(AX + side * 3f, AY - 24f); quadTo(AX + side * 18f, AY - 18f, AX + side * 12f, AY + 4f)
                    quadTo(AX + side * 16f, AY + 20f, AX + side * 6f, AY + 26f); quadTo(AX + side * 8f, AY, AX + side * 3f, AY - 24f); close()
                }, 0xFF0A0A0C.toInt(), 255)
            }
        },
        Sprite(VESSELS, RectF(AX - 64f, AY - 74f, AX + 64f, AY + 74f)) { b ->
            val white = 0xFFFFFFFF.toInt()
            b.c.drawCircle(AX, AY + 8f, 9f, b.pen(white, 2f))
            for (side in floatArrayOf(-1f, 1f)) {
                b.taper(AX + side * 8f, AY + 6f, AX + side * 26f, AY - 2f, AX + side * 40f, AY - 18f, AX + side * 56f, AY - 30f, 3.2f, white)
                b.taper(AX + side * 30f, AY - 4f, AX + side * 42f, AY + 6f, AX + side * 50f, AY + 20f, AX + side * 58f, AY + 34f, 2.4f, white)
                b.taper(AX + side * 40f, AY - 18f, AX + side * 44f, AY - 34f, AX + side * 46f, AY - 46f, AX + side * 42f, AY - 60f, 2f, white)
                b.taper(AX + side * 5f, AY - 2f, AX + side * 8f, AY - 24f, AX + side * 10f, AY - 46f, AX + side * 8f, AY - 68f, 2.4f, white)
                b.taper(AX + side * 6f, AY + 16f, AX + side * 14f, AY + 34f, AX + side * 18f, AY + 52f, AX + side * 16f, AY + 68f, 2.4f, white)
            }
            b.glow(AX, AY, 40f, white, 40)
        }
    )

    // ───────────── animation ─────────────

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La coupe monte et descend dans la tête.
        val z = .8f * sin(t * 6.2831855f / SWEEP)
        val k = sqrt(1f - z * z)
        val tc = t % CYCLE
        val contrast = smooth((tc - 3f) / 1.2f) * (1f - smooth((tc - 10f) / 2f))

        c.save(); c.clipRect(PANEL)
        c.save(); c.scale(k, k, AX, AY)
        s.draw(c, BRAIN)
        val middle = (1f - abs(z) / .45f).coerceIn(0f, 1f)
        if (middle > 0f) s.draw(c, VENTRICLES, alpha = (255 * middle).toInt())
        if (contrast > 0f) s.draw(c, VESSELS, alpha = (255 * contrast).toInt())
        c.restore()
        c.restore()

        // Le trait de coupe sur le profil.
        val y = HEAD_CY - z * HEAD_R
        line.color = 0xFFFFD040.toInt(); line.strokeWidth = 1.2f
        c.drawLine(58f, y, 132f, y, line)
        if (contrast > 0f) {
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (110 * contrast).toInt())
            c.drawOval(84f, HEAD_CY - HEAD_R + 6f, 112f, HEAD_CY + HEAD_R - 6f, fill)
        }

        // La seringue pousse le produit, puis se recharge doucement.
        val push = smooth((tc - 2f) / 1f) * (1f - smooth((tc - 10.5f) / 1.5f))
        fill.color = 0xFFC8CCD2.toInt()
        c.drawRect(56f, 266f, 72f + 30f * push, 272f, fill)
        c.drawRect(54f, 260f, 58f, 278f, fill)
        fill.color = withAlpha(0xFF7AD0FF.toInt(), (180 * (1f - push)).toInt())
        c.drawRect(72f + 30f * push, 264f, 116f, 274f, fill)
    }

    private fun smooth(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private companion object {
        const val AX = 226f
        const val AY = 186f
        const val HEAD_CY = 160f
        const val HEAD_R = 30f
        const val SWEEP = 6f
        const val CYCLE = 12f
        const val BRAIN = 1
        const val VENTRICLES = 2
        const val VESSELS = 3
        val PANEL = RectF(142f, 70f, 308f, 302f)
    }
}
