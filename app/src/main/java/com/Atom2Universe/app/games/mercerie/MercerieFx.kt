package com.Atom2Universe.app.games.mercerie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les confettis du jeu : de petits morceaux de feutrine (cœur, étoile, rond, losange, bouton à deux
 * trous), tracés une fois à l'unité puis posés à la bonne taille par le canevas.
 */
object Confetti {
    val shapes: Array<Path> = arrayOf(heart(), Felt.starPath(.82f), circle(), diamond(), button())
    /** Une étincelle à quatre branches, pour les scintillements. */
    val sparkle: Path = Path().apply {
        moveTo(0f, -1f); quadTo(.12f, -.12f, 1f, 0f); quadTo(.12f, .12f, 0f, 1f)
        quadTo(-.12f, .12f, -1f, 0f); quadTo(-.12f, -.12f, 0f, -1f); close()
    }

    private fun heart() = Path().apply {
        moveTo(0f, .9f)
        cubicTo(-1.32f, -.02f, -.6f, -1.08f, 0f, -.42f)
        cubicTo(.6f, -1.08f, 1.32f, -.02f, 0f, .9f)
        close()
    }

    private fun circle() = Path().apply { addCircle(0f, 0f, .8f, Path.Direction.CW) }

    private fun diamond() = Path().apply { moveTo(0f, -1f); lineTo(.7f, 0f); lineTo(0f, 1f); lineTo(-.7f, 0f); close() }

    private fun button() = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addCircle(0f, 0f, .85f, Path.Direction.CW)
        addCircle(-.3f, 0f, .17f, Path.Direction.CW)
        addCircle(.3f, 0f, .17f, Path.Direction.CW)
    }

    fun draw(canvas: Canvas, paint: Paint, shape: Path, x: Float, y: Float, size: Float, angle: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        canvas.scale(size, size)
        canvas.drawPath(shape, paint)
        canvas.restore()
    }
}

/** Un mot de félicitation, découpé lettre par lettre dans la feutrine. */
class CheerWord(val text: String, val tier: Int, val letters: List<Bitmap?>, val advances: FloatArray, val size: Float) {
    val width: Float = advances.sum()

    fun recycle() = letters.forEach { it?.recycle() }

    companion object {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND }
        private val glyph = Path()

        /**
         * Les couleurs des lettres, de plus en plus vives avec le palier : du vert et du bleu pour un
         * joli coup, tout l'arc-en-ciel puis l'or pour les plus grands. Le palier 0 (avertissement) est rose.
         */
        private fun colorOf(tier: Int, k: Int): Int {
            val c = Felt.COLORS
            return when (tier) {
                0 -> if (k % 2 == 0) Felt.PINK else c[0]
                1 -> if (k % 2 == 0) c[3] else c[1]
                2 -> intArrayOf(c[1], c[4], c[3])[k % 3]
                3 -> intArrayOf(c[0], c[5], c[2])[k % 3]
                4 -> c[k % c.size]
                else -> intArrayOf(Felt.GOLD, c[5], Felt.GOLD, c[0])[k % 4]
            }
        }

        fun bake(text: String, tier: Int, size: Float): CheerWord {
            paint.textSize = size
            val letters = ArrayList<Bitmap?>()
            val advances = FloatArray(text.length)
            var n = 0
            for ((k, ch) in text.withIndex()) {
                val s = ch.toString()
                val w = paint.measureText(s)
                advances[k] = w + size * .04f
                if (ch.isWhitespace()) { letters += null; continue }
                val b = Bitmap.createBitmap((w + size * .7f).toInt().coerceAtLeast(1), (size * 1.7f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.translate(b.width / 2f, size * BASE)
                paint.getTextPath(s, 0, 1, -w / 2, 0f, glyph)
                // Un bord de fil clair tout autour, pour que le mot se lise sur n'importe quelle pièce.
                outline.strokeWidth = size * .3f
                outline.color = 0x553A2418
                c.save(); c.translate(0f, size * .07f); c.drawPath(glyph, outline); c.restore()
                outline.color = Felt.CREAM
                c.drawPath(glyph, outline)
                Felt.letter(c, glyph, colorOf(tier, n), size)
                letters += b
                n++
            }
            return CheerWord(text, tier, letters, advances, size)
        }

        /** Hauteur de la ligne de base dans l'image d'une lettre, en tailles de texte. */
        const val BASE = 1.15f
    }
}

/**
 * La couche de vol, posée sur tout l'écran de jeu : les pièces récoltées volent jusqu'à leur
 * compteur, les coups restants filent du compteur vers le plateau pendant la couture finale, et les
 * confettis pleuvent à la victoire. Elle ne prend jamais le doigt.
 */
class FlightLayer(context: Context) : View(context) {
    private class Flight(
        val image: Bitmap, val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val size0: Float, val size1: Float, val start: Long, val life: Long, val bend: Float,
        val trail: Int, val arrive: (() -> Unit)?
    ) { var done = false; var lastTrail = 0L }

    private class Bit(
        var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val color: Int,
        var angle: Float, val spin: Float, val shape: Int, val born: Long, val life: Long, val gravity: Float, val sway: Float
    )

    private val flights = ArrayList<Flight>()
    private val bits = ArrayList<Bit>()
    private val rnd = Random(SystemClock.uptimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val here = IntArray(2)
    private var lastFrame = 0L

    init {
        isClickable = false
        isFocusable = false
    }

    /** Lâche tout : l'écran change. */
    fun clear() {
        flights.clear()
        bits.clear()
        invalidate()
    }

    /**
     * Fait voler [image] du point ([x0], [y0]) de la fenêtre jusqu'au centre de [target], de la
     * taille [size0] à [size1], après [delay] ms ; [arrive] est appelé à l'arrivée.
     */
    fun fly(image: Bitmap, x0: Float, y0: Float, target: View, size0: Float, size1: Float, delay: Long = 0, life: Long = 560, trail: Int = 0, arrive: (() -> Unit)? = null) {
        val t = IntArray(2)
        target.getLocationInWindow(t)
        flyTo(image, x0, y0, t[0] + target.width / 2f, t[1] + target.height / 2f, size0, size1, delay, life, trail, arrive)
    }

    fun flyTo(image: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, size0: Float, size1: Float, delay: Long = 0, life: Long = 560, trail: Int = 0, arrive: (() -> Unit)? = null) {
        getLocationInWindow(here)
        if (flights.size > 60) { arrive?.invoke(); return }
        val bend = (if (rnd.nextBoolean()) 1f else -1f) * (.18f + rnd.nextFloat() * .16f)
        flights += Flight(image, x0 - here[0], y0 - here[1], x1 - here[0], y1 - here[1], size0, size1,
            SystemClock.uptimeMillis() + delay, life, bend, trail, arrive)
        invalidate()
    }

    /** Une pluie de confettis de feutrine sur tout l'écran ; plus fournie avec les étoiles. */
    fun confetti(stars: Int) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val unit = MercerieUi.dp(context, 9f)
        repeat(50 + stars * 35) {
            val color = Felt.COLORS[rnd.nextInt(Felt.COLORS.size)]
            bits += Bit(rnd.nextFloat() * w, -rnd.nextFloat() * h * .5f - unit * 2, (rnd.nextFloat() - .5f) * unit * 8f,
                unit * (14f + rnd.nextFloat() * 18f), unit * (.8f + rnd.nextFloat() * .9f), if (rnd.nextInt(6) == 0) Felt.GOLD else color,
                rnd.nextFloat() * 360f, (rnd.nextFloat() - .5f) * 400f, rnd.nextInt(Confetti.shapes.size), now + rnd.nextInt(700), 3400L,
                unit * 6f, unit * (2f + rnd.nextFloat() * 4f))
        }
        invalidate()
    }

    private fun burst(x: Float, y: Float, color: Int, size: Float) {
        val now = SystemClock.uptimeMillis()
        repeat(7) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = size * (3f + rnd.nextFloat() * 4f)
            bits += Bit(x, y, cos(a) * v, sin(a) * v, size * (.12f + rnd.nextFloat() * .1f), if (rnd.nextBoolean()) Felt.CREAM else color,
                rnd.nextFloat() * 360f, (rnd.nextFloat() - .5f) * 600f, if (rnd.nextBoolean()) 1 else 0, now, 420L, size * 6f, 0f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(50L) / 1000f
        lastFrame = now
        var waiting = false
        val it = flights.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (now < f.start) { waiting = true; continue }
            val u = ((now - f.start).toFloat() / f.life).coerceIn(0f, 1f)
            // Départ en douceur, arrivée qui accélère : la pièce est « aspirée » par son compteur.
            val e = u * u * (3 - 2 * u) * .4f + u * u * .6f
            val mx = (f.x0 + f.x1) / 2 - (f.y1 - f.y0) * f.bend
            val my = (f.y0 + f.y1) / 2 + (f.x1 - f.x0) * f.bend
            val x = (1 - e) * (1 - e) * f.x0 + 2 * (1 - e) * e * mx + e * e * f.x1
            val y = (1 - e) * (1 - e) * f.y0 + 2 * (1 - e) * e * my + e * e * f.y1
            val pop = if (u < .2f) 1f + sin(u / .2f * PI.toFloat()) * .25f else 1f
            val s = (f.size0 + (f.size1 - f.size0) * e) * pop
            if (f.trail != 0 && now - f.lastTrail > 28 && bits.size < 400) {
                f.lastTrail = now
                bits += Bit(x, y, (rnd.nextFloat() - .5f) * s, (rnd.nextFloat() - .5f) * s, s * (.12f + rnd.nextFloat() * .08f), f.trail,
                    rnd.nextFloat() * 360f, 300f, 1, now, 380L, 0f, 0f)
            }
            dst.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
            paint.alpha = 255
            canvas.save()
            canvas.rotate(sin(u * PI.toFloat()) * 18f * (if (f.bend > 0) 1f else -1f), x, y)
            canvas.drawBitmap(f.image, null, dst, paint)
            canvas.restore()
            if (u >= 1f && !f.done) {
                f.done = true
                burst(x, y, if (f.trail != 0) f.trail else Felt.GOLD, f.size1)
                f.arrive?.invoke()
                it.remove()
            }
        }
        val bi = bits.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            if (now < b.born) { waiting = true; continue }
            val age = now - b.born
            if (age > b.life) { bi.remove(); continue }
            b.vy += b.gravity * dt
            b.vx *= 1f - min(1f, dt * 1.5f)
            b.x += (b.vx + sin(age / 260f + b.size) * b.sway) * dt
            b.y += b.vy * dt
            b.angle += b.spin * dt
            if (b.y > height + b.size * 4) { bi.remove(); continue }
            val u = age.toFloat() / b.life
            paint.color = b.color
            paint.alpha = ((if (u > .75f) (1f - u) / .25f else 1f) * 255).toInt().coerceIn(0, 255)
            Confetti.draw(canvas, paint, if (b.shape == 1 && b.gravity == 0f) Confetti.sparkle else Confetti.shapes[b.shape], b.x, b.y, b.size, b.angle)
        }
        paint.alpha = 255
        if (flights.isNotEmpty() || bits.isNotEmpty() || waiting) postInvalidateOnAnimation() else lastFrame = 0L
    }
}
