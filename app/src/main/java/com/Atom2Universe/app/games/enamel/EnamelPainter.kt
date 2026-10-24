package com.Atom2Universe.app.games.enamel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Bois blond, émaux et filets de laiton ; mêmes pièces dans le jeu et sur sa carte. */
class EnamelPainter {
    companion object {
        val INK = 0xFF173E40.toInt()
        val CREAM = 0xFFF5E9D2.toInt()
        val GOLD = 0xFFCCA86A.toInt()
        // Teintes franches et luminosités variées : chaque rang doit se repérer sans chiffre.
        val COLORS = intArrayOf(
            0xFFF4E8CE.toInt(), // Ivoire
            0xFFF19A38.toInt(), // Orange
            0xFFD94F46.toInt(), // Rouge corail
            0xFF2FAD72.toInt(), // Vert émeraude
            0xFF3564CE.toInt(), // Bleu cobalt
            0xFFBE4FA1.toInt(), // Magenta
            0xFFB4CF3A.toInt(), // Vert citron
            0xFF873D28.toInt(), // Terre cuite sombre
            0xFF26BCD0.toInt(), // Cyan
            0xFF7543AA.toInt(), // Violet
            0xFFF2D447.toInt(), // Jaune d'or
            0xFF243854.toInt()  // Bleu nuit
        )
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun board(c: Canvas, height: Float = EnamelRules.HEIGHT) {
        val base = height - EnamelRules.BASE_DEPTH
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 10f, height, intArrayOf(0xFFEDD5AD.toInt(), 0xFFF5E5C8.toInt(), 0xFFE2C398.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-.16f, -.16f, 10.16f, height + .16f, .24f, .24f, p)
        p.shader = null
        val random = Random(41)
        p.style = Paint.Style.STROKE; p.strokeWidth = .014f
        repeat(90) {
            val x = random.nextFloat() * 10
            val y = random.nextFloat() * height
            p.color = Color.argb(20 + random.nextInt(18), 132, 89, 45)
            path.reset(); path.moveTo(x, maxOf(0f, y - 4f))
            path.cubicTo(x + .22f, maxOf(0f, y - 1f), x - .19f, minOf(height, y + 1f), x + .06f, minOf(height, y + 4f))
            c.drawPath(path, p)
        }
        p.color = 0x20754A26; p.strokeWidth = .025f
        for (i in 1..4) c.drawLine(i * 2f, 0f, i * 2f, height, p)
        p.color = INK; p.strokeWidth = .18f
        c.drawRoundRect(-.09f, -.09f, 10.09f, height + .09f, .24f, .24f, p)
        p.color = GOLD; p.strokeWidth = .035f
        c.drawRoundRect(-.09f, -.09f, 10.09f, height + .09f, .24f, .24f, p)
        // Incrustation discrète, pour laisser les pièces dominer le plateau.
        p.color = 0x287F774F; p.strokeWidth = .022f
        c.drawCircle(5f, 3.25f, 1.65f, p)
        c.drawCircle(5f, 3.25f, 1.8f, p)
        repeat(24) {
            val a = it * PI.toFloat() / 12
            c.drawLine(5 + cos(a) * 1.9f, 3.25f + sin(a) * 1.9f, 5 + cos(a) * 2.16f, 3.25f + sin(a) * 2.16f, p)
        }
        p.style = Paint.Style.FILL; p.color = 0x20173E40
        c.drawRect(0f, base, 10f, height, p)
        p.color = 0x99173E40.toInt(); p.strokeWidth = .045f
        c.drawLine(.05f, base, 9.95f, base, p)
        p.color = 0x307A7353
        for (row in 1..3) repeat(19) {
            c.drawCircle(.5f + it * .5f, base + row * .7f, .018f, p)
        }
        p.style = Paint.Style.FILL
        for (x in floatArrayOf(-.09f, 10.09f)) for (y in floatArrayOf(.4f, height - .4f)) {
            p.color = GOLD; c.drawCircle(x, y, .075f, p)
            p.color = INK; p.strokeWidth = .018f; c.drawLine(x - .04f, y, x + .04f, y, p)
        }
    }

    fun puck(c: Canvas, tier: Int, x: Float, y: Float, r: Float, angle: Float = 0f, alpha: Int = 255) {
        c.save(); c.translate(x, y); c.rotate(angle * 180f / PI.toFloat())
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0x35152B29; p.alpha = alpha * 50 / 255
        c.drawCircle(.045f * r, .14f * r, r * 1.04f, p)
        p.color = INK; p.alpha = alpha; c.drawCircle(0f, .045f * r, r, p)
        p.shader = RadialGradient(-r * .35f, -r * .5f, r * 1.8f,
            intArrayOf(lighten(COLORS[tier]), COLORS[tier], darken(COLORS[tier])), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, r * .94f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = r * .035f; p.color = GOLD; p.alpha = alpha
        c.drawCircle(0f, 0f, r * .83f, p)
        // Émail lisse, sans lettre ni chiffre : couleur, taille et filets font l'identité.
        p.color = CREAM; p.alpha = alpha * 65 / 255; p.strokeWidth = r * .018f
        c.drawCircle(0f, 0f, r * .77f, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.alpha = alpha * 110 / 255; p.strokeWidth = r * .075f
        c.drawArc(-r * .7f, -r * .7f, r * .7f, r * .7f, 207f, 70f, false, p)
        p.alpha = 255; p.style = Paint.Style.FILL
        c.restore()
    }

    private fun lighten(c: Int) = Color.rgb((Color.red(c) * .75f + 64).toInt().coerceAtMost(255),
        (Color.green(c) * .75f + 64).toInt().coerceAtMost(255), (Color.blue(c) * .75f + 64).toInt().coerceAtMost(255))
    private fun darken(c: Int) = Color.rgb((Color.red(c) * .72f).toInt(), (Color.green(c) * .72f).toInt(), (Color.blue(c) * .72f).toInt())
}
