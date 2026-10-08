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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gallium — la cuillère qui fond. Le gallium fond à 30 °C : une cuillère en gallium plongée dans
 * un thé chaud s'y dissout en gouttes de métal liquide. Dans une tasse de verre, on voit la
 * cuillère descendre, fondre par le bout, ses gouttes argentées tomber au fond ; elle ressort
 * en moignon, s'en va, et une neuve redescend.
 */
internal class SceneGallium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_melting_spoon
    override val noteRes = R.string.card_note_melting_spoon
    override val explainRes = R.string.card_explain_melting_spoon

    private val dirX = sin(ANGLE * PI.toFloat() / 180f)
    private val dirY = -cos(ANGLE * PI.toFloat() / 180f)

    /** L'intérieur de la tasse sous la surface du thé. */
    private val tea = Path().apply {
        addOval(MX - 43.5f, SURF - 8.5f, MX + 43.5f, SURF + 8.5f, Path.Direction.CW)
        op(poly(MX - 43.5f, SURF, MX + 43.5f, SURF, MX + 39f, BOT - 2f, MX - 39f, BOT - 2f), Path.Op.UNION)
        op(ellipse(MX, BOT - 2f, 39f, 7.5f), Path.Op.UNION)
    }
    private val walls = poly(MX - 44f, RIM, MX + 44f, RIM, MX + 40f, BOT, MX - 40f, BOT)

    override fun engrave(b: Burin) {
        // Le mur crème, la lumière qui vient de la gauche, la table.
        val wall = rect(40f, 50f, 320f, TABLE)
        b.wash(wall, 0xFFDCCFB4.toInt(), 255)
        b.glow(70f, 90f, 220f, 0xFFFFF4DC.toInt(), 190)
        b.washGradient(wall, 0xFF3A2C1C.toInt(), 0f, 120f, 0, 0f, TABLE, 140)
        b.hatchRect(RectF(40f, 50f, 320f, TABLE), 90f, 3f, .4f, Ink.BROWN, Fade(0f, 60f, 0f, TABLE, 40, 150))
        val table = rect(40f, TABLE, 320f, 340f)
        b.wash(table, 0xFF8A5A30.toInt(), 255)
        b.hatchRect(RectF(40f, TABLE, 320f, 340f), 0f, 2.2f, .45f, Ink.SEPIA, Fade(0f, TABLE, 0f, 330f, 80, 220))
        b.line(40f, TABLE, 320f, TABLE, 1.1f, withAlpha(0xFFFFE0B8.toInt(), 180))
        teapot(b)
        // L'ombre de la tasse, son anse de verre, sa paroi du fond.
        b.fill(ellipse(MX + 10f, BOT + 2f, 52f, 7f), withAlpha(0xFF1A0E06.toInt(), 90))
        val handle = Path().apply {
            moveTo(MX - 42f, 212f); cubicTo(MX - 76f, 210f, MX - 78f, 268f, MX - 40f, 266f)
        }
        b.stroke(handle, 7f, withAlpha(0xFFE8F0F4.toInt(), 120))
        b.stroke(handle, 1f, withAlpha(Ink.SEPIA, 160))
        b.wash(walls, 0xFFFFFFFF.toInt(), 40)
        b.c.drawArc(MX - 44f, RIM - 9f, MX + 44f, RIM + 9f, 180f, 180f, false, b.pen(withAlpha(Ink.SEPIA, 170), 1f))
        // Le fond épais du verre, et la flaque de gallium des cuillères précédentes.
        b.body(ellipse(MX, BOT - 2f, 39f, 7.5f), withAlpha(0xFFE8F0F4.toInt(), 255), 0f, outline = .7f, washAlpha = 90)
        puddle(b)
    }

    /** La théière brune à droite, le bec tourné vers la tasse. */
    private fun teapot(b: Burin) {
        val glaze = 0xFF6A3A1E.toInt()
        val spout = Path().apply {
            moveTo(262f, 262f); cubicTo(248f, 258f, 244f, 240f, 236f, 228f); lineTo(242f, 226f)
            cubicTo(250f, 236f, 256f, 246f, 268f, 248f); close()
        }
        b.body(spout, glaze, .4f, 30f, outline = .9f, washAlpha = 255)
        b.stroke(Path().apply { moveTo(306f, 236f); cubicTo(330f, 236f, 330f, 272f, 304f, 272f) }, 6f, glaze)
        b.stroke(Path().apply { moveTo(306f, 236f); cubicTo(330f, 236f, 330f, 272f, 304f, 272f) }, 1f)
        val pot = ellipse(284f, 256f, 30f, 28f)
        b.body(pot, glaze, .45f, 40f, Fade(304f, 280f, 266f, 236f, 230, 20), outline = 1f, washAlpha = 255)
        b.glow(272f, 242f, 14f, 0xFFFFE8D0.toInt(), 170)
        b.body(Path().apply { addOval(266f, 224f, 302f, 236f, Path.Direction.CW) }, glaze, .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(284f, 222f, 4f, 3.4f), glaze, .2f, 0f, outline = .8f, washAlpha = 255)
        b.fill(ellipse(284f, 285f, 34f, 4f), withAlpha(0xFF1A0E06.toInt(), 80))
    }

    private fun puddle(b: Burin) {
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, PUDDLE_Y - 4f, 0f, PUDDLE_Y + 4f,
            intArrayOf(0xFFF4F6F8.toInt(), 0xFF9AA2AA.toInt(), 0xFF5A626A.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawOval(PX - 24f, PUDDLE_Y - 4f, PX + 24f, PUDDLE_Y + 4f, b.p)
        b.c.drawOval(PX - 24f, PUDDLE_Y - 4f, PX + 24f, PUDDLE_Y + 4f, b.pen(Ink.SEPIA, .6f))
    }

    override fun sprites(): List<Sprite> = listOf(
        // La cuillère, le bout en (0, 0), le manche vers le haut.
        Sprite(SPOON, RectF(-13f, -LENGTH - 2f, 13f, 2f)) { b ->
            val bowl = ellipse(0f, -BOWL, 11f, BOWL)
            val handle = Path().apply {
                moveTo(-1.6f, -2f * BOWL + 2f); lineTo(-1.8f, -60f)
                cubicTo(-2.6f, -120f, -4.6f, -180f, -4.6f, -LENGTH + 8f)
                quadTo(-4.6f, -LENGTH, 0f, -LENGTH); quadTo(4.6f, -LENGTH, 4.6f, -LENGTH + 8f)
                cubicTo(4.6f, -180f, 2.6f, -120f, 1.8f, -60f); lineTo(1.6f, -2f * BOWL + 2f); close()
            }
            val spoon = Path().apply { op(bowl, handle, Path.Op.UNION) }
            b.pen(0xFF000000.toInt()).shader = LinearGradient(-11f, 0f, 11f, 0f,
                intArrayOf(0xFF8A929A.toInt(), 0xFFF4F6F8.toInt(), 0xFFB8C0C8.toInt(), 0xFF5A626A.toInt()), floatArrayOf(0f, .35f, .6f, 1f), Shader.TileMode.CLAMP)
            b.c.drawPath(spoon, b.p)
            b.c.drawOval(-6f, -BOWL - 12f, 1f, -BOWL + 6f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 150)))
            b.stroke(spoon, .8f)
        },
        // Le thé : ambre transparent, plus sombre au fond, sa surface plus claire.
        Sprite(TEA, RectF(MX - 45f, SURF - 10f, MX + 45f, BOT + 8f)) { b ->
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, SURF, 0f, BOT,
                intArrayOf(withAlpha(0xFFC07A2A.toInt(), 130), withAlpha(0xFF8A4A14.toInt(), 190)), null, Shader.TileMode.CLAMP)
            b.c.drawPath(tea, b.p)
            b.fill(ellipse(MX, SURF, 43.5f, 8.5f), withAlpha(0xFFD8964A.toInt(), 170))
            b.c.drawOval(MX - 43.5f, SURF - 8.5f, MX + 43.5f, SURF + 8.5f, b.pen(withAlpha(0xFF5A2A0A.toInt(), 160), .8f))
            b.c.drawArc(MX - 38f, SURF - 6f, MX + 38f, SURF + 6f, 200f, 70f, false, b.pen(withAlpha(0xFFFFF0D8.toInt(), 170), 1.2f))
        },
        // Le verre devant : le bord, les reflets des parois.
        Sprite(GLASS, RectF(MX - 46f, RIM - 11f, MX + 46f, BOT + 10f)) { b ->
            b.c.drawArc(MX - 44f, RIM - 9f, MX + 44f, RIM + 9f, 0f, 180f, false, b.pen(Ink.SEPIA, 1.1f))
            b.c.drawArc(MX - 42.6f, RIM - 7.6f, MX + 42.6f, RIM + 7.6f, 20f, 140f, false, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200), 1.4f))
            b.line(MX - 44f, RIM, MX - 40f, BOT, 1.1f)
            b.line(MX + 44f, RIM, MX + 40f, BOT, 1.1f)
            b.c.drawArc(MX - 40f, BOT - 8f, MX + 40f, BOT + 8f, 0f, 180f, false, b.pen(Ink.SEPIA, 1.1f))
            b.line(MX - 34f, RIM + 12f, MX - 31f, BOT - 12f, 3f, withAlpha(0xFFFFFFFF.toInt(), 130))
            b.line(MX - 27f, RIM + 16f, MX - 25f, BOT - 30f, 1f, withAlpha(0xFFFFFFFF.toInt(), 110))
            b.line(MX + 36f, RIM + 14f, MX + 33f, BOT - 16f, 1.6f, withAlpha(0xFFFFFFFF.toInt(), 90))
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val melt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8DEE4.toInt() }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val cycle = (t / CYCLE).toInt()
        val ph = t - cycle * CYCLE
        val lift = lift(ph)
        val m = melted(ph)
        val stir = if (ph in DOWN..UP) 3f * sin((ph - DOWN) * 4f) * smooth((ph - DOWN) / .5f) * smooth((UP - ph) / .5f) else 0f
        // La cuillère : levée ou baissée, tournée autour du bord de la tasse pour touiller,
        // et coupée là où elle a fondu ; un bourrelet de métal liquide sur la coupure.
        if (lift < LIFT_OUT) {
            c.save()
            c.rotate(stir, PIVOT_X, PIVOT_Y)
            c.translate(TIP_X, TIP_Y - lift)
            c.rotate(ANGLE)
            c.save(); c.clipRect(-14f, -LENGTH - 4f, 14f, -m)
            s.draw(c, SPOON)
            c.restore()
            if (m > 0f) {
                val w = widthAt(m)
                c.drawOval(-w, -m - 2.4f, w, -m + 2.6f, melt)
                ink.color = withAlpha(Ink.SEPIA, 200); ink.strokeWidth = .7f
                c.drawOval(-w, -m - 2.4f, w, -m + 2.6f, ink)
            }
            c.restore()
        }
        // Les gouttes de gallium qui tombent au fond, et la flaque qui frémit quand elles arrivent.
        var ripple = 0f
        for (k in 0 until DROPS) {
            val born = DROP0 + k * DROP_GAP
            val age = ph - born
            if (age < 0f) continue
            val f = meltFront(born)
            val x0 = f[0]; val y0 = f[1]
            val y: Float
            if (y0 < SURF) {
                // Née hors du thé : elle tombe vite, puis ralentit dans le liquide.
                val ta = sqrt(2f * (SURF - y0) / 700f)
                y = if (age < ta) y0 + 350f * age * age else SURF + 46f * (age - ta)
            } else {
                y = y0 + 46f * age
            }
            if (y > PUDDLE_Y - 2f) {
                val since = (y - PUDDLE_Y + 2f) / 46f
                if (since < .6f) ripple = maxOf(ripple, 1f - since / .6f)
                continue
            }
            val x = x0 + 2f * sin(age * 3f + k)
            fill.color = 0xFFE4E8EC.toInt(); c.drawCircle(x, y, 2.3f, fill)
            ink.color = withAlpha(0xFF3A4048.toInt(), 200); ink.strokeWidth = .6f; c.drawCircle(x, y, 2.3f, ink)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x - .7f, y - .8f, .7f, fill)
        }
        if (ripple > 0f) {
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (200 * ripple).toInt()); ink.strokeWidth = .8f
            val r = 8f + 16f * (1f - ripple)
            c.drawOval(PX - r, PUDDLE_Y - r * .18f, PX + r, PUDDLE_Y + r * .18f, ink)
        }
        val gx = PX - 12f + 10f * sin(t * .9f)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 200)
        c.drawOval(gx - 5f, PUDDLE_Y - 2.4f, gx + 5f, PUDDLE_Y - .8f, fill)
        // Le thé par-dessus la partie plongée, puis le verre.
        s.draw(c, TEA)
        s.draw(c, GLASS)
        // La vapeur du thé chaud.
        for (k in 0 until STEAM) {
            val life = ((t * .22f + k / STEAM.toFloat()) % 1f)
            val x = MX - 24f + 48f * hash(k, 1) + 10f * sin(life * 5f + k) + life * 8f
            val y = SURF - 14f - life * 90f
            val r = 4f + life * 10f
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (70 * (1f - life) * (life * 5f).coerceAtMost(1f)).toInt())
            c.drawCircle(x, y, r, fill)
        }
    }

    /**
     * De combien la cuillère est remontée à l'instant [ph] du cycle. Elle monte droit : le long de
     * son axe penché, le moignon sortirait de la tasse par le côté et ses gouttes tomberaient à côté.
     */
    private fun lift(ph: Float): Float = when {
        ph < DOWN -> LIFT_OUT * (1f - smooth(ph / DOWN))
        ph < UP -> 0f
        ph < HOLD -> STUMP * smooth((ph - UP) / (HOLD - UP))
        ph < HOLD + .4f -> STUMP
        ph < GONE -> STUMP + (LIFT_OUT - STUMP) * smooth((ph - HOLD - .4f) / (GONE - HOLD - .4f))
        else -> LIFT_OUT
    }

    /** Ce qui a fondu du bout de la cuillère : tant qu'elle trempe, puis plus rien ne bouge. */
    private fun melted(ph: Float): Float = when {
        ph < DOWN + .3f -> 0f
        ph < UP + .4f -> MELT * smooth((ph - DOWN - .3f) / (UP + .4f - DOWN - .3f))
        ph < GONE -> MELT
        else -> 0f
    }

    private val front = FloatArray(2)

    /** Où se trouve le bout fondu de la cuillère à l'instant [ph] (sans le touillage). */
    private fun meltFront(ph: Float): FloatArray {
        val m = melted(ph)
        front[0] = TIP_X + dirX * m
        front[1] = TIP_Y - lift(ph) + dirY * m
        return front
    }

    /** La demi-largeur de la cuillère à la distance [m] du bout. */
    private fun widthAt(m: Float): Float =
        if (m < 2f * BOWL) 11f * sqrt((1f - ((m - BOWL) / BOWL).let { it * it }).coerceAtLeast(.04f)) else 1.8f + (m - 60f).coerceAtLeast(0f) * .01f

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val TABLE = 270f
        /** La tasse : axe, bord, surface du thé, fond. */
        const val MX = 172f
        const val RIM = 188f
        const val SURF = 212f
        const val BOT = 300f
        const val PX = 170f
        const val PUDDLE_Y = 292f
        /** La cuillère : bout quand elle est plongée, inclinaison, longueur, cuilleron. */
        const val TIP_X = 168f
        const val TIP_Y = 280f
        const val ANGLE = 22f
        const val LENGTH = 240f
        const val BOWL = 20f
        /** Le point du bord où elle s'appuie pour touiller. */
        const val PIVOT_X = 205f
        const val PIVOT_Y = 188f
        /** Le cycle : descente, trempage, remontée en moignon, pause, sortie. */
        const val CYCLE = 11f
        const val DOWN = 1.8f
        const val UP = 6f
        const val HOLD = 7.2f
        const val GONE = 10f
        const val LIFT_OUT = 290f
        const val STUMP = 96f
        const val MELT = 72f
        const val DROPS = 14
        const val DROP0 = 2.4f
        const val DROP_GAP = .42f
        const val STEAM = 6
        const val SPOON = 1
        const val TEA = 2
        const val GLASS = 3
    }
}
