package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Étain — la soudure. Le fil à souder est surtout fait d'étain : il fond vers 220 °C et colle
 * les pattes des composants aux pistes de cuivre. La carte électronique avance d'un cran à chaque
 * soudure ; le fer se pose sur la patte, le fil d'étain arrive, fond en une petite goutte
 * brillante, un filet de fumée monte, et la bobine se dévide un peu.
 */
internal class SceneTin : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_soldering
    override val noteRes = R.string.card_note_soldering
    override val explainRes = R.string.card_explain_soldering

    override fun engrave(b: Burin) {
        // Le tapis de travail, sa grille ; une pince posée.
        val mat = rect(40f, 50f, 320f, BOARD_T + 4f)
        b.wash(mat, 0xFF2A4A44.toInt(), 255)
        var x = 40f
        while (x < 320f) { b.line(x, 50f, x, BOARD_T, .5f, withAlpha(0xFF8AB0A8.toInt(), 90)); x += 20f }
        var y = 50f
        while (y < BOARD_T) { b.line(40f, y, 320f, y, .5f, withAlpha(0xFF8AB0A8.toInt(), 90)); y += 20f }
        b.washGradient(mat, 0xFF0A1410.toInt(), 0f, 50f, 40, 0f, BOARD_T, 140)
        b.body(poly(150f, 92f, 214f, 120f, 212f, 124f, 150f, 96f), 0xFFC8CCD0.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        b.body(poly(150f, 100f, 214f, 120f, 212f, 124f, 150f, 104f), 0xFFB8BCC0.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        // Le support de la bobine.
        b.body(rect(SPOOL_X - 3f, SPOOL_Y, SPOOL_X + 3f, BOARD_T), 0xFF5A5E64.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.fill(ellipse(SPOOL_X, BOARD_T + 2f, 16f, 4f), withAlpha(0xFF0A1410.toInt(), 120))
    }

    override fun sprites(): List<Sprite> = listOf(
        // Une tranche de carte, de quatre pattes : on la répète pour faire toute la carte.
        Sprite(BOARD, RectF(0f, BOARD_T, TILE, 342f)) { b ->
            b.wash(rect(0f, BOARD_T, TILE, 342f), 0xFF2A6A3A.toInt(), 255)
            b.hatchRect(RectF(0f, BOARD_T, TILE, 342f), 0f, 3.4f, .3f, 0xFF1A4A28.toInt())
            b.fill(rect(0f, BOARD_T, TILE, BOARD_T + 3f), 0xFF9AB880.toInt())
            b.line(0f, BOARD_T + 3f, TILE, BOARD_T + 3f, .8f, Ink.SEPIA)
            val copper = 0xFFC8843A.toInt()
            // Les pistes de cuivre : chaque patte descend vers une longue piste qui court d'un bout à l'autre.
            for (k in 0 until 4) {
                val px = SP / 2f + k * SP
                b.line(px, JY, px, JY + 26f + (k % 2) * 16f, 2.4f, copper)
                b.line(px, JY + 26f + (k % 2) * 16f, px + SP * .6f, JY + 26f + (k % 2) * 16f, 2.4f, copper)
            }
            b.line(0f, JY + 62f, TILE, JY + 62f, 3f, copper)
            b.line(0f, JY + 74f, TILE, JY + 74f, 2f, copper)
            for (k in 0 until 4) b.c.drawCircle(SP * .6f + SP / 2f + k * SP, JY + 26f + (k % 2) * 16f, 2.6f, b.pen(copper))
            // Une rangée de petits composants déjà soudés, plus bas.
            for (k in 0 until 4) {
                val cx = SP / 2f + k * SP
                b.fill(rect(cx - 6f, JY + 48f, cx - 2f, JY + 54f), 0xFFC8CCD0.toInt())
                b.fill(rect(cx + 2f, JY + 48f, cx + 6f, JY + 54f), 0xFFC8CCD0.toInt())
                b.body(rect(cx - 3f, JY + 47f, cx + 3f, JY + 55f), 0xFF2A2A2E.toInt(), 0f, outline = .5f, washAlpha = 255)
            }
            // La résistance, couchée au-dessus des pattes 1 et 2 ; ses anneaux de couleur.
            val r0 = SP / 2f; val r1 = SP * 1.5f
            b.line(r0, JY, r0, JY - 14f, 1.4f, 0xFFB8BCC0.toInt())
            b.line(r1, JY, r1, JY - 14f, 1.4f, 0xFFB8BCC0.toInt())
            b.line(r0, JY - 14f, r1, JY - 14f, 1.4f, 0xFFB8BCC0.toInt())
            b.stroke(Path().apply { addRoundRect(r0 - 4f, JY - 24f, r1 + 4f, JY - 4f, 4f, 4f, Path.Direction.CW) }, .7f, withAlpha(0xFFFFFFFF.toInt(), 200))
            val res = Path().apply { addRoundRect(r0 + 6f, JY - 19f, r1 - 6f, JY - 9f, 5f, 5f, Path.Direction.CW) }
            b.body(res, 0xFFE0C8A0.toInt(), .2f, 0f, Fade(0f, JY - 9f, 0f, JY - 19f, 180, 0), outline = .8f, washAlpha = 255)
            for ((i, band) in intArrayOf(0xFF8A4A1A.toInt(), 0xFF2A2A2A.toInt(), 0xFFE8641E.toInt(), 0xFFD8B040.toInt()).withIndex()) {
                val bx = r0 + 11f + i * 4f + (if (i == 3) 3f else 0f)
                b.fill(rect(bx, JY - 19f, bx + 2f, JY - 9f), band)
            }
            // Le condensateur, debout au-dessus des pattes 3 et 4, vu d'en haut.
            val c0 = SP * 2.5f; val c1 = SP * 3.5f
            b.line(c0, JY, (c0 + c1) / 2f - 4f, JY - 18f, 1.4f, 0xFFB8BCC0.toInt())
            b.line(c1, JY, (c0 + c1) / 2f + 4f, JY - 18f, 1.4f, 0xFFB8BCC0.toInt())
            b.c.drawCircle((c0 + c1) / 2f, JY - 22f, 13f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200), .7f))
            b.body(ellipse((c0 + c1) / 2f, JY - 22f, 11f, 11f), 0xFF2A5AB8.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
            b.fill(Path().apply { addArc((c0 + c1) / 2f - 11f, JY - 33f, (c0 + c1) / 2f + 11f, JY - 11f, 110f, 70f); close() }, 0xFFB8C8E8.toInt())
            b.stroke(ellipse((c0 + c1) / 2f, JY - 22f, 11f, 11f), .9f)
            b.line((c0 + c1) / 2f - 4f, JY - 22f, (c0 + c1) / 2f + 4f, JY - 22f, .8f, 0xFF8A9AB8.toInt())
            b.line((c0 + c1) / 2f, JY - 26f, (c0 + c1) / 2f, JY - 18f, .8f, 0xFF8A9AB8.toInt())
            // Les pastilles de cuivre et le bout des pattes, encore nus.
            for (k in 0 until 4) {
                val px = SP / 2f + k * SP
                b.c.drawCircle(px, JY, 6f, b.pen(copper))
                b.c.drawCircle(px, JY, 6f, b.pen(Ink.SEPIA, .6f))
                b.c.drawCircle(px, JY, 2f, b.pen(0xFF1A2A1A.toInt()))
                b.c.drawCircle(px, JY, 1.3f, b.pen(0xFFD8DCE0.toInt()))
            }
        },
        // La bobine de fil d'étain : deux joues percées, le fil enroulé.
        Sprite(SPOOL, RectF(SPOOL_X - 24f, SPOOL_Y - 24f, SPOOL_X + 24f, SPOOL_Y + 24f)) { b ->
            b.body(ellipse(SPOOL_X, SPOOL_Y, 23f, 23f), 0xFFC83A2A.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
            b.body(ellipse(SPOOL_X, SPOOL_Y, 17f, 17f), 0xFFB8BCC0.toInt(), .25f, 0f, outline = .8f, washAlpha = 255)
            var r = 8f
            while (r < 17f) { b.c.drawCircle(SPOOL_X, SPOOL_Y, r, b.pen(withAlpha(0xFF6A6E74.toInt(), 150), .5f)); r += 1.6f }
            for (k in 0 until 4) {
                val a = k * PI.toFloat() / 2f
                b.c.drawCircle(SPOOL_X + 12.5f * cos(a), SPOOL_Y + 12.5f * sin(a), 3f, b.pen(0xFF8A2A1A.toInt()))
            }
            b.body(ellipse(SPOOL_X, SPOOL_Y, 6f, 6f), 0xFFE8E4DC.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
            b.c.drawCircle(SPOOL_X, SPOOL_Y, 2f, b.pen(Ink.SEPIA))
        }
    )

    // ───────────── animation ─────────────

    private val wire = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val n = (t / STEP).toInt()
        val u = t / STEP - n
        val shift = (n + smooth(u / MOVE)) * SP
        // La carte, tranche après tranche.
        val base = IX - shift - SP / 2f
        val first = floor((40f - base) / TILE).toInt() - 1
        for (m in first..first + 4) s.draw(c, BOARD, dx = base + m * TILE)
        // Pendant l'arrêt n, la patte n + 1 est sous le fer ; les précédentes sont soudées.
        val dwell = ((u - MOVE) / (1f - MOVE)).coerceIn(0f, 1f)
        val melt = if (u < MOVE) 0f else smooth((dwell - .15f) / .4f)
        val lo = floor((40f - IX + shift) / SP).toInt() - 1
        val hi = lo + (280f / SP).toInt() + 3
        for (j in lo..hi) {
            val g = when {
                j <= n -> 1f
                j == n + 1 -> melt
                else -> 0f
            }
            if (g > 0f) bead(c, IX + j * SP - shift, g)
        }
        // La fumée de la résine, qui monte en volutes depuis la soudure en cours et les précédentes.
        for (k in 0 until SMOKE) {
            val period = 2.6f + hash(k, 1)
            val off = hash(k, 2) * period
            val m = ((t + off) / period).toInt()
            val birth = m * period - off
            val bs = (birth / STEP).toInt()
            val bu = birth / STEP - bs
            if (bu < MOVE + (1f - MOVE) * .2f || bu > MOVE + (1f - MOVE) * .85f) continue
            val age = t - birth
            val life = age / 2.4f
            if (life >= 1f) continue
            val sx = IX - 2f + 6f * (hash(m, k) - .5f) + age * 10f + 6f * sin(age * 2.4f + k)
            val sy = JY - 6f - age * 34f
            fill.color = withAlpha(0xFFE8ECF0.toInt(), (70 * (1f - life) * (life * 6f).coerceAtMost(1f)).toInt())
            c.drawCircle(sx, sy, 3f + age * 7f, fill)
        }
        // Le fil d'étain : il arrive sur la patte pendant la soudure, puis se retire un peu.
        val feed = if (u < MOVE) 0f else smooth(dwell / .2f) * (1f - smooth((dwell - .7f) / .2f))
        val ex = IX - 14f + 9f * feed; val ey = JY - 12f + 9f * feed
        wire.reset(); wire.moveTo(SPOOL_X + 16f, SPOOL_Y + 14f); wire.quadTo(118f, 214f, ex, ey)
        ink.color = Ink.SEPIA; ink.strokeWidth = 3.6f; c.drawPath(wire, ink)
        ink.color = 0xFFB8BEC4.toInt(); ink.strokeWidth = 2.4f; c.drawPath(wire, ink)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 170); ink.strokeWidth = .7f; c.drawPath(wire, ink)
        // La bobine tourne de ce que le fil a donné.
        s.draw(c, SPOOL, deg = (n + melt) * 34f, px = SPOOL_X, py = SPOOL_Y)
        // Le fer : posé pendant la soudure, levé pendant que la carte avance.
        val lift = if (u < MOVE) 1f else 1f - smooth(dwell / .1f) + smooth((dwell - .86f) / .14f)
        val tx = IX + 4f + 4f * lift; val ty = JY - 4f - 12f * lift
        ink.color = Ink.SEPIA; ink.strokeWidth = 16f
        c.drawLine(tx + DX * 40f, ty + DY * 40f, tx + DX * 170f, ty + DY * 170f, ink)
        ink.color = 0xFF2A4A8A.toInt(); ink.strokeWidth = 14f
        c.drawLine(tx + DX * 40f, ty + DY * 40f, tx + DX * 170f, ty + DY * 170f, ink)
        ink.color = withAlpha(0xFF8AA8E8.toInt(), 160); ink.strokeWidth = 3f
        c.drawLine(tx + DX * 44f - DY * 3f, ty + DY * 44f + DX * 3f, tx + DX * 166f - DY * 3f, ty + DY * 166f + DX * 3f, ink)
        ink.color = Ink.SEPIA; ink.strokeWidth = 7f; c.drawLine(tx + DX * 9f, ty + DY * 9f, tx + DX * 42f, ty + DY * 42f, ink)
        ink.color = 0xFFC8CCD0.toInt(); ink.strokeWidth = 5.4f; c.drawLine(tx + DX * 9f, ty + DY * 9f, tx + DX * 42f, ty + DY * 42f, ink)
        ink.color = Ink.SEPIA; ink.strokeWidth = 4f; c.drawLine(tx, ty, tx + DX * 10f, ty + DY * 10f, ink)
        ink.color = 0xFFD89A5A.toInt(); ink.strokeWidth = 2.6f; c.drawLine(tx, ty, tx + DX * 10f, ty + DY * 10f, ink)
        fill.color = withAlpha(0xFFFF8A3A.toInt(), 60 + (40 * sin(t * 5f)).toInt()); c.drawCircle(tx + DX * 3f, ty + DY * 3f, 5f, fill)
    }

    /** Une goutte d'étain sur la patte en [x] : [g] de 0 (rien) à 1 (cône lisse et brillant). */
    private fun bead(c: Canvas, x: Float, g: Float) {
        val r = 7f * g
        fill.color = 0xFF9AA0A8.toInt(); c.drawCircle(x, JY, r, fill)
        fill.color = 0xFFD8DCE2.toInt(); c.drawCircle(x - r * .15f, JY - r * .15f, r * .7f, fill)
        // Le bout de la patte dépasse au sommet du cône.
        fill.color = 0xFFB8BEC4.toInt(); c.drawCircle(x, JY, 1.4f, fill)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 230); c.drawCircle(x - r * .35f, JY - r * .35f, r * .25f + .3f, fill)
        ink.color = withAlpha(Ink.SEPIA, 200); ink.strokeWidth = .7f; c.drawCircle(x, JY, r, ink)
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** La carte : son bord, la rangée de pattes, l'écart entre deux pattes, une tranche de quatre. */
        const val BOARD_T = 160f
        const val JY = 236f
        const val SP = 34f
        const val TILE = 4 * SP
        /** La patte sous le fer ; la bobine. */
        const val IX = 196f
        const val SPOOL_X = 90f
        const val SPOOL_Y = 104f
        /** Un cran de carte : elle avance pendant [MOVE] (fraction), puis le fer soude. */
        const val STEP = 1.9f
        const val MOVE = .26f
        const val SMOKE = 14
        /** La direction du fer, de la pointe vers le manche. */
        const val DX = .55f
        const val DY = -.835f
        const val BOARD = 1
        const val SPOOL = 2
    }
}
