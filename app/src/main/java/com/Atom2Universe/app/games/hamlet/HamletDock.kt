package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.sin

/** The game's modules, reached from the shared banner at the bottom of the screen. */
enum class HamletModule(val glyph: String, val label: Int) {
    VILLAGE("house", R.string.hamlet_module_village),
    ADVENTURE("sword", R.string.hamlet_module_adventure),
    HEROES("hat", R.string.hamlet_module_heroes),
    JOURNAL("book", R.string.hamlet_module_journal),
    NEIGHBORS("signpost", R.string.hamlet_module_neighbors),
}

/** What a module page asks of the game screen around it. */
internal interface HamletNav {
    val haptics: Boolean
    val effects: Boolean
    /** Opens a module, at one of its pages when [page] is not negative. */
    fun open(module: HamletModule, page: Int = -1)
    /** Back to the village to place a building. */
    fun build(def: Def)
    /** The supplies shown in the top bar while this module is open. */
    fun supplies(chips: List<HamletChip>)
    /** Something changed in the game: badges are refreshed and the save follows. */
    fun changed()
}

/** A full-screen module page hosted by the game screen. */
internal interface HamletPage {
    val view: View
    /** Shown again, at [page] when it is not negative. */
    fun show(page: Int)
    /** Hidden behind another module: timers and animations rest. */
    fun hide()
    /** Once a second while shown. */
    fun tick() = Unit
    /** An inner step back; false lets the game return to the village. */
    fun back(): Boolean = false
    /** Released for good. */
    fun close() = Unit
}

/** One supply in the top bar. */
class HamletChip(val glyph: String, val value: Long, val text: String, val description: String)

/** The shared banner: one entry per module, the open one raised in gold, a red badge where something waits. */
internal class HamletDock(context: Context, private val choose: (HamletModule) -> Unit) : LinearLayout(context) {
    private val items = HamletModule.entries.map { Item(context, it) }
    private val paint = Paint()
    private val line = Paint().apply { color = 0xFFCFA859.toInt() }
    private var shade: LinearGradient? = null
    var current = HamletModule.VILLAGE
        private set

    init {
        setWillNotDraw(false)
        val side = HamletUi.dp(context, 4)
        setPadding(side, HamletUi.dp(context, 5), side, HamletUi.dp(context, 3))
        for (item in items) addView(item, LayoutParams(0, HamletUi.dp(context, 66), 1f))
        items[0].chosen = true
    }
    fun select(module: HamletModule) {
        current = module
        for (item in items) item.chosen = item.module == module
    }
    /** [count] above zero shows a number, -1 a plain dot, 0 nothing. */
    fun badge(module: HamletModule, count: Int) { items[module.ordinal].badge = count }
    fun item(module: HamletModule): View = items[module.ordinal]
    /** A module that is not earned yet stays out of the banner; it pops in the moment it opens. */
    fun available(module: HamletModule, on: Boolean) {
        val view = items[module.ordinal]
        val was = view.visibility == View.VISIBLE
        view.visibility = if (on) View.VISIBLE else View.GONE
        if (on && !was) HamletMotion.pop(view, 1.3f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shade = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1B3833.toInt(), 0xFF0B2320.toInt(), Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        paint.shader = shade; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawRect(0f, 0f, width.toFloat(), HamletUi.dp(context, 2).toFloat(), line)
    }

    private inner class Item(context: Context, val module: HamletModule) : View(context) {
        private val label = context.getString(module.label)
        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        private val density = resources.displayMetrics.density
        private var labelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
        private var chosenAt = 0L
        private val offset = module.ordinal * 520L
        var chosen = false
            set(value) {
                if (field == value) return
                field = value; isSelected = value
                if (value) chosenAt = SystemClock.uptimeMillis()
                invalidate()
            }
        var badge = 0
            set(value) {
                if (field == value) return
                field = value
                contentDescription = if (value != 0) context.getString(R.string.hamlet_ui_waiting, label) else label
                invalidate()
            }

        init {
            contentDescription = label; isClickable = true; isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), null,
                GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = 14 * density })
            setOnClickListener { choose(module) }
        }
        private fun d(v: Float) = v * density

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            labelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
            text.textSize = labelSize; text.typeface = Typeface.DEFAULT_BOLD
            val room = w - d(6f)
            val needed = text.measureText(label)
            if (needed > room) labelSize *= room / needed
        }
        override fun onDraw(c: Canvas) {
            val now = SystemClock.uptimeMillis()
            val motion = HamletMotion.on
            val cx = width / 2f
            if (chosen) {
                box.set(d(3f), d(1f), width - d(3f), height - d(2f))
                fill.shader = null; fill.color = 0xFFD9962F.toInt(); c.drawRoundRect(box, d(14f), d(14f), fill)
                box.inset(d(1.5f), d(1.5f)); box.bottom -= d(2f)
                fill.color = 0xFFF2C25A.toInt(); c.drawRoundRect(box, d(13f), d(13f), fill)
                box.bottom = box.top + box.height() * .42f
                fill.color = 0x38FFFFFF; c.drawRoundRect(box, d(12f), d(12f), fill)
            }
            // A freshly chosen module jumps once; one that waits for the player wiggles now and then.
            val popAge = (now - chosenAt) / 420f
            val pop = if (chosen && motion && popAge < 1f) 1f + .28f * sin(popAge * PI.toFloat()) * (1 - popAge) else 1f
            val cycle = (now + offset) % 2800L
            val burst = motion && badge != 0 && cycle < 900L
            val wiggle = if (burst) sin(cycle / 900f * PI.toFloat() * 4) * 11f * (1 - cycle / 900f) else 0f
            val size = d(if (chosen) 31f else 27f) * pop
            val iconY = d(25f)
            c.save(); c.rotate(wiggle, cx, iconY + size / 2)
            HamletGlyphs.draw(c, module.glyph, cx, iconY, size)
            c.restore()
            text.textSize = labelSize
            text.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.color = if (chosen) HamletUi.INK else 0xE0F6EDD9.toInt()
            c.drawText(label, cx, height - d(9f), text)
            if (badge != 0) {
                val swell = if (burst) 1f + .22f * sin(cycle / 900f * PI.toFloat() * 2).coerceAtLeast(0f) else 1f
                val r = d(if (badge > 0) 9f else 6f) * swell
                val bx = cx + d(15f); val by = d(10f)
                fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(bx, by, r + d(1.5f), fill)
                fill.color = HamletUi.ALERT; c.drawCircle(bx, by, r, fill)
                if (badge > 0) {
                    text.textSize = d(11f) * swell; text.typeface = Typeface.DEFAULT_BOLD; text.color = 0xFFFFFFFF.toInt()
                    val value = if (badge > 9) context.getString(R.string.hamlet_ui_many, 9) else context.getString(R.string.hamlet_ui_number, badge)
                    c.drawText(value, bx, by - (text.ascent() + text.descent()) / 2, text)
                }
            }
            if (!motion) return
            when {
                chosen && popAge < 1f -> postInvalidateOnAnimation()
                burst -> postInvalidateOnAnimation()
                badge != 0 -> postInvalidateDelayed(2800L - cycle)
            }
        }
    }
}

/** The shared top of the screen: the open module's emblem and name, its supplies, and two quiet round buttons. */
internal class HamletTopBar(context: Context, onMenu: () -> Unit, onExit: () -> Unit) : LinearLayout(context) {
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val emblem = Emblem(context)
    private val chips = LinearLayout(context)
    private val shown = ArrayList<Pair<TextView, HamletChip>>()
    private val menuDot = View(context)
    private fun dp(v: Int) = HamletUi.dp(context, v)

    init {
        orientation = VERTICAL
        setPadding(dp(10), dp(6), dp(8), dp(4))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF17332F.toInt(), HamletUi.FOREST))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(emblem, LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        val heading = LinearLayout(context).apply { orientation = VERTICAL }
        title.apply { textSize = 20f; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        subtitle.apply { textSize = 11.5f; setTextColor(HamletUi.MUTED_LIGHT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        heading.addView(title); heading.addView(subtitle)
        row.addView(heading, LayoutParams(0, -2, 1f))
        val menu = FrameLayout(context)
        menu.addView(HamletUi.round(context, "gear", context.getString(R.string.hamlet_ui_menu), 44) { onMenu() }, FrameLayout.LayoutParams(dp(44), dp(44)))
        menuDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(HamletUi.ALERT); setStroke(dp(2), 0xFFFFFFFF.toInt()) }
        menuDot.visibility = GONE; menuDot.elevation = dp(8).toFloat()
        menu.addView(menuDot, FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END))
        row.addView(menu, LayoutParams(dp(46), dp(46)).apply { marginStart = dp(4) })
        row.addView(HamletUi.round(context, "door", context.getString(R.string.hamlet_ui_exit_description), 44) { onExit() },
            LayoutParams(dp(46), dp(46)).apply { marginStart = dp(4) })
        addView(row)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; isFillViewport = true
            addView(chips); contentDescription = context.getString(R.string.hamlet_ui_resources)
        }, LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }

    fun module(module: HamletModule, name: String) {
        if (emblem.module != module) { emblem.module = module; emblem.invalidate(); HamletMotion.pop(emblem, 1.2f) }
        title.text = name
    }
    fun subtitle(value: CharSequence) { subtitle.text = value; subtitle.visibility = if (value.isEmpty()) GONE else VISIBLE }
    fun menuBadge(on: Boolean) { menuDot.visibility = if (on) VISIBLE else GONE }

    /** Same supplies as before: only the numbers change, and the ones that moved swell. */
    fun supplies(list: List<HamletChip>) {
        if (shown.size != list.size || shown.indices.any { shown[it].second.glyph != list[it].glyph }) {
            chips.removeAllViews(); shown.clear()
            for (chip in list) {
                val view = TextView(context).apply {
                    textSize = 13f; gravity = Gravity.CENTER_VERTICAL; setTextColor(HamletUi.PAPER); setTypeface(null, Typeface.BOLD)
                    setPadding(dp(7), dp(4), dp(10), dp(4)); isSingleLine = true
                    background = HamletUi.panel(context, 0xFF2C4D44.toInt(), 16, 0x40F6EDD9)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(context, chip.glyph, 19), null, null, null)
                    compoundDrawablePadding = dp(5)
                    setOnClickListener { it.performLongClick() }
                }
                val params = if (list.size <= 4) LayoutParams(0, dp(34), 1f) else LayoutParams(-2, dp(34))
                chips.addView(view, params.apply { marginEnd = dp(5) })
                shown.add(view to chip)
            }
            for (i in list.indices) fill(i, list[i], false)
            return
        }
        for (i in list.indices) if (shown[i].second.value != list[i].value || shown[i].second.text != list[i].text) fill(i, list[i], true)
    }
    private fun fill(i: Int, chip: HamletChip, moved: Boolean) {
        val view = shown[i].first
        val before = shown[i].second.value
        view.text = chip.text; view.contentDescription = chip.description; view.tooltipText = chip.description
        shown[i] = view to chip
        if (moved && chip.value != before) {
            HamletMotion.pop(view, 1.14f)
            if (HamletMotion.on) {
                view.setTextColor(if (chip.value > before) 0xFFB8F0A0.toInt() else 0xFFFFB4A6.toInt())
                view.postDelayed({ view.setTextColor(HamletUi.PAPER) }, 650)
            }
        }
    }

    /** The open module's picture in a gold ring. */
    private class Emblem(context: Context) : View(context) {
        var module = HamletModule.VILLAGE
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = minOf(width, height) / 2f; val cx = width / 2f; val cy = height / 2f
            paint.color = 0xFFD9962F.toInt(); c.drawCircle(cx, cy, r, paint)
            paint.color = 0xFFF2C25A.toInt(); c.drawCircle(cx, cy, r * .9f, paint)
            paint.color = HamletUi.DEEP; c.drawCircle(cx, cy, r * .78f, paint)
            HamletGlyphs.draw(c, module.glyph, cx, cy, r * 1.15f)
        }
    }
}
