package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/** Le module Voisins : une scène de jeu à part, jamais mêlée au village du joueur. */
internal class HamletNeighborsPage(private val context: Context, private val game: HamletGame, private val nav: HamletNav) : HamletPage {
    private val neighbors get() = game.neighbors
    private val art = HamletArt()
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View get() = root
    private val strip = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var villagesTab: Button
    private lateinit var ordersTab: Button
    private var map: HamletView? = null
    private var scene: HamletGame? = null
    private var visiting = -1
    private var selected = -1
    private var book = false
    private var detail: LinearLayout? = null
    private var shopRow: LinearLayout? = null
    private var countdown: TextView? = null
    private var wasReady = false
    private var bookReady = -1
    private var milestones = ""
    private fun dp(n: Int) = HamletUi.dp(context, n)
    private fun raw(key: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (id == 0) context.getString(R.string.hamlet_title) else context.getString(id, *args)
    }
    private fun s(key: String, vararg args: Any) = raw("hamlet_neighbors_$key", *args)
    private fun name(id: Int) = s("name_$id")
    private fun item(id: String) = raw("hamlet_n_$id")
    private fun duration(ms: Long): String = if (ms < 60_000) context.getString(R.string.hamlet_duration_seconds, (ms.coerceAtLeast(0) + 999) / 1000)
        else context.getString(R.string.hamlet_duration_minutes, (ms + 59_999) / 60_000)
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun line() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun text(parent: LinearLayout, value: CharSequence, title: Boolean = false, light: Boolean = false, small: Boolean = false, color: Int = 0): TextView = TextView(context).also {
        it.text = value; it.textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        it.setTextColor(if (color != 0) color else if (light) HamletUi.PAPER else if (small) HamletUi.MUTED else HamletUi.INK)
        it.setPadding(dp(3), dp(4), dp(3), dp(5)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD)
        parent.addView(it)
    }
    private fun button(label: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, glyph: String? = null, click: () -> Unit) =
        HamletUi.button(context, label, weight, glyph = glyph) {
            if (nav.haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            click()
        }.apply { isEnabled = enabled }
    private fun card(parent: LinearLayout) = column().also {
        it.setPadding(dp(13), dp(10), dp(13), dp(12)); it.background = HamletUi.card(context); it.elevation = dp(2).toFloat()
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }
    private fun dark(view: View) {
        view.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1A3631.toInt(), HamletUi.DEEP))
    }

    init {
        strip.setPadding(dp(8), dp(6), dp(8), dp(6))
        strip.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, 0xFF1A3631.toInt()))
        villagesTab = button(s("villages"), glyph = "signpost") { roster() }.apply { maxLines = 1 }
        ordersTab = button(s("book_count", neighbors.readyCount()), glyph = "scroll") { orderBook() }.apply { maxLines = 1 }
        strip.addView(villagesTab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        strip.addView(ordersTab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        strip.addView(HamletUi.info(context, s("title"), s("intro"), onDark = true), LinearLayout.LayoutParams(dp(44), -2))
        root.addView(strip, LinearLayout.LayoutParams(-1, -2))
        dark(body)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        roster()
    }

    override fun show(page: Int) { map?.start(); refreshTabs(); supplies() }
    override fun hide() { map?.stop() }
    override fun close() { map?.stop(); map = null; art.clear() }
    override fun back(): Boolean {
        if (visiting < 0 && !book) return false
        roster(); return true
    }
    override fun tick() {
        refreshTabs(); supplies()
        if (book && neighbors.readyCount() != bookReady) orderBook()
        if (visiting < 0 && !book && milestoneState() != milestones) roster()
        scene?.let { neighbors.syncScene(visiting, it) }
        if (visiting >= 0 && selected >= 0) {
            val order = neighbors.order(visiting, selected)
            val ready = order != null && neighbors.left(visiting, selected) == 0L
            if (ready != wasReady) showShop(selected)
            else if (order != null) countdown?.text = if (ready) s("ready") else s("waiting", duration(neighbors.left(visiting, selected)))
        }
    }
    private fun supplies() {
        nav.supplies(listOf(
            HamletChip("hourglass", game.workSeconds.toLong(), duration(game.workSeconds * 1000L),
                context.getString(R.string.hamlet_ui_work_bank, duration(game.workSeconds * 1000L))),
        ))
    }
    private fun refreshTabs() {
        ordersTab.text = s("book_count", neighbors.readyCount())
        HamletUi.choice(villagesTab, !book); HamletUi.choice(ordersTab, book)
    }
    private fun milestoneState() = (0..2).joinToString { "${neighbors.milestoneTarget(it)}:${neighbors.milestoneProgress(it) >= neighbors.milestoneTarget(it)}" }

    private fun clear() {
        map?.stop(); map = null; scene = null; countdown = null; detail = null; shopRow = null; selected = -1
        body.removeAllViews()
    }
    private fun scrolling(): LinearLayout {
        val rows = column().apply { setPadding(dp(10), 0, dp(10), dp(16)) }
        body.addView(ScrollView(context).apply { addView(rows); isFillViewport = true }, LinearLayout.LayoutParams(-1, -1))
        return rows
    }
    private fun roster() {
        clear(); visiting = -1; book = false; refreshTabs()
        val rows = scrolling()
        val goals = card(rows)
        text(goals, s("milestones"), true)
        milestones = milestoneState()
        for (i in 0..2) {
            val target = neighbors.milestoneTarget(i)
            val progress = neighbors.milestoneProgress(i)
            if (target == 0) { text(goals, s("milestone_complete", s("milestone_$i")), small = true, color = HamletUi.GOOD); continue }
            val row = line()
            row.addView(TextView(context).apply { text = s("milestone_$i"); textSize = HamletUi.SMALL; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(0, -2, 1.2f))
            row.addView(HamletBar(context).apply { set(progress, target.toLong()) }, LinearLayout.LayoutParams(0, dp(9), 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
            row.addView(TextView(context).apply { text = context.getString(R.string.hamlet_ui_progress, minOf(progress, target.toLong()), target); textSize = HamletUi.SMALL; setTextColor(HamletUi.MUTED) })
            goals.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            if (progress >= target) goals.addView(button(context.getString(R.string.hamlet_claim), weight = Weight.MAIN, glyph = "gift") {
                if (neighbors.claimMilestone(i)) { nav.changed(); roster() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }
        HamletNeighbors.villages.forEach { neighbor ->
            val card = card(rows)
            val identity = line()
            identity.addView(object : View(context) {
                override fun onDraw(canvas: Canvas) { HamletCharacterArt.drawResident(canvas, neighbor.id, width / 2f, height.toFloat(), height.toFloat()) }
            }, LinearLayout.LayoutParams(dp(54), dp(64)))
            val labels = column()
            text(labels, name(neighbor.id), true)
            text(labels, s("level", neighbor.level, neighbors.activeCount(neighbor.id)), small = true)
            identity.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            identity.addView(HamletUi.info(context, name(neighbor.id), s("story_${neighbor.id}")), LinearLayout.LayoutParams(dp(44), -2))
            card.addView(identity)
            text(card, neighbor.shops.joinToString(context.getString(R.string.hamlet_neighbors_separator)) { item(it) }, small = true)
            card.addView(button(s("visit"), glyph = "signpost") { visit(neighbor.id) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }

    private fun visit(id: Int, shop: Int = -1) {
        val scene = neighbors.generate(id) ?: return
        clear(); visiting = id; book = false; this.scene = scene; refreshTabs()
        neighbors.visit(id)
        val head = line().apply { setPadding(dp(12), dp(6), dp(8), dp(4)) }
        head.addView(TextView(context).apply {
            text = name(id); textSize = HamletUi.TITLE; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(HamletUi.info(context, name(id), s("visit_hint"), onDark = true), LinearLayout.LayoutParams(dp(44), -2))
        body.addView(head)
        val village = HamletView(context, scene, art).apply {
            readOnly = true
            haptics = nav.haptics
            renderer.effectsEnabled = nav.effects
            renderer.nightEnabled = false
            host = object : HamletViewHost {
                override fun onTapObject(o: Obj) { if (neighbors.shopType(id, o.id) != null) showShop(o.id) }
                override fun onTapVillager(v: Villager) = Unit
                override fun onTapEmpty() { renderer.selected = null; invalidate() }
                override fun onModeChanged() = Unit
            }
        }
        map = village
        body.addView(village, LinearLayout.LayoutParams(-1, 0, 1f))
        village.post { village.home(); if (root.isShown) village.start() }
        val shops = LinearLayout(context).apply { setPadding(dp(8), dp(6), dp(8), 0) }
        HamletNeighbors.villages[id].shops.forEachIndexed { i, type ->
            shops.addView(button(item(type)) { showShop(HamletNeighbors.FIRST_SHOP + i) }.apply { maxLines = 2; textSize = HamletUi.SMALL; tag = HamletNeighbors.FIRST_SHOP + i },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        body.addView(shops); shopRow = shops
        detail = column().apply { setPadding(dp(12), dp(4), dp(12), dp(8)) }
        val holder = FrameLayout(context).apply { background = HamletUi.card(context); elevation = dp(3).toFloat() }
        holder.addView(ScrollView(context).apply { addView(detail) }, FrameLayout.LayoutParams(-1, -1))
        body.addView(holder, LinearLayout.LayoutParams(-1, dp(230)).apply { setMargins(dp(8), dp(6), dp(8), dp(8)) })
        showShop(if (shop >= HamletNeighbors.FIRST_SHOP) shop else HamletNeighbors.FIRST_SHOP)
    }

    private fun showShop(shop: Int) {
        val type = neighbors.shopType(visiting, shop) ?: return
        val panel = detail ?: return
        selected = shop; panel.removeAllViews(); countdown = null
        scene?.let { neighbors.syncScene(visiting, it); map?.renderer?.selected = it.objs[shop] }
        shopRow?.let { shops -> for (i in 0 until shops.childCount) (shops.getChildAt(i) as? Button)?.let { HamletUi.choice(it, it.tag == shop) } }
        text(panel, item(type), true)
        val reward = neighbors.reward(visiting, shop)
        text(panel, s("reward", item("p_$type"), reward.coins, reward.xp, reward.work), small = true)
        val pending = neighbors.order(visiting, shop)
        wasReady = pending != null && neighbors.left(visiting, shop) == 0L
        when {
            pending == null -> {
                text(panel, s("free_duration", duration(neighbors.duration(visiting, shop) * 1000L)), small = true)
                panel.addView(button(s("place"), weight = Weight.MAIN, glyph = "scroll") { if (neighbors.placeOrder(visiting, shop)) { showShop(shop); refreshTabs(); nav.changed() } })
            }
            wasReady -> {
                countdown = text(panel, s("ready"), color = HamletUi.GOOD)
                panel.addView(button(s("collect"), weight = Weight.MAIN, glyph = "gift") {
                    neighbors.collect(visiting, shop)?.let { reward ->
                        map?.reward(scene?.objs?.get(shop), reward.coins, reward.xp, 0)
                        showShop(shop); refreshTabs(); supplies(); nav.changed()
                    }
                })
            }
            else -> {
                countdown = text(panel, s("waiting", duration(neighbors.left(visiting, shop))))
                panel.addView(button(s("use_work", duration(game.workSeconds * 1000L)), game.workSeconds > 0, glyph = "hourglass") {
                    if (neighbors.useWork(visiting, shop) > 0) { showShop(shop); supplies(); nav.changed() }
                })
                val helpLine = line()
                helpLine.addView(TextView(context).apply {
                    text = s("help_instruction", s("tool_${neighbors.helpTool(visiting, shop)}"), pending.aid, 6); textSize = HamletUi.SMALL; setTextColor(HamletUi.INK)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                helpLine.addView(HamletUi.info(context, s("tool_${neighbors.helpTool(visiting, shop)}"), s("help_free")), LinearLayout.LayoutParams(dp(44), -2))
                panel.addView(helpLine)
                val tools = LinearLayout(context)
                val glyphs = arrayOf("hammer", "brush", "wood")
                for (tool in 0..2) tools.addView(button(s("tool_$tool"), glyph = glyphs[tool]) {
                    if (neighbors.help(visiting, shop, tool)) showShop(shop)
                }.apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
                panel.addView(tools)
            }
        }
    }

    private fun orderBook() {
        clear(); visiting = -1; book = true; refreshTabs()
        bookReady = neighbors.readyCount()
        val rows = scrolling()
        if (neighbors.orders.isEmpty()) { text(rows, s("book_empty"), light = true).setPadding(dp(6), dp(16), dp(6), 0); return }
        neighbors.orders.sortedWith(compareBy<NeighborOrder> { it.due }.thenBy { it.neighbor }).forEach { order ->
            val card = card(rows)
            text(card, s("order_title", name(order.neighbor), item(order.type)), true)
            val ready = neighbors.left(order.neighbor, order.shop) == 0L
            text(card, if (ready) s("ready") else s("waiting", duration(neighbors.left(order.neighbor, order.shop))), color = if (ready) HamletUi.GOOD else 0)
            val actions = line()
            if (ready) actions.addView(button(s("collect"), weight = Weight.MAIN, glyph = "gift") {
                if (neighbors.collect(order.neighbor, order.shop) != null) { nav.changed(); supplies(); orderBook() }
            }, LinearLayout.LayoutParams(0, -2, 1.4f).apply { marginEnd = dp(4) })
            actions.addView(button(s("visit_order"), weight = if (ready) Weight.QUIET else Weight.NORMAL, glyph = "signpost") { visit(order.neighbor, order.shop) },
                LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }
}
