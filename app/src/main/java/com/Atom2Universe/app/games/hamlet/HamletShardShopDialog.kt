package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Offres nominatives et prix visibles : chaque achat renforce le héros choisi. */
internal class HamletShardShopDialog(private val context: Context, private val g: HamletGame,
    private val onChanged: () -> Unit = {}) {
    private val shop get() = g.shardShop
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var dialog: AlertDialog
    private var selectedHero = -1
    private val timer = Handler(Looper.getMainLooper())
    private var displayedState: List<Any>? = null
    private val pulse = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            if (dialog.window?.decorView?.windowVisibility == View.VISIBLE) {
                g.adventure.advanceProduction()
                if (snapshot() != displayedState) render()
            }
            timer.postDelayed(this, 1000)
        }
    }
    private fun snapshot(): List<Any> = listOf(g.adventure[AdventureResource.RUNES], g.adventure[AdventureResource.CRYSTAL],
        shop.rank(), g.adventure.trainingLevel(), shop.offers(),
        g.adventure.heroes.filter { it.owned }.map { listOf(it.id, it.level, it.stars, it.shards) })
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun raw(key: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
    private fun s(key: String, vararg args: Any) = raw("hamlet_shop_ui_$key", *args)
    private fun p(key: String, quantity: Int, vararg args: Any): String = context.resources.getQuantityString(
        context.resources.getIdentifier("hamlet_shop_ui_$key", "plurals", context.packageName), quantity, *args)
    private fun name(hero: Int) = raw("hamlet_a_hero_$hero")
    private fun rarity(rarity: HeroRarity) = raw("hamlet_a_rarity_${rarity.ordinal}")
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, label: CharSequence, title: Boolean = false, small: Boolean = false, color: Int = 0) = TextView(context).also {
        it.text = label; it.textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        it.setTextColor(if (color != 0) color else if (small) HamletUi.MUTED else HamletUi.INK)
        it.setPadding(0, dp(4), 0, dp(4)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, android.graphics.Typeface.BOLD)
        parent.addView(it)
    }
    private fun tinted(value: String, r: HeroRarity) = HamletUi.tint(value, rarity(r) to HamletUi.rarity(r))
    fun show(hero: Int = -1) {
        selectedHero = hero.takeIf { g.adventure.heroes.getOrNull(it)?.owned == true } ?: -1
        rows = column(); scroll = ScrollView(context).apply { addView(rows) }
        val body = column().apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); setBackgroundColor(HamletUi.PAPER)
            addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(500), context.resources.displayMetrics.heightPixels * 68 / 100)))
        }
        dialog = AlertDialog.Builder(context).setTitle(s("title")).setView(body)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnDismissListener { timer.removeCallbacksAndMessages(null); onChanged() }
        dialog.show(); render(); timer.post(pulse)
    }
    private fun requirements(r: HeroRarity): String {
        val req = shop.requirements(r)
        val parts = ArrayList<String>()
        if (req.chapter > 0) parts.add(s("chapter", req.chapter))
        if (req.frontier > 0) parts.add(s("route", req.frontier))
        if (req.inn > 0) parts.add(s("inn", req.inn))
        if (req.forge > 0) parts.add(s("forge", req.forge))
        if (req.academy > 0) parts.add(s("academy", req.academy))
        return s("requirement", rarity(r), parts.joinToString(raw("hamlet_list_separator")))
    }
    private fun render() {
        val y = scroll.scrollY
        g.adventure.advanceProduction(); rows.removeAllViews()
        val head = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = s("wallet", g.adventure[AdventureResource.RUNES], g.adventure[AdventureResource.CRYSTAL]); textSize = HamletUi.TITLE
            setTextColor(HamletUi.INK); setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(HamletUi.info(context, s("title"), s("help") + raw("hamlet_shop_ui_paragraph") + raw("hamlet_shard_shop_price_help") +
            raw("hamlet_shop_ui_paragraph") + raw("hamlet_shard_shop_unlock_help")), LinearLayout.LayoutParams(dp(44), -2))
        rows.addView(head)
        if (g.sandbox) text(rows, s("creative"), small = true) else for (r in HeroRarity.entries) {
            if (!shop.unlocked(r)) { text(rows, tinted(requirements(r), r), small = true); break }
            else text(rows, tinted(s(if (r == HeroRarity.MYTHIC) "weekly_stock" else "daily_stock", rarity(r), shop.remaining(r)), r), small = true)
        }
        val owned = g.adventure.heroes.filter { it.owned }
        val choices = intArrayOf(-1) + owned.map { it.id }.toIntArray()
        val picker = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf(s("all")) + owned.map { name(it.id) })
            setSelection(choices.indexOf(selectedHero).coerceAtLeast(0))
        }
        rows.addView(picker)
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (selectedHero != choices[position]) { selectedHero = choices[position]; scroll.scrollTo(0, 0); render() }
            }
        }
        for (offer in shop.offers().filter { (selectedHero < 0 && it.unlocked) || it.hero == selectedHero }) {
            val h = g.adventure.heroes[offer.hero]
            val box = column().apply {
                setPadding(dp(12), dp(8), dp(12), dp(10)); background = HamletUi.panel(context, 0xFFE9E0CC.toInt(), 12)
            }
            rows.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            val identity = LinearLayout(context)
            identity.addView(object : View(context) {
                private val backdrop = HeroBackdrop(h.def.rarity, resources.displayMetrics.density)
                override fun onDraw(canvas: Canvas) {
                    backdrop.draw(canvas, width.toFloat(), height.toFloat(), 0f)
                    val size = minOf(width, height) * .86f
                    HamletCharacterArt.draw(canvas, true, h.id, width / 2f, height - size * .07f, size, time = 0f)
                }
            }.apply { contentDescription = name(h.id) }, LinearLayout.LayoutParams(dp(68), dp(76)))
            val details = column()
            text(details, name(h.id), true, color = HamletUi.rarity(h.def.rarity))
            text(details, rarity(h.def.rarity), small = true, color = HamletUi.rarity(h.def.rarity))
            text(details, raw("hamlet_a_hero_growth", g.adventure.effectiveLevel(h.id), h.stars, h.shards))
            identity.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); box.addView(identity)
            if (h.stars < HamletHeroes.STAR_CAP) {
                box.addView(HamletUi.button(context, raw("hamlet_a_stars_cost", g.adventure.starCost(h.id))) {
                    if (g.adventure.promote(h.id)) { onChanged(); render() }
                }.apply { isEnabled = g.adventure.canPromote(h.id) })
            } else text(box, raw("hamlet_hero_stars_max", HamletHeroes.STAR_CAP), small = true, color = HamletUi.GOOD)
            if (!offer.unlocked) { text(box, requirements(offer.rarity), small = true, color = HamletUi.BAD); continue }
            if (offer.remaining <= 0) { text(box, s("sold_out"), small = true); continue }
            val actions = LinearLayout(context)
            for (quantity in listOf(1, minOf(5, offer.remaining)).distinct()) {
                val cost = shop.cost(h.id, quantity) ?: continue
                val parts = ArrayList<String>()
                if (cost.runes > 0) parts.add(s("cost_runes", cost.runes))
                if (cost.crystal > 0) parts.add(s("cost_crystal", cost.crystal))
                val label = if (g.sandbox) p("buy_free", quantity, quantity) else p("buy", quantity, quantity, parts.joinToString(raw("hamlet_list_separator")))
                actions.addView(HamletUi.button(context, label) {
                    if (shop.buy(h.id, quantity, expectedCost = cost)) onChanged()
                    render()
                }.apply { isEnabled = shop.canBuy(h.id, quantity) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            }
            box.addView(actions)
        }
        scroll.post { scroll.scrollTo(0, y) }
        displayedState = snapshot()
    }
}
