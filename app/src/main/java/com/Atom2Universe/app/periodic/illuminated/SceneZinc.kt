package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zinc — les toits de Paris. Les grandes feuilles de zinc couvrent les toits de Paris depuis plus
 * d'un siècle : elles ne rouillent pas, elles se patinent en gris bleuté. Au crépuscule, un toit
 * de zinc au premier plan, sa cheminée et sa lucarne allumée ; derrière, les mansardes, un dôme,
 * et la tour Eiffel dorée qui scintille.
 */
internal class SceneZinc : EngravedScene() {

    override val sky = Sky.DUSK
    override val nameRes = R.string.card_scene_paris_roofs
    override val noteRes = R.string.card_note_paris_roofs
    override val explainRes = R.string.card_explain_paris_roofs

    private val tower = Path()
    /** Les points où la tour scintille, tirés une fois pour toutes dans sa silhouette. */
    private val sparkX = FloatArray(SPARKS)
    private val sparkY = FloatArray(SPARKS)

    init {
        tower.moveTo(TX - halfWidth(0f), TB)
        var h = 0f
        while (h <= 128f) { tower.lineTo(TX - halfWidth(h), TB - h); h += 4f }
        h = 128f
        while (h >= 0f) { tower.lineTo(TX + halfWidth(h), TB - h); h -= 4f }
        tower.close()
        val arch = Path().apply { moveTo(TX - 17f, TB + 1f); quadTo(TX, TB - 30f, TX + 17f, TB + 1f); close() }
        tower.op(arch, Path.Op.DIFFERENCE)
        for (i in 0 until SPARKS) {
            val hh = 26f + hash(i, 1) * 92f
            sparkY[i] = TB - hh
            sparkX[i] = TX + (2f * hash(i, 2) - 1f) * halfWidth(hh) * .85f
        }
    }

    /** La demi-largeur de la tour à la hauteur [h] au-dessus de sa base, d'après ses vraies proportions. */
    private fun halfWidth(h: Float): Float {
        var k = 0
        while (k < PROFILE.size - 4 && h > PROFILE[k + 2]) k += 2
        val u = ((h - PROFILE[k]) / (PROFILE[k + 2] - PROFILE[k])).coerceIn(0f, 1f)
        return PROFILE[k + 1] + (PROFILE[k + 3] - PROFILE[k + 1]) * u
    }

    override fun engrave(b: Burin) {
        // La tour dorée, et le halo de ses projecteurs.
        b.glow(TX, TB - 56f, 80f, 0xFFFFC870.toInt(), 70)
        b.fill(tower, 0xFF6A4630.toInt())
        b.hatch(tower, 62f, 2.6f, .5f, Gilding.LEAF)
        b.hatch(tower, -62f, 2.6f, .5f, Gilding.LEAF)
        for ((h, w) in arrayOf(22f to 20f, 45f to 12.5f, 109f to 3.4f)) {
            b.fill(rect(TX - w, TB - h - 1.6f, TX + w, TB - h + 1.6f), 0xFF4A2E1E.toInt())
            b.line(TX - w, TB - h - 1.6f, TX + w, TB - h - 1.6f, .8f, Gilding.LIGHT)
        }
        b.stroke(tower, .8f, 0xFF3A2414.toInt())
        // La ville au loin : toits, cheminées, un dôme.
        val far = Path().apply {
            moveTo(40f, 206f); lineTo(58f, 206f); lineTo(58f, 198f); lineTo(64f, 198f); lineTo(64f, 204f)
            lineTo(80f, 204f); lineTo(80f, 192f)
            cubicTo(80f, 176f, 112f, 176f, 112f, 192f)
            lineTo(112f, 202f); lineTo(140f, 202f); lineTo(140f, 196f); lineTo(146f, 196f); lineTo(146f, 204f)
            lineTo(190f, 204f); lineTo(196f, 198f); lineTo(214f, 198f); lineTo(220f, 206f); lineTo(320f, 206f)
            lineTo(320f, 240f); lineTo(40f, 240f); close()
        }
        b.body(far, 0xFF5A5068.toInt(), .3f, 0f, Fade(0f, 190f, 0f, 236f, 40, 200), outline = .7f, washAlpha = 255)
        b.line(96f, 178f, 96f, 162f, 1.6f, 0xFF4A4058.toInt())
        b.body(Path().apply { addRect(92f, 172f, 100f, 180f, Path.Direction.CW) }, 0xFF5A5068.toInt(), 0f, 0f, outline = .6f, washAlpha = 255)
        b.c.drawArc(80f, 176f, 112f, 208f, 200f, 50f, false, b.pen(withAlpha(0xFFFFC8A0.toInt(), 160), 1.2f))
        // Les mansardes en zinc de la rangée du milieu, leurs lucarnes.
        for (k in MID.indices step 2) mansard(b, MID[k], MID[k + 1])
        for (w in DORMERS) {
            b.body(Path().apply { addRect(w[0] - 4f, w[1], w[0] + 4f, w[1] + 9f, Path.Direction.CW) }, 0xFFD8CCB8.toInt(), 0f, 0f, outline = .6f, washAlpha = 255)
            b.fill(poly(w[0] - 5.5f, w[1], w[0], w[1] - 4f, w[0] + 5.5f, w[1]), 0xFF5E6678.toInt())
            b.stroke(poly(w[0] - 5.5f, w[1], w[0], w[1] - 4f, w[0] + 5.5f, w[1]), .6f)
            b.fill(rect(w[0] - 2.4f, w[1] + 2f, w[0] + 2.4f, w[1] + 8f), 0xFF2A2838.toInt())
        }
        roof(b)
        chimney(b)
        // La lumière de la lucarne déborde un peu sur le zinc.
        b.glow(DORMER_X, 300f, 64f, 0xFFFFC880.toInt(), 60)
        dormer(b)
    }

    /** Une mansarde de la rangée du milieu : brisis raide en zinc, terrasson, cheminées. */
    private fun mansard(b: Burin, x0: Float, x1: Float) {
        val brisis = poly(x0, 236f, x0 + 5f, 216f, x1 - 5f, 216f, x1, 236f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 216f, 0f, 236f, 0xFF8A90A4.toInt(), 0xFF4E5468.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(brisis, b.p)
        b.c.save(); b.c.clipPath(brisis)
        var x = x0 + 3f
        while (x < x1) { b.line(x, 216f, x, 236f, .5f, withAlpha(Ink.SEPIA, 140)); x += 5f }
        b.c.restore()
        b.stroke(brisis, .7f)
        b.body(poly(x0 + 5f, 216f, x0 + 14f, 210f, x1 - 14f, 210f, x1 - 5f, 216f), 0xFF707890.toInt(), 0f, 0f, outline = .6f, washAlpha = 255)
        b.line(x0 + 14f, 210f, x1 - 14f, 210f, 1f, withAlpha(0xFFFFC8A8.toInt(), 200))
        for (cx in floatArrayOf(x0 + 18f, x1 - 20f)) {
            b.body(rect(cx - 4f, 200f, cx + 4f, 211f), 0xFFC8B8A0.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
            b.fill(rect(cx - 3f, 197f, cx - .5f, 200f), 0xFFB0603A.toInt())
            b.fill(rect(cx + .5f, 197f, cx + 3f, 200f), 0xFFB0603A.toInt())
        }
    }

    /** Le toit du premier plan : grandes feuilles de zinc à joints debout, faîtage arrondi. */
    private fun roof(b: Burin) {
        val slope = rect(40f, RIDGE, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, RIDGE, 0f, 330f,
            intArrayOf(0xFFB8A4B0.toInt(), 0xFF8A94A8.toInt(), 0xFF5A6276.toInt()), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(slope, b.p)
        b.hatchRect(RectF(40f, RIDGE, 320f, 340f), 90f, 2.2f, .35f, Ink.SEPIA, Fade(0f, RIDGE, 0f, 330f, 30, 170))
        // Les joints debout, et les raccords des feuilles en quinconce.
        var i = 0
        var x = SEAM0
        while (x < 330f) {
            b.line(x, RIDGE, x, 340f, 2.2f, 0xFF4A5064.toInt())
            b.line(x - .8f, RIDGE, x - .8f, 340f, .7f, withAlpha(0xFFE8E4F0.toInt(), 200))
            val y = 284f + (if (i % 2 == 0) 0f else 18f)
            b.line(x + 1f, y, x + SEAM - 1f, y, .6f, withAlpha(Ink.SEPIA, 140))
            x += SEAM; i++
        }
        // Le faîtage.
        val ridge = Path().apply { addRoundRect(40f, RIDGE - 4f, 320f, RIDGE + 3f, 3f, 3f, Path.Direction.CW) }
        b.body(ridge, 0xFF9AA2B4.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        b.line(40f, RIDGE - 3f, 320f, RIDGE - 3f, .9f, withAlpha(0xFFFFD8C0.toInt(), 220))
    }

    /** La souche de cheminée sur le faîtage, ses mitrons de terre cuite. */
    private fun chimney(b: Burin) {
        val stack = rect(CHIMNEY_L, 202f, CHIMNEY_R, RIDGE)
        b.body(stack, 0xFFD8C8B0.toInt(), .35f, 0f, Fade(CHIMNEY_R, 0f, CHIMNEY_L, 0f, 220, 30), outline = 1f, washAlpha = 255)
        for (y in floatArrayOf(214f, 228f, 242f)) b.line(CHIMNEY_L, y, CHIMNEY_R, y, .5f, withAlpha(Ink.SEPIA, 120))
        b.body(rect(CHIMNEY_L - 3f, 196f, CHIMNEY_R + 3f, 202f), 0xFFC8B8A0.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        for (k in POTS.indices) {
            val px = POTS[k]; val top = POT_TOP[k]
            val pot = poly(px - 4.6f, 196f, px - 3.4f, top, px + 3.4f, top, px + 4.6f, 196f)
            b.body(pot, 0xFFC0603A.toInt(), .35f, 0f, Fade(px + 4f, 0f, px - 4f, 0f, 220, 20), outline = .8f, washAlpha = 255)
            b.fill(ellipse(px, top, 3.4f, 1.2f), 0xFF3A2018.toInt())
        }
    }

    /** La lucarne du premier plan : fronton, montants, fenêtre à petits carreaux allumée. */
    private fun dormer(b: Burin) {
        val walls = rect(DORMER_X - 22f, 270f, DORMER_X + 22f, 340f)
        b.body(walls, 0xFFE0D4C0.toInt(), .3f, 0f, Fade(DORMER_X + 22f, 0f, DORMER_X - 22f, 0f, 200, 20), outline = 1f, washAlpha = 255)
        val pediment = Path().apply {
            moveTo(DORMER_X - 27f, 272f); quadTo(DORMER_X, 238f, DORMER_X + 27f, 272f); close()
        }
        b.body(pediment, 0xFF8A94A8.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
        b.c.drawArc(DORMER_X - 27f, 255f, DORMER_X + 27f, 289f, 200f, 50f, false, b.pen(withAlpha(0xFFFFD8C0.toInt(), 200), 1.2f))
        b.fill(rect(WIN_L, WIN_T, WIN_R, 340f), 0xFF3A3040.toInt())
    }

    override fun sprites(): List<Sprite> = listOf(
        // La fenêtre allumée de la lucarne : lumière chaude, rideau, petits bois.
        Sprite(WINDOW, RectF(WIN_L, WIN_T, WIN_R, 320f)) { b ->
            val glass = rect(WIN_L, WIN_T, WIN_R, 320f)
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, WIN_T, 0f, 320f,
                intArrayOf(0xFFFFE6A8.toInt(), 0xFFF0B060.toInt(), 0xFFC07838.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawPath(glass, b.p)
            b.fill(poly(WIN_L, WIN_T, WIN_L + 9f, WIN_T, WIN_L + 5f, 320f, WIN_L, 320f), withAlpha(0xFFB0503A.toInt(), 170))
            val mid = (WIN_L + WIN_R) / 2f
            b.line(mid, WIN_T, mid, 320f, 2f, 0xFFE8E0D0.toInt())
            for (y in floatArrayOf(WIN_T + 12f, WIN_T + 24f, WIN_T + 36f)) b.line(WIN_L, y, WIN_R, y, 1.4f, 0xFFE8E0D0.toInt())
            b.stroke(glass, 1f)
        },
        // Un pigeon sans tête : la tête se dessine à part pour hocher.
        Sprite(PIGEON, RectF(-10f, -10f, 10f, 2f)) { b ->
            val body = Path().apply {
                moveTo(-9f, -3f); quadTo(-4f, -9f, 3f, -7f); quadTo(7f, -5f, 6f, -1f); quadTo(0f, 1f, -6f, -1f); close()
            }
            b.body(body, 0xFF8A8C9A.toInt(), .35f, 0f, Fade(0f, 0f, 0f, -8f, 200, 0), outline = .8f, washAlpha = 255)
            b.fill(poly(-9f, -3f, -10f, -1f, -5f, -1.5f), 0xFF5A5C6A.toInt())
            b.line(0f, 0f, 0f, 2f, .8f, 0xFFC86A5A.toInt())
            b.line(2f, 0f, 2.4f, 2f, .8f, 0xFFC86A5A.toInt())
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val beam = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Le phare au sommet de la tour : un pinceau qui tourne, long quand il passe de côté.
        val a = t * 1.1f
        val reach = 150f * sin(a)
        val bright = (abs(sin(a)) * 70f).toInt()
        if (bright > 4) {
            val tipY = TB - 120f
            beam.reset()
            beam.moveTo(TX, tipY)
            beam.lineTo(TX + reach, tipY - 6f); beam.lineTo(TX + reach, tipY + 6f); beam.close()
            fill.color = withAlpha(0xFFFFF0C8.toInt(), bright)
            c.drawPath(beam, fill)
        }
        // Le scintillement : dix secondes sur quinze, la tour crépite de points blancs.
        if (t % 15f < 10f) {
            val tick = (t * 14f).toInt()
            for (i in 0 until SPARKS) {
                if (hash(tick, i) < .62f) continue
                val glow = .4f + .6f * hash(tick + 7, i)
                fill.color = withAlpha(0xFFFFFFFF.toInt(), (255 * glow).toInt())
                c.drawCircle(sparkX[i], sparkY[i], .9f + glow * .6f, fill)
                fill.color = withAlpha(0xFFFFF0D0.toInt(), (70 * glow).toInt())
                c.drawCircle(sparkX[i], sparkY[i], 3f, fill)
            }
        }
        // Les lucarnes du fond s'allument et s'éteignent chacune à son heure.
        for (k in DORMERS.indices) {
            val w = DORMERS[k]
            val phase = (t + k * 5.3f) % 23f
            if (phase < 7f) continue
            val fade = ((phase - 7f) / .6f).coerceAtMost(1f) * ((23f - phase) / .6f).coerceAtMost(1f)
            fill.color = withAlpha(0xFFFFD890.toInt(), (230 * fade).toInt())
            c.drawRect(w[0] - 2.4f, w[1] + 2f, w[0] + 2.4f, w[1] + 8f, fill)
        }
        // La fumée des mitrons : des bouffées qui montent, grossissent et dérivent avec le vent.
        for (p in SMOKING) {
            val px = POTS[p]; val top = POT_TOP[p]
            for (k in 0 until PUFFS) {
                val life = ((t + k * PUFF_LIFE / PUFFS + p * .7f) % PUFF_LIFE) / PUFF_LIFE
                val x = px + life * 34f + 3f * sin(life * 6f + k)
                val y = top - life * 58f
                val r = 2.4f + life * 10f
                fill.color = withAlpha(0xFFB8B0BC.toInt(), (120 * (1f - life) * (life * 6f).coerceAtMost(1f)).toInt())
                c.drawCircle(x, y, r, fill)
            }
        }
        // Le reflet du couchant qui glisse sur les joints du zinc.
        val sheen = 180f + 110f * sin(t * .23f)
        var x = SEAM0
        while (x < 330f) {
            val d = abs(x - sheen)
            // Pas sur la lucarne, qui est devant le toit.
            if (d < 50f && abs(x - DORMER_X) > 24f) {
                ink.color = withAlpha(0xFFFFD8C8.toInt(), (200 * (1f - d / 50f)).toInt()); ink.strokeWidth = 1.1f
                c.drawLine(x - .8f, RIDGE + 4f, x - .8f, 330f, ink)
            }
            x += SEAM
        }
        // La lucarne allumée, qui vacille un peu.
        val flicker = 225 + (20 * sin(t * 7f) * sin(t * 2.3f)).toInt()
        s.draw(c, WINDOW, alpha = flicker)
        // Les deux pigeons sur le faîtage : ils hochent la tête.
        for (k in PIGEONS.indices) {
            val px = PIGEONS[k]
            s.draw(c, PIGEON, dx = px, dy = RIDGE - 4f)
            val nod = sin(t * (3f + k) + k * 2f)
            val hx = px + 7f + 1.5f * nod.coerceAtLeast(0f)
            val hy = RIDGE - 13f + 2.5f * nod.coerceAtLeast(0f)
            fill.color = 0xFF7A7C8A.toInt(); c.drawCircle(hx, hy, 2.8f, fill)
            ink.color = Ink.SEPIA; ink.strokeWidth = .7f; c.drawCircle(hx, hy, 2.8f, ink)
            fill.color = 0xFFD8A040.toInt(); c.drawCircle(hx + 2.8f, hy + .4f, .8f, fill)
            fill.color = Ink.SEPIA; c.drawCircle(hx + 1f, hy - .6f, .7f, fill)
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** Le pied de la tour (caché derrière les toits), son axe. */
        const val TX = 258f
        const val TB = 228f
        const val SPARKS = 56
        const val RIDGE = 252f
        const val SEAM0 = 46f
        const val SEAM = 19f
        const val CHIMNEY_L = 70f
        const val CHIMNEY_R = 118f
        const val DORMER_X = 200f
        const val WIN_L = 189f
        const val WIN_R = 211f
        const val WIN_T = 276f
        const val PUFFS = 7
        const val PUFF_LIFE = 4.5f
        const val WINDOW = 1
        const val PIGEON = 2
        /** Hauteur au-dessus de la base et demi-largeur de la tour : 324 m de haut, 125 m de large au pied. */
        val PROFILE = floatArrayOf(0f, 24.5f, 22f, 17f, 45f, 10.5f, 75f, 5f, 100f, 2.6f, 109f, 1.8f, 118f, 1f, 128f, .4f)
        /** Les mansardes du milieu, de gauche à droite (bord gauche, bord droit). */
        val MID = floatArrayOf(36f, 110f, 108f, 182f, 180f, 238f, 236f, 324f)
        /** Les lucarnes des mansardes (x, haut de la fenêtre). */
        val DORMERS = arrayOf(
            floatArrayOf(58f, 222f), floatArrayOf(86f, 222f), floatArrayOf(130f, 222f), floatArrayOf(158f, 222f),
            floatArrayOf(208f, 222f), floatArrayOf(268f, 222f), floatArrayOf(300f, 222f)
        )
        val PIGEONS = floatArrayOf(250f, 284f)
        val POTS = floatArrayOf(78f, 90f, 102f, 112f)
        val POT_TOP = floatArrayOf(182f, 185f, 181f, 184f)
        val SMOKING = intArrayOf(1, 3)
    }
}
