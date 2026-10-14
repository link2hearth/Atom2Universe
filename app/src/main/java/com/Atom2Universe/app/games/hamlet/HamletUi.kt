package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
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
import android.widget.Button
import com.Atom2Universe.app.R

/**
 * Shared native controls: illustrated glyphs, warm parchment cards and three button weights.
 * Main buttons are the actions played all the time, quiet ones the rare ones.
 */
internal object HamletUi {
    const val FOREST = 0xFF213D38.toInt()
    const val INK = 0xFF293D37.toInt()
    const val PAPER = 0xFFF6EDD9.toInt()
    const val GOLD = 0xFFEAC779.toInt()
    /** Secondary text on parchment. */
    const val MUTED = 0xFF66706A.toInt()
    const val GOOD = 0xFF2F7D45.toInt()
    const val BAD = 0xFFB2453A.toInt()

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

    /** [onDark] only matters for quiet buttons, drawn as an outline over their background. */
    fun button(context: Context, label: CharSequence, weight: Weight = Weight.NORMAL, onDark: Boolean = false,
               click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false
        minWidth = 0; minimumWidth = 0
        gravity = Gravity.CENTER; backgroundTintList = null
        elevation = 0f; stateListAnimator = null
        style(this, weight, onDark)
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
        background = when (weight) { Weight.MAIN -> main(context); Weight.NORMAL -> control(context); Weight.QUIET -> quiet(context, onDark) }
        val enabled = when (weight) { Weight.MAIN -> INK; Weight.NORMAL -> PAPER; Weight.QUIET -> if (onDark) 0xE6F6EDD9.toInt() else 0xFF41544B.toInt() }
        val disabled = if (weight == Weight.QUIET) (if (onDark) 0x66F6EDD9 else 0x66293D37) else 0xFF6C786C.toInt()
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabled, enabled)))
    }
    /** A toggle among several (tabs, filters, tactics): gold when chosen. */
    fun choice(button: Button, chosen: Boolean) = button.apply {
        isSelected = chosen; background = control(context, selected = chosen, quiet = !chosen)
        setTextColor(if (chosen) INK else PAPER)
    }
    fun navigation(context: Context, label: String, glyph: String, click: () -> Unit) = button(context, label, click = click).apply {
        textSize = 12f; maxLines = 2; setPadding(dp(context, 2), dp(context, 7), dp(context, 2), dp(context, 5))
        setCompoundDrawablesWithIntrinsicBounds(null, icon(context, glyph, 24), null, null)
        compoundDrawablePadding = dp(context, 3)
        background = control(context, quiet = true)
    }
    /** A small "?" that opens a rule only for whoever asks for it. */
    fun info(context: Context, title: CharSequence, message: CharSequence, onDark: Boolean = false) =
        button(context, context.getString(R.string.hamlet_ui_info_mark), Weight.QUIET, onDark) {
            AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        }.apply { contentDescription = title; setTypeface(null, Typeface.BOLD) }

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

    fun icon(context: Context, glyph: String, size: Int = 20): Drawable = object : Drawable() {
        private val side = dp(context, size)
        override fun getIntrinsicWidth() = side
        override fun getIntrinsicHeight() = side
        override fun draw(canvas: Canvas) {
            HamletGlyphs.draw(canvas, glyph, bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()).toFloat() * .9f, 0xDDAE68)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }.apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
}
