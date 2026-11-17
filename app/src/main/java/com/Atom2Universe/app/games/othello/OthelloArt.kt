package com.Atom2Universe.app.games.othello

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le dessin du Reversi, partagé par le plateau et le score.
 *
 * Le plateau : un cadre de noyer en quatre baguettes assemblées en onglet, dont le fil suit chaque
 * baguette, et un tapis de feutre vert enfoncé dans le cadre, rayé de rainures fines, avec les
 * quatre points repères. La lumière vient du haut à gauche.
 *
 * Les pions : une face bombée avec son reflet, et une tranche où l'on voit les deux couleurs du
 * pion (sa couleur en haut, l'autre dessous), comme un vrai pion d'Othello.
 */
internal object OthelloArt {
    /** Le rayon d'un pion, en fraction de la case. */
    const val DISC = 0.41f

    /** L'épaisseur visible de la tranche d'un pion, en fraction de son rayon. */
    const val THICKNESS = 0.13f

    /** La largeur du cadre de bois pour un plateau de côté [size]. */
    fun frame(size: Float) = size * 0.05f

    fun corner(size: Float) = frame(size) * 0.7f

    // === Le plateau ===

    fun board(size: Int, dp: Float): Bitmap {
        val n = OthelloGame.BOARD_SIZE
        val s = size.toFloat()
        val f = frame(s)
        val cell = (s - 2 * f) / n
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(1957)

        // Le cadre : quatre baguettes en onglet.
        val outer = RectF(0f, 0f, s, s)
        val rounded = Path().apply { addRoundRect(outer, corner(s), corner(s), Path.Direction.CW) }
        c.save()
        c.clipPath(rounded)
        for (side in 0 until 4) {
            c.save()
            c.clipPath(railShape(side, s, f))
            c.concat(railMatrix(side, s))
            rail(c, s, f, rnd, dp)
            c.restore()
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, 0.8f * dp)
        p.color = 0x6E241005
        c.drawLine(0f, 0f, f, f, p)
        c.drawLine(s, 0f, s - f, f, p)
        c.drawLine(0f, s, f, s - f, p)
        c.drawLine(s, s, s - f, s - f, p)
        // La lumière d'ensemble, puis l'arête extérieure.
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, s, s, intArrayOf(0x30FFFFFF, 0x00FFFFFF, 0x3C000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(outer, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * dp
        p.shader = LinearGradient(0f, 0f, s, s, intArrayOf(0x70FFE4C4, 0x14FFFFFF, 0x5A000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val e = p.strokeWidth / 2f
        c.drawRoundRect(RectF(e, e, s - e, s - e), corner(s) - e, corner(s) - e, p)
        p.shader = null
        c.restore()

        // La lèvre du cadre autour du tapis : dans l'ombre en haut et à gauche, éclairée en face.
        val lip = f * 0.24f
        p.style = Paint.Style.FILL
        val a = f - lip
        val b = s - f + lip
        fun band(color: Int, vararg xy: Float) {
            p.color = color
            c.drawPath(Path().apply {
                moveTo(xy[0], xy[1]); lineTo(xy[2], xy[3]); lineTo(xy[4], xy[5]); lineTo(xy[6], xy[7]); close()
            }, p)
        }
        band(0x78000000, a, a, b, a, s - f, f, f, f)
        band(0x58000000, a, a, f, f, f, s - f, a, b)
        band(0x46FFE6C8, a, b, b, b, s - f, s - f, f, s - f)
        band(0x2EFFE6C8, b, a, b, b, s - f, s - f, s - f, f)

        // Le feutre, plus clair sous la lampe, avec son grain.
        val inner = RectF(f, f, s - f, s - f)
        p.shader = RadialGradient(s / 2f, s * 0.42f, inner.width() * 0.78f,
            intArrayOf(0xFF2F8F5A.toInt(), 0xFF217447.toInt(), 0xFF155736.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(inner, p)
        p.shader = BitmapShader(feltGrain(rnd), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(dp * 0.6f, dp * 0.6f) })
        }
        c.drawRect(inner, p)
        // L'ombre du cadre sur le tapis.
        p.shader = LinearGradient(0f, f, 0f, f + cell * 0.32f, 0x6A000000, 0, Shader.TileMode.CLAMP)
        c.drawRect(f, f, s - f, f + cell * 0.32f, p)
        p.shader = LinearGradient(f, 0f, f + cell * 0.24f, 0f, 0x50000000, 0, Shader.TileMode.CLAMP)
        c.drawRect(f, f, f + cell * 0.24f, s - f, p)
        p.shader = null

        // Les rainures entre les cases, avec leur bord éclairé.
        val groove = max(1f, cell * 0.028f)
        p.style = Paint.Style.STROKE
        for (i in 1 until n) {
            val x = f + i * cell
            p.strokeWidth = max(1f, groove * 0.6f)
            p.color = 0x24FFFFFF
            c.drawLine(x + groove, f, x + groove, s - f, p)
            c.drawLine(f, x + groove, s - f, x + groove, p)
            p.strokeWidth = groove
            p.color = 0xBE0B3A20.toInt()
            c.drawLine(x, f, x, s - f, p)
            c.drawLine(f, x, s - f, x, p)
        }
        p.strokeWidth = max(1f, groove * 0.8f)
        p.color = 0x8C000000.toInt()
        c.drawRect(inner, p)

        // Les quatre points repères.
        p.style = Paint.Style.FILL
        for (row in intArrayOf(2, 6)) for (col in intArrayOf(2, 6)) {
            val x = f + col * cell
            val y = f + row * cell
            p.color = 0xFF0A3620.toInt()
            c.drawCircle(x, y, cell * 0.075f, p)
            p.color = 0x2EFFFFFF
            c.drawCircle(x + cell * 0.012f, y + cell * 0.022f, cell * 0.05f, p)
            p.color = 0xFF0A3620.toInt()
            c.drawCircle(x, y, cell * 0.05f, p)
        }
        return bmp
    }

    /** La baguette [side] (0 haut, 1 droite, 2 bas, 3 gauche), coupée en onglet aux coins. */
    private fun railShape(side: Int, s: Float, f: Float) = Path().apply {
        when (side) {
            0 -> { moveTo(0f, 0f); lineTo(s, 0f); lineTo(s - f, f); lineTo(f, f) }
            1 -> { moveTo(s, 0f); lineTo(s, s); lineTo(s - f, s - f); lineTo(s - f, f) }
            2 -> { moveTo(0f, s); lineTo(s, s); lineTo(s - f, s - f); lineTo(f, s - f) }
            else -> { moveTo(0f, 0f); lineTo(f, f); lineTo(f, s - f); lineTo(0f, s) }
        }
        close()
    }

    /** Passe des coordonnées de baguette (x le long, y de l'arête extérieure vers le tapis) à l'écran. */
    private fun railMatrix(side: Int, s: Float) = Matrix().apply {
        when (side) {
            1 -> setValues(floatArrayOf(0f, -1f, s, 1f, 0f, 0f, 0f, 0f, 1f))
            2 -> setValues(floatArrayOf(1f, 0f, 0f, 0f, -1f, s, 0f, 0f, 1f))
            3 -> setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        }
    }

    /** Une baguette de noyer de longueur [length] et de largeur [thick] : bombée, veinée. */
    private fun rail(c: Canvas, length: Float, thick: Float, rnd: Random, dp: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, thick,
            intArrayOf(0xFF74452A.toInt(), 0xFF9A6640.toInt(), 0xFF875535.toInt(), 0xFF5A341C.toInt()),
            floatArrayOf(0f, 0.3f, 0.68f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, length, thick, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val veins = (thick / (1.5f * dp)).toInt().coerceIn(6, 40)
        val path = Path()
        val steps = 56
        repeat(veins) { j ->
            val y0 = (j + rnd.nextFloat()) * thick / veins
            val amp = thick * (0.03f + 0.07f * rnd.nextFloat())
            val wave = length * (0.35f + 0.9f * rnd.nextFloat())
            val phase = rnd.nextFloat() * 6.283f
            val amp2 = amp * 0.45f
            val wave2 = wave / 3.3f
            val phase2 = rnd.nextFloat() * 6.283f
            path.reset()
            for (k in 0..steps) {
                val x = length * k / steps
                val y = y0 + amp * sin(2 * PI.toFloat() * x / wave + phase) +
                    amp2 * sin(2 * PI.toFloat() * x / wave2 + phase2)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.color = if (rnd.nextFloat() < 0.75f) Color.argb(28 + rnd.nextInt(60), 48, 22, 8)
            else Color.argb(18 + rnd.nextInt(34), 214, 164, 112)
            p.strokeWidth = dp * (0.4f + 0.9f * rnd.nextFloat())
            c.drawPath(path, p)
        }
    }

    /** Un petit carré de bruit clair et sombre, répété sur le tapis : le grain du feutre. */
    private fun feltGrain(rnd: Random): Bitmap {
        val side = 64
        val px = IntArray(side * side) {
            val alpha = rnd.nextInt(24)
            if (rnd.nextBoolean()) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha + 4, 0, 0, 0)
        }
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, side, 0, 0, side, side) }
    }

    // === Les pions ===

    /** Un pion de couleur [color] et de rayon [r], centré sur ([cx], [cy]) tranche comprise. */
    fun disc(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, p: Paint) {
        val white = color == OthelloGame.WHITE
        val t = r * THICKNESS
        val top = cy - t / 2f
        p.style = Paint.Style.FILL
        p.shader = null
        // La tranche : la couleur de la face en haut, l'autre couleur dessous.
        p.color = if (white) 0xFF1A1A1A.toInt() else 0xFFE4E0D6.toInt()
        c.drawCircle(cx, top + t, r, p)
        p.color = if (white) 0xFFB9B4A8.toInt() else 0xFF050505.toInt()
        c.drawCircle(cx, top + t * 0.5f, r, p)
        // La face, bombée.
        p.shader = RadialGradient(cx - r * 0.35f, top - r * 0.4f, r * 1.6f,
            if (white) intArrayOf(0xFFFFFFFF.toInt(), 0xFFF3F0E9.toInt(), 0xFFCDC8BC.toInt())
            else intArrayOf(0xFF5E5E5E.toInt(), 0xFF242424.toInt(), 0xFF0A0A0A.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, top, r, p)
        p.shader = null
        // Le petit rebord moulé, puis le contour.
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.05f
        p.color = if (white) 0x18000000 else 0x1EFFFFFF
        c.drawCircle(cx, top, r * 0.8f, p)
        p.strokeWidth = max(1f, r * 0.04f)
        p.color = if (white) 0x50000000 else 0x99000000.toInt()
        c.drawCircle(cx, top, r - p.strokeWidth / 2f, p)
        // Le reflet.
        p.style = Paint.Style.FILL
        val hx = cx - r * 0.32f
        val hy = top - r * 0.42f
        val hr = r * 0.45f
        p.shader = RadialGradient(hx, hy, hr,
            intArrayOf(if (white) 0xB8FFFFFF.toInt() else 0x5CFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.save()
        c.scale(1f, 0.6f, hx, hy)
        c.drawCircle(hx, hy, hr, p)
        c.restore()
        p.shader = null
    }

    /** Le pion [color] de rayon [r] dans une image carrée, centré. */
    fun discSprite(r: Float, color: Int): Bitmap {
        val half = ceil(r * (1f + THICKNESS) + 2f).toInt()
        return Bitmap.createBitmap(2 * half, 2 * half, Bitmap.Config.ARGB_8888).also {
            disc(Canvas(it), half.toFloat(), half.toFloat(), r, color, Paint(Paint.ANTI_ALIAS_FLAG))
        }
    }

    /** L'ombre douce d'un pion de rayon [r], centrée. */
    fun shadowSprite(r: Float): Bitmap {
        val rs = r * 1.18f
        val half = ceil(rs + 2f).toInt()
        return Bitmap.createBitmap(2 * half, 2 * half, Bitmap.Config.ARGB_8888).also {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(half.toFloat(), half.toFloat(), rs,
                intArrayOf(0x8C000000.toInt(), 0x5A000000, 0x00000000), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
            Canvas(it).drawCircle(half.toFloat(), half.toFloat(), rs, p)
        }
    }
}
