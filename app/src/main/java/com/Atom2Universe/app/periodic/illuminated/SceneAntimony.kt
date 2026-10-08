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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Antimoine — la stibine. Le principal minerai d'antimoine pousse en longues aiguilles d'un gris
 * d'acier. Dans la pénombre d'un musée, une gerbe de cristaux tourne lentement sur son socle sous
 * un projecteur : chaque aiguille s'allume quand elle passe face à la lumière, des grains de
 * poussière flottent dans le faisceau.
 */
internal class SceneAntimony : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_stibnite_spray
    override val noteRes = R.string.card_note_stibnite_spray
    override val explainRes = R.string.card_explain_stibnite_spray

    /** Les aiguilles, en 3D autour du pied de la gerbe : pied (x, z), direction (azimut, inclinaison), longueur, épaisseur. */
    private val baseX = FloatArray(N); private val baseZ = FloatArray(N)
    private val azimuth = FloatArray(N); private val tilt = FloatArray(N)
    private val length = FloatArray(N); private val thick = FloatArray(N)

    init {
        val rnd = Random(51)
        for (i in 0 until N) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val r = rnd.nextFloat() * 10f
            baseX[i] = r * cos(a); baseZ[i] = r * sin(a) * .7f
            azimuth[i] = rnd.nextFloat() * 2f * PI.toFloat()
            // Presque toutes montent ; quelques-unes s'ouvrent en éventail.
            tilt[i] = (rnd.nextFloat().pow(1.6f) * 62f) * PI.toFloat() / 180f
            length[i] = 44f + rnd.nextFloat() * 74f * (1f - tilt[i] / 1.4f)
            thick[i] = 2f + rnd.nextFloat() * 2.8f
        }
    }

    override fun engrave(b: Burin) {
        // La salle sombre du musée, le faisceau du projecteur venu d'en haut à gauche.
        val room = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            intArrayOf(0xFF1A2230.toInt(), 0xFF0C1018.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(room, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 70f, 3.6f, .35f, 0xFF06080C.toInt())
        val beam = poly(96f, 46f, 136f, 46f, 262f, 262f, 104f, 262f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(110f, 50f, 180f, 262f,
            intArrayOf(0x55FFF4D8, 0x22FFF4D8, 0x08FFF4D8), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(beam, b.p)
        b.glow(180f, 262f, 90f, 0xFFFFF0D0.toInt(), 50)
        // Le socle : la colonne, le plateau tournant.
        val plinth = poly(124f, 268f, 236f, 268f, 236f, 340f, 124f, 340f)
        b.body(plinth, 0xFF2A2A30.toInt(), .45f, 0f, Fade(124f, 0f, 236f, 0f, 30, 200), outline = 1f, washAlpha = 255)
        b.body(rect(118f, 262f, 242f, 270f), 0xFF3A3A42.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(CX, CY + 10f, 48f, 10f), 0xFF4A4A54.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(CX, CY + 7f, 44f, 9f), 0xFF6A6A76.toInt(), .2f, 0f, Fade(0f, CY + 16f, 0f, CY - 2f, 200, 30), outline = .8f, washAlpha = 255)
        // La petite étiquette du musée, sans écriture : un carton et deux filets.
        b.body(poly(150f, 286f, 210f, 286f, 210f, 306f, 150f, 306f), 0xFFE8E0C8.toInt(), .1f, 0f, outline = .7f, washAlpha = 255)
        b.line(156f, 293f, 204f, 293f, .7f, withAlpha(Ink.SEPIA, 140))
        b.line(156f, 299f, 190f, 299f, .7f, withAlpha(Ink.SEPIA, 140))
    }

    override fun sprites(): List<Sprite> = listOf(
        // La gangue : un petit bloc de quartz et de roche d'où partent les aiguilles.
        Sprite(ROCK, RectF(CX - 28f, CY - 16f, CX + 28f, CY + 12f)) { b ->
            val rock = Path().apply {
                moveTo(CX - 26f, CY + 6f); cubicTo(CX - 28f, CY - 6f, CX - 16f, CY - 14f, CX - 4f, CY - 12f)
                cubicTo(CX + 8f, CY - 16f, CX + 24f, CY - 10f, CX + 26f, CY + 2f)
                cubicTo(CX + 27f, CY + 9f, CX + 12f, CY + 11f, CX, CY + 10f)
                cubicTo(CX - 12f, CY + 11f, CX - 24f, CY + 11f, CX - 26f, CY + 6f); close()
            }
            b.body(rock, 0xFF8A7A6A.toInt(), .45f, 30f, Fade(CX + 20f, CY - 10f, CX - 20f, CY + 10f, 40, 220), outline = 1f, washAlpha = 255)
            b.stipple(rock, 90, .7f, withAlpha(0xFFE8E0D0.toInt(), 200), 5)
            b.body(poly(CX - 14f, CY - 8f, CX - 6f, CY - 13f, CX + 2f, CY - 9f, CX - 4f, CY - 3f), 0xFFE8E4DC.toInt(), .1f, 0f, outline = .6f, washAlpha = 230)
            b.body(poly(CX + 8f, CY - 9f, CX + 16f, CY - 12f, CX + 20f, CY - 5f, CX + 12f, CY - 2f), 0xFFD8D4CC.toInt(), .1f, 0f, outline = .6f, washAlpha = 220)
        }
    )

    // ───────────── animation ─────────────

    private val sx0 = FloatArray(N); private val sy0 = FloatArray(N)
    private val sx1 = FloatArray(N); private val sy1 = FloatArray(N)
    private val depth = FloatArray(N); private val shine = FloatArray(N)
    private val order = IntArray(N) { it }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val turn = t * SPIN
        val cw = cos(turn); val sw = sin(turn)
        // Projette chaque aiguille : rotation autour de l'axe du socle, vue un peu plongeante.
        for (i in 0 until N) {
            val dx = sin(tilt[i]) * cos(azimuth[i]); val dy = cos(tilt[i]); val dz = sin(tilt[i]) * sin(azimuth[i])
            val x0 = baseX[i]; val z0 = baseZ[i]
            val x1 = x0 + dx * length[i]; val y1 = dy * length[i]; val z1 = z0 + dz * length[i]
            val rx0 = x0 * cw + z0 * sw; val rz0 = -x0 * sw + z0 * cw
            val rx1 = x1 * cw + z1 * sw; val rz1 = -x1 * sw + z1 * cw
            sx0[i] = CX + rx0; sy0[i] = CY - 4f + rz0 * VIEW
            sx1[i] = CX + rx1; sy1[i] = CY - 4f - y1 * LIFT + rz1 * VIEW
            depth[i] = (rz0 + rz1) / 2f
            // L'aiguille s'allume quand sa face se tourne vers le projecteur.
            val face = cos(azimuth[i] - turn - LIGHT)
            shine[i] = .3f + .7f * (if (face > 0f) face.pow(10) else 0f)
        }
        // Tri par profondeur (insertion : l'ordre change peu d'une image à l'autre).
        for (i in 1 until N) {
            val k = order[i]; var j = i - 1
            while (j >= 0 && depth[order[j]] > depth[k]) { order[j + 1] = order[j]; j-- }
            order[j + 1] = k
        }
        // Les aiguilles de derrière, la gangue, puis celles de devant.
        var i = 0
        while (i < N && depth[order[i]] < 0f) { needle(c, order[i]); i++ }
        s.draw(c, ROCK)
        while (i < N) { needle(c, order[i]); i++ }
        // Le plateau tourne : ses repères glissent sur le bord.
        ink.color = withAlpha(0xFFB8B8C8.toInt(), 160); ink.strokeWidth = 1.2f
        for (k in 0 until 8) {
            val a = turn + k * PI.toFloat() / 4f
            if (sin(a) < 0f) continue
            c.drawLine(CX + 44f * cos(a), CY + 7f + 9f * sin(a), CX + 40f * cos(a), CY + 7f + 8f * sin(a), ink)
        }
        // La poussière qui flotte dans le faisceau.
        for (k in 0 until DUST) {
            val u = (hash(k, 1) + t * (.012f + .01f * hash(k, 2))) % 1f
            val y = 60f + u * 200f
            val x = 110f + u * 70f + (hash(k, 3) - .3f) * (30f + u * 90f) + 4f * sin(t * .6f + k)
            val tw = .5f + .5f * sin(t * (1.4f + hash(k, 4)) + k * 2f)
            // Elle naît et s'éteint aux deux bouts du faisceau : le retour en haut ne se voit pas.
            fill.color = withAlpha(0xFFFFF4D8.toInt(), (140 * tw * sin(u * PI.toFloat())).toInt())
            c.drawCircle(x, y, .7f + .5f * hash(k, 5), fill)
        }
    }

    /** Une aiguille projetée : contour, corps d'acier, arête claire, et l'étoile à la pointe quand elle brille. */
    private fun needle(c: Canvas, i: Int) {
        val w = thick[i]
        ink.color = 0xFF1A1C20.toInt(); ink.strokeWidth = w + 1.2f
        c.drawLine(sx0[i], sy0[i], sx1[i], sy1[i], ink)
        ink.color = mix(0xFF4A5058.toInt(), 0xFFE8ECF0.toInt(), shine[i]); ink.strokeWidth = w
        c.drawLine(sx0[i], sy0[i], sx1[i], sy1[i], ink)
        val l = hypot(sx1[i] - sx0[i], sy1[i] - sy0[i]).coerceAtLeast(.01f)
        val nx = -(sy1[i] - sy0[i]) / l * w * .25f; val ny = (sx1[i] - sx0[i]) / l * w * .25f
        ink.color = withAlpha(0xFFFFFFFF.toInt(), (60 + 190 * (shine[i] - .3f) / .7f).toInt()); ink.strokeWidth = w * .3f
        c.drawLine(sx0[i] + nx, sy0[i] + ny, sx1[i] + nx, sy1[i] + ny, ink)
        if (shine[i] > .85f) {
            val g = (shine[i] - .85f) / .15f
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (230 * g).toInt()); ink.strokeWidth = .8f
            val r = 6f * g
            c.drawLine(sx1[i] - r, sy1[i], sx1[i] + r, sy1[i], ink); c.drawLine(sx1[i], sy1[i] - r, sx1[i], sy1[i] + r, ink)
        }
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * f).toInt()
        val g = ((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * f).toInt()
        val bl = (a and 255) + (((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val N = 44
        /** Le pied de la gerbe sur le plateau ; l'écrasement de la vue plongeante. */
        const val CX = 180f
        const val CY = 250f
        const val VIEW = .26f
        const val LIFT = .97f
        /** La vitesse du plateau (rad/s), la direction du projecteur. */
        const val SPIN = .32f
        const val LIGHT = 2.4f
        const val DUST = 22
        const val ROCK = 1
    }
}
