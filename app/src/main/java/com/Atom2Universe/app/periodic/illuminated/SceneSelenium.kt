package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Sélénium — la photocopieuse. Le sélénium ne conduit l'électricité que lorsqu'il est éclairé :
 * le tambour des premières photocopieuses en était recouvert, et la lumière y dessinait la page.
 * Couvercle levé, la barre de lumière balaie la vitre sous la feuille, revient, et la copie
 * glisse hors de la machine jusque dans le bac.
 */
internal class SceneSelenium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_photocopier
    override val noteRes = R.string.card_note_photocopier
    override val explainRes = R.string.card_explain_photocopier

    private val top = poly(92f, 176f, 268f, 176f, 290f, 208f, 70f, 208f)
    private val glass = poly(GB_L, GB_Y, GB_R, GB_Y, GF_R, GF_Y, GF_L, GF_Y)
    private val sheet = poly(112f, 182f, 196f, 182f, 196f, 200f, 100f, 200f)
    /** La vitre là où la feuille ne la couvre pas : c'est là que la lumière se voit en plein. */
    private val bare = Path().apply { op(glass, sheet, Path.Op.DIFFERENCE) }

    override fun engrave(b: Burin) {
        // Le mur du bureau, le sol.
        val wall = rect(40f, 50f, 320f, FLOOR)
        b.wash(wall, 0xFFD8DCD4.toInt(), 255)
        b.glow(260f, 80f, 200f, 0xFFFFFFF4.toInt(), 160)
        b.washGradient(wall, 0xFF3A4038.toInt(), 0f, 100f, 0, 0f, FLOOR, 110)
        b.hatchRect(RectF(40f, 50f, 320f, FLOOR), 90f, 3.2f, .35f, Ink.BROWN, Fade(0f, 60f, 0f, FLOOR, 30, 130))
        val floor = rect(40f, FLOOR, 320f, 340f)
        b.wash(floor, 0xFF7A7468.toInt(), 255)
        b.hatchRect(RectF(40f, FLOOR, 320f, 340f), 0f, 2f, .4f, Ink.SEPIA, Fade(0f, FLOOR, 0f, 330f, 60, 200))
        // La machine, décalée vers la droite pour que le bac de sortie tienne dans la fenêtre.
        b.c.save(); b.c.translate(DX, 0f)
        // Le couvercle levé : on voit son dessous, la mousse blanche.
        val lid = poly(94f, 176f, 266f, 176f, 270f, 108f, 90f, 108f)
        b.body(lid, 0xFF8A8C88.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
        b.body(poly(100f, 172f, 260f, 172f, 263f, 114f, 97f, 114f), 0xFFF0F0EA.toInt(), .15f, 90f, outline = .7f, washAlpha = 255)
        // Le corps : dessus, vitre, feuille posée face en bas, façade, tiroirs.
        b.body(top, 0xFFD8D4C8.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
        b.body(glass, 0xFF2A3438.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.body(sheet, 0xFFFAFAF4.toInt(), 0f, outline = .6f, washAlpha = 255)
        val front = rect(70f, 208f, 290f, BASE)
        b.body(front, 0xFFE0DCD0.toInt(), .3f, 0f, Fade(70f, BASE, 70f, 208f, 160, 10), outline = 1f, washAlpha = 255)
        for (y in floatArrayOf(240f, 270f)) {
            b.body(rect(78f, y, 282f, y + 24f), 0xFFD0CCC0.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
            b.body(Path().apply { addRoundRect(160f, y + 9f, 200f, y + 14f, 2f, 2f, Path.Direction.CW) }, 0xFF5A5C58.toInt(), 0f, outline = .6f, washAlpha = 255)
        }
        b.line(70f, 216f, 290f, 216f, .7f, withAlpha(Ink.SEPIA, 160))
        // Les roulettes.
        for (x in floatArrayOf(84f, 276f)) b.body(ellipse(x, BASE + 4f, 6f, 4f), 0xFF3A3C38.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.fill(ellipse(180f, BASE + 6f, 120f, 6f), withAlpha(0xFF1A1810.toInt(), 70))
        // Le pupitre à droite : écran éteint, touches, la touche verte.
        val panel = poly(244f, 186f, 280f, 186f, 288f, 204f, 248f, 204f)
        b.body(panel, 0xFF5A5E5C.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.body(poly(248f, 188f, 264f, 188f, 266f, 196f, 249f, 196f), 0xFF8AA89A.toInt(), 0f, outline = .5f, washAlpha = 255)
        for (k in 0 until 3) b.c.drawCircle(254f + k * 6f, 200.5f, 1.6f, b.pen(0xFFD8D8D0.toInt()))
        // La fente de sortie à gauche et le bac, avec les copies déjà faites.
        b.body(rect(64f, 222f, 70f, 236f), 0xFF3A3C38.toInt(), 0f, outline = .7f, washAlpha = 255)
        val tray = poly(70f, 238f, 30f, 246f, 30f, 250f, 70f, 243f)
        b.body(tray, 0xFFB8B4A8.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        for (k in 0 until 3) {
            val dy = -k * 1.4f
            b.body(poly(66f, 234f + dy, 34f, 240f + dy, 34f, 242.4f + dy, 66f, 236.4f + dy), 0xFFF6F6F0.toInt(), 0f, outline = .5f, washAlpha = 255)
        }
        b.c.restore()
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val bar = Path()
    private val copy = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        c.save(); c.translate(DX, 0f)
        // La touche verte s'allume quand on appuie, puis clignote pendant la copie.
        val pressed = ph < .5f || (ph < 5f && (ph * 4f).toInt() % 2 == 0)
        fill.color = if (pressed) 0xFF5AE87A.toInt() else 0xFF2A7A3A.toInt()
        c.drawCircle(275f, 192f, 3.2f, fill)
        if (pressed) { fill.color = withAlpha(0xFF5AE87A.toInt(), 70); c.drawCircle(275f, 192f, 8f, fill) }
        // La barre de lumière : aller lent et lumineux, retour rapide et éteint.
        val u: Float; val power: Float
        when {
            ph < .6f -> { u = 0f; power = 0f }
            ph < 2.6f -> { u = (ph - .6f) / 2f; power = 1f }
            ph < 3.2f -> { u = 1f - (ph - 2.6f) / .6f; power = .25f }
            else -> { u = 0f; power = 0f }
        }
        if (power > 0f) {
            val xb = GB_L + u * (GB_R - GB_L); val xf = GF_L + u * (GF_R - GF_L)
            // La lueur dans la pièce, sur le couvercle et sur le mur.
            fill.color = withAlpha(0xFFE8FFF0.toInt(), (40 * power).toInt())
            c.drawCircle((xb + xf) / 2f, 150f, 90f, fill)
            c.save(); c.clipPath(glass)
            bar.reset()
            bar.moveTo(xb - 6f, GB_Y); bar.lineTo(xb + 6f, GB_Y); bar.lineTo(xf + 7f, GF_Y); bar.lineTo(xf - 7f, GF_Y); bar.close()
            fill.color = withAlpha(0xFFB8FFD8.toInt(), (110 * power).toInt())
            c.save(); c.clipPath(bare); c.drawPath(bar, fill); c.restore()
            // Sous la feuille, on ne voit qu'un liseré qui déborde.
            fill.color = withAlpha(0xFFB8FFD8.toInt(), (40 * power).toInt())
            c.drawPath(bar, fill)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * power).toInt()); ink.strokeWidth = 2f
            c.save(); c.clipPath(bare); c.drawLine(xb, GB_Y, xf, GF_Y, ink); c.restore()
            c.restore()
            // Le reflet de la barre sur la mousse du couvercle.
            fill.color = withAlpha(0xFFE8FFF0.toInt(), (90 * power).toInt())
            val lx = 100f + u * 160f
            c.drawRect(lx - 5f, 116f, lx + 5f, 170f, fill)
        }
        // La copie : elle sort de la fente vers la gauche, et se pose exactement à la place de la
        // feuille du dessus de la pile : quand le cycle repart, rien ne saute.
        if (ph > 3f) {
            val k = ((ph - 3f) / 2f).coerceAtMost(1f)
            val e = k * k * (3f - 2f * k)
            val len = 32f * e
            if (len > 1f) {
                val r = 66f; val l = r - len
                val yr = 231.2f - 2f * (1f - e)
                val yl = yr + 6f * e
                copy.reset()
                copy.moveTo(r, yr); copy.lineTo(l, yl); copy.lineTo(l, yl + 2.4f); copy.lineTo(r, yr + 2.4f); copy.close()
                fill.color = 0xFFFCFCF6.toInt(); c.drawPath(copy, fill)
                ink.color = Ink.SEPIA; ink.strokeWidth = .5f; c.drawPath(copy, ink)
            }
        }
        // Le ventilateur souffle un peu de chaleur sur le côté droit.
        val heat = sin(t * 3f)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), (40 + 30 * heat).toInt()); ink.strokeWidth = 1f
        for (k in 0 until 3) {
            val y = 224f + k * 6f
            c.drawLine(292f, y, 300f + 4f * sin(t * 4f + k), y - 1f, ink)
        }
        c.restore()
    }

    private companion object {
        const val FLOOR = 262f
        const val BASE = 300f
        /** La vitre : bord du fond (gauche, droite, hauteur), bord avant. */
        const val GB_L = 104f
        const val GB_R = 240f
        const val GB_Y = 180f
        const val GF_L = 90f
        const val GF_R = 252f
        const val GF_Y = 203f
        const val CYCLE = 6.5f
        const val DX = 14f
    }
}
