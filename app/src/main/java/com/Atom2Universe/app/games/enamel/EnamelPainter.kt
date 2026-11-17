package com.Atom2Universe.app.games.enamel

import android.graphics.Bitmap
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
    /** Émail de chaque rang, à rayon 1 : la toile est mise à l'échelle du palet. */
    private val enamels = arrayOfNulls<Shader>(COLORS.size)

    fun board(c: Canvas, height: Float = EnamelRules.HEIGHT, width: Float = EnamelRules.WIDTH,
              baseDepth: Float = EnamelRules.BASE_DEPTH) {
        val base = height - baseDepth
        val mid = width / 2
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, width, height, intArrayOf(0xFFEDD5AD.toInt(), 0xFFF5E5C8.toInt(), 0xFFE2C398.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-.16f, -.16f, width + .16f, height + .16f, .24f, .24f, p)
        p.shader = null
        val random = Random(41)
        p.style = Paint.Style.STROKE; p.strokeWidth = .014f
        repeat((9 * width).toInt()) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            p.color = Color.argb(20 + random.nextInt(18), 132, 89, 45)
            path.reset(); path.moveTo(x, maxOf(0f, y - 4f))
            path.cubicTo(x + .22f, maxOf(0f, y - 1f), x - .19f, minOf(height, y + 1f), x + .06f, minOf(height, y + 4f))
            c.drawPath(path, p)
        }
        p.color = 0x20754A26; p.strokeWidth = .025f
        for (i in 1 until (width / 2).toInt()) c.drawLine(i * 2f, 0f, i * 2f, height, p)
        p.color = INK; p.strokeWidth = .18f
        c.drawRoundRect(-.09f, -.09f, width + .09f, height + .09f, .24f, .24f, p)
        p.color = GOLD; p.strokeWidth = .035f
        c.drawRoundRect(-.09f, -.09f, width + .09f, height + .09f, .24f, .24f, p)
        // Incrustation discrète, pour laisser les pièces dominer le plateau.
        p.color = 0x287F774F; p.strokeWidth = .022f
        c.drawCircle(mid, 3.25f, 1.65f, p)
        c.drawCircle(mid, 3.25f, 1.8f, p)
        repeat(24) {
            val a = it * PI.toFloat() / 12
            c.drawLine(mid + cos(a) * 1.9f, 3.25f + sin(a) * 1.9f, mid + cos(a) * 2.16f, 3.25f + sin(a) * 2.16f, p)
        }
        p.style = Paint.Style.FILL; p.color = 0x20173E40
        c.drawRect(0f, base, width, height, p)
        p.color = 0x99173E40.toInt(); p.strokeWidth = .045f
        c.drawLine(.05f, base, width - .05f, base, p)
        p.color = 0x307A7353
        for (row in 1..3) repeat((2 * width - 1).toInt()) {
            val y = base + row * .7f * baseDepth / EnamelRules.BASE_DEPTH
            c.drawCircle(.5f + it * .5f, y, .018f, p)
        }
        p.style = Paint.Style.FILL
        for (x in floatArrayOf(-.09f, width + .09f)) for (y in floatArrayOf(.4f, height - .4f)) {
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
        p.shader = enamels[tier] ?: RadialGradient(-.35f, -.5f, 1.8f,
            intArrayOf(lighten(COLORS[tier]), COLORS[tier], darken(COLORS[tier])), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
            .also { enamels[tier] = it }
        c.save(); c.scale(r, r)
        c.drawCircle(0f, 0f, .94f, p); p.shader = null
        c.restore()
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

/**
 * Une bande fixe de l'écran (la frise des palets) gardée en image : une centaine d'appels de dessin
 * remplacés par un seul à chaque image. [paint] n'est rappelé que si la clé ou la place de la bande
 * changent ; il dessine en coordonnées de la vue, l'image est posée au pixel près.
 */
internal class StripCache {
    private var bitmap: Bitmap? = null
    private var key = Long.MIN_VALUE
    private var left = 0
    private var top = 0

    fun draw(canvas: Canvas, newKey: Long, l: Int, t: Int, r: Int, b: Int, paint: (Canvas) -> Unit) {
        val w = r - l; val h = b - t
        if (w <= 0 || h <= 0) return
        val bmp = bitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it; key = Long.MIN_VALUE }
        if (newKey != key || l != left || t != top) {
            bmp.eraseColor(Color.TRANSPARENT)
            val c = Canvas(bmp); c.translate(-l.toFloat(), -t.toFloat()); paint(c)
            key = newKey; left = l; top = t
        }
        canvas.drawBitmap(bmp, l.toFloat(), t.toFloat(), null)
    }

    /** La mise en page a changé : la bande sera redessinée à la prochaine image. */
    fun clear() { key = Long.MIN_VALUE }
}
