package com.Atom2Universe.app.games.enamel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Bois blond, émaux et filets de laiton ; mêmes pièces dans le jeu et sur sa carte. */
class EnamelPainter(private val labels: Array<String>) {
    companion object {
        val INK = 0xFF173E40.toInt()
        val CREAM = 0xFFF5E9D2.toInt()
        val GOLD = 0xFFCCA86A.toInt()
        val COLORS = intArrayOf(0xFFE8DCC3.toInt(), 0xFFE4AF65.toInt(), 0xFFDC795F.toInt(),
            0xFF76ADA4.toInt(), 0xFF568C9E.toInt(), 0xFFB17888.toInt(), 0xFF98A769.toInt(),
            0xFFC26443.toInt(), 0xFF527977.toInt(), 0xFF685E86.toInt(), 0xFFBA8D3E.toInt(), 0xFF263E48.toInt())
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun board(c: Canvas) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 10f, 16f, intArrayOf(0xFFEDD5AD.toInt(), 0xFFF5E5C8.toInt(), 0xFFE2C398.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-.3f, -.3f, 10.3f, 16.3f, .28f, .28f, p)
        p.shader = null
        val random = Random(41)
        p.style = Paint.Style.STROKE; p.strokeWidth = .014f
        repeat(90) {
            val x = random.nextFloat() * 10
            val y = random.nextFloat() * 16
            p.color = Color.argb(20 + random.nextInt(18), 132, 89, 45)
            path.reset(); path.moveTo(x, y - 4f)
            path.cubicTo(x + .22f, y - 1f, x - .19f, y + 1f, x + .06f, y + 4f)
            c.drawPath(path, p)
        }
        p.color = 0x20754A26; p.strokeWidth = .025f
        for (i in 1..4) c.drawLine(i * 2f, 0f, i * 2f, 16f, p)
        p.color = INK; p.strokeWidth = .32f
        c.drawRoundRect(-.2f, -.2f, 10.2f, 16.2f, .32f, .32f, p)
        p.color = GOLD; p.strokeWidth = .035f
        c.drawRoundRect(-.2f, -.2f, 10.2f, 16.2f, .32f, .32f, p)
        // Marqueterie Art déco : un soleil à l'extrémité et un éventail au départ.
        p.color = 0x607F774F; p.strokeWidth = .022f
        c.drawCircle(5f, 3.25f, 1.65f, p)
        c.drawCircle(5f, 3.25f, 1.8f, p)
        repeat(24) {
            val a = it * PI.toFloat() / 12
            c.drawLine(5 + cos(a) * 1.9f, 3.25f + sin(a) * 1.9f, 5 + cos(a) * 2.16f, 3.25f + sin(a) * 2.16f, p)
        }
        p.style = Paint.Style.FILL; p.color = 0x14173E40
        c.drawRect(0f, 13.05f, 10f, 16f, p)
        p.color = 0x807A7353.toInt(); p.strokeWidth = .028f
        c.drawLine(.2f, 13.05f, 9.8f, 13.05f, p)
        repeat(19) {
            val x = .5f + it * .5f
            c.drawLine(x, 13.05f, x, 13.05f + if (it % 2 == 0) .17f else .09f, p)
        }
        p.style = Paint.Style.STROKE; p.color = 0x407A7353; p.strokeWidth = .025f
        c.drawArc(3.5f, 13.2f, 6.5f, 16.2f, 205f, 130f, false, p)
        c.drawArc(3.1f, 12.8f, 6.9f, 16.6f, 215f, 110f, false, p)
        p.style = Paint.Style.FILL
        for (x in floatArrayOf(-.2f, 10.2f)) for (y in floatArrayOf(.4f, 15.6f)) {
            p.color = GOLD; c.drawCircle(x, y, .075f, p)
            p.color = INK; p.strokeWidth = .018f; c.drawLine(x - .04f, y, x + .04f, y, p)
        }
    }

    fun puck(c: Canvas, tier: Int, x: Float, y: Float, r: Float, angle: Float = 0f, alpha: Int = 255) {
        c.save(); c.translate(x, y); c.rotate(angle * 180f / PI.toFloat())
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0x35152B29; p.alpha = alpha * 50 / 255
        c.drawCircle(.045f * r, .14f * r, r * 1.04f, p)
        p.alpha = alpha; p.color = INK; c.drawCircle(0f, .045f * r, r, p)
        p.shader = RadialGradient(-r * .35f, -r * .5f, r * 1.8f,
            intArrayOf(lighten(COLORS[tier]), COLORS[tier], darken(COLORS[tier])), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, r * .94f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = r * .035f; p.color = GOLD
        c.drawCircle(0f, 0f, r * .83f, p)
        p.color = if (tier < 5) INK else CREAM
        p.alpha = alpha * 150 / 255; p.strokeWidth = r * .025f
        val petals = tier + 3
        repeat(petals) {
            val a = it * 2 * PI.toFloat() / petals
            c.drawLine(cos(a) * r * .61f, sin(a) * r * .61f, cos(a) * r * .73f, sin(a) * r * .73f, p)
        }
        p.style = Paint.Style.FILL; p.alpha = alpha
        p.typeface = Typeface.create("serif", Typeface.BOLD); p.textAlign = Paint.Align.CENTER
        p.textSize = r * .72f
        c.drawText(labels[tier], 0f, -(p.ascent() + p.descent()) / 2, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.alpha = alpha * 110 / 255; p.strokeWidth = r * .075f
        c.drawArc(-r * .7f, -r * .7f, r * .7f, r * .7f, 207f, 70f, false, p)
        p.alpha = 255; p.style = Paint.Style.FILL
        c.restore()
    }

    private fun lighten(c: Int) = Color.rgb((Color.red(c) * .75f + 64).toInt().coerceAtMost(255),
        (Color.green(c) * .75f + 64).toInt().coerceAtMost(255), (Color.blue(c) * .75f + 64).toInt().coerceAtMost(255))
    private fun darken(c: Int) = Color.rgb((Color.red(c) * .72f).toInt(), (Color.green(c) * .72f).toInt(), (Color.blue(c) * .72f).toInt())
}
