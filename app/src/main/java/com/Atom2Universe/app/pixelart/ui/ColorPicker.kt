package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.PixelColor

/** Une pastille de couleur (avec damier dessous si elle est transparente). */
class SwatchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var color = 0
        set(v) { field = v; invalidate() }
    var ring = false
        set(v) { field = v; invalidate() }
    var radiusDp = 8f

    private val checker = context.checkerDrawable(context.dp(5))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val clip = android.graphics.Path()

    override fun onDraw(c: Canvas) {
        val r = context.dpf(radiusDp)
        val inset = context.dpf(1f)
        rect.set(inset, inset, width - inset, height - inset)
        clip.reset()
        clip.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        if (color ushr 24 != 255) {
            checker.setBounds(0, 0, width, height)
            checker.draw(c)
        }
        paint.color = color
        c.drawRect(rect, paint)
        c.restore()
        stroke.strokeWidth = context.dpf(if (ring) 2.5f else 1f)
        stroke.color = if (ring) context.accentColor() else 0x55FFFFFF
        c.drawRoundRect(rect, r, r, stroke)
    }
}

/** Carré saturation × luminosité pour une teinte donnée. */
internal class SvSquare(context: Context, val onChange: (Float, Float) -> Unit) : View(context) {
    var hue = 0f
        set(v) { field = v; rebuild(); invalidate() }
    var sat = 1f
    var value = 1f
    /** Appelé quand le doigt se lève. */
    var onEnd: () -> Unit = {}
    private val paint = Paint()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dpf(2f); color = Color.WHITE }
    private val ringShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dpf(4f); color = 0x88000000.toInt() }

    private fun rebuild() {
        if (width == 0) return
        val h = PixelColor.fromHsv(hue, 1f, 1f)
        val sx = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, h, Shader.TileMode.CLAMP)
        val sy = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(sx, sy, PorterDuff.Mode.SRC_OVER)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

    override fun onDraw(c: Canvas) {
        val r = context.dpf(10f)
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        val x = sat * width
        val y = (1f - value) * height
        c.drawCircle(x, y, context.dpf(9f), ringShadow)
        c.drawCircle(x, y, context.dpf(9f), ring)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        sat = (e.x / width).coerceIn(0f, 1f)
        value = 1f - (e.y / height).coerceIn(0f, 1f)
        invalidate()
        onChange(sat, value)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) onEnd()
        return true
    }
}

/**
 * Barre à dégradé : on règle une valeur de 0 à 1 le long d'un dégradé de couleurs (avec damier dessous si besoin).
 * Horizontale (0 à gauche) ou [vertical]e (0 en haut).
 */
internal class GradientBar(context: Context, val vertical: Boolean = false, val onChange: (Float) -> Unit) : View(context) {
    var fraction = 0f
        set(v) { field = v; invalidate() }
    /** Appelé quand le doigt se lève. */
    var onEnd: () -> Unit = {}
    private var stops = intArrayOf(Color.BLACK, Color.WHITE)
    private var showChecker = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dpf(2.5f); color = Color.WHITE }
    private val thumbShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dpf(4.5f); color = 0x88000000.toInt() }
    private val checker = context.checkerDrawable(context.dp(5))
    private val clip = android.graphics.Path()

    fun setStops(colors: IntArray, withChecker: Boolean = false) {
        stops = colors
        showChecker = withChecker
        rebuild()
        invalidate()
    }

    private fun rebuild() {
        if (width == 0 || height == 0) return
        paint.shader = if (vertical) LinearGradient(0f, 0f, 0f, height.toFloat(), stops, null, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, width.toFloat(), 0f, stops, null, Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

    /** Rayon des bouts arrondis, et de la pastille qui marque la valeur. */
    private val radius get() = (if (vertical) width else height) / 2f

    override fun onDraw(c: Canvas) {
        val r = radius
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, android.graphics.Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        if (showChecker) { checker.setBounds(0, 0, width, height); checker.draw(c) }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        c.restore()
        val along = r + fraction * ((if (vertical) height else width) - 2 * r)
        val cx = if (vertical) width / 2f else along
        val cy = if (vertical) along else height / 2f
        c.drawCircle(cx, cy, r - context.dpf(2f), thumbShadow)
        c.drawCircle(cx, cy, r - context.dpf(2f), thumb)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val r = radius
        val pos = if (vertical) e.y else e.x
        val len = if (vertical) height else width
        fraction = ((pos - r) / (len - 2 * r)).coerceIn(0f, 1f)
        onChange(fraction)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) onEnd()
        return true
    }
}

/** Les sept couleurs pleines de la roue des teintes, pour dégrader une barre de teinte. */
internal val HUE_STOPS: IntArray = IntArray(7) { PixelColor.fromHsv(it * 60f, 1f, 1f) }

/**
 * Sélecteur de couleur : carré saturation/luminosité, teinte, transparence, code hexadécimal,
 * couleurs récentes et couleurs de la palette courante.
 */
fun Context.showColorPicker(
    initial: Int,
    recents: List<Int>,
    palette: List<Int>,
    onPicked: (Int) -> Unit,
) {
    var hue = PixelColor.toHsv(initial)[0]
    var sat = PixelColor.toHsv(initial)[1]
    var value = PixelColor.toHsv(initial)[2]
    var alpha = PixelColor.alpha(initial)
    fun current() = PixelColor.fromHsv(hue, sat, value, alpha)

    lateinit var square: SvSquare
    lateinit var hueBar: GradientBar
    lateinit var alphaBar: GradientBar
    lateinit var hexField: EditText
    lateinit var preview: SwatchView
    var updatingHex = false

    fun refresh(fromHex: Boolean = false) {
        val c = current()
        square.hue = hue; square.sat = sat; square.value = value; square.invalidate()
        hueBar.fraction = hue / 360f
        val base = PixelColor.fromHsv(hue, sat, value)
        alphaBar.setStops(intArrayOf(base and 0x00FFFFFF, base or (0xFF shl 24)), true)
        alphaBar.fraction = alpha / 255f
        preview.color = c
        if (!fromHex) {
            updatingHex = true
            hexField.setText(PixelColor.toHex(c).removePrefix("#"))
            updatingHex = false
        }
    }

    fun setColor(c: Int) {
        val hsv = PixelColor.toHsv(c)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]; alpha = PixelColor.alpha(c)
        refresh()
    }

    val dialog = bottomSheet(getString(R.string.px_color_title)) { root, dlg ->
        square = SvSquare(this) { s, v -> sat = s; value = v; refresh() }
        root.addView(square, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)))

        hueBar = GradientBar(this) { f -> hue = f * 359.99f; refresh() }.also { it.setStops(HUE_STOPS) }
        root.addView(hueBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { topMargin = dp(14) })

        alphaBar = GradientBar(this) { a -> alpha = (a * 255f + 0.5f).toInt(); refresh() }
        root.addView(alphaBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { topMargin = dp(10) })

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        preview = SwatchView(this).apply { radiusDp = 10f }
        row.addView(preview, LinearLayout.LayoutParams(dp(56), dp(44)))
        row.addView(label("#", 16f, true).apply { setPadding(dp(16), 0, dp(4), 0) })
        hexField = EditText(this).apply {
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(8), InputFilter.AllCaps())
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
            typeface = android.graphics.Typeface.MONOSPACE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updatingHex) return
                    val parsed = PixelColor.parseHex(s.toString()) ?: return
                    val hsv = PixelColor.toHsv(parsed)
                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]; alpha = PixelColor.alpha(parsed)
                    refresh(fromHex = true)
                }
            })
        }
        row.addView(hexField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        fun strip(title: Int, colors: List<Int>) {
            if (colors.isEmpty()) return
            root.addView(label(getString(title), 12f).apply { setPadding(dp(2), dp(14), 0, dp(6)) })
            val line = LinearLayout(this)
            for (c in colors) {
                line.addView(SwatchView(this).apply {
                    color = c
                    setOnClickListener { setColor(c) }
                }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(6) })
            }
            root.addView(scrollRow(line))
        }
        strip(R.string.px_color_recent, recents)
        strip(R.string.px_color_palette, palette)

        root.addView(primaryButton(getString(R.string.px_ok)) { dlg.dismiss(); onPicked(current()) })
        refresh()
    }
    dialog.show()
}
