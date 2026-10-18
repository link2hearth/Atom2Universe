package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scandium — le vélo de course. Une pincée de scandium dans l'aluminium, et le cadre devient
 * plus raide et plus léger. Un cycliste file sur une route de campagne : les roues tournent,
 * les jambes pédalent, les poteaux, les arbres et les nuages défilent chacun à sa vitesse.
 */
internal class SceneScandium : EngravedScene() {

    override val nameRes = R.string.card_scene_racing_bike
    override val noteRes = R.string.card_note_racing_bike
    override val explainRes = R.string.card_explain_racing_bike

    override fun engrave(b: Burin) {
        val far = Path().apply {
            moveTo(40f, 196f); cubicTo(90f, 176f, 140f, 186f, 186f, 182f)
            cubicTo(236f, 178f, 276f, 168f, 320f, 180f); lineTo(320f, 230f); lineTo(40f, 230f); close()
        }
        b.body(far, 0xFF98AE9C.toInt(), .22f, 0f, Fade(0f, 176f, 0f, 226f, 40, 170), outline = .6f, washAlpha = 140)
        val fields = rect(40f, 206f, 320f, 236f)
        b.body(fields, 0xFF9AB06A.toInt(), .3f, 0f, Fade(0f, 206f, 0f, 236f, 60, 170), outline = 0f, washAlpha = 200)
        b.line(40f, 206f, 320f, 206f, .5f, withAlpha(Ink.SEPIA, 160))
        // L'accotement d'herbe, puis la route.
        b.body(rect(40f, 236f, 320f, ROAD_TOP), 0xFF6E8A44.toInt(), .35f, 0f, outline = 0f, washAlpha = 230)
        val road = rect(40f, ROAD_TOP, 320f, 340f)
        b.wash(road, 0xFF6A6A6C.toInt(), 255)
        b.hatchRect(RectF(40f, ROAD_TOP, 320f, 340f), 0f, 1.7f, .45f, Ink.SEPIA, Fade(0f, ROAD_TOP, 0f, 320f, 120, 220))
        b.stipple(road, 500, .45f, withAlpha(0xFFD8D4CC.toInt(), 150), 5)
        b.line(40f, ROAD_TOP, 320f, ROAD_TOP, .9f)
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(WHEEL, RectF(-WR - 3f, -WR - 3f, WR + 3f, WR + 3f)) { b -> wheel(b) },
        Sprite(BIKE, RectF(118f, 150f, 252f, 284f)) { b -> bike(b) },
        Sprite(POPLAR, RectF(-10f, -62f, 10f, 2f)) { b ->
            val crown = Path().apply { moveTo(0f, -60f); cubicTo(10f, -44f, 9f, -14f, 0f, -6f); cubicTo(-9f, -14f, -10f, -44f, 0f, -60f); close() }
            b.line(0f, 0f, 0f, -8f, 2f, Ink.BROWN)
            b.body(crown, 0xFF4E7A3E.toInt(), .45f, 70f, Fade(8f, 0f, -4f, 0f, 230, 20), outline = .7f, washAlpha = 255)
        },
        Sprite(OAK, RectF(-20f, -40f, 20f, 2f)) { b ->
            b.body(limb(0f, 0f, 0f, -14f, 4f, 3f), 0xFF6A4A2E.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
            val crown = Path().apply {
                addCircle(-8f, -22f, 10f, Path.Direction.CW); addCircle(7f, -24f, 11f, Path.Direction.CW); addCircle(0f, -32f, 9f, Path.Direction.CW)
            }
            val merged = Path().apply { op(crown, Path().apply { addCircle(0f, -20f, 8f, Path.Direction.CW) }, Path.Op.UNION) }
            b.body(merged, 0xFF5A8A44.toInt(), .45f, 60f, Fade(16f, -14f, -6f, -34f, 230, 10), outline = .7f, washAlpha = 255)
        },
        Sprite(CLOUD, RectF(-34f, -16f, 34f, 8f)) { b ->
            val cloud = Path().apply {
                addCircle(-16f, -2f, 9f, Path.Direction.CW); addCircle(0f, -7f, 12f, Path.Direction.CW)
                addCircle(16f, -1f, 9f, Path.Direction.CW); addRect(-25f, -2f, 25f, 6f, Path.Direction.CW)
            }
            val merged = Path().apply { op(cloud, rect(-25f, -2f, 25f, 6f), Path.Op.UNION) }
            b.wash(merged, 0xFFFFFFFF.toInt(), 235)
            b.hatch(merged, 0f, 1.8f, .35f, Ink.BROWN, Fade(0f, 6f, 0f, -4f, 130, 0))
            b.stroke(merged, .6f, withAlpha(Ink.BROWN, 200))
        }
    )

    /** Une roue de course : pneu, jante, trente-deux rayons croisés, moyeu. */
    private fun wheel(b: Burin) {
        b.c.drawCircle(0f, 0f, WR, b.pen(Ink.SEPIA, 4.4f))
        b.c.drawCircle(0f, 0f, WR, b.pen(0xFF2E2E30.toInt(), 3.2f))
        b.c.drawCircle(0f, 0f, WR - 3f, b.pen(0xFFB8BEC4.toInt(), 1.8f))
        b.c.drawCircle(0f, 0f, WR - 3f, b.pen(Ink.SEPIA, .4f))
        val spoke = b.pen(withAlpha(0xFF3A3A3C.toInt(), 220), .4f)
        for (k in 0 until 32) {
            val a = k * PI.toFloat() / 16f
            val hub = a + (if (k % 2 == 0) .35f else -.35f)
            b.c.drawLine(cos(hub) * 3f, sin(hub) * 3f, cos(a) * (WR - 4f), sin(a) * (WR - 4f), spoke)
        }
        b.c.drawCircle(0f, 0f, 3.4f, b.pen(0xFFC8CCD0.toInt()))
        b.c.drawCircle(0f, 0f, 3.4f, b.pen(Ink.SEPIA, .6f))
        // La valve, pour voir la roue tourner.
        b.line(0f, -(WR - 4f), 0f, -(WR - 8f), 1.4f, 0xFF8A8E92.toInt())
    }

    /** Le cadre, la selle, le guidon, et le coureur du bassin à la tête (les jambes bougent à part). */
    private fun bike(b: Burin) {
        val frame = 0xFFC83A2A.toInt()
        // Le bras de l'autre côté, derrière.
        b.body(limb(SHOULDER_X - 4f, SHOULDER_Y + 2f, 214f, 200f, 5f, 4f), 0xFF1E3A6A.toInt(), .5f, 60f, outline = .7f, washAlpha = 255)
        b.body(limb(214f, 200f, HAND_X - 2f, HAND_Y, 4f, 3.4f), 0xFFC89A78.toInt(), .5f, 60f, outline = .7f, washAlpha = 255)
        // Le cadre : tubes rouges, avec un filet de lumière.
        for (tube in listOf(
            floatArrayOf(BB_X, BB_Y, SEAT_X, SEAT_Y + 6f, 4.2f),
            floatArrayOf(SEAT_X + 1f, SEAT_Y + 8f, HEAD_X, HEAD_Y, 3.6f),
            floatArrayOf(HEAD_X + 1f, HEAD_Y + 10f, BB_X, BB_Y, 4.6f),
            floatArrayOf(BB_X, BB_Y, REAR_X, WHEEL_Y, 2.6f),
            floatArrayOf(SEAT_X, SEAT_Y + 8f, REAR_X, WHEEL_Y, 2.4f),
            floatArrayOf(HEAD_X, HEAD_Y - 2f, HEAD_X + 3f, HEAD_Y + 12f, 4.6f),
            floatArrayOf(HEAD_X + 3f, HEAD_Y + 12f, FRONT_X, WHEEL_Y, 2.8f)
        )) {
            val p = limb(tube[0], tube[1], tube[2], tube[3], tube[4], tube[4])
            b.wash(p, frame, 255)
            b.stroke(p, .7f)
            b.line(tube[0], tube[1] - tube[4] * .2f, tube[2], tube[3] - tube[4] * .2f, .6f, withAlpha(0xFFFFC8B8.toInt(), 200))
        }
        // La tige de selle et la selle, la potence et le cintre.
        b.line(SEAT_X, SEAT_Y + 6f, SEAT_X - 2f, SEAT_Y - 2f, 2.2f, 0xFF3A3A3C.toInt())
        val saddle = Path().apply { moveTo(SEAT_X - 14f, SEAT_Y - 4f); cubicTo(SEAT_X - 6f, SEAT_Y - 7f, SEAT_X + 4f, SEAT_Y - 6f, SEAT_X + 8f, SEAT_Y - 3f); lineTo(SEAT_X - 12f, SEAT_Y - 1f); close() }
        b.body(saddle, 0xFF1E1E20.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.line(HEAD_X, HEAD_Y - 2f, HEAD_X + 8f, HEAD_Y - 4f, 2.4f, 0xFF3A3A3C.toInt())
        val bar = Path().apply { moveTo(HEAD_X + 8f, HEAD_Y - 4f); cubicTo(HEAD_X + 16f, HEAD_Y - 4f, HEAD_X + 16f, HEAD_Y + 8f, HEAD_X + 8f, HEAD_Y + 10f) }
        b.stroke(bar, 3f, Ink.SEPIA); b.stroke(bar, 1.8f, 0xFF3A3A3C.toInt())
        // Le plateau et la chaîne.
        b.c.drawCircle(BB_X, BB_Y, 8.5f, b.pen(0xFFA8AEB4.toInt()))
        b.c.drawCircle(BB_X, BB_Y, 8.5f, b.pen(Ink.SEPIA, .7f))
        b.c.drawCircle(BB_X, BB_Y, 6f, b.pen(Ink.SEPIA, .4f))
        b.line(BB_X, BB_Y - 8.5f, REAR_X, WHEEL_Y - 3.5f, .9f, 0xFF4A4A4C.toInt())
        b.line(BB_X, BB_Y + 8.5f, REAR_X, WHEEL_Y + 3.5f, .9f, 0xFF4A4A4C.toInt())
        b.c.drawCircle(REAR_X, WHEEL_Y, 3.6f, b.pen(0xFF8A8E92.toInt()))
        // Le coureur : le maillot penché sur le cadre, la tête casquée.
        val torso = Path().apply {
            moveTo(HIP_X - 7f, HIP_Y + 4f)
            cubicTo(HIP_X - 2f, HIP_Y - 16f, SHOULDER_X - 20f, SHOULDER_Y - 8f, SHOULDER_X + 4f, SHOULDER_Y - 5f)
            cubicTo(SHOULDER_X + 8f, SHOULDER_Y, SHOULDER_X + 4f, SHOULDER_Y + 8f, SHOULDER_X - 4f, SHOULDER_Y + 8f)
            cubicTo(SHOULDER_X - 20f, SHOULDER_Y + 6f, HIP_X + 8f, HIP_Y - 2f, HIP_X + 6f, HIP_Y + 6f)
            close()
        }
        b.body(torso, 0xFF2A5AA8.toInt(), .3f, 60f, Fade(0f, SHOULDER_Y + 10f, 0f, SHOULDER_Y - 8f, 230, 0), outline = 1f, washAlpha = 255)
        b.line(HIP_X + 2f, HIP_Y - 8f, SHOULDER_X - 6f, SHOULDER_Y - 2f, 1.6f, 0xFFF4F0E4.toInt())
        // La tête, le casque profilé et les lunettes.
        val hx = SHOULDER_X + 9f; val hy = SHOULDER_Y - 9f
        b.body(ellipse(hx, hy, 6f, 6.4f), 0xFFC89A78.toInt(), .3f, 60f, outline = .8f, washAlpha = 255)
        val helmet = Path().apply {
            moveTo(hx - 12f, hy - 2f); cubicTo(hx - 8f, hy - 10f, hx + 4f, hy - 11f, hx + 8f, hy - 4f)
            lineTo(hx + 6f, hy - 2f); cubicTo(hx, hy - 4f, hx - 6f, hy - 3f, hx - 12f, hy - 2f); close()
        }
        b.body(helmet, 0xFFF4F0E4.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.line(hx - 6f, hy - 6.5f, hx + 3f, hy - 7.5f, 1.4f, 0xFFC83A2A.toInt())
        b.line(hx + 2f, hy, hx + 6.2f, hy - .5f, 1.6f, 0xFF1E1E20.toInt())
        // Le bras de devant, sur les cocottes.
        b.body(limb(SHOULDER_X, SHOULDER_Y + 1f, 216f, 202f, 5.6f, 4.4f), 0xFF2A5AA8.toInt(), .4f, 60f, outline = .8f, washAlpha = 255)
        b.body(limb(216f, 202f, HAND_X, HAND_Y + 1f, 4.4f, 3.8f), 0xFFD8AA88.toInt(), .3f, 60f, outline = .8f, washAlpha = 255)
        b.c.drawCircle(HAND_X, HAND_Y + 1f, 2.6f, b.pen(0xFF1E1E20.toInt()))
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val knee = FloatArray(2)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val road = t * SPEED
        // Les nuages, très loin ; les arbres, au milieu ; les poteaux et les tirets, tout près.
        for (k in 0 until 3) {
            val x = wrap(90f + k * 120f - t * 6f, 360f)
            s.draw(c, CLOUD, dx = x, dy = 92f + k * 18f)
        }
        val treeStep = 74f
        val treeShift = road * .3f
        val first = floor(treeShift / treeStep).toInt()
        for (i in first..first + 5) {
            val x = 30f + i * treeStep - treeShift
            val kind = if (hash(i, 3) < .55f) POPLAR else OAK
            s.draw(c, kind, dx = x + 20f * hash(i, 4), dy = 212f + 6f * hash(i, 5))
        }
        // La route : les tirets de la ligne blanche, et les bornes de l'accotement.
        ink.color = 0xFFF4F0E4.toInt(); ink.strokeWidth = 2.2f; ink.strokeCap = Paint.Cap.BUTT
        var x = -((road) % 44f)
        while (x < 330f) { c.drawLine(x + 40f, 304f, x + 64f, 304f, ink); x += 44f }
        ink.strokeCap = Paint.Cap.ROUND
        val postStep = 60f
        val postFirst = floor(road / postStep).toInt()
        for (i in postFirst..postFirst + 6) {
            val px = 40f + i * postStep - road
            fill.color = 0xFFF4F0E4.toInt()
            c.drawRect(px - 2.4f, 234f, px + 2.4f, 248f, fill)
            fill.color = 0xFFC83A2A.toInt()
            c.drawRect(px - 2.4f, 234f, px + 2.4f, 238f, fill)
            ink.color = Ink.SEPIA; ink.strokeWidth = .6f
            c.drawRect(px - 2.4f, 234f, px + 2.4f, 248f, ink)
        }
        // L'ombre sous le vélo.
        fill.color = withAlpha(0xFF1E1E20.toInt(), 70)
        c.drawOval(REAR_X - 34f, WHEEL_Y + WR - 2f, FRONT_X + 34f, WHEEL_Y + WR + 4f, fill)
        // Les roues, qui roulent sans glisser sur la route.
        val spin = road / WR * 180f / PI.toFloat()
        for (wx in floatArrayOf(REAR_X, FRONT_X)) s.draw(c, WHEEL, dx = wx, dy = WHEEL_Y, deg = spin, px = 0f, py = 0f)
        // Le pédalier : la jambe de l'autre côté, le vélo et le coureur, puis la jambe de devant.
        val crank = t * CADENCE
        leg(c, crank + PI.toFloat(), far = true)
        s.draw(c, BIKE)
        leg(c, crank, far = false)
    }

    /** Une jambe du bassin à la pédale, le genou trouvé par les longueurs de la cuisse et du mollet. */
    private fun leg(c: Canvas, crank: Float, far: Boolean) {
        val pxp = BB_X + cos(crank) * CRANK; val pyp = BB_Y + sin(crank) * CRANK
        solveKnee(HIP_X, HIP_Y, pxp, pyp)
        val shade = if (far) .72f else 1f
        val shorts = tint(0xFF1E2A4A.toInt(), shade); val skin = tint(0xFFD8AA88.toInt(), shade)
        // La manivelle.
        ink.color = Ink.SEPIA; ink.strokeWidth = 3f
        c.drawLine(BB_X, BB_Y, pxp, pyp, ink)
        ink.color = 0xFFB8BEC4.toInt(); ink.strokeWidth = 1.8f
        c.drawLine(BB_X, BB_Y, pxp, pyp, ink)
        // Le mollet, la cuisse, la chaussure.
        stroke(c, knee[0], knee[1], pxp, pyp - 2f, 7f, skin)
        ink.color = 0xFFF4F0E4.toInt(); ink.strokeWidth = 6.4f
        c.drawLine(pxp - (pxp - knee[0]) * .18f, pyp - 2f - (pyp - 2f - knee[1]) * .18f, pxp, pyp - 2f, ink)
        stroke(c, HIP_X, HIP_Y, knee[0], knee[1], 9.5f, shorts)
        ink.color = Ink.SEPIA; ink.strokeWidth = 6.6f
        c.drawLine(pxp - 5f, pyp - 1f, pxp + 7f, pyp + .5f, ink)
        ink.color = tint(0xFFF4F0E4.toInt(), shade); ink.strokeWidth = 4.6f
        c.drawLine(pxp - 5f, pyp - 1f, pxp + 7f, pyp + .5f, ink)
        fill.color = 0xFF8A8E92.toInt()
        c.drawCircle(pxp, pyp, 1.6f, fill)
    }

    /** Un membre : contour d'encre, couleur, et un filet de lumière sur le dessus. */
    private fun stroke(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int) {
        ink.color = Ink.SEPIA; ink.strokeWidth = w + 1.6f
        c.drawLine(x0, y0, x1, y1, ink)
        ink.color = color; ink.strokeWidth = w
        c.drawLine(x0, y0, x1, y1, ink)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 70); ink.strokeWidth = w * .3f
        c.drawLine(x0 - w * .15f, y0 - w * .25f, x1 - w * .15f, y1 - w * .25f, ink)
    }

    /** Le genou : l'intersection des deux cercles (cuisse autour du bassin, mollet autour de la pédale), vers l'avant. */
    private fun solveKnee(hx: Float, hy: Float, px: Float, py: Float) {
        val dx = px - hx; val dy = py - hy
        val d = hypot(dx, dy).coerceIn(abs1(THIGH - SHIN) + .01f, THIGH + SHIN - .01f)
        val a = (THIGH * THIGH - SHIN * SHIN + d * d) / (2f * d)
        val h = sqrt((THIGH * THIGH - a * a).coerceAtLeast(0f))
        val ux = dx / d; val uy = dy / d
        val mx = hx + ux * a; val my = hy + uy * a
        // Des deux solutions, celle qui pointe vers l'avant (le guidon).
        val k1x = mx - uy * h; val k2x = mx + uy * h
        if (k1x > k2x) { knee[0] = k1x; knee[1] = my + ux * h } else { knee[0] = k2x; knee[1] = my - ux * h }
    }

    private fun abs1(v: Float) = if (v < 0f) -v else v

    private fun tint(color: Int, k: Float): Int {
        if (k >= 1f) return color
        val r = ((color shr 16) and 0xFF) * k; val g = ((color shr 8) and 0xFF) * k; val bl = (color and 0xFF) * k
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or bl.toInt()
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
        const val ROAD_TOP = 246f
        const val WR = 27f
        const val WHEEL_Y = 268f
        const val REAR_X = 140f
        const val FRONT_X = 220f
        const val BB_X = 172f
        const val BB_Y = 270f
        const val SEAT_X = 162f
        const val SEAT_Y = 214f
        const val HEAD_X = 208f
        const val HEAD_Y = 222f
        const val HIP_X = 160f
        const val HIP_Y = 206f
        const val SHOULDER_X = 198f
        const val SHOULDER_Y = 184f
        const val HAND_X = 223f
        const val HAND_Y = 220f
        const val THIGH = 39f
        const val SHIN = 40f
        const val CRANK = 11f

        /** Vitesse de la route (px/s) et du pédalier (rad/s). */
        const val SPEED = 150f
        const val CADENCE = 3.4f

        const val WHEEL = 1
        const val BIKE = 2
        const val POPLAR = 3
        const val OAK = 4
        const val CLOUD = 5
    }
}
