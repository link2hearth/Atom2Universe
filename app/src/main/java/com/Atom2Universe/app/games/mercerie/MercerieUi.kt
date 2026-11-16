package com.Atom2Universe.app.games.mercerie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Mesures communes à tous les écrans du jeu. */
object MercerieUi {
    /** Sur tablette, tout est dessiné plus grand. */
    fun scale(context: Context): Float =
        if (context.resources.configuration.smallestScreenWidthDp >= 600) 1.45f else 1f

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density * scale(context)

    /** L'épaisseur de référence des coutures de l'interface. */
    fun unit(context: Context): Float = dp(context, 50f)

    val bold: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

    private val format = java.text.NumberFormat.getIntegerInstance()

    /**
     * Un nombre écrit à la manière du pays. L'espace fine des milliers (français) est remplacée par
     * une espace insécable ordinaire : avec la police grasse, la fine était mal mesurée et le
     * dernier chiffre se retrouvait rogné (« 3 50 » pour 3 500).
     */
    fun number(value: Int): String = format.format(value).replace('\u202F', '\u00A0')

    fun label(context: Context, sizeSp: Float, color: Int = Felt.INK): TextView = TextView(context).apply {
        textSize = sizeSp * scale(context)
        typeface = bold
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
}

/** Fond d'un bouton ou d'un panneau : un morceau de feutrine cousu. */
class FeltDrawable(
    context: Context, private val color: Int, private val radiusDp: Float = 26f,
    private val stitch: Boolean = true, private val threadColor: Int = Felt.lighter(color, .6f)
) : Drawable() {
    private val unit = MercerieUi.unit(context)
    private val radius = MercerieUi.dp(context, radiusDp)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val pad = unit * .05f
        Felt.panel(canvas, b.left + pad, b.top + pad * .6f, b.right - pad, b.bottom - pad * 2f,
            min(radius, b.height() / 2f), color, unit, stitch, threadColor = threadColor)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Le fond des écrans : le patchwork matelassé de `assets/mercerie/fond.webp`, posé sur la largeur
 * de l'écran et répété en miroir vers le bas. Sans image, un tissu piqué du pêche au vert d'eau.
 */
class FabricDrawable(context: Context, private val top: Int = Felt.PEACH, private val bottom: Int = 0xFFDDF2E6.toInt()) : Drawable() {
    private val step = MercerieUi.dp(context, 46f)
    private val unit = MercerieUi.unit(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val quilt = Path()
    private val zoom = if (context.resources.configuration.smallestScreenWidthDp >= 600) 1f else 1.4f
    private val image: Bitmap? = try {
        context.assets.open("mercerie/fond.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        quilt.rewind()
        if (image != null) {
            val shader = android.graphics.BitmapShader(image, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            // Trois carreaux et demi dans le petit côté d'un téléphone, cinq sur une tablette.
            val k = minOf(bounds.width(), bounds.height()).toFloat() / image.width * zoom
            shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(k, k) })
            paint.shader = shader
            return
        }
        paint.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        quilt.rewind()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        var d = -h
        while (d < w) {
            quilt.moveTo(d, 0f); quilt.lineTo(d + h, h)
            quilt.moveTo(d + h, 0f); quilt.lineTo(d, h)
            d += step * 2
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
        if (image == null) Felt.stitches(canvas, quilt, 0x66FFFFFF, unit, .03f)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/** Un bouton en feutrine : largeur naturelle, s'enfonce sous le doigt. */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class FeltButton(context: Context, text: CharSequence, color: Int = Felt.PINK, big: Boolean = false, round: Boolean = false) : TextView(context) {
    init {
        this.text = text
        typeface = MercerieUi.bold
        textSize = (if (big) 30f else 18f) * MercerieUi.scale(context)
        setTextColor(if (color == Felt.CREAM) Felt.INK else Felt.CREAM)
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = FeltDrawable(context, color, if (big) 40f else 28f)
        val h = MercerieUi.dp(context, if (round) 0f else if (big) 46f else 24f).toInt()
        val v = MercerieUi.dp(context, if (big) 22f else 14f).toInt()
        setPadding(h, v, h, v + MercerieUi.dp(context, 3f).toInt())
        if (round) {
            val side = MercerieUi.dp(context, 52f).toInt()
            minimumWidth = side; minWidth = side
            minimumHeight = side; minHeight = side
        } else {
            minHeight = MercerieUi.dp(context, 48f).toInt()
        }
        isClickable = true
        isFocusable = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(.93f).scaleY(.93f).setDuration(70).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        return super.onTouchEvent(event)
    }
}

/** Le titre du jeu, en lettres de feutrine de toutes les couleurs, qui se balancent doucement. */
@SuppressLint("ViewConstructor")
class FeltTitleView(context: Context, text: String, private val sizeDp: Float = 62f) : View(context) {
    private val lines = text.split('\n')
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold }
    private val glyph = Path()
    private val start = System.nanoTime()

    private fun fit(width: Int): Float {
        var size = MercerieUi.dp(context, sizeDp)
        paint.textSize = size
        val widest = lines.maxOf { paint.measureText(it) } * 1.12f
        if (width > 0 && widest > width) size *= width / widest
        return size
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = fit(w)
        setMeasuredDimension(w, (size * 1.22f * lines.size + size * .4f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val size = fit(width)
        paint.textSize = size
        val t = (System.nanoTime() - start) / 1e9f
        var n = 0
        for ((row, line) in lines.withIndex()) {
            val total = paint.measureText(line) + size * .06f * (line.length - 1)
            var x = (width - total) / 2
            val baseline = size * 1.05f + row * size * 1.22f
            for (ch in line) {
                val s = ch.toString()
                val w = paint.measureText(s)
                if (ch != ' ') {
                    val bob = sin(t * 2.2f + n * .7f)
                    canvas.save()
                    canvas.translate(x + w / 2, baseline + bob * size * .035f)
                    canvas.rotate(bob * 3.5f + (if (n % 2 == 0) -3f else 3f))
                    paint.getTextPath(s, 0, 1, -w / 2, 0f, glyph)
                    Felt.letter(canvas, glyph, Felt.COLORS[n % Felt.COLORS.size], size)
                    canvas.restore()
                    n++
                }
                x += w + size * .06f
            }
        }
        postInvalidateOnAnimation()
    }
}

/** Une guirlande de fanions cousus sur un fil, en haut de l'accueil. */
class BuntingView(context: Context) : View(context) {
    private val thread = Path()
    private val flag = Path()
    private val unit = MercerieUi.unit(context)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sag = h * .34f
        thread.rewind()
        thread.moveTo(-unit, h * .08f)
        thread.quadTo(w / 2, h * .08f + sag * 2, w + unit, h * .08f)
        val count = (w / (unit * 1.25f)).toInt().coerceAtLeast(5)
        for (k in 0 until count) {
            val u = (k + .5f) / count
            // Point du fil : courbe de Bézier à un seul sommet.
            val x = (1 - u) * (1 - u) * -unit + 2 * (1 - u) * u * (w / 2) + u * u * (w + unit)
            val y = h * .08f + 2 * (1 - u) * u * sag * 2
            val half = unit * .42f
            flag.rewind()
            flag.moveTo(x - half, y)
            flag.lineTo(x + half, y)
            flag.lineTo(x, y + unit * .95f)
            flag.close()
            Felt.shape(canvas, flag, Felt.COLORS[k % Felt.COLORS.size], unit, plush = false)
        }
        Felt.stitches(canvas, thread, Felt.INK, unit, .035f)
    }
}

/**
 * Pique, la mascotte : un petit hérisson en feutrine (`assets/mercerie/mascot.webp`).
 * [mood] : 0 tranquille (il respire), 1 ravi (il sautille), -1 dépité (il s'affaisse, penché).
 */
class MascotView(context: Context) : View(context) {
    var mood = 0
        set(value) { field = value; invalidate() }
    private val start = System.nanoTime()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val image: Bitmap? = cached(context)

    override fun onDraw(canvas: Canvas) {
        val img = image ?: return
        val t = (System.nanoTime() - start) / 1e9f
        val side = min(width, height) * .84f
        val breathe = sin(t * (if (mood > 0) 7f else 2.4f)) * (if (mood > 0) .05f else .02f)
        val hop = if (mood > 0) abs(sin(t * 7f)) * side * .08f else 0f
        val cx = width / 2f
        val bottom = height * .5f + side / 2
        canvas.save()
        canvas.translate(0f, -hop)
        // Il s'écrase et s'étire autour de ses pattes, qui ne quittent pas le sol.
        canvas.scale(1f + breathe, 1f - breathe, cx, bottom)
        if (mood < 0) {
            canvas.rotate(-9f, cx, bottom)
            canvas.scale(1.04f, .93f, cx, bottom)
        }
        dst.set(cx - side / 2, bottom - side, cx + side / 2, bottom)
        canvas.drawBitmap(img, null, dst, paint)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    private companion object {
        var shared: Bitmap? = null

        fun cached(context: Context): Bitmap? {
            shared?.let { return it }
            return try {
                context.assets.open("mercerie/mascot.webp").use { BitmapFactory.decodeStream(it) }.also { shared = it }
            } catch (_: Exception) { null }
        }
    }
}
