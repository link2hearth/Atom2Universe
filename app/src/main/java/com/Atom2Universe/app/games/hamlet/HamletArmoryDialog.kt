package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/** Recettes explicites, réserve commune et équipement transférable : aucune pièce cachée par l'UI.
 * Seuls les coûts et bonus non nuls s'affichent ; la rareté se lit à sa couleur. */
internal class HamletArmoryDialog(private val context: Context, private val g: HamletGame,
    private val onChanged: () -> Unit = {}) {
    private val armory get() = g.armory
    private lateinit var sheet: HamletSheet
    private val rows get() = sheet.body
    private val scroll get() = sheet.scroll
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
    private fun rarityName(r: EquipmentRarity) = raw("hamlet_armory_rarity_${r.ordinal}")
    private fun pieceName(piece: GearPiece): CharSequence {
        val rarity = rarityName(piece.rarity)
        return HamletUi.tint(s("piece", raw("hamlet_a_gear_${piece.slot}"), s("set_${piece.set.name.lowercase()}"), rarity, piece.level),
            rarity to HamletUi.gear(piece.rarity.ordinal))
    }
    /** Only what the recipe really asks for. */
    private fun cost(value: GearCost): String {
        val parts = ArrayList<String>()
        fun add(amount: Long, resource: AdventureResource) { if (amount > 0) parts.add(raw("hamlet_a_amount", amount, raw("hamlet_a_resource_${resource.name.lowercase()}"))) }
        add(value.ore, AdventureResource.ORE); add(value.runes, AdventureResource.RUNES); add(value.crystal, AdventureResource.CRYSTAL)
        add(value.timber, AdventureResource.TIMBER); add(value.stone, AdventureResource.STONE)
        if (value.coins > 0) parts.add(raw("hamlet_a_amount", value.coins, raw("hamlet_a_coins")))
        return parts.joinToString(context.getString(R.string.hamlet_ui_dot))
    }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: CharSequence, title: Boolean = false, small: Boolean = false, color: Int = 0) = TextView(context).also {
        it.text = value; it.textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        it.setTextColor(if (color != 0) color else if (small) HamletUi.MUTED else HamletUi.INK)
        it.setPadding(0, dp(4), 0, dp(4)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD); parent.addView(it)
    }
    private fun button(parent: LinearLayout, value: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, glyph: String? = null, click: () -> Unit) =
        HamletUi.button(context, value, weight, glyph = glyph, click = click).also {
            it.isEnabled = enabled; parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }
    private fun card(title: CharSequence, help: String? = null) = column().also {
        it.background = HamletUi.panel(context, 0xFFFFF8E9.toInt(), 12, 0xFFD9C9A5.toInt()); it.setPadding(dp(10), dp(6), dp(10), dp(10))
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val head = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply { text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) head.addView(HamletUi.info(context, title, help), LinearLayout.LayoutParams(dp(44), -2))
        it.addView(head)
    }
    fun show(initialTab: Int = 0, initialHero: Int = hero) {
        tab = initialTab.coerceIn(0, 2); hero = initialHero.takeIf { g.adventure.heroes.getOrNull(it)?.owned == true } ?: hero
        sheet = HamletSheet(context, context.getString(R.string.hamlet_armory_title), tall = true)
        val tabs = LinearLayout(context).apply { setPadding(0, dp(8), 0, dp(2)) }
        val glyphs = arrayOf("anvil", "shield", "crate")
        for (i in 0..2) tabButtons.add(HamletUi.button(context, s("tab_$i"), glyph = glyphs[i]) {
            tab = i; render(false)
        }.also { it.maxLines = 1; tabs.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) }) })
        sheet.top.addView(tabs)
        sheet.onDismiss = { onChanged() }
        render(false); sheet.show()
    }
    private fun render(preserve: Boolean = true) {
        val y = if (preserve) scroll.scrollY else 0
        g.adventure.advanceProduction(); rows.removeAllViews()
        tabButtons.forEachIndexed { i, b -> HamletUi.choice(b, i == tab) }
        text(rows, s("forge_rank", armory.forgeRank()), small = true)
        when (tab) { 0 -> recipes(); 1 -> equipment(); else -> inventory() }
        scroll.post { scroll.scrollTo(0, y) }
    }
    /** The current value on a button; the list opens in a sheet. */
    private fun <T> picker(parent: LinearLayout, title: CharSequence, options: List<T>, selection: T, glyph: String?, label: (T) -> CharSequence, choose: (T) -> Unit) {
        parent.addView(HamletUi.picker(context, label(selection), glyph) {
            HamletSheet.pick(context, title, options.map(label), options.indexOf(selection), options.map { glyph }) { i -> choose(options[i]); render() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    private fun rarityLabel(r: EquipmentRarity): CharSequence = HamletUi.tint(rarityName(r), rarityName(r) to HamletUi.gear(r.ordinal))
    private fun recipes() {
        val box = card(s("tab_0"), s("help"))
        val slots = LinearLayout(context)
        val glyphs = arrayOf("sword", "shield", "hat", "map")
        for (i in 0..3) slots.addView(HamletUi.choice(HamletUi.button(context, raw("hamlet_a_gear_$i"), glyph = glyphs[i]) { slot = i; render() }, i == slot)
            .apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
        box.addView(slots)
        picker(box, s("tab_0"), GearSet.entries.toList(), set, "shield", { s("set_${it.name.lowercase()}") }) { set = it }
        picker(box, s("tab_0"), EquipmentRarity.entries.toList(), rarity, "gem", ::rarityLabel) { rarity = it }
        val preview = GearPiece(0, slot, set, rarity)
        art(box, preview)
        text(box, pieceName(preview), true)
        text(box, s("set_help_${set.name.lowercase()}"), small = true)
        text(box, cost(armory.craftCost(slot, set, rarity)))
        if (!armory.unlocked(rarity)) text(box, s("requirement", rarityName(rarity), armory.requiredForge(rarity), armory.requiredFrontier(rarity)), small = true, color = HamletUi.BAD)
        button(box, s("craft"), armory.canCraft(slot, set, rarity), Weight.MAIN, "anvil") {
            val piece = armory.craft(slot, set, rarity)
            if (piece != null) { tab = 2; page = 0; render(false) }
        }
    }
    /** Only the bonuses a piece or a hero really has. */
    private fun stats(parent: LinearLayout, value: GearBonuses) {
        fun percent(v: Double) = kotlin.math.round(v * 100).toInt()
        val parts = ArrayList<Pair<String, Int>>()
        fun add(key: String, amount: Int) { if (amount != 0) parts.add(s("stat_$key", amount) to HamletUi.GOOD) }
        add("hp", percent(value.hpPercent)); add("attack", percent(value.attackPercent)); add("armor", value.armor.toInt())
        add("crit", percent(value.crit)); add("haste", percent(value.haste)); add("skills", percent(value.skillBoost))
        add("healing", percent(value.healingBonus)); add("lifesteal", percent(value.lifesteal)); add("pierce", percent(value.armorPierce))
        add("shield", percent(value.openingShield))
        if (parts.isNotEmpty()) text(parent, HamletUi.joined(context, parts), small = true)
    }
    private fun equipment() {
        val heroes = g.adventure.heroes.filter { it.owned }.map { it.id }
        picker(rows, s("tab_1"), heroes, hero, "hat", { HamletUi.tint(name(it), name(it) to HamletUi.rarity(HamletHeroes.defs[it].rarity)) }) { hero = it }
        armory.recommendedSet(hero)?.let { text(rows, s("recommendation", s("set_${it.name.lowercase()}")), small = true) }
        for (slot in 0..3) {
            val piece = armory.equipped(hero, slot); val box = card(raw("hamlet_a_gear_$slot"))
            if (piece != null) { art(box, piece); text(box, pieceName(piece)) } else text(box, s("slot_empty"), small = true)
            val choices = armory.inventory.filter { it.slot == slot }.sortedWith(compareByDescending<GearPiece> { it.rarity.ordinal }.thenByDescending { it.level })
            val actions = LinearLayout(context)
            actions.addView(HamletUi.button(context, s("equip"), glyph = "shield") {
                val labels = choices.map { p -> HamletUi.tint(android.text.TextUtils.concat(pieceName(p), raw("hamlet_list_separator"),
                    armory.owner(p.id)?.let { s("owner", name(it)) } ?: s("reserve")), rarityName(p.rarity) to HamletUi.gear(p.rarity.ordinal)) }
                HamletSheet.pick(context, s("transfer", name(hero)), labels, glyphs = choices.map { arrayOf("sword", "shield", "hat", "map")[it.slot] }) { i ->
                    armory.equip(hero, choices[i].id); render()
                }
            }.apply { isEnabled = choices.isNotEmpty() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
            if (piece != null) actions.addView(HamletUi.button(context, s("unequip"), Weight.QUIET) { armory.unequip(hero, slot); render() },
                LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(actions)
        }
        val summary = card(s("active_bonus")); stats(summary, armory.bonuses(hero))
        armory.setCounts(hero).forEach { (set, count) -> text(summary, s("set_count", s("set_${set.name.lowercase()}"), count), small = true, color = HamletUi.GOOD) }
    }
    private fun inventory() {
        val pieces = armory.inventory.sortedByDescending { it.id }
        text(rows, s("inventory", pieces.size, pieces.count { armory.owner(it.id) != null }), small = true)
        if (pieces.isEmpty()) { text(rows, s("empty")); return }
        page = page.coerceIn(0, (pieces.size - 1) / 8)
        if (pieces.size > 8) {
            val navigation = LinearLayout(context)
            navigation.addView(HamletUi.button(context, raw("hamlet_a_previous"), Weight.QUIET) { page--; render(false) }.apply { isEnabled = page > 0 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
            navigation.addView(HamletUi.button(context, raw("hamlet_a_next"), Weight.QUIET) { page++; render(false) }.apply { isEnabled = (page + 1) * 8 < pieces.size }, LinearLayout.LayoutParams(0, -2, 1f))
            rows.addView(navigation)
        }
        for (piece in pieces.drop(page * 8).take(8)) {
            val box = card(pieceName(piece)); art(box, piece)
            val owner = armory.owner(piece.id)
            text(box, owner?.let { HamletUi.tint(s("owner", name(it)), name(it) to HamletUi.rarity(HamletHeroes.defs[it].rarity)) } ?: s("reserve"), small = true)
            stats(box, armory.pieceBonus(piece))
            val price = armory.enhanceCost(piece.id)
            if (piece.level < armory.levelCap() && price != null) {
                button(box, s("enhance"), armory.canEnhance(piece.id), glyph = "hammer") { armory.enhance(piece.id); render() }
                text(box, cost(price), small = true)
            } else text(box, s("max_level"), small = true, color = HamletUi.GOOD)
            val nextQuality = armory.nextQuality(piece.id)
            val qualityCost = armory.qualityCost(piece.id)
            if (nextQuality != null && qualityCost != null) {
                val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(HamletUi.button(context, s("quality", rarityName(nextQuality))) { armory.upgradeQuality(piece.id); render() }
                    .apply { isEnabled = armory.canUpgradeQuality(piece.id) }, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(HamletUi.info(context, s("quality", rarityName(nextQuality)), s("quality_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
                box.addView(line)
                text(box, cost(qualityCost), small = true)
            } else text(box, s("quality_max"), small = true, color = HamletUi.GOOD)
            val actions = LinearLayout(context)
            actions.addView(HamletUi.button(context, s("transfer", name(hero)), Weight.QUIET) { armory.equip(hero, piece.id); render() }
                .apply { isEnabled = owner != hero }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
            if (owner != null) actions.addView(HamletUi.button(context, s("unequip"), Weight.QUIET) { armory.unequip(owner, piece.slot); render() }, LinearLayout.LayoutParams(0, -2, 1f))
            else actions.addView(HamletUi.button(context, s("salvage"), Weight.QUIET) {
                val refund = armory.salvageReturn(piece.id) ?: return@button
                HamletSheet.confirm(context, s("salvage"), s("salvage_return", cost(refund)), s("salvage"), "hammer") { armory.salvage(piece.id); render() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(actions)
        }
    }
    private fun art(parent: LinearLayout, piece: GearPiece) {
        parent.addView(object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val side = minOf(width.toFloat(), height.toFloat()) * .8f; val x = width / 2f; val y = height / 2f
                paint.color = HamletUi.gear(piece.rarity.ordinal, onDark = true)
                c.drawCircle(x, y, side / 2, paint); paint.color = 0xFF304B45.toInt(); c.drawCircle(x, y, side * .43f, paint)
                HamletGlyphs.draw(c, arrayOf("sword", "shield", "hat", "map")[piece.slot], x, y, side * .6f)
            }
        }, LinearLayout.LayoutParams(-1, dp(72)))
    }
}
