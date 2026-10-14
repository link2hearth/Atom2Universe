package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Recettes explicites, réserve commune et équipement transférable : aucune pièce cachée par l'UI. */
internal class HamletArmoryDialog(private val context: Context, private val g: HamletGame,
    private val onChanged: () -> Unit = {}) {
    private val armory get() = g.armory
    private lateinit var dialog: AlertDialog
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab = 0
    private var hero = g.adventure.teams[g.adventure.selectedTeam].firstOrNull { it >= 0 } ?: 0
    private var slot = 0
    private var set = GearSet.HEARTHWARD
    private var rarity = EquipmentRarity.RARE
    private var page = 0
    private val tabButtons = ArrayList<Button>()
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun raw(key: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
    private fun s(key: String, vararg args: Any) = raw("hamlet_armory_$key", *args)
    private fun name(id: Int) = raw("hamlet_a_hero_$id")
    private fun pieceName(piece: GearPiece) = s("piece", raw("hamlet_a_gear_${piece.slot}"), s("set_${piece.set.name.lowercase()}"), raw("hamlet_armory_rarity_${piece.rarity.ordinal}"), piece.level)
    private fun cost(value: GearCost) = s("cost", value.ore, value.runes, value.crystal, value.timber, value.stone)
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, title: Boolean = false) = TextView(context).also {
        it.text = value; it.textSize = if (title) 16f else 13f; it.setTextColor(HamletUi.INK)
        it.setPadding(0, dp(4), 0, dp(4)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, android.graphics.Typeface.BOLD); parent.addView(it)
    }
    private fun button(parent: LinearLayout, value: String, enabled: Boolean = true, click: () -> Unit) = HamletUi.button(context, value, click).also {
        it.isEnabled = enabled; parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
    }
    private fun card(title: String) = column().also {
        it.background = HamletUi.panel(context, 0xFFE9E0CC.toInt(), 12); it.setPadding(dp(10), dp(8), dp(10), dp(10))
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }); text(it, title, true)
    }
    fun show(initialTab: Int = 0, initialHero: Int = hero) {
        tab = initialTab.coerceIn(0, 2); hero = initialHero.takeIf { g.adventure.heroes.getOrNull(it)?.owned == true } ?: hero
        val body = column().apply { setPadding(dp(12), dp(5), dp(12), dp(8)); setBackgroundColor(HamletUi.PAPER) }
        val tabs = LinearLayout(context)
        for (i in 0..2) tabButtons.add(HamletUi.button(context, s("tab_$i")) {
            tab = i; render(false)
        }.also { tabs.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) }) })
        body.addView(tabs); rows = column(); scroll = ScrollView(context).apply { addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(490), context.resources.displayMetrics.heightPixels * 68 / 100)))
        dialog = AlertDialog.Builder(context).setTitle(R.string.hamlet_armory_title).setView(body).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnDismissListener { onChanged() }; dialog.show()
        render(false)
    }
    private fun render(preserve: Boolean = true) {
        val y = if (preserve) scroll.scrollY else 0
        g.adventure.advanceProduction(); rows.removeAllViews()
        tabButtons.forEachIndexed { i, b -> b.background = HamletUi.control(context, selected = i == tab); b.isSelected = i == tab }
        text(rows, s("forge_rank", armory.forgeRank()))
        when (tab) { 0 -> recipes(); 1 -> equipment(); else -> inventory() }
        scroll.post { scroll.scrollTo(0, y) }
    }
    private fun <T> picker(parent: LinearLayout, options: List<T>, selection: T, label: (T) -> String, choose: (T) -> Unit) {
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options.map(label))
        spinner.setSelection(options.indexOf(selection).coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var first = true
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }; choose(options[position]); render()
            }
        }
        parent.addView(spinner)
    }
    private fun recipes() {
        val box = card(s("tab_0")); text(box, s("help"))
        picker(box, (0..3).toList(), slot, { raw("hamlet_a_gear_$it") }) { slot = it }
        picker(box, GearSet.entries.toList(), set, { s("set_${it.name.lowercase()}") }) { set = it }
        picker(box, EquipmentRarity.entries.toList(), rarity, { raw("hamlet_armory_rarity_${it.ordinal}") }) { rarity = it }
        val preview = GearPiece(0, slot, set, rarity)
        art(box, preview); text(box, s("set_help_${set.name.lowercase()}"))
        text(box, cost(armory.craftCost(slot, set, rarity)))
        if (!armory.unlocked(rarity)) text(box, s("requirement", raw("hamlet_armory_rarity_${rarity.ordinal}"), armory.requiredForge(rarity), armory.requiredFrontier(rarity)))
        button(box, s("craft"), armory.canCraft(slot, set, rarity)) {
            val piece = armory.craft(slot, set, rarity)
            if (piece != null) { tab = 2; page = 0; render(false) }
        }
        for (family in GearSet.entries) { val info = card(s("set_${family.name.lowercase()}")); text(info, s("set_help_${family.name.lowercase()}")) }
    }
    private fun stats(parent: LinearLayout, value: GearBonuses) {
        fun percent(v: Double) = kotlin.math.round(v * 100).toInt()
        text(parent, s("stats", percent(value.hpPercent), percent(value.attackPercent), value.armor.toInt(), percent(value.crit), percent(value.haste),
            percent(value.skillBoost), percent(value.healingBonus), percent(value.lifesteal), percent(value.armorPierce), percent(value.openingShield)))
    }
    private fun equipment() {
        val heroes = g.adventure.heroes.filter { it.owned }.map { it.id }
        picker(rows, heroes, hero, ::name) { hero = it }
        text(rows, s("transfer_help"))
        armory.recommendedSet(hero)?.let { text(rows, s("recommendation", s("set_${it.name.lowercase()}"))) }
        for (slot in 0..3) {
            val piece = armory.equipped(hero, slot); val box = card(raw("hamlet_a_gear_$slot"))
            if (piece != null) { art(box, piece); text(box, pieceName(piece)); button(box, s("unequip")) { armory.unequip(hero, slot); render() } }
            else text(box, s("slot_empty"))
            button(box, s("equip")) {
                val choices = armory.inventory.filter { it.slot == slot }.sortedWith(compareByDescending<GearPiece> { it.rarity.ordinal }.thenByDescending { it.level })
                if (choices.isEmpty()) { Toast.makeText(context, s("no_matching_piece"), Toast.LENGTH_SHORT).show(); return@button }
                val labels = choices.map { p -> pieceName(p) + raw("hamlet_list_separator") + (armory.owner(p.id)?.let { s("owner", name(it)) } ?: s("reserve")) }
                AlertDialog.Builder(context).setTitle(s("transfer", name(hero))).setItems(labels.toTypedArray()) { _, i -> armory.equip(hero, choices[i].id); render() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        val summary = card(s("active_bonus")); stats(summary, armory.bonuses(hero))
        armory.setCounts(hero).forEach { (set, count) -> text(summary, s("set_count", s("set_${set.name.lowercase()}"), count)) }
    }
    private fun inventory() {
        val pieces = armory.inventory.sortedByDescending { it.id }
        text(rows, s("inventory", pieces.size, pieces.count { armory.owner(it.id) != null }))
        if (pieces.isEmpty()) { text(rows, s("empty")); return }
        page = page.coerceIn(0, (pieces.size - 1) / 8)
        val navigation = LinearLayout(context)
        val previous = HamletUi.button(context, raw("hamlet_a_previous")) { page--; render(false) }.apply { isEnabled = page > 0 }
        val next = HamletUi.button(context, raw("hamlet_a_next")) { page++; render(false) }.apply { isEnabled = (page + 1) * 8 < pieces.size }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f)); navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f)); rows.addView(navigation)
        for (piece in pieces.drop(page * 8).take(8)) {
            val box = card(pieceName(piece)); art(box, piece)
            val owner = armory.owner(piece.id); text(box, owner?.let { s("owner", name(it)) } ?: s("reserve"))
            stats(box, armory.pieceBonus(piece))
            val price = armory.enhanceCost(piece.id)
            val nextQuality = armory.nextQuality(piece.id)
            val qualityCost = armory.qualityCost(piece.id)
            if (nextQuality != null && qualityCost != null) {
                text(box, s("quality_help")); text(box, cost(qualityCost))
                button(box, s("quality", raw("hamlet_armory_rarity_${nextQuality.ordinal}")), armory.canUpgradeQuality(piece.id)) { armory.upgradeQuality(piece.id); render() }
            } else text(box, s("quality_max"))
            if (piece.level < HamletArmory.MAX_LEVEL && price != null) {
                text(box, cost(price)); button(box, s("enhance"), armory.canEnhance(piece.id)) { armory.enhance(piece.id); render() }
            } else text(box, s("max_level"))
            button(box, s("transfer", name(hero))) { armory.equip(hero, piece.id); render() }
            if (owner != null) button(box, s("unequip")) { armory.unequip(owner, piece.slot); render() }
            else button(box, s("salvage")) {
                val refund = armory.salvageReturn(piece.id) ?: return@button
                AlertDialog.Builder(context).setTitle(s("salvage")).setMessage(s("salvage_return", cost(refund)))
                    .setPositiveButton(android.R.string.ok) { _, _ -> armory.salvage(piece.id); render() }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }
    }
    private fun art(parent: LinearLayout, piece: GearPiece) {
        parent.addView(object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val side = minOf(width.toFloat(), height.toFloat()) * .8f; val x = width / 2f; val y = height / 2f
                paint.color = intArrayOf(0xFF68A9AA.toInt(), 0xFF9B77B5.toInt(), 0xFFE2BB61.toInt(), 0xFFE78B97.toInt())[piece.rarity.ordinal]
                c.drawCircle(x, y, side / 2, paint); paint.color = 0xFF304B45.toInt(); c.drawCircle(x, y, side * .43f, paint)
                HamletGlyphs.draw(c, arrayOf("sword", "shield", "hat", "map")[piece.slot], x, y, side * .6f)
            }
        }, LinearLayout.LayoutParams(-1, dp(72)))
    }
}
