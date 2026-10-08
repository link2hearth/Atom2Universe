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
 * Krypton — le spectacle laser. Les lasers au krypton ont longtemps peint les faisceaux rouges
 * des spectacles laser. Dans une salle de concert enfumée, un éventail de rayons rouges et verts
 * balaie la brume au-dessus de la foule qui saute, les projecteurs de la herse changent de couleur.
 */
internal class SceneKrypton : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_laser_show
    override val noteRes = R.string.card_note_laser_show
    override val explainRes = R.string.card_explain_laser_show

    override fun engrave(b: Burin) {
        // La salle dans le noir, un peu de brume éclairée au-dessus de la scène.
        val hall = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            intArrayOf(0xFF0A0818.toInt(), 0xFF1A1030.toInt(), 0xFF0A0610.toInt()), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(hall, b.p)
        b.glow(PX, 200f, 150f, 0xFF4A2A6A.toInt(), 90)
        // La herse et ses projecteurs.
        b.body(rect(40f, TRUSS - 4f, 320f, TRUSS + 4f), 0xFF3A3C44.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        var x = 40f
        while (x < 320f) { b.line(x, TRUSS - 4f, x + 8f, TRUSS + 4f, .6f, 0xFF6A6C74.toInt()); x += 8f }
        for (sx in SPOTS) b.body(Path().apply { addRoundRect(sx - 6f, TRUSS + 4f, sx + 6f, TRUSS + 16f, 2f, 2f, Path.Direction.CW) }, 0xFF2A2C34.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        // La scène : plancher, retours, le projecteur laser au milieu.
        b.body(rect(40f, STAGE, 320f, STAGE + 14f), 0xFF2A2430.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.line(40f, STAGE, 320f, STAGE, 1f, 0xFF6A5A7A.toInt())
        for (mx in floatArrayOf(84f, 276f)) b.body(poly(mx - 16f, STAGE, mx + 16f, STAGE, mx + 12f, STAGE - 12f, mx - 12f, STAGE - 12f), 0xFF1A1820.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(PX - 12f, PY - 2f, PX + 12f, STAGE, 2f, 2f, Path.Direction.CW) }, 0xFF3A3C44.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val cone = Path()
    private val person = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val beat = t * BPM / 60f
        val bounce = abs(sin(beat * Math.PI.toFloat()))
        // Les cônes des projecteurs, qui changent de couleur à chaque mesure.
        val bar = (beat / 4f).toInt()
        for (k in SPOTS.indices) {
            val sx = SPOTS[k]
            val color = PALETTE[(bar + k) % PALETTE.size]
            val swing = 18f * sin(t * .7f + k)
            cone.reset()
            cone.moveTo(sx - 4f, TRUSS + 16f); cone.lineTo(sx + 4f, TRUSS + 16f)
            cone.lineTo(sx + swing + 34f, STAGE); cone.lineTo(sx + swing - 34f, STAGE); cone.close()
            fill.color = withAlpha(color, 34)
            c.drawPath(cone, fill)
            fill.color = withAlpha(color, 230); c.drawCircle(sx, TRUSS + 15f, 3f, fill)
        }
        // L'éventail laser : il balaie de gauche à droite, s'ouvre et se referme ; rouge ou vert.
        val sweep = 26f * sin(t * .9f)
        val open = 9f + 7f * sin(t * .43f)
        val red = (beat / 8f).toInt() % 2 == 0
        val color = if (red) 0xFFFF3A2A.toInt() else 0xFF3AFF6A.toInt()
        for (k in -3..3) {
            val deg = sweep + k * open - 90f
            val a = deg * Math.PI.toFloat() / 180f
            val ex = PX + 420f * cos(a); val ey = PY + 420f * sin(a)
            glowLine.color = withAlpha(color, 40); glowLine.strokeWidth = 7f
            c.drawLine(PX, PY, ex, ey, glowLine)
            core.color = withAlpha(color, 230); core.strokeWidth = 1.2f
            c.drawLine(PX, PY, ex, ey, core)
        }
        // Une nappe horizontale au-dessus des têtes, de temps en temps.
        val sheet = (t % 12f) - 8f
        if (sheet in 0f..2.5f) {
            val k = 1f - abs(sheet - 1.25f) / 1.25f
            for (i in 0 until 9) {
                val deg = -180f + 4f + i * 21.5f + 4f * sin(t * 2f)
                val a = deg * Math.PI.toFloat() / 180f
                core.color = withAlpha(0xFF3AC8FF.toInt(), (180 * k).toInt()); core.strokeWidth = 1f
                c.drawLine(PX, PY, PX + 300f * cos(a), PY + 300f * sin(a) * .12f, core)
            }
        }
        fill.color = withAlpha(color, 255); c.drawCircle(PX, PY, 2.6f, fill)
        fill.color = withAlpha(color, 80); c.drawCircle(PX, PY, 9f, fill)
        // La foule en ombres chinoises, qui saute sur le temps ; quelques bras levés. Le rang du
        // fond d'abord, celui de devant par-dessus.
        for (pass in 0 until 2) for (k in 0 until CROWD) {
            val row = k % 2 // 0 devant, 1 au fond
            if (row == pass) continue
            val x = 36f + k * 22f + 8f * hash(k, 1)
            val base = 340f - row * 12f
            val jump = bounce * (3f + 4f * hash(k, 2)) * (if (hash(k, 3) > .3f) 1f else 0f)
            val y = base - 46f - row * 6f - jump
            fill.color = if (row == 0) 0xFF050308.toInt() else 0xFF0E0A16.toInt()
            person.reset()
            person.addCircle(x, y, 8f, Path.Direction.CW)
            person.addRoundRect(x - 15f, y + 8f, x + 15f, base, 10f, 10f, Path.Direction.CW)
            c.drawPath(person, fill)
            if (hash(k, 4) > .45f) {
                // Le bras levé ondule.
                val side = if (hash(k, 5) > .5f) 1f else -1f
                val wave = 6f * sin(t * 3f + k)
                core.color = fill.color; core.strokeWidth = 5f
                c.drawLine(x + side * 10f, y + 12f, x + side * 16f + wave, y - 22f - jump * .5f, core)
                c.drawCircle(x + side * 16f + wave, y - 24f - jump * .5f, 3.4f, fill)
            }
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val TRUSS = 70f
        const val STAGE = 248f
        const val PX = 180f
        const val PY = 236f
        const val BPM = 124f
        const val CROWD = 13
        val SPOTS = floatArrayOf(76f, 132f, 228f, 284f)
        val PALETTE = intArrayOf(0xFFFF4AA0.toInt(), 0xFF4A8AFF.toInt(), 0xFFFFC84A.toInt(), 0xFFB04AFF.toInt())
    }
}
