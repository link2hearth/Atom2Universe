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
 * Technétium — la scintigraphie. Le technétium 99m est l'isotope le plus utilisé à l'hôpital :
 * injecté, il se fixe sur les os, et une caméra compte un à un les rayons qu'il émet. Sur l'écran
 * du médecin, dans la pénombre, le squelette apparaît point par point, de face et de dos, puis
 * l'image s'efface pour l'examen suivant.
 */
internal class SceneTechnetium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_bone_scan
    override val noteRes = R.string.card_note_bone_scan
    override val explainRes = R.string.card_explain_bone_scan

    /** Les points de l'image, tirés une fois dans les deux squelettes, dans l'ordre où ils arrivent. */
    private val dots = FloatArray(DOTS * 2)

    init {
        // Les os comme des segments (x0, y0, x1, y1, épaisseur, poids), pour un squelette de face
        // centré en 0, haut de 120.
        val bones = floatArrayOf(
            0f, -54f, 0f, -54f, 9f, 6f,         // crâne
            0f, -44f, 0f, 4f, 2f, 5f,           // colonne
            -14f, -36f, 14f, -36f, 2f, 2f,      // clavicules
            -10f, -30f, -12f, -10f, 6f, 4f,     // côtes gauches
            10f, -30f, 12f, -10f, 6f, 4f,       // côtes droites
            -12f, 6f, 12f, 6f, 5f, 4f,          // bassin
            -15f, -35f, -19f, -12f, 1.6f, 2f,   // bras
            15f, -35f, 19f, -12f, 1.6f, 2f,
            -19f, -12f, -22f, 10f, 1.4f, 1.6f,  // avant-bras
            19f, -12f, 22f, 10f, 1.4f, 1.6f,
            -8f, 10f, -9f, 36f, 2f, 3f,         // fémurs
            8f, 10f, 9f, 36f, 2f, 3f,
            -9f, 38f, -9f, 62f, 1.6f, 2.4f,     // tibias
            9f, 38f, 9f, 62f, 1.6f, 2.4f
        )
        var total = 0f
        for (k in bones.indices step 6) total += bones[k + 5]
        var seed = 1
        for (i in 0 until DOTS) {
            // Un os choisi selon son poids, un point le long de l'os, un peu de flou.
            var pick = rnd(seed++) * total
            var k = 0
            while (k < bones.size - 6 && pick > bones[k + 5]) { pick -= bones[k + 5]; k += 6 }
            val u = rnd(seed++)
            val a = rnd(seed++) * 2f * PI.toFloat()
            val r = bones[k + 4] * rnd(seed++)
            val view = if (rnd(seed++) < .5f) VIEW_A else VIEW_B
            dots[i * 2] = view + bones[k] + (bones[k + 2] - bones[k]) * u + r * cos(a)
            dots[i * 2 + 1] = VIEW_Y + bones[k + 1] + (bones[k + 3] - bones[k + 1]) * u + r * sin(a) * .8f
        }
    }

    private fun rnd(i: Int): Float {
        var h = i * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    override fun engrave(b: Burin) {
        // La salle de lecture dans la pénombre.
        val room = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            intArrayOf(0xFF1A2228.toInt(), 0xFF0E1216.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(room, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, DESK), 90f, 3.4f, .3f, 0xFF0A0E10.toInt())
        // Le bureau éclairé par l'écran.
        val desk = poly(40f, DESK, 320f, DESK, 320f, 340f, 40f, 340f)
        b.wash(desk, 0xFF3A2E24.toInt(), 255)
        b.hatchRect(RectF(40f, DESK, 320f, 340f), 0f, 2.4f, .4f, 0xFF1A140E.toInt())
        b.glow(180f, DESK + 4f, 130f, 0xFFB8D0E0.toInt(), 70)
        // L'écran : cadre, pied, la dalle blanche des images.
        b.body(poly(166f, 250f, 194f, 250f, 200f, DESK + 6f, 160f, DESK + 6f), 0xFF2A2C30.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(68f, 70f, 292f, 252f, 6f, 6f, Path.Direction.CW) }, 0xFF1A1C20.toInt(), .2f, 0f, outline = 1.1f, washAlpha = 255)
        b.fill(rect(SCR_L, SCR_T, SCR_R, SCR_B), 0xFFF4F6F8.toInt())
        b.line(180f, SCR_T + 6f, 180f, SCR_B - 6f, .6f, 0xFFB8C0C8.toInt())
        // Le clavier, une tasse.
        b.body(poly(110f, DESK + 18f, 250f, DESK + 18f, 262f, DESK + 40f, 98f, DESK + 40f), 0xFF2A2C30.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        for (r in 0 until 3) for (k in 0 until 12) {
            val y = DESK + 21f + r * 6f
            val x = 104f + r * 2f + k * 12.5f - r * 2f
            b.fill(rect(x + 3f, y, x + 12f, y + 4.4f), 0xFF4A4C52.toInt())
        }
        b.body(Path().apply { addRoundRect(272f, DESK + 8f, 296f, DESK + 36f, 3f, 3f, Path.Direction.CW) }, 0xFFE8E4DC.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.fill(ellipse(284f, DESK + 9f, 12f, 3f), 0xFF4A2A1A.toInt())
    }

    // ───────────── animation ─────────────

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 1.5f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        // Les points arrivent vite au début, puis de plus en plus rarement : l'image se précise.
        val u = (ph / BUILD).coerceAtMost(1f)
        val n = (DOTS * (1f - (1f - u) * (1f - u))).toInt()
        val fade = if (ph > CYCLE - 1f) CYCLE - ph else 1f
        dot.color = withAlpha(0xFF14181C.toInt(), (230 * fade).toInt())
        c.drawPoints(dots, 0, n * 2, dot)
        // Les derniers arrivés clignotent un instant, plus gros.
        if (u < 1f) {
            fill.color = 0xFF2A6AB8.toInt()
            for (i in (n - 12).coerceAtLeast(0) until n) c.drawCircle(dots[i * 2], dots[i * 2 + 1], 1.6f, fill)
        }
        // La barre de progression sous les images.
        fill.color = 0xFFD0D8E0.toInt(); c.drawRect(SCR_L + 20f, SCR_B - 8f, SCR_R - 20f, SCR_B - 5f, fill)
        fill.color = 0xFF2A8AE8.toInt(); c.drawRect(SCR_L + 20f, SCR_B - 8f, SCR_L + 20f + (SCR_R - SCR_L - 40f) * u, SCR_B - 5f, fill)
        // Le reflet de l'écran qui palpite un peu dans la pièce.
        fill.color = withAlpha(0xFFB8D0E0.toInt(), (16 + 6 * sin(t * 1.3f)).toInt())
        c.drawRect(40f, 50f, 320f, 340f, fill)
    }

    private companion object {
        const val DESK = 266f
        const val SCR_L = 76f
        const val SCR_R = 284f
        const val SCR_T = 78f
        const val SCR_B = 244f
        const val VIEW_A = 128f
        const val VIEW_B = 232f
        const val VIEW_Y = 156f
        const val DOTS = 1600
        const val CYCLE = 11f
        const val BUILD = 8f
    }
}
