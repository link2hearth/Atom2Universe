package com.Atom2Universe.app.games.othello

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import kotlin.math.ceil
import kotlin.math.min

/**
 * Le score de Reversi : deux plaquettes, chacune un pion et son nombre de pions sur le plateau.
 *
 * La plaquette du joueur au trait est cerclée de la couleur du thème (le cercle respire pendant
 * que l'IA réfléchit), l'autre s'estompe. En fin de partie, celle du vainqueur garde son cercle et
 * prend une teinte. Contre l'IA, la plaquette des noirs porte la tête de robot du bouton de mode.
 * Debout, les plaquettes sont côte à côte ; couché ([vertical]), l'une sous l'autre.
 */
class OthelloScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var vertical = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var solo = true
        set(value) { field = value; invalidate() }

    /** Le joueur au trait. */
    var current = OthelloGame.WHITE
        set(value) { field = value; invalidate() }

    /** Le vainqueur une fois la partie finie (EMPTY pour une égalité), null pendant la partie. */
    var winner: Int? = null
        set(value) { field = value; invalidate() }

    /** L'IA réfléchit : le cercle de sa plaquette respire. */
    var thinking = false
        set(value) {
            if (field == value) return
            field = value
            if (value && isAttachedToWindow) pulse.start() else pulse.cancel()
            invalidate()
        }

    private var black = 2
    private var white = 2

    fun setCounts(black: Int, white: Int) {
        if (black == this.black && white == this.white) return
        this.black = black
        this.white = white
        contentDescription = context.getString(R.string.othello_score, black, white)
        invalidate()
    }

    /** Plus grand au-delà de 600 dp. */
    private val k = if (resources.configuration.smallestScreenWidthDp >= 600) 1.3f else 1f
    private val dp = resources.displayMetrics.density * k
    private val chipH = 46 * dp
    private val discR = 12.5f * dp
    private val padStart = 12 * dp
    private val gapText = 10 * dp
    private val iconSize = 18 * dp
    private val gapIcon = 6 * dp
    private val padEnd = 14 * dp
    private val chipGap = 12 * dp
    private val ring = 2 * dp

    private val raised = AppearanceStyle.color(context, R.attr.a2uRaisedColor)
    private val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
    private val outline = AppearanceStyle.color(context, R.attr.a2uOutlineColor)
    private val corner = min(AppearanceStyle.corner(context, 16f) * k, chipH / 2f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textColor = AppearanceStyle.color(context, R.attr.a2uTextColor)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f * k, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
        color = textColor
    }
    private val digits = number.measureText("64")
    private val robot = AppCompatResources.getDrawable(context, R.drawable.othello_ic_vs_ai)?.mutate()?.apply {
        setTint(AppearanceStyle.color(context, R.attr.a2uSecondaryTextColor))
    }
    private var blackDisc: Bitmap? = null
    private var whiteDisc: Bitmap? = null
    private val rect = RectF()

    private var breath = 1f
    private val pulse = ValueAnimator.ofFloat(0.35f, 1f).apply {
        duration = 700
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { breath = it.animatedValue as Float; invalidate() }
    }

    /** La largeur d'une plaquette, en pixels. */
    val chipWidth: Int
        get() = ceil(padStart + 2 * discR + gapText + digits + gapIcon + iconSize + padEnd + ring).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cw = chipWidth
        val ch = ceil(chipH + ring).toInt()
        val gap = chipGap.toInt()
        val w = (if (vertical) cw else 2 * cw + gap) + paddingLeft + paddingRight
        val h = (if (vertical) 2 * ch + gap else ch) + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (thinking && !pulse.isStarted) pulse.start()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cw = chipWidth - ring
        val total = if (vertical) 2 * chipH + chipGap else 2 * cw + chipGap
        val left0 = paddingLeft + (width - paddingLeft - paddingRight - (if (vertical) cw else total)) / 2f
        val top0 = paddingTop + (height - paddingTop - paddingBottom - (if (vertical) total else chipH)) / 2f
        for (idx in 0..1) {
            val color = if (idx == 0) OthelloGame.BLACK else OthelloGame.WHITE
            val x = if (vertical) left0 else left0 + idx * (cw + chipGap)
            val y = if (vertical) top0 + idx * (chipH + chipGap) else top0
            rect.set(x, y, x + cw, y + chipH)
            drawChip(canvas, color, if (color == OthelloGame.BLACK) black else white)
        }
    }

    private fun drawChip(canvas: Canvas, color: Int, count: Int) {
        val over = winner != null
        val active = !over && current == color
        val won = over && winner == color
        val faded = (!over && !active) || (over && winner != color && winner != OthelloGame.EMPTY)

        fill.color = if (won) ColorUtils.blendARGB(raised, accent, 0.22f) else raised
        canvas.drawRoundRect(rect, corner, corner, fill)
        if (active || won) {
            stroke.strokeWidth = ring
            stroke.color = accent
            if (active && thinking) stroke.alpha = (255 * breath).toInt()
            val e = ring / 2f
            canvas.drawRoundRect(rect.left + e, rect.top + e, rect.right - e, rect.bottom - e, corner - e, corner - e, stroke)
        } else {
            stroke.strokeWidth = resources.displayMetrics.density
            stroke.color = outline
            canvas.drawRoundRect(rect, corner, corner, stroke)
        }

        // Estompée : chaque élément prend l'alpha lui-même (ils ne se chevauchent pas, même rendu
        // qu'un calque), sans tampon hors écran à chaque image pendant que le cercle respire.
        val alpha = if (faded) FADED_ALPHA else 255
        sprite.alpha = alpha
        number.color = if (faded) ColorUtils.setAlphaComponent(textColor, Color.alpha(textColor) * FADED_ALPHA / 255) else textColor
        val showRobot = solo && color == OthelloGame.BLACK && robot != null
        val content = 2 * discR + gapText + digits + if (showRobot) gapIcon + iconSize else 0f
        var x = rect.centerX() - content / 2f
        val cy = rect.centerY()
        val disc = discBitmap(color)
        canvas.drawBitmap(disc, x + discR - disc.width / 2f, cy - disc.height / 2f, sprite)
        x += 2 * discR + gapText
        val baseline = cy - (number.ascent() + number.descent()) / 2f
        canvas.drawText(count.toString(), x + digits / 2f, baseline, number)
        x += digits
        if (showRobot) {
            x += gapIcon
            robot?.alpha = alpha
            robot?.setBounds(x.toInt(), (cy - iconSize / 2f).toInt(), (x + iconSize).toInt(), (cy + iconSize / 2f).toInt())
            robot?.draw(canvas)
        }
    }

    private companion object {
        const val FADED_ALPHA = 125
    }

    private fun discBitmap(color: Int): Bitmap =
        if (color == OthelloGame.BLACK) blackDisc ?: OthelloArt.discSprite(discR, color).also { blackDisc = it }
        else whiteDisc ?: OthelloArt.discSprite(discR, color).also { whiteDisc = it }
}
