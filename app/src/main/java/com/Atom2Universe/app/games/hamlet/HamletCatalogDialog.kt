package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Les prix et les futurs déblocages restent visibles, avec les dessins réels du village. */
internal class HamletCatalogDialog(private val a: HamletActivity) {
    private val categories = listOf(Cat.FIELD, Cat.HOME, Cat.SHOP, Cat.RANCH, Cat.NATURE, Cat.DECOR, Cat.PATH)
    private var category = Cat.FIELD
    private var query = ""
    private var toolsOnly = false
    private lateinit var rows: LinearLayout
    private lateinit var dialog: AlertDialog
    private val tabs = ArrayList<Button>()
    private lateinit var toolsTab: Button

    fun show() {
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(12), 0, a.dp(12), 0); setBackgroundColor(HamletUi.PAPER) }
        val tabRow = LinearLayout(a)
        toolsTab = HamletUi.button(a, a.getString(R.string.hamlet_builder_tools)) { toolsOnly = true; redraw() }
        tabRow.addView(toolsTab)
        for (cat in categories) tabRow.addView(Button(a).apply {
            text = a.stringByName("hamlet_category_${cat.name.lowercase()}"); isAllCaps = false; textSize = 12f
            setOnClickListener { toolsOnly = false; category = cat; redraw() }
            tabs.add(this)
        })
        root.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) })
        root.addView(EditText(a).apply {
            setHint(R.string.hamlet_search); isSingleLine = true; textSize = 14f
            setTextColor(HamletUi.INK); setHintTextColor(0xFF748171.toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString().trim(); redraw() }
                override fun afterTextChanged(s: Editable?) {}
            })
        })
        rows = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val availableDp = (a.resources.displayMetrics.heightPixels / a.resources.displayMetrics.density).toInt()
        root.addView(ScrollView(a).apply { addView(rows) }, LinearLayout.LayoutParams(-1, a.dp((availableDp - 260).coerceIn(120, 430))))
        dialog = AlertDialog.Builder(a).setTitle(R.string.hamlet_build).setView(root).setNegativeButton(android.R.string.cancel, null).create()
        redraw(); dialog.show()
    }

    private fun redraw() {
        if (!::rows.isInitialized) return
        tabs.forEachIndexed { i, b -> b.alpha = if (!toolsOnly && categories[i] == category) 1f else 0.55f }
        toolsTab.alpha = if (toolsOnly) 1f else .65f
        rows.removeAllViews()
        if (toolsOnly) { showTools(); return }
        if (query.isBlank()) recentRow()
        if (category == Cat.PATH && query.isBlank()) terrainTools()
        val defs = Hamlet.defs.filter { it.cat == category && (query.isBlank() || a.itemName(it.id).contains(query, ignoreCase = true)) }.sortedWith(compareBy({ it.lvl }, { it.cost }))
        if (defs.isEmpty()) rows.addView(TextView(a).apply { setText(R.string.hamlet_search_empty); setPadding(0, a.dp(20), 0, a.dp(20)) })
        for (d in defs) {
            val unlocked = a.game.journey.unlocked(d)
            val affordable = a.game.canAfford(d)
            val card = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(a.dp(6), a.dp(6), a.dp(8), a.dp(6))
                background = a.rounded(if (unlocked) 0xFFEFE6D3.toInt() else 0xFFE3DFD5.toInt(), 12)
                alpha = if (unlocked) 1f else 0.6f
            }
            card.addView(Preview(d), LinearLayout.LayoutParams(a.dp(82), a.dp(82)))
            val price = if (a.game.sandbox) a.getString(R.string.hamlet_price_free)
                else a.getString(if (d.gems > 0) R.string.hamlet_price_gems else R.string.hamlet_price_coins, if (d.gems > 0) d.gems else d.cost)
            val gate = a.game.journey.buildingChapter(d.id)
            val description = a.getString(R.string.hamlet_catalog_item, a.itemName(d.id), price, d.lvl) + "\n" + summary(d) +
                if (gate > 0) "\n" + a.getString(R.string.hamlet_chapter_gate, gate) else ""
            card.addView(TextView(a).apply { text = description; textSize = 13f; setTextColor(0xFF334333.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
            card.contentDescription = description
            card.setOnClickListener {
                if (!unlocked) a.toast(if (gate > a.game.journey.chapter) a.getString(R.string.hamlet_chapter_gate, gate) else a.getString(R.string.hamlet_unlock_level, d.lvl))
                else if (!affordable && d.cost + d.gems > 0) a.toast(a.getString(if (d.gems > 0) R.string.hamlet_msg_poor_gems else R.string.hamlet_msg_poor))
                else { dialog.dismiss(); a.village.startPlacing(d) }
            }
            rows.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = a.dp(6) })
        }
    }
    private fun action(label: Int, glyph: String, enabled: Boolean = true, click: () -> Unit) {
        rows.addView(HamletUi.button(a, a.getString(label), click).apply {
            isEnabled = enabled
            setCompoundDrawablesWithIntrinsicBounds(HamletUi.icon(a, glyph, 20), null, null, null)
            compoundDrawablePadding = a.dp(8)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(5); bottomMargin = a.dp(4) })
    }
    private fun help(label: Int) {
        rows.addView(TextView(a).apply {
            setText(label); textSize = 13f; setPadding(a.dp(5), a.dp(5), a.dp(5), a.dp(10)); setTextColor(HamletUi.INK)
        })
    }
    private fun showTools() {
        recentRow()
        action(R.string.hamlet_builder_undo, "clock", a.game.buildHistory.canUndo()) { dialog.dismiss(); a.village.undoBuild() }
        help(R.string.hamlet_builder_undo_hint)
        action(R.string.hamlet_builder_copy, "brush") { dialog.dismiss(); a.village.startEyedropper() }
        help(R.string.hamlet_builder_copy_hint)
        action(R.string.hamlet_builder_redesign, "crate") { dialog.dismiss(); a.village.startRedesign() }
        help(R.string.hamlet_builder_redesign_hint)
        action(R.string.hamlet_builder_bulldoze, "gear") { dialog.dismiss(); a.village.startBulldozer() }
        help(R.string.hamlet_builder_bulldoze_hint)
        terrainTools()
    }
    private fun recentRow() {
        val recent = a.game.buildHistory.recent
        if (recent.isEmpty()) { if (toolsOnly) help(R.string.hamlet_builder_recent_empty); return }
        rows.addView(TextView(a).apply { setText(R.string.hamlet_builder_recent); setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(HamletUi.INK); setPadding(a.dp(4), a.dp(8), a.dp(4), a.dp(5)) })
        val line = LinearLayout(a)
        for (id in recent) {
            val def = Hamlet.byId[id] ?: continue
            val piece = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(a.dp(4), a.dp(3), a.dp(4), a.dp(4))
                background = HamletUi.panel(a, HamletUi.PAPER, 10)
                contentDescription = a.itemName(id); isFocusable = true
                setOnClickListener { dialog.dismiss(); a.village.startPlacing(def) }
            }
            piece.addView(Preview(def), LinearLayout.LayoutParams(a.dp(60), a.dp(58)))
            piece.addView(TextView(a).apply { text = a.itemName(id); textSize = 10f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(-1, a.dp(30)))
            line.addView(piece, LinearLayout.LayoutParams(a.dp(74), a.dp(96)).apply { marginEnd = a.dp(5) })
        }
        rows.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(line) })
    }
    private fun terrainTools() {
        action(R.string.hamlet_builder_fill, "brush") {
            val paths = Hamlet.defs.filter { it.isPath && it.w == 1 && it.d == 1 && a.game.journey.unlocked(it) }
            AlertDialog.Builder(a).setTitle(R.string.hamlet_builder_fill)
                .setItems(paths.map { a.itemName(it.id) }.toTypedArray()) { _, which -> dialog.dismiss(); a.village.startFill(paths[which]) }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        help(R.string.hamlet_builder_fill_hint)
        for (on in listOf(true, false)) action(if (on) R.string.hamlet_builder_flood else R.string.hamlet_builder_drain, if (on) "drop" else "leaf") {
            AlertDialog.Builder(a).setMessage(if (on) R.string.hamlet_builder_flood_confirm else R.string.hamlet_builder_drain_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    dialog.dismiss(); a.village.browse()
                    a.toast(a.getString(R.string.hamlet_builder_terrain_result, a.game.floodRealm(on)))
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
    }
    private fun summary(d: Def): String {
        val p = HamletLife.produce[d.id]
        return when {
            p != null -> a.getString(R.string.hamlet_catalog_produce, p.coins, a.duration(p.seconds * 1000L), p.feed)
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
