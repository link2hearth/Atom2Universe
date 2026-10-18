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

/**
 * Molybdène — la perceuse. Quelques pour cent de molybdène font l'« acier rapide » des forets :
 * il reste dur même chauffé au rouge par la coupe. Sous la perceuse à colonne, le foret tourne
 * et s'enfonce dans un bloc d'acier serré dans l'étau ; il remonte de temps en temps pour
 * dégager, et le long copeau en spirale se casse et tombe.
 */
internal class SceneMolybdenum : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_drill_press
    override val noteRes = R.string.card_note_drill_press
    override val explainRes = R.string.card_explain_drill_press

    override fun engrave(b: Burin) {
        // Le mur de l'atelier et son panneau à outils.
        val wall = rect(40f, 50f, 320f, BENCH)
        b.wash(wall, 0xFFB8B0A0.toInt(), 255)
        b.washGradient(wall, 0xFF2A241C.toInt(), 0f, 60f, 40, 0f, BENCH, 150)
        val board = rect(236f, 70f, 312f, 210f)
        b.body(board, 0xFFC8A878.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
        var y = 78f
        while (y < 206f) { var x = 242f; while (x < 310f) { b.c.drawCircle(x, y, .9f, b.pen(0xFF5A4A34.toInt())); x += 8f }; y += 8f }
        b.body(limb(250f, 90f, 254f, 150f, 6f, 5f), 0xFF8A9096.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(252f, 86f, 8f, 8f), 0xFF8A9096.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.fill(ellipse(252f, 82f, 3f, 5f), 0xFFC8A878.toInt())
        b.body(limb(284f, 96f, 284f, 160f, 5f, 5f), 0xFFC83A2A.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(limb(284f, 150f, 284f, 196f, 3f, 3f), 0xFF8A9096.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // L'établi.
        val bench = rect(40f, BENCH, 320f, 340f)
        b.wash(bench, 0xFF6A4A2E.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH, 320f, 340f), 0f, 2.2f, .45f, Ink.SEPIA, Fade(0f, BENCH, 0f, 330f, 70, 210))
        // La perceuse : colonne, tête, poignées de descente, moteur.
        b.body(rect(76f, 60f, 92f, BENCH), 0xFF4A6A5A.toInt(), .35f, 0f, Fade(92f, 0f, 76f, 0f, 220, 30), outline = 1f, washAlpha = 255)
        b.c.drawLine(80f, 60f, 80f, BENCH, b.pen(withAlpha(0xFFFFFFFF.toInt(), 120), 1.4f))
        val head = Path().apply { addRoundRect(70f, 64f, 214f, 122f, 10f, 10f, Path.Direction.CW) }
        b.body(head, 0xFF4A6A5A.toInt(), .35f, 0f, Fade(0f, 122f, 0f, 64f, 200, 30), outline = 1.1f, washAlpha = 255)
        b.line(74f, 70f, 210f, 70f, 1.2f, withAlpha(0xFFFFFFFF.toInt(), 110))
        b.body(rect(DX - 16f, 122f, DX + 16f, 132f), 0xFF3A5A4A.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(214f, 100f, 6f, 6f), 0xFF2A2C30.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        // La table de la perceuse, l'étau, le bloc d'acier.
        b.body(rect(60f, 280f, 240f, 290f), 0xFF4A6A5A.toInt(), .35f, 0f, outline = 1f, washAlpha = 255)
        val vise = poly(110f, 252f, 250f, 252f, 250f, 280f, 110f, 280f)
        b.body(vise, 0xFF3A5A8A.toInt(), .4f, 0f, Fade(0f, 280f, 0f, 252f, 220, 40), outline = 1f, washAlpha = 255)
        b.body(rect(118f, 226f, 132f, 252f), 0xFF3A5A8A.toInt(), .35f, 0f, outline = .9f, washAlpha = 255)
        b.body(rect(228f, 226f, 242f, 252f), 0xFF3A5A8A.toInt(), .35f, 0f, outline = .9f, washAlpha = 255)
        b.line(242f, 266f, 296f, 266f, 3f, 0xFF8A9096.toInt())
        b.body(ellipse(298f, 266f, 4f, 6f), 0xFF2A2C30.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        val block = rect(132f, BLOCK_TOP, 228f, 252f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(132f, 0f, 228f, 0f,
            intArrayOf(0xFF8A929C.toInt(), 0xFFD0D6DC.toInt(), 0xFF7A828C.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(block, b.p)
        b.hatch(block, 0f, 2f, .35f, withAlpha(0xFF4A525C.toInt(), 150))
        b.stroke(block, 1f)
        // Le trou, une ombre sur le dessus du bloc.
        b.fill(ellipse(DX, BLOCK_TOP, 7f, 2f), 0xFF2A2C30.toInt())
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val steel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(DX - 6f, 0f, DX + 6f, 0f,
            intArrayOf(0xFF6A7078.toInt(), 0xFFE8ECF0.toInt(), 0xFF9AA2AA.toInt(), 0xFF4A5058.toInt()), floatArrayOf(0f, .35f, .65f, 1f), Shader.TileMode.CLAMP)
    }
    private val chip = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        // Descente lente en coupant ; puis le foret ressort du trou pour dégager, et revient.
        val depth = if (ph < CUT) 14f * ph / CUT else {
            val u = (ph - CUT) / (CYCLE - CUT)
            if (u < .5f) 14f - 26f * smooth(u * 2f) else -12f + 12f * smooth(u * 2f - 1f)
        }
        val spin = t * 18f
        val tip = BLOCK_TOP + depth
        // Le mandrin et ses mors.
        val chuckTop = 140f + depth
        fill.color = 0xFF9AA2AA.toInt()
        c.drawRect(DX - 13f, 130f, DX + 13f, chuckTop + 8f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = 1f
        c.drawRect(DX - 13f, 130f, DX + 13f, chuckTop + 8f, ink)
        chip.reset()
        chip.moveTo(DX - 13f, chuckTop + 8f); chip.lineTo(DX + 13f, chuckTop + 8f)
        chip.lineTo(DX + 7f, chuckTop + 30f); chip.lineTo(DX - 7f, chuckTop + 30f); chip.close()
        c.drawPath(chip, steel); c.drawPath(chip, ink)
        // La clé du mandrin qui tourne : un repère qui passe.
        val mark = sin(spin)
        if (cos(spin) > 0f) {
            fill.color = 0xFF3A3E44.toInt()
            c.drawRect(DX + mark * 10f - 1.4f, chuckTop + 12f, DX + mark * 10f + 1.4f, chuckTop + 22f, fill)
        }
        // Le foret : queue lisse, partie hélicoïdale dont les goujures défilent, pointe ; ce qui est
        // entré dans le bloc ne se voit plus.
        val top = chuckTop + 30f
        c.save(); c.clipRect(40f, 50f, 320f, BLOCK_TOP)
        c.drawRect(DX - 5f, top, DX + 5f, tip - 3f, steel)
        chip.reset()
        chip.moveTo(DX - 5f, tip - 3f); chip.lineTo(DX + 5f, tip - 3f); chip.lineTo(DX, tip); chip.close()
        c.drawPath(chip, steel)
        c.save(); c.clipRect(DX - 5f, top + 18f, DX + 5f, tip)
        val pitch = 14f
        val shift = (spin / (2f * PI.toFloat()) * pitch) % pitch
        var y = top + 18f - pitch + shift
        ink.color = withAlpha(0xFF2A2E34.toInt(), 220); ink.strokeWidth = 3.4f
        while (y < tip + pitch) {
            c.drawLine(DX - 6f, y, DX + 6f, y - 7f, ink)
            y += pitch
        }
        c.restore()
        ink.color = Ink.SEPIA; ink.strokeWidth = .9f
        c.drawLine(DX - 5f, top, DX - 5f, tip - 3f, ink); c.drawLine(DX + 5f, top, DX + 5f, tip - 3f, ink)
        c.drawLine(DX - 5f, tip - 3f, DX, tip, ink); c.drawLine(DX + 5f, tip - 3f, DX, tip, ink)
        c.restore()
        // Le copeau : une spirale qui sort du trou et s'allonge pendant la coupe ; à la remontée,
        // il se casse et tombe sur l'établi.
        val grow = if (ph < CUT) ph / CUT else 1f
        val fall = if (ph < CUT) 0f else (ph - CUT) / (CYCLE - CUT)
        val turns = 1f + 4f * grow
        chip.reset()
        val baseX = DX + 9f + fall * 34f
        val baseY = BLOCK_TOP - 2f + fall * fall * 80f
        val steps = (turns * 16f).toInt()
        for (i in 0..steps) {
            val u = i / 16f
            val a = u * 2f * PI.toFloat() + spin * .2f * (1f - fall)
            val r = 3f + u * 2.2f
            val x = baseX + r * cos(a)
            val yy = baseY - u * 5f + r * .35f * sin(a)
            if (i == 0) chip.moveTo(x, yy) else chip.lineTo(x, yy)
        }
        // Il s'efface en tombant : le suivant naît déjà au bord du trou.
        val fade = 1f - fall
        ink.color = withAlpha(0xFF5A4A3A.toInt(), (255 * fade).toInt()); ink.strokeWidth = 2.6f; c.drawPath(chip, ink)
        ink.color = withAlpha(0xFFD8A060.toInt(), (255 * fade).toInt()); ink.strokeWidth = 1.4f; c.drawPath(chip, ink)
        ink.color = withAlpha(0xFFFFE8C8.toInt(), (200 * fade).toInt()); ink.strokeWidth = .5f; c.drawPath(chip, ink)
        // Un filet d'huile de coupe, et un peu de fumée quand ça chauffe.
        if (ph < CUT) {
            for (k in 0 until 4) {
                val life = ((t * .7f + k / 4f) % 1f)
                fill.color = withAlpha(0xFFE8E4E0.toInt(), (50 * (1f - life)).toInt())
                c.drawCircle(DX - 8f - life * 10f + 3f * sin(life * 6f + k), BLOCK_TOP - 4f - life * 40f, 3f + life * 7f, fill)
            }
        }
        // La poignée de descente tourne un peu.
        val lever = depth / 14f * .5f
        val lx = 214f + 32f * cos(-.46f + lever); val ly = 100f + 32f * sin(-.46f + lever)
        ink.color = 0xFF8A9096.toInt(); ink.strokeWidth = 2.6f
        c.drawLine(214f, 100f, lx, ly, ink)
        fill.color = 0xFF1A1A1C.toInt(); c.drawCircle(lx, ly, 4f, fill)
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private companion object {
        const val BENCH = 290f
        const val DX = 180f
        const val BLOCK_TOP = 226f
        const val CYCLE = 6f
        const val CUT = 4.8f
    }
}
