package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tellure — la centrale solaire. Une couche de tellurure de cadmium, plus fine qu'un cheveu,
 * change la lumière en électricité dans les panneaux solaires minces posés par milliers dans les
 * grandes centrales. Dans le désert, les rangées de panneaux filent vers l'horizon ; le soleil
 * traverse le ciel et les rangées pivotent pour le suivre, d'est en ouest. La nuit tombe, elles
 * reviennent à plat sous les étoiles, puis se tournent vers l'est pour l'aube.
 */
internal class SceneTellurium : EngravedScene() {

    override val sky = Sky.DAY
    override val nameRes = R.string.card_scene_solar_farm
    override val noteRes = R.string.card_note_solar_farm
    override val explainRes = R.string.card_explain_solar_farm

    override fun engrave(b: Burin) {
        // Les montagnes lointaines, le désert de sable.
        val hills = Path().apply {
            moveTo(40f, HORIZON)
            for (i in RIDGE.indices step 2) lineTo(RIDGE[i], RIDGE[i + 1])
            lineTo(320f, HORIZON); close()
        }
        b.body(hills, 0xFF9A7A8A.toInt(), .3f, 0f, outline = .7f, washAlpha = 200)
        val sand = rect(40f, HORIZON, 320f, 340f)
        b.wash(sand, 0xFFE0C08A.toInt(), 255)
        b.hatchRect(RectF(40f, HORIZON, 320f, 340f), 0f, 2.6f, .4f, 0xFF8A6A3A.toInt(), Fade(0f, HORIZON, 0f, 330f, 40, 170))
        b.stipple(sand, 260, .6f, withAlpha(0xFF8A6A3A.toInt(), 160), 52)
        // Le petit poste électrique au bout du champ, la ligne qui s'en va.
        b.body(rect(266f, 142f, 286f, 152f), 0xFFD8D4CC.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.line(296f, 152f, 296f, 126f, .9f); b.line(312f, 152f, 312f, 128f, .9f)
        b.line(292f, 130f, 300f, 130f, .7f); b.line(308f, 132f, 316f, 132f, .7f)
        b.stroke(Path().apply { moveTo(296f, 130f); quadTo(304f, 134f, 312f, 132f); quadTo(318f, 134f, 324f, 131f) }, .5f)
        // Une touffe d'herbe sèche et des cailloux au premier plan.
        for (k in 0 until 9) b.line(70f + k * 2f, 318f, 62f + k * 4f, 296f + (k % 3) * 4f, .8f, 0xFF8A7A3A.toInt())
        b.body(ellipse(270f, 314f, 9f, 4f), 0xFFB89A7A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(ellipse(284f, 318f, 5f, 2.6f), 0xFFA88A6A.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val panel = Path()
    /** Le ciel au-dessus des montagnes : le soleil levant ou couchant passe derrière elles. */
    private val skyClip = Path().apply {
        moveTo(40f, 50f); lineTo(320f, 50f)
        for (i in RIDGE.size - 2 downTo 0 step 2) lineTo(RIDGE[i], RIDGE[i + 1])
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        // Le soleil : il monte à l'est (à gauche), passe, se couche à l'ouest ; la nuit, il est sous l'horizon.
        val sun = if (ph < DAY) ph / DAY else 1f + (ph - DAY) / (CYCLE - DAY)
        val elevation = if (ph < DAY) sin(PI.toFloat() * sun) else -.6f * sin(PI.toFloat() * (sun - 1f))
        val dark = smooth((.12f - elevation) / .32f)
        val warm = (1f - abs(elevation) / .3f).coerceAtLeast(0f)
        if (ph < DAY) {
            val a = PI.toFloat() * sun
            val x = 180f - 150f * cos(a); val y = HORIZON + 6f - 108f * sin(a)
            c.save(); c.clipPath(skyClip)
            fill.color = withAlpha(0xFFFFE8A0.toInt(), 50); c.drawCircle(x, y, 26f, fill)
            fill.color = withAlpha(0xFFFFE8A0.toInt(), 90); c.drawCircle(x, y, 17f, fill)
            fill.color = if (warm > .3f) 0xFFFFB060.toInt() else 0xFFFFF4C8.toInt(); c.drawCircle(x, y, 11f, fill)
            c.restore()
        }
        // Les suiveurs : face au soleil le jour (inclinaison positive = tournés vers l'est, à gauche),
        // ils reviennent à plat la nuit puis se tournent vers l'aube.
        val tilt = if (ph < DAY) (.5f - sun) * 2f * MAX_TILT
        else MAX_TILT * (2f * smooth((ph - DAY) / (CYCLE - DAY)) - 1f)
        for (k in ROWS) row(c, k * SPACING, tilt, warm)
        // Le soir et la nuit assombrissent tout ; l'aube et le crépuscule le teintent d'orange.
        if (warm > 0f) {
            fill.color = withAlpha(0xFFFF8A3A.toInt(), (60 * warm).toInt())
            c.drawRect(40f, 50f, 320f, 340f, fill)
        }
        if (dark > 0f) {
            fill.color = withAlpha(0xFF0A1028.toInt(), (190 * dark).toInt())
            c.drawRect(40f, 50f, 320f, 340f, fill)
            // Les étoiles, et le témoin rouge du poste électrique.
            for (k in 0 until STARS) {
                val tw = .6f + .4f * sin(t * (1.5f + hash(k, 3)) + k)
                fill.color = withAlpha(0xFFFFF6DC.toInt(), (230 * dark * tw).toInt())
                c.drawCircle(46f + hash(k, 1) * 268f, 60f + hash(k, 2) * 64f, .5f + hash(k, 4) * .8f, fill)
            }
            fill.color = withAlpha(0xFFFF3A2A.toInt(), (255 * dark * (if (t % 1.6f < .5f) 1f else .25f)).toInt())
            c.drawCircle(304f, 125f, 1.6f, fill)
            fill.color = withAlpha(0xFFFFD890.toInt(), (220 * dark).toInt())
            c.drawRect(270f, 145f, 275f, 149f, fill)
        }
    }

    /** Une rangée de panneaux, à [x] (en mètres) de l'axe de la vue, inclinée de [tilt] radians. */
    private fun row(c: Canvas, x: Float, tilt: Float, warm: Float) {
        val dx = HALF * cos(tilt); val dy = HALF * sin(tilt)
        // Les poteaux, du tube jusqu'au sable.
        ink.color = 0xFF5A5E64.toInt(); ink.strokeWidth = 1f
        var z = NEAR + 1f
        while (z < FAR) {
            val f = FOCAL / z
            c.drawLine(VX + x * f, HORIZON + EYE * f, VX + x * f, HORIZON + (EYE + POST) * f, ink)
            z += 3.2f
        }
        // Le plan des panneaux : quatre coins, devant et au fond.
        val fn = FOCAL / NEAR; val ff = FOCAL / FAR
        panel.reset()
        panel.moveTo(VX + (x - dx) * fn, HORIZON + (EYE + dy) * fn)
        panel.lineTo(VX + (x + dx) * fn, HORIZON + (EYE - dy) * fn)
        panel.lineTo(VX + (x + dx) * ff, HORIZON + (EYE - dy) * ff)
        panel.lineTo(VX + (x - dx) * ff, HORIZON + (EYE + dy) * ff)
        panel.close()
        // Plus la rangée nous montre sa face, plus elle reflète le ciel.
        val face = (.55f + .45f * cos(tilt - x * .12f)).coerceIn(0f, 1f)
        fill.color = mix(0xFF141C26.toInt(), 0xFF4A6A8A.toInt(), face * .6f); c.drawPath(panel, fill)
        if (warm > 0f) { fill.color = withAlpha(0xFFFFA860.toInt(), (90 * warm * face).toInt()); c.drawPath(panel, fill) }
        // Les joints entre modules, et la ligne médiane du panneau.
        ink.color = withAlpha(0xFFB8C8D8.toInt(), 120); ink.strokeWidth = .6f
        z = NEAR + MODULE
        while (z < FAR) {
            val f = FOCAL / z
            c.drawLine(VX + (x - dx) * f, HORIZON + (EYE + dy) * f, VX + (x + dx) * f, HORIZON + (EYE - dy) * f, ink)
            z += MODULE
        }
        c.drawLine(VX + x * fn, HORIZON + EYE * fn, VX + x * ff, HORIZON + EYE * ff, ink)
        ink.color = Ink.SEPIA; ink.strokeWidth = .8f; c.drawPath(panel, ink)
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * f).toInt()
        val g = ((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * f).toInt()
        val bl = (a and 255) + (((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val HORIZON = 152f
        /** La perspective : point de fuite, focale, hauteur de l'œil au-dessus des tubes, hauteur des poteaux (mètres). */
        const val VX = 180f
        const val FOCAL = 62f
        const val EYE = 2.3f
        const val POST = 1.2f
        const val NEAR = 1.5f
        const val FAR = 18f
        const val MODULE = 1.1f
        /** Les rangées : écart, demi-largeur d'un panneau, inclinaison maximale (radians). */
        const val SPACING = 1.7f
        const val HALF = .72f
        const val MAX_TILT = .9f
        /** Une journée : le jour dure [DAY] secondes sur [CYCLE]. */
        const val CYCLE = 24f
        const val DAY = 16f
        const val STARS = 30
        /** Les plus lointaines d'abord, celle du milieu en dernier. */
        val ROWS = intArrayOf(-3, 3, -2, 2, -1, 1, 0)
        /** La crête des montagnes, de gauche à droite (x, y). */
        val RIDGE = floatArrayOf(40f, 140f, 72f, 128f, 104f, 138f, 140f, 122f, 176f, 136f, 214f, 126f, 252f, 138f, 290f, 124f, 320f, 134f)
    }
}
