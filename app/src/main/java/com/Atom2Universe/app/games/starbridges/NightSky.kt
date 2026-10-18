package com.Atom2Universe.app.games.starbridges

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le ciel de Constellations, partagé par le plateau, l'atlas et la tuile du hub : un fond
 * nocturne traversé par la Voie lactée, et les étoiles avec leur halo.
 */
object NightSky {

    /** Or des traits : l'encre d'une carte du ciel ancienne. */
    const val LINE = 0xFFFFD27A.toInt()
    const val LINE_REJECT = 0xFFFF6B6B.toInt()

    /**
     * Couleur d'une étoile selon son nombre de liens : les petites sont rouges et
     * orangées, les grandes blanches puis bleues — comme les vraies, où la couleur suit la
     * température et l'éclat. Une grille se lit ainsi d'un coup d'œil.
     */
    private val STAR = intArrayOf(
        0xFFFF8A5C.toInt(), 0xFFFFB36B.toInt(), 0xFFFFD98A.toInt(), 0xFFFFF1C8.toInt(),
        0xFFF4F6FF.toInt(), 0xFFD8E6FF.toInt(), 0xFFB4CCFF.toInt(), 0xFF9DB8FF.toInt()
    )

    fun starColor(links: Int): Int = STAR[(links - 1).coerceIn(0, STAR.size - 1)]

    private val glowShaders = arrayOfNulls<RadialGradient>(STAR.size)

    /** Halo d'une étoile, en rayon 1 : on l'agrandit par la toile plutôt que de le recréer. */
    fun glowShader(links: Int): RadialGradient {
        val i = (links - 1).coerceIn(0, STAR.size - 1)
        return glowShaders[i] ?: RadialGradient(0f, 0f, 1f,
            intArrayOf((STAR[i] and 0xFFFFFF) or (0x90 shl 24), (STAR[i] and 0xFFFFFF) or (0x30 shl 24), STAR[i] and 0xFFFFFF),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP).also { glowShaders[i] = it }
    }

    /**
     * Le fond, cuit une fois à la taille de la vue : dégradé de nuit, Voie lactée en
     * diagonale (une traînée de lueur semée de milliers de points), deux nébuleuses, et les
     * étoiles lointaines.
     */
    fun bake(w: Int, h: Int, seed: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        paint(Canvas(bmp), w.toFloat(), h.toFloat(), seed, density)
        return bmp
    }

    fun paint(c: Canvas, w: Float, h: Float, seed: Int, density: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF060A1C.toInt(), 0xFF0B0826.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val rnd = Random(seed)

        // La Voie lactée : une bande inclinée, plus dense au milieu.
        val angle = -0.55f + rnd.nextFloat() * 0.3f
        val ux = cos(angle); val uy = sin(angle)
        val cx = w * (0.4f + rnd.nextFloat() * 0.2f); val cy = h * (0.4f + rnd.nextFloat() * 0.2f)
        val len = w + h
        val band = maxOf(w, h) * 0.16f
        c.save()
        c.translate(cx, cy); c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        p.shader = LinearGradient(0f, -band * 1.6f, 0f, band * 1.6f,
            intArrayOf(0x00000000, 0x1C8C7CC8, 0x2AB0A0E0, 0x1C8C7CC8, 0x00000000),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-len, -band * 1.6f, len, band * 1.6f, p)
        c.restore()
        p.shader = null
        repeat(((w * h) / (1400f * density * density)).toInt().coerceIn(200, 4000)) {
            val t = (rnd.nextFloat() * 2f - 1f) * len / 2f
            val off = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * band * 1.3f
            val x = cx + ux * t - uy * off; val y = cy + uy * t + ux * off
            p.color = Color.argb(30 + rnd.nextInt(90), 210, 205, 255)
            c.drawCircle(x, y, (0.35f + rnd.nextFloat() * 0.5f) * density, p)
        }

        fun nebula(x: Float, y: Float, r: Float, color: Int) {
            p.shader = RadialGradient(x, y, r, color, color and 0xFFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        nebula(w * rnd.nextFloat(), h * rnd.nextFloat(), maxOf(w, h) * 0.45f, 0x223A1C6E)
        nebula(w * rnd.nextFloat(), h * rnd.nextFloat(), maxOf(w, h) * 0.35f, 0x1C145A6E)
        p.shader = null

        repeat(((w * h) / (5000f * density * density)).toInt().coerceIn(80, 900)) {
            val bright = rnd.nextInt(14) == 0
            p.color = Color.argb(50 + rnd.nextInt(140), 215 + rnd.nextInt(40), 220 + rnd.nextInt(35), 255)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h,
                (if (bright) 1.2f else 0.45f + rnd.nextFloat() * 0.5f) * density, p)
        }
    }
}
