package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Titane — l'avion de ligne. Aussi solide que l'acier, presque deux fois plus léger, et il ne
 * rouille pas : le titane tient les trains d'atterrissage, les moteurs et les attaches des ailes.
 * L'avion file au-dessus d'une mer de nuages : les nuages défilent dessous, les traînées de
 * condensation s'allongent derrière les moteurs, les feux clignotent.
 */
internal class SceneTitanium : EngravedScene() {

    override val nameRes = R.string.card_scene_airliner
    override val noteRes = R.string.card_note_airliner
    override val explainRes = R.string.card_explain_airliner

    override fun engrave(b: Burin) {
        // Le ciel d'altitude, plus profond en haut, et le soleil.
        val sky = rect(40f, 50f, 320f, 260f)
        b.washGradient(sky, 0xFF2A5A9A.toInt(), 0f, 56f, 170, 0f, 210f, 0)
        b.glow(292f, 84f, 100f, 0xFFFFF4D8.toInt(), 220)
        b.c.drawCircle(292f, 84f, 9f, b.pen(0xFFFFFAEC.toInt()))
        // Au loin, le bord de la mer de nuages.
        val bank = Path().apply {
            moveTo(40f, 222f)
            var x = 40f; val rnd = Random(6)
            while (x < 320f) { val w = 10f + rnd.nextFloat() * 14f; val h = 5f + rnd.nextFloat() * 5f; cubicTo(x, 222f - h, x + w, 222f - h, x + w, 222f); x += w * .8f }
            lineTo(320f, 240f); lineTo(40f, 240f); close()
        }
        b.body(bank, 0xFFE8EEF4.toInt(), .2f, 0f, Fade(0f, 222f, 0f, 240f, 0, 140), outline = .5f, washAlpha = 230)
        // La mer de nuages, toute proche.
        val sea = Path().apply {
            moveTo(40f, 244f)
            var x = 40f; val rnd = Random(8)
            while (x < 320f) { val w = 18f + rnd.nextFloat() * 22f; val h = 8f + rnd.nextFloat() * 9f; cubicTo(x, 244f - h, x + w, 244f - h, x + w, 244f); x += w * .78f }
            lineTo(320f, 340f); lineTo(40f, 340f); close()
        }
        b.wash(sea, 0xFFF6F8FA.toInt(), 255)
        b.hatch(sea, 0f, 2f, .4f, 0xFF4A5A78.toInt(), Fade(0f, 236f, 0f, 320f, 20, 160))
        b.stroke(sea, .6f, withAlpha(0xFF4A5A78.toInt(), 200))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(PLANE, RectF(-124f, -48f, 120f, 40f)) { b -> plane(b) },
        Sprite(CUMULUS, RectF(-50f, -24f, 50f, 12f)) { b ->
            val cloud = Path().apply {
                addCircle(-28f, 0f, 11f, Path.Direction.CW); addCircle(-10f, -8f, 15f, Path.Direction.CW)
                addCircle(12f, -6f, 13f, Path.Direction.CW); addCircle(30f, 0f, 10f, Path.Direction.CW)
            }
            val merged = Path().apply { op(cloud, rect(-38f, -2f, 40f, 10f), Path.Op.UNION) }
            b.wash(merged, 0xFFFFFFFF.toInt(), 250)
            b.hatch(merged, 0f, 1.8f, .4f, 0xFF4A5A78.toInt(), Fade(0f, 10f, 0f, -8f, 170, 0))
            b.stroke(merged, .6f, withAlpha(0xFF4A5A78.toInt(), 220))
        },
        Sprite(CIRRUS, RectF(-44f, -6f, 44f, 6f)) { b ->
            val wisp = b.pen(withAlpha(0xFFFFFFFF.toInt(), 170), 1.6f)
            for (k in 0 until 5) {
                val y = -4f + k * 2f
                b.c.drawLine(-40f + k * 6f, y, 30f + k * 3f, y - 1f, wisp)
            }
        }
    )

    /** L'avion vu de profil, un peu par-dessous : le fuselage, l'aile et son moteur, la dérive. */
    private fun plane(b: Burin) {
        // La dérive.
        val fin = poly(-70f, -9f, -100f, -44f, -114f, -44f, -112f, -9f)
        b.wash(fin, 0xFF1E6A7A.toInt(), 255)
        b.hatch(fin, 70f, 1.8f, .4f, Ink.SEPIA, Fade(-70f, 0f, -110f, 0f, 200, 0))
        b.c.save(); b.c.clipPath(fin)
        b.line(-86f, -12f, -114f, -40f, 3f, 0xFFF4F0E4.toInt())
        b.line(-80f, -12f, -108f, -40f, 1.4f, Gilding.LEAF)
        b.c.restore()
        b.stroke(fin, .9f)
        // Le fuselage : un tube blanc, ombré dessous.
        val body = Path().apply {
            moveTo(-116f, -10f); lineTo(78f, -10f)
            cubicTo(100f, -10f, 112f, -5f, 116f, 2f)
            cubicTo(113f, 8f, 102f, 10f, 90f, 10f)
            lineTo(-50f, 10f)
            cubicTo(-72f, 10f, -98f, 2f, -116f, -6f)
            close()
        }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, -10f, 0f, 10f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F0EC.toInt(), 0xFFB4BAC2.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(body, b.p)
        b.hatch(body, 0f, 1.6f, .4f, Ink.SEPIA, Fade(0f, 10f, 0f, 1f, 180, 0))
        // La bande de couleur, les hublots, les portes, le cockpit.
        b.c.save(); b.c.clipPath(body)
        b.fill(rect(-116f, 1.5f, 120f, 4f), 0xFF1E6A7A.toInt())
        b.fill(rect(-116f, 4f, 120f, 5f), Gilding.LEAF)
        b.c.restore()
        val pane = b.pen(0xFF2A3A4A.toInt())
        var x = -78f
        while (x < 84f) {
            if (x !in -62f..-56f && x !in 26f..32f) b.c.drawOval(x - 1.2f, -5f, x + 1.2f, -2f, pane)
            x += 4.4f
        }
        for (dx in floatArrayOf(-59f, 29f, 80f)) {
            b.stroke(Path().apply { addRoundRect(dx - 3f, -7f, dx + 3f, 6f, 1.5f, 1.5f, Path.Direction.CW) }, .5f, withAlpha(Ink.SEPIA, 200))
        }
        b.fill(poly(97f, -7f, 106f, -6f, 109f, -2.5f, 98f, -3f), 0xFF1E2A36.toInt())
        b.stroke(body, 1f)
        // Le stabilisateur de ce côté.
        val tail = poly(-92f, 3f, -104f, 1f, -124f, 13f, -116f, 14f)
        b.body(tail, 0xFFDCE0E4.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // L'aile de ce côté, qui descend vers nous, et son winglet.
        val wing = poly(30f, 6f, -22f, 7f, -54f, 31f, -42f, 31f)
        b.wash(wing, 0xFFDCE0E4.toInt(), 255)
        b.hatch(wing, 30f, 1.8f, .4f, Ink.SEPIA, Fade(0f, 30f, 0f, 8f, 200, 30))
        b.stroke(wing, .9f)
        b.body(poly(-42f, 31f, -54f, 31f, -60f, 22f, -55f, 22f), 0xFF1E6A7A.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        for (k in 0 until 3) {
            val u = .3f + k * .22f
            val fx = -22f + (-54f + 22f) * u; val fy = 7f + 24f * u
            b.line(fx, fy, fx - 7f, fy + 1f, 1.4f, 0xFF9AA0A8.toInt())
        }
        // Le réacteur sous l'aile, son mât et sa tuyère.
        b.body(poly(18f, 10f, 34f, 10f, 30f, 17f, 20f, 17f), 0xFFC8CCD2.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(poly(8f, 19.5f, 16f, 17.5f, 16f, 26.5f, 8f, 24.5f), 0xFF8A9098.toInt(), .4f, 0f, outline = .7f, washAlpha = 255)
        val nacelle = Path().apply { addRoundRect(14f, 15f, 50f, 29f, 6.5f, 6.5f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 15f, 0f, 29f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFE4E6E8.toInt(), 0xFF9AA0A8.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(nacelle, b.p)
        b.hatch(nacelle, 0f, 1.5f, .4f, Ink.SEPIA, Fade(0f, 29f, 0f, 22f, 180, 0))
        b.stroke(nacelle, .9f)
        b.body(ellipse(49f, 22f, 2.6f, 6.6f), 0xFF2A2A30.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.line(30f, 15.5f, 46f, 15.5f, .8f, 0xFFFFFFFF.toInt())
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailShader = LinearGradient(0f, 0f, -TRAIL_LEN, 0f,
        intArrayOf(0xE6FFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = trailShader }
    private val trailMatrix = Matrix()
    private val trailPath = Path()
    private val trailBottom = FloatArray(TRAIL_N * 2)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les cirrus, haut et lentement ; les cumulus, juste sous l'avion, plus vite.
        for (k in 0 until 2) {
            val x = wrap(120f + k * 170f - t * 8f, 360f) + 20f
            s.draw(c, CIRRUS, dx = x, dy = 74f + k * 26f, alpha = 200)
        }
        val step = 116f
        val shift = t * 34f
        val first = floor(shift / step).toInt()
        for (i in first..first + 4) {
            val x = 20f + i * step - shift
            c.save(); c.translate(x + 30f * hash(i, 1), 262f + 26f * hash(i, 2))
            val sc = .7f + .5f * hash(i, 3)
            c.scale(sc, sc)
            s.draw(c, CUMULUS)
            c.restore()
        }
        // L'avion tangue à peine.
        val py = PY + 2f * sin(t * .7f)
        val deg = -4f + .8f * sin(t * .5f)
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val ca = cos(rad); val sa = sin(rad)
        // Les traînées : elles partent un peu derrière les réacteurs et s'élargissent en pâlissant.
        for ((lx, ly) in TRAILS) {
            val ex = PX + lx * ca - ly * sa; val ey = py + lx * sa + ly * ca
            // Une seule forme pleine, bord haut puis bord bas : aucun raccord ne se voit. Elle
            // s'élargit en s'éloignant, et le dégradé la fait pâlir jusqu'à disparaître.
            trailPath.reset()
            var n = 0
            for (k in 0 until TRAIL_N) {
                val d = 12f + k * 6f
                val x = ex - d
                val y = ey + d * .03f + .6f * sin(d * .2f - t * 2f)
                val w = 2.6f + d * .07f + .6f * sin(d * .33f - t * 2f)
                if (k == 0) trailPath.moveTo(x, y - w / 2f) else trailPath.lineTo(x, y - w / 2f)
                trailBottom[n * 2] = x; trailBottom[n * 2 + 1] = y + w / 2f; n++
                if (x < 30f) break
            }
            for (i in n - 1 downTo 0) trailPath.lineTo(trailBottom[i * 2], trailBottom[i * 2 + 1])
            trailPath.close()
            trailMatrix.setTranslate(ex - 12f, 0f)
            trailShader.setLocalMatrix(trailMatrix)
            c.drawPath(trailPath, trail)
        }
        c.save(); c.translate(PX, py); c.rotate(deg)
        s.draw(c, PLANE)
        // Les feux : le gyrophare rouge, et les flashs blancs en bout d'aile et en queue.
        val beacon = (t % 1.1f) < .12f
        if (beacon) {
            fill.color = 0xFFFF3A2A.toInt()
            c.drawCircle(-6f, -10.5f, 2f, fill); c.drawCircle(4f, 10.5f, 2f, fill)
            fill.color = withAlpha(0xFFFF3A2A.toInt(), 80)
            c.drawCircle(-6f, -10.5f, 6f, fill); c.drawCircle(4f, 10.5f, 6f, fill)
        }
        val strobe = t % 1.4f
        if (strobe < .06f || strobe in .16f..0.22f) {
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(-58f, 23f, 2.2f, fill); c.drawCircle(-117f, -7f, 2f, fill)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), 90)
            c.drawCircle(-58f, 23f, 8f, fill); c.drawCircle(-117f, -7f, 7f, fill)
        }
        // Le soleil glisse sur le dessus du fuselage.
        val gx = 20f + 40f * sin(t * .5f)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 230)
        c.drawOval(gx - 14f, -9.4f, gx + 14f, -7.6f, fill)
        c.restore()
    }

    private fun wrap(x: Float, span: Float): Float {
        val m = x % span
        return if (m < 0f) m + span else m
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val PX = 186f
        const val PY = 156f
        const val TRAIL_N = 46
        const val TRAIL_LEN = 288f
        const val PLANE = 1
        const val CUMULUS = 2
        const val CIRRUS = 3
        /** Les sorties des deux réacteurs, dans le repère de l'avion : celui d'en face, celui de ce côté. */
        val TRAILS = listOf(4f to 14f, 9f to 22f)
    }
}
