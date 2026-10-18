package com.Atom2Universe.app.games.hexrunner

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.*

/** Tunnel à six faces et joueur triangulaire, figés comme un instantané du jeu. */
class HexRunnerHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF254D69.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val cx = w * .5f
        val cy = h * .34f
        // Couvrir aussi les coins, quel que soit le rapport largeur/hauteur.
        val radius = hypot(w * .5f, h * .66f) / cos(PI.toFloat() / 6f) * 1.02f
        val hsv = floatArrayOf(0f, .58f, .90f)
        // Trapèzes concentriques, même palette HSV que HexRunnerView.
        for (ring in 0..9) {
            val outer = radius * .73f.pow(ring)
            val inner = outer * .73f
            hsv[0] = (165f + ring * 29f) % 360f
            val color = Color.HSVToColor(hsv)
            for (face in 0..5) {
                val a = (face * PI / 3.0 - PI / 6.0).toFloat()
                val b = a + PI.toFloat() / 3f
                path.rewind()
                path.moveTo(cx + cos(a) * outer, cy + sin(a) * outer)
                path.lineTo(cx + cos(b) * outer, cy + sin(b) * outer)
                path.lineTo(cx + cos(b) * inner, cy + sin(b) * inner)
                path.lineTo(cx + cos(a) * inner, cy + sin(a) * inner)
                path.close()
                paint.style = Paint.Style.FILL
                // Toutes les faces restent colorées ; seule la profondeur diminue leur éclat.
                paint.color = (color and 0x00FFFFFF) or (max(150, 245 - ring * 10) shl 24)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, h * .006f)
                paint.color = 0x70415C80
                canvas.drawPath(path, paint)
            }
        }
        val size = min(w, h) * .047f
        val x = cx - min(w, h) * .24f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        path.rewind(); path.moveTo(x + size, cy)
        path.lineTo(x - size, cy - size); path.lineTo(x - size, cy + size); path.close()
        canvas.drawPath(path, paint)
        paint.shader = LinearGradient(0f, h * .48f, 0f, h,
            Color.TRANSPARENT, 0xC0152948.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .48f, w, h, paint)
    }
}
