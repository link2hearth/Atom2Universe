package com.Atom2Universe.app.science

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.google.android.material.chip.ChipGroup

/**
 * Les modules reliés entre eux (arbre du vivant, chronologie, bestiaire, plantes, champignons) : chacun a son
 * icône et sa teinte, reprises partout où un module renvoie vers un autre, pour que le même lien ait toujours
 * le même aspect.
 */
enum class ScienceModule(val icon: Int, val title: Int, private val hue: Int) {
    TREE(R.drawable.ic_pt_tree, R.string.science_module_tree, 0xFF26A69A.toInt()),
    TIMELINE(R.drawable.ic_sci_timeline, R.string.ct_title, 0xFFFFB300.toInt()),
    BESTIARY(R.drawable.ic_sci_animal, R.string.be_title, 0xFFEF6C4A.toInt()),
    BOTANY(R.drawable.ic_sci_plant, R.string.science_module_plants, 0xFF7CB342.toInt()),
    MYCOLOGY(R.drawable.ic_sci_fungus, R.string.science_module_fungi, 0xFFB08968.toInt());

    /** La teinte lisible sur le fond du thème (assombrie sur fond clair). */
    fun color(palette: SciencePalette): Int = palette.mark(hue)
}

/**
 * Le kit d'interface des modules de science : les mêmes bandeau, onglets, champ de recherche, puces, cartes,
 * boutons et liens dans chaque module. Les boutons gardent leur largeur naturelle ; les textes d'aide vont
 * derrière « ? ». Tout est construit en code, à partir de la palette du thème.
 */
class ScienceUi(val activity: Activity, val palette: SciencePalette) {
    private val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    private fun dpF(value: Float) = value * density

    // ------------------------------------------------------------------ l'écran

    class Header(val view: View, val back: ImageButton, val title: TextView)
    class HeaderAction(val icon: Int, val label: Int, val action: () -> Unit)

    /**
     * Le bandeau du module : un panneau flottant comme celui des hubs, avec la flèche (appui long : retour au
     * début du module), l'icône et le nom du module, puis les actions à droite (recherche, « ? »…).
     */
    fun header(title: CharSequence, module: ScienceModule?, onBack: () -> Unit, onHome: (() -> Unit)?,
               actions: List<HeaderAction> = emptyList(), subtitle: CharSequence? = null): Header {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = AppearanceStyle.drawable(activity, R.attr.a2uPanelBackground) ?: palette.shape(palette.surface, 20f)
            elevation = AppearanceStyle.elevation(activity, 2f)
        }
        val back = icon(R.drawable.ic_arrow_back_24, R.string.back, onBack)
        onHome?.let { ScienceNavigation.bindHomeAction(back, it) }
        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        module?.let {
            row.addView(ImageView(activity).apply {
                setImageResource(it.icon)
                imageTintList = ColorStateList.valueOf(it.color(palette))
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(6); marginEnd = dp(10) })
        }
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(activity).apply {
            text = title; textSize = 20f; setTextColor(palette.text)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            if (module == null) setPadding(dp(8), 0, 0, 0)
        }
        ViewCompat.setAccessibilityHeading(name, true)
        titles.addView(name)
        subtitle?.let { titles.addView(text(it, 13f, secondary = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }) }
        row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        actions.forEach { row.addView(icon(it.icon, it.label, it.action), LinearLayout.LayoutParams(dp(48), dp(48))) }
        return Header(row, back, name)
    }

    /**
     * La colonne de l'écran : fond du thème, bandeau avec ses marges, puis [content] qui prend le reste, centré et
     * jamais plus large que [maxWidthDp]. Les marges des barres système sont prises sur la colonne.
     */
    fun screen(header: Header, content: View, maxWidthDp: Int = 840): LinearLayout {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.background) }
        root.addView(header.view, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(12), dp(16), dp(4)) })
        root.addView(MaxWidthFrame(activity, dp(maxWidthDp)).apply { addView(content, FrameLayout.LayoutParams(-1, -1)) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER_HORIZONTAL })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        return root
    }

    // ------------------------------------------------------------------ naviguer

    /** La rangée d'onglets : le choisi est une pastille pleine, les autres du texte discret. */
    class TabRow internal constructor(val view: View, private val restyle: (Int) -> Unit) {
        fun select(index: Int) = restyle(index)
    }

    fun tabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit): TabRow {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(2), dp(12), dp(6)) }
        val pills = titles.mapIndexed { index, title ->
            TextView(activity).apply {
                text = title; textSize = 14f; gravity = Gravity.CENTER
                minHeight = dp(40); setPadding(dp(16), 0, dp(16), 0)
                isClickable = true; isFocusable = true
                setOnClickListener { onSelect(index) }
            }.also { row.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) }) }
        }
        fun restyle(active: Int) {
            pills.forEachIndexed { index, pill ->
                val on = index == active
                pill.background = ripple(if (on) palette.shape(palette.accent, 20f) else null)
                pill.setTextColor(if (on) palette.onAccent else palette.secondary)
                pill.typeface = Typeface.create("sans-serif", if (on) Typeface.BOLD else Typeface.NORMAL)
                pill.isSelected = on
            }
        }
        restyle(selected)
        return TabRow(scrollRow(row), ::restyle)
    }

    /** Le lien vers un autre module : son icône et son nom, cerclés de sa teinte. */
    fun moduleChip(module: ScienceModule, onClick: () -> Unit, label: CharSequence? = null): View {
        val hue = module.color(palette)
        val chip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(38); setPadding(dp(10), 0, dp(14), 0)
            background = ripple(GradientDrawable().apply {
                setColor(palette.raised); cornerRadius = dpF(19f); setStroke(dp(2).coerceAtLeast(1), hue)
            })
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        chip.addView(ImageView(activity).apply {
            setImageResource(module.icon); imageTintList = ColorStateList.valueOf(hue)
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        chip.addView(TextView(activity).apply {
            text = label ?: activity.getString(module.title); textSize = 13.5f; setTextColor(palette.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        chip.contentDescription = label ?: activity.getString(module.title)
        return chip
    }

    // ------------------------------------------------------------------ choisir, filtrer, chercher

    /** Une puce de filtre : pleine quand elle est choisie. */
    fun chip(text: CharSequence, selected: Boolean, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER
        minHeight = dp(34); setPadding(dp(12), 0, dp(12), 0)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
        styleChip(this, selected)
    }

    fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = ripple(if (selected) palette.shape(palette.accent, 17f) else palette.shape(palette.raised, 17f))
        chip.setTextColor(if (selected) palette.onAccent else palette.text)
        chip.isSelected = selected
    }

    /** Des puces ou des liens sur une ligne qui défile de côté, sans barre. */
    fun scrollRow(row: View): HorizontalScrollView = HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled = false; clipToPadding = false
        addView(row, FrameLayout.LayoutParams(-2, -2))
    }

    fun chipRow(chips: List<View>, startPadding: Int = 0): HorizontalScrollView {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(startPadding, 0, startPadding, 0) }
        chips.forEach { row.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }) }
        return scrollRow(row)
    }

    /** Des puces ou des boutons qui passent à la ligne quand la place manque. */
    fun flow(views: List<View>): ChipGroup = ChipGroup(activity).apply {
        chipSpacingHorizontal = dp(8); chipSpacingVertical = dp(6)
        views.forEach { addView(it, ViewGroup.LayoutParams(-2, -2)) }
    }

    class SearchField(val view: View, val edit: EditText)

    /** Le champ de recherche : arrondi, avec la loupe et la croix qui efface. */
    fun searchField(hint: CharSequence, initial: String, onChange: (String) -> Unit): SearchField {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = palette.shape(palette.raised, 22f); minimumHeight = dp(44)
            setPadding(dp(12), 0, dp(2), 0)
        }
        box.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_search); imageTintList = ColorStateList.valueOf(palette.secondary)
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        val clear = icon(R.drawable.ic_close, R.string.science_clear_search) {}.apply { visibility = if (initial.isEmpty()) View.GONE else View.VISIBLE }
        val edit = EditText(activity).apply {
            background = null; isSingleLine = true; textSize = 15f
            this.hint = hint; setText(initial); setSelection(initial.length)
            setTextColor(palette.text); setHintTextColor(palette.secondary)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), dp(8), dp(4), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    onChange(s.toString())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        clear.setOnClickListener { edit.setText("") }
        box.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(clear, LinearLayout.LayoutParams(dp(40), dp(40)))
        return SearchField(box, edit)
    }

    // ------------------------------------------------------------------ agir

    /** Un bouton de largeur naturelle. */
    fun action(text: CharSequence, icon: Int = 0, onClick: () -> Unit): AppCompatButton = AppCompatButton(activity).apply {
        this.text = text; isAllCaps = false; textSize = 14f
        minHeight = dp(44); minimumHeight = dp(44); minWidth = 0; minimumWidth = 0
        setPadding(dp(16), dp(6), dp(16), dp(6))
        setTextColor(palette.text); background = ripple(palette.shape(palette.raised, 12f))
        if (icon != 0) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(palette.secondary); compoundDrawablePadding = dp(8)
        }
        setOnClickListener { onClick() }
    }

    fun icon(drawable: Int, label: Int, onClick: () -> Unit): ImageButton = ImageButton(activity).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(palette.text)
        background = ripple(null, bounded = false)
        contentDescription = activity.getString(label)
        setOnClickListener { onClick() }
    }

    /** Un lien vers une page web : souligné, couleur d'accent, rien ne se passe sans navigateur. */
    fun link(text: CharSequence, url: String): TextView = TextView(activity).apply {
        this.text = text; textSize = 14f; setTextColor(palette.ink(palette.accent, palette.background))
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        minHeight = dp(40); gravity = Gravity.CENTER_VERTICAL
        isClickable = true; isFocusable = true
        setOnClickListener { openUrl(url) }
    }

    fun openUrl(url: String) {
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: ActivityNotFoundException) { }
    }

    // ------------------------------------------------------------------ lire

    fun text(value: CharSequence, size: Float = 15f, secondary: Boolean = false, bold: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = size; setTextColor(if (secondary) palette.secondary else palette.text)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dpF(2f), 1f)
    }

    /** Le titre d'une partie de fiche. */
    fun section(value: CharSequence): TextView = text(value, 17f, bold = true).also { ViewCompat.setAccessibilityHeading(it, true) }

    fun paragraph(value: CharSequence): TextView = text(value).apply { setTextIsSelectable(true); setLineSpacing(dpF(3f), 1f) }

    fun note(value: CharSequence): TextView = text(value, 12.5f, secondary = true)

    fun latin(value: CharSequence, size: Float = 16f): TextView = text(value, size, secondary = true).apply { setTypeface(typeface, Typeface.ITALIC) }

    /** Une pastille de statut ou de catégorie ; [color] peut être translucide, le contraste tient compte du fond. */
    fun pill(value: CharSequence, color: Int, small: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = if (small) 11f else 12.5f
        val seen = ColorUtils.compositeColors(color, ColorUtils.setAlphaComponent(palette.raised, 255))
        setTextColor(SciencePalette.contrastingText(seen))
        typeface = Typeface.create("sans-serif", Typeface.BOLD); gravity = Gravity.CENTER
        setPadding(dp(if (small) 8 else 10), dp(3), dp(if (small) 8 else 10), dp(3))
        background = GradientDrawable().apply { setColor(color); cornerRadius = dpF(14f) }
    }

    /** Le bloc d'identité en tête d'une fiche : nom latin, pastilles, puis les liens vers les autres modules. */
    fun identity(latin: CharSequence?, badges: List<View> = emptyList(), links: List<View> = emptyList()): LinearLayout {
        val block = column()
        latin?.let { block.addView(latin(it), full(0)) }
        if (badges.isNotEmpty()) block.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            badges.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }) }
        }, full(8))
        if (links.isNotEmpty()) block.addView(chipRow(links), full(12))
        return block
    }

    // ------------------------------------------------------------------ contenir

    /** Une carte cliquable (fond de surface, coins arrondis, ondulation). */
    fun card(onClick: (() -> Unit)? = null): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = ripple(palette.shape(palette.surface, 16f))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() } }
    }

    /** Une ligne de liste : la vignette à gauche, le nom, le sous-titre, des pastilles. */
    class Row(val view: LinearLayout, val thumb: FrameLayout, val title: TextView, val subtitle: TextView, val badges: LinearLayout)

    fun row(thumbWidth: Int, thumbHeight: Int, onClick: (() -> Unit)? = null): Row {
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ripple(palette.shape(palette.surface, 16f))
            setPadding(dp(8), dp(8), dp(14), dp(8))
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() } }
        }
        val thumb = FrameLayout(activity).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dpF(10f) }
            clipToOutline = true
        }
        view.addView(thumb, LinearLayout.LayoutParams(thumbWidth, thumbHeight))
        val texts = column()
        val title = text("", 16f, bold = true)
        val subtitle = text("", 13.5f, secondary = true).apply { setTypeface(typeface, Typeface.ITALIC) }
        val badges = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        texts.addView(title); texts.addView(subtitle, full(2)); texts.addView(badges, full(6))
        view.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return Row(view, thumb, title, subtitle, badges)
    }

    /** Une tuile de grille : l'image en haut sur fond blanc, le nom, le sous-titre. [imageRatio] = hauteur / largeur. */
    class Tile(val view: LinearLayout, val image: FrameLayout, val title: TextView, val subtitle: TextView, val badges: LinearLayout) {
        fun badge(pill: View) { badges.visibility = View.VISIBLE; badges.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * pill.resources.displayMetrics.density).toInt() }) }
    }

    fun tile(imageRatio: Float = .75f, onClick: (() -> Unit)? = null): Tile {
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ripple(palette.shape(palette.surface, 16f))
            setPadding(dp(6), dp(6), dp(6), dp(10))
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() } }
        }
        val image = FitFrame(activity, imageRatio, Int.MAX_VALUE).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dpF(12f) }
            clipToOutline = true
        }
        view.addView(image, LinearLayout.LayoutParams(-1, -2))
        val title = text("", 15f, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(6), 0) }
        val subtitle = text("", 12f, secondary = true).apply {
            setTypeface(typeface, Typeface.ITALIC); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(6), 0)
        }
        view.addView(title, full(8)); view.addView(subtitle, full(2))
        val badges = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; setPadding(dp(6), 0, dp(6), 0)
        }
        view.addView(badges, full(6))
        return Tile(view, image, title, subtitle, badges)
    }

    /** Des tuiles rangées en [columns] colonnes de même largeur, dans une colonne qui défile normalement. */
    fun grid(tiles: List<View>, columns: Int, gap: Int = dp(10)): LinearLayout {
        val block = column()
        tiles.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(columns) { i ->
                val cell = rowTiles.getOrNull(i) ?: View(activity)
                row.addView(cell, LinearLayout.LayoutParams(0, if (cell is LinearLayout) -2 else 0, 1f).apply {
                    if (i > 0) marginStart = gap
                })
            }
            block.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = gap })
        }
        return block
    }

    /** Le nombre de colonnes pour des tuiles d'au moins [minTileDp] dans [widthPx]. */
    fun columns(widthPx: Int, minTileDp: Int = 168, atLeast: Int = 2): Int = kotlin.math.max(atLeast, widthPx / dp(minTileDp))

    /** Un chapitre de fiche : une carte opaque teintée de [hue], son titre dans cette teinte, puis [body]. */
    fun chapter(title: CharSequence, hue: Int, body: View): LinearLayout {
        val background = ColorUtils.blendARGB(palette.raised, hue, if (palette.isLight) 0.10f else 0.16f)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            this.background = GradientDrawable().apply { setColor(background); cornerRadius = AppearanceStyle.corner(activity, 18f) }
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val heading = text(title, 16f, bold = true).apply { setTextColor(palette.ink(hue, background)) }
        ViewCompat.setAccessibilityHeading(heading, true)
        card.addView(heading)
        card.addView(body, full(8))
        return card
    }

    /** Une image en tête de fiche : fond blanc, coins arrondis, jamais coupée. */
    fun hero(content: View, ratio: Float, maxWidthDp: Int = 640): FitFrame = FitFrame(activity, ratio, dp(maxWidthDp)).apply {
        addView(content, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        clipToOutline = true
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = AppearanceStyle.corner(activity, 18f) }
    }

    fun column(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    fun line(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun divider(): View = View(activity).apply { setBackgroundColor(ColorUtils.setAlphaComponent(palette.outline, 140)) }

    fun full(top: Int = 8): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    fun wrap(top: Int = 8): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) }

    fun ripple(shape: Drawable?, bounded: Boolean = true): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 55)), shape,
            if (bounded) null else palette.shape(Color.WHITE, 24f))
}

/** L'espace entre les tuiles d'une grille ; la grille elle-même garde une marge d'une demi-largeur d'espace. */
class GridGap(private val gap: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView,
                                state: androidx.recyclerview.widget.RecyclerView.State) {
        outRect.set(gap / 2, gap / 2, gap / 2, gap / 2)
    }
}

/** Un cadre qui ne dépasse jamais [maxWidth] : posé avec une gravité centrée, il reste au milieu des grands écrans. */
class MaxWidthFrame(context: Context, private val maxWidth: Int) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val spec = if (mode != MeasureSpec.UNSPECIFIED && size > maxWidth) MeasureSpec.makeMeasureSpec(maxWidth, mode) else widthMeasureSpec
        super.onMeasure(spec, heightMeasureSpec)
    }
}
