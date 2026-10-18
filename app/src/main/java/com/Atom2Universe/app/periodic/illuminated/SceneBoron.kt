package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bore — le verre qui va au feu. Un peu de bore dans le verre, et il supporte la flamme sans
 * éclater : c'est le verre des bouilloires transparentes et des plats qui vont au four. Sur la
 * gazinière, la bouilloire de verre : les flammes bleues lèchent son fond, l'eau bout à gros
 * bouillons, la vapeur file par le bec.
 */
internal class SceneBoron : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_glass_kettle
    override val noteRes = R.string.card_note_glass_kettle
    override val explainRes = R.string.card_explain_glass_kettle

    /** Le corps de la bouilloire, et l'eau qu'il contient. */
    private val body = Path().apply {
        moveTo(152f, BASE_Y)
        cubicTo(122f, BASE_Y - 4f, 126f, 178f, 140f, 164f)
        cubicTo(146f, 158f, 152f, 154f, 154f, NECK_Y)
        lineTo(206f, NECK_Y)
        cubicTo(208f, 154f, 214f, 158f, 220f, 164f)
        cubicTo(234f, 178f, 238f, BASE_Y - 4f, 208f, BASE_Y)
        close()
    }
    private val water = Path().apply { op(body, rect(100f, WATER_Y, 260f, 260f), Path.Op.INTERSECT) }

    /** Les bulles : x de départ, période, phase, taille. */
    private val bubbles = FloatArray(BUBBLES * 4)

    init {
        val rnd = kotlin.random.Random(9)
        for (k in 0 until BUBBLES) {
            bubbles[k * 4] = 140f + rnd.nextFloat() * 80f
            bubbles[k * 4 + 1] = .9f + rnd.nextFloat() * .9f
            bubbles[k * 4 + 2] = rnd.nextFloat()
            bubbles[k * 4 + 3] = .7f + rnd.nextFloat() * .9f
        }
    }

    override fun engrave(b: Burin) {
        wall(b)
        stove(b)
        kettle(b)
    }

    /** Le carrelage vert d'eau de la cuisine, en briquettes (la vapeur blanche s'y voit), et la lumière d'une fenêtre. */
    private fun wall(b: Burin) {
        val wall = rect(40f, 50f, 320f, TOP)
        b.wash(wall, 0xFF8EB8B4.toInt(), 255)
        b.glow(96f, 110f, 170f, 0xFFFFF4DC.toInt(), 110)
        b.washGradient(wall, 0xFF6A7A78.toInt(), 0f, 120f, 0, 0f, TOP, 110)
        val grout = b.pen(withAlpha(0xFFE4EEEA.toInt(), 210), .7f)
        var y = TOP - 13f; var row = 0
        while (y > 50f) {
            b.c.drawLine(40f, y, 320f, y, grout)
            var x = 40f + (row % 2) * 13f
            while (x < 320f) { b.c.drawLine(x, y, x, y + 13f, grout); x += 26f }
            y -= 13f; row++
        }
        b.hatchRect(RectF(40f, 50f, 320f, TOP), 90f, 3.2f, .35f, Ink.BROWN, Fade(0f, 90f, 0f, TOP, 20, 120))
    }

    /** La gazinière émaillée : le plateau, les deux feux, la façade, le four et ses boutons. */
    private fun stove(b: Burin) {
        val top = rect(40f, TOP, 320f, TOP + 14f)
        b.wash(top, 0xFFEDEAE0.toInt(), 255)
        b.hatchRect(RectF(40f, TOP, 320f, TOP + 14f), 0f, 1.8f, .4f, Ink.SEPIA, Fade(0f, TOP, 0f, TOP + 14f, 30, 150))
        b.line(40f, TOP, 320f, TOP, .9f)
        val front = rect(40f, TOP + 14f, 320f, 340f)
        b.wash(front, 0xFFF2F0E8.toInt(), 255)
        b.hatchRect(RectF(40f, TOP + 14f, 320f, 340f), 90f, 2.6f, .4f, Ink.SEPIA, Fade(0f, TOP + 14f, 0f, 330f, 40, 160))
        b.line(40f, TOP + 14f, 320f, TOP + 14f, 1.2f)
        // Les boutons.
        for (x in floatArrayOf(66f, 96f, 264f, 294f)) {
            val knob = ellipse(x, TOP + 26f, 7f, 6f)
            b.body(knob, 0xFF2A2624.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.line(x, TOP + 21f, x, TOP + 25f, 1.2f, 0xFFE8E4DA.toInt())
        }
        // La porte du four et sa vitre.
        val door = Path().apply { addRoundRect(120f, TOP + 36f, 240f, 350f, 6f, 6f, Path.Direction.CW) }
        b.wash(door, 0xFF2A2A2E.toInt(), 255)
        b.hatch(door, 45f, 2.4f, .4f, 0xFF5A5A62.toInt())
        b.stroke(door, 1f)
        b.line(124f, TOP + 30f, 236f, TOP + 30f, 3f, 0xFF9A9CA0.toInt())
        b.line(124f, TOP + 30f, 236f, TOP + 30f, 1f, 0xFFE8EAEC.toInt())
        // Le feu du fond, éteint, et sa grille.
        burner(b, 82f, TOP + 6f, 16f, 3.4f)
        // Le grand feu, sous la bouilloire.
        burner(b, CX, BURNER_Y, 24f, 5f)
    }

    private fun burner(b: Burin, x: Float, y: Float, rx: Float, ry: Float) {
        b.body(ellipse(x, y + 1.5f, rx + 6f, ry + 2f), 0xFF8A8A88.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(ellipse(x, y, rx, ry), 0xFF34302C.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(x, y - 1.2f, rx * .55f, ry * .55f), 0xFF5A5450.toInt(), 0f, outline = .6f, washAlpha = 255)
        // La grille de fonte : deux barres, et leurs griffes.
        val iron = 0xFF1E1C1A.toInt()
        b.line(x - rx - 16f, y - ry * .2f, x + rx + 16f, y - ry * .2f, 3f, iron)
        for (k in floatArrayOf(-1f, 1f)) b.line(x + k * (rx + 2f), y - ry - 3f, x + k * (rx + 2f), y + ry, 3f, iron)
    }

    private fun kettle(b: Burin) {
        // La poignée et le bec, en verre épais.
        val handle = Path().apply {
            moveTo(214f, 160f); cubicTo(240f, 154f, 254f, 170f, 248f, 192f)
            cubicTo(244f, 206f, 234f, 214f, 226f, 216f)
        }
        glassTube(b, handle, 7f)
        val spout = Path().apply { moveTo(136f, 192f); cubicTo(124f, 184f, 118f, 168f, 112f, SPOUT_Y + 2f) }
        glassTube(b, spout, 8f)
        b.body(ellipse(SPOUT_X, SPOUT_Y, 4.6f, 2f), 0xFFDCE8EC.toInt(), 0f, outline = .7f, washAlpha = 150)
        // Le verre : presque rien, juste un lavis, ses bords et ses reflets.
        b.wash(body, 0xFFDDEFF2.toInt(), 70)
        b.wash(water, 0xFF9CC4D8.toInt(), 120)
        b.washGradient(water, 0xFF4A7A9A.toInt(), 0f, BASE_Y, 140, 0f, WATER_Y, 0)
        b.hatch(water, 0f, 2.6f, .35f, 0xFF2A4A60.toInt(), Fade(0f, BASE_Y, 0f, WATER_Y + 10f, 150, 0))
        b.stroke(body, 2.2f, withAlpha(Ink.SEPIA, 230))
        b.stroke(body, .8f, withAlpha(0xFFFFFFFF.toInt(), 200))
        // L'épaisseur du fond, comme une lentille.
        b.body(ellipse(CX, BASE_Y - 2.5f, 26f, 3f), 0xFFB8D8E4.toInt(), 0f, outline = .6f, washAlpha = 160)
        // Reflets de la fenêtre sur la panse.
        b.fill(Path().apply { addRoundRect(136f, 172f, 141f, 214f, 2.5f, 2.5f, Path.Direction.CW) }, withAlpha(0xFFFFFFFF.toInt(), 190))
        b.fill(Path().apply { addRoundRect(144f, 168f, 146.5f, 186f, 1.2f, 1.2f, Path.Direction.CW) }, withAlpha(0xFFFFFFFF.toInt(), 150))
        b.fill(Path().apply { addRoundRect(222f, 180f, 225f, 206f, 1.5f, 1.5f, Path.Direction.CW) }, withAlpha(0xFFFFFFFF.toInt(), 110))
        // Le couvercle de verre et son bouton.
        val lid = Path().apply {
            moveTo(150f, NECK_Y + 1f); cubicTo(156f, NECK_Y - 12f, 204f, NECK_Y - 12f, 210f, NECK_Y + 1f); close()
        }
        b.wash(lid, 0xFFDDEFF2.toInt(), 110)
        b.stroke(lid, 1.6f, withAlpha(Ink.SEPIA, 230))
        b.line(156f, NECK_Y - 3f, 170f, NECK_Y - 7f, .9f, withAlpha(0xFFFFFFFF.toInt(), 220))
        val knob = ellipse(CX, NECK_Y - 13f, 5f, 4f)
        b.body(knob, 0xFFCFE4EA.toInt(), .2f, 0f, outline = 1f, washAlpha = 200)
        b.line(CX, NECK_Y - 9f, CX, NECK_Y - 6f, 2f, withAlpha(Ink.SEPIA, 200))
        b.line(152f, NECK_Y + 1f, 208f, NECK_Y + 1f, 1.4f, withAlpha(Ink.SEPIA, 230))
    }

    /** Un tube de verre plein : bord sombre, cœur clair, et le reflet. */
    private fun glassTube(b: Burin, path: Path, w: Float) {
        b.stroke(path, w + 2f, withAlpha(Ink.SEPIA, 220))
        b.stroke(path, w, 0xFFD6E8EC.toInt())
        b.stroke(path, w * .3f, withAlpha(0xFFFFFFFF.toInt(), 220))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(STEAM, RectF(-20f, -20f, 20f, 20f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 20f,
                intArrayOf(0xCCFFFFFF.toInt(), 0x66F4F6F8, 0x00F4F6F8), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 20f, b.p)
        },
        Sprite(GLOW, RectF(-40f, -40f, 40f, 40f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 40f,
                intArrayOf(0x995AA8FF.toInt(), 0x333A7AE0, 0x003A7AE0), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 40f, b.p)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val flame = Path()
    private val surface = FloatArray(SURFACE_N * 4)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La lueur bleue des flammes sur le fond de verre.
        c.save(); c.translate(CX, BURNER_Y - 4f); c.scale(1.1f, .5f)
        s.draw(c, GLOW, alpha = (200 + 40 * sin(t * 13f)).toInt())
        c.restore()
        // L'eau qui bout : les bulles montent en grossissant, la surface frémit.
        c.save(); c.clipPath(water)
        for (k in 0 until BUBBLES) {
            val life = (t / bubbles[k * 4 + 1] + bubbles[k * 4 + 2]) % 1f
            val y = BASE_Y - 4f - life * (BASE_Y - 4f - WATER_Y)
            val x = bubbles[k * 4] + 2.2f * sin(life * 9f + k)
            val r = bubbles[k * 4 + 3] * (1f + life * 1.6f)
            ring.color = withAlpha(0xFFFFFFFF.toInt(), (230 * (1f - life * .4f)).toInt()); ring.strokeWidth = .6f
            c.drawCircle(x, y, r, ring)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), 200)
            c.drawCircle(x - r * .35f, y - r * .35f, r * .3f, fill)
        }
        for (i in 0 until SURFACE_N) {
            val x0 = 128f + i * (104f / SURFACE_N); val x1 = x0 + 104f / SURFACE_N
            surface[i * 4] = x0; surface[i * 4 + 1] = level(x0, t)
            surface[i * 4 + 2] = x1; surface[i * 4 + 3] = level(x1, t)
        }
        ring.color = withAlpha(0xFFFFFFFF.toInt(), 220); ring.strokeWidth = 1f
        c.drawLines(surface, ring)
        c.restore()
        // Les flammes bleues, tout autour du brûleur.
        for (k in 0 until FLAMES) {
            val a = k * 2f * PI.toFloat() / FLAMES
            val x = CX + cos(a) * 22f
            val y = BURNER_Y + sin(a) * 4.4f
            val h = 6f + 3f * (.5f + .5f * sin(t * 17f + k * 2.3f)) + 1.5f * sin(t * 7f + k)
            flame.reset()
            flame.moveTo(x - 2.4f, y)
            flame.cubicTo(x - 2.6f, y - h * .5f, x - .6f, y - h * .8f, x + cos(a) * 1.5f, y - h)
            flame.cubicTo(x + .6f, y - h * .8f, x + 2.6f, y - h * .5f, x + 2.4f, y)
            flame.close()
            fill.color = withAlpha(0xFF3A6AE0.toInt(), 200)
            c.drawPath(flame, fill)
            fill.color = withAlpha(0xFFB8E0FF.toInt(), 220)
            c.drawOval(x - 1f, y - h * .45f, x + 1f, y, fill)
        }
        // La vapeur file par le bec, monte et s'étale.
        for (k in 0 until PUFFS) {
            val life = (t / PUFF_LIFE + k.toFloat() / PUFFS) % 1f
            val x = SPOUT_X - 4f - life * 22f + 4f * sin(t * 1.3f + k * 1.7f) * life
            val y = SPOUT_Y - 4f - life * 70f
            val sc = .25f + life * .9f
            val a = (if (life < .12f) life / .12f else 1f) * (1f - life)
            c.save(); c.translate(x, y); c.scale(sc, sc)
            s.draw(c, STEAM, alpha = (230 * a).toInt())
            c.restore()
        }
    }

    /** La surface de l'eau qui frémit, à l'abscisse [x]. */
    private fun level(x: Float, t: Float) = WATER_Y + 1.3f * sin(x * .21f + t * 7f) + .9f * sin(x * .37f - t * 5.3f)

    private companion object {
        const val TOP = 228f
        const val CX = 180f
        const val BASE_Y = 230f
        const val NECK_Y = 150f
        const val WATER_Y = 182f
        const val BURNER_Y = 236f
        const val SPOUT_X = 112f
        const val SPOUT_Y = 150f
        const val STEAM = 1
        const val GLOW = 2
        const val BUBBLES = 24
        const val FLAMES = 14
        const val PUFFS = 8
        const val PUFF_LIFE = 2.8f
        const val SURFACE_N = 26
    }
}
