package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * La mine à ciel ouvert, commune aux métaux dont on ne voit jamais le métal mais seulement la
 * mine. Le trou descend en gradins ; les camions-bennes chargés remontent la piste dans leur
 * poussière, la pelle creuse au fond. Seule la légende change d'un élément à l'autre.
 */
internal class SceneMine(
    override val noteRes: Int,
    override val nameRes: Int = R.string.card_scene_open_pit_mine,
    override val explainRes: Int = R.string.card_explain_open_pit_mine
) : EngravedScene() {

    override val sky = Sky.DAY

    override fun engrave(b: Burin) {
        // Les collines au loin et le plateau autour du trou.
        b.body(Path().apply {
            moveTo(40f, 128f); quadTo(90f, 104f, 140f, 120f); quadTo(200f, 100f, 250f, 116f); quadTo(290f, 106f, 320f, 118f)
            lineTo(320f, 140f); lineTo(40f, 140f); close()
        }, 0xFF8AA0A8.toInt(), .2f, 0f, outline = .6f, washAlpha = 200)
        val land = rect(40f, 132f, 320f, 340f)
        b.body(land, 0xFFC8A070.toInt(), .2f, 0f, outline = 0f, washAlpha = 255)
        b.stipple(land, 400, .6f, withAlpha(Ink.BROWN, 140), 4)
        // Les gradins : chaque palier un peu plus bas, un peu plus étroit, d'une couleur de roche.
        for (k in 0 until BENCHES) {
            val cy = PIT_Y + k * 10f; val rx = 140f - k * 20f; val ry = 64f - k * 8.6f
            val bench = ellipse(PIT_X, cy, rx, ry)
            b.body(bench, ROCKS[k % ROCKS.size], .25f + k * .06f, 60f, outline = .8f, washAlpha = 255)
            // La face du palier, éclairée sur le côté lointain.
            b.c.save(); b.c.clipRect(40f, cy - ry, 320f, cy - ry + 7f)
            b.wash(bench, Ink.WHITE, 40)
            b.c.restore()
        }
        // Une mare au fond, et la piste qui remonte jusqu'au bord.
        b.body(ellipse(PIT_X - 8f, 258f, 22f, 6f), 0xFF5A9A8A.toInt(), .1f, 0f, outline = .6f, washAlpha = 220)
        b.c.drawLine(ROAD_X0, ROAD_Y0, ROAD_X1, ROAD_Y1, b.pen(0xFFE0C8A0.toInt(), 8f))
        b.line(ROAD_X0, ROAD_Y0 + 4f, ROAD_X1, ROAD_Y1 + 4f, .7f, Ink.BROWN)
        // La pelle, au fond (le bras bouge dans l'animation).
        b.body(rect(SHOVEL_X - 14f, 246f, SHOVEL_X + 12f, 258f), 0xFFE8B020.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(rect(SHOVEL_X - 4f, 238f, SHOVEL_X + 8f, 247f), 0xFFE8B020.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        b.wash(rect(SHOVEL_X, 240f, SHOVEL_X + 6f, 244f), 0xFF2A3A4A.toInt(), 220)
        b.body(rect(SHOVEL_X - 16f, 257f, SHOVEL_X + 14f, 263f), 0xFF2A2A2E.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        // Un tas de minerai sur le bord.
        b.body(Path().apply { moveTo(56f, 160f); quadTo(74f, 136f, 96f, 160f); close() }, 0xFF8A5A3A.toInt(), .4f, 60f, outline = .8f, washAlpha = 255)
    }

    override fun sprites() = listOf(
        // Un camion-benne de profil, chargé.
        Sprite(TRUCK, RectF(ROAD_X0 - 14f, ROAD_Y0 - 20f, ROAD_X0 + 14f, ROAD_Y0 + 4f)) { b ->
            val x = ROAD_X0; val y = ROAD_Y0
            b.body(poly(x - 12f, y - 4f, x + 4f, y - 4f, x + 6f, y - 12f, x - 13f, y - 12f), 0xFFE8B020.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.body(Path().apply { moveTo(x - 11f, y - 12f); quadTo(x - 4f, y - 18f, x + 4f, y - 12f); close() }, 0xFF8A5A3A.toInt(), .3f, 60f, outline = .5f, washAlpha = 255)
            b.body(rect(x + 5f, y - 10f, x + 12f, y - 2f), 0xFFE8B020.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.wash(rect(x + 8f, y - 9f, x + 11f, y - 6f), 0xFF2A3A4A.toInt(), 230)
            for (wx in floatArrayOf(x - 7f, x + 8f)) b.body(ellipse(wx, y - 1f, 3f, 3f), 0xFF1A1A1E.toInt(), 0f, outline = .5f, washAlpha = 255)
        },
        // Le bras de la pelle et son godet.
        Sprite(ARM, RectF(SHOVEL_X - 34f, 222f, SHOVEL_X + 4f, 258f)) { b ->
            b.body(limb(SHOVEL_X - 2f, 242f, SHOVEL_X - 20f, 228f, 4f, 3.5f), 0xFFE8B020.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
            b.body(limb(SHOVEL_X - 20f, 228f, SHOVEL_X - 28f, 246f, 3.5f, 3f), 0xFFE8B020.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
            b.body(poly(SHOVEL_X - 33f, 244f, SHOVEL_X - 24f, 244f, SHOVEL_X - 25f, 252f, SHOVEL_X - 32f, 251f), 0xFF4A4A50.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val dust = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La pelle creuse : le bras plonge et remonte.
        s.draw(c, ARM, deg = 16f * sin(t * 1.2f), px = SHOVEL_X - 2f, py = 242f)
        // Les camions remontent la piste, chacun dans son nuage de poussière.
        for (k in 0 until 3) {
            val p = ((t + k * 3f) % TRIP) / TRIP
            val fade = smooth(p / .08f) * (1f - smooth((p - .92f) / .08f))
            val dx = (ROAD_X1 - ROAD_X0) * p; val dy = (ROAD_Y1 - ROAD_Y0) * p
            for (j in 1..3) {
                val back = j * 7f
                dust.color = withAlpha(0xFFE8D8B8.toInt(), (60 * fade / j).toInt())
                c.drawCircle(ROAD_X0 + dx - back * .9f, ROAD_Y0 + dy - 4f + back * .45f - j * 2f * sin(t * 2f + j), 4f + j * 2.5f, dust)
            }
            s.draw(c, TRUCK, dx, dy, ROAD_DEG, ROAD_X0, ROAD_Y0, (255 * fade).toInt())
        }
    }

    private fun smooth(x: Float): Float {
        val k = x.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    private companion object {
        const val PIT_X = 180f
        const val PIT_Y = 200f
        const val BENCHES = 6
        const val ROAD_X0 = 150f
        const val ROAD_Y0 = 244f
        const val ROAD_X1 = 300f
        const val ROAD_Y1 = 167f
        const val ROAD_DEG = -27f
        const val SHOVEL_X = 214f
        const val TRIP = 9f
        const val TRUCK = 1
        const val ARM = 2
        val ROCKS = intArrayOf(0xFFB0784A.toInt(), 0xFF9A6A48.toInt(), 0xFFC08A5A.toInt(), 0xFF8A6050.toInt())
    }
}
