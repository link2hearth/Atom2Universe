package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.PixelColor

/**
 * Le réglage de couleur : une petite carte flottante au-dessus de la barre de couleurs, qui laisse le dessin
 * visible et cliquable autour d'elle. Trois présentations au choix :
 *  - le carré saturation/luminosité avec les barres de teinte et d'opacité à côté ;
 *  - des curseurs teinte / saturation / luminosité ;
 *  - des curseurs rouge / vert / bleu.
 * Plus le code hexadécimal, le choix de la couleur réglée (principale ou secondaire) et les couleurs récentes.
 * La palette, elle, n'est pas ici : c'est la barre de couleurs de l'écran, qui reste la seule liste.
 */
class ColorDock @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    /** Ce que la carte demande à l'écran qui l'héberge. */
    interface Host {
        fun colorOf(secondary: Boolean): Int
        /** L'utilisateur règle la couleur (curseurs, hexadécimal) : elle suit, ainsi que la case de palette liée. */
        fun editColor(secondary: Boolean, color: Int)
        /** L'utilisateur prend une couleur toute faite (récentes) : aucune case de palette n'est modifiée. */
        fun pickColor(secondary: Boolean, color: Int)
        fun recentColors(): List<Int>
        fun hideRequested()
        /** La carte se ferme : les couleurs réglées à la main rejoignent les récentes. */
        fun closed(changed: List<Int>)
    }

    private enum class Page { SQUARE, HSV, RGB }

    private var host: Host? = null

    var editingSecondary = false
        private set
    val isOpen get() = visibility == VISIBLE

    private var page = savedPage

    // Réglage en cours : la teinte, la saturation et la valeur sont gardées à part de la couleur, pour que
    // la teinte ne saute pas quand on passe par un gris ou un noir.
    private var hue = 0f
    private var sat = 0f
    private var value = 0f
    private var alphaValue = 255
    private var lastColor = 0
    private var openPrimary = 0
    private var openSecondary = 0

    private val swatchPrimary = SwatchView(context).apply { radiusDp = 7f; contentDescription = context.getString(R.string.px_color_primary) }
    private val swatchSecondary = SwatchView(context).apply { radiusDp = 7f; contentDescription = context.getString(R.string.px_color_secondary) }
    private val hexField = EditText(context)
    private lateinit var chipSquare: TextView
    private lateinit var chipHsv: TextView
    private lateinit var chipRgb: TextView
    private lateinit var squarePage: View
    private lateinit var slidersPage: View
    private lateinit var square: SvSquare
    private lateinit var hueBar: GradientBar
    private lateinit var alphaBar: GradientBar
    private val bars = ArrayList<GradientBar>()
    private val barLabels = ArrayList<TextView>()
    private val barValues = ArrayList<TextView>()
    private lateinit var recentsRow: HorizontalScrollViewHolder
    private var settingHex = false

    /** Les récentes : une seule ligne défilante, cachée tant qu'il n'y en a pas. */
    private class HorizontalScrollViewHolder(val scroll: android.widget.HorizontalScrollView, val line: LinearLayout)

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_px_panel)
        elevation = com.Atom2Universe.app.AppearanceStyle.elevation(context, 12f)
        setPadding(dp(12), dp(8), dp(12), dp(10))
        // Les touches sur la carte ne doivent pas traverser jusqu'au dessin.
        isClickable = true
        buildHeader()
        buildBody()
        buildRecents()
    }

    fun bind(host: Host) { this.host = host }

    // ==== Ouvrir / fermer ====================================================================

    fun show(secondary: Boolean) {
        val h = host ?: return
        editingSecondary = secondary
        if (!isOpen) {
            visibility = VISIBLE
            openPrimary = h.colorOf(false)
            openSecondary = h.colorOf(true)
            buildRecentsContent()
        }
        refresh()
    }

    fun hide() {
        val h = host ?: return
        if (!isOpen) return
        visibility = GONE
        hexField.clearFocus()
        val changed = ArrayList<Int>()
        val s = h.colorOf(true)
        val p = h.colorOf(false)
        if (s != openSecondary) changed.add(s)
        if (p != openPrimary) changed.add(p)
        h.closed(changed)
    }

    // ==== Construction ===================================================================================

    private fun buildHeader() {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val pair = FrameLayout(context)
        pair.addView(swatchSecondary, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM or Gravity.END))
        pair.addView(swatchPrimary, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.START))
        row.addView(pair, LayoutParams(dp(46), dp(38)))
        swatchPrimary.setOnClickListener { chooseTarget(false) }
        swatchSecondary.setOnClickListener { chooseTarget(true) }

        row.addView(context.label("#", 13f, true).apply { setPadding(dp(8), 0, dp(1), 0) })
        hexField.apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(8), InputFilter.AllCaps())
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setPadding(dp(2), 0, dp(2), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (settingHex) return
                    val parsed = PixelColor.parseHex(s.toString()) ?: return
                    loadHsv(parsed)
                    emit(parsed)
                }
            })
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    syncUi()
                    true
                } else false
            }
        }
        row.addView(hexField, LayoutParams(0, dp(38), 1f))

        chipSquare = pageChip(context.getString(R.string.px_color_page_square), Page.SQUARE)
        chipHsv = pageChip(context.getString(R.string.px_mode_hsv), Page.HSV)
        chipRgb = pageChip(context.getString(R.string.px_mode_rgb), Page.RGB)
        row.addView(chipSquare); row.addView(chipHsv); row.addView(chipRgb)
        row.addView(context.iconButton(R.drawable.ic_px_down, R.string.px_colors_hide, 32, 6) { host?.hideRequested() })
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
    }

    private fun pageChip(text: String, target: Page): TextView =
        context.chip(text) { setPage(target) }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply { marginStart = dp(4) }
            minHeight = dp(28)
            minWidth = dp(28)
            setPadding(dp(9), 0, dp(9), 0)
        }

    private fun buildBody() {
        val frame = FrameLayout(context)

        // Page « carré » : le carré, la barre de teinte, la barre d'opacité.
        val sq = LinearLayout(context)
        square = SvSquare(context) { s, v ->
            sat = s; value = v
            emit(PixelColor.fromHsv(hue, sat, value, alphaValue))
        }
        sq.addView(square, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        hueBar = GradientBar(context, vertical = true) { f -> hue = f * 359.99f; emit(PixelColor.fromHsv(hue, sat, value, alphaValue)) }
            .also { it.setStops(HUE_STOPS) }
        sq.addView(hueBar, LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = dp(8) })
        alphaBar = GradientBar(context, vertical = true) { f -> changeAlpha(1f - f) }
        sq.addView(alphaBar, LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = dp(8) })
        squarePage = sq
        frame.addView(sq, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Page « curseurs » : trois curseurs et l'opacité, épais pour se manipuler au doigt.
        val col = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
        for (i in 0 until 4) {
            val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val name = context.label("", 12f).apply { width = dp(56) }
            val bar = GradientBar(context) { f -> onBar(i, f) }
            val num = context.label("", 12f, true).apply { width = dp(44); gravity = Gravity.END }
            line.addView(name)
            line.addView(bar, LayoutParams(0, dp(26), 1f))
            line.addView(num)
            col.addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            bars.add(bar); barLabels.add(name); barValues.add(num)
        }
        slidersPage = col
        frame.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(frame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(136)).apply { topMargin = dp(6) })
    }

    private fun buildRecents() {
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val scroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(line)
        }
        recentsRow = HorizontalScrollViewHolder(scroll, line)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)).apply { topMargin = dp(8) })
    }

    private fun buildRecentsContent() {
        val recents = host?.recentColors().orEmpty()
        recentsRow.line.removeAllViews()
        recentsRow.scroll.visibility = if (recents.isEmpty()) GONE else VISIBLE
        for (c in recents) {
            recentsRow.line.addView(SwatchView(context).apply {
                radiusDp = 6f
                color = c
                setOnClickListener { pick(c) }
            }, LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(6) })
        }
    }

    // ==== Rafraîchir l'affichage ==============================================================================

    /** À appeler quand la couleur a changé ailleurs. */
    fun refresh() {
        val h = host ?: return
        if (!isOpen) return
        val c = h.colorOf(editingSecondary)
        if (c != lastColor) loadHsv(c)
        syncUi()
    }

    private fun syncUi() {
        val h = host ?: return
        val c = lastColor
        swatchPrimary.color = h.colorOf(false)
        swatchSecondary.color = h.colorOf(true)
        swatchPrimary.ring = !editingSecondary
        swatchSecondary.ring = editingSecondary

        if (!(hexField.hasFocus() && PixelColor.parseHex(hexField.text.toString()) == c)) {
            settingHex = true
            hexField.setText(PixelColor.toHex(c).removePrefix("#"))
            settingHex = false
        }

        chipSquare.isSelected = page == Page.SQUARE
        chipHsv.isSelected = page == Page.HSV
        chipRgb.isSelected = page == Page.RGB
        squarePage.visibility = if (page == Page.SQUARE) VISIBLE else GONE
        slidersPage.visibility = if (page == Page.SQUARE) GONE else VISIBLE

        val base = c or (0xFF shl 24)
        val clear = base and 0x00FFFFFF
        if (page == Page.SQUARE) {
            square.hue = hue; square.sat = sat; square.value = value; square.invalidate()
            hueBar.fraction = hue / 360f
            // Du opaque en haut au transparent en bas.
            alphaBar.setStops(intArrayOf(base, clear), true)
            alphaBar.fraction = 1f - alphaValue / 255f
        } else if (page == Page.HSV) {
            setBar(0, R.string.px_ch_hue, HUE_STOPS, false, hue / 360f, context.getString(R.string.px_channel_degrees, hue.toInt()))
            setBar(1, R.string.px_ch_sat, intArrayOf(PixelColor.fromHsv(hue, 0f, value), PixelColor.fromHsv(hue, 1f, value)), false, sat, percent(sat))
            setBar(2, R.string.px_ch_val, intArrayOf(PixelColor.fromHsv(hue, sat, 0f), PixelColor.fromHsv(hue, sat, 1f)), false, value, percent(value))
        } else {
            val r = PixelColor.red(c)
            val g = PixelColor.green(c)
            val b = PixelColor.blue(c)
            setBar(0, R.string.px_ch_red, intArrayOf(PixelColor.argb(255, 0, g, b), PixelColor.argb(255, 255, g, b)), false, r / 255f, number(r))
            setBar(1, R.string.px_ch_green, intArrayOf(PixelColor.argb(255, r, 0, b), PixelColor.argb(255, r, 255, b)), false, g / 255f, number(g))
            setBar(2, R.string.px_ch_blue, intArrayOf(PixelColor.argb(255, r, g, 0), PixelColor.argb(255, r, g, 255)), false, b / 255f, number(b))
        }
        if (page != Page.SQUARE) setBar(3, R.string.px_ch_alpha, intArrayOf(clear, base), true, alphaValue / 255f, percent(alphaValue / 255f))
    }

    private fun percent(f: Float) = context.getString(R.string.px_opt_percent, (f * 100f + 0.5f).toInt())
    private fun number(n: Int) = context.getString(R.string.px_channel_value, n)

    private fun setBar(i: Int, name: Int, stops: IntArray, checker: Boolean, fraction: Float, text: String) {
        barLabels[i].setText(name)
        bars[i].setStops(stops, checker)
        bars[i].fraction = fraction
        barValues[i].text = text
    }

    // ==== Réglage de la couleur ==========================================================================

    private fun loadHsv(c: Int) {
        val hsv = PixelColor.toHsv(c)
        // Un gris n'a pas de teinte, un noir pas de saturation : on garde les précédentes.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) sat = hsv[1]
        value = hsv[2]
        alphaValue = PixelColor.alpha(c)
        lastColor = c
    }

    private fun changeAlpha(f: Float) {
        alphaValue = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
        emit((lastColor and 0x00FFFFFF) or (alphaValue shl 24))
    }

    private fun onBar(index: Int, f: Float) {
        if (index == 3) { changeAlpha(f); return }
        if (page == Page.HSV) {
            when (index) {
                0 -> hue = f * 359.99f
                1 -> sat = f
                else -> value = f
            }
            emit(PixelColor.fromHsv(hue, sat, value, alphaValue))
        } else {
            val ch = (f * 255f + 0.5f).toInt()
            var r = PixelColor.red(lastColor)
            var g = PixelColor.green(lastColor)
            var b = PixelColor.blue(lastColor)
            when (index) { 0 -> r = ch; 1 -> g = ch; else -> b = ch }
            val c = PixelColor.argb(alphaValue, r, g, b)
            loadHsv(c)
            emit(c)
        }
    }

    private fun emit(c: Int) {
        val h = host ?: return
        lastColor = c
        h.editColor(editingSecondary, c)
    }

    private fun pick(c: Int) {
        val h = host ?: return
        loadHsv(c)
        h.pickColor(editingSecondary, c)
    }

    private fun chooseTarget(secondary: Boolean) {
        if (editingSecondary == secondary) return
        editingSecondary = secondary
        refresh()
    }

    private fun setPage(p: Page) {
        page = p
        savedPage = p
        syncUi()
    }

    private fun dp(v: Int) = context.dp(v)

    private companion object {
        /** La présentation choisie survit à une rotation de l'écran. */
        var savedPage = Page.SQUARE
    }
}
