package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calcium — les os, en planche d'anatomie : le squelette de la main et du poignet. Le calcium
 * fait la dureté de l'os. Les doigts s'écartent en éventail puis se resserrent, et une légère
 * vague les fait plier tour à tour : on voit chaque phalange tourner sur la suivante. La planche
 * montre le dos de la main, donc un doigt qui plie vient vers nous et raccourcit : la flexion reste
 * faible (30° au plus), sinon les bouts se tassent en moignons.
 */
internal class SceneCalcium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_hand_bones
    override val noteRes = R.string.card_note_hand_bones
    override val explainRes = R.string.card_explain_hand_bones

    override fun engrave(b: Burin) {
        b.glow(186f, 200f, 170f, 0xFFD8E2EC.toInt(), 120)
        forearm(b)
        carpals(b)
        for (f in FINGERS) bone(b, f[0], f[1], f[2], f[3], f[4], false)
    }

    private fun forearm(b: Burin) {
        // Le radius, côté pouce, large au poignet ; le cubitus, plus fin, et sa pointe.
        val radius = Path().apply {
            moveTo(156f, 340f); cubicTo(156f, 316f, 150f, 296f, 150f, 284f)
            cubicTo(152f, 276f, 170f, 276f, 186f, 282f); cubicTo(184f, 296f, 178f, 316f, 178f, 340f); close()
        }
        b.wash(radius, IVORY, 255)
        b.hatch(radius, 92f, 1.4f, .45f, Ink.SEPIA, Fade(180f, 0f, 160f, 0f, 230, 0))
        b.stroke(radius, .9f)
        val ulna = Path().apply {
            moveTo(194f, 340f); cubicTo(194f, 318f, 192f, 300f, 194f, 288f)
            cubicTo(196f, 282f, 206f, 280f, 212f, 284f); cubicTo(214f, 280f, 216f, 286f, 213f, 290f)
            cubicTo(208f, 302f, 208f, 318f, 210f, 340f); close()
        }
        b.wash(ulna, IVORY, 255)
        b.hatch(ulna, 92f, 1.4f, .45f, Ink.SEPIA, Fade(212f, 0f, 198f, 0f, 230, 0))
        b.stroke(ulna, .9f)
    }

    private fun carpals(b: Burin) {
        for (k in CARPALS) {
            b.c.save(); b.c.rotate(k[4], k[0], k[1])
            val shape = Path().apply { addRoundRect(k[0] - k[2], k[1] - k[3], k[0] + k[2], k[1] + k[3], k[2] * .8f, k[3] * .8f, Path.Direction.CW) }
            b.wash(shape, IVORY, 255)
            b.hatch(shape, 40f, 1.3f, .4f, Ink.SEPIA, Fade(k[0] + k[2], k[1] + k[3], k[0], k[1], 230, 0))
            b.stroke(shape, .8f)
            b.c.restore()
        }
    }

    /** Un os long posé de ([x0], [y0]) à ([x1], [y1]) : sert aux métacarpiens. */
    private fun bone(b: Burin, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, distal: Boolean) {
        val len = kotlin.math.hypot(x1 - x0, y1 - y0)
        val deg = Math.toDegrees(atan2((x1 - x0).toDouble(), (y0 - y1).toDouble())).toFloat()
        b.c.save(); b.c.translate(x0, y0); b.c.rotate(deg)
        phalanx(b, len, w, distal)
        b.c.restore()
    }

    /** Une phalange debout, sa base en (0, 0) : base évasée, corps étroit, tête en deux condyles. */
    private fun phalanx(b: Burin, len: Float, w: Float, distal: Boolean) {
        val hw = w / 2f
        val p = Path().apply {
            moveTo(-hw, -1f)
            cubicTo(-hw, 2.2f, hw, 2.2f, hw, -1f)
            cubicTo(hw * .95f, -len * .2f, hw * .55f, -len * .32f, hw * .55f, -len * .5f)
            if (distal) {
                cubicTo(hw * .5f, -len * .74f, hw * .9f, -len * .86f, hw * .7f, -len + .5f)
                cubicTo(hw * .4f, -len - 1.6f, -hw * .4f, -len - 1.6f, -hw * .7f, -len + .5f)
                cubicTo(-hw * .9f, -len * .86f, -hw * .5f, -len * .74f, -hw * .55f, -len * .5f)
            } else {
                cubicTo(hw * .55f, -len * .7f, hw * .9f, -len * .84f, hw * .85f, -len + 1.2f)
                cubicTo(hw * .75f, -len - 1.6f, hw * .15f, -len - 1.4f, 0f, -len + .6f)
                cubicTo(-hw * .15f, -len - 1.4f, -hw * .75f, -len - 1.6f, -hw * .85f, -len + 1.2f)
                cubicTo(-hw * .9f, -len * .84f, -hw * .55f, -len * .7f, -hw * .55f, -len * .5f)
            }
            cubicTo(-hw * .55f, -len * .32f, -hw * .95f, -len * .2f, -hw, -1f)
            close()
        }
        b.wash(p, IVORY, 255)
        b.hatch(p, 90f, 1.25f, .4f, Ink.SEPIA, Fade(hw, 0f, -hw * .1f, 0f, 230, 0))
        b.hatch(p, 15f, 1.8f, .35f, Ink.SEPIA, Fade(0f, 0f, 0f, -len * .22f, 170, 0))
        b.stroke(p, .75f)
    }

    override fun sprites(): List<Sprite> {
        val list = ArrayList<Sprite>()
        for ((i, d) in DIGITS.withIndex()) {
            val n = if (i == 0) 2 else 3
            for (j in 0 until n) {
                val len = d[3 + j]; val w = d[6 + j]
                list += Sprite(i * 3 + j, RectF(-w, -len - 3f, w, 3f)) { b -> phalanx(b, len, w, j == n - 1) }
            }
        }
        return list
    }

    // ───────────── animation ─────────────

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // L'éventail : 0 doigts serrés, 1 doigts écartés.
        var open = .5f - .5f * cos(t * 6.2832f / FAN_PERIOD)
        open = open * open * (3f - 2f * open)
        for ((i, d) in DIGITS.withIndex()) {
            val n = if (i == 0) 2 else 3
            val deg = d[2] + CLOSED[i] + (OPENED[i] - CLOSED[i]) * open
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            // La vague passe du petit doigt vers le pouce.
            var f = .5f + .5f * sin(t * 1.9f - (4 - i) * .8f)
            f = f * f * (3f - 2f * f)
            var x = d[0]; var y = d[1]
            var bend = 0f
            for (j in 0 until n) {
                bend += f * (if (i == 0) THUMB_CURL[j] else CURL[j])
                val shown = cos(Math.toRadians(bend.toDouble()).toFloat())
                c.save(); c.translate(x, y); c.rotate(deg); c.scale(1f, shown)
                s.draw(c, i * 3 + j)
                c.restore()
                val len = d[3 + j] * shown
                x += sin(rad) * len; y -= cos(rad) * len
            }
        }
    }

    private companion object {
        const val IVORY = 0xFFF2E8D0.toInt()
        /** Durée d'un éventail complet (écarter puis resserrer), en secondes. */
        const val FAN_PERIOD = 6f
        /** Écart ajouté à l'angle de chaque doigt, en degrés, serré puis écarté : pouce → auriculaire. */
        val CLOSED = floatArrayOf(8f, 5f, 0f, -4f, -8f)
        val OPENED = floatArrayOf(-12f, -5f, 0f, 4f, 8f)
        /** Flexion de chaque articulation au creux de la vague : base, milieu, bout (30° cumulés au plus). */
        val CURL = floatArrayOf(8f, 12f, 10f)
        val THUMB_CURL = floatArrayOf(6f, 10f)
        /** Les métacarpiens : base, tête, largeur ; pouce, index, majeur, annulaire, auriculaire. */
        val FINGERS = arrayOf(
            floatArrayOf(160f, 252f, 138f, 216f, 10f),
            floatArrayOf(172f, 248f, 161f, 180f, 9.5f),
            floatArrayOf(184f, 247f, 183f, 174f, 10f),
            floatArrayOf(196f, 249f, 205f, 180f, 9.5f),
            floatArrayOf(205f, 254f, 225f, 194f, 8.5f)
        )
        /** Les doigts : départ (tête du métacarpien), angle, longueurs des phalanges, largeurs. */
        val DIGITS = arrayOf(
            floatArrayOf(138f, 214f, -36f, 24f, 19f, 0f, 9.6f, 8f, 0f),
            floatArrayOf(161f, 178f, -9f, 28f, 18f, 15f, 9f, 7.5f, 6.5f),
            floatArrayOf(183f, 172f, 0f, 31f, 20f, 16f, 9.5f, 8f, 6.8f),
            floatArrayOf(205f, 178f, 9f, 29f, 19f, 15f, 9f, 7.5f, 6.5f),
            floatArrayOf(225f, 192f, 19f, 22f, 14f, 13f, 7.6f, 6.4f, 5.6f)
        )
        /** Les os du carpe : centre, demi-largeur, demi-hauteur, rotation. */
        val CARPALS = arrayOf(
            floatArrayOf(166f, 272f, 9f, 6f, -20f), floatArrayOf(181f, 276f, 7f, 6f, 0f),
            floatArrayOf(195f, 274f, 6.5f, 5.5f, 15f), floatArrayOf(205f, 268f, 4f, 4f, 0f),
            floatArrayOf(160f, 258f, 7f, 6f, -30f), floatArrayOf(172f, 256f, 6f, 5f, -10f),
            floatArrayOf(184f, 259f, 7f, 8f, 0f), floatArrayOf(198f, 259f, 7f, 7f, 20f)
        )
    }
}
