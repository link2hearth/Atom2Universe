package com.Atom2Universe.app.games.atomblocks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Illustration d'une carte du menu, sans texte : un petit morceau de grille propre à chaque mode.
 * Classique : une ligne pleine qui brille. Aventure : des gemmes dans la grille.
 * Détente : quelques blocs sous un croissant de lune. Puzzle du jour : une page de calendrier.
 */
class AtomBlocksModeArt(private val mode: AtomBlocksEngine.Mode) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val colors = intArrayOf(0xFF26354B.toInt(), 0xFF50D8CB.toInt(), 0xFF6C9EFF.toInt(),
        0xFFB395FF.toInt(), 0xFFFFC66A.toInt(), 0xFFFF808E.toInt())

    private fun gradient(): IntArray = when (mode) {
        AtomBlocksEngine.Mode.CLASSIC -> intArrayOf(0xFF173A5C.toInt(), 0xFF131B33.toInt())
        AtomBlocksEngine.Mode.ADVENTURE -> intArrayOf(0xFF3A2463.toInt(), 0xFF171431.toInt())
        AtomBlocksEngine.Mode.ZEN -> intArrayOf(0xFF154842.toInt(), 0xFF101F2B.toInt())
        AtomBlocksEngine.Mode.DAILY -> intArrayOf(0xFF5A3418.toInt(), 0xFF1E1626.toInt())
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat(); val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.save(); canvas.translate(b.left.toFloat(), b.top.toFloat())
        p.shader = LinearGradient(0f, 0f, w, h, gradient(), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * .5f, h * .38f, w * .6f, 0x3341D6D4, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        when (mode) {
            AtomBlocksEngine.Mode.CLASSIC -> grid(canvas, w, h, arrayOf(
                intArrayOf(0,0,3,3,0), intArrayOf(2,0,3,0,0), intArrayOf(1,1,1,1,1),
                intArrayOf(2,2,0,4,0), intArrayOf(5,2,0,4,4)), glowRow = 2)
            AtomBlocksEngine.Mode.ADVENTURE -> grid(canvas, w, h, arrayOf(
                intArrayOf(0,3,3,0,0), intArrayOf(0,9,3,0,2), intArrayOf(1,1,0,9,2),
                intArrayOf(0,1,0,4,4), intArrayOf(9,0,0,0,4)))
            AtomBlocksEngine.Mode.ZEN -> {
                grid(canvas, w, h, arrayOf(
                    intArrayOf(0,0,0,0,0), intArrayOf(0,0,0,0,0), intArrayOf(0,0,0,1,0),
                    intArrayOf(0,2,0,1,1), intArrayOf(2,2,0,0,1)))
                moon(canvas, w * .32f, h * .27f, min(w, h) * .13f)
            }
            AtomBlocksEngine.Mode.DAILY -> calendar(canvas, w, h)
        }
        // Le bas s'assombrit pour que le titre se lise.
        p.shader = LinearGradient(0f, h * .55f, 0f, h, Color.TRANSPARENT, 0xE0101629.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .55f, w, h, p)
        p.shader = null
        canvas.restore()
    }

    private fun grid(c: Canvas, w: Float, h: Float, cells: Array<IntArray>, glowRow: Int = -1) {
        val size = min(w * .13f, h * .13f)
        val left = (w - 5 * size) / 2; val top = h * .12f
        if (glowRow >= 0) {
            p.color = 0x55FFFFFF
            c.drawRoundRect(left - size * .12f, top + glowRow * size - size * .12f,
                left + 5 * size + size * .12f, top + (glowRow + 1) * size + size * .12f, size * .25f, size * .25f, p)
        }
        for (r in cells.indices) for (col in cells[r].indices) {
            val v = cells[r][col]
            val x = left + col * size; val y = top + r * size
            p.color = colors[if (v == 9) 0 else v]
            c.drawRoundRect(x + 2, y + 2, x + size - 2, y + size - 2, size * .15f, size * .15f, p)
            if (v in 1..5) {
                p.color = 0x45FFFFFF
                c.drawRoundRect(x + size * .16f, y + size * .12f, x + size * .84f, y + size * .22f, 3f, 3f, p)
            }
            if (v == 9) gem(c, x + size / 2, y + size / 2, size * .36f)
        }
    }

    private fun gem(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.rewind()
        path.moveTo(cx, cy - r); path.lineTo(cx + r * .8f, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r * .8f, cy); path.close()
        p.color = 0xFF7CF3FF.toInt(); c.drawPath(path, p)
        path.rewind()
        path.moveTo(cx, cy - r); path.lineTo(cx + r * .8f, cy); path.lineTo(cx, cy); path.close()
        p.color = 0x90FFFFFF.toInt(); c.drawPath(path, p)
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        // Croissant découpé dans le tracé : rien ne déborde sur la grille en dessous.
        path.rewind(); path.addCircle(cx, cy, r, Path.Direction.CW)
        val bite = Path().apply { addCircle(cx + r * .45f, cy - r * .25f, r * .85f, Path.Direction.CW) }
        path.op(bite, Path.Op.DIFFERENCE)
        p.color = 0xFFFFE6A6.toInt(); c.drawPath(path, p)
        p.color = 0xB0FFFFFF.toInt()
        for (i in 0..4) c.drawCircle(cx + r * (1.6f + i * .9f), cy + r * (if (i % 2 == 0) -.6f else .5f), r * .07f, p)
    }

    private fun calendar(c: Canvas, w: Float, h: Float) {
        val pw = min(w * .56f, h * .62f); val ph = pw * .9f
        val left = (w - pw) / 2; val top = h * .1f
        val r = pw * .08f
        p.color = 0xFFEDEFF7.toInt(); c.drawRoundRect(left, top, left + pw, top + ph, r, r, p)
        p.color = 0xFFFF808E.toInt()
        c.drawRoundRect(left, top, left + pw, top + ph * .24f, r, r, p)
        c.drawRect(left, top + ph * .12f, left + pw, top + ph * .24f, p)
        p.color = 0xFF26354B.toInt()
        c.drawCircle(left + pw * .28f, top + ph * .05f, pw * .035f, p)
        c.drawCircle(left + pw * .72f, top + ph * .05f, pw * .035f, p)
        val cell = pw / 5f
        for (i in 0 until 12) {
            val col = i % 4; val row = i / 4
            val x = left + cell * (.5f + col); val y = top + ph * .3f + row * cell * .82f
            p.color = if (i == 6) colors[4] else 0xFFC9CFE0.toInt()
            c.drawRoundRect(x + 3, y + 3, x + cell - 3, y + cell * .82f - 3, cell * .15f, cell * .15f, p)
        }
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
