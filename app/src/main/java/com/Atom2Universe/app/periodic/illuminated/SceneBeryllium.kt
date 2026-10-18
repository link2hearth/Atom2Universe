package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Béryllium — le télescope spatial. Léger et indéformable dans le grand froid de l'espace, le
 * béryllium fait les dix-huit segments du miroir, couverts d'une pellicule d'or. Le miroir se
 * dresse sur son pare-soleil à cinq voiles ; une lueur glisse sur l'or, les étoiles scintillent
 * et une galaxie lointaine tourne lentement.
 */
internal class SceneBeryllium : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_space_telescope
    override val noteRes = R.string.card_note_space_telescope
    override val explainRes = R.string.card_explain_space_telescope

    /** Les centres des dix-huit segments : deux couronnes d'hexagones autour du trou central. */
    private val hexes = ArrayList<FloatArray>()
    private val mirrorClip = Path()

    /** Les étoiles qui scintillent : x, y, vitesse, phase. */
    private val stars = FloatArray(STAR_COUNT * 4)

    init {
        for (q in -2..2) for (r in -2..2) {
            val d = (abs(q) + abs(r) + abs(q + r)) / 2
            if (d == 0 || d > 2) continue
            hexes += floatArrayOf(MX + SPACING * 1.5f * q, MY + SPACING * SQRT3 * (r + q / 2f))
        }
        for (h in hexes) mirrorClip.op(hex(h[0], h[1], SPACING + .5f), Path.Op.UNION)
        // Des étoiles loin du miroir et au-dessus du pare-soleil.
        val rnd = Random(23)
        var n = 0
        while (n < STAR_COUNT) {
            val x = 52f + rnd.nextFloat() * 256f; val y = 66f + rnd.nextFloat() * 170f
            val dx = (x - MX) / 78f; val dy = (y - MY + 20f) / 92f
            if (dx * dx + dy * dy < 1f || y > 236f - (x - 46f) * .08f) continue
            stars[n * 4] = x; stars[n * 4 + 1] = y
            stars[n * 4 + 2] = 1.2f + rnd.nextFloat() * 2.4f; stars[n * 4 + 3] = rnd.nextFloat() * 6.3f
            n++
        }
    }

    override fun engrave(b: Burin) {
        // Les lueurs d'une nébuleuse, et une petite galaxie vue de profil.
        b.glow(252f, 104f, 130f, 0xFF7A4AB0.toInt(), 80)
        b.glow(96f, 150f, 110f, 0xFF3A6AB0.toInt(), 60)
        b.glow(84f, 92f, 12f, 0xFFF2E6D0.toInt(), 150)
        b.c.save(); b.c.rotate(-24f, 84f, 92f)
        b.fill(ellipse(84f, 92f, 11f, 1.6f), withAlpha(0xFFE8E0F0.toInt(), 170))
        b.c.restore()
        // Le Soleil est sous le pare-soleil : sa lueur en déborde, en bas à gauche.
        b.glow(56f, 316f, 120f, 0xFFFFE2A8.toInt(), 110)
        sunshield(b)
        // La tour qui porte le miroir.
        val tower = rect(176f, 196f, 186f, 262f)
        b.body(tower, 0xFF2A2A32.toInt(), .3f, 90f, outline = .8f, washAlpha = 255)
        mirror(b)
        secondary(b)
    }

    /** Cinq voiles superposées, argentées et violacées, vues d'au-dessus. */
    private fun sunshield(b: Burin) {
        for (k in 0 until 5) {
            val dy = (4 - k) * 4.2f
            val shield = poly(48f, 268f + dy, 178f, 310f + dy, 312f, 256f + dy, 200f, 226f + dy)
            val tint = if (k == 4) 0xFFDCD4E8.toInt() else 0xFFA89CBC.toInt()
            b.wash(shield, tint, 255)
            b.hatch(shield, 18f, 2.2f, .4f, 0xFF3A2E50.toInt(), Fade(0f, 300f + dy, 0f, 240f + dy, if (k == 4) 150 else 220, 30))
            b.stroke(shield, .8f, 0xFF2A2238.toInt())
        }
        val top = poly(48f, 268f, 178f, 310f, 312f, 256f, 200f, 226f)
        b.washGradient(top, 0xFFFFFFFF.toInt(), 120f, 236f, 150, 160f, 290f, 0)
        // Les coutures et les mâts du pare-soleil.
        val seam = b.pen(withAlpha(0xFF6A5E80.toInt(), 160), .45f)
        for (k in 1..4) {
            val u = k / 5f
            b.c.drawLine(48f + (200f - 48f) * u, 268f + (226f - 268f) * u, 178f + (312f - 178f) * u, 310f + (256f - 310f) * u, seam)
        }
        b.line(48f, 268f, 312f, 256f, .6f, withAlpha(0xFF4A4060.toInt(), 160))
    }

    private fun mirror(b: Burin) {
        b.c.save(); b.c.scale(1f, TILT, MX, MY)
        // Le dos du miroir, sombre, un peu plus grand que les segments.
        for (h in hexes) b.fill(hex(h[0], h[1], SPACING + 1.8f), 0xFF14141B.toInt())
        b.fill(hex(MX, MY, SPACING + 1.8f), 0xFF14141B.toInt())
        for (h in hexes) {
            val seg = hex(h[0], h[1], SPACING - 1.3f)
            b.gold(seg, Fade(h[0] - 12f, h[1] - 14f, h[0] + 12f, h[1] + 14f))
            b.hatch(seg, 50f, 2.4f, .35f, Gilding.DEEP, Fade(h[0] + 12f, h[1] + 12f, h[0], h[1], 200, 0))
            b.stroke(seg, .7f)
        }
        b.washGradient(mirrorClip, 0xFFFFFFFF.toInt(), MX - 60f, MY - 60f, 70, MX + 20f, MY + 20f, 0)
        // Au centre, le trou par où la lumière rejoint les instruments.
        b.fill(hex(MX, MY, SPACING - 1.3f), 0xFF24242C.toInt())
        b.c.drawCircle(MX, MY, 5f, b.pen(0xFF08080C.toInt()))
        b.c.drawCircle(MX, MY, 5f, b.pen(0xFF6A6A78.toInt(), .8f))
        b.c.restore()
    }

    /** Le petit miroir, tenu devant le grand par trois bras. */
    private fun secondary(b: Burin) {
        val top = MY - 66f * TILT
        for ((x, y) in listOf(MX to top, MX - 54f to MY + 44f * TILT, MX + 54f to MY + 44f * TILT)) {
            b.line(x, y, MX, SEC_Y, 2.6f, Ink.SEPIA)
            b.line(x, y, MX, SEC_Y, 1.2f, 0xFF50505E.toInt())
        }
        b.goldCircle(MX, SEC_Y, 6.4f)
        b.c.drawCircle(MX, SEC_Y, 7f, b.pen(Ink.SEPIA, 1.2f))
    }

    private fun hex(cx: Float, cy: Float, r: Float): Path = Path().apply {
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            val x = cx + r * cos(a); val y = cy + r * sin(a)
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(GALAXY, RectF(-28f, -28f, 28f, 28f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 28f,
                intArrayOf(0xDDFFF2D8.toInt(), 0x66C8B8FF, 0x00C8B8FF), floatArrayOf(0f, .22f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 28f, b.p)
            // Deux bras en spirale, faits d'étoiles qui pâlissent vers le bord.
            val dot = b.pen(0xFFDDE6FF.toInt())
            val rnd = Random(4)
            for (arm in 0 until 2) for (i in 0 until 80) {
                val th = i * .08f
                val r = 3f + th * 3.7f
                val a = th + arm * PI.toFloat() + (rnd.nextFloat() - .5f) * .25f
                dot.alpha = (230 * (1f - i / 90f)).toInt()
                b.c.drawCircle(cos(a) * r, sin(a) * r, .5f + .6f * (1f - i / 80f), dot)
            }
        }
    )

    // ───────────── animation ─────────────

    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-20f, 0f, 20f, 0f, intArrayOf(0x00FFF6D0, 0xAAFFF6D0.toInt(), 0x00FFF6D0), null, Shader.TileMode.CLAMP)
    }
    private val twinkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = .7f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La galaxie tourne dans son plan, vu de biais.
        c.save(); c.translate(GX, GY); c.scale(1f, .45f); c.rotate(t * 4f)
        s.draw(c, GALAXY)
        c.restore()
        // Les étoiles scintillent.
        for (k in 0 until STAR_COUNT) {
            val x = stars[k * 4]; val y = stars[k * 4 + 1]
            val tw = .5f + .5f * sin(t * stars[k * 4 + 2] + stars[k * 4 + 3])
            val r = 1.2f + 3.2f * tw * tw
            twinkle.color = withAlpha(0xFFFFF6E0.toInt(), (60 + 190 * tw).toInt())
            c.drawLine(x - r, y, x + r, y, twinkle); c.drawLine(x, y - r, x, y + r, twinkle)
            dot.color = withAlpha(0xFFFFFFFF.toInt(), (120 + 135 * tw).toInt())
            c.drawCircle(x, y, .9f, dot)
        }
        // Une lueur glisse en biais sur les segments d'or.
        val g = (t / GLINT_PERIOD) % 1f
        sheen.alpha = (255 * sin(g * PI.toFloat())).toInt()
        c.save(); c.scale(1f, TILT, MX, MY); c.clipPath(mirrorClip)
        c.translate(MX - 100f + g * 200f, MY); c.rotate(24f)
        c.drawRect(-20f, -120f, 20f, 120f, sheen)
        c.restore()
        // Le petit miroir renvoie un éclat quand la lueur passe au milieu.
        val flash = (1f - abs(g - .5f) * 6f).coerceIn(0f, 1f)
        if (flash > 0f) {
            twinkle.color = withAlpha(0xFFFFF6D0.toInt(), (230 * flash).toInt())
            val r = 9f * flash
            c.drawLine(MX - r, SEC_Y, MX + r, SEC_Y, twinkle); c.drawLine(MX, SEC_Y - r, MX, SEC_Y + r, twinkle)
        }
    }

    private companion object {
        const val MX = 180f
        const val MY = 162f
        /** Le miroir regarde un peu vers le haut : on le voit écrasé. */
        const val TILT = .84f
        const val SPACING = 15f
        const val SQRT3 = 1.7320508f
        const val SEC_Y = 88f
        const val GX = 266f
        const val GY = 104f
        const val GALAXY = 1
        const val STAR_COUNT = 14
        const val GLINT_PERIOD = 7f
    }
}
