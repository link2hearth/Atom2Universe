package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * L'accélérateur d'ions, commun aux éléments qu'on ne fabrique qu'atome par atome. Des paquets
 * d'ions filent dans l'anneau d'aimants, un paquet est dévié vers la cible ; à la loupe, un ion
 * frappe un gros noyau, fusionne avec lui, le nouveau noyau tremble, jette des neutrons, puis se
 * défait en crachant des particules alpha. Seule la légende change d'un élément à l'autre.
 */
internal class SceneAccelerator(
    override val noteRes: Int,
    override val nameRes: Int = R.string.card_scene_ion_accelerator,
    override val explainRes: Int = R.string.card_explain_ion_accelerator
) : EngravedScene() {

    override val sky = Sky.NONE

    override fun engrave(b: Burin) {
        // Le hall : mur sombre, chemins de câbles, sol de béton en perspective.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 160f,
            0xFF0C111A.toInt(), 0xFF18202C.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 160f, b.p)
        for (y in floatArrayOf(96f, 112f)) {
            b.line(40f, y, 320f, y, 2.2f, 0xFF3A4250.toInt())
            var x = 44f
            while (x < 320f) { b.line(x, y - 4f, x, y, .7f, 0xFF4A5262.toInt()); x += 14f }
        }
        val floor = rect(40f, 150f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 150f, 0f, 340f,
            0xFF3A404A.toInt(), 0xFF5A606A.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(floor, b.p)
        for (k in -6..6) b.line(180f + k * 14f, 150f, 180f + k * 70f, 340f, .6f, withAlpha(0xFF20242C.toInt(), 160))
        for (y in floatArrayOf(166f, 188f, 218f, 258f, 310f)) b.line(40f, y, 320f, y, .6f, withAlpha(0xFF20242C.toInt(), 160))
        b.line(40f, 150f, 320f, 150f, 1.4f, 0xFF20242C.toInt())

        // L'anneau : le tube, puis les aimants (bleus pour courber, rouges pour serrer le faisceau).
        b.c.drawOval(RX0 - RX, RY0 - RY, RX0 + RX, RY0 + RY, b.pen(0xFF9AA2AE.toInt(), 3f))
        magnets(b, false)
        magnets(b, true)
        // La ligne d'extraction jusqu'à la chambre de la cible.
        b.line(EX0, EY0, CHX - 14f, CHY + 2f, 3f, 0xFF9AA2AE.toInt())
        for (f in floatArrayOf(.35f, .65f)) {
            val x = EX0 + (CHX - 14f - EX0) * f; val y = EY0 + (CHY + 2f - EY0) * f
            b.body(Path().apply { addRoundRect(x - 5f, y - 7f, x + 5f, y + 7f, 1.5f, 1.5f, Path.Direction.CW) }, 0xFFC03A2A.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
        }
        val chamber = Path().apply { addRoundRect(CHX - 16f, CHY - 18f, CHX + 16f, CHY + 18f, 6f, 6f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(CHX - 16f, 0f, CHX + 16f, 0f,
            intArrayOf(0xFF6A707A.toInt(), 0xFFD8DCE2.toInt(), 0xFF4A505A.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(chamber, b.p)
        b.stroke(chamber, .9f)
        b.line(CHX - 10f, CHY + 18f, CHX - 10f, CHY + 30f, 1.6f, 0xFF4A4E58.toInt())
        b.line(CHX + 10f, CHY + 18f, CHX + 10f, CHY + 30f, 1.6f, 0xFF4A4E58.toInt())
        b.body(ellipse(CHX, CHY, 7f, 7f), 0xFF101820.toInt(), 0f, outline = .8f, washAlpha = 255)

        // La loupe dorée, reliée à la cible.
        b.c.drawLine(CHX - 4f, CHY - 18f, LX + 30f, LY + 36f, b.pen(withAlpha(Gilding.LEAF, 200), 1f).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        })
        b.pen(0xFF000000.toInt()).shader = RadialGradient(LX, LY, LR,
            intArrayOf(0xFF1A2438.toInt(), 0xFF0A0E18.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawCircle(LX, LY, LR, b.p)
        b.c.save(); b.c.clipPath(ellipse(LX, LY, LR, LR))
        b.hatchRect(RectF(LX - LR, LY - LR, LX + LR, LY + LR), 0f, 8f, .4f, withAlpha(0xFF6A8AC0.toInt(), 50))
        b.hatchRect(RectF(LX - LR, LY - LR, LX + LR, LY + LR), 90f, 8f, .4f, withAlpha(0xFF6A8AC0.toInt(), 50))
        b.c.restore()
        b.goldStroke(ellipse(LX, LY, LR + 2f, LR + 2f), 3.2f)
        b.stroke(ellipse(LX, LY, LR + 3.8f, LR + 3.8f), .6f)
    }

    private fun magnets(b: Burin, front: Boolean) {
        for (k in 0 until 16) {
            val a = k * TWO_PI / 16f + .1f
            val s = sin(a)
            if ((s >= 0f) != front) continue
            val x = RX0 + RX * cos(a); val y = RY0 + RY * s
            val scale = .75f + .25f * (s + 1f) / 2f
            val dipole = k % 2 == 0
            val w = (if (dipole) 24f else 12f) * scale * (.55f + .45f * abs(s))
            val h = 16f * scale
            b.line(x - w * .3f, y + h * .5f, x - w * .3f, y + h * .5f + 10f * scale, 1.4f * scale, 0xFF4A4E58.toInt())
            b.line(x + w * .3f, y + h * .5f, x + w * .3f, y + h * .5f + 10f * scale, 1.4f * scale, 0xFF4A4E58.toInt())
            b.body(Path().apply { addRoundRect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, 2f, 2f, Path.Direction.CW) },
                if (dipole) 0xFF2A5AA8.toInt() else 0xFFC03A2A.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
            b.wash(rect(x - w / 2f, y - h / 2f, x + w / 2f, y - h / 2f + 3f * scale), Ink.WHITE, 70)
        }
    }

    override fun sprites() = listOf(
        Sprite(TARGET, RectF(LX - 16f, LY - 22f, LX + 28f, LY + 22f)) { b -> cluster(b, LX + 6f, LY, 34, 16f, 3) },
        Sprite(ION, RectF(LX - 52f, LY - 12f, LX - 28f, LY + 12f)) { b -> cluster(b, LX - 40f, LY, 8, 7f, 9) },
        Sprite(FUSED, RectF(LX - 20f, LY - 24f, LX + 28f, LY + 24f)) { b -> cluster(b, LX + 4f, LY, 42, 18f, 5) },
        Sprite(ALPHA, RectF(LX - 7f, LY - 7f, LX + 7f, LY + 7f)) { b -> cluster(b, LX, LY, 4, 4f, 2) }
    )

    /** Un noyau : des billes rouges (protons) et grises (neutrons) serrées en spirale. */
    private fun cluster(b: Burin, cx: Float, cy: Float, n: Int, radius: Float, seed: Int) {
        val r = radius * 1.05f / sqrt(n.toFloat()) + 1.4f
        for (i in n - 1 downTo 0) {
            val d = radius * sqrt((i + .5f) / n)
            val a = i * 2.3999631f
            val x = cx + d * cos(a); val y = cy + d * sin(a)
            val proton = if (n == 4) i % 2 == 0 else hash(i, seed) > .5f
            val base = if (proton) 0xFFD8402E.toInt() else 0xFF9AA0AA.toInt()
            b.pen(base).shader = RadialGradient(x - r * .35f, y - r * .4f, r * 1.3f,
                intArrayOf(Ink.WHITE, base, withAlpha(0xFF000000.toInt(), 255)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(x, y, r, b.p)
        }
    }

    // ───────────── animation ─────────────

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val lens = ellipse(LX, LY, LR, LR)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val cycle = (t / CYCLE).toInt()
        val u = t - cycle * CYCLE
        // Les paquets d'ions font le tour de l'anneau.
        for (k in 0 until 5) {
            val a = -t * OMEGA + k * TWO_PI / 5f
            val sa = sin(a)
            val x = RX0 + RX * cos(a); val y = RY0 + RY * sa
            val sc = .8f + .2f * sa
            glow.color = withAlpha(BEAM, 70); c.drawCircle(x, y, 6f * sc, glow)
            glow.color = 0xFFE8FBFF.toInt(); c.drawCircle(x, y, 2f * sc, glow)
        }
        // Un paquet part vers la cible ; éclair dans la chambre.
        if (u < .5f) {
            val p = u / .5f
            val x = EX0 + (CHX - 14f - EX0) * p; val y = EY0 + (CHY + 2f - EY0) * p
            glow.color = withAlpha(BEAM, 90); c.drawCircle(x, y, 6f, glow)
            glow.color = 0xFFE8FBFF.toInt(); c.drawCircle(x, y, 2.2f, glow)
        }
        val hit = max(0f, 1f - abs(u - .55f) / .2f)
        if (hit > 0f) {
            glow.color = withAlpha(0xFFFFF0C0.toInt(), (200 * hit).toInt()); c.drawCircle(CHX, CHY, 4f + 10f * hit, glow)
        }

        // À la loupe : la fusion, puis la désintégration.
        c.save(); c.clipPath(lens)
        val merge = smooth((u - .5f) / .1f)
        val reset = smooth((u - 5.2f) / .8f)
        s.draw(c, TARGET, alpha = (255 * max(1f - merge, reset)).toInt())
        if (u < .56f) s.draw(c, ION, dx = 26f * (u / .5f).coerceAtMost(1f), alpha = (255 * (1f - smooth((u - .46f) / .1f))).toInt())
        val since = max(0f, u - .5f)
        val shake = 10f * sin(since * 14f) * exp(-since * 1.2f)
        val fused = (255 * merge * (1f - reset)).toInt()
        if (fused > 0) s.draw(c, FUSED, deg = shake, px = LX + 4f, py = LY, alpha = fused)
        val flash = max(0f, 1f - abs(u - .52f) / .12f)
        if (flash > 0f) { glow.color = withAlpha(Ink.WHITE, (230 * flash).toInt()); c.drawCircle(LX - 6f, LY, 6f + 16f * flash, glow) }
        // Deux neutrons s'échappent.
        if (u > .8f && u < 2.2f) {
            val e = u - .8f
            glow.color = withAlpha(0xFFB0B6C0.toInt(), (255 * (1f - e / 1.4f)).toInt())
            c.drawCircle(LX + 4f + 50f * e * .64f, LY - 50f * e * .77f, 2.6f, glow)
            c.drawCircle(LX + 4f - 50f * e * .87f, LY + 50f * e * .5f, 2.6f, glow)
        }
        // Deux particules alpha, l'une après l'autre, avec leur traînée.
        emit(c, s, u - 3f, .8f, -.6f)
        emit(c, s, u - 4.4f, -.5f, .87f)
        c.restore()
    }

    private fun emit(c: Canvas, s: Sprites, e: Float, dx: Float, dy: Float) {
        if (e <= 0f || e >= 1.2f) return
        val d = 16f + 60f * e
        val fade = 1f - smooth((e - .8f) / .4f)
        trail.color = withAlpha(0xFFFFD050.toInt(), (160 * fade).toInt()); trail.strokeWidth = 1.6f
        c.drawLine(LX + 4f + dx * 14f, LY + dy * 14f, LX + 4f + dx * d, LY + dy * d, trail)
        s.draw(c, ALPHA, LX + 4f + dx * d - LX, dy * d, alpha = (255 * fade).toInt())
        val flash = max(0f, 1f - e / .15f)
        if (flash > 0f) { glow.color = withAlpha(0xFFFFE890.toInt(), (220 * flash).toInt()); c.drawCircle(LX + 4f + dx * 18f, LY + dy * 18f, 4f + 8f * flash, glow) }
    }

    private fun smooth(x: Float): Float {
        val k = x.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 6f
        const val OMEGA = 3.9f
        const val RX0 = 150f
        const val RY0 = 200f
        const val RX = 96f
        const val RY = 40f
        const val EX0 = 198f
        const val EY0 = 234.6f
        const val CHX = 290f
        const val CHY = 214f
        const val LX = 232f
        const val LY = 120f
        const val LR = 46f
        const val TARGET = 1
        const val ION = 2
        const val FUSED = 3
        const val ALPHA = 4
        const val BEAM = 0xFF7AD8FF.toInt()
        const val TWO_PI = 6.2831855f
    }
}
