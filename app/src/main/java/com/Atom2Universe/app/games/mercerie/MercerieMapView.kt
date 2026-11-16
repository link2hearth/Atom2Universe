package com.Atom2Universe.app.games.mercerie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * La carte des étages : un fil qui grimpe en serpentant, un bouton cousu par étage. Le premier est
 * tout en bas ; la carte s'arrête [AHEAD] étages au-dessus de celui à jouer, et s'allonge à mesure
 * que le joueur monte. Sous chaque bouton, ses étoiles et son meilleur score.
 */
@SuppressLint("ViewConstructor")
class MercerieMapView(context: Context, private val progress: MercerieProgress, private val onPick: (Int) -> Unit) : View(context) {
    private val next = progress.next
    private val count = next + AHEAD
    private val rowHeight = MercerieUi.dp(context, 128f)
    private val radius = MercerieUi.dp(context, 33f)
    private val padTop = MercerieUi.dp(context, 70f)
    private val padBottom = MercerieUi.dp(context, 84f)
    private val unit = MercerieUi.unit(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold; textAlign = Paint.Align.CENTER }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val thread = Path()
    private val clip = Rect()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private val start = System.nanoTime()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = (padTop + padBottom + count * rowHeight).toInt()
        // Tant qu'il y a peu d'étages, la carte remplit quand même l'écran : le premier reste en bas.
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) maxOf(wanted, MeasureSpec.getSize(heightMeasureSpec)) else wanted
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun nodeX(k: Int) = width / 2f + min(width * .3f, MercerieUi.dp(context, 150f)) * sin(k * 1.05f)
    private fun nodeY(k: Int) = height - padBottom - radius - k * rowHeight

    /** Hauteur de l'étage [level] dans la carte, pour amener l'écran dessus. */
    fun levelY(level: Int) = nodeY(level - 1)

    private fun segment(from: Int) {
        val x0 = nodeX(from); val y0 = nodeY(from)
        val x1 = nodeX(from + 1); val y1 = nodeY(from + 1)
        thread.moveTo(x0, y0)
        thread.cubicTo(x0, y0 - rowHeight * .5f, x1, y1 + rowHeight * .5f, x1, y1)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.getClipBounds(clip)
        // Seuls les étages visibles sont dessinés : la carte peut devenir très longue.
        val first = ((height - padBottom - clip.bottom) / rowHeight).toInt().coerceIn(0, count - 1)
        val last = ((height - padBottom - clip.top) / rowHeight).toInt().plus(1).coerceIn(0, count - 1)
        thread.rewind()
        for (k in maxOf(first - 1, 0) until minOf(last + 1, next - 1)) segment(k)
        Felt.stitches(canvas, thread, Felt.alpha(Felt.INK, 150), unit, .06f)
        // Le fil des étages à venir est plus pâle, et s'efface au-dessus du dernier.
        thread.rewind()
        for (k in maxOf(first - 1, next - 1) until minOf(last + 1, count - 1)) segment(k)
        if (last >= count - 1) {
            thread.moveTo(nodeX(count - 1), nodeY(count - 1))
            thread.lineTo(nodeX(count - 1), nodeY(count - 1) - rowHeight * .55f)
        }
        Felt.stitches(canvas, thread, Felt.alpha(Felt.INK, 60), unit, .06f)

        val t = (System.nanoTime() - start) / 1e9f
        for (k in first..last) {
            val level = k + 1
            val stars = progress.stars(level)
            val open = level <= next
            val current = level == next
            val x = nodeX(k)
            val y = nodeY(k)
            val r = radius * (if (current) 1.18f + sin(t * 4f) * .05f else 1f)
            val color = if (!open) Felt.GREY else if (current) Felt.PINK else Felt.COLORS[k % Felt.COLORS.size]
            if (!open) canvas.saveLayerAlpha(x - r * 1.3f, y - r * 1.3f, x + r * 1.3f, y + r * 1.5f, 150)
            path.rewind()
            path.addCircle(x, y, r, Path.Direction.CW)
            Felt.shape(canvas, path, color, r * 2, stitch = open, shadow = open, plush = open)
            rim.color = Felt.darker(color, .85f)
            rim.strokeWidth = r * .07f
            canvas.drawCircle(x, y, r * .62f, rim)
            val digits = level.toString()
            text.textSize = r * (if (digits.length <= 2) .8f else if (digits.length == 3) .6f else .47f)
            text.color = if (open) Felt.CREAM else Felt.darker(Felt.GREY, .8f)
            canvas.drawText(digits, x, y + text.textSize * .35f, text)
            if (!open) { canvas.restore(); continue }
            val sy = y + radius * 1.52f
            for (s in 0 until 3) Felt.star(canvas, x + (s - 1) * radius * .62f, sy + (if (s == 1) radius * .08f else 0f), radius * .24f, s < stars)
            val best = progress.best(level)
            if (best > 0) {
                text.textSize = radius * .42f
                text.color = Felt.INK
                canvas.drawText(MercerieUi.number(best), x, sy + radius * .86f, text)
            }
        }
        if (next - 1 in first..last) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) return true
                val k = ((height - padBottom - radius - event.y) / rowHeight + .5f).toInt()
                for (n in maxOf(k - 1, 0)..minOf(k + 1, count - 1)) {
                    if (hypot(event.x - nodeX(n), event.y - nodeY(n)) <= radius * 1.5f) {
                        if (progress.unlocked(n + 1)) onPick(n + 1)
                        break
                    }
                }
            }
        }
        return true
    }

    companion object {
        /** Étages visibles au-dessus de celui à jouer. */
        const val AHEAD = 3
    }
}

/** La jauge de score : un fil qui se remplit, avec les trois étoiles posées à leur seuil. */
@SuppressLint("ViewConstructor")
class StarBarView(context: Context, private val thresholds: IntArray) : View(context) {
    var score = 0
        set(value) { if (field != value) { field = value; invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        val r = height * .36f
        val left = r * .6f
        val right = width - r * 1.2f
        val y = height * .5f
        val top = thresholds[2].coerceAtLeast(1)
        paint.strokeWidth = height * .26f
        paint.color = Felt.LINEN
        canvas.drawLine(left, y, right, y, paint)
        val u = (score.toFloat() / top).coerceIn(0f, 1f)
        if (u > 0f) {
            paint.color = Felt.GOLD
            canvas.drawLine(left, y, left + (right - left) * u, y, paint)
        }
        for (k in 0 until 3) {
            val x = left + (right - left) * thresholds[k] / top
            Felt.star(canvas, x, y, r, score >= thresholds[k])
        }
    }
}

/** Les étoiles d'une fin de niveau : elles se posent l'une après l'autre. */
@SuppressLint("ViewConstructor")
class StarsView(context: Context, private val count: Int, private val animated: Boolean = true) : View(context) {
    private val start = System.nanoTime()

    override fun onDraw(canvas: Canvas) {
        val r = height * .34f
        val t = (System.nanoTime() - start) / 1e6f
        var moving = false
        for (k in 0 until 3) {
            val x = width / 2f + (k - 1) * r * 2.5f
            val y = height * .5f + (if (k == 1) -r * .25f else r * .1f)
            Felt.star(canvas, x, y, r * (if (k == 1) 1.15f else 1f), false)
            if (k >= count) continue
            val u = if (animated) ((t - 250f - k * 320f) / 320f).coerceIn(0f, 1f) else 1f
            if (u < 1f) moving = true
            if (u <= 0f) continue
            val v = u - 1
            val scale = v * v * (2.9f * v + 1.9f) + 1
            Felt.star(canvas, x, y, r * (if (k == 1) 1.15f else 1f) * scale, true)
        }
        if (moving) postInvalidateOnAnimation()
    }
}
