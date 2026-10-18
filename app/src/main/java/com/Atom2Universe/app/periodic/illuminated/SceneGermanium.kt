package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Germanium — la caméra thermique. Le germanium laisse passer l'infrarouge que le verre arrête :
 * c'est la matière des objectifs des caméras thermiques. Un jardin la nuit, presque noir ; dans
 * l'écran de la caméra, le même jardin vu par sa chaleur : le renard qui passe brille en blanc et
 * jaune, la fenêtre chauffée rougeoie, le ciel est froid.
 */
internal class SceneGermanium : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_thermal_camera
    override val noteRes = R.string.card_note_thermal_camera
    override val explainRes = R.string.card_explain_thermal_camera

    private val house = poly(196f, 160f, 252f, 118f, 320f, 150f, 320f, GROUND, 196f, GROUND)
    private val roof = poly(188f, 164f, 252f, 112f, 326f, 152f, 326f, 144f, 252f, 104f, 182f, 158f)
    private val window = rect(222f, 170f, 252f, 200f)
    private val hedge = Path().apply {
        moveTo(40f, GROUND)
        var x = 40f
        while (x < 200f) {
            quadTo(x + 9f, 168f + 10f * sin(x * .13f), x + 18f, 186f + 4f * sin(x * .3f))
            x += 18f
        }
        lineTo(200f, GROUND); close()
    }
    private val tree = Path().apply {
        addCircle(76f, 126f, 30f, Path.Direction.CW); addCircle(100f, 110f, 26f, Path.Direction.CW)
        addCircle(118f, 134f, 24f, Path.Direction.CW)
        op(rect(94f, 130f, 102f, 190f), Path.Op.UNION)
    }
    private val lawn = rect(40f, GROUND, 320f, 340f)

    override fun engrave(b: Burin) {
        // Le jardin la nuit : on devine la maison, l'arbre, la haie.
        b.body(tree, 0xFF14201A.toInt(), .5f, 60f, outline = .7f, washAlpha = 255)
        b.body(house, 0xFF262838.toInt(), .45f, 0f, outline = .8f, washAlpha = 255)
        b.body(roof, 0xFF1C1C28.toInt(), .5f, 30f, outline = .8f, washAlpha = 255)
        b.body(window, 0xFF161824.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.line(237f, 170f, 237f, 200f, 1.2f, 0xFF3A3C4C.toInt())
        b.line(222f, 185f, 252f, 185f, 1.2f, 0xFF3A3C4C.toInt())
        b.line(226f, 174f, 234f, 182f, 1f, withAlpha(0xFFB8C4E0.toInt(), 90))
        b.body(hedge, 0xFF16241C.toInt(), .55f, 70f, outline = .7f, washAlpha = 255)
        b.wash(lawn, 0xFF1A2420.toInt(), 255)
        b.hatchRect(RectF(40f, GROUND, 320f, 340f), 0f, 2.2f, .4f, 0xFF0A100C.toInt())
        b.line(40f, GROUND, 320f, GROUND, .8f, 0xFF2E3A34.toInt())
    }

    override fun sprites(): List<Sprite> = listOf(
        // Le même jardin en fausses couleurs : froid en violet sombre, tiède en rouge, chaud en jaune.
        Sprite(THERMAL, RectF(40f, 50f, 320f, 340f)) { b ->
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, GROUND,
                intArrayOf(0xFF06021A.toInt(), 0xFF140840.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawRect(40f, 50f, 320f, GROUND, b.p)
            b.fill(tree, 0xFF1E0C52.toInt())
            b.fill(house, 0xFF5A1660.toInt())
            b.glow(237f, 185f, 46f, 0xFFB02A58.toInt(), 200)
            b.fill(roof, 0xFF2E0C4E.toInt())
            b.pen(0xFF000000.toInt()).shader = RadialGradient(237f, 186f, 24f,
                intArrayOf(0xFFFFE070.toInt(), 0xFFF07020.toInt(), 0xFFB0204A.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawPath(window, b.p)
            b.line(237f, 170f, 237f, 200f, 1.6f, 0xFF8A1A50.toInt())
            b.line(222f, 185f, 252f, 185f, 1.6f, 0xFF8A1A50.toInt())
            b.fill(hedge, 0xFF2A0C5A.toInt())
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, GROUND, 0f, 340f,
                intArrayOf(0xFF3A1060.toInt(), 0xFF5A1466.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawPath(lawn, b.p)
            // Le pied du mur garde la chaleur de la maison.
            b.glow(258f, GROUND + 4f, 60f, 0xFF8A1C5A.toInt(), 150)
        },
        // La caméra vue de dos : coque caoutchoutée, poignée, boutons, l'écran laissé vide.
        Sprite(CAMERA, RectF(CAM_L - 2f, CAM_T - 16f, CAM_R + 2f, 340f)) { b ->
            val shell = Path().apply { addRoundRect(CAM_L, CAM_T, CAM_R, CAM_B, 16f, 16f, Path.Direction.CW) }
            val grip = Path().apply { addRoundRect(160f, CAM_B - 6f, 200f, 340f, 10f, 10f, Path.Direction.CW) }
            b.body(grip, 0xFF2A2C30.toInt(), .5f, 80f, outline = 1f, washAlpha = 255)
            b.body(shell, 0xFF34363C.toInt(), .45f, 30f, Fade(CAM_R, CAM_B, CAM_L, CAM_T, 220, 30), outline = 1.2f, washAlpha = 255)
            // L'objectif dépasse au-dessus : bague de caoutchouc, reflet violet du germanium traité.
            b.body(Path().apply { addRoundRect(158f, CAM_T - 14f, 202f, CAM_T + 4f, 5f, 5f, Path.Direction.CW) }, 0xFF24262A.toInt(), .4f, 0f, outline = 1f, washAlpha = 255)
            b.line(164f, CAM_T - 11f, 196f, CAM_T - 11f, 1.4f, withAlpha(0xFFB080E0.toInt(), 200))
            b.line(170f, CAM_T - 8f, 190f, CAM_T - 8f, .9f, withAlpha(0xFF70E0A0.toInt(), 160))
            b.fill(Path().apply { addRoundRect(SCR_L - 4f, SCR_T - 4f, SCR_R + 4f, SCR_B + 4f, 6f, 6f, Path.Direction.CW) }, 0xFF101114.toInt())
            for ((k, x) in floatArrayOf(150f, 180f, 210f).withIndex()) {
                b.body(ellipse(x, CAM_B - 15f, 6f, 6f), if (k == 1) 0xFFB03A2A.toInt() else 0xFF4A4C52.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
            }
            b.line(CAM_L + 10f, CAM_T + 3f, CAM_R - 10f, CAM_T + 3f, 1f, withAlpha(0xFFFFFFFF.toInt(), 70))
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fox = Path()
    private val legs = FloatArray(16)
    private val heat = RadialGradient(0f, 0f, 30f,
        intArrayOf(0xFFFFFCE8.toInt(), 0xFFFFD040.toInt(), 0xFFF07020.toInt(), 0xFFB0204A.toInt()),
        floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
    private val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = heat }
    private val heatLegs = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 3f; color = 0xFFF07828.toInt() }
    private val heatMatrix = Matrix()
    private val scale = LinearGradient(0f, SCR_T + 8f, 0f, SCR_B - 8f,
        intArrayOf(0xFFFFFCE8.toInt(), 0xFFFFD040.toInt(), 0xFFF07020.toInt(), 0xFFB0204A.toInt(), 0xFF2A0C5A.toInt(), 0xFF06021A.toInt()),
        null, Shader.TileMode.CLAMP)
    private val scalePaint = Paint().apply { shader = scale }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Le renard trotte le long de la haie, de droite à gauche, puis revient au bout de la boucle.
        val walk = (t % FOX_LOOP) / FOX_LOOP
        val fx = 362f - walk * 364f
        val fy = GROUND + 18f
        val stride = t * 9f
        buildFox(fx, fy, stride)
        fill.color = 0xFF2E241C.toInt(); c.drawPath(fox, fill)
        ink.color = 0xFF2E241C.toInt(); ink.strokeWidth = 2.6f; c.drawLines(legs, ink)
        fill.color = withAlpha(0xFFE8F0A0.toInt(), 220)
        c.drawCircle(fx - 21f, fy - 17f, 1.1f, fill)
        // La caméra se balance un peu dans la main.
        val dx = 24f * sin(t * .33f)
        val dy = 3f * sin(t * .61f)
        val tilt = 1.5f * sin(t * .27f)
        c.save(); c.translate(dx, dy); c.rotate(tilt, 180f, 300f)
        s.draw(c, CAMERA)
        // L'écran : le jardin en chaleur, vu exactement là où la caméra le cache.
        c.save()
        c.clipRect(SCR_L, SCR_T, SCR_R, SCR_B)
        c.rotate(-tilt, 180f, 300f); c.translate(-dx, -dy)
        s.draw(c, THERMAL)
        c.drawLines(legs, heatLegs)
        heatMatrix.setTranslate(fx - 4f, fy - 10f)
        heat.setLocalMatrix(heatMatrix)
        c.drawPath(fox, heatPaint)
        c.restore()
        // Le réticule, l'échelle des couleurs, la pile.
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 200); ink.strokeWidth = 1f
        val mx = (SCR_L + SCR_R) / 2f; val my = (SCR_T + SCR_B) / 2f
        c.drawLine(mx - 8f, my, mx - 3f, my, ink); c.drawLine(mx + 3f, my, mx + 8f, my, ink)
        c.drawLine(mx, my - 8f, mx, my - 3f, ink); c.drawLine(mx, my + 3f, mx, my + 8f, ink)
        c.drawRect(SCR_R - 9f, SCR_T + 8f, SCR_R - 5f, SCR_B - 8f, scalePaint)
        ink.strokeWidth = .8f
        c.drawRect(SCR_L + 5f, SCR_T + 5f, SCR_L + 17f, SCR_T + 11f, ink)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 200)
        c.drawRect(SCR_L + 6.5f, SCR_T + 6.5f, SCR_L + 13f, SCR_T + 9.5f, fill)
        c.drawRect(SCR_L + 17f, SCR_T + 7f, SCR_L + 18.5f, SCR_T + 9f, fill)
        // Un reflet sur la vitre de l'écran.
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 22)
        c.drawRect(SCR_L, SCR_T, SCR_L + 40f, SCR_B, fill)
        c.restore()
    }

    /** La silhouette du renard en ([x], [y]) (milieu du ventre), tournée vers la gauche ; ses pattes dans [legs]. */
    private fun buildFox(x: Float, y: Float, stride: Float) {
        fox.reset()
        fox.addOval(x - 15f, y - 16f, x + 13f, y - 4f, Path.Direction.CW)
        fox.addCircle(x - 19f, y - 15f, 6f, Path.Direction.CW)
        // Toutes les pièces tournent dans le même sens : sinon leurs recouvrements feraient des trous.
        fox.moveTo(x - 23f, y - 17f); fox.lineTo(x - 22f, y - 11f); fox.lineTo(x - 31f, y - 12f); fox.close()
        fox.moveTo(x - 22f, y - 19f); fox.lineTo(x - 21f, y - 26f); fox.lineTo(x - 17f, y - 20f); fox.close()
        fox.moveTo(x - 17f, y - 20f); fox.lineTo(x - 14f, y - 26f); fox.lineTo(x - 13f, y - 18f); fox.close()
        // La queue en panache.
        fox.moveTo(x + 11f, y - 12f)
        fox.cubicTo(x + 24f, y - 14f, x + 32f, y - 4f, x + 34f, y + 2f)
        fox.cubicTo(x + 26f, y + 2f, x + 18f, y - 2f, x + 10f, y - 6f); fox.close()
        // Quatre pattes, en diagonale deux à deux.
        for (k in 0 until 4) {
            val swing = 5f * sin(stride + if (k == 0 || k == 3) 0f else 3.14f)
            legs[k * 4] = x + HIPS[k]; legs[k * 4 + 1] = y - 6f
            legs[k * 4 + 2] = x + HIPS[k] + swing; legs[k * 4 + 3] = y + 4f - .6f * cos(stride)
        }
    }

    private companion object {
        const val GROUND = 236f
        const val FOX_LOOP = 14f
        const val CAM_L = 104f
        const val CAM_R = 256f
        const val CAM_T = 150f
        const val CAM_B = 300f
        const val SCR_L = 118f
        const val SCR_R = 242f
        const val SCR_T = 164f
        const val SCR_B = 262f
        const val THERMAL = 1
        const val CAMERA = 2
        val HIPS = floatArrayOf(-10f, -6f, 6f, 10f)
    }
}
