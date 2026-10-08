package com.Atom2Universe.app.games.motocross

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class MotocrossHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            0xFF223B4E.toInt(), 0xFF72919C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = 0xFFF9DAA5.toInt()
        canvas.drawCircle(w * .79f, h * .19f, min(w, h) * .07f, paint)
        val path = Path()
        repeat(2) { layer ->
            val base = h * (.43f + layer * .15f)
            path.rewind(); path.moveTo(0f, base)
            path.lineTo(w * .18f, base - h * .15f)
            path.lineTo(w * .35f, base + h * .03f)
            path.lineTo(w * .67f, base - h * .12f)
            path.lineTo(w, base + h * .06f)
            path.lineTo(w, h); path.lineTo(0f, h); path.close()
            paint.color = if (layer == 0) 0xFF527180.toInt() else 0xFF244151.toInt()
            canvas.drawPath(path, paint)
        }
        path.rewind(); path.moveTo(0f, h * .66f)
        path.cubicTo(w * .2f, h * .67f, w * .25f, h * .49f, w * .40f, h * .55f)
        path.cubicTo(w * .58f, h * .72f, w * .82f, h * .59f, w, h * .57f)
        paint.color = 0xFF9B7142.toInt(); paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * .025f; canvas.drawPath(path, paint)
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        paint.style = Paint.Style.FILL; paint.color = 0xFF3C302A.toInt()
        canvas.drawPath(path, paint)

        // Pose de saut fixe : aucun circuit généré et aucune mise à jour de la physique.
        val bike = MotocrossBike()
        bike.rear.x = bike.x - MotocrossBike.HALF_BASE
        bike.front.x = bike.x + MotocrossBike.HALF_BASE
        bike.rear.y = bike.y - MotocrossBike.REST
        bike.front.y = bike.y - MotocrossBike.REST
        canvas.save()
        canvas.translate(w * .49f, h * .37f)
        canvas.rotate(-12f)
        val scale = min(w * .26f, h * .235f)
        canvas.scale(scale, -scale)
        canvas.translate(-bike.x, -bike.y)
        MotocrossArt().draw(canvas, bike)
        canvas.restore()
        paint.shader = LinearGradient(0f, h * .58f, 0f, h,
            Color.TRANSPARENT, 0xE0111B27.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .58f, w, h, paint)
    }
}
