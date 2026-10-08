package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Niobium — l'IRM. Le grand aimant d'un appareil d'IRM est une bobine supraconductrice en
 * niobium-titane, refroidie à l'hélium liquide. Vu de face, le patient allongé, les pieds vers
 * nous, glisse dans l'anneau ; pendant l'examen, le tunnel s'éclaire par à-coups et le petit
 * écran dessine, ligne après ligne, la coupe de sa tête. Puis la table le ramène.
 */
internal class SceneNiobium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_mri
    override val noteRes = R.string.card_note_mri
    override val explainRes = R.string.card_explain_mri

    private val bore = ellipse(VX, VY, BORE_R, BORE_R)

    /** Le facteur d'échelle d'un point à la profondeur [d] : 2,4 tout devant, 0,5 au plan de l'anneau. */
    private fun f(d: Float) = F0 * D / (D + d)

    override fun engrave(b: Burin) {
        // La salle : mur clair, sol.
        val wall = rect(40f, 50f, 320f, FLOOR)
        b.wash(wall, 0xFFD8E0E4.toInt(), 255)
        b.washGradient(wall, 0xFF3A4A54.toInt(), 0f, 100f, 0, 0f, FLOOR, 90)
        b.hatchRect(RectF(40f, 50f, 320f, FLOOR), 90f, 3.4f, .3f, Ink.BROWN, Fade(0f, 60f, 0f, FLOOR, 20, 110))
        val floor = rect(40f, FLOOR, 320f, 340f)
        b.wash(floor, 0xFFB8C0C0.toInt(), 255)
        b.hatchRect(RectF(40f, FLOOR, 320f, 340f), 0f, 2.2f, .35f, Ink.SEPIA, Fade(0f, FLOOR, 0f, 330f, 40, 160))
        // L'appareil : la grande face blanche arrondie, le capot gris, l'anneau.
        val face = Path().apply { addRoundRect(70f, 66f, 290f, 286f, 60f, 60f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(70f, 66f, 290f, 286f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFE4E8EC.toInt(), 0xFFB8C0C8.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(face, b.p)
        b.stroke(face, 1.2f)
        b.c.drawCircle(VX, VY, BORE_R + 16f, b.pen(0xFFC8CED4.toInt(), 14f))
        b.c.drawCircle(VX, VY, BORE_R + 23f, b.pen(withAlpha(Ink.SEPIA, 170), .8f))
        b.c.drawCircle(VX, VY, BORE_R + 9f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200), 1.2f))
        // Le tunnel : ses parois, et la sortie de l'autre côté, plus claire.
        b.pen(0xFF000000.toInt()).shader = RadialGradient(VX, VY - 4f, BORE_R,
            intArrayOf(0xFFE8F0F8.toInt(), 0xFFB8C8D8.toInt(), 0xFF6A7A8A.toInt()), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(bore, b.p)
        b.c.drawCircle(VX, VY, BORE_R * .62f, b.pen(withAlpha(0xFF5A6A7A.toInt(), 200), 1f))
        b.stroke(bore, 1.2f)
        // Le petit écran de contrôle en haut à droite, éteint ; les témoins.
        b.body(Path().apply { addRoundRect(SCR_L - 3f, SCR_T - 3f, SCR_R + 3f, SCR_B + 3f, 3f, 3f, Path.Direction.CW) }, 0xFF5A6066.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.fill(rect(SCR_L, SCR_T, SCR_R, SCR_B), 0xFF0A0E12.toInt())
        // Le socle de la table : il va de nous jusqu'à l'anneau.
        val near = f(0f); val far = f(D_FACE)
        val base = poly(
            VX - 40f * near, VY + 60f * near, VX - 40f * far, VY + 60f * far,
            VX + 40f * far, VY + 60f * far, VX + 40f * near, VY + 60f * near,
            VX + 40f * near, VY + 100f * near, VX - 40f * near, VY + 100f * near
        )
        b.body(base, 0xFFB8C0C8.toInt(), .3f, 0f, Fade(0f, VY + 60f * near, 0f, VY + 60f * far, 200, 30), outline = 1f, washAlpha = 255)
    }

    override fun sprites(): List<Sprite> = listOf(
        // La coupe de la tête, de profil, en gris : crâne, cerveau et ses circonvolutions, cervelet.
        Sprite(SLICE, RectF(SCR_L, SCR_T, SCR_R, SCR_B)) { b ->
            val cx = (SCR_L + SCR_R) / 2f; val cy = (SCR_T + SCR_B) / 2f
            b.c.scale(.74f, .74f, cx, cy)
            b.fill(ellipse(cx, cy, 15f, 13f), 0xFF5A5E62.toInt())
            b.fill(ellipse(cx + 12f, cy + 2f, 4f, 6f), 0xFF5A5E62.toInt())
            b.fill(ellipse(cx - 1f, cy - 1f, 12.5f, 10f), 0xFFB8BCC0.toInt())
            b.fill(ellipse(cx - 7f, cy + 7f, 5f, 3.4f), 0xFF9A9EA2.toInt())
            val fold = b.pen(0xFF6A6E72.toInt(), .5f)
            for (k in 0 until 7) {
                val x = cx - 10f + k * 3.2f
                b.c.drawLine(x, cy - 8f + (k % 2) * 2f, x + 1.4f, cy + 2f, fold)
            }
            b.fill(rect(cx - 1f, cy + 8f, cx + 2f, cy + 16f), 0xFF8A8E92.toInt())
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shape = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        val slide = when {
            ph < IN -> smooth(ph / IN)
            ph < OUT -> 1f
            ph < OUT + IN -> 1f - smooth((ph - OUT) / IN)
            else -> 0f
        }
        val d0 = slide * D_IN
        val near = f(d0); val far = f(d0 + LENGTH)
        // Le plateau qui glisse, avec son chant ; la couverture ; les pieds.
        shape.reset()
        shape.moveTo(VX - 46f * near, VY + 46f * near); shape.lineTo(VX - 46f * far, VY + 46f * far)
        shape.lineTo(VX + 46f * far, VY + 46f * far); shape.lineTo(VX + 46f * near, VY + 46f * near)
        shape.lineTo(VX + 46f * near, VY + 56f * near); shape.lineTo(VX - 46f * near, VY + 56f * near); shape.close()
        fill.color = 0xFFE8ECF0.toInt(); c.drawPath(shape, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = 1f; c.drawPath(shape, ink)
        ink.strokeWidth = .8f; c.drawLine(VX - 46f * near, VY + 46f * near, VX + 46f * near, VY + 46f * near, ink)
        shape.reset()
        shape.moveTo(VX - 36f * near, VY + 46f * near); shape.lineTo(VX - 34f * near, VY + 22f * near)
        shape.lineTo(VX - 30f * far, VY + 20f * far); shape.lineTo(VX + 30f * far, VY + 20f * far)
        shape.lineTo(VX + 34f * near, VY + 22f * near); shape.lineTo(VX + 36f * near, VY + 46f * near); shape.close()
        fill.color = 0xFF8AB4D8.toInt(); c.drawPath(shape, fill)
        ink.strokeWidth = 1f; c.drawPath(shape, ink)
        ink.color = withAlpha(0xFF4A6A8A.toInt(), 200); ink.strokeWidth = .8f
        for (k in -2..2) c.drawLine(VX + k * 12f * near, VY + 24f * near, VX + k * 10f * far, VY + 22f * far, ink)
        // Les deux pieds, chaussettes blanches, qui dépassent de la couverture.
        for (k in 0 until 2) {
            val side = if (k == 0) -1f else 1f
            val fx = VX + side * 12f * near
            fill.color = 0xFFF4F2EE.toInt()
            c.drawOval(fx - 8f * near, VY + 6f * near, fx + 8f * near, VY + 30f * near, fill)
            ink.color = Ink.SEPIA; ink.strokeWidth = .9f
            c.drawOval(fx - 8f * near, VY + 6f * near, fx + 8f * near, VY + 30f * near, ink)
        }
        // L'examen : le tunnel s'éclaire par à-coups (les cognements de l'aimant), l'écran dessine.
        val scan = (ph - IN) / (OUT - IN)
        if (scan in 0f..1f) {
            val knock = ((ph * 6f) % 1f).let { if (it < .3f) 1f - it / .3f else 0f }
            c.save(); c.clipPath(bore)
            fill.color = withAlpha(0xFF8AC8FF.toInt(), (60 * knock).toInt())
            c.drawCircle(VX, VY, BORE_R, fill)
            c.restore()
            ink.color = withAlpha(0xFF6AB8FF.toInt(), (120 + 80 * knock).toInt()); ink.strokeWidth = 2f
            c.drawCircle(VX, VY, BORE_R + 4f, ink)
            val line = SCR_T + (SCR_B - SCR_T) * (scan / .8f).coerceAtMost(1f)
            c.save(); c.clipRect(SCR_L, SCR_T, SCR_R, line)
            s.draw(c, SLICE)
            c.restore()
            if (scan < .8f) {
                ink.color = withAlpha(0xFF8AE8FF.toInt(), 220); ink.strokeWidth = .8f
                c.drawLine(SCR_L, line, SCR_R, line, ink)
            }
        } else if (ph > OUT && ph < OUT + .8f) {
            // L'image reste un instant, puis s'éteint.
            s.draw(c, SLICE, alpha = (255 * (1f - (ph - OUT) / .8f)).toInt())
        }
        // Le témoin vert de l'appareil.
        fill.color = if ((t % 2f) < 1f) 0xFF5AE87A.toInt() else 0xFF2A7A3A.toInt()
        c.drawCircle(SCR_L - 10f, SCR_T + 4f, 2f, fill)
        // La lueur douce du tunnel sur les pieds quand ils y entrent.
        fill.color = withAlpha(0xFFFFFFFF.toInt(), (30 * slide * (.6f + .4f * sin(t * 2f))).toInt())
        c.drawCircle(VX, VY + 20f, 40f, fill)
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private companion object {
        /** Le centre de l'anneau, où convergent les lignes du tunnel. */
        const val VX = 180f
        const val VY = 160f
        const val BORE_R = 52f
        const val FLOOR = 280f
        /** La perspective : échelle tout devant, distance de référence, profondeur du plan de l'anneau. */
        const val F0 = 2.4f
        const val D = 100f
        const val D_FACE = 380f
        /** La longueur du patient, et jusqu'où la table l'enfonce. */
        const val LENGTH = 170f
        const val D_IN = 270f
        const val CYCLE = 13f
        const val IN = 3f
        const val OUT = 9f
        const val SCR_L = 238f
        const val SCR_R = 268f
        const val SCR_T = 88f
        const val SCR_B = 110f
        const val SLICE = 1
    }
}
