package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Hydrogène — le Soleil, une boule d'hydrogène qui fusionne en hélium. Gravé comme une planche
 * d'observatoire : le disque assombri vers le bord, sa granulation, des taches qui passent avec
 * la rotation, et sur le bord les protubérances rouges, la lumière même de l'hydrogène.
 */
internal class SceneHydrogen : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_sun_hydrogen
    override val noteRes = R.string.card_note_sun_hydrogen
    override val explainRes = R.string.card_explain_sun_hydrogen

    override fun engrave(b: Burin) {
        corona(b)
        disc(b)
        chromosphere(b)
    }

    /** La couronne : une lueur et de fins rayons, plus longs à l'équateur. */
    private fun corona(b: Burin) {
        b.glow(CX, CY, RAD * 1.8f, 0xFFFFD98A.toInt(), 130)
        val rnd = Random(5)
        val n = 320
        val pts = FloatArray(n * 4)
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val equator = .45f + .55f * cos(a).pow(2)
            val len = RAD * (.06f + rnd.nextFloat().pow(2) * .6f) * equator
            val r0 = RAD + 2f
            pts[i * 4] = CX + cos(a) * r0; pts[i * 4 + 1] = CY + sin(a) * r0
            pts[i * 4 + 2] = CX + cos(a) * (r0 + len); pts[i * 4 + 3] = CY + sin(a) * (r0 + len)
        }
        b.pen(0xFFFFF2CC.toInt(), .5f).shader = RadialGradient(CX, CY, RAD * 1.7f,
            intArrayOf(0xEEFFF2CC.toInt(), 0xEEFFF2CC.toInt(), 0x00FFF2CC), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawLines(pts, b.p)
    }

    private fun disc(b: Burin) {
        val disc = ellipse(CX, CY, RAD, RAD)
        // L'assombrissement centre-bord : blanc-jaune au centre, orangé au bord.
        b.pen(0xFF000000.toInt()).shader = RadialGradient(CX, CY, RAD,
            intArrayOf(0xFFFFF6D6.toInt(), 0xFFFBDD7E.toInt(), 0xFFF2AA46.toInt(), 0xFFD86A2A.toInt()),
            floatArrayOf(0f, .5f, .84f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(disc, b.p)
        // La granulation : des cellules serrées, écrasées vers le bord comme sur une sphère.
        val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = .45f; color = withAlpha(0xFF9A4418.toInt(), 45)
        }
        val rnd = Random(8)
        b.c.save(); b.c.clipPath(disc)
        repeat(1500) {
            val rr = RAD * sqrt(rnd.nextFloat()); val a = rnd.nextFloat() * 2 * PI.toFloat()
            val x = CX + cos(a) * rr; val y = CY + sin(a) * rr
            val mu = sqrt(max(0f, 1f - (rr / RAD) * (rr / RAD))).coerceAtLeast(.18f)
            val s = 1.5f + rnd.nextFloat() * 1.3f
            b.c.save(); b.c.rotate(Math.toDegrees(a.toDouble()).toFloat(), x, y)
            b.c.drawOval(x - s * mu, y - s, x + s * mu, y + s, cell)
            b.c.restore()
        }
        b.c.restore()
        b.stipple(disc, 2600, .42f, withAlpha(0xFF8A3A14.toInt(), 80), seed = 9)
        // Facules : des traînées claires près du bord.
        repeat(16) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val rr = RAD * (.86f + rnd.nextFloat() * .1f)
            val sweep = 6f + rnd.nextFloat() * 10f
            val deg = Math.toDegrees(a.toDouble()).toFloat()
            b.c.drawArc(CX - rr, CY - rr, CX + rr, CY + rr, deg, sweep, false, b.pen(withAlpha(0xFFFFF4D0.toInt(), 150), 1.3f))
        }
        // Le bord, gravé en cercles de plus en plus serrés.
        for ((k, d) in floatArrayOf(1.2f, 2.8f, 4.8f, 7.4f, 10.8f, 15f).withIndex()) {
            b.c.drawCircle(CX, CY, RAD - d, b.pen(withAlpha(0xFF8A3414.toInt(), 160 - k * 24), .5f))
        }
        b.c.drawCircle(CX, CY, RAD, b.pen(0xFF6A2A10.toInt(), .9f))
    }

    /** La chromosphère : un liseré rouge hérissé de spicules, tout autour du disque. */
    private fun chromosphere(b: Burin) {
        b.c.drawCircle(CX, CY, RAD + 1.3f, b.pen(withAlpha(H_ALPHA, 210), 2.4f))
        val rnd = Random(12)
        val n = 420
        val pts = FloatArray(n * 4)
        for (i in 0 until n) {
            val a = i * 2 * PI.toFloat() / n + rnd.nextFloat() * .01f
            val len = 1.5f + rnd.nextFloat() * 3.5f
            val tilt = (rnd.nextFloat() - .5f) * .03f
            pts[i * 4] = CX + cos(a) * (RAD + 1.5f); pts[i * 4 + 1] = CY + sin(a) * (RAD + 1.5f)
            pts[i * 4 + 2] = CX + cos(a + tilt) * (RAD + 1.5f + len); pts[i * 4 + 3] = CY + sin(a + tilt) * (RAD + 1.5f + len)
        }
        b.c.drawLines(pts, b.pen(withAlpha(H_ALPHA, 190), .5f))
    }

    // ───────────── taches solaires ─────────────

    override fun sprites() = listOf(
        Sprite(SPOT_BIG, RectF(-18f, -15f, 18f, 15f)) { b -> spot(b, 0f, 0f, 14f, 11f, 5.5f, 1) },
        Sprite(SPOT_MID, RectF(-12f, -10f, 12f, 10f)) { b -> spot(b, 0f, 0f, 9f, 7.5f, 3.6f, 2) },
        Sprite(SPOT_PAIR, RectF(-16f, -9f, 16f, 9f)) { b ->
            spot(b, -7f, 0f, 6.5f, 5.5f, 2.5f, 3); spot(b, 8f, 1f, 5f, 4.3f, 2f, 4)
        }
    )

    /** Une tache : la pénombre, striée de filaments, autour de l'ombre presque noire. */
    private fun spot(b: Burin, x: Float, y: Float, rx: Float, ry: Float, umbra: Float, seed: Int) {
        val rnd = Random(seed)
        val pen = blob(x, y, rx, ry, rnd, .16f)
        b.fill(pen, withAlpha(0xFFA8521F.toInt(), 235))
        val fil = b.pen(withAlpha(0xFF5A2410.toInt(), 210), .45f)
        val n = (rx * 3.2f).toInt()
        for (i in 0 until n) {
            val a = i * 2 * PI.toFloat() / n + rnd.nextFloat() * .1f
            val r1 = .92f + rnd.nextFloat() * .06f
            b.c.drawLine(x + cos(a) * umbra * .9f, y + sin(a) * umbra * .9f, x + cos(a) * rx * r1, y + sin(a) * ry * r1, fil)
        }
        b.stroke(pen, .5f, 0xFF5A2410.toInt())
        val core = blob(x + .6f, y - .3f, umbra, umbra * .85f, rnd, .2f)
        b.fill(core, 0xFF2A1208.toInt())
        b.stroke(core, .4f, Ink.SEPIA)
    }

    private fun blob(x: Float, y: Float, rx: Float, ry: Float, rnd: Random, rough: Float) = android.graphics.Path().apply {
        val n = 16
        for (i in 0..n) {
            val a = i * 2 * PI.toFloat() / n
            val k = if (i == n) 1f else 1f + (rnd.nextFloat() - .5f) * 2 * rough
            val px = x + cos(a) * rx * k; val py = y + sin(a) * ry * k
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    // ───────────── animation ─────────────

    private val coronaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x00FFE6A8, 0x00FFE6A8, 0x77FFE6A8, 0x00FFE6A8),
            floatArrayOf(0f, 1f / 1.7f, 1.04f / 1.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val discClip = ellipse(CX, CY, RAD - .4f, RAD - .4f)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = H_ALPHA }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val knot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pts = FloatArray(2048)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La couronne respire.
        coronaPaint.alpha = (190 + 60 * sin(t * .9f)).toInt()
        c.save(); c.translate(CX, CY); c.scale(RAD * 1.7f, RAD * 1.7f); c.drawCircle(0f, 0f, 1f, coronaPaint); c.restore()

        // Les taches tournent avec le Soleil : un tour en 80 s.
        c.save(); c.clipPath(discClip)
        spotAt(c, s, SPOT_BIG, .26f, -.5f, t)
        spotAt(c, s, SPOT_MID, -.3f, 1.9f, t)
        spotAt(c, s, SPOT_PAIR, .42f, 3.9f, t)
        c.restore()

        // Protubérances : deux arches, une haie de flammes, et une éruption qui monte et se dissipe.
        arch(c, t, -2.56f, -2.16f, 34f, 7, 0f)
        arch(c, t, .52f, .74f, 17f, 5, 2f)
        hedge(c, t, 2.72f, .3f, 22f)
        val p = (t % 13f) / 13f
        val fade = if (p < .62f) min(1f, p / .1f) else max(0f, 1f - (p - .62f) / .38f)
        arch(c, t, -.62f - .08f - p * .14f, -.62f + .08f + p * .14f, 10f + 52f * p.pow(1.4f), 6, 4f, fade, twist = 2.6f)
    }

    private fun spotAt(c: Canvas, s: Sprites, id: Int, lat: Float, lon0: Float, t: Float) {
        val lon = lon0 + t * 2 * PI.toFloat() / 80f
        val cl = cos(lon)
        if (cl < .04f) return
        val x = CX + RAD * cos(lat) * sin(lon)
        val y = CY - RAD * sin(lat)
        c.save(); c.translate(x, y); c.scale(cl, cos(lat))
        s.draw(c, id, alpha = (255 * min(1f, cl / .3f)).toInt())
        c.restore()
    }

    /**
     * Une arche de plasma entre deux pieds pris sur le bord (angles [a0], [a1]), haute de [h].
     * [n] filaments, du plasma qui redescend le long de chacun.
     */
    private fun arch(c: Canvas, t: Float, a0: Float, a1: Float, h: Float, n: Int, phase: Float, alpha: Float = 1f, twist: Float = 0f) {
        if (alpha <= .01f) return
        val steps = 16
        var count = 0
        val breathe = 1f + .07f * sin(t * .7f + phase)
        for (k in 0 until n) {
            val f = k / (n - 1f)
            val spread = (f - .5f) * .03f
            val hk = h * breathe * (.72f + .42f * f)
            val p0x = CX + cos(a0 + spread) * RAD; val p0y = CY + sin(a0 + spread) * RAD
            val p3x = CX + cos(a1 - spread) * RAD; val p3y = CY + sin(a1 - spread) * RAD
            val p1x = p0x + cos(a0) * hk * 1.3f; val p1y = p0y + sin(a0) * hk * 1.3f
            val p2x = p3x + cos(a1) * hk * 1.3f; val p2y = p3y + sin(a1) * hk * 1.3f
            var lx = p0x; var ly = p0y
            for (i in 1..steps) {
                val u = i / steps.toFloat(); val v = 1 - u
                var x = v * v * v * p0x + 3 * v * v * u * p1x + 3 * v * u * u * p2x + u * u * u * p3x
                var y = v * v * v * p0y + 3 * v * v * u * p1y + 3 * v * u * u * p2y + u * u * u * p3y
                if (twist > 0f) {
                    val w = twist * sin(u * PI.toFloat()) * sin(u * 9f + t * 2f + k)
                    val am = (a0 + a1) / 2f
                    x += -sin(am) * w; y += cos(am) * w
                }
                pts[count++] = lx; pts[count++] = ly; pts[count++] = x; pts[count++] = y
                lx = x; ly = y
            }
        }
        glow.alpha = (55 * alpha).toInt(); glow.strokeWidth = 4.5f
        c.drawLines(pts, 0, count, glow)
        ink.color = withAlpha(0xFFFF8E9C.toInt(), (230 * alpha).toInt()); ink.strokeWidth = .9f
        c.drawLines(pts, 0, count, ink)
        // Le plasma qui coule : un point clair par filament, de l'apex vers les pieds.
        knot.color = withAlpha(0xFFFFE0E4.toInt(), (240 * alpha).toInt())
        for (k in 0 until n) {
            val u = (t * .22f + k * .37f + phase) % 1f
            val side = if (k % 2 == 0) .5f - u * .5f else .5f + u * .5f
            val i = (side * steps).toInt().coerceIn(0, steps - 1)
            val base = (k * steps + i) * 4
            c.drawCircle(pts[base], pts[base + 1], 1.1f, knot)
        }
    }

    /** Une haie de flammes : des langues qui montent du bord et ondulent lentement. */
    private fun hedge(c: Canvas, t: Float, angle: Float, span: Float, h: Float) {
        val n = 11
        val steps = 8
        var count = 0
        for (k in 0 until n) {
            val f = k / (n - 1f)
            val a = angle + (f - .5f) * span
            val bell = sin(f * PI.toFloat())
            val hk = h * (.35f + .65f * bell) * (.85f + .25f * sin(t * .8f + k * 1.7f))
            val nx = cos(a); val ny = sin(a); val tx = -ny; val ty = nx
            val bx = CX + nx * RAD; val by = CY + ny * RAD
            var lx = bx; var ly = by
            for (i in 1..steps) {
                val u = i / steps.toFloat()
                val sway = 3f * u * u * sin(t * .9f + k * .8f)
                val x = bx + nx * hk * u + tx * sway; val y = by + ny * hk * u + ty * sway
                pts[count++] = lx; pts[count++] = ly; pts[count++] = x; pts[count++] = y
                lx = x; ly = y
            }
        }
        glow.alpha = 60; glow.strokeWidth = 5f
        c.drawLines(pts, 0, count, glow)
        ink.color = withAlpha(0xFFFF8E9C.toInt(), 220); ink.strokeWidth = .9f
        c.drawLines(pts, 0, count, ink)
    }

    private companion object {
        const val CX = 180f
        const val CY = 186f
        const val RAD = 86f
        const val H_ALPHA = 0xFFE8475C.toInt()
        const val SPOT_BIG = 1
        const val SPOT_MID = 2
        const val SPOT_PAIR = 3
    }
}
