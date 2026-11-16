package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Une place de la couveuse : un nid vide, un œuf qui dort (il respire doucement, les yeux fermés,
 * des « z » montent au-dessus de lui), ou un œuf réveillé, prêt à éclore, qui se dandine.
 */
class PapattesEggView(context: Context) : View(context) {
    enum class State { EMPTY, SLEEPING, READY }

    var state = State.EMPTY
        set(value) { if (field != value) { field = value; invalidate() } }
    /** Couleur des « z » et des traits du visage, prise au thème. */
    var inkColor = 0xFF5A3A20.toInt()

    private val sprites = PapattesSprites.get(context)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val egg = RectF()
    private val rect = RectF()
    private val path = Path()
    /** Décalage propre à chaque place : les œufs ne respirent pas tous ensemble. */
    private val phase = (System.identityHashCode(this) % 1000) / 1000f * 6.28f
    private val next = Runnable { invalidate() }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f + phase
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val bmp = sprites.egg.bitmap
        val eh = min(h * .66f, w * .62f * bmp.height / bmp.width)
        val ew = eh * bmp.width / bmp.height
        val base = h * .86f
        drawNest(canvas, w / 2, base, ew * 1.45f, state == State.EMPTY)
        if (state == State.EMPTY) return

        canvas.save()
        when (state) {
            State.SLEEPING -> {
                // Il respire : un peu plus haut et plus étroit à l'inspiration, posé sur le nid.
                val breath = sin(t * 1.7f)
                canvas.scale(1f - .025f * breath, 1f + .035f * breath, w / 2, base)
                canvas.rotate(sin(t * .7f) * 2.5f, w / 2, base)
            }
            State.READY -> {
                // Par à-coups : il se dandine, se repose, recommence.
                val beat = t % 1.8f
                val angle = if (beat < .7f) sin(beat * 18f) * 9f * (1f - beat / .7f) else 0f
                canvas.rotate(angle, w / 2, base)
            }
            State.EMPTY -> Unit
        }
        egg.set(w / 2 - ew / 2, base - eh * .92f, w / 2 + ew / 2, base + eh * .08f)
        canvas.drawBitmap(bmp, null, egg, bitmapPaint)
        drawFace(canvas, state == State.SLEEPING, t)
        canvas.restore()
        if (state == State.SLEEPING) drawSnores(canvas, t, eh)
        // Trente images par seconde suffisent à une respiration ; rien ne bouge hors de l'écran.
        removeCallbacks(next)
        if (isShown) postDelayed(next, FRAME_MS)
    }

    /** Un nid de paille : une coupe pleine et quelques brins, pâle quand la place est libre. */
    private fun drawNest(canvas: Canvas, cx: Float, base: Float, width: Float, empty: Boolean) {
        val alpha = if (empty) 110 else 255
        rect.set(cx - width / 2, base - width * .16f, cx + width / 2, base + width * .14f)
        fill.color = 0xFFC9A26B.toInt(); fill.alpha = alpha
        canvas.drawOval(rect, fill)
        line.color = 0xFF8A6A3A.toInt(); line.alpha = alpha
        line.strokeWidth = width * .025f
        for (k in 0 until 5) {
            val u = -.38f + k * .19f
            path.reset()
            path.moveTo(cx + width * (u - .08f), base - width * .02f)
            path.quadTo(cx + width * u, base + width * .07f, cx + width * (u + .1f), base - width * .03f)
            canvas.drawPath(path, line)
        }
        fill.alpha = 255
        line.alpha = 255
    }

    /** Les yeux (fermés en dormant, ouverts une fois prêt) et deux joues roses. */
    private fun drawFace(canvas: Canvas, asleep: Boolean, t: Float) {
        val eyeY = egg.top + egg.height() * .56f
        val gap = egg.width() * .17f
        val r = egg.width() * .07f
        line.color = inkColor
        line.strokeWidth = egg.width() * .035f
        for (side in listOf(-1f, 1f)) {
            val x = egg.centerX() + side * gap
            if (asleep) {
                // Paupière close : un petit arc tourné vers le bas.
                rect.set(x - r, eyeY - r, x + r, eyeY + r)
                canvas.drawArc(rect, 20f, 140f, false, line)
            } else {
                fill.color = inkColor
                val blink = (t % 3.1f) < .12f
                if (blink) canvas.drawLine(x - r * .8f, eyeY, x + r * .8f, eyeY, line)
                else canvas.drawCircle(x, eyeY, r * .75f, fill)
            }
            fill.color = 0x66FF8FA8
            canvas.drawCircle(x + side * r * 1.3f, eyeY + r * 2.1f, r * 1.1f, fill)
        }
    }

    /** Trois « z » qui montent en grandissant et s'effacent, l'un après l'autre. */
    private fun drawSnores(canvas: Canvas, t: Float, eggHeight: Float) {
        line.color = inkColor
        for (k in 0 until 3) {
            val p = (t * .42f + k / 3f) % 1f
            val size = eggHeight * (.12f + .14f * p)
            val x = egg.right - egg.width() * .1f + p * egg.width() * .55f + sin(p * 6f + k) * size * .3f
            val y = egg.top + egg.height() * .15f - p * eggHeight * .55f
            val alpha = when {
                p < .15f -> p / .15f
                p > .6f -> (1f - p) / .4f
                else -> 1f
            }
            line.alpha = (230 * alpha).toInt().coerceIn(0, 255)
            line.strokeWidth = size * .2f
            path.reset()
            path.moveTo(x - size / 2, y - size / 2)
            path.lineTo(x + size / 2, y - size / 2)
            path.lineTo(x - size / 2, y + size / 2)
            path.lineTo(x + size / 2, y + size / 2)
            canvas.drawPath(path, line)
        }
        line.alpha = 255
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(next)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate() else removeCallbacks(next)
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
