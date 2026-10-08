package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/**
 * Terbium — le tube fluorescent. Le vert de la lumière des tubes vient du terbium. Dans un garage
 * noir, le tube hésite, clignote, puis s'allume et éclaire l'établi. À la loupe, la vapeur dans le
 * tube jette des ultraviolets invisibles sur la poudre qui tapisse le verre, et chaque grain les
 * rend en lumière verte, rouge ou bleue.
 */
internal class SceneTerbium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_fluorescent_tube
    override val noteRes = R.string.card_note_fluorescent_tube
    override val explainRes = R.string.card_explain_fluorescent_tube

    override fun engrave(b: Burin) {
        // Le mur du garage et son panneau perforé.
        b.wash(rect(40f, 50f, 320f, 340f), 0xFFB0A894.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 250f), 90f, 3.6f, .4f, withAlpha(Ink.BROWN, 90))
        val board = rect(58f, 112f, 214f, 226f)
        b.body(board, 0xFFC89A60.toInt(), .15f, 0f, outline = .9f, washAlpha = 255)
        var y = 118f
        while (y < 224f) { var x = 64f; while (x < 212f) { b.c.drawCircle(x, y, 1f, b.pen(0xFF5A3A1A.toInt())); x += 9f }; y += 9f }
        // Les outils pendus : clé, marteau, tournevis, scie.
        b.body(limb(80f, 128f, 80f, 196f, 6f, 6f), 0xFFB8BEC6.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
        b.body(ellipse(80f, 126f, 9f, 9f), 0xFFB8BEC6.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
        b.body(ellipse(80f, 122f, 4f, 5f), 0xFFC89A60.toInt(), 0f, outline = .6f, washAlpha = 255)
        b.body(limb(112f, 140f, 112f, 206f, 5f, 6f), 0xFF8A5A2A.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
        b.body(rect(100f, 128f, 128f, 140f), 0xFF4A4E56.toInt(), .4f, 0f, outline = .7f, washAlpha = 255)
        b.body(limb(142f, 128f, 142f, 160f, 2.5f, 2.5f), 0xFFB8BEC6.toInt(), .2f, 90f, outline = .6f, washAlpha = 255)
        b.body(limb(142f, 160f, 142f, 196f, 9f, 8f), 0xFFC8281E.toInt(), .2f, 90f, outline = .7f, washAlpha = 255)
        b.body(poly(164f, 130f, 202f, 130f, 202f, 196f, 170f, 184f), 0xFFC8CCD2.toInt(), .25f, 90f, outline = .7f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(160f, 120f, 206f, 136f, 4f, 4f, Path.Direction.CW) }, 0xFF8A5A2A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        // L'établi et son étau.
        b.body(rect(40f, 250f, 320f, 266f), 0xFF8A6040.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(rect(40f, 266f, 320f, 340f), 0xFF5A4030.toInt(), .5f, 80f, outline = 0f, washAlpha = 255)
        b.body(rect(76f, 230f, 118f, 250f), 0xFF3A5A7A.toInt(), .35f, 0f, outline = .8f, washAlpha = 255)
        b.body(rect(90f, 222f, 104f, 232f), 0xFF4A4E56.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.line(118f, 240f, 136f, 240f, 2f, 0xFF8A8E94.toInt())
        // La réglette au plafond.
        b.body(rect(64f, 70f, 296f, 84f), 0xFFE0E0DC.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        b.body(rect(66f, 84f, 74f, 98f), 0xFF8A8E94.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.body(rect(286f, 84f, 294f, 98f), 0xFF8A8E94.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pool = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 98f, 0f, 300f, 0x60F0FFE8, 0x00F0FFE8, Shader.TileMode.CLAMP)
    }
    private val lens = Path().apply { addCircle(LX, LY, LR, Path.Direction.CW) }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        val slot = (t / .07f).toInt()
        val light = when {
            tc < 1f -> 0f
            tc < 2.4f -> if (hash(slot, 3) < .25f + .5f * (tc - 1f) / 1.4f) .85f else 0f
            tc < 8.5f -> 1f - .02f * sin(t * 40f)
            tc < 8.7f -> 1f - (tc - 8.5f) / .2f
            else -> 0f
        }
        val starting = if (tc in 1f..2.4f) 1f else 0f

        // La nuit du garage, que la lumière du tube chasse.
        fill.color = withAlpha(0xFF06080A.toInt(), (232 * (1f - light)).toInt())
        c.drawRect(40f, 50f, 320f, 340f, fill)
        if (light > 0f) {
            pool.alpha = (255 * light).toInt()
            c.drawRect(40f, 98f, 320f, 300f, pool)
        }
        // Le tube : gris éteint, blanc-vert allumé, ses bouts rougeoient au démarrage.
        fill.color = if (light > 0f) withAlpha(0xFFF4FFF2.toInt(), (120 + 135 * light).toInt()) else 0xFF7A7E84.toInt()
        c.drawRoundRect(74f, 86f, 286f, 96f, 5f, 5f, fill)
        if (light > 0f) {
            fill.color = withAlpha(0xFFE8FFE0.toInt(), (60 * light).toInt())
            c.drawRoundRect(68f, 80f, 292f, 102f, 11f, 11f, fill)
        }
        if (starting > 0f) {
            fill.color = withAlpha(0xFFFF8A3A.toInt(), 200)
            c.drawCircle(80f, 91f, 3f, fill); c.drawCircle(280f, 91f, 3f, fill)
        }

        // La loupe : la paroi du tube vue de très près.
        line.color = withAlpha(Gilding.LEAF, 220); line.strokeWidth = 1f
        c.drawLine(LX - 10f, LY - LR, 236f, 96f, line)
        c.save(); c.clipPath(lens)
        fill.color = 0xFF101418.toInt(); c.drawRect(LX - LR, LY - LR, LX + LR, LY + LR, fill)
        fill.color = withAlpha(0xFFB8E0E8.toInt(), 150); c.drawRect(LX - LR, LY - LR, LX + LR, LY - 22f, fill)
        for (k in 0 until GRAINS) {
            val gx = LX - LR + 4f + k * (2f * LR - 8f) / (GRAINS - 1)
            val gy = LY - 18f + 3f * hash(k, 1)
            val color = GRAIN[k % 3]
            val shine = light * (.6f + .4f * sin(t * (5f + 3f * hash(k, 2)) + k))
            if (shine > 0f) { fill.color = withAlpha(color, (90 * shine).toInt()); c.drawCircle(gx, gy, 6.5f, fill) }
            fill.color = if (shine > 0f) withAlpha(color, (140 + 115 * shine).toInt()) else 0xFFD8D8D0.toInt()
            c.drawCircle(gx, gy, 3f, fill)
        }
        // La vapeur de mercure jette ses ultraviolets vers la poudre.
        if (light > 0f) for (k in 0 until 7) {
            val ax = LX - 30f + k * 10f + 4f * hash(k, 5)
            val ay = LY + 18f + 10f * hash(k, 6)
            fill.color = withAlpha(0xFFC8C8FF.toInt(), (200 * light).toInt()); c.drawCircle(ax, ay, 1.6f, fill)
            val f = (t * 1.6f + hash(k, 7)) % 1f
            line.color = withAlpha(0xFFA05AFF.toInt(), (220 * light * (1f - f)).toInt()); line.strokeWidth = 1.2f
            val y0 = ay - 4f - 30f * f
            c.drawLine(ax, y0, ax + 1.5f * sin(k * 2f), max(LY - 14f, y0 - 7f), line)
        }
        c.restore()
        line.color = Gilding.LEAF; line.strokeWidth = 3f
        c.drawCircle(LX, LY, LR + 1.5f, line)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 10f
        const val LX = 262f
        const val LY = 176f
        const val LR = 38f
        const val GRAINS = 11
        val GRAIN = intArrayOf(0xFF3AFF6A.toInt(), 0xFFFF4A3A.toInt(), 0xFF4A8AFF.toInt())
    }
}
