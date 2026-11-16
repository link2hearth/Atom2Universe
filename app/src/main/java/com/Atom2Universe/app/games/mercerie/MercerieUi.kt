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

    /**
     * Les décors qui bougent sans cesse (mascotte qui respire, titre qui se balance, niveau en cours
     * qui bat) se redessinent 30 fois par seconde, pas à chaque image de l'écran (120 Hz) : la
     * tablette ne chauffe pas pour un hérisson qui respire.
     */
    const val IDLE_FRAME_MS = 33L

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
    private val image: Bitmap? = Felt.image(context.assets, "fond")

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
        setTextColor(if (color == Felt.PAPER) Felt.INK else Felt.CREAM)
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
    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** Chaque lettre cousue une fois pour toutes dans une image ; seule l'image se balance ensuite. */
    private var letters: List<Bitmap> = emptyList()
    private var bakedSize = 0f

    private fun bake(size: Float) {
        letters.forEach { it.recycle() }
        paint.textSize = size
        var n = 0
        val out = ArrayList<Bitmap>()
        for (line in lines) for (ch in line) if (ch != ' ') {
            val s = ch.toString()
            val w = paint.measureText(s)
            val b = Bitmap.createBitmap((w + size * .5f).toInt().coerceAtLeast(1), (size * 1.5f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.translate(b.width / 2f, size * BASE)
            paint.getTextPath(s, 0, 1, -w / 2, 0f, glyph)
            Felt.letter(c, glyph, Felt.COLORS[n % Felt.COLORS.size], size)
            out += b
            n++
        }
        letters = out
        bakedSize = size
    }

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
        if (size != bakedSize || letters.isEmpty()) bake(size)
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
                    letters.getOrNull(n)?.let { canvas.drawBitmap(it, -it.width / 2f, -size * BASE, smooth) }
                    canvas.restore()
                    n++
                }
                x += w + size * .06f
            }
        }
        postInvalidateDelayed(MercerieUi.IDLE_FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        letters.forEach { it.recycle() }
        letters = emptyList()
    }

    private companion object {
        /** Hauteur de la ligne de base dans l'image d'une lettre, en tailles de texte. */
        const val BASE = 1.1f
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
 * La bande de mascottes en feutrine (`assets/mercerie/mascot*.webp`), la même dans les deux modes.
 * Chaque niveau a la sienne, d'après son objectif ; l'accueil en tire une au hasard.
 */
enum class Mascot(val asset: String) {
    /** Pique, le hérisson : niveaux au score. */
    HEDGEHOG("mascot"),
    /** La taupe coiffée d'un dé : dés à coudre à faire descendre. */
    MOLE("mascot_mole"),
    /** La souris aux ciseaux : écussons à découdre. */
    MOUSE("mascot_mouse"),
    /** L'écureuil au panier : pièces à récolter. */
    SQUIRREL("mascot_squirrel"),
    /** Le chaton à la pelote : nœuds de laine sur le plateau. */
    CAT("mascot_cat");

    companion object {
        /** S'il y a plusieurs objectifs, le premier de la liste choisit. */
        fun of(level: MercerieLevel): Mascot {
            val g = level.goal
            return when {
                g.thimbles > 0 -> MOLE
                g.patches -> MOUSE
                g.collect.any { it > 0 } -> SQUIRREL
                g.dust || level.map.any { row -> row.any { it in "kKMqQde" } } -> CAT
                else -> HEDGEHOG
            }
        }

        fun random(): Mascot = entries.random()
    }
}

/**
 * Une mascotte qui vit : [mood] 0 tranquille (elle respire), 1 ravie (elle sautille), -1 dépitée
 * (elle s'affaisse, penchée).
 */
class MascotView(context: Context, mascot: Mascot = Mascot.HEDGEHOG) : View(context) {
    var mood = 0
        set(value) { field = value; invalidate() }
    private val start = System.nanoTime()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val image: Bitmap? = cached(context, mascot)

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
        // Ravie, elle sautille vite : à pleine cadence. Sinon elle respire doucement.
        if (mood > 0) postInvalidateOnAnimation() else postInvalidateDelayed(MercerieUi.IDLE_FRAME_MS)
    }

    private companion object {
        val shared = HashMap<Mascot, Bitmap>()

        fun cached(context: Context, mascot: Mascot): Bitmap? {
            shared[mascot]?.let { return it }
            return try {
                context.assets.open("mercerie/${mascot.asset}.webp").use { BitmapFactory.decodeStream(it) }.also { shared[mascot] = it }
            } catch (_: Exception) { null }
        }
    }
}

/**
 * La bascule jour / nuit de l'accueil : un rail de feutrine, un soleil à gauche, une lune à droite.
 * Le pion glisse, puis [onChange] reçoit le nouvel état.
 */
@SuppressLint("ViewConstructor")
class FeltSwitch(context: Context, private var on: Boolean, private val onChange: (Boolean) -> Unit) : View(context) {
    private val unit = MercerieUi.unit(context)
    private val track = Path()
    private val knob = Path()
    private val bite = Path()
    /** Position du pion, de 0 (soleil) à 1 (lune). */
    private var pos = if (on) 1f else 0f

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            on = !on
            animate().cancel()
            val from = pos
            android.animation.ValueAnimator.ofFloat(from, if (on) 1f else 0f).apply {
                duration = 200
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = onChange(on)
                })
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MercerieUi.dp(context, 44f).toInt()
        setMeasuredDimension((h * 1.9f).toInt(), h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val pad = h * .08f
        val day = 0xFFBFE3F2.toInt()
        val night = 0xFF2C3566.toInt()
        val mixed = android.animation.ArgbEvaluator().evaluate(pos, day, night) as Int
        track.rewind()
        track.addRoundRect(RectF(pad, pad, w - pad, h - pad), h / 2, h / 2, Path.Direction.CW)
        Felt.shape(canvas, track, mixed, unit * .7f, stitch = false, plush = false)
        val r = (h - pad * 2) * .4f
        val cx = pad + r * 1.25f + (w - 2 * pad - r * 2.5f) * pos
        val cy = h / 2
        knob.rewind()
        knob.addCircle(cx, cy, r, Path.Direction.CW)
        if (pos > .5f) {
            // La lune : le disque mordu par un autre.
            bite.rewind()
            bite.addCircle(cx + r * .55f, cy - r * .35f, r * .82f, Path.Direction.CW)
            knob.op(bite, Path.Op.DIFFERENCE)
            Felt.shape(canvas, knob, 0xFFF3E9C8.toInt(), r * 2, stitch = false)
        } else {
            Felt.shape(canvas, knob, 0xFFF7C948.toInt(), r * 2, threadColor = 0xFFFFF3C4.toInt())
        }
    }
}

/**
 * Le haut-parleur du bouton de son, dessiné à l'encre du thème : deux ondes quand le son est mis,
 * une petite croix quand il est coupé.
 */
class SpeakerDrawable(context: Context, on: Boolean) : Drawable() {
    var on = on
        set(value) { field = value; invalidateSelf() }
    private val side = MercerieUi.dp(context, 24f).toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val body = Path()
    private val arc = RectF()

    override fun getIntrinsicWidth() = side
    override fun getIntrinsicHeight() = side

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x = b.exactCenterX() - s / 2
        // Le panneau de feutrine est un peu plus épais en bas : on remonte l'icône d'autant.
        val y = b.exactCenterY() - s / 2 - s * .06f
        fill.color = Felt.INK
        stroke.color = Felt.INK
        stroke.strokeWidth = s * .09f
        body.rewind()
        body.moveTo(x + s * .1f, y + s * .37f)
        body.lineTo(x + s * .3f, y + s * .37f)
        body.lineTo(x + s * .54f, y + s * .14f)
        body.lineTo(x + s * .54f, y + s * .86f)
        body.lineTo(x + s * .3f, y + s * .63f)
        body.lineTo(x + s * .1f, y + s * .63f)
        body.close()
        canvas.drawPath(body, fill)
        if (on) {
            for (r in floatArrayOf(.17f, .33f)) {
                arc.set(x + s * (.56f - r), y + s * (.5f - r), x + s * (.56f + r), y + s * (.5f + r))
                canvas.drawArc(arc, -42f, 84f, false, stroke)
            }
        } else {
            val cx = x + s * .8f
            val cy = y + s * .5f
            val h = s * .13f
            canvas.drawLine(cx - h, cy - h, cx + h, cy + h, stroke)
            canvas.drawLine(cx + h, cy - h, cx - h, cy + h, stroke)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
