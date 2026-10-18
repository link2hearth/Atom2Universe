package com.Atom2Universe.app.games.circles

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

/** L'atome du jeu : quatre couches de tubes lumineux autour d'un noyau, sur le ciel étoilé. */
class CirclesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF05050E.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = min(w * .48f, h * .42f)
        val cx = w * .5f
        val cy = h * .34f

        val random = Random(8128)
        repeat(45) {
            paint.color = Color.argb(70 + random.nextInt(140), 225, 222, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h,
                min(w, h) * (.002f + random.nextFloat() * .003f), paint)
        }

        paint.shader = RadialGradient(cx, cy, radius * 1.2f,
            intArrayOf(0x667C3AED, 0x267C3AED, 0x007C3AED), floatArrayOf(.6f, .88f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.2f, paint)
        paint.shader = null

        val path = Path()
        val offsets = floatArrayOf(-30f, 30f, -90f, 90f)
        // Même palette et secteurs de 60° que le puzzle, avec rotations décalées.
        for (ring in 0..3) {
            val outer = radius * (1f - ring * .23f)
            val inner = outer - radius * .185f
            for (segment in 0 until CirclesGame.SEGMENTS) {
                val start = offsets[ring] + segment * 60f
                path.rewind()
                path.arcTo(RectF(cx - outer, cy - outer, cx + outer, cy + outer), start, 60f, true)
                path.arcTo(RectF(cx - inner, cy - inner, cx + inner, cy + inner), start + 60f, -60f, false)
                path.close()
                paint.style = Paint.Style.FILL
                paint.color = CirclesGame.COLORS[segment]
                canvas.drawPath(path, paint)
            }
            // Relief du tube et coutures
            paint.shader = RadialGradient(cx, cy, outer,
                intArrayOf(0x66000000, 0x38FFFFFF, 0x59000000),
                floatArrayOf(inner / outer, (inner + outer) / 2f / outer, 1f), Shader.TileMode.CLAMP)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outer - inner
            canvas.drawCircle(cx, cy, (outer + inner) / 2f, paint)
            paint.shader = null
            paint.color = 0xE006081A.toInt()
            paint.strokeWidth = radius * .012f
            for (segment in 0 until CirclesGame.SEGMENTS) {
                canvas.save()
                canvas.rotate(offsets[ring] + segment * 60f, cx, cy)
                canvas.drawLine(cx + inner, cy, cx + outer, cy, paint)
                canvas.restore()
            }
        }

        // Noyau
        val glow = radius * .22f * 3.4f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, glow,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF1B8.toInt(), 0x66FFB347, 0x00FFB347),
            floatArrayOf(0f, .26f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, glow, paint)
        paint.shader = null

        paint.shader = LinearGradient(0f, h * .54f, 0f, h,
            Color.TRANSPARENT, 0xEF05050E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .54f, w, h, paint)
    }
}
