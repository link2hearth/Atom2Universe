package com.Atom2Universe.app.games.hotpotato

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Pose fixe reprenant les formes et couleurs de HotPotatoView : patate, bacon, salade. */
class HotPotatoHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h, 0xFF6E3E1E.toInt(), 0xFF2A1A10.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val size = min(w, h)
        val radius = size * .24f
        canvas.save()
        canvas.translate(w * .5f, h * .30f)
        canvas.rotate(-9f)
        canvas.scale(radius / 30f, radius / 30f)
        paint.color = 0x45000000
        canvas.drawOval(-31f, -22f, 31f, 32f, paint)
        paint.color = 0xFFC8893C.toInt()
        canvas.drawOval(-30.6f, -25.8f, 30.6f, 28.2f, paint)
        paint.color = 0x33FFFFFF
        canvas.drawOval(-18f, -18.6f, -1.5f, -3.6f, paint)
        paint.color = 0x558B5425
        canvas.drawCircle(13.5f, 6f, 2.6f, paint)
        canvas.drawCircle(-6f, 15f, 2.2f, paint)
        // Garnitures identiques à celles du jeu.
        paint.color = 0xFFB5462E.toInt()
        canvas.drawRoundRect(-25.5f, -3f, 25.5f, 5.4f, 5f, 5f, paint)
        paint.color = 0xFFE8A38C.toInt()
        canvas.drawRect(-25.5f, -.6f, 25.5f, 1.2f, paint)
        paint.color = 0xFF6FBF3B.toInt()
        for (i in -1..1) canvas.drawCircle(i * 12f, -16.5f, 7.8f, paint)
        for (side in intArrayOf(-1, 1)) {
            paint.color = 0x44FF6E6E
            canvas.drawCircle(side * 14.4f, 4.8f, 4.8f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(side * 10.8f, -3.6f, 6f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(side * 10.8f, -3.6f, 3f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(side * 10.8f + 1.5f, -5.1f, .9f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = 2.4f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(-9.6f, 3.6f, 9.6f, 18f, 10f, 160f, false, paint)
        canvas.restore()
        // Vapeur douce autour de la patate, figée comme le reste de l'illustration.
        paint.color = 0x65FFE6C0
        paint.strokeWidth = size * .012f
        for (side in intArrayOf(-1, 1)) {
            val x = w * .5f + side * radius * 1.5f
            val steam = Path().apply {
                moveTo(x, h * .43f)
                cubicTo(x - side * size * .09f, h * .32f, x + side * size * .09f, h * .24f, x, h * .13f)
            }
            canvas.drawPath(steam, paint)
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, h * .57f, 0f, h, Color.TRANSPARENT, 0xD020130C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .57f, w, h, paint)
    }
}
