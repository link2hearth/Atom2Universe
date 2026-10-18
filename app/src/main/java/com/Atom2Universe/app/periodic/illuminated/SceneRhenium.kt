package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rhénium — le réacteur d'avion. Les aubes de turbine baignent dans des gaz plus chauds que leur
 * propre point de fusion ; le rhénium de leur alliage les empêche de se déformer. Le réacteur est
 * coupé en deux : la soufflante et les compresseurs tournent, l'air file et se resserre, brûle
 * dans la chambre, traverse les aubes rougeoyantes de la turbine et sort en jet brûlant.
 */
internal class SceneRhenium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_jet_engine
    override val noteRes = R.string.card_note_jet_engine
    override val explainRes = R.string.card_explain_jet_engine

    override fun engrave(b: Burin) {
        // L'atelier : mur clair, sol, le berceau qui porte le réacteur.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 300f,
            0xFFC8CCD0.toInt(), 0xFF9AA0A6.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 300f, b.p)
        b.hatchRect(android.graphics.RectF(40f, 50f, 320f, 300f), 90f, 4.5f, .4f, withAlpha(Ink.BROWN, 60))
        b.body(rect(40f, 300f, 320f, 340f), 0xFF6A6E74.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        for (x in floatArrayOf(110f, 240f)) b.body(poly(x - 14f, 300f, x + 14f, 300f, x + 6f, 262f, x - 6f, 262f), 0xFF3A5A7A.toInt(), .3f, 90f, outline = .8f, washAlpha = 255)
        // Le carter extérieur, coupé : on voit sa tranche en haut et en bas.
        for (side in floatArrayOf(-1f, 1f)) {
            val cowl = Path().apply {
                moveTo(46f, CY + side * 70f); quadTo(150f, CY + side * 74f, 240f, CY + side * 66f); lineTo(300f, CY + side * 44f)
                lineTo(300f, CY + side * 38f); lineTo(240f, CY + side * 58f); quadTo(150f, CY + side * 66f, 56f, CY + side * 62f); close()
            }
            b.body(cowl, 0xFFB8BEC6.toInt(), .4f, 60f, outline = .9f, washAlpha = 255)
            // Le carter du cœur, qui sépare le flux chaud du flux froid.
            b.body(Path().apply {
                moveTo(94f, CY + side * 36f); lineTo(176f, CY + side * 30f); lineTo(258f, CY + side * 36f); lineTo(258f, CY + side * 40f)
                lineTo(176f, CY + side * 34f); lineTo(94f, CY + side * 40f); close()
            }, 0xFF7A8088.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        }
        // Le canal froid autour du cœur, le cœur sombre, l'arbre au milieu.
        b.wash(rect(94f, CY - 30f, 258f, CY + 30f), 0xFF2A2C30.toInt(), 255)
        b.wash(rect(176f, CY - 28f, 218f, CY + 28f), 0xFF4A2A1A.toInt(), 255)
        b.body(rect(60f, CY - 4f, 270f, CY + 4f), 0xFF8A8E94.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        // Le cône d'entrée et le cône de sortie.
        b.body(Path().apply { moveTo(56f, CY); quadTo(58f, CY - 14f, 74f, CY - 14f); lineTo(74f, CY + 14f); quadTo(58f, CY + 14f, 56f, CY); close() }, 0xFFD8DCE2.toInt(), .3f, 90f, outline = .8f, washAlpha = 255)
        b.body(poly(256f, CY - 16f, 256f, CY + 16f, 298f, CY), 0xFF8A6A50.toInt(), .4f, 0f, outline = .8f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flame = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La turbine rougeoie.
        val pulse = .8f + .2f * sin(t * 5f)
        fill.color = withAlpha(0xFFFF6A20.toInt(), (110 * pulse).toInt()); c.drawOval(214f, CY - 32f, 262f, CY + 32f, fill)
        // Les flammes de la chambre de combustion, en haut et en bas de l'arbre.
        for (side in SIDES) for (k in 0 until 3) {
            val y = CY + side * (10f + k * 6f)
            val len = 26f + 8f * sin(t * 13f + k * 2f + side)
            flame.reset()
            flame.moveTo(180f, y - 3f); flame.quadTo(190f + len * .5f, y - 5f, 182f + len, y); flame.quadTo(190f + len * .5f, y + 5f, 180f, y + 3f); flame.close()
            fill.color = withAlpha(0xFFFFA030.toInt(), 200); c.drawPath(flame, fill)
            fill.color = withAlpha(0xFF6A9AFF.toInt(), 170); c.drawCircle(181f, y, 2.4f, fill)
        }
        // Les aubes de chaque étage tournent : on les voit défiler de profil.
        for (st in STAGE_X.indices) {
            val x = STAGE_X[st]; val hw = STAGE_W[st]; val rh = 6f; val rt = STAGE_R[st]
            val n = STAGE_N[st]
            val hot = st >= 5
            for (k in 0 until n) {
                val a = t * SPIN + k * 6.2831855f / n + st
                val face = cos(a)
                if (face < 0f) continue
                val sa = sin(a)
                blade.strokeWidth = if (st == 0) 2.2f else 1.6f
                blade.color = if (hot) mixHot(face) else withAlpha(0xFFE0E4EA.toInt(), (90 + 165 * face).toInt())
                c.drawLine(x - hw, CY + rh * sa, x + hw, CY + rt * sa, blade)
            }
        }
        // L'air : froid autour du cœur, comprimé, brûlé, puis jeté dehors.
        for (k in 0 until AIR) {
            val u = (t * .45f + k / AIR.toFloat()) % 1f
            val core = k % 3 != 0
            val x = 46f + 268f * (u + .35f * u * u) / 1.35f
            val lane = (hash(k, 1) - .5f) * 2f
            val y = if (core) CY + lane * (26f - 10f * (x - 46f) / 268f).coerceAtLeast(10f) else CY + (if (lane > 0f) 1f else -1f) * (46f + 8f * kotlin.math.abs(lane))
            val alpha = (255 * sin(u * 3.1415927f)).toInt()
            fill.color = if (!core) withAlpha(0xFFB8E0FF.toInt(), alpha)
                else if (x < 176f) withAlpha(0xFF8AC8FF.toInt(), alpha)
                else withAlpha(0xFFFFB050.toInt(), alpha)
            c.drawCircle(x, y, if (core && x > 176f) 2.2f else 1.6f, fill)
        }
        // Le jet brûlant qui tremble à la sortie.
        blade.strokeWidth = 1.4f
        for (k in 0 until 4) {
            val y = CY + (k - 1.5f) * 9f
            blade.color = withAlpha(0xFFFF9A40.toInt(), 90)
            c.drawLine(298f, y + 2f * sin(t * 9f + k), 320f, y + 3f * sin(t * 7f + k * 2f), blade)
        }
    }

    private fun mixHot(face: Float): Int {
        val g = (90 + 120 * face).toInt()
        return (0xFF shl 24) or (255 shl 16) or (g shl 8) or 40
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CY = 190f
        const val SPIN = 9f
        const val AIR = 36
        val SIDES = floatArrayOf(-1f, 1f)
        // Soufflante, compresseur basse pression, trois étages haute pression, deux de turbine.
        val STAGE_X = floatArrayOf(80f, 108f, 128f, 144f, 160f, 228f, 246f)
        val STAGE_W = floatArrayOf(6f, 5f, 4f, 4f, 3.5f, 4f, 5f)
        val STAGE_R = floatArrayOf(64f, 30f, 28f, 26f, 24f, 26f, 30f)
        val STAGE_N = intArrayOf(18, 22, 24, 24, 26, 22, 22)
    }
}
