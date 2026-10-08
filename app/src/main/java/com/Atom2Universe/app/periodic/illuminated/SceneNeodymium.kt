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
import kotlin.math.sqrt

/**
 * Néodyme — l'aimant. Les aimants au néodyme sont les plus puissants qui soient. Vu d'en haut,
 * un aimant tourne lentement sur une feuille, deux trombones collés à ses bouts ; la limaille de
 * fer s'aligne sur les lignes de son champ et les boussoles des coins le suivent.
 */
internal class SceneNeodymium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_neodymium_magnet
    override val noteRes = R.string.card_note_neodymium_magnet
    override val explainRes = R.string.card_explain_neodymium_magnet

    override fun engrave(b: Burin) {
        // La table en bois.
        val table = rect(40f, 50f, 320f, 340f)
        b.wash(table, 0xFFA4774A.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 4f, 3.4f, .5f, withAlpha(Ink.BROWN, 150))
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 2f, 9f, .9f, withAlpha(Ink.BROWN, 90))
        // La feuille et son ombre.
        b.wash(poly(66f, 81f, 304f, 75f, 310f, 311f, 70f, 317f), 0xFF000000.toInt(), 70)
        val sheet = poly(62f, 76f, 300f, 70f, 306f, 306f, 66f, 312f)
        b.wash(sheet, 0xFFF6F1E4.toInt(), 255)
        b.stipple(sheet, 500, .45f, withAlpha(Ink.BROWN, 60), 5)
        b.stroke(sheet, .6f)
        // Les quatre boussoles : boîtier de laiton, cadran blanc, huit graduations.
        for (k in 0 until 4) {
            val x = COMPASS_X[k]; val y = COMPASS_Y[k]
            b.wash(ellipse(x + 2f, y + 3f, 15f, 15f), 0xFF000000.toInt(), 60)
            b.body(ellipse(x, y, 15f, 15f), 0xFFC9A24A.toInt(), .25f, 40f, outline = .8f, washAlpha = 255)
            b.body(ellipse(x, y, 12f, 12f), 0xFFFBF8F0.toInt(), 0f, outline = .6f, washAlpha = 255)
            for (i in 0 until 8) {
                val a = i * 45f * DEG
                b.line(x + 9.5f * cos(a), y + 9.5f * sin(a), x + 11.5f * cos(a), y + 11.5f * sin(a), if (i % 2 == 0) .9f else .5f)
            }
        }
    }

    override fun sprites() = listOf(
        Sprite(MAGNET, RectF(CX - 66f, CY - 66f, CX + 66f, CY + 66f)) { b -> magnet(b, false) },
        Sprite(SHADOW, RectF(CX - 66f, CY - 66f, CX + 66f, CY + 66f)) { b -> magnet(b, true) }
    )

    /** L'aimant nickelé, la limaille hérissée à ses bouts et un trombone collé à chacun. */
    private fun magnet(b: Burin, shadow: Boolean) {
        val block = Path().apply { addRoundRect(CX - 36f, CY - 12f, CX + 36f, CY + 12f, 2.5f, 2.5f, Path.Direction.CW) }
        if (shadow) {
            b.wash(block, 0xFF000000.toInt(), 80)
            for (side in SIDES) b.wash(Path().apply { addRoundRect(CX + side * 36f - (if (side < 0f) 26f else 0f), CY - 5f, CX + side * 36f + (if (side > 0f) 26f else 0f), CY + 5f, 5f, 5f, Path.Direction.CW) }, 0xFF000000.toInt(), 40)
            return
        }
        // La limaille hérissée sur les faces des pôles.
        for (side in SIDES) for (i in 0 until 9) {
            val y = CY - 11f + i * 2.75f
            val spread = (y - CY) * .5f
            b.line(CX + side * 36f, y, CX + side * 44f, y + spread, .9f, 0xFF3A3A40.toInt())
        }
        // Un trombone à chaque bout, un peu de travers.
        for (side in SIDES) {
            b.c.save(); b.c.rotate(side * 12f, CX + side * 36f, CY)
            val x0 = if (side > 0f) CX + 36f else CX - 62f
            for (k in 0 until 3) {
                val inset = k * 2.4f
                val r = RectF(x0 + inset * (if (side > 0f) 1.4f else .4f), CY - 5f + inset, x0 + 26f - inset * (if (side > 0f) .4f else 1.4f), CY + 5f - inset)
                if (r.height() > 1f) b.c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, b.pen(0xFF7A8088.toInt(), 1.3f))
            }
            b.c.restore()
        }
        // Le bloc nickelé.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, CY - 12f, 0f, CY + 12f,
            intArrayOf(0xFFF4F6F8.toInt(), 0xFF9AA0A8.toInt(), 0xFFE2E6EA.toInt(), 0xFF7A8088.toInt()), floatArrayOf(0f, .35f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(block, b.p)
        b.line(CX - 33f, CY - 9f, CX + 33f, CY - 9f, .8f, withAlpha(Ink.WHITE, 200))
        b.stroke(block, .9f)
    }

    // ───────────── animation ─────────────

    private val filings = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = 0x90FFFFFF.toInt() }
    private val bins = Array(3) { FloatArray(COLS * ROWS * 4) }
    private val counts = IntArray(3)
    private val arrow = Path()
    private val dial = RectF()
    private var fx = 0f
    private var fy = 0f

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val deg = t * SPIN
        val a = deg * DEG
        val ux = cos(a); val uy = sin(a)
        val nx = CX + ux * POLE; val ny = CY + uy * POLE
        val sx = CX - ux * POLE; val sy = CY - uy * POLE

        // La limaille : chaque grain se couche le long du champ, plus marqué près des pôles.
        counts.fill(0)
        for (j in 0 until ROWS) for (i in 0 until COLS) {
            val id = j * COLS + i
            val x = X0 + (i + .2f + .6f * hash(id, 1)) * STEP
            val y = Y0 + (j + .2f + .6f * hash(id, 2)) * STEP
            val lx = (x - CX) * ux + (y - CY) * uy
            val ly = -(x - CX) * uy + (y - CY) * ux
            if (abs(lx) < 46f && abs(ly) < 16f) continue
            if (abs(lx) < 66f && abs(ly) < 10f) continue
            if (underCompass(x, y)) continue
            field(x, y, nx, ny, sx, sy)
            val m = sqrt(fx * fx + fy * fy)
            if (m < 1e-9f) continue
            val strength = ((sqrt(m) - .0035f) / .022f).coerceIn(0f, 1f)
            val half = 2.4f + 2.2f * strength
            val bin = if (strength < .33f) 0 else if (strength < .66f) 1 else 2
            val buf = bins[bin]; val k = counts[bin]
            val ex = fx / m * half; val ey = fy / m * half
            buf[k] = x - ex; buf[k + 1] = y - ey; buf[k + 2] = x + ex; buf[k + 3] = y + ey
            counts[bin] = k + 4
        }
        for (bin in 0 until 3) {
            filings.color = withAlpha(0xFF2A2A30.toInt(), 70 + bin * 75)
            c.drawLines(bins[bin], 0, counts[bin], filings)
        }

        // L'aimant et son ombre.
        s.draw(c, SHADOW, 3f, 4f, deg, CX, CY)
        s.draw(c, MAGNET, 0f, 0f, deg, CX, CY)

        // Les aiguilles des boussoles pointent le long du champ.
        for (k in 0 until 4) {
            val x = COMPASS_X[k]; val y = COMPASS_Y[k]
            field(x, y, nx, ny, sx, sy)
            val m = sqrt(fx * fx + fy * fy).coerceAtLeast(1e-9f)
            val dx = fx / m; val dy = fy / m
            needleHalf(c, x, y, dx, dy, 0xFFC8281E.toInt())
            needleHalf(c, x, y, -dx, -dy, 0xFF4A4E58.toInt())
            needle.color = 0xFF8C6421.toInt(); c.drawCircle(x, y, 1.6f, needle)
            dial.set(x - 10f, y - 10f, x + 10f, y + 10f)
            c.drawArc(dial, 200f, 60f, false, glass)
        }
    }

    private fun underCompass(x: Float, y: Float): Boolean {
        for (k in 0 until 4) {
            val dx = x - COMPASS_X[k]; val dy = y - COMPASS_Y[k]
            if (dx * dx + dy * dy < 19f * 19f) return true
        }
        return false
    }

    private fun needleHalf(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, color: Int) {
        arrow.reset()
        arrow.moveTo(x + dx * 9f, y + dy * 9f)
        arrow.lineTo(x - dy * 2.2f, y + dx * 2.2f)
        arrow.lineTo(x + dy * 2.2f, y - dx * 2.2f)
        arrow.close()
        needle.color = color
        c.drawPath(arrow, needle)
    }

    /** Le champ de deux pôles : il sort du nord et rentre au sud. Résultat dans ([fx], [fy]). */
    private fun field(x: Float, y: Float, nx: Float, ny: Float, sx: Float, sy: Float) {
        var dx = x - nx; var dy = y - ny
        var r2 = dx * dx + dy * dy + 30f
        var inv = 1f / (r2 * sqrt(r2))
        fx = dx * inv; fy = dy * inv
        dx = x - sx; dy = y - sy
        r2 = dx * dx + dy * dy + 30f
        inv = 1f / (r2 * sqrt(r2))
        fx -= dx * inv; fy -= dy * inv
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CX = 180f
        const val CY = 192f
        const val POLE = 28f
        const val SPIN = 11f
        const val X0 = 70f
        const val Y0 = 82f
        const val STEP = 10f
        const val COLS = 23
        const val ROWS = 22
        const val MAGNET = 1
        const val SHADOW = 2
        const val DEG = .017453292f
        val SIDES = floatArrayOf(-1f, 1f)
        val COMPASS_X = floatArrayOf(92f, 270f, 96f, 274f)
        val COMPASS_Y = floatArrayOf(104f, 100f, 282f, 278f)
    }
}
