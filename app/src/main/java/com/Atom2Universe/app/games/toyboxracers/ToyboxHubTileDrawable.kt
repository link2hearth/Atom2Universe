package com.Atom2Universe.app.games.toyboxracers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Illustration fixe, dessinée et mise en cache comme les autres tuiles du hub. */
class ToyboxHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h,
            0xFF304F68.toInt(), 0xFF92B9BB.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Chambre miniature : lumière de la fenêtre, plinthe et parquet.
        paint.color = 0xFFDBEAE0.toInt()
        canvas.drawRoundRect(w * .72f, -h * .05f, w * .94f, h * .32f, 5f, 5f, paint)
        paint.color = 0xFF9CD1D7.toInt()
        canvas.drawRect(w * .735f, 0f, w * .925f, h * .29f, paint)
        paint.color = 0xFFF9EACB.toInt()
        canvas.drawRect(w * .823f, 0f, w * .837f, h * .30f, paint)
        canvas.drawRect(w * .73f, h * .135f, w * .93f, h * .15f, paint)
        canvas.drawRect(w * .705f, h * .30f, w * .955f, h * .325f, paint)
        paint.color = 0xFFA8B8AF.toInt()
        canvas.drawRect(0f, h * .42f, w, h * .45f, paint)
        paint.shader = LinearGradient(0f, h * .45f, 0f, h,
            0xFFC6AC8C.toInt(), 0xFF796A69.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .45f, w, h, paint)
        paint.shader = null
        paint.color = 0x30735C51
        paint.strokeWidth = h * .004f
        for (i in -2..6) {
            canvas.drawLine(w * (.15f + i * .17f), h * .45f, w * (-.6f + i * .4f), h, paint)
        }

        // Blocs de construction en perspective, à l'arrière du circuit.
        fun polygon(color: Int, vararg xy: Float) {
            val path = Path().apply {
                moveTo(xy[0], xy[1])
                for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
                close()
            }
            paint.color = color
            canvas.drawPath(path, paint)
        }
        fun block(x: Float, y: Float, size: Float, front: Int, top: Int, side: Int) {
            val d = size * .35f
            polygon(front, x, y, x + size, y, x + size, y + size, x, y + size)
            polygon(top, x, y, x + d, y - d, x + size + d, y - d, x + size, y)
            polygon(side, x + size, y, x + size + d, y - d,
                x + size + d, y + size - d, x + size, y + size)
        }
        val unit = min(w, h)
        block(w * .08f, h * .35f, unit * .14f,
            0xFFCE879D.toInt(), 0xFFF4B5BC.toInt(), 0xFF9D647F.toInt())
        block(w * .21f, h * .41f, unit * .115f,
            0xFF84B8A9.toInt(), 0xFFBCE0C6.toInt(), 0xFF5D908C.toInt())
        block(w * .12f, h * .255f, unit * .095f,
            0xFFE1B773.toInt(), 0xFFFFDFA0.toInt(), 0xFFB88759.toInt())

        // Virage relevé et tremplin : silhouette ouverte laissant le saut lisible.
        val track = Path().apply {
            moveTo(-w * .12f, h * .94f)
            cubicTo(w * .10f, h * .59f, w * .39f, h * .88f, w * .47f, h * .58f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = unit * .25f
        paint.color = 0xFF9C5B40.toInt()
        canvas.save()
        canvas.translate(0f, h * .02f)
        canvas.drawPath(track, paint)
        canvas.restore()
        paint.color = 0xFFFFD08A.toInt()
        canvas.drawPath(track, paint)
        paint.strokeWidth = unit * .205f
        paint.color = 0xFFEBA454.toInt()
        canvas.drawPath(track, paint)
        paint.strokeWidth = unit * .012f
        paint.color = 0xFFFFEDC0.toInt()
        canvas.drawPath(track, paint)
        val landing = Path().apply {
            moveTo(w * .78f, h * .61f)
            cubicTo(w * .88f, h * .78f, w * 1.1f, h * .65f, w * 1.12f, h * .45f)
        }
        paint.strokeWidth = unit * .25f
        paint.color = 0xFFBBE4CF.toInt()
        canvas.drawPath(landing, paint)
        paint.strokeWidth = unit * .205f
        paint.color = 0xFF589E97.toInt()
        canvas.drawPath(landing, paint)
        paint.style = Paint.Style.FILL

        // Ombre séparée de la voiture pour lire immédiatement la hauteur du saut.
        paint.color = 0x3533414D
        canvas.drawOval(w * .46f, h * .65f, w * .70f, h * .70f, paint)
        canvas.save()
        canvas.translate(w * .55f, h * .405f)
        val carScale = min(w * .0028f, h * .0039f)
        canvas.scale(carScale, carScale)
        canvas.rotate(-14f)

        // Petite voiture jouet à grosses roues, carrosserie corail et vitres bleues.
        fun wheel(x: Float, y: Float) {
            paint.color = 0xFF253748.toInt()
            canvas.drawOval(x - 12f, y - 14f, x + 12f, y + 14f, paint)
            paint.color = 0xFFAAC6CF.toInt()
            canvas.drawOval(x - 6f, y - 8f, x + 6f, y + 8f, paint)
            paint.color = 0xFFF7E8CF.toInt()
            canvas.drawCircle(x, y, 3f, paint)
        }
        wheel(-24f, 9f)
        wheel(36f, 8f)
        polygon(0xFFEC7986.toInt(), -48f, -7f, -26f, -25f, 29f, -25f,
            57f, -7f, 46f, 18f, -43f, 18f)
        polygon(0xFFB94966.toInt(), -43f, 18f, 46f, 18f, 49f, 32f, -41f, 32f)
        polygon(0xFFFFB7A8.toInt(), -48f, -7f, -26f, -25f, 29f, -25f,
            57f, -7f, 37f, 3f, -29f, 3f)
        polygon(0xFFF8DBB9.toInt(), -24f, -9f, -14f, -35f, 12f, -37f,
            33f, -17f, 26f, 3f, -29f, 3f)
        polygon(0xFF385C77.toInt(), -20f, -10f, -11f, -30f, 1f, -31f,
            3f, -9f)
        polygon(0xFF719FAD.toInt(), 6f, -31f, 13f, -32f, 28f, -17f,
            23f, -4f, 8f, -8f)
        polygon(0xFFDDF4E9.toInt(), 8f, -28f, 13f, -28f, 24f, -17f,
            21f, -13f)
        wheel(-27f, 28f)
        wheel(32f, 27f)
        paint.color = 0xFFFFE6A4.toInt()
        canvas.drawRoundRect(44f, 5f, 55f, 12f, 2f, 2f, paint)
        paint.color = 0xFFFDEBCD.toInt()
        canvas.drawRoundRect(-48f, -9f, -28f, -4f, 2f, 2f, paint)
        canvas.restore()

        // Même réserve sombre pour le titre que Motocross et Cosmo Run.
        paint.shader = LinearGradient(0f, h * .56f, 0f, h,
            Color.TRANSPARENT, 0xEE172535.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .56f, w, h, paint)
    }
}
