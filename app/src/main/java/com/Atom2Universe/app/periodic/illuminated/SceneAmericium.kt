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
 * Américium — le détecteur de fumée. Une pincée d'américium rend l'air conducteur dans la chambre
 * du détecteur ; la fumée qui y entre coupe ce petit courant, et l'alarme sonne. Dans la cuisine,
 * la tartine brûlée saute du grille-pain, la fumée monte jusqu'au plafond, le détecteur clignote
 * en rouge et sonne ; à la loupe, les particules de l'américium dans la chambre, que la fumée
 * vient gêner.
 */
internal class SceneAmericium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_smoke_detector
    override val noteRes = R.string.card_note_smoke_detector
    override val explainRes = R.string.card_explain_smoke_detector

    override fun engrave(b: Burin) {
        // Le plafond, le mur carrelé, le plan de travail.
        b.body(rect(40f, 50f, 320f, CEIL), 0xFFF0ECE2.toInt(), .15f, 0f, outline = 0f, washAlpha = 255)
        b.wash(rect(40f, CEIL, 320f, 286f), 0xFFE8E0C8.toInt(), 255)
        var y = CEIL + 60f
        while (y < 286f) { b.line(40f, y, 320f, y, .6f, 0xFFB8AE94.toInt()); y += 18f }
        var x = 46f
        while (x < 320f) { b.line(x, CEIL + 60f, x, 286f, .6f, 0xFFB8AE94.toInt()); x += 18f }
        b.line(40f, CEIL, 320f, CEIL, 1.2f)
        b.body(rect(40f, 286f, 320f, 300f), 0xFF8A6A48.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(rect(40f, 300f, 320f, 340f), 0xFFC8B898.toInt(), .3f, 90f, outline = 0f, washAlpha = 255)
        // Le détecteur au plafond : un dôme blanc ajouré.
        b.body(Path().apply { moveTo(DX - 28f, CEIL); quadTo(DX - 26f, CEIL + 16f, DX, CEIL + 16f); quadTo(DX + 26f, CEIL + 16f, DX + 28f, CEIL); close() }, 0xFFF8F6F0.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        for (k in 0 until 7) b.line(DX - 18f + k * 6f, CEIL + 4f, DX - 18f + k * 6f, CEIL + 10f, 1f, 0xFF8A8478.toInt())
        // Le grille-pain chromé et son levier.
        val toaster = Path().apply { addRoundRect(TX - 40f, 244f, TX + 40f, 288f, 10f, 10f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(TX - 40f, 0f, TX + 40f, 0f,
            intArrayOf(0xFF8A8E94.toInt(), 0xFFF0F2F4.toInt(), 0xFF7A7E84.toInt()), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(toaster, b.p)
        b.stroke(toaster, 1f)
        b.body(rect(TX + 40f, 256f, TX + 46f, 262f), 0xFF2A2A2E.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        // La loupe sur la chambre du détecteur : deux plaques et la pastille d'américium.
        b.c.drawLine(DX + 20f, CEIL + 14f, LX - 28f, LY - 28f, b.pen(withAlpha(Gilding.LEAF, 220), 1f))
        b.c.save(); b.c.clipPath(ellipse(LX, LY, LR, LR))
        b.wash(rect(LX - LR, LY - LR, LX + LR, LY + LR), 0xFF1A1E24.toInt(), 255)
        b.body(rect(LX - 30f, LY - 26f, LX + 30f, LY - 20f), 0xFFB87333.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.body(rect(LX - 30f, LY + 20f, LX + 30f, LY + 26f), 0xFFB87333.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.body(rect(LX - 6f, LY + 14f, LX + 6f, LY + 20f), 0xFF9AA0A8.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        b.c.restore()
        b.goldStroke(ellipse(LX, LY, LR + 1.5f, LR + 1.5f), 3f)
    }

    override fun sprites() = listOf(
        // La tartine, brûlée sur les bords.
        Sprite(TOAST, RectF(TX - 26f, 226f, TX + 26f, 262f)) { b ->
            val bread = Path().apply { addRoundRect(TX - 24f, 228f, TX + 24f, 262f, 8f, 8f, Path.Direction.CW) }
            b.body(bread, 0xFF6A3A1A.toInt(), .4f, 30f, outline = .8f, washAlpha = 255)
            b.wash(Path().apply { addRoundRect(TX - 18f, 233f, TX + 18f, 262f, 6f, 6f, Path.Direction.CW) }, 0xFF2A1408.toInt(), 170)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val lens = ellipse(LX, LY, LR, LR)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        // La tartine saute, puis redescend à la fin.
        val up = when {
            tc < 1f -> 0f
            tc < 1.3f -> 22f * sin((tc - 1f) / .3f * 1.5707964f)
            tc < 9f -> 22f
            tc < 9.6f -> 22f * (1f - (tc - 9f) / .6f)
            else -> 0f
        }
        c.save(); c.clipRect(40f, 50f, 320f, 246f)
        s.draw(c, TOAST, 0f, 20f - up)
        c.restore()
        // Les bouffées de fumée montent et s'étalent sous le plafond.
        for (k in 0 until PUFFS) {
            val age = tc - (1.2f + k * .24f)
            if (age < 0f || age > 4f) continue
            val rise = 60f * age
            val y = max(CEIL + 12f + 4f * hash(k, 1), 236f - rise)
            val spread = max(0f, rise - (236f - CEIL - 12f)) * .8f
            val x = TX + (hash(k, 2) - .5f) * (20f + spread * 2f) + 6f * sin(age * 2f + k)
            val a = (110 * sin(age / 4f * 3.1415927f)).toInt()
            fill.color = withAlpha(0xFF5A5A5E.toInt(), a)
            c.drawCircle(x, y, 7f + 4f * age, fill)
        }
        // Le témoin : vert de temps en temps ; rouge et sonnerie quand la fumée arrive.
        val alarm = tc in 3.8f..8.4f
        if (alarm) {
            val on = sin(t * 18f) > 0f
            fill.color = if (on) 0xFFFF2A1A.toInt() else 0xFF5A1010.toInt()
            c.drawCircle(DX + 14f, CEIL + 12f, 2.4f, fill)
            line.strokeWidth = 1.4f
            for (k in 0 until 3) {
                val f = (t * 1.5f + k / 3f) % 1f
                line.color = withAlpha(Ink.RUBRIC, (200 * (1f - f)).toInt())
                c.drawArc(DX - 34f - 30f * f, CEIL - 20f - 20f * f, DX + 34f + 30f * f, CEIL + 30f + 20f * f, 20f, 140f, false, line)
            }
        } else {
            fill.color = if ((t % 2f) < .12f) 0xFF3AD84A.toInt() else 0xFF1A4A1A.toInt()
            c.drawCircle(DX + 14f, CEIL + 12f, 2.4f, fill)
        }
        // À la loupe : l'américium jette ses particules, qui rendent l'air conducteur ; la fumée les arrête.
        val smoke = smooth((tc - 3.2f) / .8f) * (1f - smooth((tc - 8f) / 1f))
        c.save(); c.clipPath(lens)
        for (k in 0 until 8) {
            val f = (t * 2.2f + k / 8f) % 1f
            val dir = (hash(k, 3) - .5f) * 1.6f
            val reach = 34f * (1f - .7f * smoke)
            if (f * 34f > reach) continue
            line.color = withAlpha(0xFFFFE070.toInt(), (230 * (1f - f)).toInt()); line.strokeWidth = 1.4f
            val x0 = LX + dir * f * 30f; val y0 = LY + 14f - f * 34f
            c.drawLine(x0, y0, x0 - dir * 4f, y0 + 4f, line)
        }
        for (k in 0 until 10) {
            val f = (t * .7f + k / 10f) % 1f
            val ion = k % 2 == 0
            val x = LX - 24f + 48f * hash(k, 5)
            val y = if (ion) LY - 4f - 14f * f else LY + 4f + 14f * f
            fill.color = withAlpha(if (ion) 0xFFFF6A5A.toInt() else 0xFF5A9AFF.toInt(), (220 * (1f - smoke)).toInt())
            c.drawCircle(x, y, 1.8f, fill)
        }
        if (smoke > 0f) for (k in 0 until 6) {
            fill.color = withAlpha(0xFF8A8A8E.toInt(), (180 * smoke).toInt())
            c.drawCircle(LX - 22f + k * 9f + 3f * sin(t * 1.3f + k), LY - 2f + 8f * sin(t * .9f + k * 2f), 5f, fill)
        }
        c.restore()
    }

    private fun smooth(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 10f
        const val CEIL = 96f
        const val DX = 150f
        const val TX = 140f
        const val LX = 262f
        const val LY = 170f
        const val LR = 36f
        const val PUFFS = 18
        const val TOAST = 1
    }
}
