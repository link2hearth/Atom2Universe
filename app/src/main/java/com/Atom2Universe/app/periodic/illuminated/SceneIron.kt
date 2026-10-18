package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fer — le martinet de forge, comme dans les planches de l'Encyclopédie : une came soulève le
 * manche, la tête retombe sur la barre rougie, les étincelles jaillissent. Au mur, des fers à
 * cheval ; à gauche, le foyer et sa hotte.
 */
internal class SceneIron : EngravedScene() {

    override val nameRes = R.string.card_scene_trip_hammer
    override val noteRes = R.string.card_note_trip_hammer
    override val explainRes = R.string.card_explain_trip_hammer

    override val sky = Sky.NONE

    override fun engrave(b: Burin) {
        wall(b)
        floor(b)
        hearth(b)
        horseshoes(b)
        anvil(b)
        post(b)
    }

    private fun wall(b: Burin) {
        val wall = rect(40f, 50f, 320f, FLOOR)
        b.wash(wall, STONE, 55)
        // Assises de pierre décalées : un joint, et une ombre légère sous chaque pierre.
        val joints = b.pen(withAlpha(Ink.SEPIA, 120), .55f)
        val shade = Paint(b.pen(withAlpha(Ink.SEPIA, 70), .4f))
        val rnd = Random(9)
        var row = 0
        var y = FLOOR - 18f
        while (y > 50f) {
            b.c.drawLine(40f, y, 320f, y, joints)
            var x = 40f + (row % 2) * 17f
            while (x < 320f) {
                b.c.drawLine(x, y, x, y + 18f, joints)
                val n = 1 + rnd.nextInt(2)
                for (k in 0 until n) b.c.drawLine(x + 4f, y + 15.5f - k * 1.7f, x + 30f - k * 7f, y + 15.5f - k * 1.7f, shade)
                if (rnd.nextFloat() < .25f) b.c.drawLine(x + 6f + rnd.nextFloat() * 18f, y + 4f, x + 10f + rnd.nextFloat() * 18f, y + 7f, shade)
                x += 34f
            }
            y -= 18f; row++
        }
        // L'ombre monte du sol et descend du plafond ; la lueur du foyer éclaire le reste.
        b.hatchRect(RectF(40f, 220f, 320f, FLOOR), 0f, 2.3f, .45f, Ink.SEPIA, Fade(0f, FLOOR, 0f, 220f, 150, 0))
        b.hatchRect(RectF(40f, 50f, 320f, 120f), 0f, 2.3f, .45f, Ink.SEPIA, Fade(0f, 56f, 0f, 120f, 170, 0))
        b.hatchRect(RectF(270f, 50f, 320f, FLOOR), 90f, 2.4f, .45f, Ink.SEPIA, Fade(320f, 0f, 270f, 0f, 150, 0))
    }

    private fun floor(b: Burin) {
        val floor = rect(40f, FLOOR, 320f, 350f)
        b.wash(floor, 0xFF8C6A48.toInt(), 90)
        b.hatch(floor, 0f, 2.4f, .5f, Ink.SEPIA, Fade(0f, FLOOR, 0f, 340f, 120, 230))
        b.stipple(floor, 220, .5f, seed = 4)
        b.line(40f, FLOOR, 320f, FLOOR, 1.1f)
    }

    private fun hearth(b: Burin) {
        // La hotte : un tronc de pyramide de plâtre, l'ombre à droite.
        val hood = poly(50f, 132f, 134f, 132f, 114f, 64f, 70f, 64f)
        b.body(hood, 0xFFC7B595.toInt(), .35f, 80f, Fade(134f, 0f, 60f, 0f, 255, 30))
        b.fill(rect(48f, 128f, 136f, 136f), 0xFFAD9A79.toInt())
        b.stroke(rect(48f, 128f, 136f, 136f), .9f)
        // Le dessous de la hotte, noir de suie.
        val mouth = poly(54f, 136f, 130f, 136f, 124f, 150f, 60f, 150f)
        b.body(mouth, 0xFF3A2A20.toInt(), 1f, 0f, outline = .6f)
        // Le massif de briques.
        val base = rect(52f, 216f, 130f, FLOOR)
        b.body(base, BRICK, .25f, 45f, washAlpha = 150, outline = 0f)
        val joints = b.pen(withAlpha(Ink.SEPIA, 170), .5f)
        var y = 216f; var row = 0
        while (y < FLOOR) {
            b.c.drawLine(52f, y, 130f, y, joints)
            var x = 52f + (row % 2) * 8f
            while (x < 130f) { b.c.drawLine(x, y, x, (y + 8f).coerceAtMost(FLOOR), joints); x += 16f }
            y += 8f; row++
        }
        b.hatchRect(RectF(108f, 216f, 130f, FLOOR), 90f, 1.6f, .5f)
        b.stroke(base, 1.1f)
        val slab = rect(46f, 206f, 136f, 217f)
        b.body(slab, STONE, .3f, 0f, Fade(0f, 217f, 0f, 206f, 255, 0))
        // Les braises : un tas de charbon.
        val rnd = Random(5)
        for (i in 0 until 26) {
            val x = 60f + rnd.nextFloat() * 64f
            val y = 205f - rnd.nextFloat() * 9f * (1f - kotlin.math.abs(x - 92f) / 40f)
            val r = 2.2f + rnd.nextFloat() * 2.4f
            b.c.drawCircle(x, y, r, b.pen(0xFF2A1E18.toInt()))
            b.c.drawCircle(x - r * .3f, y - r * .3f, r * .45f, b.pen(withAlpha(EMBER, 160)))
        }
        // Le soufflet, accroché au flanc du foyer.
        val bellows = Path().apply {
            moveTo(46f, 236f); cubicTo(20f, 222f, 22f, 270f, 46f, 262f); close()
        }
        b.body(bellows, 0xFF7A5232.toInt(), .45f, 60f)
    }

    private fun horseshoes(b: Burin) {
        // Une barre de clous, trois fers à cheval accrochés, ouverture en haut.
        b.body(rect(150f, 88f, 246f, 93f), WOOD, .2f, 0f)
        for ((i, x) in floatArrayOf(166f, 198f, 230f).withIndex()) {
            val y = 112f + (i % 2) * 4f
            val shoe = RectF(x - 11f, y - 12f, x + 11f, y + 12f)
            b.c.drawArc(shoe, -20f, 220f, false, b.pen(Ink.SEPIA, 7f))
            b.c.drawArc(shoe, -20f, 220f, false, b.pen(IRON, 5f))
            b.c.drawArc(RectF(x - 9f, y - 10f, x + 9f, y + 10f), 40f, 100f, false, b.pen(withAlpha(0xFFFFFFFF.toInt(), 150), .8f))
            for (k in 0..5) {
                val a = Math.toRadians((-5.0 + k * 38.0)).toFloat()
                b.c.drawCircle(x + cos(a) * 11f, y + sin(a) * 12f, .8f, b.pen(Ink.SEPIA))
            }
            b.c.drawCircle(x, 93f, 1.3f, b.pen(Ink.SEPIA))
            b.line(x, 93f, x, y - 12f, .6f)
        }
    }

    private fun anvil(b: Burin) {
        // Le billot de bois.
        val block = rect(160f, 276f, 218f, FLOOR)
        b.body(block, WOOD, .35f, 90f, Fade(218f, 0f, 160f, 0f, 255, 60))
        for (k in 0..2) b.line(166f + k * 18f, 279f, 170f + k * 18f, 290f, .5f)
        // L'enclume : table, bigorne, taille, pieds.
        val anvil = Path().apply {
            moveTo(122f, 247f)                       // pointe de la bigorne
            cubicTo(134f, 244f, 142f, 243f, 150f, 243f)
            lineTo(226f, 243f); lineTo(226f, 252f); lineTo(214f, 254f)
            cubicTo(208f, 258f, 206f, 264f, 210f, 268f)
            lineTo(222f, 276f); lineTo(156f, 276f); lineTo(168f, 268f)
            cubicTo(172f, 264f, 170f, 258f, 162f, 254f)
            cubicTo(150f, 253f, 136f, 252f, 122f, 247f)
            close()
        }
        b.body(anvil, IRON, 0f, outline = 0f, washAlpha = 190)
        b.hatch(anvil, 0f, 1.9f, .55f, Ink.SEPIA, Fade(0f, 244f, 0f, 276f, 60, 255))
        b.hatch(anvil, 80f, 2.6f, .45f, Ink.SEPIA, Fade(226f, 0f, 150f, 0f, 220, 0))
        // La table, éclairée par la barre rouge.
        b.fill(rect(150f, 243f, 226f, 246f), withAlpha(0xFFE7E1D2.toInt(), 220))
        b.stroke(anvil, 1.2f)
        b.line(150f, 246f, 226f, 246f, .6f)
        // La barre chauffée au rouge.
        val bar = rect(176f, 238.5f, 214f, 243f)
        b.fill(bar, 0xFFE8622A.toInt())
        b.fill(rect(180f, 239.5f, 206f, 241f), 0xFFFFC06A.toInt())
        b.stroke(bar, .6f)
    }

    private fun post(b: Burin) {
        // Le poteau du martinet ; le manche mobile passe devant.
        val post = rect(255f, 166f, 269f, FLOOR)
        b.body(post, WOOD, .3f, 90f, Fade(269f, 0f, 255f, 0f, 255, 40))
        for (k in 0..4) b.line(258f + k * 2.5f, 175f + k * 9f, 258f + k * 2.5f, 196f + k * 15f, .4f)
        val cap = rect(246f, 160f, 278f, 168f)
        b.body(cap, WOOD, .4f, 0f)
        b.body(rect(253f, 204f, 271f, 220f), IRON, .5f, 0f)
        // L'arbre à cames sort du cadre : son palier.
        b.body(rect(282f, 258f, 312f, FLOOR), STONE, .3f, 70f)
    }

    override fun sprites() = listOf(
        Sprite(HELVE, RectF(168f, 196f, 312f, 244f)) { b -> helve(b) },
        Sprite(CAM, RectF(268f, 220f, 324f, 276f)) { b -> cam(b) }
    )

    private fun helve(b: Burin) {
        val beam = Path().apply {
            moveTo(176f, 206f); lineTo(306f, 205f); lineTo(306f, 215f); lineTo(176f, 217f); close()
        }
        b.body(beam, WOOD, .3f, 0f, Fade(0f, 217f, 0f, 205f, 255, 30))
        for (k in 0..3) b.line(190f + k * 28f, 209f, 210f + k * 28f, 210f, .4f)
        for (x in floatArrayOf(198f, 286f)) b.body(rect(x, 203.5f, x + 6f, 218.5f), IRON, .5f, 0f, outline = .7f)
        // La tête de fer, qui frappe.
        val head = poly(174f, 203f, 200f, 203f, 199f, 238f, 175f, 238f)
        b.body(head, IRON, .55f, 90f, Fade(200f, 0f, 174f, 0f, 255, 50), washAlpha = 200)
        b.line(175f, 236.5f, 199f, 236.5f, 1.4f)
    }

    private fun cam(b: Burin) {
        val cx = 296f; val cy = 248f
        for (i in 0 until 4) {
            val a = i * PI.toFloat() / 2
            val lobe = poly(
                cx + cos(a - .32f) * 8f, cy + sin(a - .32f) * 8f,
                cx + cos(a - .05f) * 25f, cy + sin(a - .05f) * 25f,
                cx + cos(a + .22f) * 22f, cy + sin(a + .22f) * 22f,
                cx + cos(a + .42f) * 8f, cy + sin(a + .42f) * 8f
            )
            b.body(lobe, WOOD, .35f, Math.toDegrees(a.toDouble()).toFloat(), outline = .8f)
        }
        b.c.drawCircle(cx, cy, 10f, b.pen(IRON))
        b.c.drawCircle(cx, cy, 10f, b.pen(Ink.SEPIA, 1f))
        b.c.drawCircle(cx, cy, 3f, b.pen(Ink.SEPIA))
    }

    // ───────────── animation ─────────────

    private val firePath = Path()
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(0x99FFB347.toInt(), 0x44FF8A2A, 0x00FF8A2A), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 1.1f }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Le cycle du martinet : la came lève lentement, la tête retombe d'un coup.
        val cycle = .9f
        val k = floor(t / cycle)
        val f = t / cycle - k
        val lift = when {
            f < .62f -> 13f * sin(f / .62f * PI.toFloat() / 2)
            f < .7f -> 13f * (1f - (f - .62f) / .08f)
            else -> 0f
        }
        val sinceHit = (f - .7f).let { if (it < 0) it + 1f else it } * cycle

        fire(c, t)
        // La lueur de la barre, ravivée à chaque coup.
        val heat = .55f + .45f * (1f - (sinceHit / .5f).coerceIn(0f, 1f))
        halo(c, 194f, 240f, 34f + 10f * heat, (170 * heat).toInt())

        s.draw(c, CAM, deg = (t / cycle) * 90f, px = 296f, py = 248f)
        s.draw(c, HELVE, deg = lift, px = 262f, py = 211f)
        c.drawCircle(262f, 211f, 3f, flamePaint.apply { shader = null; style = Paint.Style.FILL; color = Ink.SEPIA })

        if (sinceHit < .42f) sparks(c, sinceHit, k.toInt())
    }

    private fun halo(c: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        glowPaint.alpha = alpha.coerceIn(0, 255)
        c.save(); c.translate(x, y); c.scale(r, r)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
    }

    private fun fire(c: Canvas, t: Float) {
        halo(c, 92f, 192f, 66f + 6f * sin(t * 7f), 210)
        // Langues de feu : contour à l'encre, lavis orangé, cœur clair, une taille au milieu.
        for (i in 0 until 7) {
            val x = 64f + i * 9.5f + 2f * sin(i * 2.3f)
            val w = 5.5f + (i % 3) * 1.4f
            val h = 22f + 16f * (.5f + .5f * sin(t * 5.3f + i * 1.7f)) + 9f * sin(t * 9.1f + i * 2.9f).coerceAtLeast(0f) +
                (if (i in 2..4) 10f else 0f)
            val lean = 5f * sin(t * 2.6f + i * .8f)
            flame(c, x, 205f, w, h, lean, withAlpha(0xFFE8742A.toInt(), 215), withAlpha(Ink.SEPIA, 150))
            flame(c, x + lean * .15f, 205f, w * .5f, h * .58f, lean * .5f, withAlpha(0xFFFFE6A0.toInt(), 235), 0)
        }
        // Escarbilles qui montent vers la hotte.
        for (i in 0 until 9) {
            val life = ((t * .65f + i * .29f) % 1f)
            val x = 68f + (i * 23 % 52) + 7f * sin(t * 2f + i)
            val y = 196f - life * 58f
            sparkPaint.color = withAlpha(0xFFFFC36A.toInt(), (255 * (1f - life)).toInt())
            c.drawCircle(x, y, 1.1f, sparkPaint)
        }
    }

    private fun flame(c: Canvas, x: Float, base: Float, w: Float, h: Float, lean: Float, fill: Int, ink: Int) {
        firePath.reset()
        firePath.moveTo(x - w, base)
        firePath.cubicTo(x - w * 1.25f, base - h * .38f, x + lean * .4f - w * .45f, base - h * .62f, x + lean, base - h)
        firePath.cubicTo(x + lean * .45f + w * .2f, base - h * .58f, x + w * 1.15f, base - h * .4f, x + w, base)
        firePath.close()
        flamePaint.shader = null; flamePaint.style = Paint.Style.FILL; flamePaint.color = fill
        c.drawPath(firePath, flamePaint)
        if (ink != 0) {
            flamePaint.style = Paint.Style.STROKE; flamePaint.strokeWidth = .55f; flamePaint.color = ink
            c.drawPath(firePath, flamePaint)
        }
    }

    private fun sparks(c: Canvas, age: Float, blow: Int) {
        val rnd = Random(blow * 31 + 7)
        val fade = 1f - age / .42f
        for (i in 0 until 20) {
            val a = (-PI * .95 + rnd.nextFloat() * PI * .9).toFloat()   // vers le haut, en éventail
            val v = 80f + rnd.nextFloat() * 150f
            fun pos(tt: Float) = floatArrayOf(187f + cos(a) * v * tt, 239f + sin(a) * v * tt + 190f * tt * tt)
            val head = pos(age); val tail = pos((age - .06f).coerceAtLeast(0f))
            sparkPaint.strokeWidth = 1.4f
            sparkPaint.color = withAlpha(if (i % 3 == 0) 0xFFFFF6C8.toInt() else 0xFFFFA43C.toInt(), (255 * fade).toInt())
            c.drawLine(tail[0], tail[1], head[0], head[1], sparkPaint)
            c.drawCircle(head[0], head[1], 1.1f, sparkPaint)
        }
        if (age < .12f) halo(c, 187f, 238f, 26f, (255 * (1f - age / .12f)).toInt())
    }

    private companion object {
        const val FLOOR = 292f
        const val HELVE = 1
        const val CAM = 2
        const val STONE = 0xFFB8A684.toInt()
        const val BRICK = 0xFFA4553F.toInt()
        const val WOOD = 0xFFB07A45.toInt()
        const val IRON = 0xFF6F7F8F.toInt()
        const val EMBER = 0xFFFF7A30.toInt()
    }
}
