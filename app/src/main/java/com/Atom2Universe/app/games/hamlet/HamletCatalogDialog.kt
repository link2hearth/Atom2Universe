package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** La boutique de construction : onglets illustrés, cartes avec le vrai dessin, le prix et le cadenas éventuel. */
internal class HamletCatalogDialog(private val a: HamletActivity) {
    private val categories = listOf(Cat.FIELD, Cat.HOME, Cat.SHOP, Cat.RANCH, Cat.NATURE, Cat.DECOR, Cat.PATH)
    private val glyphs = listOf("leaf", "house", "crate", "paw", "flower", "lantern", "map")
    private var category = Cat.FIELD
    private var query = ""
    private var toolsOnly = false
    private lateinit var sheet: HamletSheet
    private val tabs = ArrayList<Button>()
    private lateinit var toolsTab: Button
    private lateinit var search: EditText
    private val rows get() = sheet.body
    private fun dp(v: Int) = a.dp(v)

    fun show(tools: Boolean = false) {
        toolsOnly = tools
        sheet = HamletSheet(a, a.getString(R.string.hamlet_build), tall = true, wide = true)
        val tabRow = LinearLayout(a).apply { setPadding(0, dp(8), 0, dp(4)) }
        for ((i, cat) in categories.withIndex()) tabRow.addView(HamletUi.button(a, a.stringByName("hamlet_category_${cat.name.lowercase()}"), glyph = glyphs[i]) {
            toolsOnly = false; category = cat; redraw()
        }.apply { maxLines = 1; tabs.add(this) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) })
        toolsTab = HamletUi.button(a, a.getString(R.string.hamlet_builder_tools), glyph = "brush") { toolsOnly = true; redraw() }.apply { maxLines = 1 }
        tabRow.addView(toolsTab)
        sheet.top.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) })
        search = HamletUi.field(EditText(a)).apply {
            setHint(R.string.hamlet_search); isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString().trim(); redraw() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        sheet.top.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        redraw(); sheet.show()
    }

    private fun redraw() {
        tabs.forEachIndexed { i, b -> HamletUi.choice(b, !toolsOnly && categories[i] == category) }
        HamletUi.choice(toolsTab, toolsOnly)
        search.visibility = if (toolsOnly) View.GONE else View.VISIBLE
        rows.removeAllViews()
        if (toolsOnly) { showTools(); return }
        if (query.isBlank()) recentRow()
        if (category == Cat.PATH && query.isBlank()) terrainTools()
        val defs = Hamlet.defs.filter { it.cat == category && (query.isBlank() || a.itemName(it.id).contains(query, ignoreCase = true)) }.sortedWith(compareBy({ it.lvl }, { it.cost }))
        if (defs.isEmpty()) rows.addView(TextView(a).apply { setText(R.string.hamlet_search_empty); setTextColor(HamletUi.MUTED); textSize = HamletUi.TEXT; setPadding(dp(4), dp(20), dp(4), dp(20)) })
        val columns = (a.resources.configuration.screenWidthDp / 165).coerceIn(2, 5)
        for (group in defs.chunked(columns)) {
            val row = LinearLayout(a)
            for (d in group) row.addView(card(d), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            repeat(columns - group.size) { row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)) }
            rows.addView(row)
        }
    }
    /** One building: its drawing, its name, its price; a padlock and the condition when it is not open yet. */
    private fun card(d: Def): View {
        val unlocked = a.game.journey.unlocked(d)
        val affordable = a.game.sandbox || a.game.canAfford(d)
        val gate = a.game.journey.buildingChapter(d.id)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            background = HamletUi.rowFace(a, false)
            isClickable = true; isFocusable = true
            alpha = if (unlocked) 1f else .62f
        }
        box.addView(Preview(d), LinearLayout.LayoutParams(-1, dp(84)))
        box.addView(TextView(a).apply {
            text = a.itemName(d.id); textSize = 13.5f; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
            maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        box.addView(TextView(a).apply {
            text = summary(d); textSize = 11.5f; setTextColor(HamletUi.MUTED); maxLines = 2; gravity = Gravity.CENTER
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        val tag = TextView(a).apply {
            textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; compoundDrawablePadding = dp(4)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            when {
                !unlocked -> {
                    text = if (gate > a.game.journey.chapter) a.getString(R.string.hamlet_ui_chapter_short, gate) else a.getString(R.string.hamlet_ui_level_short, d.lvl)
                    setTextColor(HamletUi.BAD); background = HamletUi.panel(a, 0x1FB2453A, 12)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(a, "key", 16), null, null, null)
                }
                a.game.sandbox || d.cost + d.gems == 0 -> {
                    setText(R.string.hamlet_price_free); setTextColor(HamletUi.GOOD); background = HamletUi.panel(a, 0x1F2F7D45, 12)
                }
                else -> {
                    text = a.getString(R.string.hamlet_ui_number, if (d.gems > 0) d.gems else d.cost)
                    setTextColor(if (affordable) HamletUi.INK else HamletUi.BAD)
                    background = HamletUi.panel(a, if (affordable) 0x33EAC779 else 0x1FB2453A, 12)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(a, if (d.gems > 0) "gem" else "coin", 17), null, null, null)
                }
            }
        }
        box.addView(tag, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5) })
        if (unlocked && !a.game.sandbox && HamletIndustry.building(d).resources.isNotEmpty()) box.addView(TextView(a).apply {
            text = HamletUi.costText(a, VillageCost(resources = HamletIndustry.building(d).resources))
            textSize = 11.5f; gravity = Gravity.CENTER; setTextColor(if (affordable) HamletUi.MUTED else HamletUi.BAD)
        }, LinearLayout.LayoutParams(-1, -2))
        box.contentDescription = a.getString(R.string.hamlet_catalog_item, a.itemName(d.id), tag.text, d.lvl)
        HamletMotion.press(box)
        box.setOnClickListener {
            if (unlocked && affordable) { sheet.dismiss(); a.village.startPlacing(d) } else if (unlocked) HamletSheet.message(a, a.itemName(d.id),
                HamletUi.costText(a, HamletIndustry.building(d), a.game) + "\n\n" + a.getString(R.string.hamlet_industry_sources))
            else HamletMotion.pop(tag, 1.25f)
        }
        return box
    }
    private fun action(label: Int, glyph: String, enabled: Boolean = true, click: () -> Unit) {
        rows.addView(HamletUi.row(a, a.getString(label), glyph = glyph, click = click).apply { isEnabled = enabled },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }
    private fun heading(label: Int, help: String? = null) {
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(10), 0, dp(2)) }
        head.addView(TextView(a).apply { setText(label); textSize = HamletUi.TITLE; setTypeface(null, Typeface.BOLD); setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) head.addView(HamletUi.info(a, a.getString(label), help), LinearLayout.LayoutParams(dp(44), -2))
        rows.addView(head)
    }
    private fun showTools() {
        recentRow()
        heading(R.string.hamlet_builder_tools, listOf(R.string.hamlet_builder_undo_hint, R.string.hamlet_builder_copy_hint, R.string.hamlet_builder_redesign_hint,
            R.string.hamlet_builder_bulldoze_hint).joinToString("\n\n") { a.getString(it) })
        action(R.string.hamlet_builder_undo, "undo", a.game.buildHistory.canUndo()) { sheet.dismiss(); a.village.undoBuild() }
        action(R.string.hamlet_builder_copy, "brush") { sheet.dismiss(); a.village.startEyedropper() }
        action(R.string.hamlet_builder_redesign, "move") { sheet.dismiss(); a.village.startRedesign() }
        action(R.string.hamlet_builder_bulldoze, "hammer") { sheet.dismiss(); a.village.startBulldozer() }
        terrainTools()
    }
    private fun recentRow() {
        val recent = a.game.buildHistory.recent
        if (recent.isEmpty()) return
        heading(R.string.hamlet_builder_recent)
        val line = LinearLayout(a)
        for (id in recent) {
            val def = Hamlet.byId[id] ?: continue
            val piece = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(3), dp(4), dp(4))
                background = HamletUi.rowFace(a, false)
                contentDescription = a.itemName(id); isClickable = true; isFocusable = true
                setOnClickListener { sheet.dismiss(); a.village.startPlacing(def) }
                HamletMotion.press(this)
            }
            piece.addView(Preview(def), LinearLayout.LayoutParams(dp(60), dp(56)))
            piece.addView(TextView(a).apply { text = a.itemName(id); textSize = 11f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(-1, dp(30)))
            line.addView(piece, LinearLayout.LayoutParams(dp(78), dp(98)).apply { marginEnd = dp(5) })
        }
        rows.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(line) })
    }
    private fun terrainTools() {
        heading(R.string.hamlet_category_path, a.getString(R.string.hamlet_builder_fill_hint))
        action(R.string.hamlet_builder_fill, "brush") {
            val paths = Hamlet.defs.filter { it.isPath && it.w == 1 && it.d == 1 && a.game.journey.unlocked(it) }
            HamletSheet.pick(a, a.getString(R.string.hamlet_builder_fill), paths.map { a.itemName(it.id) }, glyphs = paths.map { "map" }) { which ->
                sheet.dismiss(); a.village.startFill(paths[which])
            }
        }
        for (on in listOf(true, false)) action(if (on) R.string.hamlet_builder_flood else R.string.hamlet_builder_drain, if (on) "drop" else "leaf") {
            HamletSheet.confirm(a, a.getString(if (on) R.string.hamlet_builder_flood else R.string.hamlet_builder_drain),
                a.getString(if (on) R.string.hamlet_builder_flood_confirm else R.string.hamlet_builder_drain_confirm), glyph = if (on) "drop" else "leaf") {
                sheet.dismiss(); a.village.browse()
                a.toast(a.getString(R.string.hamlet_builder_terrain_result, a.game.floodRealm(on)))
            }
        }
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
    private inner class Preview(val d: Def) : View(a) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bounds = RectF()
        override fun onDraw(canvas: Canvas) {
            if (d.isPath) {
                val p = Iso(canvas, width / 2f, height / 2f)
                p.top(0f, 0f, 0.7f, 0.7f, 0f, d.look.a)
                return
            }
            val sprite = a.village.art.forDef(d, false, null, -1)
            val scale = minOf((width - 8f) / sprite.bmp.width, (height - 8f) / sprite.bmp.height)
            val w = sprite.bmp.width * scale; val h = sprite.bmp.height * scale
            bounds.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(sprite.bmp, null, bounds, paint)
        }
    }
}
