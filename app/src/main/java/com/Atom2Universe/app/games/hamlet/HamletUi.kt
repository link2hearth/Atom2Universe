package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import com.Atom2Universe.app.R

/**
 * Shared native controls: illustrated glyphs, warm parchment cards and three button weights.
 * Main buttons are the actions played all the time, quiet ones the rare ones. Nothing here reads
 * the app's colour theme, so every text keeps its contrast whatever theme the player chose.
 */
internal object HamletUi {
    const val FOREST = 0xFF213D38.toInt()
    const val DEEP = 0xFF102D2A.toInt()
    const val INK = 0xFF293D37.toInt()
    const val PAPER = 0xFFF6EDD9.toInt()
    const val GOLD = 0xFFEAC779.toInt()
    /** Secondary text on parchment. */
    const val MUTED = 0xFF66706A.toInt()
    /** Secondary text on the dark forest panels. */
    const val MUTED_LIGHT = 0xFFC3CFC0.toInt()
    const val GOOD = 0xFF2F7D45.toInt()
    const val BAD = 0xFFB2453A.toInt()
    /** Red of the badges that call for a tap. */
    const val ALERT = 0xFFD8402F.toInt()

    /** Text sizes in sp, before the game's larger font scale. */
    const val SMALL = 12.5f
    const val TEXT = 14f
    const val TITLE = 17f
    const val HEAD = 21f

    enum class Weight { MAIN, NORMAL, QUIET }

    /** The game reads a little larger everywhere, and clearly larger on tablets. */
    fun larger(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.fontScale *= if (config.smallestScreenWidthDp >= 600) 1.25f else 1.1f
        return base.createConfigurationContext(config)
    }

    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun panel(context: Context, color: Int, radius: Int = 14, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
        if (stroke != 0) setStroke(dp(context, 1).coerceAtLeast(1), stroke)
    }
    /** The parchment card every window is made of. */
    fun card(context: Context, color: Int = PAPER) = panel(context, color, 14, 0xFFD4C5A9.toInt())
    fun control(context: Context, selected: Boolean = false, quiet: Boolean = false): Drawable {
        val normal = when { selected -> GOLD; quiet -> 0xFF294942.toInt(); else -> 0xFF376250.toInt() }
        return states(context, panel(context, normal, 10, if (selected) 0xFFFFDEA0.toInt() else 0xFF527867.toInt()))
    }
    private fun states(context: Context, normal: Drawable, ripple: Int = 0x32FFFFFF): Drawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), panel(context, 0xFFD5D9CE.toInt(), 10))
            addState(intArrayOf(), normal)
        }
        return RippleDrawable(ColorStateList.valueOf(ripple), states, null)
    }
    private fun main(context: Context) = states(context, GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFFF2C25A.toInt(), 0xFFD9962F.toInt())).apply {
        cornerRadius = dp(context, 12).toFloat(); setStroke(dp(context, 1).coerceAtLeast(1), 0xFFFFE3A0.toInt())
    }, 0x40FFFFFF)
    private fun quiet(context: Context, onDark: Boolean): Drawable {
        val outline = GradientDrawable().apply {
            setColor(if (onDark) 0x14FFFFFF else 0x10293D37); cornerRadius = dp(context, 10).toFloat()
            setStroke(dp(context, 1).coerceAtLeast(1), if (onDark) 0x55F6EDD9 else 0xFFCBBB97.toInt())
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), GradientDrawable().apply { cornerRadius = dp(context, 10).toFloat() })
            addState(intArrayOf(), outline)
        }
        return RippleDrawable(ColorStateList.valueOf(if (onDark) 0x32FFFFFF else 0x22293D37), states, null)
    }

    /**
     * Puts [drawable] behind [view] in the view's current state. Our buttons drop the theme's tint,
     * so Android copies every new background; on a button already on screen that copy would keep
     * its "disabled" face until the next press.
     */
    fun face(view: View, drawable: Drawable) {
        view.background = drawable
        view.background?.let { it.state = intArrayOf(); it.state = view.drawableState }
    }
    /** [onDark] only matters for quiet buttons, drawn as an outline over their background; [glyph] puts a small picture before the label. */
    fun button(context: Context, label: CharSequence, weight: Weight = Weight.NORMAL, onDark: Boolean = false,
               glyph: String? = null, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false
        minWidth = 0; minimumWidth = 0
        gravity = Gravity.CENTER; backgroundTintList = null
        elevation = 0f; stateListAnimator = null
        style(this, weight, onDark)
        if (glyph != null) glyph(this, glyph, weight)
        HamletMotion.press(this)
        setOnClickListener { click() }
    }
    /** Gives an existing button another weight. */
    fun style(button: Button, weight: Weight, onDark: Boolean = false) = button.apply {
        val context = button.context
        val height = when (weight) { Weight.MAIN -> 58; Weight.NORMAL -> 48; Weight.QUIET -> 40 }
        minHeight = dp(context, height); minimumHeight = dp(context, height)
        textSize = when (weight) { Weight.MAIN -> 16.5f; Weight.NORMAL -> TEXT; Weight.QUIET -> SMALL }
        setTypeface(null, if (weight == Weight.MAIN) Typeface.BOLD else Typeface.NORMAL)
        val side = if (weight == Weight.QUIET) 10 else 12
        setPadding(dp(context, side), dp(context, 4), dp(context, side), dp(context, 4))
        face(this, when (weight) { Weight.MAIN -> main(context); Weight.NORMAL -> control(context); Weight.QUIET -> quiet(context, onDark) })
        foreground = if (weight == Weight.MAIN) HamletMotion.Shine(dp(context, 12).toFloat()) else null
        val enabled = when (weight) { Weight.MAIN -> INK; Weight.NORMAL -> PAPER; Weight.QUIET -> if (onDark) 0xE6F6EDD9.toInt() else 0xFF41544B.toInt() }
        val disabled = if (weight == Weight.QUIET) (if (onDark) 0x66F6EDD9 else 0x66293D37) else 0xFF6C786C.toInt()
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabled, enabled)))
    }
    /** A red dot after a tab's label: something waits there. */
    fun waiting(button: Button, on: Boolean) {
        val context = button.context
        val current = button.compoundDrawablesRelative
        val has = current[2] != null
        if (has == on) return
        val dot: Drawable? = if (on) GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(ALERT); setStroke(dp(context, 2), 0xFFFFFFFF.toInt()); setSize(dp(context, 13), dp(context, 13))
        } else null
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(current[0], current[1], dot, current[3])
    }
    /** A small picture before a button's label. */
    fun glyph(button: Button, glyph: String, weight: Weight = Weight.NORMAL) = button.apply {
        val size = when (weight) { Weight.MAIN -> 24; Weight.NORMAL -> 20; Weight.QUIET -> 17 }
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon(context, glyph, size), null, null, null)
        compoundDrawablePadding = dp(context, 6)
    }
    /** A toggle among several (filters, tactics, options): gold when chosen. */
    fun choice(button: Button, chosen: Boolean) = button.apply {
        isSelected = chosen; face(this, control(context, selected = chosen, quiet = !chosen))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(0xFF6C786C.toInt(), if (chosen) INK else PAPER)))
        setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
    }
    /** A page tab inside a module: picture above a short name, gold when open. */
    fun tab(context: Context, label: String, glyph: String, click: () -> Unit) = button(context, label, click = click).apply {
        textSize = 12.5f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 5))
        setCompoundDrawablesWithIntrinsicBounds(null, icon(context, glyph, 24), null, null)
        compoundDrawablePadding = dp(context, 2)
        minHeight = dp(context, 60); minimumHeight = dp(context, 60)
        choice(this, false)
    }
    /** A line of a list on parchment: light, gold when it is the current one. Coloured parts of [label] stay readable. */
    fun row(context: Context, label: CharSequence, chosen: Boolean = false, glyph: String? = null, click: () -> Unit) =
        button(context, label, glyph = glyph, click = click).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            face(this, rowFace(context, chosen))
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(0xFF8A928A.toInt(), INK)))
            setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
            setPadding(dp(context, 14), dp(context, 6), dp(context, 12), dp(context, 6))
        }
    /** The face of a list line: light parchment, gold when chosen. */
    fun rowFace(context: Context, chosen: Boolean): Drawable = RippleDrawable(ColorStateList.valueOf(0x22293D37), StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), panel(context, 0xFFE4DED0.toInt(), 10))
        addState(intArrayOf(), panel(context, if (chosen) GOLD else 0xFFFFF8E9.toInt(), 10, if (chosen) 0xFFD9A441.toInt() else 0xFFD9C9A5.toInt()))
    }, null)
    /** A round picture button for rare tools: menu, exit, recentre. [gold] for the one used all the time. */
    fun round(context: Context, glyph: String, description: String, size: Int = 46, gold: Boolean = false, click: () -> Unit) =
        ImageButton(context).apply {
            contentDescription = description; tooltipText = description
            setImageDrawable(icon(context, glyph, (size * .56f).toInt()))
            scaleType = ImageView.ScaleType.CENTER
            val face = if (gold) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFF2C25A.toInt(), 0xFFD9962F.toInt()))
                else GradientDrawable().apply { setColor(0xD9213D38.toInt()) }
            face.shape = GradientDrawable.OVAL
            face.setStroke(dp(context, if (gold) 2 else 1).coerceAtLeast(1), if (gold) 0xFFFFE3A0.toInt() else 0x66F6EDD9)
            background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), face, null)
            elevation = dp(context, if (gold) 6 else 3).toFloat()
            minimumWidth = dp(context, size); minimumHeight = dp(context, size)
            HamletMotion.press(this)
            setOnClickListener { click() }
        }
    /** A button that opens a list: the current value and a small arrow. */
    fun picker(context: Context, value: CharSequence, glyph: String? = null, click: () -> Unit) =
        button(context, android.text.TextUtils.concat(value, context.getString(R.string.hamlet_ui_picker)), glyph = glyph, click = click)
    /** A small "?" that opens a rule only for whoever asks for it. */
    fun info(context: Context, title: CharSequence, message: CharSequence, onDark: Boolean = false) =
        button(context, context.getString(R.string.hamlet_ui_info_mark), Weight.QUIET, onDark) {
            HamletSheet.message(context, title, message)
        }.apply { contentDescription = title; setTypeface(null, Typeface.BOLD) }
    /** A text field in the game's colours. */
    fun field(edit: EditText) = edit.apply {
        setTextColor(INK); setHintTextColor(0xFF7C877A.toInt()); textSize = TEXT
        background = panel(context, 0xFFFFF8E9.toInt(), 12, 0xFFCBBB97.toInt())
        setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
        minHeight = dp(context, 48)
    }
    /** A rounded progress bar; full ones turn gold. */
    fun bar(context: Context, value: Long, max: Long, color: Int = 0xFF668D51.toInt()) = HamletBar(context).apply { set(value, max, color) }

    /** Rarity colours readable on parchment ([onDark] false) or on the dark forest panels. */
    fun rarity(r: HeroRarity, onDark: Boolean = false) = if (onDark) HamletHeroFx.light(r) else HamletHeroFx.deep(r)
    /** Equipment rarities start at rare. */
    fun gear(ordinal: Int, onDark: Boolean = false) = rarity(HeroRarity.entries[(ordinal + 1).coerceIn(1, HeroRarity.entries.size - 1)], onDark)
    private val FACTIONS = intArrayOf(0xFF3A7F45.toInt(), 0xFFB07A1C.toInt(), 0xFFB4502E.toInt())
    private val FACTIONS_DARK = intArrayOf(0xFF9BD98C.toInt(), 0xFFF3D27A.toInt(), 0xFFF59A73.toInt())
    fun faction(f: HeroFaction, onDark: Boolean = false) = (if (onDark) FACTIONS_DARK else FACTIONS)[f.ordinal]

    /** [value] with each listed part coloured (and bold), searched from the left. */
    fun tint(value: CharSequence, vararg parts: Pair<String, Int>): CharSequence {
        val out = SpannableStringBuilder(value)
        var from = 0
        for ((part, color) in parts) {
            if (part.isEmpty()) continue
            val start = out.indexOf(part, from).takeIf { it >= 0 } ?: out.indexOf(part).takeIf { it >= 0 } ?: continue
            out.setSpan(ForegroundColorSpan(color), start, start + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(StyleSpan(Typeface.BOLD), start, start + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            from = start + part.length
        }
        return out
    }
    /** Pieces joined by the game's list separator, each in its own colour. */
    fun joined(context: Context, pieces: List<Pair<String, Int>>): CharSequence {
        val separator = context.getString(R.string.hamlet_list_separator)
        return tint(pieces.joinToString(separator) { it.first }, *pieces.toTypedArray())
    }

    fun icon(context: Context, glyph: String, size: Int = 20, accent: Int = 0xDDAE68): Drawable = object : Drawable() {
        private val side = dp(context, size)
        override fun getIntrinsicWidth() = side
        override fun getIntrinsicHeight() = side
        override fun draw(canvas: Canvas) {
            HamletGlyphs.draw(canvas, glyph, bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()).toFloat() * .9f, accent)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }.apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
}

/** A rounded progress bar that fills smoothly; a full bar turns gold. */
internal class HamletBar(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var shown = 0f
    private var target = 0f
    private var color = 0xFF668D51.toInt()
    private var started = 0L
    private var from = 0f
    init { minimumHeight = HamletUi.dp(context, 10) }

    fun set(value: Long, max: Long, fill: Int = color) {
        color = fill
        val next = if (max <= 0) 1f else (value.toFloat() / max).coerceIn(0f, 1f)
        if (next == target) { invalidate(); return }
        from = if (isAttachedToWindow && HamletMotion.on) shown else next
        target = next; started = android.os.SystemClock.uptimeMillis(); invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(HamletUi.dp(context, 10), heightMeasureSpec))
    override fun onDraw(c: Canvas) {
        val t = ((android.os.SystemClock.uptimeMillis() - started) / 420f).coerceIn(0f, 1f)
        shown = from + (target - from) * (1 - (1 - t) * (1 - t))
        val r = height / 2f
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = 0x2E293D37; c.drawRoundRect(box, r, r, paint)
        if (shown > 0f) {
            box.right = maxOf(height.toFloat(), width * shown)
            paint.color = if (target >= 1f) 0xFFE0A93A.toInt() else color
            c.drawRoundRect(box, r, r, paint)
            paint.color = 0x40FFFFFF; box.bottom = height * .45f; c.drawRoundRect(box, r * .6f, r * .6f, paint)
        }
        if (t < 1f) postInvalidateOnAnimation()
    }
}
