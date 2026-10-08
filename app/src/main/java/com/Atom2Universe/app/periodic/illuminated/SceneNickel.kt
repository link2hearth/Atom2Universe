package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nickel — la pièce de monnaie. Dur, brillant, inusable : la partie argentée des pièces de 1 et
 * 2 euros en contient. Une pièce bicolore tourne sur sa tranche au milieu de la table, entre la
 * tirelire et une pile de pièces ; ses deux faces passent tour à tour, et la tranche cannelée
 * apparaît quand elle se présente de profil.
 */
internal class SceneNickel : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_spinning_coin
    override val noteRes = R.string.card_note_spinning_coin
    override val explainRes = R.string.card_explain_spinning_coin

    override fun engrave(b: Burin) {
        // Le mur tapissé de rayures, la lampe en haut à droite.
        val wall = rect(40f, 50f, 320f, TABLE)
        b.wash(wall, 0xFFE6D8BC.toInt(), 255)
        var x = 40f
        while (x < 320f) {
            b.fill(rect(x, 50f, x + 8f, TABLE), withAlpha(0xFFC8B48C.toInt(), 110))
            x += 18f
        }
        b.glow(296f, 70f, 210f, 0xFFFFF4D8.toInt(), 170)
        b.washGradient(wall, 0xFF4A3A28.toInt(), 0f, 120f, 0, 0f, TABLE, 130)
        b.body(rect(40f, TABLE - 10f, 320f, TABLE), 0xFF7A5232.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // La table de bois, vue de haut.
        val table = rect(40f, TABLE, 320f, 340f)
        b.wash(table, 0xFFA8763E.toInt(), 255)
        b.hatchRect(RectF(40f, TABLE, 320f, 340f), 0f, 2.4f, .45f, Ink.SEPIA, Fade(0f, TABLE, 0f, 330f, 70, 210))
        for (k in 0 until 5) {
            val y = TABLE + 14f + k * 18f
            b.stroke(Path().apply { moveTo(40f, y); cubicTo(120f, y - 4f, 200f, y + 5f, 320f, y - 2f) }, .7f, withAlpha(Ink.BROWN, 150))
        }
        b.line(40f, TABLE, 320f, TABLE, 1.2f, withAlpha(0xFFFFE8C8.toInt(), 200))
        piggy(b)
        stack(b, 290f, 266f, 8)
        flatCoin(b, 112f, 282f)
        flatCoin(b, 132f, 304f)
        flatCoin(b, 252f, 308f)
    }

    /** La tirelire cochon, de profil, le groin à gauche. */
    private fun piggy(b: Burin) {
        b.c.save(); b.c.translate(8f, 0f)
        val pink = 0xFFE8A8AC.toInt()
        for (lx in floatArrayOf(66f, 102f)) {
            b.body(Path().apply { addRoundRect(lx - 5f, 228f, lx + 5f, 246f, 3f, 3f, Path.Direction.CW) }, 0xFFC8868C.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        }
        val body = Path().apply {
            addOval(50f, 192f, 122f, 242f, Path.Direction.CW)
            op(poly(62f, 198f, 66f, 180f, 76f, 194f), Path.Op.UNION)
        }
        b.body(body, pink, .35f, 110f, Fade(84f, 242f, 84f, 200f, 220, 0), outline = 1f, washAlpha = 255)
        for (lx in floatArrayOf(74f, 110f)) {
            b.body(Path().apply { addRoundRect(lx - 5f, 230f, lx + 5f, 248f, 3f, 3f, Path.Direction.CW) }, pink, .25f, 0f, outline = .8f, washAlpha = 255)
        }
        b.glow(78f, 204f, 22f, 0xFFFFFFFF.toInt(), 150)
        // Le groin, l'œil, la fente sur le dos, la queue en vrille.
        b.body(ellipse(48f, 216f, 7f, 9f), 0xFFF0B8BC.toInt(), 0f, outline = 1f, washAlpha = 255)
        b.fill(ellipse(46.5f, 213f, 1.4f, 2f), 0xFF8A4A50.toInt())
        b.fill(ellipse(46.5f, 219f, 1.4f, 2f), 0xFF8A4A50.toInt())
        b.fill(ellipse(64f, 208f, 2.4f, 2.8f), Ink.SEPIA)
        b.c.drawCircle(63.4f, 207f, .8f, b.pen(0xFFFFFFFF.toInt()))
        b.fill(Path().apply { addRoundRect(80f, 192f, 98f, 196f, 2f, 2f, Path.Direction.CW) }, 0xFF3A2024.toInt())
        b.stroke(Path().apply { moveTo(121f, 212f); cubicTo(130f, 206f, 132f, 216f, 126f, 216f); cubicTo(122f, 216f, 124f, 210f, 130f, 210f) }, 1.4f)
        b.c.restore()
    }

    /** Une pile de [n] pièces posées à plat, la plus basse en ([cx], [base]). */
    private fun stack(b: Burin, cx: Float, base: Float, n: Int) {
        val top = base - n * THICK
        val side = Path().apply {
            addOval(cx - FLAT_RX, base - FLAT_RY, cx + FLAT_RX, base + FLAT_RY, Path.Direction.CW)
            op(rect(cx - FLAT_RX, top, cx + FLAT_RX, base), Path.Op.UNION)
        }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(cx - FLAT_RX, 0f, cx + FLAT_RX, 0f,
            intArrayOf(0xFFE8ECF0.toInt(), 0xFFB0B8C0.toInt(), 0xFF6A727A.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(side, b.p)
        b.c.save(); b.c.clipPath(side)
        for (i in 1 until n) {
            val y = base - i * THICK
            b.c.drawArc(cx - FLAT_RX, y - FLAT_RY, cx + FLAT_RX, y + FLAT_RY, 0f, 180f, false, b.pen(withAlpha(Ink.SEPIA, 170), .7f))
        }
        b.c.restore()
        b.stroke(side, .9f)
        flatFace(b, cx, top)
    }

    private fun flatCoin(b: Burin, cx: Float, cy: Float) {
        b.body(ellipse(cx, cy + 1.6f, FLAT_RX, FLAT_RY), 0xFF8A9096.toInt(), 0f, outline = .8f, washAlpha = 255)
        flatFace(b, cx, cy)
    }

    /** Une face vue presque de profil : l'anneau argenté, le cœur doré. */
    private fun flatFace(b: Burin, cx: Float, cy: Float) {
        b.body(ellipse(cx, cy, FLAT_RX, FLAT_RY), 0xFFE0E4E8.toInt(), 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(cx, cy, FLAT_RX * .62f, FLAT_RY * .62f), Gilding.LEAF, 0f, outline = .6f, washAlpha = 255)
        b.c.drawOval(cx - FLAT_RX * .5f, cy - FLAT_RY * .5f, cx, cy, b.pen(withAlpha(Gilding.LIGHT, 200)))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(FACE_A, RectF(-RADIUS - 1f, -RADIUS - 1f, RADIUS + 1f, RADIUS + 1f)) { b ->
            ring(b)
            // Douze étoiles sur l'anneau.
            val star = starPath()
            for (k in 0 until 12) {
                val a = k * PI.toFloat() / 6f
                b.c.save(); b.c.translate(cos(a) * RADIUS * .81f, sin(a) * RADIUS * .81f); b.c.scale(3.4f, 3.4f)
                b.fill(star, 0xFF5A626A.toInt())
                b.c.restore()
            }
            heart(b)
            // L'atome gravé au cœur : trois orbites, un noyau, trois électrons.
            for (k in 0 until 3) {
                b.c.save(); b.c.rotate(k * 60f)
                b.c.drawOval(-RADIUS * .46f, -RADIUS * .15f, RADIUS * .46f, RADIUS * .15f, b.pen(Gilding.DEEP, 1.5f))
                b.c.drawCircle(RADIUS * .46f, 0f, 2.2f, b.pen(Gilding.DEEP))
                b.c.restore()
            }
            b.c.drawCircle(0f, 0f, 4f, b.pen(Gilding.DEEP))
            b.c.drawCircle(-1.2f, -1.2f, 1.4f, b.pen(Gilding.LIGHT))
        },
        Sprite(FACE_B, RectF(-RADIUS - 1f, -RADIUS - 1f, RADIUS + 1f, RADIUS + 1f)) { b ->
            ring(b)
            // Des rayons fins tout autour de l'anneau.
            val ray = b.pen(0xFF6A727A.toInt(), 1.1f)
            for (k in 0 until 32) {
                val a = k * PI.toFloat() / 16f
                b.c.drawLine(cos(a) * RADIUS * .7f, sin(a) * RADIUS * .7f, cos(a) * RADIUS * .9f, sin(a) * RADIUS * .9f, ray)
            }
            heart(b)
            // Le rameau de feuilles.
            b.stroke(Path().apply { moveTo(-RADIUS * .34f, RADIUS * .34f); quadTo(-RADIUS * .02f, -RADIUS * .04f, RADIUS * .32f, -RADIUS * .38f) }, 1.6f, Gilding.DEEP)
            for (k in 0 until 5) {
                val u = .12f + k * .18f
                val px = -RADIUS * .34f + u * RADIUS * .66f
                val py = RADIUS * .34f - u * RADIUS * .72f
                for (side in intArrayOf(-1, 1)) {
                    b.c.save(); b.c.translate(px, py); b.c.rotate(-45f + side * 50f)
                    b.fill(ellipse(side * 6f, 0f, 6f, 2.4f), Gilding.DEEP)
                    b.c.restore()
                }
            }
        }
    )

    /** L'anneau argenté d'une face, son listel et son contour. */
    private fun ring(b: Burin) {
        b.pen(0xFF000000.toInt()).shader = LinearGradient(-RADIUS, -RADIUS, RADIUS, RADIUS,
            intArrayOf(0xFFF6F8FA.toInt(), 0xFFA8B0B8.toInt(), 0xFFE4E8EC.toInt(), 0xFF7A828A.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawCircle(0f, 0f, RADIUS, b.p)
        b.c.drawCircle(0f, 0f, RADIUS - 2.2f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 170), 1.2f))
        b.c.drawCircle(0f, 0f, RADIUS - 3.6f, b.pen(withAlpha(0xFF5A626A.toInt(), 160), .6f))
        b.c.drawCircle(0f, 0f, RADIUS, b.pen(Ink.SEPIA, 1f))
    }

    /** Le cœur doré, cerclé. */
    private fun heart(b: Burin) {
        val r = RADIUS * .62f
        b.pen(0xFF000000.toInt()).shader = LinearGradient(-r, -r, r, r,
            intArrayOf(Gilding.LIGHT, Gilding.LEAF, Gilding.DEEP), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        b.c.drawCircle(0f, 0f, r, b.p)
        b.c.drawCircle(0f, 0f, r, b.pen(Ink.SEPIA, .9f))
    }

    private fun starPath() = Path().apply {
        for (k in 0 until 10) {
            val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
            val rr = if (k % 2 == 0) 1f else .42f
            if (k == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
        }
        close()
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -RADIUS, 0f, RADIUS,
            intArrayOf(0xFFE8ECF0.toInt(), 0xFF9AA2AA.toInt(), 0xFF5A626A.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    }
    private val reeds = FloatArray(REEDS * 4)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La pièce dérive doucement sur la table en tournant, et penche un peu d'un côté puis de l'autre.
        val theta = t * SPIN
        val cx = 196f + 20f * sin(t * .5f)
        val base = 262f + 5f * cos(t * .5f)
        val cy = base - RADIUS
        val lean = 5f * sin(t * 1.1f)
        val ct = cos(theta)
        val half = RADIUS * abs(ct)
        // Le centre de la face visible, et celui de la face cachée : la tranche est entre les deux.
        val xv = THICK * .5f * sin(theta) * sign(ct)
        // L'ombre sur la table, poussée vers la droite par la lampe.
        fill.color = withAlpha(0xFF1A120A.toInt(), 70)
        c.drawOval(cx - 10f, base - 4f, cx + 44f + 10f * abs(sin(theta)), base + 4f, fill)
        c.save(); c.translate(cx, cy); c.rotate(lean, 0f, RADIUS)
        // La tranche : la face cachée, la bande entre les deux faces, ses cannelures.
        if (abs(xv) > .2f) {
            c.drawOval(-xv - half, -RADIUS, -xv + half, RADIUS, edge)
            c.drawRect(minOf(xv, -xv), -RADIUS, maxOf(xv, -xv), RADIUS, edge)
            val side = sign(-xv)
            var n = 0
            for (k in 0 until REEDS) {
                val y = RADIUS * sin(-PI.toFloat() / 2f + (k + .5f) * PI.toFloat() / REEDS)
                val w = half * sqrt((1f - (y / RADIUS) * (y / RADIUS)).coerceAtLeast(0f))
                reeds[n * 4] = xv + side * w; reeds[n * 4 + 1] = y
                reeds[n * 4 + 2] = -xv + side * w; reeds[n * 4 + 3] = y
                n++
            }
            ink.color = withAlpha(0xFF3A4048.toInt(), 150); ink.strokeWidth = .8f
            c.drawLines(reeds, 0, n * 4, ink)
            ink.color = Ink.SEPIA; ink.strokeWidth = 1f
            c.drawLine(minOf(xv, -xv), -RADIUS, maxOf(xv, -xv), -RADIUS, ink)
            c.drawLine(minOf(xv, -xv), RADIUS, maxOf(xv, -xv), RADIUS, ink)
            c.drawOval(-xv - half, -RADIUS, -xv + half, RADIUS, ink)
        }
        // La face visible, aplatie selon l'angle ; un éclat quand elle renvoie la lampe.
        if (half > .5f) {
            c.save(); c.translate(xv, 0f); c.scale(abs(ct), 1f)
            s.draw(c, if (ct >= 0f) FACE_A else FACE_B)
            c.restore()
            val glint = abs(cos(theta + .6f)).pow(14)
            if (glint > .02f) {
                fill.color = withAlpha(0xFFFFFFFF.toInt(), (190 * glint).toInt())
                c.drawOval(xv - half, -RADIUS, xv + half, RADIUS, fill)
            }
        } else {
            ink.color = Ink.SEPIA; ink.strokeWidth = 1f
            c.drawLine(xv, -RADIUS, xv, RADIUS, ink)
        }
        c.restore()
        // Une étoile d'éclat sur le listel, quand la face est pleine lumière.
        val spark = abs(cos(theta + .6f)).pow(30)
        if (spark > .1f) {
            val sx = cx - RADIUS * .5f * abs(ct); val sy = cy - RADIUS * .6f
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * spark).toInt()); ink.strokeWidth = 1.2f
            val l = 9f * spark
            c.drawLine(sx - l, sy, sx + l, sy, ink)
            c.drawLine(sx, sy - l, sx, sy + l, ink)
        }
    }

    private companion object {
        const val TABLE = 232f
        const val RADIUS = 44f
        const val THICK = 6f
        const val FLAT_RX = 16f
        const val FLAT_RY = 5f
        /** Vitesse de rotation de la pièce, en radians par seconde. */
        const val SPIN = 7f
        const val REEDS = 26
        const val FACE_A = 1
        const val FACE_B = 2
    }
}
