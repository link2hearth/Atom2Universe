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
import android.view.ViewGroup
import android.view.ViewTreeObserver
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * La carte des niveaux : des boutons cousus le long d'un fil qui serpente, rangée après rangée, le
 * premier en haut à gauche. Chaque rangée porte autant de boutons que la largeur en accueille, pour
 * en montrer le plus possible à l'écran et remonter vite, même depuis le millième. La carte s'arrête
 * [AHEAD] niveaux après celui à jouer. Sous chaque bouton, ses étoiles et son meilleur score.
 */
@SuppressLint("ViewConstructor")
class MercerieMapView(context: Context, private val progress: MercerieProgress, private val onPick: (Int) -> Unit) : ViewGroup(context), Coverable {
    private val next = progress.next
    private val count = next + AHEAD
    private val radius = MercerieUi.dp(context, 30f)
    private val rowHeight = radius * 3.75f
    private val padTop = MercerieUi.dp(context, 14f)
    private val side = MercerieUi.dp(context, 10f)
    private val unit = MercerieUi.unit(context)
    private var perRow = 4
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold; textAlign = Paint.Align.CENTER }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val thread = Path()
    private val clip = Rect()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private val start = System.nanoTime()
    /** Le cadre du bouton qui bat, autour de son centre : le bouton gonflé, ses étoiles, son score. */
    private val beatHalf = (radius * 1.5f).toInt()
    private val beatTop = (radius * 1.5f).toInt()
    private val beatBottom = (radius * 2.75f).toInt()
    private val beat = Beat(context)
    private val easyTag = context.getString(com.Atom2Universe.app.R.string.mercerie_easy_tag)

    /** Sous la fiche d'un niveau, le bouton en cours cesse de battre. */
    override var covered = false
        set(value) { if (field != value) { field = value; beat.invalidate() } }

    init {
        setWillNotDraw(false)
        addView(beat)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        perRow = ((w - 2 * side) / (radius * 2.9f)).toInt().coerceAtLeast(3)
        val rows = (count + perRow - 1) / perRow
        val wanted = (padTop * 2 + rows * rowHeight).toInt()
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) maxOf(wanted, MeasureSpec.getSize(heightMeasureSpec)) else wanted
        setMeasuredDimension(w, h)
        beat.measure(MeasureSpec.makeMeasureSpec(beatHalf * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(beatTop + beatBottom, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = nodeX(next - 1).toInt() - beatHalf
        val y = nodeY(next - 1).toInt() - beatTop
        beat.layout(x, y, x + beat.measuredWidth, y + beat.measuredHeight)
    }

    private fun nodeX(k: Int): Float {
        val row = k / perRow
        var col = k % perRow
        // Une rangée sur deux repart dans l'autre sens : le fil ne saute jamais.
        if (row % 2 == 1) col = perRow - 1 - col
        return side + (col + .5f) * (width - 2 * side) / perRow
    }

    private fun nodeY(k: Int) = padTop + (k / perRow) * rowHeight + radius * 1.25f

    /** Hauteur du niveau [level] dans la carte, pour amener l'écran dessus. */
    fun levelY(level: Int) = nodeY(level - 1)

    private fun segment(from: Int) {
        thread.moveTo(nodeX(from), nodeY(from))
        thread.lineTo(nodeX(from + 1), nodeY(from + 1))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.getClipBounds(clip)
        // Seules les rangées visibles sont dessinées : la carte peut devenir très longue.
        val firstRow = ((clip.top - padTop) / rowHeight).toInt().coerceAtLeast(0)
        val lastRow = ((clip.bottom - padTop) / rowHeight).toInt().coerceAtLeast(0)
        val first = (firstRow * perRow).coerceAtMost(count - 1)
        val last = ((lastRow + 1) * perRow - 1).coerceAtMost(count - 1)
        thread.rewind()
        for (k in maxOf(first - 1, 0) until minOf(last + 1, next - 1)) segment(k)
        Felt.stitches(canvas, thread, Felt.alpha(Felt.INK, 150), unit, .06f)
        // Le fil des niveaux à venir est plus pâle.
        thread.rewind()
        for (k in maxOf(first - 1, next - 1) until minOf(last + 1, count - 1)) segment(k)
        Felt.stitches(canvas, thread, Felt.alpha(Felt.INK, 60), unit, .06f)
        // Le niveau à jouer est cousu par [beat], par-dessus : la carte, elle, ne bouge pas.
        for (k in first..last) if (k != next - 1) node(canvas, k, 0f)
    }

    /** Un bouton de la carte, ses étoiles et son meilleur score ; [t] fait battre celui du niveau à jouer. */
    private fun node(canvas: Canvas, k: Int, t: Float) {
        val level = k + 1
        val stars = progress.stars(level)
        val open = level <= next
        val current = level == next
        val x = nodeX(k)
        val y = nodeY(k)
        val r = radius * (if (current) 1.16f + sin(t * 4f) * .05f else 1f)
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
        if (!open) { canvas.restore(); return }
        val sy = y + radius * 1.5f
        // Fini seulement en mode facile : ses étoiles en vert menthe et « facile » à la place du score.
        val easy = progress.onlyEasy(level)
        val shown = if (easy) progress.easyStars(level) else stars
        for (s in 0 until 3) Felt.star(canvas, x + (s - 1) * radius * .62f, sy + (if (s == 1) radius * .08f else 0f), radius * .24f, s < shown, easy)
        text.textSize = radius * .42f
        if (easy) {
            text.color = if (Felt.dark) Felt.lighter(Felt.MINT, .55f) else Felt.darker(Felt.MINT, .6f)
            canvas.drawText(easyTag, x, sy + radius * .86f, text)
            return
        }
        val best = progress.best(level)
        if (best > 0) {
            text.color = Felt.INK
            canvas.drawText(MercerieUi.number(best), x, sy + radius * .86f, text)
        }
    }

    /**
     * Le bouton du niveau à jouer, qui bat. Il a sa propre vue, seule redessinée au rythme du
     * battement : avec le canevas matériel, la carte entière (des centaines de boutons cousus,
     * d'étoiles et de nombres, pas seulement ce que montre le défilement) était sinon refaite
     * vingt-cinq fois par seconde. Défilé hors de l'écran, il ne bat plus jusqu'à son retour.
     */
    private inner class Beat(context: Context) : View(context) {
        private var pending = false
        private val tick = Runnable { pending = false; invalidate() }
        private val seen = Rect()
        private val onScroll = ViewTreeObserver.OnScrollChangedListener {
            if (!pending && !covered && getLocalVisibleRect(seen)) invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver.addOnScrollChangedListener(onScroll)
        }

        override fun onDetachedFromWindow() {
            viewTreeObserver.removeOnScrollChangedListener(onScroll)
            removeCallbacks(tick)
            pending = false
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.translate(-left.toFloat(), -top.toFloat())
            node(canvas, next - 1, (System.nanoTime() - start) / 1e9f)
            canvas.restore()
            // Un seul rappel retardé à la fois : un invalidate() extérieur ne dédouble pas la chaîne.
            removeCallbacks(tick)
            pending = !covered && getLocalVisibleRect(seen)
            if (pending) postDelayed(tick, MercerieUi.idleDelay())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) return true
                val row = ((event.y - padTop) / rowHeight).toInt().coerceAtLeast(0)
                for (n in row * perRow until minOf((row + 1) * perRow, count)) {
                    if (hypot(event.x - nodeX(n), event.y - nodeY(n)) <= radius * 1.45f) {
                        if (progress.unlocked(n + 1)) onPick(n + 1)
                        break
                    }
                }
            }
        }
        return true
    }

    companion object {
        /** Niveaux visibles après celui à jouer. */
        const val AHEAD = 3
    }
}

/** La jauge de score : un fil qui se remplit, avec les trois étoiles posées à leur seuil. */
@SuppressLint("ViewConstructor")
class StarBarView(context: Context, private val thresholds: IntArray) : View(context) {
    var score = 0
        set(value) {
            if (field == value) return
            field = value
            // Une étoile atteinte saute et brille un instant.
            val now = android.os.SystemClock.uptimeMillis()
            for (k in 0 until 3) if (value >= thresholds[k] && popAt[k] == 0L) popAt[k] = now
            invalidate()
        }
    private val popAt = LongArray(3)
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
        val now = android.os.SystemClock.uptimeMillis()
        var popping = false
        for (k in 0 until 3) {
            val x = left + (right - left) * thresholds[k] / top
            val age = now - popAt[k]
            val pop = if (popAt[k] != 0L && age < 520) { popping = true; 1f + sin(age / 520f * Math.PI.toFloat()) * .7f } else 1f
            Felt.star(canvas, x, y, r * pop, score >= thresholds[k])
        }
        if (popping) postInvalidateOnAnimation()
    }
}

/** Les étoiles d'une fin de niveau : elles se posent l'une après l'autre ; [easy] : vert menthe du mode facile. */
@SuppressLint("ViewConstructor")
class StarsView(context: Context, private val count: Int, private val animated: Boolean = true, private val easy: Boolean = false) : View(context) {
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
            Felt.star(canvas, x, y, r * (if (k == 1) 1.15f else 1f) * scale, true, easy)
        }
        if (moving) postInvalidateOnAnimation()
    }
}
