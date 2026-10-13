package com.Atom2Universe.app.games.hamlet

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.widget.*
import com.Atom2Universe.app.R

/** A separate window and separate game scene. The home village is never replaced by NPC data. */
internal class HamletNeighborsDialog(private val context: Context, private val game: HamletGame,
    private val haptics: Boolean = true, private val effectsEnabled: Boolean = true,
    private val onClose: () -> Unit = {}) {
    private val neighbors get() = game.neighbors
    private val timer = Handler(Looper.getMainLooper())
    private val art = HamletArt()
    private lateinit var dialog: Dialog
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var heading: TextView
    private lateinit var ordersButton: Button
    private var map: HamletView? = null
    private var scene: HamletGame? = null
    private var visiting = -1
    private var selected = -1
    private var book = false
    private var focused = false
    private var detail: LinearLayout? = null
    private var countdown: TextView? = null
    private var wasReady = false
    private var bookReady = -1
    private val pulse = object : Runnable {
        override fun run() {
            if (!focused || !dialog.isShowing) return
            ordersButton.text = s("book_count", neighbors.readyCount())
            if (book && neighbors.readyCount() != bookReady) orderBook()
            scene?.let { neighbors.syncScene(visiting, it) }
            if (visiting >= 0 && selected >= 0) {
                val order = neighbors.order(visiting, selected)
                val ready = order != null && neighbors.left(visiting, selected) == 0L
                if (ready != wasReady) showShop(selected)
                else if (order != null) countdown?.text = if (ready) s("ready") else s("waiting", duration(neighbors.left(visiting, selected)))
            }
            timer.postDelayed(this, 1000)
        }
    }
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
    private fun text(parent: LinearLayout, value: String, title: Boolean = false, light: Boolean = false): TextView = TextView(context).also {
        it.text = value; it.textSize = if (title) 17f else 13f
        it.setTextColor(if (light) HamletUi.PAPER else HamletUi.INK)
        it.setPadding(dp(3), dp(5), dp(3), dp(6)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD)
        parent.addView(it)
    }
    private fun button(label: String, enabled: Boolean = true, click: () -> Unit) = HamletUi.button(context, label) {
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click()
    }.apply { isEnabled = enabled }
    private fun card(parent: LinearLayout) = column().also {
        it.setPadding(dp(12), dp(10), dp(12), dp(12)); it.background = HamletUi.panel(context, HamletUi.PAPER, 14)
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }
    private fun refreshOrders() { ordersButton.text = s("book_count", neighbors.readyCount()) }

    fun show() {
        dialog = object : Dialog(context) {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                super.onWindowFocusChanged(hasFocus)
                focused = hasFocus
                timer.removeCallbacks(pulse)
                if (hasFocus) { map?.start(); timer.post(pulse) } else map?.stop()
            }
            @Deprecated("Deprecated in Android")
            override fun onBackPressed() { if (visiting >= 0 || book) roster() else dismiss() }
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        root = column().apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(HamletUi.FOREST) }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading = TextView(context).apply { text = s("title"); textSize = 19f; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD) }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(s("home")) { dialog.dismiss() }, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(header)
        val navigation = LinearLayout(context)
        navigation.addView(button(s("villages")) { roster() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        ordersButton = button(s("book_count", neighbors.readyCount())) { orderBook() }
        navigation.addView(ordersButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(navigation)
        body = column()
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root, android.view.ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { focused = false; timer.removeCallbacks(pulse); map?.stop(); map = null; art.clear(); onClose() }
        dialog.show(); dialog.window?.apply { setLayout(-1, -1); setBackgroundDrawableResource(android.R.color.transparent) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(10) + bars.left, dp(8) + bars.top, dp(10) + bars.right, dp(8) + bars.bottom); insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        roster()
    }

    private fun clear() {
        map?.stop(); map = null; scene = null; countdown = null; detail = null; selected = -1
        body.removeAllViews(); refreshOrders()
    }
    private fun roster() {
        clear(); visiting = -1; book = false; heading.text = s("title")
        val rows = column()
        body.addView(ScrollView(context).apply { addView(rows) }, LinearLayout.LayoutParams(-1, -1))
        text(rows, s("intro"), light = true)
        val milestones = card(rows)
        text(milestones, s("milestones"), true)
        for (i in 0..2) {
            val target = neighbors.milestoneTarget(i)
            val progress = neighbors.milestoneProgress(i)
            if (target == 0) text(milestones, s("milestone_complete", s("milestone_$i")))
            else milestones.addView(button(s("milestone_progress", s("milestone_$i"), minOf(progress, target.toLong()), target), progress >= target) {
                if (neighbors.claimMilestone(i)) roster()
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(3) })
        }
        HamletNeighbors.villages.forEach { neighbor ->
            val card = card(rows)
            val identity = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            identity.addView(object : View(context) {
                override fun onDraw(canvas: Canvas) { HamletCharacterArt.drawResident(canvas, neighbor.id, width / 2f, height.toFloat(), height.toFloat()) }
            }, LinearLayout.LayoutParams(dp(54), dp(64)))
            val labels = column()
            text(labels, name(neighbor.id), true)
            text(labels, s("level", neighbor.level, neighbors.activeCount(neighbor.id)))
            identity.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(identity)
            text(card, s("story_${neighbor.id}"))
            text(card, neighbor.shops.joinToString(context.getString(R.string.hamlet_neighbors_separator)) { item(it) })
            card.addView(button(s("visit")) { visit(neighbor.id) }, LinearLayout.LayoutParams(-1, dp(48)))
        }
    }

    private fun visit(id: Int, shop: Int = -1) {
        val scene = neighbors.generate(id) ?: return
        clear(); visiting = id; book = false; this.scene = scene
        neighbors.visit(id); heading.text = name(id)
        text(body, s("visit_hint"), light = true)
        val village = HamletView(context, scene, art).apply {
            readOnly = true
            haptics = this@HamletNeighborsDialog.haptics
            renderer.effectsEnabled = effectsEnabled
            renderer.nightEnabled = false
            host = object : HamletViewHost {
                override fun onTapObject(o: Obj) { if (neighbors.shopType(id, o.id) != null) showShop(o.id) else Toast.makeText(context, item(o.t), Toast.LENGTH_SHORT).show() }
                override fun onTapVillager(v: Villager) { Toast.makeText(context, s("welcome", name(id)), Toast.LENGTH_SHORT).show() }
                override fun onTapEmpty() { renderer.selected = null; invalidate() }
                override fun onModeChanged() = Unit
            }
        }
        map = village
        body.addView(village, LinearLayout.LayoutParams(-1, 0, 1f))
        village.post { village.home(); if (!focused) village.stop() }
        val shops = LinearLayout(context)
        HamletNeighbors.villages[id].shops.forEachIndexed { i, type ->
            shops.addView(button(item(type)) { showShop(HamletNeighbors.FIRST_SHOP + i) }, LinearLayout.LayoutParams(0, dp(55), 1f).apply { marginEnd = dp(3) })
        }
        body.addView(shops)
        detail = column().apply { setPadding(dp(10), dp(4), dp(10), dp(8)); setBackgroundColor(HamletUi.PAPER) }
        body.addView(ScrollView(context).apply { addView(detail) }, LinearLayout.LayoutParams(-1, dp(238)))
        showShop(if (shop >= HamletNeighbors.FIRST_SHOP) shop else HamletNeighbors.FIRST_SHOP)
    }

    private fun showShop(shop: Int) {
        val type = neighbors.shopType(visiting, shop) ?: return
        val panel = detail ?: return
        selected = shop; panel.removeAllViews(); countdown = null
        scene?.let { neighbors.syncScene(visiting, it); map?.renderer?.selected = it.objs[shop] }
        text(panel, item(type), true)
        val reward = neighbors.reward(visiting, shop)
        text(panel, s("reward", item("p_$type"), reward.coins, reward.xp, reward.work))
        val pending = neighbors.order(visiting, shop)
        wasReady = pending != null && neighbors.left(visiting, shop) == 0L
        when {
            pending == null -> {
                text(panel, s("free_duration", duration(neighbors.duration(visiting, shop) * 1000L)))
                panel.addView(button(s("place")) { if (neighbors.placeOrder(visiting, shop)) { showShop(shop); refreshOrders() } })
            }
            wasReady -> {
                countdown = text(panel, s("ready"))
                panel.addView(button(s("collect")) {
                    neighbors.collect(visiting, shop)?.let { reward ->
                        map?.reward(scene?.objs?.get(shop), reward.coins, reward.xp, 0)
                        showShop(shop); refreshOrders()
                    }
                })
            }
            else -> {
                countdown = text(panel, s("waiting", duration(neighbors.left(visiting, shop))))
                panel.addView(button(s("use_work", duration(game.workSeconds * 1000L)), game.workSeconds > 0) {
                    if (neighbors.useWork(visiting, shop) > 0) showShop(shop)
                })
                text(panel, s("help_instruction", s("tool_${neighbors.helpTool(visiting, shop)}"), pending.aid, 6))
                val tools = LinearLayout(context)
                for (tool in 0..2) tools.addView(button(s("tool_$tool")) {
                    if (neighbors.help(visiting, shop, tool)) showShop(shop)
                    else Toast.makeText(context, s("help_wrong"), Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(3) })
                panel.addView(tools)
                text(panel, s("help_free"))
            }
        }
    }

    private fun orderBook() {
        clear(); visiting = -1; book = true; heading.text = s("book")
        bookReady = neighbors.readyCount()
        val rows = column()
        body.addView(ScrollView(context).apply { addView(rows) }, LinearLayout.LayoutParams(-1, -1))
        if (neighbors.orders.isEmpty()) { text(rows, s("book_empty"), light = true); return }
        neighbors.orders.sortedWith(compareBy<NeighborOrder> { it.due }.thenBy { it.neighbor }).forEach { order ->
            val card = card(rows)
            text(card, s("order_title", name(order.neighbor), item(order.type)), true)
            text(card, if (neighbors.left(order.neighbor, order.shop) == 0L) s("ready") else s("waiting", duration(neighbors.left(order.neighbor, order.shop))))
            if (neighbors.left(order.neighbor, order.shop) == 0L) card.addView(button(s("collect")) {
                if (neighbors.collect(order.neighbor, order.shop) != null) orderBook()
            })
            card.addView(button(s("visit_order")) { visit(order.neighbor, order.shop) })
        }
    }
}
