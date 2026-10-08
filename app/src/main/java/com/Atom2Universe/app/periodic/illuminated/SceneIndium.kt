package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Indium — l'écran tactile. Sur la vitre de chaque écran tactile, une couche transparente
 * d'oxyde d'indium et d'étain conduit l'électricité : c'est une grille de fils invisibles qui
 * sent le doigt. Un téléphone posé sur le bureau, vu d'en haut ; un doigt touche une icône après
 * l'autre, et à chaque contact la grille cachée s'allume un instant, la ligne et la colonne qui
 * se croisent sous le doigt plus fort que les autres.
 */
internal class SceneIndium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_phone_touch
    override val noteRes = R.string.card_note_phone_touch
    override val explainRes = R.string.card_explain_phone_touch

    override fun engrave(b: Burin) {
        // Le bureau de bois, vu d'en haut.
        val desk = rect(40f, 50f, 320f, 340f)
        b.wash(desk, 0xFF9A6A44.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 8f, 3.2f, .45f, 0xFF5A3A22.toInt())
        b.washGradient(desk, 0xFF2A1A0E.toInt(), 0f, 50f, 20, 0f, 340f, 110)
        // Une tasse de café, un crayon.
        b.fill(ellipse(75f, 121f, 24f, 24f), withAlpha(0xFF2A1A0E.toInt(), 80))
        b.body(ellipse(72f, 118f, 23f, 23f), 0xFFF4F0E8.toInt(), .15f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(72f, 118f, 15f, 15f), 0xFFFFFFFF.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        b.c.drawCircle(72f, 118f, 11.5f, b.pen(0xFF4A2814.toInt()))
        b.c.drawCircle(68f, 114f, 3f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 90)))
        b.body(limb(87f, 118f, 96f, 118f, 6f, 6f), 0xFFF4F0E8.toInt(), .15f, 0f, outline = .8f, washAlpha = 255)
        b.body(limb(56f, 300f, 100f, 262f, 5f, 5f), 0xFFE8B828.toInt(), .25f, 0f, outline = .8f, washAlpha = 255)
        b.body(poly(56f, 300f, 50f, 308f, 59f, 304f), 0xFFE8C8A0.toInt(), 0f, outline = .6f, washAlpha = 255)
        // Le téléphone : son ombre, le boîtier, la vitre, l'écran d'accueil.
        b.fill(Path().apply { addRoundRect(PL + 6f, PT + 7f, PR + 6f, PB + 7f, 18f, 18f, Path.Direction.CW) }, withAlpha(0xFF1A0E06.toInt(), 110))
        val body = Path().apply { addRoundRect(PL, PT, PR, PB, 18f, 18f, Path.Direction.CW) }
        b.body(body, 0xFF1A1C20.toInt(), 0f, outline = 1.2f, washAlpha = 255)
        b.fill(rect(PR, 130f, PR + 2.5f, 156f), 0xFF3A3C42.toInt())
        b.fill(rect(PR, 166f, PR + 2.5f, 180f), 0xFF3A3C42.toInt())
        val screen = Path().apply { addRoundRect(SL, ST, SR, SB, 10f, 10f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, ST, 0f, SB,
            intArrayOf(0xFF3A6AB8.toInt(), 0xFF6A4AA8.toInt(), 0xFFC86A8A.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(screen, b.p)
        b.c.save(); b.c.clipPath(screen)
        b.fill(Path().apply { moveTo(SL, 230f); cubicTo(150f, 210f, 200f, 240f, SR, 214f); lineTo(SR, SB); lineTo(SL, SB); close() }, withAlpha(0xFF2A1A4A.toInt(), 140))
        // La barre d'état : la pile, le réseau.
        b.fill(rect(SR - 18f, ST + 5f, SR - 8f, ST + 10f), 0xFFFFFFFF.toInt())
        for (k in 0 until 4) b.fill(rect(SR - 34f + k * 3f, ST + 10f - k * 1.4f, SR - 32.4f + k * 3f, ST + 10f), 0xFFFFFFFF.toInt())
        // Les icônes, et le dock en bas.
        b.fill(Path().apply { addRoundRect(SL + 4f, DOCK_Y - 15f, SR - 4f, DOCK_Y + 15f, 10f, 10f, Path.Direction.CW) }, withAlpha(0xFFFFFFFF.toInt(), 60))
        for (i in 0 until ICONS) icon(b, i)
        b.fill(rect(160f, SB - 6f, 200f, SB - 4f), withAlpha(0xFFFFFFFF.toInt(), 200))
        b.c.restore()
        // Le reflet de la vitre, la caméra, le haut-parleur.
        b.fill(poly(SL, ST, SL + 50f, ST, SL, ST + 90f), withAlpha(0xFFFFFFFF.toInt(), 26))
        b.c.drawCircle(180f, PT + 8f, 2.4f, b.pen(0xFF3A4A6A.toInt()))
        b.stroke(screen, .8f)
        b.stroke(body, 1.2f)
    }

    /** L'icône [i] : une pastille de couleur et un signe simple, sans texte. */
    private fun icon(b: Burin, i: Int) {
        val x = iconX(i); val y = iconY(i)
        val square = Path().apply { addRoundRect(x - HALF, y - HALF, x + HALF, y + HALF, 5f, 5f, Path.Direction.CW) }
        b.fill(square, COLORS[i % COLORS.size])
        b.stroke(square, .6f, withAlpha(Ink.SEPIA, 120))
        val white = 0xFFFFFFFF.toInt()
        when (i % 5) {
            0 -> b.c.drawCircle(x, y, 4.4f, b.pen(white, 1.4f))
            1 -> b.fill(poly(x - 3.4f, y - 4.4f, x + 4.4f, y, x - 3.4f, y + 4.4f), white)
            2 -> { b.line(x - 4.4f, y - 2.6f, x + 4.4f, y - 2.6f, 1.3f, white); b.line(x - 4.4f, y, x + 4.4f, y, 1.3f, white); b.line(x - 4.4f, y + 2.6f, x + 2f, y + 2.6f, 1.3f, white) }
            3 -> b.fill(Path().apply { addRoundRect(x - 4.4f, y - 3.4f, x + 4.4f, y + 3.4f, 1.5f, 1.5f, Path.Direction.CW) }, white)
            else -> b.fill(poly(x, y - 4.6f, x + 4.6f, y + 3.6f, x - 4.6f, y + 3.6f), white)
        }
    }

    private fun iconX(i: Int) = if (i < GRID) SL + 15f + (i % 4) * 30f else SL + 15f + (i - GRID) * 30f
    private fun iconY(i: Int) = if (i < GRID) ST + 28f + (i / 4) * 33f else DOCK_Y

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val n = (t / TRIP).toInt()
        val u = t / TRIP - n
        val from = target(n - 1); val to = target(n)
        val m = smooth(u / MOVE)
        val x = iconX(from) + (iconX(to) - iconX(from)) * m
        val y = iconY(from) + (iconY(to) - iconY(from)) * m
        // Le doigt s'approche, appuie, se relève : la hauteur ne se voit qu'à son ombre.
        val lift = when {
            u < PRESS_IN -> 1f
            u < PRESS_IN + .06f -> 1f - (u - PRESS_IN) / .06f
            u < PRESS_OUT -> 0f
            u < PRESS_OUT + .1f -> (u - PRESS_OUT) / .1f
            else -> 1f
        }
        // Sous la vitre : la grille d'oxyde d'indium s'allume au contact, l'icône s'enfonce, une onde part.
        c.save(); c.clipRect(SL, ST, SR, SB)
        for (j in 1 downTo 0) {
            val k = n - j
            if (k < 0) continue
            val since = t - (k + PRESS_IN) * TRIP
            if (since < 0f || since > GLOW) continue
            val fade = 1f - since / GLOW
            val tx = iconX(target(k)); val ty = iconY(target(k))
            ink.color = withAlpha(0xFFB8F0FF.toInt(), (60 * fade).toInt()); ink.strokeWidth = .7f
            var gx = SL + 5f
            while (gx < SR) { c.drawLine(gx, ST, gx, SB, ink); gx += WIRE }
            var gy = ST + 5f
            while (gy < SB) { c.drawLine(SL, gy, SR, gy, ink); gy += WIRE }
            val cx = SL + 5f + ((tx - SL - 5f) / WIRE + .5f).toInt() * WIRE
            val cy = ST + 5f + ((ty - ST - 5f) / WIRE + .5f).toInt() * WIRE
            ink.color = withAlpha(0xFF6AE8FF.toInt(), (230 * fade).toInt()); ink.strokeWidth = 2f
            c.drawLine(cx, ST, cx, SB, ink); c.drawLine(SL, cy, SR, cy, ink)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (255 * fade).toInt()); c.drawCircle(cx, cy, 2.6f, fill)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (180 * fade).toInt()); ink.strokeWidth = 1.2f
            c.drawCircle(tx, ty, 6f + since * 46f, ink)
        }
        if (lift < 1f) {
            fill.color = withAlpha(0xFF000000.toInt(), (70 * (1f - lift)).toInt())
            c.drawRoundRect(iconX(to) - HALF, iconY(to) - HALF, iconX(to) + HALF, iconY(to) + HALF, 5f, 5f, fill)
        }
        c.restore()
        // Le doigt et la main droite, paume en bas, l'index tendu ; d'abord leur ombre sur la vitre,
        // peinte opaque dans un calque translucide pour qu'elle reste d'un seul ton.
        val shadow = 2f + 8f * lift
        c.saveLayerAlpha(x + shadow - 60f, y + shadow - 20f, x + shadow + 120f, y + shadow + 150f, 70)
        hand(c, x + shadow, y + shadow, true)
        c.restore()
        hand(c, x, y, false)
    }

    /** La main dont l'index touche ([x], [y]) ; en ombre, d'une seule teinte sombre et sans contour. */
    private fun hand(c: Canvas, x: Float, y: Float, shadow: Boolean) {
        val skin = if (shadow) 0xFF1A0E06.toInt() else SKIN
        // Les doigts repliés, à droite de l'index ; la paume ; le pouce à gauche ; l'index.
        for (k in 0 until 3) {
            val fx = x + DX * (68f + k * 6f) - NX * (14f + k * 11f)
            val fy = y + DY * (68f + k * 6f) - NY * (14f + k * 11f)
            disc(c, fx, fy, 9.5f - k * .8f, skin, shadow)
        }
        disc(c, x + DX * 98f - NX * 14f, y + DY * 98f - NY * 14f, 30f, skin, shadow)
        stick(c, x + DX * 92f + NX * 26f, y + DY * 92f + NY * 26f, x + DX * 66f + NX * 18f, y + DY * 66f + NY * 18f, 12f, skin, shadow)
        stick(c, x + DX * 4f, y + DY * 4f, x + DX * 74f, y + DY * 74f, 15f, skin, shadow)
        if (shadow) return
        // L'ongle et les plis des phalanges.
        c.save(); c.rotate(ANGLE, x + DX * 9f, y + DY * 9f)
        fill.color = 0xFFF0C8B8.toInt(); c.drawOval(x + DX * 9f - 4f, y + DY * 9f - 5.6f, x + DX * 9f + 4f, y + DY * 9f + 5.6f, fill)
        ink.color = withAlpha(Ink.SEPIA, 180); ink.strokeWidth = .7f
        c.drawOval(x + DX * 9f - 4f, y + DY * 9f - 5.6f, x + DX * 9f + 4f, y + DY * 9f + 5.6f, ink)
        c.restore()
        ink.color = withAlpha(0xFF8A5040.toInt(), 170); ink.strokeWidth = .8f
        for (k in 0..1) {
            val d = 26f + 20f * k
            c.drawLine(x + DX * d - NX * 4f, y + DY * d - NY * 4f, x + DX * d + NX * 4f, y + DY * d + NY * 4f, ink)
        }
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int, shadow: Boolean) {
        fill.color = color; c.drawCircle(x, y, r, fill)
        if (!shadow) { ink.color = Ink.SEPIA; ink.strokeWidth = .9f; c.drawCircle(x, y, r, ink) }
    }

    private fun stick(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int, shadow: Boolean) {
        if (!shadow) { ink.color = Ink.SEPIA; ink.strokeWidth = w + 1.8f; c.drawLine(x0, y0, x1, y1, ink) }
        ink.color = color; ink.strokeWidth = w; c.drawLine(x0, y0, x1, y1, ink)
    }

    /** L'icône touchée au passage [n] ; jamais deux fois la même de suite. */
    private fun target(n: Int): Int {
        val a = (hash(n, 1) * ICONS).toInt().coerceAtMost(ICONS - 1)
        val b = (hash(n - 1, 1) * ICONS).toInt().coerceAtMost(ICONS - 1)
        return if (a == b) (a + 7) % ICONS else a
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** Le téléphone et son écran. */
        const val PL = 112f
        const val PR = 248f
        const val PT = 70f
        const val PB = 318f
        const val SL = 120f
        const val SR = 240f
        const val ST = 86f
        const val SB = 302f
        const val DOCK_Y = 280f
        const val GRID = 20
        const val ICONS = 24
        const val HALF = 9.5f
        /** L'écart des fils de la grille transparente. */
        const val WIRE = 10f
        /** Un appui : le trajet, puis le doigt posé entre [PRESS_IN] et [PRESS_OUT] (fractions du trajet). */
        const val TRIP = 1.7f
        const val MOVE = .34f
        const val PRESS_IN = .44f
        const val PRESS_OUT = .7f
        const val GLOW = .8f
        /** La direction du doigt (de la pointe vers la main) et sa normale. */
        val DX = .42f / hypot(.42f, 1f)
        val DY = 1f / hypot(.42f, 1f)
        val NX = -DY
        val NY = DX
        val ANGLE = Math.toDegrees(atan2(DY, DX).toDouble()).toFloat() - 90f
        const val SKIN = 0xFFE8B898.toInt()
        val COLORS = intArrayOf(
            0xFF3AA85A.toInt(), 0xFFE8503A.toInt(), 0xFF3A7AE8.toInt(), 0xFFF0B02A.toInt(), 0xFF8A4AD8.toInt(),
            0xFF2AB8C8.toInt(), 0xFFE84A8A.toInt(), 0xFF5A5A6A.toInt(), 0xFFF07A2A.toInt()
        )
    }
}
