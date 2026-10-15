package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/**
 * La boutique de construction : onglets, puis une grille recyclée. Seules les cartes visibles existent (la catégorie
 * des décorations en compte plus de 300) et leurs dessins arrivent d'un fil d'arrière-plan ([HamletCatalogThumbs]) :
 * l'ouverture et le changement d'onglet ne dépendent plus du nombre d'objets.
 */
internal class HamletCatalogDialog(private val a: HamletActivity) {
    private sealed class Row {
        class Building(val def: Def) : Row()
        class Heading(val label: Int, val help: String?) : Row()
        class Recent(val ids: List<String>) : Row()
        class Action(val label: Int, val glyph: String, val enabled: Boolean, val click: () -> Unit) : Row()
        object Empty : Row()
    }

    private val categories = listOf(Cat.FIELD, Cat.HOME, Cat.SHOP, Cat.RANCH, Cat.NATURE, Cat.DECOR, Cat.PATH)
    private val glyphs = listOf("leaf", "house", "crate", "paw", "flower", "lantern", "map")
    private var category = Cat.FIELD
    private var query = ""
    private var toolsOnly = false
    private var columns = 2
    private lateinit var sheet: HamletSheet
    private lateinit var toolsTab: Button
    private lateinit var search: EditText
    private lateinit var recycler: RecyclerView
    private val tabs = ArrayList<Button>()
    /** Le dernier état dessiné de chaque onglet : on ne refait le fond que de ceux qui changent. */
    private val tabState = arrayOfNulls<Boolean>(categories.size + 1)
    private val rows = ArrayList<Row>()
    private val adapter = Adapter()
    private val handler = Handler(Looper.getMainLooper())
    private val names = HashMap<String, String>()
    private val thumbs get() = a.catalogThumbs
    private fun dp(v: Int) = a.dp(v)
    private fun nameOf(id: String) = names.getOrPut(id) { a.itemName(id) }

    /** [start] opens a given category (a chapter goal sends the player to the homes, for instance). */
    fun show(tools: Boolean = false, start: Cat? = null) {
        toolsOnly = tools
        if (start != null && start in categories) category = start
        sheet = HamletSheet(a, a.getString(R.string.hamlet_build), tall = true, wide = true)
        val tabRow = LinearLayout(a).apply { setPadding(0, dp(8), 0, dp(4)) }
        for ((i, cat) in categories.withIndex()) tabRow.addView(HamletUi.button(a, a.stringByName("hamlet_category_${cat.name.lowercase()}"), glyph = glyphs[i]) {
            toolsOnly = false; category = cat; redraw()
        }.apply { maxLines = 1; tabs.add(this) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) })
        toolsTab = HamletUi.button(a, a.getString(R.string.hamlet_builder_tools), glyph = "brush") { toolsOnly = true; redraw() }.apply { maxLines = 1 }
        tabRow.addView(toolsTab)
        sheet.top.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) })
        val applySearch = Runnable { redraw() }
        search = HamletUi.field(EditText(a)).apply {
            setHint(R.string.hamlet_search); isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val typed = s.toString().trim()
                    if (typed == query) return
                    query = typed
                    // Une frappe n'a pas besoin de refaire la liste : on attend la fin du mot.
                    handler.removeCallbacks(applySearch); handler.postDelayed(applySearch, SEARCH_DELAY)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        sheet.top.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        columns = (a.resources.configuration.screenWidthDp / 165).coerceIn(2, 5)
        recycler = RecyclerView(a).apply {
            layoutManager = GridLayoutManager(a, columns).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) = if (rows.getOrNull(position) is Row.Building) 1 else columns
                }
            }
            adapter = this@HamletCatalogDialog.adapter
            itemAnimator = null; setHasFixedSize(true); setItemViewCacheSize(10)
            clipToPadding = false; setPadding(dp(11), dp(8), dp(11), dp(12)); overScrollMode = View.OVER_SCROLL_NEVER
            recycledViewPool.setMaxRecycledViews(TYPE_CARD, 30)
        }
        sheet.content(recycler)
        thumbs.onReady = { refreshPreviews(recycler) }
        sheet.onDismiss = { handler.removeCallbacksAndMessages(null); thumbs.onReady = null; thumbs.cancel() }
        redraw(); sheet.show()
    }

    private fun redraw() {
        for (i in tabs.indices) setTab(i, tabs[i], !toolsOnly && categories[i] == category)
        setTab(categories.size, toolsTab, toolsOnly)
        search.visibility = if (toolsOnly) View.GONE else View.VISIBLE
        rows.clear()
        if (toolsOnly) {
            recentRows()
            rows.add(Row.Heading(R.string.hamlet_builder_tools, listOf(R.string.hamlet_builder_undo_hint, R.string.hamlet_builder_copy_hint,
                R.string.hamlet_builder_redesign_hint, R.string.hamlet_builder_bulldoze_hint).joinToString("\n\n") { a.getString(it) }))
            rows.add(Row.Action(R.string.hamlet_builder_undo, "undo", a.game.buildHistory.canUndo()) { sheet.dismiss(); a.village.undoBuild() })
            rows.add(Row.Action(R.string.hamlet_builder_copy, "brush", true) { sheet.dismiss(); a.village.startEyedropper() })
            rows.add(Row.Action(R.string.hamlet_builder_redesign, "move", true) { sheet.dismiss(); a.village.startRedesign() })
            rows.add(Row.Action(R.string.hamlet_builder_bulldoze, "hammer", true) { sheet.dismiss(); a.village.startBulldozer() })
            terrainRows()
        } else {
            if (query.isBlank()) recentRows()
            if (category == Cat.PATH && query.isBlank()) terrainRows()
            val defs = byCategory[category].orEmpty().filter { query.isBlank() || nameOf(it.id).contains(query, ignoreCase = true) }
            if (defs.isEmpty()) rows.add(Row.Empty) else for (d in defs) rows.add(Row.Building(d))
        }
        adapter.notifyDataSetChanged()
        recycler.scrollToPosition(0)
    }

    private fun setTab(index: Int, button: Button, chosen: Boolean) {
        if (tabState[index] == chosen) return
        tabState[index] = chosen
        HamletUi.choice(button, chosen)
    }

    private fun recentRows() {
        val recent = a.game.buildHistory.recent
        if (recent.isEmpty()) return
        rows.add(Row.Heading(R.string.hamlet_builder_recent, null))
        rows.add(Row.Recent(recent))
    }

    private fun terrainRows() {
        rows.add(Row.Heading(R.string.hamlet_category_path, a.getString(R.string.hamlet_builder_fill_hint)))
        rows.add(Row.Action(R.string.hamlet_builder_fill, "brush", true) {
            val paths = Hamlet.defs.filter { it.isPath && it.w == 1 && it.d == 1 && a.game.journey.unlocked(it) }
            HamletSheet.pick(a, a.getString(R.string.hamlet_builder_fill), paths.map { nameOf(it.id) }, glyphs = paths.map { "map" }) { which ->
                sheet.dismiss(); a.village.startFill(paths[which])
            }
        })
        for (on in listOf(true, false)) rows.add(Row.Action(if (on) R.string.hamlet_builder_flood else R.string.hamlet_builder_drain, if (on) "drop" else "leaf", true) {
            HamletSheet.confirm(a, a.getString(if (on) R.string.hamlet_builder_flood else R.string.hamlet_builder_drain),
                a.getString(if (on) R.string.hamlet_builder_flood_confirm else R.string.hamlet_builder_drain_confirm), glyph = if (on) "drop" else "leaf") {
                sheet.dismiss(); a.village.browse()
                a.toast(a.getString(R.string.hamlet_builder_terrain_result, a.game.floodRealm(on)))
            }
        })
    }

    private fun refreshPreviews(v: View) {
        if (v is Preview) v.invalidate()
        else if (v is ViewGroup) for (i in 0 until v.childCount) refreshPreviews(v.getChildAt(i))
    }

    private fun summary(d: Def): String {
        val p = HamletLife.produce[d.id]
        return when {
            p != null -> a.getString(R.string.hamlet_catalog_produce, p.coins, a.duration(p.seconds * 1000L), if (p.feed > 0) 1 + p.feed / 20 else 0)
            d.cat == Cat.HOME -> a.getString(R.string.hamlet_catalog_home, d.pop, d.tax, a.duration(d.period * 1000L))
            d.cat == Cat.SHOP -> a.getString(R.string.hamlet_catalog_shop, d.pay, a.duration(d.job * 1000L)) + if (d.shore) a.getString(R.string.hamlet_shore_short) else ""
            d.cat == Cat.FIELD -> a.getString(if (d.magic) R.string.hamlet_catalog_magic else R.string.hamlet_catalog_field)
            d.isPath -> a.getString(R.string.hamlet_catalog_path)
            else -> a.getString(R.string.hamlet_catalog_charm, d.charm)
        }
    }

    /** Ce que cache le « ? » d'une carte : la recette d'un atelier, ou la production et les vivres d'un élevage. Calculé à la demande. */
    private fun details(d: Def): Pair<String, String>? = when {
        d.cat == Cat.SHOP -> a.getString(R.string.hamlet_industry_recipe_details) to HamletUi.recipeText(a, d.id, 1, a.game)
        d.id in HamletLife.produce -> a.getString(R.string.hamlet_industry_produce_details) to
            HamletUi.costText(a, VillageCost(resources = HamletIndustry.products(d.id, 1)))
        else -> null
    }

    /** Un clic sur une carte : poser l'objet, expliquer ce qui manque, ou secouer le cadenas. */
    private fun choose(d: Def, lock: View) {
        val g = a.game
        val unlocked = g.journey.unlocked(d)
        if (unlocked && (g.sandbox || g.canAfford(d))) { sheet.dismiss(); a.village.startPlacing(d) }
        else if (unlocked) HamletSheet.message(a, nameOf(d.id),
            HamletUi.costText(a, HamletIndustry.building(d), g) + "\n\n" + a.getString(R.string.hamlet_industry_sources))
        else HamletMotion.pop(lock, 1.25f)
    }

    private fun infoButton(click: () -> Unit): Button =
        HamletUi.button(a, a.getString(R.string.hamlet_ui_info_mark), Weight.QUIET) { click() }.apply { setTypeface(null, Typeface.BOLD) }

    // ------------------------------------------------------------------ la grille

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Building -> TYPE_CARD
            is Row.Heading -> TYPE_HEADING
            is Row.Recent -> TYPE_RECENT
            is Row.Action -> TYPE_ACTION
            Row.Empty -> TYPE_EMPTY
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            TYPE_CARD -> CardHolder()
            TYPE_HEADING -> HeadingHolder()
            TYPE_RECENT -> RecentHolder()
            TYPE_ACTION -> ActionHolder()
            else -> EmptyHolder()
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            when {
                holder is CardHolder && row is Row.Building -> holder.bind(row.def)
                holder is HeadingHolder && row is Row.Heading -> holder.bind(row)
                holder is RecentHolder && row is Row.Recent -> holder.bind(row.ids)
                holder is ActionHolder && row is Row.Action -> holder.bind(row)
            }
        }
    }

    /** Une carte : le dessin, le nom, ce que le bâtiment rapporte, le prix et le cadenas éventuel. Créée une fois, remplie à chaque affichage. */
    private inner class CardHolder : RecyclerView.ViewHolder(LinearLayout(a)) {
        private val box = itemView as LinearLayout
        private val preview = Preview()
        private val name = TextView(a)
        private val summary = TextView(a)
        private val tag = TextView(a)
        private val tagFace: GradientDrawable = HamletUi.panel(a, 0, 12)
        private val materials = TextView(a)
        private val info: Button
        private val keyIcon: Drawable = HamletUi.icon(a, "key", 16)
        private val coinIcon: Drawable = HamletUi.icon(a, "coin", 17)
        private val gemIcon: Drawable = HamletUi.icon(a, "gem", 17)
        private var def: Def? = null
        private var details: Pair<String, String>? = null

        init {
            box.apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(6), dp(6), dp(8))
                background = HamletUi.rowFace(a, false)
                isClickable = true; isFocusable = true
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            }
            val picture = FrameLayout(a)
            picture.addView(preview, FrameLayout.LayoutParams(-1, -1))
            info = infoButton { details?.let { (title, text) -> HamletSheet.message(a, title, text) } }
            picture.addView(info, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END))
            box.addView(picture, LinearLayout.LayoutParams(-1, dp(84)))
            // Deux lignes réservées partout : toutes les cartes d'une catégorie ont la même hauteur.
            name.apply { textSize = 13.5f; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD); setLines(2); gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END }
            box.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            summary.apply { textSize = 11.5f; setTextColor(HamletUi.MUTED); setLines(2); gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END }
            box.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            tag.apply { textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; compoundDrawablePadding = dp(4); setPadding(dp(10), dp(3), dp(10), dp(3)); background = tagFace }
            box.addView(tag, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5) })
            materials.apply { textSize = 11.5f; gravity = Gravity.CENTER; setLines(2); ellipsize = TextUtils.TruncateAt.END }
            box.addView(materials, LinearLayout.LayoutParams(-1, -2))
            HamletMotion.press(box)
            box.setOnClickListener { def?.let { choose(it, tag) } }
        }

        fun bind(d: Def) {
            def = d
            val g = a.game
            val unlocked = g.journey.unlocked(d)
            val cost = HamletIndustry.building(d)
            val affordable = g.sandbox || cost.canPay(g)
            box.alpha = if (unlocked) 1f else .62f
            preview.show(d)
            name.text = nameOf(d.id)
            summary.text = summary(d)
            when {
                !unlocked -> {
                    val gate = g.journey.buildingChapter(d.id)
                    tag.text = if (gate > g.journey.chapter) a.getString(R.string.hamlet_ui_chapter_short, gate) else a.getString(R.string.hamlet_ui_level_short, d.lvl)
                    tag.setTextColor(HamletUi.BAD); tagFace.setColor(0x1FB2453A)
                    tag.setCompoundDrawablesRelativeWithIntrinsicBounds(keyIcon, null, null, null)
                }
                g.sandbox || d.cost + d.gems == 0 -> {
                    tag.setText(R.string.hamlet_price_free); tag.setTextColor(HamletUi.GOOD); tagFace.setColor(0x1F2F7D45)
                    tag.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }
                else -> {
                    tag.text = a.getString(R.string.hamlet_ui_number, if (d.gems > 0) d.gems else d.cost)
                    tag.setTextColor(if (affordable) HamletUi.INK else HamletUi.BAD)
                    tagFace.setColor(if (affordable) 0x33EAC779 else 0x1FB2453A)
                    tag.setCompoundDrawablesRelativeWithIntrinsicBounds(if (d.gems > 0) gemIcon else coinIcon, null, null, null)
                }
            }
            // Les catégories de construction réservent la ligne des matériaux, même vide : la hauteur ne change pas d'une carte à l'autre.
            val structural = d.cat == Cat.FIELD || d.cat == Cat.HOME || d.cat == Cat.SHOP || d.cat == Cat.RANCH
            if (!structural) materials.visibility = View.GONE
            else if (unlocked && !g.sandbox && cost.resources.isNotEmpty()) {
                materials.visibility = View.VISIBLE
                materials.text = HamletUi.costText(a, VillageCost(resources = cost.resources))
                materials.setTextColor(if (affordable) HamletUi.MUTED else HamletUi.BAD)
            } else { materials.visibility = View.INVISIBLE; materials.text = "" }
            details = details(d)
            info.visibility = if (details == null) View.GONE else View.VISIBLE
            details?.let { info.contentDescription = it.first }
            box.contentDescription = a.getString(R.string.hamlet_catalog_item, name.text, tag.text, d.lvl)
        }
    }

    private inner class HeadingHolder : RecyclerView.ViewHolder(LinearLayout(a)) {
        private val label = TextView(a)
        private val help: Button
        private var row: Row.Heading? = null
        init {
            (itemView as LinearLayout).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(10), 0, dp(2))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            label.apply { textSize = HamletUi.TITLE; setTypeface(null, Typeface.BOLD); setTextColor(HamletUi.INK) }
            (itemView as LinearLayout).addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            help = infoButton { row?.let { HamletSheet.message(a, a.getString(it.label), it.help.orEmpty()) } }
            (itemView as LinearLayout).addView(help, LinearLayout.LayoutParams(dp(44), -2))
        }
        fun bind(r: Row.Heading) {
            row = r
            label.setText(r.label)
            help.visibility = if (r.help == null) View.GONE else View.VISIBLE
            help.contentDescription = a.getString(r.label)
        }
    }

    /** Les derniers objets posés : une bande qui défile de côté. */
    private inner class RecentHolder : RecyclerView.ViewHolder(HorizontalScrollView(a)) {
        private val line = LinearLayout(a)
        init {
            (itemView as HorizontalScrollView).apply {
                isHorizontalScrollBarEnabled = false; addView(line)
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
        }
        fun bind(ids: List<String>) {
            line.removeAllViews()
            for (id in ids) {
                val def = Hamlet.byId[id] ?: continue
                val piece = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(3), dp(4), dp(4))
                    background = HamletUi.rowFace(a, false)
                    contentDescription = nameOf(id); isClickable = true; isFocusable = true
                    setOnClickListener { sheet.dismiss(); a.village.startPlacing(def) }
                    HamletMotion.press(this)
                }
                piece.addView(Preview().apply { show(def) }, LinearLayout.LayoutParams(dp(60), dp(56)))
                piece.addView(TextView(a).apply { text = nameOf(id); textSize = 11f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(-1, dp(30)))
                line.addView(piece, LinearLayout.LayoutParams(dp(78), dp(98)).apply { marginEnd = dp(5) })
            }
        }
    }

    private inner class ActionHolder : RecyclerView.ViewHolder(LinearLayout(a)) {
        private var row: Row.Action? = null
        private val button: Button = HamletUi.row(a, "", glyph = "brush") { row?.click?.invoke() }
        init {
            (itemView as LinearLayout).apply {
                addView(button, LinearLayout.LayoutParams(-1, -2))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { topMargin = dp(5) }
            }
        }
        fun bind(r: Row.Action) {
            row = r
            button.text = a.getString(r.label)
            HamletUi.glyph(button, r.glyph)
            button.isEnabled = r.enabled
        }
    }

    private inner class EmptyHolder : RecyclerView.ViewHolder(TextView(a)) {
        init {
            (itemView as TextView).apply {
                setText(R.string.hamlet_search_empty); setTextColor(HamletUi.MUTED); textSize = HamletUi.TEXT
                setPadding(dp(4), dp(20), dp(4), dp(20))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
        }
    }

    /** Le dessin d'un objet : la vignette si elle est prête, sinon rien jusqu'à ce que le fil d'arrière-plan l'apporte. */
    private inner class Preview : View(a) {
        /** Lu par le fil d'arrière-plan pour savoir si la vue attend encore cette vignette. */
        @Volatile private var key = ""
        private var def: Def? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun show(d: Def) {
            if (def === d) return
            def = d; key = if (width > 0) HamletCatalogThumbs.key(d, width, height) else ""
            invalidate()
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            def?.let { key = HamletCatalogThumbs.key(it, w, h) }
        }
        override fun onDraw(canvas: Canvas) {
            val d = def ?: return
            if (key.isEmpty()) key = HamletCatalogThumbs.key(d, width, height)
            val wanted = key
            val bmp = thumbs.peek(wanted)
            if (bmp == null) { thumbs.request(d, width, height, wanted) { key == wanted }; return }
            canvas.drawBitmap(bmp, (width - bmp.width) / 2f, (height - bmp.height) / 2f, paint)
        }
    }

    private companion object {
        const val TYPE_CARD = 0
        const val TYPE_HEADING = 1
        const val TYPE_RECENT = 2
        const val TYPE_ACTION = 3
        const val TYPE_EMPTY = 4
        const val SEARCH_DELAY = 140L
        /** Les objets de chaque catégorie, par niveau puis par prix : triés une fois pour toutes. */
        val byCategory: Map<Cat, List<Def>> by lazy {
            Hamlet.defs.groupBy { it.cat }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.lvl }, { it.cost })) }
        }
    }
}
