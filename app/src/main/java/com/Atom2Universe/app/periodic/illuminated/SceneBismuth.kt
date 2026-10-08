package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bismuth — le cristal en escalier. Refroidi lentement, le bismuth pousse en trémies : des carrés
 * creux en marches, couverts d'une fine couche d'oxyde qui joue toutes les couleurs. Le cristal
 * tourne sur son velours sous un projecteur ; chaque marche change de teinte selon l'angle.
 */
internal class SceneBismuth : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_bismuth_hopper
    override val noteRes = R.string.card_note_bismuth_hopper
    override val explainRes = R.string.card_explain_bismuth_hopper

    override fun engrave(b: Burin) {
        // Velours noir, rond de lumière du projecteur, ombre du cristal.
        b.wash(rect(40f, 50f, 320f, 340f), 0xFF120E14.toInt(), 255)
        b.hatchRect(android.graphics.RectF(40f, 50f, 320f, 340f), 75f, 2.6f, .45f, withAlpha(0xFF000000.toInt(), 160))
        b.glow(CX, CY - 20f, 170f, 0xFF4A3A5A.toInt(), 120)
        b.wash(ellipse(CX + 6f, CY + 8f, 104f, 40f), 0xFF000000.toInt(), 120)
    }

    // ───────────── animation ─────────────

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .7f; strokeJoin = Paint.Join.ROUND }
    private val quad = Path()
    private var px = 0f
    private var py = 0f
    private var sn = 0f
    private var cs = 1f
    private var spin = 0f

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        spin = t * SPIN
        sn = sin(spin); cs = cos(spin)
        // Des marches du fond vers le bord : le bord, plus haut et plus proche, recouvre le reste.
        for (k in STEPS - 1 downTo 0) {
            val outer = 70f - 9f * k
            val inner = if (k == STEPS - 1) 0f else 70f - 9f * (k + 1)
            val h = 44f - 6f * k
            for (j in 0 until 4) top(c, j, outer, inner, h, k)
            if (k > 0) for (j in 0 until 4) riser(c, j, outer, h, 44f - 6f * (k - 1), k)
        }
        // Les flancs extérieurs du cristal, vers nous.
        for (j in 0 until 4) {
            val nx = DX[j]; val nz = DZ[j]
            if (nx * sn + nz * cs <= 0f) continue
            wall(c, j, 70f, 0f, 44f, .55f, 7)
        }
        // Un éclat court sur les coins tournés vers la lumière.
        for (j in 0 until 4) {
            val x = 70f * CORNER_X[j]; val z = 70f * CORNER_Z[j]
            val light = sin(spin * 2f + j * 1.5707964f)
            if (light < .85f) continue
            proj(x, z, 44f)
            val k = (light - .85f) / .15f
            edge.color = withAlpha(Ink.WHITE, (255 * k).toInt()); edge.strokeWidth = 1.2f
            c.drawLine(px - 7f * k, py, px + 7f * k, py, edge); c.drawLine(px, py - 7f * k, px, py + 7f * k, edge)
            edge.strokeWidth = .7f
        }
    }

    /** La face du dessus d'une marche, côté [j] : trapèze entre le carré extérieur et l'intérieur. */
    private fun top(c: Canvas, j: Int, outer: Float, inner: Float, h: Float, k: Int) {
        val ax = CORNER_X[j]; val az = CORNER_Z[j]
        val bx = CORNER_X[(j + 1) % 4]; val bz = CORNER_Z[(j + 1) % 4]
        quad.reset()
        proj(outer * ax, outer * az, h); quad.moveTo(px, py)
        proj(outer * bx, outer * bz, h); quad.lineTo(px, py)
        proj(inner * bx, inner * bz, h); quad.lineTo(px, py)
        proj(inner * ax, inner * az, h); quad.lineTo(px, py)
        quad.close()
        paint(c, k * .13f + j * .25f, .95f)
    }

    /** La contremarche, mur intérieur qui descend du bord [hTop] à la marche suivante [h]. */
    private fun riser(c: Canvas, j: Int, size: Float, h: Float, hTop: Float, k: Int) {
        // Vue de l'intérieur du creux : seuls les murs du fond nous font face.
        if (-(DX[j] * sn + DZ[j] * cs) <= 0f) return
        wall(c, j, size, h, hTop, .7f, k)
    }

    private fun wall(c: Canvas, j: Int, size: Float, h0: Float, h1: Float, shade: Float, k: Int) {
        val ax = CORNER_X[j]; val az = CORNER_Z[j]
        val bx = CORNER_X[(j + 1) % 4]; val bz = CORNER_Z[(j + 1) % 4]
        quad.reset()
        proj(size * ax, size * az, h1); quad.moveTo(px, py)
        proj(size * bx, size * bz, h1); quad.lineTo(px, py)
        proj(size * bx, size * bz, h0); quad.lineTo(px, py)
        proj(size * ax, size * az, h0); quad.lineTo(px, py)
        quad.close()
        paint(c, k * .13f + j * .25f + .5f, shade)
    }

    /** Une teinte d'oxyde qui glisse avec l'angle : or, rose, violet, bleu, vert. */
    private fun paint(c: Canvas, base: Float, shade: Float) {
        var hue = (base + spin / 6.2831855f * 2f) % 1f
        if (hue < 0f) hue += 1f
        val f = hue * IRIS.size
        val i = f.toInt() % IRIS.size
        val a = IRIS[i]; val b = IRIS[(i + 1) % IRIS.size]
        val m = f - f.toInt()
        val r = (((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * m) * shade).toInt()
        val g = (((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * m) * shade).toInt()
        val bl = (((a and 255) + ((b and 255) - (a and 255)) * m) * shade).toInt()
        face.color = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        c.drawPath(quad, face)
        edge.color = withAlpha(0xFF1A1418.toInt(), 200)
        c.drawPath(quad, edge)
    }

    private fun proj(x: Float, z: Float, h: Float) {
        px = CX + x * cs - z * sn
        py = CY + (x * sn + z * cs) * TILT - h
    }

    private companion object {
        const val CX = 180f
        const val CY = 226f
        const val TILT = .5f
        const val SPIN = .24f
        const val STEPS = 7
        val CORNER_X = floatArrayOf(1f, -1f, -1f, 1f)
        val CORNER_Z = floatArrayOf(1f, 1f, -1f, -1f)
        // Normale extérieure du côté j (du coin j au coin j+1).
        val DX = floatArrayOf(0f, -1f, 0f, 1f)
        val DZ = floatArrayOf(1f, 0f, -1f, 0f)
        val IRIS = intArrayOf(0xFFE8C040.toInt(), 0xFFE85AA0.toInt(), 0xFF8A4AE0.toInt(), 0xFF3A7AE8.toInt(),
            0xFF2AC8C0.toInt(), 0xFF6AD04A.toInt())
    }
}
